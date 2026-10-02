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

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.compositeOver
import org.astermail.android.design.AsterColorThemes
import org.astermail.android.design.ColorThemeId
import org.astermail.android.design.MaterialThemeGenerator
import org.astermail.android.design.contrast_ratio
import org.astermail.android.design.dark_semantic_colors
import org.astermail.android.design.light_semantic_colors
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class EmailDarkModeTest {

    @Test
    fun dark_authored_ink_is_detected_across_color_syntaxes() {
        assertTrue(reads_too_dark_on_dark("#111111"))
        assertTrue(reads_too_dark_on_dark("#333"))
        assertTrue(reads_too_dark_on_dark("rgb(20, 20, 20)"))
        assertTrue(reads_too_dark_on_dark("hsl(0, 0%, 10%)"))
        assertTrue(reads_too_dark_on_dark("black"))
    }

    @Test
    fun readable_ink_is_left_alone() {
        assertFalse(reads_too_dark_on_dark("#ffffff"))
        assertFalse(reads_too_dark_on_dark("rgb(230, 230, 230)"))
        assertFalse(reads_too_dark_on_dark("not-a-color"))
        assertFalse(reads_too_dark_on_dark(""))
    }

    @Test
    fun inline_dark_text_is_lightened() {
        val out = lighten_dark_email_text(
            "<p style=\"color:#222222;font-size:14px\">Hello</p>",
        )
        assertTrue(out.contains(FORCED_DARK_INK))
        assertFalse(out.contains("#222222"))
        assertTrue(out.contains("font-size:14px"))
    }

    @Test
    fun style_blocks_and_font_tags_are_lightened() {
        val out = lighten_dark_email_text(
            "<style>.t{color:#1a1a1a}</style><font color=\"#000000\">Hi</font>",
        )
        assertFalse(out.contains("#1a1a1a"))
        assertFalse(out.contains("#000000"))
        assertEquals(2, out.split(FORCED_DARK_INK).size - 1)
    }

    @Test
    fun light_text_over_an_image_background_is_preserved() {
        val out = lighten_dark_email_text(
            "<div style=\"background-image:url(hero.png);color:#111111\">Hero</div>",
        )
        assertTrue(out.contains("#111111"))
    }

    @Test
    fun forced_css_strips_author_backgrounds_but_spares_image_backgrounds() {
        val css = forced_dark_mode_css("#8ab4f8", "#4b5563", "#9ca3af")
        assertTrue(css.contains("background-color:transparent!important"))
        assertTrue(css.contains("td:not([style*=\"background-image\" i])"))
        assertTrue(css.contains("table:not([style*=\"background-image\" i])"))
        assertTrue(css, css.contains("color:#8ab4f8!important"))
        assertTrue(css.contains("html,body{background-color:transparent!important"))
    }

    private val newsletter =
        "<table width=\"600\" bgcolor=\"#ffffff\"><tr><td style=\"background-color:#ffffff\">" +
            "<h1 style=\"color:#111111\">Monthly update</h1>" +
            "<p style=\"color:#333333\">Here is what shipped.</p>" +
            "</td></tr></table>"

    private fun render(body: String, forced: Boolean, theme_dark: Boolean = true): String =
        build_email_html(
            body = body,
            is_dark = theme_dark,
            fg_hex = if (theme_dark) "#E8E8E8" else "#111827",
            link_hex = "#8ab4f8",
            forwarded_label = "Forwarded message",
            image_failed_label = "Image unavailable",
            force_dark_emails = forced,
            dyslexia_font = false,
            translate_mode = "off",
        )

    @Test
    fun forcing_dark_mode_neutralizes_a_designed_newsletter_page() {
        val html = render(newsletter, forced = true)
        assertTrue(
            "forced dark must strip author backgrounds: " + html,
            html.contains("background-color:transparent!important;background-image:none!important"),
        )
        assertTrue(
            "forced dark must keep light body text: " + html,
            html.contains("html,body{background-color:transparent!important;color:" + FORCED_DARK_INK),
        )
    }

    @Test
    fun forcing_dark_mode_lightens_authored_dark_text() {
        val html = render(newsletter, forced = true)
        assertFalse("dark heading ink must be lightened: " + html, html.contains("color:#111111"))
        assertFalse("dark paragraph ink must be lightened: " + html, html.contains("color:#333333"))
    }

    @Test
    fun forcing_dark_mode_leaves_image_backed_sections_alone() {
        val html = render(
            "<div style=\"background-image:url(hero.png);color:#111111\">Hero</div>",
            forced = true,
        )
        assertTrue("text over an image background must keep its color: " + html, html.contains("color:#111111"))
    }

    @Test
    fun leaving_the_toggle_off_keeps_the_white_page_behavior() {
        val html = render(newsletter, forced = false)
        assertFalse(
            "the opt-in toggle must not fire on its own: " + html,
            html.contains("background-image:none!important"),
        )
        assertTrue(
            "the white page path must still apply: " + html,
            html.contains("background-color:#ffffff!important"),
        )
    }

    @Test
    fun brand_colored_blocks_keep_their_background() {
        val out = lighten_dark_email_text(
            "<table><tr><td bgcolor=\"#1a73e8\">" +
                "<a href=\"#\" style=\"color:#ffffff\">Read more</a></td></tr></table>",
        )
        assertTrue(out, out.contains(KEEP_BACKGROUND_ATTRIBUTE))
    }

    @Test
    fun page_surfaces_are_not_spared() {
        val out = lighten_dark_email_text(
            "<table><tr><td bgcolor=\"#ffffff\" style=\"color:#111111\">Body</td></tr></table>",
        )
        assertFalse(out, out.contains(KEEP_BACKGROUND_ATTRIBUTE))
        assertTrue(out, out.contains(FORCED_DARK_INK))
    }

    @Test
    fun forcing_dark_mode_keeps_a_call_to_action_button_filled() {
        val html = render(
            "<table bgcolor=\"#ffffff\"><tr><td bgcolor=\"#1a73e8\">" +
                "<a href=\"https://example.com\" style=\"color:#ffffff\">Read more</a></td></tr></table>",
            forced = true,
        )
        assertTrue("the button cell must survive: " + html, html.contains(KEEP_BACKGROUND_ATTRIBUTE))
    }

    @Test
    fun the_force_preference_applies_only_while_the_app_theme_is_dark() {
        assertTrue(forces_dark_emails(preference = true, theme_dark = true))
        assertFalse(forces_dark_emails(preference = true, theme_dark = false))
        assertFalse(forces_dark_emails(preference = false, theme_dark = true))
        assertFalse(forces_dark_emails(preference = false, theme_dark = false))
    }

    @Test
    fun the_light_theme_keeps_the_original_styling_with_the_preference_on() {
        val html = render(
            newsletter,
            forced = forces_dark_emails(preference = true, theme_dark = false),
            theme_dark = false,
        )
        assertFalse("light theme must not force dark: " + html, html.contains("data-dark-force"))
        assertFalse("light theme must not strip backgrounds: " + html, html.contains("background-image:none!important"))
        assertFalse("light theme must not switch the color scheme: " + html, html.contains("color-scheme:dark"))
        assertTrue("authored ink must survive in the light theme: " + html, html.contains("color:#111111"))
    }

    @Test
    fun switching_back_to_the_dark_theme_forces_dark_again() {
        val light = render(newsletter, forced = forces_dark_emails(true, theme_dark = false), theme_dark = false)
        val dark = render(newsletter, forced = forces_dark_emails(true, theme_dark = true), theme_dark = true)
        assertFalse(light.contains("data-dark-force"))
        assertTrue(dark.contains("data-dark-force=\"1\""))
        assertTrue(dark.contains("html,body{background-color:transparent!important;color:" + FORCED_DARK_INK))
    }

    @Test
    fun every_coloured_palette_theme_counts_as_dark_so_forced_dark_applies() {
        val palette_themes = ColorThemeId.entries.filter { AsterColorThemes.palette_for(it) != null }
        assertEquals(15, palette_themes.size)
        for (id in palette_themes) {
            assertTrue("$id is a dark-only theme", AsterColorThemes.is_dark_only(id))
            val colors = AsterColorThemes.semantic_colors_for(true, AsterColorThemes.palette_for(id))
            assertTrue("$id must count as dark for emails", email_theme_is_dark(colors))
            assertTrue("$id must force dark emails", forces_dark_emails(true, email_theme_is_dark(colors)))
        }
    }

    @Test
    fun the_default_theme_follows_the_light_and_dark_setting() {
        assertFalse(email_theme_is_dark(AsterColorThemes.semantic_colors_for(false, null)))
        assertTrue(email_theme_is_dark(AsterColorThemes.semantic_colors_for(true, null)))
        assertFalse(forces_dark_emails(true, email_theme_is_dark(light_semantic_colors)))
        assertTrue(forces_dark_emails(true, email_theme_is_dark(dark_semantic_colors)))
    }

    @Test
    fun a_custom_seed_theme_follows_the_light_and_dark_setting() {
        for (seed in listOf("#3b82f6", "#a855f7", "#f59e0b", "#000000", "#ffffff")) {
            fun colors(dark: Boolean) = AsterColorThemes.semantic_colors_for(
                dark,
                MaterialThemeGenerator.to_palette(
                    MaterialThemeGenerator.compute_custom_theme_vars(seed, is_dark = dark, overrides = emptyMap()),
                ),
            )
            assertFalse("custom $seed light must not force dark", email_theme_is_dark(colors(false)))
            assertTrue("custom $seed dark must force dark", email_theme_is_dark(colors(true)))
        }
    }

    @Test
    fun glass_themes_count_as_dark_so_forced_dark_applies() {
        val tint = Color(0xFF1E2A3A)
        for (base in listOf(dark_semantic_colors) + ColorThemeId.entries.mapNotNull { id ->
            AsterColorThemes.palette_for(id)?.let { AsterColorThemes.semantic_colors_for(true, it) }
        }) {
            val glass = base.copy(
                bg_primary = tint.copy(alpha = 0.4f),
                thread_content_bg = tint.copy(alpha = 0.4f),
                is_glass = true,
                glass_opacity = 0.4f,
            )
            assertTrue(email_theme_is_dark(glass))
            assertTrue(forces_dark_emails(true, email_theme_is_dark(glass)))
        }
    }

    private fun glass_colors(opacity: Float) = dark_semantic_colors.copy(
        bg_primary = Color(0xFF1E2C45),
        bg_secondary = Color(0xFF243249),
        bg_card = Color(0xFF26344B),
        thread_content_bg = Color(0xFF1E2C45),
        is_glass = true,
        glass_opacity = opacity,
    )

    private fun body_surface_over_bright_backdrop(colors: org.astermail.android.design.AsterSemanticColors, card: Color): Color {
        val veiled = colors.bg_primary.copy(alpha = 0.34f).compositeOver(Color.White)
        val carded = card.compositeOver(veiled)
        return email_glass_backing(colors, white_page = false).compositeOver(carded)
    }

    @Test
    fun translucent_glass_gets_a_backing_so_light_email_ink_reads_over_a_bright_backdrop() {
        for (opacity in listOf(0.2f, 0.38f, 0.62f)) {
            val colors = glass_colors(opacity)
            val body = body_surface_over_bright_backdrop(colors, colors.bg_card.copy(alpha = opacity))
            val quote = body_surface_over_bright_backdrop(colors, colors.bg_secondary.copy(alpha = opacity * 0.82f))
            val ink = Color(0xFFE5E5E5)
            assertTrue("body at $opacity", contrast_ratio(ink, body) >= 4.5)
            assertTrue("quote at $opacity", contrast_ratio(ink, quote) >= 4.5)
            assertTrue("theme ink at $opacity", contrast_ratio(colors.text_primary, body) >= 4.5)
        }
    }

    @Test
    fun solid_themes_and_white_pages_keep_a_transparent_email_surface() {
        assertEquals(Color.Transparent, email_glass_backing(light_semantic_colors, white_page = false))
        assertEquals(Color.Transparent, email_glass_backing(dark_semantic_colors, white_page = false))
        val purple = AsterColorThemes.semantic_colors_for(true, AsterColorThemes.palette_for(ColorThemeId.purple))
        assertEquals(Color.Transparent, email_glass_backing(purple, white_page = false))
        assertEquals(Color.Transparent, email_glass_backing(glass_colors(1f), white_page = false))
        assertEquals(Color.Transparent, email_glass_backing(glass_colors(0.9f), white_page = false))
        assertEquals(Color.Transparent, email_glass_backing(glass_colors(0.2f), white_page = true))
    }
}
