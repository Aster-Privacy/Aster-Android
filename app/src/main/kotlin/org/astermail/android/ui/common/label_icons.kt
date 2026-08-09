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
import compose.icons.tablericons.AlertTriangle
import compose.icons.tablericons.At
import compose.icons.tablericons.Archive
import compose.icons.tablericons.Bell
import compose.icons.tablericons.Bolt
import compose.icons.tablericons.Bookmark
import compose.icons.tablericons.Building
import compose.icons.tablericons.CircleCheck
import compose.icons.tablericons.Clock
import compose.icons.tablericons.Code
import compose.icons.tablericons.CurrencyDollar
import compose.icons.tablericons.EyeOff
import compose.icons.tablericons.FileText
import compose.icons.tablericons.Flag
import compose.icons.tablericons.Flame
import compose.icons.tablericons.Folder
import compose.icons.tablericons.Heart
import compose.icons.tablericons.Inbox
import compose.icons.tablericons.InfoCircle
import compose.icons.tablericons.Lock
import compose.icons.tablericons.Mail
import compose.icons.tablericons.MessageDots
import compose.icons.tablericons.Send
import compose.icons.tablericons.ShoppingCart
import compose.icons.tablericons.Shield
import compose.icons.tablericons.Star
import compose.icons.tablericons.Tag
import compose.icons.tablericons.Trash
import compose.icons.tablericons.User
import compose.icons.tablericons.Wand
import compose.icons.tablericons.World

val label_icon_catalog: List<Pair<String, ImageVector>> = listOf(
    "clock" to TablerIcons.Clock,
    "archive" to TablerIcons.Archive,
    "trash" to TablerIcons.Trash,
    "send" to TablerIcons.Send,
    "draft" to TablerIcons.FileText,
    "star" to TablerIcons.Star,
    "flag" to TablerIcons.Flag,
    "bolt" to TablerIcons.Bolt,
    "shield" to TablerIcons.Shield,
    "warning" to TablerIcons.AlertTriangle,
    "check" to TablerIcons.CircleCheck,
    "tag" to TablerIcons.Tag,
    "folder" to TablerIcons.Folder,
    "envelope" to TablerIcons.Mail,
    "lock" to TablerIcons.Lock,
    "bell" to TablerIcons.Bell,
    "sparkles" to TablerIcons.Wand,
    "fire" to TablerIcons.Flame,
    "heart" to TablerIcons.Heart,
    "bookmark" to TablerIcons.Bookmark,
    "chat" to TablerIcons.MessageDots,
    "document" to TablerIcons.FileText,
    "currency" to TablerIcons.CurrencyDollar,
    "cart" to TablerIcons.ShoppingCart,
    "code" to TablerIcons.Code,
    "user" to TablerIcons.User,
    "building" to TablerIcons.Building,
    "globe" to TablerIcons.World,
    "info" to TablerIcons.InfoCircle,
    "eye-slash" to TablerIcons.EyeOff,
    "at" to TablerIcons.At,
)

private val label_icon_index: Map<String, ImageVector> =
    label_icon_catalog.toMap() + mapOf("inbox" to TablerIcons.Inbox)

fun label_icon_or_null(name: String?): ImageVector? =
    name?.trim()?.takeIf { it.isNotEmpty() }?.let { label_icon_index[it] }
