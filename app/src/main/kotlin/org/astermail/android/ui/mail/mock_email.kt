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

package org.astermail.android.ui.mail

import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import org.astermail.android.R
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

data class Email(
    val id: String,
    val sender_name: String,
    val sender_email: String,
    val subject: String,
    val preview: String,
    val received_at: Long,
    val is_read: Boolean,
    val is_starred: Boolean,
    val has_attachment: Boolean,
    val label_colors: List<Color> = emptyList(),
    val label_names: List<String> = emptyList(),
    val label_icons: List<String> = emptyList(),
    val is_encrypted: Boolean = true,
    val trackers_blocked: Int = 0,
    val thread_id: String = id,
    val is_pinned: Boolean = false,
    val thread_message_count: Int = 1,
    val size_bytes: Long = 0,
    val category: String = "primary",
    val received_on: String? = null,
    val display_sender_name: String? = null,
    val display_sender_email: String? = null,
    val folder_chip: list_folder_chip? = null,
)

data class list_folder_chip(
    val name: String,
    val icon: String,
    val color: Color,
)

data class ThreadRow(
    val thread_id: String,
    val newest: Email,
    val message_count: Int,
    val has_unread: Boolean,
    val has_encrypted: Boolean,
    val total_trackers: Int,
    val has_attachment: Boolean,
    val is_starred: Boolean,
    val is_pinned: Boolean = false,
    val label_colors: List<Color>,
    val label_names: List<String> = emptyList(),
    val label_icons: List<String> = emptyList(),
    val participants: List<Pair<String, String>> = emptyList(),
    val folder_chip: list_folder_chip? = null,
)

fun thread_open_target_id(thread: ThreadRow): String = thread.newest.id

fun group_by_thread(emails: List<Email>): List<ThreadRow> {
    val seen_threads = mutableMapOf<String, MutableList<Email>>()
    for (e in emails) {
        seen_threads.getOrPut(e.thread_id) { mutableListOf() }.add(e)
    }
    val combined = mutableListOf<ThreadRow>()
    for ((tid, msgs) in seen_threads) {
        val real_msgs = mock_threads[tid]
        val newest = msgs.maxByOrNull { it.received_at } ?: continue
        val api_count = msgs.maxOf { it.thread_message_count }
        val count = real_msgs?.size ?: maxOf(api_count, msgs.size)
        val any_unread = msgs.any { !it.is_read }
        val any_enc = real_msgs?.any { it.is_encrypted } ?: msgs.any { it.is_encrypted }
        val trackers = real_msgs?.sumOf { it.trackers_blocked } ?: msgs.sumOf { it.trackers_blocked }
        val any_attach = msgs.any { it.has_attachment }
        val any_star = msgs.any { it.is_starred }
        val any_pinned = msgs.any { it.is_pinned }
        val labels = msgs.flatMap { it.label_colors }.distinct()
        val names = msgs.flatMap { it.label_names }.distinct()
        val icons = msgs.flatMap { it.label_icons }.distinct()
        val ordered_senders = msgs.sortedByDescending { it.received_at }
            .map {
                displayed_sender_name(it.display_sender_name, it.sender_name) to
                    displayed_sender_email(it.display_sender_email, it.sender_email)
            }
        val seen_emails = mutableSetOf<String>()
        val distinct_participants = mutableListOf<Pair<String, String>>()
        for ((nm, em) in ordered_senders) {
            val key = em.lowercase().ifBlank { nm.lowercase() }
            if (key.isBlank()) continue
            if (seen_emails.add(key)) distinct_participants.add(nm to em)
        }
        combined.add(
            ThreadRow(
                thread_id = tid,
                newest = newest,
                message_count = count,
                has_unread = any_unread,
                has_encrypted = any_enc,
                total_trackers = trackers,
                has_attachment = any_attach,
                is_starred = any_star,
                is_pinned = any_pinned,
                label_colors = labels,
                label_names = names,
                label_icons = icons,
                participants = distinct_participants,
                folder_chip = newest.folder_chip,
            ),
        )
    }
    return combined
}

data class MessageAttachment(
    val id: String,
    val filename: String,
    val content_type: String,
    val size_bytes: Long,
    val encrypted_data: String? = null,
    val data_nonce: String? = null,
    val session_key: String? = null,
    val content_id: String? = null,
)

data class ThreadMessage(
    val id: String,
    val sender_name: String,
    val sender_email: String,
    val to_label: String,
    val to_addresses: List<String> = emptyList(),
    val cc_addresses: List<String> = emptyList(),
    val timestamp: Long,
    val body: String,
    val body_html: String? = null,
    val is_encrypted: Boolean = true,
    val trackers_blocked: Int = 0,
    val is_read: Boolean = true,
    val preview: String = body.take(80),
    val attachments: List<MessageAttachment> = emptyList(),
    val raw_headers: List<Pair<String, String>> = emptyList(),
    val is_undecryptable: Boolean = false,
    val display_sender_name: String? = null,
    val display_sender_email: String? = null,
    val is_body_pending: Boolean = false,
    val item_type: String = "received",
    val spf_result: String? = null,
    val dkim_result: String? = null,
    val dmarc_result: String? = null,
)

enum class SenderAuthStatus { verified, failed, unknown }

fun sender_auth_status(msg: ThreadMessage): SenderAuthStatus {
    if (msg.item_type != "received") return SenderAuthStatus.unknown
    val spf = msg.spf_result?.lowercase()
    val dkim = msg.dkim_result?.lowercase()
    val dmarc = msg.dmarc_result?.lowercase()
    if (dmarc == "pass" && (dkim == "pass" || spf == "pass")) return SenderAuthStatus.verified
    if (dmarc == "fail" || spf == "fail" || dkim == "fail") return SenderAuthStatus.failed
    return SenderAuthStatus.unknown
}

private val label_work = Color(0xFF3B82F6)
private val label_personal = Color(0xFF10B981)
private val label_receipts = Color(0xFFF59E0B)
private val label_travel = Color(0xFFEC4899)
private val label_newsletter = Color(0xFF8B5CF6)

private fun days_ago(days: Int, hour: Int = 9, minute: Int = 15): Long {
    val cal = Calendar.getInstance()
    cal.add(Calendar.DAY_OF_YEAR, -days)
    cal.set(Calendar.HOUR_OF_DAY, hour)
    cal.set(Calendar.MINUTE, minute)
    cal.set(Calendar.SECOND, 0)
    cal.set(Calendar.MILLISECOND, 0)
    return cal.timeInMillis
}

