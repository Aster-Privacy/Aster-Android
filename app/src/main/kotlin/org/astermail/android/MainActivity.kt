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

import org.astermail.android.ui.common.find_host_activity
import androidx.lifecycle.repeatOnLifecycle
import compose.icons.TablerIcons
import compose.icons.tablericons.*

import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import org.astermail.android.ui.common.apply_secure_window
import org.astermail.android.ui.common.note_app_lock_launch
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.saveable.rememberSaveableStateHolder
import androidx.lifecycle.Lifecycle
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.testTagsAsResourceId
import org.astermail.android.security.AppLockViewModel
import org.astermail.android.guard.guard_launch_target
import org.astermail.android.guard.guard_launch_target_for
import org.astermail.android.ui.auth.mark_signed_up_now
import org.astermail.android.security.LockdownStore
import org.astermail.android.ui.common.edge_swipe_to_open
import org.astermail.android.ui.common.nav_anim_duration_ms
import org.astermail.android.ui.security.AppLockScreen
import androidx.compose.foundation.layout.fillMaxWidth
import org.astermail.android.ui.common.nav_backward_enter
import org.astermail.android.ui.common.nav_backward_exit
import org.astermail.android.ui.common.nav_forward_enter
import org.astermail.android.ui.common.nav_forward_exit
import org.astermail.android.ui.common.nav_sheet_enter
import org.astermail.android.ui.common.nav_sheet_exit
import org.astermail.android.ui.common.nav_predictive_pop_enter
import org.astermail.android.ui.common.nav_predictive_pop_exit
import org.astermail.android.ui.common.nav_search_enter
import org.astermail.android.ui.common.nav_search_exit
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.foundation.layout.systemBars
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.material3.DrawerValue
import androidx.compose.material3.ModalDrawerSheet
import androidx.compose.material3.ModalNavigationDrawer
import androidx.compose.material3.rememberDrawerState
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.ui.draw.clip
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.dialog
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import dagger.hilt.android.AndroidEntryPoint
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.mapNotNull
import kotlinx.coroutines.launch
import org.astermail.android.auth.AuthGateViewModel
import org.astermail.android.design.AsterMaterial
import org.astermail.android.design.AsterSpacing
import org.astermail.android.design.AsterTheme
import org.astermail.android.design.AsterThemeMode
import org.astermail.android.design.ColorThemeId
import org.astermail.android.design.parse_hex_color_safe
import androidx.compose.ui.res.stringResource
import org.astermail.android.R
import org.astermail.android.notifications.internal_launch_token
import org.astermail.android.storage.ThemeMode
import org.astermail.android.ui.auth.ForgotPasswordScreen
import org.astermail.android.ui.auth.OnboardingScreen
import org.astermail.android.ui.auth.RegisterScreen
import org.astermail.android.ui.auth.SignInScreen
import org.astermail.android.ui.auth.WelcomeScreen
import org.astermail.android.ui.compose.ComposeScreen
import org.astermail.android.ui.contacts.ContactDetailScreen
import org.astermail.android.ui.contacts.ContactEditScreen
import org.astermail.android.ui.contacts.ContactsScreen
import androidx.compose.material.icons.Icons
import org.astermail.android.ui.drawer.DrawerContent
import org.astermail.android.ui.drawer.drawer_alias_item
import org.astermail.android.ui.drawer.drawer_folder_item
import org.astermail.android.ui.drawer.drawer_label_item
import org.astermail.android.ui.mail.FilterType
import org.astermail.android.ui.mail.FilteredInboxScreen
import org.astermail.android.ui.mail.InboxScreen
import org.astermail.android.ui.mail.MailDetailScreen
import org.astermail.android.ui.mail.MailingListsScreen
import org.astermail.android.ui.search.SearchScreen
import org.astermail.android.ui.settings.SettingsScreen
import org.astermail.android.ui.settings.detail.AboutScreen
import org.astermail.android.ui.settings.detail.AccessibilityScreen
import org.astermail.android.ui.settings.detail.ApiKeysScreen
import org.astermail.android.ui.settings.detail.DefaultSenderScreen
import org.astermail.android.ui.settings.detail.SmtpTokensScreen
import org.astermail.android.ui.settings.detail.DeveloperScreen
import org.astermail.android.ui.settings.detail.FamilyScreen
import org.astermail.android.ui.settings.detail.LinkDeviceScreen
import org.astermail.android.ui.settings.detail.KidsReservedScreen
import org.astermail.android.ui.settings.detail.FoldersScreen
import org.astermail.android.ui.settings.detail.LabelsScreen
import org.astermail.android.ui.settings.detail.LanguageScreen
import org.astermail.android.ui.settings.detail.PrivacyScreen
import org.astermail.android.ui.settings.detail.ReferralScreen
import org.astermail.android.ui.settings.detail.TrustedDevicesScreen
import org.astermail.android.ui.settings.detail.AliasesScreen
import org.astermail.android.ui.settings.detail.AppearanceScreen
import org.astermail.android.ui.settings.detail.DomainPurchaseScreen
import org.astermail.android.ui.settings.detail.DomainsScreen
import org.astermail.android.ui.settings.detail.DomainPurchaseProgressScreen
import org.astermail.android.ui.settings.detail.AutoForwardScreen
import org.astermail.android.ui.settings.detail.BehaviorScreen
import org.astermail.android.ui.settings.detail.SwipeActionsScreen
import org.astermail.android.ui.settings.detail.CustomizeToolbarScreen
import org.astermail.android.ui.settings.detail.SubscriptionsScreen
import org.astermail.android.ui.settings.detail.crypto_invoice_screen
import org.astermail.android.ui.settings.detail.FeaturesScreen
import org.astermail.android.ui.settings.detail.AllowListScreen
import org.astermail.android.ui.settings.detail.BlockedSendersScreen
import org.astermail.android.ui.settings.detail.ChangePasswordScreen
import org.astermail.android.ui.settings.detail.DeleteAccountScreen
import org.astermail.android.ui.settings.detail.DiagnosticsScreen
import org.astermail.android.ui.settings.detail.EncryptionScreen
import org.astermail.android.ui.settings.detail.FeedbackScreen
import org.astermail.android.ui.settings.detail.ImportGroupScreen
import org.astermail.android.ui.settings.detail.import_group_tab_for_route
import org.astermail.android.ui.settings.detail.NotificationsScreen
import org.astermail.android.ui.settings.detail.ProfileScreen
import org.astermail.android.ui.settings.detail.RecoveryCodesScreen
import org.astermail.android.ui.settings.detail.RecoveryEmailScreen
import org.astermail.android.ui.settings.detail.SecurityScreen
import org.astermail.android.ui.settings.detail.SenderFiltersScreen
import org.astermail.android.ui.settings.detail.SessionsScreen
import org.astermail.android.ui.settings.detail.SignatureScreen
import org.astermail.android.ui.settings.detail.StorageScreen
import org.astermail.android.ui.settings.detail.TemplatesScreen
import org.astermail.android.ui.settings.detail.TwoFactorScreen
import org.astermail.android.ui.settings.detail.VacationReplyScreen
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Density
import org.astermail.android.ui.theme.AccessibilityState
import org.astermail.android.ui.theme.ThemeViewModel
import org.astermail.android.ui.theme.local_accessibility
import org.astermail.android.ui.theme.local_text_scale

@AndroidEntryPoint
class MainActivity :
    androidx.fragment.app.FragmentActivity(),
    org.astermail.android.ui.common.SecureFlagHost {

    companion object {
        const val EXTRA_OPEN_EMAIL_ID = "open_email_id"
        const val EXTRA_OPEN_SESSIONS = "open_sessions"
        const val EXTRA_OPEN_GUARD_REQUEST = "open_guard_request"
        const val EXTRA_OPEN_GUARD_CHANGES = "open_guard_changes"
        const val EXTRA_OPEN_GUARD_SETTINGS = "open_guard_settings"
        const val EXTRA_OPEN_GUARD_PANIC = "open_guard_panic"
        const val EXTRA_GUARD_ACCOUNT_ID = "guard_account_id"
        const val EXTRA_OPEN_CONTACT_SYNC = "open_contact_sync"
        const val EXTRA_ACCOUNT_LINK_REQUEST = "account_link_request"
        private const val CONTENT_INSET_TIMEOUT_MS = 4000L
        private const val STATE_LAUNCH_PENDING = "launch_intent_pending"
    }

    private val insets_applied = java.util.concurrent.atomic.AtomicBoolean(false)

    private val insets_ready = androidx.compose.runtime.mutableStateOf(false)

    private val secure_storage_ready = androidx.compose.runtime.mutableStateOf(
        org.astermail.android.storage.SecurePrefs.warm_complete.value,
    )

    val pending_launch = pending_launch_state()

    private var boot_background_argb: Int = 0

    @javax.inject.Inject
    lateinit var app_lock_store: org.astermail.android.security.AppLockStore

    @javax.inject.Inject
    lateinit var auth_repository: dagger.Lazy<org.astermail.android.auth.AuthRepository>

    private val lockdown_listener =
        android.content.SharedPreferences.OnSharedPreferenceChangeListener { _, _ ->
            runOnUiThread { enforce_secure_flag() }
        }

    override fun attachBaseContext(new_base: android.content.Context) {
        super.attachBaseContext(org.astermail.android.settings.app_language.apply(new_base))
    }

    override fun onCreate(saved_instance_state: Bundle?) {
        super.onCreate(saved_instance_state)
        enforce_secure_flag()
        LockdownStore.register_listener(applicationContext, lockdown_listener)
        lifecycleScope.launch {
            app_lock_store.config_version.collect { enforce_secure_flag() }
        }
        if (!secure_storage_ready.value) {
            lifecycleScope.launch {
                org.astermail.android.storage.SecurePrefs.warm_complete.first { it }
                runCatching { app_lock_store.check_on_foreground() }
                enforce_secure_flag()
                secure_storage_ready.value = true
            }
        }
        if (saved_instance_state == null) note_launch_folder(intent)
        if (saved_instance_state == null || saved_instance_state.getBoolean(STATE_LAUNCH_PENDING)) {
            consume_open_email_extra(intent)
            consume_share_intent(intent)
        }
        if (saved_instance_state == null) org.astermail.android.ui.common.first_frame_gate.arm()
        if (app_lock_store.is_locked.value) org.astermail.android.ui.common.first_frame_gate.mark_ready()
        apply_boot_background()
        enableEdgeToEdge()
        org.astermail.android.ui.common.install_aster_motion_scale(this)
        setContent {
            window_inset_probe {
                insets_applied.set(true)
                insets_ready.value = true
            }
            androidx.compose.runtime.LaunchedEffect(Unit) {
                kotlinx.coroutines.delay(CONTENT_INSET_TIMEOUT_MS)
                insets_ready.value = true
            }
            androidx.compose.runtime.CompositionLocalProvider(local_pending_launch provides pending_launch) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .semantics { testTagsAsResourceId = true }
                        .graphicsLayer { alpha = if (insets_ready.value) 1f else 0f },
                ) {
                    if (secure_storage_ready.value) {
                        AsterRoot()
                    } else {
                        org.astermail.android.ui.common.aster_boot_placeholder(boot_background_argb)
                    }
                }
                app_lock_cover_layer()
            }
        }
        hold_first_frame_until_ready()
    }

    private fun hold_first_frame_until_ready() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            runCatching {
                splashScreen.setOnExitAnimationListener { splash ->
                    val motion = org.astermail.android.design.AsterMotionScale
                    if (motion.reduced || motion.system_disabled) {
                        splash.remove()
                    } else {
                        splash.animate()
                            .alpha(0f)
                            .setDuration(org.astermail.android.design.AsterMotionSpec.fade_exit_ms.toLong())
                            .withEndAction { splash.remove() }
                            .start()
                    }
                }
            }
        }
        val content = findViewById<android.view.View>(android.R.id.content) ?: return
        val started = android.os.SystemClock.uptimeMillis()
        content.viewTreeObserver.addOnPreDrawListener(
            object : android.view.ViewTreeObserver.OnPreDrawListener {
                override fun onPreDraw(): Boolean {
                    val may_draw = org.astermail.android.ui.common.first_frame_may_draw(
                        insets_applied = insets_applied.get(),
                        content_ready = org.astermail.android.ui.common.first_frame_gate.is_ready,
                        elapsed_ms = android.os.SystemClock.uptimeMillis() - started,
                    )
                    if (may_draw) {
                        content.viewTreeObserver.removeOnPreDrawListener(this)
                        insets_ready.value = true
                        return true
                    }
                    content.postInvalidateOnAnimation()
                    return false
                }
            },
        )
    }

    @androidx.compose.runtime.Composable
    private fun app_lock_cover_layer() {
        val locked = app_lock_store.is_locked.collectAsState()
        val reveal_started = app_lock_store.reveal_started.collectAsState()
        val storage_ready = secure_storage_ready.value
        val signed_in = if (storage_ready) {
            auth_repository.get().is_signed_in.collectAsState().value
        } else {
            true
        }
        val visible = org.astermail.android.ui.common.app_lock_cover_visible(
            is_locked = locked.value,
            reveal_started = reveal_started.value,
            storage_ready = storage_ready,
            signed_in = signed_in,
        )
        if (visible) org.astermail.android.ui.common.app_lock_cover(boot_background_argb)
    }

    private fun apply_boot_background() {
        val argb = org.astermail.android.ui.common.theme_boot_background_argb(this)
        boot_background_argb = argb
        window.setBackgroundDrawable(android.graphics.drawable.ColorDrawable(argb))
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            runCatching {
                splashScreen.setSplashScreenTheme(
                    org.astermail.android.ui.common.theme_boot_splash_style(this),
                )
            }
        }
    }

    override fun onSaveInstanceState(out_state: Bundle) {
        super.onSaveInstanceState(out_state)
        out_state.putBoolean(STATE_LAUNCH_PENDING, pending_launch.has_pending_target())
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        consume_open_email_extra(intent)
        consume_share_intent(intent, from_new_intent = true)
    }

    private fun note_launch_folder(intent: Intent?) {
        val extra_keys = runCatching { intent?.extras?.keySet()?.toSet() }.getOrNull().orEmpty()
        val plain = org.astermail.android.mail.is_plain_launch(
            action = intent?.action,
            has_data = intent?.data != null,
            extra_keys = extra_keys,
            target_extra_keys = if (internal_launch_token.is_trusted(this, intent)) {
                setOf(
                    EXTRA_OPEN_EMAIL_ID,
                    EXTRA_OPEN_SESSIONS,
                    EXTRA_OPEN_GUARD_REQUEST,
                    EXTRA_OPEN_GUARD_CHANGES,
                    EXTRA_OPEN_GUARD_SETTINGS,
                    EXTRA_OPEN_GUARD_PANIC,
                )
            } else {
                emptySet()
            },
        )
        if (plain) {
            pending_launch.launch_folder.value = org.astermail.android.mail.launch_folder_store.load(this)
        }
    }

    private fun app_reads_shared_storage(): Boolean = listOf(
        android.Manifest.permission.READ_EXTERNAL_STORAGE,
        android.Manifest.permission.WRITE_EXTERNAL_STORAGE,
        "android.permission.MANAGE_EXTERNAL_STORAGE",
        "android.permission.READ_MEDIA_IMAGES",
        "android.permission.READ_MEDIA_VIDEO",
        "android.permission.READ_MEDIA_AUDIO",
        "android.permission.READ_MEDIA_VISUAL_USER_SELECTED",
    ).any { checkSelfPermission(it) == android.content.pm.PackageManager.PERMISSION_GRANTED }

    private fun share_caller_is_system(intent: Intent): Boolean {
        intent.removeExtra(Intent.EXTRA_REFERRER)
        intent.removeExtra(Intent.EXTRA_REFERRER_NAME)
        val source = runCatching { referrer }.getOrNull()
        val caller_package = org.astermail.android.share.share_referrer_package(source?.scheme, source?.host)
            ?: return false
        if (caller_package == "android") return true
        return runCatching {
            packageManager.getApplicationInfo(caller_package, 0).flags and
                android.content.pm.ApplicationInfo.FLAG_SYSTEM != 0
        }.getOrDefault(false)
    }

    private fun share_caller_access(uri: Uri, from_new_intent: Boolean): org.astermail.android.share.ShareCallerAccess {
        if (Build.VERSION.SDK_INT < 35) return org.astermail.android.share.ShareCallerAccess.UNKNOWN
        return try {
            val caller = if (from_new_intent) currentCaller else initialCaller
            val result = caller.checkContentUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION)
            if (result == android.content.pm.PackageManager.PERMISSION_GRANTED) {
                org.astermail.android.share.ShareCallerAccess.GRANTED
            } else {
                org.astermail.android.share.ShareCallerAccess.DENIED
            }
        } catch (_: SecurityException) {
            org.astermail.android.share.ShareCallerAccess.DENIED
        } catch (_: Throwable) {
            org.astermail.android.share.ShareCallerAccess.UNKNOWN
        }
    }

    private fun share_stream_gate(intent: Intent?, from_new_intent: Boolean): (Uri) -> Boolean {
        if (intent == null) return { false }
        val caller = org.astermail.android.share.ShareCallerContext(
            own_package = packageName,
            has_read_grant = intent.flags and Intent.FLAG_GRANT_READ_URI_PERMISSION != 0,
            caller_is_system = share_caller_is_system(intent),
            app_reads_shared_storage = app_reads_shared_storage(),
        )
        return { uri ->
            org.astermail.android.share.is_share_stream_allowed(
                uri.scheme,
                uri.authority,
                caller,
                share_caller_access(uri, from_new_intent),
                org.astermail.android.share.app_owns_media_row(contentResolver, uri, packageName),
            )
        }
    }

    private fun consume_share_intent(intent: Intent?, from_new_intent: Boolean = false) {
        val payload = org.astermail.android.share.parse_share_intent(
            intent,
            share_stream_gate(intent, from_new_intent),
        ) ?: return
        intent?.action = null
        pending_launch.pending_share_token.value = android.os.SystemClock.elapsedRealtimeNanos().toString()
        pending_launch.pending_share.value = payload
    }

    private fun consume_billing_return(intent: Intent?) {
        val outcome = org.astermail.android.billing.parse_billing_return(intent?.data) ?: return
        intent?.data = null
        org.astermail.android.billing.billing_return_store.outcome.value = outcome
        pending_launch.pending_open_billing.value = true
    }

    private fun consume_guard_veto(intent: Intent?) {
        val link = org.astermail.android.ui.guard.parse_guard_veto(intent?.dataString) ?: return
        intent?.data = null
        pending_launch.pending_guard_veto.value = link
    }

    private fun consume_open_email_extra(intent: Intent?) {
        consume_billing_return(intent)
        consume_guard_veto(intent)
        val trusted = internal_launch_token.is_trusted(this, intent)
        intent?.removeExtra(internal_launch_token.EXTRA_TOKEN)
        if (intent?.getBooleanExtra(EXTRA_OPEN_SESSIONS, false) == true) {
            intent.removeExtra(EXTRA_OPEN_SESSIONS)
            if (trusted) pending_launch.pending_open_sessions.value = true
        }
        consume_guard_extras(intent, trusted)
        val contact_sync_nonce = runCatching {
            intent?.getStringExtra(org.astermail.android.notifications.contact_sync_launch.EXTRA_NONCE)
        }.getOrNull()
        intent?.removeExtra(org.astermail.android.notifications.contact_sync_launch.EXTRA_NONCE)
        if (intent?.getBooleanExtra(EXTRA_OPEN_CONTACT_SYNC, false) == true) {
            intent.removeExtra(EXTRA_OPEN_CONTACT_SYNC)
            val nonce_valid = org.astermail.android.notifications.contact_sync_launch.consume(contact_sync_nonce)
            if (trusted || nonce_valid) {
                pending_launch.pending_open_contact_sync.value = true
            }
        }
        intent?.getStringExtra(EXTRA_ACCOUNT_LINK_REQUEST)?.let { request_id ->
            intent.removeExtra(EXTRA_ACCOUNT_LINK_REQUEST)
            if (org.astermail.android.accountlink.AccountLinkApprovals.shared.prompt_for(request_id) != null) {
                pending_launch.pending_account_link.value = request_id
            }
        }
        val email_id = intent?.getStringExtra(EXTRA_OPEN_EMAIL_ID)?.takeIf { it.isNotBlank() } ?: return
        intent.removeExtra(EXTRA_OPEN_EMAIL_ID)
        if (!trusted) return
        pending_launch.pending_open_email_id.value = email_id
        pending_launch.pending_reveal_email_id.value = email_id
        pending_launch.pending_reveal_folder_tokens.value = null
    }

    private fun consume_guard_extras(intent: Intent?, trusted: Boolean) {
        if (intent == null) return
        val request_id = intent.getStringExtra(EXTRA_OPEN_GUARD_REQUEST)
        val changes = intent.getBooleanExtra(EXTRA_OPEN_GUARD_CHANGES, false)
        val settings = intent.getBooleanExtra(EXTRA_OPEN_GUARD_SETTINGS, false)
        val panic = intent.getBooleanExtra(EXTRA_OPEN_GUARD_PANIC, false)
        val account_id = intent.getStringExtra(EXTRA_GUARD_ACCOUNT_ID)
        intent.removeExtra(EXTRA_OPEN_GUARD_REQUEST)
        intent.removeExtra(EXTRA_OPEN_GUARD_CHANGES)
        intent.removeExtra(EXTRA_OPEN_GUARD_SETTINGS)
        intent.removeExtra(EXTRA_OPEN_GUARD_PANIC)
        intent.removeExtra(EXTRA_GUARD_ACCOUNT_ID)
        if (!trusted) return
        val target = guard_launch_target_for(request_id, changes, settings, account_id, panic) ?: return
        pending_launch.pending_guard_target.value = target
    }

    override fun onResume() {
        super.onResume()
        app_lock_store.end_external_flow()
        enforce_secure_flag()
        org.astermail.android.ui.mail.AsterTimePreferences.set_use_24h(
            android.text.format.DateFormat.is24HourFormat(this),
        )
        runCatching {
            org.astermail.android.notifications.MailPollingWorker.enqueue_reconcile(applicationContext)
        }
    }

    override fun onPause() {
        enforce_secure_flag()
        apply_boot_background()
        super.onPause()
    }

    override fun onDestroy() {
        if (isFinishing) {
            val cache_dir = applicationContext.cacheDir
            Thread { runCatching { org.astermail.android.share.clear_opened_attachments(cache_dir) } }.start()
        }
        LockdownStore.unregister_listener(applicationContext, lockdown_listener)
        super.onDestroy()
    }

    override fun enforce_secure_flag() {
        val app_lock_configured = !app_lock_store.is_store_open() ||
            runCatching { app_lock_store.is_configured() }.getOrDefault(true)
        apply_secure_window(
            org.astermail.android.ui.common.SecureScreenGuard.is_active() ||
                LockdownStore.is_enabled(applicationContext) ||
                app_lock_configured,
        )
    }

    @Deprecated("Deprecated in Java")
    override fun startActivityForResult(intent: Intent, request_code: Int, options: Bundle?) {
        note_app_lock_launch(app_lock_store, intent)
        super.startActivityForResult(intent, request_code, options)
    }

    @Deprecated("Deprecated in Java")
    override fun startIntentSenderForResult(
        intent: android.content.IntentSender,
        request_code: Int,
        fill_in_intent: Intent?,
        flags_mask: Int,
        flags_values: Int,
        extra_flags: Int,
        options: Bundle?,
    ) {
        app_lock_store.begin_external_flow()
        super.startIntentSenderForResult(intent, request_code, fill_in_intent, flags_mask, flags_values, extra_flags, options)
    }
}

