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
private const val id_value = "([A-Za-z0-9](?:[\\w-]|\\.(?=[\\w-])){3,29})"

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

private class item_pattern(val regex: Regex, val price_is_line_total: Boolean)

private val item_patterns = listOf(
    item_pattern(Regex("(\\d+)\\s*x\\s+(.+?)\\s*[-–]\\s*([\$€£¥₹]?\\s*[\\d,.]+)", RegexOption.IGNORE_CASE), false),
    item_pattern(Regex("(.+?)\\s*\\(Qty:\\s*(\\d+)\\)\\s*([\$€£¥₹]?\\s*[\\d,.]+)", RegexOption.IGNORE_CASE), false),
    item_pattern(
        Regex("Item:\\s*(.+?)(?:\\s*Qty:\\s*(\\d+))?\\s*Price:\\s*([\$€£¥₹]?\\s*[\\d,.]+)", RegexOption.IGNORE_CASE),
        false,
    ),
    item_pattern(
        Regex(
            "(?:^|\\n)[ \\t]*([^\\n\$€£¥₹]{2,160}?)\\s+[×x]\\s*(\\d{1,3})\\s+([\$€£¥₹]\\s*[\\d,.]+)[ \\t]*(?=\\n|$)",
            RegexOption.IGNORE_CASE,
        ),
        true,
    ),
)

private val all_digits_regex = Regex("^\\d+$")
private val item_summary_word = Regex("\\b(?:sub\\s?total|total|amount|tax|vat|shipping|discount)\\b", RegexOption.IGNORE_CASE)

private fun extract_items_from_body(body: String, fallback_currency: String, locale: Locale): List<ExtractedItem> {
    val items = ArrayList<ExtractedItem>()
    for (pattern in item_patterns) {
        for (match in pattern.regex.findAll(body)) {
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
            val price = parse_amount(price_str, fallback_currency, locale)
            if (item_name != null && item_name.length > 2 && item_name.length < 200 && !item_summary_word.containsMatchIn(item_name)) {
                val unit_price = price?.let {
                    if (!pattern.price_is_line_total || quantity <= 1) {
                        it
                    } else {
                        val unit = it.value / quantity
                        it.copy(value = unit, formatted = format_amount(unit, it.currency, locale))
                    }
                }
                val total_price = price?.let {
                    if (pattern.price_is_line_total || quantity <= 1) {
                        it
                    } else {
                        val total = it.value * quantity
                        it.copy(value = total, formatted = format_amount(total, it.currency, locale))
                    }
                }
                items.add(ExtractedItem(item_name, quantity, unit_price, total_price))
            }
        }
    }
    return items
}

private val merchant_role_suffix_regex = Regex(
    "(?:\\s+(?:order|shipping|notification|update|receipt|billing|payment|team|support)s?)+\\s*$",
    RegexOption.IGNORE_CASE,
)

private val payment_processor_domains = setOf(
    "stripe.com",
    "paypal.com",
    "paypal.de",
    "squareup.com",
    "square.com",
    "shopify.com",
    "shopifyemail.com",
    "gumroad.com",
    "paddle.com",
    "paddle.net",
    "lemonsqueezy.com",
    "fastspring.com",
    "braintreegateway.com",
    "adyen.com",
    "mollie.com",
    "klarna.com",
    "chargebee.com",
    "recurly.com",
    "2checkout.com",
    "checkout.com",
)

internal fun is_payment_processor_domain(domain: String): Boolean {
    val lower = domain.trim().lowercase(Locale.ROOT)
    if (lower.isEmpty()) return false
    return payment_processor_domains.any { lower == it || lower.endsWith(".$it") }
}

private val generic_merchant_words = setOf(
    "order", "orders", "payment", "purchase", "receipt", "invoice", "subscription", "online", "recent",
    "latest", "new", "monthly", "annual", "us", "store", "shop", "the", "your",
)

private const val merchant_tail = "([^\\n#\\[\\(<>@:]{2,60}?)"
private const val merchant_stop =
    "(?=\\s*(?:[\\[\\(#:,.!|\\-]|\\bis\\b|\\bhas\\b|\\bfor\\b|\\bon\\b|\\bwas\\b|\\n|\$))"

