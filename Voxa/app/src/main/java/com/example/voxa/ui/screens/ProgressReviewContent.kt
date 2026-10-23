package com.example.voxa.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
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
    val task = report.tasks
    var details by remember(report.reportId) { mutableStateOf(false) }
    val format = DateTimeFormatter.ofPattern("dd MMM", if (ar) Locale.forLanguageTag("ar") else Locale.US)
    fun date(value: String) = format.format(Instant.parse(value).atZone(ZoneId.of(report.period.timeZone)))
    @Composable fun note(value: String) { Text(value, color = colors.secondaryText, style = MaterialTheme.typography.bodySmall) }
    @Composable fun metric(value: String, label: String, detail: String, modifier: Modifier) {
        Card(modifier, colors = CardDefaults.cardColors(containerColor = colors.primaryAction.copy(alpha = .1f))) {
            Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(5.dp)) {
                Text(if (ar) "\u2066$value\u2069" else value, style = MaterialTheme.typography.headlineSmall, color = colors.primaryText, fontWeight = FontWeight.Bold)
                Text(label, style = MaterialTheme.typography.labelLarge, color = colors.primaryText)
                note(detail)
            }
        }
    }
    note("${date(report.period.start)} - ${date(report.period.end)}")
    Spacer(Modifier.height(16.dp))
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        metric("${current.activeDays} / ${report.period.days}", if (ar) "أيام الممارسة" else "Practice days",
            if (ar) "${current.usable} محاولات كلام" else "${current.usable} speech attempts", Modifier.weight(1f))
        metric(if (task.assigned == 0) "—" else "${task.cohortApproved} / ${task.assigned}", if (ar) "مهام جديدة مؤكدة" else "New tasks confirmed",
            if (task.assigned == 0) { if (ar) "لم تُضف مهام في الفترة" else "No new assignments" } else { if (ar) "أكدها ولي الأمر" else "Confirmed by caregiver" }, Modifier.weight(1f))
    }
    Spacer(Modifier.height(20.dp))
    Text(if (ar) "أهم ما يستحق انتباهك" else "What matters this time", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, color = colors.primaryText)
    ReviewHighlights.build(report, ar).forEach { insight ->
        Spacer(Modifier.height(10.dp))
        Card(Modifier.fillMaxWidth(), colors = CardDefaults.cardColors(containerColor = colors.normalCard)) {
            Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text(insight.title, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold, color = colors.primaryText)
                Text(insight.detail, style = MaterialTheme.typography.bodyMedium, color = colors.secondaryText)
            }
        }
    }
    Spacer(Modifier.height(16.dp))
    note(if (ar) "هذه استجابة التطبيق وتأكيدات ولي الأمر، وليست تقييماً سريرياً. لا يُصدَّر صوت أو أسماء." else "Based on app responses and caregiver confirmations, not a clinical assessment. No audio or names are exported.")
    TextButton(onClick = { details = !details }) { Text(if (details) { if (ar) "إخفاء التفاصيل" else "Hide details" } else { if (ar) "التفاصيل" else "Details" }) }
    if (details) {
        note(if (ar) "الفترة تشمل اليوم حتى التصدير (${report.period.timeZone}). المطابقة الحالية: ${current.matched} من ${current.usable}. لا تدخل ${current.noSpeech} محاولات دون كلام و${current.technical} أعطال في المعدل. المقارنات تتطلب ٥ محاولات على يومين في كل فترة للهدف واللغة نفسيهما." else "Includes today to export time (${report.period.timeZone}). Current app matches: ${current.matched} of ${current.usable}. ${current.noSpeech} no-speech captures and ${current.technical} technical issues are excluded. Comparisons need 5 attempts on 2 days per period for the same target and language.")
        Spacer(Modifier.height(10.dp))
        note(if (ar) "بدأ التتبع في ${date(report.captureStartedAt)}. استُبعدت ${report.legacyScoredAttempts} نتائج قديمة قد تشمل النجاحات فقط. تتوفر البيانات الكاملة في JSON." else "Tracking began ${date(report.captureStartedAt)}. ${report.legacyScoredAttempts} older entries that may include successes only are excluded. The full numerical data is available in JSON.")
        Spacer(Modifier.height(10.dp))
        note(if (ar) "المساعدة حسب ولي الأمر: ${task.support.independent} مستقلة، ${task.support.prompted} بتوجيه، ${task.support.assisted} بمساعدة، ${task.support.notRecorded} غير مسجلة." else "Caregiver-reported support: ${task.support.independent} independent, ${task.support.prompted} prompted, ${task.support.assisted} assisted, ${task.support.notRecorded} not recorded.")
    }
    Spacer(Modifier.height(20.dp))
}
