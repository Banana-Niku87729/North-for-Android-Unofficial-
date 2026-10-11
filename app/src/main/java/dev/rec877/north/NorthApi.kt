package dev.rec877.north

import android.content.ContentResolver
import android.net.Uri
import android.provider.OpenableColumns
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.channelFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.add
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonArray
import kotlinx.serialization.json.putJsonObject
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.MultipartBody
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody
import okhttp3.RequestBody.Companion.toRequestBody
import java.io.IOException
import java.net.URLEncoder
import java.util.UUID
import java.util.concurrent.TimeUnit

private class ErrInfo(val code: Int?, val message: String?, val scopes: List<String>)

private fun parseErr(body: String): ErrInfo {
    val e = runCatching {
        Json.parseToJsonElement(body).jsonObject["errors"]?.jsonArray?.firstOrNull()?.jsonObject
    }.getOrNull()
    return ErrInfo(
        code = e?.get("code")?.jsonPrimitive?.intOrNull,
        message = e?.get("message")?.jsonPrimitive?.contentOrNull,
        scopes = runCatching { e?.get("required_scopes")?.jsonArray?.map { it.jsonPrimitive.content } }
            .getOrNull().orEmpty(),
    )
}

private fun describe(status: Int, body: String): String {
    val e = parseErr(body)
    return when (e.code) {
        // 87: 権限不足。アプリ更新で必要な権限が増えた直後に起きやすいので、再ログインを案内する
        87 -> buildString {
            append("権限が足りません")
            if (e.scopes.isNotEmpty()) append("（必要: ${e.scopes.joinToString(", ")}）")
            append("。アプリを更新した直後なら、一度ログアウトして再ログインすると新しい権限を許可できます。")
        }
        88 -> "リクエストが多すぎます。しばらく待ってからもう一度お試しください。"
        else -> e.message ?: "HTTP $status"
    }
}

class ApiException(val status: Int, val body: String) : Exception(describe(status, body)) {
    /** 権限不足（code 87）。画面側で「再ログイン」ボタンを出すのに使う */
    val isPermission: Boolean = parseErr(body).code == 87
}

class NorthApi(private val auth: AuthRepository, private val http: OkHttpClient) {
    private val json = Json { ignoreUnknownKeys = true }
    private val jsonMedia = "application/json; charset=utf-8".toMediaType()
    private val bodyMethods = setOf("POST", "PUT", "PATCH")

    private class Resp(val code: Int, val text: String)

    private suspend fun send(
        method: String,
        path: String,
        body: String? = null,
        idempotent: Boolean = false,
        multipart: RequestBody? = null,
        extraHeaders: Map<String, String> = emptyMap(),
    ): String = withContext(Dispatchers.IO) {
        val key = if (idempotent) UUID.randomUUID().toString() else null

        fun exec(token: String): Resp {
            val reqBody: RequestBody? = multipart
                ?: body?.toRequestBody(jsonMedia)
                ?: if (method in bodyMethods) "".toRequestBody(null) else null
            val req = Request.Builder()
                .url(Config.API_BASE + path)
                .header("Authorization", "Bearer $token")
                .apply {
                    if (key != null) header("Idempotency-Key", key)
                    extraHeaders.forEach { (k, v) -> header(k, v) }
                }
                .method(method, reqBody)
                .build()
            return http.newCall(req).execute().use { Resp(it.code, it.body?.string().orEmpty()) }
        }

        var token = auth.accessToken()
        var r = exec(token)
        if (r.code == 401) { // 失効していたら1回だけ更新して再試行（同じIdempotency-Keyで安全に）
            token = auth.refreshAfter(token)
            r = exec(token)
        }
        if (r.code !in 200..299) throw ApiException(r.code, r.text)
        r.text
    }

    suspend fun me(): User =
        json.decodeFromString<Envelope<User>>(send("GET", "/2/users/me")).data

    /** ranked=true で「おすすめ」の並び。cursorは加工せずそのまま渡す */
    suspend fun homeTimeline(cursor: String? = null, ranked: Boolean = false): PostPage {
        val params = buildList {
            if (ranked) add("ranked=1")
            if (cursor != null) add("cursor=" + URLEncoder.encode(cursor, "UTF-8"))
        }
        val q = if (params.isEmpty()) "" else "?" + params.joinToString("&")
        return json.decodeFromString<Envelope<PostPage>>(send("GET", "/2/timelines/home$q")).data
    }

