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
import java.security.SecureRandom
import java.security.Security
import java.util.Date
import java.util.Locale
import org.bouncycastle.bcpg.ArmoredOutputStream
import org.bouncycastle.bcpg.HashAlgorithmTags
import org.bouncycastle.bcpg.PublicKeyAlgorithmTags
import org.bouncycastle.bcpg.PublicKeyPacket
import org.bouncycastle.bcpg.SymmetricKeyAlgorithmTags
import org.bouncycastle.bcpg.sig.Features
import org.bouncycastle.bcpg.sig.KeyFlags
import org.bouncycastle.crypto.generators.Ed25519KeyPairGenerator
import org.bouncycastle.crypto.generators.X25519KeyPairGenerator
import org.bouncycastle.crypto.params.Ed25519KeyGenerationParameters
import org.bouncycastle.crypto.params.X25519KeyGenerationParameters
import org.bouncycastle.jce.provider.BouncyCastleProvider
import org.bouncycastle.openpgp.PGPKeyRingGenerator
import org.bouncycastle.openpgp.PGPSignature
import org.bouncycastle.openpgp.PGPSignatureSubpacketGenerator
import org.bouncycastle.openpgp.operator.bc.BcPBESecretKeyEncryptorBuilder
import org.bouncycastle.openpgp.operator.bc.BcPGPContentSignerBuilder
import org.bouncycastle.openpgp.operator.bc.BcPGPDigestCalculatorProvider
import org.bouncycastle.openpgp.operator.bc.BcPGPKeyPair

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
        val rng = SecureRandom()
        val now = Date()

        val master_gen = Ed25519KeyPairGenerator()
        master_gen.init(Ed25519KeyGenerationParameters(rng))
        val master_raw = master_gen.generateKeyPair()

        val sub_gen = X25519KeyPairGenerator()
        sub_gen.init(X25519KeyGenerationParameters(rng))
        val sub_raw = sub_gen.generateKeyPair()

        val master_pgp = BcPGPKeyPair(
            PublicKeyPacket.VERSION_4,
            PublicKeyAlgorithmTags.EDDSA_LEGACY,
            master_raw,
            now,
        )
        val sub_pgp = BcPGPKeyPair(
            PublicKeyPacket.VERSION_4,
            PublicKeyAlgorithmTags.ECDH,
            sub_raw,
            now,
        )

        val checksum_calc = BcPGPDigestCalculatorProvider().get(HashAlgorithmTags.SHA1)
        val s2k_calc = BcPGPDigestCalculatorProvider().get(HashAlgorithmTags.SHA256)

        val signer = BcPGPContentSignerBuilder(
            master_pgp.publicKey.algorithm,
            HashAlgorithmTags.SHA512,
        )

        val encryptor = BcPBESecretKeyEncryptorBuilder(
            SymmetricKeyAlgorithmTags.AES_256,
            s2k_calc,
        ).build(passphrase)

        val master_subpackets = PGPSignatureSubpacketGenerator().apply {
            setKeyFlags(false, KeyFlags.SIGN_DATA or KeyFlags.CERTIFY_OTHER)
            setPreferredHashAlgorithms(
                false,
                intArrayOf(
                    HashAlgorithmTags.SHA512,
                    HashAlgorithmTags.SHA384,
                    HashAlgorithmTags.SHA256,
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
            checksum_calc,
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
            Locale.US,
            "%040x",
            BigInteger(1, master_pgp.publicKey.fingerprint),
        )

        val key_id = String.format(Locale.US, "%016x", master_pgp.publicKey.keyID)

        return PgpKeyPairResult(
            armored_public_key = public_out.toString(Charsets.UTF_8.name()),
            armored_private_key = private_out.toString(Charsets.UTF_8.name()),
            fingerprint = fingerprint,
            key_id = key_id,
        )
    }
}
