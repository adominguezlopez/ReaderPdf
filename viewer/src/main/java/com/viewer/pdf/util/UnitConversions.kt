package com.viewer.pdf.util

import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.unit.IntRect
import kotlin.math.ceil
import kotlin.math.floor

fun Rect.toIntRect(): IntRect {
    return IntRect(
        left = floor(left).toInt(),
        top = floor(top).toInt(),
        right = ceil(right).toInt(),
        bottom = ceil(bottom).toInt(),
    )
}

fun Rect.scale(factor: Float): Rect {
    return Rect(
        left = floor(left * factor),
        top = floor(top * factor),
        right = ceil(right * factor),
        bottom = ceil(bottom * factor),
    )
}
