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

package org.astermail.android.auth

import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import java.security.SecureRandom
import java.util.Base64
import kotlinx.coroutines.test.runTest
import org.astermail.android.api.ApiError
import org.astermail.android.api.keys.AccountKeyCapabilityFlags
import org.astermail.android.api.keys.KeysApi
import org.astermail.android.api.recovery.CodesStatusResponse
import org.astermail.android.api.recovery.CompleteRecoveryRequest
import org.astermail.android.api.recovery.CompleteRecoveryResponse
import org.astermail.android.api.recovery.ConsumeInactiveKeySetRequest
import org.astermail.android.api.recovery.ConsumeInactiveKeySetResponse
import org.astermail.android.api.recovery.DeleteDeviceSecretsRequest
import org.astermail.android.api.recovery.DeviceSecretEntry
import org.astermail.android.api.recovery.DeviceSecretsResponse
import org.astermail.android.api.recovery.FetchDeviceSecretsRequest
import org.astermail.android.api.recovery.FetchInactiveKeySetRequest
import org.astermail.android.api.recovery.FetchInactiveKeySetResponse
import org.astermail.android.api.recovery.InactiveKeySetInfo
import org.astermail.android.api.recovery.InitiateEmailRecoveryRequest
import org.astermail.android.api.recovery.InitiateEmailRecoveryResponse
import org.astermail.android.api.recovery.InitiateRecoveryRequest
import org.astermail.android.api.recovery.InitiateRecoveryResponse
import org.astermail.android.api.recovery.ListInactiveKeySetsResponse
import org.astermail.android.api.recovery.PutDeviceSecretRequest
import org.astermail.android.api.recovery.PutDeviceSecretResponse
import org.astermail.android.api.recovery.RecoveryApi
import org.astermail.android.api.recovery.RecoveryMethodsResponse
import org.astermail.android.api.recovery.SaveRecoveryBackupRequest
import org.astermail.android.api.recovery.SaveRecoveryBackupResponse
import org.astermail.android.api.recovery.VerifyCodesStepUpRequest
import org.astermail.android.api.recovery.VerifyCodesStepUpResponse
import org.astermail.android.crypto.AesGcm
import org.astermail.android.crypto.PgpDecryptor
import org.astermail.android.crypto.PgpEncryptor
import org.astermail.android.crypto.PgpKeyGenerator
import org.astermail.android.crypto.PgpKeyPairResult
import org.astermail.android.storage.SessionKeyStore
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

private class SoftwareDeviceRecoveryKey(private val key: ByteArray) : DeviceRecoveryKey {
    private val random = SecureRandom()

    override fun seal(plaintext: ByteArray, aad: ByteArray): DeviceSealedBytes {
        val iv = ByteArray(12).also { random.nextBytes(it) }
        return DeviceSealedBytes(iv, AesGcm.encrypt(key, iv, plaintext, aad))
    }

    override fun open(iv: ByteArray, ciphertext: ByteArray, aad: ByteArray): ByteArray =
        AesGcm.decrypt(key, iv, ciphertext, aad)
}

private class FakeKeyProvider(var key: DeviceRecoveryKey?) : DeviceRecoveryKeyProvider {
    var unavailable = false
    var generated = 0

    override fun load(create: Boolean): DeviceRecoveryKeyLoad {
        if (unavailable) return DeviceRecoveryKeyLoad.Unavailable
        val existing = key
        if (existing != null) return DeviceRecoveryKeyLoad.Ready(existing, created = false)
        if (!create) return DeviceRecoveryKeyLoad.Missing
        generated += 1
        val fresh = SoftwareDeviceRecoveryKey(ByteArray(32) { (40 + generated).toByte() })
        key = fresh
        return DeviceRecoveryKeyLoad.Ready(fresh, created = true)
    }
}

private class InMemoryStateStore : DeviceRecoveryStateStore {
    val attempts = mutableMapOf<String, Map<String, DeviceRecoveryAttempt>>()
    val not_before = mutableMapOf<String, Long>()

    override fun attempts(user_id: String): Map<String, DeviceRecoveryAttempt> =
        decode_device_recovery_attempts(attempts[user_id]?.let { encode_device_recovery_attempts(it) })

