package com.example.mallar.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.mallar.ui.theme.Teal
import com.example.mallar.ui.theme.TextPrimary
import com.example.mallar.ui.theme.TextSecondary
import com.example.mallar.ui.theme.White

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    onBackClick: () -> Unit,
    onLogoutClick: () -> Unit
) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Settings", fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    IconButton(onClick = onBackClick) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = White,
                    titleContentColor = Teal,
                    navigationIconContentColor = Teal
                )
            )
        },
        containerColor = Color(0xFFF8F9FA)
    ) { padding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
        ) {
            // Profile Section
            item {
                ProfileSection()
            }

            // Settings Categories
            item {
                SectionHeader("Account")
                SettingsItem(Icons.Default.Person, "Edit Profile", "Change your name and info")
                SettingsItem(Icons.Default.Favorite, "Favorite Stores", "Your saved locations")
                SettingsItem(Icons.Default.History, "Navigation History", "Where you've been")
            }

            item {
                SectionHeader("Preferences")
                SettingsItem(Icons.Default.Notifications, "Notifications", "Alerts and updates")
                SettingsItem(Icons.Default.Language, "Language", "English (US)")
                SettingsItem(Icons.Default.DarkMode, "Dark Mode", "Current: System")
            }

            item {
                SectionHeader("Support")
                SettingsItem(Icons.Default.Help, "Help Center", "FAQs and guides")
                SettingsItem(Icons.Default.Info, "About MallAR", "Version 1.0.4")
            }

            item {
                Spacer(modifier = Modifier.height(32.dp))
                Button(
                    onClick = onLogoutClick,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 24.dp)
                        .height(56.dp),
                    shape = RoundedCornerShape(16.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFE53935))
                ) {
                    Icon(Icons.Default.Logout, contentDescription = null)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Logout", fontWeight = FontWeight.Bold, fontSize = 16.sp)
                }
                Spacer(modifier = Modifier.height(40.dp))
            }
        }
    }
}

@Composable
private fun ProfileSection() {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(24.dp)
            .background(White, RoundedCornerShape(24.dp))
            .padding(20.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(64.dp)
                .background(Teal.copy(alpha = 0.1f), CircleShape),
            contentAlignment = Alignment.Center
        ) {
            Icon(Icons.Default.AccountCircle, contentDescription = null, size(40.dp), tint = Teal)
        }
        Spacer(modifier = Modifier.width(16.dp))
        Column {
            Text("Ebrahim Ahmed", fontWeight = FontWeight.ExtraBold, fontSize = 20.sp, color = TextPrimary)
            Text("+20 1012345678", color = TextSecondary, fontSize = 14.sp)
        }
    }
}

private fun size(dp: androidx.compose.ui.unit.Dp): Modifier = Modifier.size(dp)

@Composable
private fun SectionHeader(title: String) {
    Text(
        text = title,
        modifier = Modifier.padding(start = 24.dp, top = 24.dp, bottom = 12.dp),
        color = Teal,
        fontWeight = FontWeight.Bold,
        fontSize = 14.sp
    )
}

@Composable
private fun SettingsItem(icon: ImageVector, title: String, subtitle: String) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 24.dp, vertical = 4.dp)
            .background(White, RoundedCornerShape(16.dp))
            .clickable { /* Handle click */ }
            .padding(16.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(40.dp)
                .background(Teal.copy(alpha = 0.05f), RoundedCornerShape(12.dp)),
            contentAlignment = Alignment.Center
        ) {
            Icon(icon, contentDescription = null, modifier = Modifier.size(20.dp), tint = Teal)
        }
        Spacer(modifier = Modifier.width(16.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(title, fontWeight = FontWeight.Bold, fontSize = 16.sp, color = TextPrimary)
            Text(subtitle, fontSize = 12.sp, color = TextSecondary)
        }
        Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, contentDescription = null, tint = TextSecondary.copy(alpha = 0.5f))
    }
}
