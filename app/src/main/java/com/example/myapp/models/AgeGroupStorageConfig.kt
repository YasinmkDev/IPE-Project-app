package com.example.myapp.models

import android.os.Environment

/**
 * Constants and configuration for age group-based storage permissions.
 * Centralized to avoid duplication between AgeGroupManager and StorageRestrictionService.
 */
object AgeGroupStorageConfig {
    
    /**
     * Check if storage access is allowed for a given age group.
     */
    fun canAccessStorage(ageGroup: AgeGroup): Boolean {
        return when (ageGroup) {
            AgeGroup.TODDLER, AgeGroup.CHILD -> false
            AgeGroup.TEEN, AgeGroup.ADULT -> true
        }
    }

    /**
     * Check if storage should be restricted for a given age group.
     */
    fun isStorageRestricted(ageGroup: AgeGroup): Boolean {
        return !canAccessStorage(ageGroup)
    }

    /**
     * Get list of restricted directories for age group.
     */
    fun getRestrictedDirectories(ageGroup: AgeGroup): List<String> {
        return when (ageGroup) {
            AgeGroup.TODDLER -> listOf(
                Environment.DIRECTORY_DOWNLOADS,
                Environment.DIRECTORY_DOCUMENTS,
                Environment.DIRECTORY_PICTURES,
                Environment.DIRECTORY_MOVIES
            )
            AgeGroup.CHILD -> listOf(
                Environment.DIRECTORY_DOWNLOADS,
                Environment.DIRECTORY_DOCUMENTS
            )
            AgeGroup.TEEN -> listOf(
                Environment.DIRECTORY_DOWNLOADS
            )
            AgeGroup.ADULT -> emptyList()
        }
    }

    /**
     * Legacy support - accepts string age group name.
     */
    fun canAccessStorage(ageGroupName: String): Boolean {
        return try {
            canAccessStorage(AgeGroup.valueOf(ageGroupName))
        } catch (e: Exception) {
            false
        }
    }

    /**
     * Legacy support - accepts string age group name.
     */
    fun isStorageRestricted(ageGroupName: String): Boolean {
        return try {
            isStorageRestricted(AgeGroup.valueOf(ageGroupName))
        } catch (e: Exception) {
            true
        }
    }

    /**
     * Legacy support - accepts string age group name.
     */
    fun getRestrictedDirectories(ageGroupName: String): List<String> {
        return try {
            getRestrictedDirectories(AgeGroup.valueOf(ageGroupName))
        } catch (e: Exception) {
            emptyList()
        }
    }
}
