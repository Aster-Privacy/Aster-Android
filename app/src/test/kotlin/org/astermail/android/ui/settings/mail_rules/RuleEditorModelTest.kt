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

package org.astermail.android.ui.settings.mail_rules

import org.astermail.android.api.mail_rules.Action
import org.astermail.android.api.mail_rules.AddressOp
import org.astermail.android.api.mail_rules.AuthResult
import org.astermail.android.api.mail_rules.Condition
import org.astermail.android.api.mail_rules.DateOp
import org.astermail.android.api.mail_rules.MailRule
import org.astermail.android.api.mail_rules.MatchMode
import org.astermail.android.api.mail_rules.NumericOp
import org.astermail.android.api.mail_rules.ReadState
import org.astermail.android.api.mail_rules.TextOp
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

class RuleEditorModelTest {
    private fun complete_draft() = rule_draft(
        name = "Receipts",
        color = "#3b82f6",
        match_mode = MatchMode.ANY,
        conditions = listOf(Condition.From(AddressOp.CONTAINS, "shop@example.com")),
        actions = listOf(Action.MoveTo("folder_1")),
    )

    @Test
    fun draft_round_trips_through_json() {
        val draft = complete_draft().copy(
            conditions = listOf(
                Condition.From(AddressOp.CONTAINS, "shop@example.com", true),
                Condition.Header("X-Tag", TextOp.IS, "a", null),
                Condition.TotalSize(NumericOp.GREATER_THAN, 5L * 1024 * 1024),
                Condition.DkimResult(AuthResult.FAIL),
            ),
            actions = listOf(Action.ApplyLabels(listOf("t1", "t2")), Action.Star, Action.MarkAs(ReadState.UNREAD)),
        )
        assertEquals(draft, decode_rule_draft(encode_rule_draft(draft)))
    }

    @Test
    fun draft_round_trip_keeps_an_unfinished_spam_score() {
        val draft = complete_draft().copy(
            conditions = listOf(Condition.SpamScore(NumericOp.GREATER_THAN, Double.NaN)),
        )
        val decoded = decode_rule_draft(encode_rule_draft(draft))
        val score = decoded?.conditions?.single() as Condition.SpamScore
        assertTrue(score.value.isNaN())
    }

    @Test
    fun decode_rejects_blank_and_garbage() {
        assertNull(decode_rule_draft(null))
        assertNull(decode_rule_draft(""))
        assertNull(decode_rule_draft("{not json"))
    }

    @Test
    fun split_size_picks_the_largest_exact_unit() {
        assertEquals(5L to size_unit.mb, split_size(5L * 1024 * 1024))
        assertEquals(1536L to size_unit.kb, split_size(1536L * 1024))
        assertEquals(1000L to size_unit.bytes, split_size(1000L))
        assertEquals(2L to size_unit.gb, split_size(2L * 1024 * 1024 * 1024))
        assertEquals(0L to size_unit.mb, split_size(0L))
        assertEquals(0L to size_unit.mb, split_size(-1L))
    }

    @Test
    fun join_size_inverts_split_size_and_flags_bad_input() {
        listOf(1L, 1000L, 1024L, 1536L * 1024, 5L * 1024 * 1024).forEach { bytes ->
            val (amount, unit) = split_size(bytes)
            assertEquals(bytes, join_size(amount, unit))
        }
        assertEquals(invalid_number, join_size(-1L, size_unit.kb))
        assertEquals(invalid_number, join_size(Long.MAX_VALUE, size_unit.gb))
    }

    @Test
    fun size_text_tracks_the_stored_value() {
        assertTrue(size_text_matches("5", size_unit.mb, 5L * 1024 * 1024))
        assertTrue(size_text_matches("5120", size_unit.kb, 5L * 1024 * 1024))
        assertFalse(size_text_matches("5", size_unit.kb, 5L * 1024 * 1024))
        assertEquals("", size_text_of(-1L))
        assertEquals("5", size_text_of(5L * 1024 * 1024))
    }

    @Test
    fun parse_whole_number_accepts_digits_only() {
        assertEquals(42L, parse_whole_number(" 42 "))
        assertEquals(0L, parse_whole_number("0"))
        assertEquals(invalid_number, parse_whole_number(""))
        assertEquals(invalid_number, parse_whole_number("-3"))
        assertEquals(invalid_number, parse_whole_number("4.2"))
        assertEquals(invalid_number, parse_whole_number("99999999999999999999"))
        assertEquals("", whole_number_text(invalid_number))
        assertEquals("7", whole_number_text(7L))
    }

