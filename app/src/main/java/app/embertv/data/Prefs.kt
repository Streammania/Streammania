package app.embertv.data

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject

/**
 * Everything the app remembers, stored on the device only. Login and playback settings are shared;
 * favourites, history and resume points belong to the active profile ([profileId]).
 */
class Prefs(context: Context) {
    private val sp = context.getSharedPreferences("ember", Context.MODE_PRIVATE)

    /** Which profile per-profile data is read from and written to. */
    var profileId: String = MAIN_PROFILE

    // The main profile uses the original key names, so data from before profiles carries over.
    private fun pk(base: String): String = pkFor(profileId, base)

    private fun pkFor(id: String, base: String): String = if (id == MAIN_PROFILE) base else "p_${id}_$base"

    /** Channels this profile has hidden from the lists, guide and channel up/down. */
    var hiddenChannels: Set<Int>
        get() {
            val raw = sp.getString(pk("hidden_channels"), null) ?: return emptySet()
            return try {
                val arr = JSONArray(raw)
                (0 until arr.length()).map { arr.getInt(it) }.toSet()
            } catch (e: Exception) {
                emptySet()
            }
        }
        set(value) {
            val arr = JSONArray()
            for (id in value) arr.put(id)
            sp.edit().putString(pk("hidden_channels"), arr.toString()).apply()
        }

    /** Football clubs this profile follows, for goal alerts and the Home card. */
    var followedTeams: List<Team>
        get() {
            val raw = sp.getString(pk("teams"), null) ?: return emptyList()
            return try {
                JSONArray(raw).objects().map { o ->
                    Team(o.optString("id"), o.optString("name"), o.optString("short"), o.optString("logo"), o.optString("league"))
                }
            } catch (e: Exception) {
                emptyList()
            }
        }
        set(value) {
            val arr = JSONArray()
            for (t in value) {
                val o = JSONObject()
                o.put("id", t.id)
                o.put("name", t.name)
                o.put("short", t.shortName)
                o.put("logo", t.logo)
                o.put("league", t.league)
                arr.put(o)
            }
            sp.edit().putString(pk("teams"), arr.toString()).apply()
        }

    var reminders: List<Reminder>
        get() {
            val raw = sp.getString(pk("reminders"), null) ?: return emptyList()
            return try {
                JSONArray(raw).objects().map { o ->
                    Reminder(
                        streamId = o.optInt("stream"),
                        channelName = o.optString("channel"),
                        channelIcon = o.optString("icon"),
                        categoryId = o.optString("category"),
                        num = o.optInt("num"),
                        title = o.optString("title"),
                        start = o.optLong("start"),
                        end = o.optLong("end"),
                    )
                }
            } catch (e: Exception) {
                emptyList()
            }
        }
        set(value) {
            val arr = JSONArray()
            for (r in value) {
                val o = JSONObject()
                o.put("stream", r.streamId)
                o.put("channel", r.channelName)
                o.put("icon", r.channelIcon)
                o.put("category", r.categoryId)
                o.put("num", r.num)
                o.put("title", r.title)
                o.put("start", r.start)
                o.put("end", r.end)
                arr.put(o)
            }
            sp.edit().putString(pk("reminders"), arr.toString()).apply()
        }

    fun categoryRules(id: String): CategoryRules {
        val raw = sp.getString(pkFor(id, "category_rules"), null) ?: return CategoryRules(false, emptySet())
        return try {
            val o = JSONObject(raw)
            val arr = o.optJSONArray("keys") ?: JSONArray()
            val keys = HashSet<String>()
            for (i in 0 until arr.length()) keys.add(arr.optString(i))
            CategoryRules(o.optBoolean("allow"), keys)
        } catch (e: Exception) {
            CategoryRules(false, emptySet())
        }
    }

    fun setCategoryRules(id: String, rules: CategoryRules) {
        val o = JSONObject()
        o.put("allow", rules.allowMode)
        val arr = JSONArray()
        for (k in rules.keys) arr.put(k)
        o.put("keys", arr)
        sp.edit().putString(pkFor(id, "category_rules"), o.toString()).apply()
    }