private val subject_merchant_patterns = listOf(
    Regex("\\breceipt\\s+from\\s+$merchant_tail$merchant_stop", RegexOption.IGNORE_CASE),
    Regex(
        "\\b(?:order|purchase|invoice|payment)\\s+(?:confirmation\\s+|receipt\\s+)?(?:from|with|at|to)\\s+$merchant_tail$merchant_stop",
        RegexOption.IGNORE_CASE,
    ),
    Regex("\\byour\\s+$merchant_tail\\s+(?:receipt|invoice|order\\s+confirmation|order|purchase)\\b", RegexOption.IGNORE_CASE),
    Regex(
        "\\bthanks?\\s+(?:you\\s+)?for\\s+(?:your\\s+)?(?:order|purchase|shopping|payment)\\s+(?:from|with|at|to)\\s+$merchant_tail$merchant_stop",
        RegexOption.IGNORE_CASE,
    ),
)

private val body_merchant_patterns = listOf(
    Regex("\\breceipt\\s+from\\s+$merchant_tail$merchant_stop", RegexOption.IGNORE_CASE),
    Regex(
        "\\bthanks?\\s+(?:you\\s+)?for\\s+(?:your\\s+)?(?:order|purchase|shopping|payment)\\s+(?:from|with|at|to)\\s+$merchant_tail$merchant_stop",
        RegexOption.IGNORE_CASE,
    ),
    Regex(
        "\\b(?:order|purchase|invoice|payment)\\s+(?:confirmation\\s+|receipt\\s+)?(?:from|with|at|to)\\s+$merchant_tail$merchant_stop",
        RegexOption.IGNORE_CASE,
    ),
    Regex("\\bsold\\s+by\\s+$merchant_tail$merchant_stop", RegexOption.IGNORE_CASE),
    Regex(
        "\\b(?:payment\\s+of|paid|sent)\\s+(?:\\S+\\s+){1,3}?to\\s+$merchant_tail$merchant_stop",
        RegexOption.IGNORE_CASE,
    ),
)

private val whitespace_run_regex = Regex("\\s+")
private val merchant_leading_junk = Regex("^[\\s\"'“”*_-]+")
private val merchant_trailing_junk = Regex("[\\s\"'“”*_.,;:!-]+$")
private val merchant_rejected_content = Regex("https?:|www\\.|@|[<>]", RegexOption.IGNORE_CASE)
private val merchant_date_like = Regex(
    "^(?:\\d|(?:jan|feb|mar|apr|may|jun|jul|aug|sep|sept|oct|nov|dec)[a-z]*\\.?\\s+\\d)",
    RegexOption.IGNORE_CASE,
)

internal fun tidy_merchant_candidate(raw: String?): String? {
    if (raw == null) return null
    val value = raw
        .replace(whitespace_run_regex, " ")
        .replace(merchant_leading_junk, "")
        .replace(merchant_trailing_junk, "")
    if (value.length < 2 || value.length > 60) return null
    if (merchant_rejected_content.containsMatchIn(value)) return null
    if (merchant_date_like.containsMatchIn(value)) return null
    if (value.none { it.isLetter() }) return null
    val words = value.lowercase(Locale.ROOT).split(' ')
    if (words.all { it in generic_merchant_words }) return null
    return value
}

internal fun merchant_from_sender(from_email: String, from_name: String): String? {
    val name = from_name.trim()
    val lower_name = name.lowercase(Locale.ROOT)
    if (name.isNotEmpty() && !lower_name.contains("noreply") && !lower_name.contains("no-reply") && !name.contains('@')) {
        val stripped = name.replace(merchant_role_suffix_regex, "").trim()
        return stripped.ifEmpty { name }
    }
    val domain = from_email.substringAfter('@', "")
    if (domain.isNotEmpty()) {
        val parts = domain.split('.')
        if (parts.size >= 2) {
            val label = parts[parts.size - 2]
            return label.replaceFirstChar { it.uppercaseChar() }
        }
    }
    return name.ifBlank { null }
}

internal fun extract_merchant_name(from_email: String, from_name: String): String? =
    merchant_from_sender(from_email, from_name)

