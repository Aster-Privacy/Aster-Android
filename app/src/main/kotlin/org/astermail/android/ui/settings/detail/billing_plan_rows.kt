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

import android.os.Build
import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.text.BasicText
import androidx.compose.foundation.text.TextAutoSize
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.compose.ui.window.DialogWindowProvider
import androidx.core.view.WindowCompat
import compose.icons.TablerIcons
import compose.icons.tablericons.Check
import compose.icons.tablericons.Minus
import compose.icons.tablericons.X
import org.astermail.android.R
import org.astermail.android.billing.format_money
import org.astermail.android.billing.plan_comparison_feed
import org.astermail.android.billing.plan_comparison_row
import org.astermail.android.design.AsterMaterial
import org.astermail.android.design.AsterShapes
import org.astermail.android.design.AsterSpacing
import org.astermail.android.design.components.AsterButton
import org.astermail.android.design.components.AsterCard
import org.astermail.android.design.components.AsterIconButton
import org.astermail.android.design.components.AsterSecondaryButton

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
    compare_label: String,
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
                    val option_save = yearly_save_percent(option)
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
                                    note = if (is_yearly && option_save > 0 && !option.is_current) {
                                        stringResource(R.string.save_percent, option_save)
                                    } else {
                                        null
                                    },
                                    enabled = !busy,
                                    note_color = colors.success,
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
                selected.is_current -> AsterSecondaryButton(
                    label = stringResource(R.string.current_plan),
                    enabled = false,
                    onClick = {},
                )
                !price_known -> AsterSecondaryButton(
                    label = stringResource(R.string.see_pricing),
                    enabled = plans_failed,
                    onClick = on_see_pricing,
                )
                selected.is_downgrade -> AsterSecondaryButton(
                    label = plan_cta_label(selected),
                    enabled = !busy,
                    onClick = { on_choose(selected) },
                )
                else -> AsterButton(
                    label = plan_cta_label(selected),
                    enabled = !busy,
                    onClick = { on_choose(selected) },
                )
            }
        }
        Spacer(Modifier.height(AsterSpacing.xs))
        billing_link_row(
            text = compare_label,
            on_click = { on_compare(selected_code ?: options.firstOrNull()?.code.orEmpty()) },
        )
    }
}

internal data class compare_column(
    val code: String,
    val name: String,
    val price: String?,
    val option: billing_plan_option?,
    val is_current: Boolean,
)

internal fun compare_columns_for(
    feed: plan_comparison_feed,
    options: List<billing_plan_option>,
    include_free: Boolean,
): List<String> = buildList {
    if (include_free && feed.plan("free") != null) add("free")
    options.forEach { option -> if (feed.plan(option.code) != null) add(option.code) }
}

internal fun compare_row_differs(row: plan_comparison_row, codes: List<String>): Boolean =
    codes.map { row.value_for(it) }.distinct().size > 1


@Composable
internal fun billing_compare_view(
    feed: plan_comparison_feed?,
    individual_options: List<billing_plan_option>,
    family_options: List<billing_plan_option>,
    initial_code: String,
    current_code: String?,
    currency: String,
    billing_interval: String,
    on_interval_change: (String) -> Unit,
    busy: Boolean,
    on_choose: (billing_plan_option) -> Unit,
    on_see_pricing: () -> Unit,
    on_dismiss: () -> Unit,
) {
    val colors = AsterMaterial.colors
    val light_bars = colors.bg_primary.luminance() > 0.5f
    Dialog(
        onDismissRequest = on_dismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false, decorFitsSystemWindows = false),
    ) {
        val view = LocalView.current
        SideEffect {
            val window = (view.parent as? DialogWindowProvider)?.window ?: return@SideEffect
            WindowCompat.setDecorFitsSystemWindows(window, false)
            window.setDimAmount(0f)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) window.isNavigationBarContrastEnforced = false
            WindowCompat.getInsetsController(window, view).apply {
                isAppearanceLightStatusBars = light_bars
                isAppearanceLightNavigationBars = light_bars
            }
            if (Build.VERSION.SDK_INT < Build.VERSION_CODES.VANILLA_ICE_CREAM) {
                @Suppress("DEPRECATION")
                window.navigationBarColor = android.graphics.Color.TRANSPARENT
                @Suppress("DEPRECATION")
                window.statusBarColor = android.graphics.Color.TRANSPARENT
            }
        }
        billing_compare_content(
            feed = feed,
            options = individual_options + family_options,
            initial_code = initial_code,
            current_code = current_code,
            currency = currency,
            billing_interval = billing_interval,
            on_interval_change = on_interval_change,
            busy = busy,
            on_choose = on_choose,
            on_see_pricing = on_see_pricing,
            on_dismiss = on_dismiss,
        )
    }
}

