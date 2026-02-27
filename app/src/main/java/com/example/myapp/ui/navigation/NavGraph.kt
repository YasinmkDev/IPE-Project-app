package com.example.myapp.ui.navigation

import androidx.compose.animation.*
import androidx.compose.animation.core.tween
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import com.example.myapp.services.FirebaseService
import com.example.myapp.ui.screens.*

@Composable
fun IPENavGraph(
    navController: NavHostController,
    onSetupComplete: (String) -> Unit
) {
    NavHost(
        navController = navController,
        startDestination = Screen.Welcome.route,
        enterTransition = {
            slideInHorizontally(
                initialOffsetX = { it },
                animationSpec = tween(300)
            ) + fadeIn(animationSpec = tween(300))
        },
        exitTransition = {
            slideOutHorizontally(
                targetOffsetX = { -it / 3 },
                animationSpec = tween(300)
            ) + fadeOut(animationSpec = tween(300))
        },
        popEnterTransition = {
            slideInHorizontally(
                initialOffsetX = { -it / 3 },
                animationSpec = tween(300)
            ) + fadeIn(animationSpec = tween(300))
        },
        popExitTransition = {
            slideOutHorizontally(
                targetOffsetX = { it },
                animationSpec = tween(300)
            ) + fadeOut(animationSpec = tween(300))
        }
    ) {
        // Screen 1: Welcome
        composable(route = Screen.Welcome.route) {
            WelcomeScreen(
                onGetStarted = {
                    navController.navigate(Screen.LinkDevice.route)
                }
            )
        }

        // Screen 2: Link Device
        composable(route = Screen.LinkDevice.route) {
            LinkDeviceScreen(
                navController = navController,
                onLinkDevice = { pairingCode ->
                    // Resolve pairing code to childId
                    FirebaseService.resolvePairingCode(
                        pairingCode,
                        onSuccess = { childId, parentId ->
                            // Mark device as linked by updating linkedAt timestamp
                            FirebaseService.markDeviceAsLinked(
                                childId,
                                parentId,
                                onSuccess = {
                                    // Store childId in SavedStateHandle to pass through navigation
                                    navController.currentBackStackEntry?.savedStateHandle?.set("CHILD_ID", childId)
                                    navController.navigate(Screen.Permissions.route)
                                },
                                onFailure = { exception ->
                                    println("Error marking device as linked: ${exception.message}")
                                }
                            )
                        },
                        onFailure = { exception ->
                            println("Error resolving pairing code: ${exception.message}")
                        }
                    )
                },
                onScanQR = {
                    navController.navigate(Screen.QRScanner.route)
                },
                onBack = {
                    navController.popBackStack()
                }
            )
        }

        // Screen 3: QR Scanner
        composable(route = Screen.QRScanner.route) { backStackEntry ->
            val parentEntry = remember(backStackEntry) {
                navController.getBackStackEntry(Screen.LinkDevice.route)
            }
            val parentState = parentEntry.savedStateHandle

            QRScannerScreen(
                onQRCodeScanned = { code ->
                    parentState.set("scannedCode", code)
                    navController.popBackStack()
                },
                onBack = {
                    navController.popBackStack()
                }
            )
        }

        // Screen 4: Permissions
        composable(route = Screen.Permissions.route) {
            PermissionsScreen(
                onGrantAll = {
                    navController.navigate(Screen.SetupComplete.route) {
                        popUpTo(Screen.Welcome.route) {
                            inclusive = false
                        }
                    }
                },
                onSkip = {
                    navController.navigate(Screen.SetupComplete.route) {
                        popUpTo(Screen.Welcome.route) {
                            inclusive = false
                        }
                    }
                },
                onBack = {
                    navController.popBackStack()
                }
            )
        }

        // Screen 5: Setup Complete
        composable(route = Screen.SetupComplete.route) { backStackEntry ->
            // Retrieve childId from navigation back stack
            val childId = remember {
                navController.previousBackStackEntry?.savedStateHandle?.get<String>("CHILD_ID") ?: ""
            }

            SetupCompleteScreen(
                onFinish = {
                    onSetupComplete(childId)
                }
            )
        }
    }
}
