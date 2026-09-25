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

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
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
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlinx.coroutines.delay
import org.astermail.android.R
import org.astermail.android.design.AsterMaterial
import org.astermail.android.design.AsterSpacing
import org.astermail.android.design.components.AsterButton
import org.astermail.android.design.components.AsterSecondaryButton
import org.astermail.android.settings.BimiStep
import org.astermail.android.settings.BimiUiState
import org.astermail.android.settings.BimiViewModel
import org.astermail.android.settings.shared_settings_view_model
import org.astermail.android.ui.settings.detail.detail_scaffold
import org.astermail.android.ui.settings.detail.load_failed_card
import org.astermail.android.ui.settings.detail.v_gap

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
    DisposableEffect(is_started) {
        vm.set_screen_visible(is_started)
        onDispose { vm.set_screen_visible(false) }
    }

    val is_loading = state.step == BimiStep.loading && !state.load_failed
    var show_spinner by remember { mutableStateOf(false) }
    LaunchedEffect(is_loading) {
        show_spinner = false
        if (is_loading) {
            delay(250)
            show_spinner = true
        }
    }

    val scroll_state = rememberScrollState()
    LaunchedEffect(state.step) { scroll_state.scrollTo(0) }

    val domain_name = domain?.domain_name.orEmpty()
    val colors = AsterMaterial.colors

    detail_scaffold(
        title = stringResource(R.string.domain_bimi_title),
        on_back = on_back,
        scrollable = false,
    ) {
        Column(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
                .verticalScroll(scroll_state)
                .padding(horizontal = AsterSpacing.lg, vertical = AsterSpacing.lg),
        ) {
            if (domain_name.isNotEmpty() && state.step != BimiStep.manage) {
                Text(
                    text = domain_name,
                    color = colors.text_tertiary,
                    fontSize = 13.sp,
                    lineHeight = 18.sp,
                )
                v_gap(AsterSpacing.lg)
            }
            when {
                state.load_failed -> load_failed_card(message = null, on_retry = vm::retry_load)
                is_loading -> Box(
                    modifier = Modifier.fillMaxWidth().heightIn(min = 240.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    if (show_spinner) CircularProgressIndicator(color = colors.accent_blue)
                }
                state.step == BimiStep.logo -> bimi_logo_step(state = state, domain_name = domain_name, vm = vm)
                state.step == BimiStep.publish -> bimi_publish_step(state = state, domain = domain)
                state.step == BimiStep.manage -> bimi_manage_view(state = state, domain_name = domain_name, vm = vm)
            }
            v_gap(AsterSpacing.lg)
        }
        if (!state.load_failed && state.step != BimiStep.loading) {
            Box(Modifier.fillMaxWidth().height(1.dp).background(colors.border_secondary))
            bimi_footer(state = state, vm = vm, on_back = on_back)
        }
    }
}

@Composable
private fun bimi_footer(state: BimiUiState, vm: BimiViewModel, on_back: () -> Unit) {
    val view = state.view
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = AsterSpacing.lg, vertical = AsterSpacing.md),
        verticalArrangement = Arrangement.spacedBy(AsterSpacing.sm),
    ) {
        when (state.step) {
            BimiStep.logo -> {
                val has_logo = bimi_has_valid_logo(state)
                Row(horizontalArrangement = Arrangement.spacedBy(AsterSpacing.sm)) {
                    if (state.replacing) {
                        AsterSecondaryButton(
                            label = stringResource(R.string.domain_bimi_back),
                            onClick = vm::go_to_manage,
                            modifier = Modifier.weight(1f),
                            enabled = !state.busy,
                        )
                        AsterButton(
                            label = stringResource(R.string.domain_bimi_done),
                            onClick = vm::go_to_manage,
                            modifier = Modifier.weight(1f),
                            enabled = !state.busy && has_logo,
                        )
                    } else {
                        AsterSecondaryButton(
                            label = stringResource(R.string.cancel),
                            onClick = on_back,
                            modifier = Modifier.weight(1f),
                        )
                        AsterButton(
                            label = stringResource(R.string.domain_bimi_continue),
                            onClick = vm::go_to_publish,
                            modifier = Modifier.weight(1f),
                            enabled = !state.busy && has_logo,
                        )
                    }
                }
            }
            BimiStep.publish -> {
                Row(horizontalArrangement = Arrangement.spacedBy(AsterSpacing.sm)) {
                    AsterSecondaryButton(
                        label = stringResource(R.string.domain_bimi_back),
                        onClick = vm::go_to_logo,
                        modifier = Modifier.weight(1f),
                        enabled = !state.busy,
                    )
                    AsterButton(
                        label = stringResource(
                            if (state.publishing) R.string.domain_bimi_publishing else R.string.domain_bimi_publish,
                        ),
                        onClick = vm::publish,
                        modifier = Modifier.weight(1f),
                        enabled = !state.busy && view?.domain_active == true && view.preview_png != null,
                        is_loading = state.publishing,
                    )
                }
            }
            BimiStep.manage -> {
                Row(horizontalArrangement = Arrangement.spacedBy(AsterSpacing.sm)) {
                    AsterSecondaryButton(
                        label = stringResource(R.string.domain_bimi_replace_logo),
                        onClick = vm::replace_logo,
                        modifier = Modifier.weight(1f),
                        enabled = !state.busy,
                    )
                    AsterSecondaryButton(
                        label = stringResource(
                            if (state.checking) R.string.domain_bimi_checking else R.string.domain_bimi_check_again,
                        ),
                        onClick = vm::check,
                        modifier = Modifier.weight(1f),
                        enabled = !state.busy,
                        is_loading = state.checking,
                    )
                }
                AsterButton(
                    label = stringResource(R.string.domain_bimi_done),
                    onClick = on_back,
                )
            }
            BimiStep.loading -> Unit
        }
    }
}
