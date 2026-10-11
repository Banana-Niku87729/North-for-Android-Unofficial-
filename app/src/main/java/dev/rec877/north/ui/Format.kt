package dev.rec877.north.ui

import java.time.Duration
import java.time.Instant
import java.time.OffsetDateTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

/** サイトと同じ表記: 秒 / 分 / 時間 / M月d日 / yyyy年M月d日 */
fun relativeTime(iso: String, now: Instant = Instant.now()): String {
    val t = try {
        OffsetDateTime.parse(iso).toInstant()
    } catch (e: Exception) {
        return ""
    }
    val s = Duration.between(t, now).seconds.coerceAtLeast(0)
    return when {
        s < 60 -> "${s}秒"
        s < 3600 -> "${s / 60}分"
        s < 86400 -> "${s / 3600}時間"
        else -> {
            val z = ZoneId.systemDefault()
            val d = t.atZone(z)
            val n = now.atZone(z)
            if (d.year == n.year) "${d.monthValue}月${d.dayOfMonth}日"
            else "${d.year}年${d.monthValue}月${d.dayOfMonth}日"
        }
    }
}

/** 全角=2・半角=1（northの280カウントの目安。最終判定はサーバー） */
fun postWeight(s: String): Int = s.fold(0) { acc, c -> acc + if (c.code <= 0x7F) 1 else 2 }

/** 例: 午後 3:20 · 2026年10月3日 */
fun absoluteTime(iso: String): String = try {
    OffsetDateTime.parse(iso).atZoneSameInstant(ZoneId.systemDefault())
        .format(DateTimeFormatter.ofPattern("a h:mm · yyyy年M月d日", Locale.JAPAN))
} catch (e: Exception) {
    ""
}

/** 例: 2025年3月 */
fun joinedMonth(iso: String?): String = try {
    OffsetDateTime.parse(iso).atZoneSameInstant(ZoneId.systemDefault())
        .format(DateTimeFormatter.ofPattern("yyyy年M月", Locale.JAPAN))
} catch (e: Exception) {
    ""
}