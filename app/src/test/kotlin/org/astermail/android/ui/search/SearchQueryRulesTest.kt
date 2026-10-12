//
// Aster Mail - Privacy-first encrypted email
// Copyright (C) 2026 Aster Privacy
//
// This program is free software: you can redistribute it and/or modify
// it under the terms of the GNU Affero General Public License as published by
// the Free Software Foundation, either version 3 of the License, or
// (at your option) any later version.
//
// This program is distributed in the hope that it will be useful,
// but WITHOUT ANY WARRANTY; without even the implied warranty of
// MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
// GNU Affero General Public License for more details.
//
// You should have received a copy of the GNU Affero General Public License
// along with this program.  If not, see <https://www.gnu.org/licenses/>.
//


package org.astermail.android.ui.search

import org.astermail.android.api.mail.MailItem
import org.astermail.android.mail.InboxItem
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

class SearchQueryRulesTest {

    private val zone = ZoneId.of("America/New_York")

    private val env = SearchEnv(
        now = Instant.parse("2026-08-15T16:00:00Z").toEpochMilli(),
        zone = zone,
        labels = SearchLabelIndex(
            listOf(
                SearchLabelEntry("Work", "folder_work"),
                SearchLabelEntry("Workshop", "folder_workshop"),
                SearchLabelEntry("Réunion", "tag_meeting"),
            ),
        ),
    )

    private fun item(
        sender_email: String = "someone@example.com",
        sender_name: String = "Someone",
        subject: String = "hello",
        preview: String = "preview",
        timestamp: String = "2026-08-01T00:00:00Z",
        is_read: Boolean = true,
        is_starred: Boolean = false,
        has_attachments: Boolean = false,
        is_spam: Boolean = false,
        is_trashed: Boolean = false,
        labels: List<String> = emptyList(),
        tag_tokens: List<String> = emptyList(),
        to_addresses: List<String> = emptyList(),
        received_on: String? = null,
    ) = InboxItem(
        id = "1",
        thread_token = null,
        thread_message_count = 1,
        sender_name = sender_name,
        sender_email = sender_email,
        subject = subject,
        preview = preview,
        timestamp = timestamp,
        is_read = is_read,
        is_starred = is_starred,
        is_encrypted = false,
        has_attachments = has_attachments,
        is_trashed = is_trashed,
        is_archived = false,
        is_spam = is_spam,
        labels = labels,
        tag_tokens = tag_tokens,
        received_on = received_on,
        to_addresses = to_addresses,
        raw_item = MailItem(id = "1"),
    )

    private fun op(key: String, value: String, negated: Boolean = false) = SearchOperator(negated, key, value)

    private fun matches(query: String, item: InboxItem): Boolean =
        matches_item(item, parse_query(query), env = env)

    private fun matches_ops(ops: List<SearchOperator>, item: InboxItem): Boolean =
        matches_item(item, ParsedQuery("", ops), env = env)

    @Test
    fun recognized_keys_become_operators() {
        val parsed = parse_query(
            "from:ann to:bob contact:cy subject:plan has:attachment is:unread in:sent " +
                "before:7d after:2026-08-01 date:today label:work",
        )
        assertEquals(
            listOf("from", "to", "contact", "subject", "has", "is", "in", "before", "after", "date", "label"),
            parsed.operators.map { it.key },
        )
        assertEquals("", parsed.free_text)
    }

    @Test
    fun keys_are_case_insensitive_and_values_are_lowercased() {
        assertEquals(listOf(op("from", "ann@example.com")), parse_query("FROM:Ann@Example.com").operators)
    }

    @Test
    fun unrecognized_keys_stay_free_text() {
        val parsed = parse_query("foo:bar re:invent cc:ann size:10")
        assertTrue(parsed.operators.isEmpty())
        assertEquals("foo:bar re:invent cc:ann size:10", parsed.free_text)
        assertEquals(listOf("foo:bar", "re:invent", "cc:ann", "size:10"), parsed.terms)
    }

