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

import org.astermail.android.api.mail.MailItemMetadata
import org.junit.Assert.assertEquals
import org.junit.Test

class AccountAccessCategoryTest {

    private val ses_headers = listOf(
        "Return-Path" to "<0101019a5b7c1234-abcd@us-west-2.amazonses.com>",
        "DKIM-Signature" to "v=1; a=rsa-sha256; d=browser-sync.example; s=s1; bh=abc",
        "Feedback-ID" to "1.us-west-2.abc=:AmazonSES",
        "X-SES-Outgoing" to "2026.11.06-54.240.1.1",
    )

    private fun envelope(
        from_email: String,
        subject: String,
        raw_headers: List<Pair<String, String>> = emptyList(),
        list_unsubscribe: String? = null,
    ): DecryptedEnvelope = DecryptedEnvelope(
        subject = subject,
        body_text = "",
        body_html = null,
        from_name = "",
        from_email = from_email,
        to = emptyList(),
        cc = emptyList(),
        sent_at = "2026-11-06T04:25:20Z",
        raw_headers = raw_headers,
        list_unsubscribe = list_unsubscribe,
        sender_verification = null,
    )

    @Test
    fun english_sign_in_verification_lands_in_primary() {
        val env = envelope("accounts@browser-sync.example", "Verify this sign-in", ses_headers)
        assertEquals("primary", classify(env, null))
    }

    @Test
    fun english_code_subject_lands_in_primary() {
        val env = envelope("accounts@browser-sync.example", "Use 482913 to confirm your account", ses_headers)
        assertEquals("primary", classify(env, null))
    }

    @Test
    fun simplified_chinese_verification_lands_in_primary() {
        val env = envelope("accounts@browser-sync.example", "验证此次登录", ses_headers)
        assertEquals("primary", classify(env, null))
    }

    @Test
    fun simplified_chinese_code_subject_lands_in_primary() {
        val env = envelope("accounts@browser-sync.example", "您的验证码：482913", ses_headers)
        assertEquals("primary", classify(env, null))
    }

    @Test
    fun simplified_chinese_account_confirmation_lands_in_primary() {
        val env = envelope("accounts@browser-sync.example", "请确认您的帐户", ses_headers)
        assertEquals("primary", classify(env, null))
    }

    @Test
    fun verification_code_header_lands_in_primary_in_any_language() {
        val env = envelope(
            "accounts@browser-sync.example",
            "Account notice",
            ses_headers + ("X-Verify-Short-Code" to "482913"),
        )
        assertEquals("primary", classify(env, null))
    }

    @Test
    fun other_languages_land_in_primary() {
        val subjects = listOf(
            "Ihr Bestätigungscode",
            "Votre code de sécurité",
            "Tu código de verificación",
            "Seu código de verificação",
            "認証コードのお知らせ",
            "인증 코드 안내",
            "Ваш код подтверждения",
            "Reset your password",
            "Your sign-in link",
            "Confirm your email address",
            "123456 is your login code",
        )
        for (subject in subjects) {
            val env = envelope("no-reply@service.example.test", subject, ses_headers)
            assertEquals(subject, "primary", classify(env, null))
        }
    }

    @Test
    fun new_sign_in_alert_stays_updates() {
        val env = envelope(
            "no-reply@github.com",
            "A new sign-in to your account",
            listOf("Auto-Submitted" to "auto-generated"),
        )
        assertEquals("updates", classify(env, null))
    }

    @Test
    fun promo_code_is_not_account_access() {
        val env = envelope(
            "deals@shop.example.test",
            "Use promo code SAVE20 today only",
            ses_headers,
            list_unsubscribe = "<mailto:unsubscribe@shop.example.test>",
        )
        assertEquals("promotions", classify(env, null))
    }

    @Test
    fun pinned_category_still_wins() {
        val env = envelope("accounts@browser-sync.example", "Verify this sign-in", ses_headers)
        val meta = MailItemMetadata(category = "updates", category_pinned = true)
        assertEquals("updates", classify(env, meta))
    }

    @Test
    fun mail_rule_category_still_wins() {
        val env = envelope("accounts@browser-sync.example", "Verify this sign-in", ses_headers)
        assertEquals("updates", classify(env, null, rule_category = "updates"))
    }
}
