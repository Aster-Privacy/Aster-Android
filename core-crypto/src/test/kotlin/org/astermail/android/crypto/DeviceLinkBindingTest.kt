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

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class DeviceLinkBindingTest {

    private val code = "ABCD-2345"
    private val ed25519_pk = DeviceEnvelope.base64url_encode(ByteArray(32) { 1 })
    private val mlkem_pk = DeviceEnvelope.base64url_encode(ByteArray(1184) { 2 })
    private val x25519_pk = DeviceEnvelope.base64url_encode(ByteArray(32) { 3 })

    private fun tag(
        code_value: String = code,
        ed: String = ed25519_pk,
        mlkem: String = mlkem_pk,
        x: String = x25519_pk,
    ): String = DeviceLinkBinding.compute_tag(code_value, ed, mlkem, x)

    @Test
    fun `tag ignores code formatting and case`() {
        assertEquals(tag(), tag("abcd2345"))
        assertEquals(tag(), tag(" abcd 2345 "))
    }

    @Test
    fun `tag length is a full sha256 mac`() {
        assertEquals(DeviceLinkBinding.TAG_BYTES, DeviceEnvelope.base64url_decode(tag()).size)
    }

    @Test
    fun `matching bundle and code verify`() {
        assertTrue(
            DeviceLinkBinding.matches(code, ed25519_pk, mlkem_pk, x25519_pk, tag()),
        )
    }

    @Test
    fun `substituted key encapsulation key is rejected`() {
        val attacker_mlkem = DeviceEnvelope.base64url_encode(ByteArray(1184) { 9 })
        assertFalse(
            DeviceLinkBinding.matches(code, ed25519_pk, attacker_mlkem, x25519_pk, tag()),
        )
    }

    @Test
    fun `substituted x25519 key is rejected`() {
        val attacker_x25519 = DeviceEnvelope.base64url_encode(ByteArray(32) { 9 })
        assertFalse(
            DeviceLinkBinding.matches(code, ed25519_pk, mlkem_pk, attacker_x25519, tag()),
        )
    }

    @Test
    fun `tag from another code is rejected`() {
        val other = tag(code_value = "ZZZZ-9999")
        assertFalse(DeviceLinkBinding.matches(code, ed25519_pk, mlkem_pk, x25519_pk, other))
    }

    @Test
    fun `blank and malformed tags are rejected`() {
        assertFalse(DeviceLinkBinding.matches(code, ed25519_pk, mlkem_pk, x25519_pk, ""))
        assertFalse(DeviceLinkBinding.matches(code, ed25519_pk, mlkem_pk, x25519_pk, "not base64!!"))
        assertFalse(
            DeviceLinkBinding.matches(
                code,
                ed25519_pk,
                mlkem_pk,
                x25519_pk,
                DeviceEnvelope.base64url_encode(ByteArray(16) { 7 }),
            ),
        )
    }

    @Test
    fun `require_match throws on a substituted bundle`() {
        val attacker_mlkem = DeviceEnvelope.base64url_encode(ByteArray(1184) { 9 })
        var thrown = false
        try {
            DeviceLinkBinding.require_match(code, ed25519_pk, attacker_mlkem, x25519_pk, tag())
        } catch (e: DeviceLinkBinding.BindingMismatchException) {
            thrown = true
        }
        assertTrue(thrown)
    }

    @Test
    fun `field boundaries cannot be shifted between keys`() {
        val a = DeviceLinkBinding.compute_tag(code, "AA", "BB", "CC")
        val b = DeviceLinkBinding.compute_tag(code, "AAB", "B", "CC")
        assertFalse(a == b)
    }
}
