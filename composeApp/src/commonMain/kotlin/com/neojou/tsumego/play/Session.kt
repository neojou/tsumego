package com.neojou.tsumego.play

import com.neojou.tsumego.board.Outcome
import com.neojou.tsumego.board.Point
import com.neojou.tsumego.board.Position
import com.neojou.tsumego.board.Problem
import com.neojou.tsumego.board.StoneColor
import com.neojou.tsumego.board.isSuccess
import com.neojou.tsumego.classify.classify
import com.neojou.tsumego.classify.isAwayFromTargets
import com.neojou.tsumego.solve.Action
import com.neojou.tsumego.solve.AlphaBetaSolver
import com.neojou.tsumego.solve.Solver
import com.neojou.tsumego.solve.SolverInput
import com.neojou.tsumego.solve.SolverResult
import com.neojou.tsumego.solve.UnlimitedBudget
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

enum class PlayStatus {
    InProgress,
    WaitingForReply,
    Success,
    Failure,
    Timeout,
}

data class PlaySnapshot(
    val problem: Problem,
    val stones: Map<Point, StoneColor>,
    val toPlay: StoneColor,
    val status: PlayStatus,
    val lastMove: Point?,
    val lastMoveIsPass: Boolean,
    val canUndo: Boolean,
    val searchPaths: List<String> = emptyList(),
    val searchPathCount: Int = 0,
    val decisionTree: DecisionTreeView = DecisionTreeView.Empty,
    val pickingReply: Boolean = false,
    val canRedo: Boolean = false,
)

private data class Ply(
    val black: Action,
    val white: Action?,
    val positionBefore: Position,
    val passesBefore: Int,
)

private data class CachedReply(
    val result: SolverResult,
    val paths: List<String>,
    val pathCount: Int,
    val tree: DecisionTreeView,
)

