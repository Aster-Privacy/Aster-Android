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

package org.astermail.android.ui.settings.detail

import compose.icons.TablerIcons
import org.astermail.android.design.acrylic
import org.astermail.android.design.readable_on
import org.astermail.android.ui.common.show_copy_failed_toast
import org.astermail.android.ui.common.write_to_clipboard
import compose.icons.tablericons.*

import android.content.ClipData
import android.content.Context
import android.content.Intent
import android.util.Base64
import android.widget.Toast
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CheckboxDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.FileProvider
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.android.EntryPointAccessors
import dagger.hilt.components.SingletonComponent
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.astermail.android.R
import org.astermail.android.api.preferences.UserPreferences
import org.astermail.android.auth.AuthRepository
import org.astermail.android.crypto.CryptoNative
import org.astermail.android.design.AsterDuration
import org.astermail.android.design.AsterEasing
import org.astermail.android.design.AsterMaterial
import org.astermail.android.design.AsterRadius
import org.astermail.android.design.AsterShapes
import org.astermail.android.design.field_surface_color
import org.astermail.android.design.island_surface_color
import org.astermail.android.design.AsterSpacing
import org.astermail.android.design.SquircleShape
import org.astermail.android.design.components.AsterCard
import org.astermail.android.design.components.AsterIconButton
import org.astermail.android.design.components.AsterSecondaryButton
import org.astermail.android.design.components.shimmer_state
import org.astermail.android.design.components.skeleton_reveal
import org.astermail.android.design.mirror_in_rtl
import org.astermail.android.settings.SettingsViewModel
import org.astermail.android.settings.encryption_display_cache
import org.astermail.android.settings.encryption_display_snapshot
import org.astermail.android.settings.encryption_display_snapshot_of
import org.astermail.android.settings.encryption_identity_display
import org.astermail.android.storage.SessionKeyStore
import java.io.File
import java.security.MessageDigest
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.math.roundToInt
import org.astermail.android.settings.shared_settings_view_model
import org.astermail.android.ui.settings.join_launched

@EntryPoint
@InstallIn(SingletonComponent::class)
private interface EncryptionScreenDeps {
    fun session_key_store(): SessionKeyStore
    fun auth_repository(): AuthRepository
}

private const val encryption_cap_ratio = 0.73f
private const val fingerprint_placeholder_lines = 4
private val fingerprint_line_height = 20.dp
private val key_meta_line_height = 18.dp
private val recovery_summary_height = 24.dp

private enum class encryption_hero_state { unknown, protected, attention }

private suspend fun compute_identity_display(
    session_key_store: SessionKeyStore,
    auth_repository: AuthRepository,
): encryption_identity_display = withContext(Dispatchers.IO) {
    if (session_key_store.get_identity_key() == null) {
        auth_repository.try_recover_identity_key()
    }
    val stored = session_key_store.get_identity_key()
    if (stored.isNullOrBlank()) {
        encryption_identity_display(fingerprint = null, is_pgp = false, available = false)
    } else if (stored.contains("BEGIN PGP")) {
        val fp = runCatching {
            val digest = MessageDigest.getInstance("SHA-256")
                .digest(stored.toByteArray(Charsets.UTF_8))
            digest.joinToString("") { String.format(Locale.US, "%02X", it.toInt() and 0xFF) }
        }.getOrNull()
        encryption_identity_display(fingerprint = fp, is_pgp = true, available = true)
    } else {
        val pub_b64 = runCatching {
            val private_bytes = Base64.decode(stored, Base64.DEFAULT)
            val public_bytes = CryptoNative.derive_identity_public_key(private_bytes)
            private_bytes.fill(0)
            Base64.encodeToString(public_bytes, Base64.NO_WRAP)
        }.getOrNull()
        val fp = pub_b64?.let {
            runCatching {
                CryptoNative.fingerprint_hex(Base64.decode(it, Base64.NO_WRAP))
                    .filter { ch -> !ch.isWhitespace() }
            }.getOrNull()
        }
        encryption_identity_display(fingerprint = fp, is_pgp = false, available = pub_b64 != null)
    }
}