    @Test
    fun parse_score_accepts_a_decimal_comma() {
        assertEquals(5.5, parse_score("5,5"), 0.0)
        assertEquals(5.5, parse_score("5.5"), 0.0)
        assertTrue(parse_score("").isNaN())
        assertTrue(parse_score("abc").isNaN())
        assertTrue(parse_score("Infinity").isNaN())
        assertEquals("5", score_text(5.0))
        assertEquals("5.5", score_text(5.5))
        assertEquals("", score_text(Double.NaN))
        assertTrue(score_matches("5,5", 5.5))
        assertTrue(score_matches("", Double.NaN))
        assertFalse(score_matches("5", 5.5))
    }

    @Test
    fun apply_operator_clears_the_value_for_is_empty() {
        val from = Condition.From(AddressOp.CONTAINS, "a@b.c")
        val emptied = apply_operator(from, AddressOp.IS_EMPTY.name) as Condition.From
        assertEquals(AddressOp.IS_EMPTY, emptied.op)
        assertEquals("", emptied.value)
        assertFalse(needs_value(emptied))

        val header = Condition.Header("X-Tag", TextOp.CONTAINS, "v", null)
        val empty_header = apply_operator(header, TextOp.IS_EMPTY.name) as Condition.Header
        assertEquals("X-Tag", empty_header.name)
        assertEquals("", empty_header.value)
        assertFalse(needs_value(empty_header))
        assertTrue(is_condition_complete(empty_header))
    }

    @Test
    fun apply_operator_keeps_the_value_for_other_ops() {
        val subject = Condition.Subject(TextOp.CONTAINS, "invoice")
        val changed = apply_operator(subject, TextOp.STARTS_WITH.name) as Condition.Subject
        assertEquals(TextOp.STARTS_WITH, changed.op)
        assertEquals("invoice", changed.value)
    }

    @Test
    fun apply_operator_ignores_an_op_from_another_field_kind() {
        val subject = Condition.Subject(TextOp.CONTAINS, "invoice")
        assertSame(subject, apply_operator(subject, AddressOp.MATCHES_DOMAIN.name))
        assertSame(subject, apply_operator(subject, "nonsense"))
        val size = Condition.TotalSize(NumericOp.GREATER_THAN, 10L)
        assertSame(size, apply_operator(size, DateOp.OLDER_THAN_DAYS.name))
    }

    @Test
    fun operator_ids_match_the_current_operator() {
        val c = Condition.DateReceived(DateOp.NEWER_THAN_DAYS, 3L)
        assertTrue(current_operator_id(c) in operator_ids_for(c))
        assertTrue(operator_ids_for(Condition.HasAttachment(true)).isEmpty())
        assertNull(current_operator_id(Condition.DkimResult(AuthResult.PASS)))
    }

    @Test
    fun switching_between_address_fields_keeps_value_op_and_case() {
        val from = Condition.From(AddressOp.IS, "a@b.c", true)
        val to = switch_condition_field(from, field_id.to) as Condition.To
        assertEquals(AddressOp.IS, to.op)
        assertEquals("a@b.c", to.value)
        assertEquals(true, to.case_sensitive)
    }

    @Test
    fun switching_between_text_fields_keeps_value() {
        val subject = Condition.Subject(TextOp.ENDS_WITH, "report")
        val body = switch_condition_field(subject, field_id.body) as Condition.Body
        assertEquals(TextOp.ENDS_WITH, body.op)
        assertEquals("report", body.value)
    }

    @Test
    fun switching_to_an_unrelated_field_uses_its_default() {
        val subject = Condition.Subject(TextOp.CONTAINS, "report")
        assertEquals(
            default_condition_for(field_id.has_attachment),
            switch_condition_field(subject, field_id.has_attachment),
        )
        assertSame(subject, switch_condition_field(subject, field_id.subject))
    }

    @Test
    fun header_case_flag_round_trips() {
        val header = Condition.Header("X-Tag", TextOp.CONTAINS, "v", null)
        assertTrue(supports_case_sensitive(header))
        assertFalse(case_sensitive_of(header))
        val sensitive = set_case_sensitive(header, true)
        assertTrue(case_sensitive_of(sensitive))
        assertEquals("v", condition_text_value(sensitive))
    }

