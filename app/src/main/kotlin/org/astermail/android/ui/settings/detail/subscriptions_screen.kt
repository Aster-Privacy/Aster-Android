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
import compose.icons.tablericons.*

import android.content.Context
import androidx.annotation.StringRes
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.ui.semantics.Role
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInParent
import androidx.compose.material.icons.Icons
import androidx.compose.material3.AlertDialog as M3AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import org.astermail.android.R
import org.astermail.android.billing.BillingViewModel
import org.astermail.android.billing.is_resumable_crypto_invoice
import java.util.Locale
import org.astermail.android.design.AsterMaterial
import org.astermail.android.design.AsterRadius
import org.astermail.android.design.AsterSpacing
import org.astermail.android.design.SquircleShape
import org.astermail.android.design.components.AsterButton
import org.astermail.android.design.components.AsterCard
import org.astermail.android.design.components.AsterDivider
import org.astermail.android.ui.common.open_external_url
import org.astermail.android.design.components.AsterSecondaryButton
import org.astermail.android.design.components.AsterTextField
import androidx.compose.foundation.shape.CircleShape
import org.astermail.android.settings.SettingsViewModel
import org.astermail.android.settings.shared_settings_view_model

private val LOCALE_CURRENCY_MAP = mapOf(
    "en_us" to "usd", "en_gb" to "gbp", "en_au" to "aud", "en_ca" to "cad", "en_in" to "inr",
    "fr" to "eur", "de" to "eur", "es" to "eur", "it" to "eur", "nl" to "eur",
    "pt_br" to "brl", "pt" to "eur", "ja" to "jpy", "sv" to "sek",
    "nb" to "nok", "nn" to "nok", "da" to "dkk", "pl" to "pln",
    "es_mx" to "mxn", "hi" to "inr", "zh" to "cny", "ko" to "krw",
)

private val SUPPORTED_CURRENCIES = setOf(
    "usd", "eur", "gbp", "cad", "aud", "jpy", "chf", "sek", "nok", "dkk", "pln", "brl", "mxn", "inr", "cny", "krw",
)

private fun detect_currency(): String {
    val locale = Locale.getDefault()
    val tag = "${locale.language}_${locale.country}".lowercase()
    val short = locale.language.lowercase()
    return LOCALE_CURRENCY_MAP[tag] ?: LOCALE_CURRENCY_MAP[short] ?: run {
        try {
            val jcur = java.util.Currency.getInstance(locale).currencyCode.lowercase()
            if (jcur in SUPPORTED_CURRENCIES) jcur else "usd"
        } catch (_: Throwable) { "usd" }
    }
}

private fun format_price(cents: Int, currency: String): String =
    org.astermail.android.billing.format_money(cents.toLong(), currency)

private data class plan_tier(
    val code: String,
    @StringRes val name_res: Int,
    @StringRes val tagline_res: Int,
    @StringRes val lead_res: Int?,
    val features: List<Int>,
)

private val free_features = listOf(
    R.string.settings_plan_bullet_free_storage,
    R.string.settings_plan_bullet_free_aliases,
    R.string.settings_plan_bullet_e2ee,
    R.string.settings_plan_bullet_zero_knowledge,
)

private val duo_features = listOf(
    R.string.settings_plan_bullet_duo_storage,
    R.string.settings_plan_bullet_duo_members,
    R.string.settings_plan_bullet_unlimited_aliases,
    R.string.settings_plan_bullet_shared_aliases,
    R.string.settings_plan_bullet_nova_domains,
    R.string.settings_plan_bullet_e2ee,
    R.string.settings_plan_bullet_zero_knowledge,
    R.string.settings_plan_bullet_priority_support,
)

private val family_features = listOf(
    R.string.settings_plan_bullet_family_storage,
    R.string.settings_plan_bullet_family_members,
    R.string.settings_plan_bullet_unlimited_aliases,
    R.string.settings_plan_bullet_shared_aliases,
    R.string.settings_plan_bullet_nova_domains,
    R.string.settings_plan_bullet_e2ee,
    R.string.settings_plan_bullet_zero_knowledge,
    R.string.settings_plan_bullet_priority_support,
)

private val plan_tiers = listOf(
    plan_tier(
        code = "star",
        name_res = R.string.plan_name_star,
        tagline_res = R.string.settings_plan_star_tagline,
        lead_res = null,
        features = listOf(
            R.string.settings_plan_bullet_star_storage,
            R.string.settings_plan_bullet_star_attachments,
            R.string.settings_plan_bullet_star_aliases,
            R.string.settings_plan_bullet_star_domains,
            R.string.settings_plan_bullet_daily_send_limits,
            R.string.settings_plan_bullet_star_templates,
            R.string.settings_plan_bullet_tracker_protection,
            R.string.settings_plan_bullet_vacation_reply,
            R.string.settings_plan_bullet_catch_all,
            R.string.settings_plan_bullet_auto_forwarding,
            R.string.settings_plan_bullet_quiet_hours,
            R.string.settings_plan_bullet_custom_avatars,
            R.string.settings_plan_bullet_external_accounts,
            R.string.settings_plan_bullet_bridge_access,
            R.string.settings_plan_bullet_priority_support,
        ),
    ),
    plan_tier(
        code = "nova",
        name_res = R.string.plan_name_nova,
        tagline_res = R.string.settings_plan_nova_tagline,
        lead_res = R.string.settings_plan_lead_star,
        features = listOf(
            R.string.settings_plan_bullet_nova_storage,
            R.string.settings_plan_bullet_nova_attachments,
            R.string.settings_plan_bullet_unlimited_aliases,
            R.string.settings_plan_bullet_nova_domains,
            R.string.settings_plan_bullet_daily_send_limits,
            R.string.settings_plan_bullet_unlimited_templates,
            R.string.settings_plan_bullet_unlimited_signatures,
            R.string.settings_plan_bullet_tracker_protection,
            R.string.settings_plan_bullet_carddav_import,
            R.string.settings_plan_bullet_contact_merge,
            R.string.settings_plan_bullet_encrypted_export,
            R.string.settings_plan_bullet_protected_folders,
            R.string.settings_plan_bullet_key_rotation,
            R.string.settings_plan_bullet_external_accounts,
            R.string.settings_plan_bullet_bridge_access,
        ),
    ),
    plan_tier(
        code = "supernova",
        name_res = R.string.plan_name_supernova,
        tagline_res = R.string.settings_plan_supernova_tagline,
        lead_res = R.string.settings_plan_lead_nova,
        features = listOf(
            R.string.settings_plan_bullet_supernova_storage,
            R.string.settings_plan_bullet_supernova_attachments,
            R.string.settings_plan_bullet_unlimited_aliases,
            R.string.settings_plan_bullet_unlimited_domains,
            R.string.settings_plan_bullet_daily_send_limits,
            R.string.settings_plan_bullet_tracker_protection,
            R.string.settings_plan_bullet_receipt_tracking,
            R.string.settings_plan_bullet_external_accounts,
            R.string.settings_plan_bullet_bridge_access,
            R.string.settings_plan_bullet_dedicated_support,
            R.string.settings_plan_bullet_early_access,
        ),
    ),
    plan_tier(
        code = "duo",
        name_res = R.string.plan_name_duo,
        tagline_res = R.string.settings_plan_duo_tagline,
        lead_res = null,
        features = duo_features,
    ),
    plan_tier(
        code = "family",
        name_res = R.string.plan_name_family,
        tagline_res = R.string.settings_plan_family_tagline,
        lead_res = null,
        features = family_features,
    ),
)

private val FAMILY_PLAN_CODES = setOf("duo", "family")

private fun tier_rank(code: String): Int = org.astermail.android.billing.plan_tier_rank(code)

private fun is_lower_tier(code: String?, current_code: String?): Boolean =
    tier_rank(code.orEmpty()) < tier_rank(current_code.orEmpty())

private fun plan_code_of(plan_name: String?): String = org.astermail.android.billing.plan_code_from_name(plan_name)


