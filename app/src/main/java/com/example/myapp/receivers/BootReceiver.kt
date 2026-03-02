package com.example.myapp.receivers

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Build
import android.util.Log
import com.example.myapp.services.MonitoringService

class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val action = intent.action
        Log.d(TAG, "Received boot event: $action")
        
        if (action == Intent.ACTION_BOOT_COMPLETED || 
            action == Intent.ACTION_LOCKED_BOOT_COMPLETED ||
            action == "android.intent.action.QUICKBOOT_POWERON") {
            
            try {
                startMonitoringService(context)
            } catch (e: Exception) {
                Log.e(TAG, "Failed to start monitoring service after boot: ${e.message}", e)
            }
        }
    }

    private fun startMonitoringService(context: Context) {
        try {
            val childId = getStoredChildId(context)
            if (childId.isNotEmpty()) {
                val serviceIntent = Intent(context, MonitoringService::class.java)
                serviceIntent.putExtra("CHILD_ID", childId)
                
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    context.startForegroundService(serviceIntent)
                    Log.d(TAG, "Started foreground service from boot")
                } else {
                    context.startService(serviceIntent)
                    Log.d(TAG, "Started background service from boot")
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
            // Use device protected storage to read ID before decryption if possible
            val protectedContext = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                context.createDeviceProtectedStorageContext()
            } else {
                context
            }
            val sharedPreferences = protectedContext.getSharedPreferences("APP_PREFS", Context.MODE_PRIVATE)
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
