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

import java.util.Base64
import org.astermail.android.storage.outbox.PendingSendDao
import org.astermail.android.storage.outbox.PendingSendEntity

object OutboxRowSeal {
    private const val prefix = "aox1:"

    fun is_sealed(value: String): Boolean = sealed_bytes(value) != null

    fun seal(cipher: OutboxFileCipher, row: PendingSendEntity): PendingSendEntity = row.copy(
        subject = seal_text(cipher, row.id, "subject", row.subject),
        body_html = seal_text(cipher, row.id, "body_html", row.body_html),
        expiry_password = row.expiry_password?.let { seal_text(cipher, row.id, "expiry_password", it) },
    )

    fun open(cipher: OutboxFileCipher, row: PendingSendEntity): PendingSendEntity = row.copy(
        subject = open_text(cipher, row.id, "subject", row.subject),
        body_html = open_text(cipher, row.id, "body_html", row.body_html),
        expiry_password = row.expiry_password?.let { open_text(cipher, row.id, "expiry_password", it) },
    )

    fun open_or_blank(cipher: OutboxFileCipher, row: PendingSendEntity): PendingSendEntity = try {
        open(cipher, row)
    } catch (_: OutboxPayloadException) {
        row.copy(subject = "", body_html = "", expiry_password = null)
    }

    private fun field_name(row_id: String, field: String): String = "row/$row_id/$field"

    private fun sealed_bytes(value: String): ByteArray? {
        if (!value.startsWith(prefix)) return null
        val decoded = try {
            Base64.getDecoder().decode(value.substring(prefix.length))
        } catch (_: IllegalArgumentException) {
            return null
        }
        return decoded.takeIf { OutboxFileCipher.is_sealed(it) }
    }

    private fun seal_text(cipher: OutboxFileCipher, row_id: String, field: String, value: String): String {
        val plaintext = value.toByteArray(Charsets.UTF_8)
        try {
            return prefix + Base64.getEncoder().encodeToString(cipher.seal(field_name(row_id, field), plaintext))
        } finally {
            plaintext.fill(0)
        }
    }

    private fun open_text(cipher: OutboxFileCipher, row_id: String, field: String, value: String): String {
        val stored = sealed_bytes(value) ?: return value
        val plaintext = cipher.open(field_name(row_id, field), stored)
        try {
            return String(plaintext, Charsets.UTF_8)
        } finally {
            plaintext.fill(0)
        }
    }
}

class SealedPendingSendDao(
    private val inner: PendingSendDao,
    private val cipher: () -> OutboxFileCipher,
) : PendingSendDao by inner {

    override suspend fun upsert(row: PendingSendEntity) = inner.upsert(OutboxRowSeal.seal(cipher(), row))

    override suspend fun get_by_id(id: String): PendingSendEntity? =
        inner.get_by_id(id)?.let { OutboxRowSeal.open_or_blank(cipher(), it) }

    override suspend fun get_all(): List<PendingSendEntity> =
        inner.get_all().map { OutboxRowSeal.open_or_blank(cipher(), it) }

    override suspend fun get_for_account(account_id: String): List<PendingSendEntity> =
        inner.get_for_account(account_id).map { OutboxRowSeal.open_or_blank(cipher(), it) }

    suspend fun get_readable(id: String): PendingSendEntity? =
        inner.get_by_id(id)?.let { OutboxRowSeal.open(cipher(), it) }
}