    @Test
    fun urls_and_times_stay_free_text() {
        val parsed = parse_query("https://example.com/a?b=1 10:30 mailto:ann@example.com")
        assertTrue(parsed.operators.isEmpty())
        assertEquals(listOf("https://example.com/a?b=1", "10:30", "mailto:ann@example.com"), parsed.terms)
        assertTrue(matches("10:30", item(subject = "Standup at 10:30")))
        assertTrue(matches("https://example.com/a", item(preview = "See https://example.com/a for details")))
        assertFalse(matches("https://example.com/a", item(preview = "See the site for details")))
    }

    @Test
    fun an_operator_needs_a_token_start() {
        val parsed = parse_query("refrom:ann x-from:ann")
        assertTrue(parsed.operators.isEmpty())
        assertEquals(listOf("refrom:ann", "x-from:ann"), parsed.terms)
    }

    @Test
    fun a_recognized_key_with_an_unusable_value_is_plain_text() {
        val parsed = parse_query("is:banana in:receipts has:hologram date:someday before:soon report")
        assertTrue(parsed.operators.isEmpty())
        assertEquals(
            listOf("is:banana", "in:receipts", "has:hologram", "date:someday", "before:soon", "report"),
            parsed.terms,
        )
        assertFalse(matches("is:banana", item(subject = "Quarterly report")))
        assertTrue(matches("is:banana", item(subject = "Note about is:banana")))
    }

    @Test
    fun a_negated_unusable_operator_is_plain_text_too() {
        val parsed = parse_query("-is:banana")
        assertTrue(parsed.operators.isEmpty())
        assertEquals(listOf("-is:banana"), parsed.terms)
    }

    @Test
    fun negation_inverts_an_operator() {
        assertEquals(listOf(op("is", "unread", negated = true)), parse_query("-is:unread").operators)
        assertTrue(matches("-is:unread", item(is_read = true)))
        assertFalse(matches("-is:unread", item(is_read = false)))
        assertTrue(matches("-from:ann@example.com", item(sender_email = "bob@example.com")))
        assertFalse(matches("-from:ann@example.com", item(sender_email = "ann@example.com")))
        assertFalse(matches("-has:attachment", item(has_attachments = true)))
        assertTrue(matches("-subject:invoice", item(subject = "Lunch")))
    }

    @Test
    fun quoted_values_keep_their_spaces() {
        val parsed = parse_query("from:\"Ann Smith\" subject:\"budget plan\" report")
        assertEquals(listOf(op("from", "ann smith"), op("subject", "budget plan")), parsed.operators)
        assertEquals("report", parsed.free_text)
        assertEquals("from:\"ann smith\"", operator_token(op("from", "ann smith")))
        assertEquals("-to:bob", operator_token(op("to", "bob", negated = true)))
    }

    @Test
    fun recipient_query_quotes_names_with_spaces() {
        assertEquals("to:\"Ann Smith\"", recipient_search_query("Ann Smith"))
        assertEquals("to:ann@example.com", recipient_search_query("ann@example.com"))
        assertEquals(listOf(op("to", "ann smith")), parse_query(recipient_search_query("Ann Smith")).operators)
    }

    @Test
    fun an_operator_without_a_value_is_not_a_search() {
        for (query in listOf("from:", "-from:", "is:", "from:\"", "  to:  ")) {
            val parsed = parse_query(query)
            assertTrue(query, parsed.operators.isEmpty())
            assertTrue(query, parsed.terms.isEmpty())
            assertFalse(query, has_search_query(parsed))
        }
        assertTrue(has_search_query(parse_query("from: report")))
        assertEquals(listOf("report"), parse_query("from: report").terms)
        assertTrue(matches("from: report", item(subject = "Quarterly report")))
    }

    @Test
    fun after_includes_the_day_at_local_midnight() {
        assertTrue(matches("after:2026-08-10", item(timestamp = "2026-08-10T04:00:00Z")))
        assertFalse(matches("after:2026-08-10", item(timestamp = "2026-08-10T03:59:59Z")))
    }

    @Test
    fun before_excludes_the_day_at_local_midnight() {
        assertTrue(matches("before:2026-08-10", item(timestamp = "2026-08-10T03:59:59Z")))
        assertFalse(matches("before:2026-08-10", item(timestamp = "2026-08-10T04:00:00Z")))
    }

