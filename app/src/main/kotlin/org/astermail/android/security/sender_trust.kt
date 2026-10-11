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

package org.astermail.android.security

import java.text.Normalizer
import java.util.Locale
import org.json.JSONArray
import org.json.JSONObject

enum class SenderAuthState { internal, authenticated, unknown, unverified, failed }

enum class SenderWarningKind { address_in_name, lookalike_domain, lookalike_name, brand_name, home_brand_name }

data class SenderWarning(
    val kind: SenderWarningKind,
    val claimed: String,
)

data class SenderTrustContact(
    val name: String,
    val emails: List<String>,
)

data class SenderTrustInput(
    val name: String? = null,
    val email: String? = null,
    val reply_to: String? = null,
    val spf_result: String? = null,
    val dkim_result: String? = null,
    val dmarc_result: String? = null,
    val sender_aligned: Boolean? = null,
    val sender_verified: Boolean? = null,
    val sender_verified_domain: String? = null,
    val is_internal: Boolean = false,
    val own_addresses: List<String> = emptyList(),
    val contacts: List<SenderTrustContact> = emptyList(),
)

data class SenderTrust(
    val name: String,
    val address: String,
    val domain: String,
    val auth_state: SenderAuthState,
    val warning: SenderWarning?,
    val contact_notice: String?,
    val reply_to: String?,
    val show_identity_marks: Boolean,
    val show_verified_badge: Boolean,
)

private class BrandEntry(
    val names: List<String>,
    val solo_names: List<String>,
    val ambiguous: Boolean,
    val any_suffix: Boolean,
    val suffix_labels: List<String>,
    val domains: List<String>,
)

private class ParsedAddress(
    val local: String,
    val domain: String,
    val ascii_domain: String,
    val registrable: String,
    val address: String,
)

private class NameClaim(
    val compact_skeleton: String,
    val compact_fold: String,
    val bare_skeleton: String,
    val bare_fold: String,
    val has_generic: Boolean,
    val has_home_generic: Boolean,
    val foreign_script: Boolean,
)

private class BrandNameMatch(
    val claimed: String,
    val lookalike: Boolean,
    val solo: Boolean,
)

private class PreparedName(
    val candidate: String,
    val solo: Boolean,
    val variants: List<NameClaim>,
)

private class DomainIndex(
    val registrables: MutableSet<String> = HashSet(),
    val by_skeleton: MutableMap<String, String> = HashMap(),
    val by_label_skeleton: MutableMap<String, MutableList<String>> = HashMap(),
)

private class PersonalContext(
    val own_keys: Set<String>,
    val contact_keys: Set<String>,
    val contact_names: Map<String, String>,
    val domains: DomainIndex,
)

private class CachedPersonalContext(
    val own_addresses: List<String>,
    val contacts: List<SenderTrustContact>,
    val context: PersonalContext,
)

private const val sender_trust_data_resource = "sender_trust_data.json"
private const val max_address_length = 320
private const val max_raw_name_length = 2048
private const val max_trust_domain_length = 253
private const val min_contact_name_length = 5
private const val max_reply_to_entries = 20

private const val puny_base = 36
private const val puny_t_min = 1
private const val puny_t_max = 26
private const val puny_skew = 38
private const val puny_damp = 700
private const val puny_initial_bias = 72
private const val puny_initial_n = 128
private const val puny_limit = 0x7fffffffL

private val invisible_ranges = arrayOf(
    0x0000..0x001f,
    0x007f..0x009f,
    0x00ad..0x00ad,
    0x034f..0x034f,
    0x061c..0x061c,
    0x115f..0x1160,
    0x17b4..0x17b5,
    0x180b..0x180f,
    0x200b..0x200b,
    0x200e..0x200f,
    0x2028..0x202e,
    0x2060..0x206f,
    0x2800..0x2800,
    0x3164..0x3164,
    0xfeff..0xfeff,
    0xffa0..0xffa0,
    0xfff9..0xfffb,
    0x1bca0..0x1bca3,
    0x1d173..0x1d17a,
    0xe0000..0xe0fff,
)

private val joiner_ranges = arrayOf(
    0x200c..0x200d,
    0xfe00..0xfe0f,
)

private val space_ranges = arrayOf(
    0x0009..0x000d,
    0x0020..0x0020,
    0x0085..0x0085,
    0x00a0..0x00a0,
    0x1680..0x1680,
    0x2000..0x200a,
    0x202f..0x202f,
    0x205f..0x205f,
    0x3000..0x3000,
)

private val mark_ranges = arrayOf(
    0x0300..0x036f,
    0x0483..0x0489,
    0x1ab0..0x1aff,
    0x1dc0..0x1dff,
    0x20d0..0x20ff,
    0xfe20..0xfe2f,
    0x0591..0x05bd,
    0x05bf..0x05bf,
    0x05c1..0x05c2,
    0x05c4..0x05c5,
    0x05c7..0x05c7,
    0x0610..0x061a,
    0x064b..0x065f,
    0x0670..0x0670,
    0x06d6..0x06dc,
    0x06df..0x06e4,
    0x06e7..0x06e8,
    0x06ea..0x06ed,
    0x0e31..0x0e31,
    0x0e34..0x0e3a,
    0x0e47..0x0e4e,
    0x3099..0x309a,
)

private val separator_ranges = arrayOf(
    0x0021..0x002f,
    0x003a..0x0040,
    0x005b..0x0060,
    0x007b..0x007e,
    0x00a1..0x00bf,
    0x2010..0x2027,
    0x2030..0x205e,
    0x2190..0x2bff,
    0x3001..0x303f,
    0x1f000..0x1faff,
)

private val name_separator_ranges = arrayOf(
    0x02b0..0x02ff,
    0x2e00..0x2e7f,
    0x30a0..0x30a0,
    0x30fb..0x30fb,
    0xa700..0xa71f,
)

private val enclosed_letter_starts = intArrayOf(0x1f150, 0x1f170)

