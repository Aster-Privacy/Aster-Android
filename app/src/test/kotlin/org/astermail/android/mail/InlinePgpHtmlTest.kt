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

import org.astermail.android.crypto.PgpDecryptor
import org.astermail.android.crypto.PgpEncryptor
import org.astermail.android.crypto.PgpKeyGenerator
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class InlinePgpHtmlTest {

    private val passphrase = "correct horse battery staple".toCharArray()

    private val recipient by lazy {
        PgpKeyGenerator.generate("Recipient", "recipient@astermail.org", passphrase)
    }

    private val plaintext = "Hi,\n\nThis is an inline PGP test from another provider.\n\nDavid"

    private fun inline_armor(): String = PgpEncryptor.encrypt_to_keys(
        plaintext,
        listOf(recipient.armored_public_key),
    )!!
        .replace("\r\n", "\n")
        .replace(
            "$PGP_ENCRYPTED_MESSAGE_HEADER\n",
            "$PGP_ENCRYPTED_MESSAGE_HEADER\nVersion: ProtonMail\n",
        )
        .replace("\n", "\r\n")

    private fun mail_parser_text_to_html(text: String): String = buildString {
        append("<html><body>")
        for (ch in text) {
            when (ch) {
                '\n' -> append("<br/>")
                '<' -> append("&lt;")
                '\r' -> Unit
                else -> append(ch)
            }
        }
        append("</body></html>")
    }

    private fun decrypt(armor: String?): String? = PgpDecryptor.decrypt_with_status(
        armor!!,
        recipient.armored_private_key,
        passphrase,
        null,
    ).plaintext

    @Test
    fun parser_html_copy_unwraps_to_decryptable_armor() {
        val html = mail_parser_text_to_html(inline_armor())

        assertEquals(plaintext, decrypt(inline_pgp_html_armor(html)))
    }

    @Test
    fun paragraph_wrapped_armor_with_encoded_plus_signs_decrypts() {
        val armor = inline_armor().replace("\r\n", "\n")
        val html = "<div><p>" + armor.split("\n").joinToString("</p>\n<p>").replace("+", "&#43;") +
            "</p></div>"

        assertEquals(plaintext, decrypt(inline_pgp_html_armor(html)))
    }

    @Test
    fun html_without_an_encrypted_block_is_ignored() {
        assertNull(inline_pgp_html_armor("<p>Hello</p>"))
        assertNull(inline_pgp_html_armor("<p>-----BEGIN PGP MESSAGE-----</p><p>abc</p>"))
    }
}
