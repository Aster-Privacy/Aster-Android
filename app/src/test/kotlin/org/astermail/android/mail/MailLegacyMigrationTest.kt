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

import io.mockk.coEvery
import io.mockk.mockk
import java.util.Base64
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.jsonPrimitive
import org.astermail.android.crypto.storage_rotation.ApiReply
import org.astermail.android.crypto.storage_rotation.ResealApi
import org.astermail.android.crypto.storage_rotation.VaultDraft
import org.astermail.android.crypto.storage_rotation.VaultEdit
import org.astermail.android.crypto.storage_rotation.VaultUpdateOutcome
import org.astermail.android.crypto.storage_rotation.VaultUpdater
import org.astermail.android.storage.SessionKeyStore
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.After
import org.junit.Before
import org.junit.Test

class MailLegacyMigrationTest {

    private val keyed_nonce = Base64.getEncoder().encodeToString(ByteArray(12) { (it + 1).toByte() })
    private val resealed_nonce = Base64.getEncoder().encodeToString(ByteArray(12) { (it + 40).toByte() })
    private val identity_meta_nonce = Base64.getEncoder().encodeToString(ByteArray(12) { (it + 7).toByte() })

    private fun server_accepts_rewrap_nonce(nonce_b64: String): Boolean {
        val nonce = Base64.getDecoder().decode(nonce_b64)
        return nonce.size == 12 || nonce.contentEquals(byteArrayOf(2))
    }

    private class FakeApi(
        val pages: MutableList<ApiReply>,
        val put_reply: (String) -> ApiReply = { ApiReply(200, JsonObject(emptyMap())) },
        val post_reply: (JsonElement) -> ApiReply = { ApiReply(200, JsonObject(mapOf("items" to JsonObject(emptyMap())))) },
    ) : ResealApi {
        val gets = mutableListOf<String>()
        val puts = mutableListOf<Pair<String, JsonElement>>()

        override suspend fun get(path: String): ApiReply {
            gets += path
            return if (pages.isEmpty()) ApiReply(200, page(emptyList(), false)) else pages.removeAt(0)
        }

        override suspend fun post(path: String, body: JsonElement): ApiReply = post_reply(body)

        override suspend fun put(path: String, body: JsonElement): ApiReply {
            puts += path to body
            return put_reply(path)
        }
    }

    private class FakeCrypto(
        val legacy: Set<String>,
        val reseal_nonce: String,
        val legacy_meta: Set<String> = emptySet(),
        val seals_probe: Boolean = true,
    ) : MailLegacyCrypto {
        override fun classify_envelope(encrypted: String, nonce: String): StoredRecord =
            if (encrypted in legacy) StoredRecord.Legacy("{\"a\":1}".toByteArray()) else StoredRecord.Current

        override fun reseal_envelope(plaintext: ByteArray): SealedField = SealedField("resealed", reseal_nonce)
        override fun classify_metadata(encrypted: String, nonce: String): StoredRecord =
            if (encrypted in legacy_meta) StoredRecord.Legacy("{\"m\":1}".toByteArray()) else StoredRecord.Current
        override fun reseal_metadata(plaintext: ByteArray): SealedField? =
            if (seals_probe && String(plaintext, Charsets.UTF_8).contains("seal_probe")) SealedField("probe", reseal_nonce) else null
        override fun classify_attachment_meta(encrypted: String, nonce: String): StoredRecord =
            if (encrypted in legacy_meta) StoredRecord.Legacy("{\"n\":1}".toByteArray()) else StoredRecord.Current
        override fun reseal_attachment_meta(plaintext: ByteArray): SealedField? = null
        override fun stored_sha256_hex(encrypted: String): String = "sha_$encrypted"
    }

    companion object {
        fun item(id: String, envelope: String, nonce: String): JsonObject = JsonObject(
            mapOf(
                "id" to JsonPrimitive(id),
                "item_type" to JsonPrimitive("received"),
                "encrypted_envelope" to JsonPrimitive(envelope),
                "envelope_nonce" to JsonPrimitive(nonce),
            ),
        )

        fun page(items: List<JsonObject>, has_more: Boolean, cursor: String? = null): JsonObject = JsonObject(
            buildMap {
                put("items", JsonArray(items))
                put("has_more", JsonPrimitive(has_more))
                if (cursor != null) put("next_cursor", JsonPrimitive(cursor))
            },
        )
    }

