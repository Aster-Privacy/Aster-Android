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

package org.astermail.android.crypto

import java.io.ByteArrayInputStream
import java.security.MessageDigest
import java.util.Base64
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.serialization.json.Json
import org.astermail.android.api.encryption.EncryptionApi
import org.astermail.android.api.encryption.ExternalPgpKeyInfo
import org.astermail.android.storage.SessionKeyStore
import org.bouncycastle.bcpg.PublicKeyAlgorithmTags
import org.bouncycastle.bcpg.SecretKeyPacket
import org.bouncycastle.jce.provider.BouncyCastleProvider
import org.bouncycastle.openpgp.PGPPrivateKey
import org.bouncycastle.openpgp.PGPSecretKey
import org.bouncycastle.openpgp.PGPSecretKeyRing
import org.bouncycastle.openpgp.operator.jcajce.JcaKeyFingerprintCalculator

class UnlockedExternalPgpKey(
    val fingerprint: String,
    val decryption_keys: Map<Long, PGPPrivateKey>,
    val signing_key: PGPPrivateKey?,
    val expires_at_ms: Long?,
    val revoked: Boolean,
) {
    fun can_sign(now_ms: Long): Boolean {
        if (signing_key == null || revoked) return false
        val expiry = expires_at_ms ?: return true
        return now_ms < expiry
    }
}

object ExternalPgpKeyCodec {
    const val LABEL = "aster-external-pgp-v1"
    const val BLOB_VERSION: Byte = 0x01
    private const val NONCE_BYTES = 12
    private const val TAG_BYTES = 16
    private const val KEY_BYTES = 32
    private const val V4_FINGERPRINT_HEX_LEN = 40

    init {
        java.security.Security.removeProvider(BouncyCastleProvider.PROVIDER_NAME)
        java.security.Security.addProvider(BouncyCastleProvider())
    }

    fun normalize_fingerprint(fingerprint: String): String =
        fingerprint.filterNot { it.isWhitespace() || it == ':' }.lowercase()

    fun derive_wrap_key(data_kek: ByteArray, user_id: String): ByteArray {
        require(data_kek.size == KEY_BYTES)
        return hkdf_sha256(
            data_kek,
            LABEL.toByteArray(Charsets.UTF_8),
            user_id.lowercase().toByteArray(Charsets.UTF_8),
            KEY_BYTES,
        )
    }

    fun aad(user_id: String, fingerprint: String): ByteArray =
        "$LABEL|${user_id.lowercase()}|${normalize_fingerprint(fingerprint)}".toByteArray(Charsets.UTF_8)

    fun wrap(
        secret_key_bytes: ByteArray,
        data_kek: ByteArray,
        user_id: String,
        fingerprint: String,
        nonce: ByteArray,
    ): String {
        require(nonce.size == NONCE_BYTES)
        val wrap_key = derive_wrap_key(data_kek, user_id)
        try {
            val sealed = AesGcm.encrypt(wrap_key, nonce, secret_key_bytes, aad(user_id, fingerprint))
            return Base64.getEncoder().encodeToString(byteArrayOf(BLOB_VERSION) + nonce + sealed)
        } finally {
            wrap_key.fill(0)
        }
    }

    fun unwrap(
        wrapped_b64: String,
        data_kek: ByteArray,
        user_id: String,
        fingerprint: String,
    ): ByteArray? {
        val blob = runCatching { Base64.getDecoder().decode(wrapped_b64.trim()) }.getOrNull() ?: return null
        if (blob.size < 1 + NONCE_BYTES + TAG_BYTES || blob[0] != BLOB_VERSION) return null
        if (data_kek.size != KEY_BYTES || user_id.isBlank()) return null
        if (normalize_fingerprint(fingerprint).length != V4_FINGERPRINT_HEX_LEN) return null
        val nonce = blob.copyOfRange(1, 1 + NONCE_BYTES)
        val sealed = blob.copyOfRange(1 + NONCE_BYTES, blob.size)
        val wrap_key = derive_wrap_key(data_kek, user_id)
        return try {
            AesGcm.decrypt(wrap_key, nonce, sealed, aad(user_id, fingerprint))
        } catch (_: Throwable) {
            null
        } finally {
            wrap_key.fill(0)
        }
    }

    fun parse_unlocked(secret_key_bytes: ByteArray, expected_fingerprint: String): UnlockedExternalPgpKey? {
        return try {
            val ring = PGPSecretKeyRing(ByteArrayInputStream(secret_key_bytes), JcaKeyFingerprintCalculator())
            val primary = ring.publicKey
            if (primary.version != 4) return null
            val fingerprint = hex(primary.fingerprint)
            if (fingerprint != normalize_fingerprint(expected_fingerprint)) return null

            val decryption = LinkedHashMap<Long, PGPPrivateKey>()
            var primary_signing: PGPPrivateKey? = null
            var subkey_signing: PGPPrivateKey? = null
            for (secret_key in ring.secretKeys) {
                if (secret_key.isPrivateKeyEmpty) continue
                if (secret_key.s2KUsage != SecretKeyPacket.USAGE_NONE) return null
                val private_key = secret_key.extractPrivateKey(null) ?: continue
                if (secret_key.publicKey.hasRevocation()) continue
                if (secret_key.publicKey.isEncryptionKey) decryption[secret_key.keyID] = private_key
                if (secret_key.isSigningKey && is_signing_algorithm(secret_key)) {
                    if (secret_key.isMasterKey) primary_signing = private_key
                    else if (subkey_signing == null) subkey_signing = private_key
                }
            }
            val signing = primary_signing ?: subkey_signing
            if (decryption.isEmpty() && signing == null) return null

            val valid_seconds = primary.validSeconds
            val expires_at_ms = if (valid_seconds > 0) primary.creationTime.time + valid_seconds * 1000 else null
            UnlockedExternalPgpKey(
                fingerprint = fingerprint,
                decryption_keys = decryption,
                signing_key = signing,
                expires_at_ms = expires_at_ms,
                revoked = primary.hasRevocation(),
            )
        } catch (_: Throwable) {
            null
        }
    }

