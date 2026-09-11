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

import org.astermail.android.mail.looks_like_html_body
import java.util.Locale

private class carrier_patterns(val carrier: ShippingCarrier, val patterns: List<Regex>)

private val strong_tracking_patterns = listOf(
    carrier_patterns(ShippingCarrier.ups, listOf(Regex("\\b(1Z[A-Z0-9]{16})\\b", RegexOption.IGNORE_CASE))),
    carrier_patterns(
        ShippingCarrier.fedex,
        listOf(Regex("\\b(96\\d{20})\\b"), Regex("\\b(DT\\d{12})\\b", RegexOption.IGNORE_CASE)),
    ),
    carrier_patterns(
        ShippingCarrier.usps,
        listOf(
            Regex("\\b(94\\d{20,22})\\b"),
            Regex("\\b(92\\d{20,22})\\b"),
            Regex("\\b(93\\d{20,22})\\b"),
            Regex("\\b(420\\d{27,31})\\b"),
            Regex("\\b([A-Z]{2}\\d{9}US)\\b", RegexOption.IGNORE_CASE),
        ),
    ),
    carrier_patterns(ShippingCarrier.dhl, listOf(Regex("\\b(JD\\d{18})\\b", RegexOption.IGNORE_CASE))),
    carrier_patterns(ShippingCarrier.amazon, listOf(Regex("\\b(TBA\\d{12,15})\\b", RegexOption.IGNORE_CASE))),
)

private val weak_tracking_patterns = mapOf(
    ShippingCarrier.ups to listOf(Regex("\\b(T\\d{10})\\b"), Regex("\\b(\\d{18})\\b")),
    ShippingCarrier.fedex to listOf(Regex("\\b(\\d{15})\\b"), Regex("\\b(\\d{12,22})\\b")),
    ShippingCarrier.dhl to listOf(Regex("\\b([A-Z]{3}\\d{7})\\b", RegexOption.IGNORE_CASE), Regex("\\b(\\d{10,11})\\b")),
)

private val tracking_context_keywords = Regex(
    "(tracking\\s*(?:number|no|#)|track\\s+your|sendungsnummer|numero?\\s+de\\s+(?:suivi|seguimiento)|suivi|seguimiento|waybill|consignment)",
    RegexOption.IGNORE_CASE,
)

private val carrier_domain_map = listOf(
    "ups.com" to ShippingCarrier.ups,
    "fedex.com" to ShippingCarrier.fedex,
    "usps.com" to ShippingCarrier.usps,
    "dhl.com" to ShippingCarrier.dhl,
    "amazon.com" to ShippingCarrier.amazon,
    "ontrac.com" to ShippingCarrier.ontrac,
    "lasership.com" to ShippingCarrier.lasership,
)

private val carrier_name_map = listOf(
    "ups" to ShippingCarrier.ups,
    "united parcel service" to ShippingCarrier.ups,
    "fedex" to ShippingCarrier.fedex,
    "federal express" to ShippingCarrier.fedex,
    "usps" to ShippingCarrier.usps,
    "united states postal service" to ShippingCarrier.usps,
    "dhl" to ShippingCarrier.dhl,
    "amazon" to ShippingCarrier.amazon,
    "amazon logistics" to ShippingCarrier.amazon,
    "ontrac" to ShippingCarrier.ontrac,
    "lasership" to ShippingCarrier.lasership,
).map { (name, carrier) -> Regex("\\b" + Regex.escape(name) + "\\b") to carrier }

private fun detect_carrier_from_email(from_email: String, subject: String, body: String): ShippingCarrier? {
    val from_lower = from_email.lowercase(Locale.ROOT)
    for ((domain, carrier) in carrier_domain_map) {
        if (from_lower.contains(domain)) return carrier
    }
    val combined = (subject + " " + body).lowercase(Locale.ROOT)
    for ((boundary, carrier) in carrier_name_map) {
        if (boundary.containsMatchIn(combined)) return carrier
    }
    return null
}

private fun match_first_pattern(patterns: List<Regex>, text: String): String? {
    for (pattern in patterns) {
        val match = pattern.find(text) ?: continue
        return match.groupValues[1]
    }
    return null
}

private fun strong_patterns_for(carrier: ShippingCarrier): List<Regex> =
    strong_tracking_patterns.firstOrNull { it.carrier == carrier }?.patterns ?: emptyList()

private fun tracking_context_windows(body: String): List<String> {
    val windows = ArrayList<String>()
    for (match in tracking_context_keywords.findAll(body)) {
        val start = match.range.first
        windows.add(body.substring(start, minOf(body.length, start + 140)))
        if (windows.size >= 25) break
    }
    return windows
}

private class tracking_result(val number: String, val carrier: ShippingCarrier)

private val generic_tracking_pattern = Regex(
    "(?:tracking|sendungsnummer|suivi|seguimiento)\\s*(?:#|number|no\\.?|nummer)?\\s*[:\\s]\\s*([A-Z0-9]{8,35})",
    RegexOption.IGNORE_CASE,
)