    var profiles: List<Profile>
        get() {
            val raw = sp.getString("profiles", "[]") ?: "[]"
            return try {
                val arr = JSONArray(raw)
                val out = ArrayList<Profile>()
                for (i in 0 until arr.length()) {
                    val o = arr.optJSONObject(i) ?: continue
                    val id = o.optString("id")
                    if (id.isNotEmpty()) {
                        out.add(Profile(id, o.optString("name"), o.optInt("color"), o.optBoolean("kids"), o.optString("pin")))
                    }
                }
                out
            } catch (e: Exception) {
                emptyList()
            }
        }
        set(value) {
            val arr = JSONArray()
            for (p in value) {
                val o = JSONObject()
                o.put("id", p.id)
                o.put("name", p.name)
                o.put("color", p.color)
                o.put("kids", p.kids)
                o.put("pin", p.pinHash)
                arr.put(o)
            }
            sp.edit().putString("profiles", arr.toString()).apply()
        }

    var lastProfile: String?
        get() = sp.getString("last_profile", null)
        set(value) {
            sp.edit().putString("last_profile", value).apply()
        }

    /** Forgets everything saved for one profile. */
    fun deleteProfileData(id: String) {
        val e = sp.edit()
        for (key in sp.all.keys) {
            val belongs = if (id == MAIN_PROFILE) {
                key in setOf("favourites", "history", "recent_channels", "audio_lang", "subs", "reminders", "category_rules", "teams", "watched", "hidden_channels", "sport_leagues", "sport_others", "series_seen", "last_watched", "home_order", "home_hidden", "backdrop", "text_scale", "show_hints", "autoplay_next", "kickoff_reminders", "finished_at", "match_reminders", "home_artwork") ||
                    key.startsWith("pos_") || key.startsWith("last_ep_")
            } else {
                key.startsWith("p_${id}_")
            }
            if (belongs) e.remove(key)
        }
        e.apply()
    }

    /** The server and username last signed in with, to fill the boxes after signing out. Never the password. */
    var lastServer: String
        get() = sp.getString("last_server", "") ?: ""
        set(value) {
            sp.edit().putString("last_server", value).apply()
        }
    var lastUser: String
        get() = sp.getString("last_user", "") ?: ""
        set(value) {
            sp.edit().putString("last_user", value).apply()
        }

    var account: Account?
        get() {
            val server = sp.getString("server", null) ?: return null
            return Account(server, sp.getString("user", "") ?: "", sp.getString("pass", "") ?: "")
        }
        set(value) {
            val e = sp.edit()
            if (value == null) {
                e.remove("server")
                e.remove("user")
                e.remove("pass")
            } else {
                e.putString("server", value.server)
                e.putString("user", value.username)
                e.putString("pass", value.password)
            }
            e.apply()
        }

    var streamFormat: String
        get() = sp.getString("format", "ts") ?: "ts"
        set(value) {
            sp.edit().putString("format", value).apply()
        }

    var userAgent: String
        get() = (sp.getString("ua", DEFAULT_USER_AGENT) ?: DEFAULT_USER_AGENT)
            .let { if (it == OLD_DEFAULT_USER_AGENT) DEFAULT_USER_AGENT else it }
        set(value) {
            sp.edit().putString("ua", value).apply()
        }

    /** Package of another video app to play everything in (VLC, MX Player…); empty for the built-in player. */
    var externalPlayer: String
        get() = sp.getString("external_player", "") ?: ""
        set(value) {
            sp.edit().putString("external_player", value).apply()
        }

