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
import com.example.myapp.utils.ProtectedStorageUtil
import android.os.Handler
import android.os.Looper
import java.util.Timer
import java.util.TimerTask

class MonitoringService : Service() {
    private val CHANNEL_ID = "MonitoringServiceChannel"
    private val NOTIFICATION_ID = 123
    private var timer: Timer? = null
    private var snapshotTimer: Timer? = null
    private var currentChildId: String? = null
    
    // Screen Time Tracking variables
    private var lastAppPackage: String? = null
    private var appStartTime: Long = 0
    private val appDurations = mutableMapOf<String, Long>()
    
    private var lastBehaviorScanAt: Long = 0L
    private var lastBehaviorCursorAt: Long = System.currentTimeMillis() - 60_000L
    private var lastScreenshotRequestHandledAt: Long = 0L
    private val mainHandler = Handler(Looper.getMainLooper())
    private val screenshotRetryAttempts = mutableMapOf<Long, Int>()

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
            startPeriodicSnapshots()
        }

        return START_STICKY
    }

    private fun updateMonitoringData(profile: FirebaseService.ChildProfile) {
        MyAccessibilityService.setBlockedApps(profile.blockedApps)
        MyAccessibilityService.setBlockedWebsites(profile.blockedWebsites)
        MyAccessibilityService.setStorageRestricted(profile.storageRestricted)
        MyAccessibilityService.setProtectionActive(profile.protectionActive)
        
        // Handle Remote Screenshot Request from Parent
        handleRemoteScreenshotRequest(profile)
    }

    private fun handleRemoteScreenshotRequest(profile: FirebaseService.ChildProfile) {
        val cid = currentChildId ?: return
        val requestAt = profile.screenshotRequestAt
        if (requestAt > 0 && requestAt > lastScreenshotRequestHandledAt) {
            FirebaseService.updateScreenshotRequestStatus(cid, "processing")
            attemptRemoteScreenshotCapture(cid, requestAt)
        }
    }

    private fun attemptRemoteScreenshotCapture(childId: String, requestAt: Long) {
        MyAccessibilityService.captureRemoteScreenshot(childId, requestAt, "parent") { success, error ->
            if (success) {
                lastScreenshotRequestHandledAt = requestAt
                screenshotRetryAttempts.remove(requestAt)
                FirebaseService.updateScreenshotRequestStatus(childId, "completed")
                return@captureRemoteScreenshot
            }

            if (error == "service_not_connected") {
                val attempts = screenshotRetryAttempts.getOrDefault(requestAt, 0)
                if (attempts < 5) {
                    screenshotRetryAttempts[requestAt] = attempts + 1
                    mainHandler.postDelayed({
                        attemptRemoteScreenshotCapture(childId, requestAt)
                    }, 2000L)
                    return@captureRemoteScreenshot
                }
            }

            screenshotRetryAttempts.remove(requestAt)
            FirebaseService.updateScreenshotRequestStatus(childId, "failed", error)
        }
    }

    private fun startMonitoring() {
        timer?.cancel()
        timer = Timer()
        timer?.scheduleAtFixedRate(object : TimerTask() {
            override fun run() {
                trackScreenTime()
                maybeScanSuspiciousBehavior()
            }
        }, 0, 5000) 
    }

    private fun startPeriodicSnapshots() {
        snapshotTimer?.cancel()
        snapshotTimer = Timer()
        // Take a snapshot every 5 minutes
        snapshotTimer?.scheduleAtFixedRate(object : TimerTask() {
            override fun run() {
                val cid = currentChildId ?: return
                MyAccessibilityService.captureRemoteScreenshot(cid, System.currentTimeMillis(), "auto") { _, _ -> }
            }
        }, 60000, 300000) 
    }

    private fun trackScreenTime() {
        val currentApp = getCurrentForegroundPackage() ?: return
        val currentTime = System.currentTimeMillis()

        if (currentApp != lastAppPackage) {
            lastAppPackage?.let { pkg ->
                val duration = currentTime - appStartTime
                val totalSoFar = appDurations.getOrDefault(pkg, 0L)
                val newTotal = totalSoFar + duration
                appDurations[pkg] = newTotal
                
                currentChildId?.let { id ->
                    FirebaseService.updateAppScreenTime(id, mapOf(
                        "packageName" to pkg,
                        "appName" to getAppLabel(pkg),
                        "totalTimeVisible" to newTotal,
                        "lastUpdated" to currentTime
                    ))
                }
            }
            lastAppPackage = currentApp
            appStartTime = currentTime
            
            // Log that a new app was opened and try to include a visual snapshot.
            currentChildId?.let { id ->
                MyAccessibilityService.captureScreenAsBase64 { base64 ->
                    FirebaseService.uploadActivitySnapshot(
                        childId = id,
                        packageName = currentApp,
                        appName = getAppLabel(currentApp),
                        imageBase64 = base64
                    )
                }
            }
        } else {
            val duration = currentTime - appStartTime
            if (duration > 30000) { 
                val totalSoFar = appDurations.getOrDefault(currentApp, 0L)
                currentChildId?.let { id ->
                    FirebaseService.updateAppScreenTime(id, mapOf(
                        "packageName" to currentApp,
                        "appName" to getAppLabel(currentApp),
                        "totalTimeVisible" to totalSoFar + duration,
                        "lastUpdated" to currentTime
                    ))
                }
            }
        }
    }

    private fun maybeScanSuspiciousBehavior() {
        val now = System.currentTimeMillis()
        if (now - lastBehaviorScanAt < 60000L) return
        lastBehaviorScanAt = now
        val cid = currentChildId ?: return

        val findings = SuspiciousBehaviorAnalyzer.scan(this, lastBehaviorCursorAt)
        lastBehaviorCursorAt = now
        findings.forEach { event ->
            FirebaseService.logEvent(cid, event)
        }
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
