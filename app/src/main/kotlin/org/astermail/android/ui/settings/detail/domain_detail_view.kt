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

import androidx.compose.animation.Crossfade
import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.repeatOnLifecycle
import compose.icons.TablerIcons
import compose.icons.tablericons.AlertTriangle
import compose.icons.tablericons.CircleCheck
import compose.icons.tablericons.Clock
import compose.icons.tablericons.Key
import compose.icons.tablericons.Trash
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import org.astermail.android.R
import org.astermail.android.api.settings.CustomDomain
import org.astermail.android.api.settings.DnsRecord
import org.astermail.android.api.settings.DomainCheck
import org.astermail.android.api.settings.DomainHealth
import org.astermail.android.design.AsterMaterial
import org.astermail.android.design.AsterSpacing
import org.astermail.android.design.components.AsterAlert
import org.astermail.android.design.components.AsterAlertDialog
import org.astermail.android.design.components.AsterAlertSeverity
import org.astermail.android.design.components.AsterButton
import org.astermail.android.design.components.AsterCard
import org.astermail.android.design.components.AsterCompactButton
import org.astermail.android.design.components.AsterDestructiveButton
import org.astermail.android.design.components.AsterIconButton
import org.astermail.android.design.components.AsterSecondaryButton
import org.astermail.android.design.components.DialogConfirmStyle
import org.astermail.android.settings.SettingsViewModel
import org.astermail.android.settings.domain_check_message_res
import org.astermail.android.settings.domains.DOMAIN_HEALTH_POLL_MS
import org.astermail.android.settings.domains.DomainHealthLevel
import org.astermail.android.settings.domains.DomainTone
import org.astermail.android.settings.domains.DnsZoneUiState
import org.astermail.android.settings.domains.DnsZoneViewModel
import org.astermail.android.settings.domains.domain_check_label_res
import org.astermail.android.settings.domains.domain_health_level
import org.astermail.android.settings.domains.domain_health_reason_res
import org.astermail.android.settings.domains.domain_health_title_res
import org.astermail.android.settings.domains.domain_tone
import org.astermail.android.settings.domains.domain_tone_res
import org.astermail.android.settings.domains.is_check_failing
import org.astermail.android.settings.domains.is_domain_active
import org.astermail.android.settings.domains.ordered_domain_checks
import org.astermail.android.settings.domains.should_keep_polling_health
import org.astermail.android.settings.is_dkim_record
import org.astermail.android.settings.should_offer_short_dkim_key
import org.astermail.android.ui.settings.detail.bimi.bimi_row

internal data class DomainVerifyView(
    val running: Boolean = false,
    val outcome: SettingsViewModel.DomainVerifyOutcome? = null,
)

@Composable
internal fun domain_tone_color(tone: DomainTone): Color {
    val colors = AsterMaterial.colors
    return when (tone) {
        DomainTone.verified -> colors.success
        DomainTone.pending -> colors.warning
        DomainTone.attention -> colors.danger
    }
}

internal fun format_domain_grace_end(value: String?): String? {
    val millis = parse_domain_instant_ms(value) ?: return null
    return java.text.DateFormat.getDateInstance(java.text.DateFormat.MEDIUM).format(java.util.Date(millis))
}

