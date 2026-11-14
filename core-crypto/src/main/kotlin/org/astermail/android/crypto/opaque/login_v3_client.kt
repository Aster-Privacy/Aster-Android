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
package org.astermail.android.crypto.opaque

import java.math.BigInteger
import java.security.MessageDigest
import javax.crypto.Mac
import javax.crypto.spec.SecretKeySpec
import org.bouncycastle.crypto.engines.ChaChaEngine
import org.bouncycastle.crypto.generators.Argon2BytesGenerator
import org.bouncycastle.crypto.params.Argon2Parameters
import org.bouncycastle.crypto.params.KeyParameter
import org.bouncycastle.crypto.params.ParametersWithIV

class LoginV3RejectedException : Exception("sign-in proof rejected")

class LoginV3MalformedException : Exception("malformed sign-in message")

class LoginV3Start internal constructor(
    val message: ByteArray,
    internal val blind: BigInteger,
    internal val blinded_element: ByteArray,
    internal val ephemeral_secret: BigInteger?,
)

object LoginV3Client {
    const val SEED_LEN = 32
    const val REGISTRATION_REQUEST_LEN = 32
    const val REGISTRATION_RESPONSE_LEN = 64
    const val REGISTRATION_UPLOAD_LEN = 192
    const val CREDENTIAL_REQUEST_LEN = 96
    const val CREDENTIAL_RESPONSE_LEN = 320
    const val CREDENTIAL_FINALIZATION_LEN = 64

    private const val HASH_LEN = 64
    private const val NONCE_LEN = 32
    private const val ENVELOPE_LEN = NONCE_LEN + HASH_LEN
    private const val MASKED_RESPONSE_LEN = RISTRETTO_ELEMENT_LEN + ENVELOPE_LEN
    private const val HASH_BLOCK_LEN = 128
    private const val HMAC_ALGORITHM = "HmacSHA512"
    private const val DIGEST_ALGORITHM = "SHA-512"
    private const val KSF_MEMORY_KIB = 47104
    private const val KSF_ITERATIONS = 1
    private const val KSF_PARALLELISM = 1
    private const val KSF_SALT_LEN = 16
    private const val RNG_NONCE_LEN = 8
    private const val RNG_ROUNDS = 20
    private const val DERIVE_KEY_ATTEMPTS = 256

    private val login_context = ascii("aster-login-v3")
    private val oprf_context = ascii("OPRFV1-") + byteArrayOf(0) + ascii("-ristretto255-SHA512")
    private val hash_to_group_dst = ascii("HashToGroup-") + oprf_context
    private val derive_key_pair_dst = ascii("DeriveKeyPair") + oprf_context
    private val derive_key_pair_info = ascii("OPAQUE-DeriveDiffieHellmanKeyPair")
    private val finalize_label = ascii("Finalize")
    private val auth_key_label = ascii("AuthKey")
    private val private_key_label = ascii("PrivateKey")
    private val masking_key_label = ascii("MaskingKey")
    private val response_pad_label = ascii("CredentialResponsePad")
    private val transcript_prefix = ascii("OPAQUEv1-")
    private val label_prefix = ascii("OPAQUE-")
    private val handshake_secret_label = ascii("HandshakeSecret")
    private val server_mac_label = ascii("ServerMAC")
    private val client_mac_label = ascii("ClientMAC")

    fun registration_start(password: ByteArray, seed: ByteArray): LoginV3Start {
        val rng = SeededStream(seed)
        val blind = random_scalar(rng)
        val blinded = blind_password(password, blind)
        return LoginV3Start(blinded.copyOf(), blind, blinded, null)
    }

