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

import com.yubico.yubikit.core.Transport
import com.yubico.yubikit.core.YubiKeyConnection
import com.yubico.yubikit.core.application.CommandState
import com.yubico.yubikit.core.fido.FidoConnection
import com.yubico.yubikit.core.smartcard.SmartCardConnection
import com.yubico.yubikit.fido.Cbor
import java.io.IOException
import java.math.BigInteger
import java.nio.ByteBuffer
import java.security.KeyFactory
import java.security.KeyPair
import java.security.KeyPairGenerator
import java.security.MessageDigest
import java.security.SecureRandom
import java.security.Signature
import java.security.interfaces.ECPublicKey
import java.security.spec.ECGenParameterSpec
import java.security.spec.ECPoint
import java.security.spec.ECPublicKeySpec
import java.util.Base64
import javax.crypto.Cipher
import javax.crypto.KeyAgreement
import javax.crypto.Mac
import javax.crypto.spec.IvParameterSpec
import javax.crypto.spec.SecretKeySpec
import kotlinx.coroutines.runBlocking
import org.astermail.android.api.auth.WebAuthnAllowedCredential
import org.astermail.android.api.auth.WebAuthnAssertionOptions
import org.json.JSONObject
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

private const val test_rp_id = "app.astermail.org"
private const val release_origin = "android:apk-key-hash:iLCopvuU7nOkVKD5JzK9QI6S5R7B6VVnROXwBVYQCXc"
private const val web_origin = "https://app.astermail.org"
private const val good_pin = "246810"
private const val wrong_pin = "135790"
private const val uv_required = "required"
private const val uv_preferred = "preferred"

private const val ctap_invalid_command = 0x01
private const val ctap_invalid_parameter = 0x02
private const val ctap_missing_parameter = 0x14
private const val ctap_invalid_option = 0x2C
private const val ctap_keepalive_cancel = 0x2D
private const val ctap_no_credentials = 0x2E
private const val ctap_pin_invalid = 0x31
private const val ctap_pin_blocked = 0x32
private const val ctap_pin_auth_invalid = 0x33
private const val ctap_pin_auth_blocked = 0x34
private const val ctap_pin_not_set = 0x35
private const val ctap_invalid_subcommand = 0x3E

private const val permission_get_assertion = 0x02
private const val legacy_token_permissions = 0x03

private val test_random = SecureRandom()
private val test_b64url = Base64.getUrlEncoder().withoutPadding()
private val sw_ok = byteArrayOf(0x90.toByte(), 0x00)

private fun b64url(bytes: ByteArray): String = test_b64url.encodeToString(bytes)

private fun from_b64url(value: String): ByteArray = Base64.getUrlDecoder().decode(value)

private fun random_bytes(size: Int): ByteArray = ByteArray(size).also { test_random.nextBytes(it) }

private fun sha256(vararg parts: ByteArray): ByteArray {
    val digest = MessageDigest.getInstance("SHA-256")
    parts.forEach { digest.update(it) }
    return digest.digest()
}

private fun hmac(key: ByteArray, data: ByteArray): ByteArray {
    val mac = Mac.getInstance("HmacSHA256")
    mac.init(SecretKeySpec(key, "HmacSHA256"))
    return mac.doFinal(data)
}

private fun aes(mode: Int, key: ByteArray, iv: ByteArray, data: ByteArray): ByteArray {
    val cipher = Cipher.getInstance("AES/CBC/NoPadding")
    cipher.init(mode, SecretKeySpec(key, "AES"), IvParameterSpec(iv))
    return cipher.doFinal(data)
}

private fun coordinate(value: BigInteger): ByteArray {
    val raw = value.toByteArray()
    return when {
        raw.size == 32 -> raw
        raw.size > 32 -> raw.copyOfRange(raw.size - 32, raw.size)
        else -> ByteArray(32 - raw.size) + raw
    }
}

private fun ec_key_pair(): KeyPair {
    val generator = KeyPairGenerator.getInstance("EC")
    generator.initialize(ECGenParameterSpec("secp256r1"))
    return generator.generateKeyPair()
}

private fun cose_key(key: ECPublicKey, alg: Int): Map<Int, Any> = mapOf(
    1 to 2,
    3 to alg,
    -1 to 1,
    -2 to coordinate(key.w.affineX),
    -3 to coordinate(key.w.affineY),
)

@Suppress("UNCHECKED_CAST")
private fun as_map(value: Any?): Map<Any?, Any?>? = value as? Map<Any?, Any?>

private fun ok(value: Any): ByteArray = byteArrayOf(0x00) + Cbor.encode(value)

private fun ctap_error(code: Int): ByteArray = byteArrayOf(code.toByte())

private class FakePinProtocol(val version: Int) {
    fun kdf(z: ByteArray): ByteArray = if (version == 1) {
        sha256(z)
    } else {
        val prk = hmac(ByteArray(32), z)
        hmac(prk, "CTAP2 HMAC key".toByteArray() + byteArrayOf(1)) +
            hmac(prk, "CTAP2 AES key".toByteArray() + byteArrayOf(1))
    }

    fun encrypt(key: ByteArray, plain: ByteArray): ByteArray = if (version == 1) {
        aes(Cipher.ENCRYPT_MODE, key, ByteArray(16), plain)
    } else {
        val iv = random_bytes(16)
        iv + aes(Cipher.ENCRYPT_MODE, key.copyOfRange(32, 64), iv, plain)
    }

    fun decrypt(key: ByteArray, data: ByteArray): ByteArray = if (version == 1) {
        aes(Cipher.DECRYPT_MODE, key, ByteArray(16), data)
    } else {
        aes(Cipher.DECRYPT_MODE, key.copyOfRange(32, 64), data.copyOf(16), data.copyOfRange(16, data.size))
    }

    fun authenticate(key: ByteArray, message: ByteArray): ByteArray = if (version == 1) {
        hmac(key, message).copyOf(16)
    } else {
        hmac(key.copyOf(32), message)
    }
}

