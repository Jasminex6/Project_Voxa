package com.example.voxa.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.voxa.data.license.*
import com.example.voxa.ui.theme.LocalVoxaColors
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

@Composable
fun ProgressReviewContent(report: ProgressReview) {
    val ar = LocalConfiguration.current.locales[0].language == "ar"
    val colors = LocalVoxaColors.current
    val current = report.practice.current
    val comparison = ProgressAnalytics.comparison(report)
    val task = report.tasks
    val format = DateTimeFormatter.ofPattern("dd MMM", if (ar) Locale("ar") else Locale.US)
    fun date(value: String) = format.format(Instant.parse(value).atZone(ZoneId.of(report.period.timeZone)))
    @Composable fun heading(en: String, arabic: String) {
        Spacer(Modifier.height(18.dp))
        Text(if (ar) arabic else en, color = colors.primaryText, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(8.dp))
    }
    @Composable fun note(en: String, arabic: String) { Text(if (ar) arabic else en, color = colors.secondaryText, style = MaterialTheme.typography.bodySmall) }
    Text("${date(report.period.start)} - ${date(report.period.end)} · ${report.period.timeZone}", color = colors.secondaryText)
    note("Includes today to export time. Comparison uses the same cutoff in the previous period.", "تشمل اليوم حتى وقت التصدير. تقارن الفترة السابقة عند التوقيت نفسه.")
    heading("Practice consistency", "انتظام الممارسة")
    Text(if (ar) "${current.activeDays} من ${report.period.days} أيام بها محاولات كلام" else "${current.activeDays} of ${report.period.days} days with speech attempts", color = colors.primaryText)
    note("${current.usable} usable attempts; ${current.noSpeech} no-speech captures; ${current.technical} technical issues.", "${current.usable} محاولات قابلة للحساب؛ ${current.noSpeech} دون كلام مسموع؛ ${current.technical} مشاكل تقنية.")
    report.practice.daily.chunked(7).forEach { week ->
        Row(Modifier.fillMaxWidth().padding(vertical = 8.dp), horizontalArrangement = Arrangement.spacedBy(5.dp)) {
            week.forEach { day ->
                Surface(Modifier.weight(1f), shape = MaterialTheme.shapes.small,
                    color = if (day.counts.usable > 0) colors.primaryAction.copy(alpha = .15f) else colors.surfaceVariant) {
                    Column(Modifier.padding(5.dp)) {
                        Text(day.date.takeLast(2), style = MaterialTheme.typography.labelSmall, color = colors.secondaryText)
                        Text(day.counts.usable.toString(), color = colors.primaryText)
                    }
                }
            }
            repeat(7 - week.size) { Spacer(Modifier.weight(1f)) }
        }
    }
    heading("App responses, not pronunciation accuracy", "استجابة التطبيق وليست دقة النطق")
    Text(if (ar) "طابق التطبيق ${current.matched} من ${current.usable} محاولات" else "${current.matched} of ${current.usable} attempts matched by the app", color = colors.primaryText)
    if (comparison.percentagePoints != null) note(
        "Change: ${String.format(Locale.US, "%+.1f", comparison.percentagePoints)} percentage points across ${comparison.targets} comparable targets (${comparison.currentTrials} current / ${comparison.previousTrials} previous trials).",
        "التغير: \u2066${String.format(Locale.US, "%+.1f", comparison.percentagePoints)}\u2069 نقطة مئوية في ${comparison.targets} أهداف قابلة للمقارنة (${comparison.currentTrials} محاولات حالية / ${comparison.previousTrials} سابقة).")
    else note("Building a baseline. Repeat the same target and language across periods before interpreting a change.", "جارٍ بناء خط أساس. كرر الهدف واللغة نفسيهما عبر الفترات قبل تفسير التغير.")
    report.practice.targets.forEach { target ->
        Row(Modifier.fillMaxWidth().padding(vertical = 8.dp), horizontalArrangement = Arrangement.SpaceBetween) {
            Text("${ProgressAnalytics.targetName(target.code, ar)} · ${target.language.uppercase(Locale.US)}", color = colors.primaryText, modifier = Modifier.weight(1f))
            Text("${target.current.matched}/${target.current.usable}", color = colors.secondaryText)
        }
        HorizontalDivider(color = colors.border.copy(alpha = .3f))
    }
    note("Comparisons require 5 usable attempts on 2 days per period for the same target/language, with equal weight per target.", "تتطلب المقارنة ٥ محاولات على يومين في كل فترة للهدف واللغة نفسيهما، مع وزن متساوٍ لكل هدف.")
    heading("Tasks and help needed", "المهام والمساعدة المطلوبة")
    Text(if (ar) "${task.cohortApproved}/${task.assigned} مهام جديدة أكدها ولي الأمر" else "${task.cohortApproved}/${task.assigned} newly assigned tasks confirmed", color = colors.primaryText)
    note("${task.submitted} tasks reported done; ${task.approved} confirmations in this period, including older assignments; ${task.unable} marked unable.", "${task.submitted} مهام أُبلغ بإتمامها؛ ${task.approved} تأكيدات في الفترة قد تشمل مهام أقدم؛ ${task.unable} تعذر إتمامها.")
    val help = task.support
    note("Caregiver-reported help: ${help.independent} independent, ${help.prompted} prompted, ${help.assisted} assisted, ${help.notRecorded} not recorded.", "المساعدة حسب ولي الأمر: ${help.independent} بشكل مستقل، ${help.prompted} بتوجيه، ${help.assisted} بمساعدة، ${help.notRecorded} لم تسجل.")
    heading("Caregiver review queue", "مهام تنتظر مراجعة ولي الأمر")
    Text(if (ar) "${task.awaitingReview} مهام تنتظر المراجعة" else "${task.awaitingReview} tasks awaiting review", color = colors.primaryText)
    task.oldestAwaitingHours?.let { note("Oldest known wait: ${String.format(Locale.US, "%.1f", it)} hours (${task.timedAwaiting}/${task.awaitingReview} have timing).", "أقدم انتظار معروف: ${String.format(Locale.US, "%.1f", it)} ساعة (تتوفر أوقات لـ${task.timedAwaiting}/${task.awaitingReview}).") }
    task.reviewHoursMedian?.let { note("Median review time: ${String.format(Locale.US, "%.1f", it)} hours from ${task.reviewSamples} paired records. This describes review, not the child's speed.", "وسيط زمن المراجعة: ${String.format(Locale.US, "%.1f", it)} ساعة من ${task.reviewSamples} سجلات متطابقة؛ يصف المراجعة ولا يقيس سرعة الطفل.") }
    heading("Bring to the next review", "نقاط للمتابعة القادمة")
    val focus = report.practice.targets.filter { it.code != "other" && it.current.usable >= 5 && it.current.activeDays >= 2 }.minByOrNull { it.current.rate ?: 101.0 }
    if (focus != null) note("Review ${ProgressAnalytics.targetName(focus.code, false)} (${focus.language}): ${focus.current.matched}/${focus.current.usable} app matches. Discuss the target, setting, and recognizer together.", "راجع هدف ${ProgressAnalytics.targetName(focus.code, true)} (${focus.language}): ${focus.current.matched}/${focus.current.usable} مطابقات. ناقش الهدف والبيئة والمحرك معاً.")
    else note("Gather repeated attempts on the same targets before drawing a trend.", "اجمع محاولات متكررة للأهداف نفسها قبل تفسير الاتجاه.")
    if (current.technical > 0 || current.noSpeech > 0) note("Check capture conditions before interpreting non-matches.", "راجع ظروف الالتقاط قبل تفسير عدم المطابقة.")
    if (task.awaitingReview > 0) note("Review submitted tasks; waiting for caregiver confirmation is not child failure.", "راجع المهام المقدمة؛ انتظار تأكيد ولي الأمر ليس إخفاقاً للطفل.")
    heading("Coverage and privacy", "التغطية والخصوصية")
    note("New tracking began ${date(report.captureStartedAt)}. ${report.legacyScoredAttempts} older scored entries are excluded from rates and comparisons because earlier history may contain successes only. Personal targets are numbered; no names, recordings, transcripts, or private notes leave the app.",
        "بدأ التتبع الجديد ${date(report.captureStartedAt)}. استُبعدت ${report.legacyScoredAttempts} نتائج قديمة من المعدلات والمقارنات لأن السجل الأقدم قد يشمل النجاحات فقط. الأهداف الشخصية مرقمة؛ لا تُصدَّر أسماء أو تسجيلات أو نصوص التعرف أو ملاحظات خاصة.")
    Spacer(Modifier.height(24.dp))
}
