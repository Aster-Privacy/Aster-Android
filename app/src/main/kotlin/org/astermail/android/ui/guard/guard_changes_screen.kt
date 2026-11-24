//
// Aster Communications Inc.
//
// Copyright (c) 2026 Aster Communications Inc.
//
// This file is part of this project.
//
// This program is free software: you can redistribute it and/or modify
// it under the terms of the AGPLv3 as published by
// the Free Software Foundation, either version 3 of the License, or
// (at your option) any later version.
//
// This program is distributed in the hope that it will be useful,
// but WITHOUT ANY WARRANTY; without even the implied warranty of
// MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
// AGPLv3 for more details.
//
// You should have received a copy of the AGPLv3
// along with this program. If not, see <https://www.gnu.org/licenses/>.
//

package org.astermail.android.ui.guard

import androidx.annotation.PluralsRes
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.ProgressBarRangeInfo
import androidx.compose.ui.semantics.progressBarRangeInfo
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import compose.icons.TablerIcons
import compose.icons.tablericons.Clock
import compose.icons.tablericons.Download
import compose.icons.tablericons.Key
import compose.icons.tablericons.Lifebuoy
import compose.icons.tablericons.MailForward
import compose.icons.tablericons.Plug
import compose.icons.tablericons.Shield
import compose.icons.tablericons.ShieldCheck
import compose.icons.tablericons.ShieldOff
import java.time.Duration
import java.time.Instant
import org.astermail.android.R
import org.astermail.android.design.AsterEasing
import org.astermail.android.design.AsterMaterial
import org.astermail.android.design.AsterSpacing
import org.astermail.android.design.components.AsterAccentButton
import org.astermail.android.design.components.AsterButton
import org.astermail.android.design.components.AsterDestructiveButton
import org.astermail.android.design.components.AsterGhostButton
import org.astermail.android.guard.guard_format_instant
import org.astermail.android.guard.guard_hold_kind
import org.astermail.android.guard.guard_prompt_text
import org.astermail.android.guard.resolve
import org.astermail.android.guard.label
import org.astermail.android.ui.settings.detail.detail_scaffold
import org.astermail.android.ui.settings.detail.error_banner
import org.astermail.android.ui.settings.detail.load_failed_card
import org.astermail.android.ui.settings.detail.recommendation_note

@Composable
fun guard_changes_host(
    view_model: GuardChangesViewModel,
    on_back: () -> Unit,
    on_not_me: () -> Unit = {},
    on_change_password: (() -> Unit)? = null,
) {
    val state by view_model.state.collectAsState()
    val context = LocalContext.current
    val activity = find_fragment_activity(context)
    val prompt = guard_prompt_text(
        title = stringResource(R.string.guard_prompt_title),
        subtitle = stringResource(R.string.guard_changes_prompt_subtitle),
    )
    guard_changes_screen(
        state = state,
        on_back = on_back,
        on_cancel = { id -> view_model.cancel(guard_prompt_signer(activity, prompt), id) },
        on_skip = { id -> view_model.skip(guard_prompt_signer(activity, prompt), id) },
        on_stop = { id -> view_model.stop(guard_prompt_signer(activity, prompt), id) },
        on_retry = view_model::refresh,
        on_skip_passkey = { id ->
            view_model.skip_with_passkey(guard_prompt_signer(activity, prompt), guard_passkey_sheet_for(context), id)
        },
        on_not_me = on_not_me,
        on_change_password = on_change_password,
    )
}

