package com.example.myapp.services

import android.accessibilityservice.AccessibilityService
import android.content.Intent
import android.view.accessibility.AccessibilityEvent
import android.util.Log
import com.example.myapp.ui.activities.BlockedAppActivity

class MyAccessibilityService : AccessibilityService() {

    companion object {
        private var blockedApps: Set<String> = emptySet()

        fun setBlockedApps(apps: List<String>) {
            blockedApps = apps.toSet()
            Log.d("AccessibilityService", "Updated blocked apps: $blockedApps")
        }
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent) {
        if (event.eventType == AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED) {
            val packageName = event.packageName?.toString() ?: return
            Log.d("AccessibilityService", "Foreground App: $packageName")

            if (blockedApps.contains(packageName)) {
                Log.d("AccessibilityService", "Blocking app: $packageName")
                val intent = Intent(this, BlockedAppActivity::class.java).apply {
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP)
                    addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP)
                    putExtra("BLOCKED_PACKAGE", packageName)
                }
                startActivity(intent)
            }
        }
    }

    override fun onInterrupt() {
        Log.d("AccessibilityService", "Service Interrupted")
    }

    override fun onServiceConnected() {
        super.onServiceConnected()
        Log.d("AccessibilityService", "Service Connected")
    }
}
