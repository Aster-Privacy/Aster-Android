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
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import compose.icons.TablerIcons
import compose.icons.tablericons.Check
import compose.icons.tablericons.ChevronRight
import org.astermail.android.R
import org.astermail.android.api.settings.CustomDomain
import org.astermail.android.api.settings.DomainHealth
import org.astermail.android.design.AsterMaterial
import org.astermail.android.design.AsterSpacing
import org.astermail.android.design.components.AsterCard
import org.astermail.android.settings.domains.domain_tone
import org.astermail.android.settings.domains.domain_tone_res

@Composable
internal fun domain_list_card(
    domains: List<CustomDomain>,
    health_for: (String) -> DomainHealth?,
    address_count_for: (String) -> Int,
    on_open: (CustomDomain) -> Unit,
) {
    val colors = AsterMaterial.colors
    AsterCard(modifier = Modifier.fillMaxWidth()) {
        domains.forEachIndexed { index, domain ->
            if (index > 0) {
                HorizontalDivider(
                    color = colors.border_secondary,
                    thickness = 0.5.dp,
                    modifier = Modifier.padding(start = AsterSpacing.lg),
                )
            }
            domain_list_row(
                domain = domain,
                health = health_for(domain.id),
                address_count = address_count_for(domain.domain_name),
                on_click = { on_open(domain) },
            )
        }
    }
}

@Composable
private fun domain_list_row(
    domain: CustomDomain,
    health: DomainHealth?,
    address_count: Int,
    on_click: () -> Unit,
) {
    val colors = AsterMaterial.colors
    val tone = domain_tone(domain, health)
    val subtitle = when {
        domain.is_shared && domain.shared_from != null -> stringResource(R.string.domain_shared_by, domain.shared_from!!)
        address_count > 0 -> androidx.compose.ui.res.pluralStringResource(
            R.plurals.domain_mgmt_address_count,
            address_count,
            address_count,
        )
        else -> null
    }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 60.dp)
            .clickable(role = Role.Button, onClick = on_click)
            .padding(start = AsterSpacing.lg, end = AsterSpacing.md, top = AsterSpacing.sm, bottom = AsterSpacing.sm),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = domain.domain_name,
                color = colors.text_primary,
                fontSize = 15.sp,
                fontWeight = FontWeight.Medium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            if (subtitle != null) {
                Text(
                    text = subtitle,
                    color = colors.text_tertiary,
                    fontSize = 12.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
        Spacer(Modifier.width(AsterSpacing.sm))
        tone_badge(text = stringResource(domain_tone_res(tone)), tone = domain_tone_color(tone))
        Spacer(Modifier.width(AsterSpacing.xs))
        Icon(
            imageVector = TablerIcons.ChevronRight,
            contentDescription = null,
            tint = colors.text_tertiary,
            modifier = Modifier.size(18.dp),
        )
    }
}

internal enum class DomainSetupStep { add, records, verify }

internal fun domain_setup_step(records_found: Int, records_total: Int): DomainSetupStep = when {
    records_total > 0 && records_found >= records_total -> DomainSetupStep.verify
    else -> DomainSetupStep.records
}

@Composable
internal fun domain_setup_steps(current: DomainSetupStep) {
    val labels = listOf(
        DomainSetupStep.add to R.string.domain_mgmt_step_add,
        DomainSetupStep.records to R.string.domain_mgmt_step_records,
        DomainSetupStep.verify to R.string.domain_mgmt_step_verify,
    )
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(AsterSpacing.sm),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        labels.forEachIndexed { index, (step, res) ->
            domain_step_marker(
                number = index + 1,
                label = stringResource(res),
                done = step.ordinal < current.ordinal,
                current = step == current,
                modifier = Modifier.weight(1f),
            )
        }
    }
}

@Composable
private fun domain_step_marker(
    number: Int,
    label: String,
    done: Boolean,
    current: Boolean,
    modifier: Modifier,
) {
    val colors = AsterMaterial.colors
    val fill = when {
        done -> colors.success
        current -> colors.accent_blue
        else -> colors.bg_tertiary
    }
    Row(modifier = modifier, verticalAlignment = Alignment.CenterVertically) {
        Box(
            modifier = Modifier.size(22.dp).background(fill, CircleShape),
            contentAlignment = Alignment.Center,
        ) {
            if (done) {
                Icon(
                    imageVector = TablerIcons.Check,
                    contentDescription = null,
                    tint = androidx.compose.ui.graphics.Color.White,
                    modifier = Modifier.size(14.dp),
                )
            } else {
                Text(
                    text = number.toString(),
                    color = if (current) androidx.compose.ui.graphics.Color.White else colors.text_tertiary,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.SemiBold,
                )
            }
        }
        Spacer(Modifier.width(6.dp))
        Text(
            text = label,
            color = if (current) colors.text_primary else colors.text_tertiary,
            fontSize = 12.sp,
            fontWeight = if (current) FontWeight.Medium else FontWeight.Normal,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}
