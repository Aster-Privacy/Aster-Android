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

import android.content.Intent
import android.net.Uri
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
import org.astermail.android.ui.security.AppLockScreen
import org.astermail.android.ui.common.nav_backward_enter
import org.astermail.android.ui.common.nav_backward_exit
import org.astermail.android.ui.common.nav_forward_enter
import org.astermail.android.ui.common.nav_forward_exit
import org.astermail.android.ui.common.nav_sheet_enter
import org.astermail.android.ui.common.nav_sheet_exit
import androidx.compose.animation.AnimatedContent
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
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch
import org.astermail.android.auth.AuthGateViewModel
import org.astermail.android.design.AsterMaterial
import org.astermail.android.design.AsterTheme
import org.astermail.android.design.AsterThemeMode
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
import androidx.compose.material.icons.outlined.Folder
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
import org.astermail.android.ui.settings.detail.ConnectionScreen
import org.astermail.android.ui.settings.detail.DeveloperScreen
import org.astermail.android.ui.settings.detail.FamilyScreen
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
class MainActivity : ComponentActivity() {

    private val lockdown_listener =
        android.content.SharedPreferences.OnSharedPreferenceChangeListener { _, _ ->
            runOnUiThread { enforce_secure_flag() }
        }

    override fun onCreate(saved_instance_state: Bundle?) {
        super.onCreate(saved_instance_state)
        enforce_secure_flag()
        LockdownStore.register_listener(applicationContext, lockdown_listener)
        enableEdgeToEdge()
        setContent {
            AsterRoot()
        }
    }

    override fun onResume() {
        super.onResume()
        enforce_secure_flag()
    }

    override fun onPause() {
        enforce_secure_flag()
        super.onPause()
    }

    override fun onDestroy() {
        LockdownStore.unregister_listener(applicationContext, lockdown_listener)
        super.onDestroy()
    }

    private fun enforce_secure_flag() {
        window.setFlags(
            android.view.WindowManager.LayoutParams.FLAG_SECURE,
            android.view.WindowManager.LayoutParams.FLAG_SECURE,
        )
    }
}

@Composable
private fun request_notification_permission_on_launch() {
    val context = androidx.compose.ui.platform.LocalContext.current
    val launcher = androidx.activity.compose.rememberLauncherForActivityResult(
        androidx.activity.result.contract.ActivityResultContracts.RequestPermission(),
    ) {
        context.getSharedPreferences("aster_perms", android.content.Context.MODE_PRIVATE)
            .edit().putBoolean("notif_perm_asked", true).apply()
    }
    androidx.compose.runtime.LaunchedEffect(Unit) {
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.TIRAMISU) {
            val prefs = context.getSharedPreferences("aster_perms", android.content.Context.MODE_PRIVATE)
            val already_asked = prefs.getBoolean("notif_perm_asked", false)
            val granted = androidx.core.content.ContextCompat.checkSelfPermission(
                context, android.Manifest.permission.POST_NOTIFICATIONS,
            ) == android.content.pm.PackageManager.PERMISSION_GRANTED
            if (!granted && !already_asked) {
                launcher.launch(android.Manifest.permission.POST_NOTIFICATIONS)
            }
        }
    }
}

