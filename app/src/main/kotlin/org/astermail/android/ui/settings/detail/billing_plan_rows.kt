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
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.compose.ui.window.DialogWindowProvider
import androidx.core.view.WindowCompat
import compose.icons.TablerIcons
import compose.icons.tablericons.Check
import compose.icons.tablericons.X
import kotlinx.coroutines.launch
import org.astermail.android.R
import org.astermail.android.billing.format_money
import org.astermail.android.billing.plan_comparison_feed
import org.astermail.android.billing.plan_comparison_row
import org.astermail.android.billing.plan_comparison_value
import org.astermail.android.billing.plan_tier_rank
import org.astermail.android.design.AsterMaterial
import org.astermail.android.design.AsterSpacing
import org.astermail.android.design.aster_reduce_motion
import org.astermail.android.design.components.AsterButton
import org.astermail.android.design.components.AsterCard
import org.astermail.android.design.components.AsterIconButton
import org.astermail.android.design.components.AsterPlanTag
import org.astermail.android.design.components.AsterSecondaryButton
import org.astermail.android.design.components.aster_plan_kind_of
import org.astermail.android.design.field_surface_color

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

private data class compare_page(
    val code: String,
    val name: String,
    val tagline: String,
    val option: billing_plan_option?,
    val is_current: Boolean,
)

private val compare_shared_codes = setOf("duo", "family")

private const val compare_gain_limit = 6

internal fun compare_columns_for(
    feed: plan_comparison_feed,
    options: List<billing_plan_option>,
    include_free: Boolean,
): List<String> = buildList {
    if (include_free && feed.plan("free") != null) add("free")
    options.forEach { option -> if (feed.plan(option.code) != null) add(option.code) }
}

internal fun compare_value_magnitude(text: String): Double? {
    val trimmed = text.trim().lowercase()
    if (trimmed.startsWith("unlimited")) return Double.MAX_VALUE
    val match = Regex("""^(\d[\d.,]*)\s*([a-z]*)""").find(trimmed) ?: return null
    val number = match.groupValues[1].filter { it.isDigit() }.toDoubleOrNull() ?: return null
    val scale = when (match.groupValues[2]) {
        "tb", "to", "tib" -> 1_000_000.0
        "gb", "go", "gib" -> 1_000.0
        else -> 1.0
    }
    return number * scale
}

internal fun compare_row_gain(row: plan_comparison_row, code: String, base_code: String): Boolean {
    val target = row.value_for(code)
    if (!target.included) return false
    val base = row.value_for(base_code)
    if (!base.included) return true
    if (target == base) return false
    val target_text = target.text ?: return false
    val base_text = base.text ?: return false
    val target_size = compare_value_magnitude(target_text)
    val base_size = compare_value_magnitude(base_text)
    if (target_size != null && base_size != null) return target_size > base_size
    return plan_tier_rank(code) > plan_tier_rank(base_code)
}

internal fun compare_start_index(codes: List<String>, initial_code: String, current_code: String): Int {
    val initial = codes.indexOf(initial_code)
    if (initial >= 0 && initial_code != current_code) return initial
    val current = codes.indexOf(current_code)
    if (current < 0) return initial.coerceAtLeast(0)
    val shared = current_code in compare_shared_codes
    val next = codes.drop(current + 1).firstOrNull { (it in compare_shared_codes) == shared }
    return next?.let { codes.indexOf(it) } ?: current
}