private class SoftCredential(
    val id: ByteArray,
    val rp_id_hash: ByteArray,
    val key_pair: KeyPair,
    var counter: Int = 0,
) {
    val cose_public_key: ByteArray get() = Cbor.encode(cose_key(key_pair.public as ECPublicKey, -7))
}

private class Reply(val needs_touch: Boolean, val produce: () -> ByteArray)

private class SoftAuthenticator(
    var pin: String?,
    val ctap21: Boolean = true,
    val max_creds_in_list: Int? = 8,
) {
    val credentials = mutableListOf<SoftCredential>()
    var pin_retries = 8
    var consecutive_mismatches = 0
    var pin_auth_blocked = false
    var key_agreement = ec_key_pair()
    var pin_token: ByteArray? = null
    var token_permissions = 0
    var token_rp_id: String? = null
    val commands = mutableListOf<Int>()
    val pin_subcommands = mutableListOf<Int>()
    val pin_protocols = mutableListOf<Int>()
    val u2f_commands = mutableListOf<Int>()
    var touches = 0
    private val supported_protocols = if (ctap21) listOf(2, 1) else listOf(1)

    fun register(rp_id: String = test_rp_id, id_size: Int = 64): SoftCredential {
        val credential = SoftCredential(random_bytes(id_size), sha256(rp_id.toByteArray()), ec_key_pair())
        credentials += credential
        return credential
    }

    fun power_cycle() {
        pin_auth_blocked = false
        consecutive_mismatches = 0
        pin_token = null
        key_agreement = ec_key_pair()
    }

    fun handle(request: ByteArray): Reply {
        val command = request[0].toInt() and 0xff
        commands += command
        return when (command) {
            0x04 -> immediate(ok(info()))
            0x02 -> get_assertion(params(request))
            0x06 -> immediate(client_pin(params(request)))
            else -> immediate(ctap_error(ctap_invalid_command))
        }
    }

    private fun immediate(response: ByteArray): Reply = Reply(false) { response }

    private fun params(request: ByteArray): Map<Any?, Any?> =
        as_map(Cbor.decode(request, 1, request.size - 1)) ?: emptyMap()

    private fun info(): Map<Int, Any> {
        val options = mutableMapOf<String, Any>(
            "rk" to false,
            "up" to true,
            "plat" to false,
            "clientPin" to (pin != null),
        )
        if (ctap21) {
            options["pinUvAuthToken"] = true
            options["makeCredUvNotRqd"] = true
        }
        val info = mutableMapOf<Int, Any>(
            1 to if (ctap21) listOf("U2F_V2", "FIDO_2_0", "FIDO_2_1") else listOf("U2F_V2", "FIDO_2_0"),
            2 to listOf("hmac-secret"),
            3 to ByteArray(16) { 0x11 },
            4 to options,
            5 to 1200,
            6 to supported_protocols,
            8 to 255,
            9 to listOf("nfc", "usb"),
        )
        max_creds_in_list?.let { info[7] = it }
        return info
    }

    private fun get_assertion(params: Map<Any?, Any?>): Reply {
        val rp_id = params[1] as? String ?: return immediate(ctap_error(ctap_missing_parameter))
        val client_data_hash = params[2] as? ByteArray ?: return immediate(ctap_error(ctap_missing_parameter))
        val allow = (params[3] as? List<*>).orEmpty().mapNotNull { as_map(it)?.get("id") as? ByteArray }
        val options = as_map(params[5])
        val pin_auth = params[6] as? ByteArray
        val protocol = params[7] as? Int
        if (options?.get("uv") == true) return immediate(ctap_error(ctap_invalid_option))
        var verified = false
        if (pin_auth != null) {
            if (protocol == null) return immediate(ctap_error(ctap_missing_parameter))
            if (protocol !in supported_protocols) return immediate(ctap_error(ctap_invalid_parameter))
            val token = pin_token ?: return immediate(ctap_error(ctap_pin_auth_invalid))
            val expected = FakePinProtocol(protocol).authenticate(token, client_data_hash)
            if (!MessageDigest.isEqual(expected, pin_auth)) return immediate(ctap_error(ctap_pin_auth_invalid))
            val permitted = (token_permissions and permission_get_assertion) != 0
            if (ctap21 && (!permitted || (token_rp_id != null && token_rp_id != rp_id))) {
                return immediate(ctap_error(ctap_pin_auth_invalid))
            }
            verified = true
        }
        val up = options?.get("up") as? Boolean ?: true
        val rp_hash = sha256(rp_id.toByteArray())
        val match = credentials.firstOrNull { credential ->
            MessageDigest.isEqual(credential.rp_id_hash, rp_hash) && allow.any { it.contentEquals(credential.id) }
        }
        if (match == null) return Reply(up) { ctap_error(ctap_no_credentials) }
        if (!up) return immediate(assertion(match, client_data_hash, false, verified))
        return Reply(true) {
            touches += 1
            assertion(match, client_data_hash, true, verified)
        }
    }

    private fun assertion(
        credential: SoftCredential,
        client_data_hash: ByteArray,
        up: Boolean,
        verified: Boolean,
    ): ByteArray {
        if (up) credential.counter += 1
        val flags = (if (up) 0x01 else 0x00) or (if (verified) 0x04 else 0x00)
        val auth_data = ByteBuffer.allocate(37)
            .put(credential.rp_id_hash)
            .put(flags.toByte())
            .putInt(credential.counter)
            .array()
        val signer = Signature.getInstance("SHA256withECDSA")
        signer.initSign(credential.key_pair.private)
        signer.update(auth_data)
        signer.update(client_data_hash)
        return ok(
            mapOf(
                1 to mapOf("id" to credential.id, "type" to "public-key"),
                2 to auth_data,
                3 to signer.sign(),
            ),
        )
    }

    private fun client_pin(params: Map<Any?, Any?>): ByteArray {
        val protocol = params[1] as? Int ?: return ctap_error(ctap_missing_parameter)
        val subcommand = params[2] as? Int ?: return ctap_error(ctap_missing_parameter)
        pin_subcommands += subcommand
        pin_protocols += protocol
        if (protocol !in supported_protocols) return ctap_error(ctap_invalid_parameter)
        return when (subcommand) {
            0x01 -> ok(mapOf(3 to pin_retries, 4 to false))
            0x02 -> ok(mapOf(1 to cose_key(key_agreement.public as ECPublicKey, -25)))
            0x05, 0x09 -> pin_token_reply(params, subcommand, FakePinProtocol(protocol))
            else -> ctap_error(ctap_invalid_subcommand)
        }
    }

    private fun pin_token_reply(params: Map<Any?, Any?>, subcommand: Int, protocol: FakePinProtocol): ByteArray {
        val permissions = params[9] as? Int
        val permissions_rp_id = params[10] as? String
        if (subcommand == 0x09 && !ctap21) return ctap_error(ctap_invalid_subcommand)
        if (subcommand == 0x05 && (permissions != null || permissions_rp_id != null)) {
            return ctap_error(ctap_invalid_parameter)
        }
        if (subcommand == 0x09) {
            if (permissions == null) return ctap_error(ctap_missing_parameter)
            if (permissions == 0) return ctap_error(ctap_invalid_parameter)
            if ((permissions and permission_get_assertion) != 0 && permissions_rp_id == null) {
                return ctap_error(ctap_missing_parameter)
            }
        }
        val platform_key = as_map(params[3]) ?: return ctap_error(ctap_missing_parameter)
        val pin_hash_enc = params[6] as? ByteArray ?: return ctap_error(ctap_missing_parameter)
        val expected_pin = pin ?: return ctap_error(ctap_pin_not_set)
        if (pin_retries == 0) return ctap_error(ctap_pin_blocked)
        if (pin_auth_blocked) return ctap_error(ctap_pin_auth_blocked)
        val shared = protocol.kdf(ecdh(platform_key))
        val pin_hash = protocol.decrypt(shared, pin_hash_enc)
        pin_retries -= 1
        if (!MessageDigest.isEqual(pin_hash, sha256(expected_pin.toByteArray()).copyOf(16))) {
            key_agreement = ec_key_pair()
            consecutive_mismatches += 1
            return when {
                pin_retries == 0 -> ctap_error(ctap_pin_blocked)
                consecutive_mismatches >= 3 -> {
                    pin_auth_blocked = true
                    ctap_error(ctap_pin_auth_blocked)
                }
                else -> ctap_error(ctap_pin_invalid)
            }
        }
        pin_retries = 8
        consecutive_mismatches = 0
        val token = random_bytes(32)
        pin_token = token
        token_permissions = permissions ?: legacy_token_permissions
        token_rp_id = permissions_rp_id
        return ok(mapOf(2 to protocol.encrypt(shared, token)))
    }

    private fun ecdh(platform_key: Map<Any?, Any?>): ByteArray {
        val x = BigInteger(1, platform_key[-2] as ByteArray)
        val y = BigInteger(1, platform_key[-3] as ByteArray)
        val own = key_agreement.public as ECPublicKey
        val peer = KeyFactory.getInstance("EC").generatePublic(ECPublicKeySpec(ECPoint(x, y), own.params))
        val agreement = KeyAgreement.getInstance("ECDH")
        agreement.init(key_agreement.private)
        agreement.doPhase(peer, true)
        return agreement.generateSecret()
    }
}