@Composable
fun SubscriptionsScreen(
    on_back: () -> Unit,
    on_open: (id: String) -> Unit = {},
    scroll_to_addons: Boolean = false,
    on_open_crypto_invoice: (id: String) -> Unit = {},
) {
    val vm: SettingsViewModel = shared_settings_view_model()
    val billing_vm: BillingViewModel = org.astermail.android.billing.billing_view_model()
    val state by vm.state.collectAsStateWithLifecycle()
    val billing_state by billing_vm.state.collectAsStateWithLifecycle()
    val colors = AsterMaterial.colors
    val context = LocalContext.current

    LaunchedEffect(Unit) {
        vm.load_subscription()
        vm.load_storage()
        billing_vm.load_subscription()
        billing_vm.load_plans()
        billing_vm.load_history()
        billing_vm.load_storage_addons()
        billing_vm.load_crypto_native_coins()
        billing_vm.load_pending_crypto_invoices()
        billing_vm.load_credits_and_discounts()
    }

    LaunchedEffect(billing_state.created_crypto_invoice_id) {
        val id = billing_state.created_crypto_invoice_id ?: return@LaunchedEffect
        billing_vm.consume_created_crypto_invoice()
        on_open_crypto_invoice(id)
    }

    LaunchedEffect(billing_state.info) {
        val info = billing_state.info ?: return@LaunchedEffect
        android.widget.Toast.makeText(context, info, android.widget.Toast.LENGTH_SHORT).show()
        billing_vm.clear_messages()
        vm.load_subscription()
    }

    LaunchedEffect(billing_state.checkout_url) {
        val url = billing_state.checkout_url ?: return@LaunchedEffect
        org.astermail.android.billing.open_billing_tab(context, url)
        billing_vm.consume_checkout_url()
    }

    LaunchedEffect(billing_state.portal_url) {
        val url = billing_state.portal_url ?: return@LaunchedEffect
        org.astermail.android.billing.open_billing_tab(context, url)
        billing_vm.consume_portal_url()
    }

    val lifecycle_owner = LocalLifecycleOwner.current
    DisposableEffect(lifecycle_owner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                billing_vm.on_resume()
                billing_vm.load_pending_crypto_invoices()
            }
        }
        lifecycle_owner.lifecycle.addObserver(observer)
        onDispose { lifecycle_owner.lifecycle.removeObserver(observer) }
    }

    val scroll_state = rememberScrollState()
    var addons_section_offset by remember { mutableStateOf(0f) }
    LaunchedEffect(scroll_to_addons, addons_section_offset) {
        if (scroll_to_addons && addons_section_offset > 0f) {
            kotlinx.coroutines.delay(300)
            scroll_state.animateScrollTo(addons_section_offset.toInt().coerceAtLeast(0))
        }
    }

    var pending_plan_code by remember { mutableStateOf<String?>(null) }
    var pending_addon_id by remember { mutableStateOf<String?>(null) }
    var show_payment_picker by remember { mutableStateOf(false) }
    var show_crypto_terms by remember { mutableStateOf(false) }
    var show_cancel_flow by remember { mutableStateOf(false) }
    var show_crypto_coins by remember { mutableStateOf(false) }
    var pending_term_months by remember { mutableStateOf(1) }
    var picker_method by remember { mutableStateOf(payment_method_card) }
    var show_switch_yearly by remember { mutableStateOf(false) }
    var resume_cancel_flow by remember { mutableStateOf(false) }

    LaunchedEffect(billing_state.error, show_cancel_flow) {
        val err = billing_state.error ?: return@LaunchedEffect
        if (show_cancel_flow) return@LaunchedEffect
        android.widget.Toast.makeText(context, err, android.widget.Toast.LENGTH_LONG).show()
        billing_vm.clear_messages()
    }
    LaunchedEffect(show_cancel_flow) {
        if (show_cancel_flow) billing_vm.clear_messages()
    }
    var pending_downgrade_code by remember { mutableStateOf<String?>(null) }

    val sub = state.subscription
    val billing_sub = billing_state.subscription
    val detected_currency = sub?.currency?.takeIf { it.isNotBlank() }
        ?: billing_sub?.currency?.takeIf { it.isNotBlank() }
        ?: "usd"
    val is_crypto_sub = org.astermail.android.billing.is_crypto_provider(sub?.payment_provider ?: billing_sub?.payment_provider)
    val crypto_renewal_days = org.astermail.android.billing.crypto_renewal_due(
        payment_provider = sub?.payment_provider ?: billing_sub?.payment_provider,
        paid_until = billing_sub?.paid_until ?: sub?.current_period_end,
        status = sub?.status ?: billing_sub?.status,
        today = java.time.LocalDate.now().toString(),
    )
    val current_code = sub?.plan?.code ?: plan_code_of(sub?.effective_plan_name)
    val current_features = remember(current_code) {
        when (current_code) {
            "family" -> family_features
            "duo" -> duo_features
            else -> plan_tiers.firstOrNull { it.code == current_code }?.features ?: free_features
        }
    }
    val storage_overview = state.storage
    val recommendation = compute_plan_recommendation(
        current_plan_code = current_code,
        storage_used_bytes = storage_overview?.used_bytes ?: 0L,
        storage_limit_bytes = storage_overview?.total_bytes ?: 0L,
    )
    val current_plan_name = sub?.effective_plan_name
        ?: plan_tiers.firstOrNull { it.code == current_code }?.let { stringResource(it.name_res) }
    val recommended_tier_name = plan_tiers
        .firstOrNull { it.code == recommendation.recommended_plan_code }
        ?.let { stringResource(it.name_res) }

    val default_interval = stringResource(R.string.settings_interval_default)
    val plan_free_label = stringResource(R.string.plan_name_free)
    var billing_interval by remember { mutableStateOf("year") }
    val plan_load_settled = remember_load_settled(state.is_loading)
    var resumed_checkout_plan by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(billing_state.checkout_abandoned_plan, billing_state.checking_payment) {
        val resume_plan = billing_state.checkout_abandoned_plan
        if (resume_plan == null) {
            resumed_checkout_plan = null
            return@LaunchedEffect
        }
        if (billing_state.checking_payment) return@LaunchedEffect
        if (resumed_checkout_plan == resume_plan) return@LaunchedEffect
        resumed_checkout_plan = resume_plan
        if (plan_tiers.none { it.code == resume_plan }) return@LaunchedEffect
        billing_interval = billing_state.checkout_abandoned_interval ?: billing_interval
        pending_plan_code = resume_plan
        pending_addon_id = null
        show_payment_picker = true
    }

    val current_interval = org.astermail.android.billing.normalize_billing_interval(sub?.effective_interval)
    val api_monthly_cents = org.astermail.android.billing.api_plan_price_cents(billing_state.available_plans, current_code, "month")
    val api_yearly_cents = org.astermail.android.billing.api_plan_price_cents(billing_state.available_plans, current_code, "year")
    val yearly_savings = org.astermail.android.billing.yearly_savings_percent(api_monthly_cents, api_yearly_cents)
    val plan_alternatives_allowed = sub != null && org.astermail.android.billing.can_offer_plan_alternatives(
        plan_code = current_code,
        status = sub.status,
        payment_failed_at = sub.payment_failed_at,
        grace_period_end = sub.grace_period_end,
        cancel_at_period_end = sub.cancel_at_period_end,
        payment_provider = sub.payment_provider,
        has_stripe_subscription = sub.has_stripe_subscription,
    )
    val offer_yearly_switch = plan_alternatives_allowed &&
        current_interval == "month" &&
        yearly_savings != null &&
        api_yearly_cents != null
    val cancel_offer_code = if (plan_alternatives_allowed) {
        org.astermail.android.billing.cheaper_plan_code(current_code)
    } else {
        null
    }
    val cancel_offer_label = cancel_offer_code
        ?.let { code -> plan_tiers.firstOrNull { it.code == code } }
        ?.let { tier ->
            val cents = org.astermail.android.billing.api_plan_price_cents(
                billing_state.available_plans,
                tier.code,
                current_interval,
            )
            cents?.let {
                context.getString(
                    R.string.cancel_offer_switch_plan,
                    context.getString(tier.name_res),
                    format_price(it, detected_currency) + " " +
                        org.astermail.android.billing.billing_interval_per_label(context, current_interval),
                )
            }
        }
    val lapsed = if (sub != null) {
        org.astermail.android.billing.lapsed_paid_plan(
            current_plan_code = current_code,
            history = billing_state.history,
            today = java.time.LocalDate.now().toString(),
        )?.takeIf { plan_tiers.any { tier -> tier.code == plan_code_of(it.plan_name) } }
    } else {
        null
    }
    var lapsed_dismissed by remember(lapsed) {
        mutableStateOf(
            lapsed?.let {
                org.astermail.android.billing.PaymentFailedNotifier.is_lapse_dismissed(
                    context,
                    org.astermail.android.billing.lapse_dismissal_key(it),
                )
            } ?: false,
        )
    }
    val payment_failed_due = sub?.let {
        org.astermail.android.billing.payment_failed_due_date(
            status = it.status,
            payment_failed_at = it.payment_failed_at,
            grace_period_end = it.grace_period_end,
            current_period_end = it.current_period_end,
            cancel_at_period_end = it.cancel_at_period_end,
        )
    }

    detail_scaffold(title = stringResource(R.string.plan_billing), on_back = on_back, scroll_state = scroll_state) {
        if (sub == null && state.subscription_load_failed) {
            load_failed_card(state.error) { vm.load_subscription() }
            return@detail_scaffold
        }
        if (payment_failed_due != null) {
            org.astermail.android.ui.common.payment_failed_banner(
                plan_name = sub?.effective_plan_name ?: plan_free_label,
                due_date = payment_failed_due,
                is_loading = billing_state.is_acting && billing_state.acting_action == "portal",
                on_update_card = {
                    if (is_crypto_sub) {
                        pending_plan_code = current_code
                        pending_addon_id = null
                        show_crypto_terms = true
                    } else if (!billing_state.is_acting) {
                        billing_vm.open_portal()
                    }
                },
                days_left = org.astermail.android.billing.payment_failed_days_left(
                    payment_failed_due,
                    java.time.LocalDate.now().toString(),
                ),
            )
            v_gap(AsterSpacing.md)
        }
        if (billing_state.subscription_error != null) {
            notice_row(
                text = billing_state.subscription_error ?: "",
                accent = colors.text_tertiary,
                action_label = stringResource(R.string.retry),
                on_action = { billing_vm.load_subscription(); vm.load_subscription() },
                on_dismiss = { billing_vm.clear_subscription_error() },
            )
            v_gap(AsterSpacing.md)
        }
        if (billing_state.checking_payment) {
            notice_row(
                text = stringResource(R.string.confirming_payment),
                accent = colors.accent_blue,
                loading = true,
            )
            v_gap(AsterSpacing.md)
        }
        val abandoned_plan = billing_state.checkout_abandoned_plan
        if (abandoned_plan != null && !billing_state.checking_payment) {
            val abandoned_name = org.astermail.android.billing.plan_display_name(billing_state.available_plans, abandoned_plan)
                ?: plan_tiers.firstOrNull { it.code == abandoned_plan }?.let { stringResource(it.name_res) }
                ?: abandoned_plan
            AsterCard(modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(AsterSpacing.lg)) {
                    Text(
                        text = stringResource(R.string.finish_plan_setup_title, abandoned_name),
                        color = colors.text_primary,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.SemiBold,
                    )
                    Spacer(Modifier.height(AsterSpacing.xs))
                    Text(
                        text = stringResource(R.string.finish_plan_setup_message),
                        color = colors.text_tertiary,
                        fontSize = 13.sp,
                    )
                    Spacer(Modifier.height(AsterSpacing.md))
                    Row(horizontalArrangement = Arrangement.spacedBy(AsterSpacing.sm)) {
                        Box(modifier = Modifier.weight(1f)) {
                            AsterButton(
                                label = stringResource(R.string.finish_plan_setup_action),
                                onClick = {
                                    billing_interval = billing_state.checkout_abandoned_interval ?: billing_interval
                                    billing_vm.clear_checkout_abandoned()
                                    pending_plan_code = abandoned_plan
                                    pending_addon_id = null
                                    show_payment_picker = true
                                },
                                enabled = !billing_state.is_acting,
                            )
                        }
                        Box(modifier = Modifier.weight(1f)) {
                            AsterSecondaryButton(
                                label = stringResource(R.string.upgrade_not_now),
                                onClick = { billing_vm.clear_checkout_abandoned() },
                            )
                        }
                    }
                }
            }
            v_gap(AsterSpacing.md)
        }
        if (crypto_renewal_days != null) {
            val renewal_end = (billing_sub?.paid_until ?: sub?.current_period_end).orEmpty().take(10)
            AsterCard(modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(AsterSpacing.lg)) {
                    Text(
                        text = stringResource(R.string.crypto_renewal_title),
                        color = colors.text_primary,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.SemiBold,
                    )
                    Spacer(Modifier.height(AsterSpacing.xs))
                    Text(
                        text = if (crypto_renewal_days == 0) {
                            stringResource(R.string.crypto_renewal_today, renewal_end)
                        } else {
                            androidx.compose.ui.res.pluralStringResource(
                                R.plurals.crypto_renewal_days,
                                crypto_renewal_days,
                                crypto_renewal_days,
                                renewal_end,
                            )
                        },
                        color = colors.text_tertiary,
                        fontSize = 13.sp,
                    )
                    Spacer(Modifier.height(AsterSpacing.md))
                    AsterButton(
                        label = stringResource(R.string.crypto_renew_now),
                        onClick = {
                            if (!billing_state.is_acting) {
                                pending_plan_code = current_code
                                pending_addon_id = null
                                show_crypto_terms = true
                            }
                        },
                        enabled = !billing_state.is_acting,
                    )
                }
            }
            v_gap(AsterSpacing.md)
        }
        if (sub == null && (state.is_loading || !plan_load_settled)) {
            skeleton_hero_card(lines = 2)
            v_gap(AsterSpacing.lg)
            skeleton_section_label()
            skeleton_card_list(rows = 3, trailing_width = 64.dp)
            v_gap(AsterSpacing.lg)
            skeleton_section_label()
            skeleton_card_list(rows = 2, trailing_width = 64.dp)
        } else {
            AsterCard(modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(AsterSpacing.lg)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Column(modifier = Modifier.weight(1f)) {
                            aster_plan_badge(
                                text = sub?.effective_plan_name
                                    ?: if (state.error != null) stringResource(R.string.failed_to_load) else plan_free_label,
                                accent = if (current_code == "free") colors.text_muted else colors.accent_blue,
                                font_size = 13.sp,
                                horizontal_padding = 10.dp,
                                vertical_padding = 5.dp,
                            )
                            Spacer(Modifier.height(AsterSpacing.sm))
                            Text(
                                text = stringResource(R.string.current_plan),
                                color = colors.text_tertiary,
                                fontSize = 13.sp,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                        }
                        if (sub != null && sub.effective_price_cents > 0) {
                            Spacer(Modifier.width(AsterSpacing.sm))
                            Text(
                                text = stringResource(
                                    R.string.settings_price_per_interval,
                                    format_price(sub.effective_price_cents, detected_currency),
                                    org.astermail.android.billing.billing_interval_label(context, sub.effective_interval),
                                ),
                                color = colors.text_primary,
                                fontSize = 17.sp,
                                fontWeight = FontWeight.SemiBold,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                        }
                    }
                    val period_end = sub?.current_period_end
                    val ends_at_period_end = sub?.cancel_at_period_end == true
                    if (period_end != null) {
                        Spacer(Modifier.size(AsterSpacing.sm))
                        Text(
                            text = if (ends_at_period_end) {
                                stringResource(R.string.ends_date, period_end.take(10))
                            } else {
                                stringResource(R.string.renews_format, period_end.take(10))
                            },
                            color = if (ends_at_period_end) colors.danger else colors.text_tertiary,
                            fontSize = 13.sp,
                        )
                    }
                    if (ends_at_period_end && current_code != "free") {
                        Spacer(Modifier.size(AsterSpacing.lg))
                        AsterButton(
                            label = if (billing_state.acting_action == "reactivate") stringResource(R.string.loading) else stringResource(R.string.resume_plan),
                            onClick = { if (!billing_state.is_acting) billing_vm.reactivate_subscription() },
                            enabled = !billing_state.is_acting,
                        )
                    }
                    if (offer_yearly_switch) {
                        Spacer(Modifier.size(AsterSpacing.md))
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .heightIn(min = 48.dp)
                                .clip(SquircleShape(10.dp))
                                .background(colors.success.copy(alpha = 0.12f))
                                .clickable(
                                    enabled = !billing_state.is_acting,
                                    role = Role.Button,
                                ) { show_switch_yearly = true }
                                .padding(horizontal = AsterSpacing.md, vertical = 10.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Text(
                                text = stringResource(R.string.switch_yearly_save, yearly_savings ?: 0),
                                color = colors.success,
                                fontSize = 13.sp,
                                fontWeight = FontWeight.SemiBold,
                                modifier = Modifier.weight(1f),
                            )
                            Icon(
                                imageVector = TablerIcons.ChevronRight,
                                contentDescription = null,
                                tint = colors.success,
                                modifier = Modifier.size(16.dp),
                            )
                        }
                    }
                    if (current_code in FAMILY_PLAN_CODES) {
                        Spacer(Modifier.size(AsterSpacing.md))
                        Text(
                            text = stringResource(R.string.family_manage_web),
                            color = colors.accent_blue,
                            fontSize = 13.sp,
                            fontWeight = FontWeight.SemiBold,
                            modifier = Modifier
                                .heightIn(min = 48.dp)
                                .clip(SquircleShape(10.dp))
                                .clickable(role = Role.Button) {
                                    org.astermail.android.billing.open_billing_tab(context, org.astermail.android.billing.FAMILY_MANAGE_URL)
                                }
                                .padding(vertical = 14.dp),
                        )
                    }
                    if (current_code != "free" && !is_crypto_sub) {
                        Spacer(Modifier.size(AsterSpacing.lg))
                        AsterSecondaryButton(
                            label = if (billing_state.is_acting && billing_state.acting_action == "portal") stringResource(R.string.loading) else stringResource(R.string.manage_subscription),
                            onClick = { if (!billing_state.is_acting) billing_vm.open_portal() },
                            enabled = !billing_state.is_acting,
                        )
                        if (
                            sub != null &&
                                !sub.cancel_at_period_end &&
                                sub.status in setOf("active", "trialing", "past_due") &&
                                !org.astermail.android.billing.is_crypto_provider(sub.payment_provider) &&
                                sub.has_stripe_subscription != false
                        ) {
                            Spacer(Modifier.size(AsterSpacing.sm))
                            Text(
                                text = stringResource(R.string.cancel_subscription),
                                color = colors.danger,
                                fontSize = 14.sp,
                                fontWeight = FontWeight.Medium,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .heightIn(min = 48.dp)
                                    .clip(SquircleShape(10.dp))
                                    .clickable(
                                        enabled = !billing_state.is_acting,
                                        role = Role.Button,
                                    ) { show_cancel_flow = true }
                                    .padding(vertical = 14.dp),
                                textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                            )
                        }
                    }
                }
            }
        }
        if (lapsed != null && !lapsed_dismissed) {
            v_gap(AsterSpacing.lg)
            AsterCard(modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(AsterSpacing.lg)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            text = stringResource(R.string.plan_ended_on, lapsed.plan_name, lapsed.ended_on),
                            color = colors.text_primary,
                            fontSize = 14.sp,
                            fontWeight = FontWeight.SemiBold,
                            modifier = Modifier.weight(1f),
                        )
                        Box(
                            modifier = Modifier
                                .size(48.dp)
                                .clip(CircleShape)
                                .clickable(role = Role.Button) {
                                    org.astermail.android.billing.PaymentFailedNotifier.dismiss_lapse(
                                        context,
                                        org.astermail.android.billing.lapse_dismissal_key(lapsed),
                                    )
                                    lapsed_dismissed = true
                                },
                            contentAlignment = Alignment.Center,
                        ) {
                            Icon(
                                imageVector = TablerIcons.X,
                                contentDescription = stringResource(R.string.dismiss),
                                tint = colors.text_muted,
                                modifier = Modifier.size(18.dp),
                            )
                        }
                    }
                    Spacer(Modifier.height(AsterSpacing.xs))
                    Text(
                        text = stringResource(R.string.plan_ended_resubscribe_note),
                        color = colors.text_tertiary,
                        fontSize = 13.sp,
                    )
                    Spacer(Modifier.height(AsterSpacing.md))
                    AsterButton(
                        label = stringResource(R.string.resubscribe),
                        onClick = {
                            if (!billing_state.is_acting) {
                                pending_plan_code = plan_code_of(lapsed.plan_name)
                                pending_addon_id = null
                                show_payment_picker = true
                            }
                        },
                        enabled = !billing_state.is_acting,
                    )
                }
            }
        }
        val pending_invoice = billing_state.pending_crypto_invoices.firstOrNull {
            is_resumable_crypto_invoice(it, System.currentTimeMillis())
        }
        if (pending_invoice != null) {
            v_gap(AsterSpacing.lg)
            crypto_resume_card(
                invoice = pending_invoice,
                on_resume = { on_open_crypto_invoice(pending_invoice.id) },
            )
        }

        v_gap(AsterSpacing.lg)
        section_label(stringResource(R.string.plan_includes))
        AsterCard(modifier = Modifier.fillMaxWidth()) {
            Column(modifier = Modifier.padding(AsterSpacing.lg)) {
                current_features.forEach { feature_res ->
                    feature_row(feature_res)
                }
            }
        }

        v_gap(AsterSpacing.lg)

        section_label(stringResource(R.string.upgrade))
        billing_interval_toggle(
            selected = billing_interval,
            on_select = { billing_interval = it },
        )
        v_gap(AsterSpacing.md)
        plan_recommendation_banner(
            recommendation = recommendation,
            current_plan_name = current_plan_name,
            recommended_tier_name = recommended_tier_name,
        )
        val current_rank = tier_rank(current_code)
        val paid_stripe_current = current_rank >= 0 && (sub?.effective_price_cents ?: 0) > 0
        plan_tiers.forEach { tier ->
            val is_downgrade = paid_stripe_current && tier_rank(tier.code) < current_rank
            plan_tier_card(
                tier = tier,
                billing_interval = billing_interval,
                is_current = tier.code == current_code,
                is_downgrade = is_downgrade,
                is_recommended = recommendation.recommended_plan_code == tier.code,
                monthly_cents = org.astermail.android.billing.api_plan_price_cents(billing_state.available_plans, tier.code, "month"),
                yearly_cents = org.astermail.android.billing.api_plan_price_cents(billing_state.available_plans, tier.code, "year"),
                currency = detected_currency,
                plans_failed = billing_state.plans_failed,
                on_see_pricing = { org.astermail.android.billing.open_billing_tab(context, org.astermail.android.billing.PRICING_URL) },
                on_choose = {
                    if (is_downgrade) {
                        pending_downgrade_code = tier.code
                    } else {
                        pending_plan_code = tier.code
                        pending_addon_id = null
                        show_payment_picker = true
                    }
                },
            )
            v_gap(AsterSpacing.md)
        }

        val addons = billing_state.storage_addons
        if (!addons?.available_addons.isNullOrEmpty()) {
            v_gap(AsterSpacing.lg)
            Box(modifier = Modifier.onGloballyPositioned { coords ->
                addons_section_offset = coords.positionInParent().y
            }) { section_label(stringResource(R.string.storage_addons_title)) }
            AsterCard(modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(AsterSpacing.lg)) {
                    Text(
                        text = stringResource(R.string.storage_addons_description),
                        color = colors.text_secondary,
                        fontSize = 13.sp,
                    )
                    Spacer(Modifier.height(AsterSpacing.xs))
                    Text(
                        text = stringResource(R.string.settings_storage_addons_monthly_note),
                        color = colors.text_tertiary,
                        fontSize = 12.sp,
                    )
                    Spacer(Modifier.height(AsterSpacing.md))
                    addons!!.available_addons.forEachIndexed { idx, addon ->
                        if (idx > 0) { Spacer(Modifier.height(AsterSpacing.md)); AsterDivider(); Spacer(Modifier.height(AsterSpacing.md)) }
                        val acting = billing_state.acting_action == "addon_${addon.id}"
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Text(
                                text = addon.name,
                                color = colors.text_primary,
                                fontSize = 15.sp,
                                fontWeight = FontWeight.SemiBold,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                modifier = Modifier.weight(1f),
                            )
                            Spacer(Modifier.width(AsterSpacing.sm))
                            Text(
                                text = stringResource(
                                    R.string.settings_price_per_interval,
                                    format_price(addon.price_cents, detected_currency),
                                    org.astermail.android.billing.billing_interval_label(context, addon.billing_period),
                                ),
                                color = colors.text_tertiary,
                                fontSize = 13.sp,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                        }
                        Spacer(Modifier.height(AsterSpacing.sm))
                        AsterSecondaryButton(
                            label = if (acting) stringResource(R.string.loading) else stringResource(R.string.storage_addon_add, addon.name),
                            onClick = {
                                if (!billing_state.is_acting) {
                                    pending_addon_id = addon.id
                                    pending_plan_code = null
                                    show_payment_picker = true
                                }
                            },
                            enabled = !billing_state.is_acting,
                        )
                    }
                }
            }
            if (!addons.active_addons.isNullOrEmpty()) {
                v_gap(AsterSpacing.sm)
                Text(
                    text = pluralStringResource(R.plurals.storage_addons_active_count, addons.active_addons.size, addons.active_addons.size),
                    color = colors.text_tertiary,
                    fontSize = 12.sp,
                )
            }
        }

        v_gap(AsterSpacing.xs)
        Text(
            text = if (detected_currency.equals("usd", ignoreCase = true)) {
                stringResource(R.string.settings_prices_usd_note)
            } else {
                stringResource(R.string.settings_prices_currency_note, detected_currency.uppercase())
            },
            color = colors.text_tertiary,
            fontSize = 12.sp,
            modifier = Modifier.fillMaxWidth(),
        )
        v_gap(AsterSpacing.md)
        AsterSecondaryButton(
            label = stringResource(R.string.view_all_features),
            onClick = {
                org.astermail.android.billing.open_billing_tab(context, org.astermail.android.billing.PRICING_URL)
            },
        )

        v_gap(AsterSpacing.lg)
        section_label(stringResource(R.string.credits_title))
        AsterCard(modifier = Modifier.fillMaxWidth()) {
            Column(modifier = Modifier.padding(AsterSpacing.lg)) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { org.astermail.android.billing.open_billing_tab(context, org.astermail.android.billing.CREDITS_URL) },
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = stringResource(R.string.credits_balance_label),
                        color = colors.text_primary,
                        fontSize = 15.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f),
                    )
                    Spacer(Modifier.width(AsterSpacing.sm))
                    Text(
                        text = billing_state.credits?.let { format_price(it.balance_cents.toInt(), detected_currency) }
                            ?: stringResource(R.string.credits_unavailable),
                        color = colors.text_tertiary,
                        fontSize = 13.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                Spacer(Modifier.height(AsterSpacing.md))
                AsterDivider()
                Spacer(Modifier.height(AsterSpacing.md))
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { org.astermail.android.billing.open_billing_tab(context, org.astermail.android.billing.ACADEMIC_URL) },
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = stringResource(R.string.academic_discount_label),
                        color = colors.text_primary,
                        fontSize = 15.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f),
                    )
                    Spacer(Modifier.width(AsterSpacing.sm))
                    Text(
                        text = when (billing_state.academic?.status) {
                            "verified" -> stringResource(R.string.academic_status_verified)
                            "pending" -> stringResource(R.string.academic_status_pending)
                            null -> stringResource(R.string.credits_unavailable)
                            else -> stringResource(R.string.academic_status_none)
                        },
                        color = colors.text_tertiary,
                        fontSize = 13.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                Spacer(Modifier.height(AsterSpacing.sm))
                Text(
                    text = stringResource(R.string.credits_manage_web),
                    color = colors.text_muted,
                    fontSize = 12.sp,
                )
            }
        }

        if (current_code != "free" && !is_crypto_sub) {
            v_gap(AsterSpacing.md)
            AsterSecondaryButton(
                label = if (billing_state.is_acting && billing_state.acting_action == "portal") stringResource(R.string.loading) else stringResource(R.string.manage_billing_browser),
                onClick = { if (!billing_state.is_acting) billing_vm.open_portal() },
                enabled = !billing_state.is_acting,
            )
        }
        v_gap(AsterSpacing.xxl)
    }

    if (show_payment_picker) {
        val picker_tier = pending_plan_code?.let { code -> plan_tiers.firstOrNull { it.code == code } }
        val picker_addon = pending_addon_id?.let { id ->
            billing_state.storage_addons?.available_addons?.firstOrNull { it.id == id }
        }
        val picker_monthly = pending_plan_code?.let {
            org.astermail.android.billing.api_plan_price_cents(billing_state.available_plans, it, "month")
        }
        val picker_yearly = pending_plan_code?.let {
            org.astermail.android.billing.api_plan_price_cents(billing_state.available_plans, it, "year")
        }
        val picker_yearly_selected = billing_interval == "year"
        val picker_amount_cents = picker_addon?.price_cents
            ?: if (picker_yearly_selected) picker_yearly else picker_monthly
        val picker_save_cents = if (
            picker_addon == null && picker_yearly_selected && picker_monthly != null && picker_yearly != null
        ) {
            (picker_monthly * 12 - picker_yearly).coerceAtLeast(0).takeIf { it > 0 }
        } else {
            null
        }
        payment_review_dialog(
            title = stringResource(R.string.checkout_review_title),
            plan_name = picker_tier?.let { stringResource(it.name_res) }
                ?: picker_addon?.name
                ?: stringResource(R.string.storage_addons_title),
            interval_label = picker_addon?.let {
                org.astermail.android.billing.billing_interval_per_label(context, it.billing_period)
            } ?: picker_tier?.let {
                org.astermail.android.billing.billing_interval_per_label(context, billing_interval)
            },
            amount_text = picker_amount_cents?.let { format_price(it, detected_currency) }
                ?: stringResource(R.string.see_pricing),
            subtotal_text = picker_save_cents?.let { format_price((picker_monthly ?: 0) * 12, detected_currency) },
            save_text = picker_save_cents?.let { format_price(it, detected_currency) },
            is_best_value = picker_tier != null && recommendation.recommended_plan_code == picker_tier.code,
            features = picker_tier?.features.orEmpty(),
            is_busy = billing_state.is_acting,
            initial_method = picker_method,
            on_dismiss = {
                show_payment_picker = false
                picker_method = payment_method_card
            },
            on_confirm = { chosen ->
                picker_method = chosen
                show_payment_picker = false
                if (chosen == payment_method_crypto) {
                    show_crypto_terms = true
                } else {
                    pending_plan_code?.let { billing_vm.start_checkout(it, billing_interval, detected_currency) }
                        ?: pending_addon_id?.let { billing_vm.purchase_storage_addon(it) }
                }
            },
        )
    }

    val downgrade_tier = pending_downgrade_code?.let { code -> plan_tiers.firstOrNull { it.code == code } }
    if (downgrade_tier != null) {
        val downgrade_name = stringResource(downgrade_tier.name_res)
        var downgrade_interval by remember(downgrade_tier.code) { mutableStateOf(current_interval) }
        val downgrade_cents = org.astermail.android.billing.api_plan_price_cents(billing_state.available_plans, downgrade_tier.code, downgrade_interval)
        val interval_label = org.astermail.android.billing.billing_interval_per_label(context, downgrade_interval)
        val downgrade_price_text = downgrade_cents?.let { format_price(it, detected_currency) + " " + interval_label }
            ?: stringResource(R.string.see_pricing)
        LaunchedEffect(downgrade_tier.code, downgrade_interval) {
            billing_vm.load_plan_change_preview(downgrade_tier.code, downgrade_interval)
        }
        DisposableEffect(Unit) { onDispose { billing_vm.clear_plan_change_preview() } }
        org.astermail.android.design.components.AsterDialog(
            on_dismiss = {
                pending_downgrade_code = null
                if (resume_cancel_flow) {
                    resume_cancel_flow = false
                    show_cancel_flow = true
                }
            },
            title = stringResource(R.string.downgrade_to, downgrade_name),
            message = stringResource(
                R.string.downgrade_confirm_message,
                downgrade_name,
                downgrade_price_text,
            ),
            body = {
                Column(verticalArrangement = Arrangement.spacedBy(AsterSpacing.sm)) {
                    billing_interval_toggle(
                        selected = downgrade_interval,
                        on_select = { downgrade_interval = it },
                    )
                    plan_change_preview_text(billing_state)
                }
            },
            footer = {
                org.astermail.android.design.components.AsterDialogOutlineButton(
                    label = stringResource(R.string.cancel),
                    onClick = {
                        pending_downgrade_code = null
                        if (resume_cancel_flow) {
                            resume_cancel_flow = false
                            show_cancel_flow = true
                        }
                    },
                    modifier = Modifier.weight(1f),
                )
                org.astermail.android.design.components.AsterDialogPrimaryButton(
                    label = stringResource(R.string.downgrade),
                    onClick = {
                        pending_downgrade_code = null
                        resume_cancel_flow = false
                        billing_vm.change_plan(downgrade_tier.code, downgrade_interval)
                    },
                    enabled = !billing_state.plan_change_preview_loading,
                    modifier = Modifier.weight(1f),
                )
            },
        )
    }

    if (show_cancel_flow) {
        LaunchedEffect(Unit) { billing_vm.load_cancel_impact() }
        cancel_subscription_flow(
            billing_state = billing_state,
            yearly_savings = if (offer_yearly_switch) yearly_savings else null,
            downgrade_offer_label = cancel_offer_label,
            on_switch_plan = {
                show_cancel_flow = false
                resume_cancel_flow = true
                pending_downgrade_code = cancel_offer_code
            },
            on_switch_yearly = {
                show_cancel_flow = false
                resume_cancel_flow = true
                show_switch_yearly = true
            },
            on_dismiss = {
                show_cancel_flow = false
                resume_cancel_flow = false
                billing_vm.clear_messages()
            },
            on_confirm = { reason, reason_text -> billing_vm.cancel_subscription(reason, reason_text) },
        )
    }

    if (show_switch_yearly) {
        val yearly_price = format_price(api_yearly_cents ?: 0, detected_currency)
        LaunchedEffect(current_code) { billing_vm.load_plan_change_preview(current_code, "year") }
        DisposableEffect(Unit) { onDispose { billing_vm.clear_plan_change_preview() } }
        val preview = billing_state.plan_change_preview
        val close_switch_yearly = {
            show_switch_yearly = false
            if (resume_cancel_flow) {
                resume_cancel_flow = false
                show_cancel_flow = true
            }
        }
        org.astermail.android.design.components.AsterDialog(
            on_dismiss = close_switch_yearly,
            title = stringResource(R.string.switch_yearly_title),
            message = if (preview != null) {
                stringResource(
                    R.string.switch_yearly_due_today,
                    format_price(preview.amount_due_cents.toInt(), preview.currency),
                    yearly_price,
                    yearly_savings ?: 0,
                )
            } else {
                stringResource(R.string.switch_yearly_message, yearly_price, yearly_savings ?: 0)
            },
            body = if (preview == null) {
                { plan_change_preview_text(billing_state) }
            } else {
                null
            },
            footer = {
                org.astermail.android.design.components.AsterDialogOutlineButton(
                    label = stringResource(R.string.cancel),
                    onClick = close_switch_yearly,
                    modifier = Modifier.weight(1f),
                )
                org.astermail.android.design.components.AsterDialogPrimaryButton(
                    label = stringResource(R.string.switch_yearly_confirm),
                    onClick = {
                        show_switch_yearly = false
                        resume_cancel_flow = false
                        billing_vm.switch_billing("year")
                    },
                    enabled = !billing_state.plan_change_preview_loading,
                    modifier = Modifier.weight(1f),
                )
            },
        )
    }

    if (show_crypto_terms) {
        crypto_term_dialog(
            on_dismiss = {
                show_crypto_terms = false
                show_payment_picker = true
            },
            on_confirm = { term ->
                show_crypto_terms = false
                pending_term_months = term
                val plan_code = pending_plan_code
                val use_native = plan_code != null &&
                    billing_state.crypto_native_enabled &&
                    billing_state.crypto_native_coins.isNotEmpty()
                when {
                    use_native -> show_crypto_coins = true
                    plan_code != null -> billing_vm.start_crypto_checkout(plan_code, term)
                    else -> pending_addon_id?.let { billing_vm.purchase_addon_crypto(it, term) }
                }
            },
        )
    }

    if (show_crypto_coins) {
        crypto_coin_dialog(
            coins = billing_state.crypto_native_coins,
            on_dismiss = {
                show_crypto_coins = false
                show_crypto_terms = true
            },
            on_select = { coin ->
                show_crypto_coins = false
                pending_plan_code?.let {
                    billing_vm.create_crypto_native_invoice(it, pending_term_months, coin.currency, coin.chain)
                }
            },
        )
    }
}