private fun extract_tracking_number(
    body: String,
    carrier: ShippingCarrier?,
    tracking_url: String?,
): tracking_result? {
    if (tracking_url != null) {
        if (carrier != null) {
            val from_url = match_first_pattern(strong_patterns_for(carrier), tracking_url)
            if (from_url != null) return tracking_result(from_url, carrier)
        }
        for (entry in strong_tracking_patterns) {
            val from_url = match_first_pattern(entry.patterns, tracking_url)
            if (from_url != null) return tracking_result(from_url, entry.carrier)
        }
    }
    if (carrier != null) {
        val own_strong = match_first_pattern(strong_patterns_for(carrier), body)
        if (own_strong != null) return tracking_result(own_strong, carrier)
    }
    for (entry in strong_tracking_patterns) {
        val cross_strong = match_first_pattern(entry.patterns, body)
        if (cross_strong != null) return tracking_result(cross_strong, entry.carrier)
    }
    if (carrier != null) {
        val weak = weak_tracking_patterns[carrier]
        if (weak != null) {
            for (window in tracking_context_windows(body)) {
                val weak_match = match_first_pattern(weak, window)
                if (weak_match != null) return tracking_result(weak_match, carrier)
            }
        }
    }
    val generic = generic_tracking_pattern.find(body)
    if (generic != null) return tracking_result(generic.groupValues[1], carrier ?: ShippingCarrier.other)
    return null
}

private val tracking_url_patterns = listOf(
    Regex("https?://[^\\s<>\"]+track[^\\s<>\"]*", RegexOption.IGNORE_CASE),
    Regex("https?://[^\\s<>\"]+shipment[^\\s<>\"]*", RegexOption.IGNORE_CASE),
    Regex("https?://[^\\s<>\"]+delivery[^\\s<>\"]*", RegexOption.IGNORE_CASE),
)

private val url_trailing_junk = Regex("['\">\\]]+$")

private fun extract_tracking_url(body: String, html: String?): String? {
    val content = if (!html.isNullOrEmpty()) html else body
    for (pattern in tracking_url_patterns) {
        val match = pattern.find(content) ?: continue
        return match.value.replace(url_trailing_junk, "")
    }
    return null
}

private val future_deliver_regex = Regex("\\b(?:will be|to be|expected|estimated|scheduled|being)\\s+deliver")
private val strong_past_deliver_regex = Regex("\\b(?:was|has been|have been|been|successfully)\\s+delivered\\b")
private val delivered_on_regex = Regex("\\bdelivered\\s+(?:on|to|at)\\b")
private val exception_regex =
    Regex("\\b(?:delivery exception|delivery attempt|could not be delivered|unable to deliver|delivery failed)\\b")

fun detect_shipping_status(subject: String, body: String): ShippingStatus {
    val subj = subject.lowercase(Locale.ROOT)
    val combined = (subject + " " + body).lowercase(Locale.ROOT)

    if (combined.contains("out for delivery") || combined.contains("arriving today")) {
        return ShippingStatus.out_for_delivery
    }
    if (exception_regex.containsMatchIn(combined)) return ShippingStatus.exception
    if (
        strong_past_deliver_regex.containsMatchIn(combined) ||
        (delivered_on_regex.containsMatchIn(combined) && !future_deliver_regex.containsMatchIn(combined)) ||
        (subj.contains("delivered") && !future_deliver_regex.containsMatchIn(subj))
    ) {
        return ShippingStatus.delivered
    }
    if (
        combined.contains("in transit") ||
        combined.contains("on the way") ||
        combined.contains("on its way") ||
        combined.contains("en route")
    ) {
        return ShippingStatus.in_transit
    }
    if (
        combined.contains("label created") ||
        combined.contains("shipping label") ||
        combined.contains("preparing to ship")
    ) {
        return ShippingStatus.label_created
    }
    if (combined.contains("shipped")) return ShippingStatus.shipped
    return ShippingStatus.unknown
}

private val delivery_patterns = listOf(
    Regex("(?:estimated|expected)\\s+delivery[:\\s]*([A-Za-z]+,?\\s*[A-Za-z]+\\s+\\d{1,2}(?:,?\\s+\\d{4})?)", RegexOption.IGNORE_CASE),
    Regex("arriving\\s+(?:by\\s+)?([A-Za-z]+,?\\s*[A-Za-z]+\\s+\\d{1,2})", RegexOption.IGNORE_CASE),
    Regex("deliver(?:ed|y)\\s+(?:by\\s+)?([A-Za-z]+,?\\s*[A-Za-z]+\\s+\\d{1,2})", RegexOption.IGNORE_CASE),
)

private val shipped_patterns = listOf(
    Regex("shipped\\s+(?:on\\s+)?([A-Za-z]+,?\\s*[A-Za-z]+\\s+\\d{1,2}(?:,?\\s+\\d{4})?)", RegexOption.IGNORE_CASE),
    Regex("ship\\s+date[:\\s]*([A-Za-z]+\\s+\\d{1,2},?\\s+\\d{4})", RegexOption.IGNORE_CASE),
)

