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

import org.junit.Assert.assertEquals
import org.junit.Test

class SearchIndexResumedProgressTest {

    @Test
    fun `resumed progress starts from the stored count and stays within the total`() {
        assertEquals(0, resumed_progress(0, 500))
        assertEquals(0, resumed_progress(200, 0))
        assertEquals(200, resumed_progress(200, 500))
        assertEquals(500, resumed_progress(900, 500))
    }
}
