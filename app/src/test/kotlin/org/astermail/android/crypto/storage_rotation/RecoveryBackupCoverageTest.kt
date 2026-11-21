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
import org.astermail.android.api.ApiError
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

class RecoveryBackupCoverageTest {

    private class CountingApi(vararg replies: ApiReply) : ResealApi {
        val queue = ArrayDeque(replies.toList())
        val posts = ArrayList<String>()

        override suspend fun get(path: String): ApiReply = ApiReply(200)
        override suspend fun post(path: String, body: JsonElement): ApiReply {
            posts.add(path)
            return queue.removeFirstOrNull() ?: ApiReply(200)
        }
        override suspend fun put(path: String, body: JsonElement): ApiReply = ApiReply(200)
    }

    private class Record(var keys: Set<String>? = null)

    private val built_backup = json_object(
        "recovery_share" to "share",
        "encrypted_recovery_key" to "wrapped",
    )

    private fun vault(vararg keys: String): JSONObject {
        val listed = JSONArray()
        keys.forEach { listed.put(it) }
        return JSONObject()
            .put("recovery_codes", JSONArray().put("code-1"))
            .put("keys", listed)
    }

    private fun listed_keys(vault: JSONObject): Set<String> {
        val listed = vault.optJSONArray("keys") ?: return emptySet()
        return (0 until listed.length()).map { listed.getString(it) }.toSet()
    }

    private fun draft(vault: JSONObject) = VaultDraft(vault, ServerVaultSnapshot("sealed", "nonce", "sha-1"))

    private fun builder(
        record: Record,
        storage_key_due: Boolean = false,
        server_stale: Boolean = false,
        at_cap: Boolean = false,
        inner: RecoveryBackupBuilder = RecoveryBackupBuilder { built_backup },
    ) = CoverageBackupBuilder(
        inner = inner,
        vault_keys = { listed_keys(it) },
        recorded = { record.keys },
        record = { record.keys = it },
        storage_key_due = { storage_key_due },
        server_stale = server_stale,
        vault_at_cap = { at_cap },
    )

    private fun dropped_key() = ApiError.Conflict("conflict", BACKUP_DROPS_PUBLISHED_KEY_CODE)

    @Test
    fun coverage_is_stale_when_the_vault_gained_keys() {
        assertEquals(BackupCoverage.STALE, backup_coverage(null, setOf("a")))
        assertEquals(BackupCoverage.STALE, backup_coverage(emptySet(), setOf("a")))
        assertEquals(BackupCoverage.STALE, backup_coverage(setOf("a"), setOf("a", "b")))
    }

    @Test
    fun coverage_is_current_when_nothing_was_added() {
        assertEquals(BackupCoverage.CURRENT, backup_coverage(setOf("a", "b"), setOf("b", "a")))
        assertEquals(BackupCoverage.CURRENT, backup_coverage(null, emptySet()))
        assertEquals(BackupCoverage.CURRENT, backup_coverage(emptySet(), emptySet()))
    }

    @Test
    fun coverage_refuses_a_vault_that_lacks_a_recorded_key() {
        assertEquals(BackupCoverage.WOULD_SHRINK, backup_coverage(setOf("a", "b"), setOf("a")))
        assertEquals(BackupCoverage.WOULD_SHRINK, backup_coverage(setOf("a", "b"), setOf("a", "c")))
        assertEquals(BackupCoverage.WOULD_SHRINK, backup_coverage(setOf("a"), emptySet()))
    }

    @Test
    fun recorded_keys_survive_an_encode_and_decode() {
        assertEquals(setOf("a", "b"), decode_backup_keys(encode_backup_keys(setOf("b", "a"))))
        assertEquals(emptySet<String>(), decode_backup_keys(encode_backup_keys(emptySet())))
        assertNull(decode_backup_keys(null))
    }

    @Test
    fun a_retry_waits_for_the_delay() {
        assertTrue(backup_retry_allowed(null, 10L))
        assertFalse(backup_retry_allowed(20L, 10L))
        assertTrue(backup_retry_allowed(20L, 20L))
    }

    @Test
    fun a_first_inbound_key_refreshes_the_backup() = runBlocking {
        val record = Record()
        val api = CountingApi()
        val result = refresh_recovery_backup(api, { draft(vault("identity")) }, builder = builder(record))
        assertEquals(BackupRefresh.REFRESHED, result)
        assertEquals(listOf(RECOVERY_BACKUP_PATH), api.posts)
        assertEquals(setOf("identity"), record.keys)
    }

    @Test
    fun a_rotated_inbound_key_refreshes_the_backup() = runBlocking {
        val record = Record(setOf("identity"))
        val api = CountingApi()
        val result = refresh_recovery_backup(api, { draft(vault("identity", "rotated")) }, builder = builder(record))
        assertEquals(BackupRefresh.REFRESHED, result)
        assertEquals(1, api.posts.size)
        assertEquals(setOf("identity", "rotated"), record.keys)
    }