val mock_inbox: List<Email> = listOf(
    Email(
        id = "e_01",
        thread_id = "t_01",
        sender_name = "Priya Shah",
        sender_email = "priya@linearhq.com",
        subject = "Roadmap review moved to Thursday",
        preview = "Hey, pushing the planning session to Thursday 10am so Marco can join from Berlin.",
        received_at = days_ago(0, 9, 42),
        is_read = false,
        is_starred = true,
        has_attachment = true,
        label_colors = listOf(label_work),
        is_encrypted = true,
        trackers_blocked = 0,
    ),
    Email(
        id = "e_02",
        thread_id = "t_02",
        sender_name = "GitHub",
        sender_email = "noreply@github.com",
        subject = "[aster-privacy/aster-android] PR #142 ready for review",
        preview = "jordan-sami requested your review on feat(mail): native inbox with swipe actions.",
        received_at = days_ago(0, 8, 12),
        is_read = false,
        is_starred = false,
        has_attachment = false,
        label_colors = listOf(label_work),
        is_encrypted = false,
        trackers_blocked = 2,
    ),
    Email(
        id = "e_03",
        thread_id = "t_03",
        sender_name = "Stripe",
        sender_email = "receipts@stripe.com",
        subject = "Your receipt from Aster Communications [#4210-8821]",
        preview = "Amount paid $29.00 USD. Card ending in 4242. Next invoice on May 20.",
        received_at = days_ago(0, 7, 3),
        is_read = true,
        is_starred = false,
        has_attachment = true,
        label_colors = listOf(label_receipts),
        is_encrypted = false,
        trackers_blocked = 5,
    ),
    Email(
        id = "e_04",
        thread_id = "t_04",
        sender_name = "Mom",
        sender_email = "ellen.sami@icloud.com",
        subject = "Dinner Sunday?",
        preview = "Dad is making his lasagna again. 6pm. Bring that nice bottle of red you mentioned.",
        received_at = days_ago(1, 19, 4),
        is_read = false,
        is_starred = true,
        has_attachment = false,
        label_colors = listOf(label_personal),
        is_encrypted = true,
    ),
    Email(
        id = "e_05",
        thread_id = "t_05",
        sender_name = "United Airlines",
        sender_email = "confirmations@united.com",
        subject = "Your trip to SFO is confirmed",
        preview = "Confirmation HJ42XQ. Depart YYZ 7:05am, arrive SFO 9:48am.",
        received_at = days_ago(1, 16, 28),
        is_read = true,
        is_starred = false,
        has_attachment = true,
        label_colors = listOf(label_travel),
        is_encrypted = false,
        trackers_blocked = 3,
    ),
    Email(
        id = "e_06",
        thread_id = "t_06",
        sender_name = "Hacker News Digest",
        sender_email = "digest@hackernews.email",
        subject = "Top stories this morning",
        preview = "Show HN: A privacy-first email client written in Rust.",
        received_at = days_ago(1, 7, 1),
        is_read = true,
        is_starred = false,
        has_attachment = false,
        label_colors = listOf(label_newsletter),
        is_encrypted = false,
        trackers_blocked = 1,
    ),
    Email(
        id = "e_07",
        thread_id = "t_07",
        sender_name = "Marco Bianchi",
        sender_email = "marco@asterprivacy.com",
        subject = "Android build passing again",
        preview = "Merged the gradle fix. Nightly CI is green.",
        received_at = days_ago(2, 22, 47),
        is_read = false,
        is_starred = false,
        has_attachment = false,
        label_colors = listOf(label_work),
        is_encrypted = true,
    ),
    Email(
        id = "e_08",
        thread_id = "t_08",
        sender_name = "Figma",
        sender_email = "team@figma.com",
        subject = "Alex commented on Mail/Mobile - Inbox v4",
        preview = "\"Love the compact row density here.\"",
        received_at = days_ago(2, 14, 22),
        is_read = true,
        is_starred = false,
        has_attachment = false,
        label_colors = listOf(label_work),
        is_encrypted = false,
        trackers_blocked = 1,
    ),
    Email(
        id = "e_09",
        thread_id = "t_09",
        sender_name = "Lena Kowalski",
        sender_email = "lena.k@protonmail.com",
        subject = "Re: the apartment in Lisbon",
        preview = "It's available the second week of May. Landlord wants a quick call.",
        received_at = days_ago(3, 11, 18),
        is_read = true,
        is_starred = true,
        has_attachment = true,
        label_colors = listOf(label_personal, label_travel),
        is_encrypted = true,
    ),
    Email(
        id = "e_10",
        thread_id = "t_10",
        sender_name = "Amazon.ca",
        sender_email = "order-update@amazon.ca",
        subject = "Your order of Yamaha HS5 has shipped",
        preview = "Arriving Tuesday. Track with Canada Post tracking number 9405 5...",
        received_at = days_ago(3, 9, 4),
        is_read = true,
        is_starred = false,
        has_attachment = false,
        label_colors = listOf(label_receipts),
        is_encrypted = false,
        trackers_blocked = 4,
    ),
    Email(
        id = "e_11",
        thread_id = "t_11",
        sender_name = "The Browser Company",
        sender_email = "hello@thebrowser.company",
        subject = "Arc for Android - early access",
        preview = "You're on the list. We'll send your invite the first week it hits staging.",
        received_at = days_ago(4, 10, 33),
        is_read = true,
        is_starred = false,
        has_attachment = false,
        label_colors = listOf(label_newsletter),
        is_encrypted = false,
        trackers_blocked = 2,
    ),
    Email(
        id = "e_12",
        thread_id = "t_12",
        sender_name = "Notion",
        sender_email = "team@notion.so",
        subject = "Weekly summary: 4 pages edited, 12 comments",
        preview = "Most active page: \"Q2 Engineering OKRs\".",
        received_at = days_ago(4, 6, 59),
        is_read = true,
        is_starred = false,
        has_attachment = false,
        label_colors = listOf(label_newsletter),
        is_encrypted = false,
        trackers_blocked = 3,
    ),
    Email(
        id = "e_13",
        thread_id = "t_13",
        sender_name = "Dr. Hartman's Clinic",
        sender_email = "appointments@hartmanclinic.ca",
        subject = "Annual check-up reminder",
        preview = "Your appointment is scheduled for May 6 at 3:15pm.",
        received_at = days_ago(5, 8, 0),
        is_read = false,
        is_starred = false,
        has_attachment = false,
        label_colors = listOf(label_personal),
        is_encrypted = false,
    ),
    Email(
        id = "e_14",
        thread_id = "t_14",
        sender_name = "Spotify",
        sender_email = "no-reply@spotify.com",
        subject = "Your playlist \"Deep Focus\" has 3 new songs",
        preview = "Based on what you've been listening to this week.",
        received_at = days_ago(5, 5, 44),
        is_read = true,
        is_starred = false,
        has_attachment = false,
        label_colors = listOf(label_newsletter),
        is_encrypted = false,
        trackers_blocked = 4,
    ),
    Email(
        id = "e_15",
        thread_id = "t_15",
        sender_name = "Sarah Chen",
        sender_email = "sarah@asterprivacy.com",
        subject = "Can you proof the launch blog post?",
        preview = "Draft is in Notion. Mostly worried about the encryption section.",
        received_at = days_ago(6, 13, 12),
        is_read = true,
        is_starred = true,
        has_attachment = false,
        label_colors = listOf(label_work),
        is_encrypted = true,
    ),
    Email(
        id = "e_16",
        thread_id = "t_16",
        sender_name = "1Password",
        sender_email = "noreply@1password.com",
        subject = "New sign-in from MacBook Pro",
        preview = "Signed in from Toronto, Canada at 9:12 AM.",
        received_at = days_ago(6, 9, 13),
        is_read = true,
        is_starred = false,
        has_attachment = false,
        is_encrypted = false,
    ),
    Email(
        id = "e_17",
        thread_id = "t_17",
        sender_name = "Vercel",
        sender_email = "notifications@vercel.com",
        subject = "Deployment ready - astermail.org",
        preview = "Deployment for production completed in 47s.",
        received_at = days_ago(7, 16, 40),
        is_read = true,
        is_starred = false,
        has_attachment = false,
        label_colors = listOf(label_work),
        is_encrypted = false,
    ),
    Email(
        id = "e_18",
        thread_id = "t_18",
        sender_name = "The Atlantic",
        sender_email = "newsletters@theatlantic.com",
        subject = "The Daily: A quiet revolution in email",
        preview = "Why end-to-end encrypted mail is finally having its moment.",
        received_at = days_ago(8, 7, 0),
        is_read = true,
        is_starred = false,
        has_attachment = false,
        label_colors = listOf(label_newsletter),
        is_encrypted = false,
        trackers_blocked = 6,
    ),
    Email(
        id = "e_19",
        thread_id = "t_19",
        sender_name = "Dani Ruiz",
        sender_email = "dani.ruiz@gmail.com",
        subject = "Climbing Saturday?",
        preview = "Basecamp opens at 9.",
        received_at = days_ago(9, 20, 17),
        is_read = true,
        is_starred = false,
        has_attachment = false,
        label_colors = listOf(label_personal),
        is_encrypted = true,
    ),
    Email(
        id = "e_20",
        thread_id = "t_20",
        sender_name = "Airbnb",
        sender_email = "automated@airbnb.com",
        subject = "Reservation confirmed - Lisbon, Portugal",
        preview = "May 12 - May 19. Host: Joana. Self check-in with keypad.",
        received_at = days_ago(10, 12, 0),
        is_read = true,
        is_starred = true,
        has_attachment = true,
        label_colors = listOf(label_travel, label_receipts),
        is_encrypted = false,
        trackers_blocked = 2,
    ),
    Email(
        id = "e_21",
        thread_id = "t_21",
        sender_name = "LinkedIn",
        sender_email = "notifications@linkedin.com",
        subject = "You appeared in 18 searches this week",
        preview = "Recruiters from Stripe, Figma, and Linear viewed your profile.",
        received_at = days_ago(11, 8, 40),
        is_read = true,
        is_starred = false,
        has_attachment = false,
        label_colors = listOf(label_newsletter),
        is_encrypted = false,
        trackers_blocked = 7,
    ),
    Email(
        id = "e_22",
        thread_id = "t_22",
        sender_name = "City of Toronto",
        sender_email = "noreply@toronto.ca",
        subject = "Property tax installment due May 1",
        preview = "Your pre-authorized payment of $612.44 will be withdrawn on May 1.",
        received_at = days_ago(12, 14, 15),
        is_read = true,
        is_starred = false,
        has_attachment = true,
        label_colors = listOf(label_receipts),
        is_encrypted = false,
    ),
    Email(
        id = "e_23",
        thread_id = "t_23",
        sender_name = "Anthropic",
        sender_email = "team@anthropic.com",
        subject = "Claude 4.7 is available in the API",
        preview = "New release notes, migration guide, and a 50% discount on batch inference through May.",
        received_at = days_ago(13, 11, 2),
        is_read = true,
        is_starred = false,
        has_attachment = false,
        label_colors = listOf(label_newsletter),
        is_encrypted = false,
        trackers_blocked = 3,
    ),
    Email(
        id = "e_24",
        thread_id = "t_24",
        sender_name = "Jordan Sami",
        sender_email = "jordan.sami711@gmail.com",
        subject = "Backup: recovery phrase",
        preview = "Saving a copy to my own mailbox. Do not forward.",
        received_at = days_ago(14, 17, 30),
        is_read = true,
        is_starred = true,
        has_attachment = true,
        is_encrypted = true,
    ),
    Email(
        id = "e_long_01",
        thread_id = "t_long_01",
        sender_name = "Sarah Chen",
        sender_email = "sarah@asterprivacy.com",
        subject = "Android launch checklist",
        preview = "We're a go. I'll hit publish at 9am PST tomorrow. Great work, team.",
        received_at = days_ago(1, 9, 30),
        is_read = false,
        is_starred = true,
        has_attachment = false,
        label_colors = listOf(label_work),
        is_encrypted = true,
        trackers_blocked = 0,
    ),
    Email(
        id = "e_long_02",
        thread_id = "t_long_02",
        sender_name = "Lena Kowalski",
        sender_email = "lena.k@protonmail.com",
        subject = "Lisbon trip planning",
        preview = "Already charged the battery. See you at the airport Monday morning!",
        received_at = days_ago(1, 8, 0),
        is_read = true,
        is_starred = false,
        has_attachment = false,
        label_colors = listOf(label_travel, label_personal),
        is_encrypted = true,
        trackers_blocked = 0,
    ),
)

