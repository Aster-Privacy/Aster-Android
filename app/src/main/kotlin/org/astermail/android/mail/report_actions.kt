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

package org.astermail.android.mail

private val unreportable_mail_folders = setOf("sent", "drafts", "scheduled", "outbox")

fun folder_offers_report_actions(folder: String): Boolean = folder !in unreportable_mail_folders

fun is_reportable_item_type(item_type: String?): Boolean = (item_type ?: "received") == "received"

val InboxItem.is_reportable: Boolean
    get() = is_reportable_item_type(raw_item.item_type ?: raw_item.metadata?.item_type)

fun selection_offers_report_actions(folder: String, picked: Collection<InboxItem>): Boolean =
    folder_offers_report_actions(folder) && (picked.isEmpty() || picked.any { it.is_reportable })

class ReportSelection(val ids: List<String>, val sender_emails: List<String>)

fun report_selection_of(ids: List<String>, known: Map<String, InboxItem>): ReportSelection {
    val reportable = ids.distinct().filter { known[it]?.is_reportable ?: true }
    val senders = reportable
        .mapNotNull { known[it]?.sender_email?.takeIf { email -> email.isNotBlank() } }
        .distinct()
    return ReportSelection(reportable, senders)
}

fun unmark_spam_selection_of(ids: List<String>, known: Map<String, InboxItem>): ReportSelection {
    val unique = ids.distinct()
    val senders = unique
        .mapNotNull { known[it]?.sender_email?.takeIf { email -> email.isNotBlank() } }
        .distinct()
    return ReportSelection(unique, senders)
}

fun selection_action_needs_ids(action_id: String): Boolean = action_id == "spam" || action_id == "phishing"

fun gather_selection_ids(
    loaded: List<String>,
    scope_selection: Boolean,
    collect_scope: ((List<String>) -> Unit) -> Unit,
    on_ready: (List<String>) -> Unit,
) {
    if (!scope_selection) {
        on_ready(loaded)
        return
    }
    collect_scope { ids -> on_ready((loaded + ids).distinct()) }
}
