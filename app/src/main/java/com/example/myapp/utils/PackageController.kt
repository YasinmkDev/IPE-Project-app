package com.example.myapp.utils

import android.app.admin.DevicePolicyManager
import android.content.ComponentName
import android.content.Context
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import android.util.Log
import com.example.myapp.receivers.DeviceAdminReceiver

class PackageController(private val context: Context) {
    companion object {
        private const val TAG = "PackageController"
    }

    private val packageManager: PackageManager = context.packageManager
    private val devicePolicyManager: DevicePolicyManager = 
        context.getSystemService(Context.DEVICE_POLICY_SERVICE) as DevicePolicyManager
    private val adminComponent = ComponentName(context, DeviceAdminReceiver::class.java)

    /**
     * Check if app is installed
     */
    fun isAppInstalled(packageName: String): Boolean {
        return try {
            packageManager.getApplicationInfo(packageName, 0)
            true
        } catch (e: PackageManager.NameNotFoundException) {
            false
        }
    }

    /**
     * Get list of all installed packages
     */
    fun getInstalledPackages(): List<String> {
        return try {
            val packages = packageManager.getInstalledApplications(PackageManager.GET_META_DATA)
            packages.map { it.packageName }
        } catch (e: Exception) {
            Log.e(TAG, "Error getting installed packages: ${e.message}")
            emptyList()
        }
    }

    /**
     * Get user-installed packages (exclude system apps)
     */
    fun getUserInstalledPackages(): List<Pair<String, String>> {
        return try {
            val packages = packageManager.getInstalledApplications(PackageManager.GET_META_DATA)
            packages.filter { isUserApp(it) }
                .map { 
                    Pair(
                        it.packageName,
                        it.loadLabel(packageManager).toString()
                    )
                }
                .sortedBy { it.second }
        } catch (e: Exception) {
            Log.e(TAG, "Error getting user packages: ${e.message}")
            emptyList()
        }
    }

    /**
     * Check if app is a user app (not system app)
     */
    private fun isUserApp(appInfo: ApplicationInfo): Boolean {
        return (appInfo.flags and ApplicationInfo.FLAG_SYSTEM) == 0 &&
                appInfo.packageName != context.packageName
    }

    /**
     * Hide app from launcher (requires device admin)
     */
    fun hideApp(packageName: String): Boolean {
        return try {
            if (!isDeviceAdminActive()) {
                Log.w(TAG, "Device admin not active")
                return false
            }

            val componentName = ComponentName(packageName, "")
            devicePolicyManager.setApplicationHidden(adminComponent, packageName, true)
            Log.d(TAG, "App hidden: $packageName")
            true
        } catch (e: Exception) {
            Log.e(TAG, "Error hiding app: ${e.message}")
            false
        }
    }

    /**
     * Unhide app from launcher
     */
    fun unhideApp(packageName: String): Boolean {
        return try {
            if (!isDeviceAdminActive()) {
                Log.w(TAG, "Device admin not active")
                return false
            }

            devicePolicyManager.setApplicationHidden(adminComponent, packageName, false)
            Log.d(TAG, "App unhidden: $packageName")
            true
        } catch (e: Exception) {
            Log.e(TAG, "Error unhiding app: ${e.message}")
            false
        }
    }

    /**
     * Check if app is hidden
     */
    fun isAppHidden(packageName: String): Boolean {
        return try {
            if (!isDeviceAdminActive()) return false
            devicePolicyManager.isApplicationHidden(adminComponent, packageName)
        } catch (e: Exception) {
            Log.e(TAG, "Error checking if app is hidden: ${e.message}")
            false
        }
    }

    /**
     * Disable uninstall for app
     */
    fun disableUninstall(packageName: String): Boolean {
        return try {
            if (!isDeviceAdminActive()) {
                Log.w(TAG, "Device admin not active")
                return false
            }

            devicePolicyManager.setUninstallBlocked(adminComponent, packageName, true)
            Log.d(TAG, "Uninstall blocked: $packageName")
            true
        } catch (e: Exception) {
            Log.e(TAG, "Error disabling uninstall: ${e.message}")
            false
        }
    }

    /**
     * Enable uninstall for app
     */
    fun enableUninstall(packageName: String): Boolean {
        return try {
            if (!isDeviceAdminActive()) {
                Log.w(TAG, "Device admin not active")
                return false
            }

            devicePolicyManager.setUninstallBlocked(adminComponent, packageName, false)
            Log.d(TAG, "Uninstall allowed: $packageName")
            true
        } catch (e: Exception) {
            Log.e(TAG, "Error enabling uninstall: ${e.message}")
            false
        }
    }