@Composable
private fun request_notification_permission(should_request: Boolean) {
    val context = androidx.compose.ui.platform.LocalContext.current
    val launcher = androidx.activity.compose.rememberLauncherForActivityResult(
        androidx.activity.result.contract.ActivityResultContracts.RequestPermission(),
    ) {}
    androidx.compose.runtime.LaunchedEffect(should_request) {
        if (!should_request) return@LaunchedEffect
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.TIRAMISU) {
            val prefs = context.getSharedPreferences("aster_perms", android.content.Context.MODE_PRIVATE)
            val already_asked = prefs.getBoolean("notif_perm_asked", false)
            val granted = androidx.core.content.ContextCompat.checkSelfPermission(
                context, android.Manifest.permission.POST_NOTIFICATIONS,
            ) == android.content.pm.PackageManager.PERMISSION_GRANTED
            if (!granted && !already_asked) {
                prefs.edit().putBoolean("notif_perm_asked", true).apply()
                launcher.launch(android.Manifest.permission.POST_NOTIFICATIONS)
            }
        }
    }
}

class pending_launch_state {
    val pending_open_email_id = mutableStateOf<String?>(null)
    val pending_open_sessions = mutableStateOf(false)
    val pending_open_contact_sync = mutableStateOf(false)
    val pending_open_billing = mutableStateOf(false)
    val pending_guard_target = mutableStateOf<guard_launch_target?>(null)
    val pending_guard_veto = mutableStateOf<org.astermail.android.ui.guard.guard_veto_link?>(null)
    val pending_account_link = mutableStateOf<String?>(null)
    val pending_reveal_email_id = mutableStateOf<String?>(null)
    val pending_reveal_folder_tokens = mutableStateOf<List<String>?>(null)
    val pending_share = mutableStateOf<org.astermail.android.share.SharePayload?>(null)
    val pending_share_token = mutableStateOf("")
    val launch_folder = mutableStateOf<String?>(null)

    fun has_pending_target(): Boolean =
        pending_open_email_id.value != null ||
            pending_reveal_email_id.value != null ||
            pending_share.value != null ||
            pending_open_sessions.value ||
            pending_open_contact_sync.value ||
            pending_open_billing.value ||
            pending_guard_target.value != null ||
            pending_account_link.value != null

    fun take_launch_folder(): String? {
        val value = launch_folder.value
        launch_folder.value = null
        return value
    }
}

val local_pending_launch = androidx.compose.runtime.staticCompositionLocalOf<pending_launch_state> {
    error("pending_launch_state not provided")
}

@Composable
private fun AsterRoot() {
    val pending = local_pending_launch.current
    org.astermail.android.ui.common.aster_theme_root {
        Box(modifier = Modifier.fillMaxSize()) {
            AsterNavHost()
        }
    }
}

private object routes {
    const val onboarding = "onboarding"
    const val welcome = "welcome"
    const val sign_in = "sign_in"
    const val sign_in_with_email = "sign_in?email={email}"
    const val register = "register"

    fun sign_in_for(email: String): String {
        val encoded = android.net.Uri.encode(email)
        return "sign_in?email=$encoded"
    }
    const val forgot_password = "forgot_password"
    const val guard_approve = "guard_approve/{id}"

    fun guard_approve_for(request_id: String): String = "guard_approve/" + android.net.Uri.encode(request_id)
    const val inbox = "inbox"

    const val mail_detail = "mail_detail/{email_id}?swipe={swipe}&chain={chain}"
    const val crypto_invoice = "crypto_invoice/{invoice_id}"

    fun crypto_invoice_for(invoice_id: String): String {
        val encoded = android.net.Uri.encode(invoice_id)
        return "crypto_invoice/$encoded"
    }
    const val folder_filter = "folder/{folder_id}/{folder_name}"
    const val label_filter = "label/{label_id}/{label_name}"
    const val alias_filter = "alias/{alias_id}/{alias_name}"

    fun folder_filter_for(folder_id: String, folder_name: String): String {
        val id = android.net.Uri.encode(folder_id)
        val name = android.net.Uri.encode(folder_name)
        return "folder/$id/$name"
    }
    fun label_filter_for(label_id: String, label_name: String): String {
        val id = android.net.Uri.encode(label_id)
        val name = android.net.Uri.encode(label_name)
        return "label/$id/$name"
    }
    fun alias_filter_for(alias_id: String, alias_name: String): String {
        val id = android.net.Uri.encode(alias_id)
        val name = android.net.Uri.encode(alias_name)
        return "alias/$id/$name"
    }
    const val compose = "compose?reply_to={reply_to}&mode={mode}&draft_id={draft_id}&to={to}&thread_ghost={thread_ghost}&share={share}"
    const val search = "search"
    const val search_with_query = "search?q={q}&from={from}"
    fun search_for(query: String): String {
        return "search?q=" + android.net.Uri.encode(query)
    }
    fun search_for_folder(
        folder: String,
        filter_kind: String? = null,
        filter_name: String = "",
        placeholder_name: String? = null,
    ): String {
        val filter_scope = org.astermail.android.mail.search_filter_scope_query(filter_kind, filter_name, placeholder_name)
        if (filter_scope != null) return search_for(filter_scope)
        if (org.astermail.android.mail.search_narrow_query(folder) == null) return search
        val scope = org.astermail.android.mail.search_scope_query(folder)
        val from = "from=" + android.net.Uri.encode(folder)
        return if (scope == null) "search?$from" else search_for(scope) + "&$from"
    }

    fun compose_new(to: String = ""): String {
        val encoded_to = if (to.isNotBlank()) android.net.Uri.encode(to) else ""
        return "compose?reply_to=&mode=&draft_id=&to=$encoded_to&thread_ghost="
    }
    fun compose_reply(msg_id: String, mode: String, thread_ghost: String? = null): String {
        val encoded_msg = android.net.Uri.encode(msg_id)
        val encoded_mode = android.net.Uri.encode(mode)
        val encoded_ghost = if (!thread_ghost.isNullOrBlank()) android.net.Uri.encode(thread_ghost) else ""
        return "compose?reply_to=$encoded_msg&mode=$encoded_mode&draft_id=&to=&thread_ghost=$encoded_ghost"
    }
    fun compose_from(address: String): String {
        val encoded = android.net.Uri.encode(address)
        return "compose?reply_to=&mode=&draft_id=&to=&thread_ghost=$encoded"
    }
    fun compose_share(token: String): String {
        return "compose?reply_to=&mode=&draft_id=&to=&thread_ghost=&share=$token"
    }
    fun compose_draft(draft_id: String): String {
        val encoded = android.net.Uri.encode(draft_id)
        return "compose?reply_to=&mode=draft&draft_id=$encoded&to=&thread_ghost="
    }
    const val pending_send_preview = "pending_send_preview"
    const val settings = "settings"
    const val settings_search = "settings_search"
    const val contacts = "contacts"
    const val mailing_lists = "mailing_lists"
    const val contact_detail = "contact_detail/{contact_id}"
    const val contact_group = "contact_group/{group_id}"
    const val contact_edit_new = "contact_edit"
    const val contact_edit = "contact_edit/{contact_id}"

    const val domain_order = "domain_order/{order_id}"
    const val domain_bimi = "settings_domain_bimi/{domain_id}"

    fun mail_detail_for(email_id: String, swipe: Int = 0, chain: Boolean = false): String {
        val query = listOfNotNull(
            if (swipe != 0) "swipe=$swipe" else null,
            if (chain) "chain=true" else null,
        )
        val base = "mail_detail/" + android.net.Uri.encode(email_id)
        return if (query.isEmpty()) base else base + "?" + query.joinToString("&")
    }
    fun settings_detail(id: String) = "settings_$id"

    const val alias_detail = "settings_alias_detail/{alias_id}"
    fun alias_detail_for(alias_id: String): String {
        val encoded = android.net.Uri.encode(alias_id)
        return "settings_alias_detail/$encoded"
    }

    val settings_low_network =
        "settings_accessibility?focus=" +
            org.astermail.android.ui.settings.detail.SETTINGS_FOCUS_LOW_NETWORK
    fun domain_order_for(order_id: String) = "domain_order/$order_id"
    fun domain_bimi_for(domain_id: String) =
        "settings_domain_bimi/" + android.net.Uri.encode(domain_id)
    fun contact_detail_for(contact_id: String) = "contact_detail/$contact_id"
    fun contact_group_for(group_id: String) = "contact_group/" + android.net.Uri.encode(group_id)
    fun contact_edit_for(contact_id: String) = "contact_edit/$contact_id"
}


