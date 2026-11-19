package dev.rec877.north

/**
 * F-Droid 配布版。
 * - 更新は F-Droid が行うので、アプリ内アップデートは無効（REQUEST_INSTALL_PACKAGES も外す）。
 * - 判定モデルは外部から取得せず、APKに同梱した assets/care/nsfw.tflite だけを使う。
 */
object Distribution {
    const val SELF_UPDATE = false
    const val MODEL_DOWNLOAD = false
}
