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
import android.content.Intent
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
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
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.FileProvider
import androidx.compose.material3.Text
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream
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
import org.astermail.android.design.components.AsterSwitch
import org.astermail.android.settings.AliasExportSource
import org.astermail.android.settings.SettingsUiState
import org.astermail.android.settings.build_alias_rows
import org.astermail.android.settings.build_csv
import org.astermail.android.settings.build_directory_rows
import org.astermail.android.settings.build_domain_address_rows
import org.astermail.android.settings.build_ghost_rows
import org.astermail.android.settings.columns_for
import org.astermail.android.settings.export_file_name
import org.astermail.android.settings.is_exportable_alias
import org.astermail.android.settings.is_exportable_domain_address
import org.astermail.android.settings.is_exportable_ghost

private val SOURCE_ORDER = listOf(
    AliasExportSource.Aliases,
    AliasExportSource.DomainAddresses,
    AliasExportSource.Directories,
    AliasExportSource.Ghost,
)

@Composable
private fun source_label(source: AliasExportSource): String = when (source) {
    AliasExportSource.Aliases -> stringResource(R.string.alias_export_source_aliases)
    AliasExportSource.DomainAddresses ->
        stringResource(R.string.alias_export_source_domain_addresses)
    AliasExportSource.Directories -> stringResource(R.string.alias_export_source_directories)
    AliasExportSource.Ghost -> stringResource(R.string.alias_export_source_ghost)
}

private fun export_date_stamp(): String =
    SimpleDateFormat("yyyy-MM-dd", Locale.US).format(Date())

private fun csv_for(source: AliasExportSource, state: SettingsUiState): String {
    val columns = columns_for(source)
    val rows = when (source) {
        AliasExportSource.Aliases ->
            build_alias_rows(state.aliases.filter { is_exportable_alias(it) }, columns)
        AliasExportSource.DomainAddresses ->
            build_domain_address_rows(
                state.custom_domain_addresses.filter { is_exportable_domain_address(it) },
                columns,
            )
        AliasExportSource.Directories -> build_directory_rows(state.directories, columns)
        AliasExportSource.Ghost ->
            build_ghost_rows(state.ghost_aliases.filter { is_exportable_ghost(it) }, columns)
    }

    return build_csv(columns, rows)
}

private fun source_count(source: AliasExportSource, state: SettingsUiState): Int = when (source) {
    AliasExportSource.Aliases -> state.aliases.count { is_exportable_alias(it) }
    AliasExportSource.DomainAddresses ->
        state.custom_domain_addresses.count { is_exportable_domain_address(it) }
    AliasExportSource.Directories -> state.directories.size
    AliasExportSource.Ghost -> state.ghost_aliases.count { is_exportable_ghost(it) }
}

private fun write_export_archive(
    context: Context,
    state: SettingsUiState,
    sources: List<AliasExportSource>,
): File {
    val date_stamp = export_date_stamp()
    val export_dir = File(context.cacheDir, "exports").apply { mkdirs() }
    val archive = File(export_dir, "aster-aliases-export-$date_stamp.zip")
    val stream = ZipOutputStream(archive.outputStream().buffered())

    try {
        for (source in sources) {
            stream.putNextEntry(ZipEntry(export_file_name(source, date_stamp)))
            stream.write(csv_for(source, state).toByteArray(Charsets.UTF_8))
            stream.closeEntry()
        }
    } finally {
        runCatching { stream.close() }
    }

    return archive
}

private fun share_export_archive(context: Context, archive: File) {
    val uri = FileProvider.getUriForFile(
        context,
        "${context.packageName}.fileprovider",
        archive,
    )
    val intent = Intent(Intent.ACTION_SEND).apply {
        type = "application/zip"
        putExtra(Intent.EXTRA_STREAM, uri)
        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    }

    org.astermail.android.ui.common.start_external_intent(
        context,
        Intent.createChooser(intent, context.getString(R.string.alias_export_title))
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
    )
}

