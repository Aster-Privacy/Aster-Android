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

package org.astermail.android.mail_rules

import org.astermail.android.api.mail_rules.Action
import org.astermail.android.api.mail_rules.AddressOp
import org.astermail.android.api.mail_rules.Condition
import org.astermail.android.api.mail_rules.MailRule

data class AliasRuleDelivery(
    val rule_id: String,
    val rule_name: String,
    val folder_token: String,
)

data class AliasRuleLabel(
    val rule_id: String,
    val rule_name: String,
    val label_tokens: List<String>,
)

fun rule_move_to_folder(rule: MailRule): String? =
    rule.actions.filterIsInstance<Action.MoveTo>().lastOrNull()?.folder_token

fun rule_apply_labels(rule: MailRule): List<String> =
    rule.actions.filterIsInstance<Action.ApplyLabels>().lastOrNull()
        ?.label_tokens
        ?.filter { it.isNotBlank() }
        .orEmpty()

fun condition_targets_address(condition: Condition, address: String): Boolean = when (condition) {
    is Condition.To -> address_condition_matches(condition.op, condition.value, address)
    is Condition.Cc -> address_condition_matches(condition.op, condition.value, address)
    is Condition.Bcc -> address_condition_matches(condition.op, condition.value, address)
    is Condition.AnyRecipient -> address_condition_matches(condition.op, condition.value, address)
    is Condition.And -> condition.conditions.any { condition_targets_address(it, address) }
    is Condition.Or -> condition.conditions.any { condition_targets_address(it, address) }
    else -> false
}

fun rule_targets_address(rule: MailRule, address: String): Boolean =
    rule.conditions.any { condition_targets_address(it, address) }

private fun address_condition_matches(op: AddressOp, value: String, address: String): Boolean {
    val needle = value.trim()
    if (needle.isBlank()) return false
    return when (op) {
        AddressOp.IS -> needle.equals(address, ignoreCase = true)
        AddressOp.CONTAINS -> address.contains(needle, ignoreCase = true)
        AddressOp.MATCHES_DOMAIN -> address.substringAfterLast('@').equals(needle.removePrefix("@"), ignoreCase = true)
        else -> false
    }
}

data class AliasDeliverySetting(
    val delivery_folder_token: String?,
    val delivery_label_token: String?,
    val never_inbox: Boolean,
)

data class AliasDeliveryConflict(
    val alias_address: String,
    val alias_delivery: AliasDeliverySetting,
    val rule_folder_token: String,
)

data class AliasLabelConflict(
    val alias_address: String,
    val alias_label_token: String,
    val rule_label_tokens: List<String>,
)

fun condition_exact_addresses(condition: Condition): List<String> = when (condition) {
    is Condition.To -> exact_address(condition.op, condition.value)
    is Condition.Cc -> exact_address(condition.op, condition.value)
    is Condition.Bcc -> exact_address(condition.op, condition.value)
    is Condition.AnyRecipient -> exact_address(condition.op, condition.value)
    is Condition.And -> condition.conditions.flatMap { condition_exact_addresses(it) }
    is Condition.Or -> condition.conditions.flatMap { condition_exact_addresses(it) }
    else -> emptyList()
}

private fun exact_address(op: AddressOp, value: String): List<String> {
    if (op != AddressOp.IS) return emptyList()
    val address = value.trim()
    return if (address.contains('@')) listOf(address) else emptyList()
}

fun rule_alias_delivery_conflict(
    conditions: List<Condition>,
    actions: List<Action>,
    alias_delivery: Map<String, AliasDeliverySetting>,
): AliasDeliveryConflict? {
    val rule_folder = actions.filterIsInstance<Action.MoveTo>().lastOrNull()?.folder_token ?: return null
    conditions.flatMap { condition_exact_addresses(it) }.forEach { address ->
        val delivery = alias_delivery[address.lowercase()] ?: return@forEach
        val has_explicit_target = delivery.delivery_folder_token != null || delivery.never_inbox
        if (!has_explicit_target) return@forEach
        if (delivery.delivery_folder_token == rule_folder) return@forEach
        return AliasDeliveryConflict(
            alias_address = address,
            alias_delivery = delivery,
            rule_folder_token = rule_folder,
        )
    }
    return null
}

fun alias_rule_delivery(rules: List<MailRule>, alias_address: String): AliasRuleDelivery? {
    val address = alias_address.trim()
    if (address.isBlank() || !address.contains('@')) return null
    return rules
        .filter { it.enabled }
        .sortedWith(compareBy({ it.priority }, { it.created_at.orEmpty() }))
        .mapNotNull { rule ->
            val folder = rule_move_to_folder(rule) ?: return@mapNotNull null
            if (!rule_targets_address(rule, address)) return@mapNotNull null
            AliasRuleDelivery(rule_id = rule.id, rule_name = rule.name, folder_token = folder)
        }
        .lastOrNull()
}

fun rule_alias_label_conflict(
    conditions: List<Condition>,
    actions: List<Action>,
    alias_delivery: Map<String, AliasDeliverySetting>,
): AliasLabelConflict? {
    val rule_labels = actions.filterIsInstance<Action.ApplyLabels>().lastOrNull()
        ?.label_tokens
        ?.filter { it.isNotBlank() }
        .orEmpty()
    if (rule_labels.isEmpty()) return null
    conditions.flatMap { condition_exact_addresses(it) }.forEach { address ->
        val label_token = alias_delivery[address.lowercase()]?.delivery_label_token ?: return@forEach
        if (rule_labels.contains(label_token)) return@forEach
        return AliasLabelConflict(
            alias_address = address,
            alias_label_token = label_token,
            rule_label_tokens = rule_labels,
        )
    }
    return null
}

fun alias_rule_label(rules: List<MailRule>, alias_address: String): AliasRuleLabel? {
    val address = alias_address.trim()
    if (address.isBlank() || !address.contains('@')) return null
    return rules
        .filter { it.enabled }
        .sortedWith(compareBy({ it.priority }, { it.created_at.orEmpty() }))
        .mapNotNull { rule ->
            val labels = rule_apply_labels(rule)
            if (labels.isEmpty()) return@mapNotNull null
            if (!rule_targets_address(rule, address)) return@mapNotNull null
            AliasRuleLabel(rule_id = rule.id, rule_name = rule.name, label_tokens = labels)
        }
        .lastOrNull()
}
