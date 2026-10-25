package dev.rec877.north

import android.Manifest
import android.annotation.SuppressLint
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.NetworkType
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import kotlinx.coroutines.CancellationException
import java.io.IOException
import java.time.OffsetDateTime
import java.util.concurrent.TimeUnit

private const val PREFS = "north_prefs"
private const val K_LAST = "last_notified_at"

private fun toMs(iso: String): Long =
    runCatching { OffsetDateTime.parse(iso).toInstant().toEpochMilli() }.getOrDefault(0L)

/**
 * スマホへの通知（バックグラウンド）。
 * northのAPIにはプッシュ通知(FCM)の仕組みが無いので、WorkManagerで約15分おきに
 * 通知一覧を確認して、新しい未読があればAndroidの通知として出す。
 */
object BackgroundNotifications {
    private const val WORK = "north_notifications"

    fun enable(ctx: Context) {
        val req = PeriodicWorkRequestBuilder<NotificationWorker>(15, TimeUnit.MINUTES)
            .setConstraints(Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build())
            .build()
        // KEEP: すでに登録済みなら何もしない（アプリを開くたびに呼んでも安全）
        WorkManager.getInstance(ctx)
            .enqueueUniquePeriodicWork(WORK, ExistingPeriodicWorkPolicy.KEEP, req)
    }

    fun disable(ctx: Context) {
        WorkManager.getInstance(ctx).cancelUniqueWork(WORK)
        // 次に有効にしたとき、溜まっていた古い通知が一気に出ないように基準をリセット
        val prefs = ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        prefs.edit().apply {
            prefs.all.keys.filter { it == K_LAST || it.startsWith("$K_LAST:") }.forEach(::remove)
        }.apply()
    }
}

class NotificationWorker(ctx: Context, params: WorkerParameters) : CoroutineWorker(ctx, params) {
    override suspend fun doWork(): Result {
        val app = applicationContext as NorthApp
        if (!app.auth.isLoggedIn()) return Result.success()
        val accountId = app.auth.activeAccountId() ?: return Result.success()
        val prefs = applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val lastKey = "$K_LAST:$accountId"

        return try {
            val items = app.api.notifications(null).items
            // 取得中にアカウントが変わった場合、別アカウントの通知や基準時刻を混ぜない。
            if (app.auth.activeAccountId() != accountId) return Result.success()
            val newest = items.maxOfOrNull { toMs(it.createdAt) } ?: System.currentTimeMillis()
            val last = prefs.getLong(lastKey, -1L)

            // 初回は「ここから先」の基準を決めるだけで、過去の通知は出さない
            if (last >= 0 && !AppForeground.isForeground) {
                val fresh = items
                    .filter { !it.read && toMs(it.createdAt) > last }
                    .sortedBy { toMs(it.createdAt) }
                if (fresh.isNotEmpty()) {
                    // FOLLOWをタップしたときは送信者一人ではなく、自分のフォロワー一覧を開く。
                    val ownHandle = if (fresh.any { it.kind == "FOLLOW" }) runCatching { app.api.me().handle }.getOrNull() else null
                    NotificationHelper.show(applicationContext, fresh, ownHandle)
                }
            }
            prefs.edit().putLong(lastKey, if (last < 0) newest else maxOf(last, newest)).apply()
            Result.success()
        } catch (e: CancellationException) {
            throw e
        } catch (e: IOException) {
            Result.retry()
        } catch (e: Exception) {
            // 未ログイン・権限不足など。アプリを開いたときに画面側で案内するので、ここでは何もしない
            Result.success()
        }
    }
}

object NotificationHelper {
    const val CHANNEL = "north_notifications"
    private const val SUMMARY_ID = 1

    fun createChannel(ctx: Context) {
        if (Build.VERSION.SDK_INT < 26) return
        val ch = NotificationChannel(CHANNEL, "通知", NotificationManager.IMPORTANCE_DEFAULT)
            .apply { description = "northの新着通知" }
        ctx.getSystemService(NotificationManager::class.java).createNotificationChannel(ch)
    }

    @SuppressLint("MissingPermission") // 権限は下で確認している
    fun show(ctx: Context, items: List<Notification>, ownHandle: String? = null) {
        if (Build.VERSION.SDK_INT >= 33 &&
            ContextCompat.checkSelfPermission(ctx, Manifest.permission.POST_NOTIFICATIONS)
            != PackageManager.PERMISSION_GRANTED
        ) return
        val nm = NotificationManagerCompat.from(ctx)
        if (!nm.areNotificationsEnabled()) return

        if (items.size > 3) {
            nm.notify(SUMMARY_ID, build(ctx, "north", "新着の通知が${items.size}件あります", null, SUMMARY_ID))
        } else {
            items.forEach { n ->
                val dest = if (n.kind == "FOLLOW" && !ownHandle.isNullOrBlank()) Dest.follows(ownHandle, "followers")
                    else n.tweet?.id?.let { Dest.post(it) }
                    ?: n.actors.firstOrNull()?.handle?.takeIf { it.isNotBlank() }?.let { Dest.user(it) }
                val code = n.id.hashCode()
                nm.notify(
                    code,
                    build(ctx, notificationSummary(n), n.tweet?.text?.takeIf { it.isNotBlank() }, dest, code),
                )
            }
        }
    }

    private fun build(
        ctx: Context,
        title: String,
        text: String?,
        dest: String?,
        requestCode: Int,
    ): android.app.Notification {
        val intent = Intent(ctx, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
            if (dest != null) putExtra(Dest.EXTRA, dest)
        }
        val pi = PendingIntent.getActivity(
            ctx, requestCode, intent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        return NotificationCompat.Builder(ctx, CHANNEL)
            .setSmallIcon(R.drawable.ic_stat_north)
            .setContentTitle(title)
            .apply {
                if (text != null) {
                    setContentText(text)
                    setStyle(NotificationCompat.BigTextStyle().bigText(text))
                }
            }
            .setContentIntent(pi)
            .setAutoCancel(true)
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .build()
    }
}