    override fun put_attempts(user_id: String, attempts: Map<String, DeviceRecoveryAttempt>) {
        if (attempts.isEmpty()) this.attempts.remove(user_id) else this.attempts[user_id] = attempts.toMap()
    }

    override fun refresh_not_before(user_id: String): Long = not_before[user_id] ?: 0L

    override fun put_refresh_not_before(user_id: String, at_ms: Long) {
        if (at_ms <= 0L) not_before.remove(user_id) else not_before[user_id] = at_ms
    }

    override fun forget_user(user_id: String) {
        attempts.remove(user_id)
        not_before.remove(user_id)
    }
}

private class InMemorySnapshotStore : DeviceSnapshotStore {
    val records = mutableListOf<DeviceSnapshotRecord>()
    var save_fails = false

    override fun list(user_id: String): List<DeviceSnapshotRecord> =
        records.filter { it.user_id == user_id }.sortedByDescending { it.created_at }

    override fun list_all(): List<DeviceSnapshotRecord> = records.sortedByDescending { it.created_at }

    override fun save(record: DeviceSnapshotRecord): Boolean {
        if (save_fails) return false
        records.add(record)
        return true
    }

    override fun delete(user_id: String, snapshot_ids: List<String>) {
        records.removeAll { it.user_id == user_id && it.snapshot_id in snapshot_ids }
    }
}

private class FakeVaultCodec : DeviceRecoveryVaultCodec {
    override fun decrypt_vault(
        encrypted_vault: String,
        vault_nonce: String,
        passphrase: ByteArray,
    ): ByteArray? = runCatching { Base64.getDecoder().decode(encrypted_vault) }.getOrNull()

    override fun derive_storage_key(passphrase: ByteArray): String =
        Base64.getEncoder().encodeToString(ByteArray(32) { 9 })
}

private class CommitCall(
    val user_id: String,
    val passphrase: ByteArray,
    val vault_obj: JSONObject,
    val identity_keys: RecoveredIdentityKeys,
    val recovered_keks: List<String>,
    val recovered_ratchet: List<JSONObject>,
    val base_encrypted_vault: String,
)

private class FakeRecoveryApi : RecoveryApi {
    val calls = mutableListOf<String>()
    val puts = mutableListOf<PutDeviceSecretRequest>()
    val deletes = mutableListOf<List<String>>()
    val consumed = mutableListOf<String>()
    val fetched_secret_ids = mutableListOf<List<String>>()
    val secrets = mutableMapOf<String, String>()
    var inactive_sets: List<InactiveKeySetInfo> = emptyList()
    var inactive_vaults = mutableMapOf<String, String>()
    var put_succeeds = true
    var put_rate_limited = false
    var list_fails = false

    override suspend fun put_device_recovery_secret(request: PutDeviceSecretRequest): PutDeviceSecretResponse {
        calls.add("put")
        if (put_rate_limited) throw ApiError.RateLimited()
        if (!put_succeeds) throw IllegalStateException("refused")
        puts.add(request)
        secrets[request.snapshot_id] = request.secret
        return PutDeviceSecretResponse(true)
    }

    override suspend fun fetch_device_recovery_secrets(
        request: FetchDeviceSecretsRequest,
    ): DeviceSecretsResponse {
        calls.add("fetch")
        fetched_secret_ids.add(request.snapshot_ids)
        return DeviceSecretsResponse(
            request.snapshot_ids.mapNotNull { id ->
                secrets[id]?.let { DeviceSecretEntry(id, it) }
            },
        )
    }

    override suspend fun delete_device_recovery_secrets(
        request: DeleteDeviceSecretsRequest,
    ): PutDeviceSecretResponse {
        calls.add("delete")
        deletes.add(request.snapshot_ids)
        for (id in request.snapshot_ids) secrets.remove(id)
        return PutDeviceSecretResponse(true)
    }

    override suspend fun list_inactive_key_sets(): ListInactiveKeySetsResponse {
        calls.add("list_inactive")
        if (list_fails) throw IllegalStateException("offline")
        return ListInactiveKeySetsResponse(inactive_sets)
    }

