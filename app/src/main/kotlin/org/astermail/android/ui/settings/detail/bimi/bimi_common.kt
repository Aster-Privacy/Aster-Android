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
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import compose.icons.TablerIcons
import compose.icons.tablericons.AlertTriangle
import compose.icons.tablericons.Check
import compose.icons.tablericons.Clock
import org.astermail.android.R
import org.astermail.android.api.domains.BimiRecord
import org.astermail.android.api.domains.BimiState
import org.astermail.android.design.AsterMaterial
import org.astermail.android.design.AsterSpacing
import org.astermail.android.design.SquircleShape
import org.astermail.android.design.acrylic
import org.astermail.android.settings.BimiErrorKind
import org.astermail.android.ui.settings.detail.copy_dns_value
import org.astermail.android.ui.settings.detail.dns_record_field
import org.astermail.android.ui.settings.detail.domain_status_badge
import org.astermail.android.ui.settings.detail.v_gap

@Composable
internal fun bimi_state_chip(state: BimiState) {
    val colors = AsterMaterial.colors
    val (label, tint) = when (state) {
        BimiState.off -> return
        BimiState.draft -> stringResource(R.string.domain_bimi_state_draft) to colors.text_tertiary
        BimiState.pending -> stringResource(R.string.domain_bimi_state_pending) to colors.accent_blue
        BimiState.live -> stringResource(R.string.domain_bimi_state_live) to colors.success
        BimiState.attention -> stringResource(R.string.domain_bimi_state_attention) to colors.warning
        BimiState.external -> stringResource(R.string.domain_bimi_state_external) to colors.text_tertiary
    }
    domain_status_badge(text = label, tint = tint)
}

@Composable
internal fun bimi_error_text(kind: BimiErrorKind): String = when (kind) {
    BimiErrorKind.generic -> stringResource(R.string.domain_bimi_error_generic)
    BimiErrorKind.throttled -> stringResource(R.string.domain_bimi_error_throttled)
    BimiErrorKind.domain_not_active -> stringResource(R.string.domain_bimi_error_domain_not_active)
    BimiErrorKind.file_too_large -> stringResource(R.string.domain_bimi_error_file_too_large)
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
private fun bimi_logo_image(bitmap: ImageBitmap, size: Dp, shape: androidx.compose.ui.graphics.Shape) {
    Image(
        bitmap = bitmap,
        contentDescription = null,
        contentScale = ContentScale.Crop,
        modifier = Modifier
            .size(size)
            .clip(shape)
            .background(androidx.compose.ui.graphics.Color.White, shape),
    )
}

@Composable
internal fun bimi_preview(domain_name: String, png: String?) {
    val colors = AsterMaterial.colors
    val bitmap = remember(png) { decode_bimi_preview(png) } ?: return
    Column(modifier = Modifier.fillMaxWidth()) {
        Text(
            text = stringResource(R.string.domain_bimi_preview_title),
            color = colors.text_primary,
            fontSize = 14.sp,
            fontWeight = FontWeight.SemiBold,
        )
        v_gap(AsterSpacing.sm)
        Row(
            horizontalArrangement = Arrangement.spacedBy(AsterSpacing.md),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            bimi_logo_image(bitmap, 64.dp, CircleShape)
            bimi_logo_image(bitmap, 64.dp, SquircleShape(14.dp))
        }
        v_gap(AsterSpacing.md)
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .acrylic(colors, SquircleShape(14.dp), colors.bg_secondary)
                .padding(horizontal = AsterSpacing.md, vertical = AsterSpacing.sm),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            bimi_logo_image(bitmap, 40.dp, CircleShape)
            Spacer(Modifier.width(AsterSpacing.md))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = domain_name,
                    color = colors.text_primary,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    text = stringResource(R.string.domain_bimi_preview_inbox_subject),
                    color = colors.text_secondary,
                    fontSize = 13.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}

@Composable
internal fun bimi_record_card(record: BimiRecord) {
    val colors = AsterMaterial.colors
    val context = LocalContext.current
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .acrylic(colors, SquircleShape(14.dp), colors.bg_secondary)
            .padding(horizontal = AsterSpacing.md, vertical = AsterSpacing.sm),
    ) {
        dns_record_field(
            label = stringResource(R.string.domain_bimi_record_type),
            value = record.record_type,
            on_copy = { copy_dns_value(context, record.record_type, record.record_type) },
        )
        dns_record_field(
            label = stringResource(R.string.domain_bimi_record_host),
            value = record.host,
            on_copy = { copy_dns_value(context, record.record_type, record.host) },
        )
        dns_record_field(
            label = stringResource(R.string.domain_bimi_record_value),
            value = record.value,
            on_copy = { copy_dns_value(context, record.record_type, record.value) },
        )
    }
}

@Composable
internal fun bimi_requirement_row(title: String, detail: String, passed: Boolean?) {
    val colors = AsterMaterial.colors
    val tint = when (passed) {
        true -> colors.success
        false -> colors.warning
        null -> colors.text_tertiary
    }
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = AsterSpacing.xs),
        verticalAlignment = Alignment.Top,
    ) {
        Icon(
            imageVector = when (passed) {
                true -> TablerIcons.Check
                false -> TablerIcons.AlertTriangle
                null -> TablerIcons.Clock
            },
            contentDescription = null,
            tint = tint,
            modifier = Modifier.padding(top = 2.dp).size(16.dp),
        )
        Spacer(Modifier.width(AsterSpacing.sm))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                color = colors.text_primary,
                fontSize = 14.sp,
                fontWeight = FontWeight.Medium,
            )
            Text(
                text = detail,
                color = colors.text_tertiary,
                fontSize = 12.sp,
            )
        }
    }
}

@Composable
internal fun bimi_note(text: String, tint: androidx.compose.ui.graphics.Color? = null) {
    val colors = AsterMaterial.colors
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .acrylic(colors, SquircleShape(14.dp), colors.bg_secondary)
            .padding(horizontal = AsterSpacing.md, vertical = AsterSpacing.sm),
    ) {
        Text(text = text, color = tint ?: colors.text_secondary, fontSize = 13.sp)
    }
}

@Composable
internal fun bimi_bullet_list(title: String, lines: List<String>, tint: androidx.compose.ui.graphics.Color) {
    val colors = AsterMaterial.colors
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .acrylic(colors, SquircleShape(14.dp), colors.bg_secondary)
            .padding(horizontal = AsterSpacing.md, vertical = AsterSpacing.sm),
    ) {
        Text(text = title, color = colors.text_primary, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
        lines.forEach { line ->
            v_gap(4.dp)
            Row(verticalAlignment = Alignment.Top) {
                Box(
                    modifier = Modifier
                        .padding(top = 7.dp)
                        .size(5.dp)
                        .background(tint, CircleShape),
                )
                Spacer(Modifier.width(AsterSpacing.sm))
                Text(text = line, color = colors.text_secondary, fontSize = 13.sp)
            }
        }
    }
}
