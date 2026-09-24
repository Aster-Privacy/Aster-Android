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

package org.astermail.android.ui.settings.detail.bimi

import android.graphics.BitmapFactory
import android.util.Base64
import android.util.LruCache
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Cancel
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.ContentCopy
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material.icons.rounded.Error
import androidx.compose.material.icons.rounded.Info
import androidx.compose.material.icons.rounded.Public
import androidx.compose.material.icons.rounded.RemoveCircle
import androidx.compose.material.icons.rounded.Schedule
import androidx.compose.material.icons.rounded.Warning
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.astermail.android.R
import org.astermail.android.api.domains.BimiRecord
import org.astermail.android.api.domains.BimiState
import org.astermail.android.design.AsterMaterial
import org.astermail.android.design.AsterSpacing
import org.astermail.android.design.SquircleShape
import org.astermail.android.design.components.AsterIconButton
import org.astermail.android.settings.BimiErrorKind
import org.astermail.android.ui.settings.detail.copy_dns_value
import org.astermail.android.ui.settings.detail.tone_badge
import org.astermail.android.ui.settings.detail.v_gap

enum class BimiAlertTone { warning, error, success }

private data class BimiAlertPalette(
    val fill_top: Color,
    val fill_bottom: Color,
    val edge_top: Color,
    val edge_bottom: Color,
    val icon: ImageVector,
)

private fun bimi_alert_palette(tone: BimiAlertTone): BimiAlertPalette = when (tone) {
    BimiAlertTone.warning -> BimiAlertPalette(
        Color(0xFFD97706), Color(0xFF92400E), Color(0xFFF59E0B), Color(0xFF78350F), Icons.Rounded.Warning,
    )
    BimiAlertTone.error -> BimiAlertPalette(
        Color(0xFFEF4444), Color(0xFFB91C1C), Color(0xFFF87171), Color(0xFF991B1B), Icons.Rounded.Error,
    )
    BimiAlertTone.success -> BimiAlertPalette(
        Color(0xFF16A34A), Color(0xFF166534), Color(0xFF22C55E), Color(0xFF14532D), Icons.Rounded.CheckCircle,
    )
}

@Composable
internal fun bimi_first_line_icon(icon: ImageVector, tint: Color, size: Dp, line_height: TextUnit) {
    val line = with(LocalDensity.current) { line_height.toDp() }
    Box(
        modifier = Modifier.width(size).height(if (line > size) line else size),
        contentAlignment = Alignment.Center,
    ) {
        Icon(imageVector = icon, contentDescription = null, tint = tint, modifier = Modifier.size(size))
    }
}

@Composable
internal fun bimi_alert(
    tone: BimiAlertTone,
    message: String? = null,
    title: String? = null,
    items: List<String> = emptyList(),
    modifier: Modifier = Modifier,
) {
    val palette = bimi_alert_palette(tone)
    val shape = SquircleShape(8.dp)
    Row(
        modifier = modifier
            .fillMaxWidth()
            .clip(shape)
            .background(Brush.verticalGradient(listOf(palette.fill_top, palette.fill_bottom)), shape)
            .border(1.dp, Brush.verticalGradient(listOf(palette.edge_top, palette.edge_bottom)), shape)
            .padding(horizontal = AsterSpacing.md, vertical = 10.dp)
            .semantics(mergeDescendants = true) {},
        verticalAlignment = Alignment.Top,
    ) {
        bimi_first_line_icon(palette.icon, Color.White, 16.dp, if (title != null) 19.sp else 18.sp)
        Spacer(Modifier.width(AsterSpacing.sm))
        Column(modifier = Modifier.weight(1f)) {
            if (title != null) {
                Text(
                    text = title,
                    color = Color.White,
                    fontSize = 14.sp,
                    lineHeight = 19.sp,
                    fontWeight = FontWeight.Medium,
                )
            }
            if (message != null) {
                if (title != null) v_gap(2.dp)
                Text(text = message, color = Color.White, fontSize = 13.sp, lineHeight = 18.sp)
            }
            if (items.isNotEmpty()) {
                v_gap(AsterSpacing.xs)
                items.forEach { item ->
                    Row(verticalAlignment = Alignment.Top) {
                        Box(modifier = Modifier.height(18.dp), contentAlignment = Alignment.Center) {
                            Box(Modifier.size(4.dp).background(Color.White, CircleShape))
                        }
                        Spacer(Modifier.width(AsterSpacing.sm))
                        Text(
                            text = item,
                            color = Color.White,
                            fontSize = 13.sp,
                            lineHeight = 18.sp,
                            modifier = Modifier.weight(1f),
                        )
                    }
                }
            }
        }
    }
}

