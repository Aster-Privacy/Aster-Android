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

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Color
import android.graphics.pdf.LoadParams
import android.graphics.pdf.PdfRenderer
import android.graphics.pdf.PdfRendererPreV
import android.graphics.pdf.RenderParams
import android.os.Build
import android.os.ParcelFileDescriptor
import android.os.ext.SdkExtensions
import android.system.Os
import android.system.OsConstants
import androidx.annotation.RequiresApi
import androidx.annotation.RequiresExtension
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.Closeable
import java.io.File
import java.util.UUID

internal const val MAX_PDF_PASSWORD_LENGTH = 1024
internal const val MAX_PDF_PAGE_WIDTH_PX = 2048
internal const val MAX_PDF_PAGE_HEIGHT_PX = 4096
private const val PDF_SNIFF_WINDOW = 1024
private const val PDF_PASSWORD_EXTENSION_VERSION = 13
private val pdf_magic = "%PDF-".toByteArray(Charsets.US_ASCII)

internal enum class pdf_open_failure {
    password_required,
    password_incorrect,
    password_unsupported,
    unreadable,
}

internal sealed interface pdf_open_result {
    class opened(val document: pdf_document) : pdf_open_result
    data class failed(val reason: pdf_open_failure) : pdf_open_result
}

internal data class pdf_page_size(val width: Int, val height: Int)

internal fun is_pdf_attachment(content_type: String, filename: String): Boolean {
    val ct = content_type.substringBefore(';').trim().lowercase()
    if (ct == "application/pdf" || ct == "application/x-pdf") return true
    val generic = ct.isEmpty() || ct == "application/octet-stream" || ct == "binary/octet-stream"
    return generic && filename.trim().lowercase().endsWith(".pdf")
}

internal fun looks_like_pdf(bytes: ByteArray): Boolean {
    val limit = minOf(bytes.size, PDF_SNIFF_WINDOW) - pdf_magic.size
    for (start in 0..limit) {
        var matched = true
        for (i in pdf_magic.indices) {
            if (bytes[start + i] != pdf_magic[i]) {
                matched = false
                break
            }
        }
        if (matched) return true
    }
    return false
}

internal fun is_pdf_password_acceptable(password: String): Boolean =
    password.isNotEmpty() && password.length <= MAX_PDF_PASSWORD_LENGTH

internal fun classify_pdf_open_error(
    error: Throwable,
    password: String?,
    supports_password: Boolean,
): pdf_open_failure = when {
    error !is SecurityException -> pdf_open_failure.unreadable
    !supports_password -> pdf_open_failure.password_unsupported
    password.isNullOrEmpty() -> pdf_open_failure.password_required
    else -> pdf_open_failure.password_incorrect
}

internal fun pdf_page_bitmap_size(
    page_width_pt: Int,
    page_height_pt: Int,
    target_width_px: Int,
    max_width_px: Int = MAX_PDF_PAGE_WIDTH_PX,
    max_height_px: Int = MAX_PDF_PAGE_HEIGHT_PX,
): pdf_page_size {
    val safe_w = page_width_pt.coerceAtLeast(1)
    val safe_h = page_height_pt.coerceAtLeast(1)
    var width = target_width_px.coerceIn(1, max_width_px)
    var height = (width.toLong() * safe_h / safe_w).coerceAtLeast(1L)
    if (height > max_height_px) {
        height = max_height_px.toLong()
        width = (height * safe_w / safe_h).toInt().coerceAtLeast(1)
    }
    return pdf_page_size(width, height.toInt())
}

internal fun pdf_password_supported(): Boolean =
    Build.VERSION.SDK_INT >= Build.VERSION_CODES.VANILLA_ICE_CREAM ||
        (
            Build.VERSION.SDK_INT >= Build.VERSION_CODES.S &&
                SdkExtensions.getExtensionVersion(Build.VERSION_CODES.S) >= PDF_PASSWORD_EXTENSION_VERSION
            )

private interface page_renderer : Closeable {
    val page_count: Int
    fun render(index: Int, target_width_px: Int): Bitmap
}

private fun render_into_bitmap(width_pt: Int, height_pt: Int, target_width_px: Int, draw: (Bitmap) -> Unit): Bitmap {
    val size = pdf_page_bitmap_size(width_pt, height_pt, target_width_px)
    val bitmap = Bitmap.createBitmap(size.width, size.height, Bitmap.Config.ARGB_8888)
    bitmap.eraseColor(Color.WHITE)
    try {
        draw(bitmap)
    } catch (error: Throwable) {
        bitmap.recycle()
        throw error
    }
    return bitmap
}

private class platform_page_renderer(private val renderer: PdfRenderer) : page_renderer {
    override val page_count: Int get() = renderer.pageCount

    override fun render(index: Int, target_width_px: Int): Bitmap =
        renderer.openPage(index).use { page ->
            render_into_bitmap(page.width, page.height, target_width_px) {
                page.render(it, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
            }
        }

    override fun close() = renderer.close()
}

@RequiresExtension(extension = Build.VERSION_CODES.S, version = PDF_PASSWORD_EXTENSION_VERSION)
private class pre_v_page_renderer(private val renderer: PdfRendererPreV) : page_renderer {
    override val page_count: Int get() = renderer.pageCount

