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

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import org.astermail.android.BuildConfig
import org.astermail.android.R
import org.astermail.android.design.SquircleShape
import org.astermail.android.design.AsterMaterial
import org.astermail.android.design.AsterSpacing
import org.astermail.android.design.components.AsterDivider
import org.astermail.android.design.components.AsterTopBar
import org.astermail.android.design.components.shimmer_brush
import org.astermail.android.settings.SettingsUiState
import org.astermail.android.settings.SettingsViewModel
import org.astermail.android.settings.shared_settings_view_model
import org.astermail.android.ui.common.current_user_avatar
import org.astermail.android.ui.common.open_external_url
import org.astermail.android.ui.common.plan_ring
import org.astermail.android.ui.common.remember_has_paid_plan
import org.astermail.android.ui.settings.detail.format_bytes
import org.astermail.android.design.mirror_in_rtl

private const val support_address = "hello@astermail.org"
private const val terms_url = "https://astermail.org/terms"
private const val privacy_url = "https://astermail.org/privacy"

private val promoted_row_ids = setOf("billing", "referral")

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

private data class settings_quick_action(
    val id: String,
    val label_res: Int,
    val icon: ImageVector,
)

private val settings_quick_actions = listOf(
    settings_quick_action("aliases", R.string.settings_aliases, TablerIcons.At),
    settings_quick_action("security", R.string.settings_security, TablerIcons.Shield),
    settings_quick_action("storage", R.string.settings_storage, TablerIcons.Database),
    settings_quick_action("notifications", R.string.settings_notifications, TablerIcons.Bell),
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
        if (settings_state.storage == null) settings_vm.load_storage()
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
            Spacer(Modifier.size(AsterSpacing.lg))
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
                profile_loading = (settings_state.user == null && live_account == null) ||
                    (settings_state.user == null && cached_display_name == null && settings_state.is_loading),
                on_click = { on_open("profile") },
            )
            Spacer(Modifier.size(AsterSpacing.md))
            quick_action_grid(on_open = on_open)
            Spacer(Modifier.size(AsterSpacing.sm))
            feature_tile_row(
                subscription = settings_state.subscription,
                plan_loading = settings_state.subscription == null,
                on_open_billing = { on_open("billing") },
                on_open_referral = { on_open("referral") },
            )
            sections.forEach { section ->
                val rows = section.rows.filterNot { promoted_row_ids.contains(it.id) }
                if (rows.isEmpty()) return@forEach
                section_header(stringResource(section.title_res))
                Column(
                    modifier = Modifier
                        .padding(horizontal = AsterSpacing.lg)
                        .fillMaxWidth()
                        .background(colors.bg_card, SquircleShape(18.dp))
                        .border(1.dp, colors.border_secondary, SquircleShape(18.dp)),
                ) {
                    rows.forEachIndexed { idx, row ->
                        settings_row(row, settings_row_value(row.id, settings_state)) {
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
                        if (idx < rows.lastIndex) {
                            AsterDivider(modifier = Modifier.padding(start = 58.dp))
                        }
                    }
                }
            }
            settings_footer()
            Spacer(Modifier.size(AsterSpacing.xxl))
        }
    }
}

private fun settings_row_value(id: String, state: SettingsUiState): String? = when (id) {
    "storage" -> state.storage?.let { format_bytes(it.used_bytes) }
    else -> null
}

