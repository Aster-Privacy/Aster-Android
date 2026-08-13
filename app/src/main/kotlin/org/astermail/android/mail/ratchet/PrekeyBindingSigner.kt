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

package org.astermail.android.mail.ratchet

import java.io.ByteArrayOutputStream
import org.bouncycastle.bcpg.ArmoredOutputStream
import org.bouncycastle.bcpg.BCPGOutputStream
import org.bouncycastle.bcpg.HashAlgorithmTags
import org.bouncycastle.openpgp.PGPSecretKey
import org.bouncycastle.openpgp.PGPSecretKeyRing
import org.bouncycastle.openpgp.PGPSignature
import org.bouncycastle.openpgp.PGPSignatureGenerator
import org.bouncycastle.openpgp.PGPSignatureSubpacketGenerator
import org.bouncycastle.openpgp.PGPUtil
import org.bouncycastle.openpgp.operator.bc.BcKeyFingerprintCalculator
import org.bouncycastle.openpgp.operator.bc.BcPBESecretKeyDecryptorBuilder
import org.bouncycastle.openpgp.operator.bc.BcPGPContentSignerBuilder
import org.bouncycastle.openpgp.operator.bc.BcPGPDigestCalculatorProvider

object PrekeyBindingSigner {
    private const val canonical_prefix = "aster-ratchet-prekey-v1:"
    private const val armored_private_key_header = "-----BEGIN PGP PRIVATE KEY"

    fun canonical_binding(kem_identity_key_b64: String, signed_prekey_b64: String): String =
        "$canonical_prefix$kem_identity_key_b64.$signed_prekey_b64"

    fun looks_like_armored_private_key(value: String): Boolean =
        value.trimStart().startsWith(armored_private_key_header)

    fun sign_cleartext(armored_secret_key: String, passphrase: CharArray, text: String): String {
        val ring = PGPSecretKeyRing(
            PGPUtil.getDecoderStream(armored_secret_key.byteInputStream(Charsets.UTF_8)),
            BcKeyFingerprintCalculator(),
        )
        val secret_key = signing_secret_key(ring)
        val private_key = secret_key.extractPrivateKey(
            BcPBESecretKeyDecryptorBuilder(BcPGPDigestCalculatorProvider()).build(passphrase),
        )

        val generator = PGPSignatureGenerator(
            BcPGPContentSignerBuilder(secret_key.publicKey.algorithm, HashAlgorithmTags.SHA256),
        )
        generator.init(PGPSignature.CANONICAL_TEXT_DOCUMENT, private_key)
        val subpackets = PGPSignatureSubpacketGenerator()
        subpackets.setIssuerKeyID(false, secret_key.keyID)
        generator.setHashedSubpackets(subpackets.generate())

        val text_bytes = text.toByteArray(Charsets.UTF_8)
        generator.update(text_bytes)

        val out = ByteArrayOutputStream()
        val armor = ArmoredOutputStream(out)
        armor.beginClearText(HashAlgorithmTags.SHA256)
        armor.write(text_bytes)
        armor.write('\n'.code)
        armor.endClearText()
        val packet_out = BCPGOutputStream(armor)
        generator.generate().encode(packet_out)
        packet_out.flush()
        armor.close()

        val armored = out.toString(Charsets.UTF_8.name()).replace("\r\n", "\n")
        return if (armored.endsWith("\n")) armored else armored + "\n"
    }

    private fun signing_secret_key(ring: PGPSecretKeyRing): PGPSecretKey {
        var fallback: PGPSecretKey? = null
        for (key in ring.secretKeys) {
            if (!key.isSigningKey) continue
            if (key.publicKey.isMasterKey) return key
            if (fallback == null) fallback = key
        }
        return fallback ?: throw IllegalArgumentException("no signing-capable key in ring")
    }
}
