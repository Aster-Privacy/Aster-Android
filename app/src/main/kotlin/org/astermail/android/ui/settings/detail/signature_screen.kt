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
import androidx.compose.ui.draw.clip
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Icon
import compose.icons.TablerIcons
import compose.icons.tablericons.Check
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import org.astermail.android.R
import org.astermail.android.design.SquircleShape
import org.astermail.android.design.AsterMaterial
import org.astermail.android.design.AsterSpacing
import org.astermail.android.design.components.AsterCard
import org.astermail.android.design.components.AsterButton
import org.astermail.android.design.components.AsterSecondaryButton
import org.astermail.android.billing.PlanLimitsViewModel
import org.astermail.android.design.components.AsterSwitch
import org.astermail.android.settings.DecryptedSignature
import org.astermail.android.settings.SettingsViewModel
import org.astermail.android.settings.shared_settings_view_model

@Composable
fun SignatureScreen(
    on_back: () -> Unit,
    on_open: (id: String) -> Unit = {},
) {
    val colors = AsterMaterial.colors
    val vm: SettingsViewModel = shared_settings_view_model()
    val plan_vm: PlanLimitsViewModel = hiltViewModel()
    val signatures by vm.signatures.collectAsStateWithLifecycle()
    val state by vm.state.collectAsStateWithLifecycle()
    val plan_state by plan_vm.state.collectAsStateWithLifecycle()
    val is_paid = plan_state.limits?.let { it.plan_code != "free" } ?: false
    var editing by remember { mutableStateOf<DecryptedSignature?>(null) }
    var creating by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        vm.load_aliases()
        vm.load_signature()
        vm.load_preferences()
    }

    if (editing != null || creating) {
        signature_edit_modal(
            initial = editing,
            aliases = state.aliases,
            all_signatures = signatures,
            on_cancel = { editing = null; creating = false },
            on_save = { name, content, alias_id, placement, is_html, is_default ->
                val target = editing
                if (target != null) {
                    vm.update_signature(
                        id = target.id,
                        name = name,
                        content = content,
                        is_html = is_html,
                        alias_id = alias_id,
                        placement = placement,
                        clear_alias = alias_id == null,
                    )
                    if (is_default && !target.is_default && alias_id == null) {
                        vm.set_default_signature(target.id)
                    }
                } else {
                    vm.create_signature(
                        name = name,
                        content = content,
                        is_default = is_default && alias_id == null,
                        is_html = is_html,
                        alias_id = alias_id,
                        placement = placement,
                    )
                }
                editing = null
                creating = false
            },
            on_delete = {
                val target = editing
                if (target != null) {
                    vm.delete_signature(target.id)
                }
                editing = null
                creating = false
            },
        )
        return
    }

    detail_scaffold(
        title = stringResource(R.string.signature),
        on_back = on_back,
    ) {
        section_label(stringResource(R.string.your_signature))
        AsterCard(modifier = Modifier.fillMaxWidth()) {
            Column {
                if (signatures.isEmpty()) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(AsterSpacing.lg),
                    ) {
                        Text(
                            text = stringResource(R.string.signature_placeholder),
                            color = colors.text_muted,
                            fontSize = 14.sp,
                        )
                    }
                } else {
                    val sorted = signatures.sortedWith(
                        compareByDescending<DecryptedSignature> { it.alias_id == null && it.is_default }
                            .thenBy { it.alias_id ?: "" }
                            .thenBy { it.name },
                    )
                    sorted.forEach { sig ->
                        val subtitle = if (sig.alias_id == null) {
                            stringResource(R.string.signature_apply_default)
                        } else {
                            state.aliases.firstOrNull { it.id == sig.alias_id }?.address.orEmpty()
                        }
                        detail_row(
                            title = sig.name.ifBlank { stringResource(R.string.signature) },
                            subtitle = subtitle,
                            on_click = { editing = sig },
                        )
                    }
                }
            }
        }
        v_gap(AsterSpacing.md)
        AsterSecondaryButton(
            label = stringResource(R.string.add_signature),
            onClick = { creating = true },
        )
        v_gap(AsterSpacing.xxl)
        section_label(stringResource(R.string.show_aster_branding))
        AsterCard(modifier = Modifier.fillMaxWidth()) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = AsterSpacing.lg, vertical = AsterSpacing.md),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = stringResource(R.string.show_aster_branding_description),
                    color = colors.text_tertiary,
                    fontSize = 13.sp,
                    modifier = Modifier.weight(1f),
                )
                Spacer(Modifier.width(AsterSpacing.md))
                AsterSwitch(
                    checked = state.preferences?.show_aster_branding == true,
                    enabled = is_paid && state.preferences != null && state.preferences_authoritative,
                    onCheckedChange = { checked ->
                        val base = state.preferences ?: return@AsterSwitch
                        vm.save_preferences(base.copy(show_aster_branding = checked))
                    },
                )
            }
        }
        if (!is_paid) {
            v_gap(AsterSpacing.md)
            Text(
                text = stringResource(R.string.show_aster_branding_free_note),
                color = colors.text_muted,
                fontSize = 12.sp,
            )
            v_gap(AsterSpacing.sm)
            AsterSecondaryButton(
                label = stringResource(R.string.upgrade),
                onClick = { on_open("billing") },
            )
        }
        v_gap(AsterSpacing.xxl)
    }
}

