//
// Aster Communications Inc.
//
// Copyright (c) 2026 Aster Communications Inc.
//
// This file is part of this project.
//
// This program is free software: you can redistribute it and/or modify
// it under the terms of the AGPLv3 as published by
// the Free Software Foundation, either version 3 of the License, or
// (at your option) any later version.
//
// This program is distributed in the hope that it will be useful,
// but WITHOUT ANY WARRANTY; without even the implied warranty of
// MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
// AGPLv3 for more details.
//
// You should have received a copy of the AGPLv3
// along with this program. If not, see <https://www.gnu.org/licenses/>.
//
package org.astermail.android.ui.guard

import android.os.Build
import android.text.format.DateUtils
import android.view.WindowManager
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.MutableTransitionState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.slideInVertically
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.DialogWindowProvider
import androidx.core.view.WindowCompat
import compose.icons.TablerIcons
import compose.icons.tablericons.BrandAndroid
import compose.icons.tablericons.BrandApple
import compose.icons.tablericons.BrandChrome
import compose.icons.tablericons.BrandWindows
import compose.icons.tablericons.DeviceDesktop
import compose.icons.tablericons.DeviceLaptop
import compose.icons.tablericons.DeviceMobile
import compose.icons.tablericons.DeviceTablet
import compose.icons.tablericons.World
import java.time.Instant
import java.util.Locale
import kotlinx.coroutines.delay
import org.astermail.android.R
import org.astermail.android.design.AsterEasing
import org.astermail.android.design.AsterMaterial
import org.astermail.android.design.AsterShapes
import org.astermail.android.design.components.AsterButton
import org.astermail.android.design.components.AsterCard
import org.astermail.android.design.components.AsterDestructiveButton
import org.astermail.android.design.components.AsterGhostButton
import org.astermail.android.design.field_surface_color
import org.astermail.android.design.AsterSpacing
import org.astermail.android.ui.common.aster_sheet_shape
import org.astermail.android.ui.common.sheet_container_color
import org.astermail.android.ui.mail.chip_background
import org.astermail.android.ui.mail.chip_border
import org.astermail.android.ui.mail.chip_content
import org.astermail.android.ui.settings.browser_logo_res

enum class guard_os_kind { windows, apple, android, chromeos, linux, phone, tablet, unknown }

fun guard_os_kind_for(os: String?, device_type: String): guard_os_kind {
    val name = os.orEmpty().lowercase()
    val type = device_type.lowercase()
    return when {
        name.contains("windows") -> guard_os_kind.windows
        name.contains("mac") || name.contains("ios") || name.contains("ipad") -> guard_os_kind.apple
        name.contains("android") -> guard_os_kind.android
        name.contains("chromeos") || name.contains("chrome os") -> guard_os_kind.chromeos
        name.contains("linux") || name.contains("ubuntu") || name.contains("fedora") -> guard_os_kind.linux
        type.contains("tablet") -> guard_os_kind.tablet
        type.contains("mobile") || type.contains("phone") -> guard_os_kind.phone
        else -> guard_os_kind.unknown
    }
}

fun guard_os_icon(kind: guard_os_kind): ImageVector = when (kind) {
    guard_os_kind.windows -> TablerIcons.BrandWindows
    guard_os_kind.apple -> TablerIcons.BrandApple
    guard_os_kind.android -> TablerIcons.BrandAndroid
    guard_os_kind.chromeos -> TablerIcons.BrandChrome
    guard_os_kind.linux -> TablerIcons.DeviceDesktop
    guard_os_kind.phone -> TablerIcons.DeviceMobile
    guard_os_kind.tablet -> TablerIcons.DeviceTablet
    guard_os_kind.unknown -> TablerIcons.DeviceLaptop
}

fun guard_flag_emoji(country_code: String?): String? {
    val code = country_code?.trim()?.uppercase(Locale.ROOT) ?: return null
    if (code.length != 2 || !code.all { it in 'A'..'Z' }) return null
    val base = 0x1F1E6 - 'A'.code
    return String(Character.toChars(base + code[0].code)) + String(Character.toChars(base + code[1].code))
}

fun guard_country_name(country: String?, locale: Locale = Locale.getDefault()): String? {
    val raw = country?.trim()?.takeIf { it.isNotEmpty() } ?: return null
    if (guard_flag_emoji(raw) == null) return raw
    val name = runCatching { Locale.Builder().setRegion(raw.uppercase(Locale.ROOT)).build().getDisplayCountry(locale) }
        .getOrNull()
    return name?.takeIf { it.isNotBlank() } ?: raw
}

