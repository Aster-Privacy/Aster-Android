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
import compose.icons.tablericons.ShoppingCart
import compose.icons.tablericons.World

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import org.astermail.android.R
import org.astermail.android.design.AsterMaterial
import org.astermail.android.design.AsterSpacing
import org.astermail.android.design.SquircleShape
import org.astermail.android.design.acrylic
import org.astermail.android.design.components.AsterGhostButton

@Composable
fun RegisterCustomDomainStep(
    on_own_domain: () -> Unit,
    on_new_domain: () -> Unit,
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
            text = stringResource(R.string.custom_domain_step_title),
            color = colors.text_primary,
            fontSize = 30.sp,
            lineHeight = 36.sp,
            fontWeight = FontWeight.ExtraBold,
            letterSpacing = (-0.3).sp,
            textAlign = TextAlign.Center,
        )
        Spacer(Modifier.height(AsterSpacing.md))
        Text(
            text = stringResource(R.string.custom_domain_step_desc),
            color = colors.text_tertiary,
            fontSize = 14.sp,
            textAlign = TextAlign.Center,
        )

        Spacer(Modifier.height(AsterSpacing.xxl))

        custom_domain_option_card(
            icon = TablerIcons.World,
            title = stringResource(R.string.custom_domain_own),
            description = stringResource(R.string.custom_domain_own_desc),
            onClick = on_own_domain,
        )

        Spacer(Modifier.height(AsterSpacing.md))

        custom_domain_option_card(
            icon = TablerIcons.ShoppingCart,
            title = stringResource(R.string.custom_domain_new),
            description = stringResource(R.string.custom_domain_new_desc),
            onClick = on_new_domain,
        )

        Spacer(Modifier.height(AsterSpacing.xxl))

        AsterGhostButton(
            label = stringResource(R.string.skip_for_now),
            onClick = on_skip,
            modifier = Modifier.fillMaxWidth(),
        )
    }
}

@Composable
private fun custom_domain_option_card(
    icon: ImageVector,
    title: String,
    description: String,
    onClick: () -> Unit,
) {
    val colors = AsterMaterial.colors
    val shape = SquircleShape(16.dp)
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(shape)
            .acrylic(colors, shape, colors.bg_card)
            .border(1.dp, colors.border_primary, shape)
            .clickable(onClick = onClick)
            .padding(AsterSpacing.lg),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier = Modifier
                .size(44.dp)
                .acrylic(colors, SquircleShape(12.dp), colors.bg_secondary)
                .border(1.dp, colors.border_secondary, SquircleShape(12.dp)),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = colors.text_primary,
                modifier = Modifier.size(22.dp),
            )
        }
        Spacer(Modifier.width(AsterSpacing.md))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                color = colors.text_primary,
                fontSize = 15.sp,
                fontWeight = FontWeight.Bold,
            )
            Spacer(Modifier.height(2.dp))
            Text(
                text = description,
                color = colors.text_tertiary,
                fontSize = 13.sp,
            )
        }
        Spacer(Modifier.width(AsterSpacing.sm))
        Icon(
            imageVector = TablerIcons.ChevronRight,
            contentDescription = null,
            tint = colors.text_muted,
            modifier = Modifier.size(20.dp),
        )
    }
}
