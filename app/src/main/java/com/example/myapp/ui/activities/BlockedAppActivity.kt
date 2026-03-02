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
    private lateinit var blockedPackageName: String

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        // Ensure the activity shows over the lock screen and keeps screen on
        requestWindowFeature(Window.FEATURE_NO_TITLE)
        window.addFlags(WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED or
                WindowManager.LayoutParams.FLAG_DISMISS_KEYGUARD or
                WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON or
                WindowManager.LayoutParams.FLAG_TURN_SCREEN_ON)

        setContentView(R.layout.activity_blocked_app)

        blockedPackageName = intent.getStringExtra("BLOCKED_PACKAGE") ?: ""
        Log.d(TAG, "Displaying block overlay for: $blockedPackageName")

        val appName = getAppName(blockedPackageName)
        findViewById<TextView>(R.id.textBlockedAppName).text = appName

        findViewById<Button>(R.id.buttonClose).setOnClickListener {
            // Instead of just finishing, we can go to the home screen
            val startMain = Intent(Intent.ACTION_MAIN)
            startMain.addCategory(Intent.CATEGORY_HOME)
            startMain.flags = Intent.FLAG_ACTIVITY_NEW_TASK
            startActivity(startMain)
            finish()
        }
    }

    private fun getAppName(packageName: String): String {
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
        // Optional: Also redirect to home screen here
    }

    companion object {
        private const val TAG = "BlockedAppActivity"
    }
}
