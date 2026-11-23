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

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.DialogProperties
import androidx.compose.ui.window.SecureFlagPolicy
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import compose.icons.TablerIcons
import compose.icons.tablericons.Clock
import compose.icons.tablericons.MapPin
import kotlinx.coroutines.delay
import org.astermail.android.R
import org.astermail.android.design.AsterMaterial
import org.astermail.android.design.AsterSpacing
import org.astermail.android.design.components.AsterButton
import org.astermail.android.design.components.AsterDestructiveButton
import org.astermail.android.guard.guard_known_label
import org.astermail.android.guard.guard_prompt_text
import org.astermail.android.guard.resolve
import org.astermail.android.guard.guard_sign_result
import org.astermail.android.guard.guard_signer
import org.astermail.android.guard.keystore_signer
import org.astermail.android.ui.common.secure_screen

const val GUARD_SHEET_CLOSE_DELAY_MS = 1400L

val guard_approve_dialog_properties = DialogProperties(
    usePlatformDefaultWidth = false,
    decorFitsSystemWindows = false,
    dismissOnClickOutside = false,
    securePolicy = SecureFlagPolicy.SecureOn,
)

@Composable
fun guard_approve_host(
    view_model: GuardApproveViewModel,
    on_close: () -> Unit,
    on_unlock: (() -> Unit)? = null,
    on_secure: (() -> Unit)? = null,
) {
    val activity = find_fragment_activity(LocalContext.current)
    val approve_prompt = guard_prompt_text(
        stringResource(R.string.guard_prompt_title),
        stringResource(R.string.guard_prompt_approve_subtitle),
    )
    val deny_prompt = guard_prompt_text(
        stringResource(R.string.guard_prompt_title),
        stringResource(R.string.guard_prompt_deny_subtitle),
    )
    val state by view_model.state.collectAsStateWithLifecycle()
    guard_approve_screen(
        state = state,
        on_number = view_model::on_number,
        on_approve = { view_model.approve(guard_prompt_signer(activity, approve_prompt)) },
        on_deny = { view_model.deny(guard_prompt_signer(activity, deny_prompt)) },
        on_retry_unlocked = view_model::retry_unlocked,
        on_close = on_close,
        on_unlock = on_unlock ?: view_model::retry_unlocked,
        on_secure = on_secure,
    )
}

fun guard_prompt_signer(activity: androidx.fragment.app.FragmentActivity?, prompt: guard_prompt_text): guard_signer =
    activity?.let { keystore_signer(it, prompt) } ?: guard_signer { guard_sign_result.canceled }

@Composable
fun guard_approve_screen(
    state: guard_approve_state,
    on_number: (String) -> Unit,
    on_approve: () -> Unit,
    on_deny: () -> Unit,
    on_retry_unlocked: () -> Unit,
    on_close: () -> Unit,
    on_unlock: () -> Unit = on_retry_unlocked,
    auto_close: Boolean = true,
    on_secure: (() -> Unit)? = null,
) {
    secure_screen()
    BackHandler { on_close() }
    guard_sheet_frame {
        AnimatedContent(
            targetState = state,
            contentKey = { it::class },
            transitionSpec = { fadeIn(tween(200, delayMillis = 60)) togetherWith fadeOut(tween(120)) },
            label = "guard_approve",
        ) { current ->
            Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
                when (current) {
                    guard_approve_state.loading, guard_approve_state.signing -> approve_loading()
                    is guard_approve_state.ready -> approve_ready_body(current, on_number, on_approve, on_deny)
                    guard_approve_state.unlock_first -> approve_unlock(on_unlock, on_close)
                    guard_approve_state.approved -> approve_result_success(on_close, auto_close)
                    guard_approve_state.denied -> approve_result_denied(on_close, on_secure, auto_close)
                    guard_approve_state.gone -> approve_result_info(
                        stringResource(R.string.guard_approve_gone_title),
                        stringResource(R.string.guard_approve_gone),
                        on_close,
                    )
                    guard_approve_state.mismatched -> approve_result_info(
                        stringResource(R.string.guard_approve_mismatch_title),
                        stringResource(R.string.guard_approve_mismatch),
                        on_close,
                    )
                    guard_approve_state.key_lost -> approve_result_info(
                        stringResource(R.string.guard_approve_key_lost_title),
                        stringResource(R.string.guard_approve_key_lost),
                        on_close,
                    )
                    guard_approve_state.no_device -> approve_result_info(
                        stringResource(R.string.guard_approve_no_device_title),
                        stringResource(R.string.guard_approve_no_device),
                        on_close,
                    )
                }
            }
        }
    }
}

