package com.example.mallar.ui.screens

import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.mallar.R
import com.example.mallar.ui.theme.Teal
import com.example.mallar.ui.theme.White
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat

@Composable
fun WelcomeScreen(
    onPhoneAuthClick: () -> Unit,
    onSkipClick: () -> Unit
) {
    val context = LocalContext.current
    val view = LocalView.current
    val scope = rememberCoroutineScope()
    var backPressedTime by remember { mutableLongStateOf(0L) }

    // Ensure dark icons on white background
    SideEffect {
        val window = (context as? android.app.Activity)?.window
        if (window != null) {
            WindowCompat.getInsetsController(window, view).isAppearanceLightStatusBars = true
        }
    }

    BackHandler {
        val currentTime = System.currentTimeMillis()
        if (currentTime - backPressedTime < 2000) {
            // Exit app
            (context as? android.app.Activity)?.finish()
        } else {
            backPressedTime = currentTime
            Toast.makeText(context, "Press back again to exit", Toast.LENGTH_SHORT).show()
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.White)
    ) {
        // Decorative background element (optional, based on dekor.png)
        Image(
            painter = painterResource(id = R.drawable.dekor),
            contentDescription = null,
            modifier = Modifier
                .align(Alignment.Center)
                .fillMaxWidth()
                .alpha(0.3f),
            contentScale = ContentScale.Fit
        )

        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 24.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Spacer(modifier = Modifier.height(80.dp))

            // Heading
            Text(
                text = "Find the place you\nare looking for",
                fontSize = 28.sp,
                fontWeight = FontWeight.Bold,
                color = Teal,
                textAlign = TextAlign.Center,
                lineHeight = 36.sp
            )

            Spacer(modifier = Modifier.weight(1f))

            // Main Brands Illustration
            Image(
                painter = painterResource(id = R.drawable.brands_image),
                contentDescription = "Major Brands",
                modifier = Modifier
                    .fillMaxWidth()
                    .height(350.dp),
                contentScale = ContentScale.Fit
            )

            Spacer(modifier = Modifier.weight(1f))

            // "Sign Up" bit (matching design)
            Text(
                text = buildAnnotatedString {
                    append("Dont have an account? ")
                    withStyle(style = SpanStyle(
                        color = Teal,
                        fontWeight = FontWeight.Bold,
                        textDecoration = TextDecoration.Underline
                    )) {
                        append("Sign Up")
                    }
                },
                fontSize = 16.sp,
                color = Teal.copy(alpha = 0.8f),
                modifier = Modifier.clickable { /* Handle Sign Up if needed */ }
            )

            Spacer(modifier = Modifier.height(24.dp))

            // Main Action Button
            Button(
                onClick = onPhoneAuthClick,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(64.dp)
                    .shadow(12.dp, RoundedCornerShape(16.dp)),
                shape = RoundedCornerShape(16.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = Teal,
                    contentColor = White
                )
            ) {
                Text(
                    text = "Sign In with Phone Number",
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold
                )
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Skip Button (as requested)
            TextButton(
                onClick = onSkipClick,
                modifier = Modifier.padding(bottom = 32.dp)
            ) {
                Text(
                    text = "Skip for now",
                    color = Teal.copy(alpha = 0.6f),
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Medium
                )
            }
        }
    }
}

// Extension to allow brands_image to be used from drawable
// Since it was in res/welcome-screen, we need to make sure the project can see it.
// Usually resources should be in drawable-xhdpi folders etc. 
// But if they are just in res/welcome-screen, Android might not find them via R.drawable.
// Let's check where they are exactly.
