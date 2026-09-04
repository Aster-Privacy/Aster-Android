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
import java.util.TimeZone
import org.astermail.android.util.clip_units

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
    val routing_token: String? = null,
    val display_sender_name: String? = null,
    val display_sender_email: String? = null,
    val folder_chip: list_folder_chip? = null,
    val is_external: Boolean = false,
    val system_origin: Boolean = false,
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
        val newest = msgs.maxByOrNull { it.received_at } ?: continue
        val api_count = msgs.maxOf { it.thread_message_count }
        val count = maxOf(api_count, msgs.size)
        val any_unread = msgs.any { !it.is_read }
        val any_enc = msgs.any { it.is_encrypted }
        val trackers = msgs.sumOf { it.trackers_blocked }
        val any_attach = msgs.any { it.has_attachment }
        val any_star = msgs.any { it.is_starred }
        val any_pinned = msgs.any { it.is_pinned }
        val labels = mutableListOf<Color>()
        val names = mutableListOf<String>()
        val icons = mutableListOf<String>()
        val seen_labels = mutableSetOf<String>()
        msgs.forEach { msg ->
            msg.label_names.indices.forEach { i ->
                val name = msg.label_names[i]
                if (seen_labels.add(name)) {
                    names.add(name)
                    labels.add(msg.label_colors.getOrElse(i) { default_label_color })
                    icons.add(msg.label_icons.getOrElse(i) { "" })
                }
            }
        }
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

fun flat_thread_rows(emails: List<Email>): List<ThreadRow> = emails.map { e ->
    ThreadRow(
        thread_id = e.id,
        newest = e,
        message_count = 1,
        has_unread = !e.is_read,
        has_encrypted = e.is_encrypted,
        total_trackers = e.trackers_blocked,
        has_attachment = e.has_attachment,
        is_starred = e.is_starred,
        is_pinned = e.is_pinned,
        label_colors = e.label_colors,
        label_names = e.label_names,
        label_icons = e.label_icons,
        participants = listOf(
            displayed_sender_name(e.display_sender_name, e.sender_name) to
                displayed_sender_email(e.display_sender_email, e.sender_email),
        ),
        folder_chip = e.folder_chip,
    )
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
    val mail_item_id: String? = null,
    val seq_num: Int? = null,
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
    val preview: String = body.clip_units(80),
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
    val is_external: Boolean = false,
    val system_origin: Boolean = false,
    val has_recipient_key: Boolean? = null,
    val pgp_encrypted: Boolean = false,
    val pgp_signature: org.astermail.android.crypto.PgpSignatureStatus =
        org.astermail.android.crypto.PgpSignatureStatus.NONE,
)

val ThreadMessage.is_e2e_encrypted: Boolean
    get() = pgp_encrypted || !is_external || has_recipient_key == true

enum class SenderAuthStatus { verified, failed, unknown }

private val aster_sender_domains = listOf("@astermail.org", "@aster.cx")

fun is_aster_system_address(email: String): Boolean {
    val address = email.trim().lowercase()
    if (aster_sender_domains.none { address.endsWith(it) }) return false
    return is_system_local_part(address)
}

fun is_aster_system_sender(msg: ThreadMessage): Boolean =
    msg.system_origin && !msg.is_external && is_aster_system_address(msg.sender_email)

fun is_aster_system_sender(email: Email): Boolean =
    email.system_origin && !email.is_external && is_aster_system_address(email.sender_email)

private fun same_address(shown: String, actual: String): Boolean =
    shown.trim().lowercase() == actual.trim().lowercase()

fun system_avatar_authenticated(email: Email): Boolean =
    is_aster_system_sender(email) &&
        same_address(displayed_sender_email(email.display_sender_email, email.sender_email), email.sender_email)

fun system_avatar_authenticated(msg: ThreadMessage): Boolean =
    is_aster_system_sender(msg) &&
        same_address(displayed_sender_email(msg.display_sender_email, msg.sender_email), msg.sender_email)

fun sender_auth_status(msg: ThreadMessage): SenderAuthStatus {
    if (msg.item_type != "received") return SenderAuthStatus.unknown
    val spf = msg.spf_result?.lowercase()
    val dkim = msg.dkim_result?.lowercase()
    val dmarc = msg.dmarc_result?.lowercase()
    if (dmarc == "pass" && (dkim == "pass" || spf == "pass")) return SenderAuthStatus.verified
    if (dmarc == "fail" || spf == "fail" || dkim == "fail") return SenderAuthStatus.failed
    return SenderAuthStatus.unknown
}

internal val default_label_color = Color(0xFF3B82F6)

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

