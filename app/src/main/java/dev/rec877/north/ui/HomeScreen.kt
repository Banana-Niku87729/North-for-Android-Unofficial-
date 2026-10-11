@file:OptIn(ExperimentalMaterial3Api::class)

package dev.rec877.north.ui

import android.Manifest
import android.net.Uri
import android.os.Build
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.browser.customtabs.CustomTabsIntent
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.ime
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.repeatOnLifecycle
import kotlinx.coroutines.launch
import coil3.ImageLoader
import dev.rec877.north.MainViewModel
import dev.rec877.north.Media
import dev.rec877.north.Post
import dev.rec877.north.R
import dev.rec877.north.ui.theme.North

/**
 * ログイン後の全体:
 * エラー表示バー + 画面(ホーム/検索/通知) + その上に重ねる画面(ポスト詳細/プロフィール)
 * + 下部ナビ + 返信ダイアログ + メディアビューア
 */
@Composable
fun AppShell(vm: MainViewModel, loader: ImageLoader) {
    val c = North.colors
    val ui = vm.ui

    var tab by rememberSaveable { mutableIntStateOf(0) }
    // リポストボタンで開く選択肢（リポスト/引用）と、引用の作成画面の対象
    var repostTarget by remember { mutableStateOf<Post?>(null) }
    var accountDrawerOpen by remember { mutableStateOf(false) }
    var composerOpen by remember { mutableStateOf(false) }
    var inlineDraftText by remember { mutableStateOf("") }
    var inlineDraftImages by remember { mutableStateOf<List<Uri>>(emptyList()) }
    var clearInlineDraftVersion by remember { mutableIntStateOf(0) }
    var pendingTab by remember { mutableStateOf<Int?>(null) }
    var quoteTarget by remember { mutableStateOf<Post?>(null) }
    var viewer by remember { mutableStateOf<Pair<List<Media>, Int>?>(null) }

    // キーボードが出ている間は下部ナビを隠して、入力欄の上の余白を確保する
    val imeOpen = WindowInsets.ime.getBottom(LocalDensity.current) > 0

    val stack = vm.backStack
    val top = stack.lastOrNull()

    fun selectTab(i: Int) {
        // 詳細画面を見ている最中でも、ホームを押せば戻る操作なしでTLへ復帰する。
        if (i == 0) { vm.popToRoot(); tab = 0; return }
        if (i == tab) return
        if (inlineDraftText.isNotBlank() || inlineDraftImages.isNotEmpty()) pendingTab = i
        else { vm.popToRoot(); tab = i }
    }

    // 戻るボタン: 重ねている画面があれば1つ閉じる
    BackHandler(enabled = top != null) { vm.back() }

    // スクロール位置はここで持つ（タブを切り替えても戻ってきたとき保たれる）。
    // 検索結果は「語句 + タブ」が変わるたびに新しい状態にして、必ず先頭から表示する。
    val homeListState = rememberLazyListState()
    val notifListState = rememberLazyListState()
    val searchListState = rememberSaveable(
        vm.search.query, vm.search.tab, vm.search.version, saver = LazyListState.Saver
    ) { LazyListState() }

    // 画面が見えている間だけ通知のSSEをつなぐ（裏に回ったら切る）
    val lifecycleOwner = LocalLifecycleOwner.current
    LaunchedEffect(vm, lifecycleOwner) {
        lifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) { vm.runNotificationStream() }
    }

    // 通知を開いている間は「見ている」。別のタブへ移る・ポスト詳細などを開くと「見終わった」ので、自動で既読にする
    val notifShowing = tab == 2 && top == null
    LaunchedEffect(notifShowing) { if (notifShowing) vm.onNotifTabShown() else vm.onNotifTabHidden() }

    // 標準権限ダイアログの前にアプリ側の案内を出す（OSダイアログ自体は画像に変更不可）。
    val permLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { }
    var notificationIntro by remember { mutableStateOf(Build.VERSION.SDK_INT >= 33 && vm.shouldAskNotifPermission()) }

    val actions = remember(vm) {
        PostActions(
            // 返信ボタン: ポスト詳細を開いて、下の入力欄にカーソルを置く（ポップアップは出さない）
            onReply = { vm.openPost(it.id, focusReply = true) },
            onLike = { vm.toggleLike(it) },
            onRepost = { repostTarget = it },
            onBookmark = { vm.toggleBookmark(it) },
            bookmarkFolders = { vm.bookmarkFolders },
            onLoadBookmarkFolders = vm::loadBookmarkFolders,
            onAddToBookmarkFolder = vm::addToBookmarkFolder,
            onOpenMedia = { list, i -> viewer = list to i },
            onOpenPost = { vm.openPost(it.id) },
            onOpenUser = { vm.openUser(it.handle) },
            mediaHttpClient = vm.imageClient,
        )
    }

    Column(Modifier.fillMaxSize().background(c.bg).windowInsetsPadding(WindowInsets.safeDrawing)) {
        ui.message?.let { msg ->
            Row(
                Modifier
                    .fillMaxWidth()
                    .background(c.elev2)
                    .padding(start = 16.dp, end = 8.dp, top = 4.dp, bottom = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    msg,
                    color = c.danger,
                    fontSize = 14.sp,
                    modifier = Modifier
                        .weight(1f)
                        .clickable { vm.dismissMessage() }
                        .padding(vertical = 6.dp),
                )
                if (ui.reloginHint) {
                    // 権限不足(code 87)のとき: ログアウト→再ログインで新しい権限を許可し直す
                    TextButton(onClick = { vm.logout() }) { Text("再ログイン", color = c.accent) }
                }
            }
        }

        Box(Modifier.weight(1f).fillMaxWidth()) {
            when (tab) {
                0 -> HomeContent(
                    vm, loader, actions, homeListState,
                    onOpenAccountMenu = { accountDrawerOpen = true },
                    onInlineDraftChanged = { text, images -> inlineDraftText = text; inlineDraftImages = images },
                    clearInlineDraftVersion = clearInlineDraftVersion,
                )
                1 -> SearchScreen(vm, loader, actions, searchListState)
                else -> NotificationsScreen(vm, loader, notifListState)
            }

            if (top != null) {
                // 下の画面にタップが抜けないよう、pointerInput で受け止める
                Box(Modifier.fillMaxSize().background(c.bg).pointerInput(Unit) {}) {
                    key(top) {
                        when {
                            top.startsWith("post:") -> ThreadScreen(vm, loader, top.removePrefix("post:"), actions)
                            top.startsWith("user:") -> ProfileScreen(
                                vm, loader, top.removePrefix("user:"), actions,
                                onSearchPosts = { query ->
                                    vm.popToRoot()
                                    tab = 1
                                    vm.setSearchInput(query)
                                    vm.submitSearch(query)
                                },
                            )
                            top.startsWith("follows:") -> {
                                val parts = top.split(":", limit = 3)
                                if (parts.size == 3) UserListScreen(vm, loader, parts[1], parts[2])
                            }
                            top.startsWith("likes:") -> UserListScreen(vm, loader, top.removePrefix("likes:"), "likes")
                            top.startsWith("retweets:") -> UserListScreen(vm, loader, top.removePrefix("retweets:"), "retweets")
                            top.startsWith("quotes:") -> PostCollectionScreen(vm, loader, top.removePrefix("quotes:"), "quotes", actions)
                            top == dev.rec877.north.Dest.BOOKMARKS -> PostCollectionScreen(vm, loader, "all", "bookmarks", actions)
                            top == dev.rec877.north.Dest.LISTS -> ListsScreen(vm)
                            top == dev.rec877.north.Dest.SETTINGS -> SettingsScreen(vm)
                            top == dev.rec877.north.Dest.UPDATES -> UpdateScreen(vm)
                        }
                    }
                }
            }
        }

        if (!imeOpen) {
            BottomBar(
                selected = tab,
                unread = vm.notif.unread,
                onSelect = ::selectTab,
            )
        }
    }

    // どのタブ・プロフィール画面からも投稿できる、モバイルWeb版相当の投稿ボタン。
    if (!imeOpen && top != dev.rec877.north.Dest.SETTINGS) {
        // 下部ナビの通知アイコンと重ならないよう、FABはナビの上に十分な余白を取る。
        Box(Modifier.fillMaxSize().padding(end = 18.dp, bottom = 132.dp), contentAlignment = Alignment.BottomEnd) {
            Box(Modifier.size(54.dp).clip(CircleShape).background(c.accent).clickable { composerOpen = true }, contentAlignment = Alignment.Center) {
                NorthIcon(NIcons.Edit, c.textOnAccent, 24.dp, modifier = Modifier.size(24.dp))
            }
        }
    }
    // Dialogは別ウィンドウなので、前面の投稿画面から背面のプロフィール等をタップできない。
    if (composerOpen) Dialog(
        onDismissRequest = { composerOpen = false },
        properties = DialogProperties(usePlatformDefaultWidth = false, dismissOnClickOutside = false),
    ) {
        FullScreenComposer(
            me = vm.ui.me,
            loader = loader,
            posting = ui.posting,
            onClose = { composerOpen = false },
            drafts = vm.drafts,
            draftsLoading = vm.draftsLoading,
            onOpenDrafts = vm::loadDrafts,
            onSaveDraft = { text, images, savedMediaIds, done -> vm.saveDraft(text, images, savedMediaIds, done) },
            onSubmit = { text, images, savedMediaIds, poll, done -> vm.post(text, images, poll = poll, existingMediaIds = savedMediaIds) { done(); composerOpen = false } },
        )
    }

    pendingTab?.let { destination ->
        AlertDialog(
            onDismissRequest = { pendingTab = null }, containerColor = c.elev1,
            title = { Text("下書きを保存しますか？", color = c.text, fontWeight = FontWeight.ExtraBold) },
            text = { Text("タブを移動する前に、ホームの入力内容を下書きとして保存できます。", color = c.textMuted) },
            confirmButton = { TextButton(onClick = {
                vm.saveDraft(inlineDraftText, inlineDraftImages) {
                    inlineDraftText = ""; inlineDraftImages = emptyList(); clearInlineDraftVersion++
                    vm.popToRoot(); tab = destination; pendingTab = null
                }
            }) { Text("保存して移動", color = c.accent, fontWeight = FontWeight.Bold) } },
            dismissButton = { TextButton(onClick = {
                inlineDraftText = ""; inlineDraftImages = emptyList(); clearInlineDraftVersion++
                vm.popToRoot(); tab = destination; pendingTab = null
            }) { Text("保存しない", color = c.danger) } },
        )
    }

    repostTarget?.let { target ->
        RepostSheet(
            post = target,
            onRepost = { vm.toggleRepost(target); repostTarget = null },
            onQuote = { quoteTarget = target; repostTarget = null },
            onDismiss = { repostTarget = null },
        )
    }

    quoteTarget?.let { target ->
        QuoteDialog(vm, target, loader) { quoteTarget = null }
    }

    viewer?.let { (list, index) ->
        MediaViewer(list, index, loader, vm.imageClient) { viewer = null }
    }
    if (accountDrawerOpen) AccountDrawer(vm, loader) { accountDrawerOpen = false }

    if (notificationIntro) AlertDialog(
        onDismissRequest = { notificationIntro = false }, containerColor = c.elev1,
        icon = { androidx.compose.foundation.Image(painterResource(R.drawable.north_icon), contentDescription = "north", modifier = Modifier.fillMaxWidth().height(56.dp)) },
        title = { Text("通知を許可しますか？", color = c.text, fontWeight = FontWeight.ExtraBold) },
        text = { Text("返信・いいね・フォローなどの新着をお知らせします。後から設定で変更できます。", color = c.textMuted) },
        confirmButton = { TextButton(onClick = { notificationIntro = false; permLauncher.launch(Manifest.permission.POST_NOTIFICATIONS) }) { Text("許可する", color = c.accent) } },
        dismissButton = { TextButton(onClick = { notificationIntro = false }) { Text("今はしない", color = c.textMuted) } },
    )

    // emergencyタグの新しいリリースは、更新のダウンロードを開始するまでアプリ操作を止める。
    vm.availableUpdate?.takeIf { it.emergency }?.let { update ->
        AlertDialog(
            onDismissRequest = {}, containerColor = c.elev1,
            title = { Text("更新が必要です", color = c.text, fontWeight = FontWeight.ExtraBold) },
            text = { Text("North ${update.version} への緊急アップデートが必要です。\n\n${update.notes.ifBlank { "更新内容はリリースページで確認できます。" }}\n\nダウンロード完了後、通知からインストールを承認してください。", color = c.textMuted) },
            confirmButton = { TextButton(onClick = { vm.downloadUpdate(update) }) { Text("ダウンロードして更新", color = c.accent) } },
        )
    }
}

