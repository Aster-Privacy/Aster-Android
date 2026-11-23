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

import java.text.NumberFormat
import java.util.Currency
import java.util.Locale

internal val iso_currency_codes = setOf(
    "AED", "AFN", "ALL", "AMD", "ANG", "AOA", "ARS", "AUD", "AWG", "AZN", "BAM", "BBD", "BDT", "BGN",
    "BHD", "BIF", "BMD", "BND", "BOB", "BRL", "BSD", "BTN", "BWP", "BYN", "BZD", "CAD", "CDF", "CHF",
    "CLP", "CNY", "COP", "CRC", "CUP", "CVE", "CZK", "DJF", "DKK", "DOP", "DZD", "EGP", "ERN", "ETB",
    "EUR", "FJD", "FKP", "GBP", "GEL", "GHS", "GIP", "GMD", "GNF", "GTQ", "GYD", "HKD", "HNL", "HTG",
    "HUF", "IDR", "ILS", "INR", "IQD", "IRR", "ISK", "JMD", "JOD", "JPY", "KES", "KGS", "KHR", "KMF",
    "KPW", "KRW", "KWD", "KYD", "KZT", "LAK", "LBP", "LKR", "LRD", "LSL", "LYD", "MAD", "MDL", "MGA",
    "MKD", "MMK", "MNT", "MOP", "MRU", "MUR", "MVR", "MWK", "MXN", "MYR", "MZN", "NAD", "NGN", "NIO",
    "NOK", "NPR", "NZD", "OMR", "PAB", "PEN", "PGK", "PHP", "PKR", "PLN", "PYG", "QAR", "RON", "RSD",
    "RUB", "RWF", "SAR", "SBD", "SCR", "SDG", "SEK", "SGD", "SHP", "SLE", "SOS", "SRD", "SSP", "STN",
    "SVC", "SYP", "SZL", "THB", "TJS", "TMT", "TND", "TOP", "TRY", "TTD", "TWD", "TZS", "UAH", "UGX",
    "USD", "UYU", "UZS", "VES", "VND", "VUV", "WST", "XAF", "XCD", "XOF", "XPF", "YER", "ZAR", "ZMW",
    "ZWG",
)

private val symbol_currencies = linkedMapOf(
    "US$" to "USD", "CA$" to "CAD", "C$" to "CAD", "AU$" to "AUD", "A$" to "AUD", "NZ$" to "NZD",
    "HK$" to "HKD", "SG$" to "SGD", "S$" to "SGD", "MX$" to "MXN", "R$" to "BRL", "NT$" to "TWD",
    "E£" to "EGP", "KSH" to "KES", "RM" to "MYR", "RP" to "IDR", "RS." to "INR", "RS" to "INR",
    "ZŁ" to "PLN", "KČ" to "CZK", "FT" to "HUF", "LEI" to "RON", "ЛВ." to "BGN", "ЛВ" to "BGN",
    "FR." to "CHF", "KR." to "SEK", "KR" to "SEK", "R" to "ZAR",
    "$" to "USD", "€" to "EUR", "£" to "GBP", "¥" to "JPY", "￥" to "JPY", "₹" to "INR", "₩" to "KRW",
    "₽" to "RUB", "₺" to "TRY", "₪" to "ILS", "₫" to "VND", "₱" to "PHP", "฿" to "THB", "₴" to "UAH",
    "₦" to "NGN", "₡" to "CRC", "₲" to "PYG", "₵" to "GHS", "₸" to "KZT", "₼" to "AZN", "₾" to "GEL",
    "₭" to "LAK", "₮" to "MNT", "৳" to "BDT",
)

private val ambiguous_families = mapOf(
    "$" to setOf("USD", "CAD", "AUD", "NZD", "SGD", "HKD", "MXN", "TWD", "ARS", "CLP", "COP", "UYU", "BSD", "BBD", "BZD", "BMD", "FJD", "JMD", "KYD", "LRD", "NAD", "SBD", "SRD", "TTD", "XCD", "GYD", "BND", "DOP", "CUP"),
    "KR" to setOf("SEK", "NOK", "DKK", "ISK"),
    "KR." to setOf("SEK", "NOK", "DKK", "ISK"),
    "¥" to setOf("JPY", "CNY"),
    "￥" to setOf("JPY", "CNY"),
    "RS" to setOf("INR", "PKR", "LKR", "NPR", "MUR", "SCR"),
    "RS." to setOf("INR", "PKR", "LKR", "NPR", "MUR", "SCR"),
)

