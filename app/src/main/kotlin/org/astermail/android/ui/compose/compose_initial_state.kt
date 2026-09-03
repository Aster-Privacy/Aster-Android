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

package org.astermail.android.ui.compose

data class compose_screen_args(
    val reply_to: String? = null,
    val mode: String? = null,
    val draft_id: String? = null,
    val prefill_to: String? = null,
    val thread_ghost_email: String? = null,
    val share_to: List<String> = emptyList(),
    val share_cc: List<String> = emptyList(),
    val share_bcc: List<String> = emptyList(),
    val share_subject: String = "",
)

data class compose_identity_snapshot(
    val user_email: String = "",
    val display_name: String = "",
    val alias_options: List<String> = emptyList(),
    val primary_sender_email: String = "",
    val alias_display_names: Map<String, String> = emptyMap(),
    val ghost_addresses: List<String> = emptyList(),
) {
    val is_ready: Boolean
        get() = user_email.isNotBlank() && alias_options.isNotEmpty()
}

data class compose_thread_message(
    val id: String,
    val sender_email: String = "",
    val display_sender_email: String? = null,
    val to_addresses: List<String> = emptyList(),
    val cc_addresses: List<String> = emptyList(),
    val subject: String = "",
    val body_html: String = "",
    val body_text: String = "",
    val timestamp: String = "",
    val raw_headers: List<Pair<String, String>> = emptyList(),
    val delivered_to: String? = null,
    val is_sent: Boolean = false,
)

data class compose_thread_snapshot(
    val item_id: String? = null,
    val item_subject: String = "",
    val messages: List<compose_thread_message> = emptyList(),
) {
    fun covers(target_id: String?): Boolean {
        if (messages.isEmpty()) return false
        val target = target_id?.trim().orEmpty()
        if (target.isEmpty()) return false
        return item_id == target || messages.any { it.id == target }
    }
}

data class compose_initial_state(
    val from_address: String = "",
    val from_source_tier: Int = from_tier_fallback,
    val sender_display_name: String = "",
    val to_chips: List<String> = emptyList(),
    val cc_chips: List<String> = emptyList(),
    val bcc_chips: List<String> = emptyList(),
    val subject: String = "",
    val quoted_html: String? = null,
    val quoted_sender: String = "",
    val quoted_timestamp: String = "",
    val quoted_subject: String = "",
    val received_on_alias: String? = null,
    val thread_ghost_match: String? = null,
    val alias_options: List<String> = emptyList(),
    val identity_is_skeleton: Boolean = true,
    val thread_is_skeleton: Boolean = true,
) {
    val is_complete: Boolean
        get() = !identity_is_skeleton && !thread_is_skeleton
}

private fun apply_subject_prefix(original: String, mode: String): String {
    val trimmed = original.trim()
    return when (mode) {
        "forward" -> if (trimmed.startsWith("Fwd:", ignoreCase = true)) trimmed else "Fwd: $trimmed"
        else -> if (trimmed.startsWith("Re:", ignoreCase = true)) trimmed else "Re: $trimmed"
    }
}

fun resolve_thread_ghost_match(
    thread_ghost_email: String?,
    ghost_addresses: List<String>,
): String? {
    val target = thread_ghost_email?.trim()?.lowercase()?.takeIf { it.isNotEmpty() } ?: return null
    return ghost_addresses.firstOrNull { it.lowercase() == target } ?: target
}

fun split_address_list(value: String): List<String> =
    value.split(",").map { it.trim() }.filter { it.isNotEmpty() }

