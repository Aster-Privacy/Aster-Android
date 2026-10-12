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

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.selection.selectable
import androidx.compose.material3.Icon
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.semantics.Role
import compose.icons.TablerIcons
import compose.icons.tablericons.Circle
import compose.icons.tablericons.CircleCheck
import org.astermail.android.billing.billing_interval_per_label
import org.astermail.android.design.SquircleShape
import org.astermail.android.design.acrylic
import org.astermail.android.ui.settings.detail.format_bytes
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
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withTimeoutOrNull
import org.astermail.android.R
import org.astermail.android.api.billing.AvailablePlan
import org.astermail.android.api.billing.SubscriptionResponse
import org.astermail.android.billing.BillingViewModel
import org.astermail.android.billing.format_money
import org.astermail.android.billing.open_billing_tab
import org.astermail.android.billing.play_price_label
import org.astermail.android.billing.remember_play_install
import org.astermail.android.billing.yearly_savings_percent
import org.astermail.android.design.AsterMaterial
import org.astermail.android.design.AsterShapes
import org.astermail.android.design.AsterSpacing
import org.astermail.android.design.components.AsterButton
import org.astermail.android.design.components.AsterSecondaryButton
import org.astermail.android.design.tonal_surface_color
import org.astermail.android.ui.settings.detail.aster_segmented
import org.astermail.android.ui.settings.detail.switcher_option
import org.astermail.android.ui.upgrade.upgrade_plan_display_name
import org.astermail.android.ui.upgrade.upgrade_plans_split_by_period

private val signup_plan_codes = listOf("star", "nova", "supernova")

private const val SIGNUP_RECOMMENDED_PLAN = "nova"

private const val PLAY_PRICES_WAIT_MS = 6000L

private val whole_unit_suffix = Regex("[.,]0 ")

internal fun signup_plan_options(plans: List<AvailablePlan>, interval: String): List<AvailablePlan> {
    val split = upgrade_plans_split_by_period(plans)
    return signup_plan_codes.mapNotNull { code ->
        plans.firstOrNull { plan ->
            plan.code.equals(code, ignoreCase = true) && when {
                split -> (plan.billing_period ?: "month") == interval && plan.price_cents > 0
                interval == "year" -> plan.yearly_price_cents > 0
                else -> plan.price_cents > 0
            }
        }
    }
}

internal fun signup_plan_price_cents(plan: AvailablePlan, interval: String): Int =
    if (interval == "year" && plan.yearly_price_cents > 0) plan.yearly_price_cents else plan.price_cents

internal fun signup_plan_save_percent(plans: List<AvailablePlan>, code: String): Int? {
    val monthly = signup_plan_options(plans, "month").firstOrNull { it.code.equals(code, ignoreCase = true) }
    val yearly = signup_plan_options(plans, "year").firstOrNull { it.code.equals(code, ignoreCase = true) }
    if (monthly == null || yearly == null) return null
    return yearly_savings_percent(
        signup_plan_price_cents(monthly, "month"),
        signup_plan_price_cents(yearly, "year"),
    )?.takeIf { it > 0 }
}

private fun has_paid_plan(subscription: SubscriptionResponse?): Boolean =
    subscription != null &&
        subscription.plan.price_cents > 0 &&
        (subscription.status == "active" || subscription.status == "trialing")

