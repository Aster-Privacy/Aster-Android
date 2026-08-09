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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CheckboxDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import org.astermail.android.R
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import org.astermail.android.design.AsterMaterial
import org.astermail.android.design.AsterSpacing
import org.astermail.android.design.SquircleShape
import org.astermail.android.design.components.AsterButton
import org.astermail.android.design.components.AsterCard
import org.astermail.android.design.components.AsterDivider
import org.astermail.android.design.components.AsterSecondaryButton
import org.astermail.android.design.components.AsterSwitch
import org.astermail.android.export.ExportViewModel

@Composable
fun ExportScreen(
    on_back: () -> Unit,
    on_open: (id: String) -> Unit = {},
) {
    val vm: ExportViewModel = hiltViewModel()
    val state by vm.state.collectAsStateWithLifecycle()
    val colors = AsterMaterial.colors
    val context = LocalContext.current

    val effective_back: () -> Unit = {
        if (state.is_running) vm.cancel_export() else on_back()
    }

    detail_scaffold(title = stringResource(R.string.export_title_screen), on_back = effective_back) {
        when (state.step) {
            is ExportViewModel.ExportStep.Warning -> {
                warning_step(
                    acknowledged = state.acknowledged,
                    on_acknowledge = { vm.set_acknowledged(it) },
                    on_continue = { vm.proceed_to_scope() },
                )
            }
            is ExportViewModel.ExportStep.Scope -> {
                scope_step(
                    include_mail = state.include_mail,
                    include_contacts = state.include_contacts,
                    on_mail = { vm.set_include_mail(it) },
                    on_contacts = { vm.set_include_contacts(it) },
                    on_continue = { vm.proceed_from_scope() },
                )
            }
            is ExportViewModel.ExportStep.Format -> {
                format_step(
                    format = state.format,
                    on_format = { vm.set_format(it) },
                    on_start = { vm.start_export() },
                )
            }
            is ExportViewModel.ExportStep.Progress -> {
                progress_step(
                    processed = state.processed,
                    total = state.total,
                    bytes_written = state.bytes_written,
                    error = state.error,
                    on_cancel = { vm.cancel_export() },
                )
            }
            is ExportViewModel.ExportStep.Complete -> {
                complete_step(
                    processed = state.processed,
                    bytes_written = state.bytes_written,
                    skipped_undecryptable = state.skipped_undecryptable,
                    error = state.error,
                    on_share = { vm.share_export() },
                    on_done = { vm.reset(); on_back() },
                )
            }
        }
    }
}

@Composable
private fun warning_step(
    acknowledged: Boolean,
    on_acknowledge: (Boolean) -> Unit,
    on_continue: () -> Unit,
) {
    val colors = AsterMaterial.colors
    v_gap(AsterSpacing.md)
    Text(
        text = stringResource(R.string.export_device_decrypt_note),
        color = colors.text_secondary,
        fontSize = 13.sp,
        modifier = Modifier.fillMaxWidth().padding(bottom = AsterSpacing.sm),
    )
    AsterCard(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(AsterSpacing.lg)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = TablerIcons.AlertTriangle,
                    contentDescription = null,
                    tint = Color(0xFFD97706),
                    modifier = Modifier.size(22.dp),
                )
                Spacer(Modifier.width(AsterSpacing.md))
                Column {
                    Text(
                        text = stringResource(R.string.export_warning_title),
                        color = colors.text_primary,
                        fontSize = 15.sp,
                        fontWeight = FontWeight.SemiBold,
                    )
                    Text(
                        text = stringResource(R.string.export_warning_subtitle),
                        color = colors.text_tertiary,
                        fontSize = 12.sp,
                    )
                }
            }
            v_gap(AsterSpacing.md)
            AsterDivider()
            v_gap(AsterSpacing.md)
            Text(
                text = stringResource(R.string.export_warning_message),
                color = colors.text_secondary,
                fontSize = 13.sp,
                lineHeight = 19.sp,
            )
        }
    }
    v_gap(AsterSpacing.lg)
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(SquircleShape(12.dp))
            .clickable { on_acknowledge(!acknowledged) }
            .padding(horizontal = AsterSpacing.sm, vertical = AsterSpacing.xs),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Checkbox(
            checked = acknowledged,
            onCheckedChange = on_acknowledge,
            colors = CheckboxDefaults.colors(checkedColor = colors.accent_blue),
        )
        Spacer(Modifier.width(AsterSpacing.sm))
        Text(
            text = stringResource(R.string.export_acknowledge),
            color = colors.text_primary,
            fontSize = 14.sp,
        )
    }
    v_gap(AsterSpacing.lg)
    AsterButton(
        label = stringResource(R.string.action_continue),
        onClick = on_continue,
        enabled = acknowledged,
        modifier = Modifier.fillMaxWidth(),
    )
    v_gap(AsterSpacing.xxl)
}

