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

import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import android.text.format.Formatter
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import compose.icons.TablerIcons
import compose.icons.tablericons.AlertTriangle
import compose.icons.tablericons.CircleCheck
import compose.icons.tablericons.FileText
import compose.icons.tablericons.ChevronDown
import compose.icons.tablericons.Square
import compose.icons.tablericons.SquareCheck
import compose.icons.tablericons.Upload
import compose.icons.tablericons.X
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.astermail.android.R
import org.astermail.android.design.AsterMaterial
import org.astermail.android.design.AsterSpacing
import org.astermail.android.design.SquircleShape
import org.astermail.android.design.components.AsterDialog
import org.astermail.android.design.components.AsterDialogOutlineButton
import org.astermail.android.design.components.AsterDialogPrimaryButton
import org.astermail.android.design.components.AsterGhostButton
import org.astermail.android.design.components.AsterSecondaryButton
import org.astermail.android.design.components.aster_dropdown_item
import org.astermail.android.design.components.aster_dropdown_menu
import org.astermail.android.settings.ImportPreviewRow
import org.astermail.android.settings.ImportRowStatus
import org.astermail.android.settings.ParsedImportRow
import org.astermail.android.settings.SettingsUiState
import org.astermail.android.settings.SettingsViewModel
import org.astermail.android.settings.build_import_preview
import org.astermail.android.settings.is_encrypted_vault_export
import org.astermail.android.settings.parse_import_file

private const val MAX_IMPORT_FILE_BYTES = 4 * 1024 * 1024

private val IMPORT_MIME_TYPES = arrayOf(
    "text/csv",
    "text/comma-separated-values",
    "text/plain",
    "application/json",
    "*/*",
)

private enum class ImportStep {
    Select,
    Preview,
    Progress,
    Done,
}

private data class ImportFileContent(
    val text: String,
    val file_name: String,
    val size_bytes: Long,
)

private fun read_import_file(context: Context, uri: Uri): ImportFileContent? {
    var file_name = "import.csv"
    var size_bytes = -1L

    context.contentResolver.query(uri, null, null, null, null)?.use { cursor ->
        if (cursor.moveToFirst()) {
            val name_index = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
            val size_index = cursor.getColumnIndex(OpenableColumns.SIZE)
            if (name_index >= 0) file_name = cursor.getString(name_index) ?: file_name
            if (size_index >= 0 && !cursor.isNull(size_index)) size_bytes = cursor.getLong(size_index)
            if (size_bytes > MAX_IMPORT_FILE_BYTES) return null
        }
    }

    val bytes = context.contentResolver.openInputStream(uri)?.use { stream ->
        stream.readBytes()
    } ?: return null
    if (bytes.size > MAX_IMPORT_FILE_BYTES) return null

    return ImportFileContent(
        text = bytes.toString(Charsets.UTF_8),
        file_name = file_name,
        size_bytes = if (size_bytes >= 0) size_bytes else bytes.size.toLong(),
    )
}

@Composable
private fun import_drop_area(on_choose: () -> Unit) {
    val colors = AsterMaterial.colors
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(SquircleShape(14.dp))
            .background(colors.bg_secondary, SquircleShape(14.dp))
            .border(1.dp, colors.input_border, SquircleShape(14.dp))
            .clickable(onClick = on_choose)
            .padding(vertical = AsterSpacing.lg, horizontal = AsterSpacing.md),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(AsterSpacing.sm),
    ) {
        Icon(
            imageVector = TablerIcons.Upload,
            contentDescription = null,
            tint = colors.text_muted,
            modifier = Modifier.size(28.dp),
        )
        Text(
            text = stringResource(R.string.alias_import_file_hint),
            color = colors.text_secondary,
            fontSize = 13.sp,
        )
        AsterSecondaryButton(
            label = stringResource(R.string.alias_import_choose_file),
            onClick = on_choose,
        )
    }
}

@Composable
private fun import_file_card(
    file_name: String,
    size_bytes: Long,
    on_change: () -> Unit,
    on_remove: () -> Unit,
) {
    val colors = AsterMaterial.colors
    val context = LocalContext.current
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(SquircleShape(14.dp))
            .background(colors.bg_secondary, SquircleShape(14.dp))
            .border(1.dp, colors.input_border, SquircleShape(14.dp))
            .padding(AsterSpacing.md),
        verticalArrangement = Arrangement.spacedBy(AsterSpacing.sm),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(AsterSpacing.sm),
        ) {
            Icon(
                imageVector = TablerIcons.FileText,
                contentDescription = null,
                tint = colors.accent_blue,
                modifier = Modifier.size(20.dp),
            )
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = file_name,
                    color = colors.text_primary,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Medium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    text = Formatter.formatShortFileSize(context, size_bytes),
                    color = colors.text_tertiary,
                    fontSize = 12.sp,
                )
            }
        }
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(AsterSpacing.sm),
        ) {
            AsterGhostButton(
                label = stringResource(R.string.alias_import_remove_file),
                onClick = on_remove,
                modifier = Modifier.weight(1f),
            )
            AsterSecondaryButton(
                label = stringResource(R.string.alias_import_change_file),
                onClick = on_change,
                modifier = Modifier.weight(1f),
            )
        }
    }
}

