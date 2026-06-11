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

package org.astermail.android.ui.upgrade

import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.AutoAwesome
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.launch
import org.astermail.android.R
import org.astermail.android.api.UpgradeEvent
import org.astermail.android.api.UpgradeEventBus
import org.astermail.android.billing.PlanLimitsViewModel
import org.astermail.android.design.SquircleShape
import org.astermail.android.design.AsterMaterial
import org.astermail.android.design.AsterRadius
import org.astermail.android.design.AsterSpacing
import org.astermail.android.design.components.AsterButton
import org.astermail.android.design.components.AsterGhostButton
import org.astermail.android.design.components.AsterSecondaryButton

private fun limit_key_to_field(key: UpgradeLimitKey): String? = when (key) {
    UpgradeLimitKey.MaxEmailAliases -> "max_email_aliases"
    UpgradeLimitKey.MaxCustomDomains -> "max_custom_domains"
    UpgradeLimitKey.MaxContacts -> "max_contacts"
    UpgradeLimitKey.MaxEmailTemplates -> "max_email_templates"
    UpgradeLimitKey.MaxHtmlSignatures -> "max_html_signatures"
    UpgradeLimitKey.MaxCustomFilters -> "max_custom_filters"
    UpgradeLimitKey.Generic -> null
}

@Composable
private fun resource_label_for(key: UpgradeLimitKey, fallback: String?): String {
    return when (key) {
        UpgradeLimitKey.MaxEmailAliases -> stringResource(R.string.upgrade_resource_aliases)
        UpgradeLimitKey.MaxCustomDomains -> stringResource(R.string.upgrade_resource_domains)
        UpgradeLimitKey.MaxContacts -> stringResource(R.string.upgrade_resource_contacts)
        UpgradeLimitKey.MaxEmailTemplates -> stringResource(R.string.upgrade_resource_templates)
        UpgradeLimitKey.MaxHtmlSignatures -> stringResource(R.string.upgrade_resource_signatures)
        UpgradeLimitKey.MaxCustomFilters -> stringResource(R.string.upgrade_resource_filters)
        UpgradeLimitKey.Generic -> fallback ?: stringResource(R.string.upgrade_resource_generic)
    }
}

