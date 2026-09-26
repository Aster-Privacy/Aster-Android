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

import androidx.compose.animation.core.FastOutLinearInEasing
import androidx.compose.animation.core.LinearOutSlowInEasing
import androidx.compose.animation.core.MutableTransitionState
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.rememberTransition
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
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
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.Dp
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
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import compose.icons.TablerIcons
import compose.icons.tablericons.Check
import org.astermail.android.design.AsterMaterial
import org.astermail.android.design.acrylic
import org.astermail.android.design.SquircleShape
import org.astermail.android.design.aster_reduce_motion
import org.astermail.android.design.aster_ripple

private val menu_surface_shape = SquircleShape(22.dp)
private val menu_item_shape = SquircleShape(14.dp)
private val menu_screen_margin = 12.dp
private val menu_surface_padding = 6.dp
private val menu_surface_elevation = 18.dp
private val menu_item_min_height = 44.dp
private val menu_item_padding_horizontal = 12.dp
private val menu_icon_size = 18.dp
private val menu_icon_gap = 12.dp
private val menu_check_size = 16.dp
private val menu_text_size = 15.sp
private val menu_label_size = 12.sp
private const val menu_surface_lift = 0.14f
private const val menu_border_lift_dark = 0.09f
private const val menu_border_lift_light = 0.07f
private const val menu_enter_scale = 0.85f
private const val menu_exit_scale = 0.88f

private class menu_anchor_state {
    var bounds by mutableStateOf<IntRect?>(null)
}

private class menu_full_window_provider(private val state: menu_anchor_state) : PopupPositionProvider {
    override fun calculatePosition(
        anchorBounds: IntRect,
        windowSize: IntSize,
        layoutDirection: LayoutDirection,
        popupContentSize: IntSize,
    ): IntOffset {
        if (state.bounds != anchorBounds) state.bounds = anchorBounds
        return IntOffset.Zero
    }
}

fun aster_menu_surface_color(dropdown_bg: Color, is_dark: Boolean): Color {
    val base = dropdown_bg.copy(alpha = 1f)
    return if (is_dark) lerp(base, Color.White, menu_surface_lift) else base
}

fun aster_menu_border_color(dropdown_bg: Color, is_dark: Boolean): Color {
    val edge = if (is_dark) Color.White else Color.Black
    val amount = if (is_dark) menu_border_lift_dark else menu_border_lift_light
    return lerp(aster_menu_surface_color(dropdown_bg, is_dark), edge, amount)
}

@Composable
fun aster_menu_surface_color(): Color {
    val colors = AsterMaterial.colors
    return aster_menu_surface_color(colors.dropdown_bg, colors.is_dark)
}

@Composable
fun aster_menu_border_color(): Color {
    val colors = AsterMaterial.colors
    return aster_menu_border_color(colors.dropdown_bg, colors.is_dark)
}

@Composable
fun aster_menu_surface(
    modifier: Modifier = Modifier,
    min_width: Dp = 200.dp,
    max_width: Dp = 320.dp,
    max_height: Dp = 520.dp,
    content: @Composable ColumnScope.() -> Unit,
) {
    Column(
        modifier = modifier
            .shadow(menu_surface_elevation, menu_surface_shape, clip = false)
            .acrylic(AsterMaterial.colors, menu_surface_shape, aster_menu_surface_color())
            .defaultMinSize(minWidth = min_width)
            .widthIn(max = max_width)
            .width(IntrinsicSize.Max)
            .heightIn(max = max_height)
            .verticalScroll(rememberScrollState())
            .padding(menu_surface_padding),
        content = content,
    )
}

