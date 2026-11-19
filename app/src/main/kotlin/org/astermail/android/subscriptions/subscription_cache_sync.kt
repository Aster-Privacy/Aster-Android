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

package org.astermail.android.subscriptions

import java.security.MessageDigest
import java.security.SecureRandom
import java.time.Instant
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import org.astermail.android.api.subscriptions.SaveEncryptedSubscriptionCacheRequest
import org.astermail.android.api.subscriptions.SubscriptionsApi
import org.astermail.android.crypto.AesGcm
import org.astermail.android.storage.SessionKeyStore

internal const val SUBSCRIPTIONS_KEY_SUFFIX = "astermail-subscriptions-v1"

private fun string_field(obj: JsonObject, name: String): String? =
    (obj[name] as? JsonPrimitive)?.takeIf { it.isString }?.content

internal fun merge_tombstones(
    vararg sources: Map<String, String>,
    max_entries: Int = MAX_DELETED_SUBSCRIPTIONS,
): Map<String, String> {
    val latest = LinkedHashMap<String, Pair<String, Instant>>()
    for (source in sources) {
        for ((raw_sender, deleted_at) in source) {
            val sender = normalize_sender_email(raw_sender)
            if (!sender.contains('@')) continue
            val time = parse_subscription_instant(deleted_at) ?: continue
            val existing = latest[sender]
            if (existing == null || existing.second.isBefore(time)) {
                latest[sender] = deleted_at to time
            }
        }
    }
    return latest.entries
        .sortedByDescending { it.value.second }
        .take(max_entries)
        .associateTo(LinkedHashMap()) { it.key to it.value.first }
}

internal fun cache_tombstones(cache: JsonObject): Map<String, String> {
    val entries = cache["deleted"] as? JsonArray ?: return emptyMap()
    return merge_tombstones(
        *entries.mapNotNull { entry ->
            val obj = entry as? JsonObject ?: return@mapNotNull null
            val sender = string_field(obj, "sender_email") ?: return@mapNotNull null
            val deleted_at = string_field(obj, "deleted_at") ?: return@mapNotNull null
            mapOf(sender to deleted_at)
        }.toTypedArray(),
    )
}

internal fun apply_tombstones_to_cache(cache: JsonObject, tombstones: Map<String, String>): JsonObject {
    val merged = merge_tombstones(cache_tombstones(cache), tombstones)
    if (merged.isEmpty()) {
        if (!cache.containsKey("deleted")) return cache
        return JsonObject(cache.filterKeys { it != "deleted" })
    }
    val result = LinkedHashMap<String, JsonElement>(cache)
    val subscriptions = cache["subscriptions"] as? JsonArray
    if (subscriptions != null) {
        result["subscriptions"] = JsonArray(
            subscriptions.filterNot { element ->
                val obj = element as? JsonObject ?: return@filterNot false
                val sender = string_field(obj, "sender_email") ?: return@filterNot false
                is_deleted_subscription(merged, sender, string_field(obj, "last_received").orEmpty())
            },
        )
    }
    result["deleted"] = buildJsonArray {
        merged.forEach { (sender, deleted_at) ->
            add(
                buildJsonObject {
                    put("sender_email", sender)
                    put("deleted_at", deleted_at)
                },
            )
        }
    }
    return JsonObject(result)
}

internal fun cache_needs_upload(before: JsonObject, after: JsonObject): Boolean =
    cache_tombstones(before) != cache_tombstones(after) ||
        before["subscriptions"] != after["subscriptions"]

internal fun derive_subscriptions_key(source: String): ByteArray =
    MessageDigest.getInstance("SHA-256")
        .digest((source + SUBSCRIPTIONS_KEY_SUFFIX).toByteArray(Charsets.UTF_8))

internal fun seal_subscription_cache(plaintext: ByteArray, identity_key: String): Pair<String, String> {
    val key = derive_subscriptions_key(identity_key)
    val nonce = ByteArray(12).also { SecureRandom().nextBytes(it) }
    try {
        val ciphertext = AesGcm.encrypt(key, nonce, plaintext)
        val encoder = java.util.Base64.getEncoder()
        return encoder.encodeToString(ciphertext) to encoder.encodeToString(nonce)
    } finally {
        key.fill(0)
    }
}

internal fun open_subscription_cache(
    encrypted_b64: String,
    nonce_b64: String,
    key_sources: List<String>,
    raw_keks_b64: List<String>,
): ByteArray? {
    val decoder = java.util.Base64.getMimeDecoder()
    val ciphertext = runCatching { decoder.decode(encrypted_b64) }.getOrNull() ?: return null
    val nonce = runCatching { decoder.decode(nonce_b64) }.getOrNull() ?: return null
    if (nonce.size != 12) return null
    for (source in key_sources.filter { it.isNotBlank() }.distinct()) {
        val key = derive_subscriptions_key(source)
        try {
            return AesGcm.decrypt(key, nonce, ciphertext)
        } catch (_: Throwable) {
        } finally {
            key.fill(0)
        }
    }
    for (kek_b64 in raw_keks_b64) {
        val raw = runCatching { decoder.decode(kek_b64) }.getOrNull() ?: continue
        try {
            if (raw.size == 32) return AesGcm.decrypt(raw, nonce, ciphertext)
        } catch (_: Throwable) {
        } finally {
            raw.fill(0)
        }
    }
    return null
}

@Singleton
class SubscriptionCacheSync @Inject constructor(
    private val api: SubscriptionsApi,
    private val session_key_store: SessionKeyStore,
    private val deleted_store: DeletedSubscriptionsStore,
) {
    private val mutex = Mutex()
    private val json = Json { ignoreUnknownKeys = true }

    suspend fun sync_tombstones(): Map<String, String> = mutex.withLock {
        val local = deleted_store.tombstones()
        try {
            sync_with_remote(local)
        } catch (e: CancellationException) {
            throw e
        } catch (_: Throwable) {
            local
        }
    }

    private suspend fun sync_with_remote(local: Map<String, String>): Map<String, String> {
        val identity_key = session_key_store.get_identity_key()?.takeIf { it.isNotBlank() } ?: return local
        val remote = api.get_encrypted_cache()
        val encrypted = remote.encrypted_subscriptions?.takeIf { it.isNotBlank() } ?: return local
        val nonce = remote.subscriptions_nonce?.takeIf { it.isNotBlank() } ?: return local
        val plaintext = open_subscription_cache(
            encrypted,
            nonce,
            listOf(identity_key) + session_key_store.get_previous_keys().orEmpty(),
            session_key_store.get_decrypt_keks(),
        ) ?: return local
        val cache = try {
            runCatching { json.parseToJsonElement(String(plaintext, Charsets.UTF_8)) as? JsonObject }.getOrNull()
        } finally {
            plaintext.fill(0)
        } ?: return local
        val merged = merge_tombstones(cache_tombstones(cache), local)
        if (merged != local) deleted_store.replace_all(merged)
        val updated = apply_tombstones_to_cache(cache, merged)
        if (cache_needs_upload(cache, updated)) {
            val payload = updated.toString().toByteArray(Charsets.UTF_8)
            val sealed = try {
                seal_subscription_cache(payload, identity_key)
            } finally {
                payload.fill(0)
            }
            api.save_encrypted_cache(SaveEncryptedSubscriptionCacheRequest(sealed.first, sealed.second))
        }
        return merged
    }
}
