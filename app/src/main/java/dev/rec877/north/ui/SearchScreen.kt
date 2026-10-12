package dev.rec877.north.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
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
import androidx.compose.runtime.derivedStateOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil3.ImageLoader
import dev.rec877.north.MainViewModel
import dev.rec877.north.User
import dev.rec877.north.ui.theme.North

/**
 * 検索画面（サイトの .searchbox / .tabs を再現）。
 * 語句は north の検索窓と同じ書き方: from:ユーザー名 / #ハッシュタグ など。
 * タブ「最新」= latest（新しい順）、「話題」= top（話題順）。
 */
@Composable
fun SearchScreen(vm: MainViewModel, loader: ImageLoader, actions: PostActions, listState: LazyListState) {
    val c = North.colors
    val s = vm.search
    val focus = LocalFocusManager.current
    var focused by remember { mutableStateOf(false) }
    // 空の一覧に対する scrollToItem は、後から項目が入ったときに以前の位置を復元してしまう。
    // 初回の検索結果が描画可能になった後、検索世代ごとに一度だけ先頭へ戻す。
    var centeredResultVersion by remember { mutableStateOf(-1) }
    val nearEnd by remember(listState) {
        derivedStateOf {
            val info = listState.layoutInfo
            val last = info.visibleItemsInfo.lastOrNull()?.index ?: -1
            last >= info.totalItemsCount - 2
        }
    }
    LaunchedEffect(s.version, s.loading, s.results.size) {
        if (s.searched && !s.loading && s.results.isNotEmpty() && centeredResultVersion != s.version) {
            listState.scrollToItem(0)
            centeredResultVersion = s.version
        }
    }
    // 検索タブを開き直すたびに、保存済みの末尾位置ではなく先頭から表示する。
    LaunchedEffect(Unit) { listState.scrollToItem(0) }

    // 実際に末尾が表示されたときだけ続きを読む。アイテムを先読みしただけで
    // ページングを始めないので、検索直後に一覧の途中へ飛ばされない。
    LaunchedEffect(nearEnd, s.nextCursor, s.loading) {
        if (nearEnd && s.nextCursor != null && !s.loading) vm.loadMoreSearch()
    }

    Column(Modifier.fillMaxSize()) {
        // ---- 検索ボックス: 角丸ピル・42dp・フォーカスでアイコンがアクセント色に ----
        Box(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp)) {
            Row(
                Modifier
                    .fillMaxWidth()
                    .height(42.dp)
                    .clip(CircleShape)
                    .background(c.elev2)
                    .padding(horizontal = 14.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                NorthIcon(
                    NIcons.Search,
                    if (focused) c.accent else c.textMuted,
                    20.dp,
                    modifier = Modifier.size(20.dp),
                )
                BasicTextField(
                    value = s.input,
                    onValueChange = { vm.setSearchInput(it) },
                    singleLine = true,
                    textStyle = TextStyle(color = c.text, fontSize = 15.sp),
                    cursorBrush = SolidColor(c.accent),
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                    keyboardActions = KeyboardActions(onSearch = {
                        focus.clearFocus()
                        vm.submitSearch(s.input)
                    }),
                    modifier = Modifier.weight(1f).onFocusChanged { focused = it.isFocused },
                    decorationBox = { inner ->
                        Box(contentAlignment = Alignment.CenterStart) {
                            if (s.input.isEmpty()) Text("検索", color = c.textMuted, fontSize = 15.sp)
                            inner()
                        }
                    },
                )
                if (s.input.isNotEmpty()) {
                    Box(
                        Modifier
                            .size(20.dp)
                            .clip(CircleShape)
                            .background(c.accent)
                            .clickable { vm.clearSearch() },
                        contentAlignment = Alignment.Center,
                    ) { NorthIcon(NIcons.Close, c.textOnAccent, 12.dp, modifier = Modifier.size(12.dp)) }
                }
            }
        }

        if (!s.searched) {
            SearchIdle(vm, loader)
        } else {
            // ---- タブ（アクセント色の4dp下線）----
            Row(Modifier.fillMaxWidth()) {
                listOf("latest" to "最新", "top" to "話題").forEach { (key, label) ->
                    val selected = s.tab == key
                    Column(
                        Modifier.weight(1f).clickable { vm.setSearchTab(key) },
                        horizontalAlignment = Alignment.CenterHorizontally,
                    ) {
                        Text(
                            label,
                            color = if (selected) c.text else c.textMuted,
                            fontSize = 15.sp,
                            fontWeight = FontWeight.Medium,
                            modifier = Modifier.padding(top = 16.dp, bottom = 11.dp),
                        )
                        Box(
                            Modifier
                                .width(56.dp)
                                .height(4.dp)
                                .clip(RoundedCornerShape(4.dp))
                                .background(if (selected) c.accent else Color.Transparent)
                        )
                    }
                }
            }
            HorizontalDivider(color = c.border)

            LazyColumn(Modifier.fillMaxSize(), state = listState) {
                items(s.results, key = { it.id }) { post ->
                    PostCard(post, loader, actions)
                }

                if (s.nextCursor != null || s.loading) {
                    item(key = "more") {
                        Box(Modifier.fillMaxWidth().padding(24.dp), contentAlignment = Alignment.Center) {
                            CircularProgressIndicator(
                                color = c.accent,
                                strokeWidth = 2.dp,
                                modifier = Modifier.size(24.dp),
                            )
                        }
                    }
                } else if (s.results.isEmpty()) {
                    item(key = "empty") {
                        Text(
                            "「${s.query}」に一致するポストはありません",
                            color = c.textMuted,
                            fontSize = 15.sp,
                            textAlign = TextAlign.Center,
                            modifier = Modifier.fillMaxWidth().padding(32.dp),
                        )
                    }
                }
            }
        }
    }
}

