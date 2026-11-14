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
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import org.astermail.android.auth.MAX_PRIOR_STORAGE_KEYS
import org.astermail.android.auth.STORAGE_REKEY_PENDING
import org.astermail.android.auth.prepend_keks_keeping_all
import org.astermail.android.auth.vault_identity_key
import org.astermail.android.auth.vault_identity_key_materials
import org.astermail.android.crypto.AccountKey
import org.astermail.android.crypto.PgpDecryptor
import org.astermail.android.crypto.PgpEncryptor
import org.astermail.android.mail.ratchet.vault_identity_fingerprint
import org.astermail.android.util.passphrase_chars
import org.json.JSONArray
import org.json.JSONObject

const val STORAGE_KEY_WRAPS_PATH = "/core/v1/auth/me/storage-key-wraps"
private const val STORAGE_KEY_ID_BYTES = 16

data class StorageKeyWrap(
    val key_id: String,
    val wrap: String,
    val identity_fingerprint: String,
    val created_at: String?,
)

interface StorageKeyWrapCrypto {
    fun seal(data_kek: ByteArray, identity_private_key: String): ByteArray?
    fun open(wrap: ByteArray, private_keys: List<String>): ByteArray?
}

class PgpStorageKeyWrapCrypto(private val passphrase_source: () -> ByteArray?) : StorageKeyWrapCrypto {

    override fun seal(data_kek: ByteArray, identity_private_key: String): ByteArray? {
        val public_key = AccountKey.public_key_armored(identity_private_key) ?: return null
        return with_passphrase { chars ->
            PgpEncryptor.encrypt_and_sign_bytes_binary(data_kek, listOf(public_key), identity_private_key, chars)
        }
    }

    override fun open(wrap: ByteArray, private_keys: List<String>): ByteArray? =
        with_passphrase { chars -> PgpDecryptor.decrypt_bytes_signed_by_own_keys(wrap, private_keys, chars) }

    private fun <T> with_passphrase(block: (CharArray) -> T?): T? {
        val pass = passphrase_source() ?: return null
        val chars = passphrase_chars(pass)
        pass.fill(0)
        return try {
            block(chars)
        } finally {
            chars.fill('\u0000')
        }
    }
}

fun wrap_key_id(data_kek: ByteArray): String =
    MessageDigest.getInstance("SHA-256").digest(data_kek)
        .copyOf(STORAGE_KEY_ID_BYTES)
        .joinToString("") { "%02x".format(it) }

fun parse_storage_key_wraps(data: JsonElement?): List<StorageKeyWrap>? {
    val obj = data.as_object() ?: return null
    if (obj["wraps"] !is JsonArray) return null
    return obj.objects("wraps").mapNotNull { entry ->
        val key_id = entry.str("key_id")?.trim()?.lowercase() ?: return@mapNotNull null
        val wrap = entry.str("wrap") ?: return@mapNotNull null
        StorageKeyWrap(key_id, wrap, entry.str("identity_fingerprint").orEmpty().trim(), entry.str("created_at"))
    }
}

suspend fun list_storage_key_wraps(api: ResealApi): List<StorageKeyWrap>? {
    val reply = api.get(STORAGE_KEY_WRAPS_PATH)
    if (reply.error) return null
    return parse_storage_key_wraps(reply.data)
}

fun vault_random_storage_key(vault: JSONObject): ByteArray? {
    if (vault.opt("data_kek_random") != true) return null
    return decode_b64(vault.optString("data_kek", ""))?.takeIf { it.size == RESEAL_KEY_LENGTH }
}

private fun vault_key_ids(vault: JSONObject): Pair<String?, Set<String>> {
    val current = decode_b64(vault.optString("data_kek", ""))?.takeIf { it.size == RESEAL_KEY_LENGTH }
    val current_id = current?.let(::wrap_key_id)
    current?.fill(0)
    val legacy = HashSet<String>()
    val array = vault.optJSONArray("legacy_keks")
    for (i in 0 until (array?.length() ?: 0)) {
        val raw = decode_b64(array?.optJSONObject(i)?.optString("k", "")) ?: continue
        if (raw.size == RESEAL_KEY_LENGTH) legacy.add(wrap_key_id(raw))
        raw.fill(0)
    }
    return current_id to legacy
}

fun side_copy_supersedes_vault(vault: JSONObject, key_id: String): Boolean {
    val (current_id, legacy_ids) = vault_key_ids(vault)
    if (current_id == key_id) return false
    val vault_is_random = vault.opt("data_kek_random") == true
    return !(vault_is_random && key_id in legacy_ids)
}

fun open_side_copy(
    wraps: List<StorageKeyWrap>,
    vault: JSONObject,
    crypto: StorageKeyWrapCrypto,
): ByteArray? {
    val (current_id, _) = vault_key_ids(vault)
    val own_keys = vault_identity_key_materials(vault)
    if (own_keys.isEmpty()) return null
    for (wrap in wraps) {
        if (wrap.key_id == current_id) return null
        val bytes = decode_b64(wrap.wrap) ?: continue
        val opened = runCatching { crypto.open(bytes, own_keys) }.getOrNull() ?: continue
        if (opened.size != RESEAL_KEY_LENGTH || wrap_key_id(opened) != wrap.key_id) {
            opened.fill(0)
            continue
        }
        if (!side_copy_supersedes_vault(vault, wrap.key_id)) {
            opened.fill(0)
            return null
        }
        return opened
    }
    return null
}

