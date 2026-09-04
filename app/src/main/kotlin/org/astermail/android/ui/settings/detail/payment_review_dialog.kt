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

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import compose.icons.TablerIcons
import compose.icons.tablericons.Check
import compose.icons.tablericons.CreditCard
import compose.icons.tablericons.CurrencyBitcoin
import org.astermail.android.R
import org.astermail.android.design.AsterMaterial
import org.astermail.android.design.AsterSpacing
import org.astermail.android.design.SquircleShape
import org.astermail.android.design.components.AsterDialog
import org.astermail.android.design.components.AsterDialogOutlineButton
import org.astermail.android.design.components.AsterDialogPrimaryButton

internal const val payment_method_card = "card"
internal const val payment_method_crypto = "crypto"

private const val review_feature_preview = 5

@Composable
private fun review_feature_row(text: String) {
    val colors = AsterMaterial.colors
    Row(
        modifier = Modifier.padding(vertical = 3.dp),
        verticalAlignment = Alignment.Top,
    ) {
        Icon(
            imageVector = TablerIcons.Check,
            contentDescription = null,
            tint = colors.accent_blue,
            modifier = Modifier
                .padding(top = 1.dp)
                .size(15.dp),
        )
        Spacer(Modifier.width(AsterSpacing.sm))
        Text(text = text, color = colors.text_secondary, fontSize = 12.sp, lineHeight = 17.sp)
    }
}

@Composable
private fun summary_row(
    label: String,
    value: String,
    value_color: Color,
    emphasized: Boolean = false,
) {
    val colors = AsterMaterial.colors
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = label,
            color = if (emphasized) colors.text_primary else colors.text_tertiary,
            fontSize = if (emphasized) 14.sp else 12.sp,
            fontWeight = if (emphasized) FontWeight.SemiBold else FontWeight.Normal,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f),
        )
        Spacer(Modifier.width(AsterSpacing.sm))
        Text(
            text = value,
            color = value_color,
            fontSize = if (emphasized) 20.sp else 12.sp,
            fontWeight = if (emphasized) FontWeight.Bold else FontWeight.Medium,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

@Composable
internal fun plan_features_dialog(
    plan_name: String,
    features: List<Int>,
    on_dismiss: () -> Unit,
) {
    AsterDialog(
        on_dismiss = on_dismiss,
        title = plan_name,
        body = {
            Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                features.forEach { feature_res ->
                    review_feature_row(stringResource(feature_res))
                }
            }
        },
        footer = {
            AsterDialogPrimaryButton(
                label = stringResource(R.string.close),
                onClick = on_dismiss,
                modifier = Modifier.weight(1f),
            )
        },
    )
}

@Composable
private fun checkout_abandon_dialog(
    on_keep: () -> Unit,
    on_confirm: () -> Unit,
) {
    AsterDialog(
        on_dismiss = on_keep,
        title = stringResource(R.string.checkout_abandon_title),
        message = stringResource(R.string.checkout_abandon_message),
        footer = {
            AsterDialogOutlineButton(
                label = stringResource(R.string.checkout_abandon_confirm),
                onClick = on_confirm,
                modifier = Modifier.weight(1f),
            )
            AsterDialogPrimaryButton(
                label = stringResource(R.string.checkout_abandon_keep),
                onClick = on_keep,
                modifier = Modifier.weight(1f),
            )
        },
    )
}

@Composable
private fun review_method_tile(
    active: Boolean,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    label: String,
    note: String,
    on_click: () -> Unit,
    marks: @Composable () -> Unit,
) {
    val colors = AsterMaterial.colors
    review_tile(active = active, enabled = true, on_click = on_click) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(AsterSpacing.md),
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = colors.text_primary,
                modifier = Modifier.size(20.dp),
            )
            Text(
                text = label,
                color = colors.text_primary,
                fontSize = 14.sp,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier.weight(1f),
            )
            Box(
                modifier = Modifier
                    .size(18.dp)
                    .clip(androidx.compose.foundation.shape.CircleShape)
                    .background(if (active) colors.accent_blue else Color.Transparent),
                contentAlignment = Alignment.Center,
            ) {
                if (active) {
                    Icon(
                        imageVector = TablerIcons.Check,
                        contentDescription = null,
                        tint = Color.White,
                        modifier = Modifier.size(12.dp),
                    )
                }
            }
        }
        Spacer(Modifier.height(AsterSpacing.sm))
        marks()
        Spacer(Modifier.height(AsterSpacing.sm))
        Text(text = note, color = colors.text_tertiary, fontSize = 11.sp, lineHeight = 15.sp)
    }
}