    @Test
    fun set_condition_text_keeps_the_case_flag() {
        val from = Condition.From(AddressOp.CONTAINS, "", true)
        val updated = set_condition_text(from, "x@y.z")
        assertEquals("x@y.z", condition_text_value(updated))
        assertTrue(case_sensitive_of(updated))
    }

    @Test
    fun flag_auth_and_number_accessors() {
        val flag = Condition.IsReply(true)
        assertEquals(true, condition_flag_of(flag))
        assertEquals(false, condition_flag_of(set_condition_flag(flag, false)))
        assertNull(condition_flag_of(Condition.Subject(TextOp.IS, "x")))

        val auth = Condition.SpfResult(AuthResult.PASS)
        assertEquals(AuthResult.PASS, auth_result_of(auth))
        assertEquals(AuthResult.MISSING, auth_result_of(set_auth_result(auth, AuthResult.MISSING)))
        assertNull(auth_result_of(flag))

        val count = Condition.RecipientCount(NumericOp.GREATER_THAN, 3L)
        assertEquals(3L, condition_number_of(count))
        assertEquals(9L, condition_number_of(set_condition_number(count, 9L)))
        assertNull(condition_number_of(Condition.SpamScore(NumericOp.GREATER_THAN, 5.0)))
    }

    @Test
    fun negative_numbers_are_not_complete() {
        assertTrue(condition_has_invalid_number(Condition.RecipientCount(NumericOp.GREATER_THAN, invalid_number)))
        assertTrue(condition_has_invalid_number(Condition.SpamScore(NumericOp.GREATER_THAN, Double.NaN)))
        assertFalse(condition_has_invalid_number(Condition.DateReceived(DateOp.OLDER_THAN_DAYS, 0L)))
        assertFalse(is_condition_complete(Condition.AttachmentSize(NumericOp.GREATER_THAN, invalid_number)))
        assertTrue(is_condition_complete(Condition.AttachmentSize(NumericOp.GREATER_THAN, 0L)))
    }

    @Test
    fun save_blockers_come_in_order() {
        val good = complete_draft()
        assertNull(rule_save_blocker_of(good))
        assertEquals(rule_save_blocker.rule_name, rule_save_blocker_of(good.copy(name = "  ")))
        assertEquals(rule_save_blocker.add_condition, rule_save_blocker_of(good.copy(conditions = emptyList())))
        assertEquals(
            rule_save_blocker.header_name,
            rule_save_blocker_of(good.copy(conditions = listOf(Condition.Header("", TextOp.CONTAINS, "v", null)))),
        )
        assertEquals(
            rule_save_blocker.number,
            rule_save_blocker_of(good.copy(conditions = listOf(Condition.RecipientCount(NumericOp.EQUALS, invalid_number)))),
        )
        assertEquals(
            rule_save_blocker.finish_condition,
            rule_save_blocker_of(good.copy(conditions = listOf(Condition.Subject(TextOp.CONTAINS, "")))),
        )
        val duplicate = Condition.Subject(TextOp.CONTAINS, "a")
        assertEquals(
            rule_save_blocker.duplicate,
            rule_save_blocker_of(good.copy(conditions = listOf(duplicate, duplicate))),
        )
        assertEquals(rule_save_blocker.add_action, rule_save_blocker_of(good.copy(actions = emptyList())))
        assertNull(rule_save_blocker_of(good.copy(actions = emptyList()), preserved_action_count = 1))
        assertEquals(rule_save_blocker.folder, rule_save_blocker_of(good.copy(actions = listOf(Action.MoveTo("")))))
        assertEquals(
            rule_save_blocker.labels,
            rule_save_blocker_of(good.copy(actions = listOf(Action.ApplyLabels(emptyList())))),
        )
        assertEquals(rule_save_blocker.snooze, rule_save_blocker_of(good.copy(actions = listOf(Action.Snooze("")))))
    }

    @Test
    fun the_first_blocker_wins() {
        val draft = rule_draft(name = "", conditions = emptyList(), actions = emptyList())
        assertEquals(rule_save_blocker.rule_name, rule_save_blocker_of(draft))
    }

