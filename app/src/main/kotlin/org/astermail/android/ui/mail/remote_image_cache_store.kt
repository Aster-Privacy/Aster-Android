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

package org.astermail.android.ui.mail

import java.io.ByteArrayOutputStream
import java.io.File
import java.io.FilterInputStream
import java.io.InputStream
import java.nio.ByteBuffer
import java.security.MessageDigest
import java.security.SecureRandom
import javax.crypto.AEADBadTagException
import javax.crypto.Cipher
import javax.crypto.Mac
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.SecretKeySpec
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull

internal const val REMOTE_IMAGE_CACHE_MAX_ENTRY_BYTES = 5 * 1024 * 1024
internal const val REMOTE_IMAGE_CACHE_MAX_TOTAL_BYTES = 64L * 1024 * 1024
internal const val REMOTE_IMAGE_CACHE_TTL_MS = 7L * 24 * 60 * 60 * 1000

internal data class remote_image_cache_limits(
    val max_entry_bytes: Int = REMOTE_IMAGE_CACHE_MAX_ENTRY_BYTES,
    val max_total_bytes: Long = REMOTE_IMAGE_CACHE_MAX_TOTAL_BYTES,
    val ttl_ms: Long = REMOTE_IMAGE_CACHE_TTL_MS,
)

internal class cached_remote_image(val content_type: String, val bytes: ByteArray)

internal interface remote_image_key_sealer {
    fun seal(plain: ByteArray, aad: ByteArray): ByteArray
    fun open(sealed: ByteArray, aad: ByteArray): ByteArray?
}

internal class gcm_key_sealer(private val key: () -> SecretKey) : remote_image_key_sealer {
    override fun seal(plain: ByteArray, aad: ByteArray): ByteArray {
        val cipher = Cipher.getInstance(GCM_TRANSFORMATION)
        cipher.init(Cipher.ENCRYPT_MODE, key())
        cipher.updateAAD(aad)
        val body = cipher.doFinal(plain)
        return cipher.iv + body
    }

    override fun open(sealed: ByteArray, aad: ByteArray): ByteArray? {
        if (sealed.size <= GCM_IV_BYTES) return null
        val cipher = Cipher.getInstance(GCM_TRANSFORMATION)
        cipher.init(Cipher.DECRYPT_MODE, key(), GCMParameterSpec(GCM_TAG_BITS, sealed, 0, GCM_IV_BYTES))
        cipher.updateAAD(aad)
        return try {
            cipher.doFinal(sealed, GCM_IV_BYTES, sealed.size - GCM_IV_BYTES)
        } catch (_: AEADBadTagException) {
            null
        }
    }
}

internal const val GCM_TRANSFORMATION = "AES/GCM/NoPadding"
internal const val GCM_IV_BYTES = 12
internal const val GCM_TAG_BITS = 128

internal fun remote_image_cache_key(url: String?): String? {
    val raw = url?.trim()?.takeIf { it.isNotEmpty() } ?: return null
    val parsed = raw.toHttpUrlOrNull() ?: return null
    val original = if (
        parsed.host == REMOTE_IMAGE_PROXY_HOST && parsed.encodedPath == REMOTE_IMAGE_PROXY_PATH
    ) {
        parsed.queryParameter("url")?.trim()?.toHttpUrlOrNull() ?: return null
    } else {
        parsed
    }
    return original.newBuilder().fragment(null).build().toString()
}

internal fun remote_image_pixel_size(bytes: ByteArray): Pair<Int, Int>? {
    fun u8(i: Int): Int = bytes[i].toInt() and 0xff
    fun be16(i: Int): Int = (u8(i) shl 8) or u8(i + 1)
    fun le16(i: Int): Int = u8(i) or (u8(i + 1) shl 8)
    fun le24(i: Int): Int = u8(i) or (u8(i + 1) shl 8) or (u8(i + 2) shl 16)
    fun be32(i: Int): Long = (be16(i).toLong() shl 16) or be16(i + 2).toLong()
    return runCatching {
        when {
            bytes.size >= 24 && u8(0) == 0x89 && u8(1) == 0x50 && u8(2) == 0x4e && u8(3) == 0x47 ->
                be32(16).toInt() to be32(20).toInt()
            bytes.size >= 10 && u8(0) == 0x47 && u8(1) == 0x49 && u8(2) == 0x46 ->
                le16(6) to le16(8)
            bytes.size >= 30 && String(bytes, 0, 4, Charsets.US_ASCII) == "RIFF" &&
                String(bytes, 8, 4, Charsets.US_ASCII) == "WEBP" -> when (String(bytes, 12, 4, Charsets.US_ASCII)) {
                "VP8X" -> (le24(24) + 1) to (le24(27) + 1)
                "VP8 " -> (le16(26) and 0x3fff) to (le16(28) and 0x3fff)
                "VP8L" -> {
                    val b = (u8(21).toLong()) or (u8(22).toLong() shl 8) or (u8(23).toLong() shl 16) or
                        (u8(24).toLong() shl 24)
                    ((b and 0x3fff) + 1).toInt() to (((b shr 14) and 0x3fff) + 1).toInt()
                }
                else -> null
            }
            bytes.size >= 4 && u8(0) == 0xff && u8(1) == 0xd8 -> jpeg_pixel_size(bytes)
            else -> null
        }
    }.getOrNull()
}

