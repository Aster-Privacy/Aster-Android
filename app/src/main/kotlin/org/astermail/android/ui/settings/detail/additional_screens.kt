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

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.widget.Toast
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
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material.icons.Icons
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import androidx.compose.ui.Alignment
import androidx.compose.ui.draw.clip
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import org.astermail.android.R
import org.astermail.android.api.preferences.UserPreferences
import org.astermail.android.billing.PlanLimitsViewModel
import org.astermail.android.design.SquircleShape
import org.astermail.android.design.AsterMaterial
import org.astermail.android.design.AsterRadius
import org.astermail.android.design.AsterSpacing
import org.astermail.android.design.components.AsterButton
import org.astermail.android.design.components.AsterCard
import org.astermail.android.design.components.AsterDivider
import org.astermail.android.design.components.AsterGhostButton
import org.astermail.android.design.components.AsterSecondaryButton
import org.astermail.android.design.components.UpgradeGate
import org.astermail.android.folders.flatten_folder_tree
import org.astermail.android.folders.folder_sibling_group
import org.astermail.android.settings.SettingsViewModel

@Composable
private fun toggle_row(title: String, subtitle: String?, checked: Boolean, on_change: (Boolean) -> Unit) {
    val colors = AsterMaterial.colors
    Row(
        modifier = Modifier.fillMaxWidth().padding(horizontal = AsterSpacing.lg, vertical = AsterSpacing.md),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(text = title, color = colors.text_primary, fontSize = 15.sp, fontWeight = FontWeight.Medium)
            if (subtitle != null) {
                Text(text = subtitle, color = colors.text_tertiary, fontSize = 13.sp)
            }
        }
        Switch(
            checked = checked,
            onCheckedChange = on_change,
            colors = SwitchDefaults.colors(
                checkedThumbColor = Color.White,
                checkedTrackColor = colors.accent_blue,
                uncheckedThumbColor = Color.White,
                uncheckedTrackColor = colors.text_muted.copy(alpha = 0.35f),
            ),
        )
    }
}

@Composable
private fun text_area(value: String, placeholder: String, on_change: (String) -> Unit, min_height: Int = 140) {
    val colors = AsterMaterial.colors
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = min_height.dp)
            .background(colors.input_bg, SquircleShape(18.dp))
            .border(1.dp, colors.input_border, SquircleShape(18.dp))
            .padding(AsterSpacing.lg),
    ) {
        if (value.isEmpty()) {
            Text(text = placeholder, color = colors.text_muted, fontSize = 14.sp)
        }
        BasicTextField(
            value = value,
            onValueChange = on_change,
            textStyle = TextStyle(color = colors.text_primary, fontSize = 14.sp),
            cursorBrush = SolidColor(colors.accent_blue),
            modifier = Modifier.fillMaxWidth(),
        )
    }
}

@Composable
fun TrustedDevicesScreen(on_back: () -> Unit, on_open: (id: String) -> Unit = {}) {
    val vm: SettingsViewModel = hiltViewModel()
    val state by vm.state.collectAsStateWithLifecycle()
    val colors = AsterMaterial.colors

    LaunchedEffect(Unit) { vm.load_sessions() }

    detail_scaffold(title = stringResource(R.string.trusted_devices), on_back = on_back) {
        if (state.is_loading && state.sessions.isEmpty()) {
            Box(
                modifier = Modifier.fillMaxWidth().padding(AsterSpacing.xxl),
                contentAlignment = Alignment.Center,
            ) {
                CircularProgressIndicator(color = colors.accent_blue, modifier = Modifier.size(24.dp))
            }
        } else if (state.sessions.isEmpty()) {
            AsterCard(modifier = Modifier.fillMaxWidth()) {
                detail_row(
                    title = stringResource(R.string.no_devices_found),
                    subtitle = state.error ?: stringResource(R.string.could_not_load_devices),
                )
            }
        } else {
            section_label(stringResource(R.string.devices_count, state.sessions.size))
            AsterCard(modifier = Modifier.fillMaxWidth()) {
                state.sessions.forEachIndexed { idx, s ->
                    val dt = s.device_type.lowercase()
                    val icon = when {
                        dt.contains("android") || dt.contains("mobile") || dt.contains("iphone") -> TablerIcons.DeviceMobile
                        dt.contains("tablet") || dt.contains("ipad") -> TablerIcons.DeviceTablet
                        else -> TablerIcons.DeviceDesktop
                    }
                    val name = listOfNotNull(
                        s.device_type.takeIf { it.isNotBlank() },
                        s.browser.takeIf { it.isNotBlank() },
                    ).joinToString(" - ").ifEmpty { stringResource(R.string.unknown_device) }
                    val last_seen = if (s.is_current) stringResource(R.string.active_now) else s.last_active ?: stringResource(R.string.unknown)
                    detail_row(
                        title = name,
                        subtitle = last_seen,
                        icon = icon,
                        on_click = {},
                        trailing = {
                            if (s.is_current) verified_badge(stringResource(R.string.this_device))
                            else AsterGhostButton(label = stringResource(R.string.revoke), onClick = { vm.revoke_session(s.id) })
                        },
                    )
                    if (idx < state.sessions.lastIndex) AsterDivider(modifier = Modifier)
                }
            }
            if (state.sessions.size > 1) {
                v_gap(AsterSpacing.lg)
                AsterSecondaryButton(
                    label = stringResource(R.string.revoke_all_other),
                    onClick = { vm.logout_others() },
                )
            }
        }
        v_gap(AsterSpacing.xxl)
    }
}

