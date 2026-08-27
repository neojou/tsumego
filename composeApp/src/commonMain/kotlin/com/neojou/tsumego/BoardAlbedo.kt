package com.neojou.tsumego

import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.ImageBitmap
import org.jetbrains.compose.resources.imageResource

data class BoardAlbedo(
    val wood: ImageBitmap,
    val white: ImageBitmap,
    val black: ImageBitmap,
)

/**
 * On Wasm, [imageResource] is async and yields a 1×1 placeholder on the first
 * frames. Callers must treat unread bitmaps as missing (see [com.neojou.tsumego.ui.albedoReady])
 * or the wood tiler will walk the 盤 one pixel at a time and freeze the tab.
 * Desktop loads synchronously and never sees the placeholder.
 */
@Composable
fun rememberBoardAlbedo(): BoardAlbedo = BoardAlbedo(
    wood = imageResource(Res.drawable.hinoki_albedo),
    white = imageResource(Res.drawable.stone_white),
    black = imageResource(Res.drawable.stone_black),
)
