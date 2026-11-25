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

package org.astermail.android.ui.mail

import androidx.compose.animation.Crossfade
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import compose.icons.TablerIcons
import org.astermail.android.R
import compose.icons.tablericons.ChevronRight
import org.astermail.android.design.AsterDuration
import org.astermail.android.design.AsterMaterial
import org.astermail.android.design.AsterShapes
import org.astermail.android.design.AsterSpacing
import org.astermail.android.design.AsterSpring
import org.astermail.android.design.aster_finite
import org.astermail.android.design.aster_spec
import org.astermail.android.design.components.aster_spinner
import org.astermail.android.design.components.shimmer
import org.astermail.android.design.control_surface_color
import org.astermail.android.design.mirror_in_rtl
import org.astermail.android.design.tonal_surface_color

private val quick_action_circle = 52.dp
private val quick_action_icon = 22.dp

internal data class sender_quick_action_face(
    val label: String,
    val icon: ImageVector,
    val busy: Boolean = false,
    val highlighted: Boolean = false,
)

@Composable
internal fun sender_quick_action(
    face: sender_quick_action_face,
    on_click: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    placeholder: Boolean = false,
    state_description: String? = null,
) {
    val colors = AsterMaterial.colors
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val scale by animateFloatAsState(
        targetValue = if (pressed && enabled && !placeholder) 0.92f else 1f,
        animationSpec = aster_spec(AsterSpring.fast_effects()),
        label = "sender_quick_action_scale",
    )
    val base_bg = if (face.highlighted) {
        tonal_surface_color(colors, colors.accent_blue)
    } else {
        control_surface_color(colors)
    }
    val circle_bg by animateColorAsState(
        targetValue = if (pressed && enabled) lerp(base_bg, colors.text_primary, 0.08f) else base_bg,
        animationSpec = aster_finite(tween<Color>(AsterDuration.short_3)),
        label = "sender_quick_action_bg",
    )
    val icon_tint = if (face.highlighted) colors.accent_blue else colors.text_primary
    val fade = aster_finite(tween<Float>(AsterDuration.short_4))
    Column(
        modifier = modifier
            .clip(AsterShapes.item)
            .clickable(
                interactionSource = interaction,
                indication = null,
                enabled = enabled && !placeholder && !face.busy,
                role = Role.Button,
                onClick = on_click,
            )
            .semantics { if (state_description != null) stateDescription = state_description }
            .heightIn(min = 48.dp)
            .padding(vertical = AsterSpacing.xs),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Box(
            modifier = Modifier
                .size(quick_action_circle)
                .graphicsLayer {
                    scaleX = scale
                    scaleY = scale
                }
                .clip(CircleShape)
                .background(circle_bg),
            contentAlignment = Alignment.Center,
        ) {
            if (placeholder) {
                Box(
                    modifier = Modifier
                        .size(quick_action_circle)
                        .shimmer(CircleShape),
                )
            } else {
                Crossfade(
                    targetState = face.busy to face.icon,
                    animationSpec = fade,
                    label = "sender_quick_action_icon",
                ) { (busy, icon) ->
                    Box(modifier = Modifier.size(quick_action_icon), contentAlignment = Alignment.Center) {
                        if (busy) {
                            aster_spinner(
                                modifier = Modifier.size(20.dp),
                                color = icon_tint,
                                stroke_width = 2.dp,
                                track_color = Color.Transparent,
                            )
                        } else {
                            Icon(
                                imageVector = icon,
                                contentDescription = null,
                                tint = icon_tint,
                                modifier = Modifier.size(quick_action_icon),
                            )
                        }
                    }
                }
            }
        }
        Spacer(Modifier.height(AsterSpacing.sm))
        Crossfade(
            targetState = face.label,
            animationSpec = fade,
            label = "sender_quick_action_label",
        ) { label ->
            Text(
                text = label,
                color = if (face.highlighted) colors.accent_blue else colors.text_secondary,
                fontSize = 12.sp,
                lineHeight = 15.sp,
                fontWeight = FontWeight.Medium,
                textAlign = TextAlign.Center,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier
                    .fillMaxWidth()
                    .alpha(if (placeholder) 0f else 1f),
            )
        }
    }
}