@Composable
fun GhostAliasesScreen(on_back: () -> Unit, on_open: (id: String) -> Unit = {}) {
    val vm: SettingsViewModel = hiltViewModel()
    val state by vm.state.collectAsStateWithLifecycle()
    val colors = AsterMaterial.colors
    val scope = rememberCoroutineScope()
    val context = LocalContext.current

    var show_create_dialog by remember { mutableStateOf(false) }
    var create_note by remember { mutableStateOf("") }
    var is_creating by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) { vm.load_ghost_aliases() }

    detail_scaffold(title = stringResource(R.string.ghost_aliases), on_back = on_back) {
        section_label(stringResource(R.string.ghost_aliases_about))
        AsterCard(modifier = Modifier.fillMaxWidth()) {
            Column(Modifier.padding(AsterSpacing.lg)) {
                Text(
                    text = stringResource(R.string.ghost_aliases_description),
                    color = colors.text_secondary,
                    fontSize = 14.sp,
                )
            }
        }
        v_gap(AsterSpacing.lg)
        AsterButton(
            label = if (is_creating) "Creating..." else "Create Ghost Alias",
            onClick = { show_create_dialog = true },
        )
        v_gap(AsterSpacing.lg)
        if (state.is_loading && state.ghost_aliases.isEmpty()) {
            Box(
                modifier = Modifier.fillMaxWidth().padding(AsterSpacing.xxl),
                contentAlignment = Alignment.Center,
            ) {
                CircularProgressIndicator(color = colors.accent_blue, modifier = Modifier.size(24.dp))
            }
        } else if (state.ghost_aliases.isEmpty()) {
            AsterCard(modifier = Modifier.fillMaxWidth()) {
                detail_row(
                    title = stringResource(R.string.no_ghost_aliases),
                    subtitle = state.error ?: stringResource(R.string.no_ghost_aliases_subtitle),
                )
            }
        } else {
            section_label(stringResource(R.string.ghosts_count, state.ghost_aliases.size))
            AsterCard(modifier = Modifier.fillMaxWidth()) {
                state.ghost_aliases.forEachIndexed { idx, g ->
                    Column(
                        modifier = Modifier.fillMaxWidth().padding(horizontal = AsterSpacing.lg, vertical = AsterSpacing.md),
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Column(Modifier.weight(1f)) {
                                Text(
                                    g.address.ifBlank { "Ghost #${g.id.take(8)}" },
                                    color = colors.text_primary,
                                    fontSize = 14.sp,
                                    fontWeight = FontWeight.Medium,
                                )
                                if (g.note.isNotEmpty()) {
                                    Spacer(Modifier.height(2.dp))
                                    Text(
                                        g.note,
                                        color = colors.text_secondary,
                                        fontSize = 12.sp,
                                    )
                                }
                            }
                            if (g.enabled) {
                                Row(horizontalArrangement = Arrangement.spacedBy(AsterSpacing.sm)) {
                                    AsterGhostButton(label = stringResource(R.string.extend), onClick = { vm.extend_ghost_alias(g.id) })
                                    AsterGhostButton(label = stringResource(R.string.expire), onClick = { vm.expire_ghost_alias(g.id) })
                                }
                            } else {
                                Text(stringResource(R.string.expired), color = colors.text_muted, fontSize = 12.sp)
                            }
                        }
                        Spacer(Modifier.height(4.dp))
                        Row(horizontalArrangement = Arrangement.spacedBy(AsterSpacing.md)) {
                            Text(
                                "${g.forward_count} forwarded",
                                color = colors.text_tertiary,
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Medium,
                            )
                            if (!g.expires_at.isNullOrBlank()) {
                                Text(
                                    "Expires ${g.expires_at}",
                                    color = if (g.enabled) colors.text_tertiary else colors.text_muted,
                                    fontSize = 11.sp,
                                )
                            }
                        }
                    }
                    if (idx < state.ghost_aliases.lastIndex) AsterDivider(modifier = Modifier)
                }
            }
        }
        v_gap(AsterSpacing.xxl)
    }

    if (show_create_dialog) {
        androidx.compose.material3.AlertDialog(
            onDismissRequest = { if (!is_creating) { show_create_dialog = false; create_note = "" } },
            containerColor = colors.bg_card,
            title = {
                Text("Create Ghost Alias", color = colors.text_primary, fontSize = 17.sp, fontWeight = FontWeight.SemiBold)
            },
            text = {
                Column {
                    Text("Note (optional)", color = colors.text_muted, fontSize = 12.sp)
                    Spacer(Modifier.height(AsterSpacing.sm))
                    text_area(
                        value = create_note,
                        placeholder = "e.g. Newsletter signup",
                        on_change = { create_note = it },
                        min_height = 80,
                    )
                }
            },
            confirmButton = {
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(10.dp))
                        .background(colors.accent_blue)
                        .clickable(enabled = !is_creating) {
                            is_creating = true
                            scope.launch {
                                val result = vm.create_ghost_alias_now(create_note.trim())
                                is_creating = false
                                show_create_dialog = false
                                create_note = ""
                                when (result) {
                                    is SettingsViewModel.GhostAliasResult.Success ->
                                        Toast.makeText(context, "Created: ${result.address}", Toast.LENGTH_LONG).show()
                                    is SettingsViewModel.GhostAliasResult.Failure ->
                                        Toast.makeText(context, result.message, Toast.LENGTH_SHORT).show()
                                }
                            }
                        }
                        .padding(horizontal = AsterSpacing.lg, vertical = AsterSpacing.sm),
                ) {
                    Text(
                        if (is_creating) "Creating..." else "Create",
                        color = Color.White,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Medium,
                    )
                }
            },
            dismissButton = {
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(10.dp))
                        .border(1.dp, colors.border_primary, RoundedCornerShape(10.dp))
                        .clickable(enabled = !is_creating) { show_create_dialog = false; create_note = "" }
                        .padding(horizontal = AsterSpacing.lg, vertical = AsterSpacing.sm),
                ) {
                    Text(stringResource(R.string.cancel), color = colors.text_primary, fontSize = 14.sp)
                }
            },
        )
    }
}

