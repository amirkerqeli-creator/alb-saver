package ch.albmedia.saver

import android.app.Activity
import android.app.AlertDialog
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.inputmethod.EditorInfo
import android.view.inputmethod.InputMethodManager
import android.webkit.CookieManager
import android.widget.EditText
import android.widget.ImageView
import android.widget.PopupMenu
import android.widget.ProgressBar
import android.widget.TextView
import android.widget.Toast
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.RecyclerView
import coil.load

class MainActivity : Activity() {
    private lateinit var list: RecyclerView
    private lateinit var header: View
    private lateinit var urlInput: EditText
    private lateinit var status: TextView
    private lateinit var progress: ProgressBar
    private lateinit var downloadBtn: ImageView
    private val adapter = HomeAdapter()
    private var busy = false
    private var fromShare = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        header = LayoutInflater.from(this).inflate(R.layout.header_home, null, false)
        urlInput = header.findViewById(R.id.urlInput)
        status = header.findViewById(R.id.status)
        progress = header.findViewById(R.id.progress)
        downloadBtn = header.findViewById(R.id.downloadBtn)

        header.findViewById<View>(R.id.openIgBtn).setOnClickListener {
            openApp(listOf("com.instagram.android", "com.instagram.lite"), "https://www.instagram.com/")
        }
        header.findViewById<View>(R.id.menuBtn).setOnClickListener { showMainMenu(it) }
        header.findViewById<View>(R.id.pasteBtn).setOnClickListener {
            val link = clipboardLink()
            if (link != null) urlInput.setText(link) else showStatus("Kein Instagram-/Facebook-Link in der Zwischenablage.")
        }
        header.findViewById<View>(R.id.bannerLoginBtn).setOnClickListener {
            openLogin("https://www.instagram.com/accounts/login/")
        }
        downloadBtn.setOnClickListener { startDownload(urlInput.text.toString()) }
        urlInput.setOnEditorActionListener { _, actionId, _ ->
            if (actionId == EditorInfo.IME_ACTION_GO) { startDownload(urlInput.text.toString()); true } else false
        }

        list = findViewById(R.id.list)
        val glm = GridLayoutManager(this, 2)
        glm.spanSizeLookup = object : GridLayoutManager.SpanSizeLookup() {
            override fun getSpanSize(position: Int) = if (position <= 1) 2 else 1
        }
        list.layoutManager = glm
        list.adapter = adapter

