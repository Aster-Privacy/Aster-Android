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

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.material3.Text
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.astermail.android.R
import org.astermail.android.design.AsterMaterial
import org.astermail.android.design.AsterSpacing
import org.astermail.android.design.components.AsterSecondaryButton
import org.astermail.android.ui.mail.SenderAvatar

@Composable
internal fun alias_avatar_field(
    address: String,
    profile_picture: String?,
    locked: Boolean,
    on_change: (String?) -> Unit,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var working by remember { mutableStateOf(false) }
    var failed by remember { mutableStateOf(false) }

    val picker = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri: Uri? ->
        uri ?: return@rememberLauncherForActivityResult
        scope.launch {
            working = true
            failed = false
            val data_uri = withContext(Dispatchers.IO) { read_image_as_data_uri(context, uri) }
            working = false
            if (data_uri == null) failed = true else on_change(data_uri)
        }
    }

    Column(verticalArrangement = Arrangement.spacedBy(AsterSpacing.xs)) {
        Text(
            text = stringResource(R.string.alias_panel_photo),
            color = AsterMaterial.colors.text_tertiary,
            fontSize = 12.sp,
            fontWeight = FontWeight.Medium,
        )
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(AsterSpacing.md),
        ) {
            SenderAvatar(
                email = address,
                size = 44.dp,
                profile_picture_url = profile_picture,
            )
            AsterSecondaryButton(
                label = when {
                    working -> stringResource(R.string.saving)
                    failed -> stringResource(R.string.error_try_again)
                    profile_picture.isNullOrBlank() -> stringResource(R.string.alias_panel_photo_add)
                    else -> stringResource(R.string.change_photo)
                },
                onClick = {
                    failed = false
                    picker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
                },
                enabled = !locked && !working,
                is_loading = working,
                modifier = Modifier.testTag("alias_avatar_change"),
            )
            if (!profile_picture.isNullOrBlank()) {
                AsterSecondaryButton(
                    label = stringResource(R.string.alias_panel_photo_remove),
                    onClick = {
                        failed = false
                        on_change(null)
                    },
                    enabled = !locked && !working,
                    modifier = Modifier.testTag("alias_avatar_remove"),
                )
            }
        }
        panel_hint_text(
            if (locked) {
                stringResource(R.string.alias_feature_locked)
            } else {
                stringResource(R.string.alias_panel_photo_hint)
            },
        )
    }
}
