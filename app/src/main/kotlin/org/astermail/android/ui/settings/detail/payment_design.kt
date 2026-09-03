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

import androidx.annotation.DrawableRes
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import compose.icons.TablerIcons
import compose.icons.tablericons.Lock
import org.astermail.android.R
import org.astermail.android.design.AsterMaterial
import org.astermail.android.design.AsterSpacing
import org.astermail.android.design.SquircleShape

internal fun blend(base: Color, target: Color, amount: Float): Color = Color(
    red = base.red + (target.red - base.red) * amount,
    green = base.green + (target.green - base.green) * amount,
    blue = base.blue + (target.blue - base.blue) * amount,
    alpha = base.alpha,
)

@Composable
internal fun galaxy_border_brush(accent: Color, tail: Color): Brush = Brush.verticalGradient(
    0.00f to blend(accent, Color.White, 0.30f),
    0.14f to accent,
    0.38f to accent.copy(alpha = 0.40f),
    0.66f to accent.copy(alpha = 0.10f),
    1.00f to tail.copy(alpha = 0.10f),
)

@Composable
internal fun galaxy_surface(
    modifier: Modifier = Modifier,
    corner: Dp = 16.dp,
    content: @Composable ColumnScope.() -> Unit,
) {
    val colors = AsterMaterial.colors
    val accent = colors.accent_blue
    val shape = SquircleShape(corner)
    Column(
        modifier = modifier
            .border(1.dp, galaxy_border_brush(accent, colors.text_primary), shape)
            .padding(1.dp)
            .clip(shape)
            .background(colors.bg_tertiary)
            .background(
                Brush.verticalGradient(
                    0.00f to accent.copy(alpha = 0.08f),
                    0.22f to accent.copy(alpha = 0.02f),
                    0.46f to Color.Transparent,
                ),
            ),
        content = content,
    )
}

@Composable
internal fun galaxy_badge(
    text: String,
    modifier: Modifier = Modifier,
    font_size: androidx.compose.ui.unit.TextUnit = 10.sp,
) {
    val accent = AsterMaterial.colors.accent_blue
    Box(
        modifier = modifier
            .clip(CircleShape)
            .background(
                Brush.verticalGradient(
                    0.00f to blend(accent, Color.White, 0.22f),
                    0.46f to accent,
                    1.00f to blend(accent, Color.Black, 0.12f),
                ),
            )
            .padding(horizontal = 8.dp, vertical = 3.dp),
    ) {
        Text(
            text = text.uppercase(),
            color = Color.White,
            fontSize = font_size,
            fontWeight = FontWeight.Bold,
            letterSpacing = 0.6.sp,
        )
    }
}

internal data class switcher_option(
    val id: String,
    val label: String,
    val badge: String? = null,
)

@Composable
internal fun aster_tabs(
    value: String,
    options: List<switcher_option>,
    on_change: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = AsterMaterial.colors
    Row(
        modifier = modifier,
        horizontalArrangement = Arrangement.spacedBy(AsterSpacing.lg),
    ) {
        options.forEach { option ->
            val active = option.id == value
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier
                    .clickable(role = Role.Tab) { on_change(option.id) }
                    .padding(horizontal = 4.dp),
            ) {
                Text(
                    text = option.label,
                    color = if (active) colors.text_primary else colors.text_muted,
                    fontSize = 14.sp,
                    fontWeight = if (active) FontWeight.SemiBold else FontWeight.Medium,
                    modifier = Modifier.padding(vertical = 12.dp),
                )
                Box(
                    modifier = Modifier
                        .width(if (active) 100.dp else 0.dp)
                        .height(2.dp)
                        .background(
                            if (active) colors.accent_blue else Color.Transparent,
                            RoundedCornerShape(2.dp),
                        ),
                )
            }
        }
    }
}

