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

package org.astermail.android.ui.upgrade

import compose.icons.TablerIcons
import compose.icons.tablericons.Check
import compose.icons.tablericons.Minus
import compose.icons.tablericons.X

import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import org.astermail.android.R
import org.astermail.android.billing.BillingViewModel
import org.astermail.android.billing.format_money
import org.astermail.android.design.AsterMaterial
import org.astermail.android.design.SquircleShape
import org.astermail.android.ui.security.lock_dialog_window_effect

private const val SPECIAL_OFFER_PLAN_CODE = "nova"
private const val SPECIAL_OFFER_INTERVAL = "month"
private const val SPECIAL_OFFER_LIST_CENTS = 899L
private const val HERO_ASPECT_RATIO = 2f

private val CARD_SHAPE = SquircleShape(24.dp)
private val CARD_MAX_WIDTH = 420.dp
private val CARD_PADDING = 20.dp
private val COMPARISON_COLUMN_WIDTH = 84.dp
private val CTA_HEIGHT = 52.dp
private val CTA_SHAPE = SquircleShape(999.dp)
private val SCRIM_COLOR = Color(0xE6000000)
private val CLOSE_BUTTON_FILL = Color(0x66000000)

private data class SpecialOfferComparisonRow(
    val label: String,
    val free_value: String?,
    val paid_value: String?,
)

