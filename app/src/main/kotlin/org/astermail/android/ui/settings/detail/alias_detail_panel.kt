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

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.ripple
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.rememberTextMeasurer
import org.astermail.android.design.AsterDuration
import org.astermail.android.design.AsterEasing
import org.astermail.android.design.control_surface_color
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import compose.icons.TablerIcons
import compose.icons.tablericons.ChevronDown
import compose.icons.tablericons.Copy
import compose.icons.tablericons.DotsVertical
import compose.icons.tablericons.Pencil
import compose.icons.tablericons.PlayerPlay
import compose.icons.tablericons.PlayerStop
import compose.icons.tablericons.Send
import compose.icons.tablericons.Settings
import org.astermail.android.R
import org.astermail.android.design.AsterMaterial
import org.astermail.android.design.AsterSpacing
import org.astermail.android.design.AsterShapes
import org.astermail.android.design.components.AsterCard
import org.astermail.android.design.components.AsterDialog
import org.astermail.android.design.components.AsterDialogOutlineButton
import org.astermail.android.design.components.AsterDialogPrimaryButton
import org.astermail.android.design.field_surface_color
import org.astermail.android.design.components.aster_menu_item
import org.astermail.android.design.components.aster_menu
import org.astermail.android.api.settings.AliasRun
import org.astermail.android.settings.AliasDetailState
import org.astermail.android.settings.AliasRunConfirmKind
import org.astermail.android.settings.alias_run_confirm_kind
import org.astermail.android.settings.can_start_alias_run
import org.astermail.android.settings.SettingsViewModel
import org.astermail.android.settings.is_alias_run_active
import org.astermail.android.design.mirror_in_rtl

@Composable
internal fun alias_detail_panel(
    alias: org.astermail.android.api.settings.AliasInfo,
    detail: AliasDetailState,
    vm: SettingsViewModel,
    rule_delivery: AliasRuleDeliveryNote? = null,
    rule_label: AliasRuleLabelNote? = null,
    on_view_sent: (() -> Unit)? = null,
    avatars_locked: Boolean = false,
    contacts_locked: Boolean = false,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .testTag("alias_detail_panel"),
    ) {
        settings_group(title = stringResource(R.string.alias_delivery_title)) {
            alias_delivery_section(alias, vm, detail, rule_delivery, rule_label)
            if (on_view_sent != null) {
                settings_row_gap()
                Box(modifier = Modifier.testTag("alias_view_sent")) {
                    detail_row(
                        title = stringResource(R.string.alias_sent_mail_label),
                        subtitle = stringResource(R.string.alias_sent_mail_desc),
                        icon = TablerIcons.Send,
                        on_click = on_view_sent,
                    )
                }
            }
        }

        settings_group(title = stringResource(R.string.alias_panel_details_title)) {
            alias_details_section(alias, vm, avatars_locked)
        }

        when {
            detail.loaded -> {
                if (detail.stats_locked || detail.stats != null) {
                    settings_group(title = stringResource(R.string.alias_stats_title)) {
                        alias_stats_section(detail)
                    }
                }
                section_label(stringResource(R.string.alias_protection_section))
                alias_sender_pinning_section(alias.id, detail, vm)
                v_gap(AsterSpacing.md)
                alias_contacts_section(alias.id, detail, vm, contacts_locked)
                v_gap(AsterSpacing.md)
                alias_blocked_log_section(detail)
                section_label(stringResource(R.string.alias_automation_section))
                alias_rules_section(alias.id, detail, vm)
            }
            detail.load_failed -> {
                v_gap(AsterSpacing.lg)
                alias_card {
                    detail_row(
                        title = stringResource(R.string.failed_to_load),
                        trailing = {
                            alias_text_action(
                                label = stringResource(R.string.retry),
                                on_click = { vm.load_alias_detail(alias.id, force = true) },
                                modifier = Modifier.testTag("alias_detail_retry"),
                            )
                        },
                    )
                }
            }
            else -> {
                v_gap(AsterSpacing.lg)
                alias_detail_sections_skeleton(cards = 3)
            }
        }
    }
}

