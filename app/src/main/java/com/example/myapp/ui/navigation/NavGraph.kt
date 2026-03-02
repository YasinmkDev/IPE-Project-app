package com.example.myapp.ui.navigation

import androidx.compose.animation.*
import androidx.compose.animation.core.tween
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.navArgument
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
        composable(route = Screen.Welcome.route) {
            WelcomeScreen(
                onGetStarted = {
                    navController.navigate(Screen.LinkDevice.route)
                }
            )
        }

        composable(route = Screen.LinkDevice.route) {
            LinkDeviceScreen(
                navController = navController,
                onLinkDevice = { pairingCode ->
                    FirebaseService.resolvePairingCode(
                        pairingCode,
                        onSuccess = { childId, parentId ->
                            // DO NOT call markDeviceAsLinked yet.
                            // Just navigate to permissions.
                            navController.navigate(Screen.Permissions.createRoute(childId, parentId))
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

        composable(
            route = "permissions/{childId}/{parentId}",
            arguments = listOf(
                navArgument("childId") { type = NavType.StringType },
                navArgument("parentId") { type = NavType.StringType }
            )
        ) { backStackEntry ->
            val childId = backStackEntry.arguments?.getString("childId") ?: ""
            val parentId = backStackEntry.arguments?.getString("parentId") ?: ""
            
            PermissionsScreen(
                onGrantAll = {
                    navController.navigate(Screen.SetupComplete.createRoute(childId, parentId)) {
                        popUpTo(Screen.Welcome.route) { inclusive = false }
                    }
                },
                onSkip = {
                    navController.navigate(Screen.SetupComplete.createRoute(childId, parentId)) {
                        popUpTo(Screen.Welcome.route) { inclusive = false }
                    }
                },
                onBack = { navController.popBackStack() },
                onPermissionDetail = { permissionId ->
                    navController.navigate(Screen.PermissionDetail.createRoute(childId, parentId, permissionId))
                }
            )
        }

        composable(
            route = "permission_detail/{childId}/{parentId}/{permissionId}",
            arguments = listOf(
                navArgument("childId") { type = NavType.StringType },
                navArgument("parentId") { type = NavType.StringType },
                navArgument("permissionId") { type = NavType.StringType }
            )
        ) { backStackEntry ->
            val childId = backStackEntry.arguments?.getString("childId") ?: ""
            val parentId = backStackEntry.arguments?.getString("parentId") ?: ""
            val permissionId = backStackEntry.arguments?.getString("permissionId") ?: ""

            PermissionDetailScreen(
                permissionId = permissionId,
                onBack = { navController.popBackStack() },
                onPermissionGranted = { navController.popBackStack() }
            )
        }

        composable(
            route = Screen.AlreadyLinked("").route,
            arguments = listOf(
                navArgument("childId") { type = NavType.StringType }
            )
        ) { backStackEntry ->
            val childId = backStackEntry.arguments?.getString("childId") ?: ""
            AlreadyLinkedScreen(
                onContinue = {
                    onSetupComplete(childId)
                }
            )
        }

        composable(
            route = "setup_complete/{childId}/{parentId}",
            arguments = listOf(
                navArgument("childId") { type = NavType.StringType },
                navArgument("parentId") { type = NavType.StringType }
            )
        ) { backStackEntry ->
            val childId = backStackEntry.arguments?.getString("childId") ?: ""
            val parentId = backStackEntry.arguments?.getString("parentId") ?: ""

            SetupCompleteScreen(
                onFinish = {
                    // ONLY MARK AS LINKED NOW!
                    FirebaseService.markDeviceAsLinked(
                        childId,
                        parentId,
                        onSuccess = {
                            onSetupComplete(childId)
                        },
                        onFailure = { onSetupComplete(childId) }
                    )
                }
            )
        }
    }
}
