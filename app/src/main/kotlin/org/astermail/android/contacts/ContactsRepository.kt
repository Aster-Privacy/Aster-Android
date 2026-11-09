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

package org.astermail.android.contacts

import java.security.MessageDigest
import javax.crypto.Mac
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.astermail.android.crypto.AesGcm
import org.astermail.android.crypto.StorageWriteKey
import javax.crypto.spec.SecretKeySpec
import javax.inject.Inject
import javax.inject.Singleton
import org.astermail.android.api.contacts.BulkDeleteContactsRequest
import org.astermail.android.api.contacts.CONTACT_DATA_VERSION
import org.astermail.android.api.contacts.ContactGroupEncrypted
import org.astermail.android.api.contacts.ContactItem
import org.astermail.android.api.contacts.ContactsApi
import org.astermail.android.api.contacts.ListContactsResponse
import org.astermail.android.api.contacts.CreateContactGroupRequest
import org.astermail.android.api.contacts.UpdateContactGroupRequest
import org.astermail.android.api.contacts.CreateContactGroupResponse
import org.astermail.android.api.contacts.CreateContactRequest
import org.astermail.android.api.contacts.CreateContactResponse
import org.astermail.android.api.contacts.DeleteContactResponse
import org.astermail.android.api.ApiError
import org.astermail.android.api.contacts.ImportContactItem
import org.astermail.android.api.contacts.ImportContactsRequest
import org.astermail.android.api.contacts.ImportContactsResponse
import org.astermail.android.api.contacts.SuccessResponse
import org.astermail.android.api.contacts.UpdateContactRequest
import org.astermail.android.api.contacts.UpdateContactResponse
import org.astermail.android.contacts.sync.REVISION_CONFLICT_CODE
import org.astermail.android.crypto.StorageKeyUnavailableException
import org.astermail.android.mail.PendingActionKind
import org.astermail.android.mail.PendingActionPayload
import org.astermail.android.mail.PendingMailAction
import org.astermail.android.mail.PendingMailActionQueue
import org.astermail.android.mail.is_contact
import org.astermail.android.mail.is_transient_failure
import org.astermail.android.security.SenderTrustContact
import org.astermail.android.storage.contacts.ContactCacheDao
import org.astermail.android.storage.contacts.ContactCacheEntity
import org.astermail.android.storage.contacts.ContactGroupCacheDao
import org.astermail.android.storage.contacts.ContactGroupCacheEntity
import org.astermail.android.security.SenderTrustStore
import org.astermail.android.storage.SessionKeyStore
import org.astermail.android.ui.contacts.Contact
import org.astermail.android.ui.contacts.ContactEntry
import org.astermail.android.ui.contacts.ContactPostal

data class ContactGroup(
    val id: String,
    val name: String,
    val color: String,
    val icon: String? = null,
    val contact_count: Int,
    val created_at: String? = null,
)


private const val DEFAULT_GROUP_COLOR = "#4f46e5"

class ContactUndecryptableException : IllegalStateException("failed to decrypt contact")

