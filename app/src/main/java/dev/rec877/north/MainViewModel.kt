package dev.rec877.north

import android.Manifest
import android.app.Application
import android.content.Context
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.Color
import androidx.core.content.ContextCompat
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient
import dev.rec877.north.ui.theme.NorthColors
import dev.rec877.north.ui.theme.midnightColors
import dev.rec877.north.ui.theme.northColors
import java.util.Locale

data class UiState(
    val loggedIn: Boolean = false,
    val me: User? = null,
    val posts: List<Post> = emptyList(),
    val nextCursor: String? = null,
    /** true=「おすすめ」の並び / false=最新ポスト */
    val ranked: Boolean = false,
    val refreshing: Boolean = false,
    val loadingMore: Boolean = false,
    val posting: Boolean = false,
    val message: String? = null,
    /** 権限不足のエラー時に true。「再ログイン」ボタンを出す */
    val reloginHint: Boolean = false,
)

data class SearchState(
    val input: String = "",
    /** 実際に検索した語句（入力欄とは別。編集中に結果が変わらないようにする） */
    val query: String = "",
    /** latest | top */
    val tab: String = "latest",
    val searched: Boolean = false,
    val results: List<Post> = emptyList(),
    val nextCursor: String? = null,
    val loading: Boolean = false,
    val history: List<String> = emptyList(),
    /** 同じ語句を再検索した場合にも、結果リストを先頭へ戻すための世代番号 */
    val version: Int = 0,
)

data class NotifState(
    val items: List<Notification> = emptyList(),
    val nextCursor: String? = null,
    val refreshing: Boolean = false,
    val loadingMore: Boolean = false,
    val loaded: Boolean = false,
    /** 未読の数（ナビのバッジに出す） */
    val unread: Int = 0,
)

/** ポスト詳細・プロフィールのように、ホームの上に重ねる画面の状態を持つもの */
interface ScreenController {
    /** いいね・リポストなどで一覧側のポストが変わったとき、この画面の中身も揃える */
    fun patch(id: String, f: (Post) -> Post)

    /** 返信が投稿されたとき */
    fun onReplyPosted()

    /** 削除されたポストを保持中の一覧から取り除く。 */
    fun removePost(id: String) = Unit

    /** 自分の固定ポストが変わったとき、開いているプロフィールにも反映する。 */
    fun onPinnedPostChanged(ownerId: String, post: Post?) = Unit
}

class MainViewModel(app: Application) : AndroidViewModel(app) {
    private val nApp = app as NorthApp
    private val http = nApp.http
    private val auth = nApp.auth
    val api: NorthApi = nApp.api

    private val apiHost = Config.API_BASE.toHttpUrl().host
    private val prefs = app.getSharedPreferences("north_prefs", Context.MODE_PRIVATE)

    /**
     * 画像・動画取得用のクライアント。APIホストへのリクエストにだけBearerを付ける
     * （非公開メディアの配信にはBearerが必要。他ホストにはトークンを送らない）。
     */
    val imageClient: OkHttpClient = http.newBuilder().addInterceptor { chain ->
        val req = chain.request()
        if (req.url.host == apiHost) {
            val token = runCatching { runBlocking { auth.accessToken() } }.getOrNull()
            if (token != null) {
                return@addInterceptor chain.proceed(
                    req.newBuilder().header("Authorization", "Bearer $token").build()
                )
            }
        }
        chain.proceed(req)
    }.build()

    var ui by mutableStateOf(UiState(loggedIn = auth.isLoggedIn()))
        private set

    /** 端末に追加済みのアカウント。トークン本体は AuthRepository の暗号化ストアだけに置く。 */
    var accounts by mutableStateOf(auth.accounts())
        private set

    var search by mutableStateOf(SearchState(history = loadHistory()))
        private set

    /** サーバーが部分一致のユーザー検索を提供していないため、取得済みのユーザーを候補にする。 */
    var searchCandidates by mutableStateOf<List<User>>(emptyList())
        private set
    var trends by mutableStateOf<List<Trend>>(emptyList())
        private set
    var trendsLoading by mutableStateOf(false)
        private set

    var notif by mutableStateOf(NotifState())
        private set

    /** スマホへのバックグラウンド通知（約15分おきの確認）を使うか */
    var bgNotif by mutableStateOf(prefs.getBoolean(K_BG_NOTIF, true))
        private set