    @Test
    fun date_uses_the_local_day() {
        assertTrue(matches("date:today", item(timestamp = "2026-08-15T04:00:00Z")))
        assertFalse(matches("date:today", item(timestamp = "2026-08-15T03:59:59Z")))
        assertTrue(matches("date:yesterday", item(timestamp = "2026-08-15T03:59:59Z")))
        assertFalse(matches("date:yesterday", item(timestamp = "2026-08-14T03:59:59Z")))
        assertTrue(matches("date:2026-08-10", item(timestamp = "2026-08-11T03:59:59Z")))
        assertFalse(matches("date:2026-08-10", item(timestamp = "2026-08-11T04:00:00Z")))
        assertFalse(matches("-date:today", item(timestamp = "2026-08-15T12:00:00Z")))
    }

    @Test
    fun relative_days_count_back_from_now() {
        assertTrue(matches("before:7d", item(timestamp = "2026-08-08T15:59:59Z")))
        assertFalse(matches("before:7d", item(timestamp = "2026-08-08T16:00:00Z")))
        assertTrue(matches("after:7d", item(timestamp = "2026-08-08T16:00:00Z")))
        assertFalse(matches("after:7d", item(timestamp = "2026-08-08T15:59:59Z")))
    }

    @Test
    fun custom_range_includes_both_chosen_days() {
        val ops = apply_custom_range(emptyList(), LocalDate.of(2026, 8, 10), LocalDate.of(2026, 8, 12))
        assertEquals(listOf(op("after", "2026-08-10"), op("before", "2026-08-13")), ops)
        assertFalse(matches_ops(ops, item(timestamp = "2026-08-10T03:59:59Z")))
        assertTrue(matches_ops(ops, item(timestamp = "2026-08-10T04:00:00Z")))
        assertTrue(matches_ops(ops, item(timestamp = "2026-08-13T03:59:59Z")))
        assertFalse(matches_ops(ops, item(timestamp = "2026-08-13T04:00:00Z")))
        assertEquals(LocalDate.of(2026, 8, 10) to LocalDate.of(2026, 8, 12), custom_date_range(ops))
    }

    @Test
    fun custom_range_on_one_day_matches_that_day() {
        val day = LocalDate.of(2026, 8, 10)
        val ops = apply_custom_range(emptyList(), day, day)
        assertTrue(matches_ops(ops, item(timestamp = "2026-08-10T12:00:00Z")))
        assertEquals(day to day, custom_date_range(ops))
    }

    @Test
    fun an_inverted_custom_range_is_swapped() {
        assertEquals(
            apply_custom_range(emptyList(), LocalDate.of(2026, 8, 10), LocalDate.of(2026, 8, 12)),
            apply_custom_range(emptyList(), LocalDate.of(2026, 8, 12), LocalDate.of(2026, 8, 10)),
        )
    }

    @Test
    fun custom_range_replaces_only_positive_date_bounds() {
        val ops = listOf(op("before", "7d"), op("before", "2026-01-01", negated = true), op("is", "unread"))
        assertEquals(
            listOf(
                op("before", "2026-01-01", negated = true),
                op("is", "unread"),
                op("after", "2026-08-10"),
                op("before", "2026-08-11"),
            ),
            apply_custom_range(ops, LocalDate.of(2026, 8, 10), LocalDate.of(2026, 8, 10)),
        )
    }

    @Test
    fun an_invalid_date_is_plain_text() {
        for (query in listOf("after:2026-13-45", "before:2026-02-30", "date:2026-00-10", "after:7", "before:tomorrow")) {
            val parsed = parse_query(query)
            assertTrue(query, parsed.operators.isEmpty())
            assertEquals(query, listOf(query), parsed.terms)
        }
        assertNull(parse_iso_date("2026-02-30"))
        assertEquals(LocalDate.of(2026, 2, 28), parse_iso_date("2026-02-28"))
        assertEquals(7, parse_relative_days("7d"))
        assertNull(parse_relative_days("7"))
    }

    @Test
    fun date_presets_are_detected_only_for_their_own_shape() {
        assertEquals(DatePreset.ANY, detect_date_preset(emptyList()))
        assertEquals(DatePreset.WEEK, detect_date_preset(listOf(op("before", "7d"))))
        assertEquals(DatePreset.CUSTOM, detect_date_preset(listOf(op("after", "7d"))))
        assertEquals(DatePreset.CUSTOM, detect_date_preset(listOf(op("before", "9d"))))
        assertEquals(DatePreset.ANY, detect_date_preset(listOf(op("before", "7d", negated = true))))
        assertNull(custom_date_range(listOf(op("after", "7d"))))
    }