private val tld_currencies = mapOf(
    "se" to "SEK", "no" to "NOK", "dk" to "DKK", "is" to "ISK", "fo" to "DKK", "gl" to "DKK",
    "ca" to "CAD", "au" to "AUD", "nz" to "NZD", "sg" to "SGD", "hk" to "HKD", "mx" to "MXN",
    "tw" to "TWD", "cn" to "CNY", "jp" to "JPY", "ar" to "ARS", "cl" to "CLP", "co" to "COP",
    "uy" to "UYU", "pk" to "PKR", "lk" to "LKR", "np" to "NPR", "in" to "INR", "us" to "USD",
    "pl" to "PLN", "cz" to "CZK", "hu" to "HUF", "ro" to "RON", "bg" to "BGN", "ch" to "CHF",
    "li" to "CHF", "za" to "ZAR", "br" to "BRL", "kr" to "KRW", "ru" to "RUB", "tr" to "TRY",
    "il" to "ILS", "vn" to "VND", "ph" to "PHP", "th" to "THB", "ua" to "UAH", "ng" to "NGN",
    "my" to "MYR", "id" to "IDR", "ke" to "KES", "eg" to "EGP", "kw" to "KWD", "bh" to "BHD",
    "om" to "OMR", "jo" to "JOD", "tn" to "TND", "ae" to "AED", "sa" to "SAR", "qa" to "QAR",
    "uk" to "GBP", "gb" to "GBP", "de" to "EUR", "fr" to "EUR", "es" to "EUR", "it" to "EUR",
    "nl" to "EUR", "be" to "EUR", "at" to "EUR", "fi" to "EUR", "ie" to "EUR", "pt" to "EUR",
    "gr" to "EUR", "sk" to "EUR", "si" to "EUR", "ee" to "EUR", "lv" to "EUR", "lt" to "EUR",
    "lu" to "EUR", "mt" to "EUR", "cy" to "EUR", "hr" to "EUR",
)

private val suffix_symbols = mapOf(
    "SEK" to "kr", "NOK" to "kr", "DKK" to "kr", "ISK" to "kr", "PLN" to "zł", "CZK" to "Kč",
    "HUF" to "Ft", "RON" to "lei", "BGN" to "лв", "CHF" to "CHF",
)

private val prefix_symbols = mapOf(
    "USD" to "$", "EUR" to "€", "GBP" to "£", "JPY" to "¥", "CNY" to "¥", "INR" to "₹", "KRW" to "₩",
    "CAD" to "CA$", "AUD" to "A$", "NZD" to "NZ$", "HKD" to "HK$", "SGD" to "S$", "MXN" to "MX$",
    "BRL" to "R$", "TWD" to "NT$", "RUB" to "₽", "TRY" to "₺", "ILS" to "₪", "VND" to "₫",
    "PHP" to "₱", "THB" to "฿", "UAH" to "₴", "NGN" to "₦", "ZAR" to "R", "MYR" to "RM",
    "IDR" to "Rp", "PKR" to "Rs", "LKR" to "Rs", "NPR" to "Rs", "EGP" to "E£", "KES" to "KSh",
)

private val symbol_alternation: String = symbol_currencies.keys
    .sortedByDescending { it.length }
    .joinToString("|") { key ->
        val body = key.map { c ->
            when {
                c.isLetter() -> "[" + c.lowercaseChar() + c.uppercaseChar() + "]"
                c.code > 127 -> c.toString()
                else -> "\\" + c
            }
        }.joinToString("")
        if (key.first().isLetter()) "(?<![\\p{L}])$body(?![\\p{L}])" else if (key.last().isLetter()) "$body(?![\\p{L}])" else body
    }

private val code_alternation: String =
    "(?<![\\p{L}])(?:" + iso_currency_codes.sorted().joinToString("|") + ")(?![\\p{L}])"

internal const val number_body =
    "(?:[0-9]{1,2}(?:,[0-9]{2})+,[0-9]{3}(?:\\.[0-9]{1,2})?" +
        "|[0-9]{1,3}(?:[,.'’\\u00A0\\u202F ][0-9]{3})+(?:[.,][0-9]{1,3})?" +
        "|[0-9]+(?:[.,][0-9]{1,3})?)(?![0-9])(?![0-9.,]*\\s*%)"

internal val currency_token: String = "(?:$code_alternation|$symbol_alternation)"

internal const val money_gap = "[\\s\\u00A0\\u202F]?"

private const val krona_dash = ":-"

internal val money_token: String =
    "(?:" + currency_token + money_gap + ")?" + number_body + "(?:" + money_gap + "(?:" + currency_token + "|" + krona_dash + "))?"

private val money_pattern = Regex(
    "(" + currency_token + ")?" + money_gap + "(" + number_body + ")(?:" + money_gap + "(" + currency_token + "|" + krona_dash + "))?",
)

private val dollar_family = ambiguous_families.getValue("$")

internal class currency_context(
    val dominant: String?,
    private val hinted: List<String>,
    private val locale_hint: String?,
) {
    fun resolve(raw_symbol: String): String {
        val trimmed = raw_symbol.trim()
        if (trimmed.isEmpty()) return dominant ?: ""
        val upper = if (trimmed == krona_dash) "KR" else trimmed.uppercase(Locale.ROOT)
        if (upper in iso_currency_codes) return upper
        val base = symbol_currencies[upper] ?: return dominant ?: ""
        val family = ambiguous_families[upper] ?: return base
        if (dominant != null && dominant in family) return dominant
        hinted.firstOrNull { it in family }?.let { return it }
        if (family !== dollar_family && locale_hint != null && locale_hint in family) return locale_hint
        return base
    }
}

