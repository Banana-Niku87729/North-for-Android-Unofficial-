package dev.rec877.north

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.tensorflow.lite.Interpreter
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.FileInputStream
import java.io.IOException
import java.nio.channels.FileChannel
import java.security.MessageDigest
import java.util.concurrent.TimeUnit
import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlin.coroutines.coroutineContext

enum class MediaVerdict { ALLOW, BLOCK, UNKNOWN }

/**
 * 推論は端末内のモデルでのみ実行。画像・スコアを外部へ送信しない。
 * モデル本体(約22.5MB)はAPKに同梱せず、初回の判定時に Config.NSFW_MODEL_URL から1度だけ取得して
 * filesDir に保存する。SHA-256が一致しないファイルは破棄する。
 */
class OnDeviceMediaClassifier(private val context: Context) {
    private val mutex = Mutex()
    private val lock = Any()
    private var interpreter: Interpreter? = null
    private var closed = false
    private val modelFile = File(context.filesDir, "care/nsfw.tflite")
    private var lastDownloadFailureAt = 0L

    suspend fun classify(media: Media, client: OkHttpClient): MediaVerdict = withContext(Dispatchers.IO) {
        // 動画やアニメーションの先頭フレームだけで全体を許可しない。
        if (media.sensitive || media.kind != "PHOTO") return@withContext MediaVerdict.BLOCK
        mutex.withLock {
            try {
                coroutineContext.ensureActive()
                // モデル未取得ならここで取得する。失敗時は閉じたまま(非表示)にして、しばらく再試行しない。
                if (!ensureModel(client)) return@withLock MediaVerdict.UNKNOWN
                val url = Config.absUrl(media.url) ?: return@withLock MediaVerdict.UNKNOWN
                val bytes = client.newCall(Request.Builder().url(url).build()).execute().use { response ->
                    if (!response.isSuccessful) return@withLock MediaVerdict.UNKNOWN
                    val body = response.body ?: return@withLock MediaVerdict.UNKNOWN
                    if (body.contentLength() > MAX_BYTES) return@withLock MediaVerdict.UNKNOWN
                    body.byteStream().use { input ->
                        val output = ByteArrayOutputStream()
                        val buffer = ByteArray(8192)
                        while (true) {
                            coroutineContext.ensureActive()
                            val n = input.read(buffer)
                            if (n < 0) break
                            if (output.size() + n > MAX_BYTES) return@withLock MediaVerdict.UNKNOWN
                            output.write(buffer, 0, n)
                        }
                        output.toByteArray()
                    }
                }
                // PHOTO扱いで返されたGIF・animated WebP/APNGも解禁しない。
                if (isAnimatedContainer(bytes)) return@withLock MediaVerdict.BLOCK
                val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
                BitmapFactory.decodeByteArray(bytes, 0, bytes.size, bounds)
                if (bounds.outMimeType !in setOf("image/jpeg", "image/png", "image/webp", "image/bmp")) return@withLock MediaVerdict.UNKNOWN
                if (bounds.outWidth <= 0 || bounds.outHeight <= 0 || bounds.outWidth.toLong() * bounds.outHeight > 64_000_000) return@withLock MediaVerdict.UNKNOWN
                var sample = 1
                while (maxOf(bounds.outWidth, bounds.outHeight) / sample > 1024) sample *= 2
                val bitmap = BitmapFactory.decodeByteArray(bytes, 0, bytes.size, BitmapFactory.Options().apply { inSampleSize = sample })
                    ?: return@withLock MediaVerdict.UNKNOWN
                val score = try { score(bitmap) } finally { bitmap.recycle() }
                if (!score.isFinite() || score !in 0f..1f) MediaVerdict.UNKNOWN
                else if (score >= .4f) MediaVerdict.BLOCK else MediaVerdict.ALLOW
            } catch (e: kotlinx.coroutines.CancellationException) { throw e
            } catch (_: Exception) { MediaVerdict.UNKNOWN
            } catch (_: LinkageError) { MediaVerdict.UNKNOWN }
        }
    }

    internal fun score(bitmap: Bitmap): Float = synchronized(lock) {
        check(!closed)
        val model = interpreter ?: try {
            FileInputStream(modelFile).use { stream ->
                val buffer = stream.channel.map(FileChannel.MapMode.READ_ONLY, 0, modelFile.length())
                Interpreter(buffer, Interpreter.Options().setNumThreads(2)).also {
                    check(it.getInputTensor(0).shape().contentEquals(intArrayOf(1, 224, 224, 3)))
                    check(it.getOutputTensor(0).shape().contentEquals(intArrayOf(1, 2)))
                    interpreter = it
                }
            }
        } catch (e: Exception) {
            // 壊れたモデルは削除し、次回の判定で取得し直す。
            modelFile.delete()
            throw e
        }
        val scaled = Bitmap.createScaledBitmap(bitmap, 224, 224, true)
        try {
            val pixels = IntArray(224 * 224)
            scaled.getPixels(pixels, 0, 224, 0, 0, 224, 224)
            val input = ByteBuffer.allocateDirect(pixels.size * 12).order(ByteOrder.nativeOrder())
            for (pixel in pixels) {
                input.putFloat((pixel and 255) - 103.939f)
                input.putFloat(((pixel ushr 8) and 255) - 116.779f)
                input.putFloat(((pixel ushr 16) and 255) - 123.68f)
            }
            input.rewind()
            val output = arrayOf(FloatArray(2))
            model.run(input, output)
            output[0][1]
        } finally { if (scaled !== bitmap) scaled.recycle() }
    }

