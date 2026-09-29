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

import androidx.compose.ui.graphics.ImageBitmap
import java.util.Locale
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.Job
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import org.astermail.android.ui.contacts.Contact
import org.astermail.android.ui.contacts.decode_contact_photo

const val CONTACT_PHOTO_INDEX_TTL_MS = 10L * 60L * 1000L
const val CONTACT_PHOTO_RETRY_MS = 30L * 1000L
const val CONTACT_PHOTO_SEED_BUDGET_CHARS = 4 * 1024 * 1024
const val CONTACT_PHOTO_BITMAP_BUDGET_BYTES = 12 * 1024 * 1024

data class ContactPhotoEntry(
    val contact_id: String,
    val emails: List<String>,
    val photo: String,
)

sealed interface ContactPhotoFetch {
    data class Found(val photo: String) : ContactPhotoFetch
    object Missing : ContactPhotoFetch
    data class Failed(val error: Throwable) : ContactPhotoFetch
}

interface ContactPhotoSource {
    suspend fun list_photo_entries(): List<ContactPhotoEntry>
    suspend fun fetch_contact_photo(contact_id: String): ContactPhotoFetch
}

fun normalize_contact_email(raw: String?): String? {
    if (raw == null) return null
    var value = raw.trim()
    val open = value.lastIndexOf('<')
    val close = value.lastIndexOf('>')
    if (open >= 0 && close > open) value = value.substring(open + 1, close).trim()
    if (value.startsWith("mailto:", ignoreCase = true)) value = value.substring(7).trim()
    if (value.isEmpty() || value.any { it.isWhitespace() }) return null
    val at = value.lastIndexOf('@')
    if (at <= 0 || at == value.length - 1) return null
    return value.lowercase(Locale.ROOT)
}

fun is_inline_contact_photo(photo: String): Boolean =
    photo.startsWith("data:image", ignoreCase = true)

fun contact_photo_emails(contact: Contact): List<String> {
    val candidates = mutableListOf(contact.email, contact.work_email)
    if (contact.raw_json.isNotBlank()) {
        runCatching {
            val obj = org.json.JSONObject(contact.raw_json)
            obj.optJSONArray("emails")?.let { arr ->
                for (i in 0 until arr.length()) candidates.add(arr.optString(i, ""))
            }
            obj.optJSONArray("email_entries")?.let { arr ->
                for (i in 0 until arr.length()) {
                    candidates.add(arr.optJSONObject(i)?.optString("value", "").orEmpty())
                }
            }
        }
    }
    val seen = LinkedHashSet<String>()
    for (candidate in candidates) {
        normalize_contact_email(candidate)?.let { seen.add(it) }
    }
    return seen.toList()
}

fun contact_photo_entry(contact: Contact): ContactPhotoEntry? {
    if (contact.id.isBlank() || contact.deleted_at.isNotBlank()) return null
    val photo = contact.avatar_url.trim()
    if (!is_inline_contact_photo(photo)) return null
    val emails = contact_photo_emails(contact)
    if (emails.isEmpty()) return null
    return ContactPhotoEntry(contact_id = contact.id, emails = emails, photo = photo)
}

fun build_contact_photo_index(entries: List<ContactPhotoEntry>): Map<String, String> {
    val index = LinkedHashMap<String, String>()
    for (entry in entries) {
        if (entry.contact_id.isBlank() || !is_inline_contact_photo(entry.photo)) continue
        for (email in entry.emails) {
            val key = normalize_contact_email(email) ?: continue
            if (!index.containsKey(key)) index[key] = entry.contact_id
        }
    }
    return index
}

private fun photo_fingerprint(photo: String): Long =
    (photo.length.toLong() shl 32) xor (photo.hashCode().toLong() and 0xffffffffL)

internal class ContactPhotoLru<B : Any>(
    private val max_bytes: Int,
    private val size_of: (B) -> Int,
) {
    private val entries = LinkedHashMap<String, B>(16, 0.75f, true)
    private var total = 0

    fun get(key: String): B? = entries[key]

    fun contains(key: String): Boolean = entries.containsKey(key)

    fun put(key: String, value: B) {
        entries.remove(key)?.let { total -= size_of(it) }
        val size = size_of(value)
        if (size > max_bytes) return
        entries[key] = value
        total += size
        val iterator = entries.entries.iterator()
        while (total > max_bytes && iterator.hasNext()) {
            val eldest = iterator.next()
            total -= size_of(eldest.value)
            iterator.remove()
        }
    }

    fun remove(key: String) {
        entries.remove(key)?.let { total -= size_of(it) }
    }

    fun clear() {
        entries.clear()
        total = 0
    }

    fun size(): Int = entries.size
}

