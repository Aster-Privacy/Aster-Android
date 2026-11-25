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

import androidx.annotation.StringRes
import org.astermail.android.R
import org.astermail.android.api.mail_rules.Action
import org.astermail.android.api.mail_rules.AddressOp
import org.astermail.android.api.mail_rules.AttachmentNameOp
import org.astermail.android.api.mail_rules.AuthResult
import org.astermail.android.api.mail_rules.Condition
import org.astermail.android.api.mail_rules.DateOp
import org.astermail.android.api.mail_rules.MailRule
import org.astermail.android.api.mail_rules.MatchMode
import org.astermail.android.api.mail_rules.NumericOp
import org.astermail.android.api.mail_rules.ReadState
import org.astermail.android.api.mail_rules.TextOp
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

enum class field_id {
    from, reply_to, to, cc, bcc, any_recipient,
    subject, body, header, list_id,
    has_attachment, attachment_name, attachment_size,
    is_reply, is_forward, is_auto_submitted, has_calendar_invite, has_list_id,
    recipient_count, total_size, spam_score,
    date_received, dkim_result, spf_result, dmarc_result,
}

enum class field_kind {
    address, text, header, attachment_name, boolean, numeric_size, numeric_plain, date, auth,
}

fun field_kind_of(field: field_id): field_kind = when (field) {
    field_id.from, field_id.reply_to, field_id.to, field_id.cc, field_id.bcc, field_id.any_recipient -> field_kind.address
    field_id.subject, field_id.body, field_id.list_id -> field_kind.text
    field_id.header -> field_kind.header
    field_id.attachment_name -> field_kind.attachment_name
    field_id.has_attachment, field_id.is_reply, field_id.is_forward, field_id.is_auto_submitted, field_id.has_calendar_invite, field_id.has_list_id -> field_kind.boolean
    field_id.attachment_size, field_id.total_size -> field_kind.numeric_size
    field_id.recipient_count, field_id.spam_score -> field_kind.numeric_plain
    field_id.date_received -> field_kind.date
    field_id.dkim_result, field_id.spf_result, field_id.dmarc_result -> field_kind.auth
}

fun field_of(condition: Condition): field_id? = when (condition) {
    is Condition.And, is Condition.Or, is Condition.Not, Condition.Unsupported -> null
    is Condition.From -> field_id.from
    is Condition.ReplyTo -> field_id.reply_to
    is Condition.To -> field_id.to
    is Condition.Cc -> field_id.cc
    is Condition.Bcc -> field_id.bcc
    is Condition.AnyRecipient -> field_id.any_recipient
    is Condition.Subject -> field_id.subject
    is Condition.Body -> field_id.body
    is Condition.Header -> field_id.header
    is Condition.ListId -> field_id.list_id
    is Condition.AttachmentName -> field_id.attachment_name
    is Condition.HasAttachment -> field_id.has_attachment
    is Condition.IsReply -> field_id.is_reply
    is Condition.IsForward -> field_id.is_forward
    is Condition.IsAutoSubmitted -> field_id.is_auto_submitted
    is Condition.HasCalendarInvite -> field_id.has_calendar_invite
    is Condition.HasListId -> field_id.has_list_id
    is Condition.AttachmentSize -> field_id.attachment_size
    is Condition.TotalSize -> field_id.total_size
    is Condition.RecipientCount -> field_id.recipient_count
    is Condition.SpamScore -> field_id.spam_score
    is Condition.DateReceived -> field_id.date_received
    is Condition.DkimResult -> field_id.dkim_result
    is Condition.SpfResult -> field_id.spf_result
    is Condition.DmarcResult -> field_id.dmarc_result
}

fun default_condition_for(field: field_id): Condition = when (field_kind_of(field)) {
    field_kind.address -> when (field) {
        field_id.from -> Condition.From(AddressOp.CONTAINS, "")
        field_id.reply_to -> Condition.ReplyTo(AddressOp.CONTAINS, "")
        field_id.to -> Condition.To(AddressOp.CONTAINS, "")
        field_id.cc -> Condition.Cc(AddressOp.CONTAINS, "")
        field_id.bcc -> Condition.Bcc(AddressOp.CONTAINS, "")
        field_id.any_recipient -> Condition.AnyRecipient(AddressOp.CONTAINS, "")
        else -> Condition.From(AddressOp.CONTAINS, "")
    }
    field_kind.text -> when (field) {
        field_id.subject -> Condition.Subject(TextOp.CONTAINS, "")
        field_id.body -> Condition.Body(TextOp.CONTAINS, "")
        field_id.list_id -> Condition.ListId(TextOp.CONTAINS, "")
        else -> Condition.Subject(TextOp.CONTAINS, "")
    }
    field_kind.header -> Condition.Header(name = "", op = TextOp.CONTAINS, value = "")
    field_kind.attachment_name -> Condition.AttachmentName(AttachmentNameOp.CONTAINS, "")
    field_kind.boolean -> when (field) {
        field_id.has_attachment -> Condition.HasAttachment(true)
        field_id.is_reply -> Condition.IsReply(true)
        field_id.is_forward -> Condition.IsForward(true)
        field_id.is_auto_submitted -> Condition.IsAutoSubmitted(true)
        field_id.has_calendar_invite -> Condition.HasCalendarInvite(true)
        field_id.has_list_id -> Condition.HasListId(true)
        else -> Condition.HasAttachment(true)
    }
    field_kind.numeric_size -> when (field) {
        field_id.attachment_size -> Condition.AttachmentSize(NumericOp.GREATER_THAN, 1024L * 1024L)
        field_id.total_size -> Condition.TotalSize(NumericOp.GREATER_THAN, 1024L * 1024L)
        else -> Condition.AttachmentSize(NumericOp.GREATER_THAN, 1024L * 1024L)
    }
    field_kind.numeric_plain -> when (field) {
        field_id.recipient_count -> Condition.RecipientCount(NumericOp.GREATER_THAN, 5L)
        field_id.spam_score -> Condition.SpamScore(NumericOp.GREATER_THAN, 5.0)
        else -> Condition.RecipientCount(NumericOp.GREATER_THAN, 5L)
    }
    field_kind.date -> Condition.DateReceived(DateOp.OLDER_THAN_DAYS, 7L)
    field_kind.auth -> when (field) {
        field_id.dkim_result -> Condition.DkimResult(AuthResult.PASS)
        field_id.spf_result -> Condition.SpfResult(AuthResult.PASS)
        field_id.dmarc_result -> Condition.DmarcResult(AuthResult.PASS)
        else -> Condition.DkimResult(AuthResult.PASS)
    }
}

