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

package org.astermail.android.notifications

import org.junit.Assert.assertEquals
import org.junit.Test

class NotificationTextTest {

    @Test
    fun `a readable message shows its subject and preview`() {
        assertEquals(
            "Lunch" to "See you at noon",
            MailPollingWorker.notification_text(" Lunch ", "See you at noon", is_undecryptable = false, is_decrypt_pending = false),
        )
    }

    @Test
    fun `a message still decrypting keeps its subject and hides the preview`() {
        assertEquals(
            "Re: Lunch" to "",
            MailPollingWorker.notification_text("Re: Lunch", "", is_undecryptable = true, is_decrypt_pending = true),
        )
    }

    @Test
    fun `an unreadable message falls back to the neutral new message text`() {
        assertEquals(
            "" to "",
            MailPollingWorker.notification_text("Re: Lunch", "leftover", is_undecryptable = true, is_decrypt_pending = false),
        )
    }

    @Test
    fun `an unreadable message heals keys and retries before posting`() {
        assertEquals(
            MailPollingWorker.UnreadableRetry.Heal,
            MailPollingWorker.unreadable_retry(is_undecryptable = true, is_decrypt_pending = false, attempt = 0),
        )
        assertEquals(
            MailPollingWorker.UnreadableRetry.Heal,
            MailPollingWorker.unreadable_retry(is_undecryptable = true, is_decrypt_pending = false, attempt = 1),
        )
    }

    @Test
    fun `a message still decrypting waits without a key refresh`() {
        assertEquals(
            MailPollingWorker.UnreadableRetry.Wait,
            MailPollingWorker.unreadable_retry(is_undecryptable = true, is_decrypt_pending = true, attempt = 0),
        )
    }

    @Test
    fun `a readable message posts at once`() {
        assertEquals(
            MailPollingWorker.UnreadableRetry.None,
            MailPollingWorker.unreadable_retry(is_undecryptable = false, is_decrypt_pending = false, attempt = 0),
        )
    }

    @Test
    fun `the last attempt posts whatever it has`() {
        assertEquals(
            MailPollingWorker.UnreadableRetry.None,
            MailPollingWorker.unreadable_retry(
                is_undecryptable = true,
                is_decrypt_pending = false,
                attempt = MailPollingWorker.UNREADABLE_RETRY_ATTEMPTS - 1,
            ),
        )
    }

    @Test
    fun `an unreadable message later in the batch still heals`() {
        assertEquals(
            MailPollingWorker.UnreadableRetry.Heal,
            MailPollingWorker.combine_unreadable_retry(
                listOf(MailPollingWorker.UnreadableRetry.None, MailPollingWorker.UnreadableRetry.Heal),
                healed = false,
            ),
        )
    }

    @Test
    fun `keys heal once per run and later attempts wait`() {
        assertEquals(
            MailPollingWorker.UnreadableRetry.Wait,
            MailPollingWorker.combine_unreadable_retry(
                listOf(MailPollingWorker.UnreadableRetry.Heal),
                healed = true,
            ),
        )
    }

    @Test
    fun `a readable batch posts at once`() {
        assertEquals(
            MailPollingWorker.UnreadableRetry.None,
            MailPollingWorker.combine_unreadable_retry(
                listOf(MailPollingWorker.UnreadableRetry.None, MailPollingWorker.UnreadableRetry.None),
                healed = true,
            ),
        )
    }

    @Test
    fun `an unreadable message gets no notification of its own`() {
        assertEquals(false, MailPollingWorker.has_notifiable_text(is_envelope_missing = false, is_undecryptable = true, is_decrypt_pending = false))
        assertEquals(false, MailPollingWorker.has_notifiable_text(is_envelope_missing = true, is_undecryptable = false, is_decrypt_pending = false))
    }

    @Test
    fun `a readable or still decrypting message keeps its notification`() {
        assertEquals(true, MailPollingWorker.has_notifiable_text(is_envelope_missing = false, is_undecryptable = false, is_decrypt_pending = false))
        assertEquals(true, MailPollingWorker.has_notifiable_text(is_envelope_missing = false, is_undecryptable = true, is_decrypt_pending = true))
    }
}
