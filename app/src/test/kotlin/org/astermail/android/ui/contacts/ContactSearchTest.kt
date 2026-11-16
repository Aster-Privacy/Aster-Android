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

import org.astermail.android.contacts.ContactRelation
import org.astermail.android.contacts.ContactSocial
import org.astermail.android.contacts.RelationKind
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ContactSearchTest {

    private val jane = Contact(
        id = "1",
        name = "José Doe",
        email = "jane@home.example",
        phone = "+1 (555) 010-2030",
        company = "Acme",
        work_email = "jdoe@acme.example",
        emails = listOf(ContactEntry(value = "side@other.example", type = "other")),
        phones = listOf(ContactEntry(value = "555 999 8888", type = "other")),
    )

    @Test
    fun blank_query_matches_everything() {
        assertTrue(contact_matches_query(jane, ""))
        assertTrue(contact_matches_query(jane, "   "))
    }

    @Test
    fun query_is_trimmed_and_folded() {
        assertTrue(contact_matches_query(jane, " jose "))
        assertTrue(contact_matches_query(jane, "JOSÉ DOE"))
    }

    @Test
    fun secondary_addresses_match() {
        assertTrue(contact_matches_query(jane, "jdoe"))
        assertTrue(contact_matches_query(jane, "other.example"))
    }

    @Test
    fun phone_matches_regardless_of_formatting() {
        assertTrue(contact_matches_query(jane, "5550102030"))
        assertTrue(contact_matches_query(jane, "555-999"))
        assertFalse(contact_matches_query(jane, "55"))
        assertFalse(contact_matches_query(jane, "4440102030"))
    }

    @Test
    fun unrelated_query_does_not_match() {
        assertFalse(contact_matches_query(jane, "zebra"))
    }

    @Test
    fun empty_reason_names_the_active_filter() {
        assertEquals(ContactsEmptyReason.no_contacts, contacts_empty_reason("", false))
        assertEquals(ContactsEmptyReason.no_match, contacts_empty_reason("a", false))
        assertEquals(ContactsEmptyReason.no_favorites, contacts_empty_reason(" ", true))
        assertEquals(ContactsEmptyReason.no_favorites_match, contacts_empty_reason("a", true))
    }

    @Test
    fun `query matches relation values labels and kind names`() {
        val contact = Contact(
            id = "r",
            name = "Relation Android Sync",
            email = "r@example.com",
            relations = listOf(
                ContactRelation(RelationKind.MOTHER, "Ana Lima"),
                ContactRelation(RelationKind.CUSTOM, "Rui", "Godparent"),
            ),
        )
        val names: (ContactRelation) -> String = { if (it.kind == RelationKind.MOTHER) "Mutter" else it.label }
        assertTrue(contact_matches_query(contact, "ana lima", names))
        assertTrue(contact_matches_query(contact, "godpar", names))
        assertTrue(contact_matches_query(contact, "mutter", names))
        assertFalse(contact_matches_query(contact, "father", names))
    }

    @Test
    fun `query matches custom social links`() {
        val contact = Contact(id = "s", name = "Social", email = "s@example.com", social_networks = listOf(ContactSocial.custom("Fediverse", "https://social.example/@q")))
        assertTrue(contact_matches_query(contact, "fediverse"))
        assertTrue(contact_matches_query(contact, "social.example"))
        assertFalse(contact_matches_query(contact, "nothing"))
    }
}