@Composable
private fun billing_compare_content(
    feed: plan_comparison_feed?,
    options: List<billing_plan_option>,
    initial_code: String,
    current_code: String?,
    currency: String,
    billing_interval: String,
    on_interval_change: (String) -> Unit,
    busy: Boolean,
    on_choose: (billing_plan_option) -> Unit,
    on_see_pricing: () -> Unit,
    on_dismiss: () -> Unit,
) {
    val colors = AsterMaterial.colors
    var code by remember(initial_code) { mutableStateOf(initial_code) }
    var differences_only by remember { mutableStateOf(true) }
    val selected = options.firstOrNull { it.code == code }
        ?: options.firstOrNull { it.is_recommended && !it.is_current }
        ?: options.firstOrNull { !it.is_current }
        ?: options.firstOrNull()
    val is_yearly = billing_interval == "year"
    val per_month_unit = stringResource(R.string.fix_billing_per_month_short)
    val free_price = money_short(0, currency)
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(colors.bg_primary)
            .statusBarsPadding()
            .navigationBarsPadding()
            .testTag("billing_compare_view"),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(56.dp)
                .padding(horizontal = AsterSpacing.xs),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            AsterIconButton(
                icon = TablerIcons.X,
                content_description = stringResource(R.string.close),
                onClick = on_dismiss,
                tint = colors.text_primary,
                icon_size = 22,
                modifier = Modifier.testTag("billing_compare_close"),
            )
            Text(
                text = stringResource(R.string.billing_compare_title),
                color = colors.text_primary,
                fontSize = 18.sp,
                fontWeight = FontWeight.SemiBold,
                textAlign = TextAlign.Center,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier
                    .weight(1f)
                    .semantics { heading() },
            )
            Spacer(Modifier.size(48.dp))
        }
        if (feed == null) {
            Spacer(Modifier.height(AsterSpacing.xl))
            billing_link_row(text = stringResource(R.string.see_pricing), on_click = on_see_pricing)
            Spacer(Modifier.weight(1f))
        } else {
            val codes = compare_columns_for(feed, options, include_free = true)
            val columns = codes.map { column_code ->
                val option = options.firstOrNull { it.code == column_code }
                compare_column(
                    code = column_code,
                    name = option?.name ?: feed.plan(column_code)?.name ?: column_code,
                    price = if (column_code == "free") {
                        free_price
                    } else {
                        option?.let { per_month_cents(it, is_yearly) }?.let { money_short(it, currency) }
                    },
                    option = option,
                    is_current = column_code == (current_code ?: "free"),
                )
            }
            val groups = feed.groups_for(codes, include_family = true)
                .map { group ->
                    if (differences_only) group.copy(rows = group.rows.filter { compare_row_differs(it, codes) }) else group
                }
                .filter { it.rows.isNotEmpty() }
            Box(modifier = Modifier.padding(horizontal = AsterSpacing.lg)) {
                AsterCard(modifier = Modifier.fillMaxWidth()) {
                    settings_toggle_row(
                        title = stringResource(R.string.billing_compare_differences_only),
                        checked = differences_only,
                        test_tag = "billing_compare_differences_toggle",
                        on_change = { differences_only = it },
                    )
                }
            }
            Spacer(Modifier.height(AsterSpacing.sm))
            BoxWithConstraints(modifier = Modifier.weight(1f).fillMaxWidth()) {
                val count = columns.size.coerceAtLeast(1)
                val fit_width = (maxWidth - compare_side_padding * 2 - compare_column_gap * (count - 1)) / count
                val column_width = if (fit_width < compare_min_column_width) compare_scroll_column_width else fit_width
                val h_scroll = rememberScrollState()
                Column(modifier = Modifier.fillMaxSize()) {
                    compare_header(
                        columns = columns,
                        column_width = column_width,
                        h_scroll = h_scroll,
                        selected_code = selected?.code,
                        per_month_unit = per_month_unit,
                        busy = busy,
                        on_select = { code = it },
                    )
                    HorizontalDivider(color = colors.border_primary, thickness = 1.dp)
                    LazyColumn(
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxWidth()
                            .testTag("billing_compare_table"),
                        contentPadding = PaddingValues(bottom = AsterSpacing.lg),
                    ) {
                        groups.forEach { group ->
                            item(key = "group_${group.id}") {
                                Text(
                                    text = group.title.uppercase(),
                                    color = colors.text_tertiary,
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.SemiBold,
                                    letterSpacing = 0.8.sp,
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(
                                            start = compare_side_padding,
                                            end = compare_side_padding,
                                            top = AsterSpacing.xl,
                                            bottom = AsterSpacing.xs,
                                        )
                                        .semantics { heading() },
                                )
                            }
                            items(group.rows, key = { "row_${group.id}_${it.id}" }) { row ->
                                compare_feature_row(
                                    row = row,
                                    columns = columns,
                                    column_width = column_width,
                                    h_scroll = h_scroll,
                                    selected_code = selected?.code,
                                )
                            }
                        }
                    }
                }
            }
        }
        HorizontalDivider(color = colors.border_primary, thickness = 1.dp)
        if (selected != null) {
            Column(modifier = Modifier.padding(horizontal = AsterSpacing.lg, vertical = AsterSpacing.sm)) {
                val save = yearly_save_percent(selected)
                if (selected.monthly_cents != null && selected.yearly_cents != null) {
                    Box(modifier = Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                        aster_segmented(
                            value = if (is_yearly) "year" else "month",
                            options = listOf(
                                switcher_option(
                                    id = "year",
                                    label = if (save > 0) {
                                        stringResource(R.string.billing_compare_yearly_save, save)
                                    } else {
                                        stringResource(R.string.billing_pay_yearly)
                                    },
                                ),
                                switcher_option(id = "month", label = stringResource(R.string.billing_pay_monthly)),
                            ),
                            on_change = on_interval_change,
                        )
                    }
                    Spacer(Modifier.height(AsterSpacing.sm))
                }
                val price_cents = if (is_yearly) selected.yearly_cents else selected.monthly_cents
                AsterButton(
                    label = plan_cta_label(selected),
                    onClick = { on_choose(selected) },
                    enabled = !busy && !selected.is_current && price_cents != null,
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("billing_compare_cta"),
                )
                if (price_cents != null && !selected.is_current) {
                    Spacer(Modifier.height(AsterSpacing.xs))
                    Text(
                        text = if (is_yearly) {
                            stringResource(R.string.billing_billed_yearly_total, money_short(price_cents, currency))
                        } else {
                            stringResource(R.string.billing_billed_monthly)
                        },
                        color = colors.text_tertiary,
                        fontSize = 12.sp,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
            }
        }
    }
}

