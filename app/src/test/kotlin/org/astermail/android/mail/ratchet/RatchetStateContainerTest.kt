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
package org.astermail.android.mail.ratchet

import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class RatchetStateContainerTest {

    private val json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
        explicitNulls = false
    }

    private fun sample_state(conversation_id: String): RatchetState = RatchetState(
        conversation_id = conversation_id,
        dh_keypair = RatchetDhKeyPair(public_key = "pub", secret_key = "sec"),
        root_key = "root",
        send_message_number = 4,
    )

    private fun legacy_container(bound_id: String, state: RatchetState): String =
        """{"state":${json.encodeToString(RatchetState.serializer(), state)},"conversation_id":"$bound_id"}"""

    @Test
    fun `a container written for this conversation is accepted with its version`() {
        val text = RatchetStateContainer.encode("conv-a", sample_state("conv-a"), 50L)

        val decoded = RatchetStateContainer.decode(text, "conv-a", floor = 50L, opened_bound = false)

        assertTrue(decoded is RatchetStateContainerResult.Accepted)
        decoded as RatchetStateContainerResult.Accepted
        assertEquals(50L, decoded.sync_version)
        assertEquals(4, decoded.state.send_message_number)
    }

    @Test
    fun `a container bound to another conversation is refused`() {
        val text = RatchetStateContainer.encode("conv-b", sample_state("conv-b"), 50L)

        assertEquals(
            RatchetStateContainerResult.WrongConversation,
            RatchetStateContainer.decode(text, "conv-a", floor = 0L, opened_bound = false),
        )
    }

    @Test
    fun `a legacy container whose state belongs to another conversation is refused`() {
        val relabeled = legacy_container("conv-a", sample_state("conv-b"))
        val flat = json.encodeToString(RatchetState.serializer(), sample_state("conv-b"))

        assertEquals(
            RatchetStateContainerResult.WrongConversation,
            RatchetStateContainer.decode(relabeled, "conv-a", floor = 0L, opened_bound = false),
        )
        assertEquals(
            RatchetStateContainerResult.WrongConversation,
            RatchetStateContainer.decode(flat, "conv-a", floor = 0L, opened_bound = false),
        )
    }

    @Test
    fun `a version below the highest one seen is refused as a rollback`() {
        val text = RatchetStateContainer.encode("conv-a", sample_state("conv-a"), 49L)

        assertEquals(
            RatchetStateContainerResult.RolledBack,
            RatchetStateContainer.decode(text, "conv-a", floor = 50L, opened_bound = false),
        )
    }

    @Test
    fun `a legacy container without a version is still accepted`() {
        val wrapped = legacy_container("conv-a", sample_state("conv-a"))
        val flat = json.encodeToString(RatchetState.serializer(), sample_state("conv-a"))

        val from_wrapped = RatchetStateContainer.decode(wrapped, "conv-a", floor = 50L, opened_bound = false)
        val from_flat = RatchetStateContainer.decode(flat, "conv-a", floor = 50L, opened_bound = false)

        assertTrue(from_wrapped is RatchetStateContainerResult.Accepted)
        assertTrue(from_flat is RatchetStateContainerResult.Accepted)
        assertEquals(null, (from_wrapped as RatchetStateContainerResult.Accepted).sync_version)
    }

    @Test
    fun `a blob opened with bound associated data must carry a version`() {
        val wrapped = legacy_container("conv-a", sample_state("conv-a"))

        assertEquals(
            RatchetStateContainerResult.Unbound,
            RatchetStateContainer.decode(wrapped, "conv-a", floor = 0L, opened_bound = true),
        )
    }

    @Test
    fun `each write moves the version past the highest one seen`() {
        assertEquals(1_000L, RatchetStateContainer.next_version(floor = 10L, now_ms = 1_000L))
        assertEquals(2_001L, RatchetStateContainer.next_version(floor = 2_000L, now_ms = 1_000L))
    }

    @Test
    fun `the associated data differs per conversation`() {
        assertFalse(
            RatchetStateContainer.bound_aad("conv-a").contentEquals(RatchetStateContainer.bound_aad("conv-b")),
        )
    }

    @Test
    fun `the fingerprint ignores the version so an unchanged state is not synced again`() {
        val first = RatchetStateContainer.encode("conv-a", sample_state("conv-a"), 1L)
        val second = RatchetStateContainer.encode("conv-a", sample_state("conv-a"), 2L)

        assertTrue(first != second)
        assertEquals(
            RatchetStateContainer.state_fingerprint(sample_state("conv-a")),
            RatchetStateContainer.state_fingerprint(sample_state("conv-a")),
        )
    }
    private val web_state = """{"dh_keypair":{"public_key":"pub","secret_key":"sec"},"root_key":"root","send_message_number":2,"bootstrap":{"ephemeral_key":"eph","sender_identity_key":"s","recipient_identity_key":"r","x3dh_version":2}}"""

    @Test
    fun `a web state without an inner binding decodes under the outer binding`() {
        val text = """{"state":$web_state,"conversation_id":"conv-a","sync_version":3}"""

        val decoded = RatchetStateContainer.decode(text, "conv-a", floor = 0L, opened_bound = false)

        assertTrue(decoded is RatchetStateContainerResult.Accepted)
        decoded as RatchetStateContainerResult.Accepted
        assertEquals("conv-a", decoded.state.conversation_id)
        assertEquals(2, decoded.state.send_message_number)
        assertEquals(2, decoded.state.bootstrap?.x3dh_version)
        assertEquals(3L, decoded.sync_version)
    }

    @Test
    fun `a web state without an inner binding still refuses a swapped conversation`() {
        val text = """{"state":$web_state,"conversation_id":"conv-b","sync_version":3}"""

        assertEquals(
            RatchetStateContainerResult.WrongConversation,
            RatchetStateContainer.decode(text, "conv-a", floor = 0L, opened_bound = false),
        )
    }

    @Test
    fun `a bootstrap x3dh version survives a round trip`() {
        val state = sample_state("conv-a").apply {
            bootstrap = BootstrapData(ephemeral_key = "eph", x3dh_version = 2)
        }
        val text = RatchetStateContainer.encode("conv-a", state, 5L)

        val decoded = RatchetStateContainer.decode(text, "conv-a", floor = 0L, opened_bound = false)

        assertEquals(2, (decoded as RatchetStateContainerResult.Accepted).state.bootstrap?.x3dh_version)
    }
}
