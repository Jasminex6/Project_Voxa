package com.example.voxa.data.license

import android.graphics.*
import android.graphics.pdf.PdfDocument
import android.text.Layout
import android.text.StaticLayout
import android.text.TextDirectionHeuristics
import android.text.TextPaint
import java.io.File
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

/** One-page takeaway. The companion JSON retains the full numerical evidence. */
object ProgressPdf {
    private val ink = Color.rgb(22, 38, 58)
    private val muted = Color.rgb(85, 102, 122)
    private val blue = Color.rgb(17, 101, 149)
    private val pale = Color.rgb(239, 245, 249)
    private val line = Color.rgb(215, 226, 234)

    fun write(file: File, report: ProgressReview, ar: Boolean, sample: Boolean = false) {
        val doc = PdfDocument()
        try {
            val page = doc.startPage(PdfDocument.PageInfo.Builder(595, 842, 1).create())
            val canvas = page.canvas
            var y = 40f
            fun text(value: String, x: Float, top: Float, w: Float, size: Float, color: Int = ink, bold: Boolean = false): Float {
                val paint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply { textSize = size; this.color = color; typeface = Typeface.create("sans-serif", if (bold) Typeface.BOLD else Typeface.NORMAL) }
                val displayed = if (ar && value.all { it.code < 128 }) "\u2066$value\u2069" else value
                val layout = StaticLayout.Builder.obtain(displayed, 0, displayed.length, paint, w.toInt())
                    .setTextDirection(if (ar) TextDirectionHeuristics.RTL else TextDirectionHeuristics.LTR)
                    .setAlignment(Layout.Alignment.ALIGN_NORMAL).setIncludePad(false).setLineSpacing(3f, 1f).build()
                canvas.save(); canvas.translate(x, top); layout.draw(canvas); canvas.restore()
                return layout.height.toFloat()
            }
            val locale = if (ar) Locale.forLanguageTag("ar") else Locale.US
            fun date(value: String) = DateTimeFormatter.ofPattern("dd MMM yyyy, HH:mm", locale).format(Instant.parse(value).atZone(ZoneId.of(report.period.timeZone)))
            text(if (sample) { if (ar) "VOXA / مثال ببيانات خيالية" else "VOXA / FICTIONAL EXAMPLE" } else "VOXA", 40f, y, 515f, 10f, blue, true)
            y += 28
            y += text(if (ar) "لمحة عن التقدم" else "Progress at a glance", 40f, y, 515f, 27f, ink, true) + 10
            y += text("${date(report.period.start)} - ${date(report.period.end)}", 40f, y, 515f, 10f, muted) + 5
            y += text(report.period.timeZone, 40f, y, 515f, 9f, muted) + 22
            val practice = report.practice.current
            val task = report.tasks
            val metrics = listOf(
                Triple("${practice.activeDays} / ${report.period.days}", if (ar) "أيام الممارسة" else "Practice days", if (ar) "${practice.usable} محاولات كلام" else "${practice.usable} speech attempts"),
                Triple(if (task.assigned == 0) "--" else "${task.cohortApproved} / ${task.assigned}", if (ar) "مهام جديدة مؤكدة" else "New tasks confirmed", if (task.assigned == 0) { if (ar) "لم تُضف مهام في الفترة" else "No new assignments" } else { if (ar) "أكدها ولي الأمر" else "Confirmed by caregiver" })
            )
            metrics.forEachIndexed { i, (value,label,detail) ->
                val x = 40f + (if (ar) 1-i else i) * 265f
                canvas.drawRoundRect(x,y,x+250,y+100,10f,10f,Paint().apply { color=pale })
                text(value,x+14,y+12,222f,26f,blue,true)
                text(label,x+14,y+49,222f,12f,ink,true)
                text(detail,x+14,y+73,222f,10f,muted)
            }
            y += 128
            y += text(if (ar) "أهم ما يستحق انتباهك" else "What matters this time",40f,y,515f,16f,ink,true)+17
            ReviewHighlights.build(report,ar).forEachIndexed { index, insight ->
                val top=y
                val titleHeight=text(insight.title,60f,top+14,475f,13f,ink,true)
                val detailHeight=text(insight.detail,60f,top+titleHeight+23,475f,11f,muted)
                val height=titleHeight+detailHeight+40
                canvas.drawLine(40f,top,555f,top,Paint().apply { color=line;strokeWidth=.7f })
                y += height + if(index<2) 9 else 0
            }
            y += 18
            y += text(if (ar) "تعكس هذه الملاحظات استجابة التطبيق وتأكيدات ولي الأمر، وليست تقييماً سريرياً." else "These insights describe app responses and caregiver confirmations, not a clinical assessment.",40f,y,515f,10f,muted)+10
            if(report.legacyScoredAttempts>0) y += text(if(ar) "المقارنات تستخدم التتبع الجديد فقط؛ استُبعدت ${report.legacyScoredAttempts} نتائج أقدم قد تشمل النجاحات فقط." else "Comparisons use new tracking only; ${report.legacyScoredAttempts} older entries that may contain successes only are excluded.",40f,y,515f,9f,muted)+10
            check(y<775) { "Report summary exceeds its one page: $y" }
            canvas.drawLine(40f,794f,555f,794f,Paint().apply { color=line;strokeWidth=.7f })
            text(if(ar) "دون أسماء أو صوت. البيانات الكاملة في JSON." else "No names or audio. Full numerical data is available in JSON.",40f,803f,380f,8f,muted)
            text(report.reportId.take(8),470f,803f,85f,8f,muted)
            doc.finishPage(page)
            file.outputStream().use { doc.writeTo(it) }
        } finally { doc.close() }
    }
}
