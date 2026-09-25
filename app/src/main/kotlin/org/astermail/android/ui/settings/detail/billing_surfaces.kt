/*
 * Aster Mail Android
 * Copyright (C) 2026 Aster Privacy
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Affero General Public License for more details.
 *
 * You should have received a copy of the GNU Affero General Public License
 * along with this program.  If not, see <https://www.gnu.org/licenses/>.
 */

package org.astermail.android.ui.settings.detail

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import compose.icons.TablerIcons
import compose.icons.tablericons.AlertCircle
import compose.icons.tablericons.AlertTriangle
import compose.icons.tablericons.Check
import compose.icons.tablericons.ChevronRight
import compose.icons.tablericons.CircleCheck
import org.astermail.android.design.AsterMaterial
import org.astermail.android.design.AsterSpacing
import org.astermail.android.design.SquircleShape
import org.astermail.android.design.acrylic

internal val billing_tile_shape: Shape = SquircleShape(14.dp)
internal val billing_control_shape: Shape = SquircleShape(12.dp)

@Composable
internal fun Modifier.billing_surface(shape: Shape, tint: Color? = null): Modifier {
    val colors = AsterMaterial.colors
    return this.acrylic(colors, shape, tint ?: colors.bg_secondary)
}

@Composable
internal fun billing_pill(
    text: String,
    modifier: Modifier = Modifier,
    foreground: Color? = null,
    background: Color? = null,
    icon: ImageVector? = null,
) {
    val colors = AsterMaterial.colors
    val fg = foreground ?: colors.accent_blue
    val bg = background ?: fg.copy(alpha = 0.14f)
    Row(
        modifier = modifier
            .clip(CircleShape)
            .background(bg)
            .padding(horizontal = 10.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        if (icon != null) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = fg,
                modifier = Modifier.size(12.dp),
            )
        }
        Text(
            text = text,
            color = fg,
            fontSize = 11.sp,
            fontWeight = FontWeight.SemiBold,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

internal enum class billing_meter_status { none, ok, near, full }

@Composable
internal fun billing_status_mark(status: billing_meter_status, text: String) {
    val colors = AsterMaterial.colors
    val tint = when (status) {
        billing_meter_status.ok -> colors.success
        billing_meter_status.near -> colors.warning
        billing_meter_status.full -> colors.danger
        billing_meter_status.none -> colors.text_tertiary
    }
    val icon = when (status) {
        billing_meter_status.ok -> TablerIcons.CircleCheck
        billing_meter_status.near -> TablerIcons.AlertTriangle
        billing_meter_status.full -> TablerIcons.AlertCircle
        billing_meter_status.none -> null
    }
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
        if (icon != null) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = tint,
                modifier = Modifier.size(15.dp),
            )
        }
        Text(
            text = text,
            color = tint,
            fontSize = 12.sp,
            fontWeight = FontWeight.SemiBold,
            maxLines = 1,
        )
    }
}

@Composable
internal fun billing_meter(
    label: String,
    value_text: String,
    fraction: Float,
    status: billing_meter_status,
    status_text: String?,
    modifier: Modifier = Modifier,
    trailing_action: (@Composable () -> Unit)? = null,
) {
    val colors = AsterMaterial.colors
    val is_over = status == billing_meter_status.full
    Column(modifier = modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = label,
                color = colors.text_primary,
                fontSize = 14.sp,
                fontWeight = FontWeight.Medium,
            )
            if (status_text != null) {
                billing_status_mark(status = status, text = status_text)
            }
        }
        Spacer(Modifier.height(AsterSpacing.sm))
        solid_progress_bar(fraction = fraction, is_over = is_over, height = 6.dp)
        Spacer(Modifier.height(6.dp))
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = value_text,
                color = colors.text_tertiary,
                fontSize = 13.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f),
            )
            if (trailing_action != null) {
                Spacer(Modifier.width(AsterSpacing.sm))
                trailing_action()
            }
        }
    }
}

internal data class billing_amount_tile(
    val id: String,
    val title: String,
    val subtitle: String? = null,
    val subtitle_color: Color? = null,
)