    override suspend fun fetch_inactive_key_set(
        request: FetchInactiveKeySetRequest,
    ): FetchInactiveKeySetResponse {
        calls.add("fetch_inactive")
        val vault = inactive_vaults[request.inactive_vault_id] ?: throw IllegalStateException("missing")
        return FetchInactiveKeySetResponse(vault, "bm9uY2U=")
    }

    override suspend fun consume_inactive_key_set(
        request: ConsumeInactiveKeySetRequest,
    ): ConsumeInactiveKeySetResponse {
        calls.add("consume")
        consumed.add(request.inactive_vault_id)
        return ConsumeInactiveKeySetResponse(true)
    }

    override suspend fun initiate(request: InitiateRecoveryRequest): InitiateRecoveryResponse =
        throw UnsupportedOperationException()

    override suspend fun initiate_email(
        request: InitiateEmailRecoveryRequest,
    ): InitiateEmailRecoveryResponse = throw UnsupportedOperationException()

    override suspend fun complete(request: CompleteRecoveryRequest): CompleteRecoveryResponse =
        throw UnsupportedOperationException()

    override suspend fun backup(request: SaveRecoveryBackupRequest): SaveRecoveryBackupResponse =
        throw UnsupportedOperationException()

    override suspend fun unlock_inactive_key_sets_with_code(
        request: org.astermail.android.api.recovery.UnlockInactiveWithCodeRequest,
    ): org.astermail.android.api.recovery.UnlockInactiveWithCodeResponse =
        throw UnsupportedOperationException()

    override suspend fun methods(): RecoveryMethodsResponse = throw UnsupportedOperationException()

    override suspend fun codes_status(): CodesStatusResponse = throw UnsupportedOperationException()

    override suspend fun verify_step_up(
        request: VerifyCodesStepUpRequest,
    ): VerifyCodesStepUpResponse = throw UnsupportedOperationException()
}

class DeviceRecoveryTest {

    private val user_id = "user-1"
    private val old_password = "old password one"
    private val current_password = "current password two"

    private lateinit var recovery_api: FakeRecoveryApi
    private lateinit var keys_api: KeysApi
    private lateinit var session_key_store: SessionKeyStore
    private lateinit var snapshot_store: InMemorySnapshotStore
    private lateinit var key_provider: FakeKeyProvider
    private lateinit var state_store: InMemoryStateStore
    private lateinit var device_recovery: DeviceRecovery

    private val commits = mutableListOf<CommitCall>()
    private var commit_result = true
    private var commit_dropped = 0
    private var session_user: String? = user_id
    private var capabilities = AccountKeyCapabilityFlags(
        format_writes = true,
        data_conversion = true,
        device_recovery = true,
    )
    private var vault_json = ""
    private var passphrase = current_password
    private var clock = 1_800_000_000_000L
    private var snapshot_counter = 0

    companion object {
        private val current_key: PgpKeyPairResult by lazy {
            PgpKeyGenerator.generate("Owner", "owner@astermail.org", "current password two".toCharArray())
        }
        private val archived_key: PgpKeyPairResult by lazy {
            PgpKeyGenerator.generate("Owner", "owner@astermail.org", "old password one".toCharArray())
        }
    }

    private fun vault(identity: String, previous: List<String> = emptyList()): String =
        JSONObject()
            .put("identity_key", identity)
            .put("previous_keys", JSONArray(previous))
            .put("data_kek", "ZGF0YS1rZWs=")
            .toString()

    private fun encoded_vault(): String =
        Base64.getEncoder().encodeToString(vault_json.toByteArray(Charsets.UTF_8))

