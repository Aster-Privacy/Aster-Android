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

package org.astermail.android.crypto.guard

import java.nio.ByteBuffer
import java.util.Locale
import javax.crypto.Mac
import javax.crypto.spec.SecretKeySpec

object GuardCode {
    const val ALPHABET = "23456789BCDFGHJKMNPQRTVWXY"
    const val STEP_SECONDS = 30L
    const val CODE_LEN = 5
    const val RESCUE_LEN = 25
    private const val RESCUE_GROUP = 5

    fun at_step(secret: ByteArray, step: Long): String {
        val mac = Mac.getInstance("HmacSHA1")
        mac.init(SecretKeySpec(secret, "HmacSHA1"))
        val digest = mac.doFinal(ByteBuffer.allocate(8).putLong(step).array())
        val offset = digest[19].toInt() and 0x0f
        var full = ((digest[offset].toLong() and 0xff) shl 24) or
            ((digest[offset + 1].toLong() and 0xff) shl 16) or
            ((digest[offset + 2].toLong() and 0xff) shl 8) or
            (digest[offset + 3].toLong() and 0xff)
        full = full and 0x7fffffffL
        val out = StringBuilder(CODE_LEN)
        repeat(CODE_LEN) {
            out.append(ALPHABET[(full % ALPHABET.length).toInt()])
            full /= ALPHABET.length
        }
        digest.fill(0)
        return out.toString()
    }

    fun step_of(unix_seconds: Long): Long = Math.floorDiv(unix_seconds, STEP_SECONDS)

    fun now(secret: ByteArray, unix_seconds: Long): String = at_step(secret, step_of(unix_seconds))

    fun seconds_left(unix_seconds: Long): Int = (STEP_SECONDS - Math.floorMod(unix_seconds, STEP_SECONDS)).toInt()

    fun normalize_rescue(input: String): String? {
        val cleaned = input.uppercase(Locale.ROOT).filter { it != '-' && it != ' ' }
        if (cleaned.length != RESCUE_LEN) return null
        if (!cleaned.all { it in ALPHABET }) return null
        return cleaned
    }

    fun format_rescue(code: String): String = code.chunked(RESCUE_GROUP).joinToString("-")
}
