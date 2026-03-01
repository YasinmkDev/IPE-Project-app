package com.example.myapp.services

import android.app.Service
import android.content.Intent
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.os.Environment
import android.os.FileObserver
import android.util.Log
import java.io.File

class StorageRestrictionService : Service() {
    companion object {
        private const val TAG = "StorageRestrictionService"
    }

    private var fileObservers = mutableListOf<FileObserver>()

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        Log.d(TAG, "StorageRestrictionService started")
        startStorageMonitoring()
        return START_STICKY
    }

    private fun startStorageMonitoring() {
        try {
            val downloadDir = Environment.getExternalStoragePublicDirectory(
                Environment.DIRECTORY_DOWNLOADS
            )
            val documentsDir = Environment.getExternalStoragePublicDirectory(
                Environment.DIRECTORY_DOCUMENTS
            )

            monitorDirectory(downloadDir)
            monitorDirectory(documentsDir)

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                val appSpecificDir = getExternalFilesDir(null)
                appSpecificDir?.let { monitorDirectory(it) }
            }

            Log.d(TAG, "Storage monitoring started")
        } catch (e: Exception) {
            Log.e(TAG, "Error starting storage monitoring: ${e.message}")
        }
    }

    private fun monitorDirectory(directory: File) {
        try {
            if (!directory.exists()) {
                directory.mkdirs()
            }

            val observer = object : FileObserver(directory) {
                override fun onEvent(event: Int, path: String?) {
                    when (event) {
                        CREATE -> Log.d(TAG, "File created in ${directory.name}: $path")
                        DELETE -> handleFileDelete(directory, path)
                        MOVED_FROM -> Log.d(TAG, "File moved from ${directory.name}: $path")
                        MOVED_TO -> Log.d(TAG, "File moved to ${directory.name}: $path")
                    }
                }

                private fun handleFileDelete(dir: File, path: String?) {
                    if (path != null && (path.endsWith(".txt") || path.endsWith(".pdf") || path.endsWith(".doc"))) {
                        Log.w(TAG, "Sensitive file deleted from ${dir.name}: $path")
                    }
                }
            }

            observer.startWatching()
            fileObservers.add(observer)
        } catch (e: Exception) {
            Log.e(TAG, "Error monitoring directory: ${e.message}")
        }
    }

    /**
     * Check if app has storage permission
     */
    fun canAccessStorage(childAgeGroup: String): Boolean {
        return when (childAgeGroup) {
            "TODDLER", "CHILD" -> false
            "TEEN" -> true
            "ADULT" -> true
            else -> false
        }
    }

    /**
     * Check if storage access should be restricted
     */
    fun isStorageRestricted(childAgeGroup: String): Boolean {
        return when (childAgeGroup) {
            "TODDLER", "CHILD" -> true
            "TEEN" -> false
            "ADULT" -> false
            else -> true
        }
    }

    /**
     * Block access to specific directory
     */
    fun blockDirectoryAccess(packageName: String, directory: File): Boolean {
        return try {
            if (directory.exists()) {
                // Set permissions to restrict access
                val result = directory.setReadable(false, false)
                val result2 = directory.setWritable(false, false)
                Log.d(TAG, "Directory access blocked: ${directory.absolutePath}")
                result && result2
            } else {
                false
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error blocking directory access: ${e.message}")
            false
        }
    }

    /**
     * Allow access to specific directory
     */
    fun allowDirectoryAccess(packageName: String, directory: File): Boolean {
        return try {
            if (directory.exists()) {
                val result = directory.setReadable(true, false)
                val result2 = directory.setWritable(true, false)
                Log.d(TAG, "Directory access allowed: ${directory.absolutePath}")
                result && result2
            } else {
                false
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error allowing directory access: ${e.message}")
            false
        }
    }

    /**
     * Get list of restricted directories for age group
     */
    fun getRestrictedDirectories(ageGroup: String): List<String> {
        return when (ageGroup) {
            "TODDLER" -> listOf(
                Environment.DIRECTORY_DOWNLOADS,
                Environment.DIRECTORY_DOCUMENTS,
                Environment.DIRECTORY_PICTURES,
                Environment.DIRECTORY_MOVIES
            )
            "CHILD" -> listOf(
                Environment.DIRECTORY_DOWNLOADS,
                Environment.DIRECTORY_DOCUMENTS
            )
            "TEEN" -> listOf(
                Environment.DIRECTORY_DOWNLOADS
            )
            else -> emptyList()
        }
    }

    /**
     * Check if file access is allowed
     */
    fun isFileAccessAllowed(filePath: String, ageGroup: String): Boolean {
        val file = File(filePath)
        val restrictedDirs = getRestrictedDirectories(ageGroup)

        return !restrictedDirs.any { restrictedDir ->
            filePath.contains(restrictedDir)
        }
    }

    /**
     * Scan for sensitive files in restricted areas
     */
    fun scanForSensitiveFiles(ageGroup: String): List<File> {
        val sensitiveFiles = mutableListOf<File>()
        val restrictedDirs = getRestrictedDirectories(ageGroup)

        restrictedDirs.forEach { dir ->
            try {
                val directory = Environment.getExternalStoragePublicDirectory(dir)
                if (directory.exists()) {
                    scanDirectory(directory, sensitiveFiles)
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error scanning directory: ${e.message}")
            }
        }

        return sensitiveFiles
    }

    private fun scanDirectory(directory: File, files: MutableList<File>) {
        try {
            directory.listFiles()?.forEach { file ->
                if (file.isFile && isSensitiveFile(file)) {
                    files.add(file)
                } else if (file.isDirectory && file.canRead()) {
                    scanDirectory(file, files)
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error scanning directory: ${e.message}")
        }
    }

    private fun isSensitiveFile(file: File): Boolean {
        val name = file.name.lowercase()
        return name.endsWith(".pdf") || 
               name.endsWith(".doc") || 
               name.endsWith(".docx") ||
               name.endsWith(".xls") ||
               name.endsWith(".xlsx") ||
               name.startsWith(".")
    }

    /**
     * Clear cache and temporary files for child
     */
    fun clearChildCacheAndTemp(): Boolean {
        return try {
            // Clear app cache
            cacheDir?.deleteRecursively()
            
            // Clear temp files
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                externalCacheDir?.deleteRecursively()
            }

            Log.d(TAG, "Cache and temp files cleared")
            true
        } catch (e: Exception) {
            Log.e(TAG, "Error clearing cache: ${e.message}")
            false
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        fileObservers.forEach { it.stopWatching() }
        fileObservers.clear()
        Log.d(TAG, "StorageRestrictionService destroyed")
    }

    override fun onBind(intent: Intent?) = null
}
