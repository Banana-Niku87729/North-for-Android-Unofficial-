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

    /** follows.write を追加（フォロー/解除）。変更後は一度ログアウト→再ログインが必要 */
    const val SCOPES = "posts.read users.read posts.write reactions.write media.write notifications.read notifications.write follows.write drafts.read drafts.write trends.read profile.write bookmarks.read bookmarks.write lists.read lists.write"

    /** 相対URL（/media/... など）はAPIホスト基準で解決する */
    fun absUrl(u: String?): String? = when {
        u.isNullOrBlank() -> null
        u.startsWith("http://") || u.startsWith("https://") -> u
        u.startsWith("/") -> API_BASE + u
        else -> "$API_BASE/$u"
    }
}
