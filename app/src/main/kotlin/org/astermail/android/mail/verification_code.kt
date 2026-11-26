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
package org.astermail.android.mail

private const val MAX_SCAN_LENGTH = 2000
private const val LONG_BODY_LENGTH = 400
private const val MIN_CODE_LENGTH = 4
private const val MAX_CODE_LENGTH = 8
private const val MIN_GROUP_LENGTH = 3
private const val MAX_GROUP_LENGTH = 4
private const val STRONG_DISTANCE = 32
private const val WEAK_DISTANCE = 20
private const val DIRECT_DISTANCE = 8
private const val MAX_GAP_WORDS = 3
private const val BLOCKING_DISTANCE = 24
private const val TECHNICAL_MARKER_LIMIT = 2

private const val BOUNDED_START = "(?<!(?-i:[\\p{L}\\p{N}]))"
private const val BOUNDED_END = "(?!(?-i:[\\p{L}\\p{N}]))"

private val BOUNDARY_SCRIPTS = setOf(
    Character.UnicodeScript.LATIN,
    Character.UnicodeScript.CYRILLIC,
    Character.UnicodeScript.GREEK,
    Character.UnicodeScript.ARABIC,
    Character.UnicodeScript.HEBREW,
    Character.UnicodeScript.DEVANAGARI,
)

private val CODE_TOKEN = Regex("[A-Za-z0-9]+")
private val WORD = Regex("(?-i:[\\p{L}\\p{N}])+")
private val SENTENCE_BREAK = Regex("[.!?。！？](?:\\s|$)")
private val UNIT_SUFFIX = Regex("^[0-9]+(?:ST|ND|RD|TH|AM|PM|K|M|P|GB|MB|KB|TB|PX|MP|HZ|FPS)$")
private val SENDER_BRACKET = Regex("<([^<>]+)>\\s*$")
private val INTENT_PREFIX = Regex("^(?:PIN|OTP|CODE|TAN|KOD)$", RegexOption.IGNORE_CASE)
private val HYPHEN_INTENT = Regex("^(?:pin|otp|code|tan|kod|код)$", RegexOption.IGNORE_CASE)

private val REPLY_PREFIX = Regex("^\\s*(?:(?:re|fwd?|aw|wg|tr|rv|sv|vs|antw|odp|enc|r|ynt|ilt|ответ|отв|пересл|回复|答复|转发|回覆|轉寄)\\s*(?:\\[\\d+\\]|\\(\\d+\\))?\\s*[:：]\\s*)+", RegexOption.IGNORE_CASE)

private val AUTOMATED_LOCAL_PART = Regex("^(?:no[-_.]?reply|do[-_.]?not[-_.]?reply|notifications?|notify|alerts?|security|verify|verification|accounts?|auth|login|signin|otp|codes?|mailer(?:-daemon)?|system|automated|robot|bot)(?:[-_.+][a-z0-9._+-]*)?\$", RegexOption.IGNORE_CASE)

private val PROMOTION_PATTERN = Regex("(?<!(?-i:[\\p{L}\\p{N}]))(?:gift ?cards?|promo(?-i:\\p{L})*|coupons?|discount(?-i:\\p{L})*|vouchers?|rabatt(?-i:\\p{L})*|gutschein(?-i:\\p{L})*|descuento(?-i:\\p{L})*|cup[oó]n(?-i:\\p{L})*|réduction(?-i:\\p{L})*|sconto|скидк(?-i:\\p{L})*)|[0-9]{1,3} ?% off|优惠|折扣|割引", RegexOption.IGNORE_CASE)

