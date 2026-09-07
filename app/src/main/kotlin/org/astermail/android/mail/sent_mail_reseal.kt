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

import java.security.SecureRandom
import java.util.Base64
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CancellationException
import org.astermail.android.api.mail.MailApi
import org.astermail.android.api.mail.MailItem
import org.astermail.android.api.mail.UpdateAttachmentMetaRequest
import org.astermail.android.api.mail.UpdateMailItemEnvelopeRequest
import org.astermail.android.crypto.AesGcm
import org.astermail.android.crypto.PasswordKdf

data class SentMailResealSummary(
    val checked: Int = 0,
    val rewritten: Int = 0,
    val unreadable: Int = 0,
    val failed: Int = 0,
)

object SentMailResealCrypto {
    private const val SALT_LENGTH = 16
    private const val NONCE_LENGTH = 12
    private const val PBKDF2_ITERATIONS = 310000
    private val INLINE_SENTINEL = byteArrayOf(1)
    private val random = SecureRandom()

    fun sentinel_nonce_b64(): String = Base64.getEncoder().encodeToString(INLINE_SENTINEL)

    fun is_sentinel_nonce(nonce_b64: String?): Boolean {
        if (nonce_b64.isNullOrBlank()) return false
        val decoded = runCatching { Base64.getDecoder().decode(nonce_b64.trim()) }.getOrNull() ?: return false
        return decoded.contentEquals(INLINE_SENTINEL)
    }

    fun open(sealed_b64: String, passphrase: ByteArray): ByteArray? {
        val data = runCatching { Base64.getDecoder().decode(sealed_b64.trim()) }.getOrNull() ?: return null
        if (data.size <= SALT_LENGTH + NONCE_LENGTH) return null
        val salt = data.copyOfRange(0, SALT_LENGTH)
        val nonce = data.copyOfRange(SALT_LENGTH, SALT_LENGTH + NONCE_LENGTH)
        val ciphertext = data.copyOfRange(SALT_LENGTH + NONCE_LENGTH, data.size)
        val key = PasswordKdf.derive_aes_key(passphrase, salt, PBKDF2_ITERATIONS)
        return try {
            AesGcm.decrypt(key, nonce, ciphertext)
        } catch (_: Throwable) {
            null
        } finally {
            key.fill(0)
        }
    }

    fun seal(plaintext: ByteArray, passphrase: ByteArray): String {
        val salt = ByteArray(SALT_LENGTH).also { random.nextBytes(it) }
        val nonce = ByteArray(NONCE_LENGTH).also { random.nextBytes(it) }
        val key = PasswordKdf.derive_aes_key(passphrase, salt, PBKDF2_ITERATIONS)
        return try {
            Base64.getEncoder().encodeToString(salt + nonce + AesGcm.encrypt(key, nonce, plaintext))
        } finally {
            key.fill(0)
        }
    }

    fun placeholder_meta_nonce_b64(existing_b64: String?): String {
        val existing = existing_b64?.let { runCatching { Base64.getDecoder().decode(it.trim()) }.getOrNull() }
        if (existing_b64 != null && existing != null && existing.size == NONCE_LENGTH) return existing_b64
        return Base64.getEncoder().encodeToString(ByteArray(NONCE_LENGTH))
    }
}

@Singleton
class SentMailResealer @Inject constructor(
    private val mail_api: MailApi,
) {
    suspend fun run(
        old_passphrase: ByteArray,
        new_passphrase: ByteArray,
        on_progress: ((SentMailResealSummary) -> Unit)? = null,
    ): SentMailResealSummary {
        var summary = SentMailResealSummary()
        var cursor: String? = null
        while (true) {
            val page = list_sent_page(cursor)
            for (item in page.items) {
                summary = when (reseal_item(item, old_passphrase, new_passphrase)) {
                    Outcome.REWRITTEN -> summary.copy(checked = summary.checked + 1, rewritten = summary.rewritten + 1)
                    Outcome.UNREADABLE -> summary.copy(checked = summary.checked + 1, unreadable = summary.unreadable + 1)
                    Outcome.FAILED -> summary.copy(checked = summary.checked + 1, failed = summary.failed + 1)
                    Outcome.SKIPPED -> summary.copy(checked = summary.checked + 1)
                }
                on_progress?.invoke(summary)
            }
            cursor = page.next_cursor
            if (!page.has_more || cursor == null) break
        }
        return summary
    }

    private suspend fun list_sent_page(cursor: String?) = retry(3) {
        mail_api.list_messages(
            limit = PAGE_SIZE,
            cursor = cursor,
            item_type = "sent",
            skip_total = true,
            include_envelope = true,
        )
    }

    private suspend fun <T> retry(attempts: Int, block: suspend () -> T): T {
        var last: Throwable? = null
        repeat(attempts) {
            try {
                return block()
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (t: Throwable) {
                last = t
            }
        }
        throw last ?: IllegalStateException("sent mail listing failed")
    }

    private suspend fun reseal_item(item: MailItem, old_passphrase: ByteArray, new_passphrase: ByteArray): Outcome {
        val sealed = item.encrypted_envelope
        if (sealed.isNullOrBlank() || !SentMailResealCrypto.is_sentinel_nonce(item.envelope_nonce)) return Outcome.SKIPPED
        val plaintext = SentMailResealCrypto.open(sealed, old_passphrase)
        if (plaintext == null) {
            return if (SentMailResealCrypto.open(sealed, new_passphrase) != null) Outcome.SKIPPED else Outcome.UNREADABLE
        }
        val resealed = try {
            SentMailResealCrypto.seal(plaintext, new_passphrase)
        } finally {
            plaintext.fill(0)
        }
        try {
            mail_api.update_envelope(
                item.id,
                UpdateMailItemEnvelopeRequest(
                    encrypted_envelope = resealed,
                    envelope_nonce = SentMailResealCrypto.sentinel_nonce_b64(),
                ),
            )
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (_: Throwable) {
            return Outcome.FAILED
        }
        val has_attachments = item.has_attachments == true || (item.attachment_count ?: 0) > 0
        if (!has_attachments) return Outcome.REWRITTEN
        return if (reseal_attachment_meta(item.id, old_passphrase, new_passphrase)) Outcome.REWRITTEN else Outcome.FAILED
    }

    private suspend fun reseal_attachment_meta(item_id: String, old_passphrase: ByteArray, new_passphrase: ByteArray): Boolean {
        return try {
            val attachments = mail_api.list_attachments(item_id).attachments
            for (attachment in attachments) {
                val plaintext = SentMailResealCrypto.open(attachment.encrypted_meta, old_passphrase) ?: return false
                val resealed = try {
                    SentMailResealCrypto.seal(plaintext, new_passphrase)
                } finally {
                    plaintext.fill(0)
                }
                mail_api.update_attachment_meta(
                    attachment.id,
                    UpdateAttachmentMetaRequest(
                        encrypted_meta = resealed,
                        meta_nonce = SentMailResealCrypto.placeholder_meta_nonce_b64(attachment.meta_nonce),
                    ),
                )
            }
            true
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (_: Throwable) {
            false
        }
    }

    private enum class Outcome { REWRITTEN, SKIPPED, UNREADABLE, FAILED }

    private companion object {
        const val PAGE_SIZE = 100
    }
}
