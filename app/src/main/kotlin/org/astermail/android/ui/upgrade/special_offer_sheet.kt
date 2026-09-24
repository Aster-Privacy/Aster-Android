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
import compose.icons.tablericons.AlertCircle
import compose.icons.tablericons.Check
import compose.icons.tablericons.Discount2
import compose.icons.tablericons.X

import android.content.Context
import android.content.ContextWrapper
import androidx.activity.ComponentActivity
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
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
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
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
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
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import org.astermail.android.R
import org.astermail.android.billing.BillingViewModel
import org.astermail.android.billing.api_plan_price_cents
import org.astermail.android.billing.billing_interval_per_label
import org.astermail.android.billing.format_money
import org.astermail.android.design.AsterMaterial
import org.astermail.android.design.SquircleShape
import org.astermail.android.design.acrylic
import org.astermail.android.ui.security.lock_dialog_window_effect
import org.astermail.android.ui.settings.detail.crypto_term_dialog
import org.astermail.android.ui.settings.detail.payment_method_crypto
import org.astermail.android.ui.settings.detail.payment_review_dialog
import org.astermail.android.ui.settings.detail.review_offer_price

private const val SPECIAL_OFFER_INTERVAL = "month"
private const val SPECIAL_OFFER_LIST_CENTS = 899L
private const val SPECIAL_OFFER_YEARLY_CENTS = 8699L
private const val HERO_ASPECT_RATIO = 2.4f
private const val HERO_ASPECT_RATIO_COMPACT = 3.2f
private val COMPACT_HEIGHT = 780.dp

private val CARD_SHAPE = SquircleShape(24.dp)
private val ERROR_SHAPE = SquircleShape(12.dp)
private val CARD_MAX_WIDTH = 420.dp
private val CARD_PADDING = 20.dp
private val CTA_HEIGHT = 52.dp
private val CTA_LIP = 2.dp
private val CTA_SHAPE = SquircleShape(999.dp)
private val SCRIM_COLOR = Color(0xE6000000)
private val CLOSE_BUTTON_FILL = Color(0xFF1C1C1E)
private val CLOSE_BUTTON_SIZE = 32.dp
private val CLOSE_TOUCH_TARGET = 48.dp
private val BENEFIT_CHECK_SIZE = 20.dp

internal fun special_offer_price_pair(
    list_cents: Long,
    percent_off: Int,
    currency: String,
    badge: String,
): review_offer_price = review_offer_price(
    original = format_money(list_cents, currency),
    discounted = format_money(special_offer_price_cents(list_cents, percent_off), currency),
    badge = badge,
)

internal fun special_offer_term_prices(
    offer: SpecialOfferState,
    plan_code: String?,
    monthly_cents: Long?,
    yearly_cents: Long?,
    badge: String,
): Map<Int, review_offer_price> =
    SPECIAL_OFFER_CRYPTO_TERMS
        .filter { offer.applies_to_crypto_term(plan_code, it) }
        .mapNotNull { term ->
            special_offer_crypto_term_cents(monthly_cents, yearly_cents, term)?.let { total ->
                term to special_offer_price_pair(total, offer.effective_percent_off, SPECIAL_OFFER_CURRENCY, badge)
            }
        }
        .toMap()

private fun Context.find_offer_activity(): ComponentActivity? {
    var current: Context? = this
    while (current is ContextWrapper) {
        if (current is ComponentActivity) return current
        current = current.baseContext
    }
    return null
}

