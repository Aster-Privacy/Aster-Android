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
import compose.icons.tablericons.Check
import compose.icons.tablericons.Clock

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
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
import org.astermail.android.api.settings.CustomDomain
import org.astermail.android.api.settings.DnsRecord
import org.astermail.android.design.AsterMaterial
import org.astermail.android.design.AsterSpacing
import org.astermail.android.design.components.AsterDialog
import org.astermail.android.design.components.AsterDialogOutlineButton
import org.astermail.android.design.components.AsterDialogPrimaryButton
import org.astermail.android.design.components.AsterDivider
import org.astermail.android.design.components.AsterSwitch
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
    domain: CustomDomain? = null,
    dns_records: List<DnsRecord> = emptyList(),
    verifying: Boolean = false,
    verify_message: String? = null,
    catch_all_locked: Boolean = false,
    on_load_dns: () -> Unit = {},
    on_verify: () -> Unit = {},
    on_toggle_catch_all: () -> Unit = {},
) {
    val colors = AsterMaterial.colors
    val context = LocalContext.current
    val remaining = order.expires_at?.let { days_until(it) }
    val lapsed = order.status == "lapsed" || (remaining != null && remaining < 0)
    val expiring_soon = !lapsed && remaining != null && remaining <= expiring_soon_days

    LaunchedEffect(domain?.id) {
        if (domain != null && dns_records.isEmpty()) on_load_dns()
    }

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
                AsterDivider()
                Spacer(Modifier.height(AsterSpacing.md))
                Text(
                    text = stringResource(R.string.domain_manage_mail_setup),
                    color = colors.text_secondary,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.SemiBold,
                )

                if (domain == null) {
                    Spacer(Modifier.height(AsterSpacing.sm))
                    Text(
                        text = stringResource(R.string.domain_manage_dns_unavailable),
                        color = colors.text_tertiary,
                        fontSize = 13.sp,
                        lineHeight = 18.sp,
                    )
                } else {
                    manage_domain_mail_setup(
                        domain = domain,
                        dns_records = dns_records,
                        verifying = verifying,
                        verify_message = verify_message,
                        catch_all_locked = catch_all_locked,
                        on_verify = on_verify,
                        on_toggle_catch_all = on_toggle_catch_all,
                    )
                }

                Spacer(Modifier.height(AsterSpacing.md))
                AsterDivider()
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
private fun manage_domain_mail_setup(
    domain: CustomDomain,
    dns_records: List<DnsRecord>,
    verifying: Boolean,
    verify_message: String?,
    catch_all_locked: Boolean,
    on_verify: () -> Unit,
    on_toggle_catch_all: () -> Unit,
) {
    val colors = AsterMaterial.colors
    val record_states = listOf(
        "TXT" to domain.txt_verified,
        "MX" to domain.mx_verified,
        "SPF" to domain.spf_verified,
        "DKIM" to domain.dkim_verified,
        "DMARC" to domain.dmarc_configured,
    )
    val verified_count = record_states.count { it.second }
    val all_verified = verified_count == record_states.size

    manage_row(
        label = stringResource(R.string.domain_manage_verification),
        value = manage_domain_status_label(domain),
        value_color = when {
            domain.status.equals("suspended", ignoreCase = true) ||
                domain.status.equals("failed", ignoreCase = true) -> colors.danger
            all_verified || domain.status.equals("active", ignoreCase = true) -> colors.success
            else -> colors.warning
        },
    )
    manage_row(
        label = stringResource(R.string.domain_manage_last_verified),
        value = domain.verified_at?.let { format_day(it) }
            ?: stringResource(R.string.domain_manage_never_verified),
    )
    manage_row(
        label = stringResource(R.string.domain_manage_records),
        value = stringResource(
            R.string.domain_records_verified,
            verified_count,
            record_states.size,
        ),
    )

    Spacer(Modifier.height(AsterSpacing.xs))
    record_states.forEach { (label, verified) ->
        manage_dns_status_row(
            label = label,
            verified = verified,
            detail = dns_records.firstOrNull { it.type.equals(label, ignoreCase = true) }?.name,
        )
    }

    Spacer(Modifier.height(AsterSpacing.sm))
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(1f).padding(end = AsterSpacing.md)) {
            Text(
                text = stringResource(R.string.catch_all),
                color = colors.text_primary.copy(alpha = if (catch_all_locked) 0.4f else 1f),
                fontSize = 14.sp,
                fontWeight = FontWeight.Medium,
            )
            Text(
                text = stringResource(R.string.domain_manage_catch_all_description),
                color = colors.text_tertiary,
                fontSize = 12.sp,
                lineHeight = 16.sp,
            )
        }
        AsterSwitch(
            checked = domain.catch_all_enabled && !catch_all_locked,
            onCheckedChange = { if (!catch_all_locked) on_toggle_catch_all() },
            enabled = !catch_all_locked,
        )
    }

    if (!verify_message.isNullOrBlank()) {
        Spacer(Modifier.height(AsterSpacing.sm))
        Text(
            text = verify_message,
            color = colors.text_tertiary,
            fontSize = 12.sp,
            lineHeight = 16.sp,
        )
    }

    Spacer(Modifier.height(AsterSpacing.md))
    AsterDialogOutlineButton(
        label = if (verifying) {
            stringResource(R.string.domain_manage_rechecking)
        } else {
            stringResource(R.string.domain_manage_recheck)
        },
        onClick = on_verify,
        enabled = !verifying,
        modifier = Modifier.fillMaxWidth(),
    )
}

@Composable
private fun manage_domain_status_label(domain: CustomDomain): String {
    val all_core = domain.txt_verified && domain.mx_verified &&
        domain.spf_verified && domain.dkim_verified
    return when (domain.status.lowercase()) {
        "active" -> stringResource(R.string.domain_status_active)
        "verifying" -> stringResource(R.string.domain_status_verifying)
        "dns_pending" -> stringResource(R.string.domain_status_dns_pending)
        "suspended" -> stringResource(R.string.domain_status_suspended)
        "failed" -> stringResource(R.string.domain_status_failed)
        else -> stringResource(
            if (all_core) R.string.domain_status_active else R.string.domain_status_setup_required,
        )
    }
}

@Composable
private fun manage_dns_status_row(label: String, verified: Boolean, detail: String?) {
    val colors = AsterMaterial.colors
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 3.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            imageVector = if (verified) TablerIcons.Check else TablerIcons.Clock,
            contentDescription = stringResource(
                if (verified) R.string.domain_record_found else R.string.domain_record_pending,
            ),
            tint = if (verified) colors.success else colors.text_muted,
            modifier = Modifier.size(16.dp),
        )
        Spacer(Modifier.width(AsterSpacing.sm))
        Text(
            text = label,
            color = if (verified) colors.text_primary else colors.text_muted,
            fontSize = 13.sp,
            fontWeight = FontWeight.Medium,
        )
        if (!detail.isNullOrBlank()) {
            Spacer(Modifier.width(AsterSpacing.sm))
            Text(
                text = detail,
                color = colors.text_tertiary,
                fontSize = 12.sp,
                maxLines = 1,
                overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f),
            )
        } else {
            Spacer(Modifier.weight(1f))
        }
        Text(
            text = stringResource(
                if (verified) R.string.domain_record_found else R.string.domain_record_pending,
            ),
            color = if (verified) colors.success else colors.text_muted,
            fontSize = 12.sp,
            fontWeight = FontWeight.Medium,
        )
    }
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