private val QUOTE_MARKERS = listOf(
    Regex("(?:^|\\s)On\\s[^\\n]{1,200}?\\swrote:", RegexOption.IGNORE_CASE),
    Regex("(?:^|\\s)(?:Am|Le|El|Il|Op|Em|W dniu)\\s[^\\n]{1,200}?\\s(?:schrieb|a écrit|escribió|ha scritto|schreef|napisał(?-i:\\p{L})*|escreveu)\\s?(?-i:\\p{L})*:", RegexOption.IGNORE_CASE),
    Regex("-{2,}\\s*(?:original message|forwarded message|weitergeleitete nachricht|ursprüngliche nachricht|message transféré|message d'origine|mensaje reenviado|mensaje original|messaggio inoltrato|messaggio originale|doorgestuurd bericht|oorspronkelijk bericht|wiadomość przekazana|oryginalna wiadomość|mensagem encaminhada|mensagem original|пересылаемое сообщение|исходное сообщение)", RegexOption.IGNORE_CASE),
    Regex("(?:^|\\n)[ \\t]*>"),
    Regex("(?:^|\\n)\\s*(?:From|Von|De|Da|Van|Od|От|Kimden):[^\\n]*\\n\\s*(?:Sent|Date|Gesendet|Datum|Envoyé|Enviado|Fecha|Inviato|Data|Verzonden|Wysłano|Отправлено|Tarih):", RegexOption.IGNORE_CASE),
)

private val STRIPPED_SEGMENTS = listOf(
    Regex("```[\\s\\S]*?(?:```|\$)"),
    Regex("`[^`\\n]*`"),
    Regex("^(?: {4}|\\t).*\$", RegexOption.MULTILINE),
    Regex("(?:https?:\\/\\/|www\\.)\\S+", RegexOption.IGNORE_CASE),
    Regex("(?-i:[\\p{L}\\p{N}_-])+(?:\\.(?-i:[\\p{L}\\p{N}_-])+)+\\/\\S*"),
    Regex("\\S+@\\S+"),
)

private val TECHNICAL_MARKERS = listOf(
    Regex("(?<!(?-i:[\\p{L}\\p{N}]))(?=[0-9a-f]*[a-f])(?=[0-9a-f]*[0-9])[0-9a-f]{7,40}(?!(?-i:[\\p{L}\\p{N}]))"),
    Regex("(?-i:[\\p{L}\\p{N}_/.-])+\\.(?:rs|ts|tsx|js|jsx|mjs|kt|kts|swift|py|go|java|rb|php|c|cc|cpp|h|hpp|cs|sql|sh|yml|yaml|toml|gradle)(?!(?-i:[\\p{L}\\p{N}]))", RegexOption.IGNORE_CASE),
    Regex("(?<!(?-i:[\\p{L}\\p{N}]))v[0-9]+\\.[0-9]+(?:\\.[0-9]+)*(?!(?-i:[\\p{L}\\p{N}]))", RegexOption.IGNORE_CASE),
    Regex("(?<!(?-i:[\\p{L}\\p{N}]))(?:commits?|branch|pull request|merge request|stack ?trace|regression|segfault|null pointer|exception|cve-[0-9]{4}-[0-9]+|ghsa-(?-i:[\\p{L}\\p{N}-])+|line [0-9]+)(?!(?-i:[\\p{L}\\p{N}]))", RegexOption.IGNORE_CASE),
)

private val BEFORE_REJECT = listOf(
    Regex("[\$€£¥₹₽₩#+/\\\\@=_&?%~^*|]\$"),
    Regex("[\$€£¥₹₽₩]\\s\$"),
    Regex("[0-9][ \\-./:,]\$"),
    Regex("\\)\\s?\$"),
    Regex("(?:^|(?-i:[^\\p{L}]))(?:usd|eur|gbp|chf|jpy|inr|cad|aud|rs\\.?|r\\\$)\\s?\$", RegexOption.IGNORE_CASE),
)

