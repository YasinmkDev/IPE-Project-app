package com.example.myapp.services

import android.app.AlarmManager
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import android.os.SystemClock
import android.util.Log
import androidx.core.app.NotificationCompat
import com.example.myapp.R
import com.example.myapp.MainActivity
import com.example.myapp.models.AgeGroup
import com.example.myapp.models.AgeGroupManager
import com.example.myapp.utils.PackageController
import com.example.myapp.utils.ProtectedStorageUtil
import java.util.Timer
import java.util.TimerTask

class MonitoringService : Service() {
    private val CHANNEL_ID = "MonitoringServiceChannel"
    private val NOTIFICATION_ID = 123
    private var timer: Timer? = null
    private var currentChildId: String? = null
    private val ageGroupManager = AgeGroupManager()
    private var dnsBypassLogged = false
    private var parentUninstallModeApplied = false
    private var lastAgeAssessmentPushAt: Long = 0L
    private var lastScreenshotRequestHandledAt: Long = 0L
    
    // Screen Time Tracking variables
    private var lastAppPackage: String? = null
    private var appStartTime: Long = 0
    private val appDurations = mutableMapOf<String, Long>()
    private var lastBehaviorScanAt: Long = 0L
    private var lastBehaviorCursorAt: Long = System.currentTimeMillis() - 60_000L

    override fun onCreate() {
        super.onCreate()
        Log.d(TAG, "Service onCreate")
        createNotificationChannel()
        
        val notification = createNotification()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            startForeground(NOTIFICATION_ID, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC)
        } else {
            startForeground(NOTIFICATION_ID, notification)
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val childId = intent?.getStringExtra("CHILD_ID") ?: ProtectedStorageUtil.getStoredChildId(this)
        currentChildId = childId
        MyAccessibilityService.setChildId(childId)
        WebsiteFilterVpnService.setChildId(childId)
        
        Log.d(TAG, "Service onStartCommand with childId: $currentChildId")

        val notification = createNotification()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            startForeground(NOTIFICATION_ID, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC)
        } else {
            startForeground(NOTIFICATION_ID, notification)
        }

        childId?.let { id ->
            FirebaseService.fetchChildProfile(
                id,
                onSuccess = { profile -> updateMonitoringData(profile) },
                onFailure = { Log.e(TAG, "Error fetching profile") }
            )

            FirebaseService.listenToChildProfileUpdates(
                id,
                onUpdate = { profile -> updateMonitoringData(profile) },
                onError = { Log.e(TAG, "Error listening updates") }
            )
            startMonitoring()
        }

