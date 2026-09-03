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

package org.astermail.android.ui.mail

const val onboarding_download_url = "https://astermail.org/download"

enum class onboarding_task_destination {
    recovery_email,
    import_mail,
    compose,
    download_page,
    settings,
}

fun onboarding_task_destination_for(key: String): onboarding_task_destination = when (key) {
    "recovery_method" -> onboarding_task_destination.recovery_email
    "import_mail" -> onboarding_task_destination.import_mail
    "first_email" -> onboarding_task_destination.compose
    "install_app" -> onboarding_task_destination.download_page
    else -> onboarding_task_destination.settings
}
