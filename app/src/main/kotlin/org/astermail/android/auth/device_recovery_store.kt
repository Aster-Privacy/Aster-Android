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
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import java.io.File
import java.security.KeyStore
import java.security.MessageDigest
import java.util.Base64
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec
import org.astermail.android.crypto.AES_GCM_TAG_BITS
import org.json.JSONObject

private const val DEVICE_RECOVERY_DIR = "device_recovery"
private const val DEVICE_RECOVERY_KEY_ALIAS = "aster_device_recovery_v1"
private const val ANDROID_KEY_STORE = "AndroidKeyStore"
private const val KEY_TRANSFORMATION = "AES/GCM/NoPadding"
private const val DEVICE_RECOVERY_STATE_PREFS = "device_recovery_state"
private const val ATTEMPTS_KEY_PREFIX = "astermail_device_recovery_attempts_"
private const val REFRESH_NOT_BEFORE_KEY_PREFIX = "astermail_device_recovery_refresh_not_before_"

class DeviceSnapshotRecord(
    val snapshot_id: String,
    val user_id: String,
    val source_hash: String,
    val created_at: Long,
    val iv: ByteArray,
    val sealed: ByteArray,
)

interface DeviceSnapshotStore {
    fun list(user_id: String): List<DeviceSnapshotRecord>
    fun list_all(): List<DeviceSnapshotRecord>
    fun save(record: DeviceSnapshotRecord): Boolean
    fun delete(user_id: String, snapshot_ids: List<String>)
}

sealed class DeviceRecoveryKeyLoad {
    class Ready(val key: DeviceRecoveryKey, val created: Boolean) : DeviceRecoveryKeyLoad()
    object Missing : DeviceRecoveryKeyLoad()
    object Unavailable : DeviceRecoveryKeyLoad()
}

interface DeviceRecoveryKeyProvider {
    fun load(create: Boolean): DeviceRecoveryKeyLoad
}

class DeviceRecoveryAttempt(val count: Int, val last_at: Long)

interface DeviceRecoveryStateStore {
    fun attempts(user_id: String): Map<String, DeviceRecoveryAttempt>
    fun put_attempts(user_id: String, attempts: Map<String, DeviceRecoveryAttempt>)
    fun refresh_not_before(user_id: String): Long
    fun put_refresh_not_before(user_id: String, at_ms: Long)
    fun forget_user(user_id: String)
}

fun encode_device_recovery_attempts(attempts: Map<String, DeviceRecoveryAttempt>): String {
    val json = JSONObject()
    for ((snapshot_id, attempt) in attempts) {
        json.put(snapshot_id, JSONObject().put("count", attempt.count).put("last_at", attempt.last_at))
    }
    return json.toString()
}

fun decode_device_recovery_attempts(raw: String?): Map<String, DeviceRecoveryAttempt> {
    if (raw.isNullOrEmpty()) return emptyMap()
    val json = runCatching { JSONObject(raw) }.getOrNull() ?: return emptyMap()
    val attempts = mutableMapOf<String, DeviceRecoveryAttempt>()
    for (snapshot_id in json.keys()) {
        val entry = json.optJSONObject(snapshot_id) ?: continue
        val count = entry.optInt("count", 0)
        if (snapshot_id.isEmpty() || count <= 0) continue
        attempts[snapshot_id] = DeviceRecoveryAttempt(count, entry.optLong("last_at", 0L))
    }
    return attempts
}

class PrefsDeviceRecoveryStateStore(context: Context) : DeviceRecoveryStateStore {
    private val app_context = context.applicationContext

    private val prefs by lazy {
        app_context.getSharedPreferences(DEVICE_RECOVERY_STATE_PREFS, Context.MODE_PRIVATE)
    }

    override fun attempts(user_id: String): Map<String, DeviceRecoveryAttempt> =
        runCatching { decode_device_recovery_attempts(prefs.getString(ATTEMPTS_KEY_PREFIX + user_id, null)) }
            .getOrDefault(emptyMap())

