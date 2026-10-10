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

import org.astermail.android.ui.common.vertical_scroll_with_indicator
import android.content.ClipData
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
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.rememberModalBottomSheetState
import org.astermail.android.design.components.AsterCard
import org.astermail.android.design.components.AsterDragHandle
import org.astermail.android.ui.common.sheet_container_color
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import compose.icons.TablerIcons
import compose.icons.tablericons.Copy
import compose.icons.tablericons.ChevronRight
import org.astermail.android.R
import org.astermail.android.api.billing.BillingHistoryItem
import org.astermail.android.api.billing.CreditPackageItem
import org.astermail.android.billing.is_featured_storage_addon
import org.astermail.android.billing.is_supernova_nudge_addon
import org.astermail.android.billing.shows_storage_usage_nudge
import org.astermail.android.billing.storage_addon_per_month_cents
import org.astermail.android.billing.storage_addon_quote
import org.astermail.android.billing.storage_addon_save_percent
import org.astermail.android.billing.storage_usage_percent
import org.astermail.android.api.billing.UserActiveAddon
import org.astermail.android.billing.format_money
import org.astermail.android.design.AsterMaterial
import org.astermail.android.design.AsterSpacing
import org.astermail.android.design.AsterSemanticColors
import org.astermail.android.design.components.AsterButton
import org.astermail.android.design.components.AsterDialog
import org.astermail.android.design.components.AsterDialogOutlineButton
import org.astermail.android.design.components.AsterDialogPrimaryButton
import org.astermail.android.design.components.AsterSwitch
import org.astermail.android.design.components.AsterTextField
import org.astermail.android.ui.auth.TurnstileWidget
import org.astermail.android.ui.common.write_to_clipboard
import java.util.Locale
import org.astermail.android.design.mirror_in_rtl

