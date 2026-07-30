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

import compose.icons.TablerIcons
import compose.icons.tablericons.*

import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.saveable.rememberSaveableStateHolder
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.ProcessLifecycleOwner
import org.astermail.android.security.AppLockViewModel
import org.astermail.android.security.LockdownStore
import org.astermail.android.ui.common.nav_anim_duration_ms
import org.astermail.android.ui.common.nav_anim_collapse_ms
import org.astermail.android.ui.common.nav_anim_expand_ms
import org.astermail.android.ui.security.AppLockScreen
import androidx.compose.foundation.layout.fillMaxWidth
import org.astermail.android.ui.common.nav_backward_enter
import org.astermail.android.ui.common.nav_expand_enter
import org.astermail.android.ui.common.nav_expand_exit
import org.astermail.android.ui.common.nav_backward_exit
import org.astermail.android.ui.common.nav_forward_enter
import org.astermail.android.ui.common.nav_forward_exit
import org.astermail.android.ui.common.nav_sheet_enter
import org.astermail.android.ui.common.nav_sheet_exit
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
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
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
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import dagger.hilt.android.AndroidEntryPoint
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.launch
import org.astermail.android.auth.AuthGateViewModel
import org.astermail.android.design.AsterMaterial
import org.astermail.android.design.AsterTheme
import org.astermail.android.design.AsterThemeMode
import org.astermail.android.design.ColorThemeId
import org.astermail.android.design.parse_hex_color_safe
import androidx.compose.ui.res.stringResource
import org.astermail.android.R
import org.astermail.android.storage.ThemeMode
import org.astermail.android.ui.auth.ForgotPasswordScreen
import org.astermail.android.ui.auth.OnboardingScreen
import org.astermail.android.ui.auth.RecoveryKeyScreen
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
import org.astermail.android.ui.settings.detail.DeveloperScreen
import org.astermail.android.ui.settings.detail.FamilyScreen
import org.astermail.android.ui.settings.detail.KidsReservedScreen
import org.astermail.android.ui.settings.detail.FoldersScreen
import org.astermail.android.ui.settings.detail.GhostAliasesScreen
import org.astermail.android.ui.settings.detail.LabelsScreen
import org.astermail.android.ui.settings.detail.LanguageScreen
import org.astermail.android.ui.settings.detail.PrivacyScreen
import org.astermail.android.ui.settings.detail.ReferralScreen
import org.astermail.android.ui.settings.detail.TrustedDevicesScreen
import org.astermail.android.ui.settings.detail.AliasesScreen
import org.astermail.android.ui.settings.detail.AppearanceScreen
import org.astermail.android.ui.settings.detail.AutoForwardScreen
import org.astermail.android.ui.settings.detail.BehaviorScreen
import org.astermail.android.ui.settings.detail.SwipeActionsScreen
import org.astermail.android.ui.settings.detail.CustomizeToolbarScreen
import org.astermail.android.ui.settings.detail.BillingScreen
import org.astermail.android.ui.settings.detail.SubscriptionsScreen
import org.astermail.android.ui.settings.detail.FeaturesScreen
import org.astermail.android.ui.settings.detail.AllowListScreen
import org.astermail.android.ui.settings.detail.BlockedSendersScreen
import org.astermail.android.ui.settings.detail.ChangePasswordScreen
import org.astermail.android.ui.settings.detail.DeleteAccountScreen
import org.astermail.android.ui.settings.detail.DiagnosticsScreen
import org.astermail.android.ui.settings.detail.EncryptionScreen
import org.astermail.android.ui.settings.detail.ExportScreen
import org.astermail.android.ui.settings.detail.ExternalAccountsScreen
import org.astermail.android.ui.settings.detail.FeedbackScreen
import org.astermail.android.ui.settings.detail.ImportScreen
import org.astermail.android.ui.settings.detail.NotificationsScreen
import org.astermail.android.ui.settings.detail.ProfileScreen
import org.astermail.android.ui.settings.detail.RecoveryEmailScreen
import org.astermail.android.ui.settings.detail.RecoveryKeyViewScreen
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
class MainActivity : androidx.fragment.app.FragmentActivity() {

    companion object {
        const val EXTRA_OPEN_EMAIL_ID = "open_email_id"
        const val EXTRA_OPEN_SESSIONS = "open_sessions"
        val pending_open_email_id = mutableStateOf<String?>(null)
        val pending_open_sessions = mutableStateOf(false)
        val pending_reveal_email_id = mutableStateOf<String?>(null)
        val pending_reveal_folder_tokens = mutableStateOf<List<String>?>(null)
        val pending_share = mutableStateOf<org.astermail.android.share.SharePayload?>(null)
        val pending_share_token = mutableStateOf("")
    }

    @javax.inject.Inject
    lateinit var app_lock_store: org.astermail.android.security.AppLockStore

    private val lockdown_listener =
        android.content.SharedPreferences.OnSharedPreferenceChangeListener { _, _ ->
            runOnUiThread { enforce_secure_flag() }
        }

    override fun onCreate(saved_instance_state: Bundle?) {
        super.onCreate(saved_instance_state)
        enforce_secure_flag()
        LockdownStore.register_listener(applicationContext, lockdown_listener)
        lifecycleScope.launch {
            app_lock_store.config_version.collect { enforce_secure_flag() }
        }
        consume_open_email_extra(intent)
        consume_share_intent(intent)
        apply_boot_background()
        enableEdgeToEdge()
        setContent {
            AsterRoot()
        }
    }

