package com.neojou.tsumego.board

/**
 * Intersection on the 19-road board.
 *
 * [file] is 0–18 for A–T skipping I; [rank] is 1–19. A1 is the lower-left corner.
 */
data class Point(val file: Int, val rank: Int) : Comparable<Point> {
    init {
        require(file in 0..18) { "file $file" }
        require(rank in 1..19) { "rank $rank" }
    }

    val fileChar: Char get() = FILE_CHARS[file]

    val label: String get() = "$fileChar$rank"

    override fun compareTo(other: Point): Int {
        val byFile = file.compareTo(other.file)
        return if (byFile != 0) byFile else rank.compareTo(other.rank)
    }

    override fun toString(): String = label

    companion object {
        val FILE_CHARS: CharArray =
            charArrayOf('A', 'B', 'C', 'D', 'E', 'F', 'G', 'H', 'J', 'K', 'L', 'M', 'N', 'O', 'P', 'Q', 'R', 'S', 'T')

        fun fileIndex(ch: Char): Int? {
            val u = ch.uppercaseChar()
            val i = FILE_CHARS.indexOf(u)
            return i.takeIf { it >= 0 }
        }

        fun parse(label: String): Point? {
            val trimmed = label.trim()
            if (trimmed.length < 2) return null
            val file = fileIndex(trimmed[0]) ?: return null
            val rank = trimmed.substring(1).toIntOrNull() ?: return null
            if (rank !in 1..19) return null
            return Point(file, rank)
        }

        fun parseOrThrow(label: String): Point =
            parse(label) ?: throw IllegalArgumentException("座標不合法：$label")
    }
}

enum class StoneColor {
    Black,
    White,
    ;

    val opposite: StoneColor
        get() = if (this == Black) White else Black
}

enum class EdgeKind {
    Real,
    Wall,
}

data class BoardRect(
    val left: Int,
    val right: Int,
    val bottom: Int,
    val top: Int,
) {
    init {
        require(left in 0..18 && right in 0..18 && left <= right) { "files $left..$right" }
        require(bottom in 1..19 && top in 1..19 && bottom <= top) { "ranks $bottom..$top" }
    }

    fun contains(point: Point): Boolean =
        point.file in left..right && point.rank in bottom..top

    val points: List<Point>
        get() = buildList {
            for (file in left..right) {
                for (rank in bottom..top) {
                    add(Point(file, rank))
                }
            }
        }

    val files: IntRange get() = left..right
    val ranks: IntRange get() = bottom..top
}

data class Edges(
    val left: EdgeKind,
    val right: EdgeKind,
    val bottom: EdgeKind,
    val top: EdgeKind,
)
