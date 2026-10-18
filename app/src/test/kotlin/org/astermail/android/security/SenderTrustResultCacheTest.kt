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

package org.astermail.android.security

import org.json.JSONArray
import org.json.JSONObject
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotSame
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Before
import org.junit.Test

class SenderTrustResultCacheTest {

    @Before
    fun reset_before() {
        SenderTrustStore.clear()
    }

    @After
    fun reset_after() {
        SenderTrustStore.clear()
    }

    private fun counting(): Pair<SenderTrustResultCache, IntArray> {
        val calls = IntArray(1)
        val cache = SenderTrustResultCache(evaluate = { input ->
            calls[0] += 1
            evaluate_sender_trust(input)
        })
        return cache to calls
    }

    private fun input_for(context: SenderTrustContext, name: String, email: String): SenderTrustInput =
        SenderTrustInput(
            name = name,
            email = email,
            sender_aligned = true,
            own_addresses = context.own_addresses,
            contacts = context.contacts,
        )

    private fun load(name: String): JSONObject {
        val stream = checkNotNull(javaClass.classLoader?.getResourceAsStream(name))
        return JSONObject(stream.bufferedReader(Charsets.UTF_8).use { it.readText() })
    }

    private fun strings(array: JSONArray?): List<String> {
        if (array == null) return emptyList()
        return (0 until array.length()).map { array.getString(it) }
    }

    private fun objects(array: JSONArray): List<JSONObject> =
        (0 until array.length()).map { array.getJSONObject(it) }

    private fun text(source: JSONObject, key: String): String? =
        if (source.has(key) && !source.isNull(key)) source.getString(key) else null

    private fun flag(source: JSONObject, key: String): Boolean? =
        if (source.has(key) && !source.isNull(key)) source.getBoolean(key) else null

    private fun context_of(source: JSONObject): SenderTrustContext =
        SenderTrustContext(
            own_addresses = strings(source.optJSONArray("own_addresses")),
            contacts = source.optJSONArray("contacts")?.let { array ->
                objects(array).map { SenderTrustContact(it.optString("name", ""), strings(it.optJSONArray("emails"))) }
            } ?: emptyList(),
        )

    private fun input_of(source: JSONObject, context: SenderTrustContext): SenderTrustInput =
        SenderTrustInput(
            name = text(source, "name"),
            email = text(source, "email"),
            reply_to = text(source, "reply_to"),
            spf_result = text(source, "spf_result"),
            dkim_result = text(source, "dkim_result"),
            dmarc_result = text(source, "dmarc_result"),
            sender_aligned = flag(source, "sender_aligned"),
            sender_verified = flag(source, "sender_verified"),
            sender_verified_domain = text(source, "sender_verified_domain"),
            is_internal = flag(source, "is_internal") == true,
            own_addresses = context.own_addresses,
            contacts = context.contacts,
        )

    @Test
    fun same_input_and_context_evaluates_once() {
        val (cache, calls) = counting()
        val context = SenderTrustContext(own_addresses = listOf("me@astermail.org"))

        val first = cache.trust_for(input_for(context, "Pat Doe", "pat@acme.example"), context)
        val second = cache.trust_for(input_for(context, "Pat Doe", "pat@acme.example"), context)

        assertSame(first, second)
        assertEquals(1, calls[0])
    }

    @Test
    fun any_changed_field_evaluates_again() {
        val (cache, calls) = counting()
        val context = SenderTrustContext()
        val base = input_for(context, "Pat Doe", "pat@acme.example")
        val variants = listOf(
            base,
            base.copy(name = "Pat Do"),
            base.copy(email = "pat@acme.example.net"),
            base.copy(reply_to = "other@evil.example"),
            base.copy(spf_result = "fail"),
            base.copy(dkim_result = "fail"),
            base.copy(dmarc_result = "fail"),
            base.copy(sender_aligned = false),
            base.copy(sender_verified = true),
            base.copy(sender_verified_domain = "acme.example"),
            base.copy(is_internal = true),
        )

        for (variant in variants) {
            assertEquals(evaluate_sender_trust(variant), cache.trust_for(variant, context))
        }

        assertEquals(variants.size, calls[0])
    }