@Composable
fun ReferralScreen(on_back: () -> Unit, on_open: (id: String) -> Unit = {}) {
    val vm: SettingsViewModel = hiltViewModel()
    val state by vm.state.collectAsStateWithLifecycle()
    val colors = AsterMaterial.colors
    val context = LocalContext.current

    LaunchedEffect(Unit) { vm.load_referral_info() }

    val referral = state.referral
    val link = referral?.referral_link ?: ""

    val link_copied_text = stringResource(R.string.link_copied)

    val refer_earned_cents = (referral?.credits_earned_cents ?: 0L) +
        (referral?.commission_earned_cents ?: 0L)

    detail_scaffold(title = stringResource(R.string.credits_title), on_back = on_back) {
        Column(Modifier.padding(horizontal = AsterSpacing.lg, vertical = AsterSpacing.md)) {
            Text(
                stringResource(R.string.credits_title),
                color = colors.text_primary,
                fontSize = 22.sp,
                fontWeight = FontWeight.SemiBold,
            )
            Spacer(Modifier.height(AsterSpacing.xs))
            Text(
                stringResource(R.string.credits_subtitle),
                color = colors.text_muted,
                fontSize = 13.sp,
            )
        }
        v_gap(AsterSpacing.md)

        if (referral == null) {
            Box(
                modifier = Modifier.fillMaxWidth().padding(AsterSpacing.xxl),
                contentAlignment = Alignment.Center,
            ) {
                CircularProgressIndicator(color = colors.accent_blue, modifier = Modifier.size(24.dp))
            }
        } else {
            Column {
                credit_task_row(
                    amount_cents = 1000,
                    title = stringResource(R.string.credit_task_refer_title),
                    hint = stringResource(R.string.credit_task_refer_hint),
                    earned_cents = refer_earned_cents,
                    cta_label = stringResource(R.string.credit_task_refer_cta),
                    on_action = {
                        if (link.isNotEmpty()) {
                            val cm = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                            cm.setPrimaryClip(ClipData.newPlainText(context.getString(R.string.clipboard_label_referral), link))
                            Toast.makeText(context, link_copied_text, Toast.LENGTH_SHORT).show()
                        }
                    },
                )
            }

            v_gap(AsterSpacing.xl)
            Column(Modifier.padding(horizontal = AsterSpacing.lg)) {
                Text(
                    text = stringResource(R.string.your_referral_link).uppercase(),
                    color = colors.text_muted,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Medium,
                )
                Spacer(Modifier.height(AsterSpacing.sm))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    SelectionContainer(modifier = Modifier.weight(1f)) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(10.dp))
                                .background(colors.bg_card)
                                .border(1.dp, colors.border_primary, RoundedCornerShape(10.dp))
                                .padding(horizontal = AsterSpacing.md, vertical = 10.dp),
                        ) {
                            Text(
                                text = link,
                                color = colors.text_primary,
                                fontSize = 13.sp,
                            )
                        }
                    }
                    Spacer(Modifier.width(AsterSpacing.sm))
                    Box(
                        modifier = Modifier
                            .height(36.dp)
                            .clip(RoundedCornerShape(10.dp))
                            .background(colors.bg_card)
                            .border(1.dp, colors.border_primary, RoundedCornerShape(10.dp))
                            .clickable {
                                if (link.isNotEmpty()) {
                                    val cm = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                                    cm.setPrimaryClip(ClipData.newPlainText(context.getString(R.string.clipboard_label_referral), link))
                                    Toast.makeText(context, link_copied_text, Toast.LENGTH_SHORT).show()
                                }
                            }
                            .padding(horizontal = AsterSpacing.md),
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(
                            text = stringResource(R.string.copy_link),
                            color = colors.text_primary,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Medium,
                        )
                    }
                }
            }

            v_gap(AsterSpacing.xl)
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = AsterSpacing.lg),
                verticalArrangement = Arrangement.spacedBy(AsterSpacing.sm),
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(AsterSpacing.sm),
                ) {
                    referral_stat_card(
                        modifier = Modifier.weight(1f),
                        label = stringResource(R.string.total_referrals),
                        value = referral.total_referrals.toString(),
                    )
                    referral_stat_card(
                        modifier = Modifier.weight(1f),
                        label = stringResource(R.string.pending),
                        value = referral.pending_referrals.toString(),
                        value_color = colors.warning,
                    )
                }
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(AsterSpacing.sm),
                ) {
                    referral_stat_card(
                        modifier = Modifier.weight(1f),
                        label = stringResource(R.string.completed),
                        value = referral.completed_referrals.toString(),
                        value_color = colors.success,
                    )
                    referral_stat_card(
                        modifier = Modifier.weight(1f),
                        label = stringResource(R.string.total_earned),
                        value = format_cents(refer_earned_cents),
                    )
                }
            }

            v_gap(AsterSpacing.xl)
            Column(Modifier.padding(horizontal = AsterSpacing.lg)) {
                Text(
                    text = stringResource(R.string.how_it_works).uppercase(),
                    color = colors.text_muted,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Medium,
                )
                Spacer(Modifier.height(AsterSpacing.sm))
                referral_step(index = 1, text = stringResource(R.string.referral_step_1))
                referral_step(index = 2, text = stringResource(R.string.referral_step_2))
                referral_step(index = 3, text = stringResource(R.string.referral_step_3))
            }
        }
        v_gap(AsterSpacing.xxl)
    }
}

private fun format_cents(cents: Long): String {
    val dollars = cents / 100
    val rem = (cents % 100).toInt()
    return "$" + dollars.toString() + "." + rem.toString().padStart(2, '0')
}

@Composable
private fun referral_stat_card(
    modifier: Modifier = Modifier,
    label: String,
    value: String,
    value_color: Color? = null,
) {
    val colors = AsterMaterial.colors
    Column(
        modifier = modifier
            .clip(RoundedCornerShape(10.dp))
            .background(colors.bg_card)
            .border(1.dp, colors.border_primary, RoundedCornerShape(10.dp))
            .padding(horizontal = AsterSpacing.md, vertical = AsterSpacing.sm),
    ) {
        Text(
            text = value,
            color = value_color ?: colors.text_primary,
            fontSize = 16.sp,
            fontWeight = FontWeight.SemiBold,
        )
        Spacer(Modifier.height(2.dp))
        Text(
            text = label,
            color = colors.text_muted,
            fontSize = 11.sp,
        )
    }
}

@Composable
private fun referral_step(index: Int, text: String) {
    val colors = AsterMaterial.colors
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = AsterSpacing.xs),
        verticalAlignment = Alignment.Top,
    ) {
        Box(
            modifier = Modifier
                .size(22.dp)
                .clip(CircleShape)
                .background(colors.bg_card)
                .border(1.dp, colors.border_primary, CircleShape),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                text = index.toString(),
                color = colors.text_secondary,
                fontSize = 12.sp,
                fontWeight = FontWeight.SemiBold,
            )
        }
        Spacer(Modifier.width(AsterSpacing.md))
        Text(
            text = text,
            color = colors.text_secondary,
            fontSize = 13.sp,
            modifier = Modifier.padding(top = 2.dp),
        )
    }
}

@Composable
private fun credit_task_row(
    amount_cents: Int,
    title: String,
    hint: String,
    earned_cents: Long,
    cta_label: String?,
    on_action: (() -> Unit)?,
    disabled: Boolean = false,
) {
    val colors = AsterMaterial.colors
    val is_completed = earned_cents > 0
    val row_alpha = if (disabled) 0.6f else 1f

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = AsterSpacing.lg, vertical = AsterSpacing.md),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier = Modifier
                .size(44.dp)
                .clip(RoundedCornerShape(10.dp))
                .background(
                    if (is_completed) colors.success.copy(alpha = 0.15f)
                    else colors.bg_card,
                ),
            contentAlignment = Alignment.Center,
        ) {
            if (is_completed) {
                androidx.compose.material3.Icon(
                    imageVector = TablerIcons.Check,
                    contentDescription = null,
                    tint = colors.success,
                    modifier = Modifier.size(20.dp),
                )
            } else if (amount_cents > 0) {
                Text(
                    text = "$${amount_cents / 100}",
                    color = colors.text_secondary,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.SemiBold,
                )
            } else {
                Text(
                    text = "—",
                    color = colors.text_muted,
                    fontSize = 13.sp,
                )
            }
        }
        Spacer(Modifier.width(AsterSpacing.md))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                color = colors.text_primary.copy(alpha = row_alpha),
                fontSize = 14.sp,
                fontWeight = FontWeight.Medium,
            )
            Spacer(Modifier.height(2.dp))
            Text(
                text = hint,
                color = colors.text_muted.copy(alpha = row_alpha),
                fontSize = 12.sp,
            )
        }
        Spacer(Modifier.width(AsterSpacing.md))
        when {
            is_completed -> Text(
                text = stringResource(R.string.credit_task_earned),
                color = colors.success,
                fontSize = 12.sp,
                fontWeight = FontWeight.Medium,
            )
            disabled || cta_label == null || on_action == null -> {}
            else -> Box(
                modifier = Modifier
                    .height(32.dp)
                    .clip(RoundedCornerShape(10.dp))
                    .background(colors.bg_card)
                    .border(1.dp, colors.border_primary, RoundedCornerShape(10.dp))
                    .clickable(onClick = on_action)
                    .padding(horizontal = AsterSpacing.md),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = cta_label,
                    color = colors.text_primary,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Medium,
                )
            }
        }
    }
}

