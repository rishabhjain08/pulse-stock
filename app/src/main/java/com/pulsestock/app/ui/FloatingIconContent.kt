package com.pulsestock.app.ui

import androidx.compose.animation.animateColorAsState
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
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.ShowChart
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.scale
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.pulsestock.app.ui.theme.PulseGreen

@Composable
fun FloatingIconContent() {
    Box(
        contentAlignment = Alignment.Center,
        modifier = Modifier
            .size(56.dp)
            .shadow(elevation = 8.dp, shape = CircleShape, clip = false)
            .background(PulseGreen, CircleShape)
    ) {
        Icon(
            imageVector        = Icons.Default.ShowChart,
            contentDescription = "PulseStock",
            tint               = Color.White,
            modifier           = Modifier.size(28.dp)
        )
    }
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
    val labelAlpha by animateFloatAsState(
        targetValue   = if (isHovered) 1f else 0f,
        animationSpec = tween(durationMillis = 180),
        label         = "label_alpha"
    )
    val ringScale by animateFloatAsState(
        targetValue   = if (isHovered) 1f else 0f,
        animationSpec = spring(stiffness = Spring.StiffnessMediumLow, dampingRatio = Spring.DampingRatioMediumBouncy),
        label         = "ring_scale"
    )

    // Pulse ring animation when hovered
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
            horizontalAlignment = androidx.compose.ui.Alignment.CenterHorizontally,
            modifier            = Modifier.padding(bottom = 20.dp)
        ) {
            // Circle + pulse ring stacked
            Box(contentAlignment = Alignment.Center) {
                // Pulsing ring (only when hovered)
                if (isHovered) {
                    Spacer(
                        Modifier
                            .size(circleSize)
                            .scale(pulseScale * ringScale)
                            .background(Color(0xFFFF3B30).copy(alpha = pulseAlpha), CircleShape)
                    )
                }

                // Main circle
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

            // "Release to dismiss" label fades in below the circle
            Spacer(Modifier.height(8.dp))
            Text(
                text       = "Release to dismiss",
                color      = Color.White.copy(alpha = labelAlpha),
                fontSize   = 12.sp,
                fontWeight = FontWeight.Medium,
                modifier   = Modifier
                    .background(Color(0x99000000), RoundedCornerShape(8.dp))
                    .padding(horizontal = 10.dp, vertical = 4.dp)
            )
        }
    }
}