    @Test
    fun the_catch_up_runs_once() = runBlocking {
        val record = Record()
        val api = CountingApi()
        val first = builder(record)
        assertEquals(BackupRefresh.REFRESHED, refresh_recovery_backup(api, { draft(vault("identity")) }, builder = first))
        assertFalse(first.declined)
        val second = builder(record)
        assertEquals(BackupRefresh.FAILED, refresh_recovery_backup(api, { draft(vault("identity")) }, builder = second))
        assertTrue(second.declined)
        assertEquals(1, api.posts.size)
    }

    @Test
    fun a_vault_with_fewer_keys_never_replaces_the_backup() = runBlocking {
        val record = Record(setOf("identity", "rotated"))
        val api = CountingApi()
        val guarded = builder(record)
        val result = refresh_recovery_backup(api, { draft(vault("rotated")) }, builder = guarded)
        assertEquals(BackupRefresh.FAILED, result)
        assertTrue(guarded.declined)
        assertTrue(api.posts.isEmpty())
        assertEquals(setOf("identity", "rotated"), record.keys)
    }

    @Test
    fun a_rejected_save_records_nothing() = runBlocking {
        val record = Record()
        val api = CountingApi(ApiReply(409, null, RECOVERY_CODES_CHANGED_CODE))
        val result = refresh_recovery_backup(api, { draft(vault("identity")) }, builder = builder(record))
        assertEquals(BackupRefresh.CODES_CHANGED, result)
        assertNull(record.keys)
    }

    @Test
    fun a_failed_build_records_nothing() = runBlocking {
        val record = Record()
        val api = CountingApi()
        val guarded = builder(record, inner = RecoveryBackupBuilder { null })
        val result = refresh_recovery_backup(api, { draft(vault("identity")) }, builder = guarded)
        assertEquals(BackupRefresh.FAILED, result)
        assertFalse(guarded.declined)
        assertTrue(api.posts.isEmpty())
        assertNull(record.keys)
    }

    @Test
    fun a_due_storage_key_still_refreshes_a_covered_backup() = runBlocking {
        val record = Record(setOf("identity"))
        val api = CountingApi()
        val guarded = builder(record, storage_key_due = true)
        val result = refresh_recovery_backup(api, { draft(vault("identity")) }, builder = guarded)
        assertEquals(BackupRefresh.REFRESHED, result)
        assertEquals(1, api.posts.size)
        assertEquals(setOf("identity"), record.keys)
    }

    @Test
    fun the_saved_callback_reaches_the_wrapped_builder() = runBlocking {
        val saved = ArrayList<JsonObject>()
        val inner = object : RecoveryBackupBuilder {
            override fun build(vault: JSONObject): JsonObject = built_backup
            override fun saved(built: JsonObject) {
                saved.add(built)
            }
        }
        val record = Record()
        refresh_recovery_backup(CountingApi(), { draft(vault("identity")) }, builder = builder(record, inner = inner))
        assertEquals(listOf(built_backup), saved)
    }

    @Test
    fun a_server_report_refreshes_a_backup_the_device_thinks_is_covered() = runBlocking {
        val record = Record(setOf("identity"))
        val api = CountingApi()
        val guarded = builder(record, server_stale = true)
        val result = refresh_recovery_backup(api, { draft(vault("identity")) }, builder = guarded)
        assertEquals(BackupRefresh.REFRESHED, result)
        assertEquals(1, api.posts.size)
    }

    @Test
    fun a_server_report_never_replaces_the_backup_with_fewer_keys() = runBlocking {
        val record = Record(setOf("identity", "rotated"))
        val api = CountingApi()
        val guarded = builder(record, server_stale = true)
        val result = refresh_recovery_backup(api, { draft(vault("rotated")) }, builder = guarded)
        assertEquals(BackupRefresh.FAILED, result)
        assertTrue(guarded.declined)
        assertTrue(api.posts.isEmpty())
    }

    @Test
    fun a_backup_that_would_drop_a_published_key_is_reported_and_not_recorded() = runBlocking {
        val record = Record()
        val api = CountingApi(ApiReply(409, null, BACKUP_DROPS_PUBLISHED_KEY_CODE))
        val result = refresh_recovery_backup(api, { draft(vault("identity")) }, builder = builder(record))
        assertEquals(BackupRefresh.DROPS_PUBLISHED_KEY, result)
        assertEquals(1, api.posts.size)
        assertNull(record.keys)
    }

    @Test
    fun the_server_coverage_field_is_optional() {
        val covered = json_object(BACKUP_COVERS_PUBLISHED_KEYS_FIELD to true)
        val missing = json_object(BACKUP_COVERS_PUBLISHED_KEYS_FIELD to false)
        assertEquals(true, server_backup_coverage(ApiReply(200, covered)))
        assertEquals(false, server_backup_coverage(ApiReply(200, missing)))
        assertNull(server_backup_coverage(ApiReply(200, json_object("total" to 8))))
        assertNull(server_backup_coverage(ApiReply(200)))
        assertNull(server_backup_coverage(ApiReply(500, missing)))
        assertNull(server_backup_coverage(ApiReply(null)))
    }

