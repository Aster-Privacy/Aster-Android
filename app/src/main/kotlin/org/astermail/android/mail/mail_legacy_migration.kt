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
import kotlinx.coroutines.CancellationException
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import org.astermail.android.crypto.storage_rotation.ATTACHMENT_META_BATCH_PATH
import org.astermail.android.crypto.storage_rotation.ApiReply
import org.astermail.android.crypto.storage_rotation.ENVELOPE_CHANGED_CODE
import org.astermail.android.crypto.storage_rotation.ResealApi
import org.astermail.android.crypto.storage_rotation.UNLOCKED_FOLDER_PAGE_LIMIT
import org.astermail.android.crypto.storage_rotation.as_object
import org.astermail.android.crypto.storage_rotation.bool
import org.astermail.android.crypto.storage_rotation.json_object
import org.astermail.android.crypto.storage_rotation.long
import org.astermail.android.crypto.storage_rotation.objects
import org.astermail.android.crypto.storage_rotation.str
import org.astermail.android.crypto.storage_rotation.unlocked_folder_page_path
import org.astermail.android.crypto.storage_rotation.url_component

const val MAIL_LEGACY_PAGE_SIZE = 50
const val MAIL_LEGACY_NOT_FOUND_CODE = "NOT_FOUND"
private const val IDENTITY_NONCE_LENGTH = 12
private const val SEAL_FAILURE_LIMIT = 5
private const val METADATA_SEAL_PROBE = "{\"seal_probe\":true}"
private val PAUSE_STATUSES = setOf(401, 403, 429, 503)

enum class LegacyOutcome { SKIPPED, WRITTEN, CHANGED, FAILED, UNAVAILABLE, PAUSED }

enum class MailLegacyMigrationResult { DONE, DEFERRED, INCOMPLETE, FAILED, PAUSED, UNAVAILABLE }

sealed class StoredRecord {
    object Current : StoredRecord()
    object Unreadable : StoredRecord()
    class Legacy(val plaintext: ByteArray) : StoredRecord()
}

class SealedField(val encrypted: String, val nonce: String)

interface MailLegacyCrypto {
    fun classify_envelope(encrypted: String, nonce: String): StoredRecord
    fun reseal_envelope(plaintext: ByteArray): SealedField?
    fun classify_metadata(encrypted: String, nonce: String): StoredRecord
    fun reseal_metadata(plaintext: ByteArray): SealedField?
    fun classify_attachment_meta(encrypted: String, nonce: String): StoredRecord
    fun reseal_attachment_meta(plaintext: ByteArray): SealedField?
    fun stored_sha256_hex(encrypted: String): String?
    fun is_login_salted_envelope(encrypted: String, nonce: String): Boolean = false
    fun classify_server_seal(encrypted: String): StoredRecord = StoredRecord.Unreadable
}

interface MailLegacyFlagStore {
    fun get(key: String): String?
    fun put(key: String, value: String?)
}

class MemoryMailLegacyFlagStore : MailLegacyFlagStore {
    private val values = java.util.concurrent.ConcurrentHashMap<String, String>()

    override fun get(key: String): String? = values[key]

    override fun put(key: String, value: String?) {
        if (value == null) values.remove(key) else values[key] = value
    }
}

class MailLegacyMigrationDeps(
    val user_id: String,
    val api: ResealApi,
    val crypto: MailLegacyCrypto,
    val flags: MailLegacyFlagStore,
    val is_retired: () -> Boolean,
    val protected_folder_tokens: () -> Set<String>?,
    val write_marker: suspend () -> Boolean,
    val is_draft_busy: suspend (String) -> Boolean = { false },
    val on_progress: (processed: Int, rewritten: Int) -> Unit = { _, _ -> },
    val stored_keys_ready: suspend () -> Boolean = {
        org.astermail.android.crypto.stored_key_readiness.is_ready(user_id)
    },
)

fun classify_legacy_reply(reply: ApiReply): LegacyOutcome {
    if (!reply.error) return LegacyOutcome.WRITTEN
    val status = reply.status ?: return LegacyOutcome.PAUSED
    if (status in PAUSE_STATUSES) return LegacyOutcome.PAUSED
    if (status == 404) {
        return if (reply.code == MAIL_LEGACY_NOT_FOUND_CODE) LegacyOutcome.SKIPPED else LegacyOutcome.UNAVAILABLE
    }
    if (status == 426) return LegacyOutcome.UNAVAILABLE
    if (status == 409 && reply.code == ENVELOPE_CHANGED_CODE) return LegacyOutcome.CHANGED
    return LegacyOutcome.FAILED
}

