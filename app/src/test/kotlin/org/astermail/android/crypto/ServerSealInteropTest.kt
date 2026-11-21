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
import java.util.Base64
import org.bouncycastle.bcpg.ArmoredOutputStream
import org.bouncycastle.bcpg.HashAlgorithmTags
import org.bouncycastle.bcpg.SymmetricKeyAlgorithmTags
import org.bouncycastle.openpgp.PGPSecretKeyRing
import org.bouncycastle.openpgp.operator.bc.BcPBESecretKeyEncryptorBuilder
import org.bouncycastle.openpgp.operator.bc.BcPGPDigestCalculatorProvider
import org.bouncycastle.openpgp.operator.jcajce.JcaKeyFingerprintCalculator
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test

class ServerSealInteropTest {

    private lateinit var seal: JSONObject
    private lateinit var key: JSONObject

    private fun resource(name: String): JSONObject {
        val stream = checkNotNull(javaClass.classLoader?.getResourceAsStream(name))
        return JSONObject(stream.bufferedReader().use { it.readText() })
    }

    @Before
    fun set_up() {
        PgpDecryptor.clear_caches()
        seal = resource("server_seal_vectors.json")
        key = resource(seal.getString("key_vector"))
    }

    private fun protected_private_key(passphrase: CharArray): String {
        val ring = PGPSecretKeyRing(
            Base64.getDecoder().decode(key.getString("secret_key_b64")),
            JcaKeyFingerprintCalculator(),
        )
        val encryptor = BcPBESecretKeyEncryptorBuilder(
            SymmetricKeyAlgorithmTags.AES_256,
            BcPGPDigestCalculatorProvider().get(HashAlgorithmTags.SHA256),
        ).build(passphrase)
        val locked = PGPSecretKeyRing.copyWithNewPassword(ring, null, encryptor)
        val out = ByteArrayOutputStream()
        ArmoredOutputStream(out).use { locked.encode(it) }
        return out.toString(Charsets.UTF_8.name())
    }

    @Test
    fun opens_a_message_sealed_by_the_server_to_the_account_key() {
        val passphrase = "correct horse".toCharArray()
        val result = PgpDecryptor.decrypt_with_own_keys_status(
            seal.getString("sealed_armored"),
            listOf(protected_private_key(passphrase)),
            passphrase,
        )

        assertEquals(seal.getString("plaintext"), result?.plaintext)
    }

    @Test
    fun opens_the_seal_when_an_unrelated_key_comes_first() {
        val passphrase = "correct horse".toCharArray()
        val other = PgpKeyGenerator.generate("other", "other@astermail.org", passphrase)
        val result = PgpDecryptor.decrypt_with_own_keys_status(
            seal.getString("sealed_armored"),
            listOf(other.armored_private_key, protected_private_key(passphrase)),
            passphrase,
        )

        assertEquals(seal.getString("plaintext"), result?.plaintext)
    }

    @Test
    fun refuses_the_seal_with_the_wrong_passphrase() {
        val result = PgpDecryptor.decrypt_with_own_keys_status(
            seal.getString("sealed_armored"),
            listOf(protected_private_key("correct horse".toCharArray())),
            "wrong".toCharArray(),
        )

        assertNull(result?.plaintext)
    }

    @Test
    fun opens_a_large_server_seal_to_a_key_generated_on_android() {
        val large = resource("server_seal_large_vector.json")
        val passphrase = large.getString("passphrase").toCharArray()
        val result = PgpDecryptor.decrypt_with_own_keys_status(
            large.getString("sealed_armored"),
            listOf(String(Base64.getDecoder().decode(large.getString("private_key_armored_b64")), Charsets.UTF_8)),
            passphrase,
        )

        assertEquals(large.getString("plaintext"), result?.plaintext)
    }
}
