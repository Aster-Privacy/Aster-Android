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
package org.astermail.android.settings

import androidx.annotation.StringRes
import org.astermail.android.R
import org.astermail.android.api.settings.DnsRecord
import org.astermail.android.api.settings.DomainCheck

const val DKIM_TXT_SEGMENT_LIMIT = 255
const val DKIM_LONG_VALUE_CAVEAT = "dkim_long_value"

fun is_dkim_record(record: DnsRecord): Boolean = record.purpose.equals("dkim", ignoreCase = true)

fun should_offer_short_dkim_key(
    record: DnsRecord,
    can_shorten: Boolean,
    can_manage: Boolean,
): Boolean {
    if (!can_manage) return false
    if (!is_dkim_record(record)) return false
    if (record.value.length <= DKIM_TXT_SEGMENT_LIMIT) return false
    val flagged_long = record.caveat_key == DKIM_LONG_VALUE_CAVEAT && !record.verified
    return can_shorten || flagged_long
}

@StringRes
fun dns_caveat_res(caveat_key: String?): Int? = when (caveat_key) {
    DKIM_LONG_VALUE_CAVEAT -> R.string.dns_caveat_dkim_long_value
    else -> null
}

@StringRes
fun domain_hint_res(hint: String?): Int? = when (hint) {
    "dkim_truncated" -> R.string.domain_hint_dkim_truncated
    "dkim_quoted" -> R.string.domain_hint_dkim_quoted
    "dkim_other_key" -> R.string.domain_hint_dkim_other_key
    else -> null
}

@StringRes
fun domain_reason_res(reason: String?): Int? = when (reason) {
    "dkim_missing_or_stale" -> R.string.domain_reason_dkim_missing_or_stale
    else -> null
}

@StringRes
fun domain_check_message_res(check: DomainCheck): Int? {
    if (check.outcome != "fail") return null
    return domain_hint_res(check.hint)
        ?: domain_reason_res(check.reason)
        ?: R.string.domain_check_generic_failure
}

fun find_dkim_check(checks: List<DomainCheck>): DomainCheck? =
    checks.firstOrNull { it.key.equals("dkim", ignoreCase = true) }

fun replace_dkim_record(records: List<DnsRecord>, replacement: DnsRecord): List<DnsRecord> {
    val index = records.indexOfFirst { is_dkim_record(it) }
    if (index < 0) return records + replacement
    return records.toMutableList().also { it[index] = replacement }
}
