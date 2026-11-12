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

import org.astermail.android.crypto.PgpDecryptionResult
import org.astermail.android.crypto.PgpSignatureStatus
import org.astermail.android.mail.ratchet.RatchetIdentityPinRules
import org.astermail.android.mail.ratchet.SavedKeyCheck

data class PgpBodyFields(
    val body_text: String,
    val body_html: String?,
    val pgp_encrypted: Boolean,
    val pgp_signature: PgpSignatureStatus,
)

private const val PGP_ARMOR_PREFIX = "-----BEGIN PGP"

fun settle_sender_signature(
    result: PgpDecryptionResult?,
    saved_key_check: () -> SavedKeyCheck?,
    is_own_signature: () -> Boolean,
): PgpDecryptionResult? {
    if (result == null || result.signature != PgpSignatureStatus.VALID) return result
    if (RatchetIdentityPinRules.signature_trusted(saved_key_check())) return result
    if (is_own_signature()) return result
    return result.copy(signature = PgpSignatureStatus.UNVERIFIED)
}

fun resolve_pgp_body_fields(
    fields: PgpBodyFields,
    decrypt: (String) -> PgpDecryptionResult?,
): PgpBodyFields {
    var body_text = fields.body_text
    var body_html = fields.body_html
    var pgp_encrypted = fields.pgp_encrypted
    var pgp_signature = fields.pgp_signature

    var text_pgp_decrypted = false
    if (body_starts_with(body_text, PGP_ARMOR_PREFIX)) {
        val result = decrypt(body_text)
        val plaintext = result?.plaintext
        if (result != null && plaintext != null) {
            body_text = plaintext
            pgp_encrypted = true
            pgp_signature = merge_pgp_signature(pgp_signature, result.signature)
            text_pgp_decrypted = true
        }
    }
    val html_armor = body_html
        ?.takeIf { !body_starts_with(it, PGP_ARMOR_PREFIX) }
        ?.takeIf { text_pgp_decrypted || !is_quoted_inline_pgp(body_text, it) }
        ?.let { inline_pgp_html_armor(it) }
    if (html_armor != null) {
        if (text_pgp_decrypted) {
            body_html = null
        } else {
            val result = decrypt(html_armor)
            val plaintext = result?.plaintext
            if (result != null && plaintext != null) {
                body_text = plaintext
                body_html = null
                pgp_encrypted = true
                pgp_signature = merge_pgp_signature(pgp_signature, result.signature)
            }
        }
    }
    val armored_html = body_html
    if (armored_html != null && body_starts_with(armored_html, PGP_ARMOR_PREFIX)) {
        val result = decrypt(armored_html)
        val plaintext = result?.plaintext
        if (result != null && plaintext != null) {
            body_html = plaintext
            pgp_encrypted = true
            pgp_signature = merge_pgp_signature(pgp_signature, result.signature)
        }
    }
    return PgpBodyFields(body_text, body_html, pgp_encrypted, pgp_signature)
}

fun pgp_placeholder_envelope_of(
    subject: String,
    body_text: String,
    is_encrypted_armor: Boolean,
): DecryptedEnvelope = DecryptedEnvelope(
    subject = subject,
    body_text = body_text,
    body_html = null,
    from_name = "",
    from_email = "",
    to = emptyList(),
    cc = emptyList(),
    sent_at = null,
    pgp_encrypted = false,
    is_pgp_placeholder = is_encrypted_armor,
)
