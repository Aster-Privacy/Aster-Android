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

import android.content.ClipData
import androidx.compose.foundation.clickable
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.CircleShape
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
import org.astermail.android.api.billing.StorageAddonItem
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

@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun billing_addons_panel(
    available: List<StorageAddonItem>,
    active: List<UserActiveAddon>,
    selected_id: String?,
    currency: String,
    is_acting: Boolean,
    is_buying: Boolean,
    on_select: (String) -> Unit,
    on_buy: () -> Unit,
) {
    val colors = AsterMaterial.colors
    val per_month = stringResource(R.string.fix_billing_per_month_short)
    Column(modifier = Modifier.fillMaxWidth()) {
        active.forEach { addon ->
            settings_row_gap(modifier = Modifier)
            val ending = addon.cancel_at_period_end
            detail_row(
                title = addon.size_label.ifBlank { format_storage_short(addon.size_bytes) },
                subtitle = if (ending && addon.current_period_end != null) {
                    stringResource(R.string.ends_date, absolute_date_label(addon.current_period_end))
                } else {
                    format_money(addon.price_cents.toLong(), currency) + per_month
                },
                icon = billing_icon_storage,
                trailing = {
                    Text(
                        text = stringResource(if (ending) R.string.billing_status_ending else R.string.active),
                        color = if (ending) colors.warning else colors.success,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.SemiBold,
                    )
                },
            )
        }
        if (available.isNotEmpty()) {
            settings_row_gap(modifier = Modifier)
            val selected = available.firstOrNull { it.id == selected_id }
            Column(modifier = Modifier.padding(horizontal = AsterSpacing.lg, vertical = AsterSpacing.md)) {
                FlowRow(
                    modifier = Modifier.fillMaxWidth().selectableGroup(),
                    horizontalArrangement = Arrangement.spacedBy(AsterSpacing.sm),
                    verticalArrangement = Arrangement.spacedBy(AsterSpacing.sm),
                ) {
                    available.forEach { addon ->
                        billing_size_chip(
                            label = if (addon.storage_bytes > 0) format_storage_short(addon.storage_bytes) else addon.name,
                            selected = addon.id == selected_id,
                            enabled = !is_acting,
                            on_click = { on_select(addon.id) },
                        )
                    }
                }
                Spacer(Modifier.height(AsterSpacing.lg))
                Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = if (selected != null) {
                            stringResource(R.string.billing_addon_summary, format_storage_short(selected.storage_bytes))
                        } else {
                            stringResource(R.string.billing_addon_pick_size)
                        },
                        color = if (selected != null) colors.text_primary else colors.text_tertiary,
                        fontSize = 15.sp,
                        fontWeight = FontWeight.Medium,
                        modifier = Modifier.weight(1f),
                    )
                    if (selected != null) {
                        Spacer(Modifier.width(AsterSpacing.md))
                        billing_price_column(
                            amount = format_money(selected.price_cents.toLong(), currency),
                            unit = per_month,
                            note = null,
                            enabled = !is_acting,
                        )
                    }
                }
                Spacer(Modifier.height(AsterSpacing.md))
                AsterButton(
                    label = stringResource(R.string.buy_more_storage),
                    onClick = on_buy,
                    enabled = !is_acting && selected_id != null,
                    is_loading = is_buying,
                )
                Spacer(Modifier.height(AsterSpacing.sm))
                Text(
                    text = stringResource(R.string.settings_storage_addons_monthly_note),
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

@Composable
private fun billing_size_chip(
    label: String,
    selected: Boolean,
    enabled: Boolean,
    on_click: () -> Unit,
) {
    val colors = AsterMaterial.colors
    Box(
        modifier = Modifier
            .clip(CircleShape)
            .background(if (selected) colors.accent_blue else Color.Transparent)
            .border(1.dp, if (selected) colors.accent_blue else colors.border_secondary, CircleShape)
            .selectable(selected = selected, enabled = enabled, role = Role.RadioButton, onClick = on_click)
            .padding(horizontal = 14.dp, vertical = 8.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = label,
            color = when {
                selected -> colors.on_accent
                enabled -> colors.text_secondary
                else -> colors.text_muted
            },
            fontSize = 13.sp,
            fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Medium,
            maxLines = 1,
        )
    }
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
                        modifier = Modifier.size(18.dp),
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
                .verticalScroll(rememberScrollState())
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
