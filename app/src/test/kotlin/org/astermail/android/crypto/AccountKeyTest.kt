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

import java.util.Base64
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Test

class AccountKeyTest {

    private val passphrase = "correct horse battery staple".toCharArray()

    private val user_key by lazy { PgpKeyGenerator.generate("User", "user@astermail.org", passphrase) }
    private val previous_user_key by lazy { PgpKeyGenerator.generate("User", "user@astermail.org", passphrase) }
    private val attacker_key by lazy { PgpKeyGenerator.generate("Attacker", "attacker@example.com", passphrase) }

    private val account_key = ByteArray(32) { it.toByte() }

    @Test
    fun context_keys_match_web_vectors() {
        assertEquals(
            "689560f8c35dae5940ef30ae2f047099b93e4c6c846ac1075fe7e50c7b085aa5",
            hex(AccountKey.derive_context_key(account_key, "astermail-preferences-v1")),
        )
        assertEquals(
            "adb0a69ec8c6548233dd706fa786375d72820aa2c66574bcd478183a0f705a63",
            hex(AccountKey.derive_context_key(account_key, "astermail-draft-v2")),
        )
    }

    @Test
    fun derives_one_kek_per_context() {
        val keks = AccountKey.derive_context_keks(account_key)
        assertEquals(AccountKey.DATA_CONTEXTS.size, keks.size)
        assertEquals(keks.size, keks.distinct().size)
        keks.forEach { assertEquals(32, Base64.getDecoder().decode(it).size) }
        assertEquals(
            "689560f8c35dae5940ef30ae2f047099b93e4c6c846ac1075fe7e50c7b085aa5",
            hex(Base64.getDecoder().decode(keks[AccountKey.DATA_CONTEXTS.indexOf("astermail-preferences-v1")])),
        )
    }

    @Test
    fun parses_valid_payload() {
        val parsed = AccountKey.parse_token_payload(payload(), emptyList())
        assertArrayEquals(account_key, parsed)
    }

    @Test
    fun rejects_malformed_payloads() {
        val key_b64 = Base64.getEncoder().encodeToString(account_key)
        val short_b64 = Base64.getEncoder().encodeToString(ByteArray(31))
        val bad = listOf(
            "",
            "not json",
            "[]",
            """{"type":"aster-account-key","version":1}""",
            """{"type":"other","version":1,"key":"$key_b64"}""",
            """{"type":"aster-account-key","version":"1","key":"$key_b64"}""",
            """{"type":"aster-account-key","version":2,"key":"$key_b64"}""",
            """{"type":"aster-account-key","version":2,"key":"$key_b64","owner":"$owner"}""",
            """{"type":"aster-account-key","version":2,"key":"$key_b64","owner":"$owner","serial":0}""",
            """{"type":"aster-account-key","version":2,"key":"$key_b64","owner":"$owner","serial":1.5}""",
            """{"type":"aster-account-key","version":2,"key":"$key_b64","owner":"$owner","serial":"1"}""",
            """{"type":"aster-account-key","version":2,"key":"$key_b64","owner":"${"cd".repeat(20)}","serial":1}""",
            """{"type":"aster-account-key","version":2,"key":"$key_b64","owner":"${owner.uppercase()}","serial":1}""",
            """{"type":"aster-account-key","version":3,"key":"$key_b64","owner":"$owner","serial":1}""",
            """{"type":"aster-account-key","version":1,"key":"$short_b64"}""",
            """{"type":"aster-account-key","version":1,"key":"***"}""",
            """{"type":"aster-account-key","version":1,"key":32}""",
        )
        bad.forEach { assertNull(it, AccountKey.parse_token_payload(it, listOf(owner))) }
    }

    @Test
    fun opens_token_signed_and_encrypted_by_own_key() {
        val token = sign_and_encrypt(payload(), user_key.armored_private_key, user_key.armored_public_key)
        val opened = AccountKey.open_token(token, listOf(user_key.armored_private_key), passphrase)
        assertArrayEquals(account_key, opened)
    }

    @Test
    fun opens_token_from_previous_key() {
        val token = sign_and_encrypt(
            payload(),
            previous_user_key.armored_private_key,
            previous_user_key.armored_public_key,
        )
        val opened = AccountKey.open_token(
            token,
            listOf(user_key.armored_private_key, previous_user_key.armored_private_key),
            passphrase,
        )
        assertArrayEquals(account_key, opened)
    }

