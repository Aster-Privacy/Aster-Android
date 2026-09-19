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
import compose.icons.tablericons.ShoppingCart

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import org.astermail.android.R
import org.astermail.android.api.domains.DomainOrder
import org.astermail.android.api.settings.CustomDomain
import org.astermail.android.api.settings.DnsRecord
import org.astermail.android.design.AsterMaterial
import org.astermail.android.design.AsterSpacing
import org.astermail.android.design.components.AsterAlertDialog
import org.astermail.android.design.components.AsterButton
import org.astermail.android.design.components.AsterCard
import org.astermail.android.design.components.AsterDivider
import org.astermail.android.design.components.DialogConfirmStyle
import org.astermail.android.settings.DomainPurchaseUiState
import org.astermail.android.settings.is_domain_order_in_flight

private fun format_expiry_date(iso: String): String {
    return try {
        val date_part = iso.substringBefore('T')
        val parsed = java.text.SimpleDateFormat("yyyy-MM-dd", java.util.Locale.US).parse(date_part)
        if (parsed != null) {
            java.text.DateFormat.getDateInstance(java.text.DateFormat.MEDIUM).format(parsed)
        } else {
            date_part
        }
    } catch (_: Throwable) {
        iso
    }
}

@Composable
internal fun domain_purchase_area(
    state: DomainPurchaseUiState,
    on_buy: () -> Unit,
    on_open_order: (DomainOrder) -> Unit,
    on_cancel: (String) -> Unit,
    on_complete_purchase: (DomainOrder) -> Unit,
    on_renew: (String) -> Unit,
    custom_domains: List<CustomDomain> = emptyList(),
    dns_records_for: (String) -> List<DnsRecord> = { emptyList() },
    verifying_domain_id: String? = null,
    verify_message_for: (String) -> String? = { null },
    catch_all_locked: Boolean = false,
    on_load_dns: (String) -> Unit = {},
    on_verify_domain: (String) -> Unit = {},
    on_toggle_catch_all: (String) -> Unit = {},
) {
    var manage_order_id by remember { mutableStateOf<String?>(null) }
    val manage_order = state.orders.firstOrNull { it.id == manage_order_id }
    purchased_domains_section(
        state = state,
        on_buy = on_buy,
        on_open_order = on_open_order,
        on_cancel = on_cancel,
        on_complete_purchase = on_complete_purchase,
        on_manage = { manage_order_id = it },
    )
    v_gap(AsterSpacing.md)
    if (manage_order != null) {
        val linked_domain = custom_domains.firstOrNull {
            it.domain_name.equals(manage_order.domain, ignoreCase = true)
        }
        domain_purchase_manage_dialog(
            order = manage_order,
            renewing = state.renewing_order_id == manage_order.id,
            renew_error = state.order_action_error,
            domain = linked_domain,
            dns_records = linked_domain?.let { dns_records_for(it.id) }.orEmpty(),
            verifying = linked_domain != null && verifying_domain_id == linked_domain.id,
            verify_message = linked_domain?.let { verify_message_for(it.id) },
            catch_all_locked = catch_all_locked,
            on_load_dns = { linked_domain?.let { on_load_dns(it.id) } },
            on_verify = { linked_domain?.let { on_verify_domain(it.id) } },
            on_toggle_catch_all = { linked_domain?.let { on_toggle_catch_all(it.id) } },
            on_renew = { on_renew(manage_order.id) },
            on_dismiss = { manage_order_id = null },
        )
    }
}

@Composable
private fun purchased_empty_box() {
    val colors = AsterMaterial.colors
    val dash_color = colors.border_secondary
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .drawBehind {
                val stroke_px = 1.dp.toPx()
                drawRoundRect(
                    color = dash_color,
                    cornerRadius = CornerRadius(12.dp.toPx()),
                    style = Stroke(
                        width = stroke_px,
                        pathEffect = PathEffect.dashPathEffect(floatArrayOf(6.dp.toPx(), 4.dp.toPx())),
                    ),
                )
            }
            .padding(horizontal = AsterSpacing.lg, vertical = AsterSpacing.xl),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = stringResource(R.string.domain_purchase_purchased_empty),
            color = colors.text_muted,
            fontSize = 14.sp,
            textAlign = TextAlign.Center,
        )
    }
}

