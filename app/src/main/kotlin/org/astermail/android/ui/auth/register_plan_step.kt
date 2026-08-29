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

package org.astermail.android.ui.auth

import compose.icons.TablerIcons
import compose.icons.tablericons.*

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import org.astermail.android.R
import kotlinx.coroutines.delay
import org.astermail.android.api.billing.AvailablePlan
import org.astermail.android.billing.BillingViewModel
import org.astermail.android.design.SquircleShape
import org.astermail.android.design.AsterMaterial
import org.astermail.android.design.AsterRadius
import org.astermail.android.design.AsterSpacing
import org.astermail.android.design.components.AsterButton
import org.astermail.android.design.components.AsterSecondaryButton

@Composable
fun RegisterPlanStep(
    on_continue: () -> Unit,
    billing_vm: BillingViewModel = hiltViewModel(),
) {
    val colors = AsterMaterial.colors
    val state by billing_vm.state.collectAsStateWithLifecycle()
    val context = LocalContext.current
    var selected_code by remember { mutableStateOf("free") }
    var billing_interval by remember { mutableStateOf("year") }
    var plans_unavailable by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        billing_vm.load_subscription()
        billing_vm.load_plans()
        delay(2000)
        if (billing_vm.state.value.available_plans.isEmpty()) {
            billing_vm.load_plans()
            delay(3000)
            if (billing_vm.state.value.available_plans.isEmpty()) {
                plans_unavailable = true
            }
        }
    }

    LaunchedEffect(state.checkout_url) {
        val url = state.checkout_url ?: return@LaunchedEffect
        org.astermail.android.billing.open_billing_tab(context, url)
        billing_vm.consume_checkout_url()
    }

    val lifecycle_owner = LocalLifecycleOwner.current
    DisposableEffect(lifecycle_owner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                billing_vm.on_resume()
            }
        }
        lifecycle_owner.lifecycle.addObserver(observer)
        onDispose { lifecycle_owner.lifecycle.removeObserver(observer) }
    }

    val sub = state.subscription
    LaunchedEffect(sub) {
        if (sub != null && sub.plan.price_cents > 0 && sub.status == "active") {
            on_continue()
        }
    }

    val plans = state.available_plans
    val plans_failed = plans.isEmpty() && (plans_unavailable || state.plans_failed)
    val currency = state.subscription?.currency?.takeIf { it.isNotBlank() } ?: "usd"

    val has_yearly = plans.any { it.billing_period == "year" && it.price_cents > 0 }
    val has_monthly = plans.any { it.billing_period == "month" && it.price_cents > 0 }
    val show_billing_toggle = has_yearly && has_monthly

    val monthly_plans = plans.filter { it.billing_period == "month" || it.price_cents == 0 }
    val yearly_plans = plans.filter { it.billing_period == "year" || it.price_cents == 0 }
    val effective_interval = if (billing_interval == "year" && has_yearly) "year" else "month"
    val display_plans = if (effective_interval == "year") yearly_plans else monthly_plans

    auth_centered_column {
        Image(
            painter = painterResource(R.drawable.aster_wordmark),
            contentDescription = null,
            modifier = Modifier.height(40.dp),
        )

        Spacer(Modifier.height(AsterSpacing.xl))

        Text(
            text = stringResource(R.string.choose_your_plan),
            color = colors.text_primary,
            fontSize = 30.sp,
            fontWeight = FontWeight.ExtraBold,
            letterSpacing = (-0.3).sp,
            textAlign = TextAlign.Center,
        )
        Spacer(Modifier.height(AsterSpacing.md))
        Text(
            text = stringResource(R.string.plan_subtitle),
            color = colors.text_tertiary,
            fontSize = 14.sp,
            textAlign = TextAlign.Center,
        )

        Spacer(Modifier.height(AsterSpacing.xl))

        state.error?.let { message ->
            error_banner(message = message)
            Spacer(Modifier.height(AsterSpacing.lg))
        }
        val abandoned_plan = state.checkout_abandoned_plan
        if (abandoned_plan != null && !state.checking_payment) {
            val abandoned_name = org.astermail.android.billing.plan_display_name(plans, abandoned_plan) ?: abandoned_plan
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(colors.accent_blue.copy(alpha = 0.08f), SquircleShape(18.dp))
                    .padding(AsterSpacing.lg),
            ) {
                Text(
                    text = stringResource(R.string.finish_plan_setup_title, abandoned_name),
                    color = colors.text_primary,
                    fontSize = 15.sp,
                    fontWeight = FontWeight.SemiBold,
                )
                Spacer(Modifier.height(4.dp))
                Text(
                    text = stringResource(R.string.finish_plan_setup_message),
                    color = colors.text_tertiary,
                    fontSize = 13.sp,
                )
            }
            Spacer(Modifier.height(AsterSpacing.lg))
        }
        if (state.checking_payment) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                CircularProgressIndicator(color = colors.accent_blue, modifier = Modifier.size(16.dp), strokeWidth = 2.dp)
                Spacer(Modifier.size(AsterSpacing.sm))
                Text(text = stringResource(R.string.confirming_payment), color = colors.text_tertiary, fontSize = 13.sp)
            }
            Spacer(Modifier.height(AsterSpacing.lg))
        }
        if (plans_failed) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(colors.bg_card, SquircleShape(18.dp))
                    .border(1.dp, colors.border_secondary, SquircleShape(18.dp))
                    .padding(AsterSpacing.lg),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Text(
                    text = stringResource(R.string.plans_unavailable),
                    color = colors.text_secondary,
                    fontSize = 14.sp,
                    textAlign = TextAlign.Center,
                )
                Spacer(Modifier.height(AsterSpacing.md))
                AsterSecondaryButton(
                    label = stringResource(R.string.see_pricing),
                    onClick = { org.astermail.android.billing.open_billing_tab(context, org.astermail.android.billing.PRICING_URL) },
                )
            }
        } else if (plans.isEmpty()) {
            Box(
                modifier = Modifier.fillMaxWidth().padding(AsterSpacing.xxl),
                contentAlignment = Alignment.Center,
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    CircularProgressIndicator(color = colors.accent_blue, modifier = Modifier.size(24.dp))
                    Spacer(Modifier.height(AsterSpacing.md))
                    Text(
                        text = stringResource(R.string.loading_plans),
                        color = colors.text_muted,
                        fontSize = 13.sp,
                    )
                }
            }
        } else {
            if (show_billing_toggle) {
                billing_toggle(
                    selected = billing_interval,
                    on_select = { billing_interval = it },
                )
                Spacer(Modifier.height(AsterSpacing.lg))
            }

            display_plans.forEach { plan ->
                plan_card(
                    plan = plan,
                    is_selected = selected_code == plan.code,
                    billing_interval = effective_interval,
                    currency = currency,
                    on_select = {
                        selected_code = plan.code
                        billing_vm.clear_messages()
                    },
                )
                Spacer(Modifier.height(AsterSpacing.md))
            }
        }

        Spacer(Modifier.height(AsterSpacing.xl))

        if (state.error != null) {
            Text(
                text = state.error ?: "",
                color = colors.danger,
                fontSize = 13.sp,
                textAlign = TextAlign.Center,
            )
            Spacer(Modifier.height(AsterSpacing.md))
        }

        if (selected_code == "free" || plans_failed) {
            AsterButton(
                label = stringResource(R.string.continue_with_free),
                onClick = on_continue,
            )
        } else {
            AsterButton(
                label = stringResource(R.string.continue_with_upgrade),
                onClick = {
                    billing_vm.clear_messages()
                    if (plans.isEmpty()) {
                        billing_vm.load_plans()
                    }
                    billing_vm.start_checkout(selected_code, effective_interval, currency)
                },
                is_loading = state.is_acting,
            )
            Spacer(Modifier.height(AsterSpacing.sm))
            AsterSecondaryButton(
                label = stringResource(R.string.skip_for_now),
                onClick = on_continue,
            )
        }
    }
}

