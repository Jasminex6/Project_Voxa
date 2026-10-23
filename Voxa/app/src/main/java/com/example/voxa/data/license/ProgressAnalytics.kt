package com.example.voxa.data.license

import com.example.voxa.data.*
import java.time.*

data class ReportCounts(val scored: Int, val matched: Int, val unrecognized: Int, val noSpeech: Int, val technical: Int, val activeDays: Int) {
    val usable: Int get() = scored + unrecognized
    val captures: Int get() = usable + noSpeech + technical
    val rate: Double? get() = if (usable == 0) null else matched * 100.0 / usable
}
data class ReportPeriod(val days: Int, val start: String, val end: String, val previousStart: String, val previousEnd: String, val timeZone: String)
data class ReportDay(val date: String, val counts: ReportCounts)
data class ReportTarget(val code: String, val language: String, val current: ReportCounts, val previous: ReportCounts)
data class ReportPractice(val current: ReportCounts, val previous: ReportCounts, val daily: List<ReportDay>, val targets: List<ReportTarget>, val omittedTargets: Int)
data class ReportSupport(val independent: Int, val prompted: Int, val assisted: Int, val notRecorded: Int)
data class ReportTaskSnapshot(val total: Int, val pending: Int, val submitted: Int, val unable: Int, val approved: Int, val rejected: Int)
data class ReportTasks(val assigned: Int, val cohortApproved: Int, val submitted: Int, val approved: Int, val unable: Int,
    val support: ReportSupport, val awaitingReview: Int, val timedAwaiting: Int, val oldestAwaitingHours: Double?,
    val reviewHoursMedian: Double?, val reviewSamples: Int, val snapshot: ReportTaskSnapshot)
data class ProgressReview(val schemaVersion: Int = 2, val reportId: String, val subjectId: String, val generatedAt: String,
    val measure: String = "voxa_app_match_v1", val period: ReportPeriod, val captureStartedAt: String,
    val legacyScoredAttempts: Int, val practice: ReportPractice, val tasks: ReportTasks)
data class ReportComparison(val percentagePoints: Double?, val targets: Int, val currentTrials: Int, val previousTrials: Int)

