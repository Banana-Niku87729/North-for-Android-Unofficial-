@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)

package dev.rec877.north.ui

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import coil3.ImageLoader
import coil3.compose.AsyncImage
import dev.rec877.north.Config
import dev.rec877.north.Dest
import dev.rec877.north.MainViewModel
import dev.rec877.north.Media
import dev.rec877.north.Post
import dev.rec877.north.PostPage
import dev.rec877.north.ScreenController
import dev.rec877.north.User
import dev.rec877.north.patched
import dev.rec877.north.withoutDeleted
import dev.rec877.north.ui.theme.North

/** ページ送りのあるポスト一覧（プロフィールの「ポスト」「いいね」タブ用） */
class PagedPosts(private val fetch: suspend (String?) -> PostPage) {
    var items by mutableStateOf<List<Post>>(emptyList()); private set
    var cursor by mutableStateOf<String?>(null); private set
    var loading by mutableStateOf(false); private set
    var loaded by mutableStateOf(false); private set

    suspend fun first() {
        loading = true
        try {
            val p = fetch(null)
            items = p.items.distinctBy { it.id }
            cursor = p.nextCursor
            loaded = true
        } finally {
            loading = false
        }
    }

    suspend fun more() {
        val cur = cursor ?: return
        if (loading) return
        loading = true
        try {
            val p = fetch(cur)
            items = (items + p.items).distinctBy { it.id }
            cursor = p.nextCursor
        } finally {
            loading = false
        }
    }

    fun patch(id: String, f: (Post) -> Post) { items = items.map { it.patched(id, f) } }
    fun remove(id: String) { items = items.mapNotNull { it.withoutDeleted(id) } }
}

/** プロフィール画面の状態。戻ってきたとき、読み込み済みの中身とスクロール位置が残る */
class ProfileController(private val vm: MainViewModel, val handle: String) : ScreenController {
    var user by mutableStateOf<User?>(null); private set
    var failed by mutableStateOf(false); private set
    var tab by mutableIntStateOf(0); private set
    var refreshing by mutableStateOf(false); private set
    var postNotificationsEnabled by mutableStateOf(vm.isAccountNotificationEnabled(handle)); private set

    val posts = PagedPosts { vm.api.userPosts(handle, it) }
    val likes = PagedPosts { vm.api.userLikes(handle, it) }
    val listState = LazyListState()
    private var started = false

    fun start() {
        if (started) return
        started = true
        loadUser()
        vm.launchSafely { posts.first() }
    }

    fun loadUser() {
        vm.launchSafely(onError = { failed = true }) {
            failed = false
            user = vm.api.user(handle).also { vm.rememberUsers(listOf(it)) }
        }
    }

    /** プロフィール情報と、画面で選ばれている一覧を一度に取り直す。 */
    fun refresh() {
        if (refreshing) return
        refreshing = true
        vm.launchSafely(onError = { refreshing = false }) {
            failed = false
            user = vm.api.user(handle).also { vm.rememberUsers(listOf(it)) }
            if (tab == 0) posts.first() else likes.first()
            refreshing = false
        }
    }

    fun syncSelf(updated: User) { user = updated }

    fun selectTab(i: Int) {
        tab = i
        if (i == 1 && !likes.loaded && !likes.loading) vm.launchSafely { likes.first() }
    }

    fun toggleFollow() {
        val u = user ?: return
        val on = !u.following
        user = u.copy(following = on, followerCount = (u.followerCount + if (on) 1 else -1).coerceAtLeast(0))
        vm.launchSafely(onError = { user = u }) {
            if (on) vm.api.follow(handle) else vm.api.unfollow(handle)
            // 非公開アカウントはフォローリクエストになるので、実際の状態を取り直す
            user = vm.api.user(handle)
        }
    }

    fun togglePostNotifications() {
        val before = postNotificationsEnabled
        val enabled = !before
        postNotificationsEnabled = enabled
        vm.setAccountNotifications(handle, enabled) { postNotificationsEnabled = before }
    }

    override fun patch(id: String, f: (Post) -> Post) {
        posts.patch(id, f)
        likes.patch(id, f)
        user = user?.let { current ->
            current.pinnedTweet?.let { current.copy(pinnedTweet = it.patched(id, f)) } ?: current
        }
    }

    override fun removePost(id: String) {
        posts.remove(id)
        likes.remove(id)
        user = user?.let { current -> if (current.pinnedTweet?.id == id) current.copy(pinnedTweet = null) else current }
    }

    override fun onPinnedPostChanged(ownerId: String, post: Post?) {
        user = user?.let { current -> if (current.id == ownerId) current.copy(pinnedTweet = post) else current }
    }

    override fun onReplyPosted() {}
}