@Composable
fun EncryptionScreen(
    on_back: () -> Unit,
    on_open: (id: String) -> Unit = {},
) {
    org.astermail.android.ui.common.secure_screen()
    val colors = AsterMaterial.colors
    val context = LocalContext.current
    val vm: SettingsViewModel = shared_settings_view_model()
    val state by vm.state.collectAsStateWithLifecycle()
    val scope = rememberCoroutineScope()

    val deps = remember(context) {
        EntryPointAccessors.fromApplication(
            context.applicationContext,
            EncryptionScreenDeps::class.java,
        )
    }
    val session_key_store = remember(deps) { deps.session_key_store() }
    val auth_repository = remember(deps) { deps.auth_repository() }
    val account_key = remember(session_key_store) { session_key_store.get_user_id() }
    val cached = remember(account_key) {
        encryption_display_cache.memo.read(account_key) ?: encryption_display_snapshot()
    }

    LaunchedEffect(state.action_result) {
        val msg = state.action_result ?: return@LaunchedEffect
        Toast.makeText(context, msg, Toast.LENGTH_SHORT).show()
        vm.clear_action_result()
    }

    LaunchedEffect(Unit) {
        vm.load_pgp_key_info()
        vm.load_recovery_codes_status()
        vm.load_encryption_settings()
        vm.load_wkd_keyserver_status()
        vm.load_external_pgp_key()
    }

    val identity by produceState(initialValue = cached.identity, deps) {
        val computed = compute_identity_display(session_key_store, auth_repository)
        value = computed
        encryption_display_cache.memo.write(account_key, encryption_display_snapshot(identity = computed))
    }

    val live_snapshot = encryption_display_snapshot_of(
        identity = null,
        pgp_key_info = state.pgp_key_info,
        recovery_codes_status = state.recovery_codes_status,
        encryption_settings = state.encryption_settings,
        wkd_status = state.wkd_status,
        keyserver_status = state.keyserver_status,
    )
    LaunchedEffect(live_snapshot) {
        encryption_display_cache.memo.write(account_key, live_snapshot)
    }

    val fingerprint = identity?.fingerprint
    val key_ready = identity?.available == true && fingerprint != null
    val prefs = state.preferences
    val hero_state = when {
        identity == null -> encryption_hero_state.unknown
        key_ready -> encryption_hero_state.protected
        else -> encryption_hero_state.attention
    }

    var show_export_private_dialog by remember { mutableStateOf(false) }
    var export_private_password by remember { mutableStateOf("") }
    var exporting_private_key by remember { mutableStateOf(false) }
    var export_private_error by remember { mutableStateOf<String?>(null) }
    var exporting_public_key by remember { mutableStateOf(false) }

    fun toggle(update: (UserPreferences) -> UserPreferences) {
        val current = prefs ?: return
        if (!state.preferences_authoritative) {
            vm.report_preferences_locked()
            return
        }
        vm.save_preferences(update(current))
    }

    fun with_public_key(on_ready: (String?) -> Unit) {
        if (exporting_public_key) return
        exporting_public_key = true
        scope.launch {
            val armored = try {
                vm.export_public_key_now()
            } finally {
                exporting_public_key = false
            }
            on_ready(armored)
        }
    }

    detail_scaffold(
        title = stringResource(R.string.encryption_title),
        on_back = on_back,
        on_refresh = {
            vm.join_launched {
                vm.load_pgp_key_info()
                vm.load_recovery_codes_status()
                vm.load_encryption_settings()
                vm.load_wkd_keyserver_status()
                vm.load_external_pgp_key()
            }
        },
        refresh_enabled = !exporting_private_key && state.save_status != org.astermail.android.settings.SaveStatus.SAVING,
    ) {

        preferences_save_error_banner()

        encryption_hero_card(state = hero_state)

        v_gap(AsterSpacing.lg)
        section_label(stringResource(R.string.enc_keys_section_title))

        if (identity == null || key_ready) {
            val live_key = state.pgp_key_info
            val algorithm = live_key?.algorithm ?: cached.key?.algorithm
            val key_size = live_key?.key_size ?: cached.key?.key_size ?: 0
            val created_at = (live_key?.created_at ?: cached.key?.created_at).orEmpty()
            val meta_parts = listOfNotNull(
                pgp_algorithm_label(algorithm, key_size),
                if (created_at.isNotBlank()) {
                    stringResource(R.string.created_at_format, absolute_date_label(created_at))
                } else {
                    null
                },
            )
            val meta_known = live_key != null || cached.key != null
            val meta_line = when {
                meta_parts.isNotEmpty() -> meta_parts.joinToString(" · ")
                !meta_known && !state.pgp_key_info_loaded -> null
                else -> ""
            }
            identity_key_card(
                fingerprint = fingerprint,
                meta_line = meta_line,
                exporting_public_key = exporting_public_key,
                on_copy_fingerprint = { grouped ->
                    if (copy_to_clipboard(context, context.getString(R.string.clipboard_label_identity_fingerprint), grouped)) {
                        Toast.makeText(context, context.getString(R.string.fingerprint_copied), Toast.LENGTH_SHORT).show()
                    } else {
                        show_copy_failed_toast(context)
                    }
                },
                on_share_public_key = {
                    with_public_key { armored ->
                        if (armored != null) {
                            share_public_key(context, armored)
                        } else {
                            Toast.makeText(context, context.getString(R.string.something_went_wrong), Toast.LENGTH_SHORT).show()
                        }
                    }
                },
                on_copy_public_key = {
                    with_public_key { armored ->
                        if (armored != null && copy_to_clipboard(context, context.getString(R.string.clipboard_label_identity_public_key), armored)) {
                            Toast.makeText(context, context.getString(R.string.public_key_copied), Toast.LENGTH_SHORT).show()
                        } else {
                            show_copy_failed_toast(context)
                        }
                    }
                },
                on_export_private_key = {
                    export_private_password = ""
                    export_private_error = null
                    show_export_private_dialog = true
                },
            )
        } else {
            identity_key_unavailable_card()
        }

        state.external_pgp_key?.let { external_key ->
            v_gap(AsterSpacing.lg)
            external_pgp_key_card(
                external_key = external_key,
                busy = state.external_pgp_key_busy,
                on_stop_publishing = { vm.stop_publishing_external_pgp_key() },
            )
        }

        v_gap(AsterSpacing.lg)
        section_label(stringResource(R.string.recovery_codes))
        val live_recovery = state.recovery_codes_status
        recovery_codes_card(
            available = live_recovery?.available_codes ?: cached.recovery?.available,
            total = live_recovery?.total_codes ?: cached.recovery?.total,
            load_failed = live_recovery == null && state.recovery_codes_status_load_failed,
            on_retry = { vm.load_recovery_codes_status() },
            on_manage = { on_open("recovery_codes") },
        )

        v_gap(AsterSpacing.lg)
        section_label(stringResource(R.string.encryption_behavior))
        val enc = state.encryption_settings
        val enc_failed = enc == null && state.encryption_settings_load_failed
        val enc_values = enc?.let {
            org.astermail.android.settings.encryption_settings_display(
                auto_discover_keys = it.auto_discover_keys,
                encrypt_by_default = it.encrypt_by_default,
                require_encryption = it.require_encryption,
            )
        } ?: cached.settings
        val wkd = state.wkd_status
        val wkd_failed = wkd == null && state.wkd_status_load_failed
        val wkd_published = wkd?.published ?: cached.wkd_published ?: if (wkd_failed) false else null
        val keyserver_published = state.keyserver_status?.published ?: cached.keyserver_published
        val keyserver_checked = prefs?.let { keyserver_published == true || it.publish_to_keyservers }

        fun on_enc_toggle(action: () -> Unit) {
            if (enc == null) vm.load_encryption_settings() else action()
        }

        AsterCard(modifier = Modifier.fillMaxWidth()) {
            if (enc_failed) {
                detail_row(
                    title = stringResource(R.string.failed_to_load),
                    subtitle = stringResource(R.string.retry),
                    on_click = { vm.load_encryption_settings() },
                )
                settings_row_gap()
            }
            encryption_toggle_row(
                title = stringResource(R.string.auto_discover_keys),
                subtitle = stringResource(R.string.auto_discover_keys_sub),
                info_title = stringResource(R.string.info_auto_discover_title),
                info_description = stringResource(R.string.info_auto_discover_desc),
                checked = enc_values?.auto_discover_keys ?: if (enc_failed) false else null,
                on_change = { on_enc_toggle { vm.toggle_auto_discover_keys() } },
            )
            settings_row_gap()
            encryption_toggle_row(
                title = stringResource(R.string.encrypt_by_default),
                subtitle = stringResource(R.string.encrypt_by_default_sub),
                info_title = stringResource(R.string.info_encrypt_default_title),
                info_description = stringResource(R.string.info_encrypt_default_desc),
                checked = enc_values?.encrypt_by_default ?: if (enc_failed) false else null,
                on_change = { on_enc_toggle { vm.toggle_encrypt_by_default() } },
            )
            settings_row_gap()
            encryption_toggle_row(
                title = stringResource(R.string.require_encryption),
                subtitle = stringResource(R.string.require_encryption_sub),
                info_title = stringResource(R.string.info_require_encryption_title),
                info_description = stringResource(R.string.info_require_encryption_desc),
                checked = enc_values?.require_encryption ?: if (enc_failed) false else null,
                on_change = { on_enc_toggle { vm.toggle_require_encryption() } },
            )
            settings_row_gap()
            encryption_toggle_row(
                title = stringResource(R.string.show_encryption_indicators),
                subtitle = stringResource(R.string.show_encryption_indicators_sub),
                info_title = stringResource(R.string.info_encryption_indicators_title),
                info_description = stringResource(R.string.info_encryption_indicators_desc),
                checked = prefs?.let { it.show_encryption_indicators != false },
                on_change = { toggle { it.copy(show_encryption_indicators = !it.show_encryption_indicators) } },
            )
            settings_row_gap()
            encryption_toggle_row(
                title = stringResource(R.string.publish_to_wkd),
                subtitle = if (wkd_failed) {
                    stringResource(R.string.failed_to_load)
                } else {
                    stringResource(R.string.publish_to_wkd_sub)
                },
                info_title = stringResource(R.string.info_wkd_title),
                info_description = stringResource(R.string.info_wkd_desc),
                checked = wkd_published,
                on_change = {
                    if (wkd == null) vm.load_wkd_keyserver_status() else vm.toggle_wkd_publishing()
                },
            )
            settings_row_gap()
            encryption_toggle_row(
                title = stringResource(R.string.publish_to_keyservers),
                subtitle = stringResource(R.string.publish_to_keyservers_sub),
                info_title = stringResource(R.string.info_keyservers_title),
                info_description = stringResource(R.string.info_keyservers_desc),
                checked = keyserver_checked,
                enabled = keyserver_published != true,
                on_change = { vm.toggle_keyserver_publishing() },
            )
        }

        v_gap(AsterSpacing.xxl)
    }

    if (show_export_private_dialog) {
        val context_export = context
        org.astermail.android.design.components.AsterDialog(
            on_dismiss = { if (!exporting_private_key) { show_export_private_dialog = false; export_private_password = "" } },
            title = stringResource(R.string.export_private_key_dialog_title),
            message = stringResource(R.string.export_private_key_message),
            body = {
                org.astermail.android.design.components.AsterTextField(
                    value = export_private_password,
                    onValueChange = { export_private_password = it; export_private_error = null },
                    label = stringResource(R.string.export_private_key_password_label),
                    visual_transformation = PasswordVisualTransformation(),
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                    error_text = export_private_error,
                )
            },
            footer = {
                org.astermail.android.design.components.AsterDialogOutlineButton(
                    label = stringResource(R.string.cancel),
                    enabled = !exporting_private_key,
                    onClick = { show_export_private_dialog = false; export_private_password = "" },
                )
                org.astermail.android.design.components.AsterDialogDestructiveButton(
                    label = if (exporting_private_key) stringResource(R.string.export_private_key_exporting) else stringResource(R.string.export_private_key_button),
                    enabled = export_private_password.isNotBlank() && !exporting_private_key,
                    is_loading = exporting_private_key,
                    onClick = {
                        if (export_private_password.isNotBlank() && !exporting_private_key) {
                            exporting_private_key = true
                            export_private_error = null
                            scope.launch {
                                val armored = vm.export_private_key_now(export_private_password)
                                exporting_private_key = false
                                if (armored != null) {
                                    show_export_private_dialog = false
                                    export_private_password = ""
                                    if (copy_to_clipboard(context_export, "private_key", armored)) {
                                        org.astermail.android.util.schedule_sensitive_clipboard_clear(context_export)
                                        Toast.makeText(context_export, context_export.getString(R.string.toast_private_key_copied), Toast.LENGTH_LONG).show()
                                    } else {
                                        show_copy_failed_toast(context_export)
                                    }
                                } else if (vm.state.value.pgp_export_held != null) {
                                    show_export_private_dialog = false
                                    export_private_password = ""
                                    Toast.makeText(context_export, context_export.getString(R.string.guard_held_toast), Toast.LENGTH_LONG).show()
                                } else {
                                    export_private_error = context_export.getString(R.string.error_private_key_export)
                                }
                            }
                        }
                    },
                )
            },
        )
    }
}