@Composable
internal fun billing_addons_panel(
    quotes: List<storage_addon_quote>,
    active: List<UserActiveAddon>,
    selected_id: String?,
    currency: String,
    is_acting: Boolean,
    is_buying: Boolean,
    interval: String = "month",
    yearly_badge: String? = null,
    on_interval: ((String) -> Unit)? = null,
    storage_used_bytes: Long = 0L,
    storage_limit_bytes: Long = 0L,
    storage_over_limit: Boolean = false,
    show_supernova_nudge: Boolean = false,
    on_supernova: () -> Unit = {},
    price_label_for: (Long, String) -> String? = { _, _ -> null },
    active_interval_for: (Long) -> String = { "month" },
    play_product_for: (Long) -> String? = { null },
    on_manage_play: (String) -> Unit = {},
    on_select: (String) -> Unit,
    on_buy: () -> Unit,
) {
    val colors = AsterMaterial.colors
    val per_month = stringResource(R.string.fix_billing_per_month_short)
    val per_year = stringResource(R.string.fix_billing_per_year_short)
    val suffix_for = { value: String -> if (value == "year") per_year else per_month }
    val offered = quotes.filter { it.sells(interval) }
    Column(modifier = Modifier.fillMaxWidth()) {
        active.forEach { addon ->
            settings_row_gap(modifier = Modifier)
            val ending = addon.cancel_at_period_end
            val active_interval = active_interval_for(addon.size_bytes)
            val play_product_id = play_product_for(addon.size_bytes)
            detail_row(
                title = addon.size_label.ifBlank { format_storage_short(addon.size_bytes) },
                subtitle = if (ending && addon.current_period_end != null) {
                    stringResource(R.string.ends_date, absolute_date_label(addon.current_period_end))
                } else {
                    (play_product_id?.let { price_label_for(addon.size_bytes, active_interval) } ?: format_money(addon.price_cents.toLong(), currency)) +
                        suffix_for(active_interval)
                },
                icon = billing_icon_storage,
                on_click = play_product_id?.let { id -> { if (!is_acting) on_manage_play(id) } },
                trailing = {
                    Text(
                        text = if (play_product_id != null) {
                            stringResource(R.string.billing_play_manage)
                        } else {
                            stringResource(if (ending) R.string.billing_status_ending else R.string.active)
                        },
                        color = when {
                            play_product_id != null -> colors.accent_blue
                            ending -> colors.warning
                            else -> colors.success
                        },
                        fontSize = 13.sp,
                        fontWeight = FontWeight.SemiBold,
                    )
                },
            )
        }
        if (offered.isNotEmpty()) {
            settings_row_gap(modifier = Modifier)
            if (storage_limit_bytes > 0) {
                billing_addon_usage(
                    used_bytes = storage_used_bytes,
                    limit_bytes = storage_limit_bytes,
                    over_limit = storage_over_limit,
                )
            }
            if (on_interval != null) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(start = AsterSpacing.lg, end = AsterSpacing.lg, top = AsterSpacing.xs, bottom = AsterSpacing.md),
                    contentAlignment = Alignment.Center,
                ) {
                    aster_segmented(
                        value = interval,
                        options = listOf(
                            switcher_option(id = "month", label = stringResource(R.string.settings_billing_monthly)),
                            switcher_option(id = "year", label = stringResource(R.string.settings_billing_yearly), badge = yearly_badge),
                        ),
                        on_change = on_interval,
                    )
                }
            }
            val selected = offered.firstOrNull { it.addon_id == selected_id }
            Column(modifier = Modifier.fillMaxWidth().selectableGroup()) {
                offered.forEachIndexed { index, quote ->
                    if (index > 0) settings_row_gap()
                    billing_addon_option(
                        quote = quote,
                        interval = interval,
                        currency = currency,
                        selected = quote.addon_id == selected_id,
                        enabled = !is_acting,
                        per_month_unit = per_month,
                        show_supernova_nudge = show_supernova_nudge && is_supernova_nudge_addon(quote.storage_bytes),
                        on_supernova = on_supernova,
                        on_click = { on_select(quote.addon_id) },
                    )
                }
            }
            Column(modifier = Modifier.padding(horizontal = AsterSpacing.lg, vertical = AsterSpacing.md)) {
                if (selected == null) {
                    Text(
                        text = stringResource(R.string.billing_addon_pick_size),
                        color = colors.text_tertiary,
                        fontSize = 13.sp,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.fillMaxWidth().padding(vertical = AsterSpacing.sm),
                    )
                } else {
                    AsterButton(
                        label = stringResource(R.string.billing_addon_add_size, addon_size_label(selected)),
                        onClick = on_buy,
                        enabled = !is_acting,
                        is_loading = is_buying,
                    )
                }
                Spacer(Modifier.height(AsterSpacing.sm))
                Text(
                    text = stringResource(
                        if (interval == "year") R.string.settings_storage_addons_yearly_note else R.string.settings_storage_addons_monthly_note,
                    ),
                    color = colors.text_tertiary,
                    fontSize = 12.sp,
                    lineHeight = 16.sp,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        }
    }
}

private fun addon_size_label(quote: storage_addon_quote): String =
    if (quote.storage_bytes > 0) format_storage_short(quote.storage_bytes) else quote.name

@Composable
private fun billing_addon_usage(used_bytes: Long, limit_bytes: Long, over_limit: Boolean) {
    val colors = AsterMaterial.colors
    val fraction = used_bytes.toFloat() / limit_bytes.toFloat()
    val full = over_limit || fraction >= 1f
    val nudge = full || shows_storage_usage_nudge(used_bytes, limit_bytes)
    Column(modifier = Modifier.fillMaxWidth().padding(horizontal = AsterSpacing.lg, vertical = AsterSpacing.md)) {
        billing_meter(
            label = stringResource(R.string.storage),
            value_text = stringResource(
                R.string.storage_used_format,
                format_storage_short(used_bytes),
                format_storage_short(limit_bytes),
            ),
            fraction = fraction,
            status = when {
                full -> billing_meter_status.full
                nudge -> billing_meter_status.near
                else -> billing_meter_status.ok
            },
            status_text = null,
        )
        if (nudge) {
            Spacer(Modifier.height(AsterSpacing.sm))
            Text(
                text = stringResource(
                    R.string.billing_addon_usage_nudge,
                    storage_usage_percent(used_bytes, limit_bytes) ?: 100,
                ),
                color = if (full) colors.danger else colors.warning,
                fontSize = 13.sp,
                lineHeight = 18.sp,
            )
        }
    }
}

