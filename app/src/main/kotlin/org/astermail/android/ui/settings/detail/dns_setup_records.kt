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
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import compose.icons.TablerIcons
import compose.icons.tablericons.ChevronRight
import compose.icons.tablericons.Copy
import compose.icons.tablericons.ExternalLink
import org.astermail.android.R
import org.astermail.android.api.settings.DnsRecord
import org.astermail.android.design.AsterMaterial
import org.astermail.android.design.AsterShapes
import org.astermail.android.design.AsterSpacing
import org.astermail.android.design.components.AsterCard
import org.astermail.android.design.components.AsterIconButton
import org.astermail.android.design.field_surface_color
import org.astermail.android.settings.domains.DnsProvider
import org.astermail.android.settings.domains.dns_provider_dashboard_url
import org.astermail.android.settings.domains.domain_record_caveat_res
import org.astermail.android.settings.domains.format_host_for_provider
import org.astermail.android.ui.common.aster_modal_sheet
import org.astermail.android.ui.common.remember_sheet_dismiss
import org.astermail.android.ui.common.sheet_container_color

private const val dns_provider_prefs = "domain_dns_provider"

internal fun load_dns_provider(context: Context, domain_id: String): DnsProvider = try {
    DnsProvider.from_id(
        context.getSharedPreferences(dns_provider_prefs, Context.MODE_PRIVATE).getString(domain_id, null),
    )
} catch (_: Throwable) {
    DnsProvider.other
}

internal fun save_dns_provider(context: Context, domain_id: String, provider: DnsProvider) {
    try {
        context.getSharedPreferences(dns_provider_prefs, Context.MODE_PRIVATE)
            .edit()
            .putString(domain_id, provider.name)
            .apply()
    } catch (_: Throwable) {
    }
}

@Composable
internal fun remember_dns_provider(domain_id: String): Pair<DnsProvider, (DnsProvider) -> Unit> {
    val context = LocalContext.current
    var provider by rememberSaveable(domain_id) { mutableStateOf(load_dns_provider(context, domain_id)) }
    return provider to { next: DnsProvider ->
        provider = next
        save_dns_provider(context, domain_id, next)
    }
}

@Composable
internal fun dns_provider_label(provider: DnsProvider): String =
    provider.label ?: stringResource(R.string.dns_provider_other)

