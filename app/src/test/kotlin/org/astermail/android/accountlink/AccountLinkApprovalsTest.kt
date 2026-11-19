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

package org.astermail.android.accountlink

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class AccountLinkApprovalsTest {

    private var now = 1_000L
    private val approvals = AccountLinkApprovals(now_ms = { now }, ttl_ms = 60_000L)
    private val request = AccountLinkRequest("ABCD", "acct-1", "tag-1", "org.astermail.calendar")
    private val prompt = AccountLinkPrompt("Aster Calendar", "a@example.org")

    @Test
    fun nothing_is_approved_until_the_user_confirms() {
        val id = approvals.open(request, prompt)
        assertEquals(prompt, approvals.prompt_for(id))
        assertFalse(approvals.consume(request))
        assertTrue(approvals.approve(id))
        assertTrue(approvals.consume(request))
    }

    @Test
    fun an_approval_is_used_once() {
        approvals.approve(approvals.open(request, prompt))
        assertTrue(approvals.consume(request))
        assertFalse(approvals.consume(request))
    }

    @Test
    fun an_approval_covers_only_the_exact_request() {
        approvals.approve(approvals.open(request, prompt))
        assertFalse(approvals.consume(request.copy(code = "WXYZ")))
        assertFalse(approvals.consume(request.copy(account_id = "acct-2")))
        assertFalse(approvals.consume(request.copy(binding_tag = "tag-2")))
        assertFalse(approvals.consume(request.copy(caller = "org.example.other")))
        assertTrue(approvals.consume(request))
    }

    @Test
    fun a_declined_or_expired_request_cannot_be_approved() {
        val declined = approvals.open(request, prompt)
        approvals.decline(declined)
        assertFalse(approvals.approve(declined))
        assertNull(approvals.prompt_for(declined))

        val late = approvals.open(request, prompt)
        now += 60_001L
        assertNull(approvals.prompt_for(late))
        assertFalse(approvals.approve(late))
        assertFalse(approvals.consume(request))
    }

    @Test
    fun an_approval_expires_before_it_is_used() {
        approvals.approve(approvals.open(request, prompt))
        now += 60_001L
        assertFalse(approvals.consume(request))
    }

    @Test
    fun an_unknown_id_is_ignored_and_ids_are_unique() {
        assertFalse(approvals.approve("0".repeat(32)))
        val first = approvals.open(request, prompt)
        val second = approvals.open(request.copy(code = "WXYZ"), prompt)
        assertNotEquals(first, second)
        assertEquals(32, first.length)
    }
}