    fun registration_finish(
        start: LoginV3Start,
        password: ByteArray,
        response: ByteArray,
        seed: ByteArray,
    ): ByteArray {
        if (response.size != REGISTRATION_RESPONSE_LEN) throw LoginV3MalformedException()
        val rng = SeededStream(seed)
        val evaluated = response.copyOfRange(0, RISTRETTO_ELEMENT_LEN)
        val server_public = response.copyOfRange(RISTRETTO_ELEMENT_LEN, REGISTRATION_RESPONSE_LEN)
        decode_element(server_public)
        if (evaluated.contentEquals(start.blinded_element)) throw LoginV3MalformedException()
        val randomized_password = randomized_password(start, password, evaluated)
        try {
            val nonce = rng.next(NONCE_LEN)
            val opened = envelope_keys(randomized_password, nonce, server_public)
            try {
                val masking_key = hkdf_expand(randomized_password, masking_key_label, HASH_LEN)
                return opened.client_public + masking_key + nonce + opened.tag
            } finally {
                opened.wipe()
            }
        } finally {
            randomized_password.fill(0)
        }
    }

    fun login_start(password: ByteArray, seed: ByteArray): LoginV3Start {
        val rng = SeededStream(seed)
        val blind = random_scalar(rng)
        val blinded = blind_password(password, blind)
        val ephemeral_seed = rng.next(SEED_LEN)
        val ephemeral_secret = derive_scalar(ephemeral_seed)
        ephemeral_seed.fill(0)
        val client_nonce = rng.next(NONCE_LEN)
        val ephemeral_public =
            Ristretto255.encode(Ristretto255.multiply(Ristretto255.base_point, ephemeral_secret))
        return LoginV3Start(blinded + client_nonce + ephemeral_public, blind, blinded, ephemeral_secret)
    }

    fun login_finish(start: LoginV3Start, password: ByteArray, response: ByteArray): ByteArray {
        val ephemeral_secret = start.ephemeral_secret ?: throw LoginV3MalformedException()
        if (response.size != CREDENTIAL_RESPONSE_LEN || start.message.size != CREDENTIAL_REQUEST_LEN) {
            throw LoginV3MalformedException()
        }
        var offset = 0
        fun take(length: Int): ByteArray =
            response.copyOfRange(offset, offset + length).also { offset += length }
        val evaluated = take(RISTRETTO_ELEMENT_LEN)
        val masking_nonce = take(NONCE_LEN)
        val masked_response = take(MASKED_RESPONSE_LEN)
        val server_nonce = take(NONCE_LEN)
        val server_ephemeral_public = take(RISTRETTO_ELEMENT_LEN)
        val server_mac = take(HASH_LEN)

        val server_ephemeral = decode_element(server_ephemeral_public)
        val randomized_password = randomized_password(start, password, evaluated)
        try {
            val masking_key = hkdf_expand(randomized_password, masking_key_label, HASH_LEN)
            val pad = hkdf_expand(masking_key, masking_nonce + response_pad_label, MASKED_RESPONSE_LEN)
            masking_key.fill(0)
            val unmasked = ByteArray(MASKED_RESPONSE_LEN) { index ->
                (masked_response[index].toInt() xor pad[index].toInt()).toByte()
            }
            pad.fill(0)
            val server_public = unmasked.copyOfRange(0, RISTRETTO_ELEMENT_LEN)
            val envelope_nonce =
                unmasked.copyOfRange(RISTRETTO_ELEMENT_LEN, RISTRETTO_ELEMENT_LEN + NONCE_LEN)
            val envelope_tag = unmasked.copyOfRange(RISTRETTO_ELEMENT_LEN + NONCE_LEN, MASKED_RESPONSE_LEN)
            val server_static = Ristretto255.decode(server_public)
            if (server_static == null || Ristretto255.is_identity(server_static)) {
                throw LoginV3RejectedException()
            }
            val opened = envelope_keys(randomized_password, envelope_nonce, server_public)
            try {
                if (!MessageDigest.isEqual(opened.tag, envelope_tag)) throw LoginV3RejectedException()

                val transcript = transcript_prefix + length_prefixed(login_context) +
                    length_prefixed(opened.client_public) + start.message +
                    length_prefixed(server_public) + evaluated + masking_nonce + masked_response +
                    server_nonce + server_ephemeral_public
                val transcript_hash = sha512(transcript)

                val shared = shared_secret(server_ephemeral, ephemeral_secret) +
                    shared_secret(server_static, ephemeral_secret) +
                    shared_secret(server_ephemeral, opened.client_secret)
                val key_material = hkdf_extract(shared)
                shared.fill(0)
                val handshake_secret = expand_label(key_material, handshake_secret_label, transcript_hash)
                key_material.fill(0)
                val server_mac_key = expand_label(handshake_secret, server_mac_label, ByteArray(0))
                val client_mac_key = expand_label(handshake_secret, client_mac_label, ByteArray(0))
                handshake_secret.fill(0)
                try {
                    if (!MessageDigest.isEqual(hmac(server_mac_key, transcript_hash), server_mac)) {
                        throw LoginV3RejectedException()
                    }
                    return hmac(client_mac_key, sha512(transcript + server_mac))
                } finally {
                    server_mac_key.fill(0)
                    client_mac_key.fill(0)
                }
            } finally {
                opened.wipe()
            }
        } finally {
            randomized_password.fill(0)
        }
    }

