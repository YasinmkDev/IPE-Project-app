package com.example.myapp.services

import android.accessibilityservice.AccessibilityService
import android.content.Intent
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
import android.util.Log
import com.example.myapp.ui.activities.BlockedAppActivity

class MyAccessibilityService : AccessibilityService() {

    companion object {
        private var blockedApps: Set<String> = emptySet()
        private var blockedWebsites: List<String> = emptyList()

        fun setBlockedApps(apps: List<String>) {
            blockedApps = apps.toSet()
            Log.d("AccessibilityService", "Updated blocked apps: $blockedApps")
        }

        fun setBlockedWebsites(websites: List<String>) {
            blockedWebsites = websites
            Log.d("AccessibilityService", "Updated blocked websites: $blockedWebsites")
        }
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent) {
        val packageName = event.packageName?.toString() ?: return

        // 1. App Blocking Logic
        if (event.eventType == AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED) {
            Log.d("AccessibilityService", "Foreground App: $packageName")

            if (blockedApps.contains(packageName)) {
                Log.d("AccessibilityService", "Blocking app: $packageName")
                blockApp(packageName)
                return
            }
        }

        // 2. Website Blocking Logic (Only if a browser is in foreground)
        val browsers = listOf("com.android.chrome", "org.mozilla.firefox", "com.microsoft.emmx")
        if (browsers.contains(packageName)) {
            checkBrowserUrl(rootInActiveWindow)
        }
    }

    private fun checkBrowserUrl(nodeInfo: AccessibilityNodeInfo?) {
        if (nodeInfo == null) return

        // Search for the URL/Address bar. Chrome typically uses this ID:
        val urlNodes = nodeInfo.findAccessibilityNodeInfosByViewId("com.android.chrome:id/url_bar")
        
        if (urlNodes.isNotEmpty()) {
            val url = urlNodes[0].text?.toString()?.lowercase() ?: ""
            Log.d("AccessibilityService", "Detected URL: $url")

            for (blockedSite in blockedWebsites) {
                if (url.contains(blockedSite.lowercase())) {
                    Log.d("AccessibilityService", "Blocking website: $url")
                    blockApp("com.android.chrome", url)
                    break
                }
            }
        }

        // Recursive search for other browsers or older versions
        for (i in 0 until nodeInfo.childCount) {
            checkBrowserUrl(nodeInfo.getChild(i))
        }
    }

    private fun blockApp(packageName: String, url: String? = null) {
        val intent = Intent(this, BlockedAppActivity::class.java).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP)
            addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP)
            putExtra("BLOCKED_PACKAGE", packageName)
            url?.let { putExtra("BLOCKED_URL", it) }
        }
        startActivity(intent)
    }

    override fun onInterrupt() {
        Log.d("AccessibilityService", "Service Interrupted")
    }

    override fun onServiceConnected() {
        super.onServiceConnected()
        Log.d("AccessibilityService", "Service Connected")
    }
}