    @Before
    fun setup() {
        recovery_api = FakeRecoveryApi()
        snapshot_store = InMemorySnapshotStore()
        key_provider = FakeKeyProvider(SoftwareDeviceRecoveryKey(ByteArray(32) { 5 }))
        state_store = InMemoryStateStore()
        vault_json = vault(current_key.armored_private_key)

        keys_api = mockk(relaxed = true)
        coEvery { keys_api.get_account_key_capabilities() } answers { capabilities }

        session_key_store = mockk(relaxed = true)
        every { session_key_store.get_user_id() } answers { session_user }
        every { session_key_store.get_encrypted_vault() } answers { encoded_vault() to "bm9uY2U=" }
        every { session_key_store.get_passphrase() } answers { passphrase.toByteArray(Charsets.UTF_8) }

        device_recovery = DeviceRecovery(
            recovery_api = recovery_api,
            keys_api = keys_api,
            session_key_store = session_key_store,
            snapshot_store = snapshot_store,
            key_provider = key_provider,
            vault_codec = FakeVaultCodec(),
            state_store = state_store,
            commit = DeviceRecoveryCommit { id, pass, vault_obj, identity_keys, keks, ratchet, base_vault ->
                commits.add(CommitCall(id, pass.copyOf(), vault_obj, identity_keys, keks, ratchet, base_vault))
                RecoveredKeyCommitResult(written = commit_result, dropped_keks = commit_dropped)
            },
            now_ms = { clock },
            new_snapshot_id = { "snapshot-${++snapshot_counter}" },
        )
    }

    private fun payload(): DeviceSnapshotPayload =
        DeviceSnapshotPayload(
            identity_key = "identity",
            previous_keys = listOf("previous"),
            legacy_identity_keys = listOf("legacy"),
            unlocked_keys = listOf("identity" to "unlocked identity"),
            storage_keys = listOf("a2Vr"),
            ratchet_keys = listOf(JSONObject().put("ratchet_identity_public", "pub")),
        )

    private fun record(sealed: DeviceSealedBytes, source_hash: String = "hash-1") =
        DeviceSnapshotRecord(
            snapshot_id = "snapshot-1",
            user_id = user_id,
            source_hash = source_hash,
            created_at = clock,
            iv = sealed.iv,
            sealed = sealed.ciphertext,
        )

    @Test
    fun a_sealed_snapshot_opens_with_its_secret_and_device_key() {
        val key = SoftwareDeviceRecoveryKey(ByteArray(32) { 5 })
        val secret = random_device_secret()
        val meta = DeviceSnapshotMeta(user_id, "snapshot-1", "hash-1")
        val sealed = seal_device_snapshot(payload(), secret, key, meta)

        val opened = open_device_snapshot(record(sealed), secret, key)

        assertNotNull(opened)
        assertEquals("identity", opened!!.identity_key)
        assertEquals(listOf("previous"), opened.previous_keys)
        assertEquals(listOf("legacy"), opened.legacy_identity_keys)
        assertEquals(listOf("identity" to "unlocked identity"), opened.unlocked_keys)
        assertEquals(listOf("a2Vr"), opened.storage_keys)
        assertEquals("pub", opened.ratchet_keys.single().getString("ratchet_identity_public"))
    }

    @Test
    fun a_snapshot_never_opens_with_the_wrong_secret_or_device_key_or_metadata() {
        val key = SoftwareDeviceRecoveryKey(ByteArray(32) { 5 })
        val other_key = SoftwareDeviceRecoveryKey(ByteArray(32) { 6 })
        val secret = random_device_secret()
        val other_secret = random_device_secret()
        val sealed = seal_device_snapshot(
            payload(),
            secret,
            key,
            DeviceSnapshotMeta(user_id, "snapshot-1", "hash-1"),
        )

        assertNull(open_device_snapshot(record(sealed), other_secret, key))
        assertNull(open_device_snapshot(record(sealed), secret, other_key))
        assertNull(open_device_snapshot(record(sealed, source_hash = "hash-2"), secret, key))
        assertNull(
            open_device_snapshot(
                DeviceSnapshotRecord(
                    snapshot_id = "snapshot-2",
                    user_id = user_id,
                    source_hash = "hash-1",
                    created_at = clock,
                    iv = sealed.iv,
                    sealed = sealed.ciphertext,
                ),
                secret,
                key,
            ),
        )
        assertNull(
            open_device_snapshot(
                DeviceSnapshotRecord(
                    snapshot_id = "snapshot-1",
                    user_id = "user-2",
                    source_hash = "hash-1",
                    created_at = clock,
                    iv = sealed.iv,
                    sealed = sealed.ciphertext,
                ),
                secret,
                key,
            ),
        )
    }