@Composable
internal fun alias_compact_panel(
    delivery_summary: String,
    on_compose_from: (() -> Unit)?,
    on_copy: () -> Unit,
    on_open_settings: (() -> Unit)?,
) {
    val colors = AsterMaterial.colors
    val compose_label = stringResource(R.string.compose)
    val compose_a11y = stringResource(R.string.new_message)
    val copy_label = stringResource(R.string.copy)
    val copy_a11y = stringResource(R.string.copy_address)
    val settings_label = stringResource(R.string.settings)
    val settings_a11y = stringResource(R.string.alias_open_settings)
    val actions = buildList {
        if (on_compose_from != null) {
            add(AliasQuickAction(TablerIcons.Pencil, compose_label, compose_a11y, "alias_quick_compose", on_compose_from))
        }
        add(AliasQuickAction(TablerIcons.Copy, copy_label, copy_a11y, "alias_quick_copy", on_copy))
        if (on_open_settings != null) {
            add(AliasQuickAction(TablerIcons.Settings, settings_label, settings_a11y, "alias_quick_settings", on_open_settings))
        }
    }
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = AsterSpacing.sm)
            .testTag("alias_compact_panel"),
    ) {
        Spacer(
            Modifier
                .fillMaxWidth()
                .height(1.dp)
                .background(colors.border_secondary),
        )
        if (delivery_summary.isNotBlank()) {
            Text(
                text = delivery_summary,
                color = colors.text_tertiary,
                fontSize = 13.sp,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = AsterSpacing.md),
            )
        }
        Spacer(Modifier.height(AsterSpacing.md))
        BoxWithConstraints(modifier = Modifier.fillMaxWidth()) {
            val gap = AsterSpacing.sm
            val count = actions.size.coerceAtLeast(1)
            val slot = (maxWidth - gap * (count - 1)) / count
            val measurer = rememberTextMeasurer()
            val density = LocalDensity.current
            val label_style = LocalTextStyle.current.merge(
                TextStyle(fontSize = alias_quick_label_size, fontWeight = FontWeight.Medium),
            )
            val labels = actions.map { it.label }
            val show_icons = remember(labels, slot, density, label_style) {
                val budget = with(density) {
                    (slot - alias_quick_padding * 2 - alias_quick_icon_size - alias_quick_icon_gap).toPx()
                }
                labels.all { label ->
                    measurer.measure(label, label_style, maxLines = 1, softWrap = false).size.width <= budget
                }
            }
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(gap),
            ) {
                actions.forEach { action ->
                    alias_quick_action(
                        action = action,
                        show_icon = show_icons,
                        modifier = Modifier.weight(1f),
                    )
                }
            }
        }
    }
}

private val alias_quick_height = 40.dp
private val alias_quick_padding = 10.dp
private val alias_quick_icon_size = 16.dp
private val alias_quick_icon_gap = 6.dp
private val alias_quick_label_size = 13.sp

private class AliasQuickAction(
    val icon: androidx.compose.ui.graphics.vector.ImageVector,
    val label: String,
    val a11y: String,
    val test_tag: String,
    val on_click: () -> Unit,
)

@Composable
private fun alias_quick_action(
    action: AliasQuickAction,
    show_icon: Boolean,
    modifier: Modifier,
) {
    val colors = AsterMaterial.colors
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val scale by animateFloatAsState(
        targetValue = if (pressed) 0.965f else 1f,
        animationSpec = tween(
            durationMillis = if (pressed) AsterDuration.tap_down else AsterDuration.tap_up,
            easing = AsterEasing.tap_down,
        ),
        label = "alias_quick_scale",
    )
    Row(
        modifier = modifier
            .height(alias_quick_height)
            .graphicsLayer {
                scaleX = scale
                scaleY = scale
            }
            .clip(AsterShapes.control)
            .background(control_surface_color(colors))
            .clickable(
                role = Role.Button,
                interactionSource = interaction,
                indication = ripple(color = colors.text_primary),
                onClick = action.on_click,
            )
            .semantics { contentDescription = action.a11y }
            .padding(horizontal = alias_quick_padding)
            .testTag(action.test_tag),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (show_icon) {
            Icon(
                imageVector = action.icon,
                contentDescription = null,
                tint = colors.text_secondary,
                modifier = Modifier.size(alias_quick_icon_size),
            )
            Spacer(Modifier.width(alias_quick_icon_gap))
        }
        Text(
            text = action.label,
            color = colors.text_primary,
            fontSize = alias_quick_label_size,
            fontWeight = FontWeight.Medium,
            maxLines = 1,
            softWrap = false,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.clearAndSetSemantics {},
        )
    }
}

