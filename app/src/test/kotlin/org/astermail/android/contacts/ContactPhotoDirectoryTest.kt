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

package org.astermail.android.contacts

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.astermail.android.ui.contacts.Contact
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ContactPhotoDirectoryTest {

    private val photo_a = "data:image/jpeg;base64,AAAA"
    private val photo_b = "data:image/jpeg;base64,BBBB"

    private class FakeSource(
        var entries: List<ContactPhotoEntry> = emptyList(),
        var list_error: Throwable? = null,
        val fetch_results: MutableMap<String, ContactPhotoFetch> = mutableMapOf(),
    ) : ContactPhotoSource {
        var list_calls = 0
        val fetch_calls = mutableMapOf<String, Int>()

        override suspend fun list_photo_entries(): List<ContactPhotoEntry> {
            list_calls += 1
            list_error?.let { throw it }
            return entries
        }

        override suspend fun fetch_contact_photo(contact_id: String): ContactPhotoFetch {
            fetch_calls[contact_id] = (fetch_calls[contact_id] ?: 0) + 1
            return fetch_results[contact_id] ?: ContactPhotoFetch.Missing
        }
    }

    private fun TestScope.core(
        source: FakeSource,
        seed_budget: Int = CONTACT_PHOTO_SEED_BUDGET_CHARS,
        decode_calls: MutableList<String> = mutableListOf(),
        scope: CoroutineScope = backgroundScope,
    ): ContactPhotoDirectoryCore<String> = ContactPhotoDirectoryCore<String>(
        decode = { decode_calls.add(it); "bitmap:$it" },
        size_of = { 1 },
        max_bitmap_bytes = 64,
        scope = scope,
        clock = { testScheduler.currentTime },
        seed_budget_chars = seed_budget,
    ).also { it.source = source }

    @Test
    fun normalize_contact_email_handles_case_whitespace_and_display_names() {
        assertEquals("alice@example.com", normalize_contact_email("  Alice@Example.COM "))
        assertEquals("alice@example.com", normalize_contact_email("Alice Smith <Alice@Example.com>"))
        assertEquals("bob@example.org", normalize_contact_email("mailto:Bob@Example.org"))
        assertNull(normalize_contact_email(null))
        assertNull(normalize_contact_email(""))
        assertNull(normalize_contact_email("not-an-address"))
        assertNull(normalize_contact_email("@example.com"))
        assertNull(normalize_contact_email("alice@"))
        assertNull(normalize_contact_email("al ice@example.com"))
    }

    @Test
    fun contact_photo_emails_include_work_and_additional_addresses() {
        val contact = Contact(
            id = "c1",
            name = "Alice",
            email = "Alice@Example.com",
            work_email = "alice@work.example",
            raw_json = """
                {"emails":["alice@example.com","ALICE@work.example","extra@home.example"],
                 "email_entries":[{"type":"other","value":"Other@Example.net"}]}
            """.trimIndent(),
        )
        assertEquals(
            listOf("alice@example.com", "alice@work.example", "extra@home.example", "other@example.net"),
            contact_photo_emails(contact),
        )
    }

    @Test
    fun contact_photo_entry_requires_inline_photo_and_live_contact() {
        val base = Contact(id = "c1", name = "Alice", email = "alice@example.com", avatar_url = photo_a)
        assertEquals(ContactPhotoEntry("c1", listOf("alice@example.com"), photo_a), contact_photo_entry(base))
        assertNull(contact_photo_entry(base.copy(avatar_url = "")))
        assertNull(contact_photo_entry(base.copy(avatar_url = "https://example.com/a.png")))
        assertNull(contact_photo_entry(base.copy(deleted_at = "2026-09-01T00:00:00Z")))
        assertNull(contact_photo_entry(base.copy(email = "", raw_json = "")))
    }

    @Test
    fun build_contact_photo_index_maps_every_address_and_keeps_first_match() {
        val index = build_contact_photo_index(
            listOf(
                ContactPhotoEntry("c1", listOf("Alice@Example.com", "alice@work.example"), photo_a),
                ContactPhotoEntry("c2", listOf("alice@example.com", "carol@example.com"), photo_b),
                ContactPhotoEntry("c3", listOf("dave@example.com"), "https://example.com/d.png"),
            ),
        )
        assertEquals(
            mapOf(
                "alice@example.com" to "c1",
                "alice@work.example" to "c1",
                "carol@example.com" to "c2",
            ),
            index,
        )
    }

    @Test
    fun concurrent_lookups_share_one_index_fetch_and_one_decode() = runTest {
        val source = FakeSource(entries = listOf(ContactPhotoEntry("c1", listOf("alice@example.com"), photo_a)))
        val decodes = mutableListOf<String>()
        val directory = core(source, decode_calls = decodes)

        val results = (1..5).map {
            async { directory.photo_for(if (it % 2 == 0) "ALICE@example.com" else "Alice <alice@example.com>") }
        }.awaitAll()

        assertTrue(results.all { it == "bitmap:$photo_a" })
        assertEquals(1, source.list_calls)
        assertEquals(0, source.fetch_calls.size)
        assertEquals(1, decodes.size)
        assertEquals("bitmap:$photo_a", directory.cached("alice@example.com"))
        assertNull(directory.photo_for("stranger@example.com"))
        assertEquals(1, source.list_calls)
    }

    @Test
    fun index_is_refreshed_after_ttl() = runTest {
        val source = FakeSource(entries = listOf(ContactPhotoEntry("c1", listOf("alice@example.com"), photo_a)))
        val directory = core(source)
        assertEquals("bitmap:$photo_a", directory.photo_for("alice@example.com"))

        advanceTimeBy(CONTACT_PHOTO_INDEX_TTL_MS - 1)
        directory.photo_for("alice@example.com")
        assertEquals(1, source.list_calls)

        source.entries = listOf(ContactPhotoEntry("c1", listOf("alice@example.com"), photo_b))
        advanceTimeBy(2)
        val version_before = directory.version.value
        assertEquals("bitmap:$photo_a", directory.photo_for("alice@example.com"))
        runCurrent()
        assertEquals(2, source.list_calls)
        assertTrue(directory.version.value > version_before)
        assertEquals("bitmap:$photo_b", directory.photo_for("alice@example.com"))
    }

    @Test
    fun failed_index_fetch_backs_off_and_schedules_a_retry() = runTest {
        val source = FakeSource(list_error = java.io.IOException("offline"))
        val directory = core(source)

        assertNull(directory.photo_for("alice@example.com"))
        assertNull(directory.photo_for("alice@example.com"))
        assertEquals(1, source.list_calls)

        source.list_error = null
        source.entries = listOf(ContactPhotoEntry("c1", listOf("alice@example.com"), photo_a))
        val version_before = directory.version.value
        advanceTimeBy(CONTACT_PHOTO_RETRY_MS + 1)
        assertTrue(directory.version.value > version_before)
        assertEquals("bitmap:$photo_a", directory.photo_for("alice@example.com"))
        assertEquals(2, source.list_calls)
    }

    @Test
    fun missing_photo_is_negative_cached_but_network_failure_is_retried() = runTest {
        val source = FakeSource(
            entries = listOf(
                ContactPhotoEntry("gone", listOf("gone@example.com"), photo_a),
                ContactPhotoEntry("flaky", listOf("flaky@example.com"), photo_b),
            ),
            fetch_results = mutableMapOf(
                "gone" to ContactPhotoFetch.Missing,
                "flaky" to ContactPhotoFetch.Failed(java.io.IOException("offline")),
            ),
        )
        val directory = core(source, seed_budget = 0)

        assertNull(directory.photo_for("gone@example.com"))
        assertNull(directory.photo_for("gone@example.com"))
        assertEquals(1, source.fetch_calls["gone"])

        assertNull(directory.photo_for("flaky@example.com"))
        assertNull(directory.photo_for("flaky@example.com"))
        assertEquals(1, source.fetch_calls["flaky"])

        source.fetch_results["flaky"] = ContactPhotoFetch.Found(photo_b)
        advanceTimeBy(CONTACT_PHOTO_RETRY_MS + 1)
        assertEquals("bitmap:$photo_b", directory.photo_for("flaky@example.com"))
        assertEquals(2, source.fetch_calls["flaky"])
        assertNull(directory.photo_for("gone@example.com"))
        assertEquals(1, source.fetch_calls["gone"])
    }

    @Test
    fun clear_drops_cached_photos_and_bumps_version() = runTest {
        val source = FakeSource(entries = listOf(ContactPhotoEntry("c1", listOf("alice@example.com"), photo_a)))
        val directory = core(source)
        assertEquals("bitmap:$photo_a", directory.photo_for("alice@example.com"))

        val version_before = directory.version.value
        directory.clear()
        assertTrue(directory.version.value > version_before)
        assertNull(directory.cached("alice@example.com"))

        source.entries = emptyList()
        assertNull(directory.photo_for("alice@example.com"))
        assertEquals(2, source.list_calls)
    }

    @Test
    fun offered_contacts_fill_the_index_without_a_fetch() = runTest {
        val source = FakeSource()
        val directory = core(source)
        directory.offer(listOf(ContactPhotoEntry("c1", listOf("alice@example.com"), photo_a)))

        assertEquals("bitmap:$photo_a", directory.photo_for("alice@example.com"))
        assertEquals(0, source.list_calls)
    }

    @Test
    fun lru_evicts_least_recently_used_entries() {
        val lru = ContactPhotoLru<String>(max_bytes = 2, size_of = { 1 })
        lru.put("a", "A")
        lru.put("b", "B")
        lru.get("a")
        lru.put("c", "C")
        assertEquals("A", lru.get("a"))
        assertNull(lru.get("b"))
        assertEquals("C", lru.get("c"))
        assertEquals(2, lru.size())
    }
}