fun guard_place_text(city: String?, country: String?, locale: Locale = Locale.getDefault()): String =
    listOfNotNull(city?.trim()?.takeIf { it.isNotEmpty() }, guard_country_name(country, locale)).joinToString(", ")

fun guard_countdown_text(remaining_seconds: Long, locale: Locale = Locale.getDefault()): String {
    val left = remaining_seconds.coerceAtLeast(0)
    return String.format(locale, "%d:%02d", left / 60, left % 60)
}

fun guard_epoch_millis(iso: String): Long? =
    iso.takeIf { it.isNotBlank() }?.let { runCatching { Instant.parse(it).toEpochMilli() }.getOrNull() }

val local_guard_clock = staticCompositionLocalOf<() -> Long> { { org.astermail.android.api.network.server_clock.now_ms() } }

@Composable
fun guard_now(period_ms: Long, ticking: Boolean = true): Long {
    val clock = local_guard_clock.current
    var now by remember(clock) { mutableLongStateOf(clock()) }
    LaunchedEffect(period_ms, ticking, clock) {
        while (ticking) {
            delay(period_ms - (clock() % period_ms))
            now = clock()
        }
    }
    return now
}

@Composable
fun guard_relative_time(iso: String, now: Long): String? {
    val at = guard_epoch_millis(iso) ?: return null
    if (now - at < DateUtils.MINUTE_IN_MILLIS) return stringResource(R.string.guard_time_just_now)
    val locale = Locale.getDefault()
    return DateUtils.getRelativeTimeSpanString(at, now, DateUtils.MINUTE_IN_MILLIS, DateUtils.FORMAT_ABBREV_RELATIVE)
        .toString()
        .replaceFirstChar { if (it.isLowerCase()) it.titlecase(locale) else it.toString() }
}

@Composable
private fun guard_dialog_window() {
    val view = LocalView.current
    val colors = AsterMaterial.colors
    SideEffect {
        val window = (view.parent as? DialogWindowProvider)?.window ?: return@SideEffect
        WindowCompat.setDecorFitsSystemWindows(window, false)
        window.setLayout(WindowManager.LayoutParams.MATCH_PARENT, WindowManager.LayoutParams.MATCH_PARENT)
        window.addFlags(WindowManager.LayoutParams.FLAG_SECURE)
        window.addFlags(WindowManager.LayoutParams.FLAG_DIM_BEHIND)
        window.setDimAmount(0.32f)
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.VANILLA_ICE_CREAM) {
            @Suppress("DEPRECATION")
            window.statusBarColor = android.graphics.Color.TRANSPARENT
            @Suppress("DEPRECATION")
            window.navigationBarColor = android.graphics.Color.TRANSPARENT
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) window.isNavigationBarContrastEnforced = false
        WindowCompat.getInsetsController(window, view).isAppearanceLightNavigationBars =
            sheet_container_color(colors).luminance() > 0.5f
        view.filterTouchesWhenObscured = true
    }
}

@Composable
fun guard_sheet_frame(content: @Composable ColumnScope.() -> Unit) {
    guard_dialog_window()
    val colors = AsterMaterial.colors
    val visible = remember { MutableTransitionState(false) }
    visible.targetState = true
    Box(
        modifier = Modifier
            .fillMaxSize()
            .statusBarsPadding(),
        contentAlignment = Alignment.BottomCenter,
    ) {
        AnimatedVisibility(
            visibleState = visible,
            enter = slideInVertically(tween(320, easing = AsterEasing.emphasized_enter)) { it / 4 } +
                fadeIn(tween(180)),
        ) {
            Column(
                modifier = Modifier
                    .widthIn(max = 560.dp)
                    .fillMaxWidth()
                    .clip(aster_sheet_shape)
                    .background(sheet_container_color(colors))
                    .navigationBarsPadding()
                    .imePadding()
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = AsterSpacing.xl)
                    .padding(top = AsterSpacing.md, bottom = AsterSpacing.xl),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Box(
                    Modifier
                        .size(width = 36.dp, height = 4.dp)
                        .clip(CircleShape)
                        .background(colors.border_primary),
                )
                Spacer(Modifier.height(AsterSpacing.xl))
                content()
            }
        }
    }
}

@Composable
fun guard_wordmark(modifier: Modifier = Modifier, height: Dp = 22.dp) {
    Image(
        painter = painterResource(R.drawable.aster_wordmark),
        contentDescription = stringResource(R.string.guard_logo_description),
        contentScale = ContentScale.FillWidth,
        modifier = modifier
            .height(height)
            .aspectRatio(guard_wordmark_ratio),
    )
}

