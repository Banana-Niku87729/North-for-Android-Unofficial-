@file:OptIn(UnstableApi::class)

package dev.rec877.north.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.rememberTransformableState
import androidx.compose.foundation.gestures.transformable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
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
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.okhttp.OkHttpDataSource
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import androidx.media3.ui.PlayerView
import coil3.ImageLoader
import coil3.compose.AsyncImage
import dev.rec877.north.Config
import dev.rec877.north.Media
import okhttp3.OkHttpClient

/**
 * 動画1ページ分。ブラウザで開くとBearerが付かず取得できないので、
 * Bearer付きのOkHttp（MainViewModel.imageClient）をデータソースにしたExoPlayerで再生する。
 * 表示中のページだけプレーヤーを作り、離れたら解放する。
 */
@Composable
fun VideoPage(
    media: Media,
    httpClient: OkHttpClient,
    active: Boolean,
    loader: ImageLoader,
    modifier: Modifier = Modifier,
    zoomable: Boolean = false,
) {
    if (!active) {
        Box(modifier.background(Color.Black), contentAlignment = Alignment.Center) {
            AsyncImage(
                model = Config.absUrl(media.thumbnailUrl),
                imageLoader = loader,
                contentDescription = null,
                contentScale = ContentScale.Fit,
                modifier = Modifier.fillMaxSize(),
            )
        }
        return
    }

    val ctx = LocalContext.current
    var error by remember(media.id) { mutableStateOf<String?>(null) }

    val player = remember(media.id) {
        ExoPlayer.Builder(ctx)
            .setMediaSourceFactory(DefaultMediaSourceFactory(OkHttpDataSource.Factory(httpClient)))
            .build()
            .apply {
                setMediaItem(MediaItem.fromUri(Config.absUrl(media.url) ?: ""))
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
    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner, player) {
        val obs = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_PAUSE) player.pause()
        }
        lifecycleOwner.lifecycle.addObserver(obs)
        onDispose { lifecycleOwner.lifecycle.removeObserver(obs) }
    }

    var scale by remember(media.id) { mutableStateOf(1f) }
    var offset by remember(media.id) { mutableStateOf(Offset.Zero) }
    val zoomState = rememberTransformableState { zoom, pan, _ ->
        val next = (scale * zoom).coerceIn(1f, 5f)
        scale = next
        offset = if (next == 1f) Offset.Zero else offset + pan
    }
    val videoModifier = if (zoomable) modifier.transformable(zoomState).graphicsLayer {
        scaleX = scale; scaleY = scale; translationX = offset.x; translationY = offset.y
    } else modifier

    Box(videoModifier.background(Color.Black), contentAlignment = Alignment.Center) {
        AndroidView(
            factory = { c ->
                PlayerView(c).apply {
                    this.player = player
                    useController = true
                    setShowBuffering(PlayerView.SHOW_BUFFERING_WHEN_PLAYING)
                }
            },
            modifier = Modifier.fillMaxSize(),
        )
        error?.let {
            Text(
                it,
                color = Color.White,
                fontSize = 14.sp,
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(24.dp),
            )
        }
    }
}

/** TL内の動画。サムネイルを押したときだけプレーヤーを作るので、一覧を重くしない。 */
@Composable
fun InlineVideo(media: Media, httpClient: OkHttpClient, loader: ImageLoader, modifier: Modifier = Modifier) {
    var playing by remember(media.id) { mutableStateOf(false) }
    if (playing) {
        VideoPage(media, httpClient, active = true, loader = loader, modifier = modifier)
    } else {
        Box(modifier.background(Color.Black).clickable { playing = true }, contentAlignment = Alignment.Center) {
            AsyncImage(
                model = Config.absUrl(media.thumbnailUrl ?: media.url), imageLoader = loader,
                contentDescription = media.altText, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize(),
            )
            Box(Modifier.size(48.dp).clip(CircleShape).background(Color(0x99000000)), contentAlignment = Alignment.Center) {
                Text("▶", color = Color.White, fontSize = 18.sp)
            }
        }
    }
}
