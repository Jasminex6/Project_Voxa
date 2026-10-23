package com.example.voxa

import com.example.voxa.data.*
import com.example.voxa.data.license.*
import org.junit.Assert.*
import org.junit.Test
import java.time.*

class ProgressAnalyticsTest {
    private val now = Instant.parse("2026-10-23T12:00:00Z").toEpochMilli()
    private val day = 86_400_000L
    private fun build(rows: List<PracticeObservation> = emptyList(), events: List<TaskObservation> = emptyList(), quests: List<QuestEntity> = emptyList(), since: Long = now - 30 * day, history: List<PracticeStats> = emptyList()) =
        ProgressAnalytics.build("77777777-7777-4777-8777-777777777777", "88888888-8888-4888-8888-888888888888", now, ZoneId.of("Africa/Cairo"), 7, since, rows, events, quests, history)
    private fun row(code: String, daysAgo: Int, outcome: PracticeOutcome = PracticeOutcome.SCORED, matched: Boolean = true, language: String = "ar") =
        PracticeObservation(profileId = 1, targetCode = code, language = language, outcome = outcome, matched = matched, timestamp = now - daysAgo * day)

    @Test fun technicalFailuresAndNoSpeechAreNotChildFailures() {
        val p = build(listOf(row("core_water", 1), row("core_water", 1, PracticeOutcome.UNRECOGNIZED, false),
            row("core_water", 0, PracticeOutcome.NO_SPEECH, false), row("core_water", 0, PracticeOutcome.TECHNICAL, false))).practice.current
        assertEquals(2, p.usable); assertEquals(50.0, p.rate!!, .001); assertEquals(1, p.activeDays)
        assertEquals(1, p.noSpeech); assertEquals(1, p.technical)
    }

    @Test fun changingTargetMixCannotInventADeterioration() {
        fun trials(code: String, number: Int, matched: Boolean, prior: Boolean) = (0 until number).map {
            row(code, (if (prior) 8 else 1) + it % 2, matched = matched)
        }
        val report = build(trials("core_water", 10, true, false) + trials("core_bread", 30, false, false) +
            trials("core_water", 30, true, true) + trials("core_bread", 10, false, true))
        assertEquals(25.0, report.practice.current.rate!!, .001)
        assertEquals(75.0, report.practice.previous.rate!!, .001)
        assertEquals(0.0, ProgressAnalytics.comparison(report).percentagePoints!!, .001)
        assertEquals(2, ProgressAnalytics.comparison(report).targets)
    }

    @Test fun differentLanguagesAreNeverComparedAsOneTarget() {
        val report = build((0..5).map { row("core_water", 1 + it % 2, language = "ar") } +
            (0..5).map { row("core_water", 8 + it % 2, language = "en") })
        assertNull(ProgressAnalytics.comparison(report).percentagePoints)
    }

    @Test fun sparseOrPartialCoverageDoesNotProduceProgressClaims() {
        val rows = (0..5).map { row("core_water", 1 + it % 2) } + (0..5).map { row("core_water", 8 + it % 2) }
        assertNull(ProgressAnalytics.comparison(build(rows, since = now - day)).percentagePoints)
        assertNull(ProgressAnalytics.comparison(build(listOf(row("core_water", 1), row("core_water", 8)))).percentagePoints)
    }

    @Test fun legacySuccessOnlyHistoryDoesNotBecomeOneHundredPercentSuccess() {
        val old = (0..3).map { PracticeStats(profileId = 1, word = "PRIVATE", score = 100, stars = 5, timestamp = now - day) }
        val report = build(history = old)
        assertEquals(4, report.legacyScoredAttempts)
        assertNull(report.practice.current.rate)
        assertEquals(0, report.practice.current.usable)
    }

    @Test fun cohortCompletionIsSeparateFromApprovalsOfOlderTasks() {
        fun event(id: Long, ago: Int, from: String?, to: String, support: String = "notRecorded") = TaskObservation(profileId = 1, taskId = id, fromStatus = from, toStatus = to, support = support, timestamp = now - ago * day)
        val report = build(events = listOf(event(1, 2, null, "pending"), event(2, 1, null, "pending"),
            event(1, 1, "submitted", "approved", "independent"), event(99, 1, "submitted", "approved")))
        assertEquals(2, report.tasks.assigned); assertEquals(1, report.tasks.cohortApproved)
        assertEquals(2, report.tasks.approved); assertEquals(1, report.tasks.support.independent)
        assertEquals(1, report.tasks.support.notRecorded)
    }

