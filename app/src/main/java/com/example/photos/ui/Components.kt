package com.example.photos.ui

import androidx.compose.animation.animateColorAsState
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.graphics.drawscope.rotate
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


// ---------------------------------------------------------------------------------------------
// Shared building blocks (consistent spacing, menus, empty states)
// ---------------------------------------------------------------------------------------------

/** Standard screen header: optional back button, one-line title that shrinks for long names, subtitle, actions. */
@Composable
fun ScreenHeader(
    title: String,
    subtitle: String?,
    onBack: (() -> Unit)?,
    actions: @Composable androidx.compose.foundation.layout.RowScope.() -> Unit,
) {
    val base = MaterialTheme.typography.headlineLarge
    val size = if (title.length > 14) base.fontSize * 0.78f else base.fontSize
    Row(
        Modifier.fillMaxWidth().padding(start = if (onBack == null) 20.dp else 16.dp, end = 16.dp, top = 16.dp, bottom = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        if (onBack != null) {
            RoundIconButton(onClick = onBack) {
                androidx.compose.material3.Icon(androidx.compose.material.icons.Icons.AutoMirrored.Filled.ArrowBack, "Back")
            }
        }
        Column(Modifier.weight(1f)) {
            Text(
                title,
                style = base.copy(fontSize = size, lineHeight = size * 1.15f),
                maxLines = 1,
                overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
            )
            if (subtitle != null) {
                Text(
                    subtitle,
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 2.dp),
                )
            }
        }
        actions()
    }
}

/** Rounded, softly bordered popup menu used everywhere instead of the plain default. */
@Composable
fun ThemedMenu(
    expanded: Boolean,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
    content: @Composable androidx.compose.foundation.layout.ColumnScope.() -> Unit,
) {
    val p = LocalPalette.current
    androidx.compose.material3.DropdownMenu(
        expanded = expanded,
        onDismissRequest = onDismiss,
        modifier = modifier,
        shape = RoundedCornerShape(24.dp),
        containerColor = p.cardHigh,
        tonalElevation = 0.dp,
        shadowElevation = 14.dp,
        border = androidx.compose.foundation.BorderStroke(1.dp, p.text.copy(alpha = 0.08f)),
        content = content,
    )
}

@Composable
fun MenuRow(
    text: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector? = null,
    onClick: () -> Unit,
) {
    val p = LocalPalette.current
    androidx.compose.material3.DropdownMenuItem(
        text = { Text(text, style = MaterialTheme.typography.bodyLarge, color = p.text) },
        leadingIcon = if (icon != null) {
            { androidx.compose.material3.Icon(icon, null, tint = p.muted, modifier = Modifier.size(20.dp)) }
        } else null,
        onClick = onClick,
        contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 18.dp, vertical = 6.dp),
    )
}

/** A small stacked-photos illustration with a title, tinted by the current theme. */
@Composable
fun EmptyState(title: String, subtitle: String? = null, modifier: Modifier = Modifier) {
    val p = LocalPalette.current
    Column(modifier.fillMaxWidth().padding(32.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        androidx.compose.foundation.Canvas(Modifier.size(width = 150.dp, height = 116.dp)) {
            val w = size.width
            val h = size.height
            val corner = androidx.compose.ui.geometry.CornerRadius(22.dp.toPx())
            val cardSize = androidx.compose.ui.geometry.Size(w * 0.62f, h * 0.78f)
            val topLeft = androidx.compose.ui.geometry.Offset((w - cardSize.width) / 2f, (h - cardSize.height) / 2f)
            rotate(-10f, center) {
                drawRoundRect(p.cardHigh, topLeft, cardSize, corner)
            }
            rotate(9f, center) {
                drawRoundRect(p.accent.copy(alpha = 0.35f), topLeft, cardSize, corner)
            }
            drawRoundRect(p.card, topLeft, cardSize, corner)
            drawRoundRect(
                p.text.copy(alpha = 0.12f), topLeft, cardSize, corner,
                style = androidx.compose.ui.graphics.drawscope.Stroke(1.5.dp.toPx()),
            )
            // sun + hills
            drawCircle(p.accent, radius = cardSize.width * 0.09f, center = androidx.compose.ui.geometry.Offset(topLeft.x + cardSize.width * 0.7f, topLeft.y + cardSize.height * 0.3f))
            val hills = androidx.compose.ui.graphics.Path().apply {
                moveTo(topLeft.x, topLeft.y + cardSize.height)
                lineTo(topLeft.x, topLeft.y + cardSize.height * 0.78f)
                lineTo(topLeft.x + cardSize.width * 0.34f, topLeft.y + cardSize.height * 0.52f)
                lineTo(topLeft.x + cardSize.width * 0.6f, topLeft.y + cardSize.height * 0.72f)
                lineTo(topLeft.x + cardSize.width * 0.78f, topLeft.y + cardSize.height * 0.6f)
                lineTo(topLeft.x + cardSize.width, topLeft.y + cardSize.height * 0.8f)
                lineTo(topLeft.x + cardSize.width, topLeft.y + cardSize.height)
                close()
            }
            clipRect(topLeft.x, topLeft.y, topLeft.x + cardSize.width, topLeft.y + cardSize.height) {
                drawPath(hills, p.accent.copy(alpha = 0.55f))
            }
        }
        Text(title, modifier = Modifier.padding(top = 14.dp), style = MaterialTheme.typography.titleMedium)
        if (subtitle != null) {
            Text(subtitle, color = p.muted, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.padding(top = 4.dp))
        }
    }
}
