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

import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import org.astermail.android.api.mail.MailApi
import org.astermail.android.storage.search.AsterDatabase
import org.astermail.android.storage.search.DecryptedMailDao
import org.junit.Test

class SearchIndexPoisonPurgeTest {

    private fun manager(dao: DecryptedMailDao): SearchIndexManager {
        val database = mockk<AsterDatabase>()
        every { database.decrypted_mail_dao() } returns dao
        return SearchIndexManager(
            { database },
            mockk<MailApi>(relaxed = true),
            mockk<MailRepository>(relaxed = true),
            mockk<android.content.Context>(relaxed = true),
        )
    }

    @Test
    fun repeated_cache_reads_purge_poisoned_rows_only_once() = runTest {
        val dao = mockk<DecryptedMailDao>(relaxed = true)
        val manager = manager(dao)

        manager.get_cached_items()
        manager.get_cached_items()

        coVerify(exactly = 1) { dao.clear_armored_previews() }
        coVerify(exactly = 1) { dao.delete_bundle_poisoned() }
        coVerify(exactly = 1) { dao.delete_blank_rows() }
    }

    @Test
    fun a_failed_purge_is_retried_on_the_next_read() = runTest {
        val dao = mockk<DecryptedMailDao>(relaxed = true)
        coEvery { dao.delete_bundle_poisoned() } throws IllegalStateException("database busy") andThen 0
        val manager = manager(dao)

        manager.get_cached_items()
        manager.get_cached_items()
        manager.get_cached_items()

        coVerify(exactly = 2) { dao.delete_bundle_poisoned() }
        coVerify(exactly = 2) { dao.delete_blank_rows() }
    }
}
