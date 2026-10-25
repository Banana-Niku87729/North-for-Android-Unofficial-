package dev.rec877.north.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.rec877.north.MainViewModel
import android.net.Uri
import androidx.browser.customtabs.CustomTabsIntent
import androidx.compose.ui.platform.LocalContext
import dev.rec877.north.ui.theme.North
import dev.rec877.north.ui.theme.ThemePresets

private data class SettingItem(val title: String, val description: String)

@Composable
fun SettingsScreen(vm: MainViewModel) {
    val context = LocalContext.current
    fun openNorthSettings(path: String) {
        CustomTabsIntent.Builder().build().launchUrl(context, Uri.parse("https://north.rip$path"))
    }
    val c = North.colors
    var page by remember { mutableStateOf<String?>(null) }
    BackHandler(enabled = page != null) {
        page = when (page) {
            "customize" -> "accessibility"
            "display", "accessibility" -> "appearance"
            else -> null
        }
    }
    when (page) {
        "appearance" -> {
            AppearanceHub(onBack = { page = null }, open = { destination -> page = destination })
            return
        }
        "display" -> { DisplaySettings(vm) { page = "appearance" }; return }
        "accessibility" -> { AccessibilitySettings(vm, onBack = { page = "appearance" }, onCustomize = { page = "customize" }); return }
        "customize" -> { AppCustomizationSettings(vm) { page = "accessibility" }; return }
        null -> Unit
        else -> { SettingInfoPage(vm, page!!, { page = null }, ::openNorthSettings) ; return }
    }
    val sections = listOf(
        SettingItem("アカウント", "アカウント情報とログインを管理します"),
        SettingItem("ポストの設定", "ポストの送信に関する設定を管理します"),
        SettingItem("セキュリティとアカウントアクセス", "セキュリティとログイン履歴を管理します"),
        SettingItem("プライバシーと安全", "表示する情報と共有範囲を管理します"),
        SettingItem("通知", "受け取る通知の種類を選びます"),
        SettingItem("アクセシビリティ、表示、言語", "north の表示方法を管理します"),
    )
    Column(Modifier.fillMaxSize().background(c.bg)) {
        Row(Modifier.fillMaxWidth().padding(16.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(16.dp)) {
            Box(Modifier.size(28.dp).clickable { vm.back() }, contentAlignment = Alignment.Center) { NorthIcon(NIcons.Back, c.text, 22.dp, modifier = Modifier.size(22.dp)) }
            Text("設定", color = c.text, fontSize = 20.sp, fontWeight = FontWeight.ExtraBold)
        }
        HorizontalDivider(color = c.border)
        LazyColumn(Modifier.fillMaxSize()) {
            items(sections, key = SettingItem::title) { item ->
                Row(Modifier.fillMaxWidth().clickable { page = if (item.title == "アクセシビリティ、表示、言語") "appearance" else item.title }.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) { Text(item.title, color = c.text, fontSize = 16.sp, fontWeight = FontWeight.Bold); Text(item.description, color = c.textMuted, fontSize = 13.sp, modifier = Modifier.padding(top = 3.dp)) }
                    Text("›", color = c.textMuted, fontSize = 24.sp)
                }
                HorizontalDivider(color = c.border)
            }
            item("notifications") {
                Row(Modifier.fillMaxWidth().padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) { Text("バックグラウンド通知", color = c.text, fontSize = 16.sp, fontWeight = FontWeight.Bold); Text("アプリを閉じている間も通知を確認します", color = c.textMuted, fontSize = 13.sp) }
                    Switch(checked = vm.bgNotif, onCheckedChange = vm::updateBgNotif)
                }
            }
            item("update") {
                val update = vm.availableUpdate
                Row(Modifier.fillMaxWidth().clickable { vm.openUpdates() }.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text("アプリのアップデート", color = c.text, fontSize = 16.sp, fontWeight = FontWeight.Bold)
                        Text(update?.let { "${it.version} が利用できます" } ?: "更新内容と過去のバージョンを確認する", color = c.textMuted, fontSize = 13.sp, maxLines = 2)
                    }
                    Text("›", color = c.textMuted, fontSize = 24.sp)
                }
            }
        }
    }
}

