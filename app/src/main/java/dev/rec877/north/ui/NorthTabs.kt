package dev.rec877.north.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.rec877.north.ui.theme.North

/** サイトの .tabs: 選択中はアクセント色の4dp下線 */
@Composable
fun NorthTabs(labels: List<String>, selected: Int, onSelect: (Int) -> Unit) {
    val c = North.colors
    Column {
        Row(Modifier.fillMaxWidth()) {
            labels.forEachIndexed { i, label ->
                val on = i == selected
                Column(
                    Modifier.weight(1f).clickable { onSelect(i) },
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Text(
                        label,
                        color = if (on) c.text else c.textMuted,
                        fontSize = 15.sp,
                        fontWeight = FontWeight.Medium,
                        modifier = Modifier.padding(top = 16.dp, bottom = 11.dp),
                    )
                    Box(
                        Modifier
                            .width(56.dp)
                            .height(4.dp)
                            .clip(RoundedCornerShape(4.dp))
                            .background(if (on) c.accent else Color.Transparent)
                    )
                }
            }
        }
        HorizontalDivider(color = c.border)
    }
}