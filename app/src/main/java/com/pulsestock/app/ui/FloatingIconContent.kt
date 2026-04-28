package com.pulsestock.app.ui

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.ShowChart
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.dp
import com.pulsestock.app.ui.theme.PulseGreen

@Composable
fun FloatingIconContent(isPressed: Boolean = false) {
    // Subtle idle breathing — draws the eye without being distracting
    val infiniteTransition = rememberInfiniteTransition(label = "breathe")
    val breatheScale by infiniteTransition.animateFloat(
        initialValue  = 1f,
        targetValue   = 1.045f,
        animationSpec = infiniteRepeatable(
            animation  = tween(2200, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "breathe_scale"
    )

    // Press: snap down fast (stiff spring, no bounce), release: bouncy overshoot
    val pressScale by animateFloatAsState(
        targetValue   = if (isPressed) 0.87f else 1f,
        animationSpec = if (isPressed)
            spring(stiffness = Spring.StiffnessHigh, dampingRatio = Spring.DampingRatioNoBouncy)
        else
            spring(stiffness = Spring.StiffnessLow, dampingRatio = Spring.DampingRatioLowBouncy),
        label = "press_scale"
    )

    // Shadow squishes down on press (tactile depth cue), bounces back on release
    val elevation by animateDpAsState(
        targetValue   = if (isPressed) 2.dp else 12.dp,
        animationSpec = if (isPressed)
            tween(55)
        else
            spring(stiffness = Spring.StiffnessLow, dampingRatio = Spring.DampingRatioLowBouncy),
        label = "elevation"
    )

    // Suppress breathing while pressed so the two animations don't fight
    val finalScale = if (isPressed) pressScale else breatheScale * pressScale

    val gradient = Brush.linearGradient(
        colors = listOf(Color(0xFF52FF7A), Color(0xFF00A040)),
        start  = Offset(0f, 0f),
        end    = Offset(Float.POSITIVE_INFINITY, Float.POSITIVE_INFINITY)
    )

    // Outer box is larger than the circle so shadow and scale-up don't get clipped
    // by the FrameLayout bounds (scale() affects drawing, not layout size).
    Box(
        contentAlignment = Alignment.Center,
        modifier = Modifier.size(62.dp)
    ) {
    Box(
        contentAlignment = Alignment.Center,
        modifier = Modifier
            .size(48.dp)
            .scale(finalScale)
            .shadow(
                elevation    = elevation,
                shape        = CircleShape,
                clip         = false,
                ambientColor = Color.Black.copy(alpha = 0.08f),
                spotColor    = PulseGreen.copy(alpha = 0.65f)
            )
            .clip(CircleShape)
            .background(brush = gradient)
    ) {
        Icon(
            imageVector        = Icons.Default.ShowChart,
            contentDescription = "PulseStock",
            tint               = Color.White,
            modifier           = Modifier.size(24.dp)
        )
        // Glass specular arc — the key detail that makes a flat circle feel like a 3D bubble
        Canvas(modifier = Modifier.matchParentSize()) {
            drawArc(
                color      = Color.White.copy(alpha = 0.30f),
                startAngle = -155f,
                sweepAngle = 120f,
                useCenter  = false,
                topLeft    = Offset(size.width * 0.20f, size.height * 0.09f),
                size       = Size(size.width * 0.62f, size.height * 0.50f),
                style      = Stroke(width = size.width * 0.10f, cap = StrokeCap.Round)
            )
        }
    } // inner 56dp circle
    } // outer 70dp layout box
}

@Composable
fun TrashZoneContent(isHovered: Boolean = false) {
    val circleSize by animateDpAsState(
        targetValue   = if (isHovered) 92.dp else 64.dp,
        animationSpec = spring(stiffness = Spring.StiffnessMedium, dampingRatio = Spring.DampingRatioMediumBouncy),
        label         = "trash_size"
    )
    val iconSize by animateDpAsState(
        targetValue   = if (isHovered) 38.dp else 26.dp,
        animationSpec = spring(stiffness = Spring.StiffnessMedium),
        label         = "trash_icon_size"
    )
    val bgColor by animateColorAsState(
        targetValue   = if (isHovered) Color(0xFFFF3B30) else Color(0xFF2C2C2E),
        animationSpec = tween(durationMillis = 160),
        label         = "trash_color"
    )
    val ringScale by animateFloatAsState(
        targetValue   = if (isHovered) 1f else 0f,
        animationSpec = spring(stiffness = Spring.StiffnessMediumLow, dampingRatio = Spring.DampingRatioMediumBouncy),
        label         = "ring_scale"
    )

    val pulse = rememberInfiniteTransition(label = "pulse")
    val pulseAlpha by pulse.animateFloat(
        initialValue  = 0.55f,
        targetValue   = 0f,
        animationSpec = infiniteRepeatable(
            animation  = tween(700, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "pulse_alpha"
    )
    val pulseScale by pulse.animateFloat(
        initialValue  = 1f,
        targetValue   = 1.55f,
        animationSpec = infiniteRepeatable(
            animation  = tween(700, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "pulse_scale"
    )

    Box(
        modifier         = Modifier.fillMaxSize(),
        contentAlignment = Alignment.BottomCenter
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier            = Modifier.padding(bottom = 30.dp)
        ) {
            Box(contentAlignment = Alignment.Center) {
                if (isHovered) {
                    Spacer(
                        Modifier
                            .size(circleSize)
                            .scale(pulseScale * ringScale)
                            .background(Color(0xFFFF3B30).copy(alpha = pulseAlpha), CircleShape)
                    )
                }

                Box(
                    contentAlignment = Alignment.Center,
                    modifier         = Modifier
                        .size(circleSize)
                        .shadow(
                            elevation    = if (isHovered) 20.dp else 6.dp,
                            shape        = CircleShape,
                            clip         = false,
                            ambientColor = if (isHovered) Color(0xFFFF3B30) else Color.Black,
                            spotColor    = if (isHovered) Color(0xFFFF3B30) else Color.Black
                        )
                        .background(bgColor, CircleShape)
                ) {
                    Icon(
                        imageVector        = Icons.Default.Delete,
                        contentDescription = "Remove bubble",
                        tint               = Color.White,
                        modifier           = Modifier.size(iconSize)
                    )
                }
            }

        }
    }
}
