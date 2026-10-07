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
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
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
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.foundation.layout.heightIn
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
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlinx.coroutines.delay
import kotlin.math.roundToInt
import kotlinx.coroutines.launch
import org.astermail.android.R
import org.astermail.android.api.preferences.UserPreferences
import org.astermail.android.api.security.AuditEvent
import org.astermail.android.api.security.HardwareKey
import org.astermail.android.api.security.TrustedDevice
import org.astermail.android.design.AsterMaterial
import org.astermail.android.design.AsterSpacing
import org.astermail.android.design.AsterShapes
import org.astermail.android.design.SquircleShape
import org.astermail.android.design.auto_mirrored
import org.astermail.android.design.field_surface_color
import org.astermail.android.design.components.AsterAlertDialog
import org.astermail.android.design.components.AsterCard
import org.astermail.android.design.components.shimmer
import org.astermail.android.design.components.shimmer_state
import org.astermail.android.design.components.AsterGhostButton
import org.astermail.android.design.components.AsterIconButton
import org.astermail.android.design.components.AsterSwitch
import org.astermail.android.design.components.aster_menu_item
import org.astermail.android.design.components.aster_menu
import org.astermail.android.security.AppLockStore
import org.astermail.android.security.AppLockViewModel
import org.astermail.android.settings.SettingsViewModel
import org.astermail.android.settings.host_activity
import org.astermail.android.auth.create_passkey_json
import org.astermail.android.auth.request_passkey_json
import org.astermail.android.ui.common.find_host_activity
import org.astermail.android.ui.security.AppLockSetupSheet
import org.astermail.android.ui.security.AppLockVerifySheet
import org.astermail.android.settings.shared_settings_view_model
import org.astermail.android.design.mirror_in_rtl

