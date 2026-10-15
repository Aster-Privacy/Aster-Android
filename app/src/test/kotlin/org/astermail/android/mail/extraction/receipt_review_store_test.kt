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
package org.astermail.android.mail.extraction

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class receipt_review_store_test {
    private fun store(storage: memory_receipt_review_storage = memory_receipt_review_storage()): receipt_review_store {
        var clock = 1_000L
        return receipt_review_store(storage) { clock += 1; clock }
    }

    @Test
    fun `saves a correction and reuses the merchant for the same sender`() {
        val storage = memory_receipt_review_storage()
        val reviews = store(storage)
        reviews.save_correction("Mail-1", "Receipts@Stripe.com", "  Northwind   Traders ", "NW-100")
        val review = reviews.review_for("mail-1")
        assertEquals("Northwind Traders", review?.merchant_name)
        assertEquals("NW-100", review?.order_id)
        assertTrue(review?.is_corrected == true)
        assertFalse(review?.confirmed == true)
        assertEquals("Northwind Traders", reviews.remembered_merchant("receipts@stripe.com"))
        val later = reviews.apply_review("mail-2", "receipts@stripe.com", "Stripe", "X-1")
        assertEquals("Northwind Traders", later.first)
        assertEquals("X-1", later.second)
        val reloaded = store(storage).apply_review("mail-1", "receipts@stripe.com", "Stripe", "X-1")
        assertEquals("Northwind Traders", reloaded.first)
        assertEquals("NW-100", reloaded.second)
    }

    @Test
    fun `confirm and hide flags`() {
        val reviews = store()
        reviews.confirm("mail-1")
        assertTrue(reviews.review_for("mail-1")?.confirmed == true)
        assertFalse(reviews.review_for("mail-1")?.hidden == true)
        reviews.hide("mail-1")
        assertTrue(reviews.review_for("mail-1")?.hidden == true)
        assertTrue(reviews.review_for("mail-1")?.confirmed == true)
        reviews.confirm("mail-1")
        assertFalse(reviews.review_for("mail-1")?.hidden == true)
        assertNull(reviews.review_for("mail-2"))
    }

    @Test
    fun `a correction without a merchant stores no sender memory`() {
        val reviews = store()
        reviews.save_correction("mail-1", "orders@shop.example", null, "A-1")
        assertNull(reviews.remembered_merchant("orders@shop.example"))
        assertEquals("A-1", reviews.review_for("mail-1")?.order_id)
    }

    @Test
    fun `sanitizes text and keys`() {
        assertEquals("Acme <Co>".replace("<", "").replace(">", ""), sanitize_review_text(" Acme\u0000 <Co> "))
        assertNull(sanitize_review_text("  ​ "))
        assertEquals(80, sanitize_review_text("a".repeat(120))?.length)
        assertEquals("orders@shop.example", sanitize_review_key("  Orders@Shop.Example "))
    }

    @Test
    fun `ignores corrupt storage`() {
        val reviews = store(memory_receipt_review_storage("not json"))
        assertNull(reviews.review_for("mail-1"))
        assertEquals(ReceiptReviewData.empty, reviews.load())
    }
}
