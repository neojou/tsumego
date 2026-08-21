package com.neojou.tsumego.library

import com.neojou.tsumego.board.BoardRect
import com.neojou.tsumego.board.EdgeKind
import com.neojou.tsumego.board.Edges
import com.neojou.tsumego.board.Goal
import com.neojou.tsumego.board.Point
import com.neojou.tsumego.board.Problem
import com.neojou.tsumego.board.StoneColor
import kotlinx.serialization.Serializable
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json

sealed class ProblemLoad {
    data class Ok(val problem: Problem) : ProblemLoad()
    data class Err(val message: String) : ProblemLoad()
}

object ProblemLibrary {
    const val FORMAT = "tsumego"
    const val VERSION = 1
    const val FILE_SUFFIX = ".tsumego.json"

    private val json = Json {
        prettyPrint = true
        encodeDefaults = true
        ignoreUnknownKeys = false
        explicitNulls = false
    }

    fun encode(problem: Problem): String {
        val error = problem.validationError()
        require(error == null) { error ?: "" }
        val dto = ProblemFileDto(
            format = FORMAT,
            version = VERSION,
            rect = RectDto(
                left = Point.FILE_CHARS[problem.rect.left].toString(),
                right = Point.FILE_CHARS[problem.rect.right].toString(),
                bottom = problem.rect.bottom,
                top = problem.rect.top,
            ),
            edges = EdgesDto(
                left = problem.edges.left.wire(),
                right = problem.edges.right.wire(),
                bottom = problem.edges.bottom.wire(),
                top = problem.edges.top.wire(),
            ),
            stones = problem.stones.entries
                .sortedBy { it.key }
                .associate { it.key.label to it.value.wire() },
            goal = problem.goal.wire(),
            targets = problem.targets.sorted().map { it.label },
        )
        return json.encodeToString(ProblemFileDto.serializer(), dto)
    }

    fun decode(text: String): ProblemLoad {
        val dto = try {
            json.decodeFromString(ProblemFileDto.serializer(), text)
        } catch (_: SerializationException) {
            return ProblemLoad.Err("缺少欄位或 JSON 不合法")
        } catch (_: IllegalArgumentException) {
            return ProblemLoad.Err("缺少欄位或 JSON 不合法")
        }
        if (dto.format != FORMAT) return ProblemLoad.Err("不是詰碁題目檔")
        if (dto.version != VERSION) return ProblemLoad.Err("不支援的題目檔版本：${dto.version}")
        if (dto.toPlay != null && dto.toPlay != "black") return ProblemLoad.Err("題目檔含白先輪次")

        val left = Point.fileIndex(dto.rect.left.singleOrNull() ?: ' ')
            ?: return ProblemLoad.Err("座標不合法：${dto.rect.left}")
        val right = Point.fileIndex(dto.rect.right.singleOrNull() ?: ' ')
            ?: return ProblemLoad.Err("座標不合法：${dto.rect.right}")
        val rect = try {
            BoardRect(left, right, dto.rect.bottom, dto.rect.top)
        } catch (_: IllegalArgumentException) {
            return ProblemLoad.Err("座標不合法")
        }
        val edges = Edges(
            left = parseEdge(dto.edges.left) ?: return ProblemLoad.Err("四邊必須是 real 或 wall"),
            right = parseEdge(dto.edges.right) ?: return ProblemLoad.Err("四邊必須是 real 或 wall"),
            bottom = parseEdge(dto.edges.bottom) ?: return ProblemLoad.Err("四邊必須是 real 或 wall"),
            top = parseEdge(dto.edges.top) ?: return ProblemLoad.Err("四邊必須是 real 或 wall"),
        )
        val stones = LinkedHashMap<Point, StoneColor>()
        for ((label, colorText) in dto.stones) {
            val point = Point.parse(label) ?: return ProblemLoad.Err("座標不合法：$label")
            val color = parseColor(colorText) ?: return ProblemLoad.Err("子的顏色不合法：$colorText")
            stones[point] = color
        }
        val targets = LinkedHashSet<Point>()
        for (label in dto.targets) {
            val point = Point.parse(label) ?: return ProblemLoad.Err("座標不合法：$label")
            targets.add(point)
        }
        val goal = parseGoal(dto.goal) ?: return ProblemLoad.Err("題型不合法：${dto.goal}")
        if (goal != Goal.Kill) return ProblemLoad.Err("v1 題型只開殺棋")
        val problem = Problem(rect, edges, stones, goal, targets)
        val error = problem.validationError()
        return if (error != null) ProblemLoad.Err(error) else ProblemLoad.Ok(problem)
    }
}

@Serializable
internal data class ProblemFileDto(
    val format: String,
    val version: Int,
    val rect: RectDto,
    val edges: EdgesDto,
    val stones: Map<String, String>,
    val goal: String,
    val targets: List<String>,
    val toPlay: String? = null,
)

@Serializable
internal data class RectDto(
    val left: String,
    val right: String,
    val bottom: Int,
    val top: Int,
)

@Serializable
internal data class EdgesDto(
    val left: String,
    val right: String,
    val bottom: String,
    val top: String,
)

private fun EdgeKind.wire() = if (this == EdgeKind.Real) "real" else "wall"

private fun StoneColor.wire() = if (this == StoneColor.Black) "black" else "white"

private fun Goal.wire() = when (this) {
    Goal.Live -> "live"
    Goal.Kill -> "kill"
    Goal.Seki -> "seki"
    Goal.KoLive -> "koLive"
    Goal.KoKill -> "koKill"
}

private fun parseEdge(text: String): EdgeKind? = when (text) {
    "real" -> EdgeKind.Real
    "wall" -> EdgeKind.Wall
    else -> null
}

private fun parseColor(text: String): StoneColor? = when (text) {
    "black" -> StoneColor.Black
    "white" -> StoneColor.White
    else -> null
}

private fun parseGoal(text: String): Goal? = when (text) {
    "live" -> Goal.Live
    "kill" -> Goal.Kill
    "seki" -> Goal.Seki
    "koLive" -> Goal.KoLive
    "koKill" -> Goal.KoKill
    else -> null
}
