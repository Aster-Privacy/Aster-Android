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
import androidx.compose.foundation.rememberScrollState
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
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.res.pluralStringResource
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
import org.astermail.android.design.components.AsterSwitch
import org.astermail.android.design.components.UpgradeGate
import org.astermail.android.folders.flatten_folder_tree
import org.astermail.android.folders.folder_sibling_group
import org.astermail.android.settings.SettingsViewModel
import org.astermail.android.settings.shared_settings_view_model

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
        AsterSwitch(
            checked = checked,
            onCheckedChange = on_change,
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
    val vm: SettingsViewModel = shared_settings_view_model()
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
                    val name = org.astermail.android.ui.settings.device_display_name(s.browser, s.device_type)
                        .ifEmpty { stringResource(R.string.unknown_device) }
                    val last_seen = if (s.is_current) stringResource(R.string.active_now) else relative_time_label(s.last_active)
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
    val vm: SettingsViewModel = shared_settings_view_model()
    val state by vm.state.collectAsStateWithLifecycle()
    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    val ghost_scroll = rememberScrollState()

    LaunchedEffect(Unit) { vm.load_ghost_aliases() }

    detail_scaffold(
        title = stringResource(R.string.ghost_aliases),
        on_back = on_back,
        scroll_state = ghost_scroll,
    ) {
        section_label(stringResource(R.string.ghost_aliases_about))
        ghost_tab(
            vm = vm,
            state = state,
            context = context,
            scope = scope,
            scroll_state = ghost_scroll,
        )
        v_gap(AsterSpacing.xxl)
    }
}

@Composable
fun ReferralScreen(on_back: () -> Unit, on_open: (id: String) -> Unit = {}) {
    val vm: SettingsViewModel = shared_settings_view_model()
    val state by vm.state.collectAsStateWithLifecycle()
    val colors = AsterMaterial.colors
    val context = LocalContext.current

    LaunchedEffect(Unit) { vm.load_referral_info() }

    val referral = state.referral
    val history = state.referral_history
    val link = referral?.referral_link ?: ""

    val link_copied_text = stringResource(R.string.link_copied)
    val clipboard_label = stringResource(R.string.clipboard_label_referral)
    val share_title = stringResource(R.string.share_referral)
    val share_body = stringResource(R.string.share_text)

    val earned_cents = (referral?.credits_earned_cents ?: 0L) +
        (referral?.commission_earned_cents ?: 0L)
    val max_credits_cents = referral?.max_credits_cents ?: 0L

    val copy_link = {
        if (link.isNotEmpty()) {
            val cm = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
            cm.setPrimaryClip(ClipData.newPlainText(clipboard_label, link))
            Toast.makeText(context, link_copied_text, Toast.LENGTH_SHORT).show()
        }
    }

    val share_link = {
        if (link.isNotEmpty()) {
            val intent = Intent(Intent.ACTION_SEND).apply {
                type = "text/plain"
                putExtra(Intent.EXTRA_TEXT, "$share_body $link")
            }
            context.startActivity(Intent.createChooser(intent, share_title))
        }
    }

    detail_scaffold(title = stringResource(R.string.referral_program), on_back = on_back) {
        if (referral == null) {
            Box(
                modifier = Modifier.fillMaxWidth().padding(AsterSpacing.xxl),
                contentAlignment = Alignment.Center,
            ) {
                CircularProgressIndicator(color = colors.accent_blue, modifier = Modifier.size(24.dp))
            }
        } else {
            referral_hero(
                link = link,
                earned_label = format_cents(earned_cents),
                on_copy = copy_link,
                on_share = share_link,
            )

            v_gap(AsterSpacing.lg)
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(AsterSpacing.sm),
            ) {
                referral_stat_card(
                    modifier = Modifier.weight(1f),
                    icon = TablerIcons.Users,
                    label = stringResource(R.string.total_referrals),
                    value = referral.total_referrals.toString(),
                    accent = colors.text_secondary,
                )
                referral_stat_card(
                    modifier = Modifier.weight(1f),
                    icon = TablerIcons.Clock,
                    label = stringResource(R.string.pending),
                    value = referral.pending_referrals.toString(),
                    accent = colors.warning,
                )
                referral_stat_card(
                    modifier = Modifier.weight(1f),
                    icon = TablerIcons.Check,
                    label = stringResource(R.string.completed),
                    value = referral.completed_referrals.toString(),
                    accent = colors.success,
                )
            }

            v_gap(AsterSpacing.lg)
            section_label(stringResource(R.string.rewards))
            v_gap(AsterSpacing.sm)
            AsterCard(modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(AsterSpacing.lg)) {
                    Text(
                        text = stringResource(R.string.reward_amount),
                        color = colors.text_secondary,
                        fontSize = 13.sp,
                    )
                    Spacer(Modifier.height(AsterSpacing.xs))
                    Text(
                        text = stringResource(
                            R.string.referral_commission_info,
                            if (referral.commission_percent > 0) referral.commission_percent else 5,
                        ),
                        color = colors.text_secondary,
                        fontSize = 13.sp,
                    )
                    if (max_credits_cents > 0) {
                        Spacer(Modifier.height(AsterSpacing.md))
                        referral_progress(
                            earned_cents = earned_cents,
                            max_cents = max_credits_cents,
                        )
                    }
                }
            }

            v_gap(AsterSpacing.lg)
            section_label(stringResource(R.string.how_it_works))
            v_gap(AsterSpacing.sm)
            AsterCard(modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(AsterSpacing.lg)) {
                    referral_step(index = 1, text = stringResource(R.string.referral_step_1))
                    referral_step(index = 2, text = stringResource(R.string.referral_step_2))
                    referral_step(index = 3, text = stringResource(R.string.referral_step_3))
                }
            }

            v_gap(AsterSpacing.lg)
            section_label(stringResource(R.string.referral_history))
            v_gap(AsterSpacing.sm)
            if (history.isEmpty()) {
                AsterCard(modifier = Modifier.fillMaxWidth()) {
                    Column(
                        modifier = Modifier.fillMaxWidth().padding(vertical = AsterSpacing.xl),
                        horizontalAlignment = Alignment.CenterHorizontally,
                    ) {
                        androidx.compose.material3.Icon(
                            imageVector = TablerIcons.Users,
                            contentDescription = null,
                            tint = colors.text_muted,
                            modifier = Modifier.size(26.dp),
                        )
                        Spacer(Modifier.height(AsterSpacing.sm))
                        Text(
                            text = stringResource(R.string.no_referrals_yet),
                            color = colors.text_secondary,
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Medium,
                        )
                        Spacer(Modifier.height(2.dp))
                        Text(
                            text = stringResource(R.string.referral_history_empty_hint),
                            color = colors.text_muted,
                            fontSize = 12.sp,
                        )
                    }
                }
            } else {
                AsterCard(modifier = Modifier.fillMaxWidth()) {
                    history.forEachIndexed { index, item ->
                        if (index > 0) AsterDivider()
                        referral_history_row(item)
                    }
                }
            }
        }
        v_gap(AsterSpacing.xxl)
    }
}

