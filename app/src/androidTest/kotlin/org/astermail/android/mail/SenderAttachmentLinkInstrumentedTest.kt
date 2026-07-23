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

import android.util.Base64
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import kotlinx.coroutines.runBlocking
import org.astermail.android.api.mail.AttachmentResponse
import org.astermail.android.api.mail.CreateAttachmentRequestBody
import org.astermail.android.api.mail.MailApi
import org.astermail.android.api.send.ExternalAttachmentPayload
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class SenderAttachmentLinkInstrumentedTest {

    private fun build_repo(mail_api: MailApi, passphrase: ByteArray?): MailRepository {
        val session_key_store = mockk<org.astermail.android.storage.SessionKeyStore>(relaxed = true)
        every { session_key_store.get_passphrase() } answers { passphrase?.copyOf() }
        every { session_key_store.get_identity_key() } returns "device_identity_key"
        every { session_key_store.get_legacy_keks() } returns null
        every { session_key_store.get_previous_keys() } returns null
        return MailRepository(
            mail_api = mail_api,
            send_api = mockk(relaxed = true),
            snooze_api = mockk(relaxed = true),
            labels_api = mockk(relaxed = true),
            session_key_store = session_key_store,
            scheduled_api = mockk(relaxed = true),
            ratchet_decryptor = mockk(relaxed = true),
            ratchet_encryptor = mockk(relaxed = true),
            ratchet_plaintext_cache = mockk(relaxed = true),
            pending_send_dao = mockk(relaxed = true),
            context = ApplicationProvider.getApplicationContext(),
        )
    }

    @Test
    fun sender_copy_attachment_round_trips_on_device() = runBlocking {
        val mail_api = mockk<MailApi>(relaxed = true)
        val captured = slot<CreateAttachmentRequestBody>()
        coEvery { mail_api.create_attachment("sent_1", capture(captured)) } answers {
            val body = captured.captured
            AttachmentResponse(
                id = "att_1",
                mail_item_id = "sent_1",
                encrypted_data = body.encrypted_data,
                data_nonce = body.data_nonce,
                encrypted_meta = body.encrypted_meta,
                meta_nonce = body.meta_nonce,
                size_bytes = body.encrypted_data.length.toLong(),
                seq_num = body.seq_num ?: 0,
            )
        }
        val repo = build_repo(mail_api, "correct horse battery staple".toByteArray(Charsets.UTF_8))

        val raw_bytes = ByteArray(13_107_200) { (it * 31 + 7).toByte() }
        val payload = ExternalAttachmentPayload(
            data = Base64.encodeToString(raw_bytes, Base64.NO_WRAP),
            filename = "book.epub",
            content_type = "application/epub+zip",
            size_bytes = raw_bytes.size.toLong(),
        )

        repo.link_sender_attachments("sent_1", listOf(payload))

        assertTrue("create_attachment must be called", captured.isCaptured)
        val body = captured.captured
        assertEquals(12, Base64.decode(body.data_nonce, Base64.DEFAULT).size)
        assertEquals(12, Base64.decode(body.meta_nonce, Base64.DEFAULT).size)

        val meta = repo.decrypt_attachment_meta(body.encrypted_meta, body.meta_nonce)
        assertNotNull("sent-copy meta must decrypt", meta)
        assertEquals("book.epub", meta!!.filename)
        assertEquals("application/epub+zip", meta.content_type)

        val decrypted = repo.decrypt_attachment_data(body.encrypted_data, body.data_nonce, meta.session_key)
        assertArrayEquals("12.5MB attachment bytes must round-trip", raw_bytes, decrypted)
    }
}
