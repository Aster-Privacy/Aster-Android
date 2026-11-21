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

package org.astermail.android.guard

import android.content.SharedPreferences
import java.security.SecureRandom
import java.util.UUID
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import org.astermail.android.crypto.DeviceEnvelope
import org.astermail.android.crypto.ratchet.RatchetCrypto
import org.bouncycastle.crypto.params.Ed25519PrivateKeyParameters
import org.bouncycastle.crypto.params.X25519PrivateKeyParameters

class guard_box_keys(
    val x25519_sk: ByteArray,
    val x25519_pk: ByteArray,
    val mlkem_sk: ByteArray,
    val mlkem_pk: ByteArray,
    val ed25519_pk: ByteArray,
    val ed25519_sk: ByteArray?,
) {
    override fun equals(other: Any?): Boolean =
        other is guard_box_keys &&
            x25519_sk.contentEquals(other.x25519_sk) &&
            x25519_pk.contentEquals(other.x25519_pk) &&
            mlkem_sk.contentEquals(other.mlkem_sk) &&
            mlkem_pk.contentEquals(other.mlkem_pk) &&
            ed25519_pk.contentEquals(other.ed25519_pk) &&
            ed25519_sk.contentEquals(other.ed25519_sk)

    override fun hashCode(): Int = x25519_pk.contentHashCode() * 31 + ed25519_pk.contentHashCode()
}

data class guard_pending_sign_in(
    val request_id: String,
    val guard_token: String,
    val number: Int,
    val expires_at: String,
    val keys: guard_box_keys,
    val account_hint: String,
    val action_alias: String? = null,
)

data class guard_saved_rescue(
    val rescue_id: String,
    val apply_after: String,
    val account_hint: String,
)

fun generate_box_keys(random: SecureRandom = SecureRandom()): guard_box_keys {
    val mlkem = RatchetCrypto.ml_kem_768_generate_keypair()
    val x_sk = X25519PrivateKeyParameters(random)
    val ed_sk = Ed25519PrivateKeyParameters(random)
    return guard_box_keys(
        x25519_sk = x_sk.encoded,
        x25519_pk = x_sk.generatePublicKey().encoded,
        mlkem_sk = mlkem.secret_key,
        mlkem_pk = mlkem.public_key,
        ed25519_pk = ed_sk.generatePublicKey().encoded,
        ed25519_sk = ed_sk.encoded,
    )
}

@Serializable
private data class stored_keys(
    val x25519_sk: String,
    val x25519_pk: String,
    val mlkem_sk: String,
    val mlkem_pk: String,
    val ed25519_pk: String,
    val ed25519_sk: String? = null,
)

@Serializable
private data class stored_enrollment(
    val device_id: String,
    val code_secret: String,
    val keys: stored_keys,
    val action_alias: String? = null,
)

@Serializable
private data class stored_pending(
    val request_id: String,
    val guard_token: String,
    val number: Int,
    val expires_at: String,
    val keys: stored_keys,
    val account_hint: String,
    val action_alias: String? = null,
)

@Serializable
private data class stored_rescue(
    val rescue_id: String,
    val apply_after: String,
    val account_hint: String,
)

class GuardDeviceStore(private val prefs: SharedPreferences) {

    private val json = Json { ignoreUnknownKeys = true }

    fun device_id(user_id: UUID): UUID? =
        enrollment(user_id)?.let { runCatching { UUID.fromString(it.device_id) }.getOrNull() }

    fun save_enrollment(
        user_id: UUID,
        device_id: UUID,
        code_secret: ByteArray,
        x25519_sk: ByteArray,
        mlkem_sk: ByteArray,
        x25519_pk: ByteArray,
        mlkem_pk: ByteArray,
        ed25519_pk: ByteArray,
        ed25519_sk: ByteArray? = null,
        action_alias: String? = null,
    ): Boolean {
        val record = stored_enrollment(
            device_id = device_id.toString(),
            code_secret = enc(code_secret),
            keys = to_stored(guard_box_keys(x25519_sk, x25519_pk, mlkem_sk, mlkem_pk, ed25519_pk, ed25519_sk)),
            action_alias = action_alias,
        )
        return write_enrollment(user_id, record)
    }

    fun stored_action_alias(user_id: UUID): String? = enrollment(user_id)?.action_alias

    fun action_alias(user_id: UUID, current_user_id: UUID?): String {
        val record = enrollment(user_id) ?: return GuardActionKey.default_alias(user_id)
        record.action_alias?.let { return it }
        if (user_id == current_user_id && !legacy_alias_claimed()) {
            if (write_enrollment(user_id, record.copy(action_alias = GuardActionKey.LEGACY_ALIAS))) {
                runCatching { prefs.edit().putBoolean(legacy_claimed_key, true).commit() }
                return GuardActionKey.LEGACY_ALIAS
            }
        }
        return GuardActionKey.default_alias(user_id)
    }