@Singleton
class ContactsRepository @Inject constructor(
    private val contacts_api: ContactsApi,
    private val session_key_store: SessionKeyStore,
    private val cache_dao_provider: dagger.Lazy<ContactCacheDao>? = null,
    private val group_cache_dao_provider: dagger.Lazy<ContactGroupCacheDao>? = null,
    private val pending_queue_provider: dagger.Lazy<PendingMailActionQueue>? = null,
) : ContactPhotoSource {
    internal var default_dispatcher: CoroutineDispatcher = Dispatchers.Default

    internal var network_probe: (() -> Boolean)? = null

    private val local_ops_mutex = Mutex()

    private val resolved_ids = java.util.concurrent.ConcurrentHashMap<String, String>()

    private val cache_writes = java.util.concurrent.atomic.AtomicLong(0)

    private val cache_dao: ContactCacheDao?
        get() = cache_dao_provider?.get()

    private val group_cache_dao: ContactGroupCacheDao?
        get() = group_cache_dao_provider?.get()

    private val pending_queue: PendingMailActionQueue?
        get() = pending_queue_provider?.get()

    private fun current_account(): String? = session_key_store.get_user_id()?.takeIf { it.isNotBlank() }

    private fun is_online(): Boolean = network_probe?.invoke() ?: (pending_queue?.is_network_available() ?: true)

    fun resolve_contact_id(contact_id: String): String = resolved_ids[contact_id] ?: contact_id

    val pending_contact_ids: Flow<Set<String>>
        get() {
            val queue = pending_queue ?: return flowOf(emptySet())
            return queue.actions.map { actions ->
                val account = current_account() ?: return@map emptySet<String>()
                actions.asSequence()
                    .filter { it.account_id == account && it.kind.is_contact }
                    .mapNotNull { it.payload.contact?.contact_id }
                    .toSet()
            }.distinctUntilChanged()
        }

    suspend fun <T> with_op_lock(block: suspend () -> T): T = local_ops_mutex.withLock { block() }

    suspend fun fetch_contacts(group_id: String? = null): Result<List<Contact>> {
        val trust_generation = SenderTrustStore.current_generation()
        return runCatching {
            load_contacts(group_id)
        }.onSuccess { contacts ->
            if (group_id == null) {
                val photo_entries = withContext(default_dispatcher) {
                    contacts.mapNotNull { contact_photo_entry(it) }
                }
                ContactPhotoDirectory.offer(photo_entries)
                publish_trust_contacts(contacts, trust_generation)
            }
        }
    }

    private fun publish_trust_contacts(contacts: List<Contact>, trust_generation: Long) {
        SenderTrustStore.set_contacts(
            contacts
                .filter { it.deleted_at.isBlank() }
                .map { contact -> SenderTrustContact(contact.name, contact.email_entries().map { it.value }) },
            trust_generation,
        )
    }

    override suspend fun list_photo_entries(): List<ContactPhotoEntry> {
        val passphrase = session_key_store.get_passphrase() ?: throw IllegalStateException("no session")
        passphrase.fill(0)
        val trust_generation = SenderTrustStore.current_generation()
        val contacts = load_contacts(null)
        publish_trust_contacts(contacts, trust_generation)
        return contacts.mapNotNull { contact_photo_entry(it) }
    }

    override suspend fun fetch_contact_photo(contact_id: String): ContactPhotoFetch = try {
        val item = contacts_api.get_contact(contact_id)
        val photo = withContext(default_dispatcher) {
            decrypt_contact(item)?.let { contact_photo_entry(it)?.photo }
        }
        if (photo != null) ContactPhotoFetch.Found(photo) else ContactPhotoFetch.Missing
    } catch (cancelled: kotlin.coroutines.cancellation.CancellationException) {
        throw cancelled
    } catch (_: ApiError.NotFoundError) {
        ContactPhotoFetch.Missing
    } catch (t: Throwable) {
        ContactPhotoFetch.Failed(t)
    }

    private suspend fun load_contacts(group_id: String?): List<Contact> {
        val account = current_account()
        val dao = cache_dao
        if (group_id != null || account == null || dao == null) return fetch_all_contacts(group_id)
        if (!is_online()) return cached_contacts(account, dao)
        val writes_before = cache_writes.get()
        val (items, complete) = try {
            fetch_all_items(null)
        } catch (cancelled: kotlin.coroutines.cancellation.CancellationException) {
            throw cancelled
        } catch (t: Throwable) {
            if (is_transient_failure(t)) return cached_contacts(account, dao)
            throw t
        }
        if (current_account() == account) {
            val rows = items.mapNotNull { it.to_cache_row(account) }
            if (complete && cache_writes.get() == writes_before) dao.replace_for_account(account, rows) else dao.upsert_all(rows)
            if (!complete) return cached_contacts(account, dao)
        }
        return decrypt_contacts(overlay_pending(account, items, include_missing = true))
    }

    private suspend fun cached_contacts(account: String, dao: ContactCacheDao): List<Contact> {
        val rows = dao.get_for_account(account).map { it.to_item() }
        return decrypt_contacts(overlay_pending(account, rows, include_missing = true))
    }

    private suspend fun pending_contact_ops(account: String): List<PendingMailAction> {
        val queue = pending_queue ?: return emptyList()
        queue.await_loaded()
        return queue.for_account(account).filter { it.kind.is_contact && it.payload.contact != null }
    }

    private suspend fun pending_op_for(account: String, contact_id: String): PendingMailAction? =
        pending_contact_ops(account).firstOrNull { it.payload.contact?.contact_id == contact_id }

    private suspend fun overlay_pending(
        account: String,
        items: List<ContactItem>,
        include_missing: Boolean,
    ): List<ContactItem> {
        val ops = pending_contact_ops(account)
        if (ops.isEmpty()) return items
        val merged = LinkedHashMap<String, ContactItem>()
        items.forEach { merged[it.id] = it }
        for (op in ops) {
            val payload = op.payload.contact ?: continue
            val id = payload.contact_id
            when (op.kind) {
                PendingActionKind.contact_delete -> merged.remove(id)
                PendingActionKind.contact_create,
                PendingActionKind.contact_update -> {
                    val local = payload.local ?: continue
                    val existing = merged[id]
                    if (existing == null && !include_missing) continue
                    merged[id] = local.to_item(
                        id = id,
                        created_at = existing?.created_at ?: payload.created_at,
                        revision = existing?.revision,
                        contact_token = existing?.contact_token,
                    )
                }
                else -> Unit
            }
        }
        return merged.values.toList()
    }

    private suspend fun fetch_all_contacts(group_id: String?): List<Contact> =
        decrypt_contacts(fetch_all_items(group_id).first)

    private suspend fun fetch_all_items(group_id: String?): Pair<List<ContactItem>, Boolean> {
        val all = mutableListOf<ContactItem>()
        val seen_cursors = mutableSetOf<String>()
        var cursor: String? = null
        while (true) {
            val page = fetch_page_with_retry(cursor, group_id) ?: return all to false
            all.addAll(page.items)
            val next = page.next_cursor
            if (!page.has_more || next.isNullOrBlank()) return all to true
            if (!seen_cursors.add(next)) return all to false
            cursor = next
        }
    }

    private suspend fun fetch_page_with_retry(cursor: String?, group_id: String?): ListContactsResponse? {
        var attempt = 0
        while (true) {
            try {
                return contacts_api.list_contacts(limit = 100, cursor = cursor, group_id = group_id)
            } catch (t: kotlin.coroutines.cancellation.CancellationException) {
                throw t
            } catch (t: Throwable) {
                if (cursor == null) throw t
                if (attempt >= PAGE_RETRY_DELAYS_MS.size) return null
                delay(PAGE_RETRY_DELAYS_MS[attempt])
                attempt++
            }
        }
    }

    private suspend fun decrypt_contacts(items: List<ContactItem>): List<Contact> =
        withContext(default_dispatcher) { items.mapNotNull { decrypt_contact(it) } }

    suspend fun fetch_contact(contact_id: String): Result<Contact> = runCatching {
        val id = resolve_contact_id(contact_id)
        val account = current_account()
        val dao = cache_dao
        if (account != null && dao != null &&
            (is_local_contact_id(id) || !is_online() || pending_op_for(account, id) != null)
        ) {
            return@runCatching cached_contact(account, dao, id) ?: throw ApiError.NotFoundError
        }
        val item = try {
            contacts_api.get_contact(id)
        } catch (cancelled: kotlin.coroutines.cancellation.CancellationException) {
            throw cancelled
        } catch (t: Throwable) {
            if (account != null && dao != null) {
                if (t is ApiError.NotFoundError) forget_cached(account, id)
                if (is_transient_failure(t)) return@runCatching cached_contact(account, dao, id) ?: throw t
            }
            throw t
        }
        if (account != null && dao != null && current_account() == account) {
            item.to_cache_row(account)?.let { dao.upsert(it) }
        }
        withContext(default_dispatcher) { decrypt_contact(item) }
            ?: throw IllegalStateException("failed to decrypt contact")
    }

    private suspend fun cached_contact(account: String, dao: ContactCacheDao, id: String): Contact? {
        val rows = listOfNotNull(dao.get(account, id)?.to_item())
        val item = overlay_pending(account, rows, include_missing = true).firstOrNull { it.id == id }
            ?: pending_contact_ops(account)
                .firstOrNull { it.payload.contact?.contact_id == id && it.kind != PendingActionKind.contact_delete }
                ?.let { op -> op.payload.contact?.local?.to_item(id, op.payload.contact?.created_at, null, null) }
            ?: return null
        return withContext(default_dispatcher) { decrypt_contact(item) }
    }

    suspend fun get_count(): Result<Int> = runCatching {
        val account = current_account()
        val dao = cache_dao
        if (account == null || dao == null) return@runCatching contacts_api.get_contacts_count().count
        if (!is_online()) return@runCatching cached_count(account, dao)
        try {
            contacts_api.get_contacts_count().count
        } catch (cancelled: kotlin.coroutines.cancellation.CancellationException) {
            throw cancelled
        } catch (t: Throwable) {
            if (is_transient_failure(t)) cached_count(account, dao) else throw t
        }
    }

    private suspend fun cached_count(account: String, dao: ContactCacheDao): Int =
        overlay_pending(account, dao.get_for_account(account).map { it.to_item() }, include_missing = true).size

    suspend fun create_contact(contact: Contact): Result<CreateContactResponse> = runCatching {
        val account = current_account()
        val dao = cache_dao
        val queue = pending_queue
        if (account == null || dao == null || queue == null) return@runCatching create_contact_now(contact, null, null)
        queue.await_loaded()
        if (!is_online()) return@runCatching queue_create(account, contact)
        try {
            create_contact_now(contact, account, dao)
        } catch (cancelled: kotlin.coroutines.cancellation.CancellationException) {
            throw cancelled
        } catch (t: Throwable) {
            if (is_transient_failure(t)) queue_create(account, contact) else throw t
        }
    }.also { on_local_change() }

    private suspend fun create_contact_now(contact: Contact, account: String?, dao: ContactCacheDao?): CreateContactResponse {
        val key = write_contacts_key()
        try {
            val request = build_create_request(contact, key)
            val response = contacts_api.create_contact(request)
            val id = response.id
            if (account != null && dao != null && id != null && current_account() == account) {
                cache_writes.incrementAndGet()
                dao.upsert(request.to_cache_row(account, id, response.created_at, response.revision, response.change_seq))
            }
            return response
        } finally {
            key.fill(0)
        }
    }

    private suspend fun queue_create(account: String, contact: Contact): CreateContactResponse = local_ops_mutex.withLock {
        val queue = pending_queue ?: throw IllegalStateException("no queue")
        val key = write_contacts_key()
        try {
            val token = generate_contact_token(contact, key)
            if (has_duplicate_token(account, token, key)) throw ApiError.Conflict("Contact already exists", null)
            val cipher = encrypt_contact_cipher(contact, key)
            val id = LOCAL_CONTACT_PREFIX + java.util.UUID.randomUUID().toString()
            val created_at = java.time.Instant.now().toString()
            queue.enqueue(
                account,
                PendingActionKind.contact_create,
                PendingActionPayload(contact = PendingContactPayload(contact_id = id, local = cipher, created_at = created_at)),
            )
            CreateContactResponse(id = id, success = true, created_at = created_at)
        } finally {
            key.fill(0)
        }
    }

    private suspend fun has_duplicate_token(account: String, token: String, key: ByteArray): Boolean {
        val dao = cache_dao ?: return false
        val rows = dao.get_for_account(account).map { it.to_item() }
        val visible = overlay_pending(account, rows, include_missing = true)
        val pending_ids = pending_contact_ops(account).mapNotNull { it.payload.contact?.contact_id }.toSet()
        return visible.any { item ->
            if (item.id in pending_ids) {
                decrypt_contact(item)?.let { generate_contact_token(it, key) } == token
            } else {
                item.contact_token == token
            }
        }
    }

    private fun encrypt_contact_cipher(contact: Contact, key: ByteArray): PendingContactCipher {
        val payload = encode_contact_json(contact, include_envelope = true)
        val nonce = ByteArray(12).also { java.security.SecureRandom().nextBytes(it) }
        val ciphertext = aes_gcm_encrypt(payload.toByteArray(Charsets.UTF_8), key, nonce)
        val encrypted_data = b64(ciphertext)
        val data_nonce = b64(nonce)
        return PendingContactCipher(
            encrypted_data = encrypted_data,
            data_nonce = data_nonce,
            integrity_hash = generate_integrity_hash(encrypted_data, data_nonce, CONTACT_DATA_VERSION, key),
            data_version = CONTACT_DATA_VERSION,
        )
    }

    suspend fun import_contacts(
        contacts: List<Contact>,
        on_progress: (done: Int, total: Int) -> Unit = { _, _ -> },
    ): ContactImportSummary {
        var imported = 0L
        var updated = 0L
        var skipped = 0L
        var failed = 0
        var limit_reached = false
        var last_failure: Throwable? = null
        val key = write_contacts_key()
        var processed = 0
        on_progress(0, contacts.size)
        try {
            for (chunk in contacts.chunked(IMPORT_CHUNK_SIZE)) {
                if (limit_reached) {
                    failed += chunk.size
                    processed += chunk.size
                    on_progress(processed, contacts.size)
                    continue
                }
                val request = ImportContactsRequest(chunk.map { build_import_item(it, key) })
                val outcome = post_import_with_retry(request)
                outcome.fold(
                    onSuccess = { response ->
                        imported += response.imported
                        updated += response.updated
                        skipped += response.skipped
                        val limit_errors = response.errors.count { it.contains("Contact limit reached") }
                        if (limit_errors > 0) limit_reached = true
                        failed += (chunk.size - response.imported - response.updated - response.skipped)
                            .toInt().coerceAtLeast(0)
                    },
                    onFailure = { t ->
                        failed += chunk.size
                        last_failure = t
                        if (t is ApiError.PlanLimitExceeded) limit_reached = true
                    },
                )
                processed += chunk.size
                on_progress(processed, contacts.size)
            }
        } finally {
            key.fill(0)
            on_local_change()
        }
        return ContactImportSummary(
            imported = imported,
            updated = updated,
            skipped = skipped,
            failed = failed,
            limit_reached = limit_reached,
            last_failure = last_failure,
        )
    }

    private suspend fun post_import_with_retry(request: ImportContactsRequest): Result<ImportContactsResponse> {
        var delay_ms = 1_000L
        var attempt = 0
        while (true) {
            val outcome = runCatching { contacts_api.import_contacts(request) }
            val failure = outcome.exceptionOrNull() ?: return outcome
            if (failure is kotlinx.coroutines.CancellationException) throw failure
            val retryable = failure is ApiError.RateLimited ||
                failure is ApiError.NetworkError ||
                failure is java.io.IOException ||
                (failure is ApiError.ServerError && failure.code >= 500)
            if (!retryable || attempt >= IMPORT_MAX_RETRIES) return outcome
            attempt += 1
            kotlinx.coroutines.delay(delay_ms)
            delay_ms = (delay_ms * 2).coerceAtMost(16_000L)
        }
    }

    private fun build_import_item(contact: Contact, key: ByteArray): ImportContactItem {
        val payload = encode_contact_json(contact, include_envelope = true)
        val nonce = ByteArray(12).also { java.security.SecureRandom().nextBytes(it) }
        val ciphertext = aes_gcm_encrypt(payload.toByteArray(Charsets.UTF_8), key, nonce)
        return ImportContactItem(
            contact_token = generate_contact_token(contact, key),
            encrypted_data = b64(ciphertext),
            data_nonce = b64(nonce),
            name_search_token = generate_name_token(contact, key),
            email_search_token = generate_email_token(contact, key),
        )
    }

    suspend fun update_contact(contact_id: String, contact: Contact): Result<Unit> = runCatching {
        val id = resolve_contact_id(contact_id)
        val account = current_account()
        val dao = cache_dao
        val queue = pending_queue
        if (account == null || dao == null || queue == null) return@runCatching update_contact_now(id, contact, null, null)
        queue.await_loaded()
        val queued = local_ops_mutex.withLock {
            val pending = pending_op_for(account, id)
            if (pending != null || is_local_contact_id(id) || !is_online()) {
                queue_update_locked(account, id, contact, pending)
                true
            } else {
                false
            }
        }
        if (queued) return@runCatching
        try {
            update_contact_now(id, contact, account, dao)
        } catch (cancelled: kotlin.coroutines.cancellation.CancellationException) {
            throw cancelled
        } catch (t: Throwable) {
            if (!is_transient_failure(t)) throw t
            local_ops_mutex.withLock { queue_update_locked(account, id, contact, pending_op_for(account, id)) }
        }
    }.also { on_local_change() }

    private suspend fun update_contact_now(id: String, contact: Contact, account: String?, dao: ContactCacheDao?) {
        val key = write_contacts_key()
        try {
            val request = build_update_request(contact, key)
            val response = contacts_api.update_contact(id, request)
            if (account != null && dao != null) store_updated_row(account, id, request, response)
        } finally {
            key.fill(0)
        }
    }

    private suspend fun store_updated_row(account: String, id: String, request: UpdateContactRequest, response: UpdateContactResponse) {
        val dao = cache_dao ?: return
        if (current_account() != account) return
        val previous = dao.get(account, id)
        cache_writes.incrementAndGet()
        dao.upsert(
            ContactCacheEntity(
                account_id = account,
                id = id,
                contact_token = previous?.contact_token,
                encrypted_data = request.encrypted_data,
                data_nonce = request.data_nonce,
                integrity_hash = request.integrity_hash,
                data_version = CONTACT_DATA_VERSION,
                created_at = previous?.created_at,
                updated_at = java.time.Instant.now().toString(),
                change_seq = response.change_seq ?: previous?.change_seq,
                revision = response.revision ?: previous?.revision,
            ),
        )
    }

    private suspend fun queue_update_locked(account: String, id: String, contact: Contact, pending: PendingMailAction?) {
        val queue = pending_queue ?: throw IllegalStateException("no queue")
        val key = write_contacts_key()
        val cipher = try {
            encrypt_contact_cipher(contact, key)
        } finally {
            key.fill(0)
        }
        if (pending == null && is_local_contact_id(id)) {
            queue.enqueue(
                account,
                PendingActionKind.contact_create,
                PendingActionPayload(
                    contact = PendingContactPayload(contact_id = id, local = cipher, created_at = java.time.Instant.now().toString()),
                ),
            )
            return
        }
        val base = cache_dao?.get(account, id)
        val incoming = ContactOp(
            PendingActionKind.contact_update,
            PendingContactPayload(contact_id = id, local = cipher, base = base?.to_cipher(), base_revision = base?.revision),
        )
        apply_contact_op(queue, account, pending, incoming)
    }

    private suspend fun apply_contact_op(
        queue: PendingMailActionQueue,
        account: String,
        pending: PendingMailAction?,
        incoming: ContactOp,
    ) {
        if (pending == null) {
            queue.enqueue(account, incoming.kind, PendingActionPayload(contact = incoming.payload))
            return
        }
        val existing = pending.payload.contact ?: return
        when (val merged = coalesce_contact_op(ContactOp(pending.kind, existing), incoming)) {
            is ContactOpMerge.Replace -> {
                val rewritten = queue.rewrite(pending.id, merged.op.kind, PendingActionPayload(contact = merged.op.payload))
                if (!rewritten) queue.enqueue(account, merged.op.kind, PendingActionPayload(contact = merged.op.payload))
            }
            ContactOpMerge.Drop -> queue.complete(pending.id)
            ContactOpMerge.Keep -> Unit
        }
    }

    private suspend fun queue_delete_locked(account: String, id: String, pending: PendingMailAction?) {
        val queue = pending_queue ?: throw IllegalStateException("no queue")
        if (pending == null && is_local_contact_id(id)) return
        val base = cache_dao?.get(account, id)
        val incoming = ContactOp(
            PendingActionKind.contact_delete,
            PendingContactPayload(contact_id = id, base = base?.to_cipher(), base_revision = base?.revision),
        )
        apply_contact_op(queue, account, pending, incoming)
    }

    suspend fun trash_contact(contact: Contact): Result<Unit> =
        update_contact(contact.id, contact.copy(deleted_at = java.time.Instant.now().toString()))

    suspend fun restore_contact(contact: Contact): Result<Unit> =
        update_contact(contact.id, contact.copy(deleted_at = ""))

    suspend fun delete_contact(contact_id: String): Result<DeleteContactResponse> = runCatching {
        val id = resolve_contact_id(contact_id)
        val account = current_account()
        val dao = cache_dao
        val queue = pending_queue
        if (account == null || dao == null || queue == null) return@runCatching contacts_api.delete_contact(id)
        queue.await_loaded()
        val queued = local_ops_mutex.withLock {
            val pending = pending_op_for(account, id)
            if (pending != null || is_local_contact_id(id) || !is_online()) {
                queue_delete_locked(account, id, pending)
                true
            } else {
                false
            }
        }
        if (queued) return@runCatching DeleteContactResponse(success = true, deleted_count = 1)
        try {
            contacts_api.delete_contact(id).also { forget_cached(account, id) }
        } catch (cancelled: kotlin.coroutines.cancellation.CancellationException) {
            throw cancelled
        } catch (t: Throwable) {
            if (t is ApiError.NotFoundError) forget_cached(account, id)
            if (!is_transient_failure(t)) throw t
            local_ops_mutex.withLock { queue_delete_locked(account, id, pending_op_for(account, id)) }
            DeleteContactResponse(success = true, deleted_count = 1)
        }
    }.also { on_local_change() }

    suspend fun bulk_delete_contacts(ids: List<String>): Result<DeleteContactResponse> = runCatching {
        val account = current_account()
        val dao = cache_dao
        val queue = pending_queue
        if (account == null || dao == null || queue == null) {
            return@runCatching contacts_api.bulk_delete_contacts(BulkDeleteContactsRequest(ids))
        }
        queue.await_loaded()
        val resolved = ids.map { resolve_contact_id(it) }.distinct()
        val online = is_online()
        val direct = local_ops_mutex.withLock {
            val remaining = mutableListOf<String>()
            for (id in resolved) {
                val pending = pending_op_for(account, id)
                if (pending != null || is_local_contact_id(id) || !online) {
                    queue_delete_locked(account, id, pending)
                } else {
                    remaining.add(id)
                }
            }
            remaining
        }
        val queued_count = resolved.size - direct.size
        if (direct.isEmpty()) return@runCatching DeleteContactResponse(success = true, deleted_count = queued_count)
        try {
            val response = contacts_api.bulk_delete_contacts(BulkDeleteContactsRequest(direct))
            direct.forEach { forget_cached(account, it) }
            response.copy(deleted_count = response.deleted_count + queued_count)
        } catch (cancelled: kotlin.coroutines.cancellation.CancellationException) {
            throw cancelled
        } catch (t: Throwable) {
            if (!is_transient_failure(t)) throw t
            local_ops_mutex.withLock { direct.forEach { queue_delete_locked(account, it, pending_op_for(account, it)) } }
            DeleteContactResponse(success = true, deleted_count = resolved.size)
        }
    }.also { on_local_change() }

    private suspend fun forget_cached(account: String, id: String) {
        val dao = cache_dao ?: return
        if (current_account() != account) return
        cache_writes.incrementAndGet()
        dao.delete(account, id)
    }

    private suspend fun store_item(account: String, item: ContactItem) {
        val dao = cache_dao ?: return
        if (current_account() != account) return
        val row = item.to_cache_row(account) ?: return
        cache_writes.incrementAndGet()
        dao.upsert(row)
    }

    suspend fun replay_pending_op(account: String, action: PendingMailAction): Result<Unit> = try {
        if (current_account() != account) throw java.io.IOException("account not active")
        val payload = action.payload.contact
        if (payload != null) {
            when (action.kind) {
                PendingActionKind.contact_create -> replay_create(account, payload)
                PendingActionKind.contact_update -> replay_update(account, payload)
                PendingActionKind.contact_delete -> replay_delete(account, payload)
                else -> Unit
            }
            ContactPhotoDirectory.invalidate()
        }
        Result.success(Unit)
    } catch (cancelled: kotlin.coroutines.cancellation.CancellationException) {
        throw cancelled
    } catch (e: StorageKeyUnavailableException) {
        Result.failure(java.io.IOException("storage key unavailable", e))
    } catch (e: ContactUndecryptableException) {
        Result.failure(java.io.IOException("remote contact unreadable", e))
    } catch (t: Throwable) {
        Result.failure(t)
    }

    private fun cipher_json(id: String, cipher: PendingContactCipher): String? =
        decrypt_contact(cipher.to_item(id, null, null, null))?.raw_json?.takeIf { it.isNotBlank() }

    private suspend fun fetch_remote_item(id: String): ContactItem? = try {
        contacts_api.get_contact(id)
    } catch (_: ApiError.NotFoundError) {
        null
    }

    private suspend fun contact_token_for_json(json: String): String {
        val contact = parse_contact_json("", json) ?: throw IllegalArgumentException("invalid contact json")
        val key = write_contacts_key()
        return try {
            generate_contact_token(contact, key)
        } finally {
            key.fill(0)
        }
    }

    private suspend fun create_or_adopt(account: String, local_id: String, json: String, created_at: String?): String {
        val adopted = try {
            val (response, request) = create_raw_contact_request(json, unique_token = false)
            val id = response.id ?: throw java.io.IOException("create returned no id")
            store_created(account, local_id, id, request, response.created_at ?: created_at, response.revision, response.change_seq)
            return id
        } catch (e: ApiError.Conflict) {
            find_equivalent_by_token(json, contact_token_for_json(json))
        }
        if (adopted != null) {
            resolved_ids[local_id] = adopted.id
            if (adopted.id != local_id) forget_cached(account, local_id)
            store_item(account, adopted)
            return adopted.id
        }
        val (response, request) = create_raw_contact_request(json, unique_token = true)
        val id = response.id ?: throw java.io.IOException("create returned no id")
        store_created(account, local_id, id, request, response.created_at ?: created_at, response.revision, response.change_seq)
        return id
    }

    private suspend fun find_equivalent_by_token(json: String, token: String): ContactItem? {
        val (items, _) = fetch_all_items(null)
        return items.firstOrNull { item ->
            item.contact_token == token &&
                (decrypt_contact(item)?.raw_json?.let { contact_json_equivalent(it, json) } == true)
        }
    }

    private suspend fun store_created(
        account: String,
        local_id: String,
        id: String,
        request: CreateContactRequest,
        created_at: String?,
        revision: Long?,
        change_seq: Long?,
    ) {
        resolved_ids[local_id] = id
        if (id != local_id) forget_cached(account, local_id)
        val dao = cache_dao ?: return
        if (current_account() != account) return
        cache_writes.incrementAndGet()
        dao.upsert(request.to_cache_row(account, id, created_at, revision, change_seq))
    }

    private suspend fun replay_create(account: String, payload: PendingContactPayload) {
        val local = payload.local ?: return
        val json = cipher_json(payload.contact_id, local) ?: return
        create_or_adopt(account, payload.contact_id, json, payload.created_at)
    }

    private suspend fun replay_update(account: String, payload: PendingContactPayload) {
        val id = resolve_contact_id(payload.contact_id)
        val local = payload.local ?: return
        val local_json = cipher_json(id, local) ?: return
        val base_json = payload.base?.let { cipher_json(id, it) }
        var attempts = 0
        while (true) {
            val item = fetch_remote_item(id)
            if (item == null) {
                if (contact_json_trashed(local_json)) {
                    forget_cached(account, id)
                    return
                }
                create_or_adopt(account, id, local_json, payload.created_at)
                return
            }
            val remote_json = raw_record(item)?.json ?: throw ContactUndecryptableException()
            val merged = merge_contact_json(base_json, local_json, remote_json)
            if (contact_json_equivalent(merged, remote_json)) {
                store_item(account, item)
                return
            }
            try {
                val (response, request) = update_raw_contact_request(id, merged, item.revision)
                store_updated_row(account, id, request, response)
                return
            } catch (e: ApiError.Conflict) {
                if (e.code != REVISION_CONFLICT_CODE) throw e
                attempts++
                if (attempts >= 3) throw java.io.IOException("revision conflict", e)
            } catch (_: ApiError.NotFoundError) {
                attempts++
                if (attempts >= 3) throw java.io.IOException("contact vanished during update")
            }
        }
    }

    private suspend fun replay_delete(account: String, payload: PendingContactPayload) {
        val id = resolve_contact_id(payload.contact_id)
        if (is_local_contact_id(id)) return
        val item = fetch_remote_item(id)
        if (item == null) {
            forget_cached(account, id)
            return
        }
        val base = payload.base
        if (base != null && item.data_nonce != base.data_nonce) {
            store_item(account, item)
            return
        }
        try {
            contacts_api.delete_contact(id)
        } catch (_: ApiError.NotFoundError) {
        }
        forget_cached(account, id)
    }

    private fun on_local_change() {
        ContactPhotoDirectory.invalidate()
        org.astermail.android.contacts.sync.ContactSyncAccounts.notify_local_change()
    }

    suspend fun search_contacts(query: String, field: String = "all", limit: Int? = null): Result<List<Contact>> = runCatching {
        val account = current_account()
        val dao = cache_dao
        if (account == null || dao == null) return@runCatching search_contacts_online(query, field, limit)
        if (!is_online()) return@runCatching search_cached(account, dao, query, field, limit)
        val found = try {
            search_contacts_online(query, field, limit)
        } catch (cancelled: kotlin.coroutines.cancellation.CancellationException) {
            throw cancelled
        } catch (t: Throwable) {
            if (is_transient_failure(t)) return@runCatching search_cached(account, dao, query, field, limit)
            throw t
        }
        val ops = pending_contact_ops(account)
        if (ops.isEmpty()) return@runCatching found
        val pending_ids = ops.mapNotNull { it.payload.contact?.contact_id }.toSet()
        val untouched = found.filter { it.id !in pending_ids }
        val local = search_cached(account, dao, query, field, null).filter { it.id in pending_ids }
        val combined = untouched + local
        if (limit != null) combined.take(limit) else combined
    }

    private suspend fun search_cached(
        account: String,
        dao: ContactCacheDao,
        query: String,
        field: String,
        limit: Int?,
    ): List<Contact> {
        val needle = query.lowercase(java.util.Locale.ROOT).trim()
        if (needle.isEmpty()) return emptyList()
        val matches = cached_contacts(account, dao).filter { contact_matches_search(it, needle, field) }
        return if (limit != null) matches.take(limit) else matches
    }

    private suspend fun search_contacts_online(query: String, field: String, limit: Int?): List<Contact> {
        val keys = StorageWriteKey.lookup_keys(session_key_store)
        val tokens = try {
            keys.map { generate_search_token(query, it) }.distinct()
        } finally {
            keys.forEach { it.fill(0) }
        }
        if (tokens.isEmpty()) throw IllegalStateException("no storage key")
        val items = LinkedHashMap<String, ContactItem>()
        for (token in tokens) {
            if (limit != null && items.size >= limit) break
            contacts_api.search_contacts(token, field, limit).items.forEach { items.putIfAbsent(it.id, it) }
        }
        val found = decrypt_contacts(items.values.toList())
        return if (limit != null) found.take(limit) else found
    }

    suspend fun list_contact_groups(): Result<List<ContactGroup>> = runCatching {
        val account = current_account()
        val dao = group_cache_dao
        if (account == null || dao == null) {
            return@runCatching contacts_api.list_contact_groups().groups.mapNotNull { decrypt_group(it) }
        }
        if (!is_online()) return@runCatching cached_groups(account, dao)
        val response = try {
            contacts_api.list_contact_groups()
        } catch (cancelled: kotlin.coroutines.cancellation.CancellationException) {
            throw cancelled
        } catch (t: Throwable) {
            if (is_transient_failure(t)) return@runCatching cached_groups(account, dao)
            throw t
        }
        if (current_account() == account) {
            dao.replace_for_account(
                account,
                response.groups.mapIndexed { index, group ->
                    ContactGroupCacheEntity(
                        account_id = account,
                        id = group.id,
                        encrypted_name = group.encrypted_name,
                        name_nonce = group.name_nonce,
                        contact_count = group.contact_count,
                        created_at = group.created_at,
                        position = index,
                    )
                },
            )
        }
        response.groups.mapNotNull { decrypt_group(it) }
    }

    private suspend fun cached_groups(account: String, dao: ContactGroupCacheDao): List<ContactGroup> =
        dao.get_for_account(account).mapNotNull {
            decrypt_group(
                ContactGroupEncrypted(
                    id = it.id,
                    encrypted_name = it.encrypted_name,
                    name_nonce = it.name_nonce,
                    contact_count = it.contact_count,
                    created_at = it.created_at,
                ),
            )
        }

    suspend fun clear_cache(account_id: String) {
        runCatching { cache_dao?.clear_for_account(account_id) }
        runCatching { group_cache_dao?.clear_for_account(account_id) }
        resolved_ids.clear()
    }

    suspend fun create_contact_group(
        name: String,
        color: String,
        icon: String? = null,
    ): Result<CreateContactGroupResponse> = runCatching {
        val key = write_contacts_key()
        try {
            val nonce = ByteArray(12).also { java.security.SecureRandom().nextBytes(it) }
            val payload = build_group_payload(name, color, icon)
            val ciphertext = aes_gcm_encrypt(payload.toByteArray(Charsets.UTF_8), key, nonce)
            val group_token = generate_search_token(name, key)
            contacts_api.create_contact_group(
                CreateContactGroupRequest(
                    group_token = group_token,
                    encrypted_name = b64(ciphertext),
                    name_nonce = b64(nonce),
                ),
            )
        } finally {
            key.fill(0)
        }
    }

    suspend fun update_contact_group(
        group_id: String,
        name: String,
        color: String,
        icon: String? = null,
    ): Result<SuccessResponse> = runCatching {
        val key = write_contacts_key()
        try {
            val nonce = ByteArray(12).also { java.security.SecureRandom().nextBytes(it) }
            val payload = build_group_payload(name, color, icon)
            val ciphertext = aes_gcm_encrypt(payload.toByteArray(Charsets.UTF_8), key, nonce)
            contacts_api.update_contact_group(
                group_id,
                UpdateContactGroupRequest(
                    group_token = generate_search_token(name, key),
                    encrypted_name = b64(ciphertext),
                    name_nonce = b64(nonce),
                ),
            )
        } finally {
            key.fill(0)
        }
    }

    suspend fun delete_contact_group(group_id: String): Result<SuccessResponse> = runCatching {
        contacts_api.delete_contact_group(group_id)
    }

    suspend fun add_contact_to_group(contact_id: String, group_id: String): Result<SuccessResponse> = runCatching {
        contacts_api.add_contact_to_group(contact_id, group_id)
    }

    suspend fun remove_contact_from_group(contact_id: String, group_id: String): Result<SuccessResponse> = runCatching {
        contacts_api.remove_contact_from_group(contact_id, group_id)
    }

    suspend fun fetch_raw_changes(since: Long, limit: Int, full: Boolean = false): RawContactChanges {
        val response = contacts_api.list_contact_changes(since, limit, full)
        val records = mutableListOf<RawContactRecord>()
        val undecryptable = mutableListOf<String>()
        for (item in response.changes) {
            val record = raw_record(item)
            if (record == null) undecryptable.add(item.id) else records.add(record)
        }
        return RawContactChanges(
            records = records,
            deleted_ids = response.deleted.map { it.id },
            undecryptable_ids = undecryptable,
            next_since = response.next_since,
            has_more = response.has_more,
        )
    }

    suspend fun fetch_raw_contact(contact_id: String): RawContactRecord? {
        val item = try {
            contacts_api.get_contact(contact_id)
        } catch (_: ApiError.NotFoundError) {
            return null
        }
        return raw_record(item) ?: throw ContactUndecryptableException()
    }

    suspend fun create_raw_contact(json: String, unique_token: Boolean): CreateContactResponse =
        create_raw_contact_request(json, unique_token).first

    private suspend fun create_raw_contact_request(json: String, unique_token: Boolean): Pair<CreateContactResponse, CreateContactRequest> {
        val key = write_contacts_key()
        return try {
            val contact = parse_contact_json("", json) ?: throw IllegalArgumentException("invalid contact json")
            val (encrypted_data, data_nonce) = encrypt_raw_json(json, key)
            val request = CreateContactRequest(
                    contact_token = if (unique_token) {
                        b64(ByteArray(32).also { java.security.SecureRandom().nextBytes(it) })
                    } else {
                        generate_contact_token(contact, key)
                    },
                    encrypted_data = encrypted_data,
                    data_nonce = data_nonce,
                    integrity_hash = generate_integrity_hash(encrypted_data, data_nonce, CONTACT_DATA_VERSION, key),
                    data_version = CONTACT_DATA_VERSION,
                    name_search_token = generate_name_token(contact, key),
                    email_search_token = generate_email_token(contact, key),
                    company_search_token = generate_company_token(contact, key),
                )
            contacts_api.create_contact(request) to request
        } finally {
            key.fill(0)
            ContactPhotoDirectory.invalidate()
        }
    }

    suspend fun update_raw_contact(contact_id: String, json: String, expected_revision: Long?): UpdateContactResponse =
        update_raw_contact_request(contact_id, json, expected_revision).first

    private suspend fun update_raw_contact_request(
        contact_id: String,
        json: String,
        expected_revision: Long?,
    ): Pair<UpdateContactResponse, UpdateContactRequest> {
        val key = write_contacts_key()
        return try {
            val contact = parse_contact_json(contact_id, json) ?: throw IllegalArgumentException("invalid contact json")
            val (encrypted_data, data_nonce) = encrypt_raw_json(json, key)
            val request = UpdateContactRequest(
                    encrypted_data = encrypted_data,
                    data_nonce = data_nonce,
                    integrity_hash = generate_integrity_hash(encrypted_data, data_nonce, CONTACT_DATA_VERSION, key),
                    name_search_token = generate_name_token(contact, key),
                    email_search_token = generate_email_token(contact, key),
                    company_search_token = generate_company_token(contact, key),
                    expected_revision = expected_revision,
                )
            contacts_api.update_contact(contact_id, request) to request
        } finally {
            key.fill(0)
            ContactPhotoDirectory.invalidate()
        }
    }

    private fun raw_record(item: ContactItem): RawContactRecord? {
        val json = decrypt_contact(item)?.raw_json?.takeIf { it.isNotBlank() } ?: return null
        return RawContactRecord(
            id = item.id,
            revision = item.revision ?: 1L,
            change_seq = item.change_seq ?: 0L,
            json = json,
        )
    }

    private fun encrypt_raw_json(json: String, key: ByteArray): Pair<String, String> {
        val obj = org.json.JSONObject(json)
        obj.put("_version", CONTACT_DATA_VERSION)
        obj.put("_encrypted_at", java.time.Instant.now().toString())
        val nonce = ByteArray(12).also { java.security.SecureRandom().nextBytes(it) }
        val ciphertext = aes_gcm_encrypt(obj.toString().toByteArray(Charsets.UTF_8), key, nonce)
        return b64(ciphertext) to b64(nonce)
    }

    private fun build_create_request(contact: Contact, key: ByteArray): CreateContactRequest {
        val payload = encode_contact_json(contact, include_envelope = true)
        val nonce = ByteArray(12).also { java.security.SecureRandom().nextBytes(it) }
        val ciphertext = aes_gcm_encrypt(payload.toByteArray(Charsets.UTF_8), key, nonce)
        val encrypted_data = b64(ciphertext)
        val data_nonce = b64(nonce)
        val integrity_hash = generate_integrity_hash(encrypted_data, data_nonce, CONTACT_DATA_VERSION, key)
        val contact_token = generate_contact_token(contact, key)
        return CreateContactRequest(
            contact_token = contact_token,
            encrypted_data = encrypted_data,
            data_nonce = data_nonce,
            integrity_hash = integrity_hash,
            data_version = CONTACT_DATA_VERSION,
            name_search_token = generate_name_token(contact, key),
            email_search_token = generate_email_token(contact, key),
            company_search_token = generate_company_token(contact, key),
        )
    }

    private fun build_update_request(contact: Contact, key: ByteArray): UpdateContactRequest {
        val payload = encode_contact_json(contact, include_envelope = true)
        val nonce = ByteArray(12).also { java.security.SecureRandom().nextBytes(it) }
        val ciphertext = aes_gcm_encrypt(payload.toByteArray(Charsets.UTF_8), key, nonce)
        val encrypted_data = b64(ciphertext)
        val data_nonce = b64(nonce)
        val integrity_hash = generate_integrity_hash(encrypted_data, data_nonce, CONTACT_DATA_VERSION, key)
        return UpdateContactRequest(
            encrypted_data = encrypted_data,
            data_nonce = data_nonce,
            integrity_hash = integrity_hash,
            name_search_token = generate_name_token(contact, key),
            email_search_token = generate_email_token(contact, key),
            company_search_token = generate_company_token(contact, key),
        )
    }

    private fun decrypt_contact(item: ContactItem): Contact? {
        val encrypted_data = item.encrypted_data ?: return null
        val data_nonce = item.data_nonce ?: return null
        return try {
            val ciphertext = android.util.Base64.decode(encrypted_data, android.util.Base64.DEFAULT)
            val nonce = android.util.Base64.decode(data_nonce, android.util.Base64.DEFAULT)
            val decrypted = decrypt_with_storage_keys(item, encrypted_data, data_nonce, ciphertext, nonce)
                ?: throw IllegalStateException("no storage key opened the contact")
            val json_str = String(decrypted, Charsets.UTF_8)
            decrypted.fill(0)
            parse_contact_json(item.id, json_str)
        } catch (_: Throwable) {
            try {
                decrypt_contact_identity_fallback(item)
            } catch (_: Throwable) {
                null
            }
        }
    }

    private fun decrypt_with_storage_keys(
        item: ContactItem,
        encrypted_data: String,
        data_nonce: String,
        ciphertext: ByteArray,
        nonce: ByteArray,
    ): ByteArray? {
        val keys = StorageWriteKey.read_keys(session_key_store)
        try {
            val ih = item.integrity_hash
            val dv = item.data_version
            for (key in keys) {
                if (ih != null && dv != null && !verify_integrity_hash(encrypted_data, data_nonce, ih, dv, key)) continue
                val opened = runCatching { aes_gcm_decrypt(ciphertext, key, nonce) }.getOrNull()
                if (opened != null) return opened
            }
            return null
        } finally {
            keys.forEach { it.fill(0) }
        }
    }

    private fun decrypt_contact_identity_fallback(item: ContactItem): Contact? {
        val encrypted_data = item.encrypted_data ?: return null
        val data_nonce = item.data_nonce ?: return null
        val ciphertext = android.util.Base64.decode(encrypted_data, android.util.Base64.DEFAULT)
        val nonce = android.util.Base64.decode(data_nonce, android.util.Base64.DEFAULT)
        val plaintext = decrypt_with_candidates(ciphertext, nonce) ?: return null
        val json_str = String(plaintext, Charsets.UTF_8)
        plaintext.fill(0)
        return parse_contact_json(item.id, json_str)
    }

    private fun decrypt_with_candidates(ciphertext: ByteArray, nonce: ByteArray): ByteArray? {
        for (key in read_key_candidates()) {
            try {
                return aes_gcm_decrypt(ciphertext, key, nonce)
            } catch (_: Throwable) {
            } finally {
                key.fill(0)
            }
        }
        return null
    }

    private fun read_key_candidates(): Sequence<ByteArray> = sequence {
        val identity_keys = buildList {
            session_key_store.get_identity_key()?.let { add(it) }
            session_key_store.get_previous_keys()?.forEach { add(it) }
        }
        for (identity_key in identity_keys) {
            for (version in ENVELOPE_VERSIONS) {
                val material = (identity_key + version).toByteArray(Charsets.UTF_8)
                val digest = MessageDigest.getInstance("SHA-256").digest(material)
                material.fill(0)
                yield(digest)
            }
        }
        StorageWriteKey.read_keys(session_key_store).forEach { yield(it) }
    }

    private data class GroupPayload(
        val name: String,
        val color: String,
        val icon: String?,
    )

    private fun build_group_payload(name: String, color: String, icon: String?): String {
        val root = buildJsonObject {
            put("name", JsonPrimitive(name))
            put("color", JsonPrimitive(color))
            icon?.trim()?.takeIf { it.isNotEmpty() }?.let { put("icon", JsonPrimitive(it)) }
        }

        return root.toString()
    }

    private fun parse_group_payload(raw: String): GroupPayload {
        if (!raw.startsWith("{")) return GroupPayload(raw, DEFAULT_GROUP_COLOR, null)

        return try {
            val root = Json.parseToJsonElement(raw).jsonObject
            val name = root["name"]?.jsonPrimitive?.contentOrNull
                ?: return GroupPayload(raw, DEFAULT_GROUP_COLOR, null)

            GroupPayload(
                name = name,
                color = root["color"]?.jsonPrimitive?.contentOrNull ?: DEFAULT_GROUP_COLOR,
                icon = root["icon"]?.jsonPrimitive?.contentOrNull?.trim()?.takeIf { it.isNotEmpty() },
            )
        } catch (_: Throwable) {
            GroupPayload(raw, DEFAULT_GROUP_COLOR, null)
        }
    }

    private fun decrypt_group(group: ContactGroupEncrypted): ContactGroup? {
        return try {
            val ciphertext = android.util.Base64.decode(group.encrypted_name, android.util.Base64.DEFAULT)
            val nonce = android.util.Base64.decode(group.name_nonce, android.util.Base64.DEFAULT)
            val name_bytes = decrypt_with_candidates(ciphertext, nonce) ?: return null
            val payload = parse_group_payload(String(name_bytes, Charsets.UTF_8))
            name_bytes.fill(0)
            ContactGroup(
                id = group.id,
                name = payload.name,
                color = payload.color,
                icon = payload.icon,
                contact_count = group.contact_count,
                created_at = group.created_at,
            )
        } catch (_: Throwable) {
            null
        }
    }

    private suspend fun write_contacts_key(): ByteArray =
        org.astermail.android.crypto.StorageKeyGate.await_write_key(session_key_store)

    private fun derive_subkey(raw_key: ByteArray, info: String): ByteArray {
        val info_bytes = info.toByteArray(Charsets.UTF_8)
        val combined = ByteArray(raw_key.size + info_bytes.size)
        System.arraycopy(raw_key, 0, combined, 0, raw_key.size)
        System.arraycopy(info_bytes, 0, combined, raw_key.size, info_bytes.size)
        val digest = MessageDigest.getInstance("SHA-256").digest(combined)
        combined.fill(0)
        return digest
    }

    private fun hmac_sha256(key: ByteArray, data: ByteArray): ByteArray {
        val mac = Mac.getInstance("HmacSHA256")
        mac.init(SecretKeySpec(key, "HmacSHA256"))
        return mac.doFinal(data)
    }

    private fun generate_search_token(value: String, raw_key: ByteArray): String {
        val sub = derive_subkey(raw_key, SEARCH_INFO)
        try {
            val normalized = value.lowercase(java.util.Locale.ROOT).trim()
            val hash = hmac_sha256(sub, normalized.toByteArray(Charsets.UTF_8))
            return b64(hash)
        } finally {
            sub.fill(0)
        }
    }

    private fun generate_contact_token(contact: Contact, raw_key: ByteArray): String {
        val sub = derive_subkey(raw_key, HMAC_INFO)
        try {
            val (first, last) = split_name(contact.name)
            val emails = listOf(contact.email, contact.work_email).filter { it.isNotBlank() }
            val searchable = "$first $last ${emails.joinToString(" ")}".lowercase(java.util.Locale.ROOT)
            val hash = hmac_sha256(sub, searchable.toByteArray(Charsets.UTF_8))
            return b64(hash)
        } finally {
            sub.fill(0)
        }
    }

    private fun generate_name_token(contact: Contact, raw_key: ByteArray): String? {
        val full = contact.name.trim()
        if (full.isBlank()) return null
        return generate_search_token(full, raw_key)
    }

    private fun generate_email_token(contact: Contact, raw_key: ByteArray): String? {
        val primary = contact.email
        if (primary.isBlank()) return null
        return generate_search_token(primary, raw_key)
    }

    private fun generate_company_token(contact: Contact, raw_key: ByteArray): String? {
        if (contact.company.isBlank()) return null
        return generate_search_token(contact.company, raw_key)
    }

    private fun generate_integrity_hash(encrypted_data: String, nonce: String, version: Int, raw_key: ByteArray): String {
        val sub = derive_subkey(raw_key, HMAC_INFO)
        try {
            val combined = "$encrypted_data:$nonce:$version"
            val hash = hmac_sha256(sub, combined.toByteArray(Charsets.UTF_8))
            return b64(hash)
        } finally {
            sub.fill(0)
        }
    }

    private fun verify_integrity_hash(encrypted_data: String, nonce: String, expected: String, version: Int, raw_key: ByteArray): Boolean {
        val computed = generate_integrity_hash(encrypted_data, nonce, version, raw_key)
        return constant_time_equals(computed, expected)
    }

    private fun constant_time_equals(a: String, b: String): Boolean {
        if (a.length != b.length) return false
        var result = 0
        for (i in a.indices) result = result or (a[i].code xor b[i].code)
        return result == 0
    }

    private fun aes_gcm_decrypt(ciphertext: ByteArray, key: ByteArray, iv: ByteArray): ByteArray =
        AesGcm.decrypt(key, iv, ciphertext)

    private fun aes_gcm_encrypt(plaintext: ByteArray, key: ByteArray, iv: ByteArray): ByteArray =
        AesGcm.encrypt(key, iv, plaintext)

    private fun b64(bytes: ByteArray): String =
        android.util.Base64.encodeToString(bytes, android.util.Base64.NO_WRAP)

    private fun split_name(name: String): Pair<String, String> {
        val trimmed = name.trim()
        if (trimmed.isEmpty()) return "" to ""
        val parts = trimmed.split(" ", limit = 2)
        return parts[0] to (parts.getOrNull(1) ?: "")
    }

    private fun resolve_work_email(emails: List<String>, typed: String): String {
        if (emails.size > 1) return emails[1]
        val primary = emails.firstOrNull().orEmpty()
        return if (typed.equals(primary, ignoreCase = true)) "" else typed
    }

    private fun entry_value(obj: org.json.JSONObject, key: String, type: String): String {
        val arr = obj.optJSONArray(key) ?: return ""
        for (i in 0 until arr.length()) {
            val entry = arr.optJSONObject(i) ?: continue
            if (entry.optString("type", "") == type) return entry.optString("value", "")
        }
        return ""
    }

    private fun first_entry_value(obj: org.json.JSONObject, key: String, excluded_type: String): String {
        val arr = obj.optJSONArray(key) ?: return ""
        for (i in 0 until arr.length()) {
            val entry = arr.optJSONObject(i) ?: continue
            if (entry.optString("type", "") == excluded_type) continue
            val value = entry.optString("value", "")
            if (value.isNotBlank()) return value
        }
        return ""
    }

    private fun put_primary_entry(
        obj: org.json.JSONObject,
        key: String,
        previous: String,
        value: String,
        default_type: String,
    ) {
        val arr = obj.optJSONArray(key) ?: return
        var index = -1
        var fallback = -1
        for (i in 0 until arr.length()) {
            val entry = arr.optJSONObject(i) ?: continue
            if (entry.optString("type", "") == "work") continue
            if (fallback < 0) fallback = i
            if (previous.isNotBlank() && entry.optString("value", "").trim().equals(previous.trim(), ignoreCase = true)) {
                index = i
                break
            }
        }
        if (index < 0) index = fallback
        if (index >= 0 && arr.optJSONObject(index)?.optString("value", "") == value) return
        if (value.isBlank()) {
            if (index >= 0) arr.remove(index)
            if (arr.length() == 0) obj.remove(key)
            return
        }
        if (index >= 0) {
            arr.optJSONObject(index)?.put("value", value)
            return
        }
        val rebuilt = org.json.JSONArray()
        rebuilt.put(org.json.JSONObject().put("value", value).put("type", default_type))
        for (i in 0 until arr.length()) rebuilt.put(arr.opt(i))
        obj.put(key, rebuilt)
    }

    private fun put_typed_entry(obj: org.json.JSONObject, key: String, type: String, value: String, primary: String) {
        val arr = obj.optJSONArray(key)
        if (arr == null) {
            if (value.isBlank()) return
            val created = org.json.JSONArray()
            created.put(org.json.JSONObject().put("value", value).put("type", type))
            obj.put(key, created)
            return
        }
        var index = -1
        for (i in 0 until arr.length()) {
            val entry = arr.optJSONObject(i) ?: continue
            if (entry.optString("type", "") != type) continue
            if (primary.isNotBlank() && entry.optString("value", "").equals(primary, ignoreCase = true)) continue
            index = i
            break
        }
        if (value.isBlank()) {
            if (index >= 0) arr.remove(index)
            if (arr.length() == 0) obj.remove(key)
            return
        }
        if (index >= 0) {
            arr.optJSONObject(index)?.put("value", value)
        } else {
            arr.put(org.json.JSONObject().put("value", value).put("type", type))
        }
    }

    private fun parse_contact_json(id: String, json_str: String): Contact? {
        return try {
            val obj = org.json.JSONObject(json_str)
            val first_name = obj.optString("first_name", "")
            val last_name = obj.optString("last_name", "")
            val name = listOf(first_name, last_name).filter { it.isNotBlank() }.joinToString(" ")
            val emails_arr = obj.optJSONArray("emails")
            val emails = mutableListOf<String>()
            if (emails_arr != null) {
                for (i in 0 until emails_arr.length()) {
                    emails.add(emails_arr.optString(i, ""))
                }
            }
            val address_obj = obj.optJSONObject("address")
            val social_obj = obj.optJSONObject("social_links")
            val groups_arr = obj.optJSONArray("groups")
            val groups = mutableListOf<String>()
            if (groups_arr != null) {
                for (i in 0 until groups_arr.length()) {
                    val entry = groups_arr.optString(i, "").trim()
                    if (entry.isNotEmpty()) groups.add(entry)
                }
            }

            Contact(
                id = id,
                name = name.ifBlank { emails.firstOrNull() ?: "" },
                email = emails.firstOrNull() ?: "",
                phone = obj.optString("phone", "").ifBlank { first_entry_value(obj, "phone_entries", "work") },
                company = obj.optString("company", ""),
                title = obj.optString("role", "").ifBlank { obj.optString("job_title", "") },
                work_email = resolve_work_email(emails, entry_value(obj, "email_entries", "work")),
                work_phone = entry_value(obj, "phone_entries", "work"),
                birthday = obj.optString("birthday", ""),
                address = address_obj?.optString("street", "") ?: "",
                city = address_obj?.optString("city", "") ?: "",
                region = address_obj?.optString("state", "") ?: "",
                postal_code = address_obj?.optString("postal_code", "") ?: "",
                country = address_obj?.optString("country", "") ?: "",
                website = social_obj?.optString("website", "") ?: "",
                twitter = social_obj?.optString("twitter", "") ?: "",
                linkedin = social_obj?.optString("linkedin", "") ?: "",
                notes = obj.optString("notes", ""),
                avatar_url = obj.optString("avatar_url", ""),
                profile_color = obj.optString("profile_color", ""),
                is_favorite = obj.optBoolean("is_favorite", false),
                groups = groups.toList(),
                raw_json = json_str,
                deleted_at = obj.optString("deleted_at", ""),
            ).let { parsed ->
                val card = org.astermail.android.contacts.sync.contact_card_from_json_object(obj)
                parsed.with_typed_fields(
                    emails = card.emails.map { ContactEntry(it.value, it.type, it.label) },
                    phones = card.phones.map { ContactEntry(it.value, it.type, it.label) },
                    addresses = card.addresses.map {
                        ContactPostal(it.street, it.city, it.state, it.postal_code, it.country, it.type, it.label)
                    },
                )
            }
        } catch (_: Throwable) {
            null
        }
    }

    private fun encode_contact_json(contact: Contact, include_envelope: Boolean): String {
        val obj = if (contact.raw_json.isBlank()) {
            org.json.JSONObject()
        } else {
            try {
                org.json.JSONObject(contact.raw_json)
            } catch (_: Throwable) {
                org.json.JSONObject()
            }
        }
        val (first, last) = split_name(contact.name)
        obj.put("first_name", first)
        obj.put("last_name", last)

        if (contact.has_typed_fields && contact.typed_fields_in_sync()) {
            write_typed_fields(obj, contact)
        } else {
            write_flat_fields(obj, contact)
        }
        if (contact.company.isNotBlank()) obj.put("company", contact.company) else obj.remove("company")
        if (contact.title.isNotBlank()) obj.put("job_title", contact.title) else obj.remove("job_title")
        if (contact.title.isNotBlank()) obj.put("role", contact.title) else obj.remove("role")

        val social = obj.optJSONObject("social_links") ?: org.json.JSONObject()
        if (contact.website.isNotBlank()) social.put("website", contact.website) else social.remove("website")
        if (contact.twitter.isNotBlank()) social.put("twitter", contact.twitter) else social.remove("twitter")
        if (contact.linkedin.isNotBlank()) social.put("linkedin", contact.linkedin) else social.remove("linkedin")
        if (social.length() > 0) obj.put("social_links", social) else obj.remove("social_links")

        if (contact.birthday.isNotBlank()) obj.put("birthday", contact.birthday) else obj.remove("birthday")
        if (contact.notes.isNotBlank()) obj.put("notes", contact.notes) else obj.remove("notes")
        if (contact.avatar_url.isNotBlank()) {
            obj.put("avatar_url", contact.avatar_url)
        } else {
            obj.remove("avatar_url")
        }
        if (contact.profile_color.isNotBlank()) {
            obj.put("profile_color", contact.profile_color)
        } else {
            obj.remove("profile_color")
        }
        obj.put("is_favorite", contact.is_favorite)

        if (contact.groups.isNotEmpty()) {
            val groups = org.json.JSONArray()
            for (name in contact.groups) groups.put(name)
            obj.put("groups", groups)
        } else {
            obj.remove("groups")
        }

        if (contact.deleted_at.isNotBlank()) {
            obj.put("deleted_at", contact.deleted_at)
        } else {
            obj.remove("deleted_at")
        }

        if (include_envelope) {
            obj.put("_version", CONTACT_DATA_VERSION)
            obj.put("_encrypted_at", java.time.Instant.now().toString())
        }

        return obj.toString()
    }

    private fun write_flat_fields(obj: org.json.JSONObject, contact: Contact) {
        val previous_emails = obj.optJSONArray("emails")
        val previous_email = previous_emails?.optString(0, "").orEmpty()
        val previous_phone = obj.optString("phone", "").ifBlank { first_entry_value(obj, "phone_entries", "work") }
        val emails = org.json.JSONArray()
        if (contact.email.isNotBlank()) emails.put(contact.email)
        if (contact.work_email.isNotBlank()) emails.put(contact.work_email)
        if (previous_emails != null) {
            val seen = mutableSetOf(contact.email.lowercase(), contact.work_email.lowercase())
            for (i in 1 until previous_emails.length()) {
                val extra = previous_emails.optString(i, "")
                if (extra.isNotBlank() && seen.add(extra.lowercase())) emails.put(extra)
            }
        }
        obj.put("emails", emails)
        put_primary_entry(obj, "email_entries", previous_email, contact.email, "other")
        put_primary_entry(obj, "phone_entries", previous_phone, contact.phone, "mobile")
        put_typed_entry(obj, "email_entries", "work", contact.work_email, contact.email)
        put_typed_entry(obj, "phone_entries", "work", contact.work_phone, contact.phone)

        if (contact.phone.isNotBlank()) obj.put("phone", contact.phone) else obj.remove("phone")

        val has_address = listOf(contact.address, contact.city, contact.region, contact.postal_code, contact.country)
            .any { it.isNotBlank() }
        if (has_address) {
            val addr = obj.optJSONObject("address") ?: org.json.JSONObject()
            addr.put("street", contact.address)
            addr.put("city", contact.city)
            addr.put("state", contact.region)
            addr.put("postal_code", contact.postal_code)
            addr.put("country", contact.country)
            obj.put("address", addr)
        } else {
            obj.remove("address")
        }
        val entries = obj.optJSONArray("address_entries") ?: return
        var index = -1
        for (i in 0 until entries.length()) {
            val entry = entries.optJSONObject(i) ?: continue
            if (index < 0) index = i
            if (entry.optString("type", "") == "home") {
                index = i
                break
            }
        }
        if (index < 0) return
        if (!has_address) {
            entries.remove(index)
            if (entries.length() == 0) obj.remove("address_entries")
            return
        }
        entries.optJSONObject(index)?.apply {
            put("street", contact.address)
            put("city", contact.city)
            put("state", contact.region)
            put("postal_code", contact.postal_code)
            put("country", contact.country)
        }
    }

    private fun write_typed_fields(obj: org.json.JSONObject, contact: Contact) {
        val card = org.astermail.android.contacts.sync.ContactCard(
            emails = contact.emails.map { org.astermail.android.contacts.sync.CardEntry(it.value, it.type, it.label) },
            phones = contact.phones.map { org.astermail.android.contacts.sync.CardEntry(it.value, it.type, it.label) },
            addresses = contact.addresses.map {
                org.astermail.android.contacts.sync.CardAddress(
                    it.street,
                    it.city,
                    it.region,
                    it.postal_code,
                    it.country,
                    it.type,
                    it.label,
                )
            },
        ).normalized()
        for (field in TYPED_FIELDS) {
            org.astermail.android.contacts.sync.write_card_field(obj, card, field)
        }
        if (card.emails.isEmpty()) obj.remove("email_entries")
        if (card.phones.isEmpty()) obj.remove("phone_entries")
        if (card.addresses.isEmpty()) obj.remove("address_entries")
    }

    companion object {
        private const val IMPORT_CHUNK_SIZE = 100
        private const val IMPORT_MAX_RETRIES = 3
        private val PAGE_RETRY_DELAYS_MS = longArrayOf(500L, 1_500L, 4_000L)
        private const val HMAC_INFO = "contacts-hmac-v2"
        private const val SEARCH_INFO = "contacts-search-v2"
        private val TYPED_FIELDS = listOf(
            org.astermail.android.contacts.sync.CardField.EMAILS,
            org.astermail.android.contacts.sync.CardField.PHONES,
            org.astermail.android.contacts.sync.CardField.ADDRESSES,
        )
        private val ENVELOPE_VERSIONS = listOf("astermail-envelope-v1", "astermail-import-v1")
    }
}

