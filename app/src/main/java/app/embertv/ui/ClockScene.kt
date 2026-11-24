package app.embertv.ui

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.embertv.data.Channel
import app.embertv.data.ContentType
import app.embertv.data.Programme
import app.embertv.data.Repository
import app.embertv.data.WeatherNow
import app.embertv.data.toChannel
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.delay
import java.util.Calendar
import kotlin.math.PI
import kotlin.math.sin

// ---------------- Backgrounds behind the clock ----------------

/** The backgrounds the clock banner can have. [AUTO] picks one from the weather, time and season. */
enum class ClockTheme(val id: String, val title: String, val about: String) {
    AUTO("auto", "Automatic", "Christmas and New Year in season, autumn colours, and rain, snow or a starry sky with the weather"),
    PLAIN("plain", "Simple", "A soft blue glow"),
    CHRISTMAS("christmas", "Christmas", "Fairy lights and gentle snow"),
    NEW_YEAR("newyear", "New Year", "Golden fireworks"),
    AUTUMN("autumn", "Autumn", "A sunset sky with drifting leaves"),
    RAIN("rain", "Rain", "Soft rain on a deep blue"),
    SNOW("snow", "Snow", "Snow falling on an icy blue"),
    NIGHT("night", "Night sky", "Stars and a moon glow");

    companion object {
        fun of(id: String): ClockTheme = values().firstOrNull { it.id == id } ?: AUTO
    }
}

/**
 * What Automatic shows right now: New Year's Eve from 6pm, Christmas in season, then the weather
 * (snow, rain, or a clear night sky), autumn colours from September to November, else Simple.
 */
fun autoClockTheme(weather: WeatherNow?): ClockTheme {
    val c = Calendar.getInstance()
    val month = c.get(Calendar.MONTH)
    val day = c.get(Calendar.DAY_OF_MONTH)
    val hour = c.get(Calendar.HOUR_OF_DAY)
    if ((month == Calendar.DECEMBER && day == 31 && hour >= 18) || (month == Calendar.JANUARY && day == 1 && hour < 2)) return ClockTheme.NEW_YEAR
    if (Holiday.now() == Holiday.CHRISTMAS) return ClockTheme.CHRISTMAS
    val code = weather?.code
    if (code != null) {
        if (code in 71..77 || code == 85 || code == 86) return ClockTheme.SNOW
        if (code in 51..67 || code in 80..82 || code in 95..99) return ClockTheme.RAIN
        if (!weather.day && code <= 1) return ClockTheme.NIGHT
    }
    if (month in Calendar.SEPTEMBER..Calendar.NOVEMBER) return ClockTheme.AUTUMN
    return ClockTheme.PLAIN
}

/** The profile's background, with Automatic worked out. */
fun clockThemeNow(vm: AppViewModel): ClockTheme =
    if (vm.clockTheme == ClockTheme.AUTO) autoClockTheme(vm.weather) else vm.clockTheme

/**
 * The background behind the clock, drawn by the app (no pictures to download), fading into the
 * page at the bottom. Late at night ([moving] false, [dim] true) it stands still and darkens.
 */
