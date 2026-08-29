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

package org.astermail.android.ui.settings.detail

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
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
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import coil.compose.AsyncImagePainter
import coil.request.ImageRequest
import compose.icons.TablerIcons
import compose.icons.tablericons.Plus
import compose.icons.tablericons.World
import compose.icons.tablericons.X
import org.astermail.android.R
import org.astermail.android.design.AsterMaterial
import org.astermail.android.design.AsterSpacing
import org.astermail.android.design.components.AsterButton
import org.astermail.android.design.components.AsterSecondaryButton
import org.astermail.android.design.components.AsterTextField
import org.astermail.android.ui.mail.get_favicon_url
import org.astermail.android.ui.mail.get_root_domain

internal fun website_host(url: String): String {
    val without_scheme = url.substringAfter("://", url)
    return without_scheme.substringBefore('/').substringBefore('?').lowercase()
}

internal fun website_display_label(url: String): String {
    val host = website_host(url)
    return host.removePrefix("www.").ifBlank { url }
}

@Composable
private fun website_favicon(url: String, size: androidx.compose.ui.unit.Dp) {
    val colors = AsterMaterial.colors
    val host = website_host(url)
    val root_domain = if (host.isBlank()) "" else get_root_domain(host)
    var failed by remember(root_domain) { mutableStateOf(root_domain.isBlank()) }

    if (failed) {
        Icon(
            imageVector = TablerIcons.World,
            contentDescription = null,
            tint = colors.text_muted,
            modifier = Modifier.size(size),
        )
        return
    }

    val context = LocalContext.current
    AsyncImage(
        model = ImageRequest.Builder(context)
            .data(get_favicon_url(root_domain))
            .diskCacheKey("favicon:$root_domain")
            .crossfade(true)
            .build(),
        contentDescription = null,
        contentScale = ContentScale.Fit,
        modifier = Modifier.size(size).clip(RoundedCornerShape(3.dp)),
        onState = { state -> if (state is AsyncImagePainter.State.Error) failed = true },
    )
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun alias_websites_field(
    websites: List<String>,
    on_add: (String) -> Unit,
    on_remove: (String) -> Unit,
) {
    val colors = AsterMaterial.colors
    var adding by remember { mutableStateOf(false) }
    var draft by remember { mutableStateOf("") }

    Column(verticalArrangement = Arrangement.spacedBy(AsterSpacing.xs)) {
        Text(
            text = stringResource(R.string.alias_panel_websites),
            color = colors.text_tertiary,
            fontSize = 12.sp,
            fontWeight = FontWeight.Medium,
        )
        FlowRow(
            modifier = Modifier.fillMaxWidth().testTag("alias_websites_chips"),
            horizontalArrangement = Arrangement.spacedBy(AsterSpacing.xs),
            verticalArrangement = Arrangement.spacedBy(AsterSpacing.xs),
        ) {
            websites.forEach { url ->
                Row(
                    modifier = Modifier
                        .clip(RoundedCornerShape(999.dp))
                        .border(1.dp, colors.border_primary, RoundedCornerShape(999.dp))
                        .padding(start = 8.dp, end = 4.dp, top = 4.dp, bottom = 4.dp)
                        .testTag("alias_website_chip"),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    website_favicon(url, 14.dp)
                    Text(
                        text = website_display_label(url),
                        color = colors.text_primary,
                        fontSize = 13.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f, fill = false),
                    )
                    Box(
                        modifier = Modifier
                            .size(18.dp)
                            .clip(CircleShape)
                            .clickable { on_remove(url) },
                        contentAlignment = Alignment.Center,
                    ) {
                        Icon(
                            imageVector = TablerIcons.X,
                            contentDescription = stringResource(R.string.alias_website_remove),
                            tint = colors.text_muted,
                            modifier = Modifier.size(12.dp),
                        )
                    }
                }
            }
            if (!adding) {
                Row(
                    modifier = Modifier
                        .clip(RoundedCornerShape(999.dp))
                        .border(1.dp, colors.border_primary, RoundedCornerShape(999.dp))
                        .clickable { adding = true }
                        .padding(horizontal = 8.dp, vertical = 4.dp)
                        .testTag("alias_website_add"),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    Icon(
                        imageVector = TablerIcons.Plus,
                        contentDescription = null,
                        tint = colors.accent_blue,
                        modifier = Modifier.size(12.dp),
                    )
                    Text(
                        text = stringResource(R.string.alias_website_add),
                        color = colors.accent_blue,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Medium,
                    )
                }
            }
        }
        if (adding) {
            AsterTextField(
                value = draft,
                onValueChange = { if (it.length <= 200) draft = it },
                placeholder = stringResource(R.string.alias_website_input_placeholder),
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("alias_website_input"),
            )
            Row(
                modifier = Modifier.fillMaxWidth().padding(top = AsterSpacing.xs),
                horizontalArrangement = Arrangement.spacedBy(AsterSpacing.sm),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                AsterSecondaryButton(
                    label = stringResource(R.string.cancel),
                    onClick = {
                        draft = ""
                        adding = false
                    },
                    modifier = Modifier.weight(1f),
                )
                AsterButton(
                    label = stringResource(R.string.save),
                    onClick = {
                        val entry = draft.trim()
                        if (entry.isNotEmpty()) on_add(entry)
                        draft = ""
                        adding = false
                    },
                    enabled = draft.isNotBlank(),
                    modifier = Modifier.weight(1f).testTag("alias_website_save"),
                )
            }
        }
    }
}