@Composable
@OptIn(ExperimentalLayoutApi::class)
internal fun billing_amount_tiles(
    items: List<billing_amount_tile>,
    selected_id: String?,
    enabled: Boolean,
    on_select: (String) -> Unit,
    modifier: Modifier = Modifier,
    max_per_row: Int = 3,
) {
    val colors = AsterMaterial.colors
    FlowRow(
        modifier = modifier.fillMaxWidth().selectableGroup(),
        horizontalArrangement = Arrangement.spacedBy(AsterSpacing.sm),
        verticalArrangement = Arrangement.spacedBy(AsterSpacing.sm),
        maxItemsInEachRow = max_per_row,
    ) {
        items.forEach { item ->
            val selected = item.id == selected_id
            Column(
                modifier = Modifier
                    .weight(1f)
                    .clip(billing_control_shape)
                    .acrylic(colors, billing_control_shape, if (selected) colors.bg_selected else colors.bg_card)
                    .border(
                        width = if (selected) 1.5.dp else 1.dp,
                        color = if (selected) colors.accent_blue else colors.border_primary,
                        shape = billing_control_shape,
                    )
                    .selectable(selected = selected, enabled = enabled, role = Role.RadioButton) { on_select(item.id) }
                    .padding(horizontal = AsterSpacing.sm, vertical = AsterSpacing.md),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Text(
                    text = item.title,
                    color = when {
                        !enabled -> colors.text_tertiary
                        selected -> colors.accent_blue
                        else -> colors.text_primary
                    },
                    fontSize = 16.sp,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                if (item.subtitle != null) {
                    Text(
                        text = item.subtitle,
                        color = item.subtitle_color ?: colors.text_tertiary,
                        fontSize = 12.sp,
                        fontWeight = if (item.subtitle_color != null) FontWeight.SemiBold else FontWeight.Normal,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.padding(top = 2.dp),
                    )
                }
            }
        }
    }
}

@Composable
internal fun billing_select_mark(selected: Boolean, enabled: Boolean) {
    val colors = AsterMaterial.colors
    val accent = if (enabled) colors.accent_blue else colors.accent_blue.copy(alpha = 0.4f)
    val ring = if (enabled) colors.text_muted else colors.text_muted.copy(alpha = 0.4f)
    Box(
        modifier = Modifier
            .size(22.dp)
            .clip(CircleShape)
            .then(if (selected) Modifier.background(accent) else Modifier.border(1.5.dp, ring, CircleShape)),
        contentAlignment = Alignment.Center,
    ) {
        if (selected) {
            Icon(
                imageVector = TablerIcons.Check,
                contentDescription = null,
                tint = colors.on_accent,
                modifier = Modifier.size(14.dp),
            )
        }
    }
}

@Composable
internal fun billing_divider(modifier: Modifier = Modifier, inset: Dp = 0.dp) {
    val colors = AsterMaterial.colors
    Box(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = inset)
            .height(1.dp)
            .background(colors.border_primary),
    )
}

@Composable
private fun billing_option_indicator(selected: Boolean, enabled: Boolean) {
    val colors = AsterMaterial.colors
    val accent = if (enabled) colors.accent_blue else colors.accent_blue.copy(alpha = 0.4f)
    val ring = if (enabled) colors.text_tertiary else colors.text_tertiary.copy(alpha = 0.4f)
    Box(
        modifier = Modifier
            .size(22.dp)
            .clip(CircleShape)
            .border(2.dp, if (selected) accent else ring, CircleShape),
        contentAlignment = Alignment.Center,
    ) {
        if (selected) {
            Box(modifier = Modifier.size(11.dp).background(accent, CircleShape))
        }
    }
}

