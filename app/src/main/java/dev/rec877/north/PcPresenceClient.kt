package dev.rec877.north

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.json.Json
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.io.IOException

object PcPresenceClient {
    const val PREFERENCE_KEY = "suppress_mobile_notifications_when_pc_active"

    @Serializable
    data class PresenceStatus(
        val active: Boolean,
        val pcOnline: Boolean,
        val mobileOnline: Boolean,
    )

    private val json = Json { ignoreUnknownKeys = true }

    suspend fun setMobileOnline(app: NorthApp, online: Boolean): PresenceStatus = withContext(Dispatchers.IO) {
        val token = app.auth.accessToken()
        val requestBody = """{"online":$online}"""
            .toRequestBody("application/json; charset=utf-8".toMediaType())
        val request = Request.Builder()
            .url("${Config.PC_PRESENCE_BASE}/v1/mobile-presence")
            .header("Authorization", "Bearer $token")
            .post(requestBody)
            .build()
        app.http.newCall(request).execute().use { response ->
            if (!response.isSuccessful) throw IOException("Presence update failed (HTTP ${response.code})")
            val body = response.body?.string() ?: throw IOException("PC presence response was empty")
            json.decodeFromString<PresenceStatus>(body)
        }
    }
}
