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

import android.content.Intent
import android.net.Uri
import androidx.annotation.StringRes
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Check
import androidx.compose.material.icons.outlined.Star
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import org.astermail.android.R
import org.astermail.android.billing.BillingViewModel
import org.astermail.android.design.AsterMaterial
import org.astermail.android.design.AsterRadius
import org.astermail.android.design.AsterSpacing
import org.astermail.android.design.SquircleShape
import org.astermail.android.design.components.AsterButton
import org.astermail.android.design.components.AsterCard
import org.astermail.android.design.components.AsterDivider
import org.astermail.android.design.components.AsterSecondaryButton
import org.astermail.android.settings.SettingsViewModel

private data class plan_option(
    val id: String,
    @StringRes val name_res: Int,
    @StringRes val tagline_res: Int,
    @StringRes val price_res: Int,
    val features: List<Int>,
    val highlight: Boolean = false,
)

private val plans = listOf(
    plan_option(
        id = "free",
        name_res = R.string.plan_name_free,
        tagline_res = R.string.settings_plan_free_tagline,
        price_res = R.string.settings_plan_price_free,
        features = listOf(
            R.string.settings_plan_bullet_free_storage,
            R.string.settings_plan_bullet_one_alias,
            R.string.settings_plan_bullet_e2ee,
            R.string.settings_plan_bullet_zero_knowledge,
        ),
    ),
    plan_option(
        id = "nova",
        name_res = R.string.plan_name_nova,
        tagline_res = R.string.settings_plan_nova_tagline,
        price_res = R.string.settings_plan_price_nova,
        features = listOf(
            R.string.settings_plan_bullet_nova_storage,
            R.string.settings_plan_bullet_unlimited_aliases,
            R.string.settings_plan_bullet_custom_domains,
            R.string.settings_plan_bullet_priority_support,
            R.string.settings_plan_bullet_advanced_filters,
        ),
        highlight = true,
    ),
    plan_option(
        id = "supernova",
        name_res = R.string.plan_name_supernova,
        tagline_res = R.string.settings_plan_supernova_tagline,
        price_res = R.string.settings_plan_price_supernova,
        features = listOf(
            R.string.settings_plan_bullet_nova_storage,
            R.string.settings_plan_bullet_unlimited_aliases,
            R.string.settings_plan_bullet_custom_domains,
            R.string.settings_plan_bullet_priority_support,
            R.string.settings_plan_bullet_ghost_aliases,
            R.string.settings_plan_bullet_catch_all,
        ),
    ),
)

private fun normalize_plan_id(plan_name: String?): String {
    val lower = plan_name?.trim()?.lowercase().orEmpty()
    return when {
        lower.contains("supernova") -> "supernova"
        lower.contains("nova") -> "nova"
        lower.contains("star") -> "star"
        lower.contains("pro") -> "pro"
        lower.contains("plus") -> "plus"
        lower.isBlank() || lower.contains("free") -> "free"
        else -> lower
    }
}

private fun features_for_plan_id(plan_id: String): List<Int> = when (plan_id) {
    "supernova", "nova" -> listOf(
        R.string.settings_plan_bullet_nova_storage,
        R.string.settings_plan_bullet_unlimited_aliases,
        R.string.settings_plan_bullet_custom_domains,
        R.string.settings_plan_bullet_priority_support,
        R.string.settings_plan_bullet_advanced_filters,
        R.string.settings_plan_bullet_ghost_aliases,
        R.string.settings_plan_bullet_catch_all,
    )
    "star" -> listOf(
        R.string.settings_plan_bullet_star_storage,
        R.string.settings_plan_bullet_unlimited_aliases,
        R.string.settings_plan_bullet_custom_domains,
        R.string.settings_plan_bullet_priority_support,
        R.string.settings_plan_bullet_advanced_filters,
    )
    "pro" -> listOf(
        R.string.settings_plan_bullet_pro_basic_storage,
        R.string.settings_plan_bullet_ten_aliases,
        R.string.settings_plan_bullet_custom_domains,
        R.string.settings_plan_bullet_advanced_filters,
    )
    else -> listOf(
        R.string.settings_plan_bullet_free_storage,
        R.string.settings_plan_bullet_one_alias,
        R.string.settings_plan_bullet_e2ee,
        R.string.settings_plan_bullet_zero_knowledge,
    )
}

