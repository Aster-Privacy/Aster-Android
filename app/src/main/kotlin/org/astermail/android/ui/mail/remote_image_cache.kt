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

import android.content.Context
import android.content.SharedPreferences
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.webkit.WebResourceResponse
import dagger.hilt.android.EntryPointAccessors
import java.io.ByteArrayInputStream
import java.io.File
import java.io.InputStream
import java.security.KeyStore
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import org.astermail.android.AsterApplication

internal data class remote_image_cache_settings(
    val keep_images: Boolean = true,
    val prefetch_on_arrival: Boolean = false,
)

internal object remote_image_cache {
    private const val keystore = "AndroidKeyStore"
    private const val key_alias = "aster_remote_image_cache_v1"
    private const val prefs_name = "remote_image_cache_settings"
    private const val key_keep_images = "keep_images"
    private const val key_prefetch_on_arrival = "prefetch_on_arrival"
    private const val dir_name = "remote_image_cache"

    private val lock = Any()

    @Volatile
    private var store_instance: remote_image_cache_store? = null

    private val _settings = MutableStateFlow<remote_image_cache_settings?>(null)
    val settings: StateFlow<remote_image_cache_settings?> = _settings.asStateFlow()

    private fun prefs(context: Context): SharedPreferences =
        context.applicationContext.getSharedPreferences(prefs_name, Context.MODE_PRIVATE)

    fun current_settings(context: Context): remote_image_cache_settings {
        _settings.value?.let { return it }
        val loaded = runCatching {
            val prefs = prefs(context)
            remote_image_cache_settings(
                keep_images = prefs.getBoolean(key_keep_images, true),
                prefetch_on_arrival = prefs.getBoolean(key_prefetch_on_arrival, false),
            )
        }.getOrDefault(remote_image_cache_settings())
        _settings.value = loaded
        return loaded
    }

    fun set_keep_images(context: Context, enabled: Boolean) {
        val next = current_settings(context).copy(keep_images = enabled)
        runCatching { prefs(context).edit().putBoolean(key_keep_images, enabled).apply() }
        _settings.value = next
        if (!enabled) clear_all(context)
    }

    fun set_prefetch_on_arrival(context: Context, enabled: Boolean) {
        val next = current_settings(context).copy(prefetch_on_arrival = enabled)
        runCatching { prefs(context).edit().putBoolean(key_prefetch_on_arrival, enabled).apply() }
        _settings.value = next
    }

    private fun secret_key(): SecretKey {
        val store = KeyStore.getInstance(keystore).apply { load(null) }
        (store.getKey(key_alias, null) as? SecretKey)?.let { return it }
        val generator = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, keystore)
        generator.init(
            KeyGenParameterSpec.Builder(key_alias, KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT)
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .setKeySize(256)
                .build(),
        )
        return generator.generateKey()
    }

    private fun root_dir(context: Context): File = File(context.applicationContext.noBackupFilesDir, dir_name)

    private fun store(context: Context): remote_image_cache_store {
        store_instance?.let { return it }
        return synchronized(lock) {
            store_instance ?: remote_image_cache_store(
                root = root_dir(context),
                key_sealer = gcm_key_sealer(::secret_key),
            ).also { store_instance = it }
        }
    }

    fun account_id(context: Context): String? = runCatching {
        val entry_point = EntryPointAccessors.fromApplication(
            context.applicationContext,
            AsterApplication.ImageLoaderEntryPoint::class.java,
        )
        entry_point.session_key_store().get_user_id()?.takeIf { it.isNotBlank() }
            ?: entry_point.account_store().get_current_id()?.takeIf { it.isNotBlank() }
    }.getOrNull()

    fun is_enabled(context: Context): Boolean = current_settings(context).keep_images

    fun lookup(context: Context, url: String): cached_remote_image? {
        if (!is_enabled(context)) return null
        val account = account_id(context) ?: return null
        return runCatching { store(context).get(account, url) }.getOrNull()
    }

    fun contains(context: Context, account: String, url: String): Boolean =
        is_enabled(context) && runCatching { store(context).contains(account, url) }.getOrDefault(false)

    fun generation(context: Context): Long? {
        if (!is_enabled(context)) return null
        return runCatching { store(context).generation() }.getOrNull()
    }

    fun remember(
        context: Context,
        account: String,
        url: String,
        status_code: Int,
        content_type: String?,
        bytes: ByteArray,
        expected_generation: Long? = null,
    ): Boolean {
        if (!is_enabled(context)) return false
        if (account_id(context) != account) return false
        return runCatching {
            store(context).put(account, url, status_code, content_type, bytes, expected_generation)
        }.getOrDefault(false)
    }

    fun cached_response(context: Context, url: String, extra_headers: Map<String, String> = emptyMap()): WebResourceResponse? {
        val hit = lookup(context, url) ?: return null
        return WebResourceResponse(
            hit.content_type,
            null,
            200,
            "OK",
            mapOf("Cache-Control" to "no-store") + extra_headers,
            ByteArrayInputStream(hit.bytes),
        )
    }

    fun caching_stream(
        context: Context,
        url: String,
        status_code: Int,
        content_type: String?,
        source: InputStream,
    ): InputStream {
        if (!is_enabled(context) || EmailHtmlSanitizer.is_tracker_url(remote_image_cache_key(url).orEmpty())) {
            return source
        }
        val type = content_type?.substringBefore(';')?.trim()?.lowercase().orEmpty()
        if (status_code != 200 || !type.startsWith("image/")) return source
        val generation = generation(context) ?: return source
        val account = account_id(context) ?: return source
        val app_context = context.applicationContext
        return remote_image_caching_stream(source, REMOTE_IMAGE_CACHE_MAX_ENTRY_BYTES) { bytes ->
            remember(app_context, account, url, status_code, content_type, bytes, expected_generation = generation)
        }
    }

    fun clear_account(context: Context, account: String?) {
        if (account.isNullOrBlank()) {
            clear_all(context)
            return
        }
        runCatching { store(context).clear(account) }
    }

    fun clear_all(context: Context) {
        runCatching { store(context).clear_all() }
        runCatching { root_dir(context).deleteRecursively() }
    }
}