@Composable
fun DeveloperScreen(on_back: () -> Unit, on_open: (id: String) -> Unit = {}) {
    val vm: SettingsViewModel = hiltViewModel()
    val state by vm.state.collectAsStateWithLifecycle()
    val colors = AsterMaterial.colors
    val prefs = state.preferences

    LaunchedEffect(Unit) { vm.load_preferences() }

    val prefs_seeded = prefs != null && state.preferences_authoritative
    var dev_mode by remember(prefs_seeded) { mutableStateOf(prefs?.dev_mode ?: false) }
    var show_raw_headers by remember(prefs_seeded) { mutableStateOf(prefs?.show_raw_headers ?: false) }
    var allow_insecure by remember(prefs_seeded) { mutableStateOf(prefs?.allow_insecure ?: false) }
    var verbose_logs by remember(prefs_seeded) { mutableStateOf(prefs?.verbose_logs ?: false) }
    var save_trigger by remember { mutableIntStateOf(0) }
    var prefs_loaded_dev by remember { mutableStateOf(false) }

    LaunchedEffect(prefs, state.preferences_authoritative) {
        if (prefs != null && state.preferences_authoritative && !prefs_loaded_dev) {
            prefs_loaded_dev = true
            dev_mode = prefs.dev_mode
            show_raw_headers = prefs.show_raw_headers
            allow_insecure = prefs.allow_insecure
            verbose_logs = prefs.verbose_logs
        }
    }

    fun save() {
        val base = prefs ?: return
        vm.save_preferences(
            base.copy(
                dev_mode = dev_mode,
                show_raw_headers = show_raw_headers,
                allow_insecure = allow_insecure,
                verbose_logs = verbose_logs,
            ),
        )
    }

    LaunchedEffect(save_trigger) {
        if (save_trigger == 0) return@LaunchedEffect
        if (!prefs_loaded_dev || prefs == null) return@LaunchedEffect
        delay(500)
        save()
    }

    androidx.compose.runtime.DisposableEffect(Unit) {
        onDispose {
            if (save_trigger > 0 && prefs != null && prefs_loaded_dev) {
                save()
            }
        }
    }

    detail_scaffold(
        title = stringResource(R.string.developer),
        on_back = on_back,
    ) {
        if (prefs == null) {
            Box(
                modifier = Modifier.fillMaxWidth().padding(AsterSpacing.xxl),
                contentAlignment = Alignment.Center,
            ) {
                CircularProgressIndicator(color = colors.accent_blue, modifier = Modifier.size(24.dp))
            }
        } else {
            section_label(stringResource(R.string.mode))
            AsterCard(modifier = Modifier.fillMaxWidth()) {
                toggle_row(stringResource(R.string.developer_mode), stringResource(R.string.developer_mode_subtitle), dev_mode) { dev_mode = it; save_trigger++ }
                AsterDivider(modifier = Modifier)
                toggle_row(stringResource(R.string.show_raw_headers), stringResource(R.string.show_raw_headers_subtitle), show_raw_headers) { show_raw_headers = it; save_trigger++ }
                AsterDivider(modifier = Modifier)
                toggle_row(stringResource(R.string.allow_insecure), stringResource(R.string.allow_insecure_subtitle), allow_insecure) { allow_insecure = it; save_trigger++ }
                AsterDivider(modifier = Modifier)
                toggle_row(stringResource(R.string.verbose_logs), stringResource(R.string.verbose_logs_subtitle), verbose_logs) { verbose_logs = it; save_trigger++ }
            }
            v_gap(AsterSpacing.lg)
            section_label(stringResource(R.string.tools))
            val context = LocalContext.current
            var cache_cleared by remember { mutableStateOf(false) }
            val cache_cleared_text = stringResource(R.string.cache_cleared)
            val cache_clear_failed_text = stringResource(R.string.cache_clear_failed)
            AsterCard(modifier = Modifier.fillMaxWidth()) {
                detail_row(
                    title = if (cache_cleared) stringResource(R.string.cache_cleared) else stringResource(R.string.clear_local_cache),
                    subtitle = stringResource(R.string.cache_resets_subtitle),
                    icon = TablerIcons.Trash,
                    on_click = {
                        try {
                            context.cacheDir.deleteRecursively()
                            cache_cleared = true
                            Toast.makeText(context, cache_cleared_text, Toast.LENGTH_SHORT).show()
                        } catch (_: Throwable) {
                            Toast.makeText(context, cache_clear_failed_text, Toast.LENGTH_SHORT).show()
                        }
                    },
                )
            }
        }
        v_gap(AsterSpacing.xxl)
    }
}

@Composable
fun LabelsScreen(on_back: () -> Unit, on_open: (id: String) -> Unit = {}) {
    val vm: SettingsViewModel = hiltViewModel()
    val state by vm.state.collectAsStateWithLifecycle()
    val colors = AsterMaterial.colors

    LaunchedEffect(Unit) { vm.load_labels(folder_type = "label") }

    val labels = state.labels.filter { it.folder_type == "label" }

    detail_scaffold(title = stringResource(R.string.labels), on_back = on_back) {
        if (state.is_loading && labels.isEmpty()) {
            Box(
                modifier = Modifier.fillMaxWidth().padding(AsterSpacing.xxl),
                contentAlignment = Alignment.Center,
            ) {
                CircularProgressIndicator(color = colors.accent_blue, modifier = Modifier.size(24.dp))
            }
        } else if (labels.isEmpty()) {
            AsterCard(modifier = Modifier.fillMaxWidth()) {
                detail_row(
                    title = stringResource(R.string.no_labels),
                    subtitle = state.error ?: stringResource(R.string.no_labels_subtitle),
                )
            }
        } else {
            section_label(stringResource(R.string.labels_count, labels.size))
            AsterCard(modifier = Modifier.fillMaxWidth()) {
                labels.forEachIndexed { idx, l ->
                    val label_color = try {
                        l.encrypted_color?.let { Color(android.graphics.Color.parseColor(it)) }
                    } catch (_: Throwable) { null } ?: Color(0xFF6B7280)
                    val label_name = l.encrypted_name ?: l.label_token
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(horizontal = AsterSpacing.lg, vertical = AsterSpacing.md),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Box(modifier = Modifier.size(12.dp).background(label_color, CircleShape))
                        Spacer(Modifier.width(AsterSpacing.md))
                        Column(Modifier.weight(1f)) {
                            Text(label_name, color = colors.text_primary, fontSize = 15.sp, fontWeight = FontWeight.Medium)
                            val count_text = l.item_count?.let { stringResource(R.string.messages_count, it) } ?: ""
                            if (count_text.isNotEmpty()) {
                                Text(count_text, color = colors.text_tertiary, fontSize = 13.sp)
                            }
                        }
                        if (!l.is_system && !l.is_locked) {
                            Box(
                                modifier = Modifier
                                    .size(36.dp)
                                    .clickable { vm.delete_label(l.id) }
                                    .padding(AsterSpacing.xs),
                                contentAlignment = Alignment.Center,
                            ) {
                                androidx.compose.material3.Icon(
                                    imageVector = TablerIcons.Trash,
                                    contentDescription = stringResource(R.string.delete_label),
                                    tint = colors.text_tertiary,
                                    modifier = Modifier.size(18.dp),
                                )
                            }
                        }
                    }
                    if (idx < labels.lastIndex) AsterDivider(modifier = Modifier)
                }
            }
        }
        v_gap(AsterSpacing.xxl)
    }
}