@Composable
internal fun bimi_note(icon: ImageVector, text: String, success: Boolean = false) {
    val colors = AsterMaterial.colors
    Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.Top) {
        bimi_first_line_icon(icon, if (success) colors.success else colors.text_tertiary, 16.dp, 18.sp)
        Spacer(Modifier.width(AsterSpacing.sm))
        Text(
            text = text,
            color = colors.text_secondary,
            fontSize = 13.sp,
            lineHeight = 18.sp,
            modifier = Modifier.weight(1f),
        )
    }
}

@Composable
internal fun bimi_section_title(text: String) {
    Text(
        text = text,
        color = AsterMaterial.colors.text_primary,
        fontSize = 14.sp,
        lineHeight = 20.sp,
        fontWeight = FontWeight.Medium,
        modifier = Modifier.semantics { heading() },
    )
}

@Composable
internal fun bimi_stepper(current: Int) {
    val logo_label = stringResource(R.string.domain_bimi_step_logo)
    val publish_label = stringResource(R.string.domain_bimi_step_publish)
    val step_of = stringResource(R.string.domain_bimi_step_of, current, 2)
    val active_label = if (current == 1) logo_label else publish_label
    val colors = AsterMaterial.colors
    Row(
        modifier = Modifier.clearAndSetSemantics { contentDescription = "$step_of, $active_label" },
        verticalAlignment = Alignment.CenterVertically,
    ) {
        bimi_step_node(1, logo_label, current)
        Spacer(Modifier.width(AsterSpacing.md))
        Box(
            Modifier
                .width(32.dp)
                .height(1.dp)
                .background(if (current >= 2) colors.accent_blue else colors.border_secondary),
        )
        Spacer(Modifier.width(AsterSpacing.md))
        bimi_step_node(2, publish_label, current)
    }
}

@Composable
private fun bimi_step_node(number: Int, label: String, current: Int) {
    val colors = AsterMaterial.colors
    val done = number < current
    val active = number == current
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(
            modifier = Modifier
                .size(24.dp)
                .clip(CircleShape)
                .background(if (done) colors.accent_blue else Color.Transparent, CircleShape)
                .border(1.dp, if (done || active) colors.accent_blue else colors.border_secondary, CircleShape),
            contentAlignment = Alignment.Center,
        ) {
            if (done) {
                Icon(
                    imageVector = Icons.Rounded.Check,
                    contentDescription = null,
                    tint = colors.on_accent,
                    modifier = Modifier.size(14.dp),
                )
            } else {
                Text(
                    text = number.toString(),
                    color = if (active) colors.accent_blue else colors.text_muted,
                    fontSize = 12.sp,
                    lineHeight = 12.sp,
                    fontWeight = FontWeight.SemiBold,
                )
            }
        }
        Spacer(Modifier.width(AsterSpacing.sm))
        Text(
            text = label,
            color = if (active) colors.text_primary else colors.text_muted,
            fontSize = 14.sp,
            lineHeight = 20.sp,
            fontWeight = if (active) FontWeight.Medium else FontWeight.Normal,
            maxLines = 1,
        )
    }
}

@Composable
internal fun bimi_state_chip(state: BimiState, modifier: Modifier = Modifier) {
    val colors = AsterMaterial.colors
    val (label, tint, icon) = when (state) {
        BimiState.off -> return
        BimiState.draft -> Triple(stringResource(R.string.domain_bimi_state_draft), colors.text_tertiary, Icons.Rounded.Edit)
        BimiState.pending -> Triple(stringResource(R.string.domain_bimi_state_pending), colors.accent_blue, Icons.Rounded.Schedule)
        BimiState.live -> Triple(stringResource(R.string.domain_bimi_state_live), colors.success, Icons.Rounded.CheckCircle)
        BimiState.attention -> Triple(stringResource(R.string.domain_bimi_state_attention), colors.warning, Icons.Rounded.Warning)
        BimiState.external -> Triple(stringResource(R.string.domain_bimi_state_external), colors.text_tertiary, Icons.Rounded.Public)
    }
    tone_badge(text = label, tone = tint, modifier = modifier, icon = icon, icon_size = 12.dp)
}

