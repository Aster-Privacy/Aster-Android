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

import compose.icons.TablerIcons
import compose.icons.tablericons.AlertTriangle
import compose.icons.tablericons.Check
import compose.icons.tablericons.CreditCard
import compose.icons.tablericons.CurrencyDollar
import compose.icons.tablericons.Lock
import compose.icons.tablericons.Refresh
import compose.icons.tablericons.Search
import compose.icons.tablericons.X

import android.content.Intent
import android.net.Uri
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.ClickableText
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import org.astermail.android.R
import org.astermail.android.api.domains.DomainSearchResult
import org.astermail.android.design.AsterMaterial
import org.astermail.android.design.AsterSpacing
import org.astermail.android.design.SquircleShape
import org.astermail.android.design.components.AsterButton
import org.astermail.android.design.components.AsterTextField
import org.astermail.android.settings.DomainPurchaseErrorKind
import org.astermail.android.settings.DomainPurchaseUiState
import org.astermail.android.settings.DomainPurchaseViewModel

private const val domain_min_query_length = 3
private const val domain_results_page_size = 10
private const val domain_min_discount_percent = 5

internal fun format_domain_price(cents: Int, currency: String): String =
    org.astermail.android.billing.format_money(cents.toLong(), currency)

internal fun open_url(context: android.content.Context, url: String) {
    try {
        context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)))
    } catch (_: Throwable) {
        android.widget.Toast.makeText(
            context,
            context.getString(R.string.could_not_open_link),
            android.widget.Toast.LENGTH_SHORT,
        ).show()
    }
}

@Composable
internal fun domain_purchase_error_text(kind: DomainPurchaseErrorKind): String = when (kind) {
    DomainPurchaseErrorKind.taken -> stringResource(R.string.domain_purchase_error_taken)
    DomainPurchaseErrorKind.limit -> stringResource(R.string.domain_purchase_error_limit)
    DomainPurchaseErrorKind.slow_down -> stringResource(R.string.domain_purchase_error_slow_down)
    DomainPurchaseErrorKind.paused -> stringResource(R.string.domain_purchase_error_paused)
    DomainPurchaseErrorKind.not_allowed -> stringResource(R.string.domain_purchase_error_not_allowed)
    else -> stringResource(R.string.domain_purchase_error)
}

@Composable
fun DomainPurchaseScreen(
    on_back: () -> Unit,
    on_open_progress: (String) -> Unit,
) {
    val vm: DomainPurchaseViewModel = hiltViewModel()
    val state by vm.state.collectAsStateWithLifecycle()
    val context = LocalContext.current

    LaunchedEffect(state.checkout_url) {
        val url = state.checkout_url ?: return@LaunchedEffect
        open_url(context, url)
        vm.consume_checkout_url()
    }

    LaunchedEffect(state.resume_order_id) {
        val id = state.resume_order_id ?: return@LaunchedEffect
        vm.consume_resume_order()
        on_open_progress(id)
    }

    val lifecycle_owner = LocalLifecycleOwner.current
    DisposableEffect(lifecycle_owner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) vm.check_pending_order()
        }
        lifecycle_owner.lifecycle.addObserver(observer)
        onDispose { lifecycle_owner.lifecycle.removeObserver(observer) }
    }

    BackHandler(enabled = state.selected != null) { vm.clear_selected() }

    detail_scaffold(
        title = stringResource(R.string.domain_purchase_title),
        on_back = { if (state.selected != null) vm.clear_selected() else on_back() },
    ) {
        if (state.selected == null) {
            purchase_search_content(vm = vm, state = state)
        } else {
            purchase_confirm_content(vm = vm, state = state)
        }
    }
}

@Composable
internal fun domain_status_disc(available: Boolean, size: Dp = 18.dp) {
    val colors = AsterMaterial.colors
    Box(
        modifier = Modifier
            .size(size)
            .background(if (available) colors.success else colors.danger, CircleShape),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            imageVector = if (available) TablerIcons.Check else TablerIcons.X,
            contentDescription = null,
            tint = Color.White,
            modifier = Modifier.size(size * 0.62f),
        )
    }
}

