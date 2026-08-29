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
import compose.icons.tablericons.ChevronRight
import compose.icons.tablericons.World

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import org.astermail.android.R
import org.astermail.android.api.domains.DomainOrder
import org.astermail.android.api.settings.CustomDomain
import org.astermail.android.api.settings.DnsRecord
import org.astermail.android.design.AsterMaterial
import org.astermail.android.design.AsterSpacing
import org.astermail.android.design.components.AsterCard
import org.astermail.android.design.components.AsterDivider
import org.astermail.android.settings.DomainPurchaseUiState
import org.astermail.android.settings.is_domain_order_in_flight
import org.astermail.android.design.mirror_in_rtl

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
    val has_complete = state.orders.any { it.status == "complete" }
    var manage_order_id by remember { mutableStateOf<String?>(null) }
    val manage_order = state.orders.firstOrNull { it.id == manage_order_id }
    if (!has_complete) {
        domain_purchase_promo(on_buy = on_buy)
        v_gap(AsterSpacing.md)
    }
    if (state.orders.isNotEmpty()) {
        purchased_domains_section(
            state = state,
            show_buy_action = has_complete,
            on_buy = on_buy,
            on_open_order = on_open_order,
            on_cancel = on_cancel,
            on_complete_purchase = on_complete_purchase,
            on_manage = { manage_order_id = it },
        )
        v_gap(AsterSpacing.md)
    }
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
private fun domain_purchase_promo(on_buy: () -> Unit) {
    val colors = AsterMaterial.colors
    AsterCard(modifier = Modifier.fillMaxWidth(), onClick = on_buy) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(AsterSpacing.md),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                imageVector = TablerIcons.World,
                contentDescription = null,
                tint = colors.accent_blue,
                modifier = Modifier.size(24.dp),
            )
            Spacer(Modifier.width(AsterSpacing.md))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = stringResource(R.string.domain_purchase_banner_title),
                    color = colors.text_primary,
                    fontSize = 15.sp,
                    fontWeight = FontWeight.SemiBold,
                )
                Text(
                    text = stringResource(R.string.domain_purchase_banner_subtitle),
                    color = colors.text_tertiary,
                    fontSize = 13.sp,
                )
                v_gap(AsterSpacing.xs)
                Text(
                    text = stringResource(R.string.domain_purchase_banner_cta),
                    color = colors.accent_blue,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Medium,
                )
            }
            Icon(
                imageVector = TablerIcons.ChevronRight,
                contentDescription = null,
                tint = colors.text_tertiary,
                modifier = Modifier.size(20.dp).mirror_in_rtl(),
            )
        }
    }
}

@Composable
private fun purchased_domains_section(
    state: DomainPurchaseUiState,
    show_buy_action: Boolean,
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
        Text(
            text = stringResource(R.string.domain_purchase_purchased_label).uppercase(),
            color = colors.text_tertiary,
            fontSize = 11.sp,
            fontWeight = FontWeight.SemiBold,
            modifier = Modifier.weight(1f),
        )
        if (show_buy_action) {
            TextButton(onClick = on_buy) {
                Text(
                    text = stringResource(R.string.domain_purchase_banner_cta),
                    color = colors.accent_blue,
                    fontSize = 13.sp,
                )
            }
        }
    }
    v_gap(AsterSpacing.xs)
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
            .padding(horizontal = AsterSpacing.md, vertical = AsterSpacing.sm),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = order.domain,
                    color = colors.text_primary,
                    fontSize = 15.sp,
                    fontWeight = FontWeight.Medium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                when {
                    order.status == "pending_payment" -> Text(
                        text = stringResource(R.string.domain_purchase_awaiting_payment),
                        color = colors.text_tertiary,
                        fontSize = 13.sp,
                    )
                    in_flight -> Text(
                        text = stringResource(R.string.domain_purchase_purchased_in_progress),
                        color = colors.text_tertiary,
                        fontSize = 13.sp,
                    )
                    order.status == "complete" -> Text(
                        text = order.expires_at?.let {
                            stringResource(R.string.domain_purchase_purchased_expires, format_expiry_date(it))
                        } ?: "",
                        color = colors.text_tertiary,
                        fontSize = 13.sp,
                    )
                    order.status == "lapsed" -> Text(
                        text = stringResource(R.string.domain_purchase_purchased_lapsed),
                        color = colors.danger,
                        fontSize = 13.sp,
                    )
                }
            }
            when {
                in_flight -> Icon(
                    imageVector = TablerIcons.ChevronRight,
                    contentDescription = null,
                    tint = colors.text_tertiary,
                    modifier = Modifier.size(20.dp).mirror_in_rtl(),
                )
                order.status == "complete" -> {
                    if (state.renewing_order_id == order.id) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(16.dp),
                            strokeWidth = 2.dp,
                            color = colors.accent_blue,
                        )
                    } else {
                        TextButton(
                            onClick = { on_manage(order.id) },
                            enabled = state.renewing_order_id == null,
                        ) {
                            Text(
                                text = stringResource(R.string.domain_purchase_manage),
                                color = colors.accent_blue,
                                fontSize = 14.sp,
                            )
                        }
                    }
                }
            }
        }
        if (order.status == "pending_payment") {
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (state.cancelling_order_id == order.id) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(16.dp),
                        strokeWidth = 2.dp,
                        color = colors.danger,
                    )
                } else {
                    TextButton(
                        onClick = { on_cancel(order.id) },
                        enabled = state.cancelling_order_id == null,
                    ) {
                        Text(
                            text = stringResource(R.string.cancel),
                            color = colors.danger,
                            fontSize = 14.sp,
                        )
                    }
                }
                Spacer(Modifier.width(AsterSpacing.sm))
                if (state.buying) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(16.dp),
                        strokeWidth = 2.dp,
                        color = colors.accent_blue,
                    )
                } else {
                    TextButton(onClick = { on_complete_purchase(order) }) {
                        Text(
                            text = stringResource(R.string.domain_purchase_complete_purchase),
                            color = colors.accent_blue,
                            fontSize = 14.sp,
                        )
                    }
                }
            }
        }
    }
}