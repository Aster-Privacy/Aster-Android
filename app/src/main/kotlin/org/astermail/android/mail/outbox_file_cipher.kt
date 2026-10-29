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

package org.astermail.android.mail

import java.io.File
import java.nio.file.Files
import java.nio.file.StandardCopyOption
import java.security.SecureRandom
import javax.crypto.Cipher
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.SecretKeySpec

class OutboxFileKeyStore(
    private val read_encoded: () -> String?,
    private val write_encoded: (String) -> Boolean,
    private val encode: (ByteArray) -> String,
    private val decode: (String) -> ByteArray?,
    private val new_key: () -> ByteArray = { ByteArray(32).also { SecureRandom().nextBytes(it) } },
) {
    private val lock = Any()
    private var cached: ByteArray? = null

    fun key(): ByteArray? = synchronized(lock) {
        cached?.let { return it.copyOf() }
        val stored = try {
            read_encoded()
        } catch (_: Throwable) {
            return null
        }
        val key = if (stored != null) {
            decode(stored)?.takeIf { it.size == 32 } ?: return null
        } else {
            val fresh = new_key()
            val written = try {
                write_encoded(encode(fresh))
            } catch (_: Throwable) {
                false
            }
            if (!written) {
                fresh.fill(0)
                return null
            }
            fresh
        }
        cached = key
        key.copyOf()
    }
}

fun reseal_outbox_file(file: File, cipher: OutboxFileCipher, plaintext: ByteArray): Boolean {
    val tmp = File(file.parentFile, "${file.name}.${System.nanoTime()}.tmp")
    return try {
        tmp.writeBytes(cipher.seal(file.name, plaintext))
        Files.move(tmp.toPath(), file.toPath(), StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE)
        true
    } catch (_: Exception) {
        tmp.delete()
        false
    }
}

class OutboxFileCipher(private val key_source: () -> ByteArray?) {

    fun seal(name: String, plaintext: ByteArray): ByteArray {
        val key = key_source() ?: throw java.io.IOException("outbox file key unavailable")
        try {
            val nonce = ByteArray(nonce_size).also { SecureRandom().nextBytes(it) }
            val cipher = Cipher.getInstance(transformation)
            cipher.init(Cipher.ENCRYPT_MODE, SecretKeySpec(key, "AES"), GCMParameterSpec(tag_bits, nonce))
            cipher.updateAAD(aad_for(name))
            return magic + nonce + cipher.doFinal(plaintext)
        } finally {
            key.fill(0)
        }
    }

    fun open(name: String, stored: ByteArray): ByteArray {
        if (!is_sealed(stored)) return stored
        val key = key_source() ?: throw OutboxPayloadException("outbox file key unavailable")
        try {
            val nonce = stored.copyOfRange(magic.size, magic.size + nonce_size)
            val body = stored.copyOfRange(magic.size + nonce_size, stored.size)
            val cipher = Cipher.getInstance(transformation)
            cipher.init(Cipher.DECRYPT_MODE, SecretKeySpec(key, "AES"), GCMParameterSpec(tag_bits, nonce))
            cipher.updateAAD(aad_for(name))
            return cipher.doFinal(body)
        } catch (err: java.security.GeneralSecurityException) {
            throw OutboxPayloadException("outbox file could not be opened", err)
        } finally {
            key.fill(0)
        }
    }

    companion object {
        private const val transformation = "AES/GCM/NoPadding"
        private const val nonce_size = 12
        private const val tag_bits = 128
        private val magic = byteArrayOf(0x41, 0x4f, 0x58, 0x01)

        fun is_sealed(stored: ByteArray): Boolean =
            stored.size >= magic.size + nonce_size + tag_bits / 8 &&
                stored.copyOfRange(0, magic.size).contentEquals(magic)

        fun aad_for(name: String): ByteArray = "aster.outbox.v1\u0000$name".toByteArray(Charsets.UTF_8)
    }
}