@Composable
private fun referral_hero(
    link: String,
    earned_label: String,
    on_copy: () -> Unit,
    on_share: () -> Unit,
) {
    val colors = AsterMaterial.colors
    val shape = SquircleShape(20.dp)
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(shape)
            .background(
                Brush.linearGradient(
                    listOf(
                        colors.accent_blue,
                        colors.accent_blue.copy(alpha = 0.78f),
                    ),
                ),
            )
            .padding(AsterSpacing.lg),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = stringResource(R.string.your_referral_link),
                color = Color.White,
                fontSize = 18.sp,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.weight(1f),
            )
            Box(
                modifier = Modifier
                    .clip(CircleShape)
                    .background(Color.White.copy(alpha = 0.18f))
                    .padding(horizontal = AsterSpacing.sm, vertical = 4.dp),
            ) {
                Text(
                    text = earned_label + " " + stringResource(R.string.total_earned),
                    color = Color.White,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.SemiBold,
                )
            }
        }
        Spacer(Modifier.height(AsterSpacing.xs))
        Text(
            text = stringResource(R.string.invite_description),
            color = Color.White.copy(alpha = 0.82f),
            fontSize = 13.sp,
        )
        Spacer(Modifier.height(AsterSpacing.md))
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(12.dp))
                .background(Color.Black.copy(alpha = 0.22f))
                .padding(horizontal = AsterSpacing.md, vertical = 10.dp),
        ) {
            SelectionContainer {
                Text(
                    text = link.ifEmpty { stringResource(R.string.no_link_available) },
                    color = Color.White,
                    fontSize = 12.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
        Spacer(Modifier.height(AsterSpacing.sm))
        Row(horizontalArrangement = Arrangement.spacedBy(AsterSpacing.sm)) {
            hero_action(
                label = stringResource(R.string.copy_link),
                icon = TablerIcons.Copy,
                filled = true,
                modifier = Modifier.weight(1f),
                on_click = on_copy,
            )
            hero_action(
                label = stringResource(R.string.share_link),
                icon = TablerIcons.Send,
                filled = false,
                modifier = Modifier.weight(1f),
                on_click = on_share,
            )
        }
    }
}

@Composable
private fun hero_action(
    label: String,
    icon: ImageVector,
    filled: Boolean,
    modifier: Modifier = Modifier,
    on_click: () -> Unit,
) {
    val colors = AsterMaterial.colors
    val shape = RoundedCornerShape(12.dp)
    Row(
        modifier = modifier
            .height(40.dp)
            .clip(shape)
            .background(if (filled) Color.White else Color.White.copy(alpha = 0.18f))
            .clickable(onClick = on_click),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.Center,
    ) {
        androidx.compose.material3.Icon(
            imageVector = icon,
            contentDescription = null,
            tint = if (filled) colors.accent_blue else Color.White,
            modifier = Modifier.size(16.dp),
        )
        Spacer(Modifier.width(6.dp))
        Text(
            text = label,
            color = if (filled) colors.accent_blue else Color.White,
            fontSize = 13.sp,
            fontWeight = FontWeight.SemiBold,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

@Composable
private fun referral_progress(earned_cents: Long, max_cents: Long) {
    val colors = AsterMaterial.colors
    val fraction = if (max_cents <= 0L) 0f else (earned_cents.toFloat() / max_cents.toFloat()).coerceIn(0f, 1f)
    Column(modifier = Modifier.fillMaxWidth()) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(8.dp)
                .clip(CircleShape)
                .background(colors.bg_secondary),
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth(fraction)
                    .height(8.dp)
                    .clip(CircleShape)
                    .background(colors.accent_blue),
            )
        }
        Spacer(Modifier.height(AsterSpacing.xs))
        Text(
            text = stringResource(
                R.string.referral_max_credits,
                format_cents(earned_cents),
                format_cents(max_cents),
            ),
            color = colors.text_muted,
            fontSize = 12.sp,
        )
    }
}

@Composable
private fun referral_history_row(item: org.astermail.android.api.labels.ReferralHistoryItem) {
    val colors = AsterMaterial.colors
    val is_completed = item.status == "completed"
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = AsterSpacing.lg, vertical = AsterSpacing.md),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = item.referee_email_masked,
                color = colors.text_primary,
                fontSize = 14.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            val date_label = format_referral_date(item.created_at)
            if (date_label.isNotEmpty()) {
                Spacer(Modifier.height(2.dp))
                Text(text = date_label, color = colors.text_muted, fontSize = 12.sp)
            }
        }
        Spacer(Modifier.width(AsterSpacing.sm))
        Box(
            modifier = Modifier
                .clip(CircleShape)
                .background((if (is_completed) colors.success else colors.warning).copy(alpha = 0.15f))
                .padding(horizontal = AsterSpacing.sm, vertical = 3.dp),
        ) {
            Text(
                text = stringResource(if (is_completed) R.string.completed else R.string.pending),
                color = if (is_completed) colors.success else colors.warning,
                fontSize = 11.sp,
                fontWeight = FontWeight.Medium,
            )
        }
        if (item.referrer_credit_cents > 0) {
            Spacer(Modifier.width(AsterSpacing.sm))
            Text(
                text = "+" + format_cents(item.referrer_credit_cents),
                color = colors.success,
                fontSize = 13.sp,
                fontWeight = FontWeight.SemiBold,
            )
        }
    }
}

