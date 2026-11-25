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

import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import io.mockk.slot
import kotlinx.coroutines.runBlocking
import org.astermail.android.api.auth.AuthApi
import org.astermail.android.api.auth.BatchProfilesResponse
import org.astermail.android.api.auth.PublicProfile
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class AsterProfileResolverLookupTest {

    private val picture = "data:image/png;base64,iVBORw0KGgo="

    @Test
    fun `one request looks up every new aster sender`() = runBlocking {
        val api = mockk<AuthApi>()
        val asked = slot<List<String>>()
        coEvery { api.batch_profiles(capture(asked)) } returns BatchProfilesResponse(
            mapOf("one@astermail.org" to PublicProfile(profile_picture = picture)),
        )
        val subject = AsterProfileResolver(dagger.Lazy { api })
        subject.resolve_pictures(listOf("One@AsterMail.org", "two@aster.cx", "one@astermail.org", "outside@example.com"))
        assertEquals(listOf("one@astermail.org", "two@aster.cx"), asked.captured)
        assertEquals(picture, subject.profiles.value["one@astermail.org"]?.profile_picture)
        assertNull(subject.profiles.value["outside@example.com"])
    }

    @Test
    fun `a resolved sender is not looked up again`() = runBlocking {
        val api = mockk<AuthApi>()
        coEvery { api.batch_profiles(any()) } returns BatchProfilesResponse(
            mapOf("one@astermail.org" to PublicProfile(profile_picture = picture)),
        )
        val subject = AsterProfileResolver(dagger.Lazy { api })
        subject.resolve_pictures(listOf("one@astermail.org", "two@astermail.org"))
        assertEquals(picture, subject.resolve_picture("one@astermail.org"))
        assertNull(subject.resolve_picture("two@astermail.org"))
        coVerify(exactly = 1) { api.batch_profiles(any()) }
    }

    @Test
    fun `outside senders never reach the network`() = runBlocking {
        val api = mockk<AuthApi>()
        val subject = AsterProfileResolver(dagger.Lazy { api })
        subject.resolve_pictures(listOf("someone@example.com"))
        assertNull(subject.resolve_picture("someone@example.com"))
        coVerify(exactly = 0) { api.batch_profiles(any()) }
    }
}
