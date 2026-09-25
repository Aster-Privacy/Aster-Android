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

import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import io.mockk.unmockkAll
import io.mockk.verify
import kotlinx.coroutines.runBlocking
import org.astermail.android.storage.SessionKeyStore
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test

class PasswordChangeSentMailTest {

    private val old_passphrase = "old password value".toByteArray(Charsets.UTF_8)
    private val new_passphrase = "new password value".toByteArray(Charsets.UTF_8)

    private lateinit var conversion: AccountDataConversion
    private lateinit var resealer: SentMailResealer
    private lateinit var session_key_store: SessionKeyStore
    private lateinit var subject: PasswordChangeSentMail

    @Before
    fun setup() {
        conversion = mockk()
        resealer = mockk()
        session_key_store = mockk(relaxed = true)
        coEvery { resealer.run(any(), any(), any()) } returns SentMailResealSummary(checked = 2, rewritten = 2)
        subject = PasswordChangeSentMail(conversion, resealer, SentMailResealFinisher(session_key_store, resealer))
    }

    @After
    fun teardown() {
        unmockkAll()
    }

    @Test
    fun skips_the_reseal_and_stores_no_passphrase_when_not_needed() = runBlocking {
        coEvery { conversion.sent_mail_needs_password_reseal(PasswordChangeConversion.COMPLETE) } returns false

        val summary = subject.reseal_after_change(PasswordChangeConversion.COMPLETE, old_passphrase, new_passphrase, true)

        assertEquals(SentMailResealSummary(), summary)
        verify(exactly = 0) { session_key_store.put_pending_reseal_passphrase(any()) }
        verify(exactly = 1) { session_key_store.clear_pending_reseal_passphrase() }
        coVerify(exactly = 0) { resealer.run(any(), any(), any()) }
    }

    @Test
    fun still_reports_a_failed_key_rewrap_when_the_reseal_is_skipped() = runBlocking {
        coEvery { conversion.sent_mail_needs_password_reseal(any()) } returns false

        val summary = subject.reseal_after_change(PasswordChangeConversion.COMPLETE, old_passphrase, new_passphrase, false)

        assertEquals(1, summary.failed)
        verify(exactly = 0) { session_key_store.put_pending_reseal_passphrase(any()) }
    }

    @Test
    fun runs_the_legacy_reseal_when_needed() = runBlocking {
        coEvery { conversion.sent_mail_needs_password_reseal(PasswordChangeConversion.INCOMPLETE) } returns true

        val summary = subject.reseal_after_change(PasswordChangeConversion.INCOMPLETE, old_passphrase, new_passphrase, true)

        assertEquals(SentMailResealSummary(checked = 2, rewritten = 2), summary)
        verify(exactly = 1) { session_key_store.put_pending_reseal_passphrase(old_passphrase) }
        coVerify(exactly = 1) { resealer.run(old_passphrase, new_passphrase, any()) }
        verify(exactly = 1) { session_key_store.clear_pending_reseal_passphrase() }
    }

    @Test
    fun keeps_the_pending_passphrase_when_the_needed_reseal_fails() = runBlocking {
        coEvery { conversion.sent_mail_needs_password_reseal(any()) } returns true
        coEvery { resealer.run(any(), any(), any()) } returns SentMailResealSummary(checked = 1, failed = 1)

        val summary = subject.reseal_after_change(PasswordChangeConversion.UNAVAILABLE, old_passphrase, new_passphrase, true)

        assertEquals(1, summary.failed)
        verify(exactly = 1) { session_key_store.put_pending_reseal_passphrase(old_passphrase) }
        verify(exactly = 0) { session_key_store.clear_pending_reseal_passphrase() }
    }

    @Test
    fun keeps_the_pending_passphrase_when_the_needed_reseal_throws() = runBlocking {
        coEvery { conversion.sent_mail_needs_password_reseal(any()) } returns true
        coEvery { resealer.run(any(), any(), any()) } throws IllegalStateException("listing")

        val summary = subject.reseal_after_change(PasswordChangeConversion.INCOMPLETE, old_passphrase, new_passphrase, true)

        assertEquals(SentMailResealSummary(failed = 1), summary)
        verify(exactly = 1) { session_key_store.put_pending_reseal_passphrase(old_passphrase) }
        verify(exactly = 0) { session_key_store.clear_pending_reseal_passphrase() }
    }

    @Test
    fun keeps_the_pending_passphrase_when_the_key_rewrap_fails() = runBlocking {
        coEvery { conversion.sent_mail_needs_password_reseal(any()) } returns true

        val summary = subject.reseal_after_change(PasswordChangeConversion.INCOMPLETE, old_passphrase, new_passphrase, false)

        assertEquals(1, summary.failed)
        verify(exactly = 1) { session_key_store.put_pending_reseal_passphrase(old_passphrase) }
        verify(exactly = 0) { session_key_store.clear_pending_reseal_passphrase() }
    }

    @Test
    fun runs_the_legacy_reseal_when_the_decision_throws() = runBlocking {
        coEvery { conversion.sent_mail_needs_password_reseal(any()) } throws IllegalStateException("status")

        subject.reseal_after_change(PasswordChangeConversion.COMPLETE, old_passphrase, new_passphrase, true)

        verify(exactly = 1) { session_key_store.put_pending_reseal_passphrase(old_passphrase) }
        coVerify(exactly = 1) { resealer.run(old_passphrase, new_passphrase, any()) }
    }

    @Test
    fun reports_incomplete_when_the_conversion_throws() = runBlocking {
        coEvery { conversion.convert_before_password_change(any(), any(), any()) } throws IllegalStateException("x")

        assertEquals(
            PasswordChangeConversion.INCOMPLETE,
            subject.convert_before_change("identity", old_passphrase),
        )
    }

    @Test
    fun passes_the_conversion_result_through() = runBlocking {
        coEvery { conversion.convert_before_password_change("identity", old_passphrase, any()) } returns
            PasswordChangeConversion.COMPLETE

        assertEquals(
            PasswordChangeConversion.COMPLETE,
            subject.convert_before_change("identity", old_passphrase),
        )
    }
}