    fun open(
        info: ExternalPgpKeyInfo,
        data_kek: ByteArray,
        user_id: String,
    ): UnlockedExternalPgpKey? {
        if (info.wrapped_private_key.isBlank() || info.fingerprint.isBlank()) return null
        val plaintext = unwrap(info.wrapped_private_key, data_kek, user_id, info.fingerprint) ?: return null
        return try {
            parse_unlocked(plaintext, info.fingerprint)
        } finally {
            plaintext.fill(0)
        }
    }

    private fun is_signing_algorithm(secret_key: PGPSecretKey): Boolean =
        when (secret_key.publicKey.algorithm) {
            PublicKeyAlgorithmTags.ECDH,
            PublicKeyAlgorithmTags.ELGAMAL_ENCRYPT,
            PublicKeyAlgorithmTags.RSA_ENCRYPT,
            -> false
            else -> true
        }

    private fun hex(bytes: ByteArray): String = bytes.joinToString("") { "%02x".format(it) }
}

@Singleton
class ExternalPgpKeyStore @Inject constructor(
    private val session_key_store: SessionKeyStore,
    private val encryption_api: dagger.Lazy<EncryptionApi>,
) {
    private val lock = Any()

    @Volatile
    private var unlocked: UnlockedExternalPgpKey? = null

    @Volatile
    private var failed_blob_digest: String? = null

    @Volatile
    private var generation = 0L

    private val _info = MutableStateFlow(read_cached_info())
    val info: StateFlow<ExternalPgpKeyInfo?> = _info.asStateFlow()

    init {
        session_key_store.add_clear_listener { forget() }
    }

    suspend fun load() {
        val started = session_snapshot()
        val fetched = try {
            encryption_api.get().get_external_pgp_key()
        } catch (t: CancellationException) {
            throw t
        } catch (_: Throwable) {
            if (_info.value == null) _info.value = read_cached_info()
            ensure_unlocked()
            return
        }
        remember(fetched, started)
    }

    suspend fun stop_publishing(): Boolean {
        val started = session_snapshot()
        val updated = try {
            encryption_api.get().set_external_pgp_key_published(false)
        } catch (t: CancellationException) {
            throw t
        } catch (_: Throwable) {
            return false
        }
        val previous_wrapped = _info.value?.wrapped_private_key.orEmpty()
        return remember(
            updated.copy(wrapped_private_key = updated.wrapped_private_key.ifBlank { previous_wrapped }, published = false),
            started,
        )
    }

    fun decryption_keys(): Map<Long, PGPPrivateKey> = ensure_unlocked()?.decryption_keys.orEmpty()

    fun signing_key_if_ready(now_ms: Long = System.currentTimeMillis()): PGPPrivateKey? {
        val current = _info.value ?: return null
        if (!current.published) return null
        val key = ensure_unlocked() ?: return null
        return if (key.can_sign(now_ms)) key.signing_key else null
    }

    fun forget() {
        synchronized(lock) {
            generation += 1
            unlocked = null
            failed_blob_digest = null
            _info.value = null
        }
    }

    private fun session_snapshot(): Pair<Long, String?> =
        synchronized(lock) { generation to session_key_store.get_user_id() }

    private fun remember(fetched: ExternalPgpKeyInfo?, started: Pair<Long, String?>): Boolean {
        synchronized(lock) {
            if (session_snapshot() != started) return false
            val previous = _info.value
            if (previous == null || fetched == null ||
                previous.wrapped_private_key != fetched.wrapped_private_key ||
                previous.fingerprint != fetched.fingerprint
            ) {
                unlocked = null
                failed_blob_digest = null
            }
            _info.value = fetched
            session_key_store.put_external_pgp_blob(
                fetched?.let { cache_json.encodeToString(ExternalPgpKeyInfo.serializer(), it) },
            )
        }
        ensure_unlocked()
        return true
    }

    private fun ensure_unlocked(): UnlockedExternalPgpKey? {
        unlocked?.let { return it }
        synchronized(lock) {
            unlocked?.let { return it }
            val current = _info.value ?: return null
            val user_id = session_key_store.get_user_id() ?: return null
            val digest = blob_digest(current, user_id)
            if (failed_blob_digest == digest) return null
            val data_kek = session_key_store.get_data_kek() ?: return null
            val opened = try {
                ExternalPgpKeyCodec.open(current, data_kek, user_id)
            } finally {
                data_kek.fill(0)
            }
            if (opened == null) failed_blob_digest = digest else unlocked = opened
            return opened
        }
    }

    private fun read_cached_info(): ExternalPgpKeyInfo? {
        val raw = session_key_store.get_external_pgp_blob() ?: return null
        return runCatching { cache_json.decodeFromString(ExternalPgpKeyInfo.serializer(), raw) }.getOrNull()
    }

    private fun blob_digest(info: ExternalPgpKeyInfo, user_id: String): String {
        val digest = MessageDigest.getInstance("SHA-256")
        for (part in listOf(info.fingerprint, info.wrapped_private_key, user_id)) {
            digest.update(part.toByteArray(Charsets.UTF_8))
            digest.update(0.toByte())
        }
        return digest.digest().joinToString("") { "%02x".format(it) }
    }

    private companion object {
        val cache_json = Json {
            ignoreUnknownKeys = true
            encodeDefaults = true
        }
    }
}