private val forwarded_from_pattern = Regex(
    "(?:^|\\n)[ \\t>*]*(?:from|von|de|da|van)\\s*:\\s*\\*{0,2}\\s*([^\\n<]{0,80}?)\\s*<?([\\w.+-]+@[\\w-]+(?:\\.[\\w-]+)+)>?",
    RegexOption.IGNORE_CASE,
)
private val forwarded_subject_pattern = Regex(
    "(?:^|\\n)[ \\t>*]*(?:subject|betreff|objet|asunto|assunto|onderwerp)\\s*:\\s*\\*{0,2}\\s*([^\\n]{1,200})",
    RegexOption.IGNORE_CASE,
)
private val forwarded_marker_pattern = Regex(
    "(?:^|\\n)[ \\t>*]*(?:sent|date|datum|envoyé|enviado|to|an|à|para|subject|betreff|objet|asunto|assunto)\\s*:",
    RegexOption.IGNORE_CASE,
)
private val reply_prefix_pattern = Regex("^(?:\\s*(?:re|fw|fwd|aw|wg|tr|rv|enc)\\s*:\\s*)+", RegexOption.IGNORE_CASE)
private val surrounding_quotes_regex = Regex("^[\"'“”]+|[\"'“”]+$")

private const val forwarded_block_length = 600

internal data class ForwardedOrigin(
    val from_email: String,
    val from_name: String,
    val subject: String?,
)

internal fun strip_reply_prefixes(subject: String): String = subject.replace(reply_prefix_pattern, "").trim()

internal fun detect_forwarded_origin(body: String): ForwardedOrigin? {
    val from_match = forwarded_from_pattern.find(body) ?: return null
    val start = from_match.range.first
    val block = body.substring(start, minOf(body.length, start + forwarded_block_length))
    val after_from = block.substring(minOf(block.length, from_match.value.length))
    if (!forwarded_marker_pattern.containsMatchIn(after_from)) return null
    val from_name = from_match.groupValues[1].trim().replace(surrounding_quotes_regex, "").trim()
    val from_email = from_match.groupValues[2].lowercase(Locale.ROOT)
    val subject = forwarded_subject_pattern.find(block)
        ?.groupValues?.get(1)?.trim()
        ?.let { strip_reply_prefixes(it) }
        ?.ifEmpty { null }
    return ForwardedOrigin(from_email, from_name, subject)
}

private const val merchant_body_scan_length = 6000

private fun first_merchant_match(text: String, patterns: List<Regex>): String? {
    for (pattern in patterns) {
        for (match in pattern.findAll(text)) {
            tidy_merchant_candidate(match.groupValues[1])?.let { return it }
        }
    }
    return null
}

internal fun resolve_merchant(
    subject: String,
    body: String,
    from_email: String,
    from_name: String,
    forwarded: ForwardedOrigin?,
): String? {
    val origin_email = (forwarded?.from_email ?: from_email).lowercase(Locale.ROOT)
    val origin_name = if (forwarded != null) forwarded.from_name else from_name
    val subjects = listOfNotNull(strip_reply_prefixes(subject), forwarded?.subject)
    for (candidate_subject in subjects) {
        first_merchant_match(candidate_subject, subject_merchant_patterns)?.let { return it }
    }
    first_merchant_match(body.take(merchant_body_scan_length), body_merchant_patterns)?.let { return it }
    val domain = origin_email.substringAfter('@', "")
    if (is_payment_processor_domain(domain)) return null
    return merchant_from_sender(origin_email, origin_name)
}

private val subject_order_kind = Regex("\\border\\s*(?:#|no\\.?|num(?:ber)?|id)\\b", RegexOption.IGNORE_CASE)
private val subject_receipt_kind = Regex("\\b(?:receipt|invoice|payment|amount\\s+paid)\\b", RegexOption.IGNORE_CASE)
private val head_order_kind = Regex("\\border\\s*(?:#|no\\.?|num(?:ber)?|id|confirm)", RegexOption.IGNORE_CASE)
private val head_receipt_kind = Regex(
    "\\b(?:receipt|invoice|amount\\s+paid|date\\s+paid|you\\s+paid|paid\\s+with|charged\\s+to)\\b",
    RegexOption.IGNORE_CASE,
)

private const val document_kind_head_length = 1500

const val document_kind_order = "order"
const val document_kind_receipt = "receipt"

