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

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class VerificationCodeTest {

    @Test
    fun finds_digits_after_keyword() {
        assertEquals("482913", detect_verification_code("Your verification code is 482913"))
    }

    @Test
    fun finds_digits_before_keyword() {
        assertEquals("482913", detect_verification_code("482913 is your sign-in code"))
    }

    @Test
    fun finds_code_split_between_subject_and_preview() {
        assertEquals("7731", detect_verification_code("Your one-time passcode", "7731. It expires in 10 minutes."))
    }

    @Test
    fun joins_space_separated_groups() {
        assertEquals("482913", detect_verification_code("Your login code: 482 913"))
    }

    @Test
    fun joins_dash_separated_groups() {
        assertEquals("48291377", detect_verification_code("Security code 4829-1377"))
    }

    @Test
    fun finds_uppercase_alphanumeric_code() {
        assertEquals("X7K9QP", detect_verification_code("Use X7K9QP to verify your email"))
    }

    @Test
    fun joins_alphanumeric_groups() {
        assertEquals("AB3CD9", detect_verification_code("Your code is AB3-CD9"))
    }

    @Test
    fun ignores_letter_prefix_before_dash() {
        assertEquals("123456", detect_verification_code("G-123456 is your verification code"))
    }

    @Test
    fun accepts_four_and_ten_digit_codes() {
        assertEquals("1234", detect_verification_code("PIN: 1234"))
        assertEquals("1234567890", detect_verification_code("OTP 1234567890"))
    }

    @Test
    fun matches_common_keywords() {
        assertEquals("552014", detect_verification_code("Your 2FA token: 552014"))
        assertEquals("552014", detect_verification_code("One time password 552014"))
        assertEquals("552014", detect_verification_code("Verify with 552014"))
        assertEquals("552014", detect_verification_code("Your passcode is 552014"))
        assertEquals("552014", detect_verification_code("Sign in with 552014"))
    }

    @Test
    fun matches_localized_keywords() {
        assertEquals("552014", detect_verification_code("Ihr Bestätigungscode lautet 552014"))
        assertEquals("552014", detect_verification_code("Tu código es 552014"))
        assertEquals("552014", detect_verification_code("Votre code de vérification : 552014"))
        assertEquals("552014", detect_verification_code("Il tuo codice è 552014"))
        assertEquals("552014", detect_verification_code("Ваш код подтверждения: 552014"))
        assertEquals("552014", detect_verification_code("您的验证码是552014"))
        assertEquals("552014", detect_verification_code("認証コード：552014"))
    }

    @Test
    fun picks_candidate_nearest_keyword() {
        assertEquals(
            "883104",
            detect_verification_code("Request 2023 from device 77 at 10:30", "Your verification code is 883104"),
        )
    }

    @Test
    fun rejects_text_without_keyword() {
        assertNull(detect_verification_code("Meeting moved to room 4821"))
        assertNull(detect_verification_code("Your order 12345678 has shipped"))
        assertNull(detect_verification_code("Tracking number 9400111899"))
    }

    @Test
    fun rejects_empty_input() {
        assertNull(detect_verification_code())
        assertNull(detect_verification_code("", null, "  "))
    }

    @Test
    fun rejects_years() {
        assertNull(detect_verification_code("Your 2026 login summary"))
        assertNull(detect_verification_code("Verify your account before 1999"))
    }

    @Test
    fun rejects_prices() {
        assertNull(detect_verification_code("Login to pay $1299"))
        assertNull(detect_verification_code("Sign in to pay 1299.00"))
        assertNull(detect_verification_code("Sign in to pay 1,299"))
        assertNull(detect_verification_code("Sign in to pay € 1299"))
        assertNull(detect_verification_code("Sign in to pay 1299 EUR"))
        assertNull(detect_verification_code("Login for 1299€ off"))
        assertNull(detect_verification_code("Login to save 2500%"))
    }

    @Test
    fun rejects_phone_numbers() {
        assertNull(detect_verification_code("Verify by calling 555-123-4567"))
        assertNull(detect_verification_code("Verify by calling +1 5551234567"))
        assertNull(detect_verification_code("Verify by calling +4915112345678"))
        assertNull(detect_verification_code("Verify by phone 5551234567"))
        assertNull(detect_verification_code("Verify by calling 555-1234"))
    }

    @Test
    fun rejects_dates_and_times() {
        assertNull(detect_verification_code("Login on 2026-11-11 at 10:30"))
        assertNull(detect_verification_code("Login on 11/11/2026"))
        assertNull(detect_verification_code("Login on 11.11.2026 at 14:45"))
        assertNull(detect_verification_code("New sign-in at 12:30:45"))
    }

    @Test
    fun rejects_numbers_over_ten_digits() {
        assertNull(detect_verification_code("Your code is 12345678901"))
    }

    @Test
    fun rejects_too_short_codes() {
        assertNull(detect_verification_code("Your code is 123"))
    }

    @Test
    fun rejects_reference_numbers_near_keyword() {
        assertNull(detect_verification_code("Sign in to see order 12345678"))
        assertNull(detect_verification_code("Sign in to track order number 12345678"))
        assertNull(detect_verification_code("Sign in to view order #12345678"))
        assertNull(detect_verification_code("Log in to view invoice 88213"))
    }

    @Test
    fun rejects_promotional_and_postal_codes() {
        assertNull(detect_verification_code("Use promo code SAVE2026 today"))
        assertNull(detect_verification_code("Discount code: WINTER25"))
        assertNull(detect_verification_code("Postal code 10115"))
        assertNull(detect_verification_code("Utilisez le code promo NOEL25"))
        assertNull(detect_verification_code("Dein Gutscheincode: XMAS2026"))
    }

    @Test
    fun rejects_lowercase_and_mixed_case_tokens() {
        assertNull(detect_verification_code("Your code is abc123"))
        assertNull(detect_verification_code("Your code is Abc123"))
    }

    @Test
    fun rejects_digits_inside_words() {
        assertNull(detect_verification_code("Your code is 123456abc"))
    }

    @Test
    fun rejects_codes_far_from_keyword() {
        assertNull(
            detect_verification_code(
                "Sign in to see what is new this week in your account and much more besides 482913",
            ),
        )
    }
}