@Composable
fun ClockBackdrop(theme: ClockTheme, night: Boolean, moving: Boolean, dim: Boolean, lightsTop: Dp, modifier: Modifier) {
    Box(modifier) {
        when (theme) {
            ClockTheme.CHRISTMAS -> {
                Box(Modifier.fillMaxSize().bannerGlow(*festiveGlows(true, wide = true), dim = dim))
                Snowfall(Modifier.fillMaxSize(), moving = moving)
                FairyLights(Modifier.fillMaxWidth().height(30.dp).offset(y = lightsTop), moving = moving)
            }
            ClockTheme.NEW_YEAR -> {
                Box(Modifier.fillMaxSize().background(Brush.verticalGradient(listOf(Color(0xFF07060F), Color(0xFF0C0A16)))))
                Canvas(Modifier.fillMaxSize()) {
                    drawRect(Brush.radialGradient(listOf(Color(0x8C3C286E), Color.Transparent), center = Offset(size.width / 2, 0f), radius = size.width * 0.7f))
                }
                Fireworks(Modifier.fillMaxSize(), moving)
            }
            ClockTheme.AUTUMN -> {
                Box(
                    Modifier.fillMaxSize().background(
                        Brush.verticalGradient(
                            0f to Color(0xFF2A1530),
                            0.35f to Color(0xFF5A2A2E),
                            0.62f to Color(0xFFA2512A),
                            0.9f to Color(0xFF3A1A14),
                        )
                    )
                )
                Canvas(Modifier.fillMaxSize()) {
                    drawCircle(
                        Brush.radialGradient(
                            listOf(Color(0x8CFFC86E), Color(0x26FF963C), Color.Transparent),
                            center = Offset(size.width * 0.68f, size.height * 0.55f),
                            radius = size.width * 0.18f,
                        ),
                        radius = size.width * 0.18f,
                        center = Offset(size.width * 0.68f, size.height * 0.55f),
                    )
                }
                Leaves(Modifier.fillMaxSize(), moving)
            }
            ClockTheme.RAIN -> {
                Box(
                    Modifier.fillMaxSize()
                        .background(Brush.verticalGradient(0f to Color(0xFF0B1222), 0.45f to Color(0xFF101A30), 1f to Color(0xFF0A0D16)))
                )
                Canvas(Modifier.fillMaxSize()) {
                    drawRect(Brush.radialGradient(listOf(Color(0x476E8CC8), Color.Transparent), center = Offset(size.width, 0f), radius = size.width * 0.45f))
                }
                RainStreaks(Modifier.fillMaxSize(), moving)
            }
            ClockTheme.SNOW -> {
                Box(
                    Modifier.fillMaxSize()
                        .background(Brush.verticalGradient(0f to Color(0xFF0D1626), 0.5f to Color(0xFF1B2B46), 1f to Color(0xFF0C1220)))
                )
                Snowfall(Modifier.fillMaxSize(), moving = moving)
            }
            ClockTheme.NIGHT -> {
                Box(
                    Modifier.fillMaxSize()
                        .background(Brush.verticalGradient(0f to Color(0xFF050816), 0.55f to Color(0xFF0C1430), 1f to Color(0xFF0A0D18)))
                )
                Stars(Modifier.fillMaxSize(), moving)
            }
            else -> Box(
                Modifier.fillMaxSize().bannerGlow(
                    Triple(if (night) Color(0x8C2A3570) else Color(0x8C3D5E96), 0.82f, 0f),
                    Triple(Color(0x55402A66), 0f, 1f),
                    dim = dim,
                )
            )
        }
        // Into the page below, and darker late at night.
        Box(Modifier.fillMaxSize().background(Brush.verticalGradient(0.62f to Color.Transparent, 1f to Ember.Bg)))
        if (dim) Box(Modifier.fillMaxSize().background(Color(0x59000000)))
    }
}

/** A slow 0..1 clock for a background's movement (or a fixed moment when it's standing still). */
@Composable
private fun rememberLoop(moving: Boolean, millis: Int, still: Float): () -> Float {
    if (!moving) return { still }
    val t by rememberInfiniteTransition(label = "scene").animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(millis, easing = LinearEasing)),
        label = "scene",
    )
    return { t }
}

private fun seeded(count: Int, seed: Long, fields: Int): List<FloatArray> {
    val r = java.util.Random(seed)
    return List(count) { FloatArray(fields) { r.nextFloat() } }
}

/** Rain falling at a slant, fading towards the bottom. */
@Composable
private fun RainStreaks(modifier: Modifier, moving: Boolean) {
    val drops = remember { seeded(70, 5, 4) }
    val time = rememberLoop(moving, 1_600, 0.4f)
    Canvas(modifier.graphicsLayer {}) {
        val t = time()
        val len = 22.dp.toPx()
        for (d in drops) {
            // x, start, speed, length.
            val y = ((d[1] + t * (1.4f + d[2])) % 1f) * size.height
            val x = d[0] * size.width + (1f - y / size.height) * 30f
            val fade = (1f - y / size.height).coerceIn(0f, 1f)
            drawLine(
                Color(0xFFAAC8FF).copy(alpha = 0.10f + 0.28f * fade * (0.5f + d[3] / 2f)),
                Offset(x, y),
                Offset(x - len * 0.27f, y + len * (0.7f + d[3] * 0.6f)),
                strokeWidth = 1.2.dp.toPx(),
                cap = StrokeCap.Round,
            )
        }
    }
}