enum class action_id {
    move_to, apply_labels, mark_as, star, skip_inbox, pin,
    snooze, categorize, notify, forward, delete, auto_reply,
}

val unavailable_actions = setOf(action_id.forward, action_id.auto_reply)

fun selectable_actions(): List<action_id> =
    action_id.values().filter { it !in unavailable_actions }

fun strip_unavailable_actions(list: List<Action>): List<Action> =
    list.filter { action_of(it).let { id -> id == null || id !in unavailable_actions } }

fun action_of(action: Action): action_id? = when (action) {
    Action.Unsupported -> null
    is Action.MoveTo -> action_id.move_to
    is Action.ApplyLabels -> action_id.apply_labels
    is Action.MarkAs -> action_id.mark_as
    Action.Star -> action_id.star
    Action.SkipInbox -> action_id.skip_inbox
    Action.Delete -> action_id.delete
    is Action.Forward -> action_id.forward
    is Action.AutoReply -> action_id.auto_reply
    Action.Pin -> action_id.pin
    is Action.Snooze -> action_id.snooze
    is Action.Categorize -> action_id.categorize
    is Action.Notify -> action_id.notify
}

fun apply_created_target(action: Action?, token: String): Action? = when {
    token.isBlank() -> null
    action is Action.MoveTo -> action.copy(folder_token = token)
    action is Action.ApplyLabels ->
        if (token in action.label_tokens) null else action.copy(label_tokens = action.label_tokens + token)
    else -> null
}

fun default_action_for(id: action_id): Action = when (id) {
    action_id.move_to -> Action.MoveTo(folder_token = "")
    action_id.apply_labels -> Action.ApplyLabels(label_tokens = emptyList())
    action_id.mark_as -> Action.MarkAs(value = ReadState.READ)
    action_id.star -> Action.Star
    action_id.skip_inbox -> Action.SkipInbox
    action_id.pin -> Action.Pin
    action_id.snooze -> Action.Snooze(until_iso8601 = "")
    action_id.categorize -> Action.Categorize(category = "primary")
    action_id.notify -> Action.Notify(enabled = true)
    action_id.forward -> Action.Forward(to = "")
    action_id.delete -> Action.Delete
    action_id.auto_reply -> Action.AutoReply(template_id = "")
}

const val regex_max_length = 512

@StringRes
fun regex_error_res(pattern: String): Int? {
    if (pattern.isEmpty()) return R.string.mail_rules_regex_empty
    if (pattern.length > regex_max_length) return R.string.mail_rules_regex_too_long

    var index = 0
    var in_class = false

    while (index < pattern.length) {
        val ch = pattern[index]

        if (ch == '\\') {
            val next = pattern.getOrNull(index + 1)

            if (!in_class && next != null && (next in '1'..'9' || next == 'k')) {
                return R.string.mail_rules_regex_backreference
            }
            index += 2
            continue
        }
        if (in_class) {
            if (ch == ']') in_class = false
            index += 1
            continue
        }
        if (ch == '[') {
            in_class = true
            index += 1
            continue
        }
        if (ch == '(' && pattern.getOrNull(index + 1) == '?') {
            val third = pattern.getOrNull(index + 2)

            if (third == '=' || third == '!') return R.string.mail_rules_regex_lookaround
            if (third == '<' && (pattern.getOrNull(index + 3) == '=' || pattern.getOrNull(index + 3) == '!')) {
                return R.string.mail_rules_regex_lookaround
            }
        }
        index += 1
    }

    return if (runCatching { Regex(pattern) }.isSuccess) null else R.string.mail_rules_regex_invalid
}

