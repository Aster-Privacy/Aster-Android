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
import android.content.SharedPreferences
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import java.security.MessageDigest
import java.util.Base64
import javax.inject.Inject
import javax.inject.Singleton
import java.util.Locale
import org.astermail.android.api.keys.ExternalKeyInfo
import org.astermail.android.crypto.ExternalPgpKeyCodec
import org.astermail.android.crypto.OpportunisticKeyTrust
import org.astermail.android.crypto.pgp_key_fingerprint
import org.astermail.android.crypto.pgp_key_user_emails

sealed interface ExternalKeyVetting {
    data class Accepted(val fingerprint: String, val first_use: Boolean) : ExternalKeyVetting
    data class Changed(val prior_fingerprint: String, val new_fingerprint: String) : ExternalKeyVetting
    data class Unverified(val fingerprint: String) : ExternalKeyVetting
    object Rejected : ExternalKeyVetting
}

enum class ExternalKeySendStep { USE_AND_PIN, USE, SKIP_OR_REQUIRE, STOP_CHANGED, STOP_UNREADABLE }

enum class ExternalKeyUnreadableStep { USE, SEND_WITHOUT_COPY, RETRY }

data class ExternalKeyPinRead(val readable: Boolean, val fingerprint: String?)

interface ExternalKeyPinSource {
    fun read(account_id: String, email: String): ExternalKeyPinRead
    fun pinned(account_id: String, email: String): String? = read(account_id, email).fingerprint
    fun pin(account_id: String, email: String, fingerprint: String): Boolean
}

object ExternalKeyPinRule {

    fun normalize_email(email: String): String = email.trim().lowercase(Locale.ROOT)

    private fun clean(fingerprint: String?): String? =
        fingerprint?.let(ExternalPgpKeyCodec::normalize_fingerprint)?.takeIf { it.isNotEmpty() }

    fun names_address(email: String, user_emails: List<String>): Boolean {
        val wanted = normalize_email(email)
        return wanted.isNotEmpty() && user_emails.any { normalize_email(it) == wanted }
    }

    fun vet(
        email: String,
        fingerprint: String?,
        user_emails: List<String>,
        pinned: String?,
        pin_readable: Boolean = true,
        explicit: Boolean = false,
    ): ExternalKeyVetting {
        val current = clean(fingerprint) ?: return ExternalKeyVetting.Rejected
        if (!names_address(email, user_emails)) return ExternalKeyVetting.Rejected
        if (!pin_readable) return ExternalKeyVetting.Unverified(current)
        val prior = clean(pinned)
            ?: return if (explicit) ExternalKeyVetting.Accepted(current, first_use = true) else ExternalKeyVetting.Rejected
        if (prior == current) return ExternalKeyVetting.Accepted(current, first_use = false)
        return ExternalKeyVetting.Changed(prior, current)
    }

    fun vet_armored(
        email: String,
        armored_key: String,
        pinned: String?,
        pin_readable: Boolean = true,
        explicit: Boolean = false,
    ): ExternalKeyVetting =
        vet(email, pgp_key_fingerprint(armored_key), pgp_key_user_emails(armored_key), pinned, pin_readable, explicit)

    fun opportunistic_trust(email: String, armored_key: String, pinned: String?): OpportunisticKeyTrust {
        val prior = clean(pinned) ?: return OpportunisticKeyTrust.UNPINNED
        val current = clean(pgp_key_fingerprint(armored_key)) ?: return OpportunisticKeyTrust.UNPINNED
        if (prior != current) return OpportunisticKeyTrust.CHANGED
        if (!names_address(email, pgp_key_user_emails(armored_key))) return OpportunisticKeyTrust.UNPINNED
        return OpportunisticKeyTrust.PINNED
    }

    fun send_step(vetting: ExternalKeyVetting): ExternalKeySendStep = when (vetting) {
        is ExternalKeyVetting.Accepted ->
            if (vetting.first_use) ExternalKeySendStep.USE_AND_PIN else ExternalKeySendStep.USE
        is ExternalKeyVetting.Changed -> ExternalKeySendStep.STOP_CHANGED
        is ExternalKeyVetting.Unverified -> ExternalKeySendStep.STOP_UNREADABLE
        ExternalKeyVetting.Rejected -> ExternalKeySendStep.SKIP_OR_REQUIRE
    }