    /** 参考サイトの「表示」設定。既定値は従来どおり midnight / yellow。 */
    var themeColors by mutableStateOf(loadThemeColors())
        private set
    var dimBackground by mutableStateOf(prefs.getBoolean(K_DIM_BG, false))
        private set
    var selectedTheme by mutableStateOf(prefs.getString(K_THEME, "midnight") ?: "midnight")
        private set
    var fontScale by mutableStateOf(prefs.getFloat(K_FONT_SCALE, 1f))
        private set
    var reduceMotion by mutableStateOf(prefs.getBoolean(K_REDUCE_MOTION, false))
        private set
    var highContrast by mutableStateOf(prefs.getBoolean(K_HIGH_CONTRAST, false))
        private set
    /** 端末のナビゲーション領域との重なりを避けるための、投稿FABのローカル配置設定。 */
    var composeButtonOnLeft by mutableStateOf(prefs.getBoolean(K_COMPOSE_ON_LEFT, false))
        private set
    var composeButtonBottomOffset by mutableStateOf(prefs.getFloat(K_COMPOSE_BOTTOM_OFFSET, 132f))
        private set
    var composeButtonDragX by mutableStateOf(prefs.getFloat(K_COMPOSE_DRAG_X, 0f))
        private set
    var composeButtonDragY by mutableStateOf(prefs.getFloat(K_COMPOSE_DRAG_Y, 0f))
        private set
    var composeButtonCustomizationMode by mutableStateOf(false)
        private set
    var availableUpdate by mutableStateOf<AppUpdate?>(null)
        private set
    var releaseUpdates by mutableStateOf<List<AppUpdate>>(emptyList())
        private set
    var updatesLoading by mutableStateOf(false)
        private set
    var drafts by mutableStateOf<List<Draft>>(emptyList())
        private set
    var draftsLoading by mutableStateOf(false)
        private set
    var bookmarkFolders by mutableStateOf<List<BookmarkFolder>>(emptyList())
        private set

    /** ホームの上に重ねている画面。"post:ID" / "user:ユーザー名" */
    val backStack = mutableStateListOf<String>()
    private val screens = HashMap<String, ScreenController>()

    /** 通知タブを開いている間だけ true。新着イベントで一覧も取り直すかの判断に使う */
    private var notifVisible = false

    private var searchJob: Job? = null
    private var candidateJob: Job? = null
    private val knownUsers = LinkedHashMap<String, User>()

    init {
        if (ui.loggedIn) {
            refresh()
            loadTrends()
            if (bgNotif) BackgroundNotifications.enable(app)
        }
        checkForUpdates()
    }

    fun authorizeUri(): Uri = auth.buildAuthorizeUri()
    fun addAccountAuthorizeUri(): Uri = auth.buildAdditionalAccountAuthorizeUri()
    fun accountSwitchUri(): Uri = Uri.parse("https://north.rip/account/switch")
    fun activeAccountId(): String? = auth.activeAccountId()

    fun dismissMessage() { ui = ui.copy(message = null, reloginHint = false) }

    fun onRedirect(uri: Uri) = launchSafely {
        auth.handleRedirect(uri)
        ui = ui.copy(loggedIn = true, message = null, reloginHint = false)
        if (bgNotif) BackgroundNotifications.enable(getApplication())
        refresh()
        loadTrends()
    }

    /** アカウント切替時は画面キャッシュや検索結果を引き継がず、新しいアカウントの状態を読み直す。 */
    fun switchAccount(id: String) {
        if (!auth.switchAccount(id)) return
        accounts = auth.accounts()
        searchJob?.cancel()
        candidateJob?.cancel()
        popToRoot()
        search = SearchState(history = loadHistory())
        notif = NotifState()
        ui = UiState(loggedIn = true)
        refresh()
        loadTrends()
        if (bgNotif) BackgroundNotifications.enable(getApplication())
    }

    /** サーバー側の連携は維持したまま、この端末だけからアカウントを取り外す。 */
    fun removeAccountFromDevice(id: String) {
        val wasActive = auth.activeAccountId() == id
        val stillLoggedIn = auth.removeAccount(id)
        accounts = auth.accounts()
        if (!wasActive) return
        popToRoot()
        ui = UiState(loggedIn = stillLoggedIn)
        search = SearchState(history = loadHistory())
        notif = NotifState()
        if (stillLoggedIn) {
            refresh()
            loadTrends()
        } else {
            BackgroundNotifications.disable(getApplication())
        }
    }

    // ---------------- 画面遷移 ----------------
    fun open(dest: String) {
        if (dest.isBlank() || backStack.lastOrNull() == dest) return
        backStack.add(dest)
    }

    /** 返信ボタンから開いたとき、詳細画面の下の入力欄にカーソルを置くための印（対象ポストのID） */
    var replyFocusPostId by mutableStateOf<String?>(null)
        private set

    fun consumeReplyFocus() { replyFocusPostId = null }

