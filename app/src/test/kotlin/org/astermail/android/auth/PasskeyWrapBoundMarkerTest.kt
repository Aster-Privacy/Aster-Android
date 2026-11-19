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

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class PasskeyWrapBoundMarkerTest {
    private class MemoryMarkers : PrfBoundMarkers {
        val seen = mutableSetOf<String>()
        override fun is_bound(credential_id: String): Boolean =
            prf_bound_marker_key(credential_id)?.let { it in seen } == true
        override fun remember(credential_id: String) {
            prf_bound_marker_key(credential_id)?.let { seen.add(it) }
        }
    }

    private val prf = ByteArray(32) { (it + 1).toByte() }
    private val credential = "EBESExQVFhcYGRobHB0eHw"
    private val v1_cipher = "9PRytl50HjsZhcqIme/ZlG3gB9RbcgC8PKBdXsIFKYZP"
    private val v2_cipher = "4rrq0JEBoOQ3jCSzOI2h+8YyQo/vlZr+fsIWWkKjndVO"
    private val nonce = "oKGio6Slpqeoqaqr"
    private val expected = "vector passphrase".toByteArray()

    @Test
    fun marker_key_matches_web_format() {
        assertEquals("aster_prf_bound:/C4scwcr+ivaA/+TB0ct6w==", prf_bound_marker_key(credential))
    }

    @Test
    fun v1_wrap_opens_before_bound_seen() {
        val markers = MemoryMarkers()
        val opened = open_prf_wrap(prf, v1_cipher, nonce, credential, markers)
        assertNotNull(opened)
        assertArrayEquals(expected, opened!!.passphrase)
        assertFalse(opened.bound)
        assertFalse(markers.is_bound(credential))
    }

    @Test
    fun bound_open_remembers_credential() {
        val markers = MemoryMarkers()
        val opened = open_prf_wrap(prf, v2_cipher, nonce, credential, markers)
        assertNotNull(opened)
        assertArrayEquals(expected, opened!!.passphrase)
        assertTrue(opened.bound)
        assertTrue(markers.is_bound(credential))
    }

    @Test
    fun v1_wrap_refused_after_bound_seen() {
        val markers = MemoryMarkers()
        markers.remember(credential)
        assertNull(open_prf_wrap(prf, v1_cipher, nonce, credential, markers))
        assertNull(decrypt_passphrase_with_prf(prf, v1_cipher, nonce, credential, bound_seen = true))
        val opened = open_prf_wrap(prf, v2_cipher, nonce, credential, markers)
        assertNotNull(opened)
        assertArrayEquals(expected, opened!!.passphrase)
    }

    @Test
    fun new_wrap_is_bound_and_rejects_other_credential() {
        val markers = MemoryMarkers()
        val (cipher, n) = encrypt_passphrase_with_prf(prf, expected, credential)
        assertTrue(open_prf_wrap(prf, cipher, n, credential, markers)!!.bound)
        assertNull(open_prf_wrap(prf, cipher, n, "AAECAwQFBgcICQoLDA0ODw", markers))
    }
}
