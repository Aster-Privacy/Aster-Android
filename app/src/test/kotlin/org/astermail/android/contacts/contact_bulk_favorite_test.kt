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

package org.astermail.android.contacts

import org.astermail.android.ui.contacts.Contact
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Test
import java.time.LocalDate
import java.time.ZoneId
import java.time.ZoneOffset

class contact_bulk_favorite_test {

    private fun contact(id: String, favorite: Boolean = false, created_at: String = "", name: String = id) =
        Contact(id = id, name = name, email = "", is_favorite = favorite, created_at = created_at)

    @Test
    fun `mixed selection adds to favorites`() {
        assertEquals(true, favorite_target(listOf(contact("a", true), contact("b"))))
        assertEquals(true, favorite_target(listOf(contact("a"))))
    }

    @Test
    fun `all favorite selection removes from favorites`() {
        assertEquals(false, favorite_target(listOf(contact("a", true), contact("b", true))))
    }

    @Test
    fun `empty selection has no favorite action`() {
        assertNull(favorite_target(emptyList()))
    }

    @Test
    fun `only contacts that differ are saved`() {
        val selected = listOf(contact("a", true), contact("b"), contact("c"))
        assertEquals(listOf("b", "c"), contacts_needing_favorite(selected, true).map { it.id })
        assertEquals(listOf("a"), contacts_needing_favorite(selected, false).map { it.id })
    }

    @Test
    fun `apply favorite updates only the given ids and rolls back`() {
        val all = listOf(contact("a"), contact("b"), contact("c", true))
        val optimistic = apply_favorite(all, setOf("a", "b"), true)
        assertEquals(listOf(true, true, true), optimistic.map { it.is_favorite })
        val rolled = apply_favorite(optimistic, setOf("b"), false)
        assertEquals(listOf(true, false, true), rolled.map { it.is_favorite })
        assertSame(all[2], apply_favorite(all, setOf("c"), true)[2])
    }

    @Test
    fun `recently added hides without timestamps`() {
        assertEquals(false, has_recently_added(listOf(contact("a"), contact("b", created_at = "not a date"))))
        assertEquals(true, has_recently_added(listOf(contact("a"), contact("b", created_at = "2026-11-20T10:00:00Z"))))
    }

    @Test
    fun `recently added sorts newest first and skips missing timestamps`() {
        val list = listOf(
            contact("old", created_at = "2026-01-01T00:00:00Z"),
            contact("none"),
            contact("new", created_at = "2026-11-25T08:00:00.123456+00:00"),
            contact("mid", created_at = "2026-06-01T12:00:00+02:00"),
        )
        assertEquals(listOf("new", "mid", "old"), recently_added_sorted(list).map { it.id })
    }

    @Test
    fun `recently added groups by today yesterday and date`() {
        val zone: ZoneId = ZoneOffset.UTC
        val today = LocalDate.of(2026, 11, 25)
        val list = listOf(
            contact("t1", created_at = "2026-11-25T08:00:00Z"),
            contact("t2", created_at = "2026-11-25T01:00:00Z"),
            contact("y", created_at = "2026-11-24T23:59:59Z"),
            contact("d1", created_at = "2026-11-02T10:00:00Z"),
            contact("d2", created_at = "2026-11-02T09:00:00Z"),
        )
        val groups = group_recently_added(list, today, zone)
        assertEquals(listOf(RecentBucket.TODAY, RecentBucket.YESTERDAY, RecentBucket.DATE), groups.map { it.bucket })
        assertEquals(listOf(listOf("t1", "t2"), listOf("y"), listOf("d1", "d2")), groups.map { g -> g.contacts.map { it.id } })
        assertEquals(LocalDate.of(2026, 11, 2), groups[2].date)
    }

    @Test
    fun `recently added uses the local day`() {
        val zone = ZoneId.of("America/New_York")
        val today = LocalDate.of(2026, 11, 25)
        val groups = group_recently_added(listOf(contact("a", created_at = "2026-11-25T03:00:00Z")), today, zone)
        assertEquals(RecentBucket.YESTERDAY, groups.single().bucket)
    }
}
