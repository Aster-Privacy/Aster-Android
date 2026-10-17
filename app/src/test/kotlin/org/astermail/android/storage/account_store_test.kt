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

package org.astermail.android.storage

import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class AccountStoreTest {

    private fun account(id: String, picture: String? = null) = StoredAccount(
        id = id,
        email = "$id@astermail.org",
        profile_picture = picture,
        added_at = 1L,
    )

    @Test
    fun reads_do_not_wait_for_a_held_write_lock() {
        val store = AccountStore()
        store.add_or_update(account("a"))
        val lock_field = AccountStore::class.java.getDeclaredField("lock").apply { isAccessible = true }
        val lock = lock_field.get(store)
        val held = CountDownLatch(1)
        val release = CountDownLatch(1)
        val holder = Thread {
            synchronized(lock) {
                held.countDown()
                release.await(10, TimeUnit.SECONDS)
            }
        }
        holder.start()
        assertTrue(held.await(5, TimeUnit.SECONDS))
        val reader = Executors.newSingleThreadExecutor()
        try {
            val result = reader.submit<Int> {
                store.get_all().size + store.count() + (if (store.get_current() != null) 1 else 0) +
                    (if (store.account_exists("a")) 1 else 0) + (if (store.can_add()) 1 else 0) +
                    store.get_others().size + store.get_max_accounts() * 0 +
                    (if (store.get_current_id() == "a") 1 else 0)
            }.get(2, TimeUnit.SECONDS)
            assertEquals(6, result)
        } finally {
            release.countDown()
            holder.join()
            reader.shutdownNow()
        }
    }

    @Test
    fun unchanged_update_keeps_the_same_snapshot() {
        val store = AccountStore()
        store.add_or_update(account("a", picture = "data:image/png;base64,AAAA"))
        val before = store.get_current()
        store.add_or_update(account("a", picture = "data:image/png;base64,AAAA").copy(added_at = 99L))
        assertTrue(before === store.get_current())
        store.add_or_update(account("a", picture = "data:image/png;base64,BBBB"))
        assertEquals("data:image/png;base64,BBBB", store.get_current()?.profile_picture)
        assertEquals(1L, store.get_current()?.added_at)
    }

    @Test
    fun mutations_update_current_account_and_limits() {
        val store = AccountStore()
        store.set_max_accounts(2)
        assertEquals(AccountStore.AddResult.Success, store.add_or_update(account("a")))
        assertEquals(AccountStore.AddResult.Success, store.add_or_update(account("b")))
        assertEquals(AccountStore.AddResult.LimitReached(2), store.add_or_update(account("c")))
        assertFalse(store.can_add())
        assertEquals("b", store.current_account.value?.id)
        assertEquals("a", store.set_current("a")?.id)
        assertEquals("a", store.current_account.value?.id)
        val removed = store.remove("a")
        assertTrue(removed.removed)
        assertEquals("b", removed.switched_to?.id)
        assertEquals("b", store.get_current_id())
        store.clear_all()
        assertEquals(0, store.count())
        assertNull(store.current_account.value)
    }
}
