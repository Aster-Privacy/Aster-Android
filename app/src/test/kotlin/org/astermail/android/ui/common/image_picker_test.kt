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
package org.astermail.android.ui.common

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ImagePickerTest {

    private val document_fallbacks = listOf(image_pick_source.open_document, image_pick_source.get_content)

    @Test
    fun android_13_tries_system_picker_first() {
        val sources = image_pick_sources(33, 0, has_androidx_picker = false, has_gms_picker = false)
        assertEquals(listOf(image_pick_source.system_picker) + document_fallbacks, sources)
    }

    @Test
    fun android_11_with_updated_media_provider_still_falls_back_to_documents() {
        val sources = image_pick_sources(30, 2, has_androidx_picker = false, has_gms_picker = false)
        assertEquals(listOf(image_pick_source.system_picker) + document_fallbacks, sources)
    }

    @Test
    fun android_11_without_extension_skips_system_picker() {
        val sources = image_pick_sources(30, 1, has_androidx_picker = false, has_gms_picker = false)
        assertEquals(document_fallbacks, sources)
    }

    @Test
    fun android_8_uses_document_pickers() {
        val sources = image_pick_sources(26, 0, has_androidx_picker = false, has_gms_picker = false)
        assertEquals(document_fallbacks, sources)
    }

    @Test
    fun resolvable_backported_pickers_come_before_documents() {
        val sources = image_pick_sources(29, 0, has_androidx_picker = true, has_gms_picker = true)
        assertEquals(
            listOf(image_pick_source.androidx_picker, image_pick_source.gms_picker) + document_fallbacks,
            sources,
        )
    }

    @Test
    fun every_chain_ends_with_get_content() {
        for (sdk in 26..35) {
            for (ext in 0..3) {
                val sources = image_pick_sources(sdk, ext, has_androidx_picker = sdk % 2 == 0, has_gms_picker = ext % 2 == 0)
                assertEquals(document_fallbacks, sources.takeLast(2))
                assertEquals(sources.size, sources.toSet().size)
            }
        }
    }

    @Test
    fun system_picker_availability_matches_platform_rule() {
        assertTrue(is_system_photo_picker_available(34, 0))
        assertTrue(is_system_photo_picker_available(31, 2))
        assertFalse(is_system_photo_picker_available(31, 1))
        assertFalse(is_system_photo_picker_available(29, 5))
    }

    @Test
    fun single_system_pick_has_no_extras() {
        val spec = image_pick_spec_for(image_pick_source.system_picker, multiple = false)
        assertEquals(image_pick_system_action, spec.action)
        assertNull(spec.max_items_extra)
        assertFalse(spec.openable)
        assertFalse(spec.allow_multiple)
    }

    @Test
    fun multiple_pick_uses_each_pickers_own_limit_extra() {
        assertEquals(image_pick_system_max_extra, image_pick_spec_for(image_pick_source.system_picker, true).max_items_extra)
        assertEquals(image_pick_androidx_max_extra, image_pick_spec_for(image_pick_source.androidx_picker, true).max_items_extra)
        assertEquals(image_pick_gms_max_extra, image_pick_spec_for(image_pick_source.gms_picker, true).max_items_extra)
    }

    @Test
    fun document_pickers_are_openable_and_honor_multiple() {
        val open_single = image_pick_spec_for(image_pick_source.open_document, false)
        val get_multiple = image_pick_spec_for(image_pick_source.get_content, true)
        assertEquals("android.intent.action.OPEN_DOCUMENT", open_single.action)
        assertEquals("android.intent.action.GET_CONTENT", get_multiple.action)
        assertTrue(open_single.openable && get_multiple.openable)
        assertFalse(open_single.allow_multiple)
        assertTrue(get_multiple.allow_multiple)
        assertNull(get_multiple.max_items_extra)
    }

    @Test
    fun image_only_pickers_keep_the_image_type() {
        for (source in image_pick_source.values()) {
            val types = image_pick_types_for(image_pick_spec_for(source, true), include_videos = false)
            assertEquals("image/*", types.type)
            assertNull(types.extra_mime_types)
        }
    }

    @Test
    fun media_pickers_show_photos_and_videos_without_a_type_filter() {
        for (source in listOf(image_pick_source.system_picker, image_pick_source.androidx_picker, image_pick_source.gms_picker)) {
            val types = image_pick_types_for(image_pick_spec_for(source, true), include_videos = true)
            assertNull(types.type)
            assertNull(types.extra_mime_types)
        }
    }

    @Test
    fun document_pickers_ask_for_photos_and_videos() {
        for (source in listOf(image_pick_source.open_document, image_pick_source.get_content)) {
            val types = image_pick_types_for(image_pick_spec_for(source, true), include_videos = true)
            assertEquals("*/*", types.type)
            assertEquals(listOf("image/*", "video/*"), types.extra_mime_types)
        }
    }
}