@Composable
internal fun panel_section_title(text: String) {
    Text(
        text = text,
        color = AsterMaterial.colors.text_secondary,
        fontSize = 11.sp,
        fontWeight = FontWeight.SemiBold,
    )
}

@Composable
internal fun panel_hint_text(text: String) {
    Text(text = text, color = AsterMaterial.colors.text_muted, fontSize = 11.sp)
}

@Composable
internal fun panel_locked_section(title: String) {
    alias_card(title = title, description = stringResource(R.string.alias_feature_locked)) {}
}

@Composable
internal fun panel_row_chip(label: String) {
    val colors = AsterMaterial.colors
    val danger_background = org.astermail.android.ui.mail.chip_background(
        colors.danger,
        colors.bg_primary,
        colors.is_dark,
    )
    Text(
        text = label,
        color = org.astermail.android.ui.mail.chip_content(colors.danger, danger_background, colors.is_dark),
        fontSize = 10.sp,
        fontWeight = FontWeight.Medium,
        modifier = Modifier
            .clip(CircleShape)
            .background(danger_background)
            .padding(horizontal = 6.dp, vertical = 2.dp),
    )
}

@Composable
private fun alias_details_section(
    alias: org.astermail.android.api.settings.AliasInfo,
    vm: SettingsViewModel,
    avatars_locked: Boolean,
) {
    alias_card_body {
        alias_inline_field(
            label = stringResource(R.string.alias_panel_display_name),
            placeholder = stringResource(R.string.alias_panel_display_name_placeholder),
            value = alias.encrypted_display_name.orEmpty(),
            test_tag = "alias_field_display_name",
            on_save = { vm.update_alias_display_name(alias.id, it) },
            locked = avatars_locked,
        )
        alias_inline_field(
            label = stringResource(R.string.alias_note_title),
            placeholder = stringResource(R.string.alias_note_placeholder_short),
            value = alias.encrypted_note.orEmpty(),
            test_tag = "alias_field_note",
            on_save = { vm.update_alias_note(alias.id, it) },
        )
    }
    settings_row_gap()
    alias_card_body {
        alias_websites_field(
            websites = alias.websites,
            on_add = { vm.add_alias_website(alias.id, it) },
            on_remove = { vm.remove_alias_website(alias.id, it) },
        )
    }
    settings_row_gap()
    alias_card_body {
        alias_avatar_field(
            address = alias.address,
            profile_picture = alias.profile_picture,
            locked = avatars_locked,
            on_change = { vm.update_alias_avatar(alias.id, it) },
        )
    }
}

data class AliasRuleDeliveryNote(
    val rule_name: String,
    val folder_name: String,
    val matches_alias_delivery: Boolean,
)

data class AliasRuleLabelNote(
    val rule_name: String,
    val label_names: String,
    val matches_alias_label: Boolean,
)

