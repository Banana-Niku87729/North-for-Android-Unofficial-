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
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
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
import dev.rec877.north.PollOption
import dev.rec877.north.Post
import dev.rec877.north.User
import dev.rec877.north.shownBadges
import dev.rec877.north.R
import dev.rec877.north.ui.theme.North
import okhttp3.OkHttpClient

/** ホーム・検索・詳細・プロフィールで共有する、ポストへの操作 */
data class PostActions(
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
    /** 本文中の #ハッシュタグ（先頭の # を含む）。検索へ移る */
    val onOpenHashtag: (String) -> Unit = {},
    /** 投票（選択肢をタップ） */
    val onVote: (Post, PollOption) -> Unit = { _, _ -> },
    /** 自分のポストを編集 */
    val onEdit: (Post) -> Unit = {},
    /** 「リストへの追加/削除」ダイアログを開く */
    val onManageLists: (User) -> Unit = {},
    val onUnfollow: (User) -> Unit = {},
    val onMute: (User, Boolean) -> Unit = { _, _ -> },
    val onBlock: (User, Boolean) -> Unit = { _, _ -> },
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
                            onOpenHashtag = actions.onOpenHashtag,
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
                    shown.poll?.let { PollCard(shown, it, actions.onVote) }
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
    val author = post.author
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
                DropdownMenuItem(
                    text = { Text("ポストを編集", color = c.text) },
                    onClick = { expanded = false; actions.onEdit(post) },
                )
                val followingOnly = post.replyPolicy == "FOLLOWING"
                DropdownMenuItem(
                    text = { Text(if (followingOnly) "返信を全員に開放" else "返信をフォロー中のみに制限", color = c.text) },
                    onClick = {
                        expanded = false
                        actions.onSetReplyPolicy(post, if (followingOnly) "EVERYONE" else "FOLLOWING")
                    },
                )
            } else if (author.id.isNotBlank()) {
                if (author.following) {
                    DropdownMenuItem(
                        text = { Text("@${author.handle} さんのフォローを解除", color = c.text) },
                        onClick = { expanded = false; confirmation = "unfollow" },
                    )
                }
                DropdownMenuItem(
                    text = { Text("リストへの追加/削除", color = c.text) },
                    onClick = { expanded = false; actions.onManageLists(author) },
                )
                DropdownMenuItem(
                    text = { Text(if (author.muting) "@${author.handle} さんのミュートを解除" else "@${author.handle} さんをミュート", color = c.text) },
                    onClick = { expanded = false; actions.onMute(author, !author.muting) },
                )
                DropdownMenuItem(
                    text = {
                        Text(
                            if (author.blocking) "@${author.handle} さんのブロックを解除" else "@${author.handle} さんをブロック",
                            color = c.danger,
                        )
                    },
                    onClick = { expanded = false; if (author.blocking) actions.onBlock(author, false) else confirmation = "block" },
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
            "unfollow" -> Triple("フォローを解除しますか？", "@${author.handle} さんのフォローを解除します。", "フォロー解除")
            "block" -> Triple("ブロックしますか？", "@${author.handle} さんをブロックすると、お互いのフォローが解除され、ポストが表示されなくなります。", "ブロックする")
            else -> Triple("ポストを削除しますか？", "この操作は取り消せません。", "削除する")
        }
        val danger = action == "delete" || action == "block"
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
                        "unfollow" -> actions.onUnfollow(author)
                        "block" -> actions.onBlock(author, true)
                        else -> actions.onDelete(post)
                    }
                }) { Text(confirmLabel, color = if (danger) c.danger else c.accent, fontWeight = FontWeight.Bold) }
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
private const val HASHTAG_TAG = "hashtag"

/**
 * 本文の装飾タグとリンク。
 * 色: Webの <color=#RRGGBB>…</color>（"#…" の引用符つき・3桁も可）と、従来の <色="#RRGGBB">…</色>
 * 太字・斜体: <太字> <斜体>（<b> <i> も可）
 * リンク: URL / @メンション / #ハッシュタグ
 */
