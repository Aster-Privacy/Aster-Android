//
// Aster Communications Inc.
//
// Copyright (c) 2026 Aster Communications Inc.
//
// This file is part of this project.
//
// This program is free software: you can redistribute it and/or modify
// it under the terms of the GNU Affero General Public License as published by
// the Free Software Foundation, either version 3 of the License, or
// (at your option) any later version.
//
// This program is distributed in the hope that it will be useful,
// but WITHOUT ANY WARRANTY; without even the implied warranty of
// MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
// GNU Affero General Public License for more details.
//
// You should have received a copy of the GNU Affero General Public License
// along with this program. If not, see <https://www.gnu.org/licenses/>.
//

package org.astermail.android.ui.settings.detail

import android.os.Build
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import compose.icons.TablerIcons
import compose.icons.tablericons.Check
import compose.icons.tablericons.Lock
import org.astermail.android.design.AsterMaterial
import org.astermail.android.design.AsterSemanticColors
import org.astermail.android.design.ColorThemePalette
import org.astermail.android.design.SquircleShape
import org.astermail.android.design.dark_semantic_colors
import org.astermail.android.design.light_semantic_colors

@Immutable
internal data class preview_colors(
    val bg: Color,
    val chrome: Color,
    val surface: Color,
    val border: Color,
    val text: Color,
    val text_muted: Color,
    val accent: Color,
    val on_accent: Color,
)

internal fun preview_colors_of(palette: ColorThemePalette): preview_colors = preview_colors(
    bg = palette.bg_primary,
    chrome = palette.bg_secondary,
    surface = palette.bg_tertiary,
    border = palette.border_primary,
    text = palette.text_primary,
    text_muted = palette.text_tertiary,
    accent = palette.accent_color,
    on_accent = readable_on(palette.accent_color),
)

internal fun preview_colors_of(colors: AsterSemanticColors): preview_colors = preview_colors(
    bg = colors.bg_primary,
    chrome = colors.bg_secondary,
    surface = colors.bg_tertiary,
    border = colors.border_primary,
    text = colors.text_primary,
    text_muted = colors.text_tertiary,
    accent = colors.accent_blue,
    on_accent = colors.on_accent,
)

internal val light_preview_colors = preview_colors_of(light_semantic_colors)
internal val dark_preview_colors = preview_colors_of(dark_semantic_colors)

@Composable
internal fun remember_dynamic_preview_colors(): preview_colors? {
    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S) return null
    val context = LocalContext.current
    return remember(context) {
        val scheme = dynamicDarkColorScheme(context)
        preview_colors(
            bg = scheme.surfaceContainer,
            chrome = scheme.surfaceContainerLowest,
            surface = scheme.surfaceContainerHigh,
            border = scheme.outlineVariant,
            text = scheme.onSurface,
            text_muted = scheme.onSurfaceVariant,
            accent = scheme.primary,
            on_accent = scheme.onPrimary,
        )
    }
}

@Composable
internal fun remember_dynamic_light_preview_colors(): preview_colors? {
    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S) return null
    val context = LocalContext.current
    return remember(context) {
        val scheme = dynamicLightColorScheme(context)
        preview_colors(
            bg = scheme.surfaceContainerLowest,
            chrome = scheme.surfaceContainer,
            surface = scheme.surfaceContainerHigh,
            border = scheme.outlineVariant,
            text = scheme.onSurface,
            text_muted = scheme.onSurfaceVariant,
            accent = scheme.primary,
            on_accent = scheme.onPrimary,
        )
    }
}

private fun readable_on(color: Color): Color {
    val luminance = 0.2126f * color.red + 0.7152f * color.green + 0.0722f * color.blue
    return if (luminance > 0.6f) Color(0xFF111827) else Color.White
}

private fun DrawScope.pill(color: Color, x: Float, y: Float, w: Float, h: Float) {
    drawRoundRect(color, Offset(x, y), Size(w, h), CornerRadius(h / 2f, h / 2f))
}