@Composable
private fun AsterNavHost() {
    val pending = local_pending_launch.current
    val auth_gate: AuthGateViewModel = hiltViewModel()
    val theme_vm: ThemeViewModel = hiltViewModel()
    val lock_vm: AppLockViewModel = hiltViewModel()
    val is_ready by auth_gate.is_ready.collectAsStateWithLifecycle()
    val is_signed_in_state by auth_gate.is_signed_in.collectAsStateWithLifecycle()
    val is_locked by lock_vm.store.is_locked.collectAsStateWithLifecycle()

    val nav_scope = rememberCoroutineScope()

    val splash_was_shown = remember { mutableStateOf(!is_ready) }
    if (!is_ready) {
        androidx.compose.runtime.SideEffect { org.astermail.android.ui.common.first_frame_gate.mark_ready() }
        aster_launch_splash(Modifier)
        return
    }

    val guard_context = androidx.compose.ui.platform.LocalContext.current
    androidx.compose.runtime.LaunchedEffect(is_signed_in_state) {
        runCatching { lock_vm.store.forget_unverifiable_lock(signed_in = is_signed_in_state) }
    }

    val start_kind = remember(is_ready) {
        org.astermail.android.ui.common.launch_start_for(
            is_signed_in = is_signed_in_state,
            pending_guard_sign_in = !is_signed_in_state &&
                org.astermail.android.ui.guard.has_pending_guard_sign_in(guard_context),
            onboarding_seen = theme_vm.onboarding_seen.value,
        )
    }
    val start = when (start_kind) {
        org.astermail.android.ui.common.launch_start.inbox -> routes.inbox
        org.astermail.android.ui.common.launch_start.sign_in -> routes.sign_in
        org.astermail.android.ui.common.launch_start.onboarding -> routes.onboarding
        org.astermail.android.ui.common.launch_start.welcome -> routes.welcome
    }
    val ready_before_inbox = org.astermail.android.ui.common.first_frame_ready_before_inbox(
        start = start_kind,
        is_locked = is_locked && is_signed_in_state,
    )
    androidx.compose.runtime.SideEffect {
        if (ready_before_inbox) org.astermail.android.ui.common.first_frame_gate.mark_ready()
    }
    val nav_controller = rememberNavController()
    val notification_prompt_allowed by remember(nav_controller) {
        nav_controller.currentBackStackEntryFlow.map { it.destination.route != routes.register }
    }.collectAsState(initial = false)
    request_notification_permission(
        should_request = is_signed_in_state && !is_locked && notification_prompt_allowed,
    )
    val context = LocalContext.current
    val a11y = local_accessibility.current
    val nav_duration = if (org.astermail.android.design.aster_reduce_motion()) 0 else nav_anim_duration_ms
    val nav_back_duration = if (nav_duration == 0) 0 else org.astermail.android.ui.common.nav_anim_backward_ms

    val session_expired_state by auth_gate.session_expired.collectAsStateWithLifecycle()
    androidx.compose.runtime.LaunchedEffect(session_expired_state, is_signed_in_state) {
        if (!session_expired_state || is_signed_in_state) return@LaunchedEffect
        auth_gate.consume_session_expired()
        org.astermail.android.ui.common.app_toast.show(
            org.astermail.android.ui.common.TopToastState(
                message = context.getString(R.string.session_expired_sign_in),
                duration_ms = org.astermail.android.ui.common.toast_action_duration_ms,
            ),
        )
        nav_controller.navigate(routes.welcome) {
            popUpTo(0) { inclusive = true }
            launchSingleTop = true
        }
    }

    val vault_open_failed_state by auth_gate.vault_open_failed.collectAsStateWithLifecycle()
    androidx.compose.runtime.LaunchedEffect(vault_open_failed_state, is_signed_in_state) {
        if (!vault_open_failed_state || !is_signed_in_state) return@LaunchedEffect
        auth_gate.consume_vault_open_failed()
        org.astermail.android.ui.common.app_toast.show(
            org.astermail.android.ui.common.TopToastState(
                message = context.getString(R.string.vault_open_failed_notice),
                duration_ms = org.astermail.android.ui.common.toast_action_duration_ms,
            ),
        )
    }

    val forced_switch_settings_vm = org.astermail.android.settings.shared_settings_view_model()
    androidx.compose.runtime.LaunchedEffect(auth_gate) {
        auth_gate.forced_account_switch.collect {
            forced_switch_settings_vm.reset_for_account_switch()
            forced_switch_settings_vm.load_preferences(force = true)
            forced_switch_settings_vm.load_signature()
            nav_controller.navigate(routes.inbox) {
                popUpTo(0) { inclusive = true }
            }
        }
    }

    val lifecycle_owner = androidx.lifecycle.compose.LocalLifecycleOwner.current
    androidx.compose.runtime.DisposableEffect(lifecycle_owner) {
        val observer = androidx.lifecycle.LifecycleEventObserver { _, event ->
            if (event == androidx.lifecycle.Lifecycle.Event.ON_RESUME) {
                auth_gate.recheck_session()
            }
        }
        lifecycle_owner.lifecycle.addObserver(observer)
        onDispose { lifecycle_owner.lifecycle.removeObserver(observer) }
    }

    val pending_open_email = pending.pending_open_email_id.value
    androidx.compose.runtime.LaunchedEffect(pending_open_email, is_signed_in_state, is_locked) {
        if (pending_open_email.isNullOrBlank() || !is_signed_in_state || is_locked) return@LaunchedEffect
        pending.pending_open_email_id.value = null
        nav_controller.navigate(routes.mail_detail_for(pending_open_email)) {
            launchSingleTop = true
        }
    }

    val pending_share = pending.pending_share.value
    val pending_share_token = pending.pending_share_token.value
    androidx.compose.runtime.LaunchedEffect(pending_share_token, is_signed_in_state, is_locked) {
        if (pending_share == null || !is_signed_in_state || is_locked) return@LaunchedEffect
        nav_controller.navigate(routes.compose_share(pending_share_token)) {
            popUpTo(routes.compose) { inclusive = true }
        }
    }

    val pending_sessions = pending.pending_open_sessions.value
    androidx.compose.runtime.LaunchedEffect(pending_sessions, is_signed_in_state, is_locked) {
        if (!pending_sessions || !is_signed_in_state || is_locked) return@LaunchedEffect
        pending.pending_open_sessions.value = false
        nav_controller.navigate(routes.settings_detail("sessions")) {
            launchSingleTop = true
        }
    }

    val pending_contact_sync = pending.pending_open_contact_sync.value
    androidx.compose.runtime.LaunchedEffect(pending_contact_sync, is_signed_in_state, is_locked) {
        if (!pending_contact_sync || !is_signed_in_state || is_locked) return@LaunchedEffect
        pending.pending_open_contact_sync.value = false
        org.astermail.android.contacts.sync.ContactSyncAccounts.request_setup()
        nav_controller.navigate(routes.settings_detail("behavior")) {
            launchSingleTop = true
        }
    }

    val pending_billing = pending.pending_open_billing.value
    androidx.compose.runtime.LaunchedEffect(pending_billing, is_signed_in_state, is_locked) {
        if (!pending_billing || !is_signed_in_state || is_locked) return@LaunchedEffect
        pending.pending_open_billing.value = false
        val current = nav_controller.currentBackStackEntry?.destination?.route
        if (current?.contains("billing") == true) return@LaunchedEffect
        nav_controller.navigate(routes.settings_detail("billing")) {
            launchSingleTop = true
        }
    }

    val guard_rescue_context = androidx.compose.ui.platform.LocalContext.current
    androidx.compose.runtime.LaunchedEffect(is_signed_in_state, is_locked) {
        if (!is_signed_in_state || is_locked) return@LaunchedEffect
        if (!org.astermail.android.ui.guard.take_guard_setup_after_rescue(guard_rescue_context)) return@LaunchedEffect
        nav_controller.navigate(routes.settings_detail("guard") + "?rescued=true") {
            launchSingleTop = true
        }
    }

    val pending_guard = pending.pending_guard_target.value
    val guard_accounts_vm: org.astermail.android.accounts.AccountsViewModel = hiltViewModel()
    val guard_switch_settings_vm = org.astermail.android.settings.shared_settings_view_model()
    androidx.compose.runtime.LaunchedEffect(pending_guard, is_signed_in_state, is_locked) {
        if (pending_guard == null || !is_signed_in_state || is_locked) return@LaunchedEffect
        pending.pending_guard_target.value = null
        val destination = when (pending_guard) {
            is guard_launch_target.request -> routes.guard_approve_for(pending_guard.id)
            is guard_launch_target.changes -> routes.settings_detail("guard_changes")
            is guard_launch_target.settings -> routes.settings_detail("guard")
            is guard_launch_target.panic -> routes.settings_detail("guard_panic")
        }
        val route = org.astermail.android.guard.guard_launch_route_for(
            pending_guard.account_id,
            guard_accounts_vm.current_account_id(),
            guard_accounts_vm::has_stored_session,
        )
        when (route) {
            org.astermail.android.guard.guard_launch_route.ignore -> Unit
            org.astermail.android.guard.guard_launch_route.current -> nav_controller.navigate(destination) {
                launchSingleTop = true
            }
            org.astermail.android.guard.guard_launch_route.switch -> {
                val target_account = pending_guard.account_id ?: return@LaunchedEffect
                guard_accounts_vm.switch_account(target_account) { restored ->
                    if (!restored) return@switch_account
                    guard_switch_settings_vm.reset_for_account_switch()
                    guard_switch_settings_vm.load_preferences(force = true)
                    guard_switch_settings_vm.load_signature()
                    nav_controller.navigate(routes.inbox) {
                        popUpTo(0) { inclusive = true }
                    }
                    nav_controller.navigate(destination) {
                        launchSingleTop = true
                    }
                }
            }
        }
    }

    org.astermail.android.ui.upgrade.ClientUpdatePrompt()
    org.astermail.android.ui.upgrade.MailKeyUpgradePrompt(active = is_signed_in_state && is_ready && !is_locked)

    if (is_signed_in_state) {
        org.astermail.android.ui.upgrade.UpgradeHost(
            on_navigate_to_billing = {
                nav_controller.navigate(routes.settings_detail("billing"))
            },
        )
        org.astermail.android.billing.PlayBillingHost()
        androidx.compose.runtime.LaunchedEffect(Unit) {
            org.astermail.android.api.AuthEventBus.unauthorized.collect {
                auth_gate.auth_repository.handle_unauthorized_signal()
            }
        }
        androidx.compose.runtime.LaunchedEffect(Unit) {
            auth_gate.auth_repository.trigger_system_folder_bootstrap()
        }
        val ratchet_lifecycle_owner = androidx.lifecycle.compose.LocalLifecycleOwner.current
        androidx.compose.runtime.LaunchedEffect(ratchet_lifecycle_owner) {
            ratchet_lifecycle_owner.lifecycle.repeatOnLifecycle(androidx.lifecycle.Lifecycle.State.STARTED) {
                auth_gate.auth_repository.trigger_ratchet_bootstrap()
            }
        }
        val preferences_sync_vm: org.astermail.android.settings.SettingsViewModel = org.astermail.android.settings.shared_settings_view_model()
        androidx.compose.runtime.LaunchedEffect(is_signed_in_state) {
            preferences_sync_vm.load_preferences()
        }
        org.astermail.android.ui.mail.live_sync_host(
            active = !is_locked,
            on_folders_changed = { preferences_sync_vm.reload_labels_from_sync() },
            on_tags_changed = { preferences_sync_vm.reload_tags_from_sync() },
        )
        val undo_toast_allowed by remember(nav_controller) {
            nav_controller.currentBackStackEntryFlow.map { it.destination.route != routes.pending_send_preview }
        }.collectAsState(initial = true)
        val undo_context = androidx.compose.ui.platform.LocalContext.current
        org.astermail.android.ui.mail.undo_send_events(
            on_restored_draft = { draft_id ->
                undo_context.startActivity(
                    ComposeActivity.intent_for(undo_context, mode = "draft", draft_id = draft_id),
                )
            },
        )
        if (!is_locked && undo_toast_allowed) {
            org.astermail.android.ui.mail.undo_send_toast()
        }
    }

    val nav_pop_enter: androidx.compose.animation.AnimatedContentTransitionScope<androidx.navigation.NavBackStackEntry>.() -> EnterTransition = {
        val initial = initialState.destination.route
        if (initial?.startsWith("compose") == true || is_search_route(initial)) {
            androidx.compose.animation.EnterTransition.None
        } else {
            nav_backward_enter(nav_back_duration)
        }
    }
    val nav_pop_exit: androidx.compose.animation.AnimatedContentTransitionScope<androidx.navigation.NavBackStackEntry>.() -> ExitTransition = {
        if (targetState.destination.route?.startsWith("compose") == true) {
            androidx.compose.animation.ExitTransition.None
        } else if (is_search_route(initialState.destination.route)) {
            nav_search_exit(nav_duration)
        } else {
            nav_backward_exit(nav_back_duration)
        }
    }

    androidx.compose.runtime.CompositionLocalProvider(
        org.astermail.android.ui.settings.local_settings_search_opener provides {
            nav_controller.navigate(routes.settings_search)
        },
    ) {
    NavHost(
        navController = nav_controller,
        startDestination = start,
        modifier = if (is_locked && is_signed_in_state) {
            Modifier.clearAndSetSemantics {}
        } else {
            Modifier
        },
        enterTransition = {
            if (initialState.destination.route?.startsWith("compose") == true || is_detail_swipe(targetState)) {
                androidx.compose.animation.EnterTransition.None
            } else if (is_search_route(targetState.destination.route)) {
                nav_search_enter(nav_duration)
            } else {
                nav_forward_enter(nav_duration)
            }
        },
        exitTransition = {
            val target = targetState.destination.route
            if (target?.startsWith("compose") == true || is_detail_swipe(targetState) || is_search_route(target)) {
                androidx.compose.animation.ExitTransition.None
            } else {
                nav_forward_exit(nav_duration)
            }
        },
        popEnterTransition = nav_pop_enter,
        popExitTransition = nav_pop_exit,
        predictivePopEnterTransition = {
            val initial = initialState.destination.route
            if (initial?.startsWith("compose") == true || is_search_route(initial)) {
                androidx.compose.animation.EnterTransition.None
            } else {
                nav_predictive_pop_enter(nav_duration)
            }
        },
        predictivePopExitTransition = {
            if (initialState.destination.route?.startsWith("compose") == true) {
                nav_sheet_exit(nav_duration)
            } else if (targetState.destination.route?.startsWith("compose") == true) {
                androidx.compose.animation.ExitTransition.None
            } else {
                nav_predictive_pop_exit(nav_duration)
            }
        },
    ) {
        composable(routes.onboarding) {
            OnboardingScreen(
                on_sign_in = {
                    theme_vm.mark_onboarding_seen()
                    nav_controller.navigate(routes.welcome) {
                        popUpTo(routes.onboarding) {
                            inclusive = true
                            saveState = false
                        }
                    }
                    nav_controller.navigate(routes.sign_in)
                },
                on_create_account = {
                    theme_vm.mark_onboarding_seen()
                    nav_controller.navigate(routes.welcome) {
                        popUpTo(routes.onboarding) {
                            inclusive = true
                            saveState = false
                        }
                    }
                    nav_controller.navigate(routes.register)
                },
                on_skip = {
                    theme_vm.mark_onboarding_seen()
                    nav_controller.navigate(routes.welcome) {
                        popUpTo(routes.onboarding) { inclusive = true }
                    }
                },
            )
        }
        composable(routes.welcome) {
            WelcomeScreen(
                on_sign_in = { nav_controller.navigate(routes.sign_in) },
                on_create_account = { nav_controller.navigate(routes.register) },
            )
        }
        composable(
            route = routes.sign_in_with_email,
            arguments = listOf(
                navArgument("email") {
                    type = NavType.StringType
                    nullable = true
                    defaultValue = null
                },
            ),
        ) { entry ->
            val raw_email = entry.arguments?.getString("email")
            val prefill = raw_email?.takeIf { it.isNotBlank() }
                .orEmpty()
            val sign_in_settings_vm = org.astermail.android.settings.shared_settings_view_model()
            SignInScreen(
                on_back = {
                    if (!nav_controller.popBackStack()) {
                        nav_controller.navigate(routes.welcome) { popUpTo(0) { inclusive = true } }
                    }
                },
                on_forgot_password = { nav_controller.navigate(routes.forgot_password) },
                on_signed_in = {
                    sign_in_settings_vm.reset_for_account_switch()
                    sign_in_settings_vm.load_preferences(force = true)
                    sign_in_settings_vm.load_signature()
                    nav_controller.navigate(routes.inbox) {
                        popUpTo(0) { inclusive = true }
                    }
                },
                on_register = {
                    nav_controller.navigate(routes.register) {
                        popUpTo(routes.sign_in_with_email) { inclusive = true }
                        launchSingleTop = true
                    }
                },
                prefill_email = prefill,
            )
        }
        composable(routes.register) {
            val register_settings_vm = org.astermail.android.settings.shared_settings_view_model()
            RegisterScreen(
                on_back = {
                    if (!nav_controller.popBackStack()) {
                        nav_controller.navigate(routes.welcome) { popUpTo(0) { inclusive = true } }
                    }
                },
                on_registered = { destination ->
                    register_settings_vm.reset_for_account_switch()
                    register_settings_vm.load_preferences(force = true)
                    register_settings_vm.load_signature()
                    mark_signed_up_now(context)
                    theme_vm.mark_first_run(setup_pending = false)
                    nav_controller.navigate(routes.inbox) {
                        popUpTo(0) { inclusive = true }
                    }
                    if (destination != null) {
                        nav_controller.navigate(routes.settings_detail(destination))
                    }
                },
                on_sign_in = {
                    nav_controller.navigate(routes.sign_in) {
                        popUpTo(routes.register) { inclusive = true }
                        launchSingleTop = true
                    }
                },
                on_upgrade = {
                    pending.pending_open_billing.value = true
                    nav_controller.navigate(routes.sign_in) {
                        popUpTo(routes.register) { inclusive = true }
                        launchSingleTop = true
                    }
                },
                on_terms_click = {
                    try {
                        context.startActivity(
                            Intent(Intent.ACTION_VIEW, Uri.parse("https://astermail.org/terms"))
                                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
                        )
                    } catch (_: Throwable) {
                        org.astermail.android.ui.common.app_toast.show(context.getString(R.string.could_not_open_link))
                    }
                },
                on_privacy_click = {
                    try {
                        context.startActivity(
                            Intent(Intent.ACTION_VIEW, Uri.parse("https://astermail.org/privacy"))
                                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
                        )
                    } catch (_: Throwable) {
                        org.astermail.android.ui.common.app_toast.show(context.getString(R.string.could_not_open_link))
                    }
                },
            )
        }
        composable(routes.forgot_password) {
            ForgotPasswordScreen(
                on_back = { pop_once(nav_controller) },
                on_submit = { _ -> pop_once(nav_controller) },
                on_sign_in = { email ->
                    if (email.isBlank()) {
                        pop_once(nav_controller)
                    } else {
                        val came_from_sign_in =
                            nav_controller.previousBackStackEntry?.destination?.route == routes.sign_in_with_email
                        nav_controller.navigate(routes.sign_in_for(email)) {
                            popUpTo(if (came_from_sign_in) routes.sign_in_with_email else routes.forgot_password) {
                                inclusive = true
                            }
                            launchSingleTop = true
                        }
                    }
                },
            )
        }
        composable(routes.inbox) {
            InboxWithDrawer(nav_controller)
        }
        composable(
            route = routes.mail_detail,
            arguments = listOf(
                navArgument("email_id") { type = NavType.StringType },
                navArgument("swipe") {
                    type = NavType.IntType
                    defaultValue = 0
                },
                navArgument("chain") {
                    type = NavType.BoolType
                    defaultValue = false
                },
            ),
        ) { entry ->
            val email_id = entry.arguments?.getString("email_id").orEmpty()
            val swipe_direction = entry.arguments?.getInt("swipe") ?: 0
            val chained = entry.arguments?.getBoolean("chain") ?: false
            val inbox_entry = remember(entry) {
                try { nav_controller.getBackStackEntry(routes.inbox) } catch (_: Throwable) { null }
            }
            val returns_to_inbox = remember(entry) {
                nav_controller.previousBackStackEntry?.destination?.route == routes.inbox
            }
            androidx.compose.runtime.LaunchedEffect(entry, returns_to_inbox) {
                if (returns_to_inbox) org.astermail.android.design.components.arm_menu_back_return_morph()
            }
            val shared_mail_vm: org.astermail.android.mail.MailViewModel =
                if (inbox_entry != null) hiltViewModel(inbox_entry) else hiltViewModel()
            val shared_settings_vm: org.astermail.android.settings.SettingsViewModel =
                org.astermail.android.settings.shared_settings_view_model()
            org.astermail.android.ui.common.on_return_to_foreground {
                shared_mail_vm.refresh_after_background()
            }
            androidx.compose.runtime.LaunchedEffect(shared_mail_vm, shared_settings_vm) {
                shared_mail_vm.learned_category_edits.collect { shared_settings_vm.apply_learned_category_edit(it) }
            }
            val visible_order by shared_mail_vm.visible_order.collectAsStateWithLifecycle()
            val stable_order = androidx.compose.runtime.remember {
                androidx.compose.runtime.mutableStateOf(visible_order)
            }
            androidx.compose.runtime.LaunchedEffect(visible_order, email_id) {
                if (email_id in visible_order) stable_order.value = visible_order
            }
            val neighbor_id: (Int) -> String? = neighbor@{ delta ->
                val order = stable_order.value
                val idx = order.indexOf(email_id)
                if (idx < 0) return@neighbor null
                order.getOrNull(idx + delta)
            }
            val open_neighbor: (String, Int) -> Unit = { next_id, swipe ->
                nav_controller.navigate(routes.mail_detail_for(next_id, swipe, chain = true)) {
                    popUpTo(routes.mail_detail) { inclusive = true }
                    launchSingleTop = true
                }
            }
            androidx.compose.runtime.LaunchedEffect(email_id, stable_order.value) {
                val order = stable_order.value
                if (inbox_entry != null && order.isNotEmpty() && order.lastOrNull() == email_id) {
                    shared_mail_vm.load_more()
                }
            }
            val open_marked = androidx.compose.runtime.saveable.rememberSaveable(email_id) {
                androidx.compose.runtime.mutableStateOf(false)
            }
            androidx.compose.runtime.LaunchedEffect(email_id) {
                if (open_marked.value) return@LaunchedEffect
                val open_mark_as_read = shared_settings_vm.state
                    .mapNotNull { it.preferences?.mark_as_read }
                    .first()
                if (open_marked.value) return@LaunchedEffect
                open_marked.value = true
                shared_mail_vm.on_user_opened_mail(email_id, open_mark_as_read)
            }
            androidx.compose.runtime.DisposableEffect(email_id) {
                onDispose {
                    if (shared_mail_vm.cancel_opened_mail(email_id)) open_marked.value = false
                }
            }
            androidx.compose.runtime.LaunchedEffect(email_id) {
                shared_mail_vm.thread_state
                    .map { it.item to it.is_loading }
                    .distinctUntilChanged { a, b -> a.first?.id == b.first?.id && a.second == b.second }
                    .collect { (item, is_loading) ->
                        if (is_loading) return@collect
                        val reveal_id = pending.pending_reveal_email_id.value ?: return@collect
                        if (item == null) return@collect
                        if (item.id != reveal_id || reveal_id != email_id) return@collect
                        pending.pending_reveal_email_id.value = null
                        val tokens = (
                            item.labels +
                                listOfNotNull(item.raw_item.folder_token) +
                                (item.raw_item.folders?.mapNotNull { it.folder_token } ?: emptyList())
                            ).distinct()
                        if (tokens.isNotEmpty()) {
                            pending.pending_reveal_folder_tokens.value = tokens
                        }
                    }
            }
            val advance_after_action: () -> Unit = {
                val next = when (shared_settings_vm.state.value.preferences?.auto_advance ?: "Go to next message") {
                    "Go to next message" -> neighbor_id(1)
                    "Go to previous message" -> neighbor_id(-1)
                    else -> null
                }
                if (next != null) open_neighbor(next, 0) else pop_once(nav_controller)
            }
            MailDetailScreen(
                email_id = email_id,
                on_back = { pop_entry(nav_controller, entry) },
                on_reply = { msg_id, ghost ->
                    org.astermail.android.ui.compose.publish_compose_thread_seed(shared_mail_vm.thread_state.value)
                    context.startActivity(ComposeActivity.intent_for(context, reply_to = msg_id, mode = "reply", thread_ghost_email = ghost))
                },
                on_reply_all = { msg_id, ghost ->
                    org.astermail.android.ui.compose.publish_compose_thread_seed(shared_mail_vm.thread_state.value)
                    context.startActivity(ComposeActivity.intent_for(context, reply_to = msg_id, mode = "reply_all", thread_ghost_email = ghost))
                },
                on_forward = { msg_id, ghost ->
                    org.astermail.android.ui.compose.publish_compose_thread_seed(shared_mail_vm.thread_state.value)
                    context.startActivity(ComposeActivity.intent_for(context, reply_to = msg_id, mode = "forward", thread_ghost_email = ghost))
                },
                on_archive = advance_after_action,
                on_delete = advance_after_action,
                on_next = neighbor_id(1)?.let { next -> { open_neighbor(next, 1) } },
                on_previous = org.astermail.android.ui.mail.previous_or_exit(
                    neighbor_id(-1)?.let { prev -> { open_neighbor(prev, -1) } },
                ) { pop_entry(nav_controller, entry) },
                swipe_direction = swipe_direction,
                chained = chained,
                on_navigate = { path ->
                    val route = when {
                        path.startsWith("settings/") -> routes.settings_detail(path.removePrefix("settings/"))
                        path == "settings" -> routes.settings
                        path == "pending_send_preview" -> routes.pending_send_preview
                        path.startsWith("search:") -> routes.search_for(path.removePrefix("search:"))
                        path.startsWith("contact:") -> path.removePrefix("contact:").takeIf { it.isNotBlank() }
                            ?.let { routes.contact_detail_for(it) }
                        path.startsWith("alias:") -> path.removePrefix("alias:").takeIf { it.isNotBlank() }
                            ?.let { routes.alias_detail_for(it) }
                        else -> null
                    }
                    if (route != null) navigate_once(nav_controller, route)
                },
                mail_vm = shared_mail_vm,
                settings_vm = shared_settings_vm,
            )
        }
        composable(
            route = routes.folder_filter,
            arguments = listOf(
                navArgument("folder_id") { type = NavType.StringType },
                navArgument("folder_name") { type = NavType.StringType },
            ),
        ) { entry ->
            val id = entry.arguments?.getString("folder_id").orEmpty()
            val name = entry.arguments?.getString("folder_name").orEmpty()
            val inbox_entry = remember(entry) {
                try { nav_controller.getBackStackEntry(routes.inbox) } catch (_: Throwable) { null }
            }
            val shared_mail_vm: org.astermail.android.mail.MailViewModel =
                if (inbox_entry != null) hiltViewModel(inbox_entry) else hiltViewModel()
            FilteredInboxScreen(
                filter_type = FilterType.folder,
                filter_value = id,
                filter_display_name = name,
                on_open_drawer = { pop_once(nav_controller) },
                on_open_email = { eid -> open_mail_detail(nav_controller, eid) },
                mail_vm = shared_mail_vm,
            )
        }
        composable(
            route = routes.label_filter,
            arguments = listOf(
                navArgument("label_id") { type = NavType.StringType },
                navArgument("label_name") { type = NavType.StringType },
            ),
        ) { entry ->
            val id = entry.arguments?.getString("label_id").orEmpty()
            val name = entry.arguments?.getString("label_name").orEmpty()
            val inbox_entry = remember(entry) {
                try { nav_controller.getBackStackEntry(routes.inbox) } catch (_: Throwable) { null }
            }
            val shared_mail_vm: org.astermail.android.mail.MailViewModel =
                if (inbox_entry != null) hiltViewModel(inbox_entry) else hiltViewModel()
            FilteredInboxScreen(
                filter_type = FilterType.label,
                filter_value = id,
                filter_display_name = name,
                on_open_drawer = { pop_once(nav_controller) },
                on_open_email = { eid -> open_mail_detail(nav_controller, eid) },
                mail_vm = shared_mail_vm,
            )
        }
        composable(
            route = routes.alias_filter,
            arguments = listOf(
                navArgument("alias_id") { type = NavType.StringType },
                navArgument("alias_name") { type = NavType.StringType },
            ),
        ) { entry ->
            val id = entry.arguments?.getString("alias_id").orEmpty()
            val name = entry.arguments?.getString("alias_name").orEmpty()
            val inbox_entry = remember(entry) {
                try { nav_controller.getBackStackEntry(routes.inbox) } catch (_: Throwable) { null }
            }
            val shared_mail_vm: org.astermail.android.mail.MailViewModel =
                if (inbox_entry != null) hiltViewModel(inbox_entry) else hiltViewModel()
            FilteredInboxScreen(
                filter_type = FilterType.alias,
                filter_value = id,
                filter_display_name = name,
                on_open_drawer = { pop_once(nav_controller) },
                on_open_email = { eid -> open_mail_detail(nav_controller, eid) },
                mail_vm = shared_mail_vm,
            )
        }
        composable(
            route = routes.compose,
            enterTransition = { nav_sheet_enter(nav_duration) },
            exitTransition = { nav_forward_exit(nav_duration) },
            popEnterTransition = { nav_backward_enter(nav_duration) },
            popExitTransition = { nav_sheet_exit(nav_duration) },
            arguments = listOf(
                navArgument("reply_to") {
                    type = NavType.StringType
                    nullable = true
                    defaultValue = null
                },
                navArgument("mode") {
                    type = NavType.StringType
                    nullable = true
                    defaultValue = null
                },
                navArgument("draft_id") {
                    type = NavType.StringType
                    nullable = true
                    defaultValue = null
                },
                navArgument("to") {
                    type = NavType.StringType
                    nullable = true
                    defaultValue = null
                },
                navArgument("thread_ghost") {
                    type = NavType.StringType
                    nullable = true
                    defaultValue = null
                },
                navArgument("share") {
                    type = NavType.StringType
                    nullable = true
                    defaultValue = null
                },
            ),
        ) { entry ->
            val compose_inbox_entry = remember(entry) {
                try { nav_controller.getBackStackEntry(routes.inbox) } catch (_: Throwable) { null }
            }
            val compose_mail_vm: org.astermail.android.mail.MailViewModel? =
                if (compose_inbox_entry != null) hiltViewModel(compose_inbox_entry) else null
            val compose_settings_vm: org.astermail.android.settings.SettingsViewModel? =
                if (compose_inbox_entry != null) hiltViewModel(compose_inbox_entry) else null
            val raw_reply_to = entry.arguments?.getString("reply_to")
            val raw_mode = entry.arguments?.getString("mode")
            val raw_draft_id = entry.arguments?.getString("draft_id")
            val raw_to = entry.arguments?.getString("to")
            val raw_thread_ghost = entry.arguments?.getString("thread_ghost")
            val reply_to = raw_reply_to?.takeIf { it.isNotBlank() }
            val mode = raw_mode?.takeIf { it.isNotBlank() }
            val draft_id = raw_draft_id?.takeIf { it.isNotBlank() }
            val prefill_to = raw_to?.takeIf { it.isNotBlank() }
            val thread_ghost_email = raw_thread_ghost?.takeIf { it.isNotBlank() }
            val is_share_entry = !entry.arguments?.getString("share").isNullOrBlank()
            val share_payload = remember(entry.id) {
                if (!is_share_entry) null
                else pending.pending_share.value.also { pending.pending_share.value = null }
            }
            ComposeScreen(
                on_back = { pop_entry(nav_controller, entry) },
                on_sent = { pop_entry(nav_controller, entry) },
                reply_to = reply_to,
                mode = mode,
                draft_id = draft_id,
                prefill_to = prefill_to,
                thread_ghost_email = thread_ghost_email,
                shared_mail_vm = compose_mail_vm,
                shared_settings_vm = compose_settings_vm,
                share_payload = share_payload,
            )
        }
        composable(
            route = routes.search,
        ) { entry ->
            val inbox_entry = remember(entry) {
                try { nav_controller.getBackStackEntry(routes.inbox) } catch (_: Throwable) { null }
            }
            val draft_context = androidx.compose.ui.platform.LocalContext.current
            SearchScreen(
                on_back = { pop_once(nav_controller) },
                on_open_email = { id -> open_mail_detail(nav_controller, id) },
                on_open_draft = { id -> draft_context.startActivity(ComposeActivity.intent_for(draft_context, mode = "draft", draft_id = id)) },
                mail_vm = if (inbox_entry != null) hiltViewModel(inbox_entry) else hiltViewModel(),
            )
        }
        composable(
            route = routes.search_with_query,
            arguments = listOf(
                androidx.navigation.navArgument("q") { defaultValue = "" },
                androidx.navigation.navArgument("from") { defaultValue = "" },
            ),
        ) { entry ->
            val q = entry.arguments?.getString("q").orEmpty()
            val from = entry.arguments?.getString("from").orEmpty().ifEmpty { null }
            val inbox_entry = remember(entry) {
                try { nav_controller.getBackStackEntry(routes.inbox) } catch (_: Throwable) { null }
            }
            val draft_context = androidx.compose.ui.platform.LocalContext.current
            SearchScreen(
                on_back = { pop_once(nav_controller) },
                on_open_email = { id -> open_mail_detail(nav_controller, id) },
                on_open_draft = { id -> draft_context.startActivity(ComposeActivity.intent_for(draft_context, mode = "draft", draft_id = id)) },
                initial_query = q,
                origin_folder = from,
                mail_vm = if (inbox_entry != null) hiltViewModel(inbox_entry) else hiltViewModel(),
            )
        }
        composable(routes.pending_send_preview) { entry ->
            org.astermail.android.ui.mail.pending_send_preview_screen(
                on_back = { pop_entry(nav_controller, entry) },
            )
        }
        dialog(
            routes.guard_approve,
            arguments = listOf(navArgument(org.astermail.android.ui.guard.GuardApproveViewModel.ARG_ID) { type = NavType.StringType }),
            dialogProperties = org.astermail.android.ui.guard.guard_approve_dialog_properties,
        ) {
            val approve_vm: org.astermail.android.ui.guard.GuardApproveViewModel = hiltViewModel()
            val app_locked by lock_vm.store.is_locked.collectAsStateWithLifecycle()
            androidx.compose.runtime.LaunchedEffect(app_locked) {
                if (!app_locked) approve_vm.retry_unlocked()
            }
            org.astermail.android.ui.guard.guard_approve_host(
                view_model = approve_vm,
                on_close = { if (!nav_controller.popBackStack()) nav_controller.navigate(routes.inbox) },
                on_unlock = {
                    if (lock_vm.store.is_configured()) lock_vm.store.lock() else approve_vm.retry_unlocked()
                },
                on_secure = {
                    nav_controller.navigate(routes.settings_detail("guard_panic")) {
                        popUpTo(routes.guard_approve) { inclusive = true }
                        launchSingleTop = true
                    }
                },
            )
        }
        composable(routes.settings) {
            SettingsScreen(
                on_back = { pop_once(nav_controller) },
                on_open = { id -> navigate_once(nav_controller, routes.settings_detail(id)) },
            )
        }
        composable(routes.settings_search) { entry ->
            val account_inbox_entry = remember(entry) {
                try { nav_controller.getBackStackEntry(routes.inbox) } catch (_: Throwable) { null }
            }
            val account_settings_vm = org.astermail.android.settings.shared_settings_view_model()
            val account_settings_state by account_settings_vm.state.collectAsStateWithLifecycle()
            val account_accounts_vm: org.astermail.android.accounts.AccountsViewModel =
                if (account_inbox_entry != null) hiltViewModel(account_inbox_entry) else hiltViewModel()
            val account_accounts_state by account_accounts_vm.state.collectAsStateWithLifecycle()
            val account_mail_vm: org.astermail.android.mail.MailViewModel? =
                if (account_inbox_entry != null) hiltViewModel(account_inbox_entry) else null
            val account_stats = account_mail_vm?.inbox_state?.collectAsStateWithLifecycle()?.value?.stats
            val account_storage = account_storage_usage_for(account_settings_state.storage, account_stats)
            val account_user_email = account_user_email_for(account_settings_state, account_accounts_state)
            val account_display_email = org.astermail.android.ui.common.remember_display_address(
                account_settings_state,
                account_user_email,
            )
            val account_storage_label = account_profile_storage_label(
                account_storage.used_bytes,
                account_storage.total_bytes,
            )
            org.astermail.android.ui.settings.settings_search_screen(
                on_back = { pop_once(nav_controller) },
                on_open = { id -> navigate_once(nav_controller, routes.settings_detail(id)) },
                trailing_action = { dismiss_keyboard ->
                    org.astermail.android.ui.settings.settings_account_button(
                        accounts = account_accounts_state.accounts,
                        current_account_id = account_accounts_state.current_account_id,
                        user_email = account_user_email,
                        display_email = account_display_email,
                        plan_code = account_settings_state.subscription?.plan?.code,
                        storage_used_fraction = account_storage.fraction,
                        storage_used_label = account_storage_label,
                        max_accounts = account_accounts_state.max_accounts,
                        is_unlimited_accounts = account_accounts_state.is_unlimited,
                        can_add_account = account_accounts_state.can_add_more,
                        needs_sign_in = { account ->
                            account.id != account_accounts_state.current_account_id &&
                                !account_accounts_vm.has_stored_session(account.id)
                        },
                        on_open_sheet = {
                            dismiss_keyboard()
                            account_accounts_vm.refresh_with_profile()
                        },
                        on_switch_account = { account ->
                            switch_to_stored_account(
                                account,
                                nav_controller,
                                account_accounts_vm,
                                account_settings_vm,
                                account_mail_vm,
                            ) {}
                        },
                        on_add_account = {
                            open_add_account(nav_controller, account_accounts_state.can_add_more)
                        },
                        on_manage_account = {
                            nav_controller.navigate(routes.settings_detail("profile"))
                        },
                        on_open_storage = {
                            nav_controller.navigate(routes.settings_detail("storage"))
                        },
                        on_logout = {
                            sign_out_current_account(
                                nav_controller,
                                account_accounts_vm,
                                account_settings_vm,
                                account_mail_vm,
                            ) {}
                        },
                        on_logout_all = {
                            sign_out_all_accounts(nav_controller, account_accounts_vm, account_settings_vm)
                        },
                    )
                },
            )
        }
        composable(routes.mailing_lists) {
            MailingListsScreen(
                on_back = { pop_once(nav_controller) },
                on_open_search = { navigate_once(nav_controller, routes.search) },
                on_search_sender = { sender ->
                    org.astermail.android.ui.search.build_sender_mail_query(sender)?.let { query ->
                        navigate_once(nav_controller, routes.search_for(query))
                    }
                },
            )
        }
        composable(routes.contacts) {
            ContactsScreen(
                on_back = { pop_once(nav_controller) },
                on_open_contact = { id -> navigate_once(nav_controller, routes.contact_detail_for(id)) },
                on_open_group = { id -> navigate_once(nav_controller, routes.contact_group_for(id)) },
                on_create_contact = { navigate_once(nav_controller, routes.contact_edit_new) },
                on_compose_to = { addresses ->
                    context.startActivity(
                        ComposeActivity.intent_for(context, prefill_to = addresses.joinToString(",")),
                    )
                },
            )
        }
        composable(
            route = routes.contact_group,
            arguments = listOf(navArgument("group_id") { type = NavType.StringType }),
        ) { entry ->
            val group_id = entry.arguments?.getString("group_id").orEmpty()
            val contacts_entry = remember(entry) {
                try { nav_controller.getBackStackEntry(routes.contacts) } catch (_: Throwable) {
                    try { nav_controller.getBackStackEntry(routes.inbox) } catch (_: Throwable) { null }
                }
            }
            val shared_contacts_vm: org.astermail.android.contacts.ContactsViewModel =
                if (contacts_entry != null) hiltViewModel(contacts_entry) else hiltViewModel()
            org.astermail.android.ui.contacts.ContactGroupScreen(
                group_id = group_id,
                on_back = { pop_entry(nav_controller, entry) },
                on_open_contact = { id -> navigate_once(nav_controller, routes.contact_detail_for(id)) },
                on_compose_to = { addresses ->
                    context.startActivity(
                        ComposeActivity.intent_for(context, prefill_to = addresses.joinToString(",")),
                    )
                },
                vm = shared_contacts_vm,
            )
        }
        composable(
            route = routes.contact_detail,
            arguments = listOf(navArgument("contact_id") { type = NavType.StringType }),
        ) { entry ->
            val id = entry.arguments?.getString("contact_id").orEmpty()
            val contacts_entry = remember(entry) {
                try { nav_controller.getBackStackEntry(routes.contacts) } catch (_: Throwable) {
                    try { nav_controller.getBackStackEntry(routes.inbox) } catch (_: Throwable) { null }
                }
            }
            val shared_contacts_vm: org.astermail.android.contacts.ContactsViewModel =
                if (contacts_entry != null) hiltViewModel(contacts_entry) else hiltViewModel()
            ContactDetailScreen(
                contact_id = id,
                on_back = { pop_entry(nav_controller, entry) },
                on_edit = { cid -> navigate_once(nav_controller, routes.contact_edit_for(cid)) },
                on_compose = { email -> context.startActivity(ComposeActivity.intent_for(context, prefill_to = email)) },
                on_search_mail = { query -> navigate_once(nav_controller, routes.search_for(query)) },
                vm = shared_contacts_vm,
            )
        }
        composable(routes.contact_edit_new) { entry ->
            val contacts_entry = remember(entry) {
                try { nav_controller.getBackStackEntry(routes.contacts) } catch (_: Throwable) {
                    try { nav_controller.getBackStackEntry(routes.inbox) } catch (_: Throwable) { null }
                }
            }
            val shared_contacts_vm: org.astermail.android.contacts.ContactsViewModel =
                if (contacts_entry != null) hiltViewModel(contacts_entry) else hiltViewModel()
            ContactEditScreen(
                contact_id = null,
                on_back = { pop_once(nav_controller) },
                on_saved = { pop_entry(nav_controller, entry) },
                vm = shared_contacts_vm,
            )
        }
        composable(
            route = routes.contact_edit,
            arguments = listOf(navArgument("contact_id") { type = NavType.StringType }),
        ) { entry ->
            val id = entry.arguments?.getString("contact_id")
            val contacts_entry = remember(entry) {
                try { nav_controller.getBackStackEntry(routes.contacts) } catch (_: Throwable) {
                    try { nav_controller.getBackStackEntry(routes.inbox) } catch (_: Throwable) { null }
                }
            }
            val shared_contacts_vm: org.astermail.android.contacts.ContactsViewModel =
                if (contacts_entry != null) hiltViewModel(contacts_entry) else hiltViewModel()
            ContactEditScreen(
                contact_id = id,
                on_back = { pop_once(nav_controller) },
                on_saved = { pop_entry(nav_controller, entry) },
                vm = shared_contacts_vm,
            )
        }

        val back = { pop_once(nav_controller) }
        val open_detail: (String) -> Unit = { id -> navigate_once(nav_controller, routes.settings_detail(id)) }

        composable(routes.settings_detail("appearance")) {
            AppearanceScreen(on_back = { back(); Unit }, on_open = open_detail)
        }
        composable(routes.settings_detail("profile")) {
            ProfileScreen(
                on_back = { back(); Unit },
                on_open = open_detail,
            )
        }
        composable(routes.settings_detail("default_sender")) {
            DefaultSenderScreen(on_back = { back(); Unit })
        }
        composable(routes.settings_detail("signature")) {
            SignatureScreen(on_back = { back(); Unit }, on_open = open_detail)
        }
        composable(routes.settings_detail("guard")) {
            org.astermail.android.ui.guard.guard_switches_host(
                view_model = hiltViewModel(),
                on_back = { back(); Unit },
                on_open = open_detail,
            )
        }
        composable(
            routes.settings_detail("guard") + "?rescued={rescued}",
            arguments = listOf(androidx.navigation.navArgument("rescued") { type = androidx.navigation.NavType.BoolType; defaultValue = false }),
        ) { entry ->
            org.astermail.android.ui.guard.guard_switches_host(
                view_model = hiltViewModel(),
                on_back = { back(); Unit },
                on_open = open_detail,
                rescued = entry.arguments?.getBoolean("rescued") == true,
            )
        }
        composable(routes.settings_detail("guard_changes")) {
            org.astermail.android.ui.guard.guard_changes_host(
                view_model = hiltViewModel(),
                on_back = { back(); Unit },
                on_not_me = { nav_controller.navigate(routes.settings_detail("guard_panic")) { launchSingleTop = true } },
                on_change_password = {
                    nav_controller.navigate(routes.settings_detail("change_password")) { launchSingleTop = true }
                },
            )
        }
        composable(routes.settings_detail("guard_panic")) {
            org.astermail.android.ui.guard.guard_panic_host(
                view_model = hiltViewModel(),
                on_back = { back(); Unit },
                on_change_password = {
                    nav_controller.navigate(routes.settings_detail("change_password")) { launchSingleTop = true }
                },
            )
        }
        composable(routes.settings_detail("guard_setup")) {
            org.astermail.android.ui.guard.guard_setup_host(
                view_model = hiltViewModel(),
                on_back = { back(); Unit },
            )
        }
        composable(routes.settings_detail("guard_rotate_rescue")) {
            org.astermail.android.ui.guard.guard_rotate_rescue_host(
                view_model = hiltViewModel(),
                on_back = { back(); Unit },
            )
        }
        composable(routes.settings_detail("guard_code")) {
            org.astermail.android.ui.guard.guard_code_host(
                view_model = hiltViewModel(),
                on_back = { back(); Unit },
                on_join = { nav_controller.navigate(routes.settings_detail("guard_setup")) { launchSingleTop = true } },
            )
        }
        composable(routes.settings_detail("security")) {
            SecurityScreen(
                on_back = { back(); Unit },
                on_open = open_detail,
            )
        }
        composable(routes.settings_detail("password")) { entry ->
            ChangePasswordScreen(on_back = { pop_entry(nav_controller, entry) })
        }
        composable(routes.settings_detail("change_password")) { entry ->
            ChangePasswordScreen(on_back = { pop_entry(nav_controller, entry) })
        }
        composable(routes.settings_detail("two_factor")) {
            TwoFactorScreen(on_back = { back(); Unit })
        }
        composable(routes.settings_detail("delete_account")) {
            DeleteAccountScreen(
                on_back = { back(); Unit },
                on_deleted = {
                    nav_controller.navigate(routes.welcome) {
                        popUpTo(0) { inclusive = true }
                    }
                },
            )
        }
        composable(routes.settings_detail("sessions")) {
            SessionsScreen(on_back = { back(); Unit })
        }
        composable(routes.settings_detail("recovery_codes")) {
            RecoveryCodesScreen(on_back = { back(); Unit })
        }
        composable(routes.settings_detail("recovery_email")) {
            RecoveryEmailScreen(on_back = { back(); Unit })
        }
        composable(routes.settings_detail("identity_key")) {
            EncryptionScreen(on_back = { back(); Unit }, on_open = open_detail)
        }
        composable(routes.settings_detail("contact_keys")) {
            EncryptionScreen(on_back = { back(); Unit }, on_open = open_detail)
        }
        composable(routes.settings_detail("encryption")) {
            EncryptionScreen(on_back = { back(); Unit }, on_open = open_detail)
        }
        composable(routes.settings_detail("theme")) {
            AppearanceScreen(on_back = { back(); Unit }, on_open = open_detail)
        }
        composable(routes.settings_detail("text_size")) {
            AppearanceScreen(on_back = { back(); Unit }, on_open = open_detail)
        }
        composable(
            route = routes.settings_detail("aliases") + "?create={create}",
            arguments = listOf(navArgument("create") {
                type = NavType.BoolType
                defaultValue = false
            }),
        ) { entry ->
            AliasesScreen(
                on_back = { back(); Unit },
                on_open = open_detail,
                open_create = entry.arguments?.getBoolean("create") ?: false,
                on_open_alias_mail = { id, address, routing_token ->
                    org.astermail.android.mail.alias_inbox_requests.submit(
                        org.astermail.android.mail.alias_inbox_request(
                            id = id,
                            address = address,
                            routing_token = routing_token,
                            direction = org.astermail.android.mail.alias_direction_sent,
                        ),
                    )
                    if (!nav_controller.popBackStack(routes.inbox, false)) {
                        nav_controller.navigate(routes.inbox)
                    }
                },
                on_open_alias_detail = { id -> nav_controller.navigate(routes.alias_detail_for(id)) },
                on_compose_from = { address -> nav_controller.navigate(routes.compose_from(address)) },
            )
        }
        composable(
            route = routes.alias_detail,
            arguments = listOf(navArgument("alias_id") { type = NavType.StringType }),
        ) { entry ->
            org.astermail.android.ui.settings.detail.alias_detail_screen(
                alias_id = entry.arguments?.getString("alias_id").orEmpty(),
                on_back = { back(); Unit },
                on_open = open_detail,
                on_open_alias_mail = { id, address, routing_token ->
                    org.astermail.android.mail.alias_inbox_requests.submit(
                        org.astermail.android.mail.alias_inbox_request(
                            id = id,
                            address = address,
                            routing_token = routing_token,
                            direction = org.astermail.android.mail.alias_direction_sent,
                        ),
                    )
                    if (!nav_controller.popBackStack(routes.inbox, false)) {
                        nav_controller.navigate(routes.inbox)
                    }
                },
                on_compose_from = { address -> nav_controller.navigate(routes.compose_from(address)) },
            )
        }
        composable(routes.settings_detail("domains")) {
            DomainsScreen(
                on_back = { back(); Unit },
                on_open_buy_domain = { nav_controller.navigate(routes.settings_detail("buy_domain")) },
                on_open_domain_order = { id -> nav_controller.navigate(routes.domain_order_for(id)) },
                on_open_bimi = { id -> nav_controller.navigate(routes.domain_bimi_for(id)) { launchSingleTop = true } },
            )
        }
        composable(
            route = routes.domain_bimi,
            arguments = listOf(navArgument("domain_id") { type = NavType.StringType }),
        ) { entry ->
            val domain_id = entry.arguments?.getString("domain_id").orEmpty()
            org.astermail.android.ui.settings.detail.bimi.bimi_setup_screen(
                domain_id = domain_id,
                on_back = { back(); Unit },
            )
        }
        composable(routes.settings_detail("buy_domain")) {
            DomainPurchaseScreen(
                on_back = { back(); Unit },
                on_open_progress = { id ->
                    nav_controller.navigate(routes.domain_order_for(id)) { launchSingleTop = true }
                },
            )
        }
        composable(
            route = routes.domain_order,
            arguments = listOf(navArgument("order_id") { type = NavType.StringType }),
        ) { entry ->
            val order_id = entry.arguments?.getString("order_id").orEmpty()
            DomainPurchaseProgressScreen(
                order_id = order_id,
                on_back = { back(); Unit },
                on_create_address = {
                    nav_controller.navigate(routes.settings_detail("aliases") + "?create=true") {
                        popUpTo(routes.settings) { inclusive = false }
                        launchSingleTop = true
                    }
                },
                on_done = { back(); Unit },
            )
        }
        composable(routes.settings_detail("subscriptions")) {
            MailingListsScreen(
                on_back = { back(); Unit },
                on_open_search = { nav_controller.navigate(routes.search) },
                on_search_sender = { sender ->
                    org.astermail.android.ui.search.build_sender_mail_query(sender)?.let { query ->
                        nav_controller.navigate(routes.search_for(query))
                    }
                },
            )
        }
        composable(routes.settings_detail("storage")) {
            StorageScreen(
                on_back = { back(); Unit },
                on_open = open_detail,
                on_open_folder = { folder_id, folder_name ->
                    nav_controller.navigate(routes.folder_filter_for(folder_id, folder_name)) {
                        launchSingleTop = true
                    }
                },
            )
        }
        composable(routes.settings_detail("blocked")) {
            BlockedSendersScreen(on_back = { back(); Unit })
        }
        composable(routes.settings_detail("allowlist")) {
            AllowListScreen(on_back = { back(); Unit })
        }
        composable(routes.settings_detail("mail_rules")) {
            org.astermail.android.ui.settings.mail_rules.MailRulesListScreen(
                on_back = { back(); Unit },
                on_edit = { id -> nav_controller.navigate("mail_rule_edit/$id") },
                on_new = { nav_controller.navigate("mail_rule_edit/new") },
            )
        }
        composable(
            route = "mail_rule_edit/{rule_id}",
            arguments = listOf(navArgument("rule_id") { type = NavType.StringType }),
        ) { entry ->
            val id = entry.arguments?.getString("rule_id")
            org.astermail.android.ui.settings.mail_rules.RuleEditorScreen(
                rule_id = if (id == "new") null else id,
                on_back = { back(); Unit },
                on_saved = { back(); Unit },
            )
        }
        composable(routes.settings_detail("templates")) {
            TemplatesScreen(on_back = { back(); Unit })
        }
        composable(routes.settings_detail("auto_forward")) {
            AutoForwardScreen(on_back = { back(); Unit }, on_open = open_detail)
        }
        composable(routes.settings_detail("vacation_reply")) {
            VacationReplyScreen(on_back = { back(); Unit }, on_open = open_detail)
        }
        composable(routes.settings_detail("import")) {
            ImportGroupScreen(on_back = { back(); Unit }, on_open = open_detail)
        }
        composable(routes.settings_detail("export")) {
            ImportGroupScreen(
                on_back = { back(); Unit },
                on_open = open_detail,
                initial_tab = import_group_tab_for_route("export"),
            )
        }
        composable(routes.settings_detail("diagnostics")) {
            DiagnosticsScreen(on_back = { back(); Unit })
        }
        composable(
            route = routes.settings_detail("accessibility") + "?focus={focus}",
            arguments = listOf(navArgument("focus") {
                type = NavType.StringType
                defaultValue = ""
            }),
        ) { entry ->
            AccessibilityScreen(
                on_back = { back(); Unit },
                focus_id = entry.arguments?.getString("focus").orEmpty(),
            )
        }
        composable(routes.settings_detail("behavior")) {
            BehaviorScreen(on_back = { back(); Unit }, on_open = open_detail)
        }
        composable(routes.settings_detail("swipe_actions")) {
            SwipeActionsScreen(on_back = { back(); Unit })
        }
        composable(routes.settings_detail("customize_toolbar")) {
            CustomizeToolbarScreen(on_back = { back(); Unit })
        }
        val open_crypto_invoice: (String) -> Unit = { id ->
            nav_controller.navigate(routes.crypto_invoice_for(id)) { launchSingleTop = true }
        }
        composable(routes.settings_detail("billing")) {
            SubscriptionsScreen(
                on_back = { back(); Unit },
                on_open = open_detail,
                on_open_crypto_invoice = open_crypto_invoice,
            )
        }
        composable(routes.settings_detail("billing_addons")) {
            SubscriptionsScreen(
                on_back = { back(); Unit },
                on_open = open_detail,
                scroll_to_addons = true,
                on_open_crypto_invoice = open_crypto_invoice,
            )
        }
        composable(
            route = routes.crypto_invoice,
            arguments = listOf(navArgument("invoice_id") { type = NavType.StringType }),
        ) { entry ->
            val invoice_id = entry.arguments?.getString("invoice_id").orEmpty()
            crypto_invoice_screen(
                invoice_id = invoice_id,
                on_back = { back(); Unit },
                on_go_to_inbox = {
                    nav_controller.navigate(routes.inbox) {
                        popUpTo(routes.inbox) { inclusive = true }
                        launchSingleTop = true
                    }
                },
                on_view_billing = {
                    nav_controller.navigate(routes.settings_detail("billing")) {
                        popUpTo(routes.crypto_invoice) { inclusive = true }
                        launchSingleTop = true
                    }
                },
            )
        }
        composable(routes.settings_detail("features")) {
            FeaturesScreen(on_back = { back(); Unit })
        }
        composable(routes.settings_detail("notifications")) {
            NotificationsScreen(on_back = { back(); Unit }, on_open = open_detail)
        }
        composable(routes.settings_detail("feedback")) {
            FeedbackScreen(on_back = { back(); Unit })
        }
        composable(routes.settings_detail("external_accounts")) {
            ImportGroupScreen(
                on_back = { back(); Unit },
                on_open = open_detail,
                initial_tab = import_group_tab_for_route("external_accounts"),
            )
        }
        composable(routes.settings_detail("external_accounts_gmail")) {
            ImportGroupScreen(
                on_back = { back(); Unit },
                on_open = open_detail,
                initial_tab = import_group_tab_for_route("external_accounts_gmail"),
                start_gmail_wizard = true,
            )
        }
        composable(routes.settings_detail("sender_filters")) {
            SenderFiltersScreen(on_back = { back(); Unit }, on_open = open_detail)
        }
        composable(routes.settings_detail("trusted_devices")) {
            TrustedDevicesScreen(on_back = { back(); Unit }, on_open = open_detail)
        }
        composable(routes.settings_detail("link_device")) {
            LinkDeviceScreen(on_back = { back(); Unit })
        }
        composable(routes.settings_detail("referral")) {
            ReferralScreen(on_back = { back(); Unit })
        }
        composable(routes.settings_detail("labels")) {
            LabelsScreen(on_back = { back(); Unit })
        }
        composable(routes.settings_detail("folders")) {
            FoldersScreen(on_back = { back(); Unit })
        }
        composable(routes.settings_detail("privacy")) {
            PrivacyScreen(on_back = { back(); Unit })
        }
        composable(routes.settings_detail("api_keys")) {
            ApiKeysScreen(on_back = { back(); Unit })
        }
        composable(routes.settings_detail("language")) {
            LanguageScreen(on_back = { back(); Unit })
        }
composable(routes.settings_detail("family")) {
            FamilyScreen(on_back = { back(); Unit }, on_open = { id -> nav_controller.navigate(routes.settings_detail(id)) })
        }
        composable(routes.settings_detail("family_kids")) {
            KidsReservedScreen(on_back = { back(); Unit })
        }
        composable(routes.settings_detail("smtp_tokens")) {
            SmtpTokensScreen(
                on_back = { back(); Unit },
                on_upgrade = { nav_controller.navigate(routes.settings_detail("billing")) { launchSingleTop = true } },
            )
        }
        composable(routes.settings_detail("about")) {
            AboutScreen(
                on_back = { back(); Unit },
                on_open = { id -> nav_controller.navigate(routes.settings_detail(id)) { launchSingleTop = true } },
            )
        }
        composable(routes.settings_detail("developer")) {
            DeveloperScreen(on_back = { back(); Unit })
        }
    }

    if (is_signed_in_state) {
        val offer_allowed by remember(nav_controller) {
            nav_controller.currentBackStackEntryFlow.map { it.destination.route != routes.register }
        }.collectAsState(initial = true)
        if (!is_locked && offer_allowed) {
            if (BuildConfig.SPECIAL_OFFER) org.astermail.android.ui.upgrade.SpecialOfferHost()
        }
    }

    if (is_signed_in_state && !is_locked) {
        val gate_settings_vm = org.astermail.android.settings.shared_settings_view_model()
        org.astermail.android.ui.account.SuspendedAccountGate(
            on_switched = { account, restored ->
                gate_settings_vm.reset_for_account_switch()
                val destination = if (restored) routes.inbox else routes.sign_in_for(account.email)
                nav_controller.navigate(destination) {
                    popUpTo(0) { inclusive = true }
                }
            },
            on_add_account = {
                nav_controller.navigate(routes.sign_in_for(""))
            },
            on_signed_out = { switched_account ->
                val destination = if (switched_account) routes.inbox else routes.welcome
                nav_controller.navigate(destination) {
                    popUpTo(0) { inclusive = true }
                }
            },
        )
        org.astermail.android.ui.account.PendingDeletionGate(
            on_reactivated = {
                nav_controller.navigate(routes.inbox) {
                    popUpTo(0) { inclusive = true }
                }
            },
            on_signed_out = { switched_account ->
                val destination = if (switched_account) routes.inbox else routes.welcome
                nav_controller.navigate(destination) {
                    popUpTo(0) { inclusive = true }
                }
            },
        )
    }

    val guard_veto = pending.pending_guard_veto.value
    if (guard_veto != null) {
        org.astermail.android.ui.guard.guard_veto_host(
            link = guard_veto,
            on_close = { pending.pending_guard_veto.value = null },
            on_change_password = if (is_signed_in_state) {
                {
                    pending.pending_guard_veto.value = null
                    nav_controller.navigate(routes.settings_detail("change_password")) { launchSingleTop = true }
                }
            } else {
                null
            },
        )
    }

    val pending_link = pending.pending_account_link.value
    if (pending_link != null && is_signed_in_state && !is_locked) {
        org.astermail.android.accountlink.account_link_confirm_dialog(
            request_id = pending_link,
            on_done = { pending.pending_account_link.value = null },
        )
    }

    if (is_locked && is_signed_in_state) {
        AppLockScreen(
            store = lock_vm.store,
            on_sign_out = {
                nav_scope.launch {
                    auth_gate.auth_repository.logout_all()
                    nav_controller.navigate(routes.welcome) {
                        popUpTo(0) { inclusive = true }
                    }
                }
            },
        )
    }
    }
    if (splash_was_shown.value) {
        val splash_alpha = remember { androidx.compose.animation.core.Animatable(1f) }
        val splash_reduce = org.astermail.android.design.aster_reduce_motion()
        androidx.compose.runtime.LaunchedEffect(Unit) {
            splash_alpha.animateTo(
                targetValue = 0f,
                animationSpec = tween(
                    durationMillis = if (splash_reduce) {
                        org.astermail.android.design.AsterMotionSpec.reduced_fade_out_ms
                    } else {
                        org.astermail.android.design.AsterMotionSpec.fade_exit_ms
                    },
                    easing = org.astermail.android.design.AsterEasing.emphasized_accelerate,
                ),
            )
            splash_was_shown.value = false
        }
        aster_launch_splash(Modifier.graphicsLayer { alpha = splash_alpha.value })
    }
}

