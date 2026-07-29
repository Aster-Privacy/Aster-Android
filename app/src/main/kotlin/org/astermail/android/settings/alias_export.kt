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

enum class AliasExportSource {
    Aliases,
    DomainAddresses,
    Directories,
    Ghost,
}

const val UTF8_BOM = "\uFEFF"
const val CSV_LINE_BREAK = "\r\n"

private val FORMULA_TRIGGERS = setOf('=', '+', '-', '@')

val ALIAS_COLUMNS = listOf(
    "address",
    "display_name",
    "note",
    "websites",
    "enabled",
    "created_at",
)

val DOMAIN_ADDRESS_COLUMNS = listOf("address", "enabled")

val DIRECTORY_COLUMNS = listOf("directory", "domain", "auto_create", "color", "created_at")

val GHOST_COLUMNS = listOf("address", "enabled", "expires_at", "created_at")

fun columns_for(source: AliasExportSource): List<String> = when (source) {
    AliasExportSource.Aliases -> ALIAS_COLUMNS
    AliasExportSource.DomainAddresses -> DOMAIN_ADDRESS_COLUMNS
    AliasExportSource.Directories -> DIRECTORY_COLUMNS
    AliasExportSource.Ghost -> GHOST_COLUMNS
}

fun export_file_name(source: AliasExportSource, date_stamp: String): String = when (source) {
    AliasExportSource.Aliases -> "aster-aliases-$date_stamp.csv"
    AliasExportSource.DomainAddresses -> "aster-domain-addresses-$date_stamp.csv"
    AliasExportSource.Directories -> "aster-directories-$date_stamp.csv"
    AliasExportSource.Ghost -> "aster-ghost-aliases-$date_stamp.csv"
}

private fun is_formula_start(value: String): Boolean {
    if (value.isEmpty()) return false

    val first = value[0]

    if (first == '\t' || first == '\r' || first == '\n') return true

    val first_visible = value.trimStart().firstOrNull() ?: return false

    return FORMULA_TRIGGERS.contains(first_visible)
}

private fun is_guarded(value: String): Boolean {
    if (!value.startsWith("'")) return false

    var index = 0

    while (index < value.length && value[index] == '\'') index += 1

    return is_formula_start(value.substring(index))
}

fun neutralize_formula(value: String): String =
    if (is_formula_start(value) || is_guarded(value)) "'$value" else value

fun escape_csv_cell(value: String): String {
    val guarded = neutralize_formula(value)

    return "\"" + guarded.replace("\"", "\"\"") + "\""
}

fun build_csv(headers: List<String>, rows: List<List<String>>): String {
    val builder = StringBuilder(UTF8_BOM)

    for (row in listOf(headers) + rows) {
        builder.append(row.joinToString(",") { escape_csv_cell(it) })
        builder.append(CSV_LINE_BREAK)
    }

    return builder.toString()
}

fun is_exportable_alias(alias: AliasInfo): Boolean =
    !alias.decryption_failed && alias.address.contains("@") && !alias.address.startsWith("@")

fun is_exportable_domain_address(address: CustomDomainAddressInfo): Boolean =
    !address.decryption_failed &&
        address.address.contains("@") &&
        !address.address.startsWith("@")

fun is_exportable_ghost(alias: GhostAlias): Boolean =
    !alias.decryption_failed && alias.decrypted_address.contains("@")

fun build_alias_rows(aliases: List<AliasInfo>, columns: List<String>): List<List<String>> =
    aliases.map { alias ->
        columns.map { column ->
            when (column) {
                "address" -> alias.address
                "display_name" -> alias.encrypted_display_name.orEmpty()
                "note" -> alias.encrypted_note.orEmpty()
                "websites" -> alias.encrypted_websites.orEmpty()
                "enabled" -> alias.is_enabled.toString()
                "created_at" -> alias.created_at
                else -> ""
            }
        }
    }

fun build_domain_address_rows(
    addresses: List<CustomDomainAddressInfo>,
    columns: List<String>,
): List<List<String>> =
    addresses.map { address ->
        columns.map { column ->
            when (column) {
                "address" -> address.address
                "enabled" -> address.is_enabled.toString()
                else -> ""
            }
        }
    }

fun build_directory_rows(
    directories: List<AliasDirectory>,
    columns: List<String>,
): List<List<String>> =
    directories.map { directory ->
        columns.map { column ->
            when (column) {
                "directory" -> directory.decrypted_label
                "domain" -> directory.domain
                "auto_create" -> directory.auto_create_enabled.toString()
                "color" -> directory.color.orEmpty()
                "created_at" -> directory.created_at
                else -> ""
            }
        }
    }

fun build_ghost_rows(aliases: List<GhostAlias>, columns: List<String>): List<List<String>> =
    aliases.map { alias ->
        columns.map { column ->
            when (column) {
                "address" -> alias.address
                "enabled" -> alias.is_enabled.toString()
                "expires_at" -> alias.expires_at.orEmpty()
                "created_at" -> alias.created_at.orEmpty()
                else -> ""
            }
        }
    }