        return START_STICKY
    }

    private fun updateMonitoringData(profile: FirebaseService.ChildProfile) {
        pushAgeAssessmentIfNeeded(profile)
        handleScreenshotRequest(profile)

        val now = System.currentTimeMillis()
        val uninstallModeActive = profile.uninstallModeEnabled && profile.uninstallWindowEndsAt > now
        if (uninstallModeActive) {
            applyParentUninstallMode(profile)
            return
        }
        if (parentUninstallModeApplied) {
            restoreParentProtectionMode()
        }

        MyAccessibilityService.setBlockedApps(profile.blockedApps)
        MyAccessibilityService.setBlockedWebsites(profile.blockedWebsites)
        MyAccessibilityService.setStorageRestricted(profile.storageRestricted)
        MyAccessibilityService.setProtectionActive(profile.protectionActive)
        MyAccessibilityService.setSettingsTamperProtectionActive(profile.protectionActive)
        MyAccessibilityService.setParentUninstallMode(false)
        WebsiteFilterVpnService.setRules(
            blocked = (profile.blockedDomains + profile.blockedWebsites).distinct(),
            allowed = profile.allowedDomains
        )
        updateWebsiteRestrictionService(profile)
    }

    private fun handleScreenshotRequest(profile: FirebaseService.ChildProfile) {
        val childId = currentChildId ?: return
        val requestAt = profile.screenshotRequestAt
        if (requestAt <= 0L || requestAt <= lastScreenshotRequestHandledAt) return
        lastScreenshotRequestHandledAt = requestAt

        FirebaseService.updateScreenshotRequestStatus(childId, "processing")
        MyAccessibilityService.captureRemoteScreenshot(
            childId = childId,
            requestAt = requestAt,
            requestedBy = profile.screenshotRequestBy
        ) { success, error ->
            if (success) {
                FirebaseService.updateScreenshotRequestStatus(
                    childId = childId,
                    status = "completed",
                    lastCaptureAt = System.currentTimeMillis(),
                    error = null
                )
            } else {
                FirebaseService.updateScreenshotRequestStatus(
                    childId = childId,
                    status = "failed",
                    lastCaptureAt = null,
                    error = error ?: "unknown_error"
                )
            }
        }
    }

    private fun pushAgeAssessmentIfNeeded(profile: FirebaseService.ChildProfile) {
        val childId = currentChildId ?: return
        val now = System.currentTimeMillis()
        if (now - lastAgeAssessmentPushAt < 10 * 60 * 1000) return
        lastAgeAssessmentPushAt = now

        val assessment = ageGroupManager.assessAgeProfile(
            profileAge = profile.age.takeIf { it > 0 },
            birthDate = profile.birthDate.takeIf { it > 0L },
            declaredAgeGroup = profile.ageGroup
        )

        val resolvedAgeGroup = assessment.ageGroup.name
        FirebaseService.updateAgeAssessment(
            childId = childId,
            inferredAge = assessment.inferredAge,
            ageGroup = resolvedAgeGroup,
            confidence = assessment.confidence,
            source = assessment.source,
            evidence = assessment.evidence
        )

        if (assessment.confidence < 0.7) {
            FirebaseService.logChildEvent(
                childId = childId,
                event = FirebaseService.ChildEvent(
                    type = "AGE_PROFILE_LOW_CONFIDENCE",
                    severity = "low",
                    details = mapOf(
                        "ageGroup" to resolvedAgeGroup,
                        "source" to assessment.source,
                        "confidence" to assessment.confidence.toString()
                    )
                )
            )
        }
    }

    private fun applyParentUninstallMode(profile: FirebaseService.ChildProfile) {
        if (parentUninstallModeApplied) return
        parentUninstallModeApplied = true
        MyAccessibilityService.setParentUninstallMode(true)
        MyAccessibilityService.setProtectionActive(false)
        MyAccessibilityService.setSettingsTamperProtectionActive(false)
        MyAccessibilityService.setBlockedApps(emptyList())
        MyAccessibilityService.setBlockedWebsites(emptyList())
        MyAccessibilityService.setStorageRestricted(false)

        // Best effort: unblock uninstall flags for this app while in approved window.
        val packageController = PackageController(this)
        packageController.enableUninstall(packageName)

        stopService(Intent(this, WebsiteFilterVpnService::class.java))

        val childId = currentChildId ?: return
        FirebaseService.logChildEvent(
            childId = childId,
            event = FirebaseService.ChildEvent(
                type = "PARENT_UNINSTALL_MODE_ACTIVE",
                severity = "medium",
                details = mapOf(
                    "approvedBy" to profile.uninstallApprovedBy,
                    "expiresAt" to profile.uninstallWindowEndsAt.toString()
                )
            )
        )
    }

    private fun restoreParentProtectionMode() {
        parentUninstallModeApplied = false
        MyAccessibilityService.setParentUninstallMode(false)
    }

    private fun updateWebsiteRestrictionService(profile: FirebaseService.ChildProfile) {
        val childId = currentChildId ?: return
        if (!profile.protectionActive || !profile.dnsFilterEnabled) {
            stopService(Intent(this, WebsiteFilterVpnService::class.java))
            return
        }

        val started = WebsiteFilterVpnService.startIfPermitted(this)
        if (!started && !dnsBypassLogged) {
            dnsBypassLogged = true
            FirebaseService.logChildEvent(
                childId = childId,
                event = FirebaseService.ChildEvent(
                    type = "BYPASS_SUSPECTED",
                    severity = "low",
                    details = mapOf(
                        "source" to "vpn",
                        "signal" to "vpn_permission_required"
                    )
                )
            )
        }
    }

    private fun startMonitoring() {
        timer?.cancel()
        timer = Timer()
        timer?.scheduleAtFixedRate(object : TimerTask() {
            override fun run() {
                trackScreenTime()
            }
        }, 0, 5000) // Check every 5 seconds
    }

    private fun trackScreenTime() {
        val currentApp = getCurrentForegroundPackage() ?: return
        val currentTime = System.currentTimeMillis()

        if (currentApp != lastAppPackage) {
            // App has changed, save duration for the previous app
            lastAppPackage?.let { pkg ->
                val duration = currentTime - appStartTime
                val totalSoFar = appDurations.getOrDefault(pkg, 0L)
                val newTotal = totalSoFar + duration
                appDurations[pkg] = newTotal
                
                // Upload to Firebase
                currentChildId?.let { id ->
                    FirebaseService.updateAppScreenTime(id, FirebaseService.ScreenTimeData(
                        packageName = pkg,
                        appName = getAppLabel(pkg),
                        totalTimeVisible = newTotal
                    ))
                }
            }
            // Start tracking new app
            lastAppPackage = currentApp
            appStartTime = currentTime
            emitAppSnapshot(currentApp, currentTime)
        } else {
            // Same app still running, update current total in memory
            val duration = currentTime - appStartTime
            val totalSoFar = appDurations.getOrDefault(currentApp, 0L)
            // Periodically upload even if app doesn't change
            if (duration > 30000) { // every 30 seconds
                currentChildId?.let { id ->
                    FirebaseService.updateAppScreenTime(id, FirebaseService.ScreenTimeData(
                        packageName = currentApp,
                        appName = getAppLabel(currentApp),
                        totalTimeVisible = totalSoFar + duration
                    ))
                }
            }
        }

        maybeScanSuspiciousBehavior(currentTime)
    }

    private fun maybeScanSuspiciousBehavior(now: Long) {
        if (now - lastBehaviorScanAt < 60_000L) return
        lastBehaviorScanAt = now
        val childId = currentChildId ?: return

        val findings = SuspiciousBehaviorAnalyzer.scan(this, lastBehaviorCursorAt)
        lastBehaviorCursorAt = now
        findings.take(10).forEach { event ->
            FirebaseService.logChildEvent(childId, event)
        }
    }

    private fun emitAppSnapshot(packageName: String, timestamp: Long) {
        val childId = currentChildId ?: return
        FirebaseService.logActivitySnapshot(
            childId = childId,
            snapshot = FirebaseService.ActivitySnapshot(
                packageName = packageName,
                appName = getAppLabel(packageName),
                timestamp = timestamp
            )
        )
        FirebaseService.logChildEvent(
            childId = childId,
            event = FirebaseService.ChildEvent(
                type = "APP_SNAPSHOT",
                severity = "low",
                details = mapOf(
                    "packageName" to packageName,
                    "appName" to getAppLabel(packageName)
                ),
                timestamp = timestamp
            )
        )
    }

    private fun getCurrentForegroundPackage(): String? {
        val am = getSystemService(Context.ACTIVITY_SERVICE) as android.app.ActivityManager
        return try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                am.appTasks.firstOrNull()?.taskInfo?.topActivity?.packageName
            } else {
                @Suppress("DEPRECATION")
                am.getRunningTasks(1).firstOrNull()?.topActivity?.packageName
            }
        } catch (e: Exception) { null }
    }

    private fun getAppLabel(packageName: String): String {
        return try {
            val pm = packageManager
            val info = pm.getApplicationInfo(packageName, 0)
            pm.getApplicationLabel(info).toString()
        } catch (e: Exception) { packageName }
    }

    override fun onTaskRemoved(rootIntent: Intent?) {
        Log.d(TAG, "Task removed - scheduling aggressive restart")
        val restartServiceIntent = Intent(applicationContext, this.javaClass).apply {
            setPackage(packageName)
            putExtra("CHILD_ID", currentChildId)
        }
        val restartServicePendingIntent = PendingIntent.getService(
            applicationContext, 1, restartServiceIntent, 
            PendingIntent.FLAG_ONE_SHOT or PendingIntent.FLAG_IMMUTABLE
        )
        val alarmManager = getSystemService(Context.ALARM_SERVICE) as AlarmManager
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            alarmManager.setAndAllowWhileIdle(AlarmManager.ELAPSED_REALTIME_WAKEUP, SystemClock.elapsedRealtime() + 1000, restartServicePendingIntent)
        }
        super.onTaskRemoved(rootIntent)
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(CHANNEL_ID, "IPE Guard Service", NotificationManager.IMPORTANCE_HIGH).apply {
                description = "Keeps the parental protection active"
                setShowBadge(true)
                enableLights(true)
                lockscreenVisibility = Notification.VISIBILITY_PUBLIC
            }
            val notificationManager = getSystemService(NotificationManager::class.java)
            notificationManager.createNotificationChannel(channel)
        }
    }

    private fun createNotification(): Notification {
        val openAppIntent = Intent(this, MainActivity::class.java)
        val pendingIntent = PendingIntent.getActivity(this, 0, openAppIntent, PendingIntent.FLAG_IMMUTABLE)

        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("IPE Protection is ON")
            .setContentText("Your device is currently being protected.")
            .setSmallIcon(R.mipmap.ic_launcher)
            .setContentIntent(pendingIntent)
            .setOngoing(true) 
            .setCategory(NotificationCompat.CATEGORY_SERVICE)
            .setPriority(NotificationCompat.PRIORITY_MAX)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .setForegroundServiceBehavior(NotificationCompat.FOREGROUND_SERVICE_IMMEDIATE)
            .build()
    }

    override fun onBind(intent: Intent): IBinder? = null

    companion object {
        private const val TAG = "MonitoringService"
    }
}