@Composable
internal fun bimi_error_text(kind: BimiErrorKind): String = when (kind) {
    BimiErrorKind.generic -> stringResource(R.string.domain_bimi_error_generic)
    BimiErrorKind.throttled -> stringResource(R.string.domain_bimi_error_throttled)
    BimiErrorKind.domain_not_active -> stringResource(R.string.domain_bimi_error_domain_not_active)
    BimiErrorKind.file_too_large -> stringResource(R.string.domain_bimi_error_file_too_large)
    BimiErrorKind.logo_required -> stringResource(R.string.domain_bimi_error_logo_required)
}

internal fun bimi_logo_error_res(code: String): Int = when (code) {
    "too_large" -> R.string.domain_bimi_err_too_large
    "not_utf8" -> R.string.domain_bimi_err_not_utf8
    "doctype_entities" -> R.string.domain_bimi_err_doctype_entities
    "not_svg" -> R.string.domain_bimi_err_not_svg
    "empty" -> R.string.domain_bimi_err_empty
    "missing_view_box" -> R.string.domain_bimi_err_missing_view_box
    "not_square" -> R.string.domain_bimi_err_not_square
    "too_complex" -> R.string.domain_bimi_err_too_complex
    "script_content" -> R.string.domain_bimi_err_script_content
    "external_reference" -> R.string.domain_bimi_err_external_reference
    "invalid_reference" -> R.string.domain_bimi_err_invalid_reference
    "raster_image" -> R.string.domain_bimi_err_raster_image
    "text_not_outlined" -> R.string.domain_bimi_err_text_not_outlined
    "unsupported_style" -> R.string.domain_bimi_err_unsupported_style
    "unsupported_element" -> R.string.domain_bimi_err_unsupported_element
    "invalid_value" -> R.string.domain_bimi_err_invalid_value
    else -> R.string.domain_bimi_err_malformed
}

internal fun bimi_adjustment_res(code: String): Int? = when (code) {
    "set_tiny_ps_profile" -> R.string.domain_bimi_adj_set_tiny_ps_profile
    "added_title" -> R.string.domain_bimi_adj_added_title
    "removed_size" -> R.string.domain_bimi_adj_removed_size
    "removed_position" -> R.string.domain_bimi_adj_removed_position
    "derived_view_box" -> R.string.domain_bimi_adj_derived_view_box
    "removed_doctype" -> R.string.domain_bimi_adj_removed_doctype
    "removed_metadata" -> R.string.domain_bimi_adj_removed_metadata
    "removed_editor_data" -> R.string.domain_bimi_adj_removed_editor_data
    "converted_inline_styles" -> R.string.domain_bimi_adj_converted_inline_styles
    "removed_unsupported_attributes" -> R.string.domain_bimi_adj_removed_unsupported_attributes
    else -> null
}

internal fun bimi_dmarc_res(status: String): Int? = when (status) {
    "ready" -> R.string.domain_bimi_dmarc_ready
    "missing" -> R.string.domain_bimi_dmarc_missing
    "invalid" -> R.string.domain_bimi_dmarc_invalid
    "not_enforced" -> R.string.domain_bimi_dmarc_not_enforced
    "partial" -> R.string.domain_bimi_dmarc_partial
    "subdomain_policy_none" -> R.string.domain_bimi_dmarc_subdomain_policy_none
    "organization_not_enforced" -> R.string.domain_bimi_dmarc_organization_not_enforced
    else -> null
}

internal fun bimi_record_status_res(status: String): Int? = when (status) {
    "missing" -> R.string.domain_bimi_record_missing
    "published" -> R.string.domain_bimi_record_published
    "conflict" -> R.string.domain_bimi_record_conflict
    "external" -> R.string.domain_bimi_record_external
    else -> null
}

