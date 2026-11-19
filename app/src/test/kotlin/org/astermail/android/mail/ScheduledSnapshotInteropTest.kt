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
import org.astermail.android.crypto.storage_rotation.absorb_vault_markers
import org.astermail.android.storage.SessionKeyStore
import org.json.JSONArray
import org.json.JSONObject
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class ScheduledSnapshotInteropTest {

    private lateinit var session_key_store: SessionKeyStore
    private lateinit var scheduled_api: ScheduledApi
    private lateinit var repo: MailRepository

    @Before
    fun setup() {
        mockkStatic(android.util.Base64::class)
        every { android.util.Base64.decode(any<String>(), any()) } answers {
            java.util.Base64.getMimeDecoder().decode(firstArg<String>())
        }
        session_key_store = SessionKeyStore(null)
        session_key_store.put_user_id("user-a")
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

    private fun detail(id: String, updated_at: String? = "2026-11-01T00:00:00.000000+00:00"): ScheduledDetailResponse {
        val key = ByteArray(32).also { SecureRandom().nextBytes(it) }
        val nonce = ByteArray(12).also { SecureRandom().nextBytes(it) }
        val envelope = JSONObject()
            .put("to_recipients", JSONArray(listOf("friend@example.com")))
            .put("subject", "Scheduled $id")
            .put("body", "<p>scheduled</p>")
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.ENCRYPT_MODE, SecretKeySpec(key, "AES"), GCMParameterSpec(128, nonce))
        return ScheduledDetailResponse(
            id = id,
            encrypted_envelope = b64(cipher.doFinal(envelope.toString().toByteArray(Charsets.UTF_8))),
            envelope_nonce = b64(nonce),
            recipient_count = 1,
            scheduled_at = "2026-11-20T10:00:00Z",
            updated_at = updated_at,
            is_external = true,
            ephemeral_key = b64(key),
        )
    }

    private fun read_all(details: List<ScheduledDetailResponse>): Map<String, InboxItem> = runBlocking {
        coEvery { scheduled_api.list_scheduled(any(), any()) } returns ListScheduledResponse(
            items = details.map { ScheduledSummary(id = it.id, recipient_count = 1, scheduled_at = it.scheduled_at) },
            total = details.size.toLong(),
            limit = 50,
            offset = 0,
        )
        details.forEach { coEvery { scheduled_api.get_scheduled(it.id) } returns it }
        repo.fetch_scheduled().getOrThrow().items.associateBy { it.id }
    }

    private fun web_vault(at: String, vararg ids: String): JSONObject = JSONObject().put(
        "scheduled_untagged",
        JSONObject().put("v", 1).put("at", at).put("ids", JSONArray(ids.toList())),
    )

    @Test
    fun a_snapshot_written_by_the_web_app_limits_untagged_items() {
        absorb_vault_markers(session_key_store, web_vault("2026-11-05T00:00:00.000Z", "s0"))

        val items = read_all(listOf(detail("s0"), detail("s1")))

        assertFalse(items.getValue("s0").is_undecryptable)
        assertTrue(items.getValue("s1").is_undecryptable)
        assertFalse(items.getValue("s1").subject.contains("Scheduled"))
    }

    @Test
    fun an_item_changed_after_the_web_snapshot_does_not_open() {
        absorb_vault_markers(session_key_store, web_vault("2026-11-05T00:00:00.000Z", "s0", "s1"))

        val items = read_all(
            listOf(
                detail("s0", updated_at = "2026-11-04T23:59:59.999Z"),
                detail("s1", updated_at = "2026-11-06T08:00:00.123456Z"),
            ),
        )

        assertFalse(items.getValue("s0").is_undecryptable)
        assertEquals("Scheduled s0", items.getValue("s0").subject)
        assertTrue(items.getValue("s1").is_undecryptable)
    }

    @Test
    fun a_snapshot_written_by_an_older_phone_app_still_applies() {
        absorb_vault_markers(session_key_store, JSONObject().put("scheduled_untagged_ids", JSONArray(listOf("s0"))))

        val items = read_all(listOf(detail("s0"), detail("s1")))

        assertFalse(items.getValue("s0").is_undecryptable)
        assertTrue(items.getValue("s1").is_undecryptable)
    }
}