@Composable
fun ProfileScreen(
    vm: MainViewModel,
    loader: ImageLoader,
    handle: String,
    actions: PostActions,
    onSearchPosts: (String) -> Unit,
) {
    val c = North.colors
    val ctl = vm.screen("user:$handle") { ProfileController(vm, handle) }
    val user = ctl.user
    var editing by remember { mutableStateOf(false) }

    LaunchedEffect(ctl) { ctl.start() }

    Box(Modifier.fillMaxSize().background(c.bg)) {
        if (user == null) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                if (ctl.failed) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text("プロフィールを読み込めませんでした", color = c.textMuted, fontSize = 15.sp)
                        TextButton(onClick = { ctl.loadUser() }) { Text("もう一度試す", color = c.accent) }
                    }
                } else {
                    CircularProgressIndicator(color = c.accent, strokeWidth = 2.dp, modifier = Modifier.size(28.dp))
                }
            }
        } else {
            val list = if (ctl.tab == 0) ctl.posts else ctl.likes
            val pinnedPost = if (ctl.tab == 0) user.pinnedTweet else null
            val visiblePosts = list.items.filterNot { it.id == pinnedPost?.id }
            val isSelf = vm.ui.me?.id == user.id || vm.ui.me?.handle.equals(user.handle, ignoreCase = true)
            val compactHeader by remember(ctl.listState) {
                derivedStateOf { ctl.listState.firstVisibleItemIndex > 0 || ctl.listState.firstVisibleItemScrollOffset > 180 }
            }

            LaunchedEffect(vm.ui.me?.id, vm.ui.me?.name, isSelf) {
                if (isSelf) vm.ui.me?.let(ctl::syncSelf)
            }

            PullToRefreshBox(
                isRefreshing = ctl.refreshing,
                onRefresh = ctl::refresh,
                modifier = Modifier.fillMaxSize(),
            ) {
                LazyColumn(Modifier.fillMaxSize(), state = ctl.listState) {
                    item(key = "header") {
                        ProfileHeader(
                            user, isSelf, loader,
                            onFollow = { ctl.toggleFollow() },
                            postNotificationsEnabled = ctl.postNotificationsEnabled,
                            onTogglePostNotifications = { ctl.togglePostNotifications() },
                            onOpenFollows = { kind -> vm.open(Dest.follows(user.handle, kind)) },
                            onOpenMedia = { media -> actions.onOpenMedia(listOf(media), 0) },
                            onEdit = { editing = true },
                        )
                    }
                    item(key = "tabs") { NorthTabs(listOf("ポスト", "いいね"), ctl.tab) { ctl.selectTab(it) } }

                    pinnedPost?.let { pinned ->
                        item(key = "pinned:${pinned.id}") { PostCard(pinned, loader, actions, pinned = true) }
                    }

                    items(visiblePosts, key = { "${ctl.tab}:${it.id}" }) { PostCard(it, loader, actions) }

                    if (list.cursor != null) {
                        item(key = "more") {
                            // 末尾が見えたら続きを読む
                            LaunchedEffect(list.cursor, ctl.tab) { vm.launchSafely { list.more() } }
                            Box(Modifier.fillMaxWidth().padding(16.dp), contentAlignment = Alignment.Center) {
                                CircularProgressIndicator(color = c.accent, strokeWidth = 2.dp, modifier = Modifier.size(24.dp))
                            }
                        }
                    } else if (list.loaded && visiblePosts.isEmpty() && pinnedPost == null) {
                        item(key = "empty") {
                            Text(
                                if (user.isProtected && !isSelf && !user.following) "このアカウントのポストは非公開です"
                                else if (ctl.tab == 0) "ポストはまだありません" else "いいねはまだありません",
                                color = c.textMuted,
                                fontSize = 15.sp,
                                textAlign = TextAlign.Center,
                                modifier = Modifier.fillMaxWidth().padding(32.dp),
                            )
                        }
                    }
                }
            }

            if (compactHeader) {
                CompactProfileBar(user, onSearchPosts)
            }

            if (editing) {
                EditProfileDialog(user, vm, onDismiss = { editing = false })
            }
        }

        // 戻るボタン（ヘッダー画像の上に重ねる）
        Box(
            Modifier
                .padding(12.dp)
                .size(36.dp)
                .clip(CircleShape)
                .background(Color(0x99000000))
                .clickable { vm.back() }
                .align(Alignment.TopStart),
            contentAlignment = Alignment.Center,
        ) { NorthIcon(NIcons.Back, Color.White, 20.dp, modifier = Modifier.size(20.dp)) }
    }
}

