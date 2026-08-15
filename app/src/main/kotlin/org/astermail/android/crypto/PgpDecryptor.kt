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

import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.InputStream
import org.bouncycastle.jce.provider.BouncyCastleProvider
import org.bouncycastle.openpgp.PGPEncryptedDataList
import org.bouncycastle.openpgp.PGPLiteralData
import org.bouncycastle.openpgp.PGPObjectFactory
import org.bouncycastle.openpgp.PGPCompressedData
import org.bouncycastle.openpgp.PGPOnePassSignature
import org.bouncycastle.openpgp.PGPOnePassSignatureList
import org.bouncycastle.openpgp.PGPPublicKeyEncryptedData
import org.bouncycastle.openpgp.PGPPublicKeyRingCollection
import org.bouncycastle.openpgp.PGPSecretKeyRingCollection
import org.bouncycastle.openpgp.PGPSignatureList
import org.bouncycastle.openpgp.PGPUtil
import org.bouncycastle.openpgp.operator.jcajce.JcaKeyFingerprintCalculator
import org.bouncycastle.openpgp.operator.jcajce.JcePBESecretKeyDecryptorBuilder
import org.bouncycastle.openpgp.operator.jcajce.JcePublicKeyDataDecryptorFactoryBuilder

enum class PgpSignatureStatus {
    NONE,
    UNVERIFIED,
    VALID,
    INVALID,
}

data class PgpDecryptionResult(
    val plaintext: String?,
    val signature: PgpSignatureStatus,
)

object PgpDecryptor {

    init {
        java.security.Security.removeProvider(BouncyCastleProvider.PROVIDER_NAME)
        java.security.Security.addProvider(BouncyCastleProvider())
    }

    fun decrypt(
        armored_ciphertext: String,
        armored_private_key: String,
        passphrase: CharArray,
    ): String? = decrypt_with_status(armored_ciphertext, armored_private_key, passphrase, null).plaintext

    fun decrypt_with_status(
        armored_ciphertext: String,
        armored_private_key: String,
        passphrase: CharArray,
        sender_public_key_armored: String?,
    ): PgpDecryptionResult {
        val verifier = sender_public_key_armored
            ?.takeIf { it.isNotBlank() }
            ?.let { runCatching { load_public_keys(it) }.getOrNull() }
        val status = SignatureTracker(verifier)
        val plaintext = decrypt_internal(armored_ciphertext, armored_private_key, passphrase, status)
        return PgpDecryptionResult(plaintext, if (plaintext == null) PgpSignatureStatus.NONE else status.result())
    }

    private fun decrypt_internal(
        armored_ciphertext: String,
        armored_private_key: String,
        passphrase: CharArray,
        tracker: SignatureTracker,
    ): String? {
        return try {
            val key_stream = PGPUtil.getDecoderStream(
                ByteArrayInputStream(armored_private_key.toByteArray(Charsets.UTF_8)),
            )
            val key_rings = PGPSecretKeyRingCollection(key_stream, JcaKeyFingerprintCalculator())

            val msg_stream = PGPUtil.getDecoderStream(
                ByteArrayInputStream(armored_ciphertext.toByteArray(Charsets.UTF_8)),
            )
            val pgp_factory = PGPObjectFactory(msg_stream, JcaKeyFingerprintCalculator())
            val enc_data_list = find_encrypted_data_list(pgp_factory) ?: return null

            @Suppress("UNCHECKED_CAST")
            val iterator = enc_data_list.encryptedDataObjects as Iterator<Any>
            while (iterator.hasNext()) {
                val pbe = iterator.next()
                if (pbe !is PGPPublicKeyEncryptedData) continue

                @Suppress("DEPRECATION")
                val secret_key = key_rings.getSecretKey(pbe.keyID) ?: continue

                if (!pbe.isIntegrityProtected()) continue

                val decryptor = JcePBESecretKeyDecryptorBuilder()
                    .setProvider(BouncyCastleProvider.PROVIDER_NAME)
                    .build(passphrase)
                val private_key = secret_key.extractPrivateKey(decryptor)

                val decryptor_factory = JcePublicKeyDataDecryptorFactoryBuilder()
                    .setProvider(BouncyCastleProvider.PROVIDER_NAME)
                    .build(private_key)

                val clear_stream = pbe.getDataStream(decryptor_factory)
                val plaintext = extract_literal_data(clear_stream, tracker)
                if (plaintext != null) {
                    if (pbe.verify()) return plaintext
                    return null
                }
            }
            null
        } catch (_: Throwable) {
            null
        }
    }

