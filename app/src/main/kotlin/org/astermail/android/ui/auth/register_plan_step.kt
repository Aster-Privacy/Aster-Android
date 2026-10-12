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
import org.astermail.android.ui.upgrade.UpgradePlanCard
import org.astermail.android.ui.upgrade.upgrade_plan_display_name
import org.astermail.android.ui.upgrade.upgrade_plans_split_by_period

private val signup_plan_codes = listOf("star", "nova", "supernova")

private const val SIGNUP_RECOMMENDED_PLAN = "nova"

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
    val paid_at_entry = remember { has_paid_plan(billing_vm.state.value.subscription) }
    var selected_code by remember { mutableStateOf(SIGNUP_RECOMMENDED_PLAN) }
    var billing_interval by remember { mutableStateOf("year") }
    var plans_unavailable by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        billing_vm.clear_messages()
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
        open_billing_tab(context, url)
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

    val is_paid = has_paid_plan(state.subscription)
    LaunchedEffect(is_paid) {
        if (is_paid && !paid_at_entry) {
            billing_vm.consume_play_confirmation()
            on_continue()
        }
    }

    val plans = state.available_plans
    val currency = state.play_currency?.takeIf { state.play_enabled }
        ?: state.subscription?.currency?.takeIf { it.isNotBlank() }
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
    val is_loading_plans = plans.isEmpty() && !plans_unavailable && !state.plans_failed
    val save_percent = active_plan?.let { signup_plan_save_percent(plans, it.code) }
    val active_price = active_plan?.let { plan ->
        play_price_label(state.play_offers, state.play_products, plan.code, effective_interval)
            ?.takeIf { play_install }
            ?: format_money(signup_plan_price_cents(plan, effective_interval).toLong(), currency)
    }

    Column(modifier = Modifier.fillMaxSize()) {
        Column(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = AsterSpacing.xxl)
                .padding(top = AsterSpacing.md, bottom = AsterSpacing.lg),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(
                text = stringResource(R.string.choose_your_plan),
                color = colors.text_primary,
                fontSize = 30.sp,
                lineHeight = 36.sp,
                fontWeight = FontWeight.ExtraBold,
                letterSpacing = (-0.3).sp,
                textAlign = TextAlign.Center,
            )
            Spacer(Modifier.height(AsterSpacing.md))
            Text(
                text = stringResource(R.string.signup_plan_subtitle),
                color = colors.text_tertiary,
                fontSize = 14.sp,
                textAlign = TextAlign.Center,
            )

            Spacer(Modifier.height(AsterSpacing.xl))

            state.error?.let { message ->
                error_banner(message = message)
                Spacer(Modifier.height(AsterSpacing.lg))
            }
            state.info?.let { message ->
                plan_step_notice(title = null, message = message)
                Spacer(Modifier.height(AsterSpacing.lg))
            }
            val abandoned_plan = state.checkout_abandoned_plan
            if (abandoned_plan != null && !state.checking_payment) {
                plan_step_notice(
                    title = stringResource(
                        R.string.finish_plan_setup_title,
                        org.astermail.android.billing.plan_display_name(plans, abandoned_plan) ?: abandoned_plan,
                    ),
                    message = stringResource(R.string.finish_plan_setup_message),
                )
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
                plan_step_notice(title = null, message = stringResource(R.string.billing_play_success))
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
                    Spacer(Modifier.height(AsterSpacing.lg))
                }

                options.forEach { plan ->
                    UpgradePlanCard(
                        plan = plan,
                        is_selected = plan.code == active_plan?.code,
                        is_recommended = plan.code.equals(SIGNUP_RECOMMENDED_PLAN, ignoreCase = true),
                        billing_interval = effective_interval,
                        currency = currency,
                        price_label = if (play_install) {
                            play_price_label(state.play_offers, state.play_products, plan.code, effective_interval)
                        } else {
                            null
                        },
                        on_select = {
                            selected_code = plan.code
                            billing_vm.clear_messages()
                        },
                    )
                    Spacer(Modifier.height(AsterSpacing.md))
                }
            }
        }

        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = AsterSpacing.xxl)
                .padding(bottom = AsterSpacing.lg),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            if (paid_at_entry) {
                AsterButton(
                    label = stringResource(R.string.continue_action),
                    onClick = on_continue,
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
                    Spacer(Modifier.height(AsterSpacing.md))
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
                    onClick = on_continue,
                )
            }
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