@Composable
private fun compare_fit_text(
    text: String,
    color: Color,
    max_size: TextUnit,
    min_size: TextUnit,
    weight: FontWeight,
    modifier: Modifier = Modifier,
) {
    BasicText(
        text = text,
        style = LocalTextStyle.current.merge(
            TextStyle(
                color = color,
                fontSize = max_size,
                fontWeight = weight,
                textAlign = TextAlign.Center,
            ),
        ),
        maxLines = 1,
        softWrap = false,
        overflow = TextOverflow.Clip,
        autoSize = TextAutoSize.StepBased(minFontSize = min_size, maxFontSize = max_size, stepSize = 0.5.sp),
        modifier = modifier.fillMaxWidth(),
    )
}

@Composable
private fun compare_header(
    columns: List<compare_column>,
    column_width: Dp,
    h_scroll: ScrollState,
    selected_code: String?,
    per_month_unit: String,
    busy: Boolean,
    on_select: (String) -> Unit,
) {
    val colors = AsterMaterial.colors
    val shape = AsterShapes.control
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(colors.bg_primary)
            .horizontalScroll(h_scroll)
            .padding(horizontal = compare_side_padding, vertical = AsterSpacing.sm)
            .selectableGroup()
            .testTag("billing_compare_header"),
        horizontalArrangement = Arrangement.spacedBy(compare_column_gap),
    ) {
        columns.forEach { column ->
            val highlighted = column.code == selected_code
            val selectable = column.option != null && !busy
            Column(
                modifier = Modifier
                    .width(column_width)
                    .clip(shape)
                    .background(if (highlighted) colors.accent_blue.copy(alpha = 0.12f) else colors.bg_card)
                    .border(1.dp, if (highlighted) colors.accent_blue else colors.border_primary, shape)
                    .then(
                        if (column.option != null) {
                            Modifier.selectable(
                                selected = highlighted,
                                enabled = selectable,
                                role = Role.RadioButton,
                                onClick = { on_select(column.code) },
                            )
                        } else {
                            Modifier
                        },
                    )
                    .heightIn(min = 76.dp)
                    .padding(horizontal = AsterSpacing.xs, vertical = AsterSpacing.sm)
                    .testTag("billing_compare_column_${column.code}"),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center,
            ) {
                compare_fit_text(
                    text = column.name,
                    color = if (highlighted) colors.accent_blue else colors.text_primary,
                    max_size = 14.sp,
                    min_size = 9.sp,
                    weight = FontWeight.SemiBold,
                )
                Spacer(Modifier.height(2.dp))
                compare_fit_text(
                    text = column.price?.let { it + per_month_unit } ?: "",
                    color = if (highlighted) colors.accent_blue else colors.text_secondary,
                    max_size = 12.sp,
                    min_size = 8.sp,
                    weight = FontWeight.Medium,
                )
                val badge = when {
                    column.is_current -> stringResource(R.string.current)
                    column.option?.is_recommended == true -> stringResource(R.string.fix_billing_plan_recommended)
                    else -> null
                }
                Spacer(Modifier.height(2.dp))
                compare_fit_text(
                    text = badge ?: "",
                    color = if (column.is_current) colors.text_tertiary else colors.success,
                    max_size = 11.sp,
                    min_size = 7.sp,
                    weight = FontWeight.SemiBold,
                )
            }
        }
    }
}

