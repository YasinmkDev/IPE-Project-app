package com.example.myapp.receivers

import android.Manifest
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.provider.ContactsContract
import android.telephony.TelephonyManager
import android.util.Log
import com.example.myapp.services.FirebaseService
import com.example.myapp.utils.ProtectedStorageUtil

class CallReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val childId = ProtectedStorageUtil.getStoredChildId(context) ?: return
        
        if (intent.action == Intent.ACTION_NEW_OUTGOING_CALL) {
            val phoneNumber = intent.getStringExtra(Intent.EXTRA_PHONE_NUMBER)
            logCall(context, childId, "Outgoing", phoneNumber ?: "Unknown")
        } else {
            val state = intent.getStringExtra(TelephonyManager.EXTRA_STATE)
            // Note: EXTRA_INCOMING_NUMBER is only available for READ_CALL_LOG or READ_PHONE_STATE permission
            val phoneNumber = intent.getStringExtra(TelephonyManager.EXTRA_INCOMING_NUMBER)
            
            if (state == TelephonyManager.EXTRA_STATE_RINGING) {
                logCall(context, childId, "Incoming", phoneNumber ?: "Unknown")
            }
        }
    }

    private fun logCall(context: Context, childId: String, type: String, number: String) {
        val contactName = getContactName(context, number)
        val displayName = if (contactName != null) "$contactName ($number)" else number

        Log.d("CallReceiver", "$type call: $number")
        
        val event = FirebaseService.ActivityEvent(
            type = "CALL",
            severity = "low",
            title = "$type Call",
            details = "$type call detected from $displayName",
            timestamp = System.currentTimeMillis()
        )
        FirebaseService.logEvent(childId, event)
    }

    private fun getContactName(context: Context, phoneNumber: String): String? {
        if (context.checkSelfPermission(Manifest.permission.READ_CONTACTS) != PackageManager.PERMISSION_GRANTED) {
            return null
        }
        if (phoneNumber == "Unknown" || phoneNumber.isEmpty()) return null
        
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
}
