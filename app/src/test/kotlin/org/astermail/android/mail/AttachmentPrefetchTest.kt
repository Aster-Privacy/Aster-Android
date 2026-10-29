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
}
