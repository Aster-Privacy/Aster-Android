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

package org.astermail.android.ui.settings.mail_rules.pickers

import compose.icons.TablerIcons
import compose.icons.tablericons.*

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.annotation.StringRes
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import org.astermail.android.R
import org.astermail.android.design.SquircleShape
import org.astermail.android.design.AsterMaterial
import org.astermail.android.design.AsterSpacing
import org.astermail.android.design.components.AsterButton
import org.astermail.android.design.components.AsterTextField
import org.astermail.android.ui.settings.mail_rules.action_id
import org.astermail.android.ui.settings.mail_rules.field_id
import org.astermail.android.ui.settings.mail_rules.regex_error_res
import kotlin.math.roundToInt

data class picker_section_spec(
    @StringRes val title_res: Int,
    val items: List<picker_item_spec>,
)

data class picker_item_spec(
    val id: String,
    @StringRes val label_res: Int,
)

data class picker_item(
    val id: String,
    val label: String,
    val sublabel: String? = null,
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun base_sheet(
    on_dismiss: () -> Unit,
    title: String,
    content: @Composable () -> Unit,
) {
    val colors = AsterMaterial.colors
    val sheet_state = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    ModalBottomSheet(
        onDismissRequest = on_dismiss,
        sheetState = sheet_state,
        containerColor = colors.bg_primary,
        dragHandle = null,
        modifier = Modifier.testTag("mr_sheet"),
    ) {
        Column(modifier = Modifier.padding(bottom = AsterSpacing.lg)) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(AsterSpacing.lg),
                contentAlignment = Alignment.CenterStart,
            ) {
                Text(
                    text = title,
                    fontSize = 17.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = colors.text_primary,
                )
            }
            content()
        }
    }
}

@Composable
fun row_select(
    label: String,
    sublabel: String? = null,
    selected: Boolean,
    on_click: () -> Unit,
    test_tag: String? = null,
) {
    val colors = AsterMaterial.colors
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = on_click)
            .then(if (test_tag != null) Modifier.testTag(test_tag) else Modifier)
            .padding(horizontal = AsterSpacing.lg, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = label,
                color = colors.text_primary,
                fontSize = 15.sp,
                fontWeight = FontWeight.Medium,
            )
            if (sublabel != null) {
                Text(text = sublabel, color = colors.text_tertiary, fontSize = 12.sp)
            }
        }
        AnimatedVisibility(
            visible = selected,
            enter = fadeIn(tween(150)) + scaleIn(initialScale = 0.6f, animationSpec = tween(150)),
            exit = fadeOut(tween(100)) + scaleOut(targetScale = 0.6f, animationSpec = tween(100)),
        ) {
            Icon(
                imageVector = TablerIcons.Check,
                contentDescription = null,
                tint = colors.accent_blue,
                modifier = Modifier.size(20.dp),
            )
        }
    }
}

