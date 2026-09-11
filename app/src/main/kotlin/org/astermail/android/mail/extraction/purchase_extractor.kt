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

import org.astermail.android.mail.html_to_plain_text
import org.astermail.android.mail.looks_like_html_body
import java.text.NumberFormat
import java.util.Locale
import kotlin.math.abs
import kotlin.math.max

private val basic_entity_regex = Regex("&(?:amp|lt|gt|quot|#39|apos|nbsp);")

private val basic_entities = mapOf(
    "&amp;" to "&",
    "&lt;" to "<",
    "&gt;" to ">",
    "&quot;" to "\"",
    "&#39;" to "'",
    "&apos;" to "'",
    "&nbsp;" to " ",
)

internal fun decode_basic_entities(text: String): String =
    text.replace(basic_entity_regex) { basic_entities[it.value] ?: it.value }

internal fun normalize_email_text(text: String, html: String? = null): String {
    val source = if (text.isNotBlank()) text else html.orEmpty()
    if (source.isEmpty()) return ""
    if (looks_like_html_body(source)) return html_to_plain_text(source)
    return decode_basic_entities(source)
}

private val markup_char_regex = Regex("[<>]")
private val entity_leftover_regex = Regex("&#?[a-z0-9]+;", RegexOption.IGNORE_CASE)

internal fun clean_text_field(value: String?): String? {
    val trimmed = value?.trim().orEmpty()
    if (trimmed.isEmpty()) return null
    if (markup_char_regex.containsMatchIn(trimmed)) return null
    if (entity_leftover_regex.containsMatchIn(trimmed)) return null
    return trimmed
}

private val currency_symbols = mapOf(
    "$" to "USD",
    "€" to "EUR",
    "£" to "GBP",
    "¥" to "JPY",
    "₹" to "INR",
    "C$" to "CAD",
    "A$" to "AUD",
    "R$" to "BRL",
)

private val currency_to_symbol = mapOf(
    "USD" to "$",
    "EUR" to "€",
    "GBP" to "£",
    "JPY" to "¥",
    "INR" to "₹",
    "CAD" to "C$",
    "AUD" to "A$",
    "BRL" to "R$",
)

private val zero_decimal_currencies = setOf("JPY", "KRW")

internal fun format_amount(value: Double, currency: String, locale: Locale): String {
    val digits = if (currency in zero_decimal_currencies) 0 else 2
    val formatter = NumberFormat.getNumberInstance(locale).apply {
        minimumFractionDigits = digits
        maximumFractionDigits = digits
    }
    val number = formatter.format(value)
    val symbol = currency_to_symbol[currency]
    return if (symbol != null) symbol + number else "$currency $number"
}

private const val id_marker = "(?:#|no\\.?|num(?:ber)?|id)"
private const val id_value = "([A-Za-z0-9][\\w-]{4,29})"

private fun labelled_id(label: String, marker: String = id_marker): Regex =
    Regex("\\b" + label + "\\s*" + marker + "\\s*[:#]?\\s*" + id_value, RegexOption.IGNORE_CASE)

private val order_id_patterns = listOf(
    Regex("\\border[:\\s]+([0-9]{3}-[0-9]{7}-[0-9]{7})\\b", RegexOption.IGNORE_CASE),
    labelled_id("order"),
    Regex("\\border\\s*[:#]\\s*" + id_value, RegexOption.IGNORE_CASE),
    labelled_id("confirmation", "(?:#|no\\.?|num(?:ber)?|code)"),
    labelled_id("invoice"),
    labelled_id("receipt"),
    labelled_id("transaction"),
)

private val confirmation_number_pattern = labelled_id("confirmation", "(?:#|no\\.?|num(?:ber)?|code)")
private val transaction_id_pattern = labelled_id("transaction")

private const val currency_token = "C\\$|A\\$|R\\$|[\$€£¥₹]|USD|EUR|GBP|JPY|INR|CAD|AUD|CHF"
private const val number_token = "[0-9][0-9,]*(?:\\.[0-9]{1,2})?(?![0-9])"
private const val money_token =
    "(?:(?:" + currency_token + ")\\s*)?(?:" + number_token + ")(?:\\s*(?:" + currency_token + "))?"

private val amount_pattern = Regex("(" + currency_token + ")?\\s*(" + number_token + ")\\s*(" + currency_token + ")?")
private val currency_scan = Regex(currency_token, RegexOption.IGNORE_CASE)
private val thousands_regex = Regex(",")

