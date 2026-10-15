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
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ThumbDown
import androidx.compose.material.icons.filled.ThumbUp
import androidx.compose.material.icons.outlined.ThumbDown
import androidx.compose.material.icons.outlined.ThumbUp
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import compose.icons.TablerIcons
import compose.icons.tablericons.ChevronDown
import compose.icons.tablericons.ExternalLink
import compose.icons.tablericons.InfoCircle
import compose.icons.tablericons.Package
import compose.icons.tablericons.Receipt
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.astermail.android.R
import org.astermail.android.design.AsterMaterial
import org.astermail.android.design.AsterRadius
import org.astermail.android.design.AsterShapes
import org.astermail.android.design.AsterSpacing
import org.astermail.android.design.acrylic
import org.astermail.android.design.components.AsterCompactButton
import org.astermail.android.design.components.AsterIconButton
import org.astermail.android.design.components.AsterTextField
import org.astermail.android.design.island_divider_color
import org.astermail.android.mail.extraction.EmailExtractionResult
import org.astermail.android.mail.extraction.ExtractedAmount
import org.astermail.android.mail.extraction.ReceiptEmailReview
import org.astermail.android.mail.extraction.ShippingStatus
import org.astermail.android.mail.extraction.document_kind_receipt
import org.astermail.android.mail.extraction.extract_email_details
import org.astermail.android.mail.extraction.extract_order_url
import org.astermail.android.mail.extraction.read_receipt_banner_collapsed
import org.astermail.android.mail.extraction.receipt_review_store
import org.astermail.android.mail.extraction.receipt_review_store_for
import org.astermail.android.mail.extraction.write_receipt_banner_collapsed
import org.astermail.android.security.safe_sender_name
import org.astermail.android.ui.common.app_toast
import org.astermail.android.ui.common.open_external_url
import org.astermail.android.ui.common.remember_copy_action
import java.util.Locale

private const val max_visible_receipt_items = 6
private const val max_receipt_field_length = 80

internal class order_card_model(
    val result: EmailExtractionResult,
    val order_url: String?,
    val review: ReceiptEmailReview?,
    val merchant: String?,
    val order_id: String?,
)

private const val logo_min_ink_fraction = 0.04f
private const val logo_opaque_fraction = 0.9f

internal fun merchant_matches_sender_domain(merchant: String, sender_email: String): Boolean {
    val domain = sender_email.substringAfter('@', "").trim().lowercase(Locale.ROOT)
    if (domain.isEmpty()) return false
    val label = get_root_domain(domain).substringBefore('.').filter { it.isLetterOrDigit() }
    val merchant_key = merchant.lowercase(Locale.ROOT).filter { it.isLetterOrDigit() }
    if (label.length < 3 || merchant_key.length < 3) return false
    return merchant_key.contains(label) || label.contains(merchant_key)
}

internal fun is_blank_logo_pixels(pixels: IntArray): Boolean {
    if (pixels.isEmpty()) return true
    val visible = pixels.count { ((it ushr 24) and 0xFF) >= 40 }
    val transparent_backdrop = visible.toFloat() / pixels.size < logo_opaque_fraction
    var ink = 0
    for (pixel in pixels) {
        val alpha = (pixel ushr 24) and 0xFF
        if (alpha < 40) continue
        val red = (pixel shr 16) and 0xFF
        val green = (pixel shr 8) and 0xFF
        val blue = pixel and 0xFF
        if (!transparent_backdrop && red > 235 && green > 235 && blue > 235) continue
        ink += 1
    }
    return ink.toFloat() / pixels.size < logo_min_ink_fraction
}

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
    val context = LocalContext.current
    val store = remember { receipt_review_store_for(context) }
    var review_version by remember(msg.id) { mutableIntStateOf(0) }
    val model by produceState<order_card_model?>(
        initialValue = null,
        msg.id, msg.body, msg.body_html, subject, review_version,
    ) {
        value = withContext(Dispatchers.Default) {
            val result = extract_email_details(
                subject = subject,
                body_text = msg.body,
                body_html = msg.body_html,
                from_email = msg.sender_email,
                from_name = safe_sender_name(msg.sender_name, msg.sender_email),
            )
            if (result.is_empty) {
                null
            } else {
                val purchase = result.purchase
                val review = store.review_for(msg.id)
                val reviewed = purchase?.let {
                    store.apply_review(msg.id, it.merchant_source, it.merchant_name, it.order_id)
                }
                order_card_model(
                    result = result,
                    order_url = extract_order_url(msg.body, msg.body_html),
                    review = review,
                    merchant = reviewed?.first,
                    order_id = reviewed?.second,
                )
            }
        }
    }
    val snapshot = model ?: return
    if (snapshot.review?.hidden == true) return
    order_details_card(
        msg = msg,
        model = snapshot,
        store = store,
        on_review_changed = { review_version += 1 },
        modifier = modifier,
    )
}