@Composable
internal fun domain_pill_button(
    label: String,
    on_click: () -> Unit,
    modifier: Modifier = Modifier,
    filled: Boolean = false,
    tint: Color? = null,
    icon: ImageVector? = null,
    enabled: Boolean = true,
    is_loading: Boolean = false,
) {
    val colors = AsterMaterial.colors
    val content_color = when {
        filled -> colors.on_accent
        tint != null -> tint
        else -> colors.text_primary
    }
    Row(
        modifier = modifier
            .heightIn(min = 36.dp)
            .clip(CircleShape)
            .then(
                if (filled) {
                    Modifier.background(colors.accent_blue, CircleShape)
                } else {
                    Modifier.border(1.dp, colors.border_secondary, CircleShape)
                },
            )
            .clickable(enabled = enabled && !is_loading, onClick = on_click)
            .graphicsLayer { alpha = if (enabled) 1f else 0.5f }
            .padding(horizontal = 14.dp, vertical = 8.dp),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (is_loading) {
            CircularProgressIndicator(
                modifier = Modifier.size(14.dp),
                strokeWidth = 2.dp,
                color = content_color,
            )
            Spacer(Modifier.width(6.dp))
        } else if (icon != null) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = content_color,
                modifier = Modifier.size(16.dp),
            )
            Spacer(Modifier.width(6.dp))
        }
        Text(
            text = label,
            color = content_color,
            fontSize = 13.sp,
            fontWeight = if (filled) FontWeight.SemiBold else FontWeight.Medium,
            maxLines = 1,
        )
    }
}

@Composable
internal fun domain_ghost_link(
    label: String,
    on_click: () -> Unit,
    enabled: Boolean = true,
    is_loading: Boolean = false,
) {
    val colors = AsterMaterial.colors
    Row(
        modifier = Modifier
            .heightIn(min = 40.dp)
            .clip(CircleShape)
            .clickable(enabled = enabled && !is_loading, onClick = on_click)
            .padding(horizontal = 14.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = label,
            color = if (enabled) colors.accent_blue else colors.text_muted,
            fontSize = 14.sp,
            fontWeight = FontWeight.Medium,
        )
        if (is_loading) {
            Spacer(Modifier.width(8.dp))
            CircularProgressIndicator(
                modifier = Modifier.size(14.dp),
                strokeWidth = 2.dp,
                color = colors.accent_blue,
            )
        }
    }
}

@Composable
internal fun domain_warning_notice(
    message: String,
    action: (@Composable () -> Unit)? = null,
) {
    val colors = AsterMaterial.colors
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 280.dp)
            .padding(horizontal = AsterSpacing.lg),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Icon(
            imageVector = TablerIcons.AlertTriangle,
            contentDescription = null,
            tint = colors.warning,
            modifier = Modifier.size(32.dp),
        )
        v_gap(AsterSpacing.md)
        Text(
            text = message,
            color = colors.text_secondary,
            fontSize = 14.sp,
            textAlign = TextAlign.Center,
            modifier = Modifier.widthIn(max = 300.dp),
        )
        if (action != null) {
            v_gap(AsterSpacing.lg)
            action()
        }
    }
}

