package dev.rec877.north.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import coil3.ImageLoader
import dev.rec877.north.MainViewModel
import dev.rec877.north.Post
import dev.rec877.north.ui.theme.North

/** 引用ポストの作成画面。本文と画像を付けて、引用するポストのカードを下に添えて投稿する */
@Composable
fun QuoteDialog(vm: MainViewModel, post: Post, loader: ImageLoader, onClose: () -> Unit) {
    val c = North.colors
    // 引用カードの中のタップは何もしない（下の画面が開いてしまわないように）
    val noop = remember { PostActions({}, {}, {}, {}, { emptyList() }, {}, { _, _ -> }, { _, _ -> }, {}, {}, vm.imageClient) }

    Dialog(onDismissRequest = onClose, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Column(Modifier.fillMaxSize().background(c.bg)) {
            Row(
                Modifier.fillMaxWidth().height(53.dp).padding(horizontal = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                TextButton(onClick = onClose) { Text("キャンセル", color = c.text, fontSize = 15.sp) }
                Text(
                    "引用",
                    color = c.text,
                    fontSize = 17.sp,
                    fontWeight = FontWeight.ExtraBold,
                    modifier = Modifier.padding(start = 8.dp),
                )
            }
            HorizontalDivider(color = c.border)

            Column(Modifier.weight(1f).verticalScroll(rememberScrollState())) {
                Composer(
                    me = vm.ui.me,
                    loader = loader,
                    posting = vm.ui.posting,
                    onSubmit = { text, images, done ->
                        vm.post(text, images, null, post.id) {
                            done()
                            onClose()
                        }
                    },
                    extra = {
                        Box(Modifier.padding(bottom = 12.dp)) { QuotedPost(post.retweetOf ?: post, loader, noop) }
                    },
                )
            }
        }
    }
}
