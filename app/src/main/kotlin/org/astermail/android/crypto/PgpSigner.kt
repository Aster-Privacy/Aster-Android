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
import org.bouncycastle.bcpg.ArmoredOutputStream
import org.bouncycastle.bcpg.HashAlgorithmTags
import org.bouncycastle.jce.provider.BouncyCastleProvider
import org.bouncycastle.openpgp.PGPPrivateKey
import org.bouncycastle.openpgp.PGPSecretKey
import org.bouncycastle.openpgp.PGPSecretKeyRing
import org.bouncycastle.openpgp.PGPSecretKeyRingCollection
import org.bouncycastle.openpgp.PGPSignature
import org.bouncycastle.openpgp.PGPSignatureGenerator
import org.bouncycastle.openpgp.PGPUtil
import org.bouncycastle.openpgp.operator.jcajce.JcaKeyFingerprintCalculator
import org.bouncycastle.openpgp.operator.jcajce.JcaPGPContentSignerBuilder
import org.bouncycastle.openpgp.operator.jcajce.JcePBESecretKeyDecryptorBuilder

data class DetachedSignature(
    val signature: String,
    val micalg: String,
)

object PgpSigner {

    private const val MICALG_SHA512 = "pgp-sha512"

    init {
        java.security.Security.removeProvider(BouncyCastleProvider.PROVIDER_NAME)
        java.security.Security.addProvider(BouncyCastleProvider())
    }

    fun sign_detached(
        data: ByteArray,
        armored_private_key: String,
        passphrase: CharArray,
    ): DetachedSignature? {
        return try {
            val secret_key = select_signing_key(armored_private_key) ?: return null
            val decryptor = JcePBESecretKeyDecryptorBuilder()
                .setProvider(BouncyCastleProvider.PROVIDER_NAME)
                .build(passphrase)
            val private_key: PGPPrivateKey = secret_key.extractPrivateKey(decryptor)

            val generator = PGPSignatureGenerator(
                JcaPGPContentSignerBuilder(
                    secret_key.publicKey.algorithm,
                    HashAlgorithmTags.SHA512,
                ).setProvider(BouncyCastleProvider.PROVIDER_NAME),
            )
            generator.init(PGPSignature.BINARY_DOCUMENT, private_key)
            generator.update(data)

            val armored_output = ByteArrayOutputStream()
            ArmoredOutputStream(armored_output).use { armored ->
                generator.generate().encode(armored)
            }

            DetachedSignature(
                signature = armored_output.toString(Charsets.UTF_8.name()),
                micalg = MICALG_SHA512,
            )
        } catch (_: Throwable) {
            null
        }
    }

    private fun select_signing_key(armored_private_key: String): PGPSecretKey? {
        return try {
            val stream = PGPUtil.getDecoderStream(
                ByteArrayInputStream(armored_private_key.toByteArray(Charsets.UTF_8)),
            )
            val rings = PGPSecretKeyRingCollection(stream, JcaKeyFingerprintCalculator())
            var fallback: PGPSecretKey? = null
            for (ring in rings) {
                for (key in (ring as PGPSecretKeyRing)) {
                    if (!key.isSigningKey) continue
                    if (key.isMasterKey) return key
                    if (fallback == null) fallback = key
                }
            }
            fallback
        } catch (_: Throwable) {
            null
        }
    }
}