private const val activity_preview_count = 5
private const val trusted_preview_count = 3
private const val security_settle_delay_ms = 90L
private const val security_load_timeout_ms = 5000L
private const val security_score_max = 7
private const val anchor_highlight_ms = 1600L
private const val anchor_highlight_fade_ms = 350
private const val anchor_highlight_alpha = 0.14f

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
    event_type.contains("login") || event_type.contains("sign_in") -> TablerIcons.Login.auto_mirrored()
    event_type.contains("logout") || event_type.contains("sign_out") -> TablerIcons.Logout.auto_mirrored()
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
                    tint = colors.on_accent,
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
        vm.load_recovery_codes_status()
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

    val security_signals_ready = sec != null &&
        prefs != null &&
        state.login_alerts_enabled != null &&
        state.vanguard_enabled != null &&
        !state.is_loading

    var content_ready by remember {
        mutableStateOf(
            sec != null && prefs != null && state.login_alerts_enabled != null && state.vanguard_enabled != null,
        )
    }
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

    var banner_dismissed by rememberSaveable { mutableStateOf(false) }
    var hardware_keys_expanded by remember { mutableStateOf(false) }
    var show_revoke_all_confirm by remember { mutableStateOf(false) }
    var trusted_expanded by remember { mutableStateOf(false) }
    val scroll_state = rememberScrollState()
    val scope = rememberCoroutineScope()
    val density = LocalDensity.current
    val anchor_coordinates = remember { mutableMapOf<security_anchor, LayoutCoordinates>() }
    var content_top_coordinates by remember { mutableStateOf<LayoutCoordinates?>(null) }
    var highlighted_anchor by remember { mutableStateOf<security_anchor?>(null) }
    var highlight_visible by remember { mutableStateOf(false) }
    var highlight_token by remember { mutableStateOf(0) }
    val highlight_alpha by animateFloatAsState(
        targetValue = if (highlight_visible) 1f else 0f,
        animationSpec = tween(durationMillis = anchor_highlight_fade_ms),
        label = "security_anchor_highlight",
    )

    LaunchedEffect(highlight_token) {
        if (highlight_token == 0) return@LaunchedEffect
        highlight_visible = true
        delay(anchor_highlight_ms)
        highlight_visible = false
    }

    fun scroll_to_anchor(anchor: security_anchor) {
        val top = content_top_coordinates?.takeIf { it.isAttached } ?: return
        val resolved = resolve_security_anchor(anchor) { candidate ->
            anchor_coordinates[candidate]?.isAttached == true
        } ?: return
        val target = anchor_coordinates[resolved] ?: return
        val margin = with(density) { AsterSpacing.lg.toPx() }
        val offset = top.localPositionOf(target, Offset.Zero).y - margin
        highlighted_anchor = resolved
        highlight_token += 1
        scope.launch {
            scroll_state.animateScrollTo(offset.toInt().coerceIn(0, scroll_state.maxValue))
        }
    }

    fun open_check(check: security_check) {
        when (val target = security_check_target_for(check)) {
            is security_check_target.screen -> on_open(target.route_id)
            is security_check_target.section -> scroll_to_anchor(target.anchor)
        }
    }

    fun anchor_modifier(anchor: security_anchor): Modifier = Modifier
        .fillMaxWidth()
        .onGloballyPositioned { anchor_coordinates[anchor] = it }
        .drawBehind {
            if (highlighted_anchor != anchor || highlight_alpha <= 0f) return@drawBehind
            drawRect(colors.accent_blue.copy(alpha = anchor_highlight_alpha * highlight_alpha))
        }

    val totp_sub = when {
        sec == null -> stringResource(R.string.two_factor_subtitle_add)
        sec.totp_enabled -> stringResource(R.string.enabled)
        else -> stringResource(R.string.disabled)
    }
    val codes_status = state.recovery_codes_status
    val recovery_codes_sub = if (codes_status != null && codes_status.total_codes > 0) {
        pluralStringResource(
            R.plurals.recovery_codes_remaining,
            codes_status.total_codes,
            codes_status.available_codes,
            codes_status.total_codes,
        )
    } else {
        stringResource(R.string.backup_access)
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
            security_loading_skeleton(show_banner = prefs?.account_security_banner_dismissed != true)
            return@detail_scaffold
        }

        Box(
            modifier = Modifier
                .fillMaxWidth()
                .onGloballyPositioned { content_top_coordinates = it },
        )
        preferences_save_error_banner()
        val protection_items = listOf(
            protection_item(
                label = stringResource(R.string.two_factor_auth),
                done = sec?.totp_enabled == true,
                test_tag = "protection_item_two_factor",
                on_open = { open_check(security_check.two_factor) },
            ),
            protection_item(
                label = stringResource(R.string.check_passkey_registered),
                done = hardware_keys_count > 0,
                test_tag = "protection_item_passkey",
                on_open = { open_check(security_check.passkey) },
            ),
            protection_item(
                label = stringResource(R.string.check_verified_recovery_email),
                done = recovery_email_verified,
                test_tag = "protection_item_recovery_email",
                on_open = { open_check(security_check.recovery_email) },
            ),
            protection_item(
                label = stringResource(R.string.login_alerts),
                done = state.login_alerts_enabled == true,
                test_tag = "protection_item_login_alerts",
                on_open = { open_check(security_check.login_alerts) },
            ),
            protection_item(
                label = stringResource(R.string.block_tracking_pixels),
                done = prefs?.block_tracking_pixels == true,
                test_tag = "protection_item_tracking_pixels",
                on_open = { open_check(security_check.tracking_pixels) },
            ),
            protection_item(
                label = stringResource(R.string.block_remote_images),
                done = prefs?.block_external_images == true,
                test_tag = "protection_item_remote_images",
                on_open = { open_check(security_check.remote_images) },
            ),
            protection_item(
                label = stringResource(R.string.strip_exif),
                done = prefs?.strip_exif_on_compose == true,
                test_tag = "protection_item_strip_exif",
                on_open = { open_check(security_check.strip_exif) },
            ),
        )
        val banner_visible = score != null &&
            !banner_dismissed &&
            prefs?.account_security_banner_dismissed != true
        AnimatedVisibility(
            visible = banner_visible,
            enter = expandVertically() + fadeIn(),
            exit = shrinkVertically() + fadeOut(),
        ) {
            Column(modifier = Modifier.fillMaxWidth().padding(top = AsterSpacing.md)) {
                protection_banner(
                    score = score ?: 0,
                    tone = score_color,
                    items = protection_items,
                    on_dismiss = {
                        banner_dismissed = true
                        toggle { it.copy(account_security_banner_dismissed = true) }
                    },
                )
            }
        }

        section_label(stringResource(R.string.section_authentication))
        AsterCard(modifier = Modifier.fillMaxWidth()) {
            detail_row(
                title = stringResource(R.string.change_password),
                subtitle = stringResource(R.string.change_password_subtitle),
                icon = TablerIcons.Lock,
                on_click = { on_open("change_password") },
            )
            settings_row_gap()
            detail_row(
                title = stringResource(R.string.two_factor_auth),
                subtitle = totp_sub,
                icon = TablerIcons.ShieldCheck,
                on_click = { on_open("two_factor") },
                recommendation = if (sec != null && !sec.totp_enabled) {
                    stringResource(R.string.two_step_verification_recommendation)
                } else {
                    null
                },
            )
            settings_row_gap()
            Box(modifier = anchor_modifier(security_anchor.login_alerts)) {
                detail_row(
                    title = stringResource(R.string.login_alerts),
                    subtitle = stringResource(R.string.login_alerts_subtitle),
                    icon = TablerIcons.BellRinging,
                    info_title = stringResource(R.string.login_alerts_info_title),
                    info_description = stringResource(R.string.login_alerts_info_desc),
                    recommendation = if (state.login_alerts_enabled == false) {
                        stringResource(R.string.login_alerts_off_recommendation)
                    } else {
                        null
                    },
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
            }
            settings_row_gap()
            detail_row(
                title = stringResource(R.string.active_sessions),
                subtitle = stringResource(R.string.devices_signed_in),
                icon = TablerIcons.Devices,
                on_click = { on_open("sessions") },
            )
            settings_row_gap()
            Column(modifier = anchor_modifier(security_anchor.passkeys)) {
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
                                content_description = stringResource(
                                    if (hardware_keys_expanded) R.string.collapse_folder else R.string.expand_folder,
                                    stringResource(R.string.passkeys_security_keys),
                                ),
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
                                settings_row_gap()
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
                    detail_row(
                        title = stringResource(R.string.passkeys_security_keys),
                        subtitle = stringResource(R.string.passkeys_none_subtitle),
                        icon = TablerIcons.Key,
                        recommendation = stringResource(R.string.no_passkeys_recommendation),
                    )
                }
                settings_row_gap()
                detail_row(
                    title = stringResource(R.string.passkey_add),
                    subtitle = stringResource(
                        if (state.is_adding_passkey) R.string.passkey_adding else R.string.passkey_add_subtitle,
                    ),
                    icon = TablerIcons.Plus,
                    on_click = if (state.is_adding_passkey) null else {
                        {
                            val host = context.host_activity() ?: context
                            vm.add_passkey(
                                create_credential = { json -> create_passkey_json(host, json) },
                                get_credential = { json -> request_passkey_json(host, json) },
                            )
                        }
                    },
                )
            }
        }

        v_gap(AsterSpacing.lg)

        section_label(stringResource(R.string.section_trusted_devices))
        AsterCard(modifier = Modifier.fillMaxWidth().testTag("trusted_devices_card")) {
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
                val trusted_shown = if (trusted_expanded) {
                    state.trusted_devices
                } else {
                    state.trusted_devices.take(trusted_preview_count)
                }
                val trusted_hidden = state.trusted_devices.size - trusted_shown.size
                trusted_shown.forEach { device ->
                    trusted_device_row(
                        device = device,
                        on_revoke = { vm.revoke_trusted_device(device.id) },
                        colors = colors,
                    )
                    settings_row_gap()
                }
                if (trusted_hidden > 0) {
                    devices_list_action_row(
                        label = pluralStringResource(R.plurals.devices_show_more, trusted_hidden, trusted_hidden),
                        icon = TablerIcons.ChevronDown,
                        tint = colors.accent_blue,
                        test_tag = "trusted_devices_show_more",
                        on_click = { trusted_expanded = true },
                    )
                    settings_row_gap()
                } else if (trusted_expanded && state.trusted_devices.size > trusted_preview_count) {
                    devices_list_action_row(
                        label = stringResource(R.string.show_less),
                        icon = TablerIcons.ChevronUp,
                        tint = colors.accent_blue,
                        test_tag = "trusted_devices_show_less",
                        on_click = { trusted_expanded = false },
                    )
                    settings_row_gap()
                }
                devices_list_action_row(
                    label = stringResource(R.string.revoke_all_action),
                    icon = TablerIcons.Logout.auto_mirrored(),
                    tint = colors.danger,
                    test_tag = "trusted_devices_revoke_all",
                    on_click = { show_revoke_all_confirm = true },
                )
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
                Box(modifier = anchor_modifier(security_anchor.tracking_protection)) {
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
                }
                if (prefs.block_external_content != false) {
                    settings_row_gap()
                    Box(modifier = anchor_modifier(security_anchor.tracking_pixels)) {
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
                    }
                    settings_row_gap()
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
                Box(modifier = anchor_modifier(security_anchor.remote_images)) {
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
                }
                settings_row_gap()
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
                settings_row_gap()
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
                settings_row_gap()
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
                settings_row_gap()
                Box(modifier = anchor_modifier(security_anchor.strip_exif)) {
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
                title = stringResource(R.string.recovery_codes),
                subtitle = recovery_codes_sub,
                icon = TablerIcons.Key,
                on_click = { on_open("recovery_codes") },
            )
            settings_row_gap()
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
            settings_row_gap()
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
}

private enum class AppLockModal { setup, verify_to_change, change, disable, verify_to_enable_biometric }

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
    val lock_context = LocalContext.current
    val biometric_available = remember {
        androidx.biometric.BiometricManager.from(lock_context).canAuthenticate(
            androidx.biometric.BiometricManager.Authenticators.BIOMETRIC_STRONG,
        ) == androidx.biometric.BiometricManager.BIOMETRIC_SUCCESS
    }
    var biometric_enabled by remember {
        mutableStateOf(org.astermail.android.security.BiometricUnlockGate.is_enrolled(lock_context))
    }

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
                            color = colors.on_accent,
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
                    settings_row_gap()
                    Spacer(Modifier.height(AsterSpacing.md))
                    app_lock_row(
                        store = store,
                        enabled = app_lock_enabled,
                        on_toggle = { want ->
                            if (want) modal = AppLockModal.setup
                            else modal = AppLockModal.disable
                        },
                        on_change_pin = { modal = AppLockModal.verify_to_change },
                        biometric_available = biometric_available,
                        biometric_enabled = biometric_enabled,
                        on_toggle_biometric = { want ->
                            if (want) {
                                modal = AppLockModal.verify_to_enable_biometric
                            } else {
                                org.astermail.android.security.BiometricUnlockGate.reset(lock_context)
                                biometric_enabled = false
                            }
                        },
                    )
                }
            }
        }
    }

    state.hardware_key_step_up_id?.let { step_up_key_id ->
        var step_up_password by remember(step_up_key_id) { mutableStateOf("") }
        AsterAlertDialog(
            on_dismiss = { vm.dismiss_hardware_key_step_up() },
            title = stringResource(R.string.hardware_key_remove_last_title),
            message = stringResource(R.string.hardware_key_remove_last_description),
            confirm_label = stringResource(R.string.remove),
            cancel_label = stringResource(R.string.cancel),
            confirm_style = org.astermail.android.design.components.DialogConfirmStyle.destructive,
            confirm_enabled = step_up_password.isNotBlank() && !state.hardware_key_step_up_busy,
            is_busy = state.hardware_key_step_up_busy,
            dismiss_on_confirm = false,
            on_confirm = { vm.delete_hardware_key(step_up_key_id, step_up_password) },
            extra_content = {
                Column {
                    state.hardware_key_step_up_error?.let {
                        Text(
                            text = it,
                            color = colors.danger,
                            fontSize = 13.sp,
                        )
                        Spacer(Modifier.height(AsterSpacing.md))
                    }
                    org.astermail.android.design.components.AsterTextField(
                        value = step_up_password,
                        onValueChange = { step_up_password = it },
                        singleLine = true,
                        placeholder = stringResource(R.string.enter_your_password),
                        visual_transformation = PasswordVisualTransformation(),
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
            },
        )
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
                    biometric_enabled = false
                }
            },
        )
    }

    when (modal) {
        AppLockModal.setup, AppLockModal.change -> AppLockSetupSheet(
            store = store,
            on_dismiss = { modal = null },
            on_success = { app_lock_enabled = true; biometric_enabled = false; modal = null },
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
            on_success = { store.disable(); app_lock_enabled = false; biometric_enabled = false; modal = null },
        )
        AppLockModal.verify_to_enable_biometric -> AppLockVerifySheet(
            store = store,
            description = stringResource(R.string.app_lock_enter_to_enable_biometric),
            on_dismiss = { modal = null },
            on_success = {
                modal = null
                val activity = lock_context.find_host_activity() as? androidx.fragment.app.FragmentActivity
                if (activity != null) {
                    org.astermail.android.ui.security.launch_biometric_enroll(
                        activity = activity,
                        origin = org.astermail.android.security.BiometricEnrollOrigin.SETTINGS_AFTER_PIN,
                        pin_verified = true,
                        biometric_available = biometric_available,
                    ) { enrolled -> biometric_enabled = enrolled }
                }
            },
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
    biometric_available: Boolean,
    biometric_enabled: Boolean,
    on_toggle_biometric: (Boolean) -> Unit,
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
                modifier = Modifier
                    .clip(SquircleShape(8.dp))
                    .clickable(onClick = on_change_pin)
                    .padding(horizontal = AsterSpacing.xs, vertical = 2.dp),
            )
        }
        if (enabled && biometric_available) {
            Spacer(Modifier.height(AsterSpacing.md))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(modifier = Modifier.weight(1f).padding(end = AsterSpacing.md)) {
                    Text(
                        text = stringResource(R.string.app_lock_use_biometric),
                        color = colors.text_primary,
                        fontSize = 15.sp,
                        fontWeight = FontWeight.Medium,
                    )
                    Spacer(Modifier.height(2.dp))
                    Text(
                        text = stringResource(R.string.app_lock_biometric_subtitle),
                        color = colors.text_muted,
                        fontSize = 13.sp,
                    )
                }
                AsterSwitch(
                    checked = biometric_enabled,
                    onCheckedChange = on_toggle_biometric,
                )
            }
        }
    }
}