@StringRes
private fun cancel_reason_label(reason: String): Int = when (reason) {
    "too_expensive" -> R.string.cancel_reason_too_expensive
    "not_using" -> R.string.cancel_reason_not_using
    "missing_feature" -> R.string.cancel_reason_missing_feature
    "switched_provider" -> R.string.cancel_reason_switched_provider
    "bugs" -> R.string.cancel_reason_bugs
    "privacy_trust" -> R.string.cancel_reason_privacy_trust
    "just_testing" -> R.string.cancel_reason_just_testing
    else -> R.string.cancel_reason_other
}

private enum class CancelStep { reason, impact, confirm }

@Composable
private fun cancel_subscription_flow(
    billing_state: org.astermail.android.billing.BillingUiState,
    yearly_savings: Int?,
    downgrade_offer_label: String?,
    on_switch_plan: () -> Unit,
    on_switch_yearly: () -> Unit,
    on_dismiss: () -> Unit,
    on_confirm: (reason: String?, reason_text: String?) -> Boolean,
) {
    val colors = AsterMaterial.colors
    var reason by remember { mutableStateOf<String?>(null) }
    var reason_text by remember { mutableStateOf("") }
    var step by remember { mutableStateOf(CancelStep.reason) }
    var submitted by remember { mutableStateOf(false) }
    val cancelling = billing_state.is_acting && billing_state.acting_action == "cancel"
    LaunchedEffect(cancelling, billing_state.error) {
        if (submitted && !cancelling) {
            if (billing_state.error == null) on_dismiss() else submitted = false
        }
    }
    if (step == CancelStep.impact) {
        val impact = billing_state.cancel_impact
        val lines = buildList {
            if (impact != null) {
                if (impact.aliases_to_disable > 0) add(androidx.compose.ui.res.pluralStringResource(R.plurals.cancel_impact_aliases, impact.aliases_to_disable, impact.aliases_to_disable))
                if (impact.domains_to_suspend > 0) add(androidx.compose.ui.res.pluralStringResource(R.plurals.cancel_impact_domains, impact.domains_to_suspend, impact.domains_to_suspend))
                if (impact.storage_over_limit) {
                    add(
                        stringResource(
                            R.string.cancel_impact_storage,
                            android.text.format.Formatter.formatShortFileSize(LocalContext.current, impact.storage_used_bytes),
                            android.text.format.Formatter.formatShortFileSize(LocalContext.current, impact.storage_limit_after_bytes),
                        ),
                    )
                }
                if (impact.templates_to_disable > 0) add(androidx.compose.ui.res.pluralStringResource(R.plurals.cancel_impact_templates, impact.templates_to_disable, impact.templates_to_disable))
                if (impact.signatures_to_disable > 0) add(androidx.compose.ui.res.pluralStringResource(R.plurals.cancel_impact_signatures, impact.signatures_to_disable, impact.signatures_to_disable))
                if (impact.catch_all_to_revoke > 0) add(stringResource(R.string.cancel_impact_catch_all))
                if (impact.family_members_affected > 0) add(androidx.compose.ui.res.pluralStringResource(R.plurals.cancel_impact_family, impact.family_members_affected, impact.family_members_affected))
            }
        }
        org.astermail.android.design.components.AsterDialog(
            on_dismiss = on_dismiss,
            title = stringResource(R.string.cancel_impact_title),
            message = when {
                billing_state.cancel_impact_loading -> stringResource(R.string.loading)
                impact == null -> stringResource(R.string.cancel_impact_unavailable)
                lines.isEmpty() -> stringResource(R.string.cancel_impact_none)
                else -> stringResource(R.string.cancel_impact_intro)
            },
            body = {
                Column(verticalArrangement = Arrangement.spacedBy(AsterSpacing.xs)) {
                    lines.forEach { line ->
                        Row(verticalAlignment = Alignment.Top, horizontalArrangement = Arrangement.spacedBy(AsterSpacing.sm)) {
                            Icon(
                                imageVector = TablerIcons.AlertTriangle,
                                contentDescription = null,
                                tint = colors.danger,
                                modifier = Modifier.size(16.dp),
                            )
                            Text(text = line, color = colors.text_primary, fontSize = 13.sp)
                        }
                    }
                    if (downgrade_offer_label != null) {
                        Spacer(Modifier.height(AsterSpacing.xs))
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .heightIn(min = 48.dp)
                                .clip(SquircleShape(10.dp))
                                .background(colors.accent_blue.copy(alpha = 0.12f))
                                .clickable(role = Role.Button, onClick = on_switch_plan)
                                .padding(horizontal = AsterSpacing.md, vertical = 10.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Text(
                                text = downgrade_offer_label,
                                color = colors.accent_blue,
                                fontSize = 13.sp,
                                fontWeight = FontWeight.SemiBold,
                                modifier = Modifier.weight(1f),
                            )
                            Icon(
                                imageVector = TablerIcons.ChevronRight,
                                contentDescription = null,
                                tint = colors.accent_blue,
                                modifier = Modifier.size(16.dp),
                            )
                        }
                    }
                    if (yearly_savings != null) {
                        Spacer(Modifier.height(AsterSpacing.xs))
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .heightIn(min = 48.dp)
                                .clip(SquircleShape(10.dp))
                                .background(colors.success.copy(alpha = 0.12f))
                                .clickable(role = Role.Button, onClick = on_switch_yearly)
                                .padding(horizontal = AsterSpacing.md, vertical = 10.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Text(
                                text = stringResource(R.string.switch_yearly_save, yearly_savings),
                                color = colors.success,
                                fontSize = 13.sp,
                                fontWeight = FontWeight.SemiBold,
                                modifier = Modifier.weight(1f),
                            )
                            Icon(
                                imageVector = TablerIcons.ChevronRight,
                                contentDescription = null,
                                tint = colors.success,
                                modifier = Modifier.size(16.dp),
                            )
                        }
                    }
                }
            },
            footer = {
                org.astermail.android.design.components.AsterDialogOutlineButton(
                    label = stringResource(R.string.back),
                    onClick = { step = CancelStep.reason },
                    modifier = Modifier.weight(1f),
                )
                org.astermail.android.design.components.AsterDialogPrimaryButton(
                    label = stringResource(R.string.next),
                    onClick = { step = CancelStep.confirm },
                    enabled = !billing_state.cancel_impact_loading,
                    modifier = Modifier.weight(1f),
                )
            },
        )
    } else if (step == CancelStep.reason) {
        org.astermail.android.design.components.AsterDialog(
            on_dismiss = on_dismiss,
            title = stringResource(R.string.cancel_reason_title),
            message = stringResource(R.string.cancel_reason_description),
            body = {
                Column(
                    verticalArrangement = Arrangement.spacedBy(2.dp),
                    modifier = Modifier.selectableGroup(),
                ) {
                    org.astermail.android.billing.CANCEL_REASONS.forEach { option ->
                        val active = reason == option
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .heightIn(min = 48.dp)
                                .clip(SquircleShape(10.dp))
                                .background(if (active) colors.accent_blue.copy(alpha = 0.10f) else Color.Transparent)
                                .selectable(
                                    selected = active,
                                    role = Role.RadioButton,
                                    onClick = { reason = if (active) null else option },
                                )
                                .padding(horizontal = AsterSpacing.sm, vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(AsterSpacing.sm),
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(18.dp)
                                    .clip(CircleShape)
                                    .border(if (active) 6.dp else 1.5.dp, if (active) colors.accent_blue else colors.input_border, CircleShape),
                            )
                            Text(
                                text = stringResource(cancel_reason_label(option)),
                                color = colors.text_primary,
                                fontSize = 14.sp,
                                fontWeight = if (active) FontWeight.SemiBold else FontWeight.Normal,
                            )
                        }
                    }
                    Spacer(Modifier.height(AsterSpacing.xs))
                    AsterTextField(
                        value = reason_text,
                        onValueChange = { reason_text = it.take(org.astermail.android.billing.MAX_CANCEL_REASON_TEXT) },
                        placeholder = stringResource(R.string.cancel_reason_text_placeholder),
                        singleLine = false,
                        min_lines = 2,
                        max_lines = 4,
                    )
                }
            },
            footer = {
                org.astermail.android.design.components.AsterDialogOutlineButton(
                    label = stringResource(R.string.keep_plan),
                    onClick = on_dismiss,
                    modifier = Modifier.weight(1f),
                )
                org.astermail.android.design.components.AsterDialogPrimaryButton(
                    label = stringResource(R.string.next),
                    onClick = { step = CancelStep.impact },
                    modifier = Modifier.weight(1f),
                )
            },
        )
    } else {
        val failure = if (submitted) null else billing_state.error
        org.astermail.android.design.components.AsterDialog(
            on_dismiss = { if (!cancelling) on_dismiss() },
            title = stringResource(R.string.cancel_subscription_title),
            message = stringResource(R.string.cancel_subscription_description),
            body = if (failure == null) {
                null
            } else {
                { Text(text = failure, color = colors.danger, fontSize = 13.sp) }
            },
            footer = {
                org.astermail.android.design.components.AsterDialogOutlineButton(
                    label = stringResource(R.string.back),
                    onClick = { step = CancelStep.impact },
                    enabled = !cancelling,
                    modifier = Modifier.weight(1f),
                )
                org.astermail.android.design.components.AsterDialogDestructiveButton(
                    label = stringResource(R.string.cancel_subscription),
                    onClick = { submitted = on_confirm(reason, reason_text) },
                    enabled = !cancelling,
                    is_loading = cancelling,
                    modifier = Modifier.weight(1f),
                )
            },
        )
    }
}