@Composable
fun special_offer_view_model(): SpecialOfferViewModel {
    val context = LocalContext.current
    val activity = remember(context) { context.find_offer_activity() }
    return if (activity != null) hiltViewModel(activity) else hiltViewModel()
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun SpecialOfferHost() {
    val play_install = org.astermail.android.billing.remember_play_install()
    val offer_vm = special_offer_view_model()
    val offer_state by offer_vm.state.collectAsStateWithLifecycle()
    val billing_vm: BillingViewModel = org.astermail.android.billing.billing_view_model()
    val billing_state by billing_vm.state.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val lifecycle_owner = LocalLifecycleOwner.current

    LaunchedEffect(Unit) { offer_vm.retry_load() }

    DisposableEffect(lifecycle_owner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) offer_vm.retry_load()
        }
        lifecycle_owner.lifecycle.addObserver(observer)
        onDispose { lifecycle_owner.lifecycle.removeObserver(observer) }
    }

    LaunchedEffect(play_install) {
        if (play_install) billing_vm.load_play_offers()
    }

    val play_offer = if (play_install) {
        org.astermail.android.billing.play_special_offer_for(billing_state.play_offers, billing_state.play_special_offer)
    } else {
        null
    }
    val play_ready = billing_state.play_enabled && billing_state.play_special_offer_eligible && play_offer != null

    LaunchedEffect(offer_state.auto_show, play_install, play_ready) {
        if (offer_state.auto_show && (!play_install || play_ready)) offer_vm.claim_and_open()
    }

    var play_cta_pressed by remember { mutableStateOf(false) }
    LaunchedEffect(billing_state.play_purchase_request, offer_state.is_open) {
        if (!offer_state.is_open) play_cta_pressed = false
        if (play_install && offer_state.is_open && play_cta_pressed && billing_state.play_purchase_request != null) {
            play_cta_pressed = false
            offer_vm.close()
        }
    }

    var offer_checkout_pending by remember { mutableStateOf(false) }
    LaunchedEffect(offer_state.owns_checkout, billing_state.is_acting, billing_state.checkout_url) {
        if (offer_state.owns_checkout) {
            offer_checkout_pending = true
            return@LaunchedEffect
        }
        if (!offer_checkout_pending || billing_state.is_acting) return@LaunchedEffect
        offer_checkout_pending = false
        if (billing_state.checkout_url != null) billing_vm.discard_checkout_url()
    }

    LaunchedEffect(billing_state.checkout_url, offer_state.is_open, offer_state.owns_checkout) {
        if (!offer_state.is_open || !offer_state.owns_checkout) return@LaunchedEffect
        val url = billing_state.checkout_url ?: return@LaunchedEffect
        org.astermail.android.billing.open_billing_tab(context, url)
        billing_vm.consume_checkout_url()
        offer_vm.close()
    }

    if (!offer_state.is_open) return

    LaunchedEffect(Unit) {
        if (billing_state.available_plans.isEmpty()) billing_vm.load_plans()
    }

    val play_special = billing_state.play_special_offer.takeIf { play_install }
    val percent_off = play_special?.percent_off?.takeIf { it > 0 } ?: offer_state.effective_percent_off
    val months = play_special?.duration_months?.takeIf { it > 0 } ?: offer_state.effective_duration_months
    val plan_code = offer_state.plan_code
    val list_cents = api_plan_price_cents(billing_state.available_plans, plan_code, SPECIAL_OFFER_INTERVAL)
        ?.toLong()
        ?: SPECIAL_OFFER_LIST_CENTS
    val offer_cents = special_offer_price_cents(list_cents, percent_off)
    val offer_label = play_offer?.intro_formatted_price?.takeIf { it.isNotBlank() }
        ?: format_money(offer_cents, SPECIAL_OFFER_CURRENCY)
    val list_label = play_offer?.formatted_price?.takeIf { it.isNotBlank() }
        ?: format_money(list_cents, SPECIAL_OFFER_CURRENCY)
    val save_badge = stringResource(R.string.save_percent, percent_off)
    val yearly_cents = api_plan_price_cents(billing_state.available_plans, plan_code, "year")
        ?.toLong()
        ?: SPECIAL_OFFER_YEARLY_CENTS
    val term_prices = special_offer_term_prices(offer_state, plan_code, list_cents, yearly_cents, save_badge)
    val is_busy = offer_state.is_accepting || billing_state.is_acting
    val checkout_error = billing_state.error?.takeIf { (offer_state.owns_checkout || play_cta_pressed) && it.isNotBlank() }
    val error_text = when {
        offer_state.offer_expired -> stringResource(R.string.special_offer_unavailable)
        offer_state.accept_failed -> stringResource(R.string.could_not_start_checkout)
        else -> checkout_error
    }

    val benefits = listOf(
        stringResource(R.string.special_offer_benefit_aliases_title) to stringResource(R.string.special_offer_benefit_aliases_body),
        stringResource(R.string.special_offer_benefit_storage_title) to stringResource(R.string.special_offer_benefit_storage_body),
        stringResource(R.string.special_offer_benefit_domains_title) to stringResource(R.string.special_offer_benefit_domains_body),
        stringResource(R.string.special_offer_benefit_attachments_title) to stringResource(R.string.special_offer_benefit_attachments_body),
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

        BoxWithConstraints(
            modifier = Modifier
                .fillMaxSize()
                .background(SCRIM_COLOR)
                .statusBarsPadding()
                .navigationBarsPadding()
                .padding(16.dp),
            contentAlignment = Alignment.Center,
        ) {
            val height_budget = maxHeight / LocalDensity.current.fontScale.coerceAtLeast(1f)
            val compact = height_budget < COMPACT_HEIGHT
            val gap = if (compact) 10.dp else 14.dp
            Box(
                modifier = Modifier
                    .widthIn(max = CARD_MAX_WIDTH)
                    .fillMaxWidth()
                    .shadow(elevation = 28.dp, shape = CARD_SHAPE)
                    .clip(CARD_SHAPE)
                    .acrylic(colors, CARD_SHAPE, colors.bg_card)
                    .border(1.dp, colors.border_secondary, CARD_SHAPE),
            ) {
                Column(modifier = Modifier.verticalScroll(rememberScrollState())) {
                    SpecialOfferHero(aspect_ratio = if (compact) HERO_ASPECT_RATIO_COMPACT else HERO_ASPECT_RATIO)

                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(
                                start = CARD_PADDING,
                                top = CARD_PADDING,
                                end = CARD_PADDING,
                                bottom = 8.dp,
                            ),
                    ) {
                        SpecialOfferBadge()

                        Spacer(Modifier.height(10.dp))

                        Text(
                            text = stringResource(R.string.special_offer_title),
                            color = colors.text_primary,
                            fontSize = 22.sp,
                            lineHeight = 27.sp,
                            letterSpacing = (-0.3).sp,
                            fontWeight = FontWeight.SemiBold,
                        )

                        Spacer(Modifier.height(6.dp))

                        FlowRow(
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalArrangement = Arrangement.spacedBy(2.dp),
                        ) {
                            Text(
                                text = offer_label,
                                color = colors.text_primary,
                                fontSize = 38.sp,
                                lineHeight = 42.sp,
                                letterSpacing = (-1).sp,
                                fontWeight = FontWeight.Bold,
                                modifier = Modifier.alignByBaseline(),
                            )
                            Text(
                                text = stringResource(R.string.special_offer_price_period),
                                color = colors.text_secondary,
                                fontSize = 14.sp,
                                fontWeight = FontWeight.Medium,
                                modifier = Modifier.alignByBaseline(),
                            )
                            Text(
                                text = list_label,
                                color = colors.text_tertiary,
                                fontSize = 15.sp,
                                fontWeight = FontWeight.Medium,
                                textDecoration = TextDecoration.LineThrough,
                                modifier = Modifier.alignByBaseline(),
                            )
                        }

                        Spacer(Modifier.height(gap + 2.dp))

                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(1.dp)
                                .background(colors.border_secondary),
                        )

                        Spacer(Modifier.height(gap))

                        SpecialOfferBenefits(benefits = benefits, show_details = !compact)

                        Spacer(Modifier.height(gap + 4.dp))

                        if (error_text != null) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(ERROR_SHAPE)
                                    .background(colors.danger.copy(alpha = 0.08f))
                                    .padding(horizontal = 12.dp, vertical = 10.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Icon(
                                    imageVector = TablerIcons.AlertCircle,
                                    contentDescription = null,
                                    tint = colors.danger,
                                    modifier = Modifier.size(16.dp),
                                )
                                Spacer(Modifier.width(8.dp))
                                Text(
                                    text = error_text,
                                    color = colors.text_primary,
                                    fontSize = 13.sp,
                                    lineHeight = 18.sp,
                                    modifier = Modifier.weight(1f),
                                )
                            }
                            Spacer(Modifier.height(12.dp))
                        }

                        SpecialOfferDepthButton(
                            label = if (offer_state.offer_expired) {
                                stringResource(R.string.close)
                            } else {
                                stringResource(R.string.special_offer_cta_claim, percent_off)
                            },
                            is_loading = is_busy,
                            onClick = {
                                if (offer_state.offer_expired) {
                                    offer_vm.close()
                                } else if (play_install) {
                                    billing_vm.clear_messages()
                                    play_cta_pressed = true
                                    billing_vm.start_play_special_offer()
                                } else {
                                    billing_vm.clear_messages()
                                    offer_vm.release_checkout()
                                    offer_vm.accept()
                                }
                            },
                        )

                        Spacer(Modifier.height(10.dp))

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

                        Spacer(Modifier.height(2.dp))

                        Text(
                            text = stringResource(R.string.special_offer_dismiss),
                            color = colors.text_secondary,
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Medium,
                            textAlign = TextAlign.Center,
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(CircleShape)
                                .clickable(enabled = !is_busy, role = Role.Button) { offer_vm.dismiss_forever() }
                                .padding(vertical = 10.dp),
                        )
                    }
                }

                Box(
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .padding(top = 6.dp, end = 6.dp)
                        .size(CLOSE_TOUCH_TARGET)
                        .clip(CircleShape)
                        .clickable(enabled = !is_busy, role = Role.Button) { offer_vm.close() },
                    contentAlignment = Alignment.Center,
                ) {
                    Box(
                        modifier = Modifier
                            .size(CLOSE_BUTTON_SIZE)
                            .clip(CircleShape)
                            .background(CLOSE_BUTTON_FILL),
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

    when (offer_state.step) {
        SpecialOfferStep.payment_method -> payment_review_dialog(
            title = stringResource(R.string.checkout_review_title),
            plan_name = stringResource(R.string.plan_name_nova),
            interval_label = billing_interval_per_label(context, SPECIAL_OFFER_INTERVAL),
            amount_text = offer_label,
            subtotal_text = null,
            save_text = null,
            is_best_value = false,
            features = emptyList(),
            is_busy = is_busy,
            offer = review_offer_price(original = list_label, discounted = offer_label, badge = save_badge),
            on_dismiss = { offer_vm.show_step(SpecialOfferStep.offer) },
            on_confirm = { method ->
                if (method == payment_method_crypto) {
                    offer_vm.show_step(SpecialOfferStep.crypto_term)
                } else {
                    billing_vm.clear_messages()
                    offer_vm.begin_checkout()
                    billing_vm.start_checkout(plan_code, SPECIAL_OFFER_INTERVAL, SPECIAL_OFFER_CURRENCY)
                }
            },
        )
        SpecialOfferStep.crypto_term -> crypto_term_dialog(
            on_dismiss = { offer_vm.show_step(SpecialOfferStep.payment_method) },
            on_confirm = { term ->
                billing_vm.clear_messages()
                offer_vm.begin_checkout()
                billing_vm.start_crypto_checkout(plan_code, term)
            },
            offer_prices = term_prices,
        )
        SpecialOfferStep.offer -> Unit
    }
}

@Composable
private fun SpecialOfferHero(aspect_ratio: Float) {
    val colors = AsterMaterial.colors

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .aspectRatio(aspect_ratio),
    ) {
        Image(
            painter = painterResource(R.drawable.special_offer_hero),
            contentDescription = null,
            contentScale = ContentScale.Crop,
            modifier = Modifier.fillMaxSize(),
        )
        Box(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .height(1.dp)
                .background(colors.border_secondary),
        )
    }
}