@Composable
private fun scope_step(
    include_mail: Boolean,
    include_contacts: Boolean,
    on_mail: (Boolean) -> Unit,
    on_contacts: (Boolean) -> Unit,
    on_continue: () -> Unit,
) {
    val colors = AsterMaterial.colors
    v_gap(AsterSpacing.md)
    section_label(stringResource(R.string.export_scope_section))
    AsterCard(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clickable { on_mail(!include_mail) }
                .padding(horizontal = AsterSpacing.lg, vertical = AsterSpacing.md),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(stringResource(R.string.export_scope_emails), color = colors.text_primary, fontSize = 15.sp, fontWeight = FontWeight.Medium)
                Text(stringResource(R.string.export_scope_emails_subtitle), color = colors.text_tertiary, fontSize = 12.sp)
            }
            AsterSwitch(
                checked = include_mail,
                onCheckedChange = on_mail,
            )
        }
        AsterDivider(modifier = Modifier)
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clickable { on_contacts(!include_contacts) }
                .padding(horizontal = AsterSpacing.lg, vertical = AsterSpacing.md),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(stringResource(R.string.export_scope_contacts), color = colors.text_primary, fontSize = 15.sp, fontWeight = FontWeight.Medium)
                Text(stringResource(R.string.export_scope_contacts_subtitle), color = colors.text_tertiary, fontSize = 12.sp)
            }
            AsterSwitch(
                checked = include_contacts,
                onCheckedChange = on_contacts,
            )
        }
    }
    v_gap(AsterSpacing.lg)
    AsterButton(
        label = stringResource(R.string.action_continue),
        onClick = on_continue,
        enabled = include_mail || include_contacts,
        modifier = Modifier.fillMaxWidth(),
    )
    v_gap(AsterSpacing.xxl)
}

@Composable
private fun format_step(
    format: String,
    on_format: (String) -> Unit,
    on_start: () -> Unit,
) {
    val colors = AsterMaterial.colors
    v_gap(AsterSpacing.md)
    section_label(stringResource(R.string.export_format_section))
    format_card(
        title = stringResource(R.string.export_format_mbox),
        subtitle = stringResource(R.string.export_format_mbox_subtitle),
        selected = format == "mbox",
        on_click = { on_format("mbox") },
    )
    v_gap(AsterSpacing.sm)
    format_card(
        title = stringResource(R.string.export_format_eml),
        subtitle = stringResource(R.string.export_format_eml_subtitle),
        selected = format == "eml",
        on_click = { on_format("eml") },
    )
    v_gap(AsterSpacing.lg)
    AsterButton(
        label = stringResource(R.string.export_start),
        onClick = on_start,
        modifier = Modifier.fillMaxWidth(),
    )
    v_gap(AsterSpacing.xxl)
}

@Composable
private fun format_card(title: String, subtitle: String, selected: Boolean, on_click: () -> Unit) {
    val colors = AsterMaterial.colors
    val bg = if (selected) colors.accent_blue else colors.bg_card
    val border_color = if (selected) colors.accent_blue else colors.border_secondary
    val title_color = if (selected) Color.White else colors.text_primary
    val subtitle_color = if (selected) Color.White.copy(alpha = 0.8f) else colors.text_tertiary
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(SquircleShape(14.dp))
            .background(bg)
            .border(1.dp, border_color, SquircleShape(14.dp))
            .clickable(onClick = on_click)
            .padding(AsterSpacing.lg),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(title, color = title_color, fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
            v_gap(2.dp)
            Text(subtitle, color = subtitle_color, fontSize = 12.sp, lineHeight = 17.sp)
        }
        if (selected) {
            Spacer(Modifier.width(AsterSpacing.md))
            Icon(
                imageVector = TablerIcons.CircleCheck,
                contentDescription = null,
                tint = Color.White,
                modifier = Modifier.size(22.dp),
            )
        }
    }
}

