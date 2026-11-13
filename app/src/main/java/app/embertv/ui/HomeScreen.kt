package app.embertv.ui

import kotlinx.coroutines.launch
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.animation.fadeOut
import androidx.compose.animation.fadeIn
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.expandVertically
import androidx.compose.animation.AnimatedVisibility
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusDirection
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.delay

private class TvTab(val title: String, val icon: ImageVector? = null)

// Same order as Tabs: Home first. Search and Settings are icons, as on Apple TV.
private val TABS = listOf(
    TvTab("Home"),
    TvTab("Live TV"),
    TvTab("Guide"),
    TvTab("Movies"),
    TvTab("Series"),
    TvTab("Favourites"),
    TvTab("Search", EmberIcons.Search),
    TvTab("Settings", EmberIcons.Settings),
)

@Composable
fun HomeScreen(vm: AppViewModel) {
    val tabRequesters = remember { List(TABS.size) { FocusRequester() } }
    var focusedTab by remember { mutableIntStateOf(-1) }
    val focusManager = LocalFocusManager.current

    val scope = rememberCoroutineScope()
    // Back from the content jumps up to the tab bar; back from the tab bar leaves the app.
    // Shows the bar again for a moment so Back can move up to it.
    var reveal by remember { mutableStateOf(false) }
    LaunchedEffect(focusedTab) {
        if (focusedTab >= 0) reveal = false
        // Up on the menu over Home: the page goes back to the top so the menu never sits on the
        // banner's words.
        if (focusedTab == Tabs.HOME) runCatching { vm.home.list("tv").animateScrollToItem(0) }
    }
    // "See all" on Home switched tab: move the highlight there too.
    LaunchedEffect(vm.tabJump) {
        if (vm.tabJump == 0) return@LaunchedEffect
        delay(120)
        runCatching { tabRequesters[vm.tab].requestFocus() }
    }
    BackHandler(enabled = focusedTab < 0) {
        if (vm.tab == Tabs.HOME && vm.homeBarHidden) {
            reveal = true
            scope.launch {
                delay(60)
                runCatching { tabRequesters[vm.tab].requestFocus() }
            }
        } else if (vm.guideFullScreen) {
            // The full-screen guide hides the tabs; show them before moving up to them.
            vm.guideFullScreen = false
            scope.launch {
                delay(60)
                runCatching { tabRequesters[vm.tab].requestFocus() }
            }
        } else {
            runCatching { tabRequesters[vm.tab].requestFocus() }
        }
    }
    // Back on the tab bar returns to Home before leaving the app.
    BackHandler(enabled = focusedTab >= 0 && vm.tab != Tabs.HOME) {
        runCatching { tabRequesters[Tabs.HOME].requestFocus() }
    }

    LaunchedEffect(Unit) {
        delay(80)
        val pending = vm.restoreFocusKey
        if (pending != null) {
            // Back from a player or detail page: the item we came from re-focuses itself.
            var waited = 0
            while (vm.restoreFocusKey == pending && waited < 1800) {
                delay(100)
                waited += 100
            }
            if (vm.restoreClaimed) return@LaunchedEffect
        }
        runCatching { tabRequesters[vm.tab].requestFocus() }
    }

    val hideBar = focusedTab < 0 && !reveal &&
        ((vm.tab == Tabs.GUIDE && vm.guideFullScreen) || (vm.tab == Tabs.HOME && vm.homeBarHidden))
    // On Home the menu floats over the picture, which runs right up to the top as on Apple TV.
    val overHome = vm.tab == Tabs.HOME
    val homeEntry = remember { HomeEntry() }
    val density = androidx.compose.ui.platform.LocalDensity.current
    var barHeightPx by remember { mutableIntStateOf(with(density) { TV_MENU_SPACE.roundToPx() }) }
    val topBar: @Composable () -> Unit = {
        Row(
            // Kept snug so every tab fits on a TV: when it was too wide the Settings icon got squeezed out.
            Modifier
                .fillMaxWidth()
                // Down from the menu on Home goes into the page (see HomeEntry).
                .onPreviewKeyEvent { ev ->
                    if (vm.tab == Tabs.HOME && focusedTab >= 0 &&
                        ev.type == androidx.compose.ui.input.key.KeyEventType.KeyDown &&
                        ev.nativeKeyEvent.keyCode == android.view.KeyEvent.KEYCODE_DPAD_DOWN
                    ) homeEntry.enter() else false
                }
                .padding(start = 40.dp, end = 40.dp, top = 22.dp, bottom = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Wordmark()
            Spacer(Modifier.width(12.dp))
            Row(
                Modifier.weight(1f),
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                // The tabs float in one frosted glass pill, as on Apple TV.
                Row(
                    Modifier.glassSurface(RoundedCornerShape(26.dp), Color(0x14FFFFFF)).padding(horizontal = 4.dp, vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                TABS.forEachIndexed { i, t ->
                    TabItem(
                        tab = t,
                        selected = vm.tab == i,
                        requester = tabRequesters[i],
                        onFocusChange = { isFocused ->
                            if (isFocused) {
                                focusedTab = i
                                vm.tab = i
                            } else if (focusedTab == i) {
                                focusedTab = -1
                            }
                        },
                        onClick = { if (!(vm.tab == Tabs.HOME && homeEntry.enter())) focusManager.moveFocus(FocusDirection.Down) },
                    )
                }
                }
            }
            Spacer(Modifier.width(10.dp))
            ProfileButton(vm)
            Spacer(Modifier.width(12.dp))
            Text(formatClock(LocalNow.current), style = Txt.Label, color = Ember.Dim)
        }
    }
    // The same soft warm glow at the top of every page, like the guide.
    Box(Modifier.fillMaxSize().background(Ember.Bg)) {
    // Home's background fades to the highlighted film's or series' artwork, as on Apple TV.
    HomeAmbientArt(if (vm.tab == Tabs.HOME && vm.homeArtwork) vm.homeArt else null)
    Column(
        Modifier
            .fillMaxSize()
            .background(androidx.compose.ui.graphics.Brush.verticalGradient(listOf(Ember.Glow.copy(alpha = 0.55f), androidx.compose.ui.graphics.Color.Transparent), endY = 620f))
    ) {
        // Other pages start below the menu; Home runs up behind it. (The menu itself is drawn once,
        // over everything, so moving between tabs never rebuilds it and loses your place.)
        AnimatedVisibility(visible = !hideBar && !overHome, enter = expandVertically(), exit = shrinkVertically()) {
            Spacer(Modifier.fillMaxWidth().height(with(density) { barHeightPx.toDp() }))
        }
        Box(Modifier.weight(1f).fillMaxWidth()) {
            when (vm.tab) {
                Tabs.HOME -> androidx.compose.runtime.CompositionLocalProvider(
                    LocalAmbientArt provides (if (vm.homeArtwork) vm::highlightArt else null),
                    LocalHomeEntry provides homeEntry,
                ) { HomeSection(vm) }
                Tabs.LIVE -> LiveSection(vm)
                Tabs.GUIDE -> GuideSection(vm)
                Tabs.MOVIES -> MoviesSection(vm)
                Tabs.SERIES -> SeriesSection(vm)
                Tabs.FAVOURITES -> FavouritesSection(vm)
                Tabs.SEARCH -> SearchSection(vm)
                else -> SettingsSection(vm)
            }
        }
    }
    AnimatedVisibility(visible = !hideBar, enter = fadeIn(), exit = fadeOut()) {
        // On Home, a soft shade behind the menu keeps it readable over any picture.
        val shade by androidx.compose.animation.core.animateFloatAsState(if (overHome) 0.75f else 0f, label = "menuShade")
        Box(
            Modifier
                .fillMaxWidth()
                .onSizeChanged { barHeightPx = it.height }
                .background(
                    androidx.compose.ui.graphics.Brush.verticalGradient(
                        0f to Ember.Bg.copy(alpha = shade),
                        1f to androidx.compose.ui.graphics.Color.Transparent,
                    )
                )
        ) { topBar() }
    }
    }
}

/**
 * The highlighted artwork, soft and dim across the top of Home, fading into the background colour.
 * It's loaded very small and stretched, which gives the blurred look for almost no work (Fire TV
 * Sticks can't blur a full-screen picture smoothly), and pictures cross-fade as the highlight moves.
 */
@Composable
private fun HomeAmbientArt(url: String?) {
    val context = androidx.compose.ui.platform.LocalContext.current
    androidx.compose.animation.Crossfade(targetState = url, animationSpec = androidx.compose.animation.core.tween(700), label = "homeArt") { art ->
        if (art != null) {
            val request = remember(art) {
                coil.request.ImageRequest.Builder(context).data(art).size(96, 96).memoryCacheKey("ambient:$art").build()
            }
            Box(Modifier.fillMaxSize()) {
                coil.compose.AsyncImage(
                    model = request,
                    contentDescription = null,
                    contentScale = androidx.compose.ui.layout.ContentScale.Crop,
                    modifier = Modifier.fillMaxWidth().fillMaxHeight(0.8f).graphicsLayer { alpha = 0.42f },
                )
                // Fades down into the background, so rows lower on the page stay easy to read.
                Box(
                    Modifier.fillMaxSize().background(
                        androidx.compose.ui.graphics.Brush.verticalGradient(
                            0f to Ember.Bg.copy(alpha = 0.25f),
                            0.45f to Ember.Bg.copy(alpha = 0.55f),
                            0.8f to Ember.Bg,
                            1f to Ember.Bg,
                        )
                    )
                )
            }
        } else {
            Box(Modifier.fillMaxSize())
        }
    }
}

@Composable
private fun TabItem(
    tab: TvTab,
    selected: Boolean,
    requester: FocusRequester,
    onFocusChange: (Boolean) -> Unit,
    onClick: () -> Unit,
) {
    var focused by remember { mutableStateOf(false) }
    Box(
        Modifier
            .padding(horizontal = 1.dp)
            .height(40.dp)
            .clip(RoundedCornerShape(20.dp))
            .background(
                when {
                    focused -> Color.White
                    selected -> Color(0x1FFFFFFF)
                    else -> Color.Transparent
                }
            )
            .focusRequester(requester)
            .onFocusChanged { state ->
                if (state.isFocused != focused) {
                    focused = state.isFocused
                    onFocusChange(state.isFocused)
                }
            }
            .tvClickable(onClick = onClick)
            .padding(horizontal = if (tab.icon != null) 10.dp else 12.dp),
        contentAlignment = Alignment.Center,
    ) {
        val color = when {
            focused -> Color.Black
            selected -> Ember.Text
            else -> Ember.Dim
        }
        if (tab.icon != null) {
            // Search and Settings are thin line icons: drawn bright and a little bigger so they stand out.
            Icon(tab.icon, contentDescription = tab.title, tint = if (focused) Color.Black else Ember.Text, modifier = Modifier.size(22.dp))
        } else {
            Text(tab.title, style = Txt.Label, color = color)
        }
    }
}
