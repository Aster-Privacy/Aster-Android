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

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.util.Base64
import android.widget.Toast
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Check
import androidx.compose.material.icons.outlined.ContentCopy
import androidx.compose.material.icons.outlined.Key
import androidx.compose.material.icons.outlined.Lock
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import androidx.compose.ui.text.font.FontFamily
import androidx.hilt.navigation.compose.hiltViewModel
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.android.EntryPointAccessors
import dagger.hilt.components.SingletonComponent
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.astermail.android.auth.AuthRepository
import org.astermail.android.crypto.CryptoNative
import org.astermail.android.R
import org.astermail.android.design.AsterMaterial
import org.astermail.android.design.AsterSpacing
import org.astermail.android.design.components.AsterButton
import org.astermail.android.design.components.AsterCard
import org.astermail.android.design.components.AsterDivider
import org.astermail.android.design.components.AsterGhostButton
import org.astermail.android.design.components.AsterSecondaryButton
import org.astermail.android.settings.SettingsViewModel
import org.astermail.android.storage.SessionKeyStore
import java.security.MessageDigest

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
    val colors = AsterMaterial.colors
    val context = LocalContext.current
    val vm: SettingsViewModel = hiltViewModel()
    val deps = remember(context) {
        EntryPointAccessors.fromApplication(
            context.applicationContext,
            EncryptionScreenDeps::class.java,
        )
    }
    val session_key_store = remember(deps) { deps.session_key_store() }
    val auth_repository = remember(deps) { deps.auth_repository() }

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
                    val hex_bytes = digest.map { String.format(java.util.Locale.US, "%02X", it.toInt() and 0xFF) }
                    hex_bytes.chunked(4).joinToString("\n") { it.joinToString(" ") }
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
    val email = view?.email
    val key_available = view?.available == true
    val is_pgp_key = view?.is_pgp == true
    var show_full_key by remember { mutableStateOf(false) }

    detail_scaffold(title = stringResource(R.string.encryption_title), on_back = on_back) {
        AsterCard(modifier = Modifier.fillMaxWidth()) {
            Column(modifier = Modifier.padding(AsterSpacing.lg)) {
                Text(
                    text = stringResource(R.string.encryption_status_label),
                    color = colors.text_primary,
                    fontSize = 17.sp,
                    fontWeight = FontWeight.SemiBold,
                )
                Spacer(Modifier.size(AsterSpacing.xs))
                Text(
                    text = stringResource(R.string.encryption_status_description),
                    color = colors.text_secondary,
                    fontSize = 14.sp,
                )
            }
        }
        v_gap(AsterSpacing.lg)
        section_label(stringResource(R.string.encryption_status))
        AsterCard(modifier = Modifier.fillMaxWidth()) {
            detail_row(
                title = stringResource(R.string.identity_key),
                subtitle = stringResource(R.string.identity_key_active),
                icon = Icons.Outlined.Key,
            )
            AsterDivider(modifier = Modifier)
            detail_row(
                title = stringResource(R.string.encryption_protocol),
                subtitle = stringResource(R.string.encryption_protocol_value),
                icon = Icons.Outlined.Lock,
            )
            AsterDivider(modifier = Modifier)
            detail_row(
                title = stringResource(R.string.zero_access),
                subtitle = stringResource(R.string.zero_access_subtitle),
                icon = Icons.Outlined.Check,
            )
        }
        v_gap(AsterSpacing.lg)

        if (email != null) {
            section_label(stringResource(R.string.account))
            AsterCard(modifier = Modifier.fillMaxWidth()) {
                detail_row(title = email)
            }
            v_gap(AsterSpacing.lg)
        }

        if (view == null) {
            AsterCard(modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(AsterSpacing.lg)) {
                    Text(
                        text = stringResource(R.string.loading_identity_key),
                        color = colors.text_tertiary,
                        fontSize = 13.sp,
                    )
                }
            }
            v_gap(AsterSpacing.lg)
        } else if (key_available && fingerprint != null) {
            section_label(if (is_pgp_key) stringResource(R.string.pgp_identity_fingerprint) else stringResource(R.string.identity_key_fingerprint))
            AsterCard(modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(AsterSpacing.lg)) {
                    Text(
                        text = fingerprint,
                        color = colors.text_primary,
                        fontSize = 14.sp,
                        fontFamily = FontFamily.Monospace,
                    )
                    Spacer(Modifier.size(AsterSpacing.sm))
                    Text(
                        text = stringResource(R.string.fingerprint_description),
                        color = colors.text_tertiary,
                        fontSize = 12.sp,
                    )
                }
            }
            v_gap(AsterSpacing.md)
            AsterButton(
                label = stringResource(R.string.copy_fingerprint),
                onClick = {
                    copy_to_clipboard(context, context.getString(R.string.clipboard_label_identity_fingerprint), fingerprint)
                    Toast.makeText(context, context.getString(R.string.fingerprint_copied), Toast.LENGTH_SHORT).show()
                },
            )
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
            v_gap(AsterSpacing.lg)
        } else {
            AsterCard(modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(AsterSpacing.lg)) {
                    Text(
                        text = stringResource(R.string.identity_key_unavailable),
                        color = colors.text_tertiary,
                        fontSize = 13.sp,
                    )
                }
            }
            v_gap(AsterSpacing.lg)
        }

        section_label(stringResource(R.string.recovery))
        AsterCard(modifier = Modifier.fillMaxWidth()) {
            detail_row(
                title = stringResource(R.string.recovery_key),
                subtitle = stringResource(R.string.view_recovery_codes),
                icon = Icons.Outlined.Key,
                on_click = { on_open("recovery_key_view") },
            )
        }
        v_gap(AsterSpacing.lg)

        section_label(stringResource(R.string.key_rotation))
        AsterCard(modifier = Modifier.fillMaxWidth()) {
            Column(modifier = Modifier.padding(AsterSpacing.lg)) {
                Text(
                    text = stringResource(R.string.key_rotation_description),
                    color = colors.text_secondary,
                    fontSize = 13.sp,
                )
            }
        }
        v_gap(AsterSpacing.lg)

        section_label(stringResource(R.string.key_management))
        Text(
            text = stringResource(R.string.key_management_description),
            color = colors.text_tertiary,
            fontSize = 13.sp,
        )
        v_gap(AsterSpacing.lg)
        AsterSecondaryButton(
            label = stringResource(R.string.open_encryption_settings),
            onClick = {
                context.startActivity(
                    Intent(Intent.ACTION_VIEW, Uri.parse("https://app.astermail.org/settings/encryption")),
                )
            },
        )
        v_gap(AsterSpacing.xxl)
    }
}

private fun copy_to_clipboard(context: Context, label: String, value: String) {
    val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
    val clip = ClipData.newPlainText(label, value)
    clip.description.extras = android.os.PersistableBundle().apply {
        putBoolean("android.content.extra.IS_SENSITIVE", true)
    }
    clipboard.setPrimaryClip(clip)
}
