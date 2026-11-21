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
package org.astermail.android.crypto.storage_rotation

import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import org.astermail.android.api.ApiError
import org.astermail.android.mail.ratchet.collect_vault_key_fingerprints
import org.astermail.android.mail.ratchet.max_retained_ratchet_key_sets
import org.json.JSONObject

const val BACKUP_RETRY_DELAY_MS = 15L * 60L * 1000L

private const val BACKUP_KEYS_SEPARATOR = "\n"

enum class BackupCoverage { CURRENT, STALE, WOULD_SHRINK }

fun backup_coverage(
    recorded: Set<String>?,
    vault_keys: Set<String>,
    vault_at_cap: Boolean = false,
): BackupCoverage {
    val missing = recorded != null && !vault_keys.containsAll(recorded)
    val gained = recorded == null || !recorded.containsAll(vault_keys)
    return when {
        vault_keys.isEmpty() -> if (missing) BackupCoverage.WOULD_SHRINK else BackupCoverage.CURRENT
        missing && !vault_at_cap -> BackupCoverage.WOULD_SHRINK
        gained -> BackupCoverage.STALE
        else -> BackupCoverage.CURRENT
    }
}

fun vault_at_key_cap(vault: JSONObject, cap: Int = max_retained_ratchet_key_sets): Boolean =
    (vault.optJSONArray("ratchet_previous_keys")?.length() ?: 0) >= cap

fun written_backup_keys(keys: Collection<String>?): Set<String> = keys.orEmpty().toSet()

fun drops_published_key(error: Throwable): Boolean =
    error is ApiError.Conflict && error.code == BACKUP_DROPS_PUBLISHED_KEY_CODE

suspend fun <T> write_with_local_keys(
    on_uncovered: () -> Throwable,
    write: suspend (Boolean) -> T,
): T =
    try {
        write(false)
    } catch (error: ApiError.Conflict) {
        if (!drops_published_key(error)) throw error
        try {
            write(true)
        } catch (again: ApiError.Conflict) {
            throw if (drops_published_key(again)) on_uncovered() else again
        }
    }

fun encode_backup_keys(keys: Set<String>): String = keys.sorted().joinToString(BACKUP_KEYS_SEPARATOR)

fun decode_backup_keys(value: String?): Set<String>? =
    value?.split(BACKUP_KEYS_SEPARATOR)?.filter { it.isNotBlank() }?.toSet()

fun backup_retry_allowed(retry_at: Long?, now: Long): Boolean = retry_at == null || now >= retry_at

fun backup_key_fingerprints(
    vault: JSONObject,
    collect: (JSONObject) -> List<String> = ::collect_vault_key_fingerprints,
): List<String>? = runCatching { collect(vault) }.getOrNull()?.takeIf { it.isNotEmpty() }

fun server_backup_coverage(reply: ApiReply): Boolean? {
    if (reply.error) return null
    val field = (reply.data as? JsonObject)?.get(BACKUP_COVERS_PUBLISHED_KEYS_FIELD) as? JsonPrimitive
    return field?.booleanOrNull
}

class CoverageBackupBuilder(
    private val inner: RecoveryBackupBuilder,
    private val vault_keys: (JSONObject) -> Set<String>,
    private val recorded: () -> Set<String>?,
    private val record: (Set<String>) -> Unit,
    private val storage_key_due: (JSONObject) -> Boolean = { false },
    private val server_stale: Boolean = false,
    private val vault_at_cap: (JSONObject) -> Boolean = { false },
) : RecoveryBackupBuilder {
    @Volatile private var pending: Set<String>? = null

    @Volatile var declined: Boolean = false
        private set

    override fun build(vault: JSONObject): JsonObject? {
        pending = null
        val keys = vault_keys(vault)
        val coverage = backup_coverage(recorded(), keys, vault_at_cap(vault))
        declined = when (coverage) {
            BackupCoverage.STALE -> false
            BackupCoverage.CURRENT -> !server_stale && !storage_key_due(vault)
            BackupCoverage.WOULD_SHRINK -> true
        }
        if (declined) return null
        val built = inner.build(vault) ?: return null
        pending = keys
        return built
    }

    override fun saved(built: JsonObject) {
        inner.saved(built)
        pending?.let(record)
        pending = null
    }
}
