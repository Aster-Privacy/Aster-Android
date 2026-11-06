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
import java.util.Date
import org.bouncycastle.bcpg.ArmoredOutputStream
import org.bouncycastle.bcpg.HashAlgorithmTags
import org.bouncycastle.bcpg.PublicKeyAlgorithmTags
import org.bouncycastle.bcpg.PublicKeyPacket
import org.bouncycastle.bcpg.SymmetricKeyAlgorithmTags
import org.bouncycastle.bcpg.sig.KeyFlags
import org.bouncycastle.crypto.generators.RSAKeyPairGenerator
import org.bouncycastle.crypto.params.RSAKeyGenerationParameters
import org.bouncycastle.openpgp.PGPKeyRingGenerator
import org.bouncycastle.openpgp.PGPPublicKeyRing
import org.bouncycastle.openpgp.PGPSignature
import org.bouncycastle.openpgp.PGPSignatureSubpacketGenerator
import org.bouncycastle.openpgp.operator.bc.BcPBESecretKeyEncryptorBuilder
import org.bouncycastle.openpgp.operator.bc.BcPGPContentSignerBuilder
import org.bouncycastle.openpgp.operator.bc.BcPGPDigestCalculatorProvider
import org.bouncycastle.openpgp.operator.bc.BcPGPKeyPair

object TestRsaKeys {

    fun generate(user_id: String, passphrase: CharArray): PgpKeyPairResult {
        val rng = SecureRandom()
        val now = Date()
        fun rsa_pair(): BcPGPKeyPair {
            val gen = RSAKeyPairGenerator()
            gen.init(RSAKeyGenerationParameters(BigInteger.valueOf(65537), rng, 2048, 12))
            return BcPGPKeyPair(PublicKeyPacket.VERSION_4, PublicKeyAlgorithmTags.RSA_GENERAL, gen.generateKeyPair(), now)
        }
        val master = rsa_pair()
        val sub = rsa_pair()
        val ring_gen = PGPKeyRingGenerator(
            PGPSignature.POSITIVE_CERTIFICATION,
            master,
            user_id,
            BcPGPDigestCalculatorProvider().get(HashAlgorithmTags.SHA1),
            PGPSignatureSubpacketGenerator().apply {
                setKeyFlags(false, KeyFlags.SIGN_DATA or KeyFlags.CERTIFY_OTHER)
            }.generate(),
            null,
            BcPGPContentSignerBuilder(master.publicKey.algorithm, HashAlgorithmTags.SHA256),
            BcPBESecretKeyEncryptorBuilder(
                SymmetricKeyAlgorithmTags.AES_256,
                BcPGPDigestCalculatorProvider().get(HashAlgorithmTags.SHA256),
            ).build(passphrase),
        )
        ring_gen.addSubKey(
            sub,
            PGPSignatureSubpacketGenerator().apply {
                setKeyFlags(false, KeyFlags.ENCRYPT_COMMS or KeyFlags.ENCRYPT_STORAGE)
            }.generate(),
            null,
        )
        val secret_ring = ring_gen.generateSecretKeyRing()
        val public_ring = PGPPublicKeyRing(secret_ring.publicKeys.asSequence().toList())
        val private_out = ByteArrayOutputStream()
        ArmoredOutputStream(private_out).use { secret_ring.encode(it) }
        val public_out = ByteArrayOutputStream()
        ArmoredOutputStream(public_out).use { public_ring.encode(it) }
        return PgpKeyPairResult(
            armored_public_key = public_out.toString(Charsets.UTF_8.name()),
            armored_private_key = private_out.toString(Charsets.UTF_8.name()),
            fingerprint = "%040x".format(BigInteger(1, master.publicKey.fingerprint)),
            key_id = "%016x".format(master.publicKey.keyID),
        )
    }
}
