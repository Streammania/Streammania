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
                LazyColumn(
                    modifier = modifier.fillMaxHeight(),
                    state = vm.live.list(categoryId),
                    contentPadding = PaddingValues(top = 8.dp, bottom = 40.dp, start = 6.dp, end = 6.dp),
                    verticalArrangement = Arrangement.spacedBy(if (vm.compactLists) 4.dp else 6.dp),
                ) {
                    itemsIndexed(channels, key = { _, ch -> ch.streamId }) { index, ch ->
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
    val programmes by produceState(repo.cachedEpg(id), repo, id) {
        if (value == null) {
            delay(250)
            value = repo.epg(id)
        }
    }
    return programmes
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
        modifier = Modifier.fillMaxWidth().height(if (compact) 54.dp else if (tvRow) 86.dp else 70.dp),
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
            // Phones: no long provider numbers, just the logo and name, as in Apple's TV app.
            if (!phone) Text(
                if (channel.num > 0) channel.num.toString() else "",
                style = Txt.Caption.copy(fontSize = 11.sp),
                color = faint,
                modifier = Modifier.width(38.dp),
            )
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
                    if (next != null && !compact && tvRow) {
                        Spacer(Modifier.height(4.dp))
                        Text("Next: ${formatClock(next.start)} ${next.title}", style = Txt.Caption.copy(fontSize = 12.sp), color = faint, maxLines = 1, overflow = TextOverflow.Ellipsis)
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
            .glassSurface(RoundedCornerShape(20.dp), Ember.Panel)
            .padding(18.dp)
    ) {
        if (channel == null) {
            Text("Pick a channel", style = Txt.Headline)
            Spacer(Modifier.height(8.dp))
            Text("Choose a channel to see what's on.", style = Txt.Body, color = Ember.Dim)
        } else {
            PreviewDetails(vm, repo, channel)
        }
        Spacer(Modifier.weight(1f))
        Box(Modifier.fillMaxWidth()) {
            RemoteHints(listOf(Hint("OK", "Watch"), Hint("HOLD OK", "Favourite")))
        }
    }
}

@Composable
private fun PreviewDetails(vm: AppViewModel, repo: Repository, channel: Channel) {
    val now = LocalNow.current
    val programmes = rememberProgrammes(repo, channel.streamId)
    val onNow = programmes?.firstOrNull { it.isLive(now) }
    // The live picture with rounded corners and the programme's name over its foot, like Apple TV.
    Box(Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp))) {
        LivePreview(vm, channel, Modifier.fillMaxWidth())
        Column(
            Modifier
                .align(Alignment.BottomStart)
                .fillMaxWidth()
                .background(androidx.compose.ui.graphics.Brush.verticalGradient(listOf(Color.Transparent, Color(0xD9000000))))
                .padding(start = 12.dp, end = 12.dp, top = 26.dp, bottom = 10.dp)
        ) {
            if (onNow != null) {
                Box(Modifier.clip(RoundedCornerShape(4.dp)).background(Ember.Live).padding(horizontal = 5.dp, vertical = 1.dp)) {
                    Text("LIVE", style = Txt.Caption.copy(fontSize = 10.sp, fontWeight = FontWeight.Bold), color = Color.White)
                }
                Spacer(Modifier.height(4.dp))
                Text(onNow.title, style = Txt.Label.copy(fontWeight = FontWeight.Bold), color = Color.White, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
            Text(
                channelParts(channel.name).first + (onNow?.let { "  ·  until ${formatClock(it.end)}" } ?: ""),
                style = Txt.Caption,
                color = Color(0xFFCFCFD4),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
    Spacer(Modifier.height(14.dp))
    when {
        programmes == null -> SkeletonLines(Modifier.fillMaxWidth(), widths = listOf(0.7f, 0.9f, 0.6f, 0.8f))
        programmes.none { it.end > now } -> Text("No guide information for this channel.", style = Txt.Body, color = Ember.Faint)
        else -> {
            // What's on now is on the picture; underneath, a short timeline of what's later.
            if (onNow != null && onNow.description.isNotBlank()) {
                Text(onNow.description, style = Txt.Caption.copy(fontSize = 13.sp), color = Ember.Dim, maxLines = 3, overflow = TextOverflow.Ellipsis)
                Spacer(Modifier.height(14.dp))
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
