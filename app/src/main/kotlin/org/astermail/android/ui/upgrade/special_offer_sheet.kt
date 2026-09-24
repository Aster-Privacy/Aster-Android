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
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.FastOutLinearInEasing
import androidx.compose.animation.core.spring
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.input.pointer.pointerInput
import compose.icons.tablericons.ChevronDown
import compose.icons.tablericons.InfoCircle
import compose.icons.tablericons.AlertCircle
import compose.icons.tablericons.CircleCheck
import compose.icons.tablericons.X

import android.content.Context
import androidx.activity.compose.BackHandler
import android.content.ContextWrapper
import androidx.activity.ComponentActivity
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
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
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
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
import org.astermail.android.design.components.AsterPlanTag
import org.astermail.android.design.SquircleShape
import org.astermail.android.design.acrylic
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
private val CLOSE_BUTTON_SIZE = 34.dp
private const val HERO_FADE_START = 0.58f
private val DIVIDER_THICKNESS = 1.5.dp
private val CLOSE_TOUCH_TARGET = 48.dp
private val BENEFIT_CHECK_SIZE = 20.dp
private const val OPEN_DAMPING = 0.78f
private const val OPEN_STIFFNESS = 420f
private const val CLOSE_MS = 170
private const val SCRIM_LEAD = 1.6f
private const val APPEAR_START_SCALE = 0.94f
private val APPEAR_LIFT = 18.dp

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

    LaunchedEffect(billing_state.subscription?.plan?.code) {
        offer_vm.on_plan_code(billing_state.subscription?.plan?.code)
    }

    var play_cta_pressed by remember { mutableStateOf(false) }
    LaunchedEffect(offer_state.is_open, billing_state.is_acting, billing_state.play_purchase_request) {
        if (!offer_state.is_open) {
            play_cta_pressed = false
            return@LaunchedEffect
        }
        if (!play_install || !play_cta_pressed || billing_state.is_acting) return@LaunchedEffect
        if (billing_state.play_purchase_request != null || billing_state.error.isNullOrBlank()) {
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

    val appear = remember { Animatable(0f) }
    var mounted by remember { mutableStateOf(false) }
    LaunchedEffect(offer_state.is_open) {
        if (offer_state.is_open) {
            mounted = true
            appear.animateTo(1f, spring(dampingRatio = OPEN_DAMPING, stiffness = OPEN_STIFFNESS))
        } else if (mounted) {
            appear.animateTo(0f, tween(durationMillis = CLOSE_MS, easing = FastOutLinearInEasing))
            mounted = false
        }
    }

    if (!mounted && !offer_state.is_open) return

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

    val interactive = offer_state.is_open
    val close_offer = {
        if (offer_state.is_open) {
            billing_vm.cancel_play_special_offer()
            offer_vm.close()
        }
    }

    BackHandler(enabled = interactive, onBack = close_offer)

    val colors = AsterMaterial.colors
    val lift_px = with(LocalDensity.current) { APPEAR_LIFT.toPx() }

    BoxWithConstraints(
        modifier = Modifier
            .fillMaxSize()
            .drawBehind {
                drawRect(SCRIM_COLOR, alpha = (appear.value * SCRIM_LEAD).coerceIn(0f, 1f))
            }
            .pointerInput(Unit) { detectTapGestures { } }
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
                .graphicsLayer {
                    val scale = APPEAR_START_SCALE + (1f - APPEAR_START_SCALE) * appear.value
                    alpha = appear.value.coerceIn(0f, 1f)
                    scaleX = scale
                    scaleY = scale
                    translationY = (1f - appear.value) * lift_px
                    transformOrigin = TransformOrigin(0.5f, 0.6f)
                }
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
                            top = 6.dp,
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

                    Spacer(Modifier.height(gap + 4.dp))

                    SpecialOfferDivider()

                    Spacer(Modifier.height(gap + 4.dp))

                    SpecialOfferBenefits(benefits = benefits, show_details = !compact)

                    Spacer(Modifier.height(gap))

                    SpecialOfferWhy()

                    Spacer(Modifier.height(gap))

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
                        text = pluralStringResource(
                            R.plurals.special_offer_fine_print,
                            months,
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
                            .clickable(enabled = interactive, role = Role.Button) {
                                billing_vm.cancel_play_special_offer()
                                offer_vm.dismiss_forever()
                            }
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
                    .clickable(enabled = interactive, role = Role.Button) { close_offer() },
                contentAlignment = Alignment.Center,
            ) {
                Box(
                    modifier = Modifier
                        .size(CLOSE_BUTTON_SIZE)
                        .shadow(elevation = 4.dp, shape = CircleShape)
                        .acrylic(colors, CircleShape, colors.bg_secondary)
                        .border(1.dp, colors.border_secondary, CircleShape),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        imageVector = TablerIcons.X,
                        contentDescription = stringResource(R.string.close),
                        tint = colors.text_primary,
                        modifier = Modifier.size(17.dp),
                    )
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
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .aspectRatio(aspect_ratio),
    ) {
        Image(
            painter = painterResource(R.drawable.special_offer_hero),
            contentDescription = null,
            contentScale = ContentScale.Crop,
            modifier = Modifier
                .fillMaxSize()
                .graphicsLayer { compositingStrategy = CompositingStrategy.Offscreen }
                .drawWithContent {
                    drawContent()
                    drawRect(
                        brush = Brush.verticalGradient(
                            0f to Color.Black,
                            HERO_FADE_START to Color.Black,
                            1f to Color.Transparent,
                        ),
                        blendMode = BlendMode.DstIn,
                    )
                },
        )
    }
}

@Composable
private fun SpecialOfferDivider() {
    val edge = AsterMaterial.colors.border_secondary
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(DIVIDER_THICKNESS)
            .clip(CircleShape)
            .background(
                Brush.horizontalGradient(
                    0f to edge.copy(alpha = 0f),
                    0.2f to edge,
                    0.8f to edge,
                    1f to edge.copy(alpha = 0f),
                ),
            ),
    )
}

@Composable
private fun SpecialOfferWhy() {
    val colors = AsterMaterial.colors
    var expanded by rememberSaveable { mutableStateOf(false) }
    val chevron by animateFloatAsState(
        targetValue = if (expanded) 180f else 0f,
        animationSpec = tween(durationMillis = 200, easing = FastOutSlowInEasing),
        label = "offer_why_chevron",
    )

    Column(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier
                .clip(CircleShape)
                .clickable(role = Role.Button) { expanded = !expanded }
                .padding(vertical = 6.dp, horizontal = 2.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                imageVector = TablerIcons.InfoCircle,
                contentDescription = null,
                tint = colors.text_tertiary,
                modifier = Modifier.size(15.dp),
            )
            Spacer(Modifier.width(6.dp))
            Text(
                text = stringResource(R.string.special_offer_why_label),
                color = colors.text_secondary,
                fontSize = 13.sp,
                lineHeight = 18.sp,
                fontWeight = FontWeight.Medium,
            )
            Spacer(Modifier.width(2.dp))
            Icon(
                imageVector = TablerIcons.ChevronDown,
                contentDescription = null,
                tint = colors.text_tertiary,
                modifier = Modifier
                    .size(14.dp)
                    .graphicsLayer { rotationZ = chevron },
            )
        }
        AnimatedVisibility(
            visible = expanded,
            enter = fadeIn(tween(200, delayMillis = 40)) + expandVertically(tween(220, easing = FastOutSlowInEasing)),
            exit = fadeOut(tween(120)) + shrinkVertically(tween(200, easing = FastOutSlowInEasing)),
        ) {
            Text(
                text = stringResource(R.string.special_offer_why_body),
                color = colors.text_secondary,
                fontSize = 13.sp,
                lineHeight = 19.sp,
                modifier = Modifier.padding(start = 23.dp, top = 2.dp, bottom = 4.dp),
            )
        }
    }
}

@Composable
private fun SpecialOfferBadge() {
    AsterPlanTag(
        text = stringResource(R.string.special_offer_entry),
        font_size = 12.sp,
        horizontal_padding = 9.dp,
        vertical_padding = 3.dp,
    )
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
                Icon(
                    imageVector = TablerIcons.CircleCheck,
                    contentDescription = null,
                    tint = colors.accent_blue,
                    modifier = Modifier.size(BENEFIT_CHECK_SIZE),
                )
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