@Composable
internal fun domain_detail_view(
    domain: CustomDomain,
    vm: SettingsViewModel,
    health: DomainHealth?,
    dns_records: List<DnsRecord>?,
    addresses: List<String>,
    address_ids: List<String>,
    verify: DomainVerifyView,
    catch_all_locked: Boolean,
    zone_state: DnsZoneUiState,
    zone_vm: DnsZoneViewModel,
    on_load_dns: () -> Unit,
    on_verify: () -> Unit,
    on_open_bimi: () -> Unit,
    on_deleted: () -> Unit,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    val can_manage = !domain.is_shared
    val active = is_domain_active(domain)
    val tone = domain_tone(domain, health)
    var rechecking by remember { mutableStateOf(false) }
    var confirm_delete by remember { mutableStateOf(false) }
    var confirm_rotate by remember { mutableStateOf(false) }
    var confirm_short_key by remember { mutableStateOf(false) }
    var shortening by remember { mutableStateOf(false) }
    var rotated_record by remember { mutableStateOf<DnsRecord?>(null) }
    var address_to_delete by remember { mutableStateOf<Pair<String, String>?>(null) }
    val (provider, set_provider) = remember_dns_provider(domain.id)

    LaunchedEffect(domain.id) {
        if (dns_records == null && can_manage) on_load_dns()
    }

    LaunchedEffect(domain.id, can_manage) {
        if (!can_manage) return@LaunchedEffect
        lifecycle.repeatOnLifecycle(Lifecycle.State.RESUMED) {
            var latest = vm.get_domain_health_now(domain.id)
            while (should_keep_polling_health(latest)) {
                delay(DOMAIN_HEALTH_POLL_MS)
                latest = vm.get_domain_health_now(domain.id)
            }
        }
    }

    domain_detail_hero(domain = domain, tone = tone)

    if (can_manage && !active) {
        val required = dns_records.orEmpty().filter { it.required }
        v_gap(AsterSpacing.md)
        domain_setup_steps(
            current = domain_setup_step(
                records_found = required.count { it.verified },
                records_total = required.size,
            ),
        )
    }

    if (domain.is_shared) {
        v_gap(AsterSpacing.md)
        AsterAlert(
            message = stringResource(R.string.domain_shared_hint),
            title = domain.shared_from?.let { stringResource(R.string.domain_shared_by, it) },
            severity = AsterAlertSeverity.info,
        )
    }

    format_domain_grace_end(domain.downgrade_grace_expires_at)?.let { end ->
        v_gap(AsterSpacing.md)
        AsterAlert(
            title = stringResource(R.string.domain_grace_ends, end),
            message = stringResource(R.string.domain_grace_upgrade_hint),
            severity = AsterAlertSeverity.warning,
        )
    }

    if (can_manage) {
        settings_row_gap()
        domain_health_card(
            health = health,
            rechecking = rechecking,
            on_recheck = {
                if (!rechecking) {
                    rechecking = true
                    scope.launch {
                        try {
                            vm.get_domain_health_now(domain.id)
                        } finally {
                            rechecking = false
                        }
                    }
                }
            },
        )

        settings_row_gap()
        section_label(stringResource(R.string.domain_dns_records))
        Text(
            text = stringResource(R.string.domain_dns_instructions),
            color = AsterMaterial.colors.text_tertiary,
            fontSize = 13.sp,
            modifier = Modifier.padding(bottom = AsterSpacing.sm),
        )
        dns_provider_row(domain_name = domain.domain_name, provider = provider, on_change = set_provider)
        v_gap(AsterSpacing.sm)
        domain_setup_records(
            records = dns_records,
            domain = domain,
            health = health,
            provider = provider,
            confirm_short_key = { confirm_short_key = true },
            shortening = shortening,
        )
        v_gap(AsterSpacing.md)
        domain_verify_block(active = active, verify = verify, on_verify = on_verify)
        rotated_record?.let { record ->
            v_gap(AsterSpacing.md)
            dkim_rotation_notice(
                record = record,
                on_copy = { label, value -> copy_dns_value(context, label, value) },
                on_dismiss = { rotated_record = null },
            )
        }

        if (zone_state.domain_id == domain.id) {
            dns_zone_section(state = zone_state, vm = zone_vm)
        }
    }

    settings_row_gap()
    settings_group(title = stringResource(R.string.domain_mgmt_settings_title)) {
        settings_toggle_row(
            title = stringResource(R.string.catch_all),
            subtitle = stringResource(
                if (catch_all_locked) R.string.domain_mgmt_catch_all_locked else R.string.domain_catch_all_hint,
            ),
            checked = domain.catch_all_enabled,
            enabled = can_manage && active && !catch_all_locked,
            on_change = { vm.toggle_domain_catch_all(domain.id) },
        )
        if (can_manage && domain.bimi_available) {
            bimi_row(domain = domain, is_active = active, on_open = on_open_bimi)
        }
        if (can_manage) {
            detail_row(
                title = stringResource(R.string.domain_dkim_rotate_title),
                subtitle = stringResource(R.string.domain_dkim_rotate_hint),
                icon = TablerIcons.Key,
                on_click = { confirm_rotate = true },
            )
        }
    }

    settings_row_gap()
    domain_addresses_group(
        addresses = addresses,
        address_ids = address_ids,
        can_manage = can_manage,
        on_delete = { id, address -> address_to_delete = id to address },
    )

    if (can_manage) {
        settings_row_gap()
        AsterDestructiveButton(
            label = stringResource(R.string.domain_delete_domain),
            onClick = { confirm_delete = true },
            modifier = Modifier.fillMaxWidth(),
        )
    }
    v_gap(AsterSpacing.xl)

    if (confirm_delete) {
        AsterAlertDialog(
            on_dismiss = { confirm_delete = false },
            title = stringResource(R.string.domain_delete_confirm_title),
            message = stringResource(R.string.domain_delete_confirm_message, domain.domain_name),
            confirm_label = stringResource(R.string.delete),
            cancel_label = stringResource(R.string.cancel),
            confirm_style = DialogConfirmStyle.destructive,
            on_confirm = {
                confirm_delete = false
                vm.delete_domain(domain.id)
                on_deleted()
            },
        )
    }

    if (confirm_rotate) {
        dkim_rotate_confirm_dialog(
            domain_name = domain.domain_name,
            is_aster_managed = domain.is_aster_managed,
            on_dismiss = { confirm_rotate = false },
            on_confirm = {
                confirm_rotate = false
                scope.launch {
                    val outcome = vm.rotate_dkim_now(domain.id) ?: return@launch
                    val manual = outcome.manual_record
                    if (manual != null) {
                        rotated_record = manual
                    } else {
                        org.astermail.android.ui.common.app_toast.show(context.getString(R.string.domain_mgmt_dkim_rotated).toString())
                    }
                    on_load_dns()
                    vm.get_domain_health_now(domain.id)
                }
            },
        )
    }

    if (confirm_short_key) {
        AsterAlertDialog(
            on_dismiss = { if (!shortening) confirm_short_key = false },
            title = stringResource(R.string.dkim_short_key_button),
            message = stringResource(R.string.dkim_short_key_confirm),
            confirm_label = stringResource(R.string.dkim_short_key_confirm_button),
            cancel_label = stringResource(R.string.cancel),
            is_busy = shortening,
            dismiss_on_confirm = false,
            on_confirm = {
                if (!shortening) {
                    shortening = true
                    scope.launch {
                        try {
                            val message = when (val outcome = vm.shorten_dkim_key_now(domain.id)) {
                                is SettingsViewModel.DkimShortKeyOutcome.Replaced -> {
                                    confirm_short_key = false
                                    on_load_dns()
                                    if (domain.is_aster_managed) R.string.dkim_short_key_done_auto else R.string.dkim_short_key_done
                                }
                                SettingsViewModel.DkimShortKeyOutcome.RateLimited -> R.string.dkim_short_key_wait
                                SettingsViewModel.DkimShortKeyOutcome.Failed -> R.string.domain_check_generic_failure
                            }
                            org.astermail.android.ui.common.app_toast.show(context.getString(message).toString())
                        } finally {
                            shortening = false
                        }
                    }
                }
            },
        )
    }

    address_to_delete?.let { (id, address) ->
        AsterAlertDialog(
            on_dismiss = { address_to_delete = null },
            title = stringResource(R.string.domain_mgmt_address_delete_title),
            message = stringResource(R.string.domain_mgmt_address_delete_message, address),
            confirm_label = stringResource(R.string.delete),
            cancel_label = stringResource(R.string.cancel),
            confirm_style = DialogConfirmStyle.destructive,
            on_confirm = {
                address_to_delete = null
                vm.delete_domain_address(id, domain.domain_name)
            },
        )
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun domain_detail_hero(domain: CustomDomain, tone: DomainTone) {
    val colors = AsterMaterial.colors
    Column(modifier = Modifier.fillMaxWidth().padding(top = AsterSpacing.xs, bottom = AsterSpacing.xs)) {
        Text(
            text = domain.domain_name,
            color = colors.text_primary,
            fontSize = 22.sp,
            lineHeight = 28.sp,
            fontWeight = FontWeight.SemiBold,
            modifier = Modifier.semantics { heading() },
        )
        v_gap(AsterSpacing.md)
        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(AsterSpacing.sm),
            verticalArrangement = Arrangement.spacedBy(AsterSpacing.sm),
        ) {
            tone_badge(text = stringResource(domain_tone_res(tone)), tone = domain_tone_color(tone))
            if (domain.is_shared) {
                tone_badge(text = stringResource(R.string.domain_shared_label), tone = colors.accent_blue)
            }
            if (domain.is_aster_managed) {
                tone_badge(text = stringResource(R.string.domain_mgmt_managed_dns), tone = colors.text_tertiary)
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun domain_health_card(
    health: DomainHealth?,
    rechecking: Boolean,
    on_recheck: () -> Unit,
) {
    val colors = AsterMaterial.colors
    val level = domain_health_level(health)
    val (icon, tint) = when (level) {
        DomainHealthLevel.ok -> TablerIcons.CircleCheck to colors.success
        DomainHealthLevel.warning -> TablerIcons.AlertTriangle to colors.warning
        DomainHealthLevel.critical -> TablerIcons.AlertTriangle to colors.danger
        DomainHealthLevel.unknown -> TablerIcons.Clock to colors.text_tertiary
    }
    val checks = remember(health) { ordered_domain_checks(health?.checks.orEmpty()) }
    AsterCard(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .animateContentSize(domain_tween())
                .padding(AsterSpacing.lg),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Crossfade(
                    targetState = icon to tint,
                    animationSpec = domain_tween(),
                    label = "domain_health_icon",
                ) { (shown_icon, shown_tint) ->
                    Icon(imageVector = shown_icon, contentDescription = null, tint = shown_tint, modifier = Modifier.size(22.dp))
                }
                Spacer(Modifier.width(AsterSpacing.sm))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = stringResource(domain_health_title_res(level)),
                        color = colors.text_primary,
                        fontSize = 15.sp,
                        fontWeight = FontWeight.SemiBold,
                    )
                    Text(
                        text = domain_checked_label(health?.checked_at),
                        color = colors.text_tertiary,
                        fontSize = 12.sp,
                    )
                }
                AsterCompactButton(
                    label = stringResource(R.string.domain_health_recheck),
                    onClick = on_recheck,
                    enabled = !rechecking,
                    is_loading = rechecking,
                )
            }
            v_gap(AsterSpacing.md)
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(AsterSpacing.sm),
                verticalArrangement = Arrangement.spacedBy(AsterSpacing.sm),
            ) {
                domain_capability_badge(
                    label = stringResource(R.string.domain_health_receiving),
                    ok = health?.receiving_mail,
                )
                domain_capability_badge(
                    label = stringResource(R.string.domain_health_sending),
                    ok = health?.sending_trusted,
                )
            }
            checks.filter(::is_check_failing).forEachIndexed { index, check ->
                key(check.key, index) {
                    v_gap(AsterSpacing.sm)
                    domain_check_line(check)
                }
            }
        }
    }
}

