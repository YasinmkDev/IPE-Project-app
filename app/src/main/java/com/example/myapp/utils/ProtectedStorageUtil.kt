package com.example.myapp.utils

import android.content.Context
import android.os.Build

/**
 * Utility object for handling device-protected storage context.
 * This is needed to persist data even when the device is encrypted.
 */
object ProtectedStorageUtil {
    
    /**
     * Get the appropriate context for storing sensitive data.
     * On Android N+, uses device-protected storage context.
     */
    fun getProtectedContext(context: Context): Context {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            context.createDeviceProtectedStorageContext()
        } else {
            context
        }
    }

    /**
     * Get stored child ID from protected shared preferences.
     */
    fun getStoredChildId(context: Context): String? {
        val protectedContext = getProtectedContext(context)
        val prefs = protectedContext.getSharedPreferences("APP_PREFS", Context.MODE_PRIVATE)
        return prefs.getString("CHILD_ID", null)
    }

    /**
     * Save child ID to both protected and regular shared preferences.
     * Protected context ensures data persists after device encryption.
     */
    fun saveChildId(context: Context, childId: String) {
        val protectedContext = getProtectedContext(context)
        protectedContext.getSharedPreferences("APP_PREFS", Context.MODE_PRIVATE)
            .edit().putString("CHILD_ID", childId).apply()
        context.getSharedPreferences("APP_PREFS", Context.MODE_PRIVATE)
            .edit().putString("CHILD_ID", childId).apply()
    }

    /**
     * Clear stored child ID from both protected and regular preferences.
     */
    fun clearChildId(context: Context) {
        val protectedContext = getProtectedContext(context)
        protectedContext.getSharedPreferences("APP_PREFS", Context.MODE_PRIVATE)
            .edit().remove("CHILD_ID").apply()
        context.getSharedPreferences("APP_PREFS", Context.MODE_PRIVATE)
            .edit().remove("CHILD_ID").apply()
    }
}
