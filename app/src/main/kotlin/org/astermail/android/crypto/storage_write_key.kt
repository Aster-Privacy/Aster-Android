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

package org.astermail.android.crypto

import java.security.MessageDigest
import java.security.SecureRandom
import java.util.Base64
import org.astermail.android.storage.RetiredStorageKeys
import org.astermail.android.storage.SessionKeyStore

object StorageWriteKey {

    const val KEY_LENGTH = 32

    val UNSET_IDENTITY_MATERIALS: List<String> = listOf("undefined", "", "null")

    val PUBLIC_CONSTANT_CONTEXTS: List<String> = AccountKey.DATA_CONTEXTS +
        listOf("astermail-calendar-v2", "astermail-envelope-v1", "astermail-import-v1")

    private val public_mail_keks: Set<String> = UNSET_IDENTITY_MATERIALS
        .flatMap { material -> PUBLIC_CONSTANT_CONTEXTS.map { context -> material + context } }
        .map { seed ->
            val digest = MessageDigest.getInstance("SHA-256")
                .digest(seed.toByteArray(Charsets.UTF_8))
            try {
                Base64.getEncoder().encodeToString(digest)
            } finally {
                digest.fill(0)
            }
        }
        .toSet()

    fun is_public_mail_kek(key: ByteArray): Boolean =
        key.size == KEY_LENGTH && encode_key(key) in public_mail_keks

    fun public_mail_kek_raws(): List<ByteArray> = public_mail_keks.mapNotNull { decode_key(it) }

    fun real_decrypt_keks(encoded: List<String>): List<String> = encoded.filter { entry ->
        val key = decode_legacy_key(entry)
        key?.fill(0)
        key != null
    }

    fun with_public_fallback(encoded: List<String>): List<String> =
        real_decrypt_keks(encoded) + public_mail_keks.sorted()

    fun open_with_decrypt_keks(
        encoded: List<String>,
        on_constant: () -> Unit = {},
        attempt: (ByteArray) -> ByteArray?,
    ): ByteArray? {
        for (entry in with_public_fallback(encoded)) {
            val key = decode_key(entry) ?: continue
            try {
                val opened = attempt(key) ?: continue
                if (is_public_mail_kek(key)) on_constant()
                return opened
            } finally {
                key.fill(0)
            }
        }
        return null
    }

    fun decode_legacy_key(encoded: String): ByteArray? {
        val key = decode_key(encoded) ?: return null
        if (!is_public_mail_kek(key)) return key
        key.fill(0)
        return null
    }

    private val known_kdf_versions = setOf(
        CryptoNative.legacy_storage_kdf_version,
        CryptoNative.stretched_storage_kdf_version,
    )

    fun is_known_kdf_version(kdf_version: Int?): Boolean =
        kdf_version == null || kdf_version in known_kdf_versions

    fun password_storage_key(passphrase: ByteArray, kdf_version: Int?): ByteArray? = when (kdf_version ?: CryptoNative.legacy_storage_kdf_version) {
        CryptoNative.legacy_storage_kdf_version -> CryptoNative.derive_storage_key(passphrase)
        CryptoNative.stretched_storage_kdf_version -> CryptoNative.derive_stretched_storage_key(passphrase)
        else -> null
    }

    fun random_data_kek(random: SecureRandom = SecureRandom()): ByteArray {
        while (true) {
            val key = ByteArray(KEY_LENGTH)
            random.nextBytes(key)
            if (key.any { it.toInt() != 0 }) return key
        }
    }

    fun select_write_key(data_kek: ByteArray?): ByteArray? {
        if (data_kek != null && data_kek.size == KEY_LENGTH) return data_kek
        data_kek?.fill(0)
        return null
    }

    fun write_key(store: SessionKeyStore): ByteArray? = select_write_key(store.get_data_kek())

    fun password_read_keys(
        passphrase: ByteArray?,
        kdf_version: Int?,
        derive: (ByteArray, Int?) -> ByteArray? = ::password_storage_key,
    ): List<ByteArray> {
        if (passphrase == null) return emptyList()
        val versions = when (kdf_version ?: CryptoNative.legacy_storage_kdf_version) {
            CryptoNative.legacy_storage_kdf_version -> listOf(CryptoNative.legacy_storage_kdf_version)
            else -> listOf(CryptoNative.stretched_storage_kdf_version, CryptoNative.legacy_storage_kdf_version)
        }
        return versions.mapNotNull { version -> runCatching { derive(passphrase, version) }.getOrNull() }
    }

    val restore_key_derive: (ByteArray, Int?) -> ByteArray? = ::password_storage_key

    fun <T> with_restore_read_keys(
        passphrase: ByteArray?,
        kdf_version: Int?,
        derive: (ByteArray, Int?) -> ByteArray? = restore_key_derive,
        block: (List<ByteArray>) -> T,
    ): T {
        val keys = password_read_keys(passphrase, kdf_version, derive)
        try {
            return block(keys)
        } finally {
            keys.forEach { it.fill(0) }
        }
    }

    fun lookup_keys(
        write_key: ByteArray?,
        legacy_keks: List<String>,
        password_keys: List<ByteArray> = emptyList(),
    ): List<ByteArray> = dedupe(
        listOfNotNull(write_key) + legacy_keks.mapNotNull(::decode_legacy_key) + password_keys + public_mail_kek_raws(),
    )

    fun lookup_keys(store: SessionKeyStore): List<ByteArray> =
        dedupe(
            listOfNotNull(write_key(store)) + kept_decrypt_keks(store) + legacy_password_keys(store) +
                public_mail_kek_raws(),
        )