internal fun money_after(label: String): Regex =
    Regex(label + "\\s*[:=-]?\\s*-?\\s*(" + money_token + ")", RegexOption.IGNORE_CASE)

internal fun parse_amount(text: String, fallback_currency: String, locale: Locale): ExtractedAmount? {
    val match = amount_pattern.find(text) ?: return null
    val prefix = match.groupValues[1]
    val value_str = match.groupValues[2]
    val suffix = match.groupValues[3]
    val symbol = prefix.ifEmpty { suffix }.uppercase(Locale.ROOT)
    val currency = if (symbol.isEmpty()) fallback_currency else currency_symbols[symbol] ?: symbol
    val value = value_str.replace(thousands_regex, "").toDoubleOrNull() ?: return null
    return ExtractedAmount(value, currency, format_amount(value, currency, locale))
}

private val trailing_punctuation_regex = Regex("[.,;:]+$")
private val any_digit_regex = Regex("[0-9]")

internal fun clean_identifier(value: String?): String? {
    val cleaned = clean_text_field(value) ?: return null
    val trimmed = cleaned.replace(trailing_punctuation_regex, "")
    if (!any_digit_regex.containsMatchIn(trimmed)) return null
    return trimmed.ifEmpty { null }
}

private data class purchase_amounts(
    val subtotal: ExtractedAmount?,
    val tax: ExtractedAmount?,
    val shipping_cost: ExtractedAmount?,
    val discount: ExtractedAmount?,
    val total: ExtractedAmount?,
)

private fun reconcile_amounts(amounts: purchase_amounts): purchase_amounts {
    val total = amounts.total ?: return amounts
    val subtotal = amounts.subtotal ?: return amounts
    val tolerance = max(0.02, total.value * 0.005)
    val charges = subtotal.value + (amounts.tax?.value ?: 0.0) + (amounts.shipping_cost?.value ?: 0.0)
    val discount = amounts.discount
    if (discount != null) {
        if (abs(charges - discount.value - total.value) <= tolerance) return amounts
        if (abs(charges - total.value) <= tolerance) return amounts.copy(discount = null)
    }
    if (charges - (discount?.value ?: 0.0) > total.value + tolerance) {
        return purchase_amounts(null, null, null, null, total)
    }
    return amounts
}

internal fun detect_dominant_currency(text: String): String {
    val counts = HashMap<String, Int>()
    for (match in currency_scan.findAll(text)) {
        val symbol = match.value.uppercase(Locale.ROOT)
        val currency = currency_symbols[symbol] ?: symbol
        counts[currency] = (counts[currency] ?: 0) + 1
    }
    var winner = "USD"
    var best = 0
    for ((currency, count) in counts) {
        if (count > best) {
            winner = currency
            best = count
        }
    }
    return winner
}

private fun extract_amount_from_line(
    text: String,
    patterns: List<Regex>,
    fallback_currency: String,
    locale: Locale,
): ExtractedAmount? {
    for (pattern in patterns) {
        val match = pattern.find(text) ?: continue
        val amount_str = match.groups[1]?.value?.ifEmpty { null } ?: match.value
        return parse_amount(amount_str, fallback_currency, locale)
    }
    return null
}

internal fun extract_date(text: String, patterns: List<Regex>): String? {
    for (pattern in patterns) {
        val match = pattern.find(text) ?: continue
        return match.groups[1]?.value?.ifEmpty { null } ?: match.value
    }
    return null
}

private val item_patterns = listOf(
    Regex("(\\d+)\\s*x\\s+(.+?)\\s*[-–]\\s*([\$€£¥₹]?\\s*[\\d,.]+)", RegexOption.IGNORE_CASE),
    Regex("(.+?)\\s*\\(Qty:\\s*(\\d+)\\)\\s*([\$€£¥₹]?\\s*[\\d,.]+)", RegexOption.IGNORE_CASE),
    Regex("Item:\\s*(.+?)(?:\\s*Qty:\\s*(\\d+))?\\s*Price:\\s*([\$€£¥₹]?\\s*[\\d,.]+)", RegexOption.IGNORE_CASE),
)

private val all_digits_regex = Regex("^\\d+$")