@Composable
fun field_picker(
    on_dismiss: () -> Unit,
    on_pick: (field_id) -> Unit,
) {
    val sections = remember {
        listOf(
            picker_section_spec(
                R.string.rules_section_sender,
                listOf(
                    picker_item_spec(field_id.from.name, R.string.mail_rules_field_from),
                    picker_item_spec(field_id.reply_to.name, R.string.mail_rules_field_reply_to),
                ),
            ),
            picker_section_spec(
                R.string.rules_section_recipients,
                listOf(
                    picker_item_spec(field_id.to.name, R.string.mail_rules_field_to),
                    picker_item_spec(field_id.cc.name, R.string.mail_rules_field_cc),
                    picker_item_spec(field_id.bcc.name, R.string.mail_rules_field_bcc),
                    picker_item_spec(field_id.any_recipient.name, R.string.mail_rules_field_any_recipient),
                ),
            ),
            picker_section_spec(
                R.string.rules_section_content,
                listOf(
                    picker_item_spec(field_id.subject.name, R.string.mail_rules_field_subject),
                    picker_item_spec(field_id.body.name, R.string.mail_rules_field_body),
                    picker_item_spec(field_id.header.name, R.string.mail_rules_field_header),
                    picker_item_spec(field_id.list_id.name, R.string.mail_rules_field_list_id),
                ),
            ),
            picker_section_spec(
                R.string.rules_section_attachments_flags,
                listOf(
                    picker_item_spec(field_id.has_attachment.name, R.string.mail_rules_field_has_attachment),
                    picker_item_spec(field_id.attachment_name.name, R.string.mail_rules_field_attachment_name),
                    picker_item_spec(field_id.attachment_size.name, R.string.mail_rules_field_attachment_size),
                    picker_item_spec(field_id.is_reply.name, R.string.mail_rules_field_is_reply),
                    picker_item_spec(field_id.is_forward.name, R.string.mail_rules_field_is_forward),
                    picker_item_spec(field_id.is_auto_submitted.name, R.string.mail_rules_field_is_auto_submitted),
                    picker_item_spec(field_id.has_calendar_invite.name, R.string.mail_rules_field_has_calendar_invite),
                    picker_item_spec(field_id.has_list_id.name, R.string.mail_rules_field_has_list_id),
                ),
            ),
            picker_section_spec(
                R.string.rules_section_numeric_date_auth,
                listOf(
                    picker_item_spec(field_id.recipient_count.name, R.string.mail_rules_field_recipient_count),
                    picker_item_spec(field_id.total_size.name, R.string.mail_rules_field_total_size),
                    picker_item_spec(field_id.spam_score.name, R.string.mail_rules_field_spam_score),
                    picker_item_spec(field_id.date_received.name, R.string.mail_rules_field_date_received),
                    picker_item_spec(field_id.dkim_result.name, R.string.mail_rules_field_dkim),
                    picker_item_spec(field_id.spf_result.name, R.string.mail_rules_field_spf),
                    picker_item_spec(field_id.dmarc_result.name, R.string.mail_rules_field_dmarc),
                ),
            ),
        )
    }
    base_sheet(on_dismiss = on_dismiss, title = stringResource(R.string.mail_rules_pick_field)) {
        Column(modifier = Modifier.heightIn(max = 560.dp).verticalScroll(rememberScrollState())) {
            sections.forEach { section ->
                section_header(stringResource(section.title_res))
                section.items.forEach { item ->
                    row_select(
                        label = stringResource(item.label_res),
                        selected = false,
                        on_click = { on_pick(field_id.valueOf(item.id)); on_dismiss() },
                        test_tag = "field_${item.id}",
                    )
                }
            }
        }
    }
}

@Composable
fun section_header(text: String) {
    val colors = AsterMaterial.colors
    Text(
        text = text.uppercase(),
        color = colors.text_tertiary,
        fontSize = 11.sp,
        fontWeight = FontWeight.SemiBold,
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = AsterSpacing.lg, top = AsterSpacing.md, bottom = 4.dp),
    )
}

@Composable
fun options_picker(
    on_dismiss: () -> Unit,
    title: String,
    items: List<picker_item>,
    selected_id: String?,
    on_pick: (String) -> Unit,
) {
    base_sheet(on_dismiss = on_dismiss, title = title) {
        Column(modifier = Modifier.heightIn(max = 480.dp).verticalScroll(rememberScrollState())) {
            items.forEach { item ->
                row_select(
                    label = item.label,
                    sublabel = item.sublabel,
                    selected = item.id == selected_id,
                    on_click = { on_pick(item.id); on_dismiss() },
                    test_tag = "opt_${item.id}",
                )
            }
        }
    }
}

private val snooze_presets: List<Pair<Int, java.time.Duration>> = listOf(
    R.string.rules_snooze_1_hour to java.time.Duration.ofHours(1),
    R.string.rules_snooze_1_day to java.time.Duration.ofDays(1),
    R.string.rules_snooze_3_days to java.time.Duration.ofDays(3),
    R.string.rules_snooze_1_week to java.time.Duration.ofDays(7),
)

