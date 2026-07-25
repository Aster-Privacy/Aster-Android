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

package org.astermail.android.ui.settings

import compose.icons.TablerIcons
import compose.icons.tablericons.*

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.res.stringResource
import androidx.hilt.navigation.compose.hiltViewModel
import org.astermail.android.R
import org.astermail.android.design.SquircleShape
import org.astermail.android.design.AsterMaterial
import org.astermail.android.design.AsterRadius
import org.astermail.android.design.AsterSpacing
import org.astermail.android.design.components.AsterDivider
import org.astermail.android.design.components.AsterTopBar
import org.astermail.android.settings.SettingsViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import org.astermail.android.ui.common.current_user_avatar

data class settings_row_item(
    val id: String,
    val title_res: Int,
    val subtitle_res: Int? = null,
    val icon: ImageVector,
)

data class settings_section(
    val title_res: Int,
    val rows: List<settings_row_item>,
)

private fun build_settings_sections(is_family: Boolean) = listOf(
    settings_section(
        R.string.settings_account,
        buildList {
            add(settings_row_item("profile", R.string.settings_profile, icon = TablerIcons.User))
            add(settings_row_item("aliases", R.string.settings_aliases, icon = TablerIcons.At))
if (is_family) add(settings_row_item("family", R.string.settings_family, icon = TablerIcons.Users))
            add(settings_row_item("billing", R.string.settings_plans_billing, icon = TablerIcons.CreditCard))
            add(settings_row_item("storage", R.string.settings_storage, icon = TablerIcons.Database))
        },
    ),
    settings_section(
        R.string.settings_section_security,
        listOf(
            settings_row_item("security", R.string.settings_security, icon = TablerIcons.Shield),
            settings_row_item("encryption", R.string.settings_encryption, icon = TablerIcons.Key),
        ),
    ),
    settings_section(
        R.string.settings_section_mail,
        listOf(
            settings_row_item("notifications", R.string.settings_notifications, icon = TablerIcons.Bell),
            settings_row_item("signature", R.string.settings_signature, icon = TablerIcons.Edit),
            settings_row_item("templates", R.string.settings_templates, icon = TablerIcons.FileText),
            settings_row_item("behavior", R.string.settings_behavior, icon = TablerIcons.ArrowBackUp),
            settings_row_item("swipe_actions", R.string.settings_swipe_actions, icon = TablerIcons.ArrowsLeftRight),
            settings_row_item("mail_rules", R.string.mail_rules_title, icon = TablerIcons.Adjustments),
            settings_row_item("folders", R.string.folders, icon = TablerIcons.Folder),
            settings_row_item("auto_forward", R.string.settings_auto_forward, icon = TablerIcons.MailForward),
            settings_row_item("vacation_reply", R.string.settings_vacation_reply, icon = TablerIcons.MailOpened),
        ),
    ),
    settings_section(
        R.string.settings_section_appearance,
        listOf(
            settings_row_item("appearance", R.string.settings_appearance, icon = TablerIcons.Palette),
            settings_row_item("accessibility", R.string.settings_accessibility, icon = TablerIcons.Typography),
        ),
    ),
    settings_section(
        R.string.settings_section_data,
        listOf(
            settings_row_item("external_accounts", R.string.external_accounts, icon = TablerIcons.ArrowsRightLeft),
            settings_row_item("import", R.string.settings_import, icon = TablerIcons.CloudUpload),
            settings_row_item("export", R.string.settings_export, icon = TablerIcons.CloudDownload),
            settings_row_item("connection", R.string.settings_connection, icon = TablerIcons.Wifi),
        ),
    ),
    settings_section(
        R.string.settings_section_other,
        listOf(
            settings_row_item("feedback", R.string.settings_feedback, icon = TablerIcons.MessageReport),
            settings_row_item("about", R.string.about, icon = TablerIcons.InfoCircle),
            settings_row_item("developer", R.string.developer, icon = TablerIcons.Code),
            settings_row_item("diagnostics", R.string.settings_diagnostics, icon = TablerIcons.Bug),
        ),
    ),
)

