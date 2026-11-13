package ch.albmedia.saver

import android.webkit.CookieManager
import org.json.JSONArray
import java.net.HttpURLConnection
import java.net.URL

data class Media(val url: String, val ext: String, val name: String, val thumb: String? = null)

class ExtractException(msg: String) : Exception(msg)

object Net {
    const val MOBILE_UA =
        "Mozilla/5.0 (Linux; Android 14; Pixel 8) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/130.0.0.0 Mobile Safari/537.36"
    const val DESKTOP_UA =
        "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/130.0.0.0 Safari/537.36"

    class Response(val code: Int, val body: String, val finalUrl: String)

    fun get(url: String, headers: Map<String, String> = emptyMap(), ua: String = MOBILE_UA): Response {
        val c = URL(url).openConnection() as HttpURLConnection
        try {
            c.instanceFollowRedirects = true
            c.connectTimeout = 15000
            c.readTimeout = 25000
            c.setRequestProperty("User-Agent", ua)
            headers.forEach { (k, v) -> c.setRequestProperty(k, v) }
            val code = c.responseCode
            val stream = if (code in 200..299) c.inputStream else c.errorStream
            val body = stream?.bufferedReader()?.use { it.readText() } ?: ""
            return Response(code, body, c.url.toString())
        } finally {
            c.disconnect()
        }
    }

    fun cookies(domain: String): String = CookieManager.getInstance().getCookie(domain) ?: ""

    /** Entfernt JSON-Escapes wie \/ und & */
    fun unescapeJson(s: String): String = JSONArray("[\"$s\"]").getString(0)
}
