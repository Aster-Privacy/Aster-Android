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

import org.astermail.android.crypto.ratchet.RatchetCrypto
import org.bouncycastle.crypto.digests.SHA256Digest
import org.bouncycastle.crypto.generators.PKCS5S2ParametersGenerator
import org.bouncycastle.crypto.params.KeyParameter
import org.json.JSONArray
import org.json.JSONObject
import java.security.MessageDigest
import java.util.Base64

data class SecureMessageField(
    val ciphertext: String,
    val nonce: String,
)

class SecureMessageAttachment(
    val filename: String,
    val content_type: String,
    val data: ByteArray,
)

data class SealedSecureMessage(
    val kdf_salt: String,
    val auth_proof: String,
    val kem_ciphertext: String,
    val encrypted_kem_seed: String,
    val kem_seed_nonce: String,
    val encrypted_subject: SecureMessageField,
    val encrypted_body: SecureMessageField,
    val attachments_bundle: String?,
)

class OpenedSecureMessage(
    val subject: String,
    val body: String,
    val attachments: List<SecureMessageAttachment>,
)

object SecureMessageCrypto {
    const val PBKDF2_ITERATIONS = 310000
    const val SALT_BYTES = 16
    const val KEM_SEED_BYTES = 64
    const val NONCE_BYTES = 12

    private const val KDF_KEY_BYTES = 32
    private const val AUTH_VERIFIER_BYTES = 32
    private const val CONTENT_KEY_BYTES = 32
    private const val CONTENT_KEY_INFO = "aster-secure-send-v2"
    private const val FIELD_SUBJECT = "subject"
    private const val FIELD_BODY = "body"
    private const val FIELD_ATTACHMENTS_BUNDLE = "attachments_bundle"
    private const val PART_DATA = "data"
    private const val PART_FILENAME = "filename"
    private const val PART_META = "meta"
    private const val DEFAULT_CONTENT_TYPE = "application/octet-stream"

    private class RawMaterial(val kdf_key: ByteArray, val auth_verifier: ByteArray)

    private fun b64(bytes: ByteArray): String = Base64.getEncoder().encodeToString(bytes)

    private fun unb64(text: String): ByteArray = Base64.getDecoder().decode(text)

    private fun sha256(bytes: ByteArray): ByteArray =
        MessageDigest.getInstance("SHA-256").digest(bytes)

    private fun utf8(text: String): ByteArray = text.toByteArray(Charsets.UTF_8)

    private fun derive_raw_material(password: String, salt: ByteArray): RawMaterial {
        val password_bytes = utf8(password)
        val derived = try {
            val generator = PKCS5S2ParametersGenerator(SHA256Digest())
            generator.init(password_bytes, salt, PBKDF2_ITERATIONS)
            val parameters = generator.generateDerivedMacParameters(
                (KDF_KEY_BYTES + AUTH_VERIFIER_BYTES) * 8,
            ) as KeyParameter
            parameters.key
        } finally {
            password_bytes.fill(0)
        }
        val material = RawMaterial(
            kdf_key = derived.copyOfRange(0, KDF_KEY_BYTES),
            auth_verifier = derived.copyOfRange(KDF_KEY_BYTES, KDF_KEY_BYTES + AUTH_VERIFIER_BYTES),
        )
        derived.fill(0)
        return material
    }

    private fun derive_content_key(kem_shared_secret: ByteArray, kdf_key: ByteArray): ByteArray =
        hkdf_sha256(
            ikm = kem_shared_secret,
            salt = kdf_key,
            info = utf8(CONTENT_KEY_INFO),
            length = CONTENT_KEY_BYTES,
        )

    private fun field_aad(name: String): ByteArray = utf8("aster-secure-send-v2|field=$name")

    private fun attachment_aad(sequence: Int, part: String): ByteArray =
        utf8("aster-attachment-v2|att=$sequence|part=$part")

    private fun encrypt_field(key: ByteArray, data: ByteArray, aad: ByteArray): SecureMessageField {
        val nonce = RatchetCrypto.random_bytes(NONCE_BYTES)
        return SecureMessageField(
            ciphertext = b64(AesGcm.encrypt(key, nonce, data, aad)),
            nonce = b64(nonce),
        )
    }

    private fun decrypt_field(key: ByteArray, field: SecureMessageField, aad: ByteArray): ByteArray =
        AesGcm.decrypt(key, unb64(field.nonce), unb64(field.ciphertext), aad)

    fun derive_auth_proof(password: String, kdf_salt: String): String {
        val material = derive_raw_material(password, unb64(kdf_salt))
        material.kdf_key.fill(0)
        val proof = b64(sha256(material.auth_verifier))
        material.auth_verifier.fill(0)
        return proof
    }

    fun seal(
        password: String,
        subject: String,
        body: String,
        attachments: List<SecureMessageAttachment> = emptyList(),
    ): SealedSecureMessage {
        require(password.isNotEmpty()) { "a message password is required" }
        val salt = RatchetCrypto.random_bytes(SALT_BYTES)
        val material = derive_raw_material(password, salt)
        val auth_proof = b64(sha256(material.auth_verifier))
        material.auth_verifier.fill(0)

        val kem_seed = RatchetCrypto.random_bytes(KEM_SEED_BYTES)
        val keypair = RatchetCrypto.ml_kem_768_keypair_from_seed(kem_seed)
        val encapsulation = RatchetCrypto.ml_kem_768_encapsulate(keypair.public_key)
        keypair.secret_key.fill(0)
        val content_key = derive_content_key(encapsulation.shared_secret, material.kdf_key)
        encapsulation.shared_secret.fill(0)

        try {
            val kem_seed_nonce = RatchetCrypto.random_bytes(NONCE_BYTES)
            val encrypted_kem_seed = AesGcm.encrypt(material.kdf_key, kem_seed_nonce, kem_seed)

            return SealedSecureMessage(
                kdf_salt = b64(salt),
                auth_proof = auth_proof,
                kem_ciphertext = b64(encapsulation.ciphertext),
                encrypted_kem_seed = b64(encrypted_kem_seed),
                kem_seed_nonce = b64(kem_seed_nonce),
                encrypted_subject = encrypt_field(content_key, utf8(subject), field_aad(FIELD_SUBJECT)),
                encrypted_body = encrypt_field(content_key, utf8(body), field_aad(FIELD_BODY)),
                attachments_bundle = seal_attachments(content_key, attachments),
            )
        } finally {
            kem_seed.fill(0)
            content_key.fill(0)
            material.kdf_key.fill(0)
        }
    }