private class FakeNfcConnection(
    private val authenticator: SoftAuthenticator,
    private val applet_present: Boolean = true,
    private val remove_after: Int? = null,
    private val remove_on_touch: Boolean = false,
) : SmartCardConnection {
    var apdus = 0
    private var removed = false
    private var closed = false
    private var chained = ByteArray(0)
    private var pending: (() -> ByteArray)? = null
    private var remaining = ByteArray(0)

    override fun sendAndReceive(apdu: ByteArray): ByteArray {
        if (removed || closed) throw IOException("tag lost")
        if (remove_after != null && apdus >= remove_after) {
            removed = true
            throw IOException("tag lost")
        }
        apdus += 1
        val cla = apdu[0].toInt() and 0xff
        val ins = apdu[1].toInt() and 0xff
        val p1 = apdu[2].toInt() and 0xff
        val data = if (apdu.size > 5) apdu.copyOfRange(5, 5 + (apdu[4].toInt() and 0xff)) else ByteArray(0)
        if ((cla and 0x10) != 0) {
            chained += data
            return sw_ok
        }
        val full = chained + data
        chained = ByteArray(0)
        return when {
            cla == 0x00 && ins == 0xA4 ->
                if (applet_present) "U2F_V2".toByteArray() + sw_ok else byteArrayOf(0x6A, 0x82.toByte())
            cla == 0x00 && ins == 0xC0 -> next_chunk()
            cla == 0x80 && ins == 0x10 -> {
                val reply = authenticator.handle(full)
                if (reply.needs_touch) {
                    pending = reply.produce
                    byteArrayOf(0x02, 0x91.toByte(), 0x00)
                } else {
                    respond(reply.produce())
                }
            }
            cla == 0x80 && ins == 0x11 -> {
                val produce = pending ?: return byteArrayOf(0x69, 0x85.toByte())
                pending = null
                when {
                    p1 == 0x11 -> respond(ctap_error(ctap_keepalive_cancel))
                    remove_on_touch -> {
                        removed = true
                        throw IOException("tag lost")
                    }
                    else -> respond(produce())
                }
            }
            else -> {
                authenticator.u2f_commands += ins
                byteArrayOf(0x6D, 0x00)
            }
        }
    }

    private fun respond(data: ByteArray): ByteArray {
        remaining = data
        return next_chunk()
    }

    private fun next_chunk(): ByteArray {
        val size = minOf(remaining.size, 255)
        val chunk = remaining.copyOf(size)
        remaining = remaining.copyOfRange(size, remaining.size)
        return if (remaining.isEmpty()) {
            chunk + sw_ok
        } else {
            chunk + byteArrayOf(0x61, minOf(remaining.size, 255).toByte())
        }
    }

    override fun getTransport(): Transport = Transport.NFC

    override fun isExtendedLengthApduSupported(): Boolean = false

    override fun getAtr(): ByteArray = ByteArray(0)

    override fun close() {
        closed = true
    }
}