@Composable
private fun purchase_search_content(vm: DomainPurchaseViewModel, state: DomainPurchaseUiState) {
    val colors = AsterMaterial.colors
    AsterTextField(
        value = state.query,
        onValueChange = { vm.set_query(it) },
        placeholder = stringResource(R.string.domain_purchase_search_placeholder),
        leading_icon = {
            Icon(
                imageVector = TablerIcons.Search,
                contentDescription = null,
                tint = colors.text_muted,
                modifier = Modifier.size(18.dp),
            )
        },
        trailing_icon = if (state.searching) {
            {
                CircularProgressIndicator(
                    modifier = Modifier.size(16.dp),
                    strokeWidth = 2.dp,
                    color = colors.text_muted,
                )
            }
        } else null,
        min_height = 48.dp,
        modifier = Modifier.fillMaxWidth(),
    )

    val active_search = state.query.trim().length >= domain_min_query_length
    if (!active_search) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = AsterSpacing.xl, vertical = 48.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(
                text = stringResource(R.string.domain_purchase_empty_subtitle),
                color = colors.text_secondary,
                fontSize = 14.sp,
                lineHeight = 22.sp,
                textAlign = TextAlign.Center,
            )
            v_gap(AsterSpacing.md)
            Text(
                text = stringResource(R.string.domain_purchase_empty_included),
                color = colors.text_muted,
                fontSize = 12.sp,
                lineHeight = 19.sp,
                textAlign = TextAlign.Center,
            )
        }
        return
    }

    v_gap(AsterSpacing.md)
    val has_rows = state.results.isNotEmpty() || state.suggestions.isNotEmpty()
    when {
        state.search_failed -> domain_warning_notice(
            message = stringResource(R.string.domain_purchase_search_failed),
            action = {
                domain_pill_button(
                    label = stringResource(R.string.domain_purchase_retry),
                    on_click = { vm.retry_search() },
                    icon = TablerIcons.Refresh,
                )
            },
        )
        state.search_rate_limited && !has_rows -> domain_warning_notice(
            message = stringResource(R.string.domain_purchase_search_rate_limited),
        )
        !has_rows -> {
            if (state.searching || state.searched_query.isBlank()) {
                domain_results_pulse_skeleton(rows = 5)
            } else {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = 280.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        text = stringResource(R.string.domain_purchase_no_results),
                        color = colors.text_muted,
                        fontSize = 14.sp,
                        textAlign = TextAlign.Center,
                    )
                }
            }
        }
        else -> search_results_list(vm = vm, state = state)
    }
}

@Composable
private fun search_rate_limit_banner() {
    val colors = AsterMaterial.colors
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(colors.warning.copy(alpha = 0.10f), SquircleShape(10.dp))
            .padding(horizontal = 12.dp, vertical = 10.dp),
        verticalAlignment = Alignment.Top,
    ) {
        Icon(
            imageVector = TablerIcons.AlertTriangle,
            contentDescription = null,
            tint = colors.warning,
            modifier = Modifier
                .padding(top = 2.dp)
                .size(16.dp),
        )
        Spacer(Modifier.width(8.dp))
        Text(
            text = stringResource(R.string.domain_purchase_search_rate_limited),
            color = colors.text_secondary,
            fontSize = 14.sp,
        )
    }
}