@Composable
fun SpecialOfferHost() {
    val offer_vm: SpecialOfferViewModel = hiltViewModel()
    val offer_state by offer_vm.state.collectAsStateWithLifecycle()
    val billing_vm: BillingViewModel = org.astermail.android.billing.billing_view_model()
    val billing_state by billing_vm.state.collectAsStateWithLifecycle()
    val context = LocalContext.current

    LaunchedEffect(Unit) { offer_vm.load() }

    LaunchedEffect(offer_state.auto_show) {
        if (offer_state.auto_show) offer_vm.claim_and_open()
    }

    LaunchedEffect(billing_state.checkout_url, offer_state.is_open) {
        if (!offer_state.is_open) return@LaunchedEffect
        val url = billing_state.checkout_url ?: return@LaunchedEffect
        org.astermail.android.billing.open_billing_tab(context, url)
        billing_vm.consume_checkout_url()
        offer_vm.close()
    }

    if (!offer_state.is_open) return

    val percent_off = if (offer_state.percent_off > 0) offer_state.percent_off else 50
    val months = if (offer_state.duration_months > 0) offer_state.duration_months else 12
    val currency = billing_state.subscription?.currency?.takeIf { it.isNotBlank() } ?: "usd"
    val offer_cents = SPECIAL_OFFER_LIST_CENTS * (100 - percent_off) / 100
    val offer_label = format_money(offer_cents, currency)
    val list_label = format_money(SPECIAL_OFFER_LIST_CENTS, currency)

    val included = stringResource(R.string.special_offer_compare_included)
    val rows = listOf(
        SpecialOfferComparisonRow(stringResource(R.string.special_offer_compare_storage), "10 GB", "500 GB"),
        SpecialOfferComparisonRow(
            stringResource(R.string.special_offer_compare_aliases),
            "5",
            stringResource(R.string.usage_unlimited),
        ),
        SpecialOfferComparisonRow(stringResource(R.string.special_offer_compare_domains), "1", "30"),
        SpecialOfferComparisonRow(stringResource(R.string.special_offer_compare_attachments), "25 MB", "100 MB"),
        SpecialOfferComparisonRow(stringResource(R.string.special_offer_compare_vanguard), null, included),
    )

    Dialog(
        onDismissRequest = {},
        properties = DialogProperties(
            dismissOnBackPress = false,
            dismissOnClickOutside = false,
            usePlatformDefaultWidth = false,
            decorFitsSystemWindows = false,
        ),
    ) {
        lock_dialog_window_effect()
        val colors = AsterMaterial.colors

        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(SCRIM_COLOR)
                .statusBarsPadding()
                .navigationBarsPadding()
                .padding(16.dp),
            contentAlignment = Alignment.Center,
        ) {
            Box(
                modifier = Modifier
                    .widthIn(max = CARD_MAX_WIDTH)
                    .fillMaxWidth()
                    .shadow(elevation = 28.dp, shape = CARD_SHAPE)
                    .clip(CARD_SHAPE)
                    .background(colors.bg_card)
                    .border(1.dp, colors.border_secondary, CARD_SHAPE),
            ) {
                Column(modifier = Modifier.verticalScroll(rememberScrollState())) {
                    SpecialOfferHero()

                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(start = CARD_PADDING, end = CARD_PADDING, bottom = CARD_PADDING),
                    ) {
                        Text(
                            text = stringResource(R.string.special_offer_title),
                            color = colors.text_primary,
                            fontSize = 22.sp,
                            lineHeight = 27.sp,
                            letterSpacing = (-0.3).sp,
                            fontWeight = FontWeight.SemiBold,
                        )

                        Spacer(Modifier.height(14.dp))

                        Row {
                            Text(
                                text = offer_label,
                                color = colors.text_primary,
                                fontSize = 34.sp,
                                lineHeight = 38.sp,
                                letterSpacing = (-0.8).sp,
                                fontWeight = FontWeight.SemiBold,
                                modifier = Modifier.alignByBaseline(),
                            )
                            Spacer(Modifier.width(8.dp))
                            Text(
                                text = stringResource(R.string.special_offer_price_period),
                                color = colors.text_secondary,
                                fontSize = 14.sp,
                                fontWeight = FontWeight.Medium,
                                modifier = Modifier.alignByBaseline(),
                            )
                            Spacer(Modifier.width(8.dp))
                            Text(
                                text = list_label,
                                color = colors.text_tertiary,
                                fontSize = 15.sp,
                                fontWeight = FontWeight.Medium,
                                textDecoration = TextDecoration.LineThrough,
                                modifier = Modifier.alignByBaseline(),
                            )
                        }

                        Spacer(Modifier.height(6.dp))

                        Text(
                            text = stringResource(R.string.special_offer_hero_duration, months),
                            color = colors.text_secondary,
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Medium,
                        )

                        Spacer(Modifier.height(18.dp))

                        SpecialOfferComparison(rows = rows, included = included)

                        Spacer(Modifier.height(20.dp))

                        SpecialOfferDepthButton(
                            label = stringResource(R.string.special_offer_cta_upgrade, offer_label),
                            is_loading = offer_state.is_accepting || billing_state.is_acting,
                            onClick = {
                                billing_vm.clear_messages()
                                offer_vm.accept_then {
                                    billing_vm.start_checkout(
                                        SPECIAL_OFFER_PLAN_CODE,
                                        SPECIAL_OFFER_INTERVAL,
                                        currency,
                                    )
                                }
                            },
                        )

                        Spacer(Modifier.height(12.dp))

                        Text(
                            text = stringResource(R.string.special_offer_reassurance),
                            color = colors.text_secondary,
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Medium,
                            textAlign = TextAlign.Center,
                            modifier = Modifier.fillMaxWidth(),
                        )

                        Spacer(Modifier.height(16.dp))

                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(1.dp)
                                .background(colors.border_secondary),
                        )

                        Spacer(Modifier.height(14.dp))

                        Text(
                            text = stringResource(
                                R.string.special_offer_fine_print,
                                offer_label,
                                months,
                                list_label,
                            ),
                            color = colors.text_tertiary,
                            fontSize = 12.sp,
                            lineHeight = 17.sp,
                            textAlign = TextAlign.Center,
                            modifier = Modifier.fillMaxWidth(),
                        )

                        Spacer(Modifier.height(6.dp))

                        Text(
                            text = stringResource(R.string.special_offer_dismiss),
                            color = colors.text_secondary,
                            fontSize = 12.sp,
                            textAlign = TextAlign.Center,
                            textDecoration = TextDecoration.Underline,
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { offer_vm.dismiss_forever() }
                                .padding(vertical = 8.dp),
                        )
                    }
                }

                Box(
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .padding(top = 14.dp, end = 14.dp)
                        .size(32.dp)
                        .clip(CircleShape)
                        .background(CLOSE_BUTTON_FILL)
                        .clickable { offer_vm.close() },
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        imageVector = TablerIcons.X,
                        contentDescription = stringResource(R.string.close),
                        tint = Color.White,
                        modifier = Modifier.size(17.dp),
                    )
                }
            }
        }
    }
}

@Composable
private fun SpecialOfferHero() {
    val colors = AsterMaterial.colors

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .aspectRatio(HERO_ASPECT_RATIO),
    ) {
        Image(
            painter = painterResource(R.drawable.special_offer_hero),
            contentDescription = null,
            contentScale = ContentScale.FillBounds,
            modifier = Modifier.fillMaxSize(),
        )
        Box(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .height(72.dp)
                .background(
                    Brush.verticalGradient(
                        0f to colors.bg_card.copy(alpha = 0f),
                        0.55f to colors.bg_card.copy(alpha = 0.72f),
                        1f to colors.bg_card,
                    ),
                ),
        )
    }
}

