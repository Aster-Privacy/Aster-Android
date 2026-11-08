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

package org.astermail.android.crypto

import java.security.SecureRandom
import org.astermail.android.api.keys.ExternalKeyInfo

sealed interface ClientPgpCopyOutcome {
    data class Copies(val copies: Map<String, String>) : ClientPgpCopyOutcome
    data class Failed(val too_large: Boolean) : ClientPgpCopyOutcome
}

object ClientPgpCopies {
    const val PLACEHOLDER_BODY = " "
    const val MAX_COPY_ATTACHMENT_BYTES = 11L * 1024L * 1024L
    private const val BASE_COPY_BYTES = 10L * 1024L * 1024L
    private const val MAX_COPY_BYTES = 48L * 1024L * 1024L
    private const val MAX_TOTAL_COPY_BYTES = 80L * 1024L * 1024L
    private const val ARMOR_HEADER = "-----BEGIN PGP MESSAGE-----"
    private val random = SecureRandom()

    fun encrypts_for(info: ExternalKeyInfo?): Boolean =
        info != null && info.found && !info.public_key.isNullOrBlank() && info.will_encrypt

    fun outside_recipients(addresses: List<String>, is_internal: (String) -> Boolean): List<String> {
        val seen = HashSet<String>()
        return addresses
            .map { it.trim() }
            .filter { it.isNotEmpty() && !is_internal(it) && seen.add(it.lowercase()) }
    }

    fun recipients_without_keys(
        recipients: List<String>,
        discovered: List<ExternalKeyInfo>,
    ): List<String> {
        val by_address = discovered.associateBy { it.email.trim().lowercase() }
        return recipients.filterNot { encrypts_for(by_address[it.trim().lowercase()]) }
    }

    fun recipient_keys(
        recipients: List<String>,
        discovered: List<ExternalKeyInfo>,
    ): Map<String, String>? {
        val by_address = discovered.associateBy { it.email.trim().lowercase() }
        val keys = LinkedHashMap<String, String>()
        for (recipient in recipients) {
            val info = by_address[recipient.lowercase()]
            if (!encrypts_for(info)) return null
            keys[recipient] = info!!.public_key!!
        }
        return keys
    }

    fun build_signed_mime_entity(mime: ByteArray, signature: String, micalg: String): ByteArray {
        val boundary = "--=_astermail_signed_${random_token()}"
        val head = (
            "Content-Type: multipart/signed; micalg=\"$micalg\"; " +
                "protocol=\"application/pgp-signature\"; boundary=\"$boundary\"\r\n\r\n--$boundary\r\n"
            ).toByteArray(Charsets.UTF_8)
        val tail = (
            "\r\n--$boundary\r\n" +
                "Content-Type: application/pgp-signature; name=\"signature.asc\"\r\n" +
                "Content-Description: OpenPGP digital signature\r\n" +
                "Content-Disposition: attachment; filename=\"signature.asc\"\r\n\r\n" +
                to_crlf(signature.trim()) +
                "\r\n--$boundary--\r\n"
            ).toByteArray(Charsets.UTF_8)
        return head + mime + tail
    }

    fun copy_limit_bytes(attachment_bytes: Long): Long =
        minOf(MAX_COPY_BYTES, BASE_COPY_BYTES + 2L * attachment_bytes)

    fun is_armored_message(copy: String): Boolean = copy.trimStart().startsWith(ARMOR_HEADER)

    fun encrypt_copies(
        entity: ByteArray,
        recipient_keys: Map<String, String>,
        attachment_bytes: Long,
    ): Map<String, String>? =
        (encrypt_copies_outcome(entity, recipient_keys, attachment_bytes) as? ClientPgpCopyOutcome.Copies)?.copies

    fun encrypt_copies_outcome(
        entity: ByteArray,
        recipient_keys: Map<String, String>,
        attachment_bytes: Long,
        encrypt: (ByteArray, String) -> String? = { bytes, key -> PgpEncryptor.encrypt_bytes_to_keys(bytes, listOf(key)) },
    ): ClientPgpCopyOutcome {
        if (attachment_bytes > MAX_COPY_ATTACHMENT_BYTES) return ClientPgpCopyOutcome.Failed(too_large = true)
        if (recipient_keys.isEmpty()) return ClientPgpCopyOutcome.Failed(too_large = false)
        val limit = copy_limit_bytes(attachment_bytes)
        val copies = LinkedHashMap<String, String>()
        var total = 0L
        for ((recipient, key) in recipient_keys) {
            val copy = encrypt(entity, key)
            if (copy == null || !is_armored_message(copy)) return ClientPgpCopyOutcome.Failed(too_large = false)
            if (copy.length > limit) return ClientPgpCopyOutcome.Failed(too_large = true)
            total += copy.length
            if (total > MAX_TOTAL_COPY_BYTES) return ClientPgpCopyOutcome.Failed(too_large = true)
            copies[recipient] = copy
        }
        return ClientPgpCopyOutcome.Copies(copies)
    }

    private fun to_crlf(text: String): String =
        text.replace("\r\n", "\n").replace("\n", "\r\n")

    private fun random_token(): String {
        val bytes = ByteArray(16)
        random.nextBytes(bytes)
        return bytes.joinToString("") { "%02x".format(it) }
    }
}
