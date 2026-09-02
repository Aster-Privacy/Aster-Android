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
import androidx.compose.foundation.horizontalScroll
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material3.Icon
import android.widget.Toast
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
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
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlinx.coroutines.delay
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
import org.astermail.android.design.components.AsterGhostButton
import org.astermail.android.design.components.AsterIconButton
import org.astermail.android.design.components.AsterSwitch
import org.astermail.android.design.components.aster_dropdown_item
import org.astermail.android.design.components.aster_dropdown_menu
import org.astermail.android.security.AppLockStore
import org.astermail.android.security.AppLockViewModel
import org.astermail.android.settings.SettingsViewModel
import org.astermail.android.ui.security.AppLockSetupSheet
import org.astermail.android.ui.security.AppLockVerifySheet
import org.astermail.android.settings.shared_settings_view_model
import org.astermail.android.design.mirror_in_rtl

private const val activity_preview_count = 5
private const val security_settle_delay_ms = 90L
private const val security_load_timeout_ms = 5000L

@Composable
private fun format_audit_event(type: String): String {
    val label = when (type) {
        "login" -> R.string.audit_event_login
        "logout" -> R.string.audit_event_logout
        "password_change" -> R.string.audit_event_password_change
        "session_revoked" -> R.string.audit_event_session_revoked
        "settings_changed" -> R.string.audit_event_settings_changed
        "lockdown_enabled" -> R.string.audit_event_lockdown_enabled
        "lockdown_disabled" -> R.string.audit_event_lockdown_disabled
        "suspicious_activity" -> R.string.audit_event_suspicious_activity
        "account_locked" -> R.string.audit_event_account_locked
        "2fa_enabled" -> R.string.audit_event_2fa_enabled
        "2fa_disabled" -> R.string.audit_event_2fa_disabled
        "key_rotated" -> R.string.audit_event_key_rotated
        "key_exported" -> R.string.audit_event_key_exported
        "device_removed" -> R.string.audit_event_device_removed
        "recovery_codes_regenerated" -> R.string.audit_event_recovery_codes_regenerated
        else -> null
    }
    if (label != null) return stringResource(label)
    return type.replace("_", " ").replaceFirstChar { it.uppercase() }
}

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
internal fun security_choice_row(
    label: String,
    selected: Boolean,
    test_tag: String,
    on_click: () -> Unit,
) {
    val colors = AsterMaterial.colors
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = on_click)
            .testTag(test_tag)
            .padding(start = AsterSpacing.xl + AsterSpacing.md, end = AsterSpacing.md, top = AsterSpacing.sm, bottom = AsterSpacing.sm),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(label, color = colors.text_primary, fontSize = 15.sp, modifier = Modifier.weight(1f))
        if (selected) {
            Box(
                modifier = Modifier.size(20.dp).background(colors.accent_blue, CircleShape),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    imageVector = TablerIcons.Check,
                    contentDescription = null,
                    tint = Color.White,
                    modifier = Modifier.size(13.dp),
                )
            }
        }
    }
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
        vm.load_subscription(force = false)
        vm.load_inactive_key_sets()
    }

    var show_recover_dialog by remember { mutableStateOf(false) }
    var recover_password by remember { mutableStateOf("") }
    var show_discard_confirm by remember { mutableStateOf(false) }

    LaunchedEffect(state.action_result) {
        val msg = state.action_result ?: return@LaunchedEffect
        Toast.makeText(context, msg, Toast.LENGTH_SHORT).show()
        vm.clear_action_result()
    }

    val sec = state.security_status
    val prefs = state.preferences
    val recovery_email_verified = state.recovery_email_verified
    val hardware_keys_count = state.hardware_keys.size

    val security_signals_ready = sec != null &&
        prefs != null &&
        state.login_alerts_enabled != null &&
        state.vanguard_enabled != null &&
        !state.is_loading

    var content_ready by remember { mutableStateOf(false) }
    LaunchedEffect(security_signals_ready) {
        if (security_signals_ready) {
            delay(security_settle_delay_ms)
            content_ready = true
        }
    }
    LaunchedEffect(Unit) {
        delay(security_load_timeout_ms)
        content_ready = true
    }

    val score_loaded = content_ready && sec != null && prefs != null && state.login_alerts_enabled != null

    val score = if (!score_loaded) null else run {
        var s = 0
        if (sec?.totp_enabled == true) s++
        if (hardware_keys_count > 0) s++
        if (recovery_email_verified) s++
        if (state.login_alerts_enabled == true) s++
        if (prefs?.block_tracking_pixels == true) s++
        if (prefs?.block_external_images == true) s++
        if (prefs?.strip_exif_on_compose == true) s++
        s
    }

    val score_label = when (score) {
        null -> "…"
        in 0..2 -> stringResource(R.string.score_weak)
        in 3..4 -> stringResource(R.string.score_fair)
        in 5..6 -> stringResource(R.string.score_partial)
        else -> stringResource(R.string.score_strong)
    }
    val score_color = when (score) {
        null -> colors.text_muted
        in 0..2 -> colors.danger
        in 3..4 -> colors.warning
        in 5..6 -> Color(0xFFD97706)
        else -> colors.success
    }

    fun toggle(update: (UserPreferences) -> UserPreferences) {
        val current = prefs ?: return
        if (!state.preferences_authoritative) {
            vm.report_preferences_locked()

            return
        }
        vm.save_preferences(update(current))
    }

    var score_expanded by remember { mutableStateOf(false) }
    var hardware_keys_expanded by remember { mutableStateOf(false) }
    var show_revoke_all_confirm by remember { mutableStateOf(false) }
    val scroll_state = rememberScrollState()
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

    detail_scaffold(
        title = stringResource(R.string.security),
        on_back = on_back,
        scroll_state = scroll_state,
    ) {
        if (!content_ready) {
            security_loading_skeleton()
            return@detail_scaffold
        }

        preferences_save_error_banner()
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
                                text = "$score / 7",
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
                                .fillMaxWidth(fraction = (score / 7f).coerceIn(0f, 1f))
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
                        score_checklist_row(stringResource(R.string.block_remote_images), prefs?.block_external_images == true, colors) { toggle { val on = it.block_external_images != true; it.copy(block_external_images = on, load_remote_images = if (on) "never" else "always") } }
                        score_checklist_row(stringResource(R.string.strip_exif), prefs?.strip_exif_on_compose == true, colors) { toggle { it.copy(strip_exif = it.strip_exif_on_compose != true, strip_exif_on_compose = it.strip_exif_on_compose != true) } }
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
            if (state.inactive_key_sets > 0) {
                AsterDivider()
                detail_row(
                    title = stringResource(R.string.recover_older_data_title),
                    subtitle = stringResource(R.string.recover_older_data_desc),
                    icon = TablerIcons.Key,
                    on_click = { show_recover_dialog = true },
                )
            }
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
                    if (state.login_alerts_enabled == null && state.login_alerts_load_failed) {
                        AsterGhostButton(
                            label = stringResource(R.string.retry),
                            onClick = { vm.load_login_alerts() },
                        )
                    } else {
                        AsterSwitch(
                            checked = state.login_alerts_enabled == true,
                            onCheckedChange = { v -> vm.set_login_alerts(v) },
                            enabled = state.login_alerts_enabled != null,
                        )
                    }
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
            if (hardware_keys_count == 0 && state.hardware_keys_load_failed) {
                detail_row(
                    title = stringResource(R.string.passkeys_security_keys),
                    subtitle = stringResource(R.string.failed_to_load),
                    icon = TablerIcons.AlertCircle,
                    on_click = { vm.load_hardware_keys() },
                )
            }
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
            } else if (!state.hardware_keys_load_failed) {
                AsterDivider()
                detail_row(
                    title = stringResource(R.string.passkeys_security_keys),
                    subtitle = stringResource(R.string.passkeys_none_subtitle),
                    icon = TablerIcons.Key,
                )
            }
        }

        v_gap(AsterSpacing.lg)

        section_header_action(
            title = stringResource(R.string.section_trusted_devices),
            action_label = stringResource(R.string.revoke_all_action),
            enabled = state.trusted_devices.isNotEmpty(),
            on_click = { show_revoke_all_confirm = true },
        )
        AsterCard(modifier = Modifier.fillMaxWidth()) {
            if (state.trusted_devices.isEmpty() && state.trusted_devices_load_failed) {
                detail_row(
                    title = stringResource(R.string.failed_to_load),
                    subtitle = stringResource(R.string.retry),
                    icon = TablerIcons.AlertCircle,
                    on_click = { vm.load_trusted_devices() },
                )
            } else if (state.trusted_devices.isEmpty()) {
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

        v_gap(AsterSpacing.lg)

        if (prefs == null) {
            section_label(stringResource(R.string.section_tracking_protection))
            skeleton_card_list(rows = 3)
            v_gap(AsterSpacing.lg)
            section_label(stringResource(R.string.section_images))
            skeleton_card_list(rows = 6)
            v_gap(AsterSpacing.lg)
            section_label(stringResource(R.string.section_html_content))
            skeleton_card_list(rows = 1)
            v_gap(AsterSpacing.lg)
            section_label(stringResource(R.string.section_external_links))
            skeleton_card_list(rows = 1)
        } else {
            section_label(stringResource(R.string.section_tracking_protection))
            AsterCard(modifier = Modifier.fillMaxWidth()) {
                detail_row(
                    title = stringResource(R.string.tracking_protection_enabled),
                    subtitle = stringResource(R.string.tracking_protection_enabled_subtitle),
                    icon = TablerIcons.ShieldCheck,
                    trailing = {
                        AsterSwitch(
                            checked = prefs.block_external_content != false,
                            onCheckedChange = { v ->
                                toggle {
                                    if (v) it.copy(block_external_content = true, block_tracking_pixels = true)
                                    else it.copy(block_external_content = false)
                                }
                            },
                        )
                    },
                )
                if (prefs.block_external_content != false) {
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
                }
            }

            v_gap(AsterSpacing.lg)

            section_label(stringResource(R.string.section_images))
            AsterCard(modifier = Modifier.fillMaxWidth()) {
                detail_row(
                    title = stringResource(R.string.block_remote_images),
                    subtitle = stringResource(R.string.block_remote_images_subtitle_security),
                    icon = TablerIcons.PhotoOff,
                    info_title = stringResource(R.string.block_remote_images_info_title),
                    info_description = stringResource(R.string.block_remote_images_info_desc),
                    trailing = {
                        AsterSwitch(
                            checked = prefs.block_external_images != false,
                            onCheckedChange = { v ->
                                toggle {
                                    it.copy(
                                        block_external_images = v,
                                        load_remote_images = when {
                                            !v -> "always"
                                            it.load_remote_images == "ask" -> "ask"
                                            else -> "never"
                                        },
                                    )
                                }
                            },
                        )
                    },
                )
                AsterDivider()
                remote_image_loading_row(
                    selected_id = prefs.load_remote_images,
                    on_select = { id ->
                        toggle {
                            it.copy(
                                load_remote_images = id,
                                block_external_images = id != "always",
                            )
                        }
                    },
                )
                AsterDivider()
                detail_row(
                    title = stringResource(R.string.block_remote_fonts),
                    subtitle = stringResource(R.string.block_remote_fonts_subtitle),
                    icon = TablerIcons.Typography,
                    trailing = {
                        AsterSwitch(
                            checked = prefs.block_remote_fonts != false,
                            onCheckedChange = { v -> toggle { it.copy(block_remote_fonts = v) } },
                        )
                    },
                )
                AsterDivider()
                detail_row(
                    title = stringResource(R.string.block_remote_css),
                    subtitle = stringResource(R.string.block_remote_css_subtitle),
                    icon = TablerIcons.Palette,
                    trailing = {
                        AsterSwitch(
                            checked = prefs.block_remote_css != false,
                            onCheckedChange = { v -> toggle { it.copy(block_remote_css = v) } },
                        )
                    },
                )
                AsterDivider()
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
            }

            v_gap(AsterSpacing.lg)

            section_label(stringResource(R.string.section_html_content))
            AsterCard(modifier = Modifier.fillMaxWidth()) {
                detail_row(
                    title = stringResource(R.string.block_html_rendering),
                    subtitle = stringResource(R.string.block_html_rendering_subtitle),
                    icon = TablerIcons.Code,
                    trailing = {
                        AsterSwitch(
                            checked = prefs.html_rendering_mode == "plain_text",
                            onCheckedChange = { v ->
                                toggle { it.copy(html_rendering_mode = if (v) "plain_text" else "html") }
                            },
                        )
                    },
                )
            }

            v_gap(AsterSpacing.lg)

            section_label(stringResource(R.string.section_external_links))
            AsterCard(modifier = Modifier.fillMaxWidth()) {
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

        section_label(stringResource(R.string.section_recent_activity))
        recent_activity_section(
            events = state.audit_events,
            load_failed = state.audit_events_load_failed,
            on_retry = { vm.load_audit_log() },
            colors = colors,
        )

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

    if (show_revoke_all_confirm) {
        AsterAlertDialog(
            on_dismiss = { show_revoke_all_confirm = false },
            title = stringResource(R.string.revoke_all_trusted_devices),
            message = stringResource(R.string.revoke_all_trusted_devices_confirm),
            confirm_label = stringResource(R.string.revoke_all_action),
            cancel_label = stringResource(R.string.cancel),
            confirm_style = org.astermail.android.design.components.DialogConfirmStyle.destructive,
            on_confirm = {
                show_revoke_all_confirm = false
                vm.revoke_all_trusted_devices()
            },
        )
    }

    if (show_recover_dialog) {
        org.astermail.android.design.components.AsterDialog(
            on_dismiss = {
                if (!state.restoring_inactive_key_sets) {
                    show_recover_dialog = false
                    recover_password = ""
                }
            },
            title = stringResource(R.string.recover_older_data_title),
            message = stringResource(R.string.resurrection_old_password_prompt),
            body = {
                Column(modifier = Modifier.fillMaxWidth()) {
                    org.astermail.android.design.components.AsterTextField(
                        value = recover_password,
                        onValueChange = { recover_password = it },
                        label = stringResource(R.string.resurrection_old_password),
                        visual_transformation = PasswordVisualTransformation(),
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                    )
                    v_gap(AsterSpacing.md)
                    org.astermail.android.design.components.AsterDialogOutlineButton(
                        label = stringResource(R.string.discard_older_data_button),
                        enabled = !state.restoring_inactive_key_sets,
                        onClick = {
                            show_recover_dialog = false
                            recover_password = ""
                            show_discard_confirm = true
                        },
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
            },
            footer = {
                org.astermail.android.design.components.AsterDialogOutlineButton(
                    label = stringResource(R.string.cancel),
                    enabled = !state.restoring_inactive_key_sets,
                    onClick = {
                        show_recover_dialog = false
                        recover_password = ""
                    },
                )
                org.astermail.android.design.components.AsterDialogPrimaryButton(
                    label = stringResource(R.string.recover_older_data_button),
                    enabled = recover_password.isNotBlank() && !state.restoring_inactive_key_sets,
                    is_loading = state.restoring_inactive_key_sets,
                    onClick = {
                        vm.restore_inactive_key_sets(recover_password)
                        show_recover_dialog = false
                        recover_password = ""
                    },
                )
            },
        )
    }

    if (show_discard_confirm) {
        AsterAlertDialog(
            on_dismiss = { show_discard_confirm = false },
            title = stringResource(R.string.discard_older_data_title),
            message = stringResource(R.string.discard_older_data_desc),
            confirm_label = stringResource(R.string.discard_older_data_confirm),
            cancel_label = stringResource(R.string.cancel),
            confirm_style = org.astermail.android.design.components.DialogConfirmStyle.destructive,
            on_confirm = {
                show_discard_confirm = false
                vm.discard_inactive_key_sets()
            },
        )
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

    val is_nova_plus = is_vanguard_plan(state.subscription)
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
                            verified_badge(stringResource(R.string.vanguard_active))
                        }
                    }
                    Spacer(Modifier.height(2.dp))
                    Text(
                        text = stringResource(R.string.vanguard_description),
                        color = colors.text_muted,
                        fontSize = 13.sp,
                    )
                }
                if (state.vanguard_enabled == null && state.vanguard_status_load_failed) {
                    AsterGhostButton(
                        label = stringResource(R.string.retry),
                        onClick = { vm.load_vanguard_status() },
                    )
                } else if (state.vanguard_enabled == null) {
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
                Column {
                    Spacer(Modifier.height(AsterSpacing.md))
                    AsterDivider()
                    Spacer(Modifier.height(AsterSpacing.md))
                    app_lock_row(
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
                vm.disable_vanguard {
                    store.disable()
                    app_lock_enabled = false
                }
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
private fun app_lock_row(
    store: AppLockStore,
    enabled: Boolean,
    on_toggle: (Boolean) -> Unit,
    on_change_pin: () -> Unit,
) {
    val colors = AsterMaterial.colors
    Column(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(modifier = Modifier.weight(1f).padding(end = AsterSpacing.md)) {
                Text(
                    text = stringResource(R.string.app_lock_pin),
                    color = colors.text_primary,
                    fontSize = 15.sp,
                    fontWeight = FontWeight.Medium,
                )
                Spacer(Modifier.height(2.dp))
                Text(
                    text = stringResource(R.string.app_lock_pin_description),
                    color = colors.text_muted,
                    fontSize = 13.sp,
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
            modifier = Modifier.size(14.dp).mirror_in_rtl(),
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
    var show_delete_confirm by remember(key.id) { mutableStateOf(false) }
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
            onClick = { show_delete_confirm = true },
            tint = colors.danger,
        )
    }

    if (show_delete_confirm) {
        AsterAlertDialog(
            on_dismiss = { show_delete_confirm = false },
            title = stringResource(R.string.hardware_key_remove),
            message = stringResource(R.string.hardware_key_remove_confirm),
            confirm_label = stringResource(R.string.remove),
            cancel_label = stringResource(R.string.cancel),
            confirm_style = org.astermail.android.design.components.DialogConfirmStyle.destructive,
            on_confirm = {
                show_delete_confirm = false
                on_delete()
            },
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
    var show_revoke_confirm by remember(device.id) { mutableStateOf(false) }

    if (show_revoke_confirm) {
        AsterAlertDialog(
            on_dismiss = { show_revoke_confirm = false },
            title = stringResource(R.string.trusted_device_revoke),
            message = stringResource(R.string.trusted_device_revoke_confirm),
            confirm_label = stringResource(R.string.revoke),
            cancel_label = stringResource(R.string.cancel),
            confirm_style = org.astermail.android.design.components.DialogConfirmStyle.destructive,
            on_confirm = {
                show_revoke_confirm = false
                on_revoke()
            },
        )
    }

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
            onClick = { show_revoke_confirm = true },
            tint = colors.danger,
        )
    }
}

private enum class AuditFilter { all, sign_ins, security_changes, failures }

private fun audit_filter_of(event: AuditEvent): AuditFilter {
    val type = event.event_type.lowercase()
    val severity = event.severity.lowercase()
    val is_failure = severity == "critical" ||
        severity == "high" ||
        severity == "error" ||
        severity == "warning" ||
        type.contains("fail") ||
        type.contains("block") ||
        type.contains("denied") ||
        type.contains("suspicious") ||
        type.contains("locked")
    val is_sign_in = type.contains("login") ||
        type.contains("logout") ||
        type.contains("sign_in") ||
        type.contains("sign_out") ||
        type.contains("session")
    return when {
        is_failure -> AuditFilter.failures
        is_sign_in -> AuditFilter.sign_ins
        else -> AuditFilter.security_changes
    }
}

private fun parse_audit_instant(iso: String?): java.time.Instant? {
    if (iso.isNullOrBlank()) return null
    return try {
        java.time.OffsetDateTime.parse(iso).toInstant()
    } catch (_: Throwable) {
        try {
            java.time.Instant.parse(iso)
        } catch (_: Throwable) {
            null
        }
    }
}

private fun audit_device_label(user_agent: String?): String? {
    val agent = user_agent?.trim().orEmpty()
    if (agent.isEmpty()) return null
    val client = when {
        agent.contains("Aster", ignoreCase = true) -> "Aster Mail"
        agent.contains("Edg", ignoreCase = true) -> "Edge"
        agent.contains("OPR", ignoreCase = true) || agent.contains("Opera", ignoreCase = true) -> "Opera"
        agent.contains("Firefox", ignoreCase = true) -> "Firefox"
        agent.contains("Chrome", ignoreCase = true) -> "Chrome"
        agent.contains("Safari", ignoreCase = true) -> "Safari"
        else -> null
    }
    val platform = when {
        agent.contains("Android", ignoreCase = true) -> "Android"
        agent.contains("iPhone", ignoreCase = true) ||
            agent.contains("iPad", ignoreCase = true) ||
            agent.contains("iOS", ignoreCase = true) -> "iOS"
        agent.contains("Macintosh", ignoreCase = true) || agent.contains("Mac OS", ignoreCase = true) -> "macOS"
        agent.contains("Windows", ignoreCase = true) -> "Windows"
        agent.contains("Linux", ignoreCase = true) -> "Linux"
        else -> null
    }
    val label = listOfNotNull(client, platform).joinToString(" - ")
    return label.ifBlank { null }
}

private fun group_audit_events(
    events: List<AuditEvent>,
    zone: java.time.ZoneId,
    today_label: String,
    yesterday_label: String,
    unknown_label: String,
): List<Pair<String, List<AuditEvent>>> {
    val today = java.time.LocalDate.now(zone)
    val formatter = java.time.format.DateTimeFormatter
        .ofLocalizedDate(java.time.format.FormatStyle.MEDIUM)
        .withZone(zone)
    return events.groupBy { event ->
        val instant = parse_audit_instant(event.created_at)
        val date = instant?.atZone(zone)?.toLocalDate()
        when {
            instant == null || date == null -> unknown_label
            date == today -> today_label
            date == today.minusDays(1) -> yesterday_label
            else -> formatter.format(instant)
        }
    }.toList()
}

@Composable
private fun recent_activity_section(
    events: List<AuditEvent>,
    load_failed: Boolean,
    on_retry: () -> Unit,
    colors: org.astermail.android.design.AsterSemanticColors,
) {
    if (events.isEmpty()) {
        AsterCard(modifier = Modifier.fillMaxWidth()) {
            if (load_failed) {
                detail_row(
                    title = stringResource(R.string.failed_to_load),
                    subtitle = stringResource(R.string.retry),
                    icon = TablerIcons.AlertCircle,
                    on_click = on_retry,
                )
            } else {
                detail_row(
                    title = stringResource(R.string.no_recent_activity),
                    subtitle = stringResource(R.string.no_recent_activity_subtitle),
                    icon = TablerIcons.History,
                )
            }
        }
        return
    }

    var selected_filter by remember { mutableStateOf(AuditFilter.all) }
    var expanded by remember { mutableStateOf(false) }

    val present = remember(events) { events.map { audit_filter_of(it) }.toSet() }
    LaunchedEffect(present) {
        if (selected_filter != AuditFilter.all && !present.contains(selected_filter)) selected_filter = AuditFilter.all
    }

    val chips = listOf(
        AuditFilter.all to R.string.security_activity_filter_all,
        AuditFilter.sign_ins to R.string.security_activity_filter_sign_ins,
        AuditFilter.security_changes to R.string.security_activity_filter_security,
        AuditFilter.failures to R.string.security_activity_filter_failures,
    ).filter { it.first == AuditFilter.all || present.contains(it.first) }

    val filtered = remember(events, selected_filter) {
        if (selected_filter == AuditFilter.all) events else events.filter { audit_filter_of(it) == selected_filter }
    }
    val visible = if (expanded) filtered else filtered.take(activity_preview_count)

    val today_label = stringResource(R.string.security_activity_today)
    val yesterday_label = stringResource(R.string.security_activity_yesterday)
    val unknown_label = stringResource(R.string.unknown)
    val zone = org.astermail.android.ui.mail.AsterTimePreferences.account_zone_id()
    val groups = remember(visible, today_label, yesterday_label, unknown_label, zone) {
        group_audit_events(visible, zone, today_label, yesterday_label, unknown_label)
    }

    if (chips.size > 1) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState())
                .padding(bottom = AsterSpacing.sm),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            chips.forEachIndexed { idx, (id, label_res) ->
                if (idx > 0) Spacer(Modifier.width(AsterSpacing.xs))
                activity_filter_chip(
                    label = stringResource(label_res),
                    selected = selected_filter == id,
                    colors = colors,
                    on_click = {
                        selected_filter = id
                        expanded = false
                    },
                )
            }
        }
    }

    AsterCard(modifier = Modifier.fillMaxWidth()) {
        if (groups.isEmpty()) {
            detail_row(
                title = stringResource(R.string.security_activity_empty_filter),
                icon = TablerIcons.History,
            )
        } else {
            groups.forEachIndexed { group_idx, (day_label, day_events) ->
                if (group_idx > 0) AsterDivider()
                activity_day_header(label = day_label, colors = colors)
                day_events.forEachIndexed { idx, event ->
                    if (idx > 0) AsterDivider()
                    audit_event_row(event = event, colors = colors)
                }
            }
        }
        if (filtered.size > activity_preview_count) {
            AsterDivider()
            Text(
                text = if (expanded) {
                    stringResource(R.string.security_activity_show_less)
                } else {
                    stringResource(R.string.security_activity_show_more)
                },
                color = colors.accent_blue,
                fontSize = 13.sp,
                fontWeight = FontWeight.Medium,
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { expanded = !expanded }
                    .testTag("security_activity_show_more")
                    .padding(horizontal = AsterSpacing.md, vertical = AsterSpacing.md),
            )
        }
    }
}

@Composable
private fun activity_filter_chip(
    label: String,
    selected: Boolean,
    colors: org.astermail.android.design.AsterSemanticColors,
    on_click: () -> Unit,
) {
    val shape = SquircleShape(12.dp)
    Text(
        text = label,
        color = if (selected) Color.White else colors.text_secondary,
        fontSize = 13.sp,
        fontWeight = FontWeight.Medium,
        modifier = Modifier
            .clip(shape)
            .background(if (selected) colors.accent_blue else colors.bg_secondary)
            .border(1.dp, if (selected) colors.accent_blue else colors.border_primary, shape)
            .clickable(onClick = on_click)
            .padding(horizontal = AsterSpacing.md, vertical = 7.dp),
    )
}

@Composable
private fun activity_day_header(
    label: String,
    colors: org.astermail.android.design.AsterSemanticColors,
) {
    Text(
        text = label,
        color = colors.text_secondary,
        fontSize = 12.sp,
        fontWeight = FontWeight.SemiBold,
        modifier = Modifier
            .fillMaxWidth()
            .background(colors.bg_secondary)
            .padding(horizontal = AsterSpacing.md, vertical = 7.dp),
    )
}

@Composable
private fun audit_event_row(
    event: AuditEvent,
    colors: org.astermail.android.design.AsterSemanticColors,
) {
    val icon_tint = when (audit_filter_of(event)) {
        AuditFilter.failures -> colors.danger
        else -> colors.text_secondary
    }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = AsterSpacing.md, vertical = AsterSpacing.sm),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            imageVector = audit_icon(event.event_type),
            contentDescription = null,
            tint = icon_tint,
            modifier = Modifier.size(20.dp),
        )
        Spacer(Modifier.width(AsterSpacing.md))
        Column(modifier = Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = format_audit_event(event.event_type),
                    color = colors.text_primary,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Medium,
                    modifier = Modifier.weight(1f, fill = false),
                )
                Spacer(Modifier.width(AsterSpacing.sm))
                Text(
                    text = relative_time_label(event.created_at),
                    color = colors.text_muted,
                    fontSize = 12.sp,
                )
            }
            val meta = listOfNotNull(
                audit_device_label(event.user_agent),
                event.ip_address?.takeIf { it.isNotBlank() },
            ).joinToString(" - ")
            if (meta.isNotBlank()) {
                Text(
                    text = meta,
                    color = colors.text_tertiary,
                    fontSize = 12.sp,
                )
            }
        }
    }
}