private val script_ranges = arrayOf(
    Triple(0x0370, 0x03ff, "greek"),
    Triple(0x1f00, 0x1fff, "greek"),
    Triple(0x0400, 0x052f, "cyrillic"),
    Triple(0x1c80, 0x1c8f, "cyrillic"),
    Triple(0x2de0, 0x2dff, "cyrillic"),
    Triple(0xa640, 0xa69f, "cyrillic"),
    Triple(0x0530, 0x058f, "armenian"),
    Triple(0x0590, 0x05ff, "hebrew"),
    Triple(0x0600, 0x06ff, "arabic"),
    Triple(0x0750, 0x077f, "arabic"),
    Triple(0x0900, 0x097f, "devanagari"),
    Triple(0x0e00, 0x0e7f, "thai"),
    Triple(0x10a0, 0x10ff, "georgian"),
    Triple(0x3040, 0x30ff, "east_asian"),
    Triple(0x3400, 0x4dbf, "east_asian"),
    Triple(0x4e00, 0x9fff, "east_asian"),
    Triple(0xac00, 0xd7af, "east_asian"),
    Triple(0x13a0, 0x13ff, "cherokee"),
)

private val local_part_forbidden = Regex("""[<>()\[\],;:"\\]""")
private val address_in_name_pattern = Regex("""[^ <>()\[\]"',;:]+@[^ <>()\[\]"',;:@]+""")
private val letter_or_number = Regex("""[\p{L}\p{N}]""")
private val domain_edge_marks = Regex("""^[.-]+|[.-]+$""")
private val domain_last_label = Regex("""^[a-z]{2,}$""")
private val ideographic_dots = Regex("[。．｡]")
private val all_digits = Regex("^[0-9]+$")
private val mark_symbols = Regex("[\u2120\u2122]")
private val spaced_at_sign = Regex(" *@ *")

private fun in_ranges(code: Int, ranges: Array<IntRange>): Boolean {
    for (range in ranges) {
        if (code in range) return true
    }
    return false
}

private fun code_points(text: String): IntArray = text.codePoints().toArray()

private fun from_code_points(points: List<Int>): String {
    val out = StringBuilder(points.size)
    for (code in points) out.appendCodePoint(code)
    return out.toString()
}

private fun is_lone_surrogate(code: Int): Boolean = code in 0xd800..0xdfff

private fun nfkc(text: String): String = Normalizer.normalize(text, Normalizer.Form.NFKC)

private fun nfkd(text: String): String = Normalizer.normalize(text, Normalizer.Form.NFKD)

private fun lower(text: String): String = text.lowercase(Locale.ROOT)

private fun trim_spaces(text: String): String = text.trim { it == ' ' }

private fun clean_text(text: String, keep_joiners: Boolean): String {
    val result = StringBuilder(text.length)
    var pending_space = false
    for (code in code_points(text)) {
        if (is_lone_surrogate(code)) continue
        if (in_ranges(code, space_ranges)) {
            pending_space = result.isNotEmpty()
            continue
        }
        if (in_ranges(code, invisible_ranges)) continue
        if (!keep_joiners && in_ranges(code, joiner_ranges)) continue
        if (pending_space) {
            result.append(' ')
            pending_space = false
        }
        result.appendCodePoint(code)
    }
    return result.toString()
}

private fun strip_outer_quotes(text: String): String {
    var result = text
    while (result.length >= 2) {
        val first = result[0]
        val last = result[result.length - 1]
        val quoted = (first == '"' && last == '"') || (first == '\'' && last == '\'')
        if (!quoted) break
        result = trim_spaces(result.substring(1, result.length - 1))
    }
    return result
}

private fun cap_trust_code_points(text: String, max: Int): String {
    if (text.codePointCount(0, text.length) <= max) return text
    return trim_spaces(text.substring(0, text.offsetByCodePoints(0, max)))
}

private fun capped_display_name(raw: String?, max: Int): String {
    if (raw == null) return ""
    val cleaned = strip_outer_quotes(
        cap_trust_code_points(trim_spaces(clean_text(raw, true)), max_raw_name_length),
    )
    if (clean_text(cleaned, false).isEmpty()) return ""
    return cap_trust_code_points(cleaned, max)
}

fun sanitize_display_name(raw: String?): String =
    capped_display_name(raw, sender_trust_data.max_name_length)

private fun enclosed_plain(text: String): String {
    val out = StringBuilder(text.length)
    for (code in code_points(text)) {
        var plain = code
        for (start in enclosed_letter_starts) {
            if (code >= start && code < start + 26) plain = 0x41 + code - start
        }
        out.appendCodePoint(plain)
    }
    return out.toString()
}

private fun fold(text: String): String = lower(clean_text(nfkc(text), false))

fun sender_skeleton(text: String): String {
    val confusables = sender_trust_data.confusables
    val mapped = StringBuilder(text.length)
    for (code in code_points(nfkd(text))) {
        if (in_ranges(code, mark_ranges)) continue
        val replacement = confusables[code]
        if (replacement != null) mapped.append(replacement) else mapped.appendCodePoint(code)
    }
    val stripped = StringBuilder(mapped.length)
    for (code in code_points(nfkd(lower(clean_text(mapped.toString(), false))))) {
        if (in_ranges(code, mark_ranges)) continue
        stripped.appendCodePoint(code)
    }
    var result = stripped.toString()
    for ((from, to) in sender_trust_data.digraphs) {
        result = result.replace(from, to)
    }
    return result
}

private fun puny_adapt(delta: Long, count: Int, first: Boolean): Int {
    var k = 0
    var value = if (first) delta / puny_damp else delta / 2
    value += value / count
    while (value > ((puny_base - puny_t_min) * puny_t_max) / 2) {
        value /= (puny_base - puny_t_min)
        k += puny_base
    }
    return (k + ((puny_base - puny_t_min + 1) * value) / (value + puny_skew)).toInt()
}

private fun puny_threshold(k: Int, bias: Int): Int = when {
    k <= bias -> puny_t_min
    k >= bias + puny_t_max -> puny_t_max
    else -> k - bias
}

private fun puny_digit_char(digit: Int): Char = (if (digit < 26) 0x61 + digit else 0x30 + digit - 26).toChar()

private fun puny_digit_value(code: Int): Int = when (code) {
    in 0x30..0x39 -> code - 0x30 + 26
    in 0x41..0x5a -> code - 0x41
    in 0x61..0x7a -> code - 0x61
    else -> puny_base
}

