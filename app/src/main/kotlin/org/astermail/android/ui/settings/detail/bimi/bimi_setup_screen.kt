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

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import org.astermail.android.R
import org.astermail.android.design.AsterMaterial
import org.astermail.android.design.AsterSpacing
import org.astermail.android.settings.BimiStep
import org.astermail.android.settings.BimiViewModel
import org.astermail.android.settings.shared_settings_view_model
import org.astermail.android.ui.settings.detail.detail_scaffold
import org.astermail.android.ui.settings.detail.error_banner
import org.astermail.android.ui.settings.detail.load_failed_card
import org.astermail.android.ui.settings.detail.v_gap

private const val bimi_step_total = 2

@Composable
fun bimi_setup_screen(domain_id: String, on_back: () -> Unit) {
    val vm: BimiViewModel = hiltViewModel()
    val state by vm.state.collectAsStateWithLifecycle()
    val settings_vm = shared_settings_view_model()
    val settings_state by settings_vm.state.collectAsStateWithLifecycle()
    val domain = settings_state.domains.firstOrNull { it.id == domain_id }

    LaunchedEffect(domain_id) { vm.load(domain_id) }
    LaunchedEffect(domain_id, domain == null) {
        if (domain == null && !settings_state.domains_loading) settings_vm.load_domains()
    }

    val lifecycle_owner = LocalLifecycleOwner.current
    var is_started by remember {
        mutableStateOf(lifecycle_owner.lifecycle.currentState.isAtLeast(Lifecycle.State.STARTED))
    }
    DisposableEffect(lifecycle_owner) {
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_START -> is_started = true
                Lifecycle.Event.ON_STOP -> is_started = false
                else -> Unit
            }
        }
        lifecycle_owner.lifecycle.addObserver(observer)
        onDispose { lifecycle_owner.lifecycle.removeObserver(observer) }
    }
    val publish_visible = is_started && state.step == BimiStep.publish
    DisposableEffect(publish_visible) {
        vm.set_publish_visible(publish_visible)
        onDispose { vm.set_publish_visible(false) }
    }

    val domain_name = domain?.domain_name.orEmpty()

    detail_scaffold(title = stringResource(R.string.domain_bimi_title), on_back = on_back) {
        if (domain_name.isNotEmpty()) {
            Text(
                text = domain_name,
                color = AsterMaterial.colors.text_tertiary,
                fontSize = 13.sp,
            )
            v_gap(AsterSpacing.md)
        }
        if (state.load_failed) {
            load_failed_card(message = null, on_retry = vm::retry_load)
            return@detail_scaffold
        }
        if (state.step == BimiStep.loading) {
            Box(
                modifier = Modifier.fillMaxWidth().padding(vertical = AsterSpacing.xxxl),
                contentAlignment = Alignment.Center,
            ) {
                CircularProgressIndicator(color = AsterMaterial.colors.accent_blue)
            }
            return@detail_scaffold
        }
        state.error?.let { kind ->
            error_banner(bimi_error_text(kind))
            v_gap(AsterSpacing.md)
        }
        if (state.step != BimiStep.manage && !state.replacing) {
            bimi_step_indicator(
                current = if (state.step == BimiStep.logo) 1 else 2,
                name = stringResource(
                    if (state.step == BimiStep.logo) R.string.domain_bimi_step_logo else R.string.domain_bimi_step_publish,
                ),
            )
            v_gap(AsterSpacing.md)
        }
        when (state.step) {
            BimiStep.logo -> bimi_logo_step(state = state, domain_name = domain_name, vm = vm)
            BimiStep.publish -> bimi_publish_step(state = state, domain = domain, vm = vm)
            BimiStep.manage -> bimi_manage_view(state = state, domain = domain, domain_name = domain_name, vm = vm)
            BimiStep.loading -> Unit
        }
        v_gap(AsterSpacing.xxl)
    }
}

@Composable
private fun bimi_step_indicator(current: Int, name: String) {
    val colors = AsterMaterial.colors
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(
            text = stringResource(R.string.domain_bimi_step_of, current, bimi_step_total),
            color = colors.text_tertiary,
            fontSize = 12.sp,
        )
        Spacer(Modifier.width(AsterSpacing.sm))
        Text(
            text = name,
            color = colors.text_primary,
            fontSize = 16.sp,
            fontWeight = FontWeight.SemiBold,
        )
    }
}
