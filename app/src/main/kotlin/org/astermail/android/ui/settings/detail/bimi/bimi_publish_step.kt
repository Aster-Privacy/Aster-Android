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

package org.astermail.android.ui.settings.detail.bimi

import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import org.astermail.android.R
import org.astermail.android.api.domains.BimiState
import org.astermail.android.api.domains.BimiView
import org.astermail.android.api.settings.CustomDomain
import org.astermail.android.design.AsterMaterial
import org.astermail.android.design.AsterSpacing
import org.astermail.android.design.components.AsterButton
import org.astermail.android.design.components.AsterGhostButton
import org.astermail.android.design.components.AsterSecondaryButton
import org.astermail.android.settings.BimiUiState
import org.astermail.android.settings.BimiViewModel
import org.astermail.android.ui.settings.detail.relative_time_label
import org.astermail.android.ui.settings.detail.section_label
import org.astermail.android.ui.settings.detail.v_gap

@Composable
internal fun bimi_requirements(view: BimiView?, domain: CustomDomain?) {
    section_label(stringResource(R.string.domain_bimi_requirements_title))
    v_gap(AsterSpacing.xs)
    val auth_passed = domain?.let { it.spf_verified && it.dkim_verified }
    bimi_requirement_row(
        title = stringResource(R.string.domain_bimi_req_auth_title),
        detail = when (auth_passed) {
            true -> stringResource(R.string.domain_bimi_req_auth_ok)
            false -> stringResource(R.string.domain_bimi_req_auth_fail)
            null -> stringResource(R.string.domain_bimi_req_not_checked)
        },
        passed = auth_passed,
    )
    val dmarc = view?.known_dmarc_status
    val dmarc_res = dmarc?.let { bimi_dmarc_res(it) }
    bimi_requirement_row(
        title = stringResource(R.string.domain_bimi_req_dmarc_title),
        detail = stringResource(dmarc_res ?: R.string.domain_bimi_req_not_checked),
        passed = dmarc?.let { it == "ready" },
    )
}

@Composable
internal fun bimi_state_line(state: BimiState, last_checked_at: String?) {
    val colors = AsterMaterial.colors
    Row(verticalAlignment = Alignment.CenterVertically) {
        bimi_state_chip(state)
        if (!last_checked_at.isNullOrBlank()) {
            Spacer(Modifier.width(AsterSpacing.sm))
            Text(
                text = stringResource(R.string.domain_bimi_last_checked, relative_time_label(last_checked_at)),
                color = colors.text_tertiary,
                fontSize = 12.sp,
            )
        }
    }
}

@Composable
internal fun bimi_dns_status(state: BimiUiState, show_check_button: Boolean, vm: BimiViewModel) {
    val colors = AsterMaterial.colors
    val view = state.view ?: return
    if (view.managed_dns) {
        bimi_note(stringResource(R.string.domain_bimi_managed_note))
        if (state.bimi_state == BimiState.pending) {
            v_gap(AsterSpacing.sm)
            Text(
                text = stringResource(R.string.domain_bimi_row_pending_managed),
                color = colors.text_tertiary,
                fontSize = 12.sp,
            )
        }
        return
    }
    val record = view.record ?: return
    Text(
        text = stringResource(R.string.domain_bimi_record_title),
        color = colors.text_primary,
        fontSize = 14.sp,
        fontWeight = FontWeight.SemiBold,
    )
    v_gap(AsterSpacing.sm)
    bimi_record_card(record)
    view.known_record_status?.let { bimi_record_status_res(it) }?.let { res ->
        v_gap(AsterSpacing.sm)
        Text(
            text = stringResource(res),
            color = if (view.known_record_status == "published") colors.success else colors.text_tertiary,
            fontSize = 12.sp,
        )
    }
    if (show_check_button) {
        v_gap(AsterSpacing.md)
        AsterSecondaryButton(
            label = stringResource(if (state.checking) R.string.domain_bimi_checking else R.string.domain_bimi_check_record),
            onClick = vm::check,
            enabled = !state.checking,
            is_loading = state.checking,
        )
    }
}

@Composable
internal fun bimi_publish_step(state: BimiUiState, domain: CustomDomain?, vm: BimiViewModel) {
    val colors = AsterMaterial.colors
    bimi_requirements(view = state.view, domain = domain)
    v_gap(AsterSpacing.lg)

    if (!state.is_published) {
        AsterButton(
            label = stringResource(if (state.publishing) R.string.domain_bimi_publishing else R.string.domain_bimi_publish),
            onClick = vm::publish,
            enabled = !state.publishing,
            is_loading = state.publishing,
        )
        v_gap(AsterSpacing.sm)
        AsterGhostButton(
            label = stringResource(R.string.domain_bimi_back),
            onClick = vm::go_to_logo,
            enabled = !state.publishing,
        )
        return
    }

    bimi_state_line(state.bimi_state, state.view?.last_checked_at)
    v_gap(AsterSpacing.md)
    bimi_dns_status(state = state, show_check_button = true, vm = vm)
    v_gap(AsterSpacing.md)
    Text(
        text = stringResource(R.string.domain_bimi_verified_mark_note),
        color = colors.text_tertiary,
        fontSize = 12.sp,
    )
    v_gap(AsterSpacing.lg)
    AsterButton(
        label = stringResource(R.string.domain_bimi_done),
        onClick = vm::go_to_manage,
    )
}
