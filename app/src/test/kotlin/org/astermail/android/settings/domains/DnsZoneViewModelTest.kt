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
package org.astermail.android.settings.domains

import android.content.Context
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.astermail.android.api.ApiError
import org.astermail.android.api.domains.DnsZoneApi
import org.astermail.android.api.domains.DnsZoneLimits
import org.astermail.android.api.domains.DnsZoneRecord
import org.astermail.android.api.domains.DnsZoneResponse
import org.astermail.android.api.security.SecurityApi
import org.astermail.android.auth.AuthRepository
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class DnsZoneViewModelTest {

    private val dispatcher = StandardTestDispatcher()
    private lateinit var api: DnsZoneApi
    private lateinit var vm: DnsZoneViewModel

    private val mx = DnsZoneRecord(id = "p1", type = "MX", host = "@", value = "mx.astermail.org", priority = 10, protected = true, protected_reason = "mail_routing")
    private val www = DnsZoneRecord(id = "r1", type = "A", host = "www", value = "93.184.216.34")
    private val blog = DnsZoneRecord(id = "r2", type = "CNAME", host = "blog", value = "host.other.net")

    private fun managed(vararg records: DnsZoneRecord, max: Int = 50) =
        DnsZoneResponse(managed = true, records = records.toList(), limits = DnsZoneLimits(max_records = max))

    private fun a_draft(host: String = "api", value: String = "93.184.216.35") =
        DnsRecordDraft(type = DnsRecordType.a, host = host, value = value)

    @Before
    fun setup() {
        Dispatchers.setMain(dispatcher)
        api = mockk(relaxed = true)
        vm = DnsZoneViewModel(
            mockk<Context>(relaxed = true),
            api,
            mockk<AuthRepository>(relaxed = true),
            mockk<SecurityApi>(relaxed = true),
        )
    }

    @After
    fun teardown() {
        Dispatchers.resetMain()
    }

    @Test
    fun bind_loads_a_managed_zone_with_protected_records_last() = runTest(dispatcher) {
        coEvery { api.get_zone("d1") } returns managed(mx, www, blog, max = 20)
        vm.bind("d1", "example.com")
        assertEquals(DnsZonePhase.loading, vm.state.value.phase)
        advanceUntilIdle()
        val s = vm.state.value
        assertEquals(DnsZonePhase.managed, s.phase)
        assertEquals(listOf("r1", "r2", "p1"), s.records.map { it.id })
        assertEquals(20, s.max_records)
        assertEquals(2, s.editable_count)
        assertFalse(s.refreshing)
    }

    @Test
    fun bind_is_a_no_op_for_the_same_domain() = runTest(dispatcher) {
        coEvery { api.get_zone("d1") } returns managed(www)
        vm.bind("d1", "example.com")
        advanceUntilIdle()
        vm.bind("d1", "example.com")
        advanceUntilIdle()
        coVerify(exactly = 1) { api.get_zone("d1") }
    }

    @Test
    fun binding_another_domain_resets_state_and_drops_stale_results() = runTest(dispatcher) {
        coEvery { api.get_zone("d1") } returns managed(www)
        coEvery { api.get_zone("d2") } returns DnsZoneResponse(managed = false)
        vm.bind("d1", "example.com")
        vm.bind("d2", "other.com")
        advanceUntilIdle()
        val s = vm.state.value
        assertEquals("d2", s.domain_id)
        assertEquals(DnsZonePhase.unmanaged, s.phase)
        assertTrue(s.records.isEmpty())
    }

    @Test
    fun load_failures_map_to_phases() = runTest(dispatcher) {
        coEvery { api.get_zone("d1") } throws ApiError.NotFoundError
        vm.bind("d1", "example.com")
        advanceUntilIdle()
        assertEquals(DnsZonePhase.unavailable, vm.state.value.phase)

        coEvery { api.get_zone("d2") } throws ApiError.ForbiddenError(code = "DNS_LOCKED")
        vm.bind("d2", "example.com")
        advanceUntilIdle()
        assertEquals(DnsZonePhase.locked, vm.state.value.phase)

        coEvery { api.get_zone("d3") } throws ApiError.ServerError(500)
        vm.bind("d3", "example.com")
        advanceUntilIdle()
        assertEquals(DnsZonePhase.failed, vm.state.value.phase)
        assertEquals(DnsZoneReason.server, vm.state.value.load_error)
    }

    @Test
    fun refresh_failure_keeps_content_and_sets_action_error() = runTest(dispatcher) {
        coEvery { api.get_zone("d1") } returns managed(www)
        vm.bind("d1", "example.com")
        advanceUntilIdle()
        coEvery { api.get_zone("d1") } throws ApiError.NetworkError
        vm.refresh()
        advanceUntilIdle()
        val s = vm.state.value
        assertEquals(DnsZonePhase.managed, s.phase)
        assertEquals(listOf("r1"), s.records.map { it.id })
        assertEquals(DnsZoneReason.offline, s.action_error)
    }

    @Test
    fun create_is_optimistic_then_replaced_by_the_server_record() = runTest(dispatcher) {
        coEvery { api.get_zone("d1") } returns managed(www)
        coEvery { api.create_record("d1", any()) } returns DnsZoneRecord(id = "r9", type = "A", host = "api", value = "93.184.216.35")
        vm.bind("d1", "example.com")
        advanceUntilIdle()

        val result = vm.submit(a_draft(), null)
        assertNotNull(result.request)
        val during = vm.state.value
        assertTrue(during.saving)
        val pending_id = during.records.first { it.host == "api" }.id
        assertTrue(pending_id.startsWith(DNS_PENDING_ID_PREFIX))
        assertEquals(DnsPendingOp.adding, during.pending[pending_id])

        advanceUntilIdle()
        val after = vm.state.value
        assertFalse(after.saving)
        assertTrue(after.pending.isEmpty())
        assertEquals(1, after.save_done_count)
        assertEquals(listOf("r9", "r1"), after.records.map { it.id })
    }

    @Test
    fun invalid_draft_is_never_sent() = runTest(dispatcher) {
        coEvery { api.get_zone("d1") } returns managed(www)
        vm.bind("d1", "example.com")
        advanceUntilIdle()
        val result = vm.submit(a_draft(value = "10.0.0.1"), null)
        assertNull(result.request)
        assertEquals(DnsFieldError.ip_private, result.errors.value)
        advanceUntilIdle()
        coVerify(exactly = 0) { api.create_record(any(), any()) }
    }

    @Test
    fun create_at_the_limit_is_refused_locally() = runTest(dispatcher) {
        coEvery { api.get_zone("d1") } returns managed(www, max = 1)
        vm.bind("d1", "example.com")
        advanceUntilIdle()
        assertTrue(vm.state.value.at_limit)
        vm.submit(a_draft(), null)
        advanceUntilIdle()
        assertEquals(DnsZoneReason.too_many_records, vm.state.value.save_error)
        coVerify(exactly = 0) { api.create_record(any(), any()) }
    }

    @Test
    fun failed_create_rolls_back_and_reports_the_reason() = runTest(dispatcher) {
        coEvery { api.get_zone("d1") } returns managed(www)
        coEvery { api.create_record("d1", any()) } throws ApiError.ValidationError(listOf("x"), code = "PRIVATE_IP")
        vm.bind("d1", "example.com")
        advanceUntilIdle()
        vm.submit(a_draft(), null)
        advanceUntilIdle()
        val s = vm.state.value
        assertEquals(listOf("r1"), s.records.map { it.id })
        assertFalse(s.saving)
        assertEquals(DnsZoneReason.private_ip, s.save_error)
        assertEquals(DnsDraftField.value, dns_zone_reason_field(s.save_error))
    }

    @Test
    fun failed_update_restores_the_original_record() = runTest(dispatcher) {
        coEvery { api.get_zone("d1") } returns managed(www)
        coEvery { api.update_record("d1", "r1", any()) } throws ApiError.ServerError(500)
        vm.bind("d1", "example.com")
        advanceUntilIdle()
        vm.submit(a_draft(host = "www", value = "93.184.216.99"), "r1")
        assertEquals("93.184.216.99", vm.state.value.records.single().value)
        advanceUntilIdle()
        val s = vm.state.value
        assertEquals("93.184.216.34", s.records.single().value)
        assertEquals(DnsZoneReason.server, s.save_error)
    }

    @Test
    fun step_up_opens_reauth_and_dismiss_clears_it() = runTest(dispatcher) {
        coEvery { api.get_zone("d1") } returns managed(www)
        coEvery { api.create_record("d1", any()) } throws ApiError.StepUpRequired()
        vm.bind("d1", "example.com")
        advanceUntilIdle()
        vm.submit(a_draft(), null)
        advanceUntilIdle()
        val s = vm.state.value
        assertNotNull(s.reauth)
        assertEquals(listOf("r1"), s.records.map { it.id })
        vm.dismiss_reauth()
        assertNull(vm.state.value.reauth)
        assertFalse(vm.state.value.saving)
    }

    @Test
    fun delete_skips_protected_records() = runTest(dispatcher) {
        coEvery { api.get_zone("d1") } returns managed(mx, www)
        vm.bind("d1", "example.com")
        advanceUntilIdle()
        vm.delete(mx)
        advanceUntilIdle()
        coVerify(exactly = 0) { api.delete_record(any(), any()) }
    }

    @Test
    fun delete_removes_on_success_and_keeps_on_failure() = runTest(dispatcher) {
        coEvery { api.get_zone("d1") } returns managed(www, blog)
        coEvery { api.delete_record("d1", "r1") } returns Unit
        coEvery { api.delete_record("d1", "r2") } throws ApiError.RateLimited()
        vm.bind("d1", "example.com")
        advanceUntilIdle()

        vm.delete(www)
        assertEquals(DnsPendingOp.deleting, vm.state.value.pending["r1"])
        advanceUntilIdle()
        assertEquals(listOf("r2"), vm.state.value.records.map { it.id })

        vm.delete(blog)
        advanceUntilIdle()
        val s = vm.state.value
        assertEquals(listOf("r2"), s.records.map { it.id })
        assertTrue(s.pending.isEmpty())
        assertEquals(DnsZoneReason.rate_limited, s.action_error)
    }
}
