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

import org.astermail.android.ui.mail.detect_inbox_order_hint
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.Locale

class quoted_history_test {
    private val reply_subject = "Re: me again - Newsletter bug?"
    private val support_email = "hello@astermail.org"
    private val support_name = "Aster Mail"

    private val reply_text = "Hi Sam,\n\nThanks for the report. The newsletter bug is fixed now, so the next issue " +
        "should arrive normally. Let us know if it happens again.\n\nBest,\nThe Aster team"

    private val payment_lines = listOf(
        "Payment receipt",
        "Thanks for your purchase.",
        "We received your payment of \$14.49 for your Aster Mail Star plan.",
        "Amount paid: \$14.49",
        "Your plan renews on December 10, 2026.",
        "View or download the invoice in your billing settings.",
    )

    private fun details(subject: String, text: String, html: String?) =
        extract_email_details(subject, text, html, support_email, support_name, Locale.US)

    private fun assert_no_card(subject: String, text: String, html: String?) {
        val result = details(subject, text, html)
        assertFalse("purchase", result.has_purchase_details)
        assertFalse("shipping", result.has_shipping_details)
    }

    @Test
    fun `the quoted payment confirmation alone is a receipt`() {
        val result = details("Payment receipt", payment_lines.joinToString("\n"), null)
        assertTrue(result.has_purchase_details)
    }

    @Test
    fun `ignores a payment confirmation quoted in an html blockquote`() {
        val html = "<div dir=\"ltr\">" + reply_text.replace("\n", "<br>") + "</div>" +
            "<div>On Mon, Nov 10, 2026 at 3:12 PM Aster Mail &lt;hello@astermail.org&gt; wrote:<br></div>" +
            "<blockquote type=\"cite\" style=\"margin:0 0 0 .8ex;border-left:1px solid #ccc;padding-left:1ex\">" +
            payment_lines.joinToString("") { "<p>$it</p>" } +
            "</blockquote>"
        assert_no_card(reply_subject, "", html)
    }

    @Test
    fun `ignores a payment confirmation quoted in a quote container`() {
        val html = "<div>" + reply_text.replace("\n", "<br>") + "</div>" +
            "<div class=\"aster_quote\"><div class=\"aster_quote_attr\">On Mon, Nov 10, 2026, Aster Mail wrote:</div>" +
            "<blockquote>" + payment_lines.joinToString("") { "<div>$it</div>" } + "</blockquote></div>"
        assert_no_card(reply_subject, "", html)
    }

    @Test
    fun `ignores a payment confirmation quoted after an english attribution`() {
        val text = reply_text + "\n\nOn Mon, Nov 10, 2026 at 3:12 PM Aster Mail <hello@astermail.org> wrote:\n" +
            payment_lines.joinToString("\n") { "> $it" }
        assert_no_card(reply_subject, text, null)
    }

    @Test
    fun `ignores a payment confirmation below a german header block`() {
        val text = reply_text + "\n\nVon: Aster Mail <hello@astermail.org>\n" +
            "Datum: Montag, 10. November 2026 um 15:12\n" +
            "An: sam@example.com\n" +
            "Betreff: Zahlungsbeleg\n\n" +
            payment_lines.joinToString("\n")
        assert_no_card(reply_subject, text, null)
    }

    @Test
    fun `ignores a payment confirmation below a german attribution`() {
        val text = reply_text + "\n\nAm 10.11.2026 um 15:12 schrieb Aster Mail <hello@astermail.org>:\n\n" +
            payment_lines.joinToString("\n")
        assert_no_card("AW: me again - Newsletter bug?", text, null)
    }

    @Test
    fun `ignores a payment confirmation below an original message marker`() {
        val text = reply_text + "\n\n-----Original Message-----\n" +
            payment_lines.joinToString("\n")
        assert_no_card(reply_subject, text, null)
    }

    @Test
    fun `the inbox line ignores a quoted payment confirmation in the preview`() {
        val preview = "Fixed now, thanks. On Nov 10, 2026, Aster Mail <hello@astermail.org> wrote: " +
            "Payment receipt. Thanks for your purchase. Amount paid: \$14.49"
        assertNull(detect_inbox_order_hint(reply_subject, preview))
        val german_preview = "Ist behoben, danke. Von: Aster Mail <hello@astermail.org> Datum: 10.11.2026 " +
            "Betreff: Payment receipt. Thanks for your purchase. Amount paid: \$14.49"
        assertNull(detect_inbox_order_hint(reply_subject, german_preview))
    }

    @Test
    fun `the inbox line still flags a real receipt`() {
        val preview = "Payment receipt. Thanks for your purchase. Amount paid: \$14.49"
        assertNotNull(detect_inbox_order_hint("Your Aster Mail receipt", preview))
    }

    @Test
    fun `still detects a receipt sent as html`() {
        val html = "<html><body><h1>Payment receipt</h1>" +
            payment_lines.drop(1).joinToString("") { "<p>$it</p>" } + "</body></html>"
        assertTrue(details("Your Aster Mail receipt", "", html).has_purchase_details)
    }

    @Test
    fun `still detects a forwarded receipt with a short note on top`() {
        val text = "See below for the receipt.\n\n---------- Forwarded message ---------\n" +
            "From: Aster Mail <hello@astermail.org>\n" +
            "Date: Mon, Nov 10, 2026 at 3:12 PM\n" +
            "Subject: Payment receipt\n\n" +
            payment_lines.joinToString("\n")
        assertTrue(details("Fwd: Payment receipt", text, null).has_purchase_details)
    }

    @Test
    fun `still detects a receipt written in the reply itself`() {
        val text = payment_lines.joinToString("\n") +
            "\n\nOn Mon, Nov 10, 2026 at 3:12 PM Sam <sam@example.com> wrote:\n> Can you send my receipt again?"
        assertTrue(details("Re: Receipt request", text, null).has_purchase_details)
    }
}
