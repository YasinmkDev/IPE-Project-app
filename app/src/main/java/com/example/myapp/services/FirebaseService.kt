package com.example.myapp.services

import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.IgnoreExtraProperties
import com.google.firebase.firestore.PropertyName
import com.google.firebase.firestore.ktx.firestore
import com.google.firebase.ktx.Firebase

object FirebaseService {
    private val db: FirebaseFirestore = Firebase.firestore
    private val parentIdCache = mutableMapOf<String, String>()

    @IgnoreExtraProperties
    data class ChildProfile(
        val childId: String = "",
        val parentId: String = "",
        val name: String = "",
        val age: Int = 0,
        val ageGroup: String = "",
        val birthDate: Long = 0L,
        val ageDetectionConfidence: Double = 0.0,
        val ageDetectionSource: String = "",
        val ageDetectionUpdatedAt: Long = 0L,
        val screenshotRequestAt: Long = 0L,
        val screenshotRequestBy: String = "",
        val screenshotRequestStatus: String = "",
        val pairingCode: String = "",
        val blockedApps: List<String> = emptyList(),
        val blockedWebsites: List<String> = emptyList(),
        val blockedDomains: List<String> = emptyList(),
        val allowedDomains: List<String> = emptyList(),
        val allowedApps: List<String> = emptyList(),
        val allowedWebsites: List<String> = emptyList(),
        val storageRestricted: Boolean = false,
        val dnsFilterEnabled: Boolean = false,
        val uninstallModeEnabled: Boolean = false,
        val uninstallWindowEndsAt: Long = 0L,
        val uninstallApprovedBy: String = "",
        @get:PropertyName("protectionActive")
        @set:PropertyName("protectionActive")
        var protectionActive: Boolean = true,
        val linkedAt: Any? = null,
        val createdAt: Any? = null
    )

    data class AppInfo(
        val packageName: String = "",
        val name: String = "",
        val versionName: String = "",
        val versionCode: Long = 0L,
        @get:PropertyName("isSystemApp")
        @set:PropertyName("isSystemApp")
        var isSystemApp: Boolean = false
    )

    // New Data Class for Screen Time Tracking
    data class ScreenTimeData(
        val packageName: String = "",
        val appName: String = "",
        val totalTimeVisible: Long = 0L, // in milliseconds
        val lastUpdated: Long = System.currentTimeMillis()
    )

    data class ChildEvent(
        val type: String = "",
        val severity: String = "low",
        val details: Map<String, String> = emptyMap(),
        val timestamp: Long = System.currentTimeMillis()
    )

    data class ActivitySnapshot(
        val packageName: String = "",
        val appName: String = "",
        val timestamp: Long = System.currentTimeMillis(),
        val source: String = "foreground_tracker"
    )

    data class RemoteScreenshot(
        val imageBase64: String = "",
        val mimeType: String = "image/jpeg",
        val capturedAt: Long = System.currentTimeMillis(),
        val requestedBy: String = "",
        val requestAt: Long = 0L
    )

    fun resolvePairingCode(pairingCode: String, onSuccess: (String, String) -> Unit, onFailure: (Exception) -> Unit) {
        db.collection("pairingCodes").document(pairingCode)
            .get()
            .addOnSuccessListener { document ->
                if (document.exists()) {
                    val childId = document.getString("childId")
                    val parentId = document.getString("parentId")
                    if (childId != null && parentId != null) {
                        onSuccess(childId, parentId)
                    } else {
                        onFailure(Exception("Child ID or Parent ID not found"))
                    }
                } else {
                    onFailure(Exception("Pairing code not found"))
                }
            }
            .addOnFailureListener(onFailure)
    }

    fun markDeviceAsLinked(childId: String, parentId: String, onSuccess: () -> Unit, onFailure: (Exception) -> Unit) {
        val childRef = db.collection("parents").document(parentId)
            .collection("children").document(childId)
        
        childRef.update("linkedAt", com.google.firebase.Timestamp.now(), "protectionActive", true)
            .addOnSuccessListener { onSuccess() }
            .addOnFailureListener(onFailure)
    }

    fun uploadInstalledApps(childId: String, apps: List<AppInfo>) {
        db.collection("childLinks").document(childId)
            .get()
            .addOnSuccessListener { linkDoc ->
                if (linkDoc.exists()) {
                    val parentId = linkDoc.getString("parentId")
                    if (parentId != null) {
                        apps.forEach { app ->
                            db.collection("parents").document(parentId)
                                .collection("children").document(childId)
                                .collection("installedApps")
                                .document(app.packageName)
                                .set(app)
                        }
                    }
                }
            }
    }

