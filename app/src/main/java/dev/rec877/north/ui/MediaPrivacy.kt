package dev.rec877.north.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.rec877.north.*
import dev.rec877.north.ui.theme.North
import okhttp3.OkHttpClient

private data class MediaPrivacy(val active: Boolean = false, val epoch: Int = 0, val detect: Boolean = true,
    val account: String? = null, val classifier: OnDeviceMediaClassifier? = null, val foreground: Boolean = true)
private val LocalMediaPrivacy = compositionLocalOf { MediaPrivacy() }
internal val LocalMediaAllowed = compositionLocalOf { false }

@Composable
fun MediaPrivacyProvider(controller: CareModeController, classifier: OnDeviceMediaClassifier, account: String?, content: @Composable () -> Unit) {
    CompositionLocalProvider(LocalMediaPrivacy provides MediaPrivacy(controller.active, controller.epoch, controller.localDetection, account, classifier, controller.foreground), content = content)
}

/** 許可前は画像・動画コンポーザブルそのものを生成しない。 */
@Composable
internal fun MediaPrivacyGate(media: List<Media>, client: OkHttpClient, modifier: Modifier = Modifier, content: @Composable () -> Unit) {
    val privacy = LocalMediaPrivacy.current
    var revealed by remember(media, privacy) { mutableStateOf(false) }
    var verdict by remember(media, privacy) { mutableStateOf<MediaVerdict?>(null) }
    val sensitive = media.any { it.sensitive }
    val requiresCheck = privacy.active && privacy.foreground && !sensitive && privacy.detect
    LaunchedEffect(media, privacy, revealed) {
        if (requiresCheck && !revealed) {
            var result = MediaVerdict.ALLOW
            for (item in media) {
                val value = privacy.classifier?.classify(item, client) ?: MediaVerdict.UNKNOWN
                if (value != MediaVerdict.ALLOW) { result = value; break }
            }
            verdict = result
        }
    }
    val hidden = !revealed && (sensitive || (privacy.active && (!privacy.detect || verdict != MediaVerdict.ALLOW)))
    if (!hidden) {
        CompositionLocalProvider(LocalMediaAllowed provides true, content = content)
        return
    }
    val message = when {
        sensitive -> "センシティブなメディアを非表示にしています"
        !privacy.detect -> "配慮モードでメディアを非表示にしています"
        media.any { it.kind != "PHOTO" } -> "動画・GIFは配慮モードで非表示にしています"
        verdict == null -> "端末内で画像を確認しています…"
        verdict == MediaVerdict.UNKNOWN -> "判定できなかったため非表示にしています"
        else -> "センシティブな可能性があるため非表示にしています"
    }
    Column(modifier.fillMaxWidth().heightIn(min = 150.dp).clip(RoundedCornerShape(16.dp))
        .background(North.colors.elev2).padding(20.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
        Text(message, color = North.colors.textMuted, fontSize = 14.sp, textAlign = TextAlign.Center)
        TextButton(onClick = { revealed = true }) { Text("このメディアを表示", color = North.colors.accent) }
    }
}