private val AFTER_REJECT = listOf(
    Regex("^[.,:/](?-i:[\\p{L}\\p{N}])"),
    Regex("^[ -][0-9]"),
    Regex("^-(?-i:[\\p{L}\\p{N}])"),
    Regex("^[@%\$€£¥₹₽₩_=/&]"),
    Regex("^\\s?(?:%|[\$€£¥₹₽₩]|[ap]\\.?m\\.?(?!(?-i:\\p{L}))|utc|gmt|cet|cest|est|edt|pst|pdt|mins?(?!(?-i:\\p{L}))|minutes|hours?|hrs?|secs?(?!(?-i:\\p{L}))|seconds|days?(?!(?-i:\\p{L}))|weeks?|months?|years?|[kmgt]b(?!(?-i:\\p{L}))|kg(?!(?-i:\\p{L}))|km(?!(?-i:\\p{L}))|px(?!(?-i:\\p{L}))|points?|coins?|credits?|users?|people|times|items?|messages|emails)(?!(?-i:\\p{L}))", RegexOption.IGNORE_CASE),
    Regex("^\\s?(?:usd|eur|gbp|chf|jpy|inr|cad|aud|dollars?|euros?)(?!(?-i:\\p{L}))", RegexOption.IGNORE_CASE),
)

private val STRONG_BOUNDED = listOf(
    "(?:verification|verify|confirmation|security|sign[- ]?in|log[- ]?in|login|logon|access|authentication|authenticator|auth|authorization|one[- ]?time|single[- ]?use|2fa|two[- ]?factor|2[- ]?step|mfa|otp|temporary|activation|reset|recovery|device|pairing|approval)\\s+(?:pass)?codes?",
    "one[- ]?time\\s+(?:password|passcode|pin)",
    "passcodes?",
    "otp",
    "your(?:\\s+(?-i:[\\p{L}\\p{N}-])+){0,2}?\\s+(?:codes?|pin|passcode)",
    "(?:enter|use)\\s+(?:this|the\\s+following|the)\\s+code",
    "(?:best[äa]tigungs|verifizierungs|sicherheits|anmelde|einmal|zugangs|freigabe|login)code",
    "einmal(?:passwort|kennwort)",
    "(?:ihr|dein|deinen|ihren)\\s+code",
    "c[oó]digo\\s+de\\s+(?:verificaci[oó]n|verifica[cç][aã]o|seguridad|seguran[cç]a|acceso|acesso|confirmaci[oó]n|confirma[cç][aã]o|inicio\\s+de\\s+sesi[oó]n|login|un\\s+solo\\s+uso)",
    "(?:tu|su|seu|teu)\\s+c[oó]digo",
    "code\\s+de\\s+(?:v[eé]rification|s[eé]curit[eé]|connexion|confirmation|validation)",
    "(?:votre|ton)\\s+code",
    "codice\\s+(?:di\\s+(?:verifica|sicurezza|accesso|conferma)|otp)",
    "(?:il\\s+)?(?:tuo|suo)\\s+codice",
    "(?:verificatie|beveiligings|inlog|bevestigings|toegangs)code",
    "(?:je|jouw|uw)\\s+code",
    "kod\\s+(?:weryfikacyjny|bezpiecze[nń]stwa|potwierdzaj[aą]cy|logowania|jednorazowy|dost[eę]pu|sms)",
    "(?:tw[oó]j|pa[nń]ski)\\s+kod",
    "(?:do[gğ]rulama|g[uü]venlik|giri[sş]|onay)\\s+kod(?-i:\\p{L})*",
    "kodunuz",
    "код\\s+(?:подтверждения|проверки|безопасности|доступа|для\\s+входа|верификации)",
    "(?:ваш|твой)\\s+код",
    "одноразов(?-i:\\p{L})*\\s+(?:код|пароль)",
    "(?:رمز|كود)\\s+(?:التحقق|التأكيد|الأمان|الدخول|التفعيل)",
    "(?:सत्यापन|ओटीपी)\\s*कोड",
    "ओटीपी",
)

private val STRONG_BARE = listOf(
    "验证码",
    "驗證碼",
    "校验码",
    "校驗碼",
    "动态码",
    "确认码",
    "確認碼",
    "安全码",
    "登录码",
    "認証コード",
    "確認コード",
    "ワンタイムパスワード",
    "セキュリティコード",
    "ログインコード",
    "認証番号",
    "確認番号",
    "인증\\s?번호",
    "인증\\s?코드",
    "확인\\s?코드",
    "보안\\s?코드",
    "일회용\\s?비밀번호",
)

