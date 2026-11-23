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
import io.mockk.every
import io.mockk.mockk
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class TranslationEngineHostTest {

    private class fake_engine : translation_engine_port {
        val detects = mutableListOf<String>()
        val translations = mutableListOf<Pair<List<String>, String>>()
        var destroyed = false

        override fun detect(text: String, accepted: String, request_id: String) {
            detects.add(request_id)
        }

        override fun translate(segments: List<String>, from: String, to: String, request_id: String) {
            translations.add(segments to request_id)
        }

        override fun is_destroyed(): Boolean = destroyed

        override fun destroy() {
            destroyed = true
        }
    }

    private class harness {
        val engines = mutableListOf<fake_engine>()
        var router: translation_router? = null
        var gone: (() -> Unit)? = null
        val scheduled = mutableListOf<Runnable>()
        val context: Context = mockk<Context>().also { every { it.applicationContext } returns it }
        val host = TranslationEngineHost(
            factory = { _, router, on_gone ->
                this.router = router
                this.gone = on_gone
                fake_engine().also { engines.add(it) }
            },
            schedule = { runnable, _ -> scheduled.add(runnable) },
            cancel = { runnable -> scheduled.remove(runnable) },
        )
    }

    private class recorder {
        val detects = mutableListOf<String>()
        val statuses = mutableListOf<String>()
        val results = mutableListOf<String>()
    }

    private fun open(h: harness, r: recorder) = h.host.open(
        h.context,
        on_detect = { r.detects.add(it) },
        on_status = { r.statuses.add(it) },
        on_result = { r.results.add(it) },
    )

    @Test
    fun one_engine_serves_every_open_message() {
        val h = harness()
        val first = open(h, recorder())
        val second = open(h, recorder())

        first.detect("a", "en")
        second.detect("b", "en")

        assertEquals(1, h.engines.size)
        assertEquals(2, h.engines[0].detects.size)
        assertNotEquals(h.engines[0].detects[0], h.engines[0].detects[1])
    }

    @Test
    fun results_reach_only_the_message_that_asked() {
        val h = harness()
        val first_events = recorder()
        val second_events = recorder()
        val first = open(h, first_events)
        val second = open(h, second_events)

        first.translate(listOf("Hallo"), "de", "en")
        second.translate(listOf("Bonjour"), "fr", "en")
        val first_id = h.engines[0].translations[0].second
        val second_id = h.engines[0].translations[1].second
        h.router!!.route_result("{\"id\":\"$second_id\",\"segments\":[\"Hello\"]}")
        h.router!!.route_result("{\"id\":\"$first_id\",\"segments\":[\"Hello\"]}")

        assertEquals(1, first_events.results.size)
        assertTrue(first_events.results[0].contains(first_id))
        assertEquals(1, second_events.results.size)
        assertTrue(second_events.results[0].contains(second_id))
    }

    @Test
    fun a_detection_reaches_only_the_message_that_asked() {
        val h = harness()
        val first_events = recorder()
        val second_events = recorder()
        val first = open(h, first_events)
        open(h, second_events)

        first.detect("Guten Tag", "en")
        val id = h.engines[0].detects[0]
        h.router!!.route_detect("{\"id\":\"$id\",\"detected\":true,\"language\":\"de\"}")

        assertEquals(1, first_events.detects.size)
        assertTrue(second_events.detects.isEmpty())
    }

    @Test
    fun a_stale_result_from_an_earlier_request_is_dropped() {
        val h = harness()
        val events = recorder()
        val session = open(h, events)

        session.translate(listOf("Hallo"), "de", "en")
        val stale = h.engines[0].translations[0].second
        session.translate(listOf("Hallo"), "de", "en")
        h.router!!.route_result("{\"id\":\"$stale\",\"segments\":[\"Hello\"]}")

        assertTrue(events.results.isEmpty())
    }

    @Test
    fun a_closed_message_hears_nothing() {
        val h = harness()
        val events = recorder()
        val session = open(h, events)

        session.translate(listOf("Hallo"), "de", "en")
        val id = h.engines[0].translations[0].second
        session.close()
        h.router!!.route_result("{\"id\":\"$id\",\"segments\":[\"Hello\"]}")
        h.router!!.route_status("{\"id\":\"$id\",\"state\":\"translated\"}")

        assertTrue(events.results.isEmpty())
        assertTrue(events.statuses.isEmpty())
    }

    @Test
    fun a_crashed_engine_fails_waiting_messages_and_is_recreated() {
        val h = harness()
        val events = recorder()
        val session = open(h, events)

        session.translate(listOf("Hallo"), "de", "en")
        h.engines[0].destroyed = true
        h.gone!!.invoke()

        assertEquals(listOf(TRANSLATION_ERROR_STATUS), events.statuses)

        session.translate(listOf("Hallo"), "de", "en")

        assertEquals(2, h.engines.size)
        assertEquals(1, h.engines[1].translations.size)
    }

    @Test
    fun the_ready_signal_is_not_forwarded() {
        val h = harness()
        val events = recorder()
        val session = open(h, events)
        session.detect("a", "en")

        h.router!!.route_status("{\"state\":\"ready\"}")

        assertTrue(events.statuses.isEmpty())
    }

    @Test
    fun the_engine_shuts_down_after_the_last_message_closes() {
        val h = harness()
        val session = open(h, recorder())
        session.detect("a", "en")

        session.close()

        assertEquals(1, h.scheduled.size)
        h.scheduled.toList().single().run()
        assertTrue(h.engines[0].destroyed)
        assertFalse(h.host.engine_running())
    }

    @Test
    fun reopening_before_the_grace_period_keeps_the_engine() {
        val h = harness()
        open(h, recorder()).apply { detect("a", "en") }.close()

        val again = open(h, recorder())
        again.detect("b", "en")

        assertTrue(h.scheduled.isEmpty())
        assertEquals(1, h.engines.size)
        assertFalse(h.engines[0].destroyed)
    }

    @Test
    fun request_ids_name_their_session() {
        assertEquals("s3", translation_session_of("s3:9"))
        assertNull(translation_session_of("nocolon"))
        assertEquals("s3:9", translation_request_id_of("{\"id\":\"s3:9\",\"state\":\"progress\"}"))
        assertNull(translation_request_id_of("{\"state\":\"ready\"}"))
    }
}
