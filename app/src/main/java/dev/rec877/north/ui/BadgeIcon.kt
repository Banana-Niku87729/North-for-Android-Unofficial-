package dev.rec877.north.ui

import android.net.Uri
import androidx.browser.customtabs.CustomTabsIntent
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.rec877.north.DisplayedBadge
import dev.rec877.north.ui.theme.North
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Fill
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.vector.PathParser
import androidx.compose.ui.unit.Dp
import dev.rec877.north.BadgeDef

// Web版の実績バッジ(100x100のSVG)と同じ図柄。段階ごとの色は [明るい縁, 中間, 暗い面]
private val TIER_COLORS: Map<String, List<Color>> = mapOf(
    "base" to listOf(Color(0xFF2DDFF1), Color(0xFF126280), Color(0xFF082D41)),
    "bronze" to listOf(Color(0xFFFFD3A1), Color(0xFFA45631), Color(0xFF48231E)),
    "silver" to listOf(Color(0xFFF4F8FF), Color(0xFF9BADC9), Color(0xFF303F5B)),
    "gold" to listOf(Color(0xFFFFF3A5), Color(0xFFDC9B19), Color(0xFF67430E)),
    "platinum" to listOf(Color(0xFFFFFAFF), Color(0xFFBBDFFF), Color(0xFF536E9E)),
    "aurora" to listOf(Color(0xFFEFFFFF), Color(0xFFA75DFF), Color(0xFF1237A5)),
)

private val BASE_FAMILY_COLOR = mapOf(
    "photo" to Color(0xFFAD51DF), "post" to Color(0xFF20C799), "first" to Color(0xFFEF8755), "event" to Color(0xFFF35DAA),
)

private fun svgPath(d: String): Path = PathParser().parsePathString(d).toPath()

@Composable
fun BadgeIcon(badge: BadgeDef, size: Dp, modifier: Modifier = Modifier) {
    Canvas(modifier.size(size)) {
        scale(this.size.width / 100f, pivot = Offset.Zero) { drawBadge(badge) }
    }
}

