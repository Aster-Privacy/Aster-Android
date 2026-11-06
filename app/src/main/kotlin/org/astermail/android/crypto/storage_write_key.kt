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

import java.security.SecureRandom
import java.util.Base64
import org.astermail.android.storage.SessionKeyStore

object StorageWriteKey {

    const val KEY_LENGTH = 32

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

    fun select_write_key(
        data_kek: ByteArray?,
        kdf_version: Int?,
        passphrase: () -> ByteArray?,
        derive: (ByteArray, Int?) -> ByteArray? = ::password_storage_key,
    ): ByteArray? {
        if (data_kek != null && data_kek.size == KEY_LENGTH) return data_kek
        data_kek?.fill(0)
        if (!is_known_kdf_version(kdf_version)) return null
        val password = passphrase() ?: return null
        return try {
            derive(password, kdf_version)?.takeIf { it.size == KEY_LENGTH }
        } finally {
            password.fill(0)
        }
    }

    fun write_key(store: SessionKeyStore): ByteArray? =
        select_write_key(store.get_data_kek(), store.get_storage_kdf_version(), store::get_passphrase, ::remembered_password_key)

    private val memo_lock = Any()
    private var memo_fingerprint: String? = null
    private var memo_key: ByteArray? = null

    private fun remembered_password_key(passphrase: ByteArray, kdf_version: Int?): ByteArray? {
        if ((kdf_version ?: CryptoNative.legacy_storage_kdf_version) == CryptoNative.legacy_storage_kdf_version) {
            return password_storage_key(passphrase, kdf_version)
        }
        val digest = java.security.MessageDigest.getInstance("SHA-256")
        digest.update(passphrase)
        digest.update(kdf_version.toString().toByteArray(Charsets.UTF_8))
        val fingerprint = encode_key(digest.digest())
        synchronized(memo_lock) {
            if (memo_fingerprint == fingerprint) memo_key?.let { return it.copyOf() }
        }
        val derived = password_storage_key(passphrase, kdf_version) ?: return null
        synchronized(memo_lock) {
            memo_key?.fill(0)
            memo_key = derived.copyOf()
            memo_fingerprint = fingerprint
        }
        return derived
    }

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

    fun lookup_keys(
        write_key: ByteArray?,
        legacy_keks: List<String>,
        password_keys: List<ByteArray> = emptyList(),
    ): List<ByteArray> = dedupe(listOfNotNull(write_key) + legacy_keks.mapNotNull(::decode_key) + password_keys)

    fun lookup_keys(store: SessionKeyStore): List<ByteArray> {
        val passphrase = store.get_passphrase()
        return try {
            lookup_keys(
                write_key(store),
                store.get_decrypt_keks(),
                password_read_keys(passphrase, CryptoNative.legacy_storage_kdf_version),
            )
        } finally {
            passphrase?.fill(0)
        }
    }

    fun read_keys(store: SessionKeyStore): List<ByteArray> {
        val passphrase = store.get_passphrase()
        return try {
            dedupe(
                listOfNotNull(write_key(store), store.get_data_kek()) +
                    password_read_keys(passphrase, CryptoNative.legacy_storage_kdf_version) +
                    store.get_decrypt_keks().mapNotNull(::decode_key) +
                    listOfNotNull(store.get()),
            )
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