private val WEAK_BOUNDED = listOf(
    "codes?",
    "v[eé]rif(?-i:\\p{L})*",
    "sign(?:ing)?[- ]?in",
    "log(?:ging)?[- ]?in",
    "logon",
    "auth(?-i:\\p{L})*",
    "confirm(?-i:\\p{L})*",
    "pin",
    "kod(?-i:\\p{L})*",
    "c[oó]digo",
    "codice",
    "код(?-i:\\p{L}){0,3}",
    "best[äa]tig(?-i:\\p{L})*",
    "anmeld(?-i:\\p{L})*",
    "connexion",
    "acceso",
    "accesso",
    "weryfik(?-i:\\p{L})*",
    "do[gğ]rula(?-i:\\p{L})*",
    "подтвер(?-i:\\p{L})*",
)

private val WEAK_BARE = listOf(
    "コード",
    "코드",
    "رمز",
    "कोड",
)

private val BLOCKING_WORDS = listOf(
    "orders?",
    "tracking",
    "track",
    "invoice",
    "receipt",
    "shipment",
    "parcel",
    "package",
    "ref",
    "reference",
    "case",
    "ticket",
    "booking",
    "reservation",
    "flight",
    "tel",
    "phone",
    "telephone",
    "mobile",
    "fax",
    "call",
    "ring",
    "id",
    "ip",
    "version",
    "build",
    "release",
    "patch",
    "zip",
    "postal",
    "postcode",
    "error",
    "status",
    "line",
    "lines",
    "commit",
    "pr",
    "issue",
    "port",
    "pid",
    "job",
    "pipeline",
    "room",
    "step",
    "page",
    "section",
    "chapter",
    "account",
    "customer",
    "member",
    "membership",
    "transaction",
    "box",
    "store",
    "item",
    "sku",
    "model",
    "serial",
    "fingerprint",
    "hash",
    "key",
    "number",
    "no",
    "nr",
    "num",
    "nummer",
    "número",
    "numéro",
    "numero",
    "pedido",
    "commande",
    "ordine",
    "bestellung",
    "sendung",
    "rechnung",
    "factura",
    "facture",
    "fattura",
    "заказа?",
    "номер",
)

private val BLOCKING_BARE = listOf(
    "订单",
    "注文",
    "快递",
)

private val CODE_QUALIFIERS = setOf(
    "promo",
    "promotional",
    "discount",
    "coupon",
    "voucher",
    "referral",
    "gift",
    "invite",
    "invitation",
    "postal",
    "zip",
    "area",
    "country",
    "post",
    "bar",
    "qr",
    "source",
    "error",
    "status",
    "exit",
    "http",
    "dress",
    "tracking",
    "product",
    "color",
    "colour",
    "region",
    "airport",
    "swift",
    "sort",
    "booking",
    "order",
    "reservation",
    "flight",
    "ticket",
    "rabatt",
    "gutschein",
    "aktions",
    "fehler",
    "réduction",
    "descuento",
    "sconto",
    "regalo",
    "pedido",
    "commande",
    "ordine",
    "postleitzahl",
)

private val CODE_FOLLOWERS = setOf(
    "review",
    "reviews",
    "block",
    "blocks",
    "base",
    "snippet",
    "path",
    "paths",
    "change",
    "changes",
    "quality",
    "owner",
    "owners",
    "of",
    "editor",
    "sample",
    "samples",
    "coverage",
    "style",
    "freeze",
    "smell",
)

private val STRONG_PATTERN = bounded_pattern(STRONG_BOUNDED, STRONG_BARE)
private val WEAK_PATTERN = bounded_pattern(WEAK_BOUNDED, WEAK_BARE)
private val BLOCKING_PATTERN = bounded_pattern(BLOCKING_WORDS, BLOCKING_BARE)