@Composable
private fun plan_change_preview_text(billing_state: org.astermail.android.billing.BillingUiState) {
    val colors = AsterMaterial.colors
    val preview = billing_state.plan_change_preview
    val text = when {
        billing_state.plan_change_preview_loading -> stringResource(R.string.plan_change_preview_loading)
        preview != null -> stringResource(
            R.string.plan_change_due_today,
            format_price(preview.amount_due_cents.toInt(), preview.currency),
        )
        billing_state.plan_change_preview_failed -> stringResource(R.string.plan_change_preview_failed)
        else -> return
    }
    Text(
        text = text,
        color = if (preview != null) colors.text_primary else colors.text_tertiary,
        fontSize = 13.sp,
        fontWeight = if (preview != null) FontWeight.SemiBold else FontWeight.Normal,
    )
}

@Composable
private fun crypto_resume_card(
    invoice: org.astermail.android.api.billing.CryptoNativePendingInvoice,
    on_resume: () -> Unit,
) {
    val colors = AsterMaterial.colors
    val coin_label = invoice.display_name.ifBlank { invoice.currency.uppercase() }
    AsterCard(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(AsterSpacing.lg)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = TablerIcons.Clock,
                    contentDescription = null,
                    tint = colors.accent_blue,
                    modifier = Modifier.size(20.dp),
                )
                Spacer(Modifier.width(AsterSpacing.md))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = stringResource(R.string.crypto_native_pending_banner),
                        color = colors.text_primary,
                        fontSize = 15.sp,
                        fontWeight = FontWeight.SemiBold,
                    )
                    Text(
                        text = stringResource(R.string.crypto_native_invoice_title, coin_label),
                        color = colors.text_tertiary,
                        fontSize = 12.sp,
                    )
                }
            }
            Spacer(Modifier.height(AsterSpacing.md))
            AsterButton(
                label = stringResource(R.string.crypto_native_pending_banner_action),
                onClick = on_resume,
            )
        }
    }
}