    @Test
    fun a_secret_is_always_thirty_two_non_zero_bytes() {
        val secret = random_device_secret()

        assertEquals(DEVICE_RECOVERY_SECRET_BYTES, secret.size)
        assertFalse(secret.all { it == 0.toByte() })
    }

    @Test
    fun the_capability_flag_gates_every_call() = runTest {
        capabilities = AccountKeyCapabilityFlags(
            format_writes = true,
            data_conversion = true,
            device_recovery = false,
        )

        assertEquals(0, device_recovery.run(user_id))
        assertTrue(recovery_api.calls.isEmpty())
        assertTrue(snapshot_store.records.isEmpty())
    }

    @Test
    fun the_server_holds_the_secret_before_the_device_holds_the_snapshot() = runTest {
        assertTrue(device_recovery.refresh_snapshot(user_id))

        assertEquals(listOf("put"), recovery_api.calls)
        val record = snapshot_store.records.single()
        assertEquals("snapshot-1", record.snapshot_id)
        assertEquals(vault_ciphertext_hash(encoded_vault()), record.source_hash)
        assertEquals(1, recovery_api.puts.size)
        assertEquals(
            DEVICE_RECOVERY_SECRET_BYTES,
            Base64.getDecoder().decode(recovery_api.puts.single().secret).size,
        )
    }

    @Test
    fun a_refused_secret_leaves_nothing_on_the_device() = runTest {
        recovery_api.put_succeeds = false

        assertFalse(device_recovery.refresh_snapshot(user_id))
        assertTrue(snapshot_store.records.isEmpty())
    }

    @Test
    fun a_failed_local_save_deletes_the_server_secret() = runTest {
        snapshot_store.save_fails = true

        assertFalse(device_recovery.refresh_snapshot(user_id))
        assertTrue(snapshot_store.records.isEmpty())
        assertEquals(listOf("put", "delete"), recovery_api.calls)
        assertEquals(listOf(listOf("snapshot-1")), recovery_api.deletes)
        assertTrue(recovery_api.secrets.isEmpty())
    }

    @Test
    fun a_second_refresh_of_the_same_vault_does_nothing() = runTest {
        assertTrue(device_recovery.refresh_snapshot(user_id))
        assertFalse(device_recovery.refresh_snapshot(user_id))

        assertEquals(1, snapshot_store.records.size)
        assertEquals(listOf("put"), recovery_api.calls)
    }

    @Test
    fun a_missing_device_key_drops_only_the_local_snapshots() = runTest {
        snapshot_the_old_vault()
        arm_reset()
        key_provider.key = null

        assertEquals(0, device_recovery.run(user_id))
        assertTrue(commits.isEmpty())
        assertTrue(recovery_api.consumed.isEmpty())
        assertTrue(snapshot_store.records.none { it.snapshot_id == "snapshot-1" })
        assertTrue(recovery_api.deletes.none { "snapshot-1" in it })
        assertEquals(1, key_provider.generated)
    }

    @Test
    fun an_unavailable_device_key_wipes_nothing() = runTest {
        snapshot_the_old_vault()
        arm_reset()
        key_provider.unavailable = true

        assertEquals(0, device_recovery.run(user_id))
        assertTrue(commits.isEmpty())
        assertTrue(recovery_api.consumed.isEmpty())
        assertEquals(listOf("snapshot-1"), snapshot_store.records.map { it.snapshot_id })
        assertTrue(recovery_api.deletes.isEmpty())
        assertEquals(0, key_provider.generated)
    }

    @Test
    fun an_existing_device_key_is_never_replaced() = runTest {
        assertTrue(device_recovery.refresh_snapshot(user_id))
        vault_json = vault(current_key.armored_private_key, listOf("next"))
        assertTrue(device_recovery.refresh_snapshot(user_id))

        assertEquals(0, key_provider.generated)
        assertEquals(2, snapshot_store.records.size)
    }

