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
import java.util.Locale
import org.bouncycastle.bcpg.ArmoredOutputStream
import org.bouncycastle.bcpg.HashAlgorithmTags
import org.bouncycastle.bcpg.SymmetricKeyAlgorithmTags
import org.bouncycastle.bcpg.sig.Features
import org.bouncycastle.bcpg.sig.KeyFlags
import org.bouncycastle.openpgp.PGPPublicKey
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

private fun read_secret_ring(armored_private_key: String): PGPSecretKeyRing =
    PGPSecretKeyRing(
        PGPUtil.getDecoderStream(armored_private_key.byteInputStream()),
        BcKeyFingerprintCalculator(),
    )

private fun user_ids_of(key: PGPPublicKey): List<String> =
    key.userIDs.asSequence().toList()

private fun user_id_email(user_id: String): String {
    val open = user_id.lastIndexOf('<')
    val close = user_id.lastIndexOf('>')
    if (open in 0 until close) return user_id.substring(open + 1, close).lowercase(Locale.ROOT)
    return user_id.trim().lowercase(Locale.ROOT)
}

fun pgp_key_covers_address(armored_private_key: String, email: String): Boolean = try {
    val wanted = email.trim().lowercase(Locale.ROOT)
    user_ids_of(read_secret_ring(armored_private_key).publicKey).any { user_id_email(it) == wanted }
} catch (_: Throwable) {
    false
}

fun add_address_to_pgp_key(
    armored_private_key: String,
    passphrase: CharArray,
    display_name: String,
    email: String,
): String? {
    val ring = read_secret_ring(armored_private_key)
    val master = ring.secretKey
    if (!master.isMasterKey) return null

    val wanted = email.trim().lowercase(Locale.ROOT)
    if (user_ids_of(master.publicKey).any { user_id_email(it) == wanted }) return null

    val label = display_name.trim().ifEmpty { email }
    val user_id = "$label <$email>"

    val private_key = master.extractPrivateKey(
        BcPBESecretKeyDecryptorBuilder(BcPGPDigestCalculatorProvider()).build(passphrase),
    )

    val signature_generator = PGPSignatureGenerator(
        BcPGPContentSignerBuilder(master.publicKey.algorithm, HashAlgorithmTags.SHA512),
        master.publicKey,
    )
    signature_generator.init(PGPSignature.POSITIVE_CERTIFICATION, private_key)
    signature_generator.setHashedSubpackets(
        PGPSignatureSubpacketGenerator().apply {
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
            setPrimaryUserID(false, true)
        }.generate(),
    )

    val certification = signature_generator.generateCertification(user_id, master.publicKey)
    val updated_public = PGPPublicKey.addCertification(master.publicKey, user_id, certification)
    val updated_secret = PGPSecretKey.replacePublicKey(master, updated_public)
    val updated_ring = PGPSecretKeyRing.insertSecretKey(ring, updated_secret)

    val out = ByteArrayOutputStream()
    ArmoredOutputStream(out).use { updated_ring.encode(it) }

    return out.toString(Charsets.UTF_8.name())
}
