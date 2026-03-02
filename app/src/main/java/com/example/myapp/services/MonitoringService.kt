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
import com.example.myapp.security.SecurityManager
import com.example.myapp.models.AgeGroupManager
import com.example.myapp.utils.PackageController
import java.util.Timer
import java.util.TimerTask

class MonitoringService : Service() {
    private val CHANNEL_ID = "MonitoringServiceChannel"
    private val NOTIFICATION_ID = 123
    private var timer: Timer? = null
    private var currentChildId: String? = null
    private var blockedAppsList: List<String> = emptyList()
    private var blockedWebsitesList: List<String> = emptyList()
    private var childAge: Int = 0
    private var screenTimeMinutes: Int = 0
    private var screenTimeElapsed: Long = 0
    private var lastCheckTime: Long = 0
    private val ageGroupManager = AgeGroupManager()
    private var packageController: PackageController? = null
    private var securityCheckInterval = 0L

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
        startForeground(NOTIFICATION_ID, createNotification())
        packageController = PackageController(this)
        
        Log.d(TAG, "Service onCreate")
    }

    override fun onStartCommand(intent: Intent, flags: Int, startId: Int): Int {
        currentChildId = intent.getStringExtra("CHILD_ID")
        childAge = intent.getIntExtra("CHILD_AGE", 10)
        Log.d(TAG, "Service onStartCommand with childId: $currentChildId")

        currentChildId?.let { childId ->
            FirebaseService.fetchChildProfile(
                childId,
                onSuccess = { profile ->
                    updateMonitoringData(profile)
                },
                onFailure = { exception ->
                    Log.e(TAG, "Error fetching profile: ${exception.message}")
                }
            )

            FirebaseService.listenToChildProfileUpdates(
                childId,
                onUpdate = { profile ->
                    updateMonitoringData(profile)
                },
                onError = { exception ->
                    Log.e(TAG, "Error listening to updates: ${exception.message}")
                }
            )

            startMonitoring()
        }

        return START_STICKY
    }

    private fun updateMonitoringData(profile: FirebaseService.ChildProfile) {
        blockedAppsList = profile.blockedApps
        blockedWebsitesList = profile.blockedWebsites
        childAge = profile.age
        
        // Update Accessibility Service
        MyAccessibilityService.setBlockedApps(blockedAppsList)
        MyAccessibilityService.setBlockedWebsites(blockedWebsitesList)
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
        }, 0, MONITORING_INTERVAL)
    }

    private fun trackScreenTime() {
        // screen time logic...
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
        val pendingIntent = PendingIntent.getActivity(this, 0, notificationIntent, PendingIntent.FLAG_IMMUTABLE)

        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("IPE Monitoring Service")
            .setContentText("Parental control protection is active")
            .setSmallIcon(R.mipmap.ic_launcher)
            .setContentIntent(pendingIntent)
            .build()
    }

    override fun onBind(intent: Intent): IBinder? = null

    companion object {
        private const val TAG = "MonitoringService"
        private const val MONITORING_INTERVAL = 1000L
    }
}
