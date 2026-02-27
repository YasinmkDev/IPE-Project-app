package com.example.myapp.ui.navigation

sealed class Screen(val route: String) {
    data object Welcome : Screen("welcome")
    data object LinkDevice : Screen("link_device")
    data object QRScanner : Screen("qr_scanner")
    data class Permissions(val childId: String) : Screen("permissions/{childId}") {
        companion object {
            fun createRoute(childId: String) = "permissions/$childId"
        }
    }
    data class SetupComplete(val childId: String) : Screen("setup_complete/{childId}") {
        companion object {
            fun createRoute(childId: String) = "setup_complete/$childId"
        }
    }
}