@Composable
private fun aster_launch_splash(modifier: Modifier) {
    androidx.compose.foundation.layout.Column(
        modifier = modifier
            .fillMaxSize()
            .background(AsterMaterial.colors.bg_primary),
        verticalArrangement = androidx.compose.foundation.layout.Arrangement.Center,
        horizontalAlignment = androidx.compose.ui.Alignment.CenterHorizontally,
    ) {
        androidx.compose.foundation.Image(
            painter = androidx.compose.ui.res.painterResource(R.drawable.aster_wordmark),
            contentDescription = null,
            modifier = Modifier.height(22.dp),
        )
        androidx.compose.foundation.layout.Spacer(modifier = Modifier.height(20.dp))
        org.astermail.android.design.components.aster_linear_progress(
            color = AsterMaterial.colors.accent_blue,
            track_color = AsterMaterial.colors.text_primary.copy(alpha = 0.14f),
            modifier = Modifier
                .width(160.dp)
                .height(3.dp)
                .clip(androidx.compose.foundation.shape.RoundedCornerShape(2.dp)),
        )
    }
}

private val mail_folder_ids = setOf(
    "inbox", "sent", "drafts", "trash", "spam", "archive",
    "starred", "all", "scheduled", "snoozed",
)

private const val open_folder_request_key = "open_folder_request"