    private fun deps(
        api: ResealApi,
        flags: MailLegacyFlagStore = MemoryMailLegacyFlagStore(),
        tokens: Set<String>? = emptySet(),
        marker: () -> Boolean = { true },
        crypto: MailLegacyCrypto = FakeCrypto(setOf("old"), resealed_nonce),
    ) = MailLegacyMigrationDeps(
        user_id = "u1",
        api = api,
        crypto = crypto,
        flags = flags,
        is_retired = { false },
        protected_folder_tokens = { tokens },
        write_marker = { marker() },
    )

    @Before
    fun account_ready() {
        org.astermail.android.crypto.stored_key_readiness.install { true }
    }

    @After
    fun reset_readiness() {
        org.astermail.android.crypto.stored_key_readiness.install { false }
    }

    @Test
    fun retirement_waits_until_the_account_reads_as_ready() = runBlocking {
        var ready = false
        var checks = 0
        org.astermail.android.crypto.stored_key_readiness.install { checks += 1; ready }
        val flags = MemoryMailLegacyFlagStore()
        var marked = 0

        val first = MailLegacyMigration(deps(FakeApi(mutableListOf()), flags, marker = { marked += 1; true })).run()

        assertEquals(MailLegacyMigrationResult.DEFERRED, first)
        assertEquals(0, marked)
        assertEquals(1, checks)

        ready = true
        org.astermail.android.crypto.stored_key_readiness.reset()
        val second = MailLegacyMigration(deps(FakeApi(mutableListOf()), flags, marker = { marked += 1; true })).run()

        assertEquals(MailLegacyMigrationResult.DONE, second)
        assertEquals(1, marked)
    }

    @Test
    fun reply_classification_follows_the_server_contract() {
        assertEquals(LegacyOutcome.WRITTEN, classify_legacy_reply(ApiReply(200)))
        assertEquals(LegacyOutcome.PAUSED, classify_legacy_reply(ApiReply(null)))
        assertEquals(LegacyOutcome.PAUSED, classify_legacy_reply(ApiReply(429)))
        assertEquals(LegacyOutcome.PAUSED, classify_legacy_reply(ApiReply(401)))
        assertEquals(LegacyOutcome.SKIPPED, classify_legacy_reply(ApiReply(404, code = "NOT_FOUND")))
        assertEquals(LegacyOutcome.UNAVAILABLE, classify_legacy_reply(ApiReply(404)))
        assertEquals(LegacyOutcome.UNAVAILABLE, classify_legacy_reply(ApiReply(426)))
        assertEquals(LegacyOutcome.CHANGED, classify_legacy_reply(ApiReply(409, code = "ENVELOPE_CHANGED")))
        assertEquals(LegacyOutcome.FAILED, classify_legacy_reply(ApiReply(400)))
    }

    @Test
    fun legacy_envelope_is_rewrapped_with_the_stored_hash_then_marker_written() = runBlocking {
        val api = FakeApi(
            mutableListOf(ApiReply(200, page(listOf(item("m1", "old", keyed_nonce), item("m2", "new", keyed_nonce)), false))),
        )
        var marked = 0
        val result = MailLegacyMigration(deps(api, marker = { marked += 1; true })).run()
        assertEquals(MailLegacyMigrationResult.DONE, result)
        assertEquals(1, marked)
        assertEquals(1, api.puts.size)
        val (path, body) = api.puts.single()
        assertEquals("/mail/v1/messages/m1/envelope-rewrap", path)
        body as JsonObject
        assertEquals("resealed", body["encrypted_envelope"]!!.jsonPrimitive.content)
        assertEquals(resealed_nonce, body["envelope_nonce"]!!.jsonPrimitive.content)
        assertEquals("sha_old", body["expected_envelope_sha256"]!!.jsonPrimitive.content)
    }

