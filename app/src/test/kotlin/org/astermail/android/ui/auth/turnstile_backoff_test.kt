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

package org.astermail.android.ui.auth

import org.junit.Assert.assertEquals
import org.junit.Test

class turnstile_backoff_test {
    @Test
    fun automatic_resets_back_off_and_stay_bounded() {
        assertEquals(1_000L, turnstile_reset_backoff_ms(0))
        assertEquals(2_000L, turnstile_reset_backoff_ms(1))
        assertEquals(4_000L, turnstile_reset_backoff_ms(2))
        assertEquals(16_000L, turnstile_reset_backoff_ms(9))
        assertEquals(1_000L, turnstile_reset_backoff_ms(-3))
    }
}
