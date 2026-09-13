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

package org.astermail.android.ui.auth

import compose.icons.TablerIcons
import compose.icons.tablericons.ChevronRight
import compose.icons.tablericons.FileImport
import compose.icons.tablericons.Lock

import androidx.compose.foundation.Image
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
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import org.astermail.android.R
import org.astermail.android.design.AsterMaterial
import org.astermail.android.design.AsterSpacing
import org.astermail.android.design.SquircleShape
import org.astermail.android.design.components.AsterGhostButton

private data class import_source(
    val icon_res: Int?,
    val label_res: Int,
    val description_res: Int,
)

private val import_sources = listOf(
    import_source(R.drawable.ic_brand_gmail, R.string.import_provider_gmail, R.string.import_provider_gmail_desc),
    import_source(R.drawable.ic_brand_outlook, R.string.import_provider_outlook, R.string.import_provider_outlook_desc),
    import_source(R.drawable.ic_brand_yahoo, R.string.import_provider_yahoo, R.string.import_provider_yahoo_desc),
    import_source(null, R.string.import_provider_mbox, R.string.import_provider_mbox_desc),
)

@Composable
fun RegisterImportMailStep(
    on_import: () -> Unit,
    on_skip: () -> Unit,
) {
    val colors = AsterMaterial.colors

    auth_centered_column {
        Image(
            painter = painterResource(R.drawable.aster_wordmark),
            contentDescription = null,
            modifier = Modifier.height(40.dp),
        )

        Spacer(Modifier.height(AsterSpacing.xl))

        Text(
            text = stringResource(R.string.import_mail_step_title),
            color = colors.text_primary,
            fontSize = 30.sp,
            lineHeight = 36.sp,
            fontWeight = FontWeight.ExtraBold,
            letterSpacing = (-0.3).sp,
            textAlign = TextAlign.Center,
        )
        Spacer(Modifier.height(AsterSpacing.md))
        Text(
            text = stringResource(R.string.import_mail_step_desc),
            color = colors.text_tertiary,
            fontSize = 14.sp,
            textAlign = TextAlign.Center,
        )

        Spacer(Modifier.height(AsterSpacing.xxl))

        Column(
            modifier = Modifier
                .fillMaxWidth()
                .clip(SquircleShape(16.dp))
                .background(colors.bg_secondary),
        ) {
            import_sources.forEachIndexed { index, source ->
                if (index > 0) {
                    HorizontalDivider(
                        modifier = Modifier.padding(start = 68.dp),
                        thickness = 1.dp,
                        color = colors.border_primary,
                    )
                }
                import_source_row(source = source, on_click = on_import)
            }
        }

        Spacer(Modifier.height(AsterSpacing.md))

        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Center,
            modifier = Modifier.fillMaxWidth(),
        ) {
            Icon(
                imageVector = TablerIcons.Lock,
                contentDescription = null,
                tint = colors.text_muted,
                modifier = Modifier.size(14.dp),
            )
            Spacer(Modifier.width(6.dp))
            Text(
                text = stringResource(R.string.import_mail_privacy_note),
                color = colors.text_muted,
                fontSize = 13.sp,
                textAlign = TextAlign.Center,
            )
        }

        Spacer(Modifier.height(AsterSpacing.xl))

        AsterGhostButton(
            label = stringResource(R.string.import_mail_skip),
            onClick = on_skip,
            modifier = Modifier.fillMaxWidth(),
        )
    }
}

@Composable
private fun import_source_row(
    source: import_source,
    on_click: () -> Unit,
) {
    val colors = AsterMaterial.colors
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = on_click)
            .padding(horizontal = AsterSpacing.lg, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(AsterSpacing.md),
    ) {
        Box(
            modifier = Modifier
                .size(40.dp)
                .background(colors.bg_card, SquircleShape(12.dp))
                .border(1.dp, colors.border_primary, SquircleShape(12.dp)),
            contentAlignment = Alignment.Center,
        ) {
            if (source.icon_res != null) {
                Image(
                    painter = painterResource(source.icon_res),
                    contentDescription = null,
                    modifier = Modifier.size(24.dp),
                )
            } else {
                Icon(
                    imageVector = TablerIcons.FileImport,
                    contentDescription = null,
                    tint = colors.text_primary,
                    modifier = Modifier.size(22.dp),
                )
            }
        }
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = stringResource(source.label_res),
                color = colors.text_primary,
                fontSize = 15.sp,
                lineHeight = 20.sp,
                fontWeight = FontWeight.SemiBold,
            )
            Text(
                text = stringResource(source.description_res),
                color = colors.text_tertiary,
                fontSize = 13.sp,
                lineHeight = 18.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        Icon(
            imageVector = TablerIcons.ChevronRight,
            contentDescription = null,
            tint = colors.text_muted,
            modifier = Modifier.size(18.dp),
        )
    }
}