    @Test
    fun repeated_from_operators_are_any_of_and_other_keys_are_all_of() {
        val query = "from:ann@example.com from:bob is:unread"
        assertTrue(matches(query, item(sender_email = "ann@example.com", is_read = false)))
        assertTrue(matches(query, item(sender_email = "bobby@example.com", is_read = false)))
        assertFalse(matches(query, item(sender_email = "carol@example.com", is_read = false)))
        assertFalse(matches(query, item(sender_email = "ann@example.com", is_read = true)))
        assertFalse(matches("from:ann@example.com -from:ann@example.com", item(sender_email = "ann@example.com")))
        assertFalse(matches("is:unread is:starred", item(is_read = false, is_starred = false)))
    }

    @Test
    fun a_full_address_matches_exactly() {
        assertFalse(matches("from:ann@example.com", item(sender_email = "joann@example.com")))
        assertFalse(matches("to:ann@example.com", item(to_addresses = listOf("joann@example.com"))))
        assertTrue(matches("to:ann@example.com", item(to_addresses = listOf("Ann@Example.com"))))
        assertTrue(matches("to:ann@example.com", item(received_on = "ann@example.com")))
        assertTrue(matches("to:ann", item(to_addresses = listOf("joann@example.com"))))
        assertFalse(matches("contact:ann@example.com", item(to_addresses = listOf("joann@example.com"))))
    }

    @Test
    fun free_text_terms_are_all_required() {
        val mail = item(subject = "Quarterly budget report", preview = "Numbers attached")
        assertTrue(matches("report quarterly", mail))
        assertTrue(matches("REPORT numbers", mail))
        assertFalse(matches("report invoice", mail))
    }

    @Test
    fun a_quoted_phrase_is_one_contiguous_term() {
        val mail = item(subject = "Quarterly budget report")
        assertEquals(listOf("budget report", "quarterly"), parse_query("\"Budget Report\" quarterly").terms)
        assertTrue(matches("\"budget report\"", mail))
        assertFalse(matches("\"report budget\"", mail))
        assertFalse(matches("\"quarterly report\"", mail))
        assertTrue(matches("quarterly report", mail))
    }

    @Test
    fun free_text_searches_recipients() {
        assertTrue(matches("dana", item(to_addresses = listOf("dana@example.com"))))
        assertTrue(matches("alias@aster.test", item(received_on = "alias@aster.test")))
        assertFalse(matches("dana", item(to_addresses = listOf("erin@example.com"))))
    }

    @Test
    fun matching_ignores_case_and_diacritics() {
        val mail = item(sender_name = "José Núñez", subject = "Café résumé")
        assertEquals("cafe resume", fold_text("Café RÉSUMÉ"))
        assertTrue(matches("cafe resume", mail))
        assertTrue(matches("CAFÉ", mail))
        assertTrue(matches("from:jose", mail))
        assertTrue(matches("from:\"josé nunez\"", mail))
        assertTrue(matches("subject:resume", mail))
        assertTrue(matches("résumé", item(subject = "Resume attached")))
    }

    @Test
    fun label_resolves_a_typed_name_to_folder_and_tag_tokens() {
        assertEquals(setOf("folder_work"), env.labels.tokens_for("work"))
        assertEquals(setOf("folder_work", "folder_workshop"), env.labels.tokens_for("wor"))
        assertEquals(setOf("tag_meeting"), env.labels.tokens_for("reunion"))
        assertTrue(env.labels.tokens_for("missing").isEmpty())
        assertTrue(matches("label:work", item(labels = listOf("folder_work"))))
        assertFalse(matches("label:work", item(labels = listOf("folder_workshop"))))
        assertTrue(matches("label:réunion", item(tag_tokens = listOf("tag_meeting"))))
        assertFalse(matches("label:work", item()))
        assertFalse(matches("label:missing", item(labels = listOf("folder_work"))))
        assertTrue(matches("-label:work", item(labels = listOf("folder_workshop"))))
        assertFalse(matches("-label:work", item(labels = listOf("folder_work"))))
        assertTrue(matches("-label:missing", item(labels = listOf("folder_work"))))
    }

