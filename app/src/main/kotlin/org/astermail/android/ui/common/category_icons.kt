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

import androidx.compose.ui.graphics.vector.ImageVector
import compose.icons.TablerIcons
import compose.icons.tablericons.Bell
import compose.icons.tablericons.Briefcase
import compose.icons.tablericons.CreditCard
import compose.icons.tablericons.Discount
import compose.icons.tablericons.Folder
import compose.icons.tablericons.Gift
import compose.icons.tablericons.Heart
import compose.icons.tablericons.Home
import compose.icons.tablericons.Inbox
import compose.icons.tablericons.MessageDots
import compose.icons.tablericons.Plane
import compose.icons.tablericons.School
import compose.icons.tablericons.ShoppingCart
import compose.icons.tablericons.Speakerphone
import compose.icons.tablericons.Star
import compose.icons.tablericons.Users
import compose.icons.tablericons.Wand
import compose.icons.tablericons.World

val category_icon_catalog: Map<String, ImageVector> = mapOf(
    "inbox" to TablerIcons.Inbox,
    "tag" to TablerIcons.Discount,
    "users" to TablerIcons.Users,
    "bell" to TablerIcons.Bell,
    "chat" to TablerIcons.MessageDots,
    "credit_card" to TablerIcons.CreditCard,
    "plane" to TablerIcons.Plane,
    "shopping_bag" to TablerIcons.ShoppingCart,
    "star" to TablerIcons.Star,
    "heart" to TablerIcons.Heart,
    "briefcase" to TablerIcons.Briefcase,
    "home" to TablerIcons.Home,
    "globe" to TablerIcons.World,
    "academic_cap" to TablerIcons.School,
    "megaphone" to TablerIcons.Speakerphone,
    "gift" to TablerIcons.Gift,
    "folder" to TablerIcons.Folder,
    "sparkles" to TablerIcons.Wand,
)

fun category_icon_for(icon: String): ImageVector =
    category_icon_catalog[icon] ?: TablerIcons.Discount
