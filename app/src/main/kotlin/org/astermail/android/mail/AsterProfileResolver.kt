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

import org.astermail.android.BuildConfig
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import org.astermail.android.api.auth.AuthApi
import org.astermail.android.api.auth.PublicProfile

private val ASTER_DOMAINS = setOf("astermail.org", "aster.cx")

private const val PROFILE_RESOLVE_TTL_MS = 30L * 60L * 1000L

fun is_aster_domain(domain: String): Boolean = ASTER_DOMAINS.contains(domain.lowercase())

object AsterProfileResolverHolder {
    @Volatile var shared: AsterProfileResolver? = null
}

@Singleton
class AsterProfileResolver @Inject constructor(
    private val auth_api_provider: dagger.Lazy<AuthApi>,
) {
    private val auth_api: AuthApi
        get() = auth_api_provider.get()

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val _profiles = MutableStateFlow<Map<String, PublicProfile?>>(emptyMap())
    val profiles: StateFlow<Map<String, PublicProfile?>> = _profiles.asStateFlow()

    private val pending = mutableSetOf<String>()
    private val resolved_at = java.util.concurrent.ConcurrentHashMap<String, Long>()
    private val mutex = Mutex()
    private var flush_job: Job? = null

    private fun recently_resolved(email: String): Boolean {
        val at = resolved_at[email] ?: return false
        if (System.currentTimeMillis() - at < PROFILE_RESOLVE_TTL_MS) return true
        resolved_at.remove(email)
        return false
    }

    fun prime(email: String, display_name: String?, profile_picture: String?, profile_color: String? = null) {
        val lower = email.trim().lowercase()
        if (lower.isEmpty() || !lower.contains('@')) return
        val domain = lower.substringAfter('@', "")
        if (!is_aster_domain(domain)) return
        val has_any = !display_name.isNullOrBlank() || !profile_picture.isNullOrBlank() || !profile_color.isNullOrBlank()
        if (!has_any) return
        val current = _profiles.value.toMutableMap()
        val existing = current[lower]
        val merged = PublicProfile(
            display_name = display_name?.takeIf { it.isNotBlank() } ?: existing?.display_name,
            profile_picture = profile_picture?.takeIf { it.isNotBlank() } ?: existing?.profile_picture,
            profile_color = profile_color?.takeIf { it.isNotBlank() } ?: existing?.profile_color,
        )
        current[lower] = merged
        _profiles.value = current
    }

    fun request(email: String) {
        val lower = email.trim().lowercase()
        if (lower.isEmpty() || !lower.contains('@')) return
        val domain = lower.substringAfter('@', "")
        if (!is_aster_domain(domain)) return
        if (_profiles.value[lower]?.profile_picture?.isNotBlank() == true) return
        if (recently_resolved(lower)) return
        scope.launch {
            mutex.withLock {
                if (_profiles.value[lower]?.profile_picture?.isNotBlank() == true) return@withLock
                if (recently_resolved(lower)) return@withLock
                if (pending.contains(lower)) return@withLock
                pending.add(lower)
                if (flush_job == null) {
                    flush_job = scope.launch {
                        delay(150)
                        flush()
                    }
                }
            }
        }
    }

    fun request_all(emails: Collection<String>) {
        for (email in emails.toSet()) request(email)
    }

    fun clear() {
        scope.launch {
            mutex.withLock {
                pending.clear()
                resolved_at.clear()
                flush_job?.cancel()
                flush_job = null
                _profiles.value = emptyMap()
            }
        }
    }

    private fun merge_profiles(existing: PublicProfile?, fetched: PublicProfile?): PublicProfile? {
        if (existing == null) return fetched
        if (fetched == null) return existing
        return PublicProfile(
            display_name = fetched.display_name?.takeIf { it.isNotBlank() } ?: existing.display_name,
            profile_picture = fetched.profile_picture?.takeIf { it.isNotBlank() } ?: existing.profile_picture,
            profile_color = fetched.profile_color?.takeIf { it.isNotBlank() } ?: existing.profile_color,
        )
    }

    private suspend fun flush() {
        val to_query: List<String>
        mutex.withLock {
            to_query = pending.toList()
            pending.clear()
            flush_job = null
        }
        if (to_query.isEmpty()) return
        for (chunk in to_query.chunked(50)) {
            try {
                val resp = auth_api.batch_profiles(chunk)
                val current = _profiles.value.toMutableMap()
                val now = System.currentTimeMillis()
                for (email in chunk) {
                    current[email] = merge_profiles(current[email], resp.profiles[email])
                    resolved_at[email] = now
                }
                _profiles.value = current
            } catch (t: Throwable) {
                if (BuildConfig.DEBUG) android.util.Log.w("AsterProfileResolver", "batch_profiles failed", t)
                val current = _profiles.value.toMutableMap()
                for (email in chunk) {
                    if (!current.containsKey(email)) current[email] = null
                }
                _profiles.value = current
            }
        }
    }
}
