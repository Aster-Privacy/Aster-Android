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
package org.astermail.android.mail

enum class MailPriority(val wire: String) {
    HIGH("high"),
    NORMAL("normal"),
    LOW("low"),
}

object MailPriorityRules {
    private val x_priority_pattern = Regex("""^\s*([1-5])\b""")

    fun outgoing(priority: MailPriority): String? = when (priority) {
        MailPriority.HIGH -> MailPriority.HIGH.wire
        MailPriority.LOW -> MailPriority.LOW.wire
        MailPriority.NORMAL -> null
    }

    fun from_wire(value: String?): MailPriority = when (value?.trim()?.lowercase()) {
        MailPriority.HIGH.wire -> MailPriority.HIGH
        MailPriority.LOW.wire -> MailPriority.LOW
        else -> MailPriority.NORMAL
    }

    fun headers(priority: MailPriority): List<Pair<String, String>> = when (priority) {
        MailPriority.HIGH -> listOf("X-Priority" to "1 (Highest)", "Importance" to "high")
        MailPriority.LOW -> listOf("X-Priority" to "5 (Lowest)", "Importance" to "low")
        MailPriority.NORMAL -> emptyList()
    }

    private fun from_x_priority(value: String): MailPriority? {
        val digit = x_priority_pattern.find(value)?.groupValues?.get(1)?.toIntOrNull() ?: return null
        return when {
            digit <= 2 -> MailPriority.HIGH
            digit == 3 -> MailPriority.NORMAL
            else -> MailPriority.LOW
        }
    }

    private fun from_word(value: String): MailPriority? = when (value.trim().lowercase()) {
        "high", "urgent" -> MailPriority.HIGH
        "low", "non-urgent" -> MailPriority.LOW
        "normal" -> MailPriority.NORMAL
        else -> null
    }

    fun from_headers(raw_headers: List<Pair<String, String>>): MailPriority {
        var x_priority: MailPriority? = null
        var importance: MailPriority? = null
        var priority: MailPriority? = null
        for ((name, value) in raw_headers) {
            when (name.trim().lowercase()) {
                "x-priority" -> if (x_priority == null) x_priority = from_x_priority(value)
                "importance" -> if (importance == null) importance = from_word(value)
                "priority" -> if (priority == null) priority = from_word(value)
            }
        }
        return x_priority ?: importance ?: priority ?: MailPriority.NORMAL
    }
}