@Composable
private fun crypto_term_dialog(
    on_dismiss: () -> Unit,
    on_confirm: (Int) -> Unit,
) {
    val colors = AsterMaterial.colors
    var selected_term by remember { mutableStateOf(1) }
    val terms = listOf(
        1 to stringResource(R.string.crypto_term_1_month),
        3 to stringResource(R.string.crypto_term_3_months),
        6 to stringResource(R.string.crypto_term_6_months),
        12 to stringResource(R.string.crypto_term_12_months),
        24 to stringResource(R.string.crypto_term_24_months),
    )

    org.astermail.android.design.components.AsterDialog(
        on_dismiss = on_dismiss,
        title = stringResource(R.string.crypto_term_title),
        body = {
            Column(verticalArrangement = Arrangement.spacedBy(AsterSpacing.xs)) {
                terms.forEach { (months, label) ->
                    val term_active = selected_term == months
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(SquircleShape(10.dp))
                            .background(if (term_active) colors.accent_blue else colors.bg_secondary)
                            .border(1.dp, if (term_active) colors.accent_blue else colors.border_secondary, SquircleShape(10.dp))
                            .clickable { selected_term = months }
                            .padding(AsterSpacing.md),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            label,
                            color = if (term_active) Color.White else colors.text_primary,
                            fontSize = 14.sp,
                            fontWeight = if (term_active) FontWeight.SemiBold else FontWeight.Normal,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.weight(1f),
                        )
                        if (term_active) {
                            Spacer(Modifier.width(AsterSpacing.sm))
                            Icon(TablerIcons.Check, null, tint = Color.White, modifier = Modifier.size(18.dp))
                        }
                    }
                }
            }
        },
        footer = {
            org.astermail.android.design.components.AsterDialogOutlineButton(
                label = stringResource(R.string.back),
                onClick = on_dismiss,
            )
            org.astermail.android.design.components.AsterDialogPrimaryButton(
                label = stringResource(R.string.action_continue),
                onClick = { on_confirm(selected_term) },
            )
        },
    )
}

