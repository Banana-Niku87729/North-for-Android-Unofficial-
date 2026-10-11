package dev.rec877.north.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Density

/** north.rip の CSS変数（--north-*）に対応するパレット */
@Immutable
class NorthColors(
    val bg: Color,
    val elev1: Color,
    val elev2: Color,
    val elev3: Color,
    val border: Color,
    val borderStrong: Color,
    val text: Color,
    val textMuted: Color,
    val textFaint: Color,
    val accent: Color,
    val textOnAccent: Color,
    val like: Color,
    val repost: Color,
    /** 「ポストする」ボタンの背景/文字色（--north-post / --north-text-on-post） */
    val post: Color,
    val textOnPost: Color,
    val danger: Color,
)

/** サイトの設定: テーマ=midnight / アクセント=yellow */
data class ThemePreset(val id: String, val label: String, val color: Long, val light: Boolean = false)

/** 参考CSSの --north-theme-* をアプリでも選択できるようにした一覧。 */
val ThemePresets = listOf(
    ThemePreset("default", "デフォルト", 0xFFFFFFFF, true), ThemePreset("dim", "ダークブルー", 0xFF15202B),
    ThemePreset("lightsout", "ライトアウト", 0xFF000000), ThemePreset("wine", "ワイン", 0xFF0D0206),
    ThemePreset("forest", "フォレスト", 0xFF030D08), ThemePreset("midnight", "ミッドナイト", 0xFF040512),
    ThemePreset("cloud", "クラウド", 0xFFF2F5F7, true), ThemePreset("cream", "クリーム", 0xFFFFF7E6, true),
    ThemePreset("slate", "スレート", 0xFF263442), ThemePreset("ocean", "オーシャン", 0xFF123746),
    ThemePreset("plum", "プラム", 0xFF180D1C), ThemePreset("espresso", "エスプレッソ", 0xFF17100B),
    ThemePreset("paper", "ペーパー", 0xFFFBFBF9, true), ThemePreset("sakura", "さくら", 0xFFFFF0F5, true),
    ThemePreset("daylight", "デイライト", 0xFFEEF4FD, true), ThemePreset("sage", "セージ", 0xFFEEF4EE, true),
    ThemePreset("steel", "スチール", 0xFF2F3A45), ThemePreset("denim", "デニム", 0xFF1B2A41),
    ThemePreset("moss", "モス", 0xFF1E2A20), ThemePreset("rust", "ラスト", 0xFF2A1C16),
    ThemePreset("grape", "グレープ", 0xFF1A1030), ThemePreset("carbon", "カーボン", 0xFF121212),
    ThemePreset("abyss", "アビス", 0xFF00121F), ThemePreset("obsidian", "オブシディアン", 0xFF050505),
    ThemePreset("ink", "インク", 0xFF05070D), ThemePreset("tar", "タール", 0xFF08080A),
    ThemePreset("basalt", "バサルト", 0xFF0C0F12), ThemePreset("voidtheme", "ヴォイド", 0xFF010104),
    ThemePreset("soot", "スート", 0xFF0B0908), ThemePreset("deepsea", "ディープシー", 0xFF00121A),
)

fun northColors(themeId: String = "midnight", accent: Color = Color(0xFFFFD400)) : NorthColors {
    val theme = ThemePresets.firstOrNull { it.id == themeId } ?: ThemePresets.first { it.id == "midnight" }
    val bg = Color(theme.color)
    val light = theme.light
    return NorthColors(
    bg = bg,
    elev1 = if (light) Color(0xFFF8F9FA) else Color(0xFF10132B),
    elev2 = if (light) Color(0xFFE9EDF0) else Color(0xFF181C3A),
    elev3 = if (light) Color(0xFFD9E0E5) else Color(0xFF242949),
    border = if (light) Color(0xFFCED6DC) else Color(0xFF303657),
    borderStrong = if (light) Color(0xFF98A4AE) else Color(0xFF4A5180),
    text = if (light) Color(0xFF17212B) else Color(0xFFE7E9F2),
    textMuted = if (light) Color(0xFF52616B) else Color(0xFF9AA0C9),
    textFaint = if (light) Color(0xFF73818B) else Color(0xFF747BA8),
    accent = accent,
    textOnAccent = Color(0xFF0F1419),
    like = Color(0xFFF91880),
    repost = Color(0xFF00BA7C),
    post = if (light) Color(0xFF17212B) else Color(0xFFFFFFFF),
    textOnPost = if (light) Color.White else Color(0xFF040512),
    danger = Color(0xFFF4212E),
    )
}

fun midnightColors(accent: Color = Color(0xFFFFD400), dimBackground: Boolean = false) =
    northColors(if (dimBackground) "lightsout" else "midnight", accent)

val MidnightYellow = midnightColors()

val LocalNorthColors = staticCompositionLocalOf { MidnightYellow }

object North {
    val colors: NorthColors
        @Composable @ReadOnlyComposable get() = LocalNorthColors.current
}

@Composable
fun NorthTheme(colors: NorthColors = MidnightYellow, fontScale: Float = 1f, content: @Composable () -> Unit) {
    val currentDensity = LocalDensity.current
    val scheme = darkColorScheme(
        primary = colors.accent,
        onPrimary = colors.textOnAccent,
        background = colors.bg,
        onBackground = colors.text,
        surface = colors.bg,
        onSurface = colors.text,
        surfaceVariant = colors.elev2,
        onSurfaceVariant = colors.textMuted,
        surfaceContainer = colors.elev2,
        surfaceContainerHigh = colors.elev2,
        outline = colors.border,
        error = colors.danger,
    )
    CompositionLocalProvider(LocalNorthColors provides colors, LocalDensity provides Density(currentDensity.density, fontScale)) {
        MaterialTheme(colorScheme = scheme, content = content)
    }
}
