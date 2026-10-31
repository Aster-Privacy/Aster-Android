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

package org.astermail.android.api.mail

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class DraftUploadTimeoutTest {

    @Test
    fun small_drafts_keep_the_base_timeout() {
        assertEquals(60_000L, draft_upload_timeout_ms(0))
        assertTrue(draft_upload_timeout_ms(4_000) < 61_000L)
    }

    @Test
    fun large_drafts_get_time_to_upload() {
        val ten_megabytes = 10 * 1024 * 1024
        assertTrue(draft_upload_timeout_ms(ten_megabytes) >= 5 * 60_000L)
    }

    @Test
    fun the_timeout_is_capped() {
        assertEquals(30 * 60_000L, draft_upload_timeout_ms(Int.MAX_VALUE))
        assertEquals(60_000L, draft_upload_timeout_ms(-1))
    }
}
