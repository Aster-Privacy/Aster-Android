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


package org.astermail.android.contacts.sync

import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ContactCardTest {

    private val labels = DeviceLabels(sibling = "Sibling", graduation = "Graduation", wedding = "Wedding")

    private val full_card = ContactCard(
        first_name = "Ada",
        middle_name = "King",
        last_name = "Lovelace",
        prefix = "Dr.",
        suffix = "Jr.",
        phonetic_first_name = "Ay-da",
        nickname = "Countess",
        company = "Analytical Engines",
        department = "Research",
        job_title = "Mathematician",
        notes = "First program",
        birthday = "1815-12-10",
        emails = listOf(
            CardEntry("ada@example.com", "home"),
            CardEntry("ada@work.example", "work"),
            CardEntry("ada@personal.example", "personal"),
        ),
        phones = listOf(
            CardEntry("+44 20 7946 0000", "mobile"),
            CardEntry("+44 20 7946 0001", "work"),
            CardEntry("+44 20 7946 0002", "other", "Lab"),
            CardEntry("+44 20 7946 0003", "personal"),
        ),
        addresses = listOf(
            CardAddress("1 Engine Row", "Oxford", "", "OX1 1AA", "UK", "work"),
            CardAddress("12 St James's Square", "London", "", "SW1Y 4JH", "UK", "home"),
            CardAddress("2 Cottage Lane", "Bath", "", "BA1 1AA", "UK", "other", "Summer house"),
        ),
        websites = listOf(CardEntry("https://ada.example", "private")),
        relations = listOf(CardEntry("Byron", "father"), CardEntry("Augusta", "sibling")),
        messengers = listOf(CardEntry("@ada:matrix.example", "matrix")),
        dates = listOf(CardEntry("1835-07-08", "wedding"), CardEntry("1833-06-05", "graduation")),
        starred = true,
    ).normalized()

    @Test
    fun new_contact_json_round_trips() {
        assertEquals(full_card, contact_card_from_json(new_contact_json(full_card)).normalized())
    }

    @Test
    fun base_json_round_trips() {
        assertEquals(full_card, contact_card_from_base_json(full_card.to_base_json()))
    }

    @Test
    fun device_rows_round_trip() {
        val rows = device_rows_for_card(full_card, labels).map { row ->
            DeviceDataRow(row.mimetype, row.values.mapValues { (_, v) -> v?.toString() })
        }
        assertEquals(full_card, contact_card_from_device_rows(rows, starred = true, labels = labels).normalized())
    }

    @Test
    fun custom_labels_use_custom_device_type() {
        val rows = device_rows_for_card(full_card, labels)
        val phone = rows.first { it.values[android.provider.ContactsContract.CommonDataKinds.Phone.NUMBER] == "+44 20 7946 0002" }
        assertEquals(android.provider.ContactsContract.CommonDataKinds.Phone.TYPE_CUSTOM, phone.values[android.provider.ContactsContract.CommonDataKinds.Phone.TYPE])
        assertEquals("Lab", phone.values[android.provider.ContactsContract.CommonDataKinds.Phone.LABEL])
        val personal = rows.first { it.values[android.provider.ContactsContract.CommonDataKinds.Email.ADDRESS] == "ada@personal.example" }
        assertEquals("Personal", personal.values[android.provider.ContactsContract.CommonDataKinds.Email.LABEL])
    }

    @Test
    fun label_is_dropped_unless_type_is_other() {
        assertEquals("", ContactCard(phones = listOf(CardEntry("1", "work", "Desk"))).normalized().phones.single().label)
        assertEquals("Desk", ContactCard(phones = listOf(CardEntry("1", "other", " Desk "))).normalized().phones.single().label)
    }

    @Test
    fun legacy_fields_prefer_home_address_and_first_phone() {
        val json = JSONObject(new_contact_json(full_card))
        assertEquals("12 St James's Square", json.getJSONObject("address").getString("street"))
        assertEquals("+44 20 7946 0000", json.getString("phone"))
        val entry = json.getJSONArray("phone_entries").getJSONObject(2)
        assertEquals("other", entry.getString("type"))
        assertEquals("Lab", entry.getString("label"))
    }

    @Test
    fun inline_avatar_round_trips_as_fingerprint() {
        val avatar = "data:image/jpeg;base64,AAAA"
        val card = full_card.copy(photo = remote_photo_fingerprint(avatar))
        val json = new_contact_json(card, avatar)
        assertEquals(avatar, contact_inline_avatar(json))
        assertEquals(card, contact_card_from_json(json).normalized())
        val cleared = patch_contact_json(json, card, card.copy(photo = ""))
        assertFalse(JSONObject(cleared).has("avatar_url"))
    }

    @Test
    fun remote_avatar_url_is_not_treated_as_photo() {
        val json = JSONObject(new_contact_json(full_card)).put("avatar_url", "https://example.com/a.png").toString()
        assertEquals("", contact_card_from_json(json).photo)
        assertEquals(null, contact_inline_avatar(json))
    }

    @Test
    fun patch_keeps_fields_the_card_does_not_model() {
        val remote = JSONObject(new_contact_json(full_card))
            .put("groups", org.json.JSONArray().put("g1"))
            .put("avatar_url", "data:image/png;base64,AAAA")
            .put("pgp_key", "KEY")
            .toString()
        val remote_card = contact_card_from_json(remote)
        val patched = JSONObject(patch_contact_json(remote, remote_card, remote_card.copy(nickname = "Enchantress")))
        assertEquals("Enchantress", patched.getString("nickname"))
        assertEquals("g1", patched.getJSONArray("groups").getString(0))
        assertEquals("data:image/png;base64,AAAA", patched.getString("avatar_url"))
        assertEquals("KEY", patched.getString("pgp_key"))
    }

    @Test
    fun trash_marks_json_as_deleted() {
        val json = new_contact_json(full_card)
        assertFalse(is_trashed_contact_json(json))
        assertTrue(is_trashed_contact_json(trash_contact_json(json, "2026-09-29T00:00:00Z")))
    }

    @Test
    fun empty_card_has_no_content() {
        assertFalse(ContactCard(starred = true).has_content())
        assertTrue(ContactCard(emails = listOf(CardEntry("a@b.c", "home"))).has_content())
    }

    @Test
    fun only_changed_groups_are_rewritten() {
        assertEquals(listOf(DeviceGroup.NOTE), changed_device_groups(full_card, full_card.copy(notes = "Edited")))
        assertEquals(DeviceGroup.entries.toList(), changed_device_groups(null, full_card))
    }

    @Test
    fun merge_keeps_disjoint_edits() {
        val base = full_card
        val local = base.copy(nickname = "Local nick", phones = base.phones + CardEntry("+1 555 0100", "home"))
        val remote = base.copy(company = "Remote Co", emails = base.emails.drop(1))
        val merged = merge_contact_cards(base, local, remote)
        assertEquals("Local nick", merged.nickname)
        assertEquals("Remote Co", merged.company)
        assertEquals(base.phones.size + 1, merged.phones.size)
        assertEquals(base.emails.drop(1), merged.emails)
    }

    @Test
    fun merge_prefers_remote_on_same_field_conflict() {
        val merged = merge_contact_cards(full_card, full_card.copy(notes = "phone"), full_card.copy(notes = "web"))
        assertEquals("web", merged.notes)
    }

    @Test
    fun merge_list_applies_removals_and_additions_from_both_sides() {
        val base = listOf("a", "b", "c")
        assertEquals(listOf("a", "e", "d"), merge_list(base, listOf("a", "c", "d"), listOf("a", "b", "e")))
    }

    @Test
    fun merge_without_base_uses_remote_as_origin() {
        val merged = merge_contact_cards(null, full_card.copy(notes = "local"), full_card)
        assertEquals("local", merged.notes)
    }
}