@Composable
private fun compare_short_tagline(code: String, fallback: String): String = when (code) {
    "free" -> stringResource(R.string.billing_compare_tagline_free)
    "star" -> stringResource(R.string.billing_compare_tagline_star)
    "nova" -> stringResource(R.string.billing_compare_tagline_nova)
    "supernova" -> stringResource(R.string.billing_compare_tagline_supernova)
    "duo" -> stringResource(R.string.billing_compare_tagline_duo)
    "family" -> stringResource(R.string.billing_compare_tagline_family)
    else -> fallback
}

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
        org.astermail.android.design.components.aster_popup_window_effect(
            light_status_bars = light_bars,
            light_navigation_bars = light_bars,
            full_window = true,
        )
        Box(modifier = Modifier.fillMaxSize()) {
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
        org.astermail.android.ui.common.app_toast_host(nested = true)
        }
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
    val base_code = current_code ?: "free"
    val is_yearly = billing_interval == "year"
    val reduce_motion = aster_reduce_motion()
    val scope = rememberCoroutineScope()
    val free_name = stringResource(R.string.plan_name_free)
    val codes = if (feed != null) {
        compare_columns_for(feed, options, include_free = true)
    } else {
        listOf("free") + options.map { it.code }
    }
    val pages = codes.map { code ->
        val option = options.firstOrNull { it.code == code }
        val name = option?.name ?: if (code == "free") free_name else feed?.plan(code)?.name ?: code
        compare_page(
            code = code,
            name = name,
            tagline = compare_short_tagline(code, option?.tagline.orEmpty()),
            option = option,
            is_current = code == base_code || option?.is_current == true,
        )
    }
    val base_name = pages.firstOrNull { it.code == base_code }?.name ?: free_name
    val start_index = remember(codes, initial_code, base_code) { compare_start_index(codes, initial_code, base_code) }
    val pager_state = rememberPagerState(initialPage = start_index) { pages.size }
    val chip_state = rememberLazyListState()
    val page_index = pager_state.currentPage.coerceIn(0, (pages.size - 1).coerceAtLeast(0))
    LaunchedEffect(page_index) {
        val info = chip_state.layoutInfo
        val item = info.visibleItemsInfo.firstOrNull { it.index == page_index }
        val fully_visible = item != null &&
            item.offset >= info.viewportStartOffset &&
            item.offset + item.size <= info.viewportEndOffset
        if (!fully_visible) {
            val target = (page_index - 1).coerceAtLeast(0)
            if (reduce_motion) chip_state.scrollToItem(target) else chip_state.animateScrollToItem(target)
        }
    }
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
        compare_plan_chips(
            pages = pages,
            selected_index = page_index,
            list_state = chip_state,
            on_select = { index ->
                scope.launch {
                    if (reduce_motion) pager_state.scrollToPage(index) else pager_state.animateScrollToPage(index)
                }
            },
        )
        HorizontalPager(
            state = pager_state,
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
                .testTag("billing_compare_pager"),
            verticalAlignment = Alignment.Top,
            key = { index -> pages[index].code },
        ) { index ->
            compare_plan_page(
                page = pages[index],
                feed = feed,
                base_code = base_code,
                base_name = base_name,
                is_yearly = is_yearly,
                currency = currency,
                on_see_pricing = on_see_pricing,
            )
        }
        HorizontalDivider(color = colors.border_primary, thickness = 1.dp)
        val page = pages.getOrNull(page_index)
        if (page != null) {
            compare_plan_footer(
                page = page,
                options = options,
                base_code = base_code,
                is_yearly = is_yearly,
                busy = busy,
                on_interval_change = on_interval_change,
                on_choose = on_choose,
                on_see_pricing = on_see_pricing,
            )
        }
    }
}

