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

private fun galaxy_border_brush(colors: AsterSemanticColors): Brush = Brush.verticalGradient(
    0.0f to lerp(colors.accent_blue, Color.White, 0.3f),
    0.14f to colors.accent_blue,
    0.38f to colors.accent_blue.copy(alpha = 0.4f),
    0.66f to colors.accent_blue.copy(alpha = 0.12f),
    1.0f to colors.border_primary,
)

private fun galaxy_wash_brush(colors: AsterSemanticColors): Brush = Brush.verticalGradient(
    0.0f to colors.accent_blue.copy(alpha = 0.09f),
    0.22f to colors.accent_blue.copy(alpha = 0.03f),
    0.46f to Color.Transparent,
    1.0f to Color.Transparent,
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
        .background(lerp(colors.bg_card, colors.text_primary, 0.10f), shape)
        .border(BorderStroke(1.dp, colors.text_primary.copy(alpha = 0.16f)), shape)
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
private fun plan_badge(text: String, is_current: Boolean, modifier: Modifier = Modifier) {
    val colors = AsterMaterial.colors
    Box(
        modifier = modifier
            .clip(CircleShape)
            .then(
                if (is_current) {
                    Modifier.background(colors.accent_blue)
                } else {
                    Modifier.background(accent_depth_brush(colors))
                },
            )
            .padding(horizontal = 12.dp, vertical = 4.dp),
    ) {
        Text(
            text = text.uppercase(),
            color = colors.on_accent,
            fontSize = 10.sp,
            fontWeight = FontWeight.Bold,
            letterSpacing = 0.8.sp,
            maxLines = 1,
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

@Composable
internal fun billing_plan_card(
    name: String,
    tagline: String,
    price_label: String?,
    period_label: String,
    anchor_label: String?,
    save_label: String?,
    billed_note: String?,
    badge_label: String?,
    is_current: Boolean,
    is_featured: Boolean,
    lead_in: String?,
    features: List<Int>,
    show_trust_note: Boolean,
    modifier: Modifier = Modifier,
    cta: @Composable () -> Unit,
) {
    val colors = AsterMaterial.colors
    val galaxy = is_featured && !is_current
    var expanded by remember(name) { mutableStateOf(galaxy || is_current) }
    val chevron_turn by animateFloatAsState(
        targetValue = if (expanded) 180f else 0f,
        animationSpec = tween(durationMillis = 180),
        label = "billing_plan_chevron",
    )
    val border = when {
        galaxy -> BorderStroke(1.5.dp, galaxy_border_brush(colors))
        is_current -> BorderStroke(1.5.dp, Brush.verticalGradient(listOf(colors.accent_blue, colors.accent_blue)))
        else -> BorderStroke(1.dp, Brush.verticalGradient(listOf(colors.border_primary, colors.border_primary)))
    }
    Box(modifier = modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = if (badge_label != null) 11.dp else 0.dp)
                .border(border, billing_plan_shape)
                .clip(billing_plan_shape)
                .acrylic(colors, billing_plan_shape, colors.bg_secondary)
                .then(if (galaxy) Modifier.background(galaxy_wash_brush(colors)) else Modifier),
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = AsterSpacing.lg)
                    .padding(
                        top = if (badge_label != null) AsterSpacing.xl else AsterSpacing.lg,
                        bottom = AsterSpacing.lg,
                    ),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Text(
                    text = name,
                    color = colors.text_primary,
                    fontSize = 17.sp,
                    fontWeight = FontWeight.SemiBold,
                    textAlign = TextAlign.Center,
                )
                Spacer(Modifier.height(AsterSpacing.sm))
                if (price_label != null) {
                    Row(
                        verticalAlignment = Alignment.Bottom,
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                    ) {
                        if (anchor_label != null) {
                            Text(
                                text = anchor_label,
                                color = colors.text_muted,
                                fontSize = 17.sp,
                                fontWeight = FontWeight.SemiBold,
                                textDecoration = TextDecoration.LineThrough,
                                modifier = Modifier.padding(bottom = 3.dp),
                            )
                        }
                        Text(
                            text = price_label,
                            color = colors.text_primary,
                            fontSize = 30.sp,
                            lineHeight = 32.sp,
                            fontWeight = FontWeight.Bold,
                            letterSpacing = (-0.5).sp,
                            maxLines = 1,
                        )
                        Text(
                            text = period_label,
                            color = colors.text_muted,
                            fontSize = 14.sp,
                            modifier = Modifier.padding(bottom = 4.dp),
                        )
                    }
                    if (save_label != null) {
                        Spacer(Modifier.height(AsterSpacing.sm))
                        billing_pill(
                            text = save_label.uppercase(),
                            foreground = colors.on_accent,
                            background = colors.accent_blue,
                        )
                    }
                    Spacer(Modifier.height(AsterSpacing.xs))
                    Box(modifier = Modifier.height(16.dp), contentAlignment = Alignment.Center) {
                        if (billed_note != null) {
                            Text(
                                text = billed_note,
                                color = colors.text_muted,
                                fontSize = 12.sp,
                                textAlign = TextAlign.Center,
                            )
                        }
                    }
                }
                Spacer(Modifier.height(6.dp))
                Text(
                    text = tagline,
                    color = colors.text_muted,
                    fontSize = 13.sp,
                    lineHeight = 18.sp,
                    textAlign = TextAlign.Center,
                )
                Spacer(Modifier.height(AsterSpacing.lg))
                cta()
                if (show_trust_note) {
                    Spacer(Modifier.height(AsterSpacing.sm))
                    Text(
                        text = stringResource(R.string.billing_money_back_guarantee) +
                            " · " +
                            stringResource(R.string.billing_cancel_anytime),
                        color = colors.text_muted,
                        fontSize = 11.sp,
                        lineHeight = 14.sp,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
            }
            billing_divider()
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable(role = Role.Button) { expanded = !expanded }
                    .padding(horizontal = AsterSpacing.lg, vertical = 14.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = stringResource(
                        if (expanded) R.string.billing_plan_hide_features else R.string.billing_plan_show_features,
                    ),
                    color = colors.accent_blue,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.weight(1f),
                )
                Icon(
                    imageVector = TablerIcons.ChevronDown,
                    contentDescription = null,
                    tint = colors.accent_blue,
                    modifier = Modifier
                        .size(18.dp)
                        .rotate(chevron_turn),
                )
            }
            AnimatedVisibility(
                visible = expanded,
                enter = expandVertically(animationSpec = tween(durationMillis = 180)) + fadeIn(),
                exit = shrinkVertically(animationSpec = tween(durationMillis = 180)) + fadeOut(),
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = AsterSpacing.lg)
                        .padding(bottom = AsterSpacing.lg),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    if (lead_in != null) {
                        Text(
                            text = lead_in.uppercase(),
                            color = colors.text_muted,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.SemiBold,
                            letterSpacing = 0.6.sp,
                        )
                    }
                    features.forEach { feature_res -> plan_feature_line(feature_res) }
                }
            }
        }
        if (badge_label != null) {
            plan_badge(
                text = badge_label,
                is_current = is_current,
                modifier = Modifier.align(Alignment.TopCenter),
            )
        }
    }
}