    /** Films and episodes this profile has watched to the end ("movie:12", "ep:34"). */
    var watched: Set<String>
        get() {
            val raw = sp.getString(pk("watched"), null) ?: return emptySet()
            return try {
                val arr = JSONArray(raw)
                (0 until arr.length()).map { arr.optString(it) }.filter { it.isNotEmpty() }.toCollection(LinkedHashSet())
            } catch (e: Exception) {
                emptySet()
            }
        }
        set(value) {
            val arr = JSONArray()
            for (k in value) arr.put(k)
            sp.edit().putString(pk("watched"), arr.toString()).apply()
        }

    /** Phones allowed to control this TV (tokens handed out when pairing). Kept on this device only. */
    var tvTokens: Set<String>
        get() = sp.getStringSet("tv_tokens", emptySet())?.toSet() ?: emptySet()
        set(value) {
            sp.edit().putStringSet("tv_tokens", value).apply()
        }

    /** On a phone: the TVs it has paired with, as JSON (name, host, port, token). */
    var pairedTvs: String
        get() = sp.getString("paired_tvs", "[]") ?: "[]"
        set(value) {
            sp.edit().putString("paired_tvs", value).apply()
        }

    /** The user's own TMDB key, for cast photos, trailers and suggestions. Empty when not set. */
    var tmdbKey: String
        get() = sp.getString("tmdb_key", "") ?: ""
        set(value) {
            sp.edit().putString("tmdb_key", value).apply()
        }

    /** The football competitions shown first in Live Sport Now (ESPN codes). English football to start with. */
    var sportLeagues: Set<String>
        get() = sp.getStringSet(pk("sport_leagues"), null)?.toSet() ?: DEFAULT_SPORT_LEAGUES
        set(value) {
            sp.edit().putStringSet(pk("sport_leagues"), value).apply()
        }

    /** Also show other competitions after your own in Live Sport Now. */
    var sportOthers: Boolean
        get() = sp.getBoolean(pk("sport_others"), true)
        set(value) {
            sp.edit().putBoolean(pk("sport_others"), value).apply()
        }

    /** Show the Live Sport Now row on Home. */
    var sportOnHome: Boolean
        get() = sp.getBoolean("sport_on_home", true)
        set(value) {
            sp.edit().putBoolean("sport_on_home", value).apply()
        }

    /** Slimmer rows in the TV guide and channel list, so more channels fit. */
    var compactLists: Boolean
        get() = sp.getBoolean("compact_lists", true)
        set(value) {
            sp.edit().putBoolean("compact_lists", value).apply()
        }

    /** Play a small muted picture of the highlighted channel while browsing on a TV. */
    var livePreview: Boolean
        get() = sp.getBoolean("live_preview", true)
        set(value) {
            sp.edit().putBoolean("live_preview", value).apply()
        }

    /** Show each film or show once with its HD/FHD/4K copies as versions. */
    var mergeVersions: Boolean
        get() = sp.getBoolean("merge_versions", true)
        set(value) {
            sp.edit().putBoolean("merge_versions", value).apply()
        }

    /** Which version to play: "ask", "best", "FHD" or "HD". */
    var qualityChoice: String
        get() = sp.getString("quality_choice", "ask") ?: "ask"
        set(value) {
            sp.edit().putString("quality_choice", value).apply()
        }

    /** 4K streams that don't say they're HDR: "auto", "hlg", "hdr10" or "off". */
    var hdrHint: String
        get() = sp.getString("hdr_hint", "auto") ?: "auto"
        set(value) {
            sp.edit().putString("hdr_hint", value).apply()
        }

    /** Switch the TV's refresh rate to match what's playing. */
    var matchFrameRate: Boolean
        get() = sp.getBoolean("match_frame_rate", true)
        set(value) {
            sp.edit().putBoolean("match_frame_rate", value).apply()
        }

    /** How far back live TV can be rewound, in minutes; 0 turns pause and rewind off. */
    var timeshiftMinutes: Int
        get() = sp.getInt("timeshift_min", 30)
        set(value) {
            sp.edit().putInt("timeshift_min", value).apply()
        }