@Composable
private fun AsterRoot() {
    request_notification_permission_on_launch()
    val theme_vm: ThemeViewModel = hiltViewModel()
    val mode_state by theme_vm.theme_mode.collectAsStateWithLifecycle()
    val text_size_state by theme_vm.text_size.collectAsStateWithLifecycle()
    val high_contrast by theme_vm.high_contrast.collectAsStateWithLifecycle()
    val reduce_transparency by theme_vm.reduce_transparency.collectAsStateWithLifecycle()
    val reduce_motion by theme_vm.reduce_motion.collectAsStateWithLifecycle()
    val compact_mode by theme_vm.compact_mode.collectAsStateWithLifecycle()
    val text_spacing by theme_vm.text_spacing.collectAsStateWithLifecycle()
    val underline_links by theme_vm.underline_links.collectAsStateWithLifecycle()
    val dyslexia_font by theme_vm.dyslexia_font.collectAsStateWithLifecycle()
    val resolved_mode = when (mode_state) {
        ThemeMode.system -> AsterThemeMode.system
        ThemeMode.light -> AsterThemeMode.light
        ThemeMode.dark -> AsterThemeMode.dark
    }
    val a11y = AccessibilityState(
        high_contrast = high_contrast,
        reduce_transparency = reduce_transparency,
        reduce_motion = reduce_motion,
        compact_mode = compact_mode,
        text_spacing = text_spacing,
        underline_links = underline_links,
        dyslexia_font = dyslexia_font,
    )
    val dyslexia_family = if (dyslexia_font) {
        FontFamily(Font(R.font.opendyslexic_regular, FontWeight.Normal))
    } else null

    AsterTheme(
        theme_mode = resolved_mode,
        high_contrast = high_contrast,
        reduce_transparency = reduce_transparency,
        dyslexia_font = dyslexia_family,
        text_spacing = text_spacing,
    ) {
        val base_density = LocalDensity.current
        val compact_factor = if (compact_mode) 0.9f else 1f
        val scaled_density = Density(
            density = base_density.density * compact_factor,
            fontScale = base_density.fontScale * text_size_state.scale,
        )
        CompositionLocalProvider(
            LocalDensity provides scaled_density,
            local_text_scale provides text_size_state.scale,
            local_accessibility provides a11y,
        ) {
            val colors = AsterMaterial.colors
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(colors.bg_primary),
            ) {
                AsterNavHost()
            }
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
    const val compose = "compose?reply_to={reply_to}&mode={mode}&draft_id={draft_id}&to={to}&thread_ghost={thread_ghost}"
    const val search = "search"
    const val search_with_query = "search?q={q}"
    fun search_for(query: String): String {
        return "search?q=" + java.net.URLEncoder.encode(query, "UTF-8")
    }
    fun search_for_folder(folder: String): String {
        val scope = when (folder) {
            "trash" -> "in:trash "
            "archive" -> "in:archive "
            "spam" -> "in:spam "
            "starred" -> "is:starred "
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

    fun mail_detail_for(email_id: String) = "mail_detail/$email_id"
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
    }

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
            if (targetState.destination.route?.startsWith("compose") == true) {
                androidx.compose.animation.ExitTransition.None
            } else {
                nav_forward_exit(nav_duration)
            }
        },
        popEnterTransition = {
            if (initialState.destination.route?.startsWith("compose") == true) {
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
                    nav_controller.navigate(routes.welcome) {
                        popUpTo(routes.onboarding) { inclusive = true }
                    }
                    nav_controller.navigate(routes.sign_in)
                },
                on_create_account = {
                    theme_vm.mark_onboarding_seen()
                    nav_controller.navigate(routes.welcome) {
                        popUpTo(routes.onboarding) { inclusive = true }
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
                on_register = { nav_controller.navigate(routes.register) },
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
                on_sign_in = { nav_controller.navigate(routes.sign_in) },
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
            MailDetailScreen(
                email_id = email_id,
                on_back = { nav_controller.popBackStack() },
                on_reply = { msg_id, ghost -> nav_controller.navigate(routes.compose_reply(msg_id, "reply", ghost)) },
                on_reply_all = { msg_id, ghost -> nav_controller.navigate(routes.compose_reply(msg_id, "reply_all", ghost)) },
                on_forward = { msg_id, ghost -> nav_controller.navigate(routes.compose_reply(msg_id, "forward", ghost)) },
                on_archive = { nav_controller.popBackStack() },
                on_delete = { nav_controller.popBackStack() },
                on_navigate = { path ->
                    val route = when {
                        path.startsWith("settings/") -> routes.settings_detail(path.removePrefix("settings/"))
                        path == "settings" -> routes.settings
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
                on_open_email = { eid -> nav_controller.navigate(routes.mail_detail_for(eid)) },
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
                on_open_email = { eid -> nav_controller.navigate(routes.mail_detail_for(eid)) },
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
                on_open_email = { eid -> nav_controller.navigate(routes.mail_detail_for(eid)) },
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
            ),
        ) { entry ->
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
            ComposeScreen(
                on_back = { nav_controller.popBackStack() },
                on_sent = { nav_controller.popBackStack() },
                reply_to = reply_to,
                mode = mode,
                draft_id = draft_id,
                prefill_to = prefill_to,
                thread_ghost_email = thread_ghost_email,
            )
        }
        composable(routes.search) {
            SearchScreen(
                on_back = { nav_controller.popBackStack() },
                on_open_email = { id -> nav_controller.navigate(routes.mail_detail_for(id)) },
            )
        }
        composable(
            route = routes.search_with_query,
            arguments = listOf(androidx.navigation.navArgument("q") { defaultValue = "" }),
        ) { entry ->
            val q = entry.arguments?.getString("q").orEmpty()
            SearchScreen(
                on_back = { nav_controller.popBackStack() },
                on_open_email = { id -> nav_controller.navigate(routes.mail_detail_for(id)) },
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
            MailingListsScreen(on_back = { nav_controller.popBackStack(); Unit })
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
            ContactDetailScreen(
                contact_id = id,
                on_back = { nav_controller.popBackStack() },
                on_edit = { cid -> nav_controller.navigate(routes.contact_edit_for(cid)) },
                on_compose = { email -> nav_controller.navigate(routes.compose_new(to = email)) },
            )
        }
        composable(routes.contact_edit_new) {
            ContactEditScreen(
                contact_id = null,
                on_back = { nav_controller.popBackStack() },
                on_saved = { nav_controller.popBackStack() },
            )
        }
        composable(
            route = routes.contact_edit,
            arguments = listOf(navArgument("contact_id") { type = NavType.StringType }),
        ) { entry ->
            val id = entry.arguments?.getString("contact_id")
            ContactEditScreen(
                contact_id = id,
                on_back = { nav_controller.popBackStack() },
                on_saved = { nav_controller.popBackStack() },
            )
        }

        val back = { nav_controller.popBackStack() }
        val open_detail: (String) -> Unit = { id -> nav_controller.navigate(routes.settings_detail(id)) }

        composable(routes.settings_detail("appearance")) {
            AppearanceScreen(on_back = { back(); Unit })
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
            AppearanceScreen(on_back = { back(); Unit })
        }
        composable(routes.settings_detail("text_size")) {
            AppearanceScreen(on_back = { back(); Unit })
        }
        composable(routes.settings_detail("aliases")) {
            AliasesScreen(on_back = { back(); Unit })
        }
        composable(routes.settings_detail("subscriptions")) {
            MailingListsScreen(on_back = { back(); Unit })
        }
        composable(routes.settings_detail("storage")) {
            StorageScreen(on_back = { back(); Unit }, on_open = open_detail)
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
            FamilyScreen(on_back = { back(); Unit })
        }
        composable(routes.settings_detail("connection")) {
            ConnectionScreen(on_back = { back(); Unit })
        }
        composable(routes.settings_detail("about")) {
            AboutScreen(on_back = { back(); Unit })
        }
        composable(routes.settings_detail("developer")) {
            DeveloperScreen(on_back = { back(); Unit })
        }
    }

    if (is_locked && is_signed_in_state) {
        AppLockScreen(
            store = lock_vm.store,
            on_sign_out = {
                nav_scope.launch {
                    auth_gate.auth_repository.logout()
                    nav_controller.navigate(routes.welcome) {
                        popUpTo(0) { inclusive = true }
                    }
                }
            },
        )
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
        settings_vm.load_tags()
        settings_vm.load_aliases()
        settings_vm.load_preferences()
        mail_vm.load_stats()
    }

    androidx.compose.runtime.LaunchedEffect(drawer_state.isOpen) {
        if (drawer_state.isOpen) {
            settings_vm.load_labels()
            settings_vm.load_tags()
        }
    }

    val prefs = settings_state.preferences
    val theme_vm_inbox: ThemeViewModel = hiltViewModel()

    androidx.compose.runtime.LaunchedEffect(prefs) {
        if (prefs == null) return@LaunchedEffect
        theme_vm_inbox.set_high_contrast(prefs.high_contrast)
        theme_vm_inbox.set_reduce_transparency(prefs.reduce_transparency)
        theme_vm_inbox.set_reduce_motion(prefs.reduce_motion)
        theme_vm_inbox.set_compact_mode(prefs.compact_mode)
        theme_vm_inbox.set_text_spacing(prefs.text_spacing)
        theme_vm_inbox.set_underline_links(prefs.underline_links)
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

    val api_folders = settings_state.labels
        .filter { !it.is_system }
        .filter { it.folder_type == "folder" || it.folder_type == "custom" }
        .map { label ->
            val readable_name = label.encrypted_name?.takeIf { it.isNotBlank() && !looks_encrypted(it) }
            drawer_folder_item(
                id = label.label_token,
                label = readable_name ?: drawer_context.getString(R.string.folder_decrypt_failed),
                icon = Icons.Outlined.Folder,
                count = label.item_count?.toInt() ?: 0,
            )
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
            )
        }

    ModalNavigationDrawer(
        drawerState = drawer_state,
        scrimColor = Color.Black.copy(alpha = if (colors.is_dark) 0.5f else 0.32f),
        drawerContent = {
            ModalDrawerSheet(
                drawerContainerColor = colors.sidebar_bg,
                drawerTonalElevation = 0.dp,
                drawerShape = androidx.compose.foundation.shape.RoundedCornerShape(0.dp),
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
                        id == "referral" -> nav_controller.navigate(routes.settings_detail("referral"))
                        id in mail_folder_ids -> { filter_kind = null; selected_folder = id; scope.launch { drawer_state.close() } }
                        else -> { filter_kind = null; selected_folder = id; scope.launch { drawer_state.close() } }
                    }
                },
                on_close = { scope.launch { drawer_state.close() } },
                on_navigate_folder = { id, name ->
                    filter_kind = "folder"
                    filter_value = id
                    filter_name = name
                    selected_folder = id
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
                on_navigate_alias = { _, name ->
                    scope.launch { drawer_state.close() }
                    nav_controller.navigate(routes.search_for("to:$name"))
                },
                inbox_unread = stats?.unread ?: 0,
                drafts_count = stats?.drafts ?: 0,
                spam_count = stats?.spam ?: 0,
                trash_count = stats?.trash ?: 0,
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
                on_create_folder = { name ->
                    settings_vm.create_folder(name = name, sort_order = api_folders.size)
                },
                on_logout = {
                    settings_vm.logout()
                    accounts_vm.refresh()
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
            filter_kind != null -> "filter:$filter_kind:$filter_value"
            selected_folder == "subscriptions" -> "subscriptions"
            selected_folder == "contacts" -> "contacts"
            else -> "inbox:$selected_folder"
        }
        val saveable_state_holder = rememberSaveableStateHolder()
        AnimatedContent(
            targetState = folder_key,
            transitionSpec = {
                fadeIn(animationSpec = tween(120)) togetherWith fadeOut(animationSpec = tween(80))
            },
            label = "folder_switch",
        ) { active_key ->
            saveable_state_holder.SaveableStateProvider(active_key) {
                when {
                    filter_kind != null -> {
                        BackHandler(enabled = true) {
                            filter_kind = null
                            filter_value = ""
                            filter_name = ""
                            selected_folder = "inbox"
                        }
                        val effective_folder = when (filter_kind) {
                            "label" -> "label:$filter_value"
                            "tag" -> "tag:$filter_value"
                            "folder" -> filter_value
                            else -> "inbox"
                        }
                        InboxScreen(
                            on_open_drawer = { scope.launch { drawer_state.open() } },
                            on_open_search = { nav_controller.navigate(routes.search_for_folder(effective_folder)) },
                            on_compose = { nav_controller.navigate(routes.compose_new()) },
                            on_compose_draft = { id -> nav_controller.navigate(routes.compose_draft(id)) },
                            on_view_pending_send = { nav_controller.navigate(routes.pending_send_preview) },
                            on_open_email = { id -> nav_controller.navigate(routes.mail_detail_for(id)) },
                            on_open_settings = { nav_controller.navigate(routes.settings) },
                            on_open_upgrade = { nav_controller.navigate(routes.settings_detail("billing")) },
                            current_folder = effective_folder,
                            display_title = filter_name,
                            on_folder_change = { id ->
                                filter_kind = null
                                selected_folder = id
                            },
                        )
                    }
                    selected_folder == "subscriptions" -> {
                        BackHandler(enabled = true) { selected_folder = "inbox" }
                        MailingListsScreen(
                            on_open_drawer = { scope.launch { drawer_state.open() } },
                        )
                    }
                    selected_folder == "contacts" -> {
                        BackHandler(enabled = true) { selected_folder = "inbox" }
                        ContactsScreen(
                            on_open_drawer = { scope.launch { drawer_state.open() } },
                            on_open_contact = { id -> nav_controller.navigate(routes.contact_detail_for(id)) },
                            on_create_contact = { nav_controller.navigate(routes.contact_edit_new) },
                        )
                    }
                    else -> {
                        InboxScreen(
                            on_open_drawer = { scope.launch { drawer_state.open() } },
                            on_open_search = { nav_controller.navigate(routes.search_for_folder(selected_folder)) },
                            on_compose = { nav_controller.navigate(routes.compose_new()) },
                            on_compose_draft = { id -> nav_controller.navigate(routes.compose_draft(id)) },
                            on_view_pending_send = { nav_controller.navigate(routes.pending_send_preview) },
                            on_open_email = { id ->
                                if (selected_folder == "drafts") {
                                    nav_controller.navigate(routes.compose_draft(id))
                                } else {
                                    nav_controller.navigate(routes.mail_detail_for(id))
                                }
                            },
                            on_open_settings = { nav_controller.navigate(routes.settings) },
                            on_open_upgrade = { nav_controller.navigate(routes.settings_detail("billing")) },
                            current_folder = selected_folder,
                            on_folder_change = { selected_folder = it },
                        )
                    }
                }
            }
        }
    }
}

internal fun looks_encrypted(value: String?): Boolean {
    if (value.isNullOrBlank()) return false
    if (value.length < 20) return false
    val base64_chars = value.count { it in "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789+/=" }
    return base64_chars.toFloat() / value.length > 0.85f
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
