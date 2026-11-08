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

import java.util.Locale
import org.astermail.android.ui.contacts.Contact
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ContactDatesTest {

    private val month_day_pattern = { skeleton: String ->
        when (skeleton) {
            ContactDates.MONTH_DAY_SKELETON -> "MMMM d"
            ContactDates.MONTH_YEAR_SKELETON -> "MMMM yyyy"
            else -> "LLLL"
        }
    }

    @Test
    fun parses_full_and_yearless_forms() {
        assertEquals(ContactDateParts(1815, 12, 10), ContactDates.parse("1815-12-10"))
        assertEquals(ContactDateParts(1815, 12, 10), ContactDates.parse("18151210"))
        assertEquals(ContactDateParts(null, 12, 10), ContactDates.parse("--12-10"))
        assertEquals(ContactDateParts(null, 12, 10), ContactDates.parse("--1210"))
        assertEquals(ContactDateParts(1990, 1, 15), ContactDates.parse("1990-01-15T00:00:00Z"))
    }

    @Test
    fun placeholder_years_mean_no_year() {
        assertEquals(ContactDateParts(null, 3, 4), ContactDates.parse("1604-03-04"))
        assertEquals(ContactDateParts(null, 3, 4), ContactDates.parse("0000-03-04"))
        assertEquals(ContactDateParts(null, 3, 4), ContactDates.parse("1984-03-04", omitted_year = "1984"))
    }

    @Test
    fun rejects_impossible_dates() {
        assertNull(ContactDates.parse("2023-02-29"))
        assertNull(ContactDates.parse("--13-01"))
        assertNull(ContactDates.parse("--04-31"))
        assertNull(ContactDates.parse("12/28/1969"))
        assertEquals(ContactDateParts(null, 2, 29), ContactDates.parse("--02-29"))
    }

    @Test
    fun normalize_stores_web_forms_and_keeps_unknown_text() {
        assertEquals("--12-10", ContactDates.normalize("--1210"))
        assertEquals("--12-10", ContactDates.normalize("1604-12-10"))
        assertEquals("1815-12-10", ContactDates.normalize("18151210"))
        assertEquals("12/28/1969", ContactDates.normalize(" 12/28/1969 "))
    }

    @Test
    fun vcard_value_uses_basic_or_extended_form() {
        assertEquals("--1210", ContactDates.vcard_value("--12-10", basic = true))
        assertEquals("--12-10", ContactDates.vcard_value("--12-10", basic = false))
        assertEquals("1815-12-10", ContactDates.vcard_value("1815-12-10", basic = true))
        assertEquals("12/28/1969", ContactDates.vcard_value("12/28/1969", basic = false))
    }

    @Test
    fun partial_detection() {
        assertTrue(ContactDates.is_partial("--12-10"))
        assertTrue(ContactDates.is_partial("--12"))
        assertFalse(ContactDates.is_partial("1815-12-10"))
        assertFalse(ContactDates.is_partial("someday"))
    }

    @Test
    fun picker_builds_values_and_validates_the_year() {
        assertEquals("--02-29", ContactDates.from_picker(2, 29, ""))
        assertEquals("2024-02-29", ContactDates.from_picker(2, 29, "2024"))
        assertNull(ContactDates.from_picker(2, 29, "2023"))
        assertNull(ContactDates.from_picker(1, 1, "99"))
        assertNull(ContactDates.from_picker(1, 1, "0000"))
        assertNull(ContactDates.from_picker(0, 1, ""))
        assertTrue(ContactDates.picker_year("12a4").isFailure)
        assertNull(ContactDates.picker_year(" ").getOrThrow())
    }

    @Test
    fun format_shows_month_and_day_without_a_year() {
        assertEquals("December 10", ContactDates.format("--12-10", Locale.US, month_day_pattern))
        assertEquals("February 29", ContactDates.format("--0229", Locale.US, month_day_pattern))
        assertEquals("December 10, 1815", ContactDates.format("1815-12-10", Locale.US, month_day_pattern))
        assertEquals("12/28/1969", ContactDates.format("12/28/1969", Locale.US, month_day_pattern))
        assertEquals("", ContactDates.format("  ", Locale.US, month_day_pattern))
    }

    @Test
    fun vcard_round_trips_a_yearless_birthday() {
        val vcard = contact_to_vcard(Contact(id = "1", name = "Ada", email = "ada@example.com", birthday = "--12-10"))
        assertTrue(vcard.contains("BDAY:--12-10"))
        assertEquals("--12-10", parse_vcards(vcard).single().birthday)
    }

    @Test
    fun vcard_import_reads_basic_and_omitted_year_forms() {
        val basic = "BEGIN:VCARD\r\nVERSION:4.0\r\nFN:Ada\r\nBDAY:--1210\r\nEND:VCARD\r\n"
        assertEquals("--12-10", parse_vcards(basic).single().birthday)
        val omitted = "BEGIN:VCARD\r\nVERSION:3.0\r\nFN:Ada\r\nBDAY;X-APPLE-OMIT-YEAR=1604:1604-12-10\r\nEND:VCARD\r\n"
        assertEquals("--12-10", parse_vcards(omitted).single().birthday)
        val full = "BEGIN:VCARD\r\nVERSION:3.0\r\nFN:Ada\r\nBDAY:18151210\r\nEND:VCARD\r\n"
        assertEquals("1815-12-10", parse_vcards(full).single().birthday)
    }

    @Test
    fun csv_import_normalizes_a_yearless_birthday() {
        val csv = "Name,Birthday\r\nAda,--1210\r\n"
        assertEquals("--12-10", parse_csv_contacts(csv).single().birthday)
    }
}
