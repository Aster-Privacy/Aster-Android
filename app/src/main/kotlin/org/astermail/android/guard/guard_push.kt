//
// Aster Communications Inc.
//
// Copyright (c) 2026 Aster Communications Inc.
//
// This file is part of this project.
//
// This program is free software: you can redistribute it and/or modify
// it under the terms of the AGPLv3 as published by
// the Free Software Foundation, either version 3 of the License, or
// (at your option) any later version.
//
// This program is distributed in the hope that it will be useful,
// but WITHOUT ANY WARRANTY; without even the implied warranty of
// MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
// AGPLv3 for more details.
//
// You should have received a copy of the AGPLv3
// along with this program. If not, see <https://www.gnu.org/licenses/>.
//

package org.astermail.android.guard

import java.util.UUID
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import org.astermail.android.crypto.DeviceEnvelope
import org.astermail.android.crypto.guard.GuardContextSeal
import org.astermail.android.notifications.PushResult
import org.json.JSONObject

const val GUARD_PUSH_TYPE = "guard"
private const val MAX_PUSH_FIELD = 64
const val GUARD_PROMPT_DEFAULT_TIMEOUT_MS = 5 * 60_000L
const val GUARD_PROMPT_MIN_TIMEOUT_MS = 30_000L

enum class guard_event {
    sign_in_request,
    change_held,
    change_applied,
    change_cancelled,
    panic,
    rescue_started,
    rescue_vetoed,
    guard_enabled,
    prompts_paused,
    sign_in_approved,
    sign_in_denied,
    sign_in_expired,
    device_added,
    device_removed,
    rescue_completed,
    guard_disabled,
}

data class guard_push(val event: guard_event, val id: String, val account_id: String)

fun guard_uuid_field(raw: String?): String? {
    if (raw.isNullOrEmpty() || raw.length > MAX_PUSH_FIELD) return null
    if (!raw.all { it in '0'..'9' || it in 'a'..'f' || it in 'A'..'F' || it == '-' }) return null
    val parsed = runCatching { UUID.fromString(raw) }.getOrNull() ?: return null
    return parsed.toString().takeIf { it.equals(raw, ignoreCase = true) }
}

private fun push_field(obj: JSONObject, name: String): String? = guard_uuid_field(obj.optString(name, ""))

fun parse_guard_push(payload: JSONObject): guard_push? {
    if (payload.optString("type") != GUARD_PUSH_TYPE) return null
    val raw_event = payload.optString("event", "")
    val event = guard_event.entries.firstOrNull { it.name == raw_event } ?: return null
    val account_id = push_field(payload, "account_id") ?: return null
    val id = push_field(payload, "id")
    if (id == null && event.needs_id()) return null
    return guard_push(event, id.orEmpty(), account_id)
}

fun guard_event.needs_id(): Boolean = when (this) {
    guard_event.sign_in_request,
    guard_event.change_held,
    guard_event.change_applied,
    guard_event.change_cancelled,
    guard_event.rescue_started,
    guard_event.rescue_vetoed,
    guard_event.sign_in_approved,
    guard_event.sign_in_denied,
    guard_event.sign_in_expired,
    guard_event.device_added,
    guard_event.device_removed,
    guard_event.rescue_completed,
    guard_event.guard_disabled,
    -> true
    guard_event.panic, guard_event.guard_enabled, guard_event.prompts_paused -> false
}

fun guard_dedupe_key(push: guard_push): String = "guard:${push.event.name}:${push.id}"

interface guard_alert_marks {
    fun recent(account_id: String, event: guard_event): Boolean
    fun note(account_id: String, event: guard_event)

    object none : guard_alert_marks {
        override fun recent(account_id: String, event: guard_event): Boolean = false
        override fun note(account_id: String, event: guard_event) = Unit
    }
}

object guard_live_events {
    private val flow = MutableSharedFlow<guard_push>(extraBufferCapacity = 16)
    val events: SharedFlow<guard_push> = flow.asSharedFlow()

    fun emit(push: guard_push) {
        flow.tryEmit(push)
    }
}

