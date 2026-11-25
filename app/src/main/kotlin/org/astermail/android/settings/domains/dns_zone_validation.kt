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
package org.astermail.android.settings.domains

import org.astermail.android.api.domains.DnsZoneRecord
import org.astermail.android.api.domains.DnsZoneRecordRequest

enum class DnsRecordType(val wire: String) {
    a("A"),
    aaaa("AAAA"),
    cname("CNAME"),
    txt("TXT"),
    mx("MX"),
    srv("SRV"),
    caa("CAA");

    val has_priority: Boolean get() = this == mx || this == srv

    companion object {
        fun from_wire(value: String?): DnsRecordType? =
            entries.firstOrNull { it.wire.equals(value?.trim(), ignoreCase = true) }
    }
}

enum class DnsTtlPreset(val seconds: Int) {
    one_hour(3600),
    four_hours(14400),
    twelve_hours(43200),
    one_day(86400);

    companion object {
        fun for_seconds(seconds: Int): DnsTtlPreset? = entries.firstOrNull { it.seconds == seconds }
    }
}

enum class CaaTag(val wire: String) {
    issue("issue"),
    issuewild("issuewild"),
    iodef("iodef");

    companion object {
        fun from_wire(value: String?): CaaTag? =
            entries.firstOrNull { it.wire.equals(value?.trim(), ignoreCase = true) }
    }
}

enum class DnsFieldError {
    host_invalid,
    host_too_long,
    host_wildcard,
    host_protected,
    host_apex_mx,
    host_apex_cname,
    cname_conflict,
    duplicate_record,
    spf_conflict,
    srv_host_invalid,
    value_required,
    ipv4_invalid,
    ipv6_invalid,
    ip_private,
    target_invalid,
    target_is_self,
    txt_too_long,
    txt_invalid_chars,
    spf_at_apex,
    caa_value_invalid,
    ttl_out_of_range,
    priority_required,
    priority_out_of_range,
    weight_required,
    weight_out_of_range,
    port_required,
    port_out_of_range,
}

data class DnsRecordDraft(
    val type: DnsRecordType = DnsRecordType.a,
    val host: String = "",
    val value: String = "",
    val ttl: Int = DnsTtlPreset.one_hour.seconds,
    val ttl_preset: DnsTtlPreset? = DnsTtlPreset.one_hour,
    val priority: String = "",
    val weight: String = "",
    val port: String = "",
    val caa_tag: CaaTag = CaaTag.issue,
    val caa_critical: Boolean = false,
)

data class DnsDraftErrors(
    val host: DnsFieldError? = null,
    val value: DnsFieldError? = null,
    val ttl: DnsFieldError? = null,
    val priority: DnsFieldError? = null,
    val weight: DnsFieldError? = null,
    val port: DnsFieldError? = null,
) {
    val is_valid: Boolean
        get() = host == null && value == null && ttl == null && priority == null && weight == null && port == null
}

data class DnsDraftResult(
    val errors: DnsDraftErrors,
    val request: DnsZoneRecordRequest?,
)

const val DNS_TTL_MIN = 3600
const val DNS_TTL_MAX = 86400
const val DNS_TXT_MAX = 2048
const val DNS_U16_MAX = 65535
private const val DNS_LABEL_MAX = 63
private const val DNS_NAME_MAX = 253
private const val CAA_CRITICAL_FLAG = 128
const val DNS_CAA_VALUE_MAX = 255

private val protected_hosts = setOf(
    "_aster-verify",
    "_dmarc",
    "_smtp._tls",
    "_mta-sts",
    "mta-sts",
    "default._bimi",
    "_domainkey",
)

private val srv_protocols = setOf("_tcp", "_udp", "_tls")

fun clamp_dns_ttl(seconds: Int): Int = seconds.coerceIn(DNS_TTL_MIN, DNS_TTL_MAX)

fun normalize_dns_host(raw: String, domain: String): String {
    val host = raw.trim().lowercase().trimEnd('.')
    val zone = domain.trim().lowercase().trimEnd('.')
    if (host.isEmpty() || host == "@" || host == zone) return "@"
    if (zone.isNotEmpty() && host.endsWith(".$zone")) return host.removeSuffix(".$zone").ifEmpty { "@" }
    return host
}

