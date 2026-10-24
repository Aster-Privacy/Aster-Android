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
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Test

class DeviceKeyFingerprintTest {

    private val ed = ByteArray(32) { 1 }
    private val mlkem = ByteArray(1184) { 2 }
    private val x = ByteArray(32) { 3 }
    private val vector = "9B16 AF79 0A6A E2F2 55D3"

    @Test
    fun `matches the vector shared with web and the bridge`() {
        assertEquals(vector, DeviceKeyFingerprint.of(ed, mlkem, x))
    }

    @Test
    fun `changes with any public key`() {
        assertNotEquals(vector, DeviceKeyFingerprint.of(ByteArray(32) { 9 }, mlkem, x))
        assertNotEquals(vector, DeviceKeyFingerprint.of(ed, ByteArray(1184) { 9 }, x))
        assertNotEquals(vector, DeviceKeyFingerprint.of(ed, mlkem, ByteArray(32) { 9 }))
    }

    @Test
    fun `bytes cannot move between neighboring keys`() {
        assertNotEquals(
            DeviceKeyFingerprint.of(ed, ByteArray(1184) { 2 }, ByteArray(32) { 3 }),
            DeviceKeyFingerprint.of(ed, ByteArray(1183) { 2 }, ByteArray(33) { 3 }),
        )
    }

    @Test
    fun `encoded keys give the same fingerprint`() {
        assertEquals(
            vector,
            DeviceKeyFingerprint.of_encoded(
                DeviceEnvelope.base64url_encode(ed),
                DeviceEnvelope.base64url_encode(mlkem),
                DeviceEnvelope.base64url_encode(x),
            ),
        )
    }

    @Test
    fun `keys of the wrong size or encoding give no fingerprint`() {
        val e = DeviceEnvelope.base64url_encode(ed)
        val m = DeviceEnvelope.base64url_encode(mlkem)
        val k = DeviceEnvelope.base64url_encode(x)
        assertNull(DeviceKeyFingerprint.of_encoded(e, DeviceEnvelope.base64url_encode(mlkem.copyOf(1183)), k))
        assertNull(DeviceKeyFingerprint.of_encoded("", m, k))
        assertNull(DeviceKeyFingerprint.of_encoded("***", m, k))
        assertNull(DeviceKeyFingerprint.of_encoded(e, m, DeviceEnvelope.base64url_encode(ByteArray(31))))
    }
}
