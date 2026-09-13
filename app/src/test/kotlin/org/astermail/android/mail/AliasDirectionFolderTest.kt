//
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
// MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
// GNU Affero General Public License for more details.
//
// You should have received a copy of the GNU Affero General Public License
// along with this program.  If not, see <https://www.gnu.org/licenses/>.
//

package org.astermail.android.mail

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class AliasDirectionFolderTest {

    @Test
    fun all_direction_keeps_the_legacy_folder_string() {
        assertEquals("routing:abc+/=", mail_folder_for_filter(filter_kind_alias, "abc+/="))
        assertEquals("routing:abc", alias_routing_folder("abc", alias_direction_all))
    }

    @Test
    fun sent_and_received_directions_append_a_suffix() {
        assertEquals("routing:abc|sent", mail_folder_for_filter(filter_kind_alias, "abc", alias_direction_sent))
        assertEquals("routing:abc|received", alias_routing_folder("abc", alias_direction_received))
    }

    @Test
    fun parsing_round_trips_every_direction() {
        listOf(alias_direction_all, alias_direction_received, alias_direction_sent).forEach { direction ->
            val scope = parse_alias_routing_folder(alias_routing_folder("tok/en+==", direction))
            assertEquals(alias_routing_scope("tok/en+==", direction), scope)
        }
    }

    @Test
    fun unknown_suffix_falls_back_to_all() {
        assertEquals(alias_routing_scope("abc", alias_direction_all), parse_alias_routing_folder("routing:abc|bogus"))
        assertEquals(alias_direction_all, normalize_alias_direction(null))
    }

    @Test
    fun non_routing_folders_are_not_parsed() {
        assertNull(parse_alias_routing_folder("label:abc"))
        assertNull(parse_alias_routing_folder("inbox"))
    }

    @Test
    fun direction_maps_to_the_list_query_value() {
        assertEquals("either", alias_direction_query(alias_direction_all))
        assertEquals("received", alias_direction_query(alias_direction_received))
        assertEquals("sent", alias_direction_query(alias_direction_sent))
    }

    @Test
    fun label_and_tag_filters_ignore_the_direction() {
        assertEquals("label:x", mail_folder_for_filter(filter_kind_label, "x", alias_direction_sent))
        assertEquals("tag:x", mail_folder_for_filter(filter_kind_tag, "x", alias_direction_sent))
    }
}
