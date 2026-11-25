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

package org.astermail.android.ui.drawer

import android.app.Application
import android.content.Context
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], application = Application::class)
class sidebar_show_all_store_test {

    private lateinit var context: Context

    @Before
    fun set_up() {
        context = ApplicationProvider.getApplicationContext()
        context.getSharedPreferences("aster_sidebar", Context.MODE_PRIVATE).edit().clear().commit()
    }

    @Test
    fun has_no_stored_choice_until_you_expand_the_list() {
        assertNull(sidebar_show_all_store.load(context, sidebar_show_all_section.folders, "account_one"))
    }

    @Test
    fun keeps_the_expanded_list_for_the_same_account_after_a_restart() {
        sidebar_show_all_store.save(context, sidebar_show_all_section.folders, "account_one", true)

        assertEquals(true, sidebar_show_all_store.load(context, sidebar_show_all_section.folders, "account_one"))
        assertNull(sidebar_show_all_store.load(context, sidebar_show_all_section.folders, "account_two"))
        assertNull(sidebar_show_all_store.load(context, sidebar_show_all_section.aliases, "account_one"))
    }

    @Test
    fun keeps_a_collapsed_choice_after_a_restart() {
        sidebar_show_all_store.save(context, sidebar_show_all_section.aliases, "account_one", true)
        sidebar_show_all_store.save(context, sidebar_show_all_section.aliases, "account_one", false)

        assertEquals(false, sidebar_show_all_store.load(context, sidebar_show_all_section.aliases, "account_one"))
    }

    @Test
    fun forgets_the_choice_when_the_account_is_removed() {
        sidebar_show_all_store.save(context, sidebar_show_all_section.folders, "account_one", true)
        sidebar_show_all_store.save(context, sidebar_show_all_section.aliases, "account_one", true)
        sidebar_show_all_store.save(context, sidebar_show_all_section.folders, "account_two", true)

        sidebar_show_all_store.clear(context, "account_one")

        assertNull(sidebar_show_all_store.load(context, sidebar_show_all_section.folders, "account_one"))
        assertNull(sidebar_show_all_store.load(context, sidebar_show_all_section.aliases, "account_one"))
        assertEquals(true, sidebar_show_all_store.load(context, sidebar_show_all_section.folders, "account_two"))
    }

    @Test
    fun stores_only_a_true_or_false_value() {
        sidebar_show_all_store.save(context, sidebar_show_all_section.folders, "account_one", true)

        val stored = context.getSharedPreferences("aster_sidebar", Context.MODE_PRIVATE).all
        assertEquals(setOf("folders_show_all:account_one"), stored.keys)
        assertTrue(stored.values.all { it is Boolean })
        assertFalse(stored.values.any { it is String || it is Set<*> })
    }
}
