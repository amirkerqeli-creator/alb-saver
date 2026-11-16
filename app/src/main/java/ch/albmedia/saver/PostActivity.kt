package ch.albmedia.saver

import android.app.Activity
import android.content.Intent
import android.graphics.drawable.GradientDrawable
import android.net.Uri
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.VideoView
import androidx.recyclerview.widget.RecyclerView
import androidx.viewpager2.widget.ViewPager2
import coil.load

/** Zeigt einen heruntergeladenen Post: alle Bilder/Videos zum Wischen, Beschreibung und Aktionen. */
class PostActivity : Activity() {
    companion object { const val EXTRA_ID = "id" }

    private lateinit var post: Post
    private lateinit var pager: ViewPager2
    private var playing: VideoView? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        post = History.get(this, intent.getLongExtra(EXTRA_ID, 0)) ?: run { finish(); return }
        setContentView(R.layout.activity_post)

        val name = post.owner.ifBlank { if (post.source == "facebook") "Facebook" else "Instagram" }
        findViewById<TextView>(R.id.owner).text = if (post.source == "instagram" && post.owner.isNotBlank()) "@${post.owner}" else name
        findViewById<TextView>(R.id.avatar).text = name.take(1).uppercase()
        findViewById<TextView>(R.id.time).text = Ui.ago(post.time)
        findViewById<View>(R.id.backBtn).setOnClickListener { finish() }
        findViewById<View>(R.id.moreBtn).setOnClickListener {
            Ui.postMenu(this, it, post, { redownload() }, { finish() })
        }

        // Beschreibung
        val caption = findViewById<TextView>(R.id.caption)
        if (post.caption.isBlank()) findViewById<View>(R.id.captionCard).visibility = View.GONE
        else caption.text = post.caption

        // Aktionen
        findViewById<View>(R.id.copyLinkBtn).setOnClickListener { Ui.copy(this, "Link", post.url) }
        findViewById<View>(R.id.copyTextBtn).apply {
            isEnabled = post.caption.isNotBlank()
            alpha = if (isEnabled) 1f else 0.4f
            setOnClickListener { Ui.copy(this@PostActivity, "Text", post.caption) }
        }
        findViewById<View>(R.id.reloadBtn).setOnClickListener { redownload() }
        findViewById<TextView>(R.id.openBtn).apply {
            text = if (post.source == "facebook") "Facebook" else "Instagram"
            setOnClickListener { Ui.openUrl(this@PostActivity, post.url) }
        }

        // Medien
        val counter = findViewById<TextView>(R.id.counter)
        val dots = findViewById<LinearLayout>(R.id.dots)
        pager = findViewById(R.id.pager)
        pager.adapter = PageAdapter()
        val n = post.entries.size
        counter.visibility = if (n > 1) View.VISIBLE else View.GONE
        dots.visibility = if (n > 1) View.VISIBLE else View.INVISIBLE
        if (n > 1) repeat(n) {
            dots.addView(View(this).apply {
                layoutParams = LinearLayout.LayoutParams(Ui.dp(this@PostActivity, 7), Ui.dp(this@PostActivity, 7)).apply {
                    setMargins(Ui.dp(this@PostActivity, 3), 0, Ui.dp(this@PostActivity, 3), 0)
                }
                background = GradientDrawable().apply { shape = GradientDrawable.OVAL }
            })
        }
        pager.registerOnPageChangeCallback(object : ViewPager2.OnPageChangeCallback() {
            override fun onPageSelected(position: Int) {
                stopVideo()
                counter.text = "${position + 1} / $n"
                for (i in 0 until dots.childCount) {
                    (dots.getChildAt(i).background as GradientDrawable).setColor(
                        getColor(if (i == position) R.color.accent else R.color.off)
                    )
                }
            }
        })
    }

    private fun redownload() {
        // Über MainActivity neu analysieren → Auswahl-Ansicht
        startActivity(
            Intent(this, MainActivity::class.java)
                .setAction(Intent.ACTION_SEND)
                .putExtra(Intent.EXTRA_TEXT, post.url)
                .addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP)
        )
        finish()
    }

    private fun stopVideo() {
        playing?.let {
            it.stopPlayback()
            it.visibility = View.GONE
            (it.parent as View).findViewById<View>(R.id.image).visibility = View.VISIBLE
            (it.parent as View).findViewById<View>(R.id.playIcon).visibility = View.VISIBLE
        }
        playing = null
    }

    override fun onPause() {
        stopVideo()
        super.onPause()
    }

    private inner class PageHolder(v: View) : RecyclerView.ViewHolder(v) {
        val image: ImageView = v.findViewById(R.id.image)
        val video: VideoView = v.findViewById(R.id.video)
        val play: View = v.findViewById(R.id.playIcon)
    }

    private inner class PageAdapter : RecyclerView.Adapter<PageHolder>() {
        override fun getItemCount() = post.entries.size

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int) =
            PageHolder(LayoutInflater.from(parent.context).inflate(R.layout.page_media, parent, false))

        override fun onBindViewHolder(h: PageHolder, position: Int) {
            val e = post.entries[position]
            h.image.visibility = View.VISIBLE
            h.video.visibility = View.GONE
            h.image.load(History.displaySource(this@PostActivity, e))
            h.play.visibility = if (e.isVideo) View.VISIBLE else View.GONE
            val start = View.OnClickListener {
                if (!e.isVideo) return@OnClickListener
                stopVideo()
                val local = History.localUri(this@PostActivity, e)
                h.image.visibility = View.GONE
                h.play.visibility = View.GONE
                h.video.visibility = View.VISIBLE
                if (local != null) h.video.setVideoURI(local)
                else h.video.setVideoURI(Uri.parse(e.remoteUrl), mapOf("User-Agent" to Net.MOBILE_UA))
                h.video.setOnPreparedListener { mp -> mp.isLooping = true; h.video.start() }
                h.video.setOnClickListener { if (h.video.isPlaying) h.video.pause() else h.video.start() }
                h.video.setOnErrorListener { _, _, _ -> stopVideo(); true }
                playing = h.video
            }
            h.play.setOnClickListener(start)
            h.image.setOnClickListener(start)
        }

        override fun onViewRecycled(h: PageHolder) {
            if (playing == h.video) stopVideo()
        }
    }
}