internal fun bimi_rule_for_error(code: String): String = when (code) {
    "too_large", "too_complex" -> "size"
    "missing_view_box", "not_square" -> "square"
    "script_content", "external_reference", "invalid_reference" -> "safe"
    "raster_image", "text_not_outlined", "unsupported_style", "unsupported_element", "invalid_value" -> "vector"
    else -> "svg"
}

private val bimi_rules = listOf(
    "svg" to R.string.domain_bimi_rule_svg,
    "square" to R.string.domain_bimi_rule_square,
    "size" to R.string.domain_bimi_rule_size,
    "vector" to R.string.domain_bimi_rule_vector,
    "safe" to R.string.domain_bimi_rule_safe,
)

@Composable
internal fun bimi_check_row(passed: Boolean?, title: String, detail: String? = null) {
    val colors = AsterMaterial.colors
    val (icon, tint) = when (passed) {
        true -> Icons.Rounded.CheckCircle to colors.success
        false -> Icons.Rounded.Cancel to colors.danger
        null -> Icons.Rounded.RemoveCircle to colors.text_muted
    }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 6.dp)
            .semantics(mergeDescendants = true) {},
        verticalAlignment = Alignment.Top,
    ) {
        bimi_first_line_icon(icon, tint, 18.dp, 20.sp)
        Spacer(Modifier.width(AsterSpacing.md))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                color = colors.text_primary,
                fontSize = 14.sp,
                lineHeight = 20.sp,
                fontWeight = if (detail != null) FontWeight.Medium else FontWeight.Normal,
            )
            if (detail != null) {
                Text(text = detail, color = colors.text_secondary, fontSize = 13.sp, lineHeight = 18.sp)
            }
        }
    }
}

@Composable
internal fun bimi_rules_list(errors: List<String>, has_logo: Boolean) {
    val failed = remember(errors) { errors.map(::bimi_rule_for_error).toSet() }
    Column(modifier = Modifier.fillMaxWidth()) {
        bimi_section_title(stringResource(R.string.domain_bimi_rules_title))
        v_gap(AsterSpacing.xs)
        bimi_rules.forEach { (key, res) ->
            val passed = when {
                key in failed -> false
                has_logo -> true
                else -> null
            }
            bimi_check_row(passed = passed, title = stringResource(res))
        }
    }
}

private val bimi_preview_cache = LruCache<String, ImageBitmap>(4)

internal fun decode_bimi_preview(png: String?): ImageBitmap? {
    if (png.isNullOrBlank()) return null
    return try {
        val bytes = Base64.decode(png.substringAfter("base64,"), Base64.DEFAULT)
        BitmapFactory.decodeByteArray(bytes, 0, bytes.size)?.asImageBitmap()
    } catch (_: Throwable) {
        null
    }
}

@Composable
internal fun remember_bimi_preview(png: String?): ImageBitmap? {
    val cached = if (png.isNullOrBlank()) null else bimi_preview_cache.get(png)
    val decoded by produceState<Pair<String, ImageBitmap?>?>(initialValue = null, png) {
        if (png.isNullOrBlank() || bimi_preview_cache.get(png) != null) return@produceState
        val bitmap = withContext(Dispatchers.Default) { decode_bimi_preview(png) }
        if (bitmap != null) bimi_preview_cache.put(png, bitmap)
        value = png to bitmap
    }
    if (cached != null) return cached
    return decoded?.takeIf { it.first == png }?.second
}

@Composable
internal fun bimi_logo_image(
    png: String?,
    size: Dp,
    content_description: String?,
    border_color: Color = AsterMaterial.colors.border_secondary,
    placeholder_color: Color = AsterMaterial.colors.bg_secondary,
) {
    val bitmap = remember_bimi_preview(png)
    Box(
        modifier = Modifier
            .size(size)
            .clip(CircleShape)
            .background(if (bitmap != null) Color.White else placeholder_color, CircleShape)
            .border(1.dp, border_color, CircleShape),
    ) {
        if (bitmap != null) {
            Image(
                bitmap = bitmap,
                contentDescription = content_description,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize(),
            )
        }
    }
}

