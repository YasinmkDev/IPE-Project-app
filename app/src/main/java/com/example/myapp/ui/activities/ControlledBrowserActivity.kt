package com.example.myapp.ui.activities

import android.annotation.SuppressLint
import android.app.Activity
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.util.Log
import android.view.View
import android.webkit.WebView
import android.webkit.WebResourceRequest
import android.webkit.WebViewClient
import android.widget.Button
import android.widget.EditText
import android.widget.ProgressBar
import com.example.myapp.R
import com.example.myapp.services.FirebaseService
import java.net.URI

class ControlledBrowserActivity : Activity() {
    private val safeHomeUrl = "https://www.google.com"
    private lateinit var webView: WebView
    private lateinit var editTextUrl: EditText
    private lateinit var buttonGo: Button
    private lateinit var buttonBack: Button
    private lateinit var buttonForward: Button
    private lateinit var progressBar: ProgressBar
    private var blockedWebsites: List<String> = emptyList()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_controlled_browser)

        webView = findViewById(R.id.webView)
        editTextUrl = findViewById(R.id.editTextUrl)
        buttonGo = findViewById(R.id.buttonGo)
        buttonBack = findViewById(R.id.buttonBack)
        buttonForward = findViewById(R.id.buttonForward)
        progressBar = findViewById(R.id.progressBar)

        // Configure WebView
        configureWebView()

        // Handle intent data
        handleIntent(intent)

        // Set up button listeners
        buttonGo.setOnClickListener { loadUrl(editTextUrl.text.toString()) }
        buttonBack.setOnClickListener { if (webView.canGoBack()) webView.goBack() }
        buttonForward.setOnClickListener { if (webView.canGoForward()) webView.goForward() }

        // Load blocked websites list from Firestore
        loadBlockedWebsites()
    }

    @SuppressLint("SetJavaScriptEnabled")
    private fun configureWebView() {
        val webSettings = webView.settings
        webSettings.javaScriptEnabled = true
        webSettings.domStorageEnabled = true
        webSettings.allowContentAccess = true
        webSettings.allowFileAccess = true
        webSettings.useWideViewPort = true
        webSettings.loadWithOverviewMode = true

        webView.webViewClient = object : WebViewClient() {
            @Deprecated("Deprecated in Java")
            override fun shouldOverrideUrlLoading(view: WebView, url: String): Boolean {
                return if (isBlockedUrl(url)) {
                    showBlockedMessage()
                    true
                } else {
                    false
                }
            }

            override fun shouldOverrideUrlLoading(view: WebView, request: WebResourceRequest): Boolean {
                val requestUrl = request.url?.toString() ?: return false
                return if (isBlockedUrl(requestUrl)) {
                    showBlockedMessage()
                    true
                } else {
                    false
                }
            }

            override fun onPageStarted(view: WebView, url: String, favicon: android.graphics.Bitmap?) {
                super.onPageStarted(view, url, favicon)
                if (isBlockedUrl(url)) {
                    view.stopLoading()
                    showBlockedMessage()
                }
            }

            override fun onPageFinished(view: WebView, url: String) {
                super.onPageFinished(view, url)
                editTextUrl.setText(url)
                progressBar.visibility = View.GONE
            }
        }
    }

    private fun handleIntent(intent: Intent) {
        if (Intent.ACTION_VIEW == intent.action) {
            val uri = intent.data
            uri?.let {
                loadUrl(uri.toString())
            }
        } else {
            // Default to a safe website
            loadUrl(safeHomeUrl)
        }
    }

    private fun loadUrl(url: String) {
        var formattedUrl = url.trim()
        if (!formattedUrl.startsWith("http://") && !formattedUrl.startsWith("https://")) {
            formattedUrl = "https://$formattedUrl"
        }

        if (isBlockedUrl(formattedUrl)) {
            showBlockedMessage()
        } else {
            progressBar.visibility = View.VISIBLE
            webView.loadUrl(formattedUrl)
        }
    }

    private fun isBlockedUrl(url: String): Boolean {
        val host = extractNormalizedHost(url) ?: return false
        return blockedWebsites.any { blockedSite ->
            val normalizedBlocked = extractNormalizedHost(blockedSite) ?: blockedSite.trim().lowercase().removePrefix("www.")
            normalizedBlocked.isNotEmpty() && (host == normalizedBlocked || host.endsWith(".$normalizedBlocked"))
        }
    }

    private fun showBlockedMessage() {
        progressBar.visibility = View.VISIBLE
        webView.stopLoading()
        webView.loadUrl(safeHomeUrl)
    }

    private fun loadBlockedWebsites() {
        val sharedPreferences = getSharedPreferences("APP_PREFS", MODE_PRIVATE)
        val childId = sharedPreferences.getString("CHILD_ID", "") ?: ""
        if (childId.isNotEmpty()) {
            FirebaseService.fetchChildProfile(
                childId,
                onSuccess = { profile ->
                    blockedWebsites = profile.blockedWebsites
                    Log.d(TAG, "Loaded blocked websites: $blockedWebsites")
                },
                onFailure = { exception ->
                    Log.e(TAG, "Error fetching blocked websites: ${exception.message}")
                }
            )
        }
    }

    companion object {
        private const val TAG = "ControlledBrowserActivity"
    }

    private fun extractNormalizedHost(raw: String): String? {
        val input = raw.trim()
        if (input.isEmpty()) return null
        return try {
            val withScheme = if (input.startsWith("http://") || input.startsWith("https://")) input else "https://$input"
            val host = URI(withScheme).host ?: return null
            host.lowercase().removePrefix("www.")
        } catch (_: Exception) {
            null
        }
    }
}
