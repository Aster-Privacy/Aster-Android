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

package org.astermail.android.notifications

import android.content.Context
import dagger.hilt.android.EntryPointAccessors
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkStatic
import io.mockk.unmockkStatic
import io.mockk.verify
import org.astermail.android.api.ApiClient
import org.junit.After
import org.junit.Assert.assertSame
import org.junit.Before
import org.junit.Test

class PollingWorkerSharedClientTest {

    private val app_context = mockk<Context>(relaxed = true)
    private val shared_client = mockk<ApiClient>(relaxed = true)
    private val entry_point = mockk<MailPollingWorker.MailRepositoryEntryPoint>()

    @Before
    fun set_up() {
        every { entry_point.api_client() } returns shared_client
        mockkStatic(EntryPointAccessors::class)
        every {
            EntryPointAccessors.fromApplication(any(), MailPollingWorker.MailRepositoryEntryPoint::class.java)
        } returns entry_point
    }

    @After
    fun tear_down() {
        unmockkStatic(EntryPointAccessors::class)
    }

    private fun worker_context(): Context {
        val context = mockk<Context>(relaxed = true)
        every { context.applicationContext } returns app_context
        return context
    }

    @Test
    fun poll_uses_the_app_singleton_client() {
        assertSame(shared_client, MailPollingWorker.shared_api_client(worker_context()))
    }

    @Test
    fun repeated_polls_reuse_one_client_instead_of_building_a_new_one() {
        val first = MailPollingWorker.shared_api_client(worker_context())
        val second = MailPollingWorker.shared_api_client(worker_context())
        assertSame(first, second)
        verify(exactly = 0) { shared_client.close() }
    }

    @Test
    fun client_is_resolved_from_the_application_graph() {
        MailPollingWorker.shared_api_client(worker_context())
        verify { EntryPointAccessors.fromApplication(app_context, MailPollingWorker.MailRepositoryEntryPoint::class.java) }
    }
}
