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

package org.astermail.android.contacts

import org.astermail.android.ui.contacts.is_valid_contact_email
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ContactEmailValidationTest {

    @Test
    fun accepts_blank_values() {
        assertTrue(is_valid_contact_email(""))
        assertTrue(is_valid_contact_email("   "))
    }

    @Test
    fun accepts_ordinary_addresses_with_surrounding_space() {
        assertTrue(is_valid_contact_email("alice@example.org"))
        assertTrue(is_valid_contact_email(" bob.smith+work@mail.example.co.uk "))
    }

    @Test
    fun rejects_malformed_addresses() {
        assertFalse(is_valid_contact_email("alice"))
        assertFalse(is_valid_contact_email("alice@"))
        assertFalse(is_valid_contact_email("@example.org"))
        assertFalse(is_valid_contact_email("alice@example"))
        assertFalse(is_valid_contact_email("alice smith@example.org"))
        assertFalse(is_valid_contact_email("alice@@example.org"))
        assertFalse(is_valid_contact_email("a@b.org, c@d.org"))
    }
}
