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

import androidx.annotation.StringRes
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import compose.icons.TablerIcons
import compose.icons.tablericons.ChevronDown
import org.astermail.android.R
import org.astermail.android.design.AsterMaterial
import org.astermail.android.design.AsterSemanticColors
import org.astermail.android.design.AsterSpacing
import org.astermail.android.design.SquircleShape
import org.astermail.android.design.acrylic

internal val billing_plan_shape: Shape = SquircleShape(16.dp)

private val quantity_prefix = Regex("^(\\d[\\d.,]*(?:\\s?[GMT]B)?)\\s+(.+)$", RegexOption.IGNORE_CASE)

internal fun accent_depth_brush(colors: AsterSemanticColors): Brush = Brush.verticalGradient(
    listOf(
        lerp(colors.accent_blue, Color.White, 0.18f),
        colors.accent_blue,
        lerp(colors.accent_blue, Color.Black, 0.16f),
    ),
)

@Composable
internal fun billing_cta_button(
    label: String,
    enabled: Boolean,
    filled: Boolean,
    on_click: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = AsterMaterial.colors
    val shape = SquircleShape(14.dp)
    val outlined_surface = Modifier
        .acrylic(colors, shape, colors.bg_card)
        .border(BorderStroke(1.dp, colors.border_primary), shape)
    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(44.dp)
            .clip(shape)
            .then(
                if (filled) Modifier.background(accent_depth_brush(colors)) else outlined_surface,
            )
            .clickable(enabled = enabled, role = Role.Button, onClick = on_click),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = label,
            color = when {
                filled -> colors.on_accent
                enabled -> colors.text_primary
                else -> colors.text_muted
            },
            fontSize = 15.sp,
            fontWeight = FontWeight.SemiBold,
            maxLines = 1,
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(horizontal = AsterSpacing.md),
        )
    }
}

@Composable
internal fun plan_feature_label(@StringRes feature_res: Int): AnnotatedString {
    val colors = AsterMaterial.colors
    val raw = stringResource(feature_res)
    val match = quantity_prefix.find(raw)
    return buildAnnotatedString {
        if (match == null) {
            append(raw)
        } else {
            withStyle(SpanStyle(color = colors.text_primary, fontWeight = FontWeight.SemiBold)) {
                append(match.groupValues[1])
            }
            append(" ")
            append(match.groupValues[2])
        }
    }
}

@Composable
internal fun plan_feature_line(@StringRes feature_res: Int) {
    val colors = AsterMaterial.colors
    Row(verticalAlignment = Alignment.Top) {
        Icon(
            imageVector = plan_feature_icon(feature_res),
            contentDescription = null,
            tint = colors.accent_blue,
            modifier = Modifier
                .padding(top = 3.dp)
                .size(17.dp),
        )
        Spacer(Modifier.width(AsterSpacing.md))
        Text(
            text = plan_feature_label(feature_res),
            color = colors.text_secondary,
            fontSize = 14.sp,
            lineHeight = 19.sp,
        )
    }
}