    private fun find_encrypted_data_list(factory: PGPObjectFactory): PGPEncryptedDataList? {
        var obj = factory.nextObject()
        while (obj != null) {
            if (obj is PGPEncryptedDataList) return obj
            obj = factory.nextObject()
        }
        return null
    }

    private fun extract_literal_data(stream: InputStream, tracker: SignatureTracker): String? {
        val factory = PGPObjectFactory(stream, JcaKeyFingerprintCalculator())
        return extract_from_factory(factory, 0, tracker)
    }

    private fun extract_from_factory(
        factory: PGPObjectFactory,
        depth: Int,
        tracker: SignatureTracker,
    ): String? {
        if (depth > max_compression_depth) {
            throw IllegalStateException("pgp message nests compression too deeply")
        }
        var obj = factory.nextObject()
        var plaintext: String? = null
        while (obj != null) {
            when {
                obj is PGPOnePassSignatureList -> tracker.begin(obj)
                obj is PGPLiteralData -> plaintext = read_literal(obj, tracker)
                obj is PGPSignatureList -> {
                    tracker.finish(obj)
                    if (plaintext != null) return plaintext
                }
                obj is PGPCompressedData -> {
                    val inner = PGPObjectFactory(obj.dataStream, JcaKeyFingerprintCalculator())
                    val result = extract_from_factory(inner, depth + 1, tracker)
                    if (result != null) return result
                }
            }
            obj = factory.nextObject()
        }
        return plaintext
    }

    private fun read_literal(literal: PGPLiteralData, tracker: SignatureTracker): String {
        val out = ByteArrayOutputStream()
        val buffer = ByteArray(1 shl 16)
        var total = 0L
        literal.inputStream.use { input ->
            while (true) {
                val read = input.read(buffer)
                if (read <= 0) break
                total += read
                if (total > max_decompressed_bytes) {
                    throw IllegalStateException("decrypted pgp message exceeds size limit")
                }
                tracker.update(buffer, read)
                out.write(buffer, 0, read)
            }
        }
        buffer.fill(0)
        return out.toString(Charsets.UTF_8.name())
    }

    private fun load_public_keys(armored: String): PGPPublicKeyRingCollection =
        PGPPublicKeyRingCollection(
            PGPUtil.getDecoderStream(ByteArrayInputStream(armored.toByteArray(Charsets.UTF_8))),
            JcaKeyFingerprintCalculator(),
        )

    private class SignatureTracker(private val keys: PGPPublicKeyRingCollection?) {
        private var one_pass: PGPOnePassSignature? = null
        private var saw_signature = false
        private var verified: Boolean? = null

        fun begin(list: PGPOnePassSignatureList) {
            if (list.isEmpty) return
            saw_signature = true
            val key_rings = keys ?: return
            val candidate = list[0]
            val key = runCatching { key_rings.getPublicKey(candidate.keyID) }.getOrNull() ?: return
            runCatching {
                candidate.init(
                    org.bouncycastle.openpgp.operator.jcajce.JcaPGPContentVerifierBuilderProvider()
                        .setProvider(BouncyCastleProvider.PROVIDER_NAME),
                    key,
                )
                one_pass = candidate
            }
        }

        fun update(buffer: ByteArray, length: Int) {
            val active = one_pass ?: return
            runCatching { active.update(buffer, 0, length) }
        }

        fun finish(list: PGPSignatureList) {
            if (!list.isEmpty) saw_signature = true
            val active = one_pass ?: return
            if (list.isEmpty) return
            verified = runCatching { active.verify(list[0]) }.getOrDefault(false)
        }

        fun result(): PgpSignatureStatus = when {
            !saw_signature -> PgpSignatureStatus.NONE
            verified == true -> PgpSignatureStatus.VALID
            verified == false -> PgpSignatureStatus.INVALID
            else -> PgpSignatureStatus.UNVERIFIED
        }
    }

    private const val max_decompressed_bytes = 48L * 1024 * 1024
    private const val max_compression_depth = 4
}
