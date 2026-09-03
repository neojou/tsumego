package com.neojou.tsumego.solverLdrz

import com.neojou.tsumego.board.Point

/**
 * SGF 兩字母（a 起算、含 i、原點左上）↔ 詰碁座標（A–T 跳 I，A1 左下）。
 */
object LdrzCoord {
    fun toPoint(sgf: String, boardSize: Int = 19): Point? {
        val s = sgf.trim().lowercase()
        if (s.length != 2) return null
        if (boardSize !in 1..19) return null
        val file = s[0] - 'a'
        val fromTop = s[1] - 'a'
        if (file !in 0 until boardSize) return null
        if (fromTop !in 0 until boardSize) return null
        val rank = boardSize - fromTop
        if (file !in 0..18 || rank !in 1..19) return null
        return Point(file, rank)
    }

    fun toSgf(point: Point, boardSize: Int = 19): String {
        val fileChar = ('a'.code + point.file).toChar()
        val fromTop = boardSize - point.rank
        val rankChar = ('a'.code + fromTop).toChar()
        return "$fileChar$rankChar"
    }

    /**
     * Comma / space separated SGF points. Blank → empty list.
     * Any illegal token → null (caller must reject the whole problem).
     */
    fun parseList(text: String, boardSize: Int = 19): List<Point>? {
        if (text.isBlank()) return emptyList()
        val out = ArrayList<Point>()
        for (part in text.split(',', ';', ' ', '\t', '\n')) {
            val token = part.trim()
            if (token.isEmpty()) continue
            out.add(toPoint(token, boardSize) ?: return null)
        }
        return out
    }
}
