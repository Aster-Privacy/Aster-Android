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

package org.astermail.android.subscriptions

import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import io.mockk.verify
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.astermail.android.api.subscriptions.EncryptedSubscriptionCache
import org.astermail.android.api.subscriptions.SaveEncryptedSubscriptionCacheRequest
import org.astermail.android.api.subscriptions.SaveEncryptedSubscriptionCacheResult
import org.astermail.android.api.subscriptions.SubscriptionsApi
import org.astermail.android.storage.SessionKeyStore
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class SubscriptionCacheSyncTest {
    private val web_identity_key = "web-identity-key"
    private val web_ciphertext =
        "mTzHL2WgKkzRWu66haSVWxZ31xnYCU+0osQAe4WicMKh6xN4pX6Kpi5NjhlmVHXN50LvbQYDCc3Qo/7TMsy/+/IsIvHVMGlKavgotCChg7/6Au" +
            "cXn+g9O7zBp5tirVs93sFK8yliVHgoLSX9LghAceEL9lpWQzieKLIeu6MAh/eqGNn2ADrKPbnewYCeWT7vMpziF3zMYU612xB64ulZJD9oh+V" +
            "wvFu67MOYrzBRWrA3f3LiIQeroysk5CCPxUgBdFl3ON43of6c+j63jleWQojw3u1H9Xgx2mApAfhvSyZ12F/ZDpUmC7An3NXAg6AGQd2T/D5j" +
            "75X6Z7J0j58JIh0Y/QLaMb45TwOrOeYqWRfoRqbjj7Pd0xrpSFw="
    private val web_nonce = "AQIDBAUGBwgJCgsM"

    private fun parse(value: String): JsonObject = Json.parseToJsonElement(value).jsonObject

    private fun senders(cache: JsonObject): List<String> =
        (cache["subscriptions"] as JsonArray).map { it.jsonObject["sender_email"]!!.jsonPrimitive.content }

    @Test
    fun merge_keeps_the_latest_deletion_and_normalizes_senders() {
        val merged = merge_tombstones(
            mapOf("News@Shop.example " to "2026-11-01T10:00:00.000Z"),
            mapOf("news@shop.example" to "2026-11-02T10:00:00.000Z", "broken" to "2026-11-02T10:00:00.000Z"),
            mapOf("other@shop.example" to "not a date"),
        )
        assertEquals(mapOf("news@shop.example" to "2026-11-02T10:00:00.000Z"), merged)
    }

    @Test
    fun merge_caps_the_list_and_keeps_the_newest() {
        val merged = merge_tombstones(
            mapOf(
                "a@x.example" to "2026-11-01T00:00:00Z",
                "b@x.example" to "2026-11-03T00:00:00Z",
                "c@x.example" to "2026-11-02T00:00:00Z",
            ),
            max_entries = 2,
        )
        assertEquals(listOf("b@x.example", "c@x.example"), merged.keys.toList())
    }

    @Test
    fun apply_hides_deleted_senders_and_keeps_every_other_field() {
        val cache = parse(
            """{"subscriptions":[
                {"sender_email":"news@shop.example","last_received":"2026-11-01T10:00:00.000Z","status":"active"},
                {"sender_email":"keep@shop.example","last_received":"2026-11-01T10:00:00.000Z"}
            ],"last_scan_ts":42,"scan_progress":{"cursor":"abc"},"version":4}""",
        )
        val updated = apply_tombstones_to_cache(cache, mapOf("news@shop.example" to "2026-11-02T00:00:00.000Z"))
        assertEquals(listOf("keep@shop.example"), senders(updated))
        assertEquals(cache["last_scan_ts"], updated["last_scan_ts"])
        assertEquals(cache["scan_progress"], updated["scan_progress"])
        assertEquals(cache["version"], updated["version"])
        val deleted = updated["deleted"]!!.jsonArray.single().jsonObject
        assertEquals("news@shop.example", deleted["sender_email"]!!.jsonPrimitive.content)
        assertEquals("2026-11-02T00:00:00.000Z", deleted["deleted_at"]!!.jsonPrimitive.content)
        assertTrue(cache_needs_upload(cache, updated))
    }

    @Test
    fun apply_keeps_a_sender_with_mail_newer_than_the_deletion() {
        val cache = parse(
            """{"subscriptions":[{"sender_email":"news@shop.example","last_received":"2026-11-05T10:00:00.000Z"}],
               "deleted":[{"sender_email":"news@shop.example","deleted_at":"2026-11-02T00:00:00.000Z"}]}""",
        )
        val updated = apply_tombstones_to_cache(cache, emptyMap())
        assertEquals(listOf("news@shop.example"), senders(updated))
        assertFalse(cache_needs_upload(cache, updated))
    }

    @Test
    fun reads_tombstones_written_by_web() {
        val cache = parse(
            """{"subscriptions":[],"deleted":[
                {"sender_email":"promo@web.example","deleted_at":"2026-11-17T09:30:00.000Z"},
                {"sender_email":"bad"},
                {"deleted_at":"2026-11-17T09:30:00.000Z"}
            ]}""",
        )
        assertEquals(mapOf("promo@web.example" to "2026-11-17T09:30:00.000Z"), cache_tombstones(cache))
    }

    @Test
    fun opens_a_cache_encrypted_by_web() {
        val plaintext = open_subscription_cache(web_ciphertext, web_nonce, listOf(web_identity_key), emptyList())
        assertNotNull(plaintext)
        val cache = parse(String(plaintext!!, Charsets.UTF_8))
        assertEquals(listOf("news@shop.example"), senders(cache))
        assertEquals(mapOf("promo@web.example" to "2026-11-17T09:30:00.000Z"), cache_tombstones(cache))
    }

    @Test
    fun opens_with_a_previous_key_or_a_context_key_and_rejects_a_wrong_key() {
        assertNotNull(open_subscription_cache(web_ciphertext, web_nonce, listOf("new-key", web_identity_key), emptyList()))
        val context_key = derive_subscriptions_key(web_identity_key)
        val kek = java.util.Base64.getEncoder().encodeToString(context_key)
        assertNotNull(open_subscription_cache(web_ciphertext, web_nonce, listOf("new-key"), listOf(kek)))
        assertNull(open_subscription_cache(web_ciphertext, web_nonce, listOf("new-key"), emptyList()))
    }

    @Test
    fun sealed_cache_round_trips_with_the_identity_key() {
        val payload = """{"subscriptions":[]}""".toByteArray(Charsets.UTF_8)
        val (sealed, nonce) = seal_subscription_cache(payload.copyOf(), "android-identity-key")
        val opened = open_subscription_cache(sealed, nonce, listOf("android-identity-key"), emptyList())
        assertArrayEquals(payload, opened)
    }

    @Test
    fun sync_merges_both_directions_and_uploads_the_android_deletion() = runTest {
        val api = mockk<SubscriptionsApi>()
        val keys = mockk<SessionKeyStore>()
        val store = mockk<DeletedSubscriptionsStore>(relaxed = true)
        every { keys.get_identity_key() } returns web_identity_key
        every { keys.get_previous_keys() } returns null
        every { keys.get_decrypt_keks() } returns emptyList()
        every { store.tombstones() } returns mapOf("news@shop.example" to "2026-11-18T08:00:00.000Z")
        coEvery { api.get_encrypted_cache() } returns EncryptedSubscriptionCache(web_ciphertext, web_nonce)
        val saved = slot<SaveEncryptedSubscriptionCacheRequest>()
        coEvery { api.save_encrypted_cache(capture(saved)) } returns SaveEncryptedSubscriptionCacheResult(true)

        val result = SubscriptionCacheSync(api, keys, store).sync_tombstones()

        val expected = mapOf(
            "news@shop.example" to "2026-11-18T08:00:00.000Z",
            "promo@web.example" to "2026-11-17T09:30:00.000Z",
        )
        assertEquals(expected, result)
        verify { store.replace_all(expected) }
        val uploaded = open_subscription_cache(
            saved.captured.encrypted_subscriptions,
            saved.captured.subscriptions_nonce,
            listOf(web_identity_key),
            emptyList(),
        )
        val cache = parse(String(uploaded!!, Charsets.UTF_8))
        assertEquals(emptyList<String>(), senders(cache))
        assertEquals(expected, cache_tombstones(cache))
        assertEquals(4, cache["version"]!!.jsonPrimitive.content.toInt())
    }

    @Test
    fun sync_does_not_upload_when_nothing_changed() = runTest {
        val api = mockk<SubscriptionsApi>()
        val keys = mockk<SessionKeyStore>()
        val store = mockk<DeletedSubscriptionsStore>(relaxed = true)
        every { keys.get_identity_key() } returns web_identity_key
        every { keys.get_previous_keys() } returns null
        every { keys.get_decrypt_keks() } returns emptyList()
        every { store.tombstones() } returns mapOf("promo@web.example" to "2026-11-17T09:30:00.000Z")
        coEvery { api.get_encrypted_cache() } returns EncryptedSubscriptionCache(web_ciphertext, web_nonce)

        SubscriptionCacheSync(api, keys, store).sync_tombstones()

        coVerify(exactly = 0) { api.save_encrypted_cache(any()) }
        verify(exactly = 0) { store.replace_all(any()) }
    }

    @Test
    fun sync_keeps_local_deletions_when_no_cache_exists_or_the_server_fails() = runTest {
        val api = mockk<SubscriptionsApi>()
        val keys = mockk<SessionKeyStore>()
        val store = mockk<DeletedSubscriptionsStore>(relaxed = true)
        val local = mapOf("news@shop.example" to "2026-11-18T08:00:00.000Z")
        every { keys.get_identity_key() } returns web_identity_key
        every { store.tombstones() } returns local
        coEvery { api.get_encrypted_cache() } returns EncryptedSubscriptionCache()
        assertEquals(local, SubscriptionCacheSync(api, keys, store).sync_tombstones())
        coEvery { api.get_encrypted_cache() } throws IllegalStateException("offline")
        assertEquals(local, SubscriptionCacheSync(api, keys, store).sync_tombstones())
        coVerify(exactly = 0) { api.save_encrypted_cache(any()) }
    }
}
