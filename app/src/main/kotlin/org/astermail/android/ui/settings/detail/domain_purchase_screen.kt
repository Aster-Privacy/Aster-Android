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
import compose.icons.tablericons.Check
import compose.icons.tablericons.ChevronRight
import compose.icons.tablericons.Search

import android.content.Intent
import android.net.Uri
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.foundation.text.ClickableText
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
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
import org.astermail.android.design.components.AsterCard
import org.astermail.android.design.components.AsterDivider
import org.astermail.android.design.components.AsterTextField
import org.astermail.android.settings.DomainPurchaseErrorKind
import org.astermail.android.settings.DomainPurchaseUiState
import org.astermail.android.settings.DomainPurchaseViewModel
import org.astermail.android.design.mirror_in_rtl

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
                    modifier = Modifier.size(18.dp),
                    strokeWidth = 2.dp,
                    color = colors.accent_blue,
                )
            }
        } else null,
        modifier = Modifier.fillMaxWidth(),
    )
    v_gap(AsterSpacing.md)

    when {
        state.search_failed -> {
            error_banner(stringResource(R.string.domain_purchase_search_failed))
            v_gap(AsterSpacing.sm)
            TextButton(onClick = { vm.retry_search() }) {
                Text(stringResource(R.string.retry), color = colors.accent_blue, fontSize = 14.sp)
            }
        }
        state.searched_query.isBlank() && !state.searching -> {
            v_gap(AsterSpacing.xl)
            Column(
                modifier = Modifier.fillMaxWidth(),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Text(
                    text = stringResource(R.string.domain_purchase_empty_title),
                    color = colors.text_primary,
                    fontSize = 16.sp,
                    fontWeight = FontWeight.SemiBold,
                    textAlign = TextAlign.Center,
                )
                v_gap(AsterSpacing.xs)
                Text(
                    text = stringResource(R.string.domain_purchase_empty_subtitle),
                    color = colors.text_tertiary,
                    fontSize = 13.sp,
                    textAlign = TextAlign.Center,
                )
            }
        }
        state.searched_query.isNotBlank() -> {
            search_results_list(vm = vm, state = state)
        }
    }
}

@Composable
private fun search_results_list(vm: DomainPurchaseViewModel, state: DomainPurchaseUiState) {
    val colors = AsterMaterial.colors
    if (state.results.isEmpty() && state.suggestions.isEmpty()) {
        Text(
            text = stringResource(R.string.domain_purchase_no_results),
            color = colors.text_tertiary,
            fontSize = 14.sp,
            modifier = Modifier.padding(vertical = AsterSpacing.md),
        )
        return
    }
    if (state.results.isNotEmpty()) {
        AsterCard(modifier = Modifier.fillMaxWidth()) {
            state.results.forEachIndexed { i, result ->
                if (i > 0) AsterDivider()
                domain_result_row(result = result, on_select = { vm.select_result(result) })
            }
        }
        v_gap(AsterSpacing.md)
    }
    if (state.suggestions.isNotEmpty()) {
        section_label(stringResource(R.string.domain_purchase_try_instead))
        AsterCard(modifier = Modifier.fillMaxWidth()) {
            state.suggestions.forEachIndexed { i, result ->
                if (i > 0) AsterDivider()
                domain_result_row(result = result, on_select = { vm.select_result(result) })
            }
        }
        if (state.has_more_suggestions || state.loading_more_suggestions) {
            v_gap(AsterSpacing.sm)
            Row(verticalAlignment = Alignment.CenterVertically) {
                TextButton(
                    onClick = { vm.load_more_suggestions() },
                    enabled = !state.loading_more_suggestions,
                ) {
                    Text(
                        text = stringResource(R.string.domain_purchase_more_suggestions),
                        color = colors.accent_blue,
                        fontSize = 14.sp,
                    )
                }
                if (state.loading_more_suggestions) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(16.dp),
                        strokeWidth = 2.dp,
                        color = colors.accent_blue,
                    )
                }
            }
        }
    }
}

