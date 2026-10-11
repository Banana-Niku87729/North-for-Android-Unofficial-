package dev.rec877.north.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.rememberTransformableState
import androidx.compose.foundation.gestures.transformable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.compose.ui.geometry.Offset
import coil3.ImageLoader
import coil3.compose.AsyncImage
import okhttp3.OkHttpClient
import dev.rec877.north.Config
import dev.rec877.north.Media
import dev.rec877.north.ui.theme.North

/** サイトの .media-grid を再現: 角丸16・枠線・2dpの隙間・1/2/3/4枚のレイアウト */
@Composable
fun MediaGrid(
    media: List<Media>,
    loader: ImageLoader,
    onOpen: (Int) -> Unit,
    httpClient: OkHttpClient,
    modifier: Modifier = Modifier,
) {
    val c = North.colors
    val items = media.take(4)
    if (items.isEmpty()) return
    var revealed by remember(items) { mutableStateOf(false) }
    val covered = !revealed && items.any { it.sensitive }
    val shape = RoundedCornerShape(16.dp)

    Box(
        modifier
            .padding(top = 12.dp)
            .fillMaxWidth()
            .clip(shape)
            .border(1.dp, c.border, shape)
    ) {
        when (items.size) {
            1 -> {
                val m = items[0]
                val ratio = if (m.width > 0 && m.height > 0) m.width.toFloat() / m.height else 16f / 9f
                Cell(
                    m, loader, httpClient, fit = true, onClick = { onOpen(0) },
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(max = 510.dp)
                        .aspectRatio(ratio.coerceIn(0.5f, 3f)),
                )
            }
            2 -> Row(
                Modifier.fillMaxWidth().aspectRatio(16f / 9f),
                horizontalArrangement = Arrangement.spacedBy(2.dp),
            ) {
                items.forEachIndexed { i, m ->
                    Cell(m, loader, httpClient, false, { onOpen(i) }, Modifier.weight(1f).fillMaxHeight())
                }
            }
            3 -> Row(
                Modifier.fillMaxWidth().aspectRatio(16f / 9f),
                horizontalArrangement = Arrangement.spacedBy(2.dp),
            ) {
                Cell(items[0], loader, httpClient, false, { onOpen(0) }, Modifier.weight(1f).fillMaxHeight())
                Column(
                    Modifier.weight(1f).fillMaxHeight(),
                    verticalArrangement = Arrangement.spacedBy(2.dp),
                ) {
                    Cell(items[1], loader, httpClient, false, { onOpen(1) }, Modifier.weight(1f).fillMaxWidth())
                    Cell(items[2], loader, httpClient, false, { onOpen(2) }, Modifier.weight(1f).fillMaxWidth())
                }
            }
            else -> Column(
                Modifier.fillMaxWidth().aspectRatio(16f / 9f),
                verticalArrangement = Arrangement.spacedBy(2.dp),
            ) {
                Row(Modifier.weight(1f).fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(2.dp)) {
                    Cell(items[0], loader, httpClient, false, { onOpen(0) }, Modifier.weight(1f).fillMaxHeight())
                    Cell(items[1], loader, httpClient, false, { onOpen(1) }, Modifier.weight(1f).fillMaxHeight())
                }
                Row(Modifier.weight(1f).fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(2.dp)) {
                    Cell(items[2], loader, httpClient, false, { onOpen(2) }, Modifier.weight(1f).fillMaxHeight())
                    Cell(items[3], loader, httpClient, false, { onOpen(3) }, Modifier.weight(1f).fillMaxHeight())
                }
            }
        }

        if (covered) {
            // センシティブ設定のメディアは警告を挟む
            Box(
                Modifier.matchParentSize().background(c.elev2).clickable { revealed = true },
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    "センシティブな内容を含む可能性があります\nタップして表示",
                    color = c.textMuted,
                    fontSize = 14.sp,
                    textAlign = TextAlign.Center,
                )
            }
        }
    }
}

@Composable
private fun Cell(
    m: Media,
    loader: ImageLoader,
    httpClient: OkHttpClient,
    fit: Boolean,
    onClick: () -> Unit,
    modifier: Modifier,
) {
    val c = North.colors
    val src = if (m.kind == "VIDEO") (m.thumbnailUrl ?: m.url) else m.url
    if (m.kind == "VIDEO") {
        InlineVideo(m, httpClient, loader, modifier.background(c.elev2))
        return
    }
    Box(modifier.background(c.elev2).clickable(onClick = onClick), contentAlignment = Alignment.Center) {
        AsyncImage(
            model = Config.absUrl(src),
            imageLoader = loader,
            contentDescription = m.altText,
            contentScale = if (fit) ContentScale.Fit else ContentScale.Crop,
            modifier = Modifier.fillMaxSize(),
        )
    }
}

/** 全画面ビューア。スワイプで前後。写真はタップで閉じる。動画はアプリ内で再生する */
@Composable
fun MediaViewer(
    media: List<Media>,
    start: Int,
    loader: ImageLoader,
    httpClient: OkHttpClient,
    onClose: () -> Unit,
) {
    val pager = rememberPagerState(initialPage = start.coerceIn(0, media.lastIndex), pageCount = { media.size })
    Dialog(onDismissRequest = onClose, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Box(Modifier.fillMaxSize().background(Color.Black)) {
            HorizontalPager(state = pager, modifier = Modifier.fillMaxSize()) { page ->
                val m = media[page]
                if (m.kind == "VIDEO") {
                    VideoPage(
                        media = m,
                        httpClient = httpClient,
                        active = pager.currentPage == page,
                        loader = loader,
                        modifier = Modifier.fillMaxSize(),
                        zoomable = true,
                    )
                } else {
                    ZoomableImage(m, loader)
                }
            }
            // 動画の操作とタップが競合しないよう、閉じるボタンを常に出す
            Box(
                Modifier
                    .align(Alignment.TopStart)
                    .padding(top = 40.dp, start = 12.dp)
                    .size(40.dp)
                    .clip(CircleShape)
                    .background(Color(0x99000000))
                    .clickable(onClick = onClose),
                contentAlignment = Alignment.Center,
            ) { NorthIcon(NIcons.Close, Color.White, 20.dp, modifier = Modifier.size(20.dp)) }

            if (media.size > 1) {
                Text(
                    "${pager.currentPage + 1} / ${media.size}",
                    color = Color.White,
                    fontSize = 14.sp,
                    modifier = Modifier.align(Alignment.TopCenter).padding(top = 48.dp),
                )
            }
        }
    }
}

@Composable
private fun ZoomableImage(media: Media, loader: ImageLoader) {
    var scale by remember(media.id) { mutableStateOf(1f) }
    var offset by remember(media.id) { mutableStateOf(Offset.Zero) }
    val state = rememberTransformableState { zoom, pan, _ ->
        val next = (scale * zoom).coerceIn(1f, 5f)
        scale = next
        offset = if (next == 1f) Offset.Zero else offset + pan
    }
    AsyncImage(
        model = Config.absUrl(media.url), imageLoader = loader, contentDescription = media.altText,
        contentScale = ContentScale.Fit,
        modifier = Modifier.fillMaxSize().transformable(state).graphicsLayer {
            scaleX = scale; scaleY = scale; translationX = offset.x; translationY = offset.y
        },
    )
}
