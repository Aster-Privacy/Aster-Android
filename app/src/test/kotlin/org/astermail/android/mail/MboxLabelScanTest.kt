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

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class MboxLabelScanTest {

    private fun mbox(vararg messages: String): java.io.InputStream =
        messages.joinToString("").toByteArray(Charsets.UTF_8).inputStream()

    private fun message(labels: String?, body: String = "hello"): String {
        val header = if (labels == null) "" else "X-Gmail-Labels: $labels\r\n"
        return "From 1234@xxx Mon Jan 01 00:00:00 2024\r\n" +
            "From: a@example.com\r\n" +
            header +
            "Subject: s\r\n" +
            "\r\n" +
            "$body\r\n" +
            "X-Gmail-Labels: NotAHeader\r\n\r\n"
    }

    @Test
    fun `collects only custom labels in first seen order`() {
        val labels = scan_mbox_labels(
            mbox(
                message("Inbox,Category Personal,Receipts/2025,Unread"),
                message("Archived,Important,Work,IMAP_Forwarded"),
                message("Sent,Receipts/2025"),
                message(null),
            ),
        )

        assertEquals(listOf("Receipts/2025", "Work"), labels)
    }

    @Test
    fun `joins folded label headers and ignores case`() {
        val raw = "From x@y Mon Jan 01 00:00:00 2024\n" +
            "x-gmail-labels: Inbox,\n" +
            "\tProjects/Alpha, Starred\n" +
            "Subject: s\n" +
            "\n" +
            "body\n"

        assertEquals(listOf("Projects/Alpha"), scan_mbox_labels(raw.toByteArray().inputStream()))
    }

    @Test
    fun `caps the number of folders`() {
        val text = (1..MAX_IMPORT_FOLDERS + 20).joinToString("") { message("Folder $it") }

        assertEquals(MAX_IMPORT_FOLDERS, scan_mbox_labels(text.toByteArray().inputStream()).size)
    }

    @Test
    fun `blank file has no labels`() {
        assertTrue(scan_mbox_labels(ByteArray(0).inputStream()).isEmpty())
    }
}
