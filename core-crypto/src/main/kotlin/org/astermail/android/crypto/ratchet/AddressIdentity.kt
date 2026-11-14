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

import java.math.BigInteger
import java.nio.ByteBuffer
import org.bouncycastle.asn1.sec.SECNamedCurves
import org.bouncycastle.crypto.digests.SHA256Digest
import org.bouncycastle.crypto.params.ECDomainParameters
import org.bouncycastle.crypto.params.ECPrivateKeyParameters
import org.bouncycastle.crypto.params.ECPublicKeyParameters
import org.bouncycastle.crypto.signers.ECDSASigner
import org.bouncycastle.crypto.signers.HMacDSAKCalculator

data class AddressKeySource(
    val identity_jwk: String,
    val identity_public: String,
    val signed_prekey_jwk: String,
    val signed_prekey_public: String,
    val pq_identity_secret: String? = null,
    val pq_identity_seed: String? = null,
    val pq_identity_public: String? = null,
)

data class AddressKeySet(
    val identity_jwk: String,
    val identity_public: String,
    val signed_prekey_jwk: String,
    val signed_prekey_public: String,
    val pq_identity_secret: String? = null,
    val pq_identity_public: String? = null,
)

data class DerivedAddressKey(
    val identity_jwk: String,
    val identity_public: String,
)

data class SignedAddressEntry(
    val address: String,
    val kem_identity_key: String,
    val signed_prekey: String,
    val signed_prekey_signature: String,
    val pq_kem_public_key: String?,
)

data class AddressEntryVerification(
    val valid: Boolean,
    val endorsers: List<String>,
)

object AddressIdentity {

    private val identity_salt = "aster/ratchet/address-sender-identity/v1".toByteArray(Charsets.UTF_8)
    private val signed_prekey_salt = "aster/ratchet/address-signed-prekey/v1".toByteArray(Charsets.UTF_8)
    private val pq_identity_salt = "aster/ratchet/address-pq-identity/v1".toByteArray(Charsets.UTF_8)
    private const val ENTRY_LABEL = "aster/ratchet/address-entry/v1"
    private const val ENDORSEMENT_LABEL = "aster/ratchet/address-endorsement/v1"
    private const val SCALAR_LEN = 32
    private const val SCALAR_SEED_LEN = 48
    private const val PQ_SEED_LEN = 64
    private const val POINT_LEN = 65
    private const val SIGNATURE_LEN = 64
    private const val ENDORSEMENT_LEN = POINT_LEN + SIGNATURE_LEN
    private const val KEY_SET_CACHE_LIMIT = 512
    const val MAX_ENTRY_ENDORSEMENTS = 32

    private val curve: ECDomainParameters by lazy {
        val params = SECNamedCurves.getByName("secp256r1")
        ECDomainParameters(params.curve, params.g, params.n, params.h)
    }

    private val key_set_cache = HashMap<String, AddressKeySet?>()

    fun clear_cache() {
        synchronized(key_set_cache) { key_set_cache.clear() }
    }

    fun normalize(address: String): String = address.trim().lowercase(java.util.Locale.ROOT)

    fun canonical_form(address: String): String {
        val normalized = normalize(address)
        val at = normalized.lastIndexOf('@')
        if (at <= 0 || at == normalized.length - 1) return ""
        val local = normalized.substring(0, at).split('+')[0].replace(".", "")
        if (local.isEmpty()) return ""
        return local + "@" + normalized.substring(at + 1)
    }

    fun same_canonical_address(left: String?, right: String?): Boolean {
        if (left.isNullOrBlank() || right.isNullOrBlank()) return false
        val left_form = canonical_form(left).ifEmpty { normalize(left) }
        val right_form = canonical_form(right).ifEmpty { normalize(right) }
        return left_form == right_form
    }

    fun entry_forms(address: String): List<String> {
        val normalized = normalize(address)
        val canonical = canonical_form(normalized)
        if (canonical.isEmpty()) return emptyList()
        return if (normalized == canonical) listOf(normalized) else listOf(normalized, canonical)
    }

    private fun fixed(value: BigInteger, length: Int): ByteArray {
        val raw = value.toByteArray()
        if (raw.size == length) return raw
        if (raw.size > length) return raw.copyOfRange(raw.size - length, raw.size)
        val out = ByteArray(length)
        System.arraycopy(raw, 0, out, length - raw.size, raw.size)
        return out
    }