@Composable
private fun alias_delivery_section(
    alias: org.astermail.android.api.settings.AliasInfo,
    vm: SettingsViewModel,
    detail: AliasDetailState,
    rule_delivery: AliasRuleDeliveryNote?,
    rule_label: AliasRuleLabelNote?,
) {
    val state by vm.state.collectAsState()

    LaunchedEffect(Unit) {
        vm.load_labels(folder_type = "folder")
        vm.load_tags(force = false)
    }

    val folders = state.labels.filter {
        (it.folder_type == "folder" || it.folder_type == "custom") &&
            !it.encrypted_name.isNullOrBlank()
    }
    val selected_folder = alias.delivery_folder_token?.let { token ->
        folders.firstOrNull { it.label_token == token }
    }
    val selected_label = when {
        selected_folder != null -> selected_folder.encrypted_name.orEmpty()
        alias.delivery_folder_token != null -> stringResource(R.string.alias_delivery_folder_missing)
        alias.never_inbox -> stringResource(R.string.folder_archive)
        else -> stringResource(R.string.folder_inbox)
    }

    val label_decrypt_failed = stringResource(R.string.label_decrypt_failed)
    val tags = state.tags.map { tag ->
        tag.copy(encrypted_name = org.astermail.android.labels.tag_display_name(tag, label_decrypt_failed))
    }
    val selected_tag = alias.delivery_label_token?.let { token ->
        tags.firstOrNull { it.tag_token == token }
    }
    val selected_label_name = when {
        selected_tag != null -> selected_tag.encrypted_name
        alias.delivery_label_token != null -> stringResource(R.string.alias_delivery_label_missing)
        else -> stringResource(R.string.alias_delivery_label_none)
    }

    val apply_unsupported = alias.delivery_folder_token?.let { token ->
        state.labels.firstOrNull { it.label_token == token }?.folder_type == "spam"
    } ?: false
    val confirm_kind = alias_run_confirm_kind(
        folder_token = alias.delivery_folder_token,
        never_inbox = alias.never_inbox,
        label_token = alias.delivery_label_token,
    )
    val can_start = can_start_alias_run(
        kind = confirm_kind,
        folder_is_spam = apply_unsupported,
        run = detail.apply_run,
        busy = detail.apply_busy,
    )
    val show_run_menu = !detail.apply_unsupported
    var confirm_open by remember { mutableStateOf(false) }

    if (confirm_open && confirm_kind != null) {
        AsterDialog(
            on_dismiss = { confirm_open = false },
            title = stringResource(R.string.alias_apply_existing),
            message = alias_apply_existing_confirm_message(confirm_kind, selected_label, selected_label_name),
            footer = {
                AsterDialogOutlineButton(
                    label = stringResource(R.string.cancel),
                    onClick = { confirm_open = false },
                )
                AsterDialogPrimaryButton(
                    label = stringResource(R.string.alias_apply_existing),
                    onClick = {
                        confirm_open = false
                        vm.run_alias_on_existing(alias.id)
                    },
                )
            },
        )
    }

    alias_delivery_picker(
        selected_label = selected_label,
        folders = folders,
        rule_delivery = rule_delivery,
        selected_label_name = selected_label_name,
        tags = tags,
        rule_label = rule_label,
        on_select_inbox = { vm.set_alias_delivery(alias.id, null, to_archive = false) },
        on_select_archive = { vm.set_alias_delivery(alias.id, null, to_archive = true) },
        on_select_folder = { token -> vm.set_alias_delivery(alias.id, token, to_archive = false) },
        on_select_delivery_label = { token -> vm.set_alias_delivery_label(alias.id, token) },
        run_menu = if (show_run_menu) {
            {
                alias_apply_existing_menu(
                    run = detail.apply_run,
                    busy = detail.apply_busy,
                    can_start = can_start,
                    on_apply = { confirm_open = true },
                    on_cancel = { vm.cancel_alias_run(alias.id) },
                )
            }
        } else {
            null
        },
        apply_section = if (show_run_menu) {
            { alias_apply_existing_status_text(detail.apply_run, apply_unsupported) }
        } else {
            null
        },
    )
}

@Composable
private fun alias_apply_existing_confirm_message(
    kind: AliasRunConfirmKind,
    folder_name: String,
    label_name: String,
): String = when (kind) {
    AliasRunConfirmKind.FOLDER_LABEL ->
        stringResource(R.string.alias_apply_existing_confirm_folder_label, folder_name, label_name)
    AliasRunConfirmKind.FOLDER -> stringResource(R.string.alias_apply_existing_confirm_folder, folder_name)
    AliasRunConfirmKind.LABEL -> stringResource(R.string.alias_apply_existing_confirm_label, label_name)
}

@Composable
internal fun alias_delivery_rule_note(note: AliasRuleDeliveryNote, selected_label: String) {
    val colors = AsterMaterial.colors
    val text = if (note.matches_alias_delivery) {
        stringResource(R.string.alias_delivery_rule_note, note.rule_name, note.folder_name)
    } else {
        stringResource(
            R.string.alias_delivery_rule_conflict,
            note.rule_name,
            note.folder_name,
            selected_label,
        )
    }
    Text(
        text = text,
        color = if (note.matches_alias_delivery) colors.text_tertiary else colors.warning,
        fontSize = 13.sp,
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = AsterSpacing.lg, end = AsterSpacing.lg, bottom = AsterSpacing.sm)
            .testTag("alias_delivery_rule_note"),
    )
}

