/*
 * Aster Mail Android
 * Copyright (C) 2026 Aster Privacy
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Affero General Public License for more details.
 *
 * You should have received a copy of the GNU Affero General Public License
 * along with this program.  If not, see <https://www.gnu.org/licenses/>.
 */

package org.astermail.android.ui.settings.detail

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import compose.icons.TablerIcons
import compose.icons.tablericons.Check
import compose.icons.tablericons.Minus
import org.astermail.android.R
import org.astermail.android.billing.format_money
import org.astermail.android.design.AsterMaterial
import org.astermail.android.design.AsterSpacing
import org.astermail.android.design.SquircleShape
import org.astermail.android.design.components.AsterButton
import org.astermail.android.design.components.AsterCard
import org.astermail.android.design.components.AsterDragHandle
import org.astermail.android.ui.common.sheet_container_color

internal data class billing_plan_option(
    val code: String,
    val name: String,
    val tagline: String,
    val monthly_cents: Int?,
    val yearly_cents: Int?,
    val is_current: Boolean,
    val is_recommended: Boolean,
    val is_downgrade: Boolean,
    val is_interval_switch: Boolean,
)

private fun money_short(cents: Int, currency: String): String = format_money(cents.toLong(), currency)

internal fun yearly_save_percent(option: billing_plan_option): Int {
    val monthly = option.monthly_cents ?: return 0
    val yearly = option.yearly_cents ?: return 0
    if (monthly <= 0) return 0
    val full = monthly * 12
    return (((full - yearly).toFloat() / full.toFloat()) * 100f).toInt()
}

private fun per_month_cents(option: billing_plan_option, is_yearly: Boolean): Int? =
    if (is_yearly) option.yearly_cents?.let { it / 12 } else option.monthly_cents

@Composable
private fun plan_cta_label(option: billing_plan_option): String = when {
    option.is_current -> stringResource(R.string.current_plan)
    option.is_interval_switch -> stringResource(R.string.switch_to_yearly)
    option.is_downgrade -> stringResource(R.string.downgrade)
    else -> stringResource(R.string.fix_billing_get_plan, option.name)
}