@StringRes
fun condition_regex_error(c: Condition): Int? = when (c) {
    is Condition.From -> if (c.op == AddressOp.MATCHES_REGEX) regex_error_res(c.value) else null
    is Condition.ReplyTo -> if (c.op == AddressOp.MATCHES_REGEX) regex_error_res(c.value) else null
    is Condition.To -> if (c.op == AddressOp.MATCHES_REGEX) regex_error_res(c.value) else null
    is Condition.Cc -> if (c.op == AddressOp.MATCHES_REGEX) regex_error_res(c.value) else null
    is Condition.Bcc -> if (c.op == AddressOp.MATCHES_REGEX) regex_error_res(c.value) else null
    is Condition.AnyRecipient -> if (c.op == AddressOp.MATCHES_REGEX) regex_error_res(c.value) else null
    is Condition.Subject -> if (c.op == TextOp.MATCHES_REGEX) regex_error_res(c.value) else null
    is Condition.Body -> if (c.op == TextOp.MATCHES_REGEX) regex_error_res(c.value) else null
    is Condition.ListId -> if (c.op == TextOp.MATCHES_REGEX) regex_error_res(c.value) else null
    is Condition.Header -> if (c.op == TextOp.MATCHES_REGEX) regex_error_res(c.value) else null
    is Condition.AttachmentName ->
        if (c.op == AttachmentNameOp.MATCHES_REGEX) regex_error_res(c.value) else null
    else -> null
}

fun is_condition_complete(c: Condition): Boolean =
    condition_regex_error(c) == null && has_condition_value(c)

private fun has_condition_value(c: Condition): Boolean = when (c) {
    is Condition.And, is Condition.Or, is Condition.Not, Condition.Unsupported -> false
    is Condition.From -> c.value.isNotBlank() || c.op == AddressOp.IS_EMPTY
    is Condition.ReplyTo -> c.value.isNotBlank() || c.op == AddressOp.IS_EMPTY
    is Condition.To -> c.value.isNotBlank() || c.op == AddressOp.IS_EMPTY
    is Condition.Cc -> c.value.isNotBlank() || c.op == AddressOp.IS_EMPTY
    is Condition.Bcc -> c.value.isNotBlank() || c.op == AddressOp.IS_EMPTY
    is Condition.AnyRecipient -> c.value.isNotBlank() || c.op == AddressOp.IS_EMPTY
    is Condition.Subject -> c.op == TextOp.IS_EMPTY || c.value.isNotBlank()
    is Condition.Body -> c.op == TextOp.IS_EMPTY || c.value.isNotBlank()
    is Condition.ListId -> c.op == TextOp.IS_EMPTY || c.value.isNotBlank()
    is Condition.Header -> c.name.isNotBlank() && (c.op == TextOp.IS_EMPTY || c.value.isNotBlank())
    is Condition.AttachmentName -> c.value.isNotBlank()
    else -> !condition_has_invalid_number(c)
}

fun is_valid_rfc3339(value: String): Boolean =
    value.isNotBlank() && runCatching { java.time.OffsetDateTime.parse(value) }.isSuccess

fun is_action_complete(a: Action): Boolean = when (a) {
    Action.Unsupported -> false
    is Action.MoveTo -> a.folder_token.isNotBlank()
    is Action.ApplyLabels -> a.label_tokens.isNotEmpty()
    is Action.Forward -> a.to.contains("@")
    is Action.Snooze -> is_valid_rfc3339(a.until_iso8601)
    is Action.AutoReply -> a.template_id.isNotBlank()
    else -> true
}

private fun exclusive_address_value(op: AddressOp, value: String): String? =
    if (op == AddressOp.IS || op == AddressOp.MATCHES_DOMAIN) value.trim().lowercase().takeIf { it.isNotBlank() } else null

private fun exclusive_text_value(op: TextOp, value: String): String? =
    if (op == TextOp.IS) value.trim().lowercase().takeIf { it.isNotBlank() } else null

private fun exclusive_signature(c: Condition): Pair<String, String>? {
    val field = field_of(c)?.name ?: return null
    val entry = when (c) {
        is Condition.From -> c.op.name to exclusive_address_value(c.op, c.value)
        is Condition.ReplyTo -> c.op.name to exclusive_address_value(c.op, c.value)
        is Condition.Subject -> c.op.name to exclusive_text_value(c.op, c.value)
        is Condition.Body -> c.op.name to exclusive_text_value(c.op, c.value)
        is Condition.ListId -> c.op.name to exclusive_text_value(c.op, c.value)
        else -> return null
    }
    val value = entry.second ?: return null
    return "$field:${entry.first}" to value
}

fun condition_duplicate_key(c: Condition): String? {
    val field = field_of(c)?.name ?: return null
    val entry = when (c) {
        is Condition.From -> c.op.name to c.value
        is Condition.ReplyTo -> c.op.name to c.value
        is Condition.To -> c.op.name to c.value
        is Condition.Cc -> c.op.name to c.value
        is Condition.Bcc -> c.op.name to c.value
        is Condition.AnyRecipient -> c.op.name to c.value
        is Condition.Subject -> c.op.name to c.value
        is Condition.Body -> c.op.name to c.value
        is Condition.ListId -> c.op.name to c.value
        is Condition.Header -> "${c.op.name}:${c.name.trim().lowercase()}" to c.value
        is Condition.AttachmentName -> c.op.name to c.value
        else -> return null
    }
    val value = entry.second.trim().lowercase().takeIf { it.isNotBlank() } ?: return null
    return "$field:${entry.first}:$value"
}

