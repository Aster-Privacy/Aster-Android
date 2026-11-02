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

package org.astermail.android.api.errors

import com.sun.net.httpserver.HttpServer
import java.net.InetSocketAddress
import java.util.Collections
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class ClientErrorReporterConsentTest {
    private lateinit var server: HttpServer
    private val bodies: MutableList<String> = Collections.synchronizedList(mutableListOf())
    private val agents: MutableList<String> = Collections.synchronizedList(mutableListOf())

    @Before
    fun start_server() {
        server = HttpServer.create(InetSocketAddress("127.0.0.1", 0), 0)
        server.createContext("/api/core/v1/client-errors") { exchange ->
            bodies += exchange.requestBody.readBytes().toString(Charsets.UTF_8)
            agents += exchange.requestHeaders.getFirst("User-Agent").orEmpty()
            exchange.sendResponseHeaders(204, -1)
            exchange.close()
        }
        server.start()
        ClientErrorReporter.configure("http://127.0.0.1:${server.address.port}", "1.0.16", "AsterMail-Android/1.0.16")
    }

    @After
    fun stop_server() {
        ClientErrorReporter.set_enabled(false)
        server.stop(0)
    }

    @Test
    fun sends_nothing_while_the_setting_is_off() {
        ClientErrorReporter.set_enabled(false)
        assertFalse(ClientErrorReporter.is_enabled())
        ClientErrorReporter.report(feature = "consent_off", error_code = "consent_off_crash", wait_for_delivery = true)
        ClientErrorReporter.report(feature = "consent_off", error_code = "consent_off_http", http_status = 500)
        Thread.sleep(500)
        assertTrue(bodies.isEmpty())
    }

    @Test
    fun sends_only_the_allowlisted_fields_once_turned_on() {
        ClientErrorReporter.set_enabled(true)
        ClientErrorReporter.report(
            feature = "consent_on",
            error_code = "uncaught_nullpointerexception",
            route = "/app/t/gi3.c:12",
            wait_for_delivery = true,
        )
        assertEquals(1, bodies.size)
        assertEquals(
            "{\"surface\":\"mail_android\",\"feature\":\"consent_on\",\"error_code\":\"uncaught_nullpointerexception\"," +
                "\"severity\":\"error\",\"platform\":\"android\",\"release\":\"1.0.16\",\"route\":\"/app/t/gi3.c:12\"}",
            bodies[0],
        )
        assertEquals("AsterMail-Android/1.0.16", agents[0])
    }

    @Test
    fun drops_free_text_and_unsafe_routes_even_when_on() {
        ClientErrorReporter.set_enabled(true)
        ClientErrorReporter.report(feature = "consent on", error_code = "x", wait_for_delivery = true)
        ClientErrorReporter.report(feature = "consent_free", error_code = "user@astermail.org", wait_for_delivery = true)
        ClientErrorReporter.report(
            feature = "consent_route",
            error_code = "consent_route_err",
            route = "/mail/Bob Smith",
            wait_for_delivery = true,
        )
        assertEquals(1, bodies.size)
        assertTrue(bodies[0].contains("\"route\":\"/app\""))
        assertFalse(bodies[0].contains("Bob"))
    }

    @Test
    fun turning_it_off_stops_reports_right_away() {
        ClientErrorReporter.set_enabled(true)
        ClientErrorReporter.report(feature = "consent_toggle", error_code = "consent_toggle_a", wait_for_delivery = true)
        ClientErrorReporter.set_enabled(false)
        ClientErrorReporter.report(feature = "consent_toggle", error_code = "consent_toggle_b", wait_for_delivery = true)
        Thread.sleep(300)
        assertEquals(1, bodies.size)
        assertTrue(bodies[0].contains("consent_toggle_a"))
    }
}