    private fun apply_boot_background() {
        val argb = org.astermail.android.ui.common.theme_boot_background_argb(this)
        window.setBackgroundDrawable(android.graphics.drawable.ColorDrawable(argb))
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            runCatching {
                splashScreen.setSplashScreenTheme(
                    org.astermail.android.ui.common.theme_boot_splash_style(this),
                )
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        consume_open_email_extra(intent)
        consume_share_intent(intent)
    }

    private fun consume_share_intent(intent: Intent?) {
        val payload = org.astermail.android.share.parse_share_intent(intent) ?: return
        intent?.action = null
        pending_share_token.value = android.os.SystemClock.elapsedRealtimeNanos().toString()
        pending_share.value = payload
    }

    private fun consume_open_email_extra(intent: Intent?) {
        if (intent?.getBooleanExtra(EXTRA_OPEN_SESSIONS, false) == true) {
            intent.removeExtra(EXTRA_OPEN_SESSIONS)
            pending_open_sessions.value = true
        }
        val email_id = intent?.getStringExtra(EXTRA_OPEN_EMAIL_ID)?.takeIf { it.isNotBlank() } ?: return
        intent.removeExtra(EXTRA_OPEN_EMAIL_ID)
        pending_open_email_id.value = email_id
        pending_reveal_email_id.value = email_id
        pending_reveal_folder_tokens.value = null
    }

    override fun onResume() {
        super.onResume()
        enforce_secure_flag()
    }

    override fun onPause() {
        enforce_secure_flag()
        apply_boot_background()
        super.onPause()
    }

    override fun onDestroy() {
        LockdownStore.unregister_listener(applicationContext, lockdown_listener)
        super.onDestroy()
    }

    private fun enforce_secure_flag() {
        val app_lock_configured = runCatching { app_lock_store.is_configured() }.getOrDefault(true)
        if (LockdownStore.is_enabled(applicationContext) || app_lock_configured) {
            window.setFlags(
                android.view.WindowManager.LayoutParams.FLAG_SECURE,
                android.view.WindowManager.LayoutParams.FLAG_SECURE,
            )
        } else {
            window.clearFlags(android.view.WindowManager.LayoutParams.FLAG_SECURE)
        }
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

@Composable
private fun AsterRoot() {
    org.astermail.android.ui.common.aster_theme_root {
        AsterNavHost()
    }
}

private object routes {
    const val onboarding = "onboarding"
    const val welcome = "welcome"
    const val sign_in = "sign_in"
    const val sign_in_with_email = "sign_in?email={email}"
    const val register = "register"

    fun sign_in_for(email: String): String {
        val encoded = java.net.URLEncoder.encode(email, "UTF-8")
        return "sign_in?email=$encoded"
    }
    const val forgot_password = "forgot_password"
    const val recovery_key = "recovery_key/{mnemonic}"
    const val inbox = "inbox"

    fun recovery_key_for(mnemonic: String): String {
        val encoded = java.net.URLEncoder.encode(mnemonic, "UTF-8")
        return "recovery_key/$encoded"
    }
    const val mail_detail = "mail_detail/{email_id}"
    const val folder_filter = "folder/{folder_id}/{folder_name}"
    const val label_filter = "label/{label_id}/{label_name}"
    const val alias_filter = "alias/{alias_id}/{alias_name}"

    fun folder_filter_for(folder_id: String, folder_name: String): String {
        val id = java.net.URLEncoder.encode(folder_id, "UTF-8")
        val name = java.net.URLEncoder.encode(folder_name, "UTF-8")
        return "folder/$id/$name"
    }
    fun label_filter_for(label_id: String, label_name: String): String {
        val id = java.net.URLEncoder.encode(label_id, "UTF-8")
        val name = java.net.URLEncoder.encode(label_name, "UTF-8")
        return "label/$id/$name"
    }
    fun alias_filter_for(alias_id: String, alias_name: String): String {
        val id = java.net.URLEncoder.encode(alias_id, "UTF-8")
        val name = java.net.URLEncoder.encode(alias_name, "UTF-8")
        return "alias/$id/$name"
    }
    const val compose = "compose?reply_to={reply_to}&mode={mode}&draft_id={draft_id}&to={to}&thread_ghost={thread_ghost}&share={share}"
    const val search = "search"
    const val search_with_query = "search?q={q}"
    fun search_for(query: String): String {
        return "search?q=" + android.net.Uri.encode(query)
    }
    fun search_for_folder(folder: String): String {
        val scope = when (folder) {
            "trash" -> "in:trash"
            "archive" -> "in:archive"
            "spam" -> "in:spam"
            "starred" -> "is:starred"
            else -> null
        }
        return if (scope == null) search else search_for(scope)
    }

    fun compose_new(to: String = ""): String {
        val encoded_to = if (to.isNotBlank()) java.net.URLEncoder.encode(to, "UTF-8") else ""
        return "compose?reply_to=&mode=&draft_id=&to=$encoded_to&thread_ghost="
    }
    fun compose_reply(msg_id: String, mode: String, thread_ghost: String? = null): String {
        val encoded_msg = java.net.URLEncoder.encode(msg_id, "UTF-8")
        val encoded_mode = java.net.URLEncoder.encode(mode, "UTF-8")
        val encoded_ghost = if (!thread_ghost.isNullOrBlank()) java.net.URLEncoder.encode(thread_ghost, "UTF-8") else ""
        return "compose?reply_to=$encoded_msg&mode=$encoded_mode&draft_id=&to=&thread_ghost=$encoded_ghost"
    }
    fun compose_share(token: String): String {
        return "compose?reply_to=&mode=&draft_id=&to=&thread_ghost=&share=$token"
    }
    fun compose_draft(draft_id: String): String {
        val encoded = java.net.URLEncoder.encode(draft_id, "UTF-8")
        return "compose?reply_to=&mode=draft&draft_id=$encoded&to=&thread_ghost="
    }
    const val pending_send_preview = "pending_send_preview"
    const val settings = "settings"
    const val contacts = "contacts"
    const val mailing_lists = "mailing_lists"
    const val contact_detail = "contact_detail/{contact_id}"
    const val contact_edit_new = "contact_edit"
    const val contact_edit = "contact_edit/{contact_id}"

    fun mail_detail_for(email_id: String) = "mail_detail/" + java.net.URLEncoder.encode(email_id, "UTF-8")
    fun settings_detail(id: String) = "settings_$id"
    fun contact_detail_for(contact_id: String) = "contact_detail/$contact_id"
    fun contact_edit_for(contact_id: String) = "contact_edit/$contact_id"
}


@Composable
private fun AsterNavHost() {
    val auth_gate: AuthGateViewModel = hiltViewModel()
    val theme_vm: ThemeViewModel = hiltViewModel()
    val lock_vm: AppLockViewModel = hiltViewModel()
    val is_ready by auth_gate.is_ready.collectAsStateWithLifecycle()
    val is_signed_in_state by auth_gate.is_signed_in.collectAsStateWithLifecycle()
    val is_locked by lock_vm.store.is_locked.collectAsStateWithLifecycle()

    request_notification_permission(should_request = is_signed_in_state && !is_locked)

    val nav_scope = rememberCoroutineScope()

    val process_owner = ProcessLifecycleOwner.get()
    androidx.compose.runtime.DisposableEffect(process_owner) {
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_STOP -> lock_vm.store.lock()
                Lifecycle.Event.ON_START -> lock_vm.store.check_on_foreground()
                else -> {}
            }
        }
        process_owner.lifecycle.addObserver(observer)
        onDispose { process_owner.lifecycle.removeObserver(observer) }
    }

    if (!is_ready) {
        androidx.compose.foundation.layout.Column(
            modifier = Modifier
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
            androidx.compose.material3.LinearProgressIndicator(
                color = AsterMaterial.colors.accent_blue,
                trackColor = AsterMaterial.colors.border_secondary,
                modifier = Modifier
                    .width(160.dp)
                    .height(3.dp)
                    .clip(androidx.compose.foundation.shape.RoundedCornerShape(2.dp)),
            )
        }
        return
    }

    val start = remember(is_ready) {
        when {
            is_signed_in_state -> routes.inbox
            !theme_vm.onboarding_seen.value -> routes.onboarding
            else -> routes.welcome
        }
    }
    val nav_controller = rememberNavController()
    val context = LocalContext.current
    val a11y = local_accessibility.current
    val nav_duration = if (a11y.reduce_motion) 0 else nav_anim_duration_ms

    val pending_open_email = MainActivity.pending_open_email_id.value
    androidx.compose.runtime.LaunchedEffect(pending_open_email, is_signed_in_state, is_locked) {
        if (pending_open_email.isNullOrBlank() || !is_signed_in_state || is_locked) return@LaunchedEffect
        MainActivity.pending_open_email_id.value = null
        nav_controller.navigate(routes.mail_detail_for(pending_open_email)) {
            launchSingleTop = true
        }
    }

    val pending_share = MainActivity.pending_share.value
    val pending_share_token = MainActivity.pending_share_token.value
    androidx.compose.runtime.LaunchedEffect(pending_share_token, is_signed_in_state, is_locked) {
        if (pending_share == null || !is_signed_in_state || is_locked) return@LaunchedEffect
        nav_controller.navigate(routes.compose_share(pending_share_token)) {
            popUpTo(routes.compose) { inclusive = true }
        }
    }

    val pending_sessions = MainActivity.pending_open_sessions.value
    androidx.compose.runtime.LaunchedEffect(pending_sessions, is_signed_in_state, is_locked) {
        if (!pending_sessions || !is_signed_in_state || is_locked) return@LaunchedEffect
        MainActivity.pending_open_sessions.value = false
        nav_controller.navigate(routes.settings_detail("sessions")) {
            launchSingleTop = true
        }
    }

    if (is_signed_in_state) {
        org.astermail.android.ui.upgrade.UpgradeHost(
            on_navigate_to_billing = {
                nav_controller.navigate(routes.settings_detail("billing"))
            },
        )
        androidx.compose.runtime.LaunchedEffect(Unit) {
            org.astermail.android.api.AuthEventBus.unauthorized.collect {
                auth_gate.auth_repository.handle_unauthorized_signal()
            }
        }
        val preferences_sync_vm: org.astermail.android.settings.SettingsViewModel = hiltViewModel()
        androidx.compose.runtime.LaunchedEffect(is_signed_in_state) {
            preferences_sync_vm.load_preferences()
        }
        val undo_route by nav_controller.currentBackStackEntryAsState()
        if (!is_locked && undo_route?.destination?.route != routes.pending_send_preview) {
            org.astermail.android.ui.mail.undo_send_toast(
                on_view = { nav_controller.navigate(routes.pending_send_preview) },
            )
        }
    }

    androidx.compose.runtime.CompositionLocalProvider(
        org.astermail.android.ui.settings.local_settings_navigator provides { id ->
            nav_controller.navigate(routes.settings_detail(id))
        },
    ) {
    NavHost(
        navController = nav_controller,
        startDestination = start,
        enterTransition = {
            if (initialState.destination.route?.startsWith("compose") == true) {
                androidx.compose.animation.EnterTransition.None
            } else {
                nav_forward_enter(nav_duration)
            }
        },
        exitTransition = {
            val target = targetState.destination.route
            if (target?.startsWith("compose") == true || target?.startsWith("search") == true) {
                androidx.compose.animation.ExitTransition.None
            } else {
                nav_forward_exit(nav_duration)
            }
        },
        popEnterTransition = {
            val initial = initialState.destination.route
            if (initial?.startsWith("compose") == true || initial?.startsWith("search") == true) {
                androidx.compose.animation.EnterTransition.None
            } else {
                nav_backward_enter(nav_duration)
            }
        },
        popExitTransition = {
            if (targetState.destination.route?.startsWith("compose") == true) {
                androidx.compose.animation.ExitTransition.None
            } else {
                nav_backward_exit(nav_duration)
            }
        },
    ) {
        composable(routes.onboarding) {
            OnboardingScreen(
                on_sign_in = {
                    theme_vm.mark_onboarding_seen()
                    nav_controller.navigate(routes.sign_in) {
                        popUpTo(routes.onboarding) {
                            inclusive = true
                            saveState = false
                        }
                    }
                },
                on_create_account = {
                    theme_vm.mark_onboarding_seen()
                    nav_controller.navigate(routes.register) {
                        popUpTo(routes.onboarding) {
                            inclusive = true
                            saveState = false
                        }
                    }
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
                ?.let { java.net.URLDecoder.decode(it, "UTF-8") }
                .orEmpty()
            SignInScreen(
                on_back = { nav_controller.popBackStack() },
                on_forgot_password = { nav_controller.navigate(routes.forgot_password) },
                on_signed_in = {
                    nav_controller.navigate(routes.inbox) {
                        popUpTo(0) { inclusive = true }
                    }
                },
                on_register = {
                    nav_controller.navigate(routes.register) {
                        popUpTo(routes.sign_in) { inclusive = true }
                        launchSingleTop = true
                    }
                },
                prefill_email = prefill,
            )
        }
        composable(routes.register) {
            RegisterScreen(
                on_back = { nav_controller.popBackStack() },
                on_registered = {
                    nav_controller.navigate(routes.inbox) {
                        popUpTo(routes.welcome) { inclusive = true }
                    }
                },
                on_sign_in = {
                    nav_controller.navigate(routes.sign_in) {
                        popUpTo(routes.register) { inclusive = true }
                        launchSingleTop = true
                    }
                },
                on_terms_click = {
                    context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("https://astermail.org/terms")))
                },
                on_privacy_click = {
                    context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("https://astermail.org/privacy")))
                },
            )
        }
        composable(
            route = routes.recovery_key,
            arguments = listOf(navArgument("mnemonic") { type = NavType.StringType }),
        ) { entry ->
            val encoded = entry.arguments?.getString("mnemonic").orEmpty()
            val mnemonic = java.net.URLDecoder.decode(encoded, "UTF-8")
            RecoveryKeyScreen(
                mnemonic = mnemonic,
                on_continue = {
                    nav_controller.navigate(routes.inbox) {
                        popUpTo(routes.welcome) { inclusive = true }
                    }
                },
            )
        }
        composable(routes.forgot_password) {
            ForgotPasswordScreen(
                on_back = { nav_controller.popBackStack() },
                on_submit = { _ -> nav_controller.popBackStack() },
            )
        }
        composable(routes.inbox) {
            InboxWithDrawer(nav_controller)
        }
        composable(
            route = routes.mail_detail,
            arguments = listOf(navArgument("email_id") { type = NavType.StringType }),
        ) { entry ->
            val email_id = entry.arguments?.getString("email_id").orEmpty()
            val inbox_entry = remember(nav_controller) {
                try { nav_controller.getBackStackEntry(routes.inbox) } catch (_: Throwable) { null }
            }
            val shared_mail_vm: org.astermail.android.mail.MailViewModel =
                if (inbox_entry != null) hiltViewModel(inbox_entry) else hiltViewModel()
            val shared_settings_vm: org.astermail.android.settings.SettingsViewModel =
                if (inbox_entry != null) hiltViewModel(inbox_entry) else hiltViewModel()
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
            val open_neighbor: (String) -> Unit = { next_id ->
                nav_controller.navigate(routes.mail_detail_for(next_id)) {
                    popUpTo(routes.mail_detail) { inclusive = true }
                    launchSingleTop = true
                }
            }
            val settings_state by shared_settings_vm.state.collectAsStateWithLifecycle()
            val detail_thread_state by shared_mail_vm.thread_state.collectAsStateWithLifecycle()
            androidx.compose.runtime.LaunchedEffect(detail_thread_state.item?.id, detail_thread_state.is_loading) {
                if (detail_thread_state.is_loading) return@LaunchedEffect
                val reveal_id = MainActivity.pending_reveal_email_id.value ?: return@LaunchedEffect
                val item = detail_thread_state.item ?: return@LaunchedEffect
                if (item.id != reveal_id || reveal_id != email_id) return@LaunchedEffect
                MainActivity.pending_reveal_email_id.value = null
                val tokens = (
                    item.labels +
                        listOfNotNull(item.raw_item.folder_token) +
                        (item.raw_item.folders?.mapNotNull { it.folder_token } ?: emptyList())
                    ).distinct()
                if (tokens.isNotEmpty()) {
                    MainActivity.pending_reveal_folder_tokens.value = tokens
                }
            }
            val advance_after_action: () -> Unit = {
                val next = when (settings_state.preferences?.auto_advance ?: "Go to next message") {
                    "Go to next message" -> neighbor_id(1)
                    "Go to previous message" -> neighbor_id(-1)
                    else -> null
                }
                if (next != null) open_neighbor(next) else nav_controller.popBackStack()
            }
            MailDetailScreen(
                email_id = email_id,
                on_back = { nav_controller.popBackStack() },
                on_reply = { msg_id, ghost -> context.startActivity(ComposeActivity.intent_for(context, reply_to = msg_id, mode = "reply", thread_ghost_email = ghost)) },
                on_reply_all = { msg_id, ghost -> context.startActivity(ComposeActivity.intent_for(context, reply_to = msg_id, mode = "reply_all", thread_ghost_email = ghost)) },
                on_forward = { msg_id, ghost -> context.startActivity(ComposeActivity.intent_for(context, reply_to = msg_id, mode = "forward", thread_ghost_email = ghost)) },
                on_archive = advance_after_action,
                on_delete = advance_after_action,
                on_next = neighbor_id(1)?.let { next -> { open_neighbor(next) } },
                on_previous = neighbor_id(-1)?.let { prev -> { open_neighbor(prev) } },
                on_navigate = { path ->
                    val route = when {
                        path.startsWith("settings/") -> routes.settings_detail(path.removePrefix("settings/"))
                        path == "settings" -> routes.settings
                        path == "pending_send_preview" -> routes.pending_send_preview
                        path.startsWith("search:") -> routes.search_for(path.removePrefix("search:"))
                        else -> null
                    }
                    if (route != null) nav_controller.navigate(route)
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
            val id = java.net.URLDecoder.decode(
                entry.arguments?.getString("folder_id").orEmpty(), "UTF-8",
            )
            val name = java.net.URLDecoder.decode(
                entry.arguments?.getString("folder_name").orEmpty(), "UTF-8",
            )
            FilteredInboxScreen(
                filter_type = FilterType.folder,
                filter_value = id,
                filter_display_name = name,
                on_open_drawer = { nav_controller.popBackStack() },
                on_open_email = { eid -> open_mail_detail(nav_controller, eid) },
            )
        }
        composable(
            route = routes.label_filter,
            arguments = listOf(
                navArgument("label_id") { type = NavType.StringType },
                navArgument("label_name") { type = NavType.StringType },
            ),
        ) { entry ->
            val id = java.net.URLDecoder.decode(
                entry.arguments?.getString("label_id").orEmpty(), "UTF-8",
            )
            val name = java.net.URLDecoder.decode(
                entry.arguments?.getString("label_name").orEmpty(), "UTF-8",
            )
            FilteredInboxScreen(
                filter_type = FilterType.label,
                filter_value = id,
                filter_display_name = name,
                on_open_drawer = { nav_controller.popBackStack() },
                on_open_email = { eid -> open_mail_detail(nav_controller, eid) },
            )
        }
        composable(
            route = routes.alias_filter,
            arguments = listOf(
                navArgument("alias_id") { type = NavType.StringType },
                navArgument("alias_name") { type = NavType.StringType },
            ),
        ) { entry ->
            val id = java.net.URLDecoder.decode(
                entry.arguments?.getString("alias_id").orEmpty(), "UTF-8",
            )
            val name = java.net.URLDecoder.decode(
                entry.arguments?.getString("alias_name").orEmpty(), "UTF-8",
            )
            FilteredInboxScreen(
                filter_type = FilterType.alias,
                filter_value = id,
                filter_display_name = name,
                on_open_drawer = { nav_controller.popBackStack() },
                on_open_email = { eid -> open_mail_detail(nav_controller, eid) },
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
            val compose_inbox_entry = remember(nav_controller) {
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
                ?.let { java.net.URLDecoder.decode(it, "UTF-8") }
            val mode = raw_mode?.takeIf { it.isNotBlank() }
                ?.let { java.net.URLDecoder.decode(it, "UTF-8") }
            val draft_id = raw_draft_id?.takeIf { it.isNotBlank() }
                ?.let { java.net.URLDecoder.decode(it, "UTF-8") }
            val prefill_to = raw_to?.takeIf { it.isNotBlank() }
                ?.let { java.net.URLDecoder.decode(it, "UTF-8") }
            val thread_ghost_email = raw_thread_ghost?.takeIf { it.isNotBlank() }
                ?.let { java.net.URLDecoder.decode(it, "UTF-8") }
            val is_share_entry = !entry.arguments?.getString("share").isNullOrBlank()
            val share_payload = remember(entry.id) {
                if (!is_share_entry) null
                else MainActivity.pending_share.value.also { MainActivity.pending_share.value = null }
            }
            ComposeScreen(
                on_back = { nav_controller.popBackStack() },
                on_sent = { nav_controller.popBackStack() },
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
            enterTransition = { nav_expand_enter(if (nav_duration == 0) 0 else nav_anim_expand_ms) },
            exitTransition = { nav_expand_exit(if (nav_duration == 0) 0 else nav_anim_collapse_ms) },
            popEnterTransition = { nav_expand_enter(if (nav_duration == 0) 0 else nav_anim_expand_ms) },
            popExitTransition = { nav_expand_exit(if (nav_duration == 0) 0 else nav_anim_collapse_ms) },
        ) {
            SearchScreen(
                on_back = { nav_controller.popBackStack() },
                on_open_email = { id -> open_mail_detail(nav_controller, id) },
            )
        }
        composable(
            route = routes.search_with_query,
            arguments = listOf(androidx.navigation.navArgument("q") { defaultValue = "" }),
            enterTransition = { nav_expand_enter(if (nav_duration == 0) 0 else nav_anim_expand_ms) },
            exitTransition = { nav_expand_exit(if (nav_duration == 0) 0 else nav_anim_collapse_ms) },
            popEnterTransition = { nav_expand_enter(if (nav_duration == 0) 0 else nav_anim_expand_ms) },
            popExitTransition = { nav_expand_exit(if (nav_duration == 0) 0 else nav_anim_collapse_ms) },
        ) { entry ->
            val q = entry.arguments?.getString("q").orEmpty()
            SearchScreen(
                on_back = { nav_controller.popBackStack() },
                on_open_email = { id -> open_mail_detail(nav_controller, id) },
                initial_query = q,
            )
        }
        composable(routes.pending_send_preview) {
            org.astermail.android.ui.mail.pending_send_preview_screen(
                on_back = { nav_controller.popBackStack() },
            )
        }
        composable(routes.settings) {
            SettingsScreen(
                on_back = { nav_controller.popBackStack() },
                on_open = { id -> nav_controller.navigate(routes.settings_detail(id)) },
            )
        }
        composable(routes.mailing_lists) {
            MailingListsScreen(
                on_back = { nav_controller.popBackStack(); Unit },
                on_open_search = { nav_controller.navigate(routes.search) },
                on_search_sender = { sender ->
                    nav_controller.navigate(routes.search_for("from:" + sender))
                },
            )
        }
        composable(routes.contacts) {
            ContactsScreen(
                on_back = { nav_controller.popBackStack() },
                on_open_contact = { id -> nav_controller.navigate(routes.contact_detail_for(id)) },
                on_create_contact = { nav_controller.navigate(routes.contact_edit_new) },
            )
        }
        composable(
            route = routes.contact_detail,
            arguments = listOf(navArgument("contact_id") { type = NavType.StringType }),
        ) { entry ->
            val id = entry.arguments?.getString("contact_id").orEmpty()
            val contacts_entry = remember(nav_controller) {
                try { nav_controller.getBackStackEntry(routes.contacts) } catch (_: Throwable) {
                    try { nav_controller.getBackStackEntry(routes.inbox) } catch (_: Throwable) { null }
                }
            }
            val shared_contacts_vm: org.astermail.android.contacts.ContactsViewModel =
                if (contacts_entry != null) hiltViewModel(contacts_entry) else hiltViewModel()
            ContactDetailScreen(
                contact_id = id,
                on_back = { nav_controller.popBackStack() },
                on_edit = { cid -> nav_controller.navigate(routes.contact_edit_for(cid)) },
                on_compose = { email -> context.startActivity(ComposeActivity.intent_for(context, prefill_to = email)) },
                vm = shared_contacts_vm,
            )
        }
        composable(routes.contact_edit_new) {
            val contacts_entry = remember(nav_controller) {
                try { nav_controller.getBackStackEntry(routes.contacts) } catch (_: Throwable) {
                    try { nav_controller.getBackStackEntry(routes.inbox) } catch (_: Throwable) { null }
                }
            }
            val shared_contacts_vm: org.astermail.android.contacts.ContactsViewModel =
                if (contacts_entry != null) hiltViewModel(contacts_entry) else hiltViewModel()
            ContactEditScreen(
                contact_id = null,
                on_back = { nav_controller.popBackStack() },
                on_saved = { nav_controller.popBackStack() },
                vm = shared_contacts_vm,
            )
        }
        composable(
            route = routes.contact_edit,
            arguments = listOf(navArgument("contact_id") { type = NavType.StringType }),
        ) { entry ->
            val id = entry.arguments?.getString("contact_id")
            val contacts_entry = remember(nav_controller) {
                try { nav_controller.getBackStackEntry(routes.contacts) } catch (_: Throwable) {
                    try { nav_controller.getBackStackEntry(routes.inbox) } catch (_: Throwable) { null }
                }
            }
            val shared_contacts_vm: org.astermail.android.contacts.ContactsViewModel =
                if (contacts_entry != null) hiltViewModel(contacts_entry) else hiltViewModel()
            ContactEditScreen(
                contact_id = id,
                on_back = { nav_controller.popBackStack() },
                on_saved = { nav_controller.popBackStack() },
                vm = shared_contacts_vm,
            )
        }

        val back = { nav_controller.popBackStack() }
        val open_detail: (String) -> Unit = { id -> nav_controller.navigate(routes.settings_detail(id)) }

        composable(routes.settings_detail("appearance")) {
            AppearanceScreen(on_back = { back(); Unit }, on_open = open_detail)
        }
        composable(routes.settings_detail("profile")) {
            ProfileScreen(
                on_back = { back(); Unit },
                on_open = open_detail,
            )
        }
        composable(routes.settings_detail("signature")) {
            SignatureScreen(on_back = { back(); Unit })
        }
        composable(routes.settings_detail("security")) {
            SecurityScreen(
                on_back = { back(); Unit },
                on_open = open_detail,
            )
        }
        composable(routes.settings_detail("password")) {
            ChangePasswordScreen(on_back = { back(); Unit })
        }
        composable(routes.settings_detail("change_password")) {
            ChangePasswordScreen(on_back = { back(); Unit })
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
        composable(routes.settings_detail("recovery_key")) {
            RecoveryKeyViewScreen(on_back = { back(); Unit })
        }
        composable(routes.settings_detail("recovery_key_view")) {
            RecoveryKeyViewScreen(on_back = { back(); Unit })
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
                open_create = entry.arguments?.getBoolean("create") ?: false,
            )
        }
        composable(routes.settings_detail("subscriptions")) {
            MailingListsScreen(
                on_back = { back(); Unit },
                on_open_search = { nav_controller.navigate(routes.search) },
                on_search_sender = { sender ->
                    nav_controller.navigate(routes.search_for("from:" + sender))
                },
            )
        }
        composable(routes.settings_detail("storage")) {
            StorageScreen(
                on_back = { back(); Unit },
                on_open = open_detail,
                on_open_folder = { folder_id, folder_name ->
                    nav_controller.navigate(routes.folder_filter_for(folder_id, folder_name))
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
            ImportScreen(on_back = { back(); Unit }, on_open = open_detail)
        }
        composable(routes.settings_detail("export")) {
            ExportScreen(on_back = { back(); Unit })
        }
        composable(routes.settings_detail("diagnostics")) {
            DiagnosticsScreen(on_back = { back(); Unit })
        }
        composable(routes.settings_detail("accessibility")) {
            AccessibilityScreen(on_back = { back(); Unit })
        }
        composable(routes.settings_detail("behavior")) {
            BehaviorScreen(on_back = { back(); Unit })
        }
        composable(routes.settings_detail("swipe_actions")) {
            SwipeActionsScreen(on_back = { back(); Unit })
        }
        composable(routes.settings_detail("customize_toolbar")) {
            CustomizeToolbarScreen(on_back = { back(); Unit })
        }
        composable(routes.settings_detail("billing")) {
            SubscriptionsScreen(on_back = { back(); Unit }, on_open = open_detail)
        }
        composable(routes.settings_detail("billing_addons")) {
            SubscriptionsScreen(on_back = { back(); Unit }, on_open = open_detail, scroll_to_addons = true)
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
            ExternalAccountsScreen(on_back = { back(); Unit }, on_open = open_detail)
        }
        composable(routes.settings_detail("sender_filters")) {
            SenderFiltersScreen(on_back = { back(); Unit }, on_open = open_detail)
        }
        composable(routes.settings_detail("trusted_devices")) {
            TrustedDevicesScreen(on_back = { back(); Unit })
        }
        composable(routes.settings_detail("ghost_aliases")) {
            GhostAliasesScreen(on_back = { back(); Unit })
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
        composable(routes.settings_detail("about")) {
            AboutScreen(on_back = { back(); Unit })
        }
        composable(routes.settings_detail("developer")) {
            DeveloperScreen(on_back = { back(); Unit })
        }
    }

    if (is_signed_in_state) {
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
}

private val mail_folder_ids = setOf(
    "inbox", "sent", "drafts", "trash", "spam", "archive",
    "starred", "all", "scheduled", "snoozed",
)

@Composable
private fun InboxWithDrawer(nav_controller: NavHostController) {
    val drawer_state = rememberDrawerState(initialValue = DrawerValue.Closed)
    val scope = rememberCoroutineScope()
    var selected_folder by androidx.compose.runtime.saveable.rememberSaveable { mutableStateOf("inbox") }
    var inbox_category by androidx.compose.runtime.saveable.rememberSaveable { mutableStateOf("primary") }
    var filter_kind by androidx.compose.runtime.saveable.rememberSaveable { mutableStateOf<String?>(null) }
    var filter_value by androidx.compose.runtime.saveable.rememberSaveable { mutableStateOf("") }
    var filter_name by androidx.compose.runtime.saveable.rememberSaveable { mutableStateOf("") }
    val colors = AsterMaterial.colors

    val mail_vm: org.astermail.android.mail.MailViewModel = hiltViewModel()
    val inbox_state by mail_vm.inbox_state.collectAsStateWithLifecycle()
    val stats = inbox_state.stats

    val settings_vm: org.astermail.android.settings.SettingsViewModel = hiltViewModel()
    val settings_state by settings_vm.state.collectAsStateWithLifecycle()

    val accounts_vm: org.astermail.android.accounts.AccountsViewModel = hiltViewModel()
    val accounts_state by accounts_vm.state.collectAsStateWithLifecycle()

    val drawer_context = androidx.compose.ui.platform.LocalContext.current
    androidx.compose.runtime.LaunchedEffect(settings_state.action_result) {
        val msg = settings_state.action_result ?: return@LaunchedEffect
        android.widget.Toast.makeText(drawer_context, msg, android.widget.Toast.LENGTH_SHORT).show()
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
        settings_vm.load_preferences()
        mail_vm.load_stats()
    }

    androidx.compose.runtime.LaunchedEffect(drawer_state.isOpen) {
        if (drawer_state.isOpen) {
            kotlinx.coroutines.delay(220)
            settings_vm.load_labels()
            settings_vm.load_tags()
        }
    }

    val pending_reveal_tokens = MainActivity.pending_reveal_folder_tokens.value
    androidx.compose.runtime.LaunchedEffect(pending_reveal_tokens, settings_state.labels) {
        val tokens = pending_reveal_tokens ?: return@LaunchedEffect
        if (settings_state.labels.isEmpty()) return@LaunchedEffect
        MainActivity.pending_reveal_folder_tokens.value = null
        val node = org.astermail.android.folders.flatten_folder_tree(settings_state.labels)
            .firstOrNull { it.label.label_token in tokens } ?: return@LaunchedEffect
        val readable_name = node.label.encrypted_name
            ?.takeIf { it.isNotBlank() && !looks_encrypted(it) } ?: return@LaunchedEffect
        filter_kind = "folder"
        filter_value = node.label.label_token
        filter_name = readable_name
        selected_folder = node.label.label_token
    }

    val prefs = settings_state.preferences
    val categories_enabled = prefs?.inbox_categories_enabled ?: false
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
    val category_unread = androidx.compose.runtime.remember(
        inbox_state.items,
        selected_folder,
        active_category_tabs,
    ) {
        if (selected_folder == "inbox") {
            org.astermail.android.mail.category_unread_counts(inbox_state.items, active_category_tabs)
        } else {
            emptyMap()
        }
    }
    val category_entries = org.astermail.android.mail.category_entries(
        active_category_tabs,
        prefs?.custom_categories ?: emptyList(),
    )
    val category_titles = category_entries.associate { it.id to it.label }
    val theme_vm_inbox: ThemeViewModel = hiltViewModel()

    androidx.compose.runtime.LaunchedEffect(prefs?.custom_categories) {
        mail_vm.set_custom_categories(prefs?.custom_categories ?: emptyList())
    }

    androidx.compose.runtime.LaunchedEffect(prefs) {
        if (prefs == null) return@LaunchedEffect
        theme_vm_inbox.set_high_contrast(prefs.high_contrast)
        theme_vm_inbox.set_reduce_transparency(prefs.reduce_transparency)
        theme_vm_inbox.set_reduce_motion(prefs.reduce_motion)
        theme_vm_inbox.set_compact_mode(prefs.compact_mode)
        theme_vm_inbox.set_text_spacing(prefs.text_spacing)
        theme_vm_inbox.set_underline_links(prefs.underline_links)
        theme_vm_inbox.set_haptic_enabled(prefs.haptic_enabled)
        theme_vm_inbox.set_dyslexia_font(prefs.dyslexia_font)
        theme_vm_inbox.set_text_size_from_key(prefs.font_size_scale)
    }

    val storage = settings_state.storage
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
    val storage_fraction = when {
        total_bytes > 0 -> (used_bytes.toFloat() / total_bytes).coerceIn(0f, 1f)
        storage != null && storage.percentage_used > 0 -> (storage.percentage_used / 100.0).toFloat().coerceIn(0f, 1f)
        else -> 0f
    }
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

    val user_email = settings_state.user?.email
        ?: accounts_state.accounts.firstOrNull { it.id == accounts_state.current_account_id }?.email
        ?: accounts_state.accounts.firstOrNull()?.email
        ?: ""

    val folder_nodes = org.astermail.android.folders.flatten_folder_tree(settings_state.labels)

    val api_folders = folder_nodes.map { node ->
        val label = node.label
        val readable_name = label.encrypted_name?.takeIf { it.isNotBlank() && !looks_encrypted(it) }
        drawer_folder_item(
            id = label.label_token,
            label = readable_name ?: drawer_context.getString(R.string.folder_decrypt_failed),
            icon = if (org.astermail.android.folders.is_folder_protected(label)) TablerIcons.Lock else TablerIcons.Folder,
            count = label.unread_count?.toInt() ?: 0,
            depth = node.depth,
            trail = node.trail,
            has_next = node.has_next,
            has_children = node.has_children,
        )
    }

    val folder_parent_options = folder_nodes
        .filter { it.depth < org.astermail.android.folders.max_folder_depth }
        .mapNotNull { node ->
            val label = node.label
            val readable_name = label.encrypted_name?.takeIf { it.isNotBlank() && !looks_encrypted(it) }
                ?: return@mapNotNull null
            org.astermail.android.ui.drawer.folder_parent_option(
                token = label.label_token,
                label = readable_name,
                depth = node.depth,
                path_label = org.astermail.android.folders.folder_path(settings_state.labels, label.label_token)
                    .filter { it.isNotBlank() && !looks_encrypted(it) }
                    .joinToString(" · "),
            )
        }

    val quick_custom_folders = folder_nodes
        .mapNotNull { node ->
            val readable_name = node.label.encrypted_name?.takeIf { it.isNotBlank() && !looks_encrypted(it) }
                ?: return@mapNotNull null
            node.label.label_token to readable_name
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

    val api_labels = run {
        val from_tags = settings_state.tags
            .filter { it.encrypted_name.isNotBlank() }
            .filter { !looks_encrypted(it.encrypted_name) }
            .mapIndexed { idx, tag ->
                val color = parse_hex_color_safe(tag.encrypted_color)
                    ?: label_colors[idx % label_colors.size]
                val icon = tag.encrypted_icon?.takeIf { it.isNotBlank() && !looks_encrypted(it) }
                drawer_label_item(
                    id = "tag:${tag.tag_token}",
                    label = tag.encrypted_name,
                    color = color,
                    icon = icon,
                )
            }
        val from_labels = settings_state.labels
            .filter { it.folder_type == "label" }
            .filter { !it.encrypted_name.isNullOrBlank() }
            .filter { !looks_encrypted(it.encrypted_name) }
            .mapIndexed { idx, label ->
                val color = label_colors[(from_tags.size + idx) % label_colors.size]
                drawer_label_item(
                    id = "label:${label.label_token}",
                    label = label.encrypted_name.orEmpty(),
                    color = color,
                    icon = null,
                )
            }
        from_tags + from_labels
    }

    val api_aliases = settings_state.aliases
        .filter { !looks_encrypted(it.encrypted_local_part) }
        .map { alias ->
            drawer_alias_item(
                id = alias.id,
                address = alias.address,
                routing_token = alias.alias_address_hash.ifBlank { null },
            )
        } + settings_state.custom_domain_addresses
        .filter { !looks_encrypted(it.encrypted_local_part) }
        .map { addr ->
            drawer_alias_item(
                id = addr.id,
                address = addr.address,
            )
        }

    val lock_revision by org.astermail.android.folders.folder_lock_store.revision.collectAsState()

    var pending_unlock_folder by remember { mutableStateOf<Pair<String, String>?>(null) }
    var unlock_verifying by remember { mutableStateOf(false) }
    var unlock_error by remember { mutableStateOf<String?>(null) }

    val open_custom_folder: (String, String) -> Unit = { token, name ->
        filter_kind = "folder"
        filter_value = token
        filter_name = name
        selected_folder = token
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
    val locked_active_folder = androidx.compose.runtime.remember(
        active_folder_token,
        settings_state.labels,
        lock_revision,
    ) {
        org.astermail.android.folders.locked_active_folder(settings_state.labels, active_folder_token)
    }
    val effective_filter_kind = if (locked_active_folder != null) null else filter_kind
    val effective_selected_folder = if (locked_active_folder != null) "inbox" else selected_folder

    androidx.compose.runtime.LaunchedEffect(locked_active_folder?.label_token) {
        val label = locked_active_folder ?: return@LaunchedEffect
        val readable_name = label.encrypted_name
            ?.takeIf { it.isNotBlank() && !looks_encrypted(it) }
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
                drawerContainerColor = colors.bg_primary,
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
                        id == "settings" -> nav_controller.navigate(routes.settings)
                        id == "contacts" -> { filter_kind = null; selected_folder = "contacts"; scope.launch { drawer_state.close() } }
                        id == "subscriptions" -> { filter_kind = null; selected_folder = "subscriptions"; scope.launch { drawer_state.close() } }
                        id == "plan" -> nav_controller.navigate(routes.settings_detail("billing"))
                        id == "aliases_settings" -> nav_controller.navigate(routes.settings_detail("aliases"))
                        id == "aliases_create" -> nav_controller.navigate(routes.settings_detail("aliases") + "?create=true")
                        id == "referral" -> nav_controller.navigate(routes.settings_detail("referral"))
                        id == "feedback" -> nav_controller.navigate(routes.settings_detail("feedback"))
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
                on_navigate_alias = { _, name, routing_token ->
                    scope.launch { drawer_state.close() }
                    if (routing_token != null) {
                        nav_controller.navigate(routes.alias_filter_for(routing_token, name))
                    } else {
                        nav_controller.navigate(routes.search_for("to:$name"))
                    }
                },
                inbox_unread = stats?.unread ?: 0,
                drafts_count = stats?.drafts ?: 0,
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
                api_folder_items = api_folders,
                api_label_items = api_labels,
                api_alias_items = api_aliases,
                accounts = accounts_state.accounts,
                current_account_id = accounts_state.current_account_id,
                can_add_account = accounts_state.can_add_more,
                on_switch_account = { account ->
                    scope.launch { drawer_state.close() }
                    if (accounts_vm.has_stored_session(account.id)) {
                        mail_vm.reset_for_account_switch()
                        settings_vm.reset_for_account_switch()
                        accounts_vm.switch_account(account.id) { restored ->
                            if (restored) {
                                settings_vm.load_preferences()
                                selected_folder = "inbox"
                                filter_kind = null
                                nav_controller.navigate(routes.inbox) {
                                    popUpTo(0) { inclusive = true }
                                }
                            } else {
                                nav_controller.navigate(routes.sign_in_for(account.email))
                            }
                        }
                    } else {
                        mail_vm.reset_for_account_switch()
                        settings_vm.reset_for_account_switch()
                        accounts_vm.switch_account(account.id)
                        nav_controller.navigate(routes.sign_in_for(account.email))
                    }
                },
                on_add_account = {
                    nav_controller.navigate(routes.sign_in_for(""))
                },
                on_open_workspace_sheet = {
                    accounts_vm.refresh_with_profile()
                },
                on_create_label = { name, color, icon ->
                    settings_vm.create_tag(name = name, color = color, icon = icon)
                },
                on_create_folder = { name, parent_token ->
                    val sibling_count = settings_state.labels.count {
                        org.astermail.android.folders.is_custom_folder(it) &&
                            it.parent_token.orEmpty() == parent_token.orEmpty()
                    }
                    settings_vm.create_folder(
                        name = name,
                        sort_order = sibling_count,
                        parent_token = parent_token,
                    )
                },
                folder_parent_options = folder_parent_options,
                on_logout = {
                    settings_vm.logout { switched_account ->
                        accounts_vm.refresh()
                        if (switched_account) {
                            mail_vm.reset_for_account_switch()
                            settings_vm.reset_for_account_switch()
                            settings_vm.load_preferences()
                            selected_folder = "inbox"
                            filter_kind = null
                            nav_controller.navigate(routes.inbox) {
                                popUpTo(0) { inclusive = true }
                            }
                            return@logout
                        }
                        val next = accounts_vm.state.value.accounts.firstOrNull()
                        if (next != null) {
                            nav_controller.navigate(routes.sign_in_for(next.email)) {
                                popUpTo(0) { inclusive = true }
                            }
                        } else {
                            nav_controller.navigate(routes.welcome) {
                                popUpTo(0) { inclusive = true }
                            }
                        }
                    }
                },
                initial_more_collapsed = prefs?.sidebar_more_collapsed ?: false,
                initial_folders_collapsed = prefs?.sidebar_folders_collapsed ?: false,
                initial_labels_collapsed = prefs?.sidebar_labels_collapsed ?: false,
                initial_aliases_collapsed = prefs?.sidebar_aliases_collapsed ?: false,
                preferences_loaded = prefs != null,
                on_sidebar_toggle = { key, value ->
                    settings_vm.update_sidebar_state(key, value)
                },
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
        BackHandler(enabled = drawer_state.isOpen) {
            scope.launch { drawer_state.close() }
        }
        BackHandler(enabled = !drawer_state.isOpen) {
            val now = android.os.SystemClock.elapsedRealtime()
            if (now - last_back_press in 1L..2000L) {
                (exit_context as? android.app.Activity)?.finish()
            } else {
                last_back_press = now
                android.widget.Toast.makeText(exit_context, exit_hint, android.widget.Toast.LENGTH_SHORT).show()
            }
        }
        val saveable_state_holder = rememberSaveableStateHolder()
        var inbox_scroll_top_token by remember { mutableStateOf(0) }
        val folder_anim_duration = if (local_accessibility.current.reduce_motion) 0 else nav_anim_duration_ms
        AnimatedContent(
            targetState = folder_key,
            transitionSpec = {
                val from_depth = folder_key_depth(initialState)
                val to_depth = folder_key_depth(targetState)
                when {
                    folder_anim_duration == 0 ->
                        EnterTransition.None togetherWith ExitTransition.None
                    to_depth > from_depth ->
                        nav_forward_enter(folder_anim_duration) togetherWith nav_forward_exit(folder_anim_duration)
                    to_depth < from_depth ->
                        nav_backward_enter(folder_anim_duration) togetherWith nav_backward_exit(folder_anim_duration)
                    else ->
                        fadeIn(animationSpec = tween(120)) togetherWith fadeOut(animationSpec = tween(80))
                }
            },
            label = "folder_switch",
        ) { active_key ->
            saveable_state_holder.SaveableStateProvider(active_key) {
                when {
                    effective_filter_kind != null -> {
                        BackHandler(enabled = !drawer_state.isOpen) {
                            saveable_state_holder.removeState("inbox:inbox")
                            inbox_scroll_top_token += 1
                            filter_kind = null
                            filter_value = ""
                            filter_name = ""
                            selected_folder = "inbox"
                        }
                        val effective_folder = when (effective_filter_kind) {
                            "label" -> "label:$filter_value"
                            "tag" -> "tag:$filter_value"
                            "folder" -> filter_value
                            else -> "inbox"
                        }
                        InboxScreen(
                            on_open_drawer = { scope.launch { drawer_state.open() } },
                            on_open_search = { nav_controller.navigate(routes.search_for_folder(effective_folder)) },
                            on_compose = { drawer_context.startActivity(ComposeActivity.intent_for(drawer_context)) },
                            on_compose_draft = { id -> drawer_context.startActivity(ComposeActivity.intent_for(drawer_context, draft_id = id)) },
                            on_view_pending_send = { nav_controller.navigate(routes.pending_send_preview) },
                            on_open_email = { id -> open_mail_detail(nav_controller, id) },
                            on_open_settings = { nav_controller.navigate(routes.settings) },
                            on_open_upgrade = { nav_controller.navigate(routes.settings_detail("billing")) },
                            current_folder = effective_folder,
                            display_title = filter_name,
                            on_folder_change = { id ->
                                filter_kind = null
                                selected_folder = id
                            },
                            custom_folders = quick_custom_folders,
                            on_custom_folder_change = { id, name -> request_custom_folder(id, name) },
                            on_customize_toolbar = { nav_controller.navigate(routes.settings_detail("customize_toolbar")) },
                        )
                    }
                    effective_selected_folder == "subscriptions" -> {
                        BackHandler(enabled = !drawer_state.isOpen) {
                            saveable_state_holder.removeState("inbox:inbox")
                            inbox_scroll_top_token += 1
                            selected_folder = "inbox"
                        }
                        MailingListsScreen(
                            on_open_drawer = { scope.launch { drawer_state.open() } },
                            on_open_search = { nav_controller.navigate(routes.search) },
                            on_search_sender = { sender ->
                                nav_controller.navigate(routes.search_for("from:" + sender))
                            },
                        )
                    }
                    effective_selected_folder == "contacts" -> {
                        BackHandler(enabled = !drawer_state.isOpen) {
                            saveable_state_holder.removeState("inbox:inbox")
                            inbox_scroll_top_token += 1
                            selected_folder = "inbox"
                        }
                        ContactsScreen(
                            on_open_drawer = { scope.launch { drawer_state.open() } },
                            on_open_contact = { id -> nav_controller.navigate(routes.contact_detail_for(id)) },
                            on_create_contact = { nav_controller.navigate(routes.contact_edit_new) },
                        )
                    }
                    else -> {
                        BackHandler(
                            enabled = !drawer_state.isOpen &&
                                (effective_selected_folder != "inbox" || inbox_category != "primary"),
                        ) {
                            saveable_state_holder.removeState("inbox:inbox")
                            inbox_scroll_top_token += 1
                            inbox_category = "primary"
                            selected_folder = "inbox"
                        }
                        InboxScreen(
                            on_open_drawer = { scope.launch { drawer_state.open() } },
                            on_open_search = { nav_controller.navigate(routes.search_for_folder(effective_selected_folder)) },
                            on_compose = { drawer_context.startActivity(ComposeActivity.intent_for(drawer_context)) },
                            on_compose_draft = { id -> drawer_context.startActivity(ComposeActivity.intent_for(drawer_context, draft_id = id)) },
                            on_view_pending_send = { nav_controller.navigate(routes.pending_send_preview) },
                            on_open_email = { id ->
                                if (effective_selected_folder == "drafts") {
                                    drawer_context.startActivity(ComposeActivity.intent_for(drawer_context, draft_id = id))
                                } else {
                                    open_mail_detail(nav_controller, id)
                                }
                            },
                            on_open_settings = { nav_controller.navigate(routes.settings) },
                            on_open_upgrade = { nav_controller.navigate(routes.settings_detail("billing")) },
                            current_folder = effective_selected_folder,
                            inbox_category = inbox_category,
                            display_title = if (effective_selected_folder == "inbox" && categories_enabled) category_titles[inbox_category] else null,
                            on_folder_change = { selected_folder = it },
                            custom_folders = quick_custom_folders,
                            on_custom_folder_change = { id, name -> request_custom_folder(id, name) },
                            on_customize_toolbar = { nav_controller.navigate(routes.settings_detail("customize_toolbar")) },
                            scroll_top_token = if (effective_selected_folder == "inbox") inbox_scroll_top_token else 0,
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
                pending_unlock_folder = null
                unlock_verifying = false
                unlock_error = null
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
}

internal fun looks_encrypted(value: String?): Boolean {
    if (value.isNullOrBlank()) return false
    if (value.length < 20) return false
    val base64_chars = value.count { it in "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789+/=" }
    return base64_chars.toFloat() / value.length > 0.85f
}

private fun open_mail_detail(nav_controller: NavHostController, email_id: String) {
    val entry = nav_controller.currentBackStackEntry ?: return
    if (!entry.lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED)) return
    if (entry.destination.route == routes.mail_detail) return
    nav_controller.navigate(routes.mail_detail_for(email_id)) { launchSingleTop = true }
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
        "%.1f".format(java.util.Locale.US, rounded)
    }
    return "$text $suffix"
}

private fun folder_key_depth(key: String): Int = when {
    key.startsWith("filter:") -> 1
    key == "subscriptions" -> 1
    key == "contacts" -> 1
    else -> 0
}
