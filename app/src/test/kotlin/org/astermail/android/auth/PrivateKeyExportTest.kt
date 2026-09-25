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

import java.security.SecureRandom
import java.util.Base64
import org.astermail.android.crypto.AesGcm
import org.astermail.android.crypto.PasswordKdf
import org.astermail.android.crypto.PgpKeyGenerator
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class PrivateKeyExportTest {

    private val password = "correct horse battery staple"
    private val iterations = 1000

    private val current = PgpKeyGenerator.generate("Alice", "alice@astermail.org", password.toCharArray())
    private val previous = PgpKeyGenerator.generate("Alice", "alice@astermail.org", password.toCharArray())
    private val current_fingerprint = PrivateKeyExport.fingerprint(current.armored_private_key)!!
    private val previous_fingerprint = PrivateKeyExport.fingerprint(previous.armored_private_key)!!

    private fun blob_for(armored: String, blob_password: String): Pair<String, String> {
        val rng = SecureRandom()
        val salt = ByteArray(16).also { rng.nextBytes(it) }
        val nonce = ByteArray(12).also { rng.nextBytes(it) }
        val key = PasswordKdf.derive_aes_key(blob_password.toCharArray(), salt, iterations)
        val ciphertext = AesGcm.encrypt(key, nonce, armored.toByteArray(Charsets.UTF_8))
        val encoder = Base64.getEncoder()
        return encoder.encodeToString(salt + ciphertext) to encoder.encodeToString(nonce)
    }

    private fun select(
        candidates: List<String>,
        fingerprint: String,
        typed: String = password,
        blob: Pair<String, String>? = null,
    ): String? = PrivateKeyExport.select(
        candidates = candidates,
        fingerprint = fingerprint,
        password = typed.toCharArray(),
        encrypted_blob_b64 = blob?.first,
        nonce_b64 = blob?.second,
        pbkdf2_iterations = iterations,
    )

    @Test
    fun prefers_the_vault_key_that_matches_and_unlocks() {
        val found = select(
            listOf(current.armored_private_key, previous.armored_private_key),
            previous_fingerprint.lowercase(),
        )
        assertEquals(previous.armored_private_key, found)
    }

    @Test
    fun rejects_a_vault_key_when_the_password_is_wrong() {
        assertNull(select(listOf(current.armored_private_key), current_fingerprint, typed = "wrong"))
    }

    @Test
    fun falls_back_to_the_blob_when_no_vault_key_matches() {
        val blob = blob_for(current.armored_private_key, password)
        assertEquals(current.armored_private_key, select(emptyList(), current_fingerprint, blob = blob))
    }

    @Test
    fun never_returns_ciphertext_when_the_blob_cannot_be_opened() {
        val blob = blob_for(current.armored_private_key, "an older password")
        assertNull(select(emptyList(), current_fingerprint, blob = blob))
    }

    @Test
    fun rejects_a_blob_whose_key_has_a_different_fingerprint() {
        val blob = blob_for(previous.armored_private_key, password)
        assertNull(select(emptyList(), current_fingerprint, blob = blob))
    }

    @Test
    fun returns_null_for_a_blank_fingerprint() {
        assertNull(select(listOf(current.armored_private_key), " "))
    }
}
