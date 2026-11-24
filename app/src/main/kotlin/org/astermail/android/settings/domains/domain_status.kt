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
import org.astermail.android.R
import org.astermail.android.api.settings.CustomDomain
import org.astermail.android.api.settings.DomainCheck
import org.astermail.android.api.settings.DomainHealth

enum class DomainTone { verified, pending, attention }

enum class DomainHealthLevel { unknown, critical, warning, ok }

const val DOMAIN_HEALTH_POLL_MS = 15_000L

fun is_domain_active(domain: CustomDomain): Boolean =
    domain.status.equals("active", ignoreCase = true) ||
        (domain.status.isBlank() && domain.txt_verified && domain.mx_verified && domain.spf_verified && domain.dkim_verified)

fun is_domain_blocked(domain: CustomDomain): Boolean =
    domain.status.equals("suspended", ignoreCase = true) || domain.status.equals("failed", ignoreCase = true)

fun domain_health_level(health: DomainHealth?): DomainHealthLevel {
    if (health == null) return DomainHealthLevel.unknown
    return when (health.severity.trim().lowercase()) {
        "critical", "error" -> DomainHealthLevel.critical
        "warning", "warn" -> DomainHealthLevel.warning
        "ok", "healthy", "none", "info" -> DomainHealthLevel.ok
        else -> when (health.health_status.trim().lowercase()) {
            "healthy", "ok" -> DomainHealthLevel.ok
            "degraded", "warning" -> DomainHealthLevel.warning
            "broken", "critical", "unhealthy" -> DomainHealthLevel.critical
            else -> DomainHealthLevel.unknown
        }
    }
}

fun domain_tone(domain: CustomDomain, health: DomainHealth?): DomainTone {
    if (is_domain_blocked(domain)) return DomainTone.attention
    if (!domain.downgrade_grace_expires_at.isNullOrBlank()) return DomainTone.attention
    if (!is_domain_active(domain)) return DomainTone.pending
    return when (domain_health_level(health)) {
        DomainHealthLevel.critical, DomainHealthLevel.warning -> DomainTone.attention
        else -> DomainTone.verified
    }
}

@StringRes
fun domain_tone_res(tone: DomainTone): Int = when (tone) {
    DomainTone.verified -> R.string.domain_mgmt_status_verified
    DomainTone.pending -> R.string.domain_mgmt_status_pending
    DomainTone.attention -> R.string.domain_mgmt_status_attention
}

private val check_order = listOf("mx", "spf", "dkim", "dmarc")

fun is_check_failing(check: DomainCheck): Boolean =
    check.outcome.equals("fail", ignoreCase = true) || check.outcome.equals("warn", ignoreCase = true)

fun ordered_domain_checks(checks: List<DomainCheck>): List<DomainCheck> {
    val rank = { c: DomainCheck ->
        val i = check_order.indexOf(c.key.lowercase())
        if (i < 0) check_order.size else i
    }
    return checks.sortedWith(compareBy<DomainCheck>({ if (is_check_failing(it)) 0 else 1 }, { rank(it) }))
}

@StringRes
fun domain_health_reason_res(reason: String?): Int? = when (reason?.lowercase()) {
    "mx_missing" -> R.string.domain_health_reason_mx_missing
    "mx_points_elsewhere" -> R.string.domain_health_reason_mx_points_elsewhere
    "spf_missing" -> R.string.domain_health_reason_spf_missing
    "spf_missing_include" -> R.string.domain_health_reason_spf_missing_include
    "spf_duplicate_records" -> R.string.domain_health_reason_spf_duplicate
    "dkim_missing_or_stale" -> R.string.domain_reason_dkim_missing_or_stale
    "dmarc_missing" -> R.string.domain_health_reason_dmarc_missing
    else -> null
}

@StringRes
fun domain_check_label_res(key: String): Int? = when (key.lowercase()) {
    "mx" -> R.string.domain_health_check_mx
    "spf" -> R.string.domain_health_check_spf
    "dkim" -> R.string.domain_health_check_dkim
    "dmarc" -> R.string.domain_health_check_dmarc
    else -> null
}

@StringRes
fun domain_health_title_res(level: DomainHealthLevel): Int = when (level) {
    DomainHealthLevel.unknown -> R.string.domain_health_title_unknown
    DomainHealthLevel.critical -> R.string.domain_health_title_critical
    DomainHealthLevel.warning -> R.string.domain_health_title_warning
    DomainHealthLevel.ok -> R.string.domain_health_title_ok
}

fun should_keep_polling_health(health: DomainHealth?): Boolean =
    domain_health_level(health) != DomainHealthLevel.ok

@StringRes
fun domain_record_caveat_res(caveat_key: String?): Int? = when (caveat_key) {
    "mx_replaces_existing" -> R.string.domain_mgmt_caveat_mx_replaces
    "spf_single_record_other_senders" -> R.string.domain_mgmt_caveat_spf_single
    "dmarc_add_after_spf_dkim" -> R.string.domain_mgmt_caveat_dmarc_after
    else -> org.astermail.android.settings.dns_caveat_res(caveat_key)
}
