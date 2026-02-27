package com.example.myapp.ui.navigation

sealed class Screen(val route: String) {
    data object Welcome : Screen("welcome")
    data object LinkDevice : Screen("link_device")
    data object QRScanner : Screen("qr_scanner")
    data object Permissions : Screen("permissions")
    data object SetupComplete : Screen("setup_complete")
}
