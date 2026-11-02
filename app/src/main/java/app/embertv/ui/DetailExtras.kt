package app.embertv.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.background
import androidx.compose.ui.draw.clip
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import app.embertv.data.CastMember
import app.embertv.data.ContentType
import app.embertv.data.Movie
import app.embertv.data.Series
import app.embertv.data.favouriteKey

/** "Cast & Crew": round photos with names, like Apple TV. */
@Composable
fun CastRow(cast: List<CastMember>, edge: Dp) {
    if (cast.isEmpty()) return
    val isTv = LocalIsTv.current
    // Picking someone shows what else of theirs is in your lists.
    var picked by remember { mutableStateOf<CastMember?>(null) }
    picked?.let { p -> ActorSheet(LocalAppVm.current, p) { picked = null } }
    val size = if (isTv) 96.dp else 76.dp
    Column(Modifier.fillMaxWidth()) {
        Text("Cast & Crew", style = Txt.Headline, modifier = Modifier.padding(start = edge))
        LazyRow(
            contentPadding = PaddingValues(horizontal = edge, vertical = 12.dp),
            horizontalArrangement = Arrangement.spacedBy(if (isTv) 22.dp else 14.dp),
        ) {
            itemsIndexed(cast, key = { i, p -> "$i:${p.name}" }) { _, p ->
                var focused by remember { mutableStateOf(false) }
                Column(Modifier.width(size + 16.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                    FocusBox(
                        modifier = Modifier.size(size),
                        focusKey = "cast:${p.name}",
                        shape = CircleShape,
                        focusedScale = 1.1f,
                        color = Ember.Card,
                        focusedColor = Ember.Card,
                        onFocusChange = { focused = it },
                        onClick = { if (p.id > 0) picked = p },
                    ) { _ ->
                        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                            Text(initials(p.name), style = Txt.Headline, color = Ember.Dim)
                        }
                        NetImage(p.photo, Modifier.fillMaxSize())
                    }
                    Spacer(Modifier.height(8.dp))
                    Text(
                        p.name,
                        style = Txt.Caption,
                        color = if (focused) Ember.Text else Ember.Dim,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        textAlign = TextAlign.Center,
                    )
                    if (p.role.isNotBlank()) {
                        Text(p.role, style = Txt.Caption, color = Ember.Faint, maxLines = 1, overflow = TextOverflow.Ellipsis, textAlign = TextAlign.Center)
                    }
                }
            }
        }
    }
}

/**
 * Someone from the cast: their photo and name, then the films and series in your lists they're in,
 * best known first. Picking one opens it.
 */