private val richTextPattern = Regex(
    """<\s*(?:color|colour|色)\s*=\s*["']?#?(?:[0-9A-Fa-f]{8}|[0-9A-Fa-f]{6}|[0-9A-Fa-f]{3})["']?\s*>""" +
            """|<\s*/\s*(?:color|colour|色)\s*>|<太字>|</太字>|<斜体>|</斜体>|</?[bi]>""" +
            """|https?://[^\s]+|@[A-Za-z0-9_]+|(?<![\p{L}\p{N}_&/])#[\p{L}\p{N}\p{M}_]+""",
    RegexOption.IGNORE_CASE,
)
private val colorOpenPattern = Regex("""^<\s*(?:color|colour|色)\s*=""", RegexOption.IGNORE_CASE)
private val colorClosePattern = Regex("""^<\s*/\s*(?:color|colour|色)\s*>$""", RegexOption.IGNORE_CASE)
private val hexInTagPattern = Regex("""#?([0-9A-Fa-f]{8}|[0-9A-Fa-f]{6}|[0-9A-Fa-f]{3})""")
private val urlPattern = Regex("""https?://[^\s]+""")

private fun parseTagColor(tag: String): Color? {
    val hex = hexInTagPattern.find(tag.substringAfter('='))?.groupValues?.get(1) ?: return null
    val full = if (hex.length == 3) hex.map { "$it$it" }.joinToString("") else hex.take(6)
    val value = full.toLongOrNull(16) ?: return null
    return Color(
        red = ((value shr 16) and 0xFF).toFloat() / 255f,
        green = ((value shr 8) and 0xFF).toFloat() / 255f,
        blue = (value and 0xFF).toFloat() / 255f,
    )
}

/** 全数字の #123 はハッシュタグとして扱わない */
private fun isHashtag(raw: String): Boolean = raw.length > 1 && raw.drop(1).any { !it.isDigit() }

/**
 * 装飾タグ・リンクを反映した本文。ポスト本文・引用・通知の抜粋など、本文を出すすべての場所で共通に使う
 * （色指定がある場所とない場所が混ざらないようにする）。
 */
fun richAnnotatedString(text: String, linkColor: Color): AnnotatedString = buildAnnotatedString {
    var cursor = 0
    val styleTags = mutableListOf<String>()
    richTextPattern.findAll(text).forEach { match ->
        append(text.substring(cursor, match.range.first))
        val raw = match.value
        val lower = raw.lowercase()
        when {
            colorOpenPattern.containsMatchIn(raw) -> {
                val color = parseTagColor(raw)
                if (color == null) append(raw) else {
                    pushStyle(SpanStyle(color = color))
                    styleTags += "color"
                }
            }
            lower == "<太字>" || lower == "<b>" -> {
                pushStyle(SpanStyle(fontWeight = FontWeight.Bold))
                styleTags += "bold"
            }
            lower == "<斜体>" || lower == "<i>" -> {
                pushStyle(SpanStyle(fontStyle = androidx.compose.ui.text.font.FontStyle.Italic))
                styleTags += "italic"
            }
            colorClosePattern.matches(raw) || lower == "</太字>" || lower == "</斜体>" || lower == "</b>" || lower == "</i>" -> {
                val tag = when {
                    colorClosePattern.matches(raw) -> "color"
                    lower == "</太字>" || lower == "</b>" -> "bold"
                    else -> "italic"
                }
                if (styleTags.lastOrNull() == tag) {
                    pop()
                    styleTags.removeAt(styleTags.lastIndex)
                } else append(raw)
            }
            raw.startsWith("#") && !isHashtag(raw) -> append(raw)
            else -> {
                val isUrl = raw.startsWith("http://", ignoreCase = true) || raw.startsWith("https://", ignoreCase = true)
                val isTag = raw.startsWith("#")
                // 文末の句読点はURLに含めない。表示上は元の本文を欠かさず残す。
                val target = if (isUrl) raw.trimEnd('.', ',', '!', '?', '。', '、', ')', ']', '}') else raw
                val start = length
                withStyle(SpanStyle(color = linkColor)) { append(target) }
                addStringAnnotation(if (isUrl) URL_TAG else if (isTag) HASHTAG_TAG else MENTION_TAG, target, start, length)
                append(raw.removePrefix(target))
            }
        }
        cursor = match.range.last + 1
    }
    append(text.substring(cursor))
}

