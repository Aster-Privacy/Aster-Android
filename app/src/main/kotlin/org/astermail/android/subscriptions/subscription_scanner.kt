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
import android.os.SystemClock
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withContext
import org.astermail.android.api.mail.MailApi
import org.astermail.android.api.mail.MailItem
import org.astermail.android.api.mail.MailItemsListResponse
import org.astermail.android.mail.MailRepository
import org.astermail.android.ui.mail.UnsubscribeInfo
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
    val list_unsubscribe_post: String? = null,
    val unsubscribe_info: UnsubscribeInfo? = null,
)

private const val PREFS_NAME = "aster_subscription_scan"
private const val WATERMARK_PREFIX = "watermark_"
private const val SCAN_BUDGET_MS = 45_000L
private const val SCAN_DECRYPT_CONCURRENCY = 6

data class SubscriptionScanProgress(
    val scanned: Int,
    val total: Int,
    val found: List<DiscoveredSubscription>,
)

private data class ScannedHit(
    val email: String,
    val from_name: String,
    val created_at: String,
    val info: UnsubscribeInfo,
)

private class ScannedSender(val hit: ScannedHit?, val heal_pending: Boolean)

private val unfinished_sender = ScannedSender(null, true)

private val SYSTEM_DOMAINS =
    listOf("astermail.org", "astermail.com", "aster.cx", "astermail.me", "astermail.net")

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
    email.substringAfter('@', "").lowercase(java.util.Locale.ROOT)

internal fun is_system_sender(email: String): Boolean {
    val domain = sender_domain(email)
    return SYSTEM_DOMAINS.any { domain == it }
}

internal fun categorize_sender(
    domain: String,
    sender_name: String,
    has_list_unsubscribe: Boolean,
): String {
    val domain_lower = domain.lowercase(java.util.Locale.ROOT)
    val name_lower = sender_name.lowercase(java.util.Locale.ROOT)

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

    private suspend fun inspect_item(item: MailItem): ScannedSender? {
        val decrypt_result = repository.decrypt_envelope_with_heal(
            item.encrypted_envelope,
            item.envelope_nonce,
            item.id,
            received_at = item.created_at,
        )
        val envelope = decrypt_result.envelope ?: return ScannedSender(null, decrypt_result.heal_pending)
        val email = envelope.from_email.trim().lowercase(java.util.Locale.ROOT)
        if (email.isEmpty() || !email.contains('@') || is_system_sender(email)) return null
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
        if (!info.has_unsubscribe) return null
        return ScannedSender(
            ScannedHit(email, envelope.from_name, item.created_at.orEmpty(), info),
            false,
        )
    }

    private fun merge_hit(senders: LinkedHashMap<String, DiscoveredSubscription>, hit: ScannedHit) {
        val existing = senders[hit.email]
        if (existing == null) {
            val domain = sender_domain(hit.email)
            senders[hit.email] = DiscoveredSubscription(
                sender_email = hit.email,
                sender_name = hit.from_name.ifBlank { "" },
                domain = domain,
                email_count = 1,
                last_received = hit.created_at,
                unsubscribe_link = hit.info.unsubscribe_link,
                list_unsubscribe_header = hit.info.list_unsubscribe_header,
                category = categorize_sender(
                    domain,
                    hit.from_name.ifBlank { hit.email },
                    hit.info.has_unsubscribe,
                ),
                list_unsubscribe_post = hit.info.list_unsubscribe_post,
                unsubscribe_info = hit.info,
            )
        } else {
            senders[hit.email] = existing.copy(
                email_count = existing.email_count + 1,
                last_received = if (hit.created_at > existing.last_received) hit.created_at
                else existing.last_received,
                sender_name = existing.sender_name.ifBlank { hit.from_name },
                unsubscribe_link = existing.unsubscribe_link ?: hit.info.unsubscribe_link,
                list_unsubscribe_header = existing.list_unsubscribe_header
                    ?: hit.info.list_unsubscribe_header,
                list_unsubscribe_post = existing.list_unsubscribe_post ?: hit.info.list_unsubscribe_post,
                unsubscribe_info = better_unsubscribe_info(existing.unsubscribe_info, hit.info),
            )
        }
    }

    private suspend fun fetch_page(page_size: Int, cursor: String?): MailItemsListResponse? =
        try {
            mail_api.list_messages(
                limit = page_size,
                cursor = cursor,
                item_type = "received",
            )
        } catch (e: CancellationException) {
            throw e
        } catch (_: Throwable) {
            null
        }

    suspend fun scan(
        max_pages: Int = 12,
        page_size: Int = 100,
        force_full: Boolean = false,
        budget_ms: Long = SCAN_BUDGET_MS,
        persist_watermark: Boolean = true,
        on_progress: (SubscriptionScanProgress) -> Unit = {},
    ): List<DiscoveredSubscription> = withContext(Dispatchers.Default) {
        val watermark = if (force_full) "" else last_scan_watermark()
        val senders = LinkedHashMap<String, DiscoveredSubscription>()
        val deadline = SystemClock.elapsedRealtime() + budget_ms
        val gate = Semaphore(SCAN_DECRYPT_CONCURRENCY)
        val page_cap = max_pages * page_size
        var cursor: String? = null
        var pages = 0
        var scanned = 0
        var total = 0
        var newest_seen = ""
        var stop = false
        var heal_pending_failure = false
        var next_page: Deferred<MailItemsListResponse?>? = null

        try {
            while (pages < max_pages && !stop) {
                ensureActive()
                if (SystemClock.elapsedRealtime() >= deadline) break
                val pending = next_page
                next_page = null
                val response = (if (pending != null) pending.await() else fetch_page(page_size, cursor)) ?: break
                if (total == 0 && response.total > 0) total = minOf(response.total, page_cap)
                pages += 1
                val next_cursor = response.next_cursor
                val has_next = pages < max_pages && response.has_more &&
                    !next_cursor.isNullOrBlank() && next_cursor != cursor

                val fresh = response.items.takeWhile { item ->
                    val created_at = item.created_at.orEmpty()
                    watermark.isEmpty() || created_at.isEmpty() || created_at > watermark
                }
                if (fresh.size < response.items.size) stop = true
                if (has_next && !stop) next_page = async { fetch_page(page_size, next_cursor) }

                val results = coroutineScope {
                    fresh.map { item ->
                        async {
                            gate.withPermit {
                                if (SystemClock.elapsedRealtime() >= deadline) unfinished_sender else inspect_item(item)
                            }
                        }
                    }.awaitAll()
                }
                if (SystemClock.elapsedRealtime() >= deadline) stop = true
                fresh.forEachIndexed { index, item ->
                    val created_at = item.created_at.orEmpty()
                    if (created_at > newest_seen) newest_seen = created_at
                    val result = results[index] ?: return@forEachIndexed
                    if (result.heal_pending) heal_pending_failure = true
                    result.hit?.let { merge_hit(senders, it) }
                }
                scanned += fresh.size
                on_progress(
                    SubscriptionScanProgress(
                        scanned = scanned,
                        total = maxOf(total, scanned),
                        found = senders.values.toList(),
                    ),
                )
                if (!has_next) break
                cursor = next_cursor
            }
        } finally {
            next_page?.cancel()
        }

        if (persist_watermark && newest_seen.isNotEmpty() && !heal_pending_failure) save_watermark(newest_seen)
        senders.values.toList()
    }
}