private fun puny_encode(points: IntArray): String? {
    val output = StringBuilder()
    for (code in points) {
        if (code < 0x80) output.append(code.toChar())
    }
    val basic_count = output.length
    var handled = basic_count
    if (basic_count > 0) output.append('-')
    var n = puny_initial_n
    var delta = 0L
    var bias = puny_initial_bias
    while (handled < points.size) {
        var next = 0x110000
        for (code in points) {
            if (code in n until next) next = code
        }
        if (next == 0x110000) return null
        delta += (next - n).toLong() * (handled + 1)
        if (delta > puny_limit) return null
        n = next
        for (code in points) {
            if (code < n) delta += 1
            if (code != n) continue
            var q = delta
            var k = puny_base
            while (true) {
                val t = puny_threshold(k, bias)
                if (q < t) break
                output.append(puny_digit_char((t + ((q - t) % (puny_base - t))).toInt()))
                q = (q - t) / (puny_base - t)
                k += puny_base
            }
            output.append(puny_digit_char(q.toInt()))
            bias = puny_adapt(delta, handled + 1, handled == basic_count)
            delta = 0
            handled += 1
        }
        delta += 1
        n += 1
    }
    return output.toString()
}

private fun puny_decode(input: String): List<Int>? {
    val output = ArrayList<Int>()
    val basic_end = input.lastIndexOf('-')
    for (index in 0 until maxOf(basic_end, 0)) {
        val code = input[index].code
        if (code >= 0x80) return null
        output.add(code)
    }
    var n = puny_initial_n.toLong()
    var bias = puny_initial_bias
    var i = 0L
    var index = if (basic_end > 0) basic_end + 1 else 0
    while (index < input.length) {
        val old_i = i
        var w = 1L
        var k = puny_base
        while (true) {
            if (index >= input.length) return null
            val digit = puny_digit_value(input[index].code)
            index += 1
            if (digit >= puny_base) return null
            i += digit * w
            if (i > puny_limit) return null
            val t = puny_threshold(k, bias)
            if (digit < t) break
            w *= (puny_base - t)
            if (w > puny_limit) return null
            k += puny_base
        }
        val length = output.size + 1
        bias = puny_adapt(i - old_i, length, old_i == 0L)
        n += i / length
        i %= length
        if (n > 0x10ffff || is_lone_surrogate(n.toInt())) return null
        output.add(i.toInt(), n.toInt())
        i += 1
    }
    return output
}

private fun is_ascii(text: String): Boolean = text.all { it.code <= 0x7f }

private fun is_label_code(code: Int): Boolean =
    code in 0x61..0x7a || code in 0x30..0x39 || code == 0x2d

private fun is_ascii_label(label: String): Boolean {
    if (label.isEmpty() || label.length > 63) return false
    if (label.startsWith("-") || label.endsWith("-")) return false
    return label.all { is_label_code(it.code) }
}

private fun label_to_ascii(label: String): String? {
    if (is_ascii(label)) return if (is_ascii_label(label)) label else null
    val points = code_points(label)
    for (code in points) {
        if (code < 0x80 && !is_label_code(code)) return null
        if (code >= 0x80 && in_ranges(code, separator_ranges)) return null
        if (in_ranges(code, space_ranges)) return null
    }
    val encoded = puny_encode(points) ?: return null
    val ascii = "xn--$encoded"
    return if (is_ascii_label(ascii)) ascii else null
}

fun sender_domain_to_ascii(raw: String): String {
    val folded = lower(clean_text(nfkc(raw), false)).replace(ideographic_dots, ".")
    val trimmed = if (folded.endsWith(".")) folded.substring(0, folded.length - 1) else folded
    if (trimmed.isEmpty() || trimmed.contains(' ')) return ""
    val labels = ArrayList<String>()
    for (label in trimmed.split(".")) {
        labels.add(label_to_ascii(label) ?: return "")
    }
    if (labels.size < 2) return ""
    val joined = labels.joinToString(".")
    if (joined.length > max_trust_domain_length) return ""
    if (all_digits.matches(labels[labels.size - 1])) return ""
    return joined
}

private fun label_to_unicode(label: String): String {
    if (!label.startsWith("xn--")) return label
    val decoded = puny_decode(label.substring(4))
    if (decoded == null || decoded.isEmpty()) return label
    val text = from_code_points(decoded)
    if (text != nfkc(text) || text != lower(text)) return label
    if (clean_text(text, false) != text || is_ascii(text)) return label
    if (label_to_ascii(text) != label) return label
    return text
}

private fun script_of(code: Int): String {
    if (code < 0x80) {
        val letter = code in 0x61..0x7a || code in 0x41..0x5a
        return if (letter) "latin" else "common"
    }
    if (code in 0x00c0..0x024f) return "latin"
    if (code in 0x1e00..0x1eff) return "latin"
    for ((start, end, script) in script_ranges) {
        if (code in start..end) return script
    }
    return "other"
}

private fun is_safe_unicode_label(text: String): Boolean {
    val confusables = sender_trust_data.confusables
    val scripts = HashSet<String>()
    var has_distinct = false
    for (code in code_points(text)) {
        val script = script_of(code)
        if (script == "other") return false
        if (script == "common") continue
        scripts.add(script)
        if (code < 0x80) continue
        if (!confusables.containsKey(code)) {
            has_distinct = true
        } else if (script == "latin") {
            return false
        }
    }
    return scripts.size == 1 && has_distinct
}

fun sender_domain_for_display(ascii_domain: String): String =
    ascii_domain.split(".").joinToString(".") { label ->
        val text = label_to_unicode(label)
        if (text != label && is_safe_unicode_label(text)) text else label
    }

private fun domain_unicode(ascii_domain: String): String =
    ascii_domain.split(".").joinToString(".") { label_to_unicode(it) }

fun sender_registrable_domain(ascii_domain: String): String {
    val labels = ascii_domain.split(".")
    if (labels.size <= 2) return ascii_domain
    val tld = labels[labels.size - 1]
    val second = labels[labels.size - 2]
    val take = if (tld.length == 2 && second in sender_trust_data.second_level_suffixes) 3 else 2
    return labels.takeLast(take).joinToString(".")
}

private fun trust_registrable_label(registrable: String): String = registrable.split(".")[0]

private fun within_domain(ascii_domain: String, parent: String): Boolean =
    ascii_domain == parent || ascii_domain.endsWith(".$parent")