    @Test
    fun marker_waits_for_every_protected_folder() = runBlocking {
        val flags = MemoryMailLegacyFlagStore()
        var marked = 0
        val main = MailLegacyMigration(deps(FakeApi(mutableListOf()), flags, setOf("tok"), { marked += 1; true }))
        assertEquals(MailLegacyMigrationResult.DEFERRED, main.run())
        assertEquals(0, marked)
        assertTrue(main.is_main_pass_done())
        val folder = MailLegacyMigration(deps(FakeApi(mutableListOf()), flags, setOf("tok"), { marked += 1; true }))
        assertEquals(MailLegacyMigrationResult.DONE, folder.run_folder("tok") { true })
        assertEquals(1, marked)
    }

    @Test
    fun folder_pass_pauses_when_the_folder_locks() = runBlocking {
        val migration = MailLegacyMigration(deps(FakeApi(mutableListOf()), tokens = setOf("tok")))
        assertEquals(MailLegacyMigrationResult.PAUSED, migration.run_folder("tok") { false })
        assertFalse(migration.is_folder_pass_done("tok"))
    }

    @Test
    fun unknown_folders_pause_instead_of_retiring() = runBlocking {
        var marked = 0
        val result = MailLegacyMigration(deps(FakeApi(mutableListOf()), tokens = null, marker = { marked += 1; true })).run()
        assertEquals(MailLegacyMigrationResult.PAUSED, result)
        assertEquals(0, marked)
    }

    @Test
    fun changed_envelope_leaves_the_pass_incomplete() = runBlocking {
        val api = FakeApi(
            mutableListOf(ApiReply(200, page(listOf(item("m1", "old", keyed_nonce)), false))),
            put_reply = { ApiReply(409, code = "ENVELOPE_CHANGED") },
        )
        var marked = 0
        val migration = MailLegacyMigration(deps(api, marker = { marked += 1; true }))
        assertEquals(MailLegacyMigrationResult.INCOMPLETE, migration.run())
        assertEquals(0, marked)
        assertFalse(migration.is_main_pass_done())
    }

    @Test
    fun server_errors_fail_and_rate_limits_pause() = runBlocking {
        val failing = FakeApi(
            mutableListOf(ApiReply(200, page(listOf(item("m1", "old", keyed_nonce)), false))),
            put_reply = { ApiReply(500) },
        )
        assertEquals(MailLegacyMigrationResult.FAILED, MailLegacyMigration(deps(failing)).run())
        val limited = FakeApi(
            mutableListOf(ApiReply(200, page(listOf(item("m1", "old", keyed_nonce)), false))),
            put_reply = { ApiReply(429) },
        )
        assertEquals(MailLegacyMigrationResult.PAUSED, MailLegacyMigration(deps(limited)).run())
    }

    @Test
    fun password_marker_envelope_is_resealed_under_a_fresh_nonce() = runBlocking {
        val api = FakeApi(mutableListOf(ApiReply(200, page(listOf(item("m1", "old", "AQ==")), false))))
        assertEquals(MailLegacyMigrationResult.DONE, MailLegacyMigration(deps(api)).run())
        val body = api.puts.single().second as JsonObject
        assertEquals(resealed_nonce, body["envelope_nonce"]!!.jsonPrimitive.content)
    }

    @Test
    fun the_uploaded_envelope_uses_a_nonce_the_server_rewrap_check_accepts() = runBlocking {
        assertTrue(server_accepts_rewrap_nonce(Base64.getEncoder().encodeToString(ByteArray(12))))
        assertTrue(server_accepts_rewrap_nonce("Ag=="))
        assertFalse(server_accepts_rewrap_nonce("AQ=="))
        assertFalse(server_accepts_rewrap_nonce(Base64.getEncoder().encodeToString(ByteArray(16))))
        for (nonce in listOf(resealed_nonce, "Ag==")) {
            val api = FakeApi(mutableListOf(ApiReply(200, page(listOf(item("m1", "old", "AQ==")), false))))
            val result = MailLegacyMigration(deps(api, crypto = FakeCrypto(setOf("old"), nonce))).run()
            assertEquals(MailLegacyMigrationResult.DONE, result)
            val body = api.puts.single().second as JsonObject
            assertTrue(server_accepts_rewrap_nonce(body["envelope_nonce"]!!.jsonPrimitive.content))
        }
    }

