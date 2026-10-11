package dev.rec877.north

fun notificationAction(kind: String): String = when (kind) {
    "FOLLOW" -> "にフォローされました"
    "LIKE" -> "があなたのポストをいいねしました"
    "RETWEET" -> "があなたのポストをリポストしました"
    "QUOTE" -> "があなたのポストを引用しました"
    "REPLY" -> "があなたに返信しました"
    "MENTION" -> "があなたをメンションしました"
    "POST" -> "が新しくポストしました"
    else -> "からの通知です"
}

/** 先頭の人の名前 と 「さん」「さんほかN人」 */
fun notificationWho(n: Notification): Pair<String, String> {
    val first = n.actors.firstOrNull()?.name ?: "だれか"
    val others = (n.actorCount - 1).coerceAtLeast(0)
    return first to (if (others > 0) "さんほか${others}人" else "さん")
}

fun notificationSummary(n: Notification): String {
    val (name, tail) = notificationWho(n)
    return name + tail + notificationAction(n.kind)
}