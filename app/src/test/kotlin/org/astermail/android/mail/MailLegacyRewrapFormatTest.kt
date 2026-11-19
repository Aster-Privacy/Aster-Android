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

import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkStatic
import io.mockk.unmockkAll
import java.util.Base64
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import org.astermail.android.crypto.storage_rotation.ApiReply
import org.astermail.android.crypto.storage_rotation.ResealApi
import org.astermail.android.storage.SessionKeyStore
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class MailLegacyRewrapFormatTest {

    private val keyed_nonce = Base64.getEncoder().encodeToString(ByteArray(12) { (it + 1).toByte() })
    private val login_salt = ByteArray(16) { (it + 90).toByte() }

    private class RecordingApi(val items: List<JsonObject>) : ResealApi {
        val puts = mutableListOf<Pair<String, JsonElement>>()
        private var served = false

        override suspend fun get(path: String): ApiReply {
            val page = if (served) emptyList() else items
            served = true
            return ApiReply(200, JsonObject(mapOf("items" to JsonArray(page), "has_more" to JsonPrimitive(false))))
        }

        override suspend fun post(path: String, body: JsonElement): ApiReply =
            ApiReply(200, JsonObject(mapOf("items" to JsonObject(emptyMap()))))

        override suspend fun put(path: String, body: JsonElement): ApiReply {
            puts += path to body
            return ApiReply(200, JsonObject(emptyMap()))
        }
    }

    private class StubCrypto(
        val envelope: StoredRecord,
        val reseal_nonce: String?,
        val login_salted: Boolean = false,
    ) : MailLegacyCrypto {
        override fun classify_envelope(encrypted: String, nonce: String): StoredRecord = envelope
        override fun reseal_envelope(plaintext: ByteArray): SealedField? = reseal_nonce?.let { SealedField("sealed", it) }
        override fun classify_metadata(encrypted: String, nonce: String): StoredRecord = StoredRecord.Current
        override fun reseal_metadata(plaintext: ByteArray): SealedField? = null
        override fun classify_attachment_meta(encrypted: String, nonce: String): StoredRecord = StoredRecord.Current
        override fun reseal_attachment_meta(plaintext: ByteArray): SealedField? = null
        override fun stored_sha256_hex(encrypted: String): String = "sha"
        override fun is_login_salted_envelope(encrypted: String, nonce: String): Boolean = login_salted
    }

    private fun item(id: String, nonce: String) = JsonObject(
        mapOf(
            "id" to JsonPrimitive(id),
            "item_type" to JsonPrimitive("received"),
            "encrypted_envelope" to JsonPrimitive("env_$id"),
            "envelope_nonce" to JsonPrimitive(nonce),
        ),
    )

    private fun migration(
        api: ResealApi,
        crypto: MailLegacyCrypto,
        flags: MailLegacyFlagStore = MemoryMailLegacyFlagStore(),
        keys_ready: Boolean = true,
        marker_writes: MutableList<Unit> = mutableListOf(),
    ) =
        MailLegacyMigration(
            MailLegacyMigrationDeps(
                user_id = "u1",
                api = api,
                crypto = crypto,
                flags = flags,
                is_retired = { false },
                protected_folder_tokens = { emptySet() },
                write_marker = {
                    marker_writes += Unit
                    true
                },
                stored_keys_ready = { keys_ready },
            ),
        )

    private lateinit var session_key_store: SessionKeyStore
    private lateinit var repo: MailRepository

    @Before
    fun setup() {
        org.astermail.android.crypto.stored_key_readiness.install { true }
        mockkStatic(android.util.Base64::class)
        every { android.util.Base64.decode(any<String>(), any()) } answers {
            Base64.getMimeDecoder().decode(firstArg<String>())
        }
        every { android.util.Base64.encodeToString(any(), any()) } answers {
            Base64.getEncoder().encodeToString(firstArg<ByteArray>())
        }
        session_key_store = mockk(relaxed = true)
        every { session_key_store.is_login_salt(any()) } answers { firstArg<ByteArray>().contentEquals(login_salt) }
        repo = MailRepository(
            mail_api = mockk(relaxed = true),
            send_api = mockk(relaxed = true),
            snooze_api = mockk(relaxed = true),
            labels_api = mockk(relaxed = true),
            keys_api = mockk(relaxed = true),
            session_key_store = session_key_store,
            scheduled_api = mockk(relaxed = true),
            ratchet_decryptor = mockk(relaxed = true),
            ratchet_encryptor = mockk(relaxed = true),
            ratchet_plaintext_cache = mockk(relaxed = true),
            system_folder_bootstrap = mockk(relaxed = true),
            pending_send_dao_provider = dagger.Lazy { mockk(relaxed = true) },
            message_body_dao_provider = dagger.Lazy { mockk(relaxed = true) },
            thread_snapshot_dao_provider = dagger.Lazy { mockk(relaxed = true) },
            context = mockk(relaxed = true),
            auth_repository = mockk(relaxed = true),
        )
    }

    @After
    fun teardown() {
        org.astermail.android.crypto.stored_key_readiness.install { false }
        unmockkAll()
    }

    @Test
    fun rewrap_nonce_check_matches_the_server_vectors() {
        assertTrue(is_rewrap_nonce(Base64.getEncoder().encodeToString(ByteArray(12))))
        assertTrue(is_rewrap_nonce("Ag=="))
        assertFalse(is_rewrap_nonce("AQ=="))
        assertFalse(is_rewrap_nonce(""))
        assertFalse(is_rewrap_nonce(null))
        assertFalse(is_rewrap_nonce(Base64.getEncoder().encodeToString(ByteArray(16))))
    }

    @Test
    fun the_repository_reseals_envelopes_in_the_current_format() {
        every { session_key_store.is_level2_admitted() } returns true
        every { session_key_store.get_data_kek() } answers { ByteArray(32) { 5 } }
        val sealed = repo.mail_legacy_crypto().reseal_envelope("{\"a\":1}".toByteArray())
        assertNotNull(sealed)
        assertEquals("Ag==", sealed!!.nonce)
        assertTrue(is_rewrap_nonce(sealed.nonce))

        every { session_key_store.is_level2_admitted() } returns false
        every { session_key_store.get_identity_key() } returns "identity secret"
        val fallback = repo.mail_legacy_crypto().reseal_envelope("{\"a\":1}".toByteArray())
        assertTrue(fallback == null || is_rewrap_nonce(fallback.nonce))
    }

    @Test
    fun the_repository_recognizes_login_salted_password_envelopes() {
        val crypto = repo.mail_legacy_crypto()
        val login_salted = Base64.getEncoder().encodeToString(login_salt + ByteArray(12) + ByteArray(32) { 3 })
        val other_salted = Base64.getEncoder().encodeToString(ByteArray(16) { 1 } + ByteArray(12) + ByteArray(32) { 3 })
        assertTrue(crypto.is_login_salted_envelope(login_salted, "AQ=="))
        assertFalse(crypto.is_login_salted_envelope(other_salted, "AQ=="))
        assertFalse(crypto.is_login_salted_envelope(login_salted, keyed_nonce))
    }

    @Test
    fun a_failed_reseal_is_recorded_and_the_pass_goes_on() = runBlocking {
        val api = RecordingApi(listOf(item("m1", keyed_nonce), item("m2", keyed_nonce)))
        var calls = 0
        val crypto = object : MailLegacyCrypto by StubCrypto(StoredRecord.Legacy(ByteArray(0)), null) {
            override fun classify_envelope(encrypted: String, nonce: String): StoredRecord =
                StoredRecord.Legacy("{\"a\":1}".toByteArray())

            override fun reseal_envelope(plaintext: ByteArray): SealedField? {
                calls += 1
                return if (calls == 1) null else SealedField("sealed", keyed_nonce)
            }
        }
        val flags = MemoryMailLegacyFlagStore()
        val pass = migration(api, crypto, flags)
        assertEquals(MailLegacyMigrationResult.FAILED, pass.run())
        assertEquals(listOf("/mail/v1/messages/m2/envelope-rewrap"), api.puts.map { it.first })
        assertEquals(setOf("m1"), pass.skipped_item_ids())
    }

    @Test
    fun a_password_format_reseal_is_recorded_instead_of_uploaded() = runBlocking {
        val api = RecordingApi(listOf(item("m1", keyed_nonce)))
        val pass = migration(api, StubCrypto(StoredRecord.Legacy("{\"a\":1}".toByteArray()), "AQ=="))
        assertEquals(MailLegacyMigrationResult.FAILED, pass.run())
        assertTrue(api.puts.isEmpty())
        assertEquals(setOf("m1"), pass.skipped_item_ids())
    }

    @Test
    fun a_login_salted_envelope_no_key_opens_is_kept_as_unreadable_and_retirement_finishes() = runBlocking {
        val api = RecordingApi(listOf(item("m1", "AQ==")))
        val flags = MemoryMailLegacyFlagStore()
        val marker_writes = mutableListOf<Unit>()
        val pass = migration(
            api,
            StubCrypto(StoredRecord.Unreadable, keyed_nonce, login_salted = true),
            flags,
            marker_writes = marker_writes,
        )
        assertEquals(MailLegacyMigrationResult.DONE, pass.run())
        assertTrue(api.puts.isEmpty())
        assertEquals("m1", flags.get("unreadable_u1"))
        assertEquals(null, flags.get("recovery_u1"))
        assertTrue(pass.skipped_item_ids().isEmpty())
        assertEquals(1, marker_writes.size)
        assertEquals(MailLegacyMigrationResult.DONE, pass.run())
        assertEquals("m1", flags.get("unreadable_u1"))
    }

    @Test
    fun a_login_salted_envelope_stays_pending_while_stored_keys_are_not_ready() = runBlocking {
        val api = RecordingApi(listOf(item("m1", "AQ==")))
        val flags = MemoryMailLegacyFlagStore()
        val marker_writes = mutableListOf<Unit>()
        val crypto = StubCrypto(StoredRecord.Unreadable, keyed_nonce, login_salted = true)
        val waiting = migration(api, crypto, flags, keys_ready = false, marker_writes = marker_writes)
        assertEquals(MailLegacyMigrationResult.DEFERRED, waiting.run())
        assertEquals(null, flags.get("unreadable_u1"))
        assertEquals("m1", flags.get("recovery_u1"))
        assertTrue(marker_writes.isEmpty())
        val ready = migration(RecordingApi(listOf(item("m1", "AQ=="))), crypto, flags, marker_writes = marker_writes)
        assertEquals(MailLegacyMigrationResult.DONE, ready.run())
        assertEquals("m1", flags.get("unreadable_u1"))
        assertEquals(1, marker_writes.size)
    }

    @Test
    fun an_item_that_later_succeeds_leaves_the_skipped_list() = runBlocking {
        val flags = MemoryMailLegacyFlagStore()
        val first = migration(
            RecordingApi(listOf(item("m1", keyed_nonce))),
            StubCrypto(StoredRecord.Legacy("{\"a\":1}".toByteArray()), null),
            flags,
        )
        assertEquals(MailLegacyMigrationResult.FAILED, first.run())
        assertEquals(setOf("m1"), first.skipped_item_ids())
        val second = migration(
            RecordingApi(listOf(item("m1", keyed_nonce))),
            StubCrypto(StoredRecord.Legacy("{\"a\":1}".toByteArray()), keyed_nonce),
            flags,
        )
        assertEquals(MailLegacyMigrationResult.DONE, second.run())
        assertTrue(second.skipped_item_ids().isEmpty())
    }
}
