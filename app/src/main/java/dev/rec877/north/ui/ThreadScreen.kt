package dev.rec877.north.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil3.ImageLoader
import dev.rec877.north.MainViewModel
import dev.rec877.north.Post
import dev.rec877.north.ScreenController
import dev.rec877.north.patched
import dev.rec877.north.ui.theme.North

/** ポスト詳細の状態。戻ってきたときに読み込み済みの中身・スクロール位置・書きかけの返信が残るよう、ViewModelが保持する */
class ThreadController(private val vm: MainViewModel, val postId: String) : ScreenController {
    var ancestors by mutableStateOf<List<Post>>(emptyList()); private set
    var main by mutableStateOf<Post?>(null); private set
    var replies by mutableStateOf<List<Post>>(emptyList()); private set
    var nextCursor by mutableStateOf<String?>(null); private set
    var loading by mutableStateOf(false); private set
    var loadingMore by mutableStateOf(false); private set
    var failed by mutableStateOf(false); private set

    /** 下の入力欄の書きかけ */
    var draft by mutableStateOf("")

    /** 返信先。null のときは、この画面の本人のポストへの返信 */
    var replyTarget by mutableStateOf<Post?>(null)

    val listState = LazyListState()

    /** 最初の読み込み後に、本人のポストが見える位置までスクロール済みか */
    var scrolled = false
    private var started = false

    fun start() {
        if (started) return
        started = true
        load()
    }

    fun load() {
        vm.launchSafely(onError = { loading = false; failed = true }) {
            // すでに表示できる中身があるときは、スピナーに差し替えずに裏で取り直す
            loading = main == null
            failed = false
            val c = vm.api.conversation(postId, null)
            ancestors = c.ancestors
            main = c.tweet
            replies = c.replies.distinctBy { it.id }
            nextCursor = c.nextCursor
            loading = false
        }
    }

    fun loadMore() {
        val cursor = nextCursor ?: return
        if (loadingMore || loading) return
        vm.launchSafely(onError = { loadingMore = false }) {
            loadingMore = true
            val c = vm.api.conversation(postId, cursor)
            replies = (replies + c.replies).distinctBy { it.id }
            nextCursor = c.nextCursor
            loadingMore = false
        }
    }

    override fun patch(id: String, f: (Post) -> Post) {
        ancestors = ancestors.map { it.patched(id, f) }
        main = main?.patched(id, f)
        replies = replies.map { it.patched(id, f) }
    }

    override fun onReplyPosted() {
        if (main != null) load()
    }
}

@Composable
fun ThreadScreen(vm: MainViewModel, loader: ImageLoader, postId: String, actions: PostActions) {
    val c = North.colors
    val ctl = vm.screen("post:$postId") { ThreadController(vm, postId) }
    val main = ctl.main
    val focusRequester = remember { FocusRequester() }
    val keyboard = LocalSoftwareKeyboardController.current

    LaunchedEffect(ctl) { ctl.start() }

    // 最初に読み込めたら、本人のポストが先頭に来る位置までスクロール（祖先はその上に隠れる）
    LaunchedEffect(main != null) {
        if (main != null && !ctl.scrolled) {
            ctl.listState.scrollToItem(ctl.ancestors.size)
            ctl.scrolled = true
        }
    }

    // 一覧の返信ボタンから開かれたときは、下の入力欄にカーソルを置いてキーボードを出す
    LaunchedEffect(vm.replyFocusPostId, main != null) {
        if (main != null && vm.replyFocusPostId == postId) {
            ctl.replyTarget = null
            withFrameNanos { } // 入力欄が配置されるのを待つ
            runCatching { focusRequester.requestFocus() }
            keyboard?.show()
            vm.consumeReplyFocus()
        }
    }

    // この画面の中では、返信ボタンはポップアップではなく下の入力欄に切り替える（返信先を選べる）
    val mainId = (main?.retweetOf ?: main)?.id
    val threadActions = remember(actions, ctl, focusRequester, keyboard, mainId) {
        PostActions(
            onReply = { target ->
                ctl.replyTarget = if (target.id == mainId) null else target
                runCatching { focusRequester.requestFocus() }
                keyboard?.show()
            },
            onLike = actions.onLike,
            onRepost = actions.onRepost,
            onBookmark = actions.onBookmark,
            bookmarkFolders = actions.bookmarkFolders,
            onLoadBookmarkFolders = actions.onLoadBookmarkFolders,
            onAddToBookmarkFolder = actions.onAddToBookmarkFolder,
            onOpenMedia = actions.onOpenMedia,
            onOpenPost = actions.onOpenPost,
            onOpenUser = actions.onOpenUser,
            mediaHttpClient = actions.mediaHttpClient,
            onOpenMention = actions.onOpenMention,
        )
    }

    Column(Modifier.fillMaxSize().background(c.bg)) {
        Row(
            Modifier.fillMaxWidth().height(53.dp).padding(horizontal = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                Modifier.size(40.dp).clip(CircleShape).clickable { vm.back() },
                contentAlignment = Alignment.Center,
            ) { NorthIcon(NIcons.Back, c.text, 22.dp, modifier = Modifier.size(22.dp)) }
            Text(
                "ポスト",
                color = c.text,
                fontSize = 20.sp,
                fontWeight = FontWeight.ExtraBold,
                modifier = Modifier.padding(start = 12.dp),
            )
        }
        HorizontalDivider(color = c.border)

        if (main == null) {
            Box(Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
                if (ctl.failed) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text("ポストを読み込めませんでした", color = c.textMuted, fontSize = 15.sp)
                        TextButton(onClick = { ctl.load() }) { Text("もう一度試す", color = c.accent) }
                    }
                } else {
                    CircularProgressIndicator(color = c.accent, strokeWidth = 2.dp, modifier = Modifier.size(28.dp))
                }
            }
        } else {
            LazyColumn(Modifier.weight(1f).fillMaxWidth(), state = ctl.listState) {
                items(ctl.ancestors, key = { "a:${it.id}" }) { PostCard(it, loader, threadActions) }

                item(key = "main") { MainPostView(vm, main, loader, threadActions) }

                items(ctl.replies, key = { "r:${it.id}" }) { PostCard(it, loader, threadActions) }

                if (ctl.nextCursor != null) {
                    item(key = "more") {
                        // 末尾が見えたら返信の続きを読む
                        LaunchedEffect(ctl.nextCursor) { ctl.loadMore() }
                        Box(Modifier.fillMaxWidth().padding(16.dp), contentAlignment = Alignment.Center) {
                            CircularProgressIndicator(color = c.accent, strokeWidth = 2.dp, modifier = Modifier.size(24.dp))
                        }
                    }
                } else if (ctl.replies.isEmpty() && !ctl.loading) {
                    item(key = "empty") {
                        Text(
                            "返信はまだありません",
                            color = c.textMuted,
                            fontSize = 15.sp,
                            textAlign = TextAlign.Center,
                            modifier = Modifier.fillMaxWidth().padding(32.dp),
                        )
                    }
                }
            }

            ReplyBar(vm, ctl, main.retweetOf ?: main, loader, focusRequester)
        }
    }
}

