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
import kotlinx.coroutines.CancellationException
import org.astermail.android.ui.common.show_copy_result_toast
import org.astermail.android.ui.common.show_copy_failed_toast
import org.astermail.android.ui.common.write_to_clipboard
import compose.icons.tablericons.*

import android.content.ClipData
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
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
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.draw.clip
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.Paint
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.res.imageResource
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.font.FontFamily
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
import org.astermail.android.design.parse_hex_color_safe
import org.astermail.android.design.components.AsterAlertDialog
import org.astermail.android.design.components.AsterButton
import org.astermail.android.design.components.AsterCard
import org.astermail.android.design.components.AsterDivider
import org.astermail.android.design.components.AsterGhostButton
import org.astermail.android.design.components.AsterSecondaryButton
import org.astermail.android.design.components.AsterSwitch
import org.astermail.android.design.components.UpgradeGate
import org.astermail.android.design.darken
import org.astermail.android.design.lighten
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
    val devices_context = LocalContext.current

    LaunchedEffect(state.action_result) {
        val msg = state.action_result ?: return@LaunchedEffect
        android.widget.Toast.makeText(devices_context, msg, android.widget.Toast.LENGTH_SHORT).show()
        vm.clear_action_result()
    }

    val devices_load_settled = remember_load_settled(state.is_loading)
    var show_revoke_all_confirm by remember { mutableStateOf(false) }
    var pending_revoke_session by remember { mutableStateOf<String?>(null) }
    val scroll_state = rememberScrollState()

    detail_scaffold(
        title = stringResource(R.string.trusted_devices),
        on_back = on_back,
        scroll_state = scroll_state,
    ) {
        AsterCard(modifier = Modifier.fillMaxWidth()) {
            detail_row(
                title = stringResource(R.string.link_device_title),
                subtitle = stringResource(R.string.link_device_row_subtitle),
                icon = TablerIcons.DeviceDesktop,
                on_click = { on_open("link_device") },
            )
        }
        v_gap(AsterSpacing.lg)

        section_header_action(
            title = pluralStringResource(R.plurals.devices_count, state.sessions.size, state.sessions.size),
            action_label = stringResource(R.string.revoke_all_action),
            enabled = state.sessions.size > 1,
            on_click = { show_revoke_all_confirm = true },
        )
        if (state.sessions.isEmpty() && (state.is_loading || !devices_load_settled)) {
            skeleton_card_list(rows = 6, leading_circle = true, trailing_width = 72.dp)
        } else if (state.sessions.isEmpty()) {
            load_failed_card(state.error ?: stringResource(R.string.could_not_load_devices)) {
                vm.load_sessions()
            }
        } else {
            AsterCard(modifier = Modifier.fillMaxWidth()) {
                state.sessions.forEachIndexed { idx, s ->
                    val icon = org.astermail.android.ui.settings.device_client_icon(
                        org.astermail.android.ui.settings.device_client_kind(s.browser, s.device_type, s.os),
                    )
                    val name = org.astermail.android.ui.settings.device_display_name(s.browser, s.device_type)
                        .ifEmpty { stringResource(R.string.unknown_device) }
                    val last_seen = if (s.is_current) stringResource(R.string.active_now) else relative_time_label(s.last_active)
                    detail_row(
                        title = name,
                        subtitle = last_seen,
                        icon = icon,
                        on_click = null,
                        trailing = {
                            if (s.is_current) org.astermail.android.ui.settings.this_device_badge()
                            else AsterGhostButton(label = stringResource(R.string.revoke), onClick = { pending_revoke_session = s.id })
                        },
                    )
                    if (idx < state.sessions.lastIndex) AsterDivider(modifier = Modifier)
                }
            }
        }
        v_gap(AsterSpacing.xxl)
    }

    pending_revoke_session?.let { session_id ->
        AsterAlertDialog(
            on_dismiss = { pending_revoke_session = null },
            title = stringResource(R.string.revoke),
            message = stringResource(R.string.revoke_session_confirm),
            confirm_label = stringResource(R.string.revoke),
            cancel_label = stringResource(R.string.cancel),
            confirm_style = org.astermail.android.design.components.DialogConfirmStyle.destructive,
            on_confirm = {
                pending_revoke_session = null
                vm.revoke_session(session_id)
            },
        )
    }

    if (show_revoke_all_confirm) {
        AsterAlertDialog(
            on_dismiss = { show_revoke_all_confirm = false },
            title = stringResource(R.string.revoke_all_other),
            message = stringResource(R.string.revoke_all_trusted_devices_confirm),
            confirm_label = stringResource(R.string.revoke_all_action),
            cancel_label = stringResource(R.string.cancel),
            confirm_style = org.astermail.android.design.components.DialogConfirmStyle.destructive,
            on_confirm = {
                show_revoke_all_confirm = false
                vm.logout_others()
            },
        )
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
            if (write_to_clipboard(context, ClipData.newPlainText(clipboard_label, link))) {
                Toast.makeText(context, link_copied_text, Toast.LENGTH_SHORT).show()
            } else {
                show_copy_failed_toast(context)
            }
        }
    }

    val share_link = {
        if (link.isNotEmpty()) {
            val intent = Intent(Intent.ACTION_SEND).apply {
                type = "text/plain"
                putExtra(Intent.EXTRA_TEXT, "$share_body $link")
            }
            org.astermail.android.ui.common.start_external_intent(
                context,
                Intent.createChooser(intent, share_title),
            )
        }
    }

    detail_scaffold(title = stringResource(R.string.referral_program), on_back = on_back) {
        if (referral == null && state.referral_load_failed) {
            load_failed_card(null) { vm.load_referral_info() }
        } else if (referral == null) {
            Box(
                modifier = Modifier.fillMaxWidth().padding(AsterSpacing.xxl),
                contentAlignment = Alignment.Center,
            ) {
                CircularProgressIndicator(color = colors.accent_blue, modifier = Modifier.size(24.dp))
            }
        } else {
            val commission_percent =
                if (referral.commission_percent > 0) referral.commission_percent else 10

            referral_hero(
                link = link,
                earned_cents = earned_cents,
                on_copy = copy_link,
                on_share = share_link,
            )

            v_gap(AsterSpacing.md)
            referral_stats_row(
                total = referral.total_referrals,
                pending = referral.pending_referrals,
                completed = referral.completed_referrals,
            )

            v_gap(AsterSpacing.lg)
            section_label(stringResource(R.string.how_it_works))
            v_gap(AsterSpacing.xs)
            referral_steps_card()

            v_gap(AsterSpacing.lg)
            section_label(stringResource(R.string.rewards))
            v_gap(AsterSpacing.xs)
            referral_rewards_card(
                commission_percent = commission_percent,
                earned_cents = earned_cents,
                max_cents = max_credits_cents,
            )

            v_gap(AsterSpacing.lg)
            section_label(stringResource(R.string.referral_history))
            v_gap(AsterSpacing.xs)
            if (history.isEmpty()) {
                referral_empty_history()
            } else {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(SquircleShape(AsterRadius.xl))
                        .border(1.dp, colors.border_secondary, SquircleShape(AsterRadius.xl)),
                ) {
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
    earned_cents: Long,
    on_copy: () -> Unit,
    on_share: () -> Unit,
) {
    val colors = AsterMaterial.colors
    val accent = colors.accent_blue.darken(0.12f)
    val graphic = ImageBitmap.imageResource(R.drawable.referral_decentralized)
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(SquircleShape(AsterRadius.xxl))
            .background(accent),
    ) {
        Canvas(modifier = Modifier.matchParentSize()) {
            if (size.width <= 0f || size.height <= 0f) return@Canvas
            val region_left = size.width * 0.45f
            val region_width = size.width - region_left
            val scale = maxOf(region_width / graphic.width, size.height / graphic.height)
            val draw_width = (graphic.width * scale).toInt()
            val draw_height = (graphic.height * scale).toInt()
            val region = Rect(Offset(region_left, 0f), androidx.compose.ui.geometry.Size(region_width, size.height))
            drawIntoCanvas { canvas ->
                val paint = Paint().apply { blendMode = BlendMode.Screen }
                canvas.saveLayer(region, paint)
                drawImage(
                    image = graphic,
                    dstOffset = IntOffset(
                        (region_left + (region_width - draw_width) / 2f).toInt(),
                        ((size.height - draw_height) / 2f).toInt(),
                    ),
                    dstSize = IntSize(draw_width, draw_height),
                    alpha = 0.60f,
                )
                drawRect(
                    brush = Brush.horizontalGradient(
                        0.0f to Color.Transparent,
                        0.35f to Color.Black,
                        0.90f to Color.Black,
                        1.0f to Color.Transparent,
                        startX = region_left,
                        endX = size.width,
                    ),
                    topLeft = region.topLeft,
                    size = region.size,
                    blendMode = BlendMode.DstIn,
                )
                canvas.restore()
            }
        }
        Column(modifier = Modifier.padding(AsterSpacing.xl)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = stringResource(R.string.your_referral_link),
                color = Color.White,
                fontSize = 17.sp,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.weight(1f),
            )
            Spacer(Modifier.width(AsterSpacing.sm))
            Text(
                text = format_cents(earned_cents) + " " + stringResource(R.string.total_earned),
                color = Color.White,
                fontSize = 11.sp,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier
                    .clip(RoundedCornerShape(AsterRadius.pill))
                    .background(Color.White.copy(alpha = 0.15f))
                    .padding(horizontal = 10.dp, vertical = 4.dp),
            )
        }
        Spacer(Modifier.height(6.dp))
        Text(
            text = stringResource(R.string.referral_program_description),
            color = Color.White.copy(alpha = 0.70f),
            fontSize = 13.sp,
            lineHeight = 18.sp,
        )
        Spacer(Modifier.height(AsterSpacing.lg))
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(38.dp)
                .clip(RoundedCornerShape(AsterRadius.md))
                .background(Color.Black.copy(alpha = 0.20f))
                .border(1.dp, Color.White.copy(alpha = 0.12f), RoundedCornerShape(AsterRadius.md))
                .clickable(enabled = link.isNotEmpty(), onClick = on_copy)
                .padding(horizontal = AsterSpacing.md),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            androidx.compose.material3.Icon(
                imageVector = TablerIcons.Gift,
                contentDescription = null,
                tint = Color.White.copy(alpha = 0.60f),
                modifier = Modifier.size(15.dp),
            )
            Spacer(Modifier.width(AsterSpacing.sm))
            Text(
                text = link.ifEmpty { stringResource(R.string.no_link_available) },
                color = Color.White,
                fontSize = 12.sp,
                fontFamily = FontFamily.Monospace,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        Spacer(Modifier.height(AsterSpacing.sm))
        Row(horizontalArrangement = Arrangement.spacedBy(AsterSpacing.sm)) {
            referral_hero_button(
                modifier = Modifier.weight(1f),
                icon = TablerIcons.Copy,
                label = stringResource(R.string.copy_link),
                accent = accent,
                on_click = on_copy,
                enabled = link.isNotEmpty(),
            )
            referral_hero_button(
                modifier = Modifier.weight(1f),
                icon = TablerIcons.Send,
                label = stringResource(R.string.share_link),
                accent = accent,
                on_click = on_share,
                enabled = link.isNotEmpty(),
            )
        }
        }
    }
}

