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
import org.bouncycastle.openpgp.PGPPrivateKey
import org.bouncycastle.openpgp.PGPPublicKey
import org.bouncycastle.openpgp.PGPPublicKeyRing
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
    val private_key = unlock_master(master, passphrase)
    val demoted = demote_primary_user_ids(master.publicKey, private_key, wanted)
    val updated_public = certify_user_id(demoted, private_key, "$label <$email>", primary = true)

    return encode_with_public_key(ring, master, updated_public)
}

fun pgp_key_user_emails(armored_key: String): List<String> = try {
    val master = if (armored_key.contains("PRIVATE KEY BLOCK")) {
        read_secret_ring(armored_key).publicKey
    } else {
        PGPPublicKeyRing(
            PGPUtil.getDecoderStream(armored_key.byteInputStream()),
            BcKeyFingerprintCalculator(),
        ).publicKey
    }
    user_ids_of(master).map { user_id_email(it) }
} catch (_: Throwable) {
    emptyList()
}

fun account_addresses_missing_from_pgp_key(armored_key: String, account_email: String): List<String> {
    val present = pgp_key_user_emails(armored_key).toSet()
    return identity_addresses_for(account_email).filter { it.lowercase(Locale.ROOT) !in present }
}

fun add_account_addresses_to_pgp_key(
    armored_private_key: String,
    passphrase: CharArray,
    display_name: String,
    account_email: String,
): String? {
    val wanted = identity_addresses_for(account_email)
    if (wanted.size < 2) return null

    val ring = read_secret_ring(armored_private_key)
    val master = ring.secretKey
    if (!master.isMasterKey) return null

    val present = user_ids_of(master.publicKey).map { user_id_email(it) }.toSet()
    val missing = wanted.filter { it.lowercase(Locale.ROOT) !in present }
    if (missing.isEmpty()) return null

    val private_key = unlock_master(master, passphrase)
    val primary_address = wanted.first()
    var updated_public = master.publicKey

    missing.filter { it != primary_address }.forEach { address ->
        updated_public = certify_user_id(
            updated_public,
            private_key,
            "${uid_label(display_name, address)} <$address>",
            primary = false,
        )
    }

    updated_public = demote_primary_user_ids(
        updated_public,
        private_key,
        primary_address.lowercase(Locale.ROOT),
    )

    val primary_user_id = user_ids_of(updated_public)
        .firstOrNull { user_id_email(it) == primary_address.lowercase(Locale.ROOT) }
        ?: "${uid_label(display_name, primary_address)} <$primary_address>"
    updated_public = certify_user_id(updated_public, private_key, primary_user_id, primary = true)

    return encode_with_public_key(ring, master, updated_public)
}

private fun demote_primary_user_ids(
    key: PGPPublicKey,
    private_key: PGPPrivateKey,
    keep_email: String,
): PGPPublicKey {
    var updated = key
    user_ids_of(key)
        .filter { user_id_email(it) != keep_email }
        .filter { latest_self_signature_is_primary(key, it) }
        .forEach { updated = certify_user_id(updated, private_key, it, primary = false) }
    return updated
}

private fun latest_self_signature_is_primary(key: PGPPublicKey, user_id: String): Boolean =
    key.getSignaturesForID(user_id)?.asSequence()
        ?.filter { it.keyID == key.keyID }
        ?.sortedBy { it.creationTime }
        ?.lastOrNull()
        ?.hashedSubPackets
        ?.isPrimaryUserID == true

private fun uid_label(display_name: String, address: String): String =
    display_name.trim().ifEmpty { address.substringBefore('@') }

private fun unlock_master(master: PGPSecretKey, passphrase: CharArray): PGPPrivateKey =
    master.extractPrivateKey(
        BcPBESecretKeyDecryptorBuilder(BcPGPDigestCalculatorProvider()).build(passphrase),
    )

private fun encode_with_public_key(
    ring: PGPSecretKeyRing,
    master: PGPSecretKey,
    updated_public: PGPPublicKey,
): String {
    val updated_secret = PGPSecretKey.replacePublicKey(master, updated_public)
    val updated_ring = PGPSecretKeyRing.insertSecretKey(ring, updated_secret)

    val out = ByteArrayOutputStream()
    ArmoredOutputStream(out).use { updated_ring.encode(it) }

    return out.toString(Charsets.UTF_8.name())
}

private fun certify_user_id(
    public_key: PGPPublicKey,
    private_key: PGPPrivateKey,
    user_id: String,
    primary: Boolean,
): PGPPublicKey {
    val signature_generator = PGPSignatureGenerator(
        BcPGPContentSignerBuilder(public_key.algorithm, HashAlgorithmTags.SHA512),
        public_key,
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
            if (primary) setPrimaryUserID(false, true)
        }.generate(),
    )

    val certification = signature_generator.generateCertification(user_id, public_key)
    return PGPPublicKey.addCertification(public_key, user_id, certification)
}