    private fun arm_reset() {
        val archived_vault = encoded_vault()
        recovery_api.inactive_sets = listOf(InactiveKeySetInfo(id = "set-1"))
        recovery_api.inactive_vaults["set-1"] = archived_vault
        vault_json = vault(current_key.armored_private_key)
        passphrase = current_password
    }

    private suspend fun snapshot_the_old_vault() {
        vault_json = vault(archived_key.armored_private_key)
        passphrase = old_password
        assertTrue(device_recovery.refresh_snapshot(user_id))
    }

    @Test
    fun a_password_reset_restores_the_archived_key_under_the_current_password() = runTest {
        snapshot_the_old_vault()
        val ciphertext = PgpEncryptor.encrypt_to_keys("archived hello", listOf(archived_key.armored_public_key))!!
        arm_reset()

        assertEquals(1, device_recovery.run(user_id))

        val commit = commits.single()
        assertEquals(user_id, commit.user_id)
        assertEquals(listOf(true), commit.identity_keys.absorbed)
        val recovered = commit.identity_keys.previous_keys.single()
        assertEquals(pgp_key_identity(archived_key.armored_private_key), pgp_key_identity(recovered))
        assertEquals("archived hello", PgpDecryptor.decrypt(ciphertext, recovered, current_password.toCharArray()))
        assertNull(PgpDecryptor.decrypt(ciphertext, recovered, old_password.toCharArray()))
        assertEquals(listOf("set-1"), recovery_api.consumed)
        assertTrue(commit.recovered_keks.contains("ZGF0YS1rZWs="))
        assertTrue(snapshot_store.records.none { it.snapshot_id == "snapshot-1" })
        assertTrue(recovery_api.deletes.any { "snapshot-1" in it })
    }

    @Test
    fun an_archive_no_snapshot_matches_is_left_alone() = runTest {
        snapshot_the_old_vault()
        arm_reset()
        recovery_api.inactive_vaults["set-1"] =
            Base64.getEncoder().encodeToString(vault("other").toByteArray(Charsets.UTF_8))

        assertEquals(0, device_recovery.run(user_id))
        assertTrue(commits.isEmpty())
        assertTrue(recovery_api.consumed.isEmpty())
        assertTrue(snapshot_store.records.any { it.snapshot_id == "snapshot-1" })
    }

    @Test
    fun a_failed_commit_consumes_nothing() = runTest {
        snapshot_the_old_vault()
        arm_reset()
        commit_result = false

        assertEquals(0, device_recovery.run(user_id))
        assertEquals(1, commits.size)
        assertTrue(recovery_api.consumed.isEmpty())
        assertTrue(snapshot_store.records.any { it.snapshot_id == "snapshot-1" })
    }

    @Test
    fun a_snapshot_whose_secret_the_server_lost_recovers_nothing() = runTest {
        snapshot_the_old_vault()
        arm_reset()
        recovery_api.secrets.clear()

        assertEquals(0, device_recovery.run(user_id))
        assertTrue(commits.isEmpty())
        assertTrue(recovery_api.consumed.isEmpty())
    }

    @Test
    fun pruning_keeps_the_archived_matches_and_the_two_newest() = runTest {
        snapshot_the_old_vault()
        val archived_snapshot = snapshot_store.records.single()
        arm_reset()
        commit_result = false

        repeat(3) { index ->
            vault_json = vault(current_key.armored_private_key, listOf("filler-$index"))
            assertTrue(device_recovery.refresh_snapshot(user_id))
            clock += 1_000
        }

        assertEquals(0, device_recovery.run(user_id))

        val kept = snapshot_store.records.map { it.snapshot_id }
        assertTrue(archived_snapshot.snapshot_id in kept)
        assertEquals(3, kept.size)
        assertTrue(recovery_api.deletes.flatten().isNotEmpty())
        assertTrue(recovery_api.deletes.flatten().none { it == archived_snapshot.snapshot_id })
    }