private fun parse_address(raw: String?): ParsedAddress? {
    if (raw == null) return null
    var text = trim_spaces(clean_text(raw, false))
    val open = text.lastIndexOf('<')
    val close = text.lastIndexOf('>')
    if (open >= 0 && close > open) text = trim_spaces(text.substring(open + 1, close))
    if (text.isEmpty() || text.length > max_address_length || text.contains(' ')) return null
    val at = text.lastIndexOf('@')
    if (at <= 0 || at == text.length - 1) return null
    val local = text.substring(0, at)
    val ascii_domain = sender_domain_to_ascii(text.substring(at + 1))
    if (ascii_domain.isEmpty() || local_part_forbidden.containsMatchIn(local)) return null
    val domain = sender_domain_for_display(ascii_domain)
    return ParsedAddress(
        local = local,
        domain = domain,
        ascii_domain = ascii_domain,
        registrable = sender_registrable_domain(ascii_domain),
        address = "$local@$domain",
    )
}

private fun address_key(parsed: ParsedAddress): String = "${fold(parsed.local)}@${parsed.ascii_domain}"

private fun mailbox_key(parsed: ParsedAddress): String {
    val tagless = fold(parsed.local).split("+")[0]
    val local = if (parsed.ascii_domain in sender_trust_data.dotless_mailbox_domains) {
        tagless.replace(".", "")
    } else {
        tagless
    }
    return "$local@${parsed.ascii_domain}"
}

private fun tokenize(text: String): List<String> {
    val tokens = ArrayList<String>()
    val current = StringBuilder()
    val spaced = text.replace(mark_symbols, " ")
    for (code in code_points(clean_text(nfkc(spaced), false))) {
        if (
            in_ranges(code, separator_ranges) ||
            in_ranges(code, name_separator_ranges) ||
            in_ranges(code, space_ranges)
        ) {
            if (current.isNotEmpty()) tokens.add(current.toString())
            current.setLength(0)
            continue
        }
        current.appendCodePoint(code)
    }
    if (current.isNotEmpty()) tokens.add(current.toString())
    return tokens
}

private fun plain_capitals(text: String): String =
    if (text.any { it in 'a'..'z' }) text else text.replace("I", "i")

private fun is_foreign_script(text: String): Boolean {
    var found = ""
    for (code in code_points(text)) {
        val script = script_of(code)
        if (script == "common") continue
        if (script == "latin" || script == "other") return false
        if (found.isNotEmpty() && found != script) return false
        found = script
    }
    return found.isNotEmpty()
}

private fun name_claim(text: String, capitals_aware: Boolean): NameClaim {
    val kept = StringBuilder()
    val bare = StringBuilder()
    var has_generic = false
    var has_home_generic = false
    for (token in tokenize(text)) {
        val word = lower(token)
        if (word in sender_trust_data.generic_words) {
            has_generic = true
            if (word !in sender_trust_data.home_neutral_words) has_home_generic = true
            continue
        }
        kept.append(token)
        if (!all_digits.matches(token)) bare.append(token)
    }
    val joined = kept.toString()
    val bare_joined = bare.toString()
    return NameClaim(
        compact_skeleton = sender_skeleton(if (capitals_aware) plain_capitals(joined) else joined),
        compact_fold = fold(joined),
        bare_skeleton = sender_skeleton(if (capitals_aware) plain_capitals(bare_joined) else bare_joined),
        bare_fold = fold(bare_joined),
        has_generic = has_generic,
        has_home_generic = has_home_generic,
        foreign_script = is_foreign_script(joined),
    )
}

private fun reads_as_lookalike(match: BrandNameMatch, claim: NameClaim): Boolean =
    match.lookalike && (match.solo || !claim.foreign_script)

private fun prepare_names(names: List<String>, solo_names: List<String>, ambiguous: Boolean): List<PreparedName> =
    names.map { candidate ->
        PreparedName(
            candidate = candidate,
            solo = !ambiguous || candidate in solo_names,
            variants = listOf(candidate, lower(candidate), candidate.uppercase(Locale.ROOT))
                .map { name_claim(it, false) }
                .filter { it.compact_skeleton.isNotEmpty() },
        )
    }

private fun match_brand_name(claim: NameClaim, names: List<PreparedName>): BrandNameMatch? {
    if (claim.compact_skeleton.isEmpty()) return null
    for (name in names) {
        for (variant in name.variants) {
            if (variant.compact_fold == claim.compact_fold || variant.compact_fold == claim.bare_fold) {
                return BrandNameMatch(claimed = name.candidate, lookalike = false, solo = name.solo)
            }
            if (variant.compact_skeleton == claim.compact_skeleton) {
                return BrandNameMatch(
                    claimed = name.candidate,
                    lookalike = variant.compact_fold != claim.compact_fold,
                    solo = name.solo,
                )
            }
            if (variant.compact_skeleton == claim.bare_skeleton) {
                return BrandNameMatch(
                    claimed = name.candidate,
                    lookalike = variant.compact_fold != claim.bare_fold,
                    solo = name.solo,
                )
            }
        }
    }
    return null
}

private fun is_trust_brand_domain(parsed: ParsedAddress, brand: BrandEntry, authenticated: Boolean): Boolean {
    if (parsed.ascii_domain in sender_trust_data.public_mailbox_domains) {
        return authenticated &&
            parsed.ascii_domain in brand.domains &&
            lower(parsed.local) in sender_trust_data.reserved_local_parts
    }
    if (brand.domains.any { within_domain(parsed.ascii_domain, it) }) return true
    if (!brand.any_suffix) return false
    val label = trust_registrable_label(parsed.registrable)
    if (label !in brand.suffix_labels) return false
    if (parsed.registrable.length <= label.length) return false
    return parsed.registrable.substring(label.length + 1) in sender_trust_data.country_suffixes
}

private fun is_official_home_address(parsed: ParsedAddress): Boolean =
    parsed.ascii_domain in sender_trust_data.home_domains &&
        lower(parsed.local) in sender_trust_data.home_official_local_parts

private fun is_letter_or_number(code: Int): Boolean =
    letter_or_number.matches(String(Character.toChars(code)))

private fun is_local_part_char(code: Int): Boolean =
    is_letter_or_number(code) || code == 0x2e || code == 0x2d || code == 0x5f || code == 0x2b