@Composable
private fun crypto_coin_dialog(
    coins: List<org.astermail.android.api.billing.CryptoNativeCoin>,
    on_dismiss: () -> Unit,
    on_select: (org.astermail.android.api.billing.CryptoNativeCoin) -> Unit,
) {
    val colors = AsterMaterial.colors
    val ordered = remember(coins) { coins.sortedByDescending { it.recommended } }

    org.astermail.android.design.components.AsterDialog(
        on_dismiss = on_dismiss,
        title = stringResource(R.string.crypto_native_choose_coin),
        body = {
            Column(verticalArrangement = Arrangement.spacedBy(AsterSpacing.sm)) {
                ordered.forEach { coin ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(SquircleShape(14.dp))
                            .background(colors.bg_secondary)
                            .border(1.dp, colors.border_secondary, SquircleShape(14.dp))
                            .clickable { on_select(coin) }
                            .padding(AsterSpacing.md),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(AsterSpacing.md),
                    ) {
                        coin_mark(
                            currency = coin.currency,
                            chain = coin.chain,
                            label = coin.display_name,
                            size = 26.dp,
                        )
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                coin.display_name,
                                color = colors.text_primary,
                                fontSize = 14.sp,
                                fontWeight = FontWeight.Medium,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                            Text(
                                stringResource(R.string.crypto_native_coin_on_chain, coin.chain),
                                color = colors.text_tertiary,
                                fontSize = 12.sp,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                        }
                        if (coin.recommended) {
                            Text(
                                stringResource(R.string.crypto_native_recommended),
                                color = colors.accent_blue,
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Medium,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                        }
                    }
                }
            }
        },
        footer = {
            org.astermail.android.design.components.AsterDialogOutlineButton(
                label = stringResource(R.string.back),
                onClick = on_dismiss,
            )
        },
    )
}

