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
import androidx.compose.foundation.layout.height
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
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
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
import org.astermail.android.design.components.shimmer_brush
import org.astermail.android.settings.SettingsViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import org.astermail.android.ui.common.current_user_avatar
import org.astermail.android.ui.common.plan_ring
import org.astermail.android.ui.common.remember_has_paid_plan
import org.astermail.android.ui.mail.search_field_bg_color
import org.astermail.android.settings.shared_settings_view_model
import org.astermail.android.design.mirror_in_rtl

private const val support_address = "hello@astermail.org"

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

internal fun build_settings_sections(is_family: Boolean) = listOf(
    settings_section(
        R.string.settings_general,
        buildList {
            add(settings_row_item("appearance", R.string.settings_appearance, icon = TablerIcons.Palette))
            add(settings_row_item("accessibility", R.string.settings_accessibility, icon = TablerIcons.Typography))
            add(settings_row_item("security", R.string.settings_security, icon = TablerIcons.Shield))
            add(settings_row_item("encryption", R.string.settings_encryption, icon = TablerIcons.Key))
            add(settings_row_item("trusted_devices", R.string.trusted_devices, icon = TablerIcons.DeviceDesktop))
            add(settings_row_item("aliases", R.string.settings_aliases, icon = TablerIcons.At))
            add(settings_row_item("domains", R.string.settings_domains, icon = TablerIcons.World))
            add(settings_row_item("billing", R.string.settings_plans_billing, icon = TablerIcons.CreditCard))
            add(settings_row_item("storage", R.string.settings_storage, icon = TablerIcons.Database))
            add(settings_row_item("referral", R.string.refer_a_friend, icon = TablerIcons.Users))
            if (is_family) add(settings_row_item("family", R.string.settings_family, icon = TablerIcons.Home))
        },
    ),
    settings_section(
        R.string.settings_section_mail,
        listOf(
            settings_row_item("notifications", R.string.settings_notifications, icon = TablerIcons.Bell),
            settings_row_item("behavior", R.string.settings_behavior, icon = TablerIcons.ArrowBackUp),
            settings_row_item("swipe_actions", R.string.settings_swipe_actions, icon = TablerIcons.ArrowsLeftRight),
            settings_row_item("customize_toolbar", R.string.customize_toolbar, icon = TablerIcons.LayoutBottombar),
            settings_row_item("signature", R.string.settings_signature, icon = TablerIcons.Edit),
            settings_row_item("templates", R.string.settings_templates, icon = TablerIcons.FileText),
            settings_row_item("import", R.string.settings_import, icon = TablerIcons.CloudUpload),
            settings_row_item("external_accounts", R.string.external_accounts, icon = TablerIcons.ArrowsRightLeft),
            settings_row_item("sender_filters", R.string.mail_management, icon = TablerIcons.Filter),
            settings_row_item("mail_rules", R.string.mail_rules_title, icon = TablerIcons.Bolt),
            settings_row_item("folders", R.string.folders, icon = TablerIcons.Folder),
            settings_row_item("labels", R.string.labels, icon = TablerIcons.Tag),
        ),
    ),
    settings_section(
        R.string.settings_advanced,
        listOf(
            settings_row_item("about", R.string.about, icon = TablerIcons.InfoCircle),
            settings_row_item("contact_support", R.string.contact_support, icon = TablerIcons.Lifebuoy),
            settings_row_item("feedback", R.string.settings_feedback, icon = TablerIcons.MessageReport),
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
    val context = LocalContext.current
    val settings_vm: SettingsViewModel = shared_settings_view_model()
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
        AsterTopBar(
            title = stringResource(R.string.settings),
            on_back = on_back,
            trailing = { settings_search_action() },
        )
        AsterDivider()
        val is_family = settings_state.subscription?.effective_plan_name
            ?.contains("family", ignoreCase = true) == true
        val sections = build_settings_sections(is_family)
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState()),
        ) {
            Spacer(Modifier.size(AsterSpacing.md))
            val live_account by settings_vm.account_store.current_account.collectAsStateWithLifecycle(
                initialValue = settings_vm.account_store.get_current()
            )
            val cached_display_name = live_account?.display_name?.takeIf { it.isNotBlank() }
            profile_header(
                account_store = settings_vm.account_store,
                profile_picture_url = settings_state.user?.profile_picture,
                display_name = cached_display_name
                    ?: settings_state.user?.display_name?.ifBlank { null }
                    ?: settings_state.user?.username
                    ?: "",
                username = settings_state.user?.username ?: live_account?.email?.substringBefore("@") ?: "",
                email = settings_state.user?.email ?: live_account?.email ?: "",
                subscription = settings_state.subscription,
                profile_loading = (settings_state.user == null && live_account == null) ||
                    (settings_state.user == null && cached_display_name == null && settings_state.is_loading),
                plan_loading = settings_state.subscription == null,
                on_click = { on_open("profile") },
                on_upgrade = { on_open("billing") },
            )
            Spacer(Modifier.size(AsterSpacing.lg))
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
                        settings_row(row) {
                            if (row.id == "contact_support") {
                                context.startActivity(
                                    org.astermail.android.ComposeActivity.intent_for(
                                        context,
                                        prefill_to = support_address,
                                    ),
                                )
                            } else {
                                on_open(row.id)
                            }
                        }
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
    profile_picture_url: String?,
    display_name: String,
    username: String,
    email: String,
    subscription: org.astermail.android.api.settings.SubscriptionInfo?,
    profile_loading: Boolean,
    plan_loading: Boolean,
    on_click: () -> Unit,
    on_upgrade: () -> Unit,
) {
    val colors = AsterMaterial.colors
    val free_label = stringResource(R.string.plan_free)
    val plan_name = subscription?.effective_plan_name
    val is_free = subscription != null && (
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
            plan_ring(size = 88.dp, enabled = remember_has_paid_plan()) {
                current_user_avatar(
                    account_store = account_store,
                    size = 88.dp,
                    profile_picture_url = profile_picture_url,
                )
            }
            Spacer(Modifier.size(14.dp))
            if (profile_loading) {
                Box(
                    modifier = Modifier
                        .width(150.dp)
                        .height(20.dp)
                        .background(shimmer_brush(), SquircleShape(6.dp)),
                )
                Spacer(Modifier.size(6.dp))
                Box(
                    modifier = Modifier
                        .width(196.dp)
                        .height(13.dp)
                        .background(shimmer_brush(), SquircleShape(6.dp)),
                )
            } else {
                Text(
                    text = display_name.ifBlank { username.ifBlank { stringResource(R.string.settings_profile) } },
                    color = colors.text_primary,
                    fontSize = 20.sp,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
                )
                if (email.isNotBlank()) {
                    Spacer(Modifier.size(2.dp))
                    Text(
                        text = email,
                        color = colors.text_tertiary,
                        fontSize = 13.sp,
                        maxLines = 1,
                        overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
                    )
                }
            }
        }
        if (plan_loading) {
            Spacer(Modifier.size(12.dp))
            Box(
                modifier = Modifier
                    .width(112.dp)
                    .height(33.dp)
                    .background(shimmer_brush(), SquircleShape(999.dp)),
            )
        } else if (is_free) {
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
internal fun settings_row(row: settings_row_item, on_click: () -> Unit) {
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
                maxLines = 1,
                overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
            )
            if (row.subtitle_res != null) {
                Text(
                    text = stringResource(row.subtitle_res),
                    color = colors.text_tertiary,
                    fontSize = 12.sp,
                    maxLines = 1,
                    overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
                )
            }
        }
        Icon(
            imageVector = TablerIcons.ChevronRight,
            contentDescription = null,
            tint = colors.text_tertiary,
            modifier = Modifier.size(18.dp).mirror_in_rtl(),
        )
    }
}