fun more_severe(a: LegacyOutcome, b: LegacyOutcome): LegacyOutcome = if (a.ordinal >= b.ordinal) a else b

private fun decode_b64(value: String?): ByteArray? {
    if (value.isNullOrBlank()) return null
    return runCatching { Base64.getMimeDecoder().decode(value.trim()) }.getOrNull()
}

fun is_keyed_envelope_nonce(nonce_b64: String?): Boolean {
    val nonce = decode_b64(nonce_b64) ?: return false
    if (nonce.size == 1) return nonce[0] == 1.toByte()
    return nonce.size == IDENTITY_NONCE_LENGTH
}

fun is_rewrap_nonce(nonce_b64: String?): Boolean {
    val nonce = decode_b64(nonce_b64) ?: return false
    return nonce.size == IDENTITY_NONCE_LENGTH || (nonce.size == 1 && nonce[0] == 2.toByte())
}

fun is_identity_sealed_meta_nonce(nonce_b64: String?): Boolean {
    val nonce = decode_b64(nonce_b64) ?: return false
    if (nonce.size != IDENTITY_NONCE_LENGTH) return false
    if (nonce[0] == 2.toByte() && nonce.drop(1).all { it == 0.toByte() }) return false
    return nonce.any { it != 0.toByte() }
}

fun may_hold_identity_sealed_attachment_meta(item: JsonObject): Boolean {
    val type = item.str("item_type")
    if (type != "sent" && type != "draft") return false
    return item.bool("has_attachments") == true || (item.long("attachment_count") ?: 0L) > 0L
}

fun mail_item_needs_care(item: JsonObject): Boolean =
    (!item.str("encrypted_envelope").isNullOrBlank() && is_keyed_envelope_nonce(item.str("envelope_nonce"))) ||
        (!item.str("encrypted_metadata").isNullOrBlank() && !item.str("metadata_nonce").isNullOrBlank()) ||
        may_hold_identity_sealed_attachment_meta(item)

fun mail_legacy_list_path(cursor: String?): String =
    "/mail/v1/messages/encrypted?limit=$MAIL_LEGACY_PAGE_SIZE&include_envelope=true&include_reactions=true" +
        (cursor?.let { "&cursor=${url_component(it)}" } ?: "")

class MailLegacyMigration(private val deps: MailLegacyMigrationDeps) {

    private val main_done_key = "main_done_${deps.user_id}"
    private val cursor_key = "cursor_${deps.user_id}"
    private val processed_key = "processed_${deps.user_id}"
    private val rewritten_key = "rewritten_${deps.user_id}"

    private val skipped_key = "skipped_${deps.user_id}"
    private val recovery_key = "recovery_${deps.user_id}"
    private val unreadable_key = "unreadable_${deps.user_id}"
    private val seal_failures_key = "seal_failures_${deps.user_id}"

    private fun folder_done_key(token: String) = "folder_done_${deps.user_id}_$token"

    private fun id_list(key: String): Set<String> =
        deps.flags.get(key)?.split(',')?.filter { it.isNotBlank() }?.toCollection(LinkedHashSet()) ?: emptySet()

    private fun add_id(key: String, id: String) {
        val ids = id_list(key)
        if (id !in ids) deps.flags.put(key, (ids + id).joinToString(","))
    }

    private fun remove_id(key: String, id: String) {
        val ids = id_list(key)
        if (id in ids) deps.flags.put(key, (ids - id).joinToString(",").ifEmpty { null })
    }

    fun skipped_item_ids(): Set<String> = id_list(skipped_key)

    fun recovery_item_ids(): Set<String> = id_list(recovery_key)

    fun unreadable_item_ids(): Set<String> = id_list(unreadable_key)

    private fun skip_item(id: String): LegacyOutcome {
        add_id(skipped_key, id)
        return LegacyOutcome.FAILED
    }

    private fun seal_failures(): Map<String, Int> {
        val failures = LinkedHashMap<String, Int>()
        deps.flags.get(seal_failures_key)?.split(',')?.forEach { pair ->
            val split = pair.lastIndexOf('=')
            if (split <= 0) return@forEach
            val count = pair.substring(split + 1).toIntOrNull() ?: return@forEach
            failures[pair.substring(0, split)] = count
        }
        return failures
    }

    private fun store_seal_failures(failures: Map<String, Int>) {
        deps.flags.put(seal_failures_key, failures.entries.joinToString(",") { "${it.key}=${it.value}" }.ifEmpty { null })
    }

