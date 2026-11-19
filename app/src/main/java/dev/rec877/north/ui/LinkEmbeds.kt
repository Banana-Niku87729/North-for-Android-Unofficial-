package dev.rec877.north.ui

import android.annotation.SuppressLint
import android.content.Intent
import android.view.View
import android.view.ViewGroup
import android.webkit.WebChromeClient
import android.webkit.WebResourceRequest
import android.webkit.WebView
import android.webkit.WebViewClient
import android.util.LruCache
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import coil3.ImageLoader
import coil3.compose.AsyncImage
import dev.rec877.north.LinkPreview
import dev.rec877.north.ui.theme.North
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.net.URLEncoder
import java.util.concurrent.TimeUnit

/** 設定「リンクのプレビューと再生」。オフのときは従来どおりURLのカードだけを出す。 */
internal val LocalExternalEmbeds = compositionLocalOf { true }

private sealed interface Fetch<out T> {
    data object Loading : Fetch<Nothing>
    data object Failed : Fetch<Nothing>
    data class Ok<T>(val value: T) : Fetch<T>
}

/**
 * YouTube(oEmbed) と X(api.fxtwitter.com) の取得。
 * 通信先は各サービスだけで、northのトークンは送らない（このクライアントは認証情報を持たない）。
 */
internal object EmbedRepo {
    private class Entry<T>(val at: Long, val value: T?)

    private const val OK_TTL_MS = 30 * 60_000L
    private const val FAIL_TTL_MS = 2 * 60_000L

    private val http = OkHttpClient.Builder()
        .connectTimeout(10, TimeUnit.SECONDS)
        .readTimeout(15, TimeUnit.SECONDS)
        .build()
    private val youtubeCache = LruCache<String, Entry<YouTubeMeta>>(64)
    private val tweetCache = LruCache<String, Entry<TweetData>>(64)

    private fun <T> fresh(cache: LruCache<String, Entry<T>>, key: String): Entry<T>? {
        val e = cache.get(key) ?: return null
        val ttl = if (e.value != null) OK_TTL_MS else FAIL_TTL_MS
        return if (System.currentTimeMillis() - e.at < ttl) e else null
    }

    private fun get(url: String): String? = runCatching {
        val req = Request.Builder().url(url).header("User-Agent", "North-Android/1.0").build()
        http.newCall(req).execute().use { r -> if (r.isSuccessful) r.body?.string() else null }
    }.getOrNull()

    suspend fun youtube(link: EmbedLink.YouTube): YouTubeMeta? {
        fresh(youtubeCache, link.videoId)?.let { return it.value }
        val meta = withContext(Dispatchers.IO) {
            val watch = URLEncoder.encode("https://www.youtube.com/watch?v=${link.videoId}", "UTF-8")
            get("https://www.youtube.com/oembed?format=json&url=$watch")?.let(::parseYouTubeOEmbed)
        }
        youtubeCache.put(link.videoId, Entry(System.currentTimeMillis(), meta))
        return meta
    }

    suspend fun tweet(link: EmbedLink.Tweet): TweetData? {
        fresh(tweetCache, link.id)?.let { return it.value }
        val data = withContext(Dispatchers.IO) {
            get("https://api.fxtwitter.com/${link.user}/status/${link.id}")?.let(::parseTweetJson)
        }
        tweetCache.put(link.id, Entry(System.currentTimeMillis(), data))
        return data
    }
}

/**
 * ポスト内のリンク表示の入口。
 * YouTube / X のリンクはアプリ内で再生・表示し、それ以外は従来どおり
 * （APIのプレビューがあればそれ、無ければURLのカード）。
 */
@Composable
fun PostLinks(text: String, preview: LinkPreview?, loader: ImageLoader, onOpenUrl: (String) -> Unit) {
    val enabled = LocalExternalEmbeds.current
    val embed = remember(text, preview?.url, enabled) { if (enabled) findEmbed(text, preview?.url) else null }
    when (embed) {
        is EmbedLink.YouTube -> {
            YouTubeEmbed(embed, loader, onOpenUrl)
            LinkCards(text = text, onOpenUrl = onOpenUrl, skip = setOf(embed.url))
        }
        is EmbedLink.Tweet -> {
            TweetEmbed(embed, loader, onOpenUrl)
            LinkCards(text = text, onOpenUrl = onOpenUrl, skip = setOf(embed.url))
        }
        null -> {
            val p = preview?.takeIf { it.url.isNotBlank() }
            // APIがリンクのプレビューを返すポスト（ニュースなど）はそれを出し、無いときだけURLのカードにする
            if (p != null) LinkPreviewCard(p, loader, onOpenUrl) else LinkCards(text = text, onOpenUrl = onOpenUrl)
        }
    }
}

