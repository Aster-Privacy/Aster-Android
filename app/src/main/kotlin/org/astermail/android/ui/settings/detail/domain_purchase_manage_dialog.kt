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

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import org.astermail.android.ComposeActivity
import org.astermail.android.R
import org.astermail.android.api.domains.DomainOrder
import org.astermail.android.design.AsterMaterial
import org.astermail.android.design.AsterSpacing
import org.astermail.android.design.components.AsterDialog
import org.astermail.android.design.components.AsterDialogOutlineButton
import org.astermail.android.design.components.AsterDialogPrimaryButton
import org.astermail.android.settings.DomainPurchaseErrorKind

private const val support_address = "hello@astermail.org"
private const val expiring_soon_days = 30L

private fun parse_iso_day(iso: String): java.util.Date? = try {
    java.text.SimpleDateFormat("yyyy-MM-dd", java.util.Locale.US)
        .parse(iso.substringBefore('T'))
} catch (_: Throwable) {
    null
}

private fun format_day(iso: String): String {
    val parsed = parse_iso_day(iso) ?: return iso.substringBefore('T')
    return java.text.DateFormat.getDateInstance(java.text.DateFormat.MEDIUM).format(parsed)
}

private fun days_until(iso: String): Long? {
    val parsed = parse_iso_day(iso) ?: return null
    val diff = parsed.time - System.currentTimeMillis()
    return Math.ceil(diff / 86_400_000.0).toLong()
}

private fun format_order_price(cents: Int, currency: String): String {
    val amount = cents / 100.0
    return try {
        val fmt = java.text.NumberFormat.getCurrencyInstance(java.util.Locale.getDefault())
        fmt.currency = java.util.Currency.getInstance(currency.uppercase())
        fmt.format(amount)
    } catch (_: Throwable) {
        "$%.2f".format(amount)
    }
}

@Composable
internal fun domain_purchase_manage_dialog(
    order: DomainOrder,
    renewing: Boolean,
    renew_error: DomainPurchaseErrorKind?,
    on_renew: () -> Unit,
    on_dismiss: () -> Unit,
) {
    val colors = AsterMaterial.colors
    val context = LocalContext.current
    val remaining = order.expires_at?.let { days_until(it) }
    val lapsed = order.status == "lapsed" || (remaining != null && remaining < 0)
    val expiring_soon = !lapsed && remaining != null && remaining <= expiring_soon_days

    val status_text = when {
        lapsed -> stringResource(R.string.domain_purchase_purchased_lapsed)
        expiring_soon -> stringResource(R.string.domain_purchase_manage_status_expiring)
        else -> stringResource(R.string.domain_purchase_manage_status_active)
    }
    val status_color = when {
        lapsed -> colors.danger
        expiring_soon -> colors.accent_blue
        else -> colors.text_primary
    }
    val term_text = if (order.years <= 1) {
        stringResource(R.string.domain_purchase_one_year)
    } else {
        stringResource(R.string.domain_purchase_n_years, order.years)
    }

    AsterDialog(
        on_dismiss = on_dismiss,
        title = order.domain,
        message = stringResource(R.string.domain_purchase_manage_description),
        body = {
            Column(modifier = Modifier.fillMaxWidth()) {
                manage_row(
                    label = stringResource(R.string.domain_purchase_manage_status),
                    value = status_text,
                    value_color = status_color,
                )
                manage_row(
                    label = stringResource(R.string.domain_purchase_manage_registered),
                    value = format_day(order.created_at),
                )
                order.expires_at?.let {
                    manage_row(
                        label = stringResource(R.string.domain_purchase_manage_expires),
                        value = format_day(it),
                    )
                }
                manage_row(
                    label = stringResource(R.string.domain_purchase_manage_term),
                    value = term_text,
                )
                manage_row(
                    label = stringResource(R.string.domain_purchase_manage_paid),
                    value = format_order_price(order.price_cents, order.currency),
                )
                Spacer(Modifier.height(AsterSpacing.md))
                Text(
                    text = stringResource(R.string.domain_purchase_manage_auto_renew_note),
                    color = colors.text_tertiary,
                    fontSize = 13.sp,
                    lineHeight = 18.sp,
                )
                Spacer(Modifier.height(AsterSpacing.sm))
                Text(
                    text = stringResource(R.string.domain_purchase_manage_support_note),
                    color = colors.text_tertiary,
                    fontSize = 13.sp,
                    lineHeight = 18.sp,
                )
                if (renew_error != null) {
                    Spacer(Modifier.height(AsterSpacing.md))
                    error_banner(domain_purchase_error_text(renew_error))
                }
                Spacer(Modifier.height(AsterSpacing.md))
                AsterDialogOutlineButton(
                    label = stringResource(R.string.contact_support),
                    onClick = {
                        on_dismiss()
                        context.startActivity(
                            ComposeActivity.intent_for(context, prefill_to = support_address),
                        )
                    },
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        },
        footer = {
            AsterDialogOutlineButton(
                label = stringResource(R.string.close),
                onClick = on_dismiss,
            )
            AsterDialogPrimaryButton(
                label = stringResource(R.string.domain_purchase_renew),
                onClick = on_renew,
                enabled = !renewing,
                is_loading = renewing,
            )
        },
    )
}

@Composable
private fun manage_row(
    label: String,
    value: String,
    value_color: androidx.compose.ui.graphics.Color = AsterMaterial.colors.text_primary,
) {
    val colors = AsterMaterial.colors
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = label,
            color = colors.text_tertiary,
            fontSize = 14.sp,
            modifier = Modifier.weight(1f),
        )
        Text(
            text = value,
            color = value_color,
            fontSize = 14.sp,
            fontWeight = FontWeight.Medium,
        )
    }
}
