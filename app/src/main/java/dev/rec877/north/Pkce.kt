package dev.rec877.north

import android.util.Base64
import java.security.MessageDigest
import java.security.SecureRandom

object Pkce {
    private val rng = SecureRandom()

    private fun b64url(bytes: ByteArray): String =
        Base64.encodeToString(bytes, Base64.URL_SAFE or Base64.NO_PADDING or Base64.NO_WRAP)

    /** code_verifier / state 用。48バイト→64文字（verifierの条件 43〜128文字を満たす） */
    fun randomString(byteLen: Int = 48): String =
        b64url(ByteArray(byteLen).also { rng.nextBytes(it) })

    /** S256のみ対応 */
    fun challenge(verifier: String): String =
        b64url(MessageDigest.getInstance("SHA-256").digest(verifier.toByteArray(Charsets.US_ASCII)))
}
