package cz.santa36low.doprava

import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URLEncoder
import java.net.URL

data class NearbyPlace(val name: String, val address: String)

object PlaceLookup {
    fun search(latitude: Double, longitude: Double): List<NearbyPlace> {
        val query = """[out:json][timeout:8];(node(around:600,$latitude,$longitude)["name"];way(around:600,$latitude,$longitude)["name"];relation(around:600,$latitude,$longitude)["name"];);out center tags 20;"""
        val url = URL("https://overpass-api.de/api/interpreter?data=" + URLEncoder.encode(query, "UTF-8"))
        val connection = (url.openConnection() as HttpURLConnection).apply {
            connectTimeout = 5000
            readTimeout = 9000
            requestMethod = "GET"
            setRequestProperty("User-Agent", "DopravaAndroid/1.0 (personal activity log)")
        }
        return try {
            val text = connection.inputStream.bufferedReader().use { it.readText() }
            val elements = JSONObject(text).optJSONArray("elements") ?: return emptyList()
            val found = linkedMapOf<String, NearbyPlace>()
            for (i in 0 until elements.length()) {
                val tags = elements.optJSONObject(i)?.optJSONObject("tags") ?: continue
                val name = tags.optString("name").trim()
                if (name.isBlank()) continue
                val street = tags.optString("addr:street")
                val number = tags.optString("addr:housenumber")
                val city = tags.optString("addr:city")
                val address = listOf(street, number, city).filter { it.isNotBlank() }.joinToString(" ")
                found.putIfAbsent(name.lowercase(), NearbyPlace(name, address))
            }
            found.values.take(12)
        } finally {
            connection.disconnect()
        }
    }
}
