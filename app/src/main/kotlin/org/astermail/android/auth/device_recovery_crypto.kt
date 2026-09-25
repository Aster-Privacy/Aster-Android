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

import java.security.MessageDigest
import java.security.SecureRandom
import java.util.Base64
import org.astermail.android.crypto.AES_GCM_NONCE_BYTES
import org.astermail.android.crypto.AesGcm
import org.astermail.android.crypto.hkdf_sha256
import org.json.JSONArray
import org.json.JSONObject

const val DEVICE_RECOVERY_INFO = "aster-device-recovery-v1"
const val DEVICE_RECOVERY_SECRET_BYTES = 32
const val DEVICE_RECOVERY_SNAPSHOT_VERSION = 1
const val MAX_DEVICE_SECRETS_PER_REQUEST = 16

class DeviceSnapshotMeta(
    val user_id: String,
    val snapshot_id: String,
    val source_hash: String,
)

class DeviceSnapshotPayload(
    val identity_key: String,
    val previous_keys: List<String>,
    val legacy_identity_keys: List<String>,
    val unlocked_keys: List<Pair<String, String>>,
    val storage_keys: List<String>,
    val ratchet_keys: List<JSONObject>,
)

class DeviceSealedBytes(val iv: ByteArray, val ciphertext: ByteArray)

interface DeviceRecoveryKey {
    fun seal(plaintext: ByteArray, aad: ByteArray): DeviceSealedBytes
    fun open(iv: ByteArray, ciphertext: ByteArray, aad: ByteArray): ByteArray
}

fun device_snapshot_aad(user_id: String, snapshot_id: String, source_hash: String): ByteArray =
    "$DEVICE_RECOVERY_INFO|$user_id|$snapshot_id|$source_hash".toByteArray(Charsets.UTF_8)

fun vault_ciphertext_hash(encrypted_vault: String): String {
    val bytes = Base64.getDecoder().decode(encrypted_vault)
    val digest = MessageDigest.getInstance("SHA-256").digest(bytes)
    return digest.joinToString("") { "%02x".format(it) }
}

fun random_device_secret(random: SecureRandom = SecureRandom()): ByteArray {
    val secret = ByteArray(DEVICE_RECOVERY_SECRET_BYTES)
    do {
        random.nextBytes(secret)
    } while (secret.all { it == 0.toByte() })
    return secret
}

private fun derive_inner_key(secret: ByteArray, snapshot_id: String): ByteArray =
    hkdf_sha256(
        secret,
        snapshot_id.toByteArray(Charsets.UTF_8),
        DEVICE_RECOVERY_INFO.toByteArray(Charsets.UTF_8),
        32,
    )

fun encode_device_snapshot_payload(payload: DeviceSnapshotPayload): ByteArray {
    val unlocked = JSONArray()
    for ((armored, unlocked_armored) in payload.unlocked_keys) {
        unlocked.put(JSONArray().put(armored).put(unlocked_armored))
    }
    val ratchet = JSONArray()
    for (entry in payload.ratchet_keys) ratchet.put(entry)
    val json = JSONObject()
        .put("v", DEVICE_RECOVERY_SNAPSHOT_VERSION)
        .put("identity_key", payload.identity_key)
        .put("previous_keys", JSONArray(payload.previous_keys))
        .put("legacy_identity_keys", JSONArray(payload.legacy_identity_keys))
        .put("unlocked_keys", unlocked)
        .put("storage_keys", JSONArray(payload.storage_keys))
        .put("ratchet_keys", ratchet)
    return json.toString().toByteArray(Charsets.UTF_8)
}

private fun json_string_list(array: JSONArray?): List<String> {
    if (array == null) return emptyList()
    return (0 until array.length()).mapNotNull { array.optString(it, "").takeIf { value -> value.isNotEmpty() } }
}

fun decode_device_snapshot_payload(bytes: ByteArray): DeviceSnapshotPayload? {
    val json = runCatching { JSONObject(String(bytes, Charsets.UTF_8)) }.getOrNull() ?: return null
    if (json.optInt("v", 0) != DEVICE_RECOVERY_SNAPSHOT_VERSION) return null
    val identity_key = json.optString("identity_key", "")
    if (identity_key.isEmpty()) return null

    val unlocked = mutableListOf<Pair<String, String>>()
    json.optJSONArray("unlocked_keys")?.let { array ->
        for (i in 0 until array.length()) {
            val pair = array.optJSONArray(i) ?: continue
            if (pair.length() != 2) continue
            val armored = pair.optString(0, "")
            val unlocked_armored = pair.optString(1, "")
            if (armored.isEmpty() || unlocked_armored.isEmpty()) continue
            unlocked.add(armored to unlocked_armored)
        }
    }

    val ratchet = mutableListOf<JSONObject>()
    json.optJSONArray("ratchet_keys")?.let { array ->
        for (i in 0 until array.length()) array.optJSONObject(i)?.let { ratchet.add(it) }
    }

    return DeviceSnapshotPayload(
        identity_key = identity_key,
        previous_keys = json_string_list(json.optJSONArray("previous_keys")),
        legacy_identity_keys = json_string_list(json.optJSONArray("legacy_identity_keys")),
        unlocked_keys = unlocked,
        storage_keys = json_string_list(json.optJSONArray("storage_keys")),
        ratchet_keys = ratchet,
    )
}

fun seal_device_snapshot(
    payload: DeviceSnapshotPayload,
    secret: ByteArray,
    device_key: DeviceRecoveryKey,
    meta: DeviceSnapshotMeta,
    random: SecureRandom = SecureRandom(),
): DeviceSealedBytes {
    val aad = device_snapshot_aad(meta.user_id, meta.snapshot_id, meta.source_hash)
    val inner_key = derive_inner_key(secret, meta.snapshot_id)
    val plaintext = encode_device_snapshot_payload(payload)
    val nonce = ByteArray(AES_GCM_NONCE_BYTES).also { random.nextBytes(it) }
    try {
        val inner = AesGcm.encrypt(inner_key, nonce, plaintext, aad)
        val combined = nonce + inner
        try {
            return device_key.seal(combined, aad)
        } finally {
            combined.fill(0)
        }
    } finally {
        inner_key.fill(0)
        plaintext.fill(0)
    }
}

fun open_device_snapshot(
    record: DeviceSnapshotRecord,
    secret: ByteArray,
    device_key: DeviceRecoveryKey,
): DeviceSnapshotPayload? {
    val aad = device_snapshot_aad(record.user_id, record.snapshot_id, record.source_hash)
    val combined = runCatching { device_key.open(record.iv, record.sealed, aad) }.getOrNull() ?: return null
    if (combined.size <= AES_GCM_NONCE_BYTES) {
        combined.fill(0)
        return null
    }
    val inner_key = derive_inner_key(secret, record.snapshot_id)
    try {
        val plaintext = runCatching {
            AesGcm.decrypt(
                inner_key,
                combined.copyOfRange(0, AES_GCM_NONCE_BYTES),
                combined.copyOfRange(AES_GCM_NONCE_BYTES, combined.size),
                aad,
            )
        }.getOrNull() ?: return null
        try {
            return decode_device_snapshot_payload(plaintext)
        } finally {
            plaintext.fill(0)
        }
    } finally {
        inner_key.fill(0)
        combined.fill(0)
    }
}