    fun openPost(id: String, focusReply: Boolean = false) {
        if (id.isBlank()) return
        if (focusReply) replyFocusPostId = id
        open(Dest.post(id))
    }
    fun openUser(handle: String) { if (handle.isNotBlank()) open(Dest.user(handle)) }

    fun back() {
        backStack.removeLastOrNull()?.let { screens.remove(it) }
    }

    fun popToRoot() {
        backStack.clear()
        screens.clear()
    }

    /** 画面ごとの状態（読み込み済みの中身やスクロール位置）を、戻ってきたときのために保持する */
    @Suppress("UNCHECKED_CAST")
    fun <T : ScreenController> screen(key: String, create: () -> T): T =
        screens.getOrPut(key, create) as T

    // ---------------- スマホ通知 ----------------
    fun updateBgNotif(on: Boolean) {
        bgNotif = on
        prefs.edit().putBoolean(K_BG_NOTIF, on).apply()
        if (on) BackgroundNotifications.enable(getApplication()) else BackgroundNotifications.disable(getApplication())
    }

    /** ベル購読の現在値を端末にも控え、プロフィールへ即時反映する。 */
    private fun accountNotificationKey(handle: String): String =
        K_ACCOUNT_NOTIFICATION_PREFIX + (auth.activeAccountId() ?: "none") + ":" + handle.lowercase(Locale.ROOT)

    private fun legacyAccountNotificationKey(handle: String): String =
        K_ACCOUNT_NOTIFICATION_PREFIX + handle.lowercase(Locale.ROOT)

    fun isAccountNotificationEnabled(handle: String): Boolean {
        val scopedKey = accountNotificationKey(handle)
        if (prefs.contains(scopedKey)) return prefs.getBoolean(scopedKey, false)

        // 旧版はアカウントIDなしで保存していた。アップデート後も表示状態を失わないよう、
        // 現在ログイン中のアカウント用キーへ一度だけ移行する。
        val legacyKey = legacyAccountNotificationKey(handle)
        if (!prefs.contains(legacyKey)) return false
        val enabled = prefs.getBoolean(legacyKey, false)
        if (auth.activeAccountId() != null) {
            prefs.edit().putBoolean(scopedKey, enabled).remove(legacyKey).apply()
        }
        return enabled
    }

    fun setAccountNotifications(handle: String, enabled: Boolean, onError: () -> Unit) {
        val preferenceKey = accountNotificationKey(handle)
        launchSafely(onError = onError) {
            if (enabled) api.enableAccountNotifications(handle) else api.disableAccountNotifications(handle)
            prefs.edit().putBoolean(preferenceKey, enabled).apply()
            ui = ui.copy(message = if (enabled) "@${handle} の新着ポスト通知をオンにしました" else "@${handle} の新着ポスト通知をオフにしました")
        }
    }

    /** Android 13以上: 通知の許可をまだ聞いていなければ true（聞いたことは覚えておく） */
    fun shouldAskNotifPermission(): Boolean {
        if (Build.VERSION.SDK_INT < 33) return false
        if (prefs.getBoolean(K_ASKED_PERM, false)) return false
        prefs.edit().putBoolean(K_ASKED_PERM, true).apply()
        return ContextCompat.checkSelfPermission(
            getApplication(), Manifest.permission.POST_NOTIFICATIONS
        ) != PackageManager.PERMISSION_GRANTED
    }

    // ---------------- 通知（アプリ内）----------------
    /**
     * 画面が見えている間だけ呼ばれる想定（AppShell が repeatOnLifecycle(STARTED) で起動/停止）。
     * 起動時に未読数を取り直し、あとはSSEの新着イベントを待つ。
     */
    suspend fun runNotificationStream() {
        refreshUnread()
        try {
            api.notificationEvents().collect { onNotificationEvent() }
        } catch (e: CancellationException) {
            throw e
        } catch (e: NotLoggedInException) {
            popToRoot()
            ui = UiState(loggedIn = false, message = "もう一度ログインしてください")
        } catch (e: ApiException) {
            // 権限不足(403)など。再ログインの案内を出す
            ui = ui.copy(message = e.message, reloginHint = e.isPermission)
        } catch (e: Exception) {
            // 通信エラーは黙って終了（次に画面が戻ったときに再開する）
        }
    }

    private fun onNotificationEvent() {
        refreshUnread()
        if (notifVisible) refreshNotifications(silent = true)
    }

    fun onNotifTabShown() {
        notifVisible = true
        refreshNotifications(silent = notif.loaded)
    }

