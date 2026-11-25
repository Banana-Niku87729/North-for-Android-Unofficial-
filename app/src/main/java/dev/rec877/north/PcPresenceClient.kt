package dev.rec877.north

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.json.Json
import okhttp3.Request
import java.io.IOException

object PcPresenceClient {
    const val PREFERENCE_KEY = "suppress_mobile_notifications_when_pc_active"

    @Serializable
    private data class PresenceStatus(val active: Boolean)

    private val json = Json { ignoreUnknownKeys = true }

    suspend fun isPcActive(app: NorthApp): Boolean = withContext(Dispatchers.IO) {
        val token = app.auth.accessToken()
        val request = Request.Builder()
            .url("${Config.PC_PRESENCE_BASE}/v1/presence")
            .header("Authorization", "Bearer $token")
            .get()
            .build()
        app.http.newCall(request).execute().use { response ->
            if (!response.isSuccessful) throw IOException("PC presence check failed (HTTP ${response.code})")
            val body = response.body?.string() ?: throw IOException("PC presence response was empty")
            json.decodeFromString<PresenceStatus>(body).active
        }
    }
}