@Composable
private fun order_details_card(
    msg: ThreadMessage,
    model: order_card_model,
    store: receipt_review_store,
    on_review_changed: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = AsterMaterial.colors
    val context = LocalContext.current
    val copy_action = remember_copy_action()
    val purchase = model.result.purchase?.takeIf { it.has_meaningful_data }
    val shipping = model.result.shipping?.takeIf { it.has_meaningful_data }
    var collapsed by remember { mutableStateOf(read_receipt_banner_collapsed(context)) }
    var reviewing by remember(msg.id) { mutableStateOf(false) }

    val review = model.review
    val merchant = if (purchase != null) model.merchant else null
    val order_id = if (purchase != null) model.order_id else null
    val is_receipt = purchase?.document_kind == document_kind_receipt
    val carrier_name = shipping?.carrier_name?.takeIf { it.isNotBlank() }
    val title = when {
        purchase != null && merchant != null ->
            stringResource(if (is_receipt) R.string.receipt_card_receipt_from else R.string.order_card_order_from, merchant)
        purchase == null && carrier_name != null -> stringResource(R.string.order_card_package_from, carrier_name)
        else -> stringResource(R.string.order_card_title_generic)
    }
    val status = shipping?.status
    val purchase_subtitle = listOfNotNull(
        order_id?.let {
            stringResource(if (is_receipt) R.string.receipt_card_receipt_number else R.string.order_card_order_number_short, it)
        },
        purchase?.order_date,
    ).joinToString(" · ").ifEmpty { null }
    val subtitle = when {
        purchase_subtitle != null -> purchase_subtitle
        status == ShippingStatus.delivered -> shipping_status_text(status)
        shipping?.estimated_delivery != null -> stringResource(R.string.order_card_expected_by, shipping.estimated_delivery)
        status != null && status != ShippingStatus.unknown -> shipping_status_text(status)
        else -> null
    }
    val tracking_url = shipping?.tracking_url
    val view_url = purchase?.receipt_url ?: model.order_url
    val copied_text = stringResource(R.string.copied_to_clipboard)
    val tracking_label = stringResource(R.string.order_card_tracking_number)
    val could_not_open = stringResource(R.string.could_not_open_link)
    val privacy_text = stringResource(R.string.receipt_card_privacy)
    val saved_text = stringResource(R.string.receipt_card_review_saved)
    val thanks_text = stringResource(R.string.receipt_card_review_thanks)
    val payment_text = when {
        purchase?.card_last_four != null -> stringResource(R.string.receipt_card_card_ending_in, purchase.card_last_four)
        else -> purchase?.payment_method
    }
    val open_link: (String) -> Unit = { url ->
        if (!open_external_url(context, url)) app_toast.show(could_not_open)
    }

    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = AsterSpacing.lg, vertical = AsterSpacing.xs)
            .acrylic(colors, AsterShapes.island, colors.bg_secondary)
            .testTag("order_details_card"),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clickable {
                    collapsed = !collapsed
                    write_receipt_banner_collapsed(context, collapsed)
                }
                .padding(horizontal = AsterSpacing.row_pad_x, vertical = AsterSpacing.row_pad_y),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                imageVector = if (purchase != null) TablerIcons.Receipt else TablerIcons.Package,
                contentDescription = null,
                tint = colors.text_secondary,
                modifier = Modifier.size(20.dp),
            )
            Spacer(Modifier.width(AsterSpacing.md))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = title,
                    color = colors.text_primary,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                if (subtitle != null) {
                    Spacer(Modifier.height(2.dp))
                    Text(
                        text = subtitle,
                        color = colors.text_muted,
                        fontSize = 12.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
            if (purchase?.total != null) {
                Spacer(Modifier.width(AsterSpacing.sm))
                Text(
                    text = purchase.total.formatted,
                    color = colors.text_primary,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                )
            }
            Spacer(Modifier.width(AsterSpacing.sm))
            val rotation by animateFloatAsState(if (collapsed) 0f else 180f, label = "receipt_card_chevron")
            Icon(
                imageVector = TablerIcons.ChevronDown,
                contentDescription = stringResource(
                    if (collapsed) R.string.order_card_expand else R.string.order_card_collapse,
                ),
                tint = colors.text_muted,
                modifier = Modifier.size(18.dp).rotate(rotation),
            )
        }
        AnimatedVisibility(visible = !collapsed) {
            Column(modifier = Modifier.fillMaxWidth()) {
                if (purchase != null && purchase.items.isNotEmpty()) {
                    receipt_divider()
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = AsterSpacing.row_pad_x, vertical = AsterSpacing.row_pad_y),
                        verticalArrangement = Arrangement.spacedBy(AsterSpacing.xs),
                    ) {
                        for (item in purchase.items.take(max_visible_receipt_items)) {
                            val quantity = item.quantity ?: 1
                            val name = if (quantity > 1) "$quantity × " + item.name else item.name
                            receipt_amount_row(label = name, amount = item.total_price ?: item.unit_price)
                        }
                        val hidden_count = purchase.items.size - max_visible_receipt_items
                        if (hidden_count > 0) {
                            Text(
                                text = pluralStringResource(R.plurals.receipt_card_more_items, hidden_count, hidden_count),
                                color = colors.text_muted,
                                fontSize = 12.sp,
                            )
                        }
                    }
                }
                val has_totals = purchase != null && (
                    purchase.subtotal != null || purchase.shipping_cost != null || purchase.tax != null ||
                        purchase.discount != null || purchase.total != null
                    )
                if (purchase != null && has_totals) {
                    receipt_divider()
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = AsterSpacing.row_pad_x, vertical = AsterSpacing.row_pad_y),
                        verticalArrangement = Arrangement.spacedBy(AsterSpacing.xs),
                    ) {
                        purchase.subtotal?.let { receipt_amount_row(stringResource(R.string.receipt_card_subtotal), it) }
                        purchase.shipping_cost?.let { receipt_amount_row(stringResource(R.string.receipt_card_shipping), it) }
                        purchase.tax?.let { receipt_amount_row(stringResource(R.string.receipt_card_tax), it) }
                        purchase.discount?.let {
                            receipt_amount_row(stringResource(R.string.receipt_card_discount), it, negative = true)
                        }
                        purchase.total?.let {
                            receipt_amount_row(stringResource(R.string.order_card_total), it, emphasised = true)
                        }
                    }
                }
                receipt_divider()
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = AsterSpacing.row_pad_x, vertical = AsterSpacing.row_pad_y),
                    verticalArrangement = Arrangement.spacedBy(AsterSpacing.sm),
                ) {
                    if (purchase != null) {
                        receipt_detail_row(
                            label = stringResource(R.string.receipt_card_merchant),
                            value = merchant ?: stringResource(R.string.receipt_card_unknown_merchant),
                        )
                    }
                    if (order_id != null) {
                        receipt_detail_row(label = stringResource(R.string.order_card_order_number), value = order_id)
                    }
                    if (purchase?.order_date != null) {
                        receipt_detail_row(label = stringResource(R.string.date), value = purchase.order_date)
                    }
                    if (payment_text != null) {
                        receipt_detail_row(label = stringResource(R.string.receipt_card_payment), value = payment_text)
                    }
                    if (status == ShippingStatus.delivered && shipping?.delivery_date != null) {
                        receipt_detail_row(label = stringResource(R.string.order_card_delivered_on), value = shipping.delivery_date)
                    } else if (shipping?.estimated_delivery != null) {
                        receipt_detail_row(label = stringResource(R.string.order_card_expected), value = shipping.estimated_delivery)
                    }
                    if (shipping?.tracking_number != null) {
                        val tracking_value = if (carrier_name != null) {
                            carrier_name + " " + shipping.tracking_number
                        } else {
                            shipping.tracking_number
                        }
                        receipt_detail_row(
                            label = tracking_label,
                            value = tracking_value,
                            copy_value = shipping.tracking_number,
                            on_click = { copy_action(tracking_label, shipping.tracking_number, copied_text) },
                        )
                    }
                }
                receipt_divider()
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = AsterSpacing.xs, vertical = AsterSpacing.xs),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    receipt_info_button(
                        on_show = {
                            app_toast.show(if (review?.is_corrected == true) saved_text else privacy_text)
                        },
                    )
                    if (tracking_url != null) {
                        receipt_link_button(
                            label = stringResource(R.string.order_card_track_package),
                            test_tag = "order_card_track_package",
                            on_click = { open_link(tracking_url) },
                        )
                    }
                    if (view_url != null) {
                        receipt_link_button(
                            label = stringResource(
                                if (is_receipt) R.string.receipt_card_view_receipt else R.string.order_card_view_order,
                            ),
                            test_tag = "order_card_view_order",
                            on_click = { open_link(view_url) },
                        )
                    }
                    Spacer(Modifier.weight(1f))
                    if (purchase != null) {
                        val confirmed = review?.confirmed == true
                        val corrected = review?.is_corrected == true
                        AsterIconButton(
                            icon = if (confirmed) Icons.Filled.ThumbUp else Icons.Outlined.ThumbUp,
                            content_description = stringResource(R.string.receipt_card_looks_right),
                            onClick = {
                                store.confirm(msg.id)
                                if (!confirmed) app_toast.show(thanks_text)
                                reviewing = false
                                on_review_changed()
                            },
                            tint = if (confirmed) colors.accent_blue else colors.text_secondary,
                            icon_size = 18,
                            modifier = Modifier.testTag("receipt_card_looks_right"),
                        )
                        AsterIconButton(
                            icon = if (corrected) Icons.Filled.ThumbDown else Icons.Outlined.ThumbDown,
                            content_description = stringResource(R.string.receipt_card_not_right),
                            onClick = { reviewing = !reviewing },
                            tint = if (corrected) colors.accent_blue else colors.text_secondary,
                            icon_size = 18,
                            modifier = Modifier.testTag("receipt_card_not_right"),
                        )
                    }
                }
                if (purchase != null && reviewing) {
                    receipt_divider()
                    receipt_review_form(
                        initial_merchant = merchant.orEmpty(),
                        initial_order_id = order_id.orEmpty(),
                        on_save = { merchant_input, order_input ->
                            store.save_correction(msg.id, purchase.merchant_source, merchant_input, order_input)
                            reviewing = false
                            on_review_changed()
                        },
                        on_cancel = { reviewing = false },
                        on_hide = {
                            store.hide(msg.id)
                            reviewing = false
                            on_review_changed()
                        },
                    )
                }
            }
        }
    }
}

