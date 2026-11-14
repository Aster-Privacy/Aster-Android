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

import org.astermail.android.mail.ratchet.PeerKind
import org.astermail.android.mail.ratchet.PinIdentity
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class HostedRecipientDirectoryTest {

    @Before
    fun reset_before() = HostedRecipientDirectory.clear()

    @After
    fun reset_after() = HostedRecipientDirectory.clear()

    @Test
    fun custom_domain_address_is_external_until_looked_up() {
        assertFalse(is_internal_recipient("lucas@example-domain.de"))
        assertTrue(HostedRecipientDirectory.needs_lookup("lucas@example-domain.de"))
    }

    @Test
    fun confirmed_custom_domain_address_counts_as_internal() {
        HostedRecipientDirectory.record("Lucas@Example-Domain.de", internal = true)
        assertTrue(is_internal_recipient("lucas@example-domain.de"))
        assertTrue(is_internal_recipient(" LUCAS@example-domain.de "))
        assertFalse(HostedRecipientDirectory.needs_lookup("lucas@example-domain.de"))
        assertFalse(is_aster_domain_recipient("lucas@example-domain.de"))
    }

    @Test
    fun outside_answer_stays_external() {
        HostedRecipientDirectory.record("someone@example.com", internal = false)
        assertFalse(is_internal_recipient("someone@example.com"))
        assertFalse(HostedRecipientDirectory.needs_lookup("someone@example.com"))
    }

    @Test
    fun aster_domains_never_need_lookup() {
        assertTrue(is_internal_recipient("user@astermail.org"))
        assertFalse(HostedRecipientDirectory.needs_lookup("user@astermail.org"))
        HostedRecipientDirectory.record("user@astermail.org", internal = false)
        assertTrue(is_internal_recipient("user@astermail.org"))
    }

    @Test
    fun malformed_addresses_are_not_looked_up() {
        assertFalse(HostedRecipientDirectory.needs_lookup("not-an-address"))
        assertFalse(HostedRecipientDirectory.needs_lookup("bad local@example.de"))
        assertFalse(HostedRecipientDirectory.needs_lookup(""))
    }

    @Test
    fun lookup_username_drops_plus_tag() {
        assertEquals("lucas", HostedRecipientDirectory.lookup_username("lucas+news@example-domain.de"))
        assertNull(HostedRecipientDirectory.lookup_username("@example-domain.de"))
    }

    @Test
    fun hosted_list_holds_only_confirmed_custom_domain_recipients() {
        HostedRecipientDirectory.record("lucas@example-domain.de", internal = true)
        HostedRecipientDirectory.record("other@example.com", internal = false)
        val hosted = hosted_recipients_of(
            listOf("lucas@example-domain.de", "LUCAS@example-domain.de", "user@astermail.org", "other@example.com", ""),
        )
        assertEquals(listOf("lucas@example-domain.de"), hosted)
    }

    @Test
    fun confirmed_custom_domain_mixes_with_outside_recipients() {
        HostedRecipientDirectory.record("lucas@example-domain.de", internal = true)
        assertFalse(has_mixed_recipients(listOf("lucas@example-domain.de", "user@astermail.org")))
        assertTrue(has_mixed_recipients(listOf("lucas@example-domain.de", "someone@example.com")))
    }

    @Test
    fun pin_identity_keeps_custom_domains_out_of_builtin_folding() {
        HostedRecipientDirectory.record("first.last@example-domain.de", internal = true)
        assertEquals(PeerKind.EXTERNAL, PinIdentity.peer_kind("first.last@example-domain.de"))
        assertEquals(PeerKind.BUILTIN, PinIdentity.peer_kind("first.last@astermail.org"))
    }

    @Test
    fun clear_forgets_lookups() {
        HostedRecipientDirectory.record("lucas@example-domain.de", internal = true)
        HostedRecipientDirectory.clear()
        assertFalse(is_internal_recipient("lucas@example-domain.de"))
    }
}