@Composable
internal fun billing_plan_picker(
    options: List<billing_plan_option>,
    billing_interval: String,
    on_interval_change: (String) -> Unit,
    currency: String,
    selected_code: String?,
    on_select: (String) -> Unit,
    busy: Boolean,
    plans_failed: Boolean,
    on_choose: (billing_plan_option) -> Unit,
    on_see_pricing: () -> Unit,
    on_compare: (String) -> Unit,
) {
    val colors = AsterMaterial.colors
    val is_yearly = billing_interval == "year"
    val selected = options.firstOrNull { it.code == selected_code }
    val per_month_unit = stringResource(R.string.fix_billing_per_month_short)
    Column(modifier = Modifier.fillMaxWidth()) {
        AsterCard(modifier = Modifier.fillMaxWidth()) {
            Column(modifier = Modifier.selectableGroup()) {
                options.forEachIndexed { index, option ->
                    if (index > 0) settings_row_gap()
                    val per_month = per_month_cents(option, is_yearly)
                    billing_option_row(
                        title = option.name,
                        subtitle = option.tagline,
                        selected = option.code == selected_code,
                        enabled = !busy,
                        title_note = when {
                            option.is_current -> stringResource(R.string.current_plan)
                            option.is_recommended -> stringResource(R.string.fix_billing_plan_recommended)
                            else -> null
                        },
                        title_note_color = if (option.is_current) colors.text_tertiary else colors.accent_blue,
                        on_click = { on_select(option.code) },
                        trailing = {
                            if (per_month != null) {
                                billing_price_column(
                                    amount = money_short(per_month, currency),
                                    unit = per_month_unit,
                                    note = null,
                                    enabled = !busy,
                                )
                            } else {
                                Text(
                                    text = stringResource(R.string.see_pricing),
                                    color = colors.text_tertiary,
                                    fontSize = 13.sp,
                                )
                            }
                        },
                    )
                }
            }
        }
        val price_plan = selected ?: options.firstOrNull { it.is_recommended } ?: options.firstOrNull()
        if (price_plan != null && price_plan.monthly_cents != null && price_plan.yearly_cents != null) {
            Spacer(Modifier.height(AsterSpacing.md))
            val save = yearly_save_percent(price_plan)
            AsterCard(modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.selectableGroup()) {
                    billing_option_row(
                        title = stringResource(R.string.billing_pay_yearly),
                        subtitle = stringResource(R.string.billing_billed_yearly_total, money_short(price_plan.yearly_cents, currency)),
                        selected = is_yearly,
                        enabled = !busy,
                        title_note = if (save > 0) stringResource(R.string.save_percent, save) else null,
                        title_note_color = colors.success,
                        on_click = { on_interval_change("year") },
                        trailing = {
                            billing_price_column(
                                amount = money_short(price_plan.yearly_cents / 12, currency),
                                unit = per_month_unit,
                                note = null,
                                enabled = !busy,
                            )
                        },
                    )
                    settings_row_gap()
                    billing_option_row(
                        title = stringResource(R.string.billing_pay_monthly),
                        subtitle = stringResource(R.string.billing_billed_monthly),
                        selected = !is_yearly,
                        enabled = !busy,
                        on_click = { on_interval_change("month") },
                        trailing = {
                            billing_price_column(
                                amount = money_short(price_plan.monthly_cents, currency),
                                unit = per_month_unit,
                                note = null,
                                enabled = !busy,
                            )
                        },
                    )
                }
            }
        }
        Spacer(Modifier.height(AsterSpacing.lg))
        if (selected == null) {
            Text(
                text = stringResource(R.string.billing_plan_choose_hint),
                color = colors.text_tertiary,
                fontSize = 13.sp,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth().padding(vertical = AsterSpacing.sm),
            )
        } else {
            val price_known = (if (is_yearly) selected.yearly_cents else selected.monthly_cents) != null
            when {
                selected.is_current -> billing_cta_button(
                    label = stringResource(R.string.current_plan),
                    enabled = false,
                    filled = false,
                    on_click = {},
                )
                !price_known -> billing_cta_button(
                    label = stringResource(R.string.see_pricing),
                    enabled = plans_failed,
                    filled = false,
                    on_click = on_see_pricing,
                )
                else -> billing_cta_button(
                    label = plan_cta_label(selected),
                    enabled = !busy,
                    filled = !selected.is_downgrade,
                    on_click = { on_choose(selected) },
                )
            }
        }
        Spacer(Modifier.height(AsterSpacing.xs))
        billing_link_row(
            text = stringResource(R.string.billing_compare_title),
            on_click = { on_compare(selected_code ?: options.firstOrNull()?.code.orEmpty()) },
        )
    }
}

private data class compare_row(val label: String, val values: List<String?>)

@Composable
private fun individual_compare_rows(): List<compare_row> {
    val on = billing_included_marker
    val unlimited = stringResource(R.string.usage_unlimited)
    return listOf(
        compare_row(stringResource(R.string.special_offer_compare_storage), listOf("50 GB", "500 GB", "5 TB")),
        compare_row(stringResource(R.string.special_offer_compare_aliases), listOf("15", unlimited, unlimited)),
        compare_row(stringResource(R.string.special_offer_compare_domains), listOf("5", "30", unlimited)),
        compare_row(stringResource(R.string.special_offer_compare_attachments), listOf("50 MB", "100 MB", "250 MB")),
        compare_row(stringResource(R.string.settings_plan_bullet_tracker_protection), listOf(on, on, on)),
        compare_row(stringResource(R.string.billing_compare_external_accounts), listOf(on, on, on)),
        compare_row(stringResource(R.string.settings_plan_bullet_priority_support), listOf(on, on, on)),
        compare_row(stringResource(R.string.settings_plan_bullet_encrypted_export), listOf(null, on, on)),
        compare_row(stringResource(R.string.settings_plan_bullet_protected_folders), listOf(null, on, on)),
        compare_row(stringResource(R.string.settings_plan_bullet_key_rotation), listOf(null, on, on)),
        compare_row(stringResource(R.string.settings_plan_bullet_receipt_tracking), listOf(null, null, on)),
        compare_row(stringResource(R.string.settings_plan_bullet_early_access), listOf(null, null, on)),
        compare_row(stringResource(R.string.settings_plan_bullet_dedicated_support), listOf(null, null, on)),
    )
}

