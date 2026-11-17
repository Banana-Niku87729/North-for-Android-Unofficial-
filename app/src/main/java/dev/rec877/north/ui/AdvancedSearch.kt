package dev.rec877.north.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import dev.rec877.north.ui.theme.North

/** 高度な検索の入力値。空欄は検索語句に含めない */
private class AdvancedFields {
    var words by mutableStateOf("")
    var phrase by mutableStateOf("")
    var anyOf by mutableStateOf("")
    var none by mutableStateOf("")
    var hashtags by mutableStateOf("")
    var from by mutableStateOf("")
    var to by mutableStateOf("")
    var mention by mutableStateOf("")
    var since by mutableStateOf("")
    var until by mutableStateOf("")
    var minFaves by mutableStateOf("")
    var minRetweets by mutableStateOf("")
    var media by mutableStateOf(false)
    var links by mutableStateOf(false)
    var noReplies by mutableStateOf(false)
    var noRetweets by mutableStateOf(false)
}

private val datePattern = Regex("""\d{4}-\d{2}-\d{2}""")

private fun tokens(s: String): List<String> = s.trim().split(Regex("[\\s　]+")).filter { it.isNotEmpty() }
private fun handleOf(s: String): String = s.trim().removePrefix("@")

/** north の検索窓と同じ書き方（from: / to: / since: / until: / min_faves: / filter: など）へ組み立てる */
private fun buildQuery(f: AdvancedFields): String = buildList {
    tokens(f.words).forEach { add(it) }
    f.phrase.trim().takeIf { it.isNotEmpty() }?.let { add("\"${it.replace("\"", "")}\"") }
    tokens(f.anyOf).let { any ->
        if (any.size == 1) add(any[0]) else if (any.size > 1) add(any.joinToString(" OR ", prefix = "(", postfix = ")"))
    }
    tokens(f.none).forEach { add("-" + it.removePrefix("-")) }
    tokens(f.hashtags).forEach { add("#" + it.removePrefix("#")) }
    handleOf(f.from).takeIf { it.isNotEmpty() }?.let { add("from:$it") }
    handleOf(f.to).takeIf { it.isNotEmpty() }?.let { add("to:$it") }
    handleOf(f.mention).takeIf { it.isNotEmpty() }?.let { add("@$it") }
    f.since.trim().takeIf { datePattern.matches(it) }?.let { add("since:$it") }
    f.until.trim().takeIf { datePattern.matches(it) }?.let { add("until:$it") }
    f.minFaves.trim().toIntOrNull()?.takeIf { it > 0 }?.let { add("min_faves:$it") }
    f.minRetweets.trim().toIntOrNull()?.takeIf { it > 0 }?.let { add("min_retweets:$it") }
    if (f.media) add("filter:media")
    if (f.links) add("filter:links")
    if (f.noReplies) add("-filter:replies")
    if (f.noRetweets) add("-filter:retweets")
}.joinToString(" ")

/**
 * 高度な検索（Webの「検索フィルター › 高度な検索」相当）。
 * 入力から検索語句を組み立てて、そのまま検索する。
 */
