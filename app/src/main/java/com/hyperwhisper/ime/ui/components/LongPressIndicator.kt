package com.hyperwhisper.ui.components

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

private val LongPressDotColor = Color(0xFFFFB74D)

/**
 * Tiny orange dot in the top-right corner of a button — signals "this key
 * has a long-press affordance". Place inside a [Box] that fills the button's
 * bounds; the receiver scope handles alignment.
 */
@Composable
fun BoxScope.LongPressIndicator(
    padding: Dp = 3.dp,
    dotSize: Dp = 5.dp
) {
    Box(
        modifier = Modifier
            .align(Alignment.TopEnd)
            .padding(padding)
            .size(dotSize)
    ) {
        Surface(
            shape = RoundedCornerShape(2.dp),
            color = LongPressDotColor,
            modifier = Modifier.fillMaxSize()
        ) {}
    }
}
