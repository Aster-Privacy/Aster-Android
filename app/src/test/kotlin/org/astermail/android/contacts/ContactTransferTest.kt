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
import org.astermail.android.ui.contacts.ContactEntry
import org.astermail.android.ui.contacts.ContactPostal
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

    private fun typed_sample() = Contact(id = "3", name = "Ada Lovelace", email = "").with_typed_fields(
        emails = listOf(
            ContactEntry("ada@home.example", "home"),
            ContactEntry("ada@work.example", "work"),
            ContactEntry("ada@me.example", "personal"),
            ContactEntry("ada@lab.example", "other", "Lab"),
        ),
        phones = listOf(
            ContactEntry("+44 1", "mobile"),
            ContactEntry("+44 2", "work"),
            ContactEntry("+44 3", "personal"),
            ContactEntry("+44 4", "fax"),
            ContactEntry("+44 5", "other", "Boat"),
        ),
        addresses = listOf(
            ContactPostal("1 Engine Row", "Oxford", "", "OX1", "UK", "work"),
            ContactPostal("12 Bridge St", "London", "", "SW1A", "UK", "home"),
            ContactPostal("2 Cottage Ln", "Bath", "", "BA1", "UK", "other", "Summer house"),
        ),
    )

    @Test
    fun `vcard round trip keeps typed entries and custom labels`() {
        val original = typed_sample()
        val vcard = contact_to_vcard(original)
        assertTrue(vcard.contains("X-ABLabel:Personal"))
        assertTrue(vcard.contains("X-ABLabel:Summer house"))
        assertFalse(vcard.contains("TYPE=HOME:ada@me.example"))
        assertFalse(vcard.contains("INTERNET"))
        assertTrue(vcard.contains("EMAIL;TYPE=HOME:ada@home.example"))
        assertTrue(vcard.contains(".TEL;TYPE=Boat:+44 5"))
        assertTrue(vcard.contains(".ADR;TYPE=Summer house:"))
        val result = parse_vcards(vcard).single()
        assertEquals(original.emails, result.emails)
        assertEquals(original.phones, result.phones)
        assertEquals(original.addresses, result.addresses)
        assertEquals("12 Bridge St", result.address)
        assertEquals("ada@home.example", result.email)
        assertEquals("ada@work.example", result.work_email)
    }

    @Test
    fun `apple labels and unknown type tokens become labels`() {
        val vcard = buildString {
            append("BEGIN:VCARD\r\n")
            append("VERSION:3.0\r\n")
            append("FN:Grace Hopper\r\n")
            append("item1.EMAIL;type=INTERNET;type=pref:grace@example.com\r\n")
            append("item1.X-ABLabel:_\$!<Home>!\$_\r\n")
            append("item2.TEL:+1 555\r\n")
            append("item2.X-ABLabel:Ship\r\n")
            append("TEL;TYPE=CELL;TYPE=VOICE:+1 556\r\n")
            append("TEL;TYPE=X-SATELLITE:+1 557\r\n")
            append("item3.ADR:;;1 Main St;Arlington;VA;22201;USA\r\n")
            append("item3.X-ABLabel:_\$!<Work>!\$_\r\n")
            append("END:VCARD\r\n")
        }
        val result = parse_vcards(vcard).single()
        assertEquals(listOf(ContactEntry("grace@example.com", "home")), result.emails)
        assertEquals(
            listOf(
                ContactEntry("+1 555", "other", "Ship"),
                ContactEntry("+1 556", "mobile"),
                ContactEntry("+1 557", "other", "Satellite"),
            ),
            result.phones,
        )
        assertEquals("work", result.addresses.single().type)
        assertEquals("1 Main St", result.address)
    }

    @Test
    fun `remote photo urls are ignored`() {
        val vcard = buildString {
            append("BEGIN:VCARD\r\n")
            append("VERSION:3.0\r\n")
            append("FN:Grace Hopper\r\n")
            append("PHOTO;VALUE=URI:https://example.com/grace.jpg\r\n")
            append("END:VCARD\r\n")
        }
        assertEquals("", parse_vcards(vcard).single().avatar_url)
        assertFalse(contact_to_vcard(sample().copy(avatar_url = "https://example.com/a.png")).contains("PHOTO"))
    }

    @Test
    fun `share text lists the name then every phone`() {
        assertEquals("Ada Lovelace\n+44 1\n+44 2\n+44 3\n+44 4\n+44 5", contact_share_text(typed_sample()))
        val nameless = Contact(id = "", name = "", email = "").with_typed_fields(
            emptyList(),
            listOf(ContactEntry("+1", "mobile")),
            emptyList(),
        )
        assertEquals("+1", contact_share_text(nameless))
        assertEquals("Ada Lovelace.vcf", contact_share_file_name(typed_sample()))
        assertEquals("contact.vcf", contact_share_file_name(nameless))
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

    @Test
    fun `csv import reads the indexed contacts export format`() {
        val csv = listOf(
            "Name,Given Name,Additional Name,Family Name,Name Prefix,Name Suffix,Nickname,Birthday,Notes,Group Membership,E-mail 1 - Type,E-mail 1 - Value,E-mail 2 - Type,E-mail 2 - Value,Phone 1 - Type,Phone 1 - Value,Phone 2 - Type,Phone 2 - Value,Address 1 - Type,Address 1 - Formatted,Address 1 - Street,Address 1 - City,Address 1 - PO Box,Address 1 - Region,Address 1 - Postal Code,Address 1 - Country,Organization 1 - Name,Organization 1 - Title,Organization 1 - Department,Website 1 - Type,Website 1 - Value,Event 1 - Type,Event 1 - Value,Relation 1 - Type,Relation 1 - Value,Custom Field 1 - Type,Custom Field 1 - Value",
            "Ada King Lovelace,Ada,King,Lovelace,Dr.,PhD,Addie,1815-12-10,Poet of numbers,* myContacts ::: * Starred ::: Family ::: * Work,* Home,ada@home.example,* Work,ada@work.example,Mobile,+44 20 7946 0100,Work,+44 20 7946 0200,Home,\"12 Bridge St\nLondon\",12 Bridge St,London,,Greater London,SW1A 1AA,United Kingdom,Analytical Engines,Engineer,Research,Profile,https://linkedin.com/in/ada,Anniversary,1835-07-08,Spouse,William King,Pronouns,she/her",
        ).joinToString("\r\n") + "\r\n"

        val parsed = parse_csv_contacts(csv)

        assertEquals(1, parsed.size)
        val result = parsed.first()
        assertEquals("Ada Lovelace", result.name)
        assertEquals("ada@home.example", result.email)
        assertEquals("ada@work.example", result.work_email)
        assertEquals("+44 20 7946 0100", result.phone)
        assertEquals("+44 20 7946 0200", result.work_phone)
        assertEquals("Analytical Engines", result.company)
        assertEquals("Engineer", result.title)
        assertEquals("12 Bridge St", result.address)
        assertEquals("London", result.city)
        assertEquals("Greater London", result.region)
        assertEquals("SW1A 1AA", result.postal_code)
        assertEquals("United Kingdom", result.country)
        assertEquals("https://linkedin.com/in/ada", result.linkedin)
        assertEquals("1815-12-10", result.birthday)
        assertEquals("Poet of numbers\nPronouns: she/her", result.notes)
        assertTrue(result.is_favorite)
        assertEquals(listOf("Family", "Work"), result.groups)

        val raw = org.json.JSONObject(result.raw_json)
        assertEquals("King", raw.getString("middle_name"))
        assertEquals("Dr.", raw.getString("title"))
        assertEquals("PhD", raw.getString("name_suffix"))
        assertEquals("Addie", raw.getString("nickname"))
        assertEquals("Research", raw.getString("department"))
        assertEquals("home", raw.getJSONArray("email_entries").getJSONObject(0).getString("type"))
        assertEquals("work", raw.getJSONArray("email_entries").getJSONObject(1).getString("type"))
        assertEquals("mobile", raw.getJSONArray("phone_entries").getJSONObject(0).getString("type"))
        assertEquals("1835-07-08", raw.getJSONArray("date_entries").getJSONObject(0).getString("value"))
        assertEquals("spouse", raw.getJSONArray("related_people").getJSONObject(0).getString("type"))
        assertEquals("home", raw.getJSONArray("address_entries").getJSONObject(0).getString("type"))
    }

    @Test
    fun `csv import reads the labeled contacts export format`() {
        val csv = listOf(
            "First Name,Middle Name,Last Name,Phonetic First Name,Phonetic Middle Name,Phonetic Last Name,Name Prefix,Name Suffix,Nickname,File As,Organization Name,Organization Title,Organization Department,Birthday,Notes,Photo,Labels,E-mail 1 - Label,E-mail 1 - Value,E-mail 2 - Label,E-mail 2 - Value,Phone 1 - Label,Phone 1 - Value,Address 1 - Label,Address 1 - Formatted,Address 1 - Street,Address 1 - City,Address 1 - PO Box,Address 1 - Region,Address 1 - Postal Code,Address 1 - Country,Address 1 - Extended Address,Website 1 - Label,Website 1 - Value",
            "Grace,Brewster,Hopper,,,,,,Amazing Grace,,US Navy,Rear Admiral,,1906-12-09,,,* myContacts ::: Navy,* Work,grace@navy.example,Other,grace@example.com,Mobile,+1 555 0199,Work,,1 Main St,Arlington,,VA,22201,USA,Suite 4,,https://example.com/grace",
        ).joinToString("\n") + "\n"

        val parsed = parse_csv_contacts(csv)

        assertEquals(1, parsed.size)
        val result = parsed.first()
        assertEquals("Grace Hopper", result.name)
        assertEquals("grace@example.com", result.email)
        assertEquals("grace@navy.example", result.work_email)
        assertEquals("+1 555 0199", result.phone)
        assertEquals("US Navy", result.company)
        assertEquals("Rear Admiral", result.title)
        assertEquals("1 Main St, Suite 4", result.address)
        assertEquals("VA", result.region)
        assertEquals("https://example.com/grace", result.website)
        assertEquals(listOf("Navy"), result.groups)
        assertFalse(result.is_favorite)
        val raw = org.json.JSONObject(result.raw_json)
        assertEquals("Brewster", raw.getString("middle_name"))
        assertEquals("other", raw.getJSONArray("email_entries").getJSONObject(0).getString("type"))
        assertEquals("work", raw.getJSONArray("email_entries").getJSONObject(1).getString("type"))
        assertEquals("work", raw.getJSONArray("address_entries").getJSONObject(0).getString("type"))
    }

    @Test
    fun `csv import reads a desktop mail client export`() {
        val csv = listOf(
            "First Name,Middle Name,Last Name,Title,Suffix,Nickname,E-mail Address,E-mail 2 Address,E-mail 3 Address,Home Phone,Business Phone,Mobile Phone,Business Fax,Company,Job Title,Department,Home Street,Home City,Home State,Home Postal Code,Home Country/Region,Business Street,Business City,Business State,Business Postal Code,Business Country/Region,Web Page,Birthday,Anniversary,Spouse,Notes,Categories",
            "Linus,,Torvalds,Mr.,,,linus@example.com,linus@work.example,,+358 9 000 0001,+358 9 000 0002,+358 40 000 0003,+358 9 000 0004,Kernel Inc,Maintainer,Core,1 Home Rd,Helsinki,Uusimaa,00100,Finland,2 Office St,Espoo,Uusimaa,02100,Finland,https://example.com,12/28/1969,1/1/2000,Tove,Likes penguins,Friends;Open source",
        ).joinToString("\r\n") + "\r\n"

        val parsed = parse_csv_contacts(csv)

        assertEquals(1, parsed.size)
        val result = parsed.first()
        assertEquals("Linus Torvalds", result.name)
        assertEquals("Maintainer", result.title)
        assertEquals("linus@example.com", result.email)
        assertEquals("linus@work.example", result.work_email)
        assertEquals("+358 9 000 0001", result.phone)
        assertEquals("+358 9 000 0002", result.work_phone)
        assertEquals("1 Home Rd", result.address)
        assertEquals("Helsinki", result.city)
        assertEquals("Finland", result.country)
        assertEquals("https://example.com", result.website)
        assertEquals("12/28/1969", result.birthday)
        assertEquals("Likes penguins", result.notes)
        assertEquals(listOf("Friends", "Open source"), result.groups)
        val raw = org.json.JSONObject(result.raw_json)
        assertEquals("Mr.", raw.getString("title"))
        assertEquals("Core", raw.getString("department"))
        assertEquals(4, raw.getJSONArray("phone_entries").length())
        assertEquals("fax", raw.getJSONArray("phone_entries").getJSONObject(3).getString("type"))
        assertEquals(2, raw.getJSONArray("address_entries").length())
        assertEquals("Espoo", raw.getJSONArray("address_entries").getJSONObject(1).getString("city"))
        assertEquals("anniversary", raw.getJSONArray("date_entries").getJSONObject(0).getString("type"))
        assertEquals("Tove", raw.getJSONArray("related_people").getJSONObject(0).getString("value"))
    }

    @Test
    fun `csv import keeps every row of a large export`() {
        val header = "Name,E-mail 1 - Value\n"
        val rows = (1..700).joinToString("") { "Person $it,person$it@example.com\n" }

        val parsed = parse_csv_contacts(header + rows)

        assertEquals(700, parsed.size)
        assertEquals("person700@example.com", parsed.last().email)
    }

    @Test
    fun `csv import keeps rows that only have an email`() {
        val parsed = parse_csv_contacts("Name,E-mail 1 - Value\n,only@example.com\n")

        assertEquals(1, parsed.size)
        assertEquals("only@example.com", parsed.first().name)
    }

    @Test
    fun `csv import keeps the work email in the work slot when it comes first`() {
        val csv = "Name,E-mail 1 - Type,E-mail 1 - Value,E-mail 2 - Type,E-mail 2 - Value,Phone 1 - Type,Phone 1 - Value,Phone 2 - Type,Phone 2 - Value\n" +
            "Work First,* Work,first@work.example,* Home,first@home.example,Work,+1 555 0001,Mobile,+1 555 0002\n"

        val result = parse_csv_contacts(csv).single()

        assertEquals("first@home.example", result.email)
        assertEquals("first@work.example", result.work_email)
        assertEquals("+1 555 0002", result.phone)
        assertEquals("+1 555 0001", result.work_phone)
        val raw = org.json.JSONObject(result.raw_json)
        assertEquals("home", raw.getJSONArray("email_entries").getJSONObject(0).getString("type"))
        assertEquals("work", raw.getJSONArray("email_entries").getJSONObject(1).getString("type"))
    }
}
