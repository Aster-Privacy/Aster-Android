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

package org.astermail.android.settings

import android.content.Context
import android.content.SharedPreferences
import android.content.res.Configuration
import android.content.res.Resources
import io.mockk.every
import io.mockk.mockk
import org.junit.After
import org.junit.Assert.assertNotSame
import org.junit.Assert.assertSame
import org.junit.Test
import java.util.Locale

class AppLanguagePrintServiceTest {

    private val saved_locale: Locale = Locale.getDefault()

    @After
    fun restore_locale() {
        Locale.setDefault(saved_locale)
    }

    private fun activity_base(stored_code: String?, localized: Context, activity_bound_service: Any): Context {
        val prefs = mockk<SharedPreferences>()
        every { prefs.getString("code", null) } returns stored_code
        val resources = mockk<Resources>()
        every { resources.configuration } returns Configuration()
        val base = mockk<Context>()
        every { base.getSharedPreferences(any(), any()) } returns prefs
        every { base.resources } returns resources
        every { base.createConfigurationContext(any()) } returns localized
        every { base.getSystemService(Context.PRINT_SERVICE) } returns activity_bound_service
        return base
    }

    @Test
    fun print_service_is_resolved_against_the_activity_base_context() {
        val activity_bound = Any()
        val detached = Any()
        val localized = mockk<Context>()
        every { localized.getSystemService(Context.PRINT_SERVICE) } returns detached

        val applied = app_language.apply(activity_base("de", localized, activity_bound))

        assertSame(activity_bound, applied.getSystemService(Context.PRINT_SERVICE))
    }

    @Test
    fun other_services_come_from_the_localized_context() {
        val inflater = Any()
        val localized = mockk<Context>()
        every { localized.getSystemService(Context.LAYOUT_INFLATER_SERVICE) } returns inflater

        val applied = app_language.apply(activity_base("fr", localized, Any()))

        assertNotSame(localized, applied)
        assertSame(inflater, applied.getSystemService(Context.LAYOUT_INFLATER_SERVICE))
    }

    @Test
    fun base_is_returned_unchanged_without_a_stored_language() {
        val base = activity_base(null, mockk(), Any())

        assertSame(base, app_language.apply(base))
    }
}
