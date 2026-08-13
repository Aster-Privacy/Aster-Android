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

package org.astermail.android

import android.app.Application
import coil.ImageLoader
import coil.ImageLoaderFactory
import coil.decode.SvgDecoder
import coil.disk.DiskCache
import coil.memory.MemoryCache
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.android.EntryPointAccessors
import dagger.hilt.android.HiltAndroidApp
import dagger.hilt.components.SingletonComponent
import kotlinx.coroutines.launch
import okhttp3.Interceptor
import okhttp3.OkHttpClient
import okhttp3.Response
import okhttp3.ResponseBody.Companion.toResponseBody
import org.astermail.android.api.BuildConfig as ApiBuildConfig
import org.astermail.android.security.LockdownStore
import org.astermail.android.storage.TokenStore

@HiltAndroidApp
class AsterApplication : Application(), ImageLoaderFactory {

    override fun onCreate() {
        super.onCreate()
        runCatching {
            val ep = EntryPointAccessors.fromApplication(this, ImageLoaderEntryPoint::class.java)
            val resolver = ep.aster_profile_resolver()
            org.astermail.android.mail.AsterProfileResolverHolder.shared = resolver
            runCatching {
                val account_store = org.astermail.android.storage.AccountStore(applicationContext)
                for (acc in account_store.get_all()) {
                    resolver.prime(
                        email = acc.email,
                        display_name = acc.display_name,
                        profile_picture = acc.profile_picture,
                        profile_color = acc.profile_color,
                    )
                }
            }
        }
        register_app_lock_lifecycle()
        runCatching { register_folder_lock_hooks() }
        runCatching { org.astermail.android.notifications.MailPollingWorker.create_channel(this) }
        runCatching { org.astermail.android.notifications.LoginAlertNotifier.create_channel(this) }
        runCatching { org.astermail.android.notifications.MailPollingWorker.enqueue(this) }
        runCatching { org.astermail.android.notifications.UnifiedPushState.sync_registration(this) }
    }

    private fun register_app_lock_lifecycle() {
        val store = EntryPointAccessors.fromApplication(this, ImageLoaderEntryPoint::class.java).app_lock_store()
        androidx.lifecycle.ProcessLifecycleOwner.get().lifecycle.addObserver(
            androidx.lifecycle.LifecycleEventObserver { _, event ->
                when (event) {
                    androidx.lifecycle.Lifecycle.Event.ON_STOP -> store.lock()
                    androidx.lifecycle.Lifecycle.Event.ON_START -> store.check_on_foreground()
                    else -> {}
                }
            },
        )
    }

    private fun register_folder_lock_hooks() {
        val scope = kotlinx.coroutines.CoroutineScope(
            kotlinx.coroutines.SupervisorJob() + kotlinx.coroutines.Dispatchers.IO,
        )
        org.astermail.android.api.folder_unlock_resolver.register { request ->
            org.astermail.android.folders.folder_lock_store.resolve_unlock_header(request)
        }
        org.astermail.android.folders.folder_lock_store.register_remote_revoker { folder_id, unlock_token ->
            scope.launch {
                runCatching {
                    val ep = EntryPointAccessors.fromApplication(
                        this@AsterApplication,
                        ImageLoaderEntryPoint::class.java,
                    )
                    ep.labels_api().lock_folder(
                        folder_id,
                        org.astermail.android.api.labels.LockFolderRequest(
                            unlock_token = unlock_token,
                            all_sessions = false,
                        ),
                    )
                }
            }
        }
        org.astermail.android.folders.folder_lock_store.register_purge_hook { folder_tokens ->
            scope.launch {
                runCatching {
                    val ep = EntryPointAccessors.fromApplication(
                        this@AsterApplication,
                        ImageLoaderEntryPoint::class.java,
                    )
                    ep.search_index_manager().purge_folder_tokens(folder_tokens)
                }
            }
        }
    }

    @EntryPoint
    @InstallIn(SingletonComponent::class)
    interface ImageLoaderEntryPoint {
        fun token_store(): TokenStore
        fun aster_profile_resolver(): org.astermail.android.mail.AsterProfileResolver
        fun labels_api(): org.astermail.android.api.labels.LabelsApi
        fun search_index_manager(): org.astermail.android.mail.SearchIndexManager
        fun app_lock_store(): org.astermail.android.security.AppLockStore
    }

    override fun newImageLoader(): ImageLoader {
        val ep = EntryPointAccessors.fromApplication(this, ImageLoaderEntryPoint::class.java)
        val token_store = ep.token_store()
        org.astermail.android.mail.AsterProfileResolverHolder.shared = ep.aster_profile_resolver()

        val api_host = runCatching { java.net.URI(ApiBuildConfig.API_BASE_URL).host.orEmpty() }
            .getOrDefault("")

        val image_dispatcher = okhttp3.Dispatcher().apply {
            maxRequests = 32
            maxRequestsPerHost = 16
        }

        val ok_http = OkHttpClient.Builder()
            .dns(org.astermail.android.api.DualStackDns)
            .dispatcher(image_dispatcher)
            .connectTimeout(java.time.Duration.ofMillis(4_000))
            .readTimeout(java.time.Duration.ofSeconds(20))
            .callTimeout(java.time.Duration.ofSeconds(25))
            .addInterceptor(bearer_interceptor(token_store, api_host))
            .addInterceptor(lockdown_interceptor(api_host))
            .build()

        return ImageLoader.Builder(this)
            .okHttpClient(ok_http)
            .components {
                add(SvgDecoder.Factory())
            }
            .memoryCache {
                MemoryCache.Builder(this)
                    .maxSizePercent(0.25)
                    .build()
            }
            .diskCache {
                DiskCache.Builder()
                    .directory(cacheDir.resolve("image_cache"))
                    .maxSizeBytes(150L * 1024 * 1024)
                    .build()
            }
            .crossfade(false)
            .respectCacheHeaders(false)
            .build()
    }

    private fun lockdown_interceptor(api_host: String): Interceptor =
        Interceptor { chain ->
            val request = chain.request()
            if (!LockdownStore.is_enabled(applicationContext)) return@Interceptor chain.proceed(request)
            val host = request.url.host
            val is_aster = api_host.isNotEmpty() &&
                (host == api_host || host.endsWith(".$api_host"))
            if (!is_aster) {
                return@Interceptor okhttp3.Response.Builder()
                    .request(request)
                    .protocol(okhttp3.Protocol.HTTP_1_1)
                    .code(403)
                    .message("Blocked by Lockdown Mode")
                    .body(ByteArray(0).toResponseBody(null))
                    .build()
            }
            chain.proceed(request)
        }

    private fun bearer_interceptor(token_store: TokenStore, api_host: String): Interceptor =
        Interceptor { chain ->
            val request = chain.request()
            val host = request.url.host
            val should_auth = api_host.isNotEmpty() &&
                (host == api_host || host.endsWith(".$api_host"))
            if (!should_auth) return@Interceptor chain.proceed(request)
            val token = token_store.access_token ?: return@Interceptor chain.proceed(request)
            val authed = request.newBuilder()
                .header("Authorization", "Bearer $token")
                .build()
            chain.proceed(authed) as Response
        }
}
