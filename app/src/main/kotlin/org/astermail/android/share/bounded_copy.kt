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

package org.astermail.android.share

import java.io.InputStream
import java.io.OutputStream

sealed class BoundedCopyResult {
    data class Copied(val bytes: Long) : BoundedCopyResult()
    data class Exceeded(val limit: Long) : BoundedCopyResult()
}

fun bounded_copy(input: InputStream, output: OutputStream, max_bytes: Long, buffer_size: Int = 64 * 1024): BoundedCopyResult {
    if (max_bytes < 0) return BoundedCopyResult.Exceeded(max_bytes)
    val buffer = ByteArray(buffer_size.coerceAtLeast(1))
    var total = 0L
    while (true) {
        val read = input.read(buffer)
        if (read < 0) break
        if (read == 0) continue
        if (total + read > max_bytes) return BoundedCopyResult.Exceeded(max_bytes)
        output.write(buffer, 0, read)
        total += read
    }
    output.flush()
    return BoundedCopyResult.Copied(total)
}
