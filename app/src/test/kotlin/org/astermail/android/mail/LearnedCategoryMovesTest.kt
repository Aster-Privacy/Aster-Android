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

import org.astermail.android.api.mail.MailItemMetadata
import org.astermail.android.api.preferences.CustomCategoryRule
import org.astermail.android.api.preferences.LearnedCategorySender
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class LearnedCategoryMovesTest {

    private fun source(
        sender: String,
        verification: String? = null,
        system_origin: Boolean = false,
        is_external: Boolean = true,
    ) = LearnedMoveSource(sender, verification, system_origin, is_external)

    private fun entry(sender: String, category: String, at: Long) = LearnedCategorySender(sender, category, at)

    private fun envelope(
        from_email: String,
        subject: String = "",
        sender_verification: String? = null,
        raw_headers: List<Pair<String, String>> = emptyList(),
        list_unsubscribe: String? = null,
    ) = DecryptedEnvelope(
        subject = subject,
        body_text = "",
        body_html = null,
        from_name = "",
        from_email = from_email,
        to = emptyList(),
        cc = emptyList(),
        sent_at = "2026-06-06T00:00:00Z",
        raw_headers = raw_headers,
        list_unsubscribe = list_unsubscribe,
        sender_verification = sender_verification,
    )

    @Test
    fun single_move_learns_the_sender() {
        val move = learn_from_category_move(emptyList(), true, listOf(source("Shop@Store.com")), "shopping", 100)
        assertNotNull(move)
        assertEquals(listOf(entry("shop@store.com", "shopping", 100)), move!!.apply.upserts)
        assertEquals(
            listOf(entry("shop@store.com", "shopping", 100)),
            apply_learned_category_edit(emptyList(), move.apply),
        )
    }

    @Test
    fun moving_to_primary_is_learned() {
        val move = learn_from_category_move(emptyList(), true, listOf(source("friend@x.com")), "primary", 5)
        assertEquals(listOf(entry("friend@x.com", "primary", 5)), move!!.apply.upserts)
    }

    @Test
    fun bulk_move_records_each_distinct_sender_once() {
        val sources = listOf(
            source("a@x.com"),
            source("A@X.com"),
            source("b@x.com"),
            source("c@x.com"),
        )
        val move = learn_from_category_move(emptyList(), true, sources, "custom:club", 7)
        assertEquals(listOf("a@x.com", "b@x.com", "c@x.com"), move!!.apply.upserts.map { it.sender })
        assertTrue(move.apply.upserts.all { it.category == "custom:club" && it.updated_at == 7L })
    }

    @Test
    fun bulk_move_is_capped_per_move() {
        val sources = (1..MAX_LEARNED_SENDERS_PER_MOVE + 50).map { source("user$it@x.com") }
        val move = learn_from_category_move(emptyList(), true, sources, "updates", 1)
        assertEquals(MAX_LEARNED_SENDERS_PER_MOVE, move!!.apply.upserts.size)
    }

    @Test
    fun locked_official_sender_is_skipped() {
        val sources = listOf(
            source("noreply@astermail.org", system_origin = true, is_external = false),
            source("person@x.com"),
        )
        val move = learn_from_category_move(emptyList(), true, sources, "updates", 1)
        assertEquals(listOf("person@x.com"), move!!.apply.upserts.map { it.sender })
    }

    @Test
    fun only_locked_senders_learn_nothing() {
        val sources = listOf(source("noreply@astermail.org", system_origin = true, is_external = false))
        assertNull(learn_from_category_move(emptyList(), true, sources, "updates", 1))
    }

    @Test
    fun invalid_verification_is_skipped() {
        val sources = listOf(source("spoof@x.com", verification = "invalid"), source("real@x.com", verification = "valid"))
        val move = learn_from_category_move(emptyList(), true, sources, "promotions", 1)
        assertEquals(listOf("real@x.com"), move!!.apply.upserts.map { it.sender })
    }

    @Test
    fun learning_off_records_nothing() {
        assertNull(learn_from_category_move(emptyList(), false, listOf(source("a@x.com")), "social", 1))
    }

    @Test
    fun invalid_category_records_nothing() {
        assertNull(learn_from_category_move(emptyList(), true, listOf(source("a@x.com")), "bogus", 1))
    }

    @Test
    fun undo_restores_previous_entries_and_removes_new_ones() {
        val before = listOf(entry("old@x.com", "social", 50), entry("keep@x.com", "travel", 40))
        val move = learn_from_category_move(
            before,
            true,
            listOf(source("old@x.com"), source("new@x.com")),
            "promotions",
            100,
        )!!
        val after = apply_learned_category_edit(before, move.apply)
        assertEquals(
            setOf(entry("old@x.com", "promotions", 100), entry("new@x.com", "promotions", 100), entry("keep@x.com", "travel", 40)),
            after.toSet(),
        )
        val restored = apply_learned_category_edit(after, move.undo)
        assertEquals(before, restored)
    }

    @Test
    fun lookup_uses_the_newest_entry() {
        val lookup = learned_category_lookup(listOf(entry("a@x.com", "social", 1), entry("a@x.com", "finance", 2)))
        assertEquals(mapOf("a@x.com" to "finance"), lookup)
    }

    @Test
    fun learned_beats_heuristics() {
        val env = envelope(
            from_email = "Deals@Shop.com",
            subject = "50% off everything today only",
            list_unsubscribe = "<mailto:unsub@shop.com>",
        )
        assertEquals("finance", classify(env, null, learned = mapOf("deals@shop.com" to "finance")))
    }

    @Test
    fun learned_primary_beats_heuristics() {
        val env = envelope(from_email = "deals@shop.com", subject = "50% off everything", list_unsubscribe = "<mailto:u@shop.com>")
        assertEquals("primary", classify(env, null, learned = mapOf("deals@shop.com" to "primary")))
    }

    @Test
    fun pin_beats_learned() {
        val env = envelope(from_email = "deals@shop.com")
        val meta = MailItemMetadata(category = "updates", category_pinned = true)
        assertEquals("updates", classify(env, meta, learned = mapOf("deals@shop.com" to "finance")))
    }

    @Test
    fun official_aster_mail_stays_primary_over_learned() {
        val env = envelope(from_email = "noreply@astermail.org")
        assertEquals("primary", classify(env, null, learned = mapOf("noreply@astermail.org" to "promotions")))
    }

    @Test
    fun rule_beats_learned() {
        val env = envelope(from_email = "deals@shop.com")
        assertEquals("social", classify(env, null, rule_category = "social", learned = mapOf("deals@shop.com" to "finance")))
    }

    @Test
    fun learned_is_ignored_for_invalid_verification() {
        val env = envelope(from_email = "deals@shop.com", sender_verification = "invalid")
        val learned = mapOf("deals@shop.com" to "finance")
        assertEquals(classify(env, null), classify(env, null, learned = learned))
        assertNull(resolve_learned_category("deals@shop.com", "invalid", learned, emptyList()))
    }

    @Test
    fun learned_custom_category_needs_to_be_enabled() {
        val learned = mapOf("a@x.com" to "custom:club")
        val enabled = listOf(CustomCategoryRule(id = "custom:club", name = "Club", enabled = true))
        val disabled = listOf(CustomCategoryRule(id = "custom:club", name = "Club", enabled = false))
        assertEquals("custom:club", resolve_learned_category("A@X.com", null, learned, enabled))
        assertNull(resolve_learned_category("a@x.com", null, learned, disabled))
        assertNull(resolve_learned_category("a@x.com", null, learned, emptyList()))
    }

    @Test
    fun learned_beats_custom_category_match() {
        val rules = listOf(CustomCategoryRule(id = "custom:shops", name = "Shops", match_domains = listOf("shop.com")))
        val env = envelope(from_email = "deals@shop.com")
        assertEquals("custom:shops", classify(env, null, custom_categories = rules))
        assertEquals(
            "travel",
            classify(env, null, custom_categories = rules, learned = mapOf("deals@shop.com" to "travel")),
        )
    }

    @Test
    fun account_access_mail_beats_learned() {
        val learned = mapOf("alerts@bank.example.test" to "promotions")
        val by_subject = envelope(from_email = "alerts@bank.example.test", subject = "Your verification code")
        assertEquals("primary", classify(by_subject, null, learned = learned))
        val by_header = envelope(
            from_email = "alerts@bank.example.test",
            subject = "Hello",
            raw_headers = listOf("X-Verify-Code" to "123456"),
        )
        assertEquals("primary", classify(by_header, null, learned = learned))
    }

    @Test
    fun aster_domain_sender_is_not_learned_even_when_not_locked() {
        val sources = listOf(source("person@astermail.org"), source("friend@aster.cx"), source("shop@store.com"))
        val move = learn_from_category_move(emptyList(), true, sources, "promotions", 1)
        assertEquals(listOf("shop@store.com"), move!!.apply.upserts.map { it.sender })
        assertNull(learn_from_category_move(emptyList(), true, listOf(source("person@astermail.org")), "promotions", 1))
    }

    @Test
    fun aster_domain_sender_ignores_a_learned_entry() {
        val learned = mapOf("person@astermail.org" to "promotions")
        assertNull(resolve_learned_category("person@astermail.org", null, learned, emptyList()))
        assertEquals("primary", classify(envelope(from_email = "person@astermail.org"), null, learned = learned))
    }

    @Test
    fun failed_auth_sender_is_not_learned() {
        val sources = listOf(
            LearnedMoveSource("spoof@x.com", null, false, true, auth_failed = true),
            source("real@x.com"),
        )
        val move = learn_from_category_move(emptyList(), true, sources, "social", 1)
        assertEquals(listOf("real@x.com"), move!!.apply.upserts.map { it.sender })
    }

    @Test
    fun failed_auth_sender_ignores_a_learned_entry() {
        val env = envelope(from_email = "deals@shop.com")
        val learned = mapOf("deals@shop.com" to "finance")
        assertEquals("finance", classify(env, null, learned = learned))
        assertEquals(classify(env, null), classify(env, null, learned = learned, auth_failed = true))
        assertNull(resolve_learned_category("deals@shop.com", null, learned, emptyList(), auth_failed = true))
    }

    @Test
    fun auth_results_only_fail_on_an_explicit_failure() {
        assertTrue(sender_auth_failed("fail", null, null))
        assertTrue(sender_auth_failed(null, " FAIL ", null))
        assertTrue(sender_auth_failed(null, null, "hardfail"))
        assertEquals(false, sender_auth_failed(null, null, null))
        assertEquals(false, sender_auth_failed("pass", "none", "softfail"))
        assertEquals(false, sender_auth_failed("", "neutral", "temperror"))
    }

    @Test
    fun learning_and_applying_share_one_rule() {
        assertEquals(false, is_learned_sender_eligible("a@x.com", "invalid", false))
        assertEquals(false, is_learned_sender_eligible("a@x.com", null, true))
        assertEquals(false, is_learned_sender_eligible("a@astermail.org", null, false))
        assertTrue(is_learned_sender_eligible("a@x.com", null, false))
        assertTrue(is_learned_sender_eligible("a@x.com", "valid", false))
    }

    @Test
    fun forget_clears_every_learned_sender() {
        val before = listOf(entry("a@x.com", "social", 1), entry("b@x.com", "updates", 2))
        val forget = LearnedCategoryEdit(removals = before.map { it.sender }.toSet())
        assertTrue(apply_learned_category_edit(before, forget).isEmpty())
    }
}