    /** q は north の検索窓と同じ書き方（from:ユーザー名 / #ハッシュタグ など）。tab: latest | top */
    suspend fun search(q: String, tab: String = "latest", cursor: String? = null): PostPage {
        val params = buildList {
            add("q=" + URLEncoder.encode(q, "UTF-8"))
            add("tab=$tab")
            if (cursor != null) add("cursor=" + URLEncoder.encode(cursor, "UTF-8"))
        }
        return json.decodeFromString<Envelope<PostPage>>(
            send("GET", "/2/tweets/search?" + params.joinToString("&"))
        ).data
    }

    suspend fun trends(): List<Trend> =
        json.decodeFromString<Envelope<TrendPage>>(send("GET", "/2/trends")).data.items

    suspend fun createPost(
        text: String,
        mediaIds: List<String> = emptyList(),
        replyToId: String? = null,
        quoteId: String? = null,
        poll: PollInput? = null,
    ): CreatedPost {
        val body = buildJsonObject {
            put("text", text)
            if (mediaIds.isNotEmpty()) {
                putJsonObject("media") { putJsonArray("media_ids") { mediaIds.forEach { add(it) } } }
            }
            if (replyToId != null) {
                putJsonObject("reply") { put("in_reply_to_tweet_id", replyToId) }
            }
            if (quoteId != null) put("quote_tweet_id", quoteId)
            if (poll != null) putJsonObject("poll") {
                putJsonArray("options") { poll.options.forEach { add(it) } }
                put("durationMinutes", poll.durationMinutes)
            }
        }.toString()
        return json.decodeFromString<Envelope<CreatedPost>>(
            send("POST", "/2/tweets", body, idempotent = true)
        ).data
    }

    suspend fun drafts(): List<Draft> =
        json.decodeFromString<Envelope<DraftPage>>(send("GET", "/2/drafts")).data.items

    suspend fun createDraft(text: String, mediaIds: List<String> = emptyList()): Draft {
        val body = buildJsonObject {
            putJsonArray("items") {
                add(buildJsonObject {
                    put("text", text)
                    if (mediaIds.isNotEmpty()) putJsonArray("mediaIds") { mediaIds.forEach { add(it) } }
                })
            }
        }.toString()
        return json.decodeFromString<Envelope<Draft>>(send("POST", "/2/drafts", body, idempotent = true)).data
    }

    /** 画像を1枚アップロードして Media を返す（返ったidを createPost の mediaIds に渡す） */
    suspend fun uploadMedia(resolver: ContentResolver, uri: Uri, purpose: String = "posts"): Media {
        val mime = resolver.getType(uri) ?: "application/octet-stream"
        val name = resolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)
            ?.use { c -> if (c.moveToFirst()) c.getString(0) else null } ?: "image"
        val bytes = withContext(Dispatchers.IO) {
            resolver.openInputStream(uri)?.use { it.readBytes() }
        } ?: throw IOException("画像を読み込めませんでした")

