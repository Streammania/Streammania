package app.embertv.ui

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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.embertv.data.ALL
import app.embertv.data.Category
import app.embertv.data.Channel
import app.embertv.data.ContentType
import app.embertv.data.FAVOURITES
import app.embertv.data.Programme
import app.embertv.data.Repository
import app.embertv.data.favouriteKey
import app.embertv.data.toChannel
import kotlinx.coroutines.delay

@Composable
fun LiveSection(vm: AppViewModel) {
    val repo = vm.repo ?: return
    val section = vm.live
    val cats by rememberLoad(repo, vm.reloadToken) { repo.liveCategories() }
    when (val c = cats) {
        is LoadResult.Loading -> SkeletonPage(posters = false)
        is LoadResult.Err -> ErrorView(c.message) { vm.reload() }
        is LoadResult.Ok -> {
            val categories = remember(c.value) {
                listOf(Category(FAVOURITES, "Favourites"), Category(ALL, "All channels")) + c.value
            }
            val selectedId = section.categoryId ?: c.value.firstOrNull()?.id ?: ALL
            val select = rememberCategorySelector(section)
            var focusedChannel by remember { mutableStateOf<Channel?>(null) }
            Row(Modifier.fillMaxSize().padding(start = 44.dp, end = 48.dp, top = 6.dp)) {
                CategoryRail(
                    categories = categories,
                    selectedId = selectedId,
                    state = section.rail,
                    keyPrefix = "live",
                    onFocusCategory = { select(it.id, false) },
                    onSelectCategory = { select(it.id, true) },
                )
                Spacer(Modifier.width(20.dp))
                ChannelList(
                    vm = vm,
                    repo = repo,
                    categoryId = selectedId,
                    modifier = Modifier.weight(1f),
                    onFocusChannel = { focusedChannel = it },
                )
                Spacer(Modifier.width(24.dp))
                ChannelPreview(vm, repo, focusedChannel, Modifier.width(300.dp).fillMaxHeight())
            }
        }
    }
}

@Composable
private fun ChannelList(
    vm: AppViewModel,
    repo: Repository,
    categoryId: String,
    modifier: Modifier,
    onFocusChannel: (Channel) -> Unit,
) {
    val favSnapshot = if (categoryId == FAVOURITES) vm.favourites.filter { it.type == ContentType.LIVE } else null
    val load by rememberLoad(repo, categoryId, vm.reloadToken, favSnapshot) {
        favSnapshot?.let { favs -> repo.resolveAll(favs.map { it.toChannel() }) } ?: repo.channels(categoryId)
    }
    when (val r = load) {
        is LoadResult.Loading -> SkeletonRows(modifier)
        is LoadResult.Err -> ErrorView(r.message, modifier) { vm.reload() }
        is LoadResult.Ok -> {
            // Each channel once, whatever qualities the provider lists it in (like the guide).
            val channels = remember(r.value, vm.qualityChoice, vm.channelsOnce) { channelsOnce(r.value, vm.qualityChoice, vm.channelsOnce) }
            if (channels.isEmpty()) {
                EmptyView(
                    if (categoryId == FAVOURITES) "${holdPrompt()} any channel to add it to your favourites."
                    else "No channels in this category.",
                    modifier,
                )
            } else {
                val channelKeys = remember(channels) { uniqueKeys(channels) { it.streamId } }
                LazyColumn(
                    modifier = modifier.fillMaxHeight(),
                    state = vm.live.list(categoryId),
                    contentPadding = PaddingValues(top = 8.dp, bottom = 40.dp, start = 6.dp, end = 6.dp),
                    verticalArrangement = Arrangement.spacedBy(if (vm.compactLists) 4.dp else 6.dp),
                ) {
                    itemsIndexed(channels, key = { i, _ -> channelKeys[i] }) { index, ch ->
                        ChannelRow(
                            repo = repo,
                            channel = ch,
                            compact = vm.compactLists,
                            focusKey = "live:$categoryId:${ch.streamId}",
                            isFavourite = vm.favKeys.contains(favouriteKey(ContentType.LIVE, ch.streamId)),
                            onFocused = { onFocusChannel(ch) },
                            onClick = { vm.push(Screen.PlayLive(channels, index)) },
                            onLongClick = { vm.toggleFavourite(vm.favouriteOf(ch)) },
                        )
                    }
                }
            }
        }
    }
}

/** Loads now/next for a channel, after a short pause so fast scrolling doesn't flood the server. */
@Composable
fun rememberProgrammes(repo: Repository, streamId: Int): List<Programme>? {
    // A quality copy without its own listings reads them from the copy that has them.
    val id = EpgAlias.of(streamId)
    // A fresh state for each channel: otherwise moving to another channel kept the last one's
    // listings (and the preview panel said "No listings" for channels that have them).
    return androidx.compose.runtime.key(repo, id) {
        val programmes by produceState(repo.cachedEpg(id), repo, id) {
            if (value == null) {
                // A short pause first: rows you scroll straight past are gone before they ask, so the
                // ones you stop on aren't queued behind them (the provider only takes a couple at once).
                delay(400)
                value = repo.epg(id)
            }
        }
        programmes
    }
}