@Composable
private fun approve_loading() {
    val colors = AsterMaterial.colors
    guard_sheet_header(title = stringResource(R.string.guard_approve_title))
    Spacer(Modifier.height(AsterSpacing.xxl))
    CircularProgressIndicator(color = colors.accent_blue, strokeWidth = 3.dp, modifier = Modifier.size(32.dp))
    Spacer(Modifier.height(AsterSpacing.xxl))
}

@Composable
private fun approve_ready_body(
    state: guard_approve_state.ready,
    on_number: (String) -> Unit,
    on_approve: () -> Unit,
    on_deny: () -> Unit,
) {
    val colors = AsterMaterial.colors
    val expires_at = guard_epoch_millis(state.expires_at)
    val now = guard_now(1000L, ticking = expires_at != null)
    val remaining = expires_at?.let { (it - now) / 1000 }
    val expired = remaining != null && remaining <= 0
    guard_sheet_header(
        title = stringResource(if (state.is_join) R.string.guard_approve_join_title else R.string.guard_approve_title),
    )
    if (remaining != null) {
        Spacer(Modifier.height(AsterSpacing.md))
        guard_chip(
            text = if (expired) {
                stringResource(R.string.guard_approve_expired)
            } else {
                stringResource(R.string.guard_approve_expires_in, guard_countdown_text(remaining))
            },
            icon = TablerIcons.Clock,
            color = guard_expiry_tone(colors, remaining),
        )
    }
    Spacer(Modifier.height(AsterSpacing.md))
    Text(
        text = stringResource(if (state.is_join) R.string.guard_approve_join_body else R.string.guard_approve_body),
        color = colors.text_secondary,
        fontSize = 14.sp,
        lineHeight = 20.sp,
        textAlign = TextAlign.Center,
    )
    Spacer(Modifier.height(AsterSpacing.xl))
    approve_request_card(state, now)
    Spacer(Modifier.height(AsterSpacing.xxl))
    Text(
        text = stringResource(R.string.guard_approve_number_label),
        color = colors.text_primary,
        fontSize = 15.sp,
        textAlign = TextAlign.Center,
    )
    Spacer(Modifier.height(AsterSpacing.md))
    guard_digit_boxes(
        value = state.number_input,
        on_value = on_number,
        digits = 2,
        error = false,
        shake_key = 0,
        enabled = !expired,
        description = stringResource(R.string.guard_approve_number_label),
    )
    Spacer(Modifier.height(AsterSpacing.sm))
    val notice_text = when (state.notice) {
        guard_approve_notice.failed -> state.error?.resolve() ?: stringResource(R.string.guard_approve_failed)
        null -> stringResource(R.string.guard_approve_number_help)
    }
    Text(
        text = notice_text,
        color = if (state.notice != null) colors.danger else colors.text_tertiary,
        fontSize = 13.sp,
        lineHeight = 18.sp,
        textAlign = TextAlign.Center,
        modifier = Modifier
            .fillMaxWidth()
            .semantics { liveRegion = LiveRegionMode.Polite },
    )
    Spacer(Modifier.height(AsterSpacing.xl))
    AsterButton(
        label = stringResource(R.string.guard_approve_action),
        onClick = on_approve,
        enabled = state.can_approve && !expired,
        modifier = Modifier.fillMaxWidth(),
    )
    Spacer(Modifier.height(AsterSpacing.md))
    AsterDestructiveButton(
        label = stringResource(R.string.guard_deny_action),
        onClick = on_deny,
        enabled = !expired,
        modifier = Modifier.fillMaxWidth(),
    )
}

internal fun guard_expiry_tone(colors: org.astermail.android.design.AsterSemanticColors, remaining: Long) = when {
    remaining <= 0 -> colors.danger
    remaining < 60 -> colors.warning
    else -> colors.text_primary
}