@Composable
private fun SpecialOfferComparison(rows: List<SpecialOfferComparisonRow>, included: String) {
    val colors = AsterMaterial.colors

    Column(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Spacer(Modifier.weight(1f))
            Text(
                text = stringResource(R.string.plan_name_free),
                color = colors.text_tertiary,
                fontSize = 13.sp,
                fontWeight = FontWeight.Medium,
                textAlign = TextAlign.Center,
                modifier = Modifier.width(COMPARISON_COLUMN_WIDTH),
            )
            Text(
                text = stringResource(R.string.plan_name_nova),
                color = colors.accent_blue,
                fontSize = 13.sp,
                fontWeight = FontWeight.SemiBold,
                textAlign = TextAlign.Center,
                modifier = Modifier.width(COMPARISON_COLUMN_WIDTH),
            )
        }

        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(1.dp)
                .background(colors.border_primary),
        )

        rows.forEachIndexed { index, row ->
            if (index > 0) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(1.dp)
                        .background(colors.border_secondary),
                )
            }
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = 46.dp)
                    .padding(vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = row.label,
                    color = colors.text_secondary,
                    fontSize = 14.sp,
                    lineHeight = 19.sp,
                    fontWeight = FontWeight.Medium,
                    modifier = Modifier.weight(1f),
                )
                SpecialOfferComparisonValue(value = row.free_value, included = included, is_paid = false)
                SpecialOfferComparisonValue(value = row.paid_value, included = included, is_paid = true)
            }
        }

        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(1.dp)
                .background(colors.border_secondary),
        )
    }
}

@Composable
private fun SpecialOfferComparisonValue(value: String?, included: String, is_paid: Boolean) {
    val colors = AsterMaterial.colors

    Box(
        modifier = Modifier.width(COMPARISON_COLUMN_WIDTH),
        contentAlignment = Alignment.Center,
    ) {
        when {
            value == null -> Icon(
                imageVector = TablerIcons.Minus,
                contentDescription = stringResource(R.string.special_offer_compare_not_included),
                tint = colors.text_muted,
                modifier = Modifier.size(16.dp),
            )
            value == included -> Box(
                modifier = Modifier
                    .size(20.dp)
                    .clip(CircleShape)
                    .background(colors.accent_blue),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    imageVector = TablerIcons.Check,
                    contentDescription = value,
                    tint = Color.White,
                    modifier = Modifier.size(13.dp),
                )
            }
            else -> Text(
                text = value,
                color = if (is_paid) colors.text_primary else colors.text_tertiary,
                fontSize = 14.sp,
                fontWeight = if (is_paid) FontWeight.SemiBold else FontWeight.Medium,
                textAlign = TextAlign.Center,
                maxLines = 1,
            )
        }
    }
}

@Composable
private fun SpecialOfferDepthButton(label: String, is_loading: Boolean, onClick: () -> Unit) {
    val accent = AsterMaterial.colors.accent_blue
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val scale by animateFloatAsState(
        targetValue = if (pressed && !is_loading) 0.97f else 1f,
        animationSpec = tween(durationMillis = if (pressed) 90 else 160),
        label = "offer_cta_scale",
    )
    val lip by animateDpAsState(
        targetValue = if (pressed && !is_loading) 1.dp else 3.dp,
        animationSpec = tween(durationMillis = if (pressed) 90 else 160),
        label = "offer_cta_lip",
    )
    val shade = if (pressed) 0.08f else 0f
    val fill = Brush.verticalGradient(
        listOf(
            lerp(lerp(accent, Color.White, 0.14f), Color.Black, shade),
            lerp(accent, Color.Black, 0.08f + shade),
        ),
    )
    val base = lerp(accent, Color.Black, 0.38f)
    val highlight = lerp(accent, Color.White, 0.35f)

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(CTA_HEIGHT)
            .graphicsLayer { scaleX = scale; scaleY = scale }
            .clip(CTA_SHAPE)
            .background(base)
            .clickable(
                enabled = !is_loading,
                interactionSource = interaction,
                indication = null,
                onClick = onClick,
            ),
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(top = 3.dp - lip, bottom = lip)
                .clip(CTA_SHAPE)
                .background(fill)
                .border(
                    1.dp,
                    Brush.verticalGradient(0f to highlight, 0.45f to accent, 1f to accent),
                    CTA_SHAPE,
                ),
            contentAlignment = Alignment.Center,
        ) {
            if (is_loading) {
                CircularProgressIndicator(
                    modifier = Modifier.size(20.dp),
                    color = Color.White,
                    strokeWidth = 2.dp,
                )
            } else {
                Text(
                    text = label,
                    color = Color.White,
                    fontSize = 16.sp,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    style = TextStyle(
                        shadow = Shadow(
                            color = base.copy(alpha = 0.6f),
                            offset = Offset(0f, 1.5f),
                            blurRadius = 1f,
                        ),
                    ),
                )
            }
        }
    }
}
