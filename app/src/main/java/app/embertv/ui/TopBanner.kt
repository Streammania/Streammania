package app.embertv.ui

import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathOperation
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.embertv.data.Channel
import app.embertv.data.ContentType
import app.embertv.data.Match
import app.embertv.data.MatchState
import app.embertv.data.Movie
import app.embertv.data.Programme
import app.embertv.data.Repository
import app.embertv.data.TeamScore
import app.embertv.data.Weather
import app.embertv.data.WeatherNow
import app.embertv.data.WeatherPlace
import app.embertv.data.toChannel
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

/** What the banner at the top of Home can show. */
object BannerModes {
    const val AUTO = "auto"
    const val CLOCK = "clock"
    const val TEAMS = "teams"
    const val HOLIDAY = "holiday"
    const val RESUME = "resume"
    const val OFF = "off"

    /** Each choice with its name and a line about it, in the order the picker lists them. */
    val ALL = listOf(
        Triple(AUTO, "Automatic", "Your teams when they're playing, what you were just watching, holiday films in season, and the clock and weather the rest of the time"),
        Triple(CLOCK, "Clock & weather", "A big clock, the weather where you are, the news and what's on now"),
        Triple(RESUME, "Pick up where you left off", "What you were last watching, ready to carry on"),
        Triple(TEAMS, "Your teams", "Live scores, where to watch and their next games"),
        Triple(HOLIDAY, "Holiday", "Christmas films from mid-November, Halloween in October; the clock at other times"),
        Triple(OFF, "Off", "No banner: Home starts with your rows"),
    )

    fun name(id: String): String = ALL.firstOrNull { it.first == id }?.second ?: "Automatic"
}

/** The holidays the banner knows, how to find their films, and when they're in season. */
enum class Holiday(
    val title: String,
    val eyebrow: String,
    val kind: String,
    val search: String,
    val groupWords: Regex,
    val titleWords: Regex,
) {
    CHRISTMAS(
        "Christmas Films",
        "CHRISTMAS ON STREAMMANIA",
        "festive",
        "Christmas",
        Regex("(?i)christmas|xmas|x-mas|festive"),
        Regex("(?i)christmas|xmas|x-mas|\\bsanta\\b|grinch|nativity|jingle|sleigh|reindeer|\\belf\\b|\\bnoel\\b"),
    ),
    HALLOWEEN(
        "Halloween Films",
        "HALLOWEEN ON STREAMMANIA",
        "spooky",
        "Halloween",
        Regex("(?i)halloween|horror"),
        Regex("(?i)halloween|haunt|\\bwitch|ghost|spooky|zombie"),
    );

    companion object {
        /** In season today: Christmas from 15 November to New Year's Day, Halloween all October. */
        fun now(): Holiday? {
            val c = Calendar.getInstance()
            val month = c.get(Calendar.MONTH)
            val day = c.get(Calendar.DAY_OF_MONTH)
            return when {
                month == Calendar.DECEMBER -> CHRISTMAS
                month == Calendar.NOVEMBER && day >= 15 -> CHRISTMAS
                month == Calendar.JANUARY && day == 1 -> CHRISTMAS
                month == Calendar.OCTOBER -> HALLOWEEN
                else -> null
            }
        }
    }
}

private enum class BannerKind { CLOCK, TEAMS, HOLIDAY, RESUME }

/**
 * What you were last watching (a film or an episode, or the next episode once you've finished
 * one). [recentOnly] (Automatic) only takes something from the last 12 hours.
 */
private fun resumeEntry(vm: AppViewModel, recentOnly: Boolean): app.embertv.data.HistoryEntry? {
    val e = vm.history.firstOrNull { it.type == ContentType.MOVIE || it.type == ContentType.SERIES } ?: return null
    if (e.durationMs > 0 && e.progress > 0.95f) return null
    if (recentOnly && System.currentTimeMillis() - e.updatedAt > 12 * 3_600_000L) return null
    return e
}

/**
 * The game for the banner: a live one first, then the next to kick off, then the last result.
 * [soonOnly] (Automatic) only takes a live game or one kicking off in the next three hours.
 */
private fun bannerMatch(vm: AppViewModel, soonOnly: Boolean): Match? {
    val nowMs = System.currentTimeMillis()
    val games = vm.teamMatches.filter { vm.isFollowedMatch(it) }
    games.firstOrNull { it.state == MatchState.LIVE }?.let { return it }
    val next = games.filter { it.state == MatchState.UPCOMING && it.kickoff > nowMs - 15 * 60_000L }.minByOrNull { it.kickoff }
    if (soonOnly) return next?.takeIf { it.kickoff - nowMs < 3 * 3_600_000L }
    return next ?: games.filter { it.state == MatchState.FINISHED }.maxByOrNull { it.kickoff }
}

/** Which banner to show now, for the profile's choice; null when it's turned off. */
private fun bannerKind(vm: AppViewModel): BannerKind? = when (vm.bannerMode) {
    BannerModes.OFF -> null
    BannerModes.CLOCK -> BannerKind.CLOCK
    BannerModes.TEAMS -> BannerKind.TEAMS
    BannerModes.HOLIDAY -> if (Holiday.now() != null) BannerKind.HOLIDAY else BannerKind.CLOCK
    BannerModes.RESUME -> if (resumeEntry(vm, recentOnly = false) != null) BannerKind.RESUME else BannerKind.CLOCK
    else -> when {
        bannerMatch(vm, soonOnly = true) != null -> BannerKind.TEAMS
        resumeEntry(vm, recentOnly = true) != null -> BannerKind.RESUME
        Holiday.now() != null && (vm.holidayPicks?.films?.size ?: 0) >= 3 -> BannerKind.HOLIDAY
        else -> BannerKind.CLOCK
    }
}

/** The channel for "On now": your first favourite channel, or the last one you watched. */
private class OnNow(val channels: List<Channel>, val programme: Programme?)

@Composable
private fun rememberOnNow(vm: AppViewModel, repo: Repository): OnNow? {
    val favs = vm.favourites.filter { it.type == ContentType.LIVE }
    val source = favs.ifEmpty { vm.recentChannels.toList() }.take(40).map { it.toChannel() }
    val first = source.firstOrNull()
    val listings by produceState(first?.let { repo.cachedEpg(EpgAlias.of(it.streamId)) }, repo, first?.streamId) {
        if (first != null && value == null) {
            delay(600)
            value = try {
                repo.epg(EpgAlias.of(first.streamId))
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                null
            }
        }
    }
    if (first == null) return null
    val now = LocalNow.current
    return OnNow(source, listings?.firstOrNull { it.start <= now && it.end > now })
}

/** The time, kept to the minute, and today's date ("Tuesday 24 November"). */
@Composable
private fun ClockAndDate(clockStyle: TextStyle, dateStyle: TextStyle, center: Boolean) {
    val now by produceState(System.currentTimeMillis()) {
        while (true) {
            val t = System.currentTimeMillis()
            value = t
            delay(60_000L - t % 60_000L + 50L)
        }
    }
    val date = remember(now / 60_000L) { SimpleDateFormat("EEEE d MMMM", Locale.UK).format(Date(now)) }
    Column(horizontalAlignment = if (center) Alignment.CenterHorizontally else Alignment.Start) {
        Text(formatClock(now / 1000), style = clockStyle, color = Ember.Text, maxLines = 1)
        Text(date, style = dateStyle, color = Ember.Text.copy(alpha = 0.78f), maxLines = 1, modifier = Modifier.padding(start = if (center) 0.dp else 6.dp))
    }
}

/** The quiet hint under the banner's buttons. */
@Composable
private fun BannerHint(text: String, center: Boolean = false) {
    if (!HintsSetting.show) return
    Text(
        text,
        style = Txt.Caption,
        color = Ember.Faint,
        textAlign = if (center) TextAlign.Center else TextAlign.Start,
        modifier = Modifier.padding(top = 12.dp),
    )
}

// ---------------- TV ----------------

