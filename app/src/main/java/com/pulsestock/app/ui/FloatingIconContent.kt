package com.pulsestock.app.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ShowChart
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.pulsestock.app.ui.theme.PulseGreen

@Composable
fun FloatingIconContent(showHint: Boolean) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
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
        AnimatedVisibility(
            visible = showHint,
            enter   = fadeIn(),
            exit    = fadeOut()
        ) {
            Text(
                text       = "Hold to stop",
                fontSize   = 9.sp,
                fontWeight = FontWeight.Medium,
                color      = Color.White,
                modifier   = Modifier
                    .padding(top = 4.dp)
                    .background(Color(0xAA000000), RoundedCornerShape(4.dp))
                    .padding(horizontal = 6.dp, vertical = 2.dp)
            )
        }
    }
}