    private fun public_point(scalar: BigInteger): ByteArray =
        curve.g.multiply(scalar).normalize().getEncoded(false)

    fun jwk_for_scalar(scalar_bytes: ByteArray): String {
        val point = public_point(BigInteger(1, scalar_bytes))
        val d = RatchetCrypto.b64url_encode(scalar_bytes)
        val x = RatchetCrypto.b64url_encode(point.copyOfRange(1, 33))
        val y = RatchetCrypto.b64url_encode(point.copyOfRange(33, 65))
        return "{\"kty\":\"EC\",\"crv\":\"P-256\",\"d\":\"$d\",\"x\":\"$x\",\"y\":\"$y\",\"ext\":true,\"key_ops\":[\"deriveBits\"]}"
    }

    fun public_for_scalar(scalar_bytes: ByteArray): String =
        RatchetCrypto.b64_encode(public_point(BigInteger(1, scalar_bytes)))

    fun derive_sender_identity(account_identity_jwk: String?, address: String): DerivedAddressKey? =
        derive_key(account_identity_jwk, address, identity_salt)

    private fun derive_key(account_jwk: String?, address: String, salt: ByteArray): DerivedAddressKey? {
        val normalized = normalize(address)
        if (account_jwk.isNullOrBlank() || normalized.isEmpty()) return null
        var account_scalar: ByteArray? = null
        var seed: ByteArray? = null
        var scalar: ByteArray? = null
        return try {
            val extracted = RatchetCrypto.jwk_extract_d_bytes(account_jwk)
            account_scalar = extracted
            if (extracted.size != SCALAR_LEN) return null
            val expanded = RatchetCrypto.hkdf_sha256(
                extracted,
                salt,
                normalized.toByteArray(Charsets.UTF_8),
                SCALAR_SEED_LEN,
            )
            seed = expanded
            val reduced = BigInteger(1, expanded).mod(curve.n.subtract(BigInteger.ONE)).add(BigInteger.ONE)
            val reduced_bytes = fixed(reduced, SCALAR_LEN)
            scalar = reduced_bytes
            DerivedAddressKey(
                identity_jwk = jwk_for_scalar(reduced_bytes),
                identity_public = RatchetCrypto.b64_encode(public_point(reduced)),
            )
        } catch (_: Throwable) {
            null
        } finally {
            account_scalar?.fill(0)
            seed?.fill(0)
            scalar?.fill(0)
        }
    }

    private fun resolve_pq_secret(source: AddressKeySource): ByteArray? {
        val secret = source.pq_identity_secret?.takeIf { it.isNotBlank() }
        if (secret != null) return runCatching { RatchetCrypto.b64_decode(secret) }.getOrNull()
        val seed_b64 = source.pq_identity_seed?.takeIf { it.isNotBlank() } ?: return null
        return runCatching {
            val seed = RatchetCrypto.b64_decode(seed_b64)
            if (seed.size != PQ_SEED_LEN) {
                null
            } else {
                val pair = RatchetCrypto.ml_kem_768_keypair_from_seed(seed)
                seed.fill(0)
                pair.secret_key
            }
        }.getOrNull()
    }

    private fun derive_pq(source: AddressKeySource, form: String): Pair<String, String>? {
        val account_secret = resolve_pq_secret(source) ?: return null
        var seed: ByteArray? = null
        return try {
            val expanded = RatchetCrypto.hkdf_sha256(
                account_secret,
                pq_identity_salt,
                form.toByteArray(Charsets.UTF_8),
                PQ_SEED_LEN,
            )
            seed = expanded
            val pair = RatchetCrypto.ml_kem_768_keypair_from_seed(expanded.copyOf())
            val secret = RatchetCrypto.b64_encode(pair.secret_key)
            pair.secret_key.fill(0)
            pair.seed.fill(0)
            secret to RatchetCrypto.b64_encode(pair.public_key)
        } catch (_: Throwable) {
            null
        } finally {
            account_secret.fill(0)
            seed?.fill(0)
        }
    }

