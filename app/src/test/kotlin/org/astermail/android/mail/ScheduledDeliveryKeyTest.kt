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
import org.astermail.android.api.scheduled.ListScheduledResponse
import org.astermail.android.api.scheduled.ScheduledApi
import org.astermail.android.api.scheduled.ScheduledDetailResponse
import org.astermail.android.api.scheduled.ScheduledSummary
import org.astermail.android.auth.AuthRepository
import org.astermail.android.storage.SessionKeyStore
import org.json.JSONArray
import org.json.JSONObject
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class ScheduledDeliveryKeyTest {

    private val identity_key = "-----BEGIN PGP PRIVATE KEY BLOCK-----\nscheduled armor\n-----END PGP PRIVATE KEY BLOCK-----"

    private lateinit var session_key_store: SessionKeyStore
    private lateinit var scheduled_api: ScheduledApi
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
        session_key_store.put_identity_key(identity_key)
        scheduled_api = mockk(relaxed = true)
        repo = MailRepository(
            mail_api = mockk(relaxed = true),
            send_api = mockk(relaxed = true),
            snooze_api = mockk(relaxed = true),
            labels_api = mockk(relaxed = true),
            keys_api = mockk(relaxed = true),
            session_key_store = session_key_store,
            scheduled_api = scheduled_api,
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
    }

    @After
    fun teardown() {
        unmockkAll()
    }

    private fun b64(bytes: ByteArray): String = java.util.Base64.getEncoder().encodeToString(bytes)

    private fun envelope(): String = envelope_object().toString()

    private fun envelope_object() = JSONObject()
        .put("to_recipients", JSONArray(listOf("friend@example.com")))
        .put("cc_recipients", JSONArray())
        .put("bcc_recipients", JSONArray())
        .put("subject", "Planted by the server")
        .put("body", "<p>this was never written by you</p>")
        .put("from", JSONObject().put("name", "Me").put("email", "me@astermail.org"))

    private fun detail(
        key: ByteArray,
        ephemeral_key: String?,
        plaintext: String = envelope(),
        id: String = "s1",
    ): ScheduledDetailResponse {
        val nonce = ByteArray(12).also { SecureRandom().nextBytes(it) }
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.ENCRYPT_MODE, SecretKeySpec(key, "AES"), GCMParameterSpec(128, nonce))
        return ScheduledDetailResponse(
            id = id,
            encrypted_envelope = b64(cipher.doFinal(plaintext.toByteArray(Charsets.UTF_8))),
            envelope_nonce = b64(nonce),
            recipient_count = 1,
            scheduled_at = "2026-11-20T10:00:00Z",
            updated_at = "2026-11-01T00:00:00.000000+00:00",
            is_external = true,
            ephemeral_key = ephemeral_key,
        )
    }

    private fun read(detail: ScheduledDetailResponse): InboxItem = runBlocking {
        coEvery { scheduled_api.list_scheduled(any(), any()) } returns ListScheduledResponse(
            items = listOf(ScheduledSummary(id = "s1", recipient_count = 1, scheduled_at = detail.scheduled_at)),
            total = 1,
            limit = 50,
            offset = 0,
        )
        coEvery { scheduled_api.get_scheduled("s1") } returns detail
        repo.fetch_scheduled().getOrThrow().items.single()
    }

    private fun read_all(details: List<ScheduledDetailResponse>): List<InboxItem> = runBlocking {
        coEvery { scheduled_api.list_scheduled(any(), any()) } returns ListScheduledResponse(
            items = details.map { ScheduledSummary(id = it.id, recipient_count = 1, scheduled_at = it.scheduled_at) },
            total = details.size.toLong(),
            limit = 50,
            offset = 0,
        )
        details.forEach { coEvery { scheduled_api.get_scheduled(it.id) } returns it }
        repo.fetch_scheduled().getOrThrow().items
    }

    @Test
    fun an_untagged_envelope_that_appears_after_the_first_listing_does_not_open() {
        val first_key = ByteArray(32).also { SecureRandom().nextBytes(it) }
        val older = detail(first_key, b64(first_key), id = "s0")
        read_all(listOf(older))
        val server_key = ByteArray(32).also { SecureRandom().nextBytes(it) }
        val stripped = detail(server_key, b64(server_key), id = "s1")

        val items = read_all(listOf(older, stripped)).associateBy { it.id }

        assertTrue(items.getValue("s1").is_undecryptable)
        assertFalse(items.getValue("s1").subject.contains("Planted"))
        assertFalse(items.getValue("s0").is_undecryptable)
        assertEquals("Planted by the server", items.getValue("s0").subject)
    }

    @Test
    fun a_placeholder_identity_key_never_opens_scheduled_mail() {
        session_key_store.put_identity_key("undefined")
        val placeholder_key = MessageDigest.getInstance("SHA-256")
            .digest(("undefined" + "astermail-scheduled-v1").toByteArray(Charsets.UTF_8))

        val item = read(detail(placeholder_key, null))

        assertTrue(item.is_undecryptable)
        assertFalse(item.subject.contains("Planted"))
    }

    @Test
    fun an_untagged_envelope_from_an_older_app_still_opens() {
        val delivery_key = ByteArray(32).also { SecureRandom().nextBytes(it) }

        val item = read(detail(delivery_key, b64(delivery_key)))

        assertFalse(item.is_undecryptable)
        assertEquals("Planted by the server", item.subject)
    }

    @Test
    fun an_envelope_tagged_under_another_identity_does_not_open() {
        val server_key = ByteArray(32).also { SecureRandom().nextBytes(it) }
        val forged = org.astermail.android.crypto.ScheduledContentTag
            .tagged("identity-of-someone-else", envelope_object())
            .toString()

        val item = read(detail(server_key, b64(server_key), forged))

        assertTrue(item.is_undecryptable)
        assertFalse(item.subject.contains("Planted"))
    }

    @Test
    fun an_envelope_with_an_empty_tag_does_not_open() {
        val server_key = ByteArray(32).also { SecureRandom().nextBytes(it) }
        val emptied = envelope_object().put("content_tag", "").toString()

        val item = read(detail(server_key, b64(server_key), emptied))

        assertTrue(item.is_undecryptable)
    }

    @Test
    fun an_envelope_tagged_under_the_account_key_opens() {
        val delivery_key = ByteArray(32).also { SecureRandom().nextBytes(it) }
        val tagged = org.astermail.android.crypto.ScheduledContentTag
            .tagged(identity_key, envelope_object())
            .toString()

        val item = read(detail(delivery_key, b64(delivery_key), tagged))

        assertFalse(item.is_undecryptable)
        assertEquals("Planted by the server", item.subject)
    }

    @Test
    fun a_scheduled_item_sealed_under_the_account_key_still_opens() {
        val local_key = MessageDigest.getInstance("SHA-256")
            .digest((identity_key + "astermail-scheduled-v1").toByteArray(Charsets.UTF_8))

        val item = read(detail(local_key, null))

        assertFalse(item.is_undecryptable)
        assertEquals("Planted by the server", item.subject)
    }
}
