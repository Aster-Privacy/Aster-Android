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
import org.astermail.android.ui.contacts.ContactEntry
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

    private fun stored(id: String, name: String, email: String = "", phone: String = "") =
        Contact(id = id, name = name, email = email, phone = phone)

    @Test
    fun a_new_email_on_a_stored_contact_is_merged() {
        val phone = listOf(
            person("Ana").with_typed_fields(
                emails = listOf(
                    ContactEntry("ana@example.com", ContactEntry.TYPE_HOME),
                    ContactEntry("ana@work.example", ContactEntry.TYPE_WORK),
                ),
                phones = emptyList(),
                addresses = emptyList(),
            ),
        )
        val plan = plan_contact_import(phone, listOf(stored("c1", "Ana", email = "ana@example.com")))
        assertTrue(plan.new_contacts.isEmpty())
        assertEquals(listOf("c1"), plan.updates.map { it.id })
        assertEquals(
            listOf("ana@example.com", "ana@work.example"),
            plan.updates.single().email_entries().map { it.value },
        )
    }

    @Test
    fun a_new_phone_on_a_stored_contact_is_merged() {
        val phone = listOf(
            person("Ben").with_typed_fields(
                emails = emptyList(),
                phones = listOf(
                    ContactEntry("+1 555 0100", ContactEntry.TYPE_MOBILE),
                    ContactEntry("+1 555 0199", ContactEntry.TYPE_WORK),
                ),
                addresses = emptyList(),
            ),
        )
        val plan = plan_contact_import(phone, listOf(stored("c2", "Ben", phone = "+15550100")))
        assertEquals(listOf("+15550100", "+1 555 0199"), plan.updates.single().phone_entries().map { it.value })
    }

    @Test
    fun an_email_added_to_a_name_only_contact_is_merged() {
        val phone = listOf(person("Cy", "cy@example.com"))
        val plan = plan_contact_import(phone, listOf(stored("c3", "Cy")))
        assertTrue(plan.new_contacts.isEmpty())
        assertEquals(listOf("cy@example.com"), plan.updates.single().email_entries().map { it.value })
    }

    private fun with_emails(name: String, vararg emails: String) = person(name).with_typed_fields(
        emails = emails.map { ContactEntry(it, ContactEntry.TYPE_HOME) },
        phones = emptyList(),
        addresses = emptyList(),
    )

    @Test
    fun an_email_removed_in_aster_stays_removed_after_device_sync() {
        val phone = listOf(with_emails("Eve", "eve@example.com", "eve@old.example"))
        val synced_before = device_entry_keys(phone)
        val plan = plan_contact_import(phone, listOf(stored("c5", "Eve", email = "eve@example.com")), synced_before)
        assertTrue(plan.updates.isEmpty())
        assertTrue(plan.new_contacts.isEmpty())
    }

    @Test
    fun a_phone_removed_in_aster_stays_removed_after_device_sync() {
        val phone = listOf(
            person("Fay").with_typed_fields(
                emails = emptyList(),
                phones = listOf(
                    ContactEntry("+1 555 0100", ContactEntry.TYPE_MOBILE),
                    ContactEntry("+1 555 0101", ContactEntry.TYPE_WORK),
                ),
                addresses = emptyList(),
            ),
        )
        val plan = plan_contact_import(phone, listOf(stored("c6", "Fay", phone = "+15550100")), device_entry_keys(phone))
        assertTrue(plan.updates.isEmpty())
    }

    @Test
    fun only_entries_added_on_the_device_since_the_last_sync_are_merged() {
        val before = listOf(with_emails("Gus", "gus@example.com", "gus@old.example"))
        val after = listOf(with_emails("Gus", "gus@example.com", "gus@old.example", "gus@new.example"))
        val plan = plan_contact_import(after, listOf(stored("c7", "Gus", email = "gus@example.com")), device_entry_keys(before))
        assertEquals(
            listOf("gus@example.com", "gus@new.example"),
            plan.updates.single().email_entries().map { it.value },
        )
    }

    @Test
    fun a_first_device_sync_never_merges_into_stored_contacts() {
        val phone = listOf(with_emails("Hal", "hal@example.com", "hal@old.example"))
        val plan = plan_contact_import(phone, listOf(stored("c8", "Hal", email = "hal@example.com")), null)
        assertTrue(plan.updates.isEmpty())
        assertTrue(plan.new_contacts.isEmpty())
    }

    @Test
    fun a_first_device_sync_still_uploads_new_contacts() {
        val phone = listOf(with_emails("Ivy", "ivy@example.com"))
        assertEquals(1, plan_contact_import(phone, emptyList(), null).new_contacts.size)
    }

    @Test
    fun an_unchanged_contact_produces_no_update() {
        val phone = listOf(person("Dee", "dee@example.com"))
        val plan = plan_contact_import(phone, listOf(stored("c4", "Dee", email = "DEE@example.com")))
        assertTrue(plan.updates.isEmpty())
        assertTrue(plan.new_contacts.isEmpty())
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
