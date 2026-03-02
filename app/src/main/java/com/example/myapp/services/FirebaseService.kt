package com.example.myapp.services

import com.google.firebase.firestore.FirebaseFirestore
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
        db.collection("childLinks").document(childId)
            .get()
            .addOnSuccessListener { linkDoc ->
                if (linkDoc.exists()) {
                    val parentId = linkDoc.getString("parentId")
                    if (parentId != null) {
                        db.collection("parents").document(parentId)
                            .collection("children").document(childId)
                            .collection("screenTime")
                            .document(data.packageName)
                            .set(data)
                    }
                }
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
}