    @Test
    fun expired_snapshots_leave_the_device_and_only_the_signed_in_secrets_leave_the_server() = runTest {
        assertTrue(device_recovery.refresh_snapshot(user_id))
        snapshot_store.records.add(
            DeviceSnapshotRecord(
                snapshot_id = "other-1",
                user_id = "user-2",
                source_hash = "hash-other",
                created_at = clock,
                iv = ByteArray(12),
                sealed = ByteArray(32),
            ),
        )
        clock += DEVICE_RECOVERY_SNAPSHOT_TTL_MS + 1
        vault_json = vault(current_key.armored_private_key, listOf("later"))

        device_recovery.run(user_id)

        assertTrue(snapshot_store.records.none { it.snapshot_id == "snapshot-1" })
        assertTrue(snapshot_store.records.none { it.snapshot_id == "other-1" })
        assertTrue(recovery_api.deletes.any { "snapshot-1" in it })
        assertTrue(recovery_api.deletes.none { "other-1" in it })
    }

    @Test
    fun expiry_runs_even_when_the_feature_is_off() = runTest {
        assertTrue(device_recovery.refresh_snapshot(user_id))
        capabilities = AccountKeyCapabilityFlags(
            format_writes = true,
            data_conversion = true,
            device_recovery = false,
        )
        clock += DEVICE_RECOVERY_SNAPSHOT_TTL_MS + 1

        assertEquals(0, device_recovery.run(user_id))
        assertTrue(snapshot_store.records.isEmpty())
    }

    @Test
    fun secrets_are_fetched_in_batches_of_sixteen() = runTest {
        val archived_vault = Base64.getEncoder().encodeToString("archived".toByteArray(Charsets.UTF_8))
        val archived_hash = vault_ciphertext_hash(archived_vault)
        recovery_api.inactive_sets = listOf(InactiveKeySetInfo(id = "set-1"))
        recovery_api.inactive_vaults["set-1"] = archived_vault
        repeat(20) { index ->
            val id = "batch-$index"
            snapshot_store.records.add(
                DeviceSnapshotRecord(
                    snapshot_id = id,
                    user_id = user_id,
                    source_hash = archived_hash,
                    created_at = clock - index,
                    iv = ByteArray(12),
                    sealed = ByteArray(48),
                ),
            )
            recovery_api.secrets[id] = Base64.getEncoder().encodeToString(ByteArray(32) { 3 })
        }

        assertEquals(0, device_recovery.run(user_id))

        assertEquals(listOf(16, 4), recovery_api.fetched_secret_ids.map { it.size })
        assertEquals(20, recovery_api.fetched_secret_ids.flatten().toSet().size)
        assertEquals(20, state_store.attempts(user_id).size)
    }

    @Test
    fun a_snapshot_is_tried_once_a_day_and_at_most_three_times() = runTest {
        snapshot_the_old_vault()
        arm_reset()
        commit_result = false

        device_recovery.run(user_id)
        device_recovery.run(user_id)
        assertEquals(1, commits.size)

        repeat(4) {
            clock += DEVICE_RECOVERY_ATTEMPT_SPACING_MS
            device_recovery.run(user_id)
        }

        assertEquals(DEVICE_RECOVERY_MAX_ATTEMPTS, commits.size)
        assertEquals(DEVICE_RECOVERY_MAX_ATTEMPTS, state_store.attempts(user_id)["snapshot-1"]?.count)
        assertTrue(recovery_api.consumed.isEmpty())
        assertTrue(snapshot_store.records.any { it.snapshot_id == "snapshot-1" })
    }

    @Test
    fun a_consumed_snapshot_forgets_its_attempts() = runTest {
        snapshot_the_old_vault()
        arm_reset()

        assertEquals(1, device_recovery.run(user_id))
        assertTrue(state_store.attempts(user_id).isEmpty())
    }

    @Test
    fun dropped_keks_leave_the_archived_set_unconsumed() = runTest {
        snapshot_the_old_vault()
        arm_reset()
        commit_dropped = 1

        assertEquals(0, device_recovery.run(user_id))
        assertEquals(1, commits.size)
        assertTrue(recovery_api.consumed.isEmpty())
        assertTrue(snapshot_store.records.any { it.snapshot_id == "snapshot-1" })
    }