internal fun parse_domain_instant_ms(value: String?): Long? {
    if (value.isNullOrBlank()) return null
    return runCatching { java.time.OffsetDateTime.parse(value).toInstant().toEpochMilli() }.getOrNull()
        ?: runCatching { java.time.Instant.parse(value).toEpochMilli() }.getOrNull()
}

@Composable
private fun domain_checked_label(checked_at: String?): String {
    val millis = parse_domain_instant_ms(checked_at) ?: return stringResource(R.string.domain_health_never_checked)
    var now by remember { mutableStateOf(System.currentTimeMillis()) }
    LaunchedEffect(millis) {
        while (true) {
            now = System.currentTimeMillis()
            delay(30_000L)
        }
    }
    val elapsed = now - millis
    if (elapsed < 60_000L) return stringResource(R.string.domain_health_checked_just_now)
    val relative = android.text.format.DateUtils.getRelativeTimeSpanString(
        millis,
        now,
        android.text.format.DateUtils.MINUTE_IN_MILLIS,
    ).toString()
    return stringResource(R.string.domain_health_last_checked, relative)
}

@Composable
private fun domain_capability_badge(label: String, ok: Boolean?) {
    val colors = AsterMaterial.colors
    tone_badge(
        text = label,
        tone = when (ok) {
            true -> colors.success
            false -> colors.warning
            null -> colors.text_tertiary
        },
        icon = when (ok) {
            true -> TablerIcons.CircleCheck
            false -> TablerIcons.AlertTriangle
            null -> TablerIcons.Clock
        },
    )
}