@Composable
private fun InboxWithDrawer(nav_controller: NavHostController) {
    val pending = local_pending_launch.current
    val drawer_state = rememberDrawerState(initialValue = DrawerValue.Closed)
    val scope = rememberCoroutineScope()
    val settings_vm: org.astermail.android.settings.SettingsViewModel = org.astermail.android.settings.shared_settings_view_model()
    val launch_prefs = settings_vm.state.value.preferences
    val launch_target = remember {
        val stored = pending.take_launch_folder()
        org.astermail.android.mail.resolve_launch_destination(
            stored = stored,
            plain_launch = stored != null,
            has_pending_target = pending.has_pending_target(),
            categories_enabled = launch_prefs?.inbox_categories_enabled ?: true,
            active_category_tabs = launch_prefs
                ?.let { org.astermail.android.mail.active_category_tabs(it.enabled_categories, emptyList(), 0) }
                ?: org.astermail.android.mail.CATEGORY_TABS,
        )
    }
    var selected_folder by androidx.compose.runtime.saveable.rememberSaveable { mutableStateOf(launch_target.folder) }
    var inbox_category by androidx.compose.runtime.saveable.rememberSaveable { mutableStateOf(launch_target.category) }
    var filter_kind by androidx.compose.runtime.saveable.rememberSaveable { mutableStateOf<String?>(null) }
    var all_mail_include_spam by androidx.compose.runtime.saveable.rememberSaveable { mutableStateOf(false) }
    var all_mail_include_trash by androidx.compose.runtime.saveable.rememberSaveable { mutableStateOf(false) }
    var filter_value by androidx.compose.runtime.saveable.rememberSaveable { mutableStateOf("") }
    var filter_name by androidx.compose.runtime.saveable.rememberSaveable { mutableStateOf("") }
    var alias_direction by androidx.compose.runtime.saveable.rememberSaveable {
        mutableStateOf(org.astermail.android.mail.alias_direction_all)
    }
    val pending_alias_inbox by org.astermail.android.mail.alias_inbox_requests.pending.collectAsStateWithLifecycle()
    androidx.compose.runtime.LaunchedEffect(pending_alias_inbox) {
        val request = pending_alias_inbox ?: return@LaunchedEffect
        filter_kind = org.astermail.android.mail.filter_kind_alias
        filter_value = request.routing_token
        filter_name = request.address
        alias_direction = request.direction
        selected_folder = request.id
        org.astermail.android.mail.alias_inbox_requests.consume(request)
    }
    val colors = AsterMaterial.colors

    val mail_vm: org.astermail.android.mail.MailViewModel = hiltViewModel()
    val inbox_state by mail_vm.inbox_state.collectAsStateWithLifecycle()
    val stats = inbox_state.stats
    val label_unread_deltas by mail_vm.label_unread_deltas.collectAsStateWithLifecycle()

    val settings_state by settings_vm.state.collectAsStateWithLifecycle()
    androidx.compose.runtime.LaunchedEffect(settings_state.labels) {
        mail_vm.on_labels_loaded(System.identityHashCode(settings_state.labels))
    }

    val accounts_vm: org.astermail.android.accounts.AccountsViewModel = hiltViewModel()
    val accounts_state by accounts_vm.state.collectAsStateWithLifecycle()

    val drawer_context = androidx.compose.ui.platform.LocalContext.current
    androidx.compose.runtime.LaunchedEffect(
        settings_state.user,
        settings_state.aliases,
        settings_state.custom_domain_addresses,
        settings_state.ghost_aliases,
        settings_state.default_sender_id,
    ) {
        org.astermail.android.ui.compose.publish_compose_identity_seed(
            drawer_context,
            settings_state,
        )
    }
    androidx.compose.runtime.LaunchedEffect(settings_state.action_result) {
        val msg = settings_state.action_result ?: return@LaunchedEffect
        org.astermail.android.ui.common.app_toast.show(msg)
        settings_vm.clear_action_result()
    }

    androidx.compose.runtime.LaunchedEffect(Unit) {
        drawer_state.snapTo(DrawerValue.Closed)
        settings_vm.load_storage()
        settings_vm.load_profile()
        settings_vm.load_labels()
        settings_vm.load_tags(force = false)
        settings_vm.load_aliases()
        settings_vm.load_custom_domain_addresses()
        settings_vm.load_ghost_aliases()
        settings_vm.load_preferences()
        settings_vm.load_signature()
        mail_vm.load_stats()
    }

    androidx.compose.runtime.LaunchedEffect(drawer_state.isOpen) {
        if (drawer_state.isOpen) {
            androidx.compose.runtime.snapshotFlow { drawer_state.isAnimationRunning }.first { !it }
            kotlinx.coroutines.delay(120)
            settings_vm.load_labels()
            settings_vm.load_tags()
            settings_vm.load_aliases()
            settings_vm.load_custom_domain_addresses()
        }
    }

    val pending_reveal_tokens = pending.pending_reveal_folder_tokens.value
    androidx.compose.runtime.LaunchedEffect(pending_reveal_tokens, settings_state.labels) {
        val tokens = pending_reveal_tokens ?: return@LaunchedEffect
        if (settings_state.labels.isEmpty()) return@LaunchedEffect
        pending.pending_reveal_folder_tokens.value = null
        val node = org.astermail.android.folders.flatten_folder_tree(settings_state.labels)
            .firstOrNull { it.label.label_token in tokens } ?: return@LaunchedEffect
        val readable_name = org.astermail.android.labels.readable_label_name(node.label) ?: return@LaunchedEffect
        filter_kind = "folder"
        filter_value = node.label.label_token
        filter_name = readable_name
        selected_folder = node.label.label_token
    }

    val prefs = settings_state.preferences
    val categories_enabled = prefs?.inbox_categories_enabled ?: true

    androidx.compose.runtime.LaunchedEffect(prefs?.show_alias_indicators) {
        org.astermail.android.ui.mail.alias_indicator_store.set_enabled(
            prefs?.show_alias_indicators ?: true,
        )
    }

    androidx.compose.runtime.LaunchedEffect(prefs?.folder_lock_mode) {
        org.astermail.android.folders.folder_lock_store.set_lock_mode(
            prefs?.folder_lock_mode ?: org.astermail.android.folders.folder_lock_mode_session,
        )
    }

    androidx.compose.runtime.LaunchedEffect(
        prefs?.time_format,
        prefs?.time_zone,
        prefs?.date_format,
        prefs?.relative_dates,
        prefs?.calendar_week_starts_on,
    ) {
        org.astermail.android.ui.mail.AsterTimePreferences.set_account_week_start(prefs?.calendar_week_starts_on)
        org.astermail.android.ui.mail.AsterTimePreferences.set_account_time_format(prefs?.time_format)
        org.astermail.android.ui.mail.AsterTimePreferences.set_account_time_zone(prefs?.time_zone)
        org.astermail.android.ui.mail.AsterTimePreferences.set_account_date_format(prefs?.date_format)
        org.astermail.android.ui.mail.AsterTimePreferences.set_account_relative_dates(
            prefs?.relative_dates ?: true,
        )
    }

    androidx.compose.runtime.LaunchedEffect(prefs?.toast_position) {
        org.astermail.android.ui.common.set_toast_position(prefs?.toast_position)
    }

    androidx.compose.runtime.LaunchedEffect(
        settings_state.aliases,
        settings_state.ghost_aliases,
    ) {
        val alias_entries = settings_state.aliases
            .filterNot { it.decryption_failed }
            .map {
                org.astermail.android.ui.mail.AliasLabelEntry(
                    it.address,
                    it.alias_address_hash,
                )
            }
        val ghost_entries = settings_state.ghost_aliases
            .filterNot { it.decryption_failed || it.decrypted_address.isBlank() }
            .map {
                org.astermail.android.ui.mail.AliasLabelEntry(
                    it.decrypted_address,
                    address_hash = it.alias_address_hash,
                )
            }
        val entries = alias_entries + ghost_entries
        org.astermail.android.ui.mail.alias_indicator_store.set_labels(
            org.astermail.android.ui.mail.build_alias_label_map(entries),
        )
        org.astermail.android.ui.mail.alias_indicator_store.set_token_labels(
            org.astermail.android.ui.mail.build_alias_token_label_map(entries),
        )
    }
    val plan_vm_inbox: org.astermail.android.billing.PlanLimitsViewModel = hiltViewModel()
    val plan_state_inbox by plan_vm_inbox.state.collectAsStateWithLifecycle()
    val custom_category_limit =
        plan_state_inbox.limits?.limits?.get("max_custom_categories")?.limit ?: -1
    val active_category_tabs = androidx.compose.runtime.remember(
        prefs?.enabled_categories,
        prefs?.custom_categories,
        custom_category_limit,
    ) {
        if (prefs == null) {
            org.astermail.android.mail.CATEGORY_TABS
        } else {
            org.astermail.android.mail.active_category_tabs(
                prefs.enabled_categories,
                org.astermail.android.mail.sanitize_custom_categories(prefs.custom_categories),
                custom_category_limit,
            )
        }
    }
    var last_inbox_category_unread by androidx.compose.runtime.remember(accounts_state.current_account_id) {
        mutableStateOf<Map<String, Int>>(emptyMap())
    }
    val inbox_counts_ready = inbox_state.current_folder == "inbox" &&
        (inbox_state.items.isNotEmpty() || !inbox_state.is_loading)
    val category_unread by androidx.compose.runtime.produceState(
        initialValue = last_inbox_category_unread.filterKeys { it in active_category_tabs },
        inbox_state.items,
        inbox_counts_ready,
        active_category_tabs,
        accounts_state.current_account_id,
        prefs?.conversation_grouping,
    ) {
        val tabs = active_category_tabs
        if (!inbox_counts_ready) {
            value = last_inbox_category_unread.filterKeys { it in tabs }
            return@produceState
        }
        val items = inbox_state.items
        val grouping = prefs?.conversation_grouping != false
        value = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.Default) {
            org.astermail.android.mail.category_unread_counts(items, tabs, grouping)
        }
    }
    androidx.compose.runtime.LaunchedEffect(category_unread, inbox_counts_ready) {
        if (inbox_counts_ready && category_unread != last_inbox_category_unread) {
            last_inbox_category_unread = category_unread
        }
    }
    val category_entries = org.astermail.android.mail.category_entries(
        active_category_tabs,
        prefs?.custom_categories ?: emptyList(),
    )

    androidx.compose.runtime.LaunchedEffect(category_entries, categories_enabled) {
        if (categories_enabled &&
            inbox_category != "primary" &&
            category_entries.none { it.id == inbox_category }
        ) {
            inbox_category = "primary"
        }
    }
    val theme_vm_inbox: ThemeViewModel = hiltViewModel()
    val drawer_color_theme by theme_vm_inbox.color_theme.collectAsStateWithLifecycle()
    val drawer_theme_overrides by theme_vm_inbox.custom_theme_overrides.collectAsStateWithLifecycle()
    val drawer_surface = if (
        drawer_color_theme == org.astermail.android.design.ColorThemeId.custom.name &&
        org.astermail.android.design.MaterialThemeGenerator.has_sidebar_override(drawer_theme_overrides)
    ) {
        colors.sidebar_bg
    } else {
        colors.bg_primary
    }

    val learned_categories_known = settings_state.preferences_authoritative || settings_state.preferences_cached
    androidx.compose.runtime.LaunchedEffect(
        prefs?.custom_categories,
        prefs?.learn_from_category_moves,
        prefs?.learned_category_senders,
        learned_categories_known,
    ) {
        if (prefs != null) {
            if (learned_categories_known) {
                mail_vm.set_category_preferences(
                    prefs.custom_categories.orEmpty(),
                    prefs.learn_from_category_moves,
                    prefs.learned_category_senders,
                )
            } else {
                mail_vm.set_custom_categories(prefs.custom_categories.orEmpty())
            }
        }
    }

    androidx.compose.runtime.LaunchedEffect(mail_vm, settings_vm) {
        mail_vm.learned_category_edits.collect { settings_vm.apply_learned_category_edit(it) }
    }

    androidx.compose.runtime.LaunchedEffect(prefs?.conversation_grouping) {
        if (prefs != null) mail_vm.set_conversation_grouping(prefs.conversation_grouping)
    }

    androidx.compose.runtime.LaunchedEffect(prefs) {
        if (prefs == null) return@LaunchedEffect
        theme_vm_inbox.set_high_contrast(prefs.high_contrast)
        theme_vm_inbox.set_reduce_transparency(prefs.reduce_transparency)
        theme_vm_inbox.set_reduce_motion(prefs.reduce_motion)
        theme_vm_inbox.set_compact_mode(prefs.compact_mode)
        theme_vm_inbox.set_text_spacing(prefs.text_spacing)
        theme_vm_inbox.set_underline_links(prefs.underline_links)
        theme_vm_inbox.set_hide_scrollbars(prefs.hide_scrollbars)
        theme_vm_inbox.set_haptic_enabled(prefs.haptic_enabled)
        theme_vm_inbox.set_dyslexia_font(prefs.dyslexia_font)
        theme_vm_inbox.set_text_size_from_key(prefs.font_size_scale)
    }

    val storage_usage = account_storage_usage_for(settings_state.storage, stats)
    val used_bytes = storage_usage.used_bytes
    val total_bytes = storage_usage.total_bytes
    val storage_fraction = storage_usage.fraction
    val storage_label = when {
        total_bytes > 0 -> stringResource(
            R.string.common_storage_used_of_total,
            format_storage_bytes(used_bytes),
            format_storage_bytes(total_bytes),
        )
        used_bytes > 0 -> stringResource(
            R.string.common_storage_used_only,
            format_storage_bytes(used_bytes),
        )
        else -> ""
    }

    val user_email = account_user_email_for(settings_state, accounts_state)
    val display_email = org.astermail.android.ui.common.remember_display_address(settings_state, user_email)

    var show_account_manager by remember { mutableStateOf(false) }
    if (show_account_manager) {
        org.astermail.android.ui.settings.account_manager_sheet(
            accounts = accounts_state.accounts,
            current_account_id = accounts_state.current_account_id,
            user_email = user_email,
            display_email = display_email,
            plan_code = settings_state.subscription?.plan?.code,
            storage_used_fraction = storage_fraction,
            storage_used_label = account_profile_storage_label(used_bytes, total_bytes),
            max_accounts = accounts_state.max_accounts,
            is_unlimited_accounts = accounts_state.is_unlimited,
            can_add_account = accounts_state.can_add_more,
            needs_sign_in = { account ->
                account.id != accounts_state.current_account_id &&
                    !accounts_vm.has_stored_session(account.id)
            },
            on_dismiss = { show_account_manager = false },
            on_switch_account = { account ->
                switch_to_stored_account(account, nav_controller, accounts_vm, settings_vm, mail_vm) {
                    selected_folder = "inbox"
                    filter_kind = null
                }
            },
            on_add_account = { open_add_account(nav_controller, accounts_state.can_add_more) },
            on_manage_account = { navigate_once(nav_controller, routes.settings) },
            on_open_storage = { navigate_once(nav_controller, routes.settings_detail("storage")) },
            on_logout = {
                sign_out_current_account(nav_controller, accounts_vm, settings_vm, mail_vm) {
                    selected_folder = "inbox"
                    filter_kind = null
                }
            },
            on_logout_all = { sign_out_all_accounts(nav_controller, accounts_vm, settings_vm) },
        )
    }

    val folder_nodes = remember(settings_state.labels) {
        org.astermail.android.folders.flatten_folder_tree(settings_state.labels)
    }
    val folder_index = remember(settings_state.labels) {
        org.astermail.android.folders.folder_tree_index(settings_state.labels)
    }

    val muted_folder_tokens = settings_state.preferences?.muted_folder_tokens.orEmpty()

    val lock_revision by org.astermail.android.folders.folder_lock_store.revision.collectAsState()

    val api_folders = remember(
        folder_nodes,
        folder_index,
        label_unread_deltas,
        muted_folder_tokens,
        lock_revision,
        drawer_context,
    ) { folder_nodes.map { node ->
        val label = node.label
        val readable_name = org.astermail.android.labels.readable_label_name(label)
        val siblings = folder_index.sibling_group(label.id)
        val sibling_index = siblings.indexOfFirst { it.id == label.id }
        drawer_folder_item(
            id = label.label_token,
            label = readable_name ?: drawer_context.getString(R.string.folder_decrypt_failed),
            icon = if (org.astermail.android.folders.is_folder_protected(label)) TablerIcons.Lock else TablerIcons.Folder,
            count = if (org.astermail.android.folders.requires_unlock(label)) {
                0
            } else {
                ((label.unread_count?.toInt() ?: 0) + (label_unread_deltas[label.label_token] ?: 0)).coerceAtLeast(0)
            },
            depth = node.depth,
            trail = node.trail,
            has_next = node.has_next,
            has_children = node.has_children,
            label_id = label.id,
            color = label.encrypted_color?.takeIf { it.startsWith("#") },
            password_set = label.password_set || label.is_password_protected,
            is_unlocked = org.astermail.android.folders.is_folder_protected(label) &&
                org.astermail.android.folders.folder_lock_store.is_unlocked(label.id),
            muted = label.label_token in muted_folder_tokens,
            can_move_up = sibling_index > 0,
            can_move_down = sibling_index >= 0 && sibling_index < siblings.lastIndex,
            can_have_children = node.depth < org.astermail.android.folders.max_folder_depth,
            parent_token = label.parent_token?.takeIf { it.isNotBlank() },
            blocked_parent_tokens = folder_index.descendant_tokens(label.label_token),
        )
    } }

    val folder_parent_options = remember(folder_nodes, folder_index) { folder_nodes
        .filter { it.depth < org.astermail.android.folders.max_folder_depth }
        .mapNotNull { node ->
            val label = node.label
            val readable_name = org.astermail.android.labels.readable_label_name(label)
                ?: return@mapNotNull null
            org.astermail.android.ui.drawer.folder_parent_option(
                token = label.label_token,
                label = readable_name,
                depth = node.depth,
                path_label = folder_index.path(label.label_token)
                    .filter { it.isNotBlank() }
                    .joinToString(" · "),
                color = label.encrypted_color?.takeIf { it.startsWith("#") },
            )
        } }

    val quick_custom_folders = remember(folder_nodes) { folder_nodes
        .mapNotNull { node ->
            val readable_name = org.astermail.android.labels.readable_label_name(node.label)
                ?: return@mapNotNull null
            org.astermail.android.ui.mail.quick_folder_node(
                id = node.label.label_token,
                name = readable_name,
                depth = node.depth,
                has_children = node.has_children,
                parent_id = node.label.parent_token?.takeIf { it.isNotBlank() },
                color = node.label.encrypted_color?.takeIf { it.startsWith("#") },
            )
        } }

    val quick_folder_counts = buildMap {
        put("inbox", stats?.unread ?: 0)
        put("drafts", stats?.drafts ?: 0)
        put("spam", stats?.spam ?: 0)
        put("trash", stats?.trash ?: 0)
        folder_nodes.forEach { node ->
            if (org.astermail.android.folders.requires_unlock(node.label)) return@forEach
            val unread = ((node.label.unread_count?.toInt() ?: 0) + (label_unread_deltas[node.label.label_token] ?: 0))
                .coerceAtLeast(0)
            if (unread > 0) put(node.label.label_token, unread)
        }
    }

    val label_colors = listOf(
        Color(0xFF3B82F6),
        Color(0xFF22C55E),
        Color(0xFFF59E0B),
        Color(0xFFA855F7),
        Color(0xFFEC4899),
        Color(0xFF14B8A6),
        Color(0xFFF97316),
        Color(0xFF6366F1),
    )

    val tag_index = remember(settings_state.tags) {
        org.astermail.android.labels.tag_tree_index(settings_state.tags)
    }

    val label_parent_options = remember(tag_index) {
        org.astermail.android.ui.drawer.tag_parent_options(tag_index)
    }

    val api_labels = remember(tag_index, settings_state.labels, drawer_context) {
        val tag_nodes = tag_index.nodes
        val label_decrypt_failed = drawer_context.getString(R.string.label_decrypt_failed)
        val from_tags = tag_nodes
            .mapIndexed { idx, node ->
                val tag = node.tag
                val siblings = tag_index.sibling_group(tag.id)
                val sibling_index = siblings.indexOfFirst { it.id == tag.id }
                val color = parse_hex_color_safe(tag.encrypted_color)
                    ?: label_colors[idx % label_colors.size]
                val icon = org.astermail.android.labels.readable_tag_icon(tag)
                drawer_label_item(
                    id = "tag:${tag.tag_token}",
                    label = org.astermail.android.labels.tag_display_name(tag, label_decrypt_failed),
                    color = color,
                    icon = icon,
                    api_id = tag.id,
                    kind = "tag",
                    color_hex = tag.encrypted_color?.takeIf { it.startsWith("#") },
                    can_move_up = sibling_index > 0,
                    can_move_down = sibling_index >= 0 && sibling_index < siblings.lastIndex,
                    name_readable = org.astermail.android.labels.tag_name_readable(tag),
                    depth = node.depth,
                    trail = node.trail,
                    has_next = node.has_next,
                    parent_token = tag_index.parent_of(tag.tag_token),
                )
            }
        val visible_labels = org.astermail.android.labels.label_rows(settings_state.labels)
        val from_labels = visible_labels
            .mapIndexed { idx, label ->
                val own_color = label.encrypted_color?.takeIf { it.startsWith("#") }
                val color = own_color?.let { parse_hex_color_safe(it) }
                    ?: label_colors[(from_tags.size + idx) % label_colors.size]
                val icon = org.astermail.android.labels.readable_label_icon(label)
                drawer_label_item(
                    id = "label:${label.label_token}",
                    label = label.encrypted_name.orEmpty(),
                    color = color,
                    icon = icon,
                    api_id = label.id,
                    kind = "label",
                    color_hex = own_color,
                    can_move_up = idx > 0,
                    can_move_down = idx < visible_labels.lastIndex,
                )
            }
        from_tags + from_labels
    }

    val api_aliases = remember(settings_state.aliases, settings_state.custom_domain_addresses) {
        org.astermail.android.settings.sidebar_alias_items(
            settings_state.aliases,
            settings_state.custom_domain_addresses,
        )
    }

    var pending_unlock_folder by remember { mutableStateOf<Pair<String, String>?>(null) }
    var unlock_verifying by remember { mutableStateOf(false) }
    var unlock_error by remember { mutableStateOf<String?>(null) }

    val open_custom_folder: (String, String) -> Unit = { token, name ->
        filter_kind = "folder"
        filter_value = token
        filter_name = name
        selected_folder = token
    }

    val open_folder_request_handle = remember(nav_controller) {
        try { nav_controller.getBackStackEntry(routes.inbox).savedStateHandle } catch (_: Throwable) { null }
    }
    val open_folder_request by (
        open_folder_request_handle?.getStateFlow(open_folder_request_key, "")
            ?: kotlinx.coroutines.flow.MutableStateFlow("")
        ).collectAsStateWithLifecycle()
    androidx.compose.runtime.LaunchedEffect(open_folder_request) {
        val requested = open_folder_request
        if (requested.isBlank()) return@LaunchedEffect
        open_folder_request_handle?.set(open_folder_request_key, "")
        if (requested in mail_folder_ids) {
            filter_kind = null
            filter_value = ""
            filter_name = ""
            selected_folder = requested
        }
    }

    val request_custom_folder: (String, String) -> Unit = { token, name ->
        lock_revision.let { }
        val label = settings_state.labels.firstOrNull { it.label_token == token }
        if (label != null && org.astermail.android.folders.requires_unlock(label)) {
            unlock_error = null
            pending_unlock_folder = token to name
        } else {
            open_custom_folder(token, name)
        }
    }

    val active_folder_token = if (filter_kind == "folder") filter_value else selected_folder
    val leave_protected_folder: () -> Unit = {
        filter_kind = null
        filter_value = ""
        filter_name = ""
        selected_folder = "inbox"
    }
    androidx.compose.runtime.LaunchedEffect(active_folder_token) {
        org.astermail.android.folders.folder_lock_store.set_active_folder_token(active_folder_token)
    }
    val locked_active_folder = androidx.compose.runtime.remember(
        active_folder_token,
        settings_state.labels,
        lock_revision,
    ) {
        org.astermail.android.folders.locked_active_folder(settings_state.labels, active_folder_token)
    }
    val effective_filter_kind = if (locked_active_folder != null) null else filter_kind
    val effective_selected_folder = if (locked_active_folder != null) "inbox" else selected_folder
    val effective_mail_folder = if (effective_selected_folder == org.astermail.android.mail.all_mail_folder) {
        org.astermail.android.mail.all_mail_folder_id(all_mail_include_spam, all_mail_include_trash)
    } else {
        effective_selected_folder
    }

    androidx.compose.runtime.LaunchedEffect(locked_active_folder?.label_token) {
        val label = locked_active_folder ?: return@LaunchedEffect
        val readable_name = org.astermail.android.labels.readable_label_name(label)
            ?: drawer_context.getString(R.string.folder_decrypt_failed)
        filter_kind = null
        filter_value = ""
        filter_name = ""
        selected_folder = "inbox"
        unlock_error = null
        pending_unlock_folder = label.label_token to readable_name
    }

    ModalNavigationDrawer(
        drawerState = drawer_state,
        scrimColor = Color.Black.copy(alpha = 0.32f),
        drawerContent = {
            ModalDrawerSheet(
                drawerContainerColor = if (colors.is_glass) colors.sidebar_bg.copy(alpha = 1f) else drawer_surface,
                drawerTonalElevation = 0.dp,
                windowInsets = androidx.compose.foundation.layout.WindowInsets(0, 0, 0, 0),
                drawerShape = androidx.compose.foundation.shape.RoundedCornerShape(
                    topEnd = 28.dp,
                    bottomEnd = 28.dp,
                ),
                modifier = Modifier.fillMaxWidth(0.87f),
            ) {
            DrawerContent(
                selected_id = selected_folder,
                on_select = { id ->
                    when {
                        id == "settings" -> { nav_controller.navigate(routes.settings); scope.launch { drawer_state.close() } }
                        id == "contacts" -> { filter_kind = null; selected_folder = "contacts"; scope.launch { drawer_state.close() } }
                        id == "subscriptions" -> { filter_kind = null; selected_folder = "subscriptions"; scope.launch { drawer_state.close() } }
                        id == "plan" -> { nav_controller.navigate(routes.settings_detail("billing")); scope.launch { drawer_state.close() } }
                        id == "aliases_settings" -> { nav_controller.navigate(routes.settings_detail("aliases")); scope.launch { drawer_state.close() } }
                        id == "aliases_create" -> { nav_controller.navigate(routes.settings_detail("aliases") + "?create=true"); scope.launch { drawer_state.close() } }
                        id == "referral" -> { nav_controller.navigate(routes.settings_detail("referral")); scope.launch { drawer_state.close() } }
                        id == "feedback" -> { nav_controller.navigate(routes.settings_detail("feedback")); scope.launch { drawer_state.close() } }
                        id in mail_folder_ids -> { filter_kind = null; selected_folder = id; scope.launch { drawer_state.close() } }
                        else -> { filter_kind = null; selected_folder = id; scope.launch { drawer_state.close() } }
                    }
                },
                on_close = { scope.launch { drawer_state.close() } },
                on_navigate_folder = { id, name ->
                    request_custom_folder(id, name)
                    scope.launch { drawer_state.close() }
                },
                on_navigate_label = { id, name ->
                    when {
                        id.startsWith("tag:") -> {
                            filter_kind = "tag"
                            filter_value = id.removePrefix("tag:")
                        }
                        id.startsWith("label:") -> {
                            filter_kind = "label"
                            filter_value = id.removePrefix("label:")
                        }
                        else -> {
                            filter_kind = "label"
                            filter_value = id
                        }
                    }
                    filter_name = name
                    selected_folder = id
                    scope.launch { drawer_state.close() }
                },
                on_navigate_alias = { id, name, routing_token ->
                    scope.launch { drawer_state.close() }
                    if (routing_token != null) {
                        filter_kind = "alias"
                        filter_value = routing_token
                        filter_name = name
                        alias_direction = org.astermail.android.mail.alias_direction_all
                        selected_folder = id
                    } else {
                        nav_controller.navigate(routes.search_for(org.astermail.android.ui.search.recipient_search_query(name)))
                    }
                },
                inbox_unread = if (categories_enabled && category_unread.isNotEmpty()) {
                    category_unread["primary"] ?: 0
                } else {
                    stats?.unread ?: 0
                },
                drafts_count = stats?.drafts ?: 0,
                scheduled_count = stats?.scheduled ?: 0,
                snoozed_count = stats?.snoozed ?: 0,
                spam_count = stats?.spam ?: 0,
                trash_count = stats?.trash ?: 0,
                categories_enabled = categories_enabled,
                category_entries = category_entries,
                category_unread = category_unread,
                selected_category = inbox_category,
                on_select_category = { cat ->
                    filter_kind = null
                    selected_folder = "inbox"
                    inbox_category = cat
                    scope.launch { drawer_state.close() }
                },
                storage_used_fraction = storage_fraction,
                storage_label = storage_label,
                user_email = user_email,
                display_email = display_email,
                api_folder_items = api_folders,
                api_label_items = api_labels,
                api_alias_items = api_aliases,
                folders_settled = settings_state.labels_loaded_types.let { "all" in it || "folder" in it },
                labels_settled = settings_state.tags_loaded && settings_state.labels_loaded_types.let {
                    "all" in it || "label" in it
                },
                aliases_settled = settings_state.aliases_settled,
                accounts = accounts_state.accounts,
                current_account_id = accounts_state.current_account_id,
                can_add_account = accounts_state.can_add_more,
                on_switch_account = { account ->
                    scope.launch { drawer_state.close() }
                    switch_to_stored_account(account, nav_controller, accounts_vm, settings_vm, mail_vm) {
                        selected_folder = "inbox"
                        filter_kind = null
                    }
                },
                on_add_account = {
                    open_add_account(nav_controller, accounts_state.can_add_more)
                    scope.launch { drawer_state.close() }
                },
                on_open_workspace_sheet = {
                    accounts_vm.refresh_with_profile()
                },
                on_create_label = { name, color, icon, parent_token ->
                    settings_vm.create_tag(name = name, color = color, icon = icon, parent_token = parent_token)
                },
                on_create_folder = { name, parent_token ->
                    settings_vm.create_folder(
                        name = name,
                        parent_token = parent_token,
                    )
                },
                on_sort_folders = if (org.astermail.android.folders.can_sort_folders_a_z(settings_state.labels)) {
                    { settings_vm.sort_folders_a_z() }
                } else {
                    null
                },
                folder_parent_options = folder_parent_options,
                folder_actions = org.astermail.android.ui.drawer.folder_menu_actions(
                    on_rename = { item, name -> settings_vm.rename_folder(item.label_id, name) },
                    on_recolor = { item, color -> settings_vm.recolor_folder(item.label_id, color) },
                    on_move_to = { item, parent_token -> settings_vm.set_folder_parent(item.label_id, parent_token) },
                    on_move_order = { item, direction -> settings_vm.move_folder(item.label_id, direction) },
                    on_toggle_mute = { item -> settings_vm.toggle_folder_notifications(item.id) },
                    on_set_lock = { item, password -> settings_vm.set_folder_lock(item.label_id, password) {} },
                    on_remove_lock = { item, password -> settings_vm.remove_folder_lock(item.label_id, password) {} },
                    on_lock_now = { item ->
                        if (item.id == active_folder_token) leave_protected_folder()
                        settings_vm.lock_folder(item.label_id)
                    },
                    on_lock_all = {
                        if (active_folder_token in org.astermail.android.folders.folder_lock_store.protected_tokens()) {
                            leave_protected_folder()
                        }
                        settings_vm.lock_all_folders()
                    },
                    on_delete = { item, password, totp_code, purge ->
                        settings_vm.delete_label(
                            label_id = item.label_id,
                            password = password,
                            totp_code = totp_code,
                            purge_contents = purge,
                        )
                    },
                ),
                label_actions = org.astermail.android.ui.drawer.label_menu_actions(
                    on_rename = { item, name ->
                        if (item.kind == "tag") settings_vm.rename_tag(item.api_id, name)
                        else settings_vm.rename_folder(item.api_id, name)
                    },
                    on_recolor = { item, color ->
                        if (item.kind == "tag") settings_vm.recolor_tag(item.api_id, color)
                        else settings_vm.recolor_folder(item.api_id, color)
                    },
                    on_set_icon = { item, icon ->
                        if (item.kind == "tag") settings_vm.set_tag_icon(item.api_id, icon)
                        else settings_vm.set_label_icon(item.api_id, icon)
                    },
                    on_move_order = { item, direction ->
                        if (item.kind == "tag") settings_vm.move_tag(item.api_id, direction)
                        else settings_vm.move_label_row(item.api_id, direction)
                    },
                    on_delete = { item ->
                        if (item.kind == "tag") settings_vm.delete_tag(item.api_id)
                        else settings_vm.delete_label(item.api_id)
                    },
                    on_move_to = { item, parent_token ->
                        if (item.kind == "tag") settings_vm.set_tag_parent(item.api_id, parent_token)
                    },
                ),
                label_parent_options = label_parent_options,
                label_blocked_parent_tokens = { item ->
                    tag_index.blocked_parent_tokens(item.id.removePrefix("tag:"))
                },
                on_manage_account = {
                    scope.launch { drawer_state.close() }
                    nav_controller.navigate(routes.settings_detail("profile"))
                },
                on_open_storage = {
                    scope.launch { drawer_state.close() }
                    nav_controller.navigate(routes.settings_detail("storage"))
                },
                on_logout_all = {
                    sign_out_all_accounts(nav_controller, accounts_vm, settings_vm)
                },
                plan_code = settings_state.subscription?.plan?.code,
                profile_storage_label = account_profile_storage_label(used_bytes, total_bytes),
                max_accounts = accounts_state.max_accounts,
                is_unlimited_accounts = accounts_state.is_unlimited,
                needs_sign_in = { account ->
                    account.id != accounts_state.current_account_id &&
                        !accounts_vm.has_stored_session(account.id)
                },
                on_logout = {
                    sign_out_current_account(nav_controller, accounts_vm, settings_vm, mail_vm) {
                        selected_folder = "inbox"
                        filter_kind = null
                    }
                },
                initial_more_collapsed = prefs?.sidebar_more_collapsed ?: false,
                initial_folders_collapsed = prefs?.sidebar_folders_collapsed ?: false,
                initial_labels_collapsed = prefs?.sidebar_labels_collapsed ?: false,
                initial_aliases_collapsed = prefs?.sidebar_aliases_collapsed ?: false,
                initial_categories_collapsed = prefs?.sidebar_categories_collapsed ?: false,
                initial_folders_show_all = prefs?.sidebar_folders_show_all ?: false,
                initial_aliases_show_all = prefs?.sidebar_aliases_show_all ?: false,
                preferences_loaded = prefs != null,
                totp_enabled = settings_state.security_status?.totp_enabled == true,
                purge_locked_folder_default = prefs?.purge_locked_folder_on_delete ?: false,
                on_sidebar_toggle = { key, value ->
                    settings_vm.update_sidebar_state(key, value)
                },
                sidebar_section_order = prefs?.sidebar_section_order
                    ?: org.astermail.android.api.preferences.SIDEBAR_SECTION_IDS,
                hidden_items = prefs?.sidebar_hidden_items.orEmpty(),
            )
            }
        },
    ) {
        val folder_key = when {
            effective_filter_kind != null -> "filter:$effective_filter_kind:$filter_value"
            effective_selected_folder == "subscriptions" -> "subscriptions"
            effective_selected_folder == "contacts" -> "contacts"
            else -> "inbox:$effective_selected_folder"
        }
        val exit_context = LocalContext.current
        val exit_hint = stringResource(R.string.tap_again_to_exit)
        var last_back_press by remember { mutableStateOf(0L) }
        var inbox_selection_active by remember { mutableStateOf(false) }
        BackHandler(enabled = drawer_state.isOpen) {
            scope.launch { drawer_state.close() }
        }
        BackHandler(enabled = !drawer_state.isOpen && !inbox_selection_active) {
            val now = android.os.SystemClock.elapsedRealtime()
            if (!org.astermail.android.settings.ExitConfirmPref.is_enabled(exit_context)) {
                exit_context.find_host_activity()?.finish()
            } else if (now - last_back_press in 1L..2000L) {
                exit_context.find_host_activity()?.finish()
            } else {
                last_back_press = now
                org.astermail.android.ui.common.app_toast.show(exit_hint)
            }
        }
        val saveable_state_holder = rememberSaveableStateHolder()
        var inbox_scroll_top_token by remember { mutableStateOf(0) }
        val folder_anim_duration = if (org.astermail.android.design.aster_reduce_motion()) 0 else nav_anim_duration_ms
        AnimatedContent(
            targetState = folder_key,
            modifier = Modifier
                .fillMaxSize()
                .edge_swipe_to_open(enabled = !drawer_state.isOpen) {
                    scope.launch { drawer_state.open() }
                },
            transitionSpec = {
                val from_depth = folder_key_depth(initialState)
                val to_depth = folder_key_depth(targetState)
                val transform = when {
                    folder_anim_duration == 0 ->
                        org.astermail.android.design.aster_crossfade(true)
                    to_depth > from_depth ->
                        nav_forward_enter(folder_anim_duration) togetherWith nav_forward_exit(folder_anim_duration)
                    to_depth < from_depth ->
                        nav_backward_enter(folder_anim_duration) togetherWith nav_backward_exit(folder_anim_duration)
                    else ->
                        fadeIn(animationSpec = tween(folder_switch_fade_ms, easing = folder_switch_in_easing)) togetherWith
                            fadeOut(animationSpec = tween(folder_switch_fade_ms, easing = folder_switch_out_easing))
                }
                transform using null
            },
            label = "folder_switch",
        ) { active_key ->
            val slot_frozen = remember(active_key) {
                folder_slot_values(
                    filter_kind = effective_filter_kind,
                    filter_value = filter_value,
                    filter_name = filter_name,
                    selected_folder = effective_selected_folder,
                    mail_folder = effective_mail_folder,
                )
            }
            val slot_live = active_key == folder_key
            val slot_filter_kind = if (slot_live) effective_filter_kind else slot_frozen.filter_kind
            val slot_filter_value = if (slot_live) filter_value else slot_frozen.filter_value
            val slot_filter_name = if (slot_live) filter_name else slot_frozen.filter_name
            val slot_selected_folder = if (slot_live) effective_selected_folder else slot_frozen.selected_folder
            val slot_mail_folder = if (slot_live) effective_mail_folder else slot_frozen.mail_folder
            saveable_state_holder.SaveableStateProvider(active_key) {
                when {
                    slot_filter_kind != null -> {
                        BackHandler(enabled = !drawer_state.isOpen && !inbox_selection_active) {
                            saveable_state_holder.removeState("inbox:inbox")
                            inbox_scroll_top_token += 1
                            filter_kind = null
                            filter_value = ""
                            filter_name = ""
                            selected_folder = "inbox"
                        }
                        val effective_folder = org.astermail.android.mail.mail_folder_for_filter(
                            slot_filter_kind,
                            slot_filter_value,
                            alias_direction,
                        )
                        InboxScreen(
                            on_open_drawer = { scope.launch { drawer_state.open() } },
                            on_open_search = {
                                nav_controller.navigate(
                                    routes.search_for_folder(
                                        effective_folder,
                                        slot_filter_kind,
                                        slot_filter_name,
                                        drawer_context.getString(R.string.folder_decrypt_failed),
                                    ),
                                )
                            },
                            on_compose = {
                                drawer_context.startActivity(
                                    ComposeActivity.intent_for(
                                        drawer_context,
                                        selected_alias = slot_filter_name.takeIf {
                                            slot_filter_kind == org.astermail.android.mail.filter_kind_alias
                                        },
                                    ),
                                )
                            },
                            on_compose_draft = { id -> drawer_context.startActivity(ComposeActivity.intent_for(drawer_context, mode = "draft", draft_id = id)) },
                            on_view_pending_send = { nav_controller.navigate(routes.pending_send_preview) },
                            on_open_email = { id -> open_mail_detail(nav_controller, id) },
                            on_open_settings = { nav_controller.navigate(routes.settings) },
                            on_open_account = {
                                accounts_vm.refresh_with_profile()
                                show_account_manager = true
                            },
                            stored_account = accounts_state.accounts.firstOrNull { it.id == accounts_state.current_account_id },
                            on_open_low_network = {
                                nav_controller.navigate(routes.settings_low_network)
                            },
                            on_open_upgrade = { nav_controller.navigate(routes.settings_detail("billing")) },
                            on_open_recovery_email = { nav_controller.navigate(routes.settings_detail("recovery_email")) },
                            on_open_import = { nav_controller.navigate(routes.settings_detail("import")) },
                            on_open_domains = { nav_controller.navigate(routes.settings_detail("domains")) },
                            on_open_storage = { nav_controller.navigate(routes.settings_detail("storage")) { launchSingleTop = true } },
                            on_open_external_accounts = { nav_controller.navigate(routes.settings_detail("external_accounts")) { launchSingleTop = true } },
                            on_open_storage_addons = {
                                nav_controller.navigate(routes.settings_detail("billing_addons")) { launchSingleTop = true }
                            },
                            current_folder = effective_folder,
                            display_title = slot_filter_name,
                            search_scoped_to_title = org.astermail.android.mail.search_filter_scope_query(
                                slot_filter_kind,
                                slot_filter_name,
                            ) != null,
                            alias_direction = if (slot_filter_kind == org.astermail.android.mail.filter_kind_alias) alias_direction else null,
                            on_alias_direction_change = { alias_direction = it },
                            on_folder_change = { id ->
                                filter_kind = null
                                selected_folder = id
                            },
                            custom_folders = quick_custom_folders,
                            on_custom_folder_change = { id, name -> request_custom_folder(id, name) },
                            folder_unread_counts = quick_folder_counts,
                            on_customize_toolbar = { nav_controller.navigate(routes.settings_detail("customize_toolbar")) },
                            select_back_enabled = !drawer_state.isOpen,
                            on_select_mode_change = { active -> inbox_selection_active = active },
                            on_list_settled = { ids -> org.astermail.android.mail.message_preloader.request_visible(ids) },
                        )
                    }
                    slot_selected_folder == "subscriptions" -> {
                        BackHandler(enabled = !drawer_state.isOpen) {
                            saveable_state_holder.removeState("inbox:inbox")
                            inbox_scroll_top_token += 1
                            selected_folder = "inbox"
                        }
                        MailingListsScreen(
                            on_open_drawer = { scope.launch { drawer_state.open() } },
                            on_open_account = {
                                accounts_vm.refresh_with_profile()
                                show_account_manager = true
                            },
                            stored_account = accounts_state.accounts.firstOrNull { it.id == accounts_state.current_account_id },
                            on_open_search = { nav_controller.navigate(routes.search) },
                            on_search_sender = { sender ->
                                org.astermail.android.ui.search.build_sender_mail_query(sender)?.let { query ->
                                    nav_controller.navigate(routes.search_for(query))
                                }
                            },
                        )
                    }
                    slot_selected_folder == "contacts" -> {
                        BackHandler(enabled = !drawer_state.isOpen) {
                            saveable_state_holder.removeState("inbox:inbox")
                            inbox_scroll_top_token += 1
                            selected_folder = "inbox"
                        }
                        ContactsScreen(
                            on_open_drawer = { scope.launch { drawer_state.open() } },
                            search_back_enabled = !drawer_state.isOpen,
                            on_open_account = {
                                accounts_vm.refresh_with_profile()
                                show_account_manager = true
                            },
                            stored_account = accounts_state.accounts.firstOrNull { it.id == accounts_state.current_account_id },
                            on_open_contact = { id -> nav_controller.navigate(routes.contact_detail_for(id)) },
                            on_open_group = { id -> navigate_once(nav_controller, routes.contact_group_for(id)) },
                            on_create_contact = { nav_controller.navigate(routes.contact_edit_new) },
                            on_compose_to = { addresses ->
                                drawer_context.startActivity(
                                    ComposeActivity.intent_for(
                                        drawer_context,
                                        prefill_to = addresses.joinToString(","),
                                    ),
                                )
                            },
                        )
                    }
                    else -> {
                        BackHandler(
                            enabled = !drawer_state.isOpen && !inbox_selection_active &&
                                (slot_selected_folder != "inbox" || inbox_category != "primary"),
                        ) {
                            saveable_state_holder.removeState("inbox:inbox")
                            inbox_scroll_top_token += 1
                            inbox_category = "primary"
                            selected_folder = "inbox"
                        }
                        InboxScreen(
                            on_open_drawer = { scope.launch { drawer_state.open() } },
                            on_open_search = { nav_controller.navigate(routes.search_for_folder(slot_selected_folder)) },
                            on_compose = { drawer_context.startActivity(ComposeActivity.intent_for(drawer_context)) },
                            on_compose_draft = { id -> drawer_context.startActivity(ComposeActivity.intent_for(drawer_context, mode = "draft", draft_id = id)) },
                            on_view_pending_send = { nav_controller.navigate(routes.pending_send_preview) },
                            on_open_email = { id ->
                                if (slot_selected_folder == "drafts") {
                                    drawer_context.startActivity(ComposeActivity.intent_for(drawer_context, mode = "draft", draft_id = id))
                                } else {
                                    open_mail_detail(nav_controller, id)
                                }
                            },
                            on_open_settings = { nav_controller.navigate(routes.settings) },
                            on_open_account = {
                                accounts_vm.refresh_with_profile()
                                show_account_manager = true
                            },
                            stored_account = accounts_state.accounts.firstOrNull { it.id == accounts_state.current_account_id },
                            on_open_low_network = {
                                nav_controller.navigate(routes.settings_low_network)
                            },
                            on_open_upgrade = { nav_controller.navigate(routes.settings_detail("billing")) },
                            on_open_recovery_email = { nav_controller.navigate(routes.settings_detail("recovery_email")) },
                            on_open_import = { nav_controller.navigate(routes.settings_detail("import")) },
                            on_open_domains = { nav_controller.navigate(routes.settings_detail("domains")) },
                            on_open_storage = { nav_controller.navigate(routes.settings_detail("storage")) { launchSingleTop = true } },
                            on_open_external_accounts = { nav_controller.navigate(routes.settings_detail("external_accounts")) { launchSingleTop = true } },
                            on_open_storage_addons = {
                                nav_controller.navigate(routes.settings_detail("billing_addons")) { launchSingleTop = true }
                            },
                            current_folder = slot_mail_folder,
                            inbox_category = inbox_category,
                            display_title = null,
                            on_folder_change = { selected_folder = it },
                            custom_folders = quick_custom_folders,
                            on_custom_folder_change = { id, name -> request_custom_folder(id, name) },
                            folder_unread_counts = quick_folder_counts,
                            on_customize_toolbar = { nav_controller.navigate(routes.settings_detail("customize_toolbar")) },
                            scroll_top_token = if (slot_selected_folder == "inbox") inbox_scroll_top_token else 0,
                            all_mail_include_spam = all_mail_include_spam,
                            all_mail_include_trash = all_mail_include_trash,
                            on_all_mail_scope_change = { spam, trash ->
                                all_mail_include_spam = spam
                                all_mail_include_trash = trash
                            },
                            category_unread = category_unread,
                            on_select_category = { cat ->
                                filter_kind = null
                                selected_folder = "inbox"
                                inbox_category = cat
                            },
                            select_back_enabled = !drawer_state.isOpen,
                            on_select_mode_change = { active -> inbox_selection_active = active },
                            on_list_settled = { ids -> org.astermail.android.mail.message_preloader.request_visible(ids) },
                        )
                    }
                }
            }
        }
    }

    pending_unlock_folder?.let { (token, name) ->
        val label = settings_state.labels.firstOrNull { it.label_token == token }
        val unlock_failed_text = stringResource(R.string.folder_unlock_failed)
        org.astermail.android.ui.folders.folder_unlock_dialog(
            folder_name = name,
            verifying = unlock_verifying,
            error_text = unlock_error,
            on_dismiss = {
                if (!unlock_verifying) {
                    pending_unlock_folder = null
                    unlock_error = null
                }
            },
            on_submit = { password ->
                val label_id = label?.id
                if (label_id == null) {
                    unlock_error = unlock_failed_text
                } else {
                    unlock_verifying = true
                    unlock_error = null
                    settings_vm.unlock_folder(label_id, password) { ok ->
                        unlock_verifying = false
                        if (ok) {
                            pending_unlock_folder = null
                            open_custom_folder(token, name)
                        } else {
                            unlock_error = unlock_failed_text
                        }
                    }
                }
            },
        )
    }

    val first_run_setup_pending by theme_vm_inbox.first_run_setup_pending.collectAsStateWithLifecycle()
    val first_run_plan_pending by theme_vm_inbox.first_run_plan_pending.collectAsStateWithLifecycle()
    val first_run_at by theme_vm_inbox.first_run_at.collectAsStateWithLifecycle()
    val recovery_snooze_until by theme_vm_inbox.recovery_snooze_until.collectAsStateWithLifecycle()
    val phrase_prompt_snooze_until by theme_vm_inbox.phrase_prompt_snooze_until
        .collectAsStateWithLifecycle()
    val codes_refresh_snooze_until by theme_vm_inbox.codes_refresh_snooze_until
        .collectAsStateWithLifecycle()

    var recovery_prompt_dismissed by androidx.compose.runtime.saveable.rememberSaveable {
        mutableStateOf(false)
    }
    var plan_prompt_dismissed by androidx.compose.runtime.saveable.rememberSaveable {
        mutableStateOf(false)
    }
    var phrase_prompt_dismissed by androidx.compose.runtime.saveable.rememberSaveable {
        mutableStateOf(false)
    }
    var codes_refresh_prompt_dismissed by androidx.compose.runtime.saveable.rememberSaveable {
        mutableStateOf(false)
    }

    val now_ms = System.currentTimeMillis()
    val first_run_age_ms = if (first_run_at > 0L) now_ms - first_run_at else -1L
    val recovery_prompt_due = first_run_age_ms >=
        org.astermail.android.ui.onboarding.first_run_recovery_delay_ms
    val plan_prompt_due = first_run_age_ms >=
        org.astermail.android.ui.onboarding.first_run_plan_delay_ms

    androidx.compose.runtime.LaunchedEffect(Unit) {
        settings_vm.refresh_recovery_methods()
    }

    androidx.compose.runtime.LaunchedEffect(accounts_state.current_account_id) {
        settings_vm.refresh_recovery_codes_outdated()
    }

    androidx.compose.runtime.LaunchedEffect(recovery_prompt_due, accounts_state.current_account_id) {
        if (recovery_prompt_due) settings_vm.refresh_recovery_email_presence()
    }

    val recovery_methods = settings_state.recovery_methods
    val needs_codes = recovery_methods != null && !recovery_methods.has_codes
    val codes_running_low = recovery_methods != null &&
        recovery_methods.has_codes &&
        recovery_methods.codes_remaining <= 3L

    val show_codes_refresh_prompt = !first_run_setup_pending &&
        settings_state.recovery_codes_outdated &&
        !codes_refresh_prompt_dismissed &&
        now_ms >= codes_refresh_snooze_until

    val show_phrase_prompt = !show_codes_refresh_prompt &&
        recovery_methods != null &&
        recovery_methods.has_phrase &&
        !recovery_methods.has_codes &&
        !first_run_setup_pending &&
        !phrase_prompt_dismissed &&
        now_ms >= phrase_prompt_snooze_until

    val show_recovery_prompt = !show_phrase_prompt &&
        recovery_prompt_due &&
        !first_run_setup_pending &&
        !recovery_prompt_dismissed &&
        now_ms >= recovery_snooze_until &&
        (
            needs_codes ||
                codes_running_low ||
                (settings_state.recovery_email_known && !settings_state.recovery_email_set)
            )

    val recovery_prompt_wants_codes = needs_codes || codes_running_low

    val inbox_plan_code = plan_state_inbox.limits?.plan_code
    androidx.compose.runtime.LaunchedEffect(inbox_plan_code, first_run_plan_pending) {
        if (first_run_plan_pending && inbox_plan_code != null && inbox_plan_code != "free") {
            theme_vm_inbox.clear_first_run_plan()
        }
    }

    val show_plan_prompt = plan_prompt_due &&
        inbox_plan_code == "free" &&
        first_run_plan_pending &&
        !first_run_setup_pending &&
        !plan_prompt_dismissed &&
        !show_recovery_prompt &&
        !show_phrase_prompt &&
        !show_codes_refresh_prompt

    if (show_codes_refresh_prompt || show_recovery_prompt || show_phrase_prompt || show_plan_prompt) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .systemBarsPadding()
                .padding(horizontal = AsterSpacing.lg, vertical = AsterSpacing.xxl),
            contentAlignment = Alignment.BottomCenter,
        ) {
            if (show_codes_refresh_prompt) {
                org.astermail.android.ui.onboarding.RecoveryCodesRefreshCard(
                    visible = true,
                    on_get_codes = {
                        codes_refresh_prompt_dismissed = true
                        nav_controller.navigate(routes.settings_detail("recovery_codes"))
                    },
                    on_later = {
                        codes_refresh_prompt_dismissed = true
                        theme_vm_inbox.snooze_codes_refresh_prompt(
                            org.astermail.android.ui.onboarding.codes_refresh_snooze_ms,
                        )
                    },
                )
            } else if (show_phrase_prompt) {
                org.astermail.android.ui.onboarding.PhraseMigrationCard(
                    visible = true,
                    on_get_codes = {
                        phrase_prompt_dismissed = true
                        nav_controller.navigate(routes.settings_detail("recovery_codes"))
                    },
                    on_later = {
                        phrase_prompt_dismissed = true
                        theme_vm_inbox.snooze_phrase_prompt(
                            org.astermail.android.ui.onboarding.phrase_prompt_snooze_ms,
                        )
                    },
                )
            } else if (show_recovery_prompt && recovery_prompt_wants_codes) {
                org.astermail.android.ui.onboarding.RecoveryCodesReminderCard(
                    visible = true,
                    on_get_codes = {
                        recovery_prompt_dismissed = true
                        nav_controller.navigate(routes.settings_detail("recovery_codes"))
                    },
                    on_later = {
                        recovery_prompt_dismissed = true
                        theme_vm_inbox.snooze_recovery(
                            org.astermail.android.ui.onboarding.first_run_recovery_snooze_ms,
                        )
                    },
                )
            } else if (show_recovery_prompt) {
                org.astermail.android.ui.onboarding.RecoveryReminderCard(
                    visible = true,
                    on_add_recovery = {
                        recovery_prompt_dismissed = true
                        nav_controller.navigate(routes.settings_detail("recovery_email"))
                    },
                    on_later = {
                        recovery_prompt_dismissed = true
                        theme_vm_inbox.snooze_recovery(
                            org.astermail.android.ui.onboarding.first_run_recovery_snooze_ms,
                        )
                    },
                )
            } else {
                org.astermail.android.ui.onboarding.PlanPromptCard(
                    visible = true,
                    on_see_plans = {
                        plan_prompt_dismissed = true
                        theme_vm_inbox.clear_first_run_plan()
                        nav_controller.navigate(routes.settings_detail("billing"))
                    },
                    on_dismiss = {
                        plan_prompt_dismissed = true
                        theme_vm_inbox.clear_first_run_plan()
                    },
                )
            }
        }
    }

    if (first_run_setup_pending) {
        org.astermail.android.ui.onboarding.FirstRunSetupSheet(
            email = user_email,
            on_import = {
                theme_vm_inbox.clear_first_run_setup()
                nav_controller.navigate(routes.settings_detail("import"))
            },
            on_dismiss = { theme_vm_inbox.clear_first_run_setup() },
        )
    }
}