@Composable
fun aster_menu(
    expanded: Boolean,
    on_dismiss: () -> Unit,
    modifier: Modifier = Modifier,
    offset: DpOffset = DpOffset(0.dp, 6.dp),
    min_width: Dp = 200.dp,
    max_width: Dp = 320.dp,
    max_height: Dp = 520.dp,
    content: @Composable ColumnScope.() -> Unit,
) {
    val reduce_motion = aster_reduce_motion()
    val focus_manager = LocalFocusManager.current
    val parent_view = LocalView.current
    if (expanded) {
        remember(parent_view) {
            ViewCompat.getWindowInsetsController(parent_view)
                ?.hide(WindowInsetsCompat.Type.ime())
            true
        }
    }
    LaunchedEffect(expanded) {
        if (expanded) focus_manager.clearFocus(force = true)
    }
    val visible_state = remember { MutableTransitionState(false) }
    visible_state.targetState = expanded
    if (!visible_state.currentState && !visible_state.targetState) return

    val anchor = remember { menu_anchor_state() }
    val provider = remember(anchor) { menu_full_window_provider(anchor) }
    val transition = rememberTransition(visible_state, label = "aster_menu")
    val scale by transition.animateFloat(
        transitionSpec = {
            when {
                reduce_motion -> snap()
                targetState -> spring(dampingRatio = 0.86f, stiffness = 700f)
                else -> tween(120, easing = FastOutLinearInEasing)
            }
        },
        label = "aster_menu_scale",
    ) { shown ->
        when {
            shown || reduce_motion -> 1f
            visible_state.targetState -> menu_enter_scale
            else -> menu_exit_scale
        }
    }
    val fade by transition.animateFloat(
        transitionSpec = {
            when {
                reduce_motion -> snap()
                targetState -> tween(120, easing = LinearOutSlowInEasing)
                else -> tween(110, easing = FastOutLinearInEasing)
            }
        },
        label = "aster_menu_fade",
    ) { shown -> if (shown) 1f else 0f }
    val content_fade by transition.animateFloat(
        transitionSpec = {
            when {
                reduce_motion -> snap()
                targetState -> tween(150, delayMillis = 50, easing = LinearOutSlowInEasing)
                else -> tween(80)
            }
        },
        label = "aster_menu_content_fade",
    ) { shown -> if (shown) 1f else 0f }

    val menu_colors = AsterMaterial.colors
    val surface_color = aster_menu_surface_color()
    val scrim_color = Color.Black.copy(alpha = if (menu_colors.is_dark) 0.34f else 0.16f)
    val surface_tap = remember { MutableInteractionSource() }

    Popup(
        popupPositionProvider = provider,
        onDismissRequest = on_dismiss,
        properties = PopupProperties(focusable = true),
    ) {
        val popup_view = LocalView.current
        Box(modifier = Modifier.fillMaxSize()) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .graphicsLayer { alpha = fade }
                    .background(scrim_color)
                    .pointerInput(on_dismiss) {
                        awaitEachGesture {
                            awaitFirstDown(requireUnconsumed = false)
                            on_dismiss()
                        }
                    },
            )
            Layout(
                content = {
                    Column(
                        modifier = modifier
                            .acrylic(menu_colors, menu_surface_shape, surface_color)
                            .clickable(interactionSource = surface_tap, indication = null) {}
                            .defaultMinSize(minWidth = min_width)
                            .widthIn(max = max_width)
                            .width(IntrinsicSize.Max)
                            .heightIn(max = max_height)
                            .verticalScroll(rememberScrollState())
                            .padding(menu_surface_padding)
                            .graphicsLayer { alpha = content_fade },
                        content = content,
                    )
                },
            ) { measurables, constraints ->
                val margin = menu_screen_margin.roundToPx()
                val placeable = measurables.first().measure(
                    Constraints(
                        maxWidth = (constraints.maxWidth - margin * 2).coerceAtLeast(0),
                        maxHeight = (constraints.maxHeight - margin * 2).coerceAtLeast(0),
                    ),
                )
                layout(constraints.maxWidth, constraints.maxHeight) {
                    val bounds = anchor.bounds ?: return@layout
                    val parent_screen = IntArray(2).also { parent_view.getLocationOnScreen(it) }
                    val parent_window = IntArray(2).also { parent_view.getLocationInWindow(it) }
                    val popup_screen = IntArray(2).also { popup_view.getLocationOnScreen(it) }
                    val dx = parent_screen[0] - parent_window[0] - popup_screen[0]
                    val dy = parent_screen[1] - parent_window[1] - popup_screen[1]
                    val left = bounds.left + dx
                    val right = bounds.right + dx
                    val top = bounds.top + dy
                    val bottom = bounds.bottom + dy
                    val gap_x = offset.x.roundToPx()
                    val gap_y = offset.y.roundToPx()
                    val width = constraints.maxWidth
                    val height = constraints.maxHeight
                    val leftward = (left + right) / 2 > width / 2
                    val raw_x = if (leftward) right - placeable.width - gap_x else left + gap_x
                    val x = raw_x.coerceIn(margin, (width - placeable.width - margin).coerceAtLeast(margin))
                    val below = bottom + gap_y
                    val above = top - placeable.height - gap_y
                    val upward = below + placeable.height > height - margin && above >= margin
                    val y = if (upward) {
                        above
                    } else {
                        below.coerceAtMost((height - placeable.height - margin).coerceAtLeast(margin))
                    }
                    val anchor_x = ((left + right) / 2f - x) / placeable.width.coerceAtLeast(1)
                    val anchor_y = ((top + bottom) / 2f - y) / placeable.height.coerceAtLeast(1)
                    placeable.placeWithLayer(x, y) {
                        shape = menu_surface_shape
                        clip = true
                        shadowElevation = menu_surface_elevation.toPx()
                        ambientShadowColor = Color.Black
                        spotShadowColor = Color.Black
                        alpha = fade
                        scaleX = scale
                        scaleY = scale
                        transformOrigin = TransformOrigin(
                            anchor_x.coerceIn(0f, 1f),
                            anchor_y.coerceIn(0f, 1f),
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun aster_menu_item(
    label: String,
    on_click: () -> Unit,
    modifier: Modifier = Modifier,
    icon: ImageVector? = null,
    selected: Boolean = false,
    destructive: Boolean = false,
    enabled: Boolean = true,
    tint: Color? = null,
    icon_tint: Color? = null,
    test_tag: String? = null,
    count: Int = 0,
    indent: Dp = 0.dp,
    leading: (@Composable () -> Unit)? = null,
) {
    val colors = AsterMaterial.colors
    val interaction = remember { MutableInteractionSource() }
    val text_color = when {
        !enabled -> colors.text_muted
        destructive -> colors.danger
        tint != null -> tint
        else -> colors.text_primary
    }
    val resolved_icon_tint = when {
        !enabled -> colors.text_muted
        destructive -> colors.danger
        icon_tint != null -> icon_tint
        tint != null -> tint
        else -> colors.text_secondary
    }
    Row(
        modifier = modifier
            .fillMaxWidth()
            .clip(menu_item_shape)
            .clickable(
                interactionSource = interaction,
                indication = aster_ripple(color = colors.text_secondary),
                enabled = enabled,
                role = Role.Button,
                onClick = on_click,
            )
            .heightIn(min = menu_item_min_height)
            .padding(start = menu_item_padding_horizontal + indent, end = menu_item_padding_horizontal)
            .padding(vertical = 6.dp)
            .then(if (test_tag != null) Modifier.testTag(test_tag) else Modifier),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (leading != null) {
            leading()
            Spacer(Modifier.width(menu_icon_gap))
        }
        if (icon != null) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = resolved_icon_tint,
                modifier = Modifier.size(menu_icon_size),
            )
            Spacer(Modifier.width(menu_icon_gap))
        }
        Text(
            text = label,
            color = text_color,
            fontSize = menu_text_size,
            lineHeight = 20.sp,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f),
        )
        if (count > 0) {
            Spacer(Modifier.width(12.dp))
            Text(
                text = if (count > 999) "999+" else count.toString(),
                color = colors.text_primary.copy(alpha = if (selected) 0.75f else 0.45f),
                fontSize = 13.sp,
            )
        }
        if (selected) {
            Spacer(Modifier.width(12.dp))
            Icon(
                imageVector = TablerIcons.Check,
                contentDescription = null,
                tint = if (enabled) colors.accent_blue else colors.text_muted,
                modifier = Modifier.size(menu_check_size),
            )
        }
    }
}

@Composable
fun aster_menu_section_label(label: String) {
    Text(
        text = label,
        color = AsterMaterial.colors.text_primary.copy(alpha = 0.45f),
        fontSize = menu_label_size,
        fontWeight = FontWeight.Medium,
        modifier = Modifier.padding(start = menu_item_padding_horizontal, end = menu_item_padding_horizontal, top = 8.dp, bottom = 4.dp),
    )
}
