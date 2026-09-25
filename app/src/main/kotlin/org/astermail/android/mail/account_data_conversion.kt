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

import android.content.Context
import dagger.hilt.android.qualifiers.ApplicationContext
import java.nio.ByteBuffer
import java.nio.charset.CodingErrorAction
import java.security.MessageDigest
import java.security.SecureRandom
import java.util.Base64
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.coroutines.yield
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import org.astermail.android.api.keys.AccountDataConversionStatus
import org.astermail.android.api.keys.ConversionProgressRequest
import org.astermail.android.api.keys.ConversionWriteResult
import org.astermail.android.api.keys.ConvertAttachmentMetaRequest
import org.astermail.android.api.keys.ConvertSentEnvelopeRequest
import org.astermail.android.api.keys.KeysApi
import org.astermail.android.api.mail.AttachmentResponse
import org.astermail.android.api.mail.MailApi
import org.astermail.android.api.mail.MailItem
import org.astermail.android.api.mail.MailItemsListResponse
import org.astermail.android.api.preferences.PreferencesApi
import org.astermail.android.api.preferences.PreferencesSaveResult
import org.astermail.android.api.preferences.SaveVersionedPreferencesRequest
import org.astermail.android.crypto.AccountDataWriter
import org.astermail.android.crypto.AccountKeyCapabilities
import org.astermail.android.crypto.AesGcm
import org.astermail.android.crypto.PasswordKdf
import org.astermail.android.crypto.PgpDecryptor
import org.astermail.android.crypto.SentCopySeal
import org.astermail.android.storage.SessionKeyStore
import org.astermail.android.util.passphrase_chars

data class AccountDataConversionSummary(
    val checked: Int = 0,
    val converted: Int = 0,
    val skipped: Int = 0,
    val unreadable: Int = 0,
    val failed: Int = 0,
)

enum class ConversionItemOutcome { CONVERTED, SKIPPED, UNREADABLE, FAILED }

enum class PasswordChangeConversion { COMPLETE, INCOMPLETE, UNAVAILABLE }

enum class PreferencesConversionResult { CONVERTED, ALREADY_CONVERTED, NOT_FOUND, CONFLICT, UNAVAILABLE, FAILED }

interface AccountDataConversionScanStore {
    fun read_last_scan(account_id: String): Long
    fun write_last_scan(account_id: String, at_ms: Long)
}

class SharedPreferencesConversionScanStore(context: Context) : AccountDataConversionScanStore {
    private val prefs = context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    override fun read_last_scan(account_id: String): Long =
        runCatching { prefs.getLong(SCAN_KEY_PREFIX + account_id, 0L) }.getOrDefault(0L)

    override fun write_last_scan(account_id: String, at_ms: Long) {
        runCatching { prefs.edit().putLong(SCAN_KEY_PREFIX + account_id, at_ms).apply() }
    }

    companion object {
        private const val PREFS_NAME = "aster_account_data_conversion"
        private const val SCAN_KEY_PREFIX = "scan_"
    }
}

class ConversionKeys(
    val account_id: String,
    val identity_key: String,
    val previous_keys: List<String>,
    val passphrase_bytes: ByteArray,
    val passphrase: CharArray,
    val fallback_passphrases: List<ByteArray> = emptyList(),
) {
    fun zero() {
        passphrase_bytes.fill(0)
        passphrase.fill('\u0000')
        fallback_passphrases.forEach { it.fill(0) }
    }
}

