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

package org.astermail.android.devices

import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.astermail.android.R
import org.astermail.android.api.devices.DeviceCodeApi
import org.astermail.android.api.devices.DeviceCodeConfirmResponse
import org.astermail.android.api.devices.DeviceLinkError
import org.astermail.android.api.devices.PendingDevice
import org.astermail.android.crypto.DeviceEnvelope
import org.astermail.android.crypto.ratchet.RatchetCrypto
import org.astermail.android.storage.SessionKeyStore
import org.astermail.android.ui.settings.detail.LinkDeviceScreen
import org.bouncycastle.crypto.params.X25519PrivateKeyParameters
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class LinkDeviceScreenInstrumentedTest {

    @get:Rule
    val compose = createComposeRule()

    private val context = InstrumentationRegistry.getInstrumentation().targetContext
    private val passphrase = "vault-passphrase-under-test".toByteArray(Charsets.UTF_8)

    private val mlkem = RatchetCrypto.ml_kem_768_generate_keypair()
    private val x25519_sk = RatchetCrypto.random_bytes(32)
    private val x25519_pk = X25519PrivateKeyParameters(x25519_sk, 0).generatePublicKey().encoded

    private class FakeApi(
        private val device: PendingDevice,
        private val verify_error: Throwable? = null,
        private val confirm_error: Throwable? = null,
    ) : DeviceCodeApi {
        var verified_code: String? = null
        var confirmed_envelope: String? = null

        override suspend fun verify_code(code: String): PendingDevice {
            verified_code = code
            verify_error?.let { throw it }
            return device
        }

        override suspend fun confirm_code(
            code: String,
            sealed_envelope: String,
        ): DeviceCodeConfirmResponse {
            confirmed_envelope = sealed_envelope
            confirm_error?.let { throw it }
            return DeviceCodeConfirmResponse(device_id = "dev_1", machine_name = device.machine_name)
        }
    }

    private fun pending(device_type: String = "bridge") = PendingDevice(
        ed25519_pk = DeviceEnvelope.base64url_encode(RatchetCrypto.random_bytes(32)),
        mlkem_pk = DeviceEnvelope.base64url_encode(mlkem.public_key),
        x25519_pk = DeviceEnvelope.base64url_encode(x25519_pk),
        machine_name = "adam-desktop",
        device_type = device_type,
    )

    private fun session_store(with_passphrase: Boolean = true): SessionKeyStore {
        val store = SessionKeyStore(context)
        if (with_passphrase) store.put_passphrase(passphrase) else store.clear_all()
        return store
    }

    private fun show(api: DeviceCodeApi, store: SessionKeyStore) {
        compose.setContent {
            LinkDeviceScreen(on_back = {}, vm = LinkDeviceViewModel(api, store))
        }
    }

    private fun string(id: Int) = context.getString(id)

    private fun enter_code_and_continue(code: String = "qeme77et") {
        compose.onNode(hasSetTextAction()).performTextInput(code)
        compose.onNodeWithText(string(R.string.link_device_continue)).performClick()
    }

    @Test
    fun a_user_links_a_device_and_the_device_can_open_the_envelope() {
        val api = FakeApi(pending())
        show(api, session_store())

        enter_code_and_continue()

        compose.waitUntil(5_000) {
            compose.onAllNodesWithTextSafe("adam-desktop").isNotEmpty()
        }
        compose.onNodeWithText(string(R.string.link_device_type_bridge)).assertExists()
        compose.onNodeWithText(string(R.string.link_device_confirm_action)).performClick()

        compose.waitUntil(5_000) {
            compose.onAllNodesWithTextSafe(string(R.string.link_device_success_title)).isNotEmpty()
        }

        assertEquals("QEME-77ET", api.verified_code)
        val envelope = requireNonNull(api.confirmed_envelope)
        assertArrayEquals(
            passphrase,
            DeviceEnvelope.open_secret_for_device(
                DeviceEnvelope.base64url_decode(envelope),
                mlkem.secret_key,
                x25519_sk,
            ),
        )
    }

    @Test
    fun an_expired_code_shows_the_expired_message_and_returns_to_the_input_step() {
        val api = FakeApi(pending(), verify_error = DeviceLinkError.CodeNotFound)
        show(api, session_store())

        enter_code_and_continue()

        compose.waitUntil(5_000) {
            compose.onAllNodesWithTextSafe(string(R.string.link_device_error_expired)).isNotEmpty()
        }
        compose.onNodeWithText(string(R.string.link_device_continue)).assertExists()
        assertNull(api.confirmed_envelope)
    }

    @Test
    fun bridge_without_a_plan_shows_the_upgrade_message() {
        val api = FakeApi(pending(), verify_error = DeviceLinkError.PlanUpgradeRequired)
        show(api, session_store())

        enter_code_and_continue()

        compose.waitUntil(5_000) {
            compose.onAllNodesWithTextSafe(
                string(R.string.link_device_error_upgrade_required),
            ).isNotEmpty()
        }
        assertNull(api.confirmed_envelope)
    }

    @Test
    fun a_locked_vault_blocks_the_confirm_step_and_sends_nothing() {
        val api = FakeApi(pending("desktop"))
        show(api, session_store(with_passphrase = false))

        enter_code_and_continue()
        compose.waitUntil(5_000) {
            compose.onAllNodesWithTextSafe(string(R.string.link_device_confirm_action)).isNotEmpty()
        }
        compose.onNodeWithText(string(R.string.link_device_confirm_action)).performClick()

        compose.waitUntil(5_000) {
            compose.onAllNodesWithTextSafe(string(R.string.link_device_error_session)).isNotEmpty()
        }
        assertNull(api.confirmed_envelope)
    }

    private fun <T : Any> requireNonNull(value: T?): T {
        if (value == null) throw AssertionError("expected a value")
        return value
    }
}
