package com.example.myapp.services

import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.ktx.firestore
import com.google.firebase.ktx.Firebase

object FirebaseService {
    private val db: FirebaseFirestore = Firebase.firestore

    // Child profile data
    data class ChildProfile(
        val childId: String = "",
        val parentId: String = "",
        val blockedApps: List<String> = emptyList(),
        val blockedWebsites: List<String> = emptyList(),
        val allowedApps: List<String> = emptyList(),
        val allowedWebsites: List<String> = emptyList(),
        val storageRestricted: Boolean = false
    )

    // App info data
    data class AppInfo(
        val packageName: String = "",
        val name: String = "",
        val versionName: String = "",
        val versionCode: Long = 0L,
        val isSystemApp: Boolean = false
    )

    // Resolve pairing code to childId
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
                        onFailure(Exception("Child ID or Parent ID not found for pairing code"))
                    }
                } else {
                    onFailure(Exception("Pairing code not found"))
                }
            }
            .addOnFailureListener(onFailure)
    }

    // Update linkedAt timestamp when device is linked
    fun markDeviceAsLinked(childId: String, parentId: String, onSuccess: () -> Unit, onFailure: (Exception) -> Unit) {
        val childRef = db.collection("parents").document(parentId)
            .collection("children").document(childId)
        
        childRef.update("linkedAt", com.google.firebase.Timestamp.now())
            .addOnSuccessListener {
                onSuccess()
            }
            .addOnFailureListener(onFailure)
    }

    // Upload child's installed apps to Firestore
    fun uploadInstalledApps(childId: String, apps: List<AppInfo>) {
        // First, get parentId from childLinks
        db.collection("childLinks").document(childId)
            .get()
            .addOnSuccessListener { linkDoc ->
                if (linkDoc.exists()) {
                    val parentId = linkDoc.getString("parentId")
                    if (parentId != null) {
                        // Clear existing installed apps
                        db.collection("parents").document(parentId)
                            .collection("children").document(childId)
                            .collection("installedApps")
                            .get()
                            .addOnSuccessListener { documents ->
                                for (document in documents) {
                                    document.reference.delete()
                                }
                            }
                            .addOnCompleteListener {
                                // Upload new apps
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
            .addOnFailureListener { exception ->
                println("Error fetching parentId: ${exception.message}")
            }
    }

    // Fetch child profile from Firestore
    fun fetchChildProfile(childId: String, onSuccess: (ChildProfile) -> Unit, onFailure: (Exception) -> Unit) {
        // First, get parentId from childLinks
        db.collection("childLinks").document(childId)
            .get()
            .addOnSuccessListener { linkDoc ->
                if (linkDoc.exists()) {
                    val parentId = linkDoc.getString("parentId")
                    if (parentId != null) {
                        // Then, fetch child profile from parents/{parentId}/children/{childId}
                        db.collection("parents").document(parentId)
                            .collection("children").document(childId)
                            .get()
                            .addOnSuccessListener { document ->
                                if (document.exists()) {
                                    val profile = document.toObject(ChildProfile::class.java)
                                    profile?.let(onSuccess)
                                } else {
                                    onFailure(Exception("Child profile not found"))
                                }
                            }
                            .addOnFailureListener(onFailure)
                    }
                } else {
                    onFailure(Exception("Child link not found"))
                }
            }
            .addOnFailureListener(onFailure)
    }

    // Listener for real-time updates to child profile
    fun listenToChildProfileUpdates(childId: String, onUpdate: (ChildProfile) -> Unit, onError: (Exception) -> Unit) {
        // First, get parentId from childLinks
        db.collection("childLinks").document(childId)
            .get()
            .addOnSuccessListener { linkDoc ->
                if (linkDoc.exists()) {
                    val parentId = linkDoc.getString("parentId")
                    if (parentId != null) {
                        // Then, listen to child profile updates from parents/{parentId}/children/{childId}
                        db.collection("parents").document(parentId)
                            .collection("children").document(childId)
                            .addSnapshotListener { documentSnapshot, firebaseFirestoreException ->
                                firebaseFirestoreException?.let(onError)
                                documentSnapshot?.let {
                                    if (it.exists()) {
                                        val profile = it.toObject(ChildProfile::class.java)
                                        profile?.let(onUpdate)
                                    }
                                }
                            }
                    }
                } else {
                    onError(Exception("Child link not found"))
                }
            }
            .addOnFailureListener(onError)
    }
}