    fun local_changes(
        keys: List<ExternalKeyInfo>,
        read: (String) -> ExternalKeyPinRead,
    ): List<RecipientKeyChange> =
        keys.mapNotNull { info ->
            val armored = info.public_key?.takeIf { info.found && it.isNotBlank() } ?: return@mapNotNull null
            val email = normalize_email(info.email)
            val pin = read(email)
            when (val vetting = vet_armored(email, armored, pin.fingerprint, pin.readable, explicit = true)) {
                is ExternalKeyVetting.Changed -> RecipientKeyChange(email, vetting.prior_fingerprint, vetting.new_fingerprint)
                is ExternalKeyVetting.Unverified -> RecipientKeyChange(email, UNREADABLE_PRIOR, vetting.fingerprint)
                else -> null
            }
        }

    const val UNREADABLE_PRIOR = ""

    fun unreadable_step(explicit: Boolean, acknowledged: () -> Boolean): ExternalKeyUnreadableStep = when {
        !explicit -> ExternalKeyUnreadableStep.SEND_WITHOUT_COPY
        acknowledged() -> ExternalKeyUnreadableStep.USE
        else -> ExternalKeyUnreadableStep.RETRY
    }

    fun changes_to_confirm(
        changes: List<RecipientKeyChange>,
        explicit_for: (String) -> Boolean,
        acknowledged: (RecipientKeyChange) -> Boolean,
    ): List<RecipientKeyChange> =
        changes.filter { change ->
            change.prior_fingerprint != UNREADABLE_PRIOR || (explicit_for(change.email) && !acknowledged(change))
        }

    fun merge_changes(
        server: List<RecipientKeyChange>,
        local: List<RecipientKeyChange>,
    ): List<RecipientKeyChange> {
        val local_emails = local.map { it.email }.toSet()
        return local + server.filter { it.email !in local_emails }
    }

    fun entry_key(account_id: String, email: String): String =
        "${digest(account_id)}:${digest(normalize_email(email))}"

    private fun digest(value: String): String =
        MessageDigest.getInstance("SHA-256")
            .digest(value.toByteArray(Charsets.UTF_8))
            .joinToString("") { "%02x".format(it) }
}

class ExternalKeyPinStore(private val prefs: () -> SharedPreferences?) : ExternalKeyPinSource {

    override fun read(account_id: String, email: String): ExternalKeyPinRead =
        runCatching {
            val store = prefs() ?: return@runCatching ExternalKeyPinRead(readable = false, fingerprint = null)
            ExternalKeyPinRead(readable = true, fingerprint = store.getString(ExternalKeyPinRule.entry_key(account_id, email), null))
        }.getOrDefault(ExternalKeyPinRead(readable = false, fingerprint = null))

    override fun pin(account_id: String, email: String, fingerprint: String): Boolean {
        val value = ExternalPgpKeyCodec.normalize_fingerprint(fingerprint).takeIf { it.isNotEmpty() } ?: return false
        return runCatching {
            prefs()?.edit()?.putString(ExternalKeyPinRule.entry_key(account_id, email), value)?.commit() ?: false
        }.getOrDefault(false)
    }

    companion object {
        const val PREFS_NAME = "aster_external_key_pins_v1"
    }
}

@Singleton
class SecureExternalKeyPins @Inject constructor(
    @ApplicationContext private val context: Context,
) : ExternalKeyPinSource by ExternalKeyPinStore({
    runCatching { org.astermail.android.storage.SecurePrefs.open(context, ExternalKeyPinStore.PREFS_NAME) }.getOrNull()
})

@Module
@InstallIn(SingletonComponent::class)
abstract class ExternalKeyPinModule {
    @Binds
    abstract fun bind_external_key_pins(source: SecureExternalKeyPins): ExternalKeyPinSource
}

class ExternalKeyAcknowledgments(private val prefs: () -> SharedPreferences? = { null }) {
    private val scopes = LinkedHashMap<String, LinkedHashMap<String, String>>()
    private var loaded = false

    private fun <T> locked(block: (LinkedHashMap<String, LinkedHashMap<String, String>>) -> T): T =
        synchronized(scopes) {
            if (!loaded) {
                restore()
                loaded = true
            }
            block(scopes)
        }

