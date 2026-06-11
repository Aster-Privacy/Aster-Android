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

import androidx.annotation.StringRes
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import org.astermail.android.R
import org.astermail.android.design.AsterMaterial
import org.astermail.android.design.AsterRadius
import org.astermail.android.design.AsterSpacing
import org.astermail.android.design.components.AsterDivider

private sealed interface cell_value {
    object yes : cell_value
    object no : cell_value
    data class text_res(@StringRes val res: Int) : cell_value
    data class text_res_int(@StringRes val res: Int, val arg: Int) : cell_value
    data class plain(val literal: String) : cell_value
}

private data class feature_row(
    @StringRes val name_res: Int,
    val free: cell_value,
    val star: cell_value,
    val nova: cell_value,
    val supernova: cell_value,
    val is_category: Boolean = false,
)

private val empty_cell = cell_value.plain("")

private fun row(
    @StringRes name_res: Int,
    free: cell_value,
    star: cell_value,
    nova: cell_value,
    supernova: cell_value,
): feature_row = feature_row(name_res, free, star, nova, supernova)

private fun category(@StringRes name_res: Int): feature_row =
    feature_row(name_res, empty_cell, empty_cell, empty_cell, empty_cell, is_category = true)

private fun gb(value: Int): cell_value = cell_value.text_res_int(R.string.settings_val_gb, value)
private fun tb(value: Int): cell_value = cell_value.text_res_int(R.string.settings_val_tb, value)
private fun mb(value: Int): cell_value = cell_value.text_res_int(R.string.settings_val_mb, value)
private fun emails(value: Int): cell_value = cell_value.text_res_int(R.string.settings_val_emails_per_day, value)
private fun days(value: Int): cell_value = cell_value.text_res_int(R.string.settings_val_days, value)
private fun hours(value: Int): cell_value = cell_value.text_res_int(R.string.settings_val_hours, value)
private fun count(value: Int): cell_value = cell_value.text_res_int(R.string.settings_val_count, value)
private val unlimited: cell_value = cell_value.text_res(R.string.settings_val_unlimited)
private val dash: cell_value = cell_value.text_res(R.string.settings_val_dash)
private val one_year: cell_value = cell_value.text_res(R.string.settings_val_one_year)
private val yes_cell: cell_value = cell_value.yes
private val no_cell: cell_value = cell_value.no

private val plan_name_res = listOf(
    R.string.plan_name_free,
    R.string.plan_name_star,
    R.string.plan_name_nova,
    R.string.plan_name_supernova,
)