fun is_aster_dkim_host(host: String): Boolean {
    val selector = host.lowercase().removeSuffix("._domainkey")
    if (selector == host.lowercase() || selector.contains('.')) return false
    if (!selector.startsWith("aster")) return false
    return selector.removePrefix("aster").all { it in '0'..'9' }
}

fun is_protected_dns_host(host: String): Boolean {
    val h = host.lowercase()
    return h in protected_hosts || is_aster_dkim_host(h)
}

private fun is_valid_label(label: String, allow_underscore: Boolean = true): Boolean {
    if (label.isEmpty() || label.length > DNS_LABEL_MAX) return false
    if (label.startsWith('-') || label.endsWith('-')) return false
    return label.all { c ->
        c in 'a'..'z' || c in '0'..'9' || c == '-' || (allow_underscore && c == '_')
    }
}

fun validate_dns_host(host: String, domain: String): DnsFieldError? {
    if (host.contains('*')) return DnsFieldError.host_wildcard
    if (host == "@") return null
    if (!host.all { it.code < 0x80 }) return DnsFieldError.host_invalid
    val zone = domain.trim().lowercase().trimEnd('.')
    if (host.length + 1 + zone.length > DNS_NAME_MAX) return DnsFieldError.host_too_long
    if (!host.split('.').all { is_valid_label(it) }) return DnsFieldError.host_invalid
    if (is_protected_dns_host(host)) return DnsFieldError.host_protected
    return null
}

fun validate_srv_host(host: String): DnsFieldError? {
    val labels = host.split('.')
    if (labels.size < 2) return DnsFieldError.srv_host_invalid
    val service = labels[0]
    val proto = labels[1]
    if (!service.startsWith('_') || service.length < 2) return DnsFieldError.srv_host_invalid
    if (proto !in srv_protocols) return DnsFieldError.srv_host_invalid
    return null
}

fun normalize_dns_target(raw: String): String = raw.trim().lowercase().trimEnd('.')

fun is_valid_dns_target(raw: String): Boolean {
    val target = normalize_dns_target(raw)
    if (target.isEmpty() || target.length > DNS_NAME_MAX) return false
    val labels = target.split('.')
    if (labels.size < 2) return false
    if (!labels.all { is_valid_label(it) }) return false
    val tld = labels.last()
    if (tld.all { it in '0'..'9' }) return false
    if (tld.contains('_')) return false
    return true
}

fun parse_ipv4(raw: String): IntArray? {
    val parts = raw.split('.')
    if (parts.size != 4) return null
    val out = IntArray(4)
    parts.forEachIndexed { i, part ->
        if (part.isEmpty() || part.length > 3 || !part.all { it in '0'..'9' }) return null
        if (part.length > 1 && part[0] == '0') return null
        val n = part.toInt()
        if (n > 255) return null
        out[i] = n
    }
    return out
}

fun is_reserved_ipv4(o: IntArray): Boolean {
    val a = o[0]
    val b = o[1]
    val c = o[2]
    return when {
        a == 0 -> true
        a == 10 -> true
        a == 100 && b in 64..127 -> true
        a == 127 -> true
        a == 169 && b == 254 -> true
        a == 172 && b in 16..31 -> true
        a == 192 && b == 0 && c == 0 -> true
        a == 192 && b == 0 && c == 2 -> true
        a == 192 && b == 88 && c == 99 -> true
        a == 192 && b == 168 -> true
        a == 198 && b in 18..19 -> true
        a == 198 && b == 51 && c == 100 -> true
        a == 203 && b == 0 && c == 113 -> true
        a >= 224 -> true
        else -> false
    }
}