// ---------------------------------------------------------------- YouTube

@Composable
private fun YouTubeEmbed(link: EmbedLink.YouTube, loader: ImageLoader, onOpenUrl: (String) -> Unit) {
    val c = North.colors
    val shape = RoundedCornerShape(16.dp)
    val meta by produceState<YouTubeMeta?>(null, link.videoId) { value = EmbedRepo.youtube(link) }
    // 画面外へ出ると再生は止まる（プレーヤーを作り直す）ので、一覧を重くしない
    var playing by remember(link.videoId) { mutableStateOf(false) }

    Column(Modifier.fillMaxWidth().padding(top = 8.dp).clip(shape).border(1.dp, c.border, shape)) {
        Column(
            Modifier.fillMaxWidth().clickable { onOpenUrl(link.url) }
                .padding(start = 12.dp, end = 12.dp, top = 10.dp, bottom = 8.dp),
        ) {
            Text("YouTube", color = c.textMuted, fontSize = 12.sp)
            meta?.author?.takeIf { it.isNotBlank() }?.let {
                Text(it, color = c.textMuted, fontSize = 13.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
            Text(
                meta?.title ?: link.url,
                color = c.text, fontSize = 15.sp, fontWeight = FontWeight.Bold,
                maxLines = 2, overflow = TextOverflow.Ellipsis,
                modifier = Modifier.padding(top = 2.dp),
            )
        }
        Box(Modifier.fillMaxWidth().aspectRatio(16f / 9f).background(Color.Black)) {
            if (playing) {
                YouTubeWebPlayer(link, Modifier.fillMaxSize())
            } else {
                Box(Modifier.fillMaxSize().clickable { playing = true }, contentAlignment = Alignment.Center) {
                    AsyncImage(
                        model = meta?.thumbnail ?: "https://i.ytimg.com/vi/${link.videoId}/hqdefault.jpg",
                        imageLoader = loader,
                        contentDescription = meta?.title,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.fillMaxSize(),
                    )
                    PlayBadge()
                }
            }
        }
    }
}

@Composable
private fun PlayBadge() {
    Box(Modifier.size(56.dp).clip(CircleShape).background(Color(0x99000000)), contentAlignment = Alignment.Center) {
        Text("▶", color = Color.White, fontSize = 22.sp)
    }
}

private fun youtubeEmbedHtml(link: EmbedLink.YouTube): String {
    val src = "https://www.youtube-nocookie.com/embed/${link.videoId}" +
        "?autoplay=1&playsinline=1&rel=0&start=${link.startSec}"
    return """<!DOCTYPE html><html><head><meta name="viewport" content="width=device-width,initial-scale=1">
<style>html,body{margin:0;height:100%;background:#000;overflow:hidden}iframe{position:absolute;top:0;left:0;width:100%;height:100%;border:0}</style>
</head><body><iframe src="$src" allow="autoplay; encrypted-media; picture-in-picture; fullscreen" allowfullscreen
referrerpolicy="strict-origin-when-cross-origin"></iframe></body></html>"""
}

/**
 * YouTube公式の埋め込みプレーヤー(WebView)。
 * 埋め込み元を示すため、アプリのパッケージ名を origin にしたページとして読み込む
 * （origin/Referer が無いと「エラー153」になる）。全画面ボタンはDialogに載せ替える。
 */
@SuppressLint("SetJavaScriptEnabled")
@Composable
private fun YouTubeWebPlayer(link: EmbedLink.YouTube, modifier: Modifier = Modifier) {
    val ctx = LocalContext.current
    var custom by remember(link.videoId) { mutableStateOf<Pair<View, WebChromeClient.CustomViewCallback>?>(null) }

    val webView = remember(link.videoId) {
        WebView(ctx).apply {
            setBackgroundColor(android.graphics.Color.BLACK)
            settings.javaScriptEnabled = true
            settings.domStorageEnabled = true
            settings.mediaPlaybackRequiresUserGesture = false
            settings.allowFileAccess = false
            settings.allowContentAccess = false
            webViewClient = object : WebViewClient() {
                // 「YouTubeで見る」などの遷移は、このWebViewの中ではなく外部で開く
                override fun shouldOverrideUrlLoading(view: WebView, request: WebResourceRequest): Boolean {
                    if (!request.isForMainFrame) return false
                    val scheme = request.url.scheme?.lowercase()
                    if (scheme != "http" && scheme != "https") return true
                    runCatching { ctx.startActivity(Intent(Intent.ACTION_VIEW, request.url).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }
                    return true
                }
            }
            webChromeClient = object : WebChromeClient() {
                override fun onShowCustomView(view: View, callback: CustomViewCallback) { custom = view to callback }
                override fun onHideCustomView() { custom = null }
            }
            loadDataWithBaseURL("https://${ctx.packageName}/", youtubeEmbedHtml(link), "text/html", "utf-8", null)
        }
    }

    val owner = LocalLifecycleOwner.current
    DisposableEffect(owner, webView) {
        val obs = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_PAUSE -> webView.onPause()
                Lifecycle.Event.ON_RESUME -> webView.onResume()
                else -> Unit
            }
        }
        owner.lifecycle.addObserver(obs)
        onDispose { owner.lifecycle.removeObserver(obs) }
    }
    DisposableEffect(webView) {
        onDispose {
            webView.stopLoading()
            (webView.parent as? ViewGroup)?.removeView(webView)
            webView.destroy()
        }
    }

    Box(modifier.background(Color.Black)) {
        AndroidView(factory = { webView }, modifier = Modifier.fillMaxSize())
    }

    custom?.let { (view, callback) ->
        Dialog(
            onDismissRequest = { callback.onCustomViewHidden(); custom = null },
            properties = DialogProperties(usePlatformDefaultWidth = false, decorFitsSystemWindows = false),
        ) {
            AndroidView(
                factory = { (view.parent as? ViewGroup)?.removeView(view); view },
                modifier = Modifier.fillMaxSize().background(Color.Black),
            )
        }
    }
}

// ---------------------------------------------------------------- X (fxtwitter)

@Composable
private fun TweetEmbed(link: EmbedLink.Tweet, loader: ImageLoader, onOpenUrl: (String) -> Unit) {
    val c = North.colors
    val state by produceState<Fetch<TweetData>>(Fetch.Loading, link.id) {
        value = EmbedRepo.tweet(link)?.let { Fetch.Ok(it) } ?: Fetch.Failed
    }
    when (val s = state) {
        Fetch.Loading -> {
            val shape = RoundedCornerShape(16.dp)
            Box(
                Modifier.fillMaxWidth().padding(top = 8.dp).heightIn(min = 72.dp).clip(shape)
                    .border(1.dp, c.border, shape).clickable { onOpenUrl(link.url) }.padding(12.dp),
                contentAlignment = Alignment.CenterStart,
            ) { Text("Xのポストを読み込み中…", color = c.textMuted, fontSize = 13.sp) }
        }
        Fetch.Failed -> LinkUrlCard(link.url, onOpenUrl)
        is Fetch.Ok -> TweetCard(s.value, link, loader, onOpenUrl)
    }
}

private fun tweetDate(epochSec: Long): String =
    java.text.SimpleDateFormat("yyyy年M月d日 H:mm", java.util.Locale.JAPAN).format(java.util.Date(epochSec * 1000))

@Composable
private fun ExternalAvatar(url: String?, size: Dp, loader: ImageLoader) {
    val c = North.colors
    Box(Modifier.size(size).clip(CircleShape).background(c.elev3)) {
        if (url != null) AsyncImage(
            model = url, imageLoader = loader, contentDescription = null,
            contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize(),
        )
    }
}

@Composable
private fun TweetCard(t: TweetData, link: EmbedLink.Tweet, loader: ImageLoader, onOpenUrl: (String) -> Unit) {
    val c = North.colors
    val shape = RoundedCornerShape(16.dp)
    val privacy = LocalMediaPrivacy.current
    var revealed by remember(link.id) { mutableStateOf(false) }
    // 配慮モード中・センシティブ指定のポストは、メディアをタップするまで取得しない
    val hideMedia = (privacy.active || t.sensitive) && !revealed

    Column(Modifier.fillMaxWidth().padding(top = 8.dp).clip(shape).border(1.dp, c.border, shape)) {
        Column(Modifier.fillMaxWidth().clickable { onOpenUrl(link.url) }.padding(12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                ExternalAvatar(t.avatarUrl, 32.dp, loader)
                Column(Modifier.weight(1f)) {
                    Text(t.authorName, color = c.text, fontSize = 14.sp, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Text("@${t.handle}", color = c.textMuted, fontSize = 13.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
                Text("X", color = c.textMuted, fontSize = 15.sp, fontWeight = FontWeight.ExtraBold)
            }
            if (t.text.isNotEmpty()) {
                Text(
                    remember(t.text, c.accent) { richAnnotatedString(t.text, c.accent) },
                    color = c.text, fontSize = 15.sp, lineHeight = 21.sp,
                    maxLines = 10, overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.padding(top = 8.dp),
                )
            }
            t.quote?.let { q ->
                val qShape = RoundedCornerShape(12.dp)
                Column(Modifier.fillMaxWidth().padding(top = 8.dp).clip(qShape).border(1.dp, c.border, qShape).padding(10.dp)) {
                    Text("${q.authorName} @${q.handle}", color = c.textMuted, fontSize = 13.sp, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    if (q.text.isNotEmpty()) Text(q.text, color = c.text, fontSize = 14.sp, maxLines = 4, overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(top = 2.dp))
                }
            }
        }

        if (t.media.isNotEmpty()) {
            if (hideMedia) {
                Column(
                    Modifier.fillMaxWidth().heightIn(min = 120.dp).background(c.elev2).padding(16.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center,
                ) {
                    Text("メディアを非表示にしています", color = c.textMuted, fontSize = 14.sp)
                    TextButton(onClick = { revealed = true }) { Text("このメディアを表示", color = c.accent) }
                }
            } else {
                TweetMediaBlock(t.media, loader) { onOpenUrl(link.url) }
            }
        }

        Column(Modifier.fillMaxWidth().clickable { onOpenUrl(link.url) }.padding(start = 12.dp, end = 12.dp, top = 8.dp, bottom = 10.dp)) {
            t.createdEpoch?.let { Text(tweetDate(it), color = c.textMuted, fontSize = 13.sp) }
            Row(Modifier.padding(top = 6.dp), horizontalArrangement = Arrangement.spacedBy(14.dp), verticalAlignment = Alignment.CenterVertically) {
                TweetStat(NIcons.Reply, t.replies)
                TweetStat(NIcons.Repost, t.retweets)
                TweetStat(NIcons.Like, t.likes)
                t.views?.let { Text("${compactCount(it)} 表示", color = c.textMuted, fontSize = 13.sp, maxLines = 1) }
            }
        }
    }
}

@Composable
private fun TweetStat(icon: NIcon, count: Long) {
    val c = North.colors
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
        NorthIcon(icon, c.textMuted, 15.dp, modifier = Modifier.size(15.dp))
        Text(compactCount(count), color = c.textMuted, fontSize = 13.sp, maxLines = 1)
    }
}

@Composable
private fun TweetMediaBlock(media: List<TweetMedia>, loader: ImageLoader, onTapPhoto: () -> Unit) {
    val video = media.firstOrNull { it.kind != "photo" }
    if (video != null) {
        TweetVideo(video, loader)
        return
    }
    val photos = media.take(4)
    if (photos.size == 1) {
        val p = photos[0]
        val ratio = if (p.width > 0 && p.height > 0) (p.width.toFloat() / p.height).coerceIn(0.75f, 2f) else 1.6f
        AsyncImage(
            model = p.url, imageLoader = loader, contentDescription = null, contentScale = ContentScale.Crop,
            modifier = Modifier.fillMaxWidth().aspectRatio(ratio).background(North.colors.elev3).clickable(onClick = onTapPhoto),
        )
        return
    }
    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
        photos.chunked(2).forEach { rowItems ->
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(2.dp)) {
                rowItems.forEach { p ->
                    AsyncImage(
                        model = p.url, imageLoader = loader, contentDescription = null, contentScale = ContentScale.Crop,
                        modifier = Modifier.weight(1f).aspectRatio(1.4f).background(North.colors.elev3).clickable(onClick = onTapPhoto),
                    )
                }
                if (rowItems.size == 1) Spacer(Modifier.weight(1f))
            }
        }
    }
}

/** サムネイルを押したときだけプレーヤーを作る（GIFはミュートでループ）。 */
@Composable
private fun TweetVideo(media: TweetMedia, loader: ImageLoader) {
    var playing by remember(media.url) { mutableStateOf(false) }
    val ratio = if (media.width > 0 && media.height > 0) (media.width.toFloat() / media.height).coerceIn(0.6f, 2f) else 16f / 9f
    val isGif = media.kind == "gif"
    Box(Modifier.fillMaxWidth().background(Color.Black), contentAlignment = Alignment.Center) {
        Box(Modifier.fillMaxWidth().heightIn(max = 360.dp).aspectRatio(ratio)) {
            if (playing) {
                ExternalVideoPlayer(media.url, loop = isGif, modifier = Modifier.fillMaxSize())
            } else {
                Box(Modifier.fillMaxSize().clickable { playing = true }, contentAlignment = Alignment.Center) {
                    media.thumbnail?.let {
                        AsyncImage(
                            model = it, imageLoader = loader, contentDescription = null,
                            contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize(),
                        )
                    }
                    PlayBadge()
                    if (isGif) Text(
                        "GIF", color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.Bold,
                        modifier = Modifier.align(Alignment.BottomStart).padding(8.dp)
                            .clip(RoundedCornerShape(4.dp)).background(Color(0x99000000)).padding(horizontal = 6.dp, vertical = 2.dp),
                    )
                }
            }
        }
    }
}
