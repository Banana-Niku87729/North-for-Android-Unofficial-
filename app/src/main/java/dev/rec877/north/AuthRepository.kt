package dev.rec877.north

import android.net.Uri
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import okhttp3.FormBody
import okhttp3.OkHttpClient
import okhttp3.Request

@Serializable data class Tokens(val accessToken: String, val refreshToken: String, val expiresAtMs: Long)
/** 端末に追加済みのアカウント。トークン本体は別の暗号化領域に保存する。 */
@Serializable data class SavedAccount(
    val id: String,
    val handle: String,
    val name: String,
    val avatarUrl: String? = null,
)

@Serializable private data class Pending(
    val verifier: String,
    val state: String,
    /** 既存ログインを置き換える通常ログインか、追加ログインかを復元後も判別する。 */
    val additional: Boolean = false,
)

class NotLoggedInException : Exception("ログインが必要です")
class AuthException(message: String) : Exception(message)

class AuthRepository(private val secrets: SecretStore, private val http: OkHttpClient) {
    private val json = Json { ignoreUnknownKeys = true }

    /**
     * 更新トークンはローテーション式で、使用済みの再利用を検出すると許可全体が止まる。
     * 読み出し→更新→保存を必ずこのMutexの中で行い、同時更新を防ぐ。
     */
    private val mutex = Mutex()

    fun isLoggedIn(): Boolean = loadTokens() != null

    fun accounts(): List<SavedAccount> =
        secrets.get(K_ACCOUNTS)?.let { runCatching { json.decodeFromString<List<SavedAccount>>(it) }.getOrNull() }
            .orEmpty()

    fun activeAccountId(): String? = secrets.get(K_ACTIVE_ACCOUNT)

    // ---------- ログイン開始 ----------
    fun buildAuthorizeUri(): Uri = buildAuthorizeUri(additional = false)

    /** ブラウザで切り替えた north のアカウントを、この端末へ追加するための認可URL。 */
    fun buildAdditionalAccountAuthorizeUri(): Uri = buildAuthorizeUri(additional = true)

    private fun buildAuthorizeUri(additional: Boolean): Uri {
        val verifier = Pkce.randomString()
        val state = Pkce.randomString(16)
        // ブラウザに行っている間にプロセスが死んでも続けられるよう、永続化しておく
        secrets.put(K_PENDING, json.encodeToString(Pending(verifier, state, additional)))
        return Uri.parse("${Config.API_BASE}/oauth/authorize").buildUpon()
            .appendQueryParameter("client_id", Config.CLIENT_ID)
            .appendQueryParameter("response_type", "code")
            .appendQueryParameter("redirect_uri", Config.REDIRECT_URI)
            .appendQueryParameter("scope", Config.SCOPES)
            .appendQueryParameter("state", state)
            .appendQueryParameter("code_challenge", Pkce.challenge(verifier))
            .appendQueryParameter("code_challenge_method", "S256")
            .build()
    }

    // ---------- リダイレクト受け取り（認可codeは60秒・1回限り。すぐ交換する）----------
    suspend fun handleRedirect(uri: Uri) {
        val pending = secrets.get(K_PENDING)?.let { json.decodeFromString<Pending>(it) }
            ?: throw AuthException("ログイン要求が見つかりません。もう一度お試しください")
        secrets.remove(K_PENDING)

        uri.getQueryParameter("error")?.let { throw AuthException("認可が拒否されました: $it") }
        if (uri.getQueryParameter("state") != pending.state) throw AuthException("stateが一致しません")
        val code = uri.getQueryParameter("code") ?: throw AuthException("codeがありません")

        val (status, text) = postForm(
            "/oauth/token",
            mapOf(
                "grant_type" to "authorization_code",
                "client_id" to Config.CLIENT_ID,
                "code" to code,
                "code_verifier" to pending.verifier,
                "redirect_uri" to Config.REDIRECT_URI,
            )
        )
        val tokens = parseTokens(status, text)
        mutex.withLock {
            // /me で本人を確定するまで、既存のアカウント用トークンを上書きしない。
            // 追加ログイン中にもアプリが落ちても続けられるよう暗号化ストアに残す。
            secrets.put(K_PENDING_TOKENS, json.encodeToString(tokens))
        }
    }

    /** /me の結果を受けてトークンをアカウント単位の領域へ確定する。旧版の単一トークンもここで移行する。 */
    suspend fun registerAuthenticatedAccount(user: User) = mutex.withLock {
        if (user.id.isBlank()) return@withLock
        val tokens = loadPendingTokens() ?: loadTokens() ?: return@withLock
        saveTokensFor(user.id, tokens)
        val item = SavedAccount(user.id, user.handle, user.name, user.avatarUrl)
        val merged = (accounts().filterNot { it.id == item.id } + item)
        secrets.put(K_ACCOUNTS, json.encodeToString(merged))
        secrets.put(K_ACTIVE_ACCOUNT, user.id)
        secrets.remove(K_PENDING_TOKENS)
        // 1アカウント版からの移行が終わったら、平文キー名の旧領域は使わない。
        secrets.remove(K_TOKENS)
    }

    /** 指定アカウントをアクティブにする。トークンがない壊れたエントリは切り替えない。 */
    fun switchAccount(id: String): Boolean {
        if (id.isBlank() || secrets.get(tokenKey(id)) == null) return false
        secrets.put(K_ACTIVE_ACCOUNT, id)
        return true
    }

