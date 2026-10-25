package dev.rec877.north.ui

import android.content.Context
import android.net.Uri
import android.widget.Toast
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
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
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
import androidx.compose.ui.unit.TextUnit
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
    val currentUserId: () -> String? = { null },
    val onPin: (Post) -> Unit = {},
    val onUnpin: (Post) -> Unit = {},
    val onSetReplyPolicy: (Post, String) -> Unit = { _, _ -> },
    val onDelete: (Post) -> Unit = {},
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
fun PostCard(post: Post, loader: ImageLoader, actions: PostActions, pinned: Boolean = false) {
    val c = North.colors
    val context = LocalContext.current
    val shownBase = post.retweetOf ?: post
    val shown = if (pinned) shownBase.copy(pinned = true) else shownBase
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
                if (pinned) {
                    Row(
                        Modifier.padding(bottom = 3.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                    ) {
                        NorthIcon(NIcons.Pin, c.textMuted, 14.dp, modifier = Modifier.size(14.dp))
                        Text("固定されたポスト", color = c.textMuted, fontSize = 13.sp, fontWeight = FontWeight.Bold)
                    }
                }
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
                            post.author.name,
                            color = c.textMuted,
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Bold,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.weight(1f, fill = false),
                        )
                        UserBadges(post.author)
                        Text("さんがリポストしました", color = c.textMuted, fontSize = 13.sp, fontWeight = FontWeight.Bold, maxLines = 1)
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
                    PostMoreButton(shown, actions)
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
                            onOpenPost = { actions.onOpenPost(shown) },
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

@Composable
fun PostMoreButton(post: Post, actions: PostActions) {
    val c = North.colors
    val context = LocalContext.current
    val isMine = actions.currentUserId() == post.author.id
    var expanded by remember { mutableStateOf(false) }
    var confirmation by remember { mutableStateOf<String?>(null) }

    Box {
        Box(
            Modifier.size(32.dp).clip(CircleShape).clickable(role = Role.Button) { expanded = true },
            contentAlignment = Alignment.Center,
        ) {
            NorthIcon(NIcons.More, c.textMuted, 18.dp, modifier = Modifier.size(18.dp))
        }
        DropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false },
            containerColor = c.elev1,
        ) {
            if (isMine) {
                DropdownMenuItem(
                    text = { Text(if (post.pinned) "固定を解除" else "プロフィールに固定", color = c.text) },
                    onClick = { expanded = false; confirmation = if (post.pinned) "unpin" else "pin" },
                )
                val followingOnly = post.replyPolicy == "FOLLOWING"
                DropdownMenuItem(
                    text = { Text(if (followingOnly) "返信を全員に開放" else "返信をフォロー中のみに制限", color = c.text) },
                    onClick = {
                        expanded = false
                        actions.onSetReplyPolicy(post, if (followingOnly) "EVERYONE" else "FOLLOWING")
                    },
                )
            }
            DropdownMenuItem(
                text = { Text("リンクをコピー", color = c.text) },
                onClick = { expanded = false; copyPostLink(context, post) },
            )
            if (isMine) {
                DropdownMenuItem(
                    text = { Text("ポストを削除", color = c.danger) },
                    onClick = { expanded = false; confirmation = "delete" },
                )
            }
        }
    }

    confirmation?.let { action ->
        val (title, description, confirmLabel) = when (action) {
            "pin" -> Triple("ポストを固定しますか？", "このポストをプロフィールの先頭に表示します。", "固定する")
            "unpin" -> Triple("固定を解除しますか？", "このポストをプロフィールの先頭から外します。", "固定解除")
            else -> Triple("ポストを削除しますか？", "この操作は取り消せません。", "削除する")
        }
        AlertDialog(
            onDismissRequest = { confirmation = null },
            containerColor = c.elev1,
            title = { Text(title, color = c.text, fontWeight = FontWeight.ExtraBold) },
            text = { Text(description, color = c.textMuted) },
            confirmButton = {
                TextButton(onClick = {
                    confirmation = null
                    when (action) {
                        "pin" -> actions.onPin(post)
                        "unpin" -> actions.onUnpin(post)
                        else -> actions.onDelete(post)
                    }
                }) { Text(confirmLabel, color = if (action == "delete") c.danger else c.accent, fontWeight = FontWeight.Bold) }
            },
            dismissButton = { TextButton(onClick = { confirmation = null }) { Text("キャンセル", color = c.textMuted) } },
        )
    }
}