@Composable
internal fun sender_sheet_row(
    label: String,
    icon: ImageVector,
    on_click: () -> Unit,
    destructive: Boolean = false,
    show_chevron: Boolean = false,
    enabled: Boolean = true,
    highlighted: Boolean = false,
    busy: Boolean = false,
    state_description: String? = null,
) {
    val colors = AsterMaterial.colors
    val label_color = when {
        destructive -> colors.danger
        highlighted -> colors.accent_blue
        else -> colors.text_primary
    }
    val icon_color = when {
        destructive -> colors.danger
        highlighted -> colors.accent_blue
        else -> colors.text_secondary
    }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(role = Role.Button, enabled = enabled && !busy, onClick = on_click)
            .semantics { if (state_description != null) stateDescription = state_description }
            .heightIn(min = 56.dp)
            .padding(horizontal = AsterSpacing.lg, vertical = AsterSpacing.sm),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(modifier = Modifier.size(22.dp), contentAlignment = Alignment.Center) {
            if (busy) {
                aster_spinner(
                    modifier = Modifier.size(18.dp),
                    color = icon_color,
                    stroke_width = 2.dp,
                    track_color = Color.Transparent,
                )
            } else {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = icon_color,
                    modifier = Modifier.size(22.dp),
                )
            }
        }
        Spacer(Modifier.width(AsterSpacing.md))
        Text(
            text = label,
            color = label_color,
            fontSize = 15.sp,
            fontWeight = FontWeight.Medium,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f),
        )
        if (show_chevron) {
            Spacer(Modifier.width(AsterSpacing.sm))
            Icon(
                imageVector = TablerIcons.ChevronRight,
                contentDescription = null,
                tint = colors.text_muted,
                modifier = Modifier
                    .size(20.dp)
                    .mirror_in_rtl(),
            )
        }
    }
}

@Composable
internal fun sender_identity_badge(
    label: String,
    tint: Color,
    icon: ImageVector? = null,
    logo: Painter? = null,
) {
    val colors = AsterMaterial.colors
    Row(
        modifier = Modifier
            .clip(AsterShapes.pill)
            .background(tonal_surface_color(colors, tint))
            .padding(horizontal = 10.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.Center,
    ) {
        if (logo != null) {
            Image(
                painter = logo,
                contentDescription = null,
                contentScale = ContentScale.Fit,
                modifier = Modifier.size(18.dp),
            )
            Spacer(Modifier.width(4.dp))
        } else if (icon != null) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = tint,
                modifier = Modifier.size(16.dp),
            )
            Spacer(Modifier.width(6.dp))
        }
        Text(
            text = label,
            color = tint,
            fontSize = 12.sp,
            fontWeight = FontWeight.Medium,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

@Composable
internal fun aster_account_badge() {
    val colors = AsterMaterial.colors
    val tint = colors.text_primary
    val template = stringResource(R.string.sender_on_aster_logo, LOGO_SLOT)
    val label = stringResource(R.string.sender_on_aster)
    val split = template.indexOf(LOGO_SLOT)
    val before = if (split >= 0) template.substring(0, split).trim() else ""
    val after = if (split >= 0) template.substring(split + LOGO_SLOT.length).trim() else template
    val joins_before = split > 0 && !template.substring(0, split).endsWith(" ")
    val joins_after = split >= 0 && !template.substring(split + LOGO_SLOT.length).startsWith(" ")
    Row(
        modifier = Modifier
            .clip(AsterShapes.pill)
            .background(tonal_surface_color(colors, colors.accent_blue))
            .padding(horizontal = 10.dp, vertical = 5.dp)
            .clearAndSetSemantics { contentDescription = label },
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (before.isNotEmpty()) {
            Text(text = before, color = tint, fontSize = 12.sp, fontWeight = FontWeight.Medium, maxLines = 1)
            Spacer(Modifier.width(if (joins_before) 2.dp else 5.dp))
        }
        Image(
            painter = painterResource(R.drawable.aster_wordmark),
            contentDescription = null,
            contentScale = ContentScale.Fit,
            modifier = Modifier.height(11.dp),
        )
        if (after.isNotEmpty()) {
            Spacer(Modifier.width(if (joins_after) 2.dp else 5.dp))
            Text(text = after, color = tint, fontSize = 12.sp, fontWeight = FontWeight.Medium, maxLines = 1)
        }
    }
}

private const val LOGO_SLOT = ""
