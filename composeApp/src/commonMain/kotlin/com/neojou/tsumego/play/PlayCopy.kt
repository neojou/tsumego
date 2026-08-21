package com.neojou.tsumego.play

import com.neojou.tsumego.board.Goal

fun Goal.playHeading(): String = when (this) {
    Goal.Live -> "黑先做活"
    Goal.Kill -> "黑先殺白"
    Goal.Seki -> "黑先雙活題"
    Goal.KoLive -> "黑先劫活"
    Goal.KoKill -> "黑先劫殺"
}

fun numberedSearchPaths(paths: List<String>): List<String> =
    paths.mapIndexed { index, line -> "${index + 1}. $line" }

fun thinkingSeconds(elapsedMs: Long): Long = (elapsedMs / 1000L).coerceAtLeast(0L)
