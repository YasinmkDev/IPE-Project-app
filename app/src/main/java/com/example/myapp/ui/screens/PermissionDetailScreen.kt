package com.example.myapp.ui.screens

import android.Manifest
import android.accessibilityservice.AccessibilityServiceInfo
import android.app.AppOpsManager
import android.app.admin.DevicePolicyManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.provider.Settings
import android.view.accessibility.AccessibilityManager
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import com.example.myapp.receivers.DeviceAdminReceiver
import com.example.myapp.ui.theme.GreenPrimary
import com.example.myapp.ui.theme.GreenPrimaryDark
import com.example.myapp.ui.theme.GreenSurface
import com.example.myapp.ui.theme.IPETheme
import androidx.compose.ui.tooling.preview.Preview
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

data class PermissionDetail(
    val id: String,
    val icon: ImageVector,
    val title: String,
    val description: String,
    val fullDescription: String,
    val isEnabled: Boolean = false,
    val action: (Context) -> Unit,
    val checkStatus: (Context) -> Boolean
)

@Composable
fun PermissionDetailScreen(
    permissionId: String,
    onBack: () -> Unit,
    onPermissionGranted: () -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var isChecking by remember { mutableStateOf(false) }
    var isPermissionEnabled by remember { mutableStateOf(false) }

    // Define permissions with detailed info
    val permissionDetails = mapOf(
        "camera" to PermissionDetail(
            id = "camera",
            icon = Icons.Filled.PhotoCamera,
            title = "Camera",
            description = "Scan QR codes and capture images",
            fullDescription = "This app needs camera access to scan QR codes for device linking and capture images for monitoring purposes. Your privacy is protected - the camera is only accessed when you actively use features requiring it.",
            isEnabled = false,
            action = { ctx ->
                val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS)
                intent.data = android.net.Uri.parse("package:" + ctx.packageName)
                ctx.startActivity(intent)
            },
            checkStatus = { ctx ->
                ContextCompat.checkSelfPermission(ctx, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED
            }
        ),
        "usage_access" to PermissionDetail(
            id = "usage_access",
            icon = Icons.Filled.BarChart,
            title = "Usage Access",
            description = "Monitor app usage and screen time",
            fullDescription = "Usage Access allows the app to see which apps are being used and for how long. This helps track screen time and app usage patterns to ensure healthy device usage habits.",
            isEnabled = false,
            action = { ctx ->
                val intent = Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS)
                ctx.startActivity(intent)
            },
            checkStatus = { ctx ->
                val appOpsManager = ctx.getSystemService(Context.APP_OPS_SERVICE) as? AppOpsManager
                val mode = appOpsManager?.checkOpNoThrow(
                    AppOpsManager.OPSTR_GET_USAGE_STATS,
                    android.os.Process.myUid(),
                    ctx.packageName
                ) ?: AppOpsManager.MODE_DEFAULT
                mode == AppOpsManager.MODE_ALLOWED
            }
        ),
        "accessibility" to PermissionDetail(
            id = "accessibility",
            icon = Icons.Filled.Accessibility,
            title = "Accessibility Service",
            description = "Enable advanced monitoring and controls",
            fullDescription = "The Accessibility Service enables advanced features like monitoring system notifications, detecting screen lock/unlock events, and providing better app usage insights. This service respects your privacy and follows Android's accessibility guidelines.",
            isEnabled = false,
            action = { ctx ->
                val intent = Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)
                ctx.startActivity(intent)
            },
            checkStatus = { ctx ->
                val accessibilityManager = ctx.getSystemService(Context.ACCESSIBILITY_SERVICE) as? AccessibilityManager
                accessibilityManager?.getEnabledAccessibilityServiceList(AccessibilityServiceInfo.FEEDBACK_ALL_MASK)?.any {
                    it.id.contains(ctx.packageName)
                } ?: false
            }
        ),
        "overlay" to PermissionDetail(
            id = "overlay",
            icon = Icons.Filled.Layers,
            title = "Display Over Other Apps",
            description = "Show alerts and controls on screen",
            fullDescription = "This permission allows the app to display alerts and quick controls on top of other apps. This is used to show notifications and time limit warnings.",
            isEnabled = false,
            action = { ctx ->
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                    val intent = Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION)
                    intent.data = android.net.Uri.parse("package:" + ctx.packageName)
                    try {
                        ctx.startActivity(intent)
                    } catch (e: Exception) {
                        val fallback = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS)
                        fallback.data = android.net.Uri.parse("package:" + ctx.packageName)
                        ctx.startActivity(fallback)
                    }
                }
            },
            checkStatus = { ctx ->
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                    Settings.canDrawOverlays(ctx)
                } else {
                    true
                }
            }
        ),
        "device_admin" to PermissionDetail(
            id = "device_admin",
            icon = Icons.Filled.Lock,
            title = "Device Admin",
            description = "Enable lock and security features",
            fullDescription = "Device Administrator access enables remote lock and wipe capabilities, ensuring your device can be secured if needed. This is only activated with your explicit permission.",
            isEnabled = false,
            action = { ctx ->
                val intent = Intent(Settings.ACTION_SECURITY_SETTINGS)
                ctx.startActivity(intent)
            },
            checkStatus = { ctx ->
                val devicePolicyManager = ctx.getSystemService(Context.DEVICE_POLICY_SERVICE) as? DevicePolicyManager
                devicePolicyManager?.isAdminActive(
                    ComponentName(ctx, DeviceAdminReceiver::class.java)
                ) ?: false
            }
        )
    )

    val permission = permissionDetails[permissionId]

    if (permission == null) {
        Box(
            modifier = Modifier.fillMaxSize(),
            contentAlignment = Alignment.Center
        ) {
            Text("Permission not found")
        }
        return
    }

    // Check permission status on launch
    LaunchedEffect(Unit) {
        isPermissionEnabled = permission.checkStatus(context)
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.White)
    ) {
        // Header
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
                text = permission.title,
                fontSize = 18.sp,
                fontWeight = FontWeight.SemiBold,
                color = GreenPrimaryDark
            )
        }

        LazyColumn(
            modifier = Modifier
                .weight(1f)
                .padding(horizontal = 24.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
            contentPadding = PaddingValues(vertical = 16.dp)
        ) {
            // Permission Icon
            item {
                Box(
                    modifier = Modifier
                        .size(80.dp)
                        .clip(CircleShape)
                        .background(GreenSurface)
                        .align(Alignment.CenterHorizontally),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = permission.icon,
                        contentDescription = permission.title,
                        tint = GreenPrimary,
                        modifier = Modifier.size(38.dp)
                    )
                }
            }

            // Status
            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = if (isPermissionEnabled) GreenSurface else Color(0xFFFFF4E6)
                    )
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = if (isPermissionEnabled) Icons.Filled.CheckCircle else Icons.Filled.Info,
                            contentDescription = null,
                            tint = if (isPermissionEnabled) GreenPrimary else Color(0xFFFFA500),
                            modifier = Modifier.size(20.dp)
                        )
                        Spacer(modifier = Modifier.width(12.dp))
                        Text(
                            text = if (isPermissionEnabled) "Permission Granted" else "Permission Not Granted",
                            fontSize = 14.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = GreenPrimaryDark
                        )
                    }
                }
            }

            // Description
            item {
                Column {
                    Text(
                        text = "About This Permission",
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Bold,
                        color = GreenPrimaryDark
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = permission.fullDescription,
                        fontSize = 13.sp,
                        color = Color.Gray,
                        lineHeight = 20.sp
                    )
                }
            }

            // Steps
            item {
                Column {
                    Text(
                        text = "How to Enable",
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Bold,
                        color = GreenPrimaryDark
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                    
                    // Step 1
                    StepItem(
                        number = "1",
                        text = "Tap 'Open Settings' button below"
                    )
                    
                    // Step 2
                    StepItem(
                        number = "2",
                        text = "Look for the permission option in settings"
                    )
                    
                    // Step 3
                    StepItem(
                        number = "3",
                        text = "Toggle the permission ON"
                    )
                    
                    // Step 4
                    StepItem(
                        number = "4",
                        text = "Return to this app to confirm"
                    )
                }
            }
        }

        // Bottom Buttons
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .background(Color.White)
                .padding(horizontal = 24.dp, vertical = 16.dp)
        ) {
            Button(
                onClick = {
                    isChecking = true
                    permission.action(context)
                    
                    scope.launch {
                        delay(3000)
                        isPermissionEnabled = permission.checkStatus(context)
                        delay(2000)
                        isPermissionEnabled = permission.checkStatus(context)
                        isChecking = false
                        
                        if (isPermissionEnabled) {
                            onPermissionGranted()
                        }
                    }
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(52.dp),
                shape = RoundedCornerShape(26.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = if (isPermissionEnabled) GreenPrimary else GreenPrimary
                ),
                enabled = !isChecking
            ) {
                if (isChecking) {
                    CircularProgressIndicator(
                        color = Color.White,
                        modifier = Modifier.size(20.dp),
                        strokeWidth = 2.dp
                    )
                } else {
                    Icon(
                        imageVector = Icons.Filled.Settings,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = if (isPermissionEnabled) "Permission Enabled ✓" else "Open Settings",
                        fontSize = 15.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = Color.White
                    )
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            TextButton(
                onClick = onBack,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(44.dp)
            ) {
                Text(
                    text = "Back",
                    color = Color.Gray,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Medium
                )
            }
        }
    }
}

@Composable
private fun StepItem(number: String, text: String) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp),
        verticalAlignment = Alignment.Top
    ) {
        // Step Number
        Box(
            modifier = Modifier
                .size(28.dp)
                .clip(CircleShape)
                .background(GreenPrimary),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = number,
                color = Color.White,
                fontSize = 12.sp,
                fontWeight = FontWeight.Bold
            )
        }
        
        Spacer(modifier = Modifier.width(12.dp))
        
        // Step Text
        Text(
            text = text,
            fontSize = 13.sp,
            color = Color.Gray,
            lineHeight = 18.sp,
            modifier = Modifier.align(Alignment.CenterVertically)
        )
    }
}

@Preview(showBackground = true)
@Composable
fun PermissionDetailScreenPreview() {
    IPETheme {
        PermissionDetailScreen(
            permissionId = "camera",
            onBack = {},
            onPermissionGranted = {}
        )
    }
}
