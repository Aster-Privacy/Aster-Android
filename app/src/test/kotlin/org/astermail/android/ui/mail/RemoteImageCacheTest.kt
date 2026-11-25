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

import java.io.ByteArrayInputStream
import java.io.File
import java.nio.file.Files
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import org.junit.After
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class RemoteImageCacheTest {
    private lateinit var root: File
    private var now = 1_800_000_000_000L
    private val account = "account-one"
    private val image_url = "https://cdn.example.com/banner.png"
    private val proxied_url = REMOTE_IMAGE_PROXY_BASE + java.net.URLEncoder.encode(image_url, "UTF-8")

    private fun new_key(): SecretKey = KeyGenerator.getInstance("AES").apply { init(256) }.generateKey()

    private val wrap_key = new_key()

    private fun store(
        limits: remote_image_cache_limits = remote_image_cache_limits(),
        key: SecretKey = wrap_key,
    ) = remote_image_cache_store(root, gcm_key_sealer { key }, { now }, limits)

    private fun png(width: Int, height: Int, total: Int = 64): ByteArray {
        val bytes = ByteArray(maxOf(total, 24))
        val header = byteArrayOf(0x89.toByte(), 0x50, 0x4e, 0x47, 0x0d, 0x0a, 0x1a, 0x0a)
        header.copyInto(bytes)
        fun be32(at: Int, value: Int) {
            bytes[at] = (value ushr 24).toByte()
            bytes[at + 1] = (value ushr 16).toByte()
            bytes[at + 2] = (value ushr 8).toByte()
            bytes[at + 3] = value.toByte()
        }
        be32(16, width)
        be32(20, height)
        for (i in 24 until bytes.size) bytes[i] = (i % 251).toByte()
        return bytes
    }

    private fun entry_files(): List<File> =
        root.walkTopDown().filter { it.isFile && it.name.endsWith(".img") }.toList()

    @Before
    fun set_up() {
        root = Files.createTempDirectory("remote_image_cache_test").toFile()
    }

    @After
    fun tear_down() {
        root.deleteRecursively()
    }

    @Test
    fun hit_returns_stored_bytes_without_a_fetch() {
        val cache = store()
        val bytes = png(120, 80)
        assertTrue(cache.put(account, proxied_url, 200, "image/png", bytes))
        var fetches = 0
        val hit = cache.get(account, proxied_url) ?: run { fetches++; null }
        assertNotNull(hit)
        assertEquals(0, fetches)
        assertEquals("image/png", hit!!.content_type)
        assertArrayEquals(bytes, hit.bytes)
    }

    @Test
    fun proxied_and_original_urls_share_one_entry() {
        val cache = store()
        assertTrue(cache.put(account, proxied_url, 200, "image/png", png(40, 40)))
        assertNotNull(cache.get(account, image_url))
        assertEquals(cache.entry_name(account, proxied_url), cache.entry_name(account, image_url))
    }

    @Test
    fun miss_stores_after_a_complete_read() {
        val cache = store()
        assertNull(cache.get(account, image_url))
        val bytes = png(64, 64, total = 40_000)
        val stream = remote_image_caching_stream(ByteArrayInputStream(bytes), REMOTE_IMAGE_CACHE_MAX_ENTRY_BYTES) {
            cache.put(account, image_url, 200, "image/png", it)
        }
        stream.readBytes()
        assertArrayEquals(bytes, cache.get(account, image_url)!!.bytes)
    }

    @Test
    fun partial_read_is_not_stored() {
        val cache = store()
        val bytes = png(64, 64, total = 40_000)
        val stream = remote_image_caching_stream(ByteArrayInputStream(bytes), REMOTE_IMAGE_CACHE_MAX_ENTRY_BYTES) {
            cache.put(account, image_url, 200, "image/png", it)
        }
        stream.read(ByteArray(1000))
        stream.close()
        assertNull(cache.get(account, image_url))
    }

    @Test
    fun entries_expire_after_the_ttl() {
        val cache = store()
        assertTrue(cache.put(account, image_url, 200, "image/png", png(40, 40)))
        now += REMOTE_IMAGE_CACHE_TTL_MS - 1
        assertNotNull(cache.get(account, image_url))
        now += 2
        assertNull(cache.get(account, image_url))
        assertTrue(entry_files().isEmpty())
    }

    @Test
    fun least_recently_used_entry_is_evicted_at_the_cap() {
        val one = "https://cdn.example.com/one.png"
        val two = "https://cdn.example.com/two.png"
        val three = "https://cdn.example.com/three.png"
        val cache = store(remote_image_cache_limits(max_total_bytes = 2_600))
        assertTrue(cache.put(account, one, 200, "image/png", png(40, 40, total = 1_000)))
        now += 1_000
        assertTrue(cache.put(account, two, 200, "image/png", png(40, 40, total = 1_000)))
        now += 1_000
        assertNotNull(cache.get(account, one))
        now += 1_000
        assertTrue(cache.put(account, three, 200, "image/png", png(40, 40, total = 1_000)))
        assertNotNull(cache.get(account, one))
        assertNull(cache.get(account, two))
        assertNotNull(cache.get(account, three))
        assertTrue(cache.total_bytes() <= 2_600)
    }

    @Test
    fun oversize_images_are_not_stored() {
        val cache = store(remote_image_cache_limits(max_entry_bytes = 2_000))
        assertFalse(cache.put(account, image_url, 200, "image/png", png(40, 40, total = 2_001)))
        assertNull(cache.get(account, image_url))
        val stream = remote_image_caching_stream(ByteArrayInputStream(png(40, 40, total = 2_001)), 2_000) {
            cache.put(account, image_url, 200, "image/png", it)
        }
        stream.readBytes()
        assertTrue(entry_files().isEmpty())
    }

    @Test
    fun errors_and_non_images_are_not_stored() {
        val cache = store()
        assertFalse(cache.put(account, image_url, 404, "image/png", png(40, 40)))
        assertFalse(cache.put(account, image_url, 302, "image/png", png(40, 40)))
        assertFalse(cache.put(account, image_url, 200, "text/html", png(40, 40)))
        assertFalse(cache.put(account, image_url, 200, "image/png", ByteArray(0)))
        assertTrue(entry_files().isEmpty())
    }

    @Test
    fun tracking_pixels_are_never_stored() {
        val cache = store()
        assertFalse(cache.put(account, "https://cdn.example.com/spacer.png", 200, "image/png", png(1, 1)))
        assertFalse(cache.put(account, "https://news.example.com/open/abc123.png", 200, "image/png", png(600, 200)))
        assertFalse(cache.put(account, "https://mail.example.com/track/xyz", 200, "image/gif", png(300, 100)))
        assertTrue(entry_files().isEmpty())
    }

    @Test
    fun entry_names_are_keyed_per_account_key() {
        val first = store()
        val second_account = first.entry_name("account-two", image_url)
        val first_account = first.entry_name(account, image_url)
        assertNotNull(first_account)
        assertNotEquals(first_account, second_account)
        assertFalse(first_account!!.contains("banner"))
        assertEquals(64, first_account.length)
        val other_root = Files.createTempDirectory("remote_image_cache_other").toFile()
        try {
            val other = remote_image_cache_store(other_root, gcm_key_sealer { wrap_key }, { now })
            assertNotEquals(first_account, other.entry_name(account, image_url))
        } finally {
            other_root.deleteRecursively()
        }
    }

    @Test
    fun accounts_do_not_see_each_other() {
        val cache = store()
        assertTrue(cache.put(account, image_url, 200, "image/png", png(40, 40)))
        assertNull(cache.get("account-two", image_url))
        cache.clear(account)
        assertNull(cache.get(account, image_url))
    }

    @Test
    fun tampered_ciphertext_is_a_miss() {
        val cache = store()
        assertTrue(cache.put(account, image_url, 200, "image/png", png(40, 40)))
        val file = entry_files().single()
        val bytes = file.readBytes()
        bytes[bytes.size - 5] = (bytes[bytes.size - 5].toInt() xor 0x01).toByte()
        file.writeBytes(bytes)
        assertNull(store().get(account, image_url))
        assertFalse(file.exists())
    }

    @Test
    fun stored_bytes_are_encrypted() {
        val cache = store()
        val bytes = png(40, 40, total = 400)
        assertTrue(cache.put(account, image_url, 200, "image/png", bytes))
        val on_disk = entry_files().single().readBytes()
        val needle = bytes.copyOfRange(100, 140)
        val found = (0..on_disk.size - needle.size).any { i ->
            needle.indices.all { on_disk[i + it] == needle[it] }
        }
        assertFalse(found)
        assertFalse(String(on_disk, Charsets.ISO_8859_1).contains("image/png"))
    }

    @Test
    fun wrong_wrapping_key_cannot_read_entries() {
        assertTrue(store().put(account, image_url, 200, "image/png", png(40, 40)))
        assertNull(store(key = new_key()).get(account, image_url))
    }

    @Test
    fun clear_all_removes_every_entry() {
        val cache = store()
        assertTrue(cache.put(account, image_url, 200, "image/png", png(40, 40)))
        assertTrue(cache.put("account-two", image_url, 200, "image/png", png(40, 40)))
        cache.clear_all()
        assertTrue(entry_files().isEmpty())
        assertNull(cache.get(account, image_url))
    }

    @Test
    fun a_clear_during_a_fetch_blocks_the_late_store() {
        val cache = store()
        val generation = cache.generation()
        val stream = remote_image_caching_stream(ByteArrayInputStream(png(40, 40)), REMOTE_IMAGE_CACHE_MAX_ENTRY_BYTES) {
            cache.put(account, image_url, 200, "image/png", it, expected_generation = generation)
        }
        cache.clear(account)
        stream.readBytes()
        assertNull(cache.get(account, image_url))
        assertTrue(root.listFiles().orEmpty().isEmpty())
        assertTrue(cache.put(account, image_url, 200, "image/png", png(40, 40), expected_generation = cache.generation()))
    }

    @Test
    fun clear_all_also_blocks_late_stores_for_other_accounts() {
        val cache = store()
        val generation = cache.generation()
        cache.clear_all()
        assertFalse(cache.put("account-two", image_url, 200, "image/png", png(40, 40), expected_generation = generation))
        assertTrue(root.listFiles().orEmpty().isEmpty())
    }

    @Test
    fun transient_key_failures_do_not_wipe_the_cache() {
        assertTrue(store().put(account, image_url, 200, "image/png", png(40, 40)))
        var failing = true
        val flaky = object : remote_image_key_sealer {
            private val real = gcm_key_sealer { wrap_key }
            override fun seal(plain: ByteArray, aad: ByteArray) = real.seal(plain, aad)
            override fun open(sealed: ByteArray, aad: ByteArray): ByteArray? {
                if (failing) throw IllegalStateException("keystore busy")
                return real.open(sealed, aad)
            }
        }
        val cache = remote_image_cache_store(root, flaky, { now })
        assertFalse(cache.put(account, "https://cdn.example.com/other.png", 200, "image/png", png(40, 40)))
        assertNull(cache.get(account, image_url))
        assertEquals(1, entry_files().size)
        failing = false
        assertNotNull(cache.get(account, image_url))
    }

    @Test
    fun repeated_key_failures_reset_the_scope() {
        assertTrue(store().put(account, image_url, 200, "image/png", png(40, 40)))
        val broken = object : remote_image_key_sealer {
            private val real = gcm_key_sealer { wrap_key }
            override fun seal(plain: ByteArray, aad: ByteArray) = real.seal(plain, aad)
            override fun open(sealed: ByteArray, aad: ByteArray): ByteArray? = throw IllegalStateException("gone")
        }
        val cache = remote_image_cache_store(root, broken, { now })
        assertFalse(cache.put(account, "https://cdn.example.com/a.png", 200, "image/png", png(40, 40)))
        assertFalse(cache.put(account, "https://cdn.example.com/a.png", 200, "image/png", png(40, 40)))
        assertEquals(1, entry_files().size)
        assertTrue(cache.put(account, "https://cdn.example.com/a.png", 200, "image/png", png(40, 40)))
        assertEquals(1, entry_files().size)
        assertNull(cache.get(account, image_url))
    }

    @Test
    fun cache_key_drops_fragments_and_rejects_other_schemes() {
        assertEquals(image_url, remote_image_cache_key("$image_url#part"))
        assertNull(remote_image_cache_key("data:image/png;base64,AAAA"))
        assertNull(remote_image_cache_key("cid:part1"))
    }
}
