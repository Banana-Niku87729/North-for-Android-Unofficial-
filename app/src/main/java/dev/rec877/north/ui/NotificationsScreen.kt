@file:OptIn(ExperimentalMaterial3Api::class)

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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
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

        PullToRefreshBox(
            isRefreshing = n.refreshing,
            onRefresh = { vm.refreshNotifications() },
            modifier = Modifier.weight(1f).fillMaxWidth(),
        ) {
            LazyColumn(Modifier.fillMaxSize(), state = listState) {
                items(n.items, key = { it.id }) { item ->
                    NotificationRow(
                        n = item,
                        loader = loader,
                        onClick = {
                            val tweet = item.tweet
                            val actor = item.actors.firstOrNull()
                            if (tweet != null) vm.openPost(tweet.id)
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
                } else if (n.loaded && n.items.isEmpty()) {
                    item(key = "empty") {
                        Text(
                            "通知はまだありません",
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
    val (icon, tint) = when (n.kind) {
        "FOLLOW" -> NIcons.Person to c.accent
        "LIKE" -> NIcons.Like to c.like
        "RETWEET", "QUOTE" -> NIcons.Repost to c.repost
        "POST" -> NIcons.Sparkle to c.accent
        "REPLY", "MENTION" -> NIcons.Reply to c.textMuted
        else -> NIcons.Bell to c.textMuted
    }

    val (name, tail) = notificationWho(n)
    val title = buildAnnotatedString {
        withStyle(SpanStyle(fontWeight = FontWeight.Bold)) { append(name) }
        append(tail + notificationAction(n.kind))
    }

    val tweet = n.tweet?.takeIf { !it.unavailable }
    // 対象ポストの画像があれば、右にサムネイル（サイトの .notif__thumb: 48dp・角丸8）
    val thumb = tweet?.media?.firstOrNull()?.let { m ->
        Config.absUrl(if (m.kind == "VIDEO") m.thumbnailUrl else m.url)
    }

    Column {
        Row(
            Modifier
                .fillMaxWidth()
                .clickable(onClick = onClick)
                .background(if (n.read) Color.Transparent else c.accent.copy(alpha = 0.06f))
                .padding(horizontal = 16.dp, vertical = 12.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Box(Modifier.width(30.dp).padding(top = 2.dp), contentAlignment = Alignment.TopEnd) {
                NorthIcon(icon, tint, 24.dp, filled = n.kind == "LIKE", modifier = Modifier.size(24.dp))
            }

            Column(Modifier.weight(1f)) {
                if (n.actors.isNotEmpty()) {
                    Row(
                        Modifier.padding(bottom = 8.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        n.actors.take(5).forEach { actor ->
                            Avatar(actor, 32.dp, loader, Modifier.clickable { onOpenUser(actor.handle) })
                        }
                    }
                }
                Text(title, color = c.text, fontSize = 15.sp, lineHeight = 20.sp)
                if (tweet != null && tweet.text.isNotEmpty()) {
                    Text(
                        tweet.text,
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