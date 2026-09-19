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
import org.astermail.android.design.components.shimmer
import org.astermail.android.design.components.shimmer_state
import org.astermail.android.settings.SettingsViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import org.astermail.android.ui.common.current_user_avatar
import org.astermail.android.ui.common.plan_ring
import org.astermail.android.ui.common.remember_has_paid_plan
import org.astermail.android.ui.mail.search_field_bg_color
import org.astermail.android.settings.shared_settings_view_model
import org.astermail.android.design.mirror_in_rtl
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.ui.platform.testTag
import org.astermail.android.design.AsterColorThemes
import org.astermail.android.design.ColorThemeId
import org.astermail.android.storage.ThemeMode
import org.astermail.android.ui.theme.ThemeViewModel
import org.astermail.android.ui.settings.detail.color_theme_label_res
import org.astermail.android.ui.settings.detail.mail_mock
import org.astermail.android.ui.settings.detail.preview_colors_of

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
        listOf(
            settings_row_item("appearance", R.string.settings_appearance, icon = TablerIcons.Palette),
            settings_row_item("accessibility", R.string.settings_accessibility, icon = TablerIcons.Typography),
        ),
    ),
    settings_section(
        R.string.settings_security,
        listOf(
            settings_row_item("security", R.string.settings_security, icon = TablerIcons.Shield),
            settings_row_item("encryption", R.string.settings_encryption, icon = TablerIcons.Key),
            settings_row_item("trusted_devices", R.string.trusted_devices, icon = TablerIcons.DeviceDesktop),
            settings_row_item("connection", R.string.settings_connection, icon = TablerIcons.Wifi),
        ),
    ),
    settings_section(
        R.string.settings_aliases_and_domains,
        listOf(
            settings_row_item("aliases", R.string.settings_aliases, icon = TablerIcons.At),
            settings_row_item("domains", R.string.settings_domains, icon = TablerIcons.World),
        ),
    ),
    settings_section(
        R.string.settings_billing,
        buildList {
            add(settings_row_item("storage", R.string.settings_storage, icon = TablerIcons.Database))
            add(settings_row_item("billing", R.string.settings_plans_billing, icon = TablerIcons.CreditCard))
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
        ),
    ),
    settings_section(
        R.string.settings_section_writing,
        listOf(
            settings_row_item("signature", R.string.settings_signature, icon = TablerIcons.Edit),
            settings_row_item("templates", R.string.settings_templates, icon = TablerIcons.FileText),
        ),
    ),
    settings_section(
        R.string.settings_section_organization,
        listOf(
            settings_row_item("sender_filters", R.string.mail_management, icon = TablerIcons.Filter),
            settings_row_item("mail_rules", R.string.mail_rules_title, icon = TablerIcons.Bolt),
            settings_row_item("folders", R.string.folders, icon = TablerIcons.Folder),
            settings_row_item("labels", R.string.labels, icon = TablerIcons.Tag),
        ),
    ),
    settings_section(
        R.string.tools,
        listOf(
            settings_row_item("import", R.string.settings_import, icon = TablerIcons.CloudUpload),
            settings_row_item("external_accounts", R.string.external_accounts, icon = TablerIcons.ArrowsRightLeft),
            settings_row_item("smtp_tokens", R.string.settings_smtp_tokens, icon = TablerIcons.Send),
        ),
    ),
    settings_section(
        R.string.settings_section_support,
        listOf(
            settings_row_item("contact_support", R.string.contact_support, icon = TablerIcons.Lifebuoy),
            settings_row_item("feedback", R.string.settings_feedback, icon = TablerIcons.MessageReport),
            settings_row_item("about", R.string.about, icon = TablerIcons.InfoCircle),
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
            appearance_feature_card(on_click = { on_open("appearance") })
            Spacer(Modifier.size(AsterSpacing.sm))
            sections.forEach { raw_section ->
                val section = raw_section.copy(rows = raw_section.rows.filter { it.id != "appearance" })
                if (section.rows.isEmpty()) return@forEach
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
                            AsterDivider(modifier = Modifier.padding(start = 62.dp))
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
    val card_shape = SquircleShape(22.dp)

    Column(
        modifier = Modifier
            .padding(horizontal = AsterSpacing.md)
            .fillMaxWidth()
            .clip(card_shape)
            .background(colors.bg_card)
            .border(1.dp, colors.border_secondary, card_shape),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clickable(onClick = on_click)
                .padding(AsterSpacing.lg),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            plan_ring(size = 60.dp, enabled = remember_has_paid_plan()) {
                current_user_avatar(
                    account_store = account_store,
                    size = 60.dp,
                    profile_picture_url = profile_picture_url,
                )
            }
            Spacer(Modifier.width(14.dp))
            Column(modifier = Modifier.weight(1f)) {
                if (profile_loading) {
                    val profile_shimmer = shimmer_state()
                    Box(
                        modifier = Modifier
                            .width(140.dp)
                            .height(18.dp)
                            .shimmer(profile_shimmer, SquircleShape(6.dp)),
                    )
                    Spacer(Modifier.size(6.dp))
                    Box(
                        modifier = Modifier
                            .width(180.dp)
                            .height(12.dp)
                            .shimmer(profile_shimmer, SquircleShape(6.dp)),
                    )
                } else {
                    Text(
                        text = display_name.ifBlank { username.ifBlank { stringResource(R.string.settings_profile) } },
                        color = colors.text_primary,
                        fontSize = 18.sp,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 1,
                        overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
                    )
                    if (email.isNotBlank()) {
                        Spacer(Modifier.size(1.dp))
                        Text(
                            text = email,
                            color = colors.text_tertiary,
                            fontSize = 13.sp,
                            maxLines = 1,
                            overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
                        )
                    }
                    if (!plan_loading && !plan_name.isNullOrBlank()) {
                        Spacer(Modifier.size(6.dp))
                        Text(
                            text = plan_name,
                            color = if (is_free) colors.text_secondary else colors.accent_blue,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.SemiBold,
                            maxLines = 1,
                            modifier = Modifier
                                .clip(SquircleShape(999.dp))
                                .background(
                                    if (is_free) colors.text_primary.copy(alpha = 0.08f) else colors.accent_blue.copy(alpha = 0.14f),
                                )
                                .padding(horizontal = 8.dp, vertical = 3.dp),
                        )
                    }
                }
            }
            Icon(
                imageVector = TablerIcons.ChevronRight,
                contentDescription = null,
                tint = colors.text_tertiary,
                modifier = Modifier.size(18.dp).mirror_in_rtl(),
            )
        }
        if (is_free && !plan_loading) {
            AsterDivider()
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable(onClick = on_upgrade)
                    .padding(horizontal = AsterSpacing.lg, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(
                    imageVector = TablerIcons.Crown,
                    contentDescription = null,
                    tint = colors.accent_blue,
                    modifier = Modifier.size(18.dp),
                )
                Spacer(Modifier.width(10.dp))
                Text(
                    text = stringResource(R.string.settings_upgrade_cta),
                    color = colors.accent_blue,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.weight(1f),
                )
                Icon(
                    imageVector = TablerIcons.ChevronRight,
                    contentDescription = null,
                    tint = colors.accent_blue,
                    modifier = Modifier.size(16.dp).mirror_in_rtl(),
                )
            }
        }
    }
}

@Composable
private fun appearance_feature_card(on_click: () -> Unit) {
    val colors = AsterMaterial.colors
    val theme_vm: ThemeViewModel = hiltViewModel()
    val mode by theme_vm.theme_mode.collectAsStateWithLifecycle()
    val color_theme_key by theme_vm.color_theme.collectAsStateWithLifecycle()
    val color_theme = ColorThemeId.from_key(color_theme_key)
    val theme_label = stringResource(color_theme_label_res(color_theme))
    val mode_label = stringResource(
        when (mode) {
            ThemeMode.system -> R.string.theme_system
            ThemeMode.light -> R.string.theme_light
            ThemeMode.dark -> R.string.theme_dark
        },
    )
    val card_shape = SquircleShape(22.dp)
    val mock_shape = SquircleShape(12.dp)
    Row(
        modifier = Modifier
            .padding(horizontal = AsterSpacing.md)
            .fillMaxWidth()
            .clip(card_shape)
            .background(colors.bg_card)
            .border(1.dp, colors.border_secondary, card_shape)
            .clickable(onClick = on_click)
            .padding(AsterSpacing.md)
            .testTag("settings_appearance_card"),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier = Modifier
                .width(78.dp)
                .height(96.dp)
                .clip(mock_shape)
                .border(1.dp, colors.border_primary, mock_shape),
        ) {
            mail_mock(preview_colors_of(colors), Modifier.fillMaxSize(), rows = 4)
        }
        Spacer(Modifier.width(AsterSpacing.lg))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = stringResource(R.string.settings_appearance),
                color = colors.text_primary,
                fontSize = 16.sp,
                fontWeight = FontWeight.SemiBold,
            )
            Spacer(Modifier.size(2.dp))
            Text(
                text = stringResource(R.string.settings_appearance_summary),
                color = colors.text_tertiary,
                fontSize = 13.sp,
                maxLines = 2,
            )
            Spacer(Modifier.size(10.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier
                        .size(10.dp)
                        .clip(CircleShape)
                        .background(colors.accent_blue),
                )
                Spacer(Modifier.width(6.dp))
                Text(
                    text = if (AsterColorThemes.is_dark_only(color_theme)) theme_label else "$theme_label · $mode_label",
                    color = colors.text_secondary,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Medium,
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

private fun row_tint(id: String): Color = when (id) {
    "appearance" -> Color(0xFF8B5CF6)
    "accessibility" -> Color(0xFF3B82F6)
    "security" -> Color(0xFF10B981)
    "encryption" -> Color(0xFF14B8A6)
    "trusted_devices" -> Color(0xFF6366F1)
    "connection" -> Color(0xFF0EA5E9)
    "aliases" -> Color(0xFFEC4899)
    "domains" -> Color(0xFF06B6D4)
    "storage" -> Color(0xFF64748B)
    "billing" -> Color(0xFFF59E0B)
    "referral" -> Color(0xFFF97316)
    "family" -> Color(0xFFE11D48)
    "notifications" -> Color(0xFFEF4444)
    "behavior" -> Color(0xFF3B82F6)
    "swipe_actions" -> Color(0xFF8B5CF6)
    "customize_toolbar" -> Color(0xFF6366F1)
    "signature" -> Color(0xFFF97316)
    "templates" -> Color(0xFF0EA5E9)
    "sender_filters" -> Color(0xFF10B981)
    "mail_rules" -> Color(0xFFF59E0B)
    "folders" -> Color(0xFF3B82F6)
    "labels" -> Color(0xFFEC4899)
    "import" -> Color(0xFF06B6D4)
    "external_accounts" -> Color(0xFF6366F1)
    "smtp_tokens" -> Color(0xFF14B8A6)
    "contact_support" -> Color(0xFF10B981)
    "feedback" -> Color(0xFFF59E0B)
    else -> Color(0xFF64748B)
}

@Composable
private fun section_header(title: String) {
    val colors = AsterMaterial.colors
    Text(
        text = title,
        style = MaterialTheme.typography.labelLarge,
        color = colors.text_secondary,
        fontSize = 13.sp,
        fontWeight = FontWeight.SemiBold,
        modifier = Modifier.padding(
            start = AsterSpacing.xl,
            end = AsterSpacing.lg,
            top = AsterSpacing.md,
            bottom = AsterSpacing.sm,
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
        val tint = row_tint(row.id)
        Box(
            modifier = Modifier
                .size(32.dp)
                .clip(SquircleShape(10.dp))
                .background(tint.copy(alpha = if (colors.is_dark) 0.18f else 0.12f)),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                imageVector = row.icon,
                contentDescription = null,
                tint = tint,
                modifier = Modifier.size(18.dp),
            )
        }
        Spacer(Modifier.width(14.dp))
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
