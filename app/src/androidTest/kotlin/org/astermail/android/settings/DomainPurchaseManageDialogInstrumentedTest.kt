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

package org.astermail.android.settings

import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.astermail.android.ComposeActivity
import org.astermail.android.R
import org.astermail.android.api.domains.DomainOrder
import org.astermail.android.design.AsterTheme
import org.astermail.android.ui.settings.detail.domain_purchase_manage_dialog
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class DomainPurchaseManageDialogInstrumentedTest {

    @get:Rule
    val compose = createComposeRule()

    private val context = InstrumentationRegistry.getInstrumentation().targetContext

    private val order = DomainOrder(
        id = "order-1",
        domain = "example-under-test.com",
        status = "complete",
        order_type = "registration",
        years = 2,
        price_cents = 2499,
        currency = "usd",
        expires_at = "2028-01-15T00:00:00Z",
        created_at = "2026-01-15T00:00:00Z",
    )

    @Test
    fun shows_registration_detail_and_support_path() {
        var renew_calls = 0
        var dismiss_calls = 0

        compose.setContent {
            AsterTheme {
                domain_purchase_manage_dialog(
                    order = order,
                    renewing = false,
                    renew_error = null,
                    on_renew = { renew_calls += 1 },
                    on_dismiss = { dismiss_calls += 1 },
                )
            }
        }

        compose.onNodeWithText(order.domain).assertExists()
        compose.onNodeWithText(context.getString(R.string.domain_purchase_manage_status)).assertExists()
        compose.onNodeWithText(context.getString(R.string.domain_purchase_manage_status_active)).assertExists()
        compose.onNodeWithText(context.getString(R.string.domain_purchase_manage_registered)).assertExists()
        compose.onNodeWithText(context.getString(R.string.domain_purchase_manage_expires)).assertExists()
        compose.onNodeWithText(context.getString(R.string.domain_purchase_manage_term)).assertExists()
        compose.onNodeWithText(context.getString(R.string.domain_purchase_manage_paid)).assertExists()
        compose.onNodeWithText(context.getString(R.string.domain_purchase_n_years, 2)).assertExists()
        compose.onNodeWithText(context.getString(R.string.domain_purchase_manage_auto_renew_note)).assertExists()
        compose.onNodeWithText(context.getString(R.string.domain_purchase_manage_support_note)).assertExists()
        compose.onNodeWithText(context.getString(R.string.contact_support)).assertExists()

        compose.onNodeWithText(context.getString(R.string.domain_purchase_renew)).performClick()
        compose.waitForIdle()
        assertEquals(1, renew_calls)

        compose.onNodeWithText(context.getString(R.string.close)).performClick()
        compose.waitForIdle()
        assertEquals(1, dismiss_calls)
    }

    @Test
    fun marks_a_registration_inside_the_window_as_expiring() {
        val soon = java.text.SimpleDateFormat("yyyy-MM-dd", java.util.Locale.US)
            .format(java.util.Date(System.currentTimeMillis() + 5L * 86_400_000L))

        compose.setContent {
            AsterTheme {
                domain_purchase_manage_dialog(
                    order = order.copy(expires_at = soon),
                    renewing = false,
                    renew_error = null,
                    on_renew = {},
                    on_dismiss = {},
                )
            }
        }

        compose.onNodeWithText(context.getString(R.string.domain_purchase_manage_status_expiring)).assertExists()
    }

    @Test
    fun support_intent_targets_the_support_address() {
        val intent = ComposeActivity.intent_for(context, prefill_to = "hello@astermail.org")

        assertEquals("hello@astermail.org", intent.getStringExtra("prefill_to"))
        assertEquals(ComposeActivity::class.java.name, intent.component?.className)
        assertTrue(intent.dataString.orEmpty().endsWith("hello%40astermail.org"))
    }
}