private class code_span(val start: Int, val end: Int, val value: String = "")

private class code_link(val distance: Int, val words: Int)

private fun bounded_pattern(words: List<String>, bare: List<String>): Regex {
    val parts = mutableListOf("$BOUNDED_START(?:${words.joinToString("|")})$BOUNDED_END")
    if (bare.isNotEmpty()) parts.add("(?:${bare.joinToString("|")})")
    return Regex(parts.joinToString("|"), RegexOption.IGNORE_CASE)
}

private fun find_spans(text: String, pattern: Regex): MutableList<code_span> =
    pattern.findAll(text).map { code_span(it.range.first, it.range.last + 1) }.toMutableList()

private fun word_before(text: String, index: Int): String {
    var cursor = index - 1
    while (cursor >= 0 && !text[cursor].isLetter()) {
        if (text[cursor] == '\n') return ""
        cursor--
    }
    val end = cursor + 1
    while (cursor >= 0 && text[cursor].isLetter()) cursor--
    return text.substring(cursor + 1, end).lowercase()
}

private fun word_after(text: String, index: Int): String {
    var cursor = index
    while (cursor < text.length && !text[cursor].isLetter()) {
        if (text[cursor] == '\n') return ""
        cursor++
    }
    val start = cursor
    while (cursor < text.length && text[cursor].isLetter()) cursor++
    return text.substring(start, cursor).lowercase()
}

private fun is_qualified(text: String, span: code_span): Boolean {
    if (word_before(text, span.start) in CODE_QUALIFIERS) return true
    val inner = WORD.findAll(text.substring(span.start, span.end).lowercase()).map { it.value }
    if (inner.any { it in CODE_QUALIFIERS }) return true
    return word_after(text, span.end) in CODE_FOLLOWERS
}

private fun overlaps(a: code_span, b: code_span): Boolean = a.start < b.end && b.start < a.end

private fun cut_quoted(body: String): String {
    var cut = body.length
    for (marker in QUOTE_MARKERS) {
        val match = marker.find(body)
        if (match != null && match.range.first < cut) cut = match.range.first
    }
    return body.substring(0, cut)
}

private fun strip_segments(text: String): String =
    STRIPPED_SEGMENTS.fold(text) { result, pattern -> pattern.replace(result, " ") }

private fun sender_is_automated(sender: String?): Boolean {
    if (sender.isNullOrEmpty()) return false
    val bracket = SENDER_BRACKET.find(sender)
    val address = (bracket?.groupValues?.get(1) ?: sender).trim()
    val at = address.lastIndexOf('@')
    if (at <= 0) return false
    return AUTOMATED_LOCAL_PART.containsMatchIn(address.substring(0, at))
}

private fun is_technical(text: String): Boolean {
    var total = 0
    for (pattern in TECHNICAL_MARKERS) {
        total += pattern.findAll(text).count()
        if (total >= TECHNICAL_MARKER_LIMIT) return true
    }
    return false
}

private fun is_boundary_char(char: Char?): Boolean {
    if (char == null) return false
    if (char.isDigit() || char == '_') return true
    if (!char.isLetter()) return false
    return Character.UnicodeScript.of(char.code) in BOUNDARY_SCRIPTS
}

private fun is_bounded(text: String, start: Int, end: Int): Boolean =
    !is_boundary_char(text.getOrNull(start - 1)) && !is_boundary_char(text.getOrNull(end))

private fun is_digits(value: String): Boolean = value.isNotEmpty() && value.all { it in '0'..'9' }

private fun is_code_chars(value: String): Boolean =
    value.isNotEmpty() && value.all { it in 'A'..'Z' || it in '0'..'9' }

private fun has_digit(value: String): Boolean = value.any { it in '0'..'9' }

private fun is_time_pair(first: String, second: String): Boolean {
    if (first.length != 4 || second.length != 4) return false
    if (!is_digits(first) || !is_digits(second)) return false
    return listOf(first, second).all {
        it.substring(0, 2).toInt() <= 23 && it.substring(2, 4).toInt() <= 59
    }
}

