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

package org.astermail.android.mail

import org.astermail.android.crypto.ClientPgpCopies
import org.astermail.android.crypto.PgpDecryptor
import org.astermail.android.crypto.PgpKeyGenerator
import org.astermail.android.crypto.PgpKeyPairResult
import org.astermail.android.crypto.PgpSigner
import org.astermail.android.crypto.ProtectedMimeBuilder
import org.astermail.android.crypto.ProtectedMimeInput
import org.bouncycastle.openpgp.PGPEncryptedDataList
import org.bouncycastle.openpgp.PGPPublicKeyEncryptedData
import org.bouncycastle.openpgp.PGPPublicKeyRing
import org.bouncycastle.openpgp.PGPUtil
import org.bouncycastle.openpgp.jcajce.JcaPGPObjectFactory
import org.bouncycastle.openpgp.operator.jcajce.JcaKeyFingerprintCalculator
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AliasPgpCopyTest {

    private val primary = "owner@astermail.org"
    private val alias = "quiet.fox@aster.cx"
    private val recipient_address = "pal@example.org"
    private val passphrase = "correct horse battery staple"
    private val sender = PgpKeyGenerator.generate("Owner", primary, passphrase.toCharArray())
    private val recipient = PgpKeyGenerator.generate("Pal", recipient_address, passphrase.toCharArray())

    private fun outbound_copy(from: String, alias_pgp_enabled: Boolean): String {
        val mime = ProtectedMimeBuilder.build(
            ProtectedMimeInput(
                subject = "Hello",
                body = "<p>See you soon.</p>",
                is_html = true,
                from = from,
                to = listOf(recipient_address),
            ),
        ).toByteArray(Charsets.UTF_8)
        val sign_copy = MailRepository.copy_may_carry_signature(from, primary, alias_pgp_enabled)
        val signature = if (sign_copy) {
            PgpSigner.sign_detached(mime, sender.armored_private_key, passphrase.toCharArray())
        } else {
            null
        }
        val entity = signature?.let { ClientPgpCopies.build_signed_mime_entity(mime, it.signature, it.micalg) } ?: mime
        val copies = ClientPgpCopies.encrypt_copies(
            entity,
            linkedMapOf(recipient_address to recipient.armored_public_key),
            0,
        )
        return copies!!.getValue(recipient_address)
    }

    private fun ring(key: PgpKeyPairResult): PGPPublicKeyRing =
        PGPPublicKeyRing(
            PGPUtil.getDecoderStream(key.armored_public_key.byteInputStream()),
            JcaKeyFingerprintCalculator(),
        )

    private fun key_ids(key: PgpKeyPairResult): Set<String> =
        ring(key).publicKeys.asSequence().map { "%016x".format(it.keyID) }.toSet()

    private fun fingerprints(key: PgpKeyPairResult): Set<String> =
        ring(key).publicKeys.asSequence().map { it.fingerprint.joinToString("") { b -> "%02x".format(b) } }.toSet()

    private fun pkesk_ids(armored: String): Set<String> {
        val factory = JcaPGPObjectFactory(PGPUtil.getDecoderStream(armored.byteInputStream()))
        var item = factory.nextObject()
        while (item != null && item !is PGPEncryptedDataList) item = factory.nextObject()
        val list = item as PGPEncryptedDataList
        return list.encryptedDataObjects.asSequence()
            .filterIsInstance<PGPPublicKeyEncryptedData>()
            .map { "%016x".format(it.keyID) }
            .toSet()
    }

    private fun account_material_found(armored: String): List<String> {
        val found = mutableListOf<String>()
        if (pkesk_ids(armored).any { it in key_ids(sender) }) found.add("encrypted to an account key")
        val inner = PgpDecryptor.decrypt(armored, recipient.armored_private_key, passphrase.toCharArray()).orEmpty()
        val inner_lower = inner.lowercase()
        if (inner_lower.contains(primary) || armored.lowercase().contains(primary)) found.add("primary address")
        if (inner.contains("-----BEGIN PGP SIGNATURE-----") || inner_lower.contains("application/pgp-signature")) {
            found.add("account signature")
        }
        if (key_ids(sender).any { inner_lower.contains(it) }) found.add("key id")
        if (fingerprints(sender).any { inner_lower.contains(it) }) found.add("fingerprint")
        return found
    }

    @Test
    fun alias_copy_with_the_setting_off_carries_no_account_material() {
        val copy = outbound_copy(alias, alias_pgp_enabled = false)

        assertEquals(emptyList<String>(), account_material_found(copy))
        assertTrue(pkesk_ids(copy).all { it in key_ids(recipient) })
        val inner = PgpDecryptor.decrypt(copy, recipient.armored_private_key, passphrase.toCharArray()).orEmpty()
        assertTrue(inner.contains(alias))
    }

    @Test
    fun alias_copy_with_the_setting_on_keeps_the_signature() {
        val copy = outbound_copy(alias, alias_pgp_enabled = true)

        assertEquals(listOf("account signature"), account_material_found(copy))
    }

    @Test
    fun primary_copy_is_signed_whatever_the_setting() {
        val off = PgpDecryptor.decrypt(outbound_copy(primary, alias_pgp_enabled = false), recipient.armored_private_key, passphrase.toCharArray()).orEmpty()
        val on = PgpDecryptor.decrypt(outbound_copy(primary, alias_pgp_enabled = true), recipient.armored_private_key, passphrase.toCharArray()).orEmpty()

        assertTrue(off.contains("-----BEGIN PGP SIGNATURE-----"))
        assertTrue(on.contains("-----BEGIN PGP SIGNATURE-----"))
    }

    @Test
    fun the_signing_marker_is_true_only_when_no_alias_copy_is_signed() {
        assertTrue(MailRepository.copies_sign_only_as_primary(primary, primary, alias_pgp_enabled = true))
        assertTrue(MailRepository.copies_sign_only_as_primary(primary, primary, alias_pgp_enabled = false))
        assertTrue(MailRepository.copies_sign_only_as_primary(alias, primary, alias_pgp_enabled = false))
        assertFalse(MailRepository.copies_sign_only_as_primary(alias, primary, alias_pgp_enabled = true))
        assertFalse(MailRepository.copy_may_carry_signature(alias, primary, alias_pgp_enabled = false))
        assertTrue(MailRepository.copy_may_carry_signature(alias, primary, alias_pgp_enabled = true))
    }

    @Test
    fun only_dot_variants_of_the_primary_count_as_the_primary_like_the_server() {
        assertTrue(MailRepository.sends_as_primary_for_alias_policy("Owner <O.wner@AsterMail.org>", primary))
        assertFalse(MailRepository.sends_as_primary_for_alias_policy("owner+news@astermail.org", primary))
        assertFalse(MailRepository.sends_as_primary_for_alias_policy("owner@aster.cx", primary))
        assertFalse(MailRepository.sends_as_primary_for_alias_policy(primary, null))
        assertFalse(MailRepository.copy_may_carry_signature("owner+news@astermail.org", primary, alias_pgp_enabled = false))
    }
}
