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

package org.astermail.android.mail.extraction

data class ExtractedAmount(
    val value: Double,
    val currency: String,
    val formatted: String,
)

data class ExtractedItem(
    val name: String,
    val quantity: Int?,
    val unit_price: ExtractedAmount?,
    val total_price: ExtractedAmount?,
)

data class ExtractedPurchaseDetails(
    val order_id: String?,
    val order_date: String?,
    val merchant_name: String?,
    val items: List<ExtractedItem>,
    val subtotal: ExtractedAmount?,
    val tax: ExtractedAmount?,
    val shipping_cost: ExtractedAmount?,
    val discount: ExtractedAmount?,
    val total: ExtractedAmount?,
    val payment_method: String?,
    val card_last_four: String?,
    val billing_address: String?,
    val confirmation_number: String?,
    val transaction_id: String?,
    val raw_signals: List<String>,
) {
    val has_meaningful_data: Boolean
        get() = order_id != null || total != null || merchant_name != null ||
            items.any { it.name.isNotBlank() }
}

enum class ShippingCarrier(val id: String, val display_name: String, val tracking_base_url: String) {
    ups("ups", "UPS", "https://www.ups.com/track?tracknum="),
    fedex("fedex", "FedEx", "https://www.fedex.com/fedextrack/?trknbr="),
    usps("usps", "USPS", "https://tools.usps.com/go/TrackConfirmAction?tLabels="),
    dhl("dhl", "DHL", "https://www.dhl.com/us-en/home/tracking/tracking-express.html?submit=1&tracking-id="),
    amazon("amazon", "Amazon Logistics", "https://www.amazon.com/gp/css/shiptrack/view.html?trackingId="),
    ontrac("ontrac", "OnTrac", "https://www.ontrac.com/tracking/?number="),
    lasership("lasership", "LaserShip", "https://www.lasership.com/track/"),
    other("other", "", ""),
}

enum class ShippingStatus {
    label_created,
    shipped,
    in_transit,
    out_for_delivery,
    delivered,
    exception,
    unknown,
}

data class ExtractedShippingDetails(
    val tracking_number: String?,
    val carrier: ShippingCarrier?,
    val carrier_name: String?,
    val tracking_url: String?,
    val status: ShippingStatus,
    val estimated_delivery: String?,
    val shipped_date: String?,
    val delivery_date: String?,
    val origin: String?,
    val destination: String?,
    val items_shipped: List<String>,
    val raw_signals: List<String>,
) {
    val has_meaningful_data: Boolean
        get() = tracking_number != null || !carrier_name.isNullOrBlank() || status != ShippingStatus.unknown
}

data class EmailExtractionResult(
    val has_purchase_details: Boolean,
    val has_shipping_details: Boolean,
    val purchase: ExtractedPurchaseDetails?,
    val shipping: ExtractedShippingDetails?,
    val extracted_at: Long,
) {
    val is_empty: Boolean
        get() = purchase?.has_meaningful_data != true && shipping?.has_meaningful_data != true

    companion object {
        val empty = EmailExtractionResult(false, false, null, null, 0L)
    }
}
