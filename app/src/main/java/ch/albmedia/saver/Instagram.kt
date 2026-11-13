package ch.albmedia.saver

import android.net.Uri
import org.json.JSONObject
import java.math.BigInteger
import java.net.URLEncoder

object Instagram {
    private const val APP_ID = "936619743392459"
    private const val ALPHABET = "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789-_"
    private const val BASE = "https://www.instagram.com"

    fun matches(host: String) = host.contains("instagram.com") || host.contains("instagr.am")

    fun isLoggedIn() = Net.cookies(BASE).contains("sessionid=")

    fun extract(input: String): List<Media> {
        if (!isLoggedIn()) throw ExtractException("Bitte zuerst unten „Bei Instagram anmelden“ tippen.")

        var uri = Uri.parse(input)
        // Kurzlinks (instagram.com/share/...) auflösen
        if (uri.pathSegments.firstOrNull() == "share") {
            val r = Net.get(input, mapOf("Cookie" to Net.cookies(BASE)))
            uri = Uri.parse(r.finalUrl)
        }
        val s = uri.pathSegments
        return when {
            s.size >= 3 && s[0] == "stories" && s[1] == "highlights" -> reels("highlight:${s[2]}")
            s.size >= 3 && s[0] == "stories" -> mediaInfo(s[2])
            s.size == 2 && s[0] == "stories" -> reels(userId(s[1]))
            s.size >= 2 && s[0] in POST_TYPES -> mediaInfo(shortcodeToId(s[1]))
            s.size >= 3 && s[1] in POST_TYPES -> mediaInfo(shortcodeToId(s[2]))
            else -> throw ExtractException("Diesen Instagram-Link erkenne ich nicht.")
        }
    }

    private val POST_TYPES = setOf("p", "reel", "reels", "tv")

    private fun shortcodeToId(code: String): String {
        var id = BigInteger.ZERO
        for (ch in code.take(11)) {
            val i = ALPHABET.indexOf(ch)
            if (i < 0) throw ExtractException("Ungültiger Instagram-Link.")
            id = id.shiftLeft(6).add(BigInteger.valueOf(i.toLong()))
        }
        return id.toString()
    }

    private fun api(path: String): JSONObject {
        val cookie = Net.cookies(BASE)
        val csrf = Regex("csrftoken=([^;]+)").find(cookie)?.groupValues?.get(1) ?: ""
        val r = Net.get(
            "$BASE/api/v1/$path",
            mapOf(
                "Cookie" to cookie,
                "X-IG-App-ID" to APP_ID,
                "X-CSRFToken" to csrf,
                "X-Requested-With" to "XMLHttpRequest",
                "Referer" to "$BASE/",
                "Accept" to "*/*"
            )
        )
        when {
            r.code == 401 || r.code == 403 ->
                throw ExtractException("Instagram verweigert den Zugriff – bitte neu anmelden.")
            r.code == 404 ->
                throw ExtractException("Nicht gefunden – gelöscht, abgelaufen oder privat (du folgst dem Konto nicht).")
            r.code == 429 ->
                throw ExtractException("Zu viele Anfragen – bitte ein paar Minuten warten.")
            r.code !in 200..299 ->
                throw ExtractException("Instagram-Fehler ${r.code}")
        }
        return try {
            JSONObject(r.body)
        } catch (e: Exception) {
            throw ExtractException("Unerwartete Antwort von Instagram – bitte neu anmelden.")
        }
    }

    private fun mediaInfo(mediaId: String): List<Media> {
        val items = api("media/$mediaId/info/").optJSONArray("items")
            ?: throw ExtractException("Kein Inhalt gefunden.")
        val out = mutableListOf<Media>()
        for (i in 0 until items.length()) parseItem(items.getJSONObject(i), null, out)
        return out
    }

    private fun reels(reelId: String): List<Media> {
        val json = api("feed/reels_media/?reel_ids=" + URLEncoder.encode(reelId, "UTF-8"))
        val items = json.optJSONObject("reels")?.optJSONObject(reelId)?.optJSONArray("items")
            ?: json.optJSONArray("reels_media")?.optJSONObject(0)?.optJSONArray("items")
            ?: throw ExtractException("Keine aktuellen Stories gefunden.")
        val out = mutableListOf<Media>()
        for (i in 0 until items.length()) parseItem(items.getJSONObject(i), null, out)
        return out
    }

    private fun userId(username: String): String {
        val json = api("users/web_profile_info/?username=" + URLEncoder.encode(username, "UTF-8"))
        return json.optJSONObject("data")?.optJSONObject("user")?.optString("id")
            ?.takeIf { it.isNotEmpty() }
            ?: throw ExtractException("Konto „$username“ nicht gefunden.")
    }

    private fun parseItem(item: JSONObject, parentOwner: String?, out: MutableList<Media>) {
        val owner = item.optJSONObject("user")?.optString("username")?.takeIf { it.isNotEmpty() }
            ?: parentOwner ?: "instagram"
        val carousel = item.optJSONArray("carousel_media")
        if (carousel != null) {
            for (i in 0 until carousel.length()) parseItem(carousel.getJSONObject(i), owner, out)
            return
        }
        val id = item.optString("pk").ifEmpty { item.optString("id").substringBefore("_") }
        val name = "${owner}_$id"

        // Vorschaubild (ca. 320–640 px breit) und bestes Bild suchen
        val candidates = item.optJSONObject("image_versions2")?.optJSONArray("candidates")
        var bestImg: JSONObject? = null
        var thumb: String? = null
        if (candidates != null && candidates.length() > 0) {
            var thumbW = Int.MAX_VALUE
            for (i in 0 until candidates.length()) {
                val c = candidates.getJSONObject(i)
                val w = c.optInt("width")
                if (bestImg == null || w > bestImg.optInt("width")) bestImg = c
                if (w in 300 until thumbW) { thumbW = w; thumb = c.optString("url") }
            }
            if (thumb == null) thumb = bestImg?.optString("url")
        }

        val videos = item.optJSONArray("video_versions")
        if (videos != null && videos.length() > 0) {
            var best: JSONObject = videos.getJSONObject(0)
            for (i in 1 until videos.length()) {
                val v = videos.getJSONObject(i)
                if (v.optInt("width") * v.optInt("height") > best.optInt("width") * best.optInt("height")) best = v
            }
            out += Media(best.getString("url"), "mp4", name, thumb)
            return
        }
        if (bestImg != null) out += Media(bestImg.getString("url"), "jpg", name, thumb)
    }
}