    @Test
    fun a_due_storage_key_never_writes_a_vault_with_fewer_keys() = runBlocking {
        val record = Record(setOf("identity", "rotated"))
        val api = CountingApi()
        val guarded = builder(record, storage_key_due = true)
        val result = refresh_recovery_backup(api, { draft(vault("rotated")) }, builder = guarded)
        assertEquals(BackupRefresh.FAILED, result)
        assertTrue(guarded.declined)
        assertTrue(api.posts.isEmpty())
        assertEquals(setOf("identity", "rotated"), record.keys)
    }

    @Test
    fun coverage_allows_keys_the_full_vault_let_go() {
        assertEquals(BackupCoverage.STALE, backup_coverage(setOf("old", "kept"), setOf("kept", "new"), true))
        assertEquals(BackupCoverage.WOULD_SHRINK, backup_coverage(setOf("old", "kept"), setOf("kept", "new"), false))
        assertEquals(BackupCoverage.CURRENT, backup_coverage(setOf("old", "kept"), setOf("kept"), true))
        assertEquals(BackupCoverage.WOULD_SHRINK, backup_coverage(setOf("old"), emptySet(), true))
    }

    @Test
    fun a_vault_reaches_its_key_cap_at_the_retention_limit() {
        val entries = JSONArray()
        repeat(3) { entries.put(JSONObject().put("ratchet_identity_public", "key-$it")) }
        val held = JSONObject().put("ratchet_previous_keys", entries)
        assertTrue(vault_at_key_cap(held, cap = 3))
        assertFalse(vault_at_key_cap(held, cap = 4))
        assertFalse(vault_at_key_cap(JSONObject(), cap = 3))
    }

    @Test
    fun a_full_vault_that_gained_a_key_refreshes_and_replaces_the_record() = runBlocking {
        val record = Record(setOf("old", "kept"))
        val api = CountingApi()
        val guarded = builder(record, at_cap = true)
        val result = refresh_recovery_backup(api, { draft(vault("kept", "new")) }, builder = guarded)
        assertEquals(BackupRefresh.REFRESHED, result)
        assertEquals(1, api.posts.size)
        assertEquals(setOf("kept", "new"), record.keys)
    }

    @Test
    fun a_vault_below_its_cap_that_lost_a_key_is_still_refused() = runBlocking {
        val record = Record(setOf("old", "kept"))
        val api = CountingApi()
        val guarded = builder(record)
        val result = refresh_recovery_backup(api, { draft(vault("kept", "new")) }, builder = guarded)
        assertEquals(BackupRefresh.FAILED, result)
        assertTrue(api.posts.isEmpty())
        assertEquals(setOf("old", "kept"), record.keys)
    }

    @Test
    fun a_backup_written_without_a_key_list_is_refreshed_later() {
        assertEquals(emptySet<String>(), written_backup_keys(null))
        assertEquals(setOf("a", "b"), written_backup_keys(listOf("a", "b", "a")))
        assertEquals(BackupCoverage.STALE, backup_coverage(written_backup_keys(null), setOf("a")))
        assertEquals(BackupCoverage.CURRENT, backup_coverage(written_backup_keys(listOf("a")), setOf("a")))
    }

    @Test
    fun a_write_that_succeeds_is_not_repeated() = runBlocking {
        val calls = ArrayList<Boolean>()
        val result = write_with_local_keys({ IllegalStateException("uncovered") }) { fold ->
            calls.add(fold)
            "written"
        }
        assertEquals("written", result)
        assertEquals(listOf(false), calls)
    }

    @Test
    fun a_write_that_would_drop_a_key_is_repeated_once_with_the_local_keys() = runBlocking {
        val calls = ArrayList<Boolean>()
        val result = write_with_local_keys({ IllegalStateException("uncovered") }) { fold ->
            calls.add(fold)
            if (!fold) throw dropped_key()
            "written"
        }
        assertEquals("written", result)
        assertEquals(listOf(false, true), calls)
    }

    @Test
    fun a_write_that_still_drops_a_key_fails_without_a_third_try() = runBlocking {
        val calls = ArrayList<Boolean>()
        val uncovered = IllegalStateException("uncovered")
        val thrown = runCatching {
            write_with_local_keys<String>({ uncovered }) { fold ->
                calls.add(fold)
                throw dropped_key()
            }
        }.exceptionOrNull()
        assertSame(uncovered, thrown)
        assertEquals(listOf(false, true), calls)
    }

    @Test
    fun another_conflict_is_passed_on_unchanged() = runBlocking {
        val calls = ArrayList<Boolean>()
        val other = ApiError.Conflict("conflict", VAULT_CHANGED_CODE)
        val thrown = runCatching {
            write_with_local_keys<String>({ IllegalStateException("uncovered") }) { fold ->
                calls.add(fold)
                throw other
            }
        }.exceptionOrNull()
        assertSame(other, thrown)
        assertEquals(listOf(false), calls)
    }

    @Test
    fun backup_fingerprints_are_omitted_when_there_are_none() {
        assertNull(backup_key_fingerprints(JSONObject()) { emptyList() })
        assertNull(backup_key_fingerprints(JSONObject()) { error("underivable") })
        assertEquals(listOf("a"), backup_key_fingerprints(JSONObject()) { listOf("a") })
    }
}
