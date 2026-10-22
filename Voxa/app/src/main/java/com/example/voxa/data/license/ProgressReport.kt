package com.example.voxa.data.license

import android.content.Context
import android.content.Intent
import android.graphics.Paint
import android.graphics.pdf.PdfDocument
import androidx.core.content.FileProvider
import com.example.voxa.data.VoxaDatabase
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.time.Instant
import java.time.ZoneOffset
import java.util.UUID

/** A report allowlist: no recordings, embeddings, templates, contacts, or free-text notes. */
object ProgressReport {
    suspend fun build(context: Context, profileId: Long): JSONObject = withContext(Dispatchers.IO) {
        val db = VoxaDatabase.getDatabase(context)
        val attempts = db.voxaDao().getPracticeStatsForProfile(profileId)
        val quests = db.questDao().getQuestsForProfile(profileId)
        val prefs = context.getSharedPreferences("voxa_report_ids", Context.MODE_PRIVATE)
        val subject = prefs.getString("profile_$profileId", null) ?: UUID.randomUUID().toString().also {
            prefs.edit().putString("profile_$profileId", it).commit()
        }
        val daily = attempts.groupBy { Instant.ofEpochMilli(it.timestamp).atZone(ZoneOffset.UTC).toLocalDate().toString() }
        JSONObject().apply {
            put("schemaVersion", 1)
            put("reportId", UUID.randomUUID().toString())
            put("subjectId", subject)
            put("generatedAt", Instant.now().toString())
            put("periodStart", attempts.minOfOrNull { it.timestamp }?.let { Instant.ofEpochMilli(it).toString() } ?: JSONObject.NULL)
            put("periodEnd", attempts.maxOfOrNull { it.timestamp }?.let { Instant.ofEpochMilli(it).toString() } ?: JSONObject.NULL)
            put("measure", "speech_recognition_text_match")
            put("historyCaveat", "Older app versions recorded successful attempts only. Scores are not clinical measurements. Task counts are current status, not weekly completion history.")
            put("practice", JSONObject().apply {
                put("recordedAttempts", attempts.size)
                put("activeDays", daily.size)
                put("meanMatchScore", if (attempts.isEmpty()) JSONObject.NULL else attempts.map { it.score }.average())
                put("daily", JSONArray().apply {
                    daily.toSortedMap().forEach { (day, rows) -> put(JSONObject().put("date", day)
                        .put("recordedAttempts", rows.size).put("meanMatchScore", rows.map { it.score }.average())) }
                })
            })
            put("tasks", JSONObject().apply {
                put("total", quests.size)
                quests.groupingBy { it.status.name.lowercase() }.eachCount().forEach { (status, count) -> put(status, count) }
            })
        }
    }

    fun share(context: Context, report: JSONObject, pdf: Boolean) {
        val manager = LicenseManager.get(context); manager.refresh()
        check(manager.license.value.canExportProgress) { "Family Pro is required to export progress" }
        val dir = File(context.cacheDir, "reports").apply { mkdirs() }
        val reportId = UUID.fromString(report.getString("reportId"))
        val file = File(dir, "voxa-progress-$reportId.${if (pdf) "pdf" else "json"}")
        if (pdf) {
            val practice = report.getJSONObject("practice")
            val lines = mutableListOf("Voxa | Practice progress", "Generated: ${report.getString("generatedAt")}",
                "Subject: ${report.getString("subjectId")}", "", "Recorded attempts: ${practice.getInt("recordedAttempts")}",
                "Active days: ${practice.getInt("activeDays")}", "Mean text-match score: ${practice.opt("meanMatchScore")}",
                "", "Daily activity (UTC)")
            val daily = practice.getJSONArray("daily")
            for (i in 0 until daily.length()) {
                val row = daily.getJSONObject(i)
                lines.add("${row.getString("date")} | ${row.getInt("recordedAttempts")} attempts | mean ${"%.1f".format(java.util.Locale.US, row.getDouble("meanMatchScore"))}")
            }
            lines.addAll(listOf("", "Task status snapshot", report.getJSONObject("tasks").toString(), "",
                "Older app versions saved successful attempts only.", "Match scores are not validated clinical measurements.",
                "Task status totals do not measure weekly completion.", "No recordings, voiceprints, contacts, or task notes are included."))
            val document = PdfDocument()
            try {
                lines.chunked(40).forEachIndexed { index, chunk ->
                    val page = document.startPage(PdfDocument.PageInfo.Builder(595, 842, index + 1).create())
                    val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply { textSize = 12f; color = android.graphics.Color.rgb(20, 35, 55) }
                    chunk.forEachIndexed { row, text -> page.canvas.drawText(text, 40f, 55f + row * 18f, paint) }
                    document.finishPage(page)
                }
                file.outputStream().use { document.writeTo(it) }
            } finally { document.close() }
        } else file.writeText(report.toString(2))
        val uri = FileProvider.getUriForFile(context, "${context.packageName}.reports", file)
        val intent = Intent(Intent.ACTION_SEND).apply {
            type = if (pdf) "application/pdf" else "application/json"
            putExtra(Intent.EXTRA_STREAM, uri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            clipData = android.content.ClipData.newRawUri("Voxa progress report", uri)
        }
        context.startActivity(Intent.createChooser(intent, "Share progress report"))
    }
}