fun parse_ipv6(raw: String): IntArray? {
    val text = raw.trim()
    if (text.isEmpty() || text.length > 45) return null
    if (text.contains('%')) return null
    if (!text.all { it in '0'..'9' || it in 'a'..'f' || it in 'A'..'F' || it == ':' || it == '.' }) return null
    val double_colon = text.indexOf("::")
    if (double_colon >= 0 && text.indexOf("::", double_colon + 1) >= 0) return null

    fun parse_side(side: String, allow_v4_tail: Boolean): MutableList<Int>? {
        if (side.isEmpty()) return mutableListOf()
        val groups = side.split(':')
        val out = mutableListOf<Int>()
        groups.forEachIndexed { index, group ->
            if (group.isEmpty()) return null
            if (group.contains('.')) {
                if (!allow_v4_tail || index != groups.lastIndex) return null
                val v4 = parse_ipv4(group) ?: return null
                out.add((v4[0] shl 8) or v4[1])
                out.add((v4[2] shl 8) or v4[3])
            } else {
                if (group.length > 4) return null
                out.add(group.toInt(16))
            }
        }
        return out
    }

    val groups: List<Int> = if (double_colon >= 0) {
        val head = parse_side(text.substring(0, double_colon), allow_v4_tail = false) ?: return null
        val tail = parse_side(text.substring(double_colon + 2), allow_v4_tail = true) ?: return null
        val missing = 8 - head.size - tail.size
        if (missing < 1) return null
        head + List(missing) { 0 } + tail
    } else {
        val all = parse_side(text, allow_v4_tail = true) ?: return null
        if (all.size != 8) return null
        all
    }
    return groups.toIntArray()
}

fun is_reserved_ipv6(g: IntArray): Boolean = when {
    g[0] == 0 -> true
    g[0] and 0xff00 == 0xff00 -> true
    g[0] and 0xfe00 == 0xfc00 -> true
    g[0] and 0xffc0 == 0xfe80 -> true
    g[0] and 0xffc0 == 0xfec0 -> true
    g[0] == 0x2001 && g[1] == 0x0db8 -> true
    g[0] == 0x2001 && g[1] == 0 -> true
    g[0] == 0x2002 -> true
    g[0] == 0x0064 && g[1] == 0xff9b -> true
    g[0] == 0x0100 && (1..3).all { g[it] == 0 } -> true
    else -> false
}

fun is_spf_value(value: String): Boolean {
    val v = value.trim().trim('"').trim().lowercase()
    return v == "v=spf1" || v.startsWith("v=spf1 ")
}

fun validate_txt_value(value: String): DnsFieldError? {
    if (value.isEmpty()) return DnsFieldError.value_required
    if (value.length > DNS_TXT_MAX) return DnsFieldError.txt_too_long
    if (!value.all { it.code in 0x20..0x7e && it != '"' && it != '\\' }) return DnsFieldError.txt_invalid_chars
    return null
}

private fun validate_u16(
    raw: String,
    missing: DnsFieldError,
    out_of_range: DnsFieldError,
    min: Int = 0,
): Pair<Int?, DnsFieldError?> {
    val text = raw.trim()
    if (text.isEmpty()) return null to missing
    if (text.length > 5 || !text.all { it in '0'..'9' }) return null to out_of_range
    val n = text.toInt()
    if (n < min || n > DNS_U16_MAX) return null to out_of_range
    return n to null
}

private fun is_valid_iodef(value: String): Boolean {
    val lower = value.lowercase()
    if (lower.startsWith("mailto:")) {
        val address = lower.removePrefix("mailto:")
        val local = address.substringBefore('@', "")
        val host = address.substringAfter('@', "")
        return local.isNotEmpty() && !local.contains(' ') && is_valid_dns_target(host)
    }
    val rest = when {
        lower.startsWith("https://") -> lower.removePrefix("https://")
        lower.startsWith("http://") -> lower.removePrefix("http://")
        else -> return false
    }
    if (rest.contains(' ')) return false
    val host = rest.split('/', '?', '#').first()
    return is_valid_dns_target(host)
}

fun validate_caa_value(tag: CaaTag, raw: String): DnsFieldError? {
    val value = raw.trim()
    if (value.isEmpty()) return DnsFieldError.value_required
    if (value.length > DNS_CAA_VALUE_MAX) return DnsFieldError.caa_value_invalid
    if (!value.all { (it.code in 0x21..0x7e || it == ' ') && it != '"' && it != '\\' }) {
        return DnsFieldError.caa_value_invalid
    }
    return when (tag) {
        CaaTag.iodef -> if (is_valid_iodef(value)) null else DnsFieldError.caa_value_invalid
        CaaTag.issue, CaaTag.issuewild -> {
            val issuer = value.substringBefore(';').trim()
            if (issuer.isEmpty() || is_valid_dns_target(issuer)) null else DnsFieldError.caa_value_invalid
        }
    }
}

