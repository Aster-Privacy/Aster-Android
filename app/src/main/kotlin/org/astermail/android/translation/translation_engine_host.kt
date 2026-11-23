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

package org.astermail.android.translation

import android.content.Context
import android.os.Handler
import android.os.Looper
import org.json.JSONObject

internal const val TRANSLATION_ERROR_STATUS = "{\"state\":\"error\"}"

internal fun translation_request_id_of(json: String): String? = try {
    JSONObject(json).optString("id").ifEmpty { null }
} catch (_: Throwable) {
    null
}

internal fun translation_session_of(request_id: String): String? =
    request_id.substringBefore(':', "").ifEmpty { null }

internal class translation_session internal constructor(
    internal val id: String,
    private val on_detect: (String) -> Unit,
    private val on_status: (String) -> Unit,
    private val on_result: (String) -> Unit,
    private val host: TranslationEngineHost? = null,
) {
    private var sequence = 0
    internal var current_request: String? = null
        private set
    internal var closed = false
        private set

    internal fun next_request(): String {
        sequence += 1
        val request = "$id:$sequence"
        current_request = request
        return request
    }

    internal fun accepts(request_id: String?): Boolean =
        !closed && request_id != null && request_id == current_request

    internal fun deliver_detect(json: String) {
        if (!closed) on_detect(json)
    }

    internal fun deliver_status(json: String) {
        if (!closed) on_status(json)
    }

    internal fun deliver_result(json: String) {
        if (!closed) on_result(json)
    }

    fun detect(text: String, accepted: String) {
        if (closed) return
        host?.detect(this, text, accepted)
    }

    fun translate(segments: List<String>, from: String, to: String) {
        if (closed) return
        host?.translate(this, segments, from, to)
    }

    fun close() {
        if (closed) return
        closed = true
        current_request = null
        host?.release(this)
    }
}

internal interface translation_engine_port {
    fun detect(text: String, accepted: String, request_id: String)
    fun translate(segments: List<String>, from: String, to: String, request_id: String)
    fun is_destroyed(): Boolean
    fun destroy()
}

internal class translation_router {
    private val sessions = LinkedHashMap<String, translation_session>()

    fun add(session: translation_session) {
        sessions[session.id] = session
    }

    fun remove(session: translation_session) {
        sessions.remove(session.id)
    }

    fun is_empty(): Boolean = sessions.isEmpty()

    fun size(): Int = sessions.size

    fun route_detect(json: String) = route(json) { session -> session.deliver_detect(json) }

    fun route_result(json: String) = route(json) { session -> session.deliver_result(json) }

    fun route_status(json: String) {
        val request = translation_request_id_of(json)
        if (request == null) {
            if (json.contains("\"ready\"")) return
            broadcast_to_waiting(json)
            return
        }
        route(json) { session -> session.deliver_status(json) }
    }

    fun broadcast_to_waiting(json: String) {
        sessions.values.toList().forEach { session ->
            if (session.current_request != null) session.deliver_status(json)
        }
    }

    private fun route(json: String, deliver: (translation_session) -> Unit) {
        val request = translation_request_id_of(json) ?: return
        val session = sessions[translation_session_of(request) ?: return] ?: return
        if (session.accepts(request)) deliver(session)
    }
}

internal class TranslationEngineHost(
    private val factory: (Context, translation_router, () -> Unit) -> translation_engine_port?,
    private val schedule: (Runnable, Long) -> Unit,
    private val cancel: (Runnable) -> Unit,
) {
    private val router = translation_router()
    private var engine: translation_engine_port? = null
    private var app_context: Context? = null
    private var session_counter = 0L

    private val idle_release = Runnable {
        if (router.is_empty()) shut_down()
    }

    fun open(
        context: Context,
        on_detect: (String) -> Unit,
        on_status: (String) -> Unit,
        on_result: (String) -> Unit,
    ): translation_session {
        app_context = context.applicationContext ?: context
        cancel(idle_release)
        session_counter += 1
        val session = translation_session("s$session_counter", on_detect, on_status, on_result, this)
        router.add(session)
        return session
    }

    internal fun release(session: translation_session) {
        router.remove(session)
        if (router.is_empty()) {
            cancel(idle_release)
            schedule(idle_release, IDLE_RELEASE_MS)
        }
    }

    internal fun detect(session: translation_session, text: String, accepted: String) {
        val engine = engine_or_create() ?: return
        engine.detect(text, accepted, session.next_request())
    }

    internal fun translate(session: translation_session, segments: List<String>, from: String, to: String) {
        val engine = engine_or_create()
        if (engine == null) {
            session.deliver_status(TRANSLATION_ERROR_STATUS)
            return
        }
        engine.translate(segments, from, to, session.next_request())
    }

    internal fun session_count(): Int = router.size()

    internal fun engine_running(): Boolean = engine?.is_destroyed() == false

    private fun engine_or_create(): translation_engine_port? {
        engine?.let { if (!it.is_destroyed()) return it }
        val context = app_context ?: return null
        engine = runCatching { factory(context, router) { engine_gone() } }.getOrNull()
        return engine
    }

    private fun engine_gone() {
        engine = null
        router.broadcast_to_waiting(TRANSLATION_ERROR_STATUS)
    }

    fun shut_down() {
        cancel(idle_release)
        engine?.destroy()
        engine = null
    }

    companion object {
        private const val IDLE_RELEASE_MS = 30_000L
        private val main_handler by lazy { Handler(Looper.getMainLooper()) }

        val shared: TranslationEngineHost by lazy {
            TranslationEngineHost(
                factory = { context, router, on_gone ->
                    translation_engine(
                        context,
                        on_detect = router::route_detect,
                        on_status = router::route_status,
                        on_result = router::route_result,
                        on_gone = on_gone,
                    )
                },
                schedule = { runnable, delay -> main_handler.postDelayed(runnable, delay) },
                cancel = { runnable -> main_handler.removeCallbacks(runnable) },
            )
        }
    }
}