private fun extract_items_from_body(body: String, fallback_currency: String, locale: Locale): List<ExtractedItem> {
    val items = ArrayList<ExtractedItem>()
    for (pattern in item_patterns) {
        for (match in pattern.findAll(body)) {
            val qty_or_name = match.groups[1]?.value.orEmpty()
            val name_or_qty = match.groups[2]?.value.orEmpty()
            val price_str = match.groups[3]?.value.orEmpty()
            val is_qty_first = all_digits_regex.matches(qty_or_name.trim())
            val item_name = clean_text_field(if (is_qty_first) name_or_qty else qty_or_name)
            val quantity = if (is_qty_first) {
                qty_or_name.trim().toIntOrNull() ?: 1
            } else {
                name_or_qty.trim().toIntOrNull() ?: 1
            }
            val unit_price = parse_amount(price_str, fallback_currency, locale)
            if (item_name != null && item_name.length > 2 && item_name.length < 200) {
                val total_price = unit_price?.let {
                    val total = it.value * quantity
                    it.copy(value = total, formatted = format_amount(total, it.currency, locale))
                }
                items.add(ExtractedItem(item_name, quantity, unit_price, total_price))
            }
        }
    }
    return items
}

private val merchant_role_suffix_regex =
    Regex("(?:\\s+(?:order|shipping|notification|update)s?)+\\s*$", RegexOption.IGNORE_CASE)

internal fun extract_merchant_name(from_email: String, from_name: String): String? {
    if (from_name.isNotEmpty() && !from_name.lowercase(Locale.ROOT).contains("noreply")) {
        val stripped = from_name.replace(merchant_role_suffix_regex, "").trim()
        return stripped.ifEmpty { from_name.trim() }
    }
    val domain = from_email.substringAfter('@', "")
    if (domain.isNotEmpty()) {
        val parts = domain.split('.')
        if (parts.size >= 2) {
            val label = parts[parts.size - 2]
            return label.replaceFirstChar { it.uppercaseChar() }
        }
    }
    return from_name.ifBlank { null }
}

private val total_patterns = listOf(
    money_after("grand\\s+total"),
    money_after("order\\s+total"),
    money_after("purchase\\s+total"),
    money_after("total\\s+charged"),
    money_after("amount\\s+(?:paid|charged|due)"),
    money_after("\\btotals?\\b"),
)

private val subtotal_patterns = listOf(
    money_after("\\bsub\\s?total\\b"),
    money_after("\\bitems?\\s+total\\b"),
)

private val tax_patterns = listOf(
    money_after("\\b(?:sales\\s+)?tax\\b"),
    money_after("\\bvat\\b"),
    money_after("\\bgst\\b"),
)

private val shipping_cost_patterns = listOf(
    money_after("\\bshipping(?:\\s*(?:&|and)\\s*handling)?\\b"),
    money_after("\\bdelivery\\s+fee\\b"),
)

private val discount_patterns = listOf(
    money_after("\\bdiscounts?\\b"),
    money_after("\\bsavings?\\b"),
    money_after("\\bcoupon\\b"),
    money_after("\\bpromo(?:tion)?\\s*(?:code)?\\s*(?:applied)?"),
)

private val card_last_four_regex = Regex("(?:card\\s+)?ending\\s+(?:in\\s+)?[*x]?(\\d{4})", RegexOption.IGNORE_CASE)
private val payment_method_regex =
    Regex("(?:payment\\s+method|paid\\s+with|charged\\s+to)[:\\s]*([^\\n]+)", RegexOption.IGNORE_CASE)

private val order_date_patterns = listOf(
    Regex("(?:order|purchase)\\s+date[:\\s]*([A-Za-z]+\\s+\\d{1,2},?\\s+\\d{4})", RegexOption.IGNORE_CASE),
    Regex("(?:ordered|purchased)\\s+on[:\\s]*([A-Za-z]+\\s+\\d{1,2},?\\s+\\d{4})", RegexOption.IGNORE_CASE),
    Regex("date[:\\s]*(\\d{1,2}/\\d{1,2}/\\d{2,4})", RegexOption.IGNORE_CASE),
)

