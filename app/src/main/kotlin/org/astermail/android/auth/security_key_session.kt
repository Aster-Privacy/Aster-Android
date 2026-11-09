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

package org.astermail.android.auth

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.hardware.usb.UsbConstants
import android.hardware.usb.UsbDevice
import android.hardware.usb.UsbManager
import android.nfc.NfcAdapter
import android.os.Build
import android.os.SystemClock
import android.provider.Settings
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner
import com.yubico.yubikit.android.YubiKitManager
import com.yubico.yubikit.android.transport.nfc.NfcConfiguration
import com.yubico.yubikit.android.transport.nfc.NfcYubiKeyDevice
import com.yubico.yubikit.android.transport.usb.DeviceFilter
import com.yubico.yubikit.android.transport.usb.UsbConfiguration
import com.yubico.yubikit.android.transport.usb.UsbYubiKeyDevice
import com.yubico.yubikit.core.YubiKeyConnection
import com.yubico.yubikit.core.YubiKeyDevice
import com.yubico.yubikit.core.application.CommandState
import com.yubico.yubikit.core.fido.FidoConnection
import com.yubico.yubikit.core.smartcard.SmartCardConnection
import java.security.MessageDigest
import java.util.Base64
import kotlin.coroutines.cancellation.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.async
import kotlinx.coroutines.asExecutor
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import org.astermail.android.R
import org.astermail.android.api.auth.WebAuthnAssertionOptions

enum class SecurityKeyNfcStatus { ready, off, missing }

data class SecurityKeyTransports(val nfc: SecurityKeyNfcStatus, val usb: Boolean) {
    val any: Boolean get() = usb || nfc != SecurityKeyNfcStatus.missing
}

sealed interface SecurityKeyUiState {
    data object Idle : SecurityKeyUiState
    data class Waiting(
        val transports: SecurityKeyTransports,
        val tap_again: Boolean,
        val from_fallback: Boolean,
    ) : SecurityKeyUiState
    data object Working : SecurityKeyUiState
    data class NeedPin(val retries: Int?, val wrong: Boolean) : SecurityKeyUiState
}

private const val android_origin_prefix = "android:apk-key-hash:"
private const val yubico_vendor_id = 0x1050
private const val nfc_timeout_ms = 5000
private const val usb_scan_interval_ms = 1000L
private const val security_key_log_tag = "AsterSecurityKey"

private fun describe_failure(failure: Throwable): String =
    generateSequence(failure) { it.cause }.take(4).joinToString(" <- ") { "${it.javaClass.simpleName}: ${it.message}" }

fun security_key_error_message(cause: Throwable): Int? = when (cause) {
    is SecurityKeyException.PinBlocked -> R.string.security_key_error_pin_blocked
    is SecurityKeyException.PinAuthBlocked -> R.string.security_key_error_pin_auth_blocked
    is SecurityKeyException.PinNotSet -> R.string.security_key_error_pin_not_set
    is SecurityKeyException.NotRegistered -> R.string.security_key_error_not_registered
    is SecurityKeyException.Timeout -> R.string.security_key_error_timed_out
    is SecurityKeyException.Unsupported -> R.string.security_key_error_unsupported
    is SecurityKeyException.UvInvalid -> R.string.security_key_error_uv_invalid
    is SecurityKeyException -> R.string.security_key_error_failed
    is PasskeyFailedException -> R.string.security_key_error_failed
    else -> null
}

fun security_key_transports(context: Context): SecurityKeyTransports {
    val manager = context.packageManager
    val usb = runCatching { manager.hasSystemFeature(PackageManager.FEATURE_USB_HOST) }.getOrDefault(false)
    val adapter = runCatching {
        if (manager.hasSystemFeature(PackageManager.FEATURE_NFC)) NfcAdapter.getDefaultAdapter(context) else null
    }.getOrNull()
    val nfc = when {
        adapter == null -> SecurityKeyNfcStatus.missing
        runCatching { adapter.isEnabled }.getOrDefault(false) -> SecurityKeyNfcStatus.ready
        else -> SecurityKeyNfcStatus.off
    }
    return SecurityKeyTransports(nfc, usb)
}

fun open_nfc_settings(context: Context): Boolean = runCatching {
    context.startActivity(Intent(Settings.ACTION_NFC_SETTINGS))
    true
}.getOrElse {
    runCatching {
        context.startActivity(Intent(Settings.ACTION_WIRELESS_SETTINGS))
        true
    }.getOrDefault(false)
}

fun security_key_origin(context: Context, fallback: String): String {
    val certificate = runCatching { signing_certificate(context) }.getOrNull()
    if (certificate == null || certificate.isEmpty()) return fallback.trimEnd('/')
    val digest = MessageDigest.getInstance("SHA-256").digest(certificate)
    return android_origin_prefix + Base64.getUrlEncoder().withoutPadding().encodeToString(digest)
}

