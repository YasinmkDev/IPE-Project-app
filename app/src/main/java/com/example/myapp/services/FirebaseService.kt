package com.example.myapp.services

import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.IgnoreExtraProperties
import com.google.firebase.firestore.PropertyName
import com.google.firebase.firestore.ktx.firestore
import com.google.firebase.ktx.Firebase

object FirebaseService {
    private val db: FirebaseFirestore = Firebase.firestore

    @IgnoreExtraProperties
    data class ChildProfile(
        val childId: String = "",
        val parentId: String = "",
        val name: String = "",
        val age: Int = 0,
        val ageGroup: String = "",
        val pairingCode: String = "",
        val blockedApps: List<String> = emptyList(),
        val blockedWebsites: List<String> = emptyList(),
        val allowedApps: List<String> = emptyList(),
        val allowedWebsites: List<String> = emptyList(),
        val storageRestricted: Boolean = false,
        @get:PropertyName("protectionActive")
        @set:PropertyName("protectionActive")
        var protectionActive: Boolean = true,
        val linkedAt: Any? = null,
        val createdAt: Any? = null,
        val screenshotRequestAt: Long = 0L,
        val screenshotRequestBy: String = "",
        val screenshotRequestStatus: String = ""
    )

    data class AppInfo(
        val packageName: String = "",
        val name: String = "",
        val versionName: String = "",
        val versionCode: Long = 0L,
        @get:PropertyName("isSystemApp")
        @set:PropertyName("isSystemApp")
        var isSystemApp: Boolean = false,
        val icon: String? = null
    )

    data class ActivityEvent(
        val type: String = "", 
        val severity: String = "low",
        val title: String = "",
        val details: String = "",
        val timestamp: Long = System.currentTimeMillis()
    )

    fun resolvePairingCode(pairingCode: String, onSuccess: (String, String) -> Unit, onFailure: (Exception) -> Unit) {
        db.collection("pairingCodes").document(pairingCode).get()
            .addOnSuccessListener { doc ->
                val cid = doc.getString("childId")
                val pid = doc.getString("parentId")
                if (cid != null && pid != null) onSuccess(cid, pid) else onFailure(Exception("Invalid"))
            }
            .addOnFailureListener(onFailure)
    }

    fun logEvent(childId: String, event: ActivityEvent) {
        db.collection("childLinks").document(childId).get().addOnSuccessListener { link ->
            val pid = link.getString("parentId") ?: return@addOnSuccessListener
            db.collection("parents").document(pid).collection("children").document(childId)
                .collection("events").add(event)
        }
    }

    // FIXED: Using "imageBase64" to match the Admin Panel's RemoteScreenshotsTab.tsx
    fun uploadRemoteScreenshot(
        childId: String,
        base64Image: String,
        requestAt: Long,
        requestedBy: String,
        onSuccess: () -> Unit,
        onFailure: (Exception) -> Unit
    ) {
        db.collection("childLinks").document(childId).get()
            .addOnSuccessListener { link ->
                val pid = link.getString("parentId")
                if (pid.isNullOrBlank()) {
                    onFailure(Exception("parent_link_not_found"))
                    return@addOnSuccessListener
                }
                val now = System.currentTimeMillis()
                val data = mapOf(
                    "imageBase64" to base64Image,
                    "capturedAt" to now,
                    "mimeType" to "image/jpeg",
                    "requestedBy" to requestedBy,
                    "requestAt" to requestAt
                )
                db.collection("parents").document(pid).collection("children").document(childId)
                    .collection("remoteScreenshots").add(data)
                    .addOnSuccessListener {
                        db.collection("parents").document(pid).collection("children").document(childId)
                            .update("lastScreenshotCapturedAt", now)
                            .addOnSuccessListener { onSuccess() }
                            .addOnFailureListener { onSuccess() }
                    }
                    .addOnFailureListener(onFailure)
            }
            .addOnFailureListener(onFailure)
    }

    fun updateScreenshotRequestStatus(childId: String, status: String, error: String? = null) {
        db.collection("childLinks").document(childId).get().addOnSuccessListener { link ->
            val pid = link.getString("parentId") ?: return@addOnSuccessListener
            val update = mutableMapOf<String, Any>("screenshotRequestStatus" to status)
            if (error != null) update["screenshotRequestError"] = error
            db.collection("parents").document(pid).collection("children").document(childId).update(update)
        }
    }