fun condition_is_address_field(c: Condition): Boolean = when (c) {
    is Condition.From, is Condition.ReplyTo, is Condition.To,
    is Condition.Cc, is Condition.Bcc, is Condition.AnyRecipient,
    -> true
    else -> false
}

fun address_op_of(c: Condition): AddressOp? = when (c) {
    is Condition.From -> c.op
    is Condition.ReplyTo -> c.op
    is Condition.To -> c.op
    is Condition.Cc -> c.op
    is Condition.Bcc -> c.op
    is Condition.AnyRecipient -> c.op
    else -> null
}

fun value_for_address_op(op: AddressOp, value: String): String =
    if (op == AddressOp.IS_EMPTY) "" else value

fun value_for_text_op(op: TextOp, value: String): String =
    if (op == TextOp.IS_EMPTY) "" else value

fun condition_offers_alias_picker(c: Condition): Boolean = when (address_op_of(c)) {
    AddressOp.IS, AddressOp.IS_NOT, AddressOp.CONTAINS -> true
    else -> false
}

private fun address_op_is_negated(c: Condition): Boolean = address_op_of(c) == AddressOp.IS_NOT

fun set_condition_value(c: Condition, value: String, case: Boolean): Condition = when (c) {
    is Condition.From -> c.copy(value = value, case_sensitive = case)
    is Condition.ReplyTo -> c.copy(value = value, case_sensitive = case)
    is Condition.To -> c.copy(value = value, case_sensitive = case)
    is Condition.Cc -> c.copy(value = value, case_sensitive = case)
    is Condition.Bcc -> c.copy(value = value, case_sensitive = case)
    is Condition.AnyRecipient -> c.copy(value = value, case_sensitive = case)
    is Condition.Subject -> c.copy(value = value, case_sensitive = case)
    is Condition.Body -> c.copy(value = value, case_sensitive = case)
    is Condition.ListId -> c.copy(value = value, case_sensitive = case)
    is Condition.AttachmentName -> c.copy(value = value, case_sensitive = case)
    else -> c
}

fun normalize_address_values(values: List<String>): List<String> {
    val seen = mutableSetOf<String>()
    return values.mapNotNull { raw ->
        val trimmed = raw.trim()
        if (trimmed.isEmpty()) null else if (seen.add(trimmed.lowercase())) trimmed else null
    }
}

data class value_insert_result(
    val conditions: List<Condition>,
    val match_mode: MatchMode,
    val inserted: Int,
    val skipped_duplicates: Int,
)

private fun switches_to_any(
    result: List<Condition>,
    template: Condition,
    inserted: Int,
    match_mode: MatchMode,
): Boolean {
    if (inserted < 2 || match_mode != MatchMode.ALL) return false
    if (address_op_is_negated(template)) return false
    val field = field_of(template) ?: return false
    return result.all { field_of(it) == field && !address_op_is_negated(it) }
}

fun insert_condition_values(
    conditions: List<Condition>,
    index: Int,
    template: Condition,
    values: List<String>,
    case: Boolean,
    match_mode: MatchMode,
): value_insert_result {
    val cleaned = normalize_address_values(values)
    if (cleaned.isEmpty()) {
        val blanked = conditions.toMutableList()
        if (index in blanked.indices) blanked[index] = set_condition_value(template, "", case)
        return value_insert_result(blanked, match_mode, 0, 0)
    }

    val kept = conditions.filterIndexed { i, _ -> i != index }
    val accepted = mutableListOf<Condition>()
    var skipped = 0
    cleaned.forEach { value ->
        val candidate = set_condition_value(template, value, case)
        val key = condition_duplicate_key(candidate)
        val clashes = key != null &&
            (kept.any { condition_duplicate_key(it) == key } ||
                accepted.any { condition_duplicate_key(it) == key })
        if (clashes) skipped += 1 else accepted.add(candidate)
    }

    if (accepted.isEmpty()) {
        val pruned = conditions.toMutableList()
        val target = pruned.getOrNull(index)
        if (target != null && !is_condition_complete(target)) pruned.removeAt(index)
        return value_insert_result(pruned, match_mode, 0, skipped)
    }

    val result = conditions.toMutableList()
    if (index in result.indices) result[index] = accepted.first() else result.add(accepted.first())
    result.addAll(accepted.drop(1))

    val next_mode = if (switches_to_any(result, template, accepted.size, match_mode)) {
        MatchMode.ANY
    } else {
        match_mode
    }
    return value_insert_result(result, next_mode, accepted.size, skipped)
}

fun duplicate_condition_indices(conditions: List<Condition>): Set<Int> {
    val seen = mutableSetOf<String>()
    val duplicates = mutableSetOf<Int>()
    conditions.forEachIndexed { index, c ->
        val key = condition_duplicate_key(c) ?: return@forEachIndexed
        if (!seen.add(key)) duplicates.add(index)
    }
    return duplicates
}

fun duplicates_condition_at(conditions: List<Condition>, index: Int, candidate: Condition): Boolean {
    val key = condition_duplicate_key(candidate) ?: return false
    return conditions.filterIndexed { i, _ -> i != index }
        .any { condition_duplicate_key(it) == key }
}

