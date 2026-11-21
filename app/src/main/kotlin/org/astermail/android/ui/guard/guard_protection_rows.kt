//
// Aster Communications Inc.
//
// Copyright (c) 2026 Aster Communications Inc.
//
// This file is part of this project.
//
// This program is free software: you can redistribute it and/or modify
// it under the terms of the AGPLv3 as published by
// the Free Software Foundation, either version 3 of the License, or
// (at your option) any later version.
//
// This program is distributed in the hope that it will be useful,
// but WITHOUT ANY WARRANTY; without even the implied warranty of
// MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
// AGPLv3 for more details.
//
// You should have received a copy of the AGPLv3
// along with this program. If not, see <https://www.gnu.org/licenses/>.
//

package org.astermail.android.ui.guard

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.selection.toggleable
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.material3.ripple
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import compose.icons.TablerIcons
import compose.icons.tablericons.Download
import compose.icons.tablericons.HandStop
import compose.icons.tablericons.Key
import compose.icons.tablericons.Lifebuoy
import compose.icons.tablericons.Login
import compose.icons.tablericons.MailForward
import compose.icons.tablericons.Plug
import org.astermail.android.R
import org.astermail.android.api.guard.GuardHoldScope
import org.astermail.android.api.guard.GuardSwitch
import org.astermail.android.design.AsterMaterial
import org.astermail.android.design.AsterSemanticColors
import org.astermail.android.design.AsterSpacing
import org.astermail.android.design.aster_haptic
import org.astermail.android.design.aster_perform_haptic
import org.astermail.android.design.components.AsterSwitch
import org.astermail.android.ui.settings.detail.settings_row_gap
import org.astermail.android.ui.settings.detail.settings_toggle_row
import org.astermail.android.ui.settings.settings_search_anchor

private val guard_row_icon_size = 22.dp
private val guard_scope_icon_size = 18.dp
internal val guard_text_column: Dp = AsterSpacing.lg + guard_row_icon_size + AsterSpacing.md
internal val guard_connector_line_x: Dp = AsterSpacing.lg + guard_row_icon_size / 2
internal val guard_connector_end_x: Dp = guard_text_column - 4.dp
internal val guard_connector_radius: Dp = 6.dp
internal val guard_connector_width: Dp = 2.5.dp
internal const val GUARD_CONNECTOR_ALPHA = 0.6f
private val guard_connector_gap = 4.dp

internal val GUARD_SWITCH_ORDER: List<Int> = listOf(
    GuardSwitch.NEW_DEVICE_APPROVAL,
    GuardSwitch.HOLD_CHANGES,
    GuardSwitch.APPROVE_MAIL_APPS,
    GuardSwitch.RECOVERY_WAIT,
)

internal val GUARD_SCOPE_ORDER: List<Int> = listOf(
    GuardHoldScope.FORWARDING_RULES,
    GuardHoldScope.RECOVERY_PASSWORD,
    GuardHoldScope.SECURITY_EXPORT,
)

internal fun guard_switch_icon(bit: Int): ImageVector = when (bit) {
    GuardSwitch.NEW_DEVICE_APPROVAL -> TablerIcons.Login
    GuardSwitch.HOLD_CHANGES -> TablerIcons.HandStop
    GuardSwitch.APPROVE_MAIL_APPS -> TablerIcons.Plug
    else -> TablerIcons.Lifebuoy
}

internal fun guard_switch_title(bit: Int): Int = when (bit) {
    GuardSwitch.NEW_DEVICE_APPROVAL -> R.string.guard_switch_new_device
    GuardSwitch.HOLD_CHANGES -> R.string.guard_switch_hold
    GuardSwitch.APPROVE_MAIL_APPS -> R.string.guard_switch_mail_apps
    else -> R.string.guard_switch_recovery_wait
}

