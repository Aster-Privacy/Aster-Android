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

package org.astermail.android.crypto.ratchet

import android.util.Base64
import org.bouncycastle.crypto.digests.SHA256Digest
import org.bouncycastle.crypto.generators.HKDFBytesGenerator
import org.bouncycastle.crypto.params.HKDFParameters
import java.math.BigInteger
import java.security.AlgorithmParameters
import java.security.KeyFactory
import java.security.KeyPairGenerator
import java.security.MessageDigest
import java.security.SecureRandom
import java.security.spec.ECGenParameterSpec
import java.security.spec.ECParameterSpec
import java.security.spec.ECPoint
import java.security.spec.ECPrivateKeySpec
import java.security.spec.ECPublicKeySpec
import javax.crypto.KeyAgreement
import javax.crypto.Cipher
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.SecretKeySpec

object RatchetCrypto {

    private val secp256r1: ECParameterSpec by lazy {
        val params = AlgorithmParameters.getInstance("EC")
        params.init(ECGenParameterSpec("secp256r1"))
        params.getParameterSpec(ECParameterSpec::class.java)
    }

    private val ec_factory: KeyFactory by lazy { KeyFactory.getInstance("EC") }
    private val secure_random = SecureRandom()

    fun hkdf_sha256(ikm: ByteArray, salt: ByteArray, info: ByteArray, length: Int): ByteArray {
        val gen = HKDFBytesGenerator(SHA256Digest())
        gen.init(HKDFParameters(ikm, salt, info))
        val out = ByteArray(length)
        gen.generateBytes(out, 0, length)
        return out
    }

    fun sha256(input: ByteArray): ByteArray =
        MessageDigest.getInstance("SHA-256").digest(input)