val mock_inbox_extended: List<Email> = buildList {
    addAll(mock_inbox)
    val base = mock_inbox
    var day_offset = 2
    var seq = mock_inbox.size + 1
    while (size < 220) {
        for (src in base) {
            if (size >= 220) break
            val suffix = seq.toString().padStart(3, '0')
            add(
                src.copy(
                    id = "e_$suffix",
                    thread_id = "t_$suffix",
                    received_at = days_ago(day_offset, 8, (seq * 7) % 60),
                    is_read = (seq % 3) != 0,
                    is_starred = (seq % 11) == 0,
                ),
            )
            seq++
        }
        day_offset++
    }
}

@Composable
fun folder_display_name(folder_id: String): String {
    if (org.astermail.android.mail.is_all_mail_folder(folder_id)) {
        return stringResource(R.string.folder_all_mail)
    }
    return when (folder_id) {
        "inbox" -> stringResource(R.string.folder_inbox)
        "sent" -> stringResource(R.string.folder_sent)
        "drafts" -> stringResource(R.string.folder_drafts)
        "trash" -> stringResource(R.string.folder_trash)
        "spam" -> stringResource(R.string.folder_spam)
        "archive" -> stringResource(R.string.folder_archive)
        "starred" -> stringResource(R.string.folder_starred)
        "all" -> stringResource(R.string.folder_all_mail)
        "scheduled" -> stringResource(R.string.folder_scheduled)
        "snoozed" -> stringResource(R.string.folder_snoozed)
        "contacts" -> stringResource(R.string.folder_contacts)
        "subscriptions" -> stringResource(R.string.folder_subscriptions)
        else -> stringResource(R.string.folder_inbox)
    }
}

