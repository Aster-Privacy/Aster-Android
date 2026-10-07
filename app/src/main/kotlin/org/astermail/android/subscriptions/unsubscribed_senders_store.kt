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

import android.content.Context
import android.os.SystemClock
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import org.astermail.android.api.subscriptions.SubscriptionsApi
import org.astermail.android.api.subscriptions.TrackSubscriptionRequest
import org.astermail.android.api.subscriptions.UnsubscribeRequest
import org.astermail.android.mail.MailRepository

private const val PREFS_NAME = "aster_unsubscribed_senders"
private const val KEY_PREFS_NAME = "aster_unsubscribed_sender_keys"
private const val KEY_TOKEN_KEY = "token_key"
private const val CONFIRMED_PREFIX = "v2_confirmed_"
private const val PENDING_PREFIX = "v2_pending_"
private const val LEGACY_CONFIRMED_PREFIX = "confirmed_"
private const val LEGACY_PENDING_PREFIX = "pending_"
private const val HMAC_ALGORITHM = "HmacSHA256"
internal const val UNSUBSCRIBED_TOKEN_KEY_BYTES = 32
private const val REFRESH_INTERVAL_MS = 60_000L
private const val REFRESH_PAGE_SIZE = 100
private const val REFRESH_MAX_PAGES = 50
private const val RECORD_ATTEMPTS = 3
private const val RECORD_RETRY_DELAY_MS = 2_000L
private const val MAX_SENDER_NAME_LENGTH = 255
private const val MAX_UNSUBSCRIBE_FIELD_LENGTH = 2048

private fun hmac_sha256(key: ByteArray, value: String): ByteArray {
    val mac = javax.crypto.Mac.getInstance(HMAC_ALGORITHM)
    mac.init(javax.crypto.spec.SecretKeySpec(key, HMAC_ALGORITHM))
    return mac.doFinal(value.toByteArray(Charsets.UTF_8))
}

private fun to_hex(bytes: ByteArray): String = bytes.joinToString("") { "%02x".format(it) }

internal fun normalize_sender_email(sender_email: String): String =
    sender_email.trim().lowercase(java.util.Locale.ROOT)

internal fun unsubscribed_account_secret(token_key: ByteArray, account_email: String?): ByteArray =
    hmac_sha256(token_key, "account\n" + normalize_sender_email(account_email.orEmpty()))

internal fun unsubscribed_account_key(token_key: ByteArray, account_email: String?): String =
    to_hex(hmac_sha256(unsubscribed_account_secret(token_key, account_email), "slot"))

internal fun unsubscribed_sender_token(token_key: ByteArray, account_email: String?, sender_email: String): String =
    to_hex(
        hmac_sha256(
            unsubscribed_account_secret(token_key, account_email),
            "sender\n" + normalize_sender_email(sender_email),
        ),
    )

internal fun legacy_unsubscribed_pref_keys(pref_keys: Collection<String>): Set<String> =
    pref_keys.filterTo(HashSet()) {
        it.startsWith(LEGACY_CONFIRMED_PREFIX) || it.startsWith(LEGACY_PENDING_PREFIX)
    }

private fun load_or_create_unsubscribed_token_key(context: Context): ByteArray {
    val fresh = ByteArray(UNSUBSCRIBED_TOKEN_KEY_BYTES).also { java.security.SecureRandom().nextBytes(it) }
    val key_prefs = runCatching {
        org.astermail.android.storage.SecurePrefs.open(context, KEY_PREFS_NAME)
    }.getOrNull() ?: return fresh
    val stored = runCatching {
        key_prefs.getString(KEY_TOKEN_KEY, null)?.let { java.util.Base64.getDecoder().decode(it) }
    }.getOrNull()
    if (stored != null && stored.size == UNSUBSCRIBED_TOKEN_KEY_BYTES) return stored
    runCatching {
        key_prefs.edit()
            .putString(KEY_TOKEN_KEY, java.util.Base64.getEncoder().encodeToString(fresh))
            .commit()
    }
    return fresh
}

internal fun build_unsubscribe_track_request(
    sender_email: String,
    sender_name: String?,
    unsubscribe_link: String?,
    list_unsubscribe_header: String?,
): TrackSubscriptionRequest? {
    val sender = normalize_sender_email(sender_email)
    if (sender.isEmpty() || !sender.contains('@')) return null
    val link = unsubscribe_link?.trim()?.takeIf {
        it.length <= MAX_UNSUBSCRIBE_FIELD_LENGTH &&
            (it.startsWith("http://", ignoreCase = true) || it.startsWith("https://", ignoreCase = true))
    }
    return TrackSubscriptionRequest(
        sender_email = sender,
        sender_name = sender_name?.trim()?.take(MAX_SENDER_NAME_LENGTH)?.ifBlank { null },
        unsubscribe_link = link,
        list_unsubscribe_header = list_unsubscribe_header
            ?.takeIf { it.isNotBlank() && it.length <= MAX_UNSUBSCRIBE_FIELD_LENGTH },
    )
}