@Composable
private fun billing_addon_option(
    quote: storage_addon_quote,
    interval: String,
    currency: String,
    selected: Boolean,
    enabled: Boolean,
    per_month_unit: String,
    show_supernova_nudge: Boolean,
    on_supernova: () -> Unit,
    on_click: () -> Unit,
) {
    val colors = AsterMaterial.colors
    val is_yearly = interval == "year"
    val billed_cents = quote.cents_for(interval) ?: return
    val per_month_cents = storage_addon_per_month_cents(quote, interval) ?: billed_cents
    val billed_text = quote.label_for(interval) ?: format_money(billed_cents.toLong(), currency)
    val per_month_text = if (is_yearly) format_money(per_month_cents.toLong(), currency) else billed_text
    val save = if (is_yearly) storage_addon_save_percent(quote) else null
    billing_option_row(
        title = addon_size_label(quote),
        subtitle = if (is_yearly) {
            stringResource(R.string.billing_billed_yearly_total, billed_text)
        } else {
            stringResource(R.string.billing_billed_monthly)
        },
        selected = selected,
        enabled = enabled,
        title_note = if (is_featured_storage_addon(quote.storage_bytes)) stringResource(R.string.popular) else null,
        title_note_color = colors.accent_blue,
        on_click = on_click,
        trailing = {
            billing_price_column(
                amount = per_month_text,
                unit = per_month_unit,
                note = save?.let { stringResource(R.string.save_percent, it) },
                enabled = enabled,
                note_color = colors.success,
            )
        },
        below = if (show_supernova_nudge) {
            {
                Column {
                    Text(
                        text = stringResource(R.string.billing_addon_supernova_nudge),
                        color = colors.text_tertiary,
                        fontSize = 12.sp,
                        lineHeight = 16.sp,
                    )
                    billing_action_text(
                        label = stringResource(R.string.billing_addon_supernova_action),
                        on_click = on_supernova,
                    )
                }
            }
        } else {
            null
        },
    )
}

@Composable
internal fun invoice_status_label(status: String): String = when (status.lowercase(Locale.ROOT)) {
    "paid", "succeeded" -> stringResource(R.string.fix_billing_invoice_status_paid)
    "failed", "payment_failed" -> stringResource(R.string.fix_billing_invoice_status_failed)
    "open" -> stringResource(R.string.fix_billing_invoice_status_open)
    "pending", "processing" -> stringResource(R.string.fix_billing_invoice_status_pending)
    "draft" -> stringResource(R.string.fix_billing_invoice_status_draft)
    "void", "canceled", "cancelled" -> stringResource(R.string.fix_billing_invoice_status_void)
    "uncollectible" -> stringResource(R.string.fix_billing_invoice_status_uncollectible)
    "refunded" -> stringResource(R.string.fix_billing_invoice_status_refunded)
    else -> status.replaceFirstChar { it.uppercase() }
}

internal fun invoice_status_accent(status: String, colors: AsterSemanticColors): Color =
    when (status.lowercase(Locale.ROOT)) {
        "paid", "succeeded" -> colors.success
        "failed", "payment_failed", "uncollectible", "void", "canceled", "cancelled" -> colors.danger
        else -> colors.warning
    }

