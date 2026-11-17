package dev.rec877.north.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.rec877.north.ListMembership
import dev.rec877.north.MainViewModel
import dev.rec877.north.Post
import dev.rec877.north.User
import dev.rec877.north.ui.theme.North

/** 編集ダイアログ用: 編集できると確認できた時点のポストと、その版識別子(ETag) */
data class EditSession(val post: Post, val text: String, val etag: String?)

/** ポストの編集。本文だけを書き換える（If-Match にETagを付けるので、ほかで変更されていれば412で止まる） */
@Composable
fun EditPostDialog(vm: MainViewModel, session: EditSession, onDismiss: () -> Unit) {
    val c = North.colors
    var text by remember(session) { mutableStateOf(session.text) }
    val weight = postWeight(text)
    val posting = vm.ui.posting
    var saving by remember { mutableStateOf(false) }
    val canSave = !saving && text.isNotBlank() && text != session.text && weight <= 280

    AlertDialog(
        onDismissRequest = { if (!saving) onDismiss() },
        containerColor = c.elev1,
        title = { Text("ポストを編集", color = c.text, fontWeight = FontWeight.ExtraBold) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                BasicTextField(
                    value = text,
                    onValueChange = { text = it },
                    textStyle = TextStyle(color = c.text, fontSize = 16.sp, lineHeight = 22.sp),
                    cursorBrush = SolidColor(c.accent),
                    modifier = Modifier.fillMaxWidth().heightIn(min = 120.dp)
                        .background(c.elev2, RoundedCornerShape(10.dp)).padding(12.dp),
                )
                Text(
                    "$weight/280",
                    color = if (weight > 280) c.danger else c.textMuted,
                    fontSize = 13.sp,
                    modifier = Modifier.align(Alignment.End),
                )
                Text("画像や投票は変更できません。本文だけが更新されます。", color = c.textMuted, fontSize = 12.sp)
            }
        },
        confirmButton = {
            TextButton(
                enabled = canSave && !posting,
                onClick = {
                    saving = true
                    vm.editPost(session.post, text, session.etag) { onDismiss() }
                },
            ) { Text(if (saving) "保存中…" else "保存", color = c.accent, fontWeight = FontWeight.Bold) }
        },
        dismissButton = { TextButton(onClick = onDismiss, enabled = !saving) { Text("キャンセル", color = c.textMuted) } },
    )
    // 保存に失敗したとき（エラーは画面上部のバーに出る）、もう一度押せるようにする
    LaunchedEffect(vm.ui.message) { if (vm.ui.message != null) saving = false }
}

/** 「リストへの追加/削除」。自分の所有リストのうち、そのアカウントが入っているものにチェックが付く */
@Composable
fun ListMembershipDialog(vm: MainViewModel, user: User, onDismiss: () -> Unit) {
    val c = North.colors
    var lists by remember(user.id) { mutableStateOf<List<ListMembership>?>(null) }
    var busyId by remember(user.id) { mutableStateOf<String?>(null) }

    LaunchedEffect(user.handle) {
        vm.launchSafely(onError = { lists = emptyList() }) {
            lists = vm.api.listMemberships(user.handle)
        }
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = c.elev1,
        title = { Text("@${user.handle} をリストに追加/削除", color = c.text, fontWeight = FontWeight.ExtraBold, fontSize = 17.sp) },
        text = {
            Column(Modifier.heightIn(max = 360.dp).verticalScroll(rememberScrollState())) {
                val items = lists
                when {
                    items == null -> Box(Modifier.fillMaxWidth().padding(24.dp), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator(color = c.accent, strokeWidth = 2.dp, modifier = Modifier.size(24.dp))
                    }
                    items.isEmpty() -> Text(
                        "追加できるリストがありません。メニューの「リスト」から作成してください。",
                        color = c.textMuted,
                    )
                    else -> items.forEachIndexed { index, list ->
                        if (index > 0) HorizontalDivider(color = c.border)
                        Row(
                            Modifier.fillMaxWidth()
                                .clickable(enabled = busyId == null) {
                                    busyId = list.id
                                    vm.launchSafely(onError = { busyId = null }) {
                                        if (list.contains) vm.api.removeListMember(list.id, user.handle)
                                        else vm.api.addListMember(list.id, user.handle)
                                        lists = lists?.map { if (it.id == list.id) it.copy(contains = !it.contains) else it }
                                        busyId = null
                                    }
                                }
                                .padding(vertical = 14.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(12.dp),
                        ) {
                            Column(Modifier.weight(1f)) {
                                Text(list.name, color = c.text, fontWeight = FontWeight.Bold, fontSize = 15.sp)
                                Text("${list.memberCount} メンバー${if (list.isPrivate) " · 非公開" else ""}", color = c.textMuted, fontSize = 12.sp)
                            }
                            Text(
                                if (list.contains) "✓ 追加済み" else "追加",
                                color = if (list.contains) c.accent else c.textMuted,
                                fontWeight = FontWeight.Bold,
                                fontSize = 14.sp,
                            )
                        }
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("閉じる", color = c.accent) } },
    )
}