@Composable
internal fun alias_delivery_label_rule_note(note: AliasRuleLabelNote, selected_label_name: String) {
    val colors = AsterMaterial.colors
    val text = if (note.matches_alias_label) {
        stringResource(R.string.alias_delivery_label_rule_note, note.rule_name, note.label_names)
    } else {
        stringResource(
            R.string.alias_delivery_label_rule_conflict,
            note.rule_name,
            note.label_names,
            selected_label_name,
        )
    }
    Text(
        text = text,
        color = if (note.matches_alias_label) colors.text_tertiary else colors.warning,
        fontSize = 13.sp,
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = AsterSpacing.lg, end = AsterSpacing.lg, bottom = AsterSpacing.sm)
            .testTag("alias_delivery_label_rule_note"),
    )
}

@Composable
internal fun alias_delivery_picker(
    selected_label: String,
    folders: List<org.astermail.android.api.labels.LabelItem>,
    rule_delivery: AliasRuleDeliveryNote? = null,
    selected_label_name: String = "",
    tags: List<org.astermail.android.api.tags.TagItem> = emptyList(),
    rule_label: AliasRuleLabelNote? = null,
    on_select_inbox: () -> Unit,
    on_select_archive: () -> Unit,
    on_select_folder: (String) -> Unit,
    on_select_delivery_label: (String?) -> Unit = {},
    run_menu: (@Composable () -> Unit)? = null,
    apply_section: (@Composable () -> Unit)? = null,
) {
    val inbox_label = stringResource(R.string.folder_inbox)
    val archive_label = stringResource(R.string.folder_archive)
    val none_label = stringResource(R.string.alias_delivery_label_none)

    Column(modifier = Modifier.fillMaxWidth()) {
        alias_select_row(
            title = stringResource(R.string.alias_delivery_folder_title),
            subtitle = stringResource(R.string.alias_delivery_folder_subtitle),
            value = selected_label,
            test_tag = "alias_delivery_folder_selector",
            trailing = run_menu,
        ) { expanded, dismiss ->
            aster_menu(expanded = expanded, on_dismiss = dismiss) {
                aster_menu_item(
                    label = inbox_label,
                    selected = selected_label == inbox_label,
                    on_click = {
                        dismiss()
                        on_select_inbox()
                    },
                )
                aster_menu_item(
                    label = archive_label,
                    selected = selected_label == archive_label,
                    on_click = {
                        dismiss()
                        on_select_archive()
                    },
                )
                folders.forEach { folder ->
                    val folder_name = folder.encrypted_name.orEmpty()
                    aster_menu_item(
                        label = folder_name,
                        selected = folder_name.isNotBlank() && folder_name == selected_label,
                        on_click = {
                            dismiss()
                            on_select_folder(folder.label_token)
                        },
                    )
                }
            }
        }
        rule_delivery?.let { alias_delivery_rule_note(it, selected_label) }
        settings_row_gap()
        alias_select_row(
            title = stringResource(R.string.alias_delivery_label_title),
            subtitle = stringResource(R.string.alias_delivery_label_subtitle),
            value = selected_label_name,
            test_tag = "alias_delivery_label_selector",
            trailing = if (run_menu != null) {
                { Spacer(Modifier.size(ALIAS_RUN_MENU_SIZE)) }
            } else {
                null
            },
        ) { expanded, dismiss ->
            aster_menu(expanded = expanded, on_dismiss = dismiss) {
                aster_menu_item(
                    label = none_label,
                    selected = selected_label_name == none_label,
                    on_click = {
                        dismiss()
                        on_select_delivery_label(null)
                    },
                )
                tags.forEach { tag ->
                    aster_menu_item(
                        label = tag.encrypted_name,
                        selected = tag.encrypted_name == selected_label_name,
                        on_click = {
                            dismiss()
                            on_select_delivery_label(tag.tag_token)
                        },
                    )
                }
            }
        }
        rule_label?.let { alias_delivery_label_rule_note(it, selected_label_name) }
        apply_section?.invoke()
    }
}

@Composable
private fun alias_apply_existing_status(run: AliasRun?, unsupported: Boolean): String? {
    if (unsupported) return stringResource(R.string.alias_apply_existing_unavailable)
    if (run == null) return null
    val total = run.total_estimate
    return when (run.status) {
        "pending" -> stringResource(R.string.alias_apply_existing_queued)
        "running" -> if (total != null && total > 0) {
            stringResource(R.string.alias_apply_existing_progress_total, run.scanned, total, run.applied)
        } else {
            stringResource(R.string.alias_apply_existing_progress, run.scanned, run.applied)
        }
        "completed" -> stringResource(R.string.alias_apply_existing_done, run.scanned, run.applied)
        "canceled" -> pluralStringResource(R.plurals.alias_apply_existing_canceled, run.applied.toInt(), run.applied)
        "failed" -> stringResource(R.string.alias_apply_existing_error)
        else -> null
    }
}

