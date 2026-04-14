package com.example.mallar.ui.screens

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.mallar.R
import com.example.mallar.ui.theme.*

@Composable
fun ArNavigationScreen(
    onBackClick: () -> Unit
) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(CameraPlaceholder)
    ) {
        // --- Full Screen AR Camera Placeholder ---
        Image(
            painter = painterResource(id = R.drawable.background),
            contentDescription = null,
            contentScale = ContentScale.Crop,
            modifier = Modifier.fillMaxSize().alpha(0.8f)
        )

        // --- AR Arrows Placeholder ---
        Image(
            painter = painterResource(id = R.drawable.ic_nav_arrow),
            contentDescription = "AR Arrows",
            modifier = Modifier
                .align(Alignment.Center)
                .padding(top = 100.dp)
                .size(280.dp),
            contentScale = ContentScale.Fit
        )

        // --- Top Bar (Standardized Icons) ---
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .statusBarsPadding()
                .padding(16.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Surface(
                onClick = onBackClick,
                modifier = Modifier.size(56.dp),
                shape = CircleShape,
                color = White
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back", tint = Teal)
                }
            }

            Surface(
                modifier = Modifier.size(56.dp),
                shape = CircleShape,
                color = White
            ) {
                Box(contentAlignment = Alignment.Center) {
                    // Using standard Search icon instead of CenterFocusWeak
                    Icon(Icons.Filled.Search, contentDescription = "Scan", tint = Teal)
                }
            }
        }

        // --- Floating Destination Card ---
        Surface(
            modifier = Modifier
                .align(Alignment.TopCenter)
                .padding(top = 132.dp, start = 20.dp, end = 20.dp)
                .fillMaxWidth()
                .height(115.dp)
                .shadow(24.dp, RoundedCornerShape(32.dp)),
            shape = RoundedCornerShape(32.dp),
            color = White
        ) {
            Row(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(16.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Large Brand Logo Area
                Surface(
                    modifier = Modifier.size(80.dp),
                    shape = RoundedCornerShape(20.dp),
                    color = White,
                    border = androidx.compose.foundation.BorderStroke(1.dp, SurfaceLight)
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Text(
                            text = "H&M", 
                            fontWeight = FontWeight.ExtraBold, 
                            color = Color(0xFFE50010), 
                            fontSize = 22.sp
                        )
                    }
                }

                Spacer(modifier = Modifier.width(16.dp))

                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "H&M", 
                        fontWeight = FontWeight.ExtraBold, 
                        fontSize = 20.sp, 
                        color = TextPrimary
                    )
                    Spacer(modifier = Modifier.height(6.dp))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Filled.LocationOn, null, tint = RedAccent, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("200m", color = TextSecondary, fontSize = 14.sp, fontWeight = FontWeight.Bold)
                        
                        Spacer(modifier = Modifier.width(12.dp))
                        
                        // Using standard Info icon instead of AccessTime
                        Icon(Icons.Filled.Info, null, tint = RedAccent, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("3min", color = TextSecondary, fontSize = 14.sp, fontWeight = FontWeight.Bold)
                    }
                }

                // X Close Button
                Box(
                    modifier = Modifier
                        .align(Alignment.Top)
                        .padding(top = 4.dp)
                ) {
                    Surface(
                        onClick = { },
                        modifier = Modifier.size(36.dp),
                        shape = CircleShape,
                        color = SurfaceLight
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Icon(Icons.Default.Close, contentDescription = "Close", tint = TextSecondary, modifier = Modifier.size(16.dp))
                        }
                    }
                }
            }
        }

        // --- Bottom Navigation Pill & Footer ---
        Column(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .navigationBarsPadding()
                .padding(bottom = 24.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // Main Guidance Pill
            Surface(
                modifier = Modifier
                    .width(280.dp)
                    .height(80.dp)
                    .shadow(16.dp, RoundedCornerShape(40.dp), ambientColor = Color.Red, spotColor = Color.Red),
                shape = RoundedCornerShape(40.dp),
                color = Teal
            ) {
                Row(
                    modifier = Modifier.fillMaxSize(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.Center
                ) {
                    // Using standard Refresh icon instead of NearMe
                    Icon(Icons.Default.Refresh, contentDescription = null, tint = White, modifier = Modifier.size(28.dp))
                    Spacer(modifier = Modifier.width(12.dp))
                    Text(
                        text = "LEFT: 50m",
                        color = Color.White,
                        fontSize = 22.sp,
                        fontWeight = FontWeight.ExtraBold
                    )
                }
            }

            Spacer(modifier = Modifier.height(24.dp))

            // Footer Text
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = "Show Road",
                    color = TextPrimary,
                    fontSize = 24.sp,
                    fontWeight = FontWeight.Medium
                )
                Spacer(modifier = Modifier.width(12.dp))
                // Using standard ArrowForward instead of ArrowRightAlt
                Icon(Icons.AutoMirrored.Filled.ArrowForward, contentDescription = null, tint = Teal, modifier = Modifier.size(32.dp))
            }
        }
    }
}