private fun same_record_value(type: DnsRecordType, a: String, b: String): Boolean = when (type) {
    DnsRecordType.txt, DnsRecordType.caa -> a.trim().trim('"') == b.trim().trim('"')
    DnsRecordType.aaaa -> {
        val x = parse_ipv6(a.trim())
        val y = parse_ipv6(b.trim())
        if (x != null && y != null) x.contentEquals(y) else a.trim().equals(b.trim(), ignoreCase = true)
    }
    DnsRecordType.a -> a.trim() == b.trim()
    else -> normalize_dns_target(a) == normalize_dns_target(b)
}

fun check_zone_conflicts(
    request: DnsZoneRecordRequest,
    existing: List<DnsZoneRecord>,
    domain: String,
    replacing_id: String?,
): DnsFieldError? {
    val type = DnsRecordType.from_wire(request.type) ?: return null
    val others = existing.filter { replacing_id == null || it.id != replacing_id }
    for (record in others) {
        if (normalize_dns_host(record.host, domain) != request.host) continue
        val other_is_cname = record.type.equals("CNAME", ignoreCase = true)
        if (type == DnsRecordType.cname || other_is_cname) return DnsFieldError.cname_conflict
        if (!record.type.equals(type.wire, ignoreCase = true)) continue
        if (type == DnsRecordType.txt && is_spf_value(request.value) && is_spf_value(record.value)) {
            return DnsFieldError.spf_conflict
        }
        val same_priority = !type.has_priority || (record.priority ?: 0) == (request.priority ?: 0)
        if (same_priority && same_record_value(type, record.value, request.value)) {
            return DnsFieldError.duplicate_record
        }
    }
    return null
}

fun validate_dns_draft(
    draft: DnsRecordDraft,
    domain: String,
    existing: List<DnsZoneRecord> = emptyList(),
    replacing_id: String? = null,
): DnsDraftResult {
    val zone = domain.trim().lowercase().trimEnd('.')
    val host = normalize_dns_host(draft.host, zone)
    var host_error = validate_dns_host(host, zone)
    var value_error: DnsFieldError? = null
    var priority_error: DnsFieldError? = null
    var weight_error: DnsFieldError? = null
    var port_error: DnsFieldError? = null
    var priority: Int? = null
    var wire_value = ""

    val ttl_error = if (draft.ttl in DNS_TTL_MIN..DNS_TTL_MAX) null else DnsFieldError.ttl_out_of_range

    when (draft.type) {
        DnsRecordType.a -> {
            val v = draft.value.trim()
            val parsed = parse_ipv4(v)
            value_error = when {
                v.isEmpty() -> DnsFieldError.value_required
                parsed == null -> DnsFieldError.ipv4_invalid
                is_reserved_ipv4(parsed) -> DnsFieldError.ip_private
                else -> null
            }
            wire_value = v
        }
        DnsRecordType.aaaa -> {
            val v = draft.value.trim().lowercase()
            val parsed = parse_ipv6(v)
            value_error = when {
                v.isEmpty() -> DnsFieldError.value_required
                parsed == null -> DnsFieldError.ipv6_invalid
                is_reserved_ipv6(parsed) -> DnsFieldError.ip_private
                else -> null
            }
            wire_value = v
        }
        DnsRecordType.cname -> {
            if (host_error == null && host == "@") host_error = DnsFieldError.host_apex_cname
            val target = normalize_dns_target(draft.value)
            value_error = when {
                target.isEmpty() -> DnsFieldError.value_required
                !is_valid_dns_target(target) -> DnsFieldError.target_invalid
                target == (if (host == "@") zone else "$host.$zone") -> DnsFieldError.target_is_self
                else -> null
            }
            wire_value = target
        }
        DnsRecordType.txt -> {
            val v = draft.value
            value_error = validate_txt_value(v)
            if (value_error == null && host == "@" && is_spf_value(v)) value_error = DnsFieldError.spf_at_apex
            wire_value = v
        }
        DnsRecordType.mx -> {
            if (host_error == null && host == "@") host_error = DnsFieldError.host_apex_mx
            val target = normalize_dns_target(draft.value)
            value_error = when {
                target.isEmpty() -> DnsFieldError.value_required
                !is_valid_dns_target(target) -> DnsFieldError.target_invalid
                else -> null
            }
            val (p, pe) = validate_u16(draft.priority, DnsFieldError.priority_required, DnsFieldError.priority_out_of_range)
            priority = p
            priority_error = pe
            wire_value = target
        }
        DnsRecordType.srv -> {
            if (host_error == null) host_error = validate_srv_host(host)
            val target = normalize_dns_target(draft.value)
            value_error = when {
                target.isEmpty() -> DnsFieldError.value_required
                !is_valid_dns_target(target) -> DnsFieldError.target_invalid
                else -> null
            }
            val (p, pe) = validate_u16(draft.priority, DnsFieldError.priority_required, DnsFieldError.priority_out_of_range)
            val (w, we) = validate_u16(draft.weight, DnsFieldError.weight_required, DnsFieldError.weight_out_of_range)
            val (port, poe) = validate_u16(draft.port, DnsFieldError.port_required, DnsFieldError.port_out_of_range, min = 1)
            priority = p
            priority_error = pe
            weight_error = we
            port_error = poe
            wire_value = "${w ?: 0} ${port ?: 0} $target"
        }
        DnsRecordType.caa -> {
            value_error = validate_caa_value(draft.caa_tag, draft.value)
            val flags = if (draft.caa_critical) CAA_CRITICAL_FLAG else 0
            wire_value = "$flags ${draft.caa_tag.wire} \"${draft.value.trim()}\""
        }
    }

    val base_errors = DnsDraftErrors(
        host = host_error,
        value = value_error,
        ttl = ttl_error,
        priority = priority_error,
        weight = weight_error,
        port = port_error,
    )
    if (!base_errors.is_valid) return DnsDraftResult(base_errors, null)
    val request = DnsZoneRecordRequest(
        type = draft.type.wire,
        host = host,
        value = wire_value,
        ttl = clamp_dns_ttl(draft.ttl),
        priority = if (draft.type.has_priority) priority else null,
    )
    val conflict = check_zone_conflicts(request, existing, zone, replacing_id)
    if (conflict != null) {
        val errors = if (conflict == DnsFieldError.cname_conflict) {
            base_errors.copy(host = conflict)
        } else {
            base_errors.copy(value = conflict)
        }
        return DnsDraftResult(errors, null)
    }
    return DnsDraftResult(base_errors, request)
}

