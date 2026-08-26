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
import org.astermail.android.ui.common.show_copy_failed_toast
import org.astermail.android.ui.common.write_to_clipboard
import compose.icons.tablericons.*

import android.content.ClipData
import android.content.Context
import android.util.Base64
import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.ui.graphics.Color
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
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
import org.astermail.android.design.AsterMaterial
import org.astermail.android.design.AsterRadius
import org.astermail.android.design.AsterSpacing
import org.astermail.android.design.SquircleShape
import org.astermail.android.design.components.AsterCard
import org.astermail.android.design.components.AsterDivider
import org.astermail.android.design.components.AsterGhostButton
import org.astermail.android.design.components.AsterIconButton
import org.astermail.android.design.components.AsterSecondaryButton
import org.astermail.android.design.components.AsterSwitch
import org.astermail.android.settings.SettingsViewModel
import org.astermail.android.storage.SessionKeyStore
import java.security.MessageDigest
import org.astermail.android.settings.shared_settings_view_model

@EntryPoint
@InstallIn(SingletonComponent::class)
private interface EncryptionScreenDeps {
    fun session_key_store(): SessionKeyStore
    fun auth_repository(): AuthRepository
}

private data class identity_key_view(
    val email: String?,
    val public_b64: String?,
    val fingerprint: String?,
    val is_pgp: Boolean,
    val available: Boolean,
)

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

    LaunchedEffect(state.action_result) {
        val msg = state.action_result ?: return@LaunchedEffect
        android.widget.Toast.makeText(context, msg, android.widget.Toast.LENGTH_SHORT).show()
        vm.clear_action_result()
    }

    LaunchedEffect(Unit) {
        vm.load_pgp_key_info()
        vm.load_recovery_codes_status()
        vm.load_encryption_settings()
        vm.load_wkd_keyserver_status()
    }

    val view_state = produceState<identity_key_view?>(initialValue = null, deps) {
        value = withContext(Dispatchers.IO) {
            if (session_key_store.get_identity_key() == null) {
                auth_repository.try_recover_identity_key()
            }
            val stored = session_key_store.get_identity_key()
            val email_addr = session_key_store.get_user_email()
            if (stored.isNullOrBlank()) {
                identity_key_view(
                    email = email_addr,
                    public_b64 = null,
                    fingerprint = null,
                    is_pgp = false,
                    available = false,
                )
            } else if (stored.contains("BEGIN PGP")) {
                val fp = runCatching {
                    val digest = MessageDigest.getInstance("SHA-256")
                        .digest(stored.toByteArray(Charsets.UTF_8))
                    digest.joinToString("") { String.format(java.util.Locale.US, "%02X", it.toInt() and 0xFF) }
                }.getOrNull()
                identity_key_view(
                    email = email_addr,
                    public_b64 = null,
                    fingerprint = fp,
                    is_pgp = true,
                    available = true,
                )
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
                identity_key_view(
                    email = email_addr,
                    public_b64 = pub_b64,
                    fingerprint = fp,
                    is_pgp = false,
                    available = pub_b64 != null,
                )
            }
        }
    }

    val view = view_state.value
    val identity_public_b64 = view?.public_b64
    val fingerprint = view?.fingerprint
    val key_available = view?.available == true
    val is_pgp_key = view?.is_pgp == true
    val prefs = state.preferences

    var show_full_key by remember { mutableStateOf(false) }
    var show_regen_confirm by remember { mutableStateOf(false) }
    var show_new_codes_dialog by remember { mutableStateOf(false) }
    var new_recovery_codes by remember { mutableStateOf(emptyList<String>()) }
    var regenerating by remember { mutableStateOf(false) }
    var show_export_private_dialog by remember { mutableStateOf(false) }
    var export_private_password by remember { mutableStateOf("") }
    var exporting_private_key by remember { mutableStateOf(false) }
    var export_private_error by remember { mutableStateOf<String?>(null) }

    fun toggle(update: (UserPreferences) -> UserPreferences) {
        val current = prefs ?: return
        if (!state.preferences_authoritative) {
            vm.report_preferences_locked()

            return
        }
        vm.save_preferences(update(current))
    }

    detail_scaffold(title = stringResource(R.string.encryption_title), on_back = on_back) {

        preferences_save_error_banner()
        section_label(stringResource(R.string.your_key))

        if (view == null) {
            AsterCard(modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(AsterSpacing.lg)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        CircularProgressIndicator(
                            color = colors.accent_blue,
                            modifier = Modifier.size(16.dp),
                            strokeWidth = 2.dp,
                        )
                        Spacer(Modifier.width(AsterSpacing.sm))
                        Text(
                            text = stringResource(R.string.loading_identity_key),
                            color = colors.text_tertiary,
                            fontSize = 13.sp,
                        )
                    }
                }
            }
        } else if (key_available && fingerprint != null) {
            val pgp_info = state.pgp_key_info
            val algorithm_title = run {
                val raw = pgp_info?.algorithm.orEmpty().lowercase()
                val size = when {
                    (pgp_info?.key_size ?: 0) > 0 -> pgp_info!!.key_size
                    else -> raw.filter { it.isDigit() }.toIntOrNull() ?: 0
                }
                when {
                    raw.startsWith("rsa") && size > 0 -> "RSA-$size"
                    raw.startsWith("rsa") -> "RSA"
                    raw.contains("25519") -> "Curve25519"
                    raw.contains("448") -> "Curve448"
                    raw.contains("p256") || raw.contains("p-256") -> "NIST P-256"
                    raw.contains("p384") || raw.contains("p-384") -> "NIST P-384"
                    raw.isNotBlank() -> raw.replace('_', ' ').uppercase()
                    else -> stringResource(if (is_pgp_key) R.string.pgp_identity_fingerprint else R.string.identity_key_fingerprint)
                }
            }
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(SquircleShape(AsterRadius.xl))
                    .background(colors.bg_card)
                    .border(1.dp, colors.border_secondary, SquircleShape(AsterRadius.xl)),
            ) {
                Column(modifier = Modifier.fillMaxWidth()) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(AsterSpacing.lg),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Box(
                            modifier = Modifier
                                .size(38.dp)
                                .clip(CircleShape)
                                .background(colors.accent_blue.copy(alpha = 0.12f)),
                            contentAlignment = Alignment.Center,
                        ) {
                            Icon(
                                imageVector = TablerIcons.Key,
                                contentDescription = null,
                                tint = colors.accent_blue,
                                modifier = Modifier.size(19.dp),
                            )
                        }
                        Spacer(Modifier.width(AsterSpacing.md))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = algorithm_title,
                                color = colors.text_primary,
                                fontSize = 15.sp,
                                fontWeight = FontWeight.Medium,
                            )
                            val created = pgp_info?.created_at.orEmpty()
                            if (created.isNotBlank()) {
                                Spacer(Modifier.size(2.dp))
                                Text(
                                    text = stringResource(R.string.created_at_format, absolute_date_label(created)),
                                    color = colors.text_muted,
                                    fontSize = 12.sp,
                                )
                            }
                        }
                        Spacer(Modifier.width(AsterSpacing.sm))
                        Row(
                            modifier = Modifier
                                .clip(SquircleShape(AsterRadius.pill))
                                .background(colors.success.copy(alpha = 0.10f))
                                .padding(horizontal = 10.dp, vertical = 4.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(4.dp),
                        ) {
                            Icon(
                                imageVector = TablerIcons.CircleCheck,
                                contentDescription = null,
                                tint = colors.success,
                                modifier = Modifier.size(13.dp),
                            )
                            Text(
                                text = stringResource(R.string.active),
                                color = colors.success,
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Medium,
                            )
                        }
                    }

                    AsterDivider()

                    val grouped_fingerprint = remember(fingerprint) { format_fingerprint(fingerprint) }
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = AsterSpacing.lg, vertical = AsterSpacing.md),
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                imageVector = TablerIcons.Fingerprint,
                                contentDescription = null,
                                tint = colors.text_tertiary,
                                modifier = Modifier.size(14.dp),
                            )
                            Spacer(Modifier.width(6.dp))
                            Text(
                                text = stringResource(
                                    if (is_pgp_key) R.string.pgp_identity_fingerprint else R.string.identity_key_fingerprint,
                                ).uppercase(),
                                color = colors.text_tertiary,
                                fontSize = 11.sp,
                                fontWeight = FontWeight.SemiBold,
                                modifier = Modifier.weight(1f),
                            )
                            AsterIconButton(
                                icon = TablerIcons.Copy,
                                content_description = stringResource(R.string.copy_fingerprint_action),
                                onClick = {
                                    if (copy_to_clipboard(context, context.getString(R.string.clipboard_label_identity_fingerprint), grouped_fingerprint)) {
                                        Toast.makeText(context, context.getString(R.string.fingerprint_copied), Toast.LENGTH_SHORT).show()
                                    } else {
                                        show_copy_failed_toast(context)
                                    }
                                },
                                icon_size = 16,
                            )
                        }
                        Spacer(Modifier.size(AsterSpacing.sm))
                        Text(
                            text = grouped_fingerprint,
                            color = colors.text_secondary,
                            fontSize = 13.sp,
                            lineHeight = 20.sp,
                            letterSpacing = 0.5.sp,
                            fontFamily = FontFamily.Monospace,
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(SquircleShape(AsterRadius.md))
                                .background(colors.bg_secondary)
                                .border(1.dp, colors.border_primary, SquircleShape(AsterRadius.md))
                                .padding(horizontal = AsterSpacing.md, vertical = AsterSpacing.sm),
                        )
                        Spacer(Modifier.size(AsterSpacing.sm))
                        Text(
                            text = stringResource(R.string.fingerprint_description),
                            color = colors.text_tertiary,
                            fontSize = 12.sp,
                        )
                    }

                    AsterDivider()

                    detail_row(
                        title = stringResource(R.string.export_public_key),
                        icon = TablerIcons.Download,
                        on_click = {
                            scope.launch {
                                val armored = vm.export_public_key_now()
                                if (armored != null) {
                                    if (copy_to_clipboard(context, context.getString(R.string.clipboard_label_identity_public_key), armored)) {
                                        Toast.makeText(context, context.getString(R.string.public_key_copied), Toast.LENGTH_SHORT).show()
                                    } else {
                                        show_copy_failed_toast(context)
                                    }
                                } else {
                                    Toast.makeText(context, context.getString(R.string.something_went_wrong), Toast.LENGTH_SHORT).show()
                                }
                            }
                        },
                        trailing = {
                            Icon(
                                imageVector = TablerIcons.Copy,
                                contentDescription = null,
                                tint = colors.text_tertiary,
                                modifier = Modifier.size(18.dp),
                            )
                        },
                    )

                    AsterDivider()

                    detail_row(
                        title = stringResource(R.string.export_private_key_label),
                        icon = TablerIcons.ShieldLock,
                        on_click = {
                            export_private_password = ""
                            export_private_error = null
                            show_export_private_dialog = true
                        },
                    )
                }
            }
            if (identity_public_b64 != null) {
                v_gap(AsterSpacing.sm)
                AsterSecondaryButton(
                    label = if (show_full_key) stringResource(R.string.hide_public_key) else stringResource(R.string.show_public_key),
                    onClick = { show_full_key = !show_full_key },
                )
            }
            if (show_full_key && identity_public_b64 != null) {
                v_gap(AsterSpacing.md)
                AsterCard(modifier = Modifier.fillMaxWidth()) {
                    Column(modifier = Modifier.padding(AsterSpacing.lg)) {
                        Text(
                            text = identity_public_b64,
                            color = colors.text_primary,
                            fontSize = 12.sp,
                            fontFamily = FontFamily.Monospace,
                        )
                        Spacer(Modifier.size(AsterSpacing.sm))
                        AsterGhostButton(
                            label = stringResource(R.string.copy_public_key),
                            onClick = {
                                copy_to_clipboard(context, context.getString(R.string.clipboard_label_identity_public_key), identity_public_b64)
                            },
                        )
                    }
                }
            }
        } else {
            val dashed_color = colors.border_secondary
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(SquircleShape(AsterRadius.xl))
                    .background(colors.bg_secondary)
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
                    Spacer(Modifier.size(AsterSpacing.sm))
                    Text(
                        text = stringResource(R.string.identity_key_unavailable),
                        color = colors.text_muted,
                        fontSize = 13.sp,
                    )
                    Spacer(Modifier.size(AsterSpacing.xs))
                    Text(
                        text = stringResource(R.string.identity_key_unavailable_hint),
                        color = colors.text_muted,
                        fontSize = 12.sp,
                    )
                }
            }
        }

        v_gap(AsterSpacing.lg)
        section_label(stringResource(R.string.recovery_codes))
        AsterCard(modifier = Modifier.fillMaxWidth()) {
          Column(modifier = Modifier.fillMaxWidth()) {
            Column(modifier = Modifier.padding(AsterSpacing.lg)) {
                val status = state.recovery_codes_status
                if (status != null) {
                    val available = status.available_codes
                    val total = status.total_codes
                    val fraction = if (total > 0) available.toFloat() / total else 0f
                    val is_low = available <= 2
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            text = stringResource(R.string.recovery_codes_remaining, available, total),
                            color = colors.text_primary,
                            fontSize = 14.sp,
                            fontWeight = FontWeight.SemiBold,
                        )
                        Box(
                            modifier = Modifier
                                .clip(SquircleShape(6.dp))
                                .background((if (is_low) colors.danger else colors.success).copy(alpha = 0.15f))
                                .padding(horizontal = 8.dp, vertical = 3.dp),
                        ) {
                            Text(
                                text = if (is_low) stringResource(R.string.low) else stringResource(R.string.ok),
                                color = if (is_low) colors.danger else colors.success,
                                fontSize = 11.sp,
                                fontWeight = FontWeight.SemiBold,
                            )
                        }
                    }
                    Spacer(Modifier.size(AsterSpacing.sm))
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(6.dp)
                            .clip(CircleShape)
                            .background(colors.border_primary),
                    ) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth(fraction = fraction.coerceIn(0f, 1f))
                                .height(6.dp)
                                .clip(CircleShape)
                                .background(if (is_low) colors.danger else colors.success),
                        )
                    }
                    if (is_low) {
                        Spacer(Modifier.size(AsterSpacing.sm))
                        Text(
                            text = stringResource(R.string.recovery_codes_low_warning),
                            color = colors.danger,
                            fontSize = 12.sp,
                        )
                    }
                } else {
                    Text(
                        text = stringResource(R.string.loading_recovery_codes_status),
                        color = colors.text_tertiary,
                        fontSize = 13.sp,
                    )
                }
            }
            AsterDivider()
            detail_row(
                title = stringResource(R.string.view_backup_recovery_key),
                icon = TablerIcons.Key,
                on_click = { on_open("recovery_key_view") },
            )
            AsterDivider()
            detail_row(
                title = if (regenerating) stringResource(R.string.regenerating) else stringResource(R.string.regenerate_recovery_codes),
                icon = TablerIcons.Refresh,
                on_click = { if (!regenerating) show_regen_confirm = true },
                trailing = if (regenerating) {
                    {
                        CircularProgressIndicator(
                            color = colors.accent_blue,
                            modifier = Modifier.size(16.dp),
                            strokeWidth = 2.dp,
                        )
                    }
                } else {
                    null
                },
            )
          }
        }

        v_gap(AsterSpacing.lg)
        section_label(stringResource(R.string.storage_format))
        if (prefs == null) {
            AsterCard(modifier = Modifier.fillMaxWidth()) {
                Box(
                    modifier = Modifier.fillMaxWidth().padding(AsterSpacing.xl),
                    contentAlignment = Alignment.Center,
                ) {
                    CircularProgressIndicator(color = colors.accent_blue, modifier = Modifier.size(24.dp))
                }
            }
        } else {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = AsterSpacing.lg),
                verticalArrangement = Arrangement.spacedBy(AsterSpacing.md),
            ) {
                illustrated_option_card(
                    image = R.drawable.settings_aster_server,
                    title = stringResource(R.string.storage_format_aster),
                    subtitle = stringResource(R.string.storage_format_aster_sub),
                    selected = prefs.storage_format != "ipfs",
                    modifier = Modifier.fillMaxWidth(),
                    on_click = { vm.set_storage_format("aster") },
                )
                illustrated_option_card(
                    image = R.drawable.settings_decentralized,
                    title = stringResource(R.string.storage_format_ipfs),
                    subtitle = stringResource(R.string.storage_format_ipfs_sub),
                    selected = prefs.storage_format == "ipfs",
                    modifier = Modifier.fillMaxWidth(),
                    on_click = { vm.set_storage_format("ipfs") },
                )
            }
        }

        v_gap(AsterSpacing.lg)
        section_label(stringResource(R.string.encryption_behavior))
        AsterCard(modifier = Modifier.fillMaxWidth()) {
            val enc = state.encryption_settings
            val wkd = state.wkd_status
            if (enc == null && state.encryption_settings_load_failed) {
                detail_row(
                    title = stringResource(R.string.failed_to_load),
                    subtitle = stringResource(R.string.retry),
                    on_click = { vm.load_encryption_settings() },
                )
            } else if (prefs == null || enc == null) {
                Box(
                    modifier = Modifier.fillMaxWidth().padding(AsterSpacing.xl),
                    contentAlignment = Alignment.Center,
                ) {
                    CircularProgressIndicator(color = colors.accent_blue, modifier = Modifier.size(24.dp))
                }
            } else {
                detail_row(
                    title = stringResource(R.string.auto_discover_keys),
                    subtitle = stringResource(R.string.auto_discover_keys_sub),
                    info_title = stringResource(R.string.info_auto_discover_title),
                    info_description = stringResource(R.string.info_auto_discover_desc),
                    trailing = {
                        AsterSwitch(
                            checked = enc.auto_discover_keys == true,
                            onCheckedChange = { vm.toggle_auto_discover_keys() },
                        )
                    },
                )
                AsterDivider()
                detail_row(
                    title = stringResource(R.string.encrypt_by_default),
                    subtitle = stringResource(R.string.encrypt_by_default_sub),
                    info_title = stringResource(R.string.info_encrypt_default_title),
                    info_description = stringResource(R.string.info_encrypt_default_desc),
                    trailing = {
                        AsterSwitch(
                            checked = enc.encrypt_by_default == true,
                            onCheckedChange = { vm.toggle_encrypt_by_default() },
                        )
                    },
                )
                AsterDivider()
                detail_row(
                    title = stringResource(R.string.require_encryption),
                    subtitle = stringResource(R.string.require_encryption_sub),
                    info_title = stringResource(R.string.info_require_encryption_title),
                    info_description = stringResource(R.string.info_require_encryption_desc),
                    trailing = {
                        AsterSwitch(
                            checked = enc.require_encryption,
                            onCheckedChange = { vm.toggle_require_encryption() },
                        )
                    },
                )
                AsterDivider()
                detail_row(
                    title = stringResource(R.string.show_encryption_indicators),
                    subtitle = stringResource(R.string.show_encryption_indicators_sub),
                    info_title = stringResource(R.string.info_encryption_indicators_title),
                    info_description = stringResource(R.string.info_encryption_indicators_desc),
                    trailing = {
                        AsterSwitch(
                            checked = prefs.show_encryption_indicators != false,
                            onCheckedChange = { toggle { it.copy(show_encryption_indicators = !it.show_encryption_indicators) } },
                        )
                    },
                )
                AsterDivider()
                detail_row(
                    title = stringResource(R.string.publish_to_wkd),
                    subtitle = if (wkd == null && state.wkd_status_load_failed) {
                        stringResource(R.string.failed_to_load)
                    } else {
                        stringResource(R.string.publish_to_wkd_sub)
                    },
                    info_title = stringResource(R.string.info_wkd_title),
                    info_description = stringResource(R.string.info_wkd_desc),
                    on_click = if (wkd == null && state.wkd_status_load_failed) {
                        { vm.load_wkd_keyserver_status() }
                    } else {
                        null
                    },
                    trailing = {
                        AsterSwitch(
                            checked = wkd?.published == true,
                            enabled = wkd != null,
                            onCheckedChange = { vm.toggle_wkd_publishing() },
                        )
                    },
                )
                AsterDivider()
                detail_row(
                    title = stringResource(R.string.publish_to_keyservers),
                    subtitle = stringResource(R.string.publish_to_keyservers_sub),
                    info_title = stringResource(R.string.info_keyservers_title),
                    info_description = stringResource(R.string.info_keyservers_desc),
                    trailing = {
                        AsterSwitch(
                            checked = state.keyserver_status?.published == true || prefs.publish_to_keyservers,
                            enabled = state.keyserver_status?.published != true,
                            onCheckedChange = { vm.toggle_keyserver_publishing() },
                        )
                    },
                )
            }
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
                                        org.astermail.android.util.schedule_sensitive_clipboard_clear(context_export, armored)
                                        Toast.makeText(context_export, context_export.getString(R.string.toast_private_key_copied), Toast.LENGTH_LONG).show()
                                    } else {
                                        show_copy_failed_toast(context_export)
                                    }
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

    if (show_regen_confirm) {
        org.astermail.android.design.components.AsterAlertDialog(
            on_dismiss = { show_regen_confirm = false },
            title = stringResource(R.string.regenerate_recovery_codes_title),
            message = stringResource(R.string.regenerate_recovery_codes_message),
            confirm_label = stringResource(R.string.regenerate),
            cancel_label = stringResource(R.string.cancel),
            confirm_style = org.astermail.android.design.components.DialogConfirmStyle.destructive,
            on_confirm = {
                show_regen_confirm = false
                scope.launch {
                    regenerating = true
                    val codes = vm.regenerate_recovery_codes_now()
                    regenerating = false
                    if (codes.isNotEmpty()) {
                        new_recovery_codes = codes
                        show_new_codes_dialog = true
                    } else {
                        Toast.makeText(context, context.getString(R.string.something_went_wrong), Toast.LENGTH_SHORT).show()
                    }
                }
            },
        )
    }

    if (show_new_codes_dialog && new_recovery_codes.isNotEmpty()) {
        val context_dialog = LocalContext.current
        org.astermail.android.design.components.AsterDialog(
            on_dismiss = { show_new_codes_dialog = false; new_recovery_codes = emptyList() },
            title = stringResource(R.string.new_recovery_codes_title),
            body = {
                Column {
                    Text(
                        text = stringResource(R.string.new_recovery_codes_message),
                        color = colors.warning,
                        fontSize = 13.sp,
                    )
                    Spacer(Modifier.size(AsterSpacing.md))
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(SquircleShape(AsterRadius.lg))
                            .background(colors.bg_secondary)
                            .padding(vertical = AsterSpacing.md),
                        verticalArrangement = Arrangement.spacedBy(AsterSpacing.xs),
                    ) {
                        new_recovery_codes.chunked(2).forEach { pair ->
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceEvenly,
                            ) {
                                pair.forEach { code ->
                                    Text(
                                        text = code,
                                        fontFamily = FontFamily.Monospace,
                                        fontSize = 13.sp,
                                        color = colors.accent_blue,
                                    )
                                }
                            }
                        }
                    }
                }
            },
            footer = {
                org.astermail.android.design.components.AsterDialogOutlineButton(
                    label = stringResource(R.string.copy_all_codes),
                    onClick = {
                        val copied = copy_to_clipboard(
                            context_dialog,
                            "recovery_codes",
                            new_recovery_codes.joinToString("\n"),
                        )
                        if (copied) {
                            Toast.makeText(context_dialog, context_dialog.getString(R.string.copied), Toast.LENGTH_SHORT).show()
                        } else {
                            show_copy_failed_toast(context_dialog)
                        }
                    },
                )
                org.astermail.android.design.components.AsterDialogPrimaryButton(
                    label = stringResource(R.string.done),
                    onClick = {
                        show_new_codes_dialog = false
                        new_recovery_codes = emptyList()
                    },
                )
            },
        )
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
