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

package org.astermail.android.ui.common

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import org.astermail.android.R
import org.astermail.android.design.AsterMaterial
import org.astermail.android.design.components.AsterDialog

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun post_quantum_consent_dialog(
    missing: List<String>,
    downgraded: List<String>,
    on_cancel: () -> Unit,
    on_send_anyway: () -> Unit,
) {
    val colors = AsterMaterial.colors
    val is_downgrade = downgraded.isNotEmpty()
    val missing_list = (if (is_downgrade) downgraded else missing).joinToString(", ")
    AsterDialog(
        on_dismiss = on_cancel,
        title = stringResource(
            if (is_downgrade) R.string.post_quantum_downgrade_title else R.string.post_quantum_unavailable_title,
        ),
        message = stringResource(
            if (is_downgrade) R.string.post_quantum_downgrade_message else R.string.post_quantum_unavailable_message,
            missing_list,
        ),
        footer = {
            FlowRow(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(4.dp, Alignment.End),
            ) {
                TextButton(
                    modifier = Modifier.testTag("post_quantum_cancel"),
                    onClick = on_cancel,
                ) {
                    Text(
                        text = stringResource(R.string.cancel),
                        color = colors.text_secondary,
                        fontSize = 14.sp,
                    )
                }
                TextButton(
                    modifier = Modifier.testTag("post_quantum_send_anyway"),
                    onClick = on_send_anyway,
                ) {
                    Text(
                        text = stringResource(R.string.post_quantum_send_anyway),
                        color = colors.accent_blue,
                        fontSize = 14.sp,
                    )
                }
            }
        },
    )
}
