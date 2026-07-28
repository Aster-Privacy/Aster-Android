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

package org.astermail.android.subscriptions

import android.content.Context
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.astermail.android.api.mail.MailApi
import org.astermail.android.mail.MailRepository
import org.astermail.android.ui.mail.detect_unsubscribe_info

data class DiscoveredSubscription(
    val sender_email: String,
    val sender_name: String,
    val domain: String,
    val email_count: Int,
    val last_received: String,
    val unsubscribe_link: String?,
    val list_unsubscribe_header: String?,
    val category: String,
)

private const val PREFS_NAME = "aster_subscription_scan"
private const val WATERMARK_PREFIX = "watermark_"

private val SYSTEM_DOMAINS = listOf("astermail.org", "astermail.com", "aster.cx")

private val NEWSLETTER_DOMAINS = listOf(
    "substack.com",
    "mailchimp.com",
    "sendgrid.net",
    "constantcontact.com",
    "mailgun.net",
    "sendinblue.com",
    "mailjet.com",
    "campaign-archive.com",
)

private val MARKETING_DOMAINS = listOf(
    "amazonses.com",
    "salesforce.com",
    "hubspot.com",
    "marketo.com",
    "pardot.com",
    "eloqua.com",
)

private val SOCIAL_DOMAINS = listOf(
    "facebookmail.com",
    "twitter.com",
    "linkedin.com",
    "instagram.com",
    "tiktok.com",
    "reddit.com",
    "discord.com",
)

private val TRANSACTIONAL_KEYWORDS = listOf(
    "receipt",
    "order",
    "confirmation",
    "shipping",
    "tracking",
    "invoice",
    "payment",
    "password",
    "verify",
    "security",
)

internal fun sender_domain(email: String): String =
    email.substringAfter('@', "").lowercase()

internal fun is_system_sender(email: String): Boolean {
    val domain = sender_domain(email)
    return SYSTEM_DOMAINS.any { domain == it }
}

internal fun categorize_sender(
    domain: String,
    sender_name: String,
    has_list_unsubscribe: Boolean,
): String {
    val domain_lower = domain.lowercase()
    val name_lower = sender_name.lowercase()

    if (NEWSLETTER_DOMAINS.any { domain_lower.contains(it) }) return "newsletter"
    if (MARKETING_DOMAINS.any { domain_lower.contains(it) }) return "marketing"
    if (SOCIAL_DOMAINS.any { domain_lower.contains(it) }) return "social"
    if (TRANSACTIONAL_KEYWORDS.any { name_lower.contains(it) }) return "transactional"

    if (has_list_unsubscribe) {
        if (
            name_lower.contains("newsletter") ||
            name_lower.contains("digest") ||
            name_lower.contains("weekly")
        ) {
            return "newsletter"
        }
        if (
            name_lower.contains("promo") ||
            name_lower.contains("offer") ||
            name_lower.contains("deal")
        ) {
            return "marketing"
        }
    }

    return "unknown"
}

@Singleton
class SubscriptionScanner @Inject constructor(
    private val mail_api: MailApi,
    private val repository: MailRepository,
    @ApplicationContext private val context: Context,
) {
    private fun watermark_key(): String {
        val email = repository.get_user_email().orEmpty()
        val digest = java.security.MessageDigest.getInstance("SHA-256")
            .digest(email.toByteArray(Charsets.UTF_8))
        return WATERMARK_PREFIX + digest.joinToString("") { "%02x".format(it) }
    }

    fun last_scan_watermark(): String =
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .getString(watermark_key(), "")
            .orEmpty()

    fun save_watermark(value: String) {
        if (value.isBlank()) return
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit()
            .putString(watermark_key(), value)
            .apply()
    }

    fun clear_watermark() {
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit()
            .remove(watermark_key())
            .apply()
    }

    suspend fun scan(
        max_pages: Int = 12,
        page_size: Int = 100,
        force_full: Boolean = false,
    ): List<DiscoveredSubscription> = withContext(Dispatchers.Default) {
        val watermark = if (force_full) "" else last_scan_watermark()
        val senders = LinkedHashMap<String, DiscoveredSubscription>()
        var cursor: String? = null
        var pages = 0
        var newest_seen = ""
        var stop = false

        while (pages < max_pages && !stop) {
            val response = runCatching {
                mail_api.list_messages(
                    limit = page_size,
                    cursor = cursor,
                    item_type = "received",
                )
            }.getOrNull() ?: break

            for (item in response.items) {
                val created_at = item.created_at.orEmpty()
                if (watermark.isNotEmpty() && created_at.isNotEmpty() && created_at <= watermark) {
                    stop = true
                    break
                }
                if (created_at > newest_seen) newest_seen = created_at

                val envelope = repository.decrypt_envelope_public(
                    item.encrypted_envelope,
                    item.envelope_nonce,
                    item.id,
                ) ?: continue

                val email = envelope.from_email.trim().lowercase()
                if (email.isEmpty() || !email.contains('@')) continue
                if (is_system_sender(email)) continue

                val header_unsubscribe = envelope.list_unsubscribe
                    ?: envelope.raw_headers.firstOrNull {
                        it.first.equals("list-unsubscribe", ignoreCase = true)
                    }?.second
                val header_unsubscribe_post = envelope.raw_headers.firstOrNull {
                    it.first.equals("list-unsubscribe-post", ignoreCase = true)
                }?.second

                val info = detect_unsubscribe_info(
                    html_content = envelope.body_html,
                    text_content = envelope.body_text,
                    list_unsubscribe = header_unsubscribe,
                    list_unsubscribe_post = header_unsubscribe_post,
                )
                if (!info.has_unsubscribe) continue

                val existing = senders[email]
                if (existing == null) {
                    val domain = sender_domain(email)
                    senders[email] = DiscoveredSubscription(
                        sender_email = email,
                        sender_name = envelope.from_name.ifBlank { "" },
                        domain = domain,
                        email_count = 1,
                        last_received = created_at,
                        unsubscribe_link = info.unsubscribe_link,
                        list_unsubscribe_header = info.list_unsubscribe_header,
                        category = categorize_sender(
                            domain,
                            envelope.from_name.ifBlank { email },
                            info.has_unsubscribe,
                        ),
                    )
                } else {
                    senders[email] = existing.copy(
                        email_count = existing.email_count + 1,
                        last_received = if (created_at > existing.last_received) created_at
                        else existing.last_received,
                        sender_name = existing.sender_name.ifBlank { envelope.from_name },
                        unsubscribe_link = existing.unsubscribe_link ?: info.unsubscribe_link,
                        list_unsubscribe_header = existing.list_unsubscribe_header
                            ?: info.list_unsubscribe_header,
                    )
                }
            }

            pages += 1
            cursor = response.next_cursor
            if (!response.has_more || cursor.isNullOrBlank()) break
        }

        if (newest_seen.isNotEmpty()) save_watermark(newest_seen)
        senders.values.toList()
    }
}
