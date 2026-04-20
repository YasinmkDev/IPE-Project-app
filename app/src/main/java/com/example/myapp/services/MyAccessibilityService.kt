package com.example.myapp.services

import android.accessibilityservice.AccessibilityService
import android.graphics.Bitmap
import android.hardware.HardwareBuffer
import android.os.Build
import android.os.Bundle
import android.util.Base64
import android.view.Display
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
import android.util.Log
import com.example.myapp.R
import java.io.ByteArrayOutputStream
import java.util.concurrent.ConcurrentHashMap

class MyAccessibilityService : AccessibilityService() {

    companion object {
        private const val TAG = "MyAccessibilityService"
        private const val ENFORCEMENT_COOLDOWN_MS = 1200L

        private var blockedApps: List<String> = emptyList()
        private var blockedWebsites: List<String> = emptyList()
        private var storageRestricted: Boolean = false
        private var protectionActive: Boolean = true
        private var settingsTamperProtectionActive: Boolean = true
        private var parentUninstallMode: Boolean = false
        private var childId: String? = null
        private var serviceInstance: MyAccessibilityService? = null
        private val lastEnforcementByTarget = ConcurrentHashMap<String, Long>()

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
            blockedApps = apps
        }

        fun setBlockedWebsites(websites: List<String>) {
            blockedWebsites = websites
        }

        fun setStorageRestricted(restricted: Boolean) {
            storageRestricted = restricted
        }
        
        fun setProtectionActive(active: Boolean) {
            protectionActive = active
        }

        fun setSettingsTamperProtectionActive(active: Boolean) {
            settingsTamperProtectionActive = active
        }

        fun setChildId(id: String?) {
            childId = id
        }

        fun setParentUninstallMode(enabled: Boolean) {
            parentUninstallMode = enabled
        }