private data class protection_item(
    val label: String,
    val done: Boolean,
    val test_tag: String,
    val on_open: () -> Unit,
)

@Composable
private fun protection_banner(
    score: Int,
    tone: Color,
    items: List<protection_item>,
    on_dismiss: () -> Unit,
) {
    val colors = AsterMaterial.colors
    val percent = (score * 100f / security_score_max).roundToInt()
    val pending = items.filterNot { it.done }
    val protected_items = items.filter { it.done }
    var show_protected by rememberSaveable { mutableStateOf(false) }
    val tone_background = tone.copy(alpha = if (colors.is_dark) 0.18f else 0.14f)
    AsterCard(modifier = Modifier.fillMaxWidth().testTag("protection_banner")) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = AsterSpacing.lg, end = AsterSpacing.xs, top = AsterSpacing.md, bottom = AsterSpacing.sm),
        ) {
            Row(verticalAlignment = Alignment.Top) {
                Box(
                    modifier = Modifier
                        .size(44.dp)
                        .background(tone_background, SquircleShape(12.dp)),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        imageVector = if (pending.isEmpty()) TablerIcons.ShieldCheck else TablerIcons.Shield,
                        contentDescription = null,
                        tint = tone,
                        modifier = Modifier.size(24.dp),
                    )
                }
                Spacer(Modifier.width(AsterSpacing.md))
                Column(
                    modifier = Modifier
                        .weight(1f)
                        .padding(top = 2.dp),
                ) {
                    Text(
                        text = stringResource(R.string.account_security_percent_title, percent),
                        color = colors.text_primary,
                        fontSize = 16.sp,
                        fontWeight = FontWeight.SemiBold,
                    )
                    Text(
                        text = if (pending.isEmpty()) {
                            stringResource(R.string.security_center_all_clear)
                        } else {
                            pluralStringResource(R.plurals.security_actions_remaining, pending.size, pending.size)
                        },
                        color = colors.text_tertiary,
                        fontSize = 13.sp,
                    )
                }
                AsterIconButton(
                    icon = TablerIcons.X,
                    content_description = stringResource(R.string.account_security_dismiss),
                    onClick = on_dismiss,
                    modifier = Modifier.testTag("protection_banner_dismiss"),
                )
            }
            v_gap(AsterSpacing.md)
            Box(
                modifier = Modifier
                    .padding(end = AsterSpacing.md)
                    .fillMaxWidth()
                    .height(6.dp)
                    .clip(CircleShape)
                    .background(colors.border_primary),
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth(fraction = (score / security_score_max.toFloat()).coerceIn(0f, 1f))
                        .height(6.dp)
                        .clip(CircleShape)
                        .background(tone),
                )
            }
            if (pending.isNotEmpty()) {
                v_gap(AsterSpacing.md)
                Text(
                    text = stringResource(R.string.security_center_recommended).uppercase(),
                    color = colors.text_tertiary,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.SemiBold,
                    letterSpacing = 0.8.sp,
                )
                v_gap(AsterSpacing.xs)
                pending.forEach { protection_item_row(it) }
            }
            if (protected_items.isNotEmpty()) {
                Row(
                    modifier = Modifier
                        .padding(end = AsterSpacing.sm)
                        .fillMaxWidth()
                        .clip(SquircleShape(10.dp))
                        .clickable { show_protected = !show_protected }
                        .testTag("protection_banner_protected_toggle")
                        .heightIn(min = 40.dp)
                        .padding(vertical = AsterSpacing.xs),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = stringResource(R.string.security_center_protected),
                        color = colors.text_secondary,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Medium,
                    )
                    Spacer(Modifier.width(AsterSpacing.xs))
                    tone_badge(text = protected_items.size.toString(), tone = colors.success)
                    Spacer(Modifier.weight(1f))
                    Icon(
                        imageVector = if (show_protected) TablerIcons.ChevronUp else TablerIcons.ChevronDown,
                        contentDescription = null,
                        tint = colors.text_muted,
                        modifier = Modifier.size(18.dp),
                    )
                }
                AnimatedVisibility(
                    visible = show_protected,
                    enter = expandVertically() + fadeIn(),
                    exit = shrinkVertically() + fadeOut(),
                ) {
                    Column { protected_items.forEach { protection_item_row(it) } }
                }
            }
        }
    }
}

