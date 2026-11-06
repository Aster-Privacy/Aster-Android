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

import java.security.MessageDigest
import java.security.SecureRandom
import java.util.Base64
import javax.crypto.Cipher
import javax.crypto.Mac
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.SecretKeySpec
import org.json.JSONObject

const val ESCROW_HKDF_SALT = "Aster_Mail_Plaintext_Escrow"
const val ESCROW_HKDF_INFO = "plaintext_escrow_key"
const val RATCHET_HKDF_SALT = "Aster Mail_Ratchet_State_Encryption"
const val RATCHET_HKDF_INFO = "ratchet_state_key"
const val METADATA_HKDF_SALT = "aster-metadata-salt-v1"
const val METADATA_HKDF_INFO_PREFIX = "aster-metadata-encryption-v1:"
const val RESEAL_NONCE_LENGTH = 12
const val RESEAL_KEY_LENGTH = 32

data class SealedValue(val ciphertext: String, val nonce: String)

data class VaultKeyView(
    val data_kek: String?,
    val data_kek_random: Boolean,
    val storage_rekey_state: String?,
    val prior_keys: List<String>,
    val legacy_keks: List<String>,
) {
    companion object {
        fun of(vault: JSONObject): VaultKeyView {
            val rekey = vault.optJSONObject("storage_rekey")
            val prior = rekey?.optJSONArray("prior_keys")
            val legacy = vault.optJSONArray("legacy_keks")
            val prior_values = ArrayList<String>()
            for (i in 0 until (prior?.length() ?: 0)) {
                val value = prior?.optString(i, "").orEmpty()
                if (value.isNotBlank()) prior_values.add(value)
            }
            val legacy_values = ArrayList<String>()
            for (i in 0 until (legacy?.length() ?: 0)) {
                val value = legacy?.optJSONObject(i)?.optString("k", "").orEmpty()
                if (value.isNotBlank()) legacy_values.add(value)
            }
            return VaultKeyView(
                data_kek = vault.optString("data_kek", "").takeIf { it.isNotBlank() },
                data_kek_random = vault.opt("data_kek_random") == true,
                storage_rekey_state = rekey?.optString("state", "")?.takeIf { it.isNotBlank() },
                prior_keys = prior_values,
                legacy_keks = legacy_values,
            )
        }
    }
}

fun decode_b64(value: String?): ByteArray? {
    if (value.isNullOrEmpty()) return null
    return runCatching { Base64.getDecoder().decode(value.trim()) }.getOrNull()
}

fun encode_b64(bytes: ByteArray): String = Base64.getEncoder().encodeToString(bytes)

fun hmac_sha256(key: ByteArray, data: ByteArray): ByteArray {
    val mac = Mac.getInstance("HmacSHA256")
    mac.init(SecretKeySpec(key, "HmacSHA256"))
    return mac.doFinal(data)
}

fun hkdf_sha256(ikm: ByteArray, salt: ByteArray, info: ByteArray, length: Int = RESEAL_KEY_LENGTH): ByteArray {
    val prk = hmac_sha256(if (salt.isEmpty()) ByteArray(32) else salt, ikm)
    val out = ByteArray(length)
    var previous = ByteArray(0)
    var offset = 0
    var counter = 1
    while (offset < length) {
        previous = hmac_sha256(prk, previous + info + byteArrayOf(counter.toByte()))
        val take = minOf(previous.size, length - offset)
        System.arraycopy(previous, 0, out, offset, take)
        offset += take
        counter += 1
    }
    prk.fill(0)
    return out
}

fun sha256(bytes: ByteArray): ByteArray = MessageDigest.getInstance("SHA-256").digest(bytes)

fun derive_escrow_key(raw: ByteArray): ByteArray =
    hkdf_sha256(raw, ESCROW_HKDF_SALT.toByteArray(), ESCROW_HKDF_INFO.toByteArray())

fun derive_ratchet_key(raw: ByteArray): ByteArray =
    hkdf_sha256(raw, RATCHET_HKDF_SALT.toByteArray(), RATCHET_HKDF_INFO.toByteArray())

fun derive_metadata_key(raw: ByteArray, context: String): ByteArray =
    hkdf_sha256(raw, METADATA_HKDF_SALT.toByteArray(), (METADATA_HKDF_INFO_PREFIX + context).toByteArray())

fun derive_hmac_key(raw: ByteArray, info: String): ByteArray = sha256(raw + info.toByteArray(Charsets.UTF_8))

fun storage_key_id(raw: ByteArray): String = encode_b64(sha256(raw).copyOf(12))

fun gcm_open(key: ByteArray, ciphertext: ByteArray, nonce: ByteArray, aad: ByteArray? = null): ByteArray? {
    if (nonce.isEmpty() || key.size != RESEAL_KEY_LENGTH) return null
    return runCatching {
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.DECRYPT_MODE, SecretKeySpec(key, "AES"), GCMParameterSpec(128, nonce))
        if (aad != null) cipher.updateAAD(aad)
        cipher.doFinal(ciphertext)
    }.getOrNull()
}

private val seal_random = SecureRandom()

fun gcm_seal(key: ByteArray, plaintext: ByteArray, aad: ByteArray? = null): SealedValue {
    val nonce = ByteArray(RESEAL_NONCE_LENGTH).also { seal_random.nextBytes(it) }
    val cipher = Cipher.getInstance("AES/GCM/NoPadding")
    cipher.init(Cipher.ENCRYPT_MODE, SecretKeySpec(key, "AES"), GCMParameterSpec(128, nonce))
    if (aad != null) cipher.updateAAD(aad)
    return SealedValue(encode_b64(cipher.doFinal(plaintext)), encode_b64(nonce))
}

