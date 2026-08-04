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

package org.astermail.android.settings

import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class SecurityStringsWebParityTest {

    private val locales = listOf(
        "values",
        "values-ar",
        "values-de",
        "values-es",
        "values-fr",
        "values-it",
        "values-ja",
        "values-ko",
        "values-nl",
        "values-pl",
        "values-pt",
        "values-ru",
        "values-tr",
        "values-zh-rCN",
    )

    private val web_wording = mapOf(
        "tracking_protection_enabled" to "Enable Tracking Protection",
        "tracking_protection_enabled_subtitle" to "Block tracking elements embedded in emails to protect your privacy",
        "block_tracking_pixels" to "Block Spy Pixels",
        "block_tracking_pixels_subtitle_security" to "Block invisible tracking pixels that notify senders when you open an email",
        "block_tracking_links" to "Clean Tracking Links",
        "block_tracking_links_subtitle" to "Remove tracking parameters from links in emails",
        "block_remote_images" to "Block Remote Images",
        "block_remote_images_subtitle_security" to "Prevent emails from loading images hosted on external servers",
        "block_remote_fonts" to "Block Remote Fonts",
        "block_remote_fonts_subtitle" to "Prevent emails from loading fonts from external servers",
        "block_remote_css" to "Block Remote CSS",
        "block_remote_css_subtitle" to "Prevent emails from loading stylesheets from external servers",
        "block_html_rendering" to "Block HTML Rendering",
        "block_html_rendering_subtitle" to "Show incoming emails as plain text to prevent tracking, layout spoofing, and visual phishing",
        "strip_exif" to "Strip Image Metadata",
        "strip_exif_subtitle" to "Remove EXIF and other metadata from images before sending to protect your location and device information",
        "warn_suspicious_links" to "External Link Warnings",
        "warn_suspicious_links_subtitle" to "Show warning before opening external links in emails",
        "section_tracking_protection" to "Tracking Protection",
        "section_images" to "Images",
        "section_html_content" to "HTML Content",
        "section_external_links" to "External Link Warnings",
        "remote_image_loading_title" to "Remote Image Loading",
        "remote_image_loading_subtitle" to "Control when remote images are loaded in emails",
        "remote_images_never" to "Never load",
        "remote_images_ask" to "Ask before loading",
        "remote_images_always" to "Always load",
    )

    private fun strings_for(locale: String): Map<String, String> {
        val file = File("src/main/res/$locale/strings.xml")
        assertTrue("missing ${file.absolutePath}", file.exists())
        val body = file.readText()
        return Regex("<string name=\"([^\"]+)\">(.*?)</string>", RegexOption.DOT_MATCHES_ALL)
            .findAll(body)
            .associate { it.groupValues[1] to it.groupValues[2] }
    }

    private fun unescape(value: String): String = value
        .replace("\\'", "'")
        .replace("&quot;", "\"")
        .replace("&lt;", "<")
        .replace("&gt;", ">")
        .replace("&amp;", "&")

    @Test
    fun english_security_options_match_the_web_wording_exactly() {
        val strings = strings_for("values")
        web_wording.forEach { (key, expected) ->
            val actual = strings[key]
            assertEquals("string $key must match the web settings wording", expected, actual?.let(::unescape))
        }
    }

    @Test
    fun every_locale_defines_the_security_option_strings() {
        locales.forEach { locale ->
            val strings = strings_for(locale)
            web_wording.keys.forEach { key ->
                assertTrue("$locale is missing $key", strings[key]?.isNotBlank() == true)
            }
        }
    }
}
