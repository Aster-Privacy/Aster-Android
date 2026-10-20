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

import java.io.ByteArrayOutputStream
import org.bouncycastle.bcpg.ArmoredOutputStream
import org.bouncycastle.openpgp.PGPPublicKey
import org.bouncycastle.openpgp.PGPPublicKeyRing
import org.bouncycastle.openpgp.PGPSecretKey
import org.bouncycastle.openpgp.PGPSecretKeyRing
import org.bouncycastle.openpgp.PGPUtil
import org.bouncycastle.openpgp.operator.bc.BcKeyFingerprintCalculator
import org.bouncycastle.openpgp.operator.bc.BcPBESecretKeyDecryptorBuilder
import org.bouncycastle.openpgp.operator.bc.BcPGPContentVerifierBuilderProvider
import org.bouncycastle.openpgp.operator.bc.BcPGPDigestCalculatorProvider
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class DottedPgpUserIdTest {

    private val passphrase = "correct horse battery staple"

    @Test
    fun identity_addresses_cover_both_forms_on_aster_domains() {
        assertEquals(
            listOf("murati.balazs@astermail.org", "muratibalazs@astermail.org"),
            identity_addresses_for("murati.balazs@astermail.org"),
        )
        assertEquals(listOf("a.b@aster.cx", "ab@aster.cx"), identity_addresses_for("a.b@aster.cx"))
        assertEquals(listOf("alice@astermail.org"), identity_addresses_for("alice@astermail.org"))
        assertEquals(
            listOf("first.last@example.com"),
            identity_addresses_for("first.last@example.com"),
        )
        assertEquals(emptyList<String>(), identity_addresses_for(" "))
    }

    @Test
    fun new_key_for_dotted_address_carries_both_with_dotted_primary() {
        val result = PgpKeyGenerator.generate(
            "murati.balazs",
            "murati.balazs@astermail.org",
            passphrase.toCharArray(),
        )

        val expected = listOf("murati.balazs@astermail.org", "muratibalazs@astermail.org")
        assertEquals(expected, pgp_key_user_emails(result.armored_public_key))
        assertEquals(expected, pgp_key_user_emails(result.armored_private_key))

        val public_master = read_public(result.armored_public_key)
        assertEquals("murati.balazs@astermail.org", primary_email(public_master))
        assert_all_certifications_verify(public_master)
        assertEquals(
            emptyList<String>(),
            account_addresses_missing_from_pgp_key(
                result.armored_public_key,
                "murati.balazs@astermail.org",
            ),
        )
    }

    @Test
    fun new_key_without_dots_keeps_one_user_id() {
        val result = PgpKeyGenerator.generate("Alice", "alice@astermail.org", passphrase.toCharArray())

        assertEquals(listOf("alice@astermail.org"), pgp_key_user_emails(result.armored_public_key))
        assert_all_certifications_verify(read_public(result.armored_public_key))
    }

    @Test
    fun heal_adds_dotted_primary_to_a_dotless_key_without_changing_the_fingerprint() {
        val legacy = PgpKeyGenerator.generate(
            "muratibalazs",
            "muratibalazs@astermail.org",
            passphrase.toCharArray(),
        )
        assertEquals(
            listOf("murati.balazs@astermail.org"),
            account_addresses_missing_from_pgp_key(
                legacy.armored_public_key,
                "murati.balazs@astermail.org",
            ),
        )

        val healed = add_account_addresses_to_pgp_key(
            legacy.armored_private_key,
            passphrase.toCharArray(),
            "Balazs",
            "murati.balazs@astermail.org",
        )
        assertNotNull(healed)

        val before = read_secret(legacy.armored_private_key)
        val after = read_secret(healed!!)
        assertArrayEquals(before.publicKey.fingerprint, after.publicKey.fingerprint)
        assertEquals(
            setOf("murati.balazs@astermail.org", "muratibalazs@astermail.org"),
            pgp_key_user_emails(healed).toSet(),
        )
        assertEquals("murati.balazs@astermail.org", primary_email(after.publicKey))
        assert_all_certifications_verify(after.publicKey)
        assertEquals(2, after.secretKeys.asSequence().count())

        val decryptor = BcPBESecretKeyDecryptorBuilder(BcPGPDigestCalculatorProvider())
            .build(passphrase.toCharArray())
        after.secretKeys.forEach { assertNotNull(it.extractPrivateKey(decryptor)) }

        assertNull(
            add_account_addresses_to_pgp_key(
                healed,
                passphrase.toCharArray(),
                "Balazs",
                "murati.balazs@astermail.org",
            ),
        )
    }

    @Test
    fun heal_appends_dotless_form_after_a_dotted_primary() {
        val key = PgpKeyGenerator.generate("John", "john.smith@astermail.org", passphrase.toCharArray())
        val single = strip_to_first_user_id(key.armored_private_key)
        assertEquals(listOf("john.smith@astermail.org"), pgp_key_user_emails(single))

        val healed = add_account_addresses_to_pgp_key(
            single,
            passphrase.toCharArray(),
            "John",
            "john.smith@astermail.org",
        )

        assertNotNull(healed)
        assertEquals(
            listOf("john.smith@astermail.org", "johnsmith@astermail.org"),
            pgp_key_user_emails(healed!!),
        )
        assertEquals("john.smith@astermail.org", primary_email(read_secret(healed).publicKey))
        assert_all_certifications_verify(read_secret(healed).publicKey)
    }

    @Test
    fun heal_leaves_undotted_and_custom_domain_accounts_alone() {
        val key = PgpKeyGenerator.generate("Alice", "alice@astermail.org", passphrase.toCharArray())

        assertNull(
            add_account_addresses_to_pgp_key(
                key.armored_private_key,
                passphrase.toCharArray(),
                "Alice",
                "alice@astermail.org",
            ),
        )
        assertNull(
            add_account_addresses_to_pgp_key(
                key.armored_private_key,
                passphrase.toCharArray(),
                "Jo",
                "first.last@example.com",
            ),
        )
    }

    @Test
    fun existing_add_address_still_marks_the_new_address_primary() {
        val key = PgpKeyGenerator.generate("Alice", "alice@astermail.org", passphrase.toCharArray())

        val updated = add_address_to_pgp_key(
            key.armored_private_key,
            passphrase.toCharArray(),
            "Alice",
            "alias@astermail.org",
        )

        assertNotNull(updated)
        assertEquals("alias@astermail.org", primary_email(read_secret(updated!!).publicKey))
        assert_all_certifications_verify(read_secret(updated).publicKey)
    }

    @Test
    fun user_emails_tolerate_garbage() {
        assertEquals(emptyList<String>(), pgp_key_user_emails("not a key"))
    }

    private fun strip_to_first_user_id(armored_private_key: String): String {
        val ring = read_secret(armored_private_key)
        val master = ring.secretKey
        var public_key = master.publicKey
        public_key.userIDs.asSequence().toList().drop(1).forEach {
            public_key = PGPPublicKey.removeCertification(public_key, it)
        }
        val updated = PGPSecretKeyRing.insertSecretKey(
            ring,
            PGPSecretKey.replacePublicKey(master, public_key),
        )
        val out = ByteArrayOutputStream()
        ArmoredOutputStream(out).use { updated.encode(it) }
        return out.toString(Charsets.UTF_8.name())
    }

    private fun primary_email(master: PGPPublicKey): String {
        val primaries = master.userIDs.asSequence().filter { user_id ->
            master.getSignaturesForID(user_id).asSequence()
                .sortedBy { it.creationTime }
                .lastOrNull()
                ?.hashedSubPackets
                ?.isPrimaryUserID == true
        }.toList()
        assertEquals(1, primaries.size)
        return primaries.first().substringAfterLast('<').substringBefore('>').lowercase()
    }

    private fun assert_all_certifications_verify(master: PGPPublicKey) {
        master.userIDs.forEach { user_id ->
            val signatures = master.getSignaturesForID(user_id).asSequence().toList()
            assertTrue(signatures.isNotEmpty())
            signatures.forEach { signature ->
                signature.init(BcPGPContentVerifierBuilderProvider(), master)
                assertTrue(signature.verifyCertification(user_id, master))
            }
        }
    }

    private fun read_public(armored: String): PGPPublicKey =
        PGPPublicKeyRing(
            PGPUtil.getDecoderStream(armored.byteInputStream()),
            BcKeyFingerprintCalculator(),
        ).publicKey

    private fun read_secret(armored: String): PGPSecretKeyRing =
        PGPSecretKeyRing(
            PGPUtil.getDecoderStream(armored.byteInputStream()),
            BcKeyFingerprintCalculator(),
        )
}