@Composable
private fun domain_result_row(result: DomainSearchResult, on_select: () -> Unit) {
    val colors = AsterMaterial.colors
    val available = result.available && result.price_cents != null
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .then(if (available) Modifier.clickable(onClick = on_select) else Modifier)
            .padding(horizontal = AsterSpacing.md, vertical = AsterSpacing.md),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = result.domain,
                color = if (available) colors.text_primary else colors.text_muted,
                fontSize = 15.sp,
                fontWeight = FontWeight.Medium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            if (available) {
                val price = result.price_cents ?: 0
                val renewal = result.renewal_price_cents
                Text(
                    text = stringResource(
                        R.string.domain_purchase_per_year,
                        format_domain_price(price, result.currency),
                    ),
                    color = colors.text_secondary,
                    fontSize = 13.sp,
                )
                if (renewal != null && renewal != price) {
                    Text(
                        text = stringResource(
                            R.string.domain_purchase_renews_at,
                            format_domain_price(renewal, result.currency),
                        ),
                        color = colors.text_tertiary,
                        fontSize = 12.sp,
                    )
                }
            } else {
                Text(
                    text = stringResource(R.string.domain_purchase_taken),
                    color = colors.text_muted,
                    fontSize = 13.sp,
                )
            }
        }
        if (available) {
            Icon(
                imageVector = TablerIcons.ChevronRight,
                contentDescription = null,
                tint = colors.text_tertiary,
                modifier = Modifier.size(20.dp).mirror_in_rtl(),
            )
        }
    }
}

@Composable
private fun selector_chip(
    label: String,
    selected: Boolean,
    on_click: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = AsterMaterial.colors
    val shape = SquircleShape(12.dp)
    Box(
        modifier = modifier
            .border(
                width = if (selected) 1.5.dp else 1.dp,
                color = if (selected) colors.accent_blue else colors.border_secondary,
                shape = shape,
            )
            .background(
                if (selected) colors.accent_blue.copy(alpha = 0.08f) else colors.bg_card,
                shape,
            )
            .clickable(onClick = on_click)
            .padding(vertical = AsterSpacing.sm + 2.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = label,
            color = if (selected) colors.accent_blue else colors.text_secondary,
            fontSize = 14.sp,
            fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
        )
    }
}

@Composable
private fun purchase_confirm_content(vm: DomainPurchaseViewModel, state: DomainPurchaseUiState) {
    val colors = AsterMaterial.colors
    val context = LocalContext.current
    val selected = state.selected ?: return
    val price = selected.price_cents ?: 0
    val renewal = selected.renewal_price_cents ?: price
    val total = price + renewal * (state.years - 1).coerceAtLeast(0)

    Text(
        text = selected.domain,
        color = colors.text_primary,
        fontSize = 20.sp,
        fontWeight = FontWeight.Bold,
    )
    v_gap(AsterSpacing.lg)

    section_label(stringResource(R.string.domain_purchase_years))
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(AsterSpacing.sm),
    ) {
        (1..3).forEach { y ->
            selector_chip(
                label = pluralStringResource(R.plurals.domain_purchase_n_years, y, y),
                selected = state.years == y,
                on_click = { vm.set_years(y) },
                modifier = Modifier.weight(1f),
            )
        }
    }
    v_gap(AsterSpacing.md)

    section_label(stringResource(R.string.domain_purchase_pay_with))
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(AsterSpacing.sm),
    ) {
        selector_chip(
            label = stringResource(R.string.domain_purchase_pay_card),
            selected = state.payment_method == "stripe",
            on_click = { vm.set_payment_method("stripe") },
            modifier = Modifier.weight(1f),
        )
        selector_chip(
            label = stringResource(R.string.domain_purchase_pay_crypto),
            selected = state.payment_method == "crypto",
            on_click = { vm.set_payment_method("crypto") },
            modifier = Modifier.weight(1f),
        )
    }
    v_gap(AsterSpacing.md)

    section_label(stringResource(R.string.domain_purchase_included_heading))
    AsterCard(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(AsterSpacing.md)) {
            included_point(stringResource(R.string.domain_purchase_detail_privacy))
            included_point(stringResource(R.string.domain_purchase_detail_setup))
            included_point(stringResource(R.string.domain_purchase_detail_instant))
            included_point(stringResource(R.string.domain_purchase_detail_ownership))
        }
    }
    v_gap(AsterSpacing.md)

    purchase_summary_card(state = state, total = total, price = price, renewal = renewal)
    v_gap(AsterSpacing.md)

    purchase_terms_notice(on_open = { url -> open_url(context, url) })
    v_gap(AsterSpacing.md)

    state.checkout_error?.let {
        error_banner(domain_purchase_error_text(it))
        v_gap(AsterSpacing.md)
    }

    AsterButton(
        label = stringResource(
            R.string.domain_purchase_buy,
            format_domain_price(total, selected.currency),
        ),
        onClick = { vm.start_checkout(null) },
        is_loading = state.buying,
        modifier = Modifier.fillMaxWidth(),
    )
    v_gap(AsterSpacing.sm)
    Text(
        text = stringResource(R.string.domain_purchase_secure_checkout),
        color = colors.text_tertiary,
        fontSize = 12.sp,
        textAlign = TextAlign.Center,
        modifier = Modifier.fillMaxWidth(),
    )
}

