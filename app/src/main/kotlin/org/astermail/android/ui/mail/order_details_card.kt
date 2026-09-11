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
package org.astermail.android.ui.mail

import androidx.compose.animation.AnimatedVisibility
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import compose.icons.TablerIcons
import compose.icons.tablericons.BuildingStore
import compose.icons.tablericons.ChevronDown
import compose.icons.tablericons.ChevronUp
import compose.icons.tablericons.Clock
import compose.icons.tablericons.Package
import compose.icons.tablericons.Receipt
import compose.icons.tablericons.ShoppingCart
import compose.icons.tablericons.Truck
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.astermail.android.R
import org.astermail.android.design.AsterMaterial
import org.astermail.android.design.AsterSpacing
import org.astermail.android.design.components.AsterDivider
import org.astermail.android.mail.extraction.EmailExtractionResult
import org.astermail.android.mail.extraction.ShippingStatus
import org.astermail.android.mail.extraction.extract_email_details
import org.astermail.android.mail.extraction.extract_order_url
import org.astermail.android.ui.common.open_external_url
import org.astermail.android.ui.common.remember_copy_action
import java.util.Collections

private val collapsed_order_cards: MutableSet<String> = Collections.synchronizedSet(HashSet())

internal class order_card_model(
    val result: EmailExtractionResult,
    val order_url: String?,
)

@Composable
internal fun shipping_status_text(status: ShippingStatus): String = stringResource(
    when (status) {
        ShippingStatus.label_created -> R.string.shipping_status_label_created
        ShippingStatus.shipped -> R.string.shipping_status_shipped
        ShippingStatus.in_transit -> R.string.shipping_status_in_transit
        ShippingStatus.out_for_delivery -> R.string.shipping_status_out_for_delivery
        ShippingStatus.delivered -> R.string.shipping_status_delivered
        ShippingStatus.exception -> R.string.shipping_status_exception
        ShippingStatus.unknown -> R.string.shipping_status_unknown
    },
)

@Composable
internal fun order_details_card_for_message(
    msg: ThreadMessage,
    subject: String,
    modifier: Modifier = Modifier,
) {
    if (msg.is_undecryptable || msg.is_body_pending || is_aster_system_sender(msg)) return
    if (msg.body.isBlank() && msg.body_html.isNullOrBlank()) return
    val model by produceState<order_card_model?>(
        initialValue = null,
        msg.id, msg.body, msg.body_html, subject,
    ) {
        value = withContext(Dispatchers.Default) {
            val result = extract_email_details(
                subject = subject,
                body_text = msg.body,
                body_html = msg.body_html,
                from_email = msg.sender_email,
                from_name = msg.sender_name,
            )
            if (result.is_empty) {
                null
            } else {
                order_card_model(result, extract_order_url(msg.body, msg.body_html))
            }
        }
    }
    val snapshot = model ?: return
    order_details_card(message_id = msg.id, msg = msg, model = snapshot, modifier = modifier)
}