    /** 通知を見終わった（別のタブへ移った・別の画面を開いた）ら、自動で既読にしてバッジを消す */
    fun onNotifTabHidden() {
        if (!notifVisible) return
        notifVisible = false
        if (notif.unread > 0 || notif.items.any { !it.read }) markReadQuietly()
    }

    private fun markReadQuietly() {
        // 見た目は先に既読にして、バッジをすぐ消す
        notif = notif.copy(items = notif.items.map { it.copy(read = true) }, unread = 0)
        viewModelScope.launch {
            try {
                api.markNotificationsRead()
            } catch (e: CancellationException) {
                throw e
            } catch (e: NotLoggedInException) {
                popToRoot()
                ui = UiState(loggedIn = false, message = "もう一度ログインしてください")
            } catch (e: ApiException) {
                if (e.isPermission) ui = ui.copy(message = e.message, reloginHint = true)
            } catch (e: Exception) {
            }
            refreshUnread() // 実際の数に合わせ直す（失敗していたらバッジが戻る）
        }
    }

    /** 未読数だけを軽く取る。失敗しても画面は乱さない（権限不足のときだけ案内する） */
    fun refreshUnread() {
        viewModelScope.launch {
            try {
                notif = notif.copy(unread = api.unreadCount())
            } catch (e: CancellationException) {
                throw e
            } catch (e: NotLoggedInException) {
                popToRoot()
                ui = UiState(loggedIn = false, message = "もう一度ログインしてください")
            } catch (e: ApiException) {
                if (e.isPermission) ui = ui.copy(message = e.message, reloginHint = true)
            } catch (e: Exception) {
            }
        }
    }

    fun refreshNotifications(silent: Boolean = false) = launchSafely {
        if (!silent) notif = notif.copy(refreshing = true)
        val page = api.notifications(null)
        notif = notif.copy(
            refreshing = false,
            loaded = true,
            items = page.items.distinctBy { it.id },
            nextCursor = page.nextCursor,
        )
    }

    fun loadMoreNotifications() {
        val cursor = notif.nextCursor ?: return
        if (notif.loadingMore || notif.refreshing) return
        launchSafely {
            notif = notif.copy(loadingMore = true)
            val page = api.notifications(cursor)
            notif = notif.copy(
                loadingMore = false,
                items = (notif.items + page.items).distinctBy { it.id },
                nextCursor = page.nextCursor,
            )
        }
    }

    /** 「すべて既読」ボタン */
    fun markAllRead() = launchSafely {
        api.markNotificationsRead()
        notif = notif.copy(items = notif.items.map { it.copy(read = true) }, unread = 0)
        refreshUnread() // 詳細フィルターで隠れている分は既読にならないので、実際の数を取り直す
    }

    // ---------------- ホーム ----------------
    fun refresh() = launchSafely {
        ui = ui.copy(refreshing = true, message = null, reloginHint = false)
        // アカウント切替・追加直後も、前の表示名を再利用せず必ず認可済みユーザーを取得する。
        val me = api.me()
        auth.registerAuthenticatedAccount(me)
        accounts = auth.accounts()
        val page = api.homeTimeline(null, ui.ranked)
        rememberUsers(listOf(me) + page.items.map { it.author })
        ui = ui.copy(
            refreshing = false,
            me = me,
            posts = page.items.distinctBy { it.id },
            nextCursor = page.nextCursor,
        )
    }

    fun toggleRanked() {
        ui = ui.copy(ranked = !ui.ranked, posts = emptyList(), nextCursor = null)
        refresh()
    }

    fun loadMore() {
        val cursor = ui.nextCursor ?: return
        if (ui.loadingMore || ui.refreshing) return
        launchSafely {
            ui = ui.copy(loadingMore = true)
            val page = api.homeTimeline(cursor, ui.ranked) // nextCursorは加工せずそのまま渡す
            rememberUsers(page.items.map { it.author })
            // LazyColumnのkeyが重複すると落ちるので、IDで重複を除く
            ui = ui.copy(
                loadingMore = false,
                posts = (ui.posts + page.items).distinctBy { it.id },
                nextCursor = page.nextCursor,
            )
        }
    }

    // ---------------- 検索 ----------------
    fun setSearchInput(v: String) {
        search = search.copy(input = v)
        updateSearchCandidates(v)
    }

    fun clearSearch() {
        searchJob?.cancel()
        candidateJob?.cancel()
        searchCandidates = emptyList()
        search = search.copy(
            input = "", query = "", searched = false,
            results = emptyList(), nextCursor = null, loading = false,
        )
    }

