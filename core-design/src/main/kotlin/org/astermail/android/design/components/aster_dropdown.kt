//
// Aster Mail - Privacy-first encrypted email
// Copyright (C) 2026 Aster Privacy
//
// This program is free software: you can redistribute it and/or modify
// it under the terms of the GNU Affero General Public License as published by
// the Free Software Foundation, either version 3 of the License, or
// (at your option) any later version.
//
// This program is distributed in the hope that it will be useful,
// but WITHOUT ANY WARRANTY; without even the implied warranty of
// MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
// GNU Affero General Public License for more details.
//
// You should have received a copy of the GNU Affero General Public License
// along with this program.  If not, see <https://www.gnu.org/licenses/>.
//

package org.astermail.android.design.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.EnterExitState
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.MutableTransitionState
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsHoveredAsState
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.material3.ripple
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
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.DpOffset
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntRect
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Popup
import androidx.compose.ui.window.PopupPositionProvider
import androidx.compose.ui.window.PopupProperties
import compose.icons.TablerIcons
import compose.icons.tablericons.Check
import org.astermail.android.design.AsterDuration
import org.astermail.android.design.AsterEasing
import org.astermail.android.design.AsterMaterial
import org.astermail.android.design.AsterScale
import org.astermail.android.design.AsterSlide
import org.astermail.android.design.SquircleShape
import org.astermail.android.design.aster_reduce_motion

private val dropdown_surface_shape = SquircleShape(16.dp)
private val dropdown_item_shape = SquircleShape(11.dp)
private val dropdown_elevation = 16.dp
private val dropdown_surface_padding = 7.dp
private val dropdown_item_min_height = 34.dp
private val dropdown_item_padding_vertical = 8.dp
private val dropdown_item_padding_start = 10.dp
private val dropdown_item_padding_end = 8.dp
private val dropdown_indicator_size = 14.dp
private val dropdown_indicator_gap = 10.dp
private val dropdown_leading_icon_size = 16.dp
private val dropdown_leading_icon_gap = 8.dp
private val dropdown_text_size = 13.sp

private class aster_dropdown_position_provider(
    private val offset: DpOffset,
    private val density: Density,
    private val on_flip: (Boolean, Boolean) -> Unit,
) : PopupPositionProvider {
    override fun calculatePosition(
        anchorBounds: IntRect,
        windowSize: IntSize,
        layoutDirection: LayoutDirection,
        popupContentSize: IntSize,
    ): IntOffset {
        val x_offset = with(density) { offset.x.roundToPx() }
        val y_offset = with(density) { offset.y.roundToPx() }

        var x = anchorBounds.left + x_offset
        var flip_x = false
        if (x + popupContentSize.width > windowSize.width) {
            x = anchorBounds.right - popupContentSize.width - x_offset
            flip_x = true
        }
        x = x.coerceIn(0, (windowSize.width - popupContentSize.width).coerceAtLeast(0))

        val below = anchorBounds.bottom + y_offset
        val above = anchorBounds.top - popupContentSize.height - y_offset
        val flip_y = below + popupContentSize.height > windowSize.height && above >= 0
        on_flip(flip_x, flip_y)
        val y = if (flip_y) {
            above
        } else {
            below.coerceAtMost((windowSize.height - popupContentSize.height).coerceAtLeast(0))
        }
        return IntOffset(x, y)
    }
}

@Composable
fun aster_dropdown_menu(
    expanded: Boolean,
    on_dismiss: () -> Unit,
    modifier: Modifier = Modifier,
    offset: DpOffset = DpOffset(0.dp, 4.dp),
    min_width: androidx.compose.ui.unit.Dp = 200.dp,
    max_width: androidx.compose.ui.unit.Dp = 320.dp,
    max_height: androidx.compose.ui.unit.Dp = 460.dp,
    content: @Composable ColumnScope.() -> Unit,
) {
    val colors = AsterMaterial.colors
    val reduce_motion = aster_reduce_motion()
    val focus_manager = LocalFocusManager.current
    LaunchedEffect(expanded) {
        if (expanded) {
            focus_manager.clearFocus(force = true)
        }
    }
    val visible_state = remember { MutableTransitionState(false) }
    visible_state.targetState = expanded
    if (!visible_state.currentState && !visible_state.targetState) return

    val density = LocalDensity.current
    var opens_upward by remember { mutableStateOf(false) }
    var opens_leftward by remember { mutableStateOf(false) }
    val position_provider = remember(offset, density) {
        aster_dropdown_position_provider(offset, density) { flip_x, flip_y ->
            opens_leftward = flip_x
            opens_upward = flip_y
        }
    }
    val slide_px = with(density) { AsterSlide.menu_dp.dp.toPx() }
    val enter = if (reduce_motion) {
        EnterTransition.None
    } else {
        fadeIn(
            animationSpec = tween(
                durationMillis = AsterDuration.menu_fade_enter,
                easing = AsterEasing.menu_enter,
            ),
        )
    }
    val exit = if (reduce_motion) {
        ExitTransition.None
    } else {
        fadeOut(
            animationSpec = tween(
                durationMillis = AsterDuration.menu_fade_exit,
                easing = AsterEasing.menu_exit,
            ),
        )
    }
    Popup(
        popupPositionProvider = position_provider,
        onDismissRequest = on_dismiss,
        properties = PopupProperties(focusable = true),
    ) {
        AnimatedVisibility(
            visibleState = visible_state,
            enter = enter,
            exit = exit,
        ) {
            val progress = if (reduce_motion) {
                null
            } else {
                transition.animateFloat(
                    transitionSpec = {
                        if (initialState == EnterExitState.PreEnter) {
                            tween(
                                durationMillis = AsterDuration.menu_enter,
                                easing = AsterEasing.menu_enter,
                            )
                        } else {
                            tween(
                                durationMillis = AsterDuration.menu_exit,
                                easing = AsterEasing.menu_exit,
                            )
                        }
                    },
                    label = "aster_dropdown_reveal",
                ) { state ->
                    when (state) {
                        EnterExitState.PreEnter -> 0f
                        EnterExitState.Visible -> 1f
                        EnterExitState.PostExit -> 0f
                    }
                }
            }
            Column(
                modifier = modifier
                    .then(
                        if (progress == null) {
                            Modifier
                        } else {
                            Modifier.graphicsLayer {
                                val t = progress.value
                                val from = if (transition.targetState == EnterExitState.Visible) {
                                    AsterScale.menu_enter_from
                                } else {
                                    AsterScale.menu_exit_to
                                }
                                val scale = from + (1f - from) * t
                                scaleX = scale
                                scaleY = scale
                                translationY = (1f - t) * (if (opens_upward) slide_px else -slide_px)
                                transformOrigin = TransformOrigin(
                                    pivotFractionX = if (opens_leftward) 1f else 0f,
                                    pivotFractionY = if (opens_upward) 1f else 0f,
                                )
                            }
                        },
                    )
                    .shadow(dropdown_elevation, dropdown_surface_shape, clip = false)
                    .clip(dropdown_surface_shape)
                    .background(colors.dropdown_bg)
                    .defaultMinSize(minWidth = min_width)
                    .widthIn(max = max_width)
                    .width(IntrinsicSize.Max)
                    .heightIn(max = max_height)
                    .verticalScroll(rememberScrollState())
                    .padding(dropdown_surface_padding),
                content = content,
            )
        }
    }
}

