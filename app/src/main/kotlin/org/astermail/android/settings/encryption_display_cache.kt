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

import org.astermail.android.api.encryption.EncryptionSettings
import org.astermail.android.api.encryption.KeyserverStatusResponse
import org.astermail.android.api.encryption.PgpKeyInfo
import org.astermail.android.api.encryption.RecoveryCodesStatus
import org.astermail.android.api.encryption.WkdStatusResponse

data class encryption_identity_display(
    val fingerprint: String?,
    val is_pgp: Boolean,
    val available: Boolean,
)

data class encryption_key_display(
    val algorithm: String,
    val key_size: Int,
    val created_at: String,
)

data class encryption_recovery_display(
    val available: Int,
    val total: Int,
)

data class encryption_settings_display(
    val auto_discover_keys: Boolean,
    val encrypt_by_default: Boolean,
    val require_encryption: Boolean,
)

data class encryption_display_snapshot(
    val identity: encryption_identity_display? = null,
    val key: encryption_key_display? = null,
    val recovery: encryption_recovery_display? = null,
    val settings: encryption_settings_display? = null,
    val wkd_published: Boolean? = null,
    val keyserver_published: Boolean? = null,
)

internal class encryption_display_memo {
    private var key: String? = null
    private var value: encryption_display_snapshot? = null

    @Synchronized
    fun read(account_key: String?): encryption_display_snapshot? {
        if (account_key.isNullOrBlank()) return null
        return if (key == account_key) value else null
    }

    @Synchronized
    fun write(account_key: String?, snapshot: encryption_display_snapshot) {
        if (account_key.isNullOrBlank()) return
        if (key != account_key) {
            key = account_key
            value = snapshot
            return
        }
        value = (value ?: encryption_display_snapshot()).overlaid_with(snapshot)
    }

    @Synchronized
    fun clear() {
        key = null
        value = null
    }
}

object encryption_display_cache {
    internal val memo = encryption_display_memo()
}

fun encryption_display_snapshot.overlaid_with(newer: encryption_display_snapshot): encryption_display_snapshot =
    encryption_display_snapshot(
        identity = newer.identity ?: identity,
        key = newer.key ?: key,
        recovery = newer.recovery ?: recovery,
        settings = newer.settings ?: settings,
        wkd_published = newer.wkd_published ?: wkd_published,
        keyserver_published = newer.keyserver_published ?: keyserver_published,
    )

fun encryption_display_snapshot_of(
    identity: encryption_identity_display?,
    pgp_key_info: PgpKeyInfo?,
    recovery_codes_status: RecoveryCodesStatus?,
    encryption_settings: EncryptionSettings?,
    wkd_status: WkdStatusResponse?,
    keyserver_status: KeyserverStatusResponse?,
): encryption_display_snapshot =
    encryption_display_snapshot(
        identity = identity,
        key = pgp_key_info
            ?.takeIf { it.algorithm.isNotBlank() || it.created_at.isNotBlank() }
            ?.let { encryption_key_display(it.algorithm, it.key_size, it.created_at) },
        recovery = recovery_codes_status
            ?.takeIf { it.total_codes > 0 }
            ?.let { encryption_recovery_display(it.available_codes.coerceIn(0, it.total_codes), it.total_codes) },
        settings = encryption_settings?.let {
            encryption_settings_display(
                auto_discover_keys = it.auto_discover_keys,
                encrypt_by_default = it.encrypt_by_default,
                require_encryption = it.require_encryption,
            )
        },
        wkd_published = wkd_status?.published,
        keyserver_published = keyserver_status?.published,
    )