@Composable
fun FoldersScreen(on_back: () -> Unit, on_open: (id: String) -> Unit = {}) {
    val vm: SettingsViewModel = hiltViewModel()
    val state by vm.state.collectAsStateWithLifecycle()
    val colors = AsterMaterial.colors

    LaunchedEffect(Unit) { vm.load_labels(folder_type = "folder") }

    val folder_nodes = flatten_folder_tree(state.labels)

    detail_scaffold(title = stringResource(R.string.folders), on_back = on_back) {
        if (state.is_loading && folder_nodes.isEmpty()) {
            Box(
                modifier = Modifier.fillMaxWidth().padding(AsterSpacing.xxl),
                contentAlignment = Alignment.Center,
            ) {
                CircularProgressIndicator(color = colors.accent_blue, modifier = Modifier.size(24.dp))
            }
        } else if (folder_nodes.isEmpty()) {
            AsterCard(modifier = Modifier.fillMaxWidth()) {
                detail_row(
                    title = stringResource(R.string.no_folders),
                    subtitle = state.error ?: stringResource(R.string.no_folders_subtitle),
                )
            }
        } else {
            section_label(stringResource(R.string.folders_count, folder_nodes.size))
            AsterCard(modifier = Modifier.fillMaxWidth()) {
                folder_nodes.forEachIndexed { idx, node ->
                    val f = node.label
                    val folder_name = f.encrypted_name ?: f.label_token
                    val count_text = f.item_count?.let { stringResource(R.string.messages_count, it) } ?: ""
                    val siblings = folder_sibling_group(state.labels, f.id)
                    val sibling_index = siblings.indexOfFirst { it.id == f.id }
                    val can_move_up = sibling_index > 0
                    val can_move_down = sibling_index in 0 until siblings.lastIndex
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        if (node.depth > 0) Spacer(Modifier.width((node.depth * 16).dp))
                        Box(modifier = Modifier.weight(1f)) {
                            detail_row(
                                title = folder_name,
                                subtitle = count_text,
                                icon = TablerIcons.Folder,
                                on_click = {},
                                trailing = {
                                    Box(
                                        modifier = Modifier
                                            .size(32.dp)
                                            .clip(CircleShape)
                                            .clickable(enabled = can_move_up) { vm.move_folder(f.id, -1) },
                                        contentAlignment = Alignment.Center,
                                    ) {
                                        androidx.compose.material3.Icon(
                                            imageVector = TablerIcons.ChevronUp,
                                            contentDescription = stringResource(R.string.move_folder_up),
                                            tint = if (can_move_up) colors.text_secondary else colors.text_muted.copy(alpha = 0.35f),
                                            modifier = Modifier.size(20.dp),
                                        )
                                    }
                                    Box(
                                        modifier = Modifier
                                            .size(32.dp)
                                            .clip(CircleShape)
                                            .clickable(enabled = can_move_down) { vm.move_folder(f.id, 1) },
                                        contentAlignment = Alignment.Center,
                                    ) {
                                        androidx.compose.material3.Icon(
                                            imageVector = TablerIcons.ChevronDown,
                                            contentDescription = stringResource(R.string.move_folder_down),
                                            tint = if (can_move_down) colors.text_secondary else colors.text_muted.copy(alpha = 0.35f),
                                            modifier = Modifier.size(20.dp),
                                        )
                                    }
                                    if (!f.is_system && !f.is_locked) {
                                        Box(
                                            modifier = Modifier
                                                .size(32.dp)
                                                .clickable { vm.delete_label(f.id) },
                                            contentAlignment = Alignment.Center,
                                        ) {
                                            androidx.compose.material3.Icon(
                                                imageVector = TablerIcons.Trash,
                                                contentDescription = stringResource(R.string.delete_folder),
                                                tint = colors.text_tertiary,
                                                modifier = Modifier.size(18.dp),
                                            )
                                        }
                                    }
                                },
                            )
                        }
                    }
                    if (idx < folder_nodes.lastIndex) AsterDivider(modifier = Modifier)
                }
            }
        }
        v_gap(AsterSpacing.xxl)
    }
}

@Composable
fun PrivacyScreen(on_back: () -> Unit, on_open: (id: String) -> Unit = {}) {
    val vm: SettingsViewModel = hiltViewModel()
    val state by vm.state.collectAsStateWithLifecycle()
    val colors = AsterMaterial.colors
    val context = LocalContext.current
    val prefs = state.preferences

    LaunchedEffect(Unit) { vm.load_preferences() }

    val prefs_seeded = prefs != null
    var block_trackers by remember(prefs_seeded) { mutableStateOf(prefs?.block_trackers ?: true) }
    var remote_images by remember(prefs_seeded) { mutableStateOf(prefs?.load_remote_images == "always") }
    var send_receipts by remember(prefs_seeded) { mutableStateOf(prefs?.send_read_receipts ?: false) }
    var link_warnings by remember(prefs_seeded) { mutableStateOf(prefs?.warn_suspicious_links ?: true) }
    var strip_exif by remember(prefs_seeded) { mutableStateOf(prefs?.strip_exif_on_compose ?: true) }
    var ghost_mode by remember(prefs_seeded) { mutableStateOf(prefs?.ghost_mode ?: false) }
    var save_trigger by remember { mutableIntStateOf(0) }
    var prefs_loaded_priv by remember { mutableStateOf(false) }

    LaunchedEffect(prefs) {
        if (prefs != null && !prefs_loaded_priv) {
            prefs_loaded_priv = true
            block_trackers = prefs.block_trackers
            remote_images = prefs.load_remote_images == "always"
            send_receipts = prefs.send_read_receipts
            link_warnings = prefs.warn_suspicious_links
            strip_exif = prefs.strip_exif_on_compose
            ghost_mode = prefs.ghost_mode
        }
    }

    fun save() {
        val base = prefs ?: return
        vm.save_preferences(
            base.copy(
                block_trackers = block_trackers,
                load_remote_images = if (remote_images) "always" else "never",
                send_read_receipts = send_receipts,
                warn_suspicious_links = link_warnings,
                strip_exif = strip_exif,
                strip_exif_on_compose = strip_exif,
                ghost_mode = ghost_mode,
            ),
        )
    }

    LaunchedEffect(save_trigger) {
        if (save_trigger == 0) return@LaunchedEffect
        if (!prefs_loaded_priv || prefs == null) return@LaunchedEffect
        delay(500)
        save()
    }

    androidx.compose.runtime.DisposableEffect(Unit) {
        onDispose {
            if (save_trigger > 0 && prefs != null && prefs_loaded_priv) {
                save()
            }
        }
    }

    detail_scaffold(
        title = stringResource(R.string.privacy),
        on_back = on_back,
    ) {
        if (prefs == null) {
            Box(
                modifier = Modifier.fillMaxWidth().padding(AsterSpacing.xxl),
                contentAlignment = Alignment.Center,
            ) {
                CircularProgressIndicator(color = colors.accent_blue, modifier = Modifier.size(24.dp))
            }
        } else {
            section_label(stringResource(R.string.tracking))
            AsterCard(modifier = Modifier.fillMaxWidth()) {
                toggle_row(stringResource(R.string.block_tracking_pixels_privacy), stringResource(R.string.block_tracking_pixels_subtitle), block_trackers) { block_trackers = it; save_trigger++ }
                AsterDivider(modifier = Modifier)
                toggle_row(stringResource(R.string.load_remote_images), stringResource(R.string.load_remote_images_subtitle), remote_images) { remote_images = it; save_trigger++ }
                AsterDivider(modifier = Modifier)
                toggle_row(stringResource(R.string.send_read_receipts), stringResource(R.string.send_read_receipts_subtitle), send_receipts) { send_receipts = it; save_trigger++ }
            }
            v_gap(AsterSpacing.lg)
            section_label(stringResource(R.string.protection))
            AsterCard(modifier = Modifier.fillMaxWidth()) {
                toggle_row(stringResource(R.string.warn_suspicious_links), null, link_warnings) { link_warnings = it; save_trigger++ }
                AsterDivider(modifier = Modifier)
                toggle_row(stringResource(R.string.strip_exif), stringResource(R.string.strip_exif_subtitle), strip_exif) { strip_exif = it; save_trigger++ }
                AsterDivider(modifier = Modifier)
                toggle_row(stringResource(R.string.ghost_mode), stringResource(R.string.ghost_mode_subtitle), ghost_mode) { ghost_mode = it; save_trigger++ }
            }
            v_gap(AsterSpacing.lg)
            detail_row(
                title = stringResource(R.string.privacy_policy),
                icon = TablerIcons.ShieldLock,
                on_click = { context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("https://astermail.org/privacy"))) },
            )
        }
        v_gap(AsterSpacing.xxl)
    }
}

