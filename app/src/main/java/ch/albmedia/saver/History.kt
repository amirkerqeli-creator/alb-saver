package ch.albmedia.saver

import android.app.DownloadManager
import android.content.Context
import android.net.Uri
import org.json.JSONArray
import org.json.JSONObject
import java.io.File

/** Eine heruntergeladene Datei eines Posts */
data class Entry(val remoteUrl: String, val thumb: String?, val ext: String, val downloadId: Long) {
    val isVideo get() = ext == "mp4"
}

/** Ein heruntergeladener Post im Verlauf */
data class Post(
    val id: Long,
    val url: String,
    val caption: String,
    val owner: String,
    val source: String,
    val time: Long,
    val entries: List<Entry>
)

/** Verlauf der Downloads – als JSON im privaten App-Speicher */
object History {
    private const val MAX = 300
    private var cache: MutableList<Post>? = null

    private fun file(ctx: Context) = File(ctx.filesDir, "history.json")

    @Synchronized
    fun all(ctx: Context): List<Post> {
        cache?.let { return it.toList() }
        val list = mutableListOf<Post>()
        try {
            val arr = JSONArray(file(ctx).readText())
            for (i in 0 until arr.length()) list += fromJson(arr.getJSONObject(i))
        } catch (_: Exception) { }
        cache = list
        return list.toList()
    }

    @Synchronized
    fun add(ctx: Context, post: Post) {
        val list = all(ctx).toMutableList()
        list.removeAll { it.url == post.url && it.entries.map { e -> e.remoteUrl } == post.entries.map { e -> e.remoteUrl } }
        list.add(0, post)
        while (list.size > MAX) list.removeAt(list.size - 1)
        save(ctx, list)
    }

    @Synchronized
    fun remove(ctx: Context, id: Long) {
        save(ctx, all(ctx).filter { it.id != id }.toMutableList())
    }

    @Synchronized
    fun clear(ctx: Context) = save(ctx, mutableListOf())

    fun get(ctx: Context, id: Long) = all(ctx).firstOrNull { it.id == id }

    private fun save(ctx: Context, list: MutableList<Post>) {
        cache = list
        val arr = JSONArray()
        list.forEach { arr.put(toJson(it)) }
        try { file(ctx).writeText(arr.toString()) } catch (_: Exception) { }
    }

    /** Lokale Datei, sobald der Download fertig ist – sonst null */
    fun localUri(ctx: Context, e: Entry): Uri? = try {
        val dm = ctx.getSystemService(Context.DOWNLOAD_SERVICE) as DownloadManager
        dm.getUriForDownloadedFile(e.downloadId)
    } catch (_: Exception) { null }

    /** Was als Bild angezeigt wird: lokale Datei, sonst Vorschaubild aus dem Netz */
    fun displaySource(ctx: Context, e: Entry): Any? =
        localUri(ctx, e) ?: e.thumb ?: if (!e.isVideo) e.remoteUrl else null

    private fun toJson(p: Post) = JSONObject().apply {
        put("id", p.id); put("url", p.url); put("caption", p.caption)
        put("owner", p.owner); put("source", p.source); put("time", p.time)
        put("entries", JSONArray().apply {
            p.entries.forEach { e ->
                put(JSONObject().apply {
                    put("remote", e.remoteUrl); put("thumb", e.thumb ?: ""); put("ext", e.ext); put("dl", e.downloadId)
                })
            }
        })
    }

    private fun fromJson(o: JSONObject): Post {
        val arr = o.optJSONArray("entries") ?: JSONArray()
        val entries = (0 until arr.length()).map {
            val e = arr.getJSONObject(it)
            Entry(e.optString("remote"), e.optString("thumb").ifEmpty { null }, e.optString("ext"), e.optLong("dl"))
        }
        return Post(
            o.optLong("id"), o.optString("url"), o.optString("caption"), o.optString("owner"),
            o.optString("source"), o.optLong("time"), entries
        )
    }
}