@Composable
internal fun billing_history_list(
    history: List<BillingHistoryItem>,
    on_open_pdf: (String) -> Unit,
) {
    val colors = AsterMaterial.colors
    if (history.isEmpty()) {
        Text(
            text = stringResource(R.string.fix_billing_history_empty),
            color = colors.text_tertiary,
            fontSize = 13.sp,
            textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth().padding(horizontal = AsterSpacing.lg, vertical = AsterSpacing.md),
        )
        return
    }
    val years = history.map { it.created_at.take(4) }.distinct()
    val show_years = years.size > 1
    Column(modifier = Modifier.fillMaxWidth()) {
        var last_year: String? = null
        history.forEach { item ->
            val year = item.created_at.take(4)
            if (show_years && year != last_year) {
                settings_row_gap(modifier = Modifier)
                Text(
                    text = year,
                    color = colors.text_tertiary,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.padding(start = AsterSpacing.lg, end = AsterSpacing.lg, top = AsterSpacing.md, bottom = AsterSpacing.xs),
                )
                last_year = year
            } else {
                settings_row_gap(modifier = Modifier)
            }
            val pdf_url = item.invoice_pdf_url
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = settings_row_min_height)
                    .then(if (pdf_url != null) Modifier.clickable(role = Role.Button) { on_open_pdf(pdf_url) } else Modifier)
                    .padding(horizontal = AsterSpacing.lg, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = item.description?.takeIf { it.isNotBlank() }
                            ?: item.plan_name?.takeIf { it.isNotBlank() }
                            ?: stringResource(R.string.fix_billing_invoice_payment),
                        color = colors.text_primary,
                        fontSize = 15.sp,
                        fontWeight = FontWeight.Medium,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Spacer(Modifier.height(2.dp))
                    Text(
                        text = absolute_date_label(item.created_at),
                        color = colors.text_tertiary,
                        fontSize = 13.sp,
                    )
                }
                Spacer(Modifier.width(AsterSpacing.md))
                Column(horizontalAlignment = Alignment.End) {
                    Text(
                        text = format_money(item.amount_cents.toLong(), item.currency),
                        color = colors.text_primary,
                        fontSize = 15.sp,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 1,
                    )
                    Spacer(Modifier.height(2.dp))
                    Text(
                        text = invoice_status_label(item.status),
                        color = invoice_status_accent(item.status, colors),
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Medium,
                    )
                }
                if (pdf_url != null) {
                    Spacer(Modifier.width(AsterSpacing.sm))
                    Icon(
                        imageVector = TablerIcons.ChevronRight,
                        contentDescription = stringResource(R.string.fix_billing_invoice_pdf),
                        tint = colors.text_muted,
                        modifier = Modifier.size(18.dp).mirror_in_rtl(),
                    )
                }
            }
        }
    }
}

