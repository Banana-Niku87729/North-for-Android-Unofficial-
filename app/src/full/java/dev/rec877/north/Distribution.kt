package dev.rec877.north

/**
 * 配布形態ごとの違い。GitHub Releases で配るAPK（full）用。
 * F-Droid 版は src/fdroid/ に同名のオブジェクトがあり、値だけが違う。
 */
object Distribution {
    /** アプリ内アップデート（GitHub Releases の確認・APKのダウンロード・インストール） */
    const val SELF_UPDATE = true

    /** 端末内判定モデルを初回に外部(Config.NSFW_MODEL_URL)から取得する */
    const val MODEL_DOWNLOAD = true
}