/**
 * 画面の下に固定の返信入力欄。ポップアップは出さず、ここで直接返信する。
 * 返信先は、ふだんは本人のポスト。詳細の中のほかのポストの返信ボタンを押すと、そのポストに切り替わる（×で戻せる）。
 */
@Composable
private fun ReplyBar(
    vm: MainViewModel,
    ctl: ThreadController,
    shown: Post,
    loader: ImageLoader,
    focusRequester: FocusRequester,
) {
    val c = North.colors
    val focusManager = LocalFocusManager.current
    val target = ctl.replyTarget ?: shown
    val weight = postWeight(ctl.draft)
    val posting = vm.ui.posting
    val canSend = !posting && ctl.draft.isNotBlank() && weight <= 280

    Column(Modifier.fillMaxWidth().background(c.bg)) {
        HorizontalDivider(color = c.border)

        if (target.id != shown.id) {
            Row(
                Modifier.fillMaxWidth().padding(start = 16.dp, end = 8.dp, top = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    "@${target.author.handle} に返信",
                    color = c.textMuted,
                    fontSize = 13.sp,
                    maxLines = 1,
                    modifier = Modifier.weight(1f),
                )
                Box(
                    Modifier.size(32.dp).clip(CircleShape).clickable { ctl.replyTarget = null },
                    contentAlignment = Alignment.Center,
                ) { NorthIcon(NIcons.Close, c.textMuted, 14.dp, modifier = Modifier.size(14.dp)) }
            }
        }

        Row(
            Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 10.dp),
            verticalAlignment = Alignment.Bottom,
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            vm.ui.me?.let { Avatar(it, 32.dp, loader, Modifier.padding(bottom = 4.dp)) }

            Box(
                Modifier
                    .weight(1f)
                    .heightIn(min = 40.dp)
                    .clip(RoundedCornerShape(20.dp))
                    .background(c.elev2)
                    .padding(horizontal = 14.dp, vertical = 10.dp),
                contentAlignment = Alignment.CenterStart,
            ) {
                BasicTextField(
                    value = ctl.draft,
                    onValueChange = { ctl.draft = it },
                    maxLines = 5,
                    textStyle = TextStyle(color = c.text, fontSize = 16.sp, lineHeight = 22.sp),
                    cursorBrush = SolidColor(c.accent),
                    modifier = Modifier.fillMaxWidth().focusRequester(focusRequester),
                    decorationBox = { inner ->
                        Box {
                            if (ctl.draft.isEmpty()) {
                                Text("返信をポスト", color = c.textMuted, fontSize = 16.sp)
                            }
                            inner()
                        }
                    },
                )
            }

            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                if (weight > 240) {
                    Text(
                        "$weight/280",
                        color = if (weight > 280) c.danger else c.textMuted,
                        fontSize = 11.sp,
                    )
                }
                Box(
                    Modifier
                        .height(40.dp)
                        .clip(CircleShape)
                        .background(c.post.copy(alpha = if (canSend) 1f else 0.5f))
                        .clickable(enabled = canSend) {
                            vm.post(ctl.draft, emptyList(), target.id) {
                                ctl.draft = ""
                                ctl.replyTarget = null
                                focusManager.clearFocus()
                            }
                        }
                        .padding(horizontal = 16.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        if (posting) "送信中…" else "返信",
                        color = c.textOnPost,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Bold,
                    )
                }
            }
        }
    }
}