@Composable
private fun ProfileHeader(
    user: User,
    isSelf: Boolean,
    loader: ImageLoader,
    onFollow: () -> Unit,
    postNotificationsEnabled: Boolean,
    onTogglePostNotifications: () -> Unit,
    onOpenFollows: (String) -> Unit,
    onOpenMedia: (Media) -> Unit,
    onEdit: () -> Unit,
) {
    val c = North.colors
    val uriHandler = LocalUriHandler.current

    Column(Modifier.fillMaxWidth()) {
        // ヘッダー画像（3:1）。無いときはアクセント系の無地
        Box(Modifier.fillMaxWidth().aspectRatio(3f).background(c.elev3)) {
            val url = Config.absUrl(user.headerUrl)
            if (url != null) {
                AsyncImage(
                    model = url,
                    imageLoader = loader,
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize().clickable {
                        onOpenMedia(Media(id = "header:${user.id}", url = url, width = 960, height = 320))
                    },
                )
            }
        }

        // アイコン（ヘッダーに半分重ねる）とフォローボタン
        Box(Modifier.fillMaxWidth().height(48.dp).padding(horizontal = 16.dp)) {
            Avatar(
                user, 80.dp, loader,
                Modifier
                    .align(Alignment.TopStart)
                    .offset(y = (-40).dp)
                    .requiredSize(80.dp)
                    .border(4.dp, c.bg, CircleShape)
                    .clickable {
                        Config.absUrl(user.avatarUrl)?.let { onOpenMedia(Media(id = "avatar:${user.id}", url = it)) }
                    },
            )
            if (isSelf) {
                Box(
                    Modifier.align(Alignment.CenterEnd).height(34.dp).clip(CircleShape)
                        .border(1.dp, c.borderStrong, CircleShape).clickable(onClick = onEdit).padding(horizontal = 16.dp),
                    contentAlignment = Alignment.Center,
                ) { Text("プロフィールを編集", color = c.text, fontSize = 14.sp, fontWeight = FontWeight.Bold) }
            } else {
                val following = user.following
                Row(
                    Modifier.align(Alignment.CenterEnd),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    if (following) {
                        Box(
                            Modifier
                                .height(34.dp)
                                .clip(CircleShape)
                                .border(1.dp, if (postNotificationsEnabled) c.accent else c.borderStrong, CircleShape)
                                .clickable(onClick = onTogglePostNotifications)
                                .padding(horizontal = 12.dp),
                            contentAlignment = Alignment.Center,
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(5.dp),
                            ) {
                                NorthIcon(
                                    NIcons.Bell,
                                    if (postNotificationsEnabled) c.accent else c.text,
                                    16.dp,
                                    modifier = Modifier.size(16.dp),
                                )
                                Text(
                                    if (postNotificationsEnabled) "新着通知オン" else "新着を受け取る",
                                    color = if (postNotificationsEnabled) c.accent else c.text,
                                    fontSize = 13.sp,
                                    fontWeight = FontWeight.Bold,
                                )
                            }
                        }
                    }
                    Box(
                        Modifier
                            .height(34.dp)
                            .clip(CircleShape)
                            .then(
                                if (following) Modifier.border(1.dp, c.borderStrong, CircleShape)
                                else Modifier.background(c.post)
                            )
                            .clickable(onClick = onFollow)
                            .padding(horizontal = 16.dp),
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(
                            if (following) "フォロー中" else "フォローする",
                            color = if (following) c.text else c.textOnPost,
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Bold,
                        )
                    }
                }
            }
        }

        Column(Modifier.padding(horizontal = 16.dp, vertical = 4.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                Text(
                    user.name,
                    color = c.text,
                    fontSize = 20.sp,
                    fontWeight = FontWeight.ExtraBold,
                    modifier = Modifier.weight(1f, fill = false),
                )
                UserBadges(user, compact = false)
            }
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("@${user.handle}", color = c.textMuted, fontSize = 15.sp)
                if (user.followedBy) {
                    Box(Modifier.clip(CircleShape).background(c.elev3).padding(horizontal = 8.dp, vertical = 2.dp)) {
                        Text("フォローされています", color = c.textMuted, fontSize = 12.sp)
                    }
                }
            }

            if (!user.bio.isNullOrBlank()) {
                Text(user.bio, color = c.text, fontSize = 15.sp, lineHeight = 21.sp, modifier = Modifier.padding(top = 12.dp))
            }

            val meta = buildList<String> {
                user.location?.takeIf { it.isNotBlank() }?.let { add(it) }
                joinedMonth(user.createdAt).takeIf { it.isNotEmpty() }?.let { add("${it}から利用") }
            }
            if (meta.isNotEmpty()) {
                Text(meta.joinToString("  ·  "), color = c.textMuted, fontSize = 14.sp, modifier = Modifier.padding(top = 10.dp))
            }
            user.website?.takeIf { it.isNotBlank() }?.let { site ->
                Text(
                    site,
                    color = c.accent,
                    fontSize = 14.sp,
                    maxLines = 1,
                    modifier = Modifier
                        .padding(top = 4.dp)
                        .clickable {
                            val url = if (site.startsWith("http://") || site.startsWith("https://")) site else "https://$site"
                            runCatching { uriHandler.openUri(url) }
                        },
                )
            }

            Row(Modifier.padding(top = 12.dp, bottom = 12.dp), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                listOf(
                    Triple(user.followingCount, "フォロー中", "following"),
                    Triple(user.followerCount, "フォロワー", "followers"),
                    Triple(user.tweetCount, "ポスト", null),
                ).forEach { (n, label, destination) ->
                        Text(
                            buildAnnotatedString {
                                withStyle(SpanStyle(fontWeight = FontWeight.Bold, color = c.text)) { append(n.toString()) }
                                append(" $label")
                            },
                            color = c.textMuted,
                            fontSize = 14.sp,
                            modifier = if (destination != null) Modifier.clickable { onOpenFollows(destination) } else Modifier,
                        )
                    }
            }
        }
    }

}

