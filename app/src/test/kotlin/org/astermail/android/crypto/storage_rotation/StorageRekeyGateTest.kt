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
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import org.astermail.android.auth.STORAGE_REKEY_PENDING
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test

class StorageRekeyGateTest {

    private class ScriptedApi(vararg replies: ApiReply) : ResealApi {
        val queue = ArrayDeque(replies.toList())
        val calls = ArrayList<String>()
        val bodies = ArrayList<JsonObject>()

        private fun next(path: String): ApiReply {
            calls.add(path)
            return queue.removeFirstOrNull() ?: ApiReply(200, json_object("ok" to true))
        }

        override suspend fun get(path: String): ApiReply = next(path)
        override suspend fun post(path: String, body: JsonElement): ApiReply {
            bodies.add(body as JsonObject)
            return next(path)
        }
        override suspend fun put(path: String, body: JsonElement): ApiReply = next(path)
    }

    private val rekey_path = "$STORAGE_REKEY_PATH/escrow"

    private fun status(
        rollout: Boolean = true,
        may_start: Boolean = true,
        old_sessions: Long = 0,
    ) = RekeyStatus(rollout, may_start, old_sessions, 1, 1)

    private fun pending_view(): VaultKeyView = VaultKeyView(
        data_kek = encode_b64(ByteArray(32) { 7 }),
        data_kek_random = true,
        storage_rekey_state = STORAGE_REKEY_PENDING,
        prior_keys = listOf(encode_b64(ByteArray(32) { 8 })),
        legacy_keks = emptyList(),
    )

    @Test
    fun the_status_gate_admits_only_an_enabled_rollout_with_no_old_sessions() {
        assertEquals(RekeyGateDecision.Start, decide_rekey_start(status()))
        assertEquals("paused", (decide_rekey_start(status(rollout = false)) as RekeyGateDecision.Wait).reason)
        assertEquals("old_client", (decide_rekey_start(status(old_sessions = 2)) as RekeyGateDecision.Wait).reason)
        assertEquals("not_admitted", (decide_rekey_start(status(may_start = false)) as RekeyGateDecision.Wait).reason)
    }

    @Test
    fun the_status_gate_reads_the_server_reply_and_waits_when_it_cannot() = runBlocking {
        val admitted = json_object("rollout_enabled" to true, "may_start" to true, "old_client_sessions" to 0)
        assertEquals(RekeyGateDecision.Start, check_rekey_gate(ScriptedApi(ApiReply(200, admitted))))
        val limited = check_rekey_gate(ScriptedApi(ApiReply(429, null, RATE_LIMIT_EXCEEDED_CODE)))
        assertEquals("rate_limited", (limited as RekeyGateDecision.Wait).reason)
        val garbled = check_rekey_gate(ScriptedApi(ApiReply(200, json_object("rollout_enabled" to true))))
        assertTrue(garbled is RekeyGateDecision.Wait)
        val down = check_rekey_gate(ScriptedApi(ApiReply(503)))
        assertTrue(down is RekeyGateDecision.Wait)
    }

    @Test
    fun a_closed_gate_defers_the_reseal_without_touching_the_server() = runBlocking {
        val api = ScriptedApi()
        val result = run_storage_reseal(
            view = pending_view(),
            api = api,
            store = MemoryProgressStore(),
            finish = { fail("must not finish"); StorageRekeyFinish.DONE },
            sleep = {},
            gate = { RekeyGateDecision.Wait("not_admitted", 1234) },
        )
        assertEquals(ResealRunStatus.DEFERRED, result.status)
        assertEquals("not_admitted", result.reason)
        assertEquals(1234L, result.retry_after_secs)
        assertTrue(api.calls.isEmpty())
    }

    @Test
    fun a_failing_gate_check_defers_instead_of_starting() = runBlocking {
        val result = run_storage_reseal(
            view = pending_view(),
            api = ScriptedApi(),
            store = MemoryProgressStore(),
            finish = { StorageRekeyFinish.DONE },
            sleep = {},
            gate = { throw IllegalStateException("offline") },
        )
        assertEquals(ResealRunStatus.DEFERRED, result.status)
        assertEquals(REKEY_RATE_LIMIT_WAIT_SECS, result.retry_after_secs)
    }