    /** モデルが端末内にあれば true。なければ取得して SHA-256 を検証し、原子的に配置する。 */
    private suspend fun ensureModel(client: OkHttpClient): Boolean {
        if (modelFile.isFile && modelFile.length() > 0) return true
        // 外部取得を行わない配布形態(F-Droid版)は、APKに同梱されたモデルだけを使う。
        if (!Distribution.MODEL_DOWNLOAD) return installBundledModel()
        if (System.currentTimeMillis() - lastDownloadFailureAt < RETRY_COOLDOWN_MS) return false
        val part = File(modelFile.parentFile, "nsfw.tflite.part")
        return try {
            modelFile.parentFile?.mkdirs()
            val http = client.newBuilder().callTimeout(5, TimeUnit.MINUTES).readTimeout(60, TimeUnit.SECONDS).build()
            val digest = MessageDigest.getInstance("SHA-256")
            http.newCall(Request.Builder().url(Config.NSFW_MODEL_URL).build()).execute().use { response ->
                if (!response.isSuccessful) throw IOException("HTTP ${response.code}")
                val body = response.body ?: throw IOException("empty body")
                body.byteStream().use { input ->
                    part.outputStream().use { out ->
                        val buffer = ByteArray(64 * 1024)
                        var total = 0L
                        while (true) {
                            coroutineContext.ensureActive()
                            val n = input.read(buffer)
                            if (n < 0) break
                            total += n
                            if (total > MODEL_MAX_BYTES) throw IOException("model too large")
                            digest.update(buffer, 0, n)
                            out.write(buffer, 0, n)
                        }
                    }
                }
            }
            val actual = digest.digest().joinToString("") { "%02x".format(it) }
            if (!actual.equals(Config.NSFW_MODEL_SHA256, ignoreCase = true)) throw IOException("model hash mismatch")
            if (!part.renameTo(modelFile)) throw IOException("rename failed")
            true
        } catch (e: kotlinx.coroutines.CancellationException) { part.delete(); throw e
        } catch (_: Exception) {
            part.delete()
            lastDownloadFailureAt = System.currentTimeMillis()
            false
        }
    }

    /** assets/care/nsfw.tflite（同梱時のみ存在）を filesDir へ展開する。SHA-256が合わなければ使わない。 */
    private fun installBundledModel(): Boolean {
        val part = File(modelFile.parentFile, "nsfw.tflite.part")
        return try {
            modelFile.parentFile?.mkdirs()
            val digest = MessageDigest.getInstance("SHA-256")
            context.assets.open("care/nsfw.tflite").use { input ->
                part.outputStream().use { out ->
                    val buffer = ByteArray(64 * 1024)
                    while (true) {
                        val n = input.read(buffer)
                        if (n < 0) break
                        digest.update(buffer, 0, n)
                        out.write(buffer, 0, n)
                    }
                }
            }
            val actual = digest.digest().joinToString("") { "%02x".format(it) }
            if (!actual.equals(Config.NSFW_MODEL_SHA256, ignoreCase = true)) throw IOException("bundled model hash mismatch")
            if (!part.renameTo(modelFile)) throw IOException("rename failed")
            true
        } catch (_: Exception) {
            part.delete()
            false
        }
    }

    fun close() = synchronized(lock) { closed = true; interpreter?.close(); interpreter = null }

    companion object {
        private const val MAX_BYTES = 12 * 1024 * 1024
        private const val MODEL_MAX_BYTES = 40L * 1024 * 1024
        private const val RETRY_COOLDOWN_MS = 60_000L
        internal fun isAnimatedContainer(bytes: ByteArray): Boolean {
            if (bytes.size >= 3 && String(bytes, 0, 3, Charsets.US_ASCII) == "GIF") return true
            if (bytes.size >= 21 && String(bytes, 8, 4, Charsets.US_ASCII) == "WEBP" &&
                String(bytes, 12, 4, Charsets.US_ASCII) == "VP8X" && (bytes[20].toInt() and 2) != 0) return true
            // APNGのacTLチャンク。未知の形式はBitmapデコード失敗として閉じたままにする。
            if (bytes.size > 8 && bytes[0] == 0x89.toByte() && bytes[1] == 0x50.toByte()) {
                var pos = 8
                while (pos + 12 <= bytes.size) {
                    val length = ByteBuffer.wrap(bytes, pos, 4).int
                    if (length < 0 || length > bytes.size - pos - 12) break
                    if (String(bytes, pos + 4, 4, Charsets.US_ASCII) == "acTL") return true
                    pos += length + 12
                }
            }
            return false
        }
    }
}