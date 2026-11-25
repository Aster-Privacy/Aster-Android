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

import android.content.Context
import android.content.pm.PackageManager
import android.os.Bundle
import dagger.hilt.android.EntryPointAccessors
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkStatic
import io.mockk.spyk
import io.mockk.unmockkAll
import io.mockk.verify
import org.astermail.android.api.devices.DeviceCodeApi
import org.astermail.android.storage.AccountStore
import org.astermail.android.storage.SessionKeyStore
import org.astermail.android.storage.SessionSnapshotStore
import org.astermail.android.storage.StoredAccount
import org.junit.After
import org.junit.Before
import org.junit.Test

open class AccountLinkProviderConfirmTest {

    protected val account_id = "acct-1"
    protected val code = "ABCD-EFGH"
    protected val binding_tag = "tag-1"

    private val account_store = mockk<AccountStore>()
    protected val session_key_store = mockk<SessionKeyStore>()
    private val snapshot_store = mockk<SessionSnapshotStore>(relaxed = true)
    protected val device_code_api = mockk<DeviceCodeApi>(relaxed = true)

    protected lateinit var provider: AccountLinkProvider

    @Before
    fun set_up() {
        every { account_store.account_exists(account_id) } returns true
        every { account_store.get_current_id() } returns account_id
        every { account_store.get_all() } returns listOf(StoredAccount(id = account_id, email = "a@example.org", added_at = 0L))
        every { session_key_store.get_user_id() } returns account_id
        every { session_key_store.get_passphrase() } answers { ByteArray(32) { 7 } }
        coEvery { device_code_api.verify_code(any()) } throws IllegalStateException("stop after the network read")

        val entry = mockk<AccountLinkProvider.AccountLinkEntryPoint>()
        every { entry.account_store() } returns account_store
        every { entry.session_key_store() } returns session_key_store
        every { entry.session_snapshot_store() } returns snapshot_store
        every { entry.device_code_api() } returns device_code_api

        val app = mockk<Context>(relaxed = true)
        every { app.packageManager } returns mockk<PackageManager>(relaxed = true)
        every { app.applicationContext } returns app
        mockkStatic(EntryPointAccessors::class)
        every { EntryPointAccessors.fromApplication(any(), AccountLinkProvider.AccountLinkEntryPoint::class.java) } returns entry

        provider = spyk(AccountLinkProvider())
        every { provider.context } returns app
    }

    @After
    fun tear_down() {
        unmockkAll()
    }

    protected fun link_extras(): Bundle {
        val extras = mockk<Bundle>()
        every { extras.getString(AccountLinkProvider.EXTRA_CODE) } returns code
        every { extras.getString(AccountLinkProvider.EXTRA_ACCOUNT_ID) } returns account_id
        every { extras.getString(AccountLinkProvider.EXTRA_BINDING_TAG) } returns binding_tag
        every { extras.getIntArray(AccountLinkProvider.EXTRA_ENVELOPE_VERSIONS) } returns null
        return extras
    }

    @Test
    fun a_link_request_does_not_read_the_passphrase_or_the_pending_device_without_the_user() {
        runCatching { provider.call(AccountLinkProvider.METHOD_LINK, null, link_extras()) }

        verify(exactly = 0) { session_key_store.get_passphrase() }
        coVerify(exactly = 0) { device_code_api.verify_code(any()) }
        coVerify(exactly = 0) { device_code_api.confirm_code(any(), any()) }
    }
}