private val caa_pattern = Regex("^\\s*(\\d{1,3})\\s+([A-Za-z0-9]+)\\s+\"?(.*?)\"?\\s*$")

fun draft_from_record(record: DnsZoneRecord): DnsRecordDraft? {
    val type = DnsRecordType.from_wire(record.type) ?: return null
    val base = DnsRecordDraft(
        type = type,
        host = record.host,
        ttl = record.ttl,
        ttl_preset = DnsTtlPreset.for_seconds(record.ttl),
        priority = record.priority?.toString().orEmpty(),
    )
    return when (type) {
        DnsRecordType.srv -> {
            val parts = record.value.trim().split(Regex("\\s+"))
            when (parts.size) {
                4 -> base.copy(
                    priority = record.priority?.toString() ?: parts[0],
                    weight = parts[1],
                    port = parts[2],
                    value = parts[3].trimEnd('.'),
                )
                3 -> base.copy(weight = parts[0], port = parts[1], value = parts[2].trimEnd('.'))
                else -> base.copy(value = record.value)
            }
        }
        DnsRecordType.caa -> {
            val match = caa_pattern.matchEntire(record.value)
            if (match == null) {
                base.copy(value = record.value)
            } else {
                val flags = match.groupValues[1].toIntOrNull() ?: 0
                base.copy(
                    caa_critical = flags and CAA_CRITICAL_FLAG != 0,
                    caa_tag = CaaTag.from_wire(match.groupValues[2]) ?: CaaTag.issue,
                    value = match.groupValues[3],
                )
            }
        }
        DnsRecordType.mx, DnsRecordType.cname -> base.copy(value = record.value.trimEnd('.'))
        else -> base.copy(value = record.value)
    }
}

fun dns_record_display_host(host: String, domain: String): String {
    val normalized = normalize_dns_host(host, domain)
    return if (normalized == "@") domain.trim().lowercase().trimEnd('.') else "$normalized.${domain.trim().lowercase().trimEnd('.')}"
}

fun dns_record_label(record: DnsZoneRecord, domain: String): String =
    "${record.type.uppercase()} ${dns_record_display_host(record.host, domain)}"