    @Test
    fun spam_and_trash_are_excluded_unless_the_scope_includes_them() {
        assertFalse(matches("hello", item(is_spam = true)))
        assertFalse(matches("hello", item(is_trashed = true)))
        assertTrue(matches("in:anywhere hello", item(is_spam = true)))
        assertTrue(matches("in:anywhere hello", item(is_trashed = true)))
        assertFalse(matches("-in:spam hello", item(is_trashed = true)))
    }

    @Test
    fun without_key_keeps_negated_operators() {
        val ops = listOf(op("in", "trash"), op("in", "spam", negated = true), op("from", "ann"))
        assertEquals(listOf(op("in", "spam", negated = true), op("from", "ann")), without_key(ops, "in"))
        assertEquals("trash", operator_value(ops, "in"))
        assertNull(operator_value(listOf(op("in", "spam", negated = true)), "in"))
    }

    @Test
    fun picking_a_person_replaces_the_first_positive_value() {
        val ops = listOf(op("from", "ann"), op("from", "bob", negated = true), op("from", "cy"), op("to", "dee"))
        assertEquals(
            listOf(op("from", "bob", negated = true), op("from", "cy"), op("to", "dee"), op("from", "eve@example.com")),
            set_person(ops, "from", " Eve@Example.com "),
        )
        assertEquals(
            listOf(op("from", "bob", negated = true), op("from", "cy"), op("to", "dee")),
            set_person(ops, "from", ""),
        )
    }

    @Test
    fun toggles_touch_only_their_own_positive_operator() {
        val ops = listOf(op("is", "unread", negated = true), op("has", "attachments"))
        assertEquals(ops + op("is", "unread"), toggle_operator(ops, "is", "unread"))
        assertEquals(ops, toggle_operator(ops + op("is", "unread"), "is", "unread"))
        assertEquals(listOf(op("is", "unread", negated = true)), toggle_attachment(ops))
        assertEquals(listOf(op("has", "attachment")), toggle_attachment(emptyList()))
    }

    @Test
    fun a_typed_operator_is_lifted_once_whitespace_follows_it() {
        assertEquals(LiftResult(emptyList(), "from:ann"), lift_operators("from:ann", submit = false))
        assertEquals(LiftResult(listOf(op("from", "ann")), ""), lift_operators("from:ann ", submit = false))
        assertEquals(
            LiftResult(listOf(op("is", "unread")), "hello world"),
            lift_operators("hello is:unread world", submit = false),
        )
        assertEquals(
            LiftResult(listOf(op("from", "ann smith")), "report"),
            lift_operators("from:\"Ann Smith\" report", submit = false),
        )
        assertEquals(
            LiftResult(listOf(op("is", "unread", negated = true)), "x"),
            lift_operators("-is:unread x", submit = false),
        )
    }

    @Test
    fun an_operator_still_being_typed_is_not_lifted() {
        for (text in listOf("from:", "from: ", "from:an", "report from:\"ann sm", "is:unrea", "foo:bar ", "is:banana ")) {
            assertEquals(text, LiftResult(emptyList(), text), lift_operators(text, submit = false))
        }
        assertEquals(
            LiftResult(listOf(op("is", "unread")), "from:an"),
            lift_operators("is:unread from:an", submit = false),
        )
    }

    @Test
    fun submitting_lifts_the_last_operator_too() {
        assertEquals(LiftResult(listOf(op("from", "ann")), ""), lift_operators("from:ann", submit = true))
        assertEquals(
            LiftResult(listOf(op("is", "unread"), op("from", "ann")), "report "),
            lift_operators("is:unread report from:ann", submit = true),
        )
        assertEquals(LiftResult(emptyList(), "from:"), lift_operators("from:", submit = true))
        assertEquals(LiftResult(emptyList(), "10:30 foo:bar"), lift_operators("10:30 foo:bar", submit = true))
    }

    @Test
    fun lifted_operators_merge_into_the_chips() {
        val chips = listOf(op("in", "inbox"), op("from", "ann"), op("in", "spam", negated = true))
        assertEquals(
            listOf(op("from", "ann"), op("in", "spam", negated = true), op("in", "sent")),
            merge_lifted(chips, listOf(op("in", "sent"))),
        )
        assertEquals(chips + op("from", "bob"), merge_lifted(chips, listOf(op("from", "bob"))))
        assertEquals(chips, merge_lifted(chips, listOf(op("from", "ann"))))
        assertEquals(
            listOf(op("after", "2026-08-01")),
            merge_lifted(listOf(op("after", "7d")), listOf(op("after", "2026-08-01"))),
        )
    }