private fun DrawScope.draw_mail_mock(c: preview_colors, rows: Int, row_gap_scale: Float = 1f) {
    val w = size.width
    val h = size.height
    val unit = w / 100f
    drawRect(c.bg)
    val bar_h = 15f * unit
    drawRect(c.chrome, size = Size(w, bar_h))
    drawCircle(c.surface, radius = 3.6f * unit, center = Offset(9f * unit, bar_h / 2f))
    pill(c.text, 17f * unit, bar_h / 2f - 1.8f * unit, 28f * unit, 3.6f * unit)
    pill(c.surface, 62f * unit, bar_h / 2f - 3.4f * unit, 30f * unit, 6.8f * unit)
    drawRect(c.border, topLeft = Offset(0f, bar_h), size = Size(w, maxOf(1f, 0.5f * unit)))

    val row_h = 17f * unit * row_gap_scale
    var y = bar_h + 3f * unit
    for (i in 0 until rows) {
        if (y + row_h > h - 4f * unit) break
        val unread = i == 0 || i == 2
        if (i == 0) {
            drawRoundRect(
                c.surface,
                Offset(2.5f * unit, y - 1.2f * unit),
                Size(w - 5f * unit, row_h - 0.6f * unit),
                CornerRadius(3f * unit, 3f * unit),
            )
        }
        val cy = y + row_h / 2f - 0.6f * unit
        drawCircle(if (unread) c.accent else c.border, radius = 4.6f * unit, center = Offset(10f * unit, cy))
        pill(
            if (unread) c.text else c.text_muted,
            19f * unit,
            cy - 4.2f * unit,
            (if (i % 2 == 0) 38f else 30f) * unit,
            3.2f * unit,
        )
        pill(c.text_muted.copy(alpha = 0.55f), 19f * unit, cy + 1.2f * unit, (if (i % 2 == 0) 56f else 64f) * unit, 2.6f * unit)
        pill(c.text_muted.copy(alpha = 0.7f), 84f * unit, cy - 4f * unit, 10f * unit, 2.6f * unit)
        if (unread) drawCircle(c.accent, radius = 1.5f * unit, center = Offset(92.5f * unit, cy + 2.4f * unit))
        y += row_h
    }

    val fab = 17f * unit
    drawRoundRect(
        c.accent,
        Offset(w - fab - 5f * unit, h - fab - 5f * unit),
        Size(fab, fab),
        CornerRadius(5f * unit, 5f * unit),
    )
    val fab_cx = w - fab / 2f - 5f * unit
    val fab_cy = h - fab / 2f - 5f * unit
    pill(c.on_accent, fab_cx - 3.6f * unit, fab_cy - 0.9f * unit, 7.2f * unit, 1.8f * unit)
    drawRoundRect(
        c.on_accent,
        Offset(fab_cx - 0.9f * unit, fab_cy - 3.6f * unit),
        Size(1.8f * unit, 7.2f * unit),
        CornerRadius(0.9f * unit, 0.9f * unit),
    )
}

@Composable
internal fun mail_mock(
    colors: preview_colors,
    modifier: Modifier = Modifier,
    split_with: preview_colors? = null,
    rows: Int = 6,
    row_gap_scale: Float = 1f,
) {
    Canvas(modifier = modifier) {
        if (split_with == null) {
            draw_mail_mock(colors, rows, row_gap_scale)
        } else {
            clipRect(right = size.width / 2f) { draw_mail_mock(colors, rows, row_gap_scale) }
            clipRect(left = size.width / 2f) { draw_mail_mock(split_with, rows, row_gap_scale) }
        }
    }
}

@Composable
internal fun live_theme_preview(modifier: Modifier = Modifier) {
    val colors = AsterMaterial.colors
    val shape = SquircleShape(20.dp)
    Box(
        modifier = modifier
            .fillMaxWidth()
            .clip(shape)
            .background(colors.bg_secondary)
            .border(1.dp, colors.border_secondary, shape)
            .padding(horizontal = 36.dp, vertical = 16.dp),
        contentAlignment = Alignment.Center,
    ) {
        val phone_shape = SquircleShape(16.dp)
        Box(
            modifier = Modifier
                .fillMaxWidth(0.62f)
                .aspectRatio(1.35f)
                .clip(phone_shape)
                .border(1.dp, colors.border_primary, phone_shape),
        ) {
            mail_mock(preview_colors_of(colors), Modifier.fillMaxSize(), rows = 4)
        }
    }
}

@Composable
internal fun selection_badge(selected: Boolean, locked: Boolean, modifier: Modifier = Modifier) {
    if (!selected && !locked) return
    val colors = AsterMaterial.colors
    Box(
        modifier = modifier
            .size(24.dp)
            .background(if (selected) colors.accent_blue else colors.bg_card, CircleShape)
            .border(2.dp, colors.bg_primary, CircleShape),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            imageVector = if (selected) TablerIcons.Check else TablerIcons.Lock,
            contentDescription = null,
            tint = if (selected) colors.on_accent else colors.text_secondary,
            modifier = Modifier.size(14.dp),
        )
    }
}

@Composable
internal fun theme_preview_card(
    label: String,
    colors: preview_colors,
    selected: Boolean,
    on_click: () -> Unit,
    modifier: Modifier = Modifier,
    split_with: preview_colors? = null,
    locked: Boolean = false,
    aspect: Float = 0.8f,
    corner: Dp = 16.dp,
) {
    val theme = AsterMaterial.colors
    val ring = remember_selected_ring(selected)
    val shape = SquircleShape(corner)
    Column(
        modifier = modifier
            .semantics {
                role = Role.RadioButton
                this.selected = selected
            }
            .clip(SquircleShape(corner + 4.dp))
            .clickable(onClick = on_click)
            .padding(4.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Box {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .aspectRatio(aspect)
                    .border(ring.width, ring.color, SquircleShape(corner + 4.dp))
                    .padding(4.dp)
                    .clip(shape)
                    .border(1.dp, theme.border_secondary, shape),
            ) {
                mail_mock(colors, Modifier.fillMaxSize(), split_with = split_with, rows = 5)
                if (locked) {
                    Box(Modifier.fillMaxSize().background(theme.bg_primary.copy(alpha = 0.35f)))
                }
            }
            selection_badge(
                selected = selected,
                locked = locked,
                modifier = if (selected) {
                    Modifier.align(Alignment.TopEnd).offset(x = 4.dp, y = (-4).dp)
                } else {
                    Modifier.align(Alignment.TopEnd).padding(top = 12.dp, end = 12.dp)
                },
            )
        }
        Spacer(Modifier.height(8.dp))
        Text(
            text = label,
            color = if (selected) theme.text_primary else theme.text_secondary,
            fontSize = 13.sp,
            fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Medium,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth(),
        )
    }
}