@Composable
private fun compare_plan_chips(
    pages: List<compare_page>,
    selected_index: Int,
    list_state: LazyListState,
    on_select: (Int) -> Unit,
) {
    val colors = AsterMaterial.colors
    val current_label = stringResource(R.string.current)
    LazyRow(
        state = list_state,
        modifier = Modifier
            .fillMaxWidth()
            .selectableGroup()
            .testTag("billing_compare_chips"),
        contentPadding = PaddingValues(horizontal = AsterSpacing.lg),
        horizontalArrangement = Arrangement.spacedBy(AsterSpacing.xs),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        itemsIndexed(pages, key = { _, page -> page.code }) { index, page ->
            val starts_shared = index > 0 &&
                page.code in compare_shared_codes &&
                pages[index - 1].code !in compare_shared_codes
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (starts_shared) {
                    Box(
                        modifier = Modifier
                            .width(1.dp)
                            .height(20.dp)
                            .background(colors.border_primary),
                    )
                    Spacer(Modifier.width(AsterSpacing.xs))
                }
                val selected = index == selected_index
                Row(
                    modifier = Modifier
                        .minimumInteractiveComponentSize()
                        .clip(CircleShape)
                        .background(if (selected) colors.accent_blue else field_surface_color(colors))
                        .selectable(selected = selected, role = Role.Tab, onClick = { on_select(index) })
                        .heightIn(min = 36.dp)
                        .padding(horizontal = AsterSpacing.lg)
                        .testTag("billing_compare_chip_${page.code}"),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = page.name,
                        color = if (selected) colors.on_accent else colors.text_secondary,
                        fontSize = 14.sp,
                        fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Medium,
                        maxLines = 1,
                        softWrap = false,
                    )
                    if (page.is_current) {
                        Spacer(Modifier.width(6.dp))
                        Box(
                            modifier = Modifier
                                .size(6.dp)
                                .clip(CircleShape)
                                .background(if (selected) colors.on_accent else colors.accent_blue)
                                .semantics { contentDescription = current_label },
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun compare_plan_page(
    page: compare_page,
    feed: plan_comparison_feed?,
    base_code: String,
    base_name: String,
    is_yearly: Boolean,
    currency: String,
    on_see_pricing: () -> Unit,
) {
    val colors = AsterMaterial.colors
    val groups = remember(feed, page.code) {
        feed?.groups_for(listOf(page.code), include_family = true)
            ?.map { group -> group.copy(rows = group.rows.filter { it.value_for(page.code).included }) }
            ?.filter { it.rows.isNotEmpty() }
            .orEmpty()
    }
    val gains = remember(groups, page.code, page.is_current, base_code) {
        if (page.is_current) {
            emptyList()
        } else {
            groups.flatMap { it.rows }.filter { compare_row_gain(it, page.code, base_code) }.take(compare_gain_limit)
        }
    }
    val not_in_plan = stringResource(R.string.billing_compare_not_in_plan)
    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .testTag("billing_compare_page_${page.code}"),
        contentPadding = PaddingValues(
            start = AsterSpacing.lg,
            end = AsterSpacing.lg,
            top = AsterSpacing.sm,
            bottom = AsterSpacing.xl,
        ),
    ) {
        item(key = "hero") {
            compare_plan_hero(page = page, is_yearly = is_yearly, currency = currency)
        }
        if (feed == null) {
            item(key = "pricing") {
                Spacer(Modifier.height(AsterSpacing.lg))
                billing_link_row(text = stringResource(R.string.see_pricing), on_click = on_see_pricing)
            }
        }
        if (gains.isNotEmpty()) {
            item(key = "gains") {
                compare_section_heading(stringResource(R.string.billing_compare_gains_title, page.name))
                AsterCard(modifier = Modifier.fillMaxWidth()) {
                    Column {
                        gains.forEachIndexed { index, row ->
                            if (index > 0) settings_row_gap()
                            val base = row.value_for(base_code)
                            compare_plan_feature_row(
                                label = row.label,
                                tip = row.tip,
                                value = row.value_for(page.code),
                                note = when {
                                    !base.included -> not_in_plan
                                    base.text != null -> stringResource(R.string.billing_compare_you_have, base.text)
                                    else -> null
                                },
                                emphasized = true,
                            )
                        }
                    }
                }
            }
            item(key = "everything") {
                Text(
                    text = stringResource(R.string.billing_compare_everything_in, page.name),
                    color = colors.text_primary,
                    fontSize = 17.sp,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier
                        .padding(start = AsterSpacing.xs, end = AsterSpacing.xs, top = AsterSpacing.xxl)
                        .semantics { heading() },
                )
            }
        }
        groups.forEach { group ->
            item(key = "group_${group.id}") {
                compare_section_heading(group.title)
                AsterCard(modifier = Modifier.fillMaxWidth()) {
                    Column {
                        group.rows.forEachIndexed { index, row ->
                            if (index > 0) settings_row_gap()
                            compare_plan_feature_row(
                                label = row.label,
                                tip = row.tip,
                                value = row.value_for(page.code),
                                note = null,
                                emphasized = false,
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun compare_section_heading(text: String) {
    Box(modifier = Modifier.semantics(mergeDescendants = true) { heading() }) {
        section_label(text)
    }
}

@Composable
private fun compare_plan_hero(page: compare_page, is_yearly: Boolean, currency: String) {
    val colors = AsterMaterial.colors
    val option = page.option
    val price_cents = if (page.code == "free" && option == null) 0 else option?.let { per_month_cents(it, is_yearly) }
    val yearly_total = option?.yearly_cents
    AsterCard(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(AsterSpacing.lg)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = page.name,
                    color = colors.text_primary,
                    fontSize = 22.sp,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = (-0.4).sp,
                    modifier = Modifier
                        .weight(1f, fill = false)
                        .semantics { heading() },
                )
                if (page.is_current) {
                    Spacer(Modifier.width(AsterSpacing.sm))
                    AsterPlanTag(
                        text = stringResource(R.string.current),
                        plan = aster_plan_kind_of(page.code),
                    )
                } else if (option?.is_recommended == true) {
                    Spacer(Modifier.width(AsterSpacing.sm))
                    Text(
                        text = stringResource(R.string.fix_billing_plan_recommended),
                        color = colors.accent_blue,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.SemiBold,
                    )
                }
            }
            if (page.tagline.isNotEmpty()) {
                Spacer(Modifier.height(AsterSpacing.xs))
                Text(
                    text = page.tagline,
                    color = colors.text_secondary,
                    fontSize = 14.sp,
                    lineHeight = 19.sp,
                )
            }
            Spacer(Modifier.height(AsterSpacing.lg))
            if (price_cents != null) {
                Row {
                    Text(
                        text = money_short(price_cents, currency),
                        color = colors.text_primary,
                        fontSize = 34.sp,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = (-0.8).sp,
                        modifier = Modifier.alignByBaseline(),
                    )
                    Spacer(Modifier.width(2.dp))
                    Text(
                        text = stringResource(R.string.fix_billing_per_month_short),
                        color = colors.text_tertiary,
                        fontSize = 15.sp,
                        fontWeight = FontWeight.Medium,
                        modifier = Modifier.alignByBaseline(),
                    )
                }
                Spacer(Modifier.height(2.dp))
                Text(
                    text = when {
                        option == null -> stringResource(R.string.billing_compare_free_forever)
                        is_yearly && yearly_total != null -> stringResource(
                            R.string.billing_billed_yearly_total,
                            money_short(yearly_total, currency),
                        )
                        else -> stringResource(R.string.billing_billed_monthly)
                    },
                    color = colors.text_tertiary,
                    fontSize = 13.sp,
                )
            } else {
                Text(
                    text = stringResource(R.string.see_pricing),
                    color = colors.text_tertiary,
                    fontSize = 14.sp,
                )
            }
        }
    }
}

@Composable
private fun compare_plan_feature_row(
    label: String,
    tip: String?,
    value: plan_comparison_value,
    note: String?,
    emphasized: Boolean,
) {
    val colors = AsterMaterial.colors
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = settings_row_min_height)
            .padding(horizontal = AsterSpacing.lg, vertical = AsterSpacing.sm)
            .semantics(mergeDescendants = true) {},
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = label,
                    color = colors.text_primary,
                    fontSize = 15.sp,
                    fontWeight = FontWeight.Medium,
                    lineHeight = 20.sp,
                    modifier = Modifier.weight(1f, fill = false),
                )
                if (tip != null) {
                    Spacer(Modifier.width(2.dp))
                    info_dialog_button(title = label, description = tip)
                }
            }
            if (note != null) {
                Text(
                    text = note,
                    color = colors.text_tertiary,
                    fontSize = 13.sp,
                )
            }
        }
        Spacer(Modifier.width(AsterSpacing.md))
        val text = value.text
        if (text != null) {
            Text(
                text = text,
                color = if (emphasized) colors.accent_blue else colors.text_secondary,
                fontSize = 14.sp,
                fontWeight = if (emphasized) FontWeight.SemiBold else FontWeight.Medium,
                textAlign = TextAlign.End,
                modifier = Modifier.widthIn(max = 140.dp),
            )
        } else {
            Icon(
                imageVector = TablerIcons.Check,
                contentDescription = stringResource(R.string.billing_compare_included),
                tint = colors.accent_blue,
                modifier = Modifier.size(20.dp),
            )
        }
    }
}

@Composable
private fun compare_plan_footer(
    page: compare_page,
    options: List<billing_plan_option>,
    base_code: String,
    is_yearly: Boolean,
    busy: Boolean,
    on_interval_change: (String) -> Unit,
    on_choose: (billing_plan_option) -> Unit,
    on_see_pricing: () -> Unit,
) {
    val colors = AsterMaterial.colors
    val option = page.option
    val interval_plan = option?.takeIf { it.monthly_cents != null && it.yearly_cents != null }
        ?: options.firstOrNull { it.monthly_cents != null && it.yearly_cents != null }
    Column(modifier = Modifier.padding(horizontal = AsterSpacing.lg, vertical = AsterSpacing.sm)) {
        if (interval_plan != null) {
            val save = yearly_save_percent(interval_plan)
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
        val cta_modifier = Modifier
            .fillMaxWidth()
            .testTag("billing_compare_cta")
        val price_cents = option?.let { if (is_yearly) it.yearly_cents else it.monthly_cents }
        when {
            option?.is_interval_switch == true -> AsterButton(
                label = stringResource(R.string.switch_to_yearly),
                onClick = { on_choose(option) },
                enabled = !busy,
                modifier = cta_modifier,
            )
            page.is_current -> AsterSecondaryButton(
                label = stringResource(R.string.current_plan),
                onClick = {},
                enabled = false,
                modifier = cta_modifier,
            )
            option == null -> Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = 48.dp),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = stringResource(R.string.billing_compare_free_note),
                    color = colors.text_tertiary,
                    fontSize = 13.sp,
                    textAlign = TextAlign.Center,
                )
            }
            price_cents == null -> AsterSecondaryButton(
                label = stringResource(R.string.see_pricing),
                onClick = on_see_pricing,
                enabled = !busy,
                modifier = cta_modifier,
            )
            option.is_downgrade -> AsterSecondaryButton(
                label = stringResource(R.string.billing_compare_downgrade_to, option.name),
                onClick = { on_choose(option) },
                enabled = !busy,
                modifier = cta_modifier,
            )
            else -> AsterButton(
                label = if (plan_tier_rank(option.code) > plan_tier_rank(base_code)) {
                    stringResource(R.string.billing_compare_upgrade_to, option.name)
                } else {
                    stringResource(R.string.billing_compare_switch_to, option.name)
                },
                onClick = { on_choose(option) },
                enabled = !busy,
                modifier = cta_modifier,
            )
        }
    }
}
