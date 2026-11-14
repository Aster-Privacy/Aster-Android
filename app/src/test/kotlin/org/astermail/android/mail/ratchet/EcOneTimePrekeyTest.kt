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

import io.mockk.every
import io.mockk.mockkStatic
import io.mockk.unmockkStatic
import java.util.Base64
import kotlinx.coroutines.test.runTest
import org.astermail.android.api.ratchet.EcPrekeyStatusResponse
import org.astermail.android.api.ratchet.EnvelopeCapabilityResponse
import org.astermail.android.api.ratchet.PostStateOutcome
import org.astermail.android.api.ratchet.PqSecretResponse
import org.astermail.android.api.ratchet.PrekeyBundleResponse
import org.astermail.android.api.ratchet.PutStateOutcome
import org.astermail.android.api.ratchet.RatchetApi
import org.astermail.android.api.ratchet.RatchetStateResponse
import org.astermail.android.api.ratchet.ReportEnvelopeCapabilityRequest
import org.astermail.android.api.ratchet.UploadEcPrekeysRequest
import org.astermail.android.api.ratchet.UploadPrekeyBundleRequest
import org.astermail.android.crypto.ratchet.RatchetCrypto
import org.junit.After
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

private class MemoryEcSecretStorage(
    var is_available: Boolean = true,
    var accepts_writes: Boolean = true,
) : EcOneTimeSecretStorage {
    val secrets = mutableMapOf<Int, ByteArray>()

    override fun available(): Boolean = is_available

    override fun put(key_id: Int, scalar: ByteArray): Boolean {
        if (!accepts_writes) return false
        secrets[key_id] = scalar.copyOf()
        return true
    }

    override fun get(key_id: Int): ByteArray? = secrets[key_id]?.copyOf()

    override fun remove(key_id: Int) {
        secrets.remove(key_id)
    }
}

private open class EcPrekeyFakeApi(
    var status: EcPrekeyStatusResponse? = null,
    var upload_result: Boolean = true,
    var remote_secret: PqSecretResponse? = null,
    var status_error: Throwable? = null,
    var delete_error: Throwable? = null,
) : RatchetApi {
    var status_calls = 0
    var secret_calls = 0
    val uploads = mutableListOf<UploadEcPrekeysRequest>()
    val deleted = mutableListOf<Int>()
    val capability_requests = mutableListOf<ReportEnvelopeCapabilityRequest>()

    override suspend fun fetch_ec_prekey_status(): EcPrekeyStatusResponse? {
        status_calls += 1
        status_error?.let { throw it }
        return status
    }

    override suspend fun upload_ec_prekeys(request: UploadEcPrekeysRequest): Boolean {
        uploads.add(request)
        return upload_result
    }

    override suspend fun fetch_ec_prekey_secret(key_id: Int): PqSecretResponse? {
        secret_calls += 1
        return remote_secret
    }

    override suspend fun delete_ec_prekey(key_id: Int): Boolean {
        deleted.add(key_id)
        delete_error?.let { throw it }
        return true
    }

    override suspend fun report_envelope_capability(
        request: ReportEnvelopeCapabilityRequest,
    ): EnvelopeCapabilityResponse? {
        capability_requests.add(request)
        return EnvelopeCapabilityResponse(success = true)
    }

    override suspend fun fetch_state(conversation_id_b64: String): RatchetStateResponse? = null
    override suspend fun post_state(
        conversation_id_b64: String,
        encrypted_state: String,
        state_nonce: String,
    ): PostStateOutcome = PostStateOutcome.Failure(500)

    override suspend fun put_state(
        conversation_id_b64: String,
        encrypted_state: String,
        state_nonce: String,
        expected_version: Int,
    ): PutStateOutcome = PutStateOutcome.Failure(500)

    override suspend fun fetch_pq_secret(key_id: Int): PqSecretResponse? = null
    override suspend fun fetch_prekey_bundle(username: String, email: String): PrekeyBundleResponse? = null
    override suspend fun delete_state(conversation_id_b64: String): Boolean = false
    override suspend fun upload_prekey_bundle(request: UploadPrekeyBundleRequest): Boolean = false
}

private class MemoryCapabilityStore : EnvelopeCapabilityStore {
    var client_id: String? = null
    val reports = mutableMapOf<String, EnvelopeCapabilityReport>()

    override fun get_client_id(): String? = client_id

    override fun put_client_id(client_id: String) {
        this.client_id = client_id
    }