private fun DrawScope.drawBadge(badge: BadgeDef) {
    val (r, l, o) = TIER_COLORS[badge.tier] ?: TIER_COLORS.getValue("base")
    val d = if (badge.tier == "base") BASE_FAMILY_COLOR[badge.family] ?: l else l

    // 縁（斜めのグラデーション）と面（左上寄りの放射グラデーション）
    drawCircle(
        brush = Brush.linearGradient(listOf(r, d, r), start = Offset(3f, 3f), end = Offset(97f, 97f)),
        radius = 47f, center = Offset(50f, 50f),
    )
    drawCircle(
        brush = Brush.radialGradient(listOf(d, o), center = Offset(37.7f, 25.4f), radius = 69.7f),
        radius = 41f, center = Offset(50f, 50f),
    )
    drawCircle(color = r.copy(alpha = .45f), radius = 41f, center = Offset(50f, 50f), style = Stroke(1.5f))

    val round = Stroke(width = 2f, cap = StrokeCap.Round, join = StrokeJoin.Round)
    if (badge.tier != "base") {
        // 月桂樹の飾りと小さな星
        val deco = svgPath("M18 68 Q24 79 38 82 M82 68 Q76 79 62 82 M50 78 l3 5 5 2 -5 2 -3 5 -3-5 -5-2 5-2z")
        drawPath(deco, r, style = Fill)
        drawPath(deco, r, style = round)
    }
    if (badge.tier == "platinum" || badge.tier == "aurora") {
        drawPath(svgPath("M22 26 l2-6 2 6 6 2-6 2-2 6-2-6-6-2z M74 16 l1.5 4.5 4.5 1.5-4.5 1.5-1.5 4.5-1.5-4.5-4.5-1.5 4.5-1.5z"), r, style = Fill)
    }

    // 中央の図柄（上が白、下へ向かって縁の色になる）
    val symbol = Brush.verticalGradient(listOf(Color.White, r), startY = 19f, endY = 72f)
    fun fillStroke(pathData: String) {
        val p = svgPath(pathData)
        drawPath(p, symbol, style = Fill)
        drawPath(p, r, style = round)
    }
    when (badge.family) {
        "reply" -> {
            fillStroke("M25 35 Q25 27 34 27 H66 Q75 27 75 36 V53 Q75 62 66 62 H45 L31 71 L34 62 Q25 61 25 53z")
            listOf(40f, 50f, 60f).forEach { drawCircle(o, 2f, Offset(it, 45f)) }
        }
        "photo" -> {
            drawRoundRect(r, Offset(25f, 27f), Size(50f, 40f), CornerRadius(4f), style = Stroke(5f, join = StrokeJoin.Round))
            val sun = Offset(61f, 38f)
            drawCircle(symbol, 5f, sun); drawCircle(r, 5f, sun, style = round)
            fillStroke("M29 62 L42 45 L51 54 L58 47 L71 62z")
        }
        "post" -> fillStroke("M26 69 Q32 39 72 25 Q70 51 45 61 L34 64 L45 51 L29 62z")
        "first" -> {
            fillStroke("M25 65 H75 V70 H25z M34 61 A16 16 0 0 1 66 61z")
            drawPath(
                svgPath("M50 23 V31 M26 38 L33 42 M74 38 L67 42 M32 25 L37 32 M68 25 L63 32"),
                r, style = Stroke(4f, cap = StrokeCap.Round, join = StrokeJoin.Round),
            )
        }
        "anniversary" -> fillStroke("M43 36 L51 29 H57 V68 H47 V40 L43 43z")
        "event" -> fillStroke("M32 70 V27 H68 L60 39 L68 50 H35 V70z")
        "development" -> fillStroke("M35 29 A13 13 0 0 0 44 48 L63 67 L70 60 L51 41 A13 13 0 0 0 68 27 L59 35 L52 28 L60 20 A13 13 0 0 0 35 29z")
        else -> fillStroke("M50 23 L57 40 L75 41 L61 53 L66 71 L50 61 L34 71 L39 53 L25 41 L43 40z")
    }
}

/**
 * プロフィールの名前の下に出す実績バッジの行。タップで名前・条件・獲得日を表示する。
 * 付け外し（表示する最大5個の選択）は公開APIにないため、自分のプロフィールからはWebの設定を開く
 */
@Composable
fun ProfileBadgeRow(badges: List<DisplayedBadge>, isSelf: Boolean) {
    val c = North.colors
    val context = LocalContext.current
    var selected by remember { mutableStateOf<DisplayedBadge?>(null) }
    if (badges.isEmpty() && !isSelf) return

    Row(
        Modifier.padding(top = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        badges.forEach { b ->
            dev.rec877.north.badgeDef(b.key)?.let { def ->
                BadgeIcon(def, 30.dp, Modifier.clickable { selected = b })
            }
        }
        if (isSelf) {
            Text(
                if (badges.isEmpty()) "バッジを設定" else "バッジを管理",
                color = c.accent, fontSize = 13.sp, fontWeight = FontWeight.Bold,
                modifier = Modifier.clickable {
                    runCatching { CustomTabsIntent.Builder().build().launchUrl(context, Uri.parse("https://north.rip/settings/profile")) }
                },
            )
        }
    }

    selected?.let { b ->
        val def = dev.rec877.north.badgeDef(b.key)
        AlertDialog(
            onDismissRequest = { selected = null },
            containerColor = c.elev1,
            title = {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    if (def != null) BadgeIcon(def, 44.dp)
                    Text(def?.name ?: b.key, color = c.text, fontWeight = FontWeight.ExtraBold, fontSize = 18.sp)
                }
            },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    def?.let { Text(it.description, color = c.text, fontSize = 15.sp) }
                    b.awardedAt?.take(10)?.let { Text("獲得日: " + it.replace('-', '/'), color = c.textMuted, fontSize = 13.sp) }
                }
            },
            confirmButton = { TextButton(onClick = { selected = null }) { Text("閉じる", color = c.accent) } },
        )
    }
}