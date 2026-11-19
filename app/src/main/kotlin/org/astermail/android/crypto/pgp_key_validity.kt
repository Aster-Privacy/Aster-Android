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

import org.bouncycastle.bcpg.SignatureSubpacketTags
import org.bouncycastle.bcpg.sig.KeyFlags
import org.bouncycastle.jce.provider.BouncyCastleProvider
import org.bouncycastle.openpgp.PGPPublicKey
import org.bouncycastle.openpgp.PGPSignature
import org.bouncycastle.openpgp.operator.jcajce.JcaPGPContentVerifierBuilderProvider

data class PgpRevocation(val reason: Int?, val created_at_ms: Long)

object PgpKeyValidity {
    private const val reason_superseded = 1
    private const val reason_retired = 3

    fun is_soft_revocation(reason: Int?): Boolean = reason == reason_superseded || reason == reason_retired

    fun revoked_at(revocations: List<PgpRevocation>, at_ms: Long): Boolean =
        revocations.any { revocation ->
            !is_soft_revocation(revocation.reason) || at_ms > revocation.created_at_ms
        }

    fun verified_revocations(primary: PGPPublicKey, key: PGPPublicKey): List<PgpRevocation> {
        val type = if (key.isMasterKey) PGPSignature.KEY_REVOCATION else PGPSignature.SUBKEY_REVOCATION
        val found = mutableListOf<PgpRevocation>()
        val signatures = runCatching { key.getSignaturesOfType(type) }.getOrNull() ?: return found
        while (signatures.hasNext()) {
            val signature = signatures.next() as? PGPSignature ?: continue
            if (signature.keyID != primary.keyID) continue
            val verified = runCatching {
                signature.init(JcaPGPContentVerifierBuilderProvider().setProvider(BouncyCastleProvider.PROVIDER_NAME), primary)
                if (key.isMasterKey) signature.verifyCertification(primary) else signature.verifyCertification(primary, key)
            }.getOrDefault(false)
            if (!verified) continue
            val reason = runCatching { signature.hashedSubPackets?.revocationReason?.revocationReason?.toInt() }.getOrNull()
            found += PgpRevocation(reason, signature.creationTime.time)
        }
        return found
    }

    fun key_revoked_at(primary: PGPPublicKey, key: PGPPublicKey, at_ms: Long): Boolean {
        val revocations = verified_revocations(primary, primary) +
            if (key.isMasterKey) emptyList() else verified_revocations(primary, key)
        return revoked_at(revocations, at_ms)
    }

    fun key_flags(primary: PGPPublicKey, key: PGPPublicKey): Int? {
        val self_signatures = key.signatures.asSequence()
            .filterIsInstance<PGPSignature>()
            .filter { it.keyID == primary.keyID }
            .filter { signature ->
                if (key.isMasterKey) {
                    signature.isCertification || signature.signatureType == PGPSignature.DIRECT_KEY
                } else {
                    signature.signatureType == PGPSignature.SUBKEY_BINDING
                }
            }
            .filter { flags_of(it) != null }
        return self_signatures.maxByOrNull { it.creationTime }?.let(::flags_of)
    }

    private fun verified_subkey_bindings(primary: PGPPublicKey, subkey: PGPPublicKey): List<PGPSignature> =
        runCatching { subkey.getSignaturesOfType(PGPSignature.SUBKEY_BINDING).asSequence().toList() }
            .getOrDefault(emptyList())
            .filterIsInstance<PGPSignature>()
            .filter { it.keyID == primary.keyID }
            .filter { signature ->
                runCatching {
                    signature.init(JcaPGPContentVerifierBuilderProvider().setProvider(BouncyCastleProvider.PROVIDER_NAME), primary)
                    signature.verifyCertification(primary, subkey)
                }.getOrDefault(false)
            }

    fun has_valid_binding(primary: PGPPublicKey, subkey: PGPPublicKey): Boolean =
        verified_subkey_bindings(primary, subkey).isNotEmpty()

    private fun carries_back_signature(binding: PGPSignature, primary: PGPPublicKey, subkey: PGPPublicKey): Boolean =
        listOfNotNull(binding.hashedSubPackets, binding.unhashedSubPackets)
            .asSequence()
            .flatMap { packets ->
                runCatching { packets.embeddedSignatures.toList() }.getOrDefault(emptyList()).asSequence()
            }
            .filter { it.signatureType == PGPSignature.PRIMARYKEY_BINDING }
            .any { embedded ->
                runCatching {
                    embedded.init(
                        JcaPGPContentVerifierBuilderProvider().setProvider(BouncyCastleProvider.PROVIDER_NAME),
                        subkey,
                    )
                    embedded.verifyCertification(primary, subkey)
                }.getOrDefault(false)
            }

    fun signs_data(primary: PGPPublicKey, key: PGPPublicKey): Boolean {
        if (key.isMasterKey) return allows_signing(key_flags(primary, key))
        val bindings = verified_subkey_bindings(primary, key)
        val latest = bindings.maxByOrNull { it.creationTime } ?: return false
        if (!allows_signing(flags_of(latest))) return false
        return bindings.any { allows_signing(flags_of(it)) && carries_back_signature(it, primary, key) }
    }

    private fun allows_signing(flags: Int?): Boolean = flags == null || flags and KeyFlags.SIGN_DATA != 0

    private fun flags_of(signature: PGPSignature): Int? =
        signature.hashedSubPackets?.takeIf { it.hasSubpacket(SignatureSubpacketTags.KEY_FLAGS) }?.keyFlags

    fun encrypts_messages(flags: Int?): Boolean =
        flags == null || flags and (KeyFlags.ENCRYPT_COMMS or KeyFlags.ENCRYPT_STORAGE) != 0

    fun expiry_ms(created_at_ms: Long, valid_seconds: Long): Long? {
        if (valid_seconds <= 0L) return null
        return created_at_ms + valid_seconds * 1000L
    }

    fun usable_at(
        created_at_ms: Long,
        valid_seconds: Long,
        revoked: Boolean,
        primary_valid_until_ms: Long?,
        at_ms: Long,
    ): Boolean {
        if (revoked) return false
        if (at_ms < created_at_ms) return false
        val expires_at = expiry_ms(created_at_ms, valid_seconds)
        if (expires_at != null && at_ms >= expires_at) return false
        if (primary_valid_until_ms != null && at_ms >= primary_valid_until_ms) return false
        return true
    }
}
