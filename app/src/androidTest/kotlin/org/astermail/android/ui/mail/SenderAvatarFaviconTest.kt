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

import android.graphics.Bitmap
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import coil.Coil
import coil.ImageLoader
import java.io.ByteArrayOutputStream
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicInteger
import okhttp3.Interceptor
import okhttp3.OkHttpClient
import okhttp3.mockwebserver.Dispatcher
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import okhttp3.mockwebserver.RecordedRequest
import okio.Buffer
import org.astermail.android.design.AsterTheme
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

private const val FAVICON_PATH_MARKER = "/api/images/v1/favicon/"

private const val OK_DOMAIN = "avatarok.example"
private const val FLAKY_DOMAIN = "avatarflaky.example"
private const val GONE_DOMAIN = "avatargone.example"

@RunWith(AndroidJUnit4::class)
class SenderAvatarFaviconTest {

    @get:Rule
    val compose_rule = createComposeRule()

    private val context = InstrumentationRegistry.getInstrumentation().targetContext

    private lateinit var server: MockWebServer

    private val hits = ConcurrentHashMap<String, AtomicInteger>()

    private fun png_bytes(): ByteArray {
        val bitmap = Bitmap.createBitmap(16, 16, Bitmap.Config.ARGB_8888)
        bitmap.eraseColor(android.graphics.Color.MAGENTA)
        val out = ByteArrayOutputStream()
        bitmap.compress(Bitmap.CompressFormat.PNG, 100, out)
        return out.toByteArray()
    }

    private fun png_response(): MockResponse = MockResponse()
        .setResponseCode(200)
        .setHeader("Content-Type", "image/png")
        .setBody(Buffer().write(png_bytes()))

    private fun count_for(domain: String): Int = hits[domain]?.get() ?: 0

    @Before
    fun setup() {
        server = MockWebServer()
        server.dispatcher = object : Dispatcher() {
            override fun dispatch(request: RecordedRequest): MockResponse {
                val path = request.path.orEmpty()
                val domain = path.substringAfterLast('/')
                val seen = hits.getOrPut(domain) { AtomicInteger(0) }.incrementAndGet()
                return when (domain) {
                    OK_DOMAIN -> png_response()
                    FLAKY_DOMAIN -> if (seen <= 2) MockResponse().setResponseCode(503) else png_response()
                    GONE_DOMAIN -> MockResponse().setResponseCode(404)
                    else -> MockResponse().setResponseCode(404)
                }
            }
        }
        server.start()

        val rewrite = Interceptor { chain ->
            val request = chain.request()
            val url = request.url
            if (!url.encodedPath.contains(FAVICON_PATH_MARKER)) {
                chain.proceed(request)
            } else {
                val redirected = url.newBuilder()
                    .scheme("http")
                    .host(server.hostName)
                    .port(server.port)
                    .build()
                chain.proceed(request.newBuilder().url(redirected).build())
            }
        }

        Coil.setImageLoader(
            ImageLoader.Builder(context)
                .okHttpClient(OkHttpClient.Builder().addInterceptor(rewrite).build())
                .crossfade(false)
                .respectCacheHeaders(false)
                .diskCache(null)
                .build(),
        )
    }

    @After
    fun teardown() {
        Coil.reset()
        server.shutdown()
    }

    @Test
    fun healthy_favicon_replaces_the_letter_avatar() {
        compose_rule.setContent {
            AsterTheme {
                SenderAvatar(email = "news@$OK_DOMAIN", name = "Okbrand")
            }
        }

        compose_rule.waitUntil(timeoutMillis = 15_000) {
            compose_rule.onAllNodesWithTextSafe("O") == 0
        }
        assertEquals(1, count_for(OK_DOMAIN))
    }

    @Test
    fun transient_failure_is_retried_until_the_favicon_loads() {
        compose_rule.setContent {
            AsterTheme {
                SenderAvatar(email = "news@$FLAKY_DOMAIN", name = "Flakybrand")
            }
        }

        compose_rule.waitUntil(timeoutMillis = 30_000) {
            compose_rule.onAllNodesWithTextSafe("F") == 0
        }
        assertEquals(3, count_for(FLAKY_DOMAIN))
    }

    @Test
    fun definitive_miss_is_not_retried_and_keeps_the_letter_avatar() {
        compose_rule.setContent {
            AsterTheme {
                SenderAvatar(email = "news@$GONE_DOMAIN", name = "Gonebrand")
            }
        }

        compose_rule.waitUntil(timeoutMillis = 15_000) { count_for(GONE_DOMAIN) >= 1 }
        Thread.sleep(6_000)
        compose_rule.waitForIdle()

        compose_rule.onNodeWithText("G").assertIsDisplayed()
        assertEquals(1, count_for(GONE_DOMAIN))
    }
}

private fun androidx.compose.ui.test.junit4.ComposeContentTestRule.onAllNodesWithTextSafe(text: String): Int =
    runCatching { onAllNodesWithText(text).fetchSemanticsNodes().size }.getOrDefault(0)
