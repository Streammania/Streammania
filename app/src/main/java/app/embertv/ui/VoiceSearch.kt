package app.embertv.ui

import android.app.Activity
import android.content.Intent
import android.speech.RecognizerIntent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext

/**
 * Speech to text through the device's own voice typing, or null where there isn't any. Phones
 * nearly always have it. Fire TV doesn't let apps use its mic button (that's kept for Alexa), so
 * on the TV voice search comes from the phone's remote instead.
 */
@Composable
fun rememberVoiceInput(prompt: String, onText: (String) -> Unit): (() -> Unit)? {
    val context = LocalContext.current
    val intent = remember(prompt) {
        Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH)
            .putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_WEB_SEARCH)
            .putExtra(RecognizerIntent.EXTRA_PROMPT, prompt)
            .putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 1)
    }
    val available = remember {
        runCatching { context.packageManager.queryIntentActivities(intent, 0).isNotEmpty() }.getOrDefault(false)
    }
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            val text = result.data?.getStringArrayListExtra(RecognizerIntent.EXTRA_RESULTS)?.firstOrNull()?.trim()
            if (!text.isNullOrEmpty()) onText(text)
        }
    }
    if (!available) return null
    return { runCatching { launcher.launch(intent) } }
}
