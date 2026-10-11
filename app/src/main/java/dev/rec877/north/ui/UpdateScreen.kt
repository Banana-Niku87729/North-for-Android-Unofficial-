package dev.rec877.north.ui

import androidx.browser.customtabs.CustomTabsIntent
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.rec877.north.AppUpdate
import dev.rec877.north.BuildConfig
import dev.rec877.north.MainViewModel
import dev.rec877.north.ui.theme.North
import android.net.Uri

/** GitHub Releasesをアプリ内で見やすく表示し、更新内容を確認してから取得する画面。 */
@Composable
fun UpdateScreen(vm: MainViewModel) {
    val c = North.colors
    var selected by remember { mutableStateOf<AppUpdate?>(null) }
    LaunchedEffect(Unit) { vm.checkForUpdates() }

    if (selected != null) {
        UpdateDetail(selected!!, canDownload = vm.canInstallUpdate(selected!!), onBack = { selected = null }, onDownload = vm::downloadUpdate)
        return
    }
    Column(Modifier.fillMaxSize().background(c.bg)) {
        Row(Modifier.fillMaxWidth().padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(32.dp).clickable { vm.back() }, contentAlignment = Alignment.Center) {
                NorthIcon(NIcons.Back, c.text, 24.dp, modifier = Modifier.size(24.dp))
            }
            Spacer(Modifier.width(12.dp))
            Text("アップデート", color = c.text, fontSize = 24.sp, fontWeight = FontWeight.ExtraBold)
        }
        HorizontalDivider(color = c.border)
        when {
            vm.updatesLoading && vm.releaseUpdates.isEmpty() -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator(color = c.accent)
            }
            vm.releaseUpdates.isEmpty() -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text("更新情報を取得できませんでした。画面を開き直して再試行してください。", color = c.textMuted)
            }
            else -> LazyColumn(Modifier.fillMaxSize().padding(horizontal = 16.dp)) {
                item { Text("現在のバージョン  ${BuildConfig.VERSION_NAME}", color = c.textMuted, fontSize = 14.sp, modifier = Modifier.padding(top = 20.dp, bottom = 12.dp)) }
                item { Text("最新アップデート", color = c.text, fontSize = 18.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(bottom = 10.dp)) }
                item { UpdateCard(vm.releaseUpdates.first(), installed = !vm.canInstallUpdate(vm.releaseUpdates.first()), onClick = { selected = vm.releaseUpdates.first() }) }
                if (vm.releaseUpdates.size > 1) {
                    item { Text("過去のアップデート", color = c.text, fontSize = 18.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(top = 28.dp, bottom = 10.dp)) }
                    items(vm.releaseUpdates.drop(1), key = { "${it.version}-${it.tag}" }) { update ->
                        UpdateCard(update, installed = !vm.canInstallUpdate(update), onClick = { selected = update })
                    }
                }
                item { Spacer(Modifier.padding(bottom = 24.dp)) }
            }
        }
    }
}

@Composable
private fun UpdateCard(update: AppUpdate, installed: Boolean, onClick: () -> Unit) {
    val c = North.colors
    val emergency = update.emergency
    Column(
        Modifier.fillMaxWidth().padding(bottom = 14.dp).clip(RoundedCornerShape(16.dp))
            .border(2.dp, c.border, RoundedCornerShape(16.dp)).clickable(onClick = onClick).padding(18.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(update.version, color = c.text, fontSize = 30.sp, fontWeight = FontWeight.ExtraBold)
            Spacer(Modifier.width(10.dp))
            Box(Modifier.clip(CircleShape).background(if (emergency) c.danger else c.repost).padding(horizontal = 11.dp, vertical = 5.dp)) {
                Text(if (emergency) "緊急" else "通常", color = c.textOnAccent, fontSize = 13.sp, fontWeight = FontWeight.Bold)
            }
            Spacer(Modifier.weight(1f))
            Text(if (installed) "インストール済み" else "詳細 ›", color = if (installed) c.textMuted else c.accent, fontWeight = FontWeight.Bold)
        }
        val summary = update.notes.lineSequence().firstOrNull { it.trim().isNotEmpty() }?.trim()?.removePrefix("#")
        Text(summary ?: "更新内容を確認してダウンロード", color = c.textMuted, fontSize = 14.sp, maxLines = 2, modifier = Modifier.padding(top = 10.dp))
    }
}

@Composable
private fun UpdateDetail(update: AppUpdate, canDownload: Boolean, onBack: () -> Unit, onDownload: (AppUpdate) -> Unit) {
    val c = North.colors
    val context = LocalContext.current
    Column(Modifier.fillMaxSize().background(c.bg)) {
        Row(Modifier.fillMaxWidth().padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(32.dp).clickable(onClick = onBack), contentAlignment = Alignment.Center) { NorthIcon(NIcons.Back, c.text, 24.dp, modifier = Modifier.size(24.dp)) }
            Spacer(Modifier.width(12.dp))
            Text("アップデート", color = c.text, fontSize = 24.sp, fontWeight = FontWeight.ExtraBold)
        }
        HorizontalDivider(color = c.border)
        Column(Modifier.fillMaxSize().padding(24.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            Text(update.version, color = c.text, fontSize = 48.sp, fontWeight = FontWeight.ExtraBold)
            Text(if (update.emergency) "緊急アップデート" else "通常アップデート", color = if (update.emergency) c.danger else c.repost, fontWeight = FontWeight.Bold, modifier = Modifier.padding(top = 8.dp))
            Text("更新内容", color = c.text, fontSize = 20.sp, fontWeight = FontWeight.Bold, modifier = Modifier.fillMaxWidth().padding(top = 36.dp, bottom = 12.dp))
            Box(Modifier.fillMaxWidth().weight(1f).clip(RoundedCornerShape(16.dp)).border(2.dp, c.border, RoundedCornerShape(16.dp)).padding(18.dp)) {
                Text(update.notes.ifBlank { "更新内容はGitHubのリリースページで確認できます。" }, color = c.text, fontSize = 16.sp, lineHeight = 24.sp)
            }
            Row(Modifier.fillMaxWidth().padding(top = 16.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Box(Modifier.weight(1f).clip(CircleShape).border(1.dp, c.border, CircleShape).clickable { if (update.releaseUrl.isNotBlank()) CustomTabsIntent.Builder().build().launchUrl(context, Uri.parse(update.releaseUrl)) }.padding(vertical = 14.dp), contentAlignment = Alignment.Center) {
                    Text("GitHubで見る", color = c.text, fontWeight = FontWeight.Bold)
                }
                Box(Modifier.weight(1.2f).clip(CircleShape).background(c.accent.copy(alpha = if (canDownload) 1f else .45f)).clickable(enabled = canDownload) { onDownload(update) }.padding(vertical = 14.dp), contentAlignment = Alignment.Center) {
                    Text(if (canDownload) "ダウンロード" else "インストール済み", color = c.textOnAccent, fontWeight = FontWeight.ExtraBold)
                }
            }
        }
    }
}
