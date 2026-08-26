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

package org.astermail.android.translation

import android.content.Context
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class TranslationDownloadInstrumentedTest {

    private lateinit var context: Context

    @Before
    fun setup() {
        context = InstrumentationRegistry.getInstrumentation().targetContext
        TranslationDownloadPolicy.clear_consent(context)
        TranslationDownloadPolicy.set_wifi_only(context, true)
        TranslationAssets.clear_cache(context)
    }

    @After
    fun teardown() {
        TranslationDownloadPolicy.clear_consent(context)
        TranslationDownloadPolicy.set_wifi_only(context, true)
        TranslationAssets.clear_cache(context)
    }

    @Test
    fun wifi_only_persists_across_reads() {
        assertTrue(TranslationDownloadPolicy.wifi_only(context))

        TranslationDownloadPolicy.set_wifi_only(context, false)

        assertFalse(TranslationDownloadPolicy.wifi_only(context))

        TranslationDownloadPolicy.set_wifi_only(context, true)

        assertTrue(TranslationDownloadPolicy.wifi_only(context))
    }

    @Test
    fun turning_wifi_only_off_never_blocks_a_download() {
        TranslationDownloadPolicy.set_wifi_only(context, false)

        assertFalse(TranslationDownloadPolicy.download_blocked(context))
    }

    @Test
    fun wifi_only_blocks_a_download_exactly_when_the_network_is_metered() {
        TranslationDownloadPolicy.set_wifi_only(context, true)

        assertEquals(
            TranslationDownloadPolicy.metered(context),
            TranslationDownloadPolicy.download_blocked(context),
        )
    }

    @Test
    fun consent_is_withheld_until_it_is_granted_for_the_route() {
        assertFalse(TranslationDownloadPolicy.route_consent_granted(context, "de", "en"))

        TranslationDownloadPolicy.grant_route_consent(context, "de", "en")

        assertTrue(TranslationDownloadPolicy.route_consent_granted(context, "de", "en"))
        assertFalse(TranslationDownloadPolicy.route_consent_granted(context, "de", "fr"))

        TranslationDownloadPolicy.grant_route_consent(context, "en", "fr")

        assertTrue(TranslationDownloadPolicy.route_consent_granted(context, "de", "fr"))
    }

    @Test
    fun clearing_consent_asks_again_for_every_route() {
        TranslationDownloadPolicy.grant_route_consent(context, "de", "fr")
        TranslationDownloadPolicy.clear_consent(context)

        assertFalse(TranslationDownloadPolicy.route_consent_granted(context, "de", "en"))
        assertFalse(TranslationDownloadPolicy.route_consent_granted(context, "en", "fr"))
    }

    @Test
    fun a_model_request_is_refused_while_translation_is_off() {
        assertNull(
            TranslationAssets.serve(
                context,
                TranslationAssets.CONTENT_HOST,
                TranslationAssets.MODEL_PREFIX + "model.deen.bin",
                allow_models = false,
            ),
        )
        assertEquals(0L, TranslationAssets.cached_bytes(context))
    }

    @Test
    fun a_model_request_from_another_host_is_refused() {
        assertNull(
            TranslationAssets.serve(
                context,
                "example.invalid",
                TranslationAssets.MODEL_PREFIX + "model.deen.bin",
                allow_models = true,
            ),
        )
    }

    @Test
    fun a_traversing_model_path_is_refused() {
        assertNull(
            TranslationAssets.serve(
                context,
                TranslationAssets.CONTENT_HOST,
                TranslationAssets.MODEL_PREFIX + "../../etc/hosts",
                allow_models = true,
            ),
        )
        assertEquals(0L, TranslationAssets.cached_bytes(context))
    }

    @Test
    fun clearing_the_cache_reports_no_bytes_on_this_device() {
        TranslationAssets.clear_cache(context)

        assertEquals(0L, TranslationAssets.cached_bytes(context))
    }

    @Test
    fun registry_hashes_are_keyed_by_the_path_the_engine_requests() {
        val registry = """
            {
              "aren": {
                "lex": {
                  "name": "aren/lex.50.50.aren.s2t.bin",
                  "size": 4627200,
                  "expectedSha256Hash": "4b45f14dbea40d368093a13563fc1ca48457ee70c1291c82b206f3baff210081"
                },
                "model": {
                  "name": "aren/model.aren.intgemm.alphas.bin",
                  "size": 31561787,
                  "expectedSha256Hash": "7b7af0282dc5f4d8805b9a298c2fa828967e3f09ca10f1942ebeea0b2cfc12fa"
                }
              }
            }
        """.trimIndent()

        val hashes = TranslationAssets.read_registry_hashes(registry)

        assertEquals(
            "4b45f14dbea40d368093a13563fc1ca48457ee70c1291c82b206f3baff210081",
            hashes?.get("aren/lex.50.50.aren.s2t.bin"),
        )
        assertNull(hashes?.get("lex.50.50.aren.s2t.bin"))
    }

    @Test
    fun a_registry_without_usable_entries_is_refused() {
        assertNull(TranslationAssets.read_registry_hashes("{\"aren\":{\"lex\":{\"name\":\"\"}}}"))
        assertNull(TranslationAssets.read_registry_hashes("not json"))
    }
}