private class FakeHidConnection(
    private val authenticator: SoftAuthenticator,
    private val remove_on_touch: Boolean = false,
) : FidoConnection {
    private val outgoing = ArrayDeque<ByteArray>()
    private var collecting = false
    private var request_channel = 0
    private var request_command = 0
    private var request_length = 0
    private var request = ByteArray(0)
    private var pending: (() -> ByteArray)? = null
    private var cancelled = false
    private var closed = false

    override fun send(packet: ByteArray) {
        if (closed) throw IOException("device gone")
        val copy = packet.copyOf()
        val buffer = ByteBuffer.wrap(copy)
        val channel = buffer.int
        val command = buffer.get().toInt() and 0xff
        if ((command and 0x80) != 0) {
            if (command == 0x91) {
                cancelled = true
                return
            }
            request_channel = channel
            request_command = command
            request_length = buffer.short.toInt() and 0xffff
            request = copy.copyOfRange(7, 7 + minOf(request_length, 57))
            cancelled = false
            collecting = true
        } else {
            if (!collecting) return
            val needed = request_length - request.size
            request += copy.copyOfRange(5, 5 + minOf(needed, 59))
        }
        if (collecting && request.size >= request_length) dispatch()
    }

    private fun dispatch() {
        collecting = false
        when (request_command) {
            0x86 -> {
                val reply = request.copyOf(8) + byteArrayOf(0x0A, 0x0B, 0x0C, 0x0D, 0x02, 0x05, 0x04, 0x03, 0x05)
                frame(0x86, reply)
            }
            0x90 -> {
                val reply = authenticator.handle(request)
                if (reply.needs_touch) {
                    pending = reply.produce
                    val keepalive = ByteBuffer.allocate(FidoConnection.PACKET_SIZE)
                        .putInt(request_channel)
                        .put(0xBB.toByte())
                        .putShort(1)
                        .put(CommandState.STATUS_UPNEEDED)
                        .array()
                    outgoing.addLast(keepalive)
                } else {
                    frame(0x90, reply.produce())
                }
            }
            0x83 -> {
                authenticator.u2f_commands += request.getOrElse(1) { 0 }.toInt() and 0xff
                frame(0xBF, byteArrayOf(0x01))
            }
            else -> frame(0xBF, byteArrayOf(0x01))
        }
    }

    private fun frame(command: Int, data: ByteArray) {
        val first = ByteBuffer.allocate(FidoConnection.PACKET_SIZE)
            .putInt(request_channel)
            .put(command.toByte())
            .putShort(data.size.toShort())
        val first_size = minOf(data.size, 57)
        first.put(data, 0, first_size)
        outgoing.addLast(first.array())
        var offset = first_size
        var sequence = 0
        while (offset < data.size) {
            val size = minOf(data.size - offset, 59)
            val packet = ByteBuffer.allocate(FidoConnection.PACKET_SIZE)
                .putInt(request_channel)
                .put(sequence.toByte())
            packet.put(data, offset, size)
            outgoing.addLast(packet.array())
            offset += size
            sequence += 1
        }
    }

    override fun receive(packet: ByteArray) {
        if (closed) throw IOException("device gone")
        if (outgoing.isEmpty()) {
            val produce = pending ?: throw IOException("nothing to read")
            pending = null
            when {
                cancelled -> frame(0x90, ctap_error(ctap_keepalive_cancel))
                remove_on_touch -> {
                    closed = true
                    throw IOException("device gone")
                }
                else -> frame(0x90, produce())
            }
        }
        outgoing.removeFirst().copyInto(packet)
    }

    override fun close() {
        closed = true
    }
}

private fun nfc_target(
    authenticator: SoftAuthenticator,
    applet_present: Boolean = true,
    remove_after: Int? = null,
    remove_on_touch: Boolean = false,
): SecurityKeyTarget = SecurityKeyTarget {
    FakeNfcConnection(authenticator, applet_present, remove_after, remove_on_touch)
}

private fun usb_target(authenticator: SoftAuthenticator, remove_on_touch: Boolean = false): SecurityKeyTarget =
    SecurityKeyTarget { FakeHidConnection(authenticator, remove_on_touch) }

private class FlakyUsbTarget(
    private val authenticator: SoftAuthenticator,
    private val failures: List<Throwable>,
    private val still_attached: Boolean,
) : SecurityKeyTarget {
    var opens = 0

    override fun open(): YubiKeyConnection {
        opens += 1
        failures.getOrNull(opens - 1)?.let { throw it }
        return FakeHidConnection(authenticator)
    }

    override fun attached(): Boolean = still_attached
}

private fun assertion_options(
    challenge: ByteArray,
    user_verification: String,
    vararg credentials: SoftCredential,
): WebAuthnAssertionOptions = WebAuthnAssertionOptions(
    challenge = b64url(challenge),
    challenge_token = "ct",
    rpId = test_rp_id,
    allowCredentials = credentials.map { WebAuthnAllowedCredential(id = b64url(it.id)) },
    timeout = 60000,
    userVerification = user_verification,
)

private class FlowRecord {
    val targets = mutableListOf<Boolean>()
    val prompts = mutableListOf<Pair<Int?, Boolean>>()
    var prepares = 0
}

private fun run_flow(
    record: FlowRecord,
    origin: String,
    targets: List<SecurityKeyTarget>,
    pins: List<String?>,
    clock: () -> Long = { 0L },
    prepare: () -> WebAuthnAssertionOptions,
): SecurityKeyAssertion = runBlocking {
    val target_queue = ArrayDeque(targets)
    val pin_queue = ArrayDeque(pins)
    run_security_key_flow(
        prepare = {
            record.prepares += 1
            prepare()
        },
        origin = origin,
        next_target = { tap_again ->
            record.targets += tap_again
            target_queue.removeFirst()
        },
        request_pin = { retries, wrong ->
            record.prompts += retries to wrong
            pin_queue.removeFirst()?.toCharArray()
        },
        on_working = {},
        now_ms = clock,
        run_on_key = { block -> block(CommandState()) },
    )
}