@Composable private fun AppearanceHub(onBack: () -> Unit, open: (String) -> Unit) {
    val c = North.colors
    Column(Modifier.fillMaxSize().background(c.bg)) {
        SettingsHeader("アクセシビリティ、表示、言語", onBack)
        SettingLink("アクセシビリティ", "色のコントラストや動きの量を調整します") { open("accessibility") }
        SettingLink("表示", "フォントサイズ、色、背景を管理します") { open("display") }
    }
}

@Composable private fun AccessibilitySettings(vm: MainViewModel, onBack: () -> Unit, onCustomize: () -> Unit) {
    val c = North.colors
    Column(Modifier.fillMaxSize().background(c.bg)) {
        SettingsHeader("アクセシビリティ", onBack)
        SettingSwitch("動きを減らす", "画面のアニメーションを最小限にします", vm.reduceMotion, vm::updateReduceMotion)
        SettingSwitch("コントラストを上げる", "文字や境界を見分けやすくします", vm.highContrast, vm::updateHighContrast)
        SettingLink("アプリのカスタマイズ", "端末に合わせて投稿ボタンの位置を調整します") { onCustomize() }
    }
}

@Composable private fun AppCustomizationSettings(vm: MainViewModel, onBack: () -> Unit) {
    val c = North.colors
    Column(Modifier.fillMaxSize().background(c.bg)) {
        SettingsHeader("アプリのカスタマイズ", onBack)
        Text("投稿ボタン", color = c.text, fontSize = 17.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(start = 16.dp, top = 20.dp, bottom = 6.dp))
        Text("ナビゲーションや端末固有の操作領域と重なる場合に、位置を変更できます。", color = c.textMuted, fontSize = 13.sp, modifier = Modifier.padding(horizontal = 16.dp))
        Box(Modifier.padding(16.dp).fillMaxWidth().clip(CircleShape).background(c.accent).clickable { vm.setComposeButtonEditing(true); vm.popToRoot() }.padding(vertical = 14.dp), contentAlignment = Alignment.Center) {
            Text("画面上で位置を調整する", color = c.textOnAccent, fontWeight = FontWeight.Bold)
        }
    }
}

@Composable private fun SettingInfoPage(vm: MainViewModel, title: String, onBack: () -> Unit, openWeb: (String) -> Unit) {
    val c = North.colors
    Column(Modifier.fillMaxSize().background(c.bg)) {
        SettingsHeader(title, onBack)
        when (title) {
            "アカウント" -> {
                vm.ui.me?.let { user ->
                    Text("@${user.handle}", color = c.text, fontSize = 18.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(16.dp))
                    Text("メールアドレス・パスワード・アーカイブの管理は north のアカウント画面で行えます。", color = c.textMuted, fontSize = 14.sp, modifier = Modifier.padding(horizontal = 16.dp))
                }
                SettingLink("アカウント情報", "ユーザー名やメールアドレスを確認します") { openWeb("/settings/your_account_information") }
                SettingLink("パスワードを変更する", "Web版の安全な変更画面を開きます") { openWeb("/settings/password") }
                SettingLink("データのアーカイブをダウンロード", "northデータのアーカイブをリクエストします") { openWeb("/settings/download_your_data") }
                SettingToggle(vm, "private_posts", "非公開ポスト", "フォロワーだけにポストを表示します（サーバー対応時に同期されます）")
            }
            "ポストの設定" -> {
                SettingToggle(vm, "undo_original", "元のポストを取り消す", "公開前に取り消しできます")
                SettingToggle(vm, "undo_reply", "返信を取り消す", "公開前に取り消しできます")
                Text("取り消し時間", color = c.text, fontWeight = FontWeight.Bold, modifier = Modifier.padding(16.dp))
                Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) { listOf(5, 10, 20, 30, 60).forEach { seconds -> SettingChoice("${seconds}秒", vm.settingInt("undo_seconds", 10) == seconds) { vm.updateSettingInt("undo_seconds", seconds) } } }
            }
            "セキュリティとアカウントアクセス" -> {
                SettingLink("2要素認証", "Web版で認証方法を設定します") { openWeb("/settings/security") }
                SettingLink("アプリとセッション", "ログイン中の端末とセッションを管理します") { openWeb("/settings/sessions") }
            }
            "プライバシーと安全" -> {
                SettingToggle(vm, "sensitive_media", "センシティブなメディアを表示する", "可能性のあるメディアも表示します")
                SettingToggle(vm, "allow_messages", "すべてのアカウントからメッセージを受信する", "フォロー外のメッセージはリクエストに表示されます")
                SettingLink("Web版のプライバシー設定", "オーディエンスや安全設定をまとめて管理します") { openWeb("/settings/privacy_and_safety") }
                KeywordSetting(vm)
            }
            "通知" -> {
                SettingToggle(vm, "filter_non_following", "フォローしていないアカウントをフィルター", "通知に表示しません")
                SettingToggle(vm, "filter_not_followed", "フォローされていないアカウントをフィルター", "通知に表示しません")
                SettingToggle(vm, "filter_new", "新しいアカウントをフィルター", "通知に表示しません")
                SettingToggle(vm, "filter_no_avatar", "プロフィール画像がないアカウントをフィルター", "通知に表示しません")
                SettingToggle(vm, "push", "プッシュ通知", "ブラウザ・アプリを閉じていても通知を受け取ります", default = true)
                SettingLink("Web版の詳細フィルター", "northの通知フィルターを開きます") { openWeb("/settings/notifications/advanced_filters") }
            }
        }
    }
}