    fun note_if_public(store: SessionKeyStore, key: ByteArray) {
        if (is_public_mail_kek(key)) store.note_public_constant_use()
    }

    fun <T : Any> open_noting_constants(
        store: SessionKeyStore,
        keys: List<ByteArray>,
        on_constant: () -> Unit = {},
        attempt: (List<ByteArray>) -> T?,
    ): T? {
        val constants = keys.filter(::is_public_mail_kek)
        val real = keys.filterNot(::is_public_mail_kek)
        attempt(real)?.let { return it }
        if (constants.isEmpty()) return null
        val opened = attempt(constants)
        if (opened != null) {
            store.note_public_constant_use()
            on_constant()
        }
        return opened
    }

    fun read_keys(store: SessionKeyStore): List<ByteArray> =
        dedupe(
            listOfNotNull(write_key(store), store.get_data_kek()) +
                kept_decrypt_keks(store) +
                legacy_read_keys(store) +
                public_mail_kek_raws(),
        )

    fun drop_retired_keys(keys: List<ByteArray>, retired: List<ByteArray>): List<ByteArray> {
        if (retired.isEmpty()) return keys
        val out = mutableListOf<ByteArray>()
        for (key in keys) {
            if (retired.any { MessageDigest.isEqual(it, key) }) key.fill(0) else out.add(key)
        }
        return out
    }

    fun retired_key_hash(key: ByteArray): String = RetiredStorageKeys.hash(key)

    fun not_retired(keys: List<ByteArray>, retired_hashes: Collection<String>): List<ByteArray> =
        if (retired_hashes.isEmpty()) keys else keys.filterNot { retired_key_hash(it) in retired_hashes }

    fun retirement_hashes(swept_keys: List<ByteArray>): List<String>? =
        swept_keys.takeIf { it.isNotEmpty() }?.map(::retired_key_hash)?.distinct()

    fun sweep_keys(store: SessionKeyStore): List<ByteArray>? {
        if (store.legacy_storage_keys_retired()) {
            backfill_retired_hashes(store)
            return emptyList()
        }
        val password_keys = legacy_password_keys(store)
        if (password_keys.isEmpty()) return null
        return dedupe(password_keys + listOfNotNull(store.get()))
    }

    fun retire_legacy_keys(store: SessionKeyStore, swept_keys: List<ByteArray>): Boolean {
        val hashes = retirement_hashes(swept_keys) ?: return false
        return store.retire_legacy_storage_keys(hashes)
    }

    private val unrecorded_backfills: MutableSet<SessionKeyStore> =
        java.util.Collections.synchronizedSet(java.util.Collections.newSetFromMap(java.util.WeakHashMap()))

    fun backfill_retired_hashes(store: SessionKeyStore): Boolean {
        if (!store.legacy_storage_keys_retired() || store.retired_storage_key_hashes().isNotEmpty()) return false
        if (store in unrecorded_backfills) return false
        val keys = dedupe(unhashed_retired_password_keys(store).ifEmpty { return false } + listOfNotNull(store.get()))
        val recorded = try {
            retire_legacy_keys(store, keys)
        } finally {
            keys.forEach { it.fill(0) }
        }
        if (!recorded && unrecorded_backfills.add(store)) {
            store.add_clear_listener { unrecorded_backfills.remove(store) }
        }
        return recorded
    }

    private fun kept_decrypt_keks(store: SessionKeyStore): List<ByteArray> {
        if (!store.legacy_storage_keys_retired()) return store.get_decrypt_keks().mapNotNull(::decode_legacy_key)
        backfill_retired_hashes(store)
        val keks = store.get_decrypt_keks().mapNotNull(::decode_legacy_key)
        val known_retired = listOfNotNull(store.get()) +
            if (store.retired_storage_key_hashes().isEmpty()) unhashed_retired_password_keys(store) else emptyList()
        return try {
            drop_retired_keys(keks, known_retired)
        } finally {
            known_retired.forEach { it.fill(0) }
        }
    }

    private fun unhashed_retired_password_keys(store: SessionKeyStore): List<ByteArray> {
        val passphrase = store.get_passphrase() ?: return emptyList()
        return try {
            password_read_keys(passphrase, CryptoNative.legacy_storage_kdf_version)
        } finally {
            passphrase.fill(0)
        }
    }

    fun legacy_read_keys(store: SessionKeyStore): List<ByteArray> =
        if (store.legacy_storage_keys_retired()) {
            emptyList()
        } else {
            dedupe(legacy_password_keys(store) + listOfNotNull(store.get()))
        }

    private fun legacy_password_keys(store: SessionKeyStore): List<ByteArray> {
        if (store.legacy_storage_keys_retired()) return emptyList()
        val passphrase = store.get_passphrase()
        return try {
            password_read_keys(passphrase, CryptoNative.legacy_storage_kdf_version)
        } finally {
            passphrase?.fill(0)
        }
    }

    fun encode_key(key: ByteArray): String = Base64.getEncoder().encodeToString(key)

    fun decode_key(encoded: String): ByteArray? {
        val decoded = runCatching { Base64.getMimeDecoder().decode(encoded.trim()) }.getOrNull() ?: return null
        if (decoded.size != KEY_LENGTH) {
            decoded.fill(0)
            return null
        }
        return decoded
    }

    private fun dedupe(keys: List<ByteArray>): List<ByteArray> {
        val seen = mutableSetOf<String>()
        val out = mutableListOf<ByteArray>()
        for (key in keys) {
            if (key.size != KEY_LENGTH || !seen.add(encode_key(key))) {
                key.fill(0)
                continue
            }
            out.add(key)
        }
        return out
    }
}