class Session(
    val problem: Problem,
    private val solver: Solver = AlphaBetaSolver(),
    private val scope: CoroutineScope,
    private val searchDispatcher: CoroutineDispatcher = Dispatchers.Default,
) {
    private var position: Position = Position.initial(problem)
    private var consecutivePasses: Int = 0
    private val history = ArrayList<Ply>()
    private var searchJob: Job? = null
    private val replyCache = HashMap<String, CachedReply>()
    private var tree = DecisionTreeProjection()

    private val _state = MutableStateFlow(snapshotOf(PlayStatus.InProgress, lastMove = null, lastMoveIsPass = false))
    val state: StateFlow<PlaySnapshot> = _state.asStateFlow()

    fun tryMove(point: Point): Boolean {
        if (_state.value.status != PlayStatus.InProgress) return false
        val next = position.play(point, StoneColor.Black) ?: return false
        applyBlack(Action.Move(point), next)
        return true
    }

    fun pass(): Boolean {
        if (_state.value.status == PlayStatus.WaitingForReply) {
            searchJob?.cancel()
            searchJob = null
            applyWhite(Action.Pass, failing = false)
            return true
        }
        if (_state.value.status != PlayStatus.InProgress) return false
        applyBlack(Action.Pass, position)
        return true
    }

    fun undo(): Boolean {
        val last = history.lastOrNull() ?: return false
        searchJob?.cancel()
        searchJob = null
        position = last.positionBefore
        consecutivePasses = last.passesBefore
        history.removeAt(history.lastIndex)
        publish(PlayStatus.InProgress, lastMove = null, lastMoveIsPass = false, clearPaths = true)
        return true
    }

    fun redo(): Boolean {
        val status = _state.value.status
        if (status != PlayStatus.Success && status != PlayStatus.Failure) return false
        searchJob?.cancel()
        searchJob = null
        position = Position.initial(problem)
        consecutivePasses = 0
        history.clear()
        publish(PlayStatus.InProgress, lastMove = null, lastMoveIsPass = false, clearPaths = true)
        return true
    }

    suspend fun waitForIdle() {
        searchJob?.join()
    }

    private fun applyBlack(action: Action, next: Position) {
        val ply = Ply(
            black = action,
            white = null,
            positionBefore = position,
            passesBefore = consecutivePasses,
        )
        history.add(ply)
        position = next
        consecutivePasses = if (action is Action.Pass) consecutivePasses + 1 else 0
        val both = consecutivePasses >= 2
        val outcome = classify(position, problem.targets, both)
        when {
            problem.goal.isSuccess(outcome) ->
                publish(PlayStatus.Success, lastPoint(action), action is Action.Pass)
            both || outcome != Outcome.Unsettled ->
                publish(PlayStatus.Failure, lastPoint(action), action is Action.Pass)
            else -> launchSearch()
        }
    }

    private fun searchKey(pos: Position, passes: Int): String =
        "${pos.key}|$passes|${pos.past.sorted().joinToString()}"

    private fun launchSearch() {
        searchJob?.cancel()
        val frozenPosition = position
        val frozenPasses = consecutivePasses
        val lastBlack = history.lastOrNull()
        val lastBlackMove = lastBlack?.black as? Action.Move
        val blackPlayedAway = lastBlackMove != null &&
            isAwayFromTargets(lastBlack.positionBefore, lastBlackMove.point, problem.targets)
        val key = searchKey(frozenPosition, frozenPasses)
        replyCache[key]?.let { cached ->
            _state.update { old ->
                old.copy(
                    searchPaths = cached.paths,
                    searchPathCount = cached.pathCount,
                    decisionTree = cached.tree,
                    pickingReply = false,
                )
            }
            applySolverResult(cached.result)
            return
        }
        tree = DecisionTreeProjection()
        publish(
            PlayStatus.WaitingForReply,
            lastPoint(history.lastOrNull()?.black),
            history.lastOrNull()?.black is Action.Pass,
            clearPaths = true,
        )
        searchJob = scope.launch {
            val mine = coroutineContext[Job]
            fun stillThisSearch(): Boolean = searchJob === mine && mine?.isActive == true
            val result = withContext(searchDispatcher) {
                solver.solve(
                    SolverInput(
                        problem = problem,
                        position = frozenPosition,
                        consecutivePasses = frozenPasses,
                        blackPlayedAway = blackPlayedAway,
                        lastBlack = lastBlackMove?.point,
                        budget = UnlimitedBudget,
                        onPath = onPath@{ line ->
                            if (!stillThisSearch()) return@onPath
                            tree.notePath()
                            _state.update { old ->
                                val paths =
                                    if (old.searchPaths.size >= DISPLAYED_SEARCH_PATHS || line in old.searchPaths) {
                                        old.searchPaths
                                    } else {
                                        old.searchPaths + line
                                    }
                                old.copy(
                                    searchPaths = paths,
                                    searchPathCount = tree.pathCount,
                                    decisionTree = tree.view(),
                                )
                            }
                        },
                        onPv = onPv@{ white, black, continuation, replace ->
                            if (!stillThisSearch()) return@onPv
                            tree.show(white, black, continuation, replace)
                            _state.update { old ->
                                old.copy(decisionTree = tree.view())
                            }
                        },
                        onPathsComplete = onDone@{
                            if (!stillThisSearch()) return@onDone
                            _state.update { it.copy(pickingReply = true, decisionTree = tree.view()) }
                        },
                    ),
                )
            }
            if (result !is SolverResult.Timeout) {
                val snap = _state.value
                replyCache[key] = CachedReply(result, snap.searchPaths, snap.searchPathCount, snap.decisionTree)
            }
            applySolverResult(result)
        }
    }

    private fun applySolverResult(result: SolverResult) {
        when (result) {
            SolverResult.Timeout -> {
                publish(PlayStatus.WaitingForReply, lastPoint(history.lastOrNull()?.black), history.lastOrNull()?.black is Action.Pass)
            }
            is SolverResult.Resist -> applyWhite(result.action, failing = false)
            is SolverResult.Refute -> applyWhite(result.action, failing = true)
        }
    }

    private fun applyWhite(action: Action, failing: Boolean) {
        val idx = history.lastIndex
        if (idx < 0) return
        val nextPos = when (action) {
            Action.Pass -> position
            is Action.Move -> position.play(action.point, StoneColor.White) ?: position
        }
        consecutivePasses = if (action is Action.Pass) consecutivePasses + 1 else 0
        position = nextPos
        history[idx] = history[idx].copy(white = action)
        val both = consecutivePasses >= 2
        val outcome = classify(position, problem.targets, both)
        val status = when {
            problem.goal.isSuccess(outcome) -> PlayStatus.Success
            failing || both -> PlayStatus.Failure
            else -> PlayStatus.InProgress
        }
        publish(status, lastPoint(action), action is Action.Pass)
    }

    private fun publish(
        status: PlayStatus,
        lastMove: Point?,
        lastMoveIsPass: Boolean,
        clearPaths: Boolean = false,
    ) {
        if (clearPaths) tree = DecisionTreeProjection()
        _state.update { old ->
            snapshotOf(
                status,
                lastMove,
                lastMoveIsPass,
                if (clearPaths) emptyList() else old.searchPaths,
                searchPathCount = if (clearPaths) 0 else old.searchPathCount,
                decisionTree = if (clearPaths) DecisionTreeView.Empty else old.decisionTree,
                pickingReply = if (clearPaths || status != PlayStatus.WaitingForReply) false else old.pickingReply,
            )
        }
    }

    private fun snapshotOf(
        status: PlayStatus,
        lastMove: Point?,
        lastMoveIsPass: Boolean,
        searchPaths: List<String> = emptyList(),
        searchPathCount: Int = 0,
        decisionTree: DecisionTreeView = DecisionTreeView.Empty,
        pickingReply: Boolean = false,
    ) = PlaySnapshot(
        problem = problem,
        stones = position.stones,
        toPlay = when (status) {
            PlayStatus.InProgress -> StoneColor.Black
            PlayStatus.WaitingForReply, PlayStatus.Timeout -> StoneColor.White
            PlayStatus.Success, PlayStatus.Failure -> StoneColor.Black
        },
        status = status,
        lastMove = lastMove,
        lastMoveIsPass = lastMoveIsPass,
        canUndo = history.isNotEmpty(),
        searchPaths = searchPaths,
        searchPathCount = searchPathCount,
        decisionTree = decisionTree,
        pickingReply = pickingReply,
        canRedo = status == PlayStatus.Success || status == PlayStatus.Failure,
    )

    private fun lastPoint(action: Action?): Point? = (action as? Action.Move)?.point

    companion object {
        const val DISPLAYED_SEARCH_PATHS = 1000
    }
}
