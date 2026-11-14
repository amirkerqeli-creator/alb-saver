package ch.albmedia.saver

import android.app.Activity
import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.util.LruCache
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.BaseAdapter
import android.widget.FrameLayout
import android.widget.GridView
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import java.net.HttpURLConnection
import java.net.URL
import java.util.concurrent.Executors

/** Zeigt alle gefundenen Bilder/Videos eines Posts – einzeln oder alle auswählen, dann herunterladen. */
class PickerActivity : Activity() {

    companion object {
        /** Wird von MainActivity vor dem Start gesetzt (einfacher als Parcelable). */
        var pending: List<Media> = emptyList()
    }

    private lateinit var items: List<Media>
    private val selected = mutableSetOf<Int>()
    private lateinit var title: TextView
    private lateinit var toggleBtn: TextView
    private lateinit var downloadBtn: TextView
    private lateinit var adapter: Adapter

    private val pool = Executors.newFixedThreadPool(4)
    private val cache = object : LruCache<String, Bitmap>(24 * 1024 * 1024) {
        override fun sizeOf(key: String, value: Bitmap) = value.byteCount
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        items = pending
        if (items.isEmpty()) { finish(); return }
        selected.addAll(items.indices) // standardmässig alles ausgewählt

        val pad = dp(12)
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            fitsSystemWindows = true
        }

