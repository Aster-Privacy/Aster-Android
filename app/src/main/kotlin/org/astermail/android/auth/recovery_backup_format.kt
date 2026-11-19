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

package org.astermail.android.auth

import android.content.Context
import android.content.SharedPreferences
import java.nio.ByteBuffer
import java.security.MessageDigest
import java.security.SecureRandom
import java.util.Locale
import org.astermail.android.crypto.AesGcm
import org.astermail.android.crypto.hkdf_sha256
import org.json.JSONObject

private const val BACKUP_V1_INFO = "Aster Mail_Recovery_Vault_v1"
private const val BACKUP_V2_INFO = "Aster Mail_Recovery_Vault_v2"
private const val BACKUP_V2_AAD_LABEL = "aster-recovery-backup-v2"
private val BACKUP_V2_MAGIC = byteArrayOf(0x41, 0x52, 0x42, 0x02)
private const val BACKUP_V2_FIXED_HEADER = 4 + 8 + 2
private const val MAX_BACKUP_USER_ID_BYTES = 0xffff
private const val GENERATION_FIELD = "backup_generation"
private const val USER_ID_FIELD = "backup_user_id"

data class RecoveryBackupBinding(val user_id: String, val generation: Long)

class SealedVaultBackup(val encrypted_data: ByteArray, val nonce: ByteArray, val salt: ByteArray)

class OpenedVaultBackup(val plain: ByteArray, val generation: Long, val user_id: String, val format_v2: Boolean = false)

object RecoveryBackupVaultFields {
    const val GENERATION = "recovery_backup_generation"
    const val FORMAT_V2 = "recovery_backup_v2"

    fun generation(vault: JSONObject?): Long = vault?.optLong(GENERATION, 0L)?.coerceAtLeast(0L) ?: 0L

    fun format_v2(vault: JSONObject?): Boolean = vault?.optBoolean(FORMAT_V2, false) ?: false

    fun stamp(vault: JSONObject, binding: RecoveryBackupBinding, format_v2: Boolean) {
        if (binding.generation > generation(vault)) vault.put(GENERATION, binding.generation)
        if (format_v2) vault.put(FORMAT_V2, true)
    }
}

class RecoveryBackupStaleException : IllegalStateException("recovery backup is older than one this device has seen")

private class BackupHeader(val bytes: ByteArray, val generation: Long, val user_id: String)

private fun encode_header(binding: RecoveryBackupBinding): ByteArray {
    val user_id = binding.user_id.toByteArray(Charsets.UTF_8)
    require(user_id.size <= MAX_BACKUP_USER_ID_BYTES) { "recovery backup user id is too long" }
    require(binding.generation >= 0) { "recovery backup generation is invalid" }
    return ByteBuffer.allocate(BACKUP_V2_FIXED_HEADER + user_id.size)
        .put(BACKUP_V2_MAGIC)
        .putLong(binding.generation)
        .putShort(user_id.size.toShort())
        .put(user_id)
        .array()
}

private fun parse_header(data: ByteArray): BackupHeader? {
    if (data.size < BACKUP_V2_FIXED_HEADER) return null
    if (!BACKUP_V2_MAGIC.indices.all { data[it] == BACKUP_V2_MAGIC[it] }) return null
    val buffer = ByteBuffer.wrap(data, BACKUP_V2_MAGIC.size, 10)
    val generation = buffer.long
    val user_id_length = buffer.short.toInt() and 0xffff
    val header_length = BACKUP_V2_FIXED_HEADER + user_id_length
    if (generation < 0 || data.size <= header_length) return null
    val user_id = runCatching {
        Charsets.UTF_8.newDecoder()
            .decode(ByteBuffer.wrap(data, BACKUP_V2_FIXED_HEADER, user_id_length))
            .toString()
    }.getOrNull() ?: return null
    return BackupHeader(data.copyOfRange(0, header_length), generation, user_id)
}

fun recovery_backup_binding_of(encrypted: ByteArray): RecoveryBackupBinding? =
    parse_header(encrypted)?.let { RecoveryBackupBinding(it.user_id, it.generation) }

private fun backup_aad(header: ByteArray): ByteArray =
    BACKUP_V2_AAD_LABEL.toByteArray(Charsets.UTF_8) + header

private fun backup_key(recovery_key: ByteArray, salt: ByteArray, info: String): ByteArray =
    hkdf_sha256(recovery_key, salt, info.toByteArray(Charsets.UTF_8), 32)

