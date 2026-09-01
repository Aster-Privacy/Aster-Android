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

private val secure_prefs_namespaces = listOf(
    "aster_tokens_v1",
    "aster_session_keys_v1",
    "aster_accounts_v1",
    "aster_app_lock",
    "aster_db_meta",
    "aster_session_snapshots_v1",
    "aster_trusted_devices_v1",
    "aster_preferences_cache",
    "aster_ratchet_state_v1",
    "aster_ratchet_identity_pins",
    "aster_ratchet_plaintext_v1",
    "aster_unifiedpush_secure",
    "aster_app_lock_biometric",
)

@HiltAndroidApp
class AsterApplication : Application(), ImageLoaderFactory {

    override fun onCreate() {
        super.onCreate()
        start_secure_prefs_warm()
        org.astermail.android.ui.mail.AsterTimePreferences.set_use_24h(
            android.text.format.DateFormat.is24HourFormat(this),
        )
        runCatching {
            val ep = EntryPointAccessors.fromApplication(this, ImageLoaderEntryPoint::class.java)
            org.astermail.android.mail.AsterProfileResolverHolder.shared = ep.aster_profile_resolver()
        }
        runCatching {
            org.astermail.android.mail.DemoPhishingContentHolder.shared = org.astermail.android.mail.DemoPhishingContent(
                subject = getString(R.string.demo_phish_subject),
                preview = getString(R.string.demo_phish_preview),
                body_greeting = getString(R.string.demo_phish_body_greeting),
                body_para1 = getString(R.string.demo_phish_body_para1),
                body_para2 = getString(R.string.demo_phish_body_para2),
                body_para3 = getString(R.string.demo_phish_body_para3),
                body_signoff = getString(R.string.demo_phish_body_signoff),
                body_signin_label = getString(R.string.demo_phish_body_signin_label),
            )
        }
        runCatching { org.astermail.android.network.low_network_monitor.start(this) }
        register_app_lock_lifecycle()
        runCatching { register_folder_lock_hooks() }
        runCatching { seed_protected_folder_tokens() }
        start_deferred_startup()
    }

    private fun start_secure_prefs_warm() {
        Thread {
            runCatching {
                org.astermail.android.storage.SecurePrefs.warm(this, secure_prefs_namespaces)
            }
        }.apply { name = "aster-prefs-warm" }.start()
    }

    private fun start_deferred_startup() {
        Thread {
            android.os.Process.setThreadPriority(android.os.Process.THREAD_PRIORITY_BACKGROUND)
            runCatching { prime_profile_resolver() }
            runCatching { org.astermail.android.notifications.MailPollingWorker.create_channel(this) }
            runCatching { org.astermail.android.notifications.LoginAlertNotifier.create_channel(this) }
            runCatching { org.astermail.android.notifications.MailPollingWorker.enqueue(this) }
            runCatching { org.astermail.android.notifications.UnifiedPushState.sync_registration(this) }
            runCatching {
                EntryPointAccessors.fromApplication(this, ImageLoaderEntryPoint::class.java)
                    .database()
                    .get()
            }
        }.apply { name = "aster-startup" }.start()
    }

    private fun prime_profile_resolver() {
        val ep = EntryPointAccessors.fromApplication(this, ImageLoaderEntryPoint::class.java)
        val resolver = ep.aster_profile_resolver()
        org.astermail.android.mail.AsterProfileResolverHolder.shared = resolver
        for (acc in ep.account_store().get_all()) {
            resolver.prime(
                email = acc.email,
                display_name = acc.display_name,
                profile_picture = acc.profile_picture,
                profile_color = acc.profile_color,
            )
        }
    }

    private fun register_app_lock_lifecycle() {
        val store = EntryPointAccessors.fromApplication(this, ImageLoaderEntryPoint::class.java).app_lock_store()
        androidx.lifecycle.ProcessLifecycleOwner.get().lifecycle.addObserver(
            androidx.lifecycle.LifecycleEventObserver { _, event ->
                when (event) {
                    androidx.lifecycle.Lifecycle.Event.ON_STOP -> {
                        store.lock()
                        org.astermail.android.ui.common.app_session.mark_backgrounded()
                    }
                    androidx.lifecycle.Lifecycle.Event.ON_START -> {
                        store.check_on_foreground()
                        org.astermail.android.ui.common.app_session.mark_foregrounded()
                    }
                    else -> {}
                }
            },
        )
    }

    private fun seed_protected_folder_tokens() {
        if (!org.astermail.android.notifications.MailPollingWorker.protected_folder_state_known(this)) return
        org.astermail.android.folders.folder_lock_store.seed_protected_tokens(
            org.astermail.android.notifications.MailPollingWorker.protected_folder_tokens(this),
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
        fun account_store(): org.astermail.android.storage.AccountStore
        fun database(): dagger.Lazy<org.astermail.android.storage.search.AsterDatabase>
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