internal fun guard_switch_body(bit: Int): Int = when (bit) {
    GuardSwitch.NEW_DEVICE_APPROVAL -> R.string.guard_switch_new_device_body
    GuardSwitch.HOLD_CHANGES -> R.string.guard_switch_hold_body
    GuardSwitch.APPROVE_MAIL_APPS -> R.string.guard_switch_mail_apps_body
    else -> R.string.guard_switch_recovery_wait_body
}

internal fun guard_scope_icon(bit: Int): ImageVector = when (bit) {
    GuardHoldScope.FORWARDING_RULES -> TablerIcons.MailForward
    GuardHoldScope.RECOVERY_PASSWORD -> TablerIcons.Key
    else -> TablerIcons.Download
}

internal fun guard_scope_title(bit: Int): Int = when (bit) {
    GuardHoldScope.FORWARDING_RULES -> R.string.guard_scope_forwarding
    GuardHoldScope.RECOVERY_PASSWORD -> R.string.guard_scope_recovery
    else -> R.string.guard_scope_export
}

internal fun guard_scope_body(bit: Int): Int = when (bit) {
    GuardHoldScope.FORWARDING_RULES -> R.string.guard_scope_forwarding_body
    GuardHoldScope.RECOVERY_PASSWORD -> R.string.guard_scope_recovery_body
    else -> R.string.guard_scope_export_body
}

internal data class guard_connector_shape(
    val line_x: Float,
    val top: Float,
    val elbow_y: Float,
    val end_x: Float,
    val bottom: Float?,
    val radius: Float,
    val direction: Float,
)

internal fun guard_connector_geometry(
    width: Float,
    height: Float,
    line_x: Float,
    end_x: Float,
    radius: Float,
    top: Float,
    last: Boolean,
    rtl: Boolean,
): guard_connector_shape {
    val start = if (rtl) width - line_x else line_x
    val end = if (rtl) width - end_x else end_x
    val elbow_y = height / 2f
    val bend = radius.coerceAtMost(kotlin.math.abs(end - start)).coerceAtMost(elbow_y - top).coerceAtLeast(0f)
    return guard_connector_shape(
        line_x = start,
        top = top,
        elbow_y = elbow_y,
        end_x = end,
        bottom = if (last) null else height,
        radius = bend,
        direction = if (rtl) -1f else 1f,
    )
}

internal fun guard_connector_color(colors: AsterSemanticColors): Color =
    colors.text_tertiary.copy(alpha = GUARD_CONNECTOR_ALPHA)

internal fun guard_connector_path(shape: guard_connector_shape): Path = Path().apply {
    moveTo(shape.line_x, shape.top)
    lineTo(shape.line_x, shape.elbow_y - shape.radius)
    quadraticTo(shape.line_x, shape.elbow_y, shape.line_x + shape.direction * shape.radius, shape.elbow_y)
    lineTo(shape.end_x, shape.elbow_y)
    if (shape.bottom != null) {
        moveTo(shape.line_x, shape.elbow_y - shape.radius)
        lineTo(shape.line_x, shape.bottom)
    }
}

