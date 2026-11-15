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

import android.content.Context
import android.content.SharedPreferences
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import org.astermail.android.api.mail.MailApi
import org.astermail.android.api.mail.MailItem
import org.astermail.android.api.mail.MailItemsListResponse
import org.astermail.android.storage.search.AsterDatabase
import org.astermail.android.storage.search.DecryptedMailDao
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class SearchIndexResumeTest {

    private lateinit var dao: DecryptedMailDao
    private lateinit var mail_api: MailApi
    private lateinit var repository: MailRepository
    private lateinit var manager: SearchIndexManager
    private val prefs_values = mutableMapOf<String, Any?>()
    private val pages = mutableMapOf<String?, MailItemsListResponse>()
    private var inbox_total = 0
    private val seen_progress = mutableListOf<IndexProgress?>()

    private fun item(id: String): MailItem = MailItem(
        id = id,
        item_type = "received",
        thread_token = null,
        thread_message_count = 1,
        message_ts = "2026-04-26T10:00:00Z",
        created_at = "2026-04-26T10:00:00Z",
    )

    private fun ids(prefix: String, from: Int, until: Int): List<String> =
        (from until until).map { "$prefix$it" }

    private fun page(ids: List<String>, next: String?): MailItemsListResponse = MailItemsListResponse(
        items = ids.map { item(it) },
        has_more = next != null,
        next_cursor = next,
        total = inbox_total,
    )

    private fun fake_prefs(): SharedPreferences {
        val prefs = mockk<SharedPreferences>(relaxed = true)
        val editor = mockk<SharedPreferences.Editor>(relaxed = true)
        every { prefs.getBoolean(any(), any()) } answers {
            prefs_values[firstArg()] as? Boolean ?: secondArg()
        }
        every { prefs.getLong(any(), any()) } answers {
            prefs_values[firstArg()] as? Long ?: secondArg()
        }
        every { prefs.getStringSet(any(), any()) } answers { secondArg() }
        every { prefs.edit() } returns editor
        every { editor.putBoolean(any(), any()) } answers {
            prefs_values[firstArg()] = secondArg<Boolean>()
            editor
        }
        every { editor.putLong(any(), any()) } answers {
            prefs_values[firstArg()] = secondArg<Long>()
            editor
        }
        every { editor.remove(any()) } answers {
            prefs_values.remove(firstArg<String>())
            editor
        }
        return prefs
    }

    @Before
    fun setup() {
        dao = mockk(relaxed = true)
        mail_api = mockk(relaxed = true)
        repository = mockk(relaxed = true)
        coEvery {
            mail_api.list_messages(
                limit = any(),
                cursor = any(),
                item_type = any(),
                is_trashed = any(),
                is_archived = any(),
                is_spam = any(),
                skip_total = any(),
            )
        } returns MailItemsListResponse()
        coEvery {
            mail_api.list_messages(
                limit = any(),
                cursor = any(),
                item_type = any(),
                is_trashed = false,
                is_archived = false,
                is_spam = false,
                skip_total = any(),
            )
        } answers {
            val limit = firstArg<Int>()
            if (limit == 1) {
                MailItemsListResponse(total = inbox_total)
            } else {
                seen_progress.add(manager.index_progress.value)
                pages[secondArg<String?>()] ?: MailItemsListResponse()
            }
        }
        val db = mockk<AsterDatabase>(relaxed = true)
        every { db.decrypted_mail_dao() } returns dao
        val context = mockk<Context>(relaxed = true)
        val prefs = fake_prefs()
        every { context.getSharedPreferences(any(), any()) } returns prefs
        manager = SearchIndexManager(dagger.Lazy { db }, mail_api, repository, context)
    }

    private fun mark_completed(fresh: Boolean) {
        prefs_values["index_complete"] = true
        prefs_values["last_full_build"] = if (fresh) System.currentTimeMillis() else 1L
    }

    private fun inbox_page_fetches(cursor: String?): Int =
        fetched_cursors.count { it == cursor }

    private val fetched_cursors = mutableListOf<String?>()

    private fun track_cursors() {
        coEvery {
            mail_api.list_messages(
                limit = 200,
                cursor = any(),
                item_type = any(),
                is_trashed = false,
                is_archived = false,
                is_spam = false,
                skip_total = any(),
            )
        } answers {
            val cursor = secondArg<String?>()
            fetched_cursors.add(cursor)
            seen_progress.add(manager.index_progress.value)
            pages[cursor] ?: MailItemsListResponse()
        }
    }

    @Test
    fun `a completed index reopens without walking or showing progress`() = runTest {
        inbox_total = 400
        coEvery { dao.get_all_ids() } returns ids("k", 0, 400)
        pages[null] = page(ids("k", 0, 200), "c1")
        pages["c1"] = page(ids("k", 200, 400), null)
        mark_completed(fresh = true)
        track_cursors()

        manager.refresh_index_and_wait()

        assertEquals(listOf<String?>(null), fetched_cursors)
        coVerify(exactly = 0) { repository.decrypt_items_for_cache(any()) }
        coVerify(exactly = 0) { dao.remove_items(any()) }
        assertTrue(seen_progress.all { it == null })
        assertTrue(manager.index_ready.value)
    }

    @Test
    fun `new mail decrypts only the new message and stops at known mail`() = runTest {
        inbox_total = 401
        coEvery { dao.get_all_ids() } returns ids("k", 0, 400)
        pages[null] = page(listOf("n1") + ids("k", 0, 199), "c1")
        pages["c1"] = page(ids("k", 199, 399), "c2")
        pages["c2"] = page(ids("k", 399, 400), null)
        mark_completed(fresh = true)
        track_cursors()

        manager.refresh_index_and_wait()

        coVerify(exactly = 1) {
            repository.decrypt_items_for_cache(match { batch -> batch.map { it.id } == listOf("n1") })
        }
        coVerify(exactly = 1) { repository.decrypt_items_for_cache(any()) }
        assertEquals(1, inbox_page_fetches("c1"))
        assertEquals(0, inbox_page_fetches("c2"))
        assertTrue(seen_progress.all { it == null })
    }

    @Test
    fun `an interrupted build resumes its progress from the stored count`() = runTest {
        inbox_total = 1000
        coEvery { dao.get_all_ids() } returns ids("k", 0, 300)
        pages[null] = page(ids("k", 0, 200), "c1")
        pages["c1"] = page(ids("k", 200, 400), "c2")
        pages["c2"] = page(ids("k", 400, 1000), null)
        track_cursors()

        manager.refresh_index_and_wait()

        val shown = seen_progress.map { it?.indexed ?: 0 }
        assertEquals(3, shown.size)
        assertEquals(300, shown.first())
        assertEquals(1000, seen_progress.first()?.total)
        assertTrue(shown.zipWithNext().all { (a, b) -> b >= a })
        assertTrue(shown.all { it >= 300 })
        assertEquals(true, prefs_values["index_complete"])
    }

    @Test
    fun `a periodic full check of a completed index stays hidden`() = runTest {
        inbox_total = 400
        coEvery { dao.get_all_ids() } returns ids("k", 0, 400)
        pages[null] = page(ids("k", 0, 200), "c1")
        pages["c1"] = page(ids("k", 200, 400), null)
        mark_completed(fresh = false)
        track_cursors()

        manager.refresh_index_and_wait()

        assertEquals(listOf(null, "c1"), fetched_cursors)
        coVerify(exactly = 0) { repository.decrypt_items_for_cache(any()) }
        assertTrue(seen_progress.all { it == null })
        assertTrue(manager.index_ready.value)
        assertEquals(true, prefs_values["index_complete"])
    }
}
