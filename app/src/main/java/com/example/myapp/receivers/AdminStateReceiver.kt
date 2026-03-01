package com.example.myapp.receivers

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log

class AdminStateReceiver : BroadcastReceiver() {
    companion object {
        private const val TAG = "AdminStateReceiver"
    }

    override fun onReceive(context: Context, intent: Intent) {
        when (intent.action) {
            "android.app.action.DEVICE_ADMIN_ENABLED" -> {
                Log.d(TAG, "Device Admin enabled")
                onDeviceAdminEnabled(context)
            }
            "android.app.action.DEVICE_ADMIN_DISABLED" -> {
                Log.d(TAG, "Device Admin disabled")
                onDeviceAdminDisabled(context)
            }
        }
    }

    private fun onDeviceAdminEnabled(context: Context) {
        try {
            Log.d(TAG, "Device admin enabled - parental control features now active")
            
            // Save the state to SharedPreferences
            val prefs = context.getSharedPreferences("device_admin_state", Context.MODE_PRIVATE)
            prefs.edit().putBoolean("is_admin_enabled", true).apply()
            
            // Log to Firebase if needed
            // FirebaseService.logEvent("device_admin_enabled")
        } catch (e: Exception) {
            Log.e(TAG, "Error in onDeviceAdminEnabled: ${e.message}")
        }
    }

    private fun onDeviceAdminDisabled(context: Context) {
        try {
            Log.w(TAG, "Device admin disabled - parental control features may be compromised")
            
            // Save the state to SharedPreferences
            val prefs = context.getSharedPreferences("device_admin_state", Context.MODE_PRIVATE)
            prefs.edit().putBoolean("is_admin_enabled", false).apply()
            
            // Alert parent about this critical event
            // FirebaseService.logSecurityEvent("device_admin_disabled")
            
            // Attempt to re-enable if this is unexpected
            // In production, this would prompt the parent to re-enable admin
        } catch (e: Exception) {
            Log.e(TAG, "Error in onDeviceAdminDisabled: ${e.message}")
        }
    }
}
