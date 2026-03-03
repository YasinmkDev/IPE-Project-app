package com.example.myapp.ui.screens

import android.Manifest
import android.app.admin.DevicePolicyManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.provider.Settings
import com.example.myapp.receivers.DeviceAdminReceiver
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Accessibility
import androidx.compose.material.icons.filled.Layers
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.Security
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import com.example.myapp.ui.theme.GreenPrimary
import com.example.myapp.ui.theme.GreenPrimaryDark
import com.example.myapp.ui.theme.GreenSurface
import com.example.myapp.utils.PermissionChecker
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

data class PermissionItem(
    val id: String,
    val icon: ImageVector,
    val title: String,
    val description: String,
    var isEnabled: Boolean = false,
    val isSystemPermission: Boolean = false,
    val permissionType: String? = null
)

@Composable
fun PermissionsScreen(
    onGrantAll: () -> Unit,
    onSkip: () -> Unit,
    onBack: () -> Unit,
    onPermissionDetail: (String) -> Unit = {}
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var isLoading by remember { mutableStateOf(false) }

    var permissions by remember {
        mutableStateOf(
            listOf(
                PermissionItem(
                    id = "device_admin",
                    icon = Icons.Filled.Lock,
                    title = "Device Admin",
                    description = "Required for anti-uninstall protection",
                    isEnabled = false,
                    isSystemPermission = true
                ),
                PermissionItem(
                    id = "notifications",
                    icon = Icons.Filled.Notifications,
                    title = "Notifications",
                    description = "Required to keep the app running in background",
                    isEnabled = false,
                    isSystemPermission = true,
                    permissionType = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) Manifest.permission.POST_NOTIFICATIONS else null
                ),
                PermissionItem(
                    id = "usage_access",
                    icon = Icons.Filled.Accessibility,
                    title = "Usage Access",
                    description = "Monitor app usage and screen time",
                    isEnabled = false,
                    isSystemPermission = true
                ),
                PermissionItem(
                    id = "overlay",
                    icon = Icons.Filled.Layers,
                    title = "Display Over Other Apps",
                    description = "Show block screens over other apps",
                    isEnabled = false,
                    isSystemPermission = true
                ),
                PermissionItem(
                    id = "accessibility",
                    icon = Icons.Filled.Security,
                    title = "Accessibility Service",
                    description = "Core engine for app and web blocking",
                    isEnabled = false,
                    isSystemPermission = true
                )
            )
        )
    }

    suspend fun checkPermissions(ctx: Context, currentPermissions: List<PermissionItem>): List<PermissionItem> {
        return currentPermissions.map { permission ->
            val isGranted = when (permission.id) {
                "notifications" -> PermissionChecker.checkNotificationPermission(ctx)
                "usage_access" -> PermissionChecker.checkUsageAccessPermission(ctx)
                "accessibility" -> PermissionChecker.checkAccessibilityPermission(ctx)
                "overlay" -> PermissionChecker.checkOverlayPermission(ctx)
                "device_admin" -> PermissionChecker.checkDeviceAdminPermission(ctx)
                else -> permission.isEnabled
            }
            permission.copy(isEnabled = isGranted)
        }
    }

    val permissionLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { _ ->
        scope.launch { permissions = checkPermissions(context, permissions) }
    }

    val settingsLauncher = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) { _ ->
        scope.launch { permissions = checkPermissions(context, permissions) }
    }

    val requestPermission = { item: PermissionItem ->
        when (item.id) {
            "notifications" -> {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                    permissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                }
            }
            "usage_access" -> settingsLauncher.launch(Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS))
            "accessibility" -> settingsLauncher.launch(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))
            "overlay" -> if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                settingsLauncher.launch(Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, Uri.parse("package:${context.packageName}")))
            }
            "device_admin" -> {
                settingsLauncher.launch(Intent(DevicePolicyManager.ACTION_ADD_DEVICE_ADMIN).apply {
                    putExtra(DevicePolicyManager.EXTRA_DEVICE_ADMIN, ComponentName(context, DeviceAdminReceiver::class.java))
                    putExtra(DevicePolicyManager.EXTRA_ADD_EXPLANATION, "Required for protection.")
                })
            }
        }
    }

    LaunchedEffect(Unit) { permissions = checkPermissions(context, permissions) }

    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event -> if (event == Lifecycle.Event.ON_RESUME) scope.launch { permissions = checkPermissions(context, permissions) } }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    val allGranted = permissions.all { it.isEnabled }
    val isAdminEnabled = permissions.find { it.id == "device_admin" }?.isEnabled ?: false

    Column(modifier = Modifier.fillMaxSize().background(Color.White)) {
        Row(modifier = Modifier.fillMaxWidth().padding(start = 8.dp, end = 16.dp, top = 16.dp), verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onBack) { Icon(imageVector = Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back", tint = GreenPrimary) }
            Spacer(modifier = Modifier.width(4.dp))
            Text(text = "Enable Permissions", fontSize = 18.sp, fontWeight = FontWeight.SemiBold, color = GreenPrimaryDark)
        }

        LazyColumn(modifier = Modifier.weight(1f).padding(horizontal = 24.dp), verticalArrangement = Arrangement.spacedBy(14.dp), contentPadding = PaddingValues(vertical = 16.dp)) {
            item {
                Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.fillMaxWidth()) {
                    Box(modifier = Modifier.size(80.dp).clip(CircleShape).background(GreenSurface), contentAlignment = Alignment.Center) {
                        Icon(imageVector = Icons.Filled.Lock, contentDescription = null, tint = GreenPrimary, modifier = Modifier.size(38.dp))
                    }
                    Spacer(modifier = Modifier.height(20.dp))
                    Text(text = "Initial Setup", fontSize = 22.sp, fontWeight = FontWeight.Bold, color = GreenPrimaryDark)
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(text = if (!isAdminEnabled) "Please enable Device Admin FIRST." else "Great! Now enable the others.", fontSize = 14.sp, color = if (!isAdminEnabled) Color.Red else GreenPrimary, textAlign = TextAlign.Center)
                }
            }

            itemsIndexed(permissions) { _, permission ->
                val isClickable = permission.id == "device_admin" || isAdminEnabled
                PermissionCardWithAction(permission = permission, isLocked = !isClickable, onToggle = { if (isClickable) requestPermission(permission) }, onRequestPermission = { if (isClickable) requestPermission(permission) })
            }
        }

        Column(modifier = Modifier.fillMaxWidth().background(Color.White).padding(horizontal = 24.dp, vertical = 16.dp)) {
            Button(
                onClick = {
                    isLoading = true
                    scope.launch {
                        delay(500)
                        isLoading = false
                        if (allGranted) onGrantAll()
                    }
                },
                modifier = Modifier.fillMaxWidth().height(52.dp),
                shape = RoundedCornerShape(26.dp),
                colors = ButtonDefaults.buttonColors(containerColor = GreenPrimary),
                enabled = !isLoading && allGranted
            ) {
                if (isLoading) CircularProgressIndicator(color = Color.White, modifier = Modifier.size(20.dp), strokeWidth = 2.dp)
                else Text(text = if (allGranted) "Finish Setup ✓" else "Complete All Steps", color = Color.White)
            }
        }
    }
}