    fun submitSearch(raw: String) {
        val q = raw.trim()
        if (q.isEmpty()) return
        val hist = (listOf(q) + search.history.filter { it != q }).take(MAX_HISTORY)
        saveHistory(hist)
        search = search.copy(
            input = q, query = q, searched = true,
            results = emptyList(), nextCursor = null, history = hist, version = search.version + 1,
        )
        searchCandidates = emptyList()
        runSearch(null)
    }

    fun setSearchTab(tab: String) {
        if (tab == search.tab) return
        search = search.copy(tab = tab, results = emptyList(), nextCursor = null, version = search.version + 1)
        if (search.searched) runSearch(null)
    }

    fun loadMoreSearch() {
        val cursor = search.nextCursor ?: return
        if (search.loading) return
        runSearch(cursor)
    }

    fun removeHistory(q: String) {
        val hist = search.history.filter { it != q }
        saveHistory(hist)
        search = search.copy(history = hist)
    }

    fun clearHistory() {
        saveHistory(emptyList())
        search = search.copy(history = emptyList())
    }

    fun loadTrends() {
        if (trendsLoading) return
        launchSafely(onError = { trendsLoading = false }) {
            trendsLoading = true
            trends = api.trends()
            trendsLoading = false
        }
    }

    /** TL・検索・プロフィールで見つけたアカウントを、候補用に端末内で覚える。 */
    fun rememberUsers(users: Iterable<User>) {
        users.forEach { user ->
            if (user.handle.isNotBlank()) knownUsers[user.handle.lowercase()] = user
        }
    }

    private fun updateSearchCandidates(raw: String) {
        candidateJob?.cancel()
        val query = raw.trim().removePrefix("@").lowercase()
        if (query.isBlank()) {
            searchCandidates = emptyList()
            return
        }
        searchCandidates = knownUsers.values.filter {
            it.handle.lowercase().contains(query) || it.name.lowercase().contains(query)
        }.take(6)
        // 完全なハンドル入力なら、キャッシュにないアカウントも確認する。
        candidateJob = launchSafely {
            kotlinx.coroutines.delay(350)
            val exact = runCatching { api.user(query) }.getOrNull() ?: return@launchSafely
            rememberUsers(listOf(exact))
            if (search.input.trim().removePrefix("@").equals(query, ignoreCase = true)) {
                searchCandidates = (listOf(exact) + searchCandidates).distinctBy { it.id }.take(6)
            }
        }
    }

    fun updateMyProfile(
        name: String,
        bio: String?,
        location: String?,
        website: String?,
        avatar: Uri?,
        header: Uri?,
        onDone: () -> Unit,
    ) {
        launchSafely {
            val resolver = getApplication<Application>().contentResolver
            val avatarId = avatar?.let { api.uploadMedia(resolver, it, purpose = "profile").id }
            val headerId = header?.let { api.uploadMedia(resolver, it, purpose = "profile").id }
            val updated = api.updateProfile(name.trim(), bio, location, website, avatarId, headerId)
            rememberUsers(listOf(updated))
            ui = ui.copy(me = updated, message = "プロフィールを更新しました")
            onDone()
        }
    }

    fun updateAccent(argb: Long) {
        prefs.edit().putLong(K_ACCENT, argb).apply()
        themeColors = northColors(selectedTheme, Color(argb))
    }

    fun updateDimBackground(enabled: Boolean) {
        dimBackground = enabled
        prefs.edit().putBoolean(K_DIM_BG, enabled).apply()
        updateTheme(if (enabled) "lightsout" else selectedTheme)
    }

    fun updateTheme(id: String) {
        selectedTheme = id
        dimBackground = id == "lightsout"
        prefs.edit().putString(K_THEME, id).putBoolean(K_DIM_BG, dimBackground).apply()
        themeColors = northColors(id, Color(prefs.getLong(K_ACCENT, DEFAULT_ACCENT)))
    }

    fun updateFontScale(value: Float) {
        fontScale = value
        prefs.edit().putFloat(K_FONT_SCALE, value).apply()
    }

    fun updateReduceMotion(value: Boolean) {
        reduceMotion = value
        prefs.edit().putBoolean(K_REDUCE_MOTION, value).apply()
    }

    fun updateHighContrast(value: Boolean) {
        highContrast = value
        prefs.edit().putBoolean(K_HIGH_CONTRAST, value).apply()
    }

    fun updateComposeButtonSide(onLeft: Boolean) {
        composeButtonOnLeft = onLeft
        prefs.edit().putBoolean(K_COMPOSE_ON_LEFT, onLeft).apply()
    }

    fun updateComposeButtonBottomOffset(value: Float) {
        composeButtonBottomOffset = value.coerceIn(80f, 240f)
        prefs.edit().putFloat(K_COMPOSE_BOTTOM_OFFSET, composeButtonBottomOffset).apply()
    }