private inline fun <reified T : Throwable> expect_failure(block: () -> Unit): T {
    try {
        block()
    } catch (failure: Throwable) {
        if (failure is T) return failure
        throw AssertionError("expected ${T::class.java.simpleName} but got $failure", failure)
    }
    throw AssertionError("expected ${T::class.java.simpleName} but nothing was thrown")
}

private fun verify_assertion(
    response_json: String,
    authenticator: SoftAuthenticator,
    credential: SoftCredential,
    challenge: ByteArray,
    origin: String,
    expect_uv: Boolean,
): JSONObject {
    val json = JSONObject(response_json)
    assertEquals(b64url(credential.id), json.getString("id"))
    assertEquals(b64url(credential.id), json.getString("rawId"))
    assertEquals("public-key", json.getString("type"))
    val response = json.getJSONObject("response")
    val client_data = from_b64url(response.getString("clientDataJSON"))
    val client = JSONObject(String(client_data, Charsets.UTF_8))
    assertEquals("webauthn.get", client.getString("type"))
    assertEquals(b64url(challenge), client.getString("challenge"))
    assertEquals(origin, client.getString("origin"))
    assertFalse(client.getBoolean("crossOrigin"))
    val auth_data = from_b64url(response.getString("authenticatorData"))
    assertEquals(37, auth_data.size)
    assertArrayEquals(sha256(test_rp_id.toByteArray()), auth_data.copyOf(32))
    val flags = auth_data[32].toInt() and 0xff
    assertTrue((flags and 0x01) != 0)
    assertEquals(expect_uv, (flags and 0x04) != 0)
    assertEquals(credential.counter, ByteBuffer.wrap(auth_data, 33, 4).int)
    val verifier = Signature.getInstance("SHA256withECDSA")
    verifier.initVerify(credential.key_pair.public)
    verifier.update(auth_data)
    verifier.update(sha256(client_data))
    assertTrue(verifier.verify(from_b64url(response.getString("signature"))))
    assertTrue(authenticator.u2f_commands.isEmpty())
    return json
}

private fun print_fixture(name: String, credential: SoftCredential, challenge: ByteArray, json: JSONObject) {
    val response = json.getJSONObject("response")
    val client = JSONObject(String(from_b64url(response.getString("clientDataJSON")), Charsets.UTF_8))
    val fixture = JSONObject()
        .put("name", name)
        .put("cose_public_key", b64url(credential.cose_public_key))
        .put("credential_id", json.getString("rawId"))
        .put("challenge", b64url(challenge))
        .put("origin", client.getString("origin"))
        .put("client_data_json", response.getString("clientDataJSON"))
        .put("authenticator_data", response.getString("authenticatorData"))
        .put("signature", response.getString("signature"))
        .put("counter", credential.counter)
    println("FIXTURE $fixture")
}

class SecurityKeyAssertionTest {

    @Test
    fun nfc_key_with_pin_signs_when_verification_is_required() {
        val key = SoftAuthenticator(pin = good_pin)
        val credential = key.register()
        val first_challenge = random_bytes(32)
        val record = FlowRecord()
        val first = run_flow(record, release_origin, listOf(nfc_target(key)), listOf(good_pin)) {
            assertion_options(first_challenge, uv_required, credential)
        }
        val first_json = verify_assertion(first.response_json, key, credential, first_challenge, release_origin, true)
        assertEquals(listOf(false), record.targets)
        assertEquals(listOf<Pair<Int?, Boolean>>(null to false), record.prompts)
        assertTrue(key.pin_subcommands.contains(0x09))
        assertFalse(key.pin_subcommands.contains(0x05))
        assertTrue(key.pin_protocols.all { it == 2 })
        assertEquals(1, credential.counter)
        assertEquals(1, key.touches)
        print_fixture("nfc_required_pin", credential, first_challenge, first_json)

        val second_challenge = random_bytes(32)
        val second = run_flow(FlowRecord(), release_origin, listOf(nfc_target(key)), listOf(good_pin)) {
            assertion_options(second_challenge, uv_required, credential)
        }
        val second_json = verify_assertion(second.response_json, key, credential, second_challenge, release_origin, true)
        assertEquals(2, credential.counter)
        print_fixture("nfc_required_pin_next", credential, second_challenge, second_json)
    }

    @Test
    fun usb_key_with_pin_signs_when_verification_is_required() {
        val key = SoftAuthenticator(pin = good_pin)
        val credential = key.register()
        val challenge = random_bytes(32)
        val record = FlowRecord()
        val result = run_flow(record, release_origin, listOf(usb_target(key)), listOf(good_pin)) {
            assertion_options(challenge, uv_required, credential)
        }
        verify_assertion(result.response_json, key, credential, challenge, release_origin, true)
        assertEquals(listOf<Pair<Int?, Boolean>>(null to false), record.prompts)
    }

    @Test
    fun key_with_pin_asks_for_it_when_verification_is_preferred() {
        val key = SoftAuthenticator(pin = good_pin)
        val credential = key.register()
        val challenge = random_bytes(32)
        val record = FlowRecord()
        val result = run_flow(record, web_origin, listOf(usb_target(key)), listOf(good_pin)) {
            assertion_options(challenge, uv_preferred, credential)
        }
        val json = verify_assertion(result.response_json, key, credential, challenge, web_origin, true)
        assertEquals(listOf<Pair<Int?, Boolean>>(null to false), record.prompts)
        print_fixture("usb_preferred_pin", credential, challenge, json)
    }

    @Test
    fun key_without_pin_signs_without_verification_when_preferred() {
        val key = SoftAuthenticator(pin = null)
        val credential = key.register()
        val challenge = random_bytes(32)
        val record = FlowRecord()
        val result = run_flow(record, release_origin, listOf(nfc_target(key)), emptyList()) {
            assertion_options(challenge, uv_preferred, credential)
        }
        val json = verify_assertion(result.response_json, key, credential, challenge, release_origin, false)
        assertTrue(record.prompts.isEmpty())
        assertTrue(key.pin_subcommands.isEmpty())
        print_fixture("nfc_preferred_no_pin", credential, challenge, json)
    }