@Composable
fun SubscriptionsScreen(
    on_back: () -> Unit,
    on_open: (id: String) -> Unit = {},
) {
    val vm: SettingsViewModel = hiltViewModel()
    val billing_vm: BillingViewModel = hiltViewModel()
    val state by vm.state.collectAsStateWithLifecycle()
    val billing_state by billing_vm.state.collectAsStateWithLifecycle()
    val colors = AsterMaterial.colors
    val context = LocalContext.current

    LaunchedEffect(Unit) { vm.load_subscription() }

    LaunchedEffect(billing_state.checkout_url) {
        val url = billing_state.checkout_url ?: return@LaunchedEffect
        runCatching { context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url))) }
        billing_vm.consume_checkout_url()
    }

    LaunchedEffect(billing_state.portal_url) {
        val url = billing_state.portal_url ?: return@LaunchedEffect
        runCatching { context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url))) }
        billing_vm.consume_portal_url()
    }

    val lifecycle_owner = LocalLifecycleOwner.current
    DisposableEffect(lifecycle_owner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) billing_vm.on_resume()
        }
        lifecycle_owner.lifecycle.addObserver(observer)
        onDispose { lifecycle_owner.lifecycle.removeObserver(observer) }
    }

    val sub = state.subscription
    val current_plan_id = normalize_plan_id(sub?.effective_plan_name)
    val current_plan_features = remember(current_plan_id) { features_for_plan_id(current_plan_id) }
    val default_interval = stringResource(R.string.settings_interval_default)
    val plan_free_label = stringResource(R.string.plan_name_free)

    detail_scaffold(title = stringResource(R.string.plan_billing), on_back = on_back) {
        if (state.is_loading && sub == null) {
            Box(
                modifier = Modifier.fillMaxWidth().padding(AsterSpacing.xxl),
                contentAlignment = Alignment.Center,
            ) {
                CircularProgressIndicator(color = colors.accent_blue, modifier = Modifier.size(24.dp))
            }
        } else {
            AsterCard(modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(AsterSpacing.lg)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = sub?.effective_plan_name ?: plan_free_label,
                                color = colors.text_primary,
                                fontSize = 22.sp,
                                fontWeight = FontWeight.Bold,
                            )
                            Text(
                                text = stringResource(R.string.current_plan),
                                color = colors.text_tertiary,
                                fontSize = 13.sp,
                            )
                        }
                        if (sub != null && sub.effective_price_cents > 0) {
                            Text(
                                text = stringResource(
                                    R.string.settings_price_per_interval,
                                    sub.effective_price_cents / 100.0,
                                    sub.effective_interval ?: default_interval,
                                ),
                                color = colors.text_primary,
                                fontSize = 17.sp,
                                fontWeight = FontWeight.SemiBold,
                            )
                        }
                    }
                    val period_end = sub?.current_period_end
                    if (period_end != null) {
                        Spacer(Modifier.size(AsterSpacing.sm))
                        Text(
                            text = stringResource(R.string.renews_format, period_end.take(10)),
                            color = colors.text_tertiary,
                            fontSize = 13.sp,
                        )
                    }
                    Spacer(Modifier.size(AsterSpacing.lg))
                    AsterSecondaryButton(
                        label = stringResource(R.string.manage_subscription),
                        onClick = { billing_vm.open_portal() },
                    )
                }
            }
        }
        v_gap(AsterSpacing.lg)
        section_label(stringResource(R.string.plan_includes))
        AsterCard(modifier = Modifier.fillMaxWidth()) {
            Column(modifier = Modifier.padding(AsterSpacing.lg)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Outlined.Star,
                        contentDescription = null,
                        tint = colors.accent_blue,
                        modifier = Modifier.size(24.dp),
                    )
                    Spacer(Modifier.width(AsterSpacing.md))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = sub?.effective_plan_name ?: plan_free_label,
                            color = colors.text_primary,
                            fontSize = 20.sp,
                            fontWeight = FontWeight.Bold,
                        )
                        Text(
                            text = stringResource(R.string.current_plan),
                            color = colors.text_tertiary,
                            fontSize = 13.sp,
                        )
                    }
                    if (state.is_loading && sub == null) {
                        CircularProgressIndicator(
                            color = colors.accent_blue,
                            modifier = Modifier.size(18.dp),
                            strokeWidth = 2.dp,
                        )
                    } else if (sub != null && sub.effective_price_cents > 0) {
                        Text(
                            text = stringResource(
                                R.string.settings_price_per_interval,
                                sub.effective_price_cents / 100.0,
                                sub.effective_interval ?: default_interval,
                            ),
                            color = colors.text_primary,
                            fontSize = 16.sp,
                            fontWeight = FontWeight.SemiBold,
                        )
                    }
                }
                Spacer(Modifier.height(AsterSpacing.md))
                AsterDivider()
                Spacer(Modifier.height(AsterSpacing.md))
                current_plan_features.forEach { feature_res ->
                    Row(
                        modifier = Modifier.padding(vertical = 3.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Icon(
                            imageVector = Icons.Outlined.Check,
                            contentDescription = null,
                            tint = colors.success,
                            modifier = Modifier.size(16.dp),
                        )
                        Spacer(Modifier.width(AsterSpacing.sm))
                        Text(text = stringResource(feature_res), color = colors.text_primary, fontSize = 13.sp)
                    }
                }
            }
        }

        v_gap(AsterSpacing.lg)

        section_label(stringResource(R.string.upgrade))
        plans.forEach { plan ->
            plan_card(
                plan = plan,
                is_current = plan.id == current_plan_id,
                on_choose = {
                    if (plan.id == "free") {
                        billing_vm.open_portal()
                    } else {
                        billing_vm.start_checkout(plan.id, "month")
                    }
                },
            )
            v_gap(AsterSpacing.md)
        }

        v_gap(AsterSpacing.sm)
        AsterSecondaryButton(
            label = stringResource(R.string.manage_billing_browser),
            onClick = { billing_vm.open_portal() },
        )
        v_gap(AsterSpacing.xxl)
    }
}

