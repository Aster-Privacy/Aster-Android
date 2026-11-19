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
import org.astermail.android.storage.ScheduledUntaggedSnapshot
import org.astermail.android.storage.SessionKeyStore
import org.json.JSONArray
import org.json.JSONObject
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class ScheduledSnapshotStrictTest {

    private lateinit var session_key_store: SessionKeyStore
    private lateinit var scheduled_api: ScheduledApi
    private lateinit var auth_repository: AuthRepository
    private lateinit var repo: MailRepository

    private val now_ms = 1_794_960_000_000L

    @Before
    fun setup() {
        mockkStatic(android.util.Base64::class)
        every { android.util.Base64.decode(any<String>(), any()) } answers {
            java.util.Base64.getMimeDecoder().decode(firstArg<String>())
        }
        session_key_store = SessionKeyStore(null)
        session_key_store.put_user_id("user-a")
        scheduled_api = mockk(relaxed = true)
        auth_repository = mockk(relaxed = true)
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
            auth_repository = dagger.Lazy { auth_repository },
        )
    }

    @After
    fun teardown() {
        unmockkAll()
    }

    private fun shared(at: String, vararg ids: String): JSONObject =
        JSONObject().put("v", 1).put("at", at).put("ids", JSONArray(ids.toList()))

    private fun b64(bytes: ByteArray): String = java.util.Base64.getEncoder().encodeToString(bytes)

    private fun detail(id: String): ScheduledDetailResponse {
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
            updated_at = "2026-11-01T00:00:00.000000+00:00",
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

    @Test
    fun a_change_time_must_be_present_and_carry_an_offset() {
        val snapshot = ScheduledUntaggedSnapshot("2026-11-05T00:00:00.000Z", setOf("a"))

        assertFalse(snapshot.allows("a", null))
        assertFalse(snapshot.allows("a", "2026-11-01T00:00:00"))
        assertFalse(snapshot.allows("a", "2026-11-01"))
        assertTrue(snapshot.allows("a", "2026-11-04T23:59:59.123456+00:00"))
        assertTrue(snapshot.allows("a", "2026-11-05T02:00:00+02:00"))
        assertFalse(snapshot.allows("a", "2026-11-04T20:00:01-04:00"))
    }

    @Test
    fun impossible_times_are_refused_the_same_way_as_on_the_web() {
        assertNull(ScheduledUntaggedSnapshot.from_json(shared("2026-02-30T00:00:00.000Z", "a")))
        assertNull(ScheduledUntaggedSnapshot.from_json(shared("2026-11-04T24:00:00.000Z", "a")))
        val snapshot = ScheduledUntaggedSnapshot("2026-11-05T00:00:00.000Z", setOf("a"))
        assertFalse(snapshot.allows("a", "2026-11-04T24:00:00Z"))
        assertFalse(snapshot.allows("a", "2026-02-30T00:00:00Z"))
    }

    @Test
    fun an_unreadable_shared_snapshot_opens_nothing() {
        val vault = JSONObject()
            .put(SCHEDULED_UNTAGGED_FIELD, shared("2026-11-05T00:00:00.000Z", "a").put("v", 2))
            .put(LEGACY_SCHEDULED_UNTAGGED_FIELD, JSONArray(listOf("a")))

        assertEquals(emptySet<String>(), vault_scheduled_untagged(vault, now_ms)?.ids)
        assertFalse(with_scheduled_untagged_snapshot(vault, ScheduledUntaggedSnapshot.taken_at(setOf("a", "b"), now_ms)))
    }

    @Test
    fun a_damaged_older_phone_list_opens_nothing() {
        val vault = JSONObject()
            .put(SCHEDULED_UNTAGGED_FIELD, shared("2026-11-05T00:00:00.000Z", "a", "b"))
            .put(LEGACY_SCHEDULED_UNTAGGED_FIELD, "a")

        assertEquals(emptySet<String>(), vault_scheduled_untagged(vault, now_ms)?.ids)
    }

    @Test
    fun a_merge_keeps_an_unreadable_snapshot_from_the_server() {
        val unreadable = shared("2026-11-05T00:00:00.000Z", "a").put("v", 2)

        assertSame(unreadable, earliest_scheduled_untagged(unreadable, shared("2026-11-01T00:00:00.000Z", "a", "b")))
    }

    @Test
    fun an_unreadable_snapshot_replaces_the_one_on_the_phone() {
        absorb_vault_markers(session_key_store, JSONObject().put(SCHEDULED_UNTAGGED_FIELD, shared("2026-11-05T00:00:00.000Z", "s0")))
        absorb_vault_markers(session_key_store, JSONObject().put(SCHEDULED_UNTAGGED_FIELD, JSONObject.NULL))

        val items = read_all(listOf(detail("s0"), detail("s1")))

        assertTrue(items.getValue("s0").is_undecryptable)
        assertTrue(items.getValue("s1").is_undecryptable)
    }

    @Test
    fun the_first_page_uses_the_snapshot_kept_in_the_vault() {
        coEvery { auth_repository.record_scheduled_untagged_snapshot(any()) } answers {
            session_key_store.absorb_scheduled_untagged_snapshot(
                ScheduledUntaggedSnapshot("2026-11-05T00:00:00.000Z", setOf("s0")),
                replace = true,
            )
        }

        val items = read_all(listOf(detail("s0"), detail("s1")))

        assertFalse(items.getValue("s0").is_undecryptable)
        assertTrue(items.getValue("s1").is_undecryptable)
    }
}