@Singleton
class AccountDataConversion internal constructor(
    private val mail_api: MailApi,
    private val keys_api: KeysApi,
    private val preferences_api: PreferencesApi,
    private val session_key_store: SessionKeyStore,
    private val scan_store: AccountDataConversionScanStore,
    private val locked_counts: LockedSentMailCounts = NoLockedSentMailCounts,
    private val now_ms: () -> Long,
) {
    @Inject
    constructor(
        mail_api: MailApi,
        keys_api: KeysApi,
        preferences_api: PreferencesApi,
        session_key_store: SessionKeyStore,
        locked_sent_mail_store: LockedSentMailStore,
        @ApplicationContext context: Context,
    ) : this(
        mail_api,
        keys_api,
        preferences_api,
        session_key_store,
        SharedPreferencesConversionScanStore(context),
        locked_sent_mail_store,
        System::currentTimeMillis,
    )

    private val lock = Mutex()
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val random = SecureRandom()

    @Volatile
    private var scheduled: Job? = null

    fun schedule(delay_ms: Long = START_DELAY_MS) {
        if (scheduled?.isActive == true) return
        scheduled = scope.launch {
            delay(delay_ms)
            val account_id = session_key_store.get_user_id().orEmpty()
            try {
                run(account_id)
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (_: Throwable) {
            }
        }
    }

    suspend fun run(account_id: String): AccountDataConversionSummary? {
        if (account_id.isEmpty()) return null
        if (!lock.tryLock()) return null
        return try {
            run_locked(account_id)
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (_: Throwable) {
            null
        } finally {
            lock.unlock()
        }
    }

    private suspend fun run_locked(account_id: String): AccountDataConversionSummary? {
        val capabilities = keys_api.get_account_key_capabilities()
        if (!capabilities.data_conversion) return null
        val status = keys_api.get_account_data_conversion() ?: return null
        val keys = capture_keys(account_id) ?: return null
        try {
            if (status.preferences_done_at == null) convert_preferences(keys, capabilities.format_writes)
            if (status.remaining_sent == 0L && status.remaining_attachments == 0L) {
                locked_counts.write(account_id, 0)
                if (status.sent_mail_done_at == null) {
                    keys_api.report_account_data_conversion(ConversionProgressRequest(sent_mail_done = true))
                }
                return AccountDataConversionSummary()
            }
            if (now_ms() - scan_store.read_last_scan(account_id) < RESCAN_INTERVAL_MS) return null
            val (summary, complete) = convert_sent_mail(status, keys)
            if (!complete) return summary
            scan_store.write_last_scan(account_id, now_ms())
            locked_counts.write(account_id, summary.unreadable)
            if (summary.failed == 0 && summary.unreadable == 0) {
                keys_api.report_account_data_conversion(ConversionProgressRequest(sent_mail_done = true))
            }
            return summary
        } finally {
            keys.zero()
        }
    }

    suspend fun recover_sent_mail_with_password(
        account_id: String,
        password: String,
    ): AccountDataConversionSummary? {
        if (account_id.isEmpty() || password.isEmpty()) return null
        return lock.withLock { recover_locked(account_id, password) }
    }

    private suspend fun recover_locked(account_id: String, password: String): AccountDataConversionSummary? {
        val capabilities = keys_api.get_account_key_capabilities()
        if (!capabilities.data_conversion) return null
        val status = keys_api.get_account_data_conversion() ?: return null
        if (!has_remaining(status)) {
            locked_counts.write(account_id, 0)
            return AccountDataConversionSummary()
        }
        val captured = capture_keys(account_id) ?: return null
        val keys = ConversionKeys(
            account_id = captured.account_id,
            identity_key = captured.identity_key,
            previous_keys = captured.previous_keys,
            passphrase_bytes = captured.passphrase_bytes,
            passphrase = captured.passphrase,
            fallback_passphrases = listOf(password.toByteArray(Charsets.UTF_8)),
        )
        try {
            val (summary, complete) = convert_sent_mail(status, keys)
            if (!complete) return summary
            locked_counts.write(account_id, summary.unreadable)
            if (summary.failed == 0 && summary.unreadable == 0) {
                keys_api.report_account_data_conversion(ConversionProgressRequest(sent_mail_done = true))
            }
            return summary
        } finally {
            keys.zero()
        }
    }

    suspend fun convert_before_password_change(
        identity_key: String,
        passphrase: ByteArray,
        budget_ms: Long = PASSWORD_CHANGE_BUDGET_MS,
    ): PasswordChangeConversion {
        if (identity_key.isEmpty() || passphrase.isEmpty()) return PasswordChangeConversion.UNAVAILABLE
        return try {
            val deadline = now_ms() + budget_ms
            withTimeoutOrNull(budget_ms + PASSWORD_CHANGE_GRACE_MS) {
                convert_with_password_change_lock(identity_key, passphrase, deadline)
            } ?: PasswordChangeConversion.INCOMPLETE
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (_: Throwable) {
            PasswordChangeConversion.INCOMPLETE
        }
    }

    suspend fun sent_mail_needs_password_reseal(before: PasswordChangeConversion): Boolean {
        if (before != PasswordChangeConversion.COMPLETE) return true
        return try {
            withTimeoutOrNull(RESEAL_CHECK_TIMEOUT_MS) {
                val status = keys_api.get_account_data_conversion()
                status == null || has_remaining(status)
            } ?: true
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (_: Throwable) {
            true
        }
    }

    private suspend fun convert_with_password_change_lock(
        identity_key: String,
        passphrase: ByteArray,
        deadline: Long,
    ): PasswordChangeConversion {
        var acquired = lock.tryLock()
        if (!acquired) {
            val wait_ms = deadline - now_ms()
            if (wait_ms > 0) {
                withTimeoutOrNull(wait_ms) {
                    lock.lock()
                    acquired = true
                }
            }
        }
        if (!acquired) return PasswordChangeConversion.INCOMPLETE
        return try {
            convert_for_password_change(identity_key, passphrase, deadline)
        } finally {
            lock.unlock()
        }
    }

    private suspend fun convert_for_password_change(
        identity_key: String,
        passphrase: ByteArray,
        deadline: Long,
    ): PasswordChangeConversion {
        val capabilities = keys_api.get_account_key_capabilities()
        if (!capabilities.data_conversion) return PasswordChangeConversion.UNAVAILABLE
        val vault_identity = session_key_store.get_identity_key()
        if (vault_identity.isNullOrEmpty() || vault_identity != identity_key) {
            return PasswordChangeConversion.UNAVAILABLE
        }
        val account_id = session_key_store.get_user_id()
        if (account_id.isNullOrEmpty()) return PasswordChangeConversion.UNAVAILABLE
        val status = keys_api.get_account_data_conversion() ?: return PasswordChangeConversion.UNAVAILABLE
        if (!has_remaining(status)) return PasswordChangeConversion.COMPLETE
        val passphrase_bytes = passphrase.copyOf()
        val keys = ConversionKeys(
            account_id = account_id,
            identity_key = identity_key,
            previous_keys = session_key_store.get_previous_keys().orEmpty().filter { it.isNotEmpty() },
            passphrase_bytes = passphrase_bytes,
            passphrase = passphrase_chars(passphrase_bytes),
        )
        try {
            val (_, complete) = convert_sent_mail(status, keys, deadline)
            if (!complete) return PasswordChangeConversion.INCOMPLETE
            val after = keys_api.get_account_data_conversion()
            if (after == null || has_remaining(after)) return PasswordChangeConversion.INCOMPLETE
            if (after.sent_mail_done_at == null) {
                keys_api.report_account_data_conversion(ConversionProgressRequest(sent_mail_done = true))
            }
            return PasswordChangeConversion.COMPLETE
        } finally {
            keys.zero()
        }
    }

    private fun capture_keys(account_id: String): ConversionKeys? {
        val identity_key = session_key_store.get_identity_key()
        val passphrase_bytes = session_key_store.get_passphrase()
        if (identity_key.isNullOrEmpty() || passphrase_bytes == null || passphrase_bytes.isEmpty() ||
            session_key_store.get_user_id() != account_id
        ) {
            passphrase_bytes?.fill(0)
            return null
        }
        return ConversionKeys(
            account_id = account_id,
            identity_key = identity_key,
            previous_keys = session_key_store.get_previous_keys().orEmpty().filter { it.isNotEmpty() },
            passphrase_bytes = passphrase_bytes,
            passphrase = passphrase_chars(passphrase_bytes),
        )
    }

    private fun keys_still_current(keys: ConversionKeys): Boolean =
        session_key_store.get_identity_key() == keys.identity_key &&
            session_key_store.get_user_id() == keys.account_id

    private class Counts {
        var checked = 0
        var converted = 0
        var skipped = 0
        var unreadable = 0
        var failed = 0

        fun tally(outcome: ConversionItemOutcome) {
            when (outcome) {
                ConversionItemOutcome.CONVERTED -> converted += 1
                ConversionItemOutcome.SKIPPED -> skipped += 1
                ConversionItemOutcome.UNREADABLE -> unreadable += 1
                ConversionItemOutcome.FAILED -> failed += 1
            }
        }

        fun not_converted(): Int = skipped + unreadable + failed

        fun snapshot() = AccountDataConversionSummary(checked, converted, skipped, unreadable, failed)
    }

    private suspend fun convert_sent_mail(
        status: AccountDataConversionStatus,
        keys: ConversionKeys,
        deadline: Long? = null,
    ): Pair<AccountDataConversionSummary, Boolean> {
        val counts = Counts()
        var reported = AccountDataConversionSummary()
        var converted_envelopes = 0L
        var converted_attachments = 0L
        var cursor: String? = null

        suspend fun report() {
            val converted = counts.converted - reported.converted
            val skipped = counts.not_converted() -
                (reported.skipped + reported.unreadable + reported.failed)
            if (converted == 0 && skipped == 0) return
            val recorded = runCatching {
                keys_api.report_account_data_conversion(
                    ConversionProgressRequest(converted = converted.toLong(), skipped = skipped.toLong()),
                )
            }.getOrDefault(false)
            if (recorded) reported = counts.snapshot()
        }

        fun reached_target(): Boolean =
            converted_envelopes >= status.remaining_sent &&
                converted_attachments >= status.remaining_attachments

        while (true) {
            val page = list_sent_page(cursor)
            val items = page.items
            for (item in items) {
                if (!keys_still_current(keys) || (deadline != null && now_ms() >= deadline)) {
                    report()
                    return counts.snapshot() to false
                }
                counts.checked += 1
                if (converted_envelopes < status.remaining_sent) {
                    val outcome = convert_envelope_item(item, keys)
                    if (outcome != ConversionItemOutcome.SKIPPED ||
                        SentMailResealCrypto.is_sentinel_nonce(item.envelope_nonce)
                    ) {
                        counts.tally(outcome)
                    }
                    if (outcome == ConversionItemOutcome.CONVERTED) converted_envelopes += 1
                }
                val may_have_attachments = item.has_attachments == true || (item.attachment_count ?: 0) > 0
                if (may_have_attachments && converted_attachments < status.remaining_attachments) {
                    converted_attachments += convert_item_attachments(item, keys, counts)
                }
                yield()
                if (reached_target()) {
                    report()
                    return counts.snapshot() to true
                }
            }
            report()
            cursor = page.next_cursor
            if (!page.has_more || cursor.isNullOrEmpty() || items.isEmpty()) break
        }
        return counts.snapshot() to true
    }

    private suspend fun list_sent_page(cursor: String?): MailItemsListResponse {
        var last_error: Throwable? = null
        repeat(LISTING_ATTEMPTS) {
            try {
                return mail_api.list_encrypted_items(limit = PAGE_SIZE, cursor = cursor, item_type = "sent")
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (t: Throwable) {
                last_error = t
            }
        }
        throw last_error ?: IllegalStateException("sent mail listing failed")
    }

    private suspend fun convert_item_attachments(
        item: MailItem,
        keys: ConversionKeys,
        counts: Counts,
    ): Long {
        val attachments = try {
            mail_api.list_attachments(item.id).attachments
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (_: Throwable) {
            null
        }
        if (attachments == null) {
            counts.tally(ConversionItemOutcome.FAILED)
            return 0
        }
        var converted = 0L
        for (attachment in attachments) {
            if (attachment.encrypted_meta.isEmpty()) continue
            if (!is_legacy_meta_nonce(attachment.meta_nonce)) continue
            if (!keys_still_current(keys)) return converted
            val outcome = convert_attachment_row(attachment, keys)
            counts.tally(outcome)
            if (outcome == ConversionItemOutcome.CONVERTED) converted += 1
        }
        return converted
    }

    internal suspend fun convert_envelope_item(item: MailItem, keys: ConversionKeys): ConversionItemOutcome {
        val envelope = item.encrypted_envelope
        if (envelope.isNullOrEmpty() || !SentMailResealCrypto.is_sentinel_nonce(item.envelope_nonce)) {
            return ConversionItemOutcome.SKIPPED
        }
        val stored = decode_base64(envelope) ?: return ConversionItemOutcome.UNREADABLE
        val plaintext = open_password_envelope(envelope, keys) ?: return ConversionItemOutcome.UNREADABLE
        if (parse_object(plaintext) == null) return ConversionItemOutcome.UNREADABLE
        return seal_and_write(plaintext, stored, keys) { sealed, expected ->
            keys_api.convert_sent_envelope(item.id, ConvertSentEnvelopeRequest(sealed, expected))
        }
    }

    internal suspend fun convert_attachment_row(
        attachment: AttachmentResponse,
        keys: ConversionKeys,
    ): ConversionItemOutcome {
        if (attachment.encrypted_meta.isEmpty() || !is_legacy_meta_nonce(attachment.meta_nonce)) {
            return ConversionItemOutcome.SKIPPED
        }
        val stored = decode_base64(attachment.encrypted_meta)
        if (stored == null || stored.isEmpty()) return ConversionItemOutcome.UNREADABLE
        val plaintext = open_attachment_meta(attachment.encrypted_meta, stored, keys)
        if (plaintext == null || !is_attachment_meta_text(plaintext)) return ConversionItemOutcome.UNREADABLE
        return seal_and_write(plaintext, stored, keys) { sealed, expected ->
            keys_api.convert_attachment_meta(attachment.id, ConvertAttachmentMetaRequest(sealed, expected))
        }
    }

    private suspend fun seal_and_write(
        plaintext: String,
        stored: ByteArray,
        keys: ConversionKeys,
        write: suspend (String, String) -> ConversionWriteResult,
    ): ConversionItemOutcome {
        val sealed = runCatching { SentCopySeal.seal(plaintext, keys.identity_key, keys.passphrase) }
            .getOrNull()?.first ?: return ConversionItemOutcome.FAILED
        val result = try {
            write(sealed, sha256_hex(stored))
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (_: Throwable) {
            return ConversionItemOutcome.FAILED
        }
        return when (result) {
            ConversionWriteResult.CONVERTED -> ConversionItemOutcome.CONVERTED
            ConversionWriteResult.FAILED -> ConversionItemOutcome.FAILED
            else -> ConversionItemOutcome.SKIPPED
        }
    }

    private fun open_attachment_meta(encrypted_meta: String, stored: ByteArray, keys: ConversionKeys): String? {
        val text = strict_utf8(stored).orEmpty()
        if (text.startsWith(PGP_MESSAGE_HEADER)) {
            return runCatching {
                PgpDecryptor.decrypt_with_own_keys_status(
                    text,
                    listOf(keys.identity_key) + keys.previous_keys,
                    keys.passphrase,
                )?.plaintext
            }.getOrNull()
        }
        if (text.isNotEmpty() && is_attachment_meta_text(text)) return text
        return open_password_envelope(encrypted_meta, keys)
    }

    private fun open_password_envelope(sealed_b64: String, keys: ConversionKeys): String? {
        val data = decode_base64(sealed_b64) ?: return null
        if (data.size <= SALT_LENGTH + NONCE_LENGTH) return null
        val salt = data.copyOfRange(0, SALT_LENGTH)
        val nonce = data.copyOfRange(SALT_LENGTH, SALT_LENGTH + NONCE_LENGTH)
        val ciphertext = data.copyOfRange(SALT_LENGTH + NONCE_LENGTH, data.size)
        val opened = open_with_passphrases(salt, nonce, ciphertext, keys)
            ?: decrypt_with_keks(nonce, ciphertext)
            ?: return null
        return try {
            strict_utf8(opened)
        } finally {
            opened.fill(0)
        }
    }

    private fun open_with_passphrases(
        salt: ByteArray,
        nonce: ByteArray,
        ciphertext: ByteArray,
        keys: ConversionKeys,
    ): ByteArray? {
        for (passphrase in listOf(keys.passphrase_bytes) + keys.fallback_passphrases) {
            if (passphrase.isEmpty()) continue
            val key = PasswordKdf.derive_aes_key(passphrase, salt, PBKDF2_ITERATIONS)
            try {
                val opened = runCatching { AesGcm.decrypt(key, nonce, ciphertext) }.getOrNull()
                if (opened != null) return opened
            } finally {
                key.fill(0)
            }
        }
        return null
    }

    private fun decrypt_with_keks(nonce: ByteArray, ciphertext: ByteArray): ByteArray? {
        for (kek_b64 in session_key_store.get_decrypt_keks()) {
            val raw = runCatching { Base64.getMimeDecoder().decode(kek_b64.trim()) }.getOrNull() ?: continue
            try {
                if (raw.size != KEK_LENGTH) continue
                val opened = runCatching { AesGcm.decrypt(raw, nonce, ciphertext) }.getOrNull()
                if (opened != null) return opened
            } finally {
                raw.fill(0)
            }
        }
        return null
    }

    internal suspend fun convert_preferences(
        keys: ConversionKeys,
        format_writes: Boolean,
    ): PreferencesConversionResult {
        val result = try {
            convert_preferences_to_account_key(keys, format_writes)
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (_: Throwable) {
            PreferencesConversionResult.FAILED
        }
        if (result == PreferencesConversionResult.CONVERTED ||
            result == PreferencesConversionResult.ALREADY_CONVERTED ||
            result == PreferencesConversionResult.NOT_FOUND
        ) {
            runCatching {
                keys_api.report_account_data_conversion(ConversionProgressRequest(preferences_done = true))
            }
        }
        return result
    }

    private suspend fun convert_preferences_to_account_key(
        keys: ConversionKeys,
        format_writes: Boolean,
    ): PreferencesConversionResult {
        val writer = AccountDataWriter(session_key_store, AccountKeyCapabilities({ format_writes }))
        val write_key = writer.write_key(AccountDataWriter.PREFERENCES_CONTEXT)
            ?: return PreferencesConversionResult.UNAVAILABLE
        try {
            val stored = preferences_api.get_versioned_encrypted_preferences()
                ?: return PreferencesConversionResult.FAILED
            val encrypted = stored.encrypted_preferences
            val stored_nonce_b64 = stored.preferences_nonce
            if (encrypted.isNullOrEmpty() || stored_nonce_b64.isNullOrEmpty()) {
                return PreferencesConversionResult.NOT_FOUND
            }
            val version = stored.preferences_version ?: return PreferencesConversionResult.UNAVAILABLE
            val ciphertext = decode_base64(encrypted) ?: return PreferencesConversionResult.FAILED
            val stored_nonce = decode_base64(stored_nonce_b64) ?: return PreferencesConversionResult.FAILED
            val already = runCatching { AesGcm.decrypt(write_key, stored_nonce, ciphertext) }.getOrNull()
            if (already != null) {
                already.fill(0)
                return PreferencesConversionResult.ALREADY_CONVERTED
            }
            val plaintext = open_legacy_preferences(ciphertext, stored_nonce, keys)
                ?: return PreferencesConversionResult.FAILED
            try {
                val text = strict_utf8(plaintext) ?: return PreferencesConversionResult.FAILED
                if (parse_object(text) == null) return PreferencesConversionResult.FAILED
                val nonce = ByteArray(NONCE_LENGTH).also { random.nextBytes(it) }
                val sealed = AesGcm.encrypt(write_key, nonce, plaintext)
                val reopened = runCatching { AesGcm.decrypt(write_key, nonce, sealed) }.getOrNull()
                    ?: return PreferencesConversionResult.FAILED
                val matches = reopened.contentEquals(plaintext)
                reopened.fill(0)
                if (!matches) return PreferencesConversionResult.FAILED
                val saved = preferences_api.save_encrypted_preferences_if_version(
                    SaveVersionedPreferencesRequest(
                        encrypted_preferences = Base64.getEncoder().encodeToString(sealed),
                        preferences_nonce = Base64.getEncoder().encodeToString(nonce),
                        expected_version = version,
                    ),
                )
                return when (saved) {
                    PreferencesSaveResult.SAVED -> PreferencesConversionResult.CONVERTED
                    PreferencesSaveResult.CONFLICT -> PreferencesConversionResult.CONFLICT
                    PreferencesSaveResult.FAILED -> PreferencesConversionResult.FAILED
                }
            } finally {
                plaintext.fill(0)
            }
        } finally {
            write_key.fill(0)
        }
    }

    private fun open_legacy_preferences(ciphertext: ByteArray, nonce: ByteArray, keys: ConversionKeys): ByteArray? {
        val sources = (listOf(keys.identity_key) + keys.previous_keys).distinct()
        for (source in sources) {
            val key = MessageDigest.getInstance("SHA-256")
                .digest((source + AccountDataWriter.PREFERENCES_CONTEXT).toByteArray(Charsets.UTF_8))
            try {
                val opened = runCatching { AesGcm.decrypt(key, nonce, ciphertext) }.getOrNull()
                if (opened != null) return opened
            } finally {
                key.fill(0)
            }
        }
        return decrypt_with_keks(nonce, ciphertext)
    }

    companion object {
        const val PAGE_SIZE = 50
        const val LISTING_ATTEMPTS = 3
        const val RESCAN_INTERVAL_MS = 6 * 60 * 60 * 1000L
        const val START_DELAY_MS = 30_000L
        const val PASSWORD_CHANGE_BUDGET_MS = 30_000L
        const val PASSWORD_CHANGE_GRACE_MS = 15_000L
        const val RESEAL_CHECK_TIMEOUT_MS = 15_000L
        private const val SALT_LENGTH = 16
        private const val NONCE_LENGTH = 12
        private const val META_NONCE_LENGTH = 12
        private const val KEK_LENGTH = 32
        private const val PBKDF2_ITERATIONS = 310000
        private const val PGP_MESSAGE_HEADER = "-----BEGIN PGP MESSAGE-----"

        fun has_remaining(status: AccountDataConversionStatus): Boolean =
            status.remaining_sent > 0 || status.remaining_attachments > 0

        fun is_legacy_meta_nonce(nonce_b64: String?): Boolean {
            if (nonce_b64.isNullOrEmpty()) return false
            val nonce = decode_base64(nonce_b64) ?: return false
            return nonce.size == META_NONCE_LENGTH && nonce.any { it != 0.toByte() }
        }

        fun sha256_hex(bytes: ByteArray): String =
            MessageDigest.getInstance("SHA-256").digest(bytes).joinToString("") { "%02x".format(it) }

        fun strict_utf8(bytes: ByteArray): String? = runCatching {
            Charsets.UTF_8.newDecoder()
                .onMalformedInput(CodingErrorAction.REPORT)
                .onUnmappableCharacter(CodingErrorAction.REPORT)
                .decode(ByteBuffer.wrap(bytes))
                .toString()
        }.getOrNull()

        fun parse_object(text: String): JsonObject? =
            runCatching { Json.parseToJsonElement(text) }.getOrNull() as? JsonObject

        fun is_attachment_meta_text(text: String): Boolean {
            val parsed = parse_object(text) ?: return false
            val filename = parsed["filename"] as? JsonPrimitive
            val session_key = parsed["session_key"] as? JsonPrimitive
            return filename?.isString == true && session_key?.isString == true
        }

        private fun decode_base64(value: String): ByteArray? =
            runCatching { Base64.getDecoder().decode(value.trim()) }.getOrNull()
    }
}
