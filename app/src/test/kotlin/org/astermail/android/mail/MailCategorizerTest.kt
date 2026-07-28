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
import org.junit.Assert.assertEquals
import org.junit.Test

class MailCategorizerTest {

    private fun envelope(
        from_email: String,
        from_name: String = "",
        subject: String = "",
        raw_headers: List<Pair<String, String>> = emptyList(),
        list_unsubscribe: String? = null,
        sender_verification: String? = null,
    ): DecryptedEnvelope = DecryptedEnvelope(
        subject = subject,
        body_text = "",
        body_html = null,
        from_name = from_name,
        from_email = from_email,
        to = emptyList(),
        cc = emptyList(),
        sent_at = "2026-06-06T00:00:00Z",
        raw_headers = raw_headers,
        list_unsubscribe = list_unsubscribe,
        sender_verification = sender_verification,
    )

    @Test
    fun pinned_category_wins_over_signals() {
        val env = envelope(from_email = "noreply@linkedin.com", from_name = "LinkedIn")
        val meta = MailItemMetadata(category = "primary", category_pinned = true)
        assertEquals("primary", classify(env, meta))
    }

    @Test
    fun known_social_domain_is_social() {
        val env = envelope(
            from_email = "notifications@linkedin.com",
            from_name = "LinkedIn",
            subject = "You have 3 new connections",
        )
        assertEquals("social", classify(env, null))
    }

    @Test
    fun mailing_list_headers_are_forums() {
        val env = envelope(
            from_email = "list@example.org",
            from_name = "Dev List",
            subject = "[dev] weekly digest",
            raw_headers = listOf("List-Id" to "<dev.example.org>"),
        )
        assertEquals("forums", classify(env, null))
    }

    @Test
    fun transactional_receipts_are_updates() {
        val env = envelope(
            from_email = "receipts@acme.com",
            from_name = "Acme Store",
            subject = "Your order #12345 has shipped",
        )
        assertEquals("updates", classify(env, null))
    }

    @Test
    fun bulk_marketing_is_promotions() {
        val env = envelope(
            from_email = "marketing@acme.com",
            from_name = "Acme Deals",
            subject = "50% off everything this weekend",
            list_unsubscribe = "<https://acme.com/unsub>",
        )
        assertEquals("promotions", classify(env, null))
    }

    @Test
    fun plain_human_sender_is_primary() {
        val env = envelope(
            from_email = "jane@gmail.com",
            from_name = "Jane Doe",
            subject = "Lunch tomorrow?",
        )
        assertEquals("primary", classify(env, null))
    }

    @Test
    fun generic_bulk_without_signals_is_promotions() {
        val env = envelope(
            from_email = "hello@somesite.com",
            from_name = "Some Newsletter",
            subject = "This week at SomeSite",
            list_unsubscribe = "<mailto:unsub@somesite.com>",
        )
        assertEquals("promotions", classify(env, null))
    }

    @Test
    fun internal_aster_mail_stays_primary() {
        val env = envelope(
            from_email = "welcome@astermail.org",
            from_name = "Aster Mail",
            subject = "Welcome to Aster - 50% off Nova this week",
            list_unsubscribe = "<https://astermail.org/unsub>",
        )
        assertEquals("primary", classify(env, null))
    }

    @Test
    fun personal_mail_with_transactional_subject_stays_primary() {
        val env = envelope(
            from_email = "bob@gmail.com",
            from_name = "Bob",
            subject = "Your order for the concert tickets this weekend",
        )
        assertEquals("primary", classify(env, null))
    }

    @Test
    fun personal_mail_with_promo_words_stays_primary() {
        val env = envelope(
            from_email = "mom@gmail.com",
            from_name = "Mom",
            subject = "Huge sale at the mall, want to go?",
        )
        assertEquals("primary", classify(env, null))
    }

    @Test
    fun brand_through_marketing_esp_via_dkim_is_promotions() {
        val env = envelope(
            from_email = "hello@coolbrand.com",
            from_name = "Cool Brand",
            subject = "This week at Cool Brand",
            raw_headers = listOf("DKIM-Signature" to "v=1; a=rsa-sha256; d=mcsv.net; s=k1"),
        )
        assertEquals("promotions", classify(env, null))
    }

    @Test
    fun transactional_service_notification_is_updates() {
        val env = envelope(
            from_email = "no-reply@ups.com",
            from_name = "UPS",
            subject = "Your package was delivered",
        )
        assertEquals("updates", classify(env, null))
    }