private const val folder_switch_fade_ms = 200

private val folder_switch_out_easing = org.astermail.android.design.aster_threshold_easing(
    org.astermail.android.design.AsterEasing.emphasized,
    0f,
    org.astermail.android.design.AsterMotionSpec.fade_through_threshold,
)

private val folder_switch_in_easing = org.astermail.android.design.aster_threshold_easing(
    org.astermail.android.design.AsterEasing.emphasized,
    org.astermail.android.design.AsterMotionSpec.fade_through_threshold,
    1f,
)

private fun is_search_route(route: String?): Boolean =
    route == routes.search || route == routes.search_with_query

private fun is_detail_swipe(entry: androidx.navigation.NavBackStackEntry): Boolean =
    entry.destination.route == routes.mail_detail && (entry.arguments?.getInt("swipe") ?: 0) != 0

private fun pop_once(nav_controller: NavHostController) {
    val entry = nav_controller.currentBackStackEntry ?: return
    if (!entry.lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED)) return
    nav_controller.popBackStack()
}

private fun pop_entry(nav_controller: NavHostController, entry: androidx.navigation.NavBackStackEntry) {
    if (nav_controller.currentBackStackEntry?.id != entry.id) return
    nav_controller.popBackStack()
}

private fun navigate_once(nav_controller: NavHostController, route: String) {
    val entry = nav_controller.currentBackStackEntry ?: return
    if (!entry.lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED)) return
    nav_controller.navigate(route) { launchSingleTop = true }
}

