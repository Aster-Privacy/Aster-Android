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

import compose.icons.TablerIcons
import compose.icons.tablericons.*

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material3.Icon
import android.widget.Toast
import androidx.compose.ui.platform.LocalContext
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import org.astermail.android.R
import org.astermail.android.api.preferences.UserPreferences
import org.astermail.android.api.security.AuditEvent
import org.astermail.android.api.security.HardwareKey
import org.astermail.android.api.security.TrustedDevice
import org.astermail.android.design.AsterMaterial
import org.astermail.android.design.AsterSpacing
import org.astermail.android.design.SquircleShape
import org.astermail.android.design.components.AsterAlertDialog
import org.astermail.android.design.components.AsterCard
import org.astermail.android.design.components.shimmer_brush
import org.astermail.android.design.components.AsterDivider
import org.astermail.android.design.components.AsterIconButton
import org.astermail.android.design.components.AsterSecondaryButton
import org.astermail.android.design.components.AsterSwitch
import org.astermail.android.security.AppLockStore
import org.astermail.android.security.AppLockViewModel
import org.astermail.android.settings.SettingsViewModel
import org.astermail.android.ui.security.AppLockSetupSheet
import org.astermail.android.ui.security.AppLockVerifySheet
import org.astermail.android.settings.shared_settings_view_model

private fun format_audit_event(type: String): String = type
    .replace("_", " ")
    .split(" ")
    .joinToString(" ") { it.replaceFirstChar { c -> c.uppercase() } }

private fun audit_icon(event_type: String): ImageVector = when {
    event_type.contains("login") || event_type.contains("sign_in") -> TablerIcons.Login
    event_type.contains("logout") || event_type.contains("sign_out") -> TablerIcons.Logout
    event_type.contains("password") -> TablerIcons.Lock
    event_type.contains("two_factor") || event_type.contains("totp") || event_type.contains("2fa") -> TablerIcons.ShieldCheck
    event_type.contains("key") || event_type.contains("passkey") -> TablerIcons.Key
    event_type.contains("session") -> TablerIcons.Devices
    event_type.contains("recovery") -> TablerIcons.Key
    event_type.contains("fail") || event_type.contains("block") || event_type.contains("deny") -> TablerIcons.AlertTriangle
    else -> TablerIcons.Shield
}