@Composable
private fun BottomBar(selected: Int, unread: Int, onSelect: (Int) -> Unit) {
    val c = North.colors
    Column {
        HorizontalDivider(color = c.border)
        Row(Modifier.fillMaxWidth().height(52.dp).background(c.bg)) {
            listOf(
                Triple(NIcons.Home, "ホーム", 0),
                Triple(NIcons.Search, "検索", 1),
                Triple(NIcons.Bell, "通知", 2),
            ).forEach { (icon, label, i) ->
                Box(
                    Modifier
                        .weight(1f)
                        .fillMaxHeight()
                        .semantics { contentDescription = label }
                        .clickable(role = Role.Tab) { onSelect(i) },
                    contentAlignment = Alignment.Center,
                ) {
                    Box {
                        NorthIcon(
                            icon,
                            if (i == selected) c.accent else c.textMuted,
                            26.dp,
                            filled = i == 0 && selected == 0,
                            modifier = Modifier.size(26.dp),
                        )
                        // サイトの .nav__badge: アクセント色の丸（99超は 99+）
                        if (i == 2 && unread > 0) {
                            Box(
                                Modifier
                                    .align(Alignment.TopEnd)
                                    .offset(x = 10.dp, y = (-4).dp)
                                    .defaultMinSize(minWidth = 18.dp, minHeight = 18.dp)
                                    .clip(CircleShape)
                                    .background(c.accent)
                                    .padding(horizontal = 5.dp),
                                contentAlignment = Alignment.Center,
                            ) {
                                Text(
                                    if (unread > 99) "99+" else unread.toString(),
                                    color = c.textOnAccent,
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Bold,
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun HomeContent(
    vm: MainViewModel,
    loader: ImageLoader,
    actions: PostActions,
    listState: LazyListState,
    onOpenAccountMenu: () -> Unit,
    onInlineDraftChanged: (String, List<Uri>) -> Unit,
    clearInlineDraftVersion: Int,
) {
    val c = North.colors
    val ui = vm.ui
    val scope = rememberCoroutineScope()

    Column(Modifier.fillMaxSize()) {
        // ---- ヘッダー（サイトの .head: 左にアバター、タイトル、右に切り替えボタン）----
        Row(
            Modifier.fillMaxWidth().height(53.dp).padding(horizontal = 16.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            val me = ui.me
            if (me != null) Avatar(me, 32.dp, loader, Modifier.clickable(onClick = onOpenAccountMenu))
            else Box(Modifier.size(32.dp).clip(CircleShape).background(c.elev3).clickable(onClick = onOpenAccountMenu))
            Spacer(Modifier.width(12.dp))
            Text(
                if (ui.ranked) "おすすめ" else "最新ポスト",
                color = c.text,
                fontSize = 20.sp,
                fontWeight = FontWeight.ExtraBold,
                modifier = Modifier.weight(1f).clickable {
                    scope.launch { listState.animateScrollToItem(0) }
                },
            )
            // 「最新ポスト」⇄「おすすめ」の切り替え（サイトの✦ボタン）
            Box(
                Modifier.size(40.dp).clip(CircleShape).clickable { vm.toggleRanked() },
                contentAlignment = Alignment.Center,
            ) {
                NorthIcon(
                    NIcons.Sparkle,
                    if (ui.ranked) c.accent else c.textMuted,
                    20.dp,
                    modifier = Modifier.size(20.dp),
                )
            }
        }
        HorizontalDivider(color = c.border)

        PullToRefreshBox(
            isRefreshing = ui.refreshing,
            onRefresh = { vm.refresh() },
            modifier = Modifier.weight(1f).fillMaxWidth(),
        ) {
            LazyColumn(Modifier.fillMaxSize(), state = listState) {
                item(key = "composer") {
                    Composer(
                        me = ui.me,
                        loader = loader,
                        posting = ui.posting,
                        onSubmit = { text, images, onDone -> vm.post(text, images, onDone = onDone) },
                        onSubmitWithSavedMediaIds = { text, images, savedMediaIds, onDone -> vm.post(text, images, existingMediaIds = savedMediaIds, onDone = onDone) },
                        onDraftChanged = onInlineDraftChanged,
                        clearDraftVersion = clearInlineDraftVersion,
                        drafts = vm.drafts,
                        draftsLoading = vm.draftsLoading,
                        onOpenDrafts = vm::loadDrafts,
                    )
                }

                items(ui.posts, key = { it.id }) { post -> PostCard(post, loader, actions) }

                if (ui.nextCursor != null) {
                    item(key = "more") {
                        // 末尾が見えたら次ページを読む
                        LaunchedEffect(ui.nextCursor) { vm.loadMore() }
                        Box(Modifier.fillMaxWidth().padding(16.dp), contentAlignment = Alignment.Center) {
                            CircularProgressIndicator(
                                color = c.accent,
                                strokeWidth = 2.dp,
                                modifier = Modifier.size(24.dp),
                            )
                        }
                    }
                } else if (!ui.refreshing && ui.posts.isEmpty()) {
                    item(key = "empty") {
                        Text(
                            "ポストがありません",
                            color = c.textMuted,
                            textAlign = TextAlign.Center,
                            modifier = Modifier.fillMaxWidth().padding(32.dp),
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun LoginScreen(vm: MainViewModel) {
    val c = North.colors
    val ctx = LocalContext.current
    Column(
        Modifier.fillMaxSize().background(c.bg).windowInsetsPadding(WindowInsets.safeDrawing).padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        androidx.compose.foundation.Image(
            painter = painterResource(R.drawable.north_icon),
            contentDescription = "north",
            // 画面幅いっぱいにせず、ロゴ本来の比率の枠を中央に配置する。
            // 端末幅によってロゴの見た目の中心がずれるのを防ぐ。
            modifier = Modifier.width(240.dp).height(68.dp),
            contentScale = ContentScale.Fit,
            colorFilter = androidx.compose.ui.graphics.ColorFilter.tint(c.text),
        )
        Spacer(Modifier.height(8.dp))
        Text("northのアカウントでログインします", color = c.textMuted, fontSize = 15.sp)
        Spacer(Modifier.height(32.dp))
        Box(
            Modifier
                .height(48.dp)
                .clip(CircleShape)
                .background(c.post)
                .clickable { CustomTabsIntent.Builder().build().launchUrl(ctx, vm.authorizeUri()) }
                .padding(horizontal = 32.dp),
            contentAlignment = Alignment.Center,
        ) {
            Text("northでログイン", color = c.textOnPost, fontSize = 16.sp, fontWeight = FontWeight.Bold)
        }
        vm.ui.message?.let {
            Spacer(Modifier.height(24.dp))
            Text(it, color = c.danger, fontSize = 14.sp, textAlign = TextAlign.Center)
        }
    }
}