@Composable
private fun PermissionCardWithAction(permission: PermissionItem, isLocked: Boolean, onToggle: (Boolean) -> Unit, onRequestPermission: () -> Unit) {
    val cardColor by animateColorAsState(targetValue = if (permission.isEnabled) GreenSurface else if (isLocked) Color(0xFFF5F5F5) else Color.White)
    Card(modifier = Modifier.fillMaxWidth().height(85.dp).alpha(if (isLocked) 0.6f else 1.0f), shape = RoundedCornerShape(16.dp), colors = CardDefaults.cardColors(containerColor = cardColor)) {
        Row(modifier = Modifier.fillMaxSize().padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(modifier = Modifier.size(44.dp).clip(CircleShape).background(if (permission.isEnabled) GreenPrimary.copy(alpha = 0.15f) else GreenSurface).clickable(enabled = !isLocked) { onRequestPermission() }, contentAlignment = Alignment.Center) {
                Icon(imageVector = permission.icon, contentDescription = null, tint = if (permission.isEnabled) GreenPrimary else Color.Gray, modifier = Modifier.size(22.dp))
            }
            Spacer(modifier = Modifier.width(14.dp))
            Column(modifier = Modifier.weight(1f).clickable(enabled = !isLocked) { onRequestPermission() }) {
                Text(text = permission.title, fontSize = 15.sp, fontWeight = FontWeight.SemiBold, color = if (isLocked) Color.Gray else GreenPrimaryDark)
                Text(text = permission.description, fontSize = 11.sp, color = Color.Gray, maxLines = 1)
            }
            Switch(checked = permission.isEnabled, onCheckedChange = onToggle, enabled = !isLocked)
        }
    }
}