private fun tighten_address(candidate: String): String {
    val points = code_points(candidate)
    val at = points.lastIndexOf(0x40)
    if (at < 0) return candidate
    var start = at
    while (start > 0 && is_local_part_char(points[start - 1])) start -= 1
    var end = at + 1
    while (end < points.size && (is_letter_or_number(points[end]) || points[end] == 0x2d || points[end] == 0x2e)) {
        end += 1
    }
    while (end > at + 1 && !is_letter_or_number(points[end - 1])) end -= 1
    return String(points, start, end - start)
}

private fun find_address_in_name(name: String): ParsedAddress? {
    val folded = clean_text(nfkc(name), false).replace(spaced_at_sign, "@")
    for (match in address_in_name_pattern.findAll(folded)) {
        val parsed = parse_address(tighten_address(match.value))
        if (parsed != null) return parsed
    }
    return null
}

private fun is_domain_label_char(code: Int): Boolean =
    code in 0x61..0x7a || code in 0x30..0x39 || code == 0x2d || code == 0x2e

private fun domain_shaped(run: String): String? {
    val trimmed = run.replace(domain_edge_marks, "")
    val labels = trimmed.split(".")
    if (labels.size < 2 || labels.any { it.isEmpty() }) return null
    if (!domain_last_label.matches(labels.last())) return null
    return trimmed
}

private fun point_at(points: IntArray, position: Int): Int =
    if (position >= 0 && position < points.size) points[position] else -1

private fun is_gap(code: Int): Boolean = code == 0x20 || code == 0x09 || code == 0xa0

private fun ascii_lower(code: Int): Int = if (code in 0x41..0x5a) code + 0x20 else code

private fun bracketed_dot_end(points: IntArray, open: Int): Int {
    var position = open + 1
    while (is_gap(point_at(points, position))) position += 1
    if (point_at(points, position) == 0x2e) {
        position += 1
    } else if (
        ascii_lower(point_at(points, position)) == 0x64 &&
        ascii_lower(point_at(points, position + 1)) == 0x6f &&
        ascii_lower(point_at(points, position + 2)) == 0x74
    ) {
        position += 3
    } else {
        return -1
    }
    while (is_gap(point_at(points, position))) position += 1
    val close = point_at(points, position)
    return if (close == 0x29 || close == 0x5d || close == 0x7d) position + 1 else -1
}

private fun starts_framework_suffix(points: IntArray, position: Int): Boolean {
    val after = point_at(points, position + 3)
    return ascii_lower(point_at(points, position)) == 0x6e &&
        ascii_lower(point_at(points, position + 1)) == 0x65 &&
        ascii_lower(point_at(points, position + 2)) == 0x74 &&
        (after < 0 || !is_letter_or_number(after))
}

private fun plain_dots(name: String): String {
    val points = code_points(name)
    val out = ArrayList<Int>()
    var position = 0
    while (position < points.size) {
        val code = points[position]
        val end = if (code == 0x28 || code == 0x5b || code == 0x7b) bracketed_dot_end(points, position) else -1
        if (end >= 0) {
            while (out.isNotEmpty() && is_gap(out[out.size - 1])) out.removeAt(out.size - 1)
            out.add(0x2e)
            position = end
            while (is_gap(point_at(points, position))) position += 1
            continue
        }
        if (
            code == 0x2e &&
            out.isNotEmpty() &&
            is_gap(out[out.size - 1]) &&
            position + 1 < points.size &&
            is_letter_or_number(points[position + 1]) &&
            !starts_framework_suffix(points, position + 1)
        ) {
            while (out.isNotEmpty() && is_gap(out[out.size - 1])) out.removeAt(out.size - 1)
            out.add(0x2e)
            position += 1
            continue
        }
        out.add(code)
        position += 1
    }
    return from_code_points(out)
}

private fun loose_dots(name: String): String {
    val points = code_points(name)
    val out = ArrayList<Int>()
    var position = 0
    while (position < points.size) {
        val code = points[position]
        if (code == 0x2e) {
            var before = out.size
            var after = position + 1
            while (before > 0 && is_gap(out[before - 1])) before -= 1
            while (is_gap(point_at(points, after))) after += 1
            if (
                before > 0 &&
                after < points.size &&
                is_letter_or_number(out[before - 1]) &&
                is_letter_or_number(points[after])
            ) {
                while (out.size > before) out.removeAt(out.size - 1)
                out.add(0x2e)
                position = after
                continue
            }
        }
        out.add(code)
        position += 1
    }
    return from_code_points(out)
}

private fun label_runs(text: String): List<String> {
    val runs = ArrayList<String>()
    val run = StringBuilder()
    for (code in code_points(text)) {
        if (is_domain_label_char(code)) {
            run.appendCodePoint(code)
            continue
        }
        if (run.isNotEmpty()) runs.add(run.toString())
        run.setLength(0)
    }
    if (run.isNotEmpty()) runs.add(run.toString())
    return runs
}

private fun shaped_targets(text: String): List<String> {
    val targets = ArrayList<String>()
    for (candidate in label_runs(text)) {
        val shaped = domain_shaped(candidate) ?: continue
        targets.add(sender_registrable_domain(shaped))
    }
    return targets
}

private fun inner_targets(text: String): List<String> {
    val targets = ArrayList<String>()
    for (candidate in label_runs(text)) {
        val shaped = domain_shaped(candidate) ?: continue
        val labels = shaped.split(".")
        for (start in 0 until labels.size - 2) {
            for (size in 2..3) {
                val end = start + size
                if (end >= labels.size) break
                if (
                    end == labels.size - 1 &&
                    labels[end].length == 2 &&
                    labels[end - 1] in sender_trust_data.second_level_suffixes
                ) {
                    continue
                }
                val inner = labels.subList(start, end).joinToString(".")
                if (sender_trust_data.brand_domain_index.by_skeleton.containsKey(inner)) targets.add(inner)
            }
        }
    }
    return targets
}