@Composable
private fun family_compare_rows(): List<compare_row> {
    val on = billing_included_marker
    val unlimited = stringResource(R.string.usage_unlimited)
    return listOf(
        compare_row(stringResource(R.string.billing_compare_shared_storage), listOf("1 TB", "3 TB")),
        compare_row(stringResource(R.string.billing_compare_members), listOf("2", stringResource(R.string.billing_up_to, 6))),
        compare_row(stringResource(R.string.special_offer_compare_aliases), listOf(unlimited, unlimited)),
        compare_row(stringResource(R.string.settings_plan_bullet_shared_aliases), listOf(on, on)),
        compare_row(stringResource(R.string.special_offer_compare_domains), listOf("30", "30")),
        compare_row(stringResource(R.string.settings_plan_bullet_tracker_protection), listOf(on, on)),
        compare_row(stringResource(R.string.billing_compare_external_accounts), listOf(on, on)),
        compare_row(stringResource(R.string.settings_plan_bullet_priority_support), listOf(on, on)),
    )
}

@Composable
@OptIn(ExperimentalMaterial3Api::class)
internal fun billing_compare_sheet(
    individual_options: List<billing_plan_option>,
    family_options: List<billing_plan_option>,
    initial_type: String,
    initial_code: String,
    currency: String,
    billing_interval: String,
    busy: Boolean,
    on_choose: (billing_plan_option) -> Unit,
    on_dismiss: () -> Unit,
) {
    val colors = AsterMaterial.colors
    val sheet_state = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    var plan_type by remember(initial_type) { mutableStateOf(initial_type) }
    var code by remember(initial_code) { mutableStateOf(initial_code) }
    val options = if (plan_type == "family") family_options else individual_options
    val rows = if (plan_type == "family") family_compare_rows() else individual_compare_rows()
    val selected = options.firstOrNull { it.code == code } ?: options.firstOrNull() ?: return
    val is_yearly = billing_interval == "year"
    val column_width: Dp = if (options.size > 2) 62.dp else 84.dp
    ModalBottomSheet(
        onDismissRequest = on_dismiss,
        sheetState = sheet_state,
        shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp),
        containerColor = sheet_container_color(colors),
        tonalElevation = 0.dp,
        dragHandle = { AsterDragHandle() },
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(max = 760.dp)
                .padding(horizontal = AsterSpacing.xl)
                .navigationBarsPadding(),
        ) {
            Text(
                text = stringResource(R.string.billing_compare_title),
                color = colors.text_primary,
                fontSize = 20.sp,
                fontWeight = FontWeight.Bold,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth(),
            )
            Spacer(Modifier.height(AsterSpacing.lg))
            billing_segmented(
                value = plan_type,
                options = listOf(
                    switcher_option(id = "individual", label = stringResource(R.string.billing_plan_type_individual)),
                    switcher_option(id = "family", label = stringResource(R.string.billing_plan_type_family)),
                ),
                on_change = { type ->
                    plan_type = type
                    val next = if (type == "family") family_options else individual_options
                    code = next.firstOrNull { it.is_recommended && !it.is_current }?.code
                        ?: next.firstOrNull { !it.is_current }?.code
                        ?: next.firstOrNull()?.code.orEmpty()
                },
            )
            Column(
                modifier = Modifier
                    .weight(1f, fill = false)
                    .verticalScroll(rememberScrollState()),
            ) {
                Spacer(Modifier.height(AsterSpacing.lg))
                Row(
                    modifier = Modifier.fillMaxWidth().selectableGroup(),
                    verticalAlignment = Alignment.Bottom,
                ) {
                    Spacer(Modifier.weight(1f))
                    options.forEach { option ->
                        compare_header_cell(
                            option = option,
                            per_month = per_month_cents(option, is_yearly)?.let { money_short(it, currency) },
                            highlighted = option.code == selected.code,
                            width = column_width,
                            on_click = { code = option.code },
                        )
                    }
                }
                Spacer(Modifier.height(AsterSpacing.xs))
                rows.forEach { row ->
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(vertical = 12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            text = row.label,
                            color = colors.text_primary,
                            fontSize = 14.sp,
                            lineHeight = 18.sp,
                            modifier = Modifier.weight(1f).padding(end = AsterSpacing.sm),
                        )
                        options.forEachIndexed { index, option ->
                            compare_cell(
                                value = row.values.getOrNull(index),
                                highlighted = option.code == selected.code,
                                width = column_width,
                            )
                        }
                    }
                }
                Spacer(Modifier.height(AsterSpacing.md))
            }
            Spacer(Modifier.height(AsterSpacing.sm))
            val price_known = (if (is_yearly) selected.yearly_cents else selected.monthly_cents) != null
            AsterButton(
                label = plan_cta_label(selected),
                onClick = { on_choose(selected) },
                enabled = !busy && !selected.is_current && price_known,
                modifier = Modifier.fillMaxWidth(),
            )
            Spacer(Modifier.height(AsterSpacing.lg))
        }
    }
}