/** URL・メンション・ハッシュタグは各対象を開き、それ以外の本文タップはポスト詳細へ渡す。 */
@Composable
fun RichPostText(
    text: String,
    onOpenMention: (String) -> Unit,
    onOpenUrl: (String) -> Unit,
    onOpenPost: () -> Unit = {},
    fontSize: TextUnit = 16.sp,
    lineHeight: TextUnit = 22.sp,
    modifier: Modifier = Modifier,
    onOpenHashtag: (String) -> Unit = {},
) {
    val c = North.colors
    val annotated = remember(text, c.accent) { richAnnotatedString(text, c.accent) }
    ClickableText(
        text = annotated,
        style = TextStyle(color = c.text, fontSize = fontSize, lineHeight = lineHeight),
        modifier = modifier,
        onClick = { offset ->
            val annotation = annotated.getStringAnnotations(start = offset, end = offset).firstOrNull()
            when (annotation?.tag) {
                MENTION_TAG -> onOpenMention(annotation.item.removePrefix("@"))
                URL_TAG -> onOpenUrl(annotation.item)
                HASHTAG_TAG -> onOpenHashtag(annotation.item)
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

/**
 * 投票。未投票・受付中は選択肢をタップして投票し、投票後と終了後は集計（割合バー）を表示する。
 * 投票には polls.vote の権限が必要（権限不足のときは「再ログイン」を案内するエラーが出る）。
 */
@Composable
fun PollCard(post: Post, poll: Poll, onVote: (Post, PollOption) -> Unit) {
    val c = North.colors
    val shape = RoundedCornerShape(12.dp)
    val canVote = !poll.ended && poll.viewerOptionId == null
    val total = poll.totalVotes.coerceAtLeast(poll.options.sumOf { it.voteCount })
    Column(Modifier.padding(top = 10.dp).fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(7.dp)) {
        poll.options.sortedBy { it.position }.forEach { option ->
            val selected = option.id == poll.viewerOptionId
            if (canVote) {
                Box(
                    Modifier.fillMaxWidth().clip(shape).border(1.dp, c.accent, shape)
                        .clickable(role = Role.Button) { onVote(post, option) }.padding(horizontal = 14.dp, vertical = 10.dp),
                    contentAlignment = Alignment.Center,
                ) { Text(option.label, color = c.accent, fontSize = 15.sp, fontWeight = FontWeight.Bold) }
            } else {
                val percent = option.percent ?: if (total > 0) option.voteCount * 100.0 / total else 0.0
                Box(Modifier.fillMaxWidth().height(38.dp).clip(shape).border(1.dp, c.border, shape)) {
                    Box(
                        Modifier.fillMaxHeight().fillMaxWidth((percent / 100.0).toFloat().coerceIn(0f, 1f))
                            .background(if (selected) c.accent.copy(alpha = 0.35f) else c.elev3)
                    )
                    Row(Modifier.fillMaxSize().padding(horizontal = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            (if (selected) "✓ " else "") + option.label,
                            color = c.text, fontSize = 14.sp,
                            fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal,
                            maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f),
                        )
                        Text("${percent.toInt()}%", color = c.text, fontSize = 14.sp, fontWeight = FontWeight.Bold)
                    }
                }
            }
        }
        Text(
            "$total 件の投票・" + (if (poll.ended) "終了" else pollRemaining(poll.endsAt)),
            color = c.textMuted, fontSize = 12.sp,
        )
    }
}

/** 認証済み・非公開アカウントであることを、プロフィール以外の投稿一覧でも明示する。 */
@Composable
fun UserBadges(user: User, compact: Boolean = true) {
    val c = North.colors
    if (user.verified) {
        androidx.compose.foundation.Image(painterResource(R.drawable.ic_verified), "認証済み", Modifier.padding(start = 4.dp).size(if (compact) 15.dp else 18.dp), colorFilter = ColorFilter.tint(c.accent))
    }
    // ポスト一覧などの名前の横には、実績バッジを小さく並べる（プロフィールは専用の行に出す）
    if (compact) user.shownBadges().forEach { b ->
        dev.rec877.north.badgeDef(b.key)?.let { BadgeIcon(it, 15.dp, Modifier.padding(start = 3.dp)) }
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
                    remember(q.text, c.accent) { richAnnotatedString(q.text, c.accent) },
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