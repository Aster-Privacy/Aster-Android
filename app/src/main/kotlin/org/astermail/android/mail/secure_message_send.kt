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

import org.astermail.android.api.send.ExternalAttachmentPayload
import org.astermail.android.api.send.SecureMessageFieldPayload
import org.astermail.android.api.send.SecureMessagePayload
import org.astermail.android.crypto.SecureMessageAttachment
import org.astermail.android.crypto.SecureMessageCrypto
import org.astermail.android.crypto.SecureMessageField
import java.util.Base64

const val SECURE_MESSAGE_PLACEHOLDER = "[secure message]"

fun uses_password_sealed_message(expiry_password: String?): Boolean = !expiry_password.isNullOrEmpty()

private fun field_payload(field: SecureMessageField): SecureMessageFieldPayload =
    SecureMessageFieldPayload(ciphertext = field.ciphertext, nonce = field.nonce)

fun build_secure_message_payload(
    password: String,
    subject: String,
    body_html: String,
    attachments: List<ExternalAttachmentPayload>,
): SecureMessagePayload {
    val sealed = SecureMessageCrypto.seal(
        password = password,
        subject = subject,
        body = body_html,
        attachments = attachments.map { attachment ->
            SecureMessageAttachment(
                filename = attachment.filename,
                content_type = attachment.content_type,
                data = Base64.getMimeDecoder().decode(attachment.data),
            )
        },
    )
    return SecureMessagePayload(
        kdf_salt = sealed.kdf_salt,
        auth_proof = sealed.auth_proof,
        kem_ciphertext = sealed.kem_ciphertext,
        encrypted_kem_seed = sealed.encrypted_kem_seed,
        kem_seed_nonce = sealed.kem_seed_nonce,
        encrypted_subject = field_payload(sealed.encrypted_subject),
        encrypted_body = field_payload(sealed.encrypted_body),
        attachments_bundle = sealed.attachments_bundle,
    )
}