fun conditions_conflict_under_all(conditions: List<Condition>, match_mode: MatchMode): Boolean {
    if (match_mode != MatchMode.ALL || conditions.size < 2) return false
    val groups = mutableMapOf<String, MutableSet<String>>()
    conditions.forEach { c ->
        val signature = exclusive_signature(c) ?: return@forEach
        groups.getOrPut(signature.first) { mutableSetOf() }.add(signature.second)
    }
    return groups.values.any { it.size > 1 }
}

fun is_advanced_condition(c: Condition): Boolean = when (c) {
    is Condition.And, is Condition.Or, is Condition.Not, Condition.Unsupported -> true
    else -> false
}

fun is_advanced_action(a: Action): Boolean = a == Action.Unsupported

fun rule_is_advanced(rule: MailRule): Boolean =
    rule.conditions.any { is_advanced_condition(it) } || rule.actions.any { is_advanced_action(it) }

const val rule_summary_segment_limit = 2

fun rule_summary_line(
    when_label: String,
    then_label: String,
    joiner: String,
    conditions: List<String>,
    actions: List<String>,
    limit: Int = rule_summary_segment_limit,
): String {
    val parts = mutableListOf<String>()
    summary_part(when_label, conditions, " $joiner ", limit)?.let { parts.add(it) }
    summary_part(then_label, actions, ", ", limit)?.let { parts.add(it) }
    return parts.joinToString(" · ")
}

private fun summary_part(label: String, values: List<String>, separator: String, limit: Int): String? =
    summary_body(values, separator, limit)?.let { "$label $it" }

private fun summary_body(values: List<String>, separator: String, limit: Int): String? {
    val capped = limit.coerceAtLeast(1)
    val cleaned = values.map { it.trim() }.filter { it.isNotEmpty() }
    if (cleaned.isEmpty()) return null
    val shown = cleaned.take(capped).joinToString(separator)
    val hidden = cleaned.size - capped
    return if (hidden > 0) "$shown +$hidden" else shown
}

const val rule_arrow = "→"

fun rule_arrow_summary(
    conditions: List<String>,
    actions: List<String>,
    joiner: String,
    limit: Int = rule_summary_segment_limit,
): String = listOfNotNull(
    summary_body(conditions, " $joiner ", limit),
    summary_body(actions, ", ", limit),
).joinToString(" $rule_arrow ")

fun sort_rules_for_display(rules: List<MailRule>): List<MailRule> =
    rules.sortedWith(
        compareByDescending<MailRule> { it.enabled }
            .thenBy { it.name.trim().lowercase() },
    )

val palette_colors = listOf(
    "#6366F1", "#3B82F6", "#22C55E", "#F59E0B",
    "#EF4444", "#EC4899", "#A855F7", "#14B8A6",
    "#F97316", "#0EA5E9",
)

@Serializable
data class rule_draft(
    val name: String = "",
    val color: String = "",
    val match_mode: MatchMode = MatchMode.ALL,
    val conditions: List<Condition> = emptyList(),
    val actions: List<Action> = emptyList(),
)

private val rule_draft_json = Json {
    ignoreUnknownKeys = true
    encodeDefaults = true
    allowSpecialFloatingPointValues = true
}

fun encode_rule_draft(draft: rule_draft): String =
    rule_draft_json.encodeToString(rule_draft.serializer(), draft)

fun decode_rule_draft(raw: String?): rule_draft? {
    if (raw.isNullOrBlank()) return null
    return runCatching { rule_draft_json.decodeFromString(rule_draft.serializer(), raw) }.getOrNull()
}

fun rule_draft_of(rule: MailRule?, default_color: String): rule_draft =
    if (rule == null) {
        rule_draft(color = default_color)
    } else {
        rule_draft(
            name = rule.name,
            color = rule.color.ifBlank { default_color },
            match_mode = rule.match_mode,
            conditions = rule.conditions,
            actions = strip_unavailable_actions(rule.actions),
        )
    }

fun preserved_actions_of(rule: MailRule?): List<Action> =
    rule?.actions.orEmpty().filter { action_of(it) in unavailable_actions }

fun needs_value(c: Condition): Boolean = when (c) {
    is Condition.HasAttachment, is Condition.IsReply, is Condition.IsForward,
    is Condition.IsAutoSubmitted, is Condition.HasCalendarInvite, is Condition.HasListId,
    is Condition.DkimResult, is Condition.SpfResult, is Condition.DmarcResult,
    -> false
    is Condition.And, is Condition.Or, is Condition.Not, Condition.Unsupported -> false
    is Condition.Subject -> c.op != TextOp.IS_EMPTY
    is Condition.Body -> c.op != TextOp.IS_EMPTY
    is Condition.ListId -> c.op != TextOp.IS_EMPTY
    is Condition.Header -> c.op != TextOp.IS_EMPTY
    else -> address_op_of(c) != AddressOp.IS_EMPTY
}

fun current_operator_id(c: Condition): String? = when (c) {
    is Condition.From -> c.op.name
    is Condition.ReplyTo -> c.op.name
    is Condition.To -> c.op.name
    is Condition.Cc -> c.op.name
    is Condition.Bcc -> c.op.name
    is Condition.AnyRecipient -> c.op.name
    is Condition.Subject -> c.op.name
    is Condition.Body -> c.op.name
    is Condition.ListId -> c.op.name
    is Condition.Header -> c.op.name
    is Condition.AttachmentName -> c.op.name
    is Condition.AttachmentSize -> c.op.name
    is Condition.TotalSize -> c.op.name
    is Condition.RecipientCount -> c.op.name
    is Condition.SpamScore -> c.op.name
    is Condition.DateReceived -> c.op.name
    else -> null
}

