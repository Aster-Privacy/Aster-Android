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

package org.astermail.android.share

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ShareStreamPolicyTest {

    private val own = "org.astermail.android"

    private fun caller(
        has_read_grant: Boolean = true,
        caller_is_system: Boolean = false,
        app_reads_shared_storage: Boolean = false,
    ) = ShareCallerContext(own, has_read_grant, caller_is_system, app_reads_shared_storage)

    private fun allowed(
        authority: String?,
        context: ShareCallerContext = caller(),
        access: ShareCallerAccess = ShareCallerAccess.UNKNOWN,
        scheme: String? = "content",
    ) = is_share_stream_allowed(scheme, authority, context, access)

    @Test
    fun the_apps_own_providers_are_refused_whatever_the_caller_claims() {
        val trusted = caller(has_read_grant = true, caller_is_system = true)
        for (authority in listOf(
            "org.astermail.android.fileprovider",
            "org.astermail.android.account_link",
            "org.astermail.android",
            "ORG.ASTERMAIL.ANDROID.FileProvider",
            "10@org.astermail.android.fileprovider",
        )) {
            assertFalse(authority, allowed(authority, trusted, ShareCallerAccess.GRANTED))
        }
    }

    @Test
    fun another_package_with_a_similar_name_is_not_the_app() {
        assertTrue(allowed("org.astermail.androidx.files"))
    }

    @Test
    fun contacts_from_a_caller_that_cannot_read_them_are_refused() {
        for (authority in listOf("com.android.contacts", "contacts", "0@com.android.contacts")) {
            assertFalse(authority, allowed(authority, caller(has_read_grant = true), ShareCallerAccess.UNKNOWN))
            assertFalse(authority, allowed(authority, caller(has_read_grant = true), ShareCallerAccess.DENIED))
            assertFalse(
                authority,
                allowed(authority, caller(has_read_grant = true, caller_is_system = true), ShareCallerAccess.DENIED),
            )
        }
    }

    @Test
    fun a_guarded_provider_needs_the_read_grant_flag() {
        val no_grant = caller(has_read_grant = false, caller_is_system = true)
        assertFalse(allowed("com.android.contacts", no_grant, ShareCallerAccess.GRANTED))
        assertFalse(allowed("com.android.providers.downloads.documents", no_grant, ShareCallerAccess.UNKNOWN))
    }

    @Test
    fun a_guarded_provider_is_accepted_when_the_platform_confirms_the_caller() {
        assertTrue(allowed("com.android.contacts", caller(), ShareCallerAccess.GRANTED))
        assertTrue(allowed("com.android.providers.media.documents", caller(), ShareCallerAccess.GRANTED))
    }

    @Test
    fun without_a_platform_answer_only_a_system_caller_is_accepted() {
        val system = caller(caller_is_system = true)
        assertTrue(allowed("com.android.providers.media.documents", system, ShareCallerAccess.UNKNOWN))
        assertTrue(allowed("com.android.externalstorage.documents", system, ShareCallerAccess.UNKNOWN))
        assertFalse(allowed("com.android.providers.media.documents", caller(), ShareCallerAccess.UNKNOWN))
        assertFalse(allowed("com.android.externalstorage.documents", caller(), ShareCallerAccess.UNKNOWN))
        assertFalse(allowed("sms", caller(), ShareCallerAccess.UNKNOWN))
        assertFalse(allowed("settings", caller(), ShareCallerAccess.UNKNOWN))
        assertFalse(allowed("com.android.calendar", caller(), ShareCallerAccess.UNKNOWN))
    }

    @Test
    fun media_is_guarded_only_when_the_app_can_read_shared_storage_itself() {
        val reads_storage = caller(app_reads_shared_storage = true)
        assertFalse(allowed("media", reads_storage, ShareCallerAccess.UNKNOWN))
        assertTrue(allowed("media", reads_storage, ShareCallerAccess.GRANTED))
        assertTrue(allowed("media", caller(app_reads_shared_storage = false), ShareCallerAccess.UNKNOWN))
    }

    @Test
    fun an_ordinary_provider_is_accepted() {
        assertTrue(allowed("com.example.gallery.fileprovider"))
        assertTrue(allowed("com.example.gallery.fileprovider", caller(has_read_grant = false)))
    }

    @Test
    fun only_content_uris_are_accepted() {
        assertFalse(allowed("", scheme = "file"))
        assertFalse(allowed("com.example.gallery.fileprovider", scheme = "file"))
        assertFalse(allowed("com.example.gallery.fileprovider", scheme = "http"))
        assertFalse(allowed("com.example.gallery.fileprovider", scheme = null))
        assertFalse(allowed(null))
        assertFalse(allowed("  "))
        assertTrue(allowed("com.example.gallery.fileprovider", scheme = "CONTENT"))
    }

    @Test
    fun the_referrer_package_comes_only_from_an_app_referrer() {
        assertEquals("com.example.app", share_referrer_package("android-app", "com.example.app"))
        assertNull(share_referrer_package("https", "example.com"))
        assertNull(share_referrer_package("android-app", ""))
        assertNull(share_referrer_package(null, null))
    }
}
