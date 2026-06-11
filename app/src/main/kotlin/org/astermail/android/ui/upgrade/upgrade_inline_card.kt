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

package org.astermail.android.ui.upgrade

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.AutoAwesome
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.runtime.getValue
import androidx.compose.material3.Text
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import org.astermail.android.R
import org.astermail.android.billing.PlanLimitsViewModel
import org.astermail.android.design.SquircleShape
import org.astermail.android.design.AsterMaterial
import org.astermail.android.design.AsterRadius
import org.astermail.android.design.AsterSpacing
import org.astermail.android.design.components.AsterButton

@Composable
fun UpgradeInlineCard(
    limit_key: UpgradeLimitKey,
    resource_label: String?,
    modifier: Modifier = Modifier,
) {
    val vm: PlanLimitsViewModel = hiltViewModel()
    val plan_state by vm.state.collectAsStateWithLifecycle()
    val limits = plan_state.limits?.limits ?: return
    val field = when (limit_key) {
        UpgradeLimitKey.MaxEmailAliases -> "max_email_aliases"
        UpgradeLimitKey.MaxCustomDomains -> "max_custom_domains"
        UpgradeLimitKey.MaxContacts -> "max_contacts"
        UpgradeLimitKey.MaxEmailTemplates -> "max_email_templates"
        UpgradeLimitKey.MaxHtmlSignatures -> "max_html_signatures"
        UpgradeLimitKey.MaxCustomFilters -> "max_custom_filters"
        UpgradeLimitKey.Generic -> return
    }
    val info = limits[field] ?: return
    if (!info.is_at_limit) return

    val colors = AsterMaterial.colors
    Row(
        modifier = modifier
            .fillMaxWidth()
            .clip(SquircleShape(18.dp))
            .background(colors.bg_tertiary)
            .border(1.dp, colors.border_primary, SquircleShape(18.dp))
            .padding(AsterSpacing.md),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            imageVector = Icons.Outlined.AutoAwesome,
            contentDescription = null,
            tint = colors.accent_blue,
            modifier = Modifier.size(24.dp),
        )
        Spacer(Modifier.width(AsterSpacing.md))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = stringResource(R.string.upgrade_inline_card_title),
                color = colors.text_primary,
                fontSize = 14.sp,
                fontWeight = FontWeight.SemiBold,
            )
            Spacer(Modifier.height(2.dp))
            Text(
                text = stringResource(R.string.upgrade_inline_card_description),
                color = colors.text_muted,
                fontSize = 12.sp,
            )
        }
        Spacer(Modifier.width(AsterSpacing.sm))
        Box(modifier = Modifier.width(120.dp)) {
            AsterButton(
                label = stringResource(R.string.upgrade_view_plans),
                onClick = { UpgradeStore.show_plan_limit_for(limit_key, resource_label) },
            )
        }
    }
}