fun operator_ids_for(c: Condition): List<String> = when (field_of(c)?.let { field_kind_of(it) }) {
    field_kind.address -> AddressOp.entries.map { it.name }
    field_kind.text, field_kind.header -> TextOp.entries.map { it.name }
    field_kind.attachment_name -> AttachmentNameOp.entries.map { it.name }
    field_kind.numeric_size, field_kind.numeric_plain -> NumericOp.entries.map { it.name }
    field_kind.date -> DateOp.entries.map { it.name }
    else -> emptyList()
}

fun apply_operator(c: Condition, op_id: String): Condition {
    if (op_id !in operator_ids_for(c)) return c
    return when (c) {
        is Condition.From -> AddressOp.valueOf(op_id).let { c.copy(op = it, value = value_for_address_op(it, c.value)) }
        is Condition.ReplyTo -> AddressOp.valueOf(op_id).let { c.copy(op = it, value = value_for_address_op(it, c.value)) }
        is Condition.To -> AddressOp.valueOf(op_id).let { c.copy(op = it, value = value_for_address_op(it, c.value)) }
        is Condition.Cc -> AddressOp.valueOf(op_id).let { c.copy(op = it, value = value_for_address_op(it, c.value)) }
        is Condition.Bcc -> AddressOp.valueOf(op_id).let { c.copy(op = it, value = value_for_address_op(it, c.value)) }
        is Condition.AnyRecipient -> AddressOp.valueOf(op_id).let { c.copy(op = it, value = value_for_address_op(it, c.value)) }
        is Condition.Subject -> TextOp.valueOf(op_id).let { c.copy(op = it, value = value_for_text_op(it, c.value)) }
        is Condition.Body -> TextOp.valueOf(op_id).let { c.copy(op = it, value = value_for_text_op(it, c.value)) }
        is Condition.ListId -> TextOp.valueOf(op_id).let { c.copy(op = it, value = value_for_text_op(it, c.value)) }
        is Condition.Header -> TextOp.valueOf(op_id).let { c.copy(op = it, value = value_for_text_op(it, c.value)) }
        is Condition.AttachmentName -> c.copy(op = AttachmentNameOp.valueOf(op_id))
        is Condition.AttachmentSize -> c.copy(op = NumericOp.valueOf(op_id))
        is Condition.TotalSize -> c.copy(op = NumericOp.valueOf(op_id))
        is Condition.RecipientCount -> c.copy(op = NumericOp.valueOf(op_id))
        is Condition.SpamScore -> c.copy(op = NumericOp.valueOf(op_id))
        is Condition.DateReceived -> c.copy(op = DateOp.valueOf(op_id))
        else -> c
    }
}

fun uses_regex_op(c: Condition): Boolean =
    current_operator_id(c) == AddressOp.MATCHES_REGEX.name

fun supports_case_sensitive(c: Condition): Boolean = when (field_of(c)?.let { field_kind_of(it) }) {
    field_kind.address, field_kind.text, field_kind.header, field_kind.attachment_name -> true
    else -> false
}

private fun case_flag_of(c: Condition): Boolean? = when (c) {
    is Condition.From -> c.case_sensitive
    is Condition.ReplyTo -> c.case_sensitive
    is Condition.To -> c.case_sensitive
    is Condition.Cc -> c.case_sensitive
    is Condition.Bcc -> c.case_sensitive
    is Condition.AnyRecipient -> c.case_sensitive
    is Condition.Subject -> c.case_sensitive
    is Condition.Body -> c.case_sensitive
    is Condition.ListId -> c.case_sensitive
    is Condition.Header -> c.case_sensitive
    is Condition.AttachmentName -> c.case_sensitive
    else -> null
}

fun case_sensitive_of(c: Condition): Boolean = case_flag_of(c) == true

fun set_case_sensitive(c: Condition, case: Boolean?): Condition = when (c) {
    is Condition.From -> c.copy(case_sensitive = case)
    is Condition.ReplyTo -> c.copy(case_sensitive = case)
    is Condition.To -> c.copy(case_sensitive = case)
    is Condition.Cc -> c.copy(case_sensitive = case)
    is Condition.Bcc -> c.copy(case_sensitive = case)
    is Condition.AnyRecipient -> c.copy(case_sensitive = case)
    is Condition.Subject -> c.copy(case_sensitive = case)
    is Condition.Body -> c.copy(case_sensitive = case)
    is Condition.ListId -> c.copy(case_sensitive = case)
    is Condition.Header -> c.copy(case_sensitive = case)
    is Condition.AttachmentName -> c.copy(case_sensitive = case)
    else -> c
}

fun condition_text_value(c: Condition): String = when (c) {
    is Condition.From -> c.value
    is Condition.ReplyTo -> c.value
    is Condition.To -> c.value
    is Condition.Cc -> c.value
    is Condition.Bcc -> c.value
    is Condition.AnyRecipient -> c.value
    is Condition.Subject -> c.value
    is Condition.Body -> c.value
    is Condition.ListId -> c.value
    is Condition.Header -> c.value
    is Condition.AttachmentName -> c.value
    else -> ""
}