private val compare_column_gap = 4.dp
private val compare_side_padding = AsterSpacing.lg
private val compare_min_column_width = 52.dp
private val compare_scroll_column_width = 76.dp

@Composable
private fun compare_feature_row(
    row: plan_comparison_row,
    columns: List<compare_column>,
    column_width: Dp,
    h_scroll: ScrollState,
    selected_code: String?,
) {
    val colors = AsterMaterial.colors
    val included_label = stringResource(R.string.billing_compare_included)
    val excluded_label = stringResource(R.string.billing_compare_not_included)
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = AsterSpacing.sm)
            .semantics(mergeDescendants = true) {},
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .padding(horizontal = compare_side_padding)
                .heightIn(min = 28.dp),
        ) {
            Text(
                text = row.label,
                color = colors.text_primary,
                fontSize = 15.sp,
                fontWeight = FontWeight.Medium,
                lineHeight = 20.sp,
                modifier = Modifier.weight(1f, fill = false),
            )
            if (row.tip != null) {
                Spacer(Modifier.width(AsterSpacing.xs))
                info_dialog_button(title = row.label, description = row.tip)
            }
        }
        Spacer(Modifier.height(AsterSpacing.xs))
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(h_scroll)
                .padding(horizontal = compare_side_padding)
                .height(IntrinsicSize.Min),
            horizontalArrangement = Arrangement.spacedBy(compare_column_gap),
        ) {
            columns.forEach { column ->
                val value = row.value_for(column.code)
                val highlighted = column.code == selected_code
                val description = when {
                    value.text != null -> "${column.name}: ${value.text}"
                    value.included -> "${column.name}: $included_label"
                    else -> "${column.name}: $excluded_label"
                }
                Box(
                    modifier = Modifier
                        .width(column_width)
                        .fillMaxHeight()
                        .heightIn(min = 44.dp)
                        .clip(AsterShapes.item)
                        .background(if (highlighted) colors.accent_blue.copy(alpha = 0.10f) else colors.bg_card)
                        .padding(horizontal = 2.dp, vertical = AsterSpacing.sm)
                        .semantics { contentDescription = description },
                    contentAlignment = Alignment.Center,
                ) {
                    when {
                        value.text != null && !value.text.trim().contains(' ') -> compare_fit_text(
                            text = value.text.trim(),
                            color = if (highlighted) colors.accent_blue else colors.text_primary,
                            max_size = 13.sp,
                            min_size = 8.sp,
                            weight = if (highlighted) FontWeight.SemiBold else FontWeight.Medium,
                        )
                        value.text != null -> Text(
                            text = value.text,
                            color = if (highlighted) colors.accent_blue else colors.text_primary,
                            fontSize = 13.sp,
                            lineHeight = 16.sp,
                            fontWeight = if (highlighted) FontWeight.SemiBold else FontWeight.Medium,
                            textAlign = TextAlign.Center,
                            maxLines = 3,
                            overflow = TextOverflow.Ellipsis,
                        )
                        value.included -> Icon(
                            imageVector = TablerIcons.Check,
                            contentDescription = null,
                            tint = if (highlighted) colors.accent_blue else colors.success,
                            modifier = Modifier.size(20.dp),
                        )
                        else -> Icon(
                            imageVector = TablerIcons.Minus,
                            contentDescription = null,
                            tint = colors.text_muted,
                            modifier = Modifier.size(18.dp),
                        )
                    }
                }
            }
        }
    }
}
