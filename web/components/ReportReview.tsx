"use client";
import { useEffect, useState } from "react";
import {
  comparison,
  matchRate,
  targetName,
  usable,
  type ProgressReview,
} from "@/lib/report";
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
    [tab, setTab] = useState("practice"),
    [page, setPage] = useState(0);
  useEffect(() => {
    setPage(0);
    setTab("practice");
  }, [report.reportId]);
  const p = report.practice.current,
    t = report.tasks,
    c = comparison(report);
  const date = (iso: string) =>
    new Date(iso).toLocaleDateString(ar ? "ar-EG" : "en", {
      day: "numeric",
      month: "short",
      timeZone: report.period.timeZone,
    });
  const covered =
    Date.parse(report.captureStartedAt) <=
    Date.parse(report.period.previousStart);
  const max = Math.max(
    1,
    ...report.practice.daily.map(
      (d) => usable(d.counts) + d.counts.noSpeech + d.counts.technical,
    ),
  );
  const focus = report.practice.targets
    .filter(
      (x) =>
        x.code !== "other" &&
        usable(x.current) >= 5 &&
        x.current.activeDays >= 2,
    )
    .sort((a, b) => matchRate(a.current)! - matchRate(b.current)!)[0];
  const metrics = (items: [string, string][]) => (
    <div className="review-metrics">
      {items.map(([value, label]) => (
        <div key={label}>
          <strong dir="auto">{value}</strong>
          <span>{label}</span>
        </div>
      ))}
    </div>
  );
  return (
    <div className="report-review" dir={ar ? "rtl" : "ltr"}>
      <div className="review-title">
        <div>
          <div className="eyebrow">
            {sample
              ? ar
                ? "بيانات توضيحية خيالية"
                : "ILLUSTRATIVE, FICTIONAL DATA"
              : ar
                ? "مراجعة الممارسة والمهام"
                : "PRACTICE & TASK REVIEW"}
          </div>
          <h2>
            {ar
              ? "من البيانات إلى متابعة أوضح."
              : "A clearer next conversation."}
          </h2>
          <p>
            {date(report.period.start)} - {date(report.period.end)} ·{" "}
            {report.period.timeZone} ·{" "}
            {ar ? "تشمل اليوم حتى التصدير" : "includes today to export time"}
          </p>
        </div>
      </div>
      <div
        className="review-tabs"
        role="tablist"
        aria-label={ar ? "أقسام التقرير" : "Report sections"}
      >
        {[
          ["practice", ar ? "الممارسة" : "Practice"],
          ["tasks", ar ? "المهام والمساعدة" : "Tasks & help"],
          ["context", ar ? "ما يستحق المراجعة" : "Review & context"],
        ].map(([id, label]) => (
          <button
            key={id}
            id={`tab-${id}`}
            role="tab"
            aria-selected={tab === id}
            aria-controls={`panel-${id}`}
            onClick={() => setTab(id)}
          >
            {label}
          </button>
        ))}
      </div>
      <section
        id="panel-practice"
        aria-labelledby="tab-practice"
        role="tabpanel"
        className={`review-panel ${tab !== "practice" ? "inactive" : ""}`}
      >
        {metrics([
          [
            `${p.activeDays}/${report.period.days}`,
            ar ? "أيام بها محاولات كلام" : "Days with speech attempts",
          ],
          [
            `${p.matched}/${usable(p)}`,
            ar ? "محاولات طابقها التطبيق" : "App-matched attempts",
          ],
          [
            c.percentagePoints === null
              ? "—"
              : `${c.percentagePoints >= 0 ? "+" : ""}${c.percentagePoints.toFixed(1)} pp`,
            ar
              ? "التغير لأهداف قابلة للمقارنة"
              : "Change on comparable targets",
          ],
        ])}
        <div className="review-columns">
          <div className="review-block">
            <h3>
              {ar ? "توزيع المحاولات عبر الأيام" : "Practice across the days"}
            </h3>
            <div
              className="review-bars"
              aria-label={
                ar ? "محاولات الممارسة اليومية" : "Daily practice captures"
              }
            >
              {report.practice.daily.map((d, i) => (
                <div
                  key={d.date}
                  title={`${d.date}: ${d.counts.matched}/${usable(d.counts)}`}
                >
                  <div className="review-bar-stack">
                    <i
                      className="matched"
                      style={{ height: `${(d.counts.matched / max) * 100}%` }}
                    />
                    <i
                      className="unmatched"
                      style={{
                        height: `${((usable(d.counts) - d.counts.matched) / max) * 100}%`,
                      }}
                    />
                    <i
                      className="silent"
                      style={{ height: `${(d.counts.noSpeech / max) * 100}%` }}
                    />
                    <i
                      className="technical"
                      style={{ height: `${(d.counts.technical / max) * 100}%` }}
                    />
                  </div>
                  <small>
                    {report.period.days === 7 || i % 5 === 0
                      ? d.date.slice(-2)
                      : ""}
                  </small>
                </div>
              ))}
            </div>
            <div className="review-legend">
              <span className="matched">{ar ? "مطابقة" : "Matched"}</span>
              <span className="unmatched">
                {ar ? "لم تطابق" : "Not matched"}
              </span>
              <span className="silent">{ar ? "دون كلام" : "No speech"}</span>
              <span className="technical">{ar ? "تقنية" : "Technical"}</span>
            </div>
            <p>
              {ar
                ? `${p.noSpeech} محاولات دون كلام و${p.technical} مشاكل تقنية مستبعدة من معدل المطابقة.`
                : `${p.noSpeech} no-speech captures and ${p.technical} technical issues are excluded from match rates.`}
            </p>
          </div>
          <div className="review-block">
            <h3>{ar ? "الأهداف واللغة" : "Targets & language"}</h3>
            <table>
              <thead>
                <tr>
                  <th>{ar ? "الهدف" : "Target"}</th>
                  <th>{ar ? "الآن" : "Now"}</th>
                  <th>{ar ? "السابق" : "Prior"}</th>
                </tr>
              </thead>
              <tbody>
                {report.practice.targets.map((x, index) => (
                  <tr
                    className={
                      index < page * 5 || index >= page * 5 + 5
                        ? "off-page"
                        : ""
                    }
                    key={`${x.code}:${x.language}`}
                  >
                    <td>
                      {targetName(x.code, ar)}{" "}
                      <small>{x.language.toUpperCase()}</small>
                    </td>
                    <td>
                      {x.current.matched}/{usable(x.current)}
                    </td>
                    <td>
                      {covered
                        ? `${x.previous.matched}/${usable(x.previous)}`
                        : "—"}
                    </td>
                  </tr>
                ))}
              </tbody>
            </table>
            {report.practice.targets.length === 0 && (
              <p>
                {ar
                  ? "لم تسجل محاولات بالتتبع الجديد بعد."
                  : "No captures from the new tracking yet."}
              </p>
            )}
            {report.practice.targets.length > 5 && (
              <div className="review-pagination">
                <button onClick={() => setPage(page - 1)} disabled={page === 0}>
                  {ar ? "السابق" : "Previous"}
                </button>
                <span>
                  {page * 5 + 1}-
                  {Math.min(page * 5 + 5, report.practice.targets.length)} /{" "}
                  {report.practice.targets.length}
                </span>
                <button
                  onClick={() => setPage(page + 1)}
                  disabled={(page + 1) * 5 >= report.practice.targets.length}
                >
                  {ar ? "التالي" : "Next"}
                </button>
              </div>
            )}
          </div>
        </div>
        <p className="review-footnote">
          {c.percentagePoints === null
            ? ar
              ? "جارٍ بناء خط أساس: تتطلب المقارنة ٥ محاولات قابلة للحساب على يومين في كل فترة للهدف واللغة نفسيهما، مع تغطية التتبع للفترة السابقة."
              : "Building a baseline: comparison needs 5 usable attempts on 2 days per period for the same target/language, with tracking covering the prior period."
            : ar
              ? `المقارنة: ${c.targets} أهداف بوزن متساوٍ، ${c.currentTrials} محاولات حالية و${c.previousTrials} سابقة.`
              : `Comparison: ${c.targets} equally weighted targets, ${c.currentTrials} current and ${c.previousTrials} prior trials.`}{" "}
          {ar
            ? "هذه استجابة التطبيق وليست دقة نطق سريرية."
            : "These are app responses, not clinical pronunciation accuracy."}
        </p>
      </section>
      <section
        id="panel-tasks"
        aria-labelledby="tab-tasks"
        role="tabpanel"
        className={`review-panel ${tab !== "tasks" ? "inactive" : ""}`}
      >
        {metrics([
          [
            `${t.cohortApproved}/${t.assigned}`,
            ar ? "مهام جديدة أكدها ولي الأمر" : "New assignments confirmed",
          ],
          [
            `${t.submitted}`,
            ar ? "مهام أُبلغ بإتمامها" : "Tasks reported done",
          ],
          [
            `${t.awaitingReview}`,
            ar ? "تنتظر المراجعة الآن" : "Awaiting review now",
          ],
        ])}
        <div className="review-columns">
          <div className="review-block">
            <h3>
              {ar ? "المساعدة حسب ولي الأمر" : "Help reported by the caregiver"}
            </h3>
            {[
              ["independent", ar ? "بشكل مستقل" : "Independent"],
              ["prompted", ar ? "بتذكير أو توجيه" : "With prompting"],
              ["assisted", ar ? "بمساعدة مباشرة" : "Direct assistance"],
              ["notRecorded", ar ? "لم يُسجل" : "Not recorded"],
            ].map(([key, label]) => (
              <div className="review-support" key={key}>
                <span>{label}</span>
                <b>{t.support[key as keyof typeof t.support]}</b>
              </div>
            ))}
            <p>
              {ar
                ? `وُصفت المساعدة في ${t.approved - t.support.notRecorded} من ${t.approved} تأكيدات، ولا تُستنتج من الضغط على زر الإتمام.`
                : `Help was described in ${t.approved - t.support.notRecorded} of ${t.approved} confirmations; it is never inferred from tapping Done.`}
            </p>
          </div>
          <div className="review-block">
            <h3>{ar ? "عملية المراجعة" : "The review process"}</h3>
            <p>
              {t.oldestAwaitingHours === null
                ? ar
                  ? "وقت الانتظار غير معروف بعد."
                  : "No known waiting time yet."
                : ar
                  ? `أقدم انتظار معروف: ${t.oldestAwaitingHours.toFixed(1)} ساعة (${t.timedAwaiting}/${t.awaitingReview} لديها أوقات).`
                  : `Oldest known wait: ${t.oldestAwaitingHours.toFixed(1)} hours (${t.timedAwaiting}/${t.awaitingReview} have timing).`}
            </p>
            <p>
              {t.reviewHoursMedian === null
                ? ar
                  ? "لا توجد أوقات إبلاغ وتأكيد متطابقة بعد."
                  : "No paired submission/confirmation times yet."
                : ar
                  ? `وسيط زمن المراجعة: ${t.reviewHoursMedian.toFixed(1)} ساعة من ${t.reviewSamples} مهام.`
                  : `Median review time: ${t.reviewHoursMedian.toFixed(1)} hours across ${t.reviewSamples} tasks.`}
            </p>
            <p>
              {ar
                ? "هذا يقيس متابعة ولي الأمر، لا سرعة الطفل أو قدرته."
                : "This measures caregiver review, not the child's speed or ability."}
            </p>
          </div>
        </div>
        <p className="review-footnote">
          {ar
            ? `أُنشئت ${t.assigned} مهام في الفترة؛ أُكد إتمام ${t.cohortApproved} منها حتى الآن. إجمالي التأكيدات ${t.approved} قد يشمل مهام أقدم. سُجل تعذر الإتمام لـ${t.unable} مهام.`
            : `${t.assigned} tasks were assigned this period; ${t.cohortApproved} are confirmed so far. The ${t.approved} total confirmations may include older tasks. ${t.unable} tasks were marked unable.`}
        </p>
      </section>
      <section
        id="panel-context"
        aria-labelledby="tab-context"
        role="tabpanel"
        className={`review-panel ${tab !== "context" ? "inactive" : ""}`}
      >
        <div className="review-columns">
          <div className="review-block">
            <h3>
              {ar ? "للنقاش في المتابعة القادمة" : "Bring to the next review"}
            </h3>
            <ul>
              <li>
                {focus
                  ? ar
                    ? `راجع ${targetName(focus.code, true)} (${focus.language}): ${focus.current.matched}/${usable(focus.current)} مطابقات. ناقش الهدف والبيئة والمحرك معاً.`
                    : `Review ${targetName(focus.code, false)} (${focus.language}): ${focus.current.matched}/${usable(focus.current)} app matches. Discuss the target, setting, and recognizer together.`
                  : ar
                    ? "كرر الأهداف واللغة نفسيهما لبناء مقارنة."
                    : "Repeat the same targets and language to build a comparison."}
              </li>
              {(p.noSpeech > 0 || p.technical > 0) && (
                <li>
                  {ar
                    ? "راجع ظروف التقاط الصوت قبل تفسير عدم المطابقة."
                    : "Check capture conditions before interpreting non-matches."}
                </li>
              )}
              {t.awaitingReview > 0 && (
                <li>
                  {ar
                    ? `راجع ${t.awaitingReview} مهام قدمها الطفل. انتظار تأكيد ولي الأمر ليس إخفاقاً للطفل.`
                    : `Review ${t.awaitingReview} submitted tasks. Waiting for confirmation is not child failure.`}
                </li>
              )}
              {t.support.notRecorded > 0 && (
                <li>
                  {ar
                    ? "سجل مقدار المساعدة في التأكيدات المقبلة."
                    : "Record help levels on future confirmations."}
                </li>
              )}
            </ul>
          </div>
          <div className="review-block">
            <h3>{ar ? "تغطية البيانات" : "Data coverage"}</h3>
            <p>
              {ar
                ? `بدأ التتبع المنظم في ${date(report.captureStartedAt)}.`
                : `Structured tracking started ${date(report.captureStartedAt)}.`}
            </p>
            <p>
              {ar
                ? `استُبعدت ${report.legacyScoredAttempts} نتائج قديمة من المعدلات والمقارنات لأنها قد تشمل النجاحات فقط.`
                : `${report.legacyScoredAttempts} older scored entries are excluded from rates and comparisons because earlier history may contain successes only.`}
            </p>
            <p>
              {ar
                ? `لقطة المهام الآن: ${t.snapshot.pending} معلقة، ${t.snapshot.submitted} مقدمة، ${t.snapshot.unable} تعذر إتمامها. ليست معدل إنجاز أسبوعياً.`
                : `Current task snapshot: ${t.snapshot.pending} pending, ${t.snapshot.submitted} submitted, ${t.snapshot.unable} unable. This is not a weekly completion rate.`}
            </p>
            {report.practice.omittedTargets > 0 && (
              <p>
                {ar
                  ? `جُمعت ${report.practice.omittedTargets} أهداف إضافية تحت أهداف أخرى؛ لا تدخل في المقارنة.`
                  : `${report.practice.omittedTargets} additional targets are rolled into Other targets and excluded from comparison.`}
              </p>
            )}
          </div>
        </div>
        <p className="review-footnote">
          {ar
            ? "لا أسماء أو تسجيلات أو نصوص التعرف أو ملاحظات خاصة. الأهداف المخصصة تحمل أرقاماً مستعارة. التقرير لا يقيس وضوح النطق أو شدة اضطراب أو نتيجة علاج."
            : "No names, recordings, transcripts, or private notes. Personal targets are numbered. This report does not measure speech clarity, disorder severity, or treatment outcome."}
        </p>
      </section>
      <p className="review-reference">
        {ar ? "مرجع مستعار" : "Pseudonymous reference"}:{" "}
        <code>
          {report.subjectId.slice(0, 8)} / {report.reportId.slice(0, 8)}
        </code>
      </p>
    </div>
  );
}