    fun moveComposeButtonBy(deltaX: Float, deltaY: Float) {
        composeButtonDragX = (composeButtonDragX + deltaX).coerceIn(-420f, 420f)
        composeButtonDragY = (composeButtonDragY + deltaY).coerceIn(-520f, 120f)
        prefs.edit().putFloat(K_COMPOSE_DRAG_X, composeButtonDragX).putFloat(K_COMPOSE_DRAG_Y, composeButtonDragY).apply()
    }

    fun setComposeButtonEditing(enabled: Boolean) { composeButtonCustomizationMode = enabled }

    /** 参考設定のうちサーバーAPIを必要としない、端末内の表示・通知設定。 */
    fun setting(key: String, default: Boolean = false): Boolean = prefs.getBoolean("setting_$key", default)
    fun updateSetting(key: String, value: Boolean) { prefs.edit().putBoolean("setting_$key", value).apply() }
    fun settingText(key: String): String = prefs.getString("setting_$key", "").orEmpty()
    fun updateSettingText(key: String, value: String) { prefs.edit().putString("setting_$key", value).apply() }
    fun settingInt(key: String, default: Int): Int = prefs.getInt("setting_$key", default)
    fun updateSettingInt(key: String, value: Int) { prefs.edit().putInt("setting_$key", value).apply() }

    fun openSettings() = open(Dest.SETTINGS)

    fun openUpdates() = open(Dest.UPDATES)

    fun checkForUpdates() = launchSafely(onError = { updatesLoading = false }) {
        updatesLoading = true
        val releases = UpdateManager(http, getApplication()).releases()
        releaseUpdates = releases
        availableUpdate = releases.firstOrNull { isVersionNewer(it.version, BuildConfig.VERSION_NAME) }
        updatesLoading = false
    }
    fun downloadUpdate(update: AppUpdate? = availableUpdate) {
        val target = update ?: return
        if (!isVersionNewer(target.version, BuildConfig.VERSION_NAME)) {
            ui = ui.copy(message = "North ${BuildConfig.VERSION_NAME} はすでにインストール済みです。")
            return
        }
        runCatching { UpdateManager(http, getApplication()).download(target) }
            .onSuccess { ui = ui.copy(message = "更新ファイルをダウンロードしています。完了通知からインストールを承認してください。") }
            .onFailure { ui = ui.copy(message = "更新のダウンロードを開始できませんでした: ${it.message}") }
    }

    fun canInstallUpdate(update: AppUpdate): Boolean =
        isVersionNewer(update.version, BuildConfig.VERSION_NAME)

    fun loadDrafts() = launchSafely(onError = { draftsLoading = false }) {
        draftsLoading = true
        drafts = api.drafts()
        draftsLoading = false
    }

    fun saveDraft(text: String, images: List<Uri>, existingMediaIds: List<String> = emptyList(), onDone: () -> Unit = {}) {
        if (text.isBlank() && images.isEmpty() && existingMediaIds.isEmpty()) return
        launchSafely {
            val resolver = getApplication<Application>().contentResolver
            val mediaIds = existingMediaIds + images.map {
                api.awaitMediaReady(api.uploadMedia(resolver, it, purpose = "draft")).id
            }
            api.createDraft(text.trim(), mediaIds)
            loadDrafts()
            ui = ui.copy(message = "下書きを保存しました")
            onDone()
        }
    }

    private fun isVersionNewer(candidate: String, current: String): Boolean {
        val a = candidate.removePrefix("v").split('.', '-', '_').map { it.toIntOrNull() ?: 0 }
        val b = current.removePrefix("v").split('.', '-', '_').map { it.toIntOrNull() ?: 0 }
        val count = maxOf(a.size, b.size)
        return (0 until count).firstOrNull { a.getOrElse(it) { 0 } != b.getOrElse(it) { 0 } }
            ?.let { a.getOrElse(it) { 0 } > b.getOrElse(it) { 0 } } ?: false
    }

    private fun loadThemeColors(): NorthColors =
        northColors(prefs.getString(K_THEME, if (prefs.getBoolean(K_DIM_BG, false)) "lightsout" else "midnight") ?: "midnight", Color(prefs.getLong(K_ACCENT, DEFAULT_ACCENT)))

    private fun runSearch(cursor: String?) {
        searchJob?.cancel()
        val q = search.query
        val tab = search.tab
        searchJob = launchSafely(onError = { search = search.copy(loading = false) }) {
            search = search.copy(loading = true)
            val page = api.search(q, tab, cursor)
            rememberUsers(page.items.map { it.author })
            // 検索中に語句やタブが変わっていたら、古い結果は捨てる
            if (q != search.query || tab != search.tab) return@launchSafely
            search = search.copy(
                loading = false,
                results = (if (cursor == null) page.items else search.results + page.items).distinctBy { it.id },
                nextCursor = page.nextCursor,
            )
        }
    }

