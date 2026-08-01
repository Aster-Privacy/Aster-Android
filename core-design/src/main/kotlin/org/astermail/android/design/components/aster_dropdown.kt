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
import androidx.compose.animation.core.FastOutLinearInEasing
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearOutSlowInEasing
import androidx.compose.animation.core.MutableTransitionState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntRect
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.window.Popup
import androidx.compose.ui.window.PopupPositionProvider
import androidx.compose.ui.window.PopupProperties
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.DpOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import compose.icons.TablerIcons
import compose.icons.tablericons.Check
import org.astermail.android.design.AsterMaterial
import org.astermail.android.design.AsterSpacing
import org.astermail.android.design.SquircleShape

private class aster_dropdown_position_provider(
    private val offset: DpOffset,
    private val density: Density,
    private val on_flip: (Boolean) -> Unit,
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
        if (x + popupContentSize.width > windowSize.width) {
            x = anchorBounds.right - popupContentSize.width - x_offset
        }
        x = x.coerceIn(0, (windowSize.width - popupContentSize.width).coerceAtLeast(0))

        val below = anchorBounds.bottom + y_offset
        val above = anchorBounds.top - popupContentSize.height - y_offset
        val flip = below + popupContentSize.height > windowSize.height && above >= 0
        on_flip(flip)
        val y = if (flip) {
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
    max_height: androidx.compose.ui.unit.Dp = 460.dp,
    content: @Composable ColumnScope.() -> Unit,
) {
    val colors = AsterMaterial.colors
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
    val position_provider = remember(offset, density) {
        aster_dropdown_position_provider(offset, density) { flip -> opens_upward = flip }
    }
    val shape = SquircleShape(18.dp)
    Popup(
        popupPositionProvider = position_provider,
        onDismissRequest = on_dismiss,
        properties = PopupProperties(focusable = true),
    ) {
        AnimatedVisibility(
            visibleState = visible_state,
            enter = fadeIn(animationSpec = tween(120, easing = LinearOutSlowInEasing)) +
                scaleIn(
                    animationSpec = tween(190, easing = FastOutSlowInEasing),
                    initialScale = 0.88f,
                    transformOrigin = TransformOrigin(0f, if (opens_upward) 1f else 0f),
                ),
            exit = fadeOut(animationSpec = tween(110)) +
                scaleOut(
                    animationSpec = tween(130, easing = FastOutLinearInEasing),
                    targetScale = 0.94f,
                    transformOrigin = TransformOrigin(0f, if (opens_upward) 1f else 0f),
                ),
        ) {
            Column(
                modifier = modifier
                    .shadow(18.dp, shape, clip = false)
                    .clip(shape)
                    .background(colors.dropdown_bg)
                    .border(1.dp, colors.border_primary, shape)
                    .defaultMinSize(minWidth = min_width)
                    .heightIn(max = max_height)
                    .verticalScroll(rememberScrollState())
                    .padding(vertical = 6.dp),
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
    val content_color = when {
        !enabled -> colors.text_muted
        destructive -> colors.danger
        tint != null -> tint
        selected -> colors.accent_blue
        else -> colors.text_primary
    }
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 8.dp, vertical = 2.dp)
            .clip(SquircleShape(12.dp))
            .background(if (selected) colors.accent_blue.copy(alpha = 0.16f) else Color.Transparent)
            .clickable(enabled = enabled, onClick = on_click)
            .padding(start = AsterSpacing.md + indent, end = AsterSpacing.md)
            .padding(vertical = 10.dp)
            .then(if (test_tag != null) Modifier.testTag(test_tag) else Modifier),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.Start,
    ) {
        if (leading != null) {
            leading()
            Spacer(Modifier.width(4.dp))
        }
        if (icon != null) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = content_color,
                modifier = Modifier.size(19.dp),
            )
            Spacer(Modifier.width(AsterSpacing.md))
        }
        Text(
            text = label,
            color = content_color,
            fontSize = 15.sp,
            fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
            modifier = Modifier.weight(1f),
        )
        if (count > 0) {
            Spacer(Modifier.width(AsterSpacing.sm))
            Text(
                text = count.toString(),
                color = if (selected) colors.accent_blue else colors.text_secondary,
                fontSize = 13.sp,
                fontWeight = FontWeight.SemiBold,
            )
        }
        if (selected) {
            Spacer(Modifier.width(AsterSpacing.sm))
            Icon(
                imageVector = TablerIcons.Check,
                contentDescription = null,
                tint = colors.accent_blue,
                modifier = Modifier.size(18.dp),
            )
        }
    }
}

@Composable
fun aster_dropdown_section_label(label: String) {
    val colors = AsterMaterial.colors
    Text(
        text = label,
        color = colors.text_muted,
        fontSize = 11.sp,
        fontWeight = FontWeight.SemiBold,
        letterSpacing = 0.9.sp,
        modifier = Modifier.padding(horizontal = AsterSpacing.md + 8.dp, vertical = 6.dp),
    )
}

@Composable
fun aster_dropdown_divider() {
    val colors = AsterMaterial.colors
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 5.dp)
            .padding(horizontal = AsterSpacing.md)
            .height(1.dp)
            .background(colors.border_primary),
    )
}