private val word_like_codes = setOf(
    "ALL", "AMD", "ANG", "BAM", "BOB", "CUP", "DOP", "ERN", "GEL", "LAK", "MAD", "MOP", "NAD", "PEN",
    "SOS", "TOP", "TRY", "WST", "YER", "CVE", "SAR", "MRU", "STN",
)

private val separator_regex = Regex("[.,'’\u00A0\u202F ]")

private fun counts_as_evidence(raw: String, number: String): Boolean {
    val upper = raw.trim().uppercase(Locale.ROOT)
    if (upper.isEmpty()) return false
    val has_separator = separator_regex.containsMatchIn(number)
    if (upper in iso_currency_codes) return upper !in word_like_codes || has_separator
    if (upper.any { it.isLetter() }) return has_separator
    return true
}

private fun sender_tld_currency(from_email: String): String? {
    val host = from_email.substringAfterLast('@', "").trim().trimEnd('>', '.').lowercase(Locale.ROOT)
    if (host.isEmpty()) return null
    return tld_currencies[host.substringAfterLast('.')]
}

private fun locale_currency(locale: Locale): String? =
    runCatching { Currency.getInstance(locale)?.currencyCode }.getOrNull()

internal fun build_currency_context(text: String, from_email: String, locale: Locale): currency_context {
    val tld_hint = listOfNotNull(sender_tld_currency(from_email))
    val locale_hint = locale_currency(locale)
    val explicit_codes = HashMap<String, Int>()
    val symbols = HashMap<String, Int>()
    for (match in money_pattern.findAll(text)) {
        val number = match.groupValues[2]
        for (raw in listOf(match.groupValues[1], match.groupValues[3])) {
            if (!counts_as_evidence(raw, number)) continue
            val upper = if (raw.trim() == krona_dash) "KR" else raw.trim().uppercase(Locale.ROOT)
            if (upper in iso_currency_codes) {
                explicit_codes[upper] = (explicit_codes[upper] ?: 0) + 1
            } else {
                symbols[upper] = (symbols[upper] ?: 0) + 1
            }
        }
    }
    val code_hint = explicit_codes.entries.sortedByDescending { it.value }.map { it.key }
    val preliminary = currency_context(null, code_hint + tld_hint, locale_hint)
    val counts = HashMap<String, Int>()
    for ((code, n) in explicit_codes) counts[code] = (counts[code] ?: 0) + n
    for ((symbol, n) in symbols) {
        val code = preliminary.resolve(symbol)
        if (code.isNotEmpty()) counts[code] = (counts[code] ?: 0) + n
    }
    val dominant = counts.entries
        .maxWithOrNull(compareBy<Map.Entry<String, Int>> { it.value }.thenBy { if (it.key in explicit_codes) 1 else 0 })
        ?.key
    return currency_context(dominant, code_hint + tld_hint, locale_hint)
}

internal fun fraction_digits(currency: String): Int {
    if (currency.isEmpty()) return 2
    return runCatching { Currency.getInstance(currency).defaultFractionDigits }.getOrNull()?.takeIf { it >= 0 } ?: 2
}

internal fun parse_number(raw: String, currency: String): Double? {
    val digits_only = raw.replace(Regex("[\\u00A0\\u202F '’]"), "")
    val last_dot = digits_only.lastIndexOf('.')
    val last_comma = digits_only.lastIndexOf(',')
    val normalized = when {
        last_dot >= 0 && last_comma >= 0 -> {
            if (last_dot > last_comma) digits_only.replace(",", "")
            else digits_only.replace(".", "").replace(',', '.')
        }
        last_dot >= 0 || last_comma >= 0 -> {
            val sep = if (last_dot >= 0) '.' else ','
            val occurrences = digits_only.count { it == sep }
            val after = digits_only.length - digits_only.lastIndexOf(sep) - 1
            val is_decimal = when {
                occurrences > 1 -> false
                after != 3 -> true
                else -> sep == '.' && fraction_digits(currency) == 3
            }
            if (is_decimal) digits_only.replace(sep, '.') else digits_only.replace(sep.toString(), "")
        }
        else -> digits_only
    }
    return normalized.toDoubleOrNull()
}

internal fun format_amount(value: Double, currency: String, locale: Locale): String {
    val digits = fraction_digits(currency)
    val formatter = NumberFormat.getNumberInstance(locale).apply {
        minimumFractionDigits = digits
        maximumFractionDigits = digits
    }
    val number = formatter.format(value)
    if (currency.isEmpty()) return number
    prefix_symbols[currency]?.let { return it + number }
    suffix_symbols[currency]?.let { return "$number $it" }
    return "$currency $number"
}

internal fun parse_amount(text: String, context: currency_context, locale: Locale): ExtractedAmount? {
    val match = money_pattern.find(text) ?: return null
    val prefix = match.groupValues[1]
    val suffix = match.groupValues[3]
    val currency = context.resolve(prefix.ifEmpty { suffix })
    val value = parse_number(match.groupValues[2], currency) ?: return null
    return ExtractedAmount(value, currency, format_amount(value, currency, locale))
}
