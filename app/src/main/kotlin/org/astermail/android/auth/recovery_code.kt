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

package org.astermail.android.auth

import android.util.Base64
import java.security.MessageDigest
import java.security.SecureRandom
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.PBEKeySpec
import org.astermail.android.api.recovery.VerifierCandidate

const val RECOVERY_CODE_SET_SIZE = 10
const val CURRENT_RECOVERY_CODE_SEGMENTS = 4
const val VERIFIER_SALT_BYTES = 16

private const val recovery_code_alphabet = "ABCDEFGHJKLMNPQRSTUVWXYZ23456789"
private const val recovery_code_segment_length = 4
private val recovery_code_segment_counts = listOf(4, 3)

fun canonicalize_recovery_code(code: String): String {
    val upper = code.uppercase(java.util.Locale.ROOT)
    val stripped = upper.filter { it in 'A'..'Z' || it in '0'..'9' }

    if (stripped.startsWith("ASTER")) {
        val body = stripped.substring(5)

        for (segment_count in recovery_code_segment_counts) {
            if (body.length != segment_count * recovery_code_segment_length) continue

            val segments = (0 until segment_count).map {
                body.substring(
                    it * recovery_code_segment_length,
                    (it + 1) * recovery_code_segment_length,
                )
            }

            return "ASTER-" + segments.joinToString("-")
        }
    }

    return upper.filter { it in 'A'..'Z' || it in '0'..'9' || it == '-' }
}

fun is_valid_recovery_code(code: String): Boolean {
    val segments = canonicalize_recovery_code(code).split("-")

    if (segments.firstOrNull() != "ASTER") return false
    if (!recovery_code_segment_counts.contains(segments.size - 1)) return false

    return segments.drop(1).all { segment ->
        segment.length == recovery_code_segment_length &&
            segment.all { it in 'A'..'Z' || it in '0'..'9' }
    }
}

fun generate_recovery_codes(count: Int = RECOVERY_CODE_SET_SIZE): List<String> {
    val random = SecureRandom()

    return (1..count).map {
        val segments = (1..4).map {
            (1..recovery_code_segment_length)
                .map { recovery_code_alphabet[random.nextInt(recovery_code_alphabet.length)] }
                .joinToString("")
        }

        "ASTER-" + segments.joinToString("-")
    }
}

fun hash_recovery_code(code: String): String =
    Base64.encodeToString(raw_recovery_code_hash(code), Base64.NO_WRAP)

fun raw_recovery_code_hash(code: String): ByteArray {
    val canonical = canonicalize_recovery_code(code)

    return MessageDigest.getInstance("SHA-256").digest(canonical.toByteArray(Charsets.UTF_8))
}

fun recovery_code_verifier(raw_hash: ByteArray): ByteArray {
    val digest = MessageDigest.getInstance("SHA-256")
    digest.update(RECOVERY_VERIFIER_DOMAIN.toByteArray(Charsets.UTF_8))
    digest.update(raw_hash)

    return RECOVERY_VERIFIER_PREFIX + digest.digest()
}

fun recovery_code_stored_hashes(code: String): Set<String> {
    val raw_hash = raw_recovery_code_hash(code)

    return setOf(
        Base64.encodeToString(raw_hash, Base64.NO_WRAP),
        Base64.encodeToString(recovery_code_verifier(raw_hash), Base64.NO_WRAP),
    )
}

fun is_outdated_recovery_code(code: String): Boolean {
    if (!is_valid_recovery_code(code)) return false

    return canonicalize_recovery_code(code).split("-").size - 1 < CURRENT_RECOVERY_CODE_SEGMENTS
}

fun has_outdated_recovery_codes(codes: List<String>?): Boolean =
    codes.orEmpty().any { is_outdated_recovery_code(it) }

fun generate_verifier_salt(): ByteArray =
    ByteArray(VERIFIER_SALT_BYTES).also { SecureRandom().nextBytes(it) }