    override fun get_last_report(user_id: String): EnvelopeCapabilityReport? = reports[user_id]

    override fun put_last_report(user_id: String, report: EnvelopeCapabilityReport) {
        reports[user_id] = report
    }
}

class EcOneTimePrekeyTest {

    private val sync_key = ByteArray(32) { (it + 1).toByte() }
    private val other_key = ByteArray(32) { (it + 100).toByte() }
    private var clock = 1_000_000L

    @Before
    fun set_up() {
        mockkStatic(android.util.Base64::class)
        every { android.util.Base64.encodeToString(any(), any()) } answers {
            val bytes = firstArg<ByteArray>()
            val flags = secondArg<Int>()
            if (flags and android.util.Base64.URL_SAFE != 0) {
                Base64.getUrlEncoder().withoutPadding().encodeToString(bytes)
            } else {
                Base64.getEncoder().encodeToString(bytes)
            }
        }
        every { android.util.Base64.decode(any<String>(), any()) } answers {
            val text = firstArg<String>()
            val flags = secondArg<Int>()
            if (flags and android.util.Base64.URL_SAFE != 0) {
                Base64.getUrlDecoder().decode(text.trimEnd('='))
            } else {
                Base64.getDecoder().decode(text)
            }
        }
    }

    @After
    fun tear_down() {
        unmockkStatic(android.util.Base64::class)
    }

    private fun store(storage: EcOneTimeSecretStorage, api: RatchetApi) = EcOneTimePrekeyStore(
        storage = storage,
        ratchet_api = api,
        sync_key_candidates = { listOf(other_key.copyOf(), sync_key.copyOf()) },
        now_ms = { clock },
    )

    private fun service(
        storage: EcOneTimeSecretStorage,
        api: RatchetApi,
        key: ByteArray? = sync_key,
        ids: Iterator<Int>? = null,
    ) = EcOneTimePrekeyService(
        store = store(storage, api),
        ratchet_api = api,
        sync_key = { key?.copyOf() },
        now_ms = { clock },
        random_key_id = if (ids != null) {
            { ids.next() }
        } else {
            { ec_one_time_key_id_from_bytes(RatchetCrypto.random_bytes(4)) }
        },
    )

    private fun accepted(available: Int = 0, target: Int = 3, max_upload: Int = 100) =
        EcPrekeyStatusResponse(accepted = true, available = available, target = target, max_upload = max_upload)

    @Test
    fun the_wrap_format_is_a_32_byte_scalar_under_aes_gcm_with_a_12_byte_nonce() {
        val scalar = ByteArray(32) { (0xA0 + it).toByte() }
        val wrapped = checkNotNull(EcOneTimeSecretWrap.wrap(scalar, sync_key))

        val sealed = Base64.getDecoder().decode(wrapped.encrypted_secret)
        val nonce = Base64.getDecoder().decode(wrapped.secret_nonce)
        assertEquals(48, sealed.size)
        assertEquals(12, nonce.size)

        val cipher = javax.crypto.Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(
            javax.crypto.Cipher.DECRYPT_MODE,
            javax.crypto.spec.SecretKeySpec(sync_key, "AES"),
            javax.crypto.spec.GCMParameterSpec(128, nonce),
        )
        assertArrayEquals(scalar, cipher.doFinal(sealed))
    }

    @Test
    fun a_secret_wrapped_elsewhere_with_the_same_format_unwraps() {
        val scalar = ByteArray(32) { (it * 3).toByte() }
        val nonce = ByteArray(12) { (it + 9).toByte() }
        val cipher = javax.crypto.Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(
            javax.crypto.Cipher.ENCRYPT_MODE,
            javax.crypto.spec.SecretKeySpec(sync_key, "AES"),
            javax.crypto.spec.GCMParameterSpec(128, nonce),
        )
        val sealed = cipher.doFinal(scalar)

        val opened = EcOneTimeSecretWrap.unwrap(
            Base64.getEncoder().encodeToString(sealed),
            Base64.getEncoder().encodeToString(nonce),
            listOf(other_key, sync_key),
        )

        assertArrayEquals(scalar, opened)
    }

