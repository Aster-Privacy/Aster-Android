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

package org.astermail.android.ui.compose

internal fun compose_has_unsaved_changes(
    to_chips: List<String>,
    initial_to_chips: List<String>,
    cc_chips: List<String>,
    initial_cc_chips: List<String>,
    bcc_chips: List<String>,
    initial_bcc_chips: List<String>,
    subject: String,
    initial_subject: String,
    body: String,
    initial_body: String,
    to_input: String,
    cc_input: String,
    bcc_input: String,
    attachments_changed: Boolean,
    has_inline_images: Boolean,
): Boolean = to_chips != initial_to_chips ||
    cc_chips != initial_cc_chips ||
    bcc_chips != initial_bcc_chips ||
    subject != initial_subject ||
    body != initial_body ||
    to_input.isNotBlank() ||
    cc_input.isNotBlank() ||
    bcc_input.isNotBlank() ||
    attachments_changed ||
    has_inline_images
