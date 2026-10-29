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

import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import org.astermail.android.ui.mail.MessageAttachment
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.concurrent.atomic.AtomicInteger

class AttachmentPrefetchTest {

    private val mb = 1024L * 1024

    private fun att(id: String, size: Long) =
        MessageAttachment(id = id, filename = "$id.bin", content_type = "application/octet-stream", size_bytes = size)

    @Test
    fun skips_messages_with_a_large_attachment() {
        val metas = mapOf(
            "small" to listOf(att("a", 2 * mb), att("b", 3 * mb)),
            "huge" to listOf(att("c", 1 * mb), att("d", THREAD_PREFETCH_MAX_ATTACHMENT_BYTES + 1)),
            "at_cap" to listOf(att("e", THREAD_PREFETCH_MAX_ATTACHMENT_BYTES)),
        )
        assertEquals(listOf("small", "at_cap"), select_attachment_prefetch(metas))
    }

    @Test
    fun skips_messages_whose_total_is_too_large() {
        val metas = mapOf(
            "many" to List(5) { att("m$it", 10 * mb) },
            "few" to List(3) { att("f$it", 10 * mb) },
            "empty" to emptyList(),
        )
        assertEquals(listOf("few"), select_attachment_prefetch(metas))
    }

    @Test
    fun fetches_with_bounded_concurrency() = runBlocking {
        val in_flight = AtomicInteger(0)
        val peak = AtomicInteger(0)
        val ids = (1..12).map { "msg_$it" }
        val results = prefetch_thread_attachments(ids, concurrency = 2) { id ->
            val now = in_flight.incrementAndGet()
            peak.updateAndGet { maxOf(it, now) }
            delay(10)
            in_flight.decrementAndGet()
            if (id == "msg_3") Result.failure(IllegalStateException()) else Result.success(listOf(att(id, mb)))
        }
        assertTrue(peak.get() <= 2)
        assertEquals(11, results.size)
        assertTrue("msg_3" !in results)
    }

    @Test
    fun loads_inline_images_from_messages_over_the_caps() = runBlocking {
        val logo = att("logo", 40 * 1024).copy(content_id = "<logo@mail>", content_type = "image/png")
        val pdf = att("pdf", 50 * mb).copy(content_type = "application/pdf")
        val metas = mapOf(
            "big" to listOf(pdf, logo),
            "small" to listOf(att("s", mb)),
        )
        val plan = plan_attachment_prefetch(metas)
        assertEquals(listOf("small"), plan.full_message_ids)
        assertEquals(mapOf("big" to listOf("logo")), plan.inline_attachment_ids)

        val in_flight = AtomicInteger(0)
        val peak = AtomicInteger(0)
        val fetched_attachments = mutableListOf<String>()
        val results = prefetch_thread_attachments(
            plan = plan,
            metas = metas,
            concurrency = 2,
            fetch_message = { id ->
                val now = in_flight.incrementAndGet()
                peak.updateAndGet { maxOf(it, now) }
                delay(10)
                in_flight.decrementAndGet()
                Result.success(listOf(att("s", mb).copy(encrypted_data = "s_data", data_nonce = "n")))
            },
            fetch_attachment = { id ->
                val now = in_flight.incrementAndGet()
                peak.updateAndGet { maxOf(it, now) }
                synchronized(fetched_attachments) { fetched_attachments += id }
                delay(10)
                in_flight.decrementAndGet()
                Result.success(logo.copy(encrypted_data = "logo_data", data_nonce = "logo_nonce", session_key = "k"))
            },
        )
        assertTrue(peak.get() <= 2)
        assertEquals(listOf("logo"), fetched_attachments)
        val big = results.getValue("big")
        assertEquals(listOf("pdf", "logo"), big.map { it.id })
        assertNull(big[0].encrypted_data)
        assertEquals("logo_data", big[1].encrypted_data)
        assertEquals("logo_nonce", big[1].data_nonce)
        assertEquals("<logo@mail>", big[1].content_id)
        assertEquals("s_data", results.getValue("small").single().encrypted_data)
    }
}
