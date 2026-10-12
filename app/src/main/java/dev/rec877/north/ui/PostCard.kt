package dev.rec877.north.ui

import android.net.Uri
import androidx.browser.customtabs.CustomTabsIntent
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.ClickableText
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil3.ImageLoader
import coil3.compose.AsyncImage
import dev.rec877.north.Config
import dev.rec877.north.Media
import dev.rec877.north.BookmarkFolder
import dev.rec877.north.Poll
import dev.rec877.north.Post
import dev.rec877.north.User
import dev.rec877.north.R
import dev.rec877.north.ui.theme.North
import okhttp3.OkHttpClient

/** ホーム・検索・詳細・プロフィールで共有する、ポストへの操作 */
class PostActions(
    val onReply: (Post) -> Unit,
    val onLike: (Post) -> Unit,
    val onRepost: (Post) -> Unit,
    val onBookmark: (Post) -> Unit,
    val bookmarkFolders: () -> List<BookmarkFolder>,
    val onLoadBookmarkFolders: () -> Unit,
    val onAddToBookmarkFolder: (Post, BookmarkFolder) -> Unit,
    val onOpenMedia: (List<Media>, Int) -> Unit,
    val onOpenPost: (Post) -> Unit,
    val onOpenUser: (User) -> Unit,
    val mediaHttpClient: OkHttpClient,
    /** 本文中の @handle 用。User DTOを取得し直さずプロフィール画面を開ける。 */
    val onOpenMention: (String) -> Unit = {},
)

@Composable
fun Avatar(user: User, size: Dp, loader: ImageLoader, modifier: Modifier = Modifier) {
    val c = North.colors
    val url = Config.absUrl(user.avatarUrl)
    Box(
        modifier.size(size).clip(CircleShape).background(c.elev3),
        contentAlignment = Alignment.Center,
    ) {
        if (url != null) {
            AsyncImage(
                model = url,
                imageLoader = loader,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize(),
            )
        } else {
            Text(
                user.name.take(1),
                color = c.textMuted,
                fontWeight = FontWeight.Bold,
                fontSize = (size.value * 0.4f).sp,
            )
        }
    }
}

/**
 * サイトの .tweet を再現: 左に48dpアバター、右に本文。
 * リポストは「○○さんがリポストしました」を上に出し、中身は元ポストを表示する。
 * 返信/リポスト/いいねは元ポスト(shown)に対して行う。
 * 行をタップ → ポスト詳細、アイコン/名前をタップ → プロフィール。
 */
