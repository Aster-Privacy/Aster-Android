//
// Aster Communications Inc.
//
// Copyright (c) 2026 Aster Communications Inc.
//
// This file is part of this project.
//
// This program is free software: you can redistribute it and/or modify
// it under the terms of the AGPLv3 as published by
// the Free Software Foundation, either version 3 of the License, or
// (at your option) any later version.
//
// This program is distributed in the hope that it will be useful,
// but WITHOUT ANY WARRANTY; without even the implied warranty of
// MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
// AGPLv3 for more details.
//
// You should have received a copy of the AGPLv3
// along with this program. If not, see <https://www.gnu.org/licenses/>.
//
package org.astermail.android.ui.guard.screenshots

import android.app.Application
import android.app.Notification
import android.app.NotificationManager
import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.unit.dp
import androidx.test.core.app.ApplicationProvider
import java.io.File
import java.security.SecureRandom
import java.time.Instant
import java.time.temporal.ChronoUnit
import org.astermail.android.R
import org.astermail.android.api.guard.GuardDeviceInfo
import org.astermail.android.api.guard.GuardHoldScope
import org.astermail.android.api.guard.GuardPanicResult
import org.astermail.android.api.guard.GuardStatus
import org.astermail.android.api.guard.GuardSwitch
import org.astermail.android.api.guard.GuardThisDevice
import org.astermail.android.design.AsterMaterial
import org.astermail.android.design.AsterTheme
import org.astermail.android.guard.GuardHeldBanner
import org.astermail.android.guard.guard_context_view
import org.astermail.android.guard.guard_event
import org.astermail.android.guard.guard_held_notice
import org.astermail.android.guard.guard_hold_kind
import org.astermail.android.guard.guard_push
import org.astermail.android.notifications.LoginAlertNotifier
import org.astermail.android.ui.guard.GUARD_ALL_SCOPES
import org.astermail.android.ui.guard.GUARD_DEFAULT_SWITCHES
import org.astermail.android.ui.guard.generate_rescue_code
import org.astermail.android.ui.guard.guard_approve_notice
import org.astermail.android.ui.guard.guard_approve_screen
import org.astermail.android.ui.guard.guard_approve_state
import org.astermail.android.ui.guard.guard_change_row
import org.astermail.android.ui.guard.guard_changes_screen
import org.astermail.android.ui.guard.guard_changes_ui
import org.astermail.android.ui.guard.guard_code_screen
import org.astermail.android.ui.guard.guard_code_view
import org.astermail.android.ui.guard.guard_entry_ui
import org.astermail.android.ui.guard.guard_action_notice
import org.astermail.android.ui.guard.guard_panic_screen
import org.astermail.android.ui.guard.guard_panic_state
import org.astermail.android.ui.guard.guard_security_row
import org.astermail.android.ui.guard.guard_setup_screen
import org.astermail.android.ui.guard.guard_setup_step
import org.astermail.android.ui.guard.guard_setup_ui
import org.astermail.android.ui.guard.guard_switches_screen
import org.astermail.android.ui.guard.guard_switches_ui
import org.astermail.android.ui.guard.guard_resume_block
import org.astermail.android.ui.guard.guard_wait_state
import org.astermail.android.ui.guard.guard_waiting_screen
import org.astermail.android.ui.guard.guard_veto_screen
import org.astermail.android.ui.guard.guard_veto_state
import org.astermail.android.ui.guard.local_guard_clock
import org.astermail.android.ui.common.TopToastState
import org.astermail.android.ui.common.top_toast_overlay
import org.astermail.android.ui.settings.detail.detail_scaffold
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.ui.res.pluralStringResource
import compose.icons.TablerIcons
import compose.icons.tablericons.At
import compose.icons.tablericons.BellRinging
import compose.icons.tablericons.Devices
import compose.icons.tablericons.Key
import compose.icons.tablericons.Lock
import compose.icons.tablericons.Plus
import compose.icons.tablericons.ShieldCheck
import org.astermail.android.design.AsterSpacing
import org.astermail.android.design.components.AsterCard
import org.astermail.android.design.components.AsterSwitch
import org.astermail.android.ui.settings.detail.detail_row
import org.astermail.android.ui.settings.detail.section_label
import org.astermail.android.ui.settings.detail.settings_row_gap
import org.astermail.android.ui.settings.detail.v_gap
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.ParameterizedRobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

private val typed_text = "a".repeat(13)

@RunWith(ParameterizedRobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35], qualifiers = "w411dp-h891dp-xxhdpi", application = Application::class)
class GuardScreenshotTest(private val dark: Boolean) {

