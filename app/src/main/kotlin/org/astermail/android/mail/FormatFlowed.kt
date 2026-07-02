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

object FormatFlowed {

    private const val SIGNATURE_SEPARATOR = "-- "

    fun unflow(text: String, delsp: Boolean = false): String {
        if (text.isEmpty()) return ""

        val lines = text.replace("\r\n", "\n").replace("\r", "\n").split("\n")
        val output = ArrayList<String>()
        var paragraph: StringBuilder? = null
        var paragraph_depth = 0

        fun flush() {
            val current = paragraph ?: return
            val prefix = if (paragraph_depth > 0) ">".repeat(paragraph_depth) + " " else ""
            output.add(prefix + current.toString())
            paragraph = null
            paragraph_depth = 0
        }

        for (raw_line in lines) {
            var line = raw_line
            var depth = 0

            while (line.startsWith(">")) {
                depth++
                line = line.substring(1)
            }

            if (line.startsWith(" ")) {
                line = line.substring(1)
            }

            val is_signature = line == SIGNATURE_SEPARATOR
            val is_soft_break = line.endsWith(" ") && !is_signature

            if (paragraph != null && depth != paragraph_depth) {
                flush()
            }

            if (paragraph == null) {
                paragraph = StringBuilder()
                paragraph_depth = depth
            }

            if (is_soft_break) {
                paragraph!!.append(if (delsp) line.dropLast(1) else line)
            } else {
                paragraph!!.append(line)
                flush()
            }
        }

        flush()

        return output.joinToString("\n")
    }

    fun looks_flowed(text: String): Boolean {
        if (text.isEmpty()) return false

        val lines = text.replace("\r\n", "\n").replace("\r", "\n").split("\n")

        for (i in 0 until lines.size - 1) {
            val line = lines[i]

            if (line == SIGNATURE_SEPARATOR) continue
            if (line.endsWith(" ") && lines[i + 1].isNotEmpty()) return true
        }

        return false
    }
}
