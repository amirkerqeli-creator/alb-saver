package ch.albmedia.saver

import android.app.Activity
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.view.View
import android.view.inputmethod.EditorInfo
import android.webkit.CookieManager
import android.widget.EditText
import android.widget.ProgressBar
import android.widget.TextView
import android.widget.Toast

class MainActivity : Activity() {
    private lateinit var urlInput: EditText
    private lateinit var status: TextView
    private lateinit var progress: ProgressBar
    private lateinit var downloadBtn: TextView
    private var busy = false

    /** true, wenn die App über "Teilen" geöffnet wurde → danach zurück zur Quell-App */
    private var fromShare = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        urlInput = findViewById(R.id.urlInput)
        status = findViewById(R.id.status)
        progress = findViewById(R.id.progress)
        downloadBtn = findViewById(R.id.downloadBtn)

        findViewById<View>(R.id.openIgBtn).setOnClickListener {
            openApp(listOf("com.instagram.android", "com.instagram.lite"), "https://www.instagram.com/")
        }
        findViewById<View>(R.id.openFbBtn).setOnClickListener {
            openApp(listOf("com.facebook.katana", "com.facebook.lite"), "https://www.facebook.com/")
        }
        findViewById<View>(R.id.pasteBtn).setOnClickListener {
            val link = clipboardLink()
            if (link != null) urlInput.setText(link) else showStatus("Kein Instagram-/Facebook-Link in der Zwischenablage.")
        }
        downloadBtn.setOnClickListener { startDownload(urlInput.text.toString()) }
        urlInput.setOnEditorActionListener { _, actionId, _ ->
            if (actionId == EditorInfo.IME_ACTION_GO) { startDownload(urlInput.text.toString()); true } else false
        }
        findViewById<View>(R.id.igLoginBtn).setOnClickListener {
            if (Instagram.isLoggedIn()) logout("https://www.instagram.com", ".instagram.com")
            else openLogin("https://www.instagram.com/accounts/login/")
        }
        findViewById<View>(R.id.fbLoginBtn).setOnClickListener {
            if (Facebook.isLoggedIn()) logout("https://www.facebook.com", ".facebook.com")
            else openLogin("https://www.facebook.com/login/")
        }

        handleShare(intent)
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        handleShare(intent)
    }

    override fun onResume() {
        super.onResume()
        updateLoginState()
    }

    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)
        // Android erlaubt Zugriff auf die Zwischenablage erst, wenn die App den Fokus hat
        if (hasFocus && !fromShare && urlInput.text.isBlank()) clipboardLink()?.let { urlInput.setText(it) }
    }

    @Deprecated("Deprecated in Java")
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        @Suppress("DEPRECATION")
        super.onActivityResult(requestCode, resultCode, data)
        // Nach dem Download über "Teilen": App schliessen → man ist wieder genau dort in Instagram/Facebook
        if (requestCode == REQ_PICK && resultCode == RESULT_OK && fromShare) finish()
    }

    private fun handleShare(intent: Intent?) {
        if (intent?.action != Intent.ACTION_SEND) return
        fromShare = true
        val text = intent.getStringExtra(Intent.EXTRA_TEXT) ?: return
        val link = findLink(text) ?: run { showStatus("Im geteilten Text ist kein Link."); return }
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

    /** Öffnet die installierte App (bringt sie zurück, wo man war), sonst die Webseite. */
    private fun openApp(packages: List<String>, webUrl: String) {
        for (p in packages) {
            val launch = packageManager.getLaunchIntentForPackage(p) ?: continue
            startActivity(launch)
            return
        }
        try {
            startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(webUrl)))
        } catch (_: Exception) {
            Toast.makeText(this, "App nicht gefunden", Toast.LENGTH_SHORT).show()
        }
    }

    private fun openLogin(url: String) {
        startActivity(Intent(this, LoginActivity::class.java).putExtra(LoginActivity.EXTRA_URL, url))
    }

    /** Meldet nur bei einem Dienst ab, indem dessen Cookies ablaufen. */
    private fun logout(base: String, domain: String) {
        val cm = CookieManager.getInstance()
        val names = (cm.getCookie(base) ?: "").split(";").map { it.substringBefore("=").trim() }.filter { it.isNotEmpty() }
        for (n in names) {
            cm.setCookie(base, "$n=; Max-Age=0; Path=/; Domain=$domain")
            cm.setCookie(base, "$n=; Max-Age=0; Path=/")
        }
        cm.flush()
        urlInput.postDelayed({ updateLoginState() }, 300)
    }

    private fun updateLoginState() {
        val ig = Instagram.isLoggedIn()
        val fb = Facebook.isLoggedIn()
        findViewById<View>(R.id.igDot).setBackgroundResource(if (ig) R.drawable.dot_on else R.drawable.dot_off)
        findViewById<View>(R.id.fbDot).setBackgroundResource(if (fb) R.drawable.dot_on else R.drawable.dot_off)
        findViewById<TextView>(R.id.igState).text = if (ig) "Angemeldet" else "Nicht angemeldet"
        findViewById<TextView>(R.id.fbState).text = if (fb) "Angemeldet" else "Optional – öffentliche Videos gehen auch so"
        findViewById<TextView>(R.id.igLoginBtn).text = if (ig) "Abmelden" else "Anmelden"
        findViewById<TextView>(R.id.fbLoginBtn).text = if (fb) "Abmelden" else "Anmelden"
    }

    private fun showStatus(text: String) {
        status.text = text
        status.visibility = if (text.isEmpty()) View.GONE else View.VISIBLE
    }

    private fun startDownload(raw: String) {
        if (busy) return
        val url = findLink(raw.trim()) ?: run { showStatus("Bitte einen gültigen Link einfügen."); return }
        busy = true
        downloadBtn.isEnabled = false
        downloadBtn.text = "Suche Medien …"
        progress.visibility = View.VISIBLE
        showStatus("")

        Thread {
            var items: List<Media> = emptyList()
            val error = try {
                items = Saver.extract(url)
                null
            } catch (e: ExtractException) {
                "✗ ${e.message}"
            } catch (e: Exception) {
                "✗ Fehler: ${e.javaClass.simpleName} ${e.message ?: ""}"
            }
            runOnUiThread {
                progress.visibility = View.GONE
                downloadBtn.isEnabled = true
                downloadBtn.text = "Herunterladen"
                busy = false
                if (error != null) {
                    showStatus(error)
                } else {
                    urlInput.setText("")
                    PickerActivity.pending = items
                    @Suppress("DEPRECATION")
                    startActivityForResult(Intent(this, PickerActivity::class.java), REQ_PICK)
                }
            }
        }.start()
    }

    companion object {
        private const val REQ_PICK = 1
    }
}
