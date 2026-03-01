package com.example.myapp.security

import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.provider.Settings
import android.util.Log
import com.scottyab.rootbeer.RootBeer
import java.io.BufferedReader
import java.io.File
import java.io.InputStreamReader

object SecurityManager {
    private const val TAG = "SecurityManager"

    /**
     * Check if device is rooted
     */
    fun isDeviceRooted(context: Context): Boolean {
        return try {
            RootBeer(context).isRooted
        } catch (e: Exception) {
            Log.e(TAG, "Error checking root: ${e.message}")
            checkForRootDirectories()
        }
    }

    /**
     * Fallback method to check for root directories
     */
    private fun checkForRootDirectories(): Boolean {
        val paths = arrayOf(
            "/system/app/Superuser.apk",
            "/sbin/su",
            "/system/bin/su",
            "/system/xbin/su",
            "/data/local/xbin/su",
            "/data/local/bin/su",
            "/system/sd/xbin/su",
            "/system/bin/failsafe/su",
            "/data/local/su"
        )

        return paths.any { File(it).exists() }
    }

    /**
     * Check if debugger is connected
     */
    fun isDebuggerConnected(): Boolean {
        return try {
            // Check via Debug class
            android.os.Debug.isDebuggerConnected() || 
            // Check via system properties
            System.getProperty("ro.debuggable") == "1" ||
            System.getProperty("ro.secure") == "0" ||
            // Check if running under debugger
            checkDebugPort()
        } catch (e: Exception) {
            Log.e(TAG, "Error checking debugger: ${e.message}")
            false
        }
    }

    /**
     * Check if debugger port is open
     */
    private fun checkDebugPort(): Boolean {
        return try {
            val result = Runtime.getRuntime().exec("netstat -an")
                .inputStream.bufferedReader().use { reader ->
                    reader.readText().contains(":5037")
                }
            result
        } catch (e: Exception) {
            false
        }
    }

    /**
     * Check if running on emulator
     */
    fun isRunningOnEmulator(): Boolean {
        return Build.FINGERPRINT.contains("generic") ||
                Build.FINGERPRINT.contains("unknown") ||
                Build.MODEL.contains("Google") ||
                Build.PRODUCT.contains("sdk") ||
                Build.DEVICE.contains("generic") ||
                Build.BRAND == "generic"
    }

    /**
     * Check if app signature is valid
     */
    fun isSignatureValid(context: Context): Boolean {
        return try {
            val packageInfo = context.packageManager.getPackageInfo(
                context.packageName,
                PackageManager.GET_SIGNATURES
            )
            
            // Check if signatures exist
            val signatures = packageInfo.signatures
            signatures != null && signatures.isNotEmpty()
        } catch (e: Exception) {
            Log.e(TAG, "Error checking signature: ${e.message}")
            false
        }
    }

    /**
     * Check if USB debugging is enabled
     */
    fun isUSBDebuggingEnabled(context: Context): Boolean {
        return try {
            Settings.Secure.getInt(
                context.contentResolver,
                Settings.Global.ADB_ENABLED,
                0
            ) == 1
        } catch (e: Exception) {
            Log.e(TAG, "Error checking USB debugging: ${e.message}")
            false
        }
    }

    /**
     * Perform comprehensive security check
     */
    fun performSecurityCheck(context: Context): SecurityCheckResult {
        val isRooted = isDeviceRooted(context)
        val isDebugging = isDebuggerConnected()
        val isEmulator = isRunningOnEmulator()
        val isValidSignature = isSignatureValid(context)
        val isUSBDebugEnabled = isUSBDebuggingEnabled(context)

        val isTampered = isRooted || isDebugging || !isValidSignature || isUSBDebugEnabled

        return SecurityCheckResult(
            isRooted = isRooted,
            isDebugging = isDebugging,
            isEmulator = isEmulator,
            isValidSignature = isValidSignature,
            isUSBDebugEnabled = isUSBDebugEnabled,
            isTampered = isTampered
        )
    }

    data class SecurityCheckResult(
        val isRooted: Boolean,
        val isDebugging: Boolean,
        val isEmulator: Boolean,
        val isValidSignature: Boolean,
        val isUSBDebugEnabled: Boolean,
        val isTampered: Boolean
    )
}
