package com.example.myapp.receivers

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import com.example.myapp.utils.PackageController

class PackageChangeReceiver : BroadcastReceiver() {
    companion object {
        private const val TAG = "PackageChangeReceiver"
    }

    override fun onReceive(context: Context, intent: Intent) {
        Log.d(TAG, "Package change detected: ${intent.action}")

        when (intent.action) {
            Intent.ACTION_PACKAGE_ADDED -> {
                val packageName = intent.data?.schemeSpecificPart
                packageName?.let {
                    onPackageInstalled(context, it)
                }
            }
            Intent.ACTION_PACKAGE_REMOVED -> {
                val packageName = intent.data?.schemeSpecificPart
                packageName?.let {
                    onPackageUninstalled(context, it)
                }
            }
            Intent.ACTION_PACKAGE_CHANGED -> {
                val packageName = intent.data?.schemeSpecificPart
                packageName?.let {
                    onPackageChanged(context, it)
                }
            }
        }
    }

    private fun onPackageInstalled(context: Context, packageName: String) {
        try {
            Log.d(TAG, "Package installed: $packageName")
            
            val packageController = PackageController(context)
            
            // Check if this package should be blocked based on child's age
            // This would typically be checked against the child's profile
            if (packageController.isAppBlocked(packageName)) {
                packageController.hideApp(packageName)
                packageController.disableUninstall(packageName)
                Log.d(TAG, "Newly installed app blocked and hidden: $packageName")
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error handling package installation: ${e.message}")
        }
    }

    private fun onPackageUninstalled(context: Context, packageName: String) {
        try {
            Log.d(TAG, "Package uninstalled: $packageName")
            
            val sharedPrefs = context.getSharedPreferences("blocked_apps", Context.MODE_PRIVATE)
            val blockedAppsStr = sharedPrefs.getString("blocked_apps", "")
            
            if (blockedAppsStr?.contains(packageName) == true) {
                val blockedApps = blockedAppsStr.split(",").toMutableList()
                blockedApps.remove(packageName)
                sharedPrefs.edit().putString("blocked_apps", blockedApps.joinToString(",")).apply()
                Log.d(TAG, "Removed uninstalled app from blocked list: $packageName")
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error handling package uninstallation: ${e.message}")
        }
    }

    private fun onPackageChanged(context: Context, packageName: String) {
        try {
            Log.d(TAG, "Package changed: $packageName")
            
            val packageController = PackageController(context)
            
            // Re-apply restrictions if package is blocked
            if (packageController.isAppBlocked(packageName)) {
                packageController.hideApp(packageName)
                packageController.disableUninstall(packageName)
                Log.d(TAG, "Reapplied restrictions for updated package: $packageName")
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error handling package change: ${e.message}")
        }
    }
}