@Composable
private fun encryption_hero_card(state: encryption_hero_state) {
    val colors = AsterMaterial.colors
    val status_alpha by animateFloatAsState(
        targetValue = if (state == encryption_hero_state.unknown) 0f else 1f,
        animationSpec = tween(AsterDuration.medium_1, easing = AsterEasing.emphasized),
        label = "encryption_hero_status",
    )
    AsterCard(modifier = Modifier.fillMaxWidth().testTag("encryption_hero_card")) {
        Column(modifier = Modifier.padding(AsterSpacing.lg)) {
            val title_size = 21.sp
            val cap_half_px = with(LocalDensity.current) { (title_size.toPx() * encryption_cap_ratio / 2f).roundToInt() }
            Row(modifier = Modifier.fillMaxWidth()) {
                billing_wordmark(modifier = Modifier.alignBy { it.measuredHeight / 2 + cap_half_px })
                Spacer(Modifier.width(AsterSpacing.sm))
                Text(
                    text = stringResource(R.string.encryption_title),
                    color = colors.text_primary,
                    fontSize = title_size,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = (-0.5).sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f, fill = false).alignByBaseline(),
                )
                Spacer(Modifier.width(AsterSpacing.sm))
                Text(
                    text = stringResource(
                        if (state == encryption_hero_state.attention) R.string.enc_hero_status_attention
                        else R.string.enc_hero_status_protected,
                    ),
                    color = if (state == encryption_hero_state.attention) colors.danger else colors.success,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier
                        .alignByBaseline()
                        .graphicsLayer { alpha = status_alpha }
                        .testTag("encryption_hero_status"),
                )
            }
            Spacer(Modifier.height(AsterSpacing.xs))
            Text(
                text = stringResource(
                    if (state == encryption_hero_state.attention) R.string.enc_hero_summary_missing
                    else R.string.enc_hero_summary,
                ),
                color = colors.text_tertiary,
                fontSize = 13.sp,
                lineHeight = 19.sp,
            )
        }
    }
}

