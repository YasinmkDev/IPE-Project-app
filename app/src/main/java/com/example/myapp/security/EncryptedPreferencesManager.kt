package com.example.myapp.security

import android.content.Context
import android.content.SharedPreferences
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import android.util.Log

class EncryptedPreferencesManager(context: Context) {
    companion object {
        private const val TAG = "EncryptedPrefs"
        private const val PREFERENCES_FILE = "secure_parental_control_prefs"
    }

    private val sharedPreferences: SharedPreferences

    init {
        try {
            val masterKey = MasterKey.Builder(context)
                .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
                .build()

            sharedPreferences = EncryptedSharedPreferences.create(
                context,
                PREFERENCES_FILE,
                masterKey,
                EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
                EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
            )
        } catch (e: Exception) {
            Log.e(TAG, "Error initializing encrypted preferences: ${e.message}")
            throw e
        }
    }

    fun setString(key: String, value: String) {
        try {
            sharedPreferences.edit().putString(key, value).apply()
        } catch (e: Exception) {
            Log.e(TAG, "Error saving string: ${e.message}")
        }
    }

    fun getString(key: String, defaultValue: String = ""): String {
        return try {
            sharedPreferences.getString(key, defaultValue) ?: defaultValue
        } catch (e: Exception) {
            Log.e(TAG, "Error reading string: ${e.message}")
            defaultValue
        }
    }

    fun setInt(key: String, value: Int) {
        try {
            sharedPreferences.edit().putInt(key, value).apply()
        } catch (e: Exception) {
            Log.e(TAG, "Error saving int: ${e.message}")
        }
    }

    fun getInt(key: String, defaultValue: Int = 0): Int {
        return try {
            sharedPreferences.getInt(key, defaultValue)
        } catch (e: Exception) {
            Log.e(TAG, "Error reading int: ${e.message}")
            defaultValue
        }
    }

    fun setBoolean(key: String, value: Boolean) {
        try {
            sharedPreferences.edit().putBoolean(key, value).apply()
        } catch (e: Exception) {
            Log.e(TAG, "Error saving boolean: ${e.message}")
        }
    }

    fun getBoolean(key: String, defaultValue: Boolean = false): Boolean {
        return try {
            sharedPreferences.getBoolean(key, defaultValue)
        } catch (e: Exception) {
            Log.e(TAG, "Error reading boolean: ${e.message}")
            defaultValue
        }
    }

    fun setLong(key: String, value: Long) {
        try {
            sharedPreferences.edit().putLong(key, value).apply()
        } catch (e: Exception) {
            Log.e(TAG, "Error saving long: ${e.message}")
        }
    }

    fun getLong(key: String, defaultValue: Long = 0L): Long {
        return try {
            sharedPreferences.getLong(key, defaultValue)
        } catch (e: Exception) {
            Log.e(TAG, "Error reading long: ${e.message}")
            defaultValue
        }
    }

    fun remove(key: String) {
        try {
            sharedPreferences.edit().remove(key).apply()
        } catch (e: Exception) {
            Log.e(TAG, "Error removing key: ${e.message}")
        }
    }

    fun clear() {
        try {
            sharedPreferences.edit().clear().apply()
        } catch (e: Exception) {
            Log.e(TAG, "Error clearing preferences: ${e.message}")
        }
    }
}
