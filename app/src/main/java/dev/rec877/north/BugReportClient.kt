package dev.rec877.north

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.io.IOException

/** Workerの公開URLだけを呼ぶ。Discord Webhookの資格情報はこのAPKに含めない。 */
object BugReportClient {
    private val jsonMedia = "application/json; charset=utf-8".toMediaType()

    suspend fun send(
        http: OkHttpClient,
        endpoint: String,
        text: String,
        accountId: String?,
        handle: String?,
    ) = withContext(Dispatchers.IO) {
        val url = endpoint.toHttpUrlOrNull()
            ?: throw IOException("不具合報告の送信先URLが不正です")
        if (url.scheme != "https") throw IOException("不具合報告の送信先はHTTPSである必要があります")
        val payload = buildJsonObject {
            put("text", text.take(2_000))
            put("accountId", accountId.orEmpty())
            put("handle", handle.orEmpty())
            put("appVersion", BuildConfig.VERSION_NAME)
        }.toString().toRequestBody(jsonMedia)
        http.newCall(
            Request.Builder().url(url).post(payload).header("Content-Type", "application/json").build()
        ).execute().use { response ->
            if (!response.isSuccessful) throw IOException("不具合報告の送信に失敗しました（HTTP ${response.code}）")
        }
    }
}