fun derive_code_verifier(raw_hash: ByteArray, verifier_salt: ByteArray): ByteArray {
    require(verifier_salt.size == VERIFIER_SALT_BYTES) { "invalid verifier salt" }

    val digest = MessageDigest.getInstance("SHA-256")
    digest.update(RECOVERY_VERIFIER_DOMAIN.toByteArray(Charsets.UTF_8))
    digest.update(raw_hash)
    val inner = digest.digest()
    val password = CharArray(inner.size * 2)

    inner.forEachIndexed { index, byte ->
        val value = byte.toInt() and 0xff
        password[index * 2] = HEX_DIGITS[value ushr 4]
        password[index * 2 + 1] = HEX_DIGITS[value and 0x0f]
    }

    val spec = PBEKeySpec(password, verifier_salt, CODE_VERIFIER_ITERATIONS, CODE_VERIFIER_BYTES * 8)

    try {
        return SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256").generateSecret(spec).encoded
    } finally {
        spec.clearPassword()
        password.fill('\u0000')
        inner.fill(0)
    }
}

fun salted_recovery_verifier(raw_hash: ByteArray, verifier_salt: ByteArray): ByteArray {
    val code_verifier = derive_code_verifier(raw_hash, verifier_salt)
    val digest = MessageDigest.getInstance("SHA-256")
    digest.update(SALTED_VERIFIER_DOMAIN.toByteArray(Charsets.UTF_8))
    digest.update(verifier_salt)
    digest.update(code_verifier)
    code_verifier.fill(0)

    return SALTED_VERIFIER_PREFIX + verifier_salt + digest.digest()
}

fun stored_verifier_salt(stored: ByteArray): ByteArray? {
    val prefix_size = SALTED_VERIFIER_PREFIX.size

    if (stored.size != prefix_size + VERIFIER_SALT_BYTES + CODE_VERIFIER_BYTES) return null
    if (!stored.copyOfRange(0, prefix_size).contentEquals(SALTED_VERIFIER_PREFIX)) return null

    return stored.copyOfRange(prefix_size, prefix_size + VERIFIER_SALT_BYTES)
}

fun encode_verifier_field(bytes: ByteArray): String = java.util.Base64.getEncoder().encodeToString(bytes)

fun decode_verifier_field(value: String?): ByteArray? =
    value?.let { runCatching { java.util.Base64.getDecoder().decode(it) }.getOrNull() }

fun build_verifier_candidates(code: String, verifier_salts: List<String?>): List<VerifierCandidate> {
    val raw_hash = raw_recovery_code_hash(code)

    return verifier_salts.filterNotNull().distinct().mapNotNull { encoded ->
        val salt = decode_verifier_field(encoded)?.takeIf { it.size == VERIFIER_SALT_BYTES }
            ?: return@mapNotNull null

        VerifierCandidate(
            verifier_salt = encoded,
            code_verifier = encode_verifier_field(derive_code_verifier(raw_hash, salt)),
        )
    }
}

fun used_recovery_codes(codes: List<String>, stored_hashes: Set<String>): Set<String> {
    if (stored_hashes.isEmpty()) return emptySet()

    val encoder = java.util.Base64.getEncoder()
    val salts = stored_hashes
        .mapNotNull { stored -> decode_verifier_field(stored)?.let { stored_verifier_salt(it) } }
        .distinctBy { encoder.encodeToString(it) }

    return codes.filter { code ->
        val raw_hash = raw_recovery_code_hash(code)

        encoder.encodeToString(raw_hash) in stored_hashes ||
            encoder.encodeToString(recovery_code_verifier(raw_hash)) in stored_hashes ||
            salts.any { salt ->
                encoder.encodeToString(salted_recovery_verifier(raw_hash, salt)) in stored_hashes
            }
    }.toSet()
}

private const val RECOVERY_VERIFIER_DOMAIN = "aster-recovery-verifier-v2"
private val RECOVERY_VERIFIER_PREFIX = "ARV2".toByteArray(Charsets.US_ASCII)
private const val SALTED_VERIFIER_DOMAIN = "aster-recovery-verifier-v3"
private val SALTED_VERIFIER_PREFIX = "ARV3".toByteArray(Charsets.US_ASCII)
private const val CODE_VERIFIER_ITERATIONS = 310000
private const val CODE_VERIFIER_BYTES = 32
private const val HEX_DIGITS = "0123456789abcdef"