    @Test
    fun key_without_pin_reports_pin_not_set_when_verification_is_required() {
        val key = SoftAuthenticator(pin = null)
        val credential = key.register()
        val record = FlowRecord()
        expect_failure<SecurityKeyException.PinNotSet> {
            run_flow(record, release_origin, listOf(nfc_target(key)), emptyList()) {
                assertion_options(random_bytes(32), uv_required, credential)
            }
        }
        assertTrue(record.prompts.isEmpty())
        assertFalse(key.commands.contains(0x02))
        assertEquals(0, credential.counter)
    }

    @Test
    fun wrong_pin_reports_retries_and_then_accepts_the_right_pin() {
        val key = SoftAuthenticator(pin = good_pin)
        val credential = key.register()
        val challenge = random_bytes(32)
        val record = FlowRecord()
        val result = run_flow(record, release_origin, listOf(nfc_target(key)), listOf(wrong_pin, good_pin)) {
            assertion_options(challenge, uv_required, credential)
        }
        verify_assertion(result.response_json, key, credential, challenge, release_origin, true)
        assertEquals(listOf<Pair<Int?, Boolean>>(null to false, 7 to true), record.prompts)
        assertEquals(listOf(false), record.targets)
        assertEquals(8, key.pin_retries)
    }

    @Test
    fun three_wrong_pins_block_until_the_key_is_power_cycled() {
        val key = SoftAuthenticator(pin = good_pin)
        val credential = key.register()
        val record = FlowRecord()
        expect_failure<SecurityKeyException.PinAuthBlocked> {
            run_flow(record, release_origin, listOf(nfc_target(key)), listOf(wrong_pin, wrong_pin, wrong_pin)) {
                assertion_options(random_bytes(32), uv_required, credential)
            }
        }
        assertEquals(listOf<Pair<Int?, Boolean>>(null to false, 7 to true, 6 to true), record.prompts)
        assertEquals(5, key.pin_retries)
        assertFalse(key.commands.contains(0x02))

        expect_failure<SecurityKeyException.PinAuthBlocked> {
            run_flow(FlowRecord(), release_origin, listOf(nfc_target(key)), listOf(good_pin)) {
                assertion_options(random_bytes(32), uv_required, credential)
            }
        }

        key.power_cycle()
        val challenge = random_bytes(32)
        val result = run_flow(FlowRecord(), release_origin, listOf(nfc_target(key)), listOf(good_pin)) {
            assertion_options(challenge, uv_required, credential)
        }
        verify_assertion(result.response_json, key, credential, challenge, release_origin, true)
        assertEquals(8, key.pin_retries)
    }

    @Test
    fun last_wrong_pin_reports_a_blocked_pin() {
        val key = SoftAuthenticator(pin = good_pin)
        key.pin_retries = 1
        val credential = key.register()
        val record = FlowRecord()
        expect_failure<SecurityKeyException.PinBlocked> {
            run_flow(record, release_origin, listOf(nfc_target(key)), listOf(wrong_pin)) {
                assertion_options(random_bytes(32), uv_required, credential)
            }
        }
        assertEquals(listOf<Pair<Int?, Boolean>>(null to false), record.prompts)
        assertEquals(0, key.pin_retries)
        assertFalse(key.commands.contains(0x02))
    }

    @Test
    fun blocked_pin_is_reported_even_with_the_right_pin() {
        val key = SoftAuthenticator(pin = good_pin)
        key.pin_retries = 0
        val credential = key.register()
        expect_failure<SecurityKeyException.PinBlocked> {
            run_flow(FlowRecord(), release_origin, listOf(usb_target(key)), listOf(good_pin)) {
                assertion_options(random_bytes(32), uv_required, credential)
            }
        }
        assertEquals(0, credential.counter)
    }

    @Test
    fun cancelled_pin_prompt_ends_the_flow() {
        val key = SoftAuthenticator(pin = good_pin)
        val credential = key.register()
        expect_failure<PasskeyCancelledException> {
            run_flow(FlowRecord(), release_origin, listOf(nfc_target(key)), listOf(null)) {
                assertion_options(random_bytes(32), uv_preferred, credential)
            }
        }
        assertFalse(key.commands.contains(0x02))
    }

    @Test
    fun key_removed_at_touch_asks_for_another_tap_and_keeps_the_pin() {
        val key = SoftAuthenticator(pin = good_pin)
        val credential = key.register()
        val challenge = random_bytes(32)
        val record = FlowRecord()
        val result = run_flow(
            record,
            release_origin,
            listOf(nfc_target(key, remove_on_touch = true), nfc_target(key)),
            listOf(good_pin),
        ) {
            assertion_options(challenge, uv_required, credential)
        }
        verify_assertion(result.response_json, key, credential, challenge, release_origin, true)
        assertEquals(listOf(false, true), record.targets)
        assertEquals(listOf<Pair<Int?, Boolean>>(null to false), record.prompts)
        assertEquals(1, key.touches)
    }

    @Test
    fun usb_key_unplugged_at_touch_asks_for_it_again() {
        val key = SoftAuthenticator(pin = null)
        val credential = key.register()
        val challenge = random_bytes(32)
        val record = FlowRecord()
        val result = run_flow(
            record,
            release_origin,
            listOf(usb_target(key, remove_on_touch = true), usb_target(key)),
            emptyList(),
        ) {
            assertion_options(challenge, uv_preferred, credential)
        }
        verify_assertion(result.response_json, key, credential, challenge, release_origin, false)
        assertEquals(listOf(false, true), record.targets)
    }

    @Test
    fun key_removed_right_after_select_is_reported_as_removed() {
        val key = SoftAuthenticator(pin = good_pin)
        val credential = key.register()
        val connection = FakeNfcConnection(key, remove_after = 1)
        expect_failure<SecurityKeyException.Removed> {
            assert_with_security_key(
                connection,
                SecurityKeyRequest(assertion_options(random_bytes(32), uv_required, credential), release_origin),
                null,
            )
        }
        assertEquals(1, connection.apdus)
        assertTrue(key.u2f_commands.isEmpty())
        assertTrue(key.commands.isEmpty())
    }