    @Test
    fun unwrap_rejects_wrong_keys_and_wrong_shapes() {
        val scalar = ByteArray(32) { 7 }
        val wrapped = checkNotNull(EcOneTimeSecretWrap.wrap(scalar, sync_key))

        assertNull(EcOneTimeSecretWrap.unwrap(wrapped.encrypted_secret, wrapped.secret_nonce, listOf(other_key)))
        assertNull(EcOneTimeSecretWrap.unwrap(wrapped.encrypted_secret, wrapped.secret_nonce, emptyList()))
        assertNull(
            EcOneTimeSecretWrap.unwrap(
                wrapped.encrypted_secret,
                Base64.getEncoder().encodeToString(ByteArray(16)),
                listOf(sync_key),
            ),
        )
        assertNull(EcOneTimeSecretWrap.wrap(ByteArray(31), sync_key))
        assertNull(EcOneTimeSecretWrap.wrap(scalar, null))

        val long_plain = RatchetCrypto.aes_gcm_encrypt(ByteArray(33), sync_key, ByteArray(12), null)
        assertNull(
            EcOneTimeSecretWrap.unwrap(
                Base64.getEncoder().encodeToString(long_plain),
                Base64.getEncoder().encodeToString(ByteArray(12)),
                listOf(sync_key),
            ),
        )
    }

    @Test
    fun key_ids_come_from_four_masked_big_endian_bytes() {
        assertEquals(16909060, ec_one_time_key_id_from_bytes(byteArrayOf(1, 2, 3, 4)))
        assertEquals(
            0x7fffffff,
            ec_one_time_key_id_from_bytes(byteArrayOf(-1, -1, -1, -1)),
        )
        assertEquals(1, ec_one_time_key_id_from_bytes(byteArrayOf(0, 0, 0, 0)))
        assertEquals(1, ec_one_time_key_id_from_bytes(byteArrayOf(-128, 0, 0, 0)))
    }

    @Test
    fun the_planned_upload_count_follows_the_status() {
        assertEquals(0, planned_ec_prekey_upload_count(null))
        assertEquals(0, planned_ec_prekey_upload_count(EcPrekeyStatusResponse()))
        assertEquals(0, planned_ec_prekey_upload_count(accepted().copy(accepted = false)))
        assertEquals(3, planned_ec_prekey_upload_count(accepted()))
        assertEquals(2, planned_ec_prekey_upload_count(accepted(available = 1)))
        assertEquals(0, planned_ec_prekey_upload_count(accepted(available = 3)))
        assertEquals(0, planned_ec_prekey_upload_count(accepted(available = 9)))
        assertEquals(1, planned_ec_prekey_upload_count(accepted(max_upload = 1)))
        assertEquals(50, planned_ec_prekey_upload_count(accepted(target = 400, max_upload = 400)))
        assertEquals(0, planned_ec_prekey_upload_count(accepted(max_upload = 0)))
        assertEquals(0, planned_ec_prekey_upload_count(accepted().copy(target = null)))
    }

    @Test
    fun refill_stays_silent_when_the_server_has_no_route() = runTest {
        val storage = MemoryEcSecretStorage()
        val api = EcPrekeyFakeApi(status = null)
        val refill = service(storage, api)

        assertEquals(0, refill.replenish())
        assertEquals(0, refill.replenish())

        assertEquals(1, api.status_calls)
        assertTrue(api.uploads.isEmpty())
        assertTrue(storage.secrets.isEmpty())
    }

    @Test
    fun refill_uploads_nothing_until_the_server_accepts() = runTest {
        val storage = MemoryEcSecretStorage()
        val api = EcPrekeyFakeApi(status = accepted().copy(accepted = false))
        val refill = service(storage, api)

        assertEquals(0, refill.replenish())
        assertTrue(api.uploads.isEmpty())
        assertTrue(storage.secrets.isEmpty())

        api.status = accepted()
        clock += EC_PREKEY_RECHECK_MS - 1
        assertEquals(0, refill.replenish())
        assertEquals(1, api.status_calls)

        clock += 1
        assertEquals(3, refill.replenish())
        assertEquals(2, api.status_calls)
    }

    @Test
    fun refill_swallows_a_status_error() = runTest {
        val storage = MemoryEcSecretStorage()
        val api = EcPrekeyFakeApi(status_error = java.io.IOException("offline"))

        assertEquals(0, service(storage, api).replenish())
        assertTrue(api.uploads.isEmpty())
        assertTrue(storage.secrets.isEmpty())
    }