@Composable
private fun search_results_list(vm: DomainPurchaseViewModel, state: DomainPurchaseUiState) {
    val colors = AsterMaterial.colors
    var visible_count by remember(state.searched_query) { mutableIntStateOf(domain_results_page_size) }
    val showing_stale = state.searching && state.searched_query != state.query.trim()
    val visible_results = state.results.take(visible_count)
    val best_match = visible_results.firstOrNull { it.available && it.price_cents != null }
    val rest_results = if (best_match == null) visible_results else visible_results.filter { it.domain != best_match.domain }

    if (state.search_rate_limited) {
        search_rate_limit_banner()
        v_gap(AsterSpacing.sm)
    }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .graphicsLayer { alpha = if (showing_stale) 0.4f else 1f },
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = 12.dp, end = 12.dp, top = AsterSpacing.md, bottom = AsterSpacing.xs),
        ) {
            Text(
                text = stringResource(R.string.domain_purchase_results_for, state.searched_query),
                color = colors.text_primary,
                fontSize = 15.sp,
                fontWeight = FontWeight.SemiBold,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            Box(
                modifier = Modifier
                    .heightIn(min = 40.dp)
                    .clip(SquircleShape(8.dp))
                    .clickable { vm.set_query("") },
                contentAlignment = Alignment.CenterStart,
            ) {
                Text(
                    text = stringResource(R.string.domain_purchase_change_name),
                    color = colors.accent_blue,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Medium,
                )
            }
        }
        settings_row_gap()
        v_gap(AsterSpacing.xs)

        if (best_match != null) {
            domain_result_row(result = best_match, primary = true, on_select = { vm.select_result(best_match) })
        }
        rest_results.forEach { result ->
            domain_result_row(result = result, primary = false, on_select = { vm.select_result(result) })
        }
        if (state.results.size > visible_count) {
            Box(modifier = Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                domain_ghost_link(
                    label = stringResource(R.string.domain_purchase_show_more),
                    on_click = { visible_count += domain_results_page_size },
                )
            }
        }

        if (state.suggestions.isNotEmpty() || state.has_more_suggestions) {
            v_gap(AsterSpacing.xs)
            settings_row_gap()
            v_gap(AsterSpacing.sm)
            if (state.suggestions.isNotEmpty()) {
                Text(
                    text = stringResource(R.string.domain_purchase_try_instead).uppercase(),
                    color = colors.text_muted,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.SemiBold,
                    letterSpacing = 0.6.sp,
                    modifier = Modifier.padding(start = 12.dp, end = 12.dp, top = AsterSpacing.xs, bottom = 6.dp),
                )
            }
            state.suggestions.forEach { result ->
                domain_result_row(result = result, primary = false, on_select = { vm.select_result(result) })
            }
            if (state.has_more_suggestions) {
                Box(modifier = Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                    domain_ghost_link(
                        label = stringResource(R.string.domain_purchase_more_suggestions),
                        on_click = { vm.load_more_suggestions() },
                        is_loading = state.loading_more_suggestions,
                    )
                }
            }
            if (state.more_suggestions_rate_limited) {
                Text(
                    text = stringResource(R.string.domain_purchase_search_rate_limited),
                    color = colors.text_muted,
                    fontSize = 13.sp,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        }
    }
}

private fun domain_discount_percent(result: DomainSearchResult): Int? {
    val price = result.price_cents ?: return null
    val renewal = result.renewal_price_cents ?: return null
    if (renewal <= price || renewal <= 0) return null
    val percent = Math.round((1.0 - price.toDouble() / renewal.toDouble()) * 100.0).toInt()
    return if (percent >= domain_min_discount_percent) percent else null
}

@Composable
private fun domain_discount_badge(percent: Int, renewal_label: String) {
    val colors = AsterMaterial.colors
    val description = stringResource(R.string.domain_purchase_discount_tooltip, renewal_label)
    val label = remember(percent) {
        java.text.NumberFormat.getPercentInstance().format(-percent / 100.0)
    }
    Box(
        modifier = Modifier
            .semantics { contentDescription = description }
            .background(colors.success.copy(alpha = 0.14f), CircleShape)
            .padding(horizontal = 7.dp, vertical = 2.dp),
    ) {
        Text(
            text = label,
            color = colors.success,
            fontSize = 11.sp,
            fontWeight = FontWeight.SemiBold,
        )
    }
}

@Composable
private fun domain_result_row(result: DomainSearchResult, primary: Boolean, on_select: () -> Unit) {
    val colors = AsterMaterial.colors
    val available = result.available && result.price_cents != null
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(52.dp)
            .clip(SquircleShape(12.dp))
            .then(if (available) Modifier.clickable(onClick = on_select) else Modifier)
            .padding(horizontal = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        domain_status_disc(available = available)
        Spacer(Modifier.width(10.dp))
        Row(
            modifier = Modifier.weight(1f),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = result.domain,
                color = if (available) colors.text_primary else colors.text_muted,
                fontSize = if (available && primary) 16.sp else 15.sp,
                fontWeight = if (available && primary) FontWeight.SemiBold else FontWeight.Normal,
                textDecoration = if (available) null else TextDecoration.LineThrough,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f, fill = false),
            )
            val discount = if (available) domain_discount_percent(result) else null
            val renewal = result.renewal_price_cents
            if (discount != null && renewal != null) {
                Spacer(Modifier.width(8.dp))
                domain_discount_badge(
                    percent = discount,
                    renewal_label = format_domain_price(renewal, result.currency),
                )
            }
        }
        Spacer(Modifier.width(AsterSpacing.md))
        if (available) {
            Text(
                text = stringResource(
                    R.string.domain_purchase_per_year,
                    format_domain_price(result.price_cents ?: 0, result.currency),
                ),
                color = colors.accent_blue,
                fontSize = 15.sp,
                fontWeight = FontWeight.Medium,
                maxLines = 1,
            )
        } else {
            Text(
                text = stringResource(R.string.domain_purchase_taken),
                color = colors.danger,
                fontSize = 13.sp,
                fontWeight = FontWeight.Medium,
                maxLines = 1,
            )
        }
    }
}

