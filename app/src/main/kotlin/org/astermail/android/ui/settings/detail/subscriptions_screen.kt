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

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.content.Intent
import android.net.Uri
import androidx.annotation.StringRes
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
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import org.astermail.android.R
import org.astermail.android.billing.BillingViewModel
import org.astermail.android.billing.PlayReview
import org.astermail.android.billing.is_resumable_crypto_invoice
import java.util.Locale
import org.astermail.android.design.AsterMaterial
import org.astermail.android.design.AsterRadius
import org.astermail.android.design.AsterSpacing
import org.astermail.android.design.SquircleShape
import org.astermail.android.design.components.AsterButton
import org.astermail.android.design.components.AsterCard
import org.astermail.android.design.components.AsterDivider
import org.astermail.android.design.components.AsterSecondaryButton
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

private fun format_price(cents: Int, currency: String): String {
    val amount = cents / 100.0
    return try {
        val cur = java.util.Currency.getInstance(currency.uppercase())
        val fmt = java.text.NumberFormat.getCurrencyInstance(Locale.US)
        fmt.currency = cur
        fmt.format(amount)
    } catch (_: Throwable) {
        "$%.2f".format(amount)
    }
}

private data class plan_tier(
    val code: String,
    @StringRes val name_res: Int,
    @StringRes val tagline_res: Int,
    val monthly_cents: Int,
    val yearly_cents: Int,
    val save_cents: Int,
    val recommended: Boolean,
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
        monthly_cents = 299,
        yearly_cents = 2899,
        save_cents = 689,
        recommended = false,
        lead_res = null,
        features = listOf(
            R.string.settings_plan_bullet_star_storage,
            R.string.settings_plan_bullet_star_attachments,
            R.string.settings_plan_bullet_star_aliases,
            R.string.settings_plan_bullet_star_domains,
            R.string.settings_plan_bullet_unlimited_daily_emails,
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
        monthly_cents = 899,
        yearly_cents = 8699,
        save_cents = 2089,
        recommended = true,
        lead_res = R.string.settings_plan_lead_star,
        features = listOf(
            R.string.settings_plan_bullet_nova_storage,
            R.string.settings_plan_bullet_nova_attachments,
            R.string.settings_plan_bullet_unlimited_aliases,
            R.string.settings_plan_bullet_nova_domains,
            R.string.settings_plan_bullet_unlimited_daily_emails,
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
        monthly_cents = 1799,
        yearly_cents = 17399,
        save_cents = 4189,
        recommended = false,
        lead_res = R.string.settings_plan_lead_nova,
        features = listOf(
            R.string.settings_plan_bullet_supernova_storage,
            R.string.settings_plan_bullet_supernova_attachments,
            R.string.settings_plan_bullet_unlimited_aliases,
            R.string.settings_plan_bullet_unlimited_domains,
            R.string.settings_plan_bullet_unlimited_daily_emails,
            R.string.settings_plan_bullet_tracker_protection,
            R.string.settings_plan_bullet_receipt_tracking,
            R.string.settings_plan_bullet_external_accounts,
            R.string.settings_plan_bullet_bridge_access,
            R.string.settings_plan_bullet_dedicated_support,
            R.string.settings_plan_bullet_early_access,
        ),
    ),
)

private fun plan_code_of(plan_name: String?): String {
    val lower = plan_name?.trim()?.lowercase().orEmpty()
    return when {
        lower.contains("supernova") -> "supernova"
        lower.contains("nova") -> "nova"
        lower.contains("star") -> "star"
        lower.isBlank() || lower.contains("free") -> "free"
        else -> lower
    }
}

private fun Context.findActivity(): Activity? {
    var ctx: Context? = this
    while (ctx is ContextWrapper) {
        if (ctx is Activity) return ctx
        ctx = ctx.baseContext
    }
    return null
}

@Composable
fun SubscriptionsScreen(
    on_back: () -> Unit,
    on_open: (id: String) -> Unit = {},
    scroll_to_addons: Boolean = false,
    on_open_crypto_invoice: (id: String) -> Unit = {},
) {
    val vm: SettingsViewModel = shared_settings_view_model()
    val billing_vm: BillingViewModel = hiltViewModel()
    val state by vm.state.collectAsStateWithLifecycle()
    val billing_state by billing_vm.state.collectAsStateWithLifecycle()
    val colors = AsterMaterial.colors
    val context = LocalContext.current

    LaunchedEffect(Unit) {
        vm.load_subscription()
        billing_vm.load_storage_addons()
        billing_vm.load_crypto_native_coins()
        billing_vm.load_pending_crypto_invoices()
    }

    LaunchedEffect(billing_state.created_crypto_invoice_id) {
        val id = billing_state.created_crypto_invoice_id ?: return@LaunchedEffect
        billing_vm.consume_created_crypto_invoice()
        on_open_crypto_invoice(id)
    }

    LaunchedEffect(billing_state.error) {
        val err = billing_state.error ?: return@LaunchedEffect
        android.widget.Toast.makeText(context, err, android.widget.Toast.LENGTH_LONG).show()
        billing_vm.clear_messages()
    }

    LaunchedEffect(billing_state.info) {
        val info = billing_state.info ?: return@LaunchedEffect
        android.widget.Toast.makeText(context, info, android.widget.Toast.LENGTH_SHORT).show()
        billing_vm.clear_messages()
        vm.load_subscription()
    }

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

    LaunchedEffect(Unit) {
        billing_vm.review_request.collect {
            context.findActivity()?.let { PlayReview.request(it) }
        }
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

    val detected_currency = "usd"
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
    var show_crypto_coins by remember { mutableStateOf(false) }
    var pending_term_months by remember { mutableStateOf(1) }

    val sub = state.subscription
    val current_code = sub?.plan?.code ?: plan_code_of(sub?.effective_plan_name)
    val current_features = remember(current_code) {
        when (current_code) {
            "family" -> family_features
            "duo" -> duo_features
            else -> plan_tiers.firstOrNull { it.code == current_code }?.features ?: free_features
        }
    }
    val default_interval = stringResource(R.string.settings_interval_default)
    val plan_free_label = stringResource(R.string.plan_name_free)
    var billing_interval by remember { mutableStateOf("year") }
    val plan_load_settled = remember_load_settled(state.is_loading)

    val payment_failed_due = sub?.let {
        org.astermail.android.billing.payment_failed_due_date(
            status = it.status,
            payment_failed_at = it.payment_failed_at,
            grace_period_end = it.grace_period_end,
            current_period_end = it.current_period_end,
        )
    }

    detail_scaffold(title = stringResource(R.string.plan_billing), on_back = on_back, scroll_state = scroll_state) {
        if (payment_failed_due != null && !org.astermail.android.billing.payment_failed_banner_session.dismissed) {
            org.astermail.android.ui.common.payment_failed_banner(
                plan_name = sub?.effective_plan_name ?: plan_free_label,
                due_date = payment_failed_due,
                is_loading = billing_state.is_acting && billing_state.acting_action == "portal",
                on_update_card = { if (!billing_state.is_acting) billing_vm.open_portal() },
                on_dismiss = { org.astermail.android.billing.payment_failed_banner_session.dismissed = true },
            )
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
                                text = sub?.effective_plan_name ?: plan_free_label,
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
                    if (current_code != "free") {
                        Spacer(Modifier.size(AsterSpacing.lg))
                        AsterSecondaryButton(
                            label = if (billing_state.is_acting && billing_state.acting_action == "portal") stringResource(R.string.loading) else stringResource(R.string.manage_subscription),
                            onClick = { if (!billing_state.is_acting) billing_vm.open_portal() },
                            enabled = !billing_state.is_acting,
                        )
                    }
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
        plan_tiers.forEach { tier ->
            plan_tier_card(
                tier = tier,
                billing_interval = billing_interval,
                is_current = tier.code == current_code,
                currency = detected_currency,
                on_choose = {
                    pending_plan_code = tier.code
                    pending_addon_id = null
                    show_payment_picker = true
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
                            )
                            Text(
                                text = "${format_price(addon.price_cents, detected_currency)}/${addon.billing_period}",
                                color = colors.text_tertiary,
                                fontSize = 13.sp,
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
                    text = stringResource(R.string.storage_addons_active_count, addons.active_addons.size),
                    color = colors.text_tertiary,
                    fontSize = 12.sp,
                )
            }
        }

        v_gap(AsterSpacing.xs)
        Text(
            text = stringResource(R.string.settings_prices_usd_note),
            color = colors.text_tertiary,
            fontSize = 12.sp,
            modifier = Modifier.fillMaxWidth(),
        )
        v_gap(AsterSpacing.md)
        AsterSecondaryButton(
            label = "${stringResource(R.string.view_all_features)} →",
            onClick = {
                runCatching {
                    context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("https://astermail.org/pricing")))
                }
            },
        )

        if (billing_state.error != null) {
            v_gap(AsterSpacing.sm)
            Text(
                text = billing_state.error ?: "",
                color = colors.danger,
                fontSize = 13.sp,
            )
        }

        if (current_code != "free") {
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
        payment_method_dialog(
            title = pending_plan_code?.let { code ->
                plan_tiers.firstOrNull { it.code == code }?.let { context.getString(R.string.upgrade_to, context.getString(it.name_res)) }
                    ?: context.getString(R.string.upgrade)
            } ?: context.getString(R.string.storage_addons_title),
            on_dismiss = { show_payment_picker = false },
            on_card = {
                show_payment_picker = false
                pending_plan_code?.let { billing_vm.start_checkout(it, billing_interval, detected_currency) }
                    ?: pending_addon_id?.let { billing_vm.purchase_storage_addon(it) }
            },
            on_crypto = {
                show_payment_picker = false
                show_crypto_terms = true
            },
        )
    }

    if (show_crypto_terms) {
        crypto_term_dialog(
            on_dismiss = { show_crypto_terms = false },
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
            on_dismiss = { show_crypto_coins = false },
            on_select = { coin ->
                show_crypto_coins = false
                pending_plan_code?.let {
                    billing_vm.create_crypto_native_invoice(it, pending_term_months, coin.currency, coin.chain)
                }
            },
        )
    }
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
private fun payment_method_dialog(
    title: String,
    on_dismiss: () -> Unit,
    on_card: () -> Unit,
    on_crypto: () -> Unit,
) {
    val colors = AsterMaterial.colors
    org.astermail.android.design.components.AsterDialog(
        on_dismiss = on_dismiss,
        title = title,
        body = {
            Column(verticalArrangement = Arrangement.spacedBy(AsterSpacing.sm)) {
                payment_method_option(
                    icon = TablerIcons.CreditCard,
                    label = stringResource(R.string.payment_method_card),
                    subtitle = stringResource(R.string.payment_method_card_subtitle),
                    onClick = on_card,
                )
                payment_method_option(
                    icon = TablerIcons.CurrencyBitcoin,
                    label = stringResource(R.string.payment_method_crypto),
                    subtitle = stringResource(R.string.payment_method_crypto_subtitle),
                    onClick = on_crypto,
                )
            }
        },
        footer = {
            org.astermail.android.design.components.AsterDialogOutlineButton(
                label = stringResource(R.string.cancel),
                onClick = on_dismiss,
            )
        },
    )
}

@Composable
private fun payment_method_option(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    label: String,
    subtitle: String,
    onClick: () -> Unit,
) {
    val colors = AsterMaterial.colors
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(SquircleShape(14.dp))
            .background(colors.bg_secondary)
            .border(1.dp, colors.border_secondary, SquircleShape(14.dp))
            .clickable(onClick = onClick)
            .padding(AsterSpacing.md),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(AsterSpacing.md),
    ) {
        Icon(imageVector = icon, contentDescription = null, tint = colors.text_tertiary, modifier = Modifier.size(22.dp))
        Column {
            Text(label, color = colors.text_primary, fontSize = 14.sp, fontWeight = FontWeight.Medium)
            Text(subtitle, color = colors.text_tertiary, fontSize = 12.sp)
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
                        )
                        if (term_active) {
                            Icon(TablerIcons.Check, null, tint = Color.White, modifier = Modifier.size(18.dp))
                        }
                    }
                }
            }
        },
        footer = {
            org.astermail.android.design.components.AsterDialogOutlineButton(
                label = stringResource(R.string.cancel),
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
                        Icon(
                            imageVector = TablerIcons.CurrencyBitcoin,
                            contentDescription = null,
                            tint = colors.text_tertiary,
                            modifier = Modifier.size(22.dp),
                        )
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                coin.display_name,
                                color = colors.text_primary,
                                fontSize = 14.sp,
                                fontWeight = FontWeight.Medium,
                            )
                            Text(
                                stringResource(R.string.crypto_native_coin_on_chain, coin.chain),
                                color = colors.text_tertiary,
                                fontSize = 12.sp,
                            )
                        }
                        if (coin.recommended) {
                            Text(
                                stringResource(R.string.crypto_native_recommended),
                                color = colors.accent_blue,
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Medium,
                            )
                        }
                    }
                }
            }
        },
        footer = {
            org.astermail.android.design.components.AsterDialogOutlineButton(
                label = stringResource(R.string.cancel),
                onClick = on_dismiss,
            )
        },
    )
}

