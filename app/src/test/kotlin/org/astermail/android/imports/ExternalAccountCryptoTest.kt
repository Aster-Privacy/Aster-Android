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

package org.astermail.android.imports

import org.astermail.android.crypto.CryptoNative
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ExternalAccountCryptoTest {

    private val account = ExternalAccountData(
        email = "sender@example.com",
        display_name = "Sender",
        created_at = "2026-01-01T00:00:00Z",
    )

    @Test
    fun legacy_storage_kdf_matches_independent_fixture() {
        assert_storage_key(1, "6941e1204da1169f6e960b9a8c521bc10d59b6a28f06af6e199b6d40501deb73")
    }

    @Test
    fun stretched_storage_kdf_matches_independent_fixture() {
        assert_storage_key(2, "b691432b3e18d28c82ec5931f58e11c087097e79ac75135a87a80aefa11ebf7d")
    }

    @Test
    fun decrypts_storage_key_records() {
        val key = ByteArray(32) { 7 }
        val payload = encrypt_account_data(account, key)

        val opened = decrypt_account_data_with_keys(
            payload.encrypted_account_data, payload.account_data_nonce, payload.integrity_hash,
            listOf(key),
        )

        assertEquals(account.email, opened?.email)
    }

    @Test
    fun reads_previous_android_records_using_legacy_key() {
        val storage_key = ByteArray(32) { 7 }
        val old_login_key = ByteArray(32) { 9 }
        val payload = encrypt_account_data(account, old_login_key)

        val opened = decrypt_account_data_with_keys(
            payload.encrypted_account_data, payload.account_data_nonce, payload.integrity_hash,
            listOf(storage_key, old_login_key),
        )

        assertEquals(account.email, opened?.email)
    }

    @Test
    fun rejects_tampered_integrity_hash_even_with_fallbacks() {
        val key = ByteArray(32) { 7 }
        val payload = encrypt_account_data(account, key)

        assertNull(decrypt_account_data_with_keys(
            payload.encrypted_account_data, payload.account_data_nonce, "invalid",
            listOf(ByteArray(32) { 9 }, key),
        ))
    }

    @Test
    fun upgraded_session_reads_v2_metadata_from_cached_vault() {
        val passphrase = "fixture-password".toByteArray()
        val envelope = CryptoNative.encrypt_vault_with_password("{\"kdf_version\":2,\"identity_key\":\"already-present\"}".toByteArray(), passphrase)
        val encoder = java.util.Base64.getEncoder()
        val vault = encoder.encodeToString(envelope.encrypted_vault) to encoder.encodeToString(envelope.vault_nonce)

        assertEquals(2, external_account_kdf_version(null, vault, passphrase))
    }

    @Test
    fun vault_without_kdf_metadata_uses_legacy_kdf() {
        val passphrase = "fixture-password".toByteArray()
        val envelope = CryptoNative.encrypt_vault_with_password("{}".toByteArray(), passphrase)
        val encoder = java.util.Base64.getEncoder()
        val vault = encoder.encodeToString(envelope.encrypted_vault) to encoder.encodeToString(envelope.vault_nonce)

        assertEquals(1, external_account_kdf_version(null, vault, passphrase))
    }

    @Test
    fun cached_kdf_version_does_not_require_opening_vault() {
        assertEquals(2, external_account_kdf_version(2, null, ByteArray(0)))
    }

    @Test
    fun missing_metadata_without_cached_vault_does_not_guess_a_write_key() {
        assertNull(external_account_kdf_version(null, null, "fixture-password".toByteArray()))
    }

    @Test
    fun unreadable_cached_vault_does_not_guess_a_write_key() {
        assertNull(external_account_kdf_version(null, "invalid" to "invalid", "fixture-password".toByteArray()))
    }

    private fun assert_storage_key(version: Int, expected_hex: String) {
        val key = external_account_master_key(null, { "fixture-password".toByteArray() }) {
            CryptoNative.derive_storage_key_for_version(it, version)
        }
        val expected = expected_hex.chunked(2).map { it.toInt(16).toByte() }.toByteArray()

        assertArrayEquals(expected, key)
    }
}
