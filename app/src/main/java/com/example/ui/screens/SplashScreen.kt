package com.example.ui.screens

import androidx.compose.animation.core.*
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.R
import com.example.ui.components.neuExtruded
import kotlinx.coroutines.delay

@Composable
fun SplashScreen(
    onTimeout: () -> Unit
) {
    LaunchedEffect(Unit) {
        delay(1300)
        onTimeout()
    }

    val infiniteTransition = rememberInfiniteTransition(label = "splash_pulse")
    val scale by infiniteTransition.animateFloat(
        initialValue = 0.98f,
        targetValue = 1.025f,
        animationSpec = infiniteRepeatable(
            animation = tween(1100, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "logo_scale"
    )

    // Soft gray blended with crisp white gradient
    val splashGradient = Brush.verticalGradient(
        colors = listOf(
            Color(0xFFDFE4E3),
            Color(0xFFF2F4F3),
            Color(0xFFFFFFFF),
            Color(0xFFE9EDEC)
        )
    )

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(splashGradient)
            .windowInsetsPadding(WindowInsets.systemBars),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
            modifier = Modifier
                .padding(horizontal = 32.dp)
                .scale(scale)
        ) {
            // Centered new Lapak Kita logo inside a raised Soft UI frame
            Box(
                modifier = Modifier
                    .size(164.dp)
                    .neuExtruded(
                        cornerRadius = 32.dp,
                        shadowOffset = 8.dp,
                        blurRadius = 16.dp,
                        surfaceColor = Color(0xFF194220),
                        lightShadowColor = Color(0xFFFFFFFF),
                        darkShadowColor = Color(0xFFC5CECB)
                    )
                    .clip(RoundedCornerShape(32.dp)),
                contentAlignment = Alignment.Center
            ) {
                Image(
                    painter = painterResource(id = R.drawable.ic_lapak_logo),
                    contentDescription = "Logo Lapak Kita",
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize()
                )
            }

            Spacer(Modifier.height(26.dp))

            Text(
                text = "By Rafline Dev",
                fontSize = 18.sp,
                fontWeight = FontWeight.Bold,
                color = Color(0xFF2D3A35),
                letterSpacing = 0.8.sp
            )

            Spacer(Modifier.height(28.dp))

            CircularProgressIndicator(
                modifier = Modifier.size(24.dp),
                color = Color(0xFF194220),
                strokeWidth = 2.6.dp
            )
        }
    }
}
