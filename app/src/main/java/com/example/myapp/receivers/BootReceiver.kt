package com.example.myapp.receivers

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Build
import android.util.Log
import com.example.myapp.services.MonitoringService

class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action == Intent.ACTION_BOOT_COMPLETED || intent.action == "android.intent.action.QUICKBOOT_POWERON") {
            Log.d(TAG, "Device boot completed, starting monitoring service")
            try {
                startMonitoringService(context)
            } catch (e: Exception) {
                Log.e(TAG, "Failed to start monitoring service after boot: ${e.message}", e)
            }
        }
    }

    private fun startMonitoringService(context: Context) {
        try {
            // In a real app, we would store childId in shared preferences
            // For prototype purposes, we can use a hardcoded value or retrieve from shared preferences
            val childId = getStoredChildId(context)
            if (childId.isNotEmpty()) {
                val serviceIntent = Intent(context, MonitoringService::class.java)
                serviceIntent.putExtra("CHILD_ID", childId)
                
                try {
                    // Use startForegroundService for Android 8.0+
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                        context.startForegroundService(serviceIntent)
                        Log.d(TAG, "Started foreground service successfully")
                    } else {
                        context.startService(serviceIntent)
                        Log.d(TAG, "Started background service successfully")
                    }
                } catch (e: IllegalStateException) {
                    Log.w(TAG, "Cannot start foreground service (may be restricted on Android 14+): ${e.message}")
                    // Fallback to regular service start
                    try {
                        context.startService(serviceIntent)
                    } catch (fallbackError: Exception) {
                        Log.e(TAG, "Failed to start service even with fallback: ${fallbackError.message}")
                    }
                }
            } else {
                Log.d(TAG, "No childId stored, skipping service startup")
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error in startMonitoringService: ${e.message}", e)
        }
    }

    private fun getStoredChildId(context: Context): String {
        return try {
            val sharedPreferences = context.getSharedPreferences("APP_PREFS", Context.MODE_PRIVATE)
            sharedPreferences.getString("CHILD_ID", "") ?: ""
        } catch (e: Exception) {
            Log.e(TAG, "Failed to retrieve stored childId: ${e.message}")
            ""
        }
    }

    companion object {
        private const val TAG = "BootReceiver"
    }
}
