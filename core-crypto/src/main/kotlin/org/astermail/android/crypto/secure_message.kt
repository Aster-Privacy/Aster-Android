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

import java.security.MessageDigest
import java.security.SecureRandom
import java.util.Base64
import org.astermail.android.crypto.ratchet.RatchetCrypto
import org.bouncycastle.crypto.digests.SHA256Digest
import org.bouncycastle.crypto.generators.PKCS5S2ParametersGenerator
import org.bouncycastle.crypto.params.KeyParameter
import org.json.JSONArray
import org.json.JSONObject

object SecureMessage {

    const val PLACEHOLDER = "[secure message]"

    private const val PBKDF2_ITERATIONS = 310_000
    private const val SALT_BYTES = 16
    private const val KEM_SEED_BYTES = 64
    private const val NONCE_BYTES = 12
    private const val KEY_BYTES = 32
    private const val CONTENT_KEY_INFO = "aster-secure-send-v2"

    data class Field(val ciphertext: String, val nonce: String)

    class Attachment(val filename: String, val content_type: String, val data: ByteArray)

    data class Sealed(
        val kdf_salt: String,
        val auth_proof: String,
        val kem_ciphertext: String,
        val encrypted_kem_seed: String,
        val kem_seed_nonce: String,
        val encrypted_subject: Field,
        val encrypted_body: Field,
        val attachments_bundle: String?,
    )

    class PasswordMaterial(val kdf_key: ByteArray, val auth_verifier: ByteArray)

    fun derive_password_material(password: String, salt: ByteArray): PasswordMaterial {
        val password_bytes = password.toByteArray(Charsets.UTF_8)
        val generator = PKCS5S2ParametersGenerator(SHA256Digest())
        try {
            generator.init(password_bytes, salt, PBKDF2_ITERATIONS)
            val derived = (generator.generateDerivedMacParameters(KEY_BYTES * 2 * 8) as KeyParameter).key
            try {
                return PasswordMaterial(
                    kdf_key = derived.copyOfRange(0, KEY_BYTES),
                    auth_verifier = derived.copyOfRange(KEY_BYTES, KEY_BYTES * 2),
                )
            } finally {
                derived.fill(0)
            }
        } finally {
            password_bytes.fill(0)
        }
    }

    fun derive_content_key(kem_shared_secret: ByteArray, kdf_key: ByteArray): ByteArray =
        hkdf_sha256(kem_shared_secret, kdf_key, CONTENT_KEY_INFO.toByteArray(Charsets.UTF_8), KEY_BYTES)

    fun field_aad(name: String): ByteArray =
        "aster-secure-send-v2|field=$name".toByteArray(Charsets.UTF_8)

    fun attachment_aad(sequence: Int, part: String): ByteArray =
        "aster-attachment-v2|att=$sequence|part=$part".toByteArray(Charsets.UTF_8)

    fun seal(
        password: String,
        subject: String,
        body: String,
        attachments: List<Attachment> = emptyList(),
        random: SecureRandom = SecureRandom(),
    ): Sealed {
        val salt = random_bytes(random, SALT_BYTES)
        val material = derive_password_material(password, salt)
        val kem_seed = random_bytes(random, KEM_SEED_BYTES)
        val keypair = RatchetCrypto.ml_kem_768_keypair_from_seed(kem_seed)
        val encapsulation = RatchetCrypto.ml_kem_768_encapsulate(keypair.public_key)
        val content_key = derive_content_key(encapsulation.shared_secret, material.kdf_key)
        try {
            val kem_seed_nonce = random_bytes(random, NONCE_BYTES)
            val encrypted_kem_seed = AesGcm.encrypt(material.kdf_key, kem_seed_nonce, kem_seed)
            val auth_proof = MessageDigest.getInstance("SHA-256").digest(material.auth_verifier)
            return Sealed(
                kdf_salt = encode(salt),
                auth_proof = encode(auth_proof),
                kem_ciphertext = encode(encapsulation.ciphertext),
                encrypted_kem_seed = encode(encrypted_kem_seed),
                kem_seed_nonce = encode(kem_seed_nonce),
                encrypted_subject = seal_field(
                    content_key,
                    subject.toByteArray(Charsets.UTF_8),
                    field_aad("subject"),
                    random,
                ),
                encrypted_body = seal_field(
                    content_key,
                    body.toByteArray(Charsets.UTF_8),
                    field_aad("body"),
                    random,
                ),
                attachments_bundle = seal_attachments(content_key, attachments, random),
            )
        } finally {
            content_key.fill(0)
            encapsulation.shared_secret.fill(0)
            keypair.secret_key.fill(0)
            kem_seed.fill(0)
            material.kdf_key.fill(0)
            material.auth_verifier.fill(0)
        }
    }

    private fun seal_attachments(
        content_key: ByteArray,
        attachments: List<Attachment>,
        random: SecureRandom,
    ): String? {
        if (attachments.isEmpty()) return null
        val entries = JSONArray()
        attachments.forEachIndexed { sequence, attachment ->
            val data = seal_field(content_key, attachment.data, attachment_aad(sequence, "data"), random)
            val filename = seal_field(
                content_key,
                attachment.filename.toByteArray(Charsets.UTF_8),
                attachment_aad(sequence, "filename"),
                random,
            )
            val meta_json = JSONObject()
                .put("content_type", attachment.content_type)
                .put("size_bytes", attachment.data.size)
                .toString()
            val meta = seal_field(
                content_key,
                meta_json.toByteArray(Charsets.UTF_8),
                attachment_aad(sequence, "meta"),
                random,
            )
            entries.put(
                JSONObject()
                    .put("ciphertext", data.ciphertext)
                    .put("nonce", data.nonce)
                    .put("encrypted_filename", filename.ciphertext)
                    .put("filename_nonce", filename.nonce)
                    .put("encrypted_meta", meta.ciphertext)
                    .put("meta_nonce", meta.nonce),
            )
        }
        val bundle = seal_field(
            content_key,
            entries.toString().toByteArray(Charsets.UTF_8),
            field_aad("attachments_bundle"),
            random,
        )
        val bundle_json = JSONObject()
            .put("ciphertext", bundle.ciphertext)
            .put("nonce", bundle.nonce)
            .toString()
        return encode(bundle_json.toByteArray(Charsets.UTF_8))
    }

    private fun seal_field(
        content_key: ByteArray,
        plaintext: ByteArray,
        aad: ByteArray,
        random: SecureRandom,
    ): Field {
        val nonce = random_bytes(random, NONCE_BYTES)
        return Field(
            ciphertext = encode(AesGcm.encrypt(content_key, nonce, plaintext, aad)),
            nonce = encode(nonce),
        )
    }

    private fun random_bytes(random: SecureRandom, length: Int): ByteArray =
        ByteArray(length).also { random.nextBytes(it) }

    private fun encode(bytes: ByteArray): String = Base64.getEncoder().encodeToString(bytes)
}