@Composable
private fun identity_key_card(
    fingerprint: String?,
    meta_line: String?,
    exporting_public_key: Boolean,
    on_copy_fingerprint: (String) -> Unit,
    on_share_public_key: () -> Unit,
    on_copy_public_key: () -> Unit,
    on_export_private_key: () -> Unit,
) {
    val colors = AsterMaterial.colors
    val shimmer = shimmer_state()
    val grouped_fingerprint = remember(fingerprint) { fingerprint?.let { format_fingerprint(it) } }
    AsterCard(modifier = Modifier.fillMaxWidth().testTag("your_key_card")) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(AsterSpacing.lg),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                modifier = Modifier
                    .size(40.dp)
                    .clip(SquircleShape(AsterRadius.md))
                    .background(colors.accent_blue.copy(alpha = if (colors.is_dark) 0.18f else 0.10f)),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    imageVector = TablerIcons.Key,
                    contentDescription = null,
                    tint = colors.accent_blue,
                    modifier = Modifier.size(20.dp),
                )
            }
            Spacer(Modifier.width(AsterSpacing.md))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = stringResource(R.string.enc_openpgp_key_title),
                    color = colors.text_primary,
                    fontSize = 15.sp,
                    fontWeight = FontWeight.Medium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.testTag("your_key_title"),
                )
                when {
                    meta_line == null -> Box(
                        modifier = Modifier
                            .padding(top = 2.dp)
                            .height(key_meta_line_height)
                            .clearAndSetSemantics {}
                            .skeleton_reveal(),
                        contentAlignment = Alignment.CenterStart,
                    ) {
                        skeleton_block(shimmer, 148.dp, 11.dp)
                    }
                    meta_line.isNotBlank() -> Text(
                        text = meta_line,
                        color = colors.text_tertiary,
                        fontSize = 13.sp,
                        lineHeight = 18.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier
                            .padding(top = 2.dp)
                            .heightIn(min = key_meta_line_height)
                            .testTag("your_key_meta"),
                    )
                }
            }
            Spacer(Modifier.width(AsterSpacing.sm))
            verified_badge(text = stringResource(R.string.active))
        }

        settings_row_gap()

        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(AsterSpacing.lg),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = stringResource(R.string.enc_verification_code_label),
                    color = colors.text_secondary,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Medium,
                )
                Spacer(Modifier.width(AsterSpacing.xs))
                info_dialog_button(
                    title = stringResource(R.string.enc_verification_code_info_title),
                    description = stringResource(R.string.enc_verification_code_info_description),
                )
            }
            Spacer(Modifier.height(AsterSpacing.sm))
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(AsterShapes.control)
                    .background(field_surface_color(colors), AsterShapes.control)
                    .padding(start = AsterSpacing.md, end = AsterSpacing.xs, top = AsterSpacing.xs, bottom = AsterSpacing.xs),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                if (grouped_fingerprint != null) {
                    Text(
                        text = grouped_fingerprint,
                        color = colors.text_primary,
                        fontSize = 13.sp,
                        lineHeight = 20.sp,
                        letterSpacing = 0.5.sp,
                        fontFamily = FontFamily.Monospace,
                        modifier = Modifier
                            .weight(1f)
                            .padding(vertical = AsterSpacing.xs)
                            .testTag("key_fingerprint_value"),
                    )
                } else {
                    Column(
                        modifier = Modifier
                            .weight(1f)
                            .padding(vertical = AsterSpacing.xs)
                            .clearAndSetSemantics {}
                            .skeleton_reveal(),
                    ) {
                        repeat(fingerprint_placeholder_lines) { index ->
                            Box(
                                modifier = Modifier.height(fingerprint_line_height),
                                contentAlignment = Alignment.CenterStart,
                            ) {
                                skeleton_block_fill(shimmer, 11.dp, fraction = if (index == fingerprint_placeholder_lines - 1) 0.7f else 0.92f)
                            }
                        }
                    }
                }
                AsterIconButton(
                    icon = TablerIcons.Copy,
                    content_description = stringResource(R.string.enc_copy_verification_code),
                    enabled = grouped_fingerprint != null,
                    onClick = { grouped_fingerprint?.let(on_copy_fingerprint) },
                    icon_size = 18,
                )
            }
        }

        settings_row_gap()
        encryption_action_row(
            label = stringResource(R.string.export_public_key),
            icon = TablerIcons.Share,
            on_click = on_share_public_key,
            loading = exporting_public_key,
            modifier = Modifier.testTag("export_public_key_button"),
        )
        settings_row_gap()
        encryption_action_row(
            label = stringResource(R.string.copy_public_key),
            icon = TablerIcons.Copy,
            on_click = on_copy_public_key,
            enabled = !exporting_public_key,
            modifier = Modifier.testTag("copy_public_key_button"),
        )
        settings_row_gap()
        encryption_action_row(
            label = stringResource(R.string.export_private_key_label),
            icon = TablerIcons.Lock,
            on_click = on_export_private_key,
            modifier = Modifier.testTag("export_private_key_button"),
        )
    }
}