@Composable
fun guard_changes_screen(
    state: guard_changes_ui,
    on_back: () -> Unit,
    on_cancel: (String) -> Unit,
    on_skip: (String) -> Unit,
    on_stop: (String) -> Unit,
    on_retry: () -> Unit,
    live: Boolean = true,
    on_not_me: () -> Unit = {},
    on_change_password: (() -> Unit)? = null,
    on_skip_passkey: (String) -> Unit = {},
) {
    detail_scaffold(title = stringResource(R.string.guard_changes_title), on_back = on_back) {
        when {
            guard_changes_shows_stopped(state.notice, on_change_password != null) -> {
                Spacer(Modifier.height(AsterSpacing.xl))
                guard_result_block(
                    title = stringResource(R.string.guard_veto_done_title),
                    body = stringResource(R.string.guard_veto_done_body),
                    primary_label = stringResource(R.string.change_password),
                    on_primary = { on_change_password?.invoke() },
                    secondary_label = stringResource(R.string.done),
                    on_secondary = on_back,
                )
            }
            state.hidden -> error_banner(stringResource(R.string.guard_setup_unavailable))
            state.load_failed && state.changes.isEmpty() -> load_failed_card(null, on_retry)
            state.loading -> Unit
            else -> guard_changes_content(state, on_cancel, on_skip, on_stop, on_not_me, live, on_skip_passkey)
        }
    }
}

internal fun guard_changes_shows_stopped(notice: guard_changes_notice?, can_change_password: Boolean): Boolean =
    notice == guard_changes_notice.change_stopped && can_change_password

fun guard_hold_kind.icon(): ImageVector = when (this) {
    guard_hold_kind.forwarding_change, guard_hold_kind.mail_rule_change -> TablerIcons.MailForward
    guard_hold_kind.mail_app_activation -> TablerIcons.Plug
    guard_hold_kind.password_change, guard_hold_kind.second_factor_off, guard_hold_kind.pgp_export,
    guard_hold_kind.rescue_code_rotation -> TablerIcons.Key
    guard_hold_kind.data_export -> TablerIcons.Download
    guard_hold_kind.recovery_email_change, guard_hold_kind.recovery_phone_change,
    guard_hold_kind.account_recovery, guard_hold_kind.rescue -> TablerIcons.Lifebuoy
    guard_hold_kind.guard_device_remove, guard_hold_kind.guard_switches_off -> TablerIcons.ShieldOff
    guard_hold_kind.unknown -> TablerIcons.Shield
}

fun guard_change_progress(created_at: Instant?, apply_after: Instant?, now: Instant): Float? {
    if (created_at == null || apply_after == null) return null
    val total = Duration.between(created_at, apply_after).toMillis()
    if (total <= 0) return 1f
    val done = Duration.between(created_at, now).toMillis()
    return (done.toFloat() / total).coerceIn(0f, 1f)
}

data class guard_wait_amount(@PluralsRes val plural: Int, val count: Int)

fun guard_wait_amount_for(remaining: Duration): guard_wait_amount {
    val minutes = remaining.toMinutes().coerceAtLeast(1)
    return when {
        minutes >= 60 * 24 * 2 -> guard_wait_amount(R.plurals.guard_wait_days, (minutes / (60 * 24)).toInt())
        minutes >= 60 * 2 -> guard_wait_amount(R.plurals.guard_wait_hours, (minutes / 60).toInt())
        else -> guard_wait_amount(R.plurals.guard_wait_minutes, minutes.toInt())
    }
}

@Composable
private fun guard_changes_content(
    state: guard_changes_ui,
    on_cancel: (String) -> Unit,
    on_skip: (String) -> Unit,
    on_stop: (String) -> Unit,
    on_not_me: () -> Unit,
    live: Boolean,
    on_skip_passkey: (String) -> Unit,
) {
    guard_changes_notice_text(state.notice, state.error)?.let {
        if (state.notice in guard_changes_success) recommendation_note(it) else error_banner(it)
        Spacer(Modifier.height(AsterSpacing.md))
    }
    guard_passkey_failure_text(state.passkey_failure)?.let {
        error_banner(it)
        Spacer(Modifier.height(AsterSpacing.md))
    }
    if (state.changes.isEmpty()) {
        guard_changes_empty()
        return
    }
    if (!state.is_guard_device) {
        recommendation_note(stringResource(R.string.guard_changes_not_guard_device))
        Spacer(Modifier.height(AsterSpacing.md))
    }
    Text(
        text = stringResource(R.string.guard_changes_body),
        color = AsterMaterial.colors.text_secondary,
        fontSize = 14.sp,
        lineHeight = 20.sp,
    )
    Spacer(Modifier.height(AsterSpacing.md))
    val now = Instant.ofEpochMilli(guard_now(60_000L, ticking = live))
    Column(verticalArrangement = Arrangement.spacedBy(AsterSpacing.md)) {
        state.changes.forEach { row ->
            guard_change_card(
                row = row,
                state = state,
                now = now,
                on_cancel = { on_cancel(row.id) },
                on_skip = { on_skip(row.id) },
                on_stop = { on_stop(row.id) },
                on_not_me = on_not_me,
                on_skip_passkey = { on_skip_passkey(row.id) },
            )
        }
    }
}