@Composable
fun PostCard(post: Post, loader: ImageLoader, actions: PostActions) {
    val c = North.colors
    val context = LocalContext.current
    val shown = post.retweetOf ?: post
    var shareOpen by remember { mutableStateOf(false) }
    var folderPickerOpen by remember { mutableStateOf(false) }

    Column {
        Row(
            Modifier
                .fillMaxWidth()
                .clickable { actions.onOpenPost(shown) }
                .padding(horizontal = 16.dp, vertical = 12.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Avatar(shown.author, 48.dp, loader, Modifier.clickable { actions.onOpenUser(shown.author) })

            Column(Modifier.weight(1f)) {
                if (post.retweetOf != null) {
                    Row(
                        Modifier
                            .padding(bottom = 2.dp)
                            .clickable { actions.onOpenUser(post.author) },
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                    ) {
                        NorthIcon(NIcons.Repost, c.textMuted, 14.dp, modifier = Modifier.size(14.dp))
                        Text(
                            "${post.author.name}さんがリポストしました",
                            color = c.textMuted,
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Bold,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                }

                // 名前 @handle · 時間
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        shown.author.name,
                        color = c.text,
                        fontSize = 15.sp,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier
                            .weight(1f, fill = false)
                            .clickable { actions.onOpenUser(shown.author) },
                    )
                    UserBadges(shown.author)
                    Text(
                        "@${shown.author.handle}",
                        color = c.textMuted,
                        fontSize = 15.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.padding(start = 4.dp).weight(1f, fill = false),
                    )
                    Text(
                        " · ${relativeTime(shown.createdAt)}",
                        color = c.textMuted,
                        fontSize = 15.sp,
                        maxLines = 1,
                    )
                }

                if (shown.unavailable) {
                    Text("このポストは表示できません", color = c.textMuted, fontSize = 15.sp)
                } else {
                    if (!shown.inReplyToHandle.isNullOrBlank()) {
                        Text("@${shown.inReplyToHandle} に返信", color = c.textMuted, fontSize = 13.sp)
                    }
                    if (shown.text.isNotEmpty()) {
                        RichPostText(
                            text = shown.text,
                            onOpenMention = actions.onOpenMention,
                            onOpenUrl = { url -> runCatching { CustomTabsIntent.Builder().build().launchUrl(context, Uri.parse(url)) } },
                        )
                        LinkCards(
                            text = shown.text,
                            onOpenUrl = { url -> runCatching { CustomTabsIntent.Builder().build().launchUrl(context, Uri.parse(url)) } },
                        )
                    }
                    if (shown.media.isNotEmpty()) {
                        MediaGrid(
                            media = shown.media,
                            loader = loader,
                            onOpen = { i -> actions.onOpenMedia(shown.media, i) },
                            httpClient = actions.mediaHttpClient,
                        )
                    }
                    shown.poll?.let { PollCard(it) }
                    shown.quoted?.let { QuotedPost(it, loader, actions) }
                }

                // アクション: 返信 / リポスト / いいね（カウントは0なら出さない）
                Row(Modifier.fillMaxWidth().padding(top = 6.dp)) {
                    Box(Modifier.weight(1f)) {
                        ActionButton(NIcons.Reply, shown.replyCount, c.textMuted, active = false) {
                            actions.onReply(shown)
                        }
                    }
                    Box(Modifier.weight(1f)) {
                        ActionButton(
                            NIcons.Repost, shown.retweetCount,
                            if (shown.retweeted) c.repost else c.textMuted,
                            active = false,
                        ) { actions.onRepost(shown) }
                    }
                    Box(Modifier.weight(1f)) {
                        ActionButton(
                            NIcons.Like, shown.likeCount,
                            if (shown.liked) c.like else c.textMuted,
                            active = shown.liked,
                        ) { actions.onLike(shown) }
                    }
                    Box(Modifier.weight(1f)) {
                        ActionButton(NIcons.Bookmark, 0, if (shown.bookmarked) c.accent else c.textMuted, active = false) { actions.onBookmark(shown) }
                    }
                    Box(Modifier.weight(1f)) {
                        ActionButton(NIcons.Share, 0, c.textMuted, active = false) { shareOpen = true }
                    }
                }
            }
        }
        HorizontalDivider(color = c.border)
    }
    if (shareOpen) {
        ShareMenu(
            post = shown,
            folders = actions.bookmarkFolders(),
            onDismiss = { shareOpen = false },
            onBookmark = { actions.onBookmark(shown); shareOpen = false },
            onOpenFolders = { actions.onLoadBookmarkFolders(); folderPickerOpen = true },
        )
    }
    if (folderPickerOpen) {
        BookmarkFolderPicker(
            folders = actions.bookmarkFolders(),
            onDismiss = { folderPickerOpen = false },
            onPick = { folder -> actions.onAddToBookmarkFolder(shown, folder); folderPickerOpen = false; shareOpen = false },
        )
    }
}

private const val MENTION_TAG = "mention"
private const val URL_TAG = "url"
private val interactiveTextPattern = Regex("""https?://[^\s]+|@[A-Za-z0-9_]+""")
private val urlPattern = Regex("""https?://[^\s]+""")

/** URLとメンションだけを装飾し、ポスト詳細ではなく各対象を直接開ける本文表示。 */
@Composable
private fun RichPostText(text: String, onOpenMention: (String) -> Unit, onOpenUrl: (String) -> Unit) {
    val c = North.colors
    val annotated = remember(text, c.accent) {
        buildAnnotatedString {
            var cursor = 0
            interactiveTextPattern.findAll(text).forEach { match ->
                append(text.substring(cursor, match.range.first))
                val raw = match.value
                val isUrl = raw.startsWith("http://") || raw.startsWith("https://")
                // 文末の句読点はURLに含めない。表示上は元の本文を欠かさず残す。
                val target = if (isUrl) raw.trimEnd('.', ',', '!', '?', '。', '、', ')', ']', '}') else raw
                val start = length
                withStyle(SpanStyle(color = c.accent)) { append(target) }
                addStringAnnotation(if (isUrl) URL_TAG else MENTION_TAG, target, start, length)
                append(raw.removePrefix(target))
                cursor = match.range.last + 1
            }
            append(text.substring(cursor))
        }
    }
    ClickableText(
        text = annotated,
        style = TextStyle(color = c.text, fontSize = 16.sp, lineHeight = 22.sp),
        onClick = { offset ->
            annotated.getStringAnnotations(start = offset, end = offset).firstOrNull()?.let { annotation ->
                if (annotation.tag == MENTION_TAG) onOpenMention(annotation.item.removePrefix("@"))
                else if (annotation.tag == URL_TAG) onOpenUrl(annotation.item)
            }
        },
    )
}

/** APIにOGP情報がない場合の安全なフォールバック。外部サイトへ本文を送らず、URLだけをカード化する。 */
@Composable
private fun LinkCards(text: String, onOpenUrl: (String) -> Unit) {
    val c = North.colors
    val urls = remember(text) {
        urlPattern.findAll(text).map { it.value.trimEnd('.', ',', '!', '?', '。', '、', ')', ']', '}') }
            .filter { it.isNotBlank() }.distinct().take(2).toList()
    }
    urls.forEach { url ->
        val host = remember(url) { Uri.parse(url).host ?: url }
        Column(
            Modifier.fillMaxWidth().padding(top = 8.dp).clip(RoundedCornerShape(12.dp))
                .border(1.dp, c.border, RoundedCornerShape(12.dp)).clickable { onOpenUrl(url) }.padding(12.dp),
        ) {
            Text(host, color = c.text, fontSize = 14.sp, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(url, color = c.textMuted, fontSize = 13.sp, maxLines = 2, overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(top = 3.dp))
        }
    }
}

@Composable
fun ShareMenu(post: Post, folders: List<BookmarkFolder>, onDismiss: () -> Unit, onBookmark: () -> Unit, onOpenFolders: () -> Unit) {
    val c = North.colors
    val context = LocalContext.current
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = c.elev1,
        title = { Text("共有", color = c.text, fontWeight = FontWeight.ExtraBold) },
        text = {
            Column {
                Text("リンクをコピー", color = c.text, fontWeight = FontWeight.Bold, modifier = Modifier.fillMaxWidth().clickable {
                    val clipboard = context.getSystemService(android.content.ClipboardManager::class.java)
                    clipboard.setPrimaryClip(android.content.ClipData.newPlainText("north のポスト", "https://north.rip/${post.author.id}/status/${post.id}"))
                    onDismiss()
                }.padding(vertical = 14.dp))
                Text(if (post.bookmarked) "ブックマークから削除" else "ブックマークに追加", color = c.text, fontWeight = FontWeight.Bold, modifier = Modifier.fillMaxWidth().clickable(onClick = onBookmark).padding(vertical = 14.dp))
                Text("フォルダに追加", color = c.text, fontWeight = FontWeight.Bold, modifier = Modifier.fillMaxWidth().clickable(onClick = onOpenFolders).padding(vertical = 14.dp))
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("閉じる", color = c.accent) } },
    )
}

@Composable
fun BookmarkFolderPicker(folders: List<BookmarkFolder>, onDismiss: () -> Unit, onPick: (BookmarkFolder) -> Unit) {
    val c = North.colors
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = c.elev1,
        title = { Text("フォルダに追加", color = c.text, fontWeight = FontWeight.ExtraBold) },
        text = {
            Column {
                if (folders.isEmpty()) Text("フォルダがありません。Web版でフォルダを作成してください。", color = c.textMuted)
                else folders.forEach { folder ->
                    Text(folder.name, color = c.text, fontWeight = FontWeight.Bold, modifier = Modifier.fillMaxWidth().clickable { onPick(folder) }.padding(vertical = 14.dp))
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("閉じる", color = c.accent) } },
    )
}

/** 投票の選択肢と集計。投票操作APIは別途用意されるまで、結果の閲覧表示に留める。 */
@Composable
fun PollCard(poll: Poll) {
    val c = North.colors
    val shape = RoundedCornerShape(12.dp)
    Column(Modifier.padding(top = 10.dp).fillMaxWidth().clip(shape).border(1.dp, c.border, shape).padding(10.dp), verticalArrangement = Arrangement.spacedBy(7.dp)) {
        poll.options.sortedBy { it.position }.forEach { option ->
            val selected = option.id == poll.viewerOptionId
            val suffix = option.percent?.let { " ${it.toInt()}%" }.orEmpty()
            Text("${if (selected) "✓ " else ""}${option.label}$suffix", color = if (selected) c.accent else c.text, fontSize = 14.sp, fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal)
        }
        Text("${poll.totalVotes} 件の投票${if (poll.ended) "・終了" else ""}", color = c.textMuted, fontSize = 12.sp)
    }
}

/** 認証済み・非公開アカウントであることを、プロフィール以外の投稿一覧でも明示する。 */
@Composable
fun UserBadges(user: User, compact: Boolean = true) {
    val c = North.colors
    if (user.verified) {
        androidx.compose.foundation.Image(painterResource(R.drawable.ic_verified), null, Modifier.padding(start = 4.dp).size(if (compact) 15.dp else 18.dp), colorFilter = ColorFilter.tint(c.accent))
    }
    if (user.isProtected) androidx.compose.foundation.Image(painterResource(R.drawable.ic_locked), null, Modifier.padding(start = 3.dp).size(if (compact) 14.dp else 17.dp), colorFilter = ColorFilter.tint(c.text))
}

/** 引用しているポスト: 角丸の枠の中に、投稿者・本文・画像を小さく出す。タップでそのポストへ */
@Composable
fun QuotedPost(q: Post, loader: ImageLoader, actions: PostActions) {
    val c = North.colors
    val shape = RoundedCornerShape(16.dp)
    Column(
        Modifier
            .padding(top = 12.dp)
            .fillMaxWidth()
            .clip(shape)
            .border(1.dp, c.border, shape)
            .clickable { actions.onOpenPost(q) }
            .padding(12.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            Avatar(q.author, 20.dp, loader)
            Text(
                q.author.name,
                color = c.text,
                fontSize = 14.sp,
                fontWeight = FontWeight.Bold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f, fill = false),
            )
            Text(
                "@${q.author.handle} · ${relativeTime(q.createdAt)}",
                color = c.textMuted,
                fontSize = 14.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        if (q.unavailable) {
            Text("このポストは表示できません", color = c.textMuted, fontSize = 14.sp, modifier = Modifier.padding(top = 6.dp))
        } else {
            if (q.text.isNotEmpty()) {
                Text(
                    q.text,
                    color = c.text,
                    fontSize = 15.sp,
                    lineHeight = 20.sp,
                    maxLines = 6,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.padding(top = 6.dp),
                )
            }
            if (q.media.isNotEmpty()) {
                MediaGrid(media = q.media, loader = loader, onOpen = { i -> actions.onOpenMedia(q.media, i) }, httpClient = actions.mediaHttpClient)
            }
        }
    }
}

@Composable
fun ActionButton(
    icon: NIcon,
    count: Int,
    tint: Color,
    active: Boolean,
    onClick: () -> Unit,
) {
    Row(
        Modifier
            .clip(CircleShape)
            .clickable(role = Role.Button, onClick = onClick)
            .padding(horizontal = 8.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        // リポストのベクターだけ上下の描画域が広いため、他のアイコンと光学的な中心を揃える。
        NorthIcon(icon, tint, 19.dp, filled = active, modifier = Modifier.size(19.dp).offset(y = if (icon === NIcons.Repost) 1.dp else 0.dp))
        if (count > 0) Text(count.toString(), color = tint, fontSize = 13.sp)
    }
}
