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

import java.util.Base64
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonPrimitive
import org.astermail.android.mail.LevelTwoEnvelope
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class EnvelopeV2MetaMarkerTest {

    private val v1_tag: Byte = 1
    private val v2_tag: Byte = 2

    private fun b64(bytes: ByteArray): String = Base64.getEncoder().encodeToString(bytes)

    private fun unb64(value: String): ByteArray = Base64.getDecoder().decode(value)

    private fun sealed(tag: Byte, text: String): String = b64(byteArrayOf(tag) + text.toByteArray(Charsets.UTF_8))

    private inner class TaggedCrypto : EnvelopeV2Crypto {
        override fun is_v2(sealed_b64: String): Boolean = unb64(sealed_b64).firstOrNull() == v2_tag

        override fun open_v1(sealed_b64: String): ByteArray? {
            val raw = unb64(sealed_b64)
            if (raw.firstOrNull() != v1_tag) return null
            return raw.copyOfRange(1, raw.size)
        }

        override fun seal_v2(plaintext: ByteArray): String? = b64(byteArrayOf(v2_tag) + plaintext)
    }

    private class MetaRow(var encrypted_meta: String, var meta_nonce: String)

    private inner class MetaServer : ResealApi {
        val metas = LinkedHashMap<String, MetaRow>()
        val meta_puts = mutableListOf<Pair<String, JsonObject>>()

        override suspend fun get(path: String): ApiReply {
            if (!path.startsWith("$SENT_MESSAGES_PATH?")) return ApiReply(404)
            val items = if (path.contains("item_type=$SENT_ITEM_TYPE")) {
                listOf(
                    JsonObject(
                        mapOf(
                            "id" to JsonPrimitive("m1"),
                            "item_type" to JsonPrimitive(SENT_ITEM_TYPE),
                            "encrypted_envelope" to JsonPrimitive(sealed(v2_tag, "envelope")),
                            "envelope_nonce" to JsonPrimitive(LevelTwoEnvelope.marker_b64()),
                            "has_attachments" to JsonPrimitive(true),
                        ),
                    ),
                )
            } else {
                emptyList()
            }
            return ApiReply(
                200,
                JsonObject(
                    mapOf(
                        "items" to JsonArray(items),
                        "has_more" to JsonPrimitive(false),
                        "next_cursor" to JsonPrimitive(""),
                    ),
                ),
            )
        }

        override suspend fun post(path: String, body: JsonElement): ApiReply {
            if (path == ENVELOPE_V2_PATH) {
                return ApiReply(200, JsonObject(mapOf("level2_admitted" to JsonPrimitive(true))))
            }
            if (path == ATTACHMENT_META_BATCH_PATH) {
                val rows = metas.map { (id, row) ->
                    JsonObject(
                        mapOf(
                            "id" to JsonPrimitive(id),
                            "encrypted_meta" to JsonPrimitive(row.encrypted_meta),
                            "meta_nonce" to JsonPrimitive(row.meta_nonce),
                        ),
                    )
                }
                return ApiReply(200, JsonObject(mapOf("items" to JsonObject(mapOf("m1" to JsonArray(rows))))))
            }
            return ApiReply(404)
        }

        override suspend fun put(path: String, body: JsonElement): ApiReply {
            val record = body as JsonObject
            val id = path.removePrefix("/mail/v1/attachments/").removeSuffix("/meta")
            val row = metas[id] ?: return ApiReply(404)
            meta_puts.add(id to record)
            row.encrypted_meta = record.getValue("encrypted_meta").jsonPrimitive.contentOrNull!!
            row.meta_nonce = record.getValue("meta_nonce").jsonPrimitive.contentOrNull!!
            return ApiReply(200)
        }
    }

    private fun view(): VaultKeyView = VaultKeyView(
        data_kek = b64(ByteArray(32) { (it + 7).toByte() }),
        data_kek_random = true,
        storage_rekey_state = null,
        prior_keys = emptyList(),
        legacy_keks = emptyList(),
    )

    private fun sweep(server: MetaServer) = runBlocking {
        run_envelope_v2_sweep(
            view(),
            server,
            MemoryProgressStore(),
            TaggedCrypto(),
            sleep = {},
            draft_in_use = { false },
        )
    }

    @Test
    fun rewrapped_metas_are_written_under_the_shared_marker() {
        val server = MetaServer()
        val zero = b64(ByteArray(12))
        val legacy = b64(ByteArray(12) { (it + 1).toByte() })
        server.metas["a_zero"] = MetaRow(sealed(v1_tag, "meta zero"), zero)
        server.metas["a_marker_v1"] = MetaRow(sealed(v1_tag, "meta mark"), LevelTwoEnvelope.meta_marker_b64())
        server.metas["a_current"] = MetaRow(sealed(v2_tag, "meta done"), LevelTwoEnvelope.meta_marker_b64())
        server.metas["a_legacy"] = MetaRow(sealed(v1_tag, "meta keep"), legacy)

        sweep(server)

        assertEquals(listOf("a_zero", "a_marker_v1"), server.meta_puts.map { it.first })
        for ((_, body) in server.meta_puts) {
            assertEquals("AgAAAAAAAAAAAAAA", body.getValue("meta_nonce").jsonPrimitive.contentOrNull)
        }
        assertTrue(crypto_is_v2(server.metas.getValue("a_zero").encrypted_meta))
        assertEquals(legacy, server.metas.getValue("a_legacy").meta_nonce)
        assertEquals(sealed(v1_tag, "meta keep"), server.metas.getValue("a_legacy").encrypted_meta)
        assertEquals(sealed(v2_tag, "meta done"), server.metas.getValue("a_current").encrypted_meta)
    }

    @Test
    fun a_second_sweep_leaves_marker_metas_alone() {
        val server = MetaServer()
        server.metas["a_zero"] = MetaRow(sealed(v1_tag, "meta zero"), b64(ByteArray(12)))

        sweep(server)
        server.meta_puts.clear()
        sweep(server)

        assertTrue(server.meta_puts.isEmpty())
        assertEquals("AgAAAAAAAAAAAAAA", server.metas.getValue("a_zero").meta_nonce)
    }

    private fun crypto_is_v2(sealed_b64: String): Boolean = TaggedCrypto().is_v2(sealed_b64)
}