@Suppress("DEPRECATION")
private fun signing_certificate(context: Context): ByteArray? {
    val manager = context.packageManager
    return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
        val info = manager.getPackageInfo(context.packageName, PackageManager.GET_SIGNING_CERTIFICATES)
        info.signingInfo?.apkContentsSigners?.firstOrNull()?.toByteArray()
    } else {
        val info = manager.getPackageInfo(context.packageName, PackageManager.GET_SIGNATURES)
        info.signatures?.firstOrNull()?.toByteArray()
    }
}

fun is_security_key_usb_device(device: UsbDevice): Boolean =
    device.vendorId == yubico_vendor_id || (0 until device.interfaceCount).any { index ->
        val usb_interface = device.getInterface(index)
        usb_interface.interfaceClass == UsbConstants.USB_CLASS_HID &&
            usb_interface.interfaceSubclass == 0 &&
            usb_interface.interfaceProtocol == 0
    }

private class SecurityKeyUsbFilter(private val usb_manager: UsbManager?) : DeviceFilter() {
    override fun checkVendorProductIds(vendorId: Int, productId: Int): Boolean {
        if (vendorId == yubico_vendor_id) return true
        val device = runCatching {
            usb_manager?.deviceList?.values?.firstOrNull { it.vendorId == vendorId && it.productId == productId }
        }.getOrNull() ?: return false
        return is_security_key_usb_device(device)
    }

    override fun checkUsbDevice(usbManager: UsbManager, usbDevice: UsbDevice): Boolean =
        is_security_key_usb_device(usbDevice)
}

class SecurityKeySession(private val activity: ComponentActivity) {
    private val app_context: Context = activity.applicationContext
    private val manager: YubiKitManager by lazy { YubiKitManager(app_context) }
    private val devices = Channel<YubiKeyDevice>(Channel.CONFLATED)
    private val _state = MutableStateFlow<SecurityKeyUiState>(SecurityKeyUiState.Idle)
    val state: StateFlow<SecurityKeyUiState> = _state.asStateFlow()

    @Volatile private var pin_request: CompletableDeferred<CharArray?>? = null
    @Volatile private var active: Job? = null
    private val usb_manager: UsbManager? by lazy { app_context.getSystemService(Context.USB_SERVICE) as? UsbManager }
    private val delivered_usb = mutableSetOf<String>()
    @Volatile private var discovering = false
    private var nfc_running = false
    @Volatile private var usb_running = false

    private val lifecycle_observer = object : DefaultLifecycleObserver {
        override fun onResume(owner: LifecycleOwner) {
            start_nfc()
            scan_usb()
            _state.update { current ->
                if (current is SecurityKeyUiState.Waiting) {
                    current.copy(transports = security_key_transports(app_context))
                } else {
                    current
                }
            }
        }

        override fun onPause(owner: LifecycleOwner) {
            stop_nfc()
        }
    }

    fun transports(): SecurityKeyTransports = security_key_transports(app_context)

    suspend fun assert(
        prepare: suspend () -> WebAuthnAssertionOptions,
        origin: String,
        from_fallback: Boolean,
    ): SecurityKeyAssertion {
        val outer = currentCoroutineContext()
        try {
            return coroutineScope {
                val work = async { run_assert(prepare, origin, from_fallback) }
                active = work
                work.await()
            }
        } catch (failure: CancellationException) {
            if (outer.isActive) throw PasskeyCancelledException()
            throw failure
        } finally {
            active = null
            pin_request?.cancel()
            pin_request = null
            withContext(NonCancellable + Dispatchers.Main.immediate) { stop_discovery() }
            _state.value = SecurityKeyUiState.Idle
        }
    }

    fun submit_pin(pin: CharArray) {
        val request = pin_request
        if (request == null || !request.complete(pin)) pin.fill(0.toChar())
    }

    fun cancel() {
        pin_request?.complete(null)
        active?.cancel()
    }

    private suspend fun run_assert(
        prepare: suspend () -> WebAuthnAssertionOptions,
        origin: String,
        from_fallback: Boolean,
    ): SecurityKeyAssertion {
        var started = false
        return run_security_key_flow(
            prepare = prepare,
            origin = origin,
            next_target = { tap_again ->
                if (!started) {
                    withContext(Dispatchers.Main.immediate) { start_discovery() }
                    started = true
                }
                next_target(tap_again, from_fallback)
            },
            request_pin = { retries, wrong -> await_pin(retries, wrong) },
            on_working = { _state.value = SecurityKeyUiState.Working },
            now_ms = { SystemClock.elapsedRealtime() },
            run_on_key = { block -> run_on_key(block) },
            on_failure = { failure ->
                if (failure !is CancellationException && failure !is PasskeyCancelledException) {
                    Log.w(security_key_log_tag, "key attempt failed: ${describe_failure(failure)}")
                }
            },
        )
    }