data class RawContactRecord(
    val id: String,
    val revision: Long,
    val change_seq: Long,
    val json: String,
)

data class RawContactChanges(
    val records: List<RawContactRecord>,
    val deleted_ids: List<String>,
    val undecryptable_ids: List<String>,
    val next_since: Long,
    val has_more: Boolean,
)

data class ContactImportSummary(
    val imported: Long,
    val updated: Long,
    val skipped: Long,
    val failed: Int,
    val limit_reached: Boolean,
    val last_failure: Throwable?,
)

private fun ContactItem.to_cache_row(account: String): ContactCacheEntity? {
    val data = encrypted_data ?: return null
    val nonce = data_nonce ?: return null
    return ContactCacheEntity(
        account_id = account,
        id = id,
        contact_token = contact_token,
        encrypted_data = data,
        data_nonce = nonce,
        integrity_hash = integrity_hash,
        data_version = data_version,
        created_at = created_at,
        updated_at = updated_at,
        change_seq = change_seq,
        revision = revision,
    )
}

private fun ContactCacheEntity.to_item(): ContactItem = ContactItem(
    id = id,
    contact_token = contact_token,
    encrypted_data = encrypted_data,
    data_nonce = data_nonce,
    integrity_hash = integrity_hash,
    data_version = data_version,
    created_at = created_at,
    updated_at = updated_at,
    change_seq = change_seq,
    revision = revision,
)

