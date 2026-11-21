//
// Aster Communications Inc.
//
// Copyright (c) 2026 Aster Communications Inc.
//
// This file is part of this project.
//
// This program is free software: you can redistribute it and/or modify
// it under the terms of the AGPLv3 as published by
// the Free Software Foundation, either version 3 of the License, or
// (at your option) any later version.
//
// This program is distributed in the hope that it will be useful,
// but WITHOUT ANY WARRANTY; without even the implied warranty of
// MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
// AGPLv3 for more details.
//
// You should have received a copy of the AGPLv3
// along with this program. If not, see <https://www.gnu.org/licenses/>.
//
package org.astermail.android.guard

import java.security.MessageDigest
import javax.inject.Inject
import kotlinx.coroutines.CancellationException
import org.astermail.android.api.devices.PendingDevice
import org.astermail.android.api.guard.GuardApi
import org.astermail.android.api.guard.GuardSwitch
import org.astermail.android.api.guard.SignedAction
import org.astermail.android.crypto.DeviceEnvelope
import org.astermail.android.crypto.guard.GuardActionCode
import org.astermail.android.crypto.guard.GuardDigests
import org.astermail.android.ui.guard.guard_current_user

sealed interface guard_link_outcome {
    object off : guard_link_outcome
    object canceled : guard_link_outcome
    object keys_mismatch : guard_link_outcome
    object needs_device : guard_link_outcome
    data class signed(val action: SignedAction) : guard_link_outcome
}

fun guard_link_keys_digest(device: PendingDevice): ByteArray? = runCatching {
    val ed = DeviceEnvelope.base64url_decode(device.ed25519_pk)
    val mlkem = DeviceEnvelope.base64url_decode(device.mlkem_pk)
    val x = DeviceEnvelope.base64url_decode(device.x25519_pk)
    val action = device.action_pk?.takeIf { it.isNotBlank() }?.let { DeviceEnvelope.base64url_decode(it) }
        ?: GuardDigests.ABSENT_ACTION_PK
    val digest = GuardDigests.keys_digest(ed, mlkem, x, action)
    val claimed = DeviceEnvelope.base64url_decode(device.keys_digest)
    if (!MessageDigest.isEqual(digest, claimed)) null else digest
}.getOrNull()

class GuardLinkApprover @Inject constructor(
    private val api: GuardApi,
    private val store: GuardDeviceStore,
    private val user: guard_current_user,
) {
    suspend fun approve(device: PendingDevice, signer: guard_signer?): guard_link_outcome {
        val status = try {
            api.status()
        } catch (c: CancellationException) {
            throw c
        } catch (_: Throwable) {
            return guard_link_outcome.off
        }
        if (status.switches and GuardSwitch.NEW_DEVICE_APPROVAL == 0) return guard_link_outcome.off
        val digest = guard_link_keys_digest(device) ?: return guard_link_outcome.keys_mismatch
        if (signer == null) return guard_link_outcome.needs_device
        val user_id = user.user_id() ?: return guard_link_outcome.needs_device
        val repository = GuardRepository(api, store) { user_id }
        val action = try {
            repository.signed_with(
                signer,
                GuardActionCode.APPROVE,
                GuardDigests.approve_subject(null, digest, 0),
            )
        } catch (_: GuardNoDeviceException) {
            return guard_link_outcome.needs_device
        } catch (_: GuardKeyLostException) {
            return guard_link_outcome.needs_device
        } ?: return guard_link_outcome.canceled
        return guard_link_outcome.signed(action)
    }
}