private fun open_mail_detail(nav_controller: NavHostController, email_id: String) {
    val entry = nav_controller.currentBackStackEntry ?: return
    if (!entry.lifecycle.currentState.isAtLeast(Lifecycle.State.STARTED)) return
    if (entry.destination.route == routes.mail_detail) return
    nav_controller.navigate(routes.mail_detail_for(email_id)) { launchSingleTop = true }
}

private data class account_storage_usage(
    val used_bytes: Long,
    val total_bytes: Long,
    val fraction: Float,
)

private fun account_storage_usage_for(
    storage: org.astermail.android.api.settings.StorageOverview?,
    stats: org.astermail.android.api.mail.MailUserStatsResponse?,
): account_storage_usage {
    val used_bytes = when {
        storage != null && storage.used_bytes > 0 -> storage.used_bytes
        stats != null && stats.storage_used_bytes > 0 -> stats.storage_used_bytes
        else -> 0L
    }
    val total_bytes = when {
        storage != null && storage.total_bytes > 0 -> storage.total_bytes
        stats != null && stats.storage_total_bytes > 0 -> stats.storage_total_bytes
        else -> 0L
    }
    val fraction = when {
        total_bytes > 0 -> (used_bytes.toFloat() / total_bytes).coerceIn(0f, 1f)
        storage != null && storage.percentage_used > 0 -> (storage.percentage_used / 100.0).toFloat().coerceIn(0f, 1f)
        else -> 0f
    }
    return account_storage_usage(used_bytes, total_bytes, fraction)
}

