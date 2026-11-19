package dev.rec877.north.ui

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.longOrNull

/**
 * ポスト内のURLのうち、アプリ内で再生・表示できるもの。
 * 通信や描画は含めない純粋なデータ/解析だけをここに置く（JVMの単体テストで確認できる）。
 */
sealed interface EmbedLink {
    val url: String

    data class YouTube(override val url: String, val videoId: String, val startSec: Int) : EmbedLink
    data class Tweet(override val url: String, val user: String, val id: String) : EmbedLink
}

private val YT_ID = Regex("^[A-Za-z0-9_-]{11}$")
private val TWEET_HOSTS = setOf("x.com", "twitter.com", "fxtwitter.com", "vxtwitter.com", "fixupx.com", "fixvx.com", "twittpr.com")
private val YT_HOSTS = setOf("youtube.com", "music.youtube.com", "youtube-nocookie.com")
private val TIME_HMS = Regex("^(?:(\\d+)h)?(?:(\\d+)m)?(?:(\\d+)s?)?$", RegexOption.IGNORE_CASE)
private val BARE_URL = Regex("""https?://[^\s]+""")
private const val URL_TRAIL = ".,!?。、)]}"

private fun queryOf(raw: String?): Map<String, String> {
    if (raw.isNullOrEmpty()) return emptyMap()
    return raw.split('&').mapNotNull {
        val i = it.indexOf('=')
        if (i <= 0) null else {
            val k = runCatching { java.net.URLDecoder.decode(it.substring(0, i), "UTF-8") }.getOrNull()
            val v = runCatching { java.net.URLDecoder.decode(it.substring(i + 1), "UTF-8") }.getOrNull()
            if (k == null || v == null) null else k to v
        }
    }.toMap()
}

/** 120 / 2m3s / 1h2m3s / 90s を秒にする。読めなければ0 */
internal fun parseStartSeconds(raw: String?): Int {
    if (raw.isNullOrBlank()) return 0
    val m = TIME_HMS.matchEntire(raw.trim()) ?: return 0
    val h = m.groupValues[1].toIntOrNull() ?: 0
    val min = m.groupValues[2].toIntOrNull() ?: 0
    val s = m.groupValues[3].toIntOrNull() ?: 0
    // 数字だけ（"120"）は秒。正規表現では最後のグループに入る
    return (h * 3600 + min * 60 + s).coerceIn(0, 24 * 3600)
}

fun parseEmbedLink(raw: String): EmbedLink? {
    val url = raw.trim()
    val uri = runCatching { java.net.URI(url) }.getOrNull() ?: return null
    val scheme = uri.scheme?.lowercase()
    if (scheme != "http" && scheme != "https") return null
    val host = uri.host?.lowercase()
        ?.removePrefix("www.")?.removePrefix("m.")?.removePrefix("mobile.")
        ?: return null
    val segs = (uri.rawPath ?: "").split('/').filter { it.isNotEmpty() }
    val query = queryOf(uri.rawQuery)

    if (host == "youtu.be" || host in YT_HOSTS) {
        val id = when {
            host == "youtu.be" -> segs.firstOrNull()
            segs.firstOrNull() == "watch" -> query["v"]
            segs.firstOrNull() in setOf("shorts", "live", "embed", "v") -> segs.getOrNull(1)
            else -> null
        }
        if (id == null || !YT_ID.matches(id)) return null
        return EmbedLink.YouTube(url, id, parseStartSeconds(query["t"] ?: query["start"]))
    }

    if (host in TWEET_HOSTS) {
        val i = segs.indexOfFirst { it == "status" || it == "statuses" }
        if (i < 0) return null
        val id = segs.getOrNull(i + 1)?.takeIf { it.isNotEmpty() && it.all(Char::isDigit) && it.length <= 20 } ?: return null
        val user = segs.getOrNull(i - 1)?.takeIf { it != "web" && it != "i" } ?: "i"
        return EmbedLink.Tweet(url, user, id)
    }
    return null
}

/** 本文（とAPIが返したプレビューURL）の中で最初に見つかった埋め込み可能リンク */
fun findEmbed(text: String, previewUrl: String? = null): EmbedLink? {
    BARE_URL.findAll(text).forEach { m ->
        parseEmbedLink(m.value.trimEnd(*URL_TRAIL.toCharArray()))?.let { return it }
    }
    return previewUrl?.takeIf { it.isNotBlank() }?.let(::parseEmbedLink)
}

