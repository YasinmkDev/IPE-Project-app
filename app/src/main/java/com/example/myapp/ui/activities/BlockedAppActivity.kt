package com.example.myapp.ui.activities

import android.app.Activity
import android.content.Intent
import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.Window
import android.view.WindowManager
import android.widget.Button
import android.widget.TextView
import com.example.myapp.R

class BlockedAppActivity : Activity() {
    private lateinit var blockedPackageName: String

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // Remove title bar and make activity fullscreen
        requestWindowFeature(Window.FEATURE_NO_TITLE)
        window.setFlags(
            WindowManager.LayoutParams.FLAG_FULLSCREEN,
            WindowManager.LayoutParams.FLAG_FULLSCREEN
        )
        // Make activity overlay all other apps
        window.addFlags(
            WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL or
                    WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                    WindowManager.LayoutParams.FLAG_WATCH_OUTSIDE_TOUCH or
                    WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS
        )

        // Create a simple layout programmatically
        val layout = LayoutInflater.from(this).inflate(R.layout.activity_blocked_app, null)
        setContentView(layout)

        blockedPackageName = intent.getStringExtra("BLOCKED_PACKAGE") ?: ""
        Log.d(TAG, "Blocked app: $blockedPackageName")

        // Display blocked app info
        val appName = getAppName(blockedPackageName)
        findViewById<TextView>(R.id.textBlockedAppName).text = appName

        // Close button
        findViewById<Button>(R.id.buttonClose).setOnClickListener {
            finish()
        }
    }

    private fun getAppName(packageName: String): String {
        return try {
            val packageManager = packageManager
            val applicationInfo = packageManager.getApplicationInfo(packageName, 0)
            packageManager.getApplicationLabel(applicationInfo).toString()
        } catch (e: Exception) {
            packageName
        }
    }

    override fun onBackPressed() {
        // Do nothing to prevent closing via back button
    }

    companion object {
        private const val TAG = "BlockedAppActivity"
    }
}
