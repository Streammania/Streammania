package app.embertv.ui

import android.content.Context
import app.embertv.data.Category
import app.embertv.data.Channel
import app.embertv.data.Movie
import app.embertv.data.Quality
import app.embertv.data.Series
import org.json.JSONArray
import org.json.JSONObject
import java.io.File

/**
 * Home's provider rows from last time, kept in the app's cache folder (one file per account and
 * profile), so Home opens already filled in while fresh rows load behind it. Only a cache: the
 * system may clear it, it never leaves the device, and anything older than three days is ignored.
 */
object HomeCache {
    private const val MAX_AGE_MS = 3 * 24 * 3_600_000L

    private fun file(ctx: Context, key: String) = File(ctx.cacheDir, "home-$key.json")

    fun read(ctx: Context, key: String): HomeFeed? = try {
        val f = file(ctx, key)
        if (!f.exists()) null else {
            val root = JSONObject(f.readText())
            if (root.optInt("v") != 1 || System.currentTimeMillis() - root.optLong("at") > MAX_AGE_MS) null else {
                val movieRows = rowsOf(root.optJSONArray("movies")) { movieOf(it) }
                val seriesRows = rowsOf(root.optJSONArray("series")) { seriesOf(it) }
                val live = root.optJSONObject("live")?.let { l ->
                    val cat = categoryOf(l.optJSONObject("cat")) ?: return@let null
                    val items = listOf(l.optJSONArray("items")) { channelOf(it) }
                    if (items.isEmpty()) null else cat to items
                }
                val trending = listOf(root.optJSONArray("trending")) { movieOf(it) }
                if (movieRows.isEmpty() && seriesRows.isEmpty() && live == null && trending.isEmpty()) null
                else HomeFeed(
                    heroes = movieRows.firstOrNull()?.second.orEmpty().filter { it.poster.isNotBlank() }.take(6),
                    liveRow = live,
                    movieRows = movieRows,
                    seriesRows = seriesRows,
                    trending = trending,
                )
            }
        }
    } catch (e: Exception) {
        null
    }

    fun write(ctx: Context, key: String, feed: HomeFeed) {
        try {
            val root = JSONObject()
                .put("v", 1)
                .put("at", System.currentTimeMillis())
                .put("trending", JSONArray().apply { feed.trending.forEach { put(movieJson(it)) } })
                .put("movies", JSONArray().apply { feed.movieRows.forEach { (c, items) -> put(rowJson(c, items.map(::movieJson))) } })
                .put("series", JSONArray().apply { feed.seriesRows.forEach { (c, items) -> put(rowJson(c, items.map(::seriesJson))) } })
            feed.liveRow?.let { (c, items) -> root.put("live", rowJson(c, items.map(::channelJson))) }
            val f = file(ctx, key)
            val tmp = File(f.parentFile, f.name + ".tmp")
            tmp.writeText(root.toString())
            tmp.renameTo(f)
        } catch (e: Exception) {
            // Only a cache: next time Home just loads as it did before.
        }
    }

    /** Clears every saved Home (signing out, or refreshing everything). */
    fun clear(ctx: Context) {
        ctx.cacheDir.listFiles { f -> f.name.startsWith("home-") }?.forEach { it.delete() }
    }

    // ---- JSON ----

    private fun rowJson(c: Category, items: List<JSONObject>) =
        JSONObject().put("cat", JSONObject().put("id", c.id).put("name", c.name)).put("items", JSONArray().apply { items.forEach { put(it) } })

    private fun categoryOf(o: JSONObject?): Category? = o?.let { Category(it.optString("id"), it.optString("name")) }?.takeIf { it.id.isNotBlank() }

    private fun <T> listOf(arr: JSONArray?, read: (JSONObject) -> T?): List<T> {
        if (arr == null) return emptyList()
        val out = ArrayList<T>(arr.length())
        for (i in 0 until arr.length()) arr.optJSONObject(i)?.let(read)?.let { out += it }
        return out
    }

    private fun <T> rowsOf(arr: JSONArray?, read: (JSONObject) -> T?): List<Pair<Category, List<T>>> =
        listOf(arr) { o ->
            val cat = categoryOf(o.optJSONObject("cat")) ?: return@listOf null
            val items = listOf(o.optJSONArray("items"), read)
            if (items.isEmpty()) null else cat to items
        }

    private fun quality(s: String): Quality = Quality.values().firstOrNull { it.name == s } ?: Quality.UNKNOWN

    private fun movieJson(m: Movie): JSONObject = JSONObject()
        .put("id", m.streamId).put("name", m.name).put("poster", m.poster).put("cat", m.categoryId)
        .put("rating", m.rating).put("ext", m.ext).put("added", m.added).put("q", m.quality.name)
        .apply { if (m.versions.isNotEmpty()) put("v", JSONArray().apply { m.versions.forEach { put(movieJson(it)) } }) }

    private fun movieOf(o: JSONObject): Movie? {
        val id = o.optInt("id")
        if (id <= 0) return null
        return Movie(
            streamId = id,
            name = o.optString("name"),
            poster = o.optString("poster"),
            categoryId = o.optString("cat"),
            rating = o.optString("rating"),
            ext = o.optString("ext"),
            added = o.optLong("added"),
            quality = quality(o.optString("q")),
            versions = listOf(o.optJSONArray("v")) { movieOf(it) },
        )
    }

    private fun seriesJson(s: Series): JSONObject = JSONObject()
        .put("id", s.seriesId).put("name", s.name).put("cover", s.cover).put("cat", s.categoryId)
        .put("plot", s.plot.take(400)).put("genre", s.genre).put("rating", s.rating).put("year", s.year)
        .put("updated", s.updated).put("q", s.quality.name)
        .apply { if (s.versions.isNotEmpty()) put("v", JSONArray().apply { s.versions.forEach { put(seriesJson(it)) } }) }

    private fun seriesOf(o: JSONObject): Series? {
        val id = o.optInt("id")
        if (id <= 0) return null
        return Series(
            seriesId = id,
            name = o.optString("name"),
            cover = o.optString("cover"),
            categoryId = o.optString("cat"),
            plot = o.optString("plot"),
            genre = o.optString("genre"),
            rating = o.optString("rating"),
            year = o.optString("year"),
            updated = o.optLong("updated"),
            quality = quality(o.optString("q")),
            versions = listOf(o.optJSONArray("v")) { seriesOf(it) },
        )
    }

    private fun channelJson(c: Channel): JSONObject = JSONObject()
        .put("id", c.streamId).put("num", c.num).put("name", c.name).put("icon", c.icon).put("cat", c.categoryId)
        .put("epg", c.epgId).put("arch", c.hasArchive).put("days", c.archiveDays)

    private fun channelOf(o: JSONObject): Channel? {
        val id = o.optInt("id")
        if (id <= 0) return null
        return Channel(
            streamId = id,
            num = o.optInt("num"),
            name = o.optString("name"),
            icon = o.optString("icon"),
            categoryId = o.optString("cat"),
            epgId = o.optString("epg"),
            hasArchive = o.optBoolean("arch"),
            archiveDays = o.optInt("days"),
        )
    }
}