    @Test
    fun flow_recovers_from_a_key_removed_right_after_select() {
        val key = SoftAuthenticator(pin = good_pin)
        val credential = key.register()
        val challenge = random_bytes(32)
        val record = FlowRecord()
        val result = run_flow(
            record,
            release_origin,
            listOf(nfc_target(key, remove_after = 1), nfc_target(key)),
            listOf(good_pin),
        ) {
            assertion_options(challenge, uv_required, credential)
        }
        verify_assertion(result.response_json, key, credential, challenge, release_origin, true)
        assertEquals(listOf(false, true), record.targets)
        assertEquals(listOf<Pair<Int?, Boolean>>(null to false), record.prompts)
    }

    @Test
    fun either_of_two_registered_keys_can_sign() {
        val key_a = SoftAuthenticator(pin = good_pin)
        val key_b = SoftAuthenticator(pin = good_pin)
        val credential_a = key_a.register()
        val credential_b = key_b.register()

        val challenge_b = random_bytes(32)
        val result_b = run_flow(FlowRecord(), release_origin, listOf(nfc_target(key_b)), listOf(good_pin)) {
            assertion_options(challenge_b, uv_required, credential_a, credential_b)
        }
        verify_assertion(result_b.response_json, key_b, credential_b, challenge_b, release_origin, true)

        val challenge_a = random_bytes(32)
        val result_a = run_flow(FlowRecord(), release_origin, listOf(usb_target(key_a)), listOf(good_pin)) {
            assertion_options(challenge_a, uv_required, credential_a, credential_b)
        }
        verify_assertion(result_a.response_json, key_a, credential_a, challenge_a, release_origin, true)
    }

    @Test
    fun either_of_two_keys_signs_when_the_key_reports_no_list_limit() {
        val key_a = SoftAuthenticator(pin = good_pin, ctap21 = false, max_creds_in_list = null)
        val key_b = SoftAuthenticator(pin = good_pin, ctap21 = false, max_creds_in_list = null)
        val credential_a = key_a.register()
        val credential_b = key_b.register()
        val challenge = random_bytes(32)
        val result = run_flow(FlowRecord(), release_origin, listOf(usb_target(key_b)), listOf(good_pin)) {
            assertion_options(challenge, uv_required, credential_a, credential_b)
        }
        verify_assertion(result.response_json, key_b, credential_b, challenge, release_origin, true)
        assertEquals(1, key_b.touches)
        assertTrue(key_a.commands.isEmpty())
    }

    @Test
    fun ctap20_key_uses_the_legacy_pin_token() {
        val key = SoftAuthenticator(pin = good_pin, ctap21 = false)
        val credential = key.register()
        val challenge = random_bytes(32)
        val result = run_flow(FlowRecord(), release_origin, listOf(nfc_target(key)), listOf(good_pin)) {
            assertion_options(challenge, uv_required, credential)
        }
        verify_assertion(result.response_json, key, credential, challenge, release_origin, true)
        assertTrue(key.pin_subcommands.contains(0x05))
        assertFalse(key.pin_subcommands.contains(0x09))
        assertTrue(key.pin_protocols.all { it == 1 })
    }

    @Test
    fun unregistered_key_with_pin_reports_not_registered() {
        val registered = SoftAuthenticator(pin = good_pin)
        val credential = registered.register()
        val other = SoftAuthenticator(pin = good_pin)
        other.register()
        val record = FlowRecord()
        expect_failure<SecurityKeyException.NotRegistered> {
            run_flow(record, release_origin, listOf(nfc_target(other)), listOf(good_pin)) {
                assertion_options(random_bytes(32), uv_required, credential)
            }
        }
        assertEquals(listOf<Pair<Int?, Boolean>>(null to false), record.prompts)
        assertEquals(0, other.credentials.single().counter)
    }

    @Test
    fun unregistered_key_without_pin_reports_not_registered() {
        val registered = SoftAuthenticator(pin = null)
        val credential = registered.register()
        val other = SoftAuthenticator(pin = null)
        val record = FlowRecord()
        expect_failure<SecurityKeyException.NotRegistered> {
            run_flow(record, release_origin, listOf(usb_target(other)), emptyList()) {
                assertion_options(random_bytes(32), uv_preferred, credential)
            }
        }
        assertTrue(record.prompts.isEmpty())
    }

    @Test
    fun long_credential_ids_survive_nfc_and_usb_chaining() {
        val nfc_key = SoftAuthenticator(pin = good_pin)
        val nfc_credential = nfc_key.register(id_size = 160)
        val nfc_challenge = random_bytes(32)
        val nfc_result = run_flow(FlowRecord(), release_origin, listOf(nfc_target(nfc_key)), listOf(good_pin)) {
            assertion_options(nfc_challenge, uv_required, nfc_credential)
        }
        verify_assertion(nfc_result.response_json, nfc_key, nfc_credential, nfc_challenge, release_origin, true)

        val usb_key = SoftAuthenticator(pin = good_pin)
        val usb_credential = usb_key.register(id_size = 160)
        val usb_challenge = random_bytes(32)
        val usb_result = run_flow(FlowRecord(), release_origin, listOf(usb_target(usb_key)), listOf(good_pin)) {
            assertion_options(usb_challenge, uv_required, usb_credential)
        }
        verify_assertion(usb_result.response_json, usb_key, usb_credential, usb_challenge, release_origin, true)
    }

    @Test
    fun tag_without_the_fido_applet_is_unsupported() {
        val key = SoftAuthenticator(pin = good_pin)
        val credential = key.register()
        expect_failure<SecurityKeyException.Unsupported> {
            assert_with_security_key(
                FakeNfcConnection(key, applet_present = false),
                SecurityKeyRequest(assertion_options(random_bytes(32), uv_required, credential), release_origin),
                null,
            )
        }
        assertTrue(key.commands.isEmpty())
    }

