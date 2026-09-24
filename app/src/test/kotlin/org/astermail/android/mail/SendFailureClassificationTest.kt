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

import org.astermail.android.api.ApiError
import org.astermail.android.mail.ratchet.PostQuantumUnavailableException
import org.astermail.android.mail.ratchet.RatchetEncryptionException
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.IOException

class SendFailureClassificationTest {

    @Test
    fun `a server attachment refusal is a permanent failure`() {
        val err = ApiError.ValidationError(
            listOf("total attachment size exceeds 50MB limit"),
            code = "ATTACHMENTS_TOO_LARGE",
            details = mapOf("max_bytes" to "52428800"),
        )

        assertTrue(is_permanent_send_failure_cause(err))
        assertFalse(is_transient_send_cause(err))
    }

    @Test
    fun `an oversized request body is a permanent failure`() {
        assertTrue(is_permanent_send_failure_cause(ApiError.AttachmentTooLarge("too large")))
    }

    @Test
    fun `a wrapped validation error is still permanent`() {
        val err = RuntimeException("send failed", ApiError.ValidationError(listOf("bad request")))

        assertTrue(is_permanent_send_failure_cause(err))
    }

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
    fun `a missing recipient key is a permanent failure`() {
        assertTrue(is_permanent_send_failure_cause(E2eEncryptionException("encryption failed")))
        assertTrue(
            is_permanent_send_failure_cause(
                E2eEncryptionException("encryption failed", ApiError.NotFoundError),
            ),
        )
        assertTrue(
            is_permanent_send_failure_cause(
                IllegalStateException("send failed", E2eEncryptionException("encryption failed")),
            ),
        )
    }

    @Test
    fun `an attachment that cannot be prepared is a permanent failure`() {
        assertTrue(
            is_permanent_send_failure_cause(
                AttachmentPrepareException("could not prepare a.txt", IllegalArgumentException("bad")),
            ),
        )
    }

    @Test
    fun `a key lookup that fails on the network is retried`() {
        assertFalse(
            is_permanent_send_failure_cause(
                E2eEncryptionException("encryption failed", ApiError.NetworkError),
            ),
        )
        assertFalse(
            is_permanent_send_failure_cause(
                E2eEncryptionException("encryption failed", ApiError.ServerError(503)),
            ),
        )
        assertFalse(
            is_permanent_send_failure_cause(
                E2eEncryptionException("encryption failed", IOException("connection reset")),
            ),
        )
        assertFalse(
            is_permanent_send_failure_cause(
                AttachmentPrepareException("could not prepare a.txt", IOException("timed out")),
            ),
        )
    }

    @Test
    fun `a permanent cause nested behind a wrapper is still permanent`() {
        val err = IllegalStateException(
            "send failed",
            RatchetEncryptionException("friend@astermail.org", "no prekey bundle available"),
        )

        assertTrue(is_permanent_send_failure_cause(err))
    }

    @Test
    fun `a forbidden send is a permanent failure`() {
        val err = ApiError.ForbiddenError("sending is disabled for this account", "ACCOUNT_SUSPENDED")

        assertTrue(is_permanent_send_failure_cause(err))
        assertTrue(is_permanent_send_failure_cause(RuntimeException("send failed", err)))
    }

    @Test
    fun `a csrf or origin refusal is retried`() {
        assertFalse(is_permanent_send_failure_cause(ApiError.ForbiddenError("csrf", "CSRF_INVALID")))
        assertFalse(is_permanent_send_failure_cause(ApiError.ForbiddenError("origin", "ORIGIN_NOT_ALLOWED")))
    }

    @Test
    fun `plan and quota refusals are permanent failures`() {
        assertTrue(is_permanent_send_failure_cause(ApiError.PlanLimitExceeded("upgrade", "has_email_expiration")))
        assertTrue(is_permanent_send_failure_cause(ApiError.PaymentRequired("payment required")))
        assertTrue(is_permanent_send_failure_cause(ApiError.SendQuotaReached("quota")))
        assertTrue(is_permanent_send_failure_cause(ApiError.StorageQuotaExceeded("full")))
    }

    @Test
    fun `a validation error that mentions a timeout is still permanent`() {
        val err = ApiError.ValidationError(listOf("expiration timeout is out of range"))

        assertTrue(is_permanent_send_failure_cause(err))
    }

    @Test
    fun `mixed recipients are a permanent failure`() {
        assertTrue(is_permanent_send_failure_cause(MixedRecipientsException()))
    }

    @Test
    fun `unauthorized, timeout, rate limit and server errors are retried`() {
        assertFalse(is_permanent_send_failure_cause(ApiError.UnauthorizedError))
        assertFalse(is_permanent_send_failure_cause(ApiError.UnknownError("request timeout")))
        assertFalse(is_permanent_send_failure_cause(ApiError.UnknownError("http 408")))
        assertFalse(is_permanent_send_failure_cause(ApiError.RateLimited()))
        assertFalse(is_permanent_send_failure_cause(ApiError.ServerError(500)))
        assertFalse(is_permanent_send_failure_cause(ApiError.ServerError(503)))
    }

    @Test
    fun `the server rejection is surfaced from behind a wrapper`() {
        val inner = ApiError.ForbiddenError("expiring messages need a paid plan", "PLAN_REQUIRED")
        val err = IllegalStateException("send failed", inner)

        assertEquals(inner, server_rejection_cause(err))
        assertNull(server_rejection_cause(IOException("connection reset")))
    }

    @Test
    fun `recipients on internal and external domains are mixed`() {
        assertTrue(has_mixed_recipients(listOf("a@astermail.org", "b@example.com")))
        assertFalse(has_mixed_recipients(listOf("a@astermail.org", "b@aster.cx", "c@realiased.me")))
        assertFalse(has_mixed_recipients(listOf("a@example.com", "b@example.net")))
        assertFalse(has_mixed_recipients(listOf("a@gs-cloud.space", "b@example.com")))
    }
}