@Composable
fun aster_dropdown_item(
    label: String,
    on_click: () -> Unit,
    modifier: Modifier = Modifier,
    icon: ImageVector? = null,
    selected: Boolean = false,
    destructive: Boolean = false,
    enabled: Boolean = true,
    tint: Color? = null,
    test_tag: String? = null,
    count: Int = 0,
    indent: androidx.compose.ui.unit.Dp = 0.dp,
    leading: (@Composable () -> Unit)? = null,
) {
    val colors = AsterMaterial.colors
    val reduce_motion = aster_reduce_motion()
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val hovered by interaction.collectIsHoveredAsState()
    val accent = when {
        destructive -> colors.danger
        tint != null -> tint
        else -> colors.accent_blue
    }
    val content_color = when {
        !enabled -> colors.text_muted
        destructive -> colors.danger
        tint != null -> tint
        selected -> accent
        else -> colors.text_secondary
    }
    val trailing_color = when {
        !enabled -> colors.text_muted
        selected -> accent
        else -> colors.text_muted
    }
    val target_container = when {
        !enabled -> Color.Transparent
        pressed || hovered -> colors.dropdown_hover
        else -> Color.Transparent
    }
    val container_color by animateColorAsState(
        targetValue = target_container,
        animationSpec = tween(
            durationMillis = if (reduce_motion) AsterDuration.instant else AsterDuration.menu_state_change,
            easing = AsterEasing.standard_in_out,
        ),
        label = "dropdown_item_container",
    )
    Row(
        modifier = modifier
            .fillMaxWidth()
            .clip(dropdown_item_shape)
            .background(container_color)
            .clickable(
                interactionSource = interaction,
                indication = ripple(color = colors.text_secondary),
                enabled = enabled,
                role = Role.Button,
                onClick = on_click,
            )
            .heightIn(min = dropdown_item_min_height)
            .padding(
                start = dropdown_item_padding_start + indent,
                end = dropdown_item_padding_end,
            )
            .padding(vertical = dropdown_item_padding_vertical)
            .then(if (test_tag != null) Modifier.testTag(test_tag) else Modifier),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.Start,
    ) {
        if (leading != null) {
            leading()
            Spacer(Modifier.width(dropdown_leading_icon_gap))
        }
        if (icon != null) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = content_color,
                modifier = Modifier.size(dropdown_leading_icon_size),
            )
            Spacer(Modifier.width(dropdown_leading_icon_gap))
        }
        Text(
            text = label,
            color = content_color,
            fontSize = dropdown_text_size,
            fontWeight = if (selected) FontWeight.Medium else FontWeight.Normal,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f),
        )
        if (count > 0) {
            Spacer(Modifier.width(dropdown_leading_icon_gap))
            Text(
                text = count.toString(),
                color = trailing_color,
                fontSize = dropdown_text_size,
                fontWeight = FontWeight.Medium,
            )
        }
        Spacer(Modifier.width(dropdown_indicator_gap))
        Box(
            modifier = Modifier.size(dropdown_indicator_size),
            contentAlignment = Alignment.Center,
        ) {
            if (selected) {
                Icon(
                    imageVector = TablerIcons.Check,
                    contentDescription = null,
                    tint = if (enabled) accent else colors.text_muted,
                    modifier = Modifier.size(dropdown_indicator_size),
                )
            }
        }
    }
}

@Composable
fun aster_dropdown_section_label(label: String) {
    val colors = AsterMaterial.colors
    Text(
        text = label,
        color = colors.text_muted,
        fontSize = dropdown_text_size,
        fontWeight = FontWeight.SemiBold,
        modifier = Modifier.padding(horizontal = 8.dp, vertical = 6.dp),
    )
}

@Composable
fun aster_dropdown_divider() {
    val colors = AsterMaterial.colors
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp)
            .height(1.dp)
            .background(colors.border_secondary),
    )
}
