package com.example.myapp.utils

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
import android.os.Process
import android.provider.Settings
import android.view.accessibility.AccessibilityManager
import androidx.core.content.ContextCompat
import com.example.myapp.receivers.DeviceAdminReceiver

/**
 * Data class representing a permission item's status.
 */
data class PermissionStatus(
    val id: String,
    val isGranted: Boolean,
    val isSystemPermission: Boolean = false,
    val permissionType: String? = null
)

/**
 * Utility object for checking various Android permissions.
 */
object PermissionChecker {
    
    private const val TAG = "PermissionChecker"

    /**
     * Check all required permissions for the parental control app.
     */
    fun checkAllPermissions(context: Context): Map<String, Boolean> {
        return mapOf(
            "notifications" to checkNotificationPermission(context),
            "usage_access" to checkUsageAccessPermission(context),
            "accessibility" to checkAccessibilityPermission(context),
            "overlay" to checkOverlayPermission(context),
            "device_admin" to checkDeviceAdminPermission(context)
        )
    }

    /**
     * Check notification permission (Android 13+).
     */
    fun checkNotificationPermission(context: Context): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            ContextCompat.checkSelfPermission(
                context, 
                Manifest.permission.POST_NOTIFICATIONS
            ) == PackageManager.PERMISSION_GRANTED
        } else {
            true
        }
    }

    /**
     * Check usage access permission.
     */
    fun checkUsageAccessPermission(context: Context): Boolean {
        val packageName = context.packageName
        return try {
            val appOpsManager = context.getSystemService(Context.APP_OPS_SERVICE) as? AppOpsManager
            val mode = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                appOpsManager?.unsafeCheckOpNoThrow(
                    AppOpsManager.OPSTR_GET_USAGE_STATS, 
                    Process.myUid(), 
                    packageName
                ) ?: AppOpsManager.MODE_DEFAULT
            } else {
                @Suppress("DEPRECATION")
                appOpsManager?.checkOpNoThrow(
                    AppOpsManager.OPSTR_GET_USAGE_STATS, 
                    Process.myUid(), 
                    packageName
                ) ?: AppOpsManager.MODE_DEFAULT
            }
            mode == AppOpsManager.MODE_ALLOWED
        } catch (e: Exception) {
            false
        }
    }

    /**
     * Check accessibility service permission.
     */
    fun checkAccessibilityPermission(context: Context): Boolean {
        return try {
            val accessibilityManager = context.getSystemService(Context.ACCESSIBILITY_SERVICE) as? AccessibilityManager
            val packageName = context.packageName
            accessibilityManager?.getEnabledAccessibilityServiceList(AccessibilityServiceInfo.FEEDBACK_ALL_MASK)
                ?.any { it.id.contains(packageName) }
                ?: false
        } catch (e: Exception) {
            false
        }
    }

    /**
     * Check overlay permission (draw over other apps).
     */
    fun checkOverlayPermission(context: Context): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            Settings.canDrawOverlays(context)
        } else {
            true
        }
    }

    /**
     * Check device admin permission.
     */
    fun checkDeviceAdminPermission(context: Context): Boolean {
        return try {
            val dpm = context.getSystemService(Context.DEVICE_POLICY_SERVICE) as? DevicePolicyManager
            dpm?.isAdminActive(ComponentName(context, DeviceAdminReceiver::class.java)) ?: false
        } catch (e: Exception) {
            false
        }
    }

    /**
     * Get the intent to request a specific permission.
     */
    fun getPermissionIntent(context: Context, permissionId: String): Intent? {
        val packageName = context.packageName
        return when (permissionId) {
            "notifications" -> {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                    Intent(Manifest.permission.POST_NOTIFICATIONS)
                } else null
            }
            "usage_access" -> Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS)
            "accessibility" -> Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)
            "overlay" -> if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, Uri.parse("package:$packageName"))
            } else null
            "device_admin" -> Intent(DevicePolicyManager.ACTION_ADD_DEVICE_ADMIN).apply {
                putExtra(DevicePolicyManager.EXTRA_DEVICE_ADMIN, ComponentName(context, DeviceAdminReceiver::class.java))
                putExtra(DevicePolicyManager.EXTRA_ADD_EXPLANATION, "Required for protection.")
            }
            else -> null
        }
    }

    /**
     * Check if all required permissions are granted.
     */
    fun areAllPermissionsGranted(context: Context): Boolean {
        val permissions = checkAllPermissions(context)
        return permissions.values.all { it }
    }
}