    private fun forget_seal_failures(id: String) {
        val failures = seal_failures()
        if (id in failures) store_seal_failures(failures - id)
    }

    private fun unsealable_metadata(id: String): LegacyOutcome {
        val probe = METADATA_SEAL_PROBE.toByteArray(Charsets.UTF_8)
        val sealed = try {
            deps.crypto.reseal_metadata(probe)
        } finally {
            probe.fill(0)
        }
        if (sealed == null) return LegacyOutcome.PAUSED
        val failures = seal_failures()
        val count = ((failures[id] ?: 0) + 1).coerceAtMost(SEAL_FAILURE_LIMIT)
        store_seal_failures(failures + (id to count))
        if (count < SEAL_FAILURE_LIMIT) return skip_item(id)
        add_id(unreadable_key, id)
        return LegacyOutcome.SKIPPED
    }

    fun is_folder_pass_done(token: String): Boolean = deps.flags.get(folder_done_key(token)) == "1"

    fun is_main_pass_done(): Boolean = deps.flags.get(main_done_key) == "1"

    private fun reset_state() {
        deps.flags.put(cursor_key, null)
        deps.flags.put(processed_key, null)
        deps.flags.put(rewritten_key, null)
    }

    suspend fun run(): MailLegacyMigrationResult = guarded {
        if (deps.is_retired()) return@guarded MailLegacyMigrationResult.DONE
        if (is_main_pass_done()) return@guarded complete_retirement()
        var cursor = deps.flags.get(cursor_key)
        var processed = deps.flags.get(processed_key)?.toIntOrNull()?.coerceAtLeast(0) ?: 0
        var rewritten = deps.flags.get(rewritten_key)?.toIntOrNull()?.coerceAtLeast(0) ?: 0
        var incomplete = false
        var failed = false
        val pass = UnresolvedItems()
        while (true) {
            val reply = deps.api.get(mail_legacy_list_path(cursor))
            val data = reply.data.as_object()
            if (reply.error || data == null) return@guarded page_failure(reply)
            for (item in data.objects("items")) {
                processed += 1
                if (!mail_item_needs_care(item)) {
                    item.str("id")?.let(::forget_unopened)
                    continue
                }
                val id = item.str("id") ?: continue
                if (item.str("item_type") == "draft" && deps.is_draft_busy(id)) {
                    incomplete = true
                    continue
                }
                val outcome = migrate_item(id, item, pass)
                if (outcome != LegacyOutcome.FAILED) remove_id(skipped_key, id)
                when (outcome) {
                    LegacyOutcome.PAUSED -> return@guarded MailLegacyMigrationResult.PAUSED
                    LegacyOutcome.UNAVAILABLE -> return@guarded MailLegacyMigrationResult.UNAVAILABLE
                    LegacyOutcome.WRITTEN -> rewritten += 1
                    LegacyOutcome.CHANGED -> incomplete = true
                    LegacyOutcome.FAILED -> failed = true
                    else -> Unit
                }
            }
            deps.on_progress(processed, rewritten)
            val next = data.str("next_cursor")
            if (data.bool("has_more") != true || next.isNullOrBlank()) break
            cursor = next
            if (!incomplete && !failed) {
                deps.flags.put(cursor_key, cursor)
                deps.flags.put(processed_key, processed.toString())
                deps.flags.put(rewritten_key, rewritten.toString())
            }
        }
        reset_state()
        if (failed) return@guarded MailLegacyMigrationResult.FAILED
        if (incomplete) return@guarded MailLegacyMigrationResult.INCOMPLETE
        if (!settle_unresolved(pass)) return@guarded MailLegacyMigrationResult.DEFERRED
        deps.flags.put(main_done_key, "1")
        complete_retirement()
    }