fun extract_purchase_details(
    subject: String,
    body: String,
    from_email: String,
    from_name: String,
    locale: Locale = Locale.getDefault(),
): ExtractedPurchaseDetails {
    val signals = ArrayList<String>()
    val clean_subject = decode_basic_entities(subject)
    val clean_body = normalize_email_text(body)
    val combined = clean_subject + "\n" + clean_body

    var order_id: String? = null
    for (pattern in order_id_patterns) {
        val candidate = clean_identifier(pattern.find(combined)?.groups?.get(1)?.value) ?: continue
        order_id = candidate
        signals.add("order_id:$candidate")
        break
    }

    val currency = detect_dominant_currency(combined)
    val total = extract_amount_from_line(combined, total_patterns, currency, locale)
    if (total != null) signals.add("total:" + total.formatted)
    val subtotal = extract_amount_from_line(combined, subtotal_patterns, currency, locale)
    val tax = extract_amount_from_line(combined, tax_patterns, currency, locale)
    val shipping_cost = extract_amount_from_line(combined, shipping_cost_patterns, currency, locale)
    val discount = extract_amount_from_line(combined, discount_patterns, currency, locale)
    val amounts = reconcile_amounts(purchase_amounts(subtotal, tax, shipping_cost, discount, total))

    val card_last_four = card_last_four_regex.find(combined)?.groupValues?.get(1)
    if (card_last_four != null) signals.add("card:****$card_last_four")
    val payment_method = clean_text_field(payment_method_regex.find(combined)?.groupValues?.get(1))
    val order_date = clean_text_field(extract_date(combined, order_date_patterns))

    val items = extract_items_from_body(clean_body, currency, locale)
    if (items.isNotEmpty()) signals.add("items:" + items.size)

    val merchant_name = clean_text_field(extract_merchant_name(from_email, from_name))
    val confirmation_number = clean_identifier(confirmation_number_pattern.find(combined)?.groupValues?.get(1))
    val transaction_id = clean_identifier(transaction_id_pattern.find(combined)?.groupValues?.get(1))

    return ExtractedPurchaseDetails(
        order_id = order_id,
        order_date = order_date,
        merchant_name = merchant_name,
        items = items,
        subtotal = amounts.subtotal,
        tax = amounts.tax,
        shipping_cost = amounts.shipping_cost,
        discount = amounts.discount,
        total = amounts.total,
        payment_method = payment_method,
        card_last_four = card_last_four,
        billing_address = null,
        confirmation_number = confirmation_number,
        transaction_id = transaction_id,
        raw_signals = signals,
    )
}

private val order_url_patterns = listOf(
    Regex("""https?://[^\s<>"]+(?:order|purchase|invoice|receipt)[^\s<>"]*""", RegexOption.IGNORE_CASE),
)

private val order_url_trailing_junk = Regex("""['">\]).,]+$""")

fun extract_order_url(body: String, html: String?): String? {
    val content = if (!html.isNullOrEmpty()) html else body
    for (pattern in order_url_patterns) {
        for (match in pattern.findAll(content)) {
            val url = match.value.replace(order_url_trailing_junk, "")
            val lower = url.lowercase(Locale.ROOT)
            if (lower.contains("track") || lower.contains("unsubscribe")) continue
            return url
        }
    }
    return null
}

private val purchase_indicators = listOf(
    Regex("\\border\\s*(?:#|no\\.?|num(?:ber)?|id)\\s*[:#]?\\s*[A-Za-z0-9]*[0-9]", RegexOption.IGNORE_CASE),
    Regex("purchase\\s+(?:confirmation|complete|receipt)", RegexOption.IGNORE_CASE),
    Regex("thank\\s+you\\s+for\\s+(?:your\\s+)?(?:order|purchase)", RegexOption.IGNORE_CASE),
    Regex("receipt\\s+(?:for|from)", RegexOption.IGNORE_CASE),
    Regex("payment\\s+(?:receipt|confirmation|successful)", RegexOption.IGNORE_CASE),
    Regex("transaction\\s+(?:receipt|complete)", RegexOption.IGNORE_CASE),
    Regex("you\\s+(?:bought|purchased|paid)", RegexOption.IGNORE_CASE),
    Regex("order\\s+(?:placed|confirmed)", RegexOption.IGNORE_CASE),
    money_after("\\btotals?\\b"),
    money_after("\\bamount\\s+(?:paid|charged|due)\\b"),
)

fun is_purchase_email(subject: String, body: String): Boolean {
    val combined = (subject + " " + body).lowercase(Locale.ROOT)
    var matches = 0
    for (pattern in purchase_indicators) {
        if (pattern.containsMatchIn(combined)) {
            matches += 1
            if (matches >= 2) return true
        }
    }
    return false
}
