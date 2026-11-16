package ch.albmedia.saver

import android.app.Activity
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.text.format.DateUtils
import android.view.View
import android.widget.PopupMenu
import android.widget.Toast

object Ui {
    fun dp(ctx: Context, v: Int) = (v * ctx.resources.displayMetrics.density).toInt()

    fun ago(time: Long): String =
        DateUtils.getRelativeTimeSpanString(time, System.currentTimeMillis(), DateUtils.MINUTE_IN_MILLIS).toString()

    fun copy(ctx: Context, label: String, text: String) {
        val cm = ctx.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        cm.setPrimaryClip(ClipData.newPlainText(label, text))
        Toast.makeText(ctx, "$label kopiert", Toast.LENGTH_SHORT).show()
    }

    fun openUrl(ctx: Context, url: String) {
        try {
            ctx.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)))
        } catch (_: Exception) {
            Toast.makeText(ctx, "Konnte Link nicht öffnen", Toast.LENGTH_SHORT).show()
        }
    }

    /**
     * Drei-Punkte-Menü eines Posts.
     * onRedownload: Post neu laden · onRemoved: Post wurde aus dem Verlauf entfernt
     */
    fun postMenu(a: Activity, anchor: View, p: Post, onRedownload: (Post) -> Unit, onRemoved: () -> Unit) {
        val menu = PopupMenu(a, anchor)
        menu.menu.add(0, 1, 0, "Erneut herunterladen")
        menu.menu.add(0, 2, 1, "Link kopieren")
        if (p.caption.isNotBlank()) menu.menu.add(0, 3, 2, "Text kopieren")
        menu.menu.add(0, 4, 3, if (p.source == "facebook") "Auf Facebook ansehen" else "Auf Instagram ansehen")
        menu.menu.add(0, 5, 4, "Aus Verlauf entfernen")
        menu.setOnMenuItemClickListener {
            when (it.itemId) {
                1 -> onRedownload(p)
                2 -> copy(a, "Link", p.url)
                3 -> copy(a, "Text", p.caption)
                4 -> openUrl(a, p.url)
                5 -> { History.remove(a, p.id); onRemoved() }
            }
            true
        }
        menu.show()
    }
}
