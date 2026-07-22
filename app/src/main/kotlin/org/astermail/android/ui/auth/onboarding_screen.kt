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

package org.astermail.android.ui.auth

import compose.icons.TablerIcons
import compose.icons.tablericons.*

import android.provider.Settings
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.launch
import org.astermail.android.R
import org.astermail.android.design.AsterDuration
import org.astermail.android.design.AsterEasing
import org.astermail.android.design.AsterMaterial
import org.astermail.android.design.components.AsterButton
import org.astermail.android.design.components.AsterSecondaryButton

private data class OnboardingSlide(
    val title_res: Int,
    val body_res: Int,
    val icon: ImageVector?,
)

@Composable
fun OnboardingScreen(
    on_sign_in: () -> Unit,
    on_create_account: () -> Unit,
    on_skip: () -> Unit,
) {
    val colors = AsterMaterial.colors
    val context = LocalContext.current
    val reduce_motion = remember {
        Settings.Global.getFloat(
            context.contentResolver,
            Settings.Global.ANIMATOR_DURATION_SCALE,
            1f,
        ) == 0f
    }

    val slides = remember {
        listOf(
            OnboardingSlide(
                title_res = R.string.onboarding_slide1_title,
                body_res = R.string.onboarding_slide1_body,
                icon = null,
            ),
            OnboardingSlide(
                title_res = R.string.onboarding_slide2_title,
                body_res = R.string.onboarding_slide2_body,
                icon = TablerIcons.Lock,
            ),
            OnboardingSlide(
                title_res = R.string.onboarding_slide3_title,
                body_res = R.string.onboarding_slide3_body,
                icon = TablerIcons.EyeOff,
            ),
            OnboardingSlide(
                title_res = R.string.onboarding_slide4_title,
                body_res = R.string.onboarding_slide4_body,
                icon = TablerIcons.At,
            ),
        )
    }

    val page_count = slides.size
    val pager_state = rememberPagerState { page_count }
    val scope = rememberCoroutineScope()
    val is_last_page = pager_state.currentPage == page_count - 1
    val base_bg = if (colors.is_dark) colors.bg_primary else Color.White

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(base_bg)
            .systemBarsPadding(),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(48.dp)
                .padding(end = 8.dp),
            horizontalArrangement = Arrangement.End,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            AnimatedVisibility(
                visible = !is_last_page,
                enter = fadeIn(),
                exit = fadeOut(),
            ) {
                TextButton(onClick = on_skip) {
                    Text(
                        text = stringResource(R.string.onboarding_skip),
                        color = colors.text_tertiary,
                        fontSize = 15.sp,
                    )
                }
            }
        }

        HorizontalPager(
            state = pager_state,
            modifier = Modifier.weight(1f),
        ) { page ->
            onboarding_page(slide = slides[page])
        }

        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 32.dp, vertical = 24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                repeat(page_count) { index ->
                    val is_selected = pager_state.currentPage == index
                    val dot_width by animateDpAsState(
                        targetValue = if (is_selected) 24.dp else 8.dp,
                        animationSpec = tween(durationMillis = AsterDuration.short_4, easing = AsterEasing.standard_enter),
                        label = "dot",
                    )
                    Box(
                        modifier = Modifier
                            .height(8.dp)
                            .width(dot_width)
                            .clip(CircleShape)
                            .background(
                                if (is_selected) colors.accent_blue else colors.border_secondary,
                            ),
                    )
                }
            }

            if (is_last_page) {
                AsterButton(
                    label = stringResource(R.string.log_in),
                    onClick = on_sign_in,
                )
                AsterSecondaryButton(
                    label = stringResource(R.string.create_account),
                    onClick = on_create_account,
                )
            } else {
                AsterButton(
                    label = stringResource(R.string.next),
                    onClick = {
                        scope.launch {
                            pager_state.animateScrollToPage(
                                page = pager_state.currentPage + 1,
                                animationSpec = tween(
                                    durationMillis = AsterDuration.medium_3,
                                    easing = AsterEasing.standard_enter,
                                ),
                            )
                        }
                    },
                )
            }
        }
    }
}

@Composable
private fun onboarding_page(slide: OnboardingSlide) {
    val colors = AsterMaterial.colors

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        if (slide.icon != null) {
            Icon(
                imageVector = slide.icon,
                contentDescription = null,
                modifier = Modifier.size(72.dp),
                tint = colors.accent_blue,
            )
        } else {
            Image(
                painter = painterResource(R.drawable.aster_wordmark),
                contentDescription = null,
                modifier = Modifier.height(48.dp),
            )
        }

        Spacer(Modifier.height(40.dp))

        Text(
            text = stringResource(slide.title_res),
            color = colors.text_primary,
            fontSize = 30.sp,
            fontWeight = FontWeight.ExtraBold,
            textAlign = TextAlign.Center,
            lineHeight = 36.sp,
            letterSpacing = (-0.3).sp,
        )

        Spacer(Modifier.height(14.dp))

        Text(
            text = stringResource(slide.body_res),
            color = colors.text_tertiary,
            fontSize = 16.sp,
            textAlign = TextAlign.Center,
            lineHeight = 22.sp,
            modifier = Modifier.widthIn(max = 320.dp),
        )

    }
}
