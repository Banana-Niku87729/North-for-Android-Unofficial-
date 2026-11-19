@file:OptIn(ExperimentalMaterial3Api::class, androidx.compose.foundation.ExperimentalFoundationApi::class)

package dev.rec877.north.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil3.ImageLoader
import coil3.compose.AsyncImage
import dev.rec877.north.Config
import dev.rec877.north.MainViewModel
import dev.rec877.north.Notification
import dev.rec877.north.notificationAction
import dev.rec877.north.notificationWho
import dev.rec877.north.ui.theme.North

/**
 * 通知画面（サイトの .notif を再現）。未読の行はアクセント色のうっすらした背景。
 * 行をタップするとポスト詳細（フォローなどポストが無いものはプロフィール）を開く。
 * この画面を離れる（別のタブ・別の画面へ）と、自動で既読になる。
 */
@Composable
fun NotificationsScreen(vm: MainViewModel, loader: ImageLoader, listState: LazyListState) {
    val c = North.colors
    val n = vm.notif
    Column(Modifier.fillMaxSize()) {
        Row(
            Modifier.fillMaxWidth().height(53.dp).padding(start = 16.dp, end = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                "通知",
                color = c.text,
                fontSize = 20.sp,
                fontWeight = FontWeight.ExtraBold,
                modifier = Modifier.weight(1f),
            )
            if (n.unread > 0) {
                TextButton(onClick = { vm.markAllRead() }) {
                    Text("すべて既読", color = c.accent, fontSize = 14.sp)
                }
            }
        }
        HorizontalDivider(color = c.border)

        // すべて / 認証済み / @ポスト（APIに絞り込みの指定がないため、取得済みの通知を端末側で絞る）
        NorthTabs(listOf("すべて", "認証済み", "@ポスト"), n.filter) { vm.setNotifFilter(it) }

        val shown = n.visibleItems

        PullToRefreshBox(
            isRefreshing = n.refreshing,
            onRefresh = { vm.refreshNotifications() },
            modifier = Modifier.weight(1f).fillMaxWidth(),
        ) {
            LazyColumn(Modifier.fillMaxSize(), state = listState) {
                items(shown, key = { it.id }) { item ->
                    NotificationRow(
                        n = item,
                        loader = loader,
                        onClick = {
                            val tweet = item.tweet
                            val actor = item.actors.firstOrNull()
                            // 実績の獲得などは、開く先がない
                            if (item.isSystem) Unit
                            // フォロー通知は最新の一人ではなく、自分のフォロワー全体を確認できる一覧へ。
                            else if (item.kind == "FOLLOW") vm.ui.me?.let { vm.open(dev.rec877.north.Dest.follows(it.handle, "followers")) }
                            else if (tweet != null) vm.openPost(tweet.id)
                            else if (actor != null) vm.openUser(actor.handle)
                        },
                        onOpenUser = { vm.openUser(it) },
                    )
                }

                if (n.nextCursor != null) {
                    item(key = "more") {
                        // 末尾が見えたら次ページを読む
                        LaunchedEffect(n.nextCursor) { vm.loadMoreNotifications() }
                        Box(Modifier.fillMaxWidth().padding(16.dp), contentAlignment = Alignment.Center) {
                            CircularProgressIndicator(
                                color = c.accent,
                                strokeWidth = 2.dp,
                                modifier = Modifier.size(24.dp),
                            )
                        }
                    }
                } else if (n.loaded && shown.isEmpty()) {
                    item(key = "empty") {
                        Text(
                            when (n.filter) {
                                1 -> "認証済みアカウントからの通知はありません"
                                2 -> "返信・メンション・引用の通知はありません"
                                else -> "通知はまだありません"
                            },
                            color = c.textMuted,
                            textAlign = TextAlign.Center,
                            modifier = Modifier.fillMaxWidth().padding(32.dp),
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun NotificationRow(
    n: Notification,
    loader: ImageLoader,
    onClick: () -> Unit,
    onOpenUser: (String) -> Unit,
) {
    val c = North.colors
    val context = LocalContext.current
    val (icon, tint) = when (n.kind) {
        "FOLLOW" -> NIcons.Person to c.accent
        "LIKE" -> NIcons.Like to c.like
        "RETWEET", "QUOTE" -> NIcons.Repost to c.repost
        "POST" -> NIcons.Sparkle to c.accent
        "REPLY", "MENTION" -> NIcons.Reply to c.textMuted
        // 実績の獲得など（APIの種別一覧にないもの）
        else -> NIcons.Sparkle to c.accent
    }

    val (name, tail) = notificationWho(n)
    val firstActor = n.actors.firstOrNull()

    val tweet = n.tweet?.takeIf { !it.unavailable }
    // 対象ポストの画像があれば、右にサムネイル（サイトの .notif__thumb: 48dp・角丸8）
    val thumb = tweet?.media?.firstOrNull()?.let { m ->
        Config.absUrl(if (m.kind == "VIDEO") m.thumbnailUrl else m.url)
    }

    fun copyRawJson() {
        val json = n.raw ?: return
        val clipboard = context.getSystemService(android.content.ClipboardManager::class.java)
        clipboard.setPrimaryClip(android.content.ClipData.newPlainText("north 通知JSON", json))
        android.widget.Toast.makeText(context, "通知のJSONをコピーしました", android.widget.Toast.LENGTH_SHORT).show()
    }

    Column {
        Row(
            Modifier
                .fillMaxWidth()
                .combinedClickable(
                    onClick = onClick,
                    // 種別不明の通知は、長押しで生のJSONをコピーできる（項目名の確認用）
                    onLongClick = if (n.isSystem && n.raw != null) ::copyRawJson else null,
                )
                .background(if (n.read) Color.Transparent else c.accent.copy(alpha = 0.06f))
                .padding(horizontal = 16.dp, vertical = 12.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Box(Modifier.width(30.dp).padding(top = 2.dp), contentAlignment = Alignment.TopEnd) {
                val badge = if (n.isSystem) dev.rec877.north.badgeDef(n.label) else null
                if (badge != null) BadgeIcon(badge, 30.dp)
                else NorthIcon(icon, tint, 24.dp, filled = n.kind == "LIKE", modifier = Modifier.size(24.dp))
            }

            Column(Modifier.weight(1f)) {
                if (n.isSystem) {
                    // 例: 「会話のきっかけ」を獲得しました
                    Text(
                        n.label?.let { "「${dev.rec877.north.badgeDisplayName(it)}」を獲得しました" } ?: "新しい通知があります",
                        color = c.text, fontSize = 15.sp, fontWeight = FontWeight.Bold, lineHeight = 20.sp,
                    )
                    dev.rec877.north.badgeDef(n.label)?.let {
                        Text(it.description, color = c.textMuted, fontSize = 13.sp, modifier = Modifier.padding(top = 2.dp))
                    }
                } else if (n.actors.isNotEmpty()) {
                    Row(
                        Modifier.padding(bottom = 8.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        n.actors.take(5).forEach { actor ->
                            Avatar(actor, 32.dp, loader, Modifier.clickable { onOpenUser(actor.handle) })
                        }
                    }
                }
                if (n.isSystem) {
                    // 実績の説明などは行わない（表示名のみ）
                } else if (firstActor != null) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(name, color = c.text, fontSize = 15.sp, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f, fill = false))
                        UserBadges(firstActor)
                    }
                    Text(tail + notificationAction(n.kind), color = c.text, fontSize = 15.sp, lineHeight = 20.sp)
                } else {
                    Text(name + tail + notificationAction(n.kind), color = c.text, fontSize = 15.sp, lineHeight = 20.sp)
                }
                if (tweet != null && tweet.text.isNotEmpty()) {
                    Text(
                        remember(tweet.text, c.accent) { richAnnotatedString(tweet.text, c.accent) },
                        color = c.textMuted,
                        fontSize = 15.sp,
                        lineHeight = 20.sp,
                        maxLines = 3,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.padding(top = 4.dp),
                    )
                }
            }

            if (thumb != null) {
                AsyncImage(
                    model = thumb,
                    imageLoader = loader,
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.size(48.dp).clip(RoundedCornerShape(8.dp)).background(c.elev2),
                )
            }
        }
        HorizontalDivider(color = c.border)
    }
}
