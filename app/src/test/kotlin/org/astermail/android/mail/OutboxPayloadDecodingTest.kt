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

import java.io.FileNotFoundException
import java.io.IOException
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class OutboxPayloadDecodingTest {

    private val json = Json { ignoreUnknownKeys = true }

    @Test
    fun `decodes a well formed recipient list`() {
        assertEquals(
            listOf("a@astermail.org", "b@astermail.org"),
            decode_outbox_recipients(json, "[\"a@astermail.org\",\"b@astermail.org\"]", "to"),
        )
    }

    @Test
    fun `refuses to silently drop an unreadable recipient list`() {
        var thrown: Throwable? = null
        try {
            decode_outbox_recipients(json, "not json", "to")
        } catch (err: Throwable) {
            thrown = err
        }
        assertTrue(thrown is OutboxPayloadException)
        assertTrue(thrown!!.message!!.contains("to"))
    }

    @Test
    fun `a missing staged attachment file is permanent`() {
        assertTrue(
            is_permanent_attachment_failure_cause(
                FileNotFoundException("staged outbox attachments missing: x.json"),
            ),
        )
    }

    @Test
    fun `a transient read error is not permanent`() {
        assertFalse(is_permanent_attachment_failure_cause(IOException("disk busy")))
    }

    @Test
    fun `a wrapped missing file is still permanent`() {
        assertTrue(
            is_permanent_attachment_failure_cause(
                IllegalStateException("wrapped", FileNotFoundException("gone")),
            ),
        )
    }

    @Test
    fun `keeps an existing thread token for an external reply`() {
        assertEquals("tok-1", ensure_external_thread_token("tok-1"))
        assertEquals("tok-1", ensure_external_thread_token("  tok-1  "))
    }

    @Test
    fun `mints a thread token when an external send has none`() {
        val first = ensure_external_thread_token(null)
        val second = ensure_external_thread_token("   ")
        assertTrue(first.isNotBlank())
        assertTrue(second.isNotBlank())
        assertTrue(first != second)
    }

    @Test
    fun `an unreadable payload is permanent`() {
        assertTrue(is_permanent_attachment_failure_cause(OutboxPayloadException("bad")))
    }
}
