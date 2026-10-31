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

package org.astermail.android.mail.ratchet

import kotlinx.coroutines.test.runTest
import org.astermail.android.crypto.PgpDecryptor
import org.astermail.android.crypto.PgpEncryptor
import org.astermail.android.crypto.PgpKeyGenerator
import org.astermail.android.crypto.PgpSignatureStatus
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PrekeyBundlePolicyTest {

    private val passphrase = "pgp-fallback-fixture"
    private val recipient_email = "kchaos@aster.cx"
    private val sender_email = "hello@astermail.org"

    private val recipient_key by lazy { PgpKeyGenerator.generate("Recipient", recipient_email, passphrase.toCharArray()) }
    private val sender_key by lazy { PgpKeyGenerator.generate("Sender", sender_email, passphrase.toCharArray()) }

    private val pinned = "ABCDEF0123456789"

    @Test
    fun `switch defaults to current behavior`() {
        assertFalse(PrekeyBundlePolicy.require_verified_prekey_bundle)
    }

    @Test
    fun `switch off never asks for a fallback`() {
        for (result in PrekeyBindingResult.values()) {
            assertFalse(prekey_bundle_needs_pgp_fallback(false, result, null, null))
            assertFalse(prekey_bundle_needs_pgp_fallback(false, result, pinned, pinned))
            assertFalse(prekey_bundle_needs_pgp_fallback(false, result, pinned, "other"))
        }
    }

    @Test
    fun `strict mode falls back only for bundles that cannot be verified`() {
        assertTrue(prekey_bundle_needs_pgp_fallback(true, PrekeyBindingResult.UNSIGNED_LEGACY, null, null))
        assertTrue(prekey_bundle_needs_pgp_fallback(true, PrekeyBindingResult.UNVERIFIABLE, null, null))
        assertTrue(prekey_bundle_needs_pgp_fallback(true, PrekeyBindingResult.UNVERIFIABLE, null, "served"))
        assertFalse(prekey_bundle_needs_pgp_fallback(true, PrekeyBindingResult.VERIFIED, null, "served"))
        assertFalse(prekey_bundle_needs_pgp_fallback(true, PrekeyBindingResult.INVALID, null, "served"))
    }

    @Test
    fun `strict mode with a pinned owner falls back only when the served key matches the pin`() {
        assertTrue(prekey_bundle_needs_pgp_fallback(true, PrekeyBindingResult.UNSIGNED_LEGACY, pinned, pinned.lowercase()))
        assertTrue(prekey_bundle_needs_pgp_fallback(true, PrekeyBindingResult.UNVERIFIABLE, pinned, pinned))
        assertFalse(prekey_bundle_needs_pgp_fallback(true, PrekeyBindingResult.UNSIGNED_LEGACY, pinned, "other"))
        assertFalse(prekey_bundle_needs_pgp_fallback(true, PrekeyBindingResult.UNSIGNED_LEGACY, pinned, null))
        assertFalse(prekey_bundle_needs_pgp_fallback(true, PrekeyBindingResult.VERIFIED, pinned, pinned))
    }

    @Test
    fun `owner key is trusted without a pin or when it matches the pin`() {
        assertTrue(owner_key_trusted(null, null))
        assertTrue(owner_key_trusted("", "served"))
        assertTrue(owner_key_trusted(pinned, pinned.lowercase()))
        assertFalse(owner_key_trusted(pinned, "other"))
        assertFalse(owner_key_trusted(pinned, null))
    }

    @Test
    fun `ratchet result is used when the bundle is accepted`() = runTest {
        var pgp_calls = 0
        val sealed = seal_internal_body(
            recipients = listOf(recipient_email),
            allow_non_post_quantum = false,
            seal_ratchet = { "ratchet" },
            seal_pgp = {
                pgp_calls++
                "pgp"
            },
        )
        assertEquals("ratchet", sealed)
        assertEquals(0, pgp_calls)
    }

    @Test
    fun `unverified bundle seals with pgp when non post quantum is allowed`() = runTest {
        val sealed = seal_internal_body(
            recipients = listOf(recipient_email),
            allow_non_post_quantum = true,
            seal_ratchet = { throw UnverifiedPrekeyBundleException(recipient_email) },
            seal_pgp = { "pgp" },
        )
        assertEquals("pgp", sealed)
    }

    @Test
    fun `unverified bundle asks for the post quantum confirmation first`() = runTest {
        var pgp_calls = 0
        val thrown = runCatching {
            seal_internal_body(
                recipients = listOf(recipient_email),
                allow_non_post_quantum = false,
                seal_ratchet = { throw UnverifiedPrekeyBundleException(recipient_email) },
                seal_pgp = {
                    pgp_calls++
                    "pgp"
                },
            )
        }.exceptionOrNull()
        assertTrue(thrown is PostQuantumUnavailableException)
        assertEquals(listOf(recipient_email), (thrown as PostQuantumUnavailableException).recipients)
        assertEquals(0, pgp_calls)
    }

    @Test
    fun `other ratchet failures are not turned into pgp`() = runTest {
        var pgp_calls = 0
        val failures = listOf(
            RecipientKeyUntrustedException(recipient_email, "untrusted"),
            RatchetEncryptionException(recipient_email, "failed"),
            PostQuantumUnavailableException(listOf(recipient_email)),
        )
        for (failure in failures) {
            val thrown = runCatching {
                seal_internal_body(
                    recipients = listOf(recipient_email),
                    allow_non_post_quantum = true,
                    seal_ratchet = { throw failure },
                    seal_pgp = {
                        pgp_calls++
                        "pgp"
                    },
                )
            }.exceptionOrNull()
            assertTrue(thrown === failure)
        }
        assertEquals(0, pgp_calls)
    }

    private suspend fun seal_with_pgp(
        recipients: List<String>,
        keys: Map<String, String?>,
        trusted: (String, String) -> Boolean = { _, _ -> true },
        own_key: String? = sender_key.armored_public_key,
    ): String = seal_pgp_for_recipients(
        recipients = recipients,
        own_public_key = own_key,
        fetch_public_key = { keys[it] },
        is_owner_key_trusted = trusted,
        can_encrypt_to = PgpEncryptor::can_encrypt_to,
        encrypt_and_sign = { list ->
            PgpEncryptor.encrypt_and_sign("wrapped body", list, sender_key.armored_private_key, passphrase.toCharArray())
        },
    )

    @Test
    fun `pgp fallback is readable by the recipient and the sender`() = runTest {
        val armored = seal_with_pgp(listOf(recipient_email), mapOf(recipient_email to recipient_key.armored_public_key))

        assertTrue(armored.startsWith("-----BEGIN PGP"))
        val for_recipient = PgpDecryptor.decrypt_with_status(
            armored,
            recipient_key.armored_private_key,
            passphrase.toCharArray(),
            sender_key.armored_public_key,
        )
        assertEquals("wrapped body", for_recipient.plaintext)
        assertEquals(PgpSignatureStatus.VALID, for_recipient.signature)
        assertEquals(
            "wrapped body",
            PgpDecryptor.decrypt(armored, sender_key.armored_private_key, passphrase.toCharArray()),
        )
    }

    @Test
    fun `recipient without a key fails with a missing key error`() = runTest {
        var encrypted = false
        val thrown = runCatching {
            seal_pgp_for_recipients(
                recipients = listOf(recipient_email, "nokey@aster.cx"),
                own_public_key = sender_key.armored_public_key,
                fetch_public_key = { if (it == recipient_email) recipient_key.armored_public_key else null },
                is_owner_key_trusted = { _, _ -> true },
                can_encrypt_to = PgpEncryptor::can_encrypt_to,
                encrypt_and_sign = {
                    encrypted = true
                    "unused"
                },
            )
        }.exceptionOrNull()
        assertTrue(thrown is RecipientPublicKeyMissingException)
        assertEquals(listOf("nokey@aster.cx"), (thrown as RecipientPublicKeyMissingException).recipients)
        assertFalse(encrypted)
    }

    @Test
    fun `key that cannot encrypt counts as missing`() = runTest {
        val thrown = runCatching {
            seal_with_pgp(listOf(recipient_email), mapOf(recipient_email to "not a key"))
        }.exceptionOrNull()
        assertTrue(thrown is RecipientPublicKeyMissingException)
    }

    @Test
    fun `key that does not match the pinned owner is refused`() = runTest {
        val thrown = runCatching {
            seal_with_pgp(
                listOf(recipient_email),
                mapOf(recipient_email to recipient_key.armored_public_key),
                trusted = { _, _ -> false },
            )
        }.exceptionOrNull()
        assertTrue(thrown is RecipientKeyUntrustedException)
    }

    @Test
    fun `missing sender key fails instead of sending an unreadable copy`() = runTest {
        val thrown = runCatching {
            seal_with_pgp(
                listOf(recipient_email),
                mapOf(recipient_email to recipient_key.armored_public_key),
                own_key = null,
            )
        }.exceptionOrNull()
        assertTrue(thrown is PgpFallbackSealException)
    }
}
