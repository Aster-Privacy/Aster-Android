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

import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.astermail.android.crypto.StorageWriteKey
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class EscrowConstantHoldTest {

    private class EscrowApi(private val entries: List<JsonObject>) : ResealApi {
        val posts = ArrayList<Pair<String, JsonElement>>()

        override suspend fun get(path: String): ApiReply = ApiReply(200, json_object("entries" to JsonArray(entries)))

        override suspend fun post(path: String, body: JsonElement): ApiReply {
            posts.add(path to body)
            return ApiReply(200, json_object("ok" to true))
        }

        override suspend fun put(path: String, body: JsonElement): ApiReply = ApiReply(200, json_object("ok" to true))
    }

    private val new_key = ByteArray(32) { (it + 40).toByte() }
    private val old_key = ByteArray(32) { (it + 70).toByte() }

    private fun row(message_id: String, base: ByteArray, text: String): JsonObject {
        val sealed = gcm_seal(derive_escrow_key(base), text.toByteArray(Charsets.UTF_8), escrow_aad_v2(message_id))
        return json_object(
            "message_id" to message_id,
            "encrypted_plaintext" to sealed.ciphertext,
            "plaintext_nonce" to sealed.nonce,
        )
    }

    private suspend fun run_escrow(api: EscrowApi): StepResult {
        val view = VaultKeyView(
            data_kek = encode_b64(new_key),
            data_kek_random = true,
            storage_rekey_state = null,
            prior_keys = emptyList(),
            legacy_keks = listOf(encode_b64(old_key)),
        )
        val keys = build_reseal_keys(view)!!
        val ctx = ResealContext(
            keys = keys,
            api = api,
            progress = ResealProgress(keys.key_id),
            persist = {},
            sleep = {},
            still_current = { true },
        )
        return try {
            escrow_step.run(ctx)
        } finally {
            keys.dispose()
        }
    }

    @Test
    fun an_escrow_row_sealed_under_a_public_constant_is_neither_resealed_nor_deleted() = runBlocking {
        val constant = StorageWriteKey.public_mail_kek_raws().first()
        val api = EscrowApi(listOf(row("planted", constant, "planted key"), row("mine", old_key, "my copy")))

        assertEquals(StepResult.DONE, run_escrow(api))

        assertEquals(listOf(ESCROW_UPSERT_PATH), api.posts.map { it.first })
        assertEquals("mine", (api.posts.single().second as JsonObject)["message_id"]?.jsonPrimitive?.content)
    }

    @Test
    fun every_public_constant_escrow_row_is_held() = runBlocking {
        val rows = StorageWriteKey.public_mail_kek_raws().mapIndexed { index, constant ->
            row("planted-$index", constant, "planted key $index")
        }
        val api = EscrowApi(rows)

        assertEquals(StepResult.DONE, run_escrow(api))

        assertTrue(api.posts.isEmpty())
    }
}
