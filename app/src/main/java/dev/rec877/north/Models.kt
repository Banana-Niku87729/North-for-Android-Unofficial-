package dev.rec877.north

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/** /2 のJSON成功応答は { "data": ... } */
@Serializable data class Envelope<T>(val data: T)

@Serializable
data class User(
    val id: String = "",
    val handle: String = "",
    val name: String = "",
    val bio: String? = null,
    val location: String? = null,
    val website: String? = null,
    val avatarUrl: String? = null,
    val headerUrl: String? = null,
    @SerialName("protected") val isProtected: Boolean = false,
    val verified: Boolean = false,
    val followerCount: Int = 0,
    val followingCount: Int = 0,
    val tweetCount: Int = 0,
    val createdAt: String? = null,
    val following: Boolean = false,
    val followedBy: Boolean = false,
    val blocking: Boolean = false,
    val muting: Boolean = false,
)

@Serializable
data class Media(
    val id: String,
    /** PHOTO / GIF / VIDEO */
    val kind: String = "PHOTO",
    val url: String,
    val thumbnailUrl: String? = null,
    val width: Int = 0,
    val height: Int = 0,
    val altText: String? = null,
    val sensitive: Boolean = false,
    val status: String? = null,
    val durationMs: Long? = null,
    val warning: String? = null,
)

@Serializable
data class Post(
    val id: String,
    // 表示できないポストは本文などが返らないので、すべて既定値を持たせて落ちないようにする
    val text: String = "",
    val createdAt: String = "",
    val editedAt: String? = null,
    val author: User = User(),
    val conversationId: String? = null,
    val source: String? = null,
    val inReplyToId: String? = null,
    val inReplyToHandle: String? = null,
    /** 引用しているポスト（入れ子は1段まで） */
    val quoted: Post? = null,
    val retweetOf: Post? = null,
    val likeCount: Int = 0,
    val retweetCount: Int = 0,
    val replyCount: Int = 0,
    val quoteCount: Int = 0,
    val poll: Poll? = null,
    val media: List<Media> = emptyList(),
    val liked: Boolean = false,
    val retweeted: Boolean = false,
    val bookmarked: Boolean = false,
    val unavailable: Boolean = false,
    val quotedUnavailable: Boolean = false,
    /** muted / blocked */
    val hiddenReason: String? = null,
)

@Serializable data class PollOption(
    val id: String,
    val label: String,
    val position: Int = 0,
    val voteCount: Int = 0,
    val percent: Double? = null,
)

@Serializable data class Poll(
    val id: String,
    val endsAt: String,
    val ended: Boolean = false,
    val totalVotes: Int = 0,
    val viewerOptionId: String? = null,
    val options: List<PollOption> = emptyList(),
)

/** このポスト自身、リポスト元、引用先のうち id が一致するものに f を適用する */
fun Post.patched(id: String, f: (Post) -> Post): Post {
    var p = if (this.id == id) f(this) else this
    p.retweetOf?.let { if (it.id == id) p = p.copy(retweetOf = f(it)) }
    p.quoted?.let { if (it.id == id) p = p.copy(quoted = f(it)) }
    return p
}

@Serializable data class PostPage(val items: List<Post>, val nextCursor: String? = null)

@Serializable data class BookmarkFolder(
    val id: String,
    val name: String,
    val color: String = "",
    val count: Int = 0,
    val createdAt: String? = null,
)
@Serializable data class BookmarkFolderPage(val items: List<BookmarkFolder> = emptyList())

/** フォロー中・フォロワー・いいねしたユーザーのページ */
@Serializable data class UserPage(val items: List<User>, val nextCursor: String? = null)

/** 検索前の画面で表示する、north サーバー集計のトレンド。 */
@Serializable data class Trend(val tag: String, val count: Int = 0, val isHashtag: Boolean = false)
@Serializable data class TrendPage(val items: List<Trend> = emptyList())

@Serializable data class NorthList(
    val id: String, val name: String, val description: String? = null,
    @SerialName("private") val isPrivate: Boolean = false, val memberCount: Int = 0, val followerCount: Int = 0,
    val owner: User = User(), val ownedByViewer: Boolean = false, val followedByViewer: Boolean = false,
)
@Serializable data class NorthLists(val items: List<NorthList> = emptyList(), val followed: List<NorthList> = emptyList(), val pinned: List<NorthList> = emptyList())

@Serializable data class CreatedPost(val id: String, val text: String = "")

/** 投稿作成時だけ使う投票の入力値。サーバー側は2〜4択、5分〜7日を受け付ける。 */
data class PollInput(val options: List<String>, val durationMinutes: Int = 1440)

@Serializable data class DraftItem(val text: String = "", val replyPolicy: String? = null, val media: List<Media> = emptyList())
@Serializable data class Draft(val id: String, val items: List<DraftItem> = emptyList(), val createdAt: String = "", val updatedAt: String = "")
@Serializable data class DraftPage(val items: List<Draft> = emptyList())

/** /2/tweets/{id}/conversation: 祖先（古い順）・本人・返信（最大10件ずつ、続きは nextCursor） */
@Serializable
data class Conversation(
    val ancestors: List<Post> = emptyList(),
    val tweet: Post,
    val replies: List<Post> = emptyList(),
    val nextCursor: String? = null,
)

// ---- 通知 ----
@Serializable
data class Notification(
    /** まとめた中で最も新しい通知のID */
    val id: String,
    /** FOLLOW / LIKE / RETWEET / QUOTE / REPLY / MENTION / POST */
    val kind: String = "",
    val actors: List<User> = emptyList(),
    val actorCount: Int = 0,
    val groupCount: Int = 1,
    val targetCount: Int = 0,
    /** groupCount が2以上のときと、FOLLOWのときは null */
    val tweet: Post? = null,
    val createdAt: String = "",
    val read: Boolean = true,
)

@Serializable data class NotificationPage(val items: List<Notification>, val nextCursor: String? = null)
@Serializable data class UnreadCount(val count: Int = 0)
@Serializable data class MarkedRead(val marked: Int = 0)

// ---- OAuth（こちらは { "data": ... } で包まれない、RFC準拠のフラット形式）----
@Serializable
data class TokenResponse(
    @SerialName("access_token") val accessToken: String,
    @SerialName("refresh_token") val refreshToken: String,
    @SerialName("expires_in") val expiresIn: Int,
    val scope: String = "",
)

@Serializable data class OAuthError(val error: String)