@Composable
fun SecurityScreen(
    on_back: () -> Unit,
    on_open: (id: String) -> Unit = {},
) {
    val vm: SettingsViewModel = shared_settings_view_model()
    val state by vm.state.collectAsStateWithLifecycle()
    val colors = AsterMaterial.colors
    val context = LocalContext.current

    val lock_vm: AppLockViewModel = hiltViewModel()

    LaunchedEffect(Unit) {
        vm.load_security_status()
        vm.load_login_alerts()
        vm.load_recovery_email()
        vm.load_hardware_keys()
        vm.load_trusted_devices()
        vm.load_audit_log()
        vm.load_vanguard_status()
    }

    LaunchedEffect(state.action_result) {
        val msg = state.action_result ?: return@LaunchedEffect
        Toast.makeText(context, msg, Toast.LENGTH_SHORT).show()
        vm.clear_action_result()
    }

    val sec = state.security_status
    val prefs = state.preferences
    val recovery_email_verified = state.recovery_email_verified
    val hardware_keys_count = state.hardware_keys.size
    val score_loaded = sec != null && prefs != null && state.login_alerts_enabled != null

    val score = if (!score_loaded) null else run {
        var s = 0
        if (sec?.totp_enabled == true) s++
        if (hardware_keys_count > 0) s++
        if (recovery_email_verified) s++
        if (state.login_alerts_enabled == true) s++
        if (prefs?.block_tracking_pixels == true) s++
        if (prefs?.block_external_images == true) s++
        if (prefs?.strip_exif_on_compose == true) s++
        if (prefs?.send_read_receipts == false) s++
        s
    }

    val score_label = when (score) {
        null -> "…"
        in 0..2 -> stringResource(R.string.score_weak)
        in 3..5 -> stringResource(R.string.score_fair)
        in 6..7 -> stringResource(R.string.score_partial)
        else -> stringResource(R.string.score_strong)
    }
    val score_color = when (score) {
        null -> colors.text_muted
        in 0..2 -> colors.danger
        in 3..5 -> colors.warning
        in 6..7 -> Color(0xFFD97706)
        else -> colors.success
    }

    fun toggle(update: (UserPreferences) -> UserPreferences) {
        val current = prefs ?: UserPreferences()
        vm.save_preferences(update(current))
    }

    var score_expanded by remember { mutableStateOf(false) }
    var hardware_keys_expanded by remember { mutableStateOf(false) }
    val totp_sub = when {
        sec == null -> stringResource(R.string.two_factor_subtitle_add)
        sec.totp_enabled -> stringResource(R.string.enabled)
        else -> stringResource(R.string.disabled)
    }
    val recovery_email_sub = when {
        sec == null -> stringResource(R.string.backup_email_short)
        recovery_email_verified -> {
            val addr = state.recovery_email_address
            if (!addr.isNullOrBlank()) "$addr · ${stringResource(R.string.recovery_email_status_verified)}"
            else stringResource(R.string.recovery_email_status_verified)
        }
        state.recovery_email_set -> {
            val addr = state.recovery_email_address
            if (!addr.isNullOrBlank()) "$addr · ${stringResource(R.string.recovery_email_status_unverified)}"
            else stringResource(R.string.recovery_email_status_unverified)
        }
        else -> stringResource(R.string.backup_email_short)
    }

    detail_scaffold(title = stringResource(R.string.security), on_back = on_back) {

        section_label(stringResource(R.string.section_account_protection))
        AsterCard(modifier = Modifier.fillMaxWidth()) {
            Column(
                modifier = Modifier
                    .padding(AsterSpacing.md)
                    .clickable { score_expanded = !score_expanded },
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = stringResource(R.string.protection_score),
                        color = colors.text_primary,
                        fontSize = 15.sp,
                        fontWeight = FontWeight.SemiBold,
                    )
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        if (score == null) {
                            skeleton_block(shimmer_brush(), 34.dp, 15.dp)
                            Spacer(Modifier.width(AsterSpacing.xs))
                            skeleton_block(shimmer_brush(), 52.dp, 17.dp, corner = 6.dp)
                        } else {
                            Text(
                                text = "$score / 8",
                                color = score_color,
                                fontSize = 15.sp,
                                fontWeight = FontWeight.Bold,
                            )
                            Spacer(Modifier.width(AsterSpacing.xs))
                            Box(
                                modifier = Modifier
                                    .background(score_color.copy(alpha = 0.15f), SquircleShape(6.dp))
                                    .padding(horizontal = AsterSpacing.xs, vertical = 2.dp),
                            ) {
                                Text(
                                    text = score_label,
                                    color = score_color,
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.SemiBold,
                                )
                            }
                            Spacer(Modifier.width(AsterSpacing.xs))
                            Icon(
                                imageVector = if (score_expanded) TablerIcons.ChevronUp else TablerIcons.ChevronDown,
                                contentDescription = null,
                                tint = colors.text_muted,
                                modifier = Modifier.size(18.dp),
                            )
                        }
                    }
                }
                v_gap(AsterSpacing.sm)
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(6.dp)
                        .clip(CircleShape)
                        .background(colors.border_primary),
                ) {
                    if (score != null) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth(fraction = (score / 8f).coerceIn(0f, 1f))
                                .height(6.dp)
                                .clip(CircleShape)
                                .background(score_color),
                        )
                    } else {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(6.dp)
                                .clip(CircleShape)
                                .background(shimmer_brush()),
                        )
                    }
                }
                AnimatedVisibility(
                    visible = score_expanded && score != null,
                    enter = expandVertically() + fadeIn(),
                    exit = shrinkVertically() + fadeOut(),
                ) {
                    Column(modifier = Modifier.padding(top = AsterSpacing.md)) {
                        score_checklist_row(stringResource(R.string.two_factor_auth), sec?.totp_enabled == true, colors) { on_open("two_factor") }
                        score_checklist_row(stringResource(R.string.check_passkey_registered), hardware_keys_count > 0, colors) { on_open("encryption") }
                        score_checklist_row(stringResource(R.string.check_verified_recovery_email), recovery_email_verified, colors) { on_open("recovery_email") }
                        score_checklist_row(stringResource(R.string.login_alerts), state.login_alerts_enabled == true, colors) { vm.set_login_alerts(state.login_alerts_enabled != true) }
                        score_checklist_row(stringResource(R.string.block_tracking_pixels), prefs?.block_tracking_pixels == true, colors) { toggle { it.copy(block_tracking_pixels = it.block_tracking_pixels != true) } }
                        score_checklist_row(stringResource(R.string.block_remote_images), prefs?.block_external_images == true, colors) { toggle { it.copy(block_external_images = it.block_external_images != true) } }
                        score_checklist_row(stringResource(R.string.strip_exif), prefs?.strip_exif_on_compose == true, colors) { toggle { it.copy(strip_exif = it.strip_exif_on_compose != true, strip_exif_on_compose = it.strip_exif_on_compose != true) } }
                        score_checklist_row(stringResource(R.string.check_read_receipts_off), prefs?.send_read_receipts == false, colors) { toggle { it.copy(send_read_receipts = !it.send_read_receipts) } }
                    }
                }
            }
        }

        v_gap(AsterSpacing.lg)

        section_label(stringResource(R.string.section_authentication))
        AsterCard(modifier = Modifier.fillMaxWidth()) {
            detail_row(
                title = stringResource(R.string.change_password),
                subtitle = stringResource(R.string.change_password_subtitle),
                icon = TablerIcons.Lock,
                on_click = { on_open("change_password") },
            )
            AsterDivider()
            detail_row(
                title = stringResource(R.string.two_factor_auth),
                subtitle = totp_sub,
                icon = TablerIcons.ShieldCheck,
                on_click = { on_open("two_factor") },
            )
            AsterDivider()
            detail_row(
                title = stringResource(R.string.login_alerts),
                subtitle = stringResource(R.string.login_alerts_subtitle),
                icon = TablerIcons.BellRinging,
                info_title = stringResource(R.string.login_alerts_info_title),
                info_description = stringResource(R.string.login_alerts_info_desc),
                trailing = {
                    AsterSwitch(
                        checked = state.login_alerts_enabled == true,
                        onCheckedChange = { v -> vm.set_login_alerts(v) },
                        enabled = state.login_alerts_enabled != null,
                    )
                },
            )
            AsterDivider()
            detail_row(
                title = stringResource(R.string.active_sessions),
                subtitle = stringResource(R.string.devices_signed_in),
                icon = TablerIcons.Devices,
                on_click = { on_open("sessions") },
            )
            AsterDivider()
            if (hardware_keys_count > 0) {
                detail_row(
                    title = stringResource(R.string.passkeys_security_keys),
                    subtitle = androidx.compose.ui.res.pluralStringResource(R.plurals.passkeys_registered_count, hardware_keys_count, hardware_keys_count),
                    icon = TablerIcons.Key,
                    trailing = {
                        AsterIconButton(
                            icon = if (hardware_keys_expanded) TablerIcons.ChevronUp else TablerIcons.ChevronDown,
                            content_description = null,
                            onClick = { hardware_keys_expanded = !hardware_keys_expanded },
                        )
                    },
                )
                AnimatedVisibility(
                    visible = hardware_keys_expanded,
                    enter = expandVertically() + fadeIn(),
                    exit = shrinkVertically() + fadeOut(),
                ) {
                    Column {
                        state.hardware_keys.forEach { key ->
                            AsterDivider()
                            hardware_key_row(
                                key = key,
                                on_delete = { vm.delete_hardware_key(key.id) },
                                on_rename = { new_name -> vm.rename_hardware_key(key.id, new_name) },
                                colors = colors,
                            )
                        }
                    }
                }
            } else {
                AsterDivider()
                detail_row(
                    title = stringResource(R.string.passkeys_security_keys),
                    subtitle = stringResource(R.string.passkeys_none_subtitle),
                    icon = TablerIcons.Key,
                )
            }
        }

        v_gap(AsterSpacing.lg)

        section_label(stringResource(R.string.section_trusted_devices))
        AsterCard(modifier = Modifier.fillMaxWidth()) {
            if (state.trusted_devices.isEmpty()) {
                detail_row(
                    title = stringResource(R.string.no_trusted_devices),
                    subtitle = stringResource(R.string.no_trusted_devices_subtitle),
                    icon = TablerIcons.Shield,
                )
            } else {
                state.trusted_devices.forEachIndexed { idx, device ->
                    trusted_device_row(
                        device = device,
                        on_revoke = { vm.revoke_trusted_device(device.id) },
                        colors = colors,
                    )
                    if (idx < state.trusted_devices.lastIndex) AsterDivider()
                }
            }
        }
        if (state.trusted_devices.isNotEmpty()) {
            v_gap(AsterSpacing.sm)
            AsterSecondaryButton(
                label = stringResource(R.string.revoke_all_trusted_devices),
                onClick = { vm.revoke_all_trusted_devices() },
                modifier = Modifier.fillMaxWidth(),
            )
        }

        v_gap(AsterSpacing.lg)

        section_label(stringResource(R.string.section_content_protection))
        AsterCard(modifier = Modifier.fillMaxWidth()) {
            if (prefs == null) {
                Box(
                    modifier = Modifier.fillMaxWidth().padding(AsterSpacing.xl),
                    contentAlignment = Alignment.Center,
                ) {
                    androidx.compose.material3.CircularProgressIndicator(color = colors.accent_blue, modifier = Modifier.size(24.dp))
                }
            } else {
                detail_row(
                    title = stringResource(R.string.block_remote_images),
                    subtitle = stringResource(R.string.block_remote_images_subtitle_security),
                    icon = TablerIcons.PhotoOff,
                    info_title = stringResource(R.string.block_remote_images_info_title),
                    info_description = stringResource(R.string.block_remote_images_info_desc),
                    trailing = {
                        AsterSwitch(
                            checked = prefs.block_external_images != false,
                            onCheckedChange = { v -> toggle { it.copy(block_external_images = v) } },
                        )
                    },
                )
                AsterDivider()
                detail_row(
                    title = stringResource(R.string.block_tracking_pixels),
                    subtitle = stringResource(R.string.block_tracking_pixels_subtitle_security),
                    icon = TablerIcons.Target,
                    info_title = stringResource(R.string.block_tracking_pixels_info_title),
                    info_description = stringResource(R.string.block_tracking_pixels_info_desc),
                    trailing = {
                        AsterSwitch(
                            checked = prefs.block_tracking_pixels != false,
                            onCheckedChange = { v -> toggle { it.copy(block_tracking_pixels = v) } },
                        )
                    },
                )
                AsterDivider()
                detail_row(
                    title = stringResource(R.string.block_tracking_links),
                    subtitle = stringResource(R.string.block_tracking_links_subtitle),
                    icon = TablerIcons.Shield,
                    info_title = stringResource(R.string.block_tracking_links_info_title),
                    info_description = stringResource(R.string.block_tracking_links_info_desc),
                    trailing = {
                        AsterSwitch(
                            checked = prefs.block_tracking_links != false,
                            onCheckedChange = { v -> toggle { it.copy(block_tracking_links = v) } },
                        )
                    },
                )
                AsterDivider()
                detail_row(
                    title = stringResource(R.string.warn_suspicious_links),
                    subtitle = stringResource(R.string.warn_suspicious_links_subtitle),
                    icon = TablerIcons.AlertCircle,
                    info_title = stringResource(R.string.warn_suspicious_links_info_title),
                    info_description = stringResource(R.string.warn_suspicious_links_info_desc),
                    trailing = {
                        AsterSwitch(
                            checked = prefs.warn_suspicious_links != false,
                            onCheckedChange = { v -> toggle { it.copy(warn_suspicious_links = v) } },
                        )
                    },
                )
            }
        }

        v_gap(AsterSpacing.lg)

        vanguard_section(vm = vm, lock_vm = lock_vm, on_upgrade = { on_open("billing") })

        v_gap(AsterSpacing.lg)

        section_label(stringResource(R.string.section_privacy_security))
        AsterCard(modifier = Modifier.fillMaxWidth()) {
            if (prefs == null) {
                Box(
                    modifier = Modifier.fillMaxWidth().padding(AsterSpacing.xl),
                    contentAlignment = Alignment.Center,
                ) {
                    androidx.compose.material3.CircularProgressIndicator(color = colors.accent_blue, modifier = Modifier.size(24.dp))
                }
            } else {
                detail_row(
                    title = stringResource(R.string.strip_exif),
                    subtitle = stringResource(R.string.strip_exif_subtitle),
                    icon = TablerIcons.ShieldLock,
                    info_title = stringResource(R.string.strip_exif_info_title),
                    info_description = stringResource(R.string.strip_exif_info_desc),
                    trailing = {
                        AsterSwitch(
                            checked = prefs.strip_exif_on_compose != false,
                            onCheckedChange = { v -> toggle { it.copy(strip_exif = v, strip_exif_on_compose = v) } },
                        )
                    },
                )
                AsterDivider()
                detail_row(
                    title = stringResource(R.string.send_read_receipts),
                    subtitle = stringResource(R.string.send_read_receipts_subtitle),
                    icon = TablerIcons.Eye,
                    trailing = {
                        AsterSwitch(
                            checked = prefs.send_read_receipts == true,
                            onCheckedChange = { v -> toggle { it.copy(send_read_receipts = v) } },
                        )
                    },
                )
            }
        }

        v_gap(AsterSpacing.lg)

        section_label(stringResource(R.string.section_recent_activity))
        AsterCard(modifier = Modifier.fillMaxWidth()) {
            if (state.audit_events.isEmpty()) {
                detail_row(
                    title = stringResource(R.string.no_recent_activity),
                    subtitle = stringResource(R.string.no_recent_activity_subtitle),
                    icon = TablerIcons.History,
                )
            } else {
                state.audit_events.forEachIndexed { idx, event ->
                    audit_event_row(event = event, colors = colors)
                    if (idx < state.audit_events.lastIndex) AsterDivider()
                }
            }
        }

        v_gap(AsterSpacing.lg)

        section_label(stringResource(R.string.section_recovery_security))
        AsterCard(modifier = Modifier.fillMaxWidth()) {
            detail_row(
                title = stringResource(R.string.recovery_key),
                subtitle = stringResource(R.string.backup_access),
                icon = TablerIcons.Key,
                on_click = { on_open("recovery_key_view") },
            )
            AsterDivider()
            detail_row(
                title = stringResource(R.string.recovery_email),
                subtitle = recovery_email_sub,
                icon = TablerIcons.At,
                on_click = { on_open("recovery_email") },
            )
        }

        v_gap(AsterSpacing.lg)

        section_label(stringResource(R.string.section_account_security))
        AsterCard(modifier = Modifier.fillMaxWidth()) {
            detail_row(
                title = stringResource(R.string.blocked_senders),
                subtitle = stringResource(R.string.blocked_senders_subtitle_security),
                icon = TablerIcons.Ban,
                on_click = { on_open("blocked") },
            )
            AsterDivider()
            detail_row(
                title = stringResource(R.string.encryption_keys),
                subtitle = stringResource(R.string.encryption_keys_subtitle),
                icon = TablerIcons.Shield,
                on_click = { on_open("encryption") },
            )
        }

        v_gap(AsterSpacing.lg)

        AsterCard(modifier = Modifier.fillMaxWidth()) {
            detail_row(
                title = stringResource(R.string.delete_account),
                subtitle = stringResource(R.string.delete_account_subtitle),
                icon = TablerIcons.TrashOff,
                on_click = { on_open("delete_account") },
            )
        }
        v_gap(AsterSpacing.xxl)
    }
}