    private fun loadHistory(): List<String> =
        prefs.getString(K_HISTORY, "").orEmpty().split('\n').filter { it.isNotBlank() }

    private fun saveHistory(list: List<String>) {
        prefs.edit().putString(K_HISTORY, list.joinToString("\n")).apply()
    }

    // ---------------- 投稿・リアクション ----------------
    /** 画像・動画があれば先に1件ずつアップロードし、動画の変換完了後に投稿する。 */
    fun post(
        text: String,
        images: List<Uri>,
        replyToId: String? = null,
        quoteId: String? = null,
        poll: PollInput? = null,
        existingMediaIds: List<String> = emptyList(),
        onDone: () -> Unit = {},
    ) {
        if (ui.posting) return
        launchSafely {
            ui = ui.copy(posting = true, message = null, reloginHint = false)
            val resolver = getApplication<Application>().contentResolver
            val mediaIds = existingMediaIds + images.map {
                api.awaitMediaReady(api.uploadMedia(resolver, it)).id
            }
            api.createPost(text.trim(), mediaIds, replyToId, quoteId, poll)
            ui = ui.copy(posting = false)
            onDone()
            if (quoteId != null) updatePost(quoteId) { it.copy(quoteCount = it.quoteCount + 1) }
            if (replyToId == null) {
                refresh()
            } else {
                updatePost(replyToId) { it.copy(replyCount = it.replyCount + 1) }
                // 開いているポスト詳細に、いま投稿した返信を反映させる
                screens.values.toList().forEach { it.onReplyPosted() }
            }
        }
    }

    /** #おうどんに伝えたい を、SNSへ公開せずアプリ開発者へ届ける。WebhookはWorker内だけに置く。 */
    fun reportAppIssue(text: String) {
        if (BuildConfig.BUG_REPORT_ENDPOINT.isBlank()) {
            ui = ui.copy(message = "不具合報告の送信先が未設定です。アプリの設定を確認してください。")
            return
        }
        val me = ui.me
        launchSafely {
            BugReportClient.send(
                http = http,
                endpoint = BuildConfig.BUG_REPORT_ENDPOINT,
                text = text.trim(),
                accountId = me?.id,
                handle = me?.handle,
            )
            ui = ui.copy(message = "アプリ開発者へ不具合報告を送りました。投稿は公開していません。")
        }
    }

    fun toggleLike(p: Post) {
        val on = !p.liked
        val delta = if (on) 1 else -1
        updatePost(p.id) { it.copy(liked = on, likeCount = (it.likeCount + delta).coerceAtLeast(0)) }
        launchSafely(onError = {
            updatePost(p.id) { it.copy(liked = p.liked, likeCount = p.likeCount) }
        }) {
            if (on) api.like(p.id) else api.unlike(p.id)
        }
    }

    fun toggleRepost(p: Post) {
        val on = !p.retweeted
        val delta = if (on) 1 else -1
        updatePost(p.id) { it.copy(retweeted = on, retweetCount = (it.retweetCount + delta).coerceAtLeast(0)) }
        launchSafely(onError = {
            updatePost(p.id) { it.copy(retweeted = p.retweeted, retweetCount = p.retweetCount) }
        }) {
            if (on) api.retweet(p.id) else api.unretweet(p.id)
        }
    }

    fun toggleBookmark(p: Post) {
        val on = !p.bookmarked
        updatePost(p.id) { it.copy(bookmarked = on) }
        launchSafely(onError = { updatePost(p.id) { it.copy(bookmarked = p.bookmarked) } }) {
            if (on) api.addBookmark(p.id) else api.removeBookmark(p.id)
        }
    }

    fun loadBookmarkFolders() = launchSafely { bookmarkFolders = api.bookmarkFolders() }
    fun addToBookmarkFolder(post: Post, folder: BookmarkFolder) = launchSafely {
        api.addToBookmarkFolder(folder.id, post.id)
        updatePost(post.id) { it.copy(bookmarked = true) }
        ui = ui.copy(message = "「${folder.name}」に追加しました")
    }

    fun pinPost(post: Post) = launchSafely {
        val owner = ui.me ?: return@launchSafely
        api.pinPost(post.id)
        setPinnedPost(owner.id, post.copy(pinned = true))
        ui = ui.copy(message = "ポストをプロフィールに固定しました")
    }