    /** サーバーの許可は取り消さず、この端末に保存したアカウントだけを外す。 */
    fun removeAccount(id: String): Boolean {
        if (id.isBlank()) return isLoggedIn()
        secrets.remove(tokenKey(id))
        val remaining = accounts().filterNot { it.id == id }
        if (remaining.isEmpty()) {
            secrets.remove(K_ACCOUNTS)
            secrets.remove(K_ACTIVE_ACCOUNT)
        } else {
            secrets.put(K_ACCOUNTS, json.encodeToString(remaining))
            if (activeAccountId() == id) secrets.put(K_ACTIVE_ACCOUNT, remaining.first().id)
        }
        return isLoggedIn()
    }

    // ---------- アクセストークン取得（期限が近ければ更新）----------
    suspend fun accessToken(): String = mutex.withLock {
        val t = loadTokens() ?: throw NotLoggedInException()
        if (System.currentTimeMillis() < t.expiresAtMs - 60_000) t.accessToken else refreshLocked(t).accessToken
    }

    /** 401が返った時用。他の呼び出しが既に更新済みなら、更新せずその値を返す */
    suspend fun refreshAfter(failedAccessToken: String): String = mutex.withLock {
        val t = loadTokens() ?: throw NotLoggedInException()
        if (t.accessToken != failedAccessToken) t.accessToken else refreshLocked(t).accessToken
    }

    suspend fun logout() {
        val t = loadTokens()
        if (t != null) {
            // 「このアプリの許可全体を取り消す」。tokenにどちらを渡すかは仕様に明記がないので要確認
            runCatching {
                postForm("/oauth/revoke", mapOf("client_id" to Config.CLIENT_ID, "token" to t.refreshToken))
            }
        }
        val id = activeAccountId()
        if (id != null) {
            removeAccount(id)
        } else {
            secrets.remove(K_TOKENS)
        }
    }

    // ---------- 内部 ----------
    private suspend fun refreshLocked(t: Tokens): Tokens {
        val (status, text) = postForm(
            "/oauth/token",
            mapOf(
                "grant_type" to "refresh_token",
                "client_id" to Config.CLIENT_ID,
                "refresh_token" to t.refreshToken,
            )
        )
        if (status == 400 && text.contains("invalid_grant")) {
            removeCurrentTokens() // 期限切れ・取り消し済み → 再ログインが必要
            throw NotLoggedInException()
        }
        // ネットワーク例外はそのまま投げる（トークンは消さない）
        val fresh = parseTokens(status, text)
        saveTokens(fresh)
        return fresh
    }

    private fun parseTokens(status: Int, text: String): Tokens {
        if (status != 200) {
            val err = runCatching { json.decodeFromString<OAuthError>(text).error }.getOrNull()
            throw AuthException(err ?: "トークン取得に失敗しました (HTTP $status)")
        }
        val r = json.decodeFromString<TokenResponse>(text)
        return Tokens(r.accessToken, r.refreshToken, System.currentTimeMillis() + r.expiresIn * 1000L)
    }

    private fun loadTokens(): Tokens? {
        val raw = secrets.get(K_PENDING_TOKENS)
            ?: activeAccountId()?.let { secrets.get(tokenKey(it)) }
            ?: secrets.get(K_TOKENS)
        return raw?.let { runCatching { json.decodeFromString<Tokens>(it) }.getOrNull() }
    }

    private fun loadPendingTokens(): Tokens? = secrets.get(K_PENDING_TOKENS)
        ?.let { runCatching { json.decodeFromString<Tokens>(it) }.getOrNull() }

    private fun saveTokens(t: Tokens) {
        val active = activeAccountId()
        if (secrets.get(K_PENDING_TOKENS) != null) {
            secrets.put(K_PENDING_TOKENS, json.encodeToString(t))
        } else if (active != null) {
            saveTokensFor(active, t)
        } else {
            secrets.put(K_TOKENS, json.encodeToString(t))
        }
    }

    private fun saveTokensFor(id: String, t: Tokens) = secrets.put(tokenKey(id), json.encodeToString(t))

    private fun removeCurrentTokens() {
        when {
            secrets.get(K_PENDING_TOKENS) != null -> secrets.remove(K_PENDING_TOKENS)
            activeAccountId() != null -> removeAccount(activeAccountId()!!)
            else -> secrets.remove(K_TOKENS)
        }
    }

    private suspend fun postForm(path: String, form: Map<String, String>): Pair<Int, String> =
        withContext(Dispatchers.IO) {
            val body = FormBody.Builder().apply { form.forEach { (k, v) -> add(k, v) } }.build()
            val req = Request.Builder().url(Config.API_BASE + path).post(body).build()
            http.newCall(req).execute().use { it.code to it.body?.string().orEmpty() }
        }

    private companion object {
        const val K_TOKENS = "tokens"
        const val K_PENDING = "pending"
        const val K_PENDING_TOKENS = "pending_account_tokens"
        const val K_ACCOUNTS = "saved_accounts"
        const val K_ACTIVE_ACCOUNT = "active_account"
        fun tokenKey(id: String) = "tokens:$id"
    }
}