@Composable
private fun profile_header(
    account_store: org.astermail.android.storage.AccountStore,
    profile_picture_url: String?,
    display_name: String,
    username: String,
    email: String,
    profile_loading: Boolean,
    on_click: () -> Unit,
) {
    val colors = AsterMaterial.colors
    val shape = SquircleShape(22.dp)
    Row(
        modifier = Modifier
            .padding(horizontal = AsterSpacing.lg)
            .fillMaxWidth()
            .clip(shape)
            .background(colors.bg_card)
            .border(1.dp, colors.border_secondary, shape)
            .clickable(onClick = on_click)
            .padding(horizontal = AsterSpacing.lg, vertical = AsterSpacing.lg),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        plan_ring(size = 60.dp, enabled = remember_has_paid_plan()) {
            current_user_avatar(
                account_store = account_store,
                size = 60.dp,
                profile_picture_url = profile_picture_url,
            )
        }
        Spacer(Modifier.width(AsterSpacing.lg))
        Column(modifier = Modifier.weight(1f)) {
            if (profile_loading) {
                Box(
                    modifier = Modifier
                        .width(140.dp)
                        .height(18.dp)
                        .background(shimmer_brush(), SquircleShape(6.dp)),
                )
                Spacer(Modifier.size(7.dp))
                Box(
                    modifier = Modifier
                        .width(180.dp)
                        .height(12.dp)
                        .background(shimmer_brush(), SquircleShape(6.dp)),
                )
            } else {
                Text(
                    text = display_name.ifBlank { username.ifBlank { stringResource(R.string.settings_profile) } },
                    color = colors.text_primary,
                    fontSize = 18.sp,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                if (username.isNotBlank()) {
                    Spacer(Modifier.size(3.dp))
                    Text(
                        text = "@" + username,
                        color = colors.text_secondary,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Medium,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                if (email.isNotBlank()) {
                    Spacer(Modifier.size(2.dp))
                    Text(
                        text = email,
                        color = colors.text_tertiary,
                        fontSize = 12.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
        }
        Spacer(Modifier.width(AsterSpacing.sm))
        Icon(
            imageVector = TablerIcons.ChevronRight,
            contentDescription = null,
            tint = colors.text_tertiary,
            modifier = Modifier.size(18.dp).mirror_in_rtl(),
        )
    }
}

@Composable
private fun quick_action_grid(on_open: (String) -> Unit) {
    val colors = AsterMaterial.colors
    val shape = SquircleShape(18.dp)
    Row(
        modifier = Modifier
            .padding(horizontal = AsterSpacing.lg)
            .fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(AsterSpacing.sm),
    ) {
        settings_quick_actions.forEach { action ->
            Column(
                modifier = Modifier
                    .weight(1f)
                    .clip(shape)
                    .background(colors.bg_card)
                    .border(1.dp, colors.border_secondary, shape)
                    .clickable { on_open(action.id) }
                    .padding(vertical = AsterSpacing.md, horizontal = AsterSpacing.xs),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Box(
                    modifier = Modifier
                        .size(36.dp)
                        .clip(SquircleShape(12.dp))
                        .background(colors.accent_blue.copy(alpha = 0.12f)),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        imageVector = action.icon,
                        contentDescription = null,
                        tint = colors.accent_blue,
                        modifier = Modifier.size(19.dp),
                    )
                }
                Spacer(Modifier.size(AsterSpacing.sm))
                Text(
                    text = stringResource(action.label_res),
                    color = colors.text_secondary,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Medium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    textAlign = TextAlign.Center,
                )
            }
        }
    }
}

@Composable
private fun feature_tile_row(
    subscription: org.astermail.android.api.settings.SubscriptionInfo?,
    plan_loading: Boolean,
    on_open_billing: () -> Unit,
    on_open_referral: () -> Unit,
) {
    val free_label = stringResource(R.string.plan_free)
    val plan_name = subscription?.effective_plan_name
    val is_free = subscription != null && (
        subscription.effective_price_cents == 0 ||
            (!plan_name.isNullOrBlank() && plan_name.trim().equals(free_label, ignoreCase = true))
        )
    Row(
        modifier = Modifier
            .padding(horizontal = AsterSpacing.lg)
            .fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(AsterSpacing.sm),
    ) {
        feature_tile(
            icon = TablerIcons.Crown,
            title = when {
                plan_loading -> ""
                is_free -> stringResource(R.string.settings_upgrade_cta)
                else -> plan_name?.takeIf { it.isNotBlank() } ?: free_label
            },
            subtitle = stringResource(R.string.settings_your_plan),
            loading = plan_loading,
            highlighted = is_free,
            on_click = on_open_billing,
            modifier = Modifier.weight(1f),
        )
        feature_tile(
            icon = TablerIcons.Gift,
            title = stringResource(R.string.refer_a_friend),
            subtitle = stringResource(R.string.settings_invite_earn),
            loading = false,
            highlighted = false,
            on_click = on_open_referral,
            modifier = Modifier.weight(1f),
        )
    }
}

@Composable
private fun feature_tile(
    icon: ImageVector,
    title: String,
    subtitle: String,
    loading: Boolean,
    highlighted: Boolean,
    on_click: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = AsterMaterial.colors
    val shape = SquircleShape(20.dp)
    val container = if (highlighted) colors.accent_blue else colors.bg_card
    val title_color = if (highlighted) Color.White else colors.text_primary
    val subtitle_color = if (highlighted) Color.White.copy(alpha = 0.78f) else colors.text_tertiary
    val icon_bg = if (highlighted) Color.White.copy(alpha = 0.2f) else colors.accent_blue.copy(alpha = 0.12f)
    val icon_tint = if (highlighted) Color.White else colors.accent_blue
    Column(
        modifier = modifier
            .clip(shape)
            .background(container)
            .border(
                1.dp,
                if (highlighted) Color.Transparent else colors.border_secondary,
                shape,
            )
            .clickable(onClick = on_click)
            .padding(AsterSpacing.lg),
    ) {
        Box(
            modifier = Modifier
                .size(36.dp)
                .clip(SquircleShape(12.dp))
                .background(icon_bg),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = icon_tint,
                modifier = Modifier.size(19.dp),
            )
        }
        Spacer(Modifier.size(AsterSpacing.md))
        if (loading) {
            Box(
                modifier = Modifier
                    .width(72.dp)
                    .height(15.dp)
                    .background(shimmer_brush(), SquircleShape(6.dp)),
            )
            Spacer(Modifier.size(5.dp))
        } else {
            Text(
                text = title,
                color = title_color,
                fontSize = 15.sp,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Spacer(Modifier.size(2.dp))
        }
        Text(
            text = subtitle,
            color = subtitle_color,
            fontSize = 12.sp,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

@Composable
private fun section_header(title: String) {
    val colors = AsterMaterial.colors
    Text(
        text = title,
        style = MaterialTheme.typography.labelLarge,
        color = colors.text_tertiary,
        fontSize = 13.sp,
        fontWeight = FontWeight.SemiBold,
        modifier = Modifier.padding(
            start = AsterSpacing.xl,
            end = AsterSpacing.lg,
            top = AsterSpacing.xxl,
            bottom = AsterSpacing.sm,
        ),
    )
}

@Composable
private fun settings_footer() {
    val colors = AsterMaterial.colors
    val context = LocalContext.current
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = AsterSpacing.xxxl, bottom = AsterSpacing.md),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Image(
            painter = painterResource(R.drawable.aster_wordmark),
            contentDescription = null,
            colorFilter = ColorFilter.tint(colors.text_tertiary),
            modifier = Modifier.height(15.dp).alpha(0.65f),
        )
        Spacer(Modifier.size(AsterSpacing.md))
        Text(
            text = stringResource(
                R.string.settings_version_build,
                BuildConfig.VERSION_NAME,
                BuildConfig.VERSION_CODE.toString(),
            ),
            color = colors.text_muted,
            fontSize = 12.sp,
        )
        Spacer(Modifier.size(AsterSpacing.sm))
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(AsterSpacing.sm),
        ) {
            Text(
                text = stringResource(R.string.terms_of_service),
                color = colors.text_tertiary,
                fontSize = 12.sp,
                fontWeight = FontWeight.Medium,
                modifier = Modifier
                    .clip(SquircleShape(6.dp))
                    .clickable { open_external_url(context, terms_url) }
                    .padding(horizontal = AsterSpacing.xs, vertical = 2.dp),
            )
            Box(
                modifier = Modifier
                    .size(3.dp)
                    .clip(SquircleShape(999.dp))
                    .background(colors.text_muted),
            )
            Text(
                text = stringResource(R.string.privacy_policy),
                color = colors.text_tertiary,
                fontSize = 12.sp,
                fontWeight = FontWeight.Medium,
                modifier = Modifier
                    .clip(SquircleShape(6.dp))
                    .clickable { open_external_url(context, privacy_url) }
                    .padding(horizontal = AsterSpacing.xs, vertical = 2.dp),
            )
        }
    }
}

@Composable
internal fun settings_row(
    row: settings_row_item,
    value: String? = null,
    on_click: () -> Unit,
) {
    val colors = AsterMaterial.colors
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = on_click)
            .heightIn(min = 54.dp)
            .padding(horizontal = AsterSpacing.lg, vertical = AsterSpacing.sm),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier = Modifier
                .size(30.dp)
                .clip(SquircleShape(10.dp))
                .background(colors.bg_secondary),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                imageVector = row.icon,
                contentDescription = null,
                tint = colors.text_secondary,
                modifier = Modifier.size(17.dp),
            )
        }
        Spacer(Modifier.width(AsterSpacing.md))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = stringResource(row.title_res),
                color = colors.text_primary,
                fontSize = 15.sp,
                fontWeight = FontWeight.Medium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            if (row.subtitle_res != null) {
                Text(
                    text = stringResource(row.subtitle_res),
                    color = colors.text_tertiary,
                    fontSize = 12.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
        if (!value.isNullOrBlank()) {
            Spacer(Modifier.width(AsterSpacing.sm))
            Text(
                text = value,
                color = colors.text_tertiary,
                fontSize = 13.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Spacer(Modifier.width(AsterSpacing.xs))
        }
        Icon(
            imageVector = TablerIcons.ChevronRight,
            contentDescription = null,
            tint = colors.text_tertiary,
            modifier = Modifier.size(18.dp).mirror_in_rtl(),
        )
    }
}