        val form = MultipartBody.Builder()
            .setType(MultipartBody.FORM)
            .addFormDataPart("file", name, bytes.toRequestBody(mime.toMediaType()))
            .build()
        val text = send(
            "POST", "/2/media/upload",
            multipart = form,
            extraHeaders = mapOf("X-North-Media-Purpose" to purpose),
        )
        // OAuthは { "data": {...} } で包まれる。念のため包まれていない形も受ける
        return runCatching { json.decodeFromString<Envelope<Media>>(text).data }
            .getOrElse { json.decodeFromString<Media>(text) }
    }

    // ---------------- ポスト詳細・プロフィール ----------------
    private fun cursorQuery(cursor: String?) =
        if (cursor != null) "?cursor=" + URLEncoder.encode(cursor, "UTF-8") else ""

    /** 祖先・本人・返信。続きの返信は cursor で取る */
    suspend fun conversation(id: String, cursor: String? = null): Conversation =
        json.decodeFromString<Envelope<Conversation>>(
            send("GET", "/2/tweets/$id/conversation" + cursorQuery(cursor))
        ).data

    suspend fun user(handle: String): User =
        json.decodeFromString<Envelope<User>>(send("GET", "/2/users/${URLEncoder.encode(handle, "UTF-8")}")).data

    /** 本人の表示名・自己紹介・プロフィール画像を更新する（profile.write が必要）。 */
    suspend fun updateProfile(
        name: String,
        bio: String?,
        location: String?,
        website: String?,
        avatarMediaId: String? = null,
        headerMediaId: String? = null,
    ): User {
        val body = buildJsonObject {
            put("name", name)
            if (bio == null) put("bio", JsonNull) else put("bio", bio)
            if (location == null) put("location", JsonNull) else put("location", location)
            if (website == null) put("website", JsonNull) else put("website", website)
            if (avatarMediaId != null) put("avatarMediaId", avatarMediaId)
            if (headerMediaId != null) put("headerMediaId", headerMediaId)
        }.toString()
        return json.decodeFromString<Envelope<User>>(
            send("PATCH", "/2/users/me", body, idempotent = true)
        ).data
    }

    suspend fun userPosts(handle: String, cursor: String? = null): PostPage =
        json.decodeFromString<Envelope<PostPage>>(
            send("GET", "/2/users/${URLEncoder.encode(handle, "UTF-8")}/tweets" + cursorQuery(cursor))
        ).data

    suspend fun userLikes(handle: String, cursor: String? = null): PostPage =
        json.decodeFromString<Envelope<PostPage>>(
            send("GET", "/2/users/${URLEncoder.encode(handle, "UTF-8")}/liked_tweets" + cursorQuery(cursor))
        ).data

    suspend fun quotes(id: String, cursor: String? = null): PostPage =
        json.decodeFromString<Envelope<PostPage>>(send("GET", "/2/tweets/$id/quotes" + cursorQuery(cursor))).data

    suspend fun retweets(id: String, cursor: String? = null): UserPage =
        json.decodeFromString<Envelope<UserPage>>(send("GET", "/2/tweets/$id/retweets" + cursorQuery(cursor))).data

    suspend fun bookmarks(cursor: String? = null): PostPage =
        json.decodeFromString<Envelope<PostPage>>(send("GET", "/2/bookmarks" + cursorQuery(cursor))).data

    suspend fun removeBookmark(id: String) {
        send("DELETE", "/2/tweets/$id/bookmark")
    }
    suspend fun addBookmark(id: String) { send("POST", "/2/tweets/$id/bookmark", idempotent = true) }
    suspend fun bookmarkFolders(): List<BookmarkFolder> =
        json.decodeFromString<Envelope<BookmarkFolderPage>>(send("GET", "/2/bookmark-folders")).data.items
    suspend fun addToBookmarkFolder(folderId: String, tweetId: String) {
        send("PUT", "/2/bookmark-folders/$folderId/tweets/$tweetId", idempotent = true)
    }

    suspend fun lists(): NorthLists = json.decodeFromString<Envelope<NorthLists>>(send("GET", "/2/lists")).data
    suspend fun createList(name: String, description: String?, isPrivate: Boolean): NorthList {
        val body = buildJsonObject { put("name", name); put("private", isPrivate); if (description != null) put("description", description) }.toString()
        return json.decodeFromString<Envelope<NorthList>>(send("POST", "/2/lists", body, idempotent = true)).data
    }
    suspend fun followList(id: String) { send("POST", "/2/lists/$id/follow", idempotent = true) }
    suspend fun leaveList(id: String) { send("DELETE", "/2/lists/$id/follow") }

    suspend fun userFollowers(handle: String, cursor: String? = null): UserPage =
        json.decodeFromString<Envelope<UserPage>>(
            send("GET", "/2/users/${URLEncoder.encode(handle, "UTF-8")}/followers" + cursorQuery(cursor))
        ).data

    suspend fun userFollowing(handle: String, cursor: String? = null): UserPage =
        json.decodeFromString<Envelope<UserPage>>(
            send("GET", "/2/users/${URLEncoder.encode(handle, "UTF-8")}/following" + cursorQuery(cursor))
        ).data

    suspend fun postLikes(id: String, cursor: String? = null): UserPage =
        json.decodeFromString<Envelope<UserPage>>(send("GET", "/2/tweets/$id/likes" + cursorQuery(cursor))).data

    suspend fun follow(handle: String) {
        send("POST", "/2/users/${URLEncoder.encode(handle, "UTF-8")}/follow", idempotent = true)
    }

    suspend fun unfollow(handle: String) {
        send("DELETE", "/2/users/${URLEncoder.encode(handle, "UTF-8")}/follow")
    }

    // ---------------- 通知 ----------------
    suspend fun notifications(cursor: String? = null): NotificationPage {
        val q = if (cursor != null) "?cursor=" + URLEncoder.encode(cursor, "UTF-8") else ""
        return json.decodeFromString<Envelope<NotificationPage>>(send("GET", "/2/notifications$q")).data
    }

    suspend fun unreadCount(): Int =
        json.decodeFromString<Envelope<UnreadCount>>(send("GET", "/2/notifications/unread_count")).data.count

    /** 未読をすべて既読にする（notifications.write が必要） */
    suspend fun markNotificationsRead(): Int =
        json.decodeFromString<Envelope<MarkedRead>>(
            send("POST", "/2/notifications/read", idempotent = true)
        ).data.marked

    /**
     * 新着通知のSSE。イベント(notification)が届くたびに Unit を流す。本文は入っていないので、
     * 受け取った側で一覧/未読数を取り直す。切断時は5秒〜最大60秒で再接続する
     * （切断中のイベントは再送されないので、再接続後も呼び出し側で取り直すこと）。
     * 権限不足(403)などの再試行しても直らないエラーは、再接続せずに投げる。
     */
    fun notificationEvents(): Flow<Unit> = channelFlow {
        // 25秒ごとのハートビートがあるので、70秒無音なら切れているとみなす
        val sse = http.newBuilder().readTimeout(70, TimeUnit.SECONDS).build()
        var backoff = 5_000L
        while (isActive) {
            var token: String? = null
            try {
                val t = auth.accessToken()
                token = t
                val req = Request.Builder()
                    .url(Config.API_BASE + "/2/notifications/stream")
                    .header("Authorization", "Bearer $t")
                    .header("Accept", "text/event-stream")
                    .build()
                val call = sse.newCall(req)
                coroutineScope {
                    // コルーチンがキャンセルされたら、ブロック中の読み取りを中断する
                    val watcher = launch { try { awaitCancellation() } finally { call.cancel() } }
                    try {
                        withContext(Dispatchers.IO) {
                            call.execute().use { resp ->
                                if (!resp.isSuccessful) throw ApiException(resp.code, resp.body?.string().orEmpty())
                                backoff = 5_000L
                                val src = resp.body!!.source()
                                var event: String? = null
                                while (true) {
                                    val line = src.readUtf8Line() ?: break // 切断
                                    when {
                                        line.isEmpty() -> { // 空行でイベント確定
                                            if (event == "notification") trySend(Unit)
                                            event = null
                                        }
                                        line.startsWith("event:") -> event = line.substring(6).trim()
                                    }
                                }
                            }
                        }
                    } finally {
                        watcher.cancel()
                    }
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: NotLoggedInException) {
                throw e
            } catch (e: ApiException) {
                if (e.status == 401 && token != null) { // トークン失効 → 更新してすぐ繋ぎ直す
                    auth.refreshAfter(token)
                    continue
                }
                if (e.status in 400..499 && e.status != 429) throw e
            } catch (e: Exception) {
                // 通信が切れた等。下で待ってから再接続する
            }
            delay(backoff)
            backoff = (backoff * 2).coerceAtMost(60_000L)
        }
    }

    suspend fun like(id: String) { send("POST", "/2/tweets/$id/like", idempotent = true) }
    suspend fun unlike(id: String) { send("DELETE", "/2/tweets/$id/like") }
    suspend fun retweet(id: String) { send("POST", "/2/tweets/$id/retweet", idempotent = true) }
    suspend fun unretweet(id: String) { send("DELETE", "/2/tweets/$id/retweet") }
}