    @Test
    fun refill_uploads_wrapped_keys_and_keeps_the_scalars_locally() = runTest {
        val storage = MemoryEcSecretStorage()
        val api = EcPrekeyFakeApi(status = accepted(target = 4))

        assertEquals(4, service(storage, api).replenish())

        val prekeys = api.uploads.single().prekeys
        assertEquals(4, prekeys.size)
        assertEquals(4, prekeys.map { it.key_id }.toSet().size)
        for (prekey in prekeys) {
            assertTrue(X3dh.is_valid_one_time_prekey_id(prekey.key_id))
            val public_raw = Base64.getDecoder().decode(prekey.public_key)
            assertEquals(65, public_raw.size)
            assertEquals(0x04.toByte(), public_raw[0])
            assertEquals(48, Base64.getDecoder().decode(prekey.encrypted_secret).size)
            assertEquals(12, Base64.getDecoder().decode(prekey.secret_nonce).size)

            val scalar = checkNotNull(
                EcOneTimeSecretWrap.unwrap(prekey.encrypted_secret, prekey.secret_nonce, listOf(sync_key)),
            )
            assertArrayEquals(storage.secrets[prekey.key_id], scalar)
            assertArrayEquals(
                public_raw,
                RatchetCrypto.p256_public_raw_from_private_jwk(
                    RatchetCrypto.p256_private_to_jwk(RatchetCrypto.raw_d_to_private(scalar)),
                ),
            )
        }
    }

    @Test
    fun refill_drops_local_copies_when_the_upload_fails() = runTest {
        val storage = MemoryEcSecretStorage()
        val api = EcPrekeyFakeApi(status = accepted(), upload_result = false)

        assertEquals(0, service(storage, api).replenish())

        assertEquals(1, api.uploads.size)
        assertTrue(storage.secrets.isEmpty())
    }

    @Test
    fun refill_does_nothing_without_storage_or_a_sync_key() = runTest {
        val locked = MemoryEcSecretStorage(is_available = false)
        val locked_api = EcPrekeyFakeApi(status = accepted())
        assertEquals(0, service(locked, locked_api).replenish())
        assertEquals(0, locked_api.status_calls)

        val read_only = MemoryEcSecretStorage(accepts_writes = false)
        val read_only_api = EcPrekeyFakeApi(status = accepted())
        assertEquals(0, service(read_only, read_only_api).replenish())
        assertTrue(read_only_api.uploads.isEmpty())

        val storage = MemoryEcSecretStorage()
        val api = EcPrekeyFakeApi(status = accepted())
        assertEquals(0, service(storage, api, key = null).replenish())
        assertTrue(api.uploads.isEmpty())
        assertTrue(storage.secrets.isEmpty())
    }

    @Test
    fun refill_skips_key_ids_already_in_use() = runTest {
        val storage = MemoryEcSecretStorage()
        storage.secrets[5] = ByteArray(32) { 1 }
        val api = EcPrekeyFakeApi(status = accepted(target = 2))
        val ids = listOf(5, 0, 6, 6, 7).iterator()

        assertEquals(2, service(storage, api, ids = ids).replenish())

        assertEquals(listOf(6, 7), api.uploads.single().prekeys.map { it.key_id })
    }

    @Test
    fun the_store_prefers_the_local_copy() = runTest {
        val storage = MemoryEcSecretStorage()
        val scalar = ByteArray(32) { (it + 5).toByte() }
        storage.secrets[42] = scalar
        val api = EcPrekeyFakeApi()

        assertArrayEquals(scalar, store(storage, api).load(42))
        assertEquals(0, api.secret_calls)
    }

    @Test
    fun the_store_falls_back_to_the_server_copy_and_caches_it() = runTest {
        val storage = MemoryEcSecretStorage()
        val scalar = RatchetCrypto.private_to_raw_d(RatchetCrypto.generate_p256_keypair().private_key)
        val wrapped = checkNotNull(EcOneTimeSecretWrap.wrap(scalar, sync_key))
        val api = EcPrekeyFakeApi(
            remote_secret = PqSecretResponse(42, wrapped.encrypted_secret, wrapped.secret_nonce),
        )
        val prekeys = store(storage, api)

        assertArrayEquals(scalar, prekeys.load(42))
        assertArrayEquals(scalar, storage.secrets[42])
        assertArrayEquals(scalar, prekeys.load(42))
        assertEquals(1, api.secret_calls)

        val jwk = checkNotNull(prekeys.load_private_jwk(42))
        assertArrayEquals(scalar, RatchetCrypto.jwk_extract_d_bytes(jwk))
    }