@Composable
internal fun aster_segmented(
    value: String,
    options: List<switcher_option>,
    on_change: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = AsterMaterial.colors
    Row(
        modifier = modifier
            .clip(CircleShape)
            .border(1.dp, colors.border_secondary, CircleShape)
            .padding(4.dp),
        horizontalArrangement = Arrangement.spacedBy(4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        options.forEach { option ->
            val active = option.id == value
            Row(
                modifier = Modifier
                    .clip(CircleShape)
                    .background(if (active) colors.accent_blue else Color.Transparent)
                    .clickable(role = Role.RadioButton) { on_change(option.id) }
                    .padding(horizontal = 14.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                Text(
                    text = option.label,
                    color = if (active) Color.White else colors.text_muted,
                    fontSize = 13.sp,
                    fontWeight = if (active) FontWeight.SemiBold else FontWeight.Medium,
                )
                if (option.badge != null) {
                    Box(
                        modifier = Modifier
                            .clip(CircleShape)
                            .background(
                                if (active) {
                                    Color.White.copy(alpha = 0.22f)
                                } else {
                                    colors.accent_blue.copy(alpha = 0.16f)
                                },
                            )
                            .padding(horizontal = 6.dp, vertical = 2.dp),
                    ) {
                        Text(
                            text = option.badge,
                            color = if (active) Color.White else colors.accent_blue,
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold,
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun brand_tile(
    background: Color,
    border: Color?,
    @DrawableRes mark: Int,
    mark_width: Dp,
    mark_height: Dp,
    label: String,
) {
    val shape = RoundedCornerShape(4.dp)
    Box(
        modifier = Modifier
            .size(width = 36.dp, height = 24.dp)
            .clip(shape)
            .background(background)
            .then(if (border != null) Modifier.border(1.dp, border, shape) else Modifier),
        contentAlignment = Alignment.Center,
    ) {
        Image(
            painter = painterResource(mark),
            contentDescription = label,
            contentScale = ContentScale.Fit,
            modifier = Modifier.size(width = mark_width, height = mark_height),
        )
    }
}

@Composable
internal fun card_brand_marks(modifier: Modifier = Modifier) {
    val hairline = Color.Black.copy(alpha = 0.12f)
    Row(
        modifier = modifier,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        brand_tile(Color.White, hairline, R.drawable.ic_brand_visa, 30.dp, 30.dp, "Visa")
        brand_tile(Color.White, hairline, R.drawable.ic_brand_mastercard, 26.dp, 16.dp, "Mastercard")
        brand_tile(Color(0xFF006FCF), null, R.drawable.ic_brand_amex, 30.dp, 30.dp, "American Express")
        brand_tile(Color.White, hairline, R.drawable.ic_brand_discover, 30.dp, 30.dp, "Discover")
    }
}

@DrawableRes
internal fun coin_drawable_for(currency: String, chain: String): Int =
    when (currency.trim().lowercase()) {
        "btc", "xbt" -> R.drawable.ic_coin_btc
        "eth", "weth" -> R.drawable.ic_coin_eth
        "usdc" -> R.drawable.ic_coin_usdc
        "usdt", "tether" -> R.drawable.ic_coin_usdt
        "dai" -> R.drawable.ic_coin_dai
        "ltc" -> R.drawable.ic_coin_ltc
        "sol" -> R.drawable.ic_coin_sol
        "bch" -> R.drawable.ic_coin_bch
        "xmr" -> R.drawable.ic_coin_xmr
        "stable", "stablecoin" -> R.drawable.ic_coin_stable
        else -> chain_drawable_for(chain)
    }

@DrawableRes
internal fun chain_drawable_for(chain: String): Int =
    when (chain.trim().lowercase().replace("-", "")) {
        "bitcoin" -> R.drawable.ic_coin_btc
        "ethereum" -> R.drawable.ic_coin_eth
        "base" -> R.drawable.ic_coin_base
        "monero" -> R.drawable.ic_coin_xmr
        "litecoin" -> R.drawable.ic_coin_ltc
        "solana" -> R.drawable.ic_coin_sol
        "bitcoincash" -> R.drawable.ic_coin_bch
        else -> R.drawable.ic_coin_generic
    }

@Composable
internal fun coin_mark(
    currency: String,
    chain: String,
    label: String,
    size: Dp = 24.dp,
    modifier: Modifier = Modifier,
) {
    val colors = AsterMaterial.colors
    val coin = coin_drawable_for(currency, chain)
    val chain_mark = chain_drawable_for(chain)
    Box(modifier = modifier.size(size), contentAlignment = Alignment.BottomEnd) {
        Image(
            painter = painterResource(coin),
            contentDescription = label,
            modifier = Modifier.size(size),
        )
        if (chain_mark != coin) {
            Image(
                painter = painterResource(chain_mark),
                contentDescription = null,
                modifier = Modifier
                    .size(size * 0.42f)
                    .offset(x = 2.dp, y = 2.dp)
                    .border(1.5.dp, colors.bg_tertiary, CircleShape)
                    .clip(CircleShape),
            )
        }
    }
}

private val stacked_coins = listOf(
    Triple("btc", "bitcoin", "Bitcoin"),
    Triple("eth", "ethereum", "Ethereum"),
    Triple("usdc", "base", "USD Coin"),
    Triple("usdt", "ethereum", "Tether"),
    Triple("ltc", "litecoin", "Litecoin"),
    Triple("xmr", "monero", "Monero"),
)

@Composable
internal fun coin_stack(modifier: Modifier = Modifier) {
    Row(
        modifier = modifier,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        stacked_coins.forEach { (currency, chain, label) ->
            coin_mark(currency = currency, chain = chain, label = label, size = 22.dp)
        }
    }
}

@Composable
internal fun security_marks(label: String, modifier: Modifier = Modifier) {
    val colors = AsterMaterial.colors
    Row(
        modifier = modifier,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            imageVector = TablerIcons.Lock,
            contentDescription = null,
            tint = colors.accent_blue,
            modifier = Modifier.size(13.dp),
        )
        Text(text = label, color = colors.text_muted, fontSize = 11.sp)
    }
}

internal val review_tile_padding = PaddingValues(
    start = AsterSpacing.md,
    end = AsterSpacing.md,
    top = AsterSpacing.md,
    bottom = AsterSpacing.md,
)

@Composable
internal fun review_tile(
    active: Boolean,
    enabled: Boolean,
    on_click: () -> Unit,
    modifier: Modifier = Modifier,
    content: @Composable ColumnScope.() -> Unit,
) {
    val colors = AsterMaterial.colors
    val shape = SquircleShape(12.dp)
    Column(
        modifier = modifier
            .fillMaxWidth()
            .clip(shape)
            .background(if (active) colors.accent_blue.copy(alpha = 0.14f) else Color.Transparent)
            .border(
                if (active) 1.5.dp else 1.dp,
                if (active) colors.accent_blue else colors.border_secondary,
                shape,
            )
            .clickable(enabled = enabled, role = Role.RadioButton, onClick = on_click)
            .padding(review_tile_padding),
        content = content,
    )
}