fun filter_by_mail_folder(all: List<Email>, folder_id: String): List<Email> {
    return when (folder_id) {
        "inbox" -> all
        "sent" -> all.filterIndexed { idx, _ -> idx % 7 == 0 }
        "drafts" -> all.filterIndexed { idx, _ -> idx % 12 == 0 }
        "trash" -> all.filterIndexed { idx, _ -> idx % 9 == 0 }
        "spam" -> all.filterIndexed { idx, _ -> idx % 15 == 0 }
        "archive" -> all.filterIndexed { idx, _ -> idx % 5 == 0 }
        "starred" -> all.filter { it.is_starred }
        "all" -> all
        "scheduled" -> emptyList()
        "snoozed" -> emptyList()
        else -> all
    }
}

val mock_threads: Map<String, List<ThreadMessage>> = mapOf(
    "t_long_01" to buildList {
        add(ThreadMessage(
            id = "t_long_01_m01", sender_name = "Sarah Chen", sender_email = "sarah@asterprivacy.com",
            to_label = "me, Marco", timestamp = days_ago(6, 9, 0),
            body = "Team, we need to finalize the Android launch checklist before end of week. I've drafted a list in Notion - can everyone review?",
            is_encrypted = true, trackers_blocked = 0,
        ))
        add(ThreadMessage(
            id = "t_long_01_m02", sender_name = "You", sender_email = "you@astermail.org",
            to_label = "Sarah, Marco", timestamp = days_ago(6, 9, 22),
            body = "Looking at it now. The accessibility section needs more detail - I'll add screen reader testing notes.",
            is_encrypted = true, trackers_blocked = 0,
        ))
        add(ThreadMessage(
            id = "t_long_01_m03", sender_name = "Marco Bianchi", sender_email = "marco@asterprivacy.com",
            to_label = "Sarah, me", timestamp = days_ago(6, 10, 15),
            body = "Good call. Also missing: the encryption key migration test for users upgrading from beta.",
            is_encrypted = true, trackers_blocked = 0,
        ))
        add(ThreadMessage(
            id = "t_long_01_m04", sender_name = "Sarah Chen", sender_email = "sarah@asterprivacy.com",
            to_label = "me, Marco", timestamp = days_ago(6, 11, 5),
            body = "Added both. Marco can you own the key migration test? I'll handle the Play Store listing review.",
            is_encrypted = true, trackers_blocked = 0,
        ))
        add(ThreadMessage(
            id = "t_long_01_m05", sender_name = "Marco Bianchi", sender_email = "marco@asterprivacy.com",
            to_label = "Sarah, me", timestamp = days_ago(5, 8, 30),
            body = "On it. Found an edge case where devices with Android 10 fail the ML-KEM handshake. Investigating.",
            is_encrypted = true, trackers_blocked = 0,
        ))
        add(ThreadMessage(
            id = "t_long_01_m06", sender_name = "You", sender_email = "you@astermail.org",
            to_label = "Sarah, Marco", timestamp = days_ago(5, 9, 12),
            body = "That might be the BoringSSL version on older devices. We should add a fallback to X25519 for API < 30.",
            is_encrypted = true, trackers_blocked = 0,
        ))
        add(ThreadMessage(
            id = "t_long_01_m07", sender_name = "Marco Bianchi", sender_email = "marco@asterprivacy.com",
            to_label = "Sarah, me", timestamp = days_ago(5, 11, 45),
            body = "Confirmed. Added the fallback. All green on the test matrix now.",
            is_encrypted = true, trackers_blocked = 0,
        ))
        add(ThreadMessage(
            id = "t_long_01_m08", sender_name = "Sarah Chen", sender_email = "sarah@asterprivacy.com",
            to_label = "me, Marco", timestamp = days_ago(5, 14, 0),
            body = "Play Store listing looks good. Screenshots are uploaded. Just waiting on the feature graphic from design.",
            is_encrypted = true, trackers_blocked = 0,
        ))
        add(ThreadMessage(
            id = "t_long_01_m09", sender_name = "You", sender_email = "you@astermail.org",
            to_label = "Sarah, Marco", timestamp = days_ago(4, 8, 10),
            body = "Design sent over the feature graphic. I've uploaded it. Can someone double-check the tablet screenshots?",
            is_encrypted = true, trackers_blocked = 0,
        ))
        add(ThreadMessage(
            id = "t_long_01_m10", sender_name = "Marco Bianchi", sender_email = "marco@asterprivacy.com",
            to_label = "Sarah, me", timestamp = days_ago(4, 9, 33),
            body = "Tablet screenshots look fine. One thing: the 10-inch layout has a rendering glitch on the compose screen. Filing a bug.",
            is_encrypted = true, trackers_blocked = 0,
        ))
        add(ThreadMessage(
            id = "t_long_01_m11", sender_name = "Sarah Chen", sender_email = "sarah@asterprivacy.com",
            to_label = "me, Marco", timestamp = days_ago(4, 10, 20),
            body = "Is that a blocker for launch?",
            is_encrypted = true, trackers_blocked = 0,
        ))
        add(ThreadMessage(
            id = "t_long_01_m12", sender_name = "Marco Bianchi", sender_email = "marco@asterprivacy.com",
            to_label = "Sarah, me", timestamp = days_ago(4, 11, 0),
            body = "No, it's cosmetic. The text field padding is off by 8dp. I'll fix it in a follow-up.",
            is_encrypted = true, trackers_blocked = 0,
        ))
        add(ThreadMessage(
            id = "t_long_01_m13", sender_name = "You", sender_email = "you@astermail.org",
            to_label = "Sarah, Marco", timestamp = days_ago(3, 8, 45),
            body = "Accessibility audit is done. Two issues: missing content descriptions on the encryption badge and the attachment button. PR is up.",
            is_encrypted = true, trackers_blocked = 0,
        ))
        add(ThreadMessage(
            id = "t_long_01_m14", sender_name = "Sarah Chen", sender_email = "sarah@asterprivacy.com",
            to_label = "me, Marco", timestamp = days_ago(3, 9, 15),
            body = "Approved and merged. Updated the checklist. We're at 18/22 items complete.",
            is_encrypted = true, trackers_blocked = 0,
        ))
        add(ThreadMessage(
            id = "t_long_01_m15", sender_name = "Marco Bianchi", sender_email = "marco@asterprivacy.com",
            to_label = "Sarah, me", timestamp = days_ago(3, 14, 0),
            body = "Push notification registration is working on all test devices now. That's 19/22.",
            is_encrypted = true, trackers_blocked = 0,
        ))
        add(ThreadMessage(
            id = "t_long_01_m16", sender_name = "You", sender_email = "you@astermail.org",
            to_label = "Sarah, Marco", timestamp = days_ago(2, 8, 30),
            body = "Deep linking is done and tested. 20/22. The last two items are the privacy policy update and the staged rollout config.",
            is_encrypted = true, trackers_blocked = 0,
        ))
        add(ThreadMessage(
            id = "t_long_01_m17", sender_name = "Sarah Chen", sender_email = "sarah@asterprivacy.com",
            to_label = "me, Marco", timestamp = days_ago(2, 10, 0),
            body = "Legal signed off on the privacy policy. Uploaded. 21/22.",
            is_encrypted = true, trackers_blocked = 0,
        ))
        add(ThreadMessage(
            id = "t_long_01_m18", sender_name = "Marco Bianchi", sender_email = "marco@asterprivacy.com",
            to_label = "Sarah, me", timestamp = days_ago(2, 15, 30),
            body = "Staged rollout configured: 5% day one, 25% day three, 100% by end of week if no critical bugs.",
            is_encrypted = true, trackers_blocked = 0,
        ))
        add(ThreadMessage(
            id = "t_long_01_m19", sender_name = "You", sender_email = "you@astermail.org",
            to_label = "Sarah, Marco", timestamp = days_ago(1, 9, 0),
            body = "That's 22/22. Are we a go for tomorrow?",
            is_encrypted = true, trackers_blocked = 0,
        ))
        add(ThreadMessage(
            id = "t_long_01_m20", sender_name = "Sarah Chen", sender_email = "sarah@asterprivacy.com",
            to_label = "me, Marco", timestamp = days_ago(1, 9, 30),
            body = "We're a go. I'll hit publish at 9am PST tomorrow. Great work, team.",
            is_encrypted = true, trackers_blocked = 0,
        ))
    },
    "t_long_02" to buildList {
        add(ThreadMessage(
            id = "t_long_02_m01", sender_name = "Lena Kowalski", sender_email = "lena.k@protonmail.com",
            to_label = "me", timestamp = days_ago(10, 18, 0),
            body = "Hey! Have you started planning the Lisbon trip yet? I found some great spots.",
            is_encrypted = true, trackers_blocked = 0,
        ))
        add(ThreadMessage(
            id = "t_long_02_m02", sender_name = "You", sender_email = "you@astermail.org",
            to_label = "Lena", timestamp = days_ago(10, 19, 15),
            body = "Not yet! Send me what you've got. I'm thinking we should rent a car for the Sintra day trip.",
            is_encrypted = true, trackers_blocked = 0,
        ))
        add(ThreadMessage(
            id = "t_long_02_m03", sender_name = "Lena Kowalski", sender_email = "lena.k@protonmail.com",
            to_label = "me", timestamp = days_ago(9, 8, 30),
            body = "Good idea on the car. Here's what I have so far: Time Out Market for lunch day one, Belem Tower, and the LX Factory for the afternoon.",
            is_encrypted = true, trackers_blocked = 0,
        ))
        add(ThreadMessage(
            id = "t_long_02_m04", sender_name = "You", sender_email = "you@astermail.org",
            to_label = "Lena", timestamp = days_ago(9, 10, 0),
            body = "LX Factory looks amazing. I also want to check out the tile museum. And we absolutely need to do a food tour.",
            is_encrypted = true, trackers_blocked = 0,
        ))
        add(ThreadMessage(
            id = "t_long_02_m05", sender_name = "Lena Kowalski", sender_email = "lena.k@protonmail.com",
            to_label = "me", timestamp = days_ago(8, 14, 0),
            body = "Booked a food tour in Alfama for day two! It's a 3-hour walking tour with 8 stops. 45 euros each.",
            is_encrypted = true, trackers_blocked = 0,
        ))
        add(ThreadMessage(
            id = "t_long_02_m06", sender_name = "You", sender_email = "you@astermail.org",
            to_label = "Lena", timestamp = days_ago(8, 15, 20),
            body = "Perfect. What about the Sintra day? Should we do Pena Palace and Quinta da Regaleira, or is that too ambitious?",
            is_encrypted = true, trackers_blocked = 0,
        ))
        add(ThreadMessage(
            id = "t_long_02_m07", sender_name = "Lena Kowalski", sender_email = "lena.k@protonmail.com",
            to_label = "me", timestamp = days_ago(7, 9, 0),
            body = "Both are doable if we leave early. Pena Palace opens at 9:30. I'd say arrive by 9 to beat the crowds.",
            is_encrypted = true, trackers_blocked = 0,
        ))
        add(ThreadMessage(
            id = "t_long_02_m08", sender_name = "You", sender_email = "you@astermail.org",
            to_label = "Lena", timestamp = days_ago(7, 11, 30),
            body = "Works for me. I found a car rental for 35 euros/day. Should I book it?",
            is_encrypted = true, trackers_blocked = 0,
        ))
        add(ThreadMessage(
            id = "t_long_02_m09", sender_name = "Lena Kowalski", sender_email = "lena.k@protonmail.com",
            to_label = "me", timestamp = days_ago(7, 12, 45),
            body = "Yes, book it! Make sure it has GPS. Also: do we need an international driving permit?",
            is_encrypted = true, trackers_blocked = 0,
        ))
        add(ThreadMessage(
            id = "t_long_02_m10", sender_name = "You", sender_email = "you@astermail.org",
            to_label = "Lena", timestamp = days_ago(6, 8, 0),
            body = "Checked - Canadian license is fine in Portugal for up to 6 months. Car is booked. Picking up at the airport.",
            is_encrypted = true, trackers_blocked = 0,
        ))
        add(ThreadMessage(
            id = "t_long_02_m11", sender_name = "Lena Kowalski", sender_email = "lena.k@protonmail.com",
            to_label = "me", timestamp = days_ago(6, 9, 30),
            body = "Amazing. What about dinner reservations? I heard Belcanto is incredible but you need to book weeks ahead.",
            is_encrypted = true, trackers_blocked = 0,
        ))
        add(ThreadMessage(
            id = "t_long_02_m12", sender_name = "You", sender_email = "you@astermail.org",
            to_label = "Lena", timestamp = days_ago(5, 10, 15),
            body = "Belcanto is fully booked. But I got us a table at Cervejaria Ramiro - supposed to have the best seafood in the city.",
            is_encrypted = true, trackers_blocked = 0,
        ))
        add(ThreadMessage(
            id = "t_long_02_m13", sender_name = "Lena Kowalski", sender_email = "lena.k@protonmail.com",
            to_label = "me", timestamp = days_ago(5, 14, 0),
            body = "Even better honestly. I love seafood. What's our budget looking like for the whole trip?",
            is_encrypted = true, trackers_blocked = 0,
        ))
        add(ThreadMessage(
            id = "t_long_02_m14", sender_name = "You", sender_email = "you@astermail.org",
            to_label = "Lena", timestamp = days_ago(4, 8, 0),
            body = "Rough estimate: 1800 accommodation, 200 car, 400 food, 150 activities. Around 2500-2700 total for the week, split two ways.",
            is_encrypted = true, trackers_blocked = 0,
        ))
        add(ThreadMessage(
            id = "t_long_02_m15", sender_name = "Lena Kowalski", sender_email = "lena.k@protonmail.com",
            to_label = "me", timestamp = days_ago(4, 9, 45),
            body = "That's really reasonable for a week in Lisbon. I'll set up a shared expense tracker so we can split everything easily.",
            is_encrypted = true, trackers_blocked = 0,
        ))
        add(ThreadMessage(
            id = "t_long_02_m16", sender_name = "You", sender_email = "you@astermail.org",
            to_label = "Lena", timestamp = days_ago(3, 10, 0),
            body = "Great idea. Also - should we add a day trip to Cascais? It's only 30 minutes by train from Lisbon.",
            is_encrypted = true, trackers_blocked = 0,
        ))
        add(ThreadMessage(
            id = "t_long_02_m17", sender_name = "Lena Kowalski", sender_email = "lena.k@protonmail.com",
            to_label = "me", timestamp = days_ago(3, 12, 0),
            body = "YES. Cascais beach is gorgeous. Let's do that on day 5 and keep day 6 as a free day for wandering.",
            is_encrypted = true, trackers_blocked = 0,
        ))
        add(ThreadMessage(
            id = "t_long_02_m18", sender_name = "You", sender_email = "you@astermail.org",
            to_label = "Lena", timestamp = days_ago(2, 9, 0),
            body = "Done. Full itinerary is in the shared doc. I think we're all set!",
            is_encrypted = true, trackers_blocked = 0,
        ))
        add(ThreadMessage(
            id = "t_long_02_m19", sender_name = "Lena Kowalski", sender_email = "lena.k@protonmail.com",
            to_label = "me", timestamp = days_ago(2, 11, 30),
            body = "This is going to be so good. Don't forget your camera - the views from Pena Palace are unreal.",
            is_encrypted = true, trackers_blocked = 0,
        ))
        add(ThreadMessage(
            id = "t_long_02_m20", sender_name = "You", sender_email = "you@astermail.org",
            to_label = "Lena", timestamp = days_ago(1, 8, 0),
            body = "Already charged the battery. See you at the airport Monday morning!",
            is_encrypted = true, trackers_blocked = 0,
        ))
    },
    "t_01" to listOf(
        ThreadMessage(
            id = "t_01_m1",
            sender_name = "Priya Shah",
            sender_email = "priya@linearhq.com",
            to_label = "me, marco@linearhq.com",
            timestamp = days_ago(2, 10, 4),
            body = "Team,\n\nWanted to float the idea of shifting our roadmap review to Thursday 10am so Marco can dial in from Berlin without the 6am start. Does that work?\n\nPriya",
            is_encrypted = true,
            trackers_blocked = 0,
        ),
        ThreadMessage(
            id = "t_01_m2",
            sender_name = "Marco Bianchi",
            sender_email = "marco@linearhq.com",
            to_label = "Priya, me",
            timestamp = days_ago(2, 11, 22),
            body = "Thursday 10am works for me. I'll pull the latest metrics before then.\n\nMarco",
            is_encrypted = true,
            trackers_blocked = 0,
        ),
        ThreadMessage(
            id = "t_01_m3",
            sender_name = "You",
            sender_email = "you@astermail.org",
            to_label = "Priya, Marco",
            timestamp = days_ago(1, 14, 8),
            body = "Works for me too. I'll send an updated agenda tonight.",
            is_encrypted = true,
            trackers_blocked = 0,
        ),
        ThreadMessage(
            id = "t_01_m4",
            sender_name = "Priya Shah",
            sender_email = "priya@linearhq.com",
            to_label = "me, Marco",
            timestamp = days_ago(0, 9, 42),
            body = "Confirming: Thursday 10am. Calendar invite updated. Agenda attached - let me know if anything is missing.\n\nPriya",
            is_encrypted = true,
            trackers_blocked = 0,
        ),
    ),
    "t_09" to listOf(
        ThreadMessage(
            id = "t_09_m1",
            sender_name = "You",
            sender_email = "you@astermail.org",
            to_label = "Lena",
            timestamp = days_ago(5, 18, 2),
            body = "Did you hear back from the landlord on the Lisbon apartment?",
            is_encrypted = true,
            trackers_blocked = 0,
        ),
        ThreadMessage(
            id = "t_09_m2",
            sender_name = "Lena Kowalski",
            sender_email = "lena.k@protonmail.com",
            to_label = "me",
            timestamp = days_ago(4, 9, 11),
            body = "Just now. He confirmed it's available the second week of May. Asking 1800 for the week including the cleaning fee.",
            is_encrypted = true,
            trackers_blocked = 0,
        ),
        ThreadMessage(
            id = "t_09_m3",
            sender_name = "You",
            sender_email = "you@astermail.org",
            to_label = "Lena",
            timestamp = days_ago(4, 10, 4),
            body = "That's reasonable. Can we see photos of the kitchen before we commit?",
            is_encrypted = true,
            trackers_blocked = 0,
        ),
        ThreadMessage(
            id = "t_09_m4",
            sender_name = "Lena Kowalski",
            sender_email = "lena.k@protonmail.com",
            to_label = "me",
            timestamp = days_ago(3, 11, 18),
            body = "Photos attached. Landlord wants a quick call to finalize. Should I book it?",
            is_encrypted = true,
            trackers_blocked = 0,
        ),
    ),
    "t_07" to listOf(
        ThreadMessage(
            id = "t_07_m1",
            sender_name = "Marco Bianchi",
            sender_email = "marco@asterprivacy.com",
            to_label = "me",
            timestamp = days_ago(3, 15, 10),
            body = "The gradle upgrade broke resource linking again. Looking into it.",
            is_encrypted = true,
            trackers_blocked = 0,
        ),
        ThreadMessage(
            id = "t_07_m2",
            sender_name = "You",
            sender_email = "you@astermail.org",
            to_label = "Marco",
            timestamp = days_ago(3, 16, 42),
            body = "Is it the AGP 8.5 change? I hit the same thing last week.",
            is_encrypted = true,
            trackers_blocked = 0,
        ),
        ThreadMessage(
            id = "t_07_m3",
            sender_name = "Marco Bianchi",
            sender_email = "marco@asterprivacy.com",
            to_label = "me",
            timestamp = days_ago(2, 22, 47),
            body = "Fixed it. Merged the gradle fix. Nightly CI is green. Let's cut a beta tag tomorrow if review is clean.",
            is_encrypted = true,
            trackers_blocked = 0,
        ),
    ),
)