@Composable private fun SettingChoice(label: String, selected: Boolean, choose: () -> Unit) { val c = North.colors; Box(Modifier.clip(CircleShape).background(if (selected) c.accent else c.elev2).clickable(onClick = choose).padding(horizontal = 10.dp, vertical = 8.dp), contentAlignment = Alignment.Center) { Text(label, color = if (selected) c.textOnAccent else c.text, fontSize = 12.sp) } }
@Composable private fun SettingToggle(vm: MainViewModel, key: String, title: String, description: String, default: Boolean = false) { var checked by remember { mutableStateOf(vm.setting(key, default)) }; SettingSwitch(title, description, checked) { checked = it; vm.updateSetting(key, it) } }
@Composable private fun KeywordSetting(vm: MainViewModel) { val c = North.colors; var keywords by remember { mutableStateOf(vm.settingText("muted_keywords")) }; Column(Modifier.padding(16.dp)) { Text("ミュートするキーワード", color = c.text, fontWeight = FontWeight.Bold); androidx.compose.foundation.text.BasicTextField(value = keywords, onValueChange = { keywords = it; vm.updateSettingText("muted_keywords", it) }, textStyle = androidx.compose.ui.text.TextStyle(color = c.text, fontSize = 15.sp), modifier = Modifier.fillMaxWidth().padding(top = 10.dp).background(c.elev2).padding(12.dp), decorationBox = { inner -> if (keywords.isEmpty()) Text("単語をカンマ区切りで入力", color = c.textMuted); inner() }) } }

