package com.neojou.tsumego.board

enum class Goal {
    Live,
    Kill,
    Seki,
    KoLive,
    KoKill,
}

enum class Outcome {
    UnconditionalLive,
    Seki,
    KoLive,
    KoKill,
    UnconditionalDead,
    Unsettled,
}

fun Goal.isSuccess(outcome: Outcome): Boolean = when (this) {
    Goal.Live -> outcome == Outcome.UnconditionalLive
    Goal.Kill -> outcome == Outcome.UnconditionalDead
    Goal.Seki -> outcome == Outcome.Seki
    Goal.KoLive -> outcome == Outcome.KoLive || outcome == Outcome.UnconditionalLive
    Goal.KoKill -> outcome == Outcome.KoKill || outcome == Outcome.UnconditionalDead
}

data class Problem(
    val rect: BoardRect,
    val edges: Edges,
    val stones: Map<Point, StoneColor>,
    val goal: Goal,
    val targets: Set<Point>,
) {
    fun validationError(): String? {
        if (edges.left == EdgeKind.Real && rect.left != 0) return "真盤邊必須在 A 路或 1 路或 19 路或 T 路"
        if (edges.right == EdgeKind.Real && rect.right != 18) return "真盤邊必須在 A 路或 1 路或 19 路或 T 路"
        if (edges.bottom == EdgeKind.Real && rect.bottom != 1) return "真盤邊必須在 A 路或 1 路或 19 路或 T 路"
        if (edges.top == EdgeKind.Real && rect.top != 19) return "真盤邊必須在 A 路或 1 路或 19 路或 T 路"
        for (point in stones.keys) {
            if (!rect.contains(point)) return "子在題目盤矩形外：${point.label}"
        }
        if (targets.isEmpty()) return "目標棋串不能是空的"
        for (point in targets) {
            if (!rect.contains(point)) return "目標棋串在矩形外：${point.label}"
            if (point !in stones) return "目標棋串必須有子：${point.label}"
        }
        val targetColors = targets.map { stones.getValue(it) }.toSet()
        return when (goal) {
            Goal.Live, Goal.KoLive ->
                if (targetColors != setOf(StoneColor.Black)) "做活／劫活的目標必須是黑" else null
            Goal.Kill, Goal.KoKill ->
                if (targetColors != setOf(StoneColor.White)) "殺棋／劫殺的目標必須是白" else null
            Goal.Seki ->
                if (StoneColor.Black !in targetColors || StoneColor.White !in targetColors) {
                    "雙活題的目標棋串必須含黑與白"
                } else {
                    null
                }
        }
    }

    fun flipped(): Problem = copy(
        stones = stones.mapValues { it.value.opposite },
    )

    /**
     * If a 牆 side has a stone on that edge, open [extra] empty lines so the
     * stone is not sitting on the cut.
     */
    fun withOpenWallMargin(extra: Int = 2): Problem {
        var left = rect.left
        var right = rect.right
        var bottom = rect.bottom
        var top = rect.top
        if (edges.bottom == EdgeKind.Wall && stones.keys.any { it.rank == rect.bottom }) {
            bottom = (rect.bottom - extra).coerceAtLeast(1)
        }
        if (edges.top == EdgeKind.Wall && stones.keys.any { it.rank == rect.top }) {
            top = (rect.top + extra).coerceAtMost(19)
        }
        if (edges.left == EdgeKind.Wall && stones.keys.any { it.file == rect.left }) {
            left = (rect.left - extra).coerceAtLeast(0)
        }
        if (edges.right == EdgeKind.Wall && stones.keys.any { it.file == rect.right }) {
            right = (rect.right + extra).coerceAtMost(18)
        }
        if (left == rect.left && right == rect.right && bottom == rect.bottom && top == rect.top) return this
        return copy(rect = BoardRect(left, right, bottom, top))
    }
}

fun lowerLeftCorner(files: Int, ranks: Int): Pair<BoardRect, Edges> {
    require(files in 1..19 && ranks in 1..19)
    val rect = BoardRect(left = 0, right = files - 1, bottom = 1, top = ranks)
    val edges = Edges(
        left = EdgeKind.Real,
        right = EdgeKind.Wall,
        bottom = EdgeKind.Real,
        top = EdgeKind.Wall,
    )
    return rect to edges
}

fun upperRightCorner(files: Int, ranks: Int): Pair<BoardRect, Edges> {
    require(files in 1..19 && ranks in 1..19)
    val rect = BoardRect(left = 19 - files, right = 18, bottom = 20 - ranks, top = 19)
    val edges = Edges(
        left = EdgeKind.Wall,
        right = EdgeKind.Real,
        bottom = EdgeKind.Wall,
        top = EdgeKind.Real,
    )
    return rect to edges
}
