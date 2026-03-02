package com.example.myapp.services

import android.accessibilityservice.AccessibilityService
import android.content.Intent
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
import android.util.Log
import com.example.myapp.R
import com.example.myapp.ui.activities.BlockedAppActivity

class MyAccessibilityService : AccessibilityService() {

    companion object {
        private var blockedApps: Set<String> = emptySet()
        private var blockedWebsites: List<String> = emptyList()
        private var storageRestricted: Boolean = false
        // IMPORTANT: Default to FALSE so it doesn't block during setup!
        private var protectionActive: Boolean = false

        private val fileManagerPackages = setOf(
            "com.google.android.documentsui",
            "com.android.documentsui",
            "com.google.android.apps.nbu.files",
            "com.sec.android.app.myfiles",
            "com.mi.android.globalFileexplorer",
            "com.coloros.filemanager",
            "com.huawei.hidisk",
            "com.android.filemanager"
        )

        fun setBlockedApps(apps: List<String>) {
            blockedApps = apps.toSet()
        }

        fun setBlockedWebsites(websites: List<String>) {
            blockedWebsites = websites
        }

        fun setStorageRestricted(restricted: Boolean) {
            storageRestricted = restricted
        }
        
        fun setProtectionActive(active: Boolean) {
            protectionActive = active
            Log.d("AccessibilityService", "Protection status updated: $active")
        }
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent) {
        // Only run blocking logic if protection is explicitly ON
        if (!protectionActive) return

        val packageName = event.packageName?.toString() ?: return

        if (event.eventType == AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED) {
            if (blockedApps.contains(packageName)) {
                blockApp(packageName)
                return
            }

            if (storageRestricted && fileManagerPackages.contains(packageName)) {
                blockApp(packageName, isStorageBlock = true)
                return
            }
        }

        val browsers = listOf("com.android.chrome", "org.mozilla.firefox", "com.microsoft.emmx")
        if (browsers.contains(packageName)) {
            checkBrowserUrl(rootInActiveWindow)
        }

        if (packageName == "com.android.settings") {
            checkSettingsTampering(rootInActiveWindow)
        }
    }

    private fun checkSettingsTampering(nodeInfo: AccessibilityNodeInfo?) {
        if (nodeInfo == null) return
        val appName = getString(R.string.app_name)
        val textMatches = nodeInfo.findAccessibilityNodeInfosByText(appName)
        if (textMatches.isNotEmpty()) {
            val dangerousButtons = listOf("Uninstall", "Deactivate", "Force stop", "Clear data", "Remove")
            for (buttonText in dangerousButtons) {
                val buttons = nodeInfo.findAccessibilityNodeInfosByText(buttonText)
                if (buttons.isNotEmpty()) {
                    closeSettingsAndGoHome()
                    return
                }
            }
        }
        for (i in 0 until nodeInfo.childCount) {
            checkSettingsTampering(nodeInfo.getChild(i))
        }
    }

    private fun closeSettingsAndGoHome() {
        val intent = Intent(Intent.ACTION_MAIN).apply {
            addCategory(Intent.CATEGORY_HOME)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        startActivity(intent)
        
        val blockIntent = Intent(this, BlockedAppActivity::class.java).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            putExtra("TAMPER_ATTEMPT", true)
        }
        startActivity(blockIntent)
    }

    private fun checkBrowserUrl(nodeInfo: AccessibilityNodeInfo?) {
        if (nodeInfo == null) return
        val urlNodes = nodeInfo.findAccessibilityNodeInfosByViewId("com.android.chrome:id/url_bar")
        if (urlNodes.isNotEmpty()) {
            val url = urlNodes[0].text?.toString()?.lowercase() ?: ""
            for (blockedSite in blockedWebsites) {
                if (url.contains(blockedSite.lowercase())) {
                    blockApp("com.android.chrome", url)
                    break
                }
            }
        }
        for (i in 0 until nodeInfo.childCount) {
            checkBrowserUrl(nodeInfo.getChild(i))
        }
    }

    private fun blockApp(packageName: String, url: String? = null, isStorageBlock: Boolean = false) {
        val intent = Intent(this, BlockedAppActivity::class.java).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP)
            addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP)
            putExtra("BLOCKED_PACKAGE", packageName)
            if (isStorageBlock) putExtra("STORAGE_RESTRICTED", true)
            url?.let { putExtra("BLOCKED_URL", it) }
        }
        startActivity(intent)
    }

    override fun onInterrupt() {}
}
