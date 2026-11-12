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

package org.astermail.android.mail.ratchet

import android.util.Base64
import io.mockk.every
import io.mockk.mockkStatic
import io.mockk.unmockkStatic
import org.astermail.android.crypto.ratchet.RatchetCrypto
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class AddressIdentityVectorTest {

    @Before
    fun setup() {
        mockkStatic(Base64::class)
        every { Base64.encodeToString(any(), any()) } answers {
            val bytes = firstArg<ByteArray>()
            val flags = secondArg<Int>()
            if (flags and Base64.URL_SAFE != 0) {
                java.util.Base64.getUrlEncoder().withoutPadding().encodeToString(bytes)
            } else {
                java.util.Base64.getEncoder().encodeToString(bytes)
            }
        }
        every { Base64.decode(any<String>(), any()) } answers {
            val value = firstArg<String>()
            val flags = secondArg<Int>()
            if (flags and Base64.URL_SAFE != 0) {
                var padded = value
                val mod = padded.length % 4
                if (mod != 0) padded += "=".repeat(4 - mod)
                java.util.Base64.getUrlDecoder().decode(padded)
            } else {
                java.util.Base64.getDecoder().decode(value)
            }
        }
    }

    @After
    fun teardown() {
        unmockkStatic(Base64::class)
    }

    private val account_jwk =
        "{\"kty\":\"EC\",\"crv\":\"P-256\",\"d\":\"AQEBAQEBAQEBAQEBAQEBAQEBAQEBAQEBAQEBAQEBAQE\"}"

    @Test
    fun address_key_matches_the_other_clients() {
        val alias = RatchetCrypto.derive_address_identity(account_jwk, "shop@astermail.org")!!
        val custom = RatchetCrypto.derive_address_identity(account_jwk, "me@my-domain.example")!!

        assertEquals(
            "4duYxbOvvRRcxvekWSpFbEShkOeroOgmYlMHjoAVfhE",
            org.json.JSONObject(alias.identity_jwk).getString("d"),
        )
        assertEquals(
            "BJOsUxR6RC6w1IZrJE1GLztOgrU92mUlJeLgIDBmxzpT9RjmAf0UBp10+mqQflnS9CTawHNdA7VO9oGwmyZF5zM=",
            alias.identity_public_b64,
        )
        assertEquals(
            "qcV01slvXsiYJNEGbEfh1Viym6PuREXAV1DhoR4n1y8",
            org.json.JSONObject(custom.identity_jwk).getString("d"),
        )
        assertEquals(
            "BIhgLU/Vm9ETcb4S5ITrI953K1Qim92xu6ZFO8QppjALcwRAkPlFRj06j20gJsKZ4tWEVH6CCJbNs7lihqZ6o6U=",
            custom.identity_public_b64,
        )
    }

    @Test
    fun address_key_ignores_case_and_surrounding_spaces() {
        val plain = RatchetCrypto.derive_address_identity(account_jwk, "shop@astermail.org")!!
        val varied = RatchetCrypto.derive_address_identity(account_jwk, "  Shop@AsterMail.org ")!!

        assertEquals(plain.identity_public_b64, varied.identity_public_b64)
    }

    @Test
    fun address_key_differs_from_the_account_key() {
        val account_public = RatchetCrypto.b64_encode(
            RatchetCrypto.p256_public_raw_from_private_jwk(account_jwk),
        )
        val alias = RatchetCrypto.derive_address_identity(account_jwk, "shop@astermail.org")!!

        assertNotEquals(account_public, alias.identity_public_b64)
    }

    @Test
    fun address_key_needs_an_account_key_and_an_address() {
        assertNull(RatchetCrypto.derive_address_identity("", "shop@astermail.org"))
        assertNull(RatchetCrypto.derive_address_identity(account_jwk, "  "))
    }

    @Test
    fun only_the_primary_address_uses_the_account_identity() {
        assertTrue(sender_uses_account_identity("Me@astermail.org", "me@astermail.org"))
        assertFalse(sender_uses_account_identity("shop@astermail.org", "me@astermail.org"))
        assertFalse(sender_uses_account_identity("me@my-domain.example", "me@astermail.org"))
        assertTrue(sender_uses_account_identity("shop@astermail.org", null))
    }
}
