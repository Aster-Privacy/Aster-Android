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
import io.mockk.unmockkAll
import java.util.Base64
import java.util.Collections
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import org.astermail.android.api.keys.AccountDataConversionStatus
import org.astermail.android.api.keys.AccountKeyCapabilityFlags
import org.astermail.android.api.keys.ConversionProgressRequest
import org.astermail.android.api.keys.ConversionWriteResult
import org.astermail.android.api.keys.ConvertSentEnvelopeRequest
import org.astermail.android.api.keys.KeysApi
import org.astermail.android.api.mail.MailApi
import org.astermail.android.api.mail.MailItem
import org.astermail.android.api.mail.MailItemsListResponse
import org.astermail.android.api.preferences.PreferencesApi
import org.astermail.android.crypto.PgpDecryptor
import org.astermail.android.crypto.PgpKeyGenerator
import org.astermail.android.crypto.PgpKeyPairResult
import org.astermail.android.storage.SessionKeyStore
import org.junit.After
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class AccountDataConversionPasswordChangeTest {

    private val typed_password = "typed current password"
    private val typed_bytes = typed_password.toByteArray(Charsets.UTF_8)
    private val stored_passphrase = "stored session passphrase".toByteArray(Charsets.UTF_8)
    private val envelope_json = """{"version":1,"subject":"Quarterly plan","body_html":"<p>Body text</p>"}"""

    private lateinit var session_key_store: SessionKeyStore
    private lateinit var keys_api: KeysApi
    private lateinit var mail_api: MailApi
    private lateinit var conversion: AccountDataConversion

    private var identity_key = ""
    private var session_passphrase = stored_passphrase
    @Volatile
    private var clock = 1_800_000_000_000L
    private var capabilities = AccountKeyCapabilityFlags(format_writes = true, data_conversion = true)
    @Volatile
    private var status: AccountDataConversionStatus? = null
    private val queued_statuses = ArrayDeque<AccountDataConversionStatus?>()
    private var status_error: Throwable? = null
    @Volatile
    private var status_fetches = 0
    private var pages: MutableList<MailItemsListResponse> = mutableListOf()
    private var listing_hook: suspend () -> Unit = {}
    private var write_hook: () -> Unit = {}

    private val listing_cursors = Collections.synchronizedList(mutableListOf<String?>())
    private val sent_writes = Collections.synchronizedList(mutableListOf<Pair<String, ConvertSentEnvelopeRequest>>())
    private val progress = Collections.synchronizedList(mutableListOf<ConversionProgressRequest>())

    private val scan_store = object : AccountDataConversionScanStore {
        override fun read_last_scan(account_id: String): Long = 0L
        override fun write_last_scan(account_id: String, at_ms: Long) {}
    }

    companion object {
        private val own_key: PgpKeyPairResult by lazy {
            PgpKeyGenerator.generate("Owner", "owner@astermail.org", "typed current password".toCharArray())
        }
        private val other_key: PgpKeyPairResult by lazy {
            PgpKeyGenerator.generate("Owner", "owner@astermail.org", "typed current password".toCharArray())
        }
    }

    @Before
    fun setup() {
        identity_key = own_key.armored_private_key
        status = status_of(remaining_sent = 1)

        session_key_store = mockk(relaxed = true)
        every { session_key_store.get_identity_key() } answers { identity_key }
        every { session_key_store.get_previous_keys() } returns emptyList()
        every { session_key_store.get_user_id() } returns "account-1"
        every { session_key_store.get_decrypt_keks() } returns emptyList()
        every { session_key_store.get_passphrase() } answers { session_passphrase.copyOf() }

        keys_api = mockk(relaxed = true)
        coEvery { keys_api.get_account_key_capabilities() } answers { capabilities }
        coEvery { keys_api.get_account_data_conversion() } answers {
            status_fetches += 1
            status_error?.let { throw it }
            if (queued_statuses.isNotEmpty()) queued_statuses.removeFirst() else status
        }
        coEvery { keys_api.convert_sent_envelope(any(), any()) } answers {
            sent_writes.add(firstArg<String>() to secondArg())
            write_hook()
            ConversionWriteResult.CONVERTED
        }
        coEvery { keys_api.report_account_data_conversion(any()) } answers {
            progress.add(firstArg())
            true
        }

        mail_api = mockk(relaxed = true)
        coEvery { mail_api.list_encrypted_items(any(), any(), any()) } coAnswers {
            listing_cursors.add(secondArg())
            listing_hook()
            pages.getOrNull(listing_cursors.size - 1) ?: MailItemsListResponse()
        }

        conversion = AccountDataConversion(
            mail_api,
            keys_api,
            mockk<PreferencesApi>(relaxed = true),
            session_key_store,
            scan_store,
        ) { clock }
    }

    @After
    fun teardown() {
        unmockkAll()
    }

    private fun status_of(
        remaining_sent: Long = 0,
        remaining_attachments: Long = 0,
        sent_mail_done_at: String? = null,
    ) = AccountDataConversionStatus(
        sent_mail_done_at = sent_mail_done_at,
        preferences_done_at = "2026-09-01T00:00:00Z",
        remaining_sent = remaining_sent,
        remaining_attachments = remaining_attachments,
    )

    private fun sentinel_item(id: String, passphrase: ByteArray = typed_bytes) = MailItem(
        id = id,
        encrypted_envelope = SentMailResealCrypto.seal(envelope_json.toByteArray(Charsets.UTF_8), passphrase),
        envelope_nonce = SentMailResealCrypto.sentinel_nonce_b64(),
    )

    private fun page(vararg items: MailItem) = MailItemsListResponse(items = items.toList())

    private fun open_sealed(sealed_b64: String): String? {
        val armored = String(Base64.getDecoder().decode(sealed_b64), Charsets.UTF_8)
        return PgpDecryptor.decrypt_with_own_keys_status(
            armored,
            listOf(own_key.armored_private_key),
            typed_password.toCharArray(),
        )?.plaintext
    }

    private suspend fun convert(budget_ms: Long = AccountDataConversion.PASSWORD_CHANGE_BUDGET_MS) =
        conversion.convert_before_password_change(identity_key, typed_bytes.copyOf(), budget_ms)

    @Test
    fun is_unavailable_when_the_flag_is_off() = runBlocking {
        capabilities = AccountKeyCapabilityFlags(format_writes = true, data_conversion = false)
        pages = mutableListOf(page(sentinel_item("sent-1")))

        assertEquals(PasswordChangeConversion.UNAVAILABLE, convert())
        assertEquals(0, status_fetches)
        assertEquals(0, listing_cursors.size)
        assertTrue(sent_writes.isEmpty())
    }

    @Test
    fun is_unavailable_when_the_identity_key_differs_from_the_session() = runBlocking {
        pages = mutableListOf(page(sentinel_item("sent-1")))

        val result = conversion.convert_before_password_change(other_key.armored_private_key, typed_bytes.copyOf())

        assertEquals(PasswordChangeConversion.UNAVAILABLE, result)
        assertEquals(0, listing_cursors.size)
        assertTrue(sent_writes.isEmpty())
    }

    @Test
    fun is_unavailable_without_a_status() = runBlocking {
        status = null

        assertEquals(PasswordChangeConversion.UNAVAILABLE, convert())
        assertEquals(0, listing_cursors.size)
    }

    @Test
    fun is_unavailable_for_an_empty_password() = runBlocking {
        assertEquals(
            PasswordChangeConversion.UNAVAILABLE,
            conversion.convert_before_password_change(identity_key, ByteArray(0)),
        )
        assertEquals(0, status_fetches)
    }

    @Test
    fun is_complete_without_writing_when_nothing_remains() = runBlocking {
        status = status_of()
        pages = mutableListOf(page(sentinel_item("sent-1")))

        assertEquals(PasswordChangeConversion.COMPLETE, convert())
        assertEquals(0, listing_cursors.size)
        assertTrue(sent_writes.isEmpty())
        assertTrue(progress.isEmpty())
    }

    @Test
    fun converts_with_the_typed_password_and_records_completion() = runBlocking {
        pages = mutableListOf(page(sentinel_item("sent-1")))
        queued_statuses.addAll(listOf(status_of(remaining_sent = 1), status_of()))

        assertEquals(PasswordChangeConversion.COMPLETE, convert())
        assertEquals(envelope_json, open_sealed(sent_writes.single().second.encrypted_envelope))
        assertTrue(progress.contains(ConversionProgressRequest(sent_mail_done = true)))
        assertEquals(2, status_fetches)
    }

    @Test
    fun does_not_record_completion_twice() = runBlocking {
        pages = mutableListOf(page(sentinel_item("sent-1")))
        queued_statuses.addAll(
            listOf(status_of(remaining_sent = 1), status_of(sent_mail_done_at = "2026-09-19T00:00:00Z")),
        )

        assertEquals(PasswordChangeConversion.COMPLETE, convert())
        assertFalse(progress.contains(ConversionProgressRequest(sent_mail_done = true)))
    }

    @Test
    fun does_not_open_mail_with_the_stored_session_passphrase() = runBlocking {
        pages = mutableListOf(page(sentinel_item("sent-1", passphrase = stored_passphrase)))
        queued_statuses.addAll(listOf(status_of(remaining_sent = 1), status_of(remaining_sent = 1)))

        assertEquals(PasswordChangeConversion.INCOMPLETE, convert())
        assertTrue(sent_writes.isEmpty())
    }

    @Test
    fun is_incomplete_when_the_fresh_status_still_has_items() = runBlocking {
        pages = mutableListOf(page(sentinel_item("sent-1")))
        queued_statuses.addAll(listOf(status_of(remaining_sent = 1), status_of(remaining_attachments = 1)))

        assertEquals(PasswordChangeConversion.INCOMPLETE, convert())
        assertEquals(1, sent_writes.size)
        assertFalse(progress.contains(ConversionProgressRequest(sent_mail_done = true)))
    }

    @Test
    fun is_incomplete_when_the_fresh_status_is_missing() = runBlocking {
        pages = mutableListOf(page(sentinel_item("sent-1")))
        queued_statuses.addAll(listOf(status_of(remaining_sent = 1), null))

        assertEquals(PasswordChangeConversion.INCOMPLETE, convert())
    }

    @Test
    fun stops_as_incomplete_when_the_deadline_passes() = runBlocking {
        status = status_of(remaining_sent = 2)
        pages = mutableListOf(page(sentinel_item("sent-1"), sentinel_item("sent-2")))
        write_hook = { clock += AccountDataConversion.PASSWORD_CHANGE_BUDGET_MS }

        assertEquals(PasswordChangeConversion.INCOMPLETE, convert())
        assertEquals(listOf("sent-1"), sent_writes.map { it.first })
        assertEquals(1, status_fetches)
    }

    @Test
    fun is_incomplete_when_a_request_throws() = runBlocking {
        status_error = IllegalStateException("network")

        assertEquals(PasswordChangeConversion.INCOMPLETE, convert())
    }

    @Test
    fun is_incomplete_when_the_listing_throws() = runBlocking {
        listing_hook = { throw IllegalStateException("listing") }

        assertEquals(PasswordChangeConversion.INCOMPLETE, convert())
        assertTrue(sent_writes.isEmpty())
    }

    @Test
    fun leaves_the_callers_password_bytes_intact() = runBlocking {
        pages = mutableListOf(page(sentinel_item("sent-1")))
        queued_statuses.addAll(listOf(status_of(remaining_sent = 1), status_of()))
        val password = typed_bytes.copyOf()

        conversion.convert_before_password_change(identity_key, password)

        assertArrayEquals(typed_bytes, password)
    }

    @Test
    fun waits_for_the_background_job_to_release_the_lock() = runBlocking {
        session_passphrase = typed_bytes
        pages = mutableListOf(page(sentinel_item("sent-1")))
        val listing_started = CompletableDeferred<Unit>()
        val release = CompletableDeferred<Unit>()
        listing_hook = {
            listing_started.complete(Unit)
            release.await()
        }
        write_hook = { status = status_of() }

        val background = async(Dispatchers.Default) { conversion.run("account-1") }
        listing_started.await()
        val before_change = async(Dispatchers.Default) { convert() }
        delay(200)
        assertFalse(before_change.isCompleted)

        release.complete(Unit)

        assertEquals(1, background.await()!!.converted)
        assertEquals(PasswordChangeConversion.COMPLETE, before_change.await())
        assertEquals(1, sent_writes.size)
    }

    @Test
    fun gives_up_on_the_lock_at_the_deadline() = runBlocking {
        pages = mutableListOf(page(sentinel_item("sent-1", passphrase = stored_passphrase)))
        val listing_started = CompletableDeferred<Unit>()
        val release = CompletableDeferred<Unit>()
        listing_hook = {
            listing_started.complete(Unit)
            release.await()
        }

        val background = async(Dispatchers.Default) { conversion.run("account-1") }
        listing_started.await()
        val fetches_before = status_fetches

        assertEquals(PasswordChangeConversion.INCOMPLETE, convert(budget_ms = 200))
        assertEquals(fetches_before, status_fetches)

        release.complete(Unit)
        assertNotNull(background.await())
    }

    @Test
    fun releases_the_lock_for_the_background_job_afterwards() = runBlocking {
        status = status_of()

        assertEquals(PasswordChangeConversion.COMPLETE, convert())
        assertNotNull(conversion.run("account-1"))
    }

    @Test
    fun reseal_is_needed_when_the_pass_was_not_complete() = runBlocking {
        status = status_of()

        assertTrue(conversion.sent_mail_needs_password_reseal(PasswordChangeConversion.INCOMPLETE))
        assertTrue(conversion.sent_mail_needs_password_reseal(PasswordChangeConversion.UNAVAILABLE))
        assertEquals(0, status_fetches)
    }

    @Test
    fun reseal_is_needed_when_the_fresh_status_has_items() = runBlocking {
        status = status_of(remaining_sent = 1)
        assertTrue(conversion.sent_mail_needs_password_reseal(PasswordChangeConversion.COMPLETE))

        status = status_of(remaining_attachments = 1)
        assertTrue(conversion.sent_mail_needs_password_reseal(PasswordChangeConversion.COMPLETE))
    }

    @Test
    fun reseal_is_needed_without_a_status() = runBlocking {
        status = null

        assertTrue(conversion.sent_mail_needs_password_reseal(PasswordChangeConversion.COMPLETE))
    }

    @Test
    fun reseal_is_needed_when_the_status_request_throws() = runBlocking {
        status_error = IllegalStateException("network")

        assertTrue(conversion.sent_mail_needs_password_reseal(PasswordChangeConversion.COMPLETE))
    }

    @Test
    fun reseal_is_skipped_only_when_complete_and_nothing_remains() = runBlocking {
        status = status_of()

        assertFalse(conversion.sent_mail_needs_password_reseal(PasswordChangeConversion.COMPLETE))
        assertEquals(1, status_fetches)
    }
}
