"use client";
import { useEffect, useRef, useState } from "react";
import { FileChartColumn, Printer, Upload, X } from "lucide-react";
import { api } from "@/lib/browser";
import { reportSchema, usable } from "@/lib/report";
import type { Report } from "@/lib/store";
import type { Lang } from "./Shell";
import { ReportReview } from "./ReportReview";
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
          ar ? "الملف أكبر من الحد المسموح" : "Report exceeds the 64 KB limit",
        );
      const parsed = reportSchema.safeParse(JSON.parse(await file.text()));
      if (!parsed.success)
        throw new Error(
          ar
            ? "صيغة غير صحيحة أو بيانات متناقضة. الأسماء والتسجيلات والحقول الإضافية غير مقبولة."
            : "Invalid or contradictory report. Names, recordings, and extra fields are not accepted.",
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
            {ar ? "مراجعات الممارسة والمهام" : "PRACTICE & TASK REVIEWS"}
          </div>
          <h2>{ar ? "متابعة لها معنى." : "Progress with context."}</h2>
        </div>
        <button
          className="button quiet small"
          disabled={busy}
          onClick={() => input.current?.click()}
        >
          <Upload size={15} />
          {ar ? "رفع بيانات تقرير" : "Upload report data"}
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
          ? "صدّر JSON من التطبيق ثم اختر رفعه إلى حسابك. لا أسماء أو تسجيلات أو ملاحظات خاصة."
          : "Export JSON from Voxa, then choose to upload it to your account. No names, recordings, or private notes."}
      </p>
      {error && (
        <p className="error" role="alert">
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
          {reports.map((r) => (
            <button key={r.id} onClick={() => setSelected(r)}>
              <FileChartColumn size={18} />
              <span>
                <b>
                  {r.summary.schemaVersion === 2
                    ? ar
                      ? "مراجعة الممارسة والمهام"
                      : "Practice & task review"
                    : ar
                      ? "ملخص قديم"
                      : "Legacy summary"}
                </b>
                <small>
                  {new Date(r.generatedAt).toLocaleDateString(
                    ar ? "ar-EG" : "en",
                  )}{" "}
                  ·{" "}
                  {r.summary.schemaVersion === 2
                    ? usable(r.summary.practice.current)
                    : r.summary.practice.recordedAttempts}{" "}
                  {ar ? "محاولات" : "attempts"}
                </small>
              </span>
              <code>{r.subjectId.slice(0, 8)}</code>
            </button>
          ))}
        </div>
      )}
      {selected && (
        <article className="report-detail">
          <div className="report-heading">
            <span />
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
          {selected.summary.schemaVersion === 2 ? (
            <ReportReview report={selected.summary} lang={lang} />
          ) : (
            <>
              <h2>
                {ar
                  ? "ملخص سابق، ببيانات محدودة"
                  : "Earlier summary, limited evidence"}
              </h2>
              <p className="notice">
                {ar
                  ? "السجل القديم قد يشمل النجاحات فقط. لا يدعم مقارنات موثوقة أو قياس الاستقلالية. صدّر مراجعة جديدة من الإصدار المحدّث."
                  : selected.summary.historyCaveat +
                    " Export a new review from the updated app for structured comparisons."}
              </p>
              <div className="report-metrics">
                <div>
                  <b>{selected.summary.practice.recordedAttempts}</b>
                  <span>
                    {ar ? "نتائج قديمة مسجلة" : "Older scored entries"}
                  </span>
                </div>
                <div>
                  <b>{selected.summary.practice.activeDays}</b>
                  <span>{ar ? "أيام في السجل" : "Days represented"}</span>
                </div>
              </div>
              <table>
                <thead>
                  <tr>
                    <th>{ar ? "التاريخ" : "Date"}</th>
                    <th>{ar ? "النتائج المسجلة" : "Scored entries"}</th>
                  </tr>
                </thead>
                <tbody>
                  {selected.summary.practice.daily.map((d) => (
                    <tr key={d.date}>
                      <td>{d.date}</td>
                      <td>{d.recordedAttempts}</td>
                    </tr>
                  ))}
                </tbody>
              </table>
            </>
          )}
        </article>
      )}
    </section>
  );
}
