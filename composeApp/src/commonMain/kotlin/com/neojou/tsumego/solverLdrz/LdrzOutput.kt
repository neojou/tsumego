package com.neojou.tsumego.solverLdrz

import com.neojou.tsumego.board.Point
import com.neojou.tsumego.board.StoneColor
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

object LdrzOutput {
    const val ENGINE = "kotlin-v1"

    private val json = Json {
        prettyPrint = true
        encodeDefaults = true
        explicitNulls = true
    }

    fun resultJsonPath(stem: String): String = "result/result_$stem.json"

    fun uctTreePath(stem: String): String = "result/uct_tree_$stem.sgf"

    fun resultJson(problem: LdrzProblem, result: LdrzResult): String {
        val dto = LdrzResultFileDto(
            engine = ENGINE,
            problem = problem.stem,
            source_json = problem.sourceJsonName,
            status = result.status.name,
            first_move_sgf = result.firstMoveSgf,
            answer_firstmove = problem.answerFirstMove,
            NumSimulations = result.numSimulations,
            Time = result.timeSeconds,
            zone_count = result.zoneCount,
            goal_black = problem.blackGoal.name,
            goal_white = problem.whiteGoal.name,
            turn_color = problem.turnColor.let { if (it == StoneColor.Black) "b" else "w" },
            message = result.message,
        )
        return json.encodeToString(LdrzResultFileDto.serializer(), dto)
    }

    fun uctTreeSgf(problem: LdrzProblem, result: LdrzResult): String {
        val size = problem.boardSize
        val black = problem.stones.filterValues { it == StoneColor.Black }.keys.sorted()
        val white = problem.stones.filterValues { it == StoneColor.White }.keys.sorted()
        val crucial = problem.crucialStones().sorted()
        val region = problem.region.sorted()
        val rzone = region.joinToString(",") { LdrzCoord.toSgf(it, size) }
        val win = result.firstMoveSgf.orEmpty()
        val pl = if (problem.turnColor == StoneColor.Black) "B" else "W"
        return buildString {
            append("(;FF[4]CA[UTF-8]SZ[$size]GN[${problem.stem}]")
            appendProps("AB", black, size)
            appendProps("AW", white, size)
            append("PL[$pl]")
            appendProps("TR", crucial, size)
            appendProps("MA", region, size)
            append("C[RZONE:$rzone\nWIN:$win\nstatus:${result.status.name}]")
            var color = problem.turnColor
            for (point in result.principalLine) {
                val tag = if (color == StoneColor.Black) "B" else "W"
                append(';')
                append(tag)
                append('[')
                append(LdrzCoord.toSgf(point, size))
                append(']')
                color = color.opposite
            }
            append(')')
        }
    }

    fun parseResultJson(text: String): LdrzResultFileDto? = try {
        json.decodeFromString(LdrzResultFileDto.serializer(), text)
    } catch (_: Exception) {
        null
    }

    fun summary(dto: LdrzResultFileDto, sgf: String?): String = buildString {
        append("狀態: ").append(dto.status).append('\n')
        append("第一手: ").append(dto.first_move_sgf ?: "（無）")
        val point = dto.first_move_sgf?.let { LdrzCoord.toPoint(it) }
        if (point != null) append(" (").append(point.label).append(')')
        append('\n')
        append("節點數: ").append(dto.NumSimulations).append('\n')
        append("耗時: ").append(dto.Time).append(" 秒")
        val line = principalLineFromSgf(sgf)
        if (line.isNotEmpty()) {
            append('\n')
            append("SGF 主線: ").append(line)
        }
    }

    fun principalLineFromSgf(sgf: String?): String {
        if (sgf.isNullOrBlank()) return ""
        val out = ArrayList<String>()
        val regex = Regex(""";([BW])\[([a-s]{0,2})\]""")
        for (m in regex.findAll(sgf)) {
            val tag = m.groupValues[1]
            val coord = m.groupValues[2]
            out += if (coord.isEmpty()) "${tag}[]" else "$tag[$coord]"
        }
        return out.joinToString(" ")
    }
}

@Serializable
data class LdrzResultFileDto(
    val engine: String,
    val problem: String,
    val source_json: String,
    val status: String,
    val first_move_sgf: String?,
    val answer_firstmove: String?,
    val NumSimulations: Int,
    val Time: Double,
    val zone_count: Int,
    val goal_black: String,
    val goal_white: String,
    val turn_color: String,
    val message: String,
)

private fun StringBuilder.appendProps(tag: String, points: List<Point>, boardSize: Int) {
    if (points.isEmpty()) return
    append(tag)
    for (p in points) {
        append('[')
        append(LdrzCoord.toSgf(p, boardSize))
        append(']')
    }
}
