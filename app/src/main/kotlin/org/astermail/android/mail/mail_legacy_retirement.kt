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

import org.json.JSONObject

const val MAIL_LEGACY_RETIRED_FIELD = "mail_legacy_retired"

private const val MAIL_LEGACY_RETIREMENT_VERSION = 1

fun mail_legacy_retired_version(marker: Any?): Int {
    if (marker !is JSONObject) return 0
    if (marker.opt("at") !is String) return 0
    val version = when (val raw = marker.opt("v")) {
        is Int -> raw.toLong()
        is Long -> raw
        is Number -> raw.toDouble().takeIf { it % 1.0 == 0.0 && it <= Int.MAX_VALUE }?.toLong()
        else -> null
    } ?: return 0
    return if (version >= MAIL_LEGACY_RETIREMENT_VERSION && version <= Int.MAX_VALUE) version.toInt() else 0
}

fun vault_marks_mail_legacy_retired(vault: JSONObject?): Boolean =
    mail_legacy_retired_version(vault?.opt(MAIL_LEGACY_RETIRED_FIELD)) > 0

fun merge_mail_legacy_retired_marker(vararg markers: Any?): JSONObject? {
    var best: JSONObject? = null
    var best_version = 0
    for (marker in markers) {
        val version = mail_legacy_retired_version(marker)
        if (version > best_version) {
            best_version = version
            best = JSONObject().put("v", version).put("at", (marker as JSONObject).getString("at"))
        }
    }
    return best
}

private val public_identity_placeholders = setOf("", "undefined", "null")

fun is_secret_identity_key(identity_key: String): Boolean =
    identity_key.trim().lowercase() !in public_identity_placeholders
