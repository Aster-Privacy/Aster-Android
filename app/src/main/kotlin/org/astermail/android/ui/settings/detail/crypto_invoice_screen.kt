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

import android.content.ClipData
import org.astermail.android.ui.common.show_copy_failed_toast
import org.astermail.android.ui.common.write_to_clipboard
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.repeatOnLifecycle
import compose.icons.TablerIcons
import compose.icons.tablericons.*
import java.math.BigDecimal
import java.math.BigInteger
import java.time.Instant
import java.time.OffsetDateTime
import java.util.Locale
import kotlinx.coroutines.delay
import org.astermail.android.R
import org.astermail.android.api.billing.CryptoNativeInvoiceStatus
import org.astermail.android.billing.CryptoInvoiceLoadError
import org.astermail.android.billing.CryptoInvoiceViewModel
import org.astermail.android.design.AsterMaterial
import org.astermail.android.design.AsterSpacing
import org.astermail.android.design.components.AsterButton
import org.astermail.android.design.components.AsterCard
import org.astermail.android.design.components.AsterDivider
import org.astermail.android.design.components.AsterDialog
import org.astermail.android.design.components.AsterDialogDestructiveButton
import org.astermail.android.design.components.AsterDialogOutlineButton
import org.astermail.android.design.components.AsterGhostButton
import org.astermail.android.design.components.AsterSecondaryButton
import org.astermail.android.design.components.AsterTopBar
import org.astermail.android.twofactor.render_qr_code

private fun pretty_chain(chain: String): String {
    if (chain.isBlank()) return chain
    val lower = chain.lowercase(Locale.US)
    return when (lower) {
        "bitcoin" -> "Bitcoin"
        "base" -> "Base"
        "ethereum" -> "Ethereum"
        "polygon" -> "Polygon"
        "arbitrum" -> "Arbitrum"
        "optimism" -> "Optimism"
        "monero" -> "Monero"
        else -> lower.replaceFirstChar { it.uppercase(Locale.US) }
    }
}

private fun format_countdown(remaining_ms: Long, zero_label: String): String {
    if (remaining_ms <= 0) return zero_label
    val total_seconds = remaining_ms / 1000
    val hours = total_seconds / 3600
    val minutes = (total_seconds % 3600) / 60
    val seconds = total_seconds % 60
    return if (hours > 0) {
        String.format(Locale.US, "%d:%02d:%02d", hours, minutes, seconds)
    } else {
        String.format(Locale.US, "%d:%02d", minutes, seconds)
    }
}

private fun truncate_middle(value: String, head: Int = 10, tail: Int = 8): String {
    if (value.length <= head + tail + 1) return value
    return value.take(head) + "…" + value.takeLast(tail)
}

private fun parse_iso_millis(value: String): Long? {
    if (value.isBlank()) return null
    return runCatching { OffsetDateTime.parse(value).toInstant().toEpochMilli() }.getOrNull()
        ?: runCatching { Instant.parse(value).toEpochMilli() }.getOrNull()
}

private fun format_usd_amount(cents: Long): String {
    return String.format(Locale.getDefault(), "%,.2f", cents / 100.0)
}

private fun atomic_or_null(value: String): BigInteger? =
    runCatching { BigInteger(value.trim()) }.getOrNull()

private fun received_atomic_of(invoice: CryptoNativeInvoiceStatus): BigInteger =
    atomic_or_null(invoice.amount_received_atomic) ?: BigInteger.ZERO

private fun due_atomic_of(invoice: CryptoNativeInvoiceStatus): BigInteger? {
    atomic_or_null(invoice.amount_due_atomic)?.let { return it.max(BigInteger.ZERO) }
    val expected = atomic_or_null(invoice.amount_atomic) ?: return null
    return (expected - received_atomic_of(invoice)).max(BigInteger.ZERO)
}

private fun format_atomic_decimal(value: BigInteger, decimals: Int): String =
    BigDecimal(value)
        .movePointLeft(decimals.coerceAtLeast(0))
        .stripTrailingZeros()
        .toPlainString()

private fun due_decimal_of(invoice: CryptoNativeInvoiceStatus): String {
    if (invoice.amount_due_decimal.isNotBlank()) return invoice.amount_due_decimal
    val due = due_atomic_of(invoice) ?: return invoice.amount_decimal
    return format_atomic_decimal(due, invoice.decimals)
}

