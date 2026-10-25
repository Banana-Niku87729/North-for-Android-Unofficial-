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
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil3.ImageLoader
import dev.rec877.north.MainViewModel
import dev.rec877.north.Post
import dev.rec877.north.PostPage
import dev.rec877.north.ScreenController
import dev.rec877.north.ui.theme.North

/** 引用・ブックマークのような、ポストを並べる補助画面。 */
private class PostCollectionController(
    private val vm: MainViewModel,
    private val key: String,
    private val fetch: suspend (String?) -> PostPage,
) : ScreenController {
    val posts = PagedPosts(fetch)
    private var started = false
    fun start() { if (!started) { started = true; vm.launchSafely { posts.first() } } }
    override fun patch(id: String, f: (Post) -> Post) = posts.patch(id, f)
    override fun removePost(id: String) = posts.remove(id)
    override fun onReplyPosted() = Unit
}

@Composable
fun PostCollectionScreen(
    vm: MainViewModel,
    loader: ImageLoader,
    id: String,
    kind: String,
    actions: PostActions,
) {
    val c = North.colors
    val title = if (kind == "quotes") "引用" else "ブックマーク"
    val controller = vm.screen("collection:$kind:$id") {
        PostCollectionController(vm, "$kind:$id") { cursor ->
            if (kind == "quotes") vm.api.quotes(id, cursor) else vm.api.bookmarks(cursor)
        }
    }
    var removed by remember { mutableStateOf<Set<String>>(emptySet()) }
    LaunchedEffect(controller) { controller.start() }

    Column(Modifier.fillMaxSize().background(c.bg)) {
        Row(Modifier.fillMaxWidth().padding(16.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(16.dp)) {
            Box(Modifier.size(28.dp).clickable { vm.back() }, contentAlignment = Alignment.Center) {
                NorthIcon(NIcons.Back, c.text, 22.dp, modifier = Modifier.size(22.dp))
            }
            Text(title, color = c.text, fontSize = 20.sp, fontWeight = FontWeight.ExtraBold)
        }
        val visible = controller.posts.items.filterNot { it.id in removed }
        LazyColumn(Modifier.fillMaxSize()) {
            items(visible, key = Post::id) { post ->
                if (kind == "bookmarks") {
                    Row(Modifier.fillMaxWidth().padding(end = 8.dp), verticalAlignment = Alignment.Top) {
                        Box(Modifier.weight(1f)) { PostCard(post, loader, actions) }
                        Text("削除", color = c.accent, fontSize = 13.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(top = 18.dp).clickable {
                            removed = removed + post.id
                            vm.launchSafely(onError = { removed = removed - post.id }) { vm.api.removeBookmark(post.id) }
                        })
                    }
                } else PostCard(post, loader, actions)
            }
            if (controller.posts.cursor != null) item("more") {
                LaunchedEffect(controller.posts.cursor) { vm.launchSafely { controller.posts.more() } }
                Box(Modifier.fillMaxWidth().padding(20.dp), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator(color = c.accent, strokeWidth = 2.dp, modifier = Modifier.size(24.dp))
                }
            } else if (controller.posts.loaded && visible.isEmpty()) item("empty") {
                Text(if (kind == "quotes") "引用ポストはありません" else "ブックマークはありません", color = c.textMuted, modifier = Modifier.fillMaxWidth().padding(32.dp))
            }
        }
    }
}
