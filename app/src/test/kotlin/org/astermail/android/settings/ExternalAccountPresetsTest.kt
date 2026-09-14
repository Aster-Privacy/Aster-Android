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

package org.astermail.android.settings

import org.astermail.android.ui.settings.detail.clean_sync_error
import org.astermail.android.ui.settings.detail.external_provider_preset
import org.astermail.android.ui.settings.detail.is_external_preset_host
import org.astermail.android.ui.settings.detail.normalize_app_password
import org.astermail.android.ui.settings.detail.opens_gmail_wizard
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ExternalAccountPresetsTest {
    @Test
    fun `gmail address resolves to the gmail preset`() {
        val preset = external_provider_preset("Someone@Gmail.com")

        assertEquals("imap.gmail.com", preset?.host)
        assertEquals(993, preset?.port)
        assertEquals("smtp.gmail.com", preset?.smtp_host)
        assertEquals(587, preset?.smtp_port)
        assertEquals("https://myaccount.google.com/apppasswords", preset?.app_password_url)
    }

    @Test
    fun `googlemail and outlook aliases resolve`() {
        assertEquals("imap.gmail.com", external_provider_preset("a@googlemail.com")?.host)
        assertEquals("outlook.office365.com", external_provider_preset("a@hotmail.com")?.host)
        assertEquals("outlook.office365.com", external_provider_preset("a@live.com")?.host)
        assertEquals("imap.mail.me.com", external_provider_preset("a@me.com")?.host)
    }

    @Test
    fun `unknown domains and malformed addresses resolve to nothing`() {
        assertNull(external_provider_preset("someone@example.org"))
        assertNull(external_provider_preset("someone"))
        assertNull(external_provider_preset(""))
    }

    @Test
    fun `preset hosts are recognized case insensitively`() {
        assertTrue(is_external_preset_host("IMAP.GMAIL.COM"))
        assertTrue(is_external_preset_host(" imap.mail.yahoo.com "))
        assertFalse(is_external_preset_host("imap.example.org"))
        assertFalse(is_external_preset_host(""))
    }

    @Test
    fun `app password whitespace is stripped for google hosts when sixteen letters remain`() {
        assertEquals("abcdefghijklmnop", normalize_app_password("imap.gmail.com", "abcd efgh ijkl mnop"))
        assertEquals("abcdefghijklmnop", normalize_app_password("smtp.gmail.com", " abcd efgh ijkl mnop "))
        assertEquals("abcdefghijklmnop", normalize_app_password("IMAP.GMAIL.COM", "abcdefghijklmnop"))
        assertEquals(
            "abcdefghijklmnop",
            normalize_app_password("imap.gmail.com", "abcd\u00A0efgh\u00A0ijkl\u00A0mnop"),
        )
        assertEquals(
            "abcdefghijklmnop",
            normalize_app_password("imap.gmail.com", "abcd\u2009efgh\u202Fijkl\u3000mnop"),
        )
        assertEquals("abcdefghijklmnop", normalize_app_password("smtp.gmail.com", "ab cd\tefgh\nijklmnop"))
        assertEquals("abcdefghijklmnop", normalize_app_password("pop.gmail.com", "abcdefgh ijklmnop"))
        assertEquals("abcdefghijklmnop", normalize_app_password("imap.googlemail.com", "abcd efgh ijkl mnop"))
        assertEquals("ABCDefghIJKLmnop", normalize_app_password("imap.gmail.com", "ABCD efgh IJKL mnop"))
    }

    @Test
    fun `app password passes through unchanged when it is not a google app password`() {
        assertEquals("abcd efgh ijkl mnop", normalize_app_password("imap.mail.me.com", "abcd efgh ijkl mnop"))
        assertEquals("my real password", normalize_app_password("imap.gmail.com", "my real password"))
        assertEquals("abcd efgh ijkl", normalize_app_password("imap.gmail.com", "abcd efgh ijkl"))
        assertEquals("abcd efgh ijkl mnop1", normalize_app_password("imap.gmail.com", "abcd efgh ijkl mnop1"))
        assertEquals("abcd efgh ijkl mnopq", normalize_app_password("imap.gmail.com", "abcd efgh ijkl mnopq"))
        assertEquals("abc1 efgh ijkl mnop", normalize_app_password("imap.gmail.com", "abc1 efgh ijkl mnop"))
        assertEquals("abcd\u00E9fgh ijkl mnop", normalize_app_password("imap.gmail.com", "abcd\u00E9fgh ijkl mnop"))
    }

    @Test
    fun `legacy google and gmail accounts needing a new password reopen the setup wizard`() {
        assertTrue(opens_gmail_wizard("google", false, null))
        assertTrue(opens_gmail_wizard("Google", false, "a@example.org"))
        assertTrue(opens_gmail_wizard(null, true, "someone@gmail.com"))
        assertTrue(opens_gmail_wizard(null, true, "someone@googlemail.com"))
        assertFalse(opens_gmail_wizard(null, false, "someone@gmail.com"))
        assertFalse(opens_gmail_wizard(null, true, "someone@outlook.com"))
        assertFalse(opens_gmail_wizard("microsoft", true, "someone@outlook.com"))
        assertFalse(opens_gmail_wizard(null, true, null))
    }

    @Test
    fun `sync error text drops the imap authentication prefix`() {
        assertEquals(
            "Invalid credentials",
            clean_sync_error("IMAP authentication failed: Invalid credentials"),
        )
        assertEquals(
            "Invalid credentials",
            clean_sync_error("imap authentication failed:Invalid credentials"),
        )
        assertEquals("Connection timed out", clean_sync_error("  Connection timed out  "))
    }
}