    fun derive_key_set(source: AddressKeySource, address_form: String): AddressKeySet? {
        val form = normalize(address_form)
        if (form.isEmpty()) return null
        val has_pq = !source.pq_identity_secret.isNullOrBlank() || !source.pq_identity_seed.isNullOrBlank()
        val cache_key = listOf(
            source.identity_public,
            source.signed_prekey_public,
            source.pq_identity_public.orEmpty(),
            if (has_pq) "pq" else "",
            form,
        ).joinToString("|")
        synchronized(key_set_cache) {
            if (key_set_cache.containsKey(cache_key)) return key_set_cache[cache_key]
        }
        val identity = derive_key(source.identity_jwk, form, identity_salt)
        val signed_prekey = derive_key(source.signed_prekey_jwk, form, signed_prekey_salt)
        val pq = derive_pq(source, form)
        val derived = if (identity != null && signed_prekey != null && (!has_pq || pq != null)) {
            AddressKeySet(
                identity_jwk = identity.identity_jwk,
                identity_public = identity.identity_public,
                signed_prekey_jwk = signed_prekey.identity_jwk,
                signed_prekey_public = signed_prekey.identity_public,
                pq_identity_secret = pq?.first,
                pq_identity_public = pq?.second,
            )
        } else {
            return null
        }
        synchronized(key_set_cache) {
            if (key_set_cache.size >= KEY_SET_CACHE_LIMIT) key_set_cache.clear()
            key_set_cache[cache_key] = derived
        }
        return derived
    }

    fun receiver_key_sets(
        current: AddressKeySource?,
        previous: List<AddressKeySource>,
        address: String,
    ): List<AddressKeySet> {
        val forms = entry_forms(address)
        if (forms.isEmpty()) return emptyList()
        val sources = listOfNotNull(current) + previous
        val sets = mutableListOf<AddressKeySet>()
        for (source in sources) {
            for (form in forms) {
                derive_key_set(source, form)?.let { sets.add(it) }
            }
        }
        return sets
    }

    fun is_own_address_identity_key(
        account_identity_jwks: List<String>,
        addresses: List<String>,
        identity_key: String?,
    ): Boolean {
        val candidate = identity_key?.trim().orEmpty()
        if (candidate.isEmpty()) return false
        val normalized = addresses.map { normalize(it) }.filter { it.isNotEmpty() }.toSet()
        for (account_jwk in account_identity_jwks) {
            for (address in normalized) {
                if (derive_sender_identity(account_jwk, address)?.identity_public == candidate) return true
            }
        }
        return false
    }

    private fun length_prefixed_digest(parts: List<ByteArray>): ByteArray {
        val buffer = ByteBuffer.allocate(parts.sumOf { 4 + it.size })
        for (part in parts) {
            buffer.putInt(part.size)
            buffer.put(part)
        }
        return RatchetCrypto.sha256(buffer.array())
    }

    private fun entry_digest(
        label: String,
        form: String,
        kem_identity_key: String,
        signed_prekey: String,
        pq_kem_public_key: String?,
    ): ByteArray = length_prefixed_digest(
        listOf(
            label.toByteArray(Charsets.UTF_8),
            form.toByteArray(Charsets.UTF_8),
            RatchetCrypto.b64_decode(kem_identity_key),
            RatchetCrypto.b64_decode(signed_prekey),
            pq_kem_public_key?.takeIf { it.isNotEmpty() }?.let { RatchetCrypto.b64_decode(it) } ?: ByteArray(0),
        ),
    )

    private fun sign_digest(digest: ByteArray, identity_jwk: String): ByteArray {
        val scalar_bytes = RatchetCrypto.jwk_extract_d_bytes(identity_jwk)
        try {
            val signer = ECDSASigner(HMacDSAKCalculator(SHA256Digest()))
            signer.init(true, ECPrivateKeyParameters(BigInteger(1, scalar_bytes), curve))
            val components = signer.generateSignature(digest)
            val r = components[0]
            val raw_s = components[1]
            val s = if (raw_s > curve.n.shiftRight(1)) curve.n.subtract(raw_s) else raw_s
            return fixed(r, 32) + fixed(s, 32)
        } finally {
            scalar_bytes.fill(0)
        }
    }

