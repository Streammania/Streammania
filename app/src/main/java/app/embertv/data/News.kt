package app.embertv.data

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.net.HttpURLConnection
import java.net.URL

/** The top news stories, from BBC News's public headline feed. Only the headlines are read. */
object News {
    private const val FEED = "https://feeds.bbci.co.uk/news/rss.xml"

    /** Up to [count] headlines, top story first; null if the feed couldn't be fetched. */
    suspend fun headlines(count: Int = 6): List<String>? = withContext(Dispatchers.IO) {
        try {
            val c = URL(FEED).openConnection() as HttpURLConnection
            c.connectTimeout = 10_000
            c.readTimeout = 15_000
            val xml = try {
                if (c.responseCode !in 200..299) return@withContext null
                c.inputStream.bufferedReader().use { it.readText() }
            } finally {
                c.disconnect()
            }
            Regex("<item>(.*?)</item>", RegexOption.DOT_MATCHES_ALL).findAll(xml)
                .mapNotNull { item ->
                    Regex("<title>(.*?)</title>", RegexOption.DOT_MATCHES_ALL).find(item.groupValues[1])?.groupValues?.get(1)?.let(::clean)
                }
                .filter { it.isNotBlank() }
                .distinct()
                .take(count)
                .toList()
        } catch (e: Exception) {
            null
        }
    }

    private fun clean(raw: String): String = raw.trim()
        .removePrefix("<![CDATA[").removeSuffix("]]>")
        .replace("&amp;", "&").replace("&quot;", "\"").replace("&#39;", "'").replace("&apos;", "'")
        .replace("&lt;", "<").replace("&gt;", ">")
        .replace(Regex("\\s+"), " ")
        .trim()
}
