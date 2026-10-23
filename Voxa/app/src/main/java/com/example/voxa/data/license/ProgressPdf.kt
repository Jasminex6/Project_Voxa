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

/** A two-page, wrapped and bilingual review; the JSON remains the machine-readable companion. */
object ProgressPdf {
    private val ink = Color.rgb(22, 38, 58)
    private val muted = Color.rgb(85, 102, 122)
    private val blue = Color.rgb(17, 101, 149)
    private val pale = Color.rgb(239, 245, 249)
    private val line = Color.rgb(215, 226, 234)
    private fun number(value: Double) = String.format(Locale.US, "%.1f", value)

    fun write(file: File, report: ProgressReview, ar: Boolean, sample: Boolean = false) {
        val doc = PdfDocument()
        try {
            val extraTargets = report.practice.targets.drop(4).chunked(16)
            val totalPages = 2 + extraTargets.size
            val first = Page(doc, 1, report, ar, totalPages, sample)
            first.header(if (ar) "مراجعة الممارسة" else "Practice review", if (ar) "نمط الممارسة والاستجابة داخل التطبيق" else "Practice pattern and app responses")
            val current = report.practice.current
            val comparison = ProgressAnalytics.comparison(report)
            first.metrics(listOf(
                "${current.activeDays}/${report.period.days}" to if (ar) "أيام بها محاولات كلام" else "Days with speech attempts",
                "${current.matched}/${current.usable}" to if (ar) "محاولات طابقها التطبيق" else "App-matched attempts",
                (comparison.percentagePoints?.let { "${if (it >= 0) "+" else ""}${number(it)} pp" } ?: "--") to if (ar) "التغير لأهداف قابلة للمقارنة" else "Change on comparable targets"))
            first.paragraph(if (ar) "المطابقة هي قبول التطبيق للكلمة أو بدائلها، وليست دقة نطق سريرية. لا تدخل أعطال المحرك أو عدم سماع كلام في معدل المطابقة." else "A match means the app accepted the target or an alias. It is not clinical pronunciation accuracy. Engine failures and no-speech captures are excluded from the match rate.")
            first.heading(if (ar) "توزيع الممارسة عبر الأيام" else "Practice across the days")
            first.chart(report.practice.daily)
            first.paragraph(if (ar) "الأزرق: طابقها التطبيق • الرمادي: لم تطابق • الأصفر: لم يُسمع كلام • البرتقالي: مشكلة تقنية" else "Blue: app match | Grey: not matched | Yellow: no speech | Orange: technical issue", 9f)
            first.heading(if (ar) "أهداف الممارسة واللغة" else "Practice targets and language")
            first.tableRow(listOf(if (ar) "الهدف / اللغة" else "Target / language", if (ar) "الفترة الحالية" else "This period", if (ar) "الفترة السابقة" else "Previous period", if (ar) "التغير" else "Change"), true)
            val baselineCovered = !Instant.parse(report.captureStartedAt).isAfter(Instant.parse(report.period.previousStart))
            report.practice.targets.take(4).forEach { target ->
                val comparable = baselineCovered && target.code != "other" && target.current.usable >= 5 && target.previous.usable >= 5 && target.current.activeDays >= 2 && target.previous.activeDays >= 2
                val change = if (comparable) "${number(target.current.rate!! - target.previous.rate!!)} pp" else "--"
                first.tableRow(listOf("${ProgressAnalytics.targetName(target.code, ar)} / ${target.language.uppercase(Locale.US)}",
                    "${target.current.matched}/${target.current.usable}", if (baselineCovered) "${target.previous.matched}/${target.previous.usable}" else "--", change))
            }
            if (report.practice.targets.isEmpty()) first.paragraph(if (ar) "لم تُسجل محاولات بالتتبع الجديد في هذه الفترة بعد." else "No captures from the new tracking are available for this period yet.")
            first.paragraph(if (ar) "المقارنة تتطلب ٥ محاولات على يومين في كل فترة للهدف واللغة نفسيهما، مع وزن متساوٍ للأهداف. ترد بقية الأهداف في الملحق عند وجودها." else "Comparison requires 5 usable attempts on 2 days per period for the same target and language, with equal weight per target. Further targets appear in the appendix when needed.", 9f)
            first.heading(if (ar) "مدى كفاية البيانات" else "How much evidence is available?")
            first.paragraph(if (comparison.targets > 0) {
                if (ar) "المقارنة تشمل ${comparison.targets} أهداف، مع ${comparison.currentTrials} محاولات حالية و${comparison.previousTrials} سابقة. التغير وصف لاستجابة التطبيق فقط." else "Comparison includes ${comparison.targets} targets, with ${comparison.currentTrials} current and ${comparison.previousTrials} previous trials. The change describes app responses only."
            } else if (ar) "لا توجد مقارنة موثوقة بعد. يلزم تتبع يغطي الفترة السابقة وتكرار الأهداف واللغة نفسيهما، بدلاً من إظهار تحسن افتراضي." else "No supported comparison yet. Tracking must cover the prior period and repeat the same targets and language; an improvement is never invented.")
            first.footer(); doc.finishPage(first.page)

            val second = Page(doc, 2, report, ar, totalPages, sample)
            second.header(if (ar) "المهام وسياق المتابعة" else "Tasks and follow-through", if (ar) "فصل نشاط الطفل عن متابعة ولي الأمر" else "Separate child activity from caregiver review")
            val task = report.tasks
            second.metrics(listOf("${task.cohortApproved}/${task.assigned}" to if (ar) "مهام جديدة أكدها ولي الأمر" else "New tasks confirmed",
                task.submitted.toString() to if (ar) "مهام أبلغ الطفل بإتمامها" else "Tasks reported done",
                task.awaitingReview.toString() to if (ar) "تنتظر مراجعة ولي الأمر الآن" else "Awaiting caregiver review"))
            second.paragraph(if (ar) "أُنشئت ${task.assigned} مهام في الفترة؛ أُكد إتمام ${task.cohortApproved} منها حتى الآن. بلغ مجموع تأكيدات ولي الأمر ${task.approved}، وقد يشمل مهام أقدم. سُجلت صعوبة الإتمام لـ${task.unable} مهام." else "${task.assigned} tasks were assigned in this period; ${task.cohortApproved} of those are confirmed so far. There were ${task.approved} caregiver confirmations in total, which may include older tasks. ${task.unable} tasks were marked unable.")
            second.heading(if (ar) "المساعدة عند الإتمام حسب ولي الأمر" else "Help at completion, as reported by the caregiver")
            val support = task.support
            val assessed = support.independent + support.prompted + support.assisted
            second.tableRow(listOf(if (ar) "بشكل مستقل" else "Independent", if (ar) "بتذكير أو توجيه" else "With prompting", if (ar) "بمساعدة مباشرة" else "With direct help", if (ar) "لم تُسجل المساعدة" else "Not recorded"), true)
            second.tableRow(listOf(support.independent.toString(), support.prompted.toString(), support.assisted.toString(), support.notRecorded.toString()))
            second.paragraph(if (ar) "وُصفت المساعدة في $assessed من ${task.approved} تأكيدات. لا نستنتج الاستقلالية من الضغط على زر الإتمام." else "Help was described for $assessed of ${task.approved} confirmations. Independence is never inferred from tapping Done.")
            second.heading(if (ar) "متابعة ولي الأمر" else "Caregiver review process")
            second.paragraph(if (task.oldestAwaitingHours != null) {
                if (ar) "أقدم انتظار معروف: ${number(task.oldestAwaitingHours)} ساعة. تتوفر أوقات لـ${task.timedAwaiting} من ${task.awaitingReview} مهام تنتظر المراجعة." else "Oldest known wait: ${number(task.oldestAwaitingHours)} hours. Timing is known for ${task.timedAwaiting} of ${task.awaitingReview} tasks awaiting review."
            } else if (ar) "لا يوجد وقت انتظار معروف. قد تكون بعض المهام أقدم من بدء التتبع." else "No known waiting time. Some tasks may predate the new tracking.")
            second.paragraph(if (task.reviewHoursMedian != null) {
                if (ar) "وسيط الوقت من الإبلاغ إلى التأكيد: ${number(task.reviewHoursMedian)} ساعة، من ${task.reviewSamples} مهام. هذا يقيس عملية المراجعة، لا قدرة الطفل." else "Median time from submission to confirmation: ${number(task.reviewHoursMedian)} hours across ${task.reviewSamples} tasks. This measures the review process, not the child's ability."
            } else if (ar) "لا تتوفر أزواج إبلاغ وتأكيد لحساب زمن المراجعة بعد." else "No paired submission and confirmation timestamps are available yet.")
            second.heading(if (ar) "نقاط للنقاش في المتابعة القادمة" else "Bring these questions to the next review")
            val focus = report.practice.targets.filter { it.code != "other" && it.current.usable >= 5 && it.current.activeDays >= 2 }.minByOrNull { it.current.rate ?: 101.0 }
            if (focus != null) second.paragraph(if (ar) "• راجع هدف ${ProgressAnalytics.targetName(focus.code, true)} (${focus.language}): طابق التطبيق ${focus.current.matched} من ${focus.current.usable}. ناقش الهدف وبيئة الممارسة وقدرة المحرك معاً." else "- Review ${ProgressAnalytics.targetName(focus.code, false)} (${focus.language}): ${focus.current.matched}/${focus.current.usable} app matches. Discuss the target, practice setting, and recognizer together.")
            else second.paragraph(if (ar) "• اجمع محاولات متكررة للأهداف نفسها قبل تفسير التغير بين الفترات." else "- Collect repeated attempts on the same targets before interpreting a period-to-period change.")
            if (current.technical > 0 || current.noSpeech > 0) second.paragraph(if (ar) "• توجد ${current.technical} مشاكل تقنية و${current.noSpeech} محاولات دون كلام مسموع. راجع ظروف الالتقاط قبل تفسير النتائج." else "- There were ${current.technical} technical issues and ${current.noSpeech} no-speech captures. Check capture conditions before interpreting results.")
            if (task.awaitingReview > 0) second.paragraph(if (ar) "• راجع ${task.awaitingReview} مهام قدمها الطفل؛ تأخر المراجعة ليس إخفاقاً للطفل." else "- Review the ${task.awaitingReview} submitted tasks; review delays are not child failures.")
            if (support.notRecorded > 0) second.paragraph(if (ar) "• سجّل مستوى المساعدة في التأكيدات التالية لتصبح متابعة الاستقلالية ممكنة." else "- Record help levels on future confirmations so independence can be discussed with evidence.")
            second.heading(if (ar) "حدود التغطية والخصوصية" else "Coverage and privacy")
            second.paragraph(if (ar) "بدأ التتبع المنظم في ${second.date(report.captureStartedAt)}. توجد ${report.legacyScoredAttempts} نتائج قديمة في هذه الفترة، مستبعدة من معدلات المطابقة والمقارنات لأنها قد تشمل النجاحات فقط. حالة المهام الحالية: ${task.snapshot.pending} معلقة، ${task.snapshot.submitted} مقدمة، ${task.snapshot.unable} تعذر إتمامها. هذه لقطة حالية وليست معدل إنجاز أسبوعياً." else "Structured tracking started ${second.date(report.captureStartedAt)}. ${report.legacyScoredAttempts} older scored entries in this period are excluded from match rates and comparisons because they may contain successes only. Current task snapshot: ${task.snapshot.pending} pending, ${task.snapshot.submitted} submitted, ${task.snapshot.unable} unable. This snapshot is not a weekly completion rate.", 9f)
            second.paragraph(if (ar) "لا أسماء أو تسجيلات أو نصوص التعرف أو ملاحظات خاصة. الأهداف المخصصة تحمل أرقاماً مستعارة. يشارك ولي الأمر التقرير بإرادته. التقرير لا يقيس وضوح النطق أو شدة اضطراب أو نتيجة علاج." else "No names, recordings, transcripts, or private notes. Personal targets use pseudonymous numbers. Sharing is caregiver-controlled. This report does not measure speech clarity, disorder severity, or treatment outcome.", 9f)
            second.footer(); doc.finishPage(second.page)
            extraTargets.forEachIndexed { index, targets ->
                val appendix = Page(doc, index + 3, report, ar, totalPages, sample)
                appendix.header(if (ar) "تفاصيل أهداف الممارسة" else "Practice target detail", if (ar) "ملحق للمراجعة" else "Review appendix")
                appendix.tableRow(listOf(if (ar) "الهدف / اللغة" else "Target / language", if (ar) "مطابقات / محاولات" else "Matches / trials", if (ar) "أيام ممارسة" else "Practice days", if (ar) "التقاطات تقنية / صامتة" else "Technical / no speech"), true)
                targets.forEach { target -> appendix.tableRow(listOf("${ProgressAnalytics.targetName(target.code, ar)} / ${target.language.uppercase(Locale.US)}", "${target.current.matched}/${target.current.usable}", target.current.activeDays.toString(), "${target.current.technical} / ${target.current.noSpeech}")) }
                appendix.paragraph(if (ar) "هذه نتائج استجابة التطبيق وليست درجات نطق. يحتوي ملف JSON على القيم السابقة والبيانات اليومية أيضاً." else "These describe app responses, not pronunciation scores. JSON also includes prior-period and daily detail.", 9f)
                appendix.footer(); doc.finishPage(appendix.page)
            }
            file.outputStream().use { doc.writeTo(it) }
        } finally { doc.close() }
    }

