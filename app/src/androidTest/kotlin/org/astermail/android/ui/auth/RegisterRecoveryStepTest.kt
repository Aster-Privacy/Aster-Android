// AGPL-3.0 - Aster Communications Inc. 2026

package org.astermail.android.ui.auth

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.astermail.android.design.AsterTheme
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class RegisterRecoveryStepTest {

    @get:Rule
    val compose_rule = createComposeRule()

    private val test_codes = listOf(
        "ASTER-A2B3-C4D5-E6F7",
        "ASTER-G8H9-J2K3-L4M5",
        "ASTER-N6P7-Q8R9-S2T3",
        "ASTER-U4V5-W6X7-Y8Z9",
        "ASTER-2A3B-4C5D-6E7F",
        "ASTER-8G9H-2J3K-4L5M",
    )

    @Test
    fun shows_six_aster_codes() {
        compose_rule.setContent {
            AsterTheme {
                RegisterRecoveryStep(
                    codes = test_codes,
                    on_continue = {},
                )
            }
        }

        test_codes.forEach { code ->
            compose_rule.onNodeWithText(code).assertIsDisplayed()
        }
    }

    @Test
    fun shows_all_six_codes_numbered() {
        compose_rule.setContent {
            AsterTheme {
                RegisterRecoveryStep(
                    codes = test_codes,
                    on_continue = {},
                )
            }
        }

        compose_rule.onNodeWithText("1.").assertIsDisplayed()
        compose_rule.onNodeWithText("6.").assertIsDisplayed()
        test_codes.forEach { code ->
            compose_rule.onNodeWithText(code).assertIsDisplayed()
        }
    }
}
