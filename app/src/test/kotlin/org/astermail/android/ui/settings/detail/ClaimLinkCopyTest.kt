// Aster Mail - Privacy-first encrypted email
// Copyright (C) 2026 Aster Privacy
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

package org.astermail.android.ui.settings.detail

import android.content.ClipData
import android.content.Context
import android.widget.Toast
import io.mockk.Runs
import io.mockk.every
import io.mockk.just
import io.mockk.mockk
import io.mockk.mockkStatic
import io.mockk.unmockkAll
import io.mockk.verify
import org.astermail.android.ui.common.write_sensitive_to_clipboard
import org.astermail.android.ui.common.write_to_clipboard
import org.astermail.android.util.schedule_sensitive_clipboard_clear
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test

class ClaimLinkCopyTest {

    private val url = "https://app.astermail.org/family/claim/c0ffee-claim-token"
    private val context = mockk<Context>(relaxed = true)
    private val toasts = mutableListOf<String>()

    @Before
    fun setup() {
        mockkStatic("org.astermail.android.ui.common.Copy_feedbackKt")
        mockkStatic("org.astermail.android.util.ClipboardUtilKt")
        mockkStatic(ClipData::class)
        mockkStatic(Toast::class)
        every { ClipData.newPlainText(any(), any()) } returns mockk(relaxed = true)
        every { write_to_clipboard(any(), any()) } returns true
        every { write_sensitive_to_clipboard(any(), any(), any()) } returns true
        every { schedule_sensitive_clipboard_clear(any()) } just Runs
        every { context.getString(any()) } returns "Copy failed"
        every { context.getString(any(), *anyVararg()) } answers {
            "Copied " + it.invocation.args.drop(1).joinToString { arg ->
                if (arg is Array<*>) arg.joinToString() else arg.toString()
            }
        }
        every { Toast.makeText(any(), any<CharSequence>(), any()) } answers {
            toasts += secondArg<CharSequence>().toString()
            mockk(relaxed = true)
        }
    }

    @After
    fun teardown() {
        unmockkAll()
    }

    @Test
    fun `a claim link goes to the clipboard as a sensitive clip`() {
        copy_claim_link(context, url, "Claim link copied")
        verify(exactly = 1) { write_sensitive_to_clipboard(context, any(), url) }
        verify(exactly = 0) { write_to_clipboard(any(), any()) }
    }

    @Test
    fun `a copied claim link is cleared from the clipboard later`() {
        copy_claim_link(context, url, "Claim link copied")
        verify(exactly = 1) { schedule_sensitive_clipboard_clear(context) }
    }

    @Test
    fun `the toast names the action without showing the link`() {
        copy_claim_link(context, url, "Claim link copied")
        assertEquals(listOf("Claim link copied"), toasts)
    }

    @Test
    fun `a failed copy reports the failure and schedules no clear`() {
        every { write_sensitive_to_clipboard(any(), any(), any()) } returns false
        every { write_to_clipboard(any(), any()) } returns false
        copy_claim_link(context, url, "Claim link copied")
        assertEquals(listOf("Copy failed"), toasts)
        verify(exactly = 0) { schedule_sensitive_clipboard_clear(any()) }
    }
}
