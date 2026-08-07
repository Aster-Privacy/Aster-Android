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

import org.json.JSONObject

data class ParsedImportRow(
    val local_part: String,
    val original_domain: String,
    val display_name: String? = null,
    val enabled: Boolean? = null,
)

enum class ImportRowStatus {
    WillImport,
    Exists,
    Invalid,
}

data class ImportPreviewRow(
    val local_part: String,
    val display_name: String? = null,
    val enabled: Boolean? = null,
    val address: String,
    val domain: String,
    val status: ImportRowStatus,
    val existing_id: String? = null,
    val existing_domain_id: String? = null,
)

data class ImportOutcome(
    val created: Int,
    val skipped: Int,
    val failed: Int,
)

val SYSTEM_ALIAS_DOMAINS = setOf("astermail.org", "aster.cx")

private val RESERVED_ALIAS_NAMES = setOf(
    "noreply",
    "admin",
    "administrator",
    "postmaster",
    "webmaster",
    "support",
    "abuse",
    "mailer",
    "daemon",
    "root",
    "hostmaster",
    "info",
    "contact",
    "help",
    "system",
    "mail",
    "no-reply",
)

private val ALIAS_HEADERS = setOf("alias", "email", "address")
private val NOTE_HEADERS = setOf("note", "description", "display_name")
private val ENABLED_HEADERS = setOf("enabled", "active")
private val DISABLED_VALUES = setOf("false", "0", "no", "off", "disabled", "inactive")

private val LOCAL_PART_PATTERN = Regex("^[a-z0-9][a-z0-9._-]*[a-z0-9]$|^[a-z0-9]$")
private val NUMERIC_ONLY_PATTERN = Regex("^[0-9]+$")
private val EDGE_PUNCTUATION_PATTERN = Regex("^[._-]+|[._-]+$")

fun strip_formula_guard(value: String): String {
    if (!value.startsWith("'")) return value

    val rest = value.substring(1)
    val guarded = neutralize_formula(rest) != rest

    return if (guarded) rest else value
}

fun sanitize_import_local_part(local_part: String): String =
    local_part.replace(EDGE_PUNCTUATION_PATTERN, "")

fun is_valid_import_local_part(local_part: String, is_system_domain: Boolean): Boolean {
    val normalized = local_part.lowercase()

    if (normalized.isEmpty()) return false
    if (is_system_domain && normalized.length < 3) return false
    if (normalized.length > 64) return false
    if (!LOCAL_PART_PATTERN.matches(normalized)) return false
    if (normalized.contains("..")) return false
    if (NUMERIC_ONLY_PATTERN.matches(normalized)) return false
    if (is_system_domain && RESERVED_ALIAS_NAMES.contains(normalized)) return false

    return true
}

fun parse_csv_row(line: String): List<String> {
    val cols = mutableListOf<String>()
    val current = StringBuilder()
    var in_quotes = false
    var index = 0

    while (index < line.length) {
        val ch = line[index]

        when {
            ch == '"' -> {
                if (in_quotes && index + 1 < line.length && line[index + 1] == '"') {
                    current.append('"')
                    index += 1
                } else {
                    in_quotes = !in_quotes
                }
            }
            ch == ',' && !in_quotes -> {
                cols.add(current.toString())
                current.setLength(0)
            }
            else -> current.append(ch)
        }

        index += 1
    }

    cols.add(current.toString())

    return cols
}

private fun split_address(raw: String): Pair<String, String>? {
    val at = raw.lastIndexOf('@')
    if (at <= 0) return null

    val local_part = sanitize_import_local_part(raw.substring(0, at).lowercase())
    val domain = raw.substring(at + 1).lowercase()

    if (local_part.isEmpty() || domain.isEmpty()) return null

    return local_part to domain
}

