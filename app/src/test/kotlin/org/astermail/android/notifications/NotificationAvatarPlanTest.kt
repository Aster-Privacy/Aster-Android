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

package org.astermail.android.notifications

import okio.Buffer
import org.astermail.android.security.SenderAuthState
import org.astermail.android.security.SenderTrustContext
import org.astermail.android.security.SenderTrustMessage
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class NotificationAvatarPlanTest {

    private val on = NotificationAvatarSettings(show_pictures = true, low_network = false)
    private val trust_context = SenderTrustContext(own_addresses = listOf("me@astermail.org"))

    private fun facts(
        email: String = "news@bank.example",
        name: String = "Bank",
        settings: NotificationAvatarSettings = on,
        content_hidden: Boolean = false,
        undecryptable: Boolean = false,
        trust_known: Boolean = true,
        identity_marks: Boolean = true,
        has_warning: Boolean = false,
        auth_state: SenderAuthState? = SenderAuthState.authenticated,
        system_sender: Boolean = false,
    ) = NotificationAvatarFacts(
        content_hidden = content_hidden,
        settings = settings,
        sender_email = email,
        sender_name = name,
        undecryptable = undecryptable,
        trust_known = trust_known,
        identity_marks = identity_marks,
        has_warning = has_warning,
        auth_state = auth_state,
        system_sender = system_sender,
    )

    private fun authenticated_external(email: String, name: String) = SenderTrustMessage(
        sender_name = name,
        sender_email = email,
        is_external = true,
        spf_result = "pass",
        dkim_result = "pass",
        dmarc_result = "pass",
        sender_aligned = true,
    )

    @Test
    fun hidden_content_keeps_the_app_icon() {
        assertEquals(NotificationAvatarPlan.AppIcon, plan_notification_avatar(facts(content_hidden = true)))
    }

    @Test
    fun profile_pictures_off_keeps_the_app_icon() {
        val off = NotificationAvatarSettings(show_pictures = false, low_network = false)
        assertEquals(NotificationAvatarPlan.AppIcon, plan_notification_avatar(facts(settings = off)))
    }

    @Test
    fun authenticated_brand_sender_gets_its_logo() {
        val plan = plan_notification_avatar(facts()) as NotificationAvatarPlan.Picture
        assertEquals("bank.example", plan.root_domain)
        assertTrue(plan.allow_network)
        assertFalse(plan.aster_domain)
    }

    @Test
    fun subdomain_resolves_to_the_root_domain() {
        val plan = plan_notification_avatar(facts(email = "alerts@mail.shop.co.uk")) as NotificationAvatarPlan.Picture
        assertEquals("shop.co.uk", plan.root_domain)
    }

    @Test
    fun undecryptable_mail_gets_initials() {
        assertTrue(plan_notification_avatar(facts(undecryptable = true)) is NotificationAvatarPlan.Initials)
    }

    @Test
    fun warnings_and_missing_trust_get_initials() {
        assertTrue(plan_notification_avatar(facts(has_warning = true)) is NotificationAvatarPlan.Initials)
        assertTrue(plan_notification_avatar(facts(identity_marks = false)) is NotificationAvatarPlan.Initials)
        assertTrue(plan_notification_avatar(facts(trust_known = false)) is NotificationAvatarPlan.Initials)
    }

    @Test
    fun only_authenticated_or_internal_senders_get_a_picture() {
        for (state in listOf(SenderAuthState.unknown, SenderAuthState.unverified, SenderAuthState.failed, null)) {
            assertTrue(state.toString(), plan_notification_avatar(facts(auth_state = state)) is NotificationAvatarPlan.Initials)
        }
        assertTrue(
            plan_notification_avatar(facts(auth_state = SenderAuthState.internal)) is NotificationAvatarPlan.Picture,
        )
    }

    @Test
    fun spoofed_brand_sender_never_gets_the_logo() {
        val spoofed = SenderTrustMessage(
            sender_name = "Bank",
            sender_email = "security@bank.example",
            is_external = true,
            spf_result = "fail",
            dkim_result = "fail",
            dmarc_result = "fail",
            sender_aligned = false,
        )
        val plan = plan_notification_avatar(
            notification_avatar_facts(
                message = spoofed,
                shown_email = "security@bank.example",
                shown_name = "Bank",
                undecryptable = false,
                system_sender = false,
                settings = on,
                content_hidden = false,
                trust_context = trust_context,
            ),
        )
        assertTrue(plan is NotificationAvatarPlan.Initials)
    }

    @Test
    fun unauthenticated_sender_with_no_results_gets_initials() {
        val unknown = SenderTrustMessage(sender_name = "Shop", sender_email = "hi@shop.example", is_external = true)
        val plan = plan_notification_avatar(
            notification_avatar_facts(unknown, "hi@shop.example", "Shop", false, false, on, false, trust_context),
        )
        assertTrue(plan is NotificationAvatarPlan.Initials)
    }

    @Test
    fun authenticated_sender_through_real_trust_gets_a_picture() {
        val plan = plan_notification_avatar(
            notification_avatar_facts(
                authenticated_external("hi@shop.example", "Shop"),
                "hi@shop.example",
                "Shop",
                false,
                false,
                on,
                false,
                trust_context,
            ),
        )
        assertEquals("shop.example", (plan as NotificationAvatarPlan.Picture).root_domain)
    }

    @Test
    fun missing_trust_message_gets_initials() {
        val plan = plan_notification_avatar(
            notification_avatar_facts(null, "hi@shop.example", "Shop", false, false, on, false, trust_context),
        )
        assertTrue(plan is NotificationAvatarPlan.Initials)
    }

    @Test
    fun aster_system_sender_gets_the_system_logo_only_when_the_server_marked_it() {
        val system = facts(email = "noreply@astermail.org", name = "Aster", auth_state = SenderAuthState.internal, system_sender = true)
        assertTrue(plan_notification_avatar(system) is NotificationAvatarPlan.SystemLogo)
        val look_alike = system.copy(system_sender = false)
        val plan = plan_notification_avatar(look_alike) as NotificationAvatarPlan.Picture
        assertNull(plan.root_domain)
        assertFalse(plan.allow_network)
    }

    @Test
    fun aster_personal_sender_never_fetches_a_favicon() {
        val plan = plan_notification_avatar(
            facts(email = "ana@astermail.org", name = "Ana", auth_state = SenderAuthState.internal),
        ) as NotificationAvatarPlan.Picture
        assertTrue(plan.aster_domain)
        assertNull(plan.root_domain)
        assertFalse(plan.allow_network)
        assertTrue(plan.allow_profile_picture)
    }

    @Test
    fun low_network_never_fetches() {
        val low = NotificationAvatarSettings(show_pictures = true, low_network = true)
        val plan = plan_notification_avatar(facts(settings = low)) as NotificationAvatarPlan.Picture
        assertEquals("bank.example", plan.root_domain)
        assertFalse(plan.allow_network)
        assertFalse(plan.allow_profile_picture)
    }

    @Test
    fun malformed_or_missing_addresses_get_initials_or_no_fetch() {
        assertTrue(plan_notification_avatar(facts(email = "", name = "Ana")) is NotificationAvatarPlan.Initials)
        assertTrue(plan_notification_avatar(facts(email = "not an address", name = "Ana")) is NotificationAvatarPlan.Initials)
        val bad_domain = plan_notification_avatar(facts(email = "a@exa mple..com")) as NotificationAvatarPlan.Picture
        assertNull(bad_domain.root_domain)
        assertFalse(bad_domain.allow_network)
        val path_domain = plan_notification_avatar(facts(email = "a@evil.example/../x")) as NotificationAvatarPlan.Picture
        assertNull(path_domain.root_domain)
    }

    @Test
    fun push_plan_is_never_more_than_initials() {
        assertTrue(push_avatar_plan("news@bank.example", "Bank", on) is NotificationAvatarPlan.Initials)
        val off = NotificationAvatarSettings(show_pictures = false, low_network = false)
        assertEquals(NotificationAvatarPlan.AppIcon, push_avatar_plan("news@bank.example", "Bank", off))
    }

    @Test
    fun initials_handle_emoji_rtl_and_blank_names() {
        assertEquals("A", notification_initial("ana", "ana@x.example"))
        assertEquals("😀", notification_initial("😀 Smile", ""))
        assertEquals("👩‍💻", notification_initial("👩‍💻 Dev", ""))
        assertEquals("ש", notification_initial("שרה", ""))
        assertEquals("B", notification_initial("  ", "bob@x.example"))
        assertEquals("?", notification_initial("", ""))
        assertEquals("?", notification_initial("\u0007bell", ""))
        val long = "W".repeat(500)
        assertEquals("W", notification_initial(long, ""))
    }

    @Test
    fun initials_key_matches_the_inbox_avatar_key() {
        assertEquals("ana@x.example", notification_initials_plan("Ana", " ana@x.example ").key)
        assertEquals("Ana", notification_initials_plan("Ana", "").key)
        assertEquals("?", notification_initials_plan("", "").key)
    }

    @Test
    fun settings_fall_back_safely() {
        assertEquals(
            NotificationAvatarSettings(show_pictures = false, low_network = true),
            merge_avatar_settings(null, null, null),
        )
        assertEquals(
            NotificationAvatarSettings(show_pictures = true, low_network = false),
            merge_avatar_settings(null, true, false),
        )
        assertEquals(
            NotificationAvatarSettings(show_pictures = false, low_network = true),
            merge_avatar_settings(false to true, true, false),
        )
    }

    @Test
    fun capped_reader_rejects_oversize_bodies() {
        val small = ByteArray(1024) { 7 }
        assertArrayEquals(small, read_capped_bytes(Buffer().write(small), NOTIFICATION_LOGO_MAX_BYTES))
        val exact = ByteArray(NOTIFICATION_LOGO_MAX_BYTES)
        assertEquals(NOTIFICATION_LOGO_MAX_BYTES, read_capped_bytes(Buffer().write(exact), NOTIFICATION_LOGO_MAX_BYTES)?.size)
        val big = ByteArray(NOTIFICATION_LOGO_MAX_BYTES + 1)
        assertNull(read_capped_bytes(Buffer().write(big), NOTIFICATION_LOGO_MAX_BYTES))
        assertNull(read_capped_bytes(Buffer(), NOTIFICATION_LOGO_MAX_BYTES))
    }

    @Test
    fun only_raster_image_types_are_accepted() {
        assertTrue(is_acceptable_logo_mime("image/png"))
        assertTrue(is_acceptable_logo_mime("image/x-icon"))
        assertTrue(is_acceptable_logo_mime("IMAGE/WEBP; charset=binary"))
        assertTrue(is_acceptable_logo_mime(null))
        assertFalse(is_acceptable_logo_mime("image/svg+xml"))
        assertFalse(is_acceptable_logo_mime("text/html"))
        assertFalse(is_acceptable_logo_mime("application/json"))
        assertFalse(is_acceptable_logo_mime("application/xml"))
    }

    @Test
    fun decode_is_sampled_toward_the_target_and_bounded() {
        assertEquals(1, logo_sample_size(160, 160, 160))
        assertEquals(2, logo_sample_size(400, 400, 160))
        assertEquals(16, logo_sample_size(4096, 4096, 160))
        assertEquals(1, logo_sample_size(0, 10, 160))
        assertTrue(is_acceptable_logo_bounds(16, 16))
        assertFalse(is_acceptable_logo_bounds(0, 16))
        assertFalse(is_acceptable_logo_bounds(-1, -1))
        assertFalse(is_acceptable_logo_bounds(NOTIFICATION_LOGO_MAX_SOURCE_PX + 1, 16))
    }

    @Test
    fun budget_caps_each_fetch_and_the_whole_run() {
        val budget = NotificationAvatarBudget(total_ms = 3_000L, per_fetch_ms = 2_500L)
        assertEquals(2_500L, budget.allowance())
        budget.spend(2_500L)
        assertEquals(500L, budget.allowance())
        budget.spend(800L)
        assertEquals(0L, budget.allowance())
        budget.spend(-5L)
        assertEquals(0L, budget.allowance())
    }

    @Test
    fun lru_evicts_the_least_recently_used_entry() {
        val lru = NotificationAvatarLru<String>(max_entries = 2)
        lru.put("a", "1")
        lru.put("b", "2")
        assertEquals("1", lru.get("a"))
        lru.put("c", "3")
        assertNull(lru.get("b"))
        assertEquals("1", lru.get("a"))
        assertEquals(2, lru.size())
    }

    @Test
    fun a_sender_avatar_always_carries_a_redacted_lock_screen_version() {
        assertTrue(MailPollingWorker.message_lock_screen_policy(private_mode = false, has_sender_avatar = true).redacted_public_version)
        assertFalse(MailPollingWorker.message_lock_screen_policy(private_mode = false).redacted_public_version)
        assertTrue(MailPollingWorker.message_lock_screen_policy(private_mode = true).redacted_public_version)
    }
}