@Composable
private fun referral_hero_button(
    modifier: Modifier = Modifier,
    icon: ImageVector,
    label: String,
    accent: Color,
    on_click: () -> Unit,
    enabled: Boolean = true,
) {
    val ink = accent.darken(0.42f).copy(alpha = if (enabled) 1f else 0.45f)
    Row(
        modifier = modifier
            .height(38.dp)
            .clip(RoundedCornerShape(AsterRadius.md))
            .background(Color.White.copy(alpha = if (enabled) 1f else 0.55f))
            .clickable(enabled = enabled, onClick = on_click),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.Center,
    ) {
        androidx.compose.material3.Icon(
            imageVector = icon,
            contentDescription = null,
            tint = ink,
            modifier = Modifier.size(15.dp),
        )
        Spacer(Modifier.width(6.dp))
        Text(
            text = label,
            color = ink,
            fontSize = 13.sp,
            fontWeight = FontWeight.SemiBold,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

@Composable
private fun referral_stats_row(total: Long, pending: Long, completed: Long) {
    val colors = AsterMaterial.colors
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(AsterSpacing.sm),
    ) {
        referral_stat_ring(
            modifier = Modifier.weight(1f),
            value = total,
            max = total,
            accent = colors.text_secondary,
            icon = TablerIcons.Users,
            label = stringResource(R.string.total_referrals),
        )
        referral_stat_ring(
            modifier = Modifier.weight(1f),
            value = pending,
            max = total,
            accent = colors.warning,
            icon = TablerIcons.Clock,
            label = stringResource(R.string.pending),
        )
        referral_stat_ring(
            modifier = Modifier.weight(1f),
            value = completed,
            max = total,
            accent = colors.success,
            icon = TablerIcons.Check,
            label = stringResource(R.string.completed),
        )
    }
}

@Composable
private fun referral_stat_ring(
    modifier: Modifier = Modifier,
    value: Long,
    max: Long,
    accent: Color,
    icon: ImageVector,
    label: String,
) {
    val colors = AsterMaterial.colors
    val ratio = when {
        max > 0L -> (value.toFloat() / max.toFloat()).coerceIn(0f, 1f)
        value > 0L -> 1f
        else -> 0f
    }
    Column(
        modifier = modifier
            .clip(SquircleShape(AsterRadius.xl))
            .border(1.dp, colors.border_secondary, SquircleShape(AsterRadius.xl))
            .padding(vertical = AsterSpacing.md, horizontal = AsterSpacing.xs),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Box(modifier = Modifier.size(44.dp), contentAlignment = Alignment.Center) {
            val track = colors.border_secondary
            Canvas(modifier = Modifier.size(44.dp)) {
                val stroke = 4.dp.toPx()
                val inset = stroke / 2f
                val arc_size = androidx.compose.ui.geometry.Size(
                    size.width - stroke,
                    size.height - stroke,
                )
                drawArc(
                    color = track,
                    startAngle = 0f,
                    sweepAngle = 360f,
                    useCenter = false,
                    topLeft = Offset(inset, inset),
                    size = arc_size,
                    style = Stroke(width = stroke),
                )
                if (ratio > 0f) {
                    drawArc(
                        color = accent,
                        startAngle = -90f,
                        sweepAngle = 360f * ratio,
                        useCenter = false,
                        topLeft = Offset(inset, inset),
                        size = arc_size,
                        style = Stroke(width = stroke, cap = StrokeCap.Round),
                    )
                }
            }
            androidx.compose.material3.Icon(
                imageVector = icon,
                contentDescription = null,
                tint = accent,
                modifier = Modifier.size(16.dp),
            )
        }
        Spacer(Modifier.height(AsterSpacing.sm))
        Text(
            text = value.toString(),
            color = accent,
            fontSize = 18.sp,
            fontWeight = FontWeight.Bold,
        )
        Spacer(Modifier.height(1.dp))
        Text(
            text = label,
            color = colors.text_muted,
            fontSize = 10.5.sp,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

@Composable
private fun referral_steps_card() {
    val colors = AsterMaterial.colors
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(SquircleShape(AsterRadius.lg))
            .background(colors.bg_tertiary)
            .border(1.dp, colors.border_secondary, SquircleShape(AsterRadius.lg))
            .padding(AsterSpacing.md),
        verticalArrangement = Arrangement.spacedBy(AsterSpacing.md),
    ) {
        referral_step_row(1, stringResource(R.string.referral_step_1))
        referral_step_row(2, stringResource(R.string.referral_step_2))
        referral_step_row(3, stringResource(R.string.referral_step_3))
    }
}

@Composable
private fun referral_step_row(number: Int, body: String) {
    val colors = AsterMaterial.colors
    Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.Top) {
        Box(
            modifier = Modifier
                .size(24.dp)
                .border(1.5.dp, colors.accent_blue.copy(alpha = 0.55f), CircleShape),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                text = number.toString(),
                color = colors.accent_blue,
                fontSize = 12.sp,
                lineHeight = 12.sp,
                fontWeight = FontWeight.Bold,
            )
        }
        Spacer(Modifier.width(AsterSpacing.md))
        Text(
            text = body,
            color = colors.text_secondary,
            fontSize = 13.sp,
            lineHeight = 18.sp,
            modifier = Modifier.weight(1f),
        )
    }
}

@Composable
private fun referral_rewards_card(
    commission_percent: Int,
    earned_cents: Long,
    max_cents: Long,
) {
    val colors = AsterMaterial.colors
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(SquircleShape(AsterRadius.lg))
            .background(colors.bg_tertiary)
            .border(1.dp, colors.border_secondary, SquircleShape(AsterRadius.lg))
            .padding(AsterSpacing.lg),
        verticalArrangement = Arrangement.spacedBy(AsterSpacing.sm),
    ) {
        Text(
            text = stringResource(R.string.referral_reward_info),
            color = colors.text_secondary,
            fontSize = 13.sp,
            lineHeight = 18.sp,
        )
        Text(
            text = stringResource(R.string.referral_commission_info, commission_percent),
            color = colors.text_secondary,
            fontSize = 13.sp,
            lineHeight = 18.sp,
        )
        if (max_cents > 0L) {
            Spacer(Modifier.height(AsterSpacing.xs))
            Column(
                modifier = Modifier.fillMaxWidth(),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                referral_semicircle_gauge(
                    percent = (earned_cents.toFloat() / max_cents.toFloat()) * 100f,
                    bottom_label = stringResource(R.string.referral_gauge_earned_label),
                )
                Spacer(Modifier.height(AsterSpacing.sm))
                Text(
                    text = stringResource(
                        R.string.referral_max_credits,
                        format_cents(earned_cents),
                        format_cents(max_cents),
                    ),
                    color = colors.text_muted,
                    fontSize = 11.sp,
                )
            }
        }
    }
}

@Composable
private fun referral_semicircle_gauge(percent: Float, bottom_label: String) {
    val colors = AsterMaterial.colors
    val clamped = percent.coerceIn(0f, 100f)
    val track = colors.border_secondary
    val accent = colors.accent_blue
    Box(
        modifier = Modifier.width(180.dp).height(96.dp),
        contentAlignment = Alignment.BottomCenter,
    ) {
        Canvas(modifier = Modifier.fillMaxWidth().height(96.dp)) {
            val stroke = 14.dp.toPx()
            val diameter = size.width - stroke
            val arc_size = androidx.compose.ui.geometry.Size(diameter, diameter)
            val top_left = Offset(stroke / 2f, stroke / 2f)
            drawArc(
                color = track,
                startAngle = 180f,
                sweepAngle = 180f,
                useCenter = false,
                topLeft = top_left,
                size = arc_size,
                style = Stroke(width = stroke, cap = StrokeCap.Round),
            )
            if (clamped > 0f) {
                drawArc(
                    color = accent,
                    startAngle = 180f,
                    sweepAngle = 180f * (clamped / 100f),
                    useCenter = false,
                    topLeft = top_left,
                    size = arc_size,
                    style = Stroke(width = stroke, cap = StrokeCap.Round),
                )
            }
        }
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                text = kotlin.math.round(clamped).toInt().toString() + "%",
                color = colors.text_primary,
                fontSize = 20.sp,
                fontWeight = FontWeight.Bold,
            )
            Spacer(Modifier.height(2.dp))
            Text(
                text = bottom_label,
                color = colors.text_muted,
                fontSize = 11.sp,
            )
        }
    }
}

