package com.neojou.tsumego.board

/**
 * A position on a 題目盤: stones, plus the hashes of earlier boards for 同型反覆.
 */
data class Position(
    val rect: BoardRect,
    val edges: Edges,
    val stones: Map<Point, StoneColor>,
    val past: Set<String>,
) {
    val key: String = stoneKey(rect, stones)

    val playable: List<Point> get() = rect.points

    fun neighbors(point: Point): List<Point> = buildList {
        if (point.file > rect.left) add(Point(point.file - 1, point.rank))
        if (point.file < rect.right) add(Point(point.file + 1, point.rank))
        if (point.rank > rect.bottom) add(Point(point.file, point.rank - 1))
        if (point.rank < rect.top) add(Point(point.file, point.rank + 1))
    }

    fun stringAt(point: Point, board: Map<Point, StoneColor> = stones): Set<Point> {
        val color = board[point] ?: return emptySet()
        val seen = LinkedHashSet<Point>()
        val stack = ArrayDeque<Point>()
        stack.add(point)
        while (stack.isNotEmpty()) {
            val cur = stack.removeLast()
            if (!seen.add(cur)) continue
            for (n in neighbors(cur)) {
                if (board[n] == color && n !in seen) stack.add(n)
            }
        }
        return seen
    }

    fun liberties(string: Set<Point>, board: Map<Point, StoneColor> = stones): Set<Point> {
        val libs = LinkedHashSet<Point>()
        for (p in string) {
            for (n in neighbors(p)) {
                if (n !in board) libs.add(n)
            }
        }
        return libs
    }

    fun legalMoves(toPlay: StoneColor): List<Point> =
        playable.filter { play(it, toPlay) != null }.sorted()

    /**
     * @return the next position, or null if the point is occupied, outside, suicide, or 同型反覆.
     */
    fun play(point: Point, toPlay: StoneColor): Position? = playInternal(point, toPlay, respectSuperko = true)

    fun playIgnoringSuperko(point: Point, toPlay: StoneColor): Position? =
        playInternal(point, toPlay, respectSuperko = false)

    private fun playInternal(point: Point, toPlay: StoneColor, respectSuperko: Boolean): Position? {
        if (!rect.contains(point)) return null
        if (point in stones) return null
        val placed = stones + (point to toPlay)
        val captured = linkedSetOf<Point>()
        val seenStrings = HashSet<Point>()
        for (n in neighbors(point)) {
            if (placed[n] != toPlay.opposite) continue
            if (n in seenStrings) continue
            val string = stringAt(n, placed)
            seenStrings.addAll(string)
            if (liberties(string, placed).isEmpty()) captured.addAll(string)
        }
        val after = if (captured.isEmpty()) placed else placed.filterKeys { it !in captured }
        val own = stringAt(point, after)
        if (liberties(own, after).isEmpty()) return null
        val nextKey = stoneKey(rect, after)
        if (respectSuperko && nextKey in past) return null
        return Position(rect, edges, after, past + nextKey)
    }

    fun isSimpleKoCapture(point: Point, toPlay: StoneColor): Boolean {
        val next = playIgnoringSuperko(point, toPlay) ?: return false
        val captured = stones.keys.filter { it !in next.stones }
        if (captured.size != 1) return false
        val own = next.stringAt(point)
        return own.size == 1 && next.liberties(own).size == 1
    }

    fun simpleKoCaptures(toPlay: StoneColor): List<Point> =
        playable.filter { stones[it] == null && isSimpleKoCapture(it, toPlay) }.sorted()

    companion object {
        fun initial(problem: Problem): Position {
            val key = stoneKey(problem.rect, problem.stones)
            return Position(problem.rect, problem.edges, problem.stones, setOf(key))
        }

        fun stoneKey(rect: BoardRect, stones: Map<Point, StoneColor>): String = buildString {
            for (file in rect.files) {
                for (rank in rect.ranks) {
                    append(
                        when (stones[Point(file, rank)]) {
                            StoneColor.Black -> 'X'
                            StoneColor.White -> 'O'
                            null -> '.'
                        },
                    )
                }
            }
        }
    }
}