@Composable
private fun order_details_card(
    message_id: String,
    msg: ThreadMessage,
    model: order_card_model,
    modifier: Modifier = Modifier,
) {
    val colors = AsterMaterial.colors
    val context = LocalContext.current
    val copy_action = remember_copy_action()
    val purchase = model.result.purchase?.takeIf { it.has_meaningful_data }
    val shipping = model.result.shipping?.takeIf { it.has_meaningful_data }
    var collapsed by remember(message_id) { mutableStateOf(message_id in collapsed_order_cards) }

    val merchant = purchase?.merchant_name
    val carrier_name = shipping?.carrier_name?.takeIf { it.isNotBlank() }
    val title = when {
        merchant != null -> stringResource(R.string.order_card_order_from, merchant)
        carrier_name != null -> stringResource(R.string.order_card_package_from, carrier_name)
        else -> stringResource(R.string.order_card_title_generic)
    }
    val status = shipping?.status
    val subtitle = when {
        status == ShippingStatus.delivered -> shipping_status_text(status)
        shipping?.estimated_delivery != null -> stringResource(R.string.order_card_expected_by, shipping.estimated_delivery)
        status != null && status != ShippingStatus.unknown -> shipping_status_text(status)
        purchase?.order_id != null -> stringResource(R.string.order_card_order_number_short, purchase.order_id)
        purchase?.total != null -> purchase.total.formatted
        else -> null
    }
    val shown_sender_name = displayed_sender_name(msg.display_sender_name, msg.sender_name)
    val shown_sender_email = displayed_sender_email(msg.display_sender_email, msg.sender_email)
    val tracking_url = shipping?.tracking_url
    val order_url = model.order_url
    val has_actions = tracking_url != null || order_url != null
    val copied_text = stringResource(R.string.copied_to_clipboard)
    val tracking_label = stringResource(R.string.order_card_tracking_number)
    val could_not_open = stringResource(R.string.could_not_open_link)

    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(
                start = inbox_card_horizontal_margin,
                end = inbox_card_horizontal_margin,
                bottom = inbox_group_split,
            )
            .clip(inbox_card_shape)
            .background(colors.bg_card)
            .border(0.5.dp, colors.border_primary, inbox_card_shape)
            .testTag("order_details_card"),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clickable {
                    collapsed = !collapsed
                    if (collapsed) collapsed_order_cards.add(message_id) else collapsed_order_cards.remove(message_id)
                }
                .padding(
                    start = inbox_card_content_padding,
                    end = AsterSpacing.sm,
                    top = AsterSpacing.md,
                    bottom = AsterSpacing.md,
                ),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (merchant != null) {
                SenderAvatar(
                    email = shown_sender_email,
                    name = shown_sender_name,
                    size = 36.dp,
                )
            } else {
                Box(
                    modifier = Modifier
                        .size(36.dp)
                        .clip(CircleShape)
                        .background(colors.bg_tertiary),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        imageVector = TablerIcons.Package,
                        contentDescription = null,
                        tint = colors.text_secondary,
                        modifier = Modifier.size(18.dp),
                    )
                }
            }
            Spacer(Modifier.width(AsterSpacing.md))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = title,
                    color = colors.text_primary,
                    fontSize = 15.sp,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                if (subtitle != null) {
                    Spacer(Modifier.height(2.dp))
                    Text(
                        text = subtitle,
                        color = colors.text_secondary,
                        fontSize = 13.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
            Spacer(Modifier.width(AsterSpacing.sm))
            Icon(
                imageVector = if (collapsed) TablerIcons.ChevronDown else TablerIcons.ChevronUp,
                contentDescription = stringResource(
                    if (collapsed) R.string.order_card_expand else R.string.order_card_collapse,
                ),
                tint = colors.text_muted,
                modifier = Modifier.padding(AsterSpacing.sm).size(18.dp),
            )
        }
        AnimatedVisibility(visible = !collapsed) {
            Column(modifier = Modifier.fillMaxWidth()) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(
                            start = inbox_card_content_padding,
                            end = inbox_card_content_padding,
                            bottom = AsterSpacing.sm,
                        ),
                    verticalArrangement = Arrangement.spacedBy(AsterSpacing.md),
                ) {
                    if (merchant != null) {
                        order_detail_row(
                            icon = TablerIcons.BuildingStore,
                            label = stringResource(R.string.order_card_ordered_from),
                            value = merchant,
                        )
                    }
                    if (purchase?.order_id != null) {
                        order_detail_row(
                            icon = TablerIcons.Receipt,
                            label = stringResource(R.string.order_card_order_number),
                            value = purchase.order_id,
                        )
                    }
                    if (status == ShippingStatus.delivered && shipping?.delivery_date != null) {
                        order_detail_row(
                            icon = TablerIcons.Clock,
                            label = stringResource(R.string.order_card_delivered_on),
                            value = shipping.delivery_date,
                        )
                    } else if (shipping?.estimated_delivery != null) {
                        order_detail_row(
                            icon = TablerIcons.Clock,
                            label = stringResource(R.string.order_card_expected),
                            value = shipping.estimated_delivery,
                        )
                    }
                    if (shipping?.tracking_number != null) {
                        val tracking_value = if (carrier_name != null) {
                            carrier_name + " " + shipping.tracking_number
                        } else {
                            shipping.tracking_number
                        }
                        order_detail_row(
                            icon = TablerIcons.Truck,
                            label = tracking_label,
                            value = tracking_value,
                            on_click = { copy_action(tracking_label, shipping.tracking_number, copied_text) },
                        )
                    }
                    if (purchase?.total != null) {
                        order_detail_row(
                            icon = TablerIcons.Receipt,
                            label = stringResource(R.string.order_card_total),
                            value = purchase.total.formatted,
                        )
                    }
                    if (purchase != null && purchase.items.isNotEmpty()) {
                        order_detail_row(
                            icon = TablerIcons.ShoppingCart,
                            label = stringResource(R.string.order_card_items),
                            value = purchase.items.take(3).joinToString(", ") { it.name },
                        )
                    }
                }
                if (has_actions) {
                    AsterDivider(modifier = Modifier.padding(horizontal = inbox_card_content_padding))
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = AsterSpacing.sm, vertical = AsterSpacing.xs),
                        horizontalArrangement = Arrangement.spacedBy(AsterSpacing.xs),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        if (tracking_url != null) {
                            order_text_button(
                                label = stringResource(R.string.order_card_track_package),
                                test_tag = "order_card_track_package",
                                on_click = {
                                    if (!open_external_url(context, tracking_url)) {
                                        android.widget.Toast.makeText(context, could_not_open, android.widget.Toast.LENGTH_SHORT).show()
                                    }
                                },
                            )
                        }
                        if (order_url != null) {
                            order_text_button(
                                label = stringResource(R.string.order_card_view_order),
                                test_tag = "order_card_view_order",
                                on_click = {
                                    if (!open_external_url(context, order_url)) {
                                        android.widget.Toast.makeText(context, could_not_open, android.widget.Toast.LENGTH_SHORT).show()
                                    }
                                },
                            )
                        }
                    }
                } else {
                    Spacer(Modifier.height(AsterSpacing.sm))
                }
            }
        }
    }
}

@Composable
private fun order_detail_row(
    icon: ImageVector,
    label: String,
    value: String,
    on_click: (() -> Unit)? = null,
) {
    val colors = AsterMaterial.colors
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .then(if (on_click != null) Modifier.clickable(onClick = on_click) else Modifier),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = colors.text_secondary,
            modifier = Modifier.size(18.dp),
        )
        Spacer(Modifier.width(AsterSpacing.md))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = label,
                color = colors.text_muted,
                fontSize = 12.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = value,
                color = colors.text_primary,
                fontSize = 14.sp,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

@Composable
private fun order_text_button(label: String, test_tag: String, on_click: () -> Unit) {
    val colors = AsterMaterial.colors
    Text(
        text = label,
        color = colors.accent_blue,
        fontSize = 14.sp,
        fontWeight = FontWeight.SemiBold,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
        modifier = Modifier
            .clip(detail_chip_shape)
            .clickable(onClick = on_click)
            .padding(horizontal = AsterSpacing.sm, vertical = AsterSpacing.sm)
            .testTag(test_tag),
    )
}
