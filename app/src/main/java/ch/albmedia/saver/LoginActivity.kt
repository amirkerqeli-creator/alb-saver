package ch.albmedia.saver

import android.annotation.SuppressLint
import android.app.Activity
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.os.Message
import android.webkit.CookieManager
import android.webkit.WebChromeClient
import android.webkit.WebResourceRequest
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.Button
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast

class LoginActivity : Activity() {
    private lateinit var web: WebView
    private lateinit var info: TextView
    private var finished = false

    @SuppressLint("SetJavaScriptEnabled")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            fitsSystemWindows = true
        }
        val done = Button(this).apply {
            text = "Fertig – zurück zur App"
            setOnClickListener { close() }
        }
        info = TextView(this).apply {
            setPadding(24, 8, 24, 8)
            text = "Melde dich an. Die App erkennt die Anmeldung automatisch."
        }

        web = WebView(this)
        web.settings.apply {
            javaScriptEnabled = true
            domStorageEnabled = true
            @Suppress("DEPRECATION")
            databaseEnabled = true
            javaScriptCanOpenWindowsAutomatically = true
            setSupportMultipleWindows(true)
            loadWithOverviewMode = true
            useWideViewPort = true
            mixedContentMode = WebSettings.MIXED_CONTENT_NEVER_ALLOW
            // Echten Chrome-Mobile-User-Agent verwenden: "; wv" und "Version/4.0" verraten
            // die WebView, worauf Instagram/Facebook den Login blockieren oder endlos laden.
            userAgentString = userAgentString
                .replace("; wv", "")
                .replace(Regex("Version/\\d+(\\.\\d+)* "), "")
        }

        val cookies = CookieManager.getInstance()
        cookies.setAcceptCookie(true)
        cookies.setAcceptThirdPartyCookies(web, true)

        web.webViewClient = object : WebViewClient() {
            override fun shouldOverrideUrlLoading(view: WebView, request: WebResourceRequest): Boolean =
                handleScheme(view, request.url)

            override fun onPageFinished(view: WebView, url: String?) {
                super.onPageFinished(view, url)
                CookieManager.getInstance().flush()
                checkLoggedIn()
            }
        }
        // "Mit Facebook anmelden" & Co. öffnen ein neues Fenster – im selben WebView laden
        web.webChromeClient = object : WebChromeClient() {
            override fun onCreateWindow(view: WebView, isDialog: Boolean, isUserGesture: Boolean, resultMsg: Message): Boolean {
                val popup = WebView(this@LoginActivity)
                popup.webViewClient = object : WebViewClient() {
                    override fun shouldOverrideUrlLoading(v: WebView, request: WebResourceRequest): Boolean {
                        val uri = request.url
                        if (uri.scheme == "http" || uri.scheme == "https") web.loadUrl(uri.toString())
                        else handleScheme(web, uri)
                        return true
                    }
                }
                (resultMsg.obj as WebView.WebViewTransport).webView = popup
                resultMsg.sendToTarget()
                return true
            }
        }

        root.addView(done, LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT))
        root.addView(info, LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT))
        root.addView(web, LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, 0, 1f))
        setContentView(root)

        web.loadUrl(intent.getStringExtra(EXTRA_URL) ?: "https://www.instagram.com/accounts/login/")
    }

    /** Verhindert "ERR_UNKNOWN_URL_SCHEME" bei intent://, instagram://, fb:// usw. */
    private fun handleScheme(view: WebView, uri: Uri): Boolean {
        val scheme = uri.scheme ?: return false
        if (scheme == "http" || scheme == "https") return false
        if (scheme == "intent") {
            try {
                val i = Intent.parseUri(uri.toString(), Intent.URI_INTENT_SCHEME)
                val fallback = i.getStringExtra("browser_fallback_url")
                if (fallback != null) view.loadUrl(fallback)
            } catch (_: Exception) { }
        }
        // App-Links (instagram://, fb://, market:// …) ignorieren: Login bleibt in der WebView
        return true
    }

    private fun isInstagram() = (intent.getStringExtra(EXTRA_URL) ?: "").contains("instagram")

    private fun checkLoggedIn() {
        if (finished) return
        val ok = if (isInstagram()) Instagram.isLoggedIn() else Facebook.isLoggedIn()
        if (ok) {
            info.text = "✓ Angemeldet!"
            Toast.makeText(this, "✓ Angemeldet", Toast.LENGTH_SHORT).show()
            web.postDelayed({ close() }, 1200)
        }
    }

    private fun close() {
        if (finished) return
        finished = true
        CookieManager.getInstance().flush()
        finish()
    }

    @Deprecated("Deprecated in Java")
    override fun onBackPressed() {
        if (web.canGoBack()) web.goBack() else {
            CookieManager.getInstance().flush()
            @Suppress("DEPRECATION")
            super.onBackPressed()
        }
    }

    override fun onDestroy() {
        web.destroy()
        super.onDestroy()
    }

    companion object {
        const val EXTRA_URL = "url"
    }
}
