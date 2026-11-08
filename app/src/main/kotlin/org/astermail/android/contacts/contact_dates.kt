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

import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import java.util.Locale

data class ContactDateParts(val year: Int?, val month: Int, val day: Int)

data class ContactMonthParts(val year: Int?, val month: Int)

object ContactDates {
    const val LEAP_YEAR = 2000
    const val MONTH_DAY_SKELETON = "MMMMd"
    const val MONTH_YEAR_SKELETON = "yMMMM"
    const val MONTH_SKELETON = "LLLL"

    private val date_pattern = Regex(
        """^(?:(\d{4})-?|--)(\d{2})-?(\d{2})(?:T[\d:.,+\-Z]*)?$""",
        RegexOption.IGNORE_CASE,
    )
    private val month_pattern = Regex("""^(?:(\d{4})-|--)(\d{2})$""")
    private val placeholder_years = setOf("0000", "1604")

    fun days_in_month(year: Int?, month: Int): Int =
        if (month !in 1..12) 0 else java.time.YearMonth.of(year ?: LEAP_YEAR, month).lengthOfMonth()

    private fun year_of(raw: String?, omitted_year: String?): Int? {
        if (raw.isNullOrEmpty() || raw == omitted_year || raw in placeholder_years) return null
        return raw.toInt()
    }

    fun parse(value: String, omitted_year: String? = null): ContactDateParts? {
        val match = date_pattern.matchEntire(value.trim()) ?: return null
        val year = year_of(match.groupValues[1], omitted_year?.trim())
        val month = match.groupValues[2].toInt()
        val day = match.groupValues[3].toInt()
        if (month !in 1..12) return null
        if (day < 1 || day > days_in_month(year, month)) return null
        return ContactDateParts(year, month, day)
    }

    fun parse_month(value: String): ContactMonthParts? {
        val match = month_pattern.matchEntire(value.trim()) ?: return null
        val month = match.groupValues[2].toInt()
        if (month !in 1..12) return null
        return ContactMonthParts(year_of(match.groupValues[1], null), month)
    }

    private fun pad2(value: Int): String = value.toString().padStart(2, '0')

    fun from_parts(parts: ContactDateParts): String {
        val month_day = "${pad2(parts.month)}-${pad2(parts.day)}"
        return if (parts.year == null) "--$month_day" else "${parts.year.toString().padStart(4, '0')}-$month_day"
    }

    fun normalize(value: String, omitted_year: String? = null): String =
        parse(value, omitted_year)?.let { from_parts(it) } ?: value.trim()

    fun vcard_value(value: String, basic: Boolean): String {
        val parts = parse(value) ?: return value
        if (parts.year != null || !basic) return from_parts(parts)
        return "--${pad2(parts.month)}${pad2(parts.day)}"
    }

    fun picker_year(text: String): Result<Int?> {
        val trimmed = text.trim()
        if (trimmed.isEmpty()) return Result.success(null)
        if (trimmed.length != 4 || !trimmed.all { it in '0'..'9' }) return Result.failure(IllegalArgumentException())
        val year = trimmed.toInt()
        if (year < 1) return Result.failure(IllegalArgumentException())
        return Result.success(year)
    }

    fun from_picker(month: Int, day: Int, year_text: String): String? {
        val year = picker_year(year_text).getOrElse { return null }
        if (month !in 1..12 || day < 1 || day > days_in_month(year, month)) return null
        return from_parts(ContactDateParts(year, month, day))
    }

    fun is_partial(value: String): Boolean {
        val parts = parse(value)
        if (parts != null) return parts.year == null
        return parse_month(value) != null
    }

    fun format(value: String, locale: Locale, best_pattern: (String) -> String): String {
        val trimmed = value.trim()
        if (trimmed.isEmpty()) return ""
        return runCatching {
            val date = parse(trimmed)
            if (date != null) {
                if (date.year != null) {
                    LocalDate.of(date.year, date.month, date.day).format(
                        DateTimeFormatter.ofLocalizedDate(FormatStyle.LONG).withLocale(locale),
                    )
                } else {
                    LocalDate.of(LEAP_YEAR, date.month, date.day).format(
                        DateTimeFormatter.ofPattern(best_pattern(MONTH_DAY_SKELETON), locale),
                    )
                }
            } else {
                val month = parse_month(trimmed) ?: return@runCatching trimmed
                val skeleton = if (month.year != null) MONTH_YEAR_SKELETON else MONTH_SKELETON
                LocalDate.of(month.year ?: LEAP_YEAR, month.month, 1).format(
                    DateTimeFormatter.ofPattern(best_pattern(skeleton), locale),
                )
            }
        }.getOrDefault(trimmed)
    }
}