@Composable
@OptIn(ExperimentalMaterial3Api::class)
internal fun billing_credits_sheet(
    balance_cents: Long?,
    currency: String,
    use_for_renewals: Boolean,
    settings_saving: Boolean,
    packages: List<CreditPackageItem>,
    busy: Boolean,
    acting_action: String?,
    on_toggle_renewals: (Boolean) -> Unit,
    on_buy: (package_id: String, crypto: Boolean) -> Unit,
    on_dismiss: () -> Unit,
) {
    val colors = AsterMaterial.colors
    val sheet_state = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    var selected_id by remember { mutableStateOf<String?>(null) }
    val selected = packages.firstOrNull { it.id == selected_id }
    ModalBottomSheet(
        onDismissRequest = on_dismiss,
        sheetState = sheet_state,
        shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp),
        containerColor = sheet_container_color(colors),
        tonalElevation = 0.dp,
        dragHandle = { AsterDragHandle() },
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(max = 760.dp)
                .vertical_scroll_with_indicator()
                .padding(horizontal = AsterSpacing.xl)
                .navigationBarsPadding(),
        ) {
            Text(
                text = stringResource(R.string.billing_credits_title),
                color = colors.text_primary,
                fontSize = 20.sp,
                fontWeight = FontWeight.Bold,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth(),
            )
            Spacer(Modifier.height(AsterSpacing.xl))
            Text(
                text = balance_cents?.let { format_money(it, currency) }
                    ?: stringResource(R.string.credits_unavailable),
                color = colors.text_primary,
                fontSize = 34.sp,
                fontWeight = FontWeight.Bold,
                letterSpacing = (-0.8).sp,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth(),
            )
            Spacer(Modifier.height(2.dp))
            Text(
                text = stringResource(R.string.credits_balance_label),
                color = colors.text_tertiary,
                fontSize = 13.sp,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth(),
            )
            Spacer(Modifier.height(AsterSpacing.xl))
            Text(
                text = stringResource(R.string.billing_credits_add_funds),
                color = colors.text_primary,
                fontSize = 15.sp,
                fontWeight = FontWeight.SemiBold,
            )
            Spacer(Modifier.height(2.dp))
            Text(
                text = stringResource(R.string.billing_credits_add_funds_body),
                color = colors.text_tertiary,
                fontSize = 13.sp,
                lineHeight = 18.sp,
            )
            Spacer(Modifier.height(AsterSpacing.md))
            if (packages.isEmpty()) {
                Text(
                    text = stringResource(R.string.billing_credits_packages_unavailable),
                    color = colors.text_tertiary,
                    fontSize = 13.sp,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth().padding(vertical = AsterSpacing.md),
                )
            } else {
                AsterCard(modifier = Modifier.fillMaxWidth()) {
                    Column(modifier = Modifier.selectableGroup()) {
                        packages.forEachIndexed { index, item ->
                            if (index > 0) settings_row_gap()
                            billing_option_row(
                                title = format_money(item.price_cents, currency),
                                selected = item.id == selected_id,
                                enabled = !busy,
                                title_note = if (item.bonus_cents > 0) {
                                    stringResource(R.string.billing_credits_bonus, format_money(item.bonus_cents, currency))
                                } else {
                                    null
                                },
                                title_note_color = colors.success,
                                on_click = { selected_id = item.id },
                            )
                        }
                    }
                }
                Spacer(Modifier.height(AsterSpacing.lg))
                AsterButton(
                    label = stringResource(R.string.billing_credits_pay_card),
                    onClick = { selected?.let { on_buy(it.id, false) } },
                    enabled = selected != null && !busy,
                    is_loading = busy && acting_action?.startsWith("credits_") == true,
                    modifier = Modifier.fillMaxWidth(),
                )
                Spacer(Modifier.height(AsterSpacing.xs))
                billing_link_row(
                    text = stringResource(R.string.billing_credits_pay_crypto),
                    on_click = { selected?.let { on_buy(it.id, true) } },
                    modifier = Modifier.fillMaxWidth(),
                )
            }
            Spacer(Modifier.height(AsterSpacing.lg))
            Row(
                modifier = Modifier.fillMaxWidth().heightIn(min = 52.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = stringResource(R.string.billing_credits_use_for_renewals),
                        color = colors.text_primary,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Medium,
                    )
                    Text(
                        text = stringResource(R.string.billing_credits_use_for_renewals_body),
                        color = colors.text_tertiary,
                        fontSize = 12.sp,
                        lineHeight = 16.sp,
                    )
                }
                Spacer(Modifier.width(AsterSpacing.md))
                AsterSwitch(
                    checked = use_for_renewals,
                    onCheckedChange = on_toggle_renewals,
                    enabled = !settings_saving && balance_cents != null,
                )
            }
            Spacer(Modifier.height(AsterSpacing.xl))
        }
    }
}