private fun is_plausible_code(value: String): Boolean {
    if (value.length !in MIN_CODE_LENGTH..MAX_CODE_LENGTH) return false
    if (!is_code_chars(value) || !has_digit(value)) return false
    if (UNIT_SUFFIX.matches(value)) return false
    val has_letter = value.any { it in 'A'..'Z' }
    if (has_letter && value.length >= 7 && value.all { it in '0'..'9' || it in 'A'..'F' }) return false
    if (is_digits(value) && value.length == 4 && value.toInt() in 1900..2099) return false
    return true
}

private fun hyphen_prefix_allowed(text: String, hyphen: Int): Boolean {
    var cursor = hyphen - 1
    while (cursor >= 0 && text[cursor].isLetter()) cursor--
    val word = text.substring(cursor + 1, hyphen)
    if (cursor >= 0 && is_boundary_char(text[cursor])) return false
    if (word.length == 1 && word[0] in 'A'..'Z') return true
    return HYPHEN_INTENT.matches(word)
}

private fun colon_prefix_allowed(text: String, colon: Int): Boolean {
    var cursor = colon - 1
    while (cursor >= 0 && !text[cursor].isWhitespace()) cursor--
    return text.substring(cursor + 1, colon).none { it == '.' || it == '/' || it == '\\' || it.isDigit() }
}

private fun has_clean_context(text: String, span: code_span): Boolean {
    val previous = text.getOrNull(span.start - 1)
    if (previous == '-' && !hyphen_prefix_allowed(text, span.start - 1)) return false
    if (previous == '.') return false
    if ((previous == ':' || previous == '：') && !colon_prefix_allowed(text, span.start - 1)) return false
    val before = text.substring(maxOf(0, span.start - 8), span.start)
    val after = text.substring(span.end, minOf(text.length, span.end + 12))
    if (previous != '-' && BEFORE_REJECT.any { it.containsMatchIn(before) }) return false
    if (AFTER_REJECT.any { it.containsMatchIn(after) }) return false
    return true
}

private fun can_join(first: String, second: String, separator: Char): Boolean {
    if (separator != ' ' && separator != '-') return false
    if (first.length < MIN_GROUP_LENGTH || second.length < MIN_GROUP_LENGTH) return false
    if (first.length > MAX_GROUP_LENGTH || second.length > MAX_GROUP_LENGTH) return false
    if (kotlin.math.abs(first.length - second.length) > 1) return false
    if (!is_code_chars(first) || !is_code_chars(second)) return false
    if (INTENT_PREFIX.matches(first)) return false
    if (separator == ' ' && (!has_digit(first) || !has_digit(second))) return false
    if (is_time_pair(first, second)) return false
    return has_digit(first) || has_digit(second)
}

private fun collect_candidates(text: String): List<code_span> {
    val tokens = CODE_TOKEN.findAll(text)
        .map { code_span(it.range.first, it.range.last + 1) }
        .filter { is_bounded(text, it.start, it.end) }
        .toList()
    val candidates = mutableListOf<code_span>()
    var index = 0
    while (index < tokens.size) {
        val token = tokens[index]
        val value = text.substring(token.start, token.end)
        val next = tokens.getOrNull(index + 1)
        if (next != null && next.start == token.end + 1) {
            val next_value = text.substring(next.start, next.end)
            val joined = code_span(token.start, next.end, value + next_value)
            if (can_join(value, next_value, text[token.end]) &&
                is_plausible_code(joined.value) &&
                has_clean_context(text, joined)
            ) {
                candidates.add(joined)
                index += 2
                continue
            }
        }
        if (is_plausible_code(value) && has_clean_context(text, token)) {
            candidates.add(code_span(token.start, token.end, value))
        }
        index++
    }
    return candidates
}