@Composable
internal fun billing_option_row(
    title: String,
    selected: Boolean,
    on_click: () -> Unit,
    modifier: Modifier = Modifier,
    subtitle: String? = null,
    enabled: Boolean = true,
    title_note: String? = null,
    title_note_color: Color? = null,
    leading: (@Composable () -> Unit)? = null,
    trailing: (@Composable () -> Unit)? = null,
    below: (@Composable () -> Unit)? = null,
) {
    val colors = AsterMaterial.colors
    val fill = when {
        !selected -> Color.Transparent
        !colors.is_glass -> colors.bg_selected
        else -> colors.bg_selected.copy(alpha = 0.34f)
    }
    Column(
        modifier = modifier
            .fillMaxWidth()
            .clip(RectangleShape)
            .selectable(selected = selected, enabled = enabled, role = Role.RadioButton, onClick = on_click)
            .background(fill)
            .heightIn(min = 54.dp)
            .padding(horizontal = AsterSpacing.lg, vertical = AsterSpacing.sm),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            billing_option_indicator(selected = selected, enabled = enabled)
            Spacer(Modifier.width(AsterSpacing.md))
            if (leading != null) {
                leading()
                Spacer(Modifier.width(AsterSpacing.md))
            }
            Column(modifier = Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = title,
                        color = if (enabled) colors.text_primary else colors.text_tertiary,
                        fontSize = 15.sp,
                        fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f, fill = false),
                    )
                    if (title_note != null) {
                        Spacer(Modifier.width(AsterSpacing.sm))
                        Text(
                            text = title_note,
                            color = title_note_color ?: colors.text_tertiary,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.SemiBold,
                            maxLines = 1,
                        )
                    }
                }
                if (subtitle != null) {
                    Text(
                        text = subtitle,
                        color = colors.text_tertiary,
                        fontSize = 13.sp,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.padding(top = 2.dp),
                    )
                }
            }
            if (trailing != null) {
                Spacer(Modifier.width(AsterSpacing.md))
                trailing()
            }
        }
        if (below != null) {
            Spacer(Modifier.height(AsterSpacing.xs))
            Box(modifier = Modifier.padding(start = 34.dp)) { below() }
        }
    }
}

@Composable
internal fun billing_price_column(
    amount: String,
    unit: String,
    note: String? = null,
    enabled: Boolean = true,
    note_color: androidx.compose.ui.graphics.Color? = null,
) {
    val colors = AsterMaterial.colors
    Column(horizontalAlignment = Alignment.End) {
        Row(verticalAlignment = Alignment.Bottom) {
            Text(
                text = amount,
                color = if (enabled) colors.text_primary else colors.text_tertiary,
                fontSize = 15.sp,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
            )
            Text(
                text = unit,
                color = colors.text_tertiary,
                fontSize = 12.sp,
                modifier = Modifier.padding(bottom = 1.dp),
            )
        }
        if (note != null) {
            Text(
                text = note,
                color = note_color ?: colors.text_tertiary,
                fontSize = 11.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

@Composable
internal fun billing_chip(text: String, tint: Color = AsterMaterial.colors.accent_blue, solid: Boolean = false) {
    val colors = AsterMaterial.colors
    Box(
        modifier = Modifier
            .clip(CircleShape)
            .then(
                if (solid) Modifier.background(tint) else Modifier.acrylic(colors, CircleShape, tint.copy(alpha = 0.14f)),
            )
            .padding(horizontal = 9.dp, vertical = 3.dp),
    ) {
        Text(
            text = text,
            color = if (solid) colors.on_accent else tint,
            fontSize = 11.sp,
            fontWeight = FontWeight.SemiBold,
            maxLines = 1,
        )
    }
}

@Composable
internal fun billing_link_row(text: String, on_click: () -> Unit, modifier: Modifier = Modifier) {
    val colors = AsterMaterial.colors
    Row(
        modifier = modifier
            .fillMaxWidth()
            .heightIn(min = 44.dp)
            .clip(billing_control_shape)
            .clickable(role = Role.Button, onClick = on_click)
            .padding(horizontal = AsterSpacing.sm),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = text,
            color = colors.accent_blue,
            fontSize = 14.sp,
            fontWeight = FontWeight.SemiBold,
        )
        Spacer(Modifier.width(4.dp))
        Icon(
            imageVector = TablerIcons.ChevronRight,
            contentDescription = null,
            tint = colors.accent_blue,
            modifier = Modifier.size(16.dp),
        )
    }
}

@Composable
internal fun billing_action_text(label: String, on_click: () -> Unit, color: Color? = null) {
    val colors = AsterMaterial.colors
    Text(
        text = label,
        color = color ?: colors.accent_blue,
        fontSize = 13.sp,
        fontWeight = FontWeight.SemiBold,
        maxLines = 1,
        modifier = Modifier
            .clip(SquircleShape(6.dp))
            .clickable(role = Role.Button, onClick = on_click)
            .padding(horizontal = 4.dp, vertical = 2.dp),
    )
}

internal fun format_storage_short(bytes: Long): String = format_bytes(bytes).replace(".0 ", " ")