@Composable
private fun approve_request_card(state: guard_approve_state.ready, now: Long) {
    val colors = AsterMaterial.colors
    val view = state.view
    val os_kind = guard_os_kind_for(view?.os, view?.device_type.orEmpty())
    val on_os = stringResource(R.string.guard_device_browser_on_os)
    val summary = view?.device_summary { b, o -> on_os.format(b, o) }?.takeIf { it.isNotBlank() }
    val device = summary ?: stringResource(R.string.guard_device_unknown)
    val sent_name = (view?.sent_name(device) ?: guard_known_label(state.device_name))
        ?.takeIf { !it.equals(device, ignoreCase = true) }
    val named_format = stringResource(R.string.guard_device_named)
    val place = if (view?.has_place() == true) guard_place_text(view.city, view.region_code() ?: view.country) else ""
    val flag = guard_flag_emoji(view?.region_code())
    val time_iso = view?.requested_at?.takeIf { it.isNotBlank() } ?: state.created_at
    val relative = guard_relative_time(time_iso, now)
    val absolute = time_iso.takeIf { it.isNotBlank() }?.let { guard_format_time(it) }
    guard_card {
        Spacer(Modifier.height(AsterSpacing.sm))
        guard_detail_row(
            tile = { guard_vector_tile(guard_os_icon(os_kind), colors.text_secondary) },
            label = stringResource(R.string.guard_approve_device),
            value = device,
            value_leading = view?.browser?.takeIf { it.isNotBlank() }?.let { browser -> { guard_browser_mark(browser) } },
            supporting = sent_name?.let { named_format.format(it) },
        )
        if (place.isNotBlank()) {
            guard_detail_row(
                tile = { guard_vector_tile(TablerIcons.MapPin, colors.text_secondary) },
                label = stringResource(R.string.guard_approve_place),
                value = place,
                value_leading = flag?.let { emoji -> { Text(text = emoji, fontSize = 16.sp, lineHeight = 20.sp) } },
            )
        }
        val time_value = relative ?: absolute
        if (time_value != null) {
            guard_detail_row(
                tile = { guard_vector_tile(TablerIcons.Clock, colors.text_secondary) },
                label = stringResource(R.string.guard_approve_time),
                value = time_value,
                supporting = if (relative != null) absolute else null,
            )
        }
        Spacer(Modifier.height(AsterSpacing.sm))
    }
}

@Composable
private fun approve_unlock(on_unlock: () -> Unit, on_close: () -> Unit) {
    guard_result_block(
        title = stringResource(R.string.guard_approve_unlock_title),
        body = stringResource(R.string.guard_approve_unlock_first),
        primary_label = stringResource(R.string.guard_approve_unlock),
        on_primary = on_unlock,
        secondary_label = stringResource(R.string.close),
        on_secondary = on_close,
    )
}

internal fun guard_denied_auto_closes(auto_close: Boolean, can_secure: Boolean): Boolean = auto_close && !can_secure

@Composable
private fun approve_result_success(on_close: () -> Unit, auto_close: Boolean) {
    if (auto_close) {
        LaunchedEffect(Unit) {
            delay(GUARD_SHEET_CLOSE_DELAY_MS)
            on_close()
        }
    }
    guard_result_block(
        title = stringResource(R.string.guard_approve_done_title),
        body = stringResource(R.string.guard_approve_done),
        primary_label = stringResource(R.string.done),
        on_primary = on_close,
    )
}

@Composable
private fun approve_result_denied(on_close: () -> Unit, on_secure: (() -> Unit)?, auto_close: Boolean) {
    if (guard_denied_auto_closes(auto_close, on_secure != null)) {
        LaunchedEffect(Unit) {
            delay(GUARD_SHEET_CLOSE_DELAY_MS + 800)
            on_close()
        }
    }
    if (on_secure != null) {
        guard_result_block(
            title = stringResource(R.string.guard_approve_denied_title),
            body = stringResource(R.string.guard_approve_denied),
            primary_label = stringResource(R.string.guard_approve_secure_action),
            on_primary = on_secure,
            secondary_label = stringResource(R.string.done),
            on_secondary = on_close,
        )
    } else {
        guard_result_block(
            title = stringResource(R.string.guard_approve_denied_title),
            body = stringResource(R.string.guard_approve_denied),
            primary_label = stringResource(R.string.done),
            on_primary = on_close,
        )
    }
}

@Composable
private fun approve_result_info(title: String, body: String, on_close: () -> Unit) {
    guard_result_block(
        title = title,
        body = body,
        primary_label = stringResource(R.string.done),
        on_primary = on_close,
    )
}
