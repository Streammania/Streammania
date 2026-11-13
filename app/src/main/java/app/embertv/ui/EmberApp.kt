package app.embertv.ui

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.ui.window.Dialog
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.layout.widthIn
import androidx.compose.ui.layout.ContentScale
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.animation.togetherWith
import app.embertv.data.nowSeconds
import kotlinx.coroutines.delay

@Composable
fun EmberApp(vm: AppViewModel, isTv: Boolean) {
    EmberTheme {
        val now by produceState(nowSeconds()) {
            while (true) {
                delay(20_000)
                value = nowSeconds()
            }
        }
        CompositionLocalProvider(LocalAppVm provides vm, LocalNow provides now, LocalIsTv provides isTv) {
            Box(Modifier.fillMaxSize().background(Ember.Bg)) {
                // Registered first so every screen's own back handling takes priority.
                BackHandler(enabled = vm.stack.size > 1) { vm.pop() }

                val restoring = vm.restoreFocusKey
                LaunchedEffect(restoring) {
                    if (restoring != null) {
                        delay(1500)
                        if (vm.restoreFocusKey == restoring) vm.restoreFocusKey = null
                    }
                }

                if (vm.repo == null) {
                    if (isTv) LoginScreen(vm) else PhoneLoginScreen(vm)
                } else if (vm.pickingProfile) {
                    ProfilePicker(vm)
                } else {
                    // Phones: pages slide in from the right and back out, as on iPhone. Players fade.
                    val depth = vm.stack.size
                    if (isTv) Screens(vm, isTv, vm.stack.last()) else androidx.compose.animation.AnimatedContent(
                        targetState = vm.stack.last() to depth,
                        transitionSpec = {
                            val forward = targetState.second >= initialState.second
                            val player = (if (forward) targetState.first else initialState.first).let { it is Screen.PlayLive || it is Screen.PlayVod || it is Screen.MultiView }
                            when {
                                player -> androidx.compose.animation.fadeIn() togetherWith androidx.compose.animation.fadeOut()
                                forward -> androidx.compose.animation.slideInHorizontally { it } togetherWith
                                    (androidx.compose.animation.slideOutHorizontally { -it / 4 } + androidx.compose.animation.fadeOut(targetAlpha = 0.6f))
                                else -> (androidx.compose.animation.slideInHorizontally { -it / 4 } + androidx.compose.animation.fadeIn(initialAlpha = 0.6f)) togetherWith
                                    androidx.compose.animation.slideOutHorizontally { it }
                            }
                        },
                        contentKey = { it.second },
                        label = "pages",
                    ) { (s, _) -> Screens(vm, isTv, s) }
                }

                // Programme reminders
                LaunchedEffect(Unit) {
                    while (true) {
                        vm.checkReminders(nowSeconds())
                        delay(15_000)
                    }
                }
                vm.dueReminder?.let { ReminderDialog(vm, it) }
                vm.dueKickoff?.let { KickoffDialog(vm, it) }

                // TV screensaver after a few idle minutes
                if (isTv) {
                    LaunchedEffect(Unit) {
                        while (true) {
                            delay(10_000)
                            vm.checkIdle()
                        }
                    }
                    AnimatedVisibility(visible = vm.screensaver, enter = fadeIn(tween(1200)), exit = fadeOut(tween(300))) {
                        Screensaver(vm)
                    }
                }

                // Sleep timer
                val sleepAt = vm.sleepAt
                LaunchedEffect(sleepAt) {
                    vm.sleepWarning = false
                    if (sleepAt != null) {
                        // A minute before: ask if anyone's still watching.
                        delay((sleepAt - 60_000L - System.currentTimeMillis()).coerceAtLeast(0L))
                        if (vm.playerOpen && sleepAt - System.currentTimeMillis() > 5_000L) vm.sleepWarning = true
                        delay((sleepAt - System.currentTimeMillis()).coerceAtLeast(0L))
                        vm.sleepNow()
                    }
                }
                if (vm.sleepWarning && sleepAt != null) SleepWarningDialog(vm, sleepAt)

                // Never over something you're watching: it waits until the video is closed.
                if (vm.update != null && vm.repo != null && (!vm.playerOpen || vm.updateProgress != null)) UpdateDialog(vm)
                vm.crashNote?.let { CrashDialog(it) { vm.crashNote = null } }

                // Choosing HD / FHD / 4K
                vm.choosingVersion?.let { VersionPickerDialog(vm, it) }

                // "Open in another player" from inside a player
                vm.pickingOtherPlayer?.let { pending ->
                    PlayerPickerDialog(
                        vm = vm,
                        title = "Open in…",
                        subtitle = "Streammania's player closes first so the other app can use your provider connection.",
                        includeBuiltIn = false,
                        onDismiss = { vm.pickingOtherPlayer = null },
                    ) { pkg -> vm.launchElsewhere(pending, pkg) }
                }

                // Play on TV: the code on the TV, and the phone's picker, code entry and remote
                if (isTv) {
                    TvPairingDialog(vm)
                    // Linking with another TV: type the code it shows.
                    vm.pairingWith?.let { PairCodeDialog(vm, it) }
                } else {
                    if (vm.showCastSheet) CastSheet(vm)
                    vm.pairingWith?.let { PairCodeDialog(vm, it) }
                    if (vm.showRemote) RemoteScreen(vm)
                }

                // A pinned live score in the corner
                if (vm.repo != null && !vm.pickingProfile) ScoreBug(vm)

                // Open straight into the last channel or the guide, if chosen in Settings.
                LaunchedEffect(vm.repo != null, vm.pickingProfile) {
                    delay(300)
                    vm.applyStartup()
                }

                // Followed teams: goal alerts, and "Where's it on?"
                if (vm.repo != null && !vm.pickingProfile) {
                    TeamAlertBanner(vm)
                    vm.findingMatch?.let { MatchFinderDialog(vm, it) }
                }

                // Just signed in: a moment with the logo, then Home fades in.
                LaunchedEffect(vm.welcoming) {
                    if (vm.welcoming) {
                        delay(1_800)
                        vm.welcoming = false
                    }
                }
                AnimatedVisibility(
                    visible = vm.welcoming && vm.repo != null,
                    enter = fadeIn(androidx.compose.animation.core.tween(0)),
                    exit = fadeOut(androidx.compose.animation.core.tween(500)),
                    modifier = Modifier.fillMaxSize(),
                ) { WelcomeCard() }

                NoticeOverlay(vm)
            }
        }
    }
}