private fun format_referral_date(raw: String?): String {
    if (raw.isNullOrBlank()) return ""
    return try {
        val instant = java.time.Instant.parse(raw)
        java.time.format.DateTimeFormatter
            .ofLocalizedDate(java.time.format.FormatStyle.MEDIUM)
            .withZone(java.time.ZoneId.systemDefault())
            .format(instant)
    } catch (_: Throwable) {
        raw.take(10)
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
    icon: ImageVector,
    label: String,
    value: String,
    accent: Color,
) {
    val colors = AsterMaterial.colors
    Column(
        modifier = modifier
            .clip(SquircleShape(14.dp))
            .background(colors.bg_card)
            .border(1.dp, colors.border_secondary, SquircleShape(14.dp))
            .padding(horizontal = AsterSpacing.md, vertical = AsterSpacing.md),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        androidx.compose.material3.Icon(
            imageVector = icon,
            contentDescription = null,
            tint = accent,
            modifier = Modifier.size(18.dp),
        )
        Spacer(Modifier.height(AsterSpacing.xs))
        Text(
            text = value,
            color = colors.text_primary,
            fontSize = 18.sp,
            fontWeight = FontWeight.SemiBold,
        )
        Spacer(Modifier.height(2.dp))
        Text(
            text = label,
            color = colors.text_muted,
            fontSize = 11.sp,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
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
                .background(colors.accent_blue.copy(alpha = 0.15f)),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                text = index.toString(),
                color = colors.accent_blue,
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
fun DeveloperScreen(on_back: () -> Unit, on_open: (id: String) -> Unit = {}) {
    val vm: SettingsViewModel = shared_settings_view_model()
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
fun LabelsScreen(
    on_back: () -> Unit,
    on_open: (id: String) -> Unit = {},
    vm: SettingsViewModel = shared_settings_view_model(),
) {
    val state by vm.state.collectAsStateWithLifecycle()
    val colors = AsterMaterial.colors

    LaunchedEffect(Unit) { vm.load_labels(folder_type = "label") }

    val labels = state.labels.filter { it.folder_type == "label" }
    var pending_label_delete by remember { mutableStateOf<org.astermail.android.api.labels.LabelItem?>(null) }

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
                            val count_text = l.item_count?.let { pluralStringResource(R.plurals.common_messages_count, it.toInt(), it) } ?: ""
                            if (count_text.isNotEmpty()) {
                                Text(count_text, color = colors.text_tertiary, fontSize = 13.sp)
                            }
                        }
                        if (!l.is_system && !l.is_locked) {
                            Box(
                                modifier = Modifier
                                    .size(36.dp)
                                    .clip(CircleShape)
                                    .clickable { pending_label_delete = l }
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

    pending_label_delete?.let { target ->
        val target_name = target.encrypted_name?.takeIf { it.isNotBlank() } ?: target.label_token
        org.astermail.android.design.components.AsterAlertDialog(
            on_dismiss = { pending_label_delete = null },
            title = stringResource(R.string.delete_label_confirm_title),
            message = stringResource(R.string.delete_label_confirm_message, target_name),
            confirm_label = stringResource(R.string.delete),
            cancel_label = stringResource(R.string.cancel),
            confirm_style = org.astermail.android.design.components.DialogConfirmStyle.destructive,
            on_confirm = {
                vm.delete_label(target.id)
                pending_label_delete = null
            },
        )
    }
}

@Composable
fun FoldersScreen(
    on_back: () -> Unit,
    on_open: (id: String) -> Unit = {},
    vm: SettingsViewModel = shared_settings_view_model(),
) {
    val state by vm.state.collectAsStateWithLifecycle()
    val colors = AsterMaterial.colors

    LaunchedEffect(Unit) {
        vm.load_labels(folder_type = "folder")
        vm.load_preferences()
    }

    val folder_nodes = flatten_folder_tree(state.labels)
    val muted_tokens = state.preferences?.muted_folder_tokens ?: emptyList()
    var show_create_folder by remember { mutableStateOf(false) }
    var pending_folder_delete by remember { mutableStateOf<org.astermail.android.api.labels.LabelItem?>(null) }
    val folders_context = LocalContext.current

    LaunchedEffect(state.action_result) {
        val msg = state.action_result ?: return@LaunchedEffect
        android.widget.Toast.makeText(folders_context, msg, android.widget.Toast.LENGTH_SHORT).show()
        vm.clear_action_result()
    }

    val folder_parent_options = folder_nodes
        .filter { it.depth < org.astermail.android.folders.max_folder_depth }
        .mapNotNull { node ->
            val readable_name = node.label.encrypted_name?.takeIf { it.isNotBlank() } ?: return@mapNotNull null
            org.astermail.android.ui.drawer.folder_parent_option(
                token = node.label.label_token,
                label = readable_name,
                depth = node.depth,
                path_label = org.astermail.android.folders.folder_path(state.labels, node.label.label_token)
                    .filter { it.isNotBlank() }
                    .joinToString(" · "),
            )
        }

    detail_scaffold(title = stringResource(R.string.folders), on_back = on_back) {
        AsterCard(modifier = Modifier.fillMaxWidth()) {
            detail_row(
                title = stringResource(R.string.create_folder),
                subtitle = stringResource(R.string.create_folder_subtitle),
                icon = TablerIcons.FolderPlus,
                on_click = { show_create_folder = true },
            )
        }
        v_gap(AsterSpacing.md)

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
            section_label(pluralStringResource(R.plurals.common_folders_count, folder_nodes.size, folder_nodes.size))
            AsterCard(modifier = Modifier.fillMaxWidth()) {
                folder_nodes.forEachIndexed { idx, node ->
                    val f = node.label
                    val folder_name = f.encrypted_name ?: f.label_token
                    val count_text = f.item_count?.let { pluralStringResource(R.plurals.common_messages_count, it.toInt(), it) } ?: ""
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
                                    if (!f.is_system) {
                                        val is_muted = f.label_token in muted_tokens
                                        Box(
                                            modifier = Modifier
                                                .size(32.dp)
                                                .clip(CircleShape)
                                                .clickable { vm.toggle_folder_notifications(f.label_token) },
                                            contentAlignment = Alignment.Center,
                                        ) {
                                            androidx.compose.material3.Icon(
                                                imageVector = if (is_muted) TablerIcons.BellOff else TablerIcons.Bell,
                                                contentDescription = stringResource(
                                                    if (is_muted) {
                                                        R.string.unmute_folder_notifications
                                                    } else {
                                                        R.string.mute_folder_notifications
                                                    },
                                                ),
                                                tint = if (is_muted) colors.text_muted else colors.text_secondary,
                                                modifier = Modifier.size(18.dp),
                                            )
                                        }
                                    }
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
                                                .clip(CircleShape)
                                                .clickable { pending_folder_delete = f },
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

    if (show_create_folder) {
        org.astermail.android.ui.drawer.create_folder_dialog(
            title = stringResource(R.string.create_folder),
            placeholder = stringResource(R.string.folder_name),
            parent_options = folder_parent_options,
            on_dismiss = { show_create_folder = false },
            on_create = { name, parent_token ->
                vm.create_folder(name = name, parent_token = parent_token)
                show_create_folder = false
            },
        )
    }

    pending_folder_delete?.let { target ->
        val target_name = target.encrypted_name?.takeIf { it.isNotBlank() } ?: target.label_token
        val has_subfolders = org.astermail.android.folders.descendant_tokens(
            state.labels,
            target.label_token,
        ).isNotEmpty()
        org.astermail.android.design.components.AsterAlertDialog(
            on_dismiss = { pending_folder_delete = null },
            title = stringResource(R.string.delete_folder_confirm_title),
            message = delete_folder_confirm_body(target_name, has_subfolders),
            confirm_label = stringResource(R.string.delete),
            cancel_label = stringResource(R.string.cancel),
            confirm_style = org.astermail.android.design.components.DialogConfirmStyle.destructive,
            on_confirm = {
                vm.delete_label(target.id)
                pending_folder_delete = null
            },
        )
    }
}

@Composable
private fun delete_folder_confirm_body(folder_name: String, has_subfolders: Boolean): String {
    val body = stringResource(R.string.delete_folder_confirm_message, folder_name)
    val subfolders = stringResource(R.string.delete_folder_confirm_subfolders)
    return if (has_subfolders) "$body $subfolders" else body
}

@Composable
fun PrivacyScreen(on_back: () -> Unit, on_open: (id: String) -> Unit = {}) {
    val vm: SettingsViewModel = shared_settings_view_model()
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
    val vm: SettingsViewModel = shared_settings_view_model()
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
    val vm: SettingsViewModel = shared_settings_view_model()
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
    val vm: SettingsViewModel = shared_settings_view_model()
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
    val vm: SettingsViewModel = shared_settings_view_model()
    val state by vm.state.collectAsStateWithLifecycle()
    val colors = AsterMaterial.colors
    val context = LocalContext.current
    var release_target by remember { mutableStateOf<org.astermail.android.api.family.ReservedAddress?>(null) }

    LaunchedEffect(Unit) { vm.load_reserved_addresses() }

    fun copy_to_clipboard(text: String, label: String) {
        val cm = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        cm.setPrimaryClip(ClipData.newPlainText(label, text))
        org.astermail.android.ui.common.show_copied_toast(context, text)
    }

    detail_scaffold(title = stringResource(R.string.kids_reserved_addresses), on_back = on_back) {
        section_label(stringResource(R.string.kids_reserved_subtitle))

        if (state.family_max_members > 0) {
            Text(
                text = stringResource(R.string.kids_seats_used, state.family_seats_used, state.family_max_members) +
                    " · " +
                    stringResource(
                        R.string.kids_seats_free,
                        (state.family_max_members - state.family_seats_used).coerceAtLeast(0),
                    ),
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
        org.astermail.android.design.components.AsterAlertDialog(
            on_dismiss = { release_target = null },
            title = stringResource(R.string.kids_release_confirm_title),
            message = stringResource(R.string.kids_release_confirm_body, "${target.username}@${target.email_domain}"),
            confirm_label = stringResource(R.string.kids_release),
            cancel_label = stringResource(R.string.cancel),
            confirm_style = org.astermail.android.design.components.DialogConfirmStyle.destructive,
            on_confirm = {
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
    }
}

@Composable
fun LanguageScreen(on_back: () -> Unit, on_open: (id: String) -> Unit = {}) {
    val vm: SettingsViewModel = shared_settings_view_model()
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