@Composable
private fun receipt_divider() {
    val colors = AsterMaterial.colors
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(0.5.dp)
            .background(island_divider_color(colors)),
    )
}

@Composable
private fun receipt_amount_row(
    label: String,
    amount: ExtractedAmount?,
    negative: Boolean = false,
    emphasised: Boolean = false,
) {
    val colors = AsterMaterial.colors
    val size = if (emphasised) 14.sp else 13.sp
    val weight = if (emphasised) FontWeight.SemiBold else FontWeight.Normal
    Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Text(
            text = label,
            color = if (emphasised) colors.text_primary else colors.text_secondary,
            fontSize = size,
            fontWeight = weight,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f),
        )
        if (amount != null) {
            Spacer(Modifier.width(AsterSpacing.md))
            Text(
                text = if (negative) "−" + amount.formatted else amount.formatted,
                color = colors.text_primary,
                fontSize = size,
                fontWeight = weight,
                maxLines = 1,
            )
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun receipt_detail_row(
    label: String,
    value: String,
    copy_value: String = value,
    on_click: (() -> Unit)? = null,
) {
    val colors = AsterMaterial.colors
    val copy_action = remember_copy_action()
    val copied_text = stringResource(R.string.copied_to_clipboard)
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .combinedClickable(
                onLongClick = { copy_action(label, copy_value, copied_text) },
                onClick = { on_click?.invoke() },
            ),
        verticalAlignment = Alignment.Top,
    ) {
        Text(
            text = label,
            color = colors.text_muted,
            fontSize = 13.sp,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(0.4f),
        )
        Spacer(Modifier.width(AsterSpacing.md))
        Text(
            text = value,
            color = colors.text_primary,
            fontSize = 13.sp,
            textAlign = TextAlign.End,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(0.6f),
        )
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun receipt_info_button(on_show: () -> Unit) {
    val colors = AsterMaterial.colors
    Box(
        modifier = Modifier
            .size(40.dp)
            .clip(CircleShape)
            .combinedClickable(onClick = on_show, onLongClick = on_show)
            .testTag("receipt_card_info"),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            imageVector = TablerIcons.InfoCircle,
            contentDescription = stringResource(R.string.receipt_card_privacy),
            tint = colors.text_muted,
            modifier = Modifier.size(18.dp),
        )
    }
}

@Composable
private fun receipt_link_button(label: String, test_tag: String, on_click: () -> Unit) {
    val colors = AsterMaterial.colors
    Row(
        modifier = Modifier
            .clip(detail_chip_shape)
            .clickable(onClick = on_click)
            .padding(horizontal = AsterSpacing.sm, vertical = AsterSpacing.sm)
            .testTag(test_tag),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(AsterSpacing.xs),
    ) {
        Text(
            text = label,
            color = colors.accent_blue,
            fontSize = 14.sp,
            fontWeight = FontWeight.SemiBold,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        Icon(
            imageVector = TablerIcons.ExternalLink,
            contentDescription = null,
            tint = colors.accent_blue,
            modifier = Modifier.size(14.dp),
        )
    }
}

@Composable
private fun receipt_ghost_button(label: String, muted: Boolean, test_tag: String, on_click: () -> Unit) {
    val colors = AsterMaterial.colors
    Box(
        modifier = Modifier
            .height(40.dp)
            .clip(RoundedCornerShape(AsterRadius.control))
            .clickable(onClick = on_click)
            .padding(horizontal = AsterSpacing.md)
            .testTag(test_tag),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = label,
            color = if (muted) colors.text_muted else colors.accent_blue,
            fontSize = 14.sp,
            fontWeight = FontWeight.SemiBold,
            maxLines = 1,
        )
    }
}

@Composable
private fun receipt_review_form(
    initial_merchant: String,
    initial_order_id: String,
    on_save: (String, String) -> Unit,
    on_cancel: () -> Unit,
    on_hide: () -> Unit,
) {
    val colors = AsterMaterial.colors
    var merchant_input by remember { mutableStateOf(initial_merchant.take(max_receipt_field_length)) }
    var order_input by remember { mutableStateOf(initial_order_id.take(max_receipt_field_length)) }
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = AsterSpacing.row_pad_x, vertical = AsterSpacing.row_pad_y)
            .testTag("receipt_card_review_form"),
        verticalArrangement = Arrangement.spacedBy(AsterSpacing.sm),
    ) {
        Text(
            text = stringResource(R.string.receipt_card_review_title),
            color = colors.text_primary,
            fontSize = 14.sp,
            fontWeight = FontWeight.SemiBold,
        )
        AsterTextField(
            value = merchant_input,
            onValueChange = { merchant_input = it.take(max_receipt_field_length) },
            label = stringResource(R.string.receipt_card_merchant),
            modifier = Modifier.fillMaxWidth().testTag("receipt_card_merchant_field"),
        )
        AsterTextField(
            value = order_input,
            onValueChange = { order_input = it.take(max_receipt_field_length) },
            label = stringResource(R.string.order_card_order_number),
            modifier = Modifier.fillMaxWidth().testTag("receipt_card_order_field"),
        )
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(AsterSpacing.xs),
        ) {
            AsterCompactButton(
                label = stringResource(R.string.save),
                onClick = { on_save(merchant_input, order_input) },
                modifier = Modifier.testTag("receipt_card_review_save"),
            )
            receipt_ghost_button(
                label = stringResource(R.string.cancel),
                muted = false,
                test_tag = "receipt_card_review_cancel",
                on_click = on_cancel,
            )
            Spacer(Modifier.weight(1f))
            receipt_ghost_button(
                label = stringResource(R.string.receipt_card_hide),
                muted = true,
                test_tag = "receipt_card_review_hide",
                on_click = on_hide,
            )
        }
    }
}
