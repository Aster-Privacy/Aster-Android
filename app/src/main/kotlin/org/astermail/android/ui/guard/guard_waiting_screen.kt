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
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import android.content.Context
import android.content.ContextWrapper
import androidx.fragment.app.FragmentActivity
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import compose.icons.TablerIcons
import compose.icons.tablericons.Clock
import org.astermail.android.R
import org.astermail.android.design.SquircleShape
import org.astermail.android.auth.GuardSignInChallenge
import org.astermail.android.guard.guard_prompt_text
import org.astermail.android.guard.resolve
import org.astermail.android.guard.guard_sign_result
import org.astermail.android.guard.guard_signer
import org.astermail.android.guard.keystore_signer
import org.astermail.android.design.AsterExpandVisibility
import org.astermail.android.design.AsterMaterial
import org.astermail.android.design.AsterSpacing
import org.astermail.android.design.aster_fade_through
import org.astermail.android.design.aster_reduce_motion
import org.astermail.android.design.field_surface_color
import org.astermail.android.design.components.AsterButton
import org.astermail.android.design.components.AsterGhostButton
import org.astermail.android.design.components.AsterTextField
import org.astermail.android.ui.common.page_surface

fun guard_format_duration(seconds: Long): String {
    val s = seconds.coerceAtLeast(0)
    return "%d:%02d".format(s / 60, s % 60)
}

fun guard_format_clock(epoch_ms: Long): String =
    DateTimeFormatter.ofLocalizedTime(FormatStyle.SHORT)
        .withZone(ZoneId.systemDefault())
        .format(Instant.ofEpochMilli(epoch_ms))

fun guard_format_time(iso: String): String =
    runCatching {
        DateTimeFormatter.ofLocalizedDateTime(FormatStyle.MEDIUM, FormatStyle.SHORT)
            .withZone(ZoneId.systemDefault())
            .format(Instant.parse(iso))
    }.getOrDefault(iso)

tailrec fun find_fragment_activity(context: Context): FragmentActivity? = when (context) {
    is FragmentActivity -> context
    is ContextWrapper -> find_fragment_activity(context.baseContext)
    else -> null
}

@Composable
fun guard_sign_in_host(
    view_model: GuardSignInViewModel,
    challenge: GuardSignInChallenge?,
    on_done: () -> Unit,
    on_back: () -> Unit,
) {
    val activity = find_fragment_activity(LocalContext.current)
    val prompt = guard_prompt_text(
        stringResource(R.string.guard_prompt_title),
        stringResource(R.string.guard_prompt_resume_subtitle),
    )
    LaunchedEffect(challenge) {
        if (challenge != null) {
            val signer = activity?.let { keystore_signer(it, prompt) } ?: guard_signer { guard_sign_result.canceled }
            view_model.start(challenge, signer)
        }
    }
    val state by view_model.state.collectAsStateWithLifecycle()
    LaunchedEffect(state) {
        if (state == guard_wait_state.done) on_done()
    }
    guard_waiting_screen(
        state = state,
        on_submit_code = view_model::submit_code,
        on_back = on_back,
        on_rescue = view_model::open_rescue,
        on_submit_rescue = view_model::submit_rescue,
        on_close_rescue = view_model::close_rescue,
        on_retry_resume = view_model::retry_resume,
    )
}

