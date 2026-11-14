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

package org.astermail.android.ui.contacts

import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Test

class ContactAvatarPhotoStateTest {

    private data class FakePhoto(val source: String)

    private val photo_a = "data:image/png;base64,AAAA"
    private val photo_b = "data:image/png;base64,BBBB"

    private class FakeCache {
        val entries = LinkedHashMap<String, FakePhoto>()
        var decodes = 0
        fun get(key: String): FakePhoto? = entries[key]
        fun put(key: String, value: FakePhoto) { entries[key] = value }
        suspend fun decode(key: String): FakePhoto? {
            decodes += 1
            return FakePhoto(key)
        }
    }

    private suspend fun resolve(
        current: KeyedContactPhoto<FakePhoto>,
        avatar_url: String,
        cache: FakeCache,
    ): KeyedContactPhoto<FakePhoto> = resolve_keyed_contact_photo(
        current = current,
        avatar_url = avatar_url,
        cached = cache::get,
        decode = cache::decode,
        store = cache::put,
    )

    @Test
    fun two_contacts_get_their_own_photo() = runTest {
        val cache = FakeCache()
        val a = resolve(KeyedContactPhoto(photo_a, null), photo_a, cache)
        val b = resolve(KeyedContactPhoto(photo_b, null), photo_b, cache)
        assertEquals(FakePhoto(photo_a), a.photo_for(photo_a))
        assertEquals(FakePhoto(photo_b), b.photo_for(photo_b))
    }

    @Test
    fun switching_from_a_photo_to_a_contact_without_one_shows_no_photo() = runTest {
        val cache = FakeCache()
        val a = resolve(KeyedContactPhoto(photo_a, null), photo_a, cache)
        assertNull(a.photo_for(""))
        val none = resolve(a, "", cache)
        assertNull(none.photo_for(""))
    }

    @Test
    fun switching_between_two_photos_shows_the_new_photo() = runTest {
        val cache = FakeCache()
        val a = resolve(KeyedContactPhoto(photo_a, null), photo_a, cache)
        assertNull(a.photo_for(photo_b))
        val b = resolve(a, photo_b, cache)
        assertEquals(FakePhoto(photo_b), b.photo_for(photo_b))
        val back = resolve(b, photo_a, cache)
        assertEquals(FakePhoto(photo_a), back.photo_for(photo_a))
        assertEquals(2, cache.decodes)
    }

    @Test
    fun remote_and_blank_urls_never_reuse_an_inline_photo() = runTest {
        val cache = FakeCache()
        val a = resolve(KeyedContactPhoto(photo_a, null), photo_a, cache)
        val remote = resolve(a, "https://example.org/a.png", cache)
        assertNull(remote.photo_for("https://example.org/a.png"))
    }

    @Test
    fun keeps_current_photo_without_decoding_again() = runTest {
        val cache = FakeCache()
        val a = resolve(KeyedContactPhoto(photo_a, null), photo_a, cache)
        val again = resolve(a, photo_a, cache)
        assertSame(a, again)
        assertEquals(1, cache.decodes)
    }

    @Test
    fun evicted_photo_is_decoded_again_for_the_right_contact() = runTest {
        val cache = FakeCache()
        resolve(KeyedContactPhoto(photo_a, null), photo_a, cache)
        val b = resolve(KeyedContactPhoto(photo_b, null), photo_b, cache)
        cache.entries.clear()
        val a = resolve(b, photo_a, cache)
        assertEquals(FakePhoto(photo_a), a.photo_for(photo_a))
        assertNull(a.photo_for(photo_b))
    }
}
