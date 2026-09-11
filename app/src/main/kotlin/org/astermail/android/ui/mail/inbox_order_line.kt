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
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import compose.icons.TablerIcons
import compose.icons.tablericons.Package
import org.astermail.android.R
import org.astermail.android.design.AsterMaterial
import org.astermail.android.mail.extraction.ShippingStatus
import org.astermail.android.mail.extraction.detect_shipping_status
import org.astermail.android.mail.extraction.extract_estimated_delivery
import org.astermail.android.mail.extraction.is_purchase_email
import org.astermail.android.mail.extraction.is_shipping_email

internal class inbox_order_hint(
    val is_shipping: Boolean,
    val status: ShippingStatus,
    val estimated_delivery: String?,
)

private const val inbox_order_cache_limit = 512

private class inbox_order_cache_entry(val subject: String, val preview: String, val hint: inbox_order_hint?)

private val inbox_order_cache = object : LinkedHashMap<String, inbox_order_cache_entry>(64, 0.75f, true) {
    override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, inbox_order_cache_entry>?): Boolean =
        size > inbox_order_cache_limit
}

internal fun detect_inbox_order_hint(subject: String, preview: String): inbox_order_hint? {
    if (subject.isBlank() && preview.isBlank()) return null
    val shipping = is_shipping_email(subject, preview)
    val purchase = !shipping && is_purchase_email(subject, preview)
    if (!shipping && !purchase) return null
    val status = if (shipping) detect_shipping_status(subject, preview) else ShippingStatus.unknown
    val estimated = if (shipping) extract_estimated_delivery(subject + "\n" + preview) else null
    return inbox_order_hint(shipping, status, estimated)
}

internal fun cached_inbox_order_hint(id: String, subject: String, preview: String): inbox_order_hint? {
    synchronized(inbox_order_cache) {
        val cached = inbox_order_cache[id]
        if (cached != null && cached.subject == subject && cached.preview == preview) return cached.hint
    }
    val hint = detect_inbox_order_hint(subject, preview)
    synchronized(inbox_order_cache) {
        inbox_order_cache[id] = inbox_order_cache_entry(subject, preview, hint)
    }
    return hint
}

@Composable
internal fun inbox_preview_or_order_line(
    email: Email,
    preview_text: String,
    preview_color: Color,
    modifier: Modifier = Modifier,
) {
    val hint = remember(email.id, email.subject, email.preview) {
        cached_inbox_order_hint(email.id, email.subject, email.preview)
    }
    if (hint == null) {
        Text(
            text = preview_text,
            style = MaterialTheme.typography.bodySmall,
            color = preview_color,
            fontSize = 14.sp,
            fontWeight = FontWeight.Normal,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = modifier,
        )
        return
    }
    val colors = AsterMaterial.colors
    val pill_label = stringResource(
        if (hint.is_shipping) R.string.order_card_track_package else R.string.order_card_view_order,
    )
    val status_text = when {
        !hint.is_shipping -> preview_text
        hint.status == ShippingStatus.delivered -> shipping_status_text(hint.status)
        hint.status == ShippingStatus.out_for_delivery -> shipping_status_text(hint.status)
        hint.estimated_delivery != null -> stringResource(R.string.inbox_arrives_on, hint.estimated_delivery)
        hint.status != ShippingStatus.unknown -> shipping_status_text(hint.status)
        else -> preview_text
    }
    Row(
        modifier = modifier.testTag("inbox_order_line"),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = pill_label,
            color = colors.accent_blue,
            fontSize = 12.sp,
            fontWeight = FontWeight.SemiBold,
            maxLines = 1,
            softWrap = false,
            modifier = Modifier
                .clip(RoundedCornerShape(999.dp))
                .background(colors.accent_blue.copy(alpha = 0.12f))
                .padding(horizontal = 8.dp, vertical = 2.dp),
        )
        Spacer(Modifier.width(6.dp))
        if (hint.is_shipping) {
            Icon(
                imageVector = TablerIcons.Package,
                contentDescription = null,
                tint = preview_color,
                modifier = Modifier.size(14.dp),
            )
            Spacer(Modifier.width(4.dp))
        }
        Text(
            text = status_text,
            style = MaterialTheme.typography.bodySmall,
            color = preview_color,
            fontSize = 14.sp,
            fontWeight = FontWeight.Normal,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f),
        )
    }
}