    override fun put_attempts(user_id: String, attempts: Map<String, DeviceRecoveryAttempt>) {
        runCatching {
            val editor = prefs.edit()
            if (attempts.isEmpty()) {
                editor.remove(ATTEMPTS_KEY_PREFIX + user_id)
            } else {
                editor.putString(ATTEMPTS_KEY_PREFIX + user_id, encode_device_recovery_attempts(attempts))
            }
            editor.commit()
        }
    }

    override fun refresh_not_before(user_id: String): Long =
        runCatching { prefs.getLong(REFRESH_NOT_BEFORE_KEY_PREFIX + user_id, 0L) }.getOrDefault(0L)

    override fun put_refresh_not_before(user_id: String, at_ms: Long) {
        runCatching {
            val editor = prefs.edit()
            if (at_ms <= 0L) {
                editor.remove(REFRESH_NOT_BEFORE_KEY_PREFIX + user_id)
            } else {
                editor.putLong(REFRESH_NOT_BEFORE_KEY_PREFIX + user_id, at_ms)
            }
            editor.apply()
        }
    }

    override fun forget_user(user_id: String) {
        runCatching {
            prefs.edit()
                .remove(ATTEMPTS_KEY_PREFIX + user_id)
                .remove(REFRESH_NOT_BEFORE_KEY_PREFIX + user_id)
                .commit()
        }
    }
}

fun forget_deleted_account_device_recovery(context: Context, user_id: String) {
    if (user_id.isEmpty()) return
    FileDeviceSnapshotStore(context).delete_user(user_id)
    PrefsDeviceRecoveryStateStore(context).forget_user(user_id)
}

class FileDeviceSnapshotStore(context: Context) : DeviceSnapshotStore {
    private val root = File(context.applicationContext.noBackupFilesDir, DEVICE_RECOVERY_DIR)

    private fun account_dir(user_id: String): File {
        val digest = MessageDigest.getInstance("SHA-256").digest(user_id.toByteArray(Charsets.UTF_8))
        return File(root, digest.joinToString("") { "%02x".format(it) })
    }

    override fun list(user_id: String): List<DeviceSnapshotRecord> {
        val files = runCatching { account_dir(user_id).listFiles() }.getOrNull() ?: return emptyList()
        return files
            .mapNotNull { file -> runCatching { read_record(file) }.getOrNull() }
            .filter { it.user_id == user_id }
            .sortedByDescending { it.created_at }
    }

    override fun list_all(): List<DeviceSnapshotRecord> {
        val dirs = runCatching { root.listFiles() }.getOrNull() ?: return emptyList()
        return dirs
            .filter { it.isDirectory }
            .flatMap { dir -> runCatching { dir.listFiles()?.toList() }.getOrNull().orEmpty() }
            .mapNotNull { file -> runCatching { read_record(file) }.getOrNull() }
            .sortedByDescending { it.created_at }
    }

    fun delete_user(user_id: String) {
        runCatching { account_dir(user_id).deleteRecursively() }
    }

    override fun save(record: DeviceSnapshotRecord): Boolean = runCatching {
        val dir = account_dir(record.user_id)
        dir.mkdirs()
        val encoder = Base64.getEncoder()
        val json = JSONObject()
            .put("snapshot_id", record.snapshot_id)
            .put("user_id", record.user_id)
            .put("source_hash", record.source_hash)
            .put("created_at", record.created_at)
            .put("iv", encoder.encodeToString(record.iv))
            .put("sealed", encoder.encodeToString(record.sealed))
        val target = File(dir, "${record.snapshot_id}.json")
        val staging = File(dir, "${record.snapshot_id}.json.tmp")
        staging.writeText(json.toString(), Charsets.UTF_8)
        if (!staging.renameTo(target)) {
            staging.delete()
            return false
        }
        true
    }.getOrDefault(false)

