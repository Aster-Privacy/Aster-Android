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

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material3.CircularProgressIndicator
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import org.astermail.android.R
import org.astermail.android.design.AsterMaterial
import org.astermail.android.design.AsterSpacing
import org.astermail.android.design.components.AsterCard
import org.astermail.android.design.components.AsterDivider
import org.astermail.android.design.components.AsterIconButton
import org.astermail.android.design.components.AsterSecondaryButton
import org.astermail.android.settings.SettingsViewModel
import org.astermail.android.ui.upgrade.UpgradeInlineCard
import org.astermail.android.ui.upgrade.UpgradeLimitKey

@Composable
fun AliasesScreen(
    on_back: () -> Unit,
    on_open: (id: String) -> Unit = {},
) {
    val vm: SettingsViewModel = hiltViewModel()
    val state by vm.state.collectAsStateWithLifecycle()
    val colors = AsterMaterial.colors
    val context = LocalContext.current
    var pending_delete by remember { mutableStateOf<Pair<String, String>?>(null) }

    LaunchedEffect(state.action_result) {
        val msg = state.action_result ?: return@LaunchedEffect
        android.widget.Toast.makeText(context, msg, android.widget.Toast.LENGTH_SHORT).show()
        vm.clear_action_result()
    }

    LaunchedEffect(Unit) { vm.load_aliases() }

    detail_scaffold(title = stringResource(R.string.settings_aliases), on_back = on_back) {
        UpgradeInlineCard(
            limit_key = UpgradeLimitKey.MaxEmailAliases,
            resource_label = null,
            modifier = Modifier.fillMaxWidth(),
        )
        v_gap(AsterSpacing.md)
        if (state.is_loading && state.aliases.isEmpty()) {
            Box(
                modifier = Modifier.fillMaxWidth().padding(AsterSpacing.xxl),
                contentAlignment = Alignment.Center,
            ) {
                CircularProgressIndicator(color = colors.accent_blue, modifier = Modifier.size(24.dp))
            }
        } else if (state.aliases.isEmpty()) {
            AsterCard(modifier = Modifier.fillMaxWidth()) {
                detail_row(
                    title = stringResource(R.string.no_aliases),
                    subtitle = state.error ?: stringResource(R.string.no_aliases_subtitle),
                )
            }
        } else {
            AsterCard(modifier = Modifier.fillMaxWidth()) {
                state.aliases.forEachIndexed { idx, alias ->
                    detail_row(
                        title = alias.address,
                        subtitle = stringResource(R.string.forwards_to_inbox),
                        trailing = {
                            AsterIconButton(
                                icon = Icons.Outlined.Delete,
                                content_description = stringResource(R.string.delete_alias),
                                onClick = { pending_delete = alias.id to alias.address },
                                tint = colors.danger,
                            )
                        },
                    )
                    if (idx < state.aliases.lastIndex) AsterDivider(modifier = Modifier)
                }
            }
        }
        v_gap(AsterSpacing.lg)
        section_label(stringResource(R.string.custom_domains))
        AsterCard(modifier = Modifier.fillMaxWidth()) {
            Column(modifier = Modifier.padding(AsterSpacing.lg)) {
                Text(
                    text = stringResource(R.string.custom_domains_desktop_only),
                    color = colors.text_primary,
                    fontSize = 15.sp,
                    fontWeight = FontWeight.Medium,
                )
                v_gap(AsterSpacing.xs)
                Text(
                    text = stringResource(R.string.custom_domains_description),
                    color = colors.text_tertiary,
                    fontSize = 13.sp,
                )
            }
        }
        v_gap(AsterSpacing.md)
        AsterSecondaryButton(
            label = stringResource(R.string.open_on_desktop),
            onClick = {
                try {
                    context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("https://app.astermail.org/settings")))
                } catch (_: Throwable) {}
            },
        )
        v_gap(AsterSpacing.xxl)
    }

    pending_delete?.let { (id, address) ->
        org.astermail.android.design.components.AsterDialog(
            on_dismiss = { pending_delete = null },
            title = stringResource(R.string.delete_alias),
            message = stringResource(R.string.delete_alias_confirm_message, address),
            footer = {
                org.astermail.android.design.components.AsterDialogOutlineButton(
                    label = stringResource(R.string.cancel),
                    onClick = { pending_delete = null },
                )
                org.astermail.android.design.components.AsterDialogDestructiveButton(
                    label = stringResource(R.string.delete),
                    onClick = {
                        vm.delete_alias(id)
                        pending_delete = null
                    },
                )
            },
        )
    }
}
