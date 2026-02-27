package com.example.myapp.receivers

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import com.example.myapp.services.MonitoringService

class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action == Intent.ACTION_BOOT_COMPLETED || intent.action == "android.intent.action.QUICKBOOT_POWERON") {
            Log.d(TAG, "Device boot completed, starting monitoring service")
            startMonitoringService(context)
        }
    }

    private fun startMonitoringService(context: Context) {
        // In a real app, we would store childId in shared preferences
        // For prototype purposes, we can use a hardcoded value or retrieve from shared preferences
        val childId = getStoredChildId(context)
        if (childId.isNotEmpty()) {
            val serviceIntent = Intent(context, MonitoringService::class.java)
            serviceIntent.putExtra("CHILD_ID", childId)
            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
                context.startForegroundService(serviceIntent)
            } else {
                context.startService(serviceIntent)
            }
        } else {
            Log.d(TAG, "No childId stored, skipping service startup")
        }
    }

    private fun getStoredChildId(context: Context): String {
        val sharedPreferences = context.getSharedPreferences("APP_PREFS", Context.MODE_PRIVATE)
        return sharedPreferences.getString("CHILD_ID", "") ?: ""
    }

    companion object {
        private const val TAG = "BootReceiver"
    }
}
