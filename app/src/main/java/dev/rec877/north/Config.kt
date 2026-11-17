package dev.rec877.north

object Config {
    const val GITHUB_RELEASES = "https://github.com/Banana-Niku87729/North-for-Android-Unofficial-/releases"
    /** 設定 › 開発者向け で登録して得たclient_id */
    const val CLIENT_ID = "cmurqwr0y00iuwhzkrbfh56bm"

    const val API_BASE = "https://api.north.rip"

    /** northに登録したリダイレクトURIと1文字も違わないこと（完全一致で検査されます） */
    const val REDIRECT_URI = "https://north-callback.nikubanana809.workers.dev/callback"

    /** Workerの APP_SCHEME / AndroidManifest と揃える */
    const val APP_SCHEME = "rec877north"

    /**
     * posts.edit（ポスト編集）/ polls.vote（投票）/ moderation.read・moderation.write（ミュート・ブロック）を追加。
     * 変更後は一度ログアウト→再ログインが必要
     */
    const val SCOPES = "posts.read users.read posts.write posts.edit posts.delete polls.vote reactions.write media.write notifications.read notifications.write follows.write moderation.read moderation.write drafts.read drafts.write trends.read profile.write bookmarks.read bookmarks.write lists.read lists.write"

    /**
     * 端末内判定モデル(nsfw.tflite, 約22.5MB)。APKには同梱せず、初回の判定時にここから取得する。
     * リビジョン固定のURLなので内容は変わらない。自前のReleaseなどへ置く場合はURLだけ差し替え、
     * SHA-256はそのままでよい(一致しなければ破棄して使わない)。
     */
    const val NSFW_MODEL_URL = "https://raw.githubusercontent.com/hoyaaaa/nsfw_detector_flutter/77f6c72397113c7facb7ba4783ccfe70caec7c81/assets/nsfw.tflite"
    const val NSFW_MODEL_SHA256 = "a476ef325f4f776602d944fd28133e945f77a74e37dafba37936110b35b7810d"

    /** 相対URL（/media/... など）はAPIホスト基準で解決する */
    fun absUrl(u: String?): String? = when {
        u.isNullOrBlank() -> null
        u.startsWith("http://") || u.startsWith("https://") -> u
        u.startsWith("/") -> API_BASE + u
        else -> "$API_BASE/$u"
    }
}