private fun is_awaiting_funds_status(status: String): Boolean =
    status == "pending" || status == "detected" || status == "underpaid"

private fun is_quote_lapsed(invoice: CryptoNativeInvoiceStatus, corrected_now_ms: Long): Boolean {
    if (!is_awaiting_funds_status(invoice.status)) return false
    val deadline_ms = parse_iso_millis(invoice.expires_at) ?: return false
    return deadline_ms - corrected_now_ms <= 0
}

@Composable
internal fun crypto_invoice_screen(
    invoice_id: String,
    on_back: () -> Unit,
    on_go_to_inbox: () -> Unit,
    on_view_billing: () -> Unit,
    vm: CryptoInvoiceViewModel = hiltViewModel(),
) {
    val colors = AsterMaterial.colors
    val state by vm.state.collectAsStateWithLifecycle()
    val lifecycle_owner = LocalLifecycleOwner.current
    LaunchedEffect(invoice_id, state.poll_token, lifecycle_owner) {
        lifecycle_owner.lifecycle.repeatOnLifecycle(Lifecycle.State.STARTED) {
            vm.poll(invoice_id)
        }
    }
    var now_ms by remember { mutableStateOf(System.currentTimeMillis()) }
    LaunchedEffect(Unit) {
        while (true) {
            now_ms = System.currentTimeMillis()
            delay(1000)
        }
    }
    var show_cancel_confirm by remember { mutableStateOf(false) }
    val invoice = state.invoice
    val corrected_now_ms = now_ms - state.clock_skew_ms
    val quote_lapsed_unfunded = invoice != null &&
        is_quote_lapsed(invoice, corrected_now_ms) &&
        received_atomic_of(invoice).signum() == 0

    Column(Modifier.fillMaxSize().background(colors.bg_primary).systemBarsPadding()) {
        AsterTopBar(title = stringResource(R.string.crypto_native_invoice_screen_title), on_back = on_back)
        AsterDivider()
        Column(
            modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(AsterSpacing.lg),
        ) {
            when {
                invoice == null && state.is_loading -> crypto_loading_view()
                invoice == null && state.load_error == CryptoInvoiceLoadError.unavailable -> crypto_outcome_view(
                    icon = TablerIcons.CloudOff,
                    tint = colors.text_secondary,
                    title = stringResource(R.string.crypto_native_unavailable),
                    body = stringResource(R.string.crypto_native_unavailable_body),
                    primary_label = stringResource(R.string.retry),
                    on_primary = { vm.refresh() },
                    secondary_label = stringResource(R.string.crypto_native_view_billing),
                    on_secondary = on_view_billing,
                )
                invoice == null -> crypto_outcome_view(
                    icon = TablerIcons.AlertTriangle,
                    tint = colors.warning,
                    title = stringResource(R.string.crypto_native_not_found),
                    body = stringResource(R.string.crypto_native_not_found_body),
                    primary_label = null,
                    on_primary = null,
                    secondary_label = stringResource(R.string.crypto_native_view_billing),
                    on_secondary = on_view_billing,
                )
                invoice.status == "paid" -> crypto_outcome_view(
                    icon = TablerIcons.CircleCheck,
                    tint = colors.success,
                    title = stringResource(R.string.crypto_native_paid_title),
                    body = stringResource(R.string.crypto_native_paid_body),
                    primary_label = stringResource(R.string.crypto_native_go_to_inbox),
                    on_primary = on_go_to_inbox,
                    secondary_label = stringResource(R.string.crypto_native_view_billing),
                    on_secondary = on_view_billing,
                )
                invoice.status == "cancelled" -> crypto_outcome_view(
                    icon = TablerIcons.X,
                    tint = colors.text_secondary,
                    title = stringResource(R.string.crypto_native_cancelled_title),
                    body = stringResource(R.string.crypto_native_cancelled_body),
                    primary_label = null,
                    on_primary = null,
                    secondary_label = stringResource(R.string.crypto_native_view_billing),
                    on_secondary = on_view_billing,
                )
                invoice.status == "expired" || quote_lapsed_unfunded -> crypto_outcome_view(
                    icon = TablerIcons.Clock,
                    tint = colors.text_secondary,
                    title = stringResource(R.string.crypto_native_expired_title),
                    body = stringResource(R.string.crypto_native_expired_body),
                    notice = stringResource(R.string.crypto_native_expired_do_not_send),
                    primary_label = null,
                    on_primary = null,
                    secondary_label = stringResource(R.string.crypto_native_view_billing),
                    on_secondary = on_view_billing,
                )
                else -> crypto_active_view(
                    invoice = invoice,
                    corrected_now_ms = corrected_now_ms,
                    is_cancelling = state.is_cancelling,
                    cancel_error = state.cancel_error,
                    is_connection_lost = state.is_connection_lost,
                    on_retry = { vm.refresh() },
                    on_cancel = { show_cancel_confirm = true },
                    on_view_billing = on_view_billing,
                )
            }
        }
    }
    if (show_cancel_confirm) {
        AsterDialog(
            on_dismiss = { show_cancel_confirm = false },
            title = stringResource(R.string.crypto_native_cancel_confirm_title),
            message = stringResource(R.string.crypto_native_cancel_confirm_body),
            footer = {
                AsterDialogOutlineButton(
                    label = stringResource(R.string.cancel),
                    onClick = { show_cancel_confirm = false },
                    modifier = Modifier.weight(0.7f),
                )
                AsterDialogDestructiveButton(
                    label = stringResource(R.string.crypto_native_cancel_invoice),
                    onClick = {
                        show_cancel_confirm = false
                        vm.cancel()
                    },
                    modifier = Modifier.weight(1.3f),
                )
            },
        )
    }
}
@Composable
private fun crypto_active_view(
    invoice: CryptoNativeInvoiceStatus,
    corrected_now_ms: Long,
    is_cancelling: Boolean,
    cancel_error: String?,
    is_connection_lost: Boolean,
    on_retry: () -> Unit,
    on_cancel: () -> Unit,
    on_view_billing: () -> Unit,
) {
    val colors = AsterMaterial.colors
    val is_awaiting_funds = is_awaiting_funds_status(invoice.status)
    val deadline_ms = parse_iso_millis(invoice.expires_at)
    val remaining_ms = deadline_ms?.minus(corrected_now_ms)
    val quote_lapsed = is_awaiting_funds && remaining_ms != null && remaining_ms <= 0
    val due_atomic = due_atomic_of(invoice)
    val has_outstanding_balance =
        is_awaiting_funds && !quote_lapsed && (due_atomic == null || due_atomic.signum() > 0)
    val has_received_funds = received_atomic_of(invoice).signum() > 0
    val is_active_payment = is_awaiting_funds && !quote_lapsed
    val due_decimal = due_decimal_of(invoice)

    Column(Modifier.fillMaxWidth()) {
        AsterCard(modifier = Modifier.fillMaxWidth()) {
            Column(Modifier.fillMaxWidth().padding(AsterSpacing.lg)) {
                Text(
                    if (has_outstanding_balance) {
                        stringResource(R.string.crypto_native_invoice_title, invoice.display_name)
                    } else {
                        stringResource(R.string.crypto_native_received_title)
                    },
                    fontSize = 18.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = colors.text_primary,
                )
                Spacer(Modifier.height(AsterSpacing.xs))
                Text(
                    if (has_outstanding_balance) {
                        stringResource(R.string.crypto_native_awaiting_body)
                    } else {
                        stringResource(R.string.crypto_native_received_body)
                    },
                    fontSize = 13.sp,
                    color = colors.text_secondary,
                    lineHeight = 18.sp,
                )
                if (has_outstanding_balance) {
                    Spacer(Modifier.height(AsterSpacing.lg))
                    crypto_qr_box(invoice.payment_uri)
                    Spacer(Modifier.height(AsterSpacing.lg))
                    crypto_detail_row(
                        if (invoice.status == "underpaid") {
                            stringResource(R.string.crypto_native_send_remaining)
                        } else {
                            stringResource(R.string.crypto_native_send_exactly)
                        },
                        due_decimal + " " + invoice.currency,
                    )
                    Spacer(Modifier.height(AsterSpacing.md))
                    crypto_detail_row(stringResource(R.string.crypto_native_to_address), invoice.address, monospace = true)
                    Spacer(Modifier.height(AsterSpacing.lg))
                    crypto_wallet_button(invoice.payment_uri)
                }
                if (has_outstanding_balance || quote_lapsed) {
                    Spacer(Modifier.height(if (has_outstanding_balance) AsterSpacing.md else AsterSpacing.lg))
                    crypto_notice_box(
                        TablerIcons.AlertTriangle,
                        colors.warning,
                        null,
                        if (quote_lapsed) {
                            stringResource(R.string.crypto_native_expired_do_not_send)
                        } else {
                            stringResource(R.string.crypto_native_network_warning, invoice.currency, pretty_chain(invoice.chain))
                        },
                    )
                }
            }
        }

        Spacer(Modifier.height(AsterSpacing.lg))

        AsterCard(modifier = Modifier.fillMaxWidth()) {
            Column(Modifier.fillMaxWidth().padding(AsterSpacing.lg)) {
                Text(
                    if (has_received_funds) {
                        stringResource(R.string.crypto_native_usd_total_label)
                    } else {
                        stringResource(R.string.crypto_native_usd_value_label)
                    },
                    fontSize = 12.sp,
                    color = colors.text_tertiary,
                )
                Spacer(Modifier.height(AsterSpacing.xs))
                Text(
                    stringResource(R.string.crypto_native_usd_amount, format_usd_amount(invoice.usd_cents)),
                    fontSize = 24.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = colors.text_primary,
                )
                Spacer(Modifier.height(AsterSpacing.xs))
                Text(stringResource(R.string.crypto_native_rate_locked), fontSize = 11.sp, color = colors.text_muted, lineHeight = 16.sp)
                if (is_active_payment && remaining_ms != null) {
                    Spacer(Modifier.height(AsterSpacing.md))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(TablerIcons.Clock, contentDescription = null, tint = colors.text_secondary, modifier = Modifier.size(14.dp))
                        Spacer(Modifier.width(AsterSpacing.xs))
                        Text(
                            stringResource(
                                R.string.crypto_native_expires_in,
                                format_countdown(remaining_ms, stringResource(R.string.crypto_native_countdown_zero)),
                            ),
                            fontSize = 12.sp,
                            color = colors.text_secondary,
                        )
                    }
                }
            }
        }

        if (is_connection_lost) {
            Spacer(Modifier.height(AsterSpacing.lg))
            crypto_notice_box(TablerIcons.CloudOff, colors.text_secondary, null, stringResource(R.string.crypto_native_connection_lost))
            Spacer(Modifier.height(AsterSpacing.md))
            AsterSecondaryButton(label = stringResource(R.string.retry), onClick = on_retry, modifier = Modifier.fillMaxWidth())
        }

        Spacer(Modifier.height(AsterSpacing.lg))

        AsterCard(modifier = Modifier.fillMaxWidth()) {
            Column(Modifier.fillMaxWidth().padding(AsterSpacing.lg)) {
                crypto_progress_stepper(invoice.status, invoice.confirmations, invoice.min_confirmations)
            }
        }

        if (invoice.status == "underpaid") {
            Spacer(Modifier.height(AsterSpacing.lg))
            crypto_notice_box(
                TablerIcons.AlertTriangle,
                colors.warning,
                null,
                stringResource(
                    R.string.crypto_native_underpaid_body,
                    invoice.amount_received_decimal,
                    invoice.amount_decimal,
                    invoice.currency,
                    due_decimal,
                ),
            )
        }

        if (invoice.status == "manual_review") {
            Spacer(Modifier.height(AsterSpacing.lg))
            crypto_notice_box(TablerIcons.InfoCircle, colors.info, stringResource(R.string.crypto_native_manual_review), stringResource(R.string.crypto_native_manual_review_body))
        }

        if (invoice.txids.isNotEmpty()) {
            Spacer(Modifier.height(AsterSpacing.lg))
            AsterCard(modifier = Modifier.fillMaxWidth()) {
                Column(Modifier.fillMaxWidth().padding(AsterSpacing.lg)) {
                    Text(stringResource(R.string.crypto_native_transaction), fontSize = 12.sp, color = colors.text_tertiary)
                    invoice.txids.forEach { entry ->
                        Spacer(Modifier.height(AsterSpacing.xs))
                        Text(truncate_middle(entry), fontSize = 13.sp, fontFamily = FontFamily.Monospace, color = colors.text_secondary)
                    }
                }
            }
        }

        Spacer(Modifier.height(AsterSpacing.lg))
        Text(stringResource(R.string.crypto_native_refund_notice), fontSize = 11.sp, color = colors.text_muted, lineHeight = 16.sp)

        if (cancel_error != null) {
            Spacer(Modifier.height(AsterSpacing.md))
            Text(cancel_error, fontSize = 12.sp, color = colors.danger)
        }

        if (invoice.status == "pending") {
            Spacer(Modifier.height(AsterSpacing.lg))
            AsterGhostButton(label = stringResource(R.string.crypto_native_cancel_invoice), onClick = on_cancel, modifier = Modifier.fillMaxWidth(), is_loading = is_cancelling)
        }

        Spacer(Modifier.height(AsterSpacing.md))
        AsterSecondaryButton(label = stringResource(R.string.crypto_native_view_billing), onClick = on_view_billing, modifier = Modifier.fillMaxWidth())
        Spacer(Modifier.height(AsterSpacing.xxl))
    }
}
@Composable
private fun crypto_loading_view() {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(240.dp),
        contentAlignment = Alignment.Center,
    ) {
        CircularProgressIndicator(
            modifier = Modifier.size(28.dp),
            color = AsterMaterial.colors.accent_blue,
            strokeWidth = 2.dp,
        )
    }
}