    companion object {
        @JvmStatic
        @ParameterizedRobolectricTestRunner.Parameters(name = "dark={0}")
        fun themes(): List<Array<Any>> = listOf(arrayOf(false), arrayOf(true))
    }

    @get:Rule
    val compose_rule = createComposeRule()

    private val now: Instant = Instant.now().truncatedTo(ChronoUnit.MINUTES)
    private val rescue_code: String = generate_rescue_code(SecureRandom())

    private fun out_dir(): File = File(System.getProperty("user.dir"), "build/screenshots/guard").apply { mkdirs() }

    private fun save(name: String, bitmap: Bitmap) {
        val file = File(out_dir(), "${name}_${if (dark) "dark" else "light"}.png")
        file.outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
    }

    private fun shoot(name: String, content: @Composable () -> Unit) {
        var root: View? = null
        compose_rule.mainClock.autoAdvance = false
        compose_rule.setContent {
            root = LocalView.current
            AsterTheme(use_dark_theme = dark) {
                Box(Modifier.fillMaxSize().background(AsterMaterial.colors.bg_primary)) {
                    content()
                }
            }
        }
        compose_rule.mainClock.advanceTimeBy(1_500)
        val bitmap = compose_rule.runOnIdle {
            val view = requireNotNull(root).rootView
            Bitmap.createBitmap(view.width, view.height, Bitmap.Config.ARGB_8888).also { view.draw(Canvas(it)) }
        }
        save(name, bitmap)
    }

    @Composable
    private fun setup(state: guard_setup_ui) = guard_setup_screen(
        state = state,
        on_start = {},
        on_toggle = {},
        on_toggle_scope = {},
        on_to_rescue = {},
        on_save_file = {},
        on_print = {},
        on_copy = {},
        on_to_confirm = {},
        on_confirm_input = {},
        on_confirm = {},
        on_password = {},
        on_totp = {},
        on_submit = {},
        on_join = {},
        on_restart_join = {},
        on_retry = {},
        on_step_back = {},
        on_close = {},
    )

    @Test
    fun setup_01_intro() = shoot("setup_01_intro") { setup(guard_setup_ui(step = guard_setup_step.intro)) }

    @Test
    fun setup_02_choose() = shoot("setup_02_choose") { setup(guard_setup_ui(step = guard_setup_step.choose)) }

    @Test
    fun setup_03_rescue_code() = shoot("setup_03_rescue_code") {
        setup(guard_setup_ui(step = guard_setup_step.rescue, rescue_code = rescue_code))
    }

    @Test
    fun setup_04_confirm_rescue() = shoot("setup_04_confirm_rescue") {
        setup(guard_setup_ui(step = guard_setup_step.confirm_rescue, rescue_code = rescue_code, confirm_input = "7K"))
    }

    @Test
    fun setup_05_password() = shoot("setup_05_password") {
        setup(guard_setup_ui(step = guard_setup_step.password, rescue_code = rescue_code, password = typed_text))
    }

    @Test
    fun setup_06_done() = shoot("setup_06_done") { setup(guard_setup_ui(step = guard_setup_step.done)) }

    @Test
    fun setup_07_join_waiting() = shoot("setup_07_join_waiting") {
        setup(
            guard_setup_ui(
                step = guard_setup_step.join_waiting,
                join_number = 47,
                join_expires_at = now.plus(5, ChronoUnit.MINUTES).toString(),
            ),
        )
    }

    @Test
    fun waiting_01_number() = shoot("waiting_01_number") {
        guard_waiting_screen(state = guard_wait_state.waiting(number = 47, seconds_left = 241), on_submit_code = {}, on_back = {})
    }

    @Test
    fun waiting_02_held() = shoot("waiting_02_held") {
        guard_waiting_screen(
            state = guard_wait_state.held(now.plus(3, ChronoUnit.DAYS).toString()),
            on_submit_code = {},
            on_back = {},
        )
    }

    @Test
    fun waiting_03_code_open() = shoot("waiting_03_code_open") {
        guard_waiting_screen(
            state = guard_wait_state.waiting(number = 47, seconds_left = 241),
            on_submit_code = {},
            on_back = {},
            on_rescue = {},
            initial_show_code = true,
        )
    }

    @Test
    fun waiting_04_denied() = shoot("waiting_04_denied") {
        guard_waiting_screen(state = guard_wait_state.denied, on_submit_code = {}, on_back = {})
    }

