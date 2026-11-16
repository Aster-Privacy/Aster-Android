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

package org.astermail.android.settings

import kotlinx.coroutines.delay
import kotlinx.coroutines.test.runTest
import org.astermail.android.ui.contacts.Contact
import org.astermail.android.ui.contacts.ContactEntry
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.security.MessageDigest
import java.util.Base64
import java.util.Locale

class AliasContactPickTest {

    private fun hash(text: String): String {
        val normalized = text.lowercase(Locale.ROOT).trim()
        val digest = MessageDigest.getInstance("SHA-256").digest(normalized.toByteArray(Charsets.UTF_8))
        return Base64.getEncoder().encodeToString(digest)
    }

    private val ada = Contact(
        id = "c1",
        name = "Ada Lovelace",
        email = "",
        emails = listOf(
            ContactEntry("Ada@Example.com", ContactEntry.TYPE_PERSONAL),
            ContactEntry("ada@work.example", ContactEntry.TYPE_WORK),
        ),
    )
    private val bob = Contact(id = "c2", name = "Bob", email = "bob@example.org")
    private val trashed = Contact(id = "c3", name = "Gone", email = "gone@example.com", deleted_at = "2026-11-01")
    private val no_email = Contact(id = "c4", name = "Phone only", email = "", phone = "123")
    private val bad_email = Contact(id = "c5", name = "Broken", email = "not-an-address", work_email = "also bad@")
    private val nameless = Contact(id = "c6", name = "", email = "zed@example.net")

    private fun pin(sender: String, blocked: Boolean = false, sender_hash: String = "") =
        DecryptedAliasPin(id = sender, sender = sender, is_blocked = blocked, sender_hash = sender_hash)

    @Test
    fun addresses_are_normalized_deduped_and_skip_trash() {
        assertEquals(
            listOf("ada@example.com", "ada@work.example", "bob@example.org"),
            contact_pick_addresses(listOf(ada, bob, trashed, no_email, bad_email)),
        )
    }

    @Test
    fun statuses_match_decrypted_senders_case_insensitively() {
        val statuses = resolve_pick_statuses(
            emptyList(),
            listOf(pin("  BOB@example.org "), pin("ada@work.example", blocked = true)),
            ::hash,
        )
        assertEquals(PickEmailStatus.ADDED, statuses["bob@example.org"])
        assertEquals(PickEmailStatus.BLOCKED, statuses["ada@work.example"])
    }

    @Test
    fun statuses_fall_back_to_the_sender_hash_for_undecryptable_pins() {
        val statuses = resolve_pick_statuses(
            listOf("ada@example.com", "bob@example.org"),
            listOf(pin("Unknown sender", sender_hash = hash("ADA@example.com"))),
            ::hash,
        )
        assertEquals(PickEmailStatus.ADDED, statuses["ada@example.com"])
        assertFalse(statuses.containsKey("bob@example.org"))
    }

    @Test
    fun a_block_wins_over_an_allow_pin_for_the_same_sender() {
        val statuses = resolve_pick_statuses(
            emptyList(),
            listOf(pin("x@example.com", blocked = true), pin("x@example.com")),
            ::hash,
        )
        assertEquals(PickEmailStatus.BLOCKED, statuses["x@example.com"])
    }

    @Test
    fun candidates_list_every_address_and_put_finished_contacts_last() {
        val statuses = mapOf(
            "bob@example.org" to PickEmailStatus.ADDED,
            "ada@work.example" to PickEmailStatus.BLOCKED,
        )
        val candidates = build_pick_candidates(listOf(bob, ada, trashed, no_email, bad_email, nameless), statuses)

        assertEquals(listOf("c1", "c6", "c2"), candidates.map { it.contact_id })
        assertEquals(
            listOf(
                PickEmail("ada@example.com", ContactEntry.TYPE_PERSONAL, "", PickEmailStatus.AVAILABLE),
                PickEmail("ada@work.example", ContactEntry.TYPE_WORK, "", PickEmailStatus.BLOCKED),
            ),
            candidates.first().emails,
        )
        assertTrue(candidates.last().open_emails.isEmpty())
        assertTrue(build_pick_candidates(emptyList(), emptyMap()).isEmpty())
    }

    @Test
    fun hundreds_of_contacts_build_and_select_all_respects_the_cap() {
        val many = (0 until 600).map { Contact(id = "m$it", name = "P$it", email = "p$it@ex.com") }
        val built = build_pick_candidates(many, emptyMap())

        assertEquals(600, built.size)
        assertEquals(MAX_CONTACT_PICK, select_all_visible(emptySet(), built).size)
    }