    @Test
    fun chips_and_typed_text_form_one_query() {
        val parsed = combined_query(listOf(op("is", "unread")), "report from:ann")
        assertEquals(listOf(op("is", "unread"), op("from", "ann")), parsed.operators)
        assertEquals(listOf("report"), parsed.terms)
        assertFalse(has_search_query(combined_query(emptyList(), "from:")))
        assertTrue(has_search_query(combined_query(listOf(op("is", "unread")), "")))
    }

    @Test
    fun advanced_sheet_reads_the_current_scope_and_fields() {
        val fields = advanced_fields(parse_query("in:inbox in:trash from:ann is:starred has:attachments before:30d").operators)
        assertEquals("trash", fields.scope)
        assertEquals("ann", fields.from)
        assertTrue(fields.starred)
        assertTrue(fields.attachment)
        assertFalse(fields.unread)
        assertEquals(DatePreset.MONTH, fields.preset)
        assertEquals("drafts", advanced_fields(listOf(op("in", "draft"))).scope)
        assertEquals("archive", advanced_fields(listOf(op("in", "archived"))).scope)
        assertEquals("", advanced_fields(listOf(op("in", "all"))).scope)
        assertEquals("", advanced_fields(listOf(op("in", "spam", negated = true))).scope)
        assertTrue(advanced_fields(listOf(op("in", "starred"))).starred)
    }

    @Test
    fun advanced_sheet_writes_one_scope_and_keeps_operators_it_does_not_own() {
        val ops = parse_query(
            "in:inbox in:trash -in:spam label:work from:ann from:bob is:encrypted -is:unread date:today",
        ).operators
        val built = build_advanced_operators(ops, advanced_fields(ops).copy(scope = "sent", from = "Cy"))
        assertEquals(
            listOf(
                op("in", "spam", negated = true),
                op("label", "work"),
                op("from", "bob"),
                op("is", "encrypted"),
                op("is", "unread", negated = true),
                op("date", "today"),
                op("from", "cy"),
                op("in", "sent"),
            ),
            built,
        )
        assertEquals(
            listOf(op("in", "spam", negated = true)),
            build_advanced_operators(
                listOf(op("in", "trash"), op("in", "spam", negated = true)),
                advanced_fields(listOf(op("in", "trash"))).copy(scope = ""),
            ),
        )
    }

    @Test
    fun advanced_sheet_applied_unchanged_keeps_the_same_filters() {
        val ops = parse_query(
            "from:ann to:bob subject:plan has:attachment is:unread is:starred after:2026-08-10 before:2026-08-13 " +
                "in:archive -from:cy contact:dee",
        ).operators
        assertEquals(ops.toSet(), build_advanced_operators(ops, advanced_fields(ops)).toSet())
        assertEquals(DatePreset.CUSTOM, advanced_fields(ops).preset)
    }

    @Test
    fun advanced_sheet_date_presets_replace_the_date_bounds() {
        val ops = listOf(op("after", "2026-08-10"), op("before", "2026-08-13"), op("after", "2026-01-01", negated = true))
        assertEquals(
            listOf(op("after", "2026-01-01", negated = true), op("before", "7d")),
            build_advanced_operators(ops, advanced_fields(ops).copy(preset = DatePreset.WEEK)),
        )
        assertEquals(
            listOf(op("after", "2026-01-01", negated = true)),
            build_advanced_operators(ops, advanced_fields(ops).copy(preset = DatePreset.ANY)),
        )
    }

    @Test
    fun advanced_sheet_clear_drops_every_operator() {
        val ops = parse_query("in:trash -in:spam label:work from:ann after:2026-08-10 before:2026-08-13").operators
        val blank = AdvancedFields(
            from = "",
            to = "",
            subject = "",
            attachment = false,
            unread = false,
            starred = false,
            preset = DatePreset.ANY,
            scope = "",
            cleared = true,
        )
        assertTrue(build_advanced_operators(ops, blank).isEmpty())
        assertEquals(
            listOf(op("is", "starred"), op("in", "spam")),
            build_advanced_operators(ops, blank.copy(starred = true, scope = "spam")),
        )
    }

