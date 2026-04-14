package com.example.mallar.ui.screens

import androidx.compose.animation.core.*
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.scale
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.mallar.R
import com.example.mallar.ui.theme.*
import kotlinx.coroutines.delay

@Composable
fun SplashScreen(onStartClick: () -> Unit) {
    // Animation States
    val logoScale = remember { Animatable(0.7f) }
    val logoAlpha = remember { Animatable(0f) }
    val contentAlpha = remember { Animatable(0f) }

    LaunchedEffect(Unit) {
        // Logo Entrance
        logoAlpha.animateTo(1f, animationSpec = tween(1000))
        logoScale.animateTo(1f, animationSpec = spring(dampingRatio = 0.5f, stiffness = Spring.StiffnessLow))
        
        // Background elements entrance
        delay(200)
        contentAlpha.animateTo(1f, animationSpec = tween(800))
    }

    Box(modifier = Modifier.fillMaxSize()) {
        // Hero Background
        Image(
            painter = painterResource(id = R.drawable.background),
            contentDescription = null,
            contentScale = ContentScale.Crop,
            modifier = Modifier.fillMaxSize()
        )

        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 32.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Spacer(modifier = Modifier.weight(1.2f))

            // Animated Logo
            Image(
                painter = painterResource(id = R.drawable.logo_main),
                contentDescription = "Logo",
                contentScale = ContentScale.FillWidth,
                modifier = Modifier
                    .fillMaxWidth(0.65f)
                    .scale(logoScale.value)
                    .alpha(logoAlpha.value)
            )

            Spacer(modifier = Modifier.weight(1f))

            // Animated Start button area
            Column(
                modifier = Modifier.alpha(contentAlpha.value),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Button(
                    onClick = onStartClick,
                    modifier = Modifier
                        .fillMaxWidth(0.8f)
                        .height(64.dp)
                        .shadow(16.dp, RoundedCornerShape(32.dp)),
                    shape = RoundedCornerShape(32.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = White,
                        contentColor = Teal
                    ),
                    elevation = ButtonDefaults.buttonElevation(defaultElevation = 0.dp)
                ) {
                    Text(
                        text = "Get Started",
                        fontSize = 20.sp,
                        fontWeight = FontWeight.ExtraBold,
                        letterSpacing = 0.5.sp
                    )
                }

                Spacer(modifier = Modifier.height(32.dp))

                // Modern Page indicators
                Row(
                    horizontalArrangement = Arrangement.Center,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(modifier = Modifier.size(width = 24.dp, height = 8.dp).background(White, CircleShape))
                    Spacer(modifier = Modifier.width(8.dp))
                    Box(modifier = Modifier.size(8.dp).background(White.copy(alpha = 0.4f), CircleShape))
                    Spacer(modifier = Modifier.width(8.dp))
                    Box(modifier = Modifier.size(8.dp).background(White.copy(alpha = 0.4f), CircleShape))
                }
            }

            Spacer(modifier = Modifier.height(64.dp).navigationBarsPadding())
        }
    }
}
