package app.embertv.ui

import androidx.compose.foundation.border
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.Icon
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.embertv.data.credentialsFromLink
import kotlinx.coroutines.delay

private const val SERVER = 0
private const val USER = 1
private const val PASS = 2
private const val SIGN_IN = 3

@Composable
fun LoginScreen(vm: AppViewModel) {
    var server by remember { mutableStateOf("") }
    var user by remember { mutableStateOf("") }
    var pass by remember { mutableStateOf("") }
    var editing by remember { mutableStateOf<Int?>(null) }
    var focusNext by remember { mutableStateOf<Int?>(null) }
    val requesters = remember { List(4) { FocusRequester() } }

    // After a pop-up editor closes, move on to the next box (or the Sign in button).
    LaunchedEffect(focusNext) {
        val target = focusNext ?: return@LaunchedEffect
        delay(200)
        runCatching { requesters[target].requestFocus() }
        focusNext = null
    }

    fun finishEditing(next: Int) {
        editing = null
        focusNext = next
    }

    // Lets a phone on the same Wi-Fi type the details instead.
    DisposableEffect(Unit) {
        vm.startPhoneLogin()
        onDispose { vm.stopPhoneLogin() }
    }
    val fill = vm.phoneLoginFill
    LaunchedEffect(fill) {
        if (fill != null) {
            server = fill.first
            user = fill.second
            pass = fill.third
        }
    }

    Box(
        Modifier
            .fillMaxSize()
            .background(Brush.radialGradient(listOf(Ember.GlowStrong, Ember.Bg), radius = 1400f))
    ) {
        Row(
            Modifier.fillMaxSize().padding(horizontal = 72.dp, vertical = 40.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            // ---- Welcome, and signing in from a phone ----
            Column(Modifier.weight(1f)) {
                Wordmark(large = true)
                Spacer(Modifier.height(26.dp))
                Text("Welcome", style = Txt.LargeTitle)
                Spacer(Modifier.height(8.dp))
                Text(
                    "Sign in with the Xtream Codes details from your IPTV provider: a server address, a username and a password.",
                    style = Txt.Body,
                    color = Ember.Dim,
                    modifier = Modifier.width(500.dp),
                )
                Spacer(Modifier.height(10.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(EmberIcons.Lock, contentDescription = null, tint = Ember.Dim, modifier = Modifier.size(14.dp))
                    Spacer(Modifier.width(6.dp))
                    Text("Saved on this TV only", style = Txt.Caption.copy(fontWeight = FontWeight.SemiBold), color = Ember.Dim)
                }
                val url = vm.phoneLoginUrl
                if (url != null) {
                    Spacer(Modifier.height(24.dp))
                    Row(
                        Modifier
                            .width(500.dp)
                            .glassSurface(RoundedCornerShape(22.dp), Color(0x14FFFFFF))
                            .padding(16.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Box(Modifier.clip(RoundedCornerShape(12.dp)).background(Color.White).padding(6.dp)) {
                            QrCode(url, 104.dp)
                        }
                        Spacer(Modifier.width(18.dp))
                        Column {
                            Text("Sign in with your phone", style = Txt.Headline.copy(fontWeight = FontWeight.Bold))
                            Spacer(Modifier.height(6.dp))
                            Text("1  Scan the code with your phone's camera", style = Txt.Caption, color = Ember.Dim)
                            Text("2  Type your details there and send them", style = Txt.Caption, color = Ember.Dim)
                            Spacer(Modifier.height(8.dp))
                            Text("Or open ${url.removePrefix("http://")} on the same Wi-Fi", style = Txt.Caption, color = Ember.Faint, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        }
                    }
                }
                Spacer(Modifier.height(20.dp))
                Text("Version ${app.embertv.BuildConfig.VERSION_NAME}", style = Txt.Caption, color = Ember.Faint)
            }
            Spacer(Modifier.width(56.dp))
            // ---- The form, on a floating frosted panel ----
            Column(
                Modifier
                    .width(460.dp)
                    .glassSurface(RoundedCornerShape(28.dp), Color(0x12FFFFFF))
                    .padding(horizontal = 26.dp, vertical = 24.dp)
            ) {
                Text("SIGN IN", style = Txt.Caption.copy(fontWeight = FontWeight.SemiBold, letterSpacing = 1.3.sp), color = Ember.Dim, modifier = Modifier.padding(start = 4.dp))
                Spacer(Modifier.height(16.dp))
                EntryRow(
                    label = "Server address",
                    value = server,
                    placeholder = "http://example.com:8080",
                    autoFocus = true,
                    requester = requesters[SERVER],
                    onClick = { editing = SERVER },
                )
                EntryRow(
                    label = "Username",
                    value = user,
                    requester = requesters[USER],
                    onClick = { editing = USER },
                )
                EntryRow(
                    label = "Password",
                    value = pass,
                    masked = true,
                    requester = requesters[PASS],
                    onClick = { editing = PASS },
                )
                val error = vm.loginError
                if (error != null) {
                    // A small frosted note rather than coloured text.
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .padding(bottom = 14.dp)
                            .glassSurface(RoundedCornerShape(14.dp), Color(0x24FF453A))
                            .padding(horizontal = 14.dp, vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Icon(EmberIcons.Alert, contentDescription = null, tint = Ember.Text, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(10.dp))
                        Text(error, style = Txt.Label, color = Ember.Text)
                    }
                }
                Spacer(Modifier.height(4.dp))
                TvButton(
                    text = if (vm.loggingIn) "Signing in…" else "Sign in",
                    icon = "→",
                    modifier = Modifier.fillMaxWidth(),
                    requester = requesters[SIGN_IN],
                    onClick = { vm.login(server, user, pass) },
                )
                Spacer(Modifier.height(16.dp))
                RemoteHints(listOf(Hint("▲▼", "Pick a box"), Hint("OK", "Type")))
            }
        }
    }

    when (editing) {
        SERVER -> TextEntryDialog(
            title = "Server address",
            value = server,
            onValueChange = { text ->
                server = text
                // A pasted playlist link carries the username and password too.
                val creds = credentialsFromLink(text)
                if (creds != null) {
                    if (user.isEmpty()) user = creds.first
                    if (pass.isEmpty()) pass = creds.second
                }
            },
            placeholder = "http://example.com:8080",
            keyboardType = KeyboardType.Uri,
            imeAction = ImeAction.Next,
            onDone = { finishEditing(if (user.isNotEmpty() && pass.isNotEmpty()) SIGN_IN else USER) },
        )
        USER -> TextEntryDialog(
            title = "Username",
            value = user,
            onValueChange = { user = it },
            keyboardType = KeyboardType.Email,
            imeAction = ImeAction.Next,
            onDone = { finishEditing(PASS) },
        )
        PASS -> TextEntryDialog(
            title = "Password",
            value = pass,
            onValueChange = { pass = it },
            keyboardType = KeyboardType.Password,
            imeAction = ImeAction.Done,
            onDone = { finishEditing(SIGN_IN) },
        )
    }
}

/** The brand's warm glow: amber through orange to red. */
val EmberGradient = listOf(Color(0xFFFFB547), Color(0xFFFF6A2B), Color(0xFFE8362A))

/**
 * The Ember mark: an outlined play button glowing orange to red with a hollow centre, on a small
 * dark glass tile. The same mark as the app icon and the Fire TV banner.
 */
@Composable
fun EmberMark(size: androidx.compose.ui.unit.Dp) {
    Box(
        Modifier
            .size(size)
            .clip(RoundedCornerShape(size * 0.24f))
            .background(androidx.compose.ui.graphics.Brush.linearGradient(listOf(Color(0xFF2C2C33), Color(0xFF121216))))
            .border(1.dp, Color(0x24FFFFFF), RoundedCornerShape(size * 0.24f)),
        contentAlignment = Alignment.Center,
    ) {
        androidx.compose.foundation.Canvas(Modifier.size(size * 0.62f)) {
            val w = this.size.width
            val brush = androidx.compose.ui.graphics.Brush.linearGradient(EmberGradient, start = androidx.compose.ui.geometry.Offset(w * 0.2f, 0f), end = androidx.compose.ui.geometry.Offset(w * 0.9f, w))
            val outer = androidx.compose.ui.graphics.Path().apply {
                moveTo(w * 0.27f, w * 0.15f); lineTo(w * 0.86f, w * 0.5f); lineTo(w * 0.27f, w * 0.85f); close()
            }
            val round = androidx.compose.ui.graphics.drawscope.Stroke(width = w * 0.12f, join = androidx.compose.ui.graphics.StrokeJoin.Round)
            drawPath(outer, brush)
            drawPath(outer, brush, style = round)
            val inner = androidx.compose.ui.graphics.Path().apply {
                moveTo(w * 0.39f, w * 0.37f); lineTo(w * 0.62f, w * 0.5f); lineTo(w * 0.39f, w * 0.63f); close()
            }
            drawPath(inner, Color(0xFF1E1E24))
            drawPath(inner, Color(0xFF1E1E24), style = androidx.compose.ui.graphics.drawscope.Stroke(width = w * 0.04f, join = androidx.compose.ui.graphics.StrokeJoin.Round))
        }
    }
}

@Composable
fun Wordmark(large: Boolean = false) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        EmberMark(if (large) 40.dp else 30.dp)
        Spacer(Modifier.width(if (large) 12.dp else 9.dp))
        Text(
            buildAnnotatedString {
                append("stream")
                // "mania" glows from amber to red, like the mark.
                withStyle(SpanStyle(brush = androidx.compose.ui.graphics.Brush.horizontalGradient(EmberGradient))) { append("mania") }
            },
            fontSize = if (large) 30.sp else 21.sp,
            fontWeight = FontWeight.ExtraBold,
            letterSpacing = (if (large) -1.0 else -0.7).sp,
            color = Ember.Text,
            maxLines = 1,
            softWrap = false,
        )
    }
}
