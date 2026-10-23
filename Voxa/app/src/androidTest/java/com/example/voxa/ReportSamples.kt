package com.example.voxa

import com.example.voxa.data.*
import com.example.voxa.data.license.*
import java.time.*

/** Fictional observations used to render the exact same Android and web report. */
object ReportSamples {
    private fun time(day: Int, hour: Int = 10) = Instant.parse("2026-10-${day.toString().padStart(2, '0')}T${hour.toString().padStart(2, '0')}:00:00Z").toEpochMilli()
    fun review(): ProgressReview {
        val rows = mutableListOf<PracticeObservation>()
        fun trials(code: String, language: String, dates: List<Int>, perDay: Int, matches: Int) {
            dates.forEach { date -> repeat(perDay) { index -> rows.add(PracticeObservation(profileId = 1, targetCode = code,
                language = language, outcome = if (index < matches) PracticeOutcome.SCORED else PracticeOutcome.UNRECOGNIZED,
                matched = index < matches, timestamp = time(date) + index * 60_000L)) } }
        }
        trials("core_water", "ar", listOf(18,20,22,23), 6, 4)
        trials("core_water", "ar", listOf(11,13,15,16), 6, 3)
        trials("core_milk", "ar", listOf(19,21), 6, 3)
        trials("core_milk", "ar", listOf(12,14), 6, 4)
        trials("core_bread", "en", listOf(20,22), 4, 3)
        trials("core_bread", "en", listOf(13,15), 4, 2)
        trials("personal_1", "ar", listOf(21,23), 3, 1)
        rows.add(PracticeObservation(profileId = 1, targetCode = "core_water", language = "ar", outcome = PracticeOutcome.NO_SPEECH, timestamp = time(23)))
        rows.add(PracticeObservation(profileId = 1, targetCode = "core_water", language = "ar", outcome = PracticeOutcome.NO_SPEECH, timestamp = time(22)))
        rows.add(PracticeObservation(profileId = 1, targetCode = "core_water", language = "ar", outcome = PracticeOutcome.TECHNICAL, timestamp = time(23)))
        val events = mutableListOf<TaskObservation>()
        fun event(task: Long, day: Int, hour: Int, from: String?, to: String, support: String = "notRecorded") {
            events.add(TaskObservation(id = events.size + 1L, profileId = 1, taskId = task, fromStatus = from, toStatus = to, support = support, timestamp = time(day, hour)))
        }
        for (task in 1L..5L) event(task, 17 + task.toInt(), 8, null, "pending")
        event(1,20,8,"pending","submitted"); event(1,20,10,"submitted","approved","independent")
        event(2,21,8,"pending","submitted"); event(2,22,10,"submitted","approved","prompted")
        event(3,22,12,"pending","submitted"); event(3,23,10,"submitted","approved","assisted")
        event(4,23,9,"pending","submitted"); event(5,23,9,"pending","unable")
        event(6,8,8,null,"pending"); event(6,18,8,"pending","submitted"); event(6,19,8,"submitted","approved")
        val quests = (1L..5L).map { QuestEntity(id = it, profileId = 1, title = "PRIVATE TASK TITLE", description = "PRIVATE NOTE", icon = "", points = 3,
            status = when (it) { 4L -> QuestStatus.SUBMITTED; 5L -> QuestStatus.UNABLE; else -> QuestStatus.APPROVED }) }
        val history = rows.filter { it.outcome == PracticeOutcome.SCORED }.map { PracticeStats(profileId = 1, word = "PRIVATE PHRASE", score = 100, stars = 5, timestamp = it.timestamp) } +
            (1..3).map { PracticeStats(profileId = 1, word = "PRIVATE LEGACY", score = 100, stars = 5, timestamp = time(20)) }
        return ProgressAnalytics.build("77777777-7777-4777-8777-777777777777", "88888888-8888-4888-8888-888888888888", time(23,12),
            ZoneId.of("Africa/Cairo"), 7, time(1), rows, events, quests, history)
    }
}
