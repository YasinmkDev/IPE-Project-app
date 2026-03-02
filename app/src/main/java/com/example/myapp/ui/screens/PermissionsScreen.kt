package com.example.myapp.ui.screens

import android.Manifest
import android.accessibilityservice.AccessibilityServiceInfo
import android.app.AppOpsManager
import android.app.admin.DevicePolicyManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.provider.Settings
import android.view.accessibility.AccessibilityManager
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
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import com.example.myapp.receivers.DeviceAdminReceiver
import com.example.myapp.ui.theme.GreenPrimary
import com.example.myapp.ui.theme.GreenPrimaryDark
import com.example.myapp.ui.theme.GreenSurface
import com.example.myapp.ui.theme.IPETheme
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
                    description = "Enable remote lock and wipe features (Required first)",
                    isEnabled = false,
                    isSystemPermission = true
                ),
                PermissionItem(
                    id = "usage_access",
                    icon = Icons.Filled.Accessibility,
                    title = "Usage Access",
                    description = "Monitor app usage and screen time statistics",
                    isEnabled = false,
                    isSystemPermission = true
                ),
                PermissionItem(
                    id = "overlay",
                    icon = Icons.Filled.Layers,
                    title = "Display Over Other Apps",
                    description = "Show overlay for alerts and controls",
                    isEnabled = false,
                    isSystemPermission = true
                ),
                PermissionItem(
                    id = "accessibility",
                    icon = Icons.Filled.Security,
                    title = "Accessibility Service",
                    description = "Enable advanced monitoring and controls",
                    isEnabled = false,
                    isSystemPermission = true
                )
            )
        )
    }

    suspend fun checkPermissions(ctx: Context, currentPermissions: List<PermissionItem>): List<PermissionItem> {
        val packageName = ctx.packageName
        
        return currentPermissions.map { permission ->
            val isGranted = when (permission.id) {
                "usage_access" -> {
                    val appOpsManager = ctx.getSystemService(Context.APP_OPS_SERVICE) as? AppOpsManager
                    val mode = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                        appOpsManager?.unsafeCheckOpNoThrow(
                            AppOpsManager.OPSTR_GET_USAGE_STATS,
                            android.os.Process.myUid(),
                            packageName
                        ) ?: AppOpsManager.MODE_DEFAULT
                    } else {
                        @Suppress("DEPRECATION")
                        appOpsManager?.checkOpNoThrow(
                            AppOpsManager.OPSTR_GET_USAGE_STATS,
                            android.os.Process.myUid(),
                            packageName
                        ) ?: AppOpsManager.MODE_DEFAULT
                    }
                    mode == AppOpsManager.MODE_ALLOWED
                }
                "accessibility" -> {
                    val accessibilityManager = ctx.getSystemService(Context.ACCESSIBILITY_SERVICE) as? AccessibilityManager
                    accessibilityManager?.getEnabledAccessibilityServiceList(AccessibilityServiceInfo.FEEDBACK_ALL_MASK)?.any {
                        it.id.contains(packageName)
                    } ?: false
                }
                "overlay" -> {
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                        Settings.canDrawOverlays(ctx)
                    } else {
                        true
                    }
                }
                "device_admin" -> {
                    val devicePolicyManager = ctx.getSystemService(Context.DEVICE_POLICY_SERVICE) as? DevicePolicyManager
                    devicePolicyManager?.isAdminActive(
                        ComponentName(ctx, DeviceAdminReceiver::class.java)
                    ) ?: false
                }
                else -> permission.isEnabled
            }
            permission.copy(isEnabled = isGranted)
        }
    }

    val settingsLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { _ ->
        scope.launch {
            permissions = checkPermissions(context, permissions)
        }
    }

    val requestPermission = { item: PermissionItem ->
        when (item.id) {
            "usage_access" -> {
                settingsLauncher.launch(Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS))
            }
            "accessibility" -> {
                settingsLauncher.launch(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))
            }
            "overlay" -> {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                    val intent = Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION).apply {
                        data = Uri.parse("package:${context.packageName}")
                    }
                    settingsLauncher.launch(intent)
                }
            }
            "device_admin" -> {
                val intent = Intent(DevicePolicyManager.ACTION_ADD_DEVICE_ADMIN).apply {
                    putExtra(DevicePolicyManager.EXTRA_DEVICE_ADMIN, ComponentName(context, DeviceAdminReceiver::class.java))
                    putExtra(DevicePolicyManager.EXTRA_ADD_EXPLANATION, "This permission is required for device security features.")
                }
                settingsLauncher.launch(intent)
            }
        }
    }

    LaunchedEffect(Unit) {
        permissions = checkPermissions(context, permissions)
    }

    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                scope.launch {
                    permissions = checkPermissions(context, permissions)
                }
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
        }
    }

    val allGranted = permissions.all { it.isEnabled }
    val isAdminEnabled = permissions.find { it.id == "device_admin" }?.isEnabled ?: false

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.White)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = 8.dp, end = 16.dp, top = 16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = onBack) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                    contentDescription = "Back",
                    tint = GreenPrimary
                )
            }
            Spacer(modifier = Modifier.width(4.dp))
            Text(
                text = "Enable Permissions",
                fontSize = 18.sp,
                fontWeight = FontWeight.SemiBold,
                color = GreenPrimaryDark
            )
        }

        LazyColumn(
            modifier = Modifier
                .weight(1f)
                .padding(horizontal = 24.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
            contentPadding = PaddingValues(vertical = 16.dp)
        ) {
            item {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Spacer(modifier = Modifier.height(8.dp))
                    Box(
                        modifier = Modifier
                            .size(80.dp)
                            .clip(CircleShape)
                            .background(GreenSurface),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Filled.Lock,
                            contentDescription = "Permissions",
                            tint = GreenPrimary,
                            modifier = Modifier.size(38.dp)
                        )
                    }
                    Spacer(modifier = Modifier.height(20.dp))
                    Text(
                        text = "Enable Permissions",
                        fontSize = 22.sp,
                        fontWeight = FontWeight.Bold,
                        color = GreenPrimaryDark,
                        textAlign = TextAlign.Center
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = if (!isAdminEnabled) "Please enable Device Admin FIRST." 
                               else "Device Admin enabled. You can now enable other permissions.",
                        fontSize = 14.sp,
                        color = if (!isAdminEnabled) Color.Red else GreenPrimary,
                        fontWeight = FontWeight.Medium,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.padding(horizontal = 16.dp),
                        lineHeight = 20.sp
                    )
                }
            }

            itemsIndexed(permissions) { _, permission ->
                val isClickable = permission.id == "device_admin" || isAdminEnabled
                
                PermissionCardWithAction(
                    permission = permission,
                    isLocked = !isClickable,
                    onToggle = { isChecked ->
                        if (isClickable) {
                            if (isChecked && !permission.isEnabled) {
                                requestPermission(permission)
                            } else if (!isChecked && permission.isEnabled) {
                                requestPermission(permission)
                            }
                        }
                    },
                    onRequestPermission = {
                        if (isClickable) requestPermission(permission)
                    }
                )
            }
        }

        Column(
            modifier = Modifier
                .fillMaxWidth()
                .background(Color.White)
                .padding(horizontal = 24.dp, vertical = 16.dp)
        ) {
            Button(
                onClick = {
                    isLoading = true
                    scope.launch {
                        permissions = checkPermissions(context, permissions)
                        delay(500)
                        isLoading = false
                        if (permissions.all { it.isEnabled }) {
                            onGrantAll()
                        }
                    }
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(52.dp),
                shape = RoundedCornerShape(26.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = GreenPrimary
                ),
                enabled = !isLoading && allGranted
            ) {
                if (isLoading) {
                    CircularProgressIndicator(
                        color = Color.White,
                        modifier = Modifier.size(20.dp),
                        strokeWidth = 2.dp
                    )
                } else {
                    Icon(
                        imageVector = Icons.Filled.Security,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = if (allGranted) "All Set ✓" else "Continue",
                        fontSize = 15.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = Color.White
                    )
                }
            }
            Spacer(modifier = Modifier.height(12.dp))
            TextButton(
                onClick = onSkip,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(44.dp)
            ) {
                Text(
                    text = "Skip for now",
                    color = Color.Gray,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Medium
                )
            }
        }
    }
}