@Composable
fun snooze_picker(
    on_dismiss: () -> Unit,
    title: String,
    current: String,
    on_pick: (String) -> Unit,
) {
    val current_label = runCatching {
        java.time.OffsetDateTime.parse(current)
            .atZoneSameInstant(java.time.ZoneId.systemDefault())
            .format(java.time.format.DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm"))
    }.getOrNull()

    base_sheet(on_dismiss = on_dismiss, title = title) {
        Column(modifier = Modifier.heightIn(max = 480.dp).verticalScroll(rememberScrollState())) {
            snooze_presets.forEach { (label_res, duration) ->
                row_select(
                    label = stringResource(label_res),
                    sublabel = null,
                    selected = false,
                    on_click = {
                        on_pick(
                            java.time.OffsetDateTime.now(java.time.ZoneOffset.UTC)
                                .plus(duration)
                                .format(java.time.format.DateTimeFormatter.ISO_OFFSET_DATE_TIME),
                        )
                        on_dismiss()
                    },
                    test_tag = "snooze_${duration.toHours()}",
                )
            }
            if (current_label != null) {
                section_header(stringResource(R.string.rules_snooze_current, current_label))
            }
        }
    }
}

@Composable
fun text_value_picker(
    on_dismiss: () -> Unit,
    title: String,
    initial: String,
    case_sensitive: Boolean,
    show_case_toggle: Boolean,
    is_regex: Boolean = false,
    on_confirm: (String, Boolean) -> Unit,
) {
    var value by remember { mutableStateOf(initial) }
    var case by remember { mutableStateOf(case_sensitive) }
    val colors = AsterMaterial.colors
    val regex_error = if (is_regex) regex_error_res(value) else null
    base_sheet(on_dismiss = on_dismiss, title = title) {
        Column(modifier = Modifier.padding(horizontal = AsterSpacing.lg)) {
            AsterTextField(
                value = value,
                onValueChange = { value = it },
                placeholder = stringResource(R.string.mail_rules_value_placeholder),
                singleLine = false,
                modifier = Modifier.testTag("value_input"),
            )
            regex_error?.let {
                Spacer(Modifier.height(AsterSpacing.sm))
                Text(
                    text = stringResource(it),
                    color = colors.danger,
                    fontSize = 12.sp,
                    modifier = Modifier.testTag("regex_error"),
                )
            }
            if (show_case_toggle) {
                Spacer(Modifier.height(AsterSpacing.md))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = stringResource(R.string.mail_rules_match_case),
                        modifier = Modifier.weight(1f),
                        color = colors.text_primary,
                        fontSize = 14.sp,
                    )
                    Switch(checked = case, onCheckedChange = { case = it })
                }
            }
            Spacer(Modifier.height(AsterSpacing.lg))
            AsterButton(
                label = stringResource(R.string.mail_rules_confirm),
                onClick = {
                    on_confirm(value, case)
                    on_dismiss()
                },
                enabled = regex_error == null,
                modifier = Modifier.testTag("confirm_value"),
            )
            Spacer(Modifier.height(AsterSpacing.md))
        }
    }
}

@Composable
fun header_value_picker(
    on_dismiss: () -> Unit,
    initial_name: String,
    initial_value: String,
    case_sensitive: Boolean,
    is_regex: Boolean = false,
    on_confirm: (String, String, Boolean) -> Unit,
) {
    var name by remember { mutableStateOf(initial_name) }
    var value by remember { mutableStateOf(initial_value) }
    var case by remember { mutableStateOf(case_sensitive) }
    val colors = AsterMaterial.colors
    val regex_error = if (is_regex) regex_error_res(value) else null
    base_sheet(on_dismiss = on_dismiss, title = stringResource(R.string.mail_rules_header_value)) {
        Column(modifier = Modifier.padding(horizontal = AsterSpacing.lg)) {
            AsterTextField(
                value = name,
                onValueChange = { name = it },
                label = stringResource(R.string.mail_rules_header_name),
                placeholder = "X-My-Header",
            )
            Spacer(Modifier.height(AsterSpacing.md))
            AsterTextField(
                value = value,
                onValueChange = { value = it },
                label = stringResource(R.string.mail_rules_value),
                placeholder = stringResource(R.string.mail_rules_value_placeholder),
            )
            regex_error?.let {
                Spacer(Modifier.height(AsterSpacing.sm))
                Text(
                    text = stringResource(it),
                    color = colors.danger,
                    fontSize = 12.sp,
                    modifier = Modifier.testTag("regex_error"),
                )
            }
            Spacer(Modifier.height(AsterSpacing.md))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = stringResource(R.string.mail_rules_match_case),
                    modifier = Modifier.weight(1f),
                    color = colors.text_primary,
                    fontSize = 14.sp,
                )
                Switch(checked = case, onCheckedChange = { case = it })
            }
            Spacer(Modifier.height(AsterSpacing.lg))
            AsterButton(
                label = stringResource(R.string.mail_rules_confirm),
                onClick = { on_confirm(name, value, case); on_dismiss() },
                enabled = regex_error == null,
            )
            Spacer(Modifier.height(AsterSpacing.md))
        }
    }
}

