// AGPL-3.0 - Aster Communications Inc. 2026

package org.astermail.android.ui.auth

import android.app.Application
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import io.mockk.mockk
import org.astermail.android.R
import org.astermail.android.auth.AuthRepository
import org.astermail.android.auth.AuthViewModel
import org.astermail.android.auth.TotpChallenge
import org.astermail.android.design.AsterTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class PasskeyOnlyRecoveryFallbackTest {

    @get:Rule
    val compose_rule = createComposeRule()

    private val app: Application = ApplicationProvider.getApplicationContext()

    private fun text(id: Int): String = app.getString(id)

    private fun challenge(methods: List<String>) = TotpChallenge(
        pending_login_token = "pending",
        available_methods = methods,
        password_hash_bytes = ByteArray(0),
        password_bytes = ByteArray(0),
        salt_bytes = ByteArray(0),
        email = "user@astermail.org",
        remember_me = false,
    )

    private fun show(methods: List<String>, on_reset: () -> Unit) {
        val view_model = AuthViewModel(app, mockk<AuthRepository>(relaxed = true))
        compose_rule.setContent {
            AsterTheme {
                TotpVerifyScreen(
                    challenge = challenge(methods),
                    view_model = view_model,
                    on_back = {},
                    on_reset_with_recovery_code = on_reset,
                )
            }
        }
    }

    @Test
    fun passkey_only_account_offers_recovery_code_reset() {
        var resets = 0
        show(listOf("webauthn")) { resets++ }

        compose_rule.onNodeWithText(text(R.string.totp_passkey_only_recovery_hint)).assertIsDisplayed()
        compose_rule.onAllNodesWithText(text(R.string.totp_use_backup_code)).assertCountEquals(0)
        compose_rule.onNodeWithText(text(R.string.totp_reset_with_recovery_code)).performClick()
        compose_rule.runOnIdle { assertEquals(1, resets) }
    }

    @Test
    fun recovery_code_in_backup_field_routes_to_reset() {
        var resets = 0
        show(listOf("totp", "webauthn")) { resets++ }

        compose_rule.onNodeWithText(text(R.string.totp_use_backup_code)).performClick()
        compose_rule.onNodeWithText(text(R.string.totp_backup_code_label)).performTextInput("ASTER-7KQ2-M9XD-P4LA-83TN")
        compose_rule.onNodeWithText(text(R.string.totp_recovery_code_detected)).assertIsDisplayed()
        compose_rule.onAllNodesWithText(text(R.string.totp_verify_button)).assertCountEquals(0)
        compose_rule.onNodeWithText(text(R.string.totp_reset_with_recovery_code)).performClick()
        compose_rule.runOnIdle { assertEquals(1, resets) }
    }

    @Test
    fun backup_code_still_verifies_normally() {
        var resets = 0
        show(listOf("totp")) { resets++ }

        compose_rule.onNodeWithText(text(R.string.totp_use_backup_code)).performClick()
        compose_rule.onNodeWithText(text(R.string.totp_backup_code_label)).performTextInput("A1B2C3D4E5F6")
        compose_rule.onAllNodesWithText(text(R.string.totp_recovery_code_detected)).assertCountEquals(0)
        compose_rule.onAllNodesWithText(text(R.string.totp_passkey_only_recovery_hint)).assertCountEquals(0)
        compose_rule.onNodeWithText(text(R.string.totp_verify_button)).assertIsDisplayed()
        compose_rule.runOnIdle { assertEquals(0, resets) }
    }
}
