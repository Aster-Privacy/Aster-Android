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

import org.astermail.android.api.auth.AuthApi
import org.astermail.android.api.auth.PublicProfile
import org.astermail.android.ui.mail.avatar_shows_initials
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class aster_profile_store_test {

    @get:Rule
    val tmp = TemporaryFolder()

    private fun resolver(): AsterProfileResolver = AsterProfileResolver(
        dagger.Lazy<AuthApi> { throw IllegalStateException("no network in this test") },
    )

    @Test
    fun `store keeps pictures and colors and drops empty or foreign entries`() {
        val encoded = encode_profile_store(
            mapOf(
                "a@astermail.org" to PublicProfile(profile_picture = "https://cdn.example/a.png", profile_color = "#112233"),
                "b@astermail.org" to PublicProfile(display_name = "Only name"),
                "c@astermail.org" to null,
                "d@aster.cx" to PublicProfile(profile_color = "#445566"),
            ),
        )
        val decoded = decode_profile_store(encoded)
        assertEquals(setOf("a@astermail.org", "d@aster.cx"), decoded.keys)
        assertEquals("https://cdn.example/a.png", decoded["a@astermail.org"]?.profile_picture)
        assertEquals(
            emptyMap<String, PublicProfile>(),
            decode_profile_store("""{"x@example.com":{"profile_color":"#000000"}}"""),
        )
    }

    @Test
    fun `oversized pictures are not stored`() {
        val big = "data:image/png;base64," + "A".repeat(60 * 1024)
        val decoded = decode_profile_store(
            encode_profile_store(mapOf("a@astermail.org" to PublicProfile(profile_picture = big, profile_color = "#112233"))),
        )
        assertNull(decoded["a@astermail.org"]?.profile_picture)
        assertEquals("#112233", decoded["a@astermail.org"]?.profile_color)
    }

    @Test
    fun `attached store seeds profiles on the first read`() {
        val dir = tmp.newFolder()
        java.io.File(dir, "aster_peer_profiles.json").writeText(
            encode_profile_store(mapOf("a@astermail.org" to PublicProfile(profile_picture = "https://cdn.example/a.png"))),
        )
        val subject = resolver()
        subject.attach_store(dir)
        assertEquals("https://cdn.example/a.png", subject.profiles.value["a@astermail.org"]?.profile_picture)
    }

    @Test
    fun `corrupt store is ignored`() {
        val dir = tmp.newFolder()
        java.io.File(dir, "aster_peer_profiles.json").writeText("{not json")
        val subject = resolver()
        subject.attach_store(dir)
        assertTrue(subject.profiles.value.isEmpty())
    }

    @Test
    fun `initials show only after a failure or the wait expires`() {
        assertFalse(avatar_shows_initials(loaded = false, failed = false, wait_expired = false))
        assertTrue(avatar_shows_initials(loaded = false, failed = true, wait_expired = false))
        assertTrue(avatar_shows_initials(loaded = false, failed = false, wait_expired = true))
        assertFalse(avatar_shows_initials(loaded = true, failed = true, wait_expired = true))
    }
}