    @Test
    fun filter_matches_names_and_addresses() {
        val candidates = build_pick_candidates(listOf(ada, bob, nameless), emptyMap())

        assertEquals(listOf("c1"), filter_pick_candidates(candidates, "love").map { it.contact_id })
        assertEquals(listOf("c1"), filter_pick_candidates(candidates, "WORK.EX").map { it.contact_id })
        assertEquals(listOf("c6"), filter_pick_candidates(candidates, "zed").map { it.contact_id })
        assertEquals(3, filter_pick_candidates(candidates, "   ").size)
        assertTrue(filter_pick_candidates(candidates, "nobody").isEmpty())
    }

    @Test
    fun toggle_normalizes_and_respects_the_limit() {
        var selected = toggle_pick(emptySet(), " Ada@Example.com ", true)
        assertEquals(setOf("ada@example.com"), selected)
        selected = toggle_pick(selected, "ada@example.com", false)
        assertTrue(selected.isEmpty())
        assertEquals(1, toggle_pick(setOf("a@b.co"), "c@d.co", true, limit = 1).size)
    }

    @Test
    fun select_all_takes_the_first_open_address_and_keeps_an_existing_choice() {
        val candidates = build_pick_candidates(
            listOf(ada, bob, nameless),
            mapOf("bob@example.org" to PickEmailStatus.ADDED),
        )
        val fresh = select_all_visible(emptySet(), candidates)
        assertEquals(setOf("ada@example.com", "zed@example.net"), fresh)
        assertTrue(select_all_saturated(fresh, candidates))
        assertFalse(select_all_saturated(emptySet(), candidates))

        val kept = select_all_visible(setOf("ada@work.example"), candidates)
        assertTrue("ada@work.example" in kept)
        assertFalse("ada@example.com" in kept)
    }

    @Test
    fun select_all_reads_as_full_once_the_limit_stops_it_adding_more() {
        val candidates = build_pick_candidates(listOf(ada, nameless), emptyMap())
        val partial = select_all_visible(emptySet(), candidates, limit = 1)
        assertEquals(1, partial.size)
        assertFalse(select_all_saturated(partial, candidates))
        assertTrue(select_all_saturated(partial, candidates, limit = 1))
        assertTrue(clear_visible(partial, candidates).isEmpty())
    }

    @Test
    fun clear_removes_only_visible_addresses() {
        val candidates = build_pick_candidates(listOf(ada, nameless), emptyMap())
        val cleared = clear_visible(
            setOf("ada@example.com", "zed@example.net", "kept@example.com"),
            filter_pick_candidates(candidates, "ada"),
        )
        assertEquals(setOf("zed@example.net", "kept@example.com"), cleared)
    }

    @Test
    fun finalize_drops_listed_blocked_invalid_and_duplicates_and_caps() {
        val final = finalize_picks(
            setOf("ada@example.com", "ADA@example.com", "bob@example.org", "junk", "z@b.co"),
            mapOf("bob@example.org" to PickEmailStatus.ADDED, "z@b.co" to PickEmailStatus.BLOCKED),
        )
        assertEquals(listOf("ada@example.com"), final)

        val many = (0 until 250).mapTo(LinkedHashSet()) { "u$it@ex.com" }
        assertEquals(MAX_CONTACT_PICK, finalize_picks(many, emptyMap()).size)
    }

    @Test
    fun bulk_add_limits_concurrency_and_reports_failures_in_order() = runTest {
        var in_flight = 0
        var peak = 0
        val result = add_senders_in_batches(
            listOf("a@x.co", "b@x.co", "c@x.co", "d@x.co", "e@x.co", "f@x.co"),
            concurrency = 2,
        ) { address ->
            in_flight += 1
            peak = maxOf(peak, in_flight)
            delay(5)
            in_flight -= 1
            if (address == "e@x.co") throw IllegalStateException("network")
            address != "b@x.co"
        }

        assertEquals(2, peak)
        assertEquals(listOf("a@x.co", "c@x.co", "d@x.co", "f@x.co"), result.added)
        assertEquals(listOf("b@x.co", "e@x.co"), result.failed)
        assertEquals(BulkAddResult(emptyList(), emptyList()), add_senders_in_batches(emptyList()) { true })
    }
}