private enum class AppLockModal { setup, verify_to_change, change, disable }

@Composable
private fun vanguard_section(
    vm: SettingsViewModel,
    lock_vm: AppLockViewModel,
    on_upgrade: () -> Unit,
) {
    val colors = AsterMaterial.colors
    val state by vm.state.collectAsStateWithLifecycle()
    val store = lock_vm.store

    val plan_name = state.subscription?.effective_plan_name?.lowercase() ?: ""
    val is_nova_plus = plan_name.contains("nova") || plan_name.contains("supernova")
    val vanguard_enabled = state.vanguard_enabled == true

    var show_disable_confirm by remember { mutableStateOf(false) }
    var app_lock_enabled by remember { mutableStateOf(store.is_configured()) }
    var modal by remember { mutableStateOf<AppLockModal?>(null) }

    section_label(stringResource(R.string.section_vanguard))

    AsterCard(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(AsterSpacing.md)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(modifier = Modifier.weight(1f).padding(end = AsterSpacing.md)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            text = stringResource(R.string.vanguard_enable),
                            color = colors.text_primary,
                            fontSize = 15.sp,
                            fontWeight = FontWeight.Medium,
                        )
                        if (vanguard_enabled) {
                            Spacer(Modifier.width(AsterSpacing.xs))
                            Box(
                                modifier = Modifier
                                    .background(colors.success.copy(alpha = 0.15f), SquircleShape(4.dp))
                                    .padding(horizontal = 6.dp, vertical = 2.dp),
                            ) {
                                Text(
                                    text = stringResource(R.string.vanguard_active),
                                    color = colors.success,
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.SemiBold,
                                )
                            }
                        }
                    }
                    Spacer(Modifier.height(2.dp))
                    Text(
                        text = stringResource(R.string.vanguard_description),
                        color = colors.text_muted,
                        fontSize = 13.sp,
                    )
                }
                if (state.vanguard_enabled == null) {
                    androidx.compose.material3.CircularProgressIndicator(
                        modifier = Modifier.size(20.dp),
                        strokeWidth = 2.dp,
                        color = colors.text_muted,
                    )
                } else if (is_nova_plus || state.subscription == null) {
                    AsterSwitch(
                        checked = vanguard_enabled,
                        onCheckedChange = { v ->
                            if (v) vm.enable_vanguard()
                            else show_disable_confirm = true
                        },
                        enabled = state.vanguard_enabled != null,
                    )
                } else {
                    Box(
                        modifier = Modifier
                            .clip(SquircleShape(10.dp))
                            .background(colors.accent_blue)
                            .clickable(onClick = on_upgrade)
                            .padding(horizontal = AsterSpacing.md, vertical = AsterSpacing.xs),
                    ) {
                        Text(
                            text = stringResource(R.string.vanguard_upgrade_cta),
                            color = Color.White,
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Medium,
                        )
                    }
                }
            }

            AnimatedVisibility(
                visible = vanguard_enabled,
                enter = expandVertically() + fadeIn(),
                exit = shrinkVertically() + fadeOut(),
            ) {
                Column(
                    modifier = Modifier
                        .padding(top = AsterSpacing.md)
                        .padding(start = AsterSpacing.md)
                        .border(
                            width = 2.dp,
                            color = colors.accent_blue.copy(alpha = 0.25f),
                            shape = androidx.compose.foundation.shape.RoundedCornerShape(topStart = 2.dp, bottomStart = 2.dp),
                        )
                        .padding(start = AsterSpacing.md),
                ) {
                    app_lock_subsection(
                        store = store,
                        enabled = app_lock_enabled,
                        on_toggle = { want ->
                            if (want) modal = AppLockModal.setup
                            else modal = AppLockModal.disable
                        },
                        on_change_pin = { modal = AppLockModal.verify_to_change },
                    )
                }
            }
        }
    }

    if (show_disable_confirm) {
        AsterAlertDialog(
            on_dismiss = { show_disable_confirm = false },
            title = stringResource(R.string.vanguard_confirm_disable_title),
            message = stringResource(R.string.vanguard_confirm_disable_desc),
            confirm_label = stringResource(R.string.vanguard_disable),
            cancel_label = stringResource(R.string.cancel),
            confirm_style = org.astermail.android.design.components.DialogConfirmStyle.destructive,
            on_confirm = {
                show_disable_confirm = false
                store.disable()
                app_lock_enabled = false
                vm.disable_vanguard()
            },
        )
    }

    when (modal) {
        AppLockModal.setup, AppLockModal.change -> AppLockSetupSheet(
            store = store,
            on_dismiss = { modal = null },
            on_success = { app_lock_enabled = true; modal = null },
        )
        AppLockModal.verify_to_change -> AppLockVerifySheet(
            store = store,
            description = stringResource(R.string.app_lock_enter_to_change),
            on_dismiss = { modal = null },
            on_success = { modal = AppLockModal.change },
        )
        AppLockModal.disable -> AppLockVerifySheet(
            store = store,
            description = stringResource(R.string.app_lock_enter_to_disable),
            on_dismiss = { modal = null },
            on_success = { store.disable(); app_lock_enabled = false; modal = null },
        )
        null -> {}
    }
}

