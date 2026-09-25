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

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class merchant_avatar_test {
    @Test
    fun `uses the logo when the merchant matches the sender domain`() {
        assertTrue(merchant_matches_sender_domain("Amazon", "auto-confirm@amazon.com"))
        assertTrue(merchant_matches_sender_domain("Best Buy", "orders@emails.bestbuy.com"))
        assertTrue(merchant_matches_sender_domain("Aster Privacy", "billing@aster.co.uk"))
    }

    @Test
    fun `skips the logo when a payment processor sends for the merchant`() {
        assertFalse(merchant_matches_sender_domain("Aster Privacy", "receipts+acct_1@stripe.com"))
        assertFalse(merchant_matches_sender_domain("Coffee Shop", "service@paypal.com"))
    }

    @Test
    fun `skips the logo when the sender has no domain or the names are too short`() {
        assertFalse(merchant_matches_sender_domain("Amazon", ""))
        assertFalse(merchant_matches_sender_domain("HP", "orders@hp.com"))
    }

    @Test
    fun `treats an all white logo as blank`() {
        assertTrue(is_blank_logo_pixels(IntArray(64) { 0xFFFFFFFF.toInt() }))
    }

    @Test
    fun `treats a transparent logo as blank`() {
        assertTrue(is_blank_logo_pixels(IntArray(64) { 0x00000000 }))
        assertTrue(is_blank_logo_pixels(IntArray(0)))
    }

    @Test
    fun `keeps a logo that has visible marks`() {
        val pixels = IntArray(100) { index -> if (index < 20) 0xFF1A73E8.toInt() else 0xFFFFFFFF.toInt() }
        assertFalse(is_blank_logo_pixels(pixels))
    }
}
