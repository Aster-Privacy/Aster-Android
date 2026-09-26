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

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import compose.icons.TablerIcons
import compose.icons.tablericons.Discount2
import org.astermail.android.R
import org.astermail.android.billing.BillingUiState
import org.astermail.android.billing.billing_view_model
import org.astermail.android.billing.play_special_offer_for
import org.astermail.android.billing.remember_play_install
import org.astermail.android.design.components.AsterIconButton
import org.astermail.android.design.AsterMaterial

internal fun special_offer_play_ready(billing_state: BillingUiState): Boolean =
    billing_state.play_enabled &&
        billing_state.play_special_offer_eligible &&
        play_special_offer_for(billing_state.play_offers, billing_state.play_special_offer) != null

@Composable
fun special_offer_entry_visible(offer_state: SpecialOfferState): Boolean {
    if (!offer_state.available) return false
    if (!remember_play_install()) return true
    val billing_state by billing_view_model().state.collectAsStateWithLifecycle()
    return special_offer_play_ready(billing_state)
}

@Composable
fun special_offer_header_button() {
    val offer_vm = special_offer_view_model()
    val offer_state by offer_vm.state.collectAsStateWithLifecycle()
    if (!special_offer_entry_visible(offer_state)) return
    AsterIconButton(
        icon = TablerIcons.Discount2,
        content_description = stringResource(R.string.special_offer_entry),
        onClick = { offer_vm.reopen() },
        tint = AsterMaterial.colors.accent_blue,
        modifier = Modifier.testTag("special_offer_header"),
    )
}