@Composable
fun RegisterPlanStep(
    on_continue: () -> Unit,
    billing_vm: BillingViewModel = org.astermail.android.billing.billing_view_model(),
) {
    val colors = AsterMaterial.colors
    val state by billing_vm.state.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val play_install = remember_play_install()
    var subscription_fresh by remember { mutableStateOf(false) }
    var paid_at_entry by remember { mutableStateOf(false) }
    var has_continued by remember { mutableStateOf(false) }
    var refresh_attempt by remember { mutableStateOf(0) }
    var selected_code by remember { mutableStateOf(SIGNUP_RECOMMENDED_PLAN) }
    var billing_interval by remember { mutableStateOf("year") }
    var plans_unavailable by remember { mutableStateOf(false) }
    var play_settled by remember { mutableStateOf(!play_install) }

    val finish: () -> Unit = {
        if (!has_continued) {
            has_continued = true
            billing_vm.clear_messages()
            billing_vm.consume_play_confirmation()
            on_continue()
        }
    }

    val is_busy = state.is_acting || state.checking_payment || state.awaiting_checkout
    LaunchedEffect(is_busy, refresh_attempt) {
        if (is_busy || subscription_fresh) return@LaunchedEffect
        if (billing_vm.refresh_subscription()) {
            paid_at_entry = has_paid_plan(billing_vm.state.value.subscription)
            subscription_fresh = true
        }
    }

    LaunchedEffect(Unit) {
        billing_vm.clear_messages()
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

    LaunchedEffect(Unit) {
        if (play_settled) return@LaunchedEffect
        withTimeoutOrNull(PLAY_PRICES_WAIT_MS) {
            billing_vm.ensure_play_config()?.join()
            if (billing_vm.state.value.play_enabled) {
                billing_vm.state.first { it.play_offers.isNotEmpty() }
            }
        }
        play_settled = true
    }

    LaunchedEffect(state.checkout_url) {
        val url = state.checkout_url ?: return@LaunchedEffect
        open_billing_tab(context, url)
        billing_vm.consume_checkout_url()
    }

    val lifecycle_owner = LocalLifecycleOwner.current
    DisposableEffect(lifecycle_owner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                val was_awaiting_checkout = billing_vm.state.value.awaiting_checkout
                billing_vm.on_resume()
                if (!subscription_fresh) {
                    refresh_attempt += 1
                } else if (!was_awaiting_checkout) {
                    billing_vm.load_subscription()
                }
            }
        }
        lifecycle_owner.lifecycle.addObserver(observer)
        onDispose { lifecycle_owner.lifecycle.removeObserver(observer) }
    }

    val is_paid = subscription_fresh && has_paid_plan(state.subscription)
    LaunchedEffect(is_paid, paid_at_entry) {
        if (is_paid && !paid_at_entry) finish()
    }

    val plans = state.available_plans
    val currency = state.play_currency?.takeIf { state.play_enabled }
        ?: state.subscription?.currency?.takeIf { subscription_fresh && it.isNotBlank() }
        ?: "usd"
    val yearly_options = signup_plan_options(plans, "year")
    val monthly_options = signup_plan_options(plans, "month")
    val effective_interval = when {
        yearly_options.isEmpty() -> "month"
        monthly_options.isEmpty() -> "year"
        else -> billing_interval
    }
    val options = if (effective_interval == "year") yearly_options else monthly_options
    val active_plan = options.firstOrNull { it.code.equals(selected_code, ignoreCase = true) }
        ?: options.firstOrNull { it.code.equals(SIGNUP_RECOMMENDED_PLAN, ignoreCase = true) }
        ?: options.firstOrNull()
    val is_loading_plans = if (plans.isEmpty()) {
        !plans_unavailable && !state.plans_failed
    } else {
        options.isEmpty() && !play_settled
    }
    val save_percent = active_plan?.let { signup_plan_save_percent(plans, it.code) }
    val active_price = active_plan?.let { plan ->
        play_price_label(state.play_offers, state.play_products, plan.code, effective_interval)
            ?.takeIf { play_install }
            ?: format_money(signup_plan_price_cents(plan, effective_interval).toLong(), currency)
    }

    val show_abandoned_notice = state.checkout_abandoned_plan != null && !state.checking_payment && !is_paid

    Column(modifier = Modifier.fillMaxSize()) {
        Column(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = AsterSpacing.xxl)
                .padding(top = AsterSpacing.xs),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(
                text = stringResource(R.string.choose_your_plan),
                color = colors.text_primary,
                fontSize = 28.sp,
                lineHeight = 34.sp,
                fontWeight = FontWeight.ExtraBold,
                letterSpacing = (-0.3).sp,
                textAlign = TextAlign.Center,
            )
            Spacer(Modifier.height(AsterSpacing.sm))
            Text(
                text = stringResource(
                    when {
                        paid_at_entry -> R.string.billing_play_success
                        show_abandoned_notice -> R.string.finish_plan_setup_message
                        else -> R.string.signup_plan_subtitle
                    },
                ),
                color = colors.text_tertiary,
                fontSize = 14.sp,
                textAlign = TextAlign.Center,
            )

            Spacer(Modifier.height(AsterSpacing.lg))

            state.error?.let { message ->
                error_banner(message = message)
                Spacer(Modifier.height(AsterSpacing.lg))
            }
            state.info?.let { message ->
                plan_step_notice(title = null, message = message)
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

            if (paid_at_entry) {
                Spacer(Modifier.height(AsterSpacing.xs))
            } else if (is_loading_plans) {
                Box(
                    modifier = Modifier.fillMaxWidth().padding(AsterSpacing.xxl),
                    contentAlignment = Alignment.Center,
                ) {
                    CircularProgressIndicator(color = colors.accent_blue, modifier = Modifier.size(24.dp))
                }
            } else if (options.isEmpty()) {
                Text(
                    text = stringResource(R.string.plans_unavailable),
                    color = colors.text_secondary,
                    fontSize = 14.sp,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth(),
                )
            } else {
                if (yearly_options.isNotEmpty() && monthly_options.isNotEmpty()) {
                    aster_segmented(
                        value = effective_interval,
                        options = listOf(
                            switcher_option(id = "month", label = stringResource(R.string.monthly)),
                            switcher_option(id = "year", label = stringResource(R.string.yearly)),
                        ),
                        on_change = { billing_interval = it },
                    )
                    if (save_percent != null) {
                        Spacer(Modifier.height(AsterSpacing.sm))
                        Text(
                            text = stringResource(R.string.signup_plan_yearly_savings, save_percent),
                            color = colors.accent_blue,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Medium,
                            textAlign = TextAlign.Center,
                            modifier = Modifier.fillMaxWidth(),
                        )
                    }
                    Spacer(Modifier.height(AsterSpacing.md))
                }

                options.forEach { plan ->
                    signup_plan_row(
                        plan = plan,
                        is_selected = plan.code == active_plan?.code,
                        is_recommended = plan.code.equals(SIGNUP_RECOMMENDED_PLAN, ignoreCase = true),
                        billing_interval = effective_interval,
                        price = play_price_label(state.play_offers, state.play_products, plan.code, effective_interval)
                            ?.takeIf { play_install }
                            ?: format_money(signup_plan_price_cents(plan, effective_interval).toLong(), currency),
                        on_select = {
                            selected_code = plan.code
                            billing_vm.clear_messages()
                        },
                    )
                    Spacer(Modifier.height(AsterSpacing.sm))
                }
            }
        }

        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = AsterSpacing.xxl)
                .padding(top = AsterSpacing.sm, bottom = AsterSpacing.lg),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            if (paid_at_entry) {
                AsterButton(
                    label = stringResource(R.string.continue_action),
                    onClick = finish,
                )
            } else {
                if (active_plan != null && active_price != null) {
                    Text(
                        text = stringResource(
                            when {
                                play_install && effective_interval == "year" -> R.string.signup_plan_terms_play_year
                                play_install -> R.string.signup_plan_terms_play_month
                                effective_interval == "year" -> R.string.signup_plan_terms_year
                                else -> R.string.signup_plan_terms_month
                            },
                            active_price,
                        ),
                        color = colors.text_tertiary,
                        fontSize = 12.sp,
                        lineHeight = 16.sp,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.fillMaxWidth(),
                    )
                    Spacer(Modifier.height(AsterSpacing.sm))
                    AsterButton(
                        label = stringResource(R.string.upgrade_get_plan, upgrade_plan_display_name(active_plan)),
                        onClick = {
                            billing_vm.clear_messages()
                            billing_vm.start_checkout(active_plan.code, effective_interval, currency)
                        },
                        is_loading = state.is_acting,
                    )
                    Spacer(Modifier.height(AsterSpacing.sm))
                }
                AsterSecondaryButton(
                    label = stringResource(R.string.continue_with_free),
                    onClick = finish,
                )
            }
        }
    }
}

