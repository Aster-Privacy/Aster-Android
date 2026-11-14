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

package org.astermail.android.ui.mail

import org.astermail.android.mail.INLINE_IMAGE_MAX_BYTES
import org.junit.Assert.assertTrue
import org.junit.Test

class lightbox_data_uri_limit_test {
    private fun data_uri_chars(image_bytes: Long): Long =
        "data:image/jpeg;base64,".length + (image_bytes + 2) / 3 * 4

    @Test
    fun an_18_mb_image_fits_the_lightbox_limit() {
        assertTrue(data_uri_chars(18L * 1024 * 1024) <= LIGHTBOX_MAX_DATA_URI_CHARS)
    }

    @Test
    fun the_largest_inline_image_fits_the_lightbox_limit() {
        assertTrue(data_uri_chars(INLINE_IMAGE_MAX_BYTES) <= LIGHTBOX_MAX_DATA_URI_CHARS)
    }

    @Test
    fun the_lightbox_limit_stays_bounded() {
        assertTrue(LIGHTBOX_MAX_DATA_URI_CHARS < data_uri_chars(INLINE_IMAGE_MAX_BYTES + 1024 * 1024))
    }
}