@Composable
private fun crypto_outcome_view(
    icon: ImageVector,
    tint: Color,
    title: String,
    body: String,
    primary_label: String?,
    on_primary: (() -> Unit)?,
    secondary_label: String,
    on_secondary: () -> Unit,
    notice: String? = null,
) {
    val colors = AsterMaterial.colors
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = AsterSpacing.xxxl),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = tint,
            modifier = Modifier.size(48.dp),
        )
        Spacer(Modifier.height(AsterSpacing.lg))
        Text(
            title,
            fontSize = 20.sp,
            fontWeight = FontWeight.SemiBold,
            color = colors.text_primary,
            textAlign = TextAlign.Center,
        )
        Spacer(Modifier.height(AsterSpacing.sm))
        Text(
            body,
            fontSize = 14.sp,
            color = colors.text_secondary,
            textAlign = TextAlign.Center,
            lineHeight = 20.sp,
        )
        if (!notice.isNullOrBlank()) {
            Spacer(Modifier.height(AsterSpacing.lg))
            crypto_notice_box(TablerIcons.AlertTriangle, colors.warning, null, notice)
        }
        Spacer(Modifier.height(AsterSpacing.xxl))
        if (primary_label != null && on_primary != null) {
            AsterButton(
                label = primary_label,
                onClick = on_primary,
                modifier = Modifier.fillMaxWidth(),
            )
            Spacer(Modifier.height(AsterSpacing.md))
        }
        AsterSecondaryButton(
            label = secondary_label,
            onClick = on_secondary,
            modifier = Modifier.fillMaxWidth(),
        )
    }
}
@Composable
private fun crypto_progress_stepper(status: String, confirmations: Int, min_confirmations: Int) {
    val colors = AsterMaterial.colors
    val labels = listOf(
        stringResource(R.string.crypto_native_status_awaiting),
        stringResource(R.string.crypto_native_status_detected),
        stringResource(R.string.crypto_native_status_confirming_short),
        stringResource(R.string.crypto_native_status_credited)
    )
    val active_index = when (status) {
        "detected" -> 1
        "confirming" -> 2
        "paid" -> 3
        "underpaid" -> 1
        "manual_review" -> 1
        else -> 0
    }
    Column(modifier = Modifier.fillMaxWidth()) {
        labels.forEachIndexed { index, label ->
            val is_done = index < active_index
            val is_current = index == active_index
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    modifier = Modifier.size(24.dp),
                    contentAlignment = Alignment.Center
                ) {
                    if (is_done) {
                        Icon(
                            TablerIcons.Check,
                            contentDescription = null,
                            tint = colors.success,
                            modifier = Modifier.size(20.dp)
                        )
                    } else if (is_current) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(16.dp),
                            color = colors.accent_blue,
                            strokeWidth = 2.dp
                        )
                    } else {
                        Box(
                            modifier = Modifier
                                .size(20.dp)
                                .clip(CircleShape)
                                .border(1.dp, colors.border_secondary, CircleShape),
                            contentAlignment = Alignment.Center
                        ) {
                            Text("${index + 1}", fontSize = 11.sp, color = colors.text_muted)
                        }
                    }
                }
                Spacer(Modifier.width(AsterSpacing.md))
                Column {
                    Text(
                        label,
                        fontSize = 14.sp,
                        color = if (index <= active_index) colors.text_primary else colors.text_tertiary,
                        fontWeight = if (is_current) FontWeight.Medium else FontWeight.Normal
                    )
                    if (index == 2 && is_current && status == "confirming") {
                        Spacer(Modifier.height(AsterSpacing.xs))
                        Text(
                            stringResource(R.string.crypto_native_status_confirming, confirmations, min_confirmations),
                            fontSize = 12.sp,
                            color = colors.text_secondary
                        )
                    }
                }
            }
            if (index != labels.lastIndex) {
                Spacer(Modifier.height(AsterSpacing.md))
            }
        }
    }
}