@Composable
private fun plan_card(plan: plan_option, is_current: Boolean, on_choose: () -> Unit) {
    val colors = AsterMaterial.colors
    val plan_name = stringResource(plan.name_res)
    AsterCard(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(AsterSpacing.lg)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(modifier = Modifier.weight(1f)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            text = plan_name,
                            color = colors.text_primary,
                            fontSize = 17.sp,
                            fontWeight = FontWeight.SemiBold,
                        )
                        if (plan.highlight) {
                            Spacer(Modifier.width(AsterSpacing.sm))
                            Box(
                                modifier = Modifier
                                    .clip(SquircleShape(AsterRadius.sm))
                                    .background(colors.accent_blue.copy(alpha = 0.12f))
                                    .padding(horizontal = AsterSpacing.sm, vertical = 2.dp),
                            ) {
                                Text(
                                    text = stringResource(R.string.most_popular),
                                    color = colors.accent_blue,
                                    fontSize = 10.sp,
                                    fontWeight = FontWeight.SemiBold,
                                )
                            }
                        }
                    }
                    Text(
                        text = stringResource(plan.tagline_res),
                        color = colors.text_tertiary,
                        fontSize = 12.sp,
                    )
                }
                Text(
                    text = stringResource(plan.price_res),
                    color = colors.text_primary,
                    fontSize = 16.sp,
                    fontWeight = FontWeight.SemiBold,
                )
            }
            Spacer(Modifier.height(AsterSpacing.md))
            AsterDivider()
            Spacer(Modifier.height(AsterSpacing.md))
            plan.features.forEach { feature_res ->
                Row(
                    modifier = Modifier.padding(vertical = 3.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(
                        imageVector = Icons.Outlined.Check,
                        contentDescription = null,
                        tint = colors.success,
                        modifier = Modifier.size(16.dp),
                    )
                    Spacer(Modifier.width(AsterSpacing.sm))
                    Text(text = stringResource(feature_res), color = colors.text_primary, fontSize = 13.sp)
                }
            }
            Spacer(Modifier.height(AsterSpacing.md))
            if (is_current) {
                AsterSecondaryButton(label = stringResource(R.string.current_plan), onClick = {}, enabled = false)
            } else if (plan.id == "free") {
                AsterSecondaryButton(label = stringResource(R.string.downgrade), onClick = on_choose)
            } else {
                AsterButton(label = stringResource(R.string.upgrade_to, plan_name), onClick = on_choose)
            }
        }
    }
}