@Composable
private fun billing_toggle(
    selected: String,
    on_select: (String) -> Unit,
) {
    val colors = AsterMaterial.colors
    val options = listOf("month" to stringResource(R.string.monthly), "year" to stringResource(R.string.yearly))
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(colors.input_bg, SquircleShape(18.dp))
            .border(1.dp, colors.input_border, SquircleShape(18.dp))
            .padding(4.dp),
        horizontalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        options.forEach { (value, label) ->
            val active = selected == value
            Box(
                modifier = Modifier
                    .weight(1f)
                    .height(40.dp)
                    .background(
                        if (active) colors.accent_blue else Color.Transparent,
                        SquircleShape(14.dp),
                    )
                    .clickable { on_select(value) },
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = label,
                    color = if (active) Color.White else colors.text_muted,
                    fontSize = 14.sp,
                    fontWeight = if (active) FontWeight.SemiBold else FontWeight.Medium,
                )
            }
        }
    }
}

@Composable
private fun plan_card(
    plan: AvailablePlan,
    is_selected: Boolean,
    billing_interval: String,
    currency: String,
    on_select: () -> Unit,
) {
    val colors = AsterMaterial.colors
    val context = LocalContext.current
    val border_color = if (is_selected) colors.accent_blue else colors.border_secondary

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .background(colors.bg_card, SquircleShape(18.dp))
            .border(
                if (is_selected) 2.dp else 1.dp,
                border_color,
                SquircleShape(18.dp),
            )
            .clickable { on_select() }
            .padding(AsterSpacing.lg),
    ) {
        Column {
            Row(verticalAlignment = Alignment.CenterVertically) {
                val localized_name = when (plan.code.lowercase()) {
                    "free" -> stringResource(R.string.plan_name_free)
                    "star" -> stringResource(R.string.plan_name_star)
                    "nova" -> stringResource(R.string.plan_name_nova)
                    "supernova" -> stringResource(R.string.plan_name_supernova)
                    "duo" -> stringResource(R.string.plan_name_duo)
                    "family" -> stringResource(R.string.plan_name_family)
                    else -> plan.name
                }
                Text(
                    text = localized_name,
                    color = colors.text_primary,
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.weight(1f),
                )
                if (is_selected) {
                    Icon(
                        imageVector = TablerIcons.CircleCheck,
                        contentDescription = null,
                        tint = colors.accent_blue,
                        modifier = Modifier.size(24.dp),
                    )
                }
            }

            Spacer(Modifier.height(4.dp))

            val price_text = if (plan.price_cents > 0) {
                stringResource(
                    R.string.settings_price_per_interval,
                    org.astermail.android.billing.format_money(plan.price_cents.toLong(), currency),
                    org.astermail.android.billing.billing_interval_label(context, plan.billing_period ?: billing_interval),
                )
            } else {
                stringResource(R.string.free_forever)
            }
            Text(text = price_text, color = colors.text_secondary, fontSize = 15.sp)

            val localized_desc = when (plan.code.lowercase()) {
                "free" -> stringResource(R.string.plan_desc_free)
                "star" -> stringResource(R.string.plan_desc_star)
                "nova" -> stringResource(R.string.plan_desc_nova)
                "supernova" -> stringResource(R.string.plan_desc_supernova)
                "duo" -> stringResource(R.string.settings_plan_duo_tagline)
                "family" -> stringResource(R.string.settings_plan_family_tagline)
                else -> plan.description
            }
            if (!localized_desc.isNullOrBlank()) {
                Spacer(Modifier.height(4.dp))
                Text(text = localized_desc, color = colors.text_tertiary, fontSize = 13.sp)
            }

            Spacer(Modifier.height(AsterSpacing.sm))

            val storage_text = format_plan_bytes(plan.storage_limit_bytes)
            val aliases_text = if (plan.max_email_aliases < 0) {
                stringResource(R.string.unlimited_aliases)
            } else {
                androidx.compose.ui.res.pluralStringResource(R.plurals.aliases_count_plural, plan.max_email_aliases, plan.max_email_aliases)
            }
            val domains_text = if (plan.max_custom_domains < 0) {
                stringResource(R.string.unlimited_domains)
            } else {
                androidx.compose.ui.res.pluralStringResource(R.plurals.domains_count_plural, plan.max_custom_domains, plan.max_custom_domains)
            }
            Text(
                text = stringResource(R.string.plan_features_format_v2, storage_text, aliases_text, domains_text),
                color = colors.text_tertiary,
                fontSize = 12.sp,
            )
        }
    }
}

private fun format_plan_bytes(bytes: Long): String {
    if (bytes <= 0) return "0 B"
    val units = arrayOf("B", "KB", "MB", "GB", "TB")
    var size = bytes.toDouble()
    var unit = 0
    while (size >= 1024 && unit < units.size - 1) {
        size /= 1024
        unit++
    }
    return if (unit == 0) "%d %s".format(bytes, units[unit])
    else "%.1f %s".format(size, units[unit])
}

private fun format_plan_price(cents: Int): String {
    val amount = cents / 100.0
    return try {
        val fmt = java.text.NumberFormat.getCurrencyInstance(java.util.Locale.getDefault())
        fmt.currency = java.util.Currency.getInstance("USD")
        fmt.format(amount)
    } catch (_: Throwable) {
        "$%.2f".format(amount)
    }
}

@Composable
private fun plan_interval_label(interval: String): String = when (interval.lowercase()) {
    "year", "yearly", "annual" -> stringResource(R.string.billing_period_year)
    else -> stringResource(R.string.billing_period_month)
}
