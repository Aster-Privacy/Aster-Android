// Aster Mail - Privacy-first encrypted email
// Copyright (C) 2026 Aster Privacy
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

package org.astermail.android.mail

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ResumeRetryTest {
    @Test
    fun a_folder_that_did_not_reload_after_the_return_is_fetched_again() {
        assertTrue(resume_retry_due(loaded_at = 1_000L, resumed_at = 5_000L))
        assertTrue(resume_retry_due(loaded_at = null, resumed_at = 5_000L))
    }

    @Test
    fun a_folder_that_reloaded_after_the_return_is_left_alone() {
        assertFalse(resume_retry_due(loaded_at = 5_000L, resumed_at = 5_000L))
        assertFalse(resume_retry_due(loaded_at = 6_200L, resumed_at = 5_000L))
    }
}
