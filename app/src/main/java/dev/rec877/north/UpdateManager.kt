package dev.rec877.north

import android.app.DownloadManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.Settings
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.IOException

data class AppUpdate(
    val version: String,
    val tag: String,
    val apkUrl: String,
    val notes: String = "",
    val releaseUrl: String = "",
) {
    val emergency get() = tag.equals("emergency", ignoreCase = true)
}

/** 公開GitHub Releasesから、v1.2.3-regular / v1.2.3-emergency形式のAPKだけを取得する。 */
class UpdateManager(private val http: OkHttpClient, private val context: Context) {
    suspend fun releases(): List<AppUpdate> = withContext(Dispatchers.IO) {
        val request = Request.Builder().url("https://api.github.com/repos/Banana-Niku87729/North-for-Android-Unofficial-/releases")
            .header("Accept", "application/vnd.github+json").header("User-Agent", "North-for-Android").build()
        val body = http.newCall(request).execute().use { response ->
            if (!response.isSuccessful) return@withContext emptyList()
            response.body?.string() ?: return@withContext emptyList()
        }
        val releases = Json.parseToJsonElement(body).jsonArray.mapNotNull { element ->
            val obj = element.jsonObject
            val fullTag = obj["tag_name"]?.jsonPrimitive?.content ?: return@mapNotNull null
            val tag = fullTag.substringAfterLast('-', "").lowercase()
            if (tag != "regular" && tag != "emergency") return@mapNotNull null
            // 比較に使う版番号は自由入力のRelease titleではなく、規定形式のタグから得る。
            val version = fullTag.removePrefix("v").removeSuffix("-$tag")
            val apk = obj["assets"]?.jsonArray?.firstOrNull { it.jsonObject["name"]?.jsonPrimitive?.content?.endsWith(".apk", true) == true }
                ?.jsonObject?.get("browser_download_url")?.jsonPrimitive?.content ?: return@mapNotNull null
            AppUpdate(
                version, tag, apk,
                obj["body"]?.jsonPrimitive?.content.orEmpty(),
                obj["html_url"]?.jsonPrimitive?.content.orEmpty(),
            )
        }
        return@withContext releases.sortedWith { a, b ->
            when {
                a.emergency != b.emergency -> if (a.emergency) 1 else -1
                isNewer(a.version, b.version) -> 1
                isNewer(b.version, a.version) -> -1
                else -> 0
            }
        }.reversed()
    }

    suspend fun latest(current: String): AppUpdate? =
        releases().firstOrNull { isNewer(it.version, current) }

    fun download(update: AppUpdate): Long {
        val url = update.apkUrl.toHttpUrlOrNull()
            ?: throw IOException("更新ファイルのURLが不正です")
        if (!url.isHttps || url.host != "github.com") {
            throw IOException("更新ファイルの配布元を確認できません")
        }
        val safeVersion = update.version.replace(Regex("[^A-Za-z0-9._-]"), "_")
            .take(64).ifBlank { "update" }
        val request = DownloadManager.Request(Uri.parse(url.toString()))
            .setTitle("North ${update.version}")
            .setDescription("タップしてインストールを開始します")
            .setMimeType("application/vnd.android.package-archive")
            .setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED)
            .setDestinationInExternalFilesDir(context, Environment.DIRECTORY_DOWNLOADS, "North-$safeVersion.apk")
        val id = (context.getSystemService(Context.DOWNLOAD_SERVICE) as DownloadManager).enqueue(request)
        UpdateInstaller.rememberDownload(context, id)
        return id
    }

    private fun versionKey(v: String) = v.removePrefix("v").split('.', '-', '_').map { it.toIntOrNull() ?: 0 }
    private fun isNewer(candidate: String, current: String): Boolean {
        val a = versionKey(candidate); val b = versionKey(current); val count = maxOf(a.size, b.size)
        return (0 until count).firstOrNull { (a.getOrElse(it) { 0 }) != (b.getOrElse(it) { 0 }) }?.let { a.getOrElse(it) { 0 } > b.getOrElse(it) { 0 } } ?: false
    }
}

/**
 * DownloadManager の完了通知でアプリへ戻ったとき、取得済みAPKをAndroidの
 * パッケージインストーラーへ渡す。初回は「このアプリからのインストール」設定へ誘導する。
 */
object UpdateInstaller {
    private const val PREFS = "north_update"
    private const val KEY_DOWNLOAD_ID = "pending_download_id"

    fun rememberDownload(context: Context, downloadId: Long) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit()
            .putLong(KEY_DOWNLOAD_ID, downloadId).apply()
    }

    enum class Action { NONE, PERMISSION_REQUESTED, INSTALLER_OPENED }

    fun openPendingInstaller(activity: android.app.Activity, requestPermission: Boolean = true): Action {
        val prefs = activity.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val id = prefs.getLong(KEY_DOWNLOAD_ID, -1L)
        if (id < 0) return Action.NONE
        val downloads = activity.getSystemService(Context.DOWNLOAD_SERVICE) as DownloadManager
        val complete = downloads.query(DownloadManager.Query().setFilterById(id)).use { cursor ->
            cursor.moveToFirst() && cursor.getInt(cursor.getColumnIndexOrThrow(DownloadManager.COLUMN_STATUS)) == DownloadManager.STATUS_SUCCESSFUL
        }
        if (!complete) return Action.NONE

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O && !activity.packageManager.canRequestPackageInstalls()) {
            if (!requestPermission) return Action.NONE
            activity.startActivity(Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES)
                .setData(Uri.parse("package:${activity.packageName}")))
            return Action.PERMISSION_REQUESTED
        }
        val apk = downloads.getUriForDownloadedFile(id) ?: return Action.NONE
        // インストーラーを起動できた時点で消す。キャンセルした場合でも、設定から更新を再試行できる。
        prefs.edit().remove(KEY_DOWNLOAD_ID).apply()
        activity.startActivity(Intent(Intent.ACTION_VIEW)
            .setDataAndType(apk, "application/vnd.android.package-archive")
            .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION))
        return Action.INSTALLER_OPENED
    }
}