fun handle_guard_push(
    payload: JSONObject,
    known_accounts: () -> Collection<String>,
    claim: (String) -> Boolean,
    enqueue: (guard_push) -> Unit,
    marks: guard_alert_marks = guard_alert_marks.none,
): PushResult {
    val push = parse_guard_push(payload) ?: return PushResult.Ignore
    val known = runCatching { known_accounts() }.getOrDefault(emptyList())
    if (known.none { it.equals(push.account_id, ignoreCase = true) }) return PushResult.Ignore
    if (!claim(guard_dedupe_key(push))) return PushResult.Ignore
    val spec = guard_notification_spec_for(push.event)
    guard_cancelled_push(push)?.let { claim(guard_dedupe_key(it)) }
    if (spec.shows) runCatching { marks.note(push.account_id, push.event) }
    guard_live_events.emit(push)
    enqueue(push)
    return if (spec.shows) PushResult.Shown else PushResult.Ignore
}

enum class guard_open_target { request, changes, guard_settings, app }

data class guard_notification_spec(
    val target: guard_open_target,
    val review_action: Boolean,
    val private_details: Boolean,
    val panic_action: Boolean = false,
    val cancels: guard_event? = null,
    val shows: Boolean = true,
) {
    val cancels_prompt: Boolean get() = cancels == guard_event.sign_in_request
}

fun guard_prompt_push(push: guard_push): guard_push = guard_push(guard_event.sign_in_request, push.id, push.account_id)

fun guard_cancelled_push(push: guard_push): guard_push? =
    guard_notification_spec_for(push.event).cancels?.let { guard_push(it, push.id, push.account_id) }

object guard_local_holds {
    private const val MAX_ENTRIES = 32
    private val held = LinkedHashSet<String>()

    @Synchronized
    fun record(change_id: String) {
        val k = change_id.lowercase()
        if (k.isBlank()) return
        held.remove(k)
        held.add(k)
        while (held.size > MAX_ENTRIES) held.remove(held.first())
    }

    @Synchronized
    fun made_here(change_id: String): Boolean = change_id.lowercase() in held

    @Synchronized
    fun clear() = held.clear()
}

object guard_local_approvals {
    private const val MAX_ENTRIES = 32
    private val approved = LinkedHashSet<String>()

    private fun key(request_id: String, account_id: String) = "${account_id.lowercase()}:$request_id"

    @Synchronized
    fun record(request_id: String, account_id: String) {
        val k = key(request_id, account_id)
        approved.remove(k)
        approved.add(k)
        while (approved.size > MAX_ENTRIES) approved.remove(approved.first())
    }

    @Synchronized
    fun forget(request_id: String, account_id: String) {
        approved.remove(key(request_id, account_id))
    }

    @Synchronized
    fun approved_here(request_id: String, account_id: String): Boolean = key(request_id, account_id) in approved

    @Synchronized
    fun clear_account(account_id: String) {
        val prefix = "${account_id.lowercase()}:"
        approved.removeAll { it.startsWith(prefix) }
    }

    @Synchronized
    fun clear() = approved.clear()
}

enum class guard_prompt_lookup { found, absent, unknown }

fun guard_prompt_can_look_up(push_account: String, current_account: String?): Boolean =
    current_account != null && current_account.equals(push_account, ignoreCase = true)

fun guard_prompt_should_post(lookup: guard_prompt_lookup, timeout_ms: Long): Boolean =
    lookup != guard_prompt_lookup.absent && timeout_ms > 0L

fun guard_prompt_timeout_ms(expires_at: String?, now_ms: Long): Long {
    val expires = expires_at?.takeIf { it.isNotBlank() }
        ?.let { runCatching { java.time.Instant.parse(it).toEpochMilli() }.getOrNull() }
        ?: return GUARD_PROMPT_DEFAULT_TIMEOUT_MS
    val left = expires - now_ms
    if (left <= 0L) return 0L
    return left.coerceIn(GUARD_PROMPT_MIN_TIMEOUT_MS, GUARD_PROMPT_DEFAULT_TIMEOUT_MS)
}

fun guard_is_own_device_event(push: guard_push, own_device_id: UUID?): Boolean {
    if (own_device_id == null) return false
    if (push.event != guard_event.device_added) return false
    return push.id.equals(own_device_id.toString(), ignoreCase = true)
}

fun guard_should_show(
    push: guard_push,
    held_here: (String) -> Boolean = guard_local_holds::made_here,
    approved_here: (String, String) -> Boolean,
): Boolean {
    if (!guard_notification_spec_for(push.event).shows) return false
    if (push.event == guard_event.sign_in_approved && approved_here(push.id, push.account_id)) return false
    if (push.event == guard_event.change_held && held_here(push.id)) return false
    return true
}

