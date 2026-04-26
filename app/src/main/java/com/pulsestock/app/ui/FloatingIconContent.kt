package com.pulsestock.app.ui

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
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
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.pulsestock.app.ui.theme.PulseGreen

@Composable
fun FloatingIconContent() {
    Box(
        contentAlignment = Alignment.Center,
        modifier = Modifier
            .size(56.dp)
            .shadow(elevation = 6.dp, shape = CircleShape, clip = false)
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
        targetValue    = if (isHovered) 76.dp else 60.dp,
        animationSpec  = spring(stiffness = Spring.StiffnessMedium, dampingRatio = Spring.DampingRatioMediumBouncy),
        label          = "trash_size"
    )
    val iconSize by animateDpAsState(
        targetValue   = if (isHovered) 30.dp else 24.dp,
        animationSpec = spring(stiffness = Spring.StiffnessMedium),
        label         = "trash_icon_size"
    )
    val bgColor by animateColorAsState(
        targetValue   = if (isHovered) Color(0xFFFF3B30) else Color(0xFF1C1C1E),
        animationSpec = tween(durationMillis = 180),
        label         = "trash_color"
    )

    // fillMaxSize so the circle is centered within the MATCH_PARENT overlay window
    Box(
        modifier          = Modifier.fillMaxSize(),
        contentAlignment  = Alignment.Center
    ) {
        Box(
            contentAlignment = Alignment.Center,
            modifier         = Modifier
                .size(circleSize)
                .background(bgColor.copy(alpha = 0.9f), CircleShape)
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