@Composable
private fun signup_plan_row(
    plan: AvailablePlan,
    is_selected: Boolean,
    is_recommended: Boolean,
    billing_interval: String,
    price: String,
    on_select: () -> Unit,
) {
    val colors = AsterMaterial.colors
    val context = LocalContext.current
    val shape = SquircleShape(18.dp)
    val aliases_text = if (plan.max_email_aliases < 0) {
        stringResource(R.string.unlimited_aliases)
    } else {
        pluralStringResource(R.plurals.aliases_count_plural, plan.max_email_aliases, plan.max_email_aliases)
    }
    val domains_text = if (plan.max_custom_domains < 0) {
        stringResource(R.string.unlimited_domains)
    } else {
        pluralStringResource(R.plurals.domains_count_plural, plan.max_custom_domains, plan.max_custom_domains)
    }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .acrylic(
                colors,
                shape,
                if (is_selected) tonal_surface_color(colors, colors.accent_blue) else colors.bg_card,
            )
            .border(
                if (is_selected) 2.dp else 1.dp,
                if (is_selected) colors.accent_blue else colors.border_secondary,
                shape,
            )
            .selectable(selected = is_selected, role = Role.RadioButton, onClick = on_select)
            .padding(horizontal = AsterSpacing.lg, vertical = AsterSpacing.md),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            imageVector = if (is_selected) TablerIcons.CircleCheck else TablerIcons.Circle,
            contentDescription = null,
            tint = if (is_selected) colors.accent_blue else colors.text_tertiary,
            modifier = Modifier.size(22.dp),
        )
        Spacer(Modifier.width(AsterSpacing.md))
        Column(modifier = Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = upgrade_plan_display_name(plan),
                    color = colors.text_primary,
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Bold,
                )
                if (is_recommended) {
                    Spacer(Modifier.width(AsterSpacing.sm))
                    Text(
                        text = stringResource(R.string.checkout_plan_recommended),
                        color = colors.accent_blue,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 1,
                    )
                }
            }
            Spacer(Modifier.height(2.dp))
            Text(
                text = stringResource(
                    R.string.plan_features_format_v2,
                    format_bytes(plan.storage_limit_bytes).replace(whole_unit_suffix, " "),
                    aliases_text,
                    domains_text,
                ),
                color = colors.text_tertiary,
                fontSize = 12.sp,
                lineHeight = 16.sp,
            )
        }
        Spacer(Modifier.width(AsterSpacing.md))
        Column(horizontalAlignment = Alignment.End) {
            Text(
                text = price,
                color = colors.text_primary,
                fontSize = 16.sp,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
            )
            Text(
                text = billing_interval_per_label(context, billing_interval),
                color = colors.text_tertiary,
                fontSize = 12.sp,
                maxLines = 1,
            )
        }
    }
}

@Composable
private fun plan_step_notice(title: String?, message: String) {
    val colors = AsterMaterial.colors
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(tonal_surface_color(colors, colors.accent_blue), AsterShapes.island)
            .padding(AsterSpacing.lg),
    ) {
        if (title != null) {
            Text(
                text = title,
                color = colors.text_primary,
                fontSize = 15.sp,
                fontWeight = FontWeight.SemiBold,
            )
            Spacer(Modifier.height(4.dp))
        }
        Text(
            text = message,
            color = if (title != null) colors.text_tertiary else colors.text_primary,
            fontSize = 13.sp,
        )
    }
}