    /** How the picture fills the screen: 0 fit (black bars), 1 zoom (fills, trims the edges), 2 stretch. */
    var pictureMode: Int
        get() = sp.getInt("picture_mode", 0)
        set(value) {
            sp.edit().putInt("picture_mode", value).apply()
        }

    /** What the app opens on: "home", "last" (the last channel watched) or "guide". */
    var startOn: String
        get() = sp.getString("start_on", "home") ?: "home"
        set(value) {
            sp.edit().putString("start_on", value).apply()
        }

    /** Minutes of no remote presses before the TV screensaver starts; 0 turns it off. */
    var screensaverMinutes: Int
        get() = sp.getInt("screensaver_min", 3)
        set(value) {
            sp.edit().putInt("screensaver_min", value).apply()
        }

    var invertZap: Boolean
        get() = sp.getBoolean("invert_zap", false)
        set(value) {
            sp.edit().putBoolean("invert_zap", value).apply()
        }

    var favourites: List<Favourite>
        get() = decodeFavourites(sp.getString(pk("favourites"), "[]"))
        set(value) {
            sp.edit().putString(pk("favourites"), encodeFavourites(value)).apply()
        }

    /** Channels this profile watched recently, newest first. */
    var recentChannels: List<Favourite>
        get() = decodeFavourites(sp.getString(pk("recent_channels"), "[]"))
        set(value) {
            sp.edit().putString(pk("recent_channels"), encodeFavourites(value)).apply()
        }

    /** Preferred audio language code, or "" for the stream's default. */
    var audioLanguage: String
        get() = sp.getString(pk("audio_lang"), "") ?: ""
        set(value) {
            sp.edit().putString(pk("audio_lang"), value).apply()
        }

    /** "off", or a language code such as "en" to switch subtitles on in that language. */
    var subtitleMode: String
        get() = sp.getString(pk("subs"), "off") ?: "off"
        set(value) {
            sp.edit().putString(pk("subs"), value).apply()
        }

    private fun decodeFavourites(raw: String?): List<Favourite> = try {
        val arr = JSONArray(raw ?: "[]")
        val out = ArrayList<Favourite>()
        for (i in 0 until arr.length()) {
            val o = arr.optJSONObject(i) ?: continue
            val type = ContentType.entries.firstOrNull { it.name == o.optString("type") } ?: continue
            out.add(
                Favourite(
                    type = type,
                    id = o.optInt("id"),
                    name = o.optString("name"),
                    image = o.optString("image"),
                    categoryId = o.optString("cat"),
                    ext = o.optString("ext"),
                    num = o.optInt("num"),
                )
            )
        }
        out
    } catch (e: Exception) {
        emptyList()
    }

    private fun encodeFavourites(list: List<Favourite>): String {
        val arr = JSONArray()
        for (f in list) {
            val o = JSONObject()
            o.put("type", f.type.name)
            o.put("id", f.id)
            o.put("name", f.name)
            o.put("image", f.image)
            o.put("cat", f.categoryId)
            o.put("ext", f.ext)
            o.put("num", f.num)
            arr.put(o)
        }
        return arr.toString()
    }

    var history: List<HistoryEntry>
        get() {
            val raw = sp.getString(pk("history"), "[]") ?: "[]"
            return try {
                val arr = JSONArray(raw)
                val out = ArrayList<HistoryEntry>()
                for (i in 0 until arr.length()) {
                    val o = arr.optJSONObject(i) ?: continue
                    val type = ContentType.entries.firstOrNull { it.name == o.optString("type") } ?: continue
                    out.add(
                        HistoryEntry(
                            key = o.optString("key"),
                            type = type,
                            id = o.optInt("id"),
                            seriesId = o.optInt("sid"),
                            title = o.optString("title"),
                            subtitle = o.optString("sub"),
                            image = o.optString("img"),
                            seriesCover = o.optString("cover"),
                            ext = o.optString("ext"),
                            positionMs = o.optLong("pos"),
                            durationMs = o.optLong("dur"),
                            updatedAt = o.optLong("at"),
                        )
                    )
                }
                out
            } catch (e: Exception) {
                emptyList()
            }
        }
        set(value) {
            val arr = JSONArray()
            for (h in value) {
                val o = JSONObject()
                o.put("key", h.key)
                o.put("type", h.type.name)
                o.put("id", h.id)
                o.put("sid", h.seriesId)
                o.put("title", h.title)
                o.put("sub", h.subtitle)
                o.put("img", h.image)
                o.put("cover", h.seriesCover)
                o.put("ext", h.ext)
                o.put("pos", h.positionMs)
                o.put("dur", h.durationMs)
                o.put("at", h.updatedAt)
                arr.put(o)
            }
            sp.edit().putString(pk("history"), arr.toString()).apply()
        }

