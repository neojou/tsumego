package com.neojou.tsumego

import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.ImageBitmap
import org.jetbrains.compose.resources.imageResource

data class BoardAlbedo(
    val wood: ImageBitmap,
    val white: ImageBitmap,
    val black: ImageBitmap,
)

@Composable
fun rememberBoardAlbedo(): BoardAlbedo = BoardAlbedo(
    wood = imageResource(Res.drawable.hinoki_albedo),
    white = imageResource(Res.drawable.stone_white),
    black = imageResource(Res.drawable.stone_black),
)
