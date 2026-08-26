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

import org.astermail.android.mail.ratchet.PostQuantumUnavailableException
import org.astermail.android.mail.ratchet.RatchetEncryptionException
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.IOException

class SendFailureClassificationTest {

    @Test
    fun `a missing prekey bundle is a permanent failure`() {
        val err = RatchetEncryptionException(
            "friend@astermail.org",
            "no prekey bundle available for recipient",
        )

        assertTrue(is_permanent_send_failure_cause(err))
        assertFalse(is_transient_send_cause(err))
    }

    @Test
    fun `a ratchet failure caused by a dropped connection is retried`() {
        val err = RatchetEncryptionException(
            "friend@astermail.org",
            "prekey fetch failed",
            IOException("connection reset"),
        )

        assertFalse(is_permanent_send_failure_cause(err))
        assertTrue(is_transient_send_cause(err))
    }

    @Test
    fun `a post quantum gap is a permanent failure`() {
        assertTrue(
            is_permanent_send_failure_cause(
                PostQuantumUnavailableException(listOf("friend@astermail.org")),
            ),
        )
    }

    @Test
    fun `running out of memory is not retried forever`() {
        assertTrue(is_permanent_send_failure_cause(OutOfMemoryError("no room")))
        assertFalse(is_transient_send_cause(OutOfMemoryError("no room")))
    }

    @Test
    fun `a plain network failure is transient and not permanent`() {
        val err = IOException("unable to resolve host")

        assertTrue(is_transient_send_cause(err))
        assertFalse(is_permanent_send_failure_cause(err))
    }

    @Test
    fun `a permanent cause nested behind a wrapper is still permanent`() {
        val err = IllegalStateException(
            "send failed",
            RatchetEncryptionException("friend@astermail.org", "no prekey bundle available"),
        )

        assertTrue(is_permanent_send_failure_cause(err))
    }
}