    @Test
    fun snooze_from_now_is_a_valid_timestamp() {
        val now = java.time.OffsetDateTime.of(2026, 1, 1, 12, 0, 0, 0, java.time.ZoneOffset.ofHours(2))
        val until = snooze_until_from_now(java.time.Duration.ofDays(1), now)
        assertTrue(is_valid_rfc3339(until))
        assertEquals(now.plusDays(1).toInstant(), java.time.OffsetDateTime.parse(until).toInstant())
    }

    @Test
    fun arrow_summary_reads_left_to_right() {
        assertEquals(
            "From a or Subject b $rule_arrow Move to Inbox",
            rule_arrow_summary(listOf("From a", "Subject b"), listOf("Move to Inbox"), "or"),
        )
        assertEquals("From a", rule_arrow_summary(listOf("From a"), emptyList(), "and"))
        assertEquals(
            "A and B +1 $rule_arrow X, Y",
            rule_arrow_summary(listOf("A", "B", "C"), listOf("X", "Y"), "and"),
        )
    }

    @Test
    fun draft_of_an_existing_rule_strips_unavailable_actions() {
        val rule = MailRule(
            id = "r1",
            name = "Fwd",
            color = "",
            conditions = listOf(Condition.Subject(TextOp.CONTAINS, "x")),
            actions = listOf(Action.Forward("a@b.c"), Action.Star),
        )
        val draft = rule_draft_of(rule, "#3b82f6")
        assertEquals(listOf<Action>(Action.Star), draft.actions)
        assertEquals("#3b82f6", draft.color)
        assertEquals(listOf<Action>(Action.Forward("a@b.c")), preserved_actions_of(rule))
        assertEquals(rule_draft(color = "#3b82f6"), rule_draft_of(null, "#3b82f6"))
        assertNull(rule_save_blocker_of(draft.copy(actions = emptyList()), preserved_actions_of(rule).size))
    }

    @Test
    fun draft_edits_ignore_out_of_range_indices() {
        val draft = complete_draft()
        assertSame(draft, replace_condition(draft, 5, Condition.IsReply(true)))
        assertSame(draft, remove_condition(draft, -1))
        assertSame(draft, replace_action(draft, 3, Action.Star))
        assertSame(draft, remove_action(draft, 3))
        assertTrue(remove_condition(draft, 0).conditions.isEmpty())
    }

    @Test
    fun used_action_kinds_can_skip_the_row_being_edited() {
        val actions = listOf(Action.Star, Action.MoveTo("f"))
        assertEquals(setOf(action_id.star, action_id.move_to), used_action_kinds(actions))
        assertEquals(setOf(action_id.move_to), used_action_kinds(actions, except_index = 0))
    }

    @Test
    fun toggling_a_label_adds_then_removes_it() {
        val added = toggle_label_token(Action.ApplyLabels(emptyList()), "t1")
        assertEquals(listOf("t1"), added.label_tokens)
        assertTrue(toggle_label_token(added, "t1").label_tokens.isEmpty())
    }

    @Test
    fun dirty_only_after_a_real_change() {
        val base = encode_rule_draft(complete_draft())
        assertFalse(is_dirty(null, null))
        assertFalse(is_dirty(base, base))
        assertFalse(is_dirty(null, base))
        assertTrue(is_dirty(base, encode_rule_draft(complete_draft().copy(name = "Other"))))
    }
    @Test
    fun conflict_check_flags_single_valued_fields_only() {
        val two_senders = listOf(
            Condition.From(AddressOp.IS, "a@example.com"),
            Condition.From(AddressOp.IS, "b@example.com"),
        )
        assertTrue(conditions_conflict_under_all(two_senders, MatchMode.ALL))
        assertFalse(conditions_conflict_under_all(two_senders, MatchMode.ANY))
        val two_recipients = listOf(
            Condition.To(AddressOp.IS, "a@example.com"),
            Condition.To(AddressOp.IS, "b@example.com"),
        )
        assertFalse(conditions_conflict_under_all(two_recipients, MatchMode.ALL))
    }

    @Test
    fun score_input_keeps_a_leading_minus() {
        assertEquals("-2.5", score_input("-2.5"))
        assertEquals("25", score_input("2-5"))
        assertEquals("3,5", score_input("3,5x"))
        assertEquals(-2.5, parse_score(score_input("-2.5")), 0.0)
    }
}