@Composable
private fun billing_interval_toggle(selected: String, on_select: (String) -> Unit) {
    val options = listOf(
        switcher_option(id = "month", label = stringResource(R.string.settings_billing_monthly)),
        switcher_option(id = "year", label = stringResource(R.string.settings_billing_yearly)),
    )
    aster_segmented(value = selected, options = options, on_change = on_select)
}

@Composable
private fun feature_row(@StringRes feature_res: Int) {
    val colors = AsterMaterial.colors
    Row(
        modifier = Modifier.padding(vertical = 3.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            imageVector = TablerIcons.Check,
            contentDescription = null,
            tint = colors.accent_blue,
            modifier = Modifier.size(16.dp),
        )
        Spacer(Modifier.width(AsterSpacing.sm))
        Text(text = stringResource(feature_res), color = colors.text_primary, fontSize = 13.sp)
    }
}

@Composable
private fun aster_plan_badge(
    text: String,
    accent: androidx.compose.ui.graphics.Color,
    font_size: TextUnit = 10.sp,
    horizontal_padding: Dp = 8.dp,
    vertical_padding: Dp = 3.dp,
) {
    Box(
        modifier = Modifier
            .clip(androidx.compose.foundation.shape.RoundedCornerShape(6.dp))
            .background(accent)
            .padding(horizontal = horizontal_padding, vertical = vertical_padding),
    ) {
        Text(text = text, color = Color.White, fontSize = font_size, fontWeight = FontWeight.Bold)
    }
}