internal fun detect_document_kind(subject: String, body: String): String {
    val head = subject + "\n" + body.take(document_kind_head_length)
    if (subject_order_kind.containsMatchIn(subject)) return document_kind_order
    if (subject_receipt_kind.containsMatchIn(subject)) return document_kind_receipt
    if (head_order_kind.containsMatchIn(head)) return document_kind_order
    if (head_receipt_kind.containsMatchIn(head)) return document_kind_receipt
    return document_kind_order
}

private val receipt_link_text = Regex(
    "^(?:view|download|see|open|print)\\s+(?:your\\s+|the\\s+|my\\s+)?(?:receipt|invoice|order)\\b",
    RegexOption.IGNORE_CASE,
)
private val anchor_pattern = Regex(
    "<a\\b[^>]*?href\\s*=\\s*[\"']([^\"']+)[\"'][^>]*>([\\s\\S]*?)</a>",
    RegexOption.IGNORE_CASE,
)
private val bare_https_url = Regex("https://[^\\s<>\"']+")
private val bare_url_trailing_junk = Regex("[),.;'\"\\]]+$")
private val html_tag_regex = Regex("<[^>]+>")

private const val receipt_url_max_length = 2048
private const val receipt_markup_scan_length = 200_000

internal fun safe_https_url(raw: String): java.net.URI? {
    val decoded = decode_basic_entities(raw).trim()
    if (decoded.isEmpty() || decoded.length > receipt_url_max_length) return null
    val uri = runCatching { java.net.URI(decoded) }.getOrNull() ?: return null
    if (!uri.scheme.equals("https", ignoreCase = true)) return null
    if (uri.host.isNullOrEmpty()) return null
    if (uri.rawUserInfo != null) return null
    return uri
}

internal fun registrable_domain(host: String): String {
    val parts = host.lowercase(Locale.ROOT).trimEnd('.').split('.')
    return if (parts.size >= 2) parts.takeLast(2).joinToString(".") else parts.joinToString(".")
}

private fun host_matches(host: String, domain: String): Boolean = host == domain || host.endsWith(".$domain")

internal fun is_allowlisted_receipt_url(uri: java.net.URI): Boolean {
    val host = uri.host?.lowercase(Locale.ROOT) ?: return false
    val path = uri.path.orEmpty()
    return when {
        host_matches(host, "pay.stripe.com") -> path.startsWith("/receipts/")
        host_matches(host, "invoice.stripe.com") -> path.startsWith("/i/")
        host_matches(host, "squareup.com") -> path.startsWith("/receipt/")
        host_matches(host, "paypal.com") -> path.startsWith("/activity/") || path.startsWith("/myaccount/transactions/")
        else -> false
    }
}

fun extract_receipt_url(body: String, html: String?, sender_emails: List<String?>): String? {
    val sender_domains = sender_emails
        .mapNotNull { it?.substringAfter('@', "")?.trim()?.lowercase(Locale.ROOT)?.ifEmpty { null } }
        .map { registrable_domain(it) }
        .toSet()
    val markup = (html?.takeIf { it.isNotEmpty() } ?: body.takeIf { looks_like_html_body(body) })
        ?.take(receipt_markup_scan_length)
    if (markup != null) {
        for (match in anchor_pattern.findAll(markup)) {
            val uri = safe_https_url(match.groupValues[1]) ?: continue
            if (is_allowlisted_receipt_url(uri)) return uri.toString()
            val text = decode_basic_entities(match.groupValues[2].replace(html_tag_regex, " "))
                .replace(whitespace_run_regex, " ")
                .trim()
            if (!receipt_link_text.containsMatchIn(text)) continue
            if (registrable_domain(uri.host) in sender_domains) return uri.toString()
        }
    }
    val plain = if (looks_like_html_body(body)) html_to_plain_text(body) else body
    for (match in bare_https_url.findAll(plain)) {
        val uri = safe_https_url(match.value.replace(bare_url_trailing_junk, "")) ?: continue
        if (is_allowlisted_receipt_url(uri)) return uri.toString()
    }
    return null
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
    Regex("(?:order|purchase|payment)\\s+date[:\\s]*([A-Za-z]+\\s+\\d{1,2},?\\s+\\d{4})", RegexOption.IGNORE_CASE),
    Regex("(?:ordered|purchased)\\s+on[:\\s]*([A-Za-z]+\\s+\\d{1,2},?\\s+\\d{4})", RegexOption.IGNORE_CASE),
    Regex("(?:date\\s+paid|paid\\s+on)[:\\s]*([A-Za-z]+\\s+\\d{1,2},?\\s+\\d{4})", RegexOption.IGNORE_CASE),
    Regex("date[:\\s]*(\\d{1,2}/\\d{1,2}/\\d{2,4})", RegexOption.IGNORE_CASE),
)