@Composable
private fun crypto_notice_box(icon: ImageVector, tint: Color, title: String?, body: String) {
    val colors = AsterMaterial.colors
    val shape = RoundedCornerShape(12.dp)
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(shape)
            .background(tint.copy(alpha = 0.08f))
            .border(1.dp, tint.copy(alpha = 0.25f), shape)
            .padding(AsterSpacing.md),
        verticalAlignment = Alignment.Top
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = tint,
            modifier = Modifier.size(16.dp)
        )
        Spacer(Modifier.width(AsterSpacing.sm))
        Column(modifier = Modifier.fillMaxWidth()) {
            if (!title.isNullOrBlank()) {
                Text(title, fontSize = 13.sp, fontWeight = FontWeight.Medium, color = colors.text_primary)
                Spacer(Modifier.height(2.dp))
            }
            Text(body, fontSize = 12.sp, color = colors.text_secondary, lineHeight = 18.sp)
        }
    }
}
@Composable
private fun crypto_detail_row(label: String, value: String, monospace: Boolean = false) {
    val colors = AsterMaterial.colors
    val context = LocalContext.current
    val copied_message = stringResource(R.string.crypto_native_copied)
    Column(modifier = Modifier.fillMaxWidth()) {
        Text(label, fontSize = 12.sp, color = colors.text_tertiary)
        Spacer(Modifier.height(AsterSpacing.xs))
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = value,
                modifier = Modifier.weight(1f),
                fontSize = 14.sp,
                fontFamily = if (monospace) FontFamily.Monospace else null,
                color = colors.text_primary,
                lineHeight = 20.sp,
            )
            Spacer(Modifier.width(AsterSpacing.sm))
            Box(
                modifier = Modifier
                    .size(32.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .clickable {
                        if (write_to_clipboard(context, ClipData.newPlainText(label, value))) {
                            Toast.makeText(context, copied_message, Toast.LENGTH_SHORT).show()
                        } else {
                            show_copy_failed_toast(context)
                        }
                    },
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    TablerIcons.Copy,
                    contentDescription = stringResource(R.string.crypto_native_copy_action),
                    tint = colors.text_secondary,
                    modifier = Modifier.size(16.dp),
                )
            }
        }
    }
}

@Composable
private fun crypto_qr_box(content: String) {
    val image = remember(content) { render_qr_code(content, 640) }
    if (image != null) {
        Box(
            modifier = Modifier.fillMaxWidth(),
            contentAlignment = Alignment.Center,
        ) {
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(16.dp))
                    .background(Color.White)
                    .padding(AsterSpacing.sm),
            ) {
                Image(
                    bitmap = image.asImageBitmap(),
                    contentDescription = null,
                    modifier = Modifier.size(208.dp),
                )
            }
        }
    }
}

@Composable
private fun crypto_wallet_button(target_uri: String) {
    val context = LocalContext.current
    val missing_app_message = stringResource(R.string.crypto_native_no_wallet_app)
    AsterSecondaryButton(
        label = stringResource(R.string.crypto_native_open_wallet),
        onClick = {
            try {
                context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(target_uri)))
            } catch (_: Throwable) {
                Toast.makeText(context, missing_app_message, Toast.LENGTH_SHORT).show()
            }
        },
        modifier = Modifier.fillMaxWidth(),
    )
}
