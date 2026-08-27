package com.neojou.tsumego.play

import com.neojou.tsumego.solve.Action
import com.neojou.tsumego.solve.plyLabel

data class DecisionTreeLine(
    val indent: Int,
    val text: String,
)

data class DecisionTreeView(
    val pathCount: Int,
    val leafCount: Int,
    val lines: List<DecisionTreeLine>,
) {
    companion object {
        val Empty = DecisionTreeView(0, 0, emptyList())
    }
}

class DecisionTreeProjection(
    private val leafLimit: Int = 1000,
) {
    var pathCount: Int = 0
        private set

    private val whiteOrder = ArrayList<Action>()
    private val blackOrder = LinkedHashMap<Action, ArrayList<Action?>>()
    private val continuations = LinkedHashMap<Pair<Action, Action?>, String>()

    fun notePath() {
        pathCount++
    }

    fun show(white: Action, black: Action?, continuation: String, replace: Boolean) {
        val key = white to black
        if (key in continuations) {
            if (replace) continuations[key] = continuation
            return
        }
        if (continuations.size >= leafLimit) return
        if (white !in whiteOrder) {
            whiteOrder.add(white)
            blackOrder[white] = ArrayList()
        }
        blackOrder.getValue(white).add(black)
        continuations[key] = continuation
    }

    fun view(): DecisionTreeView {
        val lines = ArrayList<DecisionTreeLine>()
        for (white in whiteOrder) {
            lines += DecisionTreeLine(0, plyLabel(white, blackToPlay = false))
            for (black in blackOrder.getValue(white)) {
                val continuation = continuations.getValue(white to black)
                if (black == null) {
                    lines += DecisionTreeLine(1, continuation)
                } else {
                    lines += DecisionTreeLine(1, plyLabel(black, blackToPlay = true))
                    lines += DecisionTreeLine(2, continuation)
                }
            }
        }
        return DecisionTreeView(pathCount, continuations.size, lines)
    }
}

fun decisionTreeTitle(): String = "決策樹"