@Composable
private fun SpecialOfferBadge() {
    val colors = AsterMaterial.colors

    Row(
        modifier = Modifier
            .clip(CircleShape)
            .background(colors.accent_blue.copy(alpha = if (colors.is_dark) 0.2f else 0.1f))
            .padding(start = 8.dp, end = 10.dp, top = 4.dp, bottom = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            imageVector = TablerIcons.Discount2,
            contentDescription = null,
            tint = colors.accent_blue,
            modifier = Modifier.size(14.dp),
        )
        Spacer(Modifier.width(5.dp))
        Text(
            text = stringResource(R.string.special_offer_entry),
            color = colors.accent_blue,
            fontSize = 12.sp,
            lineHeight = 16.sp,
            fontWeight = FontWeight.SemiBold,
        )
    }
}

@Composable
private fun SpecialOfferBenefits(benefits: List<Pair<String, String>>, show_details: Boolean) {
    val colors = AsterMaterial.colors

    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(if (show_details) 12.dp else 9.dp),
    ) {
        benefits.forEach { (title, body) ->
            Row(modifier = Modifier.fillMaxWidth()) {
                Box(
                    modifier = Modifier
                        .padding(top = 1.dp)
                        .size(BENEFIT_CHECK_SIZE)
                        .clip(CircleShape)
                        .background(colors.accent_blue),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        imageVector = TablerIcons.Check,
                        contentDescription = null,
                        tint = Color.White,
                        modifier = Modifier.size(13.dp),
                    )
                }
                Spacer(Modifier.width(12.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = title,
                        color = colors.text_primary,
                        fontSize = 15.sp,
                        lineHeight = 20.sp,
                        fontWeight = FontWeight.Medium,
                    )
                    if (show_details) {
                        Text(
                            text = body,
                            color = colors.text_secondary,
                            fontSize = 13.sp,
                            lineHeight = 18.sp,
                        )
                    }
                }
            }
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
        targetValue = if (pressed && !is_loading) 1.dp else CTA_LIP,
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
                .padding(top = CTA_LIP - lip, bottom = lip)
                .fillMaxWidth()
                .heightIn(min = CTA_HEIGHT - CTA_LIP)
                .clip(CTA_SHAPE)
                .background(fill)
                .border(
                    1.dp,
                    Brush.verticalGradient(0f to highlight, 0.45f to accent, 1f to accent),
                    CTA_SHAPE,
                )
                .padding(horizontal = 20.dp, vertical = 8.dp),
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
                    lineHeight = 21.sp,
                    fontWeight = FontWeight.SemiBold,
                    textAlign = TextAlign.Center,
                    maxLines = 2,
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
