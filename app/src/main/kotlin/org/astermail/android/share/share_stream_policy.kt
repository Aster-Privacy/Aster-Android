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

package org.astermail.android.share

import java.util.Locale

enum class ShareCallerAccess { GRANTED, DENIED, UNKNOWN }

data class ShareCallerContext(
    val own_package: String,
    val has_read_grant: Boolean,
    val caller_is_system: Boolean,
    val app_reads_shared_storage: Boolean,
)

private const val MEDIA_AUTHORITY = "media"

private val guarded_authorities = setOf(
    "com.android.contacts",
    "contacts",
    "call_log",
    "call_log_shadow",
    "sms",
    "mms",
    "mms-sms",
    "telephony",
    "icc",
    "settings",
    "user_dictionary",
    "com.android.calendar",
    "com.android.voicemail",
    "com.android.blockednumber",
    "com.android.social",
    "downloads",
)

private val guarded_authority_prefixes = listOf(
    "com.android.providers.",
    "com.android.externalstorage.",
    "com.android.contacts.",
    "com.google.android.providers.",
)

fun normalize_share_authority(authority: String?): String =
    authority.orEmpty().substringAfterLast('@').trim().lowercase(Locale.ROOT)

fun is_own_share_authority(authority: String?, own_package: String): Boolean {
    val normalized = normalize_share_authority(authority)
    val own = own_package.lowercase(Locale.ROOT)
    return own.isNotEmpty() && (normalized == own || normalized.startsWith("$own."))
}

fun is_guarded_share_authority(authority: String?, app_reads_shared_storage: Boolean): Boolean {
    val normalized = normalize_share_authority(authority)
    if (normalized == MEDIA_AUTHORITY) return app_reads_shared_storage
    return normalized in guarded_authorities || guarded_authority_prefixes.any { normalized.startsWith(it) }
}

fun is_share_stream_allowed(
    scheme: String?,
    authority: String?,
    caller: ShareCallerContext,
    caller_access: ShareCallerAccess,
): Boolean {
    if (!scheme.equals("content", ignoreCase = true)) return false
    if (normalize_share_authority(authority).isEmpty()) return false
    if (is_own_share_authority(authority, caller.own_package)) return false
    if (!is_guarded_share_authority(authority, caller.app_reads_shared_storage)) return true
    if (!caller.has_read_grant) return false
    return when (caller_access) {
        ShareCallerAccess.GRANTED -> true
        ShareCallerAccess.DENIED -> false
        ShareCallerAccess.UNKNOWN -> caller.caller_is_system
    }
}

fun share_referrer_package(referrer_scheme: String?, referrer_host: String?): String? =
    referrer_host?.takeIf { referrer_scheme.equals("android-app", ignoreCase = true) && it.isNotBlank() }