@Composable
fun guard_waiting_screen(
    state: guard_wait_state,
    on_submit_code: (String) -> Unit,
    on_back: () -> Unit,
    on_rescue: (() -> Unit)? = null,
    on_submit_rescue: (String) -> Unit = {},
    on_close_rescue: () -> Unit = {},
    initial_show_code: Boolean = false,
    on_retry_resume: () -> Unit = {},
) {
    org.astermail.android.ui.common.secure_screen()
    val colors = AsterMaterial.colors
    val reduce_motion = aster_reduce_motion()
    var show_code by remember { mutableStateOf(initial_show_code) }
    var code by remember { mutableStateOf("") }
    var rescue_code by remember { mutableStateOf("") }

    BackHandler { if (state is guard_wait_state.rescue_entry) on_close_rescue() else on_back() }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .page_surface(colors),
    ) {
        AnimatedContent(
            targetState = state,
            modifier = Modifier.fillMaxSize(),
            transitionSpec = { aster_fade_through(reduce_motion) },
            contentKey = { if (it == guard_wait_state.done) guard_wait_state.approved::class else it::class },
            label = "guard_wait_state",
        ) { state ->
            when (state) {
                guard_wait_state.starting -> wait_progress(
                    title = stringResource(R.string.guard_wait_title),
                    body = stringResource(R.string.guard_wait_starting),
                )
                guard_wait_state.approved, guard_wait_state.done -> wait_progress(
                    title = stringResource(R.string.guard_wait_approved_title),
                    body = stringResource(R.string.guard_wait_approved),
                )
                is guard_wait_state.waiting -> guard_wizard_scaffold(
                    step = 0,
                    title = stringResource(R.string.guard_wait_title),
                    body = stringResource(R.string.guard_wait_body),
                    on_back = on_back,
                    on_close = null,
                    back_closes = true,
                    centered = true,
                    bottom = {
                        if (show_code) {
                            AsterButton(
                                label = stringResource(R.string.guard_wait_code_submit),
                                onClick = { on_submit_code(code) },
                                enabled = code.isNotBlank(),
                                modifier = Modifier.fillMaxWidth().testTag("guard_wait_code_submit"),
                            )
                        } else {
                            AsterGhostButton(
                                label = stringResource(R.string.guard_wait_code_action),
                                onClick = { show_code = true },
                                modifier = Modifier.fillMaxWidth().testTag("guard_wait_code_action"),
                            )
                        }
                        if (on_rescue != null && guard_wait_offers_rescue(show_code, state.notice)) {
                            AsterGhostButton(
                                label = stringResource(R.string.guard_wait_rescue_action),
                                onClick = on_rescue,
                                modifier = Modifier.fillMaxWidth().testTag("guard_wait_rescue_action"),
                            )
                        }
                    },
                ) {
                    Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
                        guard_number_tiles(state.number, stringResource(R.string.guard_number_description, state.number))
                        Spacer(Modifier.height(AsterSpacing.lg))
                        guard_chip(
                            text = if (state.expires_at_ms > 0L) {
                                stringResource(
                                    R.string.guard_wait_expires_at,
                                    guard_format_duration(state.seconds_left),
                                    guard_format_clock(state.expires_at_ms),
                                )
                            } else {
                                stringResource(R.string.guard_wait_expires_in, guard_format_duration(state.seconds_left))
                            },
                            icon = TablerIcons.Clock,
                            color = guard_expiry_tone(colors, state.seconds_left),
                        )
                    }
                    AsterExpandVisibility(visible = show_code) {
                        Column(modifier = Modifier.fillMaxWidth()) {
                            Spacer(Modifier.height(AsterSpacing.xxl))
                            AsterTextField(
                                value = code,
                                onValueChange = { code = it.take(MAX_CODE_INPUT) },
                                label = stringResource(R.string.guard_wait_code_label),
                                error_text = when (val n = state.notice) {
                                    guard_wait_notice.wrong_code -> stringResource(R.string.guard_wait_wrong_code)
                                    is guard_wait_notice.retry -> stringResource(
                                        R.string.guard_wait_retry,
                                        guard_format_duration(n.retry_after_secs),
                                    )
                                    is guard_wait_notice.error -> n.message.resolve()
                                    null -> null
                                },
                                keyboard_options = KeyboardOptions(capitalization = KeyboardCapitalization.Characters, autoCorrectEnabled = false, keyboardType = KeyboardType.Ascii),
                                text_style = guard_code_text_style,
                                modifier = Modifier.fillMaxWidth().testTag("guard_wait_code_field"),
                            )
                        }
                    }
                }
                is guard_wait_state.rescue_entry -> guard_wizard_scaffold(
                    step = 0,
                    title = stringResource(R.string.guard_wait_rescue_action),
                    body = stringResource(R.string.guard_rescue_hint),
                    on_back = if (state.busy) null else on_close_rescue,
                    on_close = null,
                    bottom = {
                        AsterButton(
                            label = stringResource(R.string.guard_rescue_submit),
                            onClick = { on_submit_rescue(rescue_code) },
                            enabled = rescue_code.isNotBlank() && !state.busy,
                            is_loading = state.busy,
                            modifier = Modifier.fillMaxWidth().testTag("guard_rescue_submit"),
                        )
                        AsterGhostButton(
                            label = stringResource(R.string.cancel),
                            onClick = on_close_rescue,
                            enabled = !state.busy,
                            modifier = Modifier.fillMaxWidth(),
                        )
                    },
                ) {
                    AsterTextField(
                        value = rescue_code,
                        onValueChange = { rescue_code = it.take(MAX_RESCUE_INPUT) },
                        label = stringResource(R.string.guard_rescue_label),
                        error_text = when (val n = state.notice) {
                            guard_rescue_notice.invalid -> stringResource(R.string.guard_rescue_invalid)
                            is guard_rescue_notice.retry -> stringResource(
                                R.string.guard_wait_retry,
                                guard_format_duration(n.retry_after_secs),
                            )
                            is guard_rescue_notice.error -> n.message.resolve()
                            null -> null
                        },
                        keyboard_options = KeyboardOptions(capitalization = KeyboardCapitalization.Characters, autoCorrectEnabled = false, keyboardType = KeyboardType.Ascii),
                        text_style = guard_code_text_style,
                        modifier = Modifier.fillMaxWidth().testTag("guard_rescue_field"),
                    )
                }
                else -> {
                    val result = guard_wait_result_for(state)
                    guard_wizard_result {
                        guard_result_block(
                            title = stringResource(result.title),
                            body = when (state) {
                                is guard_wait_state.retry -> stringResource(result.body, guard_format_duration(state.retry_after_secs))
                                is guard_wait_state.held -> stringResource(result.body, guard_format_time(state.apply_after))
                                is guard_wait_state.rescue_waiting -> stringResource(result.body, guard_format_time(state.apply_after))
                                is guard_wait_state.paused -> stringResource(
                                    result.body,
                                    org.astermail.android.guard.guard_format_instant(Instant.ofEpochMilli(state.ends_at_ms)),
                                )
                                is guard_wait_state.error -> state.message.resolve()
                                else -> stringResource(result.body)
                            },
                            primary_label = stringResource(result.action),
                            on_primary = if (state is guard_wait_state.resume_blocked) on_retry_resume else on_back,
                            secondary_label = if (state is guard_wait_state.resume_blocked) stringResource(R.string.guard_wait_back) else null,
                            on_secondary = if (state is guard_wait_state.resume_blocked) on_back else null,
                        )
                    }
                }
            }
        }
    }
}

