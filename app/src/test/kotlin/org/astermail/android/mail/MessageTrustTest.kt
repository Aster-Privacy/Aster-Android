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

class MessageTrustTest {

    private fun headers(value: String) = listOf("From" to "a@example.com", "X-Aster-Trust" to value)

    @Test
    fun `parses a phishing verdict with score and reasons in order`() {
        val trust = parse_message_trust(headers("verdict=phishing; score=87; reasons=impersonation,self-name,deceptive-link"))!!
        assertEquals(TrustVerdict.phishing, trust.verdict)
        assertEquals(87, trust.score)
        assertEquals(
            listOf(TrustReason.impersonation, TrustReason.self_name, TrustReason.deceptive_link),
            trust.reasons,
        )
    }

    @Test
    fun `parses a suspicious verdict case insensitively`() {
        val trust = parse_message_trust(listOf("x-aster-trust" to "Verdict=Suspicious;reasons=Form"))!!
        assertEquals(TrustVerdict.suspicious, trust.verdict)
        assertNull(trust.score)
        assertEquals(listOf(TrustReason.form), trust.reasons)
    }

    @Test
    fun `ignores unknown reasons and keeps at most four`() {
        val trust = parse_message_trust(
            headers("verdict=phishing; reasons=brand-new,impersonation,form,lookalike-sender,reported-link,address-in-name"),
        )!!
        assertEquals(
            listOf(TrustReason.impersonation, TrustReason.form, TrustReason.lookalike_sender, TrustReason.reported_link),
            trust.reasons,
        )
    }

    @Test
    fun `returns null without the header or with another verdict`() {
        assertNull(parse_message_trust(listOf("From" to "a@example.com")))
        assertNull(parse_message_trust(headers("verdict=clean; score=2")))
        assertNull(parse_message_trust(headers("score=99; reasons=form")))
    }

    @Test
    fun `drops an out of range score`() {
        assertNull(parse_message_trust(headers("verdict=phishing; score=180"))!!.score)
    }

    @Test
    fun `collects report hosts from links and skips local addresses`() {
        val html = """
            <a href="https://Login.Example.COM./reset?a=1&amp;b=2">x</a>
            <a href='http://user@shop.example.org:8080/path'>y</a>
            <a href="https://login.example.com/other">dup</a>
            <a href="http://192.168.1.10/admin">ip</a>
            <a href="http://localhost:3000">local</a>
            <a href="https://intranet/">single</a>
            <a href="https://[::1]/">v6</a>
            <a href="mailto:a@example.com">mail</a>
            <a href="https://bücher.example/">idn</a>
        """.trimIndent()
        assertEquals(
            listOf("login.example.com", "shop.example.org", "xn--bcher-kva.example"),
            phishing_report_hosts(html),
        )
    }

    @Test
    fun `returns no hosts for empty html`() {
        assertEquals(emptyList<String>(), phishing_report_hosts(null))
        assertEquals(emptyList<String>(), phishing_report_hosts(""))
    }
}
