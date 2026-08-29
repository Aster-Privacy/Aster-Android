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
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull
import org.astermail.android.R
import org.astermail.android.api.settings.DnsRecord
import org.astermail.android.billing.PlanLimitsViewModel
import org.astermail.android.design.AsterMaterial
import org.astermail.android.design.AsterSpacing
import org.astermail.android.design.SquircleShape
import org.astermail.android.design.components.AsterCard
import org.astermail.android.design.components.shimmer_brush
import org.astermail.android.settings.DomainPurchaseViewModel
import org.astermail.android.settings.SettingsViewModel
import org.astermail.android.settings.shared_settings_view_model

private enum class domains_load_phase { loading, loaded, failed }

private const val domains_load_timeout_ms = 25_000L

@Composable
fun DomainsScreen(
    on_back: () -> Unit,
    on_open_buy_domain: () -> Unit = {},
    on_open_domain_order: (String) -> Unit = {},
) {
    val vm: SettingsViewModel = shared_settings_view_model()
    val state by vm.state.collectAsStateWithLifecycle()
    val plan_vm: PlanLimitsViewModel = hiltViewModel()
    val plan_state by plan_vm.state.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val catch_all_locked = plan_vm.is_feature_locked("has_catch_all") && !plan_state.is_loading

    var show_add_domain by remember { mutableStateOf(false) }
    var expanded_domain_id by remember { mutableStateOf<String?>(null) }
    var domain_dns by remember { mutableStateOf<Map<String, List<DnsRecord>>>(emptyMap()) }
    var verifying_domain_id by remember { mutableStateOf<String?>(null) }
    var domain_verify_results by remember { mutableStateOf<Map<String, SettingsViewModel.DomainVerifyOutcome>>(emptyMap()) }

    var domains_phase by remember { mutableStateOf(domains_load_phase.loading) }
    var domains_error by remember { mutableStateOf<String?>(null) }
    var domains_reload_tick by remember { mutableIntStateOf(0) }

    val purchase_vm: DomainPurchaseViewModel = hiltViewModel()
    val purchase_state by purchase_vm.state.collectAsStateWithLifecycle()

    LaunchedEffect(Unit) {
        vm.load_custom_domain_addresses()
        purchase_vm.load_orders()
        purchase_vm.check_pending_order()
    }

    LaunchedEffect(domains_reload_tick) {
        domains_phase = domains_load_phase.loading
        domains_error = null
        vm.clear_action_result()
        vm.load_domains()
        val settled = withTimeoutOrNull(domains_load_timeout_ms) {
            vm.state.first { !it.domains_loading }
        }
        when {
            settled == null -> {
                domains_error = context.getString(R.string.domains_load_timeout)
                domains_phase = domains_load_phase.failed
            }
            settled.domains.isEmpty() && settled.action_result != null -> {
                domains_error = settled.action_result
                vm.clear_action_result()
                domains_phase = domains_load_phase.failed
            }
            else -> domains_phase = domains_load_phase.loaded
        }
    }

    LaunchedEffect(purchase_state.checkout_url) {
        val url = purchase_state.checkout_url ?: return@LaunchedEffect
        open_url(context, url)
        purchase_vm.consume_checkout_url()
    }

    LaunchedEffect(purchase_state.resume_order_id) {
        val id = purchase_state.resume_order_id ?: return@LaunchedEffect
        purchase_vm.consume_resume_order()
        on_open_domain_order(id)
    }

    val lifecycle_owner = LocalLifecycleOwner.current
    DisposableEffect(lifecycle_owner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                purchase_vm.check_pending_order()
                purchase_vm.load_orders()
            }
        }
        lifecycle_owner.lifecycle.addObserver(observer)
        onDispose { lifecycle_owner.lifecycle.removeObserver(observer) }
    }

    LaunchedEffect(state.action_result) {
        val msg = state.action_result ?: return@LaunchedEffect
        android.widget.Toast.makeText(context, msg, android.widget.Toast.LENGTH_SHORT).show()
        vm.clear_action_result()
    }

    val load_dns_records: (String) -> Unit = { domain_id ->
        scope.launch {
            val records = vm.get_dns_records_now(domain_id)
            if (records != null) domain_dns = domain_dns + (domain_id to records)
        }
    }

    val verify_domain: (String) -> Unit = { domain_id ->
        verifying_domain_id = domain_id
        scope.launch {
            try {
                val outcome = vm.trigger_domain_verification_now(domain_id)
                domain_verify_results = domain_verify_results + (domain_id to outcome)
                if (!outcome.rate_limited) {
                    val records = vm.get_dns_records_now(domain_id)
                    if (!records.isNullOrEmpty()) domain_dns = domain_dns + (domain_id to records)
                }
            } finally {
                verifying_domain_id = null
            }
        }
    }

    detail_scaffold(title = stringResource(R.string.settings_domains), on_back = on_back) {
        when {
            state.domains.isNotEmpty() -> domains_tab(
                vm = vm,
                state = state,
                scope = scope,
                expanded_domain_id = expanded_domain_id,
                domain_dns = domain_dns,
                verifying_domain_id = verifying_domain_id,
                verify_results = domain_verify_results,
                on_expanded_change = { expanded_domain_id = it },
                on_dns_loaded = { id, records -> domain_dns = domain_dns + (id to records) },
                on_verifying_change = { verifying_domain_id = it },
                on_verify_result = { id, outcome -> domain_verify_results = domain_verify_results + (id to outcome) },
                on_show_add = { show_add_domain = true },
                catch_all_locked = catch_all_locked,
            )
            domains_phase == domains_load_phase.loading -> {
                domains_list_header(count = null, on_show_add = { show_add_domain = true })
                skeleton_card_list(rows = 2, leading_circle = true)
            }
            domains_phase == domains_load_phase.failed -> {
                domains_list_header(count = null, on_show_add = { show_add_domain = true })
                load_failed_card(
                    message = domains_error,
                    on_retry = { domains_reload_tick += 1 },
                )
            }
            else -> {
                domains_list_header(count = 0, on_show_add = { show_add_domain = true })
                AsterCard(modifier = Modifier.fillMaxWidth()) {
                    detail_row(
                        title = stringResource(R.string.no_custom_domains),
                        subtitle = stringResource(R.string.no_custom_domains_subtitle),
                    )
                }
            }
        }
        v_gap(AsterSpacing.md)
        domain_purchase_area(
            state = purchase_state,
            on_buy = on_open_buy_domain,
            on_open_order = { on_open_domain_order(it.id) },
            on_cancel = { purchase_vm.cancel_order(it) },
            on_complete_purchase = { purchase_vm.complete_purchase(it) },
            on_renew = { purchase_vm.renew_order(it) },
            custom_domains = state.domains,
            dns_records_for = { id -> domain_dns[id].orEmpty() },
            verifying_domain_id = verifying_domain_id,
            verify_message_for = { id -> domain_verify_results[id]?.message },
            catch_all_locked = catch_all_locked,
            on_load_dns = load_dns_records,
            on_verify_domain = verify_domain,
            on_toggle_catch_all = { vm.toggle_domain_catch_all(it) },
        )
    }

    if (show_add_domain) {
        add_domain_dialog(
            on_dismiss = { show_add_domain = false },
            on_add = { domain_name, token, on_result ->
                vm.add_domain_now(domain_name, token) { domain, error ->
                    on_result(error)
                    if (domain != null) {
                        show_add_domain = false
                        domains_phase = domains_load_phase.loaded
                        expanded_domain_id = domain.id
                        scope.launch {
                            val records = vm.get_dns_records_now(domain.id)
                            if (!records.isNullOrEmpty()) domain_dns = domain_dns + (domain.id to records)
                        }
                    }
                }
            },
        )
    }
}

@Composable
private fun domains_list_header(count: Int?, on_show_add: () -> Unit) {
    val colors = AsterMaterial.colors
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (count == null) {
            Box(
                modifier = Modifier
                    .width(84.dp)
                    .height(13.dp)
                    .background(shimmer_brush(), SquircleShape(6.dp)),
            )
        } else {
            Text(
                text = pluralStringResource(R.plurals.domains_count_plural, count, count),
                color = colors.text_tertiary,
                fontSize = 13.sp,
            )
        }
        TextButton(onClick = on_show_add) {
            Text(
                text = stringResource(R.string.alias_action_add),
                color = colors.accent_blue,
                fontSize = 14.sp,
            )
        }
    }
    v_gap(AsterSpacing.sm)
}