private fun domains_in_name(name: String): List<String> {
    val targets = ArrayList<String>()
    fun keep(target: String) {
        if (target.isNotEmpty() && target !in targets) targets.add(target)
    }
    val plain = plain_dots(name)
    val bases = if (plain == name) listOf(name) else listOf(name, plain)
    for (base in bases) {
        shaped_targets(sender_skeleton(base)).forEach { keep(it) }
        shaped_targets(sender_skeleton(fold(base))).forEach { keep(it) }
    }
    val loose = loose_dots(plain)
    if (loose != plain) {
        for (text in listOf(sender_skeleton(loose), sender_skeleton(fold(loose)))) {
            for (target in shaped_targets(text)) {
                if (sender_trust_data.brand_domain_index.by_skeleton.containsKey(target)) keep(target)
            }
        }
    }
    for (target in shaped_targets(fold(plain))) {
        if (!target.contains("xn--")) continue
        val unicode = domain_unicode(target)
        if (unicode != target) keep(sender_skeleton(unicode))
    }
    for (base in bases) {
        inner_targets(sender_skeleton(fold(base))).forEach { keep(it) }
    }
    return targets
}

private fun build_domain_index(domains: List<String>, label_level: Boolean): DomainIndex {
    val index = DomainIndex()
    for (candidate in domains) {
        val target = sender_registrable_domain(candidate)
        if (target.isEmpty() || target in index.registrables) continue
        index.registrables.add(target)
        val full = sender_skeleton(target)
        if (!index.by_skeleton.containsKey(full)) index.by_skeleton[full] = target
        if (!label_level) continue
        val label = trust_registrable_label(target)
        if (label.length < 4) continue
        index.by_label_skeleton.getOrPut(sender_skeleton(label)) { ArrayList() }.add(target)
    }
    return index
}

private fun lookalike_of(parsed: ParsedAddress, index: DomainIndex): String? {
    if (parsed.registrable in index.registrables) return null
    val unicode = domain_unicode(parsed.registrable)
    val full = index.by_skeleton[sender_skeleton(unicode)]
    if (full != null) return full
    val label = trust_registrable_label(parsed.registrable)
    val bucket = index.by_label_skeleton[sender_skeleton(trust_registrable_label(unicode))].orEmpty()
    for (target in bucket) {
        if (trust_registrable_label(target) != label) return target
    }
    val labels = parsed.ascii_domain.split(".")
    val depth = labels.size - parsed.registrable.split(".").size
    for (start in 0 until depth) {
        for (size in 2..3) {
            if (start + size > depth) break
            val inner = labels.subList(start, start + size).joinToString(".")
            val known = index.by_skeleton[sender_skeleton(domain_unicode(inner))]
            if (known != null) return known
        }
    }
    return null
}

private fun string_list(array: JSONArray?): List<String> {
    if (array == null) return emptyList()
    val out = ArrayList<String>(array.length())
    for (index in 0 until array.length()) out.add(array.getString(index))
    return out
}

private object sender_trust_data {
    val max_name_length: Int
    val confusables: Map<Int, String>
    val digraphs: List<Pair<String, String>>
    val second_level_suffixes: Set<String>
    val generic_words: Set<String>
    val relay_domains: List<String>
    val public_mailbox_domains: Set<String>
    val dotless_mailbox_domains: Set<String>
    val reserved_local_parts: Set<String>
    val country_suffixes: Set<String>
    val brands: List<BrandEntry>
    val home_first_name: String
    val home_domains: List<String>
    val home_official_local_parts: List<String>
    val home_neutral_words: Set<String>
    private val home_names_raw: List<String>
    private val home_solo_names_raw: List<String>
    private val lookalike_only_domains: List<String>

    init {
        val stream = checkNotNull(
            SenderTrustInput::class.java.classLoader?.getResourceAsStream(sender_trust_data_resource),
        )
        val root = JSONObject(stream.bufferedReader(Charsets.UTF_8).use { it.readText() })
        max_name_length = root.getInt("max_name_length")
        val confusable_object = root.getJSONObject("confusables")
        val confusable_map = HashMap<Int, String>()
        for (key in confusable_object.keys()) {
            confusable_map[key.codePointAt(0)] = confusable_object.getString(key)
        }
        confusables = confusable_map
        val digraph_array = root.getJSONArray("digraphs")
        val digraph_list = ArrayList<Pair<String, String>>()
        for (index in 0 until digraph_array.length()) {
            val pair = digraph_array.getJSONArray(index)
            digraph_list.add(pair.getString(0) to pair.getString(1))
        }
        digraphs = digraph_list
        second_level_suffixes = string_list(root.getJSONArray("second_level_suffixes")).toSet()
        generic_words = string_list(root.getJSONArray("generic_words")).toSet()
        relay_domains = string_list(root.getJSONArray("relay_domains"))
        public_mailbox_domains = string_list(root.getJSONArray("public_mailbox_domains")).toSet()
        dotless_mailbox_domains = string_list(root.getJSONArray("dotless_mailbox_domains")).toSet()
        reserved_local_parts = string_list(root.getJSONArray("reserved_local_parts")).toSet()
        country_suffixes = string_list(root.getJSONArray("country_suffixes")).toSet()
        val brand_array = root.getJSONArray("brands")
        val brand_list = ArrayList<BrandEntry>()
        for (index in 0 until brand_array.length()) {
            val entry = brand_array.getJSONObject(index)
            brand_list.add(
                BrandEntry(
                    names = string_list(entry.getJSONArray("names")),
                    solo_names = string_list(entry.optJSONArray("solo_names")),
                    ambiguous = entry.optBoolean("ambiguous", false),
                    any_suffix = entry.optBoolean("any_suffix", false),
                    suffix_labels = string_list(entry.optJSONArray("suffix_labels")),
                    domains = string_list(entry.getJSONArray("domains")),
                ),
            )
        }
        brands = brand_list
        val home = root.getJSONObject("home_brand")
        home_names_raw = string_list(home.getJSONArray("names"))
        home_solo_names_raw = string_list(home.optJSONArray("solo_names"))
        home_first_name = home_names_raw[0]
        home_domains = string_list(home.getJSONArray("domains"))
        home_official_local_parts = string_list(home.getJSONArray("official_local_parts"))
        home_neutral_words = string_list(home.getJSONArray("neutral_words")).toSet()
        lookalike_only_domains = string_list(root.getJSONArray("lookalike_only_domains"))
    }

    val home_names: List<PreparedName> by lazy { prepare_names(home_names_raw, home_solo_names_raw, true) }

    val brand_names: List<List<PreparedName>> by lazy {
        brands.map { prepare_names(it.names, it.solo_names, it.ambiguous) }
    }

