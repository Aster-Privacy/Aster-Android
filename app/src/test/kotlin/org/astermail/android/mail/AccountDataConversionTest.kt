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
import java.security.MessageDigest
import java.util.Base64
import java.util.Collections
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.runBlocking
import org.astermail.android.api.keys.AccountDataConversionStatus
import org.astermail.android.api.keys.AccountKeyCapabilityFlags
import org.astermail.android.api.keys.ConversionProgressRequest
import org.astermail.android.api.keys.ConversionWriteResult
import org.astermail.android.api.keys.ConvertAttachmentMetaRequest
import org.astermail.android.api.keys.ConvertSentEnvelopeRequest
import org.astermail.android.api.keys.KeysApi
import org.astermail.android.api.mail.AttachmentListResponse
import org.astermail.android.api.mail.AttachmentResponse
import org.astermail.android.api.mail.MailApi
import org.astermail.android.api.mail.MailItem
import org.astermail.android.api.mail.MailItemsListResponse
import org.astermail.android.api.preferences.PreferencesApi
import org.astermail.android.api.preferences.PreferencesSaveResult
import org.astermail.android.api.preferences.SaveVersionedPreferencesRequest
import org.astermail.android.api.preferences.VersionedEncryptedPreferences
import org.astermail.android.crypto.AccountDataWriter
import org.astermail.android.crypto.AesGcm
import org.astermail.android.crypto.PgpDecryptor
import org.astermail.android.crypto.PgpEncryptor
import org.astermail.android.crypto.PgpKeyGenerator
import org.astermail.android.crypto.PgpKeyPairResult
import org.astermail.android.storage.SessionKeyStore
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class AccountDataConversionTest {

    private val passphrase_text = "conversion passphrase"
    private val passphrase_bytes = passphrase_text.toByteArray(Charsets.UTF_8)
    private val envelope_json = """{"version":1,"subject":"Quarterly plan","body_html":"<p>Body text</p>"}"""
    private val meta_json = """{"filename":"plan.pdf","session_key":"c2Vzc2lvbg==","size":10}"""
    private val preferences_json = """{"theme":"dark","language":"en"}"""
    private val preferences_write_key = ByteArray(32) { (it * 7 + 3).toByte() }
    private val interval = AccountDataConversion.RESCAN_INTERVAL_MS

    private lateinit var session_key_store: SessionKeyStore
    private lateinit var keys_api: KeysApi
    private lateinit var mail_api: MailApi
    private lateinit var preferences_api: PreferencesApi
    private lateinit var conversion: AccountDataConversion

    private var identity_key = ""
    private var previous_keys: List<String> = emptyList()
    private var user_id: String? = "account-1"
    private var unlocked = true
    private var clock = 1_800_000_000_000L
    private var capabilities = AccountKeyCapabilityFlags(format_writes = true, data_conversion = true)
    private var status: AccountDataConversionStatus? = null
    private var pages: MutableList<MailItemsListResponse> = mutableListOf()
    private var listing_hook: suspend () -> Unit = {}
    private var attachments: MutableMap<String, List<AttachmentResponse>> = mutableMapOf()
    private var sent_result = ConversionWriteResult.CONVERTED
    private var has_preferences_key = false
    private var stored_preferences: VersionedEncryptedPreferences? = null
    private var preferences_save_result = PreferencesSaveResult.SAVED

    private val listing_cursors = Collections.synchronizedList(mutableListOf<String?>())
    private val sent_writes = Collections.synchronizedList(mutableListOf<Pair<String, ConvertSentEnvelopeRequest>>())
    private val attachment_writes =
        Collections.synchronizedList(mutableListOf<Pair<String, ConvertAttachmentMetaRequest>>())
    private val progress = Collections.synchronizedList(mutableListOf<ConversionProgressRequest>())
    private val preferences_saves = mutableListOf<SaveVersionedPreferencesRequest>()
    private val handed_out_passphrases = mutableListOf<ByteArray>()
    private val scans = mutableMapOf<String, Long>()

    private val scan_store = object : AccountDataConversionScanStore {
        override fun read_last_scan(account_id: String): Long = scans[account_id] ?: 0L
        override fun write_last_scan(account_id: String, at_ms: Long) {
            scans[account_id] = at_ms
        }
    }

    companion object {
        private val own_key: PgpKeyPairResult by lazy {
            PgpKeyGenerator.generate("Owner", "owner@astermail.org", "conversion passphrase".toCharArray())
        }
        private val old_key: PgpKeyPairResult by lazy {
            PgpKeyGenerator.generate("Owner", "owner@astermail.org", "conversion passphrase".toCharArray())
        }
    }

    @Before
    fun setup() {
        identity_key = own_key.armored_private_key
        previous_keys = listOf(old_key.armored_private_key)
        status = status_of(remaining_sent = 1)

        session_key_store = mockk(relaxed = true)
        every { session_key_store.get_identity_key() } answers { identity_key }
        every { session_key_store.get_previous_keys() } answers { previous_keys }
        every { session_key_store.get_user_id() } answers { user_id }
        every { session_key_store.get_decrypt_keks() } returns emptyList()
        every { session_key_store.get_passphrase() } answers {
            if (unlocked) passphrase_bytes.copyOf().also { handed_out_passphrases.add(it) } else null
        }
        every { session_key_store.has_account_write_kek(AccountDataWriter.PREFERENCES_CONTEXT) } answers {
            has_preferences_key
        }
        every { session_key_store.get_account_write_kek(AccountDataWriter.PREFERENCES_CONTEXT) } answers {
            if (has_preferences_key) preferences_write_key.copyOf() else null
        }

        keys_api = mockk(relaxed = true)
        coEvery { keys_api.get_account_key_capabilities() } answers { capabilities }
        coEvery { keys_api.get_account_data_conversion() } answers { status }
        coEvery { keys_api.convert_sent_envelope(any(), any()) } answers {
            sent_writes.add(firstArg<String>() to secondArg())
            sent_result
        }
        coEvery { keys_api.convert_attachment_meta(any(), any()) } answers {
            attachment_writes.add(firstArg<String>() to secondArg())
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
            val index = listing_cursors.size - 1
            pages.getOrNull(index) ?: MailItemsListResponse()
        }
        coEvery { mail_api.list_attachments(any()) } answers {
            val rows = attachments[firstArg()] ?: throw IllegalStateException("listing failed")
            AttachmentListResponse(attachments = rows)
        }

        preferences_api = mockk(relaxed = true)
        coEvery { preferences_api.get_versioned_encrypted_preferences() } answers { stored_preferences }
        coEvery { preferences_api.save_encrypted_preferences_if_version(any()) } answers {
            preferences_saves.add(firstArg())
            preferences_save_result
        }

        conversion = AccountDataConversion(
            mail_api,
            keys_api,
            preferences_api,
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
        preferences_done_at: String? = "2026-09-01T00:00:00Z",
    ) = AccountDataConversionStatus(
        sent_mail_done_at = sent_mail_done_at,
        preferences_done_at = preferences_done_at,
        remaining_sent = remaining_sent,
        remaining_attachments = remaining_attachments,
    )

    private fun b64(bytes: ByteArray): String = Base64.getEncoder().encodeToString(bytes)

    private fun password_envelope(text: String, passphrase: ByteArray = passphrase_bytes): String =
        SentMailResealCrypto.seal(text.toByteArray(Charsets.UTF_8), passphrase)

    private fun sentinel_item(id: String, text: String = envelope_json, envelope: String? = null) = MailItem(
        id = id,
        encrypted_envelope = envelope ?: password_envelope(text),
        envelope_nonce = SentMailResealCrypto.sentinel_nonce_b64(),
    )

    private fun page(vararg items: MailItem, next_cursor: String? = null, has_more: Boolean = false) =
        MailItemsListResponse(items = items.toList(), next_cursor = next_cursor, has_more = has_more)

    private fun legacy_nonce(): String = b64(ByteArray(12) { (it + 1).toByte() })

    private fun attachment(id: String, encrypted_meta: String, meta_nonce: String? = legacy_nonce()) =
        AttachmentResponse(
            id = id,
            mail_item_id = "sent-1",
            encrypted_data = "",
            data_nonce = "",
            encrypted_meta = encrypted_meta,
            meta_nonce = meta_nonce,
            size_bytes = 10,
        )

    private fun sha256_hex_of_b64(value: String): String =
        MessageDigest.getInstance("SHA-256").digest(Base64.getDecoder().decode(value))
            .joinToString("") { "%02x".format(it) }

    private fun open_sealed(sealed_b64: String): String? {
        val armored = String(Base64.getDecoder().decode(sealed_b64), Charsets.UTF_8)
        return PgpDecryptor.decrypt_with_own_keys_status(
            armored,
            listOf(own_key.armored_private_key),
            passphrase_text.toCharArray(),
        )?.plaintext
    }

    private fun legacy_preferences_key(): ByteArray =
        MessageDigest.getInstance("SHA-256")
            .digest((identity_key + AccountDataWriter.PREFERENCES_CONTEXT).toByteArray(Charsets.UTF_8))

    private fun seal_legacy_preferences(version: Int = 4) {
        val nonce = ByteArray(12) { (it + 9).toByte() }
        val sealed = AesGcm.encrypt(legacy_preferences_key(), nonce, preferences_json.toByteArray(Charsets.UTF_8))
        stored_preferences = VersionedEncryptedPreferences(b64(sealed), b64(nonce), version)
    }

    @Test
    fun returns_null_without_listing_when_the_flag_is_off() = runBlocking {
        capabilities = AccountKeyCapabilityFlags(format_writes = true, data_conversion = false)
        pages = mutableListOf(page(sentinel_item("sent-1")))

        assertNull(conversion.run("account-1"))
        assertEquals(0, listing_cursors.size)
        assertTrue(progress.isEmpty())
    }

    @Test
    fun returns_null_when_the_status_is_unavailable() = runBlocking {
        status = null

        assertNull(conversion.run("account-1"))
        assertEquals(0, listing_cursors.size)
    }

    @Test
    fun returns_null_while_the_account_is_locked() = runBlocking {
        unlocked = false
        pages = mutableListOf(page(sentinel_item("sent-1")))

        assertNull(conversion.run("account-1"))
        assertEquals(0, listing_cursors.size)
    }

    @Test
    fun returns_null_for_an_empty_account_id() = runBlocking {
        assertNull(conversion.run(""))
        assertEquals(0, listing_cursors.size)
    }

    @Test
    fun reseals_the_exact_envelope_plaintext_and_records_completion() = runBlocking {
        val item = sentinel_item("sent-1")
        pages = mutableListOf(page(item))

        val summary = conversion.run("account-1")

        assertEquals(AccountDataConversionSummary(checked = 1, converted = 1), summary)
        assertEquals(1, sent_writes.size)
        val (id, request) = sent_writes.single()
        assertEquals("sent-1", id)
        assertEquals(envelope_json, open_sealed(request.encrypted_envelope))
        assertEquals(sha256_hex_of_b64(item.encrypted_envelope!!), request.expected_envelope_sha256)
        assertTrue(progress.contains(ConversionProgressRequest(converted = 1, skipped = 0)))
        assertTrue(progress.contains(ConversionProgressRequest(sent_mail_done = true)))
        assertEquals(clock, scans["account-1"])
    }

    @Test
    fun leaves_items_without_the_inline_sentinel_untouched() = runBlocking {
        status = status_of(remaining_sent = 2)
        pages = mutableListOf(
            page(
                MailItem(id = "sent-1", encrypted_envelope = password_envelope(envelope_json), envelope_nonce = legacy_nonce()),
                MailItem(id = "sent-2", encrypted_envelope = null, envelope_nonce = null),
            ),
        )

        val summary = conversion.run("account-1")!!

        assertEquals(2, summary.checked)
        assertEquals(0, summary.converted)
        assertEquals(0, summary.skipped)
        assertTrue(sent_writes.isEmpty())
    }

    @Test
    fun counts_an_envelope_under_another_password_as_unreadable() = runBlocking {
        pages = mutableListOf(
            page(sentinel_item("sent-1", envelope = password_envelope(envelope_json, "other".toByteArray()))),
        )

        val summary = conversion.run("account-1")!!

        assertEquals(1, summary.unreadable)
        assertTrue(sent_writes.isEmpty())
        assertEquals(listOf(ConversionProgressRequest(converted = 0, skipped = 1)), progress)
        assertFalse(progress.contains(ConversionProgressRequest(sent_mail_done = true)))
    }

    @Test
    fun counts_a_rejected_write_as_failed_without_recording_completion() = runBlocking {
        sent_result = ConversionWriteResult.FAILED
        pages = mutableListOf(page(sentinel_item("sent-1")))

        val summary = conversion.run("account-1")!!

        assertEquals(1, summary.failed)
        assertFalse(progress.contains(ConversionProgressRequest(sent_mail_done = true)))
    }

    @Test
    fun counts_a_changed_source_as_skipped() = runBlocking {
        sent_result = ConversionWriteResult.SOURCE_CHANGED
        pages = mutableListOf(page(sentinel_item("sent-1")))

        val summary = conversion.run("account-1")!!

        assertEquals(1, summary.skipped)
        assertEquals(0, summary.converted)
    }

    @Test
    fun converts_all_three_attachment_meta_forms_and_skips_sealed_rows() = runBlocking {
        status = status_of(remaining_attachments = 3)
        val plain = b64(meta_json.toByteArray(Charsets.UTF_8))
        val old_meta = meta_json.replace("plan.pdf", "old.pdf")
        val armored = PgpEncryptor.encrypt_and_sign(
            old_meta,
            listOf(old_key.armored_public_key),
            old_key.armored_private_key,
            passphrase_text.toCharArray(),
        )!!
        val pgp = b64(armored.toByteArray(Charsets.UTF_8))
        val password = password_envelope(meta_json.replace("plan.pdf", "envelope.pdf"))
        attachments["sent-1"] = listOf(
            attachment("att-plain", plain),
            attachment("att-pgp", pgp),
            attachment("att-password", password),
            attachment("att-sealed", pgp, b64(ByteArray(12))),
        )
        pages = mutableListOf(page(MailItem(id = "sent-1", has_attachments = true)))

        val summary = conversion.run("account-1")!!

        assertEquals(3, summary.converted)
        assertEquals(0, summary.skipped + summary.unreadable + summary.failed)
        assertEquals(listOf("att-plain", "att-pgp", "att-password"), attachment_writes.map { it.first })
        val opened = attachment_writes.map { open_sealed(it.second.encrypted_meta)!! }
        assertEquals(meta_json, opened[0])
        assertEquals(
            listOf("plan.pdf", "old.pdf", "envelope.pdf"),
            opened.map { AccountDataConversion.parse_object(it)!!["filename"].toString().trim('"') },
        )
        assertEquals(
            listOf(plain, pgp, password).map { sha256_hex_of_b64(it) },
            attachment_writes.map { it.second.expected_meta_sha256 },
        )
        assertTrue(progress.contains(ConversionProgressRequest(sent_mail_done = true)))
    }

    @Test
    fun counts_meta_that_is_not_an_attachment_object_as_unreadable() = runBlocking {
        status = status_of(remaining_attachments = 1)
        attachments["sent-1"] = listOf(attachment("att-1", password_envelope("""{"filename":"x"}""")))
        pages = mutableListOf(page(MailItem(id = "sent-1", attachment_count = 1)))

        val summary = conversion.run("account-1")!!

        assertEquals(1, summary.unreadable)
        assertTrue(attachment_writes.isEmpty())
    }

    @Test
    fun counts_a_failed_attachment_listing_as_failed() = runBlocking {
        status = status_of(remaining_attachments = 1)
        pages = mutableListOf(page(MailItem(id = "sent-1", has_attachments = true)))

        val summary = conversion.run("account-1")!!

        assertEquals(1, summary.failed)
        assertFalse(progress.contains(ConversionProgressRequest(sent_mail_done = true)))
    }

    @Test
    fun records_completion_without_listing_when_nothing_remains() = runBlocking {
        status = status_of()

        val summary = conversion.run("account-1")

        assertEquals(AccountDataConversionSummary(), summary)
        assertEquals(listOf(ConversionProgressRequest(sent_mail_done = true)), progress)
        assertEquals(0, listing_cursors.size)
    }

    @Test
    fun converts_preferences_to_the_account_key() = runBlocking {
        status = status_of(sent_mail_done_at = "2026-09-01T00:00:00Z", preferences_done_at = null)
        has_preferences_key = true
        seal_legacy_preferences(version = 4)

        conversion.run("account-1")

        assertEquals(listOf(ConversionProgressRequest(preferences_done = true)), progress)
        val saved = preferences_saves.single()
        assertEquals(4, saved.expected_version)
        val reopened = AesGcm.decrypt(
            preferences_write_key,
            Base64.getDecoder().decode(saved.preferences_nonce),
            Base64.getDecoder().decode(saved.encrypted_preferences),
        )
        assertEquals(preferences_json, String(reopened, Charsets.UTF_8))
    }

    @Test
    fun records_preferences_already_on_the_account_key_without_writing() = runBlocking {
        status = status_of(sent_mail_done_at = "2026-09-01T00:00:00Z", preferences_done_at = null)
        has_preferences_key = true
        val nonce = ByteArray(12) { 4 }
        val sealed = AesGcm.encrypt(preferences_write_key, nonce, preferences_json.toByteArray(Charsets.UTF_8))
        stored_preferences = VersionedEncryptedPreferences(b64(sealed), b64(nonce), 2)

        conversion.run("account-1")

        assertEquals(listOf(ConversionProgressRequest(preferences_done = true)), progress)
        assertTrue(preferences_saves.isEmpty())
    }

    @Test
    fun does_not_record_preferences_after_a_version_conflict() = runBlocking {
        status = status_of(sent_mail_done_at = "2026-09-01T00:00:00Z", preferences_done_at = null)
        has_preferences_key = true
        preferences_save_result = PreferencesSaveResult.CONFLICT
        seal_legacy_preferences()

        conversion.run("account-1")

        assertEquals(1, preferences_saves.size)
        assertTrue(progress.isEmpty())
    }

    @Test
    fun never_overwrites_preferences_it_cannot_read() = runBlocking {
        status = status_of(sent_mail_done_at = "2026-09-01T00:00:00Z", preferences_done_at = null)
        has_preferences_key = true
        val nonce = ByteArray(12) { 5 }
        val sealed = AesGcm.encrypt(ByteArray(32) { 9 }, nonce, preferences_json.toByteArray(Charsets.UTF_8))
        stored_preferences = VersionedEncryptedPreferences(b64(sealed), b64(nonce), 1)

        conversion.run("account-1")

        assertTrue(preferences_saves.isEmpty())
        assertTrue(progress.isEmpty())
    }

    @Test
    fun skips_preferences_while_the_account_key_is_unavailable() = runBlocking {
        status = status_of(sent_mail_done_at = "2026-09-01T00:00:00Z", preferences_done_at = null)
        has_preferences_key = false
        seal_legacy_preferences()

        conversion.run("account-1")

        assertTrue(progress.isEmpty())
        assertTrue(preferences_saves.isEmpty())
    }

    @Test
    fun throttles_each_account_to_one_scan_per_interval() = runBlocking {
        pages = mutableListOf(
            page(MailItem(id = "sent-1")),
            page(MailItem(id = "sent-1")),
            page(MailItem(id = "sent-1")),
        )

        assertNotNull(conversion.run("account-1"))
        assertEquals(1, listing_cursors.size)

        clock += interval - 1
        assertNull(conversion.run("account-1"))
        assertEquals(1, listing_cursors.size)

        user_id = "account-2"
        assertNotNull(conversion.run("account-2"))
        assertEquals(2, listing_cursors.size)

        user_id = "account-1"
        clock += 2
        assertNotNull(conversion.run("account-1"))
        assertEquals(3, listing_cursors.size)
    }

    @Test
    fun stops_without_writing_when_the_account_changes_during_listing() = runBlocking {
        pages = mutableListOf(page(sentinel_item("sent-1")))
        listing_hook = { user_id = "account-2" }

        val summary = conversion.run("account-1")!!

        assertEquals(0, summary.checked)
        assertTrue(sent_writes.isEmpty())
        assertFalse(progress.contains(ConversionProgressRequest(sent_mail_done = true)))
        assertNull(scans["account-1"])
    }

    @Test
    fun stops_without_writing_when_the_identity_key_changes_during_listing() = runBlocking {
        pages = mutableListOf(page(sentinel_item("sent-1")))
        listing_hook = { identity_key = old_key.armored_private_key }

        val summary = conversion.run("account-1")!!

        assertEquals(0, summary.checked)
        assertTrue(sent_writes.isEmpty())
        assertNull(scans["account-1"])
    }

    @Test
    fun follows_the_cursor_across_pages() = runBlocking {
        status = status_of(remaining_sent = 2)
        pages = mutableListOf(
            page(sentinel_item("sent-1"), next_cursor = "cursor-2", has_more = true),
            page(sentinel_item("sent-2")),
        )

        val summary = conversion.run("account-1")!!

        assertEquals(2, summary.converted)
        assertEquals(listOf(null, "cursor-2"), listing_cursors.toList())
        assertEquals(listOf("sent-1", "sent-2"), sent_writes.map { it.first })
    }

    @Test
    fun stops_listing_once_the_remaining_counts_are_met() = runBlocking {
        pages = mutableListOf(
            page(sentinel_item("sent-1"), next_cursor = "cursor-2", has_more = true),
            page(sentinel_item("sent-2")),
        )

        val summary = conversion.run("account-1")!!

        assertEquals(1, summary.converted)
        assertEquals(1, listing_cursors.size)
    }

    @Test
    fun zeroes_every_passphrase_copy() = runBlocking {
        pages = mutableListOf(page(sentinel_item("sent-1")))

        conversion.run("account-1")

        assertTrue(handed_out_passphrases.isNotEmpty())
        assertTrue(handed_out_passphrases.all { copy -> copy.all { it == 0.toByte() } })
    }

    @Test
    fun runs_one_conversion_at_a_time() = runBlocking {
        pages = mutableListOf(page(sentinel_item("sent-1")), page(sentinel_item("sent-1")))
        val listing_started = CompletableDeferred<Unit>()
        val release = CompletableDeferred<Unit>()
        listing_hook = {
            listing_started.complete(Unit)
            release.await()
        }

        val first = async(Dispatchers.Default) { conversion.run("account-1") }
        listing_started.await()
        val second = conversion.run("account-1")
        release.complete(Unit)

        assertNull(second)
        assertNotNull(first.await())
        assertEquals(1, sent_writes.size)
    }
}
