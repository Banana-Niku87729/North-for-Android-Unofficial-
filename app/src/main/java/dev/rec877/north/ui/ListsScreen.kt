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
import androidx.compose.material3.CircularProgressIndicator
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.rec877.north.MainViewModel
import dev.rec877.north.NorthList
import dev.rec877.north.NorthLists
import dev.rec877.north.ui.theme.North

@Composable
fun ListsScreen(vm: MainViewModel) {
    val c = North.colors
    var data by remember { mutableStateOf<NorthLists?>(null) }
    var creating by remember { mutableStateOf(false) }
    fun load() { vm.launchSafely { data = vm.api.lists() } }
    LaunchedEffect(Unit) { load() }
    Column(Modifier.fillMaxSize().background(c.bg)) {
        Row(Modifier.fillMaxWidth().padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(28.dp).clickable { vm.back() }, contentAlignment = Alignment.Center) { NorthIcon(NIcons.Back, c.text, 22.dp, modifier = Modifier.size(22.dp)) }
            Text("リスト", color = c.text, fontSize = 20.sp, fontWeight = FontWeight.ExtraBold, modifier = Modifier.padding(start = 16.dp).weight(1f))
            TextButton(onClick = { creating = true }) { Text("作成", color = c.accent) }
        }
        val lists = data
        if (lists == null) Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator(color = c.accent, modifier = Modifier.size(28.dp)) }
        else {
            val all = (lists.items + lists.followed).distinctBy { it.id }
            if (all.isEmpty()) Text("リストはまだありません", color = c.textMuted, modifier = Modifier.padding(24.dp))
            all.forEach { list -> ListRow(list, vm) { load() } }
        }
    }
    if (creating) CreateListDialog(vm, onDismiss = { creating = false }) { creating = false; load() }
}

@Composable private fun ListRow(list: NorthList, vm: MainViewModel, onChanged: () -> Unit) {
    val c = North.colors
    Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) { Text(list.name, color = c.text, fontWeight = FontWeight.Bold, fontSize = 16.sp); Text("${list.memberCount} メンバー · ${list.followerCount} フォロワー", color = c.textMuted, fontSize = 13.sp); list.description?.let { Text(it, color = c.textMuted, fontSize = 13.sp) } }
        if (!list.ownedByViewer) TextButton(onClick = { vm.launchSafely { if (list.followedByViewer) vm.api.leaveList(list.id) else vm.api.followList(list.id); onChanged() } }) { Text(if (list.followedByViewer) "退出" else "参加", color = c.accent) }
    }
}

@Composable private fun CreateListDialog(vm: MainViewModel, onDismiss: () -> Unit, onDone: () -> Unit) {
    var name by remember { mutableStateOf("") }
    androidx.compose.material3.AlertDialog(onDismissRequest = onDismiss, title = { Text("リストを作成") }, text = { androidx.compose.foundation.text.BasicTextField(name, { name = it }, modifier = Modifier.fillMaxWidth()) }, confirmButton = { TextButton(onClick = { if (name.isNotBlank()) vm.launchSafely { vm.api.createList(name, null, false); onDone() } }) { Text("作成") } }, dismissButton = { TextButton(onClick = onDismiss) { Text("キャンセル") } })
}