    @Test
    fun waiting_05_resume_unlock() = shoot("waiting_05_resume_unlock") {
        guard_waiting_screen(
            state = guard_wait_state.resume_blocked(guard_resume_block.unlock),
            on_submit_code = {},
            on_back = {},
            on_retry_resume = {},
        )
    }

    @Test
    fun waiting_06_resume_network() = shoot("waiting_06_resume_network") {
        guard_waiting_screen(
            state = guard_wait_state.resume_blocked(guard_resume_block.network),
            on_submit_code = {},
            on_back = {},
            on_retry_resume = {},
        )
    }

    @Test
    fun setup_08_failed() = shoot("setup_08_failed") { setup(guard_setup_ui(step = guard_setup_step.failed)) }

    @Test
    fun setup_09_join_denied() = shoot("setup_09_join_denied") { setup(guard_setup_ui(step = guard_setup_step.join_denied)) }

    @Test
    fun veto_01_confirm() = shoot("veto_01_confirm") {
        guard_veto_screen(state = guard_veto_state.confirm, on_stop = {}, on_close = {})
    }

    @Test
    fun veto_02_done() = shoot("veto_02_done") {
        guard_veto_screen(state = guard_veto_state.done, on_stop = {}, on_close = {}, on_change_password = {})
    }

    @Test
    fun toast_01_centered() = shoot("toast_01_centered") {
        top_toast_overlay(
            state = TopToastState(message = stringResource(R.string.guard_security_row_on), duration_ms = 60_000L),
            on_dismiss = {},
        )
    }

    private fun ready(input: String, notice: guard_approve_notice? = null) = guard_approve_state.ready(
        view = guard_context_view(
            device_type = "desktop",
            browser = "Firefox 140",
            os = "Windows 11",
            city = "Berlin",
            country = "DE",
            requested_at = now.toString(),
        ),
        kind = "sign_in",
        device_name = "Windows 11 · Firefox 140",
        created_at = now.toString(),
        number_input = input,
        notice = notice,
    )

    @Composable
    private fun approve(state: guard_approve_state) = guard_approve_screen(
        state = state,
        on_number = {},
        on_approve = {},
        on_deny = {},
        on_retry_unlocked = {},
        on_close = {},
        auto_close = false,
    )

    @Test
    fun approve_01_empty() = shoot("approve_01_empty") { approve(ready("")) }

    @Test
    fun approve_02_filled() = shoot("approve_02_filled") { approve(ready("47")) }

    @Test
    fun approve_03_mismatched() = shoot("approve_03_mismatched") {
        approve(guard_approve_state.mismatched)
    }

    @Test
    fun approve_04_biometric_step() = shoot("approve_04_biometric_step") { approve(guard_approve_state.signing) }

    @Test
    fun approve_05_unlock_first() = shoot("approve_05_unlock_first") { approve(guard_approve_state.unlock_first) }

    @Test
    fun approve_07_denied() = shoot("approve_07_denied") {
        guard_approve_screen(
            state = guard_approve_state.denied,
            on_number = {},
            on_approve = {},
            on_deny = {},
            on_retry_unlocked = {},
            on_close = {},
            auto_close = false,
            on_secure = {},
        )
    }

    @Test
    fun approve_08_expires_soon() = shoot("approve_08_expires_soon") {
        CompositionLocalProvider(local_guard_clock provides { now.toEpochMilli() }) {
            approve(ready("47").copy(expires_at = now.plusSeconds(45).toString()))
        }
    }

    @Test
    fun approve_06_approved() = shoot("approve_06_approved") { approve(guard_approve_state.approved) }

    @Test
    fun code_01_hidden() = shoot("code_01_hidden") {
        guard_code_screen(view = guard_code_view("482913", 17, false), revealed = false, on_reveal = {}, on_back = {}, on_join = {})
    }

    @Test
    fun code_02_revealed() = shoot("code_02_revealed") {
        guard_code_screen(view = guard_code_view("482913", 17, false), revealed = true, on_reveal = {}, on_back = {}, on_join = {})
    }

    private fun status(switches: Int, guard_device: Boolean) = GuardStatus(
        switches = switches,
        hold_scopes = GUARD_ALL_SCOPES,
        version = 3,
        enrolled_at = now.minus(40, ChronoUnit.DAYS).toString(),
        has_rescue_code = true,
        this_device = if (guard_device) GuardThisDevice(id = "d1", long_trusted = true, approved_at = now.minus(40, ChronoUnit.DAYS).toString()) else null,
        devices = listOf(
            GuardDeviceInfo("d1", "Pixel 9", "android", now.minus(40, ChronoUnit.DAYS).toString(), true),
            GuardDeviceInfo("d2", "iPad Air", "ios", now.minus(2, ChronoUnit.DAYS).toString(), false),
        ),
    )