@Composable
internal fun payment_review_dialog(
    title: String,
    plan_name: String,
    interval_label: String?,
    amount_text: String,
    subtotal_text: String?,
    save_text: String?,
    is_best_value: Boolean,
    features: List<Int>,
    is_busy: Boolean,
    initial_method: String = payment_method_card,
    on_dismiss: () -> Unit,
    on_confirm: (String) -> Unit,
) {
    val colors = AsterMaterial.colors
    var method by remember(initial_method) { mutableStateOf(initial_method) }
    var touched by remember { mutableStateOf(false) }
    var show_features by remember { mutableStateOf(false) }
    var show_abandon by remember { mutableStateOf(false) }

    val request_close = {
        if (touched) show_abandon = true else on_dismiss()
    }

    AsterDialog(
        on_dismiss = request_close,
        title = title,
        body = {
            Column(verticalArrangement = Arrangement.spacedBy(AsterSpacing.md)) {
                galaxy_surface(modifier = Modifier.fillMaxWidth()) {
                    Column(modifier = Modifier.padding(AsterSpacing.md)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Text(
                                text = stringResource(R.string.domain_purchase_order_summary),
                                color = colors.text_tertiary,
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Medium,
                            )
                            Text(
                                text = stringResource(R.string.app_name),
                                color = colors.text_primary,
                                fontSize = 13.sp,
                                fontWeight = FontWeight.Bold,
                            )
                        }
                        Spacer(Modifier.height(AsterSpacing.md))
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                text = plan_name,
                                color = colors.text_primary,
                                fontSize = 18.sp,
                                fontWeight = FontWeight.Bold,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                modifier = Modifier.weight(1f, fill = false),
                            )
                            if (is_best_value) {
                                Spacer(Modifier.width(AsterSpacing.sm))
                                galaxy_badge(text = stringResource(R.string.checkout_best_value))
                            }
                        }
                        if (!interval_label.isNullOrBlank()) {
                            Spacer(Modifier.height(2.dp))
                            Text(
                                text = interval_label,
                                color = colors.text_tertiary,
                                fontSize = 12.sp,
                            )
                        }
                        Spacer(Modifier.height(AsterSpacing.md))
                        if (subtotal_text != null) {
                            summary_row(
                                label = stringResource(R.string.checkout_subtotal),
                                value = subtotal_text,
                                value_color = colors.text_secondary,
                            )
                            Spacer(Modifier.height(AsterSpacing.xs))
                        }
                        if (save_text != null) {
                            summary_row(
                                label = stringResource(R.string.checkout_term_save, save_text),
                                value = "-$save_text",
                                value_color = colors.success,
                            )
                            Spacer(Modifier.height(AsterSpacing.xs))
                        }
                        Spacer(Modifier.height(AsterSpacing.xs))
                        summary_row(
                            label = stringResource(R.string.checkout_amount_due),
                            value = amount_text,
                            value_color = colors.text_primary,
                            emphasized = true,
                        )
                        Spacer(Modifier.height(AsterSpacing.sm))
                        Text(
                            text = stringResource(R.string.checkout_money_back),
                            color = colors.text_muted,
                            fontSize = 11.sp,
                        )
                    }
                }

                Text(
                    text = stringResource(R.string.checkout_payment_details),
                    color = colors.text_primary,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.SemiBold,
                )
                review_method_tile(
                    active = method == payment_method_card,
                    icon = TablerIcons.CreditCard,
                    label = stringResource(R.string.checkout_method_card),
                    note = stringResource(R.string.checkout_method_card_note),
                    on_click = {
                        method = payment_method_card
                        touched = true
                    },
                    marks = { card_brand_marks() },
                )
                review_method_tile(
                    active = method == payment_method_crypto,
                    icon = TablerIcons.CurrencyBitcoin,
                    label = stringResource(R.string.checkout_method_crypto),
                    note = stringResource(R.string.checkout_crypto_note),
                    on_click = {
                        method = payment_method_crypto
                        touched = true
                    },
                    marks = { coin_stack() },
                )
                security_marks(label = stringResource(R.string.checkout_stripe_secure))

                if (features.isNotEmpty()) {
                    Column {
                        Text(
                            text = stringResource(R.string.checkout_what_you_get),
                            color = colors.text_primary,
                            fontSize = 13.sp,
                            fontWeight = FontWeight.SemiBold,
                        )
                        Spacer(Modifier.height(AsterSpacing.xs))
                        features.take(review_feature_preview).forEach { feature_res ->
                            review_feature_row(stringResource(feature_res))
                        }
                        Spacer(Modifier.height(AsterSpacing.xs))
                        Text(
                            text = stringResource(R.string.plan_view_full_features),
                            color = colors.accent_blue,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.SemiBold,
                            modifier = Modifier
                                .clip(SquircleShape(8.dp))
                                .clickable(role = Role.Button) { show_features = true }
                                .padding(vertical = 6.dp),
                        )
                    }
                }

                Text(
                    text = stringResource(R.string.autorenew_notice_short),
                    color = colors.text_muted,
                    fontSize = 11.sp,
                    lineHeight = 15.sp,
                )
            }
        },
        footer = {
            AsterDialogOutlineButton(
                label = stringResource(R.string.cancel),
                onClick = request_close,
                modifier = Modifier.weight(0.7f),
            )
            AsterDialogPrimaryButton(
                label = stringResource(R.string.checkout_continue),
                onClick = { on_confirm(method) },
                enabled = !is_busy,
                is_loading = is_busy,
                modifier = Modifier.weight(1.3f),
            )
        },
    )

    if (show_features) {
        plan_features_dialog(
            plan_name = plan_name,
            features = features,
            on_dismiss = { show_features = false },
        )
    }

    if (show_abandon) {
        checkout_abandon_dialog(
            on_keep = { show_abandon = false },
            on_confirm = {
                show_abandon = false
                on_dismiss()
            },
        )
    }
}
