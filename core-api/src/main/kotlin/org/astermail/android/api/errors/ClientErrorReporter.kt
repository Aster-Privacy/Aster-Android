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

package org.astermail.android.api.errors

import java.io.OutputStreamWriter
import java.net.HttpURLConnection
import java.net.URL
import java.util.concurrent.atomic.AtomicInteger
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

private const val SURFACE = "mail_android"
private const val PLATFORM = "android"
private const val MAX_REPORTS_PER_PROCESS = 40
private const val DEDUPE_WINDOW_MS = 60_000L
private const val REPORT_PATH = "/api/core/v1/client-errors"

private val SLUG = Regex("^[a-z0-9_.-]{1,64}$")
private val ID_SEGMENT = Regex("^[0-9]+$|^[0-9a-fA-F]{8,}$|-")
private val VERSION_SEGMENT = Regex("^v[0-9]+$", RegexOption.IGNORE_CASE)

fun feature_of_path(path: String): String {
    return runCatching {
        val slug = path
            .substringBefore('?')
            .substringBefore('#')
            .split('/')
            .filter { it.isNotEmpty() }
            .filterNot { it.equals("api", ignoreCase = true) }
            .filterNot { VERSION_SEGMENT.matches(it) }
            .filterNot { ID_SEGMENT.containsMatchIn(it) }
            .map { it.lowercase().filter { c -> c.isLetterOrDigit() || c == '_' || c == '.' || c == '-' } }
            .filter { it.isNotEmpty() }
            .take(3)
            .joinToString("_")
            .take(64)
        if (SLUG.matches(slug)) slug else "request"
    }.getOrDefault("request")
}

fun http_error_code(prefix: String, status: Int): String {
    val safe = if (SLUG.matches(prefix)) prefix else "request"
    return "${safe}_http_$status".take(64)
}

object ClientErrorReporter {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val sent = AtomicInteger(0)
    private val last_seen = java.util.concurrent.ConcurrentHashMap<String, Long>()

    @Volatile
    private var base_url: String? = null

    @Volatile
    private var release: String = "unknown"

    fun configure(base_url: String, release: String) {
        this.base_url = base_url.trimEnd('/')
        this.release = if (SLUG.matches(release)) release else "unknown"
    }

    fun is_report_path(path: String): Boolean = path.endsWith(REPORT_PATH)

    fun report(
        feature: String,
        error_code: String,
        severity: String = "error",
        http_status: Int? = null,
        route: String = "/app",
    ) {
        runCatching {
            val target = base_url ?: return
            if (!SLUG.matches(feature) || !SLUG.matches(error_code)) return
            if (severity != "warn" && severity != "error") return
            val status = http_status?.takeIf { it in 100..599 }
            val key = "$feature|$error_code|${status ?: 0}"
            if (!should_send(key)) return

            val body = buildString {
                append("{")
                append("\"surface\":\"").append(SURFACE).append("\",")
                append("\"feature\":\"").append(feature).append("\",")
                append("\"error_code\":\"").append(error_code).append("\",")
                append("\"severity\":\"").append(severity).append("\",")
                append("\"platform\":\"").append(PLATFORM).append("\",")
                append("\"release\":\"").append(release).append("\",")
                append("\"route\":\"").append(route).append("\"")
                if (status != null) append(",\"http_status\":").append(status)
                append("}")
            }

            scope.launch {
                runCatching {
                    val connection = URL("$target$REPORT_PATH").openConnection() as HttpURLConnection
                    connection.requestMethod = "POST"
                    connection.connectTimeout = 4_000
                    connection.readTimeout = 4_000
                    connection.doOutput = true
                    connection.setRequestProperty("Content-Type", "application/json")
                    OutputStreamWriter(connection.outputStream, Charsets.UTF_8).use { it.write(body) }
                    connection.responseCode
                    connection.disconnect()
                }
            }
        }
    }

    private fun should_send(key: String): Boolean {
        if (sent.get() >= MAX_REPORTS_PER_PROCESS) return false
        val now = System.currentTimeMillis()
        val previous = last_seen[key]
        if (previous != null && now - previous < DEDUPE_WINDOW_MS) return false
        last_seen[key] = now
        sent.incrementAndGet()
        return true
    }
}
