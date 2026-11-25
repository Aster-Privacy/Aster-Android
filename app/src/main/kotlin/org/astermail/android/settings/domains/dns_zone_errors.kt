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

import androidx.annotation.StringRes
import java.io.IOException
import org.astermail.android.R
import org.astermail.android.api.ApiError

enum class DnsZoneReason {
    zone_not_managed,
    locked,
    protected_record,
    dkim_cname,
    step_up,
    spf_conflict,
    cname_conflict,
    apex_mx,
    apex_cname,
    apex_spf,
    duplicate,
    cname_self,
    unsupported_type,
    wildcard,
    invalid_srv_value,
    type_change,
    invalid_host,
    invalid_value,
    invalid_ip,
    private_ip,
    invalid_target,
    txt_too_long,
    invalid_txt,
    invalid_ttl,
    invalid_priority,
    invalid_port,
    invalid_weight,
    invalid_srv_host,
    invalid_caa_flags,
    invalid_caa_tag,
    invalid_caa_value,
    missing_field,
    protected_host,
    too_many_records,
    registrar_busy,
    rate_limited,
    not_found,
    offline,
    server,
    generic,
}

private val reason_by_code: Map<String, DnsZoneReason> = mapOf(
    "zone_not_managed" to DnsZoneReason.zone_not_managed,
    "not_managed" to DnsZoneReason.zone_not_managed,
    "locked" to DnsZoneReason.locked,
    "zone_locked" to DnsZoneReason.locked,
    "protected_record" to DnsZoneReason.protected_record,
    "record_protected" to DnsZoneReason.protected_record,
    "dkim_cname" to DnsZoneReason.dkim_cname,
    "step_up_required" to DnsZoneReason.step_up,
    "step_up" to DnsZoneReason.step_up,
    "spf_conflict" to DnsZoneReason.spf_conflict,
    "cname_conflict" to DnsZoneReason.cname_conflict,
    "apex_mx" to DnsZoneReason.apex_mx,
    "apex_cname" to DnsZoneReason.apex_cname,
    "apex_spf" to DnsZoneReason.apex_spf,
    "duplicate" to DnsZoneReason.duplicate,
    "duplicate_record" to DnsZoneReason.duplicate,
    "cname_self" to DnsZoneReason.cname_self,
    "unsupported_type" to DnsZoneReason.unsupported_type,
    "wildcard_not_allowed" to DnsZoneReason.wildcard,
    "wildcard" to DnsZoneReason.wildcard,
    "invalid_srv_value" to DnsZoneReason.invalid_srv_value,
    "type_change" to DnsZoneReason.type_change,
    "invalid_host" to DnsZoneReason.invalid_host,
    "invalid_value" to DnsZoneReason.invalid_value,
    "invalid_ip" to DnsZoneReason.invalid_ip,
    "private_ip" to DnsZoneReason.private_ip,
    "invalid_target" to DnsZoneReason.invalid_target,
    "txt_too_long" to DnsZoneReason.txt_too_long,
    "invalid_txt" to DnsZoneReason.invalid_txt,
    "invalid_ttl" to DnsZoneReason.invalid_ttl,
    "invalid_priority" to DnsZoneReason.invalid_priority,
    "invalid_port" to DnsZoneReason.invalid_port,
    "invalid_weight" to DnsZoneReason.invalid_weight,
    "invalid_srv_host" to DnsZoneReason.invalid_srv_host,
    "invalid_caa_flags" to DnsZoneReason.invalid_caa_flags,
    "invalid_caa_tag" to DnsZoneReason.invalid_caa_tag,
    "invalid_caa_value" to DnsZoneReason.invalid_caa_value,
    "missing_field" to DnsZoneReason.missing_field,
    "protected_host" to DnsZoneReason.protected_host,
    "too_many_records" to DnsZoneReason.too_many_records,
    "registrar_busy" to DnsZoneReason.registrar_busy,
    "rate_limited" to DnsZoneReason.rate_limited,
)

fun dns_zone_reason_for_code(code: String?): DnsZoneReason? {
    val key = code?.trim()?.lowercase()?.takeIf { it.isNotEmpty() } ?: return null
    return reason_by_code[key] ?: reason_by_code[key.removePrefix("dns_")]
}

