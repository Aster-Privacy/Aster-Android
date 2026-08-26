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

package org.astermail.android.di

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class StorageModuleResetTest {

    @Test
    fun `a single open failure never deletes an existing database`() {
        assertFalse(StorageModule.should_reset_database(true, true, 1))
    }

    @Test
    fun `a second open failure still never deletes an existing database`() {
        assertFalse(StorageModule.should_reset_database(true, true, 2))
    }

    @Test
    fun `a third consecutive open failure allows the database to be rebuilt`() {
        assertTrue(StorageModule.should_reset_database(true, true, 3))
    }

    @Test
    fun `a database that predates the encrypted store is never deleted`() {
        assertFalse(StorageModule.should_reset_database(true, false, 9))
    }

    @Test
    fun `a missing database is rebuilt on the first failure`() {
        assertTrue(StorageModule.should_reset_database(false, true, 1))
    }
}
