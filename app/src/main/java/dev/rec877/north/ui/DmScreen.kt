package dev.rec877.north.ui

import android.net.Uri
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.repeatOnLifecycle
import coil3.ImageLoader
import coil3.compose.AsyncImage
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
    BoxWithConstraints(Modifier.fillMaxSize()) {
        val wide = maxWidth >= 840.dp
        Row(Modifier.fillMaxSize()) {
            if (wide || conversation == null) {
                Box(if (wide) Modifier.width(340.dp).fillMaxHeight() else Modifier.fillMaxSize()) {
                    key(requests) { DmInbox(vm, privateLoader, requests, { requests = it }, { selected = it }, conversation?.id) }
                }
            }
            if (wide) VerticalDivider(color = North.colors.border)
            if (conversation != null) {
                Box(Modifier.weight(1f)) {
                    key(conversation.id) {
                        DmChat(vm, privateLoader, conversation, visible, onBack = { selected = null })
                    }
                }
            } else if (wide) {
                Column(Modifier.weight(1f).fillMaxHeight().padding(48.dp), verticalArrangement = Arrangement.Center) {
                    Text("メッセージを選択", color = North.colors.text, fontSize = 28.sp, fontWeight = FontWeight.Bold)
                    Text("会話を選択してメッセージを確認しましょう。", color = North.colors.textMuted)
                }
            }
        }
    }
}

@Composable
private fun DmAvatar(user: User?, loader: ImageLoader, size: Dp) {
    Box(Modifier.size(size).clip(CircleShape).background(North.colors.elev2), contentAlignment = Alignment.Center) {
        if (user?.avatarUrl.isNullOrBlank()) {
            Text(user?.name?.take(1) ?: "DM", color = North.colors.textMuted, fontWeight = FontWeight.Bold)
        } else AsyncImage(model = Config.absUrl(user?.avatarUrl), imageLoader = loader, contentDescription = null,
            contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
    }
}

/** 名前が長くても認証バッジを押し出さず、他の画面と同じバッジを表示する。 */
@Composable
private fun DmName(name: String, user: User?, modifier: Modifier = Modifier) {
    Row(modifier, verticalAlignment = Alignment.CenterVertically) {
        Text(name, color = North.colors.text, fontWeight = FontWeight.Bold, fontSize = 15.sp,
            maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f, fill = false))
        user?.let { UserBadges(it) }
    }
}

@Composable
private fun DmTool(label: String, icon: NIcon, enabled: Boolean = true, onClick: () -> Unit) {
    IconButton(onClick = onClick, enabled = enabled, modifier = Modifier.semantics { contentDescription = label }) {
        NorthIcon(icon, North.colors.accent.copy(alpha = if (enabled) 1f else .4f), 22.dp)
    }
}

private val dmRefreshIcon = NIcon(strokes = listOf("M20 7v5h-5", "M20 12a8 8 0 1 0-2 5", "M20 7v5l-4-4"))
private val dmSendIcon = NIcon(strokes = listOf("m3 3 18 9-18 9 4-9Z", "M7 12h14"))

