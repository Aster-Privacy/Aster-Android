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

import androidx.annotation.StringRes
import androidx.compose.ui.graphics.vector.ImageVector
import compose.icons.TablerIcons
import compose.icons.tablericons.AlertTriangle
import compose.icons.tablericons.Archive
import compose.icons.tablericons.At
import compose.icons.tablericons.Ban
import compose.icons.tablericons.Bell
import compose.icons.tablericons.Bolt
import compose.icons.tablericons.Book
import compose.icons.tablericons.Bookmark
import compose.icons.tablericons.Briefcase
import compose.icons.tablericons.Building
import compose.icons.tablericons.BuildingBank
import compose.icons.tablericons.Bulb
import compose.icons.tablericons.Calculator
import compose.icons.tablericons.Calendar
import compose.icons.tablericons.Camera
import compose.icons.tablericons.Cash
import compose.icons.tablericons.ChartBar
import compose.icons.tablericons.CircleCheck
import compose.icons.tablericons.ClipboardList
import compose.icons.tablericons.Clock
import compose.icons.tablericons.Cloud
import compose.icons.tablericons.Code
import compose.icons.tablericons.CreditCard
import compose.icons.tablericons.CurrencyBitcoin
import compose.icons.tablericons.CurrencyDollar
import compose.icons.tablericons.Edit
import compose.icons.tablericons.EyeOff
import compose.icons.tablericons.FileText
import compose.icons.tablericons.Flag
import compose.icons.tablericons.Flame
import compose.icons.tablericons.Flask
import compose.icons.tablericons.Folder
import compose.icons.tablericons.Gift
import compose.icons.tablericons.Heart
import compose.icons.tablericons.Home
import compose.icons.tablericons.Inbox
import compose.icons.tablericons.InfoCircle
import compose.icons.tablericons.Key
import compose.icons.tablericons.Language
import compose.icons.tablericons.Link
import compose.icons.tablericons.Lock
import compose.icons.tablericons.Mail
import compose.icons.tablericons.MapPin
import compose.icons.tablericons.MessageDots
import compose.icons.tablericons.Moon
import compose.icons.tablericons.Music
import compose.icons.tablericons.News
import compose.icons.tablericons.Package
import compose.icons.tablericons.Pencil
import compose.icons.tablericons.Phone
import compose.icons.tablericons.Presentation
import compose.icons.tablericons.Receipt
import compose.icons.tablericons.School
import compose.icons.tablericons.Send
import compose.icons.tablericons.Shield
import compose.icons.tablericons.ShoppingCart
import compose.icons.tablericons.Star
import compose.icons.tablericons.Sun
import compose.icons.tablericons.Tag
import compose.icons.tablericons.Ticket
import compose.icons.tablericons.Tools
import compose.icons.tablericons.Trash
import compose.icons.tablericons.Trophy
import compose.icons.tablericons.Truck
import compose.icons.tablericons.User
import compose.icons.tablericons.Users
import compose.icons.tablericons.Wallet
import compose.icons.tablericons.Wand
import compose.icons.tablericons.World
import org.astermail.android.R

data class LabelIconGroup(
    @StringRes val title: Int,
    val icons: List<String>,
)

val label_icon_groups: List<LabelIconGroup> = listOf(
    LabelIconGroup(
        title = R.string.label_icon_group_essentials,
        icons = listOf(
            "tag", "folder", "star", "bookmark", "flag", "check", "bell", "heart", "sparkles",
            "fire", "bolt", "clock", "info", "warning",
        ),
    ),
    LabelIconGroup(
        title = R.string.label_icon_group_mail,
        icons = listOf(
            "envelope", "at", "chat", "send", "draft", "document", "archive", "trash", "shield",
            "lock", "eye-slash",
        ),
    ),
    LabelIconGroup(
        title = R.string.label_icon_group_money,
        icons = listOf(
            "currency", "money", "bank", "card", "wallet", "receipt", "chart", "cart", "gift",
            "ticket", "crypto",
        ),
    ),
    LabelIconGroup(
        title = R.string.label_icon_group_work,
        icons = listOf(
            "briefcase", "building", "user", "users", "calendar", "clipboard", "presentation",
            "trophy", "code", "key", "link", "package",
        ),
    ),
    LabelIconGroup(
        title = R.string.label_icon_group_school,
        icons = listOf(
            "graduation", "book", "pencil", "calculator", "beaker", "language",
        ),
    ),
    LabelIconGroup(
        title = R.string.label_icon_group_everyday,
        icons = listOf(
            "home", "truck", "map-pin", "camera", "music", "cloud", "sun", "moon", "globe",
            "phone", "news", "bulb", "tools", "ban",
        ),
    ),
)

