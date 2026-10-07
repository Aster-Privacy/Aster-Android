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

package org.astermail.android.subscriptions

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

class UnsubscribedSendersStoreTest {
    private val key = ByteArray(UNSUBSCRIBED_TOKEN_KEY_BYTES) { it.toByte() }
    private val other_key = ByteArray(UNSUBSCRIBED_TOKEN_KEY_BYTES) { (it + 1).toByte() }

    private fun unsubscribed_sender_token(account: String?, sender: String): String =
        unsubscribed_sender_token(key, account, sender)

    private fun unsubscribed_account_key(account: String?): String =
        unsubscribed_account_key(key, account)

    private fun sha256_hex(value: String): String =
        java.security.MessageDigest.getInstance("SHA-256")
            .digest(value.toByteArray(Charsets.UTF_8))
            .joinToString("") { "%02x".format(it) }

    @Test
    fun sender_token_is_not_the_unkeyed_hash() {
        assertNotEquals(
            sha256_hex("me@astermail.org\nnews@example.com"),
            unsubscribed_sender_token("me@astermail.org", "news@example.com"),
        )
        assertNotEquals(
            sha256_hex("me@astermail.org"),
            unsubscribed_account_key("me@astermail.org"),
        )
    }

    @Test
    fun sender_token_and_account_slot_depend_on_the_device_key() {
        assertNotEquals(
            unsubscribed_sender_token(key, "me@astermail.org", "news@example.com"),
            unsubscribed_sender_token(other_key, "me@astermail.org", "news@example.com"),
        )
        assertNotEquals(
            unsubscribed_account_key(key, "me@astermail.org"),
            unsubscribed_account_key(other_key, "me@astermail.org"),
        )
    }

    @Test
    fun migration_drops_only_the_unkeyed_slots() {
        val legacy = legacy_unsubscribed_pref_keys(
            listOf("confirmed_ab12", "pending_ab12", "v2_confirmed_cd34", "v2_pending_cd34"),
        )
        assertEquals(setOf("confirmed_ab12", "pending_ab12"), legacy)
    }

    @Test
    fun sender_token_ignores_case_and_whitespace() {
        assertEquals(
            unsubscribed_sender_token("me@astermail.org", "news@example.com"),
            unsubscribed_sender_token(" Me@AsterMail.org ", "  News@Example.COM "),
        )
    }

    @Test
    fun sender_token_differs_per_account() {
        assertNotEquals(
            unsubscribed_sender_token("first@astermail.org", "news@example.com"),
            unsubscribed_sender_token("second@astermail.org", "news@example.com"),
        )
    }

    @Test
    fun sender_token_differs_per_sender() {
        assertNotEquals(
            unsubscribed_sender_token("me@astermail.org", "news@example.com"),
            unsubscribed_sender_token("me@astermail.org", "offers@example.com"),
        )
    }

    @Test
    fun sender_token_does_not_contain_the_address() {
        val token = unsubscribed_sender_token("me@astermail.org", "news@example.com")
        assertEquals(64, token.length)
        assertFalse(token.contains("news"))
        assertFalse(token.contains("@"))
    }

    @Test
    fun account_key_ignores_case_and_differs_per_account() {
        assertEquals(
            unsubscribed_account_key("me@astermail.org"),
            unsubscribed_account_key("ME@astermail.org "),
        )
        assertNotEquals(
            unsubscribed_account_key("me@astermail.org"),
            unsubscribed_account_key("you@astermail.org"),
        )
        assertEquals(unsubscribed_account_key(null), unsubscribed_account_key(""))
    }

    @Test
    fun track_request_normalizes_the_sender() {
        val request = build_unsubscribe_track_request(
            " News@Example.COM ",
            "  News  ",
            "https://example.com/unsubscribe",
            "<https://example.com/unsubscribe>",
        )
        assertNotNull(request)
        assertEquals("news@example.com", request!!.sender_email)
        assertEquals("News", request.sender_name)
        assertEquals("https://example.com/unsubscribe", request.unsubscribe_link)
        assertEquals("<https://example.com/unsubscribe>", request.list_unsubscribe_header)
    }

    @Test
    fun track_request_rejects_a_sender_without_an_address() {
        assertNull(build_unsubscribe_track_request("", null, null, null))
        assertNull(build_unsubscribe_track_request("   ", null, null, null))
        assertNull(build_unsubscribe_track_request("newsletter", null, null, null))
    }

    @Test
    fun track_request_drops_links_that_are_not_web_addresses() {
        val mailto = build_unsubscribe_track_request(
            "news@example.com",
            null,
            "mailto:leave@example.com",
            null,
        )
        assertNull(mailto!!.unsubscribe_link)
        val script = build_unsubscribe_track_request(
            "news@example.com",
            null,
            "javascript:alert(1)",
            null,
        )
        assertNull(script!!.unsubscribe_link)
    }

    @Test
    fun track_request_drops_oversized_and_blank_fields() {
        val request = build_unsubscribe_track_request(
            "news@example.com",
            "   ",
            "https://example.com/" + "a".repeat(2048),
            "<" + "b".repeat(2048) + ">",
        )
        assertNull(request!!.sender_name)
        assertNull(request.unsubscribe_link)
        assertNull(request.list_unsubscribe_header)
        assertNull(
            build_unsubscribe_track_request("news@example.com", null, null, "  ")!!
                .list_unsubscribe_header,
        )
    }

    @Test
    fun track_request_truncates_a_long_sender_name() {
        val request = build_unsubscribe_track_request(
            "news@example.com",
            "n".repeat(400),
            null,
            null,
        )
        assertEquals(255, request!!.sender_name!!.length)
    }
}