    @Test
    fun rejects_token_signed_by_foreign_key() {
        val token = sign_and_encrypt(payload(), attacker_key.armored_private_key, user_key.armored_public_key)
        assertNull(AccountKey.open_token(token, listOf(user_key.armored_private_key), passphrase))
    }

    @Test
    fun rejects_unsigned_token() {
        val token = PgpEncryptor.encrypt_to_keys(payload(), listOf(user_key.armored_public_key))!!
        assertNull(AccountKey.open_token(token, listOf(user_key.armored_private_key), passphrase))
    }

    @Test
    fun rejects_token_from_another_account() {
        val token = sign_and_encrypt(payload(), attacker_key.armored_private_key, attacker_key.armored_public_key)
        assertNull(AccountKey.open_token(token, listOf(user_key.armored_private_key), passphrase))
    }

    @Test
    fun rejects_token_with_wrong_passphrase() {
        val token = sign_and_encrypt(payload(), user_key.armored_private_key, user_key.armored_public_key)
        assertNull(AccountKey.open_token(token, listOf(user_key.armored_private_key), "wrong".toCharArray()))
    }

    @Test
    fun rejects_signed_token_with_bad_payload() {
        val token = sign_and_encrypt(
            """{"type":"aster-account-key","version":1,"key":"AAAA"}""",
            user_key.armored_private_key,
            user_key.armored_public_key,
        )
        assertNull(AccountKey.open_token(token, listOf(user_key.armored_private_key), passphrase))
    }

    @Test
    fun rejects_when_no_private_keys() {
        val token = sign_and_encrypt(payload(), user_key.armored_private_key, user_key.armored_public_key)
        assertNull(AccountKey.open_token(token, emptyList(), passphrase))
        assertNull(AccountKey.open_token(token, listOf("not a key"), passphrase))
    }

    private val owner = "ab".repeat(20)

    @Test
    fun parses_version_two_payload_for_known_owner() {
        val built = AccountKey.build_token_payload(account_key, owner.uppercase(), 4)
        assertArrayEquals(account_key, AccountKey.parse_token_payload(built, listOf(owner.uppercase())))
        assertNull(AccountKey.parse_token_payload(built, emptyList()))
        assertThrows(IllegalArgumentException::class.java) { AccountKey.build_token_payload(account_key, "xyz", 1) }
        assertThrows(IllegalArgumentException::class.java) { AccountKey.build_token_payload(account_key, owner, 0) }
    }

    @Test
    fun sealed_token_names_its_owner_and_serial() {
        val token = AccountKey.seal_token(account_key, user_key.armored_private_key, passphrase, 3)!!
        val plaintext = PgpDecryptor.decrypt_signed_by_own_keys(
            token,
            listOf(user_key.armored_private_key),
            passphrase,
        )!!
        val fingerprint = AccountKey.primary_fingerprint(user_key.armored_private_key)!!
        assertEquals(true, plaintext.contains("\"owner\":\"$fingerprint\""))
        assertEquals(true, plaintext.contains("\"serial\":3"))
        assertEquals(true, plaintext.contains("\"version\":2"))
        assertArrayEquals(account_key, AccountKey.open_token(token, listOf(user_key.armored_private_key), passphrase))
    }

    @Test
    fun rejects_token_naming_another_owner() {
        val attacker_fingerprint = AccountKey.primary_fingerprint(attacker_key.armored_private_key)!!
        val token = sign_and_encrypt(
            AccountKey.build_token_payload(account_key, attacker_fingerprint, 1),
            user_key.armored_private_key,
            user_key.armored_public_key,
        )
        assertNull(AccountKey.open_token(token, listOf(user_key.armored_private_key), passphrase))
    }

    private fun payload(): String =
        """{"type":"aster-account-key","version":1,"key":"${Base64.getEncoder().encodeToString(account_key)}"}"""

    private fun hex(bytes: ByteArray): String = bytes.joinToString("") { "%02x".format(it) }

    private fun sign_and_encrypt(plaintext: String, signer_private: String, recipient_public: String): String =
        AccountKeyTestTokens.sign_and_encrypt(plaintext, signer_private, recipient_public, passphrase)
}