class ContactPhotoDirectoryCore<B : Any>(
    private val decode: (String) -> B?,
    size_of: (B) -> Int,
    max_bitmap_bytes: Int,
    private val scope: CoroutineScope,
    private val clock: () -> Long = System::currentTimeMillis,
    private val seed_budget_chars: Int = CONTACT_PHOTO_SEED_BUDGET_CHARS,
) {
    @Volatile
    var source: ContactPhotoSource? = null

    private val lock = Any()
    private val _version = MutableStateFlow(0L)
    val version: StateFlow<Long> = _version.asStateFlow()

    private var generation = 0L
    private var index: Map<String, String>? = null
    private var fingerprints: Map<String, Long> = emptyMap()
    private var index_loaded_at = 0L
    private var index_failed_at: Long? = null
    private var index_job: Deferred<Map<String, String>?>? = null
    private val seeds = HashMap<String, String>()
    private val bitmaps = ContactPhotoLru(max_bitmap_bytes, size_of)
    private val missing = HashSet<String>()
    private val retry_after = HashMap<String, Long>()
    private val photo_jobs = HashMap<String, Deferred<B?>>()
    private var retry_job: Job? = null

    fun cached(email: String): B? {
        val key = normalize_contact_email(email) ?: return null
        synchronized(lock) {
            val contact_id = index?.get(key) ?: return null
            return bitmaps.get(contact_id)
        }
    }

    suspend fun photo_for(email: String): B? {
        val key = normalize_contact_email(email) ?: return null
        val current = ensure_index() ?: return null
        val contact_id = current[key] ?: return null
        val job = synchronized(lock) {
            bitmaps.get(contact_id)?.let { return it }
            if (contact_id in missing) return null
            val blocked_until = retry_after[contact_id]
            if (blocked_until != null && clock() < blocked_until) return null
            photo_jobs[contact_id] ?: start_photo_job(contact_id, generation).also {
                photo_jobs[contact_id] = it
            }
        }
        return job.await()
    }

    fun offer(entries: List<ContactPhotoEntry>) {
        val gen = synchronized(lock) { generation }
        apply_entries(entries, gen)
    }

    fun clear() {
        synchronized(lock) {
            generation += 1
            index_job?.cancel()
            photo_jobs.values.forEach { it.cancel() }
            photo_jobs.clear()
            index_job = null
            retry_job?.cancel()
            retry_job = null
            index = null
            fingerprints = emptyMap()
            index_loaded_at = 0L
            index_failed_at = null
            seeds.clear()
            bitmaps.clear()
            missing.clear()
            retry_after.clear()
            _version.value = _version.value + 1
        }
    }

    private suspend fun ensure_index(): Map<String, String>? {
        val job = synchronized(lock) {
            val now = clock()
            val current = index
            if (current != null && now - index_loaded_at < CONTACT_PHOTO_INDEX_TTL_MS) return current
            val failed_at = index_failed_at
            if (failed_at != null && now - failed_at < CONTACT_PHOTO_RETRY_MS) return current
            if (source == null) return current
            val pending = index_job ?: start_index_job(generation).also { index_job = it }
            if (current != null) return current
            pending
        }
        return job.await()
    }

    private fun start_index_job(gen: Long): Deferred<Map<String, String>?> = scope.async {
        val entries = try {
            source?.list_photo_entries()
        } catch (cancelled: kotlinx.coroutines.CancellationException) {
            synchronized(lock) { if (gen == generation) index_job = null }
            throw cancelled
        } catch (_: Throwable) {
            null
        }
        if (entries == null) {
            synchronized(lock) {
                if (gen == generation) {
                    index_failed_at = clock()
                    index_job = null
                    schedule_retry(gen)
                }
                return@async index
            }
        }
        apply_entries(entries, gen)
    }

    private fun apply_entries(entries: List<ContactPhotoEntry>, gen: Long): Map<String, String>? {
        val built = build_contact_photo_index(entries)
        val indexed_ids = built.values.toHashSet()
        val prints = HashMap<String, Long>()
        for (entry in entries) {
            if (entry.contact_id in indexed_ids && !prints.containsKey(entry.contact_id)) {
                prints[entry.contact_id] = photo_fingerprint(entry.photo)
            }
        }
        synchronized(lock) {
            if (gen != generation) return index
            for ((contact_id, print) in fingerprints) {
                if (prints[contact_id] != print) bitmaps.remove(contact_id)
            }
            fingerprints = prints
            index = built
            index_loaded_at = clock()
            index_failed_at = null
            index_job = null
            missing.clear()
            retry_after.clear()
            seeds.clear()
            var budget = seed_budget_chars
            for (entry in entries) {
                val contact_id = entry.contact_id
                if (contact_id !in indexed_ids || seeds.containsKey(contact_id)) continue
                if (bitmaps.contains(contact_id)) continue
                if (entry.photo.length > budget) continue
                seeds[contact_id] = entry.photo
                budget -= entry.photo.length
            }
            _version.value = _version.value + 1
            return built
        }
    }

    private fun mark_photo_retry(contact_id: String, gen: Long) {
        retry_after[contact_id] = clock() + CONTACT_PHOTO_RETRY_MS
        schedule_retry(gen)
    }

    private fun schedule_retry(gen: Long) {
        if (retry_job?.isActive == true) return
        retry_job = scope.launch {
            delay(CONTACT_PHOTO_RETRY_MS)
            synchronized(lock) {
                if (gen == generation) {
                    retry_job = null
                    _version.value = _version.value + 1
                }
            }
        }
    }

    private fun start_photo_job(contact_id: String, gen: Long): Deferred<B?> = scope.async {
        try {
            val seed = synchronized(lock) { seeds.remove(contact_id) }
            val fetch = when {
                seed != null -> ContactPhotoFetch.Found(seed)
                else -> source?.fetch_contact_photo(contact_id)
                    ?: ContactPhotoFetch.Failed(IllegalStateException("no source"))
            }
            when (fetch) {
                is ContactPhotoFetch.Found -> {
                    val decoded = if (is_inline_contact_photo(fetch.photo)) {
                        runCatching { decode(fetch.photo) }.getOrNull()
                    } else {
                        null
                    }
                    synchronized(lock) {
                        if (gen != generation) return@async null
                        if (decoded == null) missing.add(contact_id) else bitmaps.put(contact_id, decoded)
                    }
                    decoded
                }
                ContactPhotoFetch.Missing -> {
                    synchronized(lock) { if (gen == generation) missing.add(contact_id) }
                    null
                }
                is ContactPhotoFetch.Failed -> {
                    synchronized(lock) { if (gen == generation) mark_photo_retry(contact_id, gen) }
                    null
                }
            }
        } catch (cancelled: kotlinx.coroutines.CancellationException) {
            throw cancelled
        } catch (_: Throwable) {
            synchronized(lock) { if (gen == generation) mark_photo_retry(contact_id, gen) }
            null
        } finally {
            synchronized(lock) { if (gen == generation) photo_jobs.remove(contact_id) }
        }
    }
}

object ContactPhotoDirectory {
    private val core = ContactPhotoDirectoryCore<ImageBitmap>(
        decode = { decode_contact_photo(it) },
        size_of = { it.width * it.height * 4 },
        max_bitmap_bytes = CONTACT_PHOTO_BITMAP_BUDGET_BYTES,
        scope = CoroutineScope(SupervisorJob() + Dispatchers.IO),
    )

    val version: StateFlow<Long> get() = core.version

    fun install(provider: () -> ContactPhotoSource) {
        core.source = object : ContactPhotoSource {
            override suspend fun list_photo_entries(): List<ContactPhotoEntry> =
                provider().list_photo_entries()

            override suspend fun fetch_contact_photo(contact_id: String): ContactPhotoFetch =
                provider().fetch_contact_photo(contact_id)
        }
    }

    fun cached(email: String): ImageBitmap? = core.cached(email)

    suspend fun photo_for(email: String): ImageBitmap? = core.photo_for(email)

    fun offer(entries: List<ContactPhotoEntry>) = core.offer(entries)

    fun invalidate() = core.clear()

    fun clear() = core.clear()
}
