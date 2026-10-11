package dev.rec877.north

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.runtime.remember
import coil3.ImageLoader
import coil3.network.okhttp.OkHttpNetworkFetcherFactory
import dev.rec877.north.ui.AppShell
import dev.rec877.north.ui.LoginScreen
import dev.rec877.north.ui.theme.NorthTheme

class MainActivity : ComponentActivity() {
    private val vm: MainViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // アプリは常にダークなので、ステータス/ナビゲーションバーのアイコンは明るい色に固定
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.dark(android.graphics.Color.TRANSPARENT),
            navigationBarStyle = SystemBarStyle.dark(android.graphics.Color.TRANSPARENT),
        )
        // 画面回転などの再生成では、同じ宛先を二重処理しない
        if (savedInstanceState == null) {
            handleRedirect(intent)
            handleOpen(intent)
        }

        setContent {
            NorthTheme(colors = vm.themeColors, fontScale = vm.fontScale) {
                // Bearer付きのOkHttpで画像を取得するローダー
                val loader = remember {
                    ImageLoader.Builder(applicationContext)
                        .components { add(OkHttpNetworkFetcherFactory(callFactory = { vm.imageClient })) }
                        .build()
                }
                if (vm.ui.loggedIn) AppShell(vm, loader) else LoginScreen(vm)
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        handleRedirect(intent)
        handleOpen(intent)
    }

    // 前面にいる間は、バックグラウンド通知を出さない（アプリ内のバッジで足りるため）
    override fun onStart() {
        super.onStart()
        AppForeground.isForeground = true
    }

    override fun onStop() {
        super.onStop()
        AppForeground.isForeground = false
    }

    override fun onResume() {
        super.onResume()
        // 完了通知をタップして戻ったら、保存済みAPKをインストーラーへ渡す。
        // 未許可の場合はここから「このアプリからのインストール」設定を開く。
        UpdateInstaller.openPendingInstaller(this)
    }

    private fun handleRedirect(i: Intent?) {
        val uri = i?.data ?: return
        if (uri.scheme == Config.APP_SCHEME) vm.onRedirect(uri)
    }

    /** スマホの通知をタップして開かれたとき: 該当のポスト/プロフィールを開く */
    private fun handleOpen(i: Intent?) {
        val dest = i?.getStringExtra(Dest.EXTRA) ?: return
        i.removeExtra(Dest.EXTRA)
        if (vm.ui.loggedIn) vm.open(dest)
    }
}