private fun ContactCacheEntity.to_cipher(): PendingContactCipher = PendingContactCipher(
    encrypted_data = encrypted_data,
    data_nonce = data_nonce,
    integrity_hash = integrity_hash,
    data_version = data_version,
)

private fun PendingContactCipher.to_item(
    id: String,
    created_at: String?,
    revision: Long?,
    contact_token: String?,
): ContactItem = ContactItem(
    id = id,
    contact_token = contact_token,
    encrypted_data = encrypted_data,
    data_nonce = data_nonce,
    integrity_hash = integrity_hash,
    data_version = data_version,
    created_at = created_at,
    revision = revision,
)

private fun CreateContactRequest.to_cache_row(
    account: String,
    id: String,
    created_at: String?,
    revision: Long?,
    change_seq: Long?,
): ContactCacheEntity = ContactCacheEntity(
    account_id = account,
    id = id,
    contact_token = contact_token,
    encrypted_data = encrypted_data,
    data_nonce = data_nonce,
    integrity_hash = integrity_hash,
    data_version = data_version,
    created_at = created_at,
    updated_at = created_at,
    change_seq = change_seq,
    revision = revision,
)

internal fun contact_matches_search(contact: Contact, needle: String, field: String): Boolean {
    fun same(value: String): Boolean = value.isNotBlank() && value.lowercase(java.util.Locale.ROOT).trim() == needle
    return when (field) {
        "name" -> same(contact.name)
        "email" -> same(contact.email)
        "company" -> same(contact.company)
        else -> same(contact.name) || same(contact.email) || same(contact.company)
    }
}

private fun contact_json_trashed(json: String): Boolean =
    runCatching { org.json.JSONObject(json).optString("deleted_at", "").isNotBlank() }.getOrDefault(false)
