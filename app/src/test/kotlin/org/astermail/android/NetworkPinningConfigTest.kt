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

package org.astermail.android

import java.io.File
import java.time.LocalDate
import javax.xml.parsers.DocumentBuilderFactory
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.w3c.dom.Element

class NetworkPinningConfigTest {
    private val config: Element by lazy {
        var file = File("src/main/res/xml/network_security_config.xml")
        if (!file.isFile) file = File("app/src/main/res/xml/network_security_config.xml")
        DocumentBuilderFactory.newInstance().newDocumentBuilder().parse(file).documentElement
    }

    private fun elements(parent: Element, name: String): List<Element> {
        val nodes = parent.getElementsByTagName(name)
        return (0 until nodes.length).map { nodes.item(it) as Element }
    }

    private val pinned_config: Element by lazy {
        elements(config, "domain-config").single { elements(it, "pin-set").isNotEmpty() }
    }

    @Test
    fun every_aster_host_the_app_calls_is_pinned() {
        val domains = elements(pinned_config, "domain").map { it.textContent.trim() }

        assertEquals(listOf("app.astermail.org", "astermail.org"), domains)
    }

    @Test
    fun the_pin_set_keeps_backup_pins() {
        val pins = elements(pinned_config, "pin").map { it.textContent.trim() }

        assertTrue(pins.size >= 2)
        assertEquals(pins.size, pins.toSet().size)
        assertTrue(pins.all { it.length == 44 && it.endsWith("=") })
        assertTrue(elements(pinned_config, "pin").all { it.getAttribute("digest") == "SHA-256" })
    }

    @Test
    fun the_pin_set_does_not_expire_within_the_refresh_window() {
        val expiration = LocalDate.parse(elements(pinned_config, "pin-set").single().getAttribute("expiration"))

        assertTrue(
            "The pin set expires on $expiration. Refresh the pins and move the date forward.",
            expiration.isAfter(LocalDate.now().plusDays(REFRESH_WINDOW_DAYS)),
        )
    }

    @Test
    fun release_builds_never_allow_cleartext_or_user_certificates() {
        val base = elements(config, "base-config").single()

        assertEquals("false", base.getAttribute("cleartextTrafficPermitted"))
        assertFalse(elements(config, "certificates").any { it.getAttribute("src") == "user" })
    }

    private companion object {
        const val REFRESH_WINDOW_DAYS = 180L
    }
}
