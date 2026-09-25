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

import java.util.Base64
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import org.astermail.android.api.keys.AccountDataConversionStatus
import org.astermail.android.api.keys.AccountKeyCapabilityFlags
import org.astermail.android.api.keys.ConversionProgressRequest
import org.astermail.android.api.keys.ConversionWriteResult
import org.astermail.android.api.keys.parse_account_data_conversion
import org.astermail.android.api.keys.parse_account_key_capabilities
import org.astermail.android.api.keys.parse_conversion_write_result
import org.astermail.android.api.preferences.PreferencesSaveResult
import org.astermail.android.api.preferences.VersionedEncryptedPreferences
import org.astermail.android.api.preferences.parse_preferences_save_result
import org.astermail.android.api.preferences.parse_versioned_preferences
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class AccountDataConversionContractTest {

    private val api_json = Json {
        ignoreUnknownKeys = true
        explicitNulls = false
        encodeDefaults = true
    }

    @Test
    fun reads_the_data_conversion_capability() {
        assertEquals(
            AccountKeyCapabilityFlags(format_writes = true, data_conversion = true),
            parse_account_key_capabilities("""{"format_writes":true,"data_conversion":true}"""),
        )
        assertEquals(
            AccountKeyCapabilityFlags(format_writes = true, data_conversion = false),
            parse_account_key_capabilities("""{"format_writes":true}"""),
        )
        assertFalse(parse_account_key_capabilities("""{"data_conversion":"true"}""").data_conversion)
        assertFalse(parse_account_key_capabilities("not json").data_conversion)
    }

    @Test
    fun reads_the_conversion_status_only_when_enabled_with_valid_counts() {
        assertEquals(
            AccountDataConversionStatus(
                sent_mail_done_at = null,
                preferences_done_at = "2026-09-01T00:00:00Z",
                remaining_sent = 3,
                remaining_attachments = 0,
            ),
            parse_account_data_conversion(
                """{"enabled":true,"sent_mail_done_at":null,"preferences_done_at":"2026-09-01T00:00:00Z",""" +
                    """"converted_count":0,"skipped_count":0,"remaining_sent":3,"remaining_attachments":0}""",
            ),
        )
        assertNull(parse_account_data_conversion("""{"enabled":false,"remaining_sent":0,"remaining_attachments":0}"""))
        assertNull(parse_account_data_conversion("""{"enabled":true,"remaining_sent":"3","remaining_attachments":0}"""))
        assertNull(parse_account_data_conversion("""{"enabled":true,"remaining_sent":-1,"remaining_attachments":0}"""))
        assertNull(parse_account_data_conversion("""{"enabled":true,"remaining_sent":1}"""))
    }

    @Test
    fun maps_conversion_write_responses() {
        assertEquals(ConversionWriteResult.CONVERTED, parse_conversion_write_result(200, """{"status":"converted"}"""))
        assertEquals(ConversionWriteResult.FAILED, parse_conversion_write_result(200, """{}"""))
        assertEquals(
            ConversionWriteResult.ALREADY_CONVERTED,
            parse_conversion_write_result(409, """{"error":"x","code":"ALREADY_CONVERTED"}"""),
        )
        assertEquals(
            ConversionWriteResult.SOURCE_CHANGED,
            parse_conversion_write_result(409, """{"error":"x","code":"CONVERSION_SOURCE_CHANGED"}"""),
        )
        assertEquals(ConversionWriteResult.FAILED, parse_conversion_write_result(500, ""))
    }

    @Test
    fun omits_unset_progress_fields() {
        assertEquals(
            """{"sent_mail_done":true}""",
            api_json.encodeToString(ConversionProgressRequest(sent_mail_done = true)),
        )
        assertEquals(
            """{"converted":2,"skipped":1}""",
            api_json.encodeToString(ConversionProgressRequest(converted = 2, skipped = 1)),
        )
    }

    @Test
    fun reads_the_preferences_version() {
        assertEquals(
            VersionedEncryptedPreferences("ZW5j", "bm9uY2U=", 7),
            parse_versioned_preferences(
                """{"encrypted_preferences":"ZW5j","preferences_nonce":"bm9uY2U=","preferences_version":7}""",
            ),
        )
        assertNull(parse_versioned_preferences("""{"preferences_version":-1}""")!!.preferences_version)
        assertNull(parse_versioned_preferences("""{"preferences_version":"7"}""")!!.preferences_version)
    }

    @Test
    fun treats_the_version_conflict_code_as_a_conflict() {
        assertEquals(
            PreferencesSaveResult.CONFLICT,
            parse_preferences_save_result(409, """{"error":"x","code":"PREFERENCES_VERSION_CONFLICT"}"""),
        )
        assertEquals(
            PreferencesSaveResult.SAVED,
            parse_preferences_save_result(200, """{"success":true,"preferences_version":5}"""),
        )
        assertEquals(PreferencesSaveResult.FAILED, parse_preferences_save_result(200, """{"success":false}"""))
        assertEquals(PreferencesSaveResult.FAILED, parse_preferences_save_result(500, ""))
    }

    @Test
    fun recognizes_legacy_meta_nonces() {
        val encoder = Base64.getEncoder()
        assertTrue(AccountDataConversion.is_legacy_meta_nonce(encoder.encodeToString(ByteArray(12) { 1 })))
        assertFalse(AccountDataConversion.is_legacy_meta_nonce(encoder.encodeToString(ByteArray(12))))
        assertFalse(AccountDataConversion.is_legacy_meta_nonce(encoder.encodeToString(byteArrayOf(1))))
        assertFalse(AccountDataConversion.is_legacy_meta_nonce(null))
    }

    @Test
    fun rejects_malformed_utf8() {
        assertNull(AccountDataConversion.strict_utf8(byteArrayOf(0xC3.toByte(), 0x28)))
        assertEquals("{}", AccountDataConversion.strict_utf8("{}".toByteArray()))
    }
}
