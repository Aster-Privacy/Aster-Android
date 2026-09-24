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

package org.astermail.android.ui.settings.detail.bimi

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.Dns
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.Schedule
import androidx.compose.material.icons.rounded.VerifiedUser
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import org.astermail.android.R
import org.astermail.android.api.domains.BimiState
import org.astermail.android.design.AsterMaterial
import org.astermail.android.design.AsterSpacing
import org.astermail.android.design.SquircleShape
import org.astermail.android.design.components.AsterAlertDialog
import org.astermail.android.design.components.AsterDestructiveButton
import org.astermail.android.design.components.DialogConfirmStyle
import org.astermail.android.settings.BimiErrorKind
import org.astermail.android.settings.BimiUiState
import org.astermail.android.settings.BimiViewModel
import org.astermail.android.ui.settings.detail.absolute_date_time_label
import org.astermail.android.ui.settings.detail.v_gap

internal fun bimi_row_message_res(state: BimiState, managed_dns: Boolean): Int = when (state) {
    BimiState.draft -> R.string.domain_bimi_row_draft
    BimiState.pending -> if (managed_dns) R.string.domain_bimi_row_pending_managed else R.string.domain_bimi_row_pending
    BimiState.live -> R.string.domain_bimi_row_live
    BimiState.attention -> R.string.domain_bimi_row_attention
    BimiState.external -> R.string.domain_bimi_row_external
    BimiState.off -> R.string.domain_bimi_row_off
}

@Composable
internal fun bimi_manage_view(state: BimiUiState, domain_name: String, vm: BimiViewModel) {
    val colors = AsterMaterial.colors
    val view = state.view ?: return
    val inactive = !view.domain_active

    if (state.confirm_turn_off) {
        AsterAlertDialog(
            on_dismiss = vm::dismiss_turn_off,
            title = stringResource(R.string.domain_bimi_turn_off_title),
            message = stringResource(R.string.domain_bimi_turn_off_body),
            confirm_label = stringResource(R.string.domain_bimi_turn_off_confirm),
            cancel_label = stringResource(R.string.cancel),
            confirm_style = DialogConfirmStyle.destructive,
            is_busy = state.turning_off,
            on_confirm = vm::turn_off,
        )
    }

    bimi_manage_header(state = state, domain_name = domain_name)
    v_gap(AsterSpacing.md)
    Text(
        text = stringResource(bimi_row_message_res(state.bimi_state, view.managed_dns)),
        color = colors.text_secondary,
        fontSize = 14.sp,
        lineHeight = 20.sp,
    )

    if (inactive) {
        v_gap(AsterSpacing.md)
        bimi_alert(tone = BimiAlertTone.warning, message = stringResource(R.string.domain_bimi_error_domain_not_active))
    }
    val dmarc = view.known_dmarc_status
    if (dmarc != null && dmarc != "ready") {
        bimi_dmarc_res(dmarc)?.let { res ->
            v_gap(AsterSpacing.md)
            bimi_alert(tone = BimiAlertTone.warning, message = stringResource(res))
        }
    }
    val error = state.error?.takeUnless { inactive && it == BimiErrorKind.domain_not_active }
    if (error != null) {
        v_gap(AsterSpacing.md)
        bimi_alert(tone = BimiAlertTone.error, message = bimi_error_text(error))
    }

    v_gap(AsterSpacing.xl)
    val record = view.record
    if (view.managed_dns) {
        bimi_note(icon = Icons.Rounded.Dns, text = stringResource(R.string.domain_bimi_managed_note))
    } else if (record != null) {
        bimi_section_title(stringResource(R.string.domain_bimi_record_title))
        v_gap(AsterSpacing.sm)
        bimi_record_rows(record)
        v_gap(AsterSpacing.md)
        bimi_record_status(state)
    }

    if (view.preview_png != null) {
        v_gap(AsterSpacing.xl)
        bimi_inbox_preview(domain_name = domain_name, png = view.preview_png)
    }

    if (state.adjustments.isNotEmpty()) {
        v_gap(AsterSpacing.lg)
        bimi_adjustments_row(state.adjustments)
    }

    v_gap(AsterSpacing.lg)
    bimi_note(icon = Icons.Rounded.VerifiedUser, text = stringResource(R.string.domain_bimi_verified_mark_note))

    v_gap(AsterSpacing.xl)
    Box(Modifier.fillMaxWidth().height(1.dp).background(colors.border_secondary))
    v_gap(AsterSpacing.lg)
    bimi_section_title(stringResource(R.string.domain_bimi_turn_off))
    v_gap(2.dp)
    Text(
        text = stringResource(R.string.domain_bimi_turn_off_description),
        color = colors.text_secondary,
        fontSize = 13.sp,
        lineHeight = 18.sp,
    )
    v_gap(AsterSpacing.md)
    AsterDestructiveButton(
        label = stringResource(R.string.domain_bimi_turn_off),
        onClick = vm::request_turn_off,
        enabled = !state.busy,
        is_loading = state.turning_off,
    )
}

@Composable
private fun bimi_record_status(state: BimiUiState) {
    val view = state.view ?: return
    when (val status = view.known_record_status) {
        "published" -> bimi_note(
            icon = Icons.Rounded.CheckCircle,
            text = stringResource(R.string.domain_bimi_record_published),
            success = true,
        )
        "conflict", "external" -> bimi_alert(
            tone = BimiAlertTone.warning,
            message = stringResource(bimi_record_status_res(status) ?: R.string.domain_bimi_record_conflict),
        )
        else -> if (state.bimi_state == BimiState.attention) {
            bimi_alert(tone = BimiAlertTone.warning, message = stringResource(R.string.domain_bimi_record_removed))
        } else {
            bimi_note(icon = Icons.Rounded.Schedule, text = stringResource(R.string.domain_bimi_record_missing))
        }
    }
    if (state.auto_checking && view.known_record_status != "published") {
        v_gap(AsterSpacing.sm)
        bimi_note(icon = Icons.Rounded.Refresh, text = stringResource(R.string.domain_bimi_auto_checking))
    }
}

@Composable
private fun bimi_manage_header(state: BimiUiState, domain_name: String) {
    val colors = AsterMaterial.colors
    val shape = SquircleShape(12.dp)
    val checked_label = absolute_date_time_label(state.view?.last_checked_at)
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .border(1.dp, colors.border_secondary, shape)
            .padding(AsterSpacing.md),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        bimi_logo_image(
            png = state.view?.preview_png,
            size = 44.dp,
            content_description = stringResource(R.string.domain_bimi_preview_alt),
        )
        Spacer(Modifier.width(AsterSpacing.md))
        Column(modifier = Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = domain_name,
                    color = colors.text_primary,
                    fontSize = 15.sp,
                    lineHeight = 20.sp,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f, fill = false),
                )
                Spacer(Modifier.width(AsterSpacing.sm))
                bimi_state_chip(state.bimi_state)
            }
            if (checked_label.isNotEmpty()) {
                v_gap(2.dp)
                Row(verticalAlignment = Alignment.Top) {
                    bimi_first_line_icon(Icons.Rounded.Schedule, colors.text_tertiary, 14.dp, 16.sp)
                    Spacer(Modifier.width(AsterSpacing.xs))
                    Text(
                        text = stringResource(R.string.domain_bimi_last_checked, checked_label),
                        color = colors.text_tertiary,
                        fontSize = 12.sp,
                        lineHeight = 16.sp,
                    )
                }
            }
        }
    }
}