@Composable
private fun guard_changes_empty() {
    Text(
        text = stringResource(R.string.guard_changes_empty_title) + "\n" + stringResource(R.string.guard_changes_empty),
        color = AsterMaterial.colors.text_secondary,
        fontSize = 14.sp,
        lineHeight = 20.sp,
        modifier = Modifier.padding(horizontal = AsterSpacing.xs, vertical = AsterSpacing.lg),
    )
}

private val guard_changes_success = setOf(
    guard_changes_notice.change_canceled,
    guard_changes_notice.change_skipped,
    guard_changes_notice.change_stopped,
    guard_changes_notice.change_already_ended,
)

@Composable
private fun guard_change_card(
    row: guard_change_row,
    state: guard_changes_ui,
    now: Instant,
    on_cancel: () -> Unit,
    on_skip: () -> Unit,
    on_stop: () -> Unit,
    on_not_me: () -> Unit,
    on_skip_passkey: () -> Unit,
) {
    val colors = AsterMaterial.colors
    val busy = state.working_id != null
    val working = state.working_id == row.id
    val urgent = row.kind.can_veto
    val tone = if (urgent) colors.warning else colors.accent_blue
    guard_card {
        Column(modifier = Modifier.padding(AsterSpacing.lg)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                guard_vector_tile(icon = row.kind.icon(), color = colors.text_secondary)
                Spacer(Modifier.width(AsterSpacing.md))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = stringResource(row.kind.label()),
                        color = colors.text_primary,
                        fontSize = 16.sp,
                        lineHeight = 21.sp,
                        fontWeight = FontWeight.SemiBold,
                    )
                    row.created_at?.let {
                        Text(
                            text = stringResource(R.string.guard_changes_requested_at, guard_format_instant(it)),
                            color = colors.text_secondary,
                            fontSize = 13.sp,
                        )
                    }
                }
            }
            Spacer(Modifier.height(AsterSpacing.md))
            val remaining = row.apply_after?.let { Duration.between(now, it) }
            val chip_text = when {
                remaining == null -> stringResource(R.string.guard_changes_applies_later)
                remaining.isNegative || remaining.isZero -> stringResource(R.string.guard_changes_due_now)
                else -> {
                    val amount = guard_wait_amount_for(remaining)
                    val span = pluralStringResource(amount.plural, amount.count, amount.count)
                    stringResource(if (urgent) R.string.guard_changes_completes_in else R.string.guard_changes_applies_in, span)
                }
            }
            guard_chip(text = chip_text, icon = TablerIcons.Clock, color = tone)
            guard_change_progress(row.created_at, row.apply_after, now)?.let { progress ->
                Spacer(Modifier.height(AsterSpacing.md))
                guard_progress_bar(progress = progress, color = tone)
            }
            row.apply_after?.let {
                Spacer(Modifier.height(AsterSpacing.sm))
                Text(
                    text = stringResource(
                        if (urgent) R.string.guard_changes_completes_at else R.string.guard_changes_applies_at,
                        guard_format_instant(it),
                    ),
                    color = colors.text_secondary,
                    fontSize = 13.sp,
                )
            }
            if (urgent && state.is_guard_device) {
                Spacer(Modifier.height(AsterSpacing.sm))
                Text(
                    text = stringResource(R.string.guard_changes_recovery_hint),
                    color = colors.text_secondary,
                    fontSize = 13.sp,
                    lineHeight = 18.sp,
                )
            }
            if (state.skip_needs_trust(row)) {
                Spacer(Modifier.height(AsterSpacing.sm))
                Text(
                    text = stringResource(R.string.guard_changes_skip_needs_trust),
                    color = colors.text_secondary,
                    fontSize = 13.sp,
                    lineHeight = 18.sp,
                )
            }
            val show_cancel = state.can_cancel(row)
            val show_stop = state.can_stop(row)
            val show_skip = state.can_skip(row)
            if (show_stop) {
                Spacer(Modifier.height(AsterSpacing.md))
                AsterAccentButton(
                    label = stringResource(R.string.guard_changes_stop),
                    onClick = on_stop,
                    fill = colors.danger,
                    content_color = Color.White,
                    enabled = !busy,
                    is_loading = working,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
            Spacer(Modifier.height(AsterSpacing.md))
            Row(horizontalArrangement = Arrangement.spacedBy(AsterSpacing.sm)) {
                AsterDestructiveButton(
                    label = stringResource(R.string.guard_panic_entry),
                    onClick = on_not_me,
                    enabled = !busy,
                    modifier = Modifier.weight(1f),
                )
                if (show_cancel) {
                    AsterButton(
                        label = stringResource(R.string.guard_changes_cancel),
                        onClick = on_cancel,
                        enabled = !busy,
                        is_loading = working,
                        modifier = Modifier.weight(1f),
                    )
                } else if (show_skip) {
                    AsterGhostButton(
                        label = stringResource(R.string.guard_changes_skip),
                        onClick = on_skip,
                        enabled = !busy,
                        modifier = Modifier.weight(1f),
                    )
                }
            }
            if (show_cancel && show_skip) {
                Spacer(Modifier.height(AsterSpacing.sm))
                AsterGhostButton(
                    label = stringResource(R.string.guard_changes_skip),
                    onClick = on_skip,
                    enabled = !busy,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
            if (state.can_skip_with_passkey(row)) {
                Spacer(Modifier.height(AsterSpacing.sm))
                AsterGhostButton(
                    label = stringResource(R.string.guard_changes_skip_passkey),
                    onClick = on_skip_passkey,
                    enabled = !busy,
                    modifier = Modifier.fillMaxWidth().testTag("guard_changes_skip_passkey"),
                )
            }
        }
    }
}

@Composable
fun guard_progress_bar(progress: Float, color: Color, modifier: Modifier = Modifier) {
    val colors = AsterMaterial.colors
    val shown by animateFloatAsState(
        targetValue = progress,
        animationSpec = tween(durationMillis = 420, easing = LinearEasing),
        label = "guard_progress",
    )
    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(6.dp)
            .clip(CircleShape)
            .background(colors.border_secondary)
            .semantics { progressBarRangeInfo = ProgressBarRangeInfo(progress, 0f..1f) },
    ) {
        Box(
            Modifier
                .fillMaxHeight()
                .fillMaxWidth(shown.coerceIn(0.02f, 1f))
                .clip(CircleShape)
                .background(color),
        )
    }
}

@Composable
private fun guard_changes_notice_text(
    notice: guard_changes_notice?,
    error: org.astermail.android.guard.guard_error_message? = null,
): String? = when (notice) {
    null -> null
    guard_changes_notice.canceled -> stringResource(R.string.guard_changes_prompt_canceled)
    guard_changes_notice.key_lost -> stringResource(R.string.guard_approve_key_lost)
    guard_changes_notice.no_lock_screen -> stringResource(R.string.guard_setup_no_lock_screen)
    guard_changes_notice.no_device -> stringResource(R.string.guard_approve_no_device)
    guard_changes_notice.failed -> error?.resolve() ?: stringResource(R.string.guard_setup_failed)
    guard_changes_notice.step_up_required -> stringResource(R.string.guard_changes_step_up_required)
    guard_changes_notice.change_canceled -> stringResource(R.string.guard_changes_done_canceled)
    guard_changes_notice.change_skipped -> stringResource(R.string.guard_changes_done_skipped)
    guard_changes_notice.change_stopped -> stringResource(R.string.guard_changes_done_stopped)
    guard_changes_notice.change_already_ended -> stringResource(R.string.guard_changes_already_ended)
    guard_changes_notice.change_stale -> stringResource(R.string.guard_changes_stale)
}
