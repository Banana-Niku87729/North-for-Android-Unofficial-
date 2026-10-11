package dev.rec877.north.ui

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.TextButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil3.ImageLoader
import coil3.compose.AsyncImage
import dev.rec877.north.User
import dev.rec877.north.PollInput
import dev.rec877.north.ui.theme.North

private const val MAX_IMAGES = 4
private const val MAX_WEIGHT = 280

/**
 * サイトの .composer を再現。下段に「画像を追加」(アクセント色)と「ポストする」(白ピル)。
 * 画像はAndroidのフォトピッカー（権限不要）で最大4枚。送信時にアップロードしてから投稿する。
 */
@Composable
fun Composer(
    me: User?,
    loader: ImageLoader,
    posting: Boolean,
    onSubmit: (text: String, images: List<Uri>, onDone: () -> Unit) -> Unit,
    /** 画像サムネイルの下に差し込む内容（引用するポストのカードなど） */
    extra: (@Composable () -> Unit)? = null,
    onDraftChanged: (text: String, images: List<Uri>) -> Unit = { _, _ -> },
    clearDraftVersion: Int = 0,
    drafts: List<dev.rec877.north.Draft> = emptyList(),
    draftsLoading: Boolean = false,
    onOpenDrafts: (() -> Unit)? = null,
    onSubmitWithSavedMediaIds: ((text: String, images: List<Uri>, savedMediaIds: List<String>, onDone: () -> Unit) -> Unit)? = null,
) {
    val c = North.colors
    var text by rememberSaveable { mutableStateOf("") }
    var images by remember { mutableStateOf<List<Uri>>(emptyList()) }
    var restoredMediaIds by remember { mutableStateOf<List<String>>(emptyList()) }
    var draftPickerOpen by remember { mutableStateOf(false) }

    LaunchedEffect(clearDraftVersion) {
        if (clearDraftVersion > 0) { text = ""; images = emptyList() }
    }

    val picker = rememberLauncherForActivityResult(
        ActivityResultContracts.PickMultipleVisualMedia(MAX_IMAGES)
    ) { picked ->
        val next = (images + picked).distinct().take(MAX_IMAGES)
        images = next
        onDraftChanged(text, next)
    }

    val weight = postWeight(text)
    val canSend = !posting && (text.isNotBlank() || images.isNotEmpty() || restoredMediaIds.isNotEmpty()) && weight <= MAX_WEIGHT

    Row(
        Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        if (me != null) Avatar(me, 48.dp, loader)
        else Box(Modifier.size(48.dp).clip(CircleShape).background(c.elev3))

        Column(Modifier.weight(1f)) {
            BasicTextField(
                value = text,
                onValueChange = { text = it; onDraftChanged(it, images) },
                textStyle = TextStyle(color = c.text, fontSize = 20.sp, lineHeight = 26.sp),
                cursorBrush = SolidColor(c.accent),
                modifier = Modifier.fillMaxWidth().heightIn(min = 52.dp).padding(vertical = 12.dp),
                decorationBox = { inner ->
                    Box {
                        if (text.isEmpty()) {
                            Text("いまどうしてる？", color = c.textMuted, fontSize = 20.sp, lineHeight = 26.sp)
                        }
                        inner()
                    }
                },
            )

            if (images.isNotEmpty()) {
                Row(
                    Modifier.fillMaxWidth().padding(bottom = 8.dp).horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    images.forEach { uri ->
                        Box(Modifier.size(96.dp).clip(RoundedCornerShape(12.dp)).background(c.elev2)) {
                            AsyncImage(
                                model = uri,
                                imageLoader = loader,
                                contentDescription = null,
                                contentScale = ContentScale.Crop,
                                modifier = Modifier.fillMaxSize(),
                            )
                            Box(
                                Modifier
                                    .align(Alignment.TopEnd)
                                    .padding(4.dp)
                                    .size(24.dp)
                                    .clip(CircleShape)
                                    .background(Color(0xAA000000))
                                    .clickable { val next = images - uri; images = next; onDraftChanged(text, next) },
                                contentAlignment = Alignment.Center,
                            ) { NorthIcon(NIcons.Close, Color.White, 14.dp, modifier = Modifier.size(14.dp)) }
                        }
                    }
                }
            }

            extra?.invoke()

            if (restoredMediaIds.isNotEmpty()) Text("保存済みの添付 ${restoredMediaIds.size} 件", color = c.textMuted, fontSize = 13.sp, modifier = Modifier.padding(bottom = 8.dp))

            HorizontalDivider(color = c.border)
            Row(
                Modifier.fillMaxWidth().padding(top = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                val canAdd = images.size < MAX_IMAGES && !posting
                Box(
                    Modifier
                        .size(36.dp)
                        .clip(CircleShape)
                        .alpha(if (canAdd) 1f else 0.4f)
                        .clickable(enabled = canAdd) {
                            picker.launch(
                                PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                            )
                        },
                    contentAlignment = Alignment.Center,
                ) { NorthIcon(NIcons.Image, c.accent, 22.dp, modifier = Modifier.size(22.dp)) }

                Spacer(Modifier.weight(1f))

                if (text.isNotEmpty()) {
                    Text(
                        "$weight/$MAX_WEIGHT",
                        color = if (weight > MAX_WEIGHT) c.danger else c.textMuted,
                        fontSize = 13.sp,
                    )
                    Spacer(Modifier.width(12.dp))
                }

                // TLの下書きは送信ボタンのすぐ左に置き、片手で続けて操作しやすくする。
                if (onOpenDrafts != null) {
                    Text("下書き", color = c.accent, fontSize = 14.sp, fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(end = 12.dp).clickable { onOpenDrafts(); draftPickerOpen = true })
                }

                // サイトの .composer__submit: 白ピル + 濃い文字
                Box(
                    Modifier
                        .height(36.dp)
                        .clip(CircleShape)
                        .background(c.post.copy(alpha = if (canSend) 1f else 0.5f))
                        .clickable(enabled = canSend) {
                            val done = { text = ""; images = emptyList(); restoredMediaIds = emptyList(); onDraftChanged("", emptyList()) }
                            if (onSubmitWithSavedMediaIds != null) onSubmitWithSavedMediaIds(text, images, restoredMediaIds, done)
                            else onSubmit(text, images, done)
                        }
                        .padding(horizontal = 16.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        if (posting) "送信中…" else "ポストする",
                        color = c.textOnPost,
                        fontSize = 15.sp,
                        fontWeight = FontWeight.Bold,
                    )
                }
            }
        }
    }
    HorizontalDivider(color = c.border)
    if (draftPickerOpen) AlertDialog(
        onDismissRequest = { draftPickerOpen = false },
        containerColor = c.elev1,
        title = { Text("下書き", color = c.text, fontWeight = FontWeight.ExtraBold) },
        text = {
            Column {
                if (draftsLoading) Text("読み込み中…", color = c.textMuted)
                else if (drafts.isEmpty()) Text("保存された下書きはありません", color = c.textMuted)
                else drafts.take(10).forEach { draft ->
                    val item = draft.items.firstOrNull()
                    Text(item?.text?.ifBlank { "画像のみの下書き" } ?: "空の下書き", color = c.text, maxLines = 2,
                        modifier = Modifier.fillMaxWidth().clickable { text = item?.text.orEmpty(); images = emptyList(); restoredMediaIds = item?.media?.map { it.id }.orEmpty(); onDraftChanged(text, images); draftPickerOpen = false }.padding(vertical = 12.dp))
                    HorizontalDivider(color = c.border)
                }
            }
        },
        confirmButton = { TextButton(onClick = { draftPickerOpen = false }) { Text("閉じる", color = c.accent) } },
    )
}

/** FABから開く、画面全体を使うモバイル向け投稿画面。 */
@Composable
fun FullScreenComposer(
    me: User?,
    loader: ImageLoader,
    posting: Boolean,
    onClose: () -> Unit,
    drafts: List<dev.rec877.north.Draft>,
    draftsLoading: Boolean,
    onOpenDrafts: () -> Unit,
    onSaveDraft: (String, List<Uri>, List<String>, () -> Unit) -> Unit,
    onSubmit: (text: String, images: List<Uri>, existingMediaIds: List<String>, poll: PollInput?, onDone: () -> Unit) -> Unit,
) {
    val c = North.colors
    var text by rememberSaveable { mutableStateOf("") }
    var images by remember { mutableStateOf<List<Uri>>(emptyList()) }
    var restoredMediaIds by remember { mutableStateOf<List<String>>(emptyList()) }
    var draftPickerOpen by remember { mutableStateOf(false) }
    var confirmSaveOnClose by remember { mutableStateOf(false) }
    var pollOptions by remember { mutableStateOf<List<String>?>(null) }
    var pollEditorOpen by remember { mutableStateOf(false) }
    var pollDays by remember { mutableStateOf("1") }
    var pollHours by remember { mutableStateOf("0") }
    var pollMinutes by remember { mutableStateOf("0") }
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.PickMultipleVisualMedia(MAX_IMAGES)) { picked ->
        images = (images + picked).distinct().take(MAX_IMAGES)
    }
    val weight = postWeight(text)
    val normalizedPoll = pollOptions?.map { it.trim() }
    val submittedOptions = normalizedPoll?.filter { it.isNotBlank() }
    val durationMinutes = ((pollDays.toIntOrNull() ?: -1) * 1440) + ((pollHours.toIntOrNull() ?: -1) * 60) + (pollMinutes.toIntOrNull() ?: -1)
    val pollValid = submittedOptions == null || (submittedOptions.size in 2..4 && submittedOptions.all { it.length <= 25 } && durationMinutes in 5..10080)
    val canSend = !posting && (text.isNotBlank() || images.isNotEmpty() || restoredMediaIds.isNotEmpty() || normalizedPoll != null) && weight <= MAX_WEIGHT && pollValid

    Column(
        Modifier.fillMaxSize().background(c.bg).windowInsetsPadding(WindowInsets.safeDrawing).imePadding()
    ) {
        Row(Modifier.fillMaxWidth().padding(horizontal = 18.dp, vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(40.dp).clip(CircleShape).clickable {
                if (text.isNotBlank() || images.isNotEmpty() || restoredMediaIds.isNotEmpty() || pollOptions != null) confirmSaveOnClose = true else onClose()
            }, contentAlignment = Alignment.Center) {
                NorthIcon(NIcons.Close, c.text, 26.dp, modifier = Modifier.size(26.dp))
            }
            Spacer(Modifier.weight(1f))
            Text("下書き", color = c.accent, fontSize = 16.sp, fontWeight = FontWeight.Bold, modifier = Modifier.clickable { onOpenDrafts(); draftPickerOpen = true })
            Spacer(Modifier.width(18.dp))
            Box(
                Modifier.height(44.dp).clip(CircleShape).background(c.accent.copy(alpha = if (canSend) 1f else .45f))
                    .clickable(enabled = canSend) {
                        val poll = submittedOptions?.let { PollInput(it, durationMinutes) }
                        onSubmit(text, images, restoredMediaIds, poll) { text = ""; images = emptyList(); restoredMediaIds = emptyList(); pollOptions = null }
                    }
                    .padding(horizontal = 20.dp), contentAlignment = Alignment.Center,
            ) { Text(if (posting) "送信中…" else "ポストする", color = c.textOnAccent, fontWeight = FontWeight.ExtraBold, fontSize = 16.sp) }
        }
        HorizontalDivider(color = c.border)
        Row(Modifier.fillMaxWidth().weight(1f).padding(horizontal = 18.dp, vertical = 18.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            if (me != null) Avatar(me, 48.dp, loader) else Box(Modifier.size(48.dp).clip(CircleShape).background(c.elev3))
            Column(Modifier.weight(1f)) {
                BasicTextField(
                    value = text,
                    onValueChange = { text = it },
                    textStyle = TextStyle(color = c.text, fontSize = 22.sp, lineHeight = 30.sp),
                    cursorBrush = SolidColor(c.accent),
                    modifier = Modifier.fillMaxWidth().weight(1f),
                    decorationBox = { inner -> Box { if (text.isEmpty()) Text("いまどうしてる？", color = c.textMuted, fontSize = 22.sp); inner() } },
                )
                if (images.isNotEmpty()) {
                    Row(Modifier.fillMaxWidth().padding(top = 8.dp).horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        images.forEach { uri ->
                            Box(Modifier.size(112.dp).clip(RoundedCornerShape(14.dp)).background(c.elev2)) {
                                AsyncImage(model = uri, imageLoader = loader, contentDescription = null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
                                Box(Modifier.align(Alignment.TopEnd).padding(6.dp).size(28.dp).clip(CircleShape).background(Color(0xBB000000)).clickable { images = images - uri }, contentAlignment = Alignment.Center) {
                                    NorthIcon(NIcons.Close, Color.White, 16.dp, modifier = Modifier.size(16.dp))
                                }
                            }
                        }
                    }
                }
                if (restoredMediaIds.isNotEmpty()) Text("保存済みの添付 ${restoredMediaIds.size} 件", color = c.textMuted, fontSize = 13.sp, modifier = Modifier.padding(top = 8.dp))
                submittedOptions?.let { options ->
                    Text("投票: ${options.joinToString(" / ")}", color = c.textMuted, fontSize = 14.sp, modifier = Modifier.padding(top = 10.dp))
                }
            }
        }
        HorizontalDivider(color = c.border)
        Row(Modifier.fillMaxWidth().padding(horizontal = 18.dp, vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
            val canAdd = !posting && images.size < MAX_IMAGES
            Box(Modifier.size(44.dp).clip(CircleShape).alpha(if (canAdd) 1f else .4f).clickable(enabled = canAdd) { picker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)) }, contentAlignment = Alignment.Center) {
                NorthIcon(NIcons.Image, c.accent, 25.dp, modifier = Modifier.size(25.dp))
            }
            Text(if (pollOptions == null) "投票を追加" else "投票を編集", color = c.accent, fontSize = 14.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(start = 10.dp).clickable { pollEditorOpen = true })
            Spacer(Modifier.weight(1f))
            if (text.isNotEmpty()) { Spacer(Modifier.width(12.dp)); Text("$weight/$MAX_WEIGHT", color = if (weight > MAX_WEIGHT) c.danger else c.textMuted, fontSize = 13.sp) }
        }
    }
    if (draftPickerOpen) AlertDialog(
        onDismissRequest = { draftPickerOpen = false },
        containerColor = c.elev1,
        title = { Text("下書き", color = c.text, fontWeight = FontWeight.ExtraBold) },
        text = {
            Column {
                if (draftsLoading) Text("読み込み中…", color = c.textMuted)
                else if (drafts.isEmpty()) Text("保存された下書きはありません", color = c.textMuted)
                else drafts.take(10).forEach { draft ->
                    val item = draft.items.firstOrNull()
                    Text(item?.text?.ifBlank { "画像のみの下書き" } ?: "空の下書き", color = c.text, maxLines = 2,
                        modifier = Modifier.fillMaxWidth().clickable { text = item?.text.orEmpty(); images = emptyList(); restoredMediaIds = item?.media?.map { it.id }.orEmpty(); draftPickerOpen = false }.padding(vertical = 12.dp))
                    HorizontalDivider(color = c.border)
                }
            }
        },
        confirmButton = { TextButton(onClick = { draftPickerOpen = false }) { Text("閉じる", color = c.accent) } },
    )
    if (pollEditorOpen) AlertDialog(
        onDismissRequest = { pollEditorOpen = false },
        containerColor = c.elev1,
        title = { Text("投票を作成", color = c.text, fontWeight = FontWeight.ExtraBold) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text("選択肢は2〜4個、各25文字以内です。未入力の選択肢は投稿時に除かれます。", color = c.textMuted, fontSize = 13.sp)
                val editing = pollOptions ?: listOf("", "", "", "")
                editing.forEachIndexed { index, option ->
                    BasicTextField(value = option, onValueChange = { value ->
                        val changed = editing.toMutableList(); changed[index] = value.take(25); pollOptions = changed
                    }, textStyle = TextStyle(color = c.text, fontSize = 16.sp), cursorBrush = SolidColor(c.accent), modifier = Modifier.fillMaxWidth().background(c.elev2, RoundedCornerShape(10.dp)).padding(12.dp), decorationBox = { inner -> Box { if (option.isEmpty()) Text("選択肢 ${index + 1}", color = c.textMuted); inner() } })
                }
                HorizontalDivider(color = c.border)
                Text("アンケート期間", color = c.accent, fontSize = 13.sp, fontWeight = FontWeight.Bold)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    PollDurationField("日", pollDays) { pollDays = it.filter(Char::isDigit).take(2) }
                    PollDurationField("時間", pollHours) { pollHours = it.filter(Char::isDigit).take(2) }
                    PollDurationField("分", pollMinutes) { pollMinutes = it.filter(Char::isDigit).take(2) }
                }
                if (!pollValid) Text("選択肢を2〜4個入力し、期間は5分〜7日の範囲にしてください。", color = c.danger, fontSize = 13.sp)
            }
        },
        confirmButton = { TextButton(onClick = { pollOptions = (pollOptions ?: listOf("", "", "", "")); pollEditorOpen = false }) { Text("完了", color = c.accent) } },
        dismissButton = { TextButton(onClick = { pollEditorOpen = false }) { Text("閉じる", color = c.textMuted) } },
    )
    if (confirmSaveOnClose) AlertDialog(
        onDismissRequest = { confirmSaveOnClose = false },
        containerColor = c.elev1,
        title = { Text("下書きを保存しますか？", color = c.text, fontWeight = FontWeight.ExtraBold) },
        text = { Text("投稿せずに閉じると、入力内容と添付を下書きへ保存できます。", color = c.textMuted) },
        confirmButton = { TextButton(onClick = { onSaveDraft(text, images, restoredMediaIds) { confirmSaveOnClose = false; onClose() } }) { Text("保存して閉じる", color = c.accent, fontWeight = FontWeight.Bold) } },
        dismissButton = { TextButton(onClick = { confirmSaveOnClose = false; onClose() }) { Text("保存しない", color = c.danger) } },
    )
}

@Composable
private fun RowScope.PollDurationField(label: String, value: String, onValueChange: (String) -> Unit) {
    val c = North.colors
    Column(Modifier.weight(1f)) {
        Text(label, color = c.textMuted, fontSize = 12.sp, modifier = Modifier.padding(bottom = 4.dp))
        BasicTextField(value = value, onValueChange = onValueChange, textStyle = TextStyle(color = c.text, fontSize = 16.sp), cursorBrush = SolidColor(c.accent), modifier = Modifier.fillMaxWidth().background(c.elev2, RoundedCornerShape(8.dp)).padding(10.dp))
    }
}