@Composable
private fun choice_pill(
    label: String,
    selected: Boolean,
    on_click: () -> Unit,
    modifier: Modifier = Modifier,
    icon: ImageVector? = null,
) {
    val colors = AsterMaterial.colors
    val content_color = if (selected) colors.on_accent else colors.text_secondary
    Row(
        modifier = modifier
            .height(40.dp)
            .clip(CircleShape)
            .then(
                if (selected) {
                    Modifier.background(colors.accent_blue, CircleShape)
                } else {
                    Modifier.border(1.dp, colors.border_secondary, CircleShape)
                },
            )
            .clickable(onClick = on_click)
            .padding(horizontal = AsterSpacing.sm),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (icon != null) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = content_color,
                modifier = Modifier.size(16.dp),
            )
            Spacer(Modifier.width(8.dp))
        }
        Text(
            text = label,
            color = content_color,
            fontSize = 14.sp,
            fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
            maxLines = 1,
        )
    }
}

@Composable
private fun picker_label(text: String) {
    Text(
        text = text,
        color = AsterMaterial.colors.text_primary,
        fontSize = 14.sp,
        fontWeight = FontWeight.Medium,
    )
    v_gap(AsterSpacing.sm)
}

@Composable
private fun purchase_confirm_content(vm: DomainPurchaseViewModel, state: DomainPurchaseUiState) {
    val colors = AsterMaterial.colors
    val context = LocalContext.current
    val selected = state.selected ?: return
    val price = selected.price_cents ?: 0
    val renewal = selected.renewal_price_cents ?: price
    val total = price + renewal * (state.years - 1).coerceAtLeast(0)

    picker_label(stringResource(R.string.domain_purchase_years))
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(AsterSpacing.sm),
    ) {
        (1..3).forEach { y ->
            choice_pill(
                label = pluralStringResource(R.plurals.domain_purchase_n_years, y, y),
                selected = state.years == y,
                on_click = { vm.set_years(y) },
                modifier = Modifier.weight(1f),
            )
        }
    }
    v_gap(AsterSpacing.lg)

    picker_label(stringResource(R.string.domain_purchase_pay_with))
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(AsterSpacing.sm),
    ) {
        choice_pill(
            label = stringResource(R.string.domain_purchase_pay_card),
            selected = state.payment_method == "stripe",
            on_click = { vm.set_payment_method("stripe") },
            icon = TablerIcons.CreditCard,
            modifier = Modifier.weight(1f),
        )
        choice_pill(
            label = stringResource(R.string.domain_purchase_pay_crypto),
            selected = state.payment_method == "crypto",
            on_click = { vm.set_payment_method("crypto") },
            icon = TablerIcons.CurrencyDollar,
            modifier = Modifier.weight(1f),
        )
    }

    state.checkout_error?.let {
        v_gap(AsterSpacing.lg)
        checkout_error_box(domain_purchase_error_text(it))
    }
    v_gap(AsterSpacing.xl)

    purchase_summary_card(
        state = state,
        total = total,
        on_buy = { vm.start_checkout(null) },
    )
    v_gap(AsterSpacing.md)
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            imageVector = TablerIcons.Lock,
            contentDescription = null,
            tint = colors.text_muted,
            modifier = Modifier.size(12.dp),
        )
        Spacer(Modifier.width(6.dp))
        Text(
            text = stringResource(R.string.domain_purchase_secure_checkout),
            color = colors.text_muted,
            fontSize = 11.sp,
        )
    }
    v_gap(AsterSpacing.xl)

    included_grid()
    v_gap(AsterSpacing.xl)

    settings_row_gap()
    v_gap(AsterSpacing.lg)
    purchase_terms_notice(on_open = { url -> open_url(context, url) })
    v_gap(AsterSpacing.lg)
}