@Composable
private fun PermissionCardWithAction(
    permission: PermissionItem,
    isLocked: Boolean,
    onToggle: (Boolean) -> Unit,
    onRequestPermission: () -> Unit
) {
    val cardColor by animateColorAsState(
        targetValue = if (permission.isEnabled) GreenSurface else if (isLocked) Color(0xFFF5F5F5) else Color.White,
        label = "cardColor"
    )

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .height(90.dp)
            .alpha(if (isLocked) 0.6f else 1.0f),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = cardColor
        ),
        elevation = CardDefaults.cardElevation(
            defaultElevation = if (permission.isEnabled || isLocked) 0.dp else 2.dp
        )
    ) {
        Row(
            modifier = Modifier
                .fillMaxSize()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(48.dp)
                    .clip(CircleShape)
                    .background(
                        if (permission.isEnabled) GreenPrimary.copy(alpha = 0.15f)
                        else GreenSurface
                    )
                    .clickable(enabled = !isLocked) { onRequestPermission() },
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = permission.icon,
                    contentDescription = permission.title,
                    tint = if (permission.isEnabled) GreenPrimary else if (isLocked) Color.Gray else GreenPrimary.copy(alpha = 0.7f),
                    modifier = Modifier.size(24.dp)
                )
            }
            Spacer(modifier = Modifier.width(14.dp))
            Column(
                modifier = Modifier
                    .weight(1f)
                    .clickable(enabled = !isLocked) { onRequestPermission() }
            ) {
                Text(
                    text = permission.title,
                    fontSize = 15.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = if (isLocked) Color.Gray else GreenPrimaryDark
                )
                Spacer(modifier = Modifier.height(3.dp))
                Text(
                    text = permission.description,
                    fontSize = 12.sp,
                    color = Color.Gray,
                    maxLines = 2,
                    lineHeight = 16.sp
                )
            }
            Switch(
                checked = permission.isEnabled,
                onCheckedChange = onToggle,
                enabled = !isLocked,
                colors = SwitchDefaults.colors(
                    checkedThumbColor = Color.White,
                    checkedTrackColor = GreenPrimary,
                    uncheckedThumbColor = Color.White,
                    uncheckedTrackColor = Color.LightGray.copy(alpha = 0.5f)
                )
            )
        }
    }
}
