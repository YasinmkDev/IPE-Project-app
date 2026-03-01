package com.example.myapp.models

import java.io.Serializable

enum class AgeGroup(val minAge: Int, val maxAge: Int, val displayName: String) {
    TODDLER(0, 5, "Toddler (0-5)"),
    CHILD(6, 12, "Child (6-12)"),
    TEEN(13, 17, "Teen (13-17)"),
    ADULT(18, 99, "Adult (18+)");

    companion object {
        fun fromAge(age: Int): AgeGroup {
            return when {
                age <= 5 -> TODDLER
                age <= 12 -> CHILD
                age <= 17 -> TEEN
                else -> ADULT
            }
        }

        fun fromString(name: String): AgeGroup {
            return try {
                valueOf(name)
            } catch (e: Exception) {
                CHILD
            }
        }
    }
}

data class RestrictionProfile(
    val ageGroup: AgeGroup,
    val blockedAppsKeywords: List<String>,
    val blockedWebsites: List<String>,
    val allowedAppsOnly: Boolean,
    val screenTimeMinutes: Int,
    val bedtimeStart: String,
    val bedtimeEnd: String,
    val allowedCategories: List<String>,
    val allowUninstall: Boolean,
    val allowDataClear: Boolean,
    val allowStorageAccess: Boolean,
    val allowSettingsAccess: Boolean
) : Serializable

object RestrictionProfiles {
    val TODDLER_PROFILE = RestrictionProfile(
        ageGroup = AgeGroup.TODDLER,
        blockedAppsKeywords = listOf(
            "browser", "youtube", "tiktok", "instagram", "facebook",
            "messenger", "whatsapp", "telegram", "snapchat", "dating",
            "adult", "game", "social"
        ),
        blockedWebsites = listOf(
            "youtube.com", "tiktok.com", "instagram.com", "facebook.com",
            "twitter.com", "reddit.com", "twitch.tv", "discord.com"
        ),
        allowedAppsOnly = true,
        screenTimeMinutes = 60,
        bedtimeStart = "20:00",
        bedtimeEnd = "06:00",
        allowedCategories = listOf("EDUCATION", "MUSIC", "BOOKS"),
        allowUninstall = false,
        allowDataClear = false,
        allowStorageAccess = false,
        allowSettingsAccess = false
    )

    val CHILD_PROFILE = RestrictionProfile(
        ageGroup = AgeGroup.CHILD,
        blockedAppsKeywords = listOf(
            "tiktok", "snapchat", "dating", "adult",
            "gambling", "trading", "nsfw"
        ),
        blockedWebsites = listOf(
            "tiktok.com", "snapchat.com", "adult.com", "gambling.com",
            "pornographic.com"
        ),
        allowedAppsOnly = false,
        screenTimeMinutes = 120,
        bedtimeStart = "21:00",
        bedtimeEnd = "07:00",
        allowedCategories = listOf("EDUCATION", "MUSIC", "BOOKS", "GAMES"),
        allowUninstall = false,
        allowDataClear = false,
        allowStorageAccess = false,
        allowSettingsAccess = false
    )

    val TEEN_PROFILE = RestrictionProfile(
        ageGroup = AgeGroup.TEEN,
        blockedAppsKeywords = listOf(
            "adult", "gambling", "nsfw"
        ),
        blockedWebsites = listOf(
            "adult.com", "gambling.com", "pornographic.com"
        ),
        allowedAppsOnly = false,
        screenTimeMinutes = 240,
        bedtimeStart = "23:00",
        bedtimeEnd = "06:00",
        allowedCategories = listOf("EDUCATION", "MUSIC", "BOOKS", "GAMES", "SOCIAL"),
        allowUninstall = false,
        allowDataClear = false,
        allowStorageAccess = true,
        allowSettingsAccess = false
    )

    val ADULT_PROFILE = RestrictionProfile(
        ageGroup = AgeGroup.ADULT,
        blockedAppsKeywords = listOf(),
        blockedWebsites = listOf(),
        allowedAppsOnly = false,
        screenTimeMinutes = 0,
        bedtimeStart = "00:00",
        bedtimeEnd = "00:00",
        allowedCategories = listOf(),
        allowUninstall = true,
        allowDataClear = true,
        allowStorageAccess = true,
        allowSettingsAccess = true
    )

    fun getProfileForAge(age: Int): RestrictionProfile {
        return when (AgeGroup.fromAge(age)) {
            AgeGroup.TODDLER -> TODDLER_PROFILE
            AgeGroup.CHILD -> CHILD_PROFILE
            AgeGroup.TEEN -> TEEN_PROFILE
            AgeGroup.ADULT -> ADULT_PROFILE
        }
    }

    fun getProfileByGroup(ageGroup: AgeGroup): RestrictionProfile {
        return when (ageGroup) {
            AgeGroup.TODDLER -> TODDLER_PROFILE
            AgeGroup.CHILD -> CHILD_PROFILE
            AgeGroup.TEEN -> TEEN_PROFILE
            AgeGroup.ADULT -> ADULT_PROFILE
        }
    }
}

class AgeGroupManager {
    fun calculateAge(birthDate: Long): Int {
        val calendar = java.util.Calendar.getInstance()
        val currentYear = calendar.get(java.util.Calendar.YEAR)
        val currentMonth = calendar.get(java.util.Calendar.MONTH)
        val currentDay = calendar.get(java.util.Calendar.DAY_OF_MONTH)

        calendar.timeInMillis = birthDate
        val birthYear = calendar.get(java.util.Calendar.YEAR)
        val birthMonth = calendar.get(java.util.Calendar.MONTH)
        val birthDay = calendar.get(java.util.Calendar.DAY_OF_MONTH)

        var age = currentYear - birthYear
        if (currentMonth < birthMonth || (currentMonth == birthMonth && currentDay < birthDay)) {
            age--
        }
        return age
    }

    fun isAppBlockedForAge(appName: String, ageGroup: AgeGroup): Boolean {
        val profile = RestrictionProfiles.getProfileByGroup(ageGroup)
        val lowerAppName = appName.lowercase()
        return profile.blockedAppsKeywords.any { keyword ->
            lowerAppName.contains(keyword.lowercase())
        }
    }

    fun isWebsiteBlockedForAge(url: String, ageGroup: AgeGroup): Boolean {
        val profile = RestrictionProfiles.getProfileByGroup(ageGroup)
        val lowerUrl = url.lowercase()
        return profile.blockedWebsites.any { website ->
            lowerUrl.contains(website.lowercase())
        }
    }

    fun shouldEnforceScreenTime(ageGroup: AgeGroup): Boolean {
        return RestrictionProfiles.getProfileByGroup(ageGroup).screenTimeMinutes > 0
    }

    fun getScreenTimeLimit(ageGroup: AgeGroup): Int {
        return RestrictionProfiles.getProfileByGroup(ageGroup).screenTimeMinutes
    }

    fun shouldEnforceBedtime(ageGroup: AgeGroup): Boolean {
        val profile = RestrictionProfiles.getProfileByGroup(ageGroup)
        return profile.bedtimeStart != "00:00" && profile.bedtimeEnd != "00:00"
    }

    fun getBedtimeHours(ageGroup: AgeGroup): Pair<String, String> {
        val profile = RestrictionProfiles.getProfileByGroup(ageGroup)
        return Pair(profile.bedtimeStart, profile.bedtimeEnd)
    }
}