@Singleton
class UnsubscribedSendersStore @Inject constructor(
    private val api: SubscriptionsApi,
    private val repository: MailRepository,
    @ApplicationContext private val context: Context,
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val lock = Any()
    private val _tokens = MutableStateFlow<Set<String>>(emptySet())
    val tokens: StateFlow<Set<String>> = _tokens.asStateFlow()

    private var loaded_account_key: String? = null
    private var confirmed: Set<String> = emptySet()
    private var pending: Set<String> = emptySet()
    private var last_refresh_ms = 0L
    private var refresh_job: Job? = null

    private val token_key: ByteArray by lazy { load_or_create_unsubscribed_token_key(context) }
    private var legacy_purged = false

    private fun prefs(): android.content.SharedPreferences {
        val stored = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        synchronized(lock) {
            if (!legacy_purged) {
                legacy_purged = true
                val legacy = legacy_unsubscribed_pref_keys(stored.all.keys)
                if (legacy.isNotEmpty()) {
                    val editor = stored.edit()
                    legacy.forEach { editor.remove(it) }
                    editor.apply()
                }
            }
        }
        return stored
    }

    private fun account_key_for(account_email: String?): String =
        unsubscribed_account_key(token_key, account_email)

    private fun account_email(): String? = repository.get_user_email()

    private fun ensure_loaded(): String = synchronized(lock) {
        val account_key = account_key_for(account_email())
        if (loaded_account_key != account_key) {
            val stored = prefs()
            confirmed = stored.getStringSet(CONFIRMED_PREFIX + account_key, null).orEmpty().toSet()
            pending = stored.getStringSet(PENDING_PREFIX + account_key, null).orEmpty().toSet()
            loaded_account_key = account_key
            last_refresh_ms = 0L
            _tokens.value = confirmed + pending
        }
        account_key
    }

    private fun update(
        account_key: String,
        transform: (confirmed: Set<String>, pending: Set<String>) -> Pair<Set<String>, Set<String>>,
    ) = synchronized(lock) {
        if (ensure_loaded() != account_key) return@synchronized
        val (next_confirmed, next_pending) = transform(confirmed, pending)
        if (next_confirmed == confirmed && next_pending == pending) return@synchronized
        confirmed = next_confirmed
        pending = next_pending
        prefs().edit()
            .putStringSet(CONFIRMED_PREFIX + account_key, next_confirmed)
            .putStringSet(PENDING_PREFIX + account_key, next_pending)
            .apply()
        _tokens.value = next_confirmed + next_pending
    }

    fun token_for(sender_email: String): String =
        unsubscribed_sender_token(token_key, account_email(), sender_email)

    fun load_cached() {
        ensure_loaded()
    }

    fun refresh(force: Boolean = false) {
        val account_key = ensure_loaded()
        synchronized(lock) {
            if (refresh_job?.isActive == true) return
            val now = SystemClock.elapsedRealtime()
            if (!force && last_refresh_ms != 0L && now - last_refresh_ms < REFRESH_INTERVAL_MS) return
            refresh_job = scope.launch {
                val account = account_email()
                val server_tokens = try {
                    fetch_unsubscribed_tokens(account)
                } catch (e: CancellationException) {
                    throw e
                } catch (_: Throwable) {
                    null
                } ?: return@launch
                if (account_key_for(account_email()) != account_key) return@launch
                update(account_key) { _, current_pending ->
                    server_tokens to (current_pending - server_tokens)
                }
                synchronized(lock) {
                    if (loaded_account_key == account_key) {
                        last_refresh_ms = SystemClock.elapsedRealtime()
                    }
                }
            }
        }
    }

    private suspend fun fetch_unsubscribed_tokens(account: String?): Set<String> {
        val collected = HashSet<String>()
        var page = 0
        while (page < REFRESH_MAX_PAGES) {
            val response = api.list(
                limit = REFRESH_PAGE_SIZE,
                offset = page * REFRESH_PAGE_SIZE,
                status = "unsubscribed",
            )
            for (subscription in response.subscriptions) {
                if (subscription.status == "unsubscribed") {
                    collected += unsubscribed_sender_token(token_key, account, subscription.sender_email)
                }
            }
            page++
            if (!response.has_more || response.subscriptions.isEmpty()) break
        }
        return collected
    }

    fun replace_confirmed(unsubscribed_sender_emails: Collection<String>) {
        val account_key = ensure_loaded()
        val account = account_email()
        val server_tokens = unsubscribed_sender_emails
            .map { unsubscribed_sender_token(token_key, account, it) }
            .toSet()
        update(account_key) { _, current_pending ->
            server_tokens to (current_pending - server_tokens)
        }
    }

    fun mark_unsubscribed(sender_email: String) {
        val account_key = ensure_loaded()
        val token = token_for(sender_email)
        update(account_key) { current_confirmed, current_pending ->
            (current_confirmed + token) to (current_pending - token)
        }
    }

    fun mark_active(sender_email: String) {
        val account_key = ensure_loaded()
        val token = token_for(sender_email)
        update(account_key) { current_confirmed, current_pending ->
            (current_confirmed - token) to (current_pending - token)
        }
    }

    fun record_unsubscribed(
        sender_email: String,
        sender_name: String?,
        unsubscribe_link: String?,
        list_unsubscribe_header: String?,
    ) {
        val request = build_unsubscribe_track_request(
            sender_email,
            sender_name,
            unsubscribe_link,
            list_unsubscribe_header,
        ) ?: return
        val account_key = ensure_loaded()
        val token = token_for(request.sender_email)
        update(account_key) { current_confirmed, current_pending ->
            current_confirmed to (current_pending + token)
        }
        scope.launch {
            repeat(RECORD_ATTEMPTS) { attempt ->
                if (account_key_for(account_email()) != account_key) return@launch
                val recorded = try {
                    val tracked = api.track_subscription(request)
                    tracked.subscription_id.isNotBlank() &&
                        api.unsubscribe(
                            UnsubscribeRequest(tracked.subscription_id, method = "manual"),
                        ).success
                } catch (e: CancellationException) {
                    throw e
                } catch (_: Throwable) {
                    false
                }
                if (recorded) {
                    update(account_key) { current_confirmed, current_pending ->
                        if (token in current_pending) {
                            (current_confirmed + token) to (current_pending - token)
                        } else {
                            current_confirmed to current_pending
                        }
                    }
                    return@launch
                }
                delay(RECORD_RETRY_DELAY_MS * (attempt + 1))
            }
        }
    }
}
