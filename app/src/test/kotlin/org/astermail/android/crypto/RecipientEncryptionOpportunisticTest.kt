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

package org.astermail.android.crypto

import kotlinx.coroutines.test.runTest
import org.astermail.android.api.keys.ExternalKeyInfo
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class RecipientEncryptionOpportunisticTest {

    private val now = 1_800_000_000_000L

    private fun info(
        source: String? = "wkd",
        will_encrypt: Boolean = false,
        expires_at: String? = null,
        public_key: String? = "-----BEGIN PGP PUBLIC KEY BLOCK-----",
    ) = ExternalKeyInfo(
        email = "bob@example.com",
        found = public_key != null,
        public_key = public_key,
        source = source,
        will_encrypt = will_encrypt,
        expires_at = expires_at,
    )

    private fun decide(key: ExternalKeyInfo?, encrypt_by_default: Boolean, force_pgp: Boolean? = null) =
        RecipientEncryptionRules.decide(key, force_pgp, false, false, encrypt_by_default, now)

    @Test
    fun sender_setting_encrypts_when_server_flag_is_withheld() {
        assertEquals(RecipientEncryption.ENCRYPT, decide(info(), encrypt_by_default = true))
        assertEquals(RecipientEncryption.PLAIN, decide(info(), encrypt_by_default = false))
    }

    @Test
    fun recipient_controlled_sources_are_trusted() {
        for (source in listOf("wkd", "WKD", "autocrypt", "manual", "dane")) {
            assertTrue(source, RecipientEncryptionRules.trusts_key_for_opportunistic(info(source = source), true, now))
        }
    }

    @Test
    fun keyserver_database_and_unknown_sources_are_not_trusted() {
        for (source in listOf("keyserver", "database", "", null)) {
            assertFalse(RecipientEncryptionRules.trusts_key_for_opportunistic(info(source = source), true, now))
            assertEquals(RecipientEncryption.PLAIN, decide(info(source = source), encrypt_by_default = true))
        }
    }

    @Test
    fun expired_or_unreadable_expiry_is_not_trusted() {
        assertFalse(
            RecipientEncryptionRules.trusts_key_for_opportunistic(info(expires_at = "2020-01-01T00:00:00Z"), true, now),
        )
        assertFalse(RecipientEncryptionRules.trusts_key_for_opportunistic(info(expires_at = "soon"), true, now))
        assertTrue(
            RecipientEncryptionRules.trusts_key_for_opportunistic(info(expires_at = "2099-01-01T00:00:00Z"), true, now),
        )
    }

    @Test
    fun recipient_controlled_key_check_ignores_the_send_setting() {
        assertTrue(RecipientEncryptionRules.is_recipient_controlled_key(info(), now))
        assertTrue(RecipientEncryptionRules.is_recipient_controlled_key(info(expires_at = "2099-01-01T00:00:00Z"), now))
        assertFalse(RecipientEncryptionRules.is_recipient_controlled_key(info(source = "keyserver", will_encrypt = true), now))
        assertFalse(RecipientEncryptionRules.is_recipient_controlled_key(info(expires_at = "2020-01-01T00:00:00Z"), now))
        assertFalse(RecipientEncryptionRules.is_recipient_controlled_key(info(expires_at = "soon"), now))
        assertFalse(RecipientEncryptionRules.is_recipient_controlled_key(info(public_key = null), now))
        assertFalse(RecipientEncryptionRules.is_recipient_controlled_key(null, now))
    }

    @Test
    fun turning_encryption_off_for_the_message_still_wins() {
        assertEquals(RecipientEncryption.PLAIN, decide(info(), encrypt_by_default = true, force_pgp = false))
    }

    @Test
    fun server_flag_stays_an_upgrade_only() {
        assertEquals(RecipientEncryption.ENCRYPT, decide(info(source = "keyserver", will_encrypt = true), false))
    }

    @Test
    fun lock_status_matches_the_send_decision() {
        assertEquals(
            RecipientLockStatus.ENCRYPTED,
            RecipientEncryptionRules.lock_status_for(info(), now, encrypt_by_default = true),
        )
        assertEquals(
            RecipientLockStatus.AVAILABLE,
            RecipientEncryptionRules.lock_status_for(info(source = "keyserver"), now, encrypt_by_default = true),
        )
        assertEquals(
            RecipientLockStatus.AVAILABLE,
            RecipientEncryptionRules.lock_status_for(info(), now, encrypt_by_default = false),
        )
    }

    @Test
    fun lookup_passes_the_sender_setting_through() = runTest {
        val result = RecipientEncryptionRules.lookup_lock_statuses(
            outside_recipients = listOf("Bob@Example.com"),
            now_ms = { now },
            wait = {},
            discover = { listOf(info()) },
            encrypt_by_default = true,
        )
        assertEquals(mapOf("bob@example.com" to RecipientLockStatus.ENCRYPTED), result)
    }
}