@Composable
internal fun dns_provider_row(
    domain_name: String,
    provider: DnsProvider,
    on_change: (DnsProvider) -> Unit,
) {
    val context = LocalContext.current
    var picker_open by remember { mutableStateOf(false) }
    val dashboard = remember(provider, domain_name) { dns_provider_dashboard_url(provider, domain_name) }
    settings_group {
        detail_row(
            title = stringResource(R.string.dns_provider_title),
            subtitle = stringResource(R.string.dns_provider_subtitle),
            value = dns_provider_label(provider),
            on_click = { picker_open = true },
            trailing = {
                Icon(
                    imageVector = TablerIcons.ChevronRight,
                    contentDescription = null,
                    tint = AsterMaterial.colors.text_tertiary,
                    modifier = Modifier.size(18.dp),
                )
            },
        )
        if (dashboard != null) {
            detail_row(
                title = stringResource(R.string.dns_provider_open_dashboard, dns_provider_label(provider)),
                icon = TablerIcons.ExternalLink,
                on_click = { open_url(context, dashboard) },
            )
        }
    }
    if (picker_open) {
        dns_provider_picker_sheet(
            selected = provider,
            on_dismiss = { picker_open = false },
            on_select = {
                on_change(it)
                picker_open = false
            },
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun dns_provider_picker_sheet(
    selected: DnsProvider,
    on_dismiss: () -> Unit,
    on_select: (DnsProvider) -> Unit,
) {
    val colors = AsterMaterial.colors
    val sheet_state = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val dismiss = remember_sheet_dismiss(sheet_state, on_dismiss)
    aster_modal_sheet(
        on_dismiss_request = on_dismiss,
        sheet_state = sheet_state,
        container_color = sheet_container_color(colors),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .navigationBarsPadding()
                .padding(horizontal = AsterSpacing.lg, vertical = AsterSpacing.sm),
        ) {
            Text(
                text = stringResource(R.string.dns_provider_picker_title),
                color = colors.text_primary,
                fontSize = 17.sp,
                fontWeight = FontWeight.SemiBold,
            )
            v_gap(4.dp)
            Text(
                text = stringResource(R.string.dns_provider_picker_hint),
                color = colors.text_tertiary,
                fontSize = 13.sp,
            )
            v_gap(AsterSpacing.md)
            AsterCard(modifier = Modifier.fillMaxWidth()) {
                Column {
                    DnsProvider.entries.forEach { provider ->
                        choice_option_row(
                            label = dns_provider_label(provider),
                            selected = provider == selected,
                            on_click = {
                                on_select(provider)
                                dismiss()
                            },
                        )
                    }
                }
            }
            v_gap(AsterSpacing.lg)
        }
    }
}

@Composable
internal fun dns_setup_record_card(
    record: DnsRecord,
    domain_name: String,
    provider: DnsProvider,
    modifier: Modifier = Modifier,
) {
    val colors = AsterMaterial.colors
    val context = LocalContext.current
    val host = remember(record.name, domain_name, provider) {
        format_host_for_provider(record.name, domain_name, provider)
    }
    val status_text = when {
        record.verified -> stringResource(R.string.domain_record_found)
        record.required -> stringResource(R.string.domain_record_required)
        else -> stringResource(R.string.domain_record_recommended)
    }
    val status_tone = when {
        record.verified -> colors.success
        record.required -> colors.warning
        else -> colors.text_tertiary
    }
    Column(
        modifier = modifier
            .fillMaxWidth()
            .background(field_surface_color(colors), AsterShapes.control)
            .padding(start = AsterSpacing.md, end = AsterSpacing.xs, top = AsterSpacing.sm, bottom = AsterSpacing.sm),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            dns_type_chip(record.type)
            record.priority?.let { priority ->
                Spacer(Modifier.width(AsterSpacing.sm))
                Text(
                    text = stringResource(R.string.dns_field_priority_value, priority),
                    color = colors.text_tertiary,
                    fontSize = 12.sp,
                )
            }
            Spacer(Modifier.weight(1f))
            tone_badge(text = status_text, tone = status_tone)
            Spacer(Modifier.width(AsterSpacing.sm))
        }
        v_gap(AsterSpacing.xs)
        copyable_dns_line(
            label = stringResource(R.string.domain_record_host),
            value = host,
            empty_hint = stringResource(R.string.dns_provider_leave_blank),
            on_copy = { copy_dns_value(context, record.type, host) },
        )
        copyable_dns_line(
            label = stringResource(R.string.domain_record_value),
            value = record.value,
            on_copy = { copy_dns_value(context, record.type, record.value) },
        )
        domain_record_caveat_res(record.caveat_key)?.let { res ->
            v_gap(AsterSpacing.xs)
            Text(
                text = stringResource(res),
                color = colors.text_tertiary,
                fontSize = 12.sp,
                modifier = Modifier.padding(end = AsterSpacing.sm),
            )
        }
    }
}

@Composable
internal fun dns_type_chip(type: String) {
    val colors = AsterMaterial.colors
    Text(
        text = type.uppercase(),
        color = colors.text_primary,
        fontSize = 12.sp,
        fontWeight = FontWeight.SemiBold,
        fontFamily = FontFamily.Monospace,
        modifier = Modifier
            .background(colors.bg_tertiary, AsterShapes.item)
            .padding(horizontal = 8.dp, vertical = 3.dp),
    )
}

@Composable
internal fun copyable_dns_line(
    label: String,
    value: String,
    on_copy: () -> Unit,
    empty_hint: String? = null,
) {
    val colors = AsterMaterial.colors
    var expanded by remember(value) { mutableStateOf(false) }
    val copy_label = stringResource(R.string.dns_zone_copy_field, label)
    Row(
        modifier = Modifier.fillMaxWidth().padding(top = 2.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(AsterSpacing.sm),
    ) {
        Text(
            text = label,
            color = colors.text_tertiary,
            fontSize = 11.sp,
            modifier = Modifier.width(52.dp),
        )
        if (value.isEmpty() && empty_hint != null) {
            Text(
                text = empty_hint,
                color = colors.text_tertiary,
                fontSize = 12.sp,
                fontStyle = FontStyle.Italic,
                modifier = Modifier.weight(1f),
            )
        } else {
            Text(
                text = value,
                color = colors.text_secondary,
                fontSize = 12.sp,
                fontFamily = FontFamily.Monospace,
                maxLines = if (expanded) Int.MAX_VALUE else 2,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier
                    .weight(1f)
                    .clickable(role = Role.Button) { expanded = !expanded },
            )
        }
        AsterIconButton(
            icon = TablerIcons.Copy,
            content_description = copy_label,
            onClick = on_copy,
            enabled = value.isNotEmpty() || empty_hint == null,
            icon_size = 18,
        )
    }
}