    @Test
    fun the_commit_carries_the_vault_the_merge_started_from() = runTest {
        snapshot_the_old_vault()
        arm_reset()

        device_recovery.run(user_id)

        assertEquals(encoded_vault(), commits.single().base_encrypted_vault)
    }

    @Test
    fun the_periodic_refresh_prunes_with_the_last_archived_hashes() = runTest {
        device_recovery.tick(user_id)
        repeat(3) { index ->
            clock += 1_000
            vault_json = vault(current_key.armored_private_key, listOf("tick-$index"))
            device_recovery.tick(user_id)
        }

        assertEquals(1, recovery_api.calls.count { it == "list_inactive" })
        assertEquals(DEVICE_RECOVERY_SNAPSHOTS_KEPT, snapshot_store.records.size)
        assertTrue(recovery_api.deletes.flatten().containsAll(listOf("snapshot-1", "snapshot-2")))
    }

    @Test
    fun a_new_account_on_the_device_gets_a_full_run() = runTest {
        device_recovery.tick(user_id)
        session_user = "user-2"
        device_recovery.tick("user-2")

        assertEquals(2, recovery_api.calls.count { it == "list_inactive" })
    }

    @Test
    fun a_failed_secret_upload_waits_an_hour() = runTest {
        recovery_api.put_succeeds = false
        assertFalse(device_recovery.refresh_snapshot(user_id))
        assertFalse(device_recovery.refresh_snapshot(user_id))
        assertEquals(1, recovery_api.calls.count { it == "put" })

        recovery_api.put_succeeds = true
        clock += DEVICE_RECOVERY_PUT_BACKOFF_MS
        assertTrue(device_recovery.refresh_snapshot(user_id))
        assertEquals(0L, state_store.refresh_not_before(user_id))
    }

    @Test
    fun a_rate_limited_secret_upload_waits_six_hours() = runTest {
        recovery_api.put_rate_limited = true
        assertFalse(device_recovery.refresh_snapshot(user_id))

        recovery_api.put_rate_limited = false
        clock += DEVICE_RECOVERY_PUT_BACKOFF_MS
        assertFalse(device_recovery.refresh_snapshot(user_id))
        assertEquals(1, recovery_api.calls.count { it == "put" })

        clock += DEVICE_RECOVERY_PUT_RATE_LIMITED_BACKOFF_MS - DEVICE_RECOVERY_PUT_BACKOFF_MS
        assertTrue(device_recovery.refresh_snapshot(user_id))
    }

    @Test
    fun another_signed_in_account_is_never_snapshotted_or_recovered() = runTest {
        snapshot_the_old_vault()
        arm_reset()
        session_user = "user-2"

        assertEquals(0, device_recovery.run(user_id))
        assertFalse(device_recovery.refresh_snapshot(user_id))
        assertTrue(commits.isEmpty())
        assertTrue(recovery_api.consumed.isEmpty())
    }

    @Test
    fun attempts_round_trip_through_their_stored_form() {
        val encoded = encode_device_recovery_attempts(mapOf("snapshot-1" to DeviceRecoveryAttempt(2, 1234L)))
        val decoded = decode_device_recovery_attempts(encoded)

        assertEquals(2, decoded["snapshot-1"]?.count)
        assertEquals(1234L, decoded["snapshot-1"]?.last_at)
        assertTrue(decode_device_recovery_attempts("not json").isEmpty())
        assertTrue(device_recovery_attempt_allowed(null, 0L))
        assertFalse(
            device_recovery_attempt_allowed(DeviceRecoveryAttempt(1, 0L), DEVICE_RECOVERY_ATTEMPT_SPACING_MS - 1),
        )
        assertFalse(
            device_recovery_attempt_allowed(DeviceRecoveryAttempt(DEVICE_RECOVERY_MAX_ATTEMPTS, 0L), Long.MAX_VALUE),
        )
    }

    @Test
    fun a_failed_listing_prunes_nothing() = runTest {
        snapshot_the_old_vault()
        recovery_api.list_fails = true

        assertEquals(0, device_recovery.run(user_id))
        assertTrue(snapshot_store.records.isNotEmpty())
        assertTrue(recovery_api.deletes.isEmpty())
    }
}
