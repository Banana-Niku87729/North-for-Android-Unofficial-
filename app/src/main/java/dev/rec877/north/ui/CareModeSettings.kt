package dev.rec877.north.ui

import android.Manifest
import android.content.Intent
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.rec877.north.MainViewModel
import dev.rec877.north.ui.theme.North

@Composable
fun CareModeSettings(vm: MainViewModel, onBack: () -> Unit) {
    val mode = vm.careMode
    val c = North.colors
    val context = LocalContext.current
    var ssid by remember { mutableStateOf("") }
    var notice by remember { mutableStateOf<String?>(null) }
    var licenses by remember { mutableStateOf<String?>(null) }
    val permission = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) {
        mode.restart()
        notice = if (it[Manifest.permission.ACCESS_FINE_LOCATION] == true) "端末の位置情報もオンにしてください。接続名を取得できると下に表示されます。"
            else "Wi-Fi名を取得できない間は、配慮モードが働きます。必要なら端末のアプリ設定から正確な位置情報を許可してください。"
    }
    Column(Modifier.fillMaxSize().background(c.bg)) {
        Row(Modifier.fillMaxWidth().padding(8.dp), verticalAlignment = Alignment.CenterVertically) {
            TextButton(onClick = onBack) { Text("戻る") }
            Text("配慮モード", color = c.text, fontSize = 20.sp, fontWeight = FontWeight.Bold)
        }
        HorizontalDivider(color = c.border)
        LazyColumn(Modifier.weight(1f), contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
            item {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text("配慮モードを使用", color = c.text, fontWeight = FontWeight.Bold)
                        Text("モバイル通信・許可していないWi-Fiで自動的に働きます。", color = c.textMuted, fontSize = 13.sp)
                    }
                    Switch(mode.enabled, mode::updateEnabled)
                }
                Text(when { !mode.enabled -> "現在: オフ"; mode.active -> "現在: 配慮モード動作中"; else -> "現在: 許可したWi-Fiのため解除中" }, color = c.accent)
                Text(when {
                    mode.connection.vpn -> "接続: VPN（許可済みWi-Fiと確定できないため非表示）"
                    mode.connection.mobile -> "接続: モバイル通信"
                    mode.connection.wifi -> "接続: Wi-Fi / ${mode.connection.ssid ?: "名前を取得できません"}"
                    else -> "接続: 未確認・未接続"
                }, color = c.textMuted, fontSize = 13.sp)
            }
            item {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text("静止画を端末内で判定", color = c.text, fontWeight = FontWeight.Bold)
                        Text("オン: NSFWの可能性がある画像を隠します。オフ: すべての画像・動画を隠します。", color = c.textMuted, fontSize = 13.sp)
                    }
                    Switch(mode.localDetection, mode::updateLocalDetection)
                }
                Text("AIへの外部送信はありません。判定用の画像取得には通信量がかかります。動画・GIF・判定失敗は非表示にし、「このメディアを表示」で個別に開けます。", color = c.textMuted, fontSize = 13.sp)
                Text("判定には見逃し・誤判定があります。確実に画面へ出したくない場所では端末内判定をオフにしてください。対象はポスト・DMなどの添付メディアです。", color = c.textMuted, fontSize = 13.sp)
            }
            item {
                HorizontalDivider(color = c.border)
                Text("許可するWi-Fi", color = c.text, fontWeight = FontWeight.Bold, modifier = Modifier.padding(top = 16.dp))
                Text("Wi-Fi名（SSID）の完全一致で判定します。未登録・名前不明・未接続では解除しません。", color = c.textMuted, fontSize = 13.sp)
                TextButton(onClick = { permission.launch(arrayOf(Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION)) }) { Text("Wi-Fi名の取得を許可") }
                Text("AndroidではSSID取得に位置情報の許可と端末の位置情報オンが必要です。位置座標は取得せず、SSIDはこの端末だけに保存します。", color = c.textMuted, fontSize = 12.sp)
                TextButton(onClick = { context.startActivity(Intent(Settings.ACTION_LOCATION_SOURCE_SETTINGS)) }) { Text("端末の位置情報設定を開く") }
                notice?.let { Text(it, color = c.textMuted, fontSize = 13.sp) }
                TextButton(enabled = mode.connection.ssid != null, onClick = { mode.connection.ssid?.let(mode::addTrusted) }) { Text("現在のWi-Fiを追加") }
                OutlinedTextField(ssid, { ssid = it }, label = { Text("Wi-Fi名を直接入力") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                TextButton(enabled = ssid.isNotEmpty(), onClick = { mode.addTrusted(ssid); ssid = "" }) { Text("追加") }
            }
            items(mode.trusted.sorted(), key = { it }) { name ->
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(name, color = c.text, modifier = Modifier.weight(1f))
                    TextButton(onClick = { mode.removeTrusted(name) }) { Text("削除", color = c.danger) }
                }
            }
            item {
                TextButton(onClick = {
                    licenses = listOf("care/NOTICE.txt", "care/LICENSE-model.txt", "care/LICENSE-flutter.txt").joinToString("\n\n") { path ->
                        context.assets.open(path).bufferedReader(Charsets.UTF_8).use { it.readText() }
                    }
                }) { Text("端末内判定モデルのライセンス") }
            }
        }
    }
    licenses?.let { body -> AlertDialog(onDismissRequest = { licenses = null }, title = { Text("ライセンス") },
        text = { Text(body, modifier = Modifier.heightIn(max = 400.dp).verticalScroll(rememberScrollState()), fontSize = 12.sp) },
        confirmButton = { TextButton(onClick = { licenses = null }) { Text("閉じる") } }) }
}
