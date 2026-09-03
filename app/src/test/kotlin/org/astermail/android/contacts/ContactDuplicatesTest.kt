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
import org.junit.Assert.assertTrue
import org.junit.Test

class ContactDuplicatesTest {

    private fun contact(
        id: String,
        name: String = "",
        email: String = "",
        phone: String = "",
        company: String = "",
        work_email: String = "",
        notes: String = "",
        is_favorite: Boolean = false,
        groups: List<String> = emptyList(),
    ) = Contact(
        id = id,
        name = name,
        email = email,
        phone = phone,
        company = company,
        work_email = work_email,
        notes = notes,
        is_favorite = is_favorite,
        groups = groups,
    )

    @Test
    fun `email identity strips plus tags and lowercases`() {
        assertEquals("amara@example.com", email_identity("Amara+personal@Example.com"))
        assertEquals("amara@example.com", email_identity("  amara@example.com  "))
        assertEquals("not-an-email", email_identity("Not-An-Email"))
    }

    @Test
    fun `normalize phone keeps the last ten digits`() {
        assertEquals("4155550142", normalize_phone("+1 (415) 555-0142"))
        assertEquals("4155550142", normalize_phone("415.555.0142"))
        assertEquals("", normalize_phone("555 01"))
        assertEquals("", normalize_phone(null))
    }

    @Test
    fun `identical email scores a perfect match`() {
        val left = contact("a", name = "Amara Okonkwo", email = "amara@example.com")
        val right = contact("b", name = "A. Okonkwo", email = "AMARA@example.com")

        assertEquals(100, similarity_score(left, right))
    }

    @Test
    fun `plus tagged email scores lower than an exact match`() {
        val left = contact("a", name = "Amara Okonkwo", email = "amara@example.com")
        val right = contact("b", name = "Amara Okonkwo", email = "amara+news@example.com")
        val other = contact("c", name = "Different Person", email = "amara+news@example.com")

        assertEquals(95, similarity_score(left, right))
        assertEquals(85, similarity_score(left, other))
    }

    @Test
    fun `name and phone match without a shared email`() {
        val left = contact("a", name = "Kofi Mensah", phone = "+1 (415) 555-0142")
        val right = contact("b", name = "kofi  mensah", phone = "415.555.0142")

        assertEquals(90, similarity_score(left, right))
    }

    @Test
    fun `same name alone stays below the threshold`() {
        val left = contact("a", name = "Grace Hopper", email = "grace@one.example")
        val right = contact("b", name = "Grace Hopper", email = "grace@two.example")

        assertEquals(60, similarity_score(left, right))
        assertTrue(similarity_score(left, right) < DUPLICATE_THRESHOLD)
    }

    @Test
    fun `same name and company reaches the threshold`() {
        val left = contact("a", name = "Grace Hopper", email = "grace@one.example", company = "Compilerworks")
        val right = contact("b", name = "Grace Hopper", email = "grace@two.example", company = "compilerworks")

        assertEquals(80, similarity_score(left, right))
    }

    @Test
    fun `unrelated contacts score zero`() {
        val left = contact("a", name = "Ada Lovelace", email = "ada@example.com")
        val right = contact("b", name = "Radia Perlman", email = "radia@example.org")

        assertEquals(0, similarity_score(left, right))
    }

    @Test
    fun `work email participates in matching`() {
        val left = contact("a", name = "Ines Ferreira", email = "ines@home.example")
        val right = contact("b", name = "Ines F", work_email = "ines@home.example")

        assertEquals(100, similarity_score(left, right))
    }

    @Test
    fun `clusters group transitive duplicates`() {
        val contacts = listOf(
            contact("a", name = "Amara Okonkwo", email = "amara@example.com"),
            contact("b", name = "Amara Okonkwo", email = "amara+personal@example.com"),
            contact("c", name = "Amara Okonkwo", email = "amara@example.com", phone = "+1 415 555 0142"),
            contact("d", name = "Ola Bergstrom", email = "ola@nordic.example"),
        )

        val clusters = find_duplicate_clusters(contacts)

        assertEquals(1, clusters.size)
        assertEquals(setOf("a", "b", "c"), clusters[0].contacts.map { it.id }.toSet())
        assertEquals(3, count_duplicate_contacts(clusters))
    }

    @Test
    fun `clusters are sorted by descending score`() {
        val contacts = listOf(
            contact("a", name = "Weak One", phone = "+1 206 555 0135"),
            contact("b", name = "Weak Two", phone = "+1 206 555 0135"),
            contact("c", name = "Strong", email = "strong@example.com"),
            contact("d", name = "Strong", email = "STRONG@example.com"),
        )

        val clusters = find_duplicate_clusters(contacts)

        assertEquals(2, clusters.size)
        assertEquals(100, clusters[0].score)
        assertEquals(75, clusters[1].score)
    }

    @Test
    fun `no duplicates yields no clusters`() {
        val contacts = listOf(
            contact("a", name = "Ada Lovelace", email = "ada@example.com"),
            contact("b", name = "Radia Perlman", email = "radia@example.org"),
        )

        assertTrue(find_duplicate_clusters(contacts).isEmpty())
        assertTrue(find_duplicate_clusters(listOf(contacts[0])).isEmpty())
        assertTrue(find_duplicate_clusters(emptyList()).isEmpty())
    }

    @Test
    fun `merge keeps the first non blank value from the ordered list`() {
        val primary = contact("a", name = "", email = "amara@example.com", groups = listOf("Work"))
        val secondary = contact(
            "b",
            name = "Amara Okonkwo",
            email = "amara+personal@example.com",
            phone = "+1 415 555 0142",
            company = "Northwind Labs",
            groups = listOf("work", "Friends"),
        )

        val merged = merge_contacts(listOf(primary, secondary))

        assertEquals("a", merged.id)
        assertEquals("Amara Okonkwo", merged.name)
        assertEquals("amara@example.com", merged.email)
        assertEquals("amara+personal@example.com", merged.work_email)
        assertEquals("+1 415 555 0142", merged.phone)
        assertEquals("Northwind Labs", merged.company)
        assertEquals(listOf("Work", "Friends"), merged.groups)
    }

    @Test
    fun `merge joins distinct notes and keeps a favorite`() {
        val primary = contact("a", name = "Kofi", email = "kofi@example.org", notes = "Met at the summit.")
        val secondary = contact("b", name = "Kofi", email = "kofi@example.org", notes = "Met at the summit.")
        val third = contact("c", name = "Kofi", email = "kofi@example.org", notes = "Prefers text.", is_favorite = true)

        val merged = merge_contacts(listOf(primary, secondary, third))

        assertEquals("Met at the summit.\n\nPrefers text.", merged.notes)
        assertTrue(merged.is_favorite)
    }
}