    suspend fun run_folder(token: String, is_unlocked: () -> Boolean): MailLegacyMigrationResult = guarded {
        if (deps.is_retired()) return@guarded MailLegacyMigrationResult.DONE
        if (is_folder_pass_done(token)) {
            return@guarded if (is_main_pass_done()) complete_retirement() else MailLegacyMigrationResult.DEFERRED
        }
        var incomplete = false
        var failed = false
        val pass = UnresolvedItems()
        for (trashed in listOf(false, true)) {
            var offset = 0
            while (true) {
                if (!is_unlocked()) return@guarded MailLegacyMigrationResult.PAUSED
                val reply = deps.api.get(unlocked_folder_page_path(token, offset, trashed))
                val data = reply.data.as_object()
                if (reply.error || data == null) return@guarded page_failure(reply)
                val items = data.objects("items")
                for (item in items) {
                    if (!mail_item_needs_care(item)) {
                        item.str("id")?.let(::forget_unopened)
                        continue
                    }
                    val id = item.str("id") ?: continue
                    if (item.str("item_type") == "draft" && deps.is_draft_busy(id)) {
                        incomplete = true
                        continue
                    }
                    val outcome = migrate_item(id, item, pass)
                    if (outcome != LegacyOutcome.FAILED) remove_id(skipped_key, id)
                    when (outcome) {
                        LegacyOutcome.PAUSED -> return@guarded MailLegacyMigrationResult.PAUSED
                        LegacyOutcome.UNAVAILABLE -> return@guarded MailLegacyMigrationResult.UNAVAILABLE
                        LegacyOutcome.CHANGED -> incomplete = true
                        LegacyOutcome.FAILED -> failed = true
                        else -> Unit
                    }
                }
                if (items.isEmpty() || (items.size < UNLOCKED_FOLDER_PAGE_LIMIT && data.bool("has_more") != true)) break
                offset += items.size
            }
        }
        if (failed) return@guarded MailLegacyMigrationResult.FAILED
        if (incomplete) return@guarded MailLegacyMigrationResult.INCOMPLETE
        if (!settle_unresolved(pass)) return@guarded MailLegacyMigrationResult.DEFERRED
        deps.flags.put(folder_done_key(token), "1")
        if (is_main_pass_done()) complete_retirement() else MailLegacyMigrationResult.DEFERRED
    }

    private suspend fun guarded(block: suspend () -> MailLegacyMigrationResult): MailLegacyMigrationResult = try {
        block()
    } catch (cancelled: CancellationException) {
        throw cancelled
    } catch (_: Throwable) {
        MailLegacyMigrationResult.PAUSED
    }

    private fun page_failure(reply: ApiReply): MailLegacyMigrationResult =
        if (classify_legacy_reply(reply) == LegacyOutcome.UNAVAILABLE) {
            MailLegacyMigrationResult.UNAVAILABLE
        } else {
            MailLegacyMigrationResult.PAUSED
        }

    private suspend fun keys_ready(): Boolean = try {
        deps.stored_keys_ready()
    } catch (cancelled: CancellationException) {
        throw cancelled
    } catch (_: Throwable) {
        false
    }

    private class UnresolvedItems {
        val unreadable = LinkedHashSet<String>()
        var failed_without_keys = false
    }

    private fun forget_unopened(id: String) {
        remove_id(unreadable_key, id)
        remove_id(recovery_key, id)
    }

    private suspend fun settle_unresolved(pass: UnresolvedItems): Boolean {
        if (pass.unreadable.isNotEmpty() && !keys_ready()) return false
        for (id in pass.unreadable) {
            add_id(unreadable_key, id)
            remove_id(recovery_key, id)
        }
        return !pass.failed_without_keys
    }

