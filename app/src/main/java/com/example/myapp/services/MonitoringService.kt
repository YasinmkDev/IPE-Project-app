package com.example.myapp.services

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.IBinder
import android.util.Log
import androidx.core.app.NotificationCompat
import com.example.myapp.R
import com.example.myapp.ui.activities.BlockedAppActivity
import java.util.Timer
import java.util.TimerTask

class MonitoringService : Service() {
    private val CHANNEL_ID = "MonitoringServiceChannel"
    private val NOTIFICATION_ID = 123
    private var timer: Timer? = null
    private var currentChildId: String? = null
    private var blockedAppsList: List<String> = emptyList()
    private var blockedWebsitesList: List<String> = emptyList()

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
        startForeground(NOTIFICATION_ID, createNotification())
        Log.d(TAG, "Service onCreate")
    }

    override fun onStartCommand(intent: Intent, flags: Int, startId: Int): Int {
        currentChildId = intent.getStringExtra("CHILD_ID")
        Log.d(TAG, "Service onStartCommand with childId: $currentChildId")

        currentChildId?.let { childId ->
            // Fetch initial child profile
            FirebaseService.fetchChildProfile(
                childId,
                onSuccess = { profile ->
                    blockedAppsList = profile.blockedApps
                    blockedWebsitesList = profile.blockedWebsites
                    Log.d(TAG, "Fetched blocked apps: $blockedAppsList")
                },
                onFailure = { exception ->
                    Log.e(TAG, "Error fetching child profile: ${exception.message}")
                }
            )

            // Listen for real-time updates
            FirebaseService.listenToChildProfileUpdates(
                childId,
                onUpdate = { profile ->
                    blockedAppsList = profile.blockedApps
                    blockedWebsitesList = profile.blockedWebsites
                    Log.d(TAG, "Updated blocked apps: $blockedAppsList")
                },
                onError = { exception ->
                    Log.e(TAG, "Error listening to profile updates: ${exception.message}")
                }
            )

            // Start monitoring
            startMonitoring()
        }

        return START_STICKY
    }

    private fun startMonitoring() {
        timer?.cancel()
        timer = Timer()
        timer?.scheduleAtFixedRate(object : TimerTask() {
            override fun run() {
                checkCurrentApp()
            }
        }, 0, MONITORING_INTERVAL)
    }

    private fun checkCurrentApp() {
        val currentPackageName = getCurrentForegroundPackage()
        Log.d(TAG, "Current foreground package: $currentPackageName")

        if (currentPackageName != null && currentPackageName != packageName && currentPackageName in blockedAppsList) {
            Log.d(TAG, "Blocked app detected: $currentPackageName")
            showBlockedOverlay(currentPackageName)
        }
    }

    private fun getCurrentForegroundPackage(): String? {
        return try {
            val activityManager = getSystemService(Context.ACTIVITY_SERVICE) as android.app.ActivityManager
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                val appTasks = activityManager.appTasks
                appTasks.firstOrNull()?.taskInfo?.topActivity?.packageName
            } else {
                @Suppress("DEPRECATION")
                activityManager.runningAppProcesses?.find { it.importance == android.app.ActivityManager.RunningAppProcessInfo.IMPORTANCE_FOREGROUND }?.processName
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error getting foreground app: ${e.message}")
            null
        }
    }

    private fun showBlockedOverlay(packageName: String) {
        val intent = Intent(this, BlockedAppActivity::class.java)
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        intent.putExtra("BLOCKED_PACKAGE", packageName)
        startActivity(intent)
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "Monitoring Service Channel",
                NotificationManager.IMPORTANCE_LOW
            )
            channel.description = "Channel for Parental Control Monitoring Service"
            val notificationManager = getSystemService(NotificationManager::class.java)
            notificationManager.createNotificationChannel(channel)
        }
    }

    private fun createNotification(): Notification {
        val notificationIntent = Intent(this, BlockedAppActivity::class.java)
        val pendingIntent = PendingIntent.getActivity(
            this,
            0,
            notificationIntent,
            PendingIntent.FLAG_IMMUTABLE
        )

        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("IPE Monitoring Service")
            .setContentText("Monitoring child device for parental control policies")
            .setSmallIcon(R.mipmap.ic_launcher)
            .setContentIntent(pendingIntent)
            .build()
    }

    override fun onDestroy() {
        super.onDestroy()
        timer?.cancel()
        Log.d(TAG, "Service onDestroy")
    }

    override fun onBind(intent: Intent): IBinder? {
        return null
    }

    companion object {
        private const val TAG = "MonitoringService"
        private const val MONITORING_INTERVAL = 1000L // 1 second
    }
}
