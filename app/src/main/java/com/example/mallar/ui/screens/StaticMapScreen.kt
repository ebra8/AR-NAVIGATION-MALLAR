package com.example.mallar.ui.screens

import android.graphics.BitmapFactory
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.MyLocation
import androidx.compose.material.icons.filled.Place
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.mallar.data.MallGraphRepository
import com.example.mallar.ui.theme.Teal
import com.example.mallar.ui.theme.White
import com.example.mallar.ui.theme.RedAccent

@Composable
fun StaticMapScreen(onBackClick: () -> Unit) {
    val context = LocalContext.current
    val mapBitmap = remember {
        context.assets.open("map.png").use {
            BitmapFactory.decodeStream(it).asImageBitmap()
        }
    }

    val pathData = NavigationState.aStarPath
    val mallGraph = remember { MallGraphRepository.load(context) }
    val nodeMap = remember(mallGraph) { mallGraph.nodes.associateBy { it.id } }

    var scale by remember { mutableStateOf(1f) }
    var offset by remember { mutableStateOf(Offset.Zero) }

    Box(modifier = Modifier.fillMaxSize().background(Color(0xFF121212))) {
        Canvas(
            modifier = Modifier
                .fillMaxSize()
                .pointerInput(Unit) {
                    detectTransformGestures { _, pan, zoom, _ ->
                        scale = (scale * zoom).coerceIn(0.5f, 5f)
                        offset += pan
                    }
                }
        ) {
            withTransform({
                translate(offset.x, offset.y)
                scale(scale, scale, pivot = Offset.Zero)
            }) {
                // Draw map
                drawImage(image = mapBitmap)

                // Draw A* path
                pathData?.let { aStarPath ->
                    if (aStarPath.nodeIds.size >= 2) {
                        val path = Path()
                        val firstNode = nodeMap[aStarPath.nodeIds.first()]
                        if (firstNode != null) {
                            path.moveTo(firstNode.x.toFloat(), firstNode.y.toFloat())
                            for (i in 1 until aStarPath.nodeIds.size) {
                                val node = nodeMap[aStarPath.nodeIds[i]]
                                if (node != null) {
                                    path.lineTo(node.x.toFloat(), node.y.toFloat())
                                }
                            }
                            drawPath(
                                path = path,
                                color = Teal,
                                style = Stroke(width = 8f / scale, cap = androidx.compose.ui.graphics.StrokeCap.Round, join = androidx.compose.ui.graphics.StrokeJoin.Round)
                            )

                            // Start point marker
                            drawCircle(
                                color = Teal,
                                radius = 12f / scale,
                                center = Offset(firstNode.x.toFloat(), firstNode.y.toFloat())
                            )

                            // End point marker
                            val lastNode = nodeMap[aStarPath.nodeIds.last()]
                            if (lastNode != null) {
                                drawCircle(
                                    color = RedAccent,
                                    radius = 12f / scale,
                                    center = Offset(lastNode.x.toFloat(), lastNode.y.toFloat())
                                )
                            }
                        }
                    }
                }
            }
        }

        // Top UI Overlay
        Column(modifier = Modifier.fillMaxWidth()) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .statusBarsPadding()
                    .padding(16.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Surface(
                    onClick = onBackClick,
                    modifier = Modifier.size(48.dp),
                    shape = CircleShape,
                    color = Color.Black.copy(alpha = 0.6f)
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back", tint = White)
                    }
                }
                Spacer(modifier = Modifier.width(16.dp))
                Surface(
                    shape = RoundedCornerShape(24.dp),
                    color = Color.Black.copy(alpha = 0.6f)
                ) {
                    Text(
                        "Route Map",
                        color = White,
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(horizontal = 20.dp, vertical = 10.dp)
                    )
                }
            }
        }

        // Bottom Info Card
        Surface(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(16.dp)
                .fillMaxWidth()
                .navigationBarsPadding(),
            shape = RoundedCornerShape(24.dp),
            color = Color.Black.copy(alpha = 0.85f),
            border = androidx.compose.foundation.BorderStroke(1.dp, White.copy(alpha = 0.1f))
        ) {
            Column(modifier = Modifier.padding(20.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.MyLocation, contentDescription = null, tint = Teal, modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        "From: ${NavigationState.startPlace?.brand ?: "Current Location"}",
                        color = White, fontSize = 14.sp, fontWeight = FontWeight.Medium
                    )
                }
                Spacer(modifier = Modifier.height(8.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.Place, contentDescription = null, tint = RedAccent, modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        "To: ${NavigationState.selectedPlace?.brand ?: "Destination"}",
                        color = White, fontSize = 14.sp, fontWeight = FontWeight.Medium
                    )
                }
                Spacer(modifier = Modifier.height(12.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        "Distance: ${NavigationState.estimatedDistance}m",
                        color = White.copy(alpha = 0.7f), fontSize = 13.sp
                    )
                    Text(
                        "Est. Time: ${NavigationState.estimatedMinutes} min",
                        color = White.copy(alpha = 0.7f), fontSize = 13.sp
                    )
                }
            }
        }
        
        // Help text
        Text(
            "Pinch to zoom • Drag to pan",
            color = White.copy(alpha = 0.5f),
            fontSize = 12.sp,
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(bottom = 150.dp)
        )
    }
}
