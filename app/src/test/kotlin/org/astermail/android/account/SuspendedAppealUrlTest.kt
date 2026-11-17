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

package org.astermail.android.account

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class SuspendedAppealUrlTest {

    @Test
    fun `https appeal links are kept`() {
        assertEquals(
            "https://example.org/appeal",
            SuspendedAccountViewModel.safe_appeal_url(" https://example.org/appeal "),
        )
    }

    @Test
    fun `missing or insecure appeal links hide the appeal row`() {
        assertNull(SuspendedAccountViewModel.safe_appeal_url(null))
        assertNull(SuspendedAccountViewModel.safe_appeal_url(""))
        assertNull(SuspendedAccountViewModel.safe_appeal_url("http://example.org/appeal"))
        assertNull(SuspendedAccountViewModel.safe_appeal_url("javascript:alert(1)"))
    }
}