@Composable
private fun identity_key_unavailable_card() {
    val colors = AsterMaterial.colors
    val dashed_color = colors.border_secondary
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(SquircleShape(AsterRadius.xl))
            .acrylic(colors, SquircleShape(AsterRadius.xl), colors.bg_secondary)
            .drawBehind {
                val stroke_width = 1.dp.toPx()
                val radius_px = 14.dp.toPx()
                val inset = stroke_width / 2f
                drawRoundRect(
                    color = dashed_color,
                    topLeft = androidx.compose.ui.geometry.Offset(inset, inset),
                    size = Size(size.width - stroke_width, size.height - stroke_width),
                    cornerRadius = CornerRadius(radius_px, radius_px),
                    style = Stroke(
                        width = stroke_width,
                        pathEffect = PathEffect.dashPathEffect(
                            floatArrayOf(6.dp.toPx(), 4.dp.toPx()),
                            0f,
                        ),
                    ),
                )
            },
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = AsterSpacing.xxl, horizontal = AsterSpacing.lg),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Icon(
                imageVector = TablerIcons.Key,
                contentDescription = null,
                tint = colors.text_muted,
                modifier = Modifier.size(24.dp),
            )
            Spacer(Modifier.height(AsterSpacing.sm))
            Text(
                text = stringResource(R.string.identity_key_unavailable),
                color = colors.text_secondary,
                fontSize = 13.sp,
                lineHeight = 18.sp,
                textAlign = androidx.compose.ui.text.style.TextAlign.Center,
            )
            Spacer(Modifier.height(AsterSpacing.xs))
            Text(
                text = stringResource(R.string.identity_key_unavailable_hint),
                color = colors.text_tertiary,
                fontSize = 12.sp,
                lineHeight = 17.sp,
                textAlign = androidx.compose.ui.text.style.TextAlign.Center,
            )
        }
    }
}

