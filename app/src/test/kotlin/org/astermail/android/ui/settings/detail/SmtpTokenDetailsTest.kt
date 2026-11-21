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

package org.astermail.android.ui.settings.detail

import java.time.ZoneId
import java.time.ZoneOffset
import java.util.Locale
import kotlinx.serialization.json.Json
import org.astermail.android.api.settings.ListSmtpTokensResponse
import org.astermail.android.api.settings.SmtpTokenConnectionSettings
import org.astermail.android.api.settings.SmtpTokenRow
import org.astermail.android.settings.SMTP_TOKEN_LABEL_MAX_LENGTH
import org.astermail.android.settings.normalize_smtp_token_label
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class SmtpTokenDetailsTest {

    private val json = Json {
        ignoreUnknownKeys = true
        explicitNulls = false
        isLenient = true
        encodeDefaults = true
        coerceInputValues = true
    }

    private val settings = SmtpTokenConnectionSettings(
        host = "smtp.example.com",
        port = 587,
        security = "STARTTLS",
        username = "sender@example.com",
    )

    @Test
    fun a_row_without_connection_settings_still_decodes() {
        val body = """
            {"tokens":[{"id":"t1","bound_address":"sender@example.com","created_at":"2026-11-01T08:00:00Z",
            "last_used_at":null,"label_encrypted":"abc","label_nonce":"def"}]}
        """.trimIndent()

        val row = json.decodeFromString<ListSmtpTokensResponse>(body).tokens.single()

        assertEquals("t1", row.id)
        assertNull(row.smtp_settings)
        assertNull(row.last_used_at)
        assertNull(smtp_token_setup_details(row))
    }

    @Test
    fun a_row_with_connection_settings_decodes_them() {
        val body = """
            {"tokens":[{"id":"t1","bound_address":"sender@example.com",
            "last_used_at":"2026-11-20T10:15:30.123456Z",
            "smtp_settings":{"host":"smtp.example.com","port":587,"security":"STARTTLS",
            "username":"sender@example.com"}}]}
        """.trimIndent()

        val row = json.decodeFromString<ListSmtpTokensResponse>(body).tokens.single()

        assertEquals(settings, row.smtp_settings)
        assertEquals(settings, smtp_token_setup_details(row))
        assertEquals("2026-11-20T10:15:30.123456Z", row.last_used_at)
    }

    @Test
    fun a_password_key_in_listed_settings_is_never_surfaced() {
        val body = """
            {"tokens":[{"id":"t1","smtp_settings":{"host":"smtp.example.com","port":587,
            "security":"STARTTLS","username":"sender@example.com","password":"do-not-surface"}}]}
        """.trimIndent()

        val row = json.decodeFromString<ListSmtpTokensResponse>(body).tokens.single()
        val details = smtp_token_setup_details(row)

        assertEquals(settings, details)
        assertFalse(row.toString().contains("do-not-surface"))
        assertFalse(json.encodeToString(SmtpTokenRow.serializer(), row).contains("do-not-surface"))
        assertFalse(
            SmtpTokenConnectionSettings::class.java.declaredFields
                .any { it.name.contains("password", ignoreCase = true) },
        )
    }

    @Test
    fun setup_details_need_a_host_and_a_username() {
        assertNull(smtp_token_setup_details(SmtpTokenRow(id = "t1")))
        assertNull(smtp_token_setup_details(SmtpTokenRow(id = "t1", smtp_settings = settings.copy(host = " "))))
        assertNull(smtp_token_setup_details(SmtpTokenRow(id = "t1", smtp_settings = settings.copy(username = ""))))
        assertNotNull(smtp_token_setup_details(SmtpTokenRow(id = "t1", smtp_settings = settings)))
    }

    @Test
    fun details_copy_text_lists_only_the_four_settings() {
        val text = smtp_connection_copy_text(
            host_label = "Host",
            port_label = "Port",
            security_label = "Security",
            username_label = "Username",
            settings = settings,
        )

        assertEquals(
            listOf(
                "Host: smtp.example.com",
                "Port: 587",
                "Security: STARTTLS",
                "Username: sender@example.com",
            ),
            text.lines(),
        )
        assertFalse(text.contains("Password", ignoreCase = true))
    }

    @Test
    fun last_used_is_absent_only_when_the_token_was_never_used() {
        assertNull(format_smtp_last_used(null, ZoneOffset.UTC, Locale.US))
        assertNotNull(format_smtp_last_used("2026-11-20T10:15:30Z", ZoneOffset.UTC, Locale.US))
    }

    @Test
    fun last_used_parses_fractional_seconds() {
        assertEquals("Nov 20, 2026", format_smtp_last_used("2026-11-20T10:15:30.123456Z", ZoneOffset.UTC, Locale.US))
        assertEquals("Nov 20, 2026", format_smtp_last_used("2026-11-20T10:15:30.123Z", ZoneOffset.UTC, Locale.US))
        assertEquals("Nov 20, 2026", format_smtp_last_used("2026-11-20T10:15:30Z", ZoneOffset.UTC, Locale.US))
        assertEquals(
            "Nov 20, 2026",
            format_smtp_last_used("2026-11-20T10:15:30.123456+00:00", ZoneOffset.UTC, Locale.US),
        )
    }

    @Test
    fun last_used_follows_the_device_time_zone() {
        val zone = ZoneId.of("Asia/Tokyo")

        assertEquals("Nov 21, 2026", format_smtp_last_used("2026-11-20T23:15:30.123456Z", zone, Locale.US))
    }

    @Test
    fun last_used_falls_back_to_the_raw_value_when_it_cannot_be_parsed() {
        assertEquals("yesterday", format_smtp_last_used("yesterday", ZoneOffset.UTC, Locale.US))
    }

    @Test
    fun a_token_name_is_trimmed() {
        assertEquals("Newsletter sender", normalize_smtp_token_label("  Newsletter sender  "))
    }

    @Test
    fun an_empty_token_name_is_rejected() {
        assertNull(normalize_smtp_token_label(""))
        assertNull(normalize_smtp_token_label("   "))
    }

    @Test
    fun a_token_name_with_control_characters_is_rejected() {
        assertNull(normalize_smtp_token_label("first\nsecond"))
        assertNull(normalize_smtp_token_label("first\tsecond"))
        assertNull(normalize_smtp_token_label("first\u0000second"))
    }

    @Test
    fun a_token_name_is_capped_at_the_shared_length() {
        assertEquals(64, SMTP_TOKEN_LABEL_MAX_LENGTH)
        assertEquals("a".repeat(64), normalize_smtp_token_label("a".repeat(64)))
        assertNull(normalize_smtp_token_label("a".repeat(65)))
        assertTrue(normalize_smtp_token_label(" " + "a".repeat(64) + " ") != null)
    }
}