@Composable
private fun remote_image_loading_row(
    selected_id: String,
    on_select: (String) -> Unit,
) {
    val colors = AsterMaterial.colors
    var menu_open by remember { mutableStateOf(false) }
    val options = listOf(
        "never" to stringResource(R.string.remote_images_never),
        "ask" to stringResource(R.string.remote_images_ask),
        "always" to stringResource(R.string.remote_images_always),
    )
    val selected_label = options.firstOrNull { it.first == selected_id }?.second ?: options.first().second
    val shape = SquircleShape(12.dp)
    detail_row(
        title = stringResource(R.string.remote_image_loading_title),
        subtitle = stringResource(R.string.remote_image_loading_subtitle),
        icon = TablerIcons.Photo,
        trailing = {
            Box {
                Row(
                    modifier = Modifier
                        .clip(shape)
                        .background(colors.input_bg)
                        .border(1.dp, colors.input_border, shape)
                        .clickable { menu_open = true }
                        .testTag("remote_image_loading_select")
                        .padding(start = AsterSpacing.md, end = AsterSpacing.sm, top = 8.dp, bottom = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = selected_label,
                        color = colors.text_primary,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Medium,
                    )
                    Spacer(Modifier.width(AsterSpacing.xs))
                    Icon(
                        imageVector = TablerIcons.ChevronDown,
                        contentDescription = null,
                        tint = colors.text_muted,
                        modifier = Modifier.size(16.dp),
                    )
                }
                aster_dropdown_menu(
                    expanded = menu_open,
                    on_dismiss = { menu_open = false },
                ) {
                    options.forEach { (id, label) ->
                        aster_dropdown_item(
                            label = label,
                            selected = selected_id == id,
                            test_tag = "remote_image_loading_$id",
                            on_click = {
                                menu_open = false
                                on_select(id)
                            },
                        )
                    }
                }
            }
        },
    )
}

@Composable
private fun security_loading_skeleton() {
    section_label(stringResource(R.string.section_account_protection))
    skeleton_hero_card(lines = 1, bar = true)
    v_gap(AsterSpacing.lg)
    section_label(stringResource(R.string.section_authentication))
    skeleton_card_list(rows = 5)
    v_gap(AsterSpacing.lg)
    section_label(stringResource(R.string.section_trusted_devices))
    skeleton_card_list(rows = 2, leading_circle = true)
    v_gap(AsterSpacing.lg)
    section_label(stringResource(R.string.section_tracking_protection))
    skeleton_card_list(rows = 3, trailing_width = 44.dp)
    v_gap(AsterSpacing.lg)
    section_label(stringResource(R.string.section_images))
    skeleton_card_list(rows = 5, trailing_width = 44.dp)
    v_gap(AsterSpacing.lg)
    section_label(stringResource(R.string.section_recent_activity))
    skeleton_card_list(rows = 4, leading_circle = true)
    v_gap(AsterSpacing.xxl)
}
