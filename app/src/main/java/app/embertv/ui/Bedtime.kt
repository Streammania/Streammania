package app.embertv.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties

/**
 * Shown on a kids profile at bedtime, or once the day's watching is used up. Playing has already
 * stopped. Only a grown-up (with their profile's PIN, if they've set one) can allow more time.
 */
@Composable
fun BedtimeCard(vm: AppViewModel, reason: AppViewModel.Bedtime) {
    val isTv = LocalIsTv.current
    val name = vm.profile?.name.orEmpty()
    // The grown-up choice waiting for a PIN: minutes, or 0 for "no limit".
    var asking by remember { mutableStateOf<Int?>(null) }
    val bedtime = reason == AppViewModel.Bedtime.BEDTIME

    fun allow(minutes: Int) {
        if (vm.parentPinSet) asking = minutes else vm.allowMoreTime(minutes.takeIf { it > 0 })
    }

    Dialog(
        onDismissRequest = {},
        properties = DialogProperties(dismissOnBackPress = false, dismissOnClickOutside = false, usePlatformDefaultWidth = false),
    ) {
        DialogBackdropBlur()
        Box(
            Modifier
                .fillMaxSize()
                .background(Brush.radialGradient(listOf(Color(0xCC141A33), Color(0xF205060A)), radius = 1400f)),
            contentAlignment = Alignment.Center,
        ) {
            Column(
                Modifier
                    .width(if (isTv) 560.dp else 420.dp)
                    .liquidGlass(RoundedCornerShape(28.dp))
                    .padding(if (isTv) 28.dp else 22.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                // A moon for bedtime; a clock face for the daily limit.
                Box(
                    Modifier.size(84.dp).glassSurface(CircleShape, Color(0x1AFFFFFF)),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        if (bedtime) EmberIcons.Moon else EmberIcons.Pause,
                        contentDescription = null,
                        tint = if (bedtime) Color(0xFFF4E7B5) else Ember.Text,
                        modifier = Modifier.size(40.dp),
                    )
                }
                Spacer(Modifier.height(16.dp))
                SheetTitle(
                    if (bedtime) "Time for bed${if (name.isNotBlank()) ", $name" else ""}" else "That's all for today",
                    subtitle = if (bedtime) {
                        "Sleep well. Everything will be here in the morning."
                    } else {
                        val limit = vm.kidsRules[vm.profile?.id]?.limitMinutes ?: 0
                        "You've watched ${minutesLabel(limit)} today. See you tomorrow!"
                    },
                )
                Spacer(Modifier.height(18.dp))
                Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    SheetOption("Switch profile", focusKey = "bedtime:switch", autoFocus = true, primary = true) { vm.switchProfile() }
                    SheetOption("Grown-ups: 30 more minutes", focusKey = "bedtime:more") { allow(30) }
                    SheetOption(if (bedtime) "Grown-ups: no bedtime tonight" else "Grown-ups: no limit today", focusKey = "bedtime:off") { allow(0) }
                }
                if (!vm.parentPinSet) {
                    Text(
                        "Tip: give the grown-ups' profiles a PIN so only they can allow more time.",
                        style = Txt.Caption,
                        color = Ember.Dim,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.padding(top = 12.dp),
                    )
                }
            }
        }
    }
    val wanted = asking
    if (wanted != null) {
        PinDialog(
            title = "Grown-ups only",
            subtitle = "Enter the PIN of a grown-up's profile",
            onDismiss = { asking = null },
        ) { pin ->
            val ok = vm.parentForPin(pin) != null
            if (ok) {
                asking = null
                vm.allowMoreTime(wanted.takeIf { it > 0 })
            }
            ok
        }
    }
}

/** "1 hour", "1 hour 30 minutes", "45 minutes". */
fun minutesLabel(minutes: Int): String {
    val h = minutes / 60
    val m = minutes % 60
    return listOfNotNull(
        h.takeIf { it > 0 }?.let { if (it == 1) "1 hour" else "$it hours" },
        m.takeIf { it > 0 }?.let { "$it minutes" },
    ).joinToString(" ").ifBlank { "0 minutes" }
}

/** "Off", or the time ("19:30") for a bedtime in minutes after midnight. */
fun bedtimeLabel(minutes: Int?): String =
    if (minutes == null) "Off" else "%02d:%02d".format(minutes / 60, minutes % 60)

/** The bedtimes a kids profile can have, in minutes after midnight (null for none). */
val BEDTIMES: List<Int?> = listOf(null, 18 * 60 + 30, 19 * 60, 19 * 60 + 30, 20 * 60, 20 * 60 + 30, 21 * 60, 21 * 60 + 30, 22 * 60)

/** The daily limits a kids profile can have, in minutes (null for none). */
val DAILY_LIMITS: List<Int?> = listOf(null, 60, 90, 120, 180, 240)

/** The next choice in [options] after [current], going round. */
fun <T> nextOf(options: List<T>, current: T): T = options[(options.indexOf(current) + 1) % options.size]
