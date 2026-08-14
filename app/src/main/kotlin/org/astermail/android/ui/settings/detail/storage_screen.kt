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
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import org.astermail.android.R
import org.astermail.android.billing.BillingViewModel
import org.astermail.android.design.AsterMaterial
import org.astermail.android.design.AsterSpacing
import compose.icons.TablerIcons
import compose.icons.tablericons.AlertTriangle
import org.astermail.android.design.components.AsterAlertDialog
import org.astermail.android.design.components.AsterButton
import org.astermail.android.ui.upgrade.UpgradeStore
import org.astermail.android.design.components.AsterCard
import org.astermail.android.design.components.DialogConfirmStyle
import org.astermail.android.settings.SettingsViewModel
import org.astermail.android.settings.shared_settings_view_model

internal fun format_bytes(bytes: Long): String {
    if (bytes < 1024) return "$bytes B"
    val kb = bytes / 1024.0
    if (kb < 1024) return "%.1f KB".format(kb)
    val mb = kb / 1024.0
    if (mb < 1024) return "%.1f MB".format(mb)
    val gb = mb / 1024.0
    if (gb < 1024) return "%.1f GB".format(gb)
    val tb = gb / 1024.0
    return "%.1f TB".format(tb)
}

@Composable
fun StorageScreen(
    on_back: () -> Unit,
    on_open: (id: String) -> Unit = {},
    on_open_folder: (folder_id: String, folder_name: String) -> Unit = { _, _ -> },
) {
    val vm: SettingsViewModel = shared_settings_view_model()
    val state by vm.state.collectAsStateWithLifecycle()
    val mail_vm: org.astermail.android.mail.MailViewModel = hiltViewModel()
    val inbox_state by mail_vm.inbox_state.collectAsStateWithLifecycle()
    val is_emptying_spam by mail_vm.emptying_spam_state.collectAsStateWithLifecycle()
    val billing_vm: BillingViewModel = hiltViewModel()
    val billing_state by billing_vm.state.collectAsStateWithLifecycle()
    val colors = AsterMaterial.colors
    val context = LocalContext.current

    var load_requested by remember { mutableStateOf(false) }
    var show_empty_trash_confirm by remember { mutableStateOf(false) }
    var show_empty_spam_confirm by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) {
        load_requested = true
        vm.load_storage()
        vm.load_subscription(force = false)
        mail_vm.load_stats()
    }

    LaunchedEffect(Unit) {
        mail_vm.toast_events.collect { event ->
            android.widget.Toast.makeText(context, event.message, android.widget.Toast.LENGTH_SHORT).show()
        }
    }

    LaunchedEffect(billing_state.portal_url) {
        val url = billing_state.portal_url ?: return@LaunchedEffect
        runCatching { context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url))) }
        billing_vm.consume_portal_url()
    }

    val lifecycle_owner = LocalLifecycleOwner.current
    DisposableEffect(lifecycle_owner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) billing_vm.on_resume()
        }
        lifecycle_owner.lifecycle.addObserver(observer)
        onDispose { lifecycle_owner.lifecycle.removeObserver(observer) }
    }

    detail_scaffold(title = stringResource(R.string.storage_title), on_back = on_back) {
        val storage = state.storage
        val stats = inbox_state.stats
        val awaiting_first_load = storage == null && stats == null &&
            (!load_requested || state.is_loading || state.error == null)
        if (awaiting_first_load || (state.is_loading && storage == null && stats == null)) {
            Box(
                modifier = Modifier.fillMaxWidth().padding(AsterSpacing.xxl),
                contentAlignment = Alignment.Center,
            ) {
                CircularProgressIndicator(color = colors.accent_blue, modifier = Modifier.size(24.dp))
            }
        } else if (storage == null && stats == null) {
            AsterCard(modifier = Modifier.fillMaxWidth()) {
                detail_row(
                    title = stringResource(R.string.storage_unavailable),
                    subtitle = state.error ?: stringResource(R.string.storage_load_error),
                )
            }
        } else {
            val used_bytes = when {
                storage != null && storage.used_bytes > 0 -> storage.used_bytes
                stats != null && stats.storage_used_bytes > 0 -> stats.storage_used_bytes
                else -> storage?.used_bytes ?: 0L
            }
            val total_bytes = when {
                storage != null && storage.total_bytes > 0 -> storage.total_bytes
                stats != null && stats.storage_total_bytes > 0 -> stats.storage_total_bytes
                else -> storage?.total_bytes ?: 0L
            }
            val used = format_bytes(used_bytes)
            val total = format_bytes(total_bytes)
            val pct_from_api = storage?.percentage_used ?: 0.0
            val fraction = when {
                total_bytes > 0 -> (used_bytes.toFloat() / total_bytes).coerceIn(0f, 1f)
                pct_from_api > 0 -> (pct_from_api / 100.0).toFloat().coerceIn(0f, 1f)
                else -> 0f
            }
            val display_fraction = if (used_bytes > 0) fraction.coerceAtLeast(0.02f) else fraction
            val pct = fraction * 100
            val pct_label = when {
                pct <= 0f -> "0%"
                pct < 0.1f -> "<0.1%"
                pct < 1f -> "%.1f%%".format(pct)
                else -> "${pct.toInt()}%"
            }

            val over_limit = storage?.is_over_limit == true
            val bar_color = when {
                over_limit || fraction >= 0.95f -> colors.danger
                fraction >= 0.8f -> colors.warning
                else -> colors.accent_blue
            }
            val free_bytes = (total_bytes - used_bytes).coerceAtLeast(0L)

            AsterCard(modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(AsterSpacing.lg)) {
                    Text(
                        text = stringResource(R.string.storage_used_of_total, used, total),
                        color = colors.text_primary,
                        fontSize = 17.sp,
                        fontWeight = FontWeight.SemiBold,
                    )
                    Text(
                        text = stringResource(R.string.storage_percent_of_plan, pct_label),
                        color = colors.text_tertiary,
                        fontSize = 13.sp,
                    )
                    Spacer(Modifier.size(AsterSpacing.md))
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(14.dp)
                            .background(colors.bg_secondary, RoundedCornerShape(7.dp)),
                    ) {
                        if (display_fraction > 0f) {
                            Box(
                                modifier = Modifier
                                    .weight(display_fraction)
                                    .fillMaxWidth()
                                    .height(14.dp)
                                    .background(bar_color, RoundedCornerShape(7.dp)),
                            )
                        }
                        if (display_fraction < 1f) {
                            Box(modifier = Modifier.weight(1f - display_fraction))
                        }
                    }
                    Spacer(Modifier.size(AsterSpacing.sm))
                    Row(modifier = Modifier.fillMaxWidth()) {
                        Text(
                            text = stringResource(R.string.storage_used_label, used),
                            color = colors.text_tertiary,
                            fontSize = 12.sp,
                            modifier = Modifier.weight(1f),
                        )
                        Text(
                            text = stringResource(R.string.storage_free_label, format_bytes(free_bytes)),
                            color = colors.text_tertiary,
                            fontSize = 12.sp,
                        )
                    }
                }
            }
            if (over_limit || fraction >= 0.9f) {
                v_gap(AsterSpacing.md)
                AsterCard(modifier = Modifier.fillMaxWidth()) {
                    detail_row(
                        title = stringResource(
                            if (over_limit) R.string.storage_locked_title
                            else R.string.storage_almost_full_title,
                        ),
                        subtitle = stringResource(
                            if (over_limit) R.string.storage_locked_description
                            else R.string.storage_almost_full_body,
                        ),
                        icon = TablerIcons.AlertTriangle,
                    )
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(
                                start = AsterSpacing.md,
                                end = AsterSpacing.md,
                                bottom = AsterSpacing.md,
                            ),
                    ) {
                        AsterButton(
                            label = stringResource(R.string.upgrade),
                            onClick = { UpgradeStore.show_storage_full(null) },
                        )
                    }
                }
            }
            v_gap(AsterSpacing.lg)
            storage_plan_section(
                plan_name = state.subscription?.effective_plan_name,
                total_bytes = total_bytes,
                addon_bytes = storage?.addon_bytes ?: 0L,
                free_bytes = free_bytes,
            )
            storage_distribution_section(stats, on_open_folder)
            storage_mailbox_section(stats, used_bytes)
            storage_cleanup_section(
                trash_count = stats?.trash ?: 0,
                spam_count = stats?.spam ?: 0,
                is_emptying_spam = is_emptying_spam,
                on_empty_trash = { show_empty_trash_confirm = true },
                on_empty_spam = { show_empty_spam_confirm = true },
                on_open_folder = on_open_folder,
            )
            v_gap(AsterSpacing.lg)
            Text(
                text = stringResource(R.string.buy_more_storage_note),
                color = colors.text_tertiary,
                fontSize = 13.sp,
            )
            v_gap(AsterSpacing.sm)
            AsterButton(
                label = stringResource(R.string.buy_more_storage),
                onClick = { on_open("billing_addons") },
            )
        }
        v_gap(AsterSpacing.xxl)
    }

    if (show_empty_trash_confirm) {
        AsterAlertDialog(
            on_dismiss = { show_empty_trash_confirm = false },
            title = stringResource(R.string.empty_trash),
            message = stringResource(R.string.empty_trash_confirm),
            confirm_label = stringResource(R.string.empty_trash),
            cancel_label = stringResource(R.string.cancel),
            confirm_style = DialogConfirmStyle.destructive,
            on_confirm = {
                show_empty_trash_confirm = false
                mail_vm.empty_trash()
            },
        )
    }

    if (show_empty_spam_confirm) {
        AsterAlertDialog(
            on_dismiss = { show_empty_spam_confirm = false },
            title = stringResource(R.string.empty_spam),
            message = stringResource(R.string.empty_spam_confirm),
            confirm_label = stringResource(R.string.empty_spam),
            cancel_label = stringResource(R.string.cancel),
            confirm_style = DialogConfirmStyle.destructive,
            on_confirm = {
                show_empty_spam_confirm = false
                mail_vm.empty_spam()
            },
        )
    }
}