    private class EnvelopeKeys(
        val client_secret: BigInteger,
        val client_public: ByteArray,
        val tag: ByteArray,
        private val auth_key: ByteArray,
    ) {
        fun wipe() {
            auth_key.fill(0)
        }
    }

    private fun envelope_keys(
        randomized_password: ByteArray,
        nonce: ByteArray,
        server_public: ByteArray,
    ): EnvelopeKeys {
        val auth_key = hkdf_expand(randomized_password, nonce + auth_key_label, HASH_LEN)
        val secret_seed = hkdf_expand(randomized_password, nonce + private_key_label, SEED_LEN)
        val client_secret = derive_scalar(secret_seed)
        secret_seed.fill(0)
        val client_public =
            Ristretto255.encode(Ristretto255.multiply(Ristretto255.base_point, client_secret))
        val tag = hmac(
            auth_key,
            nonce + server_public + length_prefixed(server_public) + length_prefixed(client_public),
        )
        return EnvelopeKeys(client_secret, client_public, tag, auth_key)
    }

    private fun randomized_password(
        start: LoginV3Start,
        password: ByteArray,
        evaluated: ByteArray,
    ): ByteArray {
        val evaluated_element = decode_element(evaluated)
        val unblinded = Ristretto255.encode(
            Ristretto255.multiply(evaluated_element, start.blind.modInverse(Ristretto255.group_order)),
        )
        val oprf_output = sha512(
            length_prefixed(password) + length_prefixed(unblinded) + finalize_label,
        )
        unblinded.fill(0)
        val hardened = harden(oprf_output)
        val joined = oprf_output + hardened
        oprf_output.fill(0)
        hardened.fill(0)
        return hkdf_extract(joined).also { joined.fill(0) }
    }

    private fun harden(input: ByteArray): ByteArray {
        val parameters = Argon2Parameters.Builder(Argon2Parameters.ARGON2_id)
            .withVersion(Argon2Parameters.ARGON2_VERSION_13)
            .withMemoryAsKB(KSF_MEMORY_KIB)
            .withIterations(KSF_ITERATIONS)
            .withParallelism(KSF_PARALLELISM)
            .withSalt(ByteArray(KSF_SALT_LEN))
            .build()
        val generator = Argon2BytesGenerator()
        generator.init(parameters)
        val out = ByteArray(input.size)
        generator.generateBytes(input, out)
        return out
    }

    private fun blind_password(password: ByteArray, blind: BigInteger): ByteArray {
        val element = Ristretto255.from_uniform_bytes(
            expand_message(password, hash_to_group_dst, RISTRETTO_UNIFORM_LEN),
        )
        if (Ristretto255.is_identity(element)) throw LoginV3MalformedException()
        return Ristretto255.encode(Ristretto255.multiply(element, blind))
    }

