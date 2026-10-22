"use client";
import { useEffect, useRef, useState } from "react";
import { FileChartColumn, Printer, Upload, X } from "lucide-react";
import { api } from "@/lib/browser";
import { reportSchema } from "@/lib/report";
import type { Report } from "@/lib/store";
import type { Lang } from "./Shell";

export function Reports({ lang }: { lang: Lang }) {
  const ar = lang === "ar",
    [reports, setReports] = useState<Report[]>([]),
    [selected, setSelected] = useState<Report | null>(null),
    [error, setError] = useState(""),
    [busy, setBusy] = useState(false);
  const input = useRef<HTMLInputElement>(null);
  useEffect(() => {
    api<Report[]>("reports")
      .then(setReports)
      .catch((e) => setError(e.message));
  }, []);
  async function upload(file: File | undefined) {
    if (!file) return;
    setBusy(true);
    setError("");
    try {
      if (file.size > 65536)
        throw new Error(
          ar ? "الملف كبير جداً" : "Report exceeds the 64 KB limit",
        );
      const parsed = reportSchema.safeParse(JSON.parse(await file.text()));
      if (!parsed.success)
        throw new Error(
          ar
            ? "صيغة التقرير غير صحيحة. التسجيلات والحقول الإضافية غير مقبولة."
            : "Invalid report format. Recordings and extra fields are not accepted.",
        );
      await api("reports", parsed.data);
      setReports(await api<Report[]>("reports"));
    } catch (e) {
      setError((e as Error).message);
    } finally {
      setBusy(false);
      if (input.current) input.current.value = "";
    }
  }
  return (
    <section className="report-library">
      <div className="report-heading">
        <div>
          <div className="eyebrow">
            {ar ? "ملخصات الممارسة" : "PRACTICE SUMMARIES"}
          </div>
          <h2>{ar ? "تقاريرك، بوضوح." : "Your reports, clearly."}</h2>
        </div>
        <button
          disabled={busy}
          className="button quiet small"
          onClick={() => input.current?.click()}
        >
          <Upload size={15} />
          {ar ? "رفع تقرير JSON" : "Upload report JSON"}
        </button>
        <input
          ref={input}
          type="file"
          accept="application/json,.json"
          hidden
          onChange={(e) => upload(e.target.files?.[0])}
        />
      </div>
      <p>
        {ar
          ? "صدّر ملخصاً من تطبيق Android، ثم ارفعه بإرادتك إلى حسابك. تُحفظ الملخصات فقط؛ لا ترفع تسجيلات أو أسماء أو ملاحظات خاصة."
          : "Export a summary from Android, then choose to upload it to your account. Only summaries are stored; do not upload recordings, names, or private notes."}
      </p>
      {error && (
        <p role="alert" className="error">
          {error}
        </p>
      )}
      {reports.length === 0 ? (
        <div className="report-empty">
          <FileChartColumn size={23} />
          {ar ? "لم يتم رفع تقارير بعد." : "No reports uploaded yet."}
        </div>
      ) : (
        <div className="report-rows">
          {reports.map((report) => (
            <button key={report.id} onClick={() => setSelected(report)}>
              <FileChartColumn size={18} />
              <span>
                <b>{ar ? "ملخص ممارسة" : "Practice summary"}</b>
                <small>
                  {new Date(report.generatedAt).toLocaleDateString(
                    ar ? "ar-EG" : "en",
                  )}{" "}
                  · {report.summary.practice.recordedAttempts}{" "}
                  {ar ? "محاولة" : "attempts"}
                </small>
              </span>
              <code dir="ltr">{report.subjectId.slice(0, 8)}…</code>
            </button>
          ))}
        </div>
      )}
      {selected && (
        <article className="report-detail">
          <div className="report-heading">
            <h2>{ar ? "ملخص تقدم الممارسة" : "Practice progress summary"}</h2>
            <div>
              <button
                className="icon-button"
                onClick={() => window.print()}
                aria-label={ar ? "طباعة" : "Print report"}
              >
                <Printer size={16} />
              </button>
              <button
                className="icon-button"
                onClick={() => setSelected(null)}
                aria-label={ar ? "إغلاق" : "Close report"}
              >
                <X size={16} />
              </button>
            </div>
          </div>
          <p className="report-subject">
            {ar ? "معرف الطفل المستعار" : "Pseudonymous subject"}:{" "}
            <code dir="ltr">{selected.subjectId}</code>
          </p>
          <div className="report-metrics">
            <div>
              <b>{selected.summary.practice.recordedAttempts}</b>
              <span>{ar ? "محاولات مسجلة" : "Recorded attempts"}</span>
            </div>
            <div>
              <b>{selected.summary.practice.activeDays}</b>
              <span>{ar ? "أيام نشطة" : "Active days"}</span>
            </div>
            <div>
              <b>
                {selected.summary.practice.meanMatchScore?.toFixed(1) ?? "—"}
              </b>
              <span>{ar ? "متوسط مطابقة النص" : "Mean text-match score"}</span>
            </div>
          </div>
          <p className="notice">
            {ar
              ? "هذه بيانات ممارسة، وليست تشخيصاً. قد يشمل السجل القديم المحاولات الناجحة فقط. تعدادات المهام تصف الحالة الحالية."
              : selected.summary.historyCaveat}
          </p>
          <div className="report-table">
            <table>
              <caption>
                {ar ? "نشاط يومي بتوقيت UTC" : "Daily activity · UTC"}
              </caption>
              <thead>
                <tr>
                  <th>{ar ? "التاريخ" : "Date"}</th>
                  <th>{ar ? "المحاولات" : "Attempts"}</th>
                  <th>{ar ? "متوسط المطابقة" : "Mean match"}</th>
                </tr>
              </thead>
              <tbody>
                {[...selected.summary.practice.daily]
                  .sort((a, b) => b.date.localeCompare(a.date))
                  .map((day) => (
                    <tr key={day.date}>
                      <td>{day.date}</td>
                      <td>{day.recordedAttempts}</td>
                      <td>{day.meanMatchScore.toFixed(1)}</td>
                    </tr>
                  ))}
              </tbody>
            </table>
          </div>
          <div className="task-summary">
            {Object.entries(selected.summary.tasks).map(([key, value]) => (
              <span key={key}>
                {
                  (
                    {
                      total: ar ? "الإجمالي" : "Total",
                      approved: ar ? "معتمدة" : "Approved",
                      pending: ar ? "معلقة" : "Pending",
                      submitted: ar ? "مقدمة" : "Submitted",
                      unable: ar ? "غير مكتملة" : "Unable",
                      rejected: ar ? "مرفوضة" : "Rejected",
                    } as Record<string, string>
                  )[key]
                }
                <b>{value}</b>
              </span>
            ))}
          </div>
        </article>
      )}
    </section>
  );
}
