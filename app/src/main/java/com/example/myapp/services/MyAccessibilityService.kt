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
import java.net.URI
import java.io.ByteArrayOutputStream

class MyAccessibilityService : AccessibilityService() {
    private var lastRedirectAt: Long = 0L
    private var lastRedirectUrl: String = ""

    companion object {
        private const val TAG = "MyAccessibilityService"
        private var blockedApps: Set<String> = emptySet()
        private var blockedWebsites: List<String> = emptyList()
        private var storageRestricted: Boolean = false
        private var protectionActive: Boolean = true
        private var childId: String? = null
        private var serviceInstance: MyAccessibilityService? = null

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
        }

        fun setChildId(id: String?) {
            childId = id
        }

        fun captureRemoteScreenshot(
            childId: String,
            requestAt: Long,
            requestedBy: String,
            onResult: (Boolean, String?) -> Unit
        ) {
            val service = serviceInstance
            if (service == null) {
                onResult(false, "service_not_connected")
                return
            }
            service.takeScreenCapture(childId, requestAt, requestedBy, onResult)
        }

        fun captureScreenAsBase64(onResult: (String?) -> Unit) {
            val service = serviceInstance
            if (service == null) {
                onResult(null)
                return
            }
            service.takeScreenCaptureBase64(onResult)
        }
    }

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

    override fun onAccessibilityEvent(event: AccessibilityEvent) {
        if (!protectionActive) return

        val packageName = event.packageName?.toString() ?: return
        if (packageName == this.packageName) return

        // 1. App Blocking Logic
        if (event.eventType == AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED) {
            val isBlocked = blockedApps.contains(packageName)
            val isFileManagerBlocked = storageRestricted && fileManagerPackages.contains(packageName)

            if (isBlocked || isFileManagerBlocked) {
                Log.d(TAG, "Blocking restricted app: $packageName")
                FirebaseService.logEvent(childId ?: "", FirebaseService.ActivityEvent(
                    type = "BLOCKED_APP",
                    severity = "high",
                    title = "App Blocked",
                    details = "Attempted to open: $packageName"
                ))
                performGlobalAction(GLOBAL_ACTION_HOME)
                return
            }
        }

        // 2. Website Monitoring
        val browsers = listOf("com.android.chrome", "org.mozilla.firefox", "com.microsoft.emmx")
        if (browsers.contains(packageName)) {
            checkBrowserUrl(rootInActiveWindow, packageName)
        }

        // 3. Settings Anti-Tamper
        if (packageName == "com.android.settings") {
            checkSettingsTampering(rootInActiveWindow)
        }
    }

    private fun checkBrowserUrl(nodeInfo: AccessibilityNodeInfo?, browserPackage: String) {
        if (nodeInfo == null) return
        val urlNodes = nodeInfo.findAccessibilityNodeInfosByViewId("com.android.chrome:id/url_bar")
        if (urlNodes.isNotEmpty()) {
            val urlNode = urlNodes[0]
            val url = urlNode.text?.toString()?.lowercase() ?: ""
            
            if (url.isNotEmpty() && !url.contains("about:blank")) {
                for (blockedSite in blockedWebsites) {
                    if (isBlockedHost(url, blockedSite)) {
                        Log.d(TAG, "Blocked site detected: $url. Redirecting...")
                        
                        FirebaseService.logEvent(childId ?: "", FirebaseService.ActivityEvent(
                            type = "BLOCKED_WEBSITE",
                            severity = "medium",
                            title = "Restricted Website",
                            details = "Blocked access to: $url"
                        ))
                        
                        redirectActiveTabToSafeUrl(urlNode, url, browserPackage)
                        break
                    }
                }
            }
        }
        for (i in 0 until nodeInfo.childCount) {
            checkBrowserUrl(nodeInfo.getChild(i), browserPackage)
        }
    }

    private fun checkSettingsTampering(nodeInfo: AccessibilityNodeInfo?) {
        if (nodeInfo == null) return
        val appName = getString(R.string.app_name)
        if (nodeInfo.findAccessibilityNodeInfosByText(appName).isNotEmpty()) {
            val dangerous = listOf("Uninstall", "Deactivate", "Force stop", "Clear data")
            for (text in dangerous) {
                if (nodeInfo.findAccessibilityNodeInfosByText(text).isNotEmpty()) {
                    FirebaseService.logEvent(childId ?: "", FirebaseService.ActivityEvent(
                        type = "TAMPER",
                        severity = "high",
                        title = "Security Alert",
                        details = "Tamper attempt detected in Settings"
                    ))
                    performGlobalAction(GLOBAL_ACTION_HOME)
                    return
                }
            }
        }
        for (i in 0 until nodeInfo.childCount) {
            checkSettingsTampering(nodeInfo.getChild(i))
        }
    }

    private fun takeScreenCapture(
        childId: String,
        requestAt: Long,
        requestedBy: String,
        onResult: (Boolean, String?) -> Unit
    ) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.R) {
            onResult(false, "api_not_supported")
            return
        }

        try {
            takeScreenshot(Display.DEFAULT_DISPLAY, mainExecutor, object : AccessibilityService.TakeScreenshotCallback {
                override fun onSuccess(result: AccessibilityService.ScreenshotResult) {
                    try {
                        val hwBuffer: HardwareBuffer = result.hardwareBuffer
                        val bitmap = Bitmap.wrapHardwareBuffer(hwBuffer, result.colorSpace)
                        hwBuffer.close()
                        
                        if (bitmap != null) {
                            // Create software copy for compression
                            val softwareBitmap = bitmap.copy(Bitmap.Config.ARGB_8888, false)
                            val stream = ByteArrayOutputStream()
                            softwareBitmap.compress(Bitmap.CompressFormat.JPEG, 55, stream)
                            val encoded = Base64.encodeToString(stream.toByteArray(), Base64.NO_WRAP)
                            
                            Log.d(TAG, "Uploading screenshot to Firestore as Base64...")
                            FirebaseService.uploadRemoteScreenshot(
                                childId = childId,
                                base64Image = encoded,
                                requestAt = requestAt,
                                requestedBy = requestedBy,
                                onSuccess = {
                                    onResult(true, null)
                                    softwareBitmap.recycle()
                                },
                                onFailure = { e ->
                                    onResult(false, e.message ?: "screenshot_upload_failed")
                                    softwareBitmap.recycle()
                                }
                            )
                        } else {
                            onResult(false, "bitmap_capture_failed")
                        }
                    } catch (e: Exception) {
                        Log.e(TAG, "Screenshot processing error: ${e.message}")
                        onResult(false, e.message)
                    }
                }

                override fun onFailure(errorCode: Int) {
                    Log.e(TAG, "Screenshot API failure: $errorCode")
                    onResult(false, "error_code_$errorCode")
                }
            })
        } catch (se: SecurityException) {
            Log.e(TAG, "Screenshot security capability missing: ${se.message}", se)
            onResult(false, "screenshot_capability_not_granted")
        } catch (e: Exception) {
            Log.e(TAG, "Unexpected screenshot exception: ${e.message}", e)
            onResult(false, "screenshot_unexpected_error")
        }
    }

    override fun onInterrupt() {}

    override fun onServiceConnected() {
        super.onServiceConnected()
        serviceInstance = this
        Log.d(TAG, "Service Connected and ready for screenshots")
    }

    override fun onDestroy() {
        if (serviceInstance === this) serviceInstance = null
        super.onDestroy()
    }

    private fun takeScreenCaptureBase64(onResult: (String?) -> Unit) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.R) {
            onResult(null)
            return
        }
        try {
            takeScreenshot(Display.DEFAULT_DISPLAY, mainExecutor, object : AccessibilityService.TakeScreenshotCallback {
                override fun onSuccess(result: AccessibilityService.ScreenshotResult) {
                    try {
                        val hwBuffer: HardwareBuffer = result.hardwareBuffer
                        val bitmap = Bitmap.wrapHardwareBuffer(hwBuffer, result.colorSpace)
                        hwBuffer.close()
                        if (bitmap == null) {
                            onResult(null)
                            return
                        }
                        val softwareBitmap = bitmap.copy(Bitmap.Config.ARGB_8888, false)
                        val stream = ByteArrayOutputStream()
                        softwareBitmap.compress(Bitmap.CompressFormat.JPEG, 40, stream)
                        val encoded = Base64.encodeToString(stream.toByteArray(), Base64.NO_WRAP)
                        softwareBitmap.recycle()
                        onResult(encoded)
                    } catch (_: Exception) {
                        onResult(null)
                    }
                }

                override fun onFailure(errorCode: Int) {
                    Log.e(TAG, "Base64 screenshot failure: $errorCode")
                    onResult(null)
                }
            })
        } catch (se: SecurityException) {
            Log.e(TAG, "Base64 screenshot capability not granted: ${se.message}", se)
            onResult(null)
        } catch (e: Exception) {
            Log.e(TAG, "Unexpected base64 screenshot exception: ${e.message}", e)
            onResult(null)
        }
    }

    private fun isBlockedHost(urlOrHost: String, blockedPattern: String): Boolean {
        val normalizedPattern = blockedPattern
            .trim()
            .lowercase()
            .removePrefix("http://")
            .removePrefix("https://")
            .removePrefix("www.")
            .substringBefore("/")
            .substringBefore("?")
            .substringBefore("#")
        if (normalizedPattern.isEmpty()) return false

        val host = try {
            val parsed = if (urlOrHost.startsWith("http://") || urlOrHost.startsWith("https://")) {
                URI(urlOrHost).host
            } else {
                urlOrHost
                    .lowercase()
                    .substringBefore("/")
                    .substringBefore("?")
                    .substringBefore("#")
            }
            (parsed ?: "")
                .lowercase()
                .removePrefix("www.")
        } catch (_: Exception) {
            urlOrHost
                .lowercase()
                .substringBefore("/")
                .substringBefore("?")
                .substringBefore("#")
                .removePrefix("www.")
        }
        if (host.isEmpty()) return false
        return host == normalizedPattern || host.endsWith(".$normalizedPattern")
    }

    private fun redirectActiveTabToSafeUrl(
        urlNode: AccessibilityNodeInfo,
        blockedUrl: String,
        browserPackage: String
    ) {
        val now = System.currentTimeMillis()
        if (now - lastRedirectAt < 1500L && blockedUrl == lastRedirectUrl) return
        lastRedirectAt = now
        lastRedirectUrl = blockedUrl

        // Redirect inside the currently active tab only.
        urlNode.performAction(AccessibilityNodeInfo.ACTION_FOCUS)
        urlNode.performAction(AccessibilityNodeInfo.ACTION_CLICK)

        val args = Bundle().apply {
            putCharSequence(
                AccessibilityNodeInfo.ACTION_ARGUMENT_SET_TEXT_CHARSEQUENCE,
                getBrowserHomeUrl(browserPackage)
            )
        }
        val setOk = urlNode.performAction(AccessibilityNodeInfo.ACTION_SET_TEXT, args)
        if (!setOk) return

        // Submit navigation in the same tab with fallbacks.
        val imeSubmitted =
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                urlNode.performAction(AccessibilityNodeInfo.AccessibilityAction.ACTION_IME_ENTER.id)
            } else {
                false
            }
        var submitted = imeSubmitted
        if (!submitted) {
            submitted = clickBrowserSubmitButton(browserPackage)
        }
        if (!submitted) {
            // Final fallback: click URL bar again to trigger commit behavior.
            urlNode.performAction(AccessibilityNodeInfo.ACTION_CLICK)
        }

    }

    private fun getBrowserHomeUrl(browserPackage: String): String {
        return when (browserPackage) {
            "com.android.chrome" -> "chrome://newtab/"
            "com.microsoft.emmx" -> "edge://newtab"
            "org.mozilla.firefox" -> "about:home"
            else -> "about:newtab"
        }
    }

    private fun clickBrowserSubmitButton(browserPackage: String): Boolean {
        val root = rootInActiveWindow ?: return false

        if (browserPackage == "com.android.chrome") {
            val byId = root.findAccessibilityNodeInfosByViewId("com.android.chrome:id/url_action_button")
            if (clickFirstActionableNode(byId)) return true
        }

        val submitKeywords = listOf("Go", "Search", "Visit", "Enter", "go", "search", "visit", "enter")
        submitKeywords.forEach { keyword ->
            val matches = root.findAccessibilityNodeInfosByText(keyword)
            if (clickFirstActionableNode(matches)) return true
        }
        return false
    }

    private fun clickFirstActionableNode(nodes: List<AccessibilityNodeInfo>?): Boolean {
        if (nodes.isNullOrEmpty()) return false
        nodes.forEach { node ->
            if (node.isClickable && node.performAction(AccessibilityNodeInfo.ACTION_CLICK)) return true
            var parent = node.parent
            while (parent != null) {
                if (parent.isClickable && parent.performAction(AccessibilityNodeInfo.ACTION_CLICK)) return true
                parent = parent.parent
            }
        }
        return false
    }

}
