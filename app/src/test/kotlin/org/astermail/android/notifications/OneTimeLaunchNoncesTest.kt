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

package org.astermail.android.notifications

import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class OneTimeLaunchNoncesTest {

    @Test
    fun an_issued_nonce_opens_once() {
        val nonces = OneTimeLaunchNonces()
        val nonce = nonces.issue(now_ms = 1_000L)
        assertTrue(nonces.consume(nonce, now_ms = 2_000L))
        assertFalse(nonces.consume(nonce, now_ms = 2_001L))
    }

    @Test
    fun missing_or_unknown_nonces_are_refused() {
        val nonces = OneTimeLaunchNonces()
        nonces.issue(now_ms = 0L)
        assertFalse(nonces.consume(null, now_ms = 1L))
        assertFalse(nonces.consume("", now_ms = 1L))
        assertFalse(nonces.consume("00".repeat(32), now_ms = 1L))
    }

    @Test
    fun only_settings_or_this_app_may_ask_for_the_contact_sync_page() {
        val own_uid = 10_123
        assertTrue(contact_sync_launch.caller_allowed(contact_sync_launch.SYSTEM_UID, own_uid))
        assertTrue(contact_sync_launch.caller_allowed(own_uid, own_uid))
        assertFalse(contact_sync_launch.caller_allowed(10_456, own_uid))
        assertFalse(contact_sync_launch.caller_allowed(-1, own_uid))
    }

    @Test
    fun settings_and_this_app_in_another_profile_may_ask_for_the_contact_sync_page() {
        val own_uid = 1_010_123
        assertTrue(contact_sync_launch.caller_allowed(1_001_000, own_uid))
        assertTrue(contact_sync_launch.caller_allowed(1_010_123, own_uid))
        assertTrue(contact_sync_launch.caller_allowed(1_001_000, 10_123))
        assertFalse(contact_sync_launch.caller_allowed(1_010_456, own_uid))
        assertFalse(contact_sync_launch.caller_allowed(1_000, -1))
    }

    @Test
    fun a_nonce_expires() {
        val nonces = OneTimeLaunchNonces(ttl_ms = 100L)
        val nonce = nonces.issue(now_ms = 0L)
        assertFalse(nonces.consume(nonce, now_ms = 100L))
    }

    @Test
    fun only_the_newest_nonces_stay_outstanding() {
        val nonces = OneTimeLaunchNonces(max_outstanding = 2)
        val first = nonces.issue(now_ms = 0L)
        val second = nonces.issue(now_ms = 1L)
        val third = nonces.issue(now_ms = 2L)
        assertNotEquals(first, second)
        assertFalse(nonces.consume(first, now_ms = 3L))
        assertTrue(nonces.consume(second, now_ms = 3L))
        assertTrue(nonces.consume(third, now_ms = 3L))
    }
}