    @Test
    fun a_reseal_in_the_password_format_is_never_uploaded() = runBlocking {
        val api = FakeApi(
            mutableListOf(ApiReply(200, page(listOf(item("m1", "old", "AQ=="), item("m2", "old", keyed_nonce)), false))),
        )
        var marked = 0
        val result = MailLegacyMigration(
            deps(api, marker = { marked += 1; true }, crypto = FakeCrypto(setOf("old"), "AQ==")),
        ).run()
        assertEquals(0, api.puts.size)
        assertEquals(MailLegacyMigrationResult.FAILED, result)
        assertEquals(0, marked)
    }

    @Test
    fun a_metadata_item_that_cannot_be_resealed_is_skipped_and_the_pass_goes_on() = runBlocking {
        val bad = JsonObject(
            item("m1", "new", keyed_nonce) + mapOf(
                "encrypted_metadata" to JsonPrimitive("bad_meta"),
                "metadata_nonce" to JsonPrimitive(keyed_nonce),
            ),
        )
        val api = FakeApi(mutableListOf(ApiReply(200, page(listOf(bad, item("m2", "old", keyed_nonce)), false))))
        var marked = 0
        val migration = MailLegacyMigration(
            deps(api, marker = { marked += 1; true }, crypto = FakeCrypto(setOf("old"), resealed_nonce, setOf("bad_meta"))),
        )
        val result = migration.run()
        assertEquals(listOf("/mail/v1/messages/m2/envelope-rewrap"), api.puts.map { it.first })
        assertEquals(MailLegacyMigrationResult.FAILED, result)
        assertEquals(0, marked)
        assertFalse(migration.is_main_pass_done())
    }

    private fun unsealable_meta_item(): JsonObject = JsonObject(
        item("m1", "new", keyed_nonce) + mapOf(
            "encrypted_metadata" to JsonPrimitive("bad_meta"),
            "metadata_nonce" to JsonPrimitive(keyed_nonce),
        ),
    )

    @Test
    fun metadata_that_never_reseals_is_kept_as_unreadable_after_five_passes() = runBlocking {
        val flags = MemoryMailLegacyFlagStore()
        var marked = 0
        val results = (1..5).map {
            val api = FakeApi(mutableListOf(ApiReply(200, page(listOf(unsealable_meta_item()), false))))
            val migration = MailLegacyMigration(
                deps(api, flags, marker = { marked += 1; true }, crypto = FakeCrypto(emptySet(), resealed_nonce, setOf("bad_meta"))),
            )
            migration.run() to migration
        }
        assertEquals(List(4) { MailLegacyMigrationResult.FAILED }, results.take(4).map { it.first })
        assertEquals(MailLegacyMigrationResult.DONE, results.last().first)
        assertEquals(1, marked)
        assertEquals(setOf("m1"), results.last().second.unreadable_item_ids())
    }

    @Test
    fun metadata_reseal_pauses_without_counting_when_the_key_cannot_seal_at_all() = runBlocking {
        val flags = MemoryMailLegacyFlagStore()
        var marked = 0
        val results = (1..6).map {
            val api = FakeApi(mutableListOf(ApiReply(200, page(listOf(unsealable_meta_item()), false))))
            MailLegacyMigration(
                deps(
                    api,
                    flags,
                    marker = { marked += 1; true },
                    crypto = FakeCrypto(emptySet(), resealed_nonce, setOf("bad_meta"), seals_probe = false),
                ),
            ).run()
        }
        assertEquals(List(6) { MailLegacyMigrationResult.PAUSED }, results)
        assertEquals(0, marked)
    }

    @Test
    fun an_attachment_meta_that_cannot_be_resealed_is_skipped_and_the_pass_goes_on() = runBlocking {
        val sent = JsonObject(
            item("m1", "new", keyed_nonce) + mapOf(
                "item_type" to JsonPrimitive("sent"),
                "has_attachments" to JsonPrimitive(true),
            ),
        )
        val meta = JsonObject(
            mapOf(
                "id" to JsonPrimitive("a1"),
                "encrypted_meta" to JsonPrimitive("bad_meta"),
                "meta_nonce" to JsonPrimitive(identity_meta_nonce),
            ),
        )
        val api = FakeApi(
            mutableListOf(ApiReply(200, page(listOf(sent, item("m2", "old", keyed_nonce)), false))),
            post_reply = { ApiReply(200, JsonObject(mapOf("items" to JsonObject(mapOf("m1" to JsonArray(listOf(meta))))))) },
        )
        var marked = 0
        val result = MailLegacyMigration(
            deps(api, marker = { marked += 1; true }, crypto = FakeCrypto(setOf("old"), resealed_nonce, setOf("bad_meta"))),
        ).run()
        assertEquals(listOf("/mail/v1/messages/m2/envelope-rewrap"), api.puts.map { it.first })
        assertEquals(MailLegacyMigrationResult.FAILED, result)
        assertEquals(0, marked)
    }