    fun position(key: String): Long = sp.getLong(pk("pos_$key"), 0L)

    /** Keeps a resume point unless the video was barely started or is basically finished. */
    fun savePosition(key: String, positionMs: Long, durationMs: Long) {
        val finished = durationMs > 0 && positionMs.toDouble() > durationMs.toDouble() * 0.95
        val e = sp.edit()
        val k = pk("pos_$key")
        if (positionMs < 60_000L || finished) e.remove(k) else e.putLong(k, positionMs)
        e.apply()
    }

    fun lastEpisode(seriesId: Int): Int? = sp.getInt(pk("last_ep_$seriesId"), -1).takeIf { it >= 0 }

    fun setLastEpisode(seriesId: Int, episodeId: Int) {
        sp.edit().putInt(pk("last_ep_$seriesId"), episodeId).apply()
    }

    /** When this profile last watched each series (epoch ms), for New Episodes on Home. */
    var seriesSeen: Map<Int, Long>
        get() {
            val raw = sp.getString(pk("series_seen"), null) ?: return emptyMap()
            return try {
                val o = JSONObject(raw)
                o.keys().asSequence().mapNotNull { k -> k.toIntOrNull()?.let { it to o.optLong(k) } }.toMap()
            } catch (e: Exception) {
                emptyMap()
            }
        }
        set(value) {
            val o = JSONObject()
            for ((k, v) in value) o.put(k.toString(), v)
            sp.edit().putString(pk("series_seen"), o.toString()).apply()
        }

    /** The last film ("m:<id>") or series ("s:<id>") watched, for Because You Watched on Home. */
    var lastWatched: String
        get() = sp.getString(pk("last_watched"), "") ?: ""
        set(value) {
            sp.edit().putString(pk("last_watched"), value).apply()
        }

    /** Home's rows in this profile's order; rows not listed go in their usual place. */
    var homeOrder: List<String>
        get() {
            val raw = sp.getString(pk("home_order"), null) ?: return emptyList()
            return try {
                val arr = JSONArray(raw)
                (0 until arr.length()).map { arr.optString(it) }.filter { it.isNotEmpty() }
            } catch (e: Exception) {
                emptyList()
            }
        }
        set(value) {
            sp.edit().putString(pk("home_order"), JSONArray().apply { value.forEach { put(it) } }.toString()).apply()
        }

    /** Whether a channel's quality copies (FHD, HD, SD) are shown as one. */
    var channelsOnce: Boolean
        get() = sp.getBoolean("channels_once", true)
        set(value) {
            sp.edit().putBoolean("channels_once", value).apply()
        }

    /** How the Movies and Series grids are sorted: provider, newest, az, rating or year. */
    var movieSort: String
        get() = sp.getString("movie_sort", "provider") ?: "provider"
        set(value) {
            sp.edit().putString("movie_sort", value).apply()
        }

    var seriesSort: String
        get() = sp.getString("series_sort", "provider") ?: "provider"
        set(value) {
            sp.edit().putString("series_sort", value).apply()
        }

    /** This profile's background colours (an id from the Background list). */
    var backdrop: String
        get() = sp.getString(pk("backdrop"), "warm") ?: "warm"
        set(value) {
            sp.edit().putString(pk("backdrop"), value).apply()
        }

