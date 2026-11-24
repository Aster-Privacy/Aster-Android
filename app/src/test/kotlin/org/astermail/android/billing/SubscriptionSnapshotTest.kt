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

package org.astermail.android.billing

import kotlinx.serialization.json.Json
import org.astermail.android.api.ApiError
import org.astermail.android.api.billing.SubscriptionResponse
import org.astermail.android.api.settings.SubscriptionInfo
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class SubscriptionSnapshotTest {
    private val json = Json {
        ignoreUnknownKeys = true
        explicitNulls = false
        coerceInputValues = true
        isLenient = true
    }

    private val supernova_body = """
        {
          "plan": {
            "id": "7f1c2d1e-0000-4000-8000-000000000001",
            "code": "supernova",
            "name": "Supernova",
            "description": null,
            "storage_limit_bytes": 1099511627776,
            "price_cents": 1499,
            "billing_period": "month"
          },
          "status": "active",
          "cancel_at_period_end": false,
          "current_period_start": "2026-11-01T00:00:00Z",
          "current_period_end": "2026-12-01T00:00:00Z",
          "storage": {
            "used_bytes": 5368709120,
            "limit_bytes": 1099511627776,
            "total_limit_bytes": 1099511627776,
            "percentage_used": 0.49,
            "is_over_limit": false
          },
          "currency": "usd",
          "payment_failed_at": null,
          "grace_period_end": null,
          "payment_provider": "google_play",
          "has_stripe_subscription": false,
          "paid_until": null,
          "active_discount_description": null,
          "is_lifetime": false,
          "pending_offer": { "kind": "yearly", "percent_off": 20 },
          "yearly_switch_offer": null,
          "last_card_decline": null,
          "pay_url": "https://app.astermail.org/pay"
        }
    """.trimIndent()

    @Test
    fun supernova_parses_as_subscription_info() {
        val info = json.decodeFromString(SubscriptionInfo.serializer(), supernova_body)
        assertEquals("supernova", info.plan?.code)
        assertEquals("Supernova", info.effective_plan_name)
        assertEquals(1499, info.effective_price_cents)
        assertEquals("month", info.effective_interval)
        assertEquals("google_play", info.payment_provider)
        assertEquals("active", info.status)
    }

    @Test
    fun supernova_parses_as_subscription_response_and_maps() {
        val response = json.decodeFromString(SubscriptionResponse.serializer(), supernova_body)
        val info = response.to_subscription_info()
        assertNotNull(info)
        assertEquals("supernova", info!!.plan?.code)
        assertEquals("Supernova", info.effective_plan_name)
        assertEquals(1499, info.effective_price_cents)
        assertEquals("2026-12-01T00:00:00Z", info.current_period_end)
        assertEquals("google_play", info.payment_provider)
        assertEquals(1099511627776L, info.plan?.storage_limit_bytes)
    }

    @Test
    fun admin_granted_plan_with_status_none_and_null_fields_parses() {
        val body = """
            {
              "plan": { "id": "p", "code": "supernova", "name": "Supernova", "price_cents": null, "billing_period": null },
              "status": "none",
              "cancel_at_period_end": null,
              "current_period_end": null,
              "currency": null,
              "payment_provider": null,
              "has_stripe_subscription": null,
              "is_lifetime": null
            }
        """.trimIndent()
        val info = json.decodeFromString(SubscriptionInfo.serializer(), body)
        assertEquals("none", info.status)
        assertEquals(0, info.effective_price_cents)
        assertFalse(info.cancel_at_period_end)
        assertFalse(info.is_lifetime)
        val mapped = json.decodeFromString(SubscriptionResponse.serializer(), body).to_subscription_info()
        assertNotNull(mapped)
        assertEquals(subscription_payment_source.none, subscription_payment_source_of(null, null, true))
        assertEquals(
            subscription_status_kind.active,
            subscription_status_kind_of(mapped!!.status, true, false, false, false),
        )
    }

    @Test
    fun expired_status_parses_and_maps_to_expired() {
        val body = """{"plan":{"code":"nova","name":"Nova","price_cents":499},"status":"expired","payment_provider":"stripe"}"""
        val info = json.decodeFromString(SubscriptionInfo.serializer(), body)
        assertEquals("expired", info.status)
        assertEquals(
            subscription_status_kind.expired,
            subscription_status_kind_of(info.status, true, false, false, false),
        )
    }

    @Test
    fun free_plan_with_empty_body_parses() {
        val info = json.decodeFromString(SubscriptionInfo.serializer(), "{}")
        assertNull(info.plan)
        assertNull(info.effective_plan_name)
        assertNull(SubscriptionResponse().to_subscription_info())
    }

    @Test
    fun mapper_drops_blank_values() {
        val response = json.decodeFromString(
            SubscriptionResponse.serializer(),
            """{"plan":{"id":" ","code":" star ","name":""},"status":"  ","currency":" "}""",
        )
        val info = response.to_subscription_info()!!
        assertEquals("star", info.plan?.code)
        assertNull(info.plan?.id)
        assertNull(info.plan?.name)
        assertNull(info.status)
        assertNull(info.currency)
    }

    @Test
    fun status_kind_covers_every_state() {
        assertEquals(subscription_status_kind.free, subscription_status_kind_of("active", false, false, false, false))
        assertEquals(subscription_status_kind.lifetime, subscription_status_kind_of("expired", true, true, false, false))
        assertEquals(subscription_status_kind.expired, subscription_status_kind_of("canceled", true, false, false, false))
        assertEquals(subscription_status_kind.expired, subscription_status_kind_of(" EXPIRED ", true, false, true, false))
        assertEquals(subscription_status_kind.past_due, subscription_status_kind_of("active", true, false, false, true))
        assertEquals(subscription_status_kind.past_due, subscription_status_kind_of("past_due", true, false, false, false))
        assertEquals(subscription_status_kind.canceling, subscription_status_kind_of("active", true, false, true, false))
        assertEquals(subscription_status_kind.trialing, subscription_status_kind_of("trialing", true, false, false, false))
        assertEquals(subscription_status_kind.active, subscription_status_kind_of(null, true, false, false, false))
        assertEquals(subscription_status_kind.active, subscription_status_kind_of("none", true, false, false, false))
    }

    @Test
    fun payment_source_routes_by_provider() {
        assertEquals(subscription_payment_source.none, subscription_payment_source_of("google_play", false, false))
        assertEquals(subscription_payment_source.play, subscription_payment_source_of(" Google_Play ", false, true))
        assertEquals(subscription_payment_source.crypto, subscription_payment_source_of("stripe_crypto", true, true))
        assertEquals(subscription_payment_source.crypto, subscription_payment_source_of("crypto_native", null, true))
        assertEquals(subscription_payment_source.card, subscription_payment_source_of("stripe", null, true))
        assertEquals(subscription_payment_source.card, subscription_payment_source_of(null, true, true))
        assertEquals(subscription_payment_source.none, subscription_payment_source_of(null, false, true))
        assertEquals(subscription_payment_source.none, subscription_payment_source_of("unknown", null, true))
    }

    @Test
    fun top_tier_plan_detection() {
        assertTrue(is_top_tier_plan("supernova"))
        assertTrue(is_top_tier_plan(" SuperNova "))
        assertTrue(is_top_tier_plan("family_full"))
        assertFalse(is_top_tier_plan("nova"))
        assertFalse(is_top_tier_plan("star"))
        assertFalse(is_top_tier_plan("free"))
        assertFalse(is_top_tier_plan(""))
        assertFalse(is_top_tier_plan(null))
    }

    @Test
    fun transient_failures_are_retryable_and_client_errors_are_not() {
        assertTrue(is_transient_billing_failure(ApiError.NetworkError))
        assertTrue(is_transient_billing_failure(ApiError.ServerError(503)))
        assertTrue(is_transient_billing_failure(java.net.SocketTimeoutException("timeout")))
        assertTrue(is_transient_billing_failure(RuntimeException("wrap", java.io.IOException("reset"))))
        assertFalse(is_transient_billing_failure(ApiError.ServerError(200)))
        assertFalse(is_transient_billing_failure(ApiError.NotFoundError))
        assertFalse(is_transient_billing_failure(IllegalStateException("bad")))
    }
}
