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

import android.provider.Settings
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.material3.Text
import org.astermail.android.R
import org.astermail.android.design.AsterMaterial
import org.astermail.android.design.AsterSpacing
import org.astermail.android.design.components.AsterButton
import org.astermail.android.design.components.AsterSecondaryButton

@Composable
fun WelcomeScreen(
    on_sign_in: () -> Unit,
    on_create_account: () -> Unit,
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

    var visible by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) { visible = true }

    val base = if (colors.is_dark) colors.bg_primary else Color.White

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(base)
            .systemBarsPadding(),
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 32.dp),
            verticalArrangement = Arrangement.SpaceBetween,
        ) {
            Spacer(Modifier.weight(1f))

            Column(
                modifier = Modifier.fillMaxWidth(),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                stagger_item(visible, reduce_motion, delay_ms = 0) {
                    Image(
                        painter = painterResource(id = R.drawable.aster_wordmark),
                        contentDescription = stringResource(R.string.app_name),
                        modifier = Modifier.height(56.dp),
                    )
                }

                Spacer(Modifier.height(28.dp))

                stagger_item(visible, reduce_motion, delay_ms = 80) {
                    Text(
                        text = stringResource(R.string.welcome_tagline),
                        color = colors.text_primary,
                        fontSize = 28.sp,
                        fontWeight = FontWeight.SemiBold,
                        textAlign = TextAlign.Center,
                    )
                }

                Spacer(Modifier.height(12.dp))

                stagger_item(visible, reduce_motion, delay_ms = 140) {
                    Text(
                        text = stringResource(R.string.welcome_subtitle),
                        color = colors.text_tertiary,
                        fontSize = 15.sp,
                        textAlign = TextAlign.Center,
                        lineHeight = 22.sp,
                        modifier = Modifier.widthIn(max = 300.dp),
                    )
                }
            }

            Spacer(Modifier.weight(1f))

            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 32.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                stagger_item(visible, reduce_motion, delay_ms = 200) {
                    AsterButton(
                        label = stringResource(R.string.log_in),
                        onClick = on_sign_in,
                    )
                }
                stagger_item(visible, reduce_motion, delay_ms = 260) {
                    AsterSecondaryButton(
                        label = stringResource(R.string.create_account),
                        onClick = on_create_account,
                    )
                }
            }
        }
    }
}

@Composable
private fun stagger_item(
    visible: Boolean,
    reduce_motion: Boolean,
    delay_ms: Int,
    content: @Composable () -> Unit,
) {
    if (reduce_motion) {
        content()
        return
    }
    AnimatedVisibility(
        visible = visible,
        enter = fadeIn(animationSpec = tween(durationMillis = 260, delayMillis = delay_ms)) +
            slideInVertically(
                animationSpec = tween(durationMillis = 260, delayMillis = delay_ms),
                initialOffsetY = { it / 6 },
            ),
        exit = fadeOut(animationSpec = tween(durationMillis = 180)) +
            slideOutVertically(
                animationSpec = tween(durationMillis = 180),
                targetOffsetY = { it / 6 },
            ),
    ) {
        content()
    }
}
