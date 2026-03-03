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
import android.os.PowerManager
import android.os.SystemClock
import android.util.Log
import androidx.core.app.NotificationCompat
import com.example.myapp.R
import com.example.myapp.ui.activities.BlockedAppActivity
import com.example.myapp.MainActivity
import com.example.myapp.models.AgeGroupManager
import com.example.myapp.utils.ProtectedStorageUtil
import java.util.Timer
import java.util.TimerTask

class MonitoringService : Service() {
    private val CHANNEL_ID = "MonitoringServiceChannel"
    private val NOTIFICATION_ID = 123
    private var timer: Timer? = null
    private var currentChildId: String? = null
    private val ageGroupManager = AgeGroupManager()
    
    // Screen Time Tracking variables
    private var lastAppPackage: String? = null
    private var appStartTime: Long = 0
    private val appDurations = mutableMapOf<String, Long>()

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
        MyAccessibilityService.setBlockedApps(profile.blockedApps)
        MyAccessibilityService.setBlockedWebsites(profile.blockedWebsites)
        MyAccessibilityService.setStorageRestricted(profile.storageRestricted)
        MyAccessibilityService.setProtectionActive(profile.protectionActive)
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