    @Test
    fun quick_row_and_custom_chips_cover_every_operator_once() {
        val ops = parse_query("from:ann from:bob -from:cy is:unread is:starred has:attachment before:7d in:sent").operators
        val quick = quick_row_operators(ops)
        val custom = custom_chip_operators(ops)
        assertEquals(
            listOf(op("from", "ann"), op("is", "unread"), op("has", "attachment"), op("before", "7d")),
            quick,
        )
        assertEquals(ops.size, quick.size + custom.size)
        assertEquals(ops.toSet(), (quick + custom).toSet())
    }

    @Test
    fun attachment_type_operator_matches_by_file_name_or_content_type() {
        val with_files = item(has_attachments = true)
        val typed = env.copy(
            attachments = mapOf(
                "1" to listOf(
                    SearchAttachment("Invoice.PDF", "application/octet-stream"),
                    SearchAttachment("scan", "image/heic"),
                ),
            ),
        )
        fun typed_match(query: String, target: InboxItem = with_files) =
            matches_item(target, parse_query(query), env = typed)

        assertEquals(listOf(op("has", "pdf")), parse_query("has:pdf").operators)
        assertTrue(typed_match("has:pdf"))
        assertTrue(typed_match("has:image"))
        assertFalse(typed_match("has:video"))
        assertTrue(typed_match("-has:video"))
        assertFalse(typed_match("-has:pdf"))
        assertTrue(typed_match("has:video has:pdf"))
        assertFalse(typed_match("has:pdf", item(has_attachments = false)))
        assertFalse(matches("has:pdf", with_files))
    }

    @Test
    fun attachment_type_matching_covers_each_documented_type() {
        fun hit(type: String, name: String, mime: String = "") =
            attachment_matches_type(type, SearchAttachment(name, mime))

        assertTrue(hit("pdf", "", "application/pdf"))
        assertTrue(hit("document", "notes.docx"))
        assertTrue(hit("spreadsheet", "budget.xlsx"))
        assertTrue(hit("video", "clip", "video/mp4"))
        assertTrue(hit("audio", "memo.flac"))
        assertTrue(hit("archive", "backup.tar.gz"))
        assertFalse(hit("archive", "report.pdf"))
        assertFalse(hit("hologram", "report.pdf"))
    }

    @Test
    fun half_typed_person_operator_stays_out_of_the_chips() {
        val chips = listOf(SearchOperator(false, "is", "starred"))

        assertEquals(chips, chip_visible_operators(chips, "from:wal invoice"))
        assertEquals(
            chips + SearchOperator(false, "is", "unread"),
            chip_visible_operators(chips, "is:unread from:wal"),
        )
        assertEquals(listOf(SearchOperator(false, "from", "wal")), pending_typed_operators("is:unread from:wal"))
        assertEquals("from:wal invoice", pending_operator_text(pending_typed_operators("from:wal invoice"), "invoice"))
    }

    @Test
    fun picking_a_person_replaces_only_the_shown_operator() {
        val ops = listOf(
            SearchOperator(false, "from", "ann"),
            SearchOperator(false, "from", "bob"),
            SearchOperator(true, "from", "carl"),
        )

        assertEquals(
            listOf(ops[1], ops[2], SearchOperator(false, "from", "dana")),
            set_person(ops, "from", "Dana"),
        )
        assertEquals(listOf(ops[1], ops[2]), set_person(ops, "from", ""))
    }

    @Test
    fun advanced_apply_keeps_a_scope_the_sheet_cannot_show() {
        val ops = listOf(SearchOperator(false, "in", "starred"), SearchOperator(false, "is", "unread"))
        val fields = advanced_fields(ops)

        assertEquals("", fields.scope)
        assertTrue(build_advanced_operators(ops, fields).contains(ops[0]))
        assertFalse(build_advanced_operators(ops, fields.copy(scope = "sent")).contains(ops[0]))
        assertTrue(
            build_advanced_operators(ops, fields.copy(scope = "sent")).contains(SearchOperator(false, "in", "sent")),
        )
    }
}
