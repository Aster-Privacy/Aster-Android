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
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ContactTransferTest {
    private fun sample() = Contact(
        id = "1",
        name = "Ada Lovelace",
        email = "ada@example.com",
        phone = "+1 555 0100",
        company = "Analytical Engines",
        title = "Engineer",
        work_email = "ada@work.example.com",
        work_phone = "+1 555 0200",
        birthday = "1815-12-10",
        address = "12 Bridge Street",
        city = "London",
        region = "Greater London",
        postal_code = "SW1A",
        country = "United Kingdom",
        website = "https://example.com",
        twitter = "ada",
        linkedin = "ada-lovelace",
        notes = "Line one\nLine two, with a comma; and a semicolon",
        avatar_url = "data:image/png;base64,AAAABBBB",
        profile_color = "#5e35b1",
        is_favorite = true,
        groups = listOf("Work", "Friends"),
    )

    @Test
    fun `vcard round trip preserves every field`() {
        val original = sample()
        val parsed = parse_vcards(contact_to_vcard(original))

        assertEquals(1, parsed.size)
        val result = parsed.first()
        assertEquals(original.name, result.name)
        assertEquals(original.email, result.email)
        assertEquals(original.work_email, result.work_email)
        assertEquals(original.phone, result.phone)
        assertEquals(original.work_phone, result.work_phone)
        assertEquals(original.company, result.company)
        assertEquals(original.title, result.title)
        assertEquals(original.birthday, result.birthday)
        assertEquals(original.address, result.address)
        assertEquals(original.city, result.city)
        assertEquals(original.region, result.region)
        assertEquals(original.postal_code, result.postal_code)
        assertEquals(original.country, result.country)
        assertEquals(original.website, result.website)
        assertEquals(original.twitter, result.twitter)
        assertEquals(original.linkedin, result.linkedin)
        assertEquals(original.notes, result.notes)
        assertEquals(original.avatar_url, result.avatar_url)
        assertEquals(original.profile_color, result.profile_color)
        assertEquals(original.groups, result.groups)
        assertTrue(result.is_favorite)
    }

    @Test
    fun `photo line stays unescaped and unfolds back to one value`() {
        val long_photo = "data:image/png;base64," + "A".repeat(400)
        val vcard = contact_to_vcard(sample().copy(avatar_url = long_photo))

        assertFalse(vcard.contains("data:image/png\\;base64"))
        assertEquals(long_photo, parse_vcards(vcard).first().avatar_url)
    }

    @Test
    fun `base64 photo parameters become a data url`() {
        val vcard = buildString {
            append("BEGIN:VCARD\r\n")
            append("VERSION:3.0\r\n")
            append("FN:Grace Hopper\r\n")
            append("EMAIL:grace@example.com\r\n")
            append("PHOTO;ENCODING=b;TYPE=JPEG:AAAA\r\n")
            append("END:VCARD\r\n")
        }

        assertEquals("data:image/jpeg;base64,AAAA", parse_vcards(vcard).first().avatar_url)
    }

    @Test
    fun `multiple cards parse independently`() {
        val text = contacts_to_vcard(
            listOf(
                sample(),
                Contact(id = "2", name = "Grace Hopper", email = "grace@example.com"),
            ),
        )
        val parsed = parse_vcards(text)

        assertEquals(2, parsed.size)
        assertEquals("Grace Hopper", parsed[1].name)
        assertEquals("", parsed[1].company)
        assertFalse(parsed[1].is_favorite)
    }

    @Test
    fun `csv neutralizes formulas and escapes quotes`() {
        val csv = contacts_to_csv(
            listOf(sample().copy(name = "=HYPERLINK(1) X", notes = "He said \"hi\"")),
        )

        assertTrue(csv.contains("\"'=HYPERLINK(1)\""))
        assertTrue(csv.contains("\"He said \"\"hi\"\"\""))
    }

    @Test
    fun `csv round trip keeps the core fields`() {
        val parsed = parse_csv_contacts(contacts_to_csv(listOf(sample())))

        assertEquals(1, parsed.size)
        val result = parsed.first()
        assertEquals("Ada Lovelace", result.name)
        assertEquals("ada@example.com", result.email)
        assertEquals("ada@work.example.com", result.work_email)
        assertEquals("Greater London", result.region)
        assertEquals("https://example.com", result.website)
        assertTrue(result.is_favorite)
    }

    @Test
    fun `csv import ignores a leading byte order mark`() {
        val csv = "\uFEFF" + contacts_to_csv(listOf(sample()))
        val result = parse_csv_contacts(csv).first()

        assertEquals("Ada Lovelace", result.name)
        assertEquals("ada@example.com", result.email)
    }

    @Test
    fun `vcard import ignores a leading byte order mark`() {
        val vcard = "\uFEFF" + contacts_to_vcard(listOf(sample()))
        val result = parse_vcards(vcard)

        assertEquals(1, result.size)
        assertEquals("Ada Lovelace", result.first().name)
    }

    @Test
    fun `csv round trip restores phone numbers past the formula guard`() {
        val csv = contacts_to_csv(listOf(sample()))

        assertTrue(csv.contains("\"'+1 555 0100; +1 555 0200\""))

        val result = parse_csv_contacts(csv).first()

        assertEquals("+1 555 0100", result.phone)
        assertEquals("+1 555 0200", result.work_phone)
    }

    @Test
    fun `csv import maps a full name column and skips empty rows`() {
        val csv = "Name,Email\r\n\"Grace Hopper\",grace@example.com\r\n,\r\n"
        val parsed = parse_csv_contacts(csv)

        assertEquals(1, parsed.size)
        assertEquals("Grace Hopper", parsed.first().name)
    }

    @Test
    fun `csv import handles quoted newlines and commas`() {
        val csv = "First name,Last name,Email,Notes\r\nAda,Lovelace,ada@example.com,\"a, b\nc\"\r\n"
        val parsed = parse_csv_contacts(csv)

        assertEquals(1, parsed.size)
        assertEquals("a, b\nc", parsed.first().notes)
    }

    @Test
    fun `file detection picks the parser by content`() {
        val vcard = contact_to_vcard(sample())

        assertEquals(1, parse_contacts_file("export.txt", vcard).size)
        assertEquals(1, parse_contacts_file("export.csv", contacts_to_csv(listOf(sample()))).size)
    }

    @Test
    fun `blank input yields no contacts`() {
        assertTrue(parse_vcards("").isEmpty())
        assertTrue(parse_csv_contacts("").isEmpty())
        assertTrue(parse_contacts_file("empty.csv", "\r\n").isEmpty())
    }
}