@Composable
private fun recovery_codes_card(
    available: Int?,
    total: Int?,
    load_failed: Boolean,
    on_retry: () -> Unit,
    on_manage: () -> Unit,
) {
    val colors = AsterMaterial.colors
    AsterCard(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(AsterSpacing.lg)) {
            if (available != null && total != null) {
                val is_low = available <= 2
                val used = (total - available).coerceAtLeast(0)
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = recovery_summary_height),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = pluralStringResource(R.plurals.recovery_codes_remaining, total, available, total),
                        color = colors.text_primary,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.SemiBold,
                        modifier = Modifier.weight(1f, fill = false),
                    )
                    if (used > 0) {
                        Spacer(Modifier.width(AsterSpacing.sm))
                        val used_pill = if (is_low) colors.danger else colors.warning
                        Box(
                            modifier = Modifier
                                .clip(CircleShape)
                                .background(used_pill)
                                .padding(horizontal = 10.dp, vertical = 3.dp),
                        ) {
                            Text(
                                text = pluralStringResource(R.plurals.fix_enc_codes_used, used, used),
                                color = readable_on(used_pill),
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Medium,
                            )
                        }
                    }
                }
                Spacer(Modifier.height(AsterSpacing.sm))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    repeat(total) { index ->
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .height(6.dp)
                                .clip(CircleShape)
                                .background(
                                    if (index < available) colors.accent_blue else colors.border_primary,
                                ),
                        )
                    }
                }
                if (is_low) {
                    Spacer(Modifier.height(AsterSpacing.sm))
                    Text(
                        text = stringResource(R.string.recovery_codes_low_warning),
                        color = colors.danger,
                        fontSize = 12.sp,
                        lineHeight = 17.sp,
                    )
                }
            } else if (load_failed) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = recovery_summary_height),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = stringResource(R.string.failed_to_load),
                        color = colors.text_tertiary,
                        fontSize = 13.sp,
                        modifier = Modifier.weight(1f),
                    )
                    Text(
                        text = stringResource(R.string.retry),
                        color = colors.accent_blue,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Medium,
                        modifier = Modifier
                            .clip(SquircleShape(AsterRadius.sm))
                            .clickable(role = Role.Button, onClick = on_retry)
                            .padding(horizontal = AsterSpacing.sm, vertical = AsterSpacing.xs),
                    )
                }
                Spacer(Modifier.height(AsterSpacing.sm))
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(6.dp)
                        .clip(CircleShape)
                        .background(colors.border_primary),
                )
            } else {
                val shimmer = shimmer_state()
                Column(modifier = Modifier.clearAndSetSemantics {}.skeleton_reveal()) {
                    Box(
                        modifier = Modifier.fillMaxWidth().height(recovery_summary_height),
                        contentAlignment = Alignment.CenterStart,
                    ) {
                        skeleton_block_fill(shimmer, 13.dp, fraction = 0.5f)
                    }
                    Spacer(Modifier.height(AsterSpacing.sm))
                    skeleton_block_fill(shimmer, 6.dp, corner = 3.dp)
                }
            }
        }
        settings_row_gap()
        encryption_action_row(
            label = stringResource(R.string.recovery_codes),
            icon = TablerIcons.Key,
            on_click = on_manage,
        )
    }
}

@Composable
private fun encryption_action_row(
    label: String,
    icon: ImageVector,
    on_click: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    loading: Boolean = false,
) {
    val colors = AsterMaterial.colors
    Row(
        modifier = modifier
            .fillMaxWidth()
            .heightIn(min = settings_row_min_height)
            .clickable(enabled = enabled && !loading, role = Role.Button, onClick = on_click)
            .padding(horizontal = AsterSpacing.lg, vertical = AsterSpacing.sm),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = colors.text_secondary,
            modifier = Modifier.size(22.dp),
        )
        Spacer(Modifier.width(AsterSpacing.md))
        Text(
            text = label,
            color = if (enabled) colors.text_primary else colors.text_muted,
            fontSize = 15.sp,
            fontWeight = FontWeight.Medium,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f),
        )
        Spacer(Modifier.width(AsterSpacing.sm))
        Box(modifier = Modifier.size(18.dp), contentAlignment = Alignment.Center) {
            if (loading) {
                org.astermail.android.design.components.aster_spinner(
                    color = colors.accent_blue,
                    stroke_width = 2.dp,
                    modifier = Modifier.size(16.dp),
                )
            } else {
                Icon(
                    imageVector = TablerIcons.ChevronRight,
                    contentDescription = null,
                    tint = colors.text_muted,
                    modifier = Modifier.size(18.dp).mirror_in_rtl(),
                )
            }
        }
    }
}