fun apply_side_copy_to_vault(vault: JSONObject, data_kek: ByteArray, now: String): Boolean {
    if (data_kek.size != RESEAL_KEY_LENGTH) return false
    if (!side_copy_supersedes_vault(vault, wrap_key_id(data_kek))) return false
    val restored = encode_b64(data_kek)
    val former = vault.optString("data_kek", "").takeIf { it.isNotBlank() && it != restored }
    vault.put("data_kek", restored)
    vault.put("data_kek_random", true)
    vault.put(
        "legacy_keks",
        prepend_keks_keeping_all(vault.optJSONArray("legacy_keks"), listOfNotNull(former), now, restored),
    )
    val prior = ArrayList<String>()
    former?.let(prior::add)
    val carried = vault.optJSONObject("storage_rekey")
        ?.takeIf { it.optString("state", "") == STORAGE_REKEY_PENDING }
        ?.optJSONArray("prior_keys")
    for (i in 0 until (carried?.length() ?: 0)) {
        val value = carried?.optString(i, "").orEmpty()
        if (value.isNotBlank() && value != restored && value !in prior) prior.add(value)
    }
    val rekey = JSONObject().put("state", STORAGE_REKEY_PENDING).put("started_at", now)
    if (prior.isNotEmpty()) rekey.put("prior_keys", JSONArray(prior.take(MAX_PRIOR_STORAGE_KEYS)))
    vault.put("storage_rekey", rekey)
    return true
}

fun needs_side_copy(
    wraps: List<StorageKeyWrap>,
    vault: JSONObject,
    identity_fingerprint: String,
    crypto: StorageKeyWrapCrypto,
): Boolean {
    val key = vault_random_storage_key(vault) ?: return false
    val key_id = wrap_key_id(key)
    val own_keys = vault_identity_key_materials(vault)
    return try {
        wraps.none { wrap ->
            wrap.key_id == key_id && wrap.identity_fingerprint == identity_fingerprint &&
                opens_to(wrap, own_keys, crypto, key)
        }
    } finally {
        key.fill(0)
    }
}

private fun opens_to(
    wrap: StorageKeyWrap,
    own_keys: List<String>,
    crypto: StorageKeyWrapCrypto,
    expected: ByteArray,
): Boolean {
    val bytes = decode_b64(wrap.wrap) ?: return false
    val opened = runCatching { crypto.open(bytes, own_keys) }.getOrNull() ?: return false
    return try {
        MessageDigest.isEqual(opened, expected)
    } finally {
        opened.fill(0)
    }
}

enum class SideCopyStore { STORED, CURRENT, SKIPPED, FAILED }

suspend fun store_side_copy(
    api: ResealApi,
    crypto: StorageKeyWrapCrypto,
    vault: JSONObject,
    wraps: List<StorageKeyWrap>,
    force: Boolean = false,
): SideCopyStore {
    val identity = vault_identity_key(vault).takeIf { it.isNotBlank() } ?: return SideCopyStore.SKIPPED
    val fingerprint = runCatching { vault_identity_fingerprint(vault) }.getOrNull() ?: return SideCopyStore.SKIPPED
    if (!force && !needs_side_copy(wraps, vault, fingerprint, crypto)) return SideCopyStore.CURRENT
    val key = vault_random_storage_key(vault) ?: return SideCopyStore.SKIPPED
    val key_id = wrap_key_id(key)
    val sealed = try {
        runCatching { crypto.seal(key, identity) }.getOrNull()
    } finally {
        key.fill(0)
    }
    if (sealed == null) return SideCopyStore.FAILED
    val reply = api.post(
        STORAGE_KEY_WRAPS_PATH,
        json_object("key_id" to key_id, "wrap" to encode_b64(sealed), "identity_fingerprint" to fingerprint),
    )
    return if (reply.error) SideCopyStore.FAILED else SideCopyStore.STORED
}

fun vault_identity_marker(vault: JSONObject): String =
    vault_identity_key(vault) + "|" + runCatching { vault_identity_fingerprint(vault) }.getOrNull().orEmpty()

suspend fun refresh_side_copy(api: ResealApi, crypto: StorageKeyWrapCrypto, vault: JSONObject): SideCopyStore {
    val wraps = runCatching { list_storage_key_wraps(api) }.getOrNull() ?: return SideCopyStore.FAILED
    return runCatching { store_side_copy(api, crypto, vault, wraps, force = true) }.getOrDefault(SideCopyStore.FAILED)
}

suspend fun update_identity_with_side_copy(
    updater: VaultUpdater,
    api: ResealApi,
    crypto: StorageKeyWrapCrypto,
    edit: suspend (VaultDraft) -> VaultEdit,
): VaultUpdateOutcome {
    var before: String? = null
    val outcome = updater.update { draft ->
        before = vault_identity_marker(draft.vault)
        edit(draft)
    }
    if (outcome is VaultUpdateOutcome.Written && vault_identity_marker(outcome.vault) != before) {
        refresh_side_copy(api, crypto, outcome.vault)
    }
    return outcome
}
