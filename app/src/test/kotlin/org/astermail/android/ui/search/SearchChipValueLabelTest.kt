//
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
// MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
// GNU Affero General Public License for more details.
//
// You should have received a copy of the GNU Affero General Public License
// along with this program.  If not, see <https://www.gnu.org/licenses/>.
//

package org.astermail.android.ui.search

import org.astermail.android.R
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class SearchChipValueLabelTest {

    private fun op(key: String, value: String, negated: Boolean = false) =
        SearchOperator(negated, key, value)

    @Test
    fun scope_values_use_translated_names() {
        assertEquals(R.string.search_scope_anywhere, operator_value_label(op("in", "anywhere")))
        assertEquals(R.string.folder_all_mail, operator_value_label(op("in", "all")))
        assertEquals(R.string.folder_inbox, operator_value_label(op("in", "inbox")))
        assertEquals(R.string.folder_drafts, operator_value_label(op("in", "draft")))
        assertEquals(R.string.folder_archive, operator_value_label(op("in", "archived")))
        assertEquals(R.string.folder_trash, operator_value_label(op("in", "trash", negated = true)))
    }

    @Test
    fun status_values_use_translated_names() {
        assertEquals(R.string.filter_unread, operator_value_label(op("is", "unread")))
        assertEquals(R.string.filter_starred, operator_value_label(op("is", "starred")))
        assertEquals(R.string.filter_encrypted, operator_value_label(op("is", "encrypted")))
    }

    @Test
    fun typed_values_stay_as_written() {
        assertNull(operator_value_label(op("in", "Receipts")))
        assertNull(operator_value_label(op("from", "inbox")))
        assertNull(operator_value_label(op("subject", "anywhere")))
        assertNull(operator_value_label(op("is", "read")))
    }
}
