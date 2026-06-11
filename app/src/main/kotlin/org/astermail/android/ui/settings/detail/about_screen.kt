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

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Description
import androidx.compose.material.icons.outlined.Gavel
import androidx.compose.material.icons.outlined.Mail
import androidx.compose.material.icons.outlined.OpenInNew
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.sp
import org.astermail.android.BuildConfig
import org.astermail.android.R
import org.astermail.android.design.SquircleShape
import org.astermail.android.design.AsterMaterial
import org.astermail.android.design.AsterSpacing
import org.astermail.android.design.components.AsterCard
import org.astermail.android.design.components.AsterDivider

@Composable
fun AboutScreen(
    on_back: () -> Unit,
    on_open: (id: String) -> Unit = {},
) {
    val colors = AsterMaterial.colors
    val context = LocalContext.current
    fun open_url(url: String) {
        context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)))
    }
    detail_scaffold(title = stringResource(R.string.about), on_back = on_back) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(vertical = AsterSpacing.xl),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Box(
                modifier = Modifier
                    .size(72.dp)
                    .background(colors.accent_blue, SquircleShape(18.dp)),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    imageVector = Icons.Outlined.Mail,
                    contentDescription = stringResource(R.string.aster_logo),
                    tint = androidx.compose.ui.graphics.Color.White,
                    modifier = Modifier.size(40.dp),
                )
            }
            Spacer(Modifier.size(AsterSpacing.md))
            Text(
                text = stringResource(R.string.app_name),
                color = colors.text_primary,
                fontSize = 22.sp,
                fontWeight = FontWeight.Bold,
                textAlign = TextAlign.Center,
            )
            Text(
                text = stringResource(R.string.version_format, BuildConfig.VERSION_NAME),
                color = colors.text_tertiary,
                fontSize = 13.sp,
            )
            Spacer(Modifier.size(AsterSpacing.md))
            Text(
                text = stringResource(R.string.built_in_canada),
                color = colors.text_secondary,
                fontSize = 14.sp,
                textAlign = TextAlign.Center,
            )
        }
        AsterCard(modifier = Modifier.fillMaxWidth()) {
            detail_row(title = stringResource(R.string.privacy_policy), icon = Icons.Outlined.Description, on_click = { open_url("https://astermail.org/privacy") })
            AsterDivider()
            detail_row(title = stringResource(R.string.terms_of_service), icon = Icons.Outlined.Gavel, on_click = { open_url("https://astermail.org/terms") })
            AsterDivider()
            detail_row(title = stringResource(R.string.source_on_github), subtitle = stringResource(R.string.licensed_agpl), icon = Icons.Outlined.OpenInNew, on_click = { open_url("https://github.com/AsterCommunications") })
        }
        v_gap(AsterSpacing.xxl)
    }
}
