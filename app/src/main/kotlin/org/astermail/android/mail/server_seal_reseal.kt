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

package org.astermail.android.mail

import java.util.Base64
import java.util.concurrent.ConcurrentHashMap
import kotlinx.coroutines.CancellationException
import org.astermail.android.crypto.storage_rotation.ResealApi
import org.astermail.android.crypto.storage_rotation.json_object
import org.astermail.android.crypto.storage_rotation.url_component

private const val SERVER_SEAL_HEADER = "-----BEGIN PGP MESSAGE-----"
private const val SERVER_SEAL_SETTLED_LIMIT = 20_000

fun is_server_pgp_seal(encrypted_b64: String?, nonce_b64: String?): Boolean {
    if (encrypted_b64.isNullOrBlank()) return false
    if (!nonce_b64.isNullOrBlank()) {
        val nonce = runCatching { Base64.getMimeDecoder().decode(nonce_b64.trim()) }.getOrNull() ?: return false
        if (nonce.isNotEmpty()) return false
    }
    val raw = runCatching { Base64.getMimeDecoder().decode(encrypted_b64.trim()) }.getOrNull() ?: return false
    return String(raw, Charsets.UTF_8).trimStart().startsWith(SERVER_SEAL_HEADER)
}

class ServerSealResealer(
    private val api: ResealApi,
    private val crypto: () -> MailLegacyCrypto,
) {
    private val settled = ConcurrentHashMap.newKeySet<String>()

    suspend fun reseal(id: String, encrypted_b64: String): LegacyOutcome {
        if (id.isBlank()) return LegacyOutcome.SKIPPED
        if (settled.size >= SERVER_SEAL_SETTLED_LIMIT) settled.clear()
        if (!settled.add(id)) return LegacyOutcome.SKIPPED
        var retry_later = true
        try {
            val outcome = attempt(id, encrypted_b64)
            retry_later = outcome == LegacyOutcome.PAUSED
            return outcome
        } finally {
            if (retry_later) settled.remove(id)
        }
    }

    private suspend fun attempt(id: String, encrypted_b64: String): LegacyOutcome {
        if (!is_server_pgp_seal(encrypted_b64, null)) return LegacyOutcome.SKIPPED
        val adapter = crypto()
        val record = adapter.classify_server_seal(encrypted_b64) as? StoredRecord.Legacy
            ?: return LegacyOutcome.SKIPPED
        try {
            val expected = adapter.stored_sha256_hex(encrypted_b64) ?: return LegacyOutcome.SKIPPED
            val sealed = adapter.reseal_envelope(record.plaintext) ?: return LegacyOutcome.PAUSED
            if (!is_rewrap_nonce(sealed.nonce)) return LegacyOutcome.SKIPPED
            return try {
                classify_legacy_reply(
                    api.put(
                        "/mail/v1/messages/${url_component(id)}/envelope-rewrap",
                        json_object(
                            "encrypted_envelope" to sealed.encrypted,
                            "envelope_nonce" to sealed.nonce,
                            "expected_envelope_sha256" to expected,
                        ),
                    ),
                )
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (_: Throwable) {
                LegacyOutcome.PAUSED
            }
        } finally {
            record.plaintext.fill(0)
        }
    }
}

object ServerSealHook {
    @Volatile
    private var listener: ((String, String) -> Unit)? = null

    fun attach(block: (String, String) -> Unit) {
        listener = block
    }

    fun offer(item_id: String, encrypted_b64: String) {
        listener?.let { runCatching { it(item_id, encrypted_b64) } }
    }
}
