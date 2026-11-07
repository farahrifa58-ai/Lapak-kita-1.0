package com.example.ui.screens

import androidx.compose.animation.core.*
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.R
import com.example.ui.theme.EmeraldDark
import com.example.ui.theme.EmeraldPrimary
import kotlinx.coroutines.delay

@Composable
fun SplashScreen(
    onTimeout: () -> Unit
) {
    LaunchedEffect(Unit) {
        delay(2000)
        onTimeout()
    }

    val infiniteTransition = rememberInfiniteTransition(label = "pulse")
    val scale by infiniteTransition.animateFloat(
        initialValue = 0.96f,
        targetValue = 1.04f,
        animationSpec = infiniteRepeatable(
            animation = tween(1200, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "scale"
    )

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(
                Brush.verticalGradient(
                    colors = listOf(
                        Color(0xFF0F4724), // Hijau tua elegan
                        Color(0xFF1E703A), // Hijau lapak
                        Color(0xFFDFF0E4), // Hijau muda lembut
                        Color(0xFFFFFFFF), // Putih bersih
                        Color(0xFFFFFFFF)
                    )
                )
            ),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
            modifier = Modifier
                .padding(24.dp)
                .scale(scale)
        ) {
            // Logo Container
            Box(
                modifier = Modifier
                    .size(130.dp)
                    .shadow(16.dp, CircleShape)
                    .clip(CircleShape)
                    .background(Color.White)
                    .border(3.dp, EmeraldPrimary, CircleShape),
                contentAlignment = Alignment.Center
            ) {
                Image(
                    painter = painterResource(id = R.drawable.ic_lapak_logo),
                    contentDescription = "Logo Lapak Kita",
                    modifier = Modifier
                        .size(116.dp)
                        .clip(CircleShape)
                )
            }

            Spacer(Modifier.height(20.dp))

            // App Name
            Text(
                text = "Lapak Kita",
                fontSize = 32.sp,
                fontWeight = FontWeight.ExtraBold,
                color = EmeraldDark,
                letterSpacing = 1.sp
            )

            Spacer(Modifier.height(4.dp))

            // Tagline
            Text(
                text = "Manajemen Lapak Titipan & Kasir",
                fontSize = 14.sp,
                fontWeight = FontWeight.Medium,
                color = Color(0xFF2C5E3B)
            )

            Spacer(Modifier.height(14.dp))

            // Developer Badge: "by Rafline Dev"
            Surface(
                shape = RoundedCornerShape(20.dp),
                color = EmeraldPrimary,
                shadowElevation = 4.dp
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "by Rafline Dev",
                        color = Color.White,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 0.5.sp
                    )
                }
            }

            Spacer(Modifier.height(36.dp))

            // Smooth loading indicator
            CircularProgressIndicator(
                modifier = Modifier.size(28.dp),
                color = EmeraldPrimary,
                strokeWidth = 3.dp
            )
        }

        // Bottom version footer
        Text(
            text = "Versi 1.0 • Sistem Internal Lapak",
            fontSize = 11.sp,
            color = Color(0xFF557760),
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(bottom = 28.dp)
        )
    }
}