@Immutable
internal data class selected_ring(val width: Dp, val color: Color)

@Composable
private fun remember_selected_ring(selected: Boolean): selected_ring {
    val colors = AsterMaterial.colors
    val width by animateDpAsState(if (selected) 2.dp else 1.dp, label = "ring_width")
    val color by animateColorAsState(
        if (selected) colors.accent_blue else Color.Transparent,
        label = "ring_color",
    )
    return selected_ring(width, color)
}

@Composable
internal fun density_preview_card(
    label: String,
    subtitle: String,
    comfortable: Boolean,
    selected: Boolean,
    on_click: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = AsterMaterial.colors
    val ring = remember_selected_ring(selected)
    val shape = SquircleShape(16.dp)
    Column(
        modifier = modifier
            .semantics {
                role = Role.RadioButton
                this.selected = selected
            }
            .clip(shape)
            .background(colors.bg_card)
            .border(if (selected) ring.width else 1.dp, if (selected) ring.color else colors.border_secondary, shape)
            .clickable(onClick = on_click)
            .padding(12.dp),
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(92.dp)
                .clip(SquircleShape(10.dp))
                .border(1.dp, colors.border_secondary, SquircleShape(10.dp)),
        ) {
            density_rows(comfortable, Modifier.fillMaxSize())
        }
        Spacer(Modifier.height(10.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = label,
                color = colors.text_primary,
                fontSize = 14.sp,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier.weight(1f),
            )
            if (selected) {
                Icon(
                    imageVector = TablerIcons.Check,
                    contentDescription = null,
                    tint = colors.accent_blue,
                    modifier = Modifier.size(16.dp),
                )
            }
        }
        Spacer(Modifier.height(2.dp))
        Text(text = subtitle, color = colors.text_tertiary, fontSize = 12.sp, lineHeight = 16.sp, minLines = 2)
    }
}

@Composable
private fun density_rows(comfortable: Boolean, modifier: Modifier) {
    val c = preview_colors_of(AsterMaterial.colors)
    Canvas(modifier = modifier) {
        drawRect(c.bg)
        val unit = size.height / 100f
        val row_h = if (comfortable) 33f * unit else 24f * unit
        val avatar_r = if (comfortable) 8f * unit else 6f * unit
        var y = 4f * unit
        var i = 0
        while (y + row_h <= size.height) {
            val cy = y + row_h / 2f
            val x0 = 10f * unit + avatar_r
            drawCircle(if (i == 0) c.accent else c.border, radius = avatar_r, center = Offset(x0, cy))
            val tx = x0 + avatar_r + 8f * unit
            if (comfortable) {
                pill(c.text, tx, cy - 7f * unit, size.width * 0.36f, 4.6f * unit)
                pill(c.text_muted.copy(alpha = 0.6f), tx, cy + 2.4f * unit, size.width * 0.52f, 3.8f * unit)
            } else {
                pill(c.text, tx, cy - 2.3f * unit, size.width * 0.26f, 4.6f * unit)
                pill(c.text_muted.copy(alpha = 0.6f), tx + size.width * 0.3f, cy - 1.9f * unit, size.width * 0.28f, 3.8f * unit)
            }
            drawRect(c.border.copy(alpha = 0.6f), topLeft = Offset(tx, y + row_h - 0.5f), size = Size(size.width - tx, 1f))
            y += row_h
            i++
        }
    }
}

@Composable
internal fun color_dot(
    color: Color,
    selected: Boolean,
    on_click: () -> Unit,
    modifier: Modifier = Modifier,
    size: Dp = 34.dp,
) {
    val colors = AsterMaterial.colors
    Box(
        modifier = modifier
            .size(size)
            .semantics {
                role = Role.RadioButton
                this.selected = selected
            }
            .clip(CircleShape)
            .border(if (selected) 2.dp else 1.dp, if (selected) colors.text_primary else colors.border_primary, CircleShape)
            .padding(if (selected) 4.dp else 0.dp)
            .clip(CircleShape)
            .background(color)
            .clickable(onClick = on_click),
        contentAlignment = Alignment.Center,
    ) {
        if (selected) {
            Icon(
                imageVector = TablerIcons.Check,
                contentDescription = null,
                tint = readable_on(color),
                modifier = Modifier.size(14.dp),
            )
        }
    }
}