fun guard_notification_spec_for(event: guard_event): guard_notification_spec = when (event) {
    guard_event.sign_in_request -> guard_notification_spec(guard_open_target.request, review_action = true, private_details = true)
    guard_event.rescue_started -> guard_notification_spec(guard_open_target.guard_settings, review_action = true, private_details = false)
    guard_event.change_held ->
        guard_notification_spec(guard_open_target.changes, review_action = false, private_details = false, panic_action = true)
    guard_event.change_applied, guard_event.change_cancelled ->
        guard_notification_spec(
            guard_open_target.changes,
            review_action = false,
            private_details = false,
            cancels = guard_event.change_held,
            shows = false,
        )
    guard_event.sign_in_approved, guard_event.sign_in_denied, guard_event.sign_in_expired ->
        guard_notification_spec(
            guard_open_target.guard_settings,
            review_action = false,
            private_details = false,
            cancels = guard_event.sign_in_request,
            shows = false,
        )
    guard_event.panic, guard_event.rescue_vetoed, guard_event.guard_enabled, guard_event.prompts_paused,
    guard_event.device_added, guard_event.device_removed, guard_event.rescue_completed, guard_event.guard_disabled ->
        guard_notification_spec(guard_open_target.guard_settings, review_action = false, private_details = false, shows = false)
}

@Serializable
data class guard_context_view(
    val device_type: String = "",
    val browser: String? = null,
    val os: String? = null,
    val city: String? = null,
    val country: String? = null,
    val country_code: String? = null,
    val requested_at: String = "",
    val device_name: String? = null,
) {
    fun region_code(): String? = listOfNotNull(country_code, country).map { it.trim() }.firstOrNull { it.length == 2 }

    fun device_summary(on_os: (String, String) -> String = { b, o -> "$o · $b" }): String {
        val b = guard_known_label(browser)
        val o = guard_known_label(os)
        return when {
            b != null && o != null -> on_os(b, o)
            else -> b ?: o ?: guard_known_label(device_type).orEmpty()
        }
    }

    fun sent_name(summary: String): String? =
        guard_known_label(device_name)?.takeIf { !it.equals(summary.trim(), ignoreCase = true) }

    fun has_place(): Boolean = listOfNotNull(city, country, country_code).any { it.isNotBlank() }

    fun place(): String =
        listOfNotNull(city, country).map { it.trim() }.filter { it.isNotEmpty() }.joinToString(", ")
}

private val guard_unknown_labels = setOf("unknown", "unknown device", "unknown browser", "unknown os")

fun guard_known_label(value: String?): String? =
    value?.trim()?.takeIf { it.isNotEmpty() && it.lowercase() !in guard_unknown_labels }

private val context_json = Json { ignoreUnknownKeys = true }

fun open_guard_context(
    sealed_b64: String,
    request_id: UUID,
    device_id: UUID,
    keys: guard_box_keys,
): guard_context_view? = runCatching {
    if (sealed_b64.isBlank()) return null
    val plain = GuardContextSeal.open(
        DeviceEnvelope.base64url_decode(sealed_b64),
        GuardContextSeal.context_aad(request_id, device_id),
        keys.x25519_sk,
        keys.mlkem_sk,
    )
    try {
        context_json.decodeFromString(guard_context_view.serializer(), plain.decodeToString())
    } finally {
        plain.fill(0)
    }
}.getOrNull()

sealed interface guard_launch_target {
    val account_id: String?

    data class request(val id: String, override val account_id: String?) : guard_launch_target
    data class changes(override val account_id: String?) : guard_launch_target
    data class settings(override val account_id: String?) : guard_launch_target
    data class panic(override val account_id: String?) : guard_launch_target
}

fun guard_launch_target_for(
    request_id: String?,
    changes: Boolean,
    settings: Boolean,
    account_id: String?,
    panic: Boolean = false,
): guard_launch_target? {
    val account = guard_uuid_field(account_id)
    val request = guard_uuid_field(request_id)
    return when {
        request != null -> guard_launch_target.request(request, account)
        panic -> guard_launch_target.panic(account)
        changes -> guard_launch_target.changes(account)
        settings -> guard_launch_target.settings(account)
        else -> null
    }
}

enum class guard_launch_route { current, switch, ignore }

fun guard_launch_route_for(
    target_account: String?,
    current_account: String?,
    has_session: (String) -> Boolean,
): guard_launch_route {
    val target = target_account ?: return guard_launch_route.ignore
    if (current_account != null && current_account.equals(target, ignoreCase = true)) return guard_launch_route.current
    return if (runCatching { has_session(target) }.getOrDefault(false)) guard_launch_route.switch else guard_launch_route.ignore
}
