//
// Aster Communications Inc.
//
// Copyright (c) 2026 Aster Communications Inc.
//
// This file is part of this project.
//
// This program is free software: you can redistribute it and/or modify
// it under the terms of the AGPLv3 as published by
// the Free Software Foundation, either version 3 of the License, or
// (at your option) any later version.
//
// This program is distributed in the hope that it will be useful,
// but WITHOUT ANY WARRANTY; without even the implied warranty of
// MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
// AGPLv3 for more details.
//
// You should have received a copy of the AGPLv3
// along with this program. If not, see <https://www.gnu.org/licenses/>.
//

package org.astermail.android.crypto

import io.mockk.coEvery
import io.mockk.mockk
import kotlinx.coroutines.runBlocking
import org.astermail.android.api.ratchet.RatchetApi
import org.astermail.android.api.ratchet.SyncFormatReadinessResponse
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test

class SyncFormatReadinessTest {

    private fun api(ratchet: Boolean, backup: Boolean): RatchetApi = mockk<RatchetApi>(relaxed = true).also {
        coEvery { it.fetch_sync_format_readiness() } returns SyncFormatReadinessResponse(ratchet, backup)
    }

    @Before
    fun setup() = sync_format_readiness.uninstall()

    @After
    fun teardown() = sync_format_readiness.uninstall()

    @Test
    fun `a ratchet api installs the readiness fetcher`() = runBlocking {
        sync_format_readiness.install_api(api(ratchet = true, backup = true))

        assertEquals(SyncFormatReadiness(true, true), sync_format_readiness.current("user-1"))
    }

    @Test
    fun `a fetcher is installed when no ratchet api was built yet`() = runBlocking {
        sync_format_readiness.install_api_if_absent { api(ratchet = false, backup = true) }

        assertEquals(SyncFormatReadiness(false, true), sync_format_readiness.current("user-1"))
    }

    @Test
    fun `an installed fetcher is kept`() = runBlocking {
        sync_format_readiness.install_api(api(ratchet = true, backup = true))
        sync_format_readiness.install_api_if_absent { api(ratchet = false, backup = false) }

        assertEquals(SyncFormatReadiness(true, true), sync_format_readiness.current("user-1"))
    }
}
