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

import compose.icons.TablerIcons
import compose.icons.tablericons.*

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.Image
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.sp
import org.astermail.android.BuildConfig
import org.astermail.android.R
import org.astermail.android.design.SquircleShape
import org.astermail.android.design.AsterMaterial
import org.astermail.android.design.AsterSpacing
import org.astermail.android.design.components.AsterCard
import org.astermail.android.design.components.AsterDivider
import org.astermail.android.ui.common.remember_copy_action

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun AboutScreen(
    on_back: () -> Unit,
    on_open: (id: String) -> Unit = {},
) {
    val colors = AsterMaterial.colors
    val context = LocalContext.current
    fun open_url(url: String) {
        org.astermail.android.ui.common.open_external_url(context, url)
    }
    detail_scaffold(title = stringResource(R.string.about), on_back = on_back) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(vertical = AsterSpacing.xl),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Image(
                painter = painterResource(R.drawable.aster_wordmark),
                contentDescription = stringResource(R.string.app_name),
                contentScale = ContentScale.Fit,
                modifier = Modifier
                    .fillMaxWidth(0.72f)
                    .heightIn(max = 76.dp)
                    .padding(horizontal = AsterSpacing.lg),
            )
            Spacer(Modifier.size(AsterSpacing.sm))
            val copy_action = remember_copy_action()
            val version_text = stringResource(R.string.version_format, BuildConfig.VERSION_NAME)
            val version_copied = stringResource(R.string.version_copied)
            val clip_label = stringResource(R.string.app_name)
            Text(
                text = version_text,
                color = colors.text_tertiary,
                fontSize = 13.sp,
                modifier = Modifier
                    .combinedClickable(
                        hapticFeedbackEnabled = false,
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null,
                        onClick = {},
                        onLongClick = {
                            copy_action(
                                clip_label,
                                BuildConfig.VERSION_NAME,
                                version_copied,
                            )
                        },
                    )
                    .padding(horizontal = AsterSpacing.sm, vertical = AsterSpacing.xs),
            )
            Spacer(Modifier.size(AsterSpacing.xs))
            Text(
                text = stringResource(R.string.hold_to_copy_version),
                color = colors.text_muted,
                fontSize = 11.sp,
            )
        }
        AsterCard(modifier = Modifier.fillMaxWidth()) {
            detail_row(title = stringResource(R.string.privacy_policy), icon = TablerIcons.FileText, on_click = { open_url("https://astermail.org/privacy") })
            AsterDivider()
            detail_row(title = stringResource(R.string.terms_of_service), icon = TablerIcons.Scale, on_click = { open_url("https://astermail.org/terms") })
            AsterDivider()
            detail_row(title = stringResource(R.string.source_on_github), subtitle = stringResource(R.string.licensed_agpl), icon = TablerIcons.ExternalLink, on_click = { open_url("https://github.com/Aster-Privacy/Aster-Android") })
        }
        v_gap(AsterSpacing.xxl)
    }
}
