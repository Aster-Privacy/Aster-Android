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

package org.astermail.android

import org.astermail.android.mail.OwnAddressAvatarSource
import org.astermail.android.mail.OwnAddressAvatars
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test

class OwnAddressAvatarsTest {

    @Before
    fun reset_before() = OwnAddressAvatars.clear()

    @After
    fun reset_after() = OwnAddressAvatars.clear()

    @Test
    fun returns_null_when_nothing_published() {
        assertNull(OwnAddressAvatars.get("alias@astermail.org"))
    }

    @Test
    fun resolves_a_published_alias_picture() {
        OwnAddressAvatars.publish(
            OwnAddressAvatarSource.ALIAS,
            listOf("alias@astermail.org" to "data:image/png;base64,aa"),
        )
        assertEquals("data:image/png;base64,aa", OwnAddressAvatars.get("alias@astermail.org"))
    }

    @Test
    fun ignores_case_dots_and_whitespace() {
        OwnAddressAvatars.publish(
            OwnAddressAvatarSource.ALIAS,
            listOf("First.Last@AsterMail.org" to "pic"),
        )
        assertEquals("pic", OwnAddressAvatars.get("  firstlast@astermail.ORG "))
    }

    @Test
    fun resolves_a_custom_domain_address() {
        OwnAddressAvatars.publish(
            OwnAddressAvatarSource.DOMAIN_ADDRESS,
            listOf("me@example.com" to "domain_pic"),
        )
        assertEquals("domain_pic", OwnAddressAvatars.get("me@example.com"))
    }

    @Test
    fun skips_blank_pictures_and_malformed_addresses() {
        OwnAddressAvatars.publish(
            OwnAddressAvatarSource.ALIAS,
            listOf(
                "empty@astermail.org" to "",
                "blank@astermail.org" to "   ",
                "missing@astermail.org" to null,
                "not-an-address" to "pic",
            ),
        )
        assertNull(OwnAddressAvatars.get("empty@astermail.org"))
        assertNull(OwnAddressAvatars.get("blank@astermail.org"))
        assertNull(OwnAddressAvatars.get("missing@astermail.org"))
        assertNull(OwnAddressAvatars.get("not-an-address"))
    }

    @Test
    fun a_later_publish_of_the_same_source_replaces_it() {
        OwnAddressAvatars.publish(
            OwnAddressAvatarSource.ALIAS,
            listOf("alias@astermail.org" to "old"),
        )
        OwnAddressAvatars.publish(OwnAddressAvatarSource.ALIAS, emptyList())
        assertNull(OwnAddressAvatars.get("alias@astermail.org"))
    }

    @Test
    fun sources_do_not_overwrite_each_other() {
        OwnAddressAvatars.publish(
            OwnAddressAvatarSource.ALIAS,
            listOf("a@astermail.org" to "alias_pic"),
        )
        OwnAddressAvatars.publish(
            OwnAddressAvatarSource.GHOST,
            listOf("g@astermail.org" to "ghost_pic"),
        )
        assertEquals("alias_pic", OwnAddressAvatars.get("a@astermail.org"))
        assertEquals("ghost_pic", OwnAddressAvatars.get("g@astermail.org"))
    }

    @Test
    fun the_alias_source_wins_over_a_later_source_for_the_same_address() {
        OwnAddressAvatars.publish(
            OwnAddressAvatarSource.GHOST,
            listOf("shared@astermail.org" to "ghost_pic"),
        )
        OwnAddressAvatars.publish(
            OwnAddressAvatarSource.ALIAS,
            listOf("shared@astermail.org" to "alias_pic"),
        )
        assertEquals("alias_pic", OwnAddressAvatars.get("shared@astermail.org"))
    }

    @Test
    fun clear_drops_every_entry() {
        OwnAddressAvatars.publish(
            OwnAddressAvatarSource.ALIAS,
            listOf("a@astermail.org" to "pic"),
        )
        OwnAddressAvatars.clear()
        assertNull(OwnAddressAvatars.get("a@astermail.org"))
    }
}
