package com.example.myapp.ui.activities

import android.app.Activity
import android.content.Intent
import android.os.Bundle
import android.util.Log
import android.view.Window
import android.view.WindowManager
import android.widget.Button
import android.widget.TextView
import com.example.myapp.R

class BlockedAppActivity : Activity() {
    private var blockedPackageName: String? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        requestWindowFeature(Window.FEATURE_NO_TITLE)
        window.addFlags(WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED or
                WindowManager.LayoutParams.FLAG_DISMISS_KEYGUARD or
                WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON or
                WindowManager.LayoutParams.FLAG_TURN_SCREEN_ON)

        setContentView(R.layout.activity_blocked_app)

        blockedPackageName = intent.getStringExtra("BLOCKED_PACKAGE")
        val isStorageRestricted = intent.getBooleanExtra("STORAGE_RESTRICTED", false)
        val isTamperAttempt = intent.getBooleanExtra("TAMPER_ATTEMPT", false)
        val isScreenTimeExceeded = intent.getBooleanExtra("SCREEN_TIME_LIMIT_EXCEEDED", false)

        val titleText = findViewById<TextView>(R.id.textBlockedTitle)
        val appNameText = findViewById<TextView>(R.id.textBlockedAppName)
        val descText = findViewById<TextView>(R.id.textBlockedDescription)

        when {
            isTamperAttempt -> {
                titleText.text = "Security Alert"
                appNameText.text = "Tamper Attempt Detected"
                descText.text = "You are not allowed to modify security settings or uninstall this app."
            }
            isStorageRestricted -> {
                titleText.text = "Access Restricted"
                appNameText.text = "File Manager Blocked"
                descText.text = "Access to device storage and files has been restricted by your parent."
            }
            isScreenTimeExceeded -> {
                val minutes = intent.getLongExtra("MINUTES_USED", 0)
                titleText.text = "Time's Up!"
                appNameText.text = "Screen Time Limit Reached"
                descText.text = "You have used your daily limit of $minutes minutes."
            }
            else -> {
                val appName = getAppName(blockedPackageName ?: "")
                titleText.text = "App Blocked"
                appNameText.text = appName
                descText.text = "This app is not allowed for your age group or has been blocked by your parent."
            }
        }

        findViewById<Button>(R.id.buttonClose).setOnClickListener {
            val startMain = Intent(Intent.ACTION_MAIN)
            startMain.addCategory(Intent.CATEGORY_HOME)
            startMain.flags = Intent.FLAG_ACTIVITY_NEW_TASK
            startActivity(startMain)
            finish()
        }
    }

    private fun getAppName(packageName: String): String {
        if (packageName.isEmpty()) return "Unknown App"
        return try {
            val pm = packageManager
            val applicationInfo = pm.getApplicationInfo(packageName, 0)
            pm.getApplicationLabel(applicationInfo).toString()
        } catch (e: Exception) {
            packageName
        }
    }

    override fun onBackPressed() {
        // Prevent closing via back button
    }

    companion object {
        private const val TAG = "BlockedAppActivity"
    }
}
