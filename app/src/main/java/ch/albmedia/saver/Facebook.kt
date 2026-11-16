package ch.albmedia.saver

object Facebook {
    private const val BASE = "https://www.facebook.com"

    fun matches(host: String) = host.contains("facebook.com") || host.contains("fb.watch") || host.contains("fb.com")

    fun isLoggedIn() = Net.cookies(BASE).contains("c_user=")

    private val KEYS = listOf(
        "browser_native_hd_url",
        "playable_url_quality_hd",
        "browser_native_sd_url",
        "playable_url"
    )

    fun extract(input: String): Extracted {
        val url = input
            .replace("://m.facebook.com", "://www.facebook.com")
            .replace("://mbasic.facebook.com", "://www.facebook.com")
            .replace("://web.facebook.com", "://www.facebook.com")

        val headers = mutableMapOf(
            "Accept" to "text/html,application/xhtml+xml,application/xml;q=0.9,*/*;q=0.8",
            "Accept-Language" to "de-CH,de;q=0.9,en;q=0.8",
            "Sec-Fetch-Mode" to "navigate",
            "Sec-Fetch-Site" to "none",
            "Upgrade-Insecure-Requests" to "1"
        )
        val cookie = Net.cookies(BASE)
        if (cookie.isNotEmpty()) headers["Cookie"] = cookie

        val r = Net.get(url, headers, Net.DESKTOP_UA)
        val html = r.body
        val id = Regex("""(?:videos/|v=|reel/)(\d+)""").find(r.finalUrl)?.groupValues?.get(1)
            ?: System.currentTimeMillis().toString()

        val thumb = Regex("""<meta property="og:image" content="([^"]+)"""").find(html)
            ?.groupValues?.get(1)?.replace("&amp;", "&")
            ?: Regex(""""preferred_thumbnail":\{"image":\{"uri":"([^"]+)"""").find(html)
                ?.groupValues?.get(1)?.let { Net.unescapeJson(it) }

        val caption = Regex("""<meta property="og:description" content="([^"]*)"""").find(html)
            ?.groupValues?.get(1)?.let { android.text.Html.fromHtml(it, 0).toString() } ?: ""
        val owner = Regex("""<meta property="og:title" content="([^"]*)"""").find(html)
            ?.groupValues?.get(1)?.let { android.text.Html.fromHtml(it, 0).toString() } ?: "Facebook"
        fun result(u: String) = Extracted(listOf(Media(u, "mp4", "facebook_$id", thumb)), caption, owner, "facebook")

        for (key in KEYS) {
            val m = Regex("\"$key\":\"([^\"]+)\"").find(html) ?: continue
            return result(Net.unescapeJson(m.groupValues[1]))
        }
        for (key in listOf("hd_src", "sd_src")) {
            val m = Regex("$key:\"([^\"]+)\"").find(html) ?: continue
            return result(Net.unescapeJson(m.groupValues[1]))
        }

        throw ExtractException(
            if (isLoggedIn()) "Kein Video gefunden. Nur Facebook-Videos und Reels werden unterstützt."
            else "Kein Video gefunden – bitte unten „Bei Facebook anmelden“ tippen und nochmal versuchen."
        )
    }
}