private fun link_to(text: String, candidate: code_span, spans: List<code_span>): code_link? {
    var best: code_link? = null
    for (span in spans) {
        if (overlaps(span, candidate)) continue
        val gap = if (span.end <= candidate.start) {
            text.substring(span.end, candidate.start)
        } else {
            text.substring(candidate.end, span.start)
        }
        if (has_digit(gap) || SENTENCE_BREAK.containsMatchIn(gap)) continue
        val words = WORD.findAll(gap).count()
        if (words > MAX_GAP_WORDS) continue
        if (best == null || gap.length < best.distance) best = code_link(gap.length, words)
    }
    return best
}

private fun opens_later_sentence(text: String, body_start: Int, position: Int): Boolean {
    if (position < body_start) return false
    return SENTENCE_BREAK.containsMatchIn(text.substring(body_start, position))
}

private fun nearest_blocking(candidate: code_span, spans: List<code_span>): Int {
    var nearest = Int.MAX_VALUE
    for (span in spans) {
        if (overlaps(span, candidate)) return -1
        val distance = if (span.end <= candidate.start) candidate.start - span.end else span.start - candidate.end
        if (distance < nearest) nearest = distance
    }
    return nearest
}

fun detect_verification_code(subject: String?, body: String? = null, sender: String? = null): String? {
    val raw_subject = (subject ?: "").take(MAX_SCAN_LENGTH)
    val raw_body = (body ?: "").take(MAX_SCAN_LENGTH)
    val is_reply = REPLY_PREFIX.containsMatchIn(raw_subject)
    val clean_subject = strip_segments(REPLY_PREFIX.replaceFirst(raw_subject, "")).trim()
    val clean_body = strip_segments(cut_quoted(raw_body)).trim()
    val text = listOf(clean_subject, clean_body).filter { it.isNotEmpty() }.joinToString("\n")
    if (text.isEmpty()) return null
    if (PROMOTION_PATTERN.containsMatchIn(text)) return null
    if (is_technical("$raw_subject\n$raw_body")) return null

    val human_reply = is_reply && !sender_is_automated(sender)
    val long_body = clean_body.length > LONG_BODY_LENGTH
    val blocking = find_spans(text, BLOCKING_PATTERN)
    val strong = mutableListOf<code_span>()
    val weak = mutableListOf<code_span>()
    for (span in find_spans(text, STRONG_PATTERN)) {
        if (is_qualified(text, span)) blocking.add(span) else strong.add(span)
    }
    for (span in find_spans(text, WEAK_PATTERN)) {
        if (is_qualified(text, span)) blocking.add(span)
        else if (blocking.none { overlaps(it, span) }) weak.add(span)
    }
    if (strong.isEmpty() && weak.isEmpty()) return null

    val body_start = if (clean_subject.isNotEmpty()) clean_subject.length + 1 else 0

    var best_value: String? = null
    var best_score = Int.MAX_VALUE
    for (candidate in collect_candidates(text)) {
        val strong_link = link_to(text, candidate, strong)
        val weak_link = link_to(text, candidate, weak)
        var distance: Int? = null
        var score = 0
        if (human_reply) {
            if (!long_body && !opens_later_sentence(text, body_start, candidate.start) &&
                strong_link != null &&
                strong_link.distance <= DIRECT_DISTANCE && strong_link.words <= 1
            ) {
                distance = strong_link.distance
                score = distance
            }
        } else if (strong_link != null && strong_link.distance <= STRONG_DISTANCE) {
            distance = strong_link.distance
            score = distance
        } else if (!long_body && weak_link != null && weak_link.distance <= WEAK_DISTANCE) {
            distance = weak_link.distance
            score = STRONG_DISTANCE + distance
        }
        if (distance == null) continue
        val blocking_distance = nearest_blocking(candidate, blocking)
        if (blocking_distance < 0) continue
        if (blocking_distance <= distance && blocking_distance <= BLOCKING_DISTANCE) continue
        if (score < best_score) {
            best_value = candidate.value
            best_score = score
        }
    }
    return best_value
}