fun set_condition_text(c: Condition, value: String): Condition = when (c) {
    is Condition.Header -> c.copy(value = value)
    else -> set_case_sensitive(set_condition_value(c, value, false), case_flag_of(c))
}

private fun address_condition(field: field_id, op: AddressOp, value: String, case: Boolean?): Condition? = when (field) {
    field_id.from -> Condition.From(op, value, case)
    field_id.reply_to -> Condition.ReplyTo(op, value, case)
    field_id.to -> Condition.To(op, value, case)
    field_id.cc -> Condition.Cc(op, value, case)
    field_id.bcc -> Condition.Bcc(op, value, case)
    field_id.any_recipient -> Condition.AnyRecipient(op, value, case)
    else -> null
}

private fun text_condition(field: field_id, op: TextOp, value: String, case: Boolean?): Condition? = when (field) {
    field_id.subject -> Condition.Subject(op, value, case)
    field_id.body -> Condition.Body(op, value, case)
    field_id.list_id -> Condition.ListId(op, value, case)
    else -> null
}

fun switch_condition_field(c: Condition, field: field_id): Condition {
    if (field_of(c) == field) return c
    val case = case_flag_of(c)
    val address_op = address_op_of(c)
    if (address_op != null && field_kind_of(field) == field_kind.address) {
        return address_condition(field, address_op, condition_text_value(c), case) ?: default_condition_for(field)
    }
    val text_op = when (c) {
        is Condition.Subject -> c.op
        is Condition.Body -> c.op
        is Condition.ListId -> c.op
        else -> null
    }
    if (text_op != null && field_kind_of(field) == field_kind.text) {
        return text_condition(field, text_op, condition_text_value(c), case) ?: default_condition_for(field)
    }
    return default_condition_for(field)
}

const val invalid_number = -1L
const val number_max_length = 15

enum class size_unit(val factor: Long) {
    bytes(1L),
    kb(1024L),
    mb(1024L * 1024L),
    gb(1024L * 1024L * 1024L),
}

fun split_size(bytes: Long): Pair<Long, size_unit> {
    if (bytes <= 0L) return 0L to size_unit.mb
    val unit = size_unit.entries.reversed().first { bytes % it.factor == 0L }
    return bytes / unit.factor to unit
}

fun join_size(amount: Long, unit: size_unit): Long = when {
    amount < 0L -> invalid_number
    amount > Long.MAX_VALUE / unit.factor -> invalid_number
    else -> amount * unit.factor
}

fun parse_whole_number(text: String): Long {
    val trimmed = text.trim()
    if (trimmed.isEmpty() || !trimmed.all { it.isDigit() }) return invalid_number
    return trimmed.toLongOrNull() ?: invalid_number
}

fun score_input(raw: String): String {
    val negative = raw.trimStart().startsWith('-')
    val body = raw.filter { it.isDigit() || it == '.' || it == ',' }
    return ((if (negative) "-" else "") + body).take(number_max_length)
}

fun parse_score(text: String): Double =
    text.trim().replace(',', '.').toDoubleOrNull()?.takeIf { it.isFinite() } ?: Double.NaN

fun condition_has_invalid_number(c: Condition): Boolean = when (c) {
    is Condition.AttachmentSize -> c.value < 0L
    is Condition.TotalSize -> c.value < 0L
    is Condition.RecipientCount -> c.value < 0L
    is Condition.DateReceived -> c.value < 0L
    is Condition.SpamScore -> !c.value.isFinite()
    else -> false
}

enum class rule_save_blocker {
    rule_name, add_condition, header_name, number, finish_condition, duplicate,
    add_action, folder, labels, snooze, finish_action,
}

fun rule_save_blocker_of(draft: rule_draft, preserved_action_count: Int = 0): rule_save_blocker? = when {
    draft.name.isBlank() -> rule_save_blocker.rule_name
    draft.conditions.isEmpty() -> rule_save_blocker.add_condition
    draft.conditions.any { it is Condition.Header && it.name.isBlank() } -> rule_save_blocker.header_name
    draft.conditions.any { condition_has_invalid_number(it) } -> rule_save_blocker.number
    draft.conditions.any { !is_condition_complete(it) } -> rule_save_blocker.finish_condition
    duplicate_condition_indices(draft.conditions).isNotEmpty() -> rule_save_blocker.duplicate
    draft.actions.isEmpty() && preserved_action_count == 0 -> rule_save_blocker.add_action
    draft.actions.any { it is Action.MoveTo && it.folder_token.isBlank() } -> rule_save_blocker.folder
    draft.actions.any { it is Action.ApplyLabels && it.label_tokens.isEmpty() } -> rule_save_blocker.labels
    draft.actions.any { it is Action.Snooze && !is_valid_rfc3339(it.until_iso8601) } -> rule_save_blocker.snooze
    draft.actions.any { !is_action_complete(it) } -> rule_save_blocker.finish_action
    else -> null
}