    fun uploadInstalledApps(childId: String, apps: List<AppInfo>) {
        db.collection("childLinks").document(childId).get().addOnSuccessListener { link ->
            val pid = link.getString("parentId") ?: return@addOnSuccessListener
            apps.forEach { app ->
                db.collection("parents").document(pid).collection("children").document(childId)
                    .collection("installedApps").document(app.packageName).set(app)
            }
        }
    }

    fun updateAppScreenTime(childId: String, data: Any) {
        db.collection("childLinks").document(childId).get().addOnSuccessListener { link ->
            val pid = link.getString("parentId") ?: return@addOnSuccessListener
            val packageName = if (data is Map<*, *>) data["packageName"] as? String else null
            if (packageName != null) {
                db.collection("parents").document(pid).collection("children").document(childId)
                    .collection("screenTime").document(packageName).set(data)
            }
        }
    }

    fun uploadActivitySnapshot(
        childId: String,
        packageName: String,
        appName: String,
        imageBase64: String? = null
    ) {
        db.collection("childLinks").document(childId).get().addOnSuccessListener { link ->
            val pid = link.getString("parentId") ?: return@addOnSuccessListener
            val snapshot = mutableMapOf<String, Any>(
                "packageName" to packageName,
                "appName" to appName,
                "timestamp" to System.currentTimeMillis()
            )
            if (!imageBase64.isNullOrBlank()) {
                snapshot["image"] = imageBase64
                snapshot["imageBase64"] = imageBase64
            }
            db.collection("parents").document(pid).collection("children").document(childId)
                .collection("activitySnapshots").add(snapshot)
        }
    }

    fun markDeviceAsLinked(
        childId: String,
        parentId: String,
        onSuccess: () -> Unit,
        onFailure: (Exception) -> Unit
    ) {
        val linkData = mapOf(
            "childId" to childId,
            "parentId" to parentId,
            "linkedAt" to FieldValue.serverTimestamp()
        )

        db.collection("childLinks").document(childId).set(linkData)
            .addOnSuccessListener {
                db.collection("parents").document(parentId).collection("children").document(childId)
                    .update(mapOf("linkedAt" to FieldValue.serverTimestamp(), "parentId" to parentId))
                    .addOnSuccessListener { onSuccess() }
                    .addOnFailureListener { updateErr ->
                        // Child doc may not exist yet; create minimal record so onboarding can complete.
                        db.collection("parents").document(parentId).collection("children").document(childId)
                            .set(
                                mapOf(
                                    "childId" to childId,
                                    "parentId" to parentId,
                                    "linkedAt" to FieldValue.serverTimestamp()
                                ),
                                com.google.firebase.firestore.SetOptions.merge()
                            )
                            .addOnSuccessListener { onSuccess() }
                            .addOnFailureListener { createErr ->
                                onFailure(Exception("Failed to link device: ${updateErr.message ?: createErr.message}"))
                            }
                    }
            }
            .addOnFailureListener(onFailure)
    }

    fun fetchChildProfile(childId: String, onSuccess: (ChildProfile) -> Unit, onFailure: (Exception) -> Unit) {
        db.collection("childLinks").document(childId).get().addOnSuccessListener { link ->
            val pid = link.getString("parentId") ?: return@addOnSuccessListener
            db.collection("parents").document(pid).collection("children").document(childId).get()
                .addOnSuccessListener { doc -> doc.toObject(ChildProfile::class.java)?.let(onSuccess) ?: onFailure(Exception("Null")) }
        }
    }

    fun listenToChildProfileUpdates(childId: String, onUpdate: (ChildProfile) -> Unit, onError: (Exception) -> Unit) {
        db.collection("childLinks").document(childId).get().addOnSuccessListener { link ->
            val pid = link.getString("parentId") ?: return@addOnSuccessListener
            db.collection("parents").document(pid).collection("children").document(childId)
                .addSnapshotListener { snap, e ->
                    if (e != null) onError(e) else snap?.toObject(ChildProfile::class.java)?.let(onUpdate)
                }
        }
    }
}