    @Test fun reviewTimeMeasuresCaregiverDelayAndKeepsUnknownTimesUnknown() {
        val events = listOf(TaskObservation(profileId = 1, taskId = 1, fromStatus = "pending", toStatus = "submitted", timestamp = now - 5 * 3_600_000L),
            TaskObservation(profileId = 1, taskId = 1, fromStatus = "submitted", toStatus = "approved", timestamp = now - 3 * 3_600_000L),
            TaskObservation(profileId = 1, taskId = 2, fromStatus = "pending", toStatus = "submitted", timestamp = now - 4 * 3_600_000L))
        val quests = listOf(QuestEntity(id = 2, profileId = 1, title = "PRIVATE", description = "PRIVATE", icon = "", points = 1, status = QuestStatus.SUBMITTED),
            QuestEntity(id = 3, profileId = 1, title = "PRIVATE", description = "PRIVATE", icon = "", points = 1, status = QuestStatus.SUBMITTED))
        val tasks = build(events = events, quests = quests).tasks
        assertEquals(2.0, tasks.reviewHoursMedian!!, .001); assertEquals(1, tasks.reviewSamples)
        assertEquals(2, tasks.awaitingReview); assertEquals(1, tasks.timedAwaiting)
        assertEquals(4.0, tasks.oldestAwaitingHours!!, .001)
    }

    @Test fun emptyPeriodsHaveNullRatesAndZeroPaddedDays() {
        val report = build()
        assertEquals(7, report.practice.daily.size); assertNull(report.practice.current.rate)
        assertEquals("2026-10-17", report.practice.daily.first().date)
        assertEquals("2026-10-23", report.practice.daily.last().date)
        assertNull(ProgressAnalytics.comparison(report).percentagePoints)
    }

    @Test fun excessTargetsAreRolledUpWithoutLosingAttempts() {
        val report = build((1..50).map { row("personal_$it", 1) })
        assertEquals(50, report.practice.current.usable)
        assertEquals(41, report.practice.targets.size)
        assertEquals(10, report.practice.omittedTargets)
        assertEquals(50, report.practice.targets.sumOf { it.current.usable })
        assertEquals("other", report.practice.targets.last().code)
    }

    @Test fun highlightsChangeWithObservedCountsAndStayShort() {
        fun trials(code: String, daysAgo: Int, matches: Int) = (0..9).map {
            row(code, daysAgo + it % 2, if(it<matches) PracticeOutcome.SCORED else PracticeOutcome.UNRECOGNIZED, it<matches)
        }
        val rows=trials("core_water",0,8)+trials("core_water",8,4)+trials("core_milk",0,2)+trials("core_milk",8,6)
        val queued=QuestEntity(id=1,profileId=1,title="PRIVATE",description="PRIVATE",icon="",points=1,status=QuestStatus.SUBMITTED)
        val insights=ReviewHighlights.build(build(rows,quests=listOf(queued)),false)
        assertEquals(3,insights.size)
        assertTrue(insights[0].detail.contains("Milk (Arabic): 2 of 10"))
        assertTrue(insights[1].detail.contains("Water (Arabic): 8 of 10"))
        assertEquals("1 task needs your review",insights[2].title)
        val changed=ReviewHighlights.build(build(trials("core_water",0,6)+trials("core_water",8,4)),false)
        assertTrue(changed.single().detail.contains("6 of 10"))
        assertFalse(insights.toString().contains("PRIVATE"))
    }

    @Test fun highlightsDoNotInventAComparisonFromSparseOrLegacyData() {
        val sparse=ReviewHighlights.build(build(listOf(row("core_water",1),row("core_water",8))),false)
        assertEquals("Building a starting picture",sparse.single().title)
        val empty=ReviewHighlights.build(build(),false)
        assertTrue(empty.single().detail.startsWith("No practice from the new tracking"))
    }

    @Test fun captureProblemsTakePriorityOverPracticeTrends() {
        val rows=(0..5).map {row("core_water",it%2,PracticeOutcome.TECHNICAL,false)}+row("core_water",1)
        val insights=ReviewHighlights.build(build(rows),false)
        assertEquals("Check recording conditions",insights.single().title)
        assertTrue(insights.single().detail.startsWith("6 of 7 captures"))
    }
}
