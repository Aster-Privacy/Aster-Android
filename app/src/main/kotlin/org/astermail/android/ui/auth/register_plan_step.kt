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

import android.content.Intent
import android.net.Uri
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
import androidx.compose.runtime.mutableIntStateOf
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

private val fallback_plans = listOf(
    AvailablePlan(
        id = "free",
        code = "free",
        name = "Free",
        description = "Get started with Aster",
        storage_limit_bytes = 10L * 1024 * 1024 * 1024,
        max_email_aliases = 5,
        max_custom_domains = 1,
        price_cents = 0,
        billing_period = "month",
    ),
    AvailablePlan(
        id = "star_m",
        code = "star",
        name = "Star",
        description = "More storage, more aliases, and your first custom domain.",
        storage_limit_bytes = 50L * 1024 * 1024 * 1024,
        max_email_aliases = 15,
        max_custom_domains = 5,
        price_cents = 299,
        billing_period = "month",
    ),
    AvailablePlan(
        id = "star_y",
        code = "star",
        name = "Star",
        description = "More storage, more aliases, and your first custom domain.",
        storage_limit_bytes = 50L * 1024 * 1024 * 1024,
        max_email_aliases = 15,
        max_custom_domains = 5,
        price_cents = 2899,
        billing_period = "year",
    ),
    AvailablePlan(
        id = "nova_m",
        code = "nova",
        name = "Nova",
        description = "More storage, custom domains, and unlimited aliases.",
        storage_limit_bytes = 500L * 1024 * 1024 * 1024,
        max_email_aliases = -1,
        max_custom_domains = 30,
        price_cents = 899,
        billing_period = "month",
    ),
    AvailablePlan(
        id = "nova_y",
        code = "nova",
        name = "Nova",
        description = "More storage, custom domains, and unlimited aliases.",
        storage_limit_bytes = 500L * 1024 * 1024 * 1024,
        max_email_aliases = -1,
        max_custom_domains = 30,
        price_cents = 8699,
        billing_period = "year",
    ),
    AvailablePlan(
        id = "supernova_m",
        code = "supernova",
        name = "Supernova",
        description = "Maximum storage, unlimited everything, and dedicated support.",
        storage_limit_bytes = 5L * 1024 * 1024 * 1024 * 1024,
        max_email_aliases = -1,
        max_custom_domains = -1,
        price_cents = 1799,
        billing_period = "month",
    ),
    AvailablePlan(
        id = "supernova_y",
        code = "supernova",
        name = "Supernova",
        description = "Maximum storage, unlimited everything, and dedicated support.",
        storage_limit_bytes = 5L * 1024 * 1024 * 1024 * 1024,
        max_email_aliases = -1,
        max_custom_domains = -1,
        price_cents = 17399,
        billing_period = "year",
    ),
)

@Composable
fun RegisterPlanStep(
    on_continue: () -> Unit,
    billing_vm: BillingViewModel = hiltViewModel(),
) {
    val colors = AsterMaterial.colors
    val state by billing_vm.state.collectAsStateWithLifecycle()
    val context = LocalContext.current
    var selected_code by remember { mutableStateOf("free") }
    var billing_interval by remember { mutableStateOf("month") }
    var retry_count by remember { mutableIntStateOf(0) }
    var used_fallback by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        billing_vm.load_plans()
        delay(2000)
        if (billing_vm.state.value.available_plans.isEmpty()) {
            billing_vm.load_plans()
            delay(3000)
            if (billing_vm.state.value.available_plans.isEmpty()) {
                used_fallback = true
            }
        }
    }

    LaunchedEffect(state.checkout_url) {
        val url = state.checkout_url ?: return@LaunchedEffect
        try {
            context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)))
        } catch (_: Throwable) {
        }
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

    val api_plans = state.available_plans
    val plans = if (api_plans.isNotEmpty()) api_plans else if (used_fallback) fallback_plans else emptyList()

    val has_yearly = plans.any { it.billing_period == "year" && it.price_cents > 0 }
    val has_monthly = plans.any { it.billing_period == "month" && it.price_cents > 0 }
    val show_billing_toggle = has_yearly && has_monthly

    val monthly_plans = plans.filter { it.billing_period == "month" || it.price_cents == 0 }
    val yearly_plans = plans.filter { it.billing_period == "year" || it.price_cents == 0 }
    val display_plans = if (billing_interval == "year" && has_yearly) yearly_plans else monthly_plans

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = AsterSpacing.xxl),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Spacer(Modifier.height(AsterSpacing.xl))

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

        if (plans.isEmpty()) {
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
                    billing_interval = billing_interval,
                    on_select = { selected_code = plan.code },
                )
                Spacer(Modifier.height(AsterSpacing.md))
            }
        }

        Spacer(Modifier.height(AsterSpacing.xl))

        if (selected_code == "free") {
            AsterButton(
                label = stringResource(R.string.continue_with_free),
                onClick = on_continue,
            )
        } else {
            AsterButton(
                label = stringResource(R.string.continue_with_upgrade),
                onClick = {
                    if (used_fallback && api_plans.isEmpty()) {
                        billing_vm.load_plans()
                    }
                    billing_vm.start_checkout(selected_code, billing_interval)
                },
                is_loading = state.is_acting,
            )
            Spacer(Modifier.height(AsterSpacing.sm))
            AsterSecondaryButton(
                label = stringResource(R.string.skip_for_now),
                onClick = on_continue,
            )
        }

        Spacer(Modifier.height(AsterSpacing.xxl))
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
    on_select: () -> Unit,
) {
    val colors = AsterMaterial.colors
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
                val amount = plan.price_cents / 100.0
                "$%.2f".format(amount) + " / " + (plan.billing_period ?: billing_interval)
            } else {
                stringResource(R.string.free_forever)
            }
            Text(text = price_text, color = colors.text_secondary, fontSize = 15.sp)

            val localized_desc = when (plan.code.lowercase()) {
                "free" -> stringResource(R.string.plan_desc_free)
                "star" -> stringResource(R.string.plan_desc_star)
                "nova" -> stringResource(R.string.plan_desc_nova)
                "supernova" -> stringResource(R.string.plan_desc_supernova)
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
