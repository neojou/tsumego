package com.neojou.tsumego.solverLdrz

data class LdrzPickedFile(
    val name: String,
    val text: String,
)

expect suspend fun ldrzPickProblemJson(): LdrzPickedFile?

/** Write `result/result_<stem>.json` and `result/uct_tree_<stem>.sgf`. Null = ok, else message. */
expect suspend fun ldrzWriteResultFiles(stem: String, json: String, sgf: String): String?

expect suspend fun ldrzReadResultFiles(stem: String): Pair<String, String>?