@Composable
internal fun billing_academic_dialog(
    status: String?,
    promo_code: String?,
    code_expires_at: String?,
    submitting: Boolean,
    sent: Boolean,
    error: String?,
    on_send: (email: String, token: String?) -> Unit,
    on_resend: (token: String?) -> Unit,
    on_dismiss: () -> Unit,
) {
    val colors = AsterMaterial.colors
    val context = LocalContext.current
    var email by remember { mutableStateOf("") }
    var captcha_token by remember { mutableStateOf<String?>(null) }
    var captcha_reset by remember { mutableIntStateOf(0) }
    var copied by remember { mutableStateOf(false) }
    val clipboard_label = stringResource(R.string.academic_discount_label)
    val verified = status == "verified" && !promo_code.isNullOrBlank()
    val pending = sent || status == "pending"
    AsterDialog(
        on_dismiss = on_dismiss,
        title = stringResource(R.string.academic_discount_label),
        body = {
            Column(modifier = Modifier.fillMaxWidth()) {
                when {
                    verified -> {
                        Text(
                            text = stringResource(R.string.billing_academic_verified_body),
                            color = colors.text_secondary,
                            fontSize = 13.sp,
                            lineHeight = 18.sp,
                        )
                        Spacer(Modifier.height(AsterSpacing.md))
                        Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = promo_code.orEmpty(),
                                    color = colors.text_primary,
                                    fontSize = 20.sp,
                                    fontWeight = FontWeight.Bold,
                                    fontFamily = FontFamily.Monospace,
                                    letterSpacing = 1.sp,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                )
                                if (code_expires_at != null) {
                                    Text(
                                        text = stringResource(R.string.billing_academic_code_expires, absolute_date_label(code_expires_at)),
                                        color = colors.text_tertiary,
                                        fontSize = 12.sp,
                                    )
                                }
                            }
                            Spacer(Modifier.width(AsterSpacing.sm))
                            Box(
                                modifier = Modifier
                                    .size(40.dp)
                                    .clip(billing_control_shape)
                                    .clickable(role = Role.Button) {
                                        if (write_to_clipboard(context, ClipData.newPlainText(clipboard_label, promo_code))) {
                                            copied = true
                                        }
                                    },
                                contentAlignment = Alignment.Center,
                            ) {
                                Icon(
                                    imageVector = TablerIcons.Copy,
                                    contentDescription = stringResource(R.string.copy),
                                    tint = colors.accent_blue,
                                    modifier = Modifier.size(20.dp),
                                )
                            }
                        }
                        if (copied) {
                            Spacer(Modifier.height(AsterSpacing.xs))
                            Text(
                                text = stringResource(R.string.copied),
                                color = colors.success,
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Medium,
                                textAlign = TextAlign.End,
                                modifier = Modifier.fillMaxWidth(),
                            )
                        }
                    }
                    pending -> {
                        Text(
                            text = stringResource(R.string.billing_academic_sent_title),
                            color = colors.text_primary,
                            fontSize = 15.sp,
                            fontWeight = FontWeight.SemiBold,
                        )
                        Spacer(Modifier.height(AsterSpacing.xs))
                        Text(
                            text = stringResource(R.string.billing_academic_sent_body),
                            color = colors.text_secondary,
                            fontSize = 13.sp,
                            lineHeight = 18.sp,
                        )
                        Spacer(Modifier.height(AsterSpacing.md))
                        TurnstileWidget(
                            on_token = { token -> captcha_token = token },
                            on_error = { captcha_token = null; captcha_reset++ },
                            on_expired = { captcha_token = null; captcha_reset++ },
                            reset_trigger = captcha_reset,
                            modifier = Modifier.height(65.dp).fillMaxWidth(),
                        )
                    }
                    else -> {
                        Text(
                            text = stringResource(R.string.billing_academic_body),
                            color = colors.text_secondary,
                            fontSize = 13.sp,
                            lineHeight = 18.sp,
                        )
                        Spacer(Modifier.height(AsterSpacing.md))
                        AsterTextField(
                            value = email,
                            onValueChange = { email = it },
                            label = stringResource(R.string.email),
                            placeholder = stringResource(R.string.billing_academic_email_placeholder),
                            singleLine = true,
                            keyboard_options = KeyboardOptions(keyboardType = KeyboardType.Email),
                            enabled = !submitting,
                        )
                        Spacer(Modifier.height(AsterSpacing.sm))
                        TurnstileWidget(
                            on_token = { token -> captcha_token = token },
                            on_error = { captcha_token = null; captcha_reset++ },
                            on_expired = { captcha_token = null; captcha_reset++ },
                            reset_trigger = captcha_reset,
                            modifier = Modifier.height(65.dp).fillMaxWidth(),
                        )
                    }
                }
                if (error != null) {
                    Spacer(Modifier.height(AsterSpacing.sm))
                    Text(
                        text = error,
                        color = colors.danger,
                        fontSize = 12.sp,
                        lineHeight = 16.sp,
                    )
                }
            }
        },
        footer = {
            AsterDialogOutlineButton(
                label = stringResource(R.string.close),
                onClick = on_dismiss,
                modifier = Modifier.weight(1f),
            )
            when {
                verified -> Unit
                pending -> AsterDialogPrimaryButton(
                    label = stringResource(R.string.billing_academic_resend),
                    onClick = { on_resend(captcha_token); captcha_token = null; captcha_reset++ },
                    modifier = Modifier.weight(1f),
                    enabled = !submitting && captcha_token != null,
                    is_loading = submitting,
                )
                else -> AsterDialogPrimaryButton(
                    label = stringResource(R.string.billing_academic_send),
                    onClick = { on_send(email, captcha_token); captcha_token = null; captcha_reset++ },
                    modifier = Modifier.weight(1f),
                    enabled = !submitting && captcha_token != null && email.contains("@"),
                    is_loading = submitting,
                )
            }
        },
    )
}
