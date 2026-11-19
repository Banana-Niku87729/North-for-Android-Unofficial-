package dev.rec877.north.ui

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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil3.ImageLoader
import dev.rec877.north.MainViewModel
import dev.rec877.north.User
import dev.rec877.north.ui.theme.North
import java.util.Locale

private fun group(n: Int) = "%,d".format(Locale.US, n)

/**
 * 統計（サイトの「統計」）: アカウント数・ポスト数・累計ポスト数ランキング。
 * 開くたびに取り直す。ランキングの行をタップするとそのプロフィールを開く。
 */
@Composable
fun StatisticsScreen(vm: MainViewModel, loader: ImageLoader) {
    val c = North.colors
    LaunchedEffect(Unit) { vm.loadStats() }
    val s = vm.stats

    Column(Modifier.fillMaxSize().background(c.bg)) {
        Row(
            Modifier.fillMaxWidth().padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Box(Modifier.size(28.dp).clickable { vm.back() }, contentAlignment = Alignment.Center) {
                NorthIcon(NIcons.Back, c.text, 22.dp, modifier = Modifier.size(22.dp))
            }
            Text("統計", color = c.text, fontSize = 20.sp, fontWeight = FontWeight.ExtraBold, modifier = Modifier.weight(1f))
            TextButton(onClick = vm::loadStats, enabled = !vm.statsLoading) { Text("更新", color = c.accent, fontSize = 14.sp) }
        }
        HorizontalDivider(color = c.border)

        if (s == null) {
            Box(Modifier.fillMaxSize().padding(32.dp), contentAlignment = Alignment.Center) {
                if (vm.statsLoading) {
                    CircularProgressIndicator(color = c.accent, strokeWidth = 2.dp, modifier = Modifier.size(24.dp))
                } else {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(vm.statsError ?: "統計を読み込めませんでした", color = c.textMuted, fontSize = 14.sp, textAlign = TextAlign.Center)
                        TextButton(onClick = vm::loadStats) { Text("再読み込み", color = c.accent) }
                    }
                }
            }
        } else LazyColumn(Modifier.fillMaxSize()) {
            item(key = "accounts") { StatRow("アカウント数", "${group(s.accounts)}人") }
            item(key = "posts") { StatRow("ポスト数", "${group(s.posts)}件") }
            item(key = "ranking_head") {
                Column(Modifier.fillMaxWidth().padding(start = 16.dp, end = 16.dp, top = 20.dp, bottom = 12.dp)) {
                    Text("ポスト数ランキング", color = c.text, fontSize = 20.sp, fontWeight = FontWeight.ExtraBold)
                    Text("公開アカウントの累計ポスト数（リプライを含む）", color = c.textMuted, fontSize = 13.sp, modifier = Modifier.padding(top = 6.dp))
                }
                HorizontalDivider(color = c.border)
            }
            itemsIndexed(s.ranking, key = { _, r -> "rank:${r.id.ifEmpty { r.handle }}" }) { index, r ->
                Row(
                    Modifier.fillMaxWidth().clickable { vm.openUser(r.handle) }.padding(horizontal = 16.dp, vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    Avatar(User(id = r.id, handle = r.handle, name = r.name, avatarUrl = r.avatarUrl), 40.dp, loader)
                    Column(Modifier.weight(1f)) {
                        Text("${index + 1}位", color = c.textMuted, fontSize = 13.sp)
                        Text(r.name, color = c.text, fontSize = 16.sp, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        Text("@${r.handle}・${group(r.tweetCount)}件のポスト", color = c.textMuted, fontSize = 13.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }
                }
                HorizontalDivider(color = c.border)
            }
        }
    }
}

@Composable
private fun StatRow(label: String, value: String) {
    val c = North.colors
    Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp)) {
        Text(label, color = c.textMuted, fontSize = 14.sp)
        Text(value, color = c.text, fontSize = 17.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(top = 2.dp))
    }
    HorizontalDivider(color = c.border)
}
