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
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.MultipartBody
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody
import okhttp3.RequestBody.Companion.toRequestBody
import okio.BufferedSink
import java.io.IOException
import java.net.URLEncoder
import java.util.UUID
import java.util.concurrent.TimeUnit

private class ErrInfo(val code: Int?, val message: String?, val scopes: List<String>)

private const val MAX_UPLOAD_BYTES = 50L * 1024 * 1024
private val SUPPORTED_VIDEO_TYPES = setOf("video/mp4", "video/quicktime", "video/webm")

/** Content URIを全量メモリへ展開せず、上限を確認しながらOkHttpへ流す。 */
private class ContentUriRequestBody(
    private val resolver: ContentResolver,
    private val uri: Uri,
    private val mediaType: okhttp3.MediaType,
    private val declaredLength: Long,
) : RequestBody() {
    override fun contentType() = mediaType
    override fun contentLength(): Long = declaredLength.takeIf { it >= 0 } ?: -1L

    override fun writeTo(sink: BufferedSink) {
        resolver.openInputStream(uri)?.use { input ->
            val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
            var total = 0L
            while (true) {
                val count = input.read(buffer)
                if (count < 0) break
                total += count
                if (total > MAX_UPLOAD_BYTES) throw IOException("画像・動画は50MB以下を選択してください")
                sink.write(buffer, 0, count)
            }
        } ?: throw IOException("メディアを読み込めませんでした")
    }
}

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

        var token = auth.authToken()
        var r = exec(token.value)
        if (r.code == 401) { // 失効していたら1回だけ更新して再試行（同じIdempotency-Keyで安全に）
            token = auth.refreshAfter(token)
            r = exec(token.value)
        }
        if (r.code !in 200..299) throw ApiException(r.code, r.text)
        r.text
    }

    suspend fun me(): User =
        json.decodeFromString<Envelope<User>>(send("GET", "/2/users/me")).data

    suspend fun dmConversations(requests: Boolean, cursor: String? = null): DmConversationPage =
        json.decodeFromString<Envelope<DmConversationPage>>(send("GET",
            "/2/dm/conversations?requests=$requests" +
                (cursor?.let { "&cursor=" + URLEncoder.encode(it, "UTF-8") } ?: ""))).data

    suspend fun createDm(handle: String): DmConversation =
        json.decodeFromString<Envelope<DmConversation>>(send("POST", "/2/dm/conversations",
            buildJsonObject { put("handle", handle) }.toString(), idempotent = true)).data

    suspend fun dmMessages(id: String, cursor: String? = null): DmMessagePage =
        json.decodeFromString<Envelope<DmMessagePage>>(send("GET",
            "/2/dm/conversations/${URLEncoder.encode(id, "UTF-8")}/messages" + cursorQuery(cursor))).data

    suspend fun sendDm(id: String, text: String, mediaId: String?, key: String): DmMessage =
        json.decodeFromString<Envelope<DmMessage>>(send("POST",
            "/2/dm/conversations/${URLEncoder.encode(id, "UTF-8")}/messages",
            buildJsonObject {
                put("text", text)
                if (mediaId != null) putJsonArray("mediaIds") { add(mediaId) }
            }.toString(), extraHeaders = mapOf("Idempotency-Key" to key))).data

    suspend fun markDmRead(id: String) {
        send("POST", "/2/dm/conversations/${URLEncoder.encode(id, "UTF-8")}/read", idempotent = true)
    }

    suspend fun acceptDm(id: String) {
        send("POST", "/2/dm/conversations/${URLEncoder.encode(id, "UTF-8")}/accept", idempotent = true)
    }

    suspend fun rejectDm(id: String) {
        send("DELETE", "/2/dm/conversations/${URLEncoder.encode(id, "UTF-8")}/request")
    }

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

    /** 画像または動画を1件アップロードして Media を返す（返ったidを createPost の mediaIds に渡す） */
    suspend fun uploadMedia(resolver: ContentResolver, uri: Uri, purpose: String = "posts"): Media {
        val mime = resolver.getType(uri) ?: "application/octet-stream"
        if (mime.startsWith("video/") && mime !in SUPPORTED_VIDEO_TYPES) {
            throw IOException("動画はMP4、MOV、WebM形式を選択してください")
        }
        var declaredLength = -1L
        val name = resolver.query(
            uri,
            arrayOf(OpenableColumns.DISPLAY_NAME, OpenableColumns.SIZE),
            null,
            null,
            null,
        )?.use { c ->
            if (!c.moveToFirst()) return@use null
            val sizeIndex = c.getColumnIndex(OpenableColumns.SIZE)
            if (sizeIndex >= 0 && !c.isNull(sizeIndex)) declaredLength = c.getLong(sizeIndex)
            val nameIndex = c.getColumnIndex(OpenableColumns.DISPLAY_NAME)
            if (nameIndex >= 0 && !c.isNull(nameIndex)) c.getString(nameIndex) else null
        } ?: "media"
        if (declaredLength > MAX_UPLOAD_BYTES) throw IOException("画像・動画は50MB以下を選択してください")
        val mediaType = mime.toMediaTypeOrNull() ?: "application/octet-stream".toMediaType()

        val form = MultipartBody.Builder()
            .setType(MultipartBody.FORM)
            .addFormDataPart("file", name, ContentUriRequestBody(resolver, uri, mediaType, declaredLength))
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

    /** 動画の変換状況を取得する。 */
    suspend fun mediaStatus(id: String): Media {
        val text = send("GET", "/2/media/$id")
        return runCatching { json.decodeFromString<Envelope<Media>>(text).data }
            .getOrElse { json.decodeFromString<Media>(text) }
    }

    /** 動画はサーバー側の変換完了を待ってから投稿へ添付する。 */
    suspend fun awaitMediaReady(uploaded: Media): Media {
        var media = uploaded
        if (media.status.equals("FAILED", ignoreCase = true)) {
            throw IOException(media.errorMessage ?: "動画の処理に失敗しました")
        }
        if (!media.status.equals("PENDING", ignoreCase = true)) return media

        repeat(200) { // Web版と同じく最大約5分待つ
            delay(1_500)
            media = mediaStatus(uploaded.id)
            when {
                media.status.equals("READY", ignoreCase = true) -> return media
                media.status.equals("FAILED", ignoreCase = true) ->
                    throw IOException(media.errorMessage ?: "動画の処理に失敗しました")
            }
        }
        throw IOException("動画の処理に時間がかかっています。しばらくしてからもう一度お試しください")
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

    /** 自分のプロフィールにポストを固定する（posts.write が必要）。 */
    suspend fun pinPost(id: String) {
        val body = buildJsonObject { put("tweetId", id) }.toString()
        send("PUT", "/2/users/me/pinned", body, idempotent = true)
    }

    suspend fun unpinPost() { send("DELETE", "/2/users/me/pinned") }

    suspend fun setReplyPolicy(id: String, policy: String) {
        val body = buildJsonObject { put("replyPolicy", policy) }.toString()
        send("PATCH", "/2/tweets/$id", body, idempotent = true)
    }

    suspend fun deletePost(id: String) { send("DELETE", "/2/tweets/$id") }

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

    /** フォロー中ユーザーの新規ポストを、本人宛ての POST 通知として受け取る。 */
    suspend fun enableAccountNotifications(handle: String) {
        send("POST", "/2/users/${URLEncoder.encode(handle, "UTF-8")}/account-notifications", idempotent = true)
    }

    suspend fun disableAccountNotifications(handle: String) {
        send("DELETE", "/2/users/${URLEncoder.encode(handle, "UTF-8")}/account-notifications")
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
            var token: AuthToken? = null
            try {
                val t = auth.authToken()
                token = t
                val req = Request.Builder()
                    .url(Config.API_BASE + "/2/notifications/stream")
                    .header("Authorization", "Bearer ${t.value}")
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
