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

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Dns
import androidx.compose.material.icons.rounded.VerifiedUser
import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import org.astermail.android.R
import org.astermail.android.api.domains.BimiView
import org.astermail.android.api.settings.CustomDomain
import org.astermail.android.design.AsterSpacing
import org.astermail.android.settings.BimiErrorKind
import org.astermail.android.settings.BimiUiState
import org.astermail.android.ui.settings.detail.v_gap

@Composable
internal fun bimi_requirements(view: BimiView?, domain: CustomDomain?) {
    bimi_section_title(stringResource(R.string.domain_bimi_requirements_title))
    v_gap(AsterSpacing.xs)
    val auth_passed = domain?.let { it.spf_verified && it.dkim_verified }
    bimi_check_row(
        passed = auth_passed,
        title = stringResource(R.string.domain_bimi_req_auth_title),
        detail = when (auth_passed) {
            true -> stringResource(R.string.domain_bimi_req_auth_ok)
            false -> stringResource(R.string.domain_bimi_req_auth_fail)
            null -> stringResource(R.string.domain_bimi_req_not_checked)
        },
    )
    val dmarc = view?.known_dmarc_status
    val dmarc_res = dmarc?.let { bimi_dmarc_res(it) }
    bimi_check_row(
        passed = dmarc?.let { it == "ready" },
        title = stringResource(R.string.domain_bimi_req_dmarc_title),
        detail = stringResource(dmarc_res ?: R.string.domain_bimi_req_not_checked),
    )
}

@Composable
internal fun bimi_publish_step(state: BimiUiState, domain: CustomDomain?) {
    val view = state.view
    val inactive = view != null && !view.domain_active

    bimi_stepper(current = 2)
    v_gap(AsterSpacing.lg)

    if (inactive) {
        bimi_alert(tone = BimiAlertTone.warning, message = stringResource(R.string.domain_bimi_error_domain_not_active))
        v_gap(AsterSpacing.lg)
    }

    bimi_requirements(view = view, domain = domain)

    if (view?.managed_dns == true) {
        v_gap(AsterSpacing.lg)
        bimi_note(icon = Icons.Rounded.Dns, text = stringResource(R.string.domain_bimi_managed_note))
    }

    v_gap(AsterSpacing.md)
    bimi_note(icon = Icons.Rounded.VerifiedUser, text = stringResource(R.string.domain_bimi_verified_mark_note))

    val error = state.error?.takeUnless { inactive && it == BimiErrorKind.domain_not_active }
    if (error != null) {
        v_gap(AsterSpacing.md)
        bimi_alert(tone = BimiAlertTone.error, message = bimi_error_text(error))
    }
}