fun thread_for(email: Email): List<ThreadMessage> {
    val found = mock_threads[email.thread_id]
    if (found != null) return found
    return listOf(
        ThreadMessage(
            id = email.id + "_m1",
            sender_name = email.sender_name,
            sender_email = email.sender_email,
            to_label = "me",
            timestamp = email.received_at,
            body = email.preview,
            is_encrypted = email.is_encrypted,
            trackers_blocked = email.trackers_blocked,
            display_sender_name = email.display_sender_name,
            display_sender_email = email.display_sender_email,
        ),
    )
}

fun find_thread_message(msg_id: String): ThreadMessage? {
    mock_threads.values.forEach { list ->
        val hit = list.firstOrNull { it.id == msg_id }
        if (hit != null) return hit
    }
    mock_inbox.forEach { e ->
        if (e.id + "_m1" == msg_id) {
            return ThreadMessage(
                id = msg_id,
                sender_name = e.sender_name,
                sender_email = e.sender_email,
                to_label = "me",
                timestamp = e.received_at,
                body = e.preview,
                is_encrypted = e.is_encrypted,
                trackers_blocked = e.trackers_blocked,
                display_sender_name = e.display_sender_name,
                display_sender_email = e.display_sender_email,
            )
        }
    }
    return null
}

fun find_email_for_thread(thread_id: String): Email? {
    val matches = mock_inbox_extended.filter { it.thread_id == thread_id }
    return matches.maxByOrNull { it.received_at }
        ?: mock_inbox_extended.firstOrNull { it.id == thread_id }
}