    // New: Update Screen Time for a specific app
    fun updateAppScreenTime(childId: String, data: ScreenTimeData) {
        resolveParentId(childId) { parentId ->
            db.collection("parents").document(parentId)
                .collection("children").document(childId)
                .collection("screenTime")
                .document(data.packageName)
                .set(data)
        }
    }

    fun logChildEvent(childId: String, event: ChildEvent) {
        resolveParentId(childId) { parentId ->
            db.collection("parents").document(parentId)
                .collection("children").document(childId)
                .collection("events")
                .add(event)
        }
    }

    fun logActivitySnapshot(childId: String, snapshot: ActivitySnapshot) {
        resolveParentId(childId) { parentId ->
            db.collection("parents").document(parentId)
                .collection("children").document(childId)
                .collection("activitySnapshots")
                .add(snapshot)
        }
    }

    fun logRemoteScreenshot(childId: String, screenshot: RemoteScreenshot) {
        resolveParentId(childId) { parentId ->
            db.collection("parents").document(parentId)
                .collection("children").document(childId)
                .collection("remoteScreenshots")
                .add(screenshot)
        }
    }

    fun updateScreenshotRequestStatus(
        childId: String,
        status: String,
        lastCaptureAt: Long? = null,
        error: String? = null
    ) {
        resolveParentId(childId) { parentId ->
            val payload = mutableMapOf<String, Any>(
                "screenshotRequestStatus" to status
            )
            lastCaptureAt?.let { payload["lastScreenshotCapturedAt"] = it }
            error?.let { payload["screenshotRequestError"] = it }
            db.collection("parents").document(parentId)
                .collection("children").document(childId)
                .update(payload)
        }
    }

    fun updateAgeAssessment(
        childId: String,
        inferredAge: Int?,
        ageGroup: String,
        confidence: Double,
        source: String,
        evidence: List<String>
    ) {
        resolveParentId(childId) { parentId ->
            val payload = mutableMapOf<String, Any>(
                "ageGroup" to ageGroup,
                "ageDetectionConfidence" to confidence,
                "ageDetectionSource" to source,
                "ageDetectionUpdatedAt" to System.currentTimeMillis(),
                "ageDetectionEvidence" to evidence
            )
            inferredAge?.let { payload["age"] = it }
            db.collection("parents").document(parentId)
                .collection("children").document(childId)
                .update(payload)
        }
    }

    fun fetchChildProfile(childId: String, onSuccess: (ChildProfile) -> Unit, onFailure: (Exception) -> Unit) {
        db.collection("childLinks").document(childId)
            .get()
            .addOnSuccessListener { linkDoc ->
                if (linkDoc.exists()) {
                    val parentId = linkDoc.getString("parentId")
                    if (parentId != null) {
                        db.collection("parents").document(parentId)
                            .collection("children").document(childId)
                            .get()
                            .addOnSuccessListener { document ->
                                val profile = document.toObject(ChildProfile::class.java)
                                profile?.let(onSuccess) ?: onFailure(Exception("Profile null"))
                            }
                            .addOnFailureListener(onFailure)
                    }
                }
            }
            .addOnFailureListener(onFailure)
    }

    fun listenToChildProfileUpdates(childId: String, onUpdate: (ChildProfile) -> Unit, onError: (Exception) -> Unit) {
        db.collection("childLinks").document(childId)
            .get()
            .addOnSuccessListener { linkDoc ->
                if (linkDoc.exists()) {
                    val parentId = linkDoc.getString("parentId")
                    if (parentId != null) {
                        db.collection("parents").document(parentId)
                            .collection("children").document(childId)
                            .addSnapshotListener { snapshot, e ->
                                if (e != null) { onError(e); return@addSnapshotListener }
                                snapshot?.toObject(ChildProfile::class.java)?.let(onUpdate)
                            }
                    }
                }
            }
    }

    private fun resolveParentId(childId: String, onResolved: (String) -> Unit) {
        val cached = parentIdCache[childId]
        if (cached != null) {
            onResolved(cached)
            return
        }
        db.collection("childLinks").document(childId)
            .get()
            .addOnSuccessListener { linkDoc ->
                val parentId = linkDoc.getString("parentId")
                if (parentId != null) {
                    parentIdCache[childId] = parentId
                    onResolved(parentId)
                }
            }
    }
}