    private fun restore() {
        val stored = runCatching { prefs()?.getString(KEY_ENTRIES, null) }.getOrNull() ?: return
        parse(stored).forEach { (scope, entries) -> scopes[scope] = entries }
    }

    private fun persist() {
        runCatching { prefs()?.edit()?.putString(KEY_ENTRIES, serialize(scopes))?.commit() }
    }

    fun note(account_id: String, scope: String?, email: String, fingerprint: String): Boolean {
        val send_scope = scope?.takeIf { it.isNotBlank() } ?: return false
        val value = ExternalPgpKeyCodec.normalize_fingerprint(fingerprint).takeIf { it.isNotEmpty() } ?: return false
        return locked { all ->
            val entries = all.remove(send_scope) ?: LinkedHashMap()
            entries[ExternalKeyPinRule.entry_key(account_id, email)] = value
            all[send_scope] = entries
            while (all.size > MAX_SCOPES) all.remove(all.keys.first())
            persist()
            true
        }
    }

    fun matches(account_id: String, scope: String?, email: String, fingerprint: String): Boolean {
        val send_scope = scope?.takeIf { it.isNotBlank() } ?: return false
        val value = ExternalPgpKeyCodec.normalize_fingerprint(fingerprint).takeIf { it.isNotEmpty() } ?: return false
        return locked { all -> all[send_scope]?.get(ExternalKeyPinRule.entry_key(account_id, email)) == value }
    }

    fun consume(account_id: String, scope: String?, email: String, fingerprint: String): Boolean {
        val send_scope = scope?.takeIf { it.isNotBlank() } ?: return false
        return locked { all ->
            val entries = all[send_scope] ?: return@locked false
            val key = ExternalKeyPinRule.entry_key(account_id, email)
            val removed = entries[key] == ExternalPgpKeyCodec.normalize_fingerprint(fingerprint)
            if (removed) entries.remove(key)
            if (entries.isEmpty()) all.remove(send_scope)
            if (removed) persist()
            removed
        }
    }

    fun drop(scope: String?) {
        val send_scope = scope?.takeIf { it.isNotBlank() } ?: return
        locked { all -> if (all.remove(send_scope) != null) persist() }
    }

    fun has_scope(scope: String?): Boolean {
        val send_scope = scope?.takeIf { it.isNotBlank() } ?: return false
        return locked { all -> all.containsKey(send_scope) }
    }

    fun move(from_scope: String?, to_scope: String?) {
        val from = from_scope?.takeIf { it.isNotBlank() } ?: return
        val to = to_scope?.takeIf { it.isNotBlank() } ?: return
        if (from == to) return
        locked { all ->
            val moved = all.remove(from) ?: return@locked
            val target = all.remove(to) ?: LinkedHashMap()
            target.putAll(moved)
            all[to] = target
            persist()
        }
    }

    companion object {
        const val PREFS_NAME = "aster_external_key_acks_v1"
        const val MAX_SCOPES = 64
        private const val KEY_ENTRIES = "entries"

        private fun encode(value: String): String =
            Base64.getUrlEncoder().withoutPadding().encodeToString(value.toByteArray(Charsets.UTF_8))

        private fun decode(value: String): String? =
            runCatching { String(Base64.getUrlDecoder().decode(value), Charsets.UTF_8) }.getOrNull()

        fun serialize(all: Map<String, Map<String, String>>): String =
            all.flatMap { (scope, entries) ->
                entries.map { (key, fingerprint) -> "${encode(scope)}.${encode(key)}.$fingerprint" }
            }.joinToString("\n")

        fun parse(stored: String): LinkedHashMap<String, LinkedHashMap<String, String>> {
            val parsed = LinkedHashMap<String, LinkedHashMap<String, String>>()
            stored.split("\n").forEach { line ->
                val parts = line.split(".")
                if (parts.size != 3 || parts[2].isEmpty()) return@forEach
                val scope = decode(parts[0]) ?: return@forEach
                val key = decode(parts[1]) ?: return@forEach
                parsed.getOrPut(scope) { LinkedHashMap() }[key] = parts[2]
            }
            return parsed
        }
    }
}

data class ExternalKeyAcknowledgmentUse(val email: String, val fingerprint: String)
