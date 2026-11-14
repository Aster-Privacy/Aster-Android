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

package org.astermail.android.billing

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class CancelFlowTest {

    @Test
    fun `no retention step when nothing is offered`() {
        assertNull(retention_step_for(has_save_offer = false, has_plan_offer = false))
    }

    @Test
    fun `save offer comes before the plan offer`() {
        assertEquals(CancelStep.save, retention_step_for(has_save_offer = true, has_plan_offer = true))
        assertEquals(CancelStep.offer, retention_step_for(has_save_offer = false, has_plan_offer = true))
    }

    @Test
    fun `back from confirm returns to the reason when the user cancelled from it`() {
        assertEquals(CancelStep.reason, step_before_confirm(CancelStep.reason, has_save_offer = true, has_plan_offer = true))
    }

    @Test
    fun `back from confirm returns to the offer the user was on`() {
        assertEquals(CancelStep.save, step_before_confirm(CancelStep.save, has_save_offer = true, has_plan_offer = true))
        assertEquals(CancelStep.offer, step_before_confirm(CancelStep.offer, has_save_offer = true, has_plan_offer = true))
    }

    @Test
    fun `back from confirm falls back when the offer is gone`() {
        assertEquals(CancelStep.offer, step_before_confirm(CancelStep.save, has_save_offer = false, has_plan_offer = true))
        assertEquals(CancelStep.reason, step_before_confirm(CancelStep.offer, has_save_offer = false, has_plan_offer = false))
    }
}