    /**
     * Check if uninstall is blocked
     */
    fun isUninstallBlocked(packageName: String): Boolean {
        return try {
            if (!isDeviceAdminActive()) return false
            devicePolicyManager.isUninstallBlocked(adminComponent, packageName)
        } catch (e: Exception) {
            Log.e(TAG, "Error checking uninstall block: ${e.message}")
            false
        }
    }

    /**
     * Lock device (requires device admin)
     */
    fun lockDevice(): Boolean {
        return try {
            if (!isDeviceAdminActive()) {
                Log.w(TAG, "Device admin not active")
                return false
            }

            devicePolicyManager.lockNow()
            true
        } catch (e: Exception) {
            Log.e(TAG, "Error locking device: ${e.message}")
            false
        }
    }

    /**
     * Check if device admin is active
     */
    fun isDeviceAdminActive(): Boolean {
        return devicePolicyManager.isAdminActive(adminComponent)
    }

    /**
     * Get app label/name
     */
    fun getAppLabel(packageName: String): String {
        return try {
            val appInfo = packageManager.getApplicationInfo(packageName, 0)
            appInfo.loadLabel(packageManager).toString()
        } catch (e: Exception) {
            packageName
        }
    }

    /**
     * Get app icon URI
     */
    fun getAppIconDrawable(packageName: String) = try {
        packageManager.getApplicationIcon(packageName)
    } catch (e: Exception) {
        null
    }

    /**
     * Get list of blocked apps
     */
    fun getBlockedApps(): List<String> {
        return try {
            val prefs = context.getSharedPreferences("blocked_apps", Context.MODE_PRIVATE)
            val blockedAppsStr = prefs.getString("blocked_apps", "")
            if (blockedAppsStr.isNullOrEmpty()) {
                emptyList()
            } else {
                blockedAppsStr.split(",").filter { it.isNotEmpty() }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error getting blocked apps: ${e.message}")
            emptyList()
        }
    }

    /**
     * Add app to blocked list
     */
    fun blockApp(packageName: String): Boolean {
        return try {
            val blocked = getBlockedApps().toMutableList()
            if (!blocked.contains(packageName)) {
                blocked.add(packageName)
                saveBlockedApps(blocked)
                hideApp(packageName)
                disableUninstall(packageName)
                Log.d(TAG, "App blocked: $packageName")
                return true
            }
            false
        } catch (e: Exception) {
            Log.e(TAG, "Error blocking app: ${e.message}")
            false
        }
    }

    /**
     * Remove app from blocked list
     */
    fun unblockApp(packageName: String): Boolean {
        return try {
            val blocked = getBlockedApps().toMutableList()
            if (blocked.contains(packageName)) {
                blocked.remove(packageName)
                saveBlockedApps(blocked)
                unhideApp(packageName)
                enableUninstall(packageName)
                Log.d(TAG, "App unblocked: $packageName")
                return true
            }
            false
        } catch (e: Exception) {
            Log.e(TAG, "Error unblocking app: ${e.message}")
            false
        }
    }

    /**
     * Check if app is blocked
     */
    fun isAppBlocked(packageName: String): Boolean {
        return getBlockedApps().contains(packageName)
    }

    /**
     * Save blocked apps list
     */
    private fun saveBlockedApps(apps: List<String>) {
        try {
            val prefs = context.getSharedPreferences("blocked_apps", Context.MODE_PRIVATE)
            prefs.edit().putString("blocked_apps", apps.joinToString(",")).apply()
        } catch (e: Exception) {
            Log.e(TAG, "Error saving blocked apps: ${e.message}")
        }
    }

    /**
     * Clear all app restrictions
     */
    fun clearAllRestrictions() {
        try {
            val blocked = getBlockedApps().toList()
            blocked.forEach { packageName ->
                try {
                    unhideApp(packageName)
                    enableUninstall(packageName)
                } catch (e: Exception) {
                    Log.e(TAG, "Error clearing restriction for $packageName: ${e.message}")
                }
            }
            saveBlockedApps(emptyList())
            Log.d(TAG, "All restrictions cleared")
        } catch (e: Exception) {
            Log.e(TAG, "Error clearing restrictions: ${e.message}")
        }
    }
}
