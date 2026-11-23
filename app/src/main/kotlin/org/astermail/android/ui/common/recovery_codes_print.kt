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

import android.content.Context
import android.graphics.Canvas
import android.graphics.Typeface
import android.os.Bundle
import android.os.CancellationSignal
import android.os.ParcelFileDescriptor
import android.print.PageRange
import android.print.PrintAttributes
import android.print.PrintDocumentAdapter
import android.print.PrintDocumentInfo
import android.print.PrintManager
import android.print.pdf.PrintedPdfDocument
import android.text.Layout
import android.text.StaticLayout
import android.text.TextPaint
import java.io.FileOutputStream
import java.text.DateFormat
import java.util.Date
import org.astermail.android.R
import org.astermail.android.ui.mail.find_activity

internal data class recovery_codes_print_content(
    val title: String,
    val account_email: String,
    val created: String,
    val note: String,
    val codes: List<String>,
)

private const val PAGE_MARGIN_POINTS = 48f
private const val TITLE_SIZE_POINTS = 18f
private const val DETAIL_SIZE_POINTS = 10f
private const val CODE_SIZE_POINTS = 13f
private const val BLOCK_GAP_POINTS = 6f
private const val SECTION_GAP_POINTS = 18f
private const val CODE_GAP_POINTS = 4f
private const val TEXT_COLOR = 0xFF111111.toInt()
private const val DETAIL_COLOR = 0xFF444444.toInt()

fun print_recovery_codes(
    context: Context,
    account_email: String,
    codes: List<String>,
    created_at: Date = Date(),
    on_failure: () -> Unit,
) {
    val activity = find_activity(context)
    val print_manager = activity?.getSystemService(Context.PRINT_SERVICE) as? PrintManager
    if (activity == null || print_manager == null || codes.isEmpty()) {
        on_failure()
        return
    }
    val content = recovery_codes_print_content(
        title = context.getString(R.string.recovery_codes_print_title),
        account_email = account_email,
        created = context.getString(
            R.string.recovery_codes_print_created,
            DateFormat.getDateInstance(DateFormat.LONG).format(created_at),
        ),
        note = context.getString(R.string.recovery_codes_print_note),
        codes = codes,
    )
    val adapter = recovery_codes_print_adapter(
        context = activity.applicationContext,
        content = content,
        failure_message = context.getString(R.string.print_not_available),
    )
    val job = runCatching {
        print_manager.print(content.title, adapter, PrintAttributes.Builder().build())
    }.getOrNull()
    if (job == null) on_failure()
}

internal fun paginate_blocks(heights: List<Float>, page_height: Float): List<List<Int>> {
    if (heights.isEmpty()) return listOf(emptyList())
    val pages = mutableListOf<List<Int>>()
    var current = mutableListOf<Int>()
    var used = 0f
    heights.forEachIndexed { index, height ->
        if (current.isNotEmpty() && used + height > page_height) {
            pages.add(current)
            current = mutableListOf()
            used = 0f
        }
        current.add(index)
        used += height
    }
    pages.add(current)
    return pages
}

private class print_block(val layout: StaticLayout, val gap_after: Float) {
    val height: Float get() = layout.height + gap_after
}

