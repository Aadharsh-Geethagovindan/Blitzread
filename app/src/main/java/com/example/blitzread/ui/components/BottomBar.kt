package com.example.blitzread.ui.components

import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.graphics.Color

data class BottomBarItem(
    val icon: @Composable () -> Unit,
    val onClick: () -> Unit
)

@Composable
fun BottomBar(
    items: List<BottomBarItem>,
    modifier: Modifier = Modifier,
    background: Color = Color(0xFF016347) // green bar
) {
    Surface(color = background, modifier = modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier
                .height(64.dp)
                .padding(horizontal = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // simple evenly spaced layout
            val count = items.size.coerceAtLeast(1)
            items.forEach { item ->
                IconButton(
                    onClick = item.onClick,
                    modifier = Modifier.weight(1f)
                ) {
                    item.icon()
                }
            }
            // if items < 3/4, still fills via weight
            repeat((count - items.size).coerceAtLeast(0)) {}
        }
    }
}