@Composable
private fun BoxScope.NoticeOverlay(vm: AppViewModel) {
    val notice = vm.notice
    // Keeps the text on screen while the pill fades out.
    val lastText = remember { arrayOf("") }
    if (notice != null) lastText[0] = notice.text
    LaunchedEffect(notice) {
        if (notice != null) {
            delay(2200)
            vm.clearNotice(notice)
        }
    }
    AnimatedVisibility(
        visible = notice != null,
        enter = fadeIn(),
        exit = fadeOut(),
        modifier = Modifier.align(Alignment.TopCenter).windowInsetsPadding(WindowInsets.statusBars).padding(top = 28.dp),
    ) {
        Box(
            Modifier
                .glassSurface(RoundedCornerShape(22.dp), Color(0xE624242B))
                .padding(horizontal = 22.dp, vertical = 12.dp)
        ) {
            Text(lastText[0], style = Txt.Label, color = Ember.Text)
        }
    }
}

@Composable
private fun ReminderDialog(vm: AppViewModel, r: app.embertv.data.Reminder) {
    val isTv = LocalIsTv.current
    val now = LocalNow.current
    Dialog(onDismissRequest = { vm.dismissReminder() }) {
        DialogBackdropBlur()
        Column(
            Modifier
                .width(if (isTv) 520.dp else 440.dp)
                .clip(RoundedCornerShape(28.dp))
                .background(dialogPanelColor())
                .padding(if (isTv) 8.dp else 20.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            ChannelLogo(r.channelIcon, r.channelName, Modifier.size(width = 96.dp, height = 64.dp))
            Spacer(Modifier.height(16.dp))
            SheetTitle(
                r.title,
                eyebrow = if (r.start > now) "Starting soon" else "Starting now",
                subtitle = "${r.channelName}  ·  ${formatClock(r.start)} – ${formatClock(r.end)}",
            )
            Spacer(Modifier.height(24.dp))
            Column(Modifier.width(if (isTv) 340.dp else 400.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                SheetOption("Watch now", focusKey = "reminder:watch", autoFocus = isTv, primary = true) { vm.watchReminder(r) }
                SheetOption("Dismiss", focusKey = "reminder:dismiss") { vm.dismissReminder() }
            }
        }
    }
}

/** The sleep timer's last minute: keep watching, or let it stop. Doing nothing lets it stop. */
@Composable
private fun SleepWarningDialog(vm: AppViewModel, sleepAt: Long) {
    val isTv = LocalIsTv.current
    var left by remember { mutableStateOf(((sleepAt - System.currentTimeMillis()) / 1000).coerceAtLeast(0)) }
    LaunchedEffect(sleepAt) {
        while (true) {
            left = ((sleepAt - System.currentTimeMillis()) / 1000).coerceAtLeast(0)
            delay(1_000)
        }
    }
    Dialog(onDismissRequest = { vm.keepWatching(30) }) {
        DialogBackdropBlur()
        Column(
            Modifier
                .width(if (isTv) 480.dp else 420.dp)
                .clip(RoundedCornerShape(28.dp))
                .background(dialogPanelColor())
                .padding(if (isTv) 8.dp else 20.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Icon(EmberIcons.Moon, contentDescription = null, tint = Ember.Text, modifier = Modifier.size(40.dp))
            Spacer(Modifier.height(14.dp))
            SheetTitle(
                "Still watching?",
                eyebrow = "Sleep timer",
                subtitle = "Stopping in $left seconds",
            )
            Spacer(Modifier.height(22.dp))
            Column(Modifier.width(if (isTv) 340.dp else 380.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                SheetOption("Keep watching", focusKey = "sleep:keep", autoFocus = isTv, primary = true, subtitle = "30 more minutes") { vm.keepWatching(30) }
                SheetOption("Turn the timer off", focusKey = "sleep:off") { vm.sleepOff() }
                SheetOption("Stop now", focusKey = "sleep:stop") { vm.sleepNow() }
            }
        }
    }
}

/** Just before one of your teams kicks off: watch it, or carry on. */
@Composable
private fun KickoffDialog(vm: AppViewModel, m: app.embertv.data.Match) {
    val isTv = LocalIsTv.current
    val now = LocalNow.current
    Dialog(onDismissRequest = { vm.dismissKickoff() }) {
        DialogBackdropBlur()
        Column(
            Modifier
                .width(if (isTv) 520.dp else 440.dp)
                .clip(RoundedCornerShape(28.dp))
                .background(dialogPanelColor())
                .padding(if (isTv) 8.dp else 20.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            // A small scoreboard, like the goal alerts: crest tiles either side of the kick-off time.
            val mins = ((m.kickoff / 1000 - now) / 60).coerceAtLeast(0)
            Row(verticalAlignment = Alignment.CenterVertically) {
                KickoffCrest(m.home.logo, m.home.shortName)
                Column(Modifier.padding(horizontal = 14.dp).widthIn(max = 130.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(formatClock(m.kickoff / 1000), style = Txt.Title.copy(fontWeight = FontWeight.Bold))
                    Spacer(Modifier.height(4.dp))
                    Text(m.league.name, style = Txt.Caption, color = Ember.Faint, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
                KickoffCrest(m.away.logo, m.away.shortName)
            }
            Spacer(Modifier.height(18.dp))
            SheetTitle(
                "${m.home.shortName} v ${m.away.shortName}",
                eyebrow = if (mins > 0) "Kick-off in $mins min" else "Kicking off now",
                subtitle = if (vm.isFollowedMatch(m)) "One of your teams is about to play" else "You asked for a reminder",
            )
            Spacer(Modifier.height(24.dp))
            Column(Modifier.width(if (isTv) 340.dp else 400.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                SheetOption("Watch", focusKey = "kickoff:watch", autoFocus = isTv, primary = true) { vm.watchKickoff(m) }
                SheetOption("Not now", focusKey = "kickoff:dismiss") { vm.dismissKickoff() }
            }
        }
    }
}

@Composable
private fun KickoffCrest(logo: String, name: String) {
    Column(Modifier.width(92.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        Box(
            Modifier.size(72.dp).glassSurface(RoundedCornerShape(18.dp), Color(0x14FFFFFF)),
            contentAlignment = Alignment.Center,
        ) {
            NetImage(logo, Modifier.fillMaxSize().padding(12.dp), ContentScale.Fit)
        }
        Spacer(Modifier.height(8.dp))
        Text(name, style = Txt.Caption.copy(fontWeight = FontWeight.SemiBold), color = Ember.Text, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}

@Composable
private fun CrashDialog(note: String, onDismiss: () -> Unit) {
    val isTv = LocalIsTv.current
    Dialog(onDismissRequest = onDismiss) {
        DialogBackdropBlur()
        Column(
            Modifier
                .width(if (isTv) 620.dp else 440.dp)
                .clip(RoundedCornerShape(28.dp))
                .background(dialogPanelColor())
                .padding(if (isTv) 8.dp else 20.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            SheetTitle(
                "Streammania closed unexpectedly",
                eyebrow = "Sorry about that",
                subtitle = "If it keeps happening, take a photo of this and send it on so it can be fixed.",
            )
            Spacer(Modifier.height(16.dp))
            Text(
                note,
                style = Txt.Caption.copy(fontSize = 11.sp, letterSpacing = 0.sp),
                color = Ember.Dim,
                maxLines = 9,
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(14.dp))
                    .background(Color(0x14FFFFFF))
                    .padding(14.dp),
            )
            Spacer(Modifier.height(18.dp))
            Column(Modifier.width(if (isTv) 340.dp else 400.dp)) {
                SheetOption("OK", focusKey = "crash:ok", autoFocus = isTv, primary = true, onClick = onDismiss)
            }
        }
    }
}

/**
 * A new version, Apple "What's New" style: the app icon, the version, a line on what it's about,
 * a few points in a glass panel, then Install now (white) and Not now. While it downloads, the
 * buttons become a progress bar.
 */
@Composable
private fun UpdateDialog(vm: AppViewModel) {
    val info = vm.update ?: return
    val isTv = LocalIsTv.current
    val progress = vm.updateProgress
    val bold = androidx.compose.ui.text.font.FontWeight.Bold
    val semi = androidx.compose.ui.text.font.FontWeight.SemiBold
    Dialog(onDismissRequest = { if (progress == null) vm.dismissUpdate() }) {
        DialogBackdropBlur()
        Column(
            Modifier
                .width(if (isTv) 560.dp else 440.dp)
                .clip(RoundedCornerShape(28.dp))
                .background(dialogPanelColor())
                .padding(horizontal = if (isTv) 30.dp else 22.dp, vertical = if (isTv) 26.dp else 22.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            EmberMark(if (isTv) 76.dp else 68.dp)
            Spacer(Modifier.height(14.dp))
            Text(
                "UPDATE AVAILABLE",
                style = Txt.Caption.copy(fontWeight = semi, letterSpacing = 1.3.sp),
                color = Ember.Dim,
            )
            Spacer(Modifier.height(4.dp))
            Text("Streammania ${info.versionName}", style = Txt.Title.copy(fontWeight = bold), textAlign = TextAlign.Center)
            Spacer(Modifier.height(6.dp))
            Text(
                info.headline.ifBlank { "A new version is ready to install." },
                style = Txt.Body,
                color = Ember.Dim,
                textAlign = TextAlign.Center,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            // What's new: a few short points in a frosted panel.
            if (info.notes.isNotEmpty()) {
                Spacer(Modifier.height(16.dp))
                Column(
                    Modifier
                        .fillMaxWidth()
                        .glassSurface(RoundedCornerShape(18.dp), Color(0x12FFFFFF))
                        .padding(horizontal = 16.dp, vertical = 14.dp),
                    verticalArrangement = Arrangement.spacedBy(9.dp),
                ) {
                    Text("What's new", style = Txt.Label.copy(fontWeight = semi), color = Ember.Text)
                    for (n in info.notes) {
                        Row {
                            Box(
                                Modifier
                                    .padding(top = 7.dp)
                                    .size(5.dp)
                                    .clip(CircleShape)
                                    .background(Ember.Dim)
                            )
                            Spacer(Modifier.width(10.dp))
                            Text(n, style = Txt.Caption.copy(fontSize = 13.sp, lineHeight = 18.sp), color = Ember.Dim, maxLines = 2, overflow = TextOverflow.Ellipsis)
                        }
                    }
                }
            }
            Spacer(Modifier.height(12.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(EmberIcons.Lock, contentDescription = null, tint = Ember.Faint, modifier = Modifier.size(12.dp))
                Spacer(Modifier.width(6.dp))
                Text(
                    "${app.embertv.BuildConfig.VERSION_NAME} → ${info.versionName}  ·  Your login and favourites stay",
                    style = Txt.Caption,
                    color = Ember.Faint,
                    maxLines = 1,
                )
            }
            Spacer(Modifier.height(20.dp))
            if (progress != null) {
                Column(Modifier.width(if (isTv) 360.dp else 320.dp)) {
                    ProgressBar(progress, Modifier.fillMaxWidth())
                    Spacer(Modifier.height(8.dp))
                    Row {
                        Text(if (progress >= 1f) "Opening the installer…" else "Downloading…", style = Txt.Caption, color = Ember.Dim)
                        Spacer(Modifier.weight(1f))
                        Text("${(progress * 100).toInt()}%", style = Txt.Caption.copy(fontWeight = semi), color = Ember.Text)
                    }
                }
            } else if (!isTv) {
                PillButton("Install now", Modifier.fillMaxWidth(), primary = true) { vm.installUpdate() }
                Spacer(Modifier.height(6.dp))
                Box(
                    Modifier.fillMaxWidth().height(44.dp).pressable { vm.dismissUpdate() },
                    contentAlignment = Alignment.Center,
                ) {
                    Text("Not now", style = Txt.Label.copy(fontWeight = semi), color = Ember.Dim)
                }
            } else {
                Column(Modifier.width(360.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    SheetOption("Install now", focusKey = "update:install", autoFocus = true, primary = true) { vm.installUpdate() }
                    SheetOption("Not now", focusKey = "update:later", subtitle = "It'll be in Settings when you want it") { vm.dismissUpdate() }
                }
            }
        }
    }
}

/** The page for [screen]. */
@Composable
private fun Screens(vm: AppViewModel, isTv: Boolean, screen: Screen) {
    // A new page starts fresh, even in the same place as the last one (one channel list swapped for
    // another, one film's page for another's). Zapping within a list only changes the index, so the
    // player itself carries on.
    val identity: Any = when (screen) {
        is Screen.PlayLive -> System.identityHashCode(screen.channels)
        is Screen.PlayVod -> System.identityHashCode(screen.items)
        else -> screen
    }
    androidx.compose.runtime.key(screen::class, identity) { ScreenFor(vm, isTv, screen) }
}

@Composable
private fun ScreenFor(vm: AppViewModel, isTv: Boolean, screen: Screen) {
    when (screen) {
        Screen.Home -> if (isTv) HomeScreen(vm) else PhoneHome(vm)
        is Screen.MovieDetail -> if (isTv) MovieDetailScreen(vm, screen.movie) else PhoneMovieDetail(vm, screen.movie)
        is Screen.SeriesDetail -> if (isTv) SeriesDetailScreen(vm, screen.series) else PhoneSeriesDetail(vm, screen.series)
        is Screen.PlayLive -> LivePlayerScreen(vm, screen.channels, screen.index)
        is Screen.PlayVod -> VodPlayerScreen(vm, screen.items, screen.index, screen.startMs)
        is Screen.Catchup -> CatchupScreen(vm, screen.channel)
        is Screen.MultiView -> MultiViewScreen(vm, screen.channels, screen.pool)
    }
}