private fun error_codes(t: Throwable): List<String?> = when (t) {
    is ApiError.ValidationError -> listOf(t.code, t.details["reason"])
    is ApiError.ForbiddenError -> listOf(t.code, t.details["reason"])
    is ApiError.Conflict -> listOf(t.code)
    is ApiError.RateLimited -> listOf(t.code, t.details["reason"])
    is ApiError.PreconditionRequired -> listOf(t.code, t.details["reason"])
    else -> emptyList()
}

fun dns_zone_reason(t: Throwable): DnsZoneReason {
    error_codes(t).firstNotNullOfOrNull { dns_zone_reason_for_code(it) }?.let { return it }
    return when (t) {
        is ApiError.StepUpRequired -> DnsZoneReason.step_up
        is ApiError.RateLimited -> DnsZoneReason.rate_limited
        is ApiError.NotFoundError -> DnsZoneReason.not_found
        is ApiError.NetworkError, is IOException -> DnsZoneReason.offline
        is ApiError.ServerError -> if (t.code == 503) DnsZoneReason.registrar_busy else DnsZoneReason.server
        is ApiError.ForbiddenError -> DnsZoneReason.locked
        is ApiError.Conflict -> DnsZoneReason.duplicate
        is ApiError.ValidationError -> DnsZoneReason.invalid_value
        else -> DnsZoneReason.generic
    }
}

fun is_dns_zone_unavailable(t: Throwable): Boolean {
    if (t is ApiError.NotFoundError) return true
    if (t is ApiError.ServerError && t.code == 501) return true
    return dns_zone_reason(t) == DnsZoneReason.zone_not_managed
}

@StringRes
fun dns_zone_reason_res(reason: DnsZoneReason): Int = when (reason) {
    DnsZoneReason.zone_not_managed -> R.string.dns_zone_error_not_managed
    DnsZoneReason.locked -> R.string.dns_zone_error_locked
    DnsZoneReason.protected_record -> R.string.dns_zone_error_protected_record
    DnsZoneReason.dkim_cname -> R.string.dns_zone_error_dkim_cname
    DnsZoneReason.step_up -> R.string.dns_zone_error_step_up
    DnsZoneReason.spf_conflict -> R.string.dns_field_spf_conflict
    DnsZoneReason.cname_conflict -> R.string.dns_field_cname_conflict
    DnsZoneReason.apex_mx -> R.string.dns_field_host_apex_mx
    DnsZoneReason.apex_cname -> R.string.dns_field_host_apex_cname
    DnsZoneReason.apex_spf -> R.string.dns_field_spf_at_apex
    DnsZoneReason.duplicate -> R.string.dns_field_duplicate_record
    DnsZoneReason.cname_self -> R.string.dns_field_target_is_self
    DnsZoneReason.unsupported_type -> R.string.dns_zone_error_unsupported_type
    DnsZoneReason.wildcard -> R.string.dns_field_host_wildcard
    DnsZoneReason.invalid_srv_value -> R.string.dns_zone_error_invalid_srv_value
    DnsZoneReason.type_change -> R.string.dns_zone_error_type_change
    DnsZoneReason.invalid_host -> R.string.dns_field_host_invalid
    DnsZoneReason.invalid_value -> R.string.dns_zone_error_invalid_value
    DnsZoneReason.invalid_ip -> R.string.dns_zone_error_invalid_ip
    DnsZoneReason.private_ip -> R.string.dns_field_ip_private
    DnsZoneReason.invalid_target -> R.string.dns_field_target_invalid
    DnsZoneReason.txt_too_long -> R.string.dns_field_txt_too_long
    DnsZoneReason.invalid_txt -> R.string.dns_field_txt_invalid_chars
    DnsZoneReason.invalid_ttl -> R.string.dns_field_ttl_out_of_range
    DnsZoneReason.invalid_priority -> R.string.dns_field_priority_out_of_range
    DnsZoneReason.invalid_port -> R.string.dns_field_port_out_of_range
    DnsZoneReason.invalid_weight -> R.string.dns_field_weight_out_of_range
    DnsZoneReason.invalid_srv_host -> R.string.dns_field_srv_host_invalid
    DnsZoneReason.invalid_caa_flags -> R.string.dns_zone_error_invalid_caa_flags
    DnsZoneReason.invalid_caa_tag -> R.string.dns_zone_error_invalid_caa_tag
    DnsZoneReason.invalid_caa_value -> R.string.dns_field_caa_value_invalid
    DnsZoneReason.missing_field -> R.string.dns_field_value_required
    DnsZoneReason.protected_host -> R.string.dns_field_host_protected
    DnsZoneReason.too_many_records -> R.string.dns_zone_error_too_many_records
    DnsZoneReason.registrar_busy -> R.string.dns_zone_error_registrar_busy
    DnsZoneReason.rate_limited -> R.string.dns_zone_error_rate_limited
    DnsZoneReason.not_found -> R.string.dns_zone_error_not_found
    DnsZoneReason.offline -> R.string.dns_zone_error_offline
    DnsZoneReason.server -> R.string.dns_zone_error_server
    DnsZoneReason.generic -> R.string.dns_zone_error_generic
}