@Composable
fun ApiKeysScreen(on_back: () -> Unit, on_open: (id: String) -> Unit = {}) {
    val vm: SettingsViewModel = hiltViewModel()
    val state by vm.state.collectAsStateWithLifecycle()
    val colors = AsterMaterial.colors
    val context = LocalContext.current
    var pending_revoke by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(state.action_result) {
        val msg = state.action_result ?: return@LaunchedEffect
        android.widget.Toast.makeText(context, msg, android.widget.Toast.LENGTH_SHORT).show()
        vm.clear_action_result()
    }

    LaunchedEffect(Unit) { vm.load_api_keys() }

    detail_scaffold(title = stringResource(R.string.api_keys), on_back = on_back) {
        if (state.is_loading && state.api_keys.isEmpty()) {
            Box(
                modifier = Modifier.fillMaxWidth().padding(AsterSpacing.xxl),
                contentAlignment = Alignment.Center,
            ) {
                CircularProgressIndicator(color = colors.accent_blue, modifier = Modifier.size(24.dp))
            }
        } else if (state.api_keys.isEmpty()) {
            AsterCard(modifier = Modifier.fillMaxWidth()) {
                detail_row(
                    title = stringResource(R.string.no_api_keys),
                    subtitle = state.error ?: stringResource(R.string.no_api_keys_subtitle),
                )
            }
        } else {
            section_label(stringResource(R.string.api_keys_count, state.api_keys.size))
            AsterCard(modifier = Modifier.fillMaxWidth()) {
                state.api_keys.forEachIndexed { idx, k ->
                    detail_row(
                        title = k.decrypted_name.ifBlank { stringResource(R.string.api_key_default_name) },
                        subtitle = "${k.prefix}... - created ${k.created_at ?: ""}",
                        icon = TablerIcons.Plug,
                        on_click = {},
                        trailing = {
                            AsterGhostButton(label = stringResource(R.string.revoke), onClick = { pending_revoke = k.id })
                        },
                    )
                    if (idx < state.api_keys.lastIndex) AsterDivider(modifier = Modifier)
                }
            }
        }
        v_gap(AsterSpacing.lg)
        val mobile_key_name = stringResource(R.string.mobile_key)
        AsterButton(label = stringResource(R.string.generate_new_key), onClick = { vm.create_api_key(mobile_key_name) })
        v_gap(AsterSpacing.xxl)
    }

    pending_revoke?.let { rid ->
        org.astermail.android.design.components.AsterDialog(
            on_dismiss = { pending_revoke = null },
            title = stringResource(R.string.revoke_api_key_title),
            message = stringResource(R.string.revoke_api_key_confirm_message),
            footer = {
                org.astermail.android.design.components.AsterDialogOutlineButton(
                    label = stringResource(R.string.cancel),
                    onClick = { pending_revoke = null },
                )
                org.astermail.android.design.components.AsterDialogDestructiveButton(
                    label = stringResource(R.string.revoke),
                    onClick = {
                        vm.revoke_api_key(rid)
                        pending_revoke = null
                    },
                )
            },
        )
    }
}

@Composable
fun IntegrationsScreen(on_back: () -> Unit, on_open: (id: String) -> Unit = {}) {
    val vm: SettingsViewModel = hiltViewModel()
    val state by vm.state.collectAsStateWithLifecycle()
    val colors = AsterMaterial.colors
    val prefs = state.preferences

    LaunchedEffect(Unit) {
        vm.load_preferences()
        vm.load_webhooks()
    }

    val context = LocalContext.current

    detail_scaffold(title = stringResource(R.string.integrations), on_back = on_back) {
        section_label(stringResource(R.string.connected_apps))
        AsterCard(modifier = Modifier.fillMaxWidth()) {
            detail_row(
                title = stringResource(R.string.slack),
                subtitle = stringResource(R.string.slack_subtitle),
                icon = TablerIcons.Puzzle,
                on_click = {
                    context.startActivity(
                        Intent(Intent.ACTION_VIEW, Uri.parse("https://app.astermail.org/settings/integrations")),
                    )
                },
            )
            AsterDivider(modifier = Modifier)
            detail_row(
                title = stringResource(R.string.calendar),
                subtitle = stringResource(R.string.calendar_subtitle),
                icon = TablerIcons.Puzzle,
                on_click = {
                    context.startActivity(
                        Intent(Intent.ACTION_VIEW, Uri.parse("https://app.astermail.org/settings/integrations")),
                    )
                },
            )
            AsterDivider(modifier = Modifier)
            detail_row(
                title = stringResource(R.string.zapier),
                subtitle = stringResource(R.string.zapier_subtitle),
                icon = TablerIcons.Puzzle,
                on_click = {
                    context.startActivity(
                        Intent(Intent.ACTION_VIEW, Uri.parse("https://app.astermail.org/settings/integrations")),
                    )
                },
            )
        }
        v_gap(AsterSpacing.lg)
        section_label(stringResource(R.string.webhooks))
        AsterCard(modifier = Modifier.fillMaxWidth()) {
            val webhook_count = state.webhooks.size
            val subtitle = if (webhook_count == 0) stringResource(R.string.no_active_endpoints) else stringResource(R.string.active_endpoints, webhook_count)
            detail_row(
                title = stringResource(R.string.manage_webhooks),
                subtitle = subtitle,
                icon = TablerIcons.Link,
                on_click = {
                    context.startActivity(
                        Intent(Intent.ACTION_VIEW, Uri.parse("https://app.astermail.org/settings/integrations")),
                    )
                },
            )
        }
        v_gap(AsterSpacing.xxl)
    }
}