    private fun random_scalar(rng: SeededStream): BigInteger {
        while (true) {
            val wide = rng.next(RISTRETTO_UNIFORM_LEN)
            val scalar = Ristretto255.scalar_from_wide_bytes(wide)
            wide.fill(0)
            if (scalar.signum() != 0) return scalar
        }
    }

    private fun derive_scalar(seed: ByteArray): BigInteger {
        val prefix = seed + length_prefixed(derive_key_pair_info)
        try {
            for (counter in 0 until DERIVE_KEY_ATTEMPTS) {
                val wide = expand_message(
                    prefix + byteArrayOf(counter.toByte()),
                    derive_key_pair_dst,
                    RISTRETTO_UNIFORM_LEN,
                )
                val scalar = Ristretto255.scalar_from_wide_bytes(wide)
                wide.fill(0)
                if (scalar.signum() != 0) return scalar
            }
            throw LoginV3MalformedException()
        } finally {
            prefix.fill(0)
        }
    }

    private fun decode_element(bytes: ByteArray): RistrettoElement {
        val element = Ristretto255.decode(bytes) ?: throw LoginV3MalformedException()
        if (Ristretto255.is_identity(element)) throw LoginV3MalformedException()
        return element
    }

    private fun shared_secret(element: RistrettoElement, scalar: BigInteger): ByteArray =
        Ristretto255.encode(Ristretto255.multiply(element, scalar))

    private fun expand_message(message: ByteArray, dst: ByteArray, length: Int): ByteArray {
        require(length == HASH_LEN && dst.size < 256)
        val dst_prime = dst + byteArrayOf(dst.size.toByte())
        val b0 = sha512(
            ByteArray(HASH_BLOCK_LEN) + message + i2osp(length, 2) + byteArrayOf(0) + dst_prime,
        )
        return sha512(b0 + byteArrayOf(1) + dst_prime)
    }

    private fun expand_label(secret: ByteArray, label: ByteArray, context: ByteArray): ByteArray {
        val full_label = label_prefix + label
        val info = i2osp(HASH_LEN, 2) + i2osp(full_label.size, 1) + full_label +
            i2osp(context.size, 1) + context
        return hkdf_expand(secret, info, HASH_LEN)
    }

    private fun hkdf_extract(input: ByteArray): ByteArray = hmac(ByteArray(HASH_LEN), input)

    private fun hkdf_expand(key: ByteArray, info: ByteArray, length: Int): ByteArray {
        val out = ByteArray(length)
        var previous = ByteArray(0)
        var written = 0
        var counter = 1
        while (written < length) {
            previous = hmac(key, previous + info + byteArrayOf(counter.toByte()))
            val chunk = minOf(previous.size, length - written)
            System.arraycopy(previous, 0, out, written, chunk)
            written += chunk
            counter++
        }
        previous.fill(0)
        return out
    }

    private fun hmac(key: ByteArray, data: ByteArray): ByteArray {
        val mac = Mac.getInstance(HMAC_ALGORITHM)
        mac.init(SecretKeySpec(key, HMAC_ALGORITHM))
        return mac.doFinal(data)
    }

    private fun sha512(data: ByteArray): ByteArray = MessageDigest.getInstance(DIGEST_ALGORITHM).digest(data)

    private fun length_prefixed(data: ByteArray): ByteArray = i2osp(data.size, 2) + data

    private fun i2osp(value: Int, length: Int): ByteArray =
        ByteArray(length) { index -> (value ushr (8 * (length - 1 - index))).toByte() }

    private fun ascii(text: String): ByteArray = text.toByteArray(Charsets.US_ASCII)

    private class SeededStream(seed: ByteArray) {
        private val engine = ChaChaEngine(RNG_ROUNDS)

        init {
            require(seed.size == SEED_LEN)
            engine.init(true, ParametersWithIV(KeyParameter(seed), ByteArray(RNG_NONCE_LEN)))
        }

        fun next(length: Int): ByteArray {
            val out = ByteArray(length)
            engine.processBytes(ByteArray(length), 0, length, out, 0)
            return out
        }
    }
}