@StringRes
fun dns_field_error_res(error: DnsFieldError): Int = when (error) {
    DnsFieldError.host_invalid -> R.string.dns_field_host_invalid
    DnsFieldError.host_too_long -> R.string.dns_field_host_too_long
    DnsFieldError.host_wildcard -> R.string.dns_field_host_wildcard
    DnsFieldError.host_protected -> R.string.dns_field_host_protected
    DnsFieldError.host_apex_mx -> R.string.dns_field_host_apex_mx
    DnsFieldError.host_apex_cname -> R.string.dns_field_host_apex_cname
    DnsFieldError.cname_conflict -> R.string.dns_field_cname_conflict
    DnsFieldError.duplicate_record -> R.string.dns_field_duplicate_record
    DnsFieldError.spf_conflict -> R.string.dns_field_spf_conflict
    DnsFieldError.srv_host_invalid -> R.string.dns_field_srv_host_invalid
    DnsFieldError.value_required -> R.string.dns_field_value_required
    DnsFieldError.ipv4_invalid -> R.string.dns_field_ipv4_invalid
    DnsFieldError.ipv6_invalid -> R.string.dns_field_ipv6_invalid
    DnsFieldError.ip_private -> R.string.dns_field_ip_private
    DnsFieldError.target_invalid -> R.string.dns_field_target_invalid
    DnsFieldError.target_is_self -> R.string.dns_field_target_is_self
    DnsFieldError.txt_too_long -> R.string.dns_field_txt_too_long
    DnsFieldError.txt_invalid_chars -> R.string.dns_field_txt_invalid_chars
    DnsFieldError.spf_at_apex -> R.string.dns_field_spf_at_apex
    DnsFieldError.caa_value_invalid -> R.string.dns_field_caa_value_invalid
    DnsFieldError.ttl_out_of_range -> R.string.dns_field_ttl_out_of_range
    DnsFieldError.priority_required -> R.string.dns_field_priority_required
    DnsFieldError.priority_out_of_range -> R.string.dns_field_priority_out_of_range
    DnsFieldError.weight_required -> R.string.dns_field_weight_required
    DnsFieldError.weight_out_of_range -> R.string.dns_field_weight_out_of_range
    DnsFieldError.port_required -> R.string.dns_field_port_required
    DnsFieldError.port_out_of_range -> R.string.dns_field_port_out_of_range
}

enum class DnsProtectedReason { mail_routing, sender_auth, verification, unsupported_type, other }

fun dns_protected_reason(raw: String?): DnsProtectedReason = when (raw?.trim()?.lowercase()) {
    "mail_routing" -> DnsProtectedReason.mail_routing
    "sender_auth" -> DnsProtectedReason.sender_auth
    "verification" -> DnsProtectedReason.verification
    "unsupported_type" -> DnsProtectedReason.unsupported_type
    else -> DnsProtectedReason.other
}

@StringRes
fun dns_protected_reason_res(reason: DnsProtectedReason): Int = when (reason) {
    DnsProtectedReason.mail_routing -> R.string.dns_zone_protected_mail_routing
    DnsProtectedReason.sender_auth -> R.string.dns_zone_protected_sender_auth
    DnsProtectedReason.verification -> R.string.dns_zone_protected_verification
    DnsProtectedReason.unsupported_type -> R.string.dns_zone_protected_unsupported_type
    DnsProtectedReason.other -> R.string.dns_zone_protected_other
}
