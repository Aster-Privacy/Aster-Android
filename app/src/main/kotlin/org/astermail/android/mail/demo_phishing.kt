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
private const val DEMO_PHISH_SUBJECT = "Urgent: unusual sign-in activity on your PayPal account"
private const val DEMO_PHISH_PREVIEW = "We detected an unauthorized login attempt. Verify your identity immediately to avoid account suspension."

private const val DEMO_PHISH_BODY_TEXT =
    "Dear customer,\n\n" +
    "We detected an unusual sign-in activity on your PayPal account from a device we do not recognize. " +
    "For your protection, your account will be suspended within 24 hours unless you verify your identity immediately.\n\n" +
    "Please confirm your billing information by visiting the secure link below:\n" +
    "https://www.paypal.com/account/verify\n\n" +
    "Failure to respond will result in permanent loss of access to your funds. " +
    "This is an immediate action required notice.\n\n" +
    "PayPal Security Team"

private val DEMO_PHISH_BODY_HTML = """
<div style="font-family: Arial, sans-serif; color:#222; max-width: 560px;">
  <p>Dear customer,</p>
  <p>We detected an <b>unusual sign-in activity</b> on your PayPal account from a device we do not recognize.
  For your protection, <b>your account will be suspended</b> within 24 hours unless you verify your identity immediately.</p>
  <p>Please confirm your billing information by visiting the secure link below:</p>
  <p><a href="https://paypa1-billing-alerts.com/verify?ref=acct">https://www.paypal.com/account/verify</a></p>
  <p>Or sign in directly: <a href="https://www.&#1088;ay&#1088;al.com/signin">https://www.paypal.com/signin</a></p>
  <p>Failure to respond will result in permanent loss of access to your funds. This is an immediate action required notice.</p>
  <p>PayPal Security Team</p>
</div>
""".trim()

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
        subject = DEMO_PHISH_SUBJECT,
        preview = DEMO_PHISH_PREVIEW,
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
        body_text = DEMO_PHISH_BODY_TEXT,
        body_html = DEMO_PHISH_BODY_HTML,
        is_encrypted = false,
        is_read = false,
        raw_item = raw,
        to_addresses = listOf(DEMO_PHISH_ACCOUNT_EMAIL),
        cc_addresses = emptyList(),
        has_attachments = false,
    )
}