@Composable
fun alias_import_dialog(
    vm: SettingsViewModel,
    state: SettingsUiState,
    premium_domains_allowed: Boolean = true,
    on_dismiss: () -> Unit,
) {
    val colors = AsterMaterial.colors
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    var step by remember { mutableStateOf(ImportStep.Select) }
    var parsed_rows by remember { mutableStateOf<List<ParsedImportRow>>(emptyList()) }
    var preview_rows by remember { mutableStateOf<List<ImportPreviewRow>>(emptyList()) }
    var selected_indices by remember { mutableStateOf<Set<Int>>(emptySet()) }
    var target_domain by remember { mutableStateOf("") }
    var update_existing by remember { mutableStateOf(false) }
    var domain_menu_open by remember { mutableStateOf(false) }
    var progress_current by remember { mutableStateOf(0) }
    var progress_total by remember { mutableStateOf(0) }
    var created_count by remember { mutableStateOf(0) }
    var skipped_count by remember { mutableStateOf(0) }
    var failed_count by remember { mutableStateOf(0) }
    var error_message by remember { mutableStateOf<String?>(null) }
    var picked_file by remember { mutableStateOf<ImportFileContent?>(null) }

    val domain_options = remember(state.domains, premium_domains_allowed) {
        alias_domain_options(state.domains, include_premium = premium_domains_allowed)
    }
    val available_domains = remember(domain_options) { domain_options.map { it.domain_name } }
    val existing_aliases = remember(state.aliases) {
        state.aliases
            .filterNot { it.decryption_failed }
            .associate { it.address.lowercase() to it.id }
    }
    val existing_domain_addresses = remember(state.custom_domain_addresses, state.domains) {
        val domain_ids = state.domains.associate { it.domain_name.lowercase() to it.id }
        state.custom_domain_addresses
            .filterNot { it.decryption_failed }
            .mapNotNull { address ->
                val domain_id = domain_ids[address.domain_name.lowercase()] ?: return@mapNotNull null
                address.address.lowercase() to (address.id to domain_id)
            }
            .toMap()
    }

    fun apply_preview(rows: List<ParsedImportRow>, domain: String) {
        val preview = build_import_preview(rows, domain, existing_aliases, existing_domain_addresses)
        preview_rows = preview
        selected_indices = preview.indices.filter { preview[it].status == ImportRowStatus.WillImport }.toSet()
    }

    val picker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument(),
    ) { uri: Uri? ->
        if (uri == null) return@rememberLauncherForActivityResult
        scope.launch {
            val content = withContext(Dispatchers.IO) {
                runCatching { read_import_file(context, uri) }.getOrNull()
            }
            if (content == null) {
                picked_file = null
                error_message = context.getString(R.string.something_went_wrong)
                return@launch
            }
            picked_file = content
            if (content.file_name.lowercase().endsWith(".json") && is_encrypted_vault_export(content.text)) {
                error_message = context.getString(R.string.alias_import_error_encrypted)
                return@launch
            }
            val rows = withContext(Dispatchers.Default) {
                parse_import_file(content.text, content.file_name)
            }
            if (rows.isEmpty()) {
                error_message = context.getString(R.string.alias_import_error_no_aliases)
                return@launch
            }
            val domain = available_domains.firstOrNull().orEmpty()
            error_message = null
            parsed_rows = rows
            target_domain = domain
            apply_preview(rows, domain)
            step = ImportStep.Preview
        }
    }

    val will_import_count = preview_rows.count { it.status == ImportRowStatus.WillImport }
    val exists_count = preview_rows.count { it.status == ImportRowStatus.Exists }
    val invalid_count = preview_rows.count { it.status == ImportRowStatus.Invalid }
    val action_rows = preview_rows.filterIndexed { index, row ->
        selected_indices.contains(index) &&
            (row.status == ImportRowStatus.WillImport ||
                (update_existing && row.status == ImportRowStatus.Exists))
    }

    AsterDialog(
        on_dismiss = { if (step != ImportStep.Progress) on_dismiss() },
        title = stringResource(R.string.alias_import_title),
        body = {
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(AsterSpacing.md),
            ) {
                when (step) {
                    ImportStep.Select -> {
                        Text(
                            text = stringResource(R.string.alias_import_description),
                            color = colors.text_secondary,
                            fontSize = 13.sp,
                        )
                        val current_file = picked_file
                        if (current_file == null) {
                            import_drop_area(on_choose = { picker.launch(IMPORT_MIME_TYPES) })
                        } else {
                            import_file_card(
                                file_name = current_file.file_name,
                                size_bytes = current_file.size_bytes,
                                on_change = { picker.launch(IMPORT_MIME_TYPES) },
                                on_remove = {
                                    picked_file = null
                                    error_message = null
                                },
                            )
                        }
                    }

                    ImportStep.Preview -> {
                        picked_file?.let { current_file ->
                            import_file_card(
                                file_name = current_file.file_name,
                                size_bytes = current_file.size_bytes,
                                on_change = { picker.launch(IMPORT_MIME_TYPES) },
                                on_remove = {
                                    picked_file = null
                                    parsed_rows = emptyList()
                                    preview_rows = emptyList()
                                    selected_indices = emptySet()
                                    update_existing = false
                                    error_message = null
                                    step = ImportStep.Select
                                },
                            )
                        }
                        Box(modifier = Modifier.fillMaxWidth()) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(SquircleShape(14.dp))
                                    .background(colors.input_bg, SquircleShape(14.dp))
                                    .border(1.dp, colors.input_border, SquircleShape(14.dp))
                                    .clickable(enabled = available_domains.size > 1) { domain_menu_open = true }
                                    .padding(horizontal = AsterSpacing.md, vertical = 10.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = stringResource(R.string.alias_import_target_domain),
                                        color = colors.text_tertiary,
                                        fontSize = 11.sp,
                                    )
                                    Text(
                                        text = "@$target_domain",
                                        color = colors.text_primary,
                                        fontSize = 14.sp,
                                        fontWeight = FontWeight.Medium,
                                    )
                                }
                                if (available_domains.size > 1) {
                                    Icon(
                                        imageVector = TablerIcons.ChevronDown,
                                        contentDescription = null,
                                        tint = colors.text_muted,
                                        modifier = Modifier.size(20.dp),
                                    )
                                }
                            }
                            aster_dropdown_menu(
                                expanded = domain_menu_open,
                                on_dismiss = { domain_menu_open = false },
                            ) {
                                available_domains.forEach { domain ->
                                    aster_dropdown_item(
                                        label = "@$domain",
                                        selected = domain == target_domain,
                                        on_click = {
                                            target_domain = domain
                                            apply_preview(parsed_rows, domain)
                                            domain_menu_open = false
                                        },
                                    )
                                }
                            }
                        }

                        Text(
                            text = stringResource(
                                R.string.alias_import_counts,
                                pluralStringResource(
                                    R.plurals.alias_import_to_import,
                                    will_import_count,
                                    will_import_count,
                                ),
                                pluralStringResource(
                                    R.plurals.alias_import_existing,
                                    exists_count,
                                    exists_count,
                                ),
                                pluralStringResource(
                                    R.plurals.alias_import_invalid,
                                    invalid_count,
                                    invalid_count,
                                ),
                            ),
                            color = colors.text_tertiary,
                            fontSize = 12.sp,
                        )

                        LazyColumn(
                            modifier = Modifier
                                .fillMaxWidth()
                                .heightIn(max = 260.dp),
                            verticalArrangement = Arrangement.spacedBy(2.dp),
                        ) {
                            itemsIndexed(preview_rows) { index, row ->
                                val selectable = row.status != ImportRowStatus.Invalid
                                val selected = selected_indices.contains(index)
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clip(SquircleShape(10.dp))
                                        .clickable(enabled = selectable) {
                                            selected_indices = if (selected) {
                                                selected_indices - index
                                            } else {
                                                selected_indices + index
                                            }
                                        }
                                        .padding(horizontal = AsterSpacing.sm, vertical = 8.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(AsterSpacing.sm),
                                ) {
                                    Icon(
                                        imageVector = if (selected && selectable) TablerIcons.SquareCheck else TablerIcons.Square,
                                        contentDescription = null,
                                        tint = if (selectable) colors.accent_blue else colors.text_muted,
                                        modifier = Modifier.size(18.dp),
                                    )
                                    Text(
                                        text = row.address,
                                        color = if (selectable) colors.text_primary else colors.text_muted,
                                        fontSize = 13.sp,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis,
                                        modifier = Modifier.weight(1f),
                                    )
                                    val (status_icon, status_tint) = when (row.status) {
                                        ImportRowStatus.WillImport -> TablerIcons.CircleCheck to colors.success
                                        ImportRowStatus.Exists -> TablerIcons.AlertTriangle to colors.warning
                                        ImportRowStatus.Invalid -> TablerIcons.X to colors.danger
                                    }
                                    Icon(
                                        imageVector = status_icon,
                                        contentDescription = null,
                                        tint = status_tint,
                                        modifier = Modifier.size(16.dp),
                                    )
                                }
                            }
                        }

                        if (exists_count > 0) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(SquircleShape(12.dp))
                                    .background(colors.bg_secondary)
                                    .clickable {
                                        update_existing = !update_existing
                                        if (update_existing) {
                                            selected_indices = selected_indices + preview_rows.indices
                                                .filter { preview_rows[it].status == ImportRowStatus.Exists }
                                        }
                                    }
                                    .padding(AsterSpacing.md),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(AsterSpacing.sm),
                            ) {
                                Icon(
                                    imageVector = if (update_existing) TablerIcons.SquareCheck else TablerIcons.Square,
                                    contentDescription = null,
                                    tint = colors.accent_blue,
                                    modifier = Modifier.size(18.dp),
                                )
                                Text(
                                    text = stringResource(R.string.alias_import_update_existing),
                                    color = colors.text_primary,
                                    fontSize = 13.sp,
                                )
                            }
                        }
                    }

                    ImportStep.Progress -> {
                        LinearProgressIndicator(
                            progress = {
                                if (progress_total > 0) progress_current.toFloat() / progress_total else 0f
                            },
                            modifier = Modifier.fillMaxWidth(),
                            color = colors.accent_blue,
                            trackColor = colors.bg_secondary,
                        )
                        Text(
                            text = stringResource(
                                R.string.alias_import_progress,
                                progress_current,
                                progress_total,
                            ),
                            color = colors.text_secondary,
                            fontSize = 13.sp,
                        )
                    }

                    ImportStep.Done -> {
                        Text(
                            text = stringResource(R.string.alias_import_summary_created, created_count),
                            color = colors.success,
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Medium,
                        )
                        if (skipped_count > 0) {
                            Text(
                                text = stringResource(R.string.alias_import_summary_skipped, skipped_count),
                                color = colors.warning,
                                fontSize = 13.sp,
                            )
                        }
                        if (failed_count > 0) {
                            Text(
                                text = stringResource(R.string.alias_import_summary_failed, failed_count),
                                color = colors.danger,
                                fontSize = 13.sp,
                            )
                        }
                    }
                }

                error_message?.let {
                    Text(text = it, color = colors.danger, fontSize = 13.sp)
                }
            }
        },
        footer = {
            when (step) {
                ImportStep.Select -> {
                    AsterDialogOutlineButton(
                        label = stringResource(R.string.cancel),
                        onClick = on_dismiss,
                    )
                }

                ImportStep.Preview -> {
                    AsterDialogOutlineButton(
                        label = stringResource(R.string.back),
                        onClick = {
                            step = ImportStep.Select
                            picked_file = null
                            parsed_rows = emptyList()
                            preview_rows = emptyList()
                            selected_indices = emptySet()
                            update_existing = false
                            error_message = null
                        },
                    )
                    AsterDialogPrimaryButton(
                        label =
                            pluralStringResource(
                                R.plurals.alias_import_confirm,
                                action_rows.size,
                                action_rows.size,
                            ),
                        enabled = action_rows.isNotEmpty(),
                        onClick = {
                            val to_create = action_rows.filter { it.status == ImportRowStatus.WillImport }
                            val to_update = if (update_existing) {
                                action_rows.filter { it.status == ImportRowStatus.Exists && it.existing_id != null }
                            } else {
                                emptyList()
                            }
                            skipped_count = preview_rows.size - to_create.size - to_update.size
                            progress_total = to_create.size + to_update.size
                            progress_current = 0
                            step = ImportStep.Progress
                            scope.launch {
                                val (created, failed) = vm.import_aliases(to_create, to_update) { done ->
                                    progress_current = done
                                }
                                created_count = created
                                failed_count = failed
                                step = ImportStep.Done
                            }
                        },
                    )
                }

                ImportStep.Progress -> Unit

                ImportStep.Done -> {
                    AsterDialogPrimaryButton(
                        label = stringResource(R.string.done),
                        onClick = on_dismiss,
                    )
                }
            }
        },
    )
}
