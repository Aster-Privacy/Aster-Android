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
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class AsterProfileResolverPrimeTest {

    private fun resolver(): AsterProfileResolver = AsterProfileResolver(
        dagger.Lazy<AuthApi> { throw IllegalStateException("no network in this test") },
    )

    @Test
    fun `own alias shows the account picture`() {
        val subject = resolver()
        subject.prime(
            email = "Support@AsterMail.org",
            display_name = null,
            profile_picture = "https://cdn.example/photo.png",
            profile_color = "#123456",
        )
        val profile = subject.profiles.value["support@astermail.org"]
        assertEquals("https://cdn.example/photo.png", profile?.profile_picture)
        assertEquals("#123456", profile?.profile_color)
    }

    @Test
    fun `an alias with its own picture keeps it`() {
        val subject = resolver()
        subject.prime("alias@astermail.org", null, "https://cdn.example/alias.png", "#abcdef")
        subject.prime("alias@astermail.org", null, null, "#abcdef")
        assertEquals(
            "https://cdn.example/alias.png",
            subject.profiles.value["alias@astermail.org"]?.profile_picture,
        )
    }

    @Test
    fun `addresses outside our domains are never primed`() {
        val subject = resolver()
        subject.prime("me@example.com", null, "https://cdn.example/photo.png", "#123456")
        assertNull(subject.profiles.value["me@example.com"])
    }

    @Test
    fun `clearing forgets every primed address`() {
        val subject = resolver()
        subject.prime("me@astermail.org", null, "https://cdn.example/photo.png", null)
        subject.clear()
        val deadline = System.currentTimeMillis() + 2000
        while (subject.profiles.value.isNotEmpty() && System.currentTimeMillis() < deadline) {
            Thread.sleep(10)
        }
        assertNull(subject.profiles.value["me@astermail.org"])
    }
}