    fun aes_gcm_decrypt(
        ciphertext: ByteArray,
        key: ByteArray,
        nonce: ByteArray,
        aad: ByteArray? = null,
    ): ByteArray {
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.DECRYPT_MODE, SecretKeySpec(key, "AES"), GCMParameterSpec(128, nonce))
        if (aad != null) cipher.updateAAD(aad)
        return cipher.doFinal(ciphertext)
    }

    fun aes_gcm_encrypt(
        plaintext: ByteArray,
        key: ByteArray,
        nonce: ByteArray,
        aad: ByteArray? = null,
    ): ByteArray {
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.ENCRYPT_MODE, SecretKeySpec(key, "AES"), GCMParameterSpec(128, nonce))
        if (aad != null) cipher.updateAAD(aad)
        return cipher.doFinal(plaintext)
    }

    fun random_bytes(length: Int): ByteArray {
        val out = ByteArray(length)
        secure_random.nextBytes(out)
        return out
    }

    fun ml_kem_768_decapsulate(ciphertext: ByteArray, secret_key: ByteArray): ByteArray {
        val params = org.bouncycastle.pqc.crypto.mlkem.MLKEMParameters.ml_kem_768
        val priv = org.bouncycastle.pqc.crypto.mlkem.MLKEMPrivateKeyParameters(params, secret_key)
        val extractor = org.bouncycastle.pqc.crypto.mlkem.MLKEMExtractor(priv)
        return extractor.extractSecret(ciphertext)
    }

    data class MlKemEncapsulation(val ciphertext: ByteArray, val shared_secret: ByteArray)

    fun ml_kem_768_encapsulate(public_key: ByteArray): MlKemEncapsulation {
        val params = org.bouncycastle.pqc.crypto.mlkem.MLKEMParameters.ml_kem_768
        val pub = org.bouncycastle.pqc.crypto.mlkem.MLKEMPublicKeyParameters(params, public_key)
        val generator = org.bouncycastle.pqc.crypto.mlkem.MLKEMGenerator(secure_random)
        val encap = generator.generateEncapsulated(pub)
        return MlKemEncapsulation(encap.encapsulation, encap.secret)
    }

    fun jwk_extract_d_bytes(jwk_json: String): ByteArray {
        val obj = org.json.JSONObject(jwk_json)
        return b64url_decode(obj.getString("d"))
    }

    fun raw_d_to_private(d_bytes: ByteArray): java.security.PrivateKey {
        val d = BigInteger(1, d_bytes)
        return ec_factory.generatePrivate(ECPrivateKeySpec(d, secp256r1))
    }

    fun private_to_raw_d(private_key: java.security.PrivateKey): ByteArray {
        val ec = private_key as java.security.interfaces.ECPrivateKey
        return int_to_fixed(ec.s, 32)
    }

    fun looks_like_jwk(s: String): Boolean = s.trimStart().startsWith("{")

    data class EcKeyPair(
        val private_key: java.security.PrivateKey,
        val public_key: java.security.PublicKey,
        val public_raw: ByteArray,
    )

    fun generate_p256_keypair(): EcKeyPair {
        val gen = KeyPairGenerator.getInstance("EC")
        gen.initialize(ECGenParameterSpec("secp256r1"), secure_random)
        val kp = gen.generateKeyPair()
        val raw = public_key_to_raw(kp.public as java.security.interfaces.ECPublicKey)
        return EcKeyPair(kp.private, kp.public, raw)
    }

    fun parse_p256_private_jwk(jwk_json: String): java.security.PrivateKey {
        val obj = org.json.JSONObject(jwk_json)
        val d_bytes = b64url_decode(obj.getString("d"))
        val d = BigInteger(1, d_bytes)
        return ec_factory.generatePrivate(ECPrivateKeySpec(d, secp256r1))
    }

    fun parse_p256_public_jwk(jwk_json: String): java.security.PublicKey {
        val obj = org.json.JSONObject(jwk_json)
        val x = BigInteger(1, b64url_decode(obj.getString("x")))
        val y = BigInteger(1, b64url_decode(obj.getString("y")))
        return ec_factory.generatePublic(ECPublicKeySpec(ECPoint(x, y), secp256r1))
    }

    fun parse_p256_public_raw(raw: ByteArray): java.security.PublicKey {
        require(raw.size == 65 && raw[0] == 0x04.toByte()) { "expected uncompressed P-256 SEC1 (65 bytes, 0x04 prefix), got ${raw.size}" }
        val x = BigInteger(1, raw.copyOfRange(1, 33))
        val y = BigInteger(1, raw.copyOfRange(33, 65))
        return ec_factory.generatePublic(ECPublicKeySpec(ECPoint(x, y), secp256r1))
    }

    fun public_key_to_raw(pub: java.security.interfaces.ECPublicKey): ByteArray {
        val point = pub.w
        val x = int_to_fixed(point.affineX, 32)
        val y = int_to_fixed(point.affineY, 32)
        val out = ByteArray(65)
        out[0] = 0x04
        System.arraycopy(x, 0, out, 1, 32)
        System.arraycopy(y, 0, out, 33, 32)
        return out
    }

    fun ecdh(private_key: java.security.PrivateKey, public_key: java.security.PublicKey): ByteArray {
        val ka = KeyAgreement.getInstance("ECDH")
        ka.init(private_key)
        ka.doPhase(public_key, true)
        return ka.generateSecret()
    }

    private fun int_to_fixed(value: BigInteger, length: Int): ByteArray {
        val raw = value.toByteArray()
        if (raw.size == length) return raw
        if (raw.size == length + 1 && raw[0] == 0.toByte()) return raw.copyOfRange(1, raw.size)
        if (raw.size < length) {
            val out = ByteArray(length)
            System.arraycopy(raw, 0, out, length - raw.size, raw.size)
            return out
        }
        throw IllegalStateException("int too large for fixed-length encoding")
    }

    fun b64_encode(bytes: ByteArray): String = Base64.encodeToString(bytes, Base64.NO_WRAP)
    fun b64_decode(s: String): ByteArray = Base64.decode(s, Base64.DEFAULT)

    fun b64url_decode(s: String): ByteArray =
        Base64.decode(s, Base64.URL_SAFE or Base64.NO_WRAP or Base64.NO_PADDING)

    fun b64url_encode(bytes: ByteArray): String =
        Base64.encodeToString(bytes, Base64.URL_SAFE or Base64.NO_WRAP or Base64.NO_PADDING)
}
