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

import org.astermail.android.R
import org.junit.Assert.assertEquals
import org.junit.Test

class SendResultMessageTest {

    @Test
    fun a_missing_sent_copy_attachment_never_claims_the_send_failed() {
        val message = send_result_message_for(SentCopyAttachmentException(2))

        assertEquals(R.string.sent_copy_attachments_missing, message.res_id)
        assertEquals(2, message.arg)
    }

    @Test
    fun a_retrying_send_still_reports_that_it_is_trying() {
        val message = send_result_message_for(TransientSendException())

        assertEquals(R.string.send_still_trying, message.res_id)
        assertEquals(null, message.arg)
    }

    @Test
    fun a_real_send_failure_still_reports_a_failure() {
        val message = send_result_message_for(IllegalStateException("send rejected"))

        assertEquals(R.string.send_problem_failed_message, message.res_id)
        assertEquals(null, message.arg)
    }
}
