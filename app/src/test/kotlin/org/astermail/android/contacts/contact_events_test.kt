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
import org.astermail.android.ui.contacts.EditableEvent
import org.astermail.android.ui.contacts.apply_event_edits
import org.astermail.android.ui.contacts.event_kinds_for_row
import org.astermail.android.ui.contacts.next_event_kind
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

class contact_events_test {

    private val year = 2026

    private fun birthday(date: String) = ContactEvent(ContactEventKind.BIRTHDAY, date)
    private fun anniversary(date: String) = ContactEvent(ContactEventKind.ANNIVERSARY, date)
    private fun custom(date: String, label: String) = ContactEvent(ContactEventKind.CUSTOM, date, label)

    @Test
    fun `accepts full dates and no year dates`() {
        assertTrue(valid_event_date("1990-05-17", year))
        assertTrue(valid_event_date("--05-17", year))
        assertTrue(valid_event_date("1800-01-01", year))
        assertTrue(valid_event_date("2027-12-31", year))
    }

    @Test
    fun `rejects malformed or impossible dates`() {
        assertFalse(valid_event_date("1990-5-17", year))
        assertFalse(valid_event_date("19900517", year))
        assertFalse(valid_event_date("1990-13-01", year))
        assertFalse(valid_event_date("1990-04-31", year))
        assertFalse(valid_event_date("--00-10", year))
        assertFalse(valid_event_date("", year))
        assertFalse(valid_event_date("1990-05-17T00:00:00Z", year))
    }

    @Test
    fun `february 29 needs a leap year or no year`() {
        assertTrue(valid_event_date("2000-02-29", year))
        assertTrue(valid_event_date("2024-02-29", year))
        assertTrue(valid_event_date("--02-29", year))
        assertFalse(valid_event_date("1900-02-29", year))
        assertFalse(valid_event_date("2023-02-29", year))
        assertFalse(valid_event_date("--02-30", year))
    }

    @Test
    fun `year must sit between 1800 and next year`() {
        assertFalse(valid_event_date("1799-12-31", year))
        assertFalse(valid_event_date("2028-01-01", year))
        assertTrue(valid_event_date("2027-01-01", year))
    }

    @Test
    fun `custom label is trimmed required and capped`() {
        assertEquals(custom("2020-01-01", "Graduation"), normalize_event(custom("2020-01-01", "  Graduation "), year))
        assertNull(normalize_event(custom("2020-01-01", "   "), year))
        assertNull(normalize_event(custom("2020-01-01", "x".repeat(65)), year))
        assertEquals(64, normalize_event(custom("2020-01-01", "x".repeat(64)), year)!!.label.length)
        assertEquals("", normalize_event(ContactEvent(ContactEventKind.ANNIVERSARY, "2020-01-01", "ignored"), year)!!.label)
    }

    @Test
    fun `normalize keeps one birthday dedupes and caps at twenty`() {
        val events = listOf(
            birthday("1990-01-01"),
            birthday("1991-01-01"),
            anniversary("2010-06-01"),
            anniversary("2010-06-01"),
            custom("2015-03-03", "Moved"),
            custom("2015-03-03", "moved"),
        )
        assertEquals(
            listOf(birthday("1990-01-01"), anniversary("2010-06-01"), custom("2015-03-03", "Moved")),
            normalize_events(events, year),
        )
        val many = (1..30).map { custom("2000-01-%02d".format(it.coerceAtMost(28)), "Event $it") }
        assertEquals(MAX_CONTACT_EVENTS, normalize_events(many, year).size)
    }

    @Test
    fun `decoding drops invalid entries instead of failing`() {
        val arr = JSONArray(
            """[
                {"kind":"birthday","date":"1990-05-17"},
                {"kind":"anniversary","date":"2010-02-30"},
                {"kind":"custom","date":"2012-01-01"},
                {"kind":"custom","date":"2012-01-01","label":"Met"},
                {"kind":"holiday","date":"2012-01-01"},
                {"date":"2012-01-01"},
                "junk",
                {"kind":"anniversary","date":5}
            ]""",
        )
        assertEquals(listOf(birthday("1990-05-17"), custom("2012-01-01", "Met")), decode_events(arr, year))
        assertNull(decode_contact_events(JSONObject("{}"), year))
        assertEquals(emptyList<ContactEvent>(), decode_contact_events(JSONObject("""{"events":"bad"}"""), year))
    }

