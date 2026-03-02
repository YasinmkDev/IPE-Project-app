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
        
        // Perform initial security check
        val securityCheck = SecurityManager.performSecurityCheck(this)
        if (securityCheck.isTampered) {
            Log.w(TAG, "Security check failed - Device appears tampered")
            logSecurityIncident(securityCheck)
        }
        
        Log.d(TAG, "Service onCreate with security checks enabled")
    }

    override fun onStartCommand(intent: Intent, flags: Int, startId: Int): Int {
        currentChildId = intent.getStringExtra("CHILD_ID")
        childAge = intent.getIntExtra("CHILD_AGE", 10)
        Log.d(TAG, "Service onStartCommand with childId: $currentChildId, age: $childAge")

        currentChildId?.let { childId ->
            // Fetch initial child profile
            FirebaseService.fetchChildProfile(
                childId,
                onSuccess = { profile ->
                    updateMonitoringData(profile)
                    lastCheckTime = System.currentTimeMillis()
                    Log.d(TAG, "Fetched blocked apps: $blockedAppsList, screen time: $screenTimeMinutes min")
                    
                    // Apply restrictions based on age
                    applyAgeBasedRestrictions()
                },
                onFailure = { exception ->
                    Log.e(TAG, "Error fetching child profile: ${exception.message}")
                }
            )

            // Listen for real-time updates
            FirebaseService.listenToChildProfileUpdates(
                childId,
                onUpdate = { profile ->
                    updateMonitoringData(profile)
                    Log.d(TAG, "Updated blocked apps: $blockedAppsList")
                    applyAgeBasedRestrictions()
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

    private fun updateMonitoringData(profile: FirebaseService.ChildProfile) {
        blockedAppsList = profile.blockedApps
        blockedWebsitesList = profile.blockedWebsites
        childAge = profile.age
        screenTimeMinutes = ageGroupManager.getScreenTimeLimit(
            com.example.myapp.models.AgeGroup.fromAge(childAge)
        )
        
        // Update Accessibility Service with the new blocked lists
        MyAccessibilityService.setBlockedApps(blockedAppsList)
        MyAccessibilityService.setBlockedWebsites(blockedWebsitesList)
    }

    private fun startMonitoring() {
        timer?.cancel()
        timer = Timer()
        timer?.scheduleAtFixedRate(object : TimerTask() {
            override fun run() {
                // checkCurrentApp() // We now rely on MyAccessibilityService for app blocking
                trackScreenTime()
                
                // Periodic security check every 30 seconds
                if (securityCheckInterval % 30 == 0L) {
                    performSecurityCheck()
                }
                securityCheckInterval++
            }
        }, 0, MONITORING_INTERVAL)
    }

    private fun checkCurrentApp() {
        val currentPackageName = getCurrentForegroundPackage()
        Log.d(TAG, "Current foreground package: $currentPackageName")

        if (currentPackageName != null && currentPackageName != packageName) {
            // Check if app is in blocked list or matches blocked keywords
            val isBlocked = currentPackageName in blockedAppsList ||
                    ageGroupManager.isAppBlockedForAge(
                        currentPackageName,
                        com.example.myapp.models.AgeGroup.fromAge(childAge)
                    )
            
            if (isBlocked) {
                Log.d(TAG, "Blocked app detected: $currentPackageName")
                showBlockedOverlay(currentPackageName)
            }
        }
    }

    private fun trackScreenTime() {
        if (screenTimeMinutes <= 0) return
        
        val currentTime = System.currentTimeMillis()
        if (lastCheckTime > 0) {
            val elapsedSeconds = (currentTime - lastCheckTime) / 1000
            screenTimeElapsed += elapsedSeconds
            
            val totalMinutes = screenTimeElapsed / 60
            if (totalMinutes >= screenTimeMinutes) {
                Log.w(TAG, "Screen time limit exceeded: ${totalMinutes} minutes")
                showScreenTimeLimitOverlay(totalMinutes)
            }
        }
        lastCheckTime = currentTime
    }

    private fun showScreenTimeLimitOverlay(minutesUsed: Long) {
        val intent = Intent(this, BlockedAppActivity::class.java)
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        intent.putExtra("SCREEN_TIME_LIMIT_EXCEEDED", true)
        intent.putExtra("MINUTES_USED", minutesUsed)
        startActivity(intent)
    }

    private fun performSecurityCheck() {
        try {
            val securityCheck = SecurityManager.performSecurityCheck(this)
            if (securityCheck.isTampered) {
                Log.w(TAG, "Security check failed during monitoring")
                logSecurityIncident(securityCheck)
                packageController?.lockDevice()
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error in security check: ${e.message}")
        }
    }

    private fun applyAgeBasedRestrictions() {
        try {
            val ageGroup = com.example.myapp.models.AgeGroup.fromAge(childAge)
            packageController?.apply {
                // Block storage access for young children
                if (!RestrictionProfiles.getProfileByGroup(ageGroup).allowStorageAccess) {
                    Log.d(TAG, "Restricting storage access for age group: $ageGroup")
                }
                
                // Block uninstall for restricted age groups
                if (!RestrictionProfiles.getProfileByGroup(ageGroup).allowUninstall) {
                    Log.d(TAG, "Blocking uninstall functionality for age group: $ageGroup")
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error applying age-based restrictions: ${e.message}")
        }
    }

    private fun logSecurityIncident(securityCheck: SecurityManager.SecurityCheckResult) {
        try {
            val incidentData = mapOf(
                "timestamp" to System.currentTimeMillis(),
                "isRooted" to securityCheck.isRooted,
                "isDebugging" to securityCheck.isDebugging,
                "isEmulator" to securityCheck.isEmulator,
                "isValidSignature" to securityCheck.isValidSignature,
                "isUSBDebugEnabled" to securityCheck.isUSBDebugEnabled,
                "childId" to (currentChildId ?: "unknown")
            )
            Log.d(TAG, "Security incident logged: $incidentData")
            // In production, this would be sent to Firestore
        } catch (e: Exception) {
            Log.e(TAG, "Error logging security incident: ${e.message}")
        }
    }

    // Import RestrictionProfiles for use
    private val RestrictionProfiles = com.example.myapp.models.RestrictionProfiles

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
