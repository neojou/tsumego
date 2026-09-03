package com.neojou.tsumego.solverLdrz

import com.neojou.tsumego.board.Point
import com.neojou.tsumego.board.StoneColor
import kotlinx.serialization.Serializable
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json

sealed class LdrzLoad {
    data class Ok(val problem: LdrzProblem) : LdrzLoad()
    data class Err(val message: String) : LdrzLoad()
}

object LdrzJson {
    private val json = Json {
        ignoreUnknownKeys = true
        isLenient = true
        explicitNulls = false
    }

    fun parse(text: String, sourceFileName: String): LdrzLoad {
        val dto = try {
            json.decodeFromString(LdrzJsonDto.serializer(), text)
        } catch (_: SerializationException) {
            return LdrzLoad.Err("JSON 不合法")
        } catch (_: IllegalArgumentException) {
            return LdrzLoad.Err("JSON 不合法")
        } catch (_: Exception) {
            return LdrzLoad.Err("JSON 不合法")
        }

        val sgf = dto.masked_sgf_str?.takeIf { it.isNotBlank() }
            ?: dto.rawsgf?.takeIf { it.isNotBlank() }
            ?: return LdrzLoad.Err("缺少 masked_sgf_str／rawsgf")

        val props = parseSgfRoot(sgf)
        val boardSize = props["SZ"]?.firstOrNull()?.toIntOrNull() ?: 19
        if (boardSize !in 1..19) return LdrzLoad.Err("SZ 不合法")

        val stones = LinkedHashMap<Point, StoneColor>()
        fun addStones(tag: String, color: StoneColor): String? {
            for (coord in props[tag].orEmpty()) {
                if (coord.isBlank()) continue
                val point = LdrzCoord.toPoint(coord, boardSize)
                    ?: return "座標不合法：$coord"
                stones[point] = color
            }
            return null
        }
        addStones("AB", StoneColor.Black)?.let { return LdrzLoad.Err(it) }
        addStones("AW", StoneColor.White)?.let { return LdrzLoad.Err(it) }

        val turn = parseColor(dto.turn_color)
            ?: parsePl(props["PL"]?.firstOrNull())
            ?: StoneColor.Black

        val region = LdrzCoord.parseList(dto.region.orEmpty(), boardSize)
            ?: return LdrzLoad.Err("region 座標不合法")
        val blackCrucial = LdrzCoord.parseList(dto.black_crucial_stone.orEmpty(), boardSize)
            ?: return LdrzLoad.Err("black_crucial_stone 座標不合法")
        val whiteCrucial = LdrzCoord.parseList(dto.white_crucial_stone.orEmpty(), boardSize)
            ?: return LdrzLoad.Err("white_crucial_stone 座標不合法")

        val answer = dto.answer_firstmove?.trim()?.takeIf { it.isNotEmpty() }
        if (answer != null && LdrzCoord.toPoint(answer, boardSize) == null) {
            return LdrzLoad.Err("answer_firstmove 座標不合法")
        }

        val stem = stemFromFileName(sourceFileName)
        val sourceName = sourceFileName.substringAfterLast('/').substringAfterLast('\\')
            .ifBlank { "$stem.json" }

        return LdrzLoad.Ok(
            LdrzProblem(
                stem = stem,
                sourceJsonName = if (sourceName.endsWith(".json", ignoreCase = true)) sourceName else "$stem.json",
                filename = dto.filename.orEmpty(),
                category = dto.category.orEmpty(),
                turnColor = turn,
                winningColor = parseColor(dto.winning_color),
                blackCrucial = blackCrucial.toSet(),
                whiteCrucial = whiteCrucial.toSet(),
                blackGoal = parseGoal(dto.black_search_goal) ?: LdrzGoal.TOLIVE,
                whiteGoal = parseGoal(dto.white_search_goal) ?: LdrzGoal.TOKILL,
                answerFirstMove = answer,
                region = region.toSet(),
                stones = stones,
                boardSize = boardSize,
            ),
        )
    }
}

fun stemFromFileName(name: String): String {
    val base = name.substringAfterLast('/').substringAfterLast('\\')
    return when {
        base.endsWith(".json", ignoreCase = true) -> base.dropLast(5)
        base.contains('.') -> base.substringBeforeLast('.')
        base.isNotEmpty() -> base
        else -> "problem"
    }
}

@Serializable
private data class LdrzJsonDto(
    val filename: String? = null,
    val category: String? = null,
    val rawsgf: String? = null,
    val masked_sgf_str: String? = null,
    val turn_color: String? = null,
    val winning_color: String? = null,
    val black_crucial_stone: String? = null,
    val white_crucial_stone: String? = null,
    val black_search_goal: String? = null,
    val white_search_goal: String? = null,
    val black_ko_rule: String? = null,
    val white_ko_rule: String? = null,
    val answer_firstmove: String? = null,
    val region: String? = null,
)

internal fun parseSgfRoot(sgf: String): Map<String, List<String>> {
    val start = sgf.indexOf("(;")
    val body = if (start >= 0) sgf.substring(start + 2) else sgf
    val props = LinkedHashMap<String, MutableList<String>>()
    val keyBuf = StringBuilder()
    var lastKey = ""
    var i = 0
    while (i < body.length) {
        val c = body[i]
        when {
            c == ';' || c == ')' || c == '(' -> break
            c == '[' -> {
                val key = keyBuf.toString().trim().ifEmpty { lastKey }
                keyBuf.clear()
                lastKey = key
                val value = StringBuilder()
                i++
                while (i < body.length) {
                    val ch = body[i]
                    if (ch == '\\' && i + 1 < body.length) {
                        value.append(body[i + 1])
                        i += 2
                        continue
                    }
                    if (ch == ']') break
                    value.append(ch)
                    i++
                }
                if (key.isNotEmpty()) {
                    props.getOrPut(key) { mutableListOf() }.add(value.toString())
                }
            }
            c.isLetter() -> keyBuf.append(c)
        }
        i++
    }
    return props
}

private fun parseColor(raw: String?): StoneColor? = when (raw?.trim()?.lowercase()) {
    "b", "black" -> StoneColor.Black
    "w", "white" -> StoneColor.White
    else -> null
}

private fun parsePl(raw: String?): StoneColor? = when (raw?.trim()?.uppercase()) {
    "B", "BLACK" -> StoneColor.Black
    "W", "WHITE" -> StoneColor.White
    else -> null
}

private fun parseGoal(raw: String?): LdrzGoal? = when (raw?.trim()?.uppercase()) {
    "TOLIVE" -> LdrzGoal.TOLIVE
    "TOKILL" -> LdrzGoal.TOKILL
    else -> null
}