@Composable
private fun encryption_toggle_row(
    title: String,
    subtitle: String,
    info_title: String,
    info_description: String,
    checked: Boolean?,
    on_change: (Boolean) -> Unit,
    enabled: Boolean = true,
) {
    if (checked != null) {
        settings_toggle_row(
            title = title,
            subtitle = subtitle,
            checked = checked,
            enabled = enabled,
            info_title = info_title,
            info_description = info_description,
            on_change = on_change,
        )
        return
    }
    val colors = AsterMaterial.colors
    val shimmer = shimmer_state()
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = settings_row_min_height)
            .padding(horizontal = AsterSpacing.lg, vertical = AsterSpacing.sm),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = title,
                    color = colors.text_primary,
                    fontSize = 15.sp,
                    fontWeight = FontWeight.Medium,
                    modifier = Modifier.weight(1f, fill = false),
                )
                Spacer(Modifier.width(AsterSpacing.xs))
                info_dialog_button(title = info_title, description = info_description)
            }
            Text(
                text = subtitle,
                color = colors.text_tertiary,
                fontSize = 13.sp,
                modifier = Modifier.padding(top = 2.dp),
            )
        }
        Spacer(Modifier.width(AsterSpacing.md))
        Box(
            modifier = Modifier
                .size(48.dp, 32.dp)
                .clearAndSetSemantics {}
                .skeleton_reveal(),
            contentAlignment = Alignment.Center,
        ) {
            skeleton_block(shimmer, 44.dp, 26.dp, corner = 13.dp)
        }
    }
}

@Composable
internal fun recovery_codes_block(codes: List<String>) {
    val colors = AsterMaterial.colors
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(AsterShapes.island)
            .background(island_surface_color(colors), AsterShapes.island)
            .padding(AsterSpacing.md),
        verticalArrangement = Arrangement.spacedBy(AsterSpacing.sm),
    ) {
        codes.chunked(2).forEachIndexed { row_index, pair ->
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(AsterSpacing.sm),
            ) {
                pair.forEachIndexed { column_index, code ->
                    Row(
                        modifier = Modifier.weight(1f),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            text = "${row_index * 2 + column_index + 1}",
                            color = colors.text_muted,
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Medium,
                            modifier = Modifier.width(16.dp),
                        )
                        Text(
                            text = code,
                            color = colors.text_primary,
                            fontFamily = FontFamily.Monospace,
                            fontSize = 13.sp,
                            letterSpacing = 0.5.sp,
                        )
                    }
                }
                if (pair.size == 1) {
                    Spacer(Modifier.weight(1f))
                }
            }
        }
    }
}

@Composable
internal fun acknowledge_row(checked: Boolean, label: String, on_change: (Boolean) -> Unit) {
    val colors = AsterMaterial.colors
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(SquircleShape(AsterRadius.md))
            .clickable { on_change(!checked) }
            .padding(end = AsterSpacing.sm),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Checkbox(
            checked = checked,
            onCheckedChange = on_change,
            colors = CheckboxDefaults.colors(
                checkedColor = colors.accent_blue,
                uncheckedColor = colors.border_primary,
                checkmarkColor = colors.bg_card,
            ),
        )
        Text(
            text = label,
            color = colors.text_primary,
            fontSize = 13.sp,
            lineHeight = 18.sp,
        )
    }
}

