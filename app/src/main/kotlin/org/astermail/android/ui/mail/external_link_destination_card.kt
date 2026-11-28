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

package org.astermail.android.ui.mail

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.ContentCopy
import androidx.compose.material.icons.rounded.MailOutline
import androidx.compose.material.icons.rounded.Public
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
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay
import org.astermail.android.R
import org.astermail.android.design.AsterMaterial
import org.astermail.android.design.AsterShapes
import org.astermail.android.design.AsterSpacing
import org.astermail.android.design.components.AsterIconButton
import org.astermail.android.design.island_surface_color
import java.net.URI
import java.util.Locale

private const val copied_duration_ms = 1600L

internal data class ExternalLinkDestination(
    val heading: String,
    val address: String?,
    val is_mail: Boolean,
)

internal fun describe_external_link(link: String): ExternalLinkDestination {
    val trimmed = link.trim()
    val scheme = trimmed.substringBefore(':', "").lowercase(Locale.ROOT)
    if (scheme == "mailto" || scheme == "tel") {
        val target = trimmed.substringAfter(':').substringBefore('?')
        return ExternalLinkDestination(
            heading = target.ifBlank { trimmed },
            address = null,
            is_mail = scheme == "mailto",
        )
    }
    val host = runCatching { URI(trimmed).host }.getOrNull()
        ?.lowercase(Locale.ROOT)
        ?.removePrefix("www.")
        ?.takeIf { it.isNotBlank() }
    return ExternalLinkDestination(
        heading = host ?: trimmed,
        address = if (host != null) trimmed else null,
        is_mail = false,
    )
}

@Composable
internal fun external_link_destination_card(
    link: String,
    on_copy: () -> Boolean,
) {
    val colors = AsterMaterial.colors
    val shape = AsterShapes.island
    val destination = remember(link) { describe_external_link(link) }
    var copied by remember { mutableStateOf(false) }
    LaunchedEffect(copied) {
        if (copied) {
            delay(copied_duration_ms)
            copied = false
        }
    }
    val copy_label = stringResource(if (destination.is_mail) R.string.copy_address else R.string.copy_link)
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(shape)
            .background(island_surface_color(colors), shape)
            .padding(start = AsterSpacing.lg, end = AsterSpacing.xs, top = 14.dp, bottom = 14.dp),
        verticalAlignment = Alignment.Top,
    ) {
        Box(modifier = Modifier.size(20.dp), contentAlignment = Alignment.Center) {
            Icon(
                imageVector = if (destination.is_mail) Icons.Rounded.MailOutline else Icons.Rounded.Public,
                contentDescription = null,
                tint = colors.text_tertiary,
                modifier = Modifier.size(16.dp),
            )
        }
        Spacer(Modifier.width(AsterSpacing.md))
        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            Text(
                text = destination.heading,
                color = colors.text_primary,
                fontSize = 16.sp,
                lineHeight = 20.sp,
                fontWeight = FontWeight.SemiBold,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            if (destination.address != null) {
                Text(
                    text = destination.address,
                    color = colors.text_tertiary,
                    fontSize = 13.sp,
                    lineHeight = 18.sp,
                    maxLines = 3,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
        Spacer(Modifier.width(AsterSpacing.xs))
        Box(modifier = Modifier.height(20.dp).width(48.dp), contentAlignment = Alignment.Center) {
            AsterIconButton(
                icon = if (copied) Icons.Rounded.Check else Icons.Rounded.ContentCopy,
                content_description = if (copied) stringResource(R.string.copied) else copy_label,
                onClick = { if (on_copy()) copied = true },
                modifier = Modifier.requiredSize(48.dp),
                tint = colors.text_tertiary,
                icon_size = 18,
            )
        }
    }
}