    /** This profile's text size (1 is normal). */
    var textScale: Float
        get() = sp.getInt(pk("text_scale"), 100) / 100f
        set(value) {
            sp.edit().putInt(pk("text_scale"), (value * 100).toInt()).apply()
        }

    /** Whether this profile sees the little remote-button hints. */
    var showHints: Boolean
        get() = sp.getBoolean(pk("show_hints"), true)
        set(value) {
            sp.edit().putBoolean(pk("show_hints"), value).apply()
        }

    /** Phones: which way the app faces. 0 turns with the phone, 1 always upright, 2 always sideways. */
    var screenDirection: Int
        get() = sp.getInt("screen_direction", 0)
        set(value) {
            sp.edit().putInt("screen_direction", value).apply()
        }

    /** Remember the quality picked for each channel and open it that way next time (off unless turned on). */
    var rememberChannelQuality: Boolean
        get() = sp.getBoolean(pk("remember_channel_quality"), false)
        set(value) {
            sp.edit().putBoolean(pk("remember_channel_quality"), value).apply()
        }

    /** The quality tag ("FHD", "HD"…, or "" for plain) last picked for each channel, by its clean name. */
    fun channelQuality(name: String): String? =
        runCatching { JSONObject(sp.getString(pk("channel_quality"), "{}") ?: "{}").optString(name, "").takeIf { it.isNotEmpty() } }.getOrNull()

    fun setChannelQuality(name: String, tag: String) {
        val o = runCatching { JSONObject(sp.getString(pk("channel_quality"), "{}") ?: "{}") }.getOrElse { JSONObject() }
        o.put(name, tag)
        sp.edit().putString(pk("channel_quality"), o.toString()).apply()
    }

    fun clearChannelQualities() {
        sp.edit().remove(pk("channel_quality")).apply()
    }

    /** Whether Home's background fades to the highlighted film's or series' artwork (TV). */
    var homeArtwork: Boolean
        get() = sp.getBoolean(pk("home_artwork"), true)
        set(value) {
            sp.edit().putBoolean(pk("home_artwork"), value).apply()
        }

    /** Games (by id) this profile asked to be reminded about, whoever's playing. */
    var matchReminders: Set<String>
        get() = sp.getStringSet(pk("match_reminders"), null)?.toSet() ?: emptySet()
        set(value) {
            sp.edit().putStringSet(pk("match_reminders"), value).apply()
        }

    /** Whether a card pops up just before one of this profile's teams kicks off. */
    var kickoffReminders: Boolean
        get() = sp.getBoolean(pk("kickoff_reminders"), true)
        set(value) {
            sp.edit().putBoolean(pk("kickoff_reminders"), value).apply()
        }

    /** Whether the next episode starts by itself after a short countdown. */
    var autoplayNext: Boolean
        get() = sp.getBoolean(pk("autoplay_next"), true)
        set(value) {
            sp.edit().putBoolean(pk("autoplay_next"), value).apply()
        }

    /** Home rows this profile has turned off. */
    var homeHidden: Set<String>
        // Until you arrange it yourself, Home starts calm: six rows, the rest one tap away in Arrange Home.
        get() = sp.getStringSet(pk("home_hidden"), null)?.toSet() ?: DEFAULT_HOME_HIDDEN
        set(value) {
            sp.edit().putStringSet(pk("home_hidden"), value).apply()
        }

    // ---- Copying between devices on the home network ----

    /** Settings that belong to this device or account and never leave it. */
    private fun keepsLocal(key: String): Boolean =
        key in DEVICE_ONLY || isProgressKey(key)

    private fun isProgressKey(key: String): Boolean {
        val base = if (key.startsWith("p_")) key.substringAfter('_').substringAfter('_') else key
        return base == "history" || base == "watched" || base == "finished_at" || base.startsWith("pos_") || base.startsWith("last_ep_")
    }

