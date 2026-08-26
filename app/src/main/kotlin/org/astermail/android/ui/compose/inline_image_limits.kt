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

package org.astermail.android.ui.compose

enum class InlineImageRejection {
    TOO_MANY,
    TOO_LARGE,
    TOTAL_TOO_LARGE,
}

fun inline_image_rejection(
    existing_count: Int,
    existing_bytes: Long,
    new_bytes: Long,
    max_count: Int,
    max_single_bytes: Long,
    max_total_bytes: Long,
): InlineImageRejection? {
    if (existing_count >= max_count) return InlineImageRejection.TOO_MANY
    if (new_bytes > max_single_bytes) return InlineImageRejection.TOO_LARGE
    if (existing_bytes + new_bytes > max_total_bytes) return InlineImageRejection.TOTAL_TOO_LARGE
    return null
}