        handleShare(intent)
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        handleShare(intent)
    }

    override fun onResume() {
        super.onResume()
        header.findViewById<View>(R.id.loginBanner).visibility =
            if (Instagram.isLoggedIn()) View.GONE else View.VISIBLE
        adapter.reload()
    }

    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)
        if (hasFocus && !fromShare && urlInput.text.isBlank()) clipboardLink()?.let { urlInput.setText(it) }
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

    private fun showMainMenu(anchor: View) {
        val m = PopupMenu(this, anchor)
        m.menu.add(0, 1, 0, "Facebook öffnen")
        m.menu.add(0, 2, 1, "Konten")
        m.menu.add(0, 3, 2, "Verlauf leeren")
        m.setOnMenuItemClickListener {
            when (it.itemId) {
                1 -> openApp(listOf("com.facebook.katana", "com.facebook.lite"), "https://www.facebook.com/")
                2 -> showAccounts()
                3 -> AlertDialog.Builder(this)
                    .setMessage("Verlauf leeren? Die Dateien in der Galerie bleiben erhalten.")
                    .setPositiveButton("Leeren") { _, _ -> History.clear(this); adapter.reload() }
                    .setNegativeButton("Abbrechen", null)
                    .show()
            }
            true
        }
        m.show()
    }

    private fun showAccounts() {
        val v = LayoutInflater.from(this).inflate(R.layout.dialog_accounts, null, false)
        val dialog = AlertDialog.Builder(this).setView(v).create()
        dialog.window?.setBackgroundDrawableResource(android.R.color.transparent)
        fun bind() {
            val ig = Instagram.isLoggedIn()
            val fb = Facebook.isLoggedIn()
            v.findViewById<View>(R.id.igDot).setBackgroundResource(if (ig) R.drawable.dot_on else R.drawable.dot_off)
            v.findViewById<View>(R.id.fbDot).setBackgroundResource(if (fb) R.drawable.dot_on else R.drawable.dot_off)
            v.findViewById<TextView>(R.id.igState).text = if (ig) "Angemeldet" else "Nicht angemeldet"
            v.findViewById<TextView>(R.id.fbState).text = if (fb) "Angemeldet" else "Optional"
            v.findViewById<TextView>(R.id.igLoginBtn).text = if (ig) "Abmelden" else "Anmelden"
            v.findViewById<TextView>(R.id.fbLoginBtn).text = if (fb) "Abmelden" else "Anmelden"
        }
        v.findViewById<View>(R.id.igLoginBtn).setOnClickListener {
            if (Instagram.isLoggedIn()) { logout("https://www.instagram.com", ".instagram.com"); v.postDelayed({ bind(); onResume() }, 300) }
            else { dialog.dismiss(); openLogin("https://www.instagram.com/accounts/login/") }
        }
        v.findViewById<View>(R.id.fbLoginBtn).setOnClickListener {
            if (Facebook.isLoggedIn()) { logout("https://www.facebook.com", ".facebook.com"); v.postDelayed({ bind() }, 300) }
            else { dialog.dismiss(); openLogin("https://www.facebook.com/login/") }
        }
        bind()
        dialog.show()
    }

    /** Öffnet die installierte App (dort, wo man war), sonst die Webseite. */
    private fun openApp(packages: List<String>, webUrl: String) {
        for (p in packages) {
            val launch = packageManager.getLaunchIntentForPackage(p) ?: continue
            startActivity(launch)
            return
        }
        Ui.openUrl(this, webUrl)
    }

    private fun openLogin(url: String) {
        startActivity(Intent(this, LoginActivity::class.java).putExtra(LoginActivity.EXTRA_URL, url))
    }

    private fun logout(base: String, domain: String) {
        val cm = CookieManager.getInstance()
        val names = (cm.getCookie(base) ?: "").split(";").map { it.substringBefore("=").trim() }.filter { it.isNotEmpty() }
        for (n in names) {
            cm.setCookie(base, "$n=; Max-Age=0; Path=/; Domain=$domain")
            cm.setCookie(base, "$n=; Max-Age=0; Path=/")
        }
        cm.flush()
    }

    private fun showStatus(text: String) {
        status.text = text
        status.visibility = if (text.isEmpty()) View.GONE else View.VISIBLE
    }

    fun startDownload(raw: String) {
        if (busy) return
        val url = findLink(raw.trim()) ?: run { showStatus("Bitte einen gültigen Link einfügen."); return }
        busy = true
        downloadBtn.isEnabled = false
        progress.visibility = View.VISIBLE
        showStatus("Suche Medien …")
        (getSystemService(INPUT_METHOD_SERVICE) as InputMethodManager).hideSoftInputFromWindow(urlInput.windowToken, 0)

        Thread {
            var result: Extracted? = null
            val error = try {
                result = Saver.extract(url)
                null
            } catch (e: ExtractException) {
                "✗ ${e.message}"
            } catch (e: Exception) {
                "✗ Fehler: ${e.javaClass.simpleName} ${e.message ?: ""}"
            }
            runOnUiThread {
                progress.visibility = View.GONE
                downloadBtn.isEnabled = true
                busy = false
                if (error != null || result == null) {
                    showStatus(error ?: "✗ Unbekannter Fehler")
                } else {
                    showStatus("")
                    urlInput.setText("")
                    PickerActivity.pending = result
                    PickerActivity.pendingUrl = url
                    startActivity(Intent(this, PickerActivity::class.java))
                }
            }
        }.start()
    }

    // ---------- Verlauf ----------

    private inner class HomeAdapter : RecyclerView.Adapter<RecyclerView.ViewHolder>() {
        private var posts: List<Post> = emptyList()

        fun reload() {
            posts = History.all(this@MainActivity)
            header.findViewById<View>(R.id.emptyView).visibility = if (posts.isEmpty()) View.VISIBLE else View.GONE
            header.findViewById<TextView>(R.id.countText).text =
                if (posts.isEmpty()) "" else "${posts.size} Post${if (posts.size > 1) "s" else ""}"
            @Suppress("NotifyDataSetChanged")
            notifyDataSetChanged()
        }

        override fun getItemCount() = 1 + posts.size
        override fun getItemViewType(position: Int) = when (position) { 0 -> 0; 1 -> 1; else -> 2 }

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder {
            if (viewType == 0) {
                (header.parent as? ViewGroup)?.removeView(header)
                header.layoutParams = RecyclerView.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)
                return object : RecyclerView.ViewHolder(header) {}.apply { setIsRecyclable(false) }
            }
            val layout = if (viewType == 1) R.layout.item_featured else R.layout.item_post
            val v = LayoutInflater.from(parent.context).inflate(layout, parent, false)
            if (viewType == 1) v.findViewById<RatioFrame>(R.id.frame).ratio = 1.15f
            return PostHolder(v)
        }

        override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
            if (holder is PostHolder) holder.bind(posts[position - 1])
        }
    }

    private inner class PostHolder(v: View) : RecyclerView.ViewHolder(v) {
        private val image: ImageView = v.findViewById(R.id.image)
        private val badge: TextView = v.findViewById(R.id.badge)
        private val owner: TextView = v.findViewById(R.id.owner)
        private val more: View = v.findViewById(R.id.more)
        private val caption: TextView? = v.findViewById(R.id.caption)
        private val time: TextView? = v.findViewById(R.id.time)
        private val avatar: TextView? = v.findViewById(R.id.avatar)
        private val play: View? = v.findViewById(R.id.playIcon)

        fun bind(p: Post) {
            val first = p.entries.firstOrNull()
            image.load(first?.let { History.displaySource(this@MainActivity, it) })

            val name = p.owner.ifBlank { if (p.source == "facebook") "Facebook" else "Instagram" }
            owner.text = if (p.source == "instagram" && p.owner.isNotBlank()) "@${p.owner}" else name
            avatar?.text = name.take(1).uppercase()
            caption?.text = p.caption.lineSequence().firstOrNull { it.isNotBlank() } ?: Ui.ago(p.time)
            time?.text = Ui.ago(p.time)
            play?.visibility = if (p.entries.size == 1 && first?.isVideo == true) View.VISIBLE else View.GONE

            val videos = p.entries.count { it.isVideo }
            when {
                p.entries.size > 1 -> {
                    badge.visibility = View.VISIBLE
                    badge.text = "${p.entries.size}"
                    badge.setCompoundDrawablesRelative(small(R.drawable.ic_layers), null, null, null)
                }
                videos == 1 && play == null -> {
                    badge.visibility = View.VISIBLE
                    badge.text = "Video"
                    badge.setCompoundDrawablesRelative(small(R.drawable.ic_play), null, null, null)
                }
                else -> badge.visibility = View.GONE
            }

            itemView.findViewById<View>(R.id.frame).setOnClickListener {
                startActivity(Intent(this@MainActivity, PostActivity::class.java).putExtra(PostActivity.EXTRA_ID, p.id))
            }
            itemView.findViewById<View>(R.id.frame).setOnLongClickListener { showMenu(more, p); true }
            more.setOnClickListener { showMenu(it, p) }
        }

        private fun small(res: Int) = getDrawable(res)?.apply {
            val s = Ui.dp(this@MainActivity, 14); setBounds(0, 0, s, s)
        }

        private fun showMenu(anchor: View, p: Post) =
            Ui.postMenu(this@MainActivity, anchor, p, { startDownload(it.url) }, { adapter.reload() })
    }
}
