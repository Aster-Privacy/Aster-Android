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

import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkStatic
import io.mockk.unmockkAll
import javax.crypto.Cipher
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.SecretKeySpec
import org.astermail.android.R
import org.astermail.android.storage.SessionKeyStore
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class AttachmentMetaSealedTest {

    private lateinit var context: android.content.Context
    private lateinit var session_key_store: SessionKeyStore
    private lateinit var repo: MailRepository
    private lateinit var mail_api: org.astermail.android.api.mail.MailApi
    private lateinit var own_authored_prefs: org.astermail.android.storage.fake_prefs

    private val placeholder = "Attachment"

    @Before
    fun setup() {
        mockkStatic(android.util.Base64::class)
        every { android.util.Base64.decode(any<String>(), any()) } answers {
            java.util.Base64.getDecoder().decode(firstArg<String>())
        }
        every { android.util.Base64.encodeToString(any(), any()) } answers {
            java.util.Base64.getEncoder().encodeToString(firstArg<ByteArray>())
        }
        InboundAttachmentKeyStore.clear()

        context = mockk(relaxed = true)
        every { context.getString(R.string.attachment_unnamed) } returns placeholder
        session_key_store = mockk(relaxed = true)
        every { session_key_store.get_identity_key() } returns "test_identity_key"
        every { session_key_store.get_passphrase() } returns null
        every { session_key_store.get_user_email() } returns "me@astermail.org"
        every { session_key_store.has_ratchet_keys() } returns false
        every { session_key_store.get_user_id() } returns "user-a"
        mail_api = mockk(relaxed = true)
        own_authored_prefs = org.astermail.android.storage.fake_prefs()
        every { context.getSharedPreferences("aster_own_authored", any()) } returns own_authored_prefs

        repo = build_repo()
    }

    private fun build_repo(): MailRepository =
        MailRepository(
            mail_api = mail_api,
            send_api = mockk(relaxed = true),
            snooze_api = mockk(relaxed = true),
            labels_api = mockk(relaxed = true),
            keys_api = mockk(relaxed = true),
            session_key_store = session_key_store,
            scheduled_api = mockk(relaxed = true),
            ratchet_decryptor = mockk(relaxed = true),
            ratchet_encryptor = mockk(relaxed = true),
            ratchet_plaintext_cache = mockk(relaxed = true),
            system_folder_bootstrap = mockk(relaxed = true),
            pending_send_dao_provider = dagger.Lazy { mockk(relaxed = true) },
            message_body_dao_provider = dagger.Lazy { mockk(relaxed = true) },
            thread_snapshot_dao_provider = dagger.Lazy { mockk(relaxed = true) },
            context = context,
            auth_repository = mockk(relaxed = true),
        )

    @After
    fun teardown() {
        InboundAttachmentKeyStore.clear()
        unmockkAll()
    }

    private fun b64(bytes: ByteArray): String =
        java.util.Base64.getEncoder().encodeToString(bytes)

    private fun seal(plaintext: String, key: ByteArray, nonce: ByteArray): String {
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.ENCRYPT_MODE, SecretKeySpec(key, "AES"), GCMParameterSpec(128, nonce))
        return b64(cipher.doFinal(plaintext.toByteArray(Charsets.UTF_8)))
    }

    private val session_key = ByteArray(32) { (it + 1).toByte() }
    private val meta_nonce = ByteArray(12) { (it + 40).toByte() }

    @Test
    fun sealed_metadata_round_trips_with_the_envelope_session_key() {
        val sealed_meta = seal(
            """{"filename":"payslip.pdf","content_type":"application/pdf","content_id":"cid-42"}""",
            session_key,
            meta_nonce,
        )
        InboundAttachmentKeyStore.register_from_envelope_json(
            "item-1",
            """{"attachment_keys":[{"seq":0,"key":"${b64(session_key)}"}]}""",
        )

        val meta = repo.decrypt_attachment_meta(sealed_meta, b64(meta_nonce), "item-1", 0, 11L)

        assertFalse(meta.is_placeholder)
        assertEquals("payslip.pdf", meta.filename)
        assertEquals("application/pdf", meta.content_type)
        assertEquals("cid-42", meta.content_id)
        assertEquals(b64(session_key), meta.session_key)
        assertEquals(11L, meta.size_bytes)
    }

    @Test
    fun sealed_tag_is_not_split_off_by_hand() {
        val plaintext = """{"filename":"a.txt","content_type":"text/plain"}"""
        val sealed_meta = seal(plaintext, session_key, meta_nonce)
        val raw = java.util.Base64.getDecoder().decode(sealed_meta)

        assertEquals(plaintext.toByteArray(Charsets.UTF_8).size + 16, raw.size)

        val meta = MailRepository.decrypt_sealed_attachment_meta(
            sealed_meta,
            meta_nonce,
            b64(session_key),
        )

        assertEquals("a.txt", meta?.filename)
    }

    @Test
    fun envelope_fields_win_over_the_sealed_row() {
        val sealed_meta = seal(
            """{"filename":"row.pdf","content_type":"application/pdf"}""",
            session_key,
            meta_nonce,
        )
        InboundAttachmentKeyStore.register_from_envelope_json(
            "item-1",
            """
            {"attachment_keys":[{"seq":0,"key":"${b64(session_key)}",
            "filename":"envelope.pdf","content_type":"application/pdf",
            "content_id":"cid-7","size":4096}]}
            """.trimIndent().replace("\n", ""),
        )

        val meta = repo.decrypt_attachment_meta(sealed_meta, b64(meta_nonce), "item-1", 0, 11L)

        assertEquals("envelope.pdf", meta.filename)
        assertEquals("cid-7", meta.content_id)
        assertEquals(4096L, meta.size_bytes)
    }

    @Test
    fun received_zero_nonce_row_in_plain_json_still_downloads_as_unverified() {
        val plaintext = """{"filename":"legacy.txt","content_type":"text/plain","session_key":"c2VydmVy"}"""
        val encoded = b64(plaintext.toByteArray(Charsets.UTF_8))

        val meta = repo.decrypt_attachment_meta(encoded, b64(ByteArray(12)), "item-2", 0, 7L)

        assertFalse(meta.is_placeholder)
        assertEquals("legacy.txt", meta.filename)
        assertEquals("c2VydmVy", meta.session_key)
        assertEquals(7L, meta.size_bytes)
        assertTrue("row fields from another sender are never verified", meta.is_unverified)
    }

    @Test
    fun received_empty_nonce_row_in_plain_json_still_downloads() {
        val plaintext = """{"filename":"empty.txt","content_type":"text/plain","session_key":"c2VydmVy"}"""
        val encoded = b64(plaintext.toByteArray(Charsets.UTF_8))

        val from_blank = repo.decrypt_attachment_meta(encoded, "", "item-2", 0, 1L)
        val from_null = repo.decrypt_attachment_meta(encoded, null, "item-2", 0, 1L)

        assertEquals("empty.txt", from_blank.filename)
        assertEquals("c2VydmVy", from_blank.session_key)
        assertTrue(from_blank.is_unverified)
        assertEquals("empty.txt", from_null.filename)
        assertEquals("c2VydmVy", from_null.session_key)
    }

    @Test
    fun received_plain_row_takes_name_type_and_key_from_the_verified_manifest() {
        val plaintext = """{"filename":"row.exe","content_type":"application/x-msdownload","session_key":"c2VydmVy"}"""
        InboundAttachmentKeyStore.register_from_envelope_json(
            "item-mf",
            """{"attachment_keys":[{"seq":0,"key":"${b64(session_key)}","filename":"manifest.pdf","content_type":"application/pdf"}]}""",
        )

        val meta = repo.decrypt_attachment_meta(
            b64(plaintext.toByteArray(Charsets.UTF_8)),
            b64(ByteArray(12)),
            "item-mf",
            0,
            9L,
        )

        assertEquals("manifest.pdf", meta.filename)
        assertEquals("application/pdf", meta.content_type)
        assertEquals(b64(session_key), meta.session_key)
        assertFalse(meta.is_unverified)
    }

    @Test
    fun sent_row_in_plain_json_is_refused() {
        val plaintext = """{"filename":"forged.txt","content_type":"text/plain","session_key":"c2VydmVy"}"""
        val encoded = b64(plaintext.toByteArray(Charsets.UTF_8))
        InboundAttachmentKeyStore.note_own_authored("sent-1")

        val under_zero = repo.decrypt_attachment_meta(encoded, b64(ByteArray(12)), "sent-1", 0, 7L)
        val under_empty = repo.decrypt_attachment_meta(encoded, "", "sent-1", 0, 7L)

        assertTrue("plain JSON on an own sent row must not be trusted", under_zero.is_placeholder)
        assertEquals("", under_zero.session_key)
        assertTrue("plain JSON on an own sent row must not be trusted", under_empty.is_placeholder)
        assertEquals("", under_empty.session_key)
    }

    @Test
    fun a_thread_message_of_sent_type_makes_its_rows_strict() {
        val plaintext = """{"filename":"forged.txt","content_type":"text/plain","session_key":"c2VydmVy"}"""
        val encoded = b64(plaintext.toByteArray(Charsets.UTF_8))
        val sent = org.astermail.android.api.mail.ThreadMessageItem(
            id = "sent-thread",
            item_type = "sent",
            message_ts = "2026-04-26T10:00:00Z",
            created_at = "2026-04-26T10:00:00Z",
        )
        val received = sent.copy(id = "received-thread", item_type = "received")

        kotlinx.coroutines.runBlocking {
            repo.decrypt_single_thread_message(sent)
            repo.decrypt_single_thread_message(received)
        }

        val sent_meta = repo.decrypt_attachment_meta(encoded, b64(ByteArray(12)), "sent-thread", 0, 7L)
        val received_meta = repo.decrypt_attachment_meta(encoded, b64(ByteArray(12)), "received-thread", 0, 7L)

        assertTrue("a sent message row must not be read from plain JSON", sent_meta.is_placeholder)
        assertEquals("forged.txt", received_meta.filename)
        assertTrue(received_meta.is_unverified)
    }

    @Test
    fun a_sent_row_stays_strict_after_a_cold_start() {
        val plaintext = """{"filename":"forged.txt","content_type":"text/plain","session_key":"c2VydmVy"}"""
        val encoded = b64(plaintext.toByteArray(Charsets.UTF_8))
        val sent = org.astermail.android.api.mail.ThreadMessageItem(
            id = "sent-cold",
            item_type = "sent",
            message_ts = "2026-04-26T10:00:00Z",
            created_at = "2026-04-26T10:00:00Z",
        )
        kotlinx.coroutines.runBlocking { repo.decrypt_single_thread_message(sent) }

        InboundAttachmentKeyStore.clear()
        val restarted = build_repo()
        val meta = restarted.decrypt_attachment_meta(encoded, b64(ByteArray(12)), "sent-cold", 0, 7L)

        assertTrue("a sent row seen before a restart must stay strict", meta.is_placeholder)
        assertEquals("", meta.session_key)
    }

    @Test
    fun a_sent_row_relabeled_as_received_after_eviction_stays_strict() {
        val plaintext = """{"filename":"forged.txt","content_type":"text/plain","session_key":"c2VydmVy"}"""
        val encoded = b64(plaintext.toByteArray(Charsets.UTF_8))
        val sent = org.astermail.android.api.mail.ThreadMessageItem(
            id = "sent-relabel",
            item_type = "sent",
            message_ts = "2026-04-26T10:00:00Z",
            created_at = "2026-04-26T10:00:00Z",
        )
        kotlinx.coroutines.runBlocking { repo.decrypt_single_thread_message(sent) }

        InboundAttachmentKeyStore.clear()
        kotlinx.coroutines.runBlocking {
            repo.decrypt_single_thread_message(sent.copy(item_type = "received"))
        }
        val meta = repo.decrypt_attachment_meta(encoded, b64(ByteArray(12)), "sent-relabel", 0, 7L)

        assertTrue("a relabel must not make an own sent row lenient", meta.is_placeholder)
        assertEquals("", meta.session_key)
    }

    @Test
    fun forgetting_an_account_drops_only_its_own_authored_items() {
        val forget = MailRepository::class.java.methods.firstOrNull { it.name == "forget_own_authored" }
        assertTrue("the repository must offer a way to forget an account's own authored items", forget != null)
        own_authored_prefs.edit().putLong("user-b:other-id", 1L).apply()
        val sent = org.astermail.android.api.mail.ThreadMessageItem(
            id = "forget-me",
            item_type = "sent",
            message_ts = "2026-04-26T10:00:00Z",
            created_at = "2026-04-26T10:00:00Z",
        )
        kotlinx.coroutines.runBlocking { repo.decrypt_single_thread_message(sent) }
        assertTrue(own_authored_prefs.contains("user-a:forget-me"))

        forget!!.invoke(repo, "user-a")

        assertFalse(own_authored_prefs.contains("user-a:forget-me"))
        assertTrue(own_authored_prefs.contains("user-b:other-id"))
    }

    @Test
    fun deleting_the_account_forgets_its_own_authored_items() {
        val source = java.io.File("src/main/kotlin/org/astermail/android/auth/AuthRepository.kt")
            .readText()
            .replace("\r\n", "\n")
        val body = source.substringAfter("suspend fun delete_account(").substringBefore("\n    }\n")
        assertTrue(
            "account deletion must forget the account's own authored items",
            Regex("""mail_repository\.forget_own_authored\(it\)""").containsMatchIn(body),
        )
    }

    @Test
    fun an_item_seen_after_the_session_is_cleared_is_not_remembered() {
        every { session_key_store.get_user_id() } returns null
        val sent = org.astermail.android.api.mail.ThreadMessageItem(
            id = "late-item",
            item_type = "sent",
            message_ts = "2026-04-26T10:00:00Z",
            created_at = "2026-04-26T10:00:00Z",
        )

        kotlinx.coroutines.runBlocking { repo.decrypt_single_thread_message(sent) }

        assertFalse(
            "an item seen with no signed-in account must not be stored",
            own_authored_prefs.all.keys.any { it.endsWith(":late-item") },
        )
    }

    @Test
    fun forgetting_an_account_also_drops_entries_written_without_an_account() {
        val forget = MailRepository::class.java.methods.first { it.name == "forget_own_authored" }
        own_authored_prefs.edit().putLong("anonymous:stray-id", 1L).apply()
        own_authored_prefs.edit().putLong("user-b:other-id", 1L).apply()

        forget.invoke(repo, "user-a")

        assertFalse("entries written without an account must be dropped", own_authored_prefs.contains("anonymous:stray-id"))
        assertTrue(own_authored_prefs.contains("user-b:other-id"))
    }

    @Test
    fun signing_out_forgets_the_account_own_authored_items() {
        val source = java.io.File("src/main/kotlin/org/astermail/android/auth/AuthRepository.kt")
            .readText()
            .replace("\r\n", "\n")
        val body = source.substringAfter("private suspend fun sign_out_internal(").substringBefore("\n    }\n")
        assertTrue(
            "sign-out and account removal must forget the account's own authored items",
            Regex("""mail_repository\.forget_own_authored\(it\)""").containsMatchIn(body),
        )
    }

    @Test
    fun the_attachment_chip_labels_unverified_attachments() {
        val source = java.io.File("src/main/kotlin/org/astermail/android/ui/mail/mail_detail_screen.kt")
            .readText()
            .replace("\r\n", "\n")
        val chip = source.substringAfter("private fun attachment_chip(").substringBefore("\n}\n")
        assertTrue(
            "the attachment chip must label unverified attachments",
            Regex("""if \(attachment\.is_unverified\) \{\s*Text\(\s*text = stringResource\(R\.string\.attachment_unverified\)""")
                .containsMatchIn(chip),
        )
    }

    @Test
    fun the_attachment_preview_and_options_label_unverified_attachments() {
        val source = java.io.File("src/main/kotlin/org/astermail/android/ui/mail/mail_detail_screen.kt")
            .readText()
            .replace("\r\n", "\n")
        val label = Regex("""if \(attachment\.is_unverified\) \{\s*Text\(\s*text = stringResource\(R\.string\.attachment_unverified\)""")
        val preview = source.substringAfter("private fun attachment_preview_dialog(").substringBefore("\n}\n")
        val options = source.substringAfter("private fun attachment_options_sheet(").substringBefore("\n}\n")
        assertTrue("the attachment preview must label unverified attachments", label.containsMatchIn(preview))
        assertTrue("the attachment options must label unverified attachments", label.containsMatchIn(options))
    }

    @Test
    fun every_locale_has_the_unverified_attachment_label() {
        val locales = java.io.File("src/main/res").listFiles().orEmpty()
            .filter { java.io.File(it, "strings.xml").isFile }
        assertEquals(16, locales.size)
        val missing = locales.filterNot { dir ->
            Regex("""<string name="attachment_unverified">[^<]+</string>""")
                .containsMatchIn(java.io.File(dir, "strings.xml").readText())
        }.map { it.name }
        assertTrue("every locale needs the unverified attachment label, missing in $missing", missing.isEmpty())
    }

    private fun constant_sealed_meta(json: String): String {
        val constant = org.astermail.android.crypto.StorageWriteKey.public_mail_kek_raws().first()
        val salt = ByteArray(16) { (it + 90).toByte() }
        val iv = ByteArray(12) { (it + 120).toByte() }
        val sealed = java.util.Base64.getDecoder().decode(seal(json, constant, iv))
        return b64(salt + iv + sealed)
    }

    @Test
    fun received_meta_that_opens_under_a_public_constant_is_unverified() {
        val encoded = constant_sealed_meta(pgp_meta_json)

        val meta = repo.decrypt_attachment_meta(encoded, b64(ByteArray(12)), "item-const", 0, 5L)

        assertEquals("invoice.pdf", meta.filename)
        assertTrue("meta opened under a public constant must be unverified", meta.is_unverified)
    }

    @Test
    fun own_meta_that_opens_under_a_public_constant_is_refused() {
        val encoded = constant_sealed_meta(pgp_meta_json)
        InboundAttachmentKeyStore.note_own_authored("sent-const")

        val meta = repo.decrypt_attachment_meta(encoded, b64(ByteArray(12)), "sent-const", 0, 5L)

        assertTrue("constant meta on an own row must not be trusted", meta.is_placeholder)
        assertEquals("", meta.session_key)
    }

    @Test
    fun parsed_meta_keeps_the_unverified_mark() {
        val marked = MailRepository.parse_attachment_meta_json(
            """{"filename":"a.txt","content_type":"text/plain","legacy_unverified":true}""".toByteArray(Charsets.UTF_8),
        )

        assertTrue(marked!!.is_unverified)
    }

    @Test
    fun own_authored_items_are_kept_per_account() {
        val plaintext = """{"filename":"plain.txt","content_type":"text/plain","session_key":"c2VydmVy"}"""
        val encoded = b64(plaintext.toByteArray(Charsets.UTF_8))
        val sent = org.astermail.android.api.mail.ThreadMessageItem(
            id = "shared-id",
            item_type = "sent",
            message_ts = "2026-04-26T10:00:00Z",
            created_at = "2026-04-26T10:00:00Z",
        )
        kotlinx.coroutines.runBlocking { repo.decrypt_single_thread_message(sent) }

        InboundAttachmentKeyStore.clear()
        every { session_key_store.get_user_id() } returns "user-b"
        val other_account = build_repo()
        val meta = other_account.decrypt_attachment_meta(encoded, b64(ByteArray(12)), "shared-id", 0, 7L)

        assertEquals("plain.txt", meta.filename)
        assertTrue(meta.is_unverified)
    }

    @Test
    fun the_attachment_model_carries_the_unverified_flag() {
        val plaintext = """{"filename":"legacy.txt","content_type":"text/plain","session_key":"c2VydmVy"}"""
        val response = org.astermail.android.api.mail.AttachmentResponse(
            id = "att-1",
            mail_item_id = "item-ui",
            encrypted_data = "",
            data_nonce = "",
            encrypted_meta = b64(plaintext.toByteArray(Charsets.UTF_8)),
            meta_nonce = b64(ByteArray(12)),
            size_bytes = 7L,
        )
        io.mockk.coEvery { mail_api.get_attachment("att-1") } returns response

        val attachment = kotlinx.coroutines.runBlocking { repo.fetch_attachment("att-1") }.getOrThrow()

        assertEquals("legacy.txt", attachment.filename)
        assertTrue("the UI model must know the row fields are unverified", attachment.is_unverified)
    }

    private val own_pgp_key by lazy {
        org.astermail.android.crypto.PgpKeyGenerator.generate("Me", "me@astermail.org", "pw".toCharArray())
    }

    private fun use_own_pgp_key() {
        every { session_key_store.get_identity_key() } returns own_pgp_key.armored_private_key
        every { session_key_store.get_previous_keys() } returns emptyList()
        every { session_key_store.get_passphrase() } answers { "pw".toByteArray(Charsets.UTF_8) }
    }

    private val pgp_meta_json = """{"filename":"invoice.pdf","content_type":"application/pdf","session_key":"c2VydmVy"}"""

    @Test
    fun received_unsigned_pgp_row_from_another_sender_still_downloads() {
        use_own_pgp_key()
        val armored = org.astermail.android.crypto.PgpEncryptor.encrypt_to_keys(
            pgp_meta_json,
            listOf(own_pgp_key.armored_public_key),
        )!!

        val meta = repo.decrypt_attachment_meta(b64(armored.toByteArray(Charsets.UTF_8)), "", "item-u", 0, 5L)

        assertFalse(meta.is_placeholder)
        assertEquals("invoice.pdf", meta.filename)
        assertEquals("c2VydmVy", meta.session_key)
        assertTrue("unsigned PGP from another sender is never verified", meta.is_unverified)
    }

    @Test
    fun sent_row_in_unsigned_pgp_is_refused() {
        use_own_pgp_key()
        InboundAttachmentKeyStore.note_own_authored("sent-u")
        val armored = org.astermail.android.crypto.PgpEncryptor.encrypt_to_keys(
            pgp_meta_json,
            listOf(own_pgp_key.armored_public_key),
        )!!

        val meta = repo.decrypt_attachment_meta(b64(armored.toByteArray(Charsets.UTF_8)), "", "sent-u", 0, 5L)

        assertTrue("unsigned PGP on an own sent row must not be trusted", meta.is_placeholder)
        assertEquals("", meta.session_key)
    }

    @Test
    fun pgp_row_meta_signed_by_the_owner_is_read() {
        use_own_pgp_key()
        val armored = org.astermail.android.crypto.PgpEncryptor.encrypt_and_sign(
            pgp_meta_json,
            listOf(own_pgp_key.armored_public_key),
            own_pgp_key.armored_private_key,
            "pw".toCharArray(),
        )!!

        InboundAttachmentKeyStore.note_own_authored("item-s")

        val meta = repo.decrypt_attachment_meta(b64(armored.toByteArray(Charsets.UTF_8)), "", "item-s", 0, 5L)

        assertFalse(meta.is_placeholder)
        assertEquals("invoice.pdf", meta.filename)
        assertEquals("c2VydmVy", meta.session_key)
        assertFalse(meta.is_unverified)
    }

    @Test
    fun a_nonce_that_is_non_zero_only_in_its_last_byte_is_sealed() {
        val nonce = ByteArray(12).also { it[11] = 1 }
        val sealed_meta = seal(
            """{"filename":"tail.pdf","content_type":"application/pdf"}""",
            session_key,
            nonce,
        )
        InboundAttachmentKeyStore.register("item-3", 0, b64(session_key))

        assertTrue(MailRepository.is_sealed_meta_nonce(nonce))
        assertFalse(MailRepository.is_sealed_meta_nonce(ByteArray(12)))
        assertFalse(MailRepository.is_sealed_meta_nonce(ByteArray(0)))
        assertFalse(MailRepository.is_sealed_meta_nonce(null))

        val meta = repo.decrypt_attachment_meta(sealed_meta, b64(nonce), "item-3", 0, 3L)

        assertEquals("tail.pdf", meta.filename)
    }

    @Test
    fun sealed_json_is_never_read_as_plaintext_when_the_nonce_is_zero() {
        val sealed_meta = seal(
            """{"filename":"secret.pdf","content_type":"application/pdf"}""",
            session_key,
            meta_nonce,
        )
        InboundAttachmentKeyStore.register("item-4", 0, b64(session_key))

        val meta = repo.decrypt_attachment_meta(sealed_meta, b64(ByteArray(12)), "item-4", 0, 5L)

        assertTrue(meta.is_placeholder)
        assertEquals(placeholder, meta.filename)
    }

    @Test
    fun a_wrong_key_falls_back_to_the_placeholder_without_crashing() {
        val sealed_meta = seal(
            """{"filename":"payslip.pdf","content_type":"application/pdf"}""",
            session_key,
            meta_nonce,
        )
        InboundAttachmentKeyStore.register("item-5", 0, b64(ByteArray(32) { 9 }))

        val meta = repo.decrypt_attachment_meta(sealed_meta, b64(meta_nonce), "item-5", 0, 12L)

        assertTrue(meta.is_placeholder)
        assertEquals(placeholder, meta.filename)
        assertEquals(MailRepository.DEFAULT_ATTACHMENT_CONTENT_TYPE, meta.content_type)
        assertEquals(12L, meta.size_bytes)
        assertFalse(meta.filename.contains("payslip"))
    }

    @Test
    fun a_sealed_row_without_any_envelope_key_shows_the_placeholder_and_the_row_size() {
        val sealed_meta = seal(
            """{"filename":"payslip.pdf","content_type":"application/pdf"}""",
            session_key,
            meta_nonce,
        )

        val meta = repo.decrypt_attachment_meta(sealed_meta, b64(meta_nonce), "item-6", 0, 990L)

        assertTrue(meta.is_placeholder)
        assertEquals(placeholder, meta.filename)
        assertEquals("", meta.session_key)
        assertEquals(990L, meta.size_bytes)
    }

    @Test
    fun entries_are_matched_by_seq_not_by_array_position() {
        InboundAttachmentKeyStore.register_from_envelope_json(
            "item-7",
            """
            {"attachment_keys":[
            {"seq":3,"key":"${b64(ByteArray(32) { 3 })}","filename":"third.pdf","content_type":"application/pdf","size":300},
            {"seq":0,"key":"${b64(ByteArray(32) { 0 })}","filename":"first.pdf","content_type":"application/pdf","size":100},
            {"seq":1,"key":"${b64(ByteArray(32) { 1 })}","filename":"second.pdf","content_type":"application/pdf","size":200}]}
            """.trimIndent().replace("\n", ""),
        )

        assertEquals("first.pdf", InboundAttachmentKeyStore.entry("item-7", 0)?.filename)
        assertEquals("second.pdf", InboundAttachmentKeyStore.entry("item-7", 1)?.filename)
        assertEquals("third.pdf", InboundAttachmentKeyStore.entry("item-7", 3)?.filename)
        assertNull(InboundAttachmentKeyStore.entry("item-7", 2))
        assertEquals(300L, InboundAttachmentKeyStore.entry("item-7", 3)?.size)
    }

    @Test
    fun a_legacy_entry_without_fields_falls_back_to_the_sealed_row() {
        val sealed_meta = seal(
            """{"filename":"row.pdf","content_type":"application/pdf","content_id":"cid-9"}""",
            session_key,
            meta_nonce,
        )
        InboundAttachmentKeyStore.register_from_envelope_json(
            "item-8",
            """{"attachment_keys":[{"seq":0,"key":"${b64(session_key)}"}]}""",
        )

        val meta = repo.decrypt_attachment_meta(sealed_meta, b64(meta_nonce), "item-8", 0, 64L)

        assertEquals("row.pdf", meta.filename)
        assertEquals("cid-9", meta.content_id)
        assertEquals(64L, meta.size_bytes)
    }

    @Test
    fun a_missing_content_type_falls_back_to_the_default_type() {
        val sealed_meta = seal("""{"filename":"typeless.bin"}""", session_key, meta_nonce)
        InboundAttachmentKeyStore.register("item-9", 0, b64(session_key))

        val meta = repo.decrypt_attachment_meta(sealed_meta, b64(meta_nonce), "item-9", 0, 8L)

        assertEquals("typeless.bin", meta.filename)
        assertEquals(MailRepository.DEFAULT_ATTACHMENT_CONTENT_TYPE, meta.content_type)
    }

    @Test
    fun a_missing_content_id_is_not_synthesized() {
        val sealed_meta = seal(
            """{"filename":"plain.pdf","content_type":"application/pdf"}""",
            session_key,
            meta_nonce,
        )
        InboundAttachmentKeyStore.register_from_envelope_json(
            "item-10",
            """{"attachment_keys":[{"seq":0,"key":"${b64(session_key)}","filename":"plain.pdf","content_type":"application/pdf","size":12}]}""",
        )

        val meta = repo.decrypt_attachment_meta(sealed_meta, b64(meta_nonce), "item-10", 0, 12L)

        assertNull(meta.content_id)
    }

    @Test
    fun a_missing_envelope_size_falls_back_to_the_row_size() {
        val sealed_meta = seal(
            """{"filename":"sized.pdf","content_type":"application/pdf"}""",
            session_key,
            meta_nonce,
        )
        InboundAttachmentKeyStore.register_from_envelope_json(
            "item-11",
            """{"attachment_keys":[{"seq":0,"key":"${b64(session_key)}","filename":"sized.pdf","content_type":"application/pdf"}]}""",
        )

        val meta = repo.decrypt_attachment_meta(sealed_meta, b64(meta_nonce), "item-11", 0, 4242L)

        assertEquals(4242L, meta.size_bytes)
    }

    @Test
    fun opens_the_sealed_meta_vector_written_by_the_server() {
        val stream = checkNotNull(
            javaClass.classLoader?.getResourceAsStream("sealed_attachment_meta_vector.json"),
        )
        val vector = org.json.JSONObject(stream.bufferedReader().use { it.readText() })
        val key_b64 = vector.getString("session_key_b64")
        val nonce = java.util.Base64.getDecoder().decode(vector.getString("meta_nonce_b64"))

        assertTrue(MailRepository.is_sealed_meta_nonce(nonce))

        InboundAttachmentKeyStore.register("item-vector", 0, key_b64)

        val meta = repo.decrypt_attachment_meta(
            vector.getString("sealed_meta_b64"),
            b64(nonce),
            "item-vector",
            0,
            null,
        )

        assertFalse(meta.is_placeholder)
        assertEquals("quarterly report.pdf", meta.filename)
        assertEquals("application/pdf", meta.content_type)
        assertEquals("cid-7", meta.content_id)
        assertEquals(key_b64, meta.session_key)
    }

    private val vector_data_kek = ByteArray(32) { it.toByte() }
    private val vector_legacy_kek = ByteArray(32) { (it + 32).toByte() }
    private val vector_plaintext = """{"subject":"Vector test","n":7}"""
    private val vector_current =
        "oKGio6SlpqeoqaqrrK2ur8DBwsPExcbHyMnKyxmUrphDVc4W9hq0u8Q/kCWAkTj6oNg/L8FnTtfoYrei0cSkxxjeICkSF8UMMji/"
    private val vector_legacy_only =
        "oKGio6SlpqeoqaqrrK2ur8DBwsPExcbHyMnKywLFIOOJnavHPVGWAESVDuvrt2eV3125NPGXHdSgj9M13jpUGS2PQGNB/A8W43oA"

    private fun use_keks(data_kek: ByteArray?, legacy: List<ByteArray> = emptyList()) {
        every { session_key_store.get_data_kek() } answers { data_kek?.copyOf() }
        every { session_key_store.get_decrypt_keks() } returns legacy.map(::b64)
    }

    @Test
    fun the_web_marker_vector_opens_under_the_shared_meta_marker() {
        val marker = java.util.Base64.getDecoder().decode("AgAAAAAAAAAAAAAA")

        assertTrue(LevelTwoEnvelope.is_meta_marker(marker))
        assertTrue(MailRepository.is_sealed_meta_nonce(marker))
        assertFalse(AccountDataConversion.is_legacy_meta_nonce("AgAAAAAAAAAAAAAA"))
        assertEquals(
            vector_plaintext,
            LevelTwoEnvelope.open_b64(vector_current, listOf(vector_data_kek))?.toString(Charsets.UTF_8),
        )
        assertEquals(
            vector_plaintext,
            LevelTwoEnvelope.open_b64(vector_legacy_only, listOf(vector_data_kek, vector_legacy_kek))
                ?.toString(Charsets.UTF_8),
        )
    }

    @Test
    fun a_v2_meta_under_the_shared_marker_opens_through_the_reader() {
        use_keks(vector_data_kek)
        val sealed_meta = LevelTwoEnvelope.seal_b64(
            """{"filename":"marker.pdf","content_type":"application/pdf"}""".toByteArray(Charsets.UTF_8),
            vector_data_kek,
        )

        val meta = repo.decrypt_attachment_meta(sealed_meta, "AgAAAAAAAAAAAAAA", "item-m", 0, 21L)

        assertFalse(meta.is_placeholder)
        assertEquals("marker.pdf", meta.filename)
        assertEquals("application/pdf", meta.content_type)
    }

    @Test
    fun a_v2_meta_under_a_zero_or_random_nonce_still_opens() {
        use_keks(vector_data_kek)
        val sealed_meta = LevelTwoEnvelope.seal_b64(
            """{"filename":"either.pdf","content_type":"application/pdf"}""".toByteArray(Charsets.UTF_8),
            vector_data_kek,
        )

        val under_random = repo.decrypt_attachment_meta(sealed_meta, b64(meta_nonce), "item-r", 0, 1L)
        val under_zero = repo.decrypt_attachment_meta(sealed_meta, b64(ByteArray(12)), "item-z", 0, 1L)

        assertEquals("either.pdf", under_random.filename)
        assertEquals("either.pdf", under_zero.filename)
    }

    @Test
    fun a_raw_meta_under_the_data_kek_and_its_stored_nonce_opens() {
        use_keks(vector_data_kek)
        val sealed_meta = seal(
            """{"filename":"raw.pdf","content_type":"application/pdf"}""",
            vector_data_kek,
            meta_nonce,
        )

        val meta = repo.decrypt_attachment_meta(sealed_meta, b64(meta_nonce), "item-f6", 0, 30L)

        assertFalse(meta.is_placeholder)
        assertEquals("raw.pdf", meta.filename)
        assertEquals(30L, meta.size_bytes)
    }

    @Test
    fun a_raw_meta_under_a_legacy_kek_opens() {
        use_keks(vector_data_kek, listOf(vector_legacy_kek))
        val sealed_meta = seal(
            """{"filename":"old-kek.pdf","content_type":"application/pdf"}""",
            vector_legacy_kek,
            meta_nonce,
        )

        val meta = repo.decrypt_attachment_meta(sealed_meta, b64(meta_nonce), "item-f6l", 0, 4L)

        assertEquals("old-kek.pdf", meta.filename)
    }

    @Test
    fun a_raw_meta_under_an_unknown_key_stays_a_placeholder() {
        use_keks(vector_data_kek)
        val sealed_meta = seal(
            """{"filename":"hidden.pdf","content_type":"application/pdf"}""",
            ByteArray(32) { 5 },
            meta_nonce,
        )

        val meta = repo.decrypt_attachment_meta(sealed_meta, b64(meta_nonce), "item-f6x", 0, 4L)

        assertTrue(meta.is_placeholder)
        assertFalse(meta.filename.contains("hidden"))
    }

    @Test
    fun garbage_metadata_never_surfaces_ciphertext_as_a_name() {
        val garbage = b64(ByteArray(64) { (it * 7).toByte() })

        val meta = repo.decrypt_attachment_meta(garbage, b64(meta_nonce), "item-12", 0, 64L)

        assertTrue(meta.is_placeholder)
        assertEquals(placeholder, meta.filename)
    }
}