@Composable
fun guard_sheet_header(title: String, body: String? = null) {
    guard_wordmark()
    Spacer(Modifier.height(AsterSpacing.xxl))
    guard_sheet_header_text(title = title)
    if (body != null) {
        Spacer(Modifier.height(AsterSpacing.sm))
        guard_body_text(body)
    }
}

@Composable
fun guard_result_block(
    title: String,
    body: String?,
    primary_label: String,
    on_primary: () -> Unit,
    modifier: Modifier = Modifier,
    extra_lines: List<String> = emptyList(),
    secondary_label: String? = null,
    on_secondary: (() -> Unit)? = null,
    primary_destructive: Boolean = false,
) {
    Column(
        modifier = modifier.fillMaxWidth().testTag("guard_result"),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        guard_wordmark()
        Spacer(Modifier.height(AsterSpacing.xxl + AsterSpacing.sm))
        guard_wizard_title(title, Modifier.fillMaxWidth().testTag("guard_result_title"), TextAlign.Center)
        if (body != null) {
            Spacer(Modifier.height(AsterSpacing.sm))
            guard_wizard_body(body, Modifier.fillMaxWidth(), TextAlign.Center)
        }
        if (extra_lines.isNotEmpty()) {
            Spacer(Modifier.height(AsterSpacing.md))
            extra_lines.forEach { guard_wizard_body(it, Modifier.fillMaxWidth(), TextAlign.Center) }
        }
        Spacer(Modifier.height(AsterSpacing.xxl + AsterSpacing.sm))
        if (primary_destructive) {
            AsterDestructiveButton(label = primary_label, onClick = on_primary, modifier = Modifier.fillMaxWidth())
        } else {
            AsterButton(label = primary_label, onClick = on_primary, modifier = Modifier.fillMaxWidth())
        }
        if (secondary_label != null && on_secondary != null) {
            Spacer(Modifier.height(AsterSpacing.sm))
            AsterGhostButton(label = secondary_label, onClick = on_secondary, modifier = Modifier.fillMaxWidth())
        }
    }
}

@Composable
fun guard_sheet_header_text(title: String, body: String? = null) {
    Text(
        text = title,
        color = AsterMaterial.colors.text_primary,
        fontSize = 22.sp,
        lineHeight = 28.sp,
        fontWeight = FontWeight.SemiBold,
        textAlign = TextAlign.Center,
        modifier = Modifier.semantics { heading() },
    )
    if (body != null) {
        Spacer(Modifier.height(AsterSpacing.sm))
        guard_body_text(body)
    }
}

@Composable
fun guard_body_text(text: String, modifier: Modifier = Modifier) {
    Text(
        text = text,
        color = AsterMaterial.colors.text_secondary,
        fontSize = 14.sp,
        lineHeight = 20.sp,
        textAlign = TextAlign.Center,
        modifier = modifier,
    )
}

@Composable
fun guard_chip(text: String, icon: ImageVector?, color: Color, modifier: Modifier = Modifier) {
    val colors = AsterMaterial.colors
    val background = chip_background(color, colors.bg_primary, colors.is_dark)
    val content = chip_content(color, background, colors.is_dark)
    val border = chip_border(color, colors.bg_primary, colors.is_dark)
    Row(
        modifier = modifier
            .clip(CircleShape)
            .background(background)
            .border(1.dp, border, CircleShape)
            .padding(horizontal = 10.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (icon != null) {
            Icon(imageVector = icon, contentDescription = null, tint = content, modifier = Modifier.size(14.dp))
            Spacer(Modifier.width(5.dp))
        }
        Text(text = text, color = content, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
    }
}

private const val guard_wordmark_ratio = 800f / 199f

@Composable
fun guard_vector_tile(icon: ImageVector, color: Color, size: Dp = 40.dp) {
    Icon(imageVector = icon, contentDescription = null, tint = color, modifier = Modifier.size(size * 0.5f))
}

@Composable
fun guard_card(modifier: Modifier = Modifier, content: @Composable ColumnScope.() -> Unit) {
    AsterCard(modifier = modifier.fillMaxWidth(), content = content)
}

private val guard_detail_icon_column = 20.dp

@Composable
fun guard_detail_row(
    tile: @Composable () -> Unit,
    label: String,
    value: String,
    supporting: String? = null,
    value_leading: (@Composable () -> Unit)? = null,
) {
    val colors = AsterMaterial.colors
    val spoken = listOfNotNull(label, value, supporting).joinToString(", ")
    val indent = guard_detail_icon_column + AsterSpacing.md
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clearAndSetSemantics { contentDescription = spoken }
            .padding(horizontal = AsterSpacing.lg, vertical = AsterSpacing.sm),
    ) {
        Text(
            text = label,
            color = colors.text_secondary,
            fontSize = 12.sp,
            lineHeight = 16.sp,
            modifier = Modifier.padding(start = indent),
        )
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.width(guard_detail_icon_column), contentAlignment = Alignment.Center) { tile() }
            Spacer(Modifier.width(AsterSpacing.md))
            if (value_leading != null) {
                value_leading()
                Spacer(Modifier.width(AsterSpacing.sm))
            }
            Text(
                text = value,
                color = colors.text_primary,
                fontSize = 15.sp,
                lineHeight = 20.sp,
                fontWeight = FontWeight.Medium,
                modifier = Modifier.weight(1f, fill = false),
            )
        }
        if (supporting != null) {
            Text(
                text = supporting,
                color = colors.text_tertiary,
                fontSize = 12.sp,
                lineHeight = 16.sp,
                modifier = Modifier.padding(start = indent),
            )
        }
    }
}