    @Test
    fun receipt_from_service_domain_no_bulk_markers_is_updates() {
        val env = envelope(
            from_email = "auto-confirm@amazon.com",
            from_name = "Amazon",
            subject = "Your order #112-9 has shipped",
        )
        assertEquals("updates", classify(env, null))
    }

    @Test
    fun personal_note_from_service_domain_stays_primary() {
        val env = envelope(
            from_email = "jane.doe@github.com",
            from_name = "A Recruiter",
            subject = "Coffee next week?",
        )
        assertEquals("primary", classify(env, null))
    }

    @Test
    fun spoofed_aster_sender_does_not_ride_system_rule() {
        val env = envelope(
            from_email = "no-reply@astermail.org",
            from_name = "Aster",
            subject = "50% off Nova - act now",
            list_unsubscribe = "<https://evil.example/unsub>",
            sender_verification = "invalid",
        )
        assertEquals("promotions", classify(env, null))
    }

    @Test
    fun dkim_verified_aster_system_mail_stays_primary() {
        val env = envelope(
            from_email = "welcome@astermail.org",
            from_name = "Aster",
            subject = "Welcome to Aster",
            sender_verification = "verified",
        )
        assertEquals("primary", classify(env, null))
    }

    @Test
    fun category_for_tab_folds_forums_into_updates() {
        assertEquals("updates", category_for_tab("forums"))
        assertEquals("primary", category_for_tab("important"))
        assertEquals("primary", category_for_tab(null))
        assertEquals("social", category_for_tab("social"))
    }

    @Test
    fun bank_statement_is_finance() {
        val env = envelope(
            from_email = "alerts@chase.com",
            from_name = "Chase",
            subject = "Your statement is ready",
        )
        assertEquals("finance", classify(env, null))
    }

    @Test
    fun airline_itinerary_is_travel() {
        val env = envelope(
            from_email = "confirmation@united.com",
            from_name = "United Airlines",
            subject = "Your itinerary for flight UA123",
        )
        assertEquals("travel", classify(env, null))
    }

    @Test
    fun cart_reminder_from_retailer_is_shopping() {
        val env = envelope(
            from_email = "hello@etsy.com",
            from_name = "Etsy",
            subject = "Price drop on an item in your wishlist",
        )
        assertEquals("shopping", classify(env, null))
    }

    @Test
    fun shipping_update_from_retailer_is_updates() {
        val env = envelope(
            from_email = "orders@etsy.com",
            from_name = "Etsy",
            subject = "Your order has shipped",
        )
        assertEquals("updates", classify(env, null))
    }

    @Test
    fun custom_domain_rule_beats_builtin_signals() {
        val rules = listOf(
            CustomCategoryRule(
                id = "custom:news",
                name = "News",
                icon = "globe",
                match_domains = listOf("linkedin.com"),
                match_keywords = emptyList(),
                enabled = true,
            ),
        )
        val env = envelope(
            from_email = "notifications@linkedin.com",
            from_name = "LinkedIn",
            subject = "You have 3 new connections",
        )
        assertEquals("custom:news", classify(env, null, null, rules))
    }

    @Test
    fun custom_keyword_rule_matches_subject() {
        val rules = listOf(
            CustomCategoryRule(
                id = "custom:digest",
                name = "Digest",
                icon = "folder",
                match_domains = emptyList(),
                match_keywords = listOf("digest"),
                enabled = true,
            ),
        )
        val env = envelope(
            from_email = "hello@somesite.com",
            from_name = "Some Newsletter",
            subject = "Weekly Digest for you",
            list_unsubscribe = "<https://somesite.com/unsub>",
        )
        assertEquals("custom:digest", classify(env, null, null, rules))
    }

    @Test
    fun disabled_custom_rule_is_ignored() {
        val rules = listOf(
            CustomCategoryRule(
                id = "custom:off",
                name = "Off",
                icon = "folder",
                match_domains = listOf("linkedin.com"),
                match_keywords = emptyList(),
                enabled = false,
            ),
        )
        val env = envelope(
            from_email = "notifications@linkedin.com",
            from_name = "LinkedIn",
            subject = "You have 3 new connections",
        )
        assertEquals("social", classify(env, null, null, rules))
    }

    @Test
    fun category_for_tab_keeps_enabled_extra_tabs() {
        val tabs = listOf("primary", "promotions", "social", "updates", "forums", "finance")
        assertEquals("forums", category_for_tab("forums", tabs))
        assertEquals("finance", category_for_tab("finance", tabs))
        assertEquals("updates", category_for_tab("travel", tabs))
        assertEquals("primary", category_for_tab("custom:news", tabs))
    }
}
