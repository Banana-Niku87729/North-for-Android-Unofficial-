package dev.rec877.north.ui

import android.net.Uri
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.repeatOnLifecycle
import coil3.ImageLoader
import dev.rec877.north.*
import dev.rec877.north.ui.theme.North
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.util.UUID

/** DMの状態と通信は画面・アカウントの寿命に紐付ける。本文はディスクに保存しない。 */
@Composable
fun DmScreen(vm: MainViewModel, loader: ImageLoader, visible: Boolean) {
    // DMの画像を公開タイムラインや別アカウントのキャッシュと共有しない。
    val privateLoader = remember(loader) { loader.newBuilder().memoryCache(null).diskCache(null).build() }
    DisposableEffect(privateLoader) { onDispose { privateLoader.shutdown() } }
    var selected by remember { mutableStateOf<DmConversation?>(null) }
    var requests by rememberSaveable { mutableStateOf(false) }
    BackHandler(enabled = selected != null && visible) { selected = null }
    val conversation = selected
    if (conversation == null) {
        key(requests) { DmInbox(vm, requests, { requests = it }, { selected = it }) }
    } else {
        key(conversation.id) {
            DmChat(vm, privateLoader, conversation, visible, onBack = { selected = null })
        }
    }
}

@Composable
private fun DmError(error: String?, permission: Boolean, vm: MainViewModel) {
    if (error != null) {
        Text(error, color = North.colors.danger, modifier = Modifier.padding(12.dp))
        if (permission) TextButton(onClick = { vm.logout() }) { Text("再ログインしてDM権限を許可") }
    }
}