@Composable
private fun progress_step(
    processed: Int,
    total: Int,
    bytes_written: Long,
    error: String?,
    on_cancel: () -> Unit,
) {
    val colors = AsterMaterial.colors
    v_gap(AsterSpacing.xxl)
    if (error != null) {
        Box(modifier = Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
            Text(text = error, color = colors.danger, fontSize = 14.sp, textAlign = TextAlign.Center)
        }
        v_gap(AsterSpacing.lg)
        AsterSecondaryButton(label = stringResource(R.string.back), onClick = on_cancel, modifier = Modifier.fillMaxWidth())
    } else {
        Box(modifier = Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
            CircularProgressIndicator(color = colors.accent_blue, modifier = Modifier.size(52.dp), strokeWidth = 4.dp)
        }
        v_gap(AsterSpacing.xl)
        Text(
            text = stringResource(R.string.export_in_progress),
            color = colors.text_primary,
            fontSize = 17.sp,
            fontWeight = FontWeight.SemiBold,
            textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth(),
        )
        v_gap(AsterSpacing.sm)
        if (total > 0) {
            val fraction = (processed.toFloat() / total).coerceIn(0f, 1f)
            LinearProgressIndicator(
                progress = { fraction },
                modifier = Modifier.fillMaxWidth().height(6.dp).clip(SquircleShape(3.dp)),
                color = colors.accent_blue,
                trackColor = colors.bg_secondary,
            )
            v_gap(AsterSpacing.sm)
            Text(
                text = stringResource(R.string.export_progress_emails, processed, total),
                color = colors.text_tertiary,
                fontSize = 13.sp,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth(),
            )
        }
        if (bytes_written > 0) {
            v_gap(AsterSpacing.xs)
            Text(
                text = format_bytes(bytes_written),
                color = colors.text_muted,
                fontSize = 12.sp,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth(),
            )
        }
        v_gap(AsterSpacing.xxl)
        AsterSecondaryButton(label = stringResource(R.string.cancel), onClick = on_cancel, modifier = Modifier.fillMaxWidth())
    }
    v_gap(AsterSpacing.xxl)
}

@Composable
private fun complete_step(
    processed: Int,
    bytes_written: Long,
    skipped_undecryptable: Int,
    error: String?,
    on_share: () -> Unit,
    on_done: () -> Unit,
) {
    val colors = AsterMaterial.colors
    v_gap(AsterSpacing.xxl)
    Box(modifier = Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
        Icon(
            imageVector = TablerIcons.CircleCheck,
            contentDescription = null,
            tint = colors.success,
            modifier = Modifier.size(52.dp),
        )
    }
    v_gap(AsterSpacing.lg)
    Text(
        text = stringResource(R.string.export_complete),
        color = colors.text_primary,
        fontSize = 20.sp,
        fontWeight = FontWeight.Bold,
        textAlign = TextAlign.Center,
        modifier = Modifier.fillMaxWidth(),
    )
    v_gap(AsterSpacing.md)
    if (error != null) {
        Text(text = error, color = colors.danger, fontSize = 13.sp, textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth())
        v_gap(AsterSpacing.md)
    }
    AsterCard(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(AsterSpacing.lg), verticalArrangement = Arrangement.spacedBy(AsterSpacing.sm)) {
            export_summary_row(stringResource(R.string.export_emails_exported), "${processed - skipped_undecryptable}")
            if (bytes_written > 0) export_summary_row(stringResource(R.string.export_file_size), format_bytes(bytes_written))
        }
    }
    if (skipped_undecryptable > 0) {
        v_gap(AsterSpacing.md)
        Text(
            text = stringResource(R.string.export_undecryptable_skipped, skipped_undecryptable),
            color = colors.warning,
            fontSize = 13.sp,
            textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth(),
        )
    }
    v_gap(AsterSpacing.xl)
    AsterButton(
        label = stringResource(R.string.export_share_save),
        onClick = on_share,
        modifier = Modifier.fillMaxWidth(),
    )
    v_gap(AsterSpacing.sm)
    AsterSecondaryButton(
        label = stringResource(R.string.done),
        onClick = on_done,
        modifier = Modifier.fillMaxWidth(),
    )
    v_gap(AsterSpacing.xxl)
}

@Composable
private fun export_summary_row(label: String, value: String) {
    val colors = AsterMaterial.colors
    Row(modifier = Modifier.fillMaxWidth()) {
        Text(label, color = colors.text_tertiary, fontSize = 13.sp, modifier = Modifier.weight(1f))
        Text(value, color = colors.text_primary, fontSize = 13.sp, fontWeight = FontWeight.Medium)
    }
}