private fun format_bytes(bytes: Long): String {
    if (bytes <= 0) return "0 B"
    val units = arrayOf("B", "KB", "MB", "GB", "TB")
    var value = bytes.toDouble()
    var i = 0
    while (value >= 1024 && i < units.size - 1) {
        value /= 1024
        i++
    }
    val rounded = if (value >= 10 || i == 0) {
        value.toLong().toString()
    } else {
        "%.1f".format(java.util.Locale.US, value)
    }
    return "$rounded ${units[i]}"
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun UpgradeHost(on_navigate_to_billing: () -> Unit) {
    LaunchedEffect(Unit) {
        UpgradeEventBus.events.collect { event ->
            when (event) {
                is UpgradeEvent.PlanLimit -> UpgradeStore.show_plan_limit(event.resource, event.message)
                is UpgradeEvent.StorageFull -> UpgradeStore.show_storage_full(event.message)
            }
        }
    }

    val state by UpgradeStore.state.collectAsStateWithLifecycle()
    if (!state.is_open) return

    val sheet_state = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val plan_vm: PlanLimitsViewModel = hiltViewModel()
    val plan_state by plan_vm.state.collectAsStateWithLifecycle()
    LaunchedEffect(state.is_open) { if (state.is_open) plan_vm.load() }

    val colors = AsterMaterial.colors

    ModalBottomSheet(
        onDismissRequest = { UpgradeStore.close() },
        sheetState = sheet_state,
        containerColor = colors.bg_card,
        tonalElevation = 0.dp,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = AsterSpacing.xl)
                .padding(bottom = AsterSpacing.xxl),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = Icons.Outlined.AutoAwesome,
                    contentDescription = null,
                    tint = colors.accent_blue,
                    modifier = Modifier.size(28.dp),
                )
                Spacer(Modifier.width(AsterSpacing.md))
                Text(
                    text = if (state.reason == UpgradeReason.StorageFull) {
                        stringResource(R.string.storage_locked_title)
                    } else {
                        stringResource(R.string.upgrade_modal_title)
                    },
                    color = colors.text_primary,
                    fontSize = 18.sp,
                    fontWeight = FontWeight.SemiBold,
                )
            }
            Spacer(Modifier.height(AsterSpacing.sm))

            val resource_label = resource_label_for(state.limit_key, state.resource_label)
            val plan_name = plan_state.limits?.plan_name ?: stringResource(R.string.plan_name_free)
            val description = when {
                state.reason == UpgradeReason.StorageFull ->
                    stringResource(R.string.storage_locked_description)
                state.limit_key != UpgradeLimitKey.Generic ->
                    stringResource(R.string.upgrade_modal_description_specific, resource_label, plan_name)
                else ->
                    stringResource(R.string.upgrade_modal_description_generic)
            }
            Text(
                text = description,
                color = colors.text_secondary,
                fontSize = 14.sp,
            )
            Spacer(Modifier.height(AsterSpacing.lg))

            val storage = plan_state.limits?.storage
            if (state.reason == UpgradeReason.StorageFull && storage != null) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(SquircleShape(18.dp))
                        .background(colors.bg_tertiary)
                        .border(1.dp, colors.border_secondary, SquircleShape(18.dp))
                        .padding(AsterSpacing.lg),
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            text = stringResource(R.string.usage_storage),
                            color = colors.text_primary,
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Medium,
                        )
                        Text(
                            text = "${format_bytes(storage.used_bytes)} / ${format_bytes(storage.limit_bytes)}",
                            color = if (storage.is_locked) colors.danger else colors.text_secondary,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Medium,
                        )
                    }
                    Spacer(Modifier.height(AsterSpacing.sm))
                    LinearProgressIndicator(
                        progress = { (storage.percentage_used / 100.0).coerceIn(0.0, 1.0).toFloat() },
                        color = if (storage.is_locked) colors.danger else colors.warning,
                        trackColor = colors.border_secondary,
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(6.dp)
                            .clip(RoundedCornerShape(3.dp)),
                    )
                    if (storage.is_locked && storage.days_until_permanent_bounce != null) {
                        Spacer(Modifier.height(AsterSpacing.sm))
                        Text(
                            text = stringResource(
                                R.string.storage_locked_bounce_warning,
                                storage.days_until_permanent_bounce.toString(),
                            ),
                            color = colors.danger,
                            fontSize = 12.sp,
                        )
                    }
                }
                Spacer(Modifier.height(AsterSpacing.lg))
            }

            val limit_field = limit_key_to_field(state.limit_key)
            val limit_info = limit_field?.let { plan_state.limits?.limits?.get(it) }
            if (state.reason != UpgradeReason.StorageFull && limit_info != null) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(SquircleShape(18.dp))
                        .background(colors.bg_tertiary)
                        .border(1.dp, colors.border_secondary, SquircleShape(18.dp))
                        .padding(AsterSpacing.lg),
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            text = resource_label.replaceFirstChar { it.uppercase() },
                            color = colors.text_primary,
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Medium,
                        )
                        val limit_text = if (limit_info.limit == -1) {
                            stringResource(R.string.usage_unlimited)
                        } else {
                            stringResource(
                                R.string.usage_of,
                                limit_info.current.toString(),
                                limit_info.limit.toString(),
                            )
                        }
                        Text(
                            text = limit_text,
                            color = colors.danger,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Medium,
                        )
                    }
                    Spacer(Modifier.height(AsterSpacing.sm))
                    LinearProgressIndicator(
                        progress = {
                            if (limit_info.limit > 0) {
                                (limit_info.current.toFloat() / limit_info.limit.toFloat()).coerceIn(0f, 1f)
                            } else 1f
                        },
                        color = colors.danger,
                        trackColor = colors.border_secondary,
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(6.dp)
                            .clip(RoundedCornerShape(3.dp)),
                    )
                }
                Spacer(Modifier.height(AsterSpacing.lg))
            }

            UpgradePerk(text = stringResource(R.string.upgrade_perk_storage))
            UpgradePerk(text = stringResource(R.string.upgrade_perk_aliases))
            UpgradePerk(text = stringResource(R.string.upgrade_perk_domains))
            UpgradePerk(text = stringResource(R.string.upgrade_perk_features))

            Spacer(Modifier.height(AsterSpacing.xl))

            val cta: () -> Unit = {
                UpgradeStore.close()
                on_navigate_to_billing()
            }

            if (state.reason == UpgradeReason.StorageFull) {
                AsterSecondaryButton(
                    label = stringResource(R.string.upgrade_buy_storage),
                    onClick = cta,
                )
                Spacer(Modifier.height(AsterSpacing.sm))
                AsterButton(
                    label = stringResource(R.string.upgrade_view_plans),
                    onClick = cta,
                )
            } else {
                AsterButton(
                    label = stringResource(R.string.upgrade_view_plans),
                    onClick = cta,
                )
                Spacer(Modifier.height(AsterSpacing.xs))
                Box(
                    modifier = Modifier.fillMaxWidth(),
                    contentAlignment = Alignment.Center,
                ) {
                    AsterGhostButton(
                        label = stringResource(R.string.upgrade_not_now),
                        onClick = { UpgradeStore.close() },
                    )
                }
            }
        }
    }
}

@Composable
private fun UpgradePerk(text: String) {
    val colors = AsterMaterial.colors
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        verticalAlignment = Alignment.Top,
    ) {
        Box(
            modifier = Modifier
                .padding(top = 6.dp)
                .size(6.dp)
                .clip(CircleShape)
                .background(colors.accent_blue),
        )
        Spacer(Modifier.width(AsterSpacing.sm))
        Text(
            text = text,
            color = colors.text_secondary,
            fontSize = 13.sp,
        )
    }
}