@Composable
internal fun guard_scope_row(
    icon: ImageVector,
    title: String,
    body: String?,
    checked: Boolean,
    enabled: Boolean,
    on_change: () -> Unit,
    connector_top: Float = 0f,
    last: Boolean = false,
) {
    val colors = AsterMaterial.colors
    val view = LocalView.current
    val density = LocalDensity.current
    val tone by animateFloatAsState(
        targetValue = if (enabled) 1f else 0f,
        animationSpec = tween(180),
        label = "guard_scope_row",
    )
    val title_color = lerp(colors.text_tertiary, colors.text_primary, tone)
    val icon_color = lerp(colors.text_tertiary.copy(alpha = 0.6f), colors.text_secondary, tone)
    val line_color = guard_connector_color(colors)
    Row(
        modifier = Modifier
            .settings_search_anchor(title, body)
            .fillMaxWidth()
            .drawBehind {
                val shape = guard_connector_geometry(
                    width = size.width,
                    height = size.height,
                    line_x = guard_connector_line_x.toPx(),
                    end_x = guard_connector_end_x.toPx(),
                    radius = guard_connector_radius.toPx(),
                    top = connector_top,
                    last = last,
                    rtl = layoutDirection == LayoutDirection.Rtl,
                )
                drawPath(
                    path = guard_connector_path(shape),
                    color = line_color,
                    style = Stroke(
                        width = with(density) { guard_connector_width.toPx() },
                        cap = StrokeCap.Butt,
                        join = StrokeJoin.Round,
                    ),
                )
            }
            .toggleable(
                value = checked,
                enabled = enabled,
                role = Role.Switch,
                indication = ripple(),
                interactionSource = remember { MutableInteractionSource() },
                onValueChange = { value ->
                    view.aster_perform_haptic(if (value) aster_haptic.toggle_on else aster_haptic.toggle_off)
                    on_change()
                },
            )
            .heightIn(min = 52.dp)
            .padding(start = guard_text_column, end = AsterSpacing.lg, top = 6.dp, bottom = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = icon_color,
            modifier = Modifier.size(guard_scope_icon_size),
        )
        Spacer(Modifier.width(AsterSpacing.md))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                color = title_color,
                fontSize = 14.sp,
                lineHeight = 19.sp,
                fontWeight = FontWeight.Medium,
            )
            if (body != null) {
                Text(
                    text = body,
                    color = colors.text_tertiary,
                    fontSize = 12.sp,
                    lineHeight = 16.sp,
                    modifier = Modifier.padding(top = 1.dp),
                )
            }
        }
        Spacer(Modifier.width(AsterSpacing.md))
        AsterSwitch(checked = checked, onCheckedChange = null, enabled = enabled)
    }
}

internal fun guard_connector_top(parent_height: Float, icon_size: Float, gap: Float): Float =
    if (parent_height <= 0f) 0f else -(parent_height / 2f - icon_size / 2f - gap).coerceAtLeast(0f)

@Composable
internal fun guard_protections_list(
    switches: Int,
    scopes: Int,
    on_toggle: (Int) -> Unit,
    on_toggle_scope: (Int) -> Unit,
    switch_enabled: (Int) -> Boolean = { true },
    scope_enabled: (Int) -> Boolean = { true },
    switch_subtitle: @Composable (Int) -> String = { stringResource(guard_switch_body(it)) },
    scope_subtitle: @Composable (Int) -> String = { stringResource(guard_scope_body(it)) },
) {
    val density = LocalDensity.current
    var parent_height by remember { mutableIntStateOf(0) }
    Column(modifier = Modifier.fillMaxWidth().testTag("guard_protections")) {
        GUARD_SWITCH_ORDER.forEachIndexed { index, bit ->
            if (index > 0) settings_row_gap()
            val is_parent = bit == GuardSwitch.HOLD_CHANGES
            settings_toggle_row(
                title = stringResource(guard_switch_title(bit)),
                subtitle = switch_subtitle(bit),
                checked = switches and bit != 0,
                enabled = switch_enabled(bit),
                icon = guard_switch_icon(bit),
                modifier = if (is_parent) Modifier.onSizeChanged { parent_height = it.height } else Modifier,
                on_change = { on_toggle(bit) },
            )
            if (is_parent) {
                val top = with(density) {
                    guard_connector_top(parent_height.toFloat(), guard_row_icon_size.toPx(), guard_connector_gap.toPx())
                }
                GUARD_SCOPE_ORDER.forEachIndexed { scope_index, scope ->
                    guard_scope_row(
                        icon = guard_scope_icon(scope),
                        title = stringResource(guard_scope_title(scope)),
                        body = scope_subtitle(scope),
                        checked = scopes and scope != 0,
                        enabled = scope_enabled(scope),
                        on_change = { on_toggle_scope(scope) },
                        connector_top = if (scope_index == 0) top else 0f,
                        last = scope_index == GUARD_SCOPE_ORDER.lastIndex,
                    )
                }
            }
        }
    }
}
