package dev.rec877.north.ui

import androidx.activity.compose.BackHandler
import androidx.browser.customtabs.CustomTabsIntent
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil3.ImageLoader
import dev.rec877.north.MainViewModel
import dev.rec877.north.SavedAccount
import dev.rec877.north.User
import dev.rec877.north.ui.theme.North

/** モバイルWeb版と同様に、右上のアカウント/「…」から切替メニューを開けるドロワー。 */
@Composable
fun AccountDrawer(vm: MainViewModel, loader: ImageLoader, onDismiss: () -> Unit) {
    val c = North.colors
    val me = vm.ui.me
    val context = LocalContext.current
    val browser = remember { CustomTabsIntent.Builder().build() }
    val accountMenuOpen = remember { mutableStateOf(false) }

    BackHandler {
        if (accountMenuOpen.value) accountMenuOpen.value = false else onDismiss()
    }

    Box(Modifier.fillMaxSize()) {
        Box(Modifier.fillMaxSize().background(Color(0x99000000)).clickable(onClick = onDismiss))
        Column(
            Modifier.fillMaxHeight().width(300.dp).background(c.bg)
                .windowInsetsPadding(WindowInsets.safeDrawing)
                .verticalScroll(rememberScrollState())
        ) {
            if (accountMenuOpen.value) {
                AccountMenuHeader(onBack = { accountMenuOpen.value = false }, onDismiss = onDismiss)
                AccountMenu(
                    accounts = vm.accounts,
                    activeId = vm.activeAccountId(),
                    loader = loader,
                    onSelect = { id -> vm.switchAccount(id); onDismiss() },
                    onSwitchInBrowser = { browser.launchUrl(context, vm.accountSwitchUri()) },
                    onAdd = { browser.launchUrl(context, vm.addAccountAuthorizeUri()) },
                    onLogout = { vm.logout(); onDismiss() },
                )
            } else {
                DrawerHeader(me, loader, onAccounts = { accountMenuOpen.value = true }, onDismiss = onDismiss)
                me?.let { user ->
                    Column(Modifier.fillMaxWidth().clickable { onDismiss(); vm.openUser(user.handle) }.padding(horizontal = 16.dp, vertical = 4.dp)) {
                        Avatar(user, 48.dp, loader, Modifier.size(48.dp))
                        Row(
                            Modifier.padding(top = 8.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Text(user.name, color = c.text, fontSize = 16.sp, fontWeight = FontWeight.Bold)
                            UserBadges(user)
                        }
                        Text("@${user.handle}", color = c.textMuted, fontSize = 14.sp)
                    }
                    Row(Modifier.padding(16.dp), horizontalArrangement = Arrangement.spacedBy(18.dp)) {
                        Text("${user.followingCount} フォロー中", color = c.text, fontSize = 14.sp, fontWeight = FontWeight.Medium, modifier = Modifier.clickable { onDismiss(); vm.open(dev.rec877.north.Dest.follows(user.handle, "following")) })
                        Text("${user.followerCount} フォロワー", color = c.text, fontSize = 14.sp, fontWeight = FontWeight.Medium, modifier = Modifier.clickable { onDismiss(); vm.open(dev.rec877.north.Dest.follows(user.handle, "followers")) })
                    }
                }
                HorizontalDivider(color = c.border)
                DrawerRow(NIcons.Person, "プロフィール") { me?.let { onDismiss(); vm.openUser(it.handle) } }
                DrawerRow(NIcons.Bookmark, "ブックマーク") { onDismiss(); vm.open(dev.rec877.north.Dest.BOOKMARKS) }
                DrawerRow(NIcons.Person, "リスト") { onDismiss(); vm.open(dev.rec877.north.Dest.LISTS) }
                DrawerRow(NIcons.Sparkle, "設定") { onDismiss(); vm.openSettings() }
                HorizontalDivider(color = c.border, modifier = Modifier.padding(top = 8.dp))
                DrawerRow(NIcons.Bell, if (vm.bgNotif) "バックグラウンド通知　オン" else "バックグラウンド通知　オフ") { vm.updateBgNotif(!vm.bgNotif) }
                DrawerRow(NIcons.Close, "ログアウト", danger = true) { onDismiss(); vm.logout() }
            }
        }
    }
}

@Composable
private fun DrawerHeader(me: User?, loader: ImageLoader, onAccounts: () -> Unit, onDismiss: () -> Unit) {
    val c = North.colors
    Row(Modifier.fillMaxWidth().padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
        Text("アカウント情報", color = c.text, fontSize = 20.sp, fontWeight = FontWeight.ExtraBold, modifier = Modifier.weight(1f))
        if (me != null) {
            Box(Modifier.size(28.dp).clip(CircleShape).clickable(onClick = onAccounts)) {
                Avatar(me, 28.dp, loader, Modifier.size(28.dp))
            }
        }
        Box(Modifier.padding(start = 8.dp).size(28.dp).clip(CircleShape).clickable(onClick = onAccounts), contentAlignment = Alignment.Center) {
            Text("…", color = c.text, fontSize = 20.sp, fontWeight = FontWeight.Bold)
        }
        Box(Modifier.padding(start = 6.dp).size(28.dp).clip(CircleShape).clickable(onClick = onDismiss), contentAlignment = Alignment.Center) {
            NorthIcon(NIcons.Close, c.text, 18.dp, modifier = Modifier.size(18.dp))
        }
    }
}

@Composable
private fun AccountMenuHeader(onBack: () -> Unit, onDismiss: () -> Unit) {
    val c = North.colors
    Row(Modifier.fillMaxWidth().padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.size(28.dp).clickable(onClick = onBack), contentAlignment = Alignment.Center) {
            NorthIcon(NIcons.Back, c.text, 20.dp, modifier = Modifier.size(20.dp))
        }
        Text("アカウント", color = c.text, fontSize = 20.sp, fontWeight = FontWeight.ExtraBold, modifier = Modifier.padding(start = 12.dp).weight(1f))
        Box(Modifier.size(28.dp).clickable(onClick = onDismiss), contentAlignment = Alignment.Center) {
            NorthIcon(NIcons.Close, c.text, 18.dp, modifier = Modifier.size(18.dp))
        }
    }
    HorizontalDivider(color = c.border)
}