internal class recovery_codes_print_adapter(
    private val context: Context,
    private val content: recovery_codes_print_content,
    private val failure_message: CharSequence,
) : PrintDocumentAdapter() {

    private var attributes: PrintAttributes? = null

    override fun onLayout(
        old_attributes: PrintAttributes?,
        new_attributes: PrintAttributes?,
        cancellation_signal: CancellationSignal?,
        callback: LayoutResultCallback?,
        extras: Bundle?,
    ) {
        if (cancellation_signal?.isCanceled == true) {
            callback?.onLayoutCancelled()
            return
        }
        try {
            val target = new_attributes ?: PrintAttributes.Builder().build()
            attributes = target
            val document = PrintedPdfDocument(context, target)
            val page_count = try {
                val area = document_area(document)
                paginate_blocks(build_blocks(area.first).map { it.height }, area.second).size
            } finally {
                runCatching { document.close() }
            }
            val info = PrintDocumentInfo.Builder(content.title)
                .setContentType(PrintDocumentInfo.CONTENT_TYPE_DOCUMENT)
                .setPageCount(page_count)
                .build()
            callback?.onLayoutFinished(info, new_attributes != old_attributes)
        } catch (failure: RuntimeException) {
            callback?.onLayoutFailed(failure_message)
        }
    }

    override fun onWrite(
        pages: Array<PageRange>?,
        destination: ParcelFileDescriptor?,
        cancellation_signal: CancellationSignal?,
        callback: WriteResultCallback?,
    ) {
        val target = attributes
        if (destination == null || target == null) {
            callback?.onWriteFailed(failure_message)
            return
        }
        val document = PrintedPdfDocument(context, target)
        try {
            val area = document_area(document)
            val blocks = build_blocks(area.first)
            val layout_pages = paginate_blocks(blocks.map { it.height }, area.second)
            layout_pages.forEachIndexed { page_index, indices ->
                if (cancellation_signal?.isCanceled == true) {
                    callback?.onWriteCancelled()
                    return
                }
                val page = document.startPage(page_index)
                draw_blocks(page.canvas, indices.map { blocks[it] })
                document.finishPage(page)
            }
            FileOutputStream(destination.fileDescriptor).use { document.writeTo(it) }
            callback?.onWriteFinished(arrayOf(PageRange.ALL_PAGES))
        } catch (failure: Exception) {
            callback?.onWriteFailed(failure_message)
        } finally {
            runCatching { document.close() }
        }
    }

    private fun document_area(document: PrintedPdfDocument): Pair<Int, Float> {
        val width = (document.pageWidth - PAGE_MARGIN_POINTS * 2).toInt().coerceAtLeast(1)
        val height = (document.pageHeight - PAGE_MARGIN_POINTS * 2).coerceAtLeast(1f)
        return width to height
    }

    private fun build_blocks(width: Int): List<print_block> {
        val blocks = mutableListOf<print_block>()
        blocks.add(print_block(text_layout(content.title, width, TITLE_SIZE_POINTS, TEXT_COLOR, Typeface.DEFAULT_BOLD), BLOCK_GAP_POINTS))
        if (content.account_email.isNotBlank()) {
            blocks.add(print_block(text_layout(content.account_email, width, DETAIL_SIZE_POINTS, DETAIL_COLOR, Typeface.DEFAULT), BLOCK_GAP_POINTS))
        }
        blocks.add(print_block(text_layout(content.created, width, DETAIL_SIZE_POINTS, DETAIL_COLOR, Typeface.DEFAULT), SECTION_GAP_POINTS))
        content.codes.forEachIndexed { index, code ->
            val gap = if (index == content.codes.lastIndex) SECTION_GAP_POINTS else CODE_GAP_POINTS
            blocks.add(print_block(text_layout("${index + 1}.  $code", width, CODE_SIZE_POINTS, TEXT_COLOR, Typeface.MONOSPACE), gap))
        }
        blocks.add(print_block(text_layout(content.note, width, DETAIL_SIZE_POINTS, DETAIL_COLOR, Typeface.DEFAULT), 0f))
        return blocks
    }

    private fun text_layout(text: String, width: Int, size: Float, color: Int, typeface: Typeface): StaticLayout {
        val paint = TextPaint(TextPaint.ANTI_ALIAS_FLAG).apply {
            textSize = size
            this.color = color
            this.typeface = typeface
        }
        return StaticLayout.Builder.obtain(text, 0, text.length, paint, width)
            .setAlignment(Layout.Alignment.ALIGN_NORMAL)
            .build()
    }

    private fun draw_blocks(canvas: Canvas, blocks: List<print_block>) {
        canvas.save()
        canvas.translate(PAGE_MARGIN_POINTS, PAGE_MARGIN_POINTS)
        blocks.forEach { block ->
            block.layout.draw(canvas)
            canvas.translate(0f, block.height)
        }
        canvas.restore()
    }
}
