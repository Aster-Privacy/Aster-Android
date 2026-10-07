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

package org.astermail.android.auth

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PgpRepublishPolicyTest {

    private val modern_v4_public_key = "-----BEGIN PGP PUBLIC KEY BLOCK-----\n\nxiYEakOyohvvSnYOHVEKq1Lx7kx6EGCYlDpiMfTcnYFx4UMrus4+Y80iQnJ1\nbm8gVGVzdCA8YnJ1bm90ZXN0QGV4YW1wbGUuY29tPsLADwQTGwoAhQWCakOy\nogMLCQcJEGopfWL5aEtBRRQAAAAAABwAIHNhbHRAbm90YXRpb25zLm9wZW5w\nZ3Bqcy5vcmebnGnOR0ZytXyKPha0jtPQoKtwo1sB1QSFQ1KKq6nKhQUVCggO\nDAQWAAIBAhkBApsDAh4BFiEEaWeXYPhYVPMJtkvVail9YvloS0EAALsa+yx2\nF8ELxWdHbtx1GAMfn+bqr0Y3oXggSEXBJ7xksWUNAQ92GRMyvz13NWpE+xOY\n80UeSyir4wftaEbfsubjC84mBGpDsqIZZ8JMkVAQw41d9fVlSyIwVorSm1xO\nEb24RzCcxWItCQ3CugQYGwoAcAWCakOyogkQail9YvloS0FFFAAAAAAAHAAg\nc2FsdEBub3RhdGlvbnMub3BlbnBncGpzLm9yZ3frVDb0X26/srtD0nUlxmnI\nCItI42aya24p92BW/0aIApsMFiEEaWeXYPhYVPMJtkvVail9YvloS0EAAHGT\nbJdv03TSbx/mdos45pk05Cqzx1y7Ouf8tipRfzBKSNfFr2J+dEK+/fQW9W/M\nGPeauy6x4yWHEJIYbgkgzOcWDQ==\n=+2R8\n-----END PGP PUBLIC KEY BLOCK-----\n"

    private val legacy_public_key = "-----BEGIN PGP PUBLIC KEY BLOCK-----\n\nxjMEasZN8BYJKwYBBAHaRw8BAQdAIO0/XAsSqime0+2L7zpFMXSgARaDGlHh\nNMX55nLQfejNF1Rlc3QgPHRlc3RAZXhhbXBsZS5jb20+wsATBBMWCgCFBYJq\nxk3wAwsJBwkQZssM4TsRRj9FFAAAAAAAHAAgc2FsdEBub3RhdGlvbnMub3Bl\nbnBncGpzLm9yZw40zfldLV55EuMpddODAxYbLMK0nRbEHphNUgtAPGyiBRUK\nCA4MBBYAAgECGQECmwMCHgEWIQT0LnjpjkFfi/sfrq5mywzhOxFGPwAAl0MB\nAKr1BBy5gKOZ8mRrkzOSS0ZoF/dDd4dIntrWXolbV4DYAP93q6uuTrGpd6mv\nkl04nQK/W6WDi2hzQvIM9Lyb3l8JAs44BGrGTfASCisGAQQBl1UBBQEBB0Be\nnRhW9bGJ8a82/lRPFE9IQ05ikNx47hzfSfjI5dubXAMBCAfCvgQYFgoAcAWC\nasZN8AkQZssM4TsRRj9FFAAAAAAAHAAgc2FsdEBub3RhdGlvbnMub3BlbnBn\ncGpzLm9yZ4WmywfzX0GS1PmSiXc5BcKWQ4RoOQbQJwt8gdHyAUvrApsMFiEE\n9C546Y5BX4v7H66uZssM4TsRRj8AAN62AQCACIu5KvPOhsz0yY6rpVIWL/S1\nPJ8by1o+QJw5CePf3QD/Tu1sQ8ssYRhzTW9SOf4KWNmd8XUjg6IiuYxrjk0j\nCAk=\n=9wbt\n-----END PGP PUBLIC KEY BLOCK-----\n"

    private val local_fingerprint = "69679760F85854F309B64BD56A297D62F9684B41"

    @Test
    fun modern_algorithm_ids_on_v4_are_non_standard() {
        assertTrue(pgp_key_packet_is_non_standard(4, 25))
        assertTrue(pgp_key_packet_is_non_standard(4, 26))
        assertTrue(pgp_key_packet_is_non_standard(4, 27))
        assertTrue(pgp_key_packet_is_non_standard(4, 28))
    }

    @Test
    fun legacy_algorithms_and_v6_keys_are_standard() {
        assertFalse(pgp_key_packet_is_non_standard(4, 22))
        assertFalse(pgp_key_packet_is_non_standard(4, 18))
        assertFalse(pgp_key_packet_is_non_standard(4, 1))
        assertFalse(pgp_key_packet_is_non_standard(6, 27))
        assertFalse(pgp_key_packet_is_non_standard(6, 25))
    }

    @Test
    fun armored_modern_v4_key_is_detected() {
        assertTrue(armored_pgp_key_is_non_standard(modern_v4_public_key))
    }

    @Test
    fun armored_legacy_key_is_not_flagged() {
        assertFalse(armored_pgp_key_is_non_standard(legacy_public_key))
    }

    @Test
    fun unparseable_key_is_not_flagged() {
        assertFalse(armored_pgp_key_is_non_standard("not a key"))
    }

    @Test
    fun non_standard_local_key_is_never_republished() {
        listOf(
            PublishedPgpKey.Absent,
            PublishedPgpKey.Unknown,
            PublishedPgpKey.Present(local_fingerprint),
            PublishedPgpKey.Present("AA".repeat(20)),
        ).forEach { published ->
            assertEquals(
                PgpRepublishDecision.SKIP_NON_STANDARD_KEY,
                decide_pgp_republish(local_fingerprint, true, published),
            )
        }
    }

    @Test
    fun differing_published_key_is_kept() {
        assertEquals(
            PgpRepublishDecision.SKIP_PUBLISHED_KEY_DIFFERS,
            decide_pgp_republish(local_fingerprint, false, PublishedPgpKey.Present("AA".repeat(20))),
        )
    }

    @Test
    fun matching_published_key_is_republished_case_insensitively() {
        assertEquals(
            PgpRepublishDecision.REPUBLISH,
            decide_pgp_republish(local_fingerprint, false, PublishedPgpKey.Present(local_fingerprint.lowercase())),
        )
    }

    @Test
    fun missing_published_key_is_republished() {
        assertEquals(
            PgpRepublishDecision.REPUBLISH,
            decide_pgp_republish(local_fingerprint, false, PublishedPgpKey.Absent),
        )
    }

    @Test
    fun unknown_or_blank_published_state_is_not_overwritten() {
        assertEquals(
            PgpRepublishDecision.SKIP_PUBLISHED_STATE_UNKNOWN,
            decide_pgp_republish(local_fingerprint, false, PublishedPgpKey.Unknown),
        )
        assertEquals(
            PgpRepublishDecision.SKIP_PUBLISHED_STATE_UNKNOWN,
            decide_pgp_republish(local_fingerprint, false, PublishedPgpKey.Present("")),
        )
    }
}