@Composable
private fun DmCount(count: Int) {
    if (count > 0) Box(Modifier.defaultMinSize(minWidth = 20.dp, minHeight = 20.dp).clip(CircleShape)
        .background(North.colors.accent).padding(horizontal = 5.dp), contentAlignment = Alignment.Center) {
        Text(if (count > 99) "99+" else count.toString(), color = North.colors.textOnAccent, fontSize = 11.sp, fontWeight = FontWeight.Bold)
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
    vm: MainViewModel, loader: ImageLoader, requests: Boolean, onRequests: (Boolean) -> Unit,
    onOpen: (DmConversation) -> Unit, selectedId: String?,
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
                vm.refreshDmUnread()
            } catch (e: CancellationException) { throw e
            } catch (e: Exception) { error = e.message ?: "DMを読み込めませんでした"; permission = (e as? ApiException)?.isPermission == true
            } finally { busy = false }
        }
    }
    LaunchedEffect(vm.dmUnread, selectedId) { load() }
    Column(Modifier.fillMaxSize().background(c.bg)) {
        Row(Modifier.fillMaxWidth().heightIn(min = 53.dp).padding(horizontal = 12.dp), verticalAlignment = Alignment.CenterVertically) {
            if (requests) DmTool("受信箱に戻る", NIcons.Back) { onRequests(false) }
            Text(if (requests) "リクエスト" else "メッセージ", color = c.text, fontSize = 20.sp, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
            DmTool("会話一覧を更新", dmRefreshIcon, !busy) { load() }
            DmTool("新しいメッセージ", NIcons.Edit, !busy) { creating = true }
        }
        HorizontalDivider(color = c.border)
        if (!requests) {
            Row(Modifier.fillMaxWidth().clickable { onRequests(true) }.padding(horizontal = 16.dp, vertical = 14.dp), verticalAlignment = Alignment.CenterVertically) {
                Text("メッセージリクエスト", color = c.text, fontSize = 14.sp, modifier = Modifier.weight(1f))
                DmCount(requestCount)
                Text(" ›", color = c.textMuted, fontSize = 20.sp)
            }
            HorizontalDivider(color = c.border)
        }
        DmError(error, permission, vm)
        if (busy) LinearProgressIndicator(Modifier.fillMaxWidth())
        if (!busy && error != null) TextButton(onClick = { load() }) { Text("再試行") }
        LazyColumn(Modifier.weight(1f)) {
            if (loaded && conversations.isEmpty()) item { Text("メッセージはありません", color = c.textMuted, modifier = Modifier.padding(24.dp)) }
            items(conversations, key = { it.id }) { conversation ->
                Row(Modifier.fillMaxWidth().background(if (conversation.id == selectedId) c.elev2 else c.bg)
                    .clickable { onOpen(conversation) }.heightIn(min = 74.dp).padding(horizontal = 12.dp, vertical = 10.dp),
                    horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
                    DmAvatar(conversation.participants.firstOrNull { it.id != vm.ui.me?.id }, loader, 48.dp)
                    Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            DmName(conversation.title(vm.ui.me?.id),
                                if (conversation.group) null else conversation.participants.firstOrNull { it.id != vm.ui.me?.id },
                                modifier = Modifier.weight(1f))
                            Text(relativeTime(conversation.lastMessage?.createdAt ?: conversation.updatedAt), color = c.textMuted, fontSize = 12.sp)
                        }
                        conversation.lastMessage?.let { message ->
                            val prefix = if (message.sender.id == vm.ui.me?.id) "あなた: " else ""
                            Text(prefix + message.text.ifBlank { if (message.media.isNotEmpty()) "添付メディア" else "メッセージ" },
                                maxLines = 1, overflow = TextOverflow.Ellipsis, fontSize = 13.sp, color = c.textMuted)
                        }
                    }
                    DmCount(conversation.unreadCount)
                }
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
            if (visible && !conversation.request && page.items.any { !it.read && it.sender.id != vm.ui.me?.id }) {
                vm.api.markDmRead(initial.id)
                vm.refreshDmUnread()
            }
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
        val participant = conversation.participants.firstOrNull { it.id != vm.ui.me?.id }
        Row(Modifier.fillMaxWidth().heightIn(min = 53.dp).padding(horizontal = 6.dp), verticalAlignment = Alignment.CenterVertically) {
            DmTool("会話一覧に戻る", NIcons.Back, !sending, onBack)
            Row(Modifier.weight(1f).clickable(enabled = participant != null && !conversation.group) { participant?.let { vm.openUser(it.handle) } },
                verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                DmAvatar(participant, loader, 32.dp)
                Column {
                    DmName(conversation.title(vm.ui.me?.id), if (conversation.group) null else participant)
                    if (!conversation.group && participant != null) Text("@${participant.handle}", color = c.textMuted, fontSize = 12.sp)
                }
            }
            DmTool("メッセージを更新", dmRefreshIcon, !loading && !sending) { refresh() }
        }
        HorizontalDivider(color = c.border)
        DmError(error, permission, vm)
        if (loading) LinearProgressIndicator(Modifier.fillMaxWidth())
        if (!loaded && error != null) TextButton(onClick = { refresh() }) { Text("再試行") }
        LazyColumn(Modifier.weight(1f).fillMaxWidth(), state = list, reverseLayout = true, contentPadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            items(messages, key = { it.id }) { message ->
                val mine = message.sender.id == vm.ui.me?.id
                if (message.system) {
                    Text(message.text, color = c.textMuted, fontSize = 13.sp, textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth().padding(8.dp))
                } else Column(Modifier.fillMaxWidth(), horizontalAlignment = if (mine) Alignment.End else Alignment.Start) {
                    Column(Modifier.fillMaxWidth(.78f), horizontalAlignment = if (mine) Alignment.End else Alignment.Start) {
                        if (conversation.group && !mine) DmName(message.sender.name, message.sender)
                        message.replyTo?.let { Text(if (it.deleted) "削除されたメッセージへの返信" else "返信: ${it.text}", color = c.textMuted, maxLines = 2) }
                        if (message.text.isNotEmpty()) Text(message.text, color = if (mine) c.textOnAccent else c.text, fontSize = 14.sp, lineHeight = 20.sp,
                            modifier = Modifier.background(if (mine) c.accent else c.elev2,
                                RoundedCornerShape(topStart = 20.dp, topEnd = 20.dp, bottomEnd = if (mine) 5.dp else 20.dp, bottomStart = if (mine) 20.dp else 5.dp))
                                .padding(horizontal = 14.dp, vertical = 10.dp))
                        message.tweet?.let { post -> Text("共有されたポスト: ${post.text}", color = c.accent, modifier = Modifier.clickable { vm.openPost(post.id) }) }
                        MediaGrid(message.media, loader, { viewer = message.media }, vm.imageClient)
                        Text(relativeTime(message.createdAt) + (if (message.editedAt != null) " · 編集済み" else "") + (if (mine && message.read) " · 既読" else ""), color = c.textMuted, fontSize = 11.sp, modifier = Modifier.padding(horizontal = 5.dp, vertical = 3.dp))
                    }
                }
            }
            if (cursor != null) item { TextButton(onClick = { refresh(true) }, enabled = !loading) { Text("以前のメッセージを読む") } }
            if (loaded && messages.isEmpty()) item { Text("メッセージを送って会話を始めましょう", color = c.textMuted) }
            if (loaded && cursor == null && participant != null && !conversation.group) item {
                Column(Modifier.fillMaxWidth().padding(top = 20.dp, bottom = 28.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                    Column(Modifier.clickable { vm.openUser(participant.handle) }, horizontalAlignment = Alignment.CenterHorizontally) {
                        DmAvatar(participant, loader, 64.dp)
                        DmName(participant.name, participant, modifier = Modifier.padding(top = 8.dp))
                        Text("@${participant.handle}", color = c.textMuted, fontSize = 13.sp)
                    }
                    participant.bio?.takeIf { it.isNotBlank() }?.let { Text(it, color = c.textMuted, textAlign = TextAlign.Center, fontSize = 14.sp, modifier = Modifier.padding(12.dp)) }
                }
                HorizontalDivider(color = c.border)
            }
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
            HorizontalDivider(color = c.border)
            attachment?.let { Row(verticalAlignment = Alignment.CenterVertically) {
                Text("画像・動画を1件添付", color = c.textMuted, modifier = Modifier.padding(start = 12.dp).weight(1f))
                TextButton(enabled = !sending, onClick = { attachment = null; uploaded = null; sendKey = UUID.randomUUID().toString() }) { Text("解除") }
            } }
            Row(Modifier.fillMaxWidth().padding(8.dp), verticalAlignment = Alignment.CenterVertically) {
                DmTool("画像または動画を追加", NIcons.Image, !sending) { picker.launch("*/*") }
                BasicTextField(value = text, onValueChange = { if (it.codePointCount(0, it.length) <= 10000) { text = it; sendKey = UUID.randomUUID().toString() } },
                    enabled = !sending, textStyle = TextStyle(color = c.text, fontSize = 14.sp, lineHeight = 20.sp), cursorBrush = SolidColor(c.accent),
                    modifier = Modifier.weight(1f).clip(RoundedCornerShape(21.dp)).background(c.elev2).padding(horizontal = 16.dp, vertical = 11.dp)
                        .semantics { contentDescription = "メッセージ" }, maxLines = 5,
                    decorationBox = { field -> Box { if (text.isEmpty()) Text("新しいメッセージを作成", color = c.textMuted, fontSize = 14.sp); field() } })
                DmTool(if (sending) "送信中" else "送信", dmSendIcon, enabled = !sending && !loading && (text.isNotBlank() || attachment != null), onClick = {
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
                })
                if (sending) CircularProgressIndicator(Modifier.size(18.dp), color = c.accent, strokeWidth = 2.dp)
            }
        }
    }
    if (rejectConfirm) AlertDialog(onDismissRequest = { if (!sending) rejectConfirm = false }, title = { Text("リクエストを拒否しますか？") }, confirmButton = {
        TextButton(enabled = !sending, onClick = {
            sending = true
            scope.launch { try { vm.api.rejectDm(initial.id); vm.refreshDmUnread(); rejectConfirm = false; onBack() } catch (e: CancellationException) { throw e } catch (e: Exception) { fail(e); rejectConfirm = false } finally { sending = false } }
        }) { Text("拒否") }
    }, dismissButton = { TextButton(enabled = !sending, onClick = { rejectConfirm = false }) { Text("キャンセル") } })
    viewer?.let { MediaViewer(it, 0, loader, vm.imageClient) { viewer = null } }
}