    @Test
    fun a_deferred_run_is_rescheduled_after_the_server_wait() {
        val counts = StepCounts()
        val deferred = ResealRunResult(ResealRunStatus.DEFERRED, counts, "busy", retry_after_secs = 60)
        assertEquals(StorageRekeyNextRun(0, 60), storage_rekey_next_run(deferred, 3))
        val unknown = ResealRunResult(ResealRunStatus.DEFERRED, counts, "paused")
        assertEquals(StorageRekeyNextRun(0, REKEY_NOT_ADMITTED_WAIT_SECS), storage_rekey_next_run(unknown, 0))
        assertNull(storage_rekey_next_run(ResealRunResult(ResealRunStatus.DONE, counts), 0))
    }

    @Test
    fun busy_replies_wait_for_the_clamped_retry_after_and_resend() = runBlocking {
        val inner = ScriptedApi(
            ApiReply(429, null, STORAGE_REKEY_BUSY_CODE, retry_after_secs = 5),
            ApiReply(429, null, STORAGE_REKEY_BUSY_CODE, retry_after_secs = 9000),
            ApiReply(429, null, STORAGE_REKEY_BUSY_CODE, retry_after_secs = 0),
            ApiReply(200, json_object("ok" to true)),
        )
        val slept = ArrayList<Long>()
        val api = GatedResealApi(inner, sleep = { slept.add(it) }, jitter_ms = { 250 })
        val reply = api.post(rekey_path, json_object("a" to 1))
        assertEquals(200, reply.status)
        assertEquals(listOf(5_250L, 300_250L, 1_250L), slept)
        assertEquals(4, inner.calls.size)
    }

    @Test
    fun busy_without_a_header_uses_the_details_or_the_default() = runBlocking {
        val details = json_object("details" to json_object("retry_after_secs" to 7))
        val inner = ScriptedApi(
            ApiReply(429, details, STORAGE_REKEY_BUSY_CODE),
            ApiReply(429, null, STORAGE_REKEY_BUSY_CODE),
            ApiReply(200),
        )
        val slept = ArrayList<Long>()
        GatedResealApi(inner, sleep = { slept.add(it) }, jitter_ms = { 0 }).put(rekey_path, json_object())
        assertEquals(listOf(7_000L, REKEY_BUSY_DEFAULT_SECS * 1000), slept)
    }

    @Test
    fun endless_busy_replies_defer_the_run() = runBlocking {
        val replies = Array(REKEY_BUSY_MAX_ATTEMPTS + 2) { ApiReply(429, null, STORAGE_REKEY_BUSY_CODE, retry_after_secs = 1) }
        val api = GatedResealApi(ScriptedApi(*replies), sleep = {}, jitter_ms = { 0 })
        try {
            api.get(rekey_path)
            fail("expected a deferral")
        } catch (e: ResealDeferred) {
            assertEquals("busy", e.reason)
            assertEquals(REKEY_BUSY_WAIT_SECS, e.retry_after_secs)
        }
    }

    @Test
    fun paused_old_client_and_rate_limit_replies_defer_without_retrying() = runBlocking {
        val cases = listOf(
            ApiReply(429, null, STORAGE_REKEY_PAUSED_CODE, retry_after_secs = 300) to "paused",
            ApiReply(409, null, STORAGE_REKEY_OLD_CLIENT_CODE) to "old_client",
            ApiReply(429, null, RATE_LIMIT_EXCEEDED_CODE) to "rate_limited",
        )
        for ((reply, reason) in cases) {
            val inner = ScriptedApi(reply)
            val slept = ArrayList<Long>()
            try {
                GatedResealApi(inner, sleep = { slept.add(it) }).post(rekey_path, json_object())
                fail("expected a deferral for $reason")
            } catch (e: ResealDeferred) {
                assertEquals(reason, e.reason)
                assertTrue(e.retry_after_secs >= REKEY_RETRY_AFTER_MAX_SECS)
            }
            assertEquals(1, inner.calls.size)
            assertTrue(slept.isEmpty())
        }
    }

    @Test
    fun the_status_path_and_other_paths_pass_through_untouched() = runBlocking {
        val busy = ApiReply(429, null, STORAGE_REKEY_BUSY_CODE, retry_after_secs = 5)
        val status_api = ScriptedApi(busy)
        val reply = GatedResealApi(status_api, sleep = { fail("must not sleep") }).get(STORAGE_REKEY_STATUS_PATH)
        assertEquals(429, reply.status)
        val other_api = ScriptedApi(ApiReply(409, null, STORAGE_REKEY_OLD_CLIENT_CODE))
        val other = GatedResealApi(other_api, sleep = { fail("must not sleep") }).get("/core/v1/mail/items")
        assertEquals(409, other.status)
    }

