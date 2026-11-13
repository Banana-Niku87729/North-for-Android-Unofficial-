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
import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlin.coroutines.coroutineContext

enum class MediaVerdict { ALLOW, BLOCK, UNKNOWN }

/** 推論は同梱モデルでのみ実行。画像・スコアを外部へ送信しない。 */
class OnDeviceMediaClassifier(private val context: Context) {
    private val mutex = Mutex()
    private val lock = Any()
    private var interpreter: Interpreter? = null
    private var closed = false

    suspend fun classify(media: Media, client: OkHttpClient): MediaVerdict = withContext(Dispatchers.IO) {
        // 動画やアニメーションの先頭フレームだけで全体を許可しない。
        if (media.sensitive || media.kind != "PHOTO") return@withContext MediaVerdict.BLOCK
        mutex.withLock {
            try {
                coroutineContext.ensureActive()
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
        val model = interpreter ?: context.assets.open("care/nsfw.tflite").use { stream ->
            val bytes = stream.readBytes()
            val buffer = ByteBuffer.allocateDirect(bytes.size).order(ByteOrder.nativeOrder()).put(bytes)
            buffer.rewind()
            Interpreter(buffer, Interpreter.Options().setNumThreads(2)).also {
                check(it.getInputTensor(0).shape().contentEquals(intArrayOf(1, 224, 224, 3)))
                check(it.getOutputTensor(0).shape().contentEquals(intArrayOf(1, 2)))
                interpreter = it
            }
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

    fun close() = synchronized(lock) { closed = true; interpreter?.close(); interpreter = null }

    companion object {
        private const val MAX_BYTES = 12 * 1024 * 1024
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