/** 詳細の本人ポスト: 本文を大きく、投稿時刻と件数、操作ボタンを並べる */
@Composable
private fun MainPostView(vm: MainViewModel, post: Post, loader: ImageLoader, actions: PostActions) {
    val c = North.colors
    val shown = post.retweetOf ?: post
    var shareOpen by remember { mutableStateOf(false) }
    var folderPickerOpen by remember { mutableStateOf(false) }

    Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp)) {
        Row(
            Modifier.clickable { actions.onOpenUser(shown.author) },
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Avatar(shown.author, 48.dp, loader)
            Column {
                Text(shown.author.name, color = c.text, fontSize = 15.sp, fontWeight = FontWeight.Bold, maxLines = 1)
                Text("@${shown.author.handle}", color = c.textMuted, fontSize = 15.sp, maxLines = 1)
            }
        }

        if (shown.unavailable) {
            Text("このポストは表示できません", color = c.textMuted, fontSize = 15.sp, modifier = Modifier.padding(top = 12.dp))
        } else {
            if (!shown.inReplyToHandle.isNullOrBlank()) {
                Text("@${shown.inReplyToHandle} に返信", color = c.textMuted, fontSize = 14.sp, modifier = Modifier.padding(top = 12.dp))
            }
            if (shown.text.isNotEmpty()) {
                Text(
                    shown.text,
                    color = c.text,
                    fontSize = 20.sp,
                    lineHeight = 28.sp,
                    modifier = Modifier.padding(top = 12.dp),
                )
            }
            if (shown.media.isNotEmpty()) {
                MediaGrid(media = shown.media, loader = loader, onOpen = { i -> actions.onOpenMedia(shown.media, i) }, httpClient = actions.mediaHttpClient)
            }
            shown.poll?.let { PollCard(it) }
            shown.quoted?.let { QuotedPost(it, loader, actions) }
        }

        val stamp = absoluteTime(shown.createdAt) + (shown.source?.takeIf { it.isNotBlank() }?.let { " · $it" } ?: "")
        Text(stamp, color = c.textMuted, fontSize = 15.sp, modifier = Modifier.padding(top = 16.dp, bottom = 12.dp))

        val counts = buildList<Pair<Int, String>> {
            if (shown.retweetCount > 0) add(shown.retweetCount to "件のリポスト")
            if (shown.quoteCount > 0) add(shown.quoteCount to "件の引用")
            if (shown.likeCount > 0) add(shown.likeCount to "件のいいね")
        }
        if (counts.isNotEmpty()) {
            HorizontalDivider(color = c.border)
            Row(Modifier.padding(vertical = 12.dp), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                counts.forEach { (n, label) ->
                    Text(
                        buildAnnotatedString {
                            withStyle(SpanStyle(fontWeight = FontWeight.Bold, color = c.text)) { append(n.toString()) }
                            append(" $label")
                        },
                        color = c.textMuted,
                        fontSize = 15.sp,
                        modifier = Modifier.clickable {
                            when (label) {
                                "件のいいね" -> vm.open(dev.rec877.north.Dest.postLikes(shown.id))
                                "件のリポスト" -> vm.open(dev.rec877.north.Dest.postRetweets(shown.id))
                                "件の引用" -> vm.open(dev.rec877.north.Dest.postQuotes(shown.id))
                            }
                        },
                    )
                }
            }
        }

        HorizontalDivider(color = c.border)
        Row(
            Modifier.fillMaxWidth().padding(top = 4.dp),
            horizontalArrangement = Arrangement.SpaceAround,
        ) {
            ActionButton(NIcons.Reply, 0, c.textMuted, active = false) { actions.onReply(shown) }
            ActionButton(NIcons.Repost, 0, if (shown.retweeted) c.repost else c.textMuted, active = false) {
                actions.onRepost(shown)
            }
            ActionButton(NIcons.Like, 0, if (shown.liked) c.like else c.textMuted, active = shown.liked) {
                actions.onLike(shown)
            }
            ActionButton(NIcons.Bookmark, 0, if (shown.bookmarked) c.accent else c.textMuted, active = false) {
                actions.onBookmark(shown)
            }
            ActionButton(NIcons.Share, 0, c.textMuted, active = false) { shareOpen = true }
        }
    }
    HorizontalDivider(color = c.border)
    if (shareOpen) ShareMenu(shown, actions.bookmarkFolders(), { shareOpen = false }, { actions.onBookmark(shown); shareOpen = false }, { actions.onLoadBookmarkFolders(); folderPickerOpen = true })
    if (folderPickerOpen) BookmarkFolderPicker(actions.bookmarkFolders(), { folderPickerOpen = false }) { folder -> actions.onAddToBookmarkFolder(shown, folder); folderPickerOpen = false; shareOpen = false }
}
