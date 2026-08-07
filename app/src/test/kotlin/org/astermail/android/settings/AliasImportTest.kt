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

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class AliasImportTest {

    @Test
    fun splits_quoted_cells_and_unescapes_doubled_quotes() {
        assertEquals(
            listOf("a@b.com", "he said \"hi\", loudly", "true"),
            parse_csv_row("a@b.com,\"he said \"\"hi\"\", loudly\",true"),
        )
    }

    @Test
    fun keeps_empty_trailing_cells() {
        assertEquals(listOf("a@b.com", "", ""), parse_csv_row("a@b.com,,"))
    }

    @Test
    fun reads_headerless_files_from_the_first_column() {
        val rows = parse_alias_csv("shopping@astermail.org\nnews@astermail.org")

        assertEquals(listOf("shopping", "news"), rows.map { it.local_part })
    }

    @Test
    fun strips_the_byte_order_mark_before_parsing() {
        val rows = parse_alias_csv("${UTF8_BOM}shopping@astermail.org")

        assertEquals(listOf("shopping"), rows.map { it.local_part })
    }

    @Test
    fun resolves_alias_note_and_enabled_headers_by_name() {
        val csv = "enabled,note,address\r\nfalse,Store signups,Shopping@AsterMail.org\r\n"
        val rows = parse_alias_csv(csv)

        assertEquals(1, rows.size)
        assertEquals("shopping", rows[0].local_part)
        assertEquals("astermail.org", rows[0].original_domain)
        assertEquals("Store signups", rows[0].display_name)
        assertEquals(false, rows[0].enabled)
    }

    @Test
    fun accepts_every_header_spelling_for_the_address_column() {
        for (header in listOf("alias", "email", "address")) {
            val rows = parse_alias_csv("$header\nnews@astermail.org")

            assertEquals(listOf("news"), rows.map { it.local_part })
        }
    }

    @Test
    fun treats_a_blank_enabled_cell_as_unspecified() {
        val rows = parse_alias_csv("address,enabled\nnews@astermail.org,\n")

        assertNull(rows[0].enabled)
    }

    @Test
    fun reads_every_disabled_spelling_as_disabled() {
        for (value in listOf("false", "0", "no", "off", "disabled", "inactive", "OFF")) {
            val rows = parse_alias_csv("address,enabled\nnews@astermail.org,$value\n")

            assertEquals(false, rows[0].enabled)
        }

        val enabled = parse_alias_csv("address,enabled\nnews@astermail.org,yes\n")

        assertEquals(true, enabled[0].enabled)
    }

    @Test
    fun strips_the_formula_guard_our_export_adds() {
        val rows = parse_alias_csv("address,note\n'-news@astermail.org,'=1+1\n")

        assertEquals("news", rows[0].local_part)
        assertEquals("=1+1", rows[0].display_name)
    }

    @Test
    fun keeps_a_real_leading_apostrophe() {
        assertEquals("'it's fine", strip_formula_guard("'it's fine"))
        assertEquals("=1+1", strip_formula_guard("'=1+1"))
    }

    @Test
    fun drops_duplicate_addresses_and_rows_without_an_address() {
        val rows = parse_alias_csv(
            "address\nnews@astermail.org\nNEWS@astermail.org\nnot-an-address\nshopping@astermail.org\n",
        )

        assertEquals(listOf("news", "shopping"), rows.map { it.local_part })
    }

    @Test
    fun trims_punctuation_from_the_edges_of_a_local_part() {
        assertEquals("news", sanitize_import_local_part(".news-"))
        assertEquals("news", sanitize_import_local_part("--news--"))
        assertEquals("news.letter", sanitize_import_local_part("-news.letter-."))
    }

    @Test
    fun rejects_local_parts_that_the_server_would_refuse() {
        assertFalse(is_valid_import_local_part("ab", is_system_domain = true))
        assertTrue(is_valid_import_local_part("ab", is_system_domain = false))
        assertFalse(is_valid_import_local_part("", is_system_domain = false))
        assertFalse(is_valid_import_local_part("a".repeat(65), is_system_domain = false))
        assertFalse(is_valid_import_local_part("news..letter", is_system_domain = false))
        assertFalse(is_valid_import_local_part("12345", is_system_domain = false))
        assertFalse(is_valid_import_local_part("news letter", is_system_domain = false))
        assertFalse(is_valid_import_local_part("admin", is_system_domain = true))
        assertTrue(is_valid_import_local_part("admin", is_system_domain = false))
        assertTrue(is_valid_import_local_part("news.letter-2026", is_system_domain = true))
    }

    @Test
    fun preview_retargets_every_row_at_the_chosen_domain() {
        val rows = listOf(
            ParsedImportRow("news", "example.net"),
            ParsedImportRow("shopping", "example.net"),
        )

        val preview = build_import_preview(rows, "astermail.org", emptyMap(), emptyMap())

        assertEquals(
            listOf("news@astermail.org", "shopping@astermail.org"),
            preview.map { it.address },
        )
        assertTrue(preview.all { it.status == ImportRowStatus.WillImport })
    }

    @Test
    fun preview_marks_existing_aliases_and_carries_their_id() {
        val preview = build_import_preview(
            listOf(ParsedImportRow("news", "example.net")),
            "astermail.org",
            mapOf("news@astermail.org" to "alias-1"),
            emptyMap(),
        )

        assertEquals(ImportRowStatus.Exists, preview[0].status)
        assertEquals("alias-1", preview[0].existing_id)
        assertNull(preview[0].existing_domain_id)
    }

    @Test
    fun preview_marks_existing_domain_addresses_and_carries_both_ids() {
        val preview = build_import_preview(
            listOf(ParsedImportRow("news", "astermail.org")),
            "example.com",
            emptyMap(),
            mapOf("news@example.com" to ("address-1" to "domain-1")),
        )

        assertEquals(ImportRowStatus.Exists, preview[0].status)
        assertEquals("address-1", preview[0].existing_id)
        assertEquals("domain-1", preview[0].existing_domain_id)
    }

    @Test
    fun preview_applies_the_length_rule_of_the_target_domain() {
        val rows = listOf(ParsedImportRow("ab", "example.net"))

        assertEquals(
            ImportRowStatus.Invalid,
            build_import_preview(rows, "astermail.org", emptyMap(), emptyMap())[0].status,
        )
        assertEquals(
            ImportRowStatus.WillImport,
            build_import_preview(rows, "example.com", emptyMap(), emptyMap())[0].status,
        )
    }

    @Test
    fun detects_an_encrypted_export_before_parsing_it() {
        assertTrue(is_encrypted_vault_export("{\"encrypted\":true,\"vaults\":{}}"))
        assertFalse(is_encrypted_vault_export("{\"encrypted\":false}"))
        assertFalse(is_encrypted_vault_export("address\nnews@astermail.org"))
    }

    @Test
    fun reads_aliases_out_of_a_vault_export() {
        val json = """
            {
              "encrypted": false,
              "vaults": {
                "v1": {
                  "items": [
                    {
                      "state": 1,
                      "data": {
                        "type": "alias",
                        "metadata": { "name": "Shopping", "note": "Store signups" },
                        "content": { "aliasEmail": "Shopping@Example.net" }
                      }
                    },
                    {
                      "state": 2,
                      "data": {
                        "type": "alias",
                        "metadata": { "name": "Deleted" },
                        "content": { "aliasEmail": "deleted@example.net" }
                      }
                    },
                    {
                      "state": 1,
                      "data": {
                        "type": "login",
                        "metadata": { "name": "Bank" },
                        "content": { "aliasEmail": "bank@example.net" }
                      }
                    }
                  ]
                }
              }
            }
        """.trimIndent()

        val rows = parse_vault_json(json)

        assertEquals(1, rows.size)
        assertEquals("shopping", rows[0].local_part)
        assertEquals("example.net", rows[0].original_domain)
        assertEquals("Shopping", rows[0].display_name)
    }

    @Test
    fun falls_back_to_the_note_when_a_vault_item_has_no_name() {
        val json = """
            {
              "vaults": {
                "v1": {
                  "items": [
                    {
                      "data": {
                        "type": "alias",
                        "metadata": { "name": "", "note": "Store signups" },
                        "content": { "aliasEmail": "news@example.net" }
                      }
                    }
                  ]
                }
              }
            }
        """.trimIndent()

        assertEquals("Store signups", parse_vault_json(json)[0].display_name)
    }

    @Test
    fun malformed_input_yields_no_rows_instead_of_throwing() {
        assertTrue(parse_vault_json("not json").isEmpty())
        assertTrue(parse_vault_json("{\"encrypted\":true,\"vaults\":{}}").isEmpty())
        assertTrue(parse_alias_csv("").isEmpty())
        assertTrue(parse_alias_csv("address\n").isEmpty())
    }

    @Test
    fun picks_the_parser_from_the_file_name() {
        val csv = "address\nnews@astermail.org"
        val json = "{\"vaults\":{\"v1\":{\"items\":[{\"data\":{\"type\":\"alias\"," +
            "\"content\":{\"aliasEmail\":\"news@example.net\"}}}]}}}"

        assertEquals("astermail.org", parse_import_file(csv, "Aliases.CSV")[0].original_domain)
        assertEquals("example.net", parse_import_file(json, "Export.JSON")[0].original_domain)
    }
}
