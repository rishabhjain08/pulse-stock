package com.pulsestock.app.ui.components

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.ContentTransform
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.Row
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableDoubleStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

private const val ANIM_DURATION_MS = 120

/**
 * Robinhood-style odometer: individual digits slide vertically on change.
 *
 * Direction:
 *   price UP   → new digit enters from below, old exits to top (forward roll)
 *   price DOWN → new digit enters from above, old exits to bottom (reverse roll)
 *   neutral    → crossfade only (first render, no direction yet)
 */
@Composable
fun OdometerText(
    price: Double,
    color: Color,
    style: TextStyle = TextStyle(fontSize = 18.sp, fontWeight = FontWeight.Bold)
) {
    // -1 = down, 0 = neutral, 1 = up
    var direction by remember { mutableIntStateOf(0) }
    var prevPrice by remember { mutableDoubleStateOf(price) }

    LaunchedEffect(price) {
        direction = when {
            price > prevPrice -> 1
            price < prevPrice -> -1
            else -> 0
        }
        prevPrice = price
    }

    val formatted = "%.2f".format(price)

    Row(verticalAlignment = Alignment.CenterVertically) {
        // Dollar sign — static, no animation needed
        Text(text = "$", style = style, color = color)

        formatted.forEachIndexed { index, char ->
            if (char == '.') {
                // Decimal separator never changes
                Text(text = ".", style = style, color = color)
            } else {
                OdometerDigit(
                    char  = char,
                    color = color,
                    style = style,
                    direction = direction,
                    key   = "digit_$index"
                )
            }
        }
    }
}

@Composable
private fun OdometerDigit(
    char: Char,
    color: Color,
    style: TextStyle,
    direction: Int,
    key: String
) {
    AnimatedContent(
        targetState = char,
        transitionSpec = { odometerTransition(direction) },
        label = key
    ) { targetChar ->
        Text(text = targetChar.toString(), style = style, color = color)
    }
}

private fun odometerTransition(direction: Int): ContentTransform {
    val duration = ANIM_DURATION_MS
    return when {
        direction > 0 -> {
            // Price UP: new digit slides in from bottom, old exits to top
            slideInVertically(tween(duration)) { it } + fadeIn(tween(duration)) togetherWith
                slideOutVertically(tween(duration)) { -it } + fadeOut(tween(duration))
        }
        direction < 0 -> {
            // Price DOWN: new digit slides in from top, old exits to bottom
            slideInVertically(tween(duration)) { -it } + fadeIn(tween(duration)) togetherWith
                slideOutVertically(tween(duration)) { it } + fadeOut(tween(duration))
        }
        else -> {
            // Neutral (first render): simple crossfade
            fadeIn(tween(duration)) togetherWith fadeOut(tween(duration))
        }
    }
}