fun parse_alias_csv(text: String): List<ParsedImportRow> {
    val without_bom = if (text.startsWith(UTF8_BOM)) text.substring(1) else text
    val lines = without_bom.split(Regex("\r?\n")).filter { it.isNotBlank() }

    if (lines.isEmpty()) return emptyList()

    val first_cols = parse_csv_row(lines[0])
    val first_row_is_data = first_cols.firstOrNull().orEmpty().contains("@")
    val header = if (first_row_is_data) emptyList() else first_cols.map { it.lowercase().trim() }
    val data_lines = if (first_row_is_data) lines else lines.drop(1)

    if (data_lines.isEmpty()) return emptyList()

    val alias_col = header.indexOfFirst { ALIAS_HEADERS.contains(it) }
    val note_col = header.indexOfFirst { NOTE_HEADERS.contains(it) }
    val enabled_col = header.indexOfFirst { ENABLED_HEADERS.contains(it) }

    val rows = mutableListOf<ParsedImportRow>()
    val seen = mutableSetOf<String>()

    for (line in data_lines) {
        val cols = parse_csv_row(line)
        val raw_cell = when {
            alias_col >= 0 -> cols.getOrNull(alias_col)
            else -> cols.firstOrNull()
        }
        val raw_address = strip_formula_guard(raw_cell.orEmpty()).trim()

        if (!raw_address.contains("@")) continue

        val (local_part, domain) = split_address(raw_address) ?: continue
        val key = "$local_part@$domain"

        if (!seen.add(key)) continue

        val display_name = note_col.takeIf { it >= 0 }
            ?.let { cols.getOrNull(it) }
            ?.let { strip_formula_guard(it).trim() }
            ?.takeIf { it.isNotEmpty() }

        val enabled = enabled_col.takeIf { it >= 0 }
            ?.let { cols.getOrNull(it) }
            ?.trim()
            ?.lowercase()
            ?.takeIf { it.isNotEmpty() }
            ?.let { !DISABLED_VALUES.contains(it) }

        rows.add(ParsedImportRow(local_part, domain, display_name, enabled))
    }

    return rows
}

fun is_encrypted_vault_export(text: String): Boolean = try {
    JSONObject(text).optBoolean("encrypted", false)
} catch (_: Throwable) {
    false
}

fun parse_vault_json(text: String): List<ParsedImportRow> {
    val root = try {
        JSONObject(text)
    } catch (_: Throwable) {
        return emptyList()
    }

    if (root.optBoolean("encrypted", false)) return emptyList()

    val vaults = root.optJSONObject("vaults") ?: return emptyList()
    val rows = mutableListOf<ParsedImportRow>()
    val seen = mutableSetOf<String>()
    val vault_keys = vaults.keys()

    while (vault_keys.hasNext()) {
        val vault = vaults.optJSONObject(vault_keys.next()) ?: continue
        val items = vault.optJSONArray("items") ?: continue

        for (index in 0 until items.length()) {
            val item = items.optJSONObject(index) ?: continue

            if (item.optInt("state", 0) == 2) continue

            val data = item.optJSONObject("data") ?: continue

            if (data.optString("type") != "alias") continue

            val alias_email = data.optJSONObject("content")
                ?.optString("aliasEmail")
                ?.trim()
                ?.lowercase()
                .orEmpty()

            if (!alias_email.contains("@")) continue

            val (local_part, domain) = split_address(alias_email) ?: continue
            val key = "$local_part@$domain"

            if (!seen.add(key)) continue

            val metadata = data.optJSONObject("metadata")
            val name = metadata?.optString("name")?.trim().orEmpty()
            val note = metadata?.optString("note")?.trim().orEmpty()
            val display_name = name.ifEmpty { note }.takeIf { it.isNotEmpty() }

            rows.add(ParsedImportRow(local_part, domain, display_name))
        }
    }

    return rows
}

fun parse_import_file(text: String, file_name: String): List<ParsedImportRow> =
    if (file_name.lowercase().endsWith(".json")) parse_vault_json(text) else parse_alias_csv(text)

fun build_import_preview(
    rows: List<ParsedImportRow>,
    target_domain: String,
    existing_aliases: Map<String, String>,
    existing_domain_addresses: Map<String, Pair<String, String>>,
): List<ImportPreviewRow> {
    val is_system = SYSTEM_ALIAS_DOMAINS.contains(target_domain)

    return rows.map { row ->
        val address = "${row.local_part}@$target_domain"
        val existing_alias_id = existing_aliases[address]
        val existing_domain_address = existing_domain_addresses[address]

        val status = when {
            !is_valid_import_local_part(row.local_part, is_system) -> ImportRowStatus.Invalid
            existing_alias_id != null || existing_domain_address != null -> ImportRowStatus.Exists
            else -> ImportRowStatus.WillImport
        }

        ImportPreviewRow(
            local_part = row.local_part,
            display_name = row.display_name,
            enabled = row.enabled,
            address = address,
            domain = target_domain,
            status = status,
            existing_id = existing_alias_id ?: existing_domain_address?.first,
            existing_domain_id = existing_domain_address?.second,
        )
    }
}