    private class Page(doc: PdfDocument, val index: Int, val report: ProgressReview, val ar: Boolean, val totalPages: Int, val sample: Boolean) {
        val page = doc.startPage(PdfDocument.PageInfo.Builder(595, 842, index).create())
        val canvas = page.canvas
        var y = 38f
        private val width = 515f
        fun date(value: String): String = DateTimeFormatter.ofPattern("dd MMM yyyy, HH:mm", if (ar) Locale("ar") else Locale.US).format(Instant.parse(value).atZone(ZoneId.of(report.period.timeZone)))
        private fun text(value: String, x: Float, top: Float, w: Float, size: Float, color: Int = ink, bold: Boolean = false): Float {
            val paint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply { textSize = size; this.color = color; typeface = Typeface.create("sans-serif", if (bold) Typeface.BOLD else Typeface.NORMAL) }
            // Isolate ASCII values so RTL does not move signs or reverse ratios/page references.
            val displayed = if (ar && value.all { it.code < 128 }) "\u2066$value\u2069" else value
            val layout = StaticLayout.Builder.obtain(displayed, 0, displayed.length, paint, w.toInt())
                .setTextDirection(if (ar) TextDirectionHeuristics.RTL else TextDirectionHeuristics.LTR)
                .setAlignment(if (ar) Layout.Alignment.ALIGN_NORMAL else Layout.Alignment.ALIGN_NORMAL)
                .setIncludePad(false).setLineSpacing(2f, 1f).build()
            canvas.save(); canvas.translate(x, top); layout.draw(canvas); canvas.restore()
            return layout.height.toFloat()
        }
        fun header(title: String, subtitle: String) {
            text("VOXA / ${if (sample) { if (ar) "مثال ببيانات خيالية" else "FICTIONAL EXAMPLE" } else { if (ar) "مراجعة نشاط" else "ACTIVITY REVIEW" }}", 40f, y, width, 10f, blue, true); y += 24
            y += text(title, 40f, y, width, 25f, ink, true) + 6
            y += text(subtitle, 40f, y, width, 11f, muted) + 10
            y += text("${date(report.period.start)} - ${date(report.period.end)}  /  ${report.period.timeZone}", 40f, y, width, 9f, muted) + 7
            y += text(if (ar) "تشمل اليوم حتى وقت التصدير؛ تقارن الفترة السابقة عند التوقيت نفسه." else "Includes today to export time; the prior period uses the same local-time cutoff.", 40f, y, width, 9f, muted) + 16
        }
        fun paragraph(value: String, size: Float = 10f) { y += text(value, 40f, y, width, size, muted) + 9 }
        fun heading(value: String) { y += 9; y += text(value, 40f, y, width, 13f, ink, true) + 10 }
        fun metrics(values: List<Pair<String, String>>) {
            val cell = (width - 20) / 3
            values.forEachIndexed { i, (value, label) ->
                val x = 40 + (if (ar) 2 - i else i) * (cell + 10)
                canvas.drawRoundRect(x, y, x + cell, y + 78, 9f, 9f, Paint().apply { color = pale })
                text(value, x + 10, y + 10, cell - 20, 23f, blue, true)
                text(label, x + 10, y + 42, cell - 20, 9f, muted)
            }
            y += 91
        }
        fun tableRow(values: List<String>, header: Boolean = false) {
            val cells = if (ar) values.reversed() else values
            val cellWidth = width / cells.size
            val height = if (header) 35f else 31f
            if (header) canvas.drawRect(40f, y, 555f, y + height, Paint().apply { color = pale })
            cells.forEachIndexed { i, value -> text(value, 46f + i * cellWidth, y + 7, cellWidth - 12, if (header) 9f else 10f, if (header) muted else ink, header) }
            canvas.drawLine(40f, y + height, 555f, y + height, Paint().apply { color = line; strokeWidth = .6f })
            y += height
        }
        fun chart(days: List<ReportDay>) {
            val max = days.maxOfOrNull { it.counts.captures }?.coerceAtLeast(1) ?: 1
            val step = width / days.size
            val base = y + 69
            days.forEachIndexed { index, day ->
                val x = 40f + (if (ar) days.lastIndex - index else index) * step + 2
                var bottom = base
                val parts = listOf(day.counts.matched to blue, (day.counts.usable - day.counts.matched) to Color.rgb(162, 179, 192), day.counts.noSpeech to Color.rgb(211, 174, 69), day.counts.technical to Color.rgb(200, 107, 57))
                for ((amount, color) in parts) { val h = amount * 59f / max; canvas.drawRect(x, bottom - h, x + step - 4, bottom, Paint().apply { this.color = color }); bottom -= h }
                if (days.size <= 7 || index % 5 == 0 || index == days.lastIndex) text(day.date.takeLast(2), x, base + 5, step - 2, 8f, muted)
            }
            y += 93
        }
        fun footer() {
            check(y < 783) { "Report content exceeds page $index: $y" }
            canvas.drawLine(40f, 794f, 555f, 794f, Paint().apply { color = line; strokeWidth = .7f })
            text("${if (ar) "مرجع مستعار" else "Pseudonymous reference"}: ${report.subjectId.take(8)} / ${report.reportId.take(8)}", 40f, 802f, 420f, 8f, muted)
            text("$index / $totalPages", 490f, 802f, 65f, 8f, muted)
        }
    }
}