    @Test
    fun `legacy birthday becomes a birthday event`() {
        assertEquals(listOf(birthday("1990-05-17")), resolve_contact_events(null, "1990-05-17", year))
        assertEquals(listOf(birthday("--05-17")), resolve_contact_events(null, "--0517", year))
        assertEquals(listOf(birthday("--05-17")), resolve_contact_events(null, "1604-05-17", year))
        assertEquals(
            listOf(birthday("1990-05-17"), anniversary("2010-06-01")),
            resolve_contact_events(listOf(anniversary("2010-06-01")), "19900517", year),
        )
        assertEquals(emptyList<ContactEvent>(), resolve_contact_events(null, "not a date", year))
        assertEquals(emptyList<ContactEvent>(), resolve_contact_events(null, "1700-01-01", year))
    }

    @Test
    fun `legacy birthday wins over a stale birthday event`() {
        val events = listOf(anniversary("2010-06-01"), birthday("1990-05-17"))
        assertEquals(
            listOf(anniversary("2010-06-01"), birthday("1991-07-07")),
            resolve_contact_events(events, "1991-07-07", year),
        )
        assertEquals(events, resolve_contact_events(events, "", year))
    }

    @Test
    fun `birthday field follows the first birthday event`() {
        assertEquals("1990-05-17", birthday_from_events(listOf(anniversary("2010-06-01"), birthday("1990-05-17"))))
        assertEquals("", birthday_from_events(listOf(anniversary("2010-06-01"))))
        val contact = Contact(id = "c", name = "Ana", email = "", birthday = "1980-01-01")
            .with_events(listOf(anniversary("2010-06-01")))
        assertEquals("", contact.birthday)
        assertEquals(listOf(anniversary("2010-06-01")), contact.events)
    }

    @Test
    fun `write keeps unchanged json and encodes labels only for custom`() {
        val original = JSONArray("""[{"kind":"birthday","date":"1990-05-17","extra":"keep"}]""")
        val obj = JSONObject().put(EVENTS_KEY, original)
        write_contact_events(obj, listOf(birthday("1990-05-17")))
        assertSame(original, obj.get(EVENTS_KEY))

        write_contact_events(obj, listOf(birthday("1990-05-17"), anniversary("2010-06-01"), custom("2012-01-01", "Met")))
        val arr = obj.getJSONArray(EVENTS_KEY)
        assertEquals(3, arr.length())
        assertEquals("keep", arr.getJSONObject(0).getString("extra"))
        assertFalse(arr.getJSONObject(1).has("label"))
        assertEquals("Met", arr.getJSONObject(2).getString("label"))
        assertEquals("custom", arr.getJSONObject(2).getString("kind"))

        write_contact_events(obj, emptyList())
        assertFalse(obj.has(EVENTS_KEY))
    }

    @Test
    fun `vcard round trip keeps every event`() {
        val source = Contact(id = "c", name = "Ana Lima", email = "ana@example.com").with_events(
            listOf(
                birthday("--05-17"),
                anniversary("2010-06-01"),
                anniversary("--09-09"),
                custom("2015-03-03", "Moved, to Lisbon"),
                custom("2020-02-29", "Graduation"),
            ),
        )
        val vcard = contact_to_vcard(source)
        assertTrue(vcard.contains("ANNIVERSARY:2010-06-01"))
        assertTrue(vcard.contains("X-ABDATE:2015-03-03"))
        assertTrue(vcard.contains("X-ABLabel:Moved\\, to Lisbon"))
        val parsed = parse_vcards(vcard).single()
        assertEquals(source.event_entries(), parsed.event_entries())
        assertEquals("--05-17", parsed.birthday)
        val again = parse_vcards(contact_to_vcard(parsed)).single()
        assertEquals(source.event_entries(), again.event_entries())
    }

    @Test
    fun `vcard import maps apple labels and unlabeled dates`() {
        val vcard = listOf(
            "BEGIN:VCARD",
            "VERSION:3.0",
            "FN:Bo",
            "BDAY;X-APPLE-OMIT-YEAR=1604:1604-03-04",
            "item1.X-ABDATE:2011-11-11",
            "item1.X-ABLabel:_\$!<Anniversary>!\$_",
            "item2.X-ABDATE:2012-12-12",
            "item2.X-ABLabel:_\$!<Other>!\$_",
            "item3.X-ABDATE:2013-01-13",
            "END:VCARD",
        ).joinToString("\r\n")
        val parsed = parse_vcards(vcard).single()
        assertEquals(
            listOf(
                birthday("--03-04"),
                anniversary("2011-11-11"),
                custom("2012-12-12", "Other"),
                custom("2013-01-13", VCARD_UNLABELED_DATE),
            ),
            parsed.event_entries(),
        )
    }

