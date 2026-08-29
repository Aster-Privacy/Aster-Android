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

package org.astermail.android.ui.common

import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
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
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntRect
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Popup
import androidx.compose.ui.window.PopupPositionProvider
import androidx.compose.ui.window.PopupProperties
import kotlinx.coroutines.delay
import org.astermail.android.R
import org.astermail.android.design.AsterDuration
import org.astermail.android.design.AsterEasing
import org.astermail.android.design.AsterMaterial
import org.astermail.android.ui.theme.local_accessibility

private const val tooltip_visible_millis = 1600L

private class above_anchor_position_provider(
    private val gap_px: Int,
) : PopupPositionProvider {
    override fun calculatePosition(
        anchorBounds: IntRect,
        windowSize: IntSize,
        layoutDirection: LayoutDirection,
        popupContentSize: IntSize,
    ): IntOffset {
        val x = anchorBounds.left + (anchorBounds.width - popupContentSize.width) / 2
        val clamped_x = x.coerceIn(0, (windowSize.width - popupContentSize.width).coerceAtLeast(0))
        val above_y = anchorBounds.top - popupContentSize.height - gap_px
        val below_y = anchorBounds.bottom + gap_px
        val space_above = anchorBounds.top - gap_px
        val space_below = windowSize.height - below_y
        val fits_above = space_above >= popupContentSize.height
        val fits_below = space_below >= popupContentSize.height
        val y = when {
            fits_above -> above_y
            fits_below -> below_y
            space_above >= space_below -> above_y
            else -> below_y
        }
        val clamped_y = y.coerceIn(0, (windowSize.height - popupContentSize.height).coerceAtLeast(0))
        return IntOffset(clamped_x, clamped_y)
    }
}

@Composable
fun icon_tooltip_host(
    text: String,
    visible: Boolean,
    on_dismiss: () -> Unit,
    gap: Dp = 14.dp,
    content: @Composable () -> Unit,
) {
    val colors = AsterMaterial.colors
    val gap_px = with(LocalDensity.current) { gap.roundToPx() }
    Box {
        content()
        if (visible) {
            LaunchedEffect(text) {
                delay(tooltip_visible_millis)
                on_dismiss()
            }
            Popup(
                popupPositionProvider = remember(gap_px) { above_anchor_position_provider(gap_px) },
                onDismissRequest = on_dismiss,
                properties = PopupProperties(focusable = false, clippingEnabled = false),
            ) {
                AnimatedVisibility(
                    visible = true,
                    enter = fadeIn(
                        tween(
                            durationMillis = AsterDuration.menu_fade_enter,
                            easing = AsterEasing.menu_enter,
                        ),
                    ),
                    exit = fadeOut(
                        tween(
                            durationMillis = AsterDuration.menu_fade_exit,
                            easing = AsterEasing.menu_exit,
                        ),
                    ),
                ) {
                    Text(
                        text = text,
                        color = colors.bg_primary,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Medium,
                        maxLines = 1,
                        modifier = Modifier
                            .shadow(6.dp, RoundedCornerShape(8.dp))
                            .clip(RoundedCornerShape(8.dp))
                            .background(colors.text_primary)
                            .padding(horizontal = 10.dp, vertical = 6.dp),
                    )
                }
            }
        }
    }
}

@Composable
fun star_toggle_icon(
    is_starred: Boolean,
    icon: ImageVector,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    tint: Color = Color.Unspecified,
    icon_size: Dp = 20.dp,
    touch_size: Dp = 44.dp,
) {
    val colors = AsterMaterial.colors
    val haptics = LocalHapticFeedback.current
    val haptic_enabled = local_accessibility.current.haptic_enabled
    var tooltip_visible by remember { mutableStateOf(false) }
    val label = if (is_starred) stringResource(R.string.starred) else stringResource(R.string.not_starred)
    icon_tooltip_host(
        text = label,
        visible = tooltip_visible,
        on_dismiss = { tooltip_visible = false },
    ) {
        Box(
            modifier = modifier
                .size(touch_size)
                .clip(RoundedCornerShape(percent = 50))
                .combinedClickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null,
                    onClick = onClick,
                    onLongClick = {
                        if (haptic_enabled) haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                        tooltip_visible = true
                    },
                    onClickLabel = label,
                ),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                imageVector = icon,
                contentDescription = label,
                tint = if (tint == Color.Unspecified) colors.text_secondary else tint,
                modifier = Modifier.size(icon_size),
            )
        }
    }
}
