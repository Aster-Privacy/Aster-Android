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
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.astermail.android.crypto.AesGcm
import org.astermail.android.crypto.PasswordKdf
import org.astermail.android.crypto.storage_rotation.ApiReply
import org.astermail.android.crypto.storage_rotation.ResealApi
import org.astermail.android.storage.SessionKeyStore
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class MailLegacyLoginSaltTest {

    private val passphrase = "correct horse battery staple"
    private val login_salt = ByteArray(16) { (it + 90).toByte() }
    private val iv = ByteArray(12) { (it + 7).toByte() }
    private val plaintext = "{\"subject\":\"kept\"}".toByteArray()
    private val data_kek = ByteArray(32) { 5 }
    private val envelope_key = PasswordKdf.derive_aes_key(passphrase.toByteArray(), login_salt, 310000)
    private val login_salted_envelope = b64(login_salt + iv + AesGcm.encrypt(envelope_key, iv, plaintext))

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

    private lateinit var session_key_store: SessionKeyStore
    private lateinit var repo: MailRepository

    private fun b64(bytes: ByteArray): String = Base64.getEncoder().encodeToString(bytes)

    private fun item(envelope: String = login_salted_envelope, nonce: String = "AQ==") = JsonObject(
        mapOf(
            "id" to JsonPrimitive("m1"),
            "item_type" to JsonPrimitive("received"),
            "encrypted_envelope" to JsonPrimitive(envelope),
            "envelope_nonce" to JsonPrimitive(nonce),
        ),
    )

    private fun parsed(bytes: ByteArray?): JsonObject = Json.parseToJsonElement(String(bytes!!)).jsonObject

    private fun pass(
        api: ResealApi,
        marker: MutableList<Boolean>,
        flags: MailLegacyFlagStore = MemoryMailLegacyFlagStore(),
        crypto: MailLegacyCrypto = repo.mail_legacy_crypto(),
        keys_ready: suspend () -> Boolean = { true },
        on_progress: (Int, Int) -> Unit = { _, _ -> },
    ) = MailLegacyMigration(
        MailLegacyMigrationDeps(
            user_id = "u1",
            api = api,
            crypto = crypto,
            flags = flags,
            is_retired = { false },
            protected_folder_tokens = { emptySet() },
            write_marker = {
                marker += true
                true
            },
            on_progress = on_progress,
            stored_keys_ready = keys_ready,
        ),
    )

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
        every { session_key_store.get_passphrase() } answers { passphrase.toByteArray() }
        every { session_key_store.mail_legacy_retired() } returns false
        every { session_key_store.get_decrypt_keks() } returns emptyList()
        every { session_key_store.is_level2_admitted() } returns true
        every { session_key_store.get_data_kek() } answers { data_kek.copyOf() }
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
    fun metadata_sealed_under_a_public_constant_is_left_out_of_the_migration() {
        val constant = org.astermail.android.crypto.StorageWriteKey.public_mail_kek_raws().first()
        val key = org.astermail.android.crypto.storage_rotation.hkdf_sha256(
            constant,
            "aster-metadata-salt-v1".toByteArray(Charsets.UTF_8),
            "aster-metadata-encryption-v1:mail-item-metadata".toByteArray(Charsets.UTF_8),
            32,
        )
        val nonce = ByteArray(12) { 6 }
        val sealed = AesGcm.encrypt(key, nonce, "{\"folder\":\"inbox\"}".toByteArray(Charsets.UTF_8))

        val record = repo.mail_legacy_crypto().classify_metadata(b64(sealed), b64(nonce))

        assertTrue("constant metadata must not be offered for reseal", record is StoredRecord.Unreadable)
    }

    @Test
    fun a_login_salted_item_the_vault_vouches_for_is_resealed_and_opens_without_the_salt() = runBlocking {
        every { session_key_store.get_legacy_keks() } returns listOf(b64(envelope_key))
        val crypto = repo.mail_legacy_crypto()
        val opened = crypto.classify_envelope(login_salted_envelope, "AQ==")
        assertTrue("vault-vouched login-salted envelope should open read-only", opened is StoredRecord.Legacy)
        assertEquals(marked_plaintext, parsed((opened as StoredRecord.Legacy).plaintext))

        val api = RecordingApi(listOf(item()))
        val marker = mutableListOf<Boolean>()
        assertEquals(MailLegacyMigrationResult.DONE, pass(api, marker).run())
        assertEquals(listOf("/mail/v1/messages/m1/envelope-rewrap"), api.puts.map { it.first })
        val body = api.puts.single().second.jsonObject
        val sealed = body.getValue("encrypted_envelope").jsonPrimitive.content
        val sealed_nonce = body.getValue("envelope_nonce").jsonPrimitive.content
        assertNotEquals("AQ==", sealed_nonce)
        assertFalse(Base64.getDecoder().decode(sealed).copyOfRange(0, 16).contentEquals(login_salt))
        assertEquals(listOf(true), marker)

        assertEquals("Ag==", sealed_nonce)
        assertEquals(marked_plaintext, parsed(LevelTwoEnvelope.open_b64(sealed, listOf(data_kek))))
    }

    private val marked_plaintext = JsonObject(
        mapOf("subject" to JsonPrimitive("kept"), "legacy_unverified" to JsonPrimitive(true)),
    )

    @Test
    fun an_envelope_forged_from_the_login_hash_alone_is_resealed_unverified_and_never_counts_as_own_sealed() = runBlocking {
        val login_hash = PasswordKdf.derive_aes_key(passphrase.toByteArray(), login_salt, 310000)
        val forged_body = (
            "{\"subject\":\"forged\",\"body_text\":\"pay here\",\"from\":\"me@example.org\"," +
                "\"sent_encryption\":{\"outside\":1,\"outside_pgp\":1,\"internal_readable\":0}}"
            ).toByteArray()
        val forged_iv = ByteArray(12) { (it + 40).toByte() }
        val forged = b64(login_salt + forged_iv + AesGcm.encrypt(login_hash, forged_iv, forged_body))
        every { session_key_store.get_legacy_keks() } returns listOf(b64(login_hash))

        val api = RecordingApi(listOf(item(forged)))
        assertEquals(MailLegacyMigrationResult.DONE, pass(api, mutableListOf()).run())
        val body = api.puts.single().second.jsonObject
        val sealed = body.getValue("encrypted_envelope").jsonPrimitive.content
        val sealed_nonce = body.getValue("envelope_nonce").jsonPrimitive.content
        val resealed = parsed(LevelTwoEnvelope.open_b64(sealed, listOf(data_kek)))
        assertEquals(JsonPrimitive(true), resealed["legacy_unverified"])

        val trusted = LevelTwoEnvelope.seal_b64(forged_body, data_kek)
        val control = repo.decrypt_envelope_with_heal(trusted, "Ag==").envelope!!
        assertTrue(control.own_sealed)

        val envelope = repo.decrypt_envelope_with_heal(sealed, sealed_nonce).envelope!!
        assertEquals("forged", envelope.subject)
        assertFalse(envelope.own_sealed)
        assertNull(envelope.sent_encryption_scope)
    }

    private val sent_evidence_body = (
        "{\"subject\":\"forged\",\"body_text\":\"pay here\",\"from\":\"me@example.org\"," +
            "\"sent_encryption\":{\"outside\":1,\"outside_pgp\":1,\"internal_readable\":0}}"
        ).toByteArray()

    private fun random_salted_envelope(key: ByteArray, body: ByteArray): String {
        val random_salt = ByteArray(16) { (it * 7 + 3).toByte() }
        val random_iv = ByteArray(12) { (it + 61).toByte() }
        return b64(random_salt + random_iv + AesGcm.encrypt(key, random_iv, body))
    }

    private fun hold_fallback_kek(kek: ByteArray) {
        every { session_key_store.get_decrypt_keks() } returns listOf(b64(kek))
        every { session_key_store.get_legacy_keks() } returns listOf(b64(kek))
        every { session_key_store.get_password_salt() } answers { login_salt.copyOf() }
        every { session_key_store.get_retired_password_salts() } returns emptyList()
    }

    private fun resealed_envelope(api: RecordingApi): Pair<String, String> {
        val body = api.puts.single().second.jsonObject
        return body.getValue("encrypted_envelope").jsonPrimitive.content to
            body.getValue("envelope_nonce").jsonPrimitive.content
    }

    @Test
    fun a_random_salt_envelope_opened_only_by_the_login_hash_kek_is_resealed_unverified() = runBlocking {
        val login_hash = PasswordKdf.derive_aes_key(passphrase.toByteArray(), login_salt, 310000)
        hold_fallback_kek(login_hash)
        val forged = random_salted_envelope(login_hash, sent_evidence_body)

        val read = repo.decrypt_envelope_with_heal(forged, "AQ==").envelope!!
        assertEquals("forged", read.subject)
        assertFalse(read.own_sealed)
        assertNull(read.sent_encryption_scope)

        val api = RecordingApi(listOf(item(forged)))
        assertEquals(MailLegacyMigrationResult.DONE, pass(api, mutableListOf()).run())
        val (sealed, sealed_nonce) = resealed_envelope(api)
        val resealed = parsed(LevelTwoEnvelope.open_b64(sealed, listOf(data_kek)))
        assertEquals(JsonPrimitive(true), resealed["legacy_unverified"])

        val envelope = repo.decrypt_envelope_with_heal(sealed, sealed_nonce).envelope!!
        assertEquals("forged", envelope.subject)
        assertFalse(envelope.own_sealed)
        assertNull(envelope.sent_encryption_scope)
    }

    @Test
    fun a_random_salt_envelope_under_a_kek_the_server_cannot_derive_stays_trusted() = runBlocking {
        val private_kek = ByteArray(32) { 9 }
        hold_fallback_kek(private_kek)
        val trusted = random_salted_envelope(private_kek, sent_evidence_body)

        val api = RecordingApi(listOf(item(trusted)))
        assertEquals(MailLegacyMigrationResult.DONE, pass(api, mutableListOf()).run())
        val (sealed, sealed_nonce) = resealed_envelope(api)
        val resealed = parsed(LevelTwoEnvelope.open_b64(sealed, listOf(data_kek)))
        assertNull(resealed["legacy_unverified"])

        val envelope = repo.decrypt_envelope_with_heal(sealed, sealed_nonce).envelope!!
        assertEquals("forged", envelope.subject)
        assertTrue(envelope.own_sealed)
        assertEquals(JsonPrimitive(1), resealed["sent_encryption"]!!.jsonObject["outside_pgp"])
    }

    @Test
    fun an_item_seen_before_the_keys_are_ready_is_not_marked_unreadable_when_they_become_ready_mid_pass() = runBlocking {
        every { session_key_store.get_legacy_keks() } returns listOf(b64(ByteArray(32) { 9 }))
        var ready = false
        val marker = mutableListOf<Boolean>()
        val migration = pass(
            RecordingApi(listOf(item())),
            marker,
            keys_ready = { ready },
            on_progress = { _, _ -> ready = true },
        )
        val result = migration.run()
        assertNotEquals(MailLegacyMigrationResult.DONE, result)
        assertTrue(migration.unreadable_item_ids().isEmpty())
        assertEquals(setOf("m1"), migration.recovery_item_ids())
        assertFalse(migration.is_main_pass_done())
        assertTrue(marker.isEmpty())
    }

    @Test
    fun an_unreadable_item_is_cleared_once_it_later_opens() = runBlocking {
        every { session_key_store.get_legacy_keks() } returns listOf(b64(ByteArray(32) { 9 }))
        val flags = MemoryMailLegacyFlagStore()
        val first = pass(RecordingApi(listOf(item())), mutableListOf(), flags)
        assertEquals(MailLegacyMigrationResult.DONE, first.run())
        assertEquals(setOf("m1"), first.unreadable_item_ids())

        flags.put("main_done_u1", null)
        val resealed_elsewhere = LevelTwoEnvelope.seal_b64(plaintext, data_kek)
        val second = pass(RecordingApi(listOf(item(resealed_elsewhere, "Ag=="))), mutableListOf(), flags)
        assertEquals(MailLegacyMigrationResult.DONE, second.run())
        assertTrue(second.unreadable_item_ids().isEmpty())
        assertTrue(second.recovery_item_ids().isEmpty())
    }

    @Test
    fun a_skipped_item_is_cleared_once_a_later_pass_reseals_it() = runBlocking {
        every { session_key_store.get_legacy_keks() } returns listOf(b64(envelope_key))
        val flags = MemoryMailLegacyFlagStore()
        val real = repo.mail_legacy_crypto()
        val refusing = object : MailLegacyCrypto by real {
            override fun reseal_envelope(plaintext: ByteArray): SealedField? = null
        }
        val first = pass(RecordingApi(listOf(item())), mutableListOf(), flags, crypto = refusing)
        assertEquals(MailLegacyMigrationResult.FAILED, first.run())
        assertEquals(setOf("m1"), first.skipped_item_ids())

        val api = RecordingApi(listOf(item()))
        val second = pass(api, mutableListOf(), flags)
        assertEquals(MailLegacyMigrationResult.DONE, second.run())
        assertEquals(1, api.puts.size)
        assertTrue(second.skipped_item_ids().isEmpty())
    }

    @Test
    fun a_login_salted_item_whose_key_the_vault_does_not_hold_is_refused_and_kept_unreadable() = runBlocking {
        every { session_key_store.get_legacy_keks() } returns listOf(b64(ByteArray(32) { 9 }))
        val crypto = repo.mail_legacy_crypto()
        assertTrue(crypto.classify_envelope(login_salted_envelope, "AQ==") is StoredRecord.Unreadable)

        val api = RecordingApi(listOf(item()))
        val marker = mutableListOf<Boolean>()
        val migration = pass(api, marker)
        val result = migration.run()
        assertTrue(api.puts.isEmpty())
        assertEquals(setOf("m1"), migration.unreadable_item_ids())
        assertEquals(MailLegacyMigrationResult.DONE, result)
        assertEquals(listOf(true), marker)
    }
}