    @Test
    fun `vcard with only a birthday leaves events unset`() {
        val parsed = parse_vcards("BEGIN:VCARD\r\nVERSION:3.0\r\nFN:Cy\r\nBDAY:1999-09-09\r\nEND:VCARD").single()
        assertNull(parsed.events)
        assertEquals(listOf(birthday("1999-09-09")), parsed.event_entries())
    }

    @Test
    fun `csv round trip keeps events`() {
        val source = Contact(id = "c", name = "Ana Lima", email = "ana@example.com").with_events(
            listOf(birthday("1990-05-17"), anniversary("2010-06-01"), custom("2015-03-03", "Moved")),
        )
        val parsed = parse_csv_contacts(contacts_to_csv(listOf(source))).single()
        assertEquals(source.event_entries(), parsed.event_entries())
    }

    @Test
    fun `csv labels map to event kinds`() {
        assertEquals(anniversary("2010-06-01"), event_from_csv("* Anniversary", "2010-06-01"))
        assertEquals(birthday("--05-17"), event_from_csv("Birthday", "--05-17"))
        assertEquals(custom("2010-06-01", "Met"), event_from_csv("Met", "2010-06-01"))
        assertNull(event_from_csv("Met", "someday"))
    }

    @Test
    fun `duplicate merge unions events by kind label and date`() {
        val left = Contact(id = "a", name = "Ana", email = "ana@example.com").with_events(
            listOf(birthday("1990-05-17"), anniversary("2010-06-01"), custom("2015-03-03", "Moved")),
        )
        val right = Contact(id = "b", name = "Ana", email = "ana@example.com").with_events(
            listOf(birthday("1990-05-17"), anniversary("2010-06-01"), custom("2015-03-03", "moved"), custom("2016-04-04", "Promoted")),
        )
        val merged = merge_contacts(listOf(left, right))
        assertEquals(
            listOf(birthday("1990-05-17"), anniversary("2010-06-01"), custom("2015-03-03", "Moved"), custom("2016-04-04", "Promoted")),
            merged.event_entries(),
        )
        assertEquals("1990-05-17", merged.birthday)
    }

    @Test
    fun `duplicate merge of birthday only contacts keeps the legacy shape`() {
        val left = Contact(id = "a", name = "Ana", email = "ana@example.com", birthday = "1990-05-17")
        val right = Contact(id = "b", name = "Ana", email = "ana@example.com")
        val merged = merge_contacts(listOf(left, right))
        assertNull(merged.events)
        assertEquals("1990-05-17", merged.birthday)
    }

    @Test
    fun `edit rows allow one birthday and default to it first`() {
        val rows = listOf(EditableEvent(ContactEventKind.BIRTHDAY, "1990-05-17"), EditableEvent(ContactEventKind.ANNIVERSARY))
        assertFalse(ContactEventKind.BIRTHDAY in event_kinds_for_row(rows, 1))
        assertTrue(ContactEventKind.BIRTHDAY in event_kinds_for_row(rows, 0))
        assertEquals(ContactEventKind.ANNIVERSARY, next_event_kind(rows))
        assertEquals(ContactEventKind.BIRTHDAY, next_event_kind(emptyList()))
    }

    @Test
    fun `saving edits keeps a birthday the editor could not show`() {
        val source = Contact(id = "c", name = "Ana", email = "", birthday = "spring 1990")
        val saved = apply_event_edits(source, source, listOf(EditableEvent(ContactEventKind.ANNIVERSARY, "2010-06-01")))
        assertEquals("spring 1990", saved.birthday)
        assertEquals(listOf(anniversary("2010-06-01")), saved.events)

        val dated = Contact(id = "c", name = "Ana", email = "", birthday = "1990-05-17")
        val cleared = apply_event_edits(dated, dated, emptyList())
        assertEquals("", cleared.birthday)
        assertEquals(emptyList<ContactEvent>(), cleared.events)
    }
}