    override fun render(index: Int, target_width_px: Int): Bitmap =
        renderer.openPage(index).use { page ->
            render_into_bitmap(page.width, page.height, target_width_px) {
                page.render(it, null, null, RenderParams.Builder(RenderParams.RENDER_MODE_FOR_DISPLAY).build())
            }
        }

    override fun close() = renderer.close()
}

@RequiresApi(Build.VERSION_CODES.VANILLA_ICE_CREAM)
private fun open_platform_with_password(fd: ParcelFileDescriptor, password: String): page_renderer =
    platform_page_renderer(PdfRenderer(fd, LoadParams.Builder().setPassword(password).build()))

@RequiresExtension(extension = Build.VERSION_CODES.S, version = PDF_PASSWORD_EXTENSION_VERSION)
private fun open_pre_v(fd: ParcelFileDescriptor, password: String?): page_renderer {
    val renderer = if (password == null) {
        PdfRendererPreV(fd)
    } else {
        PdfRendererPreV(fd, LoadParams.Builder().setPassword(password).build())
    }
    return pre_v_page_renderer(renderer)
}

private fun create_renderer(fd: ParcelFileDescriptor, password: String?): page_renderer {
    if (password == null) return platform_page_renderer(PdfRenderer(fd))
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.VANILLA_ICE_CREAM) {
        return open_platform_with_password(fd, password)
    }
    if (
        Build.VERSION.SDK_INT >= Build.VERSION_CODES.S &&
        SdkExtensions.getExtensionVersion(Build.VERSION_CODES.S) >= PDF_PASSWORD_EXTENSION_VERSION
    ) {
        return open_pre_v(fd, password)
    }
    throw SecurityException()
}

@RequiresApi(Build.VERSION_CODES.R)
private fun memory_descriptor(bytes: ByteArray): ParcelFileDescriptor {
    val fd = Os.memfd_create("aster_pdf", 0)
    try {
        var offset = 0
        while (offset < bytes.size) {
            offset += Os.write(fd, bytes, offset, bytes.size - offset)
        }
        Os.lseek(fd, 0L, OsConstants.SEEK_SET)
        return ParcelFileDescriptor.dup(fd)
    } finally {
        Os.close(fd)
    }
}

private fun unlinked_file_descriptor(context: Context, bytes: ByteArray): ParcelFileDescriptor {
    val dir = File(context.noBackupFilesDir, "pdf_view").apply { mkdirs() }
    dir.listFiles()?.forEach { runCatching { it.delete() } }
    val file = File(dir, UUID.randomUUID().toString())
    try {
        file.writeBytes(bytes)
        return ParcelFileDescriptor.open(file, ParcelFileDescriptor.MODE_READ_ONLY)
    } finally {
        file.delete()
    }
}

private fun bytes_to_descriptor(context: Context, bytes: ByteArray): ParcelFileDescriptor =
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
        runCatching { memory_descriptor(bytes) }.getOrElse { unlinked_file_descriptor(context, bytes) }
    } else {
        unlinked_file_descriptor(context, bytes)
    }

@OptIn(ExperimentalCoroutinesApi::class)
private val pdf_dispatcher = Dispatchers.IO.limitedParallelism(1)
private val pdf_cleanup_scope = CoroutineScope(SupervisorJob() + pdf_dispatcher)

internal class pdf_document private constructor(
    private val descriptor: ParcelFileDescriptor,
    private val renderer: page_renderer,
) {
    val page_count: Int = renderer.page_count
    private var closed = false

    suspend fun render_page(index: Int, target_width_px: Int): Bitmap? = withContext(pdf_dispatcher) {
        if (closed || index !in 0 until page_count) return@withContext null
        try {
            renderer.render(index, target_width_px)
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (_: Throwable) {
            null
        }
    }

    fun close() {
        pdf_cleanup_scope.launch {
            if (closed) return@launch
            closed = true
            runCatching { renderer.close() }
            runCatching { descriptor.close() }
        }
    }

    companion object {
        suspend fun open(context: Context, bytes: ByteArray, password: String?): pdf_open_result =
            withContext(pdf_dispatcher) {
                if (!looks_like_pdf(bytes)) return@withContext pdf_open_result.failed(pdf_open_failure.unreadable)
                if (password != null && !is_pdf_password_acceptable(password)) {
                    return@withContext pdf_open_result.failed(pdf_open_failure.password_incorrect)
                }
                val descriptor = try {
                    bytes_to_descriptor(context, bytes)
                } catch (_: Throwable) {
                    return@withContext pdf_open_result.failed(pdf_open_failure.unreadable)
                }
                try {
                    val renderer = create_renderer(descriptor, password)
                    if (renderer.page_count <= 0) {
                        runCatching { renderer.close() }
                        runCatching { descriptor.close() }
                        return@withContext pdf_open_result.failed(pdf_open_failure.unreadable)
                    }
                    pdf_open_result.opened(pdf_document(descriptor, renderer))
                } catch (error: Throwable) {
                    runCatching { descriptor.close() }
                    if (error is CancellationException) throw error
                    pdf_open_result.failed(classify_pdf_open_error(error, password, pdf_password_supported()))
                }
            }
    }
}