private fun copyPostLink(context: Context, post: Post) {
    val clipboard = context.getSystemService(android.content.ClipboardManager::class.java)
    clipboard.setPrimaryClip(
        android.content.ClipData.newPlainText("north のポスト", "https://north.rip/${post.author.handle}/status/${post.id}")
    )
    Toast.makeText(context, "リンクをコピーしました", Toast.LENGTH_SHORT).show()
}

private const val MENTION_TAG = "mention"
private const val URL_TAG = "url"
private val richTextPattern = Regex("""<色=\"#[0-9A-Fa-f]{6}\">|</色>|<太字>|</太字>|<斜体>|</斜体>|https?://[^\s]+|@[A-Za-z0-9_]+""")
private val urlPattern = Regex("""https?://[^\s]+""")

/** URLとメンションは各対象を開き、それ以外の本文タップはポスト詳細へ渡す。 */
@Composable
fun RichPostText(
    text: String,
    onOpenMention: (String) -> Unit,
    onOpenUrl: (String) -> Unit,
    onOpenPost: () -> Unit = {},
    fontSize: TextUnit = 16.sp,
    lineHeight: TextUnit = 22.sp,
    modifier: Modifier = Modifier,
) {
    val c = North.colors
    val annotated = remember(text, c.accent) {
        buildAnnotatedString {
            var cursor = 0
            val styleTags = mutableListOf<String>()
            richTextPattern.findAll(text).forEach { match ->
                append(text.substring(cursor, match.range.first))
                val raw = match.value
                when {
                    raw.startsWith("<色=") -> {
                        val hex = raw.substringAfter('"').substringBefore('"')
                        val color = hex.removePrefix("#").toLongOrNull(16)?.let { value ->
                            Color(
                                red = ((value shr 16) and 0xFF).toFloat() / 255f,
                                green = ((value shr 8) and 0xFF).toFloat() / 255f,
                                blue = (value and 0xFF).toFloat() / 255f,
                            )
                        }
                        if (color == null) append(raw) else {
                            pushStyle(SpanStyle(color = color))
                            styleTags += "色"
                        }
                    }
                    raw == "<太字>" -> {
                        pushStyle(SpanStyle(fontWeight = FontWeight.Bold))
                        styleTags += "太字"
                    }
                    raw == "<斜体>" -> {
                        pushStyle(SpanStyle(fontStyle = androidx.compose.ui.text.font.FontStyle.Italic))
                        styleTags += "斜体"
                    }
                    raw == "</色>" || raw == "</太字>" || raw == "</斜体>" -> {
                        val tag = raw.removePrefix("</").removeSuffix(">")
                        if (styleTags.lastOrNull() == tag) {
                            pop()
                            styleTags.removeAt(styleTags.lastIndex)
                        } else append(raw)
                    }
                    else -> {
                        val isUrl = raw.startsWith("http://") || raw.startsWith("https://")
                        // 文末の句読点はURLに含めない。表示上は元の本文を欠かさず残す。
                        val target = if (isUrl) raw.trimEnd('.', ',', '!', '?', '。', '、', ')', ']', '}') else raw
                        val start = length
                        withStyle(SpanStyle(color = c.accent)) { append(target) }
                        addStringAnnotation(if (isUrl) URL_TAG else MENTION_TAG, target, start, length)
                        append(raw.removePrefix(target))
                    }
                }
                cursor = match.range.last + 1
            }
            append(text.substring(cursor))
        }
    }
    ClickableText(
        text = annotated,
        style = TextStyle(color = c.text, fontSize = fontSize, lineHeight = lineHeight),
        modifier = modifier,
        onClick = { offset ->
            val annotation = annotated.getStringAnnotations(start = offset, end = offset).firstOrNull()
            when (annotation?.tag) {
                MENTION_TAG -> onOpenMention(annotation.item.removePrefix("@"))
                URL_TAG -> onOpenUrl(annotation.item)
                else -> onOpenPost()
            }
        },
    )
}

/** APIにOGP情報がない場合の安全なフォールバック。外部サイトへ本文を送らず、URLだけをカード化する。 */
@Composable
fun LinkCards(text: String, onOpenUrl: (String) -> Unit) {
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
                    copyPostLink(context, post)
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
        androidx.compose.foundation.Image(painterResource(R.drawable.ic_verified), "認証済み", Modifier.padding(start = 4.dp).size(if (compact) 15.dp else 18.dp), colorFilter = ColorFilter.tint(c.accent))
    }
    if (user.isProtected) androidx.compose.foundation.Image(painterResource(R.drawable.ic_locked), "非公開アカウント", Modifier.padding(start = 3.dp).size(if (compact) 14.dp else 17.dp), colorFilter = ColorFilter.tint(c.text))
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
            UserBadges(q.author)
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
