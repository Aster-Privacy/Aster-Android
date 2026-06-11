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

package org.astermail.android.api

import org.astermail.android.api.labels.CreateLabelRequest
import org.astermail.android.api.labels.CreateLabelResponse
import org.astermail.android.api.labels.LabelItem
import org.astermail.android.api.labels.LabelsListResponse
import org.astermail.android.api.labels.ReferralInfoResponse
import org.astermail.android.api.labels.UpdateLabelRequest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class LabelsModelsTest {

    @Test
    fun `LabelItem required fields`() {
        val label = LabelItem(id = "l1", label_token = "lt_1")
        assertEquals("l1", label.id)
        assertEquals("lt_1", label.label_token)
        assertNull(label.encrypted_name)
        assertNull(label.name_nonce)
        assertNull(label.encrypted_color)
        assertNull(label.color_nonce)
        assertNull(label.encrypted_icon)
        assertNull(label.icon_nonce)
        assertFalse(label.is_system)
        assertFalse(label.is_locked)
        assertEquals("custom", label.folder_type)
        assertEquals(0, label.sort_order)
        assertNull(label.parent_token)
        assertNull(label.item_count)
        assertNull(label.created_at)
        assertNull(label.updated_at)
    }

    @Test
    fun `LabelItem with all fields`() {
        val label = LabelItem(
            id = "l1",
            label_token = "lt_1",
            encrypted_name = "enc_name",
            name_nonce = "nn",
            encrypted_color = "enc_color",
            color_nonce = "cn",
            encrypted_icon = "enc_icon",
            icon_nonce = "in_",
            is_system = true,
            is_locked = true,
            folder_type = "folder",
            sort_order = 5,
            parent_token = "pt_parent",
            item_count = 42L,
            created_at = "2026-01-01T00:00:00Z",
            updated_at = "2026-04-26T00:00:00Z",
        )
        assertEquals("enc_name", label.encrypted_name)
        assertEquals("enc_color", label.encrypted_color)
        assertEquals("enc_icon", label.encrypted_icon)
        assertTrue(label.is_system)
        assertTrue(label.is_locked)
        assertEquals("folder", label.folder_type)
        assertEquals(5, label.sort_order)
        assertEquals("pt_parent", label.parent_token)
        assertEquals(42L, label.item_count)
    }

    @Test
    fun `LabelItem copy`() {
        val original = LabelItem(id = "l1", label_token = "lt_1")
        val copied = original.copy(
            encrypted_name = "Work",
            is_system = true,
            sort_order = 10,
        )
        assertEquals("Work", copied.encrypted_name)
        assertTrue(copied.is_system)
        assertEquals(10, copied.sort_order)
        assertEquals("l1", copied.id)
    }

    @Test
    fun `LabelsListResponse defaults`() {
        val response = LabelsListResponse()
        assertTrue(response.labels.isEmpty())
        assertEquals(0L, response.total)
        assertFalse(response.has_more)
    }

    @Test
    fun `LabelsListResponse with labels`() {
        val labels = listOf(
            LabelItem(id = "l1", label_token = "lt_1"),
            LabelItem(id = "l2", label_token = "lt_2"),
        )
        val response = LabelsListResponse(
            labels = labels,
            total = 25,
            has_more = true,
        )
        assertEquals(2, response.labels.size)
        assertEquals(25L, response.total)
        assertTrue(response.has_more)
    }

    @Test
    fun `LabelsListResponse with empty labels`() {
        val response = LabelsListResponse(labels = emptyList(), total = 0, has_more = false)
        assertTrue(response.labels.isEmpty())
        assertEquals(0L, response.total)
    }

    @Test
    fun `CreateLabelRequest required fields`() {
        val request = CreateLabelRequest(
            label_token = "lt_new",
            encrypted_name = "enc_n",
            name_nonce = "nn",
        )
        assertEquals("lt_new", request.label_token)
        assertEquals("enc_n", request.encrypted_name)
        assertEquals("nn", request.name_nonce)
        assertNull(request.encrypted_color)
        assertNull(request.color_nonce)
        assertNull(request.encrypted_icon)
        assertNull(request.icon_nonce)
        assertEquals("label", request.folder_type)
        assertNull(request.sort_order)
        assertNull(request.parent_token)
    }

    @Test
    fun `CreateLabelRequest with all fields`() {
        val request = CreateLabelRequest(
            label_token = "lt_new",
            encrypted_name = "enc_n",
            name_nonce = "nn",
            encrypted_color = "enc_c",
            color_nonce = "cn",
            encrypted_icon = "enc_i",
            icon_nonce = "in_",
            folder_type = "folder",
            sort_order = 3,
            parent_token = "pt_parent",
        )
        assertEquals("enc_c", request.encrypted_color)
        assertEquals("folder", request.folder_type)
        assertEquals(3, request.sort_order)
        assertEquals("pt_parent", request.parent_token)
    }

    @Test
    fun `CreateLabelRequest copy`() {
        val original = CreateLabelRequest(
            label_token = "lt", encrypted_name = "en", name_nonce = "nn",
        )
        val copied = original.copy(folder_type = "folder", sort_order = 5)
        assertEquals("folder", copied.folder_type)
        assertEquals(5, copied.sort_order)
        assertEquals("lt", copied.label_token)
    }

    @Test
    fun `CreateLabelResponse defaults`() {
        val response = CreateLabelResponse()
        assertNull(response.id)
        assertNull(response.label_token)
        assertFalse(response.success)
    }

    @Test
    fun `CreateLabelResponse with values`() {
        val response = CreateLabelResponse(
            id = "l_new",
            label_token = "lt_new",
            success = true,
        )
        assertEquals("l_new", response.id)
        assertEquals("lt_new", response.label_token)
        assertTrue(response.success)
    }

    @Test
    fun `UpdateLabelRequest defaults all null`() {
        val request = UpdateLabelRequest()
        assertNull(request.encrypted_name)
        assertNull(request.name_nonce)
        assertNull(request.encrypted_color)
        assertNull(request.color_nonce)
        assertNull(request.encrypted_icon)
        assertNull(request.icon_nonce)
        assertNull(request.sort_order)
    }

    @Test
    fun `UpdateLabelRequest with selective fields`() {
        val request = UpdateLabelRequest(
            encrypted_name = "new_enc_name",
            name_nonce = "new_nn",
            sort_order = 7,
        )
        assertEquals("new_enc_name", request.encrypted_name)
        assertEquals("new_nn", request.name_nonce)
        assertNull(request.encrypted_color)
        assertEquals(7, request.sort_order)
    }

    @Test
    fun `UpdateLabelRequest copy`() {
        val original = UpdateLabelRequest(encrypted_name = "old")
        val copied = original.copy(encrypted_color = "new_color", color_nonce = "cn")
        assertEquals("old", copied.encrypted_name)
        assertEquals("new_color", copied.encrypted_color)
        assertEquals("cn", copied.color_nonce)
    }

    @Test
    fun `ReferralInfoResponse defaults`() {
        val info = ReferralInfoResponse()
        assertEquals("", info.referral_link)
        assertEquals("", info.referral_code)
        assertEquals(0L, info.total_referrals)
        assertEquals(0L, info.pending_referrals)
        assertEquals(0L, info.completed_referrals)
        assertEquals(0L, info.months_earned)
        assertEquals(0L, info.credit_balance_cents)
    }

    @Test
    fun `ReferralInfoResponse with values`() {
        val info = ReferralInfoResponse(
            referral_link = "https://astermail.org/ref/ABC123",
            referral_code = "ABC123",
            total_referrals = 10,
            pending_referrals = 3,
            completed_referrals = 7,
            months_earned = 14,
            credit_balance_cents = 4900,
        )
        assertEquals("https://astermail.org/ref/ABC123", info.referral_link)
        assertEquals("ABC123", info.referral_code)
        assertEquals(10L, info.total_referrals)
        assertEquals(3L, info.pending_referrals)
        assertEquals(7L, info.completed_referrals)
        assertEquals(14L, info.months_earned)
        assertEquals(4900L, info.credit_balance_cents)
    }

    @Test
    fun `ReferralInfoResponse copy`() {
        val original = ReferralInfoResponse(referral_code = "XYZ")
        val copied = original.copy(total_referrals = 5, months_earned = 10)
        assertEquals("XYZ", copied.referral_code)
        assertEquals(5L, copied.total_referrals)
        assertEquals(10L, copied.months_earned)
    }

    @Test
    fun `LabelItem equality`() {
        val a = LabelItem(id = "l1", label_token = "lt_1", folder_type = "label")
        val b = LabelItem(id = "l1", label_token = "lt_1", folder_type = "label")
        assertEquals(a, b)
        assertEquals(a.hashCode(), b.hashCode())
    }

    @Test
    fun `ReferralInfoResponse equality`() {
        val a = ReferralInfoResponse(referral_code = "A", total_referrals = 5)
        val b = ReferralInfoResponse(referral_code = "A", total_referrals = 5)
        assertEquals(a, b)
    }
}