@Composable
fun guard_browser_mark(browser: String?, size: Dp = 18.dp) {
    val name = browser.orEmpty()
    val res = when {
        name.contains("aster", ignoreCase = true) -> R.drawable.aster_favicon
        else -> browser_logo_res(name)
    }
    if (res != null) {
        Image(painter = painterResource(res), contentDescription = null, modifier = Modifier.size(size))
    } else {
        Icon(
            imageVector = TablerIcons.World,
            contentDescription = null,
            tint = AsterMaterial.colors.text_secondary,
            modifier = Modifier.size(size),
        )
    }
}

internal fun guard_digit_input(raw: String, digits: Int): String =
    raw.filter { it in '0'..'9' }.take(digits)

internal fun guard_active_digit(value: String, digits: Int): Int =
    value.length.coerceAtMost(digits - 1)

@Composable
fun guard_digit_boxes(
    value: String,
    on_value: (String) -> Unit,
    digits: Int,
    error: Boolean,
    shake_key: Int,
    enabled: Boolean,
    description: String,
) {
    val colors = AsterMaterial.colors
    val focus = remember { FocusRequester() }
    var focused by remember { mutableStateOf(false) }
    val shake = remember { Animatable(0f) }
    LaunchedEffect(shake_key) {
        if (shake_key == 0) return@LaunchedEffect
        for (x in listOf(14f, -12f, 9f, -6f, 3f, 0f)) shake.animateTo(x, tween(48))
    }
    LaunchedEffect(Unit) { runCatching { focus.requestFocus() } }
    BasicTextField(
        value = TextFieldValue(value, TextRange(value.length)),
        onValueChange = { next ->
            val clean = guard_digit_input(next.text, digits)
            if (clean != value) on_value(clean)
        },
        enabled = enabled,
        singleLine = true,
        cursorBrush = SolidColor(Color.Transparent),
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword, imeAction = ImeAction.Done),
        modifier = Modifier
            .focusRequester(focus)
            .onFocusChanged { focused = it.isFocused }
            .semantics { contentDescription = description },
        decorationBox = { _ ->
            Row(
                modifier = Modifier.graphicsLayer { translationX = shake.value.dp.toPx() },
                horizontalArrangement = Arrangement.spacedBy(AsterSpacing.md),
            ) {
                repeat(digits) { index ->
                    val char = value.getOrNull(index)
                    val active = focused && index == guard_active_digit(value, digits)
                    val ring = when {
                        error -> colors.danger
                        active -> colors.accent_blue
                        else -> Color.Transparent
                    }
                    val shape = AsterShapes.control
                    Box(
                        modifier = Modifier
                            .testTag("guard_digit_box_$index")
                            .size(width = 56.dp, height = 64.dp)
                            .background(field_surface_color(colors), shape)
                            .border(2.dp, ring, shape),
                        contentAlignment = Alignment.Center,
                    ) {
                        if (char != null) {
                            Text(
                                text = char.toString(),
                                color = colors.text_primary,
                                fontSize = 28.sp,
                                fontWeight = FontWeight.SemiBold,
                            )
                        } else if (!active) {
                            Box(
                                Modifier
                                    .size(8.dp)
                                    .background(colors.text_tertiary.copy(alpha = 0.45f), CircleShape),
                            )
                        } else {
                            Box(
                                Modifier
                                    .size(width = 2.dp, height = 28.dp)
                                    .background(colors.accent_blue, CircleShape),
                            )
                        }
                    }
                }
            }
        },
    )
}