/** Autumn leaves drifting down, turning as they go. */
@Composable
private fun Leaves(modifier: Modifier, moving: Boolean) {
    val leaves = remember { seeded(20, 31, 5) }
    val colours = remember { listOf(Color(0xFFE07A2E), Color(0xFFC2492B), Color(0xFFE8A33A), Color(0xFF8E3B22)) }
    val time = rememberLoop(moving, 26_000, 0.3f)
    Canvas(modifier.graphicsLayer {}) {
        val t = time()
        for ((i, l) in leaves.withIndex()) {
            // x, start, speed, size, spin.
            val y = ((l[1] + t * (0.6f + l[2] * 0.6f)) % 1f) * size.height
            val x = l[0] * size.width + sin((t * 4f + l[4]) * 2f * PI.toFloat()) * 18.dp.toPx()
            val w = (8f + l[3] * 8f).dp.toPx()
            val h = w * 0.6f
            rotate(degrees = (t * 360f * (1f + l[4])) + l[4] * 360f, pivot = Offset(x, y)) {
                drawRoundRect(
                    colours[i % colours.size].copy(alpha = 0.35f + l[3] * 0.4f),
                    topLeft = Offset(x - w / 2, y - h / 2),
                    size = Size(w, h),
                    cornerRadius = CornerRadius(w * 0.8f, h * 0.2f),
                )
            }
        }
    }
}

/** Golden fireworks bursting in turn on the right, and a scatter of sparkles. */
@Composable
private fun Fireworks(modifier: Modifier, moving: Boolean) {
    val sparkles = remember { seeded(60, 41, 3) }
    val time = rememberLoop(moving, 4_200, 0.55f)
    val bursts = remember {
        // x, y (fractions), size, colour, start in the cycle.
        listOf(
            floatArrayOf(0.64f, 0.24f, 72f, 0f, 0f),
            floatArrayOf(0.83f, 0.18f, 54f, 1f, 0.3f),
            floatArrayOf(0.75f, 0.46f, 46f, 2f, 0.55f),
            floatArrayOf(0.92f, 0.44f, 38f, 0f, 0.8f),
        )
    }
    val colours = listOf(Color(0xFFFFD27A), Color(0xFFFF8FB1), Color(0xFF8CC8FF))
    Canvas(modifier.graphicsLayer {}) {
        val t = time()
        for (s in sparkles) {
            drawCircle(Color(0xFFFFE7A8).copy(alpha = 0.15f + s[2] * 0.45f), radius = (0.6f + s[2]).dp.toPx(), center = Offset(s[0] * size.width, s[1] * size.height * 0.85f))
        }
        for (b in bursts) {
            val p = if (moving) (t + b[4]) % 1f else (0.45f + b[4] * 0.2f)
            val open = 1f - (1f - p) * (1f - p) // fast at first, then slowing
            val fade = (1f - p).let { it * it }
            val r = b[2].dp.toPx() * (0.25f + 0.75f * open)
            val c = colours[b[3].toInt()]
            val cx = b[0] * size.width
            val cy = b[1] * size.height + p * p * 14.dp.toPx() // a little fall
            for (k in 0 until 22) {
                val a = k / 22f * 2f * PI.toFloat()
                val px = cx + kotlin.math.cos(a) * r
                val py = cy + sin(a) * r
                drawCircle(c.copy(alpha = 0.25f * fade), radius = 4.dp.toPx(), center = Offset(px, py))
                drawCircle(c.copy(alpha = 0.95f * fade), radius = 1.6.dp.toPx(), center = Offset(px, py))
                drawCircle(c.copy(alpha = 0.5f * fade), radius = 1.dp.toPx(), center = Offset(cx + kotlin.math.cos(a) * r * 0.6f, cy + sin(a) * r * 0.6f))
            }
        }
    }
}

