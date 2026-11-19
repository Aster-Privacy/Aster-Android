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

import org.astermail.android.api.keys.ExternalKeyInfo
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test

class RecipientEncryptionServerFlagTest {

    private val now = 1_800_000_000_000L

    private fun served(source: String?) = ExternalKeyInfo(
        email = "pal@example.com",
        found = true,
        public_key = "-----BEGIN PGP PUBLIC KEY BLOCK-----",
        source = source,
        will_encrypt = true,
    )

    @Test
    fun a_keyserver_key_is_not_picked_because_the_server_says_so() {
        assertFalse(RecipientEncryptionRules.will_encrypt_opportunistically(served("keyserver"), true, now))
        assertEquals(
            RecipientEncryption.PLAIN,
            RecipientEncryptionRules.decide(served("keyserver"), null, false, false, true, now),
        )
    }

    @Test
    fun the_sender_setting_decides_over_the_server_flag() {
        assertFalse(RecipientEncryptionRules.will_encrypt_opportunistically(served("wkd"), false, now))
        assertEquals(
            RecipientLockStatus.AVAILABLE,
            RecipientEncryptionRules.lock_status_for(served("wkd"), now, encrypt_by_default = false),
        )
    }

    @Test
    fun an_unknown_sender_setting_follows_the_last_server_answer_instead_of_plain() {
        assertEquals(
            RecipientEncryption.ENCRYPT,
            RecipientEncryptionRules.decide(served("wkd"), null, false, false),
        )
        assertEquals(
            RecipientLockStatus.ENCRYPTED,
            RecipientEncryptionRules.lock_status_for(served("wkd"), now) { OpportunisticKeyTrust.PINNED },
        )
    }
}