@Composable
private fun checkout_error_box(message: String) {
    val colors = AsterMaterial.colors
    val shape = SquircleShape(12.dp)
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .border(1.dp, colors.warning.copy(alpha = 0.30f), shape)
            .background(colors.warning.copy(alpha = 0.05f), shape)
            .padding(12.dp),
        verticalAlignment = Alignment.Top,
    ) {
        Icon(
            imageVector = TablerIcons.AlertTriangle,
            contentDescription = null,
            tint = colors.warning,
            modifier = Modifier
                .padding(top = 1.dp)
                .size(20.dp),
        )
        Spacer(Modifier.width(10.dp))
        Text(text = message, color = colors.text_primary, fontSize = 14.sp)
    }
}

@Composable
private fun included_grid() {
    val colors = AsterMaterial.colors
    val titles = listOf(
        stringResource(R.string.domain_purchase_detail_privacy),
        stringResource(R.string.domain_purchase_detail_setup),
        stringResource(R.string.domain_purchase_detail_instant),
        stringResource(R.string.domain_purchase_detail_ownership),
    )
    Text(
        text = stringResource(R.string.domain_purchase_included_heading),
        color = colors.text_muted,
        fontSize = 13.sp,
        fontWeight = FontWeight.Medium,
    )
    v_gap(AsterSpacing.md)
    titles.chunked(2).forEachIndexed { row_index, pair ->
        if (row_index > 0) v_gap(AsterSpacing.md)
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(AsterSpacing.xl),
        ) {
            pair.forEach { title ->
                Row(
                    modifier = Modifier.weight(1f),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(
                        imageVector = TablerIcons.Check,
                        contentDescription = null,
                        tint = colors.success,
                        modifier = Modifier.size(16.dp),
                    )
                    Spacer(Modifier.width(10.dp))
                    Text(
                        text = title,
                        color = colors.text_primary,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Medium,
                    )
                }
            }
        }
    }
}

@Composable
private fun summary_line(label: String, value: String, value_emphasized: Boolean) {
    val colors = AsterMaterial.colors
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = label,
            color = colors.text_secondary,
            fontSize = 14.sp,
            modifier = Modifier.weight(1f, fill = false),
        )
        Spacer(Modifier.width(AsterSpacing.md))
        Text(
            text = value,
            color = if (value_emphasized) colors.text_primary else colors.text_muted,
            fontSize = 14.sp,
            fontWeight = if (value_emphasized) FontWeight.Medium else FontWeight.Normal,
        )
    }
}