internal fun share_public_key(context: Context, armored: String) {
    val shared = runCatching {
        val export_dir = File(context.cacheDir, "exports").apply { mkdirs() }
        val file = File(export_dir, "aster-public-key.asc")
        file.writeText(armored, Charsets.UTF_8)
        val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
        val title = context.getString(R.string.export_public_key)
        val intent = Intent(Intent.ACTION_SEND).apply {
            type = "application/pgp-keys"
            putExtra(Intent.EXTRA_STREAM, uri)
            putExtra(Intent.EXTRA_SUBJECT, title)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        val started = org.astermail.android.ui.common.start_external_intent(
            context,
            Intent.createChooser(intent, title).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
        )
        if (!started) org.astermail.android.util.delete_export_file_quietly(file)
        started
    }.getOrDefault(false)
    if (!shared) {
        Toast.makeText(context, context.getString(R.string.something_went_wrong), Toast.LENGTH_SHORT).show()
    }
}

internal fun save_recovery_codes(context: Context, codes: List<String>) {
    val saved = runCatching {
        val export_dir = File(context.cacheDir, "exports").apply { mkdirs() }
        val stamp = SimpleDateFormat("yyyy-MM-dd", Locale.US).format(Date())
        val file = File(export_dir, "aster-recovery-codes-$stamp.txt")
        val note = context.getString(R.string.fix_enc_codes_file_note)
        file.writeText(codes.joinToString("\n") + "\n\n" + note + "\n", Charsets.UTF_8)
        val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
        val title = context.getString(R.string.fix_enc_codes_share_title)
        val intent = Intent(Intent.ACTION_SEND).apply {
            type = "text/plain"
            putExtra(Intent.EXTRA_STREAM, uri)
            putExtra(Intent.EXTRA_SUBJECT, title)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        val started = org.astermail.android.ui.common.start_external_intent(
            context,
            Intent.createChooser(intent, title).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
        )
        if (!started) org.astermail.android.util.delete_export_file_quietly(file)
        started
    }.getOrDefault(false)
    if (!saved) {
        Toast.makeText(context, context.getString(R.string.fix_enc_codes_save_failed), Toast.LENGTH_LONG).show()
    }
}

internal fun pgp_algorithm_label(algorithm: String?, key_size: Int): String? {
    val raw = algorithm.orEmpty().lowercase()
    return when {
        raw.isBlank() -> null
        raw.contains("25519") -> "Curve25519"
        raw.contains("448") -> "Curve448"
        raw.contains("p256") || raw.contains("p-256") -> "NIST P-256"
        raw.contains("p384") || raw.contains("p-384") -> "NIST P-384"
        raw.startsWith("rsa") && key_size > 0 -> "RSA-$key_size"
        else -> raw.replace('_', ' ').uppercase()
    }
}

@Composable
private fun external_pgp_key_card(
    external_key: org.astermail.android.api.encryption.ExternalPgpKeyInfo,
    busy: Boolean,
    on_stop_publishing: () -> Unit,
) {
    val colors = AsterMaterial.colors
    val algorithm_line = pgp_algorithm_label(external_key.algorithm, external_key.key_size)
    val expires_at = external_key.expires_at.orEmpty()
    val expiry_line = if (expires_at.isNotBlank()) {
        stringResource(R.string.external_pgp_key_expires, absolute_date_label(expires_at))
    } else {
        stringResource(R.string.external_pgp_key_no_expiry)
    }
    val detail_line = listOfNotNull(algorithm_line, expiry_line).joinToString(" · ")
    val grouped_fingerprint = remember(external_key.fingerprint) { format_fingerprint(external_key.fingerprint) }
    AsterCard(modifier = Modifier.fillMaxWidth().testTag("external_pgp_key_card")) {
        Column(modifier = Modifier.fillMaxWidth()) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(AsterSpacing.lg),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = stringResource(R.string.external_pgp_key_title),
                        color = colors.text_primary,
                        fontSize = 15.sp,
                        fontWeight = FontWeight.Medium,
                    )
                    Spacer(Modifier.size(2.dp))
                    Text(
                        text = detail_line,
                        color = colors.text_tertiary,
                        fontSize = 13.sp,
                        lineHeight = 18.sp,
                    )
                }
            }

            settings_row_gap()

            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(AsterSpacing.lg),
            ) {
                Text(
                    text = stringResource(R.string.external_pgp_key_fingerprint),
                    color = colors.text_secondary,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Medium,
                )
                Spacer(Modifier.size(AsterSpacing.sm))
                Text(
                    text = grouped_fingerprint,
                    color = colors.text_primary,
                    fontSize = 13.sp,
                    lineHeight = 20.sp,
                    fontFamily = FontFamily.Monospace,
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(AsterShapes.control)
                        .background(field_surface_color(colors), AsterShapes.control)
                        .padding(AsterSpacing.sm)
                        .testTag("external_pgp_key_fingerprint"),
                )
                Spacer(Modifier.size(AsterSpacing.sm))
                Text(
                    text = stringResource(
                        if (external_key.published) R.string.external_pgp_key_published
                        else R.string.external_pgp_key_not_published,
                    ),
                    color = colors.text_secondary,
                    fontSize = 13.sp,
                    lineHeight = 18.sp,
                    modifier = Modifier.testTag("external_pgp_key_published_state"),
                )
                if (external_key.published) {
                    Spacer(Modifier.size(AsterSpacing.sm))
                    AsterSecondaryButton(
                        label = stringResource(R.string.external_pgp_key_stop_publishing),
                        onClick = on_stop_publishing,
                        enabled = !busy,
                        is_loading = busy,
                        modifier = Modifier.fillMaxWidth().testTag("external_pgp_key_stop_publishing"),
                    )
                }
                Spacer(Modifier.size(AsterSpacing.sm))
                Text(
                    text = stringResource(R.string.external_pgp_key_manage_note),
                    color = colors.text_tertiary,
                    fontSize = 12.sp,
                    lineHeight = 17.sp,
                )
            }
        }
    }
}

internal fun format_fingerprint(hex: String): String {
    val clean = hex.filter { !it.isWhitespace() }
    if (clean.isEmpty()) return hex
    return clean.chunked(2).chunked(8).joinToString("\n") { line -> line.joinToString(" ") }
}

private fun copy_to_clipboard(context: Context, label: String, value: String): Boolean {
    val clip = ClipData.newPlainText(label, value)
    clip.description.extras = android.os.PersistableBundle().apply {
        putBoolean("android.content.extra.IS_SENSITIVE", true)
    }
    return write_to_clipboard(context, clip)
}
