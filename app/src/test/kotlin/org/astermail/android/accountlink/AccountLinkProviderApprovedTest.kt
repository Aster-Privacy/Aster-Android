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

import io.mockk.coVerify
import io.mockk.verify
import org.junit.Test

class AccountLinkProviderApprovedTest : AccountLinkProviderConfirmTest() {

    @Test
    fun a_confirmed_request_continues_once() {
        val request = AccountLinkRequest(code, account_id, binding_tag, "")
        val id = AccountLinkApprovals.shared.open(request, AccountLinkPrompt("Aster Calendar", "a@example.org"))
        AccountLinkApprovals.shared.approve(id)

        runCatching { provider.call(AccountLinkProvider.METHOD_LINK, null, link_extras()) }
        runCatching { provider.call(AccountLinkProvider.METHOD_LINK, null, link_extras()) }

        verify(exactly = 1) { session_key_store.get_passphrase() }
        coVerify(exactly = 1) { device_code_api.verify_code(code) }
    }
}