// ---------------- fxtwitter ----------------

data class TweetMedia(
    /** photo / video / gif */
    val kind: String,
    val url: String,
    val thumbnail: String?,
    val width: Int,
    val height: Int,
)

data class TweetData(
    val authorName: String,
    val handle: String,
    val avatarUrl: String?,
    val text: String,
    /** 秒 */
    val createdEpoch: Long?,
    val replies: Long,
    val retweets: Long,
    val likes: Long,
    val views: Long?,
    val sensitive: Boolean,
    val media: List<TweetMedia>,
    val quote: TweetData?,
)

data class YouTubeMeta(val title: String, val author: String?, val thumbnail: String?)

private val lenientJson = Json { ignoreUnknownKeys = true; isLenient = true }

private fun JsonObject.str(key: String): String? = (this[key] as? JsonPrimitive)?.contentOrNull
private fun JsonObject.long(key: String): Long? = (this[key] as? JsonPrimitive)?.longOrNull
private fun JsonObject.obj(key: String): JsonObject? = this[key] as? JsonObject
private fun JsonObject.arr(key: String): JsonArray? = this[key] as? JsonArray

private fun mediaFrom(o: JsonObject): TweetMedia? {
    val type = o.str("type")?.lowercase() ?: return null
    val url = o.str("url")?.takeIf { it.isNotBlank() } ?: return null
    val kind = when (type) { "photo" -> "photo"; "gif" -> "gif"; "video" -> "video"; else -> return null }
    return TweetMedia(
        kind = kind,
        url = url,
        thumbnail = o.str("thumbnail_url"),
        width = o.long("width")?.toInt() ?: 0,
        height = o.long("height")?.toInt() ?: 0,
    )
}

private fun tweetFrom(t: JsonObject, depth: Int): TweetData? {
    val author = t.obj("author")
    val handle = author?.str("screen_name") ?: return null
    val mediaObj = t.obj("media")
    val all = mediaObj?.arr("all")?.mapNotNull { (it as? JsonObject)?.let(::mediaFrom) }
        ?: (mediaObj?.arr("photos").orEmpty() + mediaObj?.arr("videos").orEmpty())
            .mapNotNull { (it as? JsonObject)?.let(::mediaFrom) }
    return TweetData(
        authorName = author.str("name") ?: handle,
        handle = handle,
        avatarUrl = author.str("avatar_url"),
        text = t.str("text").orEmpty(),
        createdEpoch = t.long("created_timestamp"),
        replies = t.long("replies") ?: 0,
        retweets = t.long("retweets") ?: 0,
        likes = t.long("likes") ?: 0,
        views = t.long("views"),
        sensitive = (t["possibly_sensitive"] as? JsonPrimitive)?.booleanOrNull ?: false,
        media = all,
        // 引用は1段だけ（引用の引用は辿らない）
        quote = if (depth < 1) t.obj("quote")?.let { tweetFrom(it, depth + 1) } else null,
    )
}

fun parseTweetJson(body: String): TweetData? = runCatching {
    val root = lenientJson.parseToJsonElement(body).jsonObject
    val tweet = root.obj("tweet") ?: return@runCatching null
    tweetFrom(tweet, 0)
}.getOrNull()

fun parseYouTubeOEmbed(body: String): YouTubeMeta? = runCatching {
    val o = lenientJson.parseToJsonElement(body).jsonObject
    val title = o.str("title")?.takeIf { it.isNotBlank() } ?: return@runCatching null
    YouTubeMeta(title, o.str("author_name"), o.str("thumbnail_url"))
}.getOrNull()

/** 1234 → 1,234 / 12,345 → 1.2万 / 1.2億 */
fun compactCount(n: Long): String = when {
    n >= 100_000_000 -> String.format(java.util.Locale.JAPAN, "%.1f億", n / 100_000_000.0)
    n >= 10_000 -> String.format(java.util.Locale.JAPAN, "%.1f万", n / 10_000.0)
    else -> java.text.NumberFormat.getIntegerInstance(java.util.Locale.JAPAN).format(n)
}
