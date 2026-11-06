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
package org.astermail.android.crypto.storage_rotation

import kotlin.random.Random
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject

const val STORAGE_REKEY_STATUS_PATH = "/core/v1/auth/me/storage-rekey/status"
const val STORAGE_REKEY_PAUSED_CODE = "STORAGE_REKEY_PAUSED"
const val STORAGE_REKEY_BUSY_CODE = "STORAGE_REKEY_BUSY"
const val STORAGE_REKEY_OLD_CLIENT_CODE = "STORAGE_REKEY_OLD_CLIENT"
const val RATE_LIMIT_EXCEEDED_CODE = "RATE_LIMIT_EXCEEDED"

const val REKEY_RETRY_AFTER_MIN_SECS = 1L
const val REKEY_RETRY_AFTER_MAX_SECS = 300L
const val REKEY_BUSY_DEFAULT_SECS = 5L
const val REKEY_BUSY_MAX_ATTEMPTS = 8
const val REKEY_BUSY_JITTER_MS = 1000L
const val REKEY_RATE_LIMIT_WAIT_SECS = 3600L
const val REKEY_NOT_ADMITTED_WAIT_SECS = 6 * 3600L
const val REKEY_BUSY_WAIT_SECS = 60L

data class RekeyStatus(
    val rollout_enabled: Boolean,
    val may_start: Boolean,
    val old_client_sessions: Long,
    val storage_rekey_level: Long,
    val vault_writer_level: Long,
)

sealed class RekeyGateDecision {
    object Start : RekeyGateDecision()
    data class Wait(val reason: String, val retry_after_secs: Long) : RekeyGateDecision()
}

fun parse_rekey_status(data: JsonElement?): RekeyStatus? {
    val obj = data as? JsonObject ?: return null
    val may_start = obj.bool("may_start") ?: return null
    return RekeyStatus(
        rollout_enabled = obj.bool("rollout_enabled") ?: false,
        may_start = may_start,
        old_client_sessions = obj.long("old_client_sessions") ?: 0,
        storage_rekey_level = obj.long("storage_rekey_level") ?: 0,
        vault_writer_level = obj.long("vault_writer_level") ?: 0,
    )
}

suspend fun check_rekey_gate(api: ResealApi): RekeyGateDecision {
    val reply = api.get(STORAGE_REKEY_STATUS_PATH)
    if (reply.status == 429) return RekeyGateDecision.Wait("rate_limited", REKEY_RATE_LIMIT_WAIT_SECS)
    if (reply.error) return RekeyGateDecision.Wait("status_unavailable", REKEY_RATE_LIMIT_WAIT_SECS)
    val status = parse_rekey_status(reply.data)
        ?: return RekeyGateDecision.Wait("status_unreadable", REKEY_RATE_LIMIT_WAIT_SECS)
    return decide_rekey_start(status)
}

fun decide_rekey_start(status: RekeyStatus): RekeyGateDecision = when {
    !status.rollout_enabled -> RekeyGateDecision.Wait("paused", REKEY_NOT_ADMITTED_WAIT_SECS)
    status.old_client_sessions > 0 -> RekeyGateDecision.Wait("old_client", REKEY_NOT_ADMITTED_WAIT_SECS)
    !status.may_start -> RekeyGateDecision.Wait("not_admitted", REKEY_NOT_ADMITTED_WAIT_SECS)
    else -> RekeyGateDecision.Start
}

fun clamp_retry_after(reply: ApiReply, fallback_secs: Long): Long {
    val from_details = ((reply.data as? JsonObject)?.get("details") as? JsonObject).long("retry_after_secs")
    val raw = reply.retry_after_secs ?: from_details ?: fallback_secs
    return raw.coerceIn(REKEY_RETRY_AFTER_MIN_SECS, REKEY_RETRY_AFTER_MAX_SECS)
}

fun is_gated_rekey_path(path: String): Boolean =
    path.startsWith(STORAGE_REKEY_PATH) && !path.startsWith(STORAGE_REKEY_STATUS_PATH)

fun gate_deferral(reply: ApiReply): ResealDeferred? = when {
    reply.status == 429 && reply.code == STORAGE_REKEY_PAUSED_CODE ->
        ResealDeferred("paused", maxOf(clamp_retry_after(reply, REKEY_RETRY_AFTER_MAX_SECS), REKEY_RETRY_AFTER_MAX_SECS))
    reply.status == 409 && reply.code == STORAGE_REKEY_OLD_CLIENT_CODE ->
        ResealDeferred("old_client", REKEY_NOT_ADMITTED_WAIT_SECS)
    reply.status == 429 && reply.code == RATE_LIMIT_EXCEEDED_CODE ->
        ResealDeferred("rate_limited", REKEY_RATE_LIMIT_WAIT_SECS)
    reply.status == 429 && reply.code != STORAGE_REKEY_BUSY_CODE && reply.retry_after_secs == null ->
        ResealDeferred("rate_limited", REKEY_RATE_LIMIT_WAIT_SECS)
    else -> null
}

fun is_rekey_busy(reply: ApiReply): Boolean = reply.status == 429 && reply.code == STORAGE_REKEY_BUSY_CODE

class GatedResealApi(
    private val inner: ResealApi,
    private val sleep: suspend (Long) -> Unit,
    private val jitter_ms: () -> Long = { Random.nextLong(0, REKEY_BUSY_JITTER_MS + 1) },
    private val is_cancelled: () -> Boolean = { false },
) : ResealApi {

    override suspend fun get(path: String): ApiReply = gated(path) { inner.get(path) }

    override suspend fun post(path: String, body: JsonElement): ApiReply = gated(path) { inner.post(path, body) }

    override suspend fun put(path: String, body: JsonElement): ApiReply = gated(path) { inner.put(path, body) }

    private suspend fun gated(path: String, send: suspend () -> ApiReply): ApiReply {
        if (!is_gated_rekey_path(path)) return send()
        var attempts = 0
        while (true) {
            val reply = send()
            gate_deferral(reply)?.let { throw it }
            if (!is_rekey_busy(reply)) return reply
            attempts += 1
            if (attempts >= REKEY_BUSY_MAX_ATTEMPTS || is_cancelled()) {
                throw ResealDeferred("busy", REKEY_BUSY_WAIT_SECS)
            }
            sleep(clamp_retry_after(reply, REKEY_BUSY_DEFAULT_SECS) * 1000 + jitter_ms())
        }
    }
}