/** 検索前: 最近の検索 + 書き方のヒント */
@Composable
private fun SearchIdle(vm: MainViewModel, loader: ImageLoader) {
    val c = North.colors
    val s = vm.search
    val candidates = vm.searchCandidates

    LaunchedEffect(Unit) { vm.loadTrends() }

    LazyColumn(Modifier.fillMaxSize()) {
        if (s.input.isNotBlank()) {
            item(key = "candidate_head") {
                Text(
                    "アカウント",
                    color = c.text,
                    fontSize = 17.sp,
                    fontWeight = FontWeight.ExtraBold,
                    modifier = Modifier.padding(start = 16.dp, top = 14.dp, bottom = 6.dp),
                )
            }
            if (candidates.isEmpty()) {
                item(key = "candidate_empty") {
                    Text(
                        "一致する候補はありません。@ユーザー名を入力して検索できます。",
                        color = c.textMuted,
                        fontSize = 14.sp,
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 10.dp),
                    )
                }
            } else {
                items(candidates, key = { "candidate:${it.id}" }) { user ->
                    AccountCandidate(user, loader) { vm.openUser(user.handle) }
                }
            }
        } else {
            item(key = "trend_head") {
                Row(
                    Modifier.fillMaxWidth().padding(start = 16.dp, end = 8.dp, top = 14.dp, bottom = 6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text("トレンド", color = c.text, fontSize = 17.sp, fontWeight = FontWeight.ExtraBold, modifier = Modifier.weight(1f))
                    TextButton(onClick = vm::loadTrends) { Text("更新", color = c.accent, fontSize = 14.sp) }
                }
            }
            if (vm.trendsLoading) {
                item(key = "trend_loading") {
                    Box(Modifier.fillMaxWidth().padding(20.dp), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator(color = c.accent, strokeWidth = 2.dp, modifier = Modifier.size(22.dp))
                    }
                }
            } else {
                items(vm.trends, key = { "trend:${it.tag}" }) { trend ->
                    Column(
                        Modifier.fillMaxWidth().clickable {
                            val q = if (trend.isHashtag && !trend.tag.startsWith("#")) "#${trend.tag}" else trend.tag
                            vm.setSearchInput(q)
                            vm.submitSearch(q)
                        }.padding(horizontal = 16.dp, vertical = 12.dp)
                    ) {
                        Text(if (trend.isHashtag && !trend.tag.startsWith("#")) "#${trend.tag}" else trend.tag, color = c.text, fontSize = 16.sp, fontWeight = FontWeight.Bold)
                        Text("${trend.count} 件のポスト", color = c.textMuted, fontSize = 13.sp, modifier = Modifier.padding(top = 2.dp))
                    }
                }
            }
        }
        if (s.history.isNotEmpty()) {
            item(key = "histhead") {
                Row(
                    Modifier.fillMaxWidth().padding(start = 16.dp, end = 12.dp, top = 12.dp, bottom = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        "最近の検索",
                        color = c.text,
                        fontSize = 17.sp,
                        fontWeight = FontWeight.ExtraBold,
                        modifier = Modifier.weight(1f),
                    )
                    TextButton(onClick = { vm.clearHistory() }) {
                        Text("すべて削除", color = c.accent, fontSize = 14.sp)
                    }
                }
            }
            items(s.history, key = { "h:$it" }) { q ->
                Row(
                    Modifier.fillMaxWidth().clickable { vm.submitSearch(q) }.padding(start = 16.dp, end = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    NorthIcon(NIcons.Search, c.textMuted, 18.dp, modifier = Modifier.size(18.dp))
                    Text(
                        q,
                        color = c.text,
                        fontSize = 15.sp,
                        maxLines = 1,
                        modifier = Modifier.weight(1f).padding(vertical = 14.dp),
                    )
                    Box(
                        Modifier.size(36.dp).clip(CircleShape).clickable { vm.removeHistory(q) },
                        contentAlignment = Alignment.Center,
                    ) { NorthIcon(NIcons.Close, c.textMuted, 16.dp, modifier = Modifier.size(16.dp)) }
                }
            }
        }
        item(key = "hint") {
            Text(
                "from:ユーザー名 や #ハッシュタグ で絞り込めます。\n" +
                        "センシティブな内容を含むポストは、検索結果に表示されません。",
                color = c.textMuted,
                fontSize = 13.sp,
                lineHeight = 22.sp,
                modifier = Modifier.padding(16.dp),
            )
        }
    }
}

@Composable
private fun AccountCandidate(user: User, loader: ImageLoader, onOpen: () -> Unit) {
    val c = North.colors
    Row(
        Modifier.fillMaxWidth().clickable(onClick = onOpen).padding(horizontal = 16.dp, vertical = 10.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Avatar(user, 42.dp, loader)
        Column(Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(user.name, color = c.text, fontSize = 15.sp, fontWeight = FontWeight.Bold, maxLines = 1)
                UserBadges(user, compact = true)
            }
            Text("@${user.handle}", color = c.textMuted, fontSize = 13.sp, maxLines = 1)
        }
    }
}
