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
    fun save(record: DeviceSnapshotRecord): Boolean
    fun delete(user_id: String, snapshot_ids: List<String>)
}

class LoadedDeviceRecoveryKey(val key: DeviceRecoveryKey, val created: Boolean)

interface DeviceRecoveryKeyProvider {
    fun load(create: Boolean): LoadedDeviceRecoveryKey?
    fun delete()
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
    override fun load(create: Boolean): LoadedDeviceRecoveryKey? {
        val usable = usable_key()
        if (usable != null) return LoadedDeviceRecoveryKey(KeystoreDeviceRecoveryKey(usable), false)
        if (!create) return null
        delete()
        val generated = runCatching { generate_key() }.getOrNull() ?: return null
        return LoadedDeviceRecoveryKey(KeystoreDeviceRecoveryKey(generated), true)
    }

    override fun delete() {
        runCatching { keystore().deleteEntry(DEVICE_RECOVERY_KEY_ALIAS) }
    }

    private fun keystore(): KeyStore = KeyStore.getInstance(ANDROID_KEY_STORE).apply { load(null) }

    private fun usable_key(): SecretKey? {
        val key = runCatching {
            val store = keystore()
            if (!store.containsAlias(DEVICE_RECOVERY_KEY_ALIAS)) null
            else store.getKey(DEVICE_RECOVERY_KEY_ALIAS, null) as? SecretKey
        }.getOrNull() ?: return null
        val works = runCatching {
            Cipher.getInstance(KEY_TRANSFORMATION).init(Cipher.ENCRYPT_MODE, key)
            true
        }.getOrDefault(false)
        return if (works) key else null
    }

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