    @Test
    fun a_missing_key_is_remembered_for_ten_minutes() = runTest {
        val storage = MemoryEcSecretStorage()
        val api = EcPrekeyFakeApi(remote_secret = null)
        val prekeys = store(storage, api)

        assertNull(prekeys.load(42))
        assertNull(prekeys.load(42))
        assertNull(prekeys.load_private_jwk(42))
        assertEquals(1, api.secret_calls)

        clock += EC_ONE_TIME_MISS_TTL_MS
        assertNull(prekeys.load(42))
        assertEquals(2, api.secret_calls)

        prekeys.clear_missing()
        assertNull(prekeys.load(42))
        assertEquals(3, api.secret_calls)
    }

    @Test
    fun an_unreadable_server_copy_counts_as_missing() = runTest {
        val storage = MemoryEcSecretStorage()
        val wrapped = checkNotNull(EcOneTimeSecretWrap.wrap(ByteArray(32) { 3 }, ByteArray(32) { 99 }))
        val api = EcPrekeyFakeApi(
            remote_secret = PqSecretResponse(42, wrapped.encrypted_secret, wrapped.secret_nonce),
        )

        assertNull(store(storage, api).load(42))
        assertTrue(storage.secrets.isEmpty())
    }

    @Test
    fun invalid_key_ids_never_reach_the_server() = runTest {
        val storage = MemoryEcSecretStorage()
        val api = EcPrekeyFakeApi()
        val prekeys = store(storage, api)

        for (bad_id in listOf(null, 0, -1)) {
            assertNull(prekeys.load(bad_id))
            prekeys.consume(bad_id)
        }

        assertEquals(0, api.secret_calls)
        assertTrue(api.deleted.isEmpty())
    }

    @Test
    fun consuming_removes_the_local_and_server_copies_and_swallows_errors() = runTest {
        val storage = MemoryEcSecretStorage()
        storage.secrets[42] = ByteArray(32) { 1 }
        storage.secrets[43] = ByteArray(32) { 2 }
        val api = EcPrekeyFakeApi()
        val prekeys = store(storage, api)

        prekeys.consume(42)
        assertFalse(storage.secrets.containsKey(42))
        assertEquals(listOf(42), api.deleted)

        api.delete_error = java.io.IOException("offline")
        prekeys.consume(43)
        assertFalse(storage.secrets.containsKey(43))
    }

    @Test
    fun capability_reports_four_only_when_one_time_storage_works() = runTest {
        val identity = Base64.getEncoder().encodeToString(ByteArray(65) { if (it == 0) 0x04 else 0x11 })
        val api = EcPrekeyFakeApi()
        val reporter = EnvelopeCapabilityReporter(
            store = MemoryCapabilityStore(),
            ratchet_api = api,
            new_client_id = { "fixed-client-id" },
            now_ms = { clock },
        )

        assertNotNull(reporter.report_if_due("user", identity))
        assertEquals(3, api.capability_requests.last().x3dh_max_version)

        assertNull(reporter.report_if_due("user", identity))
        assertEquals(1, api.capability_requests.size)

        assertNotNull(reporter.report_if_due("user", identity, one_time_prekeys_readable = true))
        assertEquals(4, api.capability_requests.last().x3dh_max_version)

        assertNull(reporter.report_if_due("user", identity, one_time_prekeys_readable = true))
        assertEquals(2, api.capability_requests.size)

        assertNotNull(reporter.report_if_due("user", identity, one_time_prekeys_readable = false))
        assertEquals(3, api.capability_requests.last().x3dh_max_version)
    }

    @Test
    fun an_older_saved_report_counts_as_version_three() = runTest {
        val identity = Base64.getEncoder().encodeToString(ByteArray(65) { if (it == 0) 0x04 else 0x11 })
        val api = EcPrekeyFakeApi()
        val saved = MemoryCapabilityStore()
        val reporter = EnvelopeCapabilityReporter(
            store = saved,
            ratchet_api = api,
            new_client_id = { "fixed-client-id" },
            now_ms = { clock },
        )
        reporter.report_if_due("user", identity)
        saved.reports["user"] = checkNotNull(saved.reports["user"]).copy(x3dh_max_version = 0)

        assertNull(reporter.report_if_due("user", identity))
        assertEquals(1, api.capability_requests.size)
        assertEquals(3, EnvelopeCapabilityReporter.reported_x3dh_max_version(false))
        assertEquals(4, EnvelopeCapabilityReporter.reported_x3dh_max_version(true))
    }
}