private fun single_decimal(v: String): String {
    val filtered = v.filter { it.isDigit() || it == '.' }
    val dot = filtered.indexOf('.')
    return if (dot >= 0) {
        filtered.substring(0, dot + 1) + filtered.substring(dot + 1).replace(".", "")
    } else {
        filtered
    }
}

@Composable
fun numeric_value_picker(
    on_dismiss: () -> Unit,
    title: String,
    initial: Long,
    is_size: Boolean,
    on_confirm: (Long) -> Unit,
) {
    val (init_value, init_unit) = remember(initial, is_size) {
        if (is_size && initial > 0) {
            when {
                initial % (1024L * 1024L * 1024L) == 0L -> (initial / (1024L * 1024L * 1024L)).toString() to "GB"
                initial % (1024L * 1024L) == 0L -> (initial / (1024L * 1024L)).toString() to "MB"
                else -> (initial / 1024L).coerceAtLeast(1L).toString() to "KB"
            }
        } else {
            initial.toString() to if (is_size) "MB" else ""
        }
    }
    var value by remember { mutableStateOf(init_value) }
    var unit by remember { mutableStateOf(init_unit) }
    val colors = AsterMaterial.colors
    base_sheet(on_dismiss = on_dismiss, title = title) {
        Column(modifier = Modifier.padding(horizontal = AsterSpacing.lg)) {
            AsterTextField(
                value = value,
                onValueChange = { v -> value = single_decimal(v) },
                placeholder = "0",
                modifier = Modifier.testTag("numeric_input"),
            )
            if (is_size) {
                Spacer(Modifier.height(AsterSpacing.md))
                val unit_options = listOf(
                    "KB" to stringResource(R.string.rules_unit_kb),
                    "MB" to stringResource(R.string.rules_unit_mb),
                    "GB" to stringResource(R.string.rules_unit_gb),
                )
                Row(horizontalArrangement = Arrangement.spacedBy(AsterSpacing.sm)) {
                    unit_options.forEach { (u, label) ->
                        val selected = u == unit
                        Box(
                            modifier = Modifier
                                .clickable { unit = u }
                                .background(
                                    if (selected) colors.accent_blue.copy(alpha = 0.12f) else colors.bg_secondary,
                                    SquircleShape(8.dp),
                                )
                                .padding(horizontal = 14.dp, vertical = 8.dp),
                        ) {
                            Text(
                                text = label,
                                color = if (selected) colors.accent_blue else colors.text_secondary,
                                fontSize = 13.sp,
                                fontWeight = FontWeight.SemiBold,
                            )
                        }
                    }
                }
            }
            Spacer(Modifier.height(AsterSpacing.lg))
            AsterButton(
                label = stringResource(R.string.mail_rules_confirm),
                onClick = {
                    val v = value.toDoubleOrNull() ?: 0.0
                    val multiplier = when (unit) {
                        "KB" -> 1024L
                        "MB" -> 1024L * 1024L
                        "GB" -> 1024L * 1024L * 1024L
                        else -> 1L
                    }
                    on_confirm((v * multiplier).toLong())
                    on_dismiss()
                },
            )
            Spacer(Modifier.height(AsterSpacing.md))
        }
    }
}

@Composable
fun decimal_value_picker(
    on_dismiss: () -> Unit,
    title: String,
    initial: Double,
    on_confirm: (Double) -> Unit,
) {
    var value by remember { mutableStateOf("%.1f".format(java.util.Locale.US, initial)) }
    base_sheet(on_dismiss = on_dismiss, title = title) {
        Column(modifier = Modifier.padding(horizontal = AsterSpacing.lg)) {
            AsterTextField(
                value = value,
                onValueChange = { v -> value = single_decimal(v) },
                placeholder = "0.0",
                modifier = Modifier.testTag("decimal_input"),
            )
            Spacer(Modifier.height(AsterSpacing.lg))
            AsterButton(
                label = stringResource(R.string.mail_rules_confirm),
                onClick = {
                    val raw = value.toDoubleOrNull() ?: 0.0
                    val rounded = (raw * 10.0).roundToInt() / 10.0
                    on_confirm(rounded)
                    on_dismiss()
                },
            )
            Spacer(Modifier.height(AsterSpacing.md))
        }
    }
}

