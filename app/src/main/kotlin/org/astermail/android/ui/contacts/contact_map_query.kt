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

package org.astermail.android.ui.contacts

import java.net.URLEncoder

private val map_query_whitespace = Regex("\\s+")

fun build_contact_map_query(postal: ContactPostal): String =
    listOf(postal.street, postal.city, postal.region, postal.postal_code, postal.country)
        .flatMap { it.split('\n') }
        .map { it.replace(map_query_whitespace, " ").trim().trim(',').trim() }
        .filter { it.isNotEmpty() }
        .joinToString(", ")

fun encode_contact_map_query(query: String): String =
    URLEncoder.encode(query, "UTF-8").replace("+", "%20")

fun build_contact_geo_uri(query: String): String? {
    if (query.isBlank()) return null
    return "geo:0,0?q=" + encode_contact_map_query(query)
}

fun build_contact_map_web_url(query: String): String? {
    if (query.isBlank()) return null
    return "https://www.openstreetmap.org/search?query=" + encode_contact_map_query(query)
}