    @Test
    fun cursor_resumes_only_after_clean_pages() = runBlocking {
        val flags = MemoryMailLegacyFlagStore()
        val api = FakeApi(
            mutableListOf(
                ApiReply(200, page(listOf(item("m1", "new", keyed_nonce)), true, "c1")),
                ApiReply(503),
            ),
        )
        assertEquals(MailLegacyMigrationResult.PAUSED, MailLegacyMigration(deps(api, flags)).run())
        val resumed = FakeApi(mutableListOf())
        MailLegacyMigration(deps(resumed, flags)).run()
        assertTrue(resumed.gets.first().endsWith("&cursor=c1"))
    }

    @Test
    fun scheduler_steps_match_the_web_client() {
        assertTrue(next_mail_key_upgrade_step(MailLegacyMigrationResult.DONE, true, 3) is MailKeyUpgradeStep.Done)
        assertTrue(next_mail_key_upgrade_step(MailLegacyMigrationResult.DEFERRED, false, 0) is MailKeyUpgradeStep.Idle)
        assertTrue(next_mail_key_upgrade_step(MailLegacyMigrationResult.UNAVAILABLE, true, 3) is MailKeyUpgradeStep.Idle)
        val retry = next_mail_key_upgrade_step(MailLegacyMigrationResult.FAILED, false, 1) as MailKeyUpgradeStep.Retry
        assertEquals(2, retry.attempts)
        val paused = next_mail_key_upgrade_step(MailLegacyMigrationResult.PAUSED, false, 3) as MailKeyUpgradeStep.Retry
        assertEquals(3, paused.attempts)
        assertTrue(next_mail_key_upgrade_step(MailLegacyMigrationResult.FAILED, false, 3) is MailKeyUpgradeStep.Failed)
        assertTrue(next_mail_key_upgrade_step(MailLegacyMigrationResult.PAUSED, true, 3) is MailKeyUpgradeStep.Failed)
        assertTrue(MailKeyUpgradeState(MailKeyUpgradePhase.FAILED, 0, true).is_blocking)
        assertFalse(MailKeyUpgradeState(MailKeyUpgradePhase.RUNNING, 0, false).is_blocking)
    }

    @Test
    fun marker_writer_records_version_and_time_then_pins() = runBlocking {
        val store = SessionKeyStore(null).also { it.put_user_id("u1") }
        val updater = mockk<VaultUpdater>()
        val vault = JSONObject()
        coEvery { updater.update(any(), any()) } coAnswers {
            val edit = secondArg<suspend (VaultDraft) -> VaultEdit>()
            assertEquals(VaultEdit.CHANGED, edit(VaultDraft(vault, mockk(relaxed = true))))
            VaultUpdateOutcome.Written(vault, mockk(relaxed = true))
        }
        assertFalse(store.mail_legacy_retired())
        assertTrue(write_mail_legacy_retired_marker(updater, store) { "2026-11-17T00:00:00Z" })
        val marker = vault.getJSONObject(MAIL_LEGACY_RETIRED_FIELD)
        assertEquals(1, marker.getInt("v"))
        assertEquals("2026-11-17T00:00:00Z", marker.getString("at"))
        assertTrue(store.mail_legacy_retired())
    }

    @Test
    fun marker_writer_refuses_when_the_vault_write_fails() = runBlocking {
        val store = SessionKeyStore(null).also { it.put_user_id("u1") }
        val updater = mockk<VaultUpdater>()
        coEvery { updater.update(any(), any()) } returns VaultUpdateOutcome.Failed("conflict")
        assertFalse(write_mail_legacy_retired_marker(updater, store))
        assertFalse(store.mail_legacy_retired())
    }
}