@Composable
fun SettingsScreen(
    on_back: () -> Unit,
    on_open: (String) -> Unit,
) {
    val colors = AsterMaterial.colors
    val settings_vm: SettingsViewModel = hiltViewModel()
    val settings_state by settings_vm.state.collectAsStateWithLifecycle()
    LaunchedEffect(Unit) {
        if (settings_state.user == null) settings_vm.load_profile()
        if (settings_state.subscription == null) settings_vm.load_subscription()
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(colors.bg_primary)
            .systemBarsPadding(),
    ) {
        AsterTopBar(title = stringResource(R.string.settings), on_back = on_back)
        AsterDivider()
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState()),
        ) {
            Spacer(Modifier.size(AsterSpacing.md))
            val live_account by settings_vm.account_store.current_account.collectAsStateWithLifecycle(
                initialValue = settings_vm.account_store.get_current()
            )
            profile_header(
                account_store = settings_vm.account_store,
                display_name = live_account?.display_name?.takeIf { it.isNotBlank() }
                    ?: settings_state.user?.display_name?.ifBlank { null }
                    ?: settings_state.user?.username
                    ?: "",
                username = settings_state.user?.username ?: live_account?.email?.substringBefore("@") ?: "",
                email = settings_state.user?.email ?: live_account?.email ?: "",
                subscription = settings_state.subscription,
                on_click = { on_open("profile") },
                on_upgrade = { on_open("billing") },
            )
            Spacer(Modifier.size(AsterSpacing.lg))
            val is_family = settings_state.subscription?.effective_plan_name
                ?.contains("family", ignoreCase = true) == true
            val sections = build_settings_sections(is_family)
            sections.forEach { section ->
                section_header(stringResource(section.title_res))
                Column(
                    modifier = Modifier
                        .padding(horizontal = AsterSpacing.md)
                        .fillMaxWidth()
                        .background(colors.bg_card, SquircleShape(18.dp))
                        .border(1.dp, colors.border_secondary, SquircleShape(18.dp)),
                ) {
                    section.rows.forEachIndexed { idx, row ->
                        settings_row(row) { on_open(row.id) }
                        if (idx < section.rows.lastIndex) {
                            AsterDivider(modifier = Modifier.padding(start = 50.dp))
                        }
                    }
                }
                Spacer(Modifier.size(AsterSpacing.md))
            }
            Spacer(Modifier.size(AsterSpacing.xxl))
        }
    }
}

@Composable
private fun profile_header(
    account_store: org.astermail.android.storage.AccountStore,
    display_name: String,
    username: String,
    email: String,
    subscription: org.astermail.android.api.settings.SubscriptionInfo?,
    on_click: () -> Unit,
    on_upgrade: () -> Unit,
) {
    val colors = AsterMaterial.colors
    val free_label = stringResource(R.string.plan_free)
    val plan_name = subscription?.effective_plan_name
    val is_free = subscription == null || (
        subscription.effective_price_cents == 0 ||
            (!plan_name.isNullOrBlank() && plan_name.trim().equals(free_label, ignoreCase = true))
        )

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = AsterSpacing.lg, vertical = 8.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .clickable(onClick = on_click),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            current_user_avatar(
                account_store = account_store,
                size = 88.dp,
            )
            Spacer(Modifier.size(14.dp))
            Text(
                text = display_name.ifBlank { username.ifBlank { stringResource(R.string.settings_profile) } },
                color = colors.text_primary,
                fontSize = 20.sp,
                fontWeight = FontWeight.SemiBold,
            )
            if (email.isNotBlank()) {
                Spacer(Modifier.size(2.dp))
                Text(
                    text = email,
                    color = colors.text_tertiary,
                    fontSize = 13.sp,
                )
            }
        }
        if (is_free) {
            Spacer(Modifier.size(12.dp))
            Box(
                modifier = Modifier
                    .clip(SquircleShape(999.dp))
                    .background(colors.accent_blue)
                    .clickable(onClick = on_upgrade)
                    .padding(horizontal = 18.dp, vertical = 8.dp),
            ) {
                Text(
                    text = stringResource(R.string.settings_upgrade_cta),
                    color = Color.White,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.SemiBold,
                )
            }
        }
    }
}

@Composable
private fun section_header(title: String) {
    val colors = AsterMaterial.colors
    Text(
        text = title.uppercase(),
        style = MaterialTheme.typography.labelSmall,
        color = colors.text_tertiary,
        fontSize = 11.sp,
        fontWeight = FontWeight.SemiBold,
        letterSpacing = 0.8.sp,
        modifier = Modifier.padding(
            start = AsterSpacing.xl,
            end = AsterSpacing.lg,
            top = AsterSpacing.md,
            bottom = AsterSpacing.md,
        ),
    )
}

@Composable
private fun settings_row(row: settings_row_item, on_click: () -> Unit) {
    val colors = AsterMaterial.colors
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = on_click)
            .heightIn(min = 52.dp)
            .padding(horizontal = AsterSpacing.lg, vertical = AsterSpacing.sm),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            imageVector = row.icon,
            contentDescription = null,
            tint = colors.text_secondary,
            modifier = Modifier.size(22.dp),
        )
        Spacer(Modifier.width(AsterSpacing.md))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = stringResource(row.title_res),
                color = colors.text_primary,
                fontSize = 15.sp,
                fontWeight = FontWeight.Medium,
            )
            if (row.subtitle_res != null) {
                Text(
                    text = stringResource(row.subtitle_res),
                    color = colors.text_tertiary,
                    fontSize = 12.sp,
                )
            }
        }
        Icon(
            imageVector = TablerIcons.ChevronRight,
            contentDescription = null,
            tint = colors.text_tertiary,
            modifier = Modifier.size(18.dp),
        )
    }
}