private fun build_features(): List<feature_row> = listOf(
    category(R.string.settings_cat_storage_limits),
    row(R.string.settings_feat_secure_storage, gb(10), gb(50), gb(500), tb(5)),
    row(R.string.settings_feat_max_attachment, mb(25), mb(50), mb(100), mb(250)),
    row(R.string.settings_feat_daily_send, emails(200), unlimited, unlimited, unlimited),
    row(R.string.settings_feat_email_retention, unlimited, unlimited, unlimited, unlimited),

    category(R.string.settings_cat_email_features),
    row(R.string.settings_feat_e2ee, yes_cell, yes_cell, yes_cell, yes_cell),
    row(R.string.settings_feat_zero_knowledge, yes_cell, yes_cell, yes_cell, yes_cell),
    row(R.string.settings_feat_aliases, count(5), count(15), unlimited, unlimited),
    row(R.string.settings_feat_custom_domains, count(1), count(5), count(30), unlimited),
    row(R.string.settings_feat_scheduled_send, yes_cell, yes_cell, yes_cell, yes_cell),
    row(R.string.settings_feat_undo_send, yes_cell, yes_cell, yes_cell, yes_cell),
    row(R.string.settings_feat_read_receipts, no_cell, no_cell, no_cell, yes_cell),
    row(R.string.settings_feat_email_templates, count(3), count(10), unlimited, unlimited),
    row(R.string.settings_feat_auto_responder, no_cell, yes_cell, yes_cell, yes_cell),
    row(R.string.settings_feat_alias_avatars, no_cell, yes_cell, yes_cell, yes_cell),

    category(R.string.settings_cat_organization),
    row(R.string.settings_feat_folders, count(10), unlimited, unlimited, unlimited),
    row(R.string.settings_feat_labels, count(15), unlimited, unlimited, unlimited),
    row(R.string.settings_feat_smart_folders, no_cell, no_cell, yes_cell, yes_cell),
    row(R.string.settings_feat_advanced_search, yes_cell, yes_cell, yes_cell, yes_cell),
    row(R.string.settings_feat_search_history, days(30), one_year, unlimited, unlimited),
    row(R.string.settings_feat_contacts, count(150), unlimited, unlimited, unlimited),
    row(R.string.settings_feat_contact_groups, yes_cell, yes_cell, yes_cell, yes_cell),

    category(R.string.settings_cat_security),
    row(R.string.settings_feat_two_factor, yes_cell, yes_cell, yes_cell, yes_cell),
    row(R.string.settings_feat_recovery_codes, yes_cell, yes_cell, yes_cell, yes_cell),
    row(R.string.settings_feat_password_folders, no_cell, no_cell, yes_cell, yes_cell),
    row(R.string.settings_feat_session_mgmt, yes_cell, yes_cell, yes_cell, yes_cell),
    row(R.string.settings_feat_login_notifs, yes_cell, yes_cell, yes_cell, yes_cell),
    row(R.string.settings_feat_encrypted_exports, no_cell, no_cell, yes_cell, yes_cell),
    row(R.string.settings_feat_hardware_keys, no_cell, no_cell, no_cell, yes_cell),

    category(R.string.settings_cat_privacy),
    row(R.string.settings_feat_no_ads, yes_cell, yes_cell, yes_cell, yes_cell),
    row(R.string.settings_feat_no_tracking, yes_cell, yes_cell, yes_cell, yes_cell),
    row(R.string.settings_feat_anonymous_signup, yes_cell, yes_cell, yes_cell, yes_cell),
    row(R.string.settings_feat_tor_support, yes_cell, yes_cell, yes_cell, yes_cell),
    row(R.string.settings_feat_link_tracking, yes_cell, yes_cell, yes_cell, yes_cell),
    row(R.string.settings_feat_tracker_protection, yes_cell, yes_cell, yes_cell, yes_cell),
    row(R.string.settings_feat_remote_image_blocking, yes_cell, yes_cell, yes_cell, yes_cell),

    category(R.string.settings_cat_import_export),
    row(R.string.settings_feat_import_gmail, yes_cell, yes_cell, yes_cell, yes_cell),
    row(R.string.settings_feat_import_outlook, yes_cell, yes_cell, yes_cell, yes_cell),
    row(R.string.settings_feat_mbox_import, yes_cell, yes_cell, yes_cell, yes_cell),
    row(R.string.settings_feat_export_emails, yes_cell, yes_cell, yes_cell, yes_cell),
    row(R.string.settings_feat_export_contacts, yes_cell, yes_cell, yes_cell, yes_cell),

    category(R.string.settings_cat_support),
    row(R.string.settings_feat_help_center, yes_cell, yes_cell, yes_cell, yes_cell),
    row(R.string.settings_feat_community_forum, yes_cell, yes_cell, yes_cell, yes_cell),
    row(R.string.settings_feat_email_support, no_cell, yes_cell, yes_cell, yes_cell),
    row(R.string.settings_feat_priority_support, no_cell, yes_cell, yes_cell, yes_cell),
    row(R.string.settings_feat_response_time, dash, hours(48), hours(24), hours(24)),

    category(R.string.settings_cat_apps_integrations),
    row(R.string.settings_feat_web_app, yes_cell, yes_cell, yes_cell, yes_cell),
    row(R.string.settings_feat_ios_app, yes_cell, yes_cell, yes_cell, yes_cell),
    row(R.string.settings_feat_android_app, yes_cell, yes_cell, yes_cell, yes_cell),
    row(R.string.settings_feat_desktop_app, yes_cell, yes_cell, yes_cell, yes_cell),
    row(R.string.settings_feat_browser_ext, yes_cell, yes_cell, yes_cell, yes_cell),
    row(R.string.settings_feat_imap_smtp, yes_cell, yes_cell, yes_cell, yes_cell),
)

