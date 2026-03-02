package com.example.myapp

import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.PowerManager
import android.provider.Settings
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.compose.rememberNavController
import com.example.myapp.services.FirebaseService
import com.example.myapp.services.MonitoringService
import com.example.myapp.services.FirebaseService.AppInfo
import com.example.myapp.ui.navigation.IPENavGraph
import com.example.myapp.ui.navigation.Screen
import com.example.myapp.ui.theme.GreenPrimary
import com.example.myapp.ui.theme.GreenPrimaryDark
import com.example.myapp.ui.theme.IPETheme
import kotlinx.coroutines.delay

class MainActivity : ComponentActivity() {
    private val TAG = "MainActivity"

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        setContent {
            IPETheme {
                var isInitialLoading by remember { mutableStateOf(true) }
                val storedChildId = remember { mutableStateOf<String?>(null) }
                val navController = rememberNavController()

                LaunchedEffect(Unit) {
                    val childId = getStoredChildId()
                    storedChildId.value = childId
                    
                    if (childId != null) {
                        Log.d(TAG, "Device already linked with childId: $childId")
                        startMonitoringService(childId)
                    }
                    
                    delay(1500) 
                    isInitialLoading = false
                }

                Surface(modifier = Modifier.fillMaxSize()) {
                    if (isInitialLoading) {
                        SplashScreen()
                    } else {
                        val startDest = if (storedChildId.value != null) {
                            Screen.AlreadyLinked.createRoute(storedChildId.value!!)
                        } else {
                            Screen.Welcome.route
                        }

                        IPENavGraph(
                            navController = navController,
                            startDestination = startDest,
                            onSetupComplete = { childId ->
                                saveChildId(childId)
                                uploadInstalledApps(childId)
                                requestBatteryExemption() // NEW: Ask to ignore battery limits
                                startMonitoringService(childId)
                                navController.navigate(Screen.AlreadyLinked.createRoute(childId)) {
                                    popUpTo(0)
                                }
                            },
                            onExit = {
                                finish()
                            }
                        )
                    }
                }
            }
        }
    }

    private fun requestBatteryExemption() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            val pm = getSystemService(POWER_SERVICE) as PowerManager
            if (!pm.isIgnoringBatteryOptimizations(packageName)) {
                try {
                    val intent = Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS).apply {
                        data = Uri.parse("package:$packageName")
                    }
                    startActivity(intent)
                } catch (e: Exception) {
                    Log.e(TAG, "Could not request battery exemption: ${e.message}")
                }
            }
        }
    }

    private fun getStoredChildId(): String? {
        val protectedContext = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            createDeviceProtectedStorageContext()
        } else this
        val prefs = protectedContext.getSharedPreferences("APP_PREFS", Context.MODE_PRIVATE)
        return prefs.getString("CHILD_ID", null)
    }

    private fun saveChildId(childId: String) {
        val protectedContext = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            createDeviceProtectedStorageContext()
        } else this
        protectedContext.getSharedPreferences("APP_PREFS", Context.MODE_PRIVATE)
            .edit().putString("CHILD_ID", childId).apply()
        getSharedPreferences("APP_PREFS", Context.MODE_PRIVATE)
            .edit().putString("CHILD_ID", childId).apply()
    }

    @Composable
    fun SplashScreen() {
        Column(modifier = Modifier.fillMaxSize().background(Color.White), verticalArrangement = Arrangement.Center, horizontalAlignment = Alignment.CenterHorizontally) {
            Box(modifier = Modifier.size(100.dp).background(GreenPrimary, shape = MaterialTheme.shapes.medium), contentAlignment = Alignment.Center) {
                Text(text = "IPE", color = Color.White, fontSize = 32.sp, fontWeight = FontWeight.Bold)
            }
            Spacer(modifier = Modifier.height(24.dp))
            Text(text = "Initializing Protection...", fontSize = 18.sp, fontWeight = FontWeight.SemiBold, color = GreenPrimaryDark)
            Spacer(modifier = Modifier.height(48.dp))
            CircularProgressIndicator(color = GreenPrimary, strokeWidth = 3.dp, modifier = Modifier.size(40.dp))
        }
    }

    private fun uploadInstalledApps(childId: String) {
        val pm = packageManager
        val appsList = mutableListOf<AppInfo>()
        val packages = pm.getInstalledApplications(0)
        for (appInfo in packages) {
            if (appInfo.packageName != packageName) { 
                val appName = pm.getApplicationLabel(appInfo).toString()
                val packageInfo = try { pm.getPackageInfo(appInfo.packageName, 0) } catch (e: Exception) { null }
                val versionName = packageInfo?.versionName ?: ""
                val versionCode = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                    packageInfo?.longVersionCode ?: 0L
                } else {
                    @Suppress("DEPRECATION") packageInfo?.versionCode?.toLong() ?: 0L
                }
                appsList.add(AppInfo(appInfo.packageName, appName, versionName, versionCode, (appInfo.flags and android.content.pm.ApplicationInfo.FLAG_SYSTEM != 0)))
            }
        }
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
