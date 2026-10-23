package com.example.voxa.data.license

import java.time.Instant

data class ReviewHighlight(val title: String, val detail: String)

/** At most three observations to discuss, derived from the same export snapshot. */
object ReviewHighlights {
    fun build(report: ProgressReview, ar: Boolean): List<ReviewHighlight> {
        val current = report.practice.current
        val covered = !Instant.parse(report.captureStartedAt).isAfter(Instant.parse(report.period.previousStart))
        val eligible = if (!covered) emptyList() else report.practice.targets.filter {
            it.code != "other" && it.current.usable >= 5 && it.previous.usable >= 5 &&
                it.current.activeDays >= 2 && it.previous.activeDays >= 2
        }
        val highlights = mutableListOf<ReviewHighlight>()
        fun label(target: ReportTarget) = "${ProgressAnalytics.targetName(target.code, ar)} (${if (target.language == "ar") { if (ar) "العربية" else "Arabic" } else { if (ar) "الإنجليزية" else "English" }})"
        fun change(target: ReportTarget, up: Boolean) = ReviewHighlight(
            if (up) { if (ar) "مطابقات أكثر في التطبيق" else "More app matches" }
            else { if (ar) "نقطة للنقاش في المتابعة" else "Worth discussing" },
            if (ar) "${label(target)}: طابق التطبيق ${target.current.matched} من ${target.current.usable}، مقابل ${target.previous.matched} من ${target.previous.usable} سابقاً."
            else "${label(target)}: ${target.current.matched} of ${target.current.usable} matched, ${if (up) "up" else "down"} from ${target.previous.matched} of ${target.previous.usable}.")
        val captures = current.captures
        val captureTrouble = captures > 0 && (current.noSpeech + current.technical).toDouble() / captures >= .25
        if (captureTrouble) highlights.add(ReviewHighlight(
            if (ar) "راجع ظروف التسجيل" else "Check recording conditions",
            if (ar) "لم يُلتقط كلام أو حدث عطل في ${current.noSpeech + current.technical} من $captures تسجيلات. افحص الميكروفون وجرب مكاناً أهدأ."
            else "${current.noSpeech + current.technical} of $captures captures had no speech or a technical issue. Check the microphone and try a quieter setting."))
        else {
            eligible.minByOrNull { it.current.rate!! - it.previous.rate!! }?.takeIf { it.current.rate!! < it.previous.rate!! }?.let { highlights.add(change(it, false)) }
            eligible.maxByOrNull { it.current.rate!! - it.previous.rate!! }?.takeIf { it.current.rate!! > it.previous.rate!! }?.let { highlights.add(change(it, true)) }
        }
        if (highlights.isEmpty()) {
            val focus = report.practice.targets.filter { it.code != "other" && it.current.usable >= 5 && it.current.activeDays >= 2 }
                .minByOrNull { it.current.rate ?: 101.0 }?.takeIf { it.current.rate!! < 100 }
            if (eligible.isNotEmpty()) highlights.add(ReviewHighlight(
                if (ar) "استجابة متقاربة" else "Similar app responses",
                if (ar) "لم تتغير معدلات المطابقة للأهداف القابلة للمقارنة. استمر في متابعة الأهداف نفسها."
                else "Match rates are unchanged on comparable targets. Keep tracking the same words."))
            else if (focus != null) highlights.add(ReviewHighlight(
                if (ar) "هدف للمتابعة" else "A target to follow up",
                if (ar) "${label(focus)}: طابق التطبيق ${focus.current.matched} من ${focus.current.usable}. لا توجد مقارنة سابقة كافية بعد."
                else "${label(focus)}: ${focus.current.matched} of ${focus.current.usable} matched. There isn't enough earlier data to compare yet."))
            else highlights.add(ReviewHighlight(
                if (ar) "جارٍ بناء صورة أولية" else "Building a starting picture",
                if (current.usable == 0) { if (ar) "لم تُسجل ممارسة بالتتبع الجديد خلال هذه الفترة. ابدأ بجلسة قصيرة." else "No practice from the new tracking in this period. Start with a short session." }
                else { if (ar) "كرر الكلمات واللغة نفسيهما حتى تتضح التغيرات في المراجعة المقبلة." else "Repeat the same words and language so a later review can show changes." }))
        }
        val tasks = report.tasks
        if (tasks.awaitingReview > 0) highlights.add(ReviewHighlight(
            if (ar) "مهام تنتظر تأكيدك: ${tasks.awaitingReview}" else if (tasks.awaitingReview == 1) "1 task needs your review" else "${tasks.awaitingReview} tasks need your review",
            if (ar) "أكد إتمام المهام وسجل المساعدة المطلوبة."
            else "Confirm completion and note any help needed."))
        else if (tasks.support.independent + tasks.support.prompted + tasks.support.assisted > 0) {
            val known = tasks.support.independent + tasks.support.prompted + tasks.support.assisted
            val helped = tasks.support.prompted + tasks.support.assisted
            highlights.add(ReviewHighlight(
                if (ar) "المساعدة في المنزل" else "Support at home",
                if (ar) "حسب ولي الأمر، لزمت المساعدة في $helped من $known مهام سُجل فيها مستوى المساعدة."
                else "The caregiver reported help on $helped of $known tasks with a recorded support level."))
        }
        return highlights.take(3)
    }
}