/** A starry sky, twinkling, with a soft moon glow. */
@Composable
private fun Stars(modifier: Modifier, moving: Boolean) {
    val stars = remember { seeded(140, 21, 4) }
    val time = rememberLoop(moving, 7_000, 0.2f)
    Canvas(modifier.graphicsLayer {}) {
        val t = time()
        drawCircle(
            Brush.radialGradient(listOf(Color(0x52F4E7B5), Color(0x14F4E7B5), Color.Transparent), center = Offset(size.width * 0.76f, size.height * 0.22f), radius = size.width * 0.15f),
            radius = size.width * 0.15f,
            center = Offset(size.width * 0.76f, size.height * 0.22f),
        )
        drawRect(Brush.radialGradient(listOf(Color(0x59463C8C), Color.Transparent), center = Offset(size.width / 2, size.height), radius = size.width * 0.5f))
        for (s in stars) {
            val twinkle = 0.65f + 0.35f * sin((t * (1f + s[3] * 2f) + s[3]) * 2f * PI.toFloat())
            drawCircle(Color.White.copy(alpha = (0.2f + s[2] * 0.7f) * twinkle), radius = (0.5f + s[2] * 1.1f).dp.toPx(), center = Offset(s[0] * size.width, s[1] * size.height * 0.85f))
        }
    }
}

// ---------------- On now on your channels ----------------

/** Your favourite channels (or the ones you watched last), and the guide for the first few. */
class OnNowList(val channels: List<Channel>, val listings: Map<Int, List<Programme>>)

@Composable
fun rememberOnNowList(vm: AppViewModel, repo: Repository, count: Int): OnNowList {
    val favs = vm.favourites.filter { it.type == ContentType.LIVE }
    val all = favs.ifEmpty { vm.recentChannels.toList() }.take(40).map { it.toChannel() }
    val shown = all.take(count)
    val ids = shown.map { it.streamId }
    // What's already loaded shows at once; the rest one channel at a time, gently.
    val listings by produceState<Map<Int, List<Programme>>>(emptyMap(), repo, ids) {
        val out = HashMap<Int, List<Programme>>()
        for (c in shown) repo.cachedEpg(EpgAlias.of(c.streamId))?.let { out[c.streamId] = it }
        value = HashMap(out)
        delay(600)
        for (c in shown) {
            if (c.streamId in out) continue
            val list = try {
                repo.epg(EpgAlias.of(c.streamId))
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                null
            } ?: continue
            out[c.streamId] = list
            value = HashMap(out)
        }
    }
    return OnNowList(all, listings)
}

/**
 * One channel and what's on it now: the logo and name, the programme, and a red line for how far
 * through it is, with when it ends. OK watches. Like the other cards: white with black text when
 * it's highlighted.
 */