    @Composable
    private fun switches(state: guard_switches_ui) = guard_switches_screen(
        state = state,
        on_back = {},
        on_toggle = {},
        on_toggle_scope = {},
        on_save = {},
        on_discard = {},
        on_remove = {},
        on_retry = {},
        on_open = {},
    )

    @Test
    fun switches_01_off() = shoot("switches_01_off") {
        switches(guard_switches_ui(loading = false, status = GuardStatus()))
    }

    @Test
    fun switches_02_on() = shoot("switches_02_on") {
        val s = status(GUARD_DEFAULT_SWITCHES, true)
        switches(guard_switches_ui(loading = false, status = s, draft_switches = s.switches, draft_scopes = s.hold_scopes))
    }

    @Test
    fun switches_05_rescued() = shoot("switches_05_rescued") {
        guard_switches_screen(
            state = guard_switches_ui(loading = false, status = GuardStatus()),
            on_back = {},
            on_toggle = {},
            on_toggle_scope = {},
            on_save = {},
            on_discard = {},
            on_remove = {},
            on_retry = {},
            on_open = {},
            rescued = true,
        )
    }

    @Test
    fun switches_03_greyed() = shoot("switches_03_greyed") {
        val s = status(GUARD_DEFAULT_SWITCHES, false)
        switches(guard_switches_ui(loading = false, status = s, draft_switches = s.switches, draft_scopes = s.hold_scopes))
    }

    @Test
    fun switches_04_held() = shoot("switches_04_held") {
        val s = status(GUARD_DEFAULT_SWITCHES or GuardSwitch.RECOVERY_WAIT, true)
        switches(
            guard_switches_ui(
                loading = false,
                status = s,
                draft_switches = s.switches,
                draft_scopes = s.hold_scopes,
                pending_off_switches = GuardSwitch.HOLD_CHANGES,
                pending_off_scopes = GuardHoldScope.FORWARDING_RULES,
                held_notice = guard_held_notice("c9", guard_hold_kind.guard_switches_off, now.plus(3, ChronoUnit.DAYS)),
            ),
        )
    }

    @Test
    fun changes_01_list() = shoot("changes_01_list") {
        guard_changes_screen(
            state = guard_changes_ui(
                loading = false,
                is_guard_device = true,
                long_trusted = true,
                changes = listOf(
                    guard_change_row("c1", guard_hold_kind.forwarding_change, now.plus(3, ChronoUnit.DAYS), now),
                    guard_change_row("c2", guard_hold_kind.password_change, now.plus(2, ChronoUnit.DAYS), now.minus(1, ChronoUnit.DAYS)),
                    guard_change_row("c3", guard_hold_kind.account_recovery, now.plus(7, ChronoUnit.DAYS), now),
                ),
            ),
            on_back = {},
            on_cancel = {},
            on_skip = {},
            on_stop = {},
            on_retry = {},
            live = false,
        )
    }

    @Test
    fun changes_02_empty() = shoot("changes_02_empty") {
        guard_changes_screen(
            state = guard_changes_ui(loading = false, is_guard_device = true),
            on_back = {},
            on_cancel = {},
            on_skip = {},
            on_stop = {},
            on_retry = {},
            live = false,
        )
    }

    @Test
    fun held_01_password_banner() = shoot("held_01_password_banner") {
        detail_scaffold(title = stringResource(R.string.change_password), on_back = {}) {
            GuardHeldBanner(guard_held_notice("c2", guard_hold_kind.password_change, now.plus(3, ChronoUnit.DAYS)))
        }
    }

    @Test
    fun held_02_forwarding_banner() = shoot("held_02_forwarding_banner") {
        detail_scaffold(title = stringResource(R.string.auto_forward_title), on_back = {}) {
            GuardHeldBanner(guard_held_notice("c1", guard_hold_kind.forwarding_change, now.plus(3, ChronoUnit.DAYS)))
        }
    }

