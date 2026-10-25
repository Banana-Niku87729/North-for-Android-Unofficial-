package dev.rec877.north.ui

import androidx.compose.foundation.Image
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.addPathNodes
import androidx.compose.ui.graphics.vector.rememberVectorPainter
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

@Immutable
class NIcon(
    val strokes: List<String> = emptyList(),
    val fills: List<String> = emptyList(),
    val strokeWidth: Float = 1.8f,
    val viewport: Float = 24f,
    /** viewBox の左上座標（スパークルのみ -128 から始まる） */
    val origin: Float = 0f,
)

object NIcons {
    val Message = NIcon(strokes = listOf("M3 5h18v14H3Z", "m3 5 9 7 9-7"))
    val Reply = NIcon(strokes = listOf(
        "M4 11.5c0-3.6 3.1-6.5 7-6.5h2c3.9 0 7 2.9 7 6.5S16.9 18 13 18h-1.5L7 21.2V17.4C5.2 16.2 4 14 4 11.5Z"
    ))
    val Repost = NIcon(strokes = listOf(
        "M6.5 20V7.5A2.5 2.5 0 0 1 9 5h8",
        "m14.5 2.4 3 2.6-3 2.6",
        "M17.5 4v12.5a2.5 2.5 0 0 1-2.5 2.5H7",
        "m9.5 21.6-3-2.6 3-2.6",
    ))
    val Like = NIcon(strokes = listOf(
        "M12 20.3 4.9 13.6a4.6 4.6 0 0 1 0-6.6 4.9 4.9 0 0 1 7.1.5 4.9 4.9 0 0 1 7.1-.5 4.6 4.6 0 0 1 0 6.6z"
    ))
    /** X系のブックマークを示す、しおり形のアイコン。 */
    val Bookmark = NIcon(strokes = listOf(
        "M6.5 4.5h11a1.5 1.5 0 0 1 1.5 1.5v14l-7-4.2L5 20V6a1.5 1.5 0 0 1 1.5-1.5Z"
    ))
    val Share = NIcon(strokes = listOf(
        "M12 15.5V4", "m8 8 4-4 4 4", "M5 12.5v6a2 2 0 0 0 2 2h10a2 2 0 0 0 2-2v-6"
    ))
    val Image = NIcon(strokes = listOf(
        "M6 4.5h12a2.5 2.5 0 0 1 2.5 2.5v10a2.5 2.5 0 0 1-2.5 2.5H6a2.5 2.5 0 0 1-2.5-2.5V7A2.5 2.5 0 0 1 6 4.5Z",
        "M6.9 9.5a1.6 1.6 0 1 0 3.2 0a1.6 1.6 0 1 0-3.2 0Z",
        "m4.5 17 4.6-4.6 3.4 3.4 2.6-2.6 4.4 4.4",
    ))
    val Home = NIcon(strokes = listOf(
        "M4 11.2 12 4l8 7.2V19a1.5 1.5 0 0 1-1.5 1.5H15v-5.5H9v5.5H5.5A1.5 1.5 0 0 1 4 19Z"
    ))
    val Search = NIcon(strokes = listOf(
        "M11 4.5a6.5 6.5 0 1 0 0 13a6.5 6.5 0 1 0 0-13Z",
        "m20 20-4.2-4.2",
    ), strokeWidth = 2f)
    val Bell = NIcon(strokes = listOf(
        // 他のナビアイコンと描画の中心を揃える（元のパスを2単位上へ）。
        "M6 15v-5.5a6 6 0 1 1 12 0V15l1.6 2.2H4.4Z",
        "M10 19.5a2.2 2.2 0 0 0 4 0",
    ))
    val Person = NIcon(strokes = listOf(
        "M12 12a4 4 0 1 0 0-8a4 4 0 0 0 0 8Z",
        "M4.5 20.5a7.5 7.5 0 0 1 15 0",
    ))
    val Edit = NIcon(strokes = listOf(
        "M4 20h4L19 9a2.1 2.1 0 0 0-3-3L5 17v3Z",
        "m14.5 7.5 2 2",
    ))
    val Back = NIcon(strokes = listOf("M19 12H5", "m11 18-6-6 6-6"), strokeWidth = 2.2f)
    val Close = NIcon(strokes = listOf("M6 6l12 12M18 6L6 18"), strokeWidth = 2.2f)
    val More = NIcon(fills = listOf(
        "M5 10.5a1.5 1.5 0 1 0 0 3a1.5 1.5 0 1 0 0-3Z",
        "M12 10.5a1.5 1.5 0 1 0 0 3a1.5 1.5 0 1 0 0-3Z",
        "M19 10.5a1.5 1.5 0 1 0 0 3a1.5 1.5 0 1 0 0-3Z",
    ))
    val Pin = NIcon(strokes = listOf(
        "m8 4 8 0", "m9 4 1 6-3 3h10l-3-3 1-6", "M12 13v7", "m10 20 2 2 2-2",
    ))
    val Sparkle = NIcon(
        fills = listOf("M0 -118 C 1.4 -24 15 -2.2 80 0 C 15 2.2 1.4 24 0 118 C -1.4 24 -15 2.2 -80 0 C -15 -2.2 -1.4 -24 0 -118 Z"),
        viewport = 256f,
        origin = -128f,
    )
}

@Composable
fun NorthIcon(
    icon: NIcon,
    tint: Color,
    size: Dp = 20.dp,
    filled: Boolean = false,
    modifier: Modifier = Modifier,
) {
    val vector = remember(icon, tint, filled, size) {
        ImageVector.Builder(
            defaultWidth = size,
            defaultHeight = size,
            viewportWidth = icon.viewport,
            viewportHeight = icon.viewport,
        ).apply {
            if (icon.origin != 0f) addGroup(translationX = -icon.origin, translationY = -icon.origin)
            icon.strokes.forEach { d ->
                addPath(
                    pathData = addPathNodes(d),
                    fill = if (filled) SolidColor(tint) else null,
                    stroke = SolidColor(tint),
                    strokeLineWidth = icon.strokeWidth,
                    strokeLineCap = StrokeCap.Round,
                    strokeLineJoin = StrokeJoin.Round,
                )
            }
            icon.fills.forEach { d ->
                addPath(pathData = addPathNodes(d), fill = SolidColor(tint))
            }
        }.build()
    }
    Image(
        painter = rememberVectorPainter(vector),
        contentDescription = null,
        modifier = modifier,
    )
}