    @Test
    fun retry_after_is_clamped_between_one_and_three_hundred_seconds() {
        assertEquals(1L, clamp_retry_after(ApiReply(429, retry_after_secs = 0), 5))
        assertEquals(300L, clamp_retry_after(ApiReply(429, retry_after_secs = 86_400), 5))
        assertEquals(42L, clamp_retry_after(ApiReply(429, retry_after_secs = 42), 5))
        assertEquals(5L, clamp_retry_after(ApiReply(429), 5))
    }

    @Test
    fun an_old_client_reply_is_not_treated_as_a_write_conflict() {
        assertFalse(is_conflict_reply(ApiReply(409, null, STORAGE_REKEY_OLD_CLIENT_CODE)))
        assertTrue(is_conflict_reply(ApiReply(409, null, VAULT_CHANGED_CODE)))
        assertTrue(is_conflict_reply(ApiReply(409)))
    }

    private fun draft(vault: JSONObject, sha: String) =
        VaultDraft(vault, ServerVaultSnapshot("sealed", "nonce", sha))

    private val built_backup = json_object(
        "encrypted_vault" to "must-not-be-sent",
        "vault_nonce" to "must-not-be-sent",
        "recovery_share" to "share",
        "encrypted_recovery_key" to "wrapped",
    )

    private fun vault_with_codes() = JSONObject().put("recovery_codes", JSONArray().put("code-1").put("code-2"))

    @Test
    fun a_backup_refresh_sends_only_the_refresh_fields_with_the_vault_digest() = runBlocking {
        val api = ScriptedApi(ApiReply(200))
        val result = refresh_recovery_backup(api, { draft(vault_with_codes(), "sha-1") }) { built_backup }
        assertEquals(BackupRefresh.REFRESHED, result)
        assertEquals(listOf(RECOVERY_BACKUP_PATH), api.calls)
        val body = api.bodies.single()
        assertFalse(body.containsKey("encrypted_vault"))
        assertFalse(body.containsKey("vault_nonce"))
        assertEquals("true", body["refresh_only"].toString())
        assertEquals("sha-1", body.str("expected_vault_sha256"))
        assertEquals("share", body.str("recovery_share"))
    }

    @Test
    fun a_backup_refresh_retries_after_a_vault_change_with_the_new_digest() = runBlocking {
        val api = ScriptedApi(ApiReply(409, null, VAULT_CHANGED_CODE), ApiReply(200))
        var loads = 0
        val result = refresh_recovery_backup(api, { loads += 1; draft(vault_with_codes(), "sha-$loads") }) { built_backup }
        assertEquals(BackupRefresh.REFRESHED, result)
        assertEquals(listOf("sha-1", "sha-2"), api.bodies.map { it.str("expected_vault_sha256") })
    }

    @Test
    fun a_backup_refresh_stops_when_the_codes_changed_or_an_update_is_needed() = runBlocking {
        val changed = ScriptedApi(ApiReply(409, null, RECOVERY_CODES_CHANGED_CODE))
        assertEquals(
            BackupRefresh.CODES_CHANGED,
            refresh_recovery_backup(changed, { draft(vault_with_codes(), "s") }) { built_backup },
        )
        assertEquals(1, changed.calls.size)
        val upgrade = ScriptedApi(ApiReply(426, null, "CLIENT_UPGRADE_REQUIRED"))
        assertEquals(
            BackupRefresh.UPGRADE_REQUIRED,
            refresh_recovery_backup(upgrade, { draft(vault_with_codes(), "s") }) { built_backup },
        )
    }

    @Test
    fun a_backup_refresh_is_skipped_without_recovery_codes() = runBlocking {
        val api = ScriptedApi()
        assertEquals(
            BackupRefresh.NO_CODES,
            refresh_recovery_backup(api, { draft(JSONObject(), "s") }) { built_backup },
        )
        assertTrue(api.calls.isEmpty())
    }

    @Test
    fun a_backup_refresh_gives_up_after_repeated_vault_changes() = runBlocking {
        val replies = Array(MAX_VAULT_CONFLICT_RETRIES + 2) { ApiReply(409, null, VAULT_CHANGED_CODE) }
        val api = ScriptedApi(*replies)
        assertEquals(
            BackupRefresh.FAILED,
            refresh_recovery_backup(api, { draft(vault_with_codes(), "s") }) { built_backup },
        )
        assertEquals(MAX_VAULT_CONFLICT_RETRIES + 1, api.calls.size)
    }
}