@Composable
private fun app_lock_subsection(
    store: AppLockStore,
    enabled: Boolean,
    on_toggle: (Boolean) -> Unit,
    on_change_pin: () -> Unit,
) {
    val colors = AsterMaterial.colors
    Column(modifier = Modifier.fillMaxWidth().padding(vertical = AsterSpacing.sm)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(modifier = Modifier.weight(1f).padding(end = AsterSpacing.sm)) {
                Text(
                    text = stringResource(R.string.app_lock_pin),
                    color = colors.text_primary,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Medium,
                )
                Spacer(Modifier.height(2.dp))
                Text(
                    text = stringResource(R.string.app_lock_pin_description),
                    color = colors.text_muted,
                    fontSize = 12.sp,
                )
            }
            AsterSwitch(
                checked = enabled,
                onCheckedChange = on_toggle,
            )
        }
        if (enabled) {
            Spacer(Modifier.height(AsterSpacing.xs))
            Text(
                text = stringResource(R.string.app_lock_change_pin),
                color = colors.accent_blue,
                fontSize = 13.sp,
                modifier = Modifier.clickable(onClick = on_change_pin),
            )
        }
    }
}

@Composable
private fun score_checklist_row(
    label: String,
    checked: Boolean,
    colors: org.astermail.android.design.AsterSemanticColors,
    on_click: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(SquircleShape(8.dp))
            .clickable(onClick = on_click)
            .padding(vertical = 5.dp, horizontal = 2.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (checked) {
            Icon(
                imageVector = TablerIcons.CircleCheck,
                contentDescription = null,
                tint = colors.success,
                modifier = Modifier.size(17.dp),
            )
        } else {
            Box(
                modifier = Modifier
                    .size(17.dp)
                    .border(1.5.dp, colors.text_muted, CircleShape),
            )
        }
        Spacer(Modifier.width(AsterSpacing.sm))
        Text(
            text = label,
            color = if (checked) colors.text_primary else colors.text_tertiary,
            fontSize = 13.sp,
            modifier = Modifier.weight(1f),
        )
        Icon(
            imageVector = TablerIcons.ChevronRight,
            contentDescription = null,
            tint = colors.text_muted,
            modifier = Modifier.size(14.dp),
        )
    }
}

