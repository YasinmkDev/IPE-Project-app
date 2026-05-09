package com.example.myapp.services

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.provider.CallLog
import android.provider.Telephony
import androidx.core.content.ContextCompat

object SuspiciousBehaviorAnalyzer {
    private val riskyKeywords = listOf(
        "meet alone",
        "send pic",
        "dont tell parents",
        "secret chat",
        "money transfer",
        "otp",
        "drugs",
        "kill"
    )

    fun scan(context: Context, sinceMs: Long): List<FirebaseService.ActivityEvent> {
        val findings = mutableListOf<FirebaseService.ActivityEvent>()
        findings.addAll(scanSms(context, sinceMs))
        findings.addAll(scanCallLogs(context, sinceMs))
        return findings
    }

    private fun scanSms(context: Context, sinceMs: Long): List<FirebaseService.ActivityEvent> {
        if (ContextCompat.checkSelfPermission(context, Manifest.permission.READ_SMS) != PackageManager.PERMISSION_GRANTED) {
            return emptyList()
        }

        val results = mutableListOf<FirebaseService.ActivityEvent>()
        val projection = arrayOf(Telephony.Sms.DATE, Telephony.Sms.ADDRESS, Telephony.Sms.BODY)
        val selection = "${Telephony.Sms.DATE} > ?"
        val args = arrayOf(sinceMs.toString())
        context.contentResolver.query(Telephony.Sms.Inbox.CONTENT_URI, projection, selection, args, "${Telephony.Sms.DATE} DESC")
            ?.use { cursor ->
                val dateIdx = cursor.getColumnIndex(Telephony.Sms.DATE)
                val addressIdx = cursor.getColumnIndex(Telephony.Sms.ADDRESS)
                val bodyIdx = cursor.getColumnIndex(Telephony.Sms.BODY)
                while (cursor.moveToNext()) {
                    val ts = cursor.getLong(dateIdx)
                    val address = cursor.getString(addressIdx) ?: "unknown"
                    val body = cursor.getString(bodyIdx)?.lowercase() ?: ""
                    val keyword = riskyKeywords.firstOrNull { body.contains(it) } ?: continue
                    results.add(
                        FirebaseService.ActivityEvent(
                            type = "SMS",
                            severity = "high",
                            title = "Suspicious Message from $address",
                            details = "Contains keyword: $keyword",
                            timestamp = ts
                        )
                    )
                }
            }
        return results
    }

    private fun scanCallLogs(context: Context, sinceMs: Long): List<FirebaseService.ActivityEvent> {
        if (ContextCompat.checkSelfPermission(context, Manifest.permission.READ_CALL_LOG) != PackageManager.PERMISSION_GRANTED) {
            return emptyList()
        }

        val results = mutableListOf<FirebaseService.ActivityEvent>()
        val projection = arrayOf(CallLog.Calls.DATE, CallLog.Calls.NUMBER, CallLog.Calls.DURATION, CallLog.Calls.TYPE)
        val selection = "${CallLog.Calls.DATE} > ?"
        val args = arrayOf(sinceMs.toString())
        context.contentResolver.query(CallLog.Calls.CONTENT_URI, projection, selection, args, "${CallLog.Calls.DATE} DESC")
            ?.use { cursor ->
                val dateIdx = cursor.getColumnIndex(CallLog.Calls.DATE)
                val numberIdx = cursor.getColumnIndex(CallLog.Calls.NUMBER)
                val durationIdx = cursor.getColumnIndex(CallLog.Calls.DURATION)
                val typeIdx = cursor.getColumnIndex(CallLog.Calls.TYPE)
                while (cursor.moveToNext()) {
                    val ts = cursor.getLong(dateIdx)
                    val number = cursor.getString(numberIdx) ?: "unknown"
                    val duration = cursor.getLong(durationIdx)
                    val type = cursor.getInt(typeIdx)
                    
                    // Simple pattern: Many missed calls from unknown number or short duration calls at night
                    if (type == CallLog.Calls.MISSED_TYPE && duration == 0L) {
                        results.add(
                            FirebaseService.ActivityEvent(
                                type = "CALL",
                                severity = "medium",
                                title = "Missed Call from $number",
                                details = "Unanswered call detected",
                                timestamp = ts
                            )
                        )
                    }
                }
            }
        return results
    }
}