@StringRes
fun rule_save_blocker_res(blocker: rule_save_blocker): Int = when (blocker) {
    rule_save_blocker.rule_name -> R.string.rules_hint_name
    rule_save_blocker.add_condition -> R.string.rules_hint_add_condition
    rule_save_blocker.header_name -> R.string.rules_hint_header_name
    rule_save_blocker.number -> R.string.rules_hint_number
    rule_save_blocker.finish_condition -> R.string.rules_hint_finish_condition
    rule_save_blocker.duplicate -> R.string.rules_hint_duplicate
    rule_save_blocker.add_action -> R.string.rules_hint_add_action
    rule_save_blocker.folder -> R.string.rules_hint_folder
    rule_save_blocker.labels -> R.string.rules_hint_labels
    rule_save_blocker.snooze -> R.string.rules_hint_snooze
    rule_save_blocker.finish_action -> R.string.rules_hint_finish_action
}

fun replace_condition(draft: rule_draft, index: Int, updated: Condition): rule_draft =
    if (index !in draft.conditions.indices) {
        draft
    } else {
        draft.copy(conditions = draft.conditions.toMutableList().also { it[index] = updated })
    }

fun remove_condition(draft: rule_draft, index: Int): rule_draft =
    if (index !in draft.conditions.indices) {
        draft
    } else {
        draft.copy(conditions = draft.conditions.filterIndexed { i, _ -> i != index })
    }

fun replace_action(draft: rule_draft, index: Int, updated: Action): rule_draft =
    if (index !in draft.actions.indices) {
        draft
    } else {
        draft.copy(actions = draft.actions.toMutableList().also { it[index] = updated })
    }

fun remove_action(draft: rule_draft, index: Int): rule_draft =
    if (index !in draft.actions.indices) {
        draft
    } else {
        draft.copy(actions = draft.actions.filterIndexed { i, _ -> i != index })
    }

fun used_action_kinds(actions: List<Action>, except_index: Int? = null): Set<action_id> =
    actions.filterIndexed { i, _ -> i != except_index }.mapNotNull { action_of(it) }.toSet()

fun toggle_label_token(action: Action.ApplyLabels, token: String): Action.ApplyLabels =
    if (token in action.label_tokens) {
        action.copy(label_tokens = action.label_tokens - token)
    } else {
        action.copy(label_tokens = action.label_tokens + token)
    }

fun snooze_until_from_now(duration: java.time.Duration, now: java.time.OffsetDateTime): String =
    now.withOffsetSameInstant(java.time.ZoneOffset.UTC)
        .plus(duration)
        .format(java.time.format.DateTimeFormatter.ISO_OFFSET_DATE_TIME)

fun condition_flag_of(c: Condition): Boolean? = when (c) {
    is Condition.HasAttachment -> c.`is`
    is Condition.IsReply -> c.`is`
    is Condition.IsForward -> c.`is`
    is Condition.IsAutoSubmitted -> c.`is`
    is Condition.HasCalendarInvite -> c.`is`
    is Condition.HasListId -> c.`is`
    else -> null
}

fun set_condition_flag(c: Condition, value: Boolean): Condition = when (c) {
    is Condition.HasAttachment -> c.copy(`is` = value)
    is Condition.IsReply -> c.copy(`is` = value)
    is Condition.IsForward -> c.copy(`is` = value)
    is Condition.IsAutoSubmitted -> c.copy(`is` = value)
    is Condition.HasCalendarInvite -> c.copy(`is` = value)
    is Condition.HasListId -> c.copy(`is` = value)
    else -> c
}

fun auth_result_of(c: Condition): AuthResult? = when (c) {
    is Condition.DkimResult -> c.value
    is Condition.SpfResult -> c.value
    is Condition.DmarcResult -> c.value
    else -> null
}

fun set_auth_result(c: Condition, value: AuthResult): Condition = when (c) {
    is Condition.DkimResult -> c.copy(value = value)
    is Condition.SpfResult -> c.copy(value = value)
    is Condition.DmarcResult -> c.copy(value = value)
    else -> c
}

fun condition_number_of(c: Condition): Long? = when (c) {
    is Condition.AttachmentSize -> c.value
    is Condition.TotalSize -> c.value
    is Condition.RecipientCount -> c.value
    is Condition.DateReceived -> c.value
    else -> null
}

fun set_condition_number(c: Condition, value: Long): Condition = when (c) {
    is Condition.AttachmentSize -> c.copy(value = value)
    is Condition.TotalSize -> c.copy(value = value)
    is Condition.RecipientCount -> c.copy(value = value)
    is Condition.DateReceived -> c.copy(value = value)
    else -> c
}

fun whole_number_text(value: Long): String = if (value < 0L) "" else value.toString()

fun score_text(value: Double): String = when {
    !value.isFinite() -> ""
    value % 1.0 == 0.0 && kotlin.math.abs(value) < 1e15 -> value.toLong().toString()
    else -> value.toString()
}

fun score_matches(text: String, value: Double): Boolean {
    val parsed = parse_score(text)
    return if (parsed.isNaN()) value.isNaN() else parsed == value
}

fun size_text_matches(text: String, unit: size_unit, bytes: Long): Boolean =
    join_size(parse_whole_number(text), unit) == bytes

fun size_text_of(bytes: Long): String = if (bytes < 0L) "" else split_size(bytes).first.toString()

fun is_dirty(initial_json: String?, draft_json: String?): Boolean =
    draft_json != null && initial_json != null && draft_json != initial_json
