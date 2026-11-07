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

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.test.runTest
import org.astermail.android.api.ApiError
import org.astermail.android.ui.contacts.Contact
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test

class device_contact_upload_test {

    private fun person(name: String, email: String = "", deleted_at: String = "", work_email: String = "") =
        Contact(id = "", name = name, email = email, work_email = work_email, deleted_at = deleted_at)

    @Test
    fun repeated_import_adds_nothing_when_every_contact_is_stored() {
        val phone = listOf(person("Ana", "ana@example.com"), person("Ben"), person("Cy", "cy@example.com"))
        val stored = listOf(person("Ana", "ANA@example.com "), person("ben"), person("Cy", "cy@example.com"))
        val plan = plan_contact_import(phone, stored)
        assertTrue(plan.new_contacts.isEmpty())
        assertEquals(0, plan.in_trash)
    }

    @Test
    fun duplicates_inside_one_import_are_added_once() {
        val phone = listOf(
            person("Ana", "ana@example.com"),
            person("Ana Work", "Ana@Example.com"),
            person("Ben"),
            person("BEN"),
        )
        val plan = plan_contact_import(phone, emptyList())
        assertEquals(listOf("Ana", "Ben"), plan.new_contacts.map { it.name })
    }

    @Test
    fun work_email_counts_as_a_match() {
        val phone = listOf(person("Ana", "ana@work.example"))
        val stored = listOf(person("Ana", "ana@example.com", work_email = "ana@work.example"))
        assertTrue(plan_contact_import(phone, stored).new_contacts.isEmpty())
    }

    @Test
    fun trashed_contacts_are_reported_and_not_reimported() {
        val phone = listOf(person("Ana", "ana@example.com"), person("Dee", "dee@example.com"))
        val stored = listOf(person("Ana", "ana@example.com", deleted_at = "2026-11-01T00:00:00Z"))
        val plan = plan_contact_import(phone, stored)
        assertEquals(listOf("Dee"), plan.new_contacts.map { it.name })
        assertEquals(1, plan.in_trash)
    }

    @Test
    fun an_email_match_ignores_a_shared_name() {
        val phone = listOf(person("Ana", "ana.new@example.com"))
        val stored = listOf(person("Ana", "ana@example.com"))
        assertEquals(1, plan_contact_import(phone, stored).new_contacts.size)
    }

    private fun limited_server(cap: Int): Pair<MutableList<Int>, suspend (Int) -> Result<*>> {
        val stored = mutableListOf<Int>()
        val create: suspend (Int) -> Result<*> = { contact ->
            if (stored.size >= cap) {
                Result.failure<Unit>(ApiError.PlanLimitExceeded("plan limit reached", "contacts"))
            } else {
                stored.add(contact)
                Result.success(Unit)
            }
        }
        return stored to create
    }

    @Test
    fun uploads_every_contact_under_the_limit() = runTest {
        val (stored, create) = limited_server(cap = 1000)
        val result = upload_device_contacts((1..460).toList(), create)
        assertEquals(460, result.imported)
        assertEquals(0, result.failed)
        assertFalse(result.limit_reached)
        assertEquals(460, stored.size)
    }

    @Test
    fun stops_at_the_plan_limit_and_reports_it() = runTest {
        val (stored, create) = limited_server(cap = 150)
        var calls = 0
        val counting: suspend (Int) -> Result<*> = { calls++; create(it) }
        val result = upload_device_contacts((1..460).toList(), counting)
        assertEquals(150, result.imported)
        assertEquals(0, result.failed)
        assertTrue(result.limit_reached)
        assertEquals(150, stored.size)
        assertEquals(151, calls)
    }

    @Test
    fun reports_the_limit_when_nothing_fits() = runTest {
        val (_, create) = limited_server(cap = 0)
        val result = upload_device_contacts(listOf(1, 2, 3), create)
        assertEquals(0, result.imported)
        assertTrue(result.limit_reached)
    }

    @Test
    fun counts_other_failures_and_keeps_going() = runTest {
        val boom = IllegalStateException("network")
        val result = upload_device_contacts((1..5).toList(), { n ->
            if (n % 2 == 0) Result.failure<Unit>(boom) else Result.success(Unit)
        })
        assertEquals(3, result.imported)
        assertEquals(2, result.failed)
        assertFalse(result.limit_reached)
        assertSame(boom, result.last_failure)
    }

    @Test
    fun reports_progress_for_each_contact() = runTest {
        val progress = mutableListOf<Int>()
        upload_device_contacts(listOf("a", "b", "c"), { Result.success(Unit) }, on_progress = { progress.add(it) })
        assertEquals(listOf(1, 2, 3), progress)
    }

    @Test
    fun rethrows_cancellation() = runTest {
        try {
            upload_device_contacts(listOf(1, 2), { Result.failure<Unit>(CancellationException("stop")) })
            fail("expected cancellation")
        } catch (_: CancellationException) {
        }
    }
}