fun find_email_for_message(msg_id: String): Email? {
    mock_threads.forEach { (thread_id, list) ->
        if (list.any { it.id == msg_id }) {
            return mock_inbox.firstOrNull { it.thread_id == thread_id }
        }
    }
    return mock_inbox.firstOrNull { it.id + "_m1" == msg_id }
}

private val iso_timestamp_parser: ThreadLocal<SimpleDateFormat> = ThreadLocal.withInitial {
    SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss", Locale.US).apply {
        timeZone = java.util.TimeZone.getTimeZone("UTC")
    }
}

private fun parse_iso_timestamp(raw: String): Long = try {
    iso_timestamp_parser.get()!!.parse(raw.take(19))?.time ?: System.currentTimeMillis()
} catch (_: Throwable) {
    System.currentTimeMillis()
}

fun inbox_item_to_email(
    item: org.astermail.android.mail.InboxItem,
    tags: List<org.astermail.android.api.tags.TagItem> = emptyList(),
    folder_chip: list_folder_chip? = null,
): Email {
    val ts = parse_iso_timestamp(item.timestamp)
    val display_name = item.sender_name.ifBlank {
        item.sender_email.substringBefore('@').ifBlank { "Unknown" }
    }
    val matched_tags = tags.filter { it.tag_token in item.tag_tokens }
    return Email(
        id = item.id,
        thread_id = item.thread_token ?: item.id,
        thread_message_count = item.thread_message_count.coerceAtLeast(1),
        sender_name = display_name,
        sender_email = item.sender_email,
        subject = item.subject.ifBlank { "(no subject)" },
        preview = strip_html_simple(item.preview).ifBlank { item.subject.ifBlank { "" } },
        received_at = ts,
        is_read = item.is_read,
        is_starred = item.is_starred,
        has_attachment = item.has_attachments,
        is_encrypted = item.is_encrypted,
        trackers_blocked = 0,
        is_pinned = item.raw_item.metadata?.is_pinned ?: false,
        size_bytes = item.raw_item.metadata?.size_bytes ?: 0L,
        label_colors = matched_tags.mapNotNull { tag ->
            try { tag.encrypted_color?.let { Color(android.graphics.Color.parseColor(it)) } }
            catch (_: Throwable) { null }
        },
        label_names = matched_tags.map { it.encrypted_name },
        label_icons = matched_tags.map { it.encrypted_icon.orEmpty() },
        category = item.category,
        received_on = item.received_on,
        display_sender_name = item.display_sender_name,
        display_sender_email = item.display_sender_email,
        folder_chip = folder_chip,
    )
}