@Composable
fun ChannelRow(
    repo: Repository,
    channel: Channel,
    focusKey: String,
    isFavourite: Boolean,
    onFocused: () -> Unit,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
    compact: Boolean = false,
) {
    val now = LocalNow.current
    val programmes = rememberProgrammes(repo, channel.streamId)
    val current = programmes?.firstOrNull { it.isLive(now) }
    val next = current?.let { c -> programmes.firstOrNull { it.start >= c.end } }
    val tvRow = LocalIsTv.current
    FocusBox(
        modifier = Modifier.fillMaxWidth().height(if (compact) 54.dp else if (tvRow) 78.dp else 70.dp),
        focusKey = focusKey,
        shape = RoundedCornerShape(16.dp),
        focusedScale = 1.02f,
        color = Color(0x0BFFFFFF),
        focusedColor = Color.White,
        glassEdge = false,
        onFocusChange = { if (it) onFocused() },
        onClick = onClick,
        onLongClick = onLongClick,
    ) { focused ->
        val phone = !LocalIsTv.current
        // The row you're on turns white, so its text turns dark.
        val text = if (focused) Color.Black else Ember.Text
        val dim = if (focused) Color(0xFF55555C) else Ember.Dim
        val faint = if (focused) Color(0xFF77777C) else Ember.Faint
        Row(Modifier.fillMaxSize().padding(horizontal = 14.dp), verticalAlignment = Alignment.CenterVertically) {
            // No long provider numbers (TV or phone), just the logo and name, as in Apple's TV app.
            // Logos in a small frosted tile, so every shape and size lines up.
            Box(
                Modifier
                    .size(if (compact) 52.dp else 66.dp, if (compact) 36.dp else 44.dp)
                    .clip(RoundedCornerShape(10.dp))
                    .background(if (focused) Color(0x14000000) else Color(0x14FFFFFF))
                    .padding(4.dp),
                contentAlignment = Alignment.Center,
            ) {
                ChannelLogo(channel.icon, channel.name, Modifier.fillMaxSize())
            }
            Spacer(Modifier.width(14.dp))
            Column(Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    val (clean, tag) = channelParts(channel.name)
                    Text(
                        clean,
                        style = Txt.Headline.copy(fontSize = 16.sp, fontWeight = FontWeight.SemiBold),
                        color = text,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f, fill = false),
                    )
                    if (tag != null) {
                        Spacer(Modifier.width(8.dp))
                        ChannelQualityTag(tag)
                    }
                }
                if (current != null) {
                    Spacer(Modifier.height(if (compact) 1.dp else 3.dp))
                    // What's on in bold, until when, a full-width bar, then what's next.
                    Text(
                        androidx.compose.ui.text.buildAnnotatedString {
                            withStyle(androidx.compose.ui.text.SpanStyle(fontWeight = FontWeight.SemiBold, color = text)) { append(current.title) }
                            withStyle(androidx.compose.ui.text.SpanStyle(color = dim)) { append("  ·  until ${formatClock(current.end)}") }
                        },
                        style = Txt.Caption.copy(fontSize = 14.sp),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Spacer(Modifier.height(if (compact) 3.dp else 6.dp))
                    Box(Modifier.fillMaxWidth().height(3.dp).clip(RoundedCornerShape(2.dp)).background(if (focused) Color(0x26000000) else Color(0x2EFFFFFF))) {
                        Box(Modifier.fillMaxHeight().fillMaxWidth(current.progress(now)).background(if (focused) Color.Black else Color.White))
                    }
                }
            }
            if (isFavourite) {
                Spacer(Modifier.width(8.dp))
                StarMark(15.dp)
            }
        }
    }
}

@Composable
private fun ChannelPreview(vm: AppViewModel, repo: Repository, channel: Channel?, modifier: Modifier) {
    Column(
        modifier
            .padding(top = 8.dp, bottom = 24.dp)
            .glassSurface(RoundedCornerShape(24.dp), Ember.Panel)
            .padding(14.dp)
    ) {
        if (channel == null) {
            LivePreview(vm, null, Modifier.fillMaxWidth())
            Spacer(Modifier.height(16.dp))
            Text("Pick a channel", style = Txt.Headline.copy(fontWeight = FontWeight.Bold), modifier = Modifier.padding(horizontal = 4.dp))
            Spacer(Modifier.height(6.dp))
            Text("Its picture and what's on show here.", style = Txt.Body, color = Ember.Dim, modifier = Modifier.padding(horizontal = 4.dp))
        } else {
            PreviewDetails(vm, repo, channel)
        }
        Spacer(Modifier.weight(1f))
        Box(Modifier.fillMaxWidth().padding(horizontal = 4.dp)) {
            RemoteHints(listOf(Hint("OK", "Watch"), Hint("HOLD OK", "Favourite")))
        }
    }
}