    @Test
    fun settings_01_security_row() = shoot("settings_01_security_row") {
        detail_scaffold(title = stringResource(R.string.security), on_back = {}) {
            section_label(stringResource(R.string.section_authentication))
            AsterCard(modifier = Modifier.fillMaxWidth()) {
                detail_row(
                    title = stringResource(R.string.change_password),
                    subtitle = stringResource(R.string.change_password_subtitle),
                    icon = TablerIcons.Lock,
                    on_click = {},
                )
                settings_row_gap()
                detail_row(
                    title = stringResource(R.string.two_factor_auth),
                    subtitle = stringResource(R.string.enabled),
                    icon = TablerIcons.ShieldCheck,
                    on_click = {},
                )
                settings_row_gap()
                guard_security_row(on_open = {}, entry = guard_entry_ui(visible = true, on = true, is_guard_device = true))
                settings_row_gap()
                detail_row(
                    title = stringResource(R.string.login_alerts),
                    subtitle = stringResource(R.string.login_alerts_subtitle),
                    icon = TablerIcons.BellRinging,
                    trailing = { AsterSwitch(checked = true, onCheckedChange = {}) },
                )
                settings_row_gap()
                detail_row(
                    title = stringResource(R.string.active_sessions),
                    subtitle = stringResource(R.string.devices_signed_in),
                    icon = TablerIcons.Devices,
                    on_click = {},
                )
                settings_row_gap()
                detail_row(
                    title = stringResource(R.string.passkeys_security_keys),
                    subtitle = pluralStringResource(R.plurals.passkeys_registered_count, 1, 1),
                    icon = TablerIcons.Key,
                )
                settings_row_gap()
                detail_row(
                    title = stringResource(R.string.passkey_add),
                    subtitle = stringResource(R.string.passkey_add_subtitle),
                    icon = TablerIcons.Plus,
                    on_click = {},
                )
            }
            v_gap(AsterSpacing.lg)
            section_label(stringResource(R.string.section_recovery_security))
            AsterCard(modifier = Modifier.fillMaxWidth()) {
                detail_row(
                    title = stringResource(R.string.recovery_codes),
                    subtitle = stringResource(R.string.enabled),
                    icon = TablerIcons.Key,
                    on_click = {},
                )
                settings_row_gap()
                detail_row(
                    title = stringResource(R.string.recovery_email),
                    subtitle = stringResource(R.string.enabled),
                    icon = TablerIcons.At,
                    on_click = {},
                )
            }
        }
    }

    @Composable
    private fun panic(state: guard_panic_state) = guard_panic_screen(
        state = state,
        on_back = {},
        on_confirm = {},
        on_retry = {},
        on_change_password = {},
    )

    @Test
    fun panic_01_confirm() = shoot("panic_01_confirm") { panic(guard_panic_state.confirm()) }

    @Test
    fun panic_02_working() = shoot("panic_02_working") { panic(guard_panic_state.working) }

    @Test
    fun panic_03_done_password() = shoot("panic_03_done_password") {
        panic(guard_panic_state.done(GuardPanicResult(3, 1, 2, can_reset_password_now = true)))
    }

    @Test
    fun panic_04_done() = shoot("panic_04_done") {
        panic(guard_panic_state.done(GuardPanicResult(1, 0, 1, can_reset_password_now = false)))
    }

    @Test
    fun panic_05_failed() = shoot("panic_05_failed") { panic(guard_panic_state.failed(guard_action_notice.failed)) }

    @Test
    fun notification_01_sign_in_request() {
        val context = ApplicationProvider.getApplicationContext<Application>()
        shadowOf(context).grantPermissions(android.Manifest.permission.POST_NOTIFICATIONS)
        LoginAlertNotifier.show_guard(
            context,
            guard_push(guard_event.sign_in_request, "0b6e2c1a-4f3d-4e5a-9b8c-7d6e5f4a3b2c", "acct"),
        )
        val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        val posted = requireNotNull(shadowOf(manager).allNotifications.lastOrNull())
        save("notification_01_sign_in_request", render_notification(context, posted))
    }

    private fun render_notification(context: Context, notification: Notification): Bitmap {
        val themed = android.view.ContextThemeWrapper(
            context,
            if (dark) android.R.style.Theme_DeviceDefault else android.R.style.Theme_DeviceDefault_Light,
        )
        val builder = Notification.Builder.recoverBuilder(themed, notification)
        val remote = builder.createBigContentView() ?: builder.createContentView()
        val frame = FrameLayout(themed)
        frame.setBackgroundColor(if (dark) Color.rgb(32, 33, 36) else Color.WHITE)
        frame.addView(remote.apply(themed, frame), ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT))
        val width = (context.resources.displayMetrics.widthPixels)
        frame.measure(
            View.MeasureSpec.makeMeasureSpec(width, View.MeasureSpec.EXACTLY),
            View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED),
        )
        frame.layout(0, 0, frame.measuredWidth, frame.measuredHeight)
        return Bitmap.createBitmap(frame.measuredWidth, maxOf(1, frame.measuredHeight), Bitmap.Config.ARGB_8888)
            .also { frame.draw(Canvas(it)) }
    }
}
