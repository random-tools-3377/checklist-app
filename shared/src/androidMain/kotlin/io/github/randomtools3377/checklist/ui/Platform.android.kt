package io.github.randomtools3377.checklist.ui

import android.Manifest
import android.content.Context
import android.content.ContextWrapper
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.BitmapFactory
import android.graphics.ImageDecoder
import android.graphics.Paint
import android.graphics.drawable.BitmapDrawable
import android.graphics.drawable.Drawable
import android.media.AudioAttributes
import android.media.SoundPool
import android.os.Build
import android.text.format.DateFormat
import android.widget.ImageView
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.biometric.BiometricManager
import androidx.biometric.BiometricManager.Authenticators.BIOMETRIC_WEAK
import androidx.biometric.BiometricManager.Authenticators.DEVICE_CREDENTIAL
import androidx.biometric.BiometricPrompt
import androidx.compose.foundation.layout.Box
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.fragment.app.FragmentActivity
import io.github.randomtools3377.checklist.core.AiContract
import io.github.randomtools3377.checklist.ui.resources.Res
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalTime
import kotlinx.datetime.toJavaLocalDate
import kotlinx.datetime.toJavaLocalTime
import java.nio.ByteBuffer
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import java.util.Locale

private val ShortDate = DateTimeFormatter.ofPattern("EEE d MMM", Locale.getDefault())
private val LongDate = DateTimeFormatter.ofPattern("EEEE d MMMM", Locale.getDefault())
private val ShortTime = DateTimeFormatter.ofLocalizedTime(FormatStyle.SHORT)

actual fun shortDate(date: LocalDate): String = date.toJavaLocalDate().format(ShortDate)
actual fun longDate(date: LocalDate): String = date.toJavaLocalDate().format(LongDate)
actual fun timeText(time: LocalTime): String = time.toJavaLocalTime().format(ShortTime)

@Composable
actual fun is24HourClock(): Boolean = DateFormat.is24HourFormat(LocalContext.current)

actual val canMatchWallpaper: Boolean get() = Build.VERSION.SDK_INT >= Build.VERSION_CODES.S

@Composable
actual fun wallpaperColors(dark: Boolean): ColorScheme? {
    if (!canMatchWallpaper) return null
    val context = LocalContext.current
    return if (dark) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
}

private val GlyphPaint = Paint()

actual fun canShowEmoji(emoji: String): Boolean = try {
    GlyphPaint.hasGlyph(emoji)
} catch (e: Exception) {
    false
}

@Composable
actual fun BackButton(enabled: Boolean, onBack: () -> Unit) = BackHandler(enabled = enabled, onBack = onBack)

@Composable
actual fun rememberSaveFile(mimeType: String, onPicked: (PickedFile?) -> Unit): (suggestedName: String) -> Unit {
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument(mimeType)) { uri -> onPicked(uri?.toString()) }
    return { name -> launcher.launch(name) }
}

@Composable
actual fun rememberOpenFile(mimeTypes: List<String>, onPicked: (PickedFile?) -> Unit): () -> Unit {
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri -> onPicked(uri?.toString()) }
    return { launcher.launch(mimeTypes.toTypedArray()) }
}

@Composable
actual fun rememberPickFolder(onPicked: (PickedFile?) -> Unit): () -> Unit {
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocumentTree()) { uri -> onPicked(uri?.toString()) }
    return { launcher.launch(null) }
}

@Composable
actual fun rememberPickImage(gifOnly: Boolean, onPicked: (PickedFile?) -> Unit): () -> Unit {
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri -> onPicked(uri?.toString()) }
    val kind = if (gifOnly) ActivityResultContracts.PickVisualMedia.SingleMimeType("image/gif") else ActivityResultContracts.PickVisualMedia.ImageOnly
    return { launcher.launch(PickVisualMediaRequest(kind)) }
}

@Composable
actual fun rememberAsk(what: Ask, onAnswer: (Boolean) -> Unit): () -> Unit {
    val context = LocalContext.current
    val permission = when (what) {
        Ask.NOTIFICATIONS -> if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) Manifest.permission.POST_NOTIFICATIONS else null
        Ask.CALENDAR -> Manifest.permission.READ_CALENDAR
    }
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted -> onAnswer(granted) }
    return {
        when {
            permission == null -> onAnswer(true)
            ContextCompat.checkSelfPermission(context, permission) == PackageManager.PERMISSION_GRANTED -> onAnswer(true)
            else -> launcher.launch(permission)
        }
    }
}

private fun Context.fragmentActivity(): FragmentActivity? = when (this) {
    is FragmentActivity -> this
    is ContextWrapper -> baseContext.fragmentActivity()
    else -> null
}

