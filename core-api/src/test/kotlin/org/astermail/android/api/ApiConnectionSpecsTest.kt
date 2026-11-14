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

package org.astermail.android.api

import okhttp3.ConnectionSpec
import okhttp3.TlsVersion
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ApiConnectionSpecsTest {
    private val allowed_versions = setOf(TlsVersion.TLS_1_2, TlsVersion.TLS_1_3)

    @Test
    fun production_connections_use_modern_tls_only() {
        val specs = api_connection_specs(allow_cleartext = false)

        assertEquals(listOf(ConnectionSpec.MODERN_TLS), specs)
        assertTrue(specs.all { it.isTls })
        assertTrue(specs.all { allowed_versions.containsAll(it.tlsVersions.orEmpty()) })
    }

    @Test
    fun no_connection_spec_offers_tls_1_0_or_1_1() {
        listOf(true, false).forEach { allow_cleartext ->
            val versions = api_connection_specs(allow_cleartext).flatMap { it.tlsVersions.orEmpty() }

            assertFalse(versions.contains(TlsVersion.TLS_1_0))
            assertFalse(versions.contains(TlsVersion.TLS_1_1))
        }
    }

    @Test
    fun cleartext_is_offered_only_to_tests() {
        assertTrue(api_connection_specs(allow_cleartext = true).contains(ConnectionSpec.CLEARTEXT))
        assertFalse(api_connection_specs(allow_cleartext = false).contains(ConnectionSpec.CLEARTEXT))
    }
}