    @Test
    fun cancelling_during_touch_stops_the_nfc_request() {
        val key = SoftAuthenticator(pin = null)
        val credential = key.register()
        val state = CommandState()
        state.cancel()
        expect_failure<SecurityKeyException.Removed> {
            assert_with_security_key(
                FakeNfcConnection(key),
                SecurityKeyRequest(assertion_options(random_bytes(32), uv_preferred, credential), release_origin),
                null,
                state,
            )
        }
        assertEquals(0, key.touches)
        assertEquals(0, credential.counter)
    }

    @Test
    fun cancelling_during_touch_stops_the_usb_request() {
        val key = SoftAuthenticator(pin = null)
        val credential = key.register()
        val state = CommandState()
        state.cancel()
        expect_failure<SecurityKeyException.Removed> {
            assert_with_security_key(
                FakeHidConnection(key),
                SecurityKeyRequest(assertion_options(random_bytes(32), uv_preferred, credential), release_origin),
                null,
                state,
            )
        }
        assertEquals(0, key.touches)
        assertEquals(0, credential.counter)
    }

    @Test
    fun empty_allow_list_is_not_registered_without_touching_the_key() {
        val key = SoftAuthenticator(pin = good_pin)
        val connection = FakeNfcConnection(key)
        expect_failure<SecurityKeyException.NotRegistered> {
            assert_with_security_key(
                connection,
                SecurityKeyRequest(assertion_options(random_bytes(32), uv_required), release_origin),
                null,
            )
        }
        assertEquals(0, connection.apdus)
    }

    @Test
    fun stale_options_are_fetched_again_before_the_key_is_used() {
        val key = SoftAuthenticator(pin = null)
        val credential = key.register()
        val challenges = listOf(random_bytes(32), random_bytes(32))
        val record = FlowRecord()
        var clock = 0L
        val target = nfc_target(key)
        val result = runBlocking {
            run_security_key_flow(
                prepare = {
                    record.prepares += 1
                    assertion_options(challenges[record.prepares - 1], uv_preferred, credential)
                },
                origin = release_origin,
                next_target = {
                    clock = security_key_options_max_age_ms + 1_000L
                    target
                },
                request_pin = { _, _ -> null },
                on_working = {},
                now_ms = { clock },
                run_on_key = { block -> block(CommandState()) },
            )
        }
        assertEquals(2, record.prepares)
        assertEquals(b64url(challenges[1]), result.options.challenge)
        verify_assertion(result.response_json, key, credential, challenges[1], release_origin, false)
    }

    @Test
    fun client_data_normalizes_a_padded_challenge() {
        val challenge = byteArrayOf(0xFB.toByte(), 0xFF.toByte(), 0xFE.toByte(), 0x01)
        val padded = Base64.getEncoder().encodeToString(challenge)
        assertTrue(padded.contains('+') || padded.contains('/') || padded.endsWith("="))
        val client = JSONObject(String(security_key_client_data_json(padded, release_origin), Charsets.UTF_8))
        assertEquals(b64url(challenge), client.getString("challenge"))
        assertEquals(release_origin, client.getString("origin"))
        assertEquals("webauthn.get", client.getString("type"))
        assertFalse(client.getBoolean("crossOrigin"))
        assertFalse(client.has("tokenBinding"))
    }

    @Test
    fun usb_key_that_fails_to_open_while_attached_is_retried_in_place() {
        val key = SoftAuthenticator(pin = good_pin)
        val credential = key.register()
        val challenge = random_bytes(32)
        val record = FlowRecord()
        val target = FlakyUsbTarget(key, listOf(IOException("transfer failed")), still_attached = true)
        val result = run_flow(record, release_origin, listOf(target), listOf(good_pin)) {
            assertion_options(challenge, uv_required, credential)
        }
        verify_assertion(result.response_json, key, credential, challenge, release_origin, true)
        assertEquals(listOf(false), record.targets)
        assertEquals(listOf<Pair<Int?, Boolean>>(null to false), record.prompts)
        assertEquals(3, target.opens)
    }

    @Test
    fun usb_key_that_keeps_failing_while_attached_reports_an_error_instead_of_waiting() {
        val key = SoftAuthenticator(pin = good_pin)
        val credential = key.register()
        val record = FlowRecord()
        val failures = List(security_key_attached_retries + 1) { IOException("transfer failed") }
        val target = FlakyUsbTarget(key, failures, still_attached = true)
        val failure = expect_failure<SecurityKeyException.Failed> {
            run_flow(record, release_origin, listOf(target), emptyList()) {
                assertion_options(random_bytes(32), uv_required, credential)
            }
        }
        assertTrue(failure.cause is IOException)
        assertEquals(listOf(false), record.targets)
        assertEquals(security_key_attached_retries + 1, target.opens)
        assertTrue(record.prompts.isEmpty())
    }

    @Test
    fun usb_key_unplugged_while_the_pin_is_typed_is_asked_for_again_and_keeps_the_pin() {
        val key = SoftAuthenticator(pin = good_pin)
        val credential = key.register()
        val challenge = random_bytes(32)
        val record = FlowRecord()
        val first = object : SecurityKeyTarget {
            var opens = 0
            override fun open(): YubiKeyConnection {
                opens += 1
                if (opens > 1) throw IllegalStateException("Device access not permitted")
                return FakeHidConnection(key)
            }
        }
        val result = run_flow(record, release_origin, listOf(first, usb_target(key)), listOf(good_pin)) {
            assertion_options(challenge, uv_required, credential)
        }
        verify_assertion(result.response_json, key, credential, challenge, release_origin, true)
        assertEquals(listOf(false, true), record.targets)
        assertEquals(listOf<Pair<Int?, Boolean>>(null to false), record.prompts)
        assertEquals(2, first.opens)
    }

    @Test
    fun open_failures_map_to_removal_only_for_transport_and_state_errors() {
        assertTrue(map_security_key_open_failure(IllegalStateException("gone")) is SecurityKeyException.Removed)
        assertTrue(map_security_key_open_failure(IOException("gone")) is SecurityKeyException.Removed)
        assertTrue(map_security_key_open_failure(IllegalArgumentException("bad")) is SecurityKeyException.Failed)
    }
}