/** Behavioral activity summaries. No diagnosis, pronunciation rating, or inferred independence. */
object ProgressAnalytics {
    fun build(reportId: String, subjectId: String, now: Long, zone: ZoneId, days: Int, captureStartedAt: Long,
        observations: List<PracticeObservation>, taskEvents: List<TaskObservation>, quests: List<QuestEntity>, history: List<PracticeStats>): ProgressReview {
        require(days in listOf(7, 30))
        val end = Instant.ofEpochMilli(now).atZone(zone)
        val start = end.toLocalDate().minusDays(days - 1L).atStartOfDay(zone)
        val previousStart = start.minusDays(days.toLong())
        val previousEnd = end.minusDays(days.toLong())
        fun within(at: Long, a: ZonedDateTime, b: ZonedDateTime) = at >= a.toInstant().toEpochMilli() && at <= b.toInstant().toEpochMilli()
        val current = observations.filter { within(it.timestamp, start, end) }
        val previous = observations.filter { within(it.timestamp, previousStart, previousEnd) }
        fun counts(rows: List<PracticeObservation>): ReportCounts = ReportCounts(
            rows.count { it.outcome == PracticeOutcome.SCORED }, rows.count { it.outcome == PracticeOutcome.SCORED && it.matched },
            rows.count { it.outcome == PracticeOutcome.UNRECOGNIZED }, rows.count { it.outcome == PracticeOutcome.NO_SPEECH },
            rows.count { it.outcome == PracticeOutcome.TECHNICAL },
            rows.filter { it.outcome in listOf(PracticeOutcome.SCORED, PracticeOutcome.UNRECOGNIZED) }
                .map { Instant.ofEpochMilli(it.timestamp).atZone(zone).toLocalDate() }.distinct().size)
        val keys = (current + previous).map { it.targetCode to it.language }.distinct()
            .sortedWith(compareByDescending<Pair<String, String>> { key -> current.count { it.targetCode == key.first && it.language == key.second } }.thenBy { it.first }.thenBy { it.second })
        fun rowsFor(rows: List<PracticeObservation>, key: Pair<String, String>) = rows.filter { it.targetCode == key.first && it.language == key.second }
        val shown = keys.take(40)
        val targets = shown.map { ReportTarget(it.first, it.second, counts(rowsFor(current, it)), counts(rowsFor(previous, it))) }.toMutableList()
        if (keys.size > shown.size) {
            val remaining = keys.drop(40).toSet()
            targets.add(ReportTarget("other", "und", counts(current.filter { (it.targetCode to it.language) in remaining }), counts(previous.filter { (it.targetCode to it.language) in remaining })))
        }
        val daily = (0 until days).map { offset ->
            val date = start.toLocalDate().plusDays(offset.toLong())
            ReportDay(date.toString(), counts(current.filter { Instant.ofEpochMilli(it.timestamp).atZone(zone).toLocalDate() == date }))
        }
        val allEvents = taskEvents.filter { it.timestamp <= now }.sortedWith(compareBy<TaskObservation> { it.timestamp }.thenBy { it.id })
        val periodEvents = allEvents.filter { within(it.timestamp, start, end) }
        val assigned = periodEvents.filter { it.fromStatus == null && it.toStatus == "pending" }.map { it.taskId }.toSet()
        val lastState = allEvents.groupBy { it.taskId }.mapValues { it.value.last() }
        val approvals = periodEvents.filter { it.toStatus == "approved" }.groupBy { it.taskId }.map { it.value.last() }
        val reviewHours = approvals.mapNotNull { approval ->
            allEvents.lastOrNull { it.taskId == approval.taskId && it.toStatus == "submitted" && it.timestamp <= approval.timestamp }
                ?.let { (approval.timestamp - it.timestamp) / 3_600_000.0 }
        }.sorted()
        val median = if (reviewHours.isEmpty()) null else if (reviewHours.size % 2 == 1) reviewHours[reviewHours.size / 2]
            else (reviewHours[reviewHours.size / 2 - 1] + reviewHours[reviewHours.size / 2]) / 2
        val awaiting = quests.filter { it.status == QuestStatus.SUBMITTED }
        val ages = awaiting.mapNotNull { quest -> lastState[quest.id]?.takeIf { it.toStatus == "submitted" }?.let { (now - it.timestamp) / 3_600_000.0 } }
        val tasks = ReportTasks(
            assigned.size, assigned.count { lastState[it]?.toStatus == "approved" },
            periodEvents.filter { it.toStatus == "submitted" }.map { it.taskId }.distinct().size, approvals.size,
            periodEvents.filter { it.toStatus == "unable" }.map { it.taskId }.distinct().size,
            ReportSupport(approvals.count { it.support == "independent" }, approvals.count { it.support == "prompted" },
                approvals.count { it.support == "assisted" }, approvals.count { it.support == "notRecorded" }),
            awaiting.size, ages.size, ages.maxOrNull(), median, reviewHours.size,
            ReportTaskSnapshot(quests.size, quests.count { it.status == QuestStatus.PENDING }, awaiting.size,
                quests.count { it.status == QuestStatus.UNABLE }, quests.count { it.status == QuestStatus.APPROVED }, quests.count { it.status == QuestStatus.REJECTED }))
        return ProgressReview(reportId = reportId, subjectId = subjectId, generatedAt = end.toInstant().toString(),
            period = ReportPeriod(days, start.toInstant().toString(), end.toInstant().toString(), previousStart.toInstant().toString(), previousEnd.toInstant().toString(), zone.id),
            captureStartedAt = Instant.ofEpochMilli(captureStartedAt).toString(),
            legacyScoredAttempts = (history.count { within(it.timestamp, start, end) } - current.count { it.outcome == PracticeOutcome.SCORED }).coerceAtLeast(0),
            practice = ReportPractice(counts(current), counts(previous), daily, targets, (keys.size - shown.size).coerceAtLeast(0)), tasks = tasks)
    }

    /** Equal-weight comparison of the same target/language, with repeated observations in both periods. */
    fun comparison(report: ProgressReview): ReportComparison {
        if (Instant.parse(report.captureStartedAt).isAfter(Instant.parse(report.period.previousStart))) return ReportComparison(null, 0, 0, 0)
        val comparable = report.practice.targets.filter { it.code != "other" &&
            it.current.usable >= 5 && it.previous.usable >= 5 && it.current.activeDays >= 2 && it.previous.activeDays >= 2 }
        return ReportComparison(if (comparable.isEmpty()) null else comparable.map { it.current.rate!! - it.previous.rate!! }.average(),
            comparable.size, comparable.sumOf { it.current.usable }, comparable.sumOf { it.previous.usable })
    }

    fun targetName(code: String, ar: Boolean): String = when (code) {
        "core_water" -> if (ar) "مياه" else "Water"
        "core_milk" -> if (ar) "لبن" else "Milk"
        "core_bread" -> if (ar) "عيش" else "Bread"
        "core_help" -> if (ar) "مساعدة" else "Help"
        "core_mom" -> if (ar) "ماما" else "Mom"
        "core_dad" -> if (ar) "بابا" else "Dad"
        "core_food" -> if (ar) "أكل" else "Food"
        "core_bathroom" -> if (ar) "حمام" else "Bathroom"
        "core_sleep" -> if (ar) "نوم" else "Sleep"
        "other" -> if (ar) "أهداف أخرى" else "Other targets"
        else -> if (ar) "هدف شخصي ${code.removePrefix("personal_")}" else "Personal target ${code.removePrefix("personal_")}"
    }
}
