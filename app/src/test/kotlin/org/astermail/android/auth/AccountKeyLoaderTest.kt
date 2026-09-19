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

import java.util.Base64
import kotlinx.coroutines.runBlocking
import org.astermail.android.api.keys.AccountKeyTokenHistoryEntry
import org.astermail.android.api.keys.AccountKeyTokenResponse
import org.astermail.android.api.keys.CurrentVaultResult
import org.astermail.android.api.keys.ExternalKeyInfo
import org.astermail.android.api.keys.KeysApi
import org.astermail.android.api.keys.PublicKeyResponse
import org.astermail.android.crypto.AccountKey
import org.astermail.android.crypto.AccountKeyTestTokens
import org.astermail.android.crypto.PgpKeyGenerator
import org.astermail.android.storage.SessionKeyStore
import org.astermail.android.crypto.PgpDecryptor
import org.astermail.android.crypto.PgpEncryptor
import org.astermail.android.crypto.PgpSignatureStatus
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class AccountKeyLoaderTest {

    private val passphrase = "correct horse battery staple"
    private val passphrase_chars = passphrase.toCharArray()

    private val user_key by lazy { PgpKeyGenerator.generate("User", "user@astermail.org", passphrase_chars) }
    private val previous_user_key by lazy { PgpKeyGenerator.generate("User", "user@astermail.org", passphrase_chars) }
    private val attacker_key by lazy { PgpKeyGenerator.generate("Attacker", "attacker@example.com", passphrase_chars) }

    private val current_key = ByteArray(32) { it.toByte() }
    private val old_key = ByteArray(32) { (it + 100).toByte() }

    @Test
    fun loads_current_and_history_keks() = runBlocking {
        val store = signed_in_store(previous = listOf(previous_user_key.armored_private_key))
        val api = FakeKeysApi(
            current = token(current_key, user_key.armored_private_key, user_key.armored_public_key),
            history = listOf(
                token(old_key, previous_user_key.armored_private_key, previous_user_key.armored_public_key),
            ),
        )

        assertEquals(2, AccountKeyLoader(api, store).load())

        val expected = AccountKey.derive_context_keks(current_key) + AccountKey.derive_context_keks(old_key)
        assertEquals(expected, store.get_account_keks())
        assertTrue(store.get_decrypt_keks().containsAll(expected))
    }

    @Test
    fun keeps_legacy_keks_separate() = runBlocking {
        val store = signed_in_store()
        val legacy = Base64.getEncoder().encodeToString(ByteArray(32) { 7 })
        store.put_legacy_keks(listOf(legacy))
        val api = FakeKeysApi(current = token(current_key, user_key.armored_private_key, user_key.armored_public_key))

        AccountKeyLoader(api, store).load()

        assertEquals(listOf(legacy), store.get_legacy_keks())
        assertEquals(listOf(legacy) + AccountKey.derive_context_keks(current_key), store.get_decrypt_keks())
    }

    @Test
    fun ignores_forged_history_entries() = runBlocking {
        val store = signed_in_store()
        val api = FakeKeysApi(
            current = token(current_key, user_key.armored_private_key, user_key.armored_public_key),
            history = listOf(token(old_key, attacker_key.armored_private_key, user_key.armored_public_key)),
        )

        assertEquals(1, AccountKeyLoader(api, store).load())
        assertEquals(AccountKey.derive_context_keks(current_key), store.get_account_keks())
    }

    @Test
    fun never_wipes_existing_keks_when_nothing_opens() = runBlocking {
        val store = signed_in_store()
        val existing = AccountKey.derive_context_keks(old_key)
        store.put_account_keks(existing, store.account_kek_generation())
        val api = FakeKeysApi(
            current = token(current_key, attacker_key.armored_private_key, user_key.armored_public_key),
        )

        assertEquals(0, AccountKeyLoader(api, store).load())
        assertEquals(existing, store.get_account_keks())
        assertTrue(api.puts.isEmpty())
    }

    @Test
    fun creates_signed_token_when_account_has_none() = runBlocking {
        val store = signed_in_store()
        val api = FakeKeysApi(current = null, history_route = true)

        assertEquals(1, AccountKeyLoader(api, store).load())
        assertEquals(1, api.puts.size)

        val (token, fingerprint) = api.puts.single()
        assertEquals(AccountKey.primary_fingerprint(user_key.armored_private_key), fingerprint)
        assertTrue(fingerprint.matches(Regex("^[0-9a-f]{40}([0-9a-f]{24})?$")))
        val opened = AccountKey.open_token(token, listOf(user_key.armored_private_key), passphrase_chars)
        assertNotNull(opened)
        assertEquals(AccountKey.LENGTH, opened!!.size)
        assertTrue(opened.any { it != 0.toByte() })
        assertEquals(AccountKey.derive_context_keks(opened), store.get_account_keks())
    }

    @Test
    fun loads_stored_token_when_another_device_created_one_first() = runBlocking {
        val store = signed_in_store()
        val winner = token(old_key, user_key.armored_private_key, user_key.armored_public_key)
        val api = FakeKeysApi(
            current = null,
            history_route = true,
            put_result = { _, _ -> AccountKeyTokenResponse(winner, "fp", 1L, null) },
        )

        assertEquals(1, AccountKeyLoader(api, store).load())
        assertEquals(AccountKey.derive_context_keks(old_key), store.get_account_keks())
    }

    @Test
    fun creates_nothing_when_server_lacks_the_routes() = runBlocking {
        val store = signed_in_store()
        val api = FakeKeysApi(current = null, history_route = false)

        assertEquals(0, AccountKeyLoader(api, store).load())
        assertTrue(api.puts.isEmpty())
    }

    @Test
    fun creates_nothing_when_older_tokens_exist() = runBlocking {
        val store = signed_in_store()
        val api = FakeKeysApi(
            current = null,
            history = listOf(token(old_key, user_key.armored_private_key, user_key.armored_public_key)),
            history_route = true,
        )

        assertEquals(0, AccountKeyLoader(api, store).load())
        assertTrue(api.puts.isEmpty())
    }

    @Test
    fun uploads_nothing_when_passphrase_is_wrong() = runBlocking {
        val store = SessionKeyStore(null)
        store.put_identity_key(user_key.armored_private_key)
        store.put_passphrase("wrong".toByteArray(Charsets.UTF_8))
        val api = FakeKeysApi(current = null, history_route = true)

        assertEquals(0, AccountKeyLoader(api, store).load())
        assertTrue(api.puts.isEmpty())
    }

    @Test
    fun stays_quiet_when_server_refuses_the_token() = runBlocking {
        val store = signed_in_store()
        val api = FakeKeysApi(current = null, history_route = true, put_result = { _, _ -> null })

        assertEquals(0, AccountKeyLoader(api, store).load())
        assertEquals(1, api.puts.size)
        assertNull(store.get_account_keks())
    }

    @Test
    fun encrypt_and_sign_verifies_as_own_signature() {
        val sealed = PgpEncryptor.encrypt_and_sign(
            "hello",
            listOf(user_key.armored_public_key),
            user_key.armored_private_key,
            passphrase_chars,
        )
        assertNotNull(sealed)
        val result = PgpDecryptor.decrypt_with_status(
            sealed!!,
            user_key.armored_private_key,
            passphrase_chars,
            user_key.armored_public_key,
        )
        assertEquals("hello", result.plaintext)
        assertEquals(PgpSignatureStatus.VALID, result.signature)
        assertNull(
            PgpEncryptor.encrypt_and_sign(
                "hello",
                listOf(user_key.armored_public_key),
                user_key.armored_private_key,
                "wrong".toCharArray(),
            ),
        )
    }

    @Test
    fun built_payload_parses_back() {
        val parsed = AccountKey.parse_token_payload(AccountKey.build_token_payload(current_key))
        assertArrayEquals(current_key, parsed)
    }

    @Test
    fun does_nothing_without_token() = runBlocking {
        val store = signed_in_store()
        assertEquals(0, AccountKeyLoader(FakeKeysApi(current = null), store).load())
        assertNull(store.get_account_keks())
    }

    @Test
    fun does_nothing_without_session_keys() = runBlocking {
        val store = SessionKeyStore(null)
        val api = FakeKeysApi(current = token(current_key, user_key.armored_private_key, user_key.armored_public_key))
        assertEquals(0, AccountKeyLoader(api, store).load())
        assertNull(store.get_account_keks())
    }

    @Test
    fun drops_result_when_session_cleared_during_fetch() = runBlocking {
        val store = signed_in_store()
        val api = FakeKeysApi(
            current = token(current_key, user_key.armored_private_key, user_key.armored_public_key),
            on_fetch = { store.clear() },
        )

        assertEquals(0, AccountKeyLoader(api, store).load())
        assertNull(store.get_account_keks())
    }

    @Test
    fun clear_removes_account_keks() = runBlocking {
        val store = signed_in_store()
        val api = FakeKeysApi(current = token(current_key, user_key.armored_private_key, user_key.armored_public_key))
        AccountKeyLoader(api, store).load()

        store.clear()

        assertNull(store.get_account_keks())
        assertEquals(emptyList<String>(), store.get_decrypt_keks())
    }

    private fun signed_in_store(previous: List<String> = emptyList()): SessionKeyStore {
        val store = SessionKeyStore(null)
        store.put_identity_key(user_key.armored_private_key)
        store.put_passphrase(passphrase.toByteArray(Charsets.UTF_8))
        if (previous.isNotEmpty()) store.put_previous_keys(previous)
        return store
    }

    private fun token(key: ByteArray, signer_private: String, recipient_public: String): String {
        val payload =
            """{"type":"aster-account-key","version":1,"key":"${Base64.getEncoder().encodeToString(key)}"}"""
        return AccountKeyTestTokens.sign_and_encrypt(payload, signer_private, recipient_public, passphrase_chars)
    }

    private class FakeKeysApi(
        private val current: String?,
        private val history: List<String> = emptyList(),
        private val on_fetch: () -> Unit = {},
        private val history_route: Boolean = false,
        private val put_result: (String, String) -> AccountKeyTokenResponse? = { token, fingerprint ->
            AccountKeyTokenResponse(token, fingerprint, 1L, null)
        },
    ) : KeysApi {
        val puts = mutableListOf<Pair<String, String>>()

        override suspend fun get_account_key_token_history_or_null(): List<AccountKeyTokenHistoryEntry>? =
            if (history_route) get_account_key_token_history() else null

        override suspend fun put_account_key_token_if_absent(
            token: String,
            key_fingerprint: String,
        ): AccountKeyTokenResponse? {
            puts.add(token to key_fingerprint)
            return put_result(token, key_fingerprint)
        }

        override suspend fun get_account_key_token(): AccountKeyTokenResponse? {
            on_fetch()
            return current?.let { AccountKeyTokenResponse(it, "fp", 1L, null) }
        }

        override suspend fun get_account_key_token_history(): List<AccountKeyTokenHistoryEntry> =
            history.map { AccountKeyTokenHistoryEntry(it, "fp", 0L, null) }

        override suspend fun get_recipient_public_key(username: String, email: String?): PublicKeyResponse =
            throw UnsupportedOperationException()

        override suspend fun discover_external_key(email: String): ExternalKeyInfo =
            throw UnsupportedOperationException()

        override suspend fun discover_external_keys_batch(emails: List<String>): List<ExternalKeyInfo> =
            throw UnsupportedOperationException()

        override suspend fun acknowledge_external_key_fingerprint_change(
            email: String,
            prior_fingerprint: String,
            new_fingerprint: String,
        ): Boolean = throw UnsupportedOperationException()

        override suspend fun update_vault(
            encrypted_vault: String,
            vault_nonce: String,
            expected_user_id: String?,
            vault_key_fingerprints: List<String>?,
        ): Boolean = throw UnsupportedOperationException()

        override suspend fun fetch_current_vault(): CurrentVaultResult = throw UnsupportedOperationException()
    }
}
