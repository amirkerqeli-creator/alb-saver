package ch.albmedia.saver

import android.app.DownloadManager
import android.content.Context
import android.net.Uri
import android.os.Environment

object Saver {
    fun extract(url: String): List<Media> {
        val host = Uri.parse(url).host?.lowercase() ?: throw ExtractException("Ungültiger Link.")
        return when {
            Instagram.matches(host) -> Instagram.extract(url)
            Facebook.matches(host) -> Facebook.extract(url)
            else -> throw ExtractException("Nur Instagram- und Facebook-Links werden unterstützt.")
        }.ifEmpty { throw ExtractException("Nichts zum Herunterladen gefunden.") }
    }

    fun enqueue(ctx: Context, items: List<Media>) {
        val dm = ctx.getSystemService(Context.DOWNLOAD_SERVICE) as DownloadManager
        items.forEachIndexed { i, m ->
            val dir = if (m.ext == "mp4") Environment.DIRECTORY_MOVIES else Environment.DIRECTORY_PICTURES
            val suffix = if (items.size > 1 && items.count { it.name == m.name } > 1) "_${i + 1}" else ""
            val fileName = m.name.replace(Regex("[^A-Za-z0-9._-]"), "_") + suffix + "." + m.ext
            val req = DownloadManager.Request(Uri.parse(m.url))
                .setTitle(fileName)
                .setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED)
                .setDestinationInExternalPublicDir(dir, "ALBSaver/$fileName")
                .addRequestHeader("User-Agent", Net.MOBILE_UA)
            dm.enqueue(req)
        }
    }
}
