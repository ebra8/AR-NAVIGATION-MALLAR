package com.example.mallar

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.example.mallar.ui.screens.ArNavigationScreen
import com.example.mallar.ui.screens.LogoScanScreen
import com.example.mallar.ui.screens.OtpVerifyScreen
import com.example.mallar.ui.screens.PermissionsScreen
import com.example.mallar.ui.screens.PhoneAuthScreen
import com.example.mallar.ui.screens.SplashScreen
import com.example.mallar.ui.screens.StoreSearchScreen
import com.example.mallar.ui.theme.MallARTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            MallARTheme {
                Surface(modifier = Modifier.fillMaxSize()) {
                    MallARNavGraph()
                }
            }
        }
    }
}

/**
 * Navigation graph for the entire MallAR app.
 *
 * Flow:
 *   Splash → Permissions → PhoneAuth → OtpVerify → LogoScan → StoreSearch → ArNavigation
 *                                      ↘ (Skip) → LogoScan
 */
@Composable
fun MallARNavGraph() {
    val navController = rememberNavController()

    NavHost(
        navController = navController,
        startDestination = "splash"
    ) {
        composable("splash") {
            SplashScreen(
                onStartClick = {
                    navController.navigate("permissions")
                }
            )
        }

        composable("permissions") {
            PermissionsScreen(
                onContinueClick = {
                    navController.navigate("phone_auth")
                }
            )
        }

        composable("phone_auth") {
            PhoneAuthScreen(
                onBackClick = { navController.popBackStack() },
                onSendClick = {
                    navController.navigate("otp_verify")
                },
                onSkipClick = {
                    navController.navigate("logo_scan")
                }
            )
        }

        composable("otp_verify") {
            OtpVerifyScreen(
                onBackClick = { navController.popBackStack() },
                onVerifyClick = {
                    navController.navigate("logo_scan")
                }
            )
        }

        composable("logo_scan") {
            LogoScanScreen(
                onBackClick = { navController.popBackStack() },
                onStoreSelected = {
                    navController.navigate("ar_navigation")
                }
            )
        }

        composable("ar_navigation") {
            ArNavigationScreen(
                onBackClick = { navController.popBackStack() }
            )
        }
    }
}