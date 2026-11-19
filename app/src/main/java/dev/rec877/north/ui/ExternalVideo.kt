@file:androidx.annotation.OptIn(androidx.media3.common.util.UnstableApi::class)

package dev.rec877.north.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.PlayerView

/**
 * X(video.twimg.com)など、northの外にある動画の再生。
 * VideoPage と違い、Bearerを付けない通常の通信で取得する（northのトークンを外部へ送らないため）。
 */
@Composable
internal fun ExternalVideoPlayer(url: String, loop: Boolean, modifier: Modifier = Modifier) {
    val ctx = LocalContext.current
    var error by remember(url) { mutableStateOf<String?>(null) }

    val player = remember(url) {
        ExoPlayer.Builder(ctx).build().apply {
            setMediaItem(MediaItem.fromUri(url))
            if (loop) {
                repeatMode = Player.REPEAT_MODE_ONE
                volume = 0f
            }
            prepare()
            playWhenReady = true
        }
    }

    DisposableEffect(player) {
        val listener = object : Player.Listener {
            override fun onPlayerError(e: PlaybackException) {
                error = "動画を再生できませんでした（${e.errorCodeName}）"
            }
        }
        player.addListener(listener)
        onDispose {
            player.removeListener(listener)
            player.release()
        }
    }

    // アプリが裏に回ったら止める（音だけ鳴り続けないように）
    val owner = LocalLifecycleOwner.current
    DisposableEffect(owner, player) {
        val obs = LifecycleEventObserver { _, event -> if (event == Lifecycle.Event.ON_PAUSE) player.pause() }
        owner.lifecycle.addObserver(obs)
        onDispose { owner.lifecycle.removeObserver(obs) }
    }

    Box(modifier.background(Color.Black), contentAlignment = Alignment.Center) {
        AndroidView(
            factory = { c ->
                PlayerView(c).apply {
                    this.player = player
                    useController = !loop
                    setShowBuffering(PlayerView.SHOW_BUFFERING_WHEN_PLAYING)
                }
            },
            modifier = Modifier.fillMaxSize(),
        )
        error?.let {
            Text(it, color = Color.White, fontSize = 14.sp, textAlign = TextAlign.Center, modifier = Modifier.padding(24.dp))
        }
    }
}
