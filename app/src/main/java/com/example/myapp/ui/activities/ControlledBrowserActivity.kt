package com.example.myapp.ui.activities

import android.annotation.SuppressLint
import android.app.Activity
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.util.Log
import android.view.View
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.Button
import android.widget.EditText
import android.widget.ProgressBar
import android.widget.Toast
import com.example.myapp.R
import com.example.myapp.services.FirebaseService

class ControlledBrowserActivity : Activity() {
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
            override fun shouldOverrideUrlLoading(view: WebView, url: String): Boolean {
                return if (isBlockedUrl(url)) {
                    showBlockedMessage()
                    true
                } else {
                    view.loadUrl(url)
                    false
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
            loadUrl("https://www.google.com")
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
        val host = Uri.parse(url).host?.lowercase() ?: return false
        return blockedWebsites.any { blockedSite ->
            host.contains(blockedSite.lowercase())
        }
    }

    private fun showBlockedMessage() {
        Toast.makeText(this, "This website is blocked by your parent", Toast.LENGTH_LONG).show()
        webView.loadUrl("about:blank")
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
}