@Composable
private fun AccountMenu(
    accounts: List<SavedAccount>, activeId: String?, loader: ImageLoader,
    onSelect: (String) -> Unit, onSwitchInBrowser: () -> Unit, onAdd: () -> Unit, onLogout: () -> Unit,
) {
    val c = North.colors
    accounts.forEach { account ->
        val active = account.id == activeId
        Row(Modifier.fillMaxWidth().clickable { if (!active) onSelect(account.id) }.padding(horizontal = 18.dp, vertical = 12.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Avatar(User(id = account.id, handle = account.handle, name = account.name, avatarUrl = account.avatarUrl), 38.dp, loader, Modifier.size(38.dp))
            Column(Modifier.weight(1f)) {
                Text(account.name, color = c.text, fontSize = 14.sp, fontWeight = FontWeight.Bold)
                Text("@${account.handle}", color = c.textMuted, fontSize = 12.sp)
            }
            if (active) Text("✓", color = c.accent, fontSize = 18.sp, fontWeight = FontWeight.Bold)
        }
    }
    HorizontalDivider(color = c.border)
    Text("既存のアカウントを追加", color = c.accent, fontSize = 14.sp, fontWeight = FontWeight.Bold, modifier = Modifier.fillMaxWidth().clickable(onClick = onSwitchInBrowser).padding(18.dp, 14.dp))
    Text("ブラウザでアカウントを切り替えた後、下の「連携を開始」を押してください。", color = c.textMuted, fontSize = 12.sp, modifier = Modifier.padding(horizontal = 18.dp))
    Text("※ 同じアカウントを別の端末で新たに連携すると、northの仕様により以前の端末は再ログインが必要になります。", color = c.textMuted, fontSize = 12.sp, modifier = Modifier.padding(horizontal = 18.dp, vertical = 8.dp))
    Text("連携を開始", color = c.accent, fontSize = 14.sp, fontWeight = FontWeight.Bold, modifier = Modifier.fillMaxWidth().clickable(onClick = onAdd).padding(18.dp, 14.dp))
    HorizontalDivider(color = c.border)
    Text("現在のアカウントからログアウト", color = c.danger, fontSize = 14.sp, fontWeight = FontWeight.Bold, modifier = Modifier.fillMaxWidth().clickable(onClick = onLogout).padding(18.dp, 16.dp))
}

@Composable
private fun DrawerRow(icon: NIcon, label: String, danger: Boolean = false, onClick: () -> Unit) {
    val c = North.colors
    Row(Modifier.fillMaxWidth().clickable(onClick = onClick).padding(horizontal = 18.dp, vertical = 16.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(18.dp)) {
        NorthIcon(icon, if (danger) c.danger else c.text, 22.dp, modifier = Modifier.size(22.dp))
        Text(label, color = if (danger) c.danger else c.text, fontSize = 16.sp, fontWeight = FontWeight.Bold)
    }
}