@Composable
fun boolean_value_picker(
    on_dismiss: () -> Unit,
    title: String,
    initial: Boolean,
    on_confirm: (Boolean) -> Unit,
) {
    base_sheet(on_dismiss = on_dismiss, title = title) {
        Column {
            row_select(
                label = stringResource(R.string.mail_rules_yes),
                selected = initial,
                on_click = { on_confirm(true); on_dismiss() },
                test_tag = "bool_yes",
            )
            row_select(
                label = stringResource(R.string.mail_rules_no),
                selected = !initial,
                on_click = { on_confirm(false); on_dismiss() },
                test_tag = "bool_no",
            )
        }
    }
}

@Composable
fun color_picker(
    on_dismiss: () -> Unit,
    selected: String,
    palette: List<String>,
    on_pick: (String) -> Unit,
) {
    base_sheet(on_dismiss = on_dismiss, title = stringResource(R.string.mail_rules_pick_color)) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = AsterSpacing.lg, vertical = AsterSpacing.md),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            palette.forEach { hex ->
                val color = try { Color(android.graphics.Color.parseColor(hex)) } catch (_: Throwable) { Color.Gray }
                val is_selected = hex.equals(selected, ignoreCase = true)
                Box(
                    modifier = Modifier
                        .size(36.dp)
                        .background(color, SquircleShape(999.dp))
                        .border(
                            if (is_selected) 3.dp else 0.dp,
                            AsterMaterial.colors.text_primary,
                            SquircleShape(999.dp),
                        )
                        .clickable { on_pick(hex); on_dismiss() },
                )
            }
        }
    }
}

@Composable
fun folder_picker(
    on_dismiss: () -> Unit,
    folders: List<picker_item>,
    selected_token: String?,
    on_pick: (String, String) -> Unit,
) {
    base_sheet(on_dismiss = on_dismiss, title = stringResource(R.string.mail_rules_pick_folder)) {
        if (folders.isEmpty()) {
            Box(
                modifier = Modifier.fillMaxWidth().padding(AsterSpacing.lg),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = stringResource(R.string.mail_rules_no_folders),
                    color = AsterMaterial.colors.text_tertiary,
                    fontSize = 13.sp,
                )
            }
        } else {
            LazyColumn(modifier = Modifier.heightIn(max = 480.dp)) {
                items(folders) { f ->
                    row_select(
                        label = f.label,
                        selected = f.id == selected_token,
                        on_click = { on_pick(f.id, f.label); on_dismiss() },
                        test_tag = "folder_${f.label}",
                    )
                }
            }
        }
    }
}

@Composable
fun label_multi_picker(
    on_dismiss: () -> Unit,
    labels: List<picker_item>,
    selected_tokens: List<String>,
    on_confirm: (List<String>) -> Unit,
) {
    var selected by remember { mutableStateOf(selected_tokens.toSet()) }
    base_sheet(on_dismiss = on_dismiss, title = stringResource(R.string.mail_rules_pick_labels)) {
        if (labels.isEmpty()) {
            Box(
                modifier = Modifier.fillMaxWidth().padding(AsterSpacing.lg),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = stringResource(R.string.rules_no_labels),
                    color = AsterMaterial.colors.text_tertiary,
                    fontSize = 13.sp,
                )
            }
        } else {
            Column(modifier = Modifier.heightIn(max = 480.dp).verticalScroll(rememberScrollState())) {
                labels.forEach { l ->
                    row_select(
                        label = l.label,
                        selected = l.id in selected,
                        on_click = {
                            selected = if (l.id in selected) selected - l.id else selected + l.id
                        },
                    )
                }
                Spacer(Modifier.height(AsterSpacing.md))
                Box(modifier = Modifier.padding(horizontal = AsterSpacing.lg)) {
                    AsterButton(
                        label = stringResource(R.string.mail_rules_confirm),
                        onClick = { on_confirm(selected.toList()); on_dismiss() },
                    )
                }
                Spacer(Modifier.height(AsterSpacing.md))
            }
        }
    }
}