@Composable
private fun ActorSheet(vm: AppViewModel, person: CastMember, onDismiss: () -> Unit) {
    val isTv = LocalIsTv.current
    val found by androidx.compose.runtime.produceState<Pair<List<Movie>, List<Series>>?>(null, person.id) { value = vm.withActor(person) ?: (emptyList<Movie>() to emptyList()) }
    androidx.compose.ui.window.Dialog(onDismissRequest = onDismiss, properties = androidx.compose.ui.window.DialogProperties(usePlatformDefaultWidth = false)) {
        DialogBackdropBlur()
        Column(
            Modifier
                .width(if (isTv) 760.dp else 360.dp)
                .clip(androidx.compose.foundation.shape.RoundedCornerShape(28.dp))
                .background(dialogPanelColor())
                .padding(vertical = if (isTv) 10.dp else 20.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Box(Modifier.size(if (isTv) 96.dp else 80.dp).glassSurface(CircleShape, Ember.Card), contentAlignment = Alignment.Center) {
                Text(initials(person.name), style = Txt.Headline, color = Ember.Dim)
                NetImage(person.photo, Modifier.fillMaxSize().clip(CircleShape))
            }
            Spacer(Modifier.height(12.dp))
            Text(person.name, style = Txt.Title, textAlign = TextAlign.Center)
            Text("In your films and series", style = Txt.Caption, color = Ember.Dim)
            Spacer(Modifier.height(16.dp))
            val f = found
            when {
                f == null -> SkeletonPosters(Modifier.height(if (isTv) 230.dp else 200.dp).padding(horizontal = 20.dp), minSize = if (isTv) 120.dp else 96.dp)
                f.first.isEmpty() && f.second.isEmpty() -> Text(
                    "Nothing else with ${person.name} in your lists.",
                    style = Txt.Body,
                    color = Ember.Dim,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.padding(horizontal = 24.dp, vertical = 16.dp),
                )
                else -> androidx.compose.foundation.lazy.LazyColumn(Modifier.heightIn(max = if (isTv) 440.dp else 480.dp)) {
                    if (f.first.isNotEmpty()) item(key = "films") {
                        Column {
                            SheetSection("Films", Modifier.padding(start = 20.dp))
                            LazyRow(contentPadding = PaddingValues(horizontal = 20.dp, vertical = 6.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                                items(f.first, key = { it.streamId }) { m ->
                                    PosterCard(
                                        title = m.name,
                                        image = m.poster,
                                        focusKey = "actor:m:${m.streamId}",
                                        modifier = Modifier.width(if (isTv) 120.dp else 104.dp),
                                        badge = badgeText(m.qualities()),
                                        watched = vm.movieWatched(m),
                                        onClick = {
                                            onDismiss()
                                            vm.push(Screen.MovieDetail(m))
                                        },
                                    )
                                }
                            }
                        }
                    }
                    if (f.second.isNotEmpty()) item(key = "series") {
                        Column {
                            SheetSection("Series", Modifier.padding(start = 20.dp, top = 10.dp))
                            LazyRow(contentPadding = PaddingValues(horizontal = 20.dp, vertical = 6.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                                items(f.second, key = { it.seriesId }) { sr ->
                                    PosterCard(
                                        title = sr.name,
                                        image = sr.cover,
                                        focusKey = "actor:s:${sr.seriesId}",
                                        modifier = Modifier.width(if (isTv) 120.dp else 104.dp),
                                        onClick = {
                                            onDismiss()
                                            vm.push(Screen.SeriesDetail(sr))
                                        },
                                    )
                                }
                            }
                        }
                    }
                }
            }
            if (!isTv) {
                Spacer(Modifier.height(10.dp))
                PillButton("Close", Modifier.padding(horizontal = 20.dp).fillMaxWidth(), onClick = onDismiss)
            } else {
                Spacer(Modifier.height(8.dp))
                RemoteHints(listOf(Hint("OK", "Open"), Hint("BACK", "Close")))
            }
        }
    }
}

/** "More Like This" for a film. */
@Composable
fun MoreMoviesRow(vm: AppViewModel, movies: List<Movie>, edge: Dp) {
    if (movies.isEmpty()) return
    val isTv = LocalIsTv.current
    Column(Modifier.fillMaxWidth()) {
        Text("More Like This", style = Txt.Headline, modifier = Modifier.padding(start = edge))
        LazyRow(
            contentPadding = PaddingValues(horizontal = edge, vertical = 12.dp),
            horizontalArrangement = Arrangement.spacedBy(if (isTv) 20.dp else 12.dp),
        ) {
            items(movies, key = { it.streamId }) { m ->
                PosterCard(
                    title = m.name,
                    image = m.poster,
                    focusKey = "more:m:${m.streamId}",
                    modifier = Modifier.width(if (isTv) 120.dp else 104.dp),
                    badge = badgeText(m.qualities()),
                    watched = vm.movieWatched(m),
                    favourite = vm.favKeys.contains(favouriteKey(ContentType.MOVIE, m.streamId)),
                    onClick = { vm.push(Screen.MovieDetail(m)) },
                    onLongClick = { vm.toggleFavourite(vm.favouriteOf(m)) },
                )
            }
        }
    }
}

/** "More Like This" for a show. */
@Composable
fun MoreSeriesRow(vm: AppViewModel, series: List<Series>, edge: Dp) {
    if (series.isEmpty()) return
    val isTv = LocalIsTv.current
    Column(Modifier.fillMaxWidth()) {
        Text("More Like This", style = Txt.Headline, modifier = Modifier.padding(start = edge))
        LazyRow(
            contentPadding = PaddingValues(horizontal = edge, vertical = 12.dp),
            horizontalArrangement = Arrangement.spacedBy(if (isTv) 20.dp else 12.dp),
        ) {
            items(series, key = { it.seriesId }) { s ->
                PosterCard(
                    title = s.name,
                    image = s.cover,
                    focusKey = "more:s:${s.seriesId}",
                    modifier = Modifier.width(if (isTv) 120.dp else 104.dp),
                    badge = badgeText(s.qualities()),
                    favourite = vm.favKeys.contains(favouriteKey(ContentType.SERIES, s.seriesId)),
                    onClick = { vm.push(Screen.SeriesDetail(s)) },
                    onLongClick = { vm.toggleFavourite(vm.favouriteOf(s)) },
                )
            }
        }
    }
}