@Composable
fun FeaturesScreen(on_back: () -> Unit) {
    val colors = AsterMaterial.colors
    val features = build_features()
    val h_scroll = rememberScrollState()

    detail_scaffold(title = stringResource(R.string.settings_compare_plans), on_back = on_back) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(h_scroll),
        ) {
            Column(modifier = Modifier.width(IntrinsicSize.Max)) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(colors.bg_secondary, RoundedCornerShape(topStart = AsterRadius.lg, topEnd = AsterRadius.lg))
                        .padding(vertical = AsterSpacing.md),
                ) {
                    Box(
                        modifier = Modifier.width(150.dp).padding(horizontal = AsterSpacing.md),
                        contentAlignment = Alignment.CenterStart,
                    ) {
                        Text(
                            text = stringResource(R.string.feature),
                            color = colors.text_secondary,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.SemiBold,
                        )
                    }
                    plan_name_res.forEach { name_res ->
                        Box(
                            modifier = Modifier.width(80.dp),
                            contentAlignment = Alignment.Center,
                        ) {
                            Text(
                                text = stringResource(name_res),
                                color = colors.text_primary,
                                fontSize = 13.sp,
                                fontWeight = FontWeight.Bold,
                                textAlign = TextAlign.Center,
                            )
                        }
                    }
                }

                features.forEach { row ->
                    if (row.is_category) {
                        v_gap(AsterSpacing.md)
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .background(colors.accent_blue.copy(alpha = 0.08f))
                                .padding(horizontal = AsterSpacing.md, vertical = AsterSpacing.sm),
                        ) {
                            Text(
                                text = stringResource(row.name_res),
                                color = colors.accent_blue,
                                fontSize = 13.sp,
                                fontWeight = FontWeight.Bold,
                            )
                        }
                    } else {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 6.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Box(
                                modifier = Modifier.width(150.dp).padding(horizontal = AsterSpacing.md),
                                contentAlignment = Alignment.CenterStart,
                            ) {
                                Text(
                                    text = stringResource(row.name_res),
                                    color = colors.text_primary,
                                    fontSize = 13.sp,
                                )
                            }
                            listOf(row.free, row.star, row.nova, row.supernova).forEach { value ->
                                Box(
                                    modifier = Modifier.width(80.dp),
                                    contentAlignment = Alignment.Center,
                                ) {
                                    feature_cell(value)
                                }
                            }
                        }
                        AsterDivider()
                    }
                }

                v_gap(AsterSpacing.xxl)
            }
        }
    }
}

@Composable
private fun feature_cell(value: cell_value) {
    val colors = AsterMaterial.colors
    when (value) {
        is cell_value.yes -> Icon(
            imageVector = Icons.Default.Check,
            contentDescription = null,
            tint = colors.success,
            modifier = Modifier.size(18.dp),
        )
        is cell_value.no -> Icon(
            imageVector = Icons.Default.Close,
            contentDescription = null,
            tint = colors.text_tertiary.copy(alpha = 0.4f),
            modifier = Modifier.size(16.dp),
        )
        is cell_value.text_res -> Text(
            text = stringResource(value.res),
            color = colors.text_secondary,
            fontSize = 12.sp,
            textAlign = TextAlign.Center,
        )
        is cell_value.text_res_int -> Text(
            text = stringResource(value.res, value.arg),
            color = colors.text_secondary,
            fontSize = 12.sp,
            textAlign = TextAlign.Center,
        )
        is cell_value.plain -> if (value.literal.isNotEmpty()) {
            Text(
                text = value.literal,
                color = colors.text_secondary,
                fontSize = 12.sp,
                textAlign = TextAlign.Center,
            )
        }
    }
}
