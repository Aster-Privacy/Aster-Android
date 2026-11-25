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

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Icon
import compose.icons.tablericons.InfoCircle
import androidx.compose.ui.Alignment
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import compose.icons.TablerIcons
import compose.icons.tablericons.Clock
import compose.icons.tablericons.DeviceMobile
import compose.icons.tablericons.Logout
import org.astermail.android.R
import org.astermail.android.api.guard.GuardPanicResult
import org.astermail.android.design.AsterMaterial
import org.astermail.android.design.AsterSpacing
import org.astermail.android.design.components.AsterButton
import org.astermail.android.design.components.AsterDestructiveButton
import org.astermail.android.guard.guard_prompt_text
import org.astermail.android.ui.settings.detail.detail_scaffold
import org.astermail.android.ui.settings.detail.error_banner

@Composable
fun guard_panic_host(
    view_model: GuardPanicViewModel,
    on_back: () -> Unit,
    on_change_password: () -> Unit,
) {
    val state by view_model.state.collectAsState()
    val activity = find_fragment_activity(LocalContext.current)
    val prompt = guard_prompt_text(
        title = stringResource(R.string.guard_prompt_title),
        subtitle = stringResource(R.string.guard_panic_prompt_subtitle),
    )
    guard_panic_screen(
        state = state,
        on_back = on_back,
        on_confirm = { view_model.confirm(guard_prompt_signer(activity, prompt)) },
        on_retry = view_model::retry,
        on_change_password = on_change_password,
    )
}

@Composable
fun guard_panic_screen(
    state: guard_panic_state,
    on_back: () -> Unit,
    on_confirm: () -> Unit,
    on_retry: () -> Unit,
    on_change_password: () -> Unit,
) {
    detail_scaffold(title = "", on_back = on_back) {
        when (state) {
            is guard_panic_state.confirm -> guard_panic_confirm(state.notice, working = false, on_confirm = on_confirm)
            guard_panic_state.working -> guard_panic_confirm(null, working = true, on_confirm = on_confirm)
            is guard_panic_state.done -> guard_panic_done(state, on_back, on_change_password)
            is guard_panic_state.failed -> {
                guard_notice_text(state.notice, state.error)?.let { error_banner(it) }
                Spacer(Modifier.height(AsterSpacing.md))
                AsterButton(
                    label = stringResource(R.string.guard_panic_try_again),
                    onClick = on_retry,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        }
    }
}

@Composable
private fun guard_panic_confirm(notice: guard_action_notice?, working: Boolean, on_confirm: () -> Unit) {
    val colors = AsterMaterial.colors
    guard_notice_text(notice)?.let {
        error_banner(it)
        Spacer(Modifier.height(AsterSpacing.md))
    }
    guard_page_hero(
        title = stringResource(R.string.guard_panic_title),
        info = stringResource(R.string.guard_panic_body),
    )
    guard_card {
        Column(modifier = Modifier.padding(vertical = AsterSpacing.sm)) {
            guard_panic_line(TablerIcons.Logout, stringResource(R.string.guard_panic_step_sessions))
            guard_panic_line(TablerIcons.DeviceMobile, stringResource(R.string.guard_panic_step_devices))
            guard_panic_line(TablerIcons.Clock, stringResource(R.string.guard_panic_step_changes))
        }
    }
    Spacer(Modifier.height(AsterSpacing.md))
    guard_panic_caption(TablerIcons.InfoCircle, stringResource(R.string.guard_panic_stay))
    Spacer(Modifier.height(AsterSpacing.xl))
    AsterDestructiveButton(
        label = stringResource(R.string.guard_panic_confirm),
        onClick = on_confirm,
        modifier = Modifier.fillMaxWidth(),
        is_loading = working,
    )
}

@Composable
private fun guard_panic_line(icon: ImageVector, text: String) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 56.dp)
            .padding(horizontal = AsterSpacing.lg, vertical = AsterSpacing.sm),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(modifier = Modifier.width(36.dp), contentAlignment = Alignment.Center) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = AsterMaterial.colors.text_secondary,
                modifier = Modifier.size(22.dp),
            )
        }
        Spacer(Modifier.width(AsterSpacing.md))
        Text(
            text = text,
            color = AsterMaterial.colors.text_primary,
            fontSize = 15.sp,
            lineHeight = 20.sp,
            fontWeight = FontWeight.Medium,
            modifier = Modifier.weight(1f),
        )
    }
}

@Composable
private fun guard_panic_caption(icon: ImageVector, text: String) {
    val colors = AsterMaterial.colors
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = AsterSpacing.lg),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(modifier = Modifier.width(36.dp), contentAlignment = Alignment.Center) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = colors.text_tertiary,
                modifier = Modifier.size(16.dp),
            )
        }
        Spacer(Modifier.width(AsterSpacing.md))
        Text(
            text = text,
            color = colors.text_secondary,
            fontSize = 13.sp,
            lineHeight = 18.sp,
            modifier = Modifier.weight(1f),
        )
    }
}

@Composable
private fun guard_panic_done(state: guard_panic_state.done, on_back: () -> Unit, on_change_password: () -> Unit) {
    val result: GuardPanicResult = state.result
    val lines = listOf(
        pluralStringResource(R.plurals.guard_panic_done_sessions, result.signed_out_sessions, result.signed_out_sessions),
        pluralStringResource(R.plurals.guard_panic_done_devices, result.removed_devices, result.removed_devices),
        pluralStringResource(R.plurals.guard_panic_done_changes, result.cancelled_changes, result.cancelled_changes),
    )
    Spacer(Modifier.height(AsterSpacing.md))
    if (state.offers_password_change) {
        guard_result_block(
            title = stringResource(R.string.guard_panic_done_title),
            body = stringResource(R.string.guard_panic_password_hint),
            extra_lines = lines,
            primary_label = stringResource(R.string.guard_panic_change_password),
            on_primary = on_change_password,
            secondary_label = stringResource(R.string.guard_panic_close),
            on_secondary = on_back,
        )
    } else {
        guard_result_block(
            title = stringResource(R.string.guard_panic_done_title),
            body = null,
            extra_lines = lines,
            primary_label = stringResource(R.string.guard_panic_close),
            on_primary = on_back,
        )
    }
}
