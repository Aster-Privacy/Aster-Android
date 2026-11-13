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

package org.astermail.android.mail.ratchet

import kotlinx.coroutines.test.runTest
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class SignedPrekeyRotationTest {

    private val now = 10_000_000_000L
    private val fresh = FreshSignedPrekey("fresh-private", "fresh-public")

    private fun vault(previous: Int = 0): JSONObject {
        val json = JSONObject()
            .put("ratchet_identity_key", "identity-private")
            .put("ratchet_identity_public", "identity-public")
            .put("ratchet_signed_prekey", "old-private")
            .put("ratchet_signed_prekey_public", "old-public")
            .put("unrelated", "kept")
        if (previous > 0) {
            val retained = JSONArray()
            for (index in 0 until previous) {
                retained.put(
                    JSONObject()
                        .put("ratchet_identity_key", "identity-private")
                        .put("ratchet_identity_public", "identity-public")
                        .put("ratchet_signed_prekey", "older-private-$index")
                        .put("ratchet_signed_prekey_public", "older-public-$index"),
                )
            }
            json.put("ratchet_previous_keys", retained)
        }
        return json
    }

    private class Recorder {
        var persisted: JSONObject? = null
        var published: JSONObject? = null
        var recorded_at: Long? = null
        var generated = 0
    }

    private fun deps(
        recorder: Recorder,
        enabled: Boolean = true,
        last_rotated_at: Long? = null,
        source: JSONObject? = vault(),
        persist_ok: Boolean = true,
        publish_ok: Boolean = true,
        generate_error: Throwable? = null,
    ) = SignedPrekeyRotationDeps(
        now = { now },
        last_rotated_at = { last_rotated_at },
        vault = { source },
        persist = { next, _ ->
            recorder.persisted = next
            if (persist_ok) next else null
        },
        publish = { published ->
            recorder.published = published
            publish_ok
        },
        record_rotation = { recorder.recorded_at = it },
        enabled = enabled,
        generate = {
            recorder.generated += 1
            generate_error?.let { throw it }
            fresh
        },
    )

    @Test
    fun rotation_ships_switched_off() {
        assertFalse(SIGNED_PREKEY_ROTATION_ENABLED)
        assertEquals(30L * 24 * 60 * 60 * 1000, SIGNED_PREKEY_ROTATION_INTERVAL_MS)
        assertEquals(32, SIGNED_PREKEY_RETAINED_LIMIT)
        assertFalse(signed_prekey_rotation_due(null, now))
        assertFalse(signed_prekey_rotation_due(0L, now))
    }

    @Test
    fun the_default_dependencies_do_nothing() = runTest {
        val recorder = Recorder()
        val defaults = SignedPrekeyRotationDeps(
            now = { now },
            last_rotated_at = { null },
            vault = { vault() },
            persist = { next, _ ->
                recorder.persisted = next
                next
            },
            publish = { published ->
                recorder.published = published
                true
            },
            record_rotation = { recorder.recorded_at = it },
        )

        assertEquals(SignedPrekeyRotationOutcome.DISABLED, rotate_signed_prekey_if_due(defaults))
        assertNull(recorder.persisted)
        assertNull(recorder.published)
        assertNull(recorder.recorded_at)
    }

    @Test
    fun rotation_is_due_after_the_interval_or_when_never_recorded() {
        assertTrue(signed_prekey_rotation_due(null, now, true))
        assertFalse(signed_prekey_rotation_due(now - SIGNED_PREKEY_ROTATION_INTERVAL_MS + 1, now, true))
        assertTrue(signed_prekey_rotation_due(now - SIGNED_PREKEY_ROTATION_INTERVAL_MS, now, true))
        assertTrue(signed_prekey_rotation_due(now + 1, now, true))
        assertFalse(signed_prekey_rotation_due(now, now, true))
    }

    @Test
    fun applying_a_rotation_retains_the_previous_key_set() {
        val source = vault()
        val next = checkNotNull(apply_signed_prekey_rotation(source, fresh))

        assertEquals("fresh-private", next.getString("ratchet_signed_prekey"))
        assertEquals("fresh-public", next.getString("ratchet_signed_prekey_public"))
        assertEquals("identity-private", next.getString("ratchet_identity_key"))
        assertEquals("kept", next.getString("unrelated"))

        val retained = next.getJSONArray("ratchet_previous_keys")
        assertEquals(1, retained.length())
        assertEquals("old-private", retained.getJSONObject(0).getString("ratchet_signed_prekey"))
        assertEquals("old-public", retained.getJSONObject(0).getString("ratchet_signed_prekey_public"))
        assertEquals("identity-private", retained.getJSONObject(0).getString("ratchet_identity_key"))

        assertEquals("old-private", source.getString("ratchet_signed_prekey"))
        assertFalse(source.has("ratchet_previous_keys"))
    }

    @Test
    fun the_retained_list_is_capped() {
        val next = checkNotNull(apply_signed_prekey_rotation(vault(previous = 40), fresh))
        val retained = next.getJSONArray("ratchet_previous_keys")

        assertEquals(SIGNED_PREKEY_RETAINED_LIMIT, retained.length())
        assertEquals("old-private", retained.getJSONObject(0).getString("ratchet_signed_prekey"))
    }

    @Test
    fun a_rotation_is_refused_without_an_identity_or_a_new_key() {
        val no_identity = vault().also { it.remove("ratchet_identity_key") }

        assertNull(apply_signed_prekey_rotation(no_identity, fresh))
        assertNull(apply_signed_prekey_rotation(vault(), FreshSignedPrekey("", "fresh-public")))
        assertNull(apply_signed_prekey_rotation(vault(), FreshSignedPrekey("fresh-private", " ")))
        assertNull(apply_signed_prekey_rotation(vault(), FreshSignedPrekey("fresh-private", "old-public")))
    }

    @Test
    fun a_switched_off_rotation_touches_nothing() = runTest {
        val recorder = Recorder()

        assertEquals(
            SignedPrekeyRotationOutcome.DISABLED,
            rotate_signed_prekey_if_due(deps(recorder, enabled = false)),
        )
        assertEquals(0, recorder.generated)
        assertNull(recorder.persisted)
        assertNull(recorder.recorded_at)
    }

    @Test
    fun a_recent_rotation_is_not_repeated() = runTest {
        val recorder = Recorder()

        assertEquals(
            SignedPrekeyRotationOutcome.NOT_DUE,
            rotate_signed_prekey_if_due(deps(recorder, last_rotated_at = now - 1000)),
        )
        assertEquals(0, recorder.generated)
        assertNull(recorder.persisted)
    }

    @Test
    fun a_due_rotation_persists_then_publishes_then_records() = runTest {
        val recorder = Recorder()

        assertEquals(SignedPrekeyRotationOutcome.ROTATED, rotate_signed_prekey_if_due(deps(recorder)))

        val persisted = checkNotNull(recorder.persisted)
        assertEquals("fresh-public", persisted.getString("ratchet_signed_prekey_public"))
        assertNotNull(recorder.published)
        assertEquals("fresh-public", checkNotNull(recorder.published).getString("ratchet_signed_prekey_public"))
        assertEquals(now, recorder.recorded_at)
    }

    @Test
    fun a_failed_step_records_nothing() = runTest {
        val no_vault = Recorder()
        assertEquals(
            SignedPrekeyRotationOutcome.FAILED,
            rotate_signed_prekey_if_due(deps(no_vault, source = null)),
        )
        assertNull(no_vault.recorded_at)

        val not_persisted = Recorder()
        assertEquals(
            SignedPrekeyRotationOutcome.FAILED,
            rotate_signed_prekey_if_due(deps(not_persisted, persist_ok = false)),
        )
        assertNull(not_persisted.published)
        assertNull(not_persisted.recorded_at)

        val not_published = Recorder()
        assertEquals(
            SignedPrekeyRotationOutcome.FAILED,
            rotate_signed_prekey_if_due(deps(not_published, publish_ok = false)),
        )
        assertNull(not_published.recorded_at)

        val generate_failed = Recorder()
        assertEquals(
            SignedPrekeyRotationOutcome.FAILED,
            rotate_signed_prekey_if_due(deps(generate_failed, generate_error = IllegalStateException("no key"))),
        )
        assertNull(generate_failed.persisted)
        assertNull(generate_failed.recorded_at)
    }
}