/**
 * Apple TV style: the live picture on top, then the channel, what's on in big bold type, its time
 * and progress, a short description, and a little timeline of what's later.
 */
@Composable
private fun PreviewDetails(vm: AppViewModel, repo: Repository, channel: Channel) {
    val now = LocalNow.current
    val short = rememberProgrammes(repo, channel.streamId)
    // The quick listings sometimes miss what's on now; the full day's guide fills the gap.
    val needFull = short != null && short.none { it.isLive(now) }
    val full = if (needFull) rememberFullProgrammes(repo, channel.streamId) else null
    val programmes = when {
        full != null && full.any { it.end > now } -> full.sortedBy { it.start }
        else -> short
    }
    val onNow = programmes?.firstOrNull { it.isLive(now) }
    LivePreview(vm, channel, Modifier.fillMaxWidth())
    Spacer(Modifier.height(16.dp))
    Column(Modifier.padding(horizontal = 4.dp)) {
        val (name, tag) = channelParts(channel.name)
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(name.uppercase(), style = Txt.Caption.copy(fontWeight = FontWeight.SemiBold, letterSpacing = 1.2.sp), color = Ember.Dim, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f, fill = false))
            if (tag != null) {
                Spacer(Modifier.width(8.dp))
                ChannelQualityTag(tag)
            }
        }
        Spacer(Modifier.height(6.dp))
        when {
            programmes == null || (needFull && full == null) -> SkeletonLines(Modifier.fillMaxWidth(), widths = listOf(0.85f, 0.6f, 0.9f, 0.7f))
            onNow == null && programmes.none { it.end > now } -> {
                Text("No listings", style = Txt.Title.copy(fontWeight = FontWeight.Bold))
                Spacer(Modifier.height(4.dp))
                Text("Your provider has no guide for this channel right now. It still plays.", style = Txt.Caption.copy(fontSize = 14.sp), color = Ember.Dim)
            }
            else -> {
                if (onNow != null) {
                    Text(onNow.title, style = Txt.Title.copy(fontWeight = FontWeight.Bold), maxLines = 2, overflow = TextOverflow.Ellipsis)
                    Spacer(Modifier.height(4.dp))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(Modifier.size(7.dp).clip(CircleShape).background(Ember.Live))
                        Spacer(Modifier.width(6.dp))
                        Text(
                            "${formatClock(onNow.start)} – ${formatClock(onNow.end)}  ·  ${((onNow.end - now) / 60).coerceAtLeast(1)} min left",
                            style = Txt.Caption.copy(fontSize = 14.sp, fontWeight = FontWeight.Medium),
                            color = Ember.Dim,
                            maxLines = 1,
                        )
                    }
                    Spacer(Modifier.height(8.dp))
                    Box(Modifier.fillMaxWidth().height(4.dp).clip(RoundedCornerShape(2.dp)).background(Color(0x2EFFFFFF))) {
                        Box(Modifier.fillMaxHeight().fillMaxWidth(onNow.progress(now)).background(Color.White))
                    }
                    if (onNow.description.isNotBlank()) {
                        Spacer(Modifier.height(10.dp))
                        Text(onNow.description, style = Txt.Caption.copy(fontSize = 13.sp, lineHeight = 18.sp), color = Ember.Dim, maxLines = 3, overflow = TextOverflow.Ellipsis)
                    }
                    Spacer(Modifier.height(16.dp))
                }
                val later = programmes.filter { it.start >= now }.take(3)
                if (later.isNotEmpty()) {
                    Text("LATER", style = Txt.Caption.copy(fontSize = 11.sp, fontWeight = FontWeight.SemiBold, letterSpacing = 1.2.sp), color = Ember.Faint)
                    Spacer(Modifier.height(8.dp))
                    later.forEachIndexed { i, p ->
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(formatClock(p.start), style = Txt.Caption.copy(fontWeight = FontWeight.SemiBold), color = Ember.Dim, modifier = Modifier.width(48.dp))
                            Box(Modifier.size(6.dp).clip(CircleShape).background(Color(0x80FFFFFF)))
                            Spacer(Modifier.width(10.dp))
                            Text(p.title, style = Txt.Label.copy(fontSize = 15.sp), color = Ember.Text, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        }
                        if (i < later.lastIndex) Spacer(Modifier.height(10.dp))
                    }
                }
            }
        }
    }
}
