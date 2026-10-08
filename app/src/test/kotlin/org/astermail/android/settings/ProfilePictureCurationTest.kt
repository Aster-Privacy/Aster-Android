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

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test

class ProfilePictureCurationTest {
    private fun item(slug: String) = GalleryItem(slug = slug, category = "space", credit = null)

    @Test
    fun featured_first_in_rank_order_rest_kept_in_place() {
        val items = listOf("zeta", "jupiter", "alpha", "blue_marble").map(::item)
        assertEquals(
            listOf("blue_marble", "jupiter", "zeta", "alpha"),
            curate_gallery_items(items).map { it.slug },
        )
    }

    @Test
    fun hidden_slugs_dropped() {
        val items = listOf("deep_field", "mars").map(::item)
        assertEquals(listOf("mars"), curate_gallery_items(items).map { it.slug })
    }

    @Test
    fun featured_never_hidden() {
        featured_gallery_slugs.forEach { assertFalse(it, it in hidden_gallery_slugs) }
    }
}
