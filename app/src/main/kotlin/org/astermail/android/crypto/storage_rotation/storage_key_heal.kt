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

import java.time.Instant
import kotlin.coroutines.cancellation.CancellationException
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import org.astermail.android.auth.RecoveryBackupVaultFields
import org.astermail.android.auth.recovery_backup_binding_of
import org.json.JSONObject

const val RECOVERY_BACKUP_PATH = "/core/v1/recovery/backup"
const val RECOVERY_CODES_CHANGED_CODE = "RECOVERY_CODES_CHANGED"
const val BACKUP_DROPS_PUBLISHED_KEY_CODE = "backup_drops_published_key"
const val RECOVERY_CODES_STATUS_PATH = "/core/v1/recovery/codes/status"
const val BACKUP_COVERS_PUBLISHED_KEYS_FIELD = "backup_covers_published_keys"
private const val UPGRADE_REQUIRED_CODE = "CLIENT_UPGRADE_REQUIRED"
private val REFRESH_FORBIDDEN_FIELDS = setOf(
    "encrypted_vault",
    "vault_nonce",
    "step_up_token",
    "password_hash",
    "totp_code",
    "refresh_only",
    "expected_vault_sha256",
)

sealed class StorageKeyHeal {
    class Healed(val vault: JSONObject) : StorageKeyHeal()
    object Current : StorageKeyHeal()
    class Skipped(val reason: String) : StorageKeyHeal()
}

suspend fun heal_storage_key_from_side_copy(
    api: ResealApi,
    updater: VaultUpdater,
    crypto: StorageKeyWrapCrypto,
    lock_held: Boolean = false,
    now: () -> String = { Instant.now().toString() },
): StorageKeyHeal {
    val wraps = runCatching { list_storage_key_wraps(api) }.getOrNull()
        ?: return StorageKeyHeal.Skipped("wraps_unavailable")
    if (wraps.isEmpty()) return StorageKeyHeal.Current
    val edit: suspend (VaultDraft) -> VaultEdit = { draft ->
        val restored = open_side_copy(wraps, draft.vault, crypto)
        if (restored == null) {
            VaultEdit.ABORT
        } else {
            try {
                if (apply_side_copy_to_vault(draft.vault, restored, now())) VaultEdit.CHANGED else VaultEdit.ABORT
            } finally {
                restored.fill(0)
            }
        }
    }
    val outcome = runCatching {
        if (lock_held) updater.update_locked(true, edit) else updater.update(true, edit)
    }.getOrElse { return StorageKeyHeal.Skipped("heal_failed") }
    return when (outcome) {
        is VaultUpdateOutcome.Written -> StorageKeyHeal.Healed(outcome.vault)
        is VaultUpdateOutcome.Unchanged, VaultUpdateOutcome.Aborted -> StorageKeyHeal.Current
        is VaultUpdateOutcome.Failed -> StorageKeyHeal.Skipped(outcome.reason)
    }
}

enum class BackupRefresh {
    REFRESHED,
    VAULT_NOT_RECORDED,
    NO_CODES,
    CODES_CHANGED,
    UPGRADE_REQUIRED,
    DROPS_PUBLISHED_KEY,
    FAILED,
}

fun interface RecoveryBackupBuilder {
    fun build(vault: JSONObject): JsonObject?

    fun saved(built: JsonObject) = Unit
}

fun vault_recovery_codes(vault: JSONObject): List<String> {
    val array = vault.optJSONArray("recovery_codes") ?: return emptyList()
    return (0 until array.length()).mapNotNull { i -> array.optString(i, "").takeIf { it.isNotBlank() } }
}

fun refresh_backup_body(built: JsonObject, expected_vault_sha256: String): JsonObject =
    JsonObject(
        built.filterKeys { it !in REFRESH_FORBIDDEN_FIELDS } +
            mapOf(
                "refresh_only" to JsonPrimitive(true),
                "expected_vault_sha256" to JsonPrimitive(expected_vault_sha256),
            ),
    )

fun stamp_refreshed_backup(vault: JSONObject, built: JsonObject): VaultEdit {
    val encoded = (built["encrypted_vault_backup"] as? JsonPrimitive)?.content ?: return VaultEdit.UNCHANGED
    val bytes = runCatching { java.util.Base64.getMimeDecoder().decode(encoded) }.getOrNull() ?: return VaultEdit.UNCHANGED
    val binding = recovery_backup_binding_of(bytes) ?: return VaultEdit.UNCHANGED
    val generation = RecoveryBackupVaultFields.generation(vault)
    val format_v2 = RecoveryBackupVaultFields.format_v2(vault)
    if (binding.generation <= generation && format_v2) return VaultEdit.UNCHANGED
    RecoveryBackupVaultFields.stamp(vault, binding, format_v2 = true)
    return VaultEdit.CHANGED
}

private inline fun <T> attempt(block: () -> T): T? =
    try {
        block()
    } catch (cancelled: CancellationException) {
        throw cancelled
    } catch (_: Exception) {
        null
    }

suspend fun refresh_recovery_backup(
    api: ResealApi,
    load: suspend () -> VaultDraft?,
    update: suspend (suspend (VaultDraft) -> VaultEdit) -> Boolean = { true },
    builder: RecoveryBackupBuilder,
): BackupRefresh {
    repeat(MAX_VAULT_CONFLICT_RETRIES + 1) {
        val draft = attempt { load() } ?: return BackupRefresh.FAILED
        if (vault_recovery_codes(draft.vault).isEmpty()) return BackupRefresh.NO_CODES
        val built = attempt { builder.build(draft.vault) } ?: return BackupRefresh.FAILED
        val reply = api.post(RECOVERY_BACKUP_PATH, refresh_backup_body(built, draft.snapshot.vault_sha256))
        if (!reply.error) {
            attempt { builder.saved(built) }
            val recorded = attempt { update { latest -> stamp_refreshed_backup(latest.vault, built) } } ?: false
            return if (recorded) BackupRefresh.REFRESHED else BackupRefresh.VAULT_NOT_RECORDED
        }
        when {
            reply.code == RECOVERY_CODES_CHANGED_CODE -> return BackupRefresh.CODES_CHANGED
            reply.status == 426 || reply.code == UPGRADE_REQUIRED_CODE -> return BackupRefresh.UPGRADE_REQUIRED
            reply.code == BACKUP_DROPS_PUBLISHED_KEY_CODE -> return BackupRefresh.DROPS_PUBLISHED_KEY
            reply.code == VAULT_CHANGED_CODE -> Unit
            reply.status == 409 && reply.code == null -> Unit
            else -> return BackupRefresh.FAILED
        }
    }
    return BackupRefresh.FAILED
}
