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

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import compose.icons.TablerIcons
import compose.icons.tablericons.AlertTriangle
import compose.icons.tablericons.InfoCircle
import org.astermail.android.R
import org.astermail.android.design.AsterMaterial
import org.astermail.android.design.AsterShapes
import org.astermail.android.design.AsterSpacing
import org.astermail.android.design.field_surface_color
import org.astermail.android.design.tonal_surface_color
import org.astermail.android.security.SenderTrust
import org.astermail.android.security.SenderTrustMessage
import org.astermail.android.security.SenderTrustStore
import org.astermail.android.security.SenderWarningKind
import org.astermail.android.security.sender_trust_for

private const val slot_isolate_start = '\u2068'
private const val slot_isolate_end = '\u2069'

private fun is_direction_control(character: Char): Boolean =
    character in '\u202A'..'\u202E' ||
        character in '\u2066'..'\u2069' ||
        character == '\u200E' ||
        character == '\u200F' ||
        character == '\u061C'

internal fun isolated_slot_value(value: String): String =
    slot_isolate_start + value.filterNot { is_direction_control(it) } + slot_isolate_end

internal fun emphasized_slots(sentence: String, slots: List<String>, emphasis: SpanStyle): AnnotatedString {
    val ranges = ArrayList<IntRange>()
    for (slot in slots.distinct()) {
        if (slot.length <= 2) continue
        var from = sentence.indexOf(slot)
        while (from >= 0) {
            ranges.add(from until from + slot.length)
            from = sentence.indexOf(slot, from + slot.length)
        }
    }
    ranges.sortBy { it.first }
    return buildAnnotatedString {
        var cursor = 0
        for (range in ranges) {
            if (range.first < cursor) continue
            append(sentence.substring(cursor, range.first))
            withStyle(emphasis) { append(sentence.substring(range.first, range.last + 1)) }
            cursor = range.last + 1
        }
        append(sentence.substring(cursor))
    }
}

@Composable
internal fun slot_sentence(resource: Int, vararg values: String): AnnotatedString {
    val colors = AsterMaterial.colors
    val slots = values.map { isolated_slot_value(it) }
    val sentence = stringResource(resource, *slots.toTypedArray())
    return remember(sentence, slots, colors.text_primary) {
        emphasized_slots(
            sentence,
            slots,
            SpanStyle(fontWeight = FontWeight.SemiBold, color = colors.text_primary),
        )
    }
}

@Composable
fun remember_sender_trust(message: SenderTrustMessage?): SenderTrust? {
    val context by SenderTrustStore.context.collectAsStateWithLifecycle()
    return remember(message, context) { sender_trust_for(message, context) }
}

@Composable
internal fun sender_trust_banner(trust: SenderTrust?, modifier: Modifier = Modifier) {
    if (trust == null) return
    val warning = trust.warning
    val contact_notice = trust.contact_notice
    val reply_to = trust.reply_to
    if (warning == null && contact_notice == null && reply_to == null) return
    val colors = AsterMaterial.colors
    Column(modifier = modifier.fillMaxWidth()) {
        if (warning != null) {
            val body = when (warning.kind) {
                SenderWarningKind.address_in_name ->
                    slot_sentence(R.string.sender_warning_address_in_name, warning.claimed, trust.address)
                SenderWarningKind.lookalike_name ->
                    slot_sentence(R.string.sender_warning_lookalike_name, warning.claimed, trust.address)
                SenderWarningKind.brand_name ->
                    slot_sentence(R.string.sender_warning_brand_name, warning.claimed, trust.address)
                SenderWarningKind.home_brand_name ->
                    slot_sentence(R.string.sender_warning_home_brand_name, trust.address)
                SenderWarningKind.lookalike_domain ->
                    slot_sentence(R.string.sender_warning_lookalike_domain, trust.domain, warning.claimed)
            }
            sender_trust_note(
                surface = tonal_surface_color(colors, colors.warning),
                icon_tint = colors.warning,
                is_warning = true,
                title = stringResource(R.string.sender_warning_title),
                body = body,
                tag = "sender_trust_warning",
            )
        }
        if (contact_notice != null) {
            sender_trust_note(
                surface = field_surface_color(colors),
                icon_tint = colors.text_muted,
                is_warning = false,
                title = null,
                body = slot_sentence(R.string.sender_contact_notice, contact_notice),
                tag = "sender_trust_contact_notice",
            )
        }
        if (reply_to != null) {
            sender_trust_note(
                surface = field_surface_color(colors),
                icon_tint = colors.text_muted,
                is_warning = false,
                title = null,
                body = slot_sentence(R.string.sender_reply_to_notice, reply_to),
                tag = "sender_trust_reply_to",
            )
        }
    }
}

@Composable
private fun sender_trust_note(
    surface: Color,
    icon_tint: Color,
    is_warning: Boolean,
    title: String?,
    body: AnnotatedString,
    tag: String,
) {
    val colors = AsterMaterial.colors
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = AsterSpacing.md)
            .padding(bottom = AsterSpacing.sm)
            .clip(AsterShapes.island)
            .background(surface)
            .padding(AsterSpacing.md)
            .testTag(tag),
        verticalAlignment = Alignment.Top,
    ) {
        Icon(
            imageVector = if (is_warning) TablerIcons.AlertTriangle else TablerIcons.InfoCircle,
            contentDescription = null,
            tint = icon_tint,
            modifier = Modifier.size(18.dp),
        )
        Spacer(Modifier.width(AsterSpacing.sm))
        Column {
            if (title != null) {
                Text(
                    text = title,
                    color = colors.text_primary,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.SemiBold,
                )
                Spacer(Modifier.height(2.dp))
            }
            Text(
                text = body,
                color = colors.text_secondary,
                fontSize = 12.sp,
                lineHeight = 17.sp,
            )
        }
    }
}