@Composable
private fun hardware_key_row(
    key: HardwareKey,
    on_delete: () -> Unit,
    on_rename: (String) -> Unit,
    colors: org.astermail.android.design.AsterSemanticColors,
) {
    var show_rename by remember(key.id) { mutableStateOf(false) }
    var rename_text by remember(key.id) { mutableStateOf(key.display_name) }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = AsterSpacing.md, vertical = AsterSpacing.sm),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            imageVector = TablerIcons.Key,
            contentDescription = null,
            tint = colors.text_secondary,
            modifier = Modifier.size(20.dp),
        )
        Spacer(Modifier.width(AsterSpacing.md))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = key.display_name.ifBlank { stringResource(R.string.hardware_key_default_name) },
                color = colors.text_primary,
                fontSize = 14.sp,
                fontWeight = FontWeight.Medium,
            )
            Text(
                text = relative_time_label(key.created_at),
                color = colors.text_tertiary,
                fontSize = 12.sp,
            )
        }
        AsterIconButton(
            icon = TablerIcons.Edit,
            content_description = stringResource(R.string.hardware_key_rename),
            onClick = {
                rename_text = key.display_name
                show_rename = true
            },
            tint = colors.text_secondary,
        )
        AsterIconButton(
            icon = TablerIcons.Trash,
            content_description = stringResource(R.string.hardware_key_remove),
            onClick = on_delete,
            tint = colors.danger,
        )
    }

    if (show_rename) {
        AsterAlertDialog(
            on_dismiss = { show_rename = false },
            title = stringResource(R.string.hardware_key_rename),
            confirm_label = stringResource(R.string.save),
            cancel_label = stringResource(R.string.cancel),
            confirm_enabled = rename_text.isNotBlank(),
            on_confirm = {
                show_rename = false
                on_rename(rename_text.trim())
            },
            extra_content = {
                org.astermail.android.design.components.AsterTextField(
                    value = rename_text,
                    onValueChange = { if (it.length <= 128) rename_text = it },
                    singleLine = true,
                    placeholder = stringResource(R.string.hardware_key_rename_placeholder),
                    modifier = Modifier.fillMaxWidth(),
                )
            },
        )
    }
}

