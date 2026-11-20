package com.example.photos.ui

import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import com.example.photos.data.ThemeStyle
import com.example.photos.ui.theme.LocalPalette
import com.example.photos.ui.theme.paletteOf

/** Circular, softly tinted icon button used in headers (follows the active theme). */
@Composable
fun RoundIconButton(onClick: () -> Unit, modifier: Modifier = Modifier, content: @Composable () -> Unit) {
    val p = LocalPalette.current
    Box(
        modifier.size(42.dp).clip(CircleShape).background(p.card).clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) { content() }
}

/** Pill-shaped filter / option chip. */
@Composable
fun AppChip(selected: Boolean, label: String, onClick: () -> Unit) {
    val p = LocalPalette.current
    val bg by animateColorAsState(if (selected) p.accent else p.card, label = "chipBg")
    val fg by animateColorAsState(if (selected) p.onAccent else p.text, label = "chipFg")
    Text(
        label,
        color = fg,
        style = MaterialTheme.typography.labelLarge,
        maxLines = 1,
        modifier = Modifier
            .clip(RoundedCornerShape(50))
            .background(bg)
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 9.dp),
    )
}

@Composable
fun ThemePickerDialog(current: ThemeStyle, onPick: (ThemeStyle) -> Unit, onDismiss: () -> Unit) {
    val p = LocalPalette.current
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Appearance") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                ThemeStyle.entries.forEach { style ->
                    val pal = paletteOf(style)
                    Row(
                        Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp))
                            .clickable { onPick(style) }.padding(vertical = 8.dp, horizontal = 4.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(14.dp),
                    ) {
                        Box(
                            Modifier.size(40.dp).clip(CircleShape).background(pal.backgroundBrush)
                                .border(3.dp, pal.accent, CircleShape),
                        )
                        Text(style.label, modifier = Modifier.weight(1f), color = p.text)
                        if (style == current) Text("✓", color = p.accent)
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("Done") } },
    )
}

