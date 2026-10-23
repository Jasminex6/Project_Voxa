"use client";
import { reviewHighlights, usable, type ProgressReview } from "@/lib/report";
import type { Lang } from "./Shell";

export function ReportReview({
  report,
  lang,
  sample = false,
}: {
  report: ProgressReview;
  lang: Lang;
  sample?: boolean;
}) {
  const ar = lang === "ar",
    p = report.practice.current,
    t = report.tasks;
  const date = (iso: string) =>
    new Date(iso).toLocaleDateString(ar ? "ar-EG" : "en", {
      day: "numeric",
      month: "short",
      timeZone: report.period.timeZone,
    });
  return (
    <div className="report-review" dir={ar ? "rtl" : "ltr"}>
      <div className="review-title">
        <div className="eyebrow">
          {sample
            ? ar
              ? "بيانات توضيحية خيالية"
              : "FICTIONAL EXAMPLE"
            : ar
              ? "من نشاط التطبيق"
              : "FROM APP ACTIVITY"}
        </div>
        <h2>{ar ? "لمحة عن التقدم" : "Progress at a glance"}</h2>
        <p>
          {date(report.period.start)} - {date(report.period.end)}
        </p>
        <p className="review-user-id">
          <span>{ar ? "معرّف المستخدم" : "User ID"}: </span>
          <code dir="ltr">{report.subjectId}</code>
        </p>
      </div>
      <div className="review-metrics">
        <div>
          <strong dir="ltr">
            {p.activeDays} / {report.period.days}
          </strong>
          <b>{ar ? "أيام الممارسة" : "Practice days"}</b>
          <span>
            {ar ? `${usable(p)} محاولات كلام` : `${usable(p)} speech attempts`}
          </span>
        </div>
        <div>
          <strong dir="ltr">
            {t.assigned ? `${t.cohortApproved} / ${t.assigned}` : "—"}
          </strong>
          <b>{ar ? "مهام جديدة مؤكدة" : "New tasks confirmed"}</b>
          <span>
            {t.assigned
              ? ar
                ? "أكدها ولي الأمر"
                : "Confirmed by caregiver"
              : ar
                ? "لم تُضف مهام في الفترة"
                : "No new assignments"}
          </span>
        </div>
      </div>
      <h3>{ar ? "أهم ما يستحق انتباهك" : "What matters this time"}</h3>
      <ol className="review-highlights">
        {reviewHighlights(report, ar).map((h, i) => (
          <li key={h.title}>
            <span aria-hidden="true">{i + 1}</span>
            <div>
              <h4>{h.title}</h4>
              <p>{h.detail}</p>
            </div>
          </li>
        ))}
      </ol>
      <p className="review-footnote">
        {ar
          ? "هذه استجابة التطبيق وتأكيدات ولي الأمر، وليست تقييماً سريرياً. لا يُصدَّر صوت أو أسماء."
          : "Based on app responses and caregiver confirmations, not a clinical assessment. No audio or names are exported."}
      </p>
      <details className="review-details" key={report.reportId}>
        <summary>{ar ? "التفاصيل" : "Details"}</summary>
        <p>
          {ar
            ? `تشمل اليوم حتى التصدير (${report.period.timeZone}). المطابقة الحالية: ${p.matched} من ${usable(p)}. لا تدخل ${p.noSpeech} محاولات دون كلام و${p.technical} أعطال في المعدل.`
            : `Includes today to export time (${report.period.timeZone}). Current app matches: ${p.matched} of ${usable(p)}. ${p.noSpeech} no-speech captures and ${p.technical} technical issues are excluded.`}
        </p>
        <p>
          {ar
            ? "المقارنات تتطلب ٥ محاولات على يومين في كل فترة للهدف واللغة نفسيهما."
            : "Comparisons need 5 attempts on 2 days per period for the same target and language."}
        </p>
        <p>
          {ar
            ? `بدأ التتبع في ${date(report.captureStartedAt)}. استُبعدت ${report.legacyScoredAttempts} نتائج قديمة قد تشمل النجاحات فقط. تتوفر البيانات الكاملة في JSON.`
            : `Tracking began ${date(report.captureStartedAt)}. ${report.legacyScoredAttempts} older entries that may include successes only are excluded. The full numerical data is available in JSON.`}
        </p>
        <p>
          {ar
            ? `المساعدة حسب ولي الأمر: ${t.support.independent} مستقلة، ${t.support.prompted} بتوجيه، ${t.support.assisted} بمساعدة، ${t.support.notRecorded} غير مسجلة.`
            : `Caregiver-reported support: ${t.support.independent} independent, ${t.support.prompted} prompted, ${t.support.assisted} assisted, ${t.support.notRecorded} not recorded.`}
        </p>
        <code>{ar ? "مرجع التقرير" : "Report reference"}: {report.reportId.slice(0, 8)}</code>
      </details>
    </div>
  );
}
