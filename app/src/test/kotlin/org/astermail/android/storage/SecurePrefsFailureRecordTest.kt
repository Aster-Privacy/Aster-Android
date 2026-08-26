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

package org.astermail.android.storage

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SecurePrefsFailureRecordTest {

    private val failure_key = "keystore_failure_aster_prefs_v2"
    private val attempts_key = "rekey_attempts_aster_prefs"

    @Test
    fun a_first_keystore_failure_is_recorded_without_touching_key_material() {
        val prefs = fake_prefs()

        assertFalse(SecurePrefs.record_failure_in(prefs, failure_key))
        assertEquals(true, prefs.values[failure_key])
    }

    @Test
    fun a_second_keystore_failure_is_reported_once_the_first_one_is_durable() {
        val prefs = fake_prefs()

        SecurePrefs.record_failure_in(prefs, failure_key)

        assertTrue(SecurePrefs.record_failure_in(prefs, failure_key))
    }

    @Test
    fun key_material_is_never_destroyed_when_the_failure_flag_cannot_be_written() {
        val prefs = fake_prefs(commit_result = false)

        repeat(5) { assertFalse(SecurePrefs.record_failure_in(prefs, failure_key)) }

        assertFalse(prefs.values.containsKey(failure_key))
    }

    @Test
    fun key_material_is_never_destroyed_when_the_failure_flag_cannot_be_read() {
        val prefs = fake_prefs(read_error = true)

        assertFalse(SecurePrefs.record_failure_in(prefs, failure_key))
    }

    @Test
    fun clearing_a_failure_record_reports_a_write_that_landed() {
        val prefs = fake_prefs()
        SecurePrefs.record_failure_in(prefs, failure_key)

        assertTrue(SecurePrefs.clear_failure_record_in(prefs, failure_key))
        assertFalse(prefs.values.containsKey(failure_key))
    }

    @Test
    fun clearing_a_failure_record_reports_a_write_that_never_landed() {
        val prefs = fake_prefs(commit_result = false)
        prefs.values[failure_key] = true

        assertFalse(SecurePrefs.clear_failure_record_in(prefs, failure_key))
        assertEquals(true, prefs.values[failure_key])
    }

    @Test
    fun a_legacy_rekey_is_retried_before_the_prefs_file_is_quarantined() {
        val prefs = fake_prefs()

        val outcomes = (1..rekey_max_attempts).map {
            SecurePrefs.exhausted_rekey_attempts(prefs, attempts_key)
        }

        assertEquals(List(rekey_max_attempts - 1) { false } + true, outcomes)
    }

    @Test
    fun a_legacy_rekey_is_never_marked_done_when_the_attempt_count_cannot_be_written() {
        val prefs = fake_prefs(commit_result = false)

        repeat(rekey_max_attempts * 3) {
            assertFalse(SecurePrefs.exhausted_rekey_attempts(prefs, attempts_key))
        }
    }

    @Test
    fun a_legacy_rekey_is_never_marked_done_when_the_attempt_count_cannot_be_read() {
        val prefs = fake_prefs(read_error = true)

        assertFalse(SecurePrefs.exhausted_rekey_attempts(prefs, attempts_key))
    }
}