@Composable
fun FamilyScreen(on_back: () -> Unit, on_open: (id: String) -> Unit = {}) {
    val vm: SettingsViewModel = hiltViewModel()
    val state by vm.state.collectAsStateWithLifecycle()
    val colors = AsterMaterial.colors
    val context = LocalContext.current

    LaunchedEffect(Unit) { if (state.subscription == null) vm.load_subscription() }

    val sub = state.subscription
    val is_family = sub?.effective_plan_name?.contains("family", ignoreCase = true) == true

    detail_scaffold(title = stringResource(R.string.family_plan), on_back = on_back) {
        if (state.is_loading && sub == null) {
            Box(
                modifier = Modifier.fillMaxWidth().padding(AsterSpacing.xxl),
                contentAlignment = Alignment.Center,
            ) {
                CircularProgressIndicator(color = colors.accent_blue, modifier = Modifier.size(24.dp))
            }
        } else if (is_family) {
            AsterCard(modifier = Modifier.fillMaxWidth()) {
                detail_row(
                    title = stringResource(R.string.manage_family),
                    subtitle = stringResource(R.string.manage_family_subtitle),
                    icon = TablerIcons.Users,
                    on_click = {
                        context.startActivity(
                            Intent(Intent.ACTION_VIEW, Uri.parse("https://app.astermail.org/settings/family")),
                        )
                    },
                )
                AsterDivider(modifier = Modifier)
                detail_row(
                    title = stringResource(R.string.kids_reserved_addresses),
                    subtitle = stringResource(R.string.kids_reserved_subtitle),
                    icon = TablerIcons.Users,
                    on_click = { on_open("family_kids") },
                )
            }
        } else {
            AsterCard(modifier = Modifier.fillMaxWidth()) {
                Column(Modifier.padding(AsterSpacing.lg)) {
                    Text(
                        text = stringResource(R.string.no_family_plan),
                        color = colors.text_primary,
                        fontSize = 15.sp,
                        fontWeight = FontWeight.Medium,
                    )
                    Spacer(Modifier.height(AsterSpacing.xs))
                    Text(
                        text = stringResource(R.string.no_family_plan_subtitle),
                        color = colors.text_secondary,
                        fontSize = 13.sp,
                    )
                }
            }
            v_gap(AsterSpacing.lg)
            AsterButton(
                label = stringResource(R.string.view_plans),
                onClick = {
                    context.startActivity(
                        Intent(Intent.ACTION_VIEW, Uri.parse("https://app.astermail.org/settings/billing")),
                    )
                },
            )
        }
        v_gap(AsterSpacing.xxl)
    }
}

@Composable
fun KidsReservedScreen(on_back: () -> Unit) {
    val vm: SettingsViewModel = hiltViewModel()
    val state by vm.state.collectAsStateWithLifecycle()
    val colors = AsterMaterial.colors
    val context = LocalContext.current
    var release_target by remember { mutableStateOf<org.astermail.android.api.family.ReservedAddress?>(null) }

    LaunchedEffect(Unit) { vm.load_reserved_addresses() }

    fun copy_to_clipboard(text: String, label: String) {
        val cm = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        cm.setPrimaryClip(ClipData.newPlainText(label, text))
        Toast.makeText(context, label, Toast.LENGTH_SHORT).show()
    }

    detail_scaffold(title = stringResource(R.string.kids_reserved_addresses), on_back = on_back) {
        section_label(stringResource(R.string.kids_reserved_subtitle))

        if (state.family_max_members > 0) {
            Text(
                text = stringResource(R.string.kids_seats_used, state.family_seats_used, state.family_max_members),
                color = colors.text_tertiary,
                fontSize = 12.sp,
                modifier = androidx.compose.ui.Modifier.padding(bottom = AsterSpacing.sm),
            )
        }

        AsterButton(
            label = stringResource(R.string.kids_reserve_on_web),
            onClick = {
                context.startActivity(
                    Intent(Intent.ACTION_VIEW, Uri.parse("https://app.astermail.org/settings/family")),
                )
            },
        )

        v_gap(AsterSpacing.lg)

        if (state.is_loading && state.reserved_addresses.isEmpty()) {
            Box(
                modifier = androidx.compose.ui.Modifier.fillMaxWidth().padding(AsterSpacing.xxl),
                contentAlignment = Alignment.Center,
            ) {
                CircularProgressIndicator(color = colors.accent_blue, modifier = androidx.compose.ui.Modifier.size(24.dp))
            }
        } else if (state.reserved_addresses.isEmpty()) {
            AsterCard(modifier = androidx.compose.ui.Modifier.fillMaxWidth()) {
                detail_row(
                    title = stringResource(R.string.kids_no_reservations),
                    subtitle = state.error,
                )
            }
        } else {
            AsterCard(modifier = androidx.compose.ui.Modifier.fillMaxWidth()) {
                state.reserved_addresses.forEachIndexed { idx, r ->
                    Column(
                        modifier = androidx.compose.ui.Modifier
                            .fillMaxWidth()
                            .padding(horizontal = AsterSpacing.lg, vertical = AsterSpacing.md),
                    ) {
                        Row(
                            modifier = androidx.compose.ui.Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Column(modifier = androidx.compose.ui.Modifier.weight(1f)) {
                                Text(
                                    text = "${r.username}@${r.email_domain}",
                                    color = colors.text_primary,
                                    fontSize = 14.sp,
                                    fontWeight = FontWeight.Medium,
                                )
                                if (!r.label.isNullOrBlank()) {
                                    Spacer(androidx.compose.ui.Modifier.height(2.dp))
                                    Text(r.label ?: "", color = colors.text_secondary, fontSize = 12.sp)
                                }
                            }
                            Text(
                                text = if (r.status == "reserved") stringResource(R.string.kids_status_reserved)
                                       else stringResource(R.string.kids_status_active),
                                color = if (r.status == "reserved") colors.accent_blue else colors.success,
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Medium,
                            )
                        }
                        if (r.status == "reserved") {
                            Spacer(androidx.compose.ui.Modifier.height(AsterSpacing.sm))
                            Row(horizontalArrangement = Arrangement.spacedBy(AsterSpacing.sm)) {
                                AsterGhostButton(
                                    label = stringResource(R.string.kids_copy_link),
                                    onClick = {
                                        r.claim_url?.let { url ->
                                            copy_to_clipboard(url, context.getString(R.string.kids_link_copied))
                                        }
                                    },
                                )
                                AsterGhostButton(
                                    label = stringResource(R.string.kids_regenerate),
                                    onClick = {
                                        vm.regenerate_reservation_link(r.id) { new_url ->
                                            if (new_url != null) {
                                                copy_to_clipboard(new_url, context.getString(R.string.kids_link_regenerated))
                                            } else {
                                                Toast.makeText(context, context.getString(R.string.kids_action_failed), Toast.LENGTH_SHORT).show()
                                            }
                                        }
                                    },
                                )
                                AsterGhostButton(
                                    label = stringResource(R.string.kids_release),
                                    onClick = { release_target = r },
                                )
                            }
                        }
                    }
                    if (idx < state.reserved_addresses.lastIndex) AsterDivider(modifier = androidx.compose.ui.Modifier)
                }
            }
        }

        v_gap(AsterSpacing.xxl)
    }

    release_target?.let { target ->
        androidx.compose.material3.AlertDialog(
            onDismissRequest = { release_target = null },
            title = { Text(stringResource(R.string.kids_release_confirm_title), color = colors.text_primary) },
            text = {
                Text(
                    stringResource(R.string.kids_release_confirm_body, "${target.username}@${target.email_domain}"),
                    color = colors.text_secondary,
                )
            },
            confirmButton = {
                AsterButton(
                    label = stringResource(R.string.kids_release),
                    onClick = {
                        vm.release_reservation(target.id) { ok ->
                            if (ok) {
                                Toast.makeText(context, context.getString(R.string.kids_released), Toast.LENGTH_SHORT).show()
                            } else {
                                Toast.makeText(context, context.getString(R.string.kids_action_failed), Toast.LENGTH_SHORT).show()
                            }
                        }
                        release_target = null
                    },
                )
            },
            dismissButton = {
                AsterGhostButton(
                    label = stringResource(R.string.cancel),
                    onClick = { release_target = null },
                )
            },
            containerColor = colors.modal_bg,
        )
    }
}