@Composable
private fun referral_empty_history() {
    val colors = AsterMaterial.colors
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(SquircleShape(AsterRadius.xl))
            .border(1.dp, colors.border_secondary, SquircleShape(AsterRadius.xl))
            .padding(vertical = AsterSpacing.xxl),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        androidx.compose.material3.Icon(
            imageVector = TablerIcons.Users,
            contentDescription = null,
            tint = colors.text_muted,
            modifier = Modifier.size(30.dp),
        )
        Spacer(Modifier.height(AsterSpacing.sm))
        Text(
            text = stringResource(R.string.no_referrals_yet),
            color = colors.text_muted,
            fontSize = 12.sp,
        )
    }
}

@Composable
private fun referral_history_row(item: org.astermail.android.api.labels.ReferralHistoryItem) {
    val colors = AsterMaterial.colors
    val is_completed = item.status == "completed"
    val status_color = if (is_completed) colors.success else colors.warning
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
                fontWeight = FontWeight.Medium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            val date_label = format_referral_date(item.created_at)
            if (date_label.isNotEmpty()) {
                Spacer(Modifier.height(2.dp))
                Text(
                    text = date_label,
                    color = colors.text_muted,
                    fontSize = 11.5.sp,
                )
            }
        }
        Spacer(Modifier.width(AsterSpacing.sm))
        Text(
            text = stringResource(if (is_completed) R.string.completed else R.string.pending),
            color = status_color,
            fontSize = 11.sp,
            fontWeight = FontWeight.Medium,
            modifier = Modifier
                .clip(RoundedCornerShape(AsterRadius.pill))
                .background(status_color.copy(alpha = 0.15f))
                .padding(horizontal = 8.dp, vertical = 3.dp),
        )
        if (item.referrer_credit_cents > 0) {
            Spacer(Modifier.width(AsterSpacing.sm))
            Text(
                text = "+" + format_cents(item.referrer_credit_cents),
                color = colors.success,
                fontSize = 13.5.sp,
                fontWeight = FontWeight.Medium,
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
            .withZone(org.astermail.android.ui.mail.AsterTimePreferences.account_zone_id())
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
fun DeveloperScreen(on_back: () -> Unit, on_open: (id: String) -> Unit = {}) {
    val vm: SettingsViewModel = shared_settings_view_model()
    val state by vm.state.collectAsStateWithLifecycle()
    val colors = AsterMaterial.colors
    val prefs = state.preferences

    LaunchedEffect(Unit) { vm.load_preferences() }

    val prefs_seeded = prefs != null && state.preferences_authoritative
    var dev_mode by remember(prefs_seeded) { mutableStateOf(prefs?.dev_mode ?: false) }
    var show_raw_headers by remember(prefs_seeded) { mutableStateOf(prefs?.show_raw_headers ?: false) }
    var save_trigger by remember { mutableIntStateOf(0) }
    var prefs_loaded_dev by remember { mutableStateOf(false) }

    LaunchedEffect(prefs, state.preferences_authoritative) {
        if (prefs != null && state.preferences_authoritative && !prefs_loaded_dev) {
            prefs_loaded_dev = true
            dev_mode = prefs.dev_mode
            show_raw_headers = prefs.show_raw_headers
        }
    }

    LaunchedEffect(state.save_status) {
        if (state.save_status != org.astermail.android.settings.SaveStatus.ERROR || !prefs_loaded_dev) return@LaunchedEffect
        val base = prefs ?: return@LaunchedEffect
        dev_mode = base.dev_mode
        show_raw_headers = base.show_raw_headers
    }

    fun save() {
        val base = prefs ?: return
        vm.save_preferences(
            base.copy(
                dev_mode = dev_mode,
                show_raw_headers = show_raw_headers,
            ),
        )
    }

    LaunchedEffect(save_trigger) {
        if (save_trigger == 0) return@LaunchedEffect
        if (!prefs_loaded_dev || prefs == null) return@LaunchedEffect
        delay(500)
        save()
    }

    val flush_on_exit: androidx.compose.runtime.State<() -> Unit> =
        androidx.compose.runtime.rememberUpdatedState({
            if (save_trigger > 0 && prefs != null && prefs_loaded_dev) save()
        })

    androidx.compose.runtime.DisposableEffect(Unit) {
        onDispose { flush_on_exit.value() }
    }

    detail_scaffold(
        title = stringResource(R.string.developer),
        on_back = on_back,
    ) {
        if (prefs == null || !state.preferences_authoritative) {
            preferences_load_placeholder()
        } else {
            preferences_save_error_banner()
            section_label(stringResource(R.string.mode))
            AsterCard(modifier = Modifier.fillMaxWidth()) {
                toggle_row(stringResource(R.string.show_raw_headers), stringResource(R.string.show_raw_headers_subtitle), show_raw_headers) { show_raw_headers = it; save_trigger++ }
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
                        } catch (cancelled: CancellationException) {
                            throw cancelled
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

internal data class label_screen_row(
    val id: String,
    val name: String,
    val color: Color,
    val count: Long?,
    val can_move_up: Boolean,
    val can_move_down: Boolean,
    val can_delete: Boolean,
    val is_tag: Boolean,
)

internal fun label_screen_rows(
    tags: List<org.astermail.android.api.tags.TagItem>,
    labels: List<org.astermail.android.api.labels.LabelItem>,
): List<label_screen_row> {
    val fallback = Color(0xFF6B7280)
    val tag_rows = org.astermail.android.labels.tag_rows(tags)
    val label_rows = org.astermail.android.labels.label_rows(labels)
    val from_tags = tag_rows.mapIndexed { idx, tag ->
        label_screen_row(
            id = tag.id,
            name = tag.encrypted_name.takeIf { it.isNotBlank() } ?: tag.tag_token,
            color = parse_hex_color_safe(tag.encrypted_color) ?: fallback,
            count = tag.item_count,
            can_move_up = idx > 0,
            can_move_down = idx < tag_rows.lastIndex,
            can_delete = true,
            is_tag = true,
        )
    }
    val from_labels = label_rows.mapIndexed { idx, label ->
        label_screen_row(
            id = label.id,
            name = label.encrypted_name?.takeIf { it.isNotBlank() } ?: label.label_token,
            color = parse_hex_color_safe(label.encrypted_color) ?: fallback,
            count = label.item_count,
            can_move_up = idx > 0,
            can_move_down = idx < label_rows.lastIndex,
            can_delete = !label.is_system && !label.is_locked,
            is_tag = false,
        )
    }
    return from_tags + from_labels
}

@Composable
fun LabelsScreen(
    on_back: () -> Unit,
    on_open: (id: String) -> Unit = {},
    vm: SettingsViewModel = shared_settings_view_model(),
) {
    val state by vm.state.collectAsStateWithLifecycle()
    val colors = AsterMaterial.colors

    LaunchedEffect(Unit) {
        vm.load_labels(folder_type = "label")
        vm.load_tags()
    }

    val labels_context = LocalContext.current

    LaunchedEffect(state.action_result) {
        val msg = state.action_result ?: return@LaunchedEffect
        android.widget.Toast.makeText(labels_context, msg, android.widget.Toast.LENGTH_SHORT).show()
        vm.clear_action_result()
    }

    val rows = label_screen_rows(state.tags, state.labels)
    var pending_label_delete by remember { mutableStateOf<label_screen_row?>(null) }
    var pending_label_rename by remember { mutableStateOf<label_screen_row?>(null) }
    var show_create_label by remember { mutableStateOf(false) }

    detail_scaffold(title = stringResource(R.string.labels), on_back = on_back) {
        AsterCard(modifier = Modifier.fillMaxWidth()) {
            detail_row(
                title = stringResource(R.string.create_label),
                subtitle = stringResource(R.string.create_label_subtitle),
                icon = TablerIcons.Tag,
                on_click = { show_create_label = true },
            )
        }
        v_gap(AsterSpacing.md)

        if (state.is_loading && rows.isEmpty()) {
            Box(
                modifier = Modifier.fillMaxWidth().padding(AsterSpacing.xxl),
                contentAlignment = Alignment.Center,
            ) {
                CircularProgressIndicator(color = colors.accent_blue, modifier = Modifier.size(24.dp))
            }
        } else if (rows.isEmpty() && state.error != null) {
            load_failed_card(state.error) {
                vm.load_labels(folder_type = "label")
                vm.load_tags()
            }
        } else if (rows.isEmpty()) {
            AsterCard(modifier = Modifier.fillMaxWidth()) {
                detail_row(
                    title = stringResource(R.string.no_labels),
                    subtitle = stringResource(R.string.no_labels_subtitle),
                )
            }
        } else {
            section_label(pluralStringResource(R.plurals.labels_count, rows.size, rows.size))
            AsterCard(modifier = Modifier.fillMaxWidth()) {
                rows.forEachIndexed { idx, row ->
                    label_settings_row(
                        name = row.name,
                        color = row.color,
                        count_text = row.count?.let { pluralStringResource(R.plurals.common_messages_count, it.toInt(), it) } ?: "",
                        can_move_up = row.can_move_up,
                        can_move_down = row.can_move_down,
                        can_delete = row.can_delete,
                        tag_suffix = idx.toString(),
                        on_move_up = { if (row.is_tag) vm.move_tag(row.id, -1) else vm.move_label_row(row.id, -1) },
                        on_move_down = { if (row.is_tag) vm.move_tag(row.id, 1) else vm.move_label_row(row.id, 1) },
                        on_delete = { pending_label_delete = row },
                        on_rename = if (row.can_delete) ({ pending_label_rename = row }) else null,
                    )
                    if (idx < rows.lastIndex) AsterDivider(modifier = Modifier)
                }
            }
        }
        v_gap(AsterSpacing.xxl)
    }

    if (show_create_label) {
        org.astermail.android.ui.drawer.create_label_dialog(
            on_dismiss = { show_create_label = false },
            on_create = { name, color, icon ->
                vm.create_tag(name = name, color = color, icon = icon)
                show_create_label = false
            },
            existing_names = rows.map { it.name },
        )
    }

    pending_label_rename?.let { target ->
        org.astermail.android.ui.drawer.folder_rename_dialog(
            initial_name = target.name,
            title = stringResource(R.string.rename_label),
            placeholder = stringResource(R.string.label_name),
            on_dismiss = { pending_label_rename = null },
            on_confirm = { new_name ->
                if (target.is_tag) vm.rename_tag(target.id, new_name) else vm.rename_folder(target.id, new_name)
                pending_label_rename = null
            },
        )
    }

    pending_label_delete?.let { target ->
        org.astermail.android.design.components.AsterAlertDialog(
            on_dismiss = { pending_label_delete = null },
            title = stringResource(R.string.delete_label_confirm_title),
            message = stringResource(R.string.delete_label_confirm_message, target.name),
            confirm_label = stringResource(R.string.delete),
            cancel_label = stringResource(R.string.cancel),
            confirm_style = org.astermail.android.design.components.DialogConfirmStyle.destructive,
            on_confirm = {
                if (target.is_tag) vm.delete_tag(target.id) else vm.delete_label(target.id)
                pending_label_delete = null
            },
        )
    }
}

@Composable
internal fun label_settings_row(
    name: String,
    color: Color,
    count_text: String,
    can_move_up: Boolean,
    can_move_down: Boolean,
    can_delete: Boolean,
    tag_suffix: String,
    on_move_up: () -> Unit,
    on_move_down: () -> Unit,
    on_delete: () -> Unit,
    on_rename: (() -> Unit)? = null,
) {
    val colors = AsterMaterial.colors
    Row(
        modifier = Modifier.fillMaxWidth().padding(horizontal = AsterSpacing.lg, vertical = AsterSpacing.md),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(modifier = Modifier.size(12.dp).background(color, CircleShape))
        Spacer(Modifier.width(AsterSpacing.md))
        Column(
            modifier = Modifier
                .weight(1f)
                .then(if (on_rename != null) Modifier.clickable(onClick = on_rename) else Modifier),
        ) {
            Text(name, color = colors.text_primary, fontSize = 15.sp, fontWeight = FontWeight.Medium)
            if (count_text.isNotEmpty()) {
                Text(count_text, color = colors.text_tertiary, fontSize = 13.sp)
            }
        }
        Box(
            modifier = Modifier
                .size(32.dp)
                .clip(CircleShape)
                .clickable(enabled = can_move_up, onClick = on_move_up)
                .testTag("label_move_up_$tag_suffix"),
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
                .clickable(enabled = can_move_down, onClick = on_move_down)
                .testTag("label_move_down_$tag_suffix"),
            contentAlignment = Alignment.Center,
        ) {
            androidx.compose.material3.Icon(
                imageVector = TablerIcons.ChevronDown,
                contentDescription = stringResource(R.string.move_folder_down),
                tint = if (can_move_down) colors.text_secondary else colors.text_muted.copy(alpha = 0.35f),
                modifier = Modifier.size(20.dp),
            )
        }
        if (can_delete) {
            Box(
                modifier = Modifier
                    .size(36.dp)
                    .clip(CircleShape)
                    .clickable(onClick = on_delete)
                    .padding(AsterSpacing.xs)
                    .testTag("label_delete_$tag_suffix"),
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
    var pending_folder_rename by remember { mutableStateOf<org.astermail.android.api.labels.LabelItem?>(null) }
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
        } else if (folder_nodes.isEmpty() && state.error != null) {
            load_failed_card(state.error) {
                vm.load_labels(folder_type = "folder")
                vm.load_preferences()
            }
        } else if (folder_nodes.isEmpty()) {
            AsterCard(modifier = Modifier.fillMaxWidth()) {
                detail_row(
                    title = stringResource(R.string.no_folders),
                    subtitle = stringResource(R.string.no_folders_subtitle),
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
                                on_click = if (f.is_system || f.is_locked) null else ({ pending_folder_rename = f }),
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

    pending_folder_rename?.let { target ->
        org.astermail.android.ui.drawer.folder_rename_dialog(
            initial_name = target.encrypted_name?.takeIf { it.isNotBlank() } ?: target.label_token,
            on_dismiss = { pending_folder_rename = null },
            on_confirm = { new_name ->
                vm.rename_folder(target.id, new_name)
                pending_folder_rename = null
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

    val prefs_seeded = prefs != null && state.preferences_authoritative
    var block_trackers by remember(prefs_seeded) {
        mutableStateOf(prefs?.block_tracking_pixels != false && prefs?.block_external_content != false)
    }
    var remote_images by remember(prefs_seeded) { mutableStateOf(prefs?.load_remote_images == "always") }
    var link_warnings by remember(prefs_seeded) { mutableStateOf(prefs?.warn_suspicious_links ?: true) }
    var strip_exif by remember(prefs_seeded) { mutableStateOf(prefs?.strip_exif_on_compose ?: true) }
    var save_trigger by remember { mutableIntStateOf(0) }
    var prefs_loaded_priv by remember { mutableStateOf(false) }

    LaunchedEffect(prefs, state.preferences_authoritative) {
        if (prefs != null && state.preferences_authoritative && !prefs_loaded_priv) {
            prefs_loaded_priv = true
            block_trackers = prefs.block_tracking_pixels && prefs.block_external_content != false
            remote_images = prefs.load_remote_images == "always"
            link_warnings = prefs.warn_suspicious_links
            strip_exif = prefs.strip_exif_on_compose
        }
    }

    LaunchedEffect(state.save_status) {
        if (state.save_status != org.astermail.android.settings.SaveStatus.ERROR || !prefs_loaded_priv) return@LaunchedEffect
        val base = prefs ?: return@LaunchedEffect
        block_trackers = base.block_tracking_pixels && base.block_external_content != false
        remote_images = base.load_remote_images == "always"
        link_warnings = base.warn_suspicious_links
        strip_exif = base.strip_exif_on_compose
    }

    fun save() {
        val base = prefs ?: return
        vm.save_preferences(
            base.copy(
                block_trackers = block_trackers,
                block_tracking_pixels = block_trackers,
                block_external_content = if (block_trackers) true else base.block_external_content,
                load_remote_images = when {
                    remote_images -> "always"
                    base.load_remote_images == "ask" -> "ask"
                    else -> "never"
                },
                block_external_images = !remote_images,
                warn_suspicious_links = link_warnings,
                strip_exif = strip_exif,
                strip_exif_on_compose = strip_exif,
            ),
        )
    }

    LaunchedEffect(save_trigger) {
        if (save_trigger == 0) return@LaunchedEffect
        if (!prefs_loaded_priv || prefs == null) return@LaunchedEffect
        delay(500)
        save()
        save_trigger = 0
    }

    val flush_on_exit: androidx.compose.runtime.State<() -> Unit> =
        androidx.compose.runtime.rememberUpdatedState({
            if (save_trigger > 0 && prefs != null && prefs_loaded_priv) save()
        })

    androidx.compose.runtime.DisposableEffect(Unit) {
        onDispose { flush_on_exit.value() }
    }

    detail_scaffold(
        title = stringResource(R.string.privacy),
        on_back = on_back,
    ) {
        if (prefs == null || !state.preferences_authoritative) {
            preferences_load_placeholder()
        } else {
            preferences_save_error_banner()
            section_label(stringResource(R.string.tracking))
            AsterCard(modifier = Modifier.fillMaxWidth()) {
                toggle_row(stringResource(R.string.block_tracking_pixels_privacy), stringResource(R.string.block_tracking_pixels_subtitle), block_trackers) { block_trackers = it; save_trigger++ }
                AsterDivider(modifier = Modifier)
                toggle_row(stringResource(R.string.load_remote_images), stringResource(R.string.load_remote_images_subtitle), remote_images) { remote_images = it; save_trigger++ }
            }
            v_gap(AsterSpacing.lg)
            section_label(stringResource(R.string.protection))
            AsterCard(modifier = Modifier.fillMaxWidth()) {
                toggle_row(stringResource(R.string.warn_suspicious_links), null, link_warnings) { link_warnings = it; save_trigger++ }
                AsterDivider(modifier = Modifier)
                toggle_row(stringResource(R.string.strip_exif), stringResource(R.string.strip_exif_subtitle), strip_exif) { strip_exif = it; save_trigger++ }
            }
            v_gap(AsterSpacing.lg)
            detail_row(
                title = stringResource(R.string.privacy_policy),
                icon = TablerIcons.ShieldLock,
                on_click = { org.astermail.android.ui.common.open_external_url(context, "https://astermail.org/privacy") },
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
        } else if (state.api_keys.isEmpty() && state.error != null) {
            load_failed_card(state.error) { vm.load_api_keys() }
        } else if (state.api_keys.isEmpty()) {
            AsterCard(modifier = Modifier.fillMaxWidth()) {
                detail_row(
                    title = stringResource(R.string.no_api_keys),
                    subtitle = stringResource(R.string.no_api_keys_subtitle),
                )
            }
        } else {
            section_label(pluralStringResource(R.plurals.api_keys_count, state.api_keys.size, state.api_keys.size))
            AsterCard(modifier = Modifier.fillMaxWidth()) {
                state.api_keys.forEachIndexed { idx, k ->
                    detail_row(
                        title = k.decrypted_name.ifBlank { stringResource(R.string.api_key_default_name) },
                        subtitle = "${k.prefix}... " + stringResource(R.string.created_at_format, relative_time_label(k.created_at)),
                        icon = TablerIcons.Plug,
                        on_click = null,
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
        AsterButton(
            label = stringResource(R.string.generate_new_key),
            onClick = { vm.create_api_key(mobile_key_name) },
            enabled = !state.api_key_creating,
            is_loading = state.api_key_creating,
        )
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
                    org.astermail.android.ui.common.open_external_url(context, "https://app.astermail.org/settings/integrations")
                },
            )
            AsterDivider(modifier = Modifier)
            detail_row(
                title = stringResource(R.string.calendar),
                subtitle = stringResource(R.string.calendar_subtitle),
                icon = TablerIcons.Puzzle,
                on_click = {
                    org.astermail.android.ui.common.open_external_url(context, "https://app.astermail.org/settings/integrations")
                },
            )
            AsterDivider(modifier = Modifier)
            detail_row(
                title = stringResource(R.string.zapier),
                subtitle = stringResource(R.string.zapier_subtitle),
                icon = TablerIcons.Puzzle,
                on_click = {
                    org.astermail.android.ui.common.open_external_url(context, "https://app.astermail.org/settings/integrations")
                },
            )
        }
        v_gap(AsterSpacing.lg)
        section_label(stringResource(R.string.webhooks))
        AsterCard(modifier = Modifier.fillMaxWidth()) {
            val webhook_count = state.webhooks.size
            val subtitle = if (webhook_count == 0) stringResource(R.string.no_active_endpoints) else pluralStringResource(R.plurals.active_endpoints, webhook_count, webhook_count)
            detail_row(
                title = stringResource(R.string.manage_webhooks),
                subtitle = subtitle,
                icon = TablerIcons.Link,
                on_click = {
                    org.astermail.android.ui.common.open_external_url(context, "https://app.astermail.org/settings/integrations")
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
        } else if (state.subscription_load_failed && sub == null) {
            load_failed_card(state.error) { vm.load_subscription() }
        } else if (is_family) {
            AsterCard(modifier = Modifier.fillMaxWidth()) {
                detail_row(
                    title = stringResource(R.string.manage_family),
                    subtitle = stringResource(R.string.manage_family_subtitle),
                    icon = TablerIcons.Users,
                    on_click = {
                        org.astermail.android.ui.common.open_external_url(context, "https://app.astermail.org/settings/family")
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
                    org.astermail.android.ui.common.open_external_url(context, "https://app.astermail.org/settings/billing")
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
        val copied = write_to_clipboard(context, ClipData.newPlainText(label, text))
        show_copy_result_toast(context, text, copied)
    }

    detail_scaffold(title = stringResource(R.string.kids_reserved_addresses), on_back = on_back) {
        section_label(stringResource(R.string.kids_reserved_subtitle))

        val seats = state.family_seats
        if (seats != null && seats.max_members > 0) {
            Text(
                text = stringResource(R.string.kids_seats_used, seats.seats_used, seats.max_members) +
                    " · " +
                    pluralStringResource(R.plurals.kids_seats_free, seats.seats_remaining, seats.seats_remaining),
                color = colors.text_tertiary,
                fontSize = 12.sp,
            )
            val breakdown = seats.breakdown
            if (breakdown != null) {
                Text(
                    text = stringResource(
                        R.string.kids_seats_breakdown,
                        pluralStringResource(
                            R.plurals.kids_seats_members,
                            breakdown.active_members,
                            breakdown.active_members,
                        ),
                        pluralStringResource(
                            R.plurals.kids_seats_invitations,
                            breakdown.pending_invites,
                            breakdown.pending_invites,
                        ),
                        pluralStringResource(
                            R.plurals.kids_seats_reserved,
                            breakdown.reserved_addresses,
                            breakdown.reserved_addresses,
                        ),
                    ),
                    color = colors.text_tertiary,
                    fontSize = 11.sp,
                    modifier = androidx.compose.ui.Modifier.padding(bottom = AsterSpacing.sm),
                )
            }
        }

        AsterButton(
            label = stringResource(R.string.kids_reserve_on_web),
            onClick = {
                org.astermail.android.ui.common.open_external_url(context, "https://app.astermail.org/settings/family")
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

    val context = LocalContext.current
    val activity = context as? android.app.Activity
    val languages = org.astermail.android.settings.app_language.supported
    val prefs_seeded = prefs != null
    var selected by remember(prefs_seeded) {
        mutableStateOf(org.astermail.android.settings.app_language.normalize_code(prefs?.language) ?: "en")
    }
    var lang_loaded by remember { mutableStateOf(false) }

    LaunchedEffect(prefs) {
        if (prefs != null && !lang_loaded) {
            lang_loaded = true
            selected = org.astermail.android.settings.app_language.stored_code(context)
                ?: org.astermail.android.settings.app_language.normalize_code(prefs.language)
                ?: selected
        }
    }

    fun save(code: String) {
        if (code == selected) return
        selected = code
        org.astermail.android.settings.app_language.store_code(context, code)
        prefs?.let { vm.save_preferences(it.copy(language = code)) }
        activity?.recreate()
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
                        val device_language = android.content.res.Resources.getSystem()
                            .configuration.locales[0].language
                        val supported = languages.any { it.first == device_language }
                        save(if (supported) device_language else "en")
                    },
                )
            }
        }
        v_gap(AsterSpacing.xxl)
    }
}