@Composable
private fun signature_edit_modal(
    initial: DecryptedSignature?,
    aliases: List<org.astermail.android.api.settings.AliasInfo>,
    all_signatures: List<DecryptedSignature>,
    on_cancel: () -> Unit,
    on_save: (name: String, content: String, alias_id: String?, placement: Int?, is_html: Boolean, is_default: Boolean) -> Unit,
    on_delete: () -> Unit,
) {
    val colors = AsterMaterial.colors
    var name by remember { mutableStateOf(initial?.name.orEmpty()) }
    var content by remember { mutableStateOf(initial?.content.orEmpty()) }
    var alias_id by remember { mutableStateOf(initial?.alias_id) }
    var placement by remember { mutableStateOf(initial?.placement) }
    var is_default by remember { mutableStateOf(initial?.is_default ?: (initial == null)) }
    var is_html by remember { mutableStateOf(initial?.is_html ?: false) }
    val editor_controller = remember { signature_editor_controller() }

    fun commit(latest_content: String) {
        val prepared = if (is_html) {
            sanitize_signature_html(latest_content)
        } else {
            latest_content
        }
        if (prepared.isBlank()) return
        on_save(name, prepared, alias_id, placement, is_html, is_default)
    }

    detail_scaffold(
        title = if (initial == null) stringResource(R.string.add_signature) else stringResource(R.string.signature),
        on_back = on_cancel,
    ) {
        section_label(stringResource(R.string.signature_name))
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .background(colors.input_bg, SquircleShape(18.dp))
                .border(1.dp, colors.input_border, SquircleShape(18.dp))
                .padding(AsterSpacing.md),
        ) {
            BasicTextField(
                value = name,
                onValueChange = { name = it },
                textStyle = TextStyle(color = colors.text_primary, fontSize = 15.sp),
                cursorBrush = SolidColor(colors.accent_blue),
                modifier = Modifier.fillMaxWidth(),
            )
        }
        v_gap(AsterSpacing.lg)
        section_label(stringResource(R.string.your_signature))
        if (is_html) {
            signature_format_toolbar(controller = editor_controller)
            v_gap(AsterSpacing.sm)
            signature_rich_editor(
                initial_html = content,
                controller = editor_controller,
                on_html_change = { content = it },
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(SquircleShape(18.dp))
                    .border(1.dp, colors.input_border, SquircleShape(18.dp))
                    .height(220.dp),
            )
            v_gap(AsterSpacing.sm)
            AsterSecondaryButton(
                label = stringResource(R.string.signature_edit_as_plain),
                onClick = {
                    content = android.text.Html.fromHtml(
                        content,
                        android.text.Html.FROM_HTML_MODE_COMPACT,
                    ).toString().trim()
                    is_html = false
                },
            )
        } else {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = 160.dp)
                    .background(colors.input_bg, SquircleShape(18.dp))
                    .border(1.dp, colors.input_border, SquircleShape(18.dp))
                    .padding(AsterSpacing.lg),
            ) {
                if (content.isEmpty()) {
                    Text(
                        text = stringResource(R.string.signature_placeholder),
                        color = colors.text_muted,
                        fontSize = 15.sp,
                    )
                }
                BasicTextField(
                    value = content,
                    onValueChange = { content = it },
                    textStyle = TextStyle(color = colors.text_primary, fontSize = 15.sp),
                    cursorBrush = SolidColor(colors.accent_blue),
                    modifier = Modifier.fillMaxWidth(),
                )
            }
            v_gap(AsterSpacing.sm)
            AsterSecondaryButton(
                label = stringResource(R.string.signature_add_formatting),
                onClick = {
                    content = plain_signature_to_html(content)
                    is_html = true
                },
            )
        }
        v_gap(AsterSpacing.lg)
        section_label(stringResource(R.string.signature_apply_to))
        AsterCard(modifier = Modifier.fillMaxWidth()) {
            Column {
                dropdown_row(
                    label = stringResource(R.string.signature_apply_default),
                    selected = alias_id == null,
                    on_click = { alias_id = null },
                )
                aliases.filter { it.is_enabled && it.address.contains('@') }.forEach { a ->
                    val in_use_by_other = all_signatures.any {
                        it.alias_id == a.id && it.id != initial?.id
                    }
                    val label = if (in_use_by_other) {
                        a.address + " (" + stringResource(R.string.signature_alias_conflict) + ")"
                    } else {
                        a.address
                    }
                    dropdown_row(
                        label = label,
                        selected = alias_id == a.id,
                        on_click = { if (!in_use_by_other) alias_id = a.id },
                    )
                }
            }
        }
        v_gap(AsterSpacing.lg)
        section_label(stringResource(R.string.signature_placement))
        AsterCard(modifier = Modifier.fillMaxWidth()) {
            Column {
                dropdown_row(
                    label = stringResource(R.string.signature_placement_inherit),
                    selected = placement == null,
                    on_click = { placement = null },
                )
                dropdown_row(
                    label = stringResource(R.string.signature_placement_above),
                    selected = placement == 1,
                    on_click = { placement = 1 },
                )
                dropdown_row(
                    label = stringResource(R.string.signature_placement_below),
                    selected = placement == 0,
                    on_click = { placement = 0 },
                )
            }
        }
        v_gap(AsterSpacing.lg)
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(AsterSpacing.md),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(modifier = Modifier.weight(1f)) {
                AsterSecondaryButton(
                    label = stringResource(R.string.cancel),
                    onClick = on_cancel,
                )
            }
            Box(modifier = Modifier.weight(1f)) {
                AsterButton(
                    label = stringResource(R.string.save),
                    onClick = {
                        if (is_html) {
                            editor_controller.request_html { latest ->
                                commit(latest?.takeIf { it.isNotBlank() } ?: content)
                            }
                        } else {
                            commit(content)
                        }
                    },
                )
            }
        }
        if (initial != null) {
            v_gap(AsterSpacing.md)
            AsterSecondaryButton(
                label = stringResource(R.string.delete_signature),
                onClick = on_delete,
            )
        }
        v_gap(AsterSpacing.xxl)
    }
}

@Composable
private fun dropdown_row(
    label: String,
    selected: Boolean,
    on_click: () -> Unit,
) {
    val colors = AsterMaterial.colors
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = on_click)
            .heightIn(min = 48.dp)
            .padding(horizontal = AsterSpacing.md, vertical = AsterSpacing.sm),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = label,
            color = if (selected) colors.accent_blue else colors.text_primary,
            fontSize = 15.sp,
            fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
            modifier = Modifier.weight(1f),
        )
        if (selected) {
            Spacer(Modifier.width(AsterSpacing.sm))
            Icon(
                imageVector = TablerIcons.Check,
                contentDescription = null,
                tint = colors.accent_blue,
                modifier = Modifier.size(18.dp),
            )
        }
    }
}
