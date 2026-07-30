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

package org.astermail.android.ui.settings

import android.content.res.Configuration
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import java.text.NumberFormat
import java.util.Locale
import org.astermail.android.R
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class KidsSeatsStringsTest {

    private val locales = listOf(
        "en", "ar", "de", "es", "fr", "it", "ja", "ko", "nl", "pl", "pt", "ru", "tr", "zh-CN",
    )

    @Test
    fun seat_strings_format_in_every_shipped_locale() {
        val base = InstrumentationRegistry.getInstrumentation().targetContext

        locales.forEach { tag ->
            val locale = Locale.forLanguageTag(tag)
            val config = Configuration(base.resources.configuration)
            config.setLocale(locale)
            val context = base.createConfigurationContext(config)

            val digits = NumberFormat.getIntegerInstance(locale)
            val five = digits.format(5)
            val six = digits.format(6)
            val one = digits.format(1)

            val used = context.getString(R.string.kids_seats_used, 5, 6)
            val free = context.getString(R.string.kids_seats_free, 1)

            assertTrue(tag, used.isNotBlank())
            assertTrue("$tag: $used", used.contains(five) && used.contains(six))
            assertTrue(tag, free.isNotBlank())
            assertTrue("$tag: $free", free.contains(one))
            assertTrue("$tag: $used", !used.contains("%"))
            assertTrue("$tag: $free", !free.contains("%"))
        }
    }
}