    private fun verify_digest(digest: ByteArray, signature: ByteArray, public_key: ByteArray): Boolean = try {
        if (signature.size != SIGNATURE_LEN || public_key.size != POINT_LEN) {
            false
        } else {
            val point = curve.curve.decodePoint(public_key)
            val verifier = ECDSASigner()
            verifier.init(false, ECPublicKeyParameters(point, curve))
            verifier.verifySignature(
                digest,
                BigInteger(1, signature.copyOfRange(0, 32)),
                BigInteger(1, signature.copyOfRange(32, 64)),
            )
        }
    } catch (_: Throwable) {
        false
    }

    fun sign_entry(
        current: AddressKeySource?,
        previous_identity_jwks: List<String>,
        address_form: String,
    ): SignedAddressEntry? {
        val form = normalize(address_form)
        if (form.isEmpty() || current == null) return null
        return try {
            val derived = derive_key_set(current, form) ?: return null
            val pq = derived.pq_identity_public
            val parts = mutableListOf(
                sign_digest(
                    entry_digest(ENTRY_LABEL, form, derived.identity_public, derived.signed_prekey_public, pq),
                    derived.identity_jwk,
                ),
            )
            val endorsement_digest = entry_digest(
                ENDORSEMENT_LABEL,
                form,
                derived.identity_public,
                derived.signed_prekey_public,
                pq,
            )
            val seen = mutableSetOf(derived.identity_public)
            for (previous_jwk in previous_identity_jwks) {
                if (parts.size > MAX_ENTRY_ENDORSEMENTS) break
                val earlier = derive_key(previous_jwk, form, identity_salt) ?: continue
                if (!seen.add(earlier.identity_public)) continue
                parts.add(
                    RatchetCrypto.b64_decode(earlier.identity_public) +
                        sign_digest(endorsement_digest, earlier.identity_jwk),
                )
            }
            SignedAddressEntry(
                address = form,
                kem_identity_key = derived.identity_public,
                signed_prekey = derived.signed_prekey_public,
                signed_prekey_signature = RatchetCrypto.b64_encode(parts.reduce { acc, bytes -> acc + bytes }),
                pq_kem_public_key = pq,
            )
        } catch (_: Throwable) {
            null
        }
    }

    private fun verify_for_form(
        form: String,
        kem_identity_key: String,
        signed_prekey: String,
        signed_prekey_signature: String,
        pq_kem_public_key: String?,
    ): AddressEntryVerification? {
        val signature = RatchetCrypto.b64_decode(signed_prekey_signature)
        val identity = RatchetCrypto.b64_decode(kem_identity_key)
        if (identity.size != POINT_LEN ||
            signature.size < SIGNATURE_LEN ||
            (signature.size - SIGNATURE_LEN) % ENDORSEMENT_LEN != 0
        ) {
            return null
        }
        val own_valid = verify_digest(
            entry_digest(ENTRY_LABEL, form, kem_identity_key, signed_prekey, pq_kem_public_key),
            signature.copyOfRange(0, SIGNATURE_LEN),
            identity,
        )
        if (!own_valid) return null
        val endorsement_digest = entry_digest(ENDORSEMENT_LABEL, form, kem_identity_key, signed_prekey, pq_kem_public_key)
        val endorsers = mutableListOf<String>()
        var offset = SIGNATURE_LEN
        while (offset < signature.size && endorsers.size < MAX_ENTRY_ENDORSEMENTS) {
            val endorser = signature.copyOfRange(offset, offset + POINT_LEN)
            val endorsement = signature.copyOfRange(offset + POINT_LEN, offset + ENDORSEMENT_LEN)
            if (verify_digest(endorsement_digest, endorsement, endorser)) {
                endorsers.add(RatchetCrypto.b64_encode(endorser))
            }
            offset += ENDORSEMENT_LEN
        }
        return AddressEntryVerification(valid = true, endorsers = endorsers)
    }

    fun verify_entry(
        address: String,
        kem_identity_key: String,
        signed_prekey: String,
        signed_prekey_signature: String,
        pq_kem_public_key: String?,
    ): AddressEntryVerification {
        try {
            for (form in entry_forms(address)) {
                val verified = verify_for_form(
                    form,
                    kem_identity_key,
                    signed_prekey,
                    signed_prekey_signature,
                    pq_kem_public_key,
                )
                if (verified != null) return verified
            }
        } catch (_: Throwable) {
            return AddressEntryVerification(valid = false, endorsers = emptyList())
        }
        return AddressEntryVerification(valid = false, endorsers = emptyList())
    }
}