@Composable
private fun compare_header_cell(
    option: billing_plan_option,
    per_month: String?,
    highlighted: Boolean,
    width: Dp,
    on_click: () -> Unit,
) {
    val colors = AsterMaterial.colors
    val shape = SquircleShape(10.dp)
    Column(
        modifier = Modifier
            .width(width)
            .clip(shape)
            .selectable(selected = highlighted, role = Role.RadioButton, onClick = on_click)
            .padding(vertical = AsterSpacing.xs),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(
            text = option.name,
            color = if (highlighted) colors.accent_blue else colors.text_secondary,
            fontSize = 13.sp,
            fontWeight = FontWeight.SemiBold,
            textAlign = TextAlign.Center,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        Text(
            text = per_month?.let { it + stringResource(R.string.fix_billing_per_month_short) } ?: "",
            color = if (highlighted) colors.accent_blue else colors.text_tertiary,
            fontSize = 11.sp,
            textAlign = TextAlign.Center,
            maxLines = 1,
        )
    }
}

@Composable
private fun compare_cell(value: String?, highlighted: Boolean, width: Dp) {
    val colors = AsterMaterial.colors
    Box(modifier = Modifier.width(width), contentAlignment = Alignment.Center) {
        when (value) {
            null -> Icon(
                imageVector = TablerIcons.Minus,
                contentDescription = null,
                tint = colors.text_muted,
                modifier = Modifier.size(16.dp),
            )
            billing_included_marker -> Icon(
                imageVector = TablerIcons.Check,
                contentDescription = null,
                tint = if (highlighted) colors.accent_blue else colors.text_secondary,
                modifier = Modifier.size(18.dp),
            )
            else -> Text(
                text = value,
                color = if (highlighted) colors.accent_blue else colors.text_secondary,
                fontSize = 13.sp,
                fontWeight = if (highlighted) FontWeight.SemiBold else FontWeight.Normal,
                textAlign = TextAlign.Center,
                maxLines = 1,
            )
        }
    }
}