private data class BimiPanelPalette(
    val background: Color,
    val border: Color,
    val divider: Color,
    val strong: Color,
    val muted: Color,
    val placeholder: Color,
)

private val bimi_light_panel = BimiPanelPalette(
    Color.White, Color(0xFFE5E7EB), Color(0xFFF0F1F3), Color(0xFF111827), Color(0xFF6B7280), Color(0xFFECEEF1),
)

private val bimi_dark_panel = BimiPanelPalette(
    Color(0xFF121212), Color(0xFF2A2A2A), Color(0xFF242424), Color(0xFFF5F5F5), Color(0xFFA3A3A3), Color(0xFF2A2A2A),
)

@Composable
internal fun bimi_inbox_preview(domain_name: String, png: String?) {
    val context = LocalContext.current
    val time = remember { android.text.format.DateFormat.getTimeFormat(context).format(java.util.Date()) }
    Column(modifier = Modifier.fillMaxWidth()) {
        bimi_section_title(stringResource(R.string.domain_bimi_preview_inbox_title))
        v_gap(AsterSpacing.sm)
        bimi_preview_panel(stringResource(R.string.domain_bimi_preview_light), bimi_light_panel, domain_name, png, time)
        v_gap(AsterSpacing.md)
        bimi_preview_panel(stringResource(R.string.domain_bimi_preview_dark), bimi_dark_panel, domain_name, png, time)
    }
}

@Composable
private fun bimi_preview_panel(
    label: String,
    palette: BimiPanelPalette,
    domain_name: String,
    png: String?,
    time: String,
) {
    val shape = SquircleShape(12.dp)
    Column(modifier = Modifier.fillMaxWidth()) {
        Text(text = label, color = AsterMaterial.colors.text_tertiary, fontSize = 12.sp, lineHeight = 16.sp)
        v_gap(AsterSpacing.xs)
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .clip(shape)
                .background(palette.background, shape)
                .border(1.dp, palette.border, shape),
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = AsterSpacing.md, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                bimi_logo_image(
                    png = png,
                    size = 32.dp,
                    content_description = stringResource(R.string.domain_bimi_preview_alt),
                    border_color = palette.border,
                    placeholder_color = palette.placeholder,
                )
                Spacer(Modifier.width(AsterSpacing.md))
                Column(modifier = Modifier.weight(1f).clearAndSetSemantics {}) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            text = domain_name,
                            color = palette.strong,
                            fontSize = 14.sp,
                            lineHeight = 18.sp,
                            fontWeight = FontWeight.SemiBold,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.weight(1f),
                        )
                        Spacer(Modifier.width(AsterSpacing.sm))
                        Text(text = time, color = palette.muted, fontSize = 12.sp, lineHeight = 16.sp, maxLines = 1)
                    }
                    Text(
                        text = stringResource(R.string.domain_bimi_preview_inbox_subject),
                        color = palette.muted,
                        fontSize = 13.sp,
                        lineHeight = 18.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
            Box(Modifier.fillMaxWidth().height(1.dp).background(palette.divider))
            bimi_placeholder_row(palette, 0.4f, 0.8f)
            Box(Modifier.fillMaxWidth().height(1.dp).background(palette.divider))
            bimi_placeholder_row(palette, 0.33f, 0.6f)
        }
    }
}

@Composable
private fun bimi_placeholder_row(palette: BimiPanelPalette, first: Float, second: Float) {
    val bar = RoundedCornerShape(4.dp)
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = AsterSpacing.md, vertical = 10.dp)
            .clearAndSetSemantics {},
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(Modifier.size(32.dp).background(palette.placeholder, CircleShape))
        Spacer(Modifier.width(AsterSpacing.md))
        Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Box(Modifier.fillMaxWidth(first).height(8.dp).background(palette.placeholder, bar))
            Box(Modifier.fillMaxWidth(second).height(8.dp).background(palette.placeholder, bar))
        }
    }
}

private fun wrap_by_character(value: String): String = value.toList().joinToString("​")