private fun jpeg_pixel_size(bytes: ByteArray): Pair<Int, Int>? {
    fun u8(i: Int): Int = bytes[i].toInt() and 0xff
    var i = 2
    while (i + 9 < bytes.size) {
        if (u8(i) != 0xff) {
            i++
            continue
        }
        val marker = u8(i + 1)
        if (marker == 0xff) {
            i++
            continue
        }
        if (marker == 0xd8 || marker == 0x01 || marker in 0xd0..0xd7) {
            i += 2
            continue
        }
        val length = (u8(i + 2) shl 8) or u8(i + 3)
        val is_frame = marker in 0xc0..0xcf && marker != 0xc4 && marker != 0xc8 && marker != 0xcc
        if (is_frame) {
            val height = (u8(i + 5) shl 8) or u8(i + 6)
            val width = (u8(i + 7) shl 8) or u8(i + 8)
            return width to height
        }
        if (length < 2) return null
        i += 2 + length
    }
    return null
}

internal const val REMOTE_IMAGE_PIXEL_MAX_DIMENSION = 4

internal fun remote_image_is_storable(
    url: String?,
    status_code: Int,
    content_type: String?,
    bytes: ByteArray,
    max_entry_bytes: Int = REMOTE_IMAGE_CACHE_MAX_ENTRY_BYTES,
): Boolean {
    if (status_code != 200) return false
    val key = remote_image_cache_key(url) ?: return false
    if (EmailHtmlSanitizer.is_tracker_url(key)) return false
    val type = content_type?.substringBefore(';')?.trim()?.lowercase().orEmpty()
    if (!type.startsWith("image/")) return false
    if (bytes.isEmpty() || bytes.size > max_entry_bytes) return false
    val size = remote_image_pixel_size(bytes)
    if (size != null && size.first <= REMOTE_IMAGE_PIXEL_MAX_DIMENSION && size.second <= REMOTE_IMAGE_PIXEL_MAX_DIMENSION) {
        return false
    }
    return true
}

internal class remote_image_caching_stream(
    source: InputStream,
    private val limit: Int,
    private val on_complete: (ByteArray) -> Unit,
) : FilterInputStream(source) {
    private val buffer = ByteArrayOutputStream()
    private var overflowed = false
    private var finished = false

    private fun finish() {
        if (finished) return
        finished = true
        if (!overflowed) runCatching { on_complete(buffer.toByteArray()) }
        buffer.reset()
    }

    private fun record(bytes: ByteArray, off: Int, len: Int) {
        if (overflowed || finished) return
        if (buffer.size() + len > limit) {
            overflowed = true
            buffer.reset()
            return
        }
        buffer.write(bytes, off, len)
    }

    override fun read(): Int {
        val value = super.read()
        if (value < 0) finish() else record(byteArrayOf(value.toByte()), 0, 1)
        return value
    }

    override fun read(b: ByteArray, off: Int, len: Int): Int {
        val count = super.read(b, off, len)
        if (count < 0) finish() else if (count > 0) record(b, off, count)
        return count
    }

    override fun skip(n: Long): Long {
        overflowed = true
        buffer.reset()
        return super.skip(n)
    }

    override fun markSupported(): Boolean = false

    override fun close() {
        finished = true
        buffer.reset()
        super.close()
    }
}

