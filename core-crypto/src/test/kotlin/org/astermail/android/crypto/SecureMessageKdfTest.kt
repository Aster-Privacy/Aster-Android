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

package org.astermail.android.crypto

import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertThrows
import org.junit.Test
import java.security.MessageDigest
import java.util.Base64

class SecureMessageKdfTest {

    private val password = "correct horse battery staple"
    private val salt = Base64.getEncoder().encodeToString(ByteArray(16) { 9 })
    private val v1_proof = "I06gV1lYTvcRQHBtiJ5nZ7zyCXmtvzwVNgFAKHI9OBo="
    private val v2_proof = "tpeOb5wVVq27aF05yN7D2x7CpBQ+a5sd90nS3gg0/z8="

    @After
    fun reset_switch() {
        SecureMessageKdf.write_v2 = false
    }

    private fun open(sealed: SealedSecureMessage, kdf_version: Int): OpenedSecureMessage =
        SecureMessageCrypto.open(
            password = password,
            kdf_salt = sealed.kdf_salt,
            kem_ciphertext = sealed.kem_ciphertext,
            encrypted_kem_seed = sealed.encrypted_kem_seed,
            kem_seed_nonce = sealed.kem_seed_nonce,
            encrypted_subject = sealed.encrypted_subject,
            encrypted_body = sealed.encrypted_body,
            attachments_bundle = sealed.attachments_bundle,
            kdf_version = kdf_version,
        )

    private fun proof_from(material: SecureMessage.PasswordMaterial): String =
        Base64.getEncoder().encodeToString(
            MessageDigest.getInstance("SHA-256").digest(material.auth_verifier),
        )

    @Test
    fun v1_proof_matches_the_web_vector() {
        assertEquals(v1_proof, SecureMessageCrypto.derive_auth_proof(password, salt))
        assertEquals(v1_proof, SecureMessageCrypto.derive_auth_proof(password, salt, SecureMessageKdf.V1))
    }

    @Test
    fun v2_proof_matches_the_web_vector() {
        assertEquals(v2_proof, SecureMessageCrypto.derive_auth_proof(password, salt, SecureMessageKdf.V2))
        val material = SecureMessage.derive_password_material(
            password,
            Base64.getDecoder().decode(salt),
            SecureMessageKdf.V2,
        )
        assertEquals(v2_proof, proof_from(material))
    }

    @Test
    fun messages_stay_v1_while_the_switch_is_off() {
        val sealed = SecureMessageCrypto.seal(password, "Hello", "Body")
        assertEquals(SecureMessageKdf.V1, sealed.kdf_version)
        assertEquals(sealed.auth_proof, SecureMessageCrypto.derive_auth_proof(password, sealed.kdf_salt))
        assertEquals("Hello", open(sealed, SecureMessageKdf.V1).subject)
        assertEquals(SecureMessageKdf.V1, SecureMessage.seal(password, "Hello", "Body").kdf_version)
    }

    @Test
    fun v2_messages_seal_and_open_once_the_switch_is_on() {
        SecureMessageKdf.write_v2 = true
        val sealed = SecureMessageCrypto.seal(password, "Hello", "Body")
        assertEquals(SecureMessageKdf.V2, sealed.kdf_version)
        assertEquals(
            sealed.auth_proof,
            SecureMessageCrypto.derive_auth_proof(password, sealed.kdf_salt, SecureMessageKdf.V2),
        )
        assertNotEquals(
            sealed.auth_proof,
            SecureMessageCrypto.derive_auth_proof(password, sealed.kdf_salt, SecureMessageKdf.V1),
        )
        assertEquals("Body", open(sealed, SecureMessageKdf.V2).body)
        assertThrows(Exception::class.java) { open(sealed, SecureMessageKdf.V1) }
    }

    @Test
    fun the_second_sealer_writes_v2_proofs_once_the_switch_is_on() {
        SecureMessageKdf.write_v2 = true
        val sealed = SecureMessage.seal(password, "Hello", "Body")
        assertEquals(SecureMessageKdf.V2, sealed.kdf_version)
        val material = SecureMessage.derive_password_material(
            password,
            Base64.getDecoder().decode(sealed.kdf_salt),
            SecureMessageKdf.V2,
        )
        assertEquals(sealed.auth_proof, proof_from(material))
    }

    @Test
    fun only_known_versions_are_accepted() {
        assertEquals(SecureMessageKdf.V1, SecureMessageKdf.parse(null))
        assertEquals(SecureMessageKdf.V2, SecureMessageKdf.parse(2))
        assertThrows(IllegalArgumentException::class.java) { SecureMessageKdf.parse(3) }
    }
}