@Composable
private fun included_point(text: String) {
    val colors = AsterMaterial.colors
    Row(
        modifier = Modifier.padding(vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            imageVector = TablerIcons.Check,
            contentDescription = null,
            tint = colors.success,
            modifier = Modifier.size(18.dp),
        )
        Spacer(Modifier.width(AsterSpacing.sm))
        Text(text = text, color = colors.text_secondary, fontSize = 14.sp)
    }
}

@Composable
private fun summary_row(label: String, value: String, emphasize: Boolean = false) {
    val colors = AsterMaterial.colors
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 6.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = label,
            color = if (emphasize) colors.text_primary else colors.text_secondary,
            fontSize = if (emphasize) 15.sp else 14.sp,
            fontWeight = if (emphasize) FontWeight.SemiBold else FontWeight.Normal,
        )
        Text(
            text = value,
            color = if (emphasize) colors.text_primary else colors.text_secondary,
            fontSize = if (emphasize) 15.sp else 14.sp,
            fontWeight = if (emphasize) FontWeight.SemiBold else FontWeight.Normal,
        )
    }
}

@Composable
private fun purchase_summary_card(
    state: DomainPurchaseUiState,
    total: Int,
    price: Int,
    renewal: Int,
) {
    val colors = AsterMaterial.colors
    val selected = state.selected ?: return
    section_label(stringResource(R.string.domain_purchase_order_summary))
    AsterCard(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(AsterSpacing.md)) {
            summary_row(
                label = pluralStringResource(R.plurals.domain_purchase_years_line, state.years, state.years),
                value = format_domain_price(total, selected.currency),
            )
            summary_row(
                label = stringResource(R.string.domain_purchase_summary_whois),
                value = stringResource(R.string.domain_purchase_summary_included),
            )
            summary_row(
                label = stringResource(R.string.domain_purchase_summary_dns),
                value = stringResource(R.string.domain_purchase_summary_included),
            )
            v_gap(AsterSpacing.xs)
            AsterDivider()
            v_gap(AsterSpacing.xs)
            summary_row(
                label = stringResource(R.string.domain_purchase_total_today),
                value = format_domain_price(total, selected.currency),
                emphasize = true,
            )
            if (renewal != price || selected.renewal_price_cents != null) {
                Text(
                    text = stringResource(
                        R.string.domain_purchase_renews_at,
                        format_domain_price(renewal, selected.currency),
                    ),
                    color = colors.text_tertiary,
                    fontSize = 12.sp,
                )
            }
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
                withStyle(SpanStyle(color = colors.text_tertiary)) { append(rest) }
                break
            }
            val (link, index) = hit
            if (index > 0) {
                withStyle(SpanStyle(color = colors.text_tertiary)) { append(rest.take(index)) }
            }
            pushStringAnnotation(tag = "url", annotation = link.second.second)
            withStyle(SpanStyle(color = colors.accent_blue, fontWeight = FontWeight.Medium)) {
                append(link.second.first)
            }
            pop()
            rest = rest.substring(index + link.first.length)
        }
    }
    @Suppress("DEPRECATION")
    ClickableText(
        text = annotated,
        style = androidx.compose.ui.text.TextStyle(fontSize = 12.sp),
        modifier = Modifier.fillMaxWidth(),
        onClick = { offset ->
            annotated.getStringAnnotations("url", offset, offset).firstOrNull()?.let {
                on_open(it.item)
            }
        },
    )
}