        fun captureRemoteScreenshot(
            childId: String,
            requestAt: Long,
            requestedBy: String,
            onResult: (Boolean, String?) -> Unit
        ) {
            val service = serviceInstance
            if (service == null) {
                onResult(false, "accessibility_service_unavailable")
                return
            }
            service.captureCurrentScreen(childId, requestAt, requestedBy, onResult)
        }
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent) {
        val packageName = event.packageName?.toString() ?: return
        if (packageName == this.packageName) return

        if (protectionActive) {
            // App Blocking
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

            // Website Blocking
            val browsers = listOf("com.android.chrome", "org.mozilla.firefox", "com.microsoft.emmx")
            if (browsers.contains(packageName)) {
                checkBrowserUrl(rootInActiveWindow)
            }
        }

        // Settings Anti-Tamper
        if (settingsTamperProtectionActive && !parentUninstallMode && packageName == "com.android.settings") {
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
        val enforced = enforceSilentClose("tamper:settings")
        if (enforced) {
            emitPolicyEvent(
                type = "TAMPER_ATTEMPT",
                severity = "high",
                details = mapOf("target" to "settings")
            )
        }
    }

    private fun checkBrowserUrl(nodeInfo: AccessibilityNodeInfo?) {
        if (nodeInfo == null) return
        val urlNodes = nodeInfo.findAccessibilityNodeInfosByViewId("com.android.chrome:id/url_bar")
        if (urlNodes.isNotEmpty()) {
            val url = urlNodes[0].text?.toString()?.lowercase() ?: ""
            val normalizedHost = normalizeDomain(url)
            if (normalizedHost.isNotEmpty() && WebsiteFilterVpnService.shouldBlockDomain(normalizedHost)) {
                blockWebsiteInBrowser(urlNodes[0], url, "com.android.chrome")
                return
            }
            for (blockedSite in blockedWebsites) {
                if (url.contains(blockedSite.lowercase())) {
                    blockWebsiteInBrowser(urlNodes[0], url, "com.android.chrome")
                    break
                }
            }
        }
        for (i in 0 until nodeInfo.childCount) {
            checkBrowserUrl(nodeInfo.getChild(i))
        }
    }

    private fun blockWebsiteInBrowser(
        urlNode: AccessibilityNodeInfo,
        blockedUrl: String,
        packageName: String
    ) {
        val targetKey = "website:$packageName"
        if (!canEnforce(targetKey)) return

        // Keep browser open, only neutralize blocked page.
        val replacement = Bundle().apply {
            putCharSequence(
                AccessibilityNodeInfo.ACTION_ARGUMENT_SET_TEXT_CHARSEQUENCE,
                "about:blank"
            )
        }
        val setTextOk = urlNode.performAction(AccessibilityNodeInfo.ACTION_SET_TEXT, replacement)
        val focusOk = urlNode.performAction(AccessibilityNodeInfo.ACTION_FOCUS)
        val clickOk = urlNode.performAction(AccessibilityNodeInfo.ACTION_CLICK)
        val backOk = if (!setTextOk && !focusOk && !clickOk) performGlobalAction(GLOBAL_ACTION_BACK) else false

        Log.i(
            TAG,
            "Blocked website in-browser. package=$packageName url=$blockedUrl setTextOk=$setTextOk focusOk=$focusOk clickOk=$clickOk backOk=$backOk"
        )
        emitPolicyEvent(
            type = "BLOCKED_WEBSITE",
            severity = "medium",
            details = mapOf(
                "packageName" to packageName,
                "reason" to "website",
                "url" to blockedUrl
            )
        )
    }

    private fun canEnforce(targetKey: String): Boolean {
        val now = System.currentTimeMillis()
        val last = lastEnforcementByTarget[targetKey] ?: 0L
        if (now - last < ENFORCEMENT_COOLDOWN_MS) {
            return false
        }
        lastEnforcementByTarget[targetKey] = now
        return true
    }

    private fun blockApp(packageName: String, url: String? = null, isStorageBlock: Boolean = false) {
        val reasonPrefix = when {
            isStorageBlock -> "storage"
            !url.isNullOrEmpty() -> "website"
            else -> "app"
        }
        val targetKey = "$reasonPrefix:$packageName"
        val enforced = enforceSilentClose(targetKey)
        if (enforced) {
            Log.i(
                TAG,
                "Restricted target closed silently. reason=$reasonPrefix package=$packageName url=${url ?: ""}"
            )
            emitPolicyEvent(
                type = when (reasonPrefix) {
                    "website" -> "BLOCKED_WEBSITE"
                    "storage" -> "BLOCKED_STORAGE_ACCESS"
                    else -> "BLOCKED_APP"
                },
                severity = if (reasonPrefix == "website") "medium" else "high",
                details = mapOf(
                    "packageName" to packageName,
                    "reason" to reasonPrefix,
                    "url" to (url ?: "")
                )
            )
        }
    }

    private fun enforceSilentClose(targetKey: String): Boolean {
        if (!canEnforce(targetKey)) return false

        // BACK + HOME reduces edge cases where HOME alone is ignored.
        val backOk = performGlobalAction(GLOBAL_ACTION_BACK)
        val homeOk = performGlobalAction(GLOBAL_ACTION_HOME)
        if (!homeOk) {
            Log.w(TAG, "Silent close failed for key=$targetKey (backOk=$backOk, homeOk=$homeOk)")
        }
        return homeOk || backOk
    }

    private fun emitPolicyEvent(type: String, severity: String, details: Map<String, String>) {
        val linkedChildId = childId ?: return
        FirebaseService.logChildEvent(
            childId = linkedChildId,
            event = FirebaseService.ChildEvent(
                type = type,
                severity = severity,
                details = details,
                timestamp = System.currentTimeMillis()
            )
        )
    }

    private fun normalizeDomain(input: String): String {
        var value = input.trim().lowercase()
        value = value.removePrefix("https://").removePrefix("http://")
        value = value.substringBefore("/")
        return value.removePrefix("www.")
    }

    override fun onInterrupt() {}

    override fun onServiceConnected() {
        super.onServiceConnected()
        serviceInstance = this
        Log.d("AccessibilityService", "Service Connected")
    }

    override fun onDestroy() {
        if (serviceInstance === this) {
            serviceInstance = null
        }
        super.onDestroy()
    }

    private fun captureCurrentScreen(
        childId: String,
        requestAt: Long,
        requestedBy: String,
        onResult: (Boolean, String?) -> Unit
    ) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.R) {
            onResult(false, "screenshot_not_supported_api")
            return
        }
        takeScreenshot(Display.DEFAULT_DISPLAY, mainExecutor, object : AccessibilityService.TakeScreenshotCallback {
            override fun onSuccess(result: AccessibilityService.ScreenshotResult) {
                try {
                    val hwBuffer: HardwareBuffer = result.hardwareBuffer
                    val bitmap = Bitmap.wrapHardwareBuffer(hwBuffer, result.colorSpace)
                    hwBuffer.close()
                    if (bitmap == null) {
                        onResult(false, "bitmap_wrap_failed")
                        return
                    }
                    val safeBitmap = bitmap.copy(Bitmap.Config.ARGB_8888, false)
                    bitmap.recycle()

                    val stream = ByteArrayOutputStream()
                    safeBitmap.compress(Bitmap.CompressFormat.JPEG, 55, stream)
                    safeBitmap.recycle()
                    val encoded = Base64.encodeToString(stream.toByteArray(), Base64.NO_WRAP)

                    FirebaseService.logRemoteScreenshot(
                        childId = childId,
                        screenshot = FirebaseService.RemoteScreenshot(
                            imageBase64 = encoded,
                            mimeType = "image/jpeg",
                            capturedAt = System.currentTimeMillis(),
                            requestedBy = requestedBy,
                            requestAt = requestAt
                        )
                    )
                    onResult(true, null)
                } catch (e: Exception) {
                    onResult(false, e.message ?: "screenshot_failed")
                }
            }

            override fun onFailure(errorCode: Int) {
                onResult(false, "screenshot_error_$errorCode")
            }
        })
    }
}