@Composable
fun ConnectionScreen(on_back: () -> Unit, on_open: (id: String) -> Unit = {}) {
    val vm: SettingsViewModel = hiltViewModel()
    val state by vm.state.collectAsStateWithLifecycle()
    val colors = AsterMaterial.colors
    val prefs = state.preferences

    LaunchedEffect(Unit) { vm.load_preferences() }

    val prefs_seeded = prefs != null
    var low_network_mode by remember(prefs_seeded) { mutableStateOf(prefs?.low_network_mode ?: false) }
    var save_trigger by remember { mutableIntStateOf(0) }
    var prefs_loaded_conn by remember { mutableStateOf(false) }

    LaunchedEffect(prefs) {
        if (prefs != null && !prefs_loaded_conn) {
            prefs_loaded_conn = true
            low_network_mode = prefs.low_network_mode
        }
    }

    fun save() {
        val base = prefs ?: return
        vm.save_preferences(base.copy(low_network_mode = low_network_mode))
    }

    LaunchedEffect(save_trigger) {
        if (save_trigger == 0) return@LaunchedEffect
        if (!prefs_loaded_conn || prefs == null) return@LaunchedEffect
        delay(500)
        save()
    }

    androidx.compose.runtime.DisposableEffect(Unit) {
        onDispose {
            if (save_trigger > 0 && prefs != null && prefs_loaded_conn) {
                save()
            }
        }
    }

    detail_scaffold(title = stringResource(R.string.settings_connection), on_back = on_back) {
        if (prefs == null) {
            Box(
                modifier = Modifier.fillMaxWidth().padding(AsterSpacing.xxl),
                contentAlignment = Alignment.Center,
            ) {
                CircularProgressIndicator(color = colors.accent_blue, modifier = Modifier.size(24.dp))
            }
        } else {
            section_label(stringResource(R.string.network))
            AsterCard(modifier = Modifier.fillMaxWidth()) {
                toggle_row(
                    title = stringResource(R.string.low_network_mode),
                    subtitle = stringResource(R.string.low_network_mode_subtitle),
                    checked = low_network_mode,
                    on_change = { low_network_mode = it; save_trigger++ },
                )
            }
            v_gap(AsterSpacing.lg)
            AsterCard(modifier = Modifier.fillMaxWidth()) {
                Column(Modifier.padding(AsterSpacing.lg)) {
                    Text(
                        text = stringResource(R.string.low_network_mode_info_desc),
                        color = colors.text_secondary,
                        fontSize = 13.sp,
                    )
                }
            }
        }
        v_gap(AsterSpacing.xxl)
    }
}

@Composable
fun LanguageScreen(on_back: () -> Unit, on_open: (id: String) -> Unit = {}) {
    val vm: SettingsViewModel = hiltViewModel()
    val state by vm.state.collectAsStateWithLifecycle()
    val colors = AsterMaterial.colors
    val prefs = state.preferences

    LaunchedEffect(Unit) { vm.load_preferences() }

    val languages = listOf(
        "en" to "English",
        "es" to "Espanol",
        "fr" to "Francais",
        "de" to "Deutsch",
        "it" to "Italiano",
        "pt" to "Portugues",
        "ja" to "Japanese",
        "ko" to "Korean",
        "zh" to "Chinese (simplified)",
        "ru" to "Russian",
        "ar" to "Arabic",
        "hi" to "Hindi",
    )
    val prefs_seeded = prefs != null
    var selected by remember(prefs_seeded) { mutableStateOf(prefs?.language ?: "en") }
    var lang_loaded by remember { mutableStateOf(false) }

    LaunchedEffect(prefs) {
        if (prefs != null && !lang_loaded) {
            lang_loaded = true
            selected = prefs.language
        }
    }

    fun save(code: String) {
        selected = code
        val base = prefs ?: return
        vm.save_preferences(base.copy(language = code))
    }

    detail_scaffold(title = stringResource(R.string.language), on_back = on_back) {
        if (prefs == null) {
            Box(
                modifier = Modifier.fillMaxWidth().padding(AsterSpacing.xxl),
                contentAlignment = Alignment.Center,
            ) {
                CircularProgressIndicator(color = colors.accent_blue, modifier = Modifier.size(24.dp))
            }
        } else {
            section_label(stringResource(R.string.display_language))
            AsterCard(modifier = Modifier.fillMaxWidth()) {
                languages.forEachIndexed { idx, (code, name) ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { save(code) }
                            .padding(horizontal = AsterSpacing.lg, vertical = AsterSpacing.md),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            text = name,
                            color = colors.text_primary,
                            fontSize = 15.sp,
                            fontWeight = FontWeight.Medium,
                            modifier = Modifier.weight(1f),
                        )
                        if (selected == code) {
                            androidx.compose.material3.Icon(
                                imageVector = TablerIcons.Check,
                                contentDescription = null,
                                tint = colors.accent_blue,
                                modifier = Modifier.size(20.dp),
                            )
                        }
                    }
                    if (idx < languages.lastIndex) AsterDivider(modifier = Modifier)
                }
            }
            v_gap(AsterSpacing.xxl)
            Box(
                modifier = Modifier.fillMaxWidth().padding(horizontal = AsterSpacing.md),
            ) {
                AsterSecondaryButton(
                    label = stringResource(R.string.set_from_system),
                    onClick = {
                        val device_language = java.util.Locale.getDefault().language
                        val supported = languages.any { it.first == device_language }
                        save(if (supported) device_language else "en")
                    },
                )
            }
        }
        v_gap(AsterSpacing.xxl)
    }
}
