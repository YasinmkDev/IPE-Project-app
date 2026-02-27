package com.example.myapp

import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.os.Build
import android.os.Bundle
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.ui.Modifier
import androidx.navigation.compose.rememberNavController
import com.example.myapp.services.FirebaseService
import com.example.myapp.services.MonitoringService
import com.example.myapp.services.FirebaseService.AppInfo
import com.example.myapp.ui.navigation.IPENavGraph
import com.example.myapp.ui.theme.IPETheme

class MainActivity : ComponentActivity() {
    private lateinit var sharedPreferences: SharedPreferences
    private val TAG = "MainActivity"

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        sharedPreferences = getSharedPreferences("APP_PREFS", Context.MODE_PRIVATE)

        setContent {
            IPETheme {
                val navController = rememberNavController()

                Surface(modifier = Modifier.fillMaxSize()) {
                    IPENavGraph(
                        navController = navController,
                        onSetupComplete = { childId ->
                            // Store childId in shared preferences
                            sharedPreferences.edit().putString("CHILD_ID", childId).apply()
                            Log.d(TAG, "Stored childId: $childId")

                            // Upload installed apps to Firestore
                            uploadInstalledApps(childId)

                            // Start monitoring service
                            startMonitoringService(childId)

                            // Close UI
                            finish()
                        }
                    )
                }
            }
        }
    }

    private fun uploadInstalledApps(childId: String) {
        val pm = packageManager
        val appsList = mutableListOf<AppInfo>()

        val packages = pm.getInstalledApplications(0)
        for (appInfo in packages) {
            if (appInfo.packageName != packageName) { // Exclude our own app
                val appName = pm.getApplicationLabel(appInfo).toString()
                val packageInfo = pm.getPackageInfo(appInfo.packageName, 0)
                val versionName = packageInfo.versionName ?: ""
                val versionCode = packageInfo.longVersionCode
                val isSystemApp = appInfo.flags and android.content.pm.ApplicationInfo.FLAG_SYSTEM != 0

                appsList.add(
                    AppInfo(
                        packageName = appInfo.packageName,
                        appName = appName,
                        versionName = versionName,
                        versionCode = versionCode,
                        isSystemApp = isSystemApp
                    )
                )
            }
        }

        Log.d(TAG, "Found ${appsList.size} installed apps")
        FirebaseService.uploadInstalledApps(childId, appsList)
    }

    private fun startMonitoringService(childId: String) {
        val serviceIntent = Intent(this, MonitoringService::class.java)
        serviceIntent.putExtra("CHILD_ID", childId)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            startForegroundService(serviceIntent)
        } else {
            startService(serviceIntent)
        }
    }
}