/** The banner at the top of Home on the TV: whichever the profile chose (see [BannerModes]). */
@Composable
fun TvTopBanner(vm: AppViewModel, repo: Repository) {
    // The holiday's films are only gathered when this banner could show them.
    if (vm.bannerMode == BannerModes.AUTO || vm.bannerMode == BannerModes.HOLIDAY) {
        LaunchedEffect(repo, vm.reloadToken, vm.profile?.id) { vm.ensureHoliday() }
    }
    var picking by remember { mutableStateOf(false) }
    if (picking) BannerPickerDialog(vm) { picking = false }
    val kind = bannerKind(vm)
    if (kind == null) {
        Spacer(Modifier.height(TV_MENU_SPACE))
        return
    }
    val scope = rememberCoroutineScope()
    Box(
        Modifier
            .fillMaxWidth()
            .heightIn(min = 310.dp + TV_MENU_SPACE)
            // Back on the banner: the page slides back to the top, as on Apple TV.
            .onFocusChanged { if (it.hasFocus) scope.launch { runCatching { vm.home.list("tv").animateScrollToItem(0) } } }
    ) {
        Crossfade(targetState = kind, label = "banner") { k ->
            when (k) {
                BannerKind.CLOCK -> TvClockBanner(vm, repo) { picking = true }
                BannerKind.TEAMS -> TvTeamsBanner(vm) { picking = true }
                BannerKind.HOLIDAY -> TvHolidayBanner(vm) { picking = true }
                BannerKind.RESUME -> TvResumeBanner(vm, repo) { picking = true }
            }
        }
    }
}

/** The soft colour behind a banner, fading into the page at the bottom. */
private fun Modifier.bannerGlow(vararg glows: Triple<Color, Float, Float>): Modifier = drawBehind {
    for ((color, x, y) in glows) {
        drawRect(
            Brush.radialGradient(
                listOf(color, Color.Transparent),
                center = Offset(size.width * x, size.height * y),
                radius = size.width * 0.55f,
            )
        )
    }
    drawRect(Brush.verticalGradient(0.55f to Color.Transparent, 1f to Ember.Bg))
}