@Composable
private fun purchase_summary_card(
    state: DomainPurchaseUiState,
    total: Int,
    on_buy: () -> Unit,
) {
    val colors = AsterMaterial.colors
    val selected = state.selected ?: return
    val shape = SquircleShape(16.dp)
    Image(
        painter = painterResource(R.drawable.aster_wordmark),
        contentDescription = null,
        modifier = Modifier
            .fillMaxWidth()
            .height(24.dp)
            .graphicsLayer { alpha = 0.9f },
    )
    v_gap(14.dp)
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(shape)
            .background(colors.bg_secondary, shape)
            .border(1.dp, colors.border_secondary, shape),
    ) {
        Column(modifier = Modifier.padding(start = 24.dp, end = 24.dp, top = 20.dp, bottom = AsterSpacing.lg)) {
            Text(
                text = stringResource(R.string.domain_purchase_order_summary).uppercase(),
                color = colors.text_muted,
                fontSize = 11.sp,
                fontWeight = FontWeight.SemiBold,
                letterSpacing = 0.6.sp,
            )
            v_gap(AsterSpacing.sm)
            Text(
                text = selected.domain,
                color = colors.text_primary,
                fontSize = 17.sp,
                fontWeight = FontWeight.SemiBold,
            )
        }
        settings_row_gap()
        Column(
            modifier = Modifier.padding(horizontal = 24.dp, vertical = 20.dp),
            verticalArrangement = Arrangement.spacedBy(AsterSpacing.lg),
        ) {
            summary_line(
                label = pluralStringResource(R.plurals.domain_purchase_years_line, state.years, state.years),
                value = format_domain_price(total, selected.currency),
                value_emphasized = true,
            )
            summary_line(
                label = stringResource(R.string.domain_purchase_summary_whois),
                value = stringResource(R.string.domain_purchase_summary_included),
                value_emphasized = false,
            )
            summary_line(
                label = stringResource(R.string.domain_purchase_summary_dns),
                value = stringResource(R.string.domain_purchase_summary_included),
                value_emphasized = false,
            )
        }
        settings_row_gap()
        Column(modifier = Modifier.padding(start = 24.dp, end = 24.dp, top = 20.dp, bottom = 24.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = stringResource(R.string.domain_purchase_total_today),
                    color = colors.text_primary,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Medium,
                )
                Text(
                    text = format_domain_price(total, selected.currency),
                    color = colors.text_primary,
                    fontSize = 20.sp,
                    fontWeight = FontWeight.Bold,
                )
            }
            selected.renewal_price_cents?.let { renewal ->
                v_gap(6.dp)
                Text(
                    text = stringResource(
                        R.string.domain_purchase_renews_at,
                        format_domain_price(renewal, selected.currency),
                    ),
                    color = colors.text_muted,
                    fontSize = 12.sp,
                    textAlign = TextAlign.End,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
            v_gap(20.dp)
            AsterButton(
                label = stringResource(
                    R.string.domain_purchase_buy,
                    format_domain_price(total, selected.currency),
                ),
                onClick = on_buy,
                is_loading = state.buying,
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }
}

private const val terms_url_aster = "https://astermail.org/terms"
private const val terms_url_registrar =
    "https://www.namesilo.com/support/v2/articles/general-terms/terms-and-conditions"
private const val terms_url_icann = "https://www.icann.org/resources/pages/benefits-2013-09-16-en"

@Composable
private fun purchase_terms_notice(on_open: (String) -> Unit) {
    val colors = AsterMaterial.colors
    val aster_label = stringResource(R.string.domain_purchase_terms_aster)
    val registrar_label = stringResource(R.string.domain_purchase_terms_registrar)
    val icann_label = stringResource(R.string.domain_purchase_terms_icann)
    val template = stringResource(
        R.string.domain_purchase_terms_notice,
        "{a}",
        "{r}",
        "{i}",
    )
    val links = listOf(
        Pair("{a}", Pair(aster_label, terms_url_aster)),
        Pair("{r}", Pair(registrar_label, terms_url_registrar)),
        Pair("{i}", Pair(icann_label, terms_url_icann)),
    )
    val annotated = buildAnnotatedString {
        var rest = template
        while (rest.isNotEmpty()) {
            val hit = links
                .map { it to rest.indexOf(it.first) }
                .filter { it.second >= 0 }
                .minByOrNull { it.second }
            if (hit == null) {
                append(rest)
                break
            }
            val (link, index) = hit
            if (index > 0) append(rest.take(index))
            pushStringAnnotation(tag = "url", annotation = link.second.second)
            withStyle(SpanStyle(textDecoration = TextDecoration.Underline)) {
                append(link.second.first)
            }
            pop()
            rest = rest.substring(index + link.first.length)
        }
    }
    @Suppress("DEPRECATION")
    ClickableText(
        text = annotated,
        style = TextStyle(fontSize = 12.sp, lineHeight = 19.sp, color = colors.text_muted),
        modifier = Modifier.fillMaxWidth(),
        onClick = { offset ->
            annotated.getStringAnnotations("url", offset, offset).firstOrNull()?.let {
                on_open(it.item)
            }
        },
    )
}