fun extract_purchase_details(
    subject: String,
    body: String,
    from_email: String,
    from_name: String,
    locale: Locale = Locale.getDefault(),
    body_html: String? = null,
): ExtractedPurchaseDetails {
    val signals = ArrayList<String>()
    val clean_subject = decode_basic_entities(subject)
    val clean_body = normalize_email_text(body, body_html)
    val forwarded = detect_forwarded_origin(clean_body)
    if (forwarded != null) signals.add("forwarded_from:" + forwarded.from_email)
    val effective_subject = forwarded?.subject ?: strip_reply_prefixes(clean_subject)
    val combined = effective_subject + "\n" + clean_body

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

    val merchant_name = clean_text_field(resolve_merchant(clean_subject, clean_body, from_email, from_name, forwarded))
    val merchant_source = (forwarded?.from_email ?: from_email).trim().lowercase(Locale.ROOT)
    val confirmation_number = clean_identifier(confirmation_number_pattern.find(combined)?.groupValues?.get(1))
    val transaction_id = clean_identifier(transaction_id_pattern.find(combined)?.groupValues?.get(1))
    val receipt_url = extract_receipt_url(
        body = clean_body,
        html = body_html?.takeIf { it.isNotEmpty() } ?: body.takeIf { looks_like_html_body(body) },
        sender_emails = listOf(from_email, forwarded?.from_email),
    )

    return ExtractedPurchaseDetails(
        order_id = order_id,
        order_date = order_date,
        merchant_name = merchant_name,
        document_kind = detect_document_kind(effective_subject, clean_body),
        merchant_source = merchant_source,
        receipt_url = receipt_url,
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

private val order_url_keywords = listOf("order", "purchase", "invoice", "receipt")

private val order_url_trailing_junk = Regex("""['">\]).,]+$""")

private val order_url_image_path = Regex("""\.(?:png|jpe?g|gif|svg|webp|bmp|ico)(?:[?#].*)?$""", RegexOption.IGNORE_CASE)

private val order_url_excluded_words = listOf("track", "unsubscribe", "preferences", "opt-out", "optout")

private val order_link_view_phrase = Regex(
    """\b(?:view|see|open|download|print|get)\s+(?:your\s+|the\s+|this\s+|my\s+)?(?:full\s+)?(?:order|receipt|invoice|purchase)""",
    RegexOption.IGNORE_CASE,
)

private val order_link_details_phrase = Regex(
    """\b(?:order|receipt|invoice|purchase)\s+(?:details|summary|status|history)\b""",
    RegexOption.IGNORE_CASE,
)

private val order_link_keyword = Regex("""\b(?:order|receipt|invoice|purchase)s?\b""", RegexOption.IGNORE_CASE)

private val order_link_browser_phrase = Regex(
    """\bview\s+(?:it\s+|this\s+(?:email\s+|message\s+)?)?(?:in|on)\s+(?:your\s+|a\s+)?(?:web\s+)?browser\b""",
    RegexOption.IGNORE_CASE,
)

private val order_link_url_keyword = Regex("""(?:order|receipt|invoice|purchase)""", RegexOption.IGNORE_CASE)

private fun is_hosted_receipt_url(uri: java.net.URI): Boolean {
    val host = uri.host?.lowercase(Locale.ROOT) ?: return false
    val path = uri.path?.lowercase(Locale.ROOT).orEmpty()
    return when (host) {
        "pay.stripe.com" -> path.startsWith("/receipts/") || path.startsWith("/invoice/")
        "invoice.stripe.com" -> path.startsWith("/i/") || path.contains("invoice") || path.contains("receipt")
        else -> false
    }
}

private fun is_usable_order_url(url: String): Boolean {
    val lower = url.lowercase(Locale.ROOT)
    if (!lower.startsWith("https://") && !lower.startsWith("http://")) return false
    if (order_url_excluded_words.any { lower.contains(it) }) return false
    val path = runCatching { java.net.URI(url).path }.getOrNull().orEmpty()
    return !order_url_image_path.containsMatchIn(path)
}

internal fun score_order_link(href: String, text: String): Int {
    if (!is_usable_order_url(href)) return 0
    val label = text.replace(Regex("""\s+"""), " ").trim()
    if (label.isEmpty()) return 0
    val lower_label = label.lowercase(Locale.ROOT)
    if (order_url_excluded_words.any { lower_label.contains(it) }) return 0
    val uri = runCatching { java.net.URI(href) }.getOrNull() ?: return 0
    val path_and_query = (uri.rawPath.orEmpty() + "?" + uri.rawQuery.orEmpty())
    var score = 0
    if (is_hosted_receipt_url(uri)) score += 100
    score += when {
        order_link_view_phrase.containsMatchIn(label) -> 80
        order_link_details_phrase.containsMatchIn(label) -> 70
        order_link_keyword.containsMatchIn(label) -> 50
        order_link_browser_phrase.containsMatchIn(label) -> 40
        else -> 0
    }
    if (order_link_url_keyword.containsMatchIn(path_and_query)) score += 30
    return score
}

private fun select_order_link_from_html(html: String): String? {
    val document = runCatching { org.jsoup.Jsoup.parse(html.take(link_scan_max_length)) }.getOrNull() ?: return null
    var best: String? = null
    var best_score = 0
    for (anchor in document.select("a[href]")) {
        val href = anchor.attr("href").trim()
        val score = score_order_link(href, anchor.text())
        if (score > best_score) {
            best = href
            best_score = score
        }
    }
    return best
}

fun extract_order_url(body: String, html: String?): String? {
    val body_is_html = looks_like_html_body(body)
    val markup = html?.takeIf { it.isNotEmpty() } ?: body.takeIf { body_is_html }
    if (markup != null) {
        select_order_link_from_html(markup)?.let { return it }
    }
    val plain = if (body_is_html) html_to_plain_text(body) else body
    for (token in url_tokens(plain)) {
        if (order_url_keywords.none { contains_after_scheme(token, it) }) continue
        val url = token.replace(order_url_trailing_junk, "")
        if (!is_usable_order_url(url)) continue
        return url
    }
    return null
}

private val strong_purchase_indicators = listOf(
    Regex("\\border\\s*(?:#|no\\.?|num(?:ber)?|id)\\s*[:#]?\\s*[A-Za-z0-9]*[0-9]", RegexOption.IGNORE_CASE),
    Regex("purchase\\s+(?:confirmation|complete|receipt)", RegexOption.IGNORE_CASE),
    Regex("thanks?\\s+(?:you\\s+)?for\\s+(?:your\\s+)?(?:[A-Za-z.]+\\s+){0,2}(?:order|purchase|booking)\\b", RegexOption.IGNORE_CASE),
    Regex("receipt\\s+(?:for|from)", RegexOption.IGNORE_CASE),
    Regex("payment\\s+(?:receipt|confirmation|successful)", RegexOption.IGNORE_CASE),
    Regex("transaction\\s+(?:receipt|complete)", RegexOption.IGNORE_CASE),
    Regex("you\\s+(?:bought|purchased|paid)", RegexOption.IGNORE_CASE),
    Regex("order\\s+(?:placed|confirmed)", RegexOption.IGNORE_CASE),
    Regex("\\bitems?\\s+(?:ordered|purchased)\\b", RegexOption.IGNORE_CASE),
)

private val supporting_purchase_indicators = listOf(
    money_after("\\border\\s+total\\b"),
    money_after("\\bgrand\\s+total\\b"),
    money_after("\\bamount\\s+(?:paid|charged|due)\\b"),
    money_after("\\bsub\\s?total\\b"),
    money_after("\\btotals?\\b"),
    card_last_four_regex,
)

fun is_purchase_email(subject: String, body: String): Boolean {
    val combined = (subject + " " + body).lowercase(Locale.ROOT)
    var strong = 0
    for (pattern in strong_purchase_indicators) {
        if (pattern.containsMatchIn(combined)) {
            strong += 1
            if (strong >= 2) return true
        }
    }
    if (strong == 0) return false
    return supporting_purchase_indicators.any { it.containsMatchIn(combined) }
}