fun thread_message_to_mock(msg: org.astermail.android.mail.ThreadMessageDecrypted): ThreadMessage {
    val ts = parse_iso_timestamp(msg.timestamp)
    val has_pgp_text = msg.body_text.contains("-----BEGIN PGP")
    val html = if (msg.body_html != null && !msg.body_html.contains("-----BEGIN PGP")) {
        msg.body_html
    } else null
    val raw_body = when {
        html != null && (msg.body_text.isBlank() || has_pgp_text) -> html
        has_pgp_text -> ""
        else -> msg.body_text
    }
    val display_body = strip_html_simple(raw_body)
    return ThreadMessage(
        id = msg.id,
        sender_name = msg.sender_name.ifBlank { msg.sender_email.substringBefore('@') },
        sender_email = msg.sender_email,
        to_label = msg.to_label,
        to_addresses = msg.to_addresses,
        cc_addresses = msg.cc_addresses,
        timestamp = ts,
        body = display_body,
        body_html = html,
        is_encrypted = msg.is_encrypted,
        trackers_blocked = 0,
        is_read = msg.is_read,
        raw_headers = msg.raw_headers,
        is_undecryptable = msg.is_undecryptable,
        display_sender_name = msg.display_sender_name,
        display_sender_email = msg.display_sender_email,
        is_body_pending = msg.is_body_pending,
        item_type = msg.raw_item.item_type ?: "received",
        spf_result = msg.raw_item.spf_result,
        dkim_result = msg.raw_item.dkim_result,
        dmarc_result = msg.raw_item.dmarc_result,
    )
}