@Composable
private fun billing_interval_toggle(selected: String, on_select: (String) -> Unit) {
    val colors = AsterMaterial.colors
    val options = listOf(
        "month" to stringResource(R.string.settings_billing_monthly),
        "year" to stringResource(R.string.settings_billing_yearly),
    )
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
                    .clip(SquircleShape(14.dp))
                    .background(if (active) colors.accent_blue else Color.Transparent)
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
private fun feature_row(@StringRes feature_res: Int) {
    val colors = AsterMaterial.colors
    Row(
        modifier = Modifier.padding(vertical = 3.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            imageVector = TablerIcons.Check,
            contentDescription = null,
            tint = colors.success,
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
private fun plan_tier_card(
    tier: plan_tier,
    billing_interval: String,
    is_current: Boolean,
    currency: String = "usd",
    on_choose: () -> Unit,
) {
    val colors = AsterMaterial.colors
    val plan_name = stringResource(tier.name_res)
    val is_yearly = billing_interval == "year"
    val amount_cents = if (is_yearly) tier.yearly_cents else tier.monthly_cents
    AsterCard(modifier = Modifier.fillMaxWidth()) {
        if (is_current || tier.recommended) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(if (is_current) colors.success else colors.accent_blue)
                    .padding(vertical = 5.dp),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = if (is_current) stringResource(R.string.current_plan) else stringResource(R.string.most_popular),
                    color = Color.White,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                )
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
            Row(verticalAlignment = Alignment.Bottom) {
                Text(
                    text = format_price(amount_cents, currency),
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
            if (is_yearly) {
                Spacer(Modifier.height(AsterSpacing.sm))
                Box(
                    modifier = Modifier
                        .clip(SquircleShape(10.dp))
                        .background(colors.success.copy(alpha = 0.15f))
                        .padding(horizontal = AsterSpacing.sm, vertical = 3.dp),
                ) {
                    Text(
                        text = stringResource(R.string.plan_save_per_year, format_price(tier.save_cents, currency)),
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
            } else {
                AsterButton(
                    label = stringResource(R.string.upgrade_to, plan_name),
                    onClick = on_choose,
                )
            }
        }
    }
}