@Composable
private fun trusted_device_row(
    device: TrustedDevice,
    on_revoke: () -> Unit,
    colors: org.astermail.android.design.AsterSemanticColors,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = AsterSpacing.md, vertical = AsterSpacing.sm),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            imageVector = TablerIcons.Devices,
            contentDescription = null,
            tint = colors.text_secondary,
            modifier = Modifier.size(20.dp),
        )
        Spacer(Modifier.width(AsterSpacing.md))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = org.astermail.android.ui.settings.clean_trusted_device_label(device.label)
                    .ifBlank { stringResource(R.string.trusted_device_default_label) },
                color = colors.text_primary,
                fontSize = 14.sp,
                fontWeight = FontWeight.Medium,
            )
            val expires_label = stringResource(R.string.trusted_device_expires)
            val meta = buildList {
                val ip = device.ip_snippet
                if (!ip.isNullOrBlank()) add(ip)
                val expires = device.expires_at
                if (!expires.isNullOrBlank()) add("$expires_label ${relative_time_label(expires)}")
            }.joinToString(" - ")
            if (meta.isNotBlank()) {
                Text(
                    text = meta,
                    color = colors.text_tertiary,
                    fontSize = 12.sp,
                )
            }
        }
        AsterIconButton(
            icon = TablerIcons.Trash,
            content_description = stringResource(R.string.trusted_device_revoke),
            onClick = on_revoke,
            tint = colors.danger,
        )
    }
}

@Composable
private fun audit_event_row(
    event: AuditEvent,
    colors: org.astermail.android.design.AsterSemanticColors,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = AsterSpacing.md, vertical = AsterSpacing.sm),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            imageVector = audit_icon(event.event_type),
            contentDescription = null,
            tint = colors.text_secondary,
            modifier = Modifier.size(20.dp),
        )
        Spacer(Modifier.width(AsterSpacing.md))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = format_audit_event(event.event_type),
                color = colors.text_primary,
                fontSize = 14.sp,
                fontWeight = FontWeight.Medium,
            )
            val meta = buildList {
                add(relative_time_label(event.created_at))
                val ip = event.ip_address
                if (!ip.isNullOrBlank()) add(ip)
            }.joinToString(" - ")
            Text(
                text = meta,
                color = colors.text_tertiary,
                fontSize = 12.sp,
            )
        }
    }
}