fun seal_vault_backup(
    vault: ByteArray,
    recovery_key: ByteArray,
    binding: RecoveryBackupBinding,
    format_v2: Boolean = true,
): SealedVaultBackup {
    if (!format_v2) return seal_v1(vault, recovery_key)
    require(binding.user_id.isNotBlank()) { "recovery backup user id is missing" }
    val header = encode_header(binding)
    val bound = JSONObject(String(vault, Charsets.UTF_8))
        .put(GENERATION_FIELD, binding.generation)
        .put(USER_ID_FIELD, binding.user_id)
        .toString()
        .toByteArray(Charsets.UTF_8)
    val salt = ByteArray(16).also { SecureRandom().nextBytes(it) }
    val nonce = ByteArray(12).also { SecureRandom().nextBytes(it) }
    val key = backup_key(recovery_key, salt, BACKUP_V2_INFO)
    try {
        val encrypted = AesGcm.encrypt(key, nonce, bound, backup_aad(header))
        return SealedVaultBackup(header + encrypted, nonce, salt)
    } finally {
        key.fill(0)
        bound.fill(0)
    }
}

private fun seal_v1(vault: ByteArray, recovery_key: ByteArray): SealedVaultBackup {
    val salt = ByteArray(16).also { SecureRandom().nextBytes(it) }
    val nonce = ByteArray(12).also { SecureRandom().nextBytes(it) }
    val key = backup_key(recovery_key, salt, BACKUP_V1_INFO)
    try {
        return SealedVaultBackup(AesGcm.encrypt(key, nonce, vault), nonce, salt)
    } finally {
        key.fill(0)
    }
}

private fun open_v2(
    header: BackupHeader,
    encrypted: ByteArray,
    nonce: ByteArray,
    salt: ByteArray,
    recovery_key: ByteArray,
): OpenedVaultBackup? {
    val key = backup_key(recovery_key, salt, BACKUP_V2_INFO)
    val plain = try {
        AesGcm.decrypt(
            key,
            nonce,
            encrypted.copyOfRange(header.bytes.size, encrypted.size),
            backup_aad(header.bytes),
        )
    } catch (_: javax.crypto.AEADBadTagException) {
        return null
    } finally {
        key.fill(0)
    }
    try {
        val vault = JSONObject(String(plain, Charsets.UTF_8))
        val generation = vault.opt(GENERATION_FIELD)
        val user_id = vault.opt(USER_ID_FIELD)
        if ((generation as? Number)?.toLong() != header.generation || user_id != header.user_id) {
            throw IllegalStateException("recovery backup binding does not match its header")
        }
        vault.remove(GENERATION_FIELD)
        vault.remove(USER_ID_FIELD)
        return OpenedVaultBackup(vault.toString().toByteArray(Charsets.UTF_8), header.generation, header.user_id, true)
    } finally {
        plain.fill(0)
    }
}

fun read_vault_backup(
    encrypted: ByteArray,
    nonce: ByteArray,
    salt: ByteArray,
    recovery_key: ByteArray,
): OpenedVaultBackup {
    parse_header(encrypted)?.let { header ->
        open_v2(header, encrypted, nonce, salt, recovery_key)?.let { return it }
    }
    val key = backup_key(recovery_key, salt, BACKUP_V1_INFO)
    try {
        return OpenedVaultBackup(AesGcm.decrypt(key, nonce, encrypted), 0L, "")
    } finally {
        key.fill(0)
    }
}

class RecoveryBackupFloors(private val prefs: SharedPreferences?) {

    fun floor(user_id: String?, email: String? = null): Long {
        val by_user = user_id?.takeIf { it.isNotBlank() }?.let { read_long(USER_FLOOR_PREFIX + it) } ?: 0L
        val by_email = email_digest(email)?.let { read_long(EMAIL_FLOOR_PREFIX + it) } ?: 0L
        return maxOf(by_user, by_email)
    }

    fun raise(user_id: String?, generation: Long, email: String? = null) {
        if (generation <= 0) return
        val id = user_id?.takeIf { it.isNotBlank() }
        val digest = email_digest(email)
        if (id == null && digest == null) return
        runCatching {
            val editor = prefs?.edit() ?: return
            if (id != null && generation > read_long(USER_FLOOR_PREFIX + id)) {
                editor.putLong(USER_FLOOR_PREFIX + id, generation)
            }
            if (digest != null) {
                if (generation > read_long(EMAIL_FLOOR_PREFIX + digest)) {
                    editor.putLong(EMAIL_FLOOR_PREFIX + digest, generation)
                }
                if (id != null) editor.putString(EMAIL_USER_PREFIX + digest, id)
            }
            editor.apply()
        }
    }

    fun user_for_email(email: String?): String? {
        val digest = email_digest(email) ?: return null
        return runCatching { prefs?.getString(EMAIL_USER_PREFIX + digest, null) }.getOrNull()?.takeIf { it.isNotBlank() }
    }

