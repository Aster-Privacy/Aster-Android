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

package org.astermail.android.ui.security

import androidx.biometric.BiometricManager.Authenticators.BIOMETRIC_STRONG
import androidx.biometric.BiometricPrompt
import androidx.core.content.ContextCompat
import androidx.fragment.app.FragmentActivity
import org.astermail.android.R
import org.astermail.android.security.BiometricEnrollOrigin
import org.astermail.android.security.BiometricGatePreparation
import org.astermail.android.security.BiometricUnlockGate
import org.astermail.android.security.may_enroll_biometric_unlock

fun launch_biometric_enroll(
    activity: FragmentActivity,
    origin: BiometricEnrollOrigin,
    pin_verified: Boolean,
    biometric_available: Boolean,
    on_finished: (Boolean) -> Unit,
) {
    if (!may_enroll_biometric_unlock(origin, pin_verified, biometric_available)) {
        on_finished(false)
        return
    }
    val app_context = activity.applicationContext
    val preparation = BiometricUnlockGate.prepare_enroll(app_context)
    val cipher = (preparation as? BiometricGatePreparation.Enroll)?.cipher
    if (cipher == null) {
        on_finished(false)
        return
    }
    val info = BiometricPrompt.PromptInfo.Builder()
        .setTitle(activity.getString(R.string.app_lock_use_biometric))
        .setSubtitle(activity.getString(R.string.app_lock_biometric_subtitle))
        .setNegativeButtonText(activity.getString(R.string.cancel))
        .setAllowedAuthenticators(BIOMETRIC_STRONG)
        .setConfirmationRequired(false)
        .build()
    val callback = object : BiometricPrompt.AuthenticationCallback() {
        override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) {
            val authenticated_cipher = result.cryptoObject?.cipher
            val ok = authenticated_cipher != null &&
                BiometricUnlockGate.complete_enroll(app_context, authenticated_cipher)
            if (!ok) BiometricUnlockGate.reset(app_context)
            on_finished(ok)
        }

        override fun onAuthenticationError(code: Int, message: CharSequence) {
            BiometricUnlockGate.reset(app_context)
            on_finished(false)
        }
    }
    try {
        BiometricPrompt(activity, ContextCompat.getMainExecutor(activity), callback)
            .authenticate(info, BiometricPrompt.CryptoObject(cipher))
    } catch (t: Throwable) {
        BiometricUnlockGate.reset(app_context)
        on_finished(false)
    }
}
