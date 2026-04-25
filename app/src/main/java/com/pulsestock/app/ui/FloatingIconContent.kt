package com.pulsestock.app.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.ShowChart
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
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
fun TrashZoneContent() {
    Box(
        contentAlignment = Alignment.Center,
        modifier = Modifier
            .size(72.dp)
            .background(Color(0xCCFF3B30), CircleShape)
    ) {
        Icon(
            imageVector        = Icons.Default.Delete,
            contentDescription = "Remove bubble",
            tint               = Color.White,
            modifier           = Modifier.size(32.dp)
        )
    }
}