@Composable
internal fun plan_recommendation_banner(
    recommendation: plan_recommendation,
    current_plan_name: String?,
    recommended_tier_name: String?,
) {
    val colors = AsterMaterial.colors
    if (!recommendation.is_paid || current_plan_name.isNullOrBlank()) return

    val show_storage_tight =
        !recommendation.is_top_tier &&
            recommendation.storage_is_tight &&
            recommended_tier_name != null

    AsterCard(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(AsterSpacing.lg)) {
            Text(
                text = if (recommendation.is_top_tier) {
                    stringResource(R.string.settings_plan_top_tier_title)
                } else {
                    stringResource(R.string.settings_plan_current_title, current_plan_name)
                },
                color = colors.text_primary,
                fontSize = 15.sp,
                fontWeight = FontWeight.SemiBold,
            )
            Spacer(Modifier.height(AsterSpacing.xs))
            Text(
                text = when {
                    recommendation.is_top_tier -> stringResource(
                        R.string.settings_plan_top_tier_note,
                        current_plan_name,
                    )
                    show_storage_tight -> stringResource(
                        R.string.settings_plan_storage_tight_note,
                        recommendation.storage_percent.toInt(),
                        recommended_tier_name.orEmpty(),
                    )
                    else -> stringResource(
                        R.string.settings_plan_current_note,
                        recommendation.storage_percent.toInt(),
                    )
                },
                color = colors.text_secondary,
                fontSize = 13.sp,
            )
        }
    }
    v_gap(AsterSpacing.md)
}

@Composable
private fun plan_tier_surface(
    highlighted: Boolean,
    content: @Composable androidx.compose.foundation.layout.ColumnScope.() -> Unit,
) {
    if (highlighted) {
        galaxy_surface(modifier = Modifier.fillMaxWidth(), content = content)
    } else {
        AsterCard(modifier = Modifier.fillMaxWidth(), content = content)
    }
}

@Composable
private fun plan_tier_card(
    tier: plan_tier,
    billing_interval: String,
    is_current: Boolean,
    is_downgrade: Boolean = false,
    is_recommended: Boolean = false,
    monthly_cents: Int? = null,
    yearly_cents: Int? = null,
    currency: String,
    plans_failed: Boolean = false,
    on_see_pricing: () -> Unit = {},
    on_choose: () -> Unit,
) {
    val colors = AsterMaterial.colors
    val plan_name = stringResource(tier.name_res)
    val is_yearly = billing_interval == "year"
    val save_cents = if (monthly_cents != null && yearly_cents != null) (monthly_cents * 12 - yearly_cents).coerceAtLeast(0) else null
    val amount_cents = if (is_yearly) yearly_cents else monthly_cents
    val price_known = amount_cents != null
    plan_tier_surface(highlighted = is_recommended && !is_current) {
        if (is_current || is_recommended) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = AsterSpacing.md),
                contentAlignment = Alignment.Center,
            ) {
                if (is_current) {
                    aster_plan_badge(
                        text = stringResource(R.string.current_plan),
                        accent = colors.success,
                        font_size = 11.sp,
                        horizontal_padding = 10.dp,
                        vertical_padding = 4.dp,
                    )
                } else {
                    galaxy_badge(text = stringResource(R.string.most_popular), font_size = 11.sp)
                }
            }
        }
        Column(modifier = Modifier.padding(AsterSpacing.lg)) {
            Text(
                text = plan_name,
                color = colors.text_primary,
                fontSize = 20.sp,
                fontWeight = FontWeight.Bold,
            )
            Spacer(Modifier.height(2.dp))
            Text(
                text = stringResource(tier.tagline_res),
                color = colors.text_tertiary,
                fontSize = 12.sp,
            )
            Spacer(Modifier.height(AsterSpacing.md))
            if (price_known) {
                Row(verticalAlignment = Alignment.Bottom) {
                    Text(
                        text = format_price(amount_cents ?: 0, currency),
                        color = colors.text_primary,
                        fontSize = 26.sp,
                        fontWeight = FontWeight.Bold,
                    )
                    Spacer(Modifier.width(AsterSpacing.xs))
                    Text(
                        text = if (is_yearly) stringResource(R.string.plan_price_per_year) else stringResource(R.string.plan_price_per_month),
                        color = colors.text_muted,
                        fontSize = 13.sp,
                        modifier = Modifier.padding(bottom = 3.dp),
                    )
                }
            } else {
                Text(
                    text = if (plans_failed) stringResource(R.string.see_pricing) else stringResource(R.string.loading),
                    color = colors.text_secondary,
                    fontSize = 15.sp,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier
                        .heightIn(min = 48.dp)
                        .clickable(enabled = plans_failed, role = Role.Button, onClick = on_see_pricing)
                        .padding(vertical = 14.dp),
                )
            }
            if (is_yearly && save_cents != null) {
                Spacer(Modifier.height(AsterSpacing.sm))
                Box(
                    modifier = Modifier
                        .clip(SquircleShape(10.dp))
                        .background(colors.success.copy(alpha = 0.15f))
                        .padding(horizontal = AsterSpacing.sm, vertical = 3.dp),
                ) {
                    Text(
                        text = stringResource(R.string.plan_save_per_year, format_price(save_cents, currency)),
                        color = colors.success,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.SemiBold,
                    )
                }
            }
            Spacer(Modifier.height(AsterSpacing.md))
            AsterDivider()
            Spacer(Modifier.height(AsterSpacing.md))
            if (tier.lead_res != null) {
                Text(
                    text = stringResource(tier.lead_res),
                    color = colors.text_secondary,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.SemiBold,
                )
                Spacer(Modifier.height(AsterSpacing.xs))
            }
            tier.features.forEach { feature_res ->
                feature_row(feature_res)
            }
            Spacer(Modifier.height(AsterSpacing.md))
            if (is_current) {
                AsterSecondaryButton(
                    label = stringResource(R.string.current_plan),
                    onClick = {},
                    enabled = false,
                )
            } else if (!price_known) {
                AsterSecondaryButton(
                    label = stringResource(R.string.see_pricing),
                    onClick = on_see_pricing,
                    enabled = plans_failed,
                )
            } else {
                AsterButton(
                    label = if (is_downgrade) stringResource(R.string.downgrade_to, plan_name) else stringResource(R.string.upgrade_to, plan_name),
                    onClick = on_choose,
                )
            }
        }
    }
}

@Composable
private fun notice_row(
    text: String,
    accent: Color,
    loading: Boolean = false,
    action_label: String? = null,
    on_action: (() -> Unit)? = null,
    on_dismiss: (() -> Unit)? = null,
) {
    val colors = AsterMaterial.colors
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(SquircleShape(10.dp))
            .background(accent.copy(alpha = 0.10f))
            .padding(horizontal = AsterSpacing.md, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(AsterSpacing.sm),
    ) {
        if (loading) {
            androidx.compose.material3.CircularProgressIndicator(
                color = accent,
                strokeWidth = 2.dp,
                modifier = Modifier.size(14.dp),
            )
        }
        Text(
            text = text,
            color = colors.text_secondary,
            fontSize = 13.sp,
            modifier = Modifier.weight(1f),
        )
        if (action_label != null && on_action != null) {
            Text(
                text = action_label,
                color = colors.accent_blue,
                fontSize = 13.sp,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier
                    .heightIn(min = 48.dp)
                    .clip(SquircleShape(10.dp))
                    .clickable(role = Role.Button, onClick = on_action)
                    .padding(vertical = 14.dp),
            )
        }
        if (on_dismiss != null) {
            Box(
                modifier = Modifier
                    .size(48.dp)
                    .clip(CircleShape)
                    .clickable(role = Role.Button, onClick = on_dismiss),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    imageVector = TablerIcons.X,
                    contentDescription = stringResource(R.string.dismiss),
                    tint = colors.text_muted,
                    modifier = Modifier.size(16.dp),
                )
            }
        }
    }
}
