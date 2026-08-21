package com.neojou.tsumego.play

import com.neojou.tsumego.board.Point
import com.neojou.tsumego.board.StoneColor

enum class StoneSoundKind { Place, Capture }

data class StoneFx(
    val sounds: List<StoneSoundKind>,
    val drop: Point?,
    val retract: Map<Point, StoneColor>,
) {
    companion object {
        val None = StoneFx(emptyList(), null, emptyMap())
    }
}

fun stoneFx(
    before: Map<Point, StoneColor>,
    after: Map<Point, StoneColor>,
    lastMove: Point?,
    lastMoveIsPass: Boolean,
    undo: Boolean,
): StoneFx {
    if (undo || lastMoveIsPass) return StoneFx.None
    val retract = before.filter { (point, _) -> point !in after }
    val drop = lastMove?.takeIf { it in after }
    val sounds = buildList {
        if (drop != null) add(StoneSoundKind.Place)
        if (retract.isNotEmpty()) add(StoneSoundKind.Capture)
    }
    return StoneFx(sounds, drop, retract)
}

const val PLACE_DROP_MS = 150
const val CAPTURE_RETRACT_MS = 100