    @Test
    fun new_context_evaluates_again() {
        val (cache, calls) = counting()
        val empty = SenderTrustContext()
        val known = SenderTrustContext(contacts = listOf(SenderTrustContact("Pat Doe", listOf("pat@acme.example"))))

        val before = cache.trust_for(input_for(empty, "Pat Doe", "pat.doe@freemail.example"), empty)
        val after = cache.trust_for(input_for(known, "Pat Doe", "pat.doe@freemail.example"), known)
        val again = cache.trust_for(input_for(empty, "Pat Doe", "pat.doe@freemail.example"), empty)

        assertNull(before.contact_notice)
        assertEquals("Pat Doe", after.contact_notice)
        assertNull(again.contact_notice)
        assertEquals(3, calls[0])
    }

    @Test
    fun input_built_from_other_lists_is_not_cached() {
        val (cache, calls) = counting()
        val context = SenderTrustContext()
        val foreign = SenderTrustContext(contacts = listOf(SenderTrustContact("Pat Doe", listOf("pat@acme.example"))))
        val input = input_for(foreign, "Pat Doe", "pat.doe@freemail.example")

        assertEquals("Pat Doe", cache.trust_for(input, context).contact_notice)
        assertEquals("Pat Doe", cache.trust_for(input, context).contact_notice)
        assertEquals(2, calls[0])
    }

    @Test
    fun long_input_is_not_cached() {
        val (cache, calls) = counting()
        val context = SenderTrustContext()
        val input = input_for(context, "a".repeat(5000), "pat@acme.example")

        cache.trust_for(input, context)
        cache.trust_for(input, context)

        assertEquals(2, calls[0])
    }

    @Test
    fun oldest_entries_are_evicted_past_the_bound() {
        val calls = IntArray(1)
        val cache = SenderTrustResultCache(max_entries = 2, evaluate = { input ->
            calls[0] += 1
            evaluate_sender_trust(input)
        })
        val context = SenderTrustContext()

        cache.trust_for(input_for(context, "A", "a@one.example"), context)
        cache.trust_for(input_for(context, "B", "b@two.example"), context)
        cache.trust_for(input_for(context, "C", "c@three.example"), context)
        cache.trust_for(input_for(context, "C", "c@three.example"), context)
        cache.trust_for(input_for(context, "A", "a@one.example"), context)

        assertEquals(4, calls[0])
    }

    @Test
    fun clear_drops_every_entry() {
        val (cache, calls) = counting()
        val context = SenderTrustContext()
        val input = input_for(context, "Pat Doe", "pat@acme.example")

        cache.trust_for(input, context)
        cache.clear()
        cache.trust_for(input, context)

        assertEquals(2, calls[0])
    }

    @Test
    fun cached_results_match_uncached_for_every_shared_vector() {
        val cache = SenderTrustResultCache()
        val vectors = objects(load("sender_trust_vectors.json").getJSONArray("vectors"))
        val shared = SenderTrustContext()
        for (pass in 0 until 2) {
            for (vector in vectors) {
                val source = vector.getJSONObject("input")
                val own = context_of(source)
                val own_input = input_of(source, own)
                val id = vector.getString("id")
                assertEquals(id, evaluate_sender_trust(own_input), cache.trust_for(own_input, own))
                assertEquals(id, evaluate_sender_trust(own_input), cache.trust_for(own_input, own))
                val shared_input = input_of(source, shared)
                assertEquals(id, evaluate_sender_trust(shared_input), cache.trust_for(shared_input, shared))
            }
        }
    }

    @Test
    fun store_context_results_are_reused() {
        val message = SenderTrustMessage(sender_name = "Pat Doe", sender_email = "pat@acme.example", sender_aligned = true)

        val first = sender_trust_for(message, SenderTrustStore.context.value)
        val second = sender_trust_for(message, SenderTrustStore.context.value)

        assertSame(first, second)
    }

    @Test
    fun store_contacts_change_is_seen() {
        val message = SenderTrustMessage(
            sender_name = "Pat Doe",
            sender_email = "pat.doe@freemail.example",
            sender_aligned = true,
        )

        val before = sender_trust_for(message, SenderTrustStore.context.value)
        SenderTrustStore.set_contacts(listOf(SenderTrustContact("Pat Doe", listOf("pat@acme.example"))))
        val after = sender_trust_for(message, SenderTrustStore.context.value)
        SenderTrustStore.clear()
        val cleared = sender_trust_for(message, SenderTrustStore.context.value)

        assertNull(before?.contact_notice)
        assertEquals("Pat Doe", after?.contact_notice)
        assertNull(cleared?.contact_notice)
        assertNotSame(before, cleared)
    }
}
