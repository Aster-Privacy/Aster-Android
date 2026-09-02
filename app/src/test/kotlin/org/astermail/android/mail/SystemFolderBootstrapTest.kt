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

import android.content.Context
import android.util.Base64
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkStatic
import io.mockk.slot
import io.mockk.unmockkStatic
import kotlinx.coroutines.test.runTest
import org.astermail.android.api.labels.CreateLabelRequest
import org.astermail.android.api.labels.CreateLabelResponse
import org.astermail.android.api.labels.LabelItem
import org.astermail.android.api.labels.LabelsApi
import org.astermail.android.api.labels.LabelsListResponse
import org.astermail.android.storage.SessionKeyStore
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class SystemFolderBootstrapTest {
    private lateinit var labels_api: LabelsApi
    private lateinit var session_key_store: SessionKeyStore
    private lateinit var context: Context
    private lateinit var bootstrap: SystemFolderBootstrap

    @Before
    fun setup() {
        mockkStatic(Base64::class)
        every { Base64.encodeToString(any(), any()) } answers {
            java.util.Base64.getEncoder().encodeToString(firstArg())
        }
        labels_api = mockk(relaxed = true)
        session_key_store = mockk(relaxed = true)
        context = mockk(relaxed = true)
        every { context.getString(any()) } answers { "folder_" + firstArg<Int>() }
        every { session_key_store.get_identity_key() } returns "test_identity_key"
        coEvery { labels_api.create_label(any()) } returns CreateLabelResponse(id = "new", label_token = "ignored")
        bootstrap = SystemFolderBootstrap(labels_api, session_key_store, context)
    }

    @After
    fun teardown() {
        unmockkStatic(Base64::class)
    }

    @Test
    fun `creates only the missing system folders`() = runTest {
        coEvery { labels_api.list_labels(include_counts = false) } returns LabelsListResponse(
            labels = listOf(
                LabelItem(id = "l1", label_token = "inbox_token", is_system = true, folder_type = "inbox"),
                LabelItem(id = "l2", label_token = "custom_token", folder_type = "folder"),
            ),
        )

        val tokens = bootstrap.ensure_system_folders()

        val requests = mutableListOf<CreateLabelRequest>()
        coVerify(exactly = 5) { labels_api.create_label(capture(requests)) }
        assertEquals(listOf("sent", "drafts", "trash", "spam", "archive"), requests.map { it.folder_type })
        assertEquals("inbox_token", tokens["inbox"])
        assertEquals(requests.first { it.folder_type == "sent" }.label_token, tokens["sent"])
        assertTrue(requests.all { it.encrypted_name.isNotBlank() && it.name_nonce.isNotBlank() })
    }

    @Test
    fun `does nothing when every system folder exists`() = runTest {
        coEvery { labels_api.list_labels(include_counts = false) } returns LabelsListResponse(
            labels = SYSTEM_FOLDER_SPECS.map {
                LabelItem(id = it.folder_type, label_token = it.folder_type + "_token", is_system = true, folder_type = it.folder_type)
            },
        )

        val tokens = bootstrap.ensure_system_folders()

        coVerify(exactly = 0) { labels_api.create_label(any()) }
        assertEquals("sent_token", tokens["sent"])
    }

    @Test
    fun `keeps the other folders when one creation fails`() = runTest {
        coEvery { labels_api.list_labels(include_counts = false) } returns LabelsListResponse(labels = emptyList())
        val request = slot<CreateLabelRequest>()
        coEvery { labels_api.create_label(capture(request)) } answers {
            if (request.captured.folder_type == "drafts") throw RuntimeException("server error")
            CreateLabelResponse(id = "new", label_token = "ignored")
        }

        val tokens = bootstrap.ensure_system_folders()

        assertEquals(setOf("inbox", "sent", "trash", "spam", "archive"), tokens.keys)
    }

    @Test
    fun `skips creation without an identity key`() = runTest {
        every { session_key_store.get_identity_key() } returns null
        coEvery { labels_api.list_labels(include_counts = false) } returns LabelsListResponse(labels = emptyList())

        val tokens = bootstrap.ensure_system_folders()

        coVerify(exactly = 0) { labels_api.create_label(any()) }
        assertTrue(tokens.isEmpty())
    }
}