/**
 * Unlocks with the phone's own lock: fingerprint, face, or the phone's PIN or pattern. A phone
 * with no screen lock can't be asked, so there the lock simply opens, with a note saying why.
 */
@Composable
actual fun rememberUnlock(vm: MainViewModel): () -> Unit {
    val context = LocalContext.current
    return {
        val activity = context.fragmentActivity()
        val authenticators = BIOMETRIC_WEAK or DEVICE_CREDENTIAL
        if (activity == null || BiometricManager.from(context).canAuthenticate(authenticators) != BiometricManager.BIOMETRIC_SUCCESS) {
            Toast.makeText(context, "This phone has no screen lock, so the privacy lock can't ask for one.", Toast.LENGTH_LONG).show()
            vm.markUnlocked()
        } else {
            val prompt = BiometricPrompt(
                activity,
                ContextCompat.getMainExecutor(activity),
                object : BiometricPrompt.AuthenticationCallback() {
                    override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) {
                        vm.markUnlocked()
                    }
                },
            )
            prompt.authenticate(
                BiometricPrompt.PromptInfo.Builder()
                    .setTitle("Unlock")
                    .setSubtitle("Use your phone's lock")
                    .setAllowedAuthenticators(authenticators)
                    .build(),
            )
        }
    }
}

/** Where a file that comes with the app sits among the APK's assets. */
private fun assetPath(path: String): String = Res.getUri(path).removePrefix("file:///android_asset/")

/**
 * The two celebration sounds (made by tools/make-sounds.py), played when sound is on in
 * Settings → Appearance. They play like touch sounds, at the phone's sound volume.
 */
private class AndroidSounds(context: Context) : Sounds {
    private val pool = SoundPool.Builder()
        .setMaxStreams(2)
        .setAudioAttributes(
            AudioAttributes.Builder()
                .setUsage(AudioAttributes.USAGE_ASSISTANCE_SONIFICATION)
                .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                .build(),
        )
        .build()
    private val tick = runCatching { context.assets.openFd(assetPath("files/sounds/celebrate_tick.wav")).use { pool.load(it, 1) } }.getOrDefault(0)
    private val big = runCatching { context.assets.openFd(assetPath("files/sounds/celebrate_big.wav")).use { pool.load(it, 1) } }.getOrDefault(0)

    override fun tick() {
        if (tick != 0) pool.play(tick, 0.7f, 0.7f, 1, 0, 1f)
    }

    override fun big() {
        if (big != 0) pool.play(big, 0.9f, 0.9f, 1, 0, 1f)
    }

    override fun release() = pool.release()
}

@Composable
actual fun rememberSounds(): Sounds? {
    val context = LocalContext.current.applicationContext
    return remember { AndroidSounds(context) }
}

/** A celebration GIF as something Android can play. It moves on Android 9 and later; older phones show its first frame. */
private fun decodeGif(context: Context, bytes: ByteArray): Drawable? = try {
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
        ImageDecoder.decodeDrawable(ImageDecoder.createSource(ByteBuffer.wrap(bytes)))
    } else {
        BitmapFactory.decodeByteArray(bytes, 0, bytes.size)?.let { BitmapDrawable(context.resources, it) }
    }
} catch (e: Exception) {
    null
}

@Composable
actual fun CelebrationGif(celebration: String, modifier: Modifier) {
    val context = LocalContext.current
    val phone = LocalPhone.current
    val drawable by produceState<Drawable?>(null, celebration) {
        value = celebrationBytes(phone, celebration)?.let { bytes -> withContext(Dispatchers.IO) { decodeGif(context, bytes) } }
    }
    val shown = drawable
    if (shown == null) {
        Box(modifier)
        return
    }
    DisposableEffect(shown) {
        (shown as? android.graphics.drawable.Animatable)?.start()
        onDispose { (shown as? android.graphics.drawable.Animatable)?.stop() }
    }
    AndroidView(
        factory = { ImageView(it).apply { scaleType = ImageView.ScaleType.FIT_CENTER } },
        update = { it.setImageDrawable(shown) },
        modifier = modifier.semantics { contentDescription = "Celebration GIF" },
    )
}

/** Voice through Checklist AI's listening screen: it answers with the words it heard. */
@Composable
actual fun rememberVoice(onHeard: (String) -> Unit, onProblem: (String) -> Unit): (() -> Unit)? {
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        val heard = result.data?.getStringExtra(AiContract.KEY_TEXT)
        if (heard != null) onHeard(heard) else result.data?.getStringExtra(AiContract.KEY_ERROR)?.let(onProblem)
    }
    return {
        runCatching { launcher.launch(Intent(AiContract.ACTION_LISTEN).setPackage(AiContract.PACKAGE)) }
            .onFailure { onProblem("Checklist AI couldn't be opened. Is it installed?") }
    }
}
