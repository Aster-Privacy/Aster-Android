//
// Aster Communications Inc.
//
// Copyright (c) 2026 Aster Communications Inc.
//
// This file is part of this project.
//
// This program is free software: you can redistribute it and/or modify
// it under the terms of the AGPLv3 as published by
// the Free Software Foundation, either version 3 of the License, or
// (at your option) any later version.
//
// This program is distributed in the hope that it will be useful,
// but WITHOUT ANY WARRANTY; without even the implied warranty of
// MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
// AGPLv3 for more details.
//
// You should have received a copy of the AGPLv3
// along with this program. If not, see <https://www.gnu.org/licenses/>.
//

package org.astermail.android.notifications

import java.security.SecureRandom
import java.util.UUID
import org.astermail.android.crypto.DeviceEnvelope
import org.astermail.android.crypto.guard.GuardContextSeal
import org.astermail.android.crypto.ratchet.RatchetCrypto
import org.astermail.android.guard.GUARD_PROMPT_DEFAULT_TIMEOUT_MS
import org.astermail.android.guard.GUARD_PROMPT_MIN_TIMEOUT_MS
import org.astermail.android.guard.guard_box_keys
import org.astermail.android.guard.guard_dedupe_key
import org.astermail.android.guard.guard_event
import org.astermail.android.guard.guard_launch_target
import org.astermail.android.guard.guard_launch_route
import org.astermail.android.guard.guard_launch_route_for
import org.astermail.android.guard.guard_launch_target_for
import org.astermail.android.guard.guard_local_approvals
import org.astermail.android.guard.guard_notification_spec_for
import org.astermail.android.guard.guard_open_target
import org.astermail.android.guard.guard_prompt_lookup
import org.astermail.android.guard.guard_prompt_push
import org.astermail.android.guard.guard_prompt_should_post
import org.astermail.android.guard.guard_prompt_timeout_ms
import org.astermail.android.guard.guard_push
import org.astermail.android.guard.guard_should_show
import org.astermail.android.guard.guard_uuid_field
import org.astermail.android.guard.handle_guard_push
import org.astermail.android.guard.needs_id
import org.astermail.android.guard.open_guard_context
import org.astermail.android.guard.parse_guard_push
import org.bouncycastle.crypto.params.X25519PrivateKeyParameters
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class GuardPushTest {

    private val account = "7f3c2a10-1111-4222-8333-944455556666"
    private val other_account = "1a2b3c4d-1111-4222-8333-944455556666"
    private val r1 = "0d1e2f30-aaaa-4bbb-8ccc-000000000001"
    private val r7 = "0d1e2f30-aaaa-4bbb-8ccc-000000000007"
    private val x1 = "0d1e2f30-aaaa-4bbb-8ccc-0000000000a1"

    private fun req(n: Int): String = "0d1e2f30-aaaa-4bbb-8ccc-%012d".format(n)

    private fun payload(
        event: String = "sign_in_request",
        id: String? = r1,
        account_id: String? = account,
        type: String = "guard",
    ): JSONObject = JSONObject().apply {
        put("type", type)
        put("event", event)
        if (id != null) put("id", id)
        if (account_id != null) put("account_id", account_id)
    }

    private class fake_dedupe {
        var entries: Map<String, Long> = emptyMap()
        val claimed = mutableListOf<String>()
        fun claim(key: String): Boolean {
            val now = 1_000L
            if (NotificationDedupe.is_seen(entries, key, now)) return false
            entries = NotificationDedupe.record_seen(entries, key, now)
            claimed += key
            return true
        }
    }

    @Test
    fun parses_valid_sign_in_request() {
        val push = parse_guard_push(payload())
        assertEquals(guard_push(guard_event.sign_in_request, r1, account), push)
    }

    @Test
    fun rejects_camel_case_event() {
        assertNull(parse_guard_push(payload(event = "SignInRequest")))
    }

    @Test
    fun rejects_unknown_event() {
        assertNull(parse_guard_push(payload(event = "approve_everything")))
    }

    @Test
    fun rejects_missing_account() {
        assertNull(parse_guard_push(payload(account_id = null)))
    }

    @Test
    fun rejects_wrong_type() {
        assertNull(parse_guard_push(payload(type = "login_alert")))
    }

    @Test
    fun rejects_missing_id_for_request() {
        assertNull(parse_guard_push(payload(id = null)))
    }

    @Test
    fun accepts_missing_id_for_panic() {
        val push = parse_guard_push(payload(event = "panic", id = null))
        assertEquals(guard_push(guard_event.panic, "", account), push)
    }

    @Test
    fun rejects_unsafe_or_long_fields() {
        assertNull(parse_guard_push(payload(id = "$r1/../x")))
        assertNull(parse_guard_push(payload(id = "a".repeat(65))))
        assertNull(parse_guard_push(payload(account_id = "<script>")))
    }

    @Test
    fun unknown_account_is_ignored_without_claim() {
        val dedupe = fake_dedupe()
        val enqueued = mutableListOf<guard_push>()
        val result = handle_guard_push(
            payload(),
            known_accounts = { listOf("00000000-0000-4000-8000-000000000000") },
            claim = dedupe::claim,
            enqueue = { enqueued += it },
        )
        assertEquals(PushResult.Ignore, result)
        assertTrue(dedupe.claimed.isEmpty())
        assertTrue(enqueued.isEmpty())
    }

    @Test
    fun account_match_ignores_case() {
        val enqueued = mutableListOf<guard_push>()
        val result = handle_guard_push(
            payload(),
            known_accounts = { listOf(account.uppercase()) },
            claim = { true },
            enqueue = { enqueued += it },
        )
        assertEquals(PushResult.Shown, result)
        assertEquals(1, enqueued.size)
    }

    @Test
    fun account_lookup_failure_is_ignored() {
        val result = handle_guard_push(
            payload(),
            known_accounts = { error("no accounts") },
            claim = { true },
            enqueue = { },
        )
        assertEquals(PushResult.Ignore, result)
    }

    @Test
    fun duplicate_push_is_claimed_once() {
        val dedupe = fake_dedupe()
        val enqueued = mutableListOf<guard_push>()
        val first = handle_guard_push(payload(), { listOf(account) }, dedupe::claim, { enqueued += it })
        val second = handle_guard_push(payload(), { listOf(account) }, dedupe::claim, { enqueued += it })
        assertEquals(PushResult.Shown, first)
        assertEquals(PushResult.Ignore, second)
        assertEquals(listOf("guard:sign_in_request:$r1"), dedupe.claimed)
        assertEquals(1, enqueued.size)
    }

    @Test
    fun dedupe_key_format() {
        assertEquals(
            "guard:change_held:c9",
            guard_dedupe_key(guard_push(guard_event.change_held, "c9", account)),
        )
    }

    @Test
    fun sign_in_request_spec_has_review_and_private_details() {
        val spec = guard_notification_spec_for(guard_event.sign_in_request)
        assertTrue(spec.review_action)
        assertTrue(spec.private_details)
        assertEquals(guard_open_target.request, spec.target)
    }

    @Test
    fun only_sign_in_and_rescue_offer_review() {
        val with_review = guard_event.entries.filter { guard_notification_spec_for(it).review_action }
        assertEquals(listOf(guard_event.sign_in_request, guard_event.rescue_started), with_review)
    }

    @Test
    fun change_events_open_changes() {
        listOf(guard_event.change_held, guard_event.change_applied, guard_event.change_cancelled).forEach {
            assertEquals(guard_open_target.changes, guard_notification_spec_for(it).target)
        }
    }

    @Test
    fun notification_tags_are_unique_per_request_event_and_account() {
        val tags = (0 until 500).flatMap { n ->
            listOf(account, other_account).flatMap { acct ->
                listOf(guard_event.sign_in_request, guard_event.sign_in_approved).map { event ->
                    LoginAlertNotifier.guard_notification_tag(guard_push(event, req(n), acct))
                }
            }
        }
        assertEquals(tags.size, tags.toSet().size)
        assertTrue(tags.all { it.startsWith("guard:") })
    }

    @Test
    fun notification_tag_ignores_case() {
        assertEquals(
            LoginAlertNotifier.guard_notification_tag(guard_push(guard_event.sign_in_request, r1, account)),
            LoginAlertNotifier.guard_notification_tag(guard_push(guard_event.sign_in_request, r1.uppercase(), account.uppercase())),
        )
    }

    @Test
    fun request_codes_differ_between_slots_and_requests() {
        val a = guard_push(guard_event.sign_in_request, r1, account)
        val b = guard_push(guard_event.sign_in_request, r7, account)
        val codes = listOf(1, 2, 3).flatMap { slot ->
            listOf(LoginAlertNotifier.guard_request_code(a, slot), LoginAlertNotifier.guard_request_code(b, slot))
        }
        assertEquals(codes.size, codes.toSet().size)
    }

    @Test
    fun uuid_fields_are_canonical() {
        assertEquals(r1, guard_uuid_field(r1))
        assertEquals(r1, guard_uuid_field(r1.uppercase()))
        assertNull(guard_uuid_field(null))
        assertNull(guard_uuid_field(""))
        assertNull(guard_uuid_field("r1"))
        assertNull(guard_uuid_field("1-1-1-1-1"))
        assertNull(guard_uuid_field(" $r1"))
        assertNull(guard_uuid_field("$r1/x"))
        assertNull(guard_uuid_field("{$r1}"))
    }

    @Test
    fun parse_rejects_non_uuid_ids() {
        assertNull(parse_guard_push(payload(id = "r1")))
        assertNull(parse_guard_push(payload(account_id = "acct")))
    }

    @Test
    fun launch_route_follows_the_push_account() {
        val has = { id: String -> id == other_account }
        assertEquals(guard_launch_route.current, guard_launch_route_for(account, account, has))
        assertEquals(guard_launch_route.current, guard_launch_route_for(account.uppercase(), account, has))
        assertEquals(guard_launch_route.switch, guard_launch_route_for(other_account, account, has))
        assertEquals(guard_launch_route.switch, guard_launch_route_for(other_account, null, has))
        assertEquals(guard_launch_route.ignore, guard_launch_route_for(req(5), account, has))
        assertEquals(guard_launch_route.ignore, guard_launch_route_for(null, account, has))
        assertEquals(guard_launch_route.ignore, guard_launch_route_for(other_account, account) { error("boom") })
    }

    @Test
    fun prompt_posts_only_for_current_account_and_live_request() {
        assertTrue(guard_prompt_should_post(account, account, guard_prompt_lookup.found))
        assertTrue(guard_prompt_should_post(account, account.uppercase(), guard_prompt_lookup.unknown))
        assertFalse(guard_prompt_should_post(account, account, guard_prompt_lookup.absent))
        assertFalse(guard_prompt_should_post(account, other_account, guard_prompt_lookup.found))
        assertFalse(guard_prompt_should_post(account, null, guard_prompt_lookup.found))
    }

    @Test
    fun prompt_timeout_tracks_expiry() {
        val now = java.time.Instant.parse("2026-11-16T10:00:00Z").toEpochMilli()
        assertEquals(GUARD_PROMPT_DEFAULT_TIMEOUT_MS, guard_prompt_timeout_ms(null, now))
        assertEquals(GUARD_PROMPT_DEFAULT_TIMEOUT_MS, guard_prompt_timeout_ms("", now))
        assertEquals(GUARD_PROMPT_DEFAULT_TIMEOUT_MS, guard_prompt_timeout_ms("garbage", now))
        assertEquals(120_000L, guard_prompt_timeout_ms("2026-11-16T10:02:00Z", now))
        assertEquals(GUARD_PROMPT_DEFAULT_TIMEOUT_MS, guard_prompt_timeout_ms("2026-11-16T11:00:00Z", now))
        assertEquals(GUARD_PROMPT_MIN_TIMEOUT_MS, guard_prompt_timeout_ms("2026-11-16T09:00:00Z", now))
    }

    @Test
    fun clearing_an_account_forgets_its_local_approvals() {
        guard_local_approvals.clear()
        guard_local_approvals.record(r1, account)
        guard_local_approvals.record(r7, other_account)
        guard_local_approvals.clear_account(account.uppercase())
        assertFalse(guard_local_approvals.approved_here(r1, account))
        assertTrue(guard_local_approvals.approved_here(r7, other_account))
        guard_local_approvals.clear()
    }

    @Test
    fun every_shown_event_has_text() {
        guard_event.entries.forEach {
            if (guard_notification_spec_for(it).shows) {
                assertNotNull(LoginAlertNotifier.guard_title_res(it))
                assertNotNull(LoginAlertNotifier.guard_body_res(it))
            } else {
                assertNull(LoginAlertNotifier.guard_title_res(it))
                assertNull(LoginAlertNotifier.guard_body_res(it))
            }
        }
    }

    private val new_events = listOf(
        guard_event.sign_in_approved,
        guard_event.sign_in_denied,
        guard_event.sign_in_expired,
        guard_event.device_added,
        guard_event.device_removed,
        guard_event.rescue_completed,
        guard_event.guard_disabled,
    )

    @Test
    fun parses_each_new_event() {
        new_events.forEach {
            assertEquals(guard_push(it, x1, account), parse_guard_push(payload(event = it.name, id = x1)))
        }
    }

    @Test
    fun new_events_need_an_id() {
        new_events.forEach {
            assertTrue(it.needs_id())
            assertNull(parse_guard_push(payload(event = it.name, id = null)))
            assertNull(parse_guard_push(payload(event = it.name, id = "$x1/../y")))
            assertNull(parse_guard_push(payload(event = it.name, id = "a".repeat(65))))
        }
    }

    @Test
    fun ids_optional_only_for_panic_enabled_and_paused() {
        val optional = guard_event.entries.filterNot { it.needs_id() }
        assertEquals(listOf(guard_event.panic, guard_event.guard_enabled, guard_event.prompts_paused), optional)
    }

    @Test
    fun denied_and_expired_only_cancel_the_prompt() {
        listOf(guard_event.sign_in_denied, guard_event.sign_in_expired).forEach {
            val spec = guard_notification_spec_for(it)
            assertTrue(spec.cancels_prompt)
            assertFalse(spec.shows)
            assertFalse(spec.review_action)
            assertFalse(spec.panic_action)
            assertFalse(guard_should_show(guard_push(it, r1, account)) { _, _ -> false })
        }
    }

    @Test
    fun approved_cancels_prompt_and_offers_panic() {
        val spec = guard_notification_spec_for(guard_event.sign_in_approved)
        assertTrue(spec.cancels_prompt)
        assertTrue(spec.shows)
        assertTrue(spec.panic_action)
        assertFalse(spec.review_action)
        assertFalse(spec.private_details)
        assertEquals(guard_open_target.guard_settings, spec.target)
    }

    @Test
    fun only_sign_in_outcomes_cancel_the_prompt() {
        val cancelling = guard_event.entries.filter { guard_notification_spec_for(it).cancels_prompt }
        assertEquals(listOf(guard_event.sign_in_approved, guard_event.sign_in_denied, guard_event.sign_in_expired), cancelling)
    }

    @Test
    fun device_and_off_events_open_guard_settings() {
        listOf(guard_event.device_added, guard_event.device_removed, guard_event.rescue_completed, guard_event.guard_disabled).forEach {
            val spec = guard_notification_spec_for(it)
            assertEquals(guard_open_target.guard_settings, spec.target)
            assertTrue(spec.shows)
            assertFalse(spec.cancels_prompt)
            assertFalse(spec.review_action)
        }
        assertTrue(guard_notification_spec_for(guard_event.device_added).panic_action)
        assertFalse(guard_notification_spec_for(guard_event.device_removed).panic_action)
        assertFalse(guard_notification_spec_for(guard_event.rescue_completed).panic_action)
        assertFalse(guard_notification_spec_for(guard_event.guard_disabled).panic_action)
    }

    @Test
    fun panic_action_events() {
        val with_panic = guard_event.entries.filter { guard_notification_spec_for(it).panic_action }
        assertEquals(
            listOf(guard_event.change_held, guard_event.change_applied, guard_event.sign_in_approved, guard_event.device_added),
            with_panic,
        )
    }

    @Test
    fun prompt_push_targets_the_sign_in_request() {
        val approved = guard_push(guard_event.sign_in_approved, r7, account)
        val prompt = guard_prompt_push(approved)
        assertEquals(guard_push(guard_event.sign_in_request, r7, account), prompt)
        assertEquals(
            LoginAlertNotifier.guard_notification_tag(guard_push(guard_event.sign_in_request, r7, account)),
            LoginAlertNotifier.guard_notification_tag(prompt),
        )
        assertEquals("guard_sign_in_request_$r7", LoginAlertNotifier.guard_work_name(prompt))
    }

    @Test
    fun approval_from_this_device_is_not_shown() {
        guard_local_approvals.clear()
        val push = guard_push(guard_event.sign_in_approved, "r9", account)
        assertTrue(guard_should_show(push, guard_local_approvals::approved_here))
        guard_local_approvals.record("r9", account.uppercase())
        assertFalse(guard_should_show(push, guard_local_approvals::approved_here))
        assertTrue(guard_should_show(guard_push(guard_event.sign_in_approved, "r10", account), guard_local_approvals::approved_here))
        guard_local_approvals.forget("r9", account)
        assertTrue(guard_should_show(push, guard_local_approvals::approved_here))
        guard_local_approvals.clear()
    }

    @Test
    fun local_approvals_stay_bounded() {
        guard_local_approvals.clear()
        (0 until 40).forEach { guard_local_approvals.record("r$it", account) }
        assertFalse(guard_local_approvals.approved_here("r0", account))
        assertTrue(guard_local_approvals.approved_here("r39", account))
        guard_local_approvals.clear()
    }

    @Test
    fun outcome_blocks_a_late_prompt_for_the_same_request() {
        val dedupe = fake_dedupe()
        val enqueued = mutableListOf<guard_push>()
        val outcome = handle_guard_push(payload(event = "sign_in_expired"), { listOf(account) }, dedupe::claim, { enqueued += it })
        val late = handle_guard_push(payload(), { listOf(account) }, dedupe::claim, { enqueued += it })
        assertEquals(PushResult.Shown, outcome)
        assertEquals(PushResult.Ignore, late)
        assertEquals(listOf("guard:sign_in_expired:$r1", "guard:sign_in_request:$r1"), dedupe.claimed)
        assertEquals(listOf(guard_push(guard_event.sign_in_expired, r1, account)), enqueued)
    }

    @Test
    fun device_events_do_not_block_prompts() {
        val dedupe = fake_dedupe()
        handle_guard_push(payload(event = "device_added"), { listOf(account) }, dedupe::claim, { })
        assertEquals(listOf("guard:device_added:$r1"), dedupe.claimed)
    }

    @Test
    fun cancel_only_push_is_still_claimed_and_enqueued() {
        val enqueued = mutableListOf<guard_push>()
        val result = handle_guard_push(payload(event = "sign_in_denied"), { listOf(account) }, { true }, { enqueued += it })
        assertEquals(PushResult.Shown, result)
        assertEquals(listOf(guard_push(guard_event.sign_in_denied, r1, account)), enqueued)
    }

    @Test
    fun work_name_uses_event_and_id() {
        assertEquals(
            "guard_sign_in_request_$r1",
            LoginAlertNotifier.guard_work_name(guard_push(guard_event.sign_in_request, r1, account)),
        )
    }

    @Test
    fun launch_target_prefers_request() {
        assertEquals(
            guard_launch_target.request(r1, account),
            guard_launch_target_for(r1, changes = true, settings = true, account_id = account),
        )
        assertEquals(
            guard_launch_target.changes(account),
            guard_launch_target_for(null, changes = true, settings = false, account_id = account),
        )
        assertEquals(
            guard_launch_target.settings(null),
            guard_launch_target_for(null, changes = false, settings = true, account_id = "bad id"),
        )
        assertNull(guard_launch_target_for(null, changes = false, settings = false, account_id = account))
        assertNull(guard_launch_target_for("../x", changes = false, settings = false, account_id = account))
    }

    private fun keys(): Pair<guard_box_keys, Pair<ByteArray, ByteArray>> {
        val mlkem = RatchetCrypto.ml_kem_768_generate_keypair()
        val x_sk = X25519PrivateKeyParameters(SecureRandom())
        val x_pk = x_sk.generatePublicKey().encoded
        val box = guard_box_keys(x_sk.encoded, x_pk, mlkem.secret_key, mlkem.public_key, ByteArray(32), null)
        return box to (x_pk to mlkem.public_key)
    }

    @Test
    fun opens_sealed_context() {
        val (box, pks) = keys()
        val request_id = UUID.randomUUID()
        val device_id = UUID.randomUUID()
        val plain = """{"device_type":"desktop","browser":"Firefox","os":"Linux","city":"Berlin","country":"Germany","requested_at":"2026-11-15T10:00:00Z","extra":1}"""
        val sealed = GuardContextSeal.seal(
            plain.toByteArray(),
            GuardContextSeal.context_aad(request_id, device_id),
            pks.first,
            pks.second,
        )
        val view = open_guard_context(DeviceEnvelope.base64url_encode(sealed), request_id, device_id, box)
        assertNotNull(view)
        assertEquals("Linux · Firefox", view!!.device_summary())
        assertEquals("Berlin, Germany", view.place())
    }

    @Test
    fun wrong_aad_gives_null() {
        val (box, pks) = keys()
        val request_id = UUID.randomUUID()
        val device_id = UUID.randomUUID()
        val sealed = GuardContextSeal.seal(
            """{"device_type":"phone","requested_at":""}""".toByteArray(),
            GuardContextSeal.context_aad(request_id, device_id),
            pks.first,
            pks.second,
        )
        val encoded = DeviceEnvelope.base64url_encode(sealed)
        assertNull(open_guard_context(encoded, UUID.randomUUID(), device_id, box))
        assertNull(open_guard_context("", request_id, device_id, box))
        assertNull(open_guard_context("not base64!", request_id, device_id, box))
    }

    @Test
    fun device_summary_falls_back_to_type() {
        val view = org.astermail.android.guard.guard_context_view(device_type = "tablet")
        assertEquals("tablet", view.device_summary())
        assertEquals("", view.place())
        assertFalse(view.device_summary().isEmpty())
    }
}
