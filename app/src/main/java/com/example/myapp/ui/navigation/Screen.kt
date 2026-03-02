package com.example.myapp.ui.navigation

sealed class Screen(val route: String) {
    data object Welcome : Screen("welcome")
    data object LinkDevice : Screen("link_device")
    data object QRScanner : Screen("qr_scanner")
    
    data class Permissions(val childId: String, val parentId: String) : Screen("permissions/{childId}/{parentId}") {
        companion object {
            fun createRoute(childId: String, parentId: String) = "permissions/$childId/$parentId"
        }
    }
    
    data class PermissionDetail(val childId: String, val parentId: String, val permissionId: String) : 
        Screen("permission_detail/{childId}/{parentId}/{permissionId}") {
        companion object {
            fun createRoute(childId: String, parentId: String, permissionId: String) = "permission_detail/$childId/$parentId/$permissionId"
        }
    }
    
    data class AlreadyLinked(val childId: String) : Screen("already_linked/{childId}") {
        companion object {
            fun createRoute(childId: String) = "already_linked/$childId"
        }
    }
    
    data class SetupComplete(val childId: String, val parentId: String) : Screen("setup_complete/{childId}/{parentId}") {
        companion object {
            fun createRoute(childId: String, parentId: String) = "setup_complete/$childId/$parentId"
        }
    }
}