    private suspend fun next_target(tap_again: Boolean, from_fallback: Boolean): SecurityKeyTarget {
        while (true) {
            _state.value = SecurityKeyUiState.Waiting(transports(), tap_again, from_fallback)
            val device = withTimeoutOrNull(usb_scan_interval_ms) { devices.receive() }
            if (device == null) {
                scan_usb()
                continue
            }
            if (device is UsbYubiKeyDevice && !device.supportsConnection(FidoConnection::class.java)) {
                Log.w(security_key_log_tag, "usb device has no FIDO interface")
                continue
            }
            return security_key_target(device)
        }
    }

    private fun security_key_target(device: YubiKeyDevice): SecurityKeyTarget = when (device) {
        is UsbYubiKeyDevice -> object : SecurityKeyTarget {
            override fun open(): YubiKeyConnection = open_connection(device)
            override fun attached(): Boolean = usb_attached(device.usbDevice)
        }
        else -> SecurityKeyTarget { open_connection(device) }
    }

    private fun usb_attached(device: UsbDevice): Boolean = runCatching {
        val manager = usb_manager ?: return@runCatching false
        manager.deviceList.containsKey(device.deviceName) && manager.hasPermission(device)
    }.getOrDefault(false)

    private fun deliver_usb(device: UsbYubiKeyDevice) {
        val name = device.usbDevice.deviceName
        val fresh = synchronized(delivered_usb) { delivered_usb.add(name) }
        if (fresh) devices.trySend(device)
    }

    private fun scan_usb() {
        if (!discovering || !usb_running) return
        val manager = usb_manager ?: return
        runCatching {
            manager.deviceList.values
                .filter { is_security_key_usb_device(it) && manager.hasPermission(it) }
                .filter { synchronized(delivered_usb) { it.deviceName !in delivered_usb } }
                .forEach { device ->
                    Log.i(security_key_log_tag, "usb key found with permission granted")
                    deliver_usb(UsbYubiKeyDevice(manager, device))
                }
        }.onFailure { Log.w(security_key_log_tag, "usb scan failed: ${describe_failure(it)}") }
    }

    private suspend fun await_pin(retries: Int?, wrong: Boolean): CharArray? {
        val request = CompletableDeferred<CharArray?>()
        pin_request = request
        _state.value = SecurityKeyUiState.NeedPin(retries, wrong)
        return try {
            request.await()?.takeIf { it.size >= security_key_min_pin_length }
        } finally {
            pin_request = null
        }
    }

    private suspend fun run_on_key(block: (CommandState) -> String): String {
        val command_state = CommandState()
        return suspendCancellableCoroutine { continuation ->
            continuation.invokeOnCancellation { command_state.cancel() }
            Dispatchers.IO.asExecutor().execute {
                continuation.resumeWith(runCatching { block(command_state) })
            }
        }
    }

    private fun open_connection(device: YubiKeyDevice): YubiKeyConnection = when (device) {
        is NfcYubiKeyDevice -> device.openConnection(SmartCardConnection::class.java)
        else -> device.openConnection(FidoConnection::class.java)
    }

    private fun start_discovery() {
        discovering = true
        while (devices.tryReceive().isSuccess) continue
        synchronized(delivered_usb) { delivered_usb.clear() }
        if (!usb_running && transports().usb) {
            usb_running = runCatching {
                manager.startUsbDiscovery(
                    UsbConfiguration()
                        .handlePermissions(true)
                        .setDeviceFilter(SecurityKeyUsbFilter(usb_manager)),
                ) { device -> deliver_usb(device) }
                true
            }.onFailure {
                Log.w(security_key_log_tag, "usb discovery failed: ${describe_failure(it)}")
            }.getOrDefault(false)
        }
        runCatching { activity.lifecycle.addObserver(lifecycle_observer) }
    }

    private fun start_nfc() {
        if (!discovering || nfc_running) return
        if (transports().nfc != SecurityKeyNfcStatus.ready) return
        nfc_running = runCatching {
            manager.startNfcDiscovery(
                NfcConfiguration().timeout(nfc_timeout_ms).skipNdefCheck(true),
                activity,
            ) { device -> devices.trySend(device) }
            true
        }.getOrDefault(false)
    }

    private fun stop_nfc() {
        if (!nfc_running) return
        runCatching { manager.stopNfcDiscovery(activity) }
        nfc_running = false
    }

    private fun stop_discovery() {
        discovering = false
        runCatching { activity.lifecycle.removeObserver(lifecycle_observer) }
        stop_nfc()
        if (usb_running) {
            runCatching { manager.stopUsbDiscovery() }
            usb_running = false
        }
        synchronized(delivered_usb) { delivered_usb.clear() }
        while (devices.tryReceive().isSuccess) continue
    }
}
