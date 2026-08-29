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

package org.astermail.android.mail

import org.astermail.android.api.mail.MailItem
import org.astermail.android.api.mail.MailItemMetadata
import org.astermail.android.api.mail.ThreadMessageItem

const val DEMO_PHISH_ACCOUNT_EMAIL = "hello@astermail.org"
const val DEMO_PHISH_ITEM_ID = "demo_phish_001"
const val DEMO_PHISH_THREAD_TOKEN = "demo_phish_thread_001"

private const val DEMO_PHISH_SENDER_NAME = "PayPal Security <support@paypal.com>"
private const val DEMO_PHISH_SENDER_EMAIL = "security@paypa1-billing-alerts.com"
private const val DEMO_PHISH_SUBJECT_FALLBACK = "Urgent: unusual sign-in activity on your PayPal account"
private const val DEMO_PHISH_PREVIEW_FALLBACK = "We detected an unauthorized login attempt. Verify your identity immediately to avoid account suspension."

object DemoPhishingContentHolder {
    @Volatile
    var shared: DemoPhishingContent? = null
}

data class DemoPhishingContent(
    val subject: String,
    val preview: String,
    val body_greeting: String,
    val body_para1: String,
    val body_para2: String,
    val body_para3: String,
    val body_signoff: String,
    val body_signin_label: String,
)

private fun demo_phish_subject(): String =
    DemoPhishingContentHolder.shared?.subject ?: DEMO_PHISH_SUBJECT_FALLBACK

private fun demo_phish_preview(): String =
    DemoPhishingContentHolder.shared?.preview ?: DEMO_PHISH_PREVIEW_FALLBACK

private fun demo_phish_body_text(): String {
    val content = DemoPhishingContentHolder.shared
    val greeting = content?.body_greeting ?: "Dear customer,"
    val para1 = content?.body_para1
        ?: "We detected an unusual sign-in activity on your PayPal account from a device we do not recognize. " +
        "For your protection, your account will be suspended within 24 hours unless you verify your identity immediately."
    val para2 = content?.body_para2 ?: "Please confirm your billing information by visiting the secure link below:"
    val para3 = content?.body_para3
        ?: "Failure to respond will result in permanent loss of access to your funds. This is an immediate action required notice."
    val signoff = content?.body_signoff ?: "PayPal Security Team"
    return "$greeting\n\n$para1\n\n$para2\nhttps://www.paypal.com/account/verify\n\n$para3\n\n$signoff"
}

private fun demo_phish_body_html(): String {
    val content = DemoPhishingContentHolder.shared
    val greeting = content?.body_greeting ?: "Dear customer,"
    val para1 = content?.body_para1
        ?: "We detected an unusual sign-in activity on your PayPal account from a device we do not recognize. " +
        "For your protection, your account will be suspended within 24 hours unless you verify your identity immediately."
    val para2 = content?.body_para2 ?: "Please confirm your billing information by visiting the secure link below:"
    val para3 = content?.body_para3
        ?: "Failure to respond will result in permanent loss of access to your funds. This is an immediate action required notice."
    val signoff = content?.body_signoff ?: "PayPal Security Team"
    val signin_label = content?.body_signin_label ?: "Or sign in directly:"
    return """
<div style="font-family: Arial, sans-serif; color:#222; max-width: 560px;">
  <p>$greeting</p>
  <p>$para1</p>
  <p>$para2</p>
  <p><a href="https://paypa1-billing-alerts.com/verify?ref=acct">https://www.paypal.com/account/verify</a></p>
  <p>$signin_label <a href="https://www.&#1088;ay&#1088;al.com/signin">https://www.paypal.com/signin</a></p>
  <p>$para3</p>
  <p>$signoff</p>
</div>
""".trim()
}

private fun build_demo_metadata(): MailItemMetadata = MailItemMetadata(
    is_read = false,
    is_starred = false,
    is_pinned = false,
    is_trashed = false,
    is_archived = false,
    is_spam = false,
    has_attachments = false,
    item_type = "received",
)

fun build_demo_phishing_inbox_item(): InboxItem {
    val now = System.currentTimeMillis()
    val ts = java.time.Instant.ofEpochMilli(now).toString()
    val raw = MailItem(
        id = DEMO_PHISH_ITEM_ID,
        item_type = "received",
        thread_token = DEMO_PHISH_THREAD_TOKEN,
        thread_message_count = 1,
        is_external = true,
        is_read = false,
        message_ts = ts,
        created_at = ts,
    )
    return InboxItem(
        id = DEMO_PHISH_ITEM_ID,
        thread_token = DEMO_PHISH_THREAD_TOKEN,
        thread_message_count = 1,
        sender_name = DEMO_PHISH_SENDER_NAME,
        sender_email = DEMO_PHISH_SENDER_EMAIL,
        subject = demo_phish_subject(),
        preview = demo_phish_preview(),
        timestamp = ts,
        is_read = false,
        is_starred = false,
        is_encrypted = false,
        has_attachments = false,
        is_trashed = false,
        is_archived = false,
        is_spam = false,
        labels = emptyList(),
        tag_tokens = emptyList(),
        raw_item = raw,
    )
}

fun build_demo_phishing_thread_message(): ThreadMessageDecrypted {
    val ts = java.time.Instant.now().toString()
    val raw = ThreadMessageItem(
        id = DEMO_PHISH_ITEM_ID,
        item_type = "received",
        is_external = true,
        message_ts = ts,
        created_at = ts,
        metadata = build_demo_metadata(),
    )
    return ThreadMessageDecrypted(
        id = DEMO_PHISH_ITEM_ID,
        sender_name = DEMO_PHISH_SENDER_NAME,
        sender_email = DEMO_PHISH_SENDER_EMAIL,
        to_label = DEMO_PHISH_ACCOUNT_EMAIL,
        timestamp = ts,
        body_text = demo_phish_body_text(),
        body_html = demo_phish_body_html(),
        is_encrypted = false,
        is_read = false,
        raw_item = raw,
        to_addresses = listOf(DEMO_PHISH_ACCOUNT_EMAIL),
        cc_addresses = emptyList(),
        has_attachments = false,
    )
}