    private fun seal_attachments(
        content_key: ByteArray,
        attachments: List<SecureMessageAttachment>,
    ): String? {
        if (attachments.isEmpty()) return null
        val entries = JSONArray()
        attachments.forEachIndexed { sequence, attachment ->
            val data_field = encrypt_field(
                content_key,
                attachment.data,
                attachment_aad(sequence, PART_DATA),
            )
            val filename_field = encrypt_field(
                content_key,
                utf8(attachment.filename),
                attachment_aad(sequence, PART_FILENAME),
            )
            val meta = JSONObject()
                .put("content_type", attachment.content_type)
                .put("size_bytes", attachment.data.size)
            val meta_field = encrypt_field(
                content_key,
                utf8(meta.toString()),
                attachment_aad(sequence, PART_META),
            )
            entries.put(
                JSONObject()
                    .put("ciphertext", data_field.ciphertext)
                    .put("nonce", data_field.nonce)
                    .put("encrypted_filename", filename_field.ciphertext)
                    .put("filename_nonce", filename_field.nonce)
                    .put("encrypted_meta", meta_field.ciphertext)
                    .put("meta_nonce", meta_field.nonce),
            )
        }
        val bundle_field = encrypt_field(
            content_key,
            utf8(entries.toString()),
            field_aad(FIELD_ATTACHMENTS_BUNDLE),
        )
        val envelope = JSONObject()
            .put("ciphertext", bundle_field.ciphertext)
            .put("nonce", bundle_field.nonce)
        return b64(utf8(envelope.toString()))
    }

    fun open(
        password: String,
        kdf_salt: String,
        kem_ciphertext: String,
        encrypted_kem_seed: String,
        kem_seed_nonce: String,
        encrypted_subject: SecureMessageField,
        encrypted_body: SecureMessageField,
        attachments_bundle: String?,
    ): OpenedSecureMessage {
        val material = derive_raw_material(password, unb64(kdf_salt))
        material.auth_verifier.fill(0)
        val content_key = try {
            val kem_seed = AesGcm.decrypt(
                material.kdf_key,
                unb64(kem_seed_nonce),
                unb64(encrypted_kem_seed),
            )
            val keypair = RatchetCrypto.ml_kem_768_keypair_from_seed(kem_seed)
            kem_seed.fill(0)
            val shared_secret = RatchetCrypto.ml_kem_768_decapsulate(
                unb64(kem_ciphertext),
                keypair.secret_key,
            )
            keypair.secret_key.fill(0)
            val key = derive_content_key(shared_secret, material.kdf_key)
            shared_secret.fill(0)
            key
        } finally {
            material.kdf_key.fill(0)
        }

        try {
            return OpenedSecureMessage(
                subject = String(
                    decrypt_field(content_key, encrypted_subject, field_aad(FIELD_SUBJECT)),
                    Charsets.UTF_8,
                ),
                body = String(
                    decrypt_field(content_key, encrypted_body, field_aad(FIELD_BODY)),
                    Charsets.UTF_8,
                ),
                attachments = open_attachments(content_key, attachments_bundle),
            )
        } finally {
            content_key.fill(0)
        }
    }

    private fun open_attachments(
        content_key: ByteArray,
        attachments_bundle: String?,
    ): List<SecureMessageAttachment> {
        if (attachments_bundle.isNullOrEmpty()) return emptyList()
        val envelope = JSONObject(String(unb64(attachments_bundle), Charsets.UTF_8))
        val entries = JSONArray(
            String(
                decrypt_field(
                    content_key,
                    SecureMessageField(
                        ciphertext = envelope.getString("ciphertext"),
                        nonce = envelope.getString("nonce"),
                    ),
                    field_aad(FIELD_ATTACHMENTS_BUNDLE),
                ),
                Charsets.UTF_8,
            ),
        )
        return (0 until entries.length()).map { sequence ->
            val entry = entries.getJSONObject(sequence)
            val data = decrypt_field(
                content_key,
                SecureMessageField(entry.getString("ciphertext"), entry.getString("nonce")),
                attachment_aad(sequence, PART_DATA),
            )
            val filename = String(
                decrypt_field(
                    content_key,
                    SecureMessageField(
                        entry.getString("encrypted_filename"),
                        entry.getString("filename_nonce"),
                    ),
                    attachment_aad(sequence, PART_FILENAME),
                ),
                Charsets.UTF_8,
            )
            val meta = JSONObject(
                String(
                    decrypt_field(
                        content_key,
                        SecureMessageField(
                            entry.getString("encrypted_meta"),
                            entry.getString("meta_nonce"),
                        ),
                        attachment_aad(sequence, PART_META),
                    ),
                    Charsets.UTF_8,
                ),
            )
            SecureMessageAttachment(
                filename = filename,
                content_type = meta.optString("content_type", DEFAULT_CONTENT_TYPE),
                data = data,
            )
        }
    }
}
