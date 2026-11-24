package app.embertv.data

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import org.json.JSONTokener
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder

/** A place for the weather: what to call it and where it is. */
data class WeatherPlace(val name: String, val detail: String, val lat: Double, val lon: Double) {
    fun toJson(): String = JSONObject().put("name", name).put("detail", detail).put("lat", lat).put("lon", lon).toString()

    companion object {
        fun fromJson(s: String): WeatherPlace? = try {
            if (s.isBlank()) null else JSONObject(s).let { o ->
                WeatherPlace(o.optString("name"), o.optString("detail"), o.optDouble("lat"), o.optDouble("lon"))
                    .takeIf { it.name.isNotBlank() && !it.lat.isNaN() && !it.lon.isNaN() }
            }
        } catch (e: Exception) {
            null
        }
    }
}

/** One hour of the forecast. [time] is "HH:mm" local to the place. */
data class WeatherHour(val time: String, val temp: Int, val code: Int, val day: Boolean)

/** Now, today's high and low, and the next few hours. Temperatures in whole degrees. */
data class WeatherNow(
    val temp: Int,
    val feelsLike: Int,
    val code: Int,
    val day: Boolean,
    val high: Int,
    val low: Int,
    val hours: List<WeatherHour>,
    val fetchedAt: Long,
)

/**
 * The weather from Open-Meteo: free, no account or key, and only the town's position is sent (it
 * never sees who you are or what you watch).
 */
object Weather {
    private suspend fun get(url: String): JSONObject? = withContext(Dispatchers.IO) {
        try {
            val c = URL(url).openConnection() as HttpURLConnection
            c.connectTimeout = 10_000
            c.readTimeout = 15_000
            c.setRequestProperty("Accept", "application/json")
            try {
                if (c.responseCode !in 200..299) return@withContext null
                JSONTokener(c.inputStream.bufferedReader().use { it.readText() }).nextValue() as? JSONObject
            } finally {
                c.disconnect()
            }
        } catch (e: Exception) {
            null
        }
    }

    /** Towns matching [query], best first. Null if the search couldn't be done (no connection). */
    suspend fun findPlaces(query: String): List<WeatherPlace>? {
        val q = URLEncoder.encode(query.trim(), "UTF-8")
        val root = get("https://geocoding-api.open-meteo.com/v1/search?name=$q&count=8&language=en&format=json") ?: return null
        val arr = root.optJSONArray("results") ?: return emptyList()
        val out = ArrayList<WeatherPlace>()
        for (i in 0 until arr.length()) {
            val o = arr.optJSONObject(i) ?: continue
            val name = o.optString("name")
            if (name.isBlank()) continue
            val detail = listOf(o.optString("admin1"), o.optString("country"))
                .filter { it.isNotBlank() && it != "null" && !it.equals(name, ignoreCase = true) }
                .distinct()
                .joinToString(", ")
            out += WeatherPlace(name, detail, o.optDouble("latitude"), o.optDouble("longitude"))
        }
        return out
    }

    /** The weather at [place] now and for the next hours; [fahrenheit] for °F. Null if it couldn't be fetched. */
    suspend fun forecast(place: WeatherPlace, fahrenheit: Boolean): WeatherNow? {
        val unit = if (fahrenheit) "&temperature_unit=fahrenheit" else ""
        val root = get(
            "https://api.open-meteo.com/v1/forecast?latitude=${place.lat}&longitude=${place.lon}" +
                "&current=temperature_2m,apparent_temperature,weather_code,is_day" +
                "&hourly=temperature_2m,weather_code,is_day" +
                "&daily=temperature_2m_max,temperature_2m_min" +
                "&timezone=auto&forecast_days=2$unit"
        ) ?: return null
        val current = root.optJSONObject("current") ?: return null
        val nowTime = current.optString("time") // "2026-11-24T19:00", local to the place
        val hourly = root.optJSONObject("hourly")
        val times = hourly?.optJSONArray("time") ?: JSONArray()
        val temps = hourly?.optJSONArray("temperature_2m") ?: JSONArray()
        val codes = hourly?.optJSONArray("weather_code") ?: JSONArray()
        val days = hourly?.optJSONArray("is_day") ?: JSONArray()
        val hours = ArrayList<WeatherHour>()
        // The hours after this one.
        for (i in 0 until times.length()) {
            val t = times.optString(i)
            if (t <= nowTime) continue
            hours += WeatherHour(t.substringAfter('T').take(5), Math.round(temps.optDouble(i)).toInt(), codes.optInt(i), days.optInt(i, 1) == 1)
            if (hours.size == 5) break
        }
        val daily = root.optJSONObject("daily")
        val temp = current.optDouble("temperature_2m").takeIf { !it.isNaN() } ?: return null
        fun whole(d: Double?, fallback: Double) = Math.round(d?.takeIf { !it.isNaN() } ?: fallback).toInt()
        return WeatherNow(
            temp = whole(temp, temp),
            feelsLike = whole(current.optDouble("apparent_temperature"), temp),
            code = current.optInt("weather_code"),
            day = current.optInt("is_day", 1) == 1,
            high = whole(daily?.optJSONArray("temperature_2m_max")?.optDouble(0), temp),
            low = whole(daily?.optJSONArray("temperature_2m_min")?.optDouble(0), temp),
            hours = hours,
            fetchedAt = System.currentTimeMillis(),
        )
    }

    /** Words for a WMO weather code, as a weather app would put it. */
    fun describe(code: Int): String = when (code) {
        0 -> "Clear"
        1 -> "Mostly clear"
        2 -> "Partly cloudy"
        3 -> "Cloudy"
        45, 48 -> "Fog"
        51, 53, 55 -> "Drizzle"
        56, 57 -> "Freezing drizzle"
        61 -> "Light rain"
        63 -> "Rain"
        65 -> "Heavy rain"
        66, 67 -> "Freezing rain"
        71 -> "Light snow"
        73 -> "Snow"
        75 -> "Heavy snow"
        77 -> "Snow grains"
        80, 81 -> "Showers"
        82 -> "Heavy showers"
        85, 86 -> "Snow showers"
        95 -> "Thunderstorms"
        96, 99 -> "Thunderstorms with hail"
        else -> "—"
    }
}