    private suspend fun complete_retirement(): MailLegacyMigrationResult {
        val tokens = deps.protected_folder_tokens() ?: return MailLegacyMigrationResult.PAUSED
        if (tokens.any { !is_folder_pass_done(it) }) return MailLegacyMigrationResult.DEFERRED
        if (!keys_ready()) return MailLegacyMigrationResult.DEFERRED
        val written = try {
            deps.write_marker()
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (_: Throwable) {
            false
        }
        return if (written) MailLegacyMigrationResult.DONE else MailLegacyMigrationResult.PAUSED
    }

    private suspend fun migrate_item(id: String, item: JsonObject, pass: UnresolvedItems): LegacyOutcome {
        val envelope = migrate_envelope(id, item, pass)
        if (envelope == LegacyOutcome.PAUSED || envelope == LegacyOutcome.UNAVAILABLE) return envelope
        val metadata = migrate_metadata(id, item)
        if (metadata == LegacyOutcome.PAUSED || metadata == LegacyOutcome.UNAVAILABLE) return metadata
        val attachments = migrate_attachments(id, item)
        if (attachments == LegacyOutcome.PAUSED || attachments == LegacyOutcome.UNAVAILABLE) return attachments
        return more_severe(more_severe(envelope, metadata), attachments)
    }

    private suspend fun send(request: suspend () -> ApiReply): LegacyOutcome = try {
        classify_legacy_reply(request())
    } catch (cancelled: CancellationException) {
        throw cancelled
    } catch (_: Throwable) {
        LegacyOutcome.PAUSED
    }

    private suspend fun migrate_envelope(id: String, item: JsonObject, pass: UnresolvedItems): LegacyOutcome {
        val encrypted = item.str("encrypted_envelope")
        val nonce = item.str("envelope_nonce")
        if (encrypted.isNullOrBlank() || nonce.isNullOrBlank() || !is_keyed_envelope_nonce(nonce)) {
            forget_unopened(id)
            return LegacyOutcome.SKIPPED
        }
        val classified = deps.crypto.classify_envelope(encrypted, nonce)
        if (classified is StoredRecord.Unreadable && deps.crypto.is_login_salted_envelope(encrypted, nonce)) {
            add_id(recovery_key, id)
            if (keys_ready()) pass.unreadable.add(id) else pass.failed_without_keys = true
            return LegacyOutcome.SKIPPED
        }
        if (classified !is StoredRecord.Unreadable) forget_unopened(id)
        val record = classified as? StoredRecord.Legacy ?: return LegacyOutcome.SKIPPED
        try {
            val expected = deps.crypto.stored_sha256_hex(encrypted) ?: return skip_item(id)
            val sealed = deps.crypto.reseal_envelope(record.plaintext) ?: return skip_item(id)
            if (!is_rewrap_nonce(sealed.nonce)) return skip_item(id)
            val outcome = send {
                deps.api.put(
                    "/mail/v1/messages/${url_component(id)}/envelope-rewrap",
                    json_object(
                        "encrypted_envelope" to sealed.encrypted,
                        "envelope_nonce" to sealed.nonce,
                        "expected_envelope_sha256" to expected,
                    ),
                )
            }
            if (outcome == LegacyOutcome.WRITTEN) {
                remove_id(recovery_key, id)
                remove_id(unreadable_key, id)
            }
            return outcome
        } finally {
            record.plaintext.fill(0)
        }
    }

    private suspend fun migrate_metadata(id: String, item: JsonObject): LegacyOutcome {
        val encrypted = item.str("encrypted_metadata")
        val nonce = item.str("metadata_nonce")
        if (encrypted.isNullOrBlank() || nonce.isNullOrBlank()) return LegacyOutcome.SKIPPED
        val record = deps.crypto.classify_metadata(encrypted, nonce) as? StoredRecord.Legacy
            ?: return LegacyOutcome.SKIPPED
        try {
            val sealed = deps.crypto.reseal_metadata(record.plaintext) ?: return unsealable_metadata(id)
            val outcome = send {
                deps.api.put(
                    "/mail/v1/messages/${url_component(id)}",
                    json_object("encrypted_metadata" to sealed.encrypted, "metadata_nonce" to sealed.nonce),
                )
            }
            if (outcome == LegacyOutcome.WRITTEN) forget_seal_failures(id)
            return outcome
        } finally {
            record.plaintext.fill(0)
        }
    }

    private suspend fun migrate_attachments(id: String, item: JsonObject): LegacyOutcome {
        if (!may_hold_identity_sealed_attachment_meta(item)) return LegacyOutcome.SKIPPED
        val reply = try {
            deps.api.post(ATTACHMENT_META_BATCH_PATH, json_object("mail_ids" to JsonArray(listOf(JsonPrimitive(id)))))
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (_: Throwable) {
            return LegacyOutcome.PAUSED
        }
        if (reply.error) return classify_legacy_reply(reply)
        var result = LegacyOutcome.SKIPPED
        for (meta in reply.data.as_object()?.get("items").as_object().objects(id)) {
            val outcome = migrate_attachment_meta(id, meta)
            if (outcome == LegacyOutcome.PAUSED || outcome == LegacyOutcome.UNAVAILABLE) return outcome
            result = more_severe(result, outcome)
        }
        return result
    }

    private suspend fun migrate_attachment_meta(id: String, meta: JsonObject): LegacyOutcome {
        val meta_id = meta.str("id") ?: return LegacyOutcome.SKIPPED
        val encrypted = meta.str("encrypted_meta")
        val nonce = meta.str("meta_nonce")
        if (encrypted.isNullOrBlank() || !is_identity_sealed_meta_nonce(nonce)) return LegacyOutcome.SKIPPED
        val record = deps.crypto.classify_attachment_meta(encrypted, nonce!!) as? StoredRecord.Legacy
            ?: return LegacyOutcome.SKIPPED
        try {
            val sealed = deps.crypto.reseal_attachment_meta(record.plaintext) ?: return skip_item(id)
            return send {
                deps.api.put(
                    "/mail/v1/attachments/${url_component(meta_id)}/meta",
                    json_object("encrypted_meta" to sealed.encrypted, "meta_nonce" to sealed.nonce),
                )
            }
        } finally {
            record.plaintext.fill(0)
        }
    }
}