@Composable
private fun CompactProfileBar(user: User, onSearchPosts: (String) -> Unit) {
    val c = North.colors
    Row(
        Modifier.fillMaxWidth().height(54.dp).background(c.bg).border(0.5.dp, c.border)
            .padding(start = 60.dp, end = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(user.name, color = c.text, fontSize = 16.sp, fontWeight = FontWeight.Bold, maxLines = 1)
                UserBadges(user)
            }
            Text("${user.tweetCount} 件のポスト", color = c.textMuted, fontSize = 12.sp)
        }
        Box(Modifier.size(40.dp).clip(CircleShape).clickable { onSearchPosts("from:${user.handle}") }, contentAlignment = Alignment.Center) {
            NorthIcon(NIcons.Search, c.text, 20.dp, modifier = Modifier.size(20.dp))
        }
    }
}

@Composable
private fun EditProfileDialog(user: User, vm: MainViewModel, onDismiss: () -> Unit) {
    val c = North.colors
    var name by remember(user.id) { mutableStateOf(user.name) }
    var bio by remember(user.id) { mutableStateOf(user.bio.orEmpty()) }
    var location by remember(user.id) { mutableStateOf(user.location.orEmpty()) }
    var website by remember(user.id) { mutableStateOf(user.website.orEmpty()) }
    var avatar by remember { mutableStateOf<Uri?>(null) }
    var header by remember { mutableStateOf<Uri?>(null) }
    val avatarPicker = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { avatar = it }
    val headerPicker = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { header = it }

    Dialog(onDismissRequest = onDismiss) {
        Column(Modifier.fillMaxWidth().clip(androidx.compose.foundation.shape.RoundedCornerShape(24.dp)).background(c.elev2).padding(20.dp)) {
            Text("プロフィールを編集", color = c.text, fontSize = 20.sp, fontWeight = FontWeight.ExtraBold)
            EditProfileField("名前", name) { name = it }
            EditProfileField("自己紹介", bio, singleLine = false) { bio = it }
            EditProfileField("場所", location) { location = it }
            EditProfileField("ウェブサイト", website) { website = it }
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                TextButton(onClick = { avatarPicker.launch("image/*") }, modifier = Modifier.weight(1f)) { Text(if (avatar == null) "アイコンを変更" else "アイコンを選択済み", color = c.accent) }
                TextButton(onClick = { headerPicker.launch("image/*") }, modifier = Modifier.weight(1f)) { Text(if (header == null) "ヘッダーを変更" else "ヘッダーを選択済み", color = c.accent) }
            }
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                TextButton(onClick = onDismiss) { Text("キャンセル", color = c.textMuted) }
                TextButton(onClick = {
                    if (name.isNotBlank()) vm.updateMyProfile(
                        name, bio.ifBlank { null }, location.ifBlank { null }, website.ifBlank { null }, avatar, header, onDismiss
                    )
                }) { Text("保存", color = c.accent, fontWeight = FontWeight.Bold) }
            }
        }
    }
}

@Composable
private fun EditProfileField(label: String, value: String, singleLine: Boolean = true, onChange: (String) -> Unit) {
    val c = North.colors
    Column(Modifier.padding(top = 12.dp)) {
        Text(label, color = c.textMuted, fontSize = 12.sp, modifier = Modifier.padding(bottom = 4.dp))
        BasicTextField(
            value = value, onValueChange = onChange, singleLine = singleLine,
            textStyle = androidx.compose.ui.text.TextStyle(color = c.text, fontSize = 15.sp),
            cursorBrush = SolidColor(c.accent),
            modifier = Modifier.fillMaxWidth().clip(androidx.compose.foundation.shape.RoundedCornerShape(10.dp)).background(c.bg).padding(12.dp),
        )
    }
}
