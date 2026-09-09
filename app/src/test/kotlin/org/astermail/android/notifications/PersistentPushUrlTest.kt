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

package org.astermail.android.notifications

import org.junit.Assert.assertEquals
import org.junit.Test

class PersistentPushUrlTest {

    @Test
    fun `https base becomes a secure websocket url`() {
        assertEquals("wss://app.astermail.org/ws", websocket_url_for("https://app.astermail.org"))
    }

    @Test
    fun `trailing slashes are trimmed`() {
        assertEquals("wss://app.astermail.org/ws", websocket_url_for("https://app.astermail.org/"))
        assertEquals("wss://app.astermail.org/ws", websocket_url_for("  https://app.astermail.org//  "))
    }

    @Test
    fun `http base is only used for plain websockets`() {
        assertEquals("ws://10.0.2.2:8080/ws", websocket_url_for("http://10.0.2.2:8080"))
    }

    @Test
    fun `an explicit websocket scheme is preserved`() {
        assertEquals("wss://app.astermail.org/ws", websocket_url_for("wss://app.astermail.org"))
        assertEquals("ws://localhost:8080/ws", websocket_url_for("ws://localhost:8080"))
    }

    @Test
    fun `a schemeless base defaults to tls`() {
        assertEquals("wss://app.astermail.org/ws", websocket_url_for("app.astermail.org"))
    }

    @Test
    fun `a path on the base is preserved`() {
        assertEquals("wss://example.org/api/ws", websocket_url_for("https://example.org/api"))
    }
}
