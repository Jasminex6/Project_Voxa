package com.example.voxa.data.license

import android.content.Context
import android.content.Intent
import androidx.core.content.FileProvider
import androidx.room.withTransaction
import com.example.voxa.data.VoxaDatabase
import com.google.gson.GsonBuilder
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.File
import java.time.ZoneId
import java.util.UUID

/** Explicit export DTO: no names, audio, transcripts, custom phrases, contacts, or task notes. */
object ProgressReport {
    val gson = GsonBuilder().serializeNulls().create()
    private val coreCodes = setOf("core_water", "core_milk", "core_bread", "core_help", "core_mom", "core_dad", "core_food", "core_bathroom", "core_sleep")

    @Synchronized fun targetCode(context: Context, profileId: Long, word: String, supplied: String?): String {
        if (supplied in coreCodes) return supplied!!
        val prefs = context.getSharedPreferences("voxa_report_targets", Context.MODE_PRIVATE)
        val digest = java.security.MessageDigest.getInstance("SHA-256").digest(word.toByteArray()).joinToString("") { "%02x".format(it) }
        val key = "$profileId:$digest"
        prefs.getString(key, null)?.let { return it }
        val next = prefs.getInt("next:$profileId", 1)
        val code = "personal_$next"
        check(prefs.edit().putString(key, code).putInt("next:$profileId", next + 1).commit())
        return code
    }

    suspend fun review(context: Context, profileId: Long, days: Int = 7): ProgressReview = withContext(Dispatchers.IO) {
        val db = VoxaDatabase.getDatabase(context)
        val prefs = context.getSharedPreferences("voxa_report_ids", Context.MODE_PRIVATE)
        val subject = prefs.getString("profile_$profileId", null) ?: UUID.randomUUID().toString().also {
            check(prefs.edit().putString("profile_$profileId", it).commit())
        }
        val now = System.currentTimeMillis()
        val since = context.getSharedPreferences("voxa_report_tracking", Context.MODE_PRIVATE).getLong("startedAt", now)
        db.withTransaction {
            ProgressAnalytics.build(UUID.randomUUID().toString(), subject, now, ZoneId.systemDefault(), days, since,
                db.voxaDao().getPracticeObservations(profileId), db.questDao().getTaskObservations(profileId),
                db.questDao().getQuestsForProfile(profileId), db.voxaDao().getPracticeStatsForProfile(profileId))
        }
    }

    suspend fun build(context: Context, profileId: Long, days: Int = 7): JSONObject = JSONObject(gson.toJson(review(context, profileId, days)))

    fun share(context: Context, report: JSONObject, pdf: Boolean) {
        val manager = LicenseManager.get(context); manager.refresh()
        check(manager.license.value.canExportProgress) { "Family Pro is required to export progress" }
        val review = gson.fromJson(report.toString(), ProgressReview::class.java)
        check(review.schemaVersion == 2)
        val dir = File(context.cacheDir, "reports").apply { mkdirs() }
        val reportId = UUID.fromString(review.reportId)
        val file = File(dir, "voxa-review-$reportId.${if (pdf) "pdf" else "json"}")
        if (pdf) ProgressPdf.write(file, review, context.resources.configuration.locales[0].language == "ar")
        else file.writeText(report.toString(2))
        val uri = FileProvider.getUriForFile(context, "${context.packageName}.reports", file)
        context.startActivity(Intent.createChooser(Intent(Intent.ACTION_SEND).apply {
            type = if (pdf) "application/pdf" else "application/json"
            putExtra(Intent.EXTRA_STREAM, uri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            clipData = android.content.ClipData.newRawUri("Voxa progress review", uri)
        }, if (context.resources.configuration.locales[0].language == "ar") "مشاركة مراجعة التقدم" else "Share progress review"))
    }
}