    val brand_domain_index: DomainIndex by lazy {
        build_domain_index(home_domains + brands.flatMap { it.domains } + lookalike_only_domains, true)
    }
}

private fun contact_name_key(name: String): String = sender_skeleton(tokenize(name).joinToString(" "))

private fun build_personal_context(own_addresses: List<String>, contacts: List<SenderTrustContact>): PersonalContext {
    val own_keys = HashSet<String>()
    val contact_keys = HashSet<String>()
    val contact_names = HashMap<String, String>()
    val domains = ArrayList<String>()
    for (own in own_addresses) {
        val parsed = parse_address(own) ?: continue
        own_keys.add(address_key(parsed))
        domains.add(parsed.ascii_domain)
    }
    for (contact in contacts) {
        var has_address = false
        for (email in contact.emails) {
            val parsed = parse_address(email) ?: continue
            has_address = true
            contact_keys.add(address_key(parsed))
            domains.add(parsed.ascii_domain)
        }
        if (!has_address) continue
        val contact_name = sanitize_display_name(contact.name)
        val key = contact_name_key(contact_name)
        if (key.isNotEmpty() && !contact_names.containsKey(key)) contact_names[key] = contact_name
    }
    return PersonalContext(own_keys, contact_keys, contact_names, build_domain_index(domains, false))
}

@Volatile
private var cached_personal_context: CachedPersonalContext? = null

private fun personal_context_of(input: SenderTrustInput): PersonalContext {
    val cached = cached_personal_context
    if (cached != null && cached.own_addresses === input.own_addresses && cached.contacts === input.contacts) {
        return cached.context
    }
    val context = build_personal_context(input.own_addresses, input.contacts)
    cached_personal_context = CachedPersonalContext(input.own_addresses, input.contacts, context)
    return context
}

private fun status_of(raw: String?): String {
    val value = raw?.let { lower(it.trim(' ', '\t', '\r', '\n')) }.orEmpty()
    return when (value) {
        "", "missing" -> "missing"
        "pass", "none" -> value
        "fail", "hardfail" -> "fail"
        else -> "other"
    }
}

private fun auth_state_of(input: SenderTrustInput): SenderAuthState {
    if (input.is_internal) return SenderAuthState.internal
    val statuses = listOf(status_of(input.spf_result), status_of(input.dkim_result), status_of(input.dmarc_result))
    if (input.sender_aligned == true) return SenderAuthState.authenticated
    if (input.sender_aligned == false) {
        return if ("fail" in statuses) SenderAuthState.failed else SenderAuthState.unverified
    }
    if (statuses.all { it == "missing" }) return SenderAuthState.unknown
    if (statuses[2] == "fail") return SenderAuthState.failed
    if (statuses[2] == "pass") return SenderAuthState.authenticated
    if ("pass" in statuses || "other" in statuses) return SenderAuthState.unknown
    if ("fail" in statuses) return SenderAuthState.failed
    return SenderAuthState.unverified
}

private fun claims_identity(name: String): Boolean {
    if (name.isEmpty()) return false
    if (find_address_in_name(name) != null) return true
    val claim = name_claim(name, true)
    if (match_brand_name(claim, sender_trust_data.home_names) != null) return true
    return sender_trust_data.brand_names.any { match_brand_name(claim, it) != null }
}

private fun find_warning(
    name: String,
    parsed: ParsedAddress,
    auth_state: SenderAuthState,
    personal: PersonalContext,
): SenderWarning? {
    val trusted_auth = auth_state == SenderAuthState.internal || auth_state == SenderAuthState.authenticated
    if (trusted_auth && address_key(parsed) in personal.own_keys) return null
    val lookalike = lookalike_of(parsed, sender_trust_data.brand_domain_index) ?: lookalike_of(parsed, personal.domains)
    if (lookalike != null) return SenderWarning(SenderWarningKind.lookalike_domain, lookalike)
    val in_local = if (parsed.local.contains('@')) parse_address(parsed.local) else null
    if (in_local != null && in_local.registrable != parsed.registrable) {
        return SenderWarning(SenderWarningKind.lookalike_domain, in_local.registrable)
    }
    if (name.isEmpty()) return null

    val in_name = find_address_in_name(name)
    val relayed = sender_trust_data.relay_domains.any { within_domain(parsed.ascii_domain, it) }
    val from_home = sender_trust_data.home_domains.any { within_domain(parsed.ascii_domain, it) }
    val other_mailbox = in_name != null &&
        (parsed.ascii_domain in sender_trust_data.public_mailbox_domains || from_home) &&
        mailbox_key(in_name) != mailbox_key(parsed)
    if (in_name != null && (in_name.registrable != parsed.registrable || other_mailbox) && !relayed) {
        return SenderWarning(SenderWarningKind.address_in_name, in_name.address)
    }

    val named_targets = if (relayed) emptyList() else domains_in_name(name)
    for (target in named_targets) {
        val named_domain = sender_trust_data.brand_domain_index.by_skeleton[target] ?: continue
        if (named_domain == parsed.registrable) continue
        if (named_domain in sender_trust_data.public_mailbox_domains) continue
        val home_named = sender_trust_data.home_domains.any { sender_registrable_domain(it) == named_domain }
        val owners = sender_trust_data.brands.filter { entry ->
            entry.domains.any { sender_registrable_domain(it) == named_domain }
        }
        if (home_named) {
            if (!from_home) {
                return SenderWarning(SenderWarningKind.home_brand_name, sender_trust_data.home_first_name)
            }
        } else if (owners.isNotEmpty() && owners.none { is_trust_brand_domain(parsed, it, trusted_auth) }) {
            return SenderWarning(SenderWarningKind.brand_name, named_domain)
        }
    }

    for (target in named_targets) {
        if (target == parsed.registrable) continue
        if (sender_trust_data.brand_domain_index.by_skeleton.containsKey(target)) continue
        val label = trust_registrable_label(target)
        val label_claim = name_claim(label, true)
        val home_label = match_brand_name(label_claim, sender_trust_data.home_names)
        if (home_label != null && (reads_as_lookalike(home_label, label_claim) || home_label.solo)) {
            if (!from_home) {
                return SenderWarning(SenderWarningKind.home_brand_name, sender_trust_data.home_first_name)
            }
            continue
        }
        val country = target.drop(label.length + 1) in sender_trust_data.country_suffixes
        for (position in sender_trust_data.brands.indices) {
            val match = match_brand_name(label_claim, sender_trust_data.brand_names[position]) ?: continue
            if (is_trust_brand_domain(parsed, sender_trust_data.brands[position], trusted_auth)) break
            if (reads_as_lookalike(match, label_claim)) {
                return SenderWarning(SenderWarningKind.lookalike_name, match.claimed)
            }
            if (match.solo || country) return SenderWarning(SenderWarningKind.brand_name, match.claimed)
            break
        }
    }

    val claim = name_claim(name, true)
    val home = match_brand_name(claim, sender_trust_data.home_names)
    if (home != null && (reads_as_lookalike(home, claim) || home.solo || claim.has_home_generic)) {
        val trusted = is_official_home_address(parsed) && trusted_auth
        return if (trusted) null else SenderWarning(SenderWarningKind.home_brand_name, sender_trust_data.home_first_name)
    }

    val brands = sender_trust_data.brands
    val brand_names = sender_trust_data.brand_names
    for (position in brands.indices) {
        val match = match_brand_name(claim, brand_names[position]) ?: continue
        if (is_trust_brand_domain(parsed, brands[position], trusted_auth)) return null
        if (reads_as_lookalike(match, claim)) return SenderWarning(SenderWarningKind.lookalike_name, match.claimed)
        if (match.solo || claim.has_generic) return SenderWarning(SenderWarningKind.brand_name, match.claimed)
        return null
    }
    return null
}