@Composable
private fun DmInbox(
    vm: MainViewModel, requests: Boolean, onRequests: (Boolean) -> Unit,
    onOpen: (DmConversation) -> Unit,
) {
    val c = North.colors
    val scope = rememberCoroutineScope()
    var conversations by remember { mutableStateOf<List<DmConversation>>(emptyList()) }
    var cursor by remember { mutableStateOf<String?>(null) }
    var requestCount by remember { mutableIntStateOf(0) }
    var busy by remember { mutableStateOf(false) }
    var loaded by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    var permission by remember { mutableStateOf(false) }
    var creating by remember { mutableStateOf(false) }
    var handle by remember { mutableStateOf("") }
    fun load(more: Boolean = false) {
        if (busy) return
        busy = true
        scope.launch {
            try {
                error = null; permission = false
                val page = vm.api.dmConversations(requests, if (more) cursor else null)
                conversations = ((if (more) conversations else emptyList()) + page.items).distinctBy { it.id }
                cursor = page.nextCursor; requestCount = page.requestCount; loaded = true
            } catch (e: CancellationException) { throw e
            } catch (e: Exception) { error = e.message ?: "DMを読み込めませんでした"; permission = (e as? ApiException)?.isPermission == true
            } finally { busy = false }
        }
    }
    LaunchedEffect(Unit) { load() }
    Column(Modifier.fillMaxSize().background(c.bg)) {
        Row(Modifier.fillMaxWidth().padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
            Text("メッセージ", color = c.text, fontSize = 21.sp, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
            TextButton(onClick = { load() }, enabled = !busy) { Text("更新") }
            TextButton(onClick = { creating = true }, enabled = !busy) { Text("新規") }
        }
        NorthTabs(listOf("受信箱", "リクエスト" + if (requestCount > 0) " ($requestCount)" else ""), if (requests) 1 else 0) { onRequests(it == 1) }
        DmError(error, permission, vm)
        if (busy) LinearProgressIndicator(Modifier.fillMaxWidth())
        if (!busy && error != null) TextButton(onClick = { load() }) { Text("再試行") }
        LazyColumn(Modifier.weight(1f)) {
            if (loaded && conversations.isEmpty()) item { Text("メッセージはありません", color = c.textMuted, modifier = Modifier.padding(24.dp)) }
            items(conversations, key = { it.id }) { conversation ->
                Column(Modifier.fillMaxWidth().clickable { onOpen(conversation) }.padding(16.dp)) {
                    Row {
                        Text(conversation.title(vm.ui.me?.id), color = c.text, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
                        if (conversation.unreadCount > 0) Text("${conversation.unreadCount} 未読", color = c.accent)
                    }
                    conversation.lastMessage?.let { message ->
                        Text(message.text.ifBlank { if (message.media.isNotEmpty()) "添付メディア" else "メッセージ" }, maxLines = 2, color = c.textMuted)
                    }
                }
                HorizontalDivider(color = c.border)
            }
            if (cursor != null) item { TextButton(onClick = { load(true) }, enabled = !busy) { Text("さらに読み込む") } }
        }
    }
    if (creating) AlertDialog(
        onDismissRequest = { if (!busy) creating = false },
        title = { Text("新しいメッセージ") },
        text = { Column {
            OutlinedTextField(value = handle, onValueChange = { handle = it }, label = { Text("相手のユーザー名（@なし）") }, singleLine = true, enabled = !busy)
            error?.let { Text(it, color = c.danger) }
        } },
        confirmButton = { TextButton(enabled = !busy && handle.trim().removePrefix("@").length in 1..15, onClick = {
            busy = true
            scope.launch {
                try {
                    error = null
                    val conversation = vm.api.createDm(handle.trim().removePrefix("@"))
                    creating = false; onOpen(conversation)
                } catch (e: CancellationException) { throw e
                } catch (e: Exception) { error = e.message; permission = (e as? ApiException)?.isPermission == true
                } finally { busy = false }
            }
        }) { Text(if (busy) "処理中…" else "開く") } },
        dismissButton = { TextButton(onClick = { creating = false }, enabled = !busy) { Text("キャンセル") } },
    )
}

@Composable
private fun DmChat(vm: MainViewModel, loader: ImageLoader, initial: DmConversation, visible: Boolean, onBack: () -> Unit) {
    val c = North.colors
    val scope = rememberCoroutineScope()
    val resolver = LocalContext.current.contentResolver
    val owner = LocalLifecycleOwner.current
    var conversation by remember { mutableStateOf(initial) }
    var messages by remember { mutableStateOf<List<DmMessage>>(emptyList()) }
    var cursor by remember { mutableStateOf<String?>(null) }
    var loaded by remember { mutableStateOf(false) }
    var loading by remember { mutableStateOf(false) }
    var sending by remember { mutableStateOf(false) }
    var text by remember { mutableStateOf("") }
    var attachment by remember { mutableStateOf<Uri?>(null) }
    var uploaded by remember { mutableStateOf<String?>(null) }
    // 送信結果が不明な通信エラーでも、同じ入力の再送は同じキーを使う。
    var sendKey by remember { mutableStateOf(UUID.randomUUID().toString()) }
    var error by remember { mutableStateOf<String?>(null) }
    var permission by remember { mutableStateOf(false) }
    var rejectConfirm by remember { mutableStateOf(false) }
    var viewer by remember { mutableStateOf<List<Media>?>(null) }
    val list = rememberLazyListState()
    fun fail(e: Exception) { error = e.message ?: "DMの処理に失敗しました"; permission = (e as? ApiException)?.isPermission == true }
    suspend fun load(more: Boolean = false) {
        if (loading) return
        loading = true
        try {
            val page = vm.api.dmMessages(initial.id, if (more) cursor else null)
            // 最新ページの削除を反映しつつ、読み込み済みの古い履歴を保持する。
            val oldest = page.items.minOfOrNull { it.createdAt }
            val retained = if (more) messages else messages.filter { oldest != null && it.createdAt < oldest }
            messages = (page.items + retained).distinctBy { it.id }.sortedByDescending { it.createdAt }
            if (more || !loaded || retained.isEmpty()) cursor = page.nextCursor
            conversation = page.conversation ?: conversation; loaded = true
            if (visible && !conversation.request && page.items.any { !it.read && it.sender.id != vm.ui.me?.id }) vm.api.markDmRead(initial.id)
        } finally { loading = false }
    }
    fun refresh(more: Boolean = false) {
        scope.launch { try { error = null; permission = false; load(more) } catch (e: CancellationException) { throw e } catch (e: Exception) { fail(e) } }
    }
    LaunchedEffect(visible, owner) {
        if (visible) owner.repeatOnLifecycle(Lifecycle.State.RESUMED) {
            while (true) {
                try { if (!sending) load() } catch (e: CancellationException) { throw e } catch (e: Exception) { fail(e); break }
                delay(30_000)
            }
        }
    }
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        if (uri != null) {
            val mime = resolver.getType(uri).orEmpty()
            if (mime.startsWith("image/") || mime.startsWith("video/")) {
                attachment = uri; uploaded = null; sendKey = UUID.randomUUID().toString()
            } else error = "画像または動画を選択してください"
        }
    }
    Column(Modifier.fillMaxSize().background(c.bg).imePadding()) {
        Row(Modifier.fillMaxWidth().padding(8.dp), verticalAlignment = Alignment.CenterVertically) {
            TextButton(onClick = onBack, enabled = !sending) { Text("戻る") }
            Text(conversation.title(vm.ui.me?.id), color = c.text, fontWeight = FontWeight.Bold, maxLines = 1, modifier = Modifier.weight(1f))
            TextButton(onClick = { refresh() }, enabled = !loading && !sending) { Text("更新") }
        }
        DmError(error, permission, vm)
        if (loading) LinearProgressIndicator(Modifier.fillMaxWidth())
        if (!loaded && error != null) TextButton(onClick = { refresh() }) { Text("再試行") }
        LazyColumn(Modifier.weight(1f).fillMaxWidth(), state = list, reverseLayout = true, contentPadding = PaddingValues(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            items(messages, key = { it.id }) { message ->
                val mine = message.sender.id == vm.ui.me?.id
                Column(Modifier.fillMaxWidth(), horizontalAlignment = if (mine) Alignment.End else Alignment.Start) {
                    Column(Modifier.widthIn(max = 320.dp).background(if (mine) c.elev2 else c.elev1, RoundedCornerShape(14.dp)).padding(12.dp)) {
                        Text(message.sender.name, color = c.textMuted, fontSize = 12.sp)
                        message.replyTo?.let { Text(if (it.deleted) "削除されたメッセージへの返信" else "返信: ${it.text}", color = c.textMuted, maxLines = 2) }
                        if (message.text.isNotEmpty()) Text(message.text, color = c.text)
                        message.tweet?.let { post -> Text("共有されたポスト: ${post.text}", color = c.accent, modifier = Modifier.clickable { vm.openPost(post.id) }) }
                        MediaGrid(message.media, loader, { viewer = message.media }, vm.imageClient)
                        Text(absoluteTime(message.createdAt) + (if (message.editedAt != null) " · 編集済み" else "") + (if (mine && message.read) " · 既読" else ""), color = c.textMuted, fontSize = 10.sp)
                    }
                }
            }
            if (cursor != null) item { TextButton(onClick = { refresh(true) }, enabled = !loading) { Text("以前のメッセージを読む") } }
            if (loaded && messages.isEmpty()) item { Text("メッセージを送って会話を始めましょう", color = c.textMuted) }
        }
        if (conversation.request) {
            Text("メッセージリクエスト", color = c.textMuted, modifier = Modifier.padding(horizontal = 16.dp))
            Row {
                TextButton(enabled = !sending && !loading, onClick = {
                    sending = true
                    scope.launch { try { vm.api.acceptDm(initial.id); conversation = conversation.copy(request = false); error = null; load() } catch (e: CancellationException) { throw e } catch (e: Exception) { fail(e) } finally { sending = false } }
                }) { Text("承認") }
                TextButton(enabled = !sending, onClick = { rejectConfirm = true }) { Text("拒否", color = c.danger) }
            }
        } else {
            attachment?.let { Row(verticalAlignment = Alignment.CenterVertically) {
                Text("画像・動画を1件添付", color = c.textMuted, modifier = Modifier.padding(start = 12.dp).weight(1f))
                TextButton(enabled = !sending, onClick = { attachment = null; uploaded = null; sendKey = UUID.randomUUID().toString() }) { Text("解除") }
            } }
            Row(Modifier.fillMaxWidth().padding(8.dp), verticalAlignment = Alignment.CenterVertically) {
                TextButton(enabled = !sending, onClick = { picker.launch("*/*") }) { Text("添付") }
                OutlinedTextField(value = text, onValueChange = { if (it.codePointCount(0, it.length) <= 10000) { text = it; sendKey = UUID.randomUUID().toString() } }, enabled = !sending, placeholder = { Text("メッセージ") }, modifier = Modifier.weight(1f), maxLines = 5)
                TextButton(enabled = !sending && !loading && (text.isNotBlank() || attachment != null), onClick = {
                    sending = true
                    scope.launch {
                        try {
                            error = null; permission = false
                            if (uploaded == null) attachment?.let { uploaded = vm.api.awaitMediaReady(vm.api.uploadMedia(resolver, it, "dm")).id }
                            val sent = vm.api.sendDm(initial.id, text, uploaded, sendKey)
                            messages = (listOf(sent) + messages).distinctBy { it.id }.sortedByDescending { it.createdAt }
                            text = ""; attachment = null; uploaded = null; sendKey = UUID.randomUUID().toString()
                            list.animateScrollToItem(0)
                        } catch (e: CancellationException) { throw e } catch (e: Exception) { fail(e) } finally { sending = false }
                    }
                }) { Text(if (sending) "送信中" else "送信") }
            }
        }
    }
    if (rejectConfirm) AlertDialog(onDismissRequest = { if (!sending) rejectConfirm = false }, title = { Text("リクエストを拒否しますか？") }, confirmButton = {
        TextButton(enabled = !sending, onClick = {
            sending = true
            scope.launch { try { vm.api.rejectDm(initial.id); rejectConfirm = false; onBack() } catch (e: CancellationException) { throw e } catch (e: Exception) { fail(e); rejectConfirm = false } finally { sending = false } }
        }) { Text("拒否") }
    }, dismissButton = { TextButton(enabled = !sending, onClick = { rejectConfirm = false }) { Text("キャンセル") } })
    viewer?.let { MediaViewer(it, 0, loader, vm.imageClient) { viewer = null } }
}
