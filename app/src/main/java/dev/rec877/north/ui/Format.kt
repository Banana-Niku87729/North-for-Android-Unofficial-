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

/** 投票の残り時間。例: 残り3時間 / 残り2日 / 残り5分 */
fun pollRemaining(endsAtIso: String, now: Instant = Instant.now()): String {
    val end = try {
        OffsetDateTime.parse(endsAtIso).toInstant()
    } catch (e: Exception) {
        return ""
    }
    val s = Duration.between(now, end).seconds
    return when {
        s <= 0 -> "終了"
        s < 3600 -> "残り${(s / 60).coerceAtLeast(1)}分"
        s < 86400 -> "残り${s / 3600}時間"
        else -> "残り${s / 86400}日"
    }
}

/**
 * 長い本文を、280（全角2・半角1）ずつの連投に分ける。
 * 上限に近い位置に改行・句点・空白があればそこで区切り、なければ上限ちょうどで区切る。
 */
fun splitPostText(s: String, max: Int = 280): List<String> {
    val out = mutableListOf<String>()
    var start = 0
    while (start < s.length) {
        var i = start
        var w = 0
        var lastBreak = -1
        while (i < s.length) {
            val ch = s[i]
            val cw = if (ch.code <= 0x7F) 1 else 2
            if (w + cw > max) break
            w += cw
            i++
            if (ch == '\n' || ch == '。' || ch == '！' || ch == '？' || ch == ' ') lastBreak = i
        }
        if (i >= s.length) {
            out += s.substring(start)
            break
        }
        if (i > start && s[i - 1].isHighSurrogate()) i-- // サロゲートペアを分断しない
        val cut = if (lastBreak > start + (i - start) / 2) lastBreak else i
        out += s.substring(start, cut)
        start = cut
    }
    return out.map { it.trim('\n', ' ') }.filter { it.isNotEmpty() }
}
