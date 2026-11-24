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

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.ContentTransform
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.rememberScrollState
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
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
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
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull
import org.astermail.android.R
import org.astermail.android.api.settings.DnsRecord
import org.astermail.android.billing.PlanLimitsViewModel
import org.astermail.android.design.AsterDuration
import org.astermail.android.design.AsterEasing
import org.astermail.android.design.AsterMaterial
import org.astermail.android.design.AsterSpacing
import org.astermail.android.design.aster_reduce_motion
import org.astermail.android.design.components.AsterCard
import org.astermail.android.settings.DomainPurchaseViewModel
import org.astermail.android.settings.SettingsViewModel
import org.astermail.android.settings.domains.DnsZoneViewModel
import org.astermail.android.settings.shared_settings_view_model
import org.astermail.android.ui.settings.join_launched

private enum class domains_load_phase { loading, loaded, failed }

private const val domains_load_timeout_ms = 25_000L

@Composable
fun DomainsScreen(
    on_back: () -> Unit,
    on_open_buy_domain: () -> Unit = {},
    on_open_domain_order: (String) -> Unit = {},
    on_open_bimi: (String) -> Unit = {},
) {
    val vm: SettingsViewModel = shared_settings_view_model()
    val state by vm.state.collectAsStateWithLifecycle()
    val plan_vm: PlanLimitsViewModel = hiltViewModel()
    val plan_state by plan_vm.state.collectAsStateWithLifecycle()
    val zone_vm: DnsZoneViewModel = hiltViewModel()
    val zone_state by zone_vm.state.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val catch_all_locked = plan_vm.is_feature_locked("has_catch_all") && !plan_state.is_loading
    val reduce_motion = aster_reduce_motion()

    var show_add_domain by remember { mutableStateOf(false) }
    var selected_domain_id by rememberSaveable { mutableStateOf<String?>(null) }
    var domain_dns by remember { mutableStateOf<Map<String, List<DnsRecord>>>(emptyMap()) }
    var verifying_domain_id by remember { mutableStateOf<String?>(null) }
    var domain_verify_results by remember { mutableStateOf<Map<String, SettingsViewModel.DomainVerifyOutcome>>(emptyMap()) }

    var domains_phase by remember { mutableStateOf(domains_load_phase.loading) }
    var domains_error by remember { mutableStateOf<String?>(null) }
    var domains_reload_tick by remember { mutableIntStateOf(0) }
    var domains_settled_tick by remember { mutableIntStateOf(-1) }

    val list_scroll = rememberScrollState()
    val detail_scroll = rememberScrollState()

    val cached_dns = state.domain_dns_records
    val effective_dns = remember(cached_dns, domain_dns) { cached_dns + domain_dns }
    val selected_domain = state.domains.firstOrNull { it.id == selected_domain_id }

    val purchase_vm: DomainPurchaseViewModel = hiltViewModel()
    val purchase_state by purchase_vm.state.collectAsStateWithLifecycle()

    BackHandler(enabled = selected_domain_id != null) { selected_domain_id = null }

    LaunchedEffect(Unit) {
        vm.load_custom_domain_addresses()
        purchase_vm.load_orders()
        purchase_vm.check_pending_order()
    }

    LaunchedEffect(domains_reload_tick) {
        val had_domains = vm.state.value.domains.isNotEmpty()
        if (!had_domains) domains_phase = domains_load_phase.loading
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
        domains_settled_tick = domains_reload_tick
    }

    val managed_domain_ids = remember(state.domains) { state.domains.filter { !it.is_shared }.map { it.id } }
    LaunchedEffect(managed_domain_ids) {
        val missing = managed_domain_ids.filter { it !in vm.state.value.domain_health }
        coroutineScope {
            missing.forEach { id -> launch { vm.get_domain_health_now(id) } }
        }
    }

    LaunchedEffect(selected_domain_id, state.domains, domains_phase) {
        val id = selected_domain_id ?: return@LaunchedEffect
        if (domains_phase == domains_load_phase.loaded && state.domains.none { it.id == id }) {
            selected_domain_id = null
        }
    }

    LaunchedEffect(selected_domain?.id, selected_domain?.is_shared) {
        val domain = selected_domain ?: return@LaunchedEffect
        detail_scroll.scrollTo(0)
        if (!domain.is_shared) zone_vm.bind(domain.id, domain.domain_name)
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
        org.astermail.android.ui.common.app_toast.show(msg.toString())
        vm.clear_action_result()
    }

    val load_dns_records: (String) -> Unit = { domain_id ->
        scope.launch {
            val records = vm.get_dns_records_now(domain_id)
            if (records != null) domain_dns = domain_dns + (domain_id to records)
        }
    }

    val verify_domain: (String) -> Unit = verify@{ domain_id ->
        if (verifying_domain_id != null) return@verify
        verifying_domain_id = domain_id
        domain_verify_results = domain_verify_results - domain_id
        scope.launch {
            try {
                val outcome = vm.trigger_domain_verification_now(domain_id)
                domain_verify_results = domain_verify_results + (domain_id to outcome)
                if (!outcome.rate_limited) {
                    val records = vm.get_dns_records_now(domain_id)
                    if (!records.isNullOrEmpty()) domain_dns = domain_dns + (domain_id to records)
                    vm.get_domain_health_now(domain_id)
                }
            } finally {
                verifying_domain_id = null
            }
        }
    }

    detail_scaffold(
        title = selected_domain?.domain_name ?: stringResource(R.string.settings_domains),
        on_back = { if (selected_domain_id != null) selected_domain_id = null else on_back() },
        scroll_state = if (selected_domain != null) detail_scroll else list_scroll,
        on_refresh = {
            domains_reload_tick += 1
            val target_tick = domains_reload_tick
            val refresh_id = selected_domain_id
            coroutineScope {
                launch { purchase_vm.join_launched { purchase_vm.load_orders() } }
                launch { vm.join_launched { vm.load_custom_domain_addresses(force = true) } }
                launch { snapshotFlow { domains_settled_tick }.first { it >= target_tick } }
                if (refresh_id != null) {
                    launch {
                        val records = vm.get_dns_records_now(refresh_id)
                        if (records != null) domain_dns = domain_dns + (refresh_id to records)
                    }
                    launch { vm.get_domain_health_now(refresh_id) }
                    zone_vm.refresh()
                }
            }
        },
        refresh_enabled = verifying_domain_id == null && !show_add_domain,
    ) {
        AnimatedContent(
            targetState = selected_domain?.id,
            transitionSpec = {
                if (reduce_motion) {
                    ContentTransform(EnterTransition.None, ExitTransition.None, sizeTransform = null)
                } else {
                    ContentTransform(
                        targetContentEnter = fadeIn(
                            tween(AsterDuration.short_4, delayMillis = 40, easing = AsterEasing.standard_decelerate),
                        ),
                        initialContentExit = fadeOut(tween(AsterDuration.short_3, easing = AsterEasing.standard_accelerate)),
                        sizeTransform = null,
                    )
                }
            },
            contentAlignment = Alignment.TopStart,
            label = "domains_detail",
        ) { target_id ->
            val domain = state.domains.firstOrNull { it.id == target_id }
            Column(modifier = Modifier.fillMaxWidth()) {
                if (domain != null) {
                    val addresses = state.custom_domain_addresses.filter {
                        it.domain_name.equals(domain.domain_name, ignoreCase = true) && !it.decryption_failed
                    }
                    domain_detail_view(
                        domain = domain,
                        vm = vm,
                        health = state.domain_health[domain.id],
                        dns_records = effective_dns[domain.id],
                        addresses = addresses.map { it.address },
                        address_ids = addresses.map { it.id },
                        verify = DomainVerifyView(
                            running = verifying_domain_id == domain.id,
                            outcome = domain_verify_results[domain.id],
                        ),
                        catch_all_locked = catch_all_locked,
                        zone_state = zone_state,
                        zone_vm = zone_vm,
                        on_load_dns = { load_dns_records(domain.id) },
                        on_verify = { verify_domain(domain.id) },
                        on_open_bimi = { on_open_bimi(domain.id) },
                        on_deleted = { selected_domain_id = null },
                    )
                } else {
                    domains_list_content(
                        state = state,
                        phase = domains_phase,
                        error = domains_error,
                        on_show_add = { show_add_domain = true },
                        on_retry = { domains_reload_tick += 1 },
                        on_open = {
                            if (!it.is_shared) zone_vm.bind(it.id, it.domain_name)
                            selected_domain_id = it.id
                        },
                    )
                    v_gap(AsterSpacing.md)
                    domain_purchase_area(
                        state = purchase_state,
                        can_buy = !org.astermail.android.billing.remember_play_install(),
                        on_buy = on_open_buy_domain,
                        on_open_order = { on_open_domain_order(it.id) },
                        on_cancel = { purchase_vm.cancel_order(it) },
                        on_complete_purchase = { purchase_vm.complete_purchase(it) },
                        on_renew = { purchase_vm.renew_order(it) },
                        custom_domains = state.domains,
                        dns_records_for = { id -> effective_dns[id].orEmpty() },
                        verifying_domain_id = verifying_domain_id,
                        verify_message_for = { id -> domain_verify_results[id]?.message },
                        catch_all_locked = catch_all_locked,
                        on_load_dns = load_dns_records,
                        on_verify_domain = verify_domain,
                        on_toggle_catch_all = { vm.toggle_domain_catch_all(it) },
                    )
                }
            }
        }
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
                        zone_vm.bind(domain.id, domain.domain_name)
                        selected_domain_id = domain.id
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
private fun domains_list_content(
    state: org.astermail.android.settings.SettingsUiState,
    phase: domains_load_phase,
    error: String?,
    on_show_add: () -> Unit,
    on_retry: () -> Unit,
    on_open: (org.astermail.android.api.settings.CustomDomain) -> Unit,
) {
    val show_skeleton = remember_delayed_flag(phase == domains_load_phase.loading && state.domains.isEmpty())
    when {
        state.domains.isNotEmpty() -> {
            domains_list_header(count = state.domains.size, on_show_add = on_show_add)
            domain_list_card(
                domains = state.domains,
                health_for = { state.domain_health[it] },
                address_count_for = { name ->
                    state.custom_domain_addresses.count { it.domain_name.equals(name, ignoreCase = true) }
                },
                on_open = on_open,
            )
        }
        phase == domains_load_phase.loading -> {
            domains_list_header(count = null, on_show_add = on_show_add)
            Box(modifier = Modifier.fillMaxWidth().heightIn(min = 140.dp)) {
                if (show_skeleton) domain_cards_pulse_skeleton(rows = 2)
            }
        }
        phase == domains_load_phase.failed -> {
            domains_list_header(count = null, on_show_add = on_show_add)
            load_failed_card(message = error, on_retry = on_retry)
        }
        else -> {
            domains_list_header(count = 0, on_show_add = on_show_add)
            AsterCard(modifier = Modifier.fillMaxWidth()) {
                detail_row(
                    title = stringResource(R.string.no_custom_domains),
                    subtitle = stringResource(R.string.no_custom_domains_subtitle),
                )
            }
        }
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
            domain_count_pulse_bone()
        } else {
            Text(
                text = pluralStringResource(R.plurals.domains_count_plural, count, count),
                color = colors.text_tertiary,
                fontSize = 13.sp,
            )
        }
        TextButton(onClick = on_show_add) {
            Text(
                text = stringResource(R.string.domain_mgmt_add_domain),
                color = colors.accent_blue,
                fontSize = 14.sp,
            )
        }
    }
    v_gap(AsterSpacing.sm)
}
