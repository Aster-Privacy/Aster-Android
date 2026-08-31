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

import org.astermail.android.crypto.PgpSignatureStatus
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PgpSignatureMergeTest {

    @Test
    fun a_signature_replaces_the_absence_of_one() {
        assertEquals(
            PgpSignatureStatus.UNVERIFIED,
            merge_pgp_signature(PgpSignatureStatus.NONE, PgpSignatureStatus.UNVERIFIED),
        )
    }

    @Test
    fun a_verified_signature_outranks_an_unverified_one() {
        assertEquals(
            PgpSignatureStatus.VALID,
            merge_pgp_signature(PgpSignatureStatus.UNVERIFIED, PgpSignatureStatus.VALID),
        )
    }

    @Test
    fun a_broken_signature_wins_over_every_other_status() {
        assertEquals(
            PgpSignatureStatus.INVALID,
            merge_pgp_signature(PgpSignatureStatus.VALID, PgpSignatureStatus.INVALID),
        )
    }

    @Test
    fun a_weaker_status_never_downgrades_a_stronger_one() {
        assertEquals(
            PgpSignatureStatus.VALID,
            merge_pgp_signature(PgpSignatureStatus.VALID, PgpSignatureStatus.NONE),
        )
    }

    @Test
    fun an_armored_pgp_message_body_counts_as_encrypted() {
        val body = "-----BEGIN PGP MESSAGE-----\n\nhQIMA0x\n-----END PGP MESSAGE-----"

        assertTrue(body_starts_with(body, PGP_ENCRYPTED_MESSAGE_HEADER))
    }

    @Test
    fun a_cleartext_signed_body_is_not_counted_as_encrypted() {
        val body = "-----BEGIN PGP SIGNED MESSAGE-----\nHash: SHA512\n\nhello"

        assertTrue(body_starts_with(body, "-----BEGIN PGP"))
        assertFalse(body_starts_with(body, PGP_ENCRYPTED_MESSAGE_HEADER))
    }

    @Test
    fun an_ordinary_body_is_not_counted_as_encrypted() {
        assertFalse(body_starts_with("hello there", PGP_ENCRYPTED_MESSAGE_HEADER))
    }
}