private val iso_timestamp_parser: ThreadLocal<SimpleDateFormat> = ThreadLocal.withInitial {
    SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss", Locale.US).apply {
        timeZone = java.util.TimeZone.getTimeZone("UTC")
    }
}

private fun parse_iso_timestamp(raw: String): Long = try {
    iso_timestamp_parser.get()!!.parse(raw.take(19))?.time ?: 0L
} catch (_: Throwable) {
    0L
}

fun inbox_item_to_email(
    item: org.astermail.android.mail.InboxItem,
    tags: List<org.astermail.android.api.tags.TagItem> = emptyList(),
    folder_chip: list_folder_chip? = null,
    context: android.content.Context? = null,
): Email {
    val ts = parse_iso_timestamp(item.timestamp)
    val unknown_sender_label = context?.getString(org.astermail.android.R.string.unknown) ?: "Unknown"
    val no_subject_label = context?.getString(org.astermail.android.R.string.no_subject) ?: "(no subject)"
    val display_name = item.sender_name.ifBlank {
        item.sender_email.substringBefore('@').ifBlank { unknown_sender_label }
    }
    val matched_tags = tags.filter { it.tag_token in item.tag_tokens }
    return Email(
        id = item.id,
        thread_id = item.thread_token?.takeIf { it.isNotBlank() } ?: item.id,
        thread_message_count = item.thread_message_count.coerceAtLeast(1),
        sender_name = display_name,
        sender_email = item.sender_email,
        subject = item.subject.ifBlank { no_subject_label },
        preview = strip_html_simple(item.preview).ifBlank { item.subject.ifBlank { "" } },
        received_at = ts,
        is_read = item.is_read,
        is_starred = item.is_starred,
        has_attachment = item.has_attachments,
        is_encrypted = item.is_encrypted,
        trackers_blocked = 0,
        is_pinned = item.raw_item.metadata?.is_pinned ?: false,
        size_bytes = item.raw_item.metadata?.size_bytes ?: 0L,
        label_colors = matched_tags.map { tag ->
            try { tag.encrypted_color?.let { Color(android.graphics.Color.parseColor(it)) } }
            catch (_: Throwable) { null } ?: default_label_color
        },
        label_names = matched_tags.map { it.encrypted_name },
        label_icons = matched_tags.map { it.encrypted_icon.orEmpty() },
        category = item.category,
        received_on = item.received_on,
        routing_token = item.routing_token,
        display_sender_name = item.display_sender_name,
        display_sender_email = item.display_sender_email,
        folder_chip = folder_chip,
        is_external = item.raw_item.is_external,
        system_origin = item.raw_item.system_origin,
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
        is_external = msg.raw_item.is_external ?: false,
        system_origin = msg.raw_item.system_origin ?: false,
        has_recipient_key = msg.raw_item.has_recipient_key,
        pgp_encrypted = msg.pgp_encrypted,
        pgp_signature = msg.pgp_signature,
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

object AsterTimePreferences {
    @Volatile
    var use_24h: Boolean = false
        private set

    @Volatile
    var generation: Int = 0
        private set

    @Volatile
    var time_zone: TimeZone? = null
        private set

    @Volatile
    private var device_use_24h: Boolean = false

    @Volatile
    private var account_time_format: String? = null

    @Volatile
    var account_date_format: String? = null
        private set

    @Synchronized
    fun set_use_24h(value: Boolean) {
        device_use_24h = value
        apply_resolved_use_24h()
    }

    @Synchronized
    fun set_account_time_format(value: String?) {
        val normalized = when (value) {
            "12h", "24h" -> value
            else -> null
        }
        if (normalized == account_time_format) return
        account_time_format = normalized
        apply_resolved_use_24h()
    }

    @Synchronized
    fun set_account_date_format(value: String?) {
        val normalized = when (value) {
            "MM/DD/YYYY", "DD/MM/YYYY", "YYYY-MM-DD" -> value
            else -> null
        }
        if (normalized == account_date_format) return
        account_date_format = normalized
        generation += 1
    }

    @Synchronized
    fun set_account_time_zone(value: String?) {
        val resolved = resolve_time_zone(value)
        if (resolved?.id == time_zone?.id) return
        time_zone = resolved
        generation += 1
    }

    fun account_calendar(): Calendar {
        return time_zone?.let { Calendar.getInstance(it) } ?: Calendar.getInstance()
    }

    fun account_zone_id(): java.time.ZoneId {
        return time_zone?.toZoneId() ?: java.time.ZoneId.systemDefault()
    }

    private fun resolve_time_zone(value: String?): TimeZone? {
        val trimmed = value?.trim().orEmpty()
        if (trimmed.isEmpty() || trimmed == "auto") return null
        if (!TimeZone.getAvailableIDs().contains(trimmed)) return null
        return TimeZone.getTimeZone(trimmed)
    }

    private fun apply_resolved_use_24h() {
        val resolved = when (account_time_format) {
            "24h" -> true
            "12h" -> false
            else -> device_use_24h
        }
        if (resolved == use_24h) return
        use_24h = resolved
        generation += 1
    }
}

private class cached_date_format(
    val generation: Int,
    val locale: Locale,
    val format: SimpleDateFormat,
)

private val time_of_day_holder = ThreadLocal<cached_date_format>()
private val weekday_holder = ThreadLocal<cached_date_format>()
private val short_date_holder = ThreadLocal<cached_date_format>()
private val long_date_holder = ThreadLocal<cached_date_format>()
private val full_datetime_holder = ThreadLocal<cached_date_format>()

private fun localized_format(
    holder: ThreadLocal<cached_date_format>,
    skeleton_12h: String,
    skeleton_24h: String,
): SimpleDateFormat {
    val locale = Locale.getDefault()
    val generation = AsterTimePreferences.generation
    val cached = holder.get()
    if (cached != null && cached.generation == generation && cached.locale == locale) {
        return cached.format
    }
    val skeleton = if (AsterTimePreferences.use_24h) skeleton_24h else skeleton_12h
    val pattern = account_date_pattern(skeleton)
        ?: android.text.format.DateFormat.getBestDateTimePattern(locale, skeleton)
    val format = SimpleDateFormat(pattern, locale)
    AsterTimePreferences.time_zone?.let { format.timeZone = it }
    holder.set(cached_date_format(generation, locale, format))
    return format
}

private fun account_date_pattern(skeleton: String): String? {
    val preference = AsterTimePreferences.account_date_format ?: return null
    val with_year = skeleton.startsWith("y")
    val date_part = when (preference) {
        "DD/MM/YYYY" -> if (with_year) "dd/MM/yyyy" else "dd/MM"
        "YYYY-MM-DD" -> if (with_year) "yyyy-MM-dd" else "MM-dd"
        else -> if (with_year) "MM/dd/yyyy" else "MM/dd"
    }
    if (!skeleton.contains("d")) return null
    val time_part = when {
        skeleton.endsWith("hm") -> " h:mm a"
        skeleton.endsWith("Hm") -> " HH:mm"
        else -> ""
    }
    return date_part + time_part
}

private fun time_of_day_format() = localized_format(time_of_day_holder, "hm", "Hm")

private fun weekday_format() = localized_format(weekday_holder, "EEE", "EEE")

private fun short_date_format() = localized_format(short_date_holder, "MMMd", "MMMd")

private fun long_date_format() = localized_format(long_date_holder, "yMMMd", "yMMMd")

private fun full_datetime_format() =
    localized_format(full_datetime_holder, "yMMMdhm", "yMMMdHm")

private fun start_of_day_millis(source: Calendar): Long {
    val start = source.clone() as Calendar
    start.set(Calendar.HOUR_OF_DAY, 0)
    start.set(Calendar.MINUTE, 0)
    start.set(Calendar.SECOND, 0)
    start.set(Calendar.MILLISECOND, 0)
    return start.timeInMillis
}

private fun calendar_days_between(from: Calendar, to: Calendar): Int {
    val span = start_of_day_millis(to) - start_of_day_millis(from)
    return Math.round(span / 86_400_000.0).toInt()
}

fun Long.format_relative_time(yesterday_label: String = "Yesterday"): String {
    val now = AsterTimePreferences.account_calendar()
    val then = AsterTimePreferences.account_calendar()
        .apply { timeInMillis = this@format_relative_time }
    val same_year = now.get(Calendar.YEAR) == then.get(Calendar.YEAR)
    val same_day = same_year && now.get(Calendar.DAY_OF_YEAR) == then.get(Calendar.DAY_OF_YEAR)
    if (same_day) {
        return time_of_day_format().format(Date(this))
    }
    val yesterday = AsterTimePreferences.account_calendar()
        .apply { add(Calendar.DAY_OF_YEAR, -1) }
    val is_yesterday = yesterday.get(Calendar.YEAR) == then.get(Calendar.YEAR) &&
        yesterday.get(Calendar.DAY_OF_YEAR) == then.get(Calendar.DAY_OF_YEAR)
    if (is_yesterday) return yesterday_label
    val diff_days = calendar_days_between(then, now)
    if (diff_days in 2..6) {
        return weekday_format().format(Date(this))
    }
    val formatter = if (same_year) short_date_format() else long_date_format()
    return formatter.format(Date(this))
}

fun Long.format_full_datetime(): String {
    return full_datetime_format().format(Date(this))
}

fun Long.format_long_date(): String {
    return long_date_format().format(Date(this))
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
