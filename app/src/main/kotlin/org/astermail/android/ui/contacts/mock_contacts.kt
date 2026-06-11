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

package org.astermail.android.ui.contacts

data class Contact(
    val id: String,
    val name: String,
    val email: String,
    val phone: String = "",
    val company: String = "",
    val title: String = "",
    val work_email: String = "",
    val work_phone: String = "",
    val birthday: String = "",
    val address: String = "",
    val city: String = "",
    val region: String = "",
    val postal_code: String = "",
    val country: String = "",
    val website: String = "",
    val twitter: String = "",
    val linkedin: String = "",
    val notes: String = "",
    val is_favorite: Boolean = false,
)

val mock_contacts: List<Contact> = listOf(
    Contact(
        id = "c_01",
        name = "Amelia Chen",
        email = "amelia.chen@aster.cx",
        phone = "+1 415 555 0142",
        company = "Aster Labs",
        title = "Product Designer",
        city = "San Francisco",
        country = "USA",
        is_favorite = true,
        notes = "Met at the Privacy Summit 2025.",
    ),
    Contact(
        id = "c_02",
        name = "Benjamin Novak",
        email = "ben.novak@astermail.org",
        phone = "+420 602 444 017",
        company = "Novak & Partners",
        title = "Tax Advisor",
        city = "Prague",
        country = "Czechia",
    ),
    Contact(
        id = "c_03",
        name = "Clara Hoffmann",
        email = "clara@hoffmann.de",
        phone = "+49 30 555 8812",
        company = "Kreative Werkstatt",
        title = "Illustrator",
        city = "Berlin",
        country = "Germany",
        is_favorite = true,
    ),
    Contact(
        id = "c_04",
        name = "Daniel O'Connor",
        email = "daniel.oconnor@astermail.org",
        phone = "+353 1 555 2024",
        company = "Emerald Bicycles",
        title = "Founder",
        city = "Dublin",
        country = "Ireland",
    ),
    Contact(
        id = "c_05",
        name = "Elena Rossi",
        email = "elena@rossi.it",
        phone = "+39 02 555 7701",
        city = "Milan",
        country = "Italy",
    ),
    Contact(
        id = "c_06",
        name = "Farid Ahmadi",
        email = "farid.ahmadi@astermail.org",
        phone = "+1 647 555 0188",
        company = "Northwind Analytics",
        title = "Data Engineer",
        city = "Toronto",
        country = "Canada",
    ),
    Contact(
        id = "c_07",
        name = "Grace Kim",
        email = "grace.kim@aster.cx",
        phone = "+82 2 555 0011",
        company = "Aster Labs",
        title = "Engineering Manager",
        city = "Seoul",
        country = "South Korea",
        is_favorite = true,
    ),
    Contact(
        id = "c_08",
        name = "Hiroshi Tanaka",
        email = "h.tanaka@astermail.org",
        phone = "+81 3 5555 7788",
        company = "Tanaka Studio",
        title = "Photographer",
        city = "Tokyo",
        country = "Japan",
    ),
    Contact(
        id = "c_09",
        name = "Ingrid Larsen",
        email = "ingrid.larsen@astermail.org",
        phone = "+47 22 555 113",
        company = "Nordic Timber Co",
        title = "Operations Lead",
        city = "Oslo",
        country = "Norway",
    ),
    Contact(
        id = "c_10",
        name = "Jordan Reed",
        email = "jordan.reed@aster.cx",
        phone = "+1 503 555 0199",
        company = "Cascade Outdoors",
        title = "Brand Manager",
        city = "Portland",
        country = "USA",
    ),
    Contact(
        id = "c_11",
        name = "Kaveh Azizi",
        email = "kaveh@azizi.dev",
        phone = "+49 40 555 7122",
        company = "Azizi Consulting",
        title = "Security Consultant",
        city = "Hamburg",
        country = "Germany",
        is_favorite = true,
    ),
    Contact(
        id = "c_12",
        name = "Lucia Mendes",
        email = "lucia.mendes@astermail.org",
        phone = "+55 21 555 0042",
        city = "Rio de Janeiro",
        country = "Brazil",
    ),
    Contact(
        id = "c_13",
        name = "Marcus Okafor",
        email = "marcus.okafor@astermail.org",
        phone = "+44 20 7946 0991",
        company = "Okafor Legal",
        title = "Partner",
        city = "London",
        country = "UK",
    ),
    Contact(
        id = "c_14",
        name = "Nadia Volkov",
        email = "nadia@volkov.studio",
        phone = "+33 1 40 55 99 02",
        company = "Volkov Studio",
        title = "Art Director",
        city = "Paris",
        country = "France",
    ),
    Contact(
        id = "c_15",
        name = "Oliver Brandt",
        email = "oliver.brandt@aster.cx",
        phone = "+61 2 555 4401",
        company = "Aster Labs",
        title = "Backend Engineer",
        city = "Sydney",
        country = "Australia",
    ),
    Contact(
        id = "c_16",
        name = "Priya Shah",
        email = "priya.shah@astermail.org",
        phone = "+91 22 5555 3321",
        company = "Shah Architects",
        title = "Principal",
        city = "Mumbai",
        country = "India",
        is_favorite = true,
    ),
    Contact(
        id = "c_17",
        name = "Quinn Walsh",
        email = "quinn.walsh@astermail.org",
        phone = "+1 212 555 8830",
        city = "New York",
        country = "USA",
    ),
    Contact(
        id = "c_18",
        name = "Rafael Ortega",
        email = "rafael.ortega@astermail.org",
        phone = "+34 91 555 4009",
        company = "Ortega Fincas",
        title = "Broker",
        city = "Madrid",
        country = "Spain",
    ),
)