@Composable
private fun purchased_domains_section(
    state: DomainPurchaseUiState,
    on_buy: () -> Unit,
    on_open_order: (DomainOrder) -> Unit,
    on_cancel: (String) -> Unit,
    on_complete_purchase: (DomainOrder) -> Unit,
    on_manage: (String) -> Unit,
) {
    val colors = AsterMaterial.colors
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            imageVector = TablerIcons.ShoppingCart,
            contentDescription = null,
            tint = colors.text_primary,
            modifier = Modifier.size(20.dp),
        )
        Spacer(Modifier.width(AsterSpacing.sm))
        Text(
            text = stringResource(R.string.domain_purchase_purchased_label),
            color = colors.text_primary,
            fontSize = 16.sp,
            fontWeight = FontWeight.SemiBold,
            modifier = Modifier.weight(1f, fill = false),
        )
        if (state.orders.isNotEmpty()) {
            Spacer(Modifier.width(AsterSpacing.sm))
            Text(
                text = java.text.NumberFormat.getIntegerInstance().format(state.orders.size),
                color = colors.text_muted,
                fontSize = 14.sp,
            )
        }
    }
    v_gap(AsterSpacing.xs)
    Text(
        text = stringResource(R.string.domain_purchase_purchased_desc),
        color = colors.text_muted,
        fontSize = 14.sp,
        lineHeight = 21.sp,
    )
    v_gap(AsterSpacing.md)
    AsterButton(
        label = stringResource(R.string.domain_purchase_buy_new),
        onClick = on_buy,
        modifier = Modifier.fillMaxWidth(),
    )
    v_gap(AsterSpacing.md)
    if (state.orders.isEmpty()) {
        purchased_empty_box()
    } else {
        AsterCard(modifier = Modifier.fillMaxWidth()) {
            state.orders.forEachIndexed { i, order ->
                if (i > 0) AsterDivider()
                purchased_domain_row(
                    order = order,
                    state = state,
                    on_open_order = on_open_order,
                    on_cancel = on_cancel,
                    on_complete_purchase = on_complete_purchase,
                    on_manage = on_manage,
                )
            }
        }
    }
    state.order_action_error?.let {
        v_gap(AsterSpacing.sm)
        error_banner(domain_purchase_error_text(it))
    }
}

@Composable
private fun purchased_domain_row(
    order: DomainOrder,
    state: DomainPurchaseUiState,
    on_open_order: (DomainOrder) -> Unit,
    on_cancel: (String) -> Unit,
    on_complete_purchase: (DomainOrder) -> Unit,
    on_manage: (String) -> Unit,
) {
    val colors = AsterMaterial.colors
    val in_flight = is_domain_order_in_flight(order.status)
    val pending = order.status == "pending_payment"
    var show_cancel_confirm by remember { mutableStateOf(false) }
    if (show_cancel_confirm) {
        AsterAlertDialog(
            on_dismiss = { show_cancel_confirm = false },
            title = stringResource(R.string.domain_purchase_cancel_payment_title),
            message = stringResource(R.string.domain_purchase_cancel_payment_message),
            confirm_label = stringResource(R.string.domain_purchase_cancel_payment_confirm),
            cancel_label = stringResource(R.string.domain_purchase_cancel_payment_keep),
            confirm_style = DialogConfirmStyle.destructive,
            on_confirm = {
                show_cancel_confirm = false
                on_cancel(order.id)
            },
        )
    }
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .then(
                when {
                    in_flight -> Modifier.clickable { on_open_order(order) }
                    order.status == "complete" -> Modifier.clickable { on_manage(order.id) }
                    else -> Modifier
                },
            )
            .padding(horizontal = AsterSpacing.md, vertical = 14.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = order.domain,
                    color = colors.text_primary,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Medium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                val status_text = when {
                    pending -> stringResource(R.string.domain_purchase_awaiting_payment)
                    in_flight -> stringResource(R.string.domain_purchase_purchased_in_progress)
                    order.status == "complete" -> order.expires_at?.let {
                        stringResource(R.string.domain_purchase_purchased_expires, format_expiry_date(it))
                    }
                    order.status == "lapsed" -> stringResource(R.string.domain_purchase_purchased_lapsed)
                    else -> null
                }
                if (!status_text.isNullOrEmpty()) {
                    Text(
                        text = status_text,
                        color = if (order.status == "lapsed") colors.danger else colors.text_muted,
                        fontSize = 13.sp,
                    )
                }
            }
            if (order.status == "complete") {
                Spacer(Modifier.width(AsterSpacing.sm))
                domain_pill_button(
                    label = stringResource(R.string.domain_purchase_manage),
                    on_click = { on_manage(order.id) },
                    enabled = state.renewing_order_id == null || state.renewing_order_id == order.id,
                    is_loading = state.renewing_order_id == order.id,
                )
            }
        }
        if (pending) {
            v_gap(AsterSpacing.sm)
            Row(
                horizontalArrangement = Arrangement.spacedBy(AsterSpacing.sm),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                domain_pill_button(
                    label = stringResource(R.string.domain_purchase_complete_purchase),
                    on_click = { on_complete_purchase(order) },
                    filled = true,
                    enabled = !state.buying,
                    is_loading = state.buying,
                )
                domain_pill_button(
                    label = stringResource(R.string.cancel),
                    on_click = { show_cancel_confirm = true },
                    tint = colors.danger,
                    enabled = state.cancelling_order_id == null,
                    is_loading = state.cancelling_order_id == order.id,
                )
            }
        }
    }
}