@Composable
internal fun bimi_record_rows(record: BimiRecord) {
    val colors = AsterMaterial.colors
    val context = LocalContext.current
    val shape = SquircleShape(12.dp)
    val copy_label = stringResource(R.string.copy)
    val rows = listOf(
        stringResource(R.string.domain_bimi_record_type) to record.record_type,
        stringResource(R.string.domain_bimi_record_host) to record.host,
        stringResource(R.string.domain_bimi_record_value) to record.value,
    )
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(shape)
            .border(1.dp, colors.border_secondary, shape),
    ) {
        rows.forEachIndexed { index, (label, value) ->
            if (index > 0) Box(Modifier.fillMaxWidth().height(1.dp).background(colors.border_secondary))
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(start = AsterSpacing.md, end = AsterSpacing.xs, top = 10.dp, bottom = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(
                    modifier = Modifier
                        .weight(1f)
                        .clearAndSetSemantics { contentDescription = "$label, $value" },
                ) {
                    Text(text = label, color = colors.text_tertiary, fontSize = 12.sp, lineHeight = 16.sp)
                    v_gap(2.dp)
                    Text(
                        text = wrap_by_character(value),
                        color = colors.text_primary,
                        fontSize = 13.sp,
                        lineHeight = 18.sp,
                        fontFamily = FontFamily.Monospace,
                    )
                }
                Spacer(Modifier.width(AsterSpacing.xs))
                AsterIconButton(
                    icon = Icons.Rounded.ContentCopy,
                    content_description = "$copy_label $label",
                    onClick = { copy_dns_value(context, label, value) },
                    tint = colors.text_tertiary,
                    icon_size = 18,
                )
            }
        }
    }
}

@Composable
internal fun bimi_adjustments_row(adjustments: List<String>) {
    val colors = AsterMaterial.colors
    val lines = adjustments.mapNotNull { bimi_adjustment_res(it) }.distinct()
    if (lines.isEmpty()) return
    var expanded by rememberSaveable { mutableStateOf(false) }
    Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.Top) {
        bimi_first_line_icon(Icons.Rounded.Info, colors.text_tertiary, 16.dp, 18.sp)
        Spacer(Modifier.width(AsterSpacing.sm))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = stringResource(R.string.domain_bimi_adjustments_title),
                color = colors.text_secondary,
                fontSize = 13.sp,
                lineHeight = 18.sp,
            )
            Text(
                text = stringResource(if (expanded) R.string.detail_hide_details else R.string.detail_show_details),
                color = colors.accent_blue,
                fontSize = 13.sp,
                lineHeight = 18.sp,
                fontWeight = FontWeight.Medium,
                modifier = Modifier
                    .clip(RoundedCornerShape(4.dp))
                    .clickable(role = Role.Button) { expanded = !expanded }
                    .padding(vertical = AsterSpacing.xs),
            )
            if (expanded) {
                lines.forEach { res ->
                    Row(verticalAlignment = Alignment.Top) {
                        Box(modifier = Modifier.height(18.dp), contentAlignment = Alignment.Center) {
                            Box(Modifier.size(4.dp).background(colors.text_tertiary, CircleShape))
                        }
                        Spacer(Modifier.width(AsterSpacing.sm))
                        Text(
                            text = stringResource(res),
                            color = colors.text_secondary,
                            fontSize = 13.sp,
                            lineHeight = 18.sp,
                            modifier = Modifier.weight(1f),
                        )
                    }
                }
            }
        }
    }
}

@Composable
internal fun bimi_compact_button(
    label: String,
    on_click: () -> Unit,
    enabled: Boolean = true,
    modifier: Modifier = Modifier,
) {
    val colors = AsterMaterial.colors
    val shape = SquircleShape(10.dp)
    Box(
        modifier = modifier
            .minimumInteractiveComponentSize()
            .heightIn(min = 36.dp)
            .alpha(if (enabled) 1f else 0.5f)
            .clip(shape)
            .border(1.dp, colors.border_primary, shape)
            .clickable(enabled = enabled, role = Role.Button, onClick = on_click)
            .padding(horizontal = AsterSpacing.md),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = label,
            color = colors.text_primary,
            fontSize = 13.sp,
            lineHeight = 18.sp,
            fontWeight = FontWeight.Medium,
            maxLines = 1,
        )
    }
}
