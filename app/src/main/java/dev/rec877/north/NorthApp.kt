package dev.rec877.north

import android.app.Application
import okhttp3.OkHttpClient

/** 画面遷移の宛先（通知タップなどから渡す文字列） */
object Dest {
    const val EXTRA = "north_open"
    fun post(id: String) = "post:$id"
    fun user(handle: String) = "user:$handle"
    fun follows(handle: String, kind: String) = "follows:$handle:$kind"
    fun postLikes(id: String) = "likes:$id"
    fun postRetweets(id: String) = "retweets:$id"
    fun postQuotes(id: String) = "quotes:$id"
    const val BOOKMARKS = "bookmarks"
    const val LISTS = "lists"
    const val SETTINGS = "settings"
    const val UPDATES = "updates"
}

/** アプリが前面にいるか。前面のときは、バックグラウンド通知を出さない（アプリ内のバッジで足りるため） */
object AppForeground {
    @Volatile var isForeground: Boolean = false
}

/**
 * 認証やAPIクライアントをアプリ全体で1つにする。
 * 更新トークンのローテーションがあるので、AuthRepository（とその排他）は
 * 画面側とバックグラウンドのWorkerで必ず同じインスタンスを使う。
 */
class NorthApp : Application() {
    val http: OkHttpClient by lazy { OkHttpClient() }
    val secrets: SecretStore by lazy { SecretStore(this) }
    val auth: AuthRepository by lazy { AuthRepository(secrets, http) }
    val api: NorthApi by lazy { NorthApi(auth, http) }

    override fun onCreate() {
        super.onCreate()
        NotificationHelper.createChannel(this)
    }
}