@Composable
private fun domain_check_line(check: DomainCheck) {
    val colors = AsterMaterial.colors
    val label = domain_check_label_res(check.key)?.let { stringResource(it) } ?: check.key.uppercase()
    val message_res = domain_health_reason_res(check.reason) ?: domain_check_message_res(check)
    val tint = if (check.outcome.equals("fail", ignoreCase = true)) colors.danger else colors.warning
    Row(verticalAlignment = Alignment.Top) {
        Text(
            text = label,
            color = tint,
            fontSize = 13.sp,
            fontWeight = FontWeight.SemiBold,
            modifier = Modifier.width(56.dp),
        )
        Text(
            text = message_res?.let { stringResource(it) } ?: stringResource(R.string.domain_check_generic_failure),
            color = colors.text_secondary,
            fontSize = 13.sp,
            modifier = Modifier.weight(1f),
        )
    }
}

@Composable
private fun domain_setup_records(
    records: List<DnsRecord>?,
    domain: CustomDomain,
    health: DomainHealth?,
    provider: org.astermail.android.settings.domains.DnsProvider,
    confirm_short_key: () -> Unit,
    shortening: Boolean,
) {
    val show_skeleton = remember_delayed_flag(records == null)
    if (records == null) {
        Box(modifier = Modifier.fillMaxWidth().heightIn(min = 160.dp)) {
            if (show_skeleton) domain_cards_pulse_skeleton(rows = 2)
        }
        return
    }
    AsterCard(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(AsterSpacing.sm),
            verticalArrangement = Arrangement.spacedBy(AsterSpacing.sm),
        ) {
            if (records.isEmpty()) {
                Text(
                    text = stringResource(R.string.domain_mgmt_records_empty),
                    color = AsterMaterial.colors.text_tertiary,
                    fontSize = 13.sp,
                    modifier = Modifier.padding(AsterSpacing.sm),
                )
            }
            records.forEachIndexed { index, record ->
                key(record.type, record.name, record.value, index) {
                Column {
                    dns_setup_record_card(record = record, domain_name = domain.domain_name, provider = provider)
                    val offer_short = is_dkim_record(record) && should_offer_short_dkim_key(
                        record = record,
                        can_shorten = health?.dkim_can_shorten == true,
                        can_manage = !domain.is_shared,
                    )
                    if (offer_short) {
                        v_gap(AsterSpacing.xs)
                        AsterSecondaryButton(
                            label = stringResource(R.string.dkim_short_key_button),
                            onClick = confirm_short_key,
                            enabled = !shortening,
                            is_loading = shortening,
                            modifier = Modifier.fillMaxWidth(),
                        )
                    }
                }
                }
            }
        }
    }
}

