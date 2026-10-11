package dev.rec877.north.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil3.ImageLoader
import dev.rec877.north.MainViewModel
import dev.rec877.north.ScreenController
import dev.rec877.north.User
import dev.rec877.north.UserPage
import dev.rec877.north.ui.theme.North

class PagedUsers(private val fetch: suspend (String?) -> UserPage) {
    var items by mutableStateOf<List<User>>(emptyList()); private set
    var cursor by mutableStateOf<String?>(null); private set
    var loading by mutableStateOf(false); private set
    var loaded by mutableStateOf(false); private set
    suspend fun first() { loading = true; try { fetch(null).also { items = it.items.distinctBy(User::id); cursor = it.nextCursor; loaded = true } } finally { loading = false } }
    suspend fun more() { val next = cursor ?: return; if (loading) return; loading = true; try { fetch(next).also { items = (items + it.items).distinctBy(User::id); cursor = it.nextCursor } } finally { loading = false } }
}

class UserListController(vm: MainViewModel, handle: String, kind: String) : ScreenController {
    val users = PagedUsers { cursor -> when (kind) { "followers" -> vm.api.userFollowers(handle, cursor); "likes" -> vm.api.postLikes(handle, cursor); "retweets" -> vm.api.retweets(handle, cursor); else -> vm.api.userFollowing(handle, cursor) } }
    private var started = false
    fun start(vm: MainViewModel) { if (!started) { started = true; vm.launchSafely { users.first() } } }
    override fun patch(id: String, f: (dev.rec877.north.Post) -> dev.rec877.north.Post) = Unit
    override fun onReplyPosted() = Unit
}

@Composable
fun UserListScreen(vm: MainViewModel, loader: ImageLoader, handle: String, kind: String) {
    val c = North.colors
    val ctl = vm.screen("follows:$handle:$kind") { UserListController(vm, handle, kind) }
    val listState = rememberLazyListState()
    var initialPositionApplied by androidx.compose.runtime.remember(handle, kind) { mutableStateOf(false) }
    LaunchedEffect(ctl) { ctl.start(vm) }
    // 初回ページが届いた直後にだけ先頭に置く。再描画・続きを読む操作で末尾へ飛ばさない。
    LaunchedEffect(ctl.users.loaded, ctl.users.items.size) {
        if (ctl.users.loaded && !initialPositionApplied) {
            listState.scrollToItem(0)
            initialPositionApplied = true
        }
    }
    Column(Modifier.fillMaxSize().background(c.bg)) {
        Row(Modifier.fillMaxWidth().padding(16.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(16.dp)) {
            Box(Modifier.size(28.dp).clickable { vm.back() }, contentAlignment = Alignment.Center) { NorthIcon(NIcons.Back, c.text, 22.dp, modifier = Modifier.size(22.dp)) }
            Column { Text(if (kind == "followers") "フォロワー" else if (kind == "likes") "いいね" else if (kind == "retweets") "リポスト" else "フォロー中", color = c.text, fontSize = 20.sp, fontWeight = FontWeight.ExtraBold); Text(if (kind == "likes") "このポストにいいねしたユーザー" else if (kind == "retweets") "このポストをリポストしたユーザー" else "@$handle", color = c.textMuted, fontSize = 14.sp) }
        }
        HorizontalDivider(color = c.border)
        LazyColumn(Modifier.fillMaxSize(), state = listState) {
            items(ctl.users.items, key = User::id) { user ->
                Row(Modifier.fillMaxWidth().clickable { vm.openUser(user.handle) }.padding(16.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    Avatar(user, 48.dp, loader, Modifier.size(48.dp))
                    Column(Modifier.weight(1f)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(user.name, color = c.text, fontSize = 16.sp, fontWeight = FontWeight.Bold)
                            UserBadges(user)
                        }
                        Text("@${user.handle}", color = c.textMuted, fontSize = 14.sp)
                        user.bio?.takeIf(String::isNotBlank)?.let { Text(it, color = c.text, fontSize = 14.sp, modifier = Modifier.padding(top = 4.dp)) }
                    }
                }
            }
            if (ctl.users.cursor != null || ctl.users.loading) item("more") { Box(Modifier.fillMaxWidth().padding(24.dp).clickable { vm.launchSafely { ctl.users.more() } }, contentAlignment = Alignment.Center) { if (ctl.users.loading) CircularProgressIndicator(color = c.accent, strokeWidth = 2.dp, modifier = Modifier.size(24.dp)) else Text("さらに表示", color = c.accent, fontWeight = FontWeight.Bold) } }
            else if (ctl.users.loaded && ctl.users.items.isEmpty()) item("empty") { Text("まだいません", color = c.textMuted, modifier = Modifier.fillMaxWidth().padding(32.dp)) }
        }
    }
}
