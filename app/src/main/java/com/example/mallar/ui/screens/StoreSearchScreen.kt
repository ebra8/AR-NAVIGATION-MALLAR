package com.example.mallar.ui.screens

import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.List
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.mallar.ui.theme.*
import kotlinx.coroutines.delay

data class StoreItem(
    val name: String,
    val initials: String,
    val color: Color,
    val distance: String,
    val walkTime: String
)

private val sampleStores = listOf(
    StoreItem("H&M", "HM", Color(0xFFE50010), "230m", "5min"),
    StoreItem("HP", "HP", Color(0xFF0096D6), "180m", "4min"),
    StoreItem("HomeCentre", "HC", Color(0xFFD4A34F), "350m", "7min"),
    StoreItem("Hollister", "Ho", Color(0xFF1C3A5F), "290m", "6min"),
    StoreItem("Hugo Boss", "HB", Color(0xFF1A1A1A), "410m", "8min")
)

@Composable
fun StoreSearchScreen(
    onStoreClick: () -> Unit,
    onBackClick: () -> Unit
) {
    var searchQuery by remember { mutableStateOf("") }
    val filteredStores = remember(searchQuery) {
        sampleStores.filter { it.name.contains(searchQuery, ignoreCase = true) }
    }

    Box(modifier = Modifier.fillMaxSize().background(SurfaceLight)) {
        // High-Fidelity Header (Glass Camera Overlay)
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(300.dp)
                .background(CameraPlaceholder)
        ) {
            // Animated Header Controls
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
                    color = White.copy(alpha = 0.9f)
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back", tint = Teal)
                    }
                }
                
                Surface(
                    modifier = Modifier
                        .height(44.dp)
                        .padding(horizontal = 4.dp),
                    shape = RoundedCornerShape(22.dp),
                    color = Color.Black.copy(alpha = 0.4f)
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 16.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(modifier = Modifier.size(8.dp).background(Color.Red, CircleShape))
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("LIVE", color = White, fontWeight = FontWeight.Bold, fontSize = 12.sp)
                    }
                }
            }
        }

        // Store Content Sheet
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(top = 220.dp)
                .background(
                    color = White,
                    shape = RoundedCornerShape(topStart = 32.dp, topEnd = 32.dp)
                )
        ) {
            // Premium Search Bar
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(24.dp)
            ) {
                OutlinedTextField(
                    value = searchQuery,
                    onValueChange = { searchQuery = it },
                    placeholder = { Text("Find store or brand...", color = TextSecondary.copy(alpha = 0.5f)) },
                    modifier = Modifier
                        .fillMaxWidth()
                        .shadow(12.dp, RoundedCornerShape(20.dp)),
                    leadingIcon = { Icon(Icons.Filled.Search, contentDescription = null, tint = Teal) },
                    shape = RoundedCornerShape(20.dp),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = Teal,
                        unfocusedBorderColor = Color.Transparent,
                        focusedContainerColor = SurfaceLight,
                        unfocusedContainerColor = SurfaceLight
                    ),
                    singleLine = true
                )
            }

            // Staggered Store List
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(start = 24.dp, end = 24.dp, bottom = 100.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                item {
                    Text(
                        "Featured Brands",
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Bold,
                        color = TextSecondary,
                        modifier = Modifier.padding(bottom = 4.dp)
                    )
                }
                
                itemsIndexed(filteredStores) { index, store ->
                    StoreCardPremium(
                        store = store,
                        index = index,
                        onClick = onStoreClick
                    )
                }
            }
        }

        // Animated Toggle FAB
        FloatingActionButton(
            onClick = { },
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(bottom = 32.dp)
                .size(width = 140.dp, height = 56.dp)
                .shadow(16.dp, RoundedCornerShape(28.dp)),
            containerColor = Teal,
            contentColor = White,
            shape = RoundedCornerShape(28.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Filled.List, contentDescription = null)
                Spacer(modifier = Modifier.width(8.dp))
                Text("View Map", fontWeight = FontWeight.Bold)
            }
        }
    }
}

@Composable
private fun StoreCardPremium(
    store: StoreItem,
    index: Int,
    onClick: () -> Unit
) {
    var isVisible by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) {
        delay(index * 100L)
        isVisible = true
    }

    AnimatedVisibility(
        visible = isVisible,
        enter = fadeIn() + slideInVertically { it / 3 }
    ) {
        Surface(
            onClick = onClick,
            modifier = Modifier
                .fillMaxWidth()
                .shadow(2.dp, RoundedCornerShape(24.dp)),
            shape = RoundedCornerShape(24.dp),
            color = White
        ) {
            Row(
                modifier = Modifier.padding(16.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Surface(
                    modifier = Modifier.size(64.dp),
                    shape = RoundedCornerShape(18.dp),
                    color = store.color.copy(alpha = 0.1f),
                    border = androidx.compose.foundation.BorderStroke(1.dp, store.color.copy(alpha = 0.2f))
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Text(
                            text = store.initials,
                            color = store.color,
                            fontWeight = FontWeight.ExtraBold,
                            fontSize = 20.sp
                        )
                    }
                }
                
                Spacer(modifier = Modifier.width(16.dp))
                
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = store.name,
                        fontWeight = FontWeight.ExtraBold,
                        fontSize = 18.sp,
                        color = TextPrimary
                    )
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Filled.LocationOn, null, tint = RedAccent, modifier = Modifier.size(14.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(text = store.distance, color = TextSecondary, fontSize = 13.sp)
                    }
                }
                
                Icon(Icons.Filled.ChevronRight, contentDescription = null, tint = DividerColor)
            }
        }
    }
}