@Composable private fun SettingsHeader(title: String, onBack: () -> Unit) {
    val c = North.colors
    Row(Modifier.fillMaxWidth().padding(16.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(16.dp)) { Box(Modifier.size(28.dp).clickable(onClick = onBack), contentAlignment = Alignment.Center) { NorthIcon(NIcons.Back, c.text, 22.dp, modifier = Modifier.size(22.dp)) }; Text(title, color = c.text, fontSize = 20.sp, fontWeight = FontWeight.ExtraBold) }
    HorizontalDivider(color = c.border)
}
@Composable private fun SettingLink(title: String, description: String, open: () -> Unit) { val c = North.colors; Row(Modifier.fillMaxWidth().clickable(onClick = open).padding(16.dp), verticalAlignment = Alignment.CenterVertically) { Column(Modifier.weight(1f)) { Text(title, color = c.text, fontWeight = FontWeight.Bold); Text(description, color = c.textMuted, fontSize = 13.sp) }; Text("›", color = c.textMuted, fontSize = 24.sp) }; HorizontalDivider(color = c.border) }
@Composable private fun SettingSwitch(title: String, description: String, checked: Boolean, change: (Boolean) -> Unit) { val c = North.colors; Row(Modifier.fillMaxWidth().padding(16.dp), verticalAlignment = Alignment.CenterVertically) { Column(Modifier.weight(1f)) { Text(title, color = c.text, fontWeight = FontWeight.Bold); Text(description, color = c.textMuted, fontSize = 13.sp) }; Switch(checked = checked, onCheckedChange = change) }; HorizontalDivider(color = c.border) }

@Composable
private fun DisplaySettings(vm: MainViewModel, onBack: () -> Unit) {
    val c = North.colors
    val accents = listOf(
        "白" to 0xFFFFFFFFL, "青" to 0xFF1D9BF0L, "黄" to 0xFFFFD400L, "桃" to 0xFFF91880L, "紫" to 0xFF7856FFL, "橙" to 0xFFFF7A00L, "緑" to 0xFF00BA7CL, "赤" to 0xFFF4212EL, "シアン" to 0xFF00B8D4L, "ライム" to 0xFF76C043L,
        "バイオレット" to 0xFFB14AEDL, "アイス" to 0xFFA8D8FFL, "インディゴ" to 0xFF4F46E5L, "ティール" to 0xFF0F8B8DL, "ローズ" to 0xFFD45079L, "アンバー" to 0xFFFFB000L, "ミント" to 0xFF4ECDC4L, "コッパー" to 0xFFC76D3AL, "ネイビー" to 0xFF1E3A8AL, "スカイ" to 0xFF38BDF8L,
        "コーラル" to 0xFFFF6B6BL, "マゼンタ" to 0xFFD946EFL, "ラベンダー" to 0xFFA78BFAL, "ゴールド" to 0xFFEAB308L, "エメラルド" to 0xFF059669L, "シルバー" to 0xFF94A3B8L, "スカーレット" to 0xFFE23B3BL, "サーモン" to 0xFFFA8072L, "ピーチ" to 0xFFFFAB76L, "アプリコット" to 0xFFF4A261L,
    )
    Column(Modifier.fillMaxSize().background(c.bg)) {
        Row(Modifier.fillMaxWidth().padding(16.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(16.dp)) {
            Box(Modifier.size(28.dp).clickable(onClick = onBack), contentAlignment = Alignment.Center) { NorthIcon(NIcons.Back, c.text, 22.dp, modifier = Modifier.size(22.dp)) }
            Column { Text("表示", color = c.text, fontSize = 20.sp, fontWeight = FontWeight.ExtraBold); Text("テーマ、色、背景", color = c.textMuted, fontSize = 14.sp) }
        }
        HorizontalDivider(color = c.border)
        Text("フォントサイズ", color = c.text, fontSize = 17.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(16.dp))
        Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            listOf("小" to .85f, "標準" to 1f, "大" to 1.15f, "特大" to 1.3f).forEach { (label, scale) ->
                Box(Modifier.weight(1f).clip(CircleShape).background(if (vm.fontScale == scale) c.accent else c.elev2).clickable { vm.updateFontScale(scale) }.padding(vertical = 10.dp), contentAlignment = Alignment.Center) { Text(label, color = if (vm.fontScale == scale) c.textOnAccent else c.text, fontWeight = FontWeight.Bold) }
            }
        }
        Text("色", color = c.text, fontSize = 17.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(16.dp))
        Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            accents.forEach { (name, argb) -> Column(horizontalAlignment = Alignment.CenterHorizontally) { Box(Modifier.size(38.dp).clip(CircleShape).background(Color(argb)).clickable { vm.updateAccent(argb) }); Text(name, color = c.textMuted, fontSize = 11.sp, modifier = Modifier.padding(top = 5.dp)) } }
        }
        Text("背景テーマ", color = c.text, fontSize = 17.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(16.dp))
        LazyColumn(Modifier.weight(1f)) {
            items(ThemePresets, key = { it.id }) { theme ->
                Row(Modifier.fillMaxWidth().clickable { vm.updateTheme(theme.id) }.padding(horizontal = 16.dp, vertical = 10.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    Box(Modifier.size(30.dp).clip(CircleShape).background(Color(theme.color)))
                    Text(theme.label, color = c.text, fontSize = 15.sp, modifier = Modifier.weight(1f))
                    if (vm.selectedTheme == theme.id) Text("✓", color = c.accent, fontWeight = FontWeight.Bold)
                }
            }
        }
    }
}