    fun next_binding(user_id: String?, email: String? = null, vault_generation: Long = 0L): RecoveryBackupBinding {
        val id = user_id.orEmpty()
        return RecoveryBackupBinding(id, maxOf(vault_generation, floor(id, email)) + 1)
    }

    fun mark_v2(user_id: String?, email: String? = null) {
        val id = user_id?.takeIf { it.isNotBlank() }
        val digest = email_digest(email)
        if (id == null && digest == null) return
        runCatching {
            val editor = prefs?.edit() ?: return
            if (id != null) editor.putBoolean(USER_V2_PREFIX + id, true)
            if (digest != null) editor.putBoolean(EMAIL_V2_PREFIX + digest, true)
            editor.apply()
        }
    }

    fun v2_marked(user_id: String?, email: String? = null): Boolean {
        val by_user = user_id?.takeIf { it.isNotBlank() }?.let { read_flag(USER_V2_PREFIX + it) } ?: false
        val by_email = email_digest(email)?.let { read_flag(EMAIL_V2_PREFIX + it) } ?: false
        return by_user || by_email
    }

    fun open_for_recovery(
        encrypted: ByteArray,
        nonce: ByteArray,
        salt: ByteArray,
        recovery_key: ByteArray,
        email: String?,
    ): OpenedVaultBackup {
        val opened = read_vault_backup(encrypted, nonce, salt, recovery_key)
        val expected = user_for_email(email)
        if (expected != null && opened.user_id.isNotEmpty() && opened.user_id != expected) {
            opened.plain.fill(0)
            throw RecoveryBackupStaleException()
        }
        val user_id = opened.user_id.ifEmpty { expected.orEmpty() }
        if (!opened.format_v2 && v2_marked(user_id, email)) {
            opened.plain.fill(0)
            throw RecoveryBackupStaleException()
        }
        if (opened.generation < floor(user_id, email)) {
            opened.plain.fill(0)
            throw RecoveryBackupStaleException()
        }
        return OpenedVaultBackup(opened.plain, opened.generation, user_id, opened.format_v2)
    }

    private fun read_flag(key: String): Boolean =
        runCatching { prefs?.getBoolean(key, false) ?: false }.getOrDefault(false)

    private fun read_long(key: String): Long =
        runCatching { prefs?.getLong(key, 0L) ?: 0L }.getOrDefault(0L)

    companion object {
        const val PREFS_NAME = "aster_recovery_backup_floor"
        private const val USER_FLOOR_PREFIX = "user:"
        private const val EMAIL_FLOOR_PREFIX = "email:"
        private const val EMAIL_USER_PREFIX = "email_user:"
        private const val USER_V2_PREFIX = "user_v2:"
        private const val EMAIL_V2_PREFIX = "email_v2:"

        fun email_digest(email: String?): String? {
            val normalized = email?.trim()?.lowercase(Locale.ROOT)?.takeIf { it.isNotEmpty() } ?: return null
            val digest = MessageDigest.getInstance("SHA-256").digest(normalized.toByteArray(Charsets.UTF_8))
            return digest.joinToString("") { "%02x".format(it) }
        }

        fun of(context: Context): RecoveryBackupFloors =
            RecoveryBackupFloors(runCatching { context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE) }.getOrNull())
    }
}

class RecoveryBackupPlan(val user_id: String, val format_v2: Boolean)

fun plan_recovery_backup(
    opened: OpenedVaultBackup,
    vault: JSONObject?,
    floors: RecoveryBackupFloors,
    email: String?,
): RecoveryBackupPlan? {
    val user_id = opened.user_id.trim()
    val format_v2 = opened.format_v2 ||
        RecoveryBackupVaultFields.format_v2(vault) ||
        floors.v2_marked(user_id, email)
    if (format_v2 && user_id.isEmpty()) return null
    return RecoveryBackupPlan(user_id, format_v2)
}

class RotatedBackupPlan(val binding: RecoveryBackupBinding, val format_v2: Boolean)

fun apply_rotated_codes(
    vault: JSONObject,
    codes: List<String>,
    floors: RecoveryBackupFloors,
    user_id: String?,
    email: String?,
    ready_v2: Boolean,
): RotatedBackupPlan {
    val codes_array = org.json.JSONArray()
    codes.forEach { codes_array.put(it) }
    vault.put("recovery_codes", codes_array)
    val format_v2 = ready_v2 || RecoveryBackupVaultFields.format_v2(vault)
    val binding = floors.next_binding(user_id, email, RecoveryBackupVaultFields.generation(vault))
    RecoveryBackupVaultFields.stamp(vault, binding, format_v2)
    return RotatedBackupPlan(binding, format_v2)
}
