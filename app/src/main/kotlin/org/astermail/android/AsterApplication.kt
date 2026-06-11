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
import okhttp3.Interceptor
import okhttp3.OkHttpClient
import okhttp3.Response
import org.astermail.android.api.BuildConfig as ApiBuildConfig
import org.astermail.android.crypto.CryptoNative
import org.astermail.android.storage.TokenStore

@HiltAndroidApp
class AsterApplication : Application(), ImageLoaderFactory {

    override fun onCreate() {
        super.onCreate()
        runCatching {
            val pepper = BuildConfig.HASH_EMAIL_PEPPER
            if (pepper.isNotEmpty()) {
                CryptoNative.configure_hash_email_pepper(pepper)
            }
        }
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
        runCatching { org.astermail.android.notifications.MailPollingWorker.create_channel(this) }
        runCatching { org.astermail.android.notifications.MailPollingWorker.enqueue(this) }
        runCatching { org.astermail.android.notifications.UnifiedPushState.try_register(this) }
    }

    @EntryPoint
    @InstallIn(SingletonComponent::class)
    interface ImageLoaderEntryPoint {
        fun token_store(): TokenStore
        fun aster_profile_resolver(): org.astermail.android.mail.AsterProfileResolver
    }

    override fun newImageLoader(): ImageLoader {
        val ep = EntryPointAccessors.fromApplication(this, ImageLoaderEntryPoint::class.java)
        val token_store = ep.token_store()
        org.astermail.android.mail.AsterProfileResolverHolder.shared = ep.aster_profile_resolver()

        val api_host = runCatching { java.net.URI(ApiBuildConfig.API_BASE_URL).host.orEmpty() }
            .getOrDefault("")

        val ok_http = OkHttpClient.Builder()
            .addInterceptor(bearer_interceptor(token_store, api_host))
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
                    .maxSizeBytes(50L * 1024 * 1024)
                    .build()
            }
            .crossfade(false)
            .respectCacheHeaders(false)
            .build()
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
