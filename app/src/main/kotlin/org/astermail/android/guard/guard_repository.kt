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

package org.astermail.android.guard

import androidx.fragment.app.FragmentActivity
import java.util.UUID
import org.astermail.android.api.api_error_code
import org.astermail.android.api.GUARD_DEVICE_REQUIRED_CODE
import org.astermail.android.api.guard.GuardApi
import org.astermail.android.api.guard.SignedAction
import org.astermail.android.api.guard.StepUpProof
import org.astermail.android.crypto.DeviceEnvelope
import org.astermail.android.crypto.guard.GuardActionCode
import org.astermail.android.crypto.guard.GuardDigests

class GuardKeyLostException : IllegalStateException("guard key lost")

class GuardNoDeviceException : IllegalStateException("guard device required")

class GuardNoLockScreenException : IllegalStateException("guard needs a screen lock")

class GuardSignFailedException(val reason: String) : IllegalStateException("guard sign failed")

data class guard_prompt_text(val title: String, val subtitle: String)

fun interface guard_signer {
    suspend fun sign(message: ByteArray): guard_sign_result

    fun with_alias(alias: String): guard_signer = this
}

const val UNBOUND_SIGNER_REASON = "unbound"

fun keystore_signer(activity: FragmentActivity, prompt: guard_prompt_text, alias: String? = null): guard_signer =
    object : guard_signer {
        override suspend fun sign(message: ByteArray): guard_sign_result =
            if (alias == null) {
                guard_sign_result.failed(UNBOUND_SIGNER_REASON)
            } else {
                guard_sign(activity, alias, message, prompt.title, prompt.subtitle)
            }

        override fun with_alias(alias: String): guard_signer = keystore_signer(activity, prompt, alias)
    }

fun is_guard_device_required(t: Throwable): Boolean = api_error_code(t) == GUARD_DEVICE_REQUIRED_CODE

class GuardRepository(
    private val api: GuardApi,
    private val store: GuardDeviceStore,
    private val user_id: () -> UUID,
) {

    val device_store: GuardDeviceStore get() = store

    suspend fun signed(
        activity: FragmentActivity,
        code: GuardActionCode,
        subject: ByteArray,
        prompt: guard_prompt_text,
        proof: StepUpProof? = null,
    ): SignedAction? = signed_with(keystore_signer(activity, prompt), code, subject, proof)

    suspend fun signed_with(
        signer: guard_signer,
        code: GuardActionCode,
        subject: ByteArray,
        proof: StepUpProof? = null,
    ): SignedAction? {
        val challenge = try {
            api.challenge()
        } catch (t: Throwable) {
            if (is_guard_device_required(t)) throw GuardNoDeviceException()
            throw t
        }
        val nonce = DeviceEnvelope.base64url_decode(challenge.nonce)
        val user = user_id()
        val message = GuardDigests.action_message(code, user, nonce, subject)
        val alias = store.action_alias(user, user)
        return when (val result = signer.with_alias(alias).sign(message)) {
            is guard_sign_result.signed -> SignedAction(
                nonce = challenge.nonce,
                signature = DeviceEnvelope.base64url_encode(result.der),
                proof = proof,
            )
            guard_sign_result.canceled -> null
            guard_sign_result.key_lost -> throw GuardKeyLostException()
            guard_sign_result.no_lock_screen -> throw GuardNoLockScreenException()
            is guard_sign_result.failed -> throw GuardSignFailedException(result.reason)
        }
    }
}
