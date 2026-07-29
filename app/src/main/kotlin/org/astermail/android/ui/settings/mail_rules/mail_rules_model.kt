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
        field_id.attachment_size -> Condition.AttachmentSize(NumericOp.GREATER_THAN, 1_000_000L)
        field_id.total_size -> Condition.TotalSize(NumericOp.GREATER_THAN, 1_000_000L)
        else -> Condition.AttachmentSize(NumericOp.GREATER_THAN, 1_000_000L)
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
    is Condition.From -> c.value.isNotBlank() || c.op == AddressOp.IS_NOT
    is Condition.ReplyTo -> c.value.isNotBlank() || c.op == AddressOp.IS_NOT
    is Condition.To -> c.value.isNotBlank() || c.op == AddressOp.IS_NOT
    is Condition.Cc -> c.value.isNotBlank() || c.op == AddressOp.IS_NOT
    is Condition.Bcc -> c.value.isNotBlank() || c.op == AddressOp.IS_NOT
    is Condition.AnyRecipient -> c.value.isNotBlank() || c.op == AddressOp.IS_NOT
    is Condition.Subject -> c.op == TextOp.IS_EMPTY || c.value.isNotBlank()
    is Condition.Body -> c.op == TextOp.IS_EMPTY || c.value.isNotBlank()
    is Condition.ListId -> c.op == TextOp.IS_EMPTY || c.value.isNotBlank()
    is Condition.Header -> c.name.isNotBlank() && (c.op == TextOp.IS_EMPTY || c.value.isNotBlank())
    is Condition.AttachmentName -> c.value.isNotBlank()
    else -> true
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
        is Condition.To -> c.op.name to exclusive_address_value(c.op, c.value)
        is Condition.Cc -> c.op.name to exclusive_address_value(c.op, c.value)
        is Condition.Bcc -> c.op.name to exclusive_address_value(c.op, c.value)
        is Condition.AnyRecipient -> c.op.name to exclusive_address_value(c.op, c.value)
        is Condition.Subject -> c.op.name to exclusive_text_value(c.op, c.value)
        is Condition.Body -> c.op.name to exclusive_text_value(c.op, c.value)
        is Condition.ListId -> c.op.name to exclusive_text_value(c.op, c.value)
        else -> return null
    }
    val value = entry.second ?: return null
    return "$field:${entry.first}" to value
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

val palette_colors = listOf(
    "#6366F1", "#3B82F6", "#22C55E", "#F59E0B",
    "#EF4444", "#EC4899", "#A855F7", "#14B8A6",
    "#F97316", "#0EA5E9",
)