fun open_with_any(keys: MutableList<ByteArray>, ciphertext: ByteArray, nonce: ByteArray, aad: ByteArray? = null): ByteArray? {
    for (i in keys.indices) {
        val opened = gcm_open(keys[i], ciphertext, nonce, aad) ?: continue
        if (i > 0) keys.add(0, keys.removeAt(i))
        return opened
    }
    return null
}

fun collect_old_storage_raws(view: VaultKeyView, extra: List<ByteArray> = emptyList()): MutableList<ByteArray> {
    val current = decode_b64(view.data_kek)?.takeIf { it.size == RESEAL_KEY_LENGTH }
    val out = ArrayList<ByteArray>()
    fun consider(raw: ByteArray?) {
        if (raw == null) return
        val rejected = raw.size != RESEAL_KEY_LENGTH ||
            (current != null && MessageDigest.isEqual(raw, current)) ||
            out.any { MessageDigest.isEqual(it, raw) }
        if (rejected) {
            raw.fill(0)
            return
        }
        out.add(raw)
    }
    view.prior_keys.forEach { consider(decode_b64(it)) }
    view.legacy_keks.forEach { consider(decode_b64(it)) }
    extra.forEach { consider(it.copyOf()) }
    current?.fill(0)
    return out
}

class ResealKeys(
    private val new_raw: ByteArray,
    private val old_raws: List<ByteArray>,
) {
    val key_id: String = storage_key_id(new_raw)
    val new_aes: ByteArray = new_raw
    val new_escrow: ByteArray = derive_escrow_key(new_raw)
    val new_ratchet: ByteArray = derive_ratchet_key(new_raw)
    val old_aes: MutableList<ByteArray> = old_raws.toMutableList()
    val old_escrow: MutableList<ByteArray> = old_raws.map(::derive_escrow_key).toMutableList()
    val old_ratchet: MutableList<ByteArray> = old_raws.map(::derive_ratchet_key).toMutableList()
    private val hmac_cache = HashMap<String, ByteArray>()
    private val old_hmac_cache = HashMap<String, List<ByteArray>>()
    private val metadata_cache = HashMap<String, ByteArray>()
    private val old_metadata_cache = HashMap<String, MutableList<ByteArray>>()
    private var disposed = false

    val old_count: Int get() = old_raws.size

    fun hmac(info: String): ByteArray = hmac_cache.getOrPut(info) { derive_hmac_key(new_raw, info) }

    fun old_hmacs(info: String): List<ByteArray> = old_hmac_cache.getOrPut(info) {
        old_raws.map { derive_hmac_key(it, info) }
    }

    fun metadata_key(context: String): ByteArray =
        metadata_cache.getOrPut(context) { derive_metadata_key(new_raw, context) }

    fun old_metadata_keys(context: String): MutableList<ByteArray> = old_metadata_cache.getOrPut(context) {
        old_raws.map { derive_metadata_key(it, context) }.toMutableList()
    }

    fun dispose() {
        if (disposed) return
        disposed = true
        new_escrow.fill(0)
        new_ratchet.fill(0)
        old_escrow.forEach { it.fill(0) }
        old_ratchet.forEach { it.fill(0) }
        hmac_cache.values.forEach { it.fill(0) }
        old_hmac_cache.values.forEach { list -> list.forEach { it.fill(0) } }
        metadata_cache.values.forEach { it.fill(0) }
        old_metadata_cache.values.forEach { list -> list.forEach { it.fill(0) } }
        new_raw.fill(0)
        old_raws.forEach { it.fill(0) }
    }
}

fun build_reseal_keys(view: VaultKeyView, extra_old: List<ByteArray> = emptyList()): ResealKeys? {
    if (!view.data_kek_random) return null
    val raw = decode_b64(view.data_kek) ?: return null
    if (raw.size != RESEAL_KEY_LENGTH) {
        raw.fill(0)
        return null
    }
    return ResealKeys(raw, collect_old_storage_raws(view, extra_old))
}

fun opens_under_new(keys: ResealKeys, ciphertext_b64: String?, nonce_b64: String?): Boolean {
    val ciphertext = decode_b64(ciphertext_b64) ?: return false
    val nonce = decode_b64(nonce_b64) ?: return false
    val opened = gcm_open(keys.new_aes, ciphertext, nonce) ?: return false
    opened.fill(0)
    return true
}

fun open_under_old(keys: ResealKeys, ciphertext_b64: String?, nonce_b64: String?): ByteArray? {
    val ciphertext = decode_b64(ciphertext_b64) ?: return null
    val nonce = decode_b64(nonce_b64) ?: return null
    return open_with_any(keys.old_aes, ciphertext, nonce)
}

fun seal_under_new(
    keys: ResealKeys,
    plaintext: ByteArray,
    aad: ByteArray? = null,
    aes_key: ByteArray = keys.new_aes,
): SealedValue = gcm_seal(aes_key, plaintext, aad)

fun sign_under_new(keys: ResealKeys, info: String, value: String): String =
    encode_b64(hmac_sha256(keys.hmac(info), value.toByteArray(Charsets.UTF_8)))

fun sign_with(key: ByteArray, value: String): String =
    encode_b64(hmac_sha256(key, value.toByteArray(Charsets.UTF_8)))