private fun find_contact_notice(name: String, parsed: ParsedAddress, personal: PersonalContext): String? {
    if (name.isEmpty() || personal.contact_names.isEmpty()) return null
    val key = address_key(parsed)
    if (key in personal.contact_keys || key in personal.own_keys) return null
    val name_key = contact_name_key(name)
    if (name_key.codePointCount(0, name_key.length) < min_contact_name_length) return null
    if (!name_key.contains(' ')) return null
    return personal.contact_names[name_key]
}

private fun split_address_list(text: String): List<String> {
    val entries = ArrayList<String>()
    val current = StringBuilder()
    var quoted = false
    var escaped = false
    var angle = false
    for (char in text) {
        if (escaped) {
            escaped = false
            current.append(char)
            continue
        }
        if (quoted && char == '\\') {
            escaped = true
            current.append(char)
            continue
        }
        if (char == '"' && !angle) {
            quoted = !quoted
        } else if (!quoted && char == '<') {
            angle = true
        } else if (!quoted && char == '>') {
            angle = false
        }
        if (!quoted && !angle && (char == ',' || char == ';')) {
            if (current.isNotBlank()) entries.add(current.toString())
            current.setLength(0)
            if (entries.size >= max_reply_to_entries) return entries
            continue
        }
        current.append(char)
    }
    if (current.isNotBlank()) entries.add(current.toString())
    return entries
}

private fun reply_to_notice(parsed: ParsedAddress, reply_to: String?): String? {
    if (reply_to == null || clean_text(reply_to, false).isEmpty()) return null
    for (entry in split_address_list(reply_to)) {
        val reply = parse_address(entry) ?: continue
        if (reply.registrable != parsed.registrable) return reply.address
    }
    return null
}

fun notification_sender_name(
    name: String?,
    email: String?,
    own_addresses: List<String>,
    contacts: List<SenderTrustContact>,
): String {
    val trust = evaluate_sender_trust(
        SenderTrustInput(name = name, email = email, own_addresses = own_addresses, contacts = contacts),
    )
    return trust.name.ifEmpty { trust.address }
}

fun evaluate_sender_trust(input: SenderTrustInput): SenderTrust {
    val parsed = parse_address(input.email)
    val sanitized = sanitize_display_name(input.name)
    val auth_state = auth_state_of(input)

    if (parsed == null) {
        val cleaned = cap_trust_code_points(clean_text(input.email.orEmpty(), false), max_address_length)
        val has_address = cleaned.isNotEmpty()
        val fallback = if (cleaned.contains('@') && !cleaned.contains(' ')) cleaned else ""
        return SenderTrust(
            name = if (has_address || claims_identity(sanitized)) "" else sanitized,
            address = fallback,
            domain = "",
            auth_state = if (has_address && auth_state != SenderAuthState.internal) {
                SenderAuthState.unverified
            } else {
                auth_state
            },
            warning = null,
            contact_notice = null,
            reply_to = null,
            show_identity_marks = false,
            show_verified_badge = false,
        )
    }

    val name_is_address = sanitized.isNotEmpty() && fold(sanitized) == fold("${parsed.local}@${parsed.ascii_domain}")
    val base_name = if (name_is_address) "" else sanitized
    val internal = auth_state == SenderAuthState.internal
    val personal = personal_context_of(input)
    val whole_name = if (name_is_address) {
        ""
    } else {
        capped_display_name(input.name, sender_trust_data.max_name_length + max_address_length)
    }
    var found_warning = find_warning(base_name, parsed, auth_state, personal)
    val checked = arrayListOf(base_name)
    for (other in listOf(whole_name, enclosed_plain(whole_name))) {
        if (found_warning != null || other in checked) continue
        checked.add(other)
        found_warning = find_warning(other, parsed, auth_state, personal)
    }
    val warning = found_warning
    val hide_name = warning != null &&
        (warning.kind != SenderWarningKind.lookalike_domain || claims_identity(base_name))
    val distrusted = auth_state == SenderAuthState.failed || auth_state == SenderAuthState.unverified
    val verified_domain = sender_domain_to_ascii(input.sender_verified_domain.orEmpty())
    val verified_matches = verified_domain.isNotEmpty() && within_domain(parsed.ascii_domain, verified_domain)

    return SenderTrust(
        name = if (hide_name) "" else base_name,
        address = parsed.address,
        domain = parsed.domain,
        auth_state = auth_state,
        warning = warning,
        contact_notice = if (warning == null && !internal) find_contact_notice(base_name, parsed, personal) else null,
        reply_to = reply_to_notice(parsed, input.reply_to),
        show_identity_marks = warning == null && !distrusted,
        show_verified_badge = input.sender_verified == true &&
            verified_matches &&
            warning == null &&
            (auth_state == SenderAuthState.authenticated || auth_state == SenderAuthState.internal),
    )
}
