package roro.stellar.manager.location

import com.google.gson.JsonParser
import roro.stellar.manager.BuildConfig
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder

internal object LocationSearch {
    private val COORD = Regex("""^\s*(-?\d+(?:\.\d+)?)\s*[,，]\s*(-?\d+(?:\.\d+)?)\s*$""")

    fun query(raw: String): List<SearchHit> {
        val text = raw.trim()
        if (text.length < 2) return emptyList()
        COORD.matchEntire(text)?.let { match ->
            val lat = match.groupValues[1].toDouble()
            val lng = match.groupValues[2].toDouble()
            if (lat in -90.0..90.0 && lng in -180.0..180.0) {
                return listOf(SearchHit(text, lat, lng))
            }
        }
        val url = URL(
            "https://nominatim.openstreetmap.org/search?format=json&limit=5&q=" +
                URLEncoder.encode(text, Charsets.UTF_8.name())
        )
        val connection = (url.openConnection() as HttpURLConnection).apply {
            connectTimeout = 8000
            readTimeout = 8000
            setRequestProperty("User-Agent", "Stellar/${BuildConfig.VERSION_NAME}")
            setRequestProperty("Accept-Language", "zh-CN,zh,en")
        }
        return try {
            if (connection.responseCode != 200) return emptyList()
            val body = connection.inputStream.bufferedReader().use { it.readText() }
            JsonParser.parseString(body).asJsonArray.mapNotNull { el ->
                val obj = el.asJsonObject
                val lat = obj.get("lat")?.asString?.toDoubleOrNull() ?: return@mapNotNull null
                val lng = obj.get("lon")?.asString?.toDoubleOrNull() ?: return@mapNotNull null
                val name = obj.get("display_name")?.asString.orEmpty()
                    .split(',')
                    .take(3)
                    .joinToString("，") { it.trim() }
                    .ifEmpty { "$lat, $lng" }
                SearchHit(name, lat, lng)
            }
        } finally {
            connection.disconnect()
        }
    }
}
