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
import java.math.BigInteger
import java.security.KeyPairGenerator
import java.security.Security
import java.util.Date
import org.bouncycastle.bcpg.ArmoredOutputStream
import org.bouncycastle.bcpg.HashAlgorithmTags
import org.bouncycastle.bcpg.SymmetricKeyAlgorithmTags
import org.bouncycastle.bcpg.sig.Features
import org.bouncycastle.bcpg.sig.KeyFlags
import org.bouncycastle.jce.provider.BouncyCastleProvider
import org.bouncycastle.openpgp.PGPKeyRingGenerator
import org.bouncycastle.openpgp.PGPPublicKey
import org.bouncycastle.openpgp.PGPSignature
import org.bouncycastle.openpgp.PGPSignatureSubpacketGenerator
import org.bouncycastle.openpgp.operator.jcajce.JcaPGPContentSignerBuilder
import org.bouncycastle.openpgp.operator.jcajce.JcaPGPDigestCalculatorProviderBuilder
import org.bouncycastle.openpgp.operator.jcajce.JcaPGPKeyPair
import org.bouncycastle.openpgp.operator.jcajce.JcePBESecretKeyEncryptorBuilder

data class PgpKeyPairResult(
    val armored_public_key: String,
    val armored_private_key: String,
    val fingerprint: String,
    val key_id: String,
)

object PgpKeyGenerator {

    init {
        Security.removeProvider(BouncyCastleProvider.PROVIDER_NAME)
        Security.addProvider(BouncyCastleProvider())
    }

    fun generate(
        name: String,
        email: String,
        passphrase: CharArray,
    ): PgpKeyPairResult {
        val provider = BouncyCastleProvider.PROVIDER_NAME
        val rsa_gen = KeyPairGenerator.getInstance("RSA", provider)
        rsa_gen.initialize(4096)

        val master_pair = rsa_gen.generateKeyPair()
        val sub_pair = rsa_gen.generateKeyPair()

        val now = Date()
        val master_pgp = JcaPGPKeyPair(PGPPublicKey.RSA_GENERAL, master_pair, now)
        val sub_pgp = JcaPGPKeyPair(PGPPublicKey.RSA_GENERAL, sub_pair, now)

        val digest_calc = JcaPGPDigestCalculatorProviderBuilder()
            .setProvider(provider)
            .build()
            .get(HashAlgorithmTags.SHA256)

        val signer = JcaPGPContentSignerBuilder(
            master_pgp.publicKey.algorithm,
            HashAlgorithmTags.SHA256,
        ).setProvider(provider)

        val encryptor = JcePBESecretKeyEncryptorBuilder(
            SymmetricKeyAlgorithmTags.AES_256,
            digest_calc,
        ).setProvider(provider)
            .build(passphrase)

        val master_subpackets = PGPSignatureSubpacketGenerator().apply {
            setKeyFlags(false, KeyFlags.SIGN_DATA or KeyFlags.CERTIFY_OTHER)
            setPreferredHashAlgorithms(
                false,
                intArrayOf(
                    HashAlgorithmTags.SHA256,
                    HashAlgorithmTags.SHA384,
                    HashAlgorithmTags.SHA512,
                ),
            )
            setPreferredSymmetricAlgorithms(
                false,
                intArrayOf(
                    SymmetricKeyAlgorithmTags.AES_256,
                    SymmetricKeyAlgorithmTags.AES_192,
                    SymmetricKeyAlgorithmTags.AES_128,
                ),
            )
            setFeature(false, Features.FEATURE_MODIFICATION_DETECTION)
        }

        val sub_subpackets = PGPSignatureSubpacketGenerator().apply {
            setKeyFlags(false, KeyFlags.ENCRYPT_COMMS or KeyFlags.ENCRYPT_STORAGE)
        }

        val key_ring_gen = PGPKeyRingGenerator(
            PGPSignature.POSITIVE_CERTIFICATION,
            master_pgp,
            "$name <$email>",
            digest_calc,
            master_subpackets.generate(),
            null,
            signer,
            encryptor,
        )
        key_ring_gen.addSubKey(sub_pgp, sub_subpackets.generate(), null)

        val secret_ring = key_ring_gen.generateSecretKeyRing()
        val public_ring = key_ring_gen.generatePublicKeyRing()

        val private_out = ByteArrayOutputStream()
        ArmoredOutputStream(private_out).use { secret_ring.encode(it) }

        val public_out = ByteArrayOutputStream()
        ArmoredOutputStream(public_out).use { public_ring.encode(it) }

        val fingerprint = String.format(
            "%040x",
            BigInteger(1, master_pgp.publicKey.fingerprint),
        )

        val key_id = String.format(java.util.Locale.US, "%016x", master_pgp.publicKey.keyID)

        return PgpKeyPairResult(
            armored_public_key = public_out.toString(Charsets.UTF_8.name()),
            armored_private_key = private_out.toString(Charsets.UTF_8.name()),
            fingerprint = fingerprint,
            key_id = key_id,
        )
    }
}
