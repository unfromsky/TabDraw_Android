package com.unfromsky.tabdraw

import android.annotation.SuppressLint
import android.content.Context
import android.content.SharedPreferences
import android.graphics.Color
import android.os.Build
import android.os.Bundle
import android.view.MotionEvent
import android.view.Surface
import android.view.View
import android.view.WindowInsets
import android.view.WindowInsetsController
import android.view.WindowManager
import android.webkit.WebChromeClient
import android.webkit.WebResourceRequest
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.Button
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity

class MainActivity : AppCompatActivity() {

    private lateinit var webView: WebView
    private lateinit var ipConfigBar: LinearLayout
    private lateinit var etHostUrl: EditText
    private lateinit var btnConnect: Button
    private lateinit var btnCloseBar: Button
    private lateinit var btnSettings: Button

    private lateinit var prefs: SharedPreferences

    companion object {
        private const val PREFS_NAME = "TabDrawPrefs"
        private const val KEY_LAST_URL = "last_url"
        private const val DEFAULT_URL = "http://192.168.0.13:3000/tablet.html"
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        // Keep Screen On while drawing
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)

        setContentView(R.layout.activity_main)

        prefs = getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

        initViews()
        setupFullscreen()
        forceHighRefreshRate()
        setupWebView()

        // Load saved or default URL
        val savedUrl = prefs.getString(KEY_LAST_URL, DEFAULT_URL) ?: DEFAULT_URL
        etHostUrl.setText(savedUrl)
        loadDrawingPad(savedUrl)
    }

    private fun initViews() {
        webView = findViewById(R.id.webView)
        ipConfigBar = findViewById(R.id.ipConfigBar)
        etHostUrl = findViewById(R.id.etHostUrl)
        btnConnect = findViewById(R.id.btnConnect)
        btnCloseBar = findViewById(R.id.btnCloseBar)
        btnSettings = findViewById(R.id.btnSettings)

        btnConnect.setOnClickListener {
            val inputUrl = etHostUrl.text.toString().trim()
            if (inputUrl.isNotEmpty()) {
                val fullUrl = if (!inputUrl.startsWith("http://") && !inputUrl.startsWith("https://")) {
                    "http://$inputUrl"
                } else {
                    inputUrl
                }
                prefs.edit().putString(KEY_LAST_URL, fullUrl).apply()
                loadDrawingPad(fullUrl)
                ipConfigBar.visibility = View.GONE
                hideSystemUI()
            }
        }

        btnCloseBar.setOnClickListener {
            ipConfigBar.visibility = View.GONE
            hideSystemUI()
        }

        btnSettings.setOnClickListener {
            if (ipConfigBar.visibility == View.VISIBLE) {
                ipConfigBar.visibility = View.GONE
                hideSystemUI()
            } else {
                ipConfigBar.visibility = View.VISIBLE
            }
        }
    }

    @SuppressLint("SetJavaScriptEnabled")
    private fun setupWebView() {
        webView.setBackgroundColor(Color.BLACK)
        webView.setLayerType(View.LAYER_TYPE_HARDWARE, null)

        val settings: WebSettings = webView.settings
        settings.javaScriptEnabled = true
        settings.domStorageEnabled = true
        settings.databaseEnabled = true
        settings.allowFileAccess = true
        settings.allowContentAccess = true
        settings.loadWithOverviewMode = true
        settings.useWideViewPort = true
        settings.setSupportZoom(false)
        settings.builtInZoomControls = false
        settings.displayZoomControls = false
        settings.mediaPlaybackRequiresUserGesture = false
        settings.cacheMode = WebSettings.LOAD_DEFAULT

        webView.webViewClient = object : WebViewClient() {
            override fun shouldOverrideUrlLoading(view: WebView?, request: WebResourceRequest?): Boolean {
                return false
            }

            override fun onReceivedError(view: WebView?, errorCode: Int, description: String?, failingUrl: String?) {
                super.onReceivedError(view, errorCode, description, failingUrl)
                Toast.makeText(this@MainActivity, "連線失敗: $description\n請確認電腦伺服器已啟動", Toast.LENGTH_LONG).show()
                ipConfigBar.visibility = View.VISIBLE
            }
        }

        webView.webChromeClient = WebChromeClient()

        // Enable airborne stylus hover events to be received without restriction
        webView.setOnHoverListener { v, event ->
            v.dispatchGenericMotionEvent(event)
        }
    }

    private fun loadDrawingPad(url: String) {
        val target = if (!url.contains("/tablet.html") && !url.contains("/debug_hover.html")) {
            if (url.endsWith("/")) "${url}tablet.html" else "$url/tablet.html"
        } else {
            url
        }
        webView.loadUrl(target)
    }

    /**
     * Force hardware 120Hz high refresh rate to prevent HyperOS 60Hz throttling
     */
    private fun forceHighRefreshRate() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            try {
                window.setFrameRate(120f, Surface.FRAME_RATE_COMPATIBILITY_DEFAULT)
            } catch (e: Exception) {}
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            try {
                val targetDisplay = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                    display
                } else {
                    @Suppress("DEPRECATION")
                    windowManager.defaultDisplay
                }

                val modes = targetDisplay?.supportedModes
                val highHzMode = modes?.filter { it.refreshRate >= 119f }?.maxByOrNull { it.refreshRate }
                if (highHzMode != null) {
                    val params = window.attributes
                    params.preferredDisplayModeId = highHzMode.modeId
                    window.attributes = params
                } else {
                    val params = window.attributes
                    params.preferredRefreshRate = 120f
                    window.attributes = params
                }
            } catch (e: Exception) {}
        }
    }

    /**
     * Setup immersive sticky fullscreen mode
     */
    private fun setupFullscreen() {
        hideSystemUI()
    }

    private fun hideSystemUI() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            window.insetsController?.let { controller ->
                controller.hide(WindowInsets.Type.statusBars() or WindowInsets.Type.navigationBars())
                controller.systemBarsBehavior = WindowInsetsController.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
            }
        } else {
            @Suppress("DEPRECATION")
            window.decorView.systemUiVisibility = (
                View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY
                or View.SYSTEM_UI_FLAG_LAYOUT_STABLE
                or View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION
                or View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
                or View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
                or View.SYSTEM_UI_FLAG_FULLSCREEN
            )
        }
    }

    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)
        if (hasFocus) {
            hideSystemUI()
            forceHighRefreshRate()
        }
    }

    override fun onResume() {
        super.onResume()
        webView.onResume()
        hideSystemUI()
        forceHighRefreshRate()
    }

    override fun onPause() {
        webView.onPause()
        super.onPause()
    }

    override fun onGenericMotionEvent(event: MotionEvent?): Boolean {
        // Forward stylus hover motion directly
        if (event != null && webView.dispatchGenericMotionEvent(event)) {
            return true
        }
        return super.onGenericMotionEvent(event)
    }

    @Deprecated("Deprecated in Java")
    override fun onBackPressed() {
        if (webView.canGoBack()) {
            webView.goBack()
        } else {
            if (ipConfigBar.visibility == View.VISIBLE) {
                ipConfigBar.visibility = View.GONE
                hideSystemUI()
            } else {
                ipConfigBar.visibility = View.VISIBLE
            }
        }
    }
}