private val delivered_patterns = listOf(
    Regex("delivered\\s+(?:on\\s+)?([A-Za-z]+,?\\s*[A-Za-z]+\\s+\\d{1,2}(?:,?\\s+\\d{4})?)", RegexOption.IGNORE_CASE),
    Regex("delivery\\s+date[:\\s]*([A-Za-z]+\\s+\\d{1,2},?\\s+\\d{4})", RegexOption.IGNORE_CASE),
)

fun extract_estimated_delivery(text: String): String? =
    clean_text_field(extract_date(text, delivery_patterns))

private val destination_regex =
    Regex("(?:deliver(?:ed|ing)?\\s+to|destination)[:\\s]*([^\\n]{10,100})", RegexOption.IGNORE_CASE)

fun extract_shipping_details(
    subject: String,
    body: String,
    html: String?,
    from_email: String,
): ExtractedShippingDetails {
    val signals = ArrayList<String>()
    val clean_subject = decode_basic_entities(subject)
    val clean_body = normalize_email_text(body)
    val raw_html = html ?: if (looks_like_html_body(body)) body else null

    val carrier = detect_carrier_from_email(from_email, clean_subject, clean_body)
    if (carrier != null) signals.add("carrier:" + carrier.id)

    var tracking_url = extract_tracking_url(clean_body, raw_html)
    val result = extract_tracking_number(clean_body, carrier, tracking_url)
    val tracking_number = result?.number
    val detected_carrier = result?.carrier ?: carrier
    if (tracking_number != null) signals.add("tracking:$tracking_number")

    if (tracking_url == null && tracking_number != null && detected_carrier != null) {
        val base_url = detected_carrier.tracking_base_url
        if (base_url.isNotEmpty()) tracking_url = base_url + tracking_number
    }

    val status = detect_shipping_status(clean_subject, clean_body)
    signals.add("status:" + status.name)

    val estimated_delivery = clean_text_field(extract_date(clean_body, delivery_patterns))
    val shipped_date = clean_text_field(extract_date(clean_body, shipped_patterns))
    val delivery_date = if (status == ShippingStatus.delivered) {
        clean_text_field(extract_date(clean_body, delivered_patterns))
    } else {
        null
    }
    val destination = clean_text_field(destination_regex.find(clean_body)?.groupValues?.get(1)?.take(100))
    val carrier_name = detected_carrier?.display_name

    return ExtractedShippingDetails(
        tracking_number = tracking_number,
        carrier = detected_carrier,
        carrier_name = carrier_name,
        tracking_url = tracking_url,
        status = status,
        estimated_delivery = estimated_delivery,
        shipped_date = shipped_date,
        delivery_date = delivery_date,
        origin = null,
        destination = destination,
        items_shipped = emptyList(),
        raw_signals = signals,
    )
}

private val shipping_indicators = listOf(
    Regex("\\bshipped\\b", RegexOption.IGNORE_CASE),
    Regex("\\btracking\\s*(?:#|number)?\\b", RegexOption.IGNORE_CASE),
    Regex("\\bout\\s+for\\s+delivery\\b", RegexOption.IGNORE_CASE),
    Regex("\\bdelivered\\b", RegexOption.IGNORE_CASE),
    Regex("\\bin\\s+transit\\b", RegexOption.IGNORE_CASE),
    Regex("\\bhas\\s+shipped\\b", RegexOption.IGNORE_CASE),
    Regex("\\bshipment\\s+(?:update|notification)\\b", RegexOption.IGNORE_CASE),
    Regex("\\bpackage\\s+(?:update|notification|shipped|delivered)\\b", RegexOption.IGNORE_CASE),
    Regex("\\bestimated\\s+delivery\\b", RegexOption.IGNORE_CASE),
    Regex("\\barriving\\s+(?:today|tomorrow|soon)\\b", RegexOption.IGNORE_CASE),
    Regex("\\b1Z[A-Z0-9]{16}\\b", RegexOption.IGNORE_CASE),
    Regex("\\bTBA\\d{12,15}\\b", RegexOption.IGNORE_CASE),
)

fun is_shipping_email(subject: String, body: String): Boolean {
    val combined = (subject + " " + body).lowercase(Locale.ROOT)
    var matches = 0
    for (pattern in shipping_indicators) {
        if (pattern.containsMatchIn(combined)) {
            matches += 1
            if (matches >= 2) return true
        }
    }
    return false
}

fun extract_email_details(
    subject: String,
    body_text: String,
    body_html: String?,
    from_email: String,
    from_name: String,
    locale: Locale = Locale.getDefault(),
): EmailExtractionResult {
    val clean_subject = decode_basic_entities(subject)
    val clean_text = normalize_email_text(body_text, body_html)
    val is_purchase = is_purchase_email(clean_subject, clean_text)
    val is_shipping = is_shipping_email(clean_subject, clean_text)
    return EmailExtractionResult(
        has_purchase_details = is_purchase,
        has_shipping_details = is_shipping,
        purchase = if (is_purchase) extract_purchase_details(clean_subject, clean_text, from_email, from_name, locale) else null,
        shipping = if (is_shipping) extract_shipping_details(clean_subject, clean_text, body_html, from_email) else null,
        extracted_at = System.currentTimeMillis(),
    )
}