@Composable
private fun protection_item_row(item: protection_item) {
    val colors = AsterMaterial.colors
    Row(
        modifier = Modifier
            .padding(end = AsterSpacing.sm)
            .fillMaxWidth()
            .clip(SquircleShape(10.dp))
            .clickable(onClick = item.on_open)
            .testTag(item.test_tag)
            .heightIn(min = 44.dp)
            .padding(vertical = AsterSpacing.xs),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            imageVector = if (item.done) TablerIcons.CircleCheck else TablerIcons.AlertCircle,
            contentDescription = null,
            tint = if (item.done) colors.success else colors.warning,
            modifier = Modifier.size(20.dp),
        )
        Spacer(Modifier.width(AsterSpacing.md))
        Text(
            text = item.label,
            color = if (item.done) colors.text_secondary else colors.text_primary,
            fontSize = 14.sp,
            fontWeight = if (item.done) FontWeight.Normal else FontWeight.Medium,
            modifier = Modifier.weight(1f),
        )
        Icon(
            imageVector = TablerIcons.ChevronRight,
            contentDescription = null,
            tint = colors.text_muted,
            modifier = Modifier.size(16.dp).mirror_in_rtl(),
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
            .heightIn(min = 64.dp)
            .padding(horizontal = AsterSpacing.lg, vertical = AsterSpacing.sm)
            .testTag("trusted_device_row"),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        list_icon_tile(icon = TablerIcons.DeviceLaptop, tint = colors.text_secondary)
        Spacer(Modifier.width(AsterSpacing.md))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = org.astermail.android.ui.settings.clean_trusted_device_label(device.label)
                    .ifBlank { stringResource(R.string.trusted_device_default_label) },
                color = colors.text_primary,
                fontSize = 15.sp,
                fontWeight = FontWeight.Medium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            val expires = device.expires_at
            if (!expires.isNullOrBlank()) {
                Text(
                    text = stringResource(R.string.expires_custom_at, relative_time_label(expires)),
                    color = colors.text_tertiary,
                    fontSize = 13.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            val ip = device.ip_snippet
            if (!ip.isNullOrBlank()) {
                Text(
                    text = ip,
                    color = colors.text_muted,
                    fontSize = 12.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
        Spacer(Modifier.width(AsterSpacing.sm))
        revoke_pill_button(
            label = stringResource(R.string.revoke),
            in_flight = false,
            on_click = { show_revoke_confirm = true },
        )
    }
}

@Composable
private fun list_icon_tile(
    icon: ImageVector,
    tint: Color,
) {
    Box(
        modifier = Modifier.size(24.dp),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = tint,
            modifier = Modifier.size(20.dp),
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
    var selected_filter by remember { mutableStateOf(AuditFilter.all) }
    var expanded by remember { mutableStateOf(false) }
    var filter_menu_open by remember { mutableStateOf(false) }

    val present = remember(events) { events.map { audit_filter_of(it) }.toSet() }
    LaunchedEffect(present) {
        if (selected_filter != AuditFilter.all && !present.contains(selected_filter)) selected_filter = AuditFilter.all
    }

    val filter_options = listOf(
        AuditFilter.all to R.string.security_activity_filter_all,
        AuditFilter.sign_ins to R.string.security_activity_filter_sign_ins,
        AuditFilter.security_changes to R.string.security_activity_filter_security,
        AuditFilter.failures to R.string.security_activity_filter_failures,
    ).filter { it.first == AuditFilter.all || present.contains(it.first) }

    Row(
        modifier = Modifier.fillMaxWidth().padding(top = AsterSpacing.md, bottom = AsterSpacing.sm),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = stringResource(R.string.section_recent_activity).uppercase(),
            color = colors.text_tertiary,
            fontSize = 11.sp,
            fontWeight = FontWeight.SemiBold,
            modifier = Modifier.weight(1f),
        )
        if (filter_options.size > 1) {
            Box {
                Row(
                    modifier = Modifier
                        .clip(SquircleShape(10.dp))
                        .clickable { filter_menu_open = true }
                        .testTag("security_activity_filter")
                        .padding(horizontal = AsterSpacing.sm, vertical = AsterSpacing.xs),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(
                        imageVector = TablerIcons.Filter,
                        contentDescription = null,
                        tint = colors.accent_blue,
                        modifier = Modifier.size(14.dp),
                    )
                    Spacer(Modifier.width(AsterSpacing.xs))
                    Text(
                        text = stringResource(filter_options.first { it.first == selected_filter }.second),
                        color = colors.accent_blue,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Medium,
                    )
                    Icon(
                        imageVector = TablerIcons.ChevronDown,
                        contentDescription = null,
                        tint = colors.accent_blue,
                        modifier = Modifier.size(14.dp),
                    )
                }
                aster_menu(
                    expanded = filter_menu_open,
                    on_dismiss = { filter_menu_open = false },
                ) {
                    filter_options.forEach { (id, label_res) ->
                        aster_menu_item(
                            label = stringResource(label_res),
                            selected = selected_filter == id,
                            test_tag = "security_activity_filter_${id.name}",
                            on_click = {
                                filter_menu_open = false
                                selected_filter = id
                                expanded = false
                            },
                        )
                    }
                }
            }
        }
    }

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

    AsterCard(modifier = Modifier.fillMaxWidth().testTag("security_activity_card")) {
        if (groups.isEmpty()) {
            detail_row(
                title = stringResource(R.string.security_activity_empty_filter),
                icon = TablerIcons.History,
            )
        } else {
            groups.forEachIndexed { group_idx, (day_label, day_events) ->
                activity_day_header(label = day_label, first = group_idx == 0, colors = colors)
                day_events.forEach { event ->
                    audit_event_row(event = event, colors = colors)
                }
            }
            Spacer(Modifier.height(AsterSpacing.sm))
        }
        if (filtered.size > activity_preview_count) {
            settings_row_gap()
            val remaining = filtered.size - activity_preview_count
            devices_list_action_row(
                label = if (expanded) {
                    stringResource(R.string.show_less)
                } else {
                    pluralStringResource(R.plurals.devices_show_more, remaining, remaining)
                },
                icon = if (expanded) TablerIcons.ChevronUp else TablerIcons.ChevronDown,
                tint = colors.accent_blue,
                test_tag = "security_activity_show_more",
                on_click = { expanded = !expanded },
            )
        }
    }
}

@Composable
private fun activity_day_header(
    label: String,
    first: Boolean,
    colors: org.astermail.android.design.AsterSemanticColors,
) {
    Text(
        text = label,
        color = colors.text_primary,
        fontSize = 13.sp,
        fontWeight = FontWeight.SemiBold,
        modifier = Modifier
            .fillMaxWidth()
            .padding(
                start = AsterSpacing.lg,
                end = AsterSpacing.lg,
                top = if (first) AsterSpacing.md else AsterSpacing.lg,
                bottom = AsterSpacing.xs,
            ),
    )
}

@Composable
private fun audit_event_row(
    event: AuditEvent,
    colors: org.astermail.android.design.AsterSemanticColors,
) {
    val is_failure = audit_filter_of(event) == AuditFilter.failures
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = AsterSpacing.lg, vertical = AsterSpacing.sm)
            .testTag("security_activity_row"),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        list_icon_tile(
            icon = audit_icon(event.event_type),
            tint = if (is_failure) colors.danger else colors.text_secondary,
        )
        Spacer(Modifier.width(AsterSpacing.md))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = format_audit_event(event.event_type),
                color = if (is_failure) colors.danger else colors.text_primary,
                fontSize = 15.sp,
                fontWeight = FontWeight.Medium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            val device = audit_device_label(event.user_agent)
            if (device != null) {
                Text(
                    text = device,
                    color = colors.text_tertiary,
                    fontSize = 13.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            val ip = event.ip_address?.takeIf { it.isNotBlank() }
            if (ip != null) {
                Text(
                    text = ip,
                    color = colors.text_muted,
                    fontSize = 12.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
        Spacer(Modifier.width(AsterSpacing.sm))
        Text(
            text = relative_time_label(event.created_at),
            color = colors.text_muted,
            fontSize = 12.sp,
        )
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
    val shape = AsterShapes.control
    detail_row(
        title = stringResource(R.string.remote_image_loading_title),
        subtitle = stringResource(R.string.remote_image_loading_subtitle),
        icon = TablerIcons.Photo,
        trailing = {
            Box {
                Row(
                    modifier = Modifier
                        .clip(shape)
                        .background(field_surface_color(colors), shape)
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
                aster_menu(
                    expanded = menu_open,
                    on_dismiss = { menu_open = false },
                ) {
                    options.forEach { (id, label) ->
                        aster_menu_item(
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
private fun security_loading_skeleton(show_banner: Boolean) {
    if (show_banner) {
        v_gap(AsterSpacing.md)
        skeleton_hero_card(lines = 1, bar = true)
    }
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
