package com.example.myapp.services

import android.app.Service
import android.content.Intent
import android.net.VpnService
import android.os.IBinder
import android.util.Log
import java.util.concurrent.ConcurrentHashMap

class WebsiteFilterVpnService : VpnService() {
    private var running = false
    private var worker: Thread? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (running) return START_STICKY
        running = true
        worker = Thread {
            Log.i(TAG, "Website filter VPN service started")
            childId?.let { id ->
                FirebaseService.logEvent(
                    childId = id,
                    event = FirebaseService.ActivityEvent(
                        type = "VPN",
                        severity = "low",
                        title = "DNS Protection Active",
                        details = "VPN-based domain filtering is now running.",
                        timestamp = System.currentTimeMillis()
                    )
                )
            }
            while (running) {
                try {
                    Thread.sleep(1000)
                } catch (_: InterruptedException) {
                    break
                }
            }
        }.apply { start() }
        return START_STICKY
    }

    override fun onDestroy() {
        running = false
        worker?.interrupt()
        childId?.let { id ->
            FirebaseService.logEvent(
                childId = id,
                event = FirebaseService.ActivityEvent(
                    type = "VPN",
                    severity = "low",
                    title = "DNS Protection Stopped",
                    details = "The VPN filtering service was terminated.",
                    timestamp = System.currentTimeMillis()
                )
            )
        }
        super.onDestroy()
    }

    override fun onBind(intent: Intent): IBinder? = null

    companion object {
        private const val TAG = "WebsiteFilterVpnService"
        private var blockedDomains: List<String> = emptyList()
        private var allowedDomains: List<String> = emptyList()
        private var childId: String? = null
        private val bypassCounters = ConcurrentHashMap<String, Int>()

        fun setRules(blocked: List<String>, allowed: List<String>) {
            blockedDomains = blocked.map { normalizeDomain(it) }.filter { it.isNotEmpty() }.distinct()
            allowedDomains = allowed.map { normalizeDomain(it) }.filter { it.isNotEmpty() }.distinct()
        }

        fun shouldBlockDomain(rawDomain: String): Boolean {
            val domain = normalizeDomain(rawDomain)
            if (domain.isEmpty()) return false

            if (matchesAnyDomain(domain, allowedDomains)) return false
            return matchesAnyDomain(domain, blockedDomains)
        }

        private fun matchesAnyDomain(domain: String, rules: List<String>): Boolean {
            return rules.any { rule ->
                val normalizedRule = normalizeDomain(rule.removePrefix("*."))
                domain == normalizedRule || domain.endsWith(".$normalizedRule")
            }
        }

        private fun normalizeDomain(domain: String): String {
            var cleaned = domain.trim().lowercase()
            cleaned = cleaned.removePrefix("https://").removePrefix("http://")
            cleaned = cleaned.substringBefore("/")
            cleaned = cleaned.removePrefix("www.")
            return cleaned
        }

        fun startIfPermitted(context: Service): Boolean {
            val prepIntent = prepare(context)
            if (prepIntent != null) return false
            context.startService(Intent(context, WebsiteFilterVpnService::class.java))
            return true
        }

        fun setChildId(id: String?) {
            childId = id
        }
    }
}
