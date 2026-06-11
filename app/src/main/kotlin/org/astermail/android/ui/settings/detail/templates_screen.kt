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
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import org.astermail.android.R
import org.astermail.android.design.SquircleShape
import org.astermail.android.design.AsterMaterial
import org.astermail.android.design.AsterSpacing
import org.astermail.android.design.components.AsterButton
import org.astermail.android.design.components.AsterCard
import org.astermail.android.design.components.AsterDivider
import org.astermail.android.design.components.AsterGhostButton
import org.astermail.android.design.components.AsterSecondaryButton
import org.astermail.android.templates.TemplatesViewModel

@Composable
fun TemplatesScreen(
    on_back: () -> Unit,
    on_open: (id: String) -> Unit = {},
) {
    val colors = AsterMaterial.colors
    val vm: TemplatesViewModel = hiltViewModel()
    val state by vm.state.collectAsStateWithLifecycle()

    LaunchedEffect(Unit) { vm.load() }

    val draft = state.draft
    if (draft != null) {
        template_editor(
            title = if (draft.id.isBlank()) stringResource(R.string.new_template) else stringResource(R.string.edit_template),
            name = draft.name,
            category = draft.category,
            content = draft.content,
            is_saving = state.is_saving,
            error = state.error,
            on_back = { vm.cancel_edit() },
            on_name_change = { vm.update_draft(name = it) },
            on_category_change = { vm.update_draft(category = it) },
            on_content_change = { vm.update_draft(content = it) },
            on_save = { vm.save_draft() },
        )
        return
    }

    detail_scaffold(
        title = stringResource(R.string.templates),
        on_back = on_back,
        trailing = {
            AsterGhostButton(label = stringResource(R.string.new_label), onClick = { vm.start_new() })
        },
    ) {
        if (state.is_loading && state.items.isEmpty()) {
            Box(
                modifier = Modifier.fillMaxWidth().padding(AsterSpacing.xxl),
                contentAlignment = Alignment.Center,
            ) {
                CircularProgressIndicator(color = colors.accent_blue, modifier = Modifier.size(24.dp))
            }
            return@detail_scaffold
        }

        state.error?.let { err ->
            error_banner(err)
            v_gap(AsterSpacing.md)
        }

        if (state.items.isEmpty() && !state.is_loading) {
            AsterCard(modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(AsterSpacing.lg)) {
                    Text(
                        text = stringResource(R.string.no_templates),
                        color = colors.text_primary,
                        fontSize = 16.sp,
                        fontWeight = FontWeight.SemiBold,
                    )
                    Spacer(Modifier.size(AsterSpacing.xs))
                    Text(
                        text = stringResource(R.string.templates_description),
                        color = colors.text_tertiary,
                        fontSize = 13.sp,
                    )
                    Spacer(Modifier.size(AsterSpacing.lg))
                    AsterButton(label = stringResource(R.string.create_template), onClick = { vm.start_new() })
                }
            }
            v_gap(AsterSpacing.xxl)
            return@detail_scaffold
        }

        AsterCard(modifier = Modifier.fillMaxWidth()) {
            Column {
                state.items.forEachIndexed { index, item ->
                    template_row(
                        name = item.name,
                        category = item.category,
                        preview = item.content.take(120),
                        on_edit = { vm.start_edit(item.id) },
                        on_delete = { vm.delete(item.id) },
                    )
                    if (index < state.items.lastIndex) AsterDivider()
                }
            }
        }
        v_gap(AsterSpacing.xxl)
    }
}

@Composable
private fun template_row(
    name: String,
    category: String,
    preview: String,
    on_edit: () -> Unit,
    on_delete: () -> Unit,
) {
    val colors = AsterMaterial.colors
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = on_edit)
            .padding(horizontal = AsterSpacing.md, vertical = AsterSpacing.md),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = name.ifBlank { stringResource(R.string.unnamed_template) },
                color = colors.text_primary,
                fontSize = 15.sp,
                fontWeight = FontWeight.SemiBold,
            )
            if (category.isNotBlank()) {
                Text(
                    text = category,
                    color = colors.accent_blue,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Medium,
                )
            }
            if (preview.isNotBlank()) {
                Text(
                    text = preview,
                    color = colors.text_tertiary,
                    fontSize = 13.sp,
                    maxLines = 2,
                )
            }
        }
        Icon(
            imageVector = Icons.Outlined.Edit,
            contentDescription = stringResource(R.string.edit),
            tint = colors.text_tertiary,
            modifier = Modifier
                .size(36.dp)
                .clickable(onClick = on_edit)
                .padding(8.dp),
        )
        Icon(
            imageVector = Icons.Outlined.Delete,
            contentDescription = stringResource(R.string.delete),
            tint = colors.danger,
            modifier = Modifier
                .size(36.dp)
                .clickable(onClick = on_delete)
                .padding(8.dp),
        )
    }
}

@Composable
private fun template_editor(
    title: String,
    name: String,
    category: String,
    content: String,
    is_saving: Boolean,
    error: String?,
    on_back: () -> Unit,
    on_name_change: (String) -> Unit,
    on_category_change: (String) -> Unit,
    on_content_change: (String) -> Unit,
    on_save: () -> Unit,
) {
    val colors = AsterMaterial.colors
    detail_scaffold(
        title = title,
        on_back = on_back,
        trailing = {
            if (is_saving) {
                CircularProgressIndicator(color = colors.accent_blue, modifier = Modifier.size(18.dp))
            } else {
                AsterGhostButton(label = stringResource(R.string.save), onClick = on_save)
            }
        },
    ) {
        if (error != null) {
            error_banner(error)
            v_gap(AsterSpacing.md)
        }
        section_label(stringResource(R.string.template_name_section))
        labeled_text_field(value = name, placeholder = stringResource(R.string.template_name_placeholder), on_change = on_name_change)
        v_gap(AsterSpacing.md)
        section_label(stringResource(R.string.template_category))
        labeled_text_field(value = category, placeholder = stringResource(R.string.template_category_placeholder), on_change = on_category_change)
        v_gap(AsterSpacing.md)
        section_label(stringResource(R.string.template_content))
        labeled_text_field(
            value = content,
            placeholder = stringResource(R.string.template_content_placeholder),
            on_change = on_content_change,
            min_height = 220.dp,
        )
        v_gap(AsterSpacing.lg)
        AsterSecondaryButton(label = stringResource(R.string.cancel), onClick = on_back)
        v_gap(AsterSpacing.xxl)
    }
}

@Composable
private fun labeled_text_field(
    value: String,
    placeholder: String,
    on_change: (String) -> Unit,
    min_height: androidx.compose.ui.unit.Dp = 56.dp,
) {
    val colors = AsterMaterial.colors
    BasicTextField(
        value = value,
        onValueChange = on_change,
        textStyle = TextStyle(color = colors.text_primary, fontSize = 15.sp),
        cursorBrush = SolidColor(colors.accent_blue),
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = min_height)
            .background(colors.input_bg, SquircleShape(18.dp))
            .border(1.dp, colors.input_border, SquircleShape(18.dp))
            .padding(AsterSpacing.lg),
        decorationBox = { inner_field ->
            Box {
                if (value.isEmpty()) {
                    Text(text = placeholder, color = colors.text_muted, fontSize = 15.sp)
                }
                inner_field()
            }
        },
    )
}