@Composable
fun alias_export_dialog(
    state: SettingsUiState,
    on_dismiss: () -> Unit,
    on_load_directories: () -> Unit,
    on_load_ghost_aliases: () -> Unit,
) {
    val colors = AsterMaterial.colors
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var confirming by remember { mutableStateOf(false) }
    var exporting by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    var selected by remember {
        mutableStateOf(setOf(AliasExportSource.Aliases))
    }

    LaunchedEffect(Unit) {
        on_load_directories()
        on_load_ghost_aliases()
    }

    val available = SOURCE_ORDER.filter { source_count(it, state) > 0 }
    val effective = selected.intersect(available.toSet())
    val total_rows = effective.sumOf { source_count(it, state) }
    val date_stamp = export_date_stamp()

    AsterDialog(
        on_dismiss = on_dismiss,
        title = stringResource(R.string.alias_export_title),
        message = if (confirming) {
            stringResource(R.string.alias_export_confirm_description)
        } else {
            stringResource(R.string.alias_export_description)
        },
        body = {
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(AsterSpacing.sm),
            ) {
                if (!confirming) {
                    for (source in SOURCE_ORDER) {
                        val count = source_count(source, state)
                        val enabled = count > 0

                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(SquircleShape(12.dp))
                                .background(colors.bg_secondary)
                                .clickable(enabled = enabled) {
                                    selected = if (selected.contains(source)) {
                                        selected - source
                                    } else {
                                        selected + source
                                    }
                                }
                                .padding(AsterSpacing.md),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = source_label(source),
                                    color = if (enabled) colors.text_primary else colors.text_muted,
                                    fontSize = 14.sp,
                                    fontWeight = FontWeight.Medium,
                                )
                                Text(
                                    text =
                                        pluralStringResource(
                                            R.plurals.alias_export_source_count,
                                            count,
                                            count,
                                        ),
                                    color = colors.text_tertiary,
                                    fontSize = 12.sp,
                                )
                            }
                            AsterSwitch(
                                checked = enabled && selected.contains(source),
                                onCheckedChange = { checked ->
                                    selected = if (checked) selected + source else selected - source
                                },
                                enabled = enabled,
                            )
                        }
                    }
                } else {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(SquircleShape(12.dp))
                            .background(colors.warning.copy(alpha = 0.12f))
                            .padding(AsterSpacing.md),
                        verticalArrangement = Arrangement.spacedBy(AsterSpacing.xs),
                    ) {
                        Text(
                            text = stringResource(R.string.alias_export_warning_title),
                            color = colors.warning,
                            fontSize = 14.sp,
                            fontWeight = FontWeight.SemiBold,
                        )
                        Text(
                            text = stringResource(R.string.alias_export_warning_body),
                            color = colors.text_secondary,
                            fontSize = 13.sp,
                        )
                    }

                    Text(
                        text = stringResource(
                            R.string.alias_export_summary,
                            pluralStringResource(
                                R.plurals.alias_export_entries,
                                total_rows,
                                total_rows,
                            ),
                            pluralStringResource(
                                R.plurals.alias_export_files,
                                effective.size,
                                effective.size,
                            ),
                        ),
                        color = colors.text_secondary,
                        fontSize = 13.sp,
                    )

                    for (source in SOURCE_ORDER.filter { effective.contains(it) }) {
                        Text(
                            text = export_file_name(source, date_stamp),
                            color = colors.text_tertiary,
                            fontSize = 12.sp,
                        )
                    }
                }

                error?.let {
                    Text(text = it, color = colors.danger, fontSize = 13.sp)
                }
            }
        },
        footer = {
            AsterDialogOutlineButton(
                label = if (confirming) {
                    stringResource(R.string.back)
                } else {
                    stringResource(R.string.cancel)
                },
                onClick = {
                    if (confirming) {
                        error = null
                        confirming = false
                    } else {
                        on_dismiss()
                    }
                },
                enabled = !exporting,
            )
            AsterDialogPrimaryButton(
                label = if (confirming) {
                    stringResource(R.string.alias_export_download)
                } else {
                    stringResource(R.string.next)
                },
                enabled = effective.isNotEmpty() && !exporting,
                is_loading = exporting,
                onClick = {
                    if (!confirming) {
                        error = null
                        confirming = true
                    } else if (!exporting) {
                        error = null
                        exporting = true
                        val ordered = SOURCE_ORDER.filter { effective.contains(it) }
                        scope.launch {
                            val archive = withContext(Dispatchers.IO) {
                                runCatching { write_export_archive(context, state, ordered) }
                            }
                            val shared = archive.mapCatching { share_export_archive(context, it) }
                            exporting = false
                            if (shared.isSuccess) {
                                on_dismiss()
                            } else {
                                error = context.getString(R.string.alias_export_failed)
                            }
                        }
                    }
                },
            )
        },
    )
}