    /** Every copyable setting, with its type, so another device can take it as it is. */
    fun exportSettings(): JSONObject {
        val o = JSONObject()
        for ((k, v) in sp.all) {
            if (keepsLocal(k) || v == null) continue
            val t = when (v) {
                is String -> JSONObject().put("t", "s").put("v", v)
                is Boolean -> JSONObject().put("t", "b").put("v", v)
                is Int -> JSONObject().put("t", "i").put("v", v)
                is Long -> JSONObject().put("t", "l").put("v", v)
                is Float -> JSONObject().put("t", "f").put("v", v.toDouble())
                is Set<*> -> JSONObject().put("t", "set").put("v", JSONArray(v.map { it.toString() }))
                else -> null
            } ?: continue
            o.put(k, t)
        }
        return JSONObject().put("settings", o).put("version", 1)
    }

    /** Takes another device's settings. This device's sign-in, pairings and progress are left alone. */
    fun importSettings(from: JSONObject): Boolean {
        val o = from.optJSONObject("settings") ?: return false
        val e = sp.edit()
        // Settings the other device has at their default are cleared here too, so both end up the same.
        for (k in sp.all.keys) if (!keepsLocal(k) && !o.has(k)) e.remove(k)
        for (k in o.keys()) {
            if (keepsLocal(k)) continue
            val item = o.optJSONObject(k) ?: continue
            when (item.optString("t")) {
                "s" -> e.putString(k, item.optString("v"))
                "b" -> e.putBoolean(k, item.optBoolean("v"))
                "i" -> e.putInt(k, item.optInt("v"))
                "l" -> e.putLong(k, item.optLong("v"))
                "f" -> e.putFloat(k, item.optDouble("v").toFloat())
                "set" -> {
                    val arr = item.optJSONArray("v") ?: JSONArray()
                    e.putStringSet(k, (0 until arr.length()).map { arr.optString(it) }.toSet())
                }
            }
        }
        e.commit()
        return true
    }

    /** Continue Watching and what's been finished, for every profile. */
    fun exportProgress(): JSONObject {
        val profilesOut = JSONObject()
        for (id in progressProfileIds()) {
            profilesOut.put(
                id,
                JSONObject()
                    .put("history", runCatching { JSONArray(sp.getString(pkFor(id, "history"), "[]") ?: "[]") }.getOrElse { JSONArray() })
                    .put("watched", runCatching { JSONArray(sp.getString(pkFor(id, "watched"), "[]") ?: "[]") }.getOrElse { JSONArray() })
                    .put("finished", finishedJson(id))
            )
        }
        return JSONObject().put("profiles", profilesOut)
    }

    private fun finishedJson(id: String): JSONObject =
        runCatching { JSONObject(sp.getString(pkFor(id, "finished_at"), "{}") ?: "{}") }.getOrElse { JSONObject() }

    /** Remembers when a film or episode was finished, so another device's older progress on it is dropped. */
    fun noteFinished(key: String) {
        val o = finishedJson(profileId)
        o.put(key, System.currentTimeMillis())
        // Only recent ones matter: keep the newest 300.
        if (o.length() > 300) {
            val keep = o.keys().asSequence().map { it to o.optLong(it) }.sortedByDescending { it.second }.take(300).toList()
            val n = JSONObject()
            for ((k, v) in keep) n.put(k, v)
            sp.edit().putString(pk("finished_at"), n.toString()).apply()
            return
        }
        sp.edit().putString(pk("finished_at"), o.toString()).apply()
    }

    private fun progressProfileIds(): Set<String> {
        val ids = linkedSetOf(MAIN_PROFILE)
        for (k in sp.all.keys) {
            if (k.startsWith("p_") && (k.endsWith("_history") || k.endsWith("_watched"))) ids.add(k.removePrefix("p_").substringBefore('_'))
        }
        return ids
    }