@Composable
fun OnNowCard(
    channel: Channel,
    programme: Programme?,
    now: Long,
    width: Dp,
    focusKey: String,
    requester: FocusRequester? = null,
    onLongClick: (() -> Unit)? = null,
    onClick: () -> Unit,
) {
    FocusBox(
        modifier = Modifier.size(width = width, height = 84.dp),
        focusKey = focusKey,
        shape = RoundedCornerShape(16.dp),
        focusedScale = 1.06f,
        color = Color(0x1FFFFFFF),
        focusedColor = Color.White,
        requester = requester,
        onClick = onClick,
        onLongClick = onLongClick,
    ) { f ->
        val text = if (f) Color.Black else Ember.Text
        val dim = if (f) Color(0xFF55555C) else Ember.Dim
        Column(Modifier.fillMaxSize().padding(start = 12.dp, end = 12.dp, top = 9.dp, bottom = 10.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                ChannelLogo(channel.icon, channel.name, Modifier.size(width = 36.dp, height = 22.dp))
                Spacer(Modifier.width(8.dp))
                Text(channelParts(channel.name).first, style = Txt.Caption.copy(fontWeight = FontWeight.SemiBold), color = dim, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
            Spacer(Modifier.height(5.dp))
            Text(programme?.title?.ifBlank { null } ?: "Watch live", style = Txt.Label.copy(fontWeight = FontWeight.SemiBold), color = text, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Spacer(Modifier.weight(1f))
            Row(verticalAlignment = Alignment.CenterVertically) {
                val progress = programme?.let { p ->
                    if (p.end > p.start) ((now - p.start).toFloat() / (p.end - p.start)).coerceIn(0f, 1f) else null
                }
                Box(Modifier.weight(1f).height(3.dp).clip(RoundedCornerShape(2.dp)).background(if (f) Color(0x1F000000) else Color(0x2EFFFFFF))) {
                    if (progress != null) Box(Modifier.fillMaxHeight().fillMaxWidth(progress.coerceAtLeast(0.03f)).background(Ember.Live))
                }
                if (programme != null) {
                    Spacer(Modifier.width(8.dp))
                    Text("until ${formatClock(programme.end)}", style = Txt.Caption.copy(fontSize = 11.sp), color = dim, maxLines = 1)
                }
            }
        }
    }
}

/** The last card in the row: the forecast (or setting the town, before there is one). */
@Composable
fun ForecastCard(weather: WeatherNow?, hasPlace: Boolean, width: Dp, requester: FocusRequester? = null, onLongClick: (() -> Unit)? = null, onClick: () -> Unit) {
    FocusBox(
        modifier = Modifier.size(width = width, height = 84.dp),
        focusKey = "home:banner:forecast",
        shape = RoundedCornerShape(16.dp),
        focusedScale = 1.06f,
        color = Color(0x1FFFFFFF),
        focusedColor = Color.White,
        requester = requester,
        onClick = onClick,
        onLongClick = onLongClick,
    ) { f ->
        Column(Modifier.fillMaxSize(), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
            WeatherIcon(weather?.code ?: 2, weather?.day ?: true, 26.dp)
            Spacer(Modifier.height(6.dp))
            Text(
                if (hasPlace) "Forecast" else "Add weather",
                style = Txt.Label.copy(fontWeight = FontWeight.SemiBold),
                color = if (f) Color.Black else Ember.Text,
                maxLines = 1,
            )
        }
    }
}

/** "● ON NOW ON YOUR CHANNELS" over the cards. */
@Composable
fun OnNowLabel(modifier: Modifier = Modifier) {
    Row(modifier, verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.size(7.dp).clip(RoundedCornerShape(4.dp)).background(Ember.Live))
        Spacer(Modifier.width(8.dp))
        Text("ON NOW ON YOUR CHANNELS", style = Txt.Caption.copy(fontWeight = FontWeight.SemiBold, letterSpacing = 1.3.sp), color = Ember.Text.copy(alpha = 0.8f))
    }
}

/** The phone's version: the cards in a row you can swipe, the forecast at the end. */
@Composable
fun PhoneOnNowRow(vm: AppViewModel, list: OnNowList, onForecast: () -> Unit) {
    val now = LocalNow.current
    val shown = list.channels.take(6)
    LazyRow(
        contentPadding = PaddingValues(horizontal = 20.dp),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        items(shown, key = { it.streamId }) { c ->
            val programme = list.listings[c.streamId]?.firstOrNull { it.start <= now && it.end > now }
            OnNowCard(c, programme, now, 168.dp, "home:onnow:${c.streamId}") {
                vm.push(Screen.PlayLive(list.channels, list.channels.indexOf(c).coerceAtLeast(0)))
            }
        }
        item(key = "forecast") { ForecastCard(vm.weather, vm.weatherPlace != null, 104.dp, onClick = onForecast) }
    }
}

// ---------------- Choosing the background ----------------

@Composable
fun ClockThemeDialog(vm: AppViewModel, onDone: () -> Unit) {
    val isTv = LocalIsTv.current
    androidx.compose.ui.window.Dialog(onDismissRequest = onDone) {
        DialogBackdropBlur()
        Column(
            Modifier
                .padding(16.dp)
                .width(if (isTv) 540.dp else 440.dp)
                .liquidGlass(RoundedCornerShape(28.dp))
                .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            SheetTitle("Clock background", subtitle = "Behind the clock at the top of Home. It stands still and dims late at night.")
            Spacer(Modifier.height(14.dp))
            Column(
                Modifier.fillMaxWidth().weight(1f, fill = false).verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                val tick: @Composable (Boolean) -> Unit = { dark ->
                    Icon(EmberIcons.Check, contentDescription = "Chosen", tint = if (dark) Color.Black else Ember.Text, modifier = Modifier.size(18.dp))
                }
                for (theme in ClockTheme.values()) {
                    val chosen = theme == vm.clockTheme
                    SheetOption(
                        theme.title,
                        focusKey = "clocktheme:${theme.id}",
                        subtitle = if (theme == ClockTheme.AUTO) "${theme.about}. Now: ${autoClockTheme(vm.weather).title}" else theme.about,
                        autoFocus = chosen,
                        trailing = if (chosen) tick else null,
                    ) {
                        vm.chooseClockTheme(theme)
                        onDone()
                    }
                }
            }
        }
    }
}
