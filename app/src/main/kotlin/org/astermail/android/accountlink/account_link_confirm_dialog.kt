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

package org.astermail.android.accountlink

import android.app.Activity
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import org.astermail.android.R
import org.astermail.android.design.AsterMaterial
import org.astermail.android.design.components.AsterDialog

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun account_link_confirm_dialog(
    request_id: String,
    on_done: () -> Unit,
    approvals: AccountLinkApprovals = AccountLinkApprovals.shared,
) {
    val prompt = remember(request_id) { approvals.prompt_for(request_id) }
    if (prompt == null) {
        LaunchedEffect(request_id) { on_done() }
        return
    }
    val activity = LocalContext.current as? Activity
    val colors = AsterMaterial.colors
    val decline = {
        approvals.decline(request_id)
        on_done()
    }
    AsterDialog(
        on_dismiss = decline,
        title = stringResource(R.string.account_link_confirm_title),
        message = stringResource(R.string.account_link_confirm_message, prompt.caller_label, prompt.account_email),
        footer = {
            FlowRow(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(4.dp, Alignment.End),
            ) {
                TextButton(
                    modifier = Modifier.testTag("account_link_cancel"),
                    onClick = decline,
                ) {
                    Text(
                        text = stringResource(R.string.cancel),
                        color = colors.text_secondary,
                        fontSize = 14.sp,
                    )
                }
                TextButton(
                    modifier = Modifier.testTag("account_link_confirm"),
                    onClick = {
                        val approved = approvals.approve(request_id)
                        on_done()
                        if (approved) activity?.moveTaskToBack(true)
                    },
                ) {
                    Text(
                        text = stringResource(R.string.confirm),
                        color = colors.accent_blue,
                        fontSize = 14.sp,
                    )
                }
            }
        },
    )
}