    /**
     * Merges another device's progress into this one: for each film or series, whichever device
     * watched it most recently wins, so pausing on the TV and opening the phone carries on there.
     * Something finished on one device drops off the other's Continue Watching.
     */
    fun mergeProgress(remote: JSONObject) {
        val profilesIn = remote.optJSONObject("profiles") ?: return
        val e = sp.edit()
        for (id in profilesIn.keys()) {
            val theirs = profilesIn.optJSONObject(id) ?: continue
            val mineHistory = runCatching { JSONArray(sp.getString(pkFor(id, "history"), "[]") ?: "[]") }.getOrElse { JSONArray() }
            val mineWatched = runCatching { JSONArray(sp.getString(pkFor(id, "watched"), "[]") ?: "[]") }.getOrElse { JSONArray() }
            val theirHistory = theirs.optJSONArray("history") ?: JSONArray()
            val theirWatched = theirs.optJSONArray("watched") ?: JSONArray()
            fun keys(a: JSONArray, field: String?) = (0 until a.length()).mapNotNull {
                if (field == null) a.optString(it).ifEmpty { null } else a.optJSONObject(it)?.optString(field)?.ifEmpty { null }
            }.toSet()
            val mineDone = keys(mineWatched, null)
            val theirDone = keys(theirWatched, null)
            // When each was last finished, on either device. Progress older than that is stale.
            val finished = finishedJson(id)
            val theirFinished = theirs.optJSONObject("finished") ?: JSONObject()
            for (k in theirFinished.keys()) if (theirFinished.optLong(k) > finished.optLong(k)) finished.put(k, theirFinished.optLong(k))
            val drop = HashSet<String>()
            val all = ArrayList<JSONObject>()
            for (a in listOf(mineHistory, theirHistory)) for (i in 0 until a.length()) a.optJSONObject(i)?.let { all.add(it) }
            val seen = HashSet<String>()
            val merged = all
                .filter { h ->
                    val done = finished.optLong(h.optString("key"))
                    val stale = done > 0 && done >= h.optLong("at")
                    if (stale) drop.add(h.optString("key"))
                    !stale
                }
                .sortedByDescending { it.optLong("at") }
                .filter { h ->
                    val group = if (h.optString("type") == ContentType.SERIES.name && h.optInt("sid") > 0) "s:${h.optInt("sid")}" else h.optString("key")
                    seen.add(group)
                }
                .take(30)
            val out = JSONArray()
            for (h in merged) {
                out.put(h)
                val key = h.optString("key")
                val pos = h.optLong("pos")
                if (pos >= 60_000L) e.putLong(pkFor(id, "pos_$key"), pos)
                val sid = h.optInt("sid")
                if (h.optString("type") == ContentType.SERIES.name && sid > 0) e.putInt(pkFor(id, "last_ep_$sid"), h.optInt("id"))
            }
            for (key in drop) if (merged.none { it.optString("key") == key }) e.remove(pkFor(id, "pos_$key"))
            e.putString(pkFor(id, "finished_at"), finished.toString())
            e.putString(pkFor(id, "history"), out.toString())
            val watchedOut = JSONArray()
            for (w in (mineDone + theirDone).toList().takeLast(3000)) watchedOut.put(w)
            e.putString(pkFor(id, "watched"), watchedOut.toString())
        }
        e.commit()
    }
}

/** Kept on each device: sign-in, pairings and how this screen is held. */
private val DEVICE_ONLY = setOf("server", "user", "pass", "paired_tvs", "tv_tokens", "screen_direction", "last_profile", "match_frame_rate", "hdr_hint", "picture_mode", "external_player", "last_crash", "last_server", "last_user")

/** Rows Home leaves off until switched on: Home starts with six rows instead of eleven. */
val DEFAULT_HOME_HIDDEN = setOf("recent-channels", "fav-channels", "live", "movies", "series")

/** English football: the Premier League, the EFL and the domestic cups. */
val DEFAULT_SPORT_LEAGUES = setOf("eng.1", "eng.2", "eng.3", "eng.4", "eng.fa", "eng.league_cup")