    fun unpinPost(post: Post) = launchSafely {
        val owner = ui.me ?: return@launchSafely
        api.unpinPost()
        setPinnedPost(owner.id, null)
        updatePost(post.id) { it.copy(pinned = false) }
        ui = ui.copy(message = "ポストの固定表示を解除しました")
    }

    fun setPostReplyPolicy(post: Post, policy: String) = launchSafely {
        api.setReplyPolicy(post.id, policy)
        updatePost(post.id) { it.copy(replyPolicy = policy) }
        ui = ui.copy(
            message = if (policy == "FOLLOWING") "返信をフォロー中のみに制限しました" else "返信を全員に開放しました"
        )
    }

    fun deletePost(post: Post) = launchSafely {
        api.deletePost(post.id)
        removePost(post.id)
        if (backStack.lastOrNull() == Dest.post(post.id)) back()
        ui = ui.copy(message = "ポストを削除しました")
    }

    fun logout() = launchSafely {
        auth.logout()
        accounts = auth.accounts()
        searchJob?.cancel()
        popToRoot()
        val stillLoggedIn = auth.isLoggedIn()
        ui = UiState(loggedIn = stillLoggedIn)
        search = SearchState(history = search.history)
        notif = NotifState()
        notifVisible = false
        if (stillLoggedIn) {
            refresh()
            loadTrends()
        } else {
            BackgroundNotifications.disable(getApplication())
        }
    }

    /** ホーム・検索結果・開いている詳細/プロフィールのすべてで、該当ポストを書き換える */
    private fun updatePost(id: String, f: (Post) -> Post) {
        ui = ui.copy(posts = ui.posts.map { it.patched(id, f) })
        search = search.copy(results = search.results.map { it.patched(id, f) })
        screens.values.toList().forEach { it.patch(id, f) }
    }

    private fun setPinnedPost(ownerId: String, post: Post?) {
        ui.me?.takeIf { it.id == ownerId }?.let { me ->
            me.pinnedTweet?.let { previous -> updatePost(previous.id) { it.copy(pinned = false) } }
            ui = ui.copy(me = me.copy(pinnedTweet = post))
        }
        post?.let { pinned -> updatePost(pinned.id) { it.copy(pinned = true) } }
        screens.values.toList().forEach { it.onPinnedPostChanged(ownerId, post) }
    }

    private fun removePost(id: String) {
        ui = ui.copy(
            posts = ui.posts.mapNotNull { it.withoutDeleted(id) },
            me = ui.me?.let { me -> if (me.pinnedTweet?.id == id) me.copy(pinnedTweet = null) else me },
        )
        search = search.copy(results = search.results.mapNotNull { it.withoutDeleted(id) })
        screens.values.toList().forEach { it.removePost(id) }
    }

    /** 画面から使えるように公開している。例外は共通のエラー表示（赤いバー）に流す */
    fun launchSafely(onError: (() -> Unit)? = null, block: suspend () -> Unit): Job =
        viewModelScope.launch {
            try {
                block()
            } catch (e: CancellationException) {
                throw e
            } catch (e: NotLoggedInException) {
                popToRoot()
                ui = UiState(loggedIn = false, message = "もう一度ログインしてください")
            } catch (e: ApiException) {
                onError?.invoke()
                notif = notif.copy(refreshing = false, loadingMore = false)
                ui = ui.copy(
                    refreshing = false, loadingMore = false, posting = false,
                    message = e.message, reloginHint = e.isPermission,
                )
            } catch (e: Exception) {
                onError?.invoke()
                notif = notif.copy(refreshing = false, loadingMore = false)
                ui = ui.copy(
                    refreshing = false, loadingMore = false, posting = false,
                    message = e.message ?: e.toString(),
                )
            }
        }

    private companion object {
        const val K_HISTORY = "search_history"
        const val K_BG_NOTIF = "bg_notif"
        const val K_ASKED_PERM = "asked_notif_perm"
        const val K_ACCOUNT_NOTIFICATION_PREFIX = "account_notification_"
        const val K_ACCENT = "accent"
        const val K_DIM_BG = "dim_background"
        const val K_THEME = "theme"
        const val K_FONT_SCALE = "font_scale"
        const val K_REDUCE_MOTION = "reduce_motion"
        const val K_HIGH_CONTRAST = "high_contrast"
        const val K_COMPOSE_ON_LEFT = "compose_on_left"
        const val K_COMPOSE_BOTTOM_OFFSET = "compose_bottom_offset"
        const val K_COMPOSE_DRAG_X = "compose_drag_x"
        const val K_COMPOSE_DRAG_Y = "compose_drag_y"
        const val DEFAULT_ACCENT = 0xFFFFD400L
        const val MAX_HISTORY = 10
    }
}
