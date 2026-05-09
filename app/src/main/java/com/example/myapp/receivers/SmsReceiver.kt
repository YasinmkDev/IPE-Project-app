package com.example.myapp.receivers

import android.Manifest
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.provider.ContactsContract
import android.provider.Telephony
import android.util.Log
import com.example.myapp.services.FirebaseService
import com.example.myapp.utils.ProtectedStorageUtil

class SmsReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action == Telephony.Sms.Intents.SMS_RECEIVED_ACTION) {
            val messages = Telephony.Sms.Intents.getMessagesFromIntent(intent)
            val childId = ProtectedStorageUtil.getStoredChildId(context) ?: return

            for (sms in messages) {
                val address = sms.displayOriginatingAddress ?: "Unknown"
                val body = sms.displayMessageBody ?: ""
                val contactName = getContactName(context, address)
                val displayName = if (contactName != null) "$contactName ($address)" else address
                
                // Detection logic for Objective #4
                val isSuspicious = checkForSuspiciousKeywords(body)

                val event = FirebaseService.ActivityEvent(
                    type = "SMS",
                    severity = if (isSuspicious) "high" else "low",
                    title = if (isSuspicious) "Suspicious Text from $displayName" else "Text Message from $displayName",
                    details = body,
                    timestamp = System.currentTimeMillis()
                )

                FirebaseService.logEvent(childId, event)
            }
        }
    }

    private fun getContactName(context: Context, phoneNumber: String): String? {
        if (context.checkSelfPermission(Manifest.permission.READ_CONTACTS) != PackageManager.PERMISSION_GRANTED) {
            return null
        }
        val uri = Uri.withAppendedPath(ContactsContract.PhoneLookup.CONTENT_FILTER_URI, Uri.encode(phoneNumber))
        val projection = arrayOf(ContactsContract.PhoneLookup.DISPLAY_NAME)
        return try {
            context.contentResolver.query(uri, projection, null, null, null)?.use { cursor ->
                if (cursor.moveToFirst()) {
                    cursor.getString(0)
                } else null
            }
        } catch (e: Exception) {
            null
        }
    }

    private fun checkForSuspiciousKeywords(text: String): Boolean {
        val dangerWords = listOf("hate", "kill", "drugs", "meet", "secret", "don't tell", "password", "otp")
        return dangerWords.any { text.lowercase().contains(it) }
    }
}