@Composable
fun AdvancedSearchDialog(onDismiss: () -> Unit, onSearch: (String) -> Unit) {
    val c = North.colors
    val f = remember { AdvancedFields() }
    val query = buildQuery(f)
    val badDate = (f.since.isNotBlank() && !datePattern.matches(f.since.trim())) ||
        (f.until.isNotBlank() && !datePattern.matches(f.until.trim()))

    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Column(Modifier.fillMaxSize().background(c.bg).windowInsetsPadding(WindowInsets.safeDrawing).imePadding()) {
            Row(Modifier.fillMaxWidth().height(53.dp).padding(horizontal = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.size(40.dp).clip(CircleShape).clickable(onClick = onDismiss), contentAlignment = Alignment.Center) {
                    NorthIcon(NIcons.Close, c.text, 22.dp, modifier = Modifier.size(22.dp))
                }
                Text("高度な検索", color = c.text, fontSize = 18.sp, fontWeight = FontWeight.ExtraBold, modifier = Modifier.padding(start = 8.dp).weight(1f))
                val canSearch = query.isNotBlank() && !badDate
                Box(
                    Modifier.height(36.dp).clip(CircleShape).background(c.accent.copy(alpha = if (canSearch) 1f else 0.45f))
                        .clickable(enabled = canSearch) { onSearch(query) }.padding(horizontal = 18.dp),
                    contentAlignment = Alignment.Center,
                ) { Text("検索", color = c.textOnAccent, fontWeight = FontWeight.ExtraBold, fontSize = 15.sp) }
            }
            HorizontalDivider(color = c.border)

            Column(Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(horizontal = 16.dp, vertical = 8.dp)) {
                Section("語句")
                AdvField("すべての語句", "例: north アプリ", f.words) { f.words = it }
                AdvField("フレーズ（完全一致）", "例: 今日のごはん", f.phrase) { f.phrase = it }
                AdvField("いずれかの語句", "スペース区切り  例: 猫 犬", f.anyOf) { f.anyOf = it }
                AdvField("含めない語句", "スペース区切り", f.none) { f.none = it }
                AdvField("ハッシュタグ", "スペース区切り（#は不要）", f.hashtags) { f.hashtags = it }

                Section("アカウント")
                AdvField("投稿者", "ユーザー名（@は不要）", f.from) { f.from = it }
                AdvField("返信先", "ユーザー名（@は不要）", f.to) { f.to = it }
                AdvField("メンション", "ユーザー名（@は不要）", f.mention) { f.mention = it }

                Section("期間・反応")
                AdvField("開始日", "YYYY-MM-DD", f.since, error = f.since.isNotBlank() && !datePattern.matches(f.since.trim())) { f.since = it }
                AdvField("終了日", "YYYY-MM-DD", f.until, error = f.until.isNotBlank() && !datePattern.matches(f.until.trim())) { f.until = it }
                AdvField("最小いいね数", "例: 10", f.minFaves, number = true) { f.minFaves = it.filter(Char::isDigit).take(7) }
                AdvField("最小リポスト数", "例: 5", f.minRetweets, number = true) { f.minRetweets = it.filter(Char::isDigit).take(7) }

                Section("絞り込み")
                AdvToggle("メディア（画像・動画）を含む", f.media) { f.media = it }
                AdvToggle("リンクを含む", f.links) { f.links = it }
                AdvToggle("返信を除く", f.noReplies) { f.noReplies = it }
                AdvToggle("リポストを除く", f.noRetweets) { f.noRetweets = it }

                Text("検索語句", color = c.textMuted, fontSize = 12.sp, modifier = Modifier.padding(top = 18.dp, bottom = 4.dp))
                Text(
                    query.ifBlank { "（項目を入力すると、ここに検索語句が表示されます）" },
                    color = if (query.isBlank()) c.textMuted else c.text,
                    fontSize = 14.sp,
                    modifier = Modifier.fillMaxWidth().background(c.elev2, RoundedCornerShape(10.dp)).padding(12.dp),
                )
                Text(
                    "northの検索窓と同じ書き方に変換して検索します。センシティブな内容を含むポストは、検索結果に表示されません。",
                    color = c.textMuted, fontSize = 12.sp, lineHeight = 18.sp,
                    modifier = Modifier.padding(top = 8.dp, bottom = 24.dp),
                )
            }
        }
    }
}

@Composable
private fun Section(title: String) {
    val c = North.colors
    Text(title, color = c.text, fontSize = 17.sp, fontWeight = FontWeight.ExtraBold, modifier = Modifier.padding(top = 18.dp, bottom = 2.dp))
}

@Composable
private fun AdvField(
    label: String,
    placeholder: String,
    value: String,
    number: Boolean = false,
    error: Boolean = false,
    onChange: (String) -> Unit,
) {
    val c = North.colors
    Column(Modifier.padding(top = 10.dp)) {
        Text(label, color = if (error) c.danger else c.textMuted, fontSize = 12.sp, modifier = Modifier.padding(bottom = 4.dp))
        BasicTextField(
            value = value,
            onValueChange = onChange,
            singleLine = true,
            textStyle = TextStyle(color = c.text, fontSize = 15.sp),
            cursorBrush = SolidColor(c.accent),
            keyboardOptions = KeyboardOptions(keyboardType = if (number) KeyboardType.Number else KeyboardType.Text),
            modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(10.dp))
                .background(c.elev2).border(1.dp, if (error) c.danger else c.elev2, RoundedCornerShape(10.dp)).padding(12.dp),
            decorationBox = { inner ->
                Box { if (value.isEmpty()) Text(placeholder, color = c.textMuted, fontSize = 15.sp); inner() }
            },
        )
    }
}

@Composable
private fun AdvToggle(label: String, on: Boolean, onChange: (Boolean) -> Unit) {
    val c = North.colors
    Row(
        Modifier.fillMaxWidth().clickable { onChange(!on) }.padding(vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text(label, color = c.text, fontSize = 15.sp, modifier = Modifier.weight(1f))
        Box(
            Modifier.size(24.dp).clip(RoundedCornerShape(6.dp))
                .background(if (on) c.accent else c.elev2).border(1.dp, if (on) c.accent else c.borderStrong, RoundedCornerShape(6.dp)),
            contentAlignment = Alignment.Center,
        ) { if (on) Text("✓", color = c.textOnAccent, fontSize = 14.sp, fontWeight = FontWeight.Bold) }
    }
}