@Composable
private fun domain_verify_block(
    active: Boolean,
    verify: DomainVerifyView,
    on_verify: () -> Unit,
) {
    val outcome = verify.outcome
    if (!active || verify.running || outcome != null) {
        AsterButton(
            label = stringResource(if (verify.running) R.string.domain_verifying else R.string.domain_mgmt_verify_action),
            onClick = on_verify,
            enabled = !verify.running,
            is_loading = verify.running,
            modifier = Modifier.fillMaxWidth(),
        )
    }
    val last_holder = remember { arrayOfNulls<SettingsViewModel.DomainVerifyOutcome>(1) }
    if (outcome != null) last_holder[0] = outcome
    val last = last_holder[0]
    domain_reveal(visible = outcome != null && !verify.running) {
        Column {
            v_gap(AsterSpacing.sm)
            last?.let { result ->
                AsterAlert(
                    message = result.message.ifBlank {
                        stringResource(if (result.verified) R.string.domain_verify_success else R.string.domain_verify_pending_generic)
                    },
                    severity = when {
                        result.verified -> AsterAlertSeverity.success
                        result.rate_limited -> AsterAlertSeverity.info
                        else -> AsterAlertSeverity.warning
                    },
                )
            }
        }
    }
}

@Composable
private fun domain_addresses_group(
    addresses: List<String>,
    address_ids: List<String>,
    can_manage: Boolean,
    on_delete: (String, String) -> Unit,
) {
    val colors = AsterMaterial.colors
    settings_group(title = stringResource(R.string.domain_mgmt_addresses_title)) {
        if (addresses.isEmpty()) {
            Text(
                text = stringResource(R.string.domain_mgmt_addresses_empty),
                color = colors.text_tertiary,
                fontSize = 13.sp,
                modifier = Modifier.padding(horizontal = AsterSpacing.lg, vertical = AsterSpacing.sm),
            )
        }
        addresses.forEachIndexed { index, address ->
            key(address_ids.getOrNull(index) ?: address, index) {
            if (index > 0) HorizontalDivider(color = colors.border_secondary, thickness = 0.5.dp)
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = 48.dp)
                    .padding(start = AsterSpacing.lg, end = AsterSpacing.xs),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                alias_address_text(
                    address = address,
                    font_size = 14.sp,
                    font_weight = FontWeight.Normal,
                    modifier = Modifier
                        .weight(1f)
                        .padding(vertical = AsterSpacing.sm),
                )
                if (can_manage) {
                    AsterIconButton(
                        icon = TablerIcons.Trash,
                        content_description = stringResource(R.string.domain_mgmt_address_delete_label, address),
                        onClick = { address_ids.getOrNull(index)?.let { on_delete(it, address) } },
                        tint = colors.text_tertiary,
                        icon_size = 18,
                    )
                }
            }
            }
        }
    }
}