private val ALIAS_RUN_MENU_SIZE = 48.dp

@Composable
internal fun alias_apply_existing_menu(
    run: AliasRun?,
    busy: Boolean,
    can_start: Boolean,
    on_apply: () -> Unit,
    on_cancel: () -> Unit,
) {
    val colors = AsterMaterial.colors
    val active = is_alias_run_active(run)
    var open by remember { mutableStateOf(false) }

    Box(modifier = Modifier.size(ALIAS_RUN_MENU_SIZE), contentAlignment = Alignment.Center) {
        IconButton(
            onClick = { open = true },
            modifier = Modifier.testTag("alias_delivery_menu_button"),
        ) {
            if (busy) {
                org.astermail.android.design.components.aster_spinner(
                    modifier = Modifier.size(16.dp),
                    stroke_width = 2.dp,
                    color = colors.text_muted,
                )
            } else {
                Icon(
                    imageVector = TablerIcons.DotsVertical,
                    contentDescription = stringResource(R.string.more_options),
                    tint = colors.text_secondary,
                )
            }
        }
        aster_menu(expanded = open, on_dismiss = { open = false }) {
            if (active) {
                aster_menu_item(
                    label = stringResource(R.string.alias_apply_existing_cancel),
                    icon = TablerIcons.PlayerStop,
                    enabled = !busy,
                    test_tag = "alias_apply_existing_stop",
                    on_click = {
                        open = false
                        on_cancel()
                    },
                )
            } else {
                aster_menu_item(
                    label = stringResource(R.string.alias_apply_existing),
                    icon = TablerIcons.PlayerPlay,
                    enabled = can_start,
                    test_tag = "alias_apply_existing_button",
                    on_click = {
                        open = false
                        on_apply()
                    },
                )
            }
        }
    }
}

@Composable
internal fun alias_apply_existing_status_text(run: AliasRun?, unsupported: Boolean) {
    val colors = AsterMaterial.colors
    alias_apply_existing_status(run, unsupported)?.let { status ->
        Text(
            text = status,
            color = colors.text_tertiary,
            fontSize = 13.sp,
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = AsterSpacing.lg, vertical = AsterSpacing.sm)
                .testTag("alias_apply_existing_status"),
        )
    }
}

@Composable
private fun alias_stats_section(detail: AliasDetailState) {
    if (detail.stats_locked) {
        alias_card_hint(stringResource(R.string.alias_feature_locked))
        return
    }
    val stats = detail.stats ?: return
    val large_text = LocalDensity.current.fontScale > 1.3f
    val cells = listOf(
        stringResource(R.string.alias_stats_received) to stats.received,
        stringResource(R.string.alias_stats_forwarded) to stats.forwarded,
        stringResource(R.string.alias_stats_blocked) to stats.blocked,
        stringResource(R.string.alias_stats_replied) to stats.replied,
    )
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = AsterSpacing.lg, vertical = AsterSpacing.md),
        verticalArrangement = Arrangement.spacedBy(AsterSpacing.md),
    ) {
        cells.chunked(if (large_text) 2 else 4).forEach { row ->
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(AsterSpacing.sm),
            ) {
                row.forEach { (label, value) ->
                    alias_stat_cell(label, value, Modifier.weight(1f))
                }
            }
        }
        val created = format_panel_date(stats.created_at)
        if (created.isNotBlank()) {
            Text(
                text = stringResource(R.string.alias_stats_created, created),
                color = AsterMaterial.colors.text_tertiary,
                fontSize = 13.sp,
            )
        }
    }
}

@Composable
private fun alias_stat_cell(label: String, value: Long, modifier: Modifier = Modifier) {
    val colors = AsterMaterial.colors
    Column(modifier = modifier) {
        Text(
            text = value.toString(),
            color = colors.text_primary,
            fontSize = 20.sp,
            lineHeight = 24.sp,
            fontWeight = FontWeight.SemiBold,
            maxLines = 1,
        )
        Text(
            text = label,
            color = colors.text_tertiary,
            fontSize = 12.sp,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

internal fun format_panel_date(value: String): String {
    if (value.isBlank()) return ""
    return absolute_date_label(value)
}
