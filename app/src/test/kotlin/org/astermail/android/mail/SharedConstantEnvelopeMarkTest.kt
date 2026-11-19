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
import java.security.MessageDigest
import java.security.SecureRandom
import javax.crypto.Cipher
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.SecretKeySpec
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.jsonObject
import org.astermail.android.auth.AuthRepository
import org.astermail.android.crypto.EnvelopeV2
import org.astermail.android.storage.SessionKeyStore
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class SharedConstantEnvelopeMarkTest {

    private val envelope_json = """{"subject":"Stored","body_text":"kept on the server"}"""
    private val passphrase = "account passphrase".toByteArray(Charsets.UTF_8)
    private val password_marker_nonce = java.util.Base64.getEncoder().encodeToString(byteArrayOf(1))

    private lateinit var session_key_store: SessionKeyStore
    private lateinit var repo: MailRepository

    @Before
    fun setup() {
        mockkStatic(android.util.Base64::class)
        every { android.util.Base64.decode(any<String>(), any()) } answers {
            java.util.Base64.getMimeDecoder().decode(firstArg<String>())
        }
        every { android.util.Base64.encodeToString(any(), any()) } answers {
            java.util.Base64.getEncoder().encodeToString(firstArg<ByteArray>())
        }
        session_key_store = SessionKeyStore(null)
        session_key_store.put_user_id("user-a")
        session_key_store.put_passphrase(passphrase.copyOf())
        session_key_store.put_password_salt(random_bytes(16))
        repo = build_repo(session_key_store)
    }

    private fun build_repo(store: SessionKeyStore): MailRepository =
        MailRepository(
            mail_api = mockk(relaxed = true),
            send_api = mockk(relaxed = true),
            snooze_api = mockk(relaxed = true),
            labels_api = mockk(relaxed = true),
            keys_api = mockk(relaxed = true),
            session_key_store = store,
            scheduled_api = mockk(relaxed = true),
            ratchet_decryptor = mockk(relaxed = true),
            ratchet_encryptor = mockk(relaxed = true),
            ratchet_plaintext_cache = mockk(relaxed = true),
            system_folder_bootstrap = mockk(relaxed = true),
            pending_send_dao_provider = dagger.Lazy { mockk(relaxed = true) },
            message_body_dao_provider = dagger.Lazy { mockk(relaxed = true) },
            thread_snapshot_dao_provider = dagger.Lazy { mockk(relaxed = true) },
            context = mockk(relaxed = true),
            auth_repository = dagger.Lazy { mockk<AuthRepository>(relaxed = true) },
        )

    @After
    fun teardown() {
        unmockkAll()
    }

    private fun random_bytes(size: Int): ByteArray = ByteArray(size).also { SecureRandom().nextBytes(it) }

    private fun b64(bytes: ByteArray): String = java.util.Base64.getEncoder().encodeToString(bytes)

    private fun shared_constant(context: String): ByteArray =
        MessageDigest.getInstance("SHA-256").digest(("undefined" + context).toByteArray(Charsets.UTF_8))

    private fun aes_gcm(key: ByteArray, iv: ByteArray, plaintext: ByteArray): ByteArray {
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.ENCRYPT_MODE, SecretKeySpec(key, "AES"), GCMParameterSpec(128, iv))
        return cipher.doFinal(plaintext)
    }

    private fun password_envelope_under(context: String): String {
        val iv = random_bytes(12)
        return b64(random_bytes(16) + iv + aes_gcm(shared_constant(context), iv, envelope_json.toByteArray(Charsets.UTF_8)))
    }

    private fun identity_envelope_under(context: String): Pair<String, String> {
        val nonce = random_bytes(12)
        return b64(aes_gcm(shared_constant(context), nonce, envelope_json.toByteArray(Charsets.UTF_8))) to b64(nonce)
    }

    private fun level_two_envelope_under(key: ByteArray): String =
        b64(EnvelopeV2.seal(envelope_json.toByteArray(Charsets.UTF_8), key))

    private fun identity_material_envelope(material: String): Pair<String, String> {
        val key = MessageDigest.getInstance("SHA-256")
            .digest((material + "astermail-envelope-v1").toByteArray(Charsets.UTF_8))
        val nonce = random_bytes(12)
        return b64(aes_gcm(key, nonce, envelope_json.toByteArray(Charsets.UTF_8))) to b64(nonce)
    }

    private fun assert_marked_legacy(record: StoredRecord, context: String) {
        assertTrue("$context envelope should open as legacy", record is StoredRecord.Legacy)
        val opened = Json.parseToJsonElement(String((record as StoredRecord.Legacy).plaintext, Charsets.UTF_8)).jsonObject
        assertEquals("$context envelope should carry the unverified mark", JsonPrimitive(true), opened["legacy_unverified"])
        assertEquals(JsonPrimitive("kept on the server"), opened["body_text"])
    }

    @Test
    fun password_envelopes_under_any_shared_constant_open_unverified() {
        for (context in listOf("astermail-tags-v1", "astermail-calendar-v2", "astermail-preferences-v1")) {
            val record = repo.mail_legacy_crypto().classify_envelope(password_envelope_under(context), password_marker_nonce)

            assert_marked_legacy(record, context)
        }
    }

    @Test
    fun identity_envelopes_under_any_shared_constant_open_unverified() {
        session_key_store.put_identity_key("undefined")
        for (context in listOf("astermail-draft-v2", "astermail-tags-v1", "astermail-calendar-v2")) {
            val (sealed, nonce) = identity_envelope_under(context)

            assert_marked_legacy(repo.mail_legacy_crypto().classify_envelope(sealed, nonce), context)
        }
    }

    @Test
    fun level_two_envelopes_under_a_shared_constant_never_count_as_own_sealed() = runBlocking {
        for (context in listOf("astermail-tags-v1", "astermail-envelope-v1")) {
            val envelope = repo.decrypt_envelope_with_heal(
                level_two_envelope_under(shared_constant(context)),
                LevelTwoEnvelope.marker_b64(),
            ).envelope

            assertNotNull(context, envelope)
            assertEquals("kept on the server", envelope!!.body_text)
            assertFalse("$context level two envelope should not count as own sealed", envelope.own_sealed)
        }
    }

    @Test
    fun level_two_envelopes_under_the_empty_and_null_placeholders_never_count_as_own_sealed() = runBlocking {
        for (material in listOf("", "null")) {
            val key = MessageDigest.getInstance("SHA-256")
                .digest((material + "astermail-envelope-v1").toByteArray(Charsets.UTF_8))
            session_key_store.put_legacy_keks(listOf(b64(key)))
            val envelope = repo.decrypt_envelope_with_heal(
                level_two_envelope_under(key),
                LevelTwoEnvelope.marker_b64(),
            ).envelope

            assertNotNull("'$material'", envelope)
            assertEquals("kept on the server", envelope!!.body_text)
            assertFalse("'$material' level two envelope should not count as own sealed", envelope.own_sealed)
        }
    }

    private fun open_level_two(store: SessionKeyStore, key: ByteArray) = runBlocking {
        build_repo(store).decrypt_envelope_with_heal(level_two_envelope_under(key), LevelTwoEnvelope.marker_b64()).envelope
    }

    @Test
    fun a_level_two_envelope_under_an_older_key_is_unverified_when_no_passphrase_is_held() {
        val store = SessionKeyStore(null)
        store.put_user_id("user-a")
        store.put_password_salt(random_bytes(16))
        val older = random_bytes(32)
        store.put_legacy_keks(listOf(b64(older)))

        val envelope = open_level_two(store, older)

        assertNotNull(envelope)
        assertEquals("kept on the server", envelope!!.body_text)
        assertFalse("an older key that cannot be checked must not count as own sealed", envelope.own_sealed)
    }

    @Test
    fun a_level_two_envelope_under_an_older_key_is_unverified_when_no_salt_is_known() {
        val store = SessionKeyStore(null)
        store.put_user_id("user-a")
        store.put_passphrase(passphrase.copyOf())
        val older = random_bytes(32)
        store.put_legacy_keks(listOf(b64(older)))

        val envelope = open_level_two(store, older)

        assertNotNull(envelope)
        assertFalse("an older key with no salt to check must not count as own sealed", envelope!!.own_sealed)
    }

    @Test
    fun a_level_two_envelope_under_the_data_kek_counts_as_own_sealed_without_a_passphrase() {
        val store = SessionKeyStore(null)
        store.put_user_id("user-a")
        val data_kek = random_bytes(32)
        store.put_data_kek(data_kek.copyOf())

        val envelope = open_level_two(store, data_kek)

        assertNotNull(envelope)
        assertTrue("the vault data key is the account's own key", envelope!!.own_sealed)
    }

    @Test
    fun a_level_two_envelope_under_the_login_hash_never_counts_as_own_sealed() = runBlocking {
        val salt = random_bytes(16)
        session_key_store.put_password_salt(salt.copyOf())
        val login_hash = org.astermail.android.crypto.CryptoNative.derive_pbkdf2_hash(passphrase.copyOf(), salt, 310000)
        session_key_store.put_legacy_keks(listOf(b64(login_hash)))
        val envelope = repo.decrypt_envelope_with_heal(
            level_two_envelope_under(login_hash),
            LevelTwoEnvelope.marker_b64(),
        ).envelope

        assertNotNull(envelope)
        assertEquals("kept on the server", envelope!!.body_text)
        assertFalse("a login hash level two envelope should not count as own sealed", envelope.own_sealed)
    }

    @Test
    fun a_level_two_envelope_under_the_account_data_key_stays_own_sealed() = runBlocking {
        val data_kek = random_bytes(32)
        session_key_store.put_data_kek(data_kek)
        val envelope = repo.decrypt_envelope_with_heal(
            level_two_envelope_under(data_kek),
            LevelTwoEnvelope.marker_b64(),
        ).envelope

        assertNotNull(envelope)
        assertEquals("kept on the server", envelope!!.body_text)
        assertTrue(envelope.own_sealed)
    }

    @Test
    fun level_two_attachment_meta_under_a_shared_constant_is_never_queued_for_reseal() {
        val meta = """{"filename":"plan.pdf","content_type":"application/pdf","session_key":"c2Vzc2lvbg=="}"""
        val nonce = LevelTwoEnvelope.meta_marker()
        val sealed = b64(aes_gcm(shared_constant("astermail-tags-v1"), nonce, meta.toByteArray(Charsets.UTF_8)))

        val record = repo.mail_legacy_crypto().classify_attachment_meta(sealed, LevelTwoEnvelope.meta_marker_b64())

        assertEquals("unverified meta must not be resealed under the account key", StoredRecord.Unreadable, record)
    }

    @Test
    fun a_non_json_level_two_meta_row_under_a_shared_constant_becomes_a_placeholder() {
        val sealed = b64(EnvelopeV2.seal("x".toByteArray(Charsets.UTF_8), shared_constant("astermail-tags-v1")))

        val meta = runCatching { repo.decrypt_attachment_meta(sealed, null) }

        assertTrue("a bad meta row should not fail the attachment list", meta.isSuccess)
        assertTrue(meta.getOrThrow().is_placeholder)
    }

    @Test
    fun envelopes_under_any_placeholder_identity_material_open_unverified() {
        for (material in listOf("null", "Undefined", " null ")) {
            session_key_store.put_identity_key(material)
            val (sealed, nonce) = identity_material_envelope(material)

            assert_marked_legacy(repo.mail_legacy_crypto().classify_envelope(sealed, nonce), "'$material'")
        }
    }

    @Test
    fun a_tags_constant_envelope_never_counts_as_own_sealed() = runBlocking {
        val envelope = repo.decrypt_envelope_with_heal(
            password_envelope_under("astermail-tags-v1"),
            password_marker_nonce,
        ).envelope

        assertNotNull(envelope)
        assertEquals("kept on the server", envelope!!.body_text)
        assertFalse(envelope.own_sealed)
    }
}