@Composable
private fun TvClockBanner(vm: AppViewModel, repo: Repository, onCustomise: () -> Unit) {
    val place = vm.weatherPlace
    val weather = vm.weather
    // Kept fresh while it's on screen; it only fetches when the last one is 20 minutes old.
    LaunchedEffect(place) {
        while (true) {
            vm.refreshWeather()
            delay(5 * 60_000L)
        }
    }
    var settingPlace by remember { mutableStateOf(false) }
    if (settingPlace) WeatherPlaceDialog(vm) { settingPlace = false }
    var forecast by remember { mutableStateOf(false) }
    if (forecast) ForecastDialog(vm) { forecast = false }
    LaunchedEffect(vm.headlinesOn) {
        while (true) {
            vm.refreshHeadlines()
            delay(5 * 60_000L)
        }
    }
    val onNow = rememberOnNow(vm, repo)
    val night = weather?.day == false
    val hero = LocalHomeEntry.current?.hero
    Box(
        Modifier
            .fillMaxWidth()
            .heightIn(min = 310.dp + TV_MENU_SPACE)
            .bannerGlow(
                Triple(if (night) Color(0x8C2A3570) else Color(0x8C3D5E96), 0.82f, 0f),
                Triple(Color(0x55402A66), 0f, 1f),
            )
    ) {
        Row(
            Modifier.fillMaxWidth().align(Alignment.BottomStart).padding(start = 48.dp, end = 48.dp, top = TV_MENU_SPACE + 6.dp, bottom = 22.dp),
            verticalAlignment = Alignment.Bottom,
        ) {
            Column(Modifier.weight(1f)) {
                ClockAndDate(
                    clockStyle = Txt.LargeTitle.copy(fontSize = 118.sp, lineHeight = 120.sp, fontWeight = FontWeight.SemiBold, letterSpacing = (-5).sp),
                    dateStyle = Txt.Headline,
                    center = false,
                )
                val programme = onNow?.programme
                val channels = onNow?.channels.orEmpty()
                val channel = channels.firstOrNull()
                if (channel != null) {
                    Spacer(Modifier.height(14.dp))
                    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(start = 6.dp)) {
                        Box(Modifier.size(7.dp).clip(CircleShape).background(Ember.Live))
                        Spacer(Modifier.width(8.dp))
                        Text(
                            if (programme != null) "On now: ${programme.title} · ${channelParts(channel.name).first}" else "On ${channelParts(channel.name).first}",
                            style = Txt.Label,
                            color = Ember.Text.copy(alpha = 0.85f),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                }
                HeadlinePill(vm, onCustomise)
                Spacer(Modifier.height(16.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(14.dp), verticalAlignment = Alignment.CenterVertically) {
                    if (channel != null) {
                        TvButton(
                            "Watch ${channelParts(channel.name).first}",
                            icon = "▶",
                            focusKey = "home:banner:watch",
                            requester = hero,
                            onLongClick = onCustomise,
                        ) { vm.push(Screen.PlayLive(channels, 0)) }
                    }
                    TvButton(
                        if (place == null) "Add the weather" else "Forecast",
                        focusKey = "home:banner:weather",
                        requester = if (channel == null) hero else null,
                        onLongClick = onCustomise,
                    ) { if (place == null) settingPlace = true else forecast = true }
                }
                BannerHint("Hold OK to change what's shown here")
            }
            TvWeatherPanel(weather, place)
        }
    }
}

@Composable
private fun TvWeatherPanel(weather: WeatherNow?, place: WeatherPlace?) {
    Column(Modifier.widthIn(max = 480.dp).padding(bottom = 4.dp), horizontalAlignment = Alignment.End) {
        if (place == null) {
            Text("Add your town to see the weather here", style = Txt.Label, color = Ember.Dim)
            return@Column
        }
        Row(verticalAlignment = Alignment.CenterVertically) {
            WeatherIcon(weather?.code ?: 3, weather?.day ?: true, 66.dp)
            Spacer(Modifier.width(14.dp))
            Text(
                weather?.let { "${it.temp}°" } ?: "–°",
                style = Txt.LargeTitle.copy(fontSize = 64.sp, lineHeight = 66.sp, fontWeight = FontWeight.SemiBold, letterSpacing = (-2).sp),
                color = Ember.Text,
            )
        }
        Text(
            listOfNotNull(
                place.name,
                weather?.let { Weather.describe(it.code) },
                weather?.let { "Feels like ${it.feelsLike}°" },
                weather?.let { "H ${it.high}°  L ${it.low}°" },
            ).joinToString("  ·  "),
            style = Txt.Label,
            color = Ember.Text.copy(alpha = 0.8f),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        WeatherNotes(weather, Txt.Label)
        val hours = weather?.hours.orEmpty()
        if (hours.isNotEmpty()) {
            Spacer(Modifier.height(14.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                for (h in hours) {
                    Column(
                        Modifier.size(width = 82.dp, height = 96.dp).glassSurface(RoundedCornerShape(16.dp), Color(0x14FFFFFF)),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center,
                    ) {
                        Text(h.time, style = Txt.Caption.copy(fontWeight = FontWeight.SemiBold), color = Ember.Text.copy(alpha = 0.85f))
                        Spacer(Modifier.height(6.dp))
                        WeatherIcon(h.code, h.day, 26.dp)
                        Spacer(Modifier.height(6.dp))
                        Text("${h.temp}°", style = Txt.Label.copy(fontWeight = FontWeight.SemiBold), color = Ember.Text)
                    }
                }
            }
        }
    }
}

/** The channel showing a game: found live, or looked up from the guide ahead of time. */
private fun matchChannel(vm: AppViewModel, m: Match): String? =
    vm.liveSport.firstOrNull { it.match.id == m.id }?.hits?.firstOrNull()?.channel?.name?.let { channelParts(it).first }
        ?: vm.fixtureChannels[m.id]?.ifEmpty { null }

@Composable
private fun TvTeamsBanner(vm: AppViewModel, onCustomise: () -> Unit) {
    val match = bannerMatch(vm, soonOnly = false)
    LaunchedEffect(match?.id) { match?.let { vm.lookUpFixtureChannel(it) } }
    val live = match?.state == MatchState.LIVE
    val hero = LocalHomeEntry.current?.hero
    Box(
        Modifier
            .fillMaxWidth()
            .heightIn(min = 310.dp + TV_MENU_SPACE)
            .bannerGlow(
                Triple(if (live) Color(0x66C8202D) else Color(0x5532508C), 0.1f, 0.25f),
                Triple(Color(0x552A46A0), 0.62f, 0.15f),
            )
    ) {
        Row(
            Modifier.fillMaxWidth().align(Alignment.BottomStart).padding(start = 48.dp, end = 48.dp, top = TV_MENU_SPACE + 20.dp, bottom = 22.dp),
            verticalAlignment = Alignment.Bottom,
        ) {
            Column(Modifier.weight(1f)) {
                if (match == null) {
                    Text("YOUR TEAMS", style = Txt.Caption.copy(fontWeight = FontWeight.SemiBold, letterSpacing = 1.3.sp), color = Ember.Dim)
                    Spacer(Modifier.height(6.dp))
                    Text(if (vm.followedTeams.isEmpty()) "Follow your teams" else "No games coming up", style = Txt.LargeTitle)
                    Spacer(Modifier.height(6.dp))
                    Text(
                        if (vm.followedTeams.isEmpty()) "Choose your teams in Settings → Sport, and their games show here with live scores and where to watch."
                        else "Your teams' next games show here once they're on the fixture list.",
                        style = Txt.Body,
                        color = Ember.Text.copy(alpha = 0.85f),
                        maxLines = 2,
                        modifier = Modifier.widthIn(max = 560.dp),
                    )
                    Spacer(Modifier.height(18.dp))
                    TvButton("Choose teams", focusKey = "home:banner:teams", requester = hero, onLongClick = onCustomise) { vm.tab = Tabs.SETTINGS }
                } else {
                    MatchEyebrow(match)
                    Spacer(Modifier.height(14.dp))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        BannerCrest(match.home, 84.dp)
                        Spacer(Modifier.width(22.dp))
                        if (match.state != MatchState.UPCOMING && match.home.score.isNotBlank()) {
                            Text("${match.home.score} – ${match.away.score}", style = Txt.LargeTitle.copy(fontSize = 54.sp, lineHeight = 58.sp, fontWeight = FontWeight.Bold))
                        } else {
                            Text("v", style = Txt.LargeTitle.copy(fontSize = 34.sp), color = Ember.Dim)
                        }
                        Spacer(Modifier.width(22.dp))
                        BannerCrest(match.away, 84.dp)
                    }
                    Spacer(Modifier.height(14.dp))
                    Text("${match.home.name} v ${match.away.name}", style = Txt.Title.copy(fontWeight = FontWeight.Bold), maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Spacer(Modifier.height(4.dp))
                    val channel = matchChannel(vm, match)
                    Text(
                        listOfNotNull(match.league.name, channel?.let { "On $it" }).joinToString("  ·  "),
                        style = Txt.Label,
                        color = Ember.Dim,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    // And the one after.
                    val after = vm.teamMatches.filter { vm.isFollowedMatch(it) && it.id != match.id && it.state == MatchState.UPCOMING }.minByOrNull { it.kickoff }
                    if (after != null) {
                        Text(
                            "Next: ${formatDay(after.kickoff / 1000)} ${formatClock(after.kickoff / 1000)}  ·  ${after.home.shortName} v ${after.away.shortName}",
                            style = Txt.Caption,
                            color = Ember.Faint,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.padding(top = 4.dp),
                        )
                    }
                    Spacer(Modifier.height(16.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                        val soon = match.state == MatchState.UPCOMING && match.kickoff - System.currentTimeMillis() < 15 * 60_000L
                        when {
                            live || soon -> TvButton("Watch", icon = "▶", focusKey = "home:banner:watch", requester = hero, onLongClick = onCustomise) { vm.watchKickoff(match) }
                            match.state == MatchState.UPCOMING -> TvButton(
                                if (vm.hasMatchReminder(match)) "Reminder on" else "Remind me",
                                focusKey = "home:banner:remind",
                                requester = hero,
                                onLongClick = onCustomise,
                            ) { vm.toggleMatchReminder(match) }
                            else -> TvButton("Your teams", focusKey = "home:banner:teams", requester = hero, onLongClick = onCustomise) { vm.tab = Tabs.SETTINGS }
                        }
                        if (match.state == MatchState.UPCOMING && !soon) {
                            TvButton("Where's it on?", focusKey = "home:banner:find", onLongClick = onCustomise) { vm.findingMatch = match }
                        }
                    }
                }
                BannerHint("Hold OK to change what's shown here")
            }
            // Where your team stands, on the right.
            LeagueTableCard(vm, match, Modifier.width(300.dp).padding(bottom = 4.dp), rows = 5)
        }
    }
}

/** "● LIVE · 63'  ·  YOUR TEAMS", "TODAY · 17:30" or "FULL TIME". */
@Composable
private fun MatchEyebrow(match: Match) {
    val style = Txt.Caption.copy(fontWeight = FontWeight.SemiBold, letterSpacing = 1.3.sp)
    Row(verticalAlignment = Alignment.CenterVertically) {
        when (match.state) {
            MatchState.LIVE -> {
                Box(Modifier.size(7.dp).clip(CircleShape).background(Ember.Live))
                Spacer(Modifier.width(7.dp))
                Text("LIVE · ${match.status}", style = style, color = Ember.Live)
            }
            MatchState.UPCOMING -> Text("${formatDay(match.kickoff / 1000)} · ${formatClock(match.kickoff / 1000)}".uppercase(Locale.UK), style = style, color = Ember.Text)
            MatchState.FINISHED -> Text("FULL TIME", style = style, color = Ember.Dim)
        }
        Text("  ·  YOUR TEAMS", style = style, color = Ember.Dim)
    }
}

@Composable
private fun BannerCrest(team: TeamScore, size: Dp) {
    Box(Modifier.size(size).glassSurface(CircleShape, Color(0x1AFFFFFF)), contentAlignment = Alignment.Center) {
        if (team.logo.isNotBlank()) {
            NetImage(team.logo, Modifier.fillMaxSize().padding(size / 6), androidx.compose.ui.layout.ContentScale.Fit)
        } else {
            Text(team.shortName.take(3).uppercase(Locale.UK), style = Txt.Headline.copy(fontWeight = FontWeight.Bold))
        }
    }
}

@Composable
private fun TvHolidayBanner(vm: AppViewModel, onCustomise: () -> Unit) {
    val holiday = Holiday.now() ?: Holiday.CHRISTMAS
    val picks = vm.holidayPicks?.takeIf { it.holiday == holiday }
    val films = picks?.films.orEmpty()
    val hero = LocalHomeEntry.current?.hero
    val christmas = holiday == Holiday.CHRISTMAS
    Box(
        Modifier
            .fillMaxWidth()
            .heightIn(min = 310.dp + TV_MENU_SPACE)
            .bannerGlow(
                *(if (christmas) arrayOf(
                    Triple(Color(0x8CD63C28), 0.75f, 0f),
                    Triple(Color(0x59E6AA46), 0.98f, 0.6f),
                    Triple(Color(0x592A7850), 0.08f, 0f),
                ) else arrayOf(
                    Triple(Color(0x80E0701E), 0.75f, 0f),
                    Triple(Color(0x596A2C9A), 0.08f, 0.1f),
                ))
            )
    ) {
        if (christmas) {
            Snowfall(Modifier.matchParentSize())
            FairyLights(Modifier.fillMaxWidth().height(30.dp).offset(y = TV_MENU_SPACE - 2.dp))
        }
        Row(
            Modifier.fillMaxWidth().align(Alignment.BottomStart).padding(start = 48.dp, end = 48.dp, top = TV_MENU_SPACE + 40.dp, bottom = 22.dp),
            verticalAlignment = Alignment.Bottom,
        ) {
            Column(Modifier.weight(1f)) {
                Text(
                    holiday.eyebrow,
                    style = Txt.Caption.copy(fontWeight = FontWeight.SemiBold, letterSpacing = 1.3.sp),
                    color = if (christmas) Color(0xFFFFD27A) else Color(0xFFFFA45C),
                )
                Spacer(Modifier.height(6.dp))
                Text(holiday.title, style = Txt.LargeTitle)
                Spacer(Modifier.height(6.dp))
                Text(
                    when {
                        picks == null -> "Gathering ${holiday.kind} films from your provider…"
                        films.isEmpty() -> "No ${holiday.search} films found in your lists yet. Search to look through everything."
                        else -> "${films.size} ${holiday.kind} films from your provider, all in one place."
                    },
                    style = Txt.Body,
                    color = Ember.Text.copy(alpha = 0.85f),
                    maxLines = 2,
                    modifier = Modifier.widthIn(max = 520.dp),
                )
                Spacer(Modifier.height(18.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                    TvButton(
                        if (films.isEmpty()) "Search" else "See them all",
                        focusKey = "home:banner:browse",
                        requester = hero,
                        onLongClick = onCustomise,
                    ) {
                        val cat = picks?.categoryId
                        if (cat != null) vm.openCategory(Tabs.MOVIES, cat) else vm.searchHoliday(holiday)
                    }
                    if (films.isNotEmpty()) {
                        TvButton("Surprise me", focusKey = "home:banner:surprise", onLongClick = onCustomise) { vm.push(Screen.MovieDetail(films.random())) }
                    }
                }
                BannerHint("Hold OK to change what's shown here")
            }
            if (films.isNotEmpty()) PosterFan(films.take(4), posterWidth = 116.dp, step = 90.dp)
        }
    }
}

/** A few posters fanned out like cards on a table. */
@Composable
private fun PosterFan(films: List<Movie>, posterWidth: Dp, step: Dp) {
    val tilts = listOf(-6f, -2f, 3f, 7f)
    val drops = listOf(26.dp, 6.dp, 18.dp, 34.dp)
    val height = posterWidth * 1.5f
    Box(Modifier.size(width = step * (films.size - 1) + posterWidth + 16.dp, height = height + 44.dp)) {
        films.forEachIndexed { i, m ->
            val shape = RoundedCornerShape(12.dp)
            Box(
                Modifier
                    .offset(x = step * i, y = drops[i % drops.size])
                    .size(width = posterWidth, height = height)
                    .graphicsLayer { rotationZ = tilts[i % tilts.size] }
                    .shadow(18.dp, shape)
                    .clip(shape)
            ) {
                Placeholder(m.name, Modifier.fillMaxSize(), fontSizeSp = 14)
                NetImage(m.poster, Modifier.fillMaxSize())
            }
        }
    }
}

/** Gentle snow drifting down: drawn in its own layer, so it never makes the page itself redraw. */
@Composable
private fun Snowfall(modifier: Modifier) {
    val flakes = remember {
        val r = java.util.Random(7)
        List(60) { floatArrayOf(r.nextFloat(), r.nextFloat(), 1.2f + r.nextFloat() * 2.6f, 0.5f + r.nextFloat(), r.nextFloat()) }
    }
    val time by rememberInfiniteTransition(label = "snow").animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(24_000, easing = LinearEasing)),
        label = "snow",
    )
    Canvas(modifier.graphicsLayer {}) {
        val t: Float = time
        for (f in flakes) {
            // x, y, size, speed, phase: each falls at its own pace and sways a little.
            val y = ((f[1] + t * f[3] * 2f) % 1f) * size.height
            val x = (f[0] * size.width + sin((t * 6f + f[4]) * 2f * PI.toFloat()) * 14f)
            drawCircle(Color.White.copy(alpha = 0.25f + f[2] / 3.8f * 0.45f), radius = f[2] * density / 1.6f, center = Offset(x, y))
        }
    }
}

/** A string of fairy lights along the top, twinkling softly. */
@Composable
private fun FairyLights(modifier: Modifier) {
    val colours = listOf(Color(0xFFFFD27A), Color(0xFFFF6B5A), Color(0xFF9FE0A8), Color(0xFFFFF3D6))
    val time by rememberInfiniteTransition(label = "lights").animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(6_000, easing = LinearEasing), RepeatMode.Restart),
        label = "lights",
    )
    Canvas(modifier.graphicsLayer {}) {
        val step = 36.dp.toPx()
        val count = (size.width / step).toInt() + 1
        val wire = Path()
        for (i in 0 until count) {
            val x = 18.dp.toPx() + i * step
            val y = size.height / 2 + sin(i * 0.9f) * 6.dp.toPx()
            if (i == 0) wire.moveTo(x, y) else wire.lineTo(x, y)
        }
        drawPath(wire, Color(0x33FFFFFF), style = androidx.compose.ui.graphics.drawscope.Stroke(width = 1.dp.toPx()))
        for (i in 0 until count) {
            val x = 18.dp.toPx() + i * step
            val y = size.height / 2 + sin(i * 0.9f) * 6.dp.toPx()
            val c = colours[i % colours.size]
            val phase: Float = time + i * 0.17f
            val glow: Float = 0.55f + 0.45f * sin(phase * 2f * PI.toFloat())
            drawCircle(c.copy(alpha = 0.22f * glow), radius = 9.dp.toPx(), center = Offset(x, y))
            drawCircle(c.copy(alpha = 0.6f + 0.4f * glow), radius = 3.5.dp.toPx(), center = Offset(x, y))
        }
    }
}

// ---------------- Phone ----------------

/** The banner at the top of Home on a phone: the same choices, laid out for a narrow screen. */
@Composable
fun PhoneTopBanner(vm: AppViewModel, repo: Repository) {
    // The holiday's films are only gathered when this banner could show them.
    if (vm.bannerMode == BannerModes.AUTO || vm.bannerMode == BannerModes.HOLIDAY) {
        LaunchedEffect(repo, vm.reloadToken, vm.profile?.id) { vm.ensureHoliday() }
    }
    var picking by remember { mutableStateOf(false) }
    if (picking) BannerPickerDialog(vm) { picking = false }
    val top = WindowInsets.statusBars.asPaddingValues().calculateTopPadding() + 74.dp
    val kind = bannerKind(vm)
    if (kind == null) {
        Spacer(Modifier.height(top))
        return
    }
    // Press and hold anywhere on it (away from its buttons) to choose what it shows.
    Box(Modifier.fillMaxWidth().pointerInput(Unit) { detectTapGestures(onLongPress = { picking = true }) }) {
        Crossfade(targetState = kind, label = "banner") { k ->
            when (k) {
                BannerKind.CLOCK -> PhoneClockBanner(vm, repo, top)
                BannerKind.TEAMS -> PhoneTeamsBanner(vm, top)
                BannerKind.HOLIDAY -> PhoneHolidayBanner(vm, top)
                BannerKind.RESUME -> PhoneResumeBanner(vm, repo)
            }
        }
    }
}

@Composable
private fun PhoneClockBanner(vm: AppViewModel, repo: Repository, top: Dp) {
    val place = vm.weatherPlace
    val weather = vm.weather
    LaunchedEffect(place) {
        while (true) {
            vm.refreshWeather()
            delay(5 * 60_000L)
        }
    }
    var settingPlace by remember { mutableStateOf(false) }
    if (settingPlace) WeatherPlaceDialog(vm) { settingPlace = false }
    var forecast by remember { mutableStateOf(false) }
    if (forecast) ForecastDialog(vm) { forecast = false }
    LaunchedEffect(vm.headlinesOn) {
        while (true) {
            vm.refreshHeadlines()
            delay(5 * 60_000L)
        }
    }
    val onNow = rememberOnNow(vm, repo)
    val night = weather?.day == false
    Column(
        Modifier
            .fillMaxWidth()
            .bannerGlow(Triple(if (night) Color(0x8C2A3570) else Color(0x8C3D5E96), 0.85f, 0f), Triple(Color(0x55402A66), 0f, 0.9f))
            .padding(start = 20.dp, end = 20.dp, top = top, bottom = 12.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        ClockAndDate(
            clockStyle = Txt.LargeTitle.copy(fontSize = 78.sp, lineHeight = 82.sp, fontWeight = FontWeight.SemiBold, letterSpacing = (-3).sp),
            dateStyle = Txt.Label.copy(fontSize = 17.sp),
            center = true,
        )
        Spacer(Modifier.height(14.dp))
        if (place != null) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                WeatherIcon(weather?.code ?: 3, weather?.day ?: true, 40.dp)
                Spacer(Modifier.width(10.dp))
                Text(weather?.let { "${it.temp}°" } ?: "–°", style = Txt.LargeTitle.copy(fontSize = 40.sp, lineHeight = 44.sp, fontWeight = FontWeight.SemiBold))
            }
            Text(
                listOfNotNull(place.name, weather?.let { Weather.describe(it.code) }, weather?.let { "H ${it.high}°  L ${it.low}°" }).joinToString("  ·  "),
                style = Txt.Caption.copy(fontSize = 14.sp),
                color = Ember.Text.copy(alpha = 0.8f),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            WeatherNotes(weather, Txt.Caption.copy(fontSize = 14.sp), center = true)
            val hours = weather?.hours.orEmpty()
            if (hours.isNotEmpty()) {
                Spacer(Modifier.height(12.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    for (h in hours) {
                        Column(
                            Modifier.size(width = 58.dp, height = 76.dp).glassSurface(RoundedCornerShape(14.dp), Color(0x14FFFFFF)),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.Center,
                        ) {
                            Text(h.time, style = Txt.Caption.copy(fontSize = 11.sp, fontWeight = FontWeight.SemiBold), color = Ember.Text.copy(alpha = 0.85f))
                            Spacer(Modifier.height(4.dp))
                            WeatherIcon(h.code, h.day, 20.dp)
                            Spacer(Modifier.height(4.dp))
                            Text("${h.temp}°", style = Txt.Caption.copy(fontWeight = FontWeight.SemiBold), color = Ember.Text)
                        }
                    }
                }
            }
        }
        val channels = onNow?.channels.orEmpty()
        val channel = channels.firstOrNull()
        val programme = onNow?.programme
        if (channel != null && programme != null) {
            Spacer(Modifier.height(12.dp))
            Text(
                "On now: ${programme.title} · ${channelParts(channel.name).first}",
                style = Txt.Caption.copy(fontSize = 14.sp),
                color = Ember.Text.copy(alpha = 0.85f),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        HeadlinePill(vm, onCustomise = null)
        Spacer(Modifier.height(14.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            if (channel != null) {
                PillButton("Watch", icon = EmberIcons.Play, primary = true, modifier = Modifier.widthIn(min = 120.dp)) { vm.push(Screen.PlayLive(channels, 0)) }
            }
            PillButton(if (place == null) "Add the weather" else "Forecast", primary = channel == null) {
                if (place == null) settingPlace = true else forecast = true
            }
        }
        BannerHint("Press and hold to change what's shown here", center = true)
    }
}

@Composable
private fun PhoneTeamsBanner(vm: AppViewModel, top: Dp) {
    val match = bannerMatch(vm, soonOnly = false)
    LaunchedEffect(match?.id) { match?.let { vm.lookUpFixtureChannel(it) } }
    val live = match?.state == MatchState.LIVE
    Column(
        Modifier
            .fillMaxWidth()
            .bannerGlow(Triple(if (live) Color(0x66C8202D) else Color(0x5532508C), 0.1f, 0.2f), Triple(Color(0x552A46A0), 0.9f, 0.1f))
            .padding(start = 20.dp, end = 20.dp, top = top, bottom = 12.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        if (match == null) {
            Text(if (vm.followedTeams.isEmpty()) "Follow your teams" else "No games coming up", style = Txt.Title.copy(fontWeight = FontWeight.Bold))
            Spacer(Modifier.height(6.dp))
            Text(
                if (vm.followedTeams.isEmpty()) "Choose your teams in Settings → Sport, and their games show here." else "Their next games show here once they're on the fixture list.",
                style = Txt.Caption.copy(fontSize = 14.sp),
                color = Ember.Text.copy(alpha = 0.8f),
                textAlign = TextAlign.Center,
            )
            Spacer(Modifier.height(14.dp))
            PillButton("Choose teams", primary = true) { vm.tab = Tabs.SETTINGS }
        } else {
            MatchEyebrow(match)
            Spacer(Modifier.height(14.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                BannerCrest(match.home, 64.dp)
                Spacer(Modifier.width(18.dp))
                if (match.state != MatchState.UPCOMING && match.home.score.isNotBlank()) {
                    Text("${match.home.score} – ${match.away.score}", style = Txt.LargeTitle.copy(fontSize = 40.sp, lineHeight = 44.sp, fontWeight = FontWeight.Bold))
                } else {
                    Text("v", style = Txt.Title, color = Ember.Dim)
                }
                Spacer(Modifier.width(18.dp))
                BannerCrest(match.away, 64.dp)
            }
            Spacer(Modifier.height(10.dp))
            Text("${match.home.shortName} v ${match.away.shortName}", style = Txt.Title.copy(fontWeight = FontWeight.Bold), maxLines = 1, overflow = TextOverflow.Ellipsis)
            val channel = matchChannel(vm, match)
            Text(
                listOfNotNull(match.league.name, channel?.let { "On $it" }).joinToString("  ·  "),
                style = Txt.Caption.copy(fontSize = 14.sp),
                color = Ember.Text.copy(alpha = 0.8f),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Spacer(Modifier.height(14.dp))
            val soon = match.state == MatchState.UPCOMING && match.kickoff - System.currentTimeMillis() < 15 * 60_000L
            when {
                live || soon -> PillButton("Watch", icon = EmberIcons.Play, primary = true, modifier = Modifier.widthIn(min = 150.dp)) { vm.watchKickoff(match) }
                match.state == MatchState.UPCOMING -> Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    PillButton(if (vm.hasMatchReminder(match)) "Reminder on" else "Remind me", icon = EmberIcons.Bell, primary = true) { vm.toggleMatchReminder(match) }
                    PillButton("Where's it on?") { vm.findingMatch = match }
                }
                else -> {}
            }
            val others = vm.teamMatches.filter { vm.isFollowedMatch(it) && it.id != match.id && it.state == MatchState.UPCOMING }.take(2)
            if (others.isNotEmpty()) {
                Spacer(Modifier.height(12.dp))
                for (m in others) {
                    Text(
                        "${formatDay(m.kickoff / 1000)} ${formatClock(m.kickoff / 1000)}  ·  ${m.home.shortName} v ${m.away.shortName}",
                        style = Txt.Caption,
                        color = Ember.Dim,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
        }
        Spacer(Modifier.height(12.dp))
        LeagueTableCard(vm, match, Modifier.fillMaxWidth(), rows = 5)
        BannerHint("Press and hold to change what's shown here", center = true)
    }
}

@Composable
private fun PhoneHolidayBanner(vm: AppViewModel, top: Dp) {
    val holiday = Holiday.now() ?: Holiday.CHRISTMAS
    val picks = vm.holidayPicks?.takeIf { it.holiday == holiday }
    val films = picks?.films.orEmpty()
    val christmas = holiday == Holiday.CHRISTMAS
    Box(
        Modifier
            .fillMaxWidth()
            .bannerGlow(
                *(if (christmas) arrayOf(Triple(Color(0x8CD63C28), 0.8f, 0f), Triple(Color(0x592A7850), 0.1f, 0.1f))
                else arrayOf(Triple(Color(0x80E0701E), 0.8f, 0f), Triple(Color(0x596A2C9A), 0.1f, 0.1f)))
            )
    ) {
        if (christmas) Snowfall(Modifier.matchParentSize())
        Column(
            Modifier.fillMaxWidth().padding(start = 20.dp, end = 20.dp, top = top, bottom = 12.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            if (films.isNotEmpty()) {
                PosterFan(films.take(3), posterWidth = 96.dp, step = 74.dp)
                Spacer(Modifier.height(4.dp))
            }
            Text(
                holiday.eyebrow,
                style = Txt.Caption.copy(fontWeight = FontWeight.SemiBold, letterSpacing = 1.3.sp),
                color = if (christmas) Color(0xFFFFD27A) else Color(0xFFFFA45C),
            )
            Spacer(Modifier.height(4.dp))
            Text(holiday.title, style = Txt.LargeTitle.copy(fontSize = (30f * Txt.scale).sp))
            Spacer(Modifier.height(4.dp))
            Text(
                when {
                    picks == null -> "Gathering ${holiday.kind} films…"
                    films.isEmpty() -> "None found in your lists yet."
                    else -> "${films.size} ${holiday.kind} films from your provider"
                },
                style = Txt.Caption.copy(fontSize = 14.sp),
                color = Ember.Text.copy(alpha = 0.85f),
            )
            Spacer(Modifier.height(14.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                PillButton(if (films.isEmpty()) "Search" else "See them all", primary = true) {
                    val cat = picks?.categoryId
                    if (cat != null) vm.openCategory(Tabs.MOVIES, cat) else vm.searchHoliday(holiday)
                }
                if (films.isNotEmpty()) PillButton("Surprise me") { vm.push(Screen.MovieDetail(films.random())) }
            }
            BannerHint("Press and hold to change what's shown here", center = true)
        }
    }
}

// ---------------- Pieces ----------------

/** "Rain likely from 21:00 · Sunset 16:01" under the weather, the rain part in blue. */
@Composable
private fun WeatherNotes(weather: WeatherNow?, style: TextStyle, center: Boolean = false) {
    val rain = weather?.rainNote
    val sun = weather?.sunNote
    if (rain == null && sun == null) return
    Row(Modifier.padding(top = 4.dp), verticalAlignment = Alignment.CenterVertically) {
        if (rain != null) {
            Text(rain, style = style.copy(fontWeight = FontWeight.SemiBold), color = Color(0xFF8CC8FF), maxLines = 1)
            if (sun != null) Text("  ·  ", style = style, color = Ember.Dim)
        }
        if (sun != null) Text(sun, style = style, color = Ember.Text.copy(alpha = 0.8f), maxLines = 1, textAlign = if (center) TextAlign.Center else TextAlign.Start)
    }
}

/**
 * The top news story, changing every few seconds, in a slim glass pill. OK (or a tap) finds a
 * news channel to watch. Not shown on kids profiles, or when headlines are turned off.
 */
@Composable
private fun HeadlinePill(vm: AppViewModel, onCustomise: (() -> Unit)?) {
    val list = vm.headlines
    if (!vm.headlinesOn || vm.profile?.kids == true || list.isEmpty()) return
    val isTv = LocalIsTv.current
    var index by remember { mutableStateOf(0) }
    var focused by remember { mutableStateOf(false) }
    LaunchedEffect(list, focused) {
        // Holds still while it's highlighted, so it can be read.
        while (!focused) {
            delay(8_000)
            index = (index + 1) % list.size
        }
    }
    val headline = list[index % list.size]
    Spacer(Modifier.height(if (isTv) 12.dp else 10.dp))
    FocusBox(
        modifier = Modifier.then(if (isTv) Modifier.widthIn(max = 560.dp) else Modifier.fillMaxWidth()).height(if (isTv) 38.dp else 40.dp),
        focusKey = "home:banner:news",
        shape = RoundedCornerShape(19.dp),
        focusedScale = 1.03f,
        color = Color(0x1AFFFFFF),
        focusedColor = Color.White,
        onFocusChange = { focused = it },
        onClick = { vm.watchNews() },
        onLongClick = onCustomise,
    ) { f ->
        Row(Modifier.fillMaxSize().padding(horizontal = 14.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(
                "NEWS",
                style = Txt.Caption.copy(fontWeight = FontWeight.Bold, letterSpacing = 1.2.sp),
                color = if (f) Color.Black else Ember.Live,
            )
            Spacer(Modifier.width(10.dp))
            Crossfade(targetState = headline, label = "headline", modifier = Modifier.weight(1f)) { h ->
                Text(
                    h,
                    style = Txt.Label.copy(fontWeight = FontWeight.Medium),
                    color = if (f) Color.Black else Ember.Text.copy(alpha = 0.9f),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}

/**
 * Where your team stands: a few lines of the league table around them, your team lit up. For
 * the team playing in [match], or your first team.
 */
@Composable
private fun LeagueTableCard(vm: AppViewModel, match: Match?, modifier: Modifier, rows: Int) {
    val team = vm.followedTeams.firstOrNull { it.id == match?.home?.id || it.id == match?.away?.id } ?: vm.followedTeams.firstOrNull() ?: return
    LaunchedEffect(team.league) { vm.ensureLeagueTable(team.league) }
    val tables = vm.leagueTables[team.league].orEmpty()
    val table = tables.firstOrNull { t -> t.any { it.teamId == team.id } } ?: return
    val at = table.indexOfFirst { it.teamId == team.id }
    val from = (at - rows / 2).coerceIn(0, (table.size - rows).coerceAtLeast(0))
    val shown = table.subList(from, minOf(table.size, from + rows))
    val leagueName = app.embertv.data.LEAGUES.firstOrNull { it.code == team.league }?.name ?: "League table"
    Column(modifier.glassSurface(RoundedCornerShape(16.dp), Color(0x14FFFFFF)).padding(horizontal = 12.dp, vertical = 10.dp)) {
        Row(Modifier.padding(horizontal = 6.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(leagueName.uppercase(Locale.UK), style = Txt.Caption.copy(fontWeight = FontWeight.SemiBold, letterSpacing = 1.2.sp), color = Ember.Dim, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
            Text("P", style = Txt.Caption, color = Ember.Faint, textAlign = TextAlign.End, modifier = Modifier.width(28.dp))
            Text("PTS", style = Txt.Caption, color = Ember.Faint, textAlign = TextAlign.End, modifier = Modifier.width(36.dp))
        }
        Spacer(Modifier.height(6.dp))
        for (r in shown) {
            val ours = r.teamId == team.id
            val colour = if (ours) Ember.Text else Ember.Text.copy(alpha = 0.72f)
            val weight = if (ours) FontWeight.Bold else FontWeight.Normal
            Row(
                Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(8.dp))
                    .background(if (ours) Color(0x24FFFFFF) else Color.Transparent)
                    .padding(horizontal = 6.dp, vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text("${r.rank}", style = Txt.Caption.copy(fontWeight = weight), color = colour, modifier = Modifier.width(24.dp))
                Box(Modifier.size(18.dp), contentAlignment = Alignment.Center) {
                    if (r.logo.isNotBlank()) NetImage(r.logo, Modifier.fillMaxSize(), androidx.compose.ui.layout.ContentScale.Fit)
                }
                Spacer(Modifier.width(8.dp))
                Text(r.shortName, style = Txt.Label.copy(fontWeight = weight), color = colour, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
                Text("${r.played}", style = Txt.Caption.copy(fontWeight = weight), color = colour, textAlign = TextAlign.End, modifier = Modifier.width(28.dp))
                Text("${r.points}", style = Txt.Label.copy(fontWeight = FontWeight.SemiBold), color = colour, textAlign = TextAlign.End, modifier = Modifier.width(36.dp))
            }
        }
    }
}

/** The day's forecast and the next five, with the rain warning and sunset, as in Apple's Weather app. */
@Composable
fun ForecastDialog(vm: AppViewModel, onDone: () -> Unit) {
    val isTv = LocalIsTv.current
    var changing by remember { mutableStateOf(false) }
    if (changing) {
        WeatherPlaceDialog(vm) { changing = false }
        return
    }
    val weather = vm.weather
    val place = vm.weatherPlace
    androidx.compose.ui.window.Dialog(onDismissRequest = onDone) {
        DialogBackdropBlur()
        Column(
            Modifier
                .widthIn(max = 560.dp)
                .fillMaxWidth()
                .heightIn(max = if (isTv) 560.dp else 680.dp)
                .liquidGlass(RoundedCornerShape(28.dp))
                .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            SheetTitle(
                place?.name ?: "Weather",
                eyebrow = "FORECAST",
                subtitle = weather?.let { w -> "${w.temp}°  ${Weather.describe(w.code)}  ·  Feels like ${w.feelsLike}°" },
            )
            WeatherNotes(weather, Txt.Label, center = true)
            Spacer(Modifier.height(14.dp))
            val days = weather?.days.orEmpty()
            if (days.isEmpty()) {
                Text("The forecast isn't in yet.", style = Txt.Body, color = Ember.Dim)
            } else {
                val coldest = days.minOf { it.low }
                val warmest = days.maxOf { it.high }
                Column(Modifier.fillMaxWidth().weight(1f, fill = false).verticalScroll(rememberScrollState())) {
                    days.forEachIndexed { i, d ->
                        if (i > 0) Box(Modifier.fillMaxWidth().height(0.6.dp).background(Color(0x1FFFFFFF)))
                        Row(Modifier.fillMaxWidth().padding(vertical = 9.dp), verticalAlignment = Alignment.CenterVertically) {
                            Text(d.name, style = Txt.Label.copy(fontWeight = FontWeight.SemiBold), modifier = Modifier.width(66.dp))
                            WeatherIcon(d.code, true, 28.dp)
                            Box(Modifier.width(50.dp).padding(start = 8.dp)) {
                                if (d.rainChance >= 20) Text("${d.rainChance}%", style = Txt.Caption.copy(fontWeight = FontWeight.SemiBold), color = Color(0xFF8CC8FF))
                            }
                            Text("${d.low}°", style = Txt.Label, color = Ember.Dim, textAlign = TextAlign.End, modifier = Modifier.width(38.dp))
                            TempRange(d.low, d.high, coldest, warmest, Modifier.weight(1f).padding(horizontal = 12.dp))
                            Text("${d.high}°", style = Txt.Label.copy(fontWeight = FontWeight.SemiBold), modifier = Modifier.width(38.dp))
                        }
                    }
                }
            }
            Spacer(Modifier.height(16.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                GlassButton("Change town", EmberIcons.Search) { changing = true }
                GlassButton("Done", EmberIcons.Check, autoFocus = true, onClick = onDone)
            }
        }
    }
}

/** A day's low-to-high bar on the week's scale, cool blue to warm yellow, like Apple's Weather. */
@Composable
private fun TempRange(low: Int, high: Int, coldest: Int, warmest: Int, modifier: Modifier) {
    Canvas(modifier.height(6.dp)) {
        val span = (warmest - coldest).coerceAtLeast(1).toFloat()
        val r = CornerRadius(size.height / 2, size.height / 2)
        drawRoundRect(Color(0x1FFFFFFF), cornerRadius = r)
        val x0 = (low - coldest) / span * size.width
        val x1 = ((high - coldest) / span * size.width).coerceAtLeast(x0 + size.height)
        drawRoundRect(
            Brush.horizontalGradient(listOf(Color(0xFF6CB6FF), Color(0xFFFFD25A)), startX = 0f, endX = size.width),
            topLeft = Offset(x0, 0f),
            size = Size(x1 - x0, size.height),
            cornerRadius = r,
        )
    }
}

// ---------------- Pick up where you left off ----------------

/** Wide artwork for a history entry: the film's or show's backdrop when there is one. */
@Composable
private fun rememberBackdrop(repo: Repository, e: app.embertv.data.HistoryEntry): String {
    val info = rememberLoad(e.key) {
        if (e.type == ContentType.MOVIE) repo.movieInfo(e.id).backdrop else repo.seriesInfo(e.seriesId).backdrop
    }.value
    val wide = (info as? LoadResult.Ok)?.value
    return wide?.takeIf { it.isNotBlank() } ?: e.image.ifBlank { e.seriesCover }
}

private fun timeLeft(e: app.embertv.data.HistoryEntry): String? {
    if (e.durationMs <= 0 || e.positionMs <= 0) return null
    val mins = ((e.durationMs - e.positionMs) / 60_000L).coerceAtLeast(1L)
    return if (mins >= 60) "${mins / 60} hr ${mins % 60} min left" else "$mins min left"
}

private fun openDetails(vm: AppViewModel, e: app.embertv.data.HistoryEntry) {
    if (e.type == ContentType.MOVIE) {
        vm.push(Screen.MovieDetail(Movie(e.id, e.title, e.seriesCover, "", "", e.ext.ifBlank { "mp4" }, 0L)))
    } else {
        vm.push(Screen.SeriesDetail(app.embertv.data.Series(e.seriesId, e.title, e.seriesCover, "", "", "", "", "")))
    }
}

/** A thin progress line with the time left beside it. */
@Composable
private fun ResumeProgress(e: app.embertv.data.HistoryEntry, width: Dp) {
    val left = timeLeft(e) ?: return
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.width(width).height(4.dp).clip(RoundedCornerShape(2.dp)).background(Color(0x33FFFFFF))) {
            Box(Modifier.fillMaxWidth(e.progress.coerceIn(0.02f, 1f)).height(4.dp).background(Color.White))
        }
        Spacer(Modifier.width(10.dp))
        Text(left, style = Txt.Caption.copy(fontWeight = FontWeight.SemiBold), color = Ember.Text.copy(alpha = 0.85f))
    }
}

@Composable
private fun TvResumeBanner(vm: AppViewModel, repo: Repository, onCustomise: () -> Unit) {
    val e = resumeEntry(vm, recentOnly = false) ?: return
    val art = rememberBackdrop(repo, e)
    val hero = LocalHomeEntry.current?.hero
    Box(Modifier.fillMaxWidth().heightIn(min = 310.dp + TV_MENU_SPACE)) {
        Crossfade(targetState = art, label = "resume-art", modifier = Modifier.matchParentSize()) { a ->
            NetImage(a, Modifier.fillMaxSize())
        }
        // Apple TV style: the picture fills the width, fading softly into the page on the left and below.
        Box(
            Modifier.matchParentSize().background(
                Brush.horizontalGradient(0f to Ember.Bg.copy(alpha = 0.92f), 0.35f to Ember.Bg.copy(alpha = 0.55f), 0.65f to Color.Transparent)
            )
        )
        Box(Modifier.matchParentSize().background(Brush.verticalGradient(0f to Ember.Bg.copy(alpha = 0.55f), 0.25f to Color.Transparent, 0.62f to Color.Transparent, 1f to Ember.Bg)))
        Column(Modifier.align(Alignment.BottomStart).padding(start = 48.dp, top = TV_MENU_SPACE + 30.dp, bottom = 22.dp).width(560.dp)) {
            Text("PICK UP WHERE YOU LEFT OFF", style = Txt.Caption.copy(fontWeight = FontWeight.SemiBold, letterSpacing = 1.3.sp), color = Ember.Dim)
            Spacer(Modifier.height(6.dp))
            Text(app.embertv.data.Versions.cleanTitle(e.title), style = Txt.LargeTitle, maxLines = 1, overflow = TextOverflow.Ellipsis)
            if (e.subtitle.isNotBlank()) {
                Spacer(Modifier.height(4.dp))
                Text(e.subtitle, style = Txt.Label, color = Ember.Text.copy(alpha = 0.85f), maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
            Spacer(Modifier.height(12.dp))
            ResumeProgress(e, 220.dp)
            Spacer(Modifier.height(18.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                TvButton(
                    if (e.positionMs > 0) "Resume" else "Play",
                    icon = "▶",
                    focusKey = "home:banner:resume",
                    requester = hero,
                    onLongClick = onCustomise,
                ) { vm.resume(e) }
                TvButton("More info", focusKey = "home:banner:info", onLongClick = onCustomise) { openDetails(vm, e) }
            }
            BannerHint("Hold OK to change what's shown here")
        }
    }
}

@Composable
private fun PhoneResumeBanner(vm: AppViewModel, repo: Repository) {
    val e = resumeEntry(vm, recentOnly = false) ?: return
    val landscape = androidx.compose.ui.platform.LocalConfiguration.current.orientation == android.content.res.Configuration.ORIENTATION_LANDSCAPE
    val wide = rememberBackdrop(repo, e)
    val art = if (landscape) wide else e.seriesCover.ifBlank { wide }
    Box(Modifier.fillMaxWidth().aspectRatio(if (landscape) 2.4f else 4f / 5f)) {
        Placeholder(e.title, Modifier.fillMaxSize(), fontSizeSp = 32)
        NetImage(art, Modifier.fillMaxSize())
        Box(
            Modifier.fillMaxSize().background(
                Brush.verticalGradient(0f to Color(0x66000000), 0.18f to Color.Transparent, 0.45f to Color.Transparent, 0.78f to Ember.Bg.copy(alpha = 0.85f), 1f to Ember.Bg)
            )
        )
        Column(
            Modifier.align(Alignment.BottomCenter).fillMaxWidth().padding(start = 24.dp, end = 24.dp, bottom = 12.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text("PICK UP WHERE YOU LEFT OFF", style = Txt.Caption.copy(fontWeight = FontWeight.SemiBold, letterSpacing = 1.3.sp), color = Ember.Text.copy(alpha = 0.75f))
            Spacer(Modifier.height(4.dp))
            Text(
                app.embertv.data.Versions.cleanTitle(e.title),
                style = Txt.LargeTitle.copy(fontSize = (30f * Txt.scale).sp, lineHeight = (34f * Txt.scale).sp),
                textAlign = TextAlign.Center,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            if (e.subtitle.isNotBlank()) {
                Text(e.subtitle, style = Txt.Caption.copy(fontSize = 14.sp), color = Ember.Text.copy(alpha = 0.85f), maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
            Spacer(Modifier.height(10.dp))
            ResumeProgress(e, 120.dp)
            Spacer(Modifier.height(14.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                PillButton(if (e.positionMs > 0) "Resume" else "Play", icon = EmberIcons.Play, primary = true, modifier = Modifier.widthIn(min = 150.dp)) { vm.resume(e) }
                PillButton("More info") { openDetails(vm, e) }
            }
            BannerHint("Press and hold to change what's shown here", center = true)
        }
    }
}

// ---------------- Choosing ----------------

/** Choose what the banner shows, and the town for the weather. */
@Composable
fun BannerPickerDialog(vm: AppViewModel, onDone: () -> Unit) {
    val isTv = LocalIsTv.current
    var settingPlace by remember { mutableStateOf(false) }
    androidx.compose.ui.window.Dialog(onDismissRequest = onDone) {
        DialogBackdropBlur()
        Column(
            Modifier
                .widthIn(max = 540.dp)
                .fillMaxWidth()
                .heightIn(max = if (isTv) 560.dp else 640.dp)
                .liquidGlass(RoundedCornerShape(28.dp))
                .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            SheetTitle("Top of Home", eyebrow = vm.profile?.name, subtitle = "What the big banner at the top of Home shows. Each profile chooses its own.")
            Spacer(Modifier.height(14.dp))
            Column(
                Modifier.fillMaxWidth().weight(1f, fill = false).verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                val tick: @Composable (Boolean) -> Unit = { dark ->
                    Icon(EmberIcons.Check, contentDescription = "Chosen", tint = if (dark) Color.Black else Ember.Text, modifier = Modifier.size(18.dp))
                }
                for ((id, name, about) in BannerModes.ALL) {
                    SheetOption(
                        name,
                        focusKey = "banner:$id",
                        subtitle = about,
                        autoFocus = id == vm.bannerMode,
                        trailing = if (id == vm.bannerMode) tick else null,
                    ) {
                        vm.chooseBanner(id)
                        onDone()
                    }
                }
                Spacer(Modifier.height(6.dp))
                SheetSection("Weather")
                val chevron: @Composable (Boolean) -> Unit = { dark ->
                    Icon(EmberIcons.ChevronRight, contentDescription = null, tint = if (dark) Color.Black else Ember.Dim, modifier = Modifier.size(18.dp))
                }
                SheetOption(
                    "Town for the weather",
                    focusKey = "banner:weather",
                    subtitle = vm.weatherPlace?.let { p -> listOf(p.name, p.detail).filter { it.isNotBlank() }.joinToString(", ") } ?: "Not set",
                    trailing = chevron,
                ) { settingPlace = true }
            }
        }
    }
    if (settingPlace) WeatherPlaceDialog(vm) { settingPlace = false }
}

/** Type a town, then pick the right one from what's found. */
@Composable
fun WeatherPlaceDialog(vm: AppViewModel, onDone: () -> Unit) {
    val isTv = LocalIsTv.current
    var typing by remember { mutableStateOf(true) }
    var query by remember { mutableStateOf("") }
    var results by remember { mutableStateOf<List<WeatherPlace>?>(null) }
    var searching by remember { mutableStateOf(false) }
    var failed by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    if (typing) {
        TextEntryDialog(
            title = "Your town or city, for the weather",
            value = query,
            onValueChange = { query = it },
            placeholder = "For example, Glasgow",
            onDone = {
                typing = false
                val q = query.trim()
                if (q.isEmpty()) {
                    onDone()
                } else {
                    searching = true
                    failed = false
                    results = null
                    scope.launch {
                        val found = Weather.findPlaces(q)
                        searching = false
                        failed = found == null
                        results = found.orEmpty()
                    }
                }
            },
        )
        return
    }
    androidx.compose.ui.window.Dialog(onDismissRequest = onDone) {
        DialogBackdropBlur()
        Column(
            Modifier
                .widthIn(max = 520.dp)
                .fillMaxWidth()
                .heightIn(max = if (isTv) 540.dp else 620.dp)
                .liquidGlass(RoundedCornerShape(28.dp))
                .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            val found = results.orEmpty()
            SheetTitle(
                "Weather",
                subtitle = when {
                    searching -> "Looking for “${query.trim()}”…"
                    failed -> "Couldn't look that up just now. Check the connection and try again."
                    found.isEmpty() -> "Nowhere called “${query.trim()}” was found. Try the nearest town or city."
                    else -> "Which one is yours?"
                },
            )
            Spacer(Modifier.height(14.dp))
            Column(
                Modifier.fillMaxWidth().weight(1f, fill = false).verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                found.forEachIndexed { i, p ->
                    SheetOption(p.name, focusKey = "weather:place:$i", subtitle = p.detail.ifBlank { null }, autoFocus = i == 0) {
                        vm.chooseWeatherPlace(p)
                        onDone()
                    }
                }
                if (!searching) {
                    SheetOption("Search again", focusKey = "weather:again", autoFocus = found.isEmpty()) {
                        query = ""
                        typing = true
                    }
                    if (vm.weatherPlace != null) {
                        SheetOption("Turn the weather off", focusKey = "weather:off") {
                            vm.chooseWeatherPlace(null)
                            onDone()
                        }
                    }
                }
            }
        }
    }
}

// ---------------- Weather symbols ----------------

/** A small drawn weather symbol for a WMO [code]: sun or moon, cloud, rain, snow, fog or thunder. */
@Composable
fun WeatherIcon(code: Int, day: Boolean, size: Dp) {
    Canvas(Modifier.size(size)) { drawWeather(code, day) }
}

private val Sun = Color(0xFFFFD25A)
private val Moon = Color(0xFFF4E7B5)
private val CloudWhite = Color(0xFFF2F2F5)
private val CloudGrey = Color(0xFFAEB3C0)
private val RainBlue = Color(0xFF6CB6FF)

private fun DrawScope.drawSun(cx: Float, cy: Float, r: Float) {
    drawCircle(Sun, r, Offset(cx, cy))
    for (i in 0 until 8) {
        val a = i * PI.toFloat() / 4f
        drawLine(
            Sun,
            Offset(cx + cos(a) * r * 1.35f, cy + sin(a) * r * 1.35f),
            Offset(cx + cos(a) * r * 1.75f, cy + sin(a) * r * 1.75f),
            strokeWidth = r * 0.22f,
            cap = StrokeCap.Round,
        )
    }
}

private fun DrawScope.drawMoon(cx: Float, cy: Float, r: Float) {
    val whole = Path().apply { addOval(androidx.compose.ui.geometry.Rect(Offset(cx, cy), r)) }
    val bite = Path().apply { addOval(androidx.compose.ui.geometry.Rect(Offset(cx + r * 0.55f, cy - r * 0.35f), r * 0.85f)) }
    val crescent = Path().apply { op(whole, bite, PathOperation.Difference) }
    drawPath(crescent, Moon)
}

private fun DrawScope.drawCloud(cx: Float, cy: Float, w: Float, color: Color) {
    drawCircle(color, w * 0.21f, Offset(cx - w * 0.2f, cy + w * 0.02f))
    drawCircle(color, w * 0.28f, Offset(cx + w * 0.04f, cy - w * 0.08f))
    drawCircle(color, w * 0.19f, Offset(cx + w * 0.27f, cy + w * 0.04f))
    drawRoundRect(color, topLeft = Offset(cx - w * 0.41f, cy + w * 0.02f), size = Size(w * 0.86f, w * 0.21f), cornerRadius = CornerRadius(w * 0.105f))
}

private fun DrawScope.drawWeather(code: Int, day: Boolean) {
    val s = size.width
    when (code) {
        0, 1 -> if (day) drawSun(s / 2, s / 2, s * 0.22f) else drawMoon(s / 2, s / 2, s * 0.32f)
        2 -> {
            if (day) drawSun(s * 0.64f, s * 0.36f, s * 0.15f) else drawMoon(s * 0.64f, s * 0.36f, s * 0.2f)
            drawCloud(s * 0.46f, s * 0.6f, s * 0.78f, CloudWhite)
        }
        3 -> {
            drawCloud(s * 0.6f, s * 0.4f, s * 0.62f, CloudGrey)
            drawCloud(s * 0.44f, s * 0.6f, s * 0.78f, CloudWhite)
        }
        45, 48 -> for (i in 0 until 3) {
            val y = s * (0.34f + i * 0.17f)
            drawLine(CloudWhite.copy(alpha = 0.85f - i * 0.15f), Offset(s * (0.14f + i * 0.06f), y), Offset(s * (0.86f - i * 0.04f), y), strokeWidth = s * 0.08f, cap = StrokeCap.Round)
        }
        71, 73, 75, 77, 85, 86 -> {
            drawCloud(s * 0.5f, s * 0.4f, s * 0.8f, CloudWhite)
            for (i in 0 until 3) drawCircle(Color.White, s * 0.055f, Offset(s * (0.3f + i * 0.2f), s * (0.78f + (i % 2) * 0.08f)))
        }
        95, 96, 99 -> {
            drawCloud(s * 0.5f, s * 0.38f, s * 0.8f, CloudGrey)
            val bolt = Path().apply {
                moveTo(s * 0.52f, s * 0.56f)
                lineTo(s * 0.38f, s * 0.78f)
                lineTo(s * 0.5f, s * 0.78f)
                lineTo(s * 0.42f, s * 0.96f)
                lineTo(s * 0.64f, s * 0.7f)
                lineTo(s * 0.52f, s * 0.7f)
                lineTo(s * 0.6f, s * 0.56f)
                close()
            }
            drawPath(bolt, Sun)
        }
        else -> {
            // Drizzle, rain and showers.
            val heavy = code in setOf(65, 67, 82)
            drawCloud(s * 0.5f, s * 0.38f, s * 0.8f, if (heavy) CloudGrey else CloudWhite)
            val drops = if (heavy) 4 else 3
            for (i in 0 until drops) {
                val x = s * (0.28f + i * (0.46f / (drops - 1)))
                drawLine(RainBlue, Offset(x + s * 0.04f, s * 0.68f), Offset(x - s * 0.04f, s * 0.88f), strokeWidth = s * 0.06f, cap = StrokeCap.Round)
            }
        }
    }
}
