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
        val allowedWebsites: List<String> = emptyList()
    )

    // App info data
    data class AppInfo(
        val packageName: String = "",
        val appName: String = "",
        val versionName: String = "",
        val versionCode: Long = 0L,
        val isSystemApp: Boolean = false
    )

    // Upload child's installed apps to Firestore
    fun uploadInstalledApps(childId: String, apps: List<AppInfo>) {
        db.collection("children").document(childId)
            .collection("installedApps")
            .get()
            .addOnSuccessListener { documents ->
                for (document in documents) {
                    document.reference.delete()
                }
            }
            .addOnCompleteListener {
                apps.forEach { app ->
                    db.collection("children").document(childId)
                        .collection("installedApps")
                        .document(app.packageName)
                        .set(app)
                }
            }
    }

    // Fetch child profile from Firestore
    fun fetchChildProfile(childId: String, onSuccess: (ChildProfile) -> Unit, onFailure: (Exception) -> Unit) {
        db.collection("children").document(childId)
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

    // Listener for real-time updates to child profile
    fun listenToChildProfileUpdates(childId: String, onUpdate: (ChildProfile) -> Unit, onError: (Exception) -> Unit) {
        db.collection("children").document(childId)
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
}
