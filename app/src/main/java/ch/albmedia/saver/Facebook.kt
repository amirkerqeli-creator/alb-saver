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

    fun extract(input: String): List<Media> {
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

        for (key in KEYS) {
            val m = Regex("\"$key\":\"([^\"]+)\"").find(html) ?: continue
            return listOf(Media(Net.unescapeJson(m.groupValues[1]), "mp4", "facebook_$id"))
        }
        for (key in listOf("hd_src", "sd_src")) {
            val m = Regex("$key:\"([^\"]+)\"").find(html) ?: continue
            return listOf(Media(Net.unescapeJson(m.groupValues[1]), "mp4", "facebook_$id"))
        }

        throw ExtractException(
            if (isLoggedIn()) "Kein Video gefunden. Nur Facebook-Videos und Reels werden unterstützt."
            else "Kein Video gefunden – bitte unten „Bei Facebook anmelden“ tippen und nochmal versuchen."
        )
    }
}