fun thread_message_with_attachments(
    msg: ThreadMessage,
    attachments: List<MessageAttachment>,
): ThreadMessage {
    return msg.copy(attachments = attachments)
}

private val re_strip_style = Regex("<style[^>]*>[\\s\\S]*?</style>", RegexOption.IGNORE_CASE)
private val re_strip_script = Regex("<script[^>]*>[\\s\\S]*?</script>", RegexOption.IGNORE_CASE)
private val re_strip_br = Regex("<br\\s*/?>", RegexOption.IGNORE_CASE)
private val re_strip_tag = Regex("<[^>]+>")
private val re_strip_blank_lines = Regex("\\n{3,}")
private val re_strip_spaces = Regex("[ \\t]+")

private fun strip_html_simple(text: String): String {
    var t = text
    t = t.replace(re_strip_style, "")
    t = t.replace(re_strip_script, "")
    t = t.replace(re_strip_br, "\n")
    t = t.replace(re_strip_tag, "")
    t = t.replace("&nbsp;", " ")
    t = t.replace("&amp;", "&")
    t = t.replace("&lt;", "<")
    t = t.replace("&gt;", ">")
    t = t.replace("&quot;", "\"")
    t = t.replace("&#39;", "'")
    t = t.replace(re_strip_blank_lines, "\n\n")
    t = t.replace(re_strip_spaces, " ")
    return t.trim()
}

private val time_of_day_format: ThreadLocal<SimpleDateFormat> =
    ThreadLocal.withInitial { SimpleDateFormat("h:mm a", Locale.getDefault()) }
private val weekday_format: ThreadLocal<SimpleDateFormat> =
    ThreadLocal.withInitial { SimpleDateFormat("EEE", Locale.getDefault()) }
private val short_date_format: ThreadLocal<SimpleDateFormat> =
    ThreadLocal.withInitial { SimpleDateFormat("MMM d", Locale.getDefault()) }
private val long_date_format: ThreadLocal<SimpleDateFormat> =
    ThreadLocal.withInitial { SimpleDateFormat("MMM d, yyyy", Locale.getDefault()) }
private val full_datetime_format: ThreadLocal<SimpleDateFormat> =
    ThreadLocal.withInitial { SimpleDateFormat("MMM d, yyyy h:mm a", Locale.getDefault()) }

fun Long.format_relative_time(yesterday_label: String = "Yesterday"): String {
    val now = Calendar.getInstance()
    val then = Calendar.getInstance().apply { timeInMillis = this@format_relative_time }
    val same_year = now.get(Calendar.YEAR) == then.get(Calendar.YEAR)
    val same_day = same_year && now.get(Calendar.DAY_OF_YEAR) == then.get(Calendar.DAY_OF_YEAR)
    if (same_day) {
        return time_of_day_format.get()!!.format(Date(this))
    }
    val yesterday = Calendar.getInstance().apply { add(Calendar.DAY_OF_YEAR, -1) }
    val is_yesterday = yesterday.get(Calendar.YEAR) == then.get(Calendar.YEAR) &&
        yesterday.get(Calendar.DAY_OF_YEAR) == then.get(Calendar.DAY_OF_YEAR)
    if (is_yesterday) return yesterday_label
    val diff_days = ((now.timeInMillis - this) / (1000L * 60 * 60 * 24)).toInt()
    if (diff_days in 2..6) {
        return weekday_format.get()!!.format(Date(this))
    }
    val formatter = if (same_year) short_date_format else long_date_format
    return formatter.get()!!.format(Date(this))
}

fun Long.format_full_datetime(): String {
    return full_datetime_format.get()!!.format(Date(this))
}

private fun html_to_plain_text(html: String): String {
    return android.text.Html.fromHtml(html, android.text.Html.FROM_HTML_MODE_LEGACY).toString().trim()
}

fun build_quoted_body(msg: ThreadMessage, mode: String): String {
    val plain_body = if (msg.body_html != null) html_to_plain_text(msg.body_html) else msg.body
    val quoted_name = displayed_sender_name(msg.display_sender_name, msg.sender_name)
    val quoted_email = displayed_sender_email(msg.display_sender_email, msg.sender_email)
    return when (mode) {
        "forward" -> buildString {
            append("\n\n")
            append("---------- Forwarded message ----------\n")
            append("From: $quoted_name <$quoted_email>\n")
            append("Date: ${msg.timestamp.format_full_datetime()}\n\n")
            plain_body.lines().forEach { append("> $it\n") }
        }
        else -> buildString {
            append("\n\n")
            append("On ${msg.timestamp.format_full_datetime()}, $quoted_name <$quoted_email> wrote:\n")
            plain_body.lines().forEach { append("> $it\n") }
        }
    }
}

fun subject_prefix(original: String, mode: String): String {
    val trimmed = original.trim()
    return when (mode) {
        "forward" -> if (trimmed.startsWith("Fwd:", ignoreCase = true)) trimmed else "Fwd: $trimmed"
        else -> if (trimmed.startsWith("Re:", ignoreCase = true)) trimmed else "Re: $trimmed"
    }
}

data class ComposePrefill(
    val to_chips: List<String>,
    val subject: String,
    val body: String,
    val cc_chips: List<String> = emptyList(),
)

fun compose_prefill_for(reply_to: String?, mode: String?): ComposePrefill {
    if (reply_to.isNullOrBlank() || mode.isNullOrBlank()) {
        return ComposePrefill(emptyList(), "", "")
    }
    val msg = find_thread_message(reply_to)
        ?: return ComposePrefill(emptyList(), "", "")
    val email = find_email_for_message(reply_to)
    val original_subject = email?.subject.orEmpty()
    val to_chips = when (mode) {
        "forward" -> emptyList()
        else -> listOf(msg.sender_email)
    }
    val body = build_quoted_body(msg, mode)
    val subject = subject_prefix(original_subject, mode)
    return ComposePrefill(to_chips, subject, body)
}
