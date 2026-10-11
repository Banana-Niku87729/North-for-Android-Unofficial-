package dev.rec877.north

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

/**
 * Android Keystore のAES鍵で暗号化して SharedPreferences に置く小さな保管庫。
 * 更新トークンはローテーションされるので、書き込みは commit()（同期）で確実に残す。
 */
class SecretStore(context: Context) {
    private val prefs =
        context.applicationContext.getSharedPreferences("north_secrets", Context.MODE_PRIVATE)

    private fun key(): SecretKey {
        val ks = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
        (ks.getKey(ALIAS, null) as? SecretKey)?.let { return it }
        val gen = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, "AndroidKeyStore")
        gen.init(
            KeyGenParameterSpec.Builder(
                ALIAS, KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT
            )
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .setKeySize(256)
                .build()
        )
        return gen.generateKey()
    }

    private fun encrypt(plain: String): String {
        val c = Cipher.getInstance("AES/GCM/NoPadding").apply { init(Cipher.ENCRYPT_MODE, key()) }
        val out = c.iv + c.doFinal(plain.toByteArray(Charsets.UTF_8))
        return Base64.encodeToString(out, Base64.NO_WRAP)
    }

    private fun decrypt(data: String): String {
        val raw = Base64.decode(data, Base64.NO_WRAP)
        val c = Cipher.getInstance("AES/GCM/NoPadding").apply {
            init(Cipher.DECRYPT_MODE, key(), GCMParameterSpec(128, raw.copyOfRange(0, 12)))
        }
        return String(c.doFinal(raw, 12, raw.size - 12), Charsets.UTF_8)
    }

    fun get(name: String): String? {
        val s = prefs.getString(name, null) ?: return null
        return try { decrypt(s) } catch (e: Exception) { null } // 鍵が失われた等 → 未ログイン扱い
    }

    fun put(name: String, value: String) {
        prefs.edit().putString(name, encrypt(value)).commit()
    }

    fun remove(name: String) {
        prefs.edit().remove(name).commit()
    }

    private companion object {
        const val ALIAS = "north_secret_key"
    }
}