internal data class guard_wait_result(val title: Int, val body: Int, val action: Int = R.string.guard_wait_back)

internal fun guard_wait_offers_rescue(code_open: Boolean, notice: guard_wait_notice?): Boolean = code_open || notice != null

internal fun guard_wait_result_for(state: guard_wait_state): guard_wait_result = when (state) {
    is guard_wait_state.retry -> guard_wait_result(R.string.guard_wait_retry_title, R.string.guard_wait_retry)
    is guard_wait_state.paused -> guard_wait_result(R.string.guard_wait_paused_title, R.string.guard_wait_paused)
    guard_wait_state.denied -> guard_wait_result(R.string.guard_wait_denied_title, R.string.guard_wait_denied)
    guard_wait_state.expired -> guard_wait_result(
        R.string.guard_wait_expired_title,
        R.string.guard_wait_expired,
        R.string.guard_wait_sign_in_again,
    )
    is guard_wait_state.error -> guard_wait_result(
        R.string.guard_wait_failed_title,
        R.string.guard_wait_failed,
        R.string.guard_wait_sign_in_again,
    )
    is guard_wait_state.held -> guard_wait_result(R.string.guard_wait_held_title, R.string.guard_wait_held)
    guard_wait_state.needs_password -> guard_wait_result(R.string.guard_wait_needs_password_title, R.string.guard_wait_needs_password)
    is guard_wait_state.rescue_waiting -> guard_wait_result(R.string.guard_rescue_waiting_title, R.string.guard_rescue_started)
    guard_wait_state.rescue_applied -> guard_wait_result(
        R.string.guard_rescue_applied_title,
        R.string.guard_rescue_applied_body,
        R.string.guard_rescue_sign_in,
    )
    guard_wait_state.rescue_stopped -> guard_wait_result(R.string.guard_rescue_stopped_title, R.string.guard_rescue_stopped_body)
    is guard_wait_state.resume_blocked -> when (state.reason) {
        guard_resume_block.unlock -> guard_wait_result(
            R.string.guard_wait_resume_unlock_title,
            R.string.guard_wait_resume_unlock,
            R.string.guard_wait_resume_retry,
        )
        guard_resume_block.network -> guard_wait_result(
            R.string.guard_wait_resume_network_title,
            R.string.guard_wait_resume_network,
            R.string.guard_wait_resume_retry,
        )
    }
    else -> guard_wait_result(R.string.guard_wait_failed_title, R.string.guard_wait_failed, R.string.guard_wait_sign_in_again)
}

internal fun guard_wait_title_for(state: guard_wait_state): Int = when (state) {
    guard_wait_state.starting, is guard_wait_state.waiting -> R.string.guard_wait_title
    guard_wait_state.approved, guard_wait_state.done -> R.string.guard_wait_approved_title
    is guard_wait_state.rescue_entry -> R.string.guard_wait_rescue_action
    else -> guard_wait_result_for(state).title
}

@Composable
private fun wait_progress(title: String, body: String) {
    guard_wizard_result {
        guard_wordmark()
        Spacer(Modifier.height(AsterSpacing.xxl + AsterSpacing.sm))
        guard_wizard_title(title, Modifier.fillMaxWidth(), TextAlign.Center)
        Spacer(Modifier.height(AsterSpacing.sm))
        guard_wizard_body(body, Modifier.fillMaxWidth(), TextAlign.Center)
        Spacer(Modifier.height(AsterSpacing.xxl))
        org.astermail.android.design.components.aster_spinner(color = AsterMaterial.colors.text_secondary, modifier = Modifier.size(28.dp))
    }
}

@Composable
internal fun guard_number_tiles(number: Int, description: String) {
    val colors = AsterMaterial.colors
    val shape = SquircleShape(18.dp)
    Row(
        modifier = Modifier.clearAndSetSemantics { contentDescription = description },
        horizontalArrangement = Arrangement.spacedBy(AsterSpacing.md),
    ) {
        "%02d".format(number.coerceIn(0, 99)).forEach { digit ->
            Box(
                modifier = Modifier
                    .size(width = 72.dp, height = 84.dp)
                    .background(field_surface_color(colors), shape)
                    .border(2.dp, colors.accent_blue, shape),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = digit.toString(),
                    color = colors.text_primary,
                    fontSize = 40.sp,
                    fontFamily = FontFamily.Monospace,
                    fontWeight = FontWeight.Bold,
                )
            }
        }
    }
}

private const val MAX_CODE_INPUT = 16
private const val MAX_RESCUE_INPUT = 40