internal class remote_image_cache_store(
    private val root: File,
    private val key_sealer: remote_image_key_sealer,
    private val clock: () -> Long = System::currentTimeMillis,
    private val limits: remote_image_cache_limits = remote_image_cache_limits(),
    private val random: SecureRandom = SecureRandom(),
) {
    private class scope_keys(val data_key: SecretKey, val name_key: ByteArray)

    private val lock = Any()
    private val keys = HashMap<String, scope_keys>()
    private val unseal_failures = HashMap<String, Int>()
    private var clear_generation = 0L

    fun generation(): Long = synchronized(lock) { clear_generation }

    fun scope_name(account_id: String): String = sha256_hex("aster-remote-image-cache:$account_id").take(32)

    private fun scope_dir(account_id: String): File = File(root, scope_name(account_id))

    private fun key_aad(scope: String): ByteArray = "remote-image-cache-key:v1:$scope".toByteArray(Charsets.UTF_8)

    private fun keys_for(account_id: String, create: Boolean): scope_keys? {
        val scope = scope_name(account_id)
        keys[scope]?.let { return it }
        val dir = File(root, scope)
        val key_file = File(dir, KEY_FILE)
        val existing = if (key_file.isFile) {
            val opened = runCatching { key_sealer.open(key_file.readBytes(), key_aad(scope)) }
            if (opened.isFailure) {
                val failures = (unseal_failures[scope] ?: 0) + 1
                unseal_failures[scope] = failures
                if (failures < MAX_TRANSIENT_UNSEAL_FAILURES) return null
            }
            opened.getOrNull()
        } else {
            null
        }
        val material = if (existing != null && existing.size == KEY_MATERIAL_BYTES) {
            existing
        } else {
            if (!create) return null
            unseal_failures.remove(scope)
            dir.deleteRecursively()
            if (!dir.mkdirs() && !dir.isDirectory) return null
            val fresh = ByteArray(KEY_MATERIAL_BYTES).also { random.nextBytes(it) }
            val sealed = key_sealer.seal(fresh, key_aad(scope))
            if (!write_atomic(key_file, sealed)) return null
            fresh
        }
        val loaded = scope_keys(
            data_key = SecretKeySpec(material.copyOfRange(0, 32), "AES"),
            name_key = material.copyOfRange(32, 64),
        )
        material.fill(0)
        unseal_failures.remove(scope)
        keys[scope] = loaded
        return loaded
    }

    private fun name_for(keys: scope_keys, url_key: String): String {
        val mac = Mac.getInstance("HmacSHA256")
        mac.init(SecretKeySpec(keys.name_key, "HmacSHA256"))
        return mac.doFinal(url_key.toByteArray(Charsets.UTF_8)).joinToString("") { "%02x".format(it) }
    }

    fun entry_name(account_id: String, url: String): String? = synchronized(lock) {
        val url_key = remote_image_cache_key(url) ?: return null
        val scope_keys = keys_for(account_id, create = true) ?: return null
        name_for(scope_keys, url_key)
    }

    private fun entry_aad(scope: String, name: String): ByteArray =
        "remote-image-cache-entry:v1:$scope:$name".toByteArray(Charsets.UTF_8)

    fun get(account_id: String, url: String): cached_remote_image? = synchronized(lock) {
        val url_key = remote_image_cache_key(url) ?: return null
        val scope = scope_name(account_id)
        val scope_keys = keys_for(account_id, create = false) ?: return null
        val name = name_for(scope_keys, url_key)
        val file = File(File(root, scope), name + ENTRY_SUFFIX)
        if (!file.isFile) return null
        val sealed = runCatching { file.readBytes() }.getOrNull() ?: return null
        val plain = open_entry(scope_keys.data_key, sealed, entry_aad(scope, name))
        if (plain == null) {
            file.delete()
            return null
        }
        val parsed = parse_entry(plain)
        if (parsed == null) {
            file.delete()
            return null
        }
        val (created_at, image) = parsed
        val now = clock()
        if (now - created_at >= limits.ttl_ms || created_at - now > limits.ttl_ms) {
            file.delete()
            return null
        }
        file.setLastModified(now)
        image
    }

    fun contains(account_id: String, url: String): Boolean = get(account_id, url) != null

    fun put(
        account_id: String,
        url: String,
        status_code: Int,
        content_type: String?,
        bytes: ByteArray,
        expected_generation: Long? = null,
    ): Boolean {
        if (!remote_image_is_storable(url, status_code, content_type, bytes, limits.max_entry_bytes)) return false
        val url_key = remote_image_cache_key(url) ?: return false
        val type = content_type!!.substringBefore(';').trim().lowercase()
        return synchronized(lock) {
            if (expected_generation != null && expected_generation != clear_generation) return@synchronized false
            val scope = scope_name(account_id)
            val scope_keys = keys_for(account_id, create = true) ?: return@synchronized false
            val name = name_for(scope_keys, url_key)
            val plain = encode_entry(clock(), type, bytes)
            val sealed = runCatching {
                seal_entry(scope_keys.data_key, plain, entry_aad(scope, name))
            }.getOrNull()
            plain.fill(0)
            if (sealed == null) return@synchronized false
            val file = File(File(root, scope), name + ENTRY_SUFFIX)
            if (!write_atomic(file, sealed)) return@synchronized false
            file.setLastModified(clock())
            enforce_limits()
            file.isFile
        }
    }

    fun clear(account_id: String) = synchronized(lock) {
        clear_generation++
        val scope = scope_name(account_id)
        keys.remove(scope)
        unseal_failures.remove(scope)
        File(root, scope).deleteRecursively()
    }

    fun clear_all() = synchronized(lock) {
        clear_generation++
        keys.clear()
        unseal_failures.clear()
        root.listFiles()?.forEach { it.deleteRecursively() }
    }

    fun total_bytes(): Long = synchronized(lock) { entry_files().sumOf { it.length() } }

    private fun entry_files(): List<File> =
        root.listFiles { file -> file.isDirectory }.orEmpty()
            .flatMap { dir -> dir.listFiles { file -> file.isFile && file.name.endsWith(ENTRY_SUFFIX) }.orEmpty().toList() }

    private fun enforce_limits() {
        val now = clock()
        val files = entry_files().toMutableList()
        files.removeAll { file ->
            val stale = now - file.lastModified() >= limits.ttl_ms
            if (stale) file.delete()
            stale
        }
        var total = files.sumOf { it.length() }
        if (total <= limits.max_total_bytes) return
        for (file in files.sortedBy { it.lastModified() }) {
            if (total <= limits.max_total_bytes) break
            val size = file.length()
            if (file.delete()) total -= size
        }
    }

    private fun seal_entry(key: SecretKey, plain: ByteArray, aad: ByteArray): ByteArray {
        val iv = ByteArray(GCM_IV_BYTES).also { random.nextBytes(it) }
        val cipher = Cipher.getInstance(GCM_TRANSFORMATION)
        cipher.init(Cipher.ENCRYPT_MODE, key, GCMParameterSpec(GCM_TAG_BITS, iv))
        cipher.updateAAD(aad)
        return byteArrayOf(ENTRY_VERSION) + iv + cipher.doFinal(plain)
    }

    private fun open_entry(key: SecretKey, sealed: ByteArray, aad: ByteArray): ByteArray? = runCatching {
        if (sealed.size <= 1 + GCM_IV_BYTES || sealed[0] != ENTRY_VERSION) return null
        val cipher = Cipher.getInstance(GCM_TRANSFORMATION)
        cipher.init(Cipher.DECRYPT_MODE, key, GCMParameterSpec(GCM_TAG_BITS, sealed, 1, GCM_IV_BYTES))
        cipher.updateAAD(aad)
        cipher.doFinal(sealed, 1 + GCM_IV_BYTES, sealed.size - 1 - GCM_IV_BYTES)
    }.getOrNull()

    private fun encode_entry(created_at: Long, content_type: String, bytes: ByteArray): ByteArray {
        val type_bytes = content_type.toByteArray(Charsets.UTF_8)
        return ByteBuffer.allocate(8 + 2 + type_bytes.size + bytes.size)
            .putLong(created_at)
            .putShort(type_bytes.size.toShort())
            .put(type_bytes)
            .put(bytes)
            .array()
    }

    private fun parse_entry(plain: ByteArray): Pair<Long, cached_remote_image>? = runCatching {
        val buffer = ByteBuffer.wrap(plain)
        val created_at = buffer.long
        val type_length = buffer.short.toInt() and 0xffff
        val type = ByteArray(type_length).also { buffer.get(it) }.toString(Charsets.UTF_8)
        if (!type.startsWith("image/")) return null
        val bytes = ByteArray(buffer.remaining()).also { buffer.get(it) }
        created_at to cached_remote_image(type, bytes)
    }.getOrNull()

    private fun write_atomic(target: File, bytes: ByteArray): Boolean = runCatching {
        target.parentFile?.mkdirs()
        val temp = File(target.parentFile, target.name + ".tmp")
        temp.writeBytes(bytes)
        if (!temp.renameTo(target)) {
            target.delete()
            if (!temp.renameTo(target)) {
                temp.delete()
                return false
            }
        }
        true
    }.getOrDefault(false)

    private fun sha256_hex(value: String): String =
        MessageDigest.getInstance("SHA-256").digest(value.toByteArray(Charsets.UTF_8))
            .joinToString("") { "%02x".format(it) }

    private companion object {
        const val KEY_FILE = "cache_key.bin"
        const val ENTRY_SUFFIX = ".img"
        const val KEY_MATERIAL_BYTES = 64
        const val MAX_TRANSIENT_UNSEAL_FAILURES = 3
        const val ENTRY_VERSION: Byte = 1
    }
}