val label_icon_catalog: List<Pair<String, ImageVector>> = listOf(
    "tag" to TablerIcons.Tag,
    "folder" to TablerIcons.Folder,
    "star" to TablerIcons.Star,
    "bookmark" to TablerIcons.Bookmark,
    "flag" to TablerIcons.Flag,
    "check" to TablerIcons.CircleCheck,
    "bell" to TablerIcons.Bell,
    "heart" to TablerIcons.Heart,
    "sparkles" to TablerIcons.Wand,
    "fire" to TablerIcons.Flame,
    "bolt" to TablerIcons.Bolt,
    "clock" to TablerIcons.Clock,
    "info" to TablerIcons.InfoCircle,
    "warning" to TablerIcons.AlertTriangle,
    "envelope" to TablerIcons.Mail,
    "at" to TablerIcons.At,
    "chat" to TablerIcons.MessageDots,
    "send" to TablerIcons.Send,
    "draft" to TablerIcons.Edit,
    "document" to TablerIcons.FileText,
    "archive" to TablerIcons.Archive,
    "trash" to TablerIcons.Trash,
    "shield" to TablerIcons.Shield,
    "lock" to TablerIcons.Lock,
    "eye-slash" to TablerIcons.EyeOff,
    "currency" to TablerIcons.CurrencyDollar,
    "money" to TablerIcons.Cash,
    "bank" to TablerIcons.BuildingBank,
    "card" to TablerIcons.CreditCard,
    "wallet" to TablerIcons.Wallet,
    "receipt" to TablerIcons.Receipt,
    "chart" to TablerIcons.ChartBar,
    "cart" to TablerIcons.ShoppingCart,
    "gift" to TablerIcons.Gift,
    "ticket" to TablerIcons.Ticket,
    "crypto" to TablerIcons.CurrencyBitcoin,
    "briefcase" to TablerIcons.Briefcase,
    "building" to TablerIcons.Building,
    "user" to TablerIcons.User,
    "users" to TablerIcons.Users,
    "calendar" to TablerIcons.Calendar,
    "clipboard" to TablerIcons.ClipboardList,
    "presentation" to TablerIcons.Presentation,
    "trophy" to TablerIcons.Trophy,
    "code" to TablerIcons.Code,
    "key" to TablerIcons.Key,
    "link" to TablerIcons.Link,
    "package" to TablerIcons.Package,
    "graduation" to TablerIcons.School,
    "book" to TablerIcons.Book,
    "pencil" to TablerIcons.Pencil,
    "calculator" to TablerIcons.Calculator,
    "beaker" to TablerIcons.Flask,
    "language" to TablerIcons.Language,
    "home" to TablerIcons.Home,
    "truck" to TablerIcons.Truck,
    "map-pin" to TablerIcons.MapPin,
    "camera" to TablerIcons.Camera,
    "music" to TablerIcons.Music,
    "cloud" to TablerIcons.Cloud,
    "sun" to TablerIcons.Sun,
    "moon" to TablerIcons.Moon,
    "globe" to TablerIcons.World,
    "phone" to TablerIcons.Phone,
    "news" to TablerIcons.News,
    "bulb" to TablerIcons.Bulb,
    "tools" to TablerIcons.Tools,
    "ban" to TablerIcons.Ban,
)

private val label_icon_index: Map<String, ImageVector> =
    label_icon_catalog.toMap() + mapOf("inbox" to TablerIcons.Inbox)

fun label_icon_or_null(name: String?): ImageVector? =
    name?.trim()?.takeIf { it.isNotEmpty() }?.let { label_icon_index[it] }
