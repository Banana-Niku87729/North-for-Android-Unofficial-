@file:OptIn(ExperimentalMaterial3Api::class)

package dev.rec877.north.ui

import android.app.Activity
import android.os.Build
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.rec877.north.Post
import dev.rec877.north.ui.theme.North
import androidx.core.view.WindowInsetsControllerCompat

/** リポストボタンを押したときに下から出る選択肢: 「リポスト（取り消し）」と「引用」 */
@Composable
fun RepostSheet(post: Post, onRepost: () -> Unit, onQuote: () -> Unit, onDismiss: () -> Unit) {
    val c = North.colors
    val view = LocalView.current
    // Bottom sheet がナビゲーションバーまで覆う時も、常に midnight のままにする。
    // Android の既定のコントラスト処理による白いバーを無効化する。
    DisposableEffect(view, c.elev1) {
        val window = (view.context as? Activity)?.window
        val previous = window?.navigationBarColor
        window?.navigationBarColor = c.elev1.toArgb()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) window?.isNavigationBarContrastEnforced = false
        window?.let { WindowInsetsControllerCompat(it, view).isAppearanceLightNavigationBars = false }
        onDispose { if (previous != null) window?.navigationBarColor = previous }
    }
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        containerColor = c.elev1,
    ) {
        Column(Modifier.padding(bottom = 24.dp)) {
            SheetRow(
                icon = NIcons.Repost,
                label = if (post.retweeted) "リポストを取り消す" else "リポスト",
                tint = if (post.retweeted) c.repost else c.text,
                onClick = onRepost,
            )
            SheetRow(icon = NIcons.Edit, label = "引用", tint = c.text, onClick = onQuote)
        }
    }
}

@Composable
private fun SheetRow(icon: NIcon, label: String, tint: Color, onClick: () -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 24.dp, vertical = 16.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        NorthIcon(icon, tint, 22.dp, modifier = Modifier.size(22.dp))
        Text(label, color = tint, fontSize = 17.sp)
    }
}
