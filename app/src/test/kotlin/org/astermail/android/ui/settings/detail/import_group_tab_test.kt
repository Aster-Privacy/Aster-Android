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
package org.astermail.android.ui.settings.detail

import org.junit.Assert.assertEquals
import org.junit.Test

class import_group_tab_test {
    @Test
    fun `opens the import tab for the import route`() {
        assertEquals(import_tab_import, import_group_tab_for_route("import"))
    }

    @Test
    fun `opens the external accounts tab for its routes`() {
        assertEquals(import_tab_external_accounts, import_group_tab_for_route("external_accounts"))
        assertEquals(import_tab_external_accounts, import_group_tab_for_route("external_accounts_gmail"))
    }

    @Test
    fun `opens the export tab for the export route`() {
        assertEquals(import_tab_export, import_group_tab_for_route("export"))
    }

    @Test
    fun `lists the tabs in the same order as the web client`() {
        assertEquals(listOf("import", "external_accounts", "export"), import_group_tabs)
    }
}