    override fun delete(user_id: String, snapshot_ids: List<String>) {
        if (snapshot_ids.isEmpty()) return
        val dir = account_dir(user_id)
        for (id in snapshot_ids) runCatching { File(dir, "$id.json").delete() }
    }

    private fun read_record(file: File): DeviceSnapshotRecord? {
        if (!file.name.endsWith(".json")) return null
        val json = JSONObject(file.readText(Charsets.UTF_8))
        val snapshot_id = json.optString("snapshot_id", "")
        val user_id = json.optString("user_id", "")
        val source_hash = json.optString("source_hash", "")
        if (snapshot_id.isEmpty() || user_id.isEmpty() || source_hash.isEmpty()) return null
        val decoder = Base64.getDecoder()
        return DeviceSnapshotRecord(
            snapshot_id = snapshot_id,
            user_id = user_id,
            source_hash = source_hash,
            created_at = json.optLong("created_at", 0L),
            iv = decoder.decode(json.optString("iv", "")),
            sealed = decoder.decode(json.optString("sealed", "")),
        )
    }
}

class KeystoreDeviceRecoveryKey(private val key: SecretKey) : DeviceRecoveryKey {
    override fun seal(plaintext: ByteArray, aad: ByteArray): DeviceSealedBytes {
        val cipher = Cipher.getInstance(KEY_TRANSFORMATION)
        cipher.init(Cipher.ENCRYPT_MODE, key)
        cipher.updateAAD(aad)
        val ciphertext = cipher.doFinal(plaintext)
        return DeviceSealedBytes(cipher.iv.copyOf(), ciphertext)
    }

    override fun open(iv: ByteArray, ciphertext: ByteArray, aad: ByteArray): ByteArray {
        val cipher = Cipher.getInstance(KEY_TRANSFORMATION)
        cipher.init(Cipher.DECRYPT_MODE, key, GCMParameterSpec(AES_GCM_TAG_BITS, iv))
        cipher.updateAAD(aad)
        return cipher.doFinal(ciphertext)
    }
}

class KeystoreDeviceRecoveryKeyProvider : DeviceRecoveryKeyProvider {
    @Synchronized
    override fun load(create: Boolean): DeviceRecoveryKeyLoad {
        val store = runCatching { keystore() }.getOrNull() ?: return DeviceRecoveryKeyLoad.Unavailable
        val present = runCatching { store.containsAlias(DEVICE_RECOVERY_KEY_ALIAS) }.getOrNull()
            ?: return DeviceRecoveryKeyLoad.Unavailable

        if (present) {
            val key = runCatching { store.getKey(DEVICE_RECOVERY_KEY_ALIAS, null) as? SecretKey }.getOrNull()
                ?: return DeviceRecoveryKeyLoad.Unavailable
            val works = runCatching {
                Cipher.getInstance(KEY_TRANSFORMATION).init(Cipher.ENCRYPT_MODE, key)
                true
            }.getOrDefault(false)
            if (!works) return DeviceRecoveryKeyLoad.Unavailable
            return DeviceRecoveryKeyLoad.Ready(KeystoreDeviceRecoveryKey(key), created = false)
        }

        if (!create) return DeviceRecoveryKeyLoad.Missing
        val generated = runCatching { generate_key() }.getOrNull() ?: return DeviceRecoveryKeyLoad.Unavailable
        return DeviceRecoveryKeyLoad.Ready(KeystoreDeviceRecoveryKey(generated), created = true)
    }

    private fun keystore(): KeyStore = KeyStore.getInstance(ANDROID_KEY_STORE).apply { load(null) }

    private fun generate_key(): SecretKey {
        val generator = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, ANDROID_KEY_STORE)
        val spec = KeyGenParameterSpec.Builder(
            DEVICE_RECOVERY_KEY_ALIAS,
            KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT,
        )
            .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
            .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
            .setKeySize(256)
            .setRandomizedEncryptionRequired(true)
            .build()
        generator.init(spec)
        return generator.generateKey()
    }
}