fun build_compose_initial_state(
    args: compose_screen_args,
    identity: compose_identity_snapshot,
    thread: compose_thread_snapshot,
    effective_mode: String? = args.mode,
): compose_initial_state {
    val alias_options = identity.alias_options
    val user_email = identity.user_email
    val ghost_match = resolve_thread_ghost_match(args.thread_ghost_email, identity.ghost_addresses)
    val is_thread_mode = !args.reply_to.isNullOrBlank() && !effective_mode.isNullOrBlank() &&
        effective_mode != "new" && effective_mode != "draft"
    val target = if (is_thread_mode) {
        thread.messages.firstOrNull { it.id == args.reply_to } ?: thread.messages.lastOrNull()
    } else {
        null
    }
    val thread_ready = !is_thread_mode || (thread.covers(args.reply_to) && target != null)

    val received_on = if (!is_thread_mode || target == null) {
        null
    } else {
        val newest = thread.messages.firstOrNull { it.id == args.reply_to }
            ?: thread.messages.filterNot { it.is_sent }.lastOrNull()
            ?: thread.messages.lastOrNull()
        val recipients = listOfNotNull(newest?.delivered_to) +
            (newest?.to_addresses ?: emptyList()) +
            (newest?.cc_addresses ?: emptyList())
        compute_received_on_alias(recipients, alias_options, user_email)
    }

    val from_resolution = resolve_from_alias_tiered(
        received_on,
        ghost_match,
        identity.primary_sender_email,
        alias_options,
    )
    val from_address = from_resolution.address

    val display_name = if (from_address.isNotBlank() && from_address != user_email) {
        identity.alias_display_names[from_address]?.trim().orEmpty()
    } else {
        identity.display_name.trim()
    }

    val own_addresses = (listOf(user_email) + alias_options).filter { it.isNotBlank() }
    val me = user_email.lowercase()

    val reply_target = target?.let { msg ->
        build_reply_recipient(
            sender_email = msg.sender_email,
            first_to = msg.to_addresses.firstOrNull(),
            reply_to = extract_reply_to(msg.raw_headers),
            display_sender_email = msg.display_sender_email,
            own_addresses = own_addresses,
            is_own_message = msg.is_sent,
        )
    }

    val thread_to = when {
        target == null || reply_target == null -> emptyList()
        effective_mode == "forward" -> emptyList()
        effective_mode == "reply_all" -> {
            val all = mutableListOf(reply_target)
            target.to_addresses
                .filter { it.lowercase() != me && it !in all }
                .forEach { all.add(it) }
            all
        }
        else -> listOf(reply_target)
    }.filter { it.isNotBlank() }

    val thread_cc = if (target != null && effective_mode == "reply_all") {
        target.cc_addresses.filter { it.lowercase() != me && it !in thread_to }
    } else {
        emptyList()
    }

    val to_chips = when {
        args.share_to.isNotEmpty() -> args.share_to
        !args.prefill_to.isNullOrBlank() -> split_address_list(args.prefill_to)
        else -> thread_to
    }

    val original_subject = if (target == null) {
        thread.item_subject
    } else {
        sequenceOf(
            target.subject,
            thread.item_subject,
            thread.messages.firstNotNullOfOrNull { m -> m.subject.takeIf { it.isNotBlank() } }.orEmpty(),
        ).firstOrNull { it.isNotBlank() }.orEmpty()
    }

    val subject = when {
        args.share_subject.isNotBlank() -> args.share_subject
        is_thread_mode && effective_mode != null && (target != null || original_subject.isNotBlank()) ->
            apply_subject_prefix(original_subject, effective_mode)
        else -> ""
    }

    val quoted_html = target?.let { msg ->
        msg.body_html.takeIf { it.isNotBlank() } ?: msg.body_text.replace("\n", "<br>")
    }

    return compose_initial_state(
        from_address = from_address,
        from_source_tier = from_resolution.tier,
        sender_display_name = display_name,
        to_chips = to_chips,
        cc_chips = if (args.share_cc.isNotEmpty()) args.share_cc else thread_cc,
        bcc_chips = args.share_bcc,
        subject = subject,
        quoted_html = quoted_html,
        quoted_sender = target?.let { it.display_sender_email ?: it.sender_email }.orEmpty(),
        quoted_timestamp = target?.timestamp.orEmpty(),
        quoted_subject = thread.item_subject,
        received_on_alias = received_on,
        thread_ghost_match = ghost_match,
        alias_options = alias_options,
        identity_is_skeleton = !identity.is_ready,
        thread_is_skeleton = !thread_ready,
    )
}