@Composable
private fun account_profile_storage_label(used_bytes: Long, total_bytes: Long): String =
    if (total_bytes > 0) {
        stringResource(
            R.string.profile_menu_storage_of_used,
            format_storage_bytes(used_bytes),
            format_storage_bytes(total_bytes),
        )
    } else {
        ""
    }

private fun account_user_email_for(
    settings_state: org.astermail.android.settings.SettingsUiState,
    accounts_state: org.astermail.android.accounts.AccountsUiState,
): String =
    settings_state.user?.email
        ?: accounts_state.accounts.firstOrNull { it.id == accounts_state.current_account_id }?.email
        ?: accounts_state.accounts.firstOrNull()?.email
        ?: ""

private fun switch_to_stored_account(
    account: org.astermail.android.storage.StoredAccount,
    nav_controller: NavHostController,
    accounts_vm: org.astermail.android.accounts.AccountsViewModel,
    settings_vm: org.astermail.android.settings.SettingsViewModel,
    mail_vm: org.astermail.android.mail.MailViewModel?,
    on_restored: () -> Unit,
) {
    if (!accounts_vm.has_stored_session(account.id)) {
        nav_controller.navigate(routes.sign_in_for(account.email))
        return
    }
    mail_vm?.reset_for_account_switch()
    settings_vm.reset_for_account_switch()
    accounts_vm.switch_account(account.id) { restored ->
        if (restored) {
            settings_vm.load_preferences(force = true)
            settings_vm.load_signature()
            on_restored()
            nav_controller.navigate(routes.inbox) {
                popUpTo(0) { inclusive = true }
            }
        } else {
            nav_controller.navigate(routes.sign_in_for(account.email))
        }
    }
}

private fun open_add_account(nav_controller: NavHostController, can_add_more: Boolean) {
    if (can_add_more) {
        nav_controller.navigate(routes.sign_in_for(""))
    } else {
        nav_controller.navigate(routes.settings_detail("billing"))
    }
}

private fun sign_out_current_account(
    nav_controller: NavHostController,
    accounts_vm: org.astermail.android.accounts.AccountsViewModel,
    settings_vm: org.astermail.android.settings.SettingsViewModel,
    mail_vm: org.astermail.android.mail.MailViewModel?,
    on_switched: () -> Unit,
) {
    settings_vm.logout { switched_account ->
        accounts_vm.refresh()
        if (switched_account) {
            mail_vm?.reset_for_account_switch()
            settings_vm.reset_for_account_switch()
            settings_vm.load_preferences(force = true)
            settings_vm.load_signature()
            on_switched()
            nav_controller.navigate(routes.inbox) {
                popUpTo(0) { inclusive = true }
            }
        } else {
            val next = accounts_vm.state.value.accounts.firstOrNull()
            val target = if (next != null) routes.sign_in_for(next.email) else routes.welcome
            nav_controller.navigate(target) {
                popUpTo(0) { inclusive = true }
            }
        }
    }
}

private fun sign_out_all_accounts(
    nav_controller: NavHostController,
    accounts_vm: org.astermail.android.accounts.AccountsViewModel,
    settings_vm: org.astermail.android.settings.SettingsViewModel,
) {
    settings_vm.logout_all {
        accounts_vm.refresh()
        nav_controller.navigate(routes.welcome) {
            popUpTo(0) { inclusive = true }
        }
    }
}

private fun format_storage_bytes(bytes: Long): String {
    if (bytes < 0) return "0 B"
    if (bytes < 1024) return "$bytes B"
    val kb = bytes / 1024.0
    if (kb < 1024) return format_unit(kb, "KB")
    val mb = kb / 1024.0
    if (mb < 1024) return format_unit(mb, "MB")
    val gb = mb / 1024.0
    if (gb < 1024) return format_unit(gb, "GB")
    val tb = gb / 1024.0
    return format_unit(tb, "TB")
}

private fun format_unit(value: Double, suffix: String): String {
    val rounded = Math.round(value * 10.0) / 10.0
    val text = if (rounded == rounded.toLong().toDouble()) {
        rounded.toLong().toString()
    } else {
        "%.1f".format(java.util.Locale.getDefault(), rounded)
    }
    return "$text $suffix"
}

private fun folder_key_depth(key: String): Int = when {
    key.startsWith("filter:") -> 1
    key == "subscriptions" -> 1
    key == "contacts" -> 1
    else -> 0
}

@androidx.compose.runtime.Composable
private fun window_inset_probe(on_applied: () -> Unit) {
    val top = WindowInsets.systemBars
        .asPaddingValues()
        .calculateTopPadding()
    if (top > androidx.compose.ui.unit.Dp(0f)) on_applied()
}

private data class folder_slot_values(
    val filter_kind: String?,
    val filter_value: String,
    val filter_name: String,
    val selected_folder: String,
    val mail_folder: String,
)
