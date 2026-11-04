package app.embertv.ui

import androidx.compose.foundation.border
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.widthIn
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
    // After signing out, the server and username are still there: only the password to type.
    val last = remember { vm.lastSignIn() }
    var server by remember { mutableStateOf(last.first) }
    var user by remember { mutableStateOf(last.second) }
    var pass by remember { mutableStateOf("") }
    var reveal by remember { mutableStateOf(false) }
    // Start on the first empty box.
    val startAt = remember { if (last.first.isBlank()) SERVER else if (last.second.isBlank()) USER else PASS }
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

    // When sign-in fails, say what kind of problem it is and go to the box that needs fixing.
    val problem = vm.loginError?.let { loginProblem(it, server.isBlank()) }
    LaunchedEffect(vm.loginError) {
        val field = problem?.field ?: return@LaunchedEffect
        delay(150)
        runCatching { requesters[field].requestFocus() }
    }

    Box(
        Modifier
            .fillMaxSize()
            .background(Ember.Bg)
            // The same soft warm glow as the rest of the app, behind the logo.
            .background(Brush.radialGradient(listOf(Ember.GlowSoft, Color.Transparent), center = androidx.compose.ui.geometry.Offset(380f, 260f), radius = 1100f))
    ) {
        Row(
            Modifier.fillMaxSize().padding(horizontal = 64.dp, vertical = 32.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            // ---- Welcome, and the quickest way in: a phone ----
            Column(Modifier.weight(1f)) {
                Wordmark(large = true)
                Spacer(Modifier.height(22.dp))
                Text("Welcome", style = Txt.LargeTitle)
                Spacer(Modifier.height(6.dp))
                Text(
                    "Sign in with the details from your IPTV provider: a server address, username and password.",
                    style = Txt.Body,
                    color = Ember.Dim,
                    modifier = Modifier.widthIn(max = 400.dp),
                )
                Spacer(Modifier.height(10.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(EmberIcons.Lock, contentDescription = null, tint = Ember.Dim, modifier = Modifier.size(13.dp))
                    Spacer(Modifier.width(6.dp))
                    Text("Saved on this TV only", style = Txt.Caption.copy(fontWeight = FontWeight.SemiBold), color = Ember.Dim)
                }
                val url = vm.phoneLoginUrl
                if (url != null) {
                    Spacer(Modifier.height(22.dp))
                    Row(
                        Modifier
                            .widthIn(max = 410.dp)
                            .glassSurface(RoundedCornerShape(22.dp), Color(0x12FFFFFF))
                            .padding(14.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Box(Modifier.clip(RoundedCornerShape(14.dp)).background(Color.White).padding(7.dp)) {
                            QrCode(url, 122.dp)
                        }
                        Spacer(Modifier.width(18.dp))
                        Column {
                            Box(
                                Modifier.clip(RoundedCornerShape(6.dp)).background(Color.White).padding(horizontal = 7.dp, vertical = 2.dp)
                            ) {
                                Text("QUICKEST", style = Txt.Caption.copy(fontSize = 10.sp, fontWeight = FontWeight.Bold, letterSpacing = 0.8.sp), color = Color.Black)
                            }
                            Spacer(Modifier.height(7.dp))
                            Text("Use your phone", style = Txt.Headline.copy(fontWeight = FontWeight.Bold))
                            Spacer(Modifier.height(4.dp))
                            LoginStep(1, "Scan the code with the camera")
                            LoginStep(2, "Type your details and tap Send")
                            Spacer(Modifier.height(8.dp))
                            Text(
                                buildAnnotatedString {
                                    append("No camera? Go to ")
                                    withStyle(SpanStyle(color = Ember.Dim, fontWeight = FontWeight.SemiBold)) { append(url.removePrefix("http://").substringBefore('#').trimEnd('/')) }
                                },
                                style = Txt.Caption,
                                color = Ember.Faint,
                                maxLines = 1,
                            )
                        }
                    }
                }
            }
            Spacer(Modifier.width(44.dp))
            // ---- Or type them with the remote ----
            Column(Modifier.width(380.dp)) {
                EntryRow(
                    label = "Server address",
                    value = server,
                    placeholder = "http://example.com:8080",
                    autoFocus = startAt == SERVER,
                    requester = requesters[SERVER],
                    onClick = { editing = SERVER },
                )
                EntryRow(
                    label = "Username",
                    value = user,
                    autoFocus = startAt == USER,
                    requester = requesters[USER],
                    onClick = { editing = USER },
                )
                // Show lets you check what the remote's keyboard typed.
                Row(verticalAlignment = Alignment.Bottom) {
                    EntryRow(
                        label = "Password",
                        value = pass,
                        masked = !reveal,
                        autoFocus = startAt == PASS,
                        requester = requesters[PASS],
                        modifier = Modifier.weight(1f),
                        onClick = { editing = PASS },
                    )
                    Spacer(Modifier.width(10.dp))
                    FocusBox(
                        modifier = Modifier.padding(bottom = 16.dp).size(width = 76.dp, height = 56.dp),
                        focusKey = "login:show",
                        shape = RoundedCornerShape(14.dp),
                        focusedScale = 1.05f,
                        color = Color(0x17FFFFFF),
                        focusedColor = Color.White,
                        onClick = { reveal = !reveal },
                    ) { focused ->
                        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                            Text(
                                if (reveal) "Hide" else "Show",
                                style = Txt.Label.copy(fontWeight = FontWeight.SemiBold),
                                color = if (focused) Color.Black else Ember.Text,
                            )
                        }
                    }
                }
                if (problem != null) LoginProblemNote(problem)
                Spacer(Modifier.height(2.dp))
                TvButton(
                    text = if (vm.loggingIn) "Signing in…" else "Sign in",
                    modifier = Modifier.fillMaxWidth(),
                    requester = requesters[SIGN_IN],
                    onClick = { vm.login(server, user, pass) },
                )
                Spacer(Modifier.height(14.dp))
                Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                    RemoteHints(listOf(Hint("▲▼", "Pick a box"), Hint("OK", "Type")))
                }
            }
        }
        Text(
            "Version ${app.embertv.BuildConfig.VERSION_NAME}",
            style = Txt.Caption,
            color = Ember.Faint,
            modifier = Modifier.align(Alignment.BottomStart).padding(start = 64.dp, bottom = 20.dp),
        )
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

@Composable
private fun LoginStep(n: Int, text: String) {
    Row(Modifier.padding(top = 6.dp), verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.size(18.dp).clip(CircleShape).background(Color(0x1FFFFFFF)), contentAlignment = Alignment.Center) {
            Text(n.toString(), style = Txt.Caption.copy(fontSize = 10.sp, fontWeight = FontWeight.Bold), color = Ember.Text)
        }
        Spacer(Modifier.width(8.dp))
        Text(text, style = Txt.Caption, color = Ember.Dim, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}

/** What went wrong signing in, in plain words, and which box to fix (if any). */
class LoginProblem(val title: String, val detail: String, val field: Int?)

fun loginProblem(message: String, serverBlank: Boolean): LoginProblem {
    val m = message.lowercase()
    return when {
        m.startsWith("enter the server") -> LoginProblem("Something's missing", message, if (serverBlank) SERVER else USER)
        "username or password" in m || "refused this login" in m ->
            LoginProblem("Username or password not recognised", "Check them against what your provider sent. Show lets you see the password.", USER)
        "expired" in m || "banned" in m || "disabled" in m ->
            LoginProblem("There's a problem with your subscription", message + " Contact your provider.", null)
        "busy" in m -> LoginProblem("Your provider is busy", message, null)
        "network problem" in m && "unable to resolve" !in m -> LoginProblem("Can't connect", message + " Check this device's internet connection.", SERVER)
        else -> LoginProblem("Can't reach that server", message, SERVER)
    }
}

/** A small frosted note under the boxes, with a plain-words title. */
@Composable
fun LoginProblemNote(p: LoginProblem) {
    Row(
        Modifier
            .fillMaxWidth()
            .padding(bottom = 14.dp)
            .glassSurface(RoundedCornerShape(16.dp), Color(0x24FF453A))
            .padding(horizontal = 14.dp, vertical = 11.dp),
    ) {
        Icon(EmberIcons.Alert, contentDescription = null, tint = Ember.Text, modifier = Modifier.padding(top = 1.dp).size(18.dp))
        Spacer(Modifier.width(10.dp))
        Column {
            Text(p.title, style = Txt.Label.copy(fontWeight = FontWeight.SemiBold), color = Ember.Text)
            Spacer(Modifier.height(2.dp))
            Text(p.detail, style = Txt.Caption, color = Ember.Dim)
        }
    }
}

/** For a moment after signing in: the logo and a spinner, then Home fades in underneath. */
@Composable
fun WelcomeCard() {
    Box(
        Modifier
            .fillMaxSize()
            .background(Ember.Bg)
            .background(Brush.radialGradient(listOf(Ember.GlowSoft, Color.Transparent), radius = 1100f))
            .clickable(interactionSource = remember { androidx.compose.foundation.interaction.MutableInteractionSource() }, indication = null) {},
        contentAlignment = Alignment.Center,
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Wordmark(large = true)
            Spacer(Modifier.height(28.dp))
            AppleSpinner(Modifier.size(28.dp))
            Spacer(Modifier.height(14.dp))
            Text("Getting your channels ready…", style = Txt.Body, color = Ember.Dim)
        }
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