    fun enrolled_users(): List<UUID> = runCatching {
        prefs.all.keys
            .filter { it.startsWith(enrollment_prefix) }
            .mapNotNull { runCatching { UUID.fromString(it.removePrefix(enrollment_prefix)) }.getOrNull() }
    }.getOrDefault(emptyList())

    private fun legacy_alias_claimed(): Boolean =
        runCatching { prefs.getBoolean(legacy_claimed_key, false) }.getOrDefault(false)

    private fun write_enrollment(user_id: UUID, record: stored_enrollment): Boolean = runCatching {
        prefs.edit().putString(enrollment_key(user_id), json.encodeToString(stored_enrollment.serializer(), record)).commit()
    }.getOrDefault(false)

    fun code_secret(user_id: UUID): ByteArray? = enrollment(user_id)?.let { dec(it.code_secret) }

    fun box_keys(user_id: UUID): guard_box_keys? = enrollment(user_id)?.let { runCatching { from_stored(it.keys) }.getOrNull() }

    fun clear(user_id: UUID): String? {
        val alias = enrollment(user_id)?.action_alias
        runCatching { prefs.edit().remove(enrollment_key(user_id)).commit() }
        return alias
    }

    fun save_pending_sign_in(p: guard_pending_sign_in): Boolean {
        val record = stored_pending(
            p.request_id, p.guard_token, p.number, p.expires_at, to_stored(p.keys), p.account_hint, p.action_alias,
        )
        return runCatching {
            prefs.edit().putString(pending_key, json.encodeToString(stored_pending.serializer(), record)).commit()
        }.getOrDefault(false)
    }

    fun pending_sign_in(): guard_pending_sign_in? {
        val raw = runCatching { prefs.getString(pending_key, null) }.getOrNull() ?: return null
        return runCatching {
            val p = json.decodeFromString(stored_pending.serializer(), raw)
            guard_pending_sign_in(
                p.request_id, p.guard_token, p.number, p.expires_at, from_stored(p.keys), p.account_hint, p.action_alias,
            )
        }.getOrNull()
    }

    fun clear_pending_sign_in() {
        runCatching { prefs.edit().remove(pending_key).commit() }
    }

    fun save_rescue(r: guard_saved_rescue): Boolean {
        val record = stored_rescue(r.rescue_id, r.apply_after, r.account_hint)
        return runCatching {
            prefs.edit().putString(rescue_key, json.encodeToString(stored_rescue.serializer(), record)).commit()
        }.getOrDefault(false)
    }

    fun rescue(): guard_saved_rescue? {
        val raw = runCatching { prefs.getString(rescue_key, null) }.getOrNull() ?: return null
        return runCatching {
            val r = json.decodeFromString(stored_rescue.serializer(), raw)
            guard_saved_rescue(r.rescue_id, r.apply_after, r.account_hint)
        }.getOrNull()
    }

    fun clear_rescue() {
        runCatching { prefs.edit().remove(rescue_key).commit() }
    }

    fun set_setup_after_rescue() {
        runCatching { prefs.edit().putBoolean(setup_after_rescue_key, true).commit() }
    }

    fun take_setup_after_rescue(): Boolean {
        val set = runCatching { prefs.getBoolean(setup_after_rescue_key, false) }.getOrDefault(false)
        if (set) runCatching { prefs.edit().remove(setup_after_rescue_key).commit() }
        return set
    }

    private fun enrollment(user_id: UUID): stored_enrollment? {
        val raw = runCatching { prefs.getString(enrollment_key(user_id), null) }.getOrNull() ?: return null
        return runCatching { json.decodeFromString(stored_enrollment.serializer(), raw) }.getOrNull()
    }

    private fun to_stored(k: guard_box_keys) = stored_keys(
        x25519_sk = enc(k.x25519_sk),
        x25519_pk = enc(k.x25519_pk),
        mlkem_sk = enc(k.mlkem_sk),
        mlkem_pk = enc(k.mlkem_pk),
        ed25519_pk = enc(k.ed25519_pk),
        ed25519_sk = k.ed25519_sk?.let { enc(it) },
    )

    private fun from_stored(k: stored_keys) = guard_box_keys(
        x25519_sk = dec(k.x25519_sk),
        x25519_pk = dec(k.x25519_pk),
        mlkem_sk = dec(k.mlkem_sk),
        mlkem_pk = dec(k.mlkem_pk),
        ed25519_pk = dec(k.ed25519_pk),
        ed25519_sk = k.ed25519_sk?.let { dec(it) },
    )

    private fun enc(b: ByteArray): String = DeviceEnvelope.base64url_encode(b)

    private fun dec(s: String): ByteArray = DeviceEnvelope.base64url_decode(s)

    private fun enrollment_key(user_id: UUID): String = "${enrollment_prefix}$user_id"

    companion object {
        const val PREFS_NAME = "aster_guard_device_v1"
        private const val enrollment_prefix = "enr_"
        private const val pending_key = "pending_sign_in"
        private const val rescue_key = "pending_rescue"
        private const val setup_after_rescue_key = "setup_after_rescue"
        private const val legacy_claimed_key = "legacy_action_alias_claimed"
    }
}
