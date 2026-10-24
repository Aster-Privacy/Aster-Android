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

import kotlinx.coroutines.CancellationException
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonPrimitive
import org.astermail.android.crypto.AuthSaltCollisionException
import org.astermail.android.crypto.hkdf_sha256

interface LoginPinSource {
    fun is_pinned(keys: List<String>): Boolean
    fun pin(keys: List<String>)
    fun unpin(keys: List<String>)
}

enum class LoginProofChoice { v1, v2 }

class PendingLoginEnrollment(v1: ByteArray, v2: ByteArray) {
    val v1: ByteArray = v1.copyOf()
    val v2: ByteArray = v2.copyOf()

    fun wipe() {
        v1.fill(0)
        v2.fill(0)
    }
}

class SignInProof(val proof: ByteArray, val enrollment: PendingLoginEnrollment?)

object LoginProof {
    const val max_pinned_accounts = 64
    const val proof_bytes = 32
    private const val pin_separator = "\n"
    private val v2_info = "aster-login-v2".toByteArray(Charsets.UTF_8)

    fun derive_v2(v1: ByteArray): ByteArray = hkdf_sha256(v1, ByteArray(0), v2_info, proof_bytes)

    fun read_kdf_version(value: JsonElement?): Int {
        val primitive = value as? JsonPrimitive ?: return 1
        if (primitive.isString) return 1
        return if (primitive.content == "2") 2 else 1
    }

    fun user_hash_pin_key(user_hash: String): String = "h:$user_hash"

    fun user_id_pin_key(user_id: String): String = "u:$user_id"

    fun pin_keys(user_id: String?, user_hash: String?): List<String> = listOfNotNull(
        user_id?.takeIf { it.isNotBlank() }?.let(::user_id_pin_key),
        user_hash?.takeIf { it.isNotBlank() }?.let(::user_hash_pin_key),
    )

    fun choose_proof(kdf_version: Int, pinned: Boolean): LoginProofChoice {
        if (kdf_version == 2) return LoginProofChoice.v2
        if (pinned) throw AuthSaltCollisionException("server offered the older login proof for a pinned account")
        return LoginProofChoice.v1
    }

    fun is_valid_pin_entry(entry: String): Boolean {
        if (entry.length < 3 || entry.length > 256) return false
        if (!entry.startsWith("h:") && !entry.startsWith("u:")) return false
        return entry.none { it.isWhitespace() || it.isISOControl() }
    }

    fun parse_pins(stored: String?): List<String> {
        if (stored.isNullOrEmpty()) return emptyList()
        return stored.split(pin_separator)
            .filter(::is_valid_pin_entry)
            .distinct()
            .takeLast(max_pinned_accounts)
    }

    fun serialize_pins(entries: List<String>): String = entries.joinToString(pin_separator)

    fun add_pins(entries: List<String>, keys: List<String>): List<String> {
        val fresh = keys.filter(::is_valid_pin_entry).distinct()
        if (fresh.isEmpty()) return entries
        return (entries.filter { it !in fresh } + fresh).takeLast(max_pinned_accounts)
    }

    fun remove_pins(entries: List<String>, keys: List<String>): List<String> = entries.filter { it !in keys }

    fun is_pinned(entries: List<String>, keys: List<String>): Boolean = keys.any { it in entries }

    fun select_sign_in_proof(
        v1: ByteArray,
        kdf_version: Int,
        pin_keys: List<String>,
        pins: LoginPinSource,
    ): SignInProof {
        return when (choose_proof(kdf_version, pins.is_pinned(pin_keys))) {
            LoginProofChoice.v2 -> SignInProof(derive_v2(v1), null)
            LoginProofChoice.v1 -> {
                val v2 = derive_v2(v1)
                val enrollment = PendingLoginEnrollment(v1, v2)
                v2.fill(0)
                SignInProof(v1.copyOf(), enrollment)
            }
        }
    }

    fun select_step_up_proof(
        v1: ByteArray,
        kdf_version: Int,
        pin_keys: List<String>,
        pins: LoginPinSource,
    ): ByteArray {
        return when (choose_proof(kdf_version, pins.is_pinned(pin_keys))) {
            LoginProofChoice.v2 -> {
                pins.pin(pin_keys)
                derive_v2(v1)
            }
            LoginProofChoice.v1 -> v1.copyOf()
        }
    }

    suspend fun flush_enrollment(
        enrollment: PendingLoginEnrollment,
        pin_keys: List<String>,
        pins: LoginPinSource,
        enroll: suspend (ByteArray, ByteArray) -> Boolean,
    ): Boolean {
        val enrolled = try {
            enroll(enrollment.v1, enrollment.v2)
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (_: Throwable) {
            false
        }
        if (enrolled) pins.pin(pin_keys)
        return enrolled
    }
}
