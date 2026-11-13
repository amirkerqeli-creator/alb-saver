package ch.albmedia.saver

import android.app.Activity
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.view.View
import android.view.inputmethod.EditorInfo
import android.webkit.CookieManager
import android.widget.Button
import android.widget.EditText
import android.widget.ProgressBar
import android.widget.TextView

class MainActivity : Activity() {
    private lateinit var urlInput: EditText
    private lateinit var status: TextView
    private lateinit var progress: ProgressBar
    private lateinit var downloadBtn: Button
    private lateinit var loginState: TextView
    private var busy = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        urlInput = findViewById(R.id.urlInput)
        status = findViewById(R.id.status)
        progress = findViewById(R.id.progress)
        downloadBtn = findViewById(R.id.downloadBtn)
        loginState = findViewById(R.id.loginState)

        findViewById<Button>(R.id.pasteBtn).setOnClickListener {
            val link = clipboardLink()
            if (link != null) urlInput.setText(link) else status.text = "Kein Link in der Zwischenablage."
        }
        downloadBtn.setOnClickListener { startDownload(urlInput.text.toString()) }
        urlInput.setOnEditorActionListener { _, actionId, _ ->
            if (actionId == EditorInfo.IME_ACTION_GO) { startDownload(urlInput.text.toString()); true } else false
        }
        findViewById<Button>(R.id.igLoginBtn).setOnClickListener {
            openLogin("https://www.instagram.com/accounts/login/")
        }
        findViewById<Button>(R.id.fbLoginBtn).setOnClickListener {
            openLogin("https://www.facebook.com/login/")
        }
        findViewById<Button>(R.id.logoutBtn).setOnClickListener {
            CookieManager.getInstance().removeAllCookies { updateLoginState() }
            CookieManager.getInstance().flush()
        }

        handleShare(intent)
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        handleShare(intent)
    }

    override fun onResume() {
        super.onResume()
        updateLoginState()
    }

    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)
        // Android erlaubt Zugriff auf die Zwischenablage erst, wenn die App den Fokus hat
        if (hasFocus && urlInput.text.isBlank()) clipboardLink()?.let { urlInput.setText(it) }
    }

    private fun handleShare(intent: Intent?) {
        if (intent?.action != Intent.ACTION_SEND) return
        val text = intent.getStringExtra(Intent.EXTRA_TEXT) ?: return
        val link = findLink(text) ?: run { status.text = "Im geteilten Text ist kein Link."; return }
        urlInput.setText(link)
        startDownload(link)
    }

    private fun findLink(text: String): String? =
        Regex("""https?://\S+""").find(text)?.value?.trimEnd('.', ',', ')', '"')

    private fun clipboardLink(): String? {
        val cm = getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        val clip = cm.primaryClip ?: return null
        if (clip.itemCount == 0) return null
        val text = clip.getItemAt(0).coerceToText(this)?.toString() ?: return null
        val link = findLink(text) ?: return null
        return if (link.contains("instagram") || link.contains("facebook") || link.contains("fb.watch")) link else null
    }

    private fun openLogin(url: String) {
        startActivity(Intent(this, LoginActivity::class.java).putExtra(LoginActivity.EXTRA_URL, url))
    }

    private fun updateLoginState() {
        val ig = if (Instagram.isLoggedIn()) "✓ angemeldet" else "✗ nicht angemeldet"
        val fb = if (Facebook.isLoggedIn()) "✓ angemeldet" else "✗ nicht angemeldet (öffentliche Videos gehen oft trotzdem)"
        loginState.text = "Instagram: $ig\nFacebook: $fb"
    }

    private fun startDownload(raw: String) {
        if (busy) return
        val url = findLink(raw.trim()) ?: run { status.text = "Bitte einen gültigen Link einfügen."; return }
        busy = true
        downloadBtn.isEnabled = false
        progress.visibility = View.VISIBLE
        status.text = "Suche Medien …"

        Thread {
            val result = try {
                val items = Saver.extract(url)
                Saver.enqueue(applicationContext, items)
                val videos = items.count { it.ext == "mp4" }
                val photos = items.size - videos
                val parts = listOfNotNull(
                    if (videos > 0) "$videos Video${if (videos > 1) "s" else ""}" else null,
                    if (photos > 0) "$photos Foto${if (photos > 1) "s" else ""}" else null
                )
                "✓ ${parts.joinToString(" und ")} werden gespeichert (siehe Benachrichtigung / Galerie)."
            } catch (e: ExtractException) {
                "✗ ${e.message}"
            } catch (e: Exception) {
                "✗ Fehler: ${e.javaClass.simpleName} ${e.message ?: ""}"
            }
            runOnUiThread {
                status.text = result
                progress.visibility = View.GONE
                downloadBtn.isEnabled = true
                busy = false
                if (result.startsWith("✓")) urlInput.setText("")
            }
        }.start()
    }
}
