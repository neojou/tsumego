package com.neojou.tsumego.solverLdrz

data class LdrzSession(
    val problem: LdrzProblem? = null,
    val calculating: Boolean = false,
    val result: LdrzResult? = null,
    val outputJson: String? = null,
    val outputSgf: String? = null,
    val showText: String? = null,
) {
    val menuEnabled: Boolean get() = !calculating
}