        val header = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(pad, pad, pad, pad)
        }
        title = TextView(this).apply {
            textSize = 18f
            setTypeface(typeface, Typeface.BOLD)
        }
        toggleBtn = TextView(this).apply {
            setTextColor(Color.WHITE)
            textSize = 14f
            setTypeface(typeface, Typeface.BOLD)
            gravity = Gravity.CENTER
            setPadding(dp(16), 0, dp(16), 0)
            minHeight = dp(38)
            setBackgroundResource(R.drawable.bg_small_btn)
            setOnClickListener {
                if (selected.size == items.size) selected.clear() else selected.addAll(items.indices)
                refresh()
            }
        }
        header.addView(title, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
        header.addView(toggleBtn, LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, dp(38)))

        val grid = GridView(this).apply {
            numColumns = 3
            horizontalSpacing = dp(3)
            verticalSpacing = dp(3)
            stretchMode = GridView.STRETCH_COLUMN_WIDTH
            setPadding(dp(3), 0, dp(3), 0)
            clipToPadding = false
        }
        adapter = Adapter()
        grid.adapter = adapter
        grid.setOnItemClickListener { _, _, pos, _ ->
            if (!selected.remove(pos)) selected.add(pos)
            refresh()
        }

        downloadBtn = TextView(this).apply {
            textSize = 16f
            setTextColor(Color.WHITE)
            setTypeface(typeface, Typeface.BOLD)
            gravity = Gravity.CENTER
            setBackgroundResource(R.drawable.bg_primary)
            setOnClickListener { download() }
        }
        val footer = LinearLayout(this).apply {
            setPadding(pad, pad / 2, pad, pad)
            addView(downloadBtn, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(54)))
        }

        root.addView(header)
        root.addView(grid, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f))
        root.addView(footer)
        setContentView(root)
        refresh()
    }

    private fun refresh() {
        val videos = items.count { it.ext == "mp4" }
        val photos = items.size - videos
        title.text = listOfNotNull(
            if (photos > 0) "$photos Foto${if (photos > 1) "s" else ""}" else null,
            if (videos > 0) "$videos Video${if (videos > 1) "s" else ""}" else null
        ).joinToString(" · ")
        toggleBtn.text = if (selected.size == items.size) "Keine" else "Alle"
        downloadBtn.text = if (selected.isEmpty()) "Nichts ausgewählt" else "Herunterladen (${selected.size})"
        downloadBtn.isEnabled = selected.isNotEmpty()
        adapter.notifyDataSetChanged()
    }

    private fun download() {
        val chosen = selected.sorted().map { items[it] }
        try {
            Saver.enqueue(applicationContext, chosen)
            Toast.makeText(this, "✓ ${chosen.size} wird gespeichert", Toast.LENGTH_SHORT).show()
            setResult(RESULT_OK)
            finish()
        } catch (e: Exception) {
            Toast.makeText(this, "✗ Fehler: ${e.message}", Toast.LENGTH_LONG).show()
        }
    }

    override fun onDestroy() {
        pool.shutdownNow()
        super.onDestroy()
    }

    private fun dp(v: Int) = (v * resources.displayMetrics.density).toInt()

    // ---------- Raster ----------

    private class SquareFrame(ctx: Context) : FrameLayout(ctx) {
        override fun onMeasure(w: Int, h: Int) = super.onMeasure(w, w)
    }

    private class Holder(val image: ImageView, val check: TextView, val badge: TextView, val dim: View) {
        var url: String? = null
    }

    private inner class Adapter : BaseAdapter() {
        override fun getCount() = items.size
        override fun getItem(pos: Int) = items[pos]
        override fun getItemId(pos: Int) = pos.toLong()

        override fun getView(pos: Int, convertView: View?, parent: ViewGroup): View {
            val cell = convertView ?: createCell()
            val h = cell.tag as Holder
            val m = items[pos]
            val isSel = pos in selected

            h.badge.visibility = if (m.ext == "mp4") View.VISIBLE else View.GONE
            h.dim.visibility = if (isSel) View.GONE else View.VISIBLE
            h.check.text = if (isSel) "✓" else ""
            (h.check.background as GradientDrawable).setColor(if (isSel) Color.parseColor("#E53935") else Color.argb(80, 0, 0, 0))

            val url = m.thumb ?: if (m.ext == "jpg") m.url else null
            h.url = url
            if (url == null) {
                h.image.setImageBitmap(null)
            } else {
                val cached = cache.get(url)
                if (cached != null) h.image.setImageBitmap(cached) else {
                    h.image.setImageBitmap(null)
                    loadThumb(url, h)
                }
            }
            return cell
        }

        private fun createCell(): View {
            val cell = SquareFrame(this@PickerActivity)
            cell.setBackgroundColor(Color.parseColor("#23232B"))
            val img = ImageView(this@PickerActivity).apply { scaleType = ImageView.ScaleType.CENTER_CROP }
            val dim = View(this@PickerActivity).apply { setBackgroundColor(Color.argb(110, 0, 0, 0)) }
            val check = TextView(this@PickerActivity).apply {
                gravity = Gravity.CENTER
                setTextColor(Color.WHITE)
                textSize = 15f
                setTypeface(typeface, Typeface.BOLD)
                background = GradientDrawable().apply {
                    shape = GradientDrawable.OVAL
                    setStroke(dp(2), Color.WHITE)
                }
            }
            val badge = TextView(this@PickerActivity).apply {
                text = "▶"
                setTextColor(Color.WHITE)
                textSize = 13f
                setPadding(dp(6), dp(2), dp(6), dp(2))
                background = GradientDrawable().apply {
                    cornerRadius = dp(4).toFloat()
                    setColor(Color.argb(160, 0, 0, 0))
                }
            }
            val full = FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT)
            cell.addView(img, full)
            cell.addView(dim, FrameLayout.LayoutParams(full))
            cell.addView(check, FrameLayout.LayoutParams(dp(26), dp(26), Gravity.TOP or Gravity.END).apply {
                setMargins(0, dp(6), dp(6), 0)
            })
            cell.addView(badge, FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT, Gravity.BOTTOM or Gravity.START
            ).apply { setMargins(dp(6), 0, 0, dp(6)) })
            cell.tag = Holder(img, check, badge, dim)
            return cell
        }
    }

    private fun loadThumb(url: String, h: Holder) {
        pool.execute {
            val bmp = try { fetchBitmap(url) } catch (_: Exception) { null } ?: return@execute
            cache.put(url, bmp)
            runOnUiThread { if (h.url == url) h.image.setImageBitmap(bmp) }
        }
    }

    private fun fetchBitmap(url: String): Bitmap? {
        val c = URL(url).openConnection() as HttpURLConnection
        try {
            c.connectTimeout = 10000
            c.readTimeout = 15000
            c.setRequestProperty("User-Agent", Net.MOBILE_UA)
            val bytes = c.inputStream.use { it.readBytes() }
            val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            BitmapFactory.decodeByteArray(bytes, 0, bytes.size, bounds)
            var sample = 1
            val target = resources.displayMetrics.widthPixels / 3
            while (bounds.outWidth / (sample * 2) >= target) sample *= 2
            return BitmapFactory.decodeByteArray(bytes, 0, bytes.size, BitmapFactory.Options().apply { inSampleSize = sample })
        } finally {
            c.disconnect()
        }
    }
}